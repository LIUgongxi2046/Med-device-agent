import request from '@/utils/request'
import type { DocumentBody, DocumentSnapshot, Selection } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue, immutableDocumentValue } from './document-body'
import { annotationAllowed, annotationContent, annotationContext, annotationDigest, assertAnnotationSession, captureAnnotationSession, readPendingAnnotation } from './annotation-domain'
import type { Annotation, SessionReader } from './annotation-domain'
import { annotationSources } from './annotation-sources'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { lookupOperation, readOperation } from './operations'

export const anchorSchema = 'urn:medical:annotation-anchors:1.0.0#/$defs/'
export interface EffectiveAnchor {
  anchor_id: string; previous_anchor_id: string | null; document_revision: string; selection_artifact_id: string; selection_sha256: string; selection: Selection
  annotation_revision: string; actor_id: string; created_at: string; reason: string | null
}
export interface AnchorItem { annotation: Annotation; effective_anchor: EffectiveAnchor; anchor_revision: string; location_status: 'CURRENT_VERSION' | 'REANCHOR_REQUIRED' | 'ORIGINAL_VERSION' }
export interface AnchorListing {
  schema_version: '1.0.0'; document_id: string; document_revision: string; revision: string; issue_watermark: string; evidence_watermark: string
  complete: true; total: string; items: AnchorItem[]; listing_sha256: string
}
export interface ReanchorCreate {
  schema_version: '1.0.0'; document_revision: string; expected_revision: string; expected_document_revision: string
  expected_anchor_id: string; expected_anchor_revision: string; selection: Selection; reason: string; human_confirmed: true
}
export interface ReanchorReceipt {
  schema_version: '1.0.0'; annotation_id: string; document_id: string; document_revision: string; revision: string; status: 'OPEN'
  previous_revision: string; document_head_revision: string; previous_document_revision: string; issue_watermark: string; evidence_watermark: string
  operation_id: string; previous_anchor_id: string; previous_anchor_revision: string; effective_anchor_sha256: string; receipt_sha256: string; anchor_revision: string; effective_anchor: EffectiveAnchor; invalidated_review_ids: string[]
}
export interface PendingReanchor {
  actor: string; context: ReturnType<typeof annotationContext>; operation: 'RV-06-REANCHOR-1'; key: string; document_id: string
  annotation_id: string; document_revision: string; expected_revision: string; expected_document_revision: string; expected_anchor_id: string
  expected_anchor_revision: string; body_sha256: string; fixed_body_sha256: string; issue_watermark: string; evidence_watermark: string
}
function key(session: MedicalSession, documentId: string): string {
  return 'medical.annotation.reanchor:' + session.user.user_id + ':' + annotationContext(session).tenant_id + ':' + documentId
}
function checkPending(session: MedicalSession, value: PendingReanchor): void {
  validate('urn:medical:annotation-reanchor-write-view:1.0.0', value); validContext(value.context)
  if (value.actor !== session.user.user_id || value.context.tenant_id !== annotationContext(session).tenant_id) fail('CONTENT_MISMATCH', '重新定位恢复身份不一致')
  for (const field of ['expected_revision', 'expected_document_revision', 'expected_anchor_revision', 'issue_watermark', 'evidence_watermark'] as const) revision(value[field])
}
export function readPendingReanchor(session: MedicalSession, documentId: string): PendingReanchor | null {
  const raw = sessionStorage.getItem(key(session, documentId)); if (raw === null) return null
  let value: PendingReanchor
  try { value = JSON.parse(raw) } catch { fail('CONTENT_MISMATCH', '原重新定位记录损坏，不能创建替代请求') }
  checkPending(session, value)
  if (value.document_id !== documentId) fail('CONTENT_MISMATCH', '重新定位记录不属于此文稿')
  return immutableDocumentValue(value)
}
function headers(session: SessionReader, pending?: PendingReanchor) {
  const value = session()
  return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': annotationContext(value).context_revision,
    ...(pending ? { 'Idempotency-Key': pending.key, [value.csrf.header_name]: value.csrf.token } : {}) }
}
export async function verifyAnchor(anchor: EffectiveAnchor, signal: AbortSignal): Promise<void> {
  validate(anchorSchema + 'Anchor', anchor); revision(anchor.annotation_revision)
  if (anchor.annotation_revision === '0') fail('CONTENT_MISMATCH', '有效位置修订必须大于零')
  if (anchor.selection.document_revision !== anchor.document_revision || await annotationDigest(anchor.selection) !== anchor.selection_sha256) fail('CONTENT_MISMATCH', '有效位置的版本或选区摘要不一致')
  if (anchor.reason !== null) annotationContent(anchor.reason)
  signal.throwIfAborted()
}
export async function readAnnotationAnchors(session: SessionReader, snapshot: DocumentSnapshot, body: DocumentBody, signal: AbortSignal): Promise<AnchorListing> {
  const original = captureAnnotationSession(session()); assertAnnotationSession(session(), original, signal)
  if (!annotationAllowed(session(), snapshot, 'annotation.read')) fail('ACCESS_DENIED', '当前身份没有标注读取权限')
  const raw = await request.get('/api/documents/' + snapshot.document_id + '/annotation-anchors', { medical: 'envelope', signal, timeout: 60000,
    params: { document_revision: snapshot.document_revision }, headers: headers(session) })
  assertAnnotationSession(session(), original, signal)
  const response = envelope(raw), value = validate<AnchorListing>(anchorSchema + 'Listing', response.data)
  const { listing_sha256, ...content } = value
  if (raw.status !== 200 || value.document_id !== snapshot.document_id || value.document_revision !== snapshot.document_revision || value.revision !== response.resource_revision
      || BigInt(value.revision) < BigInt(snapshot.revision) || BigInt(value.issue_watermark) < BigInt(snapshot.issue_watermark)
      || BigInt(value.evidence_watermark) < BigInt(snapshot.evidence_watermark) || value.total !== String(value.items.length) || await annotationDigest(content) !== listing_sha256) fail('CONTENT_MISMATCH', '有效位置列表与固定正文或完整摘要不一致')
  if (await annotationDigest(body) !== snapshot.body.sha256) fail('CONTENT_MISMATCH', '有效位置必须使用完整已保存正文')
  const source = annotationSources(session, snapshot, body, signal), seen = new Set<string>()
  for (const item of value.items) {
    const annotation = item.annotation
    if (seen.has(annotation.annotation_id)) fail('CONTENT_MISMATCH', '有效位置列表包含重复标注')
    seen.add(annotation.annotation_id); annotationContent(annotation.content); revision(annotation.revision); revision(item.anchor_revision)
    if (annotation.selection.document_revision !== annotation.document_revision || await annotationDigest(annotation.selection) !== annotation.selection_sha256) fail('CONTENT_MISMATCH', '原始选区与原始版本不一致')
    await source(annotation.selection); await verifyAnchor(item.effective_anchor, signal); await source(item.effective_anchor.selection)
    if (annotation.revision === '0' || item.anchor_revision === '0' || BigInt(item.effective_anchor.annotation_revision) > BigInt(annotation.revision)) fail('CONTENT_MISMATCH', '有效位置与批注修订无效')
    if ((annotation.status === 'REANCHOR_REQUIRED') !== (item.location_status === 'REANCHOR_REQUIRED')) fail('CONTENT_MISMATCH', '待重新定位状态与位置投影不一致')
    if (item.location_status === 'CURRENT_VERSION' && (item.effective_anchor.document_revision !== snapshot.document_revision || annotation.status === 'REANCHOR_REQUIRED')
        || item.location_status === 'REANCHOR_REQUIRED' && annotation.status !== 'REANCHOR_REQUIRED') fail('CONTENT_MISMATCH', '有效位置状态不一致')
  }
  assertAnnotationSession(session(), original, signal); return immutableDocumentValue(value)
}
export async function prepareReanchor(session: SessionReader, snapshot: DocumentSnapshot, body: DocumentBody, item: AnchorItem, input: ReanchorCreate, signal: AbortSignal): Promise<PendingReanchor> {
  const original = captureAnnotationSession(session()); validate(anchorSchema + 'Create', input); annotationContent(input.reason)
  if (input.selection.document_revision !== input.document_revision) fail('SELECTION_STALE', '新选区必须属于目标固定正文')
  for (const value of [input.expected_revision, input.expected_document_revision, input.expected_anchor_revision]) { revision(value); if (value === '0' || value === '18446744073709551615') fail('REVISION_LIMIT', '当前修订无法继续重新定位') }
  if (!annotationAllowed(session(), snapshot, 'review.decide') || !annotationAllowed(session(), snapshot, 'annotation.read')) fail('ACCESS_DENIED', '当前身份没有人工重新定位权限')
  if (snapshot.signature_ids.length || input.document_revision !== snapshot.document_revision || input.expected_document_revision !== snapshot.revision
      || item.annotation.status !== 'REANCHOR_REQUIRED' || input.expected_revision !== item.annotation.revision || input.expected_anchor_id !== item.effective_anchor.anchor_id
      || input.expected_anchor_revision !== item.anchor_revision) fail('ACTION_STALE', '请使用当前未签发正文及待重新定位标注')
  const source = annotationSources(session, snapshot, body, signal)
  for (const selection of [item.annotation.selection, item.effective_anchor.selection]) {
    const originalSource = await source(selection)
    if (!annotationAllowed(session(), originalSource.snapshot, 'review.decide')) fail('ACCESS_DENIED', '没有原固定正文的人工审查权限')
  }
  await annotationSources(session, snapshot, body, signal)(input.selection)
  if (await annotationDigest(body) !== snapshot.body.sha256) fail('CONTENT_MISMATCH', '必须使用完整已保存正文')
  const hash = await annotationDigest(input); assertAnnotationSession(session(), original, signal)
  if (readPendingAnnotation(session(), snapshot.document_id)) fail('IDEMPOTENCY_CONFLICT', '请先核查原标注写入请求')
  if (sessionStorage.getItem('medical.annotation.reconciliation:' + session().user.user_id + ':' + annotationContext(session()).tenant_id + ':' + snapshot.document_id) !== null) fail('IDEMPOTENCY_CONFLICT', '请先核查原旧位置核对请求')
  const previous = readPendingReanchor(session(), snapshot.document_id)
  if (previous) {
    if (previous.body_sha256 !== hash || previous.annotation_id !== item.annotation.annotation_id || canonicalDocumentValue(previous.context) !== canonicalDocumentValue(original.context)) fail('IDEMPOTENCY_CONFLICT', '请保留原重新定位请求和选区')
    return previous
  }
  const pending: PendingReanchor = { ...original, operation: 'RV-06-REANCHOR-1', key: crypto.randomUUID(), document_id: snapshot.document_id,
    annotation_id: item.annotation.annotation_id, document_revision: snapshot.document_revision, expected_revision: input.expected_revision,
    expected_document_revision: input.expected_document_revision, expected_anchor_id: input.expected_anchor_id, expected_anchor_revision: input.expected_anchor_revision,
    body_sha256: hash, fixed_body_sha256: snapshot.body.sha256, issue_watermark: snapshot.issue_watermark, evidence_watermark: snapshot.evidence_watermark }
  checkPending(session(), pending); assertAnnotationSession(session(), original, signal)
  sessionStorage.setItem(key(session(), snapshot.document_id), canonicalDocumentValue(pending)); return immutableDocumentValue(pending)
}
export async function verifyReanchorRequest(session: SessionReader, pending: PendingReanchor, snapshot: DocumentSnapshot, body: DocumentBody, input: ReanchorCreate, signal: AbortSignal): Promise<void> {
  checkPending(session(), pending); assertAnnotationSession(session(), pending, signal); validate(anchorSchema + 'Create', input); annotationContent(input.reason)
  if (input.selection.document_revision !== input.document_revision) fail('SELECTION_STALE', '新选区必须属于目标固定正文')
  if (!annotationAllowed(session(), snapshot, 'review.decide') || pending.document_id !== snapshot.document_id || pending.document_revision !== snapshot.document_revision
      || input.document_revision !== pending.document_revision || input.expected_revision !== pending.expected_revision
      || input.expected_document_revision !== pending.expected_document_revision || input.expected_anchor_id !== pending.expected_anchor_id || input.expected_anchor_revision !== pending.expected_anchor_revision
      || pending.fixed_body_sha256 !== snapshot.body.sha256 || await annotationDigest(body) !== pending.fixed_body_sha256 || await annotationDigest(input) !== pending.body_sha256
      || canonicalDocumentValue(readPendingReanchor(session(), pending.document_id)) !== canonicalDocumentValue(pending)) fail('IDEMPOTENCY_CONFLICT', '恢复需要相同的原选区、理由、固定正文和请求标识')
  await annotationSources(session, snapshot, body, signal)(input.selection); assertAnnotationSession(session(), pending, signal)
}
export async function verifyReanchorReceipt(pending: PendingReanchor, input: ReanchorCreate, value: ReanchorReceipt, resourceRevision: string | null, signal: AbortSignal): Promise<void> {
  validate(anchorSchema + 'Receipt', value); await verifyAnchor(value.effective_anchor, signal)
  const anchor = value.effective_anchor
  const { receipt_sha256, ...receiptContent } = value
  if (value.document_id !== pending.document_id || value.document_revision !== pending.document_revision || value.annotation_id !== pending.annotation_id || value.status !== 'OPEN'
      || value.previous_revision !== pending.expected_revision || value.revision !== resourceRevision || value.revision !== String(BigInt(pending.expected_revision) + 1n)
      || value.previous_document_revision !== pending.expected_document_revision || value.document_head_revision !== String(BigInt(pending.expected_document_revision) + 1n)
      || await annotationDigest(receiptContent) !== receipt_sha256 || await annotationDigest(anchor) !== value.effective_anchor_sha256
      || value.previous_anchor_revision !== pending.expected_anchor_revision || anchor.previous_anchor_id !== pending.expected_anchor_id
      || value.previous_anchor_id !== pending.expected_anchor_id || value.anchor_revision !== String(BigInt(pending.expected_anchor_revision) + 1n)
      || anchor.annotation_revision !== value.revision || anchor.document_revision !== input.document_revision || anchor.actor_id !== pending.actor || anchor.reason !== input.reason
      || canonicalDocumentValue(anchor.selection) !== canonicalDocumentValue(input.selection) || value.issue_watermark !== String(BigInt(pending.issue_watermark) + 1n)
      || value.evidence_watermark !== pending.evidence_watermark) fail('CONTENT_MISMATCH', '重新定位回执与原请求、人工确认或水位不一致')
  signal.throwIfAborted()
}
export async function submitReanchor(session: SessionReader, pending: PendingReanchor, snapshot: DocumentSnapshot, body: DocumentBody, input: ReanchorCreate, signal: AbortSignal): Promise<ReanchorReceipt> {
  const frozen = immutableDocumentValue(input); await verifyReanchorRequest(session, pending, snapshot, body, frozen, signal)
  const raw = await request.post('/api/annotation-threads/' + pending.annotation_id + '/reanchors', frozen,
    { medical: 'envelope', signal, timeout: 60000, headers: headers(session, pending) })
  assertAnnotationSession(session(), pending, signal); const response = envelope(raw)
  if (raw.status !== 201) fail('CONTENT_MISMATCH', '重新定位没有返回完整原回执')
  const receipt = validate<ReanchorReceipt>(anchorSchema + 'Receipt', response.data)
  await verifyReanchorReceipt(pending, frozen, receipt, response.resource_revision, signal)
  assertAnnotationSession(session(), pending, signal); return immutableDocumentValue(receipt)
}
export async function lookupReanchor(session: SessionReader, pending: PendingReanchor, signal: AbortSignal) {
  checkPending(session(), pending); assertAnnotationSession(session(), pending, signal)
  const value = await lookupOperation(session(), pending.operation, pending.key, signal)
  assertAnnotationSession(session(), pending, signal)
  if (value.operation && value.operation.resource_id !== pending.annotation_id) fail('CONTENT_MISMATCH', '原操作不属于这条标注')
  return value
}
export async function settleReanchor(session: SessionReader, pending: PendingReanchor, snapshot: DocumentSnapshot, body: DocumentBody, input: ReanchorCreate, receipt: ReanchorReceipt, signal: AbortSignal): Promise<AnchorListing> {
  await verifyReanchorRequest(session, pending, snapshot, body, input, signal); await verifyReanchorReceipt(pending, input, receipt, receipt.revision, signal)
  const operation = await readOperation(session(), receipt.operation_id, signal); assertAnnotationSession(session(), pending, signal)
  if (operation.status !== 'COMPLETED' || operation.resource_id !== pending.annotation_id) fail('ACTION_STALE', '原重新定位操作尚未核验完成')
  const listing = await readAnnotationAnchors(session, snapshot, body, signal)
  const current = listing.items.find(item => item.annotation.annotation_id === pending.annotation_id)
  if (!current || BigInt(current.annotation.revision) < BigInt(receipt.revision) || BigInt(listing.revision) < BigInt(receipt.document_head_revision)
      || BigInt(listing.issue_watermark) < BigInt(receipt.issue_watermark)) fail('CONTENT_MISMATCH', '重新定位尚未取得完整重读结果')
  // Immutable history readback is required even when a later reanchor has already changed the current pointer.
  await verifyReanchorHistoryReceipt(session, pending, receipt, signal)
  assertAnnotationSession(session(), pending, signal)
  if (canonicalDocumentValue(readPendingReanchor(session(), pending.document_id)) !== canonicalDocumentValue(pending)) fail('ACTION_STALE', '原恢复身份已变化')
  sessionStorage.removeItem(key(session(), pending.document_id)); return listing
}
async function verifyReanchorHistoryReceipt(session: SessionReader, pending: PendingReanchor, receipt: ReanchorReceipt, signal: AbortSignal): Promise<void> {
  const history = await readAnchorHistory(session, pending.annotation_id, signal)
  if (history.document_id !== pending.document_id || BigInt(history.revision) < BigInt(receipt.revision) || !history.items.some(anchor => canonicalDocumentValue(anchor) === canonicalDocumentValue(receipt.effective_anchor))) fail('CONTENT_MISMATCH', '原重新定位未出现在不可变位置历史中')
}
export interface AnchorLifecycle { kind: 'DECISION' | 'REPLY' | 'INVALIDATE' | 'REANCHOR'; previous_revision: string; revision: string; previous_status: Annotation['status']; status: Annotation['status'] }
export interface AnchorHistory { lifecycle: AnchorLifecycle[]; schema_version: '1.0.0'; annotation_id: string; document_id: string; document_revision: string; status: Annotation['status']; anchor_revision: string; effective_anchor_id: string; revision: string; complete: true; total: string; items: EffectiveAnchor[]; listing_sha256: string }
export async function readAnchorHistory(session: SessionReader, annotationId: string, signal: AbortSignal): Promise<AnchorHistory> {
  const original = captureAnnotationSession(session()); validate('urn:medical:annotation-threads:1.0.0#/$defs/Id', annotationId)
  const raw = await request.get('/api/annotation-threads/' + annotationId + '/anchors', { medical: 'envelope', signal, timeout: 60000, headers: headers(session) })
  assertAnnotationSession(session(), original, signal)
  const response = envelope(raw), value = validate<AnchorHistory>(anchorSchema + 'History', response.data)
  const { listing_sha256, ...content } = value
  if (raw.status !== 200 || value.annotation_id !== annotationId || value.revision !== response.resource_revision || value.total !== String(value.items.length)
      || await annotationDigest(content) !== listing_sha256) fail('CONTENT_MISMATCH', '位置历史不完整或摘要不一致')
  const ids = new Set<string>(); let previous = 0n; let previousId: string | null = null
  for (const anchor of value.items) {
    await verifyAnchor(anchor, signal)
    if (anchor.previous_anchor_id !== previousId || ids.has(anchor.anchor_id) || BigInt(anchor.annotation_revision) <= previous || BigInt(anchor.annotation_revision) > BigInt(value.revision)) fail('CONTENT_MISMATCH', '位置历史身份或顺序无效')
    ids.add(anchor.anchor_id); previous = BigInt(anchor.annotation_revision); previousId = anchor.anchor_id
  }
  let currentRevision = '1'; let currentStatus: Annotation['status'] = 'OPEN'
  for (const event of value.lifecycle) {
    if (event.previous_revision !== currentRevision || event.previous_status !== currentStatus || event.revision !== String(BigInt(currentRevision) + 1n)
        || event.kind === 'REPLY' && event.status !== event.previous_status
        || event.kind === 'INVALIDATE' && event.status !== 'REANCHOR_REQUIRED'
        || event.kind === 'REANCHOR' && (event.previous_status !== 'REANCHOR_REQUIRED' || event.status !== 'OPEN')
        || event.kind === 'DECISION' && !(event.previous_status === 'OPEN' && event.status === 'RESOLVED' || event.previous_status === 'RESOLVED' && event.status === 'OPEN')) fail('CONTENT_MISMATCH', '标注完整生命周期状态或修订链无效')
    currentRevision = event.revision; currentStatus = event.status
  }
  if (currentRevision !== value.revision || currentStatus !== value.status) fail('CONTENT_MISMATCH', '生命周期未覆盖当前标注状态')
  const reanchors = value.lifecycle.filter(event => event.kind === 'REANCHOR')
  if (reanchors.length !== value.items.length - 1 || reanchors.some((event, index) => event.revision !== value.items[index + 1]?.annotation_revision)) fail('CONTENT_MISMATCH', '位置历史与重新定位事件不一致')
  if (value.items[0]?.annotation_revision !== '1' || value.items[0]?.reason !== null || value.items.slice(1).some(anchor => anchor.reason === null)) fail('CONTENT_MISMATCH', '原始位置或人工理由历史无效')
  if (previousId !== value.effective_anchor_id || value.anchor_revision !== String(value.items.length) || value.items.at(-1)?.document_revision !== value.document_revision) fail('CONTENT_MISMATCH', '位置历史与当前有效指针不一致')
  assertAnnotationSession(session(), original, signal); return immutableDocumentValue(value)
}

export interface ReanchorDraft { reasons: Record<string, string>; selections: Record<string, Selection>; request: ReanchorCreate | null }
const reanchorDrafts = new Map<string, ReanchorDraft>()
export function retainReanchorDraft(key: string, value: ReanchorDraft): void { reanchorDrafts.set(key, structuredClone(value)) }
export function resumeReanchorDraft(key: string): ReanchorDraft | null { const value = reanchorDrafts.get(key); reanchorDrafts.delete(key); return value ?? null }
window.addEventListener('medical-context-invalidated', () => reanchorDrafts.clear())
