import request from '@/utils/request'
import { envelope, fail, validate, validContext, schemaIssues } from '@/api/medical/protocol'
import type { Context, MedicalSession } from '@/api/medical/protocol'
import { canonicalDocumentValue as canonical } from '@/api/medical/document-body'
import { sourceDigest } from '@/api/medical/content-digest'

const ref = 'urn:medical:runtime-retention:1.0.0#/$defs/'
export type Ownership = { scope: 'ENTERPRISE'; project_id: null } | { scope: 'PROJECT'; project_id: string }
export interface Term { category: 'RUN' | 'STREAM'; start_event: 'RUN_CREATED' | 'STREAM_CREATED'; retention_duration_seconds: string; hold_override: false; purge_method: 'CRYPTOGRAPHIC_ERASURE' | 'VERIFIED_DELETE'; backup_expiry_seconds: string }
export interface Approval { schema_version: '1.0.0'; project_id: string | null; classification: 'PUBLIC' | 'INTERNAL' | 'RESTRICTED' | 'PERSONAL_HEALTH'; effective_from: string; effective_until: string; terms: Term[]; reason: string; replaces: { policy_revision: string; expected_revision: string } | null }
export interface Retirement { schema_version: '1.0.0'; policy_revision: string; expected_revision: string; effective_at: string; reason: string }
export interface Fixed { schema_version: '1.0.0'; tenant_id: string; policy_revision: string; request: Approval; approved_by: string; approved_at: string; approval_grant: { grant_id: string; revision: string } }
export interface PolicyView { schema_version: '1.0.0'; fixed: Fixed; content_sha256: string; revision: string; lifecycle: 'SCHEDULED' | 'IN_EFFECT' | 'EXPIRED' | 'RETIRED'; observed_at: string; retirement_at: string | null; retirement_decision_id: string | null }
export interface PolicyList { schema_version: '1.0.0'; scope: Ownership['scope']; project_id: string | null; current_context: Context; pagination: 'LIVE_KEYSET'; limit: number; items: PolicyView[]; next_after: string | null; complete: boolean; listing_sha256: string }
export interface Receipt { schema_version: '1.0.0'; policy_revision: string; operation_id: string; request_body_sha256: string; content_sha256?: string; decision_id?: string; effective_at?: string; revision?: string; status: string }
export interface Pending { version: 1; actor: string; tenant: string; original_context: Context; kind: 'APPROVE' | 'RETIRE'; key: string; body: Approval | Retirement; body_sha256: string; record_sha256: string }
export interface Recovery { schema_version: '1.0.0'; status: 'FOUND' | 'NOT_FOUND'; receipt: Receipt | null }
export class RetentionInputError extends Error {}
export function retentionIdentity(s: MedicalSession) { return canonical({ actor: s.user.user_id, context: s.current_context, actions: s.action_summary }) }
function context(s: MedicalSession) { if (!s.current_context) fail('ACCESS_DENIED', '请先选择企业'); validContext(s.current_context); return s.current_context }
const headers = (s: MedicalSession) => ({ 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context(s).context_revision })
function unchanged(s: MedicalSession, identity: string, signal: AbortSignal) { signal.throwIfAborted(); if (retentionIdentity(s) !== identity) fail('CONTEXT_CHANGED', '操作期间身份或权限发生变化') }
export function retentionAllowed(s: MedicalSession, owner: Ownership, action: 'retention.read' | 'retention.approve') {
  return !!s.current_context && s.action_summary.some(a => a.action === action && (a.resource_kind === 'ENTERPRISE' && a.resource_id === s.current_context?.tenant_id && a.project_id === null || owner.scope === 'PROJECT' && a.resource_kind === 'PROJECT' && a.resource_id === owner.project_id && a.project_id === owner.project_id))
}
async function read<T>(s: MedicalSession, path: string, definition: string, signal: AbortSignal, params?: Record<string, unknown>, extra?: Record<string, string>) {
  const identity = retentionIdentity(s), raw = await request.get(path, { medical: 'envelope', headers: { ...headers(s), ...extra }, params, signal })
  unchanged(s, identity, signal); const e = envelope(raw)
  if (raw.status !== 200 || e.resource_revision !== (definition === 'Availability' ? context(s).context_revision : '1')) fail('CONTENT_MISMATCH', '读取回执版本不匹配')
  return validate<T>(ref + definition, e.data)
}
export async function retentionAvailability(s: MedicalSession, signal: AbortSignal) {
  const v = await read<{ schema_version: '1.0.0'; current_context: Context; runtime_retention_enabled: boolean }>(s, '/api/runtime-retention-availability', 'Availability', signal)
  if (canonical(v.current_context) !== canonical(context(s))) fail('CONTEXT_CHANGED', '服务状态身份不匹配')
  return v.runtime_retention_enabled
}
function instantMicros(value: string): bigint {
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/.exec(value)
  const milliseconds = match ? Date.parse(match[1] + 'Z') : NaN
  if (!match || !Number.isFinite(milliseconds)) fail('CONTENT_MISMATCH', '策略时间无法精确解释')
  return BigInt(milliseconds) * 1000n + BigInt((match[2] ?? '').padEnd(6, '0'))
}
async function verifyView(s: MedicalSession, v: PolicyView, owner?: Ownership) {
  validate(ref + 'RetentionView', v)
  if (v.fixed.tenant_id !== context(s).tenant_id || owner && v.fixed.request.project_id !== owner.project_id || await sourceDigest(v.fixed) !== v.content_sha256) fail('CONTENT_MISMATCH', '保存策略身份或摘要不匹配')
  const retired = v.retirement_at !== null, decision = v.retirement_decision_id !== null
  if (!['1', '2'].includes(v.revision) || retired !== decision || (v.revision === '2') !== retired) fail('CONTENT_MISMATCH', '策略生命周期与 CAS 修订不一致')
  const at = instantMicros(v.observed_at), start = instantMicros(v.fixed.request.effective_from), end = instantMicros(v.fixed.request.effective_until)
  if (end <= start) fail('CONTENT_MISMATCH', '策略生效区间无效')
  const expected = retired && at >= instantMicros(v.retirement_at!) ? 'RETIRED' : at >= end ? 'EXPIRED' : at < start ? 'SCHEDULED' : 'IN_EFFECT'
  if (v.lifecycle !== expected) fail('CONTENT_MISMATCH', '策略时间状态不一致')
  return v
}
export async function retentionList(s: MedicalSession, owner: Ownership, signal: AbortSignal, after?: string) {
  if (owner.scope === 'PROJECT') validate(ref + 'Id', owner.project_id)
  if (after) validate(ref + 'Id', after)
  const identity = retentionIdentity(s), params: Record<string, unknown> = { scope: owner.scope, limit: 10 }
  if (owner.scope === 'PROJECT') params.project_id = owner.project_id
  if (after) params.after = after
  const page = await read<PolicyList>(s, '/api/runtime-retention-policies', 'RetentionList', signal, params)
  if (page.scope !== owner.scope || page.project_id !== owner.project_id || page.limit !== 10 || canonical(page.current_context) !== canonical(context(s)) || page.items.length > 10 || page.listing_sha256 !== await sourceDigest(page.items)) fail('CONTENT_MISMATCH', '策略列表范围或摘要不匹配')
  let previous = after ?? ''
  for (const v of page.items) { await verifyView(s, v, owner); if (v.fixed.policy_revision <= previous) fail('CONTENT_MISMATCH', '策略列表排序不匹配'); previous = v.fixed.policy_revision }
  if (page.complete !== (page.next_after === null) || page.next_after !== null && (!page.items.length || page.next_after !== previous)) fail('CONTENT_MISMATCH', '策略分页回执不匹配')
  unchanged(s, identity, signal); return page
}
export async function retentionDetail(s: MedicalSession, id: string, signal: AbortSignal) {
  validate(ref + 'Id', id); const identity = retentionIdentity(s), v = await read<PolicyView>(s, '/api/runtime-retention-policies/' + id, 'RetentionView', signal)
  await verifyView(s, v); if (v.fixed.policy_revision !== id) fail('CONTENT_MISMATCH', '策略详情目标不匹配'); unchanged(s, identity, signal); return v
}
export function validateRetentionBody(kind: Pending['kind'], body: Approval | Retirement) {
  if (schemaIssues(ref + (kind === 'APPROVE' ? 'RetentionRequest' : 'RetireRequest'), body).length) throw new RetentionInputError('保存策略字段不符合要求，请核对必填项、时间和整数期限')
  if (!body.reason.trim() || body.reason !== body.reason.trim() || body.reason !== body.reason.normalize('NFC') || /[\u0000-\u001f\u007f-\u009f]/u.test(body.reason)) throw new RetentionInputError('请填写不含控制字符和首尾空格的操作理由')
  if (kind === 'APPROVE') { const v = body as Approval; if (Date.parse(v.effective_from) >= Date.parse(v.effective_until) || v.terms.length !== 2 || new Set(v.terms.map(t => t.category)).size !== 2 || v.terms.some(t => t.start_event !== t.category + '_CREATED')) throw new RetentionInputError('请核对生效区间及 RUN / STREAM 保存规则') }
}
export async function prepareRetention(s: MedicalSession, kind: Pending['kind'], body: Approval | Retirement): Promise<Pending> {
  validateRetentionSubmissionTime(kind, body); const fixed = JSON.parse(canonical(body)) as Approval | Retirement
  const record = { version: 1 as const, actor: s.user.user_id, tenant: context(s).tenant_id, original_context: { ...context(s) }, kind, key: crypto.randomUUID(), body: fixed, body_sha256: await sourceDigest(fixed) }
  return { ...record, record_sha256: await sourceDigest(record) }
}
export async function verifyPending(s: MedicalSession, p: Pending) {
  if (!p || Object.keys(p).sort().join(',') !== 'actor,body,body_sha256,key,kind,original_context,record_sha256,tenant,version' || p.version !== 1 || !['APPROVE', 'RETIRE'].includes(p.kind) || p.actor !== s.user.user_id || p.tenant !== context(s).tenant_id) throw new RetentionInputError('待确认请求不属于当前身份')
  if ((typeof p.key !== 'string' || !/^[\x21-\x7e]{16,128}$/.test(p.key)) || schemaIssues(ref + 'Context', p.original_context).length) throw new RetentionInputError('待确认请求身份字段无效'); validateRetentionBody(p.kind, p.body)
  const { record_sha256, ...record } = p
  if (p.original_context.tenant_id !== p.tenant || p.body_sha256 !== await sourceDigest(p.body) || record_sha256 !== await sourceDigest(record)) throw new RetentionInputError('待确认请求记录损坏，不能发起或重发')
}
const normalizedTime = (value: string) => value.replace(/\.0+Z$/, 'Z').replace(/(\.\d*?[1-9])0+Z$/, '$1Z')
function verifyReceipt(p: Pending, value: Receipt) {
  validate(ref + (p.kind === 'APPROVE' ? 'RetentionReceipt' : 'RetireReceipt'), value)
  if (value.request_body_sha256 !== p.body_sha256 || p.kind === 'RETIRE' && (value.policy_revision !== (p.body as Retirement).policy_revision || normalizedTime(value.effective_at ?? '') !== normalizedTime((p.body as Retirement).effective_at) || value.revision !== String(BigInt((p.body as Retirement).expected_revision) + 1n))) fail('CONTENT_MISMATCH', '操作回执与原请求不匹配')
  return value
}
export async function sendRetention(s: MedicalSession, p: Pending, signal: AbortSignal) {
  const identity = retentionIdentity(s)
  p = JSON.parse(canonical(p)) as Pending
  await verifyPending(s, p); unchanged(s, identity, signal)
  if (canonical(p.original_context) !== canonical(context(s))) throw new RetentionInputError('身份水位已变化，请只读核查原请求，不要重发')
  const raw = await request.post(p.kind === 'APPROVE' ? '/api/runtime-retention-policies' : '/api/runtime-retention-retirements', p.body, { medical: 'envelope', headers: { ...headers(s), [s.csrf.header_name]: s.csrf.token, 'Idempotency-Key': p.key }, signal })
  unchanged(s, identity, signal); const e = envelope(raw)
  if (raw.status !== (p.kind === 'APPROVE' ? 201 : 200) || e.resource_revision !== '1') fail('CONTENT_MISMATCH', '策略写入回执版本不匹配')
  return verifyReceipt(p, e.data as Receipt)
}
export async function recoverRetention(s: MedicalSession, p: Pending, signal: AbortSignal) {
  const identity = retentionIdentity(s)
  p = JSON.parse(canonical(p)) as Pending
  await verifyPending(s, p); unchanged(s, identity, signal); const v = await read<Recovery>(s, p.kind === 'APPROVE' ? '/api/runtime-retention-policy-operations' : '/api/runtime-retention-retirement-operations', p.kind === 'APPROVE' ? 'RetentionRecovery' : 'RetireRecovery', signal, undefined, { 'Idempotency-Key': p.key })
  if (v.status === 'FOUND') { if (!v.receipt) fail('CONTENT_MISMATCH', '原请求回执缺失'); verifyReceipt(p, v.receipt) }
  else if (v.receipt !== null) fail('CONTENT_MISMATCH', '未找到状态携带了不一致回执')
  return v
}

export interface CancelRequest { schema_version: '1.0.0'; original_kind: Pending['kind']; original_key: string; original_request: Approval | Retirement }
export interface CancelPending { version: 1; type: 'CANCEL'; actor: string; tenant: string; original_context: Context; key: string; original: Pending; body: CancelRequest; body_sha256: string; record_sha256: string }
export interface CancelReceipt { schema_version: '1.0.0'; status: 'CANCELLED' | 'COMPLETED'; cancellation_id: string; operation_id: string; tenant_id: string; actor_id: string; original_kind: Pending['kind']; original_key: string; original_request_sha256: string; cancellation_request_sha256: string; recorded_at: string; receipt: Receipt | null }
export interface CancelRecovery { schema_version: '1.0.0'; status: 'FOUND' | 'NOT_FOUND'; receipt: CancelReceipt | null }
/** New submissions only. Never use wall-clock checks while validating a historical pending request. */
export function validateRetentionSubmissionTime(kind: Pending['kind'], body: Approval | Retirement, now = Date.now()) {
  validateRetentionBody(kind, body)
  if (kind === 'RETIRE' && Date.parse((body as Retirement).effective_at) <= now) throw new RetentionInputError('停止使用时间已过去，请选择未来时间并留出提交余量')
  if (kind === 'APPROVE') { const value = body as Approval; if (Date.parse(value.effective_until) <= now) throw new RetentionInputError('策略结束时间已过去，请重新填写'); if (value.replaces && Date.parse(value.effective_from) <= now) throw new RetentionInputError('替换生效时间已过去，请选择未来时间并留出提交余量') }
}
export async function prepareCancellation(s: MedicalSession, original: Pending, signal: AbortSignal): Promise<CancelPending> {
  const identity = retentionIdentity(s)
  original = JSON.parse(canonical(original)) as Pending; await verifyPending(s, original)
  const project = original.kind === 'APPROVE' ? (original.body as Approval).project_id : (await retentionDetail(s, (original.body as Retirement).policy_revision, signal)).fixed.request.project_id
  const owner: Ownership = project === null ? { scope: 'ENTERPRISE', project_id: null } : { scope: 'PROJECT', project_id: project }
  unchanged(s, identity, signal)
  if (!retentionAllowed(s, owner, 'retention.read')) throw new RetentionInputError('当前没有原请求范围的读取权限，无法撤回未登记请求')
  const body: CancelRequest = { schema_version: '1.0.0', original_kind: original.kind, original_key: original.key, original_request: original.body }
  const record = { version: 1 as const, type: 'CANCEL' as const, actor: s.user.user_id, tenant: context(s).tenant_id, original_context: { ...context(s) }, key: crypto.randomUUID(), original, body, body_sha256: await sourceDigest(body) }
  unchanged(s, identity, signal); return { ...record, record_sha256: await sourceDigest(record) }
}
export async function verifyCancelPending(s: MedicalSession, p: CancelPending) {
  if (!p || Object.keys(p).sort().join(',') !== 'actor,body,body_sha256,key,original,original_context,record_sha256,tenant,type,version' || p.version !== 1 || p.type !== 'CANCEL' || p.actor !== s.user.user_id || p.tenant !== context(s).tenant_id || typeof p.key !== 'string' || !/^[\x21-\x7e]{16,128}$/.test(p.key) || schemaIssues(ref + 'Context', p.original_context).length || p.original_context.tenant_id !== p.tenant) throw new RetentionInputError('撤回请求记录身份无效，不能提交或覆盖')
  await verifyPending(s, p.original)
  if (schemaIssues(ref + 'CancelRequest', p.body).length || p.body.original_kind !== p.original.kind || p.body.original_key !== p.original.key || canonical(p.body.original_request) !== canonical(p.original.body)) throw new RetentionInputError('撤回请求未绑定完整原请求')
  const { record_sha256, ...record } = p
  if (p.body_sha256 !== await sourceDigest(p.body) || record_sha256 !== await sourceDigest(record)) throw new RetentionInputError('撤回请求摘要不匹配，不能提交或覆盖')
}
function verifyCancelReceipt(p: CancelPending, value: CancelReceipt) {
  validate(ref + 'CancelReceipt', value)
  if (value.tenant_id !== p.tenant || value.actor_id !== p.actor || value.original_kind !== p.original.kind || value.original_key !== p.original.key || value.original_request_sha256 !== p.original.body_sha256 || value.cancellation_request_sha256 !== p.body_sha256) fail('CONTENT_MISMATCH', '撤回回执未绑定当前主体与完整原请求')
  if (value.status === 'CANCELLED') { if (value.receipt !== null) fail('CONTENT_MISMATCH', '撤回证明与原完成回执冲突') }
  else { if (!value.receipt) fail('CONTENT_MISMATCH', '已完成原请求缺少真实回执'); verifyReceipt(p.original, value.receipt) }
  return value
}
export async function sendCancellation(s: MedicalSession, pending: CancelPending, signal: AbortSignal) {
  const identity = retentionIdentity(s), p = JSON.parse(canonical(pending)) as CancelPending; await verifyCancelPending(s, p); unchanged(s, identity, signal)
  const raw = await request.post('/api/runtime-retention-cancellations', p.body, { medical: 'envelope', headers: { ...headers(s), [s.csrf.header_name]: s.csrf.token, 'Idempotency-Key': p.key }, signal })
  unchanged(s, identity, signal); const e = envelope(raw)
  if (raw.status !== 200 || e.resource_revision !== '1') fail('CONTENT_MISMATCH', '撤回请求回执版本不匹配')
  return verifyCancelReceipt(p, e.data as CancelReceipt)
}
export async function recoverCancellation(s: MedicalSession, pending: CancelPending, signal: AbortSignal) {
  const identity = retentionIdentity(s), p = JSON.parse(canonical(pending)) as CancelPending; await verifyCancelPending(s, p); unchanged(s, identity, signal)
  const result = await read<CancelRecovery>(s, '/api/runtime-retention-cancellation-operations', 'CancelRecovery', signal, undefined, { 'Idempotency-Key': p.key })
  if (result.status === 'FOUND') { if (!result.receipt) fail('CONTENT_MISMATCH', '缺少撤回请求回执'); verifyCancelReceipt(p, result.receipt) }
  else if (result.receipt !== null) fail('CONTENT_MISMATCH', '未找到状态附带不一致撤回回执')
  return result
}
