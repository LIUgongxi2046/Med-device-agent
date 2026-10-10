import request from '@/utils/request'
import { canonicalize } from 'json-canonicalize'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { projectActionAllowed, projectTextIssue } from './project-write'
import { formatProjectJson } from './project-json'
import { readOperation } from './operations'
import type { Operation } from './operations'

export interface RoleCondition {
  schema_version: '1.0.0'; identity_modes: ('local' | 'oidc')[]; purposes: string[] | null
  delegation: { actions: string[]; role_ids: string[]; resource_kinds: string[]; project_ids: string[] | null; resource_ids: string[] | null }
}
export interface RoleDefinition { schema_version: '1.0.0'; role_id: string; actions: string[]; condition: RoleCondition }
export interface EnterpriseRole {
  schema_version: '1.0.0'; tenant_id: string; role_id: string; version_id: string; name: string; definition: RoleDefinition
  status: 'ACTIVE' | 'REVOKED'; revision: string; content_sha256: string; approved_by: string; approved_at: string
  valid_from: string; valid_to: string | null; approval_basis: Basis; reason: string; authority_grant: string; authority_revision: string
  head: { current_version: string; revision: string }
}
export interface RoleListing { schema_version: '1.0.0'; roles: EnterpriseRole[]; next_after: string | null }
export interface PublishRole {
  schema_version: '1.0.0'; name: string; definition: RoleDefinition; valid_from: string; valid_to: string | null
  approval_basis: Basis; reason: string; approval_attested: true; expected_head_revision: string
}
export interface RevokeRole { schema_version: '1.0.0'; expected_revision: string; reason: string; approval_attested: true }
export interface RoleReceipt {
  schema_version: '1.0.0'; operation_id: string; decision: 'PUBLISH' | 'REVOKE'; role: EnterpriseRole
  revoked_grants: string[]; terminated_invitations: string[]; current_context: Context
}
export interface PendingRole {
  actor: string; tenant_id: string; role_id: string; version_id: string | null; operation: 'ROLE-03' | 'ROLE-04'
  key: string; context: Context; body_sha256: string; expected_revision: string
}
const prefix = 'medical.role.write:'
export const roleReference = (name: string): string => 'urn:medical:enterprise-roles:1.0.0#/$defs/' + name
export const roleAllowed = (session: MedicalSession | null): boolean => projectActionAllowed(session, 'role.manage', null)
function context(session: MedicalSession): Context {
  if (!session.current_context || !roleAllowed(session)) fail('ACCESS_DENIED', '当前企业没有岗位管理许可')
  validContext(session.current_context); return session.current_context
}
function storageKey(session: MedicalSession): string { return prefix + session.user.user_id + ':' + context(session).tenant_id }
async function hash(value: unknown): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonicalize(value)))
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
}
function definition(value: RoleDefinition): void {
  validate('urn:medical:member-invitations:1.0.0#/$defs/RoleDefinition', value)
  validate('urn:medical:permission-decisions:1.0.0#/$defs/Condition', value.condition)
}
async function checkRole(value: EnterpriseRole, current: Context): Promise<void> {
  validate(roleReference('Role'), value); definition(value.definition)
  for (const number of [value.revision, value.authority_revision, value.head.revision]) {
    if (revision(number) === '0') fail('CONTENT_MISMATCH', '岗位及批准版本无效')
  }
  if (value.tenant_id !== current.tenant_id || value.role_id !== value.definition.role_id
      || value.content_sha256 !== await hash(value.definition) || projectTextIssue(value.name, 120)
      || projectTextIssue(value.reason, 2000) || value.valid_to !== null && Date.parse(value.valid_to) <= Date.parse(value.valid_from)) {
    fail('CONTENT_MISMATCH', '岗位内容、批准依据或企业身份不一致')
  }
}
export async function listRoles(session: MedicalSession, after: string | null, signal: AbortSignal): Promise<RoleListing> {
  const current = context(session)
  if (after !== null) validate(roleReference('Id'), after)
  const result = envelope(await request.get('/api/enterprise-roles', { medical: 'envelope', signal,
    params: after === null ? { limit: '50' } : { limit: '50', after },
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current.context_revision } }))
  const value = validate<RoleListing>(roleReference('Listing'), result.data)
  if (result.resource_revision !== null || value.roles.length > 50 || new Set(value.roles.map(role => role.role_id)).size !== value.roles.length
      || value.next_after !== null && value.next_after !== value.roles.at(-1)?.role_id) fail('CONTENT_MISMATCH', '岗位集合及分页身份不一致')
  let previous = after
  for (const role of value.roles) {
    await checkRole(role, current)
    if (role.version_id !== role.head.current_version || previous !== null && role.role_id <= previous) fail('CONTENT_MISMATCH', '岗位当前发布版本或顺序不一致')
    previous = role.role_id
  }
  return value
}
export async function readRole(session: MedicalSession, roleId: string, versionId: string, signal: AbortSignal): Promise<EnterpriseRole> {
  const current = context(session); validate(roleReference('Id'), roleId); validate(roleReference('Id'), versionId)
  const result = envelope(await request.get('/api/enterprise-roles/' + roleId + '/versions/' + versionId, {
    medical: 'envelope', signal, headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': current.context_revision } }))
  const value = validate<EnterpriseRole>(roleReference('Role'), result.data); await checkRole(value, current)
  if (value.role_id !== roleId || value.version_id !== versionId || result.resource_revision !== value.revision) fail('CONTENT_MISMATCH', '岗位读取与原目标不一致')
  return value
}
export function readPendingRole(session: MedicalSession): PendingRole | null {
  const text = sessionStorage.getItem(storageKey(session))
  if (text === null) return null
  const value = validate<PendingRole>('urn:medical:role-write-view:1.0.0', JSON.parse(text)); validContext(value.context)
  revision(value.expected_revision)
  if (value.actor !== session.user.user_id || value.tenant_id !== context(session).tenant_id || value.context.tenant_id !== value.tenant_id) {
    fail('CONTENT_MISMATCH', '原岗位请求与当前主体或企业不一致')
  }
  return value
}
export function checkRoleBody(operation: PendingRole['operation'], value: PublishRole | RevokeRole): void {
  validate(roleReference(operation === 'ROLE-03' ? 'Publish' : 'Revoke'), value)
  if (projectTextIssue(value.reason, 2000)) fail('SCHEMA_INVALID', '岗位决定原因无效')
  const expected = 'expected_head_revision' in value ? value.expected_head_revision : value.expected_revision
  if (revision(expected) === '18446744073709551615') fail('REVISION_LIMIT', '岗位版本无法继续增加')
  if ('definition' in value) {
    definition(value.definition)
    if (projectTextIssue(value.name, 120) || value.valid_to !== null && Date.parse(value.valid_to) <= Date.parse(value.valid_from)) fail('SCHEMA_INVALID', '岗位名称或有效期限无效')
  } else if (expected === '0') fail('SCHEMA_INVALID', '撤销岗位需要已读取的对象版本')
}
export async function prepareRole(session: MedicalSession, roleId: string, versionId: string | null,
  body: PublishRole | RevokeRole): Promise<PendingRole> {
  const current = context(session); const operation = versionId === null ? 'ROLE-03' : 'ROLE-04'
  validate(roleReference('Id'), roleId); checkRoleBody(operation, body)
  if (versionId !== null) validate(roleReference('Id'), versionId)
  if ('definition' in body && body.definition.role_id !== roleId) fail('CONTENT_MISMATCH', '岗位定义与目标标识不一致')
  const digest = await hash(body); const previous = readPendingRole(session)
  if (previous !== null) {
    if (previous.role_id !== roleId || previous.version_id !== versionId || previous.operation !== operation || previous.body_sha256 !== digest) {
      fail('IDEMPOTENCY_CONFLICT', '原岗位请求仍需核查，请保留完整原参数')
    }
    return previous
  }
  for (const number of [current.context_revision, current.auth_epoch, current.governance_epoch]) {
    if (number === '18446744073709551615') fail('REVISION_LIMIT', '当前企业版本无法继续增加')
  }
  const value: PendingRole = { actor: session.user.user_id, tenant_id: current.tenant_id, role_id: roleId, version_id: versionId,
    operation, key: crypto.randomUUID(), context: { ...current }, body_sha256: digest,
    expected_revision: 'expected_head_revision' in body ? body.expected_head_revision : body.expected_revision }
  validate('urn:medical:role-write-view:1.0.0', value)
  // 仅保存原请求身份和摘要，岗位名称、条件及批准内容保存在当前页面内存。
  sessionStorage.setItem(storageKey(session), JSON.stringify(value)); return value
}
export async function submitRole(session: MedicalSession, pending: PendingRole, body: PublishRole | RevokeRole): Promise<RoleReceipt> {
  const current = context(session); checkRoleBody(pending.operation, body)
  validate('urn:medical:role-write-view:1.0.0', pending)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== current.tenant_id || pending.body_sha256 !== await hash(body)) fail('CONTENT_MISMATCH', '岗位提交与原请求不一致')
  const path = '/api/enterprise-roles/' + pending.role_id + '/versions'
    + (pending.version_id === null ? '' : '/' + pending.version_id + '/revocations')
  const response = await request.post(path, formatProjectJson(body), { medical: 'envelope', headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': pending.context.context_revision,
    'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token } })
  const result = envelope(response); const value = validate<RoleReceipt>(roleReference('Receipt'), result.data)
  await checkRole(value.role, current); validContext(value.current_context)
  if (response.status !== (pending.operation === 'ROLE-03' ? 201 : 200) || value.role.role_id !== pending.role_id
      || pending.version_id !== null && value.role.version_id !== pending.version_id || result.resource_revision !== value.role.revision
      || value.decision !== (pending.operation === 'ROLE-03' ? 'PUBLISH' : 'REVOKE')
      || value.role.status !== (pending.operation === 'ROLE-03' ? 'ACTIVE' : 'REVOKED')
      || value.current_context.tenant_id !== pending.tenant_id
      || ['context_revision', 'auth_epoch', 'governance_epoch'].some(name => value.current_context[name as keyof Context]
        !== (BigInt(pending.context[name as 'context_revision' | 'auth_epoch' | 'governance_epoch']) + 1n).toString())) fail('CONTENT_MISMATCH', '岗位回执与原目标或企业版本不一致')
  if ('definition' in body) {
    if (value.role.head.revision !== (BigInt(body.expected_head_revision) + 1n).toString() || value.role.revision !== '1'
        || value.role.name !== body.name || canonicalize(value.role.definition) !== canonicalize(body.definition)
        || canonicalize(value.role.approval_basis) !== canonicalize(body.approval_basis) || value.role.reason !== body.reason
        || value.role.approved_by !== pending.actor || Date.parse(value.role.valid_from) !== Date.parse(body.valid_from)
        || (value.role.valid_to === null) !== (body.valid_to === null) || body.valid_to !== null && Date.parse(value.role.valid_to!) !== Date.parse(body.valid_to)
        || value.revoked_grants.length || value.terminated_invitations.length) fail('CONTENT_MISMATCH', '岗位发布结果与批准内容不一致')
  } else if (value.role.revision !== (BigInt(body.expected_revision) + 1n).toString()) fail('CONTENT_MISMATCH', '岗位撤销对象版本不一致')
  return value
}
export async function confirmRoleOperation(session: MedicalSession, pending: PendingRole, operationId: string, versionId: string,
  signal: AbortSignal): Promise<Operation> {
  const current = context(session)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== current.tenant_id || pending.version_id !== null && pending.version_id !== versionId) fail('ACCESS_DENIED', '原岗位操作与当前身份不一致')
  const operation = await readOperation(session, operationId, signal)
  if (operation.status !== 'COMPLETED' || operation.resource_id !== versionId) fail('CONTENT_MISMATCH', '原岗位操作没有一致的完成结果')
  return operation
}
export function clearCompletedRole(session: MedicalSession, pending: PendingRole, operation: Operation): void {
  const saved = readPendingRole(session)
  if (saved === null || saved.key !== pending.key || saved.body_sha256 !== pending.body_sha256 || operation.status !== 'COMPLETED'
      || operation.resource_id === null || pending.version_id !== null && operation.resource_id !== pending.version_id) fail('CONTENT_MISMATCH', '原岗位请求仍需核查')
  sessionStorage.removeItem(storageKey(session))
}
