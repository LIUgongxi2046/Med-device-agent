import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { documentBytesDigest } from './document-body'
import { lookupOperation, readOperation } from './operations'
import type { Operation, OperationLookup } from './operations'
import { confirmRequestMetadata, expireRequestMetadata, prepareRequestMetadata, readRequestMetadata, requireRequestMetadata } from './request-metadata'
import type { RequestMetadata } from './request-metadata'

export type ExportPurpose = 'FORMAL_BODY' | 'REVIEW_PACKAGE' | 'INTERNAL_EXCHANGE'
export type ExportFormat = 'DOCX' | 'PDF' | 'MARKDOWN'
export interface ExportBasis {
  schema_version: '1.0.0'; document_id: string; document_revision: string; template_version: string
  format_profile_revision: string; review_manifest_sha256: string
  review_status: 'UNREVIEWED' | 'CONFIRMED' | 'RETURNED'; formal_signature_ready: boolean
  allowed_formats: ExportFormat[]
}
export interface ExportCreateInput {
  schema_version: '1.0.0'; document_revision: string; purpose: ExportPurpose; formats: ExportFormat[]
  template_version: string; review_manifest_sha256: string; format_profile_revision: string
  channel_profile_revision: string | null
}
export interface ExportItem {
  export_item_id: string; format: ExportFormat; attempt_no: string
  status: 'QUEUED' | 'RUNNING' | 'VERIFYING' | 'SUCCEEDED' | 'FAILED' | 'CANCEL_REQUESTED' | 'CANCELLED'
  artifact_id: string | null; validation_artifact_id: string | null; downloadable: boolean
  validation: { schema_version: '1.0.0'; format: ExportFormat; sha256: string; size_bytes: string; input_sha256: string
    verified_node_ids: string[]; plan_sha256: string; channel_check: 'APPROVED' | 'PENDING_REVIEW'
    visual_check: 'PASSED' | 'NOT_RUN'; converter_revision: string; font_manifest_revision: string } | null
  revision: string; created_at: string; updated_at: string
}
export interface ExportView {
  schema_version: '1.0.0'; export_id: string; document_id: string; document_revision: string
  purpose: ExportPurpose; status: 'QUEUED' | 'RUNNING' | 'PARTIAL_FAILED' | 'SUCCEEDED' | 'FAILED' | 'CANCEL_REQUESTED' | 'CANCELLED'
  template_version: string; review_manifest_sha256: string; format_profile_revision: string
  channel_profile_revision: string | null; manifest_sha256: string; revision: string
  items: ExportItem[]; created_at: string; updated_at: string
}
export interface ExportHistory {
  schema_version: '1.0.0'; document_id: string; document_revision: string
  page: string; size: string; total: string; has_more: boolean; items: ExportView[]
}
export interface ExportReceipt { schema_version: '1.0.0'; operation_id: string; export: ExportView; actor_id: string; accepted_at: string }
const reference = 'urn:medical:export-api:1.0.0#/$defs/'

function headers(session: MedicalSession, key?: string): Record<string, string> {
  const context = session.current_context
  if (context === null) fail('ACCESS_DENIED', '导出需要当前企业身份')
  validContext(context)
  const values: Record<string, string> = { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision }
  if (key !== undefined) { values['Idempotency-Key'] = key; values[session.csrf.header_name] = session.csrf.token }
  return values
}
export async function readExportBasis(session: MedicalSession, documentId: string, documentRevision: string,
  signal: AbortSignal): Promise<ExportBasis> {
  const response = envelope(await request.get('/api/documents/' + documentId + '/export-basis', { medical: 'envelope', signal,
    params: { document_revision: documentRevision }, headers: headers(session) }))
  if (response.resource_revision !== null) fail('CONTENT_MISMATCH', '导出依据返回了未定义的修订')
  const result = validate<ExportBasis>(reference + 'Basis', response.data)
  if (result.document_id !== documentId || result.document_revision !== documentRevision) fail('CONTENT_MISMATCH', '导出依据与当前文稿不一致')
  return result
}
export interface RecoveredExport { lookup: OperationLookup; view: ExportView | null }
export async function recoverExportRequest(session: MedicalSession, pending: RequestMetadata, signal: AbortSignal): Promise<RecoveredExport> {
  requireRequestMetadata(session, pending)
  if (pending.operation !== 'EX-01' && pending.operation !== 'EX-04') fail('SCHEMA_INVALID', '原请求操作没有导出定义')
  if (pending.state === 'EXPIRED') fail('ACTION_STALE', '原导出请求已经过期')
  const lookup = await lookupOperation(session, pending.operation, pending.key, signal)
  signal.throwIfAborted()
  if (lookup.lookup_status === 'EXPIRED') expireRequestMetadata(session, pending)
  if (lookup.operation?.status !== 'COMPLETED') return { lookup, view: null }
  if (lookup.operation.resource_id === null) fail('CONTENT_MISMATCH', '导出原操作缺少资源身份')
  const view = await readExport(session, pending.operation === 'EX-01' ? lookup.operation.resource_id : pending.resource_id, signal)
  verifyRecoveredExport(pending, lookup.operation, view)
  signal.throwIfAborted()
  return { lookup, view }
}
function verifyRecoveredExport(pending: RequestMetadata, operation: Operation, view: ExportView): void {
  if (view.document_id !== pending.scope_id) fail('CONTENT_MISMATCH', '原导出结果所属文稿不一致')
  if (pending.operation === 'EX-01') {
    if (view.document_id !== pending.target_id || view.export_id !== operation.resource_id) fail('CONTENT_MISMATCH', '原导出结果与文稿身份不一致')
  } else {
    const original = view.items.find(item => item.export_item_id === pending.target_id)
    const created = view.items.find(item => item.export_item_id === operation.resource_id)
    if (view.export_id !== pending.resource_id || !original || !created || created.format !== original.format
        || BigInt(created.attempt_no) <= BigInt(original.attempt_no)) fail('CONTENT_MISMATCH', '原重试结果与指定格式不一致')
  }
}
function verifyCreateInput(view: ExportView, documentId: string, input: ExportCreateInput): void {
  if (view.document_id !== documentId || view.document_revision !== input.document_revision || view.purpose !== input.purpose
      || view.template_version !== input.template_version || view.review_manifest_sha256 !== input.review_manifest_sha256
      || view.format_profile_revision !== input.format_profile_revision || view.channel_profile_revision !== input.channel_profile_revision
      || [...new Set(view.items.map(item => item.format))].sort().join(',') !== [...input.formats].sort().join(',')) {
    fail('CONTENT_MISMATCH', '导出固定结果与原参数不一致')
  }
}
function requestHeaders(session: MedicalSession, pending: RequestMetadata): Record<string, string> {
  return { ...headers(session, pending.key), 'X-Context-Revision': pending.context.context_revision }
}
export async function createExport(session: MedicalSession, documentId: string, input: ExportCreateInput, signal: AbortSignal): Promise<ExportView> {
  validate(reference + 'CreateInput', input)
  const previous = readRequestMetadata(session, 'EX-01', documentId)
  const pending = await prepareRequestMetadata(session, 'EX-01', documentId, documentId, documentId, input, signal)
  if (previous !== null) {
    const recovered = await recoverExportRequest(session, pending, signal)
    if (recovered.view !== null && recovered.lookup.operation !== null) {
      verifyCreateInput(recovered.view, documentId, input)
      confirmRequestMetadata(session, pending, recovered.lookup.operation, recovered.view.export_id)
      return recovered.view
    }
    if (recovered.lookup.lookup_status !== 'NOT_FOUND') fail('ACTION_STALE', '原导出操作尚未取得可读取的完成结果')
  }
  signal.throwIfAborted()
  const response = envelope(await request.post('/api/documents/' + documentId + '/exports', input, { medical: 'envelope', signal,
    headers: requestHeaders(session, pending) }))
  const receipt = validate<ExportReceipt>(reference + 'Receipt', response.data)
  if (receipt.export.document_id !== documentId || receipt.export.document_revision !== input.document_revision
      || receipt.actor_id !== session.user.user_id || response.resource_revision !== receipt.export.revision) fail('CONTENT_MISMATCH', '导出接收回执与请求不一致')
  const operation = await readOperation(session, receipt.operation_id, signal)
  const view = await readExport(session, receipt.export.export_id, signal)
  verifyCreateInput(view, documentId, input); signal.throwIfAborted()
  confirmRequestMetadata(session, pending, operation, view.export_id)
  return view
}
export async function readExport(session: MedicalSession, exportId: string, signal: AbortSignal): Promise<ExportView> {
  const response = envelope(await request.get('/api/exports/' + exportId, { medical: 'envelope', signal, headers: headers(session) }))
  const result = validate<ExportView>(reference + 'View', response.data)
  if (result.export_id !== exportId || response.resource_revision !== revision(result.revision)) fail('CONTENT_MISMATCH', '导出状态与请求不一致')
  return result
}
export async function readExportHistory(session: MedicalSession, documentId: string, documentRevision: string,
  signal: AbortSignal): Promise<ExportHistory> {
  const response = envelope(await request.get('/api/documents/' + documentId + '/exports', { medical: 'envelope', signal,
    params: { document_revision: documentRevision, page: 1, size: 20 }, headers: headers(session) }))
  if (response.resource_revision !== null) fail('CONTENT_MISMATCH', '导出记录返回了未定义的修订')
  const result = validate<ExportHistory>(reference + 'History', response.data)
  if (result.document_id !== documentId || result.document_revision !== documentRevision) fail('CONTENT_MISMATCH', '导出记录与当前文稿不一致')
  return result
}
export async function retryExportItem(session: MedicalSession, parent: ExportView, item: ExportItem, reason: string, signal: AbortSignal): Promise<ExportView> {
  const body = { schema_version: '1.0.0' as const, expected_revision: item.revision, reason }
  validate(reference + 'RetryInput', body)
  if (!parent.items.includes(item)) fail('CONTENT_MISMATCH', '重试格式没有属于当前导出结果')
  const previous = readRequestMetadata(session, 'EX-04', item.export_item_id)
  const pending = await prepareRequestMetadata(session, 'EX-04', item.export_item_id, parent.export_id, parent.document_id, body, signal)
  if (previous !== null) {
    const recovered = await recoverExportRequest(session, pending, signal)
    if (recovered.view !== null && recovered.lookup.operation?.resource_id !== null && recovered.lookup.operation !== null) {
      confirmRequestMetadata(session, pending, recovered.lookup.operation, recovered.lookup.operation.resource_id)
      return recovered.view
    }
    if (recovered.lookup.lookup_status !== 'NOT_FOUND') fail('ACTION_STALE', '原导出重试尚未取得可读取的完成结果')
  }
  signal.throwIfAborted()
  const response = envelope(await request.post('/api/export-items/' + item.export_item_id + '/attempts', body,
    { medical: 'envelope', signal, headers: requestHeaders(session, pending) }))
  const receipt = validate<ExportReceipt>(reference + 'Receipt', response.data)
  if (receipt.actor_id !== session.user.user_id || receipt.export.export_id !== parent.export_id
      || response.resource_revision !== receipt.export.revision) fail('CONTENT_MISMATCH', '导出重试回执与请求不一致')
  const operation = await readOperation(session, receipt.operation_id, signal)
  const view = await readExport(session, parent.export_id, signal)
  verifyRecoveredExport(pending, operation, view); signal.throwIfAborted()
  if (operation.resource_id === null) fail('CONTENT_MISMATCH', '导出重试原操作缺少资源身份')
  confirmRequestMetadata(session, pending, operation, operation.resource_id)
  return view
}
export async function executeExportItem(session: MedicalSession, item: ExportItem, signal: AbortSignal): Promise<ExportView> {
  const body = { schema_version: '1.0.0' as const, expected_revision: item.revision }
  validate(reference + 'ProcessInput', body); const key = crypto.randomUUID()
  const response = envelope(await request.post('/api/export-items/' + item.export_item_id + '/executions', body,
    { medical: 'envelope', signal, headers: headers(session, key), timeout: 600000 }))
  return validate<ExportView>(reference + 'View', response.data)
}
export async function downloadExportItem(session: MedicalSession, item: ExportItem, signal: AbortSignal): Promise<Blob> {
  if (!item.downloadable || item.status !== 'SUCCEEDED' || item.validation === null || item.validation.format !== item.format
      || item.artifact_id === null || item.validation_artifact_id === null) fail('ACTION_STALE', '当前格式尚未通过核验')
  const response = await request.get('/api/export-items/' + item.export_item_id + '/content', {
    medical: 'auth', signal, responseType: 'arraybuffer', headers: headers(session), timeout: 120000
  })
  signal.throwIfAborted()
  if (response.status !== 200 || !(response.data instanceof ArrayBuffer)) fail('DEPENDENCY_UNAVAILABLE', '导出文件读取未完成')
  if (String(response.data.byteLength) !== item.validation.size_bytes
      || await documentBytesDigest(response.data) !== item.validation.sha256) fail('CONTENT_MISMATCH', '导出文件字节与固定核验记录不一致')
  signal.throwIfAborted()
  const mediaTypes: Record<ExportFormat, string> = {
    DOCX: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', PDF: 'application/pdf', MARKDOWN: 'text/markdown'
  }
  return new Blob([response.data], { type: mediaTypes[item.format] })
}
