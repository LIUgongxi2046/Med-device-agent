import request from '@/utils/request'
import { Temporal } from '@js-temporal/polyfill'
import { canonicalize } from 'json-canonicalize'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import type { RoleCondition } from './enterprise-roles'
import { formatProjectJson } from './project-json'
import { governanceAllowed, governanceId, listGovernance } from './member-governance'
import type { GovernanceInvitation, GovernanceItem, GovernanceKind, GovernancePage, GovernanceScope } from './member-governance'
import { readOperation } from './operations'

export type GovernanceDecision = 'INVITE' | 'GRANT' | 'REVOKE' | 'DISABLE_MEMBER'
export interface InviteMember {
  schema_version: '1.0.0'; identity_binding: { mode: 'local' | 'oidc'; user_id: string; issuer: string | null; subject: string | null }
  roles: GovernanceInvitation['roles']; scope: GovernanceScope; expires_at: string; basis: Basis
}
export interface DecidePermission {
  schema_version: '1.0.0'; decision: Exclude<GovernanceDecision, 'INVITE'>; subject_id: string; scope: GovernanceScope
  action: string | null; grant_id: string | null; condition: RoleCondition | null; valid_from: string | null; valid_to: string | null
  basis: Basis; expected_revision: string; reason: string
}
export type GovernanceBody = InviteMember | DecidePermission
export interface PendingGovernance {
  actor: string; tenant_id: string; operation: 'ID-07' | 'ID-09'; decision: GovernanceDecision; subject_id: string; target_id: string | null
  key: string; context: Context; body_sha256: string; expected_revision: string
}
export interface InvitationCreated {
  schema_version: '1.0.0'; operation_id: string; invitation_id: string; user_id: string; status: 'PENDING'; revision: string
  expires_at: string; credential: string; auth_epoch: string
}
export interface PermissionDecided {
  schema_version: '1.0.0'; operation_id: string; decision: DecidePermission['decision']; subject_id: string; scope: GovernanceScope
  grant: { id: string; status: 'ACTIVE' | 'REVOKED'; revision: string } | null
  member: { id: string; status: 'DISABLED'; revision: string } | null
  revoked_grants: string; terminated_invitations: string; current_context: Context
}
export type GovernanceReceipt = InvitationCreated | PermissionDecided
const pendingSchema = 'urn:medical:member-governance-write-view:1.0.0'
export const permissionReference = (name: string): string => 'urn:medical:permission-decisions:1.0.0#/$defs/' + name
export const invitationReference = (name: string): string => 'urn:medical:member-invitations:1.0.0#/$defs/' + name
const equal = (left: unknown, right: unknown): boolean => canonicalize(left) === canonicalize(right)
function current(session: MedicalSession, decision?: GovernanceDecision): Context {
  if (!session.current_context || !governanceAllowed(session, decision === 'INVITE' || decision === 'DISABLE_MEMBER' ? 'Members' : 'Grants')) {
    fail('ACCESS_DENIED', '当前企业没有相应管理许可')
  }
  validContext(session.current_context); return session.current_context
}
function storageKey(session: MedicalSession): string { return 'medical.member.write:' + session.user.user_id + ':' + current(session).tenant_id }
async function hash(body: GovernanceBody): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonicalize(body)))
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
}
function sameTime(left: string | null, right: string | null): boolean {
  return left === null || right === null ? left === right : Temporal.Instant.compare(left, right) === 0
}
export function checkGovernanceBody(body: GovernanceBody): void {
  validate(permissionReference('Scope'), body.scope); validate(permissionReference('Basis'), body.basis)
  if ('identity_binding' in body) {
    validate(invitationReference('Create'), body); validate('urn:medical:identity-binding:1.0.0', body.identity_binding)
    validate(permissionReference('Time'), body.expires_at); Temporal.Instant.from(body.expires_at)
    if (new Set(body.roles.map(role => role.role_id)).size !== body.roles.length) fail('SCHEMA_INVALID', '邀请岗位标识重复')
  } else {
    validate(permissionReference('Request'), body)
    if (revision(body.expected_revision) === '18446744073709551615') fail('REVISION_LIMIT', '对象版本无法继续增加')
    if (body.decision !== 'GRANT' && body.expected_revision === '0') fail('SCHEMA_INVALID', '撤销和停用需要已读取的对象版本')
    if (body.valid_from !== null) Temporal.Instant.from(body.valid_from)
    if (body.valid_to !== null) Temporal.Instant.from(body.valid_to)
    if (body.valid_from !== null && body.valid_to !== null && Temporal.Instant.compare(body.valid_to, body.valid_from) <= 0) {
      fail('SCHEMA_INVALID', '授权截止时间需要晚于起始时间')
    }
    if (body.decision === 'DISABLE_MEMBER' && (body.scope.resource_kind !== 'MEMBER' || body.scope.resource_id === null || body.scope.project_id !== null)) {
      fail('SCHEMA_INVALID', '停用成员需要完整成员范围')
    }
  }
}
function checkPending(session: MedicalSession, value: PendingGovernance): void {
  validate(pendingSchema, value); validContext(value.context); revision(value.expected_revision)
  if (value.actor !== session.user.user_id || value.tenant_id !== current(session, value.decision).tenant_id || value.context.tenant_id !== value.tenant_id) {
    fail('CONTENT_MISMATCH', '原管理请求与当前主体或企业不一致')
  }
}
export function readPendingGovernance(session: MedicalSession): PendingGovernance | null {
  const text = sessionStorage.getItem(storageKey(session))
  if (text === null) return null
  const value = validate<PendingGovernance>(pendingSchema, JSON.parse(text)); checkPending(session, value); return value
}
export async function prepareGovernance(session: MedicalSession, body: GovernanceBody): Promise<PendingGovernance> {
  checkGovernanceBody(body)
  const decision = 'identity_binding' in body ? 'INVITE' : body.decision; const context = current(session, decision)
  const subject = 'identity_binding' in body ? body.identity_binding.user_id : body.subject_id
  const target = 'identity_binding' in body || body.decision === 'GRANT' ? null : body.decision === 'REVOKE' ? body.grant_id : body.scope.resource_id
  if (body.scope.tenant_id !== context.tenant_id) fail('CONTENT_MISMATCH', '管理范围与当前企业不一致')
  const digest = await hash(body); const previous = readPendingGovernance(session)
  if (previous !== null) {
    if (previous.decision !== decision || previous.subject_id !== subject || previous.target_id !== target || previous.body_sha256 !== digest) {
      fail('IDEMPOTENCY_CONFLICT', '请填写完整原参数并保持原请求身份')
    }
    return previous
  }
  if ([context.context_revision, context.auth_epoch].includes('18446744073709551615')) fail('REVISION_LIMIT', '企业版本无法继续增加')
  const value: PendingGovernance = { actor: session.user.user_id, tenant_id: context.tenant_id, operation: decision === 'INVITE' ? 'ID-07' : 'ID-09',
    decision, subject_id: subject, target_id: target, key: crypto.randomUUID(), context: { ...context }, body_sha256: digest,
    expected_revision: 'identity_binding' in body ? '0' : body.expected_revision }
  checkPending(session, value)
  // 原参数及邀请秘密保存在页面内存，Storage 只保存恢复原请求所需的身份与摘要。
  sessionStorage.setItem(storageKey(session), JSON.stringify(value)); return value
}
export async function submitGovernance(session: MedicalSession, original: PendingGovernance, body: GovernanceBody): Promise<GovernanceReceipt> {
  checkGovernanceBody(body); checkPending(session, original)
  if (original.body_sha256 !== await hash(body)) fail('IDEMPOTENCY_CONFLICT', '管理输入与原请求摘要不一致')
  const response = await request.post(original.operation === 'ID-07' ? '/api/member-invitations' : '/api/permission-decisions', formatProjectJson(body), {
    medical: 'envelope', headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': original.context.context_revision,
      'Idempotency-Key': original.key, [session.csrf.header_name]: session.csrf.token } })
  const result = envelope(response)
  if ('identity_binding' in body) {
    const receipt = validate<InvitationCreated>(invitationReference('CreationReceipt'), result.data)
    revision(receipt.auth_epoch)
    if (response.status !== 201 || receipt.user_id !== original.subject_id || receipt.revision !== '1' || result.resource_revision !== '1'
        || receipt.auth_epoch !== (BigInt(original.context.auth_epoch) + 1n).toString() || !sameTime(receipt.expires_at, body.expires_at)) {
      fail('CONTENT_MISMATCH', '邀请回执与原目标、期限或版本不一致')
    }
    return receipt
  }
  const receipt = validate<PermissionDecided>(permissionReference('Receipt'), result.data); validContext(receipt.current_context)
  revision(receipt.revoked_grants); revision(receipt.terminated_invitations)
  const target = body.decision === 'DISABLE_MEMBER' ? receipt.member : receipt.grant
  const expected = (BigInt(original.expected_revision) + 1n).toString()
  const status = body.decision === 'GRANT' ? 'ACTIVE' : body.decision === 'REVOKE' ? 'REVOKED' : 'DISABLED'
  if (response.status !== 200 || receipt.decision !== body.decision || receipt.subject_id !== original.subject_id || !equal(receipt.scope, body.scope)
      || target === null || target.status !== status || target.revision !== expected || result.resource_revision !== expected
      || original.target_id !== null && target.id !== original.target_id || (body.decision === 'DISABLE_MEMBER' ? receipt.grant !== null : receipt.member !== null)
      || receipt.current_context.tenant_id !== original.tenant_id || receipt.current_context.governance_epoch !== original.context.governance_epoch
      || receipt.current_context.context_revision !== (BigInt(original.context.context_revision) + 1n).toString()
      || receipt.current_context.auth_epoch !== (BigInt(original.context.auth_epoch) + 1n).toString()
      || body.decision === 'GRANT' && (receipt.revoked_grants !== '0' || receipt.terminated_invitations !== '0')
      || body.decision === 'REVOKE' && (receipt.revoked_grants !== '1' || receipt.terminated_invitations !== '0')) {
    fail('CONTENT_MISMATCH', '权限决定回执与原目标、状态或上下文不一致')
  }
  return receipt
}
export async function confirmGovernance(session: MedicalSession, original: PendingGovernance, body: GovernanceBody,
  receipt: GovernanceReceipt, signal: AbortSignal): Promise<{ kind: GovernanceKind; page: GovernancePage; target: GovernanceItem }> {
  checkPending(session, original); const context = current(session, original.decision)
  const wanted: Context = 'current_context' in receipt ? receipt.current_context : { ...original.context,
    context_revision: (BigInt(original.context.context_revision) + 1n).toString(), auth_epoch: receipt.auth_epoch }
  if (!equal(context, wanted)) fail('SCOPE_STALE', '管理写入后当前企业版本需要重新核查')
  const targetId = 'invitation_id' in receipt ? receipt.invitation_id : (receipt.grant ?? receipt.member)!.id
  const operation = await readOperation(session, receipt.operation_id, signal)
  if (operation.status !== 'COMPLETED' || operation.resource_id !== (original.operation === 'ID-07' ? targetId : original.subject_id)) {
    fail('CONTENT_MISMATCH', '原管理操作尚未取得一致的完成结果')
  }
  const kind = original.decision === 'INVITE' ? 'Invitations' : original.decision === 'DISABLE_MEMBER' ? 'Members' : 'Grants'
  // 逐页使用原水位读取目标，授权后的完整集合决定目标是否存在。
  let page = await listGovernance(session, kind, { subject_id: original.subject_id, limit: 100 }, signal)
  let target = page.items.find(item => governanceId(item) === targetId)
  while (!target && page.next_after !== null) {
    page = await listGovernance(session, kind, { subject_id: original.subject_id, limit: 100, after: page.next_after, dataset_watermark: page.dataset_watermark }, signal)
    target = page.items.find(item => governanceId(item) === targetId)
  }
  if (!target) fail('CONTENT_MISMATCH', '管理写入完成后没有读取到原目标')
  const expected = 'revision' in receipt ? receipt.revision : (receipt.grant ?? receipt.member)!.revision
  const status = 'status' in receipt ? receipt.status : (receipt.grant ?? receipt.member)!.status
  if (target.revision !== expected || target.status !== status) fail('SCOPE_STALE', '管理写入后目标状态或版本已经变化')
  if ('identity_binding' in body) {
    if (!('roles' in target) || target.created_by !== original.actor || !equal(target.roles, body.roles) || !equal(target.scope, body.scope)
        || !equal(target.basis, body.basis) || !sameTime(target.expires_at, body.expires_at)) fail('CONTENT_MISMATCH', '当前邀请与完整原参数不一致')
    const members = await listGovernance(session, 'Members', { subject_id: original.subject_id, limit: 100 }, signal)
    const member = members.items[0]
    if (members.items.length !== 1 || !member || member.status !== 'INVITED' || member.revision !== '1') fail('CONTENT_MISMATCH', '新邀请的实际受邀成员没有一致保存')
  } else if ('grant_id' in target) {
    if (!equal(target.scope, body.scope) || target.action !== body.action || body.decision === 'GRANT' && (!equal(target.condition, body.condition)
        || !equal(target.basis, body.basis) || !sameTime(target.valid_to, body.valid_to)
        || body.valid_from !== null && !sameTime(target.valid_from, body.valid_from))) fail('CONTENT_MISMATCH', '当前授权与完整原参数不一致')
  }
  const saved = readPendingGovernance(session)
  if (!saved || saved.key !== original.key || saved.body_sha256 !== await hash(body)) fail('CONTENT_MISMATCH', '原管理请求身份需要继续核查')
  return { kind, page, target }
}
export function clearCompletedGovernance(session: MedicalSession, original: PendingGovernance): void {
  const saved = readPendingGovernance(session)
  if (!saved || !equal(saved, original)) fail('CONTENT_MISMATCH', '完成请求与保存的原请求身份不一致')
  sessionStorage.removeItem(storageKey(session))
}
