import type { DocumentBody, DocumentSnapshot, Selection } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue, documentBytesDigest, documentReference, immutableDocumentValue } from './document-body'
import { createDocumentSelection } from './document-selection'
import type { SelectionRequest } from './document-selection'
import { fail, revision, validate, validContext } from './protocol'
import type { Context, MedicalSession } from './protocol'
import type { Operation } from './operations'
import type { AnchorLifecycle } from './annotation-reanchors'

export const annotationSchema = 'urn:medical:annotation-threads:1.0.0#/$defs/'
export const decisionSchema = 'urn:medical:annotation-decisions:1.0.0#/$defs/'
export const replySchema = 'urn:medical:annotation-replies:1.0.0#/$defs/'
const pendingSchema = 'urn:medical:annotation-write-view:1.0.0'
export type AnnotationStatus = 'OPEN' | 'RESOLVED' | 'RETURNED' | 'REANCHOR_REQUIRED'
export type AnnotationLocation = 'CURRENT_VERSION' | 'ORIGINAL_VERSION'
export interface Annotation {
  annotation_id: string; document_revision: string; revision: string; selection: Selection; selection_artifact_id: string
  selection_sha256: string; content: string; actor_id: string; created_at: string; status: AnnotationStatus
  location_status: AnnotationLocation; reply_count: string
  effective_anchor?: { document_revision: string; selection: Selection }
}
export interface AnnotationListing {
  schema_version: '1.0.0'; document_id: string; document_revision: string; revision: string; issue_watermark: string
  evidence_watermark: string; status_filter: AnnotationStatus | null; total: string; complete: true; items: Annotation[]; listing_sha256: string
}
export interface AnnotationCreate { document_revision: string; selection: Selection; content: string; expected_revision: string }
export interface ReplyCreate { document_revision: string; content: string; expected_revision: string }
export interface AnnotationReceipt {
  schema_version: '1.0.0'; document_id: string; document_revision: string; revision: string; issue_watermark: string; evidence_watermark: string
  operation_id: string; previous_revision: string; annotation: Annotation; annotation_sha256: string; invalidated_review_ids: string[]
}
export interface AnnotationReply { reply_id: string; annotation_id: string; actor_id: string; created_at: string; content: string }
export interface ReplyListing {
  schema_version: '1.0.0'; annotation_id: string; document_id: string; document_revision: string; revision: string
  status: AnnotationStatus; location_status: AnnotationLocation; total: string; complete: true; items: AnnotationReply[]; listing_sha256: string
}
export interface ReplyReceipt {
  schema_version: '1.0.0'; annotation_id: string; document_id: string; document_revision: string; revision: string; status: AnnotationStatus
  operation_id: string; previous_revision: string; reply: AnnotationReply; reply_sha256: string
}
export interface PendingAnnotation {
  actor: string; context: Context; operation: 'RV-04-THREAD-1' | 'RV-05-REPLY-1' | 'RV-06-DECISION-1'; key: string; document_id: string
  document_revision: string; annotation_id: string | null; expected_revision: string; body_sha256: string; fixed_body_sha256: string
  issue_watermark: string; evidence_watermark: string; status: AnnotationStatus | null
  expected_document_revision?: string; decision?: AnnotationDecisionKind
}
export type AnnotationDecisionKind = 'RESOLVE' | 'REOPEN'
export interface DecisionCreate { document_revision: string; expected_revision: string; expected_document_revision: string; decision: AnnotationDecisionKind; reason: string }
export interface AnnotationDecision {
  decision_id: string; annotation_id: string; actor_id: string; created_at: string; decision: AnnotationDecisionKind; reason: string
  previous_status: 'OPEN' | 'RESOLVED'; status: 'OPEN' | 'RESOLVED'; previous_revision: string; revision: string
}
export interface DecisionListing {
  schema_version: '1.0.0'; annotation_id: string; document_id: string; document_revision: string; revision: string; status: AnnotationStatus
  location_status: AnnotationLocation; total: string; complete: true; items: AnnotationDecision[]; listing_sha256: string
}
export interface DecisionReceipt {
  schema_version: '1.0.0'; annotation_id: string; document_id: string; document_revision: string; revision: string; status: AnnotationStatus
  operation_id: string; previous_revision: string; previous_document_revision: string; document_head_revision: string
  issue_watermark: string; evidence_watermark: string; invalidated_review_ids: string[]; decision: AnnotationDecision; decision_sha256: string
}
export type AnnotationRequest = AnnotationCreate | ReplyCreate | DecisionCreate
export type AnnotationWriteReceipt = AnnotationReceipt | ReplyReceipt | DecisionReceipt
export type SessionReader = () => MedicalSession
export const annotationStatusText: Record<AnnotationStatus, string> = {
  OPEN: '待处理', RESOLVED: '已解决', RETURNED: '已退回', REANCHOR_REQUIRED: '需要重新定位'
}
export function annotationContext(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '标注需要当前企业身份')
  validContext(session.current_context); return session.current_context
}
export function annotationAllowed(session: MedicalSession, snapshot: DocumentSnapshot, action: 'annotation.read' | 'annotation.write' | 'review.decide'): boolean {
  if (!session.current_context || snapshot.auth_epoch !== session.current_context.auth_epoch || snapshot.governance_epoch !== session.current_context.governance_epoch) return false
  return session.action_summary.some(item => item.action === action && (item.project_id === null || item.project_id === snapshot.project_id) && (
    item.resource_kind === 'ENTERPRISE' && item.resource_id === session.current_context?.tenant_id && item.project_id === null
    || item.resource_kind === 'PROJECT' && item.resource_id === snapshot.project_id && item.project_id === snapshot.project_id
    || item.resource_kind === 'DOCUMENT' && (item.resource_id === null || item.resource_id === snapshot.document_id)
  ))
}
export function captureAnnotationSession(session: MedicalSession): { actor: string; context: Context } {
  return immutableDocumentValue({ actor: session.user.user_id, context: { ...annotationContext(session) } })
}
export function assertAnnotationSession(session: MedicalSession, original: { actor: string; context: Context }, signal: AbortSignal): void {
  signal.throwIfAborted()
  if (session.user.user_id !== original.actor || canonicalDocumentValue(annotationContext(session)) !== canonicalDocumentValue(original.context)) {
    fail('CONTEXT_CHANGED', '标注处理期间当前企业身份或授权已经变化')
  }
}
export async function annotationDigest(value: unknown): Promise<string> {
  return documentBytesDigest(new TextEncoder().encode(canonicalDocumentValue(value)))
}
export function annotationContent(value: string): void {
  canonicalDocumentValue(value)
  // Match Java String.isBlank without normalizing the user's original content.
  if (!value || /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*$/u.test(value)
      || Array.from(value).length > 16000 || /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f-\u009f]/u.test(value)) {
    fail('SCHEMA_INVALID', '请输入不超过16000个字符的有效标注内容，原空格与换行会保留')
  }
}
export function validateAnnotationRequest(operation: PendingAnnotation['operation'], value: AnnotationRequest): void {
  validate((operation === 'RV-04-THREAD-1' ? annotationSchema : operation === 'RV-06-DECISION-1' ? decisionSchema : replySchema) + 'Create', value)
  annotationContent('reason' in value ? value.reason : value.content); revision(value.expected_revision)
  if ('reason' in value) { revision(value.expected_document_revision); if (value.expected_document_revision === '0' || value.expected_document_revision === '18446744073709551615') fail('REVISION_LIMIT', '当前文稿修订无法继续写入') }
  if (value.expected_revision === '0' || value.expected_revision === '18446744073709551615') fail('REVISION_LIMIT', '当前修订无法继续写入')
  if (new TextEncoder().encode(canonicalDocumentValue(value)).length > (operation === 'RV-04-THREAD-1' ? 1048576 : 65536)) fail('FILE_TOO_LARGE', '标注请求超过允许大小')
}
export async function verifyAnnotationSelection(body: DocumentBody, documentRevision: string, selection: Selection, signal: AbortSignal): Promise<void> {
  validate(documentReference + 'Selection', selection); signal.throwIfAborted()
  if (selection.document_revision !== documentRevision) fail('SELECTION_STALE', '选区不属于当前固定正文版本')
  const requests: SelectionRequest[] = selection.segments.map(segment => {
    if (segment.kind === 'IMAGE') return { kind: 'IMAGE', node_id: segment.node_id }
    if (segment.kind === 'TABLE') return { kind: 'TABLE', node_id: segment.node_id, cell_ids: [...segment.cell_ids] }
    const node = body.nodes.find(item => item.node_id === segment.node_id)
    if (!node || node.content.kind !== 'TEXT') fail('SELECTION_STALE', '原文文字位置已经不可核验')
    const points = Array.from(node.content.text)
    if (segment.start_cp >= segment.end_cp || segment.end_cp > points.length) fail('SELECTION_INVALID', '文字选区超出固定原文范围')
    return { kind: 'TEXT', node_id: segment.node_id, start_utf16: points.slice(0, segment.start_cp).join('').length,
      end_utf16: points.slice(0, segment.end_cp).join('').length }
  })
  const checked = await createDocumentSelection(body, documentRevision, requests, signal)
  signal.throwIfAborted()
  if (canonicalDocumentValue(checked) !== canonicalDocumentValue(selection)) fail('SELECTION_STALE', '选区摘要、文字或表格范围与已保存正文不一致')
}
export function selectionSummary(body: DocumentBody, selection: Selection): string {
  return selection.segments.map(segment => {
    const node = body.nodes.find(item => item.node_id === segment.node_id)
    if (segment.kind === 'TEXT' && node?.content.kind === 'TEXT') {
      const text = Array.from(node.content.text).slice(segment.start_cp, segment.end_cp)
      return '“' + text.slice(0, 90).join('') + (text.length > 90 ? '…' : '') + '”'
    }
    if (segment.kind === 'IMAGE' && node?.content.kind === 'IMAGE') return '图片：' + (node.content.alt_text || node.content.caption || '未命名图片')
    if (segment.kind === 'TABLE' && node?.content.kind === 'TABLE') return '表格：' + (node.content.caption || '未命名表格') + '，' + segment.cell_ids.length + ' 个单元格'
    return '原位置暂不可展示'
  }).join('；')
}
async function verifyFixed(snapshot: DocumentSnapshot, body: DocumentBody, signal: AbortSignal): Promise<void> {
  validate(documentReference + 'DocumentSnapshot', snapshot); validate(documentReference + 'DocumentBody', body)
  if (body.template_version !== snapshot.template_version || await annotationDigest(body) !== snapshot.body.sha256) fail('CONTENT_MISMATCH', '标注必须使用完整已保存正文')
  signal.throwIfAborted()
}
function positive(value: string): void { revision(value); if (value === '0') fail('CONTENT_MISMATCH', '标注修订必须大于零') }
function chronological(value: string): string {
  // Service timestamps are UTC instants, with up to nanosecond precision.
  const match = /^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d{1,9}))?Z$/.exec(value)
  if (!match) fail('CONTENT_MISMATCH', '标注时间不是服务端固定UTC时间')
  return match[1] + '.' + (match[2] ?? '').padEnd(9, '0')
}
function checkOrder<T>(items: T[], id: (item: T) => string, time: (item: T) => string): void {
  const seen = new Set<string>(); let previous: string | null = null
  for (const item of items) {
    const key = chronological(time(item)) + ':' + id(item)
    if (seen.has(id(item)) || previous !== null && previous >= key) fail('CONTENT_MISMATCH', '标注集合包含重复身份或无效顺序')
    seen.add(id(item)); previous = key
  }
}
export async function verifyAnnotation(snapshot: DocumentSnapshot, body: DocumentBody, value: Annotation, signal: AbortSignal): Promise<void> {
  validate(annotationSchema + 'Annotation', value); positive(value.revision); revision(value.reply_count); annotationContent(value.content)
  if (value.document_revision !== snapshot.document_revision || await annotationDigest(value.selection) !== value.selection_sha256) fail('CONTENT_MISMATCH', '标注版本或原选区摘要不一致')
  await verifyAnnotationSelection(body, snapshot.document_revision, value.selection, signal)
}
export async function verifyAnnotationListing(snapshot: DocumentSnapshot, body: DocumentBody, value: AnnotationListing, resourceRevision: string | null, signal: AbortSignal): Promise<void> {
  validate(annotationSchema + 'Listing', value); await verifyFixed(snapshot, body, signal)
  positive(value.revision); revision(value.total); revision(value.issue_watermark); revision(value.evidence_watermark)
  const { listing_sha256: digest, ...content } = value
  if (value.document_id !== snapshot.document_id || value.document_revision !== snapshot.document_revision || value.revision !== resourceRevision
      || BigInt(value.revision) < BigInt(snapshot.revision) || BigInt(value.issue_watermark) < BigInt(snapshot.issue_watermark)
      || BigInt(value.evidence_watermark) < BigInt(snapshot.evidence_watermark) || value.status_filter !== null || value.total !== String(value.items.length)
      || await annotationDigest(content) !== digest) fail('CONTENT_MISMATCH', '标注列表与固定正文、数量或完整摘要不一致')
  checkOrder(value.items, item => item.annotation_id, item => item.created_at)
  for (const item of value.items) await verifyAnnotation(snapshot, body, item, signal)
  signal.throwIfAborted()
}
export async function verifyReplyListing(snapshot: DocumentSnapshot, annotationId: string, value: ReplyListing, resourceRevision: string | null, signal: AbortSignal): Promise<void> {
  validate(replySchema + 'Listing', value); positive(value.revision); revision(value.total)
  const { listing_sha256: digest, ...content } = value
  if (value.document_id !== snapshot.document_id || value.document_revision !== snapshot.document_revision || value.annotation_id !== annotationId
      || value.revision !== resourceRevision || value.total !== String(value.items.length) || await annotationDigest(content) !== digest) fail('CONTENT_MISMATCH', '回复列表与原标注、版本、数量或摘要不一致')
  checkOrder(value.items, item => item.reply_id, item => item.created_at)
  for (const item of value.items) {
    annotationContent(item.content)
    if (item.annotation_id !== annotationId) fail('CONTENT_MISMATCH', '回复不属于当前标注')
  }
  signal.throwIfAborted()
}
function pendingKey(session: MedicalSession, documentId: string): string {
  validate(annotationSchema + 'Id', documentId)
  return 'medical.annotation.write:' + session.user.user_id + ':' + annotationContext(session).tenant_id + ':' + documentId
}
function checkPending(session: MedicalSession, value: PendingAnnotation): void {
  validate(pendingSchema, value); validContext(value.context); positive(value.expected_revision)
  if (value.operation === 'RV-06-DECISION-1') { positive(value.expected_document_revision!); if (!value.status || !decisionTransition(value.status, value.decision!)) fail('CONTENT_MISMATCH', '原人工处理状态无效') }
  revision(value.issue_watermark); revision(value.evidence_watermark)
  if (value.actor !== session.user.user_id || value.context.tenant_id !== annotationContext(session).tenant_id
      || (value.operation === 'RV-04-THREAD-1' ? value.annotation_id !== null || value.status !== null : value.annotation_id === null || value.status === null)) fail('CONTENT_MISMATCH', '原标注请求身份无效')
}
export function readPendingAnnotation(session: MedicalSession, documentId: string): PendingAnnotation | null {
  const raw = sessionStorage.getItem(pendingKey(session, documentId)); if (raw === null) return null
  let value: unknown
  try { value = JSON.parse(raw) } catch { fail('CONTENT_MISMATCH', '原标注请求记录损坏，不能创建替代请求') }
  const result = validate<PendingAnnotation>(pendingSchema, value); checkPending(session, result)
  if (result.document_id !== documentId) fail('CONTENT_MISMATCH', '原标注请求属于其他文稿')
  return immutableDocumentValue(result)
}
export async function prepareAnnotation(session: SessionReader, snapshot: DocumentSnapshot, body: DocumentBody, request: AnnotationRequest,
  parent: ReplyListing | DecisionListing | null, signal: AbortSignal): Promise<PendingAnnotation> {
  const original = captureAnnotationSession(session()); const operation = 'decision' in request ? 'RV-06-DECISION-1' : parent === null ? 'RV-04-THREAD-1' : 'RV-05-REPLY-1'
  validateAnnotationRequest(operation, request); await verifyFixed(snapshot, body, signal)
  if (!annotationAllowed(session(), snapshot, operation === 'RV-06-DECISION-1' ? 'review.decide' : 'annotation.write')) fail('ACCESS_DENIED', '当前身份没有此文稿的标注写入权限')
  if (request.document_revision !== snapshot.document_revision || parent === null && (snapshot.signature_ids.length > 0 || request.expected_revision !== snapshot.revision)
      || parent !== null && (parent.document_id !== snapshot.document_id || parent.document_revision !== snapshot.document_revision || parent.revision !== request.expected_revision)) fail('REVISION_CONFLICT', '请读取当前文稿与标注修订后再提交')
  if ('decision' in request && (!parent || snapshot.signature_ids.length > 0 || parent.location_status !== 'CURRENT_VERSION'
      || request.expected_document_revision !== snapshot.revision || !decisionTransition(parent.status, request.decision))) fail('ACTION_STALE', '只允许当前未签发正文的待处理标注解决，或已解决标注重开')
  if (parent === null) await verifyAnnotationSelection(body, snapshot.document_revision, (request as AnnotationCreate).selection, signal)
  const hash = await annotationDigest(request); assertAnnotationSession(session(), original, signal)
  if (sessionStorage.getItem('medical.annotation.reanchor:' + session().user.user_id + ':' + annotationContext(session()).tenant_id + ':' + snapshot.document_id) !== null) fail('IDEMPOTENCY_CONFLICT', '请先核查原重新定位请求')
  if (sessionStorage.getItem('medical.annotation.reconciliation:' + session().user.user_id + ':' + annotationContext(session()).tenant_id + ':' + snapshot.document_id) !== null) fail('IDEMPOTENCY_CONFLICT', '请先核查原旧位置核对请求')
  const previous = readPendingAnnotation(session(), snapshot.document_id)
  if (previous) {
    if (previous.body_sha256 !== hash || previous.operation !== operation || previous.annotation_id !== (parent?.annotation_id ?? null)
        || canonicalDocumentValue(previous.context) !== canonicalDocumentValue(original.context)) fail('IDEMPOTENCY_CONFLICT', '请先核查原标注请求，不能替换内容或请求标识')
    return previous
  }
  const pending: PendingAnnotation = { ...original, operation, key: crypto.randomUUID(), document_id: snapshot.document_id,
    document_revision: snapshot.document_revision, annotation_id: parent?.annotation_id ?? null, expected_revision: request.expected_revision,
    body_sha256: hash, fixed_body_sha256: snapshot.body.sha256, issue_watermark: snapshot.issue_watermark,
    evidence_watermark: snapshot.evidence_watermark, status: parent?.status ?? null,
    ...('decision' in request ? { expected_document_revision: request.expected_document_revision, decision: request.decision } : {}) }
  checkPending(session(), pending); assertAnnotationSession(session(), original, signal)
  sessionStorage.setItem(pendingKey(session(), snapshot.document_id), canonicalDocumentValue(pending))
  return immutableDocumentValue(pending)
}
export async function verifyAnnotationRequest(session: SessionReader, pending: PendingAnnotation, snapshot: DocumentSnapshot, body: DocumentBody,
  request: AnnotationRequest, signal: AbortSignal): Promise<void> {
  checkPending(session(), pending); assertAnnotationSession(session(), pending, signal); validateAnnotationRequest(pending.operation, request)
  if (!annotationAllowed(session(), snapshot, pending.operation === 'RV-06-DECISION-1' ? 'review.decide' : 'annotation.write') || pending.document_id !== snapshot.document_id
      || pending.document_revision !== snapshot.document_revision || pending.fixed_body_sha256 !== snapshot.body.sha256
      || request.document_revision !== pending.document_revision || request.expected_revision !== pending.expected_revision
      || 'decision' in request && (request.expected_document_revision !== pending.expected_document_revision || request.decision !== pending.decision)
      || canonicalDocumentValue(readPendingAnnotation(session(), pending.document_id)) !== canonicalDocumentValue(pending)
      || await annotationDigest(request) !== pending.body_sha256) fail('IDEMPOTENCY_CONFLICT', '恢复需要原内容、选区、固定版本和请求标识')
  await verifyFixed(snapshot, body, signal)
  if (pending.operation === 'RV-04-THREAD-1') await verifyAnnotationSelection(body, pending.document_revision, (request as AnnotationCreate).selection, signal)
  assertAnnotationSession(session(), pending, signal)
  if (!annotationAllowed(session(), snapshot, pending.operation === 'RV-06-DECISION-1' ? 'review.decide' : 'annotation.write')) fail('ACCESS_DENIED', '当前身份已不具备此文稿的写入权限')
}
export async function verifyAnnotationReceipt(pending: PendingAnnotation, snapshot: DocumentSnapshot, body: DocumentBody, request: AnnotationRequest,
  value: AnnotationWriteReceipt, resourceRevision: string | null, signal: AbortSignal): Promise<void> {
  if (pending.operation === 'RV-06-DECISION-1') { await verifyDecisionReceipt(pending, request as DecisionCreate, value as DecisionReceipt, resourceRevision, signal); return }
  const create = pending.operation === 'RV-04-THREAD-1'
  validate((create ? annotationSchema : replySchema) + 'Receipt', value); positive(value.revision)
  if (value.document_id !== pending.document_id || value.document_revision !== pending.document_revision || value.previous_revision !== pending.expected_revision
      || value.revision !== resourceRevision || value.revision !== String(BigInt(pending.expected_revision) + 1n)) fail('CONTENT_MISMATCH', '标注回执与原请求修订不一致')
  if (create) {
    const receipt = value as AnnotationReceipt; const item = receipt.annotation
    await verifyAnnotation(snapshot, body, item, signal)
    revision(receipt.issue_watermark); revision(receipt.evidence_watermark)
    if (receipt.issue_watermark !== String(BigInt(pending.issue_watermark) + 1n) || receipt.evidence_watermark !== pending.evidence_watermark
        || item.actor_id !== pending.actor || item.content !== (request as AnnotationCreate).content || item.status !== 'OPEN' || item.revision !== '1' || item.reply_count !== '0'
        || item.location_status !== 'CURRENT_VERSION' || canonicalDocumentValue(item.selection) !== canonicalDocumentValue((request as AnnotationCreate).selection)
        || await annotationDigest(item) !== receipt.annotation_sha256) fail('CONTENT_MISMATCH', '新标注回执的选区、作者、内容或问题水位不一致')
  } else {
    const receipt = value as ReplyReceipt
    if (receipt.annotation_id !== pending.annotation_id || receipt.reply.annotation_id !== pending.annotation_id || receipt.status !== pending.status
        || receipt.reply.actor_id !== pending.actor || receipt.reply.content !== (request as ReplyCreate).content || await annotationDigest(receipt.reply) !== receipt.reply_sha256) fail('CONTENT_MISMATCH', '回复回执的原标注、状态、作者或内容不一致')
  }
  signal.throwIfAborted()
}
export function verifyAnnotationOperation(pending: PendingAnnotation, operation: Operation, receipt?: AnnotationWriteReceipt): void {
  validate('urn:medical:shared#/$defs/Operation', operation)
  if (operation.status_url !== '/api/operations/' + operation.operation_id
      || operation.resource_id !== (pending.annotation_id ?? pending.document_id)
      || receipt !== undefined && operation.operation_id !== receipt.operation_id) fail('CONTENT_MISMATCH', '原操作不属于当前标注请求')
}
export function clearPendingAnnotation(session: MedicalSession, pending: PendingAnnotation): void {
  checkPending(session, pending)
  if (canonicalDocumentValue(readPendingAnnotation(session, pending.document_id)) !== canonicalDocumentValue(pending)) fail('ACTION_STALE', '原标注恢复记录已经变化')
  sessionStorage.removeItem(pendingKey(session, pending.document_id))
}

// Raw drafts exist only in this tab's memory. Context invalidation discards them; web storage only holds PendingAnnotation.
export interface AnnotationDraft { content: string; replies: Record<string, string>; reasons?: Record<string, string>; request: AnnotationRequest | null }
const drafts = new Map<string, AnnotationDraft>()
export function annotationDraftKey(session: MedicalSession, documentId: string, documentRevision: string): string {
  return canonicalDocumentValue([captureAnnotationSession(session), documentId, documentRevision])
}
export function retainAnnotationDraft(key: string, value: AnnotationDraft): void { drafts.set(key, structuredClone(value)) }
export function resumeAnnotationDraft(key: string): AnnotationDraft | null { const value = drafts.get(key); drafts.delete(key); return value ?? null }
window.addEventListener('medical-context-invalidated', () => drafts.clear())

export function decisionTransition(status: AnnotationStatus, decision: AnnotationDecisionKind): boolean {
  return status === 'OPEN' && decision === 'RESOLVE' || status === 'RESOLVED' && decision === 'REOPEN'
}
function verifyDecision(item: AnnotationDecision, annotationId: string): void {
  validate(decisionSchema + 'Decision', item); annotationContent(item.reason); positive(item.previous_revision); positive(item.revision)
  if (item.annotation_id !== annotationId || !decisionTransition(item.previous_status, item.decision)
      || item.status !== (item.decision === 'RESOLVE' ? 'RESOLVED' : 'OPEN')
      || item.revision !== String(BigInt(item.previous_revision) + 1n)) fail('CONTENT_MISMATCH', '人工处理历史的标注、状态或修订不一致')
}
export async function verifyDecisionListing(snapshot: DocumentSnapshot, annotationId: string, value: DecisionListing, resourceRevision: string | null, signal: AbortSignal, lifecycle?: AnchorLifecycle[]): Promise<void> {
  validate(decisionSchema + 'Listing', value); positive(value.revision); revision(value.total)
  const { listing_sha256: digest, ...content } = value
  if (value.document_id !== snapshot.document_id || value.document_revision !== snapshot.document_revision || value.annotation_id !== annotationId
      || value.revision !== resourceRevision || value.total !== String(value.items.length) || await annotationDigest(content) !== digest) fail('CONTENT_MISMATCH', '人工处理历史与标注、版本、数量或摘要不一致')
  const seen = new Set<string>()
  let previous: AnnotationDecision | null = null
  for (const item of value.items) {
    verifyDecision(item, annotationId); chronological(item.created_at)
    if (seen.has(item.decision_id)) fail('CONTENT_MISMATCH', '人工处理历史包含重复身份')
    seen.add(item.decision_id)
    if (BigInt(item.revision) > BigInt(value.revision) || previous && (BigInt(item.previous_revision) < BigInt(previous.revision) || lifecycle === undefined && item.previous_status !== previous.status)) fail('CONTENT_MISMATCH', '人工处理历史的状态链或修订顺序不一致')
    previous = item
  }
  if (lifecycle !== undefined) {
    const events = lifecycle.filter(event => event.kind === 'DECISION')
    if (events.length !== value.items.length || events.some((event, index) => { const item = value.items[index]; return !item || event.previous_revision !== item.previous_revision || event.revision !== item.revision || event.previous_status !== item.previous_status || event.status !== item.status })) fail('CONTENT_MISMATCH', '人工决定与完整生命周期事件不一致')
    const last = lifecycle.at(-1)
    if ((last?.revision ?? '1') !== value.revision || (last?.status ?? 'OPEN') !== value.status) fail('CONTENT_MISMATCH', '人工决定读取与完整生命周期修订不一致')
  }
  if (lifecycle === undefined && previous && previous.status !== value.status) fail('CONTENT_MISMATCH', '当前状态与最后人工处理记录不一致')
  signal.throwIfAborted()
}
async function verifyDecisionReceipt(pending: PendingAnnotation, request: DecisionCreate, value: DecisionReceipt, resourceRevision: string | null, signal: AbortSignal): Promise<void> {
  validate(decisionSchema + 'Receipt', value); verifyDecision(value.decision, pending.annotation_id!)
  const item = value.decision
  if (value.document_id !== pending.document_id || value.document_revision !== pending.document_revision || value.annotation_id !== pending.annotation_id
      || value.previous_revision !== pending.expected_revision || value.revision !== resourceRevision || value.revision !== String(BigInt(pending.expected_revision) + 1n)
      || value.previous_document_revision !== pending.expected_document_revision || value.document_head_revision !== String(BigInt(pending.expected_document_revision!) + 1n)
      || item.previous_revision !== value.previous_revision || item.revision !== value.revision || item.previous_status !== pending.status || item.status !== value.status
      || item.actor_id !== pending.actor || item.decision !== pending.decision || item.decision !== request.decision || item.reason !== request.reason
      || value.issue_watermark !== String(BigInt(pending.issue_watermark) + 1n)
      || value.evidence_watermark !== pending.evidence_watermark || await annotationDigest(item) !== value.decision_sha256) fail('CONTENT_MISMATCH', '人工处理回执与原决定、理由、修订或水位不一致')
  signal.throwIfAborted()
}
