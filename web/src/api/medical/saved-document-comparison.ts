import request from '@/utils/request'
import { envelope, fail, validate, validContext } from './protocol'
import type { Context, MedicalSession } from './protocol'
import { canonicalDocumentValue as canonical } from './document-body'
import { sourceDigest, byteDigest } from './content-digest'
import type { DifferenceRecord } from './document-comparison-display'

const ref = 'urn:medical:document-comparison-views:1.0.0#/$defs/'
export interface CompareRequest { document_id: string; left_revision: string; right_revision: string }
export interface Coverage { selection_sha256: string; delivered_sha256: string; complete: true; next_cursor: null }
export interface Comparison {
  comparison_id: string; comparison_revision: string; left_id: string; right_revision: string
  differences: { artifact_id: string; sha256: string; schema_ref: string; coverage: Coverage }
  affected_node_ids: string[]
}
export interface CoreResult {
  schema_version: '1.0.0'; kind: string; algorithm_id: string; implementation_sha256: string
  schema_sha256: string; body_schema_sha256: string; request: CompareRequest
  left_body_sha256: string; right_body_sha256: string; limits: Record<string, number>
  input_sha256: string; semantic_assessment: 'NOT_PERFORMED'; record_count: number
  records: DifferenceRecord[]; affected_node_ids: string[]
  left_affected_section_ids: string[]; right_affected_section_ids: string[]; coverage: Coverage
}
export interface ComparisonRead {
  schema_version: '1.0.0'; current_document_revision: string | null; document_head_revision: string
  left_is_current: boolean; right_is_current: boolean; comparison: Comparison; result: CoreResult
}
export interface ComparisonAvailability {
  schema_version: '1.0.0'; current_context: Context; features: { document_comparison: boolean }
  maximum_request_bytes: number | null; maximum_result_bytes: number | null; maximum_records: number | null
}
export interface ComparisonRecovery {
  schema_version: '1.0.0'; state: 'NOT_FOUND' | 'PROCESSING' | 'COMPLETED'
  request_body_sha256: string | null; comparison_id: string | null; comparison: Comparison | null
}
export interface ComparisonPending {
  actor: string; context: Context; key: string; body: CompareRequest
  request_body_sha256: string; digest: string
}
function context(session: MedicalSession) {
  if (!session.current_context) fail('ACCESS_DENIED', '需要当前企业身份')
  validContext(session.current_context)
  return session.current_context
}
function headers(session: MedicalSession) {
  return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context(session).context_revision }
}
function identity(session: MedicalSession) { return canonical({ actor: session.user.user_id, context: context(session) }) }
function requireIdentity(session: MedicalSession, original: string, signal: AbortSignal) {
  signal.throwIfAborted()
  if (original !== identity(session)) fail('CONTEXT_CHANGED', '比较读取期间身份已变化')
}
export async function comparisonAvailability(session: MedicalSession, signal: AbortSignal) {
  const original = identity(session)
  const raw = await request.get('/api/document-comparison-feature-availability', { medical: 'envelope', headers: headers(session), signal })
  requireIdentity(session, original, signal)
  const e = envelope(raw)
  const value = validate<ComparisonAvailability>('urn:medical:document-comparison-feature-availability:1.0.0', e.data)
  if (raw.status !== 200 || e.resource_revision !== context(session).context_revision || canonical(value.current_context) !== canonical(context(session))) fail('CONTENT_MISMATCH', '比较服务状态不匹配当前身份')
  return value
}
export async function prepareComparison(session: MedicalSession, body: CompareRequest): Promise<ComparisonPending> {
  validate(ref + 'CompareRequest', body)
  const original = { actor: session.user.user_id, context: { ...context(session) }, key: crypto.randomUUID(), body: structuredClone(body), request_body_sha256: await sourceDigest(body) }
  return { ...original, digest: await sourceDigest(original) }
}
export async function verifyComparisonPending(session: MedicalSession, pending: ComparisonPending) {
  if (!pending || typeof pending !== 'object' || Array.isArray(pending) || Object.keys(pending).sort().join(',') !== 'actor,body,context,digest,key,request_body_sha256') fail('CONTENT_MISMATCH', '原比较恢复记录结构损坏')
  validate(ref + 'CompareRequest', pending.body)
  for (const id of [pending.actor, pending.key]) validate(ref + 'Id', id)
  validContext(pending.context)
  const { digest, ...original } = pending
  if (pending.actor !== session.user.user_id || pending.context.tenant_id !== context(session).tenant_id || pending.request_body_sha256 !== await sourceDigest(pending.body) || digest !== await sourceDigest(original)) fail('CONTENT_MISMATCH', '原比较请求或身份绑定已变化')
}
function matchComparison(body: CompareRequest, value: Comparison) {
  validate(ref + 'Comparison', value)
  if (value.comparison_revision !== '1' || value.left_id !== body.left_revision || value.right_revision !== body.right_revision || !value.differences.coverage.complete || value.differences.coverage.next_cursor !== null || value.differences.schema_ref !== 'urn:medical:document-structural-comparison-core:1.0.0#/$defs/CoreResult') fail('CONTENT_MISMATCH', '比较回执不匹配固定输入')
  return value
}
export async function createComparison(session: MedicalSession, pending: ComparisonPending, signal: AbortSignal) {
  await verifyComparisonPending(session, pending)
  if (canonical(pending.context) !== canonical(context(session))) fail('CONTEXT_CHANGED', '身份水位已变化，请只读核查原比较')
  signal.throwIfAborted()
  // Once dispatched, a write is not cancelled: cancellation cannot prove it did not commit.
  const raw = await request.post('/api/document-comparisons', pending.body, { medical: 'envelope', headers: { ...headers(session), 'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token } })
  const e = envelope(raw)
  if (raw.status !== 200 || e.resource_revision !== '1') fail('CONTENT_MISMATCH', '比较创建响应修订不一致')
  return matchComparison(pending.body, validate<Comparison>(ref + 'Comparison', e.data))
}
export async function recoverComparison(session: MedicalSession, pending: ComparisonPending, signal: AbortSignal) {
  await verifyComparisonPending(session, pending)
  const original = identity(session)
  const raw = await request.get('/api/document-comparison-operations', { medical: 'envelope', headers: { ...headers(session), 'Idempotency-Key': pending.key }, signal })
  requireIdentity(session, original, signal)
  const e = envelope(raw)
  const value = validate<ComparisonRecovery>(ref + 'Recovery', e.data)
  if (raw.status !== 200 || e.resource_revision !== '1' || value.state !== 'NOT_FOUND' && value.request_body_sha256 !== pending.request_body_sha256) fail('CONTENT_MISMATCH', '原比较恢复摘要不一致')
  if (value.comparison) {
    matchComparison(pending.body, value.comparison)
    if (value.comparison_id !== value.comparison.comparison_id) fail('CONTENT_MISMATCH', '恢复比较标识不一致')
  }
  return value
}
/** Self-consistency only; complete provenance is checked by the server against both actual fixed inputs. */
export async function verifyComparisonRead(body: CompareRequest, receipt: Comparison, value: ComparisonRead, capacity: ComparisonAvailability) {
  validate(ref + 'Read', value)
  matchComparison(body, value.comparison)
  const result = value.result
  if (body.left_revision === body.right_revision && result.left_body_sha256 !== result.right_body_sha256) fail('CONTENT_MISMATCH', '同一固定版本正文摘要不一致')
  if (!capacity.features.document_comparison || !capacity.maximum_result_bytes || !capacity.maximum_records) fail('DEPENDENCY_UNAVAILABLE', '比较服务未启用')
  if (new TextEncoder().encode(canonical(result)).byteLength > capacity.maximum_result_bytes || result.records.length > capacity.maximum_records) fail('CONTEXT_LIMIT', '比较结果超过实际部署容量')
  if (canonical(value.comparison) !== canonical(receipt) || canonical(result.request) !== canonical(body) || result.record_count !== result.records.length || result.coverage.selection_sha256 !== result.input_sha256 || result.coverage.delivered_sha256 !== await sourceDigest(result.records) || canonical(result.coverage) !== canonical(receipt.differences.coverage) || canonical(result.affected_node_ids) !== canonical(receipt.affected_node_ids) || await sourceDigest(result) !== receipt.differences.sha256 || value.left_is_current !== (body.left_revision === value.current_document_revision) || value.right_is_current !== (body.right_revision === value.current_document_revision)) fail('CONTENT_MISMATCH', '比较结果、完整记录摘要或固定输入不一致')
  const { input_sha256, records, record_count, affected_node_ids, left_affected_section_ids, right_affected_section_ids, coverage, ...input } = result
  if (await sourceDigest(input) !== input_sha256) fail('CONTENT_MISMATCH', '比较输入摘要不一致')
  const identities = new Set<string>()
  for (const record of records) {
    const key = record.category + ':' + record.identity
    if (identities.has(key)) fail('CONTENT_MISMATCH', '比较记录身份重复')
    identities.add(key)
    const before = record.before_json, after = record.after_json
    if (record.status === 'ADDED' && (before !== null || after === null) || record.status === 'REMOVED' && (before === null || after !== null) || record.status === 'UNCHANGED' && (before === null || before !== after) || record.status === 'CHANGED' && (before === null || after === null || before === after)) fail('CONTENT_MISMATCH', '差异变化标签与两侧原文不一致')
    for (const side of ['before', 'after'] as const) {
      const json = record[`${side}_json`], hash = record[`${side}_sha256`]
      if (json === null ? hash !== null : hash !== await byteDigest(new TextEncoder().encode(json))) fail('CONTENT_MISMATCH', '差异原文摘要不一致')
      if (json !== null) {
        let parsed: unknown
        try { parsed = JSON.parse(json) } catch { fail('SCHEMA_INVALID', '差异原文不是有效 JSON') }
        if (canonical(parsed) !== json) fail('CONTENT_MISMATCH', '差异原文不是规范 JSON')
      }
    }
    if (record.text_change) {
      if (record.category !== 'NODE' || record.text_change.node_id !== record.identity || before === null || after === null) fail('CONTENT_MISMATCH', '文字选区不属于原节点')
      for (const [selection, snippet] of [[record.text_change.left, before], [record.text_change.right, after]] as const) {
        const node = JSON.parse(snippet!)
        const text = node?.content?.kind === 'TEXT' ? node.content.text : undefined
        if (typeof text !== 'string' || node.node_id !== record.identity || selection.end < selection.start || selection.end > Array.from(text).length || Array.from(text).slice(selection.start, selection.end).join('') !== selection.quote || await byteDigest(new TextEncoder().encode(selection.quote)) !== selection.quote_sha256) fail('CONTENT_MISMATCH', '差异文字选区不一致')
      }
    }
  }
  return value
}
export async function readComparison(session: MedicalSession, pending: ComparisonPending, receipt: Comparison, capacity: ComparisonAvailability, signal: AbortSignal) {
  await verifyComparisonPending(session, pending)
  const original = identity(session)
  const raw = await request.get('/api/document-comparisons/' + receipt.comparison_id, { medical: 'envelope', headers: headers(session), signal })
  requireIdentity(session, original, signal)
  const e = envelope(raw)
  if (raw.status !== 200 || e.resource_revision !== '1') fail('CONTENT_MISMATCH', '固定比较读取修订不一致')
  const value = await verifyComparisonRead(pending.body, receipt, validate<ComparisonRead>(ref + 'Read', e.data), capacity)
  requireIdentity(session, original, signal)
  return value
}
function storageKey(session: MedicalSession, documentId: string) { return 'medical.saved-comparison:' + context(session).tenant_id + ':' + session.user.user_id + ':' + documentId }
export function retainComparison(session: MedicalSession, pending: ComparisonPending) { sessionStorage.setItem(storageKey(session, pending.body.document_id), JSON.stringify(pending)) }
export function restoreComparison(session: MedicalSession, documentId: string): ComparisonPending | null {
  const raw = sessionStorage.getItem(storageKey(session, documentId))
  if (!raw) return null
  try { return JSON.parse(raw) } catch { return fail('CONTENT_MISMATCH', '原比较恢复记录损坏') }
}
export function forgetComparison(session: MedicalSession, pending: ComparisonPending) { sessionStorage.removeItem(storageKey(session, pending.body.document_id)) }
