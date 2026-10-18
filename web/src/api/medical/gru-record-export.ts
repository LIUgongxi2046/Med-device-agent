import request from '@/utils/request'
import { envelope, fail, MedicalFailure, validate, validContext } from './protocol'
import type { Context, MedicalSession } from './protocol'
import type { GruView } from './gru-records'
import { canonicalDocumentValue as canonical } from './document-body'
import { sourceDigest, byteDigest } from './content-digest'
import { readGruExportJson, gruExportContentLimit } from './gru-export-json'
import { readBoundedJson } from './project-json'

const ref = 'urn:medical:gru-record-export:1.0.0#/$defs/'
export interface ManifestItem { message_id: string; sequence: string; content_sha256: string }
export interface GruExportBasis {
  schema_version: '1.0.0'; tenant_id: string; project_id: string; conversation_id: string; owner_id: string
  conversation_revision: string; document_id: string; document_revision: string; body_sha256: string
  scope_snapshot_id: string; scope_sha256: string; mode: 'RECORD_ONLY'; execution_state: 'NOT_EXECUTED'
  message_count: number; message_manifest: ManifestItem[]; message_manifest_sha256: string
  complete: true; next_cursor: null; current_context: Context
}
export interface GruExportRequest { schema_version: '1.0.0'; basis: GruExportBasis; reason: string; purpose: 'GRU_RECORD_EXPORT'; format: 'JSON' }
export interface GruExportReceipt {
  schema_version: '1.0.0'; export_id: string; operation_id: string; conversation_id: string; conversation_revision: string
  document_id: string; document_revision: string; message_manifest_sha256: string; request_body_sha256: string
  artifact_id: string; content_sha256: string; size_bytes: string; media_type: 'application/json'
  format: 'JSON'; mode: 'RECORD_ONLY'; execution_state: 'NOT_EXECUTED'; created_at: string
}
export interface GruExportPending { actor: string; context: Context; key: string; body: GruExportRequest; body_sha256: string; digest: string }
export interface GruExportRecovery {
  schema_version: '1.0.0'; conversation_id: string; idempotency_key: string; status: 'NOT_FOUND' | 'PROCESSING' | 'COMPLETED'
  request_body_sha256: string | null; receipt: GruExportReceipt | null; current_context: Context
}
export interface GruExportDocument extends Omit<GruExportBasis, 'current_context'> {
  purpose: 'GRU_RECORD_EXPORT'; format: 'JSON'
  messages: { message_id: string; sequence: string; text: string; intent: 'QUERY' | 'RETRIEVE' | 'CHECK' | 'PROPOSE_EDIT'; execution_state: 'NOT_EXECUTED'; created_at: string; content_sha256: string }[]
}
function context(s: MedicalSession) {
  if (!s.current_context) fail('ACCESS_DENIED', '导出需要当前企业身份')
  validContext(s.current_context); return s.current_context
}
const identity = (s: MedicalSession) => canonical({ actor: s.user.user_id, context: context(s) })
const headers = (s: MedicalSession) => ({ 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context(s).context_revision })
function current(s: MedicalSession, original: string, signal: AbortSignal) {
  signal.throwIfAborted()
  if (identity(s) !== original) fail('CONTEXT_CHANGED', '导出读取期间身份发生变化')
}
export function gruExportAllowed(s: MedicalSession, selected: Pick<GruView, 'project_id' | 'document_id'>, action: 'conversation.export' | 'conversation.read') {
  return !!s.current_context && s.action_summary.some(a => a.action === action && (a.project_id === null || a.project_id === selected.project_id) && (
    a.resource_kind === 'ENTERPRISE' && a.resource_id === s.current_context?.tenant_id ||
    a.resource_kind === 'PROJECT' && a.resource_id === selected.project_id ||
    a.resource_kind === 'DOCUMENT' && (a.resource_id === null || a.resource_id === selected.document_id)))
}
export function gruExportDownloadAllowed(s: MedicalSession, projectId: string, receipt: GruExportReceipt) {
  return !!s.current_context && s.action_summary.some(a => a.action === 'artifact.read' && (a.project_id === null || a.project_id === projectId) && (
    a.resource_kind === 'ENTERPRISE' && a.resource_id === s.current_context?.tenant_id ||
    a.resource_kind === 'PROJECT' && a.resource_id === projectId ||
    a.resource_kind === 'ARTIFACT' && (a.resource_id === null || a.resource_id === receipt.artifact_id)))
}
export async function gruExportAvailability(s: MedicalSession, signal: AbortSignal) {
  const original = identity(s)
  const raw = await request.get('/api/gru-record-export-feature-availability', { medical: 'envelope', headers: headers(s), signal })
  current(s, original, signal)
  const e = envelope(raw), v = validate<{ schema_version: '1.0.0'; current_context: Context; features: { gru_record_export: boolean } }>('urn:medical:gru-record-export-feature-availability:1.0.0', e.data)
  if (raw.status !== 200 || e.resource_revision !== context(s).context_revision || canonical(v.current_context) !== canonical(context(s))) fail('CONTENT_MISMATCH', '导出部署状态与身份不一致')
  return v.features.gru_record_export
}
async function manifest(b: Omit<GruExportBasis, 'current_context'>) {
  if (b.message_manifest.length !== b.message_count || b.conversation_revision !== String(b.message_count + 1) || new Set(b.message_manifest.map(m => m.message_id)).size !== b.message_count || b.message_manifest.some((m, i) => m.sequence !== String(i + 2)) || await sourceDigest(b.message_manifest) !== b.message_manifest_sha256) fail('CONTENT_MISMATCH', '完整消息清单、顺序或固定修订不一致')
}
export async function readGruExportBasis(s: MedicalSession, selected: GruView, signal: AbortSignal) {
  validate(ref + 'Id', selected.conversation_id)
  const original = identity(s)
  const raw = await request.get('/api/gru-conversations/' + selected.conversation_id + '/export-basis', { medical: 'envelope', headers: headers(s), signal })
  current(s, original, signal)
  const e = envelope(raw), b = validate<GruExportBasis>(ref + 'Basis', e.data)
  if (raw.status !== 200 || e.resource_revision !== '1' || b.conversation_id !== selected.conversation_id || b.owner_id !== s.user.user_id || b.tenant_id !== context(s).tenant_id || b.project_id !== selected.project_id || b.document_id !== selected.document_id || b.document_revision !== selected.document_revision || b.scope_snapshot_id !== selected.scope_snapshot_id || b.scope_sha256 !== await sourceDigest(selected.scope) || canonical(b.current_context) !== canonical(context(s))) fail('CONTENT_MISMATCH', '导出依据不匹配所选完整固定会话；请重新读取会话')
  await manifest(b); current(s, original, signal)
  if (b.conversation_revision !== selected.revision) throw new MedicalFailure('ACTION_STALE', '会话已有新消息，请重新读取所选会话后再读取导出依据')
  return b
}
export function gruExportReasonIssue(reason: string) {
  return !reason.trim() || reason !== reason.trim() || reason !== reason.normalize('NFC') || Array.from(reason).length > 2000 || /[\u0000-\u001f\u007f-\u009f]/u.test(reason) ? '请填写符合 NFC 且不含首尾空白或控制字符的导出理由（最多 2000 字）' : ''
}
export async function prepareGruExport(s: MedicalSession, basis: GruExportBasis, reason: string): Promise<GruExportPending> {
  validate(ref + 'Basis', basis); await manifest(basis)
  if (gruExportReasonIssue(reason)) throw new MedicalFailure('SCHEMA_INVALID', gruExportReasonIssue(reason))
  if (canonical(basis.current_context) !== canonical(context(s)) || basis.owner_id !== s.user.user_id) fail('CONTEXT_CHANGED', '导出依据已过期')
  const body: GruExportRequest = { schema_version: '1.0.0', basis: JSON.parse(canonical(basis)), reason, purpose: 'GRU_RECORD_EXPORT', format: 'JSON' }
  validate(ref + 'Request', body)
  const original = { actor: s.user.user_id, context: { ...context(s) }, key: crypto.randomUUID(), body, body_sha256: await sourceDigest(body) }
  return { ...original, digest: await sourceDigest(original) }
}
export async function verifyGruExportPending(s: MedicalSession, p: GruExportPending) {
  if (!p || typeof p !== 'object' || Object.keys(p).sort().join(',') !== 'actor,body,body_sha256,context,digest,key') fail('CONTENT_MISMATCH', '原导出记录结构损坏')
  validate(ref + 'Request', p.body); validContext(p.context)
  const { digest, ...original } = p
  if (!/^[!-~]{16,128}$/.test(p.key) || p.actor !== s.user.user_id || p.context.tenant_id !== context(s).tenant_id || p.body.basis.owner_id !== p.actor || p.body.basis.tenant_id !== p.context.tenant_id || canonical(p.body.basis.current_context) !== canonical(p.context) || p.body_sha256 !== await sourceDigest(p.body) || digest !== await sourceDigest(original)) fail('CONTENT_MISMATCH', '原导出请求摘要或身份不一致')
  await manifest(p.body.basis)
}
function matchReceipt(p: GruExportPending, r: GruExportReceipt) {
  validate(ref + 'Receipt', r)
  const b = p.body.basis
  if (r.request_body_sha256 !== p.body_sha256 || r.conversation_id !== b.conversation_id || r.conversation_revision !== b.conversation_revision || r.document_id !== b.document_id || r.document_revision !== b.document_revision || r.message_manifest_sha256 !== b.message_manifest_sha256) fail('CONTENT_MISMATCH', '导出回执不匹配完整原请求')
  return r
}
export async function createGruExport(s: MedicalSession, p: GruExportPending, signal: AbortSignal) {
  await verifyGruExportPending(s, p)
  if (canonical(p.context) !== canonical(context(s))) fail('CONTEXT_CHANGED', '当前身份水位已变化，仅可只读恢复原导出')
  signal.throwIfAborted()
  // A dispatched write is not aborted; unknown completion retains its original request.
  const raw = await request.post('/api/gru-conversations/' + p.body.basis.conversation_id + '/exports', p.body, { medical: 'envelope', headers: { ...headers(s), 'Idempotency-Key': p.key, [s.csrf.header_name]: s.csrf.token } })
  const e = envelope(raw)
  if (raw.status !== 201 || e.resource_revision !== '1') fail('CONTENT_MISMATCH', '导出回执状态不一致')
  return matchReceipt(p, validate<GruExportReceipt>(ref + 'Receipt', e.data))
}
export async function recoverGruExport(s: MedicalSession, p: GruExportPending, signal: AbortSignal) {
  await verifyGruExportPending(s, p)
  const original = identity(s)
  const raw = await request.get('/api/gru-conversations/' + p.body.basis.conversation_id + '/export-operations', { medical: 'envelope', headers: { ...headers(s), 'Idempotency-Key': p.key }, signal })
  current(s, original, signal)
  const e = envelope(raw), v = validate<GruExportRecovery>(ref + 'Recovery', e.data)
  if (raw.status !== 200 || e.resource_revision !== '1' || canonical(v.current_context) !== canonical(context(s)) || v.conversation_id !== p.body.basis.conversation_id || v.idempotency_key !== p.key || v.status !== 'NOT_FOUND' && v.request_body_sha256 !== p.body_sha256) fail('CONTENT_MISMATCH', '原导出恢复绑定不一致')
  if (v.receipt) matchReceipt(p, v.receipt)
  return v
}
export async function readGruExport(s: MedicalSession, p: GruExportPending, r: GruExportReceipt, signal: AbortSignal) {
  await verifyGruExportPending(s, p); matchReceipt(p, r)
  const original = identity(s)
  const raw = await request.get('/api/gru-record-exports/' + r.export_id, { medical: 'envelope', headers: headers(s), signal })
  current(s, original, signal)
  const e = envelope(raw), v = validate<{ receipt: GruExportReceipt; current_context: Context; status: 'AVAILABLE' }>(ref + 'View', e.data)
  if (raw.status !== 200 || e.resource_revision !== '1' || canonical(v.receipt) !== canonical(r) || canonical(v.current_context) !== canonical(context(s))) fail('CONTENT_MISMATCH', '导出读取回执或当前身份不一致')
  return v
}
export async function downloadGruExport(s: MedicalSession, p: GruExportPending, r: GruExportReceipt, signal: AbortSignal): Promise<Blob> {
  await verifyGruExportPending(s, p); matchReceipt(p, r)
  const length = Number(r.size_bytes)
  if (!Number.isSafeInteger(length) || length > gruExportContentLimit) throw new MedicalFailure('CONTEXT_LIMIT', '导出文件超过客户端读取容量')
  const original = identity(s)
  const raw = await request.get('/api/gru-record-exports/' + r.export_id + '/content', { medical: 'envelope', headers: headers(s), responseType: 'arraybuffer', signal })
  current(s, original, signal)
  if (!(raw.data instanceof ArrayBuffer)) fail('SCHEMA_INVALID', '导出下载未返回原始字节')
  if (raw.data.byteLength > gruExportContentLimit) throw new MedicalFailure('CONTEXT_LIMIT', '导出文件超过客户端读取容量')
  let text: string
  try { text = new TextDecoder('utf-8', { fatal: true }).decode(raw.data) } catch { return fail('CONTENT_MISMATCH', '导出文件不是有效 UTF-8') }
  if (raw.status !== 200) { envelope({ ...raw, data: readGruExportJson(text) }); return fail('CONTENT_MISMATCH', '导出下载状态无效') }
  const h = raw.headers
  if (h['cache-control'] !== 'no-store' || h['x-schema-version'] !== '1.0.0' || h['x-resource-revision'] !== '1' || h['x-content-type-options'] !== 'nosniff' || String(h['content-type']).split(';')[0]?.trim() !== 'application/json' || h['content-length'] !== r.size_bytes || h['x-content-sha256'] !== r.content_sha256 || raw.data.byteLength !== length || await byteDigest(raw.data) !== r.content_sha256) fail('CONTENT_MISMATCH', '下载字节、长度、摘要或响应元数据不一致')
  validate(ref + 'Id', h['x-request-id'])
  // Full file uses its own bound; envelope JSON's smaller ceiling does not truncate content.
  const v = validate<GruExportDocument>(ref + 'Document', readBoundedJson(text))
  const { current_context, ...basis } = p.body.basis
  const { messages, purpose, format, ...documentBasis } = v
  if (canonical(documentBasis) !== canonical(basis) || canonical(v) !== text || messages.length !== basis.message_count || messages.some((m, i) => m.message_id !== basis.message_manifest[i]?.message_id || m.sequence !== basis.message_manifest[i]?.sequence || m.content_sha256 !== basis.message_manifest[i]?.content_sha256)) fail('CONTENT_MISMATCH', '下载内容不匹配完整固定消息清单')
  current(s, original, signal)
  return new Blob([raw.data], { type: 'application/json' })
}
const storageKey = (s: MedicalSession, conversation: string) => 'medical.gru-export:' + context(s).tenant_id + ':' + s.user.user_id + ':' + conversation
export function retainGruExport(s: MedicalSession, p: GruExportPending) { sessionStorage.setItem(storageKey(s, p.body.basis.conversation_id), JSON.stringify(p)) }
export function restoreGruExport(s: MedicalSession, conversation: string): GruExportPending | null {
  const raw = sessionStorage.getItem(storageKey(s, conversation)); if (!raw) return null
  try { return JSON.parse(raw) } catch { return fail('CONTENT_MISMATCH', '原导出恢复记录损坏') }
}
/** Explicit user acknowledgement after verified download; unresolved requests never enter here. */
export async function finishGruExport(s: MedicalSession, p: GruExportPending, receipt: GruExportReceipt) {
  await verifyGruExportPending(s, p); matchReceipt(p, receipt)
  const key = storageKey(s, p.body.basis.conversation_id)
  const stored = sessionStorage.getItem(key)
  if (stored !== JSON.stringify(p)) fail('CONTENT_MISMATCH', '当前保留的导出请求已经变化')
  sessionStorage.setItem(key + ':completed:' + receipt.export_id, JSON.stringify({ pending: p, receipt }))
  sessionStorage.removeItem(key)
}
