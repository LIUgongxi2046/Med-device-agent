import request from '@/utils/request'
import { envelope, fail, validate } from './protocol'
import type { MedicalSession } from './protocol'
import { readOperation } from './operations'
import { evaluationAllowed, evaluationContext, evaluationSchema, evaluationViewSchema, validateEvaluationAppend, validateEvaluationCreate,
  verifyEvaluationRead, verifyEvaluationReceipt, verifyEvaluationRequest, confirmEvaluationResult } from './evaluation-domain'
import type { EvaluationAppend, EvaluationCreate, EvaluationFinalize, EvaluationRead, EvaluationReceipt, EvaluationScores, PendingEvaluation } from './evaluation-domain'

export async function readEvaluation(session: MedicalSession, id: string, signal: AbortSignal): Promise<EvaluationRead> {
  validate(evaluationViewSchema + 'Id', id)
  if (!evaluationAllowed(session, 'evaluation.read', undefined, id)) fail('ACCESS_DENIED', '当前范围没有评测读取权限')
  const response = envelope(await request.get('/api/evaluation-batches/' + id, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': evaluationContext(session).context_revision } }))
  const view = await verifyEvaluationRead(session, id, response.data, response.resource_revision); signal.throwIfAborted(); return view
}
export async function readEvaluationScores(session: MedicalSession, view: EvaluationRead, signal: AbortSignal): Promise<EvaluationRead> {
  if (!evaluationAllowed(session, 'evaluation.read', view.definition.request.project_id, view.evaluation_id)) fail('ACCESS_DENIED', '当前范围没有评测历史读取权限')
  const response = envelope(await request.get('/api/evaluation-batches/' + view.evaluation_id + '/scores', { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': evaluationContext(session).context_revision } }))
  const scores = validate<EvaluationScores>(evaluationViewSchema + 'Scores', response.data)
  const result = await verifyEvaluationRead(session, view.evaluation_id, { ...scores, definition: view.definition }, response.resource_revision)
  signal.throwIfAborted(); return result
}
export async function submitEvaluation(session: MedicalSession, pending: PendingEvaluation, body: EvaluationCreate | EvaluationAppend | EvaluationFinalize, signal: AbortSignal): Promise<EvaluationReceipt> {
  if (pending.operation === 'EV-01') await validateEvaluationCreate(body)
  else if (pending.operation === 'EV-02') validateEvaluationAppend(body)
  else validate(evaluationSchema + 'Finalize', body)
  await verifyEvaluationRequest(session, pending, body); signal.throwIfAborted()
  const path = '/api/evaluation-batches' + (pending.operation === 'EV-01' ? '' : '/' + pending.target_id + (pending.operation === 'EV-02' ? '/scores' : '/finalize'))
  const raw = await request.post(path, body, { medical: 'envelope', signal, headers: {
    'X-Schema-Version': '1.0.0', 'X-Context-Revision': pending.context.context_revision, 'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token
  } })
  const response = envelope(raw)
  if (raw.status !== (pending.operation === 'EV-03' ? 200 : 201)) fail('CONTENT_MISMATCH', '评测写入HTTP状态与操作类型不一致')
  return verifyEvaluationReceipt(pending, response.data, response.resource_revision)
}
export async function recoverEvaluation(session: MedicalSession, pending: PendingEvaluation, operationId: string, receipt: EvaluationReceipt | null, signal: AbortSignal): Promise<EvaluationRead> {
  const operation = await readOperation(session, operationId, signal)
  if (operation.status !== 'COMPLETED' || operation.resource_id === null || pending.target_id !== null && operation.resource_id !== pending.target_id) fail('CONTENT_MISMATCH', '原评测操作尚未确认完成')
  const view = await readEvaluation(session, operation.resource_id, signal); signal.throwIfAborted()
  await confirmEvaluationResult(session, pending, operation, view, receipt); return view
}
