import request from '@/utils/request'
import type { DocumentBody, DocumentSnapshot } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue, documentReference, immutableDocumentValue } from './document-body'
import { annotationAllowed, annotationContent, annotationContext, annotationDigest, assertAnnotationSession, captureAnnotationSession, readPendingAnnotation } from './annotation-domain'
import type { SessionReader } from './annotation-domain'
import { annotationSources } from './annotation-sources'
import { readAnchorHistory, readAnnotationAnchors, readPendingReanchor } from './annotation-reanchors'
import type { AnchorHistory, AnchorItem, AnchorListing, PendingReanchor, ReanchorCreate } from './annotation-reanchors'
import { readDocumentSnapshot, readSavedDocumentRevision } from './documents'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { lookupOperation, readOperation } from './operations'

export const reconciliationSchema = 'urn:medical:annotation-anchor-reconciliations:1.0.0#/$defs/'
export type ReconciliationCreate = Omit<ReanchorCreate, 'selection'>
export interface PendingReconciliation extends Omit<PendingReanchor, 'operation'> {
  operation: 'RV-06-ANCHOR-RECONCILE-1'; previous_status: 'OPEN' | 'RESOLVED'; origin_anchor_sha256: string
}
export interface ReconciliationReceipt {
  schema_version: '1.0.0'; document_id: string; document_revision: string; annotation_id: string; operation_id: string
  previous_revision: string; revision: string; previous_status: 'OPEN' | 'RESOLVED'; status: 'REANCHOR_REQUIRED'
  previous_document_revision: string; document_head_revision: string; anchor_id: string; anchor_revision: string
  issue_watermark: string; evidence_watermark: string; transition_id: string; actor_id: string; created_at: string
  reason: string; invalidated_review_ids: string[]; receipt_sha256: string; origin_anchor_sha256: string
}
function storageKey(session: MedicalSession, documentId: string): string {
  return 'medical.annotation.reconciliation:' + session.user.user_id + ':' + annotationContext(session).tenant_id + ':' + documentId
}
function checkPending(session: MedicalSession, value: PendingReconciliation): void {
  validate('urn:medical:annotation-reconciliation-write-view:1.0.0', value); validContext(value.context)
  if (value.actor !== session.user.user_id || value.context.tenant_id !== annotationContext(session).tenant_id) fail('CONTENT_MISMATCH', '旧位置核对恢复身份不一致')
  for (const field of ['expected_revision', 'expected_document_revision', 'expected_anchor_revision', 'issue_watermark', 'evidence_watermark'] as const) revision(value[field])
}
export function readPendingReconciliation(session: MedicalSession, documentId: string): PendingReconciliation | null {
  const raw = sessionStorage.getItem(storageKey(session, documentId)); if (raw === null) return null
  let value: PendingReconciliation
  try { value = JSON.parse(raw) } catch { fail('CONTENT_MISMATCH', '原旧位置核对记录损坏，不能创建替代请求') }
  checkPending(session, value)
  if (value.document_id !== documentId) fail('CONTENT_MISMATCH', '旧位置核对记录不属于此文稿')
  return immutableDocumentValue(value)
}
function headers(session: SessionReader, pending: PendingReconciliation) {
  const value = session()
  return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': annotationContext(value).context_revision,
    'Idempotency-Key': pending.key, [value.csrf.header_name]: value.csrf.token }
}
export function legacyAnchorEligible(item: AnchorItem, snapshot: DocumentSnapshot, history: AnchorHistory | null): boolean {
  const anchor = item.effective_anchor
  return (item.annotation.status === 'OPEN' || item.annotation.status === 'RESOLVED') && !snapshot.signature_ids.length
    && anchor.document_revision !== snapshot.document_revision && item.location_status === 'ORIGINAL_VERSION'
    && anchor.anchor_id === item.annotation.annotation_id && anchor.previous_anchor_id === null && item.anchor_revision === '1'
    && anchor.document_revision === item.annotation.document_revision && anchor.selection_sha256 === item.annotation.selection_sha256
    && anchor.selection_artifact_id === item.annotation.selection_artifact_id && history !== null
    && history.annotation_id === item.annotation.annotation_id && history.revision === item.annotation.revision && history.status === item.annotation.status
    && history.items.length === 1 && history.anchor_revision === '1' && history.effective_anchor_id === anchor.anchor_id
    && canonicalDocumentValue(history.items[0]) === canonicalDocumentValue(anchor)
    && history.lifecycle.every(event => event.kind === 'DECISION' || event.kind === 'REPLY')
}
async function originProof(session: SessionReader, pending: PendingReconciliation, snapshot: DocumentSnapshot, body: DocumentBody, signal: AbortSignal): Promise<AnchorHistory> {
  const history = await readAnchorHistory(session, pending.annotation_id, signal), origin = history.items[0]
  if (history.document_id !== pending.document_id || !origin || origin.anchor_id !== pending.expected_anchor_id
      || await annotationDigest(origin) !== pending.origin_anchor_sha256) fail('CONTENT_MISMATCH', '原始位置证据发生变化')
  const source = await annotationSources(session, snapshot, body, signal)(origin.selection)
  if (!annotationAllowed(session(), source.snapshot, 'review.decide')) fail('ACCESS_DENIED', '没有原固定正文的人工审查权限')
  assertAnnotationSession(session(), pending, signal); return history
}
export async function prepareReconciliation(session: SessionReader, snapshot: DocumentSnapshot, body: DocumentBody, item: AnchorItem, input: ReconciliationCreate, signal: AbortSignal): Promise<PendingReconciliation> {
  const original = captureAnnotationSession(session()); validate(reconciliationSchema + 'Create', input); annotationContent(input.reason)
  validate(documentReference + 'DocumentSnapshot', snapshot); validate(documentReference + 'DocumentBody', body)
  if (body.template_version !== snapshot.template_version) fail('CONTENT_MISMATCH', '目标固定正文模板不一致')
  for (const value of [input.expected_revision, input.expected_document_revision, input.expected_anchor_revision, snapshot.issue_watermark]) {
    revision(value); if (value === '18446744073709551615') fail('REVISION_LIMIT', '当前修订无法继续核对')
  }
  if (!annotationAllowed(session(), snapshot, 'review.decide') || !annotationAllowed(session(), snapshot, 'annotation.read')) fail('ACCESS_DENIED', '当前身份没有人工核对权限')
  const history = await readAnchorHistory(session, item.annotation.annotation_id, signal)
  if (!legacyAnchorEligible(item, snapshot, history) || input.document_revision !== snapshot.document_revision || input.expected_document_revision !== snapshot.revision
      || input.expected_revision !== item.annotation.revision || input.expected_anchor_id !== item.effective_anchor.anchor_id || input.expected_anchor_revision !== item.anchor_revision) fail('ACTION_STALE', '请使用当前未签发正文及尚未核对的原始旧位置批注')
  if (await annotationDigest(body) !== snapshot.body.sha256) fail('CONTENT_MISMATCH', '必须使用完整已保存正文')
  const hash = await annotationDigest(input), originHash = await annotationDigest(item.effective_anchor); assertAnnotationSession(session(), original, signal)
  if (readPendingAnnotation(session(), snapshot.document_id) || readPendingReanchor(session(), snapshot.document_id)) fail('IDEMPOTENCY_CONFLICT', '请先核查原标注或重新定位请求')
  const pending: PendingReconciliation = { ...original, operation: 'RV-06-ANCHOR-RECONCILE-1', key: crypto.randomUUID(), document_id: snapshot.document_id,
    annotation_id: item.annotation.annotation_id, document_revision: snapshot.document_revision, expected_revision: input.expected_revision,
    expected_document_revision: input.expected_document_revision, expected_anchor_id: input.expected_anchor_id, expected_anchor_revision: input.expected_anchor_revision,
    body_sha256: hash, fixed_body_sha256: snapshot.body.sha256, issue_watermark: snapshot.issue_watermark, evidence_watermark: snapshot.evidence_watermark,
    origin_anchor_sha256: originHash, previous_status: item.annotation.status as 'OPEN' | 'RESOLVED' }
  await originProof(session, pending, snapshot, body, signal)
  const previous = readPendingReconciliation(session(), snapshot.document_id)
  if (previous) {
    if (previous.body_sha256 !== hash || previous.annotation_id !== pending.annotation_id || canonicalDocumentValue(previous.context) !== canonicalDocumentValue(original.context)) fail('IDEMPOTENCY_CONFLICT', '请保留原旧位置核对请求和原因')
    return previous
  }
  checkPending(session(), pending); assertAnnotationSession(session(), original, signal)
  sessionStorage.setItem(storageKey(session(), snapshot.document_id), canonicalDocumentValue(pending)); return immutableDocumentValue(pending)
}
export async function verifyReconciliationRequest(session: SessionReader, pending: PendingReconciliation, snapshot: DocumentSnapshot, body: DocumentBody, input: ReconciliationCreate, signal: AbortSignal): Promise<void> {
  checkPending(session(), pending); assertAnnotationSession(session(), pending, signal); validate(reconciliationSchema + 'Create', input); annotationContent(input.reason)
  validate(documentReference + 'DocumentSnapshot', snapshot); validate(documentReference + 'DocumentBody', body)
  if (body.template_version !== snapshot.template_version) fail('CONTENT_MISMATCH', '目标固定正文模板不一致')
  if (!annotationAllowed(session(), snapshot, 'review.decide') || !annotationAllowed(session(), snapshot, 'annotation.read')) fail('ACCESS_DENIED', '当前身份没有人工核对权限')
  if (pending.document_id !== snapshot.document_id || pending.document_revision !== snapshot.document_revision || input.document_revision !== pending.document_revision
      || input.expected_revision !== pending.expected_revision || input.expected_document_revision !== pending.expected_document_revision
      || input.expected_anchor_id !== pending.expected_anchor_id || input.expected_anchor_revision !== pending.expected_anchor_revision
      || pending.fixed_body_sha256 !== snapshot.body.sha256 || await annotationDigest(body) !== pending.fixed_body_sha256 || await annotationDigest(input) !== pending.body_sha256
      || canonicalDocumentValue(readPendingReconciliation(session(), pending.document_id)) !== canonicalDocumentValue(pending)) fail('IDEMPOTENCY_CONFLICT', '恢复需要相同的原理由、固定正文和请求标识')
  assertAnnotationSession(session(), pending, signal); await originProof(session, pending, snapshot, body, signal); assertAnnotationSession(session(), pending, signal)
}
export async function verifyReconciliationReceipt(pending: PendingReconciliation, input: ReconciliationCreate, value: ReconciliationReceipt, resourceRevision: string | null, signal: AbortSignal): Promise<void> {
  validate(reconciliationSchema + 'Receipt', value)
  for (const field of ['previous_revision', 'revision', 'previous_document_revision', 'document_head_revision', 'anchor_revision', 'issue_watermark', 'evidence_watermark'] as const) revision(value[field])
  const { receipt_sha256, ...content } = value
  if (await annotationDigest(content) !== receipt_sha256 || value.document_id !== pending.document_id || value.document_revision !== pending.document_revision
      || value.annotation_id !== pending.annotation_id || value.previous_revision !== pending.expected_revision || value.revision !== resourceRevision
      || value.revision !== String(BigInt(pending.expected_revision) + 1n) || value.previous_status !== pending.previous_status || value.status !== 'REANCHOR_REQUIRED'
      || value.previous_document_revision !== pending.expected_document_revision || value.document_head_revision !== String(BigInt(pending.expected_document_revision) + 1n)
      || value.anchor_id !== pending.expected_anchor_id || value.anchor_revision !== pending.expected_anchor_revision || value.actor_id !== pending.actor || value.reason !== input.reason
      || value.origin_anchor_sha256 !== pending.origin_anchor_sha256 || value.issue_watermark !== String(BigInt(pending.issue_watermark) + 1n)
      || value.evidence_watermark !== pending.evidence_watermark) fail('CONTENT_MISMATCH', '旧位置核对回执与原请求、操作者或水位不一致')
  signal.throwIfAborted()
}
export async function submitReconciliation(session: SessionReader, pending: PendingReconciliation, snapshot: DocumentSnapshot, body: DocumentBody, input: ReconciliationCreate, signal: AbortSignal): Promise<ReconciliationReceipt> {
  const frozen = immutableDocumentValue(input); await verifyReconciliationRequest(session, pending, snapshot, body, frozen, signal)
  const raw = await request.post('/api/annotation-threads/' + pending.annotation_id + '/anchor-reconciliations', frozen, { medical: 'envelope', signal, timeout: 60000, headers: headers(session, pending) })
  assertAnnotationSession(session(), pending, signal); const response = envelope(raw)
  if (raw.status !== 201) fail('CONTENT_MISMATCH', '旧位置核对没有返回完整原回执')
  const receipt = validate<ReconciliationReceipt>(reconciliationSchema + 'Receipt', response.data)
  await verifyReconciliationReceipt(pending, frozen, receipt, response.resource_revision, signal)
  assertAnnotationSession(session(), pending, signal); return immutableDocumentValue(receipt)
}
export async function lookupReconciliation(session: SessionReader, pending: PendingReconciliation, signal: AbortSignal) {
  checkPending(session(), pending); assertAnnotationSession(session(), pending, signal)
  const value = await lookupOperation(session(), pending.operation, pending.key, signal); assertAnnotationSession(session(), pending, signal)
  if (value.operation && value.operation.resource_id !== pending.annotation_id) fail('CONTENT_MISMATCH', '原操作不属于这条批注')
  return value
}
export async function settleReconciliation(session: SessionReader, pending: PendingReconciliation, snapshot: DocumentSnapshot, body: DocumentBody, input: ReconciliationCreate, receipt: ReconciliationReceipt, signal: AbortSignal): Promise<AnchorListing> {
  await verifyReconciliationRequest(session, pending, snapshot, body, input, signal); await verifyReconciliationReceipt(pending, input, receipt, receipt.revision, signal)
  const operation = await readOperation(session(), receipt.operation_id, signal); assertAnnotationSession(session(), pending, signal)
  if (operation.status !== 'COMPLETED' || operation.resource_id !== pending.annotation_id) fail('ACTION_STALE', '原旧位置核对操作尚未核验完成')
  const history = await originProof(session, pending, snapshot, body, signal)
  if (!history.lifecycle.some(event => event.kind === 'INVALIDATE' && event.previous_revision === receipt.previous_revision && event.revision === receipt.revision
      && event.previous_status === receipt.previous_status && event.status === receipt.status)) fail('CONTENT_MISMATCH', '原核对未出现在完整生命周期中')
  // Settlement follows the actual head even when the immutable original target is now historical.
  const current = await readDocumentSnapshot(session(), snapshot.project_id, snapshot.document_id, signal); assertAnnotationSession(session(), pending, signal)
  const saved = current.document_revision === snapshot.document_revision ? { snapshot: current, body }
    : await readSavedDocumentRevision(session(), snapshot.project_id, snapshot.document_id, current.document_revision, { maximumPages: 1000, maximumBytes: 104857600, pageSize: 100 }, signal)
  assertAnnotationSession(session(), pending, signal)
  const listing = await readAnnotationAnchors(session, saved.snapshot, saved.body, signal), item = listing.items.find(row => row.annotation.annotation_id === pending.annotation_id)
  if (!item || BigInt(item.annotation.revision) < BigInt(receipt.revision) || BigInt(listing.revision) < BigInt(receipt.document_head_revision)
      || BigInt(listing.issue_watermark) < BigInt(receipt.issue_watermark)) fail('CONTENT_MISMATCH', '核对后尚未取得完整当前状态')
  assertAnnotationSession(session(), pending, signal)
  if (canonicalDocumentValue(readPendingReconciliation(session(), pending.document_id)) !== canonicalDocumentValue(pending)) fail('ACTION_STALE', '原恢复身份已变化')
  sessionStorage.removeItem(storageKey(session(), pending.document_id)); return listing
}
export interface ReconciliationDraft { reasons: Record<string, string>; request: ReconciliationCreate | null }
const drafts = new Map<string, ReconciliationDraft>()
export function retainReconciliationDraft(key: string, value: ReconciliationDraft): void { drafts.set(key, structuredClone(value)) }
export function resumeReconciliationDraft(key: string): ReconciliationDraft | null { const value = drafts.get(key); drafts.delete(key); return value ?? null }
window.addEventListener('medical-context-invalidated', () => drafts.clear())
