import { canonicalizeEx } from 'json-canonicalize'
import { fail, validate, validContext } from './protocol'
import type { Context, MedicalSession } from './protocol'
import type { Operation } from './operations'

export type RecoverableOperation = 'KN-14-S04' | 'KN-RIGHTS-01' | 'KN-EXACT-PUBLISH' | 'KN-14-EXACT' | 'EX-01' | 'EX-04'
export interface RequestMetadata {
  actor: string; tenant_id: string; operation: RecoverableOperation; target_id: string
  resource_id: string; scope_id: string
  key: string; context: Context; body_sha256: string; state: 'PENDING' | 'EXPIRED'
}
const reference = 'urn:medical:request-metadata:1.0.0'
const bodyReferences = {
  'KN-14-S04': 'urn:medical:retrieval-view:1.0.0#/$defs/SearchInput',
  'KN-RIGHTS-01': 'urn:medical:source-rights-decisions:1.0.0#/$defs/DecisionInput',
  'KN-EXACT-PUBLISH': 'urn:medical:source-exact:1.0.0#/$defs/PublicationInput',
  'KN-14-EXACT': 'urn:medical:source-exact:1.0.0#/$defs/SearchInput',
  'EX-01': 'urn:medical:export-api:1.0.0#/$defs/CreateInput',
  'EX-04': 'urn:medical:export-api:1.0.0#/$defs/RetryInput'
} as const
function current(session: MedicalSession): Context {
  if (session.current_context === null) fail('ACCESS_DENIED', '原请求恢复需要当前企业身份')
  validContext(session.current_context)
  return session.current_context
}
function storageKey(session: MedicalSession, operation: RecoverableOperation, target: string): string {
  validate(reference + '#/$defs/Id', target)
  if (!Object.hasOwn(bodyReferences, operation)) fail('SCHEMA_INVALID', '原请求操作定义无效')
  return 'medical.request:' + session.user.user_id + ':' + current(session).tenant_id + ':' + operation + ':' + target
}
export function readRequestMetadata(session: MedicalSession, operation: RecoverableOperation, target: string): RequestMetadata | null {
  const saved = sessionStorage.getItem(storageKey(session, operation, target))
  if (saved === null) return null
  const value = validate<RequestMetadata>(reference, JSON.parse(saved))
  validContext(value.context)
  if (value.actor !== session.user.user_id || value.tenant_id !== current(session).tenant_id
      || value.context.tenant_id !== value.tenant_id || value.operation !== operation || value.target_id !== target) {
    fail('CONTENT_MISMATCH', '原请求与当前人员、企业或目标不一致')
  }
  return value
}
export async function requestBodyDigest(body: unknown): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonicalizeEx(body, { strictUndefined: true })))
  return Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, '0')).join('')
}
export async function prepareRequestMetadata(session: MedicalSession, operation: RecoverableOperation, target: string,
    resourceId: string, scopeId: string, body: unknown, signal: AbortSignal): Promise<RequestMetadata> {
  signal.throwIfAborted(); const context = { ...current(session) }; const key = storageKey(session, operation, target)
  validate(reference + '#/$defs/Id', resourceId)
  validate(reference + '#/$defs/Id', scopeId)
  validate(bodyReferences[operation], body)
  const digest = await requestBodyDigest(body); signal.throwIfAborted()
  const previous = readRequestMetadata(session, operation, target)
  if (previous !== null) {
    if (previous.state === 'EXPIRED') fail('ACTION_STALE', '原请求已经过期，不能再次发送原请求')
    if (previous.body_sha256 !== digest || previous.resource_id !== resourceId || previous.scope_id !== scopeId) fail('IDEMPOTENCY_CONFLICT', '原请求仍待核查，请保留原参数')
    return previous
  }
  const value = validate<RequestMetadata>(reference, { actor: session.user.user_id, tenant_id: context.tenant_id,
    operation, target_id: target, resource_id: resourceId, scope_id: scopeId, key: crypto.randomUUID(), context, body_sha256: digest, state: 'PENDING' })
  // 原文字、查询及理由保存在受控内存，浏览器只保存安全身份与摘要。
  sessionStorage.setItem(key, JSON.stringify(value))
  return value
}
export function listRequestMetadata(session: MedicalSession): RequestMetadata[] {
  const prefix = 'medical.request:' + session.user.user_id + ':' + current(session).tenant_id + ':'
  const found: RequestMetadata[] = []
  for (let index = 0; index < sessionStorage.length; index++) {
    const key = sessionStorage.key(index)
    if (key === null || !key.startsWith(prefix)) continue
    const saved = sessionStorage.getItem(key)
    if (saved === null) fail('ACTION_STALE', '原请求元数据在读取期间发生变化')
    const value = validate<RequestMetadata>(reference, JSON.parse(saved))
    if (key !== storageKey(session, value.operation, value.target_id)) fail('CONTENT_MISMATCH', '原请求保存身份不一致')
    found.push(readRequestMetadata(session, value.operation, value.target_id)!)
  }
  return found
}
export function requireRequestMetadata(session: MedicalSession, pending: RequestMetadata): RequestMetadata {
  validate(reference, pending)
  const saved = readRequestMetadata(session, pending.operation, pending.target_id)
  if (saved === null || canonicalizeEx(saved) !== canonicalizeEx(pending)) fail('ACTION_STALE', '原请求元数据已经变化')
  return saved
}
export function expireRequestMetadata(session: MedicalSession, pending: RequestMetadata): void {
  requireRequestMetadata(session, pending)
  sessionStorage.setItem(storageKey(session, pending.operation, pending.target_id), JSON.stringify({ ...pending, state: 'EXPIRED' }))
}
export function clearExpiredRequestMetadata(session: MedicalSession, pending: RequestMetadata): void {
  requireRequestMetadata(session, pending)
  if (pending.state !== 'EXPIRED') fail('ACTION_STALE', '原请求尚未取得过期结果')
  sessionStorage.removeItem(storageKey(session, pending.operation, pending.target_id))
}
export function confirmRequestMetadata(session: MedicalSession, pending: RequestMetadata, operation: Operation, resource: string): void {
  requireRequestMetadata(session, pending)
  validate('urn:medical:shared#/$defs/Operation', operation); validate(reference + '#/$defs/Id', resource)
  if (pending.state !== 'PENDING' || operation.status !== 'COMPLETED' || operation.resource_id !== resource
      || operation.status_url !== '/api/operations/' + operation.operation_id) fail('CONTENT_MISMATCH', '原请求尚未取得对应资源的完成结果')
  sessionStorage.removeItem(storageKey(session, pending.operation, pending.target_id))
}
