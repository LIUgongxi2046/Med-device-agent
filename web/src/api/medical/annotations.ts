import request from '@/utils/request'
import { readAnnotationAnchors, readAnchorHistory } from './annotation-reanchors'
import { readDocumentSnapshot, readDocumentBody } from './documents'
import type { DocumentBody, DocumentSnapshot } from '../../../../contracts/typescript/src/interfaces'
import { canonicalDocumentValue, immutableDocumentValue } from './document-body'
import { envelope, fail } from './protocol'
import { lookupOperation, readOperation } from './operations'
import type { OperationLookup } from './operations'
import { annotationAllowed, annotationContext, annotationSchema, replySchema, decisionSchema, verifyDecisionListing, assertAnnotationSession, captureAnnotationSession,
  verifyAnnotationListing, verifyReplyListing, verifyAnnotationRequest, verifyAnnotationReceipt, verifyAnnotationOperation,
  clearPendingAnnotation } from './annotation-domain'
import type { AnnotationListing, AnnotationReceipt, AnnotationRequest, AnnotationWriteReceipt, DecisionListing, DecisionReceipt, ReplyListing, ReplyReceipt, PendingAnnotation, SessionReader } from './annotation-domain'
import { validate } from './protocol'

function headers(session: SessionReader, pending?: PendingAnnotation): Record<string, string> {
  const value = session()
  return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': annotationContext(value).context_revision,
    ...(pending ? { 'Idempotency-Key': pending.key, [value.csrf.header_name]: value.csrf.token } : {}) }
}
export async function readAnnotationThreads(session: SessionReader, snapshot: DocumentSnapshot, body: DocumentBody, signal: AbortSignal, effective = false): Promise<AnnotationListing> {
  if (effective) {
    const projection = await readAnnotationAnchors(session, snapshot, body, signal)
    // Projection was fully verified against each distinct source; keep immutable original annotation fields.
    return immutableDocumentValue({ ...projection, status_filter: null, items: projection.items.map(item => ({ ...item.annotation, location_status: item.location_status === 'CURRENT_VERSION' ? 'CURRENT_VERSION' as const : 'ORIGINAL_VERSION' as const, effective_anchor: item.effective_anchor })) })
  }
  const original = captureAnnotationSession(session()); assertAnnotationSession(session(), original, signal)
  if (!annotationAllowed(session(), snapshot, 'annotation.read')) fail('ACCESS_DENIED', '当前身份没有此文稿的标注读取权限')
  const raw = await request.get('/api/documents/' + snapshot.document_id + '/annotation-threads', { medical: 'envelope', signal,
    timeout: 60000, params: { document_revision: snapshot.document_revision }, headers: headers(session) })
  assertAnnotationSession(session(), original, signal); const response = envelope(raw)
  if (raw.status !== 200) fail('CONTENT_MISMATCH', '标注列表响应状态无效')
  const value = validate<AnnotationListing>(annotationSchema + 'Listing', response.data)
  await verifyAnnotationListing(snapshot, body, value, response.resource_revision, signal)
  assertAnnotationSession(session(), original, signal); return immutableDocumentValue(value)
}
export async function readAnnotationReplies(session: SessionReader, snapshot: DocumentSnapshot, annotationId: string, signal: AbortSignal, historical = false): Promise<ReplyListing> {
  validate(annotationSchema + 'Id', annotationId)
  const original = captureAnnotationSession(session()); assertAnnotationSession(session(), original, signal)
  if (!annotationAllowed(session(), snapshot, 'annotation.read')) fail('ACCESS_DENIED', '当前身份没有此文稿的标注读取权限')
  const raw = await request.get('/api/annotation-threads/' + annotationId + '/replies', { medical: 'envelope', signal, timeout: 60000, headers: headers(session) })
  assertAnnotationSession(session(), original, signal); const response = envelope(raw)
  if (raw.status !== 200) fail('CONTENT_MISMATCH', '回复列表响应状态无效')
  const value = validate<ReplyListing>(replySchema + 'Listing', response.data)
  const source = historical && value.document_revision !== snapshot.document_revision
    ? await readDocumentSnapshot(session(), snapshot.project_id, snapshot.document_id, signal, value.document_revision) : snapshot
  assertAnnotationSession(session(), original, signal)
  await verifyReplyListing(source, annotationId, value, response.resource_revision, signal)
  assertAnnotationSession(session(), original, signal); return immutableDocumentValue(value)
}
export async function submitAnnotation(session: SessionReader, pending: PendingAnnotation, snapshot: DocumentSnapshot, body: DocumentBody,
  input: AnnotationRequest, signal: AbortSignal): Promise<AnnotationWriteReceipt> {
  // Freeze before asynchronous hashing; never dispatch after cancellation/session change during a digest.
  const value = immutableDocumentValue(input)
  await verifyAnnotationRequest(session, pending, snapshot, body, value, signal)
  assertAnnotationSession(session(), pending, signal)
  const path = pending.operation === 'RV-04-THREAD-1' ? '/api/documents/' + pending.document_id + '/annotation-threads'
    : '/api/annotation-threads/' + pending.annotation_id + (pending.operation === 'RV-06-DECISION-1' ? '/decisions' : '/replies')
  const raw = await request.post(path, value, { medical: 'envelope', signal, timeout: 60000, headers: headers(session, pending) })
  assertAnnotationSession(session(), pending, signal); const response = envelope(raw)
  if (raw.status !== 201) fail('CONTENT_MISMATCH', '标注写入未返回完整原回执')
  const receipt = validate<AnnotationWriteReceipt>((pending.operation === 'RV-04-THREAD-1' ? annotationSchema : pending.operation === 'RV-06-DECISION-1' ? decisionSchema : replySchema) + 'Receipt', response.data)
  await verifyAnnotationReceipt(pending, snapshot, body, value, receipt, response.resource_revision, signal)
  assertAnnotationSession(session(), pending, signal); return immutableDocumentValue(receipt)
}
export async function lookupAnnotation(session: SessionReader, pending: PendingAnnotation, signal: AbortSignal): Promise<OperationLookup> {
  assertAnnotationSession(session(), pending, signal)
  const result = await lookupOperation(session(), pending.operation, pending.key, signal)
  assertAnnotationSession(session(), pending, signal)
  if (result.operation) verifyAnnotationOperation(pending, result.operation)
  return result
}
export async function settleAnnotation(session: SessionReader, pending: PendingAnnotation, snapshot: DocumentSnapshot, body: DocumentBody,
  input: AnnotationRequest, receipt: AnnotationWriteReceipt, signal: AbortSignal, effective = false): Promise<{ threads: AnnotationListing; replies: ReplyListing | null; decisions: DecisionListing | null }> {
  await verifyAnnotationRequest(session, pending, snapshot, body, input, signal)
  await verifyAnnotationReceipt(pending, snapshot, body, input, receipt, receipt.revision, signal)
  assertAnnotationSession(session(), pending, signal)
  const operation = await readOperation(session(), receipt.operation_id, signal)
  assertAnnotationSession(session(), pending, signal); verifyAnnotationOperation(pending, operation, receipt)
  if (operation.status !== 'COMPLETED') fail('ACTION_STALE', '原标注操作尚未确认完成，请保留原请求继续核查')
  // Recovery validates the immutable request against its old body, then reads today's separately verified discussion basis.
  const currentSnapshot = effective ? await readDocumentSnapshot(session(), snapshot.project_id, snapshot.document_id, signal) : snapshot
  assertAnnotationSession(session(), pending, signal)
  const currentBody = currentSnapshot.document_revision === snapshot.document_revision && currentSnapshot.body.sha256 === snapshot.body.sha256 ? body
    : await readDocumentBody(session(), currentSnapshot, { maximumPages: 1000, maximumBytes: 104857600, pageSize: 100 }, signal)
  assertAnnotationSession(session(), pending, signal)
  const threads = await readAnnotationThreads(session, currentSnapshot, currentBody, signal, effective)
  let replies: ReplyListing | null = null; let decisions: DecisionListing | null = null
  if (pending.operation === 'RV-04-THREAD-1') {
    const created = (receipt as AnnotationReceipt).annotation; const current = threads.items.find(item => item.annotation_id === created.annotation_id)
    if (!current || BigInt(threads.revision) < BigInt(receipt.revision) || BigInt(current.revision) < BigInt(created.revision)
        || ['document_revision', 'selection_artifact_id', 'selection_sha256', 'content', 'actor_id', 'created_at'].some(key => current[key as keyof typeof current] !== created[key as keyof typeof created])
        || canonicalDocumentValue(current.selection) !== canonicalDocumentValue(created.selection)) fail('CONTENT_MISMATCH', '原新标注尚未取得一致的完整重读结果')
  } else if (pending.operation === 'RV-06-DECISION-1') {
    const posted = receipt as DecisionReceipt
    decisions = await readAnnotationDecisions(session, currentSnapshot, posted.annotation_id, signal, effective, effective)
    const current = decisions.items.find(item => item.decision_id === posted.decision.decision_id)
    const thread = threads.items.find(item => item.annotation_id === posted.annotation_id)
    if (!current || !thread || BigInt(threads.revision) < BigInt(posted.document_head_revision)
        || BigInt(threads.issue_watermark) < BigInt(posted.issue_watermark) || BigInt(thread.revision) < BigInt(posted.revision)
        || BigInt(decisions.revision) < BigInt(posted.revision) || canonicalDocumentValue(current) !== canonicalDocumentValue(posted.decision)) fail('CONTENT_MISMATCH', '原人工处理记录尚未取得一致的完整重读结果')
    replies = await readAnnotationReplies(session, currentSnapshot, posted.annotation_id, signal, effective)
  } else {
    const posted = receipt as ReplyReceipt
    replies = await readAnnotationReplies(session, currentSnapshot, posted.annotation_id, signal, effective)
    const current = replies.items.find(item => item.reply_id === posted.reply.reply_id)
    if (!current || BigInt(replies.revision) < BigInt(posted.revision) || canonicalDocumentValue(current) !== canonicalDocumentValue(posted.reply)) fail('CONTENT_MISMATCH', '原回复尚未取得一致的完整重读结果')
  }
  assertAnnotationSession(session(), pending, signal); clearPendingAnnotation(session(), pending)
  return { threads, replies, decisions }
}

export async function readAnnotationDecisions(session: SessionReader, snapshot: DocumentSnapshot, annotationId: string, signal: AbortSignal, historical = false, effective = false): Promise<DecisionListing> {
  validate(annotationSchema + 'Id', annotationId)
  const original = captureAnnotationSession(session()); assertAnnotationSession(session(), original, signal)
  if (!annotationAllowed(session(), snapshot, 'annotation.read')) fail('ACCESS_DENIED', '当前身份没有此文稿的标注读取权限')
  const raw = await request.get('/api/annotation-threads/' + annotationId + '/decisions', { medical: 'envelope', signal, timeout: 60000, headers: headers(session) })
  assertAnnotationSession(session(), original, signal); const response = envelope(raw)
  if (raw.status !== 200) fail('CONTENT_MISMATCH', '人工处理历史响应状态无效')
  const value = validate<DecisionListing>(decisionSchema + 'Listing', response.data)
  const source = historical && value.document_revision !== snapshot.document_revision
    ? await readDocumentSnapshot(session(), snapshot.project_id, snapshot.document_id, signal, value.document_revision) : snapshot
  assertAnnotationSession(session(), original, signal)
  const history = effective ? await readAnchorHistory(session, annotationId, signal) : null
  if (history && (history.document_id !== value.document_id || history.document_revision !== value.document_revision || history.revision !== value.revision || history.status !== value.status)) fail('REVISION_CONFLICT', '人工决定与位置生命周期读取发生并发变化')
  await verifyDecisionListing(source, annotationId, value, response.resource_revision, signal, history?.lifecycle)
  assertAnnotationSession(session(), original, signal); return immutableDocumentValue(value)
}
