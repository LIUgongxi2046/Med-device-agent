import request from '@/utils/request'
import { envelope, fail, revision, validate } from './protocol'
import type { Context, MedicalSession, Member, SelectionOptions } from './protocol'
import { readOptions, readSessionCsrf } from './session'

export interface InvitationIdentity { user: { user_id: string }; csrf: MedicalSession['csrf']; options: SelectionOptions }
export interface PendingInvitation { actor: string; tenant_id: string; invitation_id: string; expected_revision: string; key: string }
export interface PendingInvitationSelection { actor: string; tenant_id: string; expected_revision: string; key: string }
export interface AcceptanceReceipt {
  schema_version: '1.0.0'; operation_id: string; invitation_id: string; user_id: string; status: 'ACCEPTED'
  revision: string; member_id: string; member_revision: string; grants: string[]; auth_epoch: string
}
const prefix = 'medical.invitation.accept:'
const pendingReference = 'urn:medical:invitation-accept-view:1.0.0'
const selectionPrefix = 'medical.invitation.selection:'
const selectionReference = pendingReference + '#/$defs/Selection'
export const invitationReference = (name: string): string => 'urn:medical:member-invitations:1.0.0#/$defs/' + name

export async function readInvitationIdentity(signal: AbortSignal): Promise<InvitationIdentity> {
  const options = await readOptions(signal)
  const csrf = await readSessionCsrf(signal)
  return { user: { user_id: options.user_id }, options, csrf }
}
export function readPendingInvitation(actor: string): PendingInvitation | null {
  validate(invitationReference('Id'), actor)
  const text = sessionStorage.getItem(prefix + actor)
  if (text === null) return null
  const value = validate<PendingInvitation>(pendingReference, JSON.parse(text))
  revision(value.expected_revision)
  if (value.actor !== actor || value.expected_revision === '0') fail('CONTENT_MISMATCH', '原邀请请求与当前本人身份或版本不一致')
  return value
}
export function prepareInvitation(identity: InvitationIdentity, tenant: string, invitation: string, expected: string): PendingInvitation {
  validate(invitationReference('Id'), tenant); validate(invitationReference('Id'), invitation)
  if (revision(expected) === '0' || expected === '18446744073709551615') fail('REVISION_LIMIT', '邀请需要仍可增加的原版本')
  const previous = readPendingInvitation(identity.user.user_id)
  if (previous !== null) {
    if (previous.tenant_id !== tenant || previous.invitation_id !== invitation || previous.expected_revision !== expected) {
      fail('IDEMPOTENCY_CONFLICT', '原邀请接受请求仍需核查，请保留原邀请和版本')
    }
    return previous
  }
  const value = { actor: identity.user.user_id, tenant_id: tenant, invitation_id: invitation, expected_revision: expected, key: crypto.randomUUID() }
  validate(pendingReference, value)
  // 邀请凭据及其摘要不进入浏览器保存记录。
  sessionStorage.setItem(prefix + value.actor, JSON.stringify(value)); return value
}
export async function acceptInvitation(identity: InvitationIdentity, pending: PendingInvitation, credential: string): Promise<AcceptanceReceipt> {
  validate(pendingReference, pending)
  if (pending.actor !== identity.user.user_id) fail('ACCESS_DENIED', '邀请接受需要重新核查本人身份')
  const body = { schema_version: '1.0.0', credential, expected_revision: pending.expected_revision }
  validate(invitationReference('Accept'), body)
  const response = await request.post('/api/member-invitations/' + pending.invitation_id + '/acceptances', body,
    { medical: 'envelope', headers: { 'X-Schema-Version': '1.0.0', 'Idempotency-Key': pending.key, [identity.csrf.header_name]: identity.csrf.token } })
  const result = envelope(response)
  const value = validate<AcceptanceReceipt>(invitationReference('AcceptanceReceipt'), result.data)
  for (const number of [value.revision, value.member_revision, value.auth_epoch]) {
    if (revision(number) === '0') fail('CONTENT_MISMATCH', '邀请或成员版本无效')
  }
  if (response.status !== 200 || value.user_id !== pending.actor || value.invitation_id !== pending.invitation_id
      || result.resource_revision !== value.revision || value.revision !== (BigInt(pending.expected_revision) + 1n).toString()) {
    fail('CONTENT_MISMATCH', '邀请回执与原请求不一致')
  }
  return value
}
export function confirmInvitation(identity: InvitationIdentity, pending: PendingInvitation, receipt: AcceptanceReceipt): Member {
  const saved = readPendingInvitation(identity.user.user_id)
  const actual = identity.options.memberships.filter(member => member.tenant_id === pending.tenant_id && member.member_id === receipt.member_id)
  if (identity.user.user_id !== pending.actor || receipt.user_id !== pending.actor || saved?.key !== pending.key
      || actual.length !== 1 || actual[0]!.status !== 'ACTIVE' || actual[0]!.revision !== receipt.member_revision) {
    fail('CONTEXT_CHANGED', '邀请接受后的当前本人或成员需要重新核查')
  }
  sessionStorage.removeItem(prefix + pending.actor); return actual[0]!
}
export function readInvitationSelection(actor: string): PendingInvitationSelection | null {
  validate(invitationReference('Id'), actor)
  const text = sessionStorage.getItem(selectionPrefix + actor)
  if (text === null) return null
  const value = validate<PendingInvitationSelection>(selectionReference, JSON.parse(text))
  if (value.actor !== actor) fail('CONTENT_MISMATCH', '原企业选择与当前本人身份不一致')
  revision(value.expected_revision); return value
}
export function prepareInvitationSelection(identity: InvitationIdentity, target: string): PendingInvitationSelection {
  validate(invitationReference('Id'), target)
  if (!identity.options.memberships.some(member => member.tenant_id === target && member.status === 'ACTIVE')) fail('ACCESS_DENIED', '当前本人没有目标企业成员身份')
  const previous = readInvitationSelection(identity.user.user_id)
  if (previous) {
    if (previous.tenant_id !== target) fail('IDEMPOTENCY_CONFLICT', '原企业选择仍需核查，请保留原目标')
    return previous
  }
  const expected = revision(identity.options.expected_context_revision)
  if (expected === '18446744073709551615') fail('REVISION_LIMIT', '企业上下文版本已经达到上限')
  const value = { actor: identity.user.user_id, tenant_id: target, expected_revision: expected, key: crypto.randomUUID() }
  validate(selectionReference, value); sessionStorage.setItem(selectionPrefix + value.actor, JSON.stringify(value)); return value
}
export function confirmInvitationSelection(session: MedicalSession, pending: PendingInvitationSelection, selected: Context): void {
  const saved = readInvitationSelection(session.user.user_id)
  if (saved?.key !== pending.key || saved.tenant_id !== pending.tenant_id || session.user.user_id !== pending.actor
      || selected.tenant_id !== pending.tenant_id || selected.context_revision !== (BigInt(pending.expected_revision) + 1n).toString()
      || session.current_context === null || ['tenant_id', 'context_revision', 'auth_epoch', 'governance_epoch'].some(name =>
        session.current_context![name as keyof Context] !== selected[name as keyof Context])) fail('CONTEXT_CHANGED', '原企业选择返回后当前上下文仍需核查')
  sessionStorage.removeItem(selectionPrefix + pending.actor)
}
