import request from '@/utils/request'
import type { AxiosResponse } from 'axios'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { readCurrentTask, taskActionAllowed } from './task-detail'
import type { CurrentTask } from './task-detail'
import { readTaskPlan } from './task-plans'
import { templateValueDigest } from './template-approval'
import { projectTextIssue } from './project-write'

export interface PlanDecisionBaseline {
  schema_version: '1.0.0'; tenant_id: string; project_id: string; task_id: string; task_revision: string
  plan_revision: string; plan_sha256: string; snapshot_id: string; template_version: string; profile_revision: string
  task_revision_sha256: string; input_sha256: string; scope_sha256: string; requirements_sha256: string
  task_status: 'NEEDS_MATERIALS' | 'PLAN_PENDING'; expected_revision: string; plan_head_revision: string
  context_revision: string; auth_epoch: string; governance_epoch: string; dependency_authorizations_sha256: string
}
export interface PlanReturnRequest {
  schema_version: '1.0.0'; target_revision: string; expected_revision: string; decision: 'RETURN' | 'CONFIRM'; reason: string
  baseline_manifest: PlanDecisionBaseline
}
export interface PlanReturnReceipt {
  schema_version: '1.0.0'; operation_id: string; decision_id: string; tenant_id: string; project_id: string; task_id: string
  target_plan_revision: string; target_plan_sha256: string; basis_task_revision: string; resulting_task_revision: string
  resulting_plan_revision: string | null; decision: 'RETURN' | 'CONFIRM'; from_status: 'NEEDS_MATERIALS' | 'PLAN_PENDING'
  to_status: 'NEEDS_MATERIALS' | 'PLAN_PENDING' | 'READY'; expected_revision: string; resulting_revision: string
  actor_id: string; decided_at: string; reason: string
}
export interface PendingPlanReturn {
  actor_id: string; tenant_id: string; project_id: string; task_id: string; target_revision: string
  expected_revision: string; key: string; body_sha256: string; operation: 'TK-04'
}
const reference = 'urn:medical:task-plan-decision:1.0.0#/$defs/'
const pendingReference = 'urn:medical:task-plan-return-view:1.0.0'
function headers(session: MedicalSession, projectId: string, taskId: string): Record<string, string> {
  const context = session.current_context
  if (!context || !taskActionAllowed(session, projectId, taskId, 'plan.confirm')) fail('ACCESS_DENIED', '人工计划需要当前人工决定权限')
  validContext(context); return { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision }
}
function storageKey(session: MedicalSession, projectId: string, taskId: string): string {
  headers(session, projectId, taskId)
  return 'medical.task.plan.return:' + session.user.user_id + ':' + session.current_context?.tenant_id + ':' + projectId + ':' + taskId
}
export function pendingPlanReturn(session: MedicalSession, projectId: string, taskId: string): PendingPlanReturn | null {
  const raw = sessionStorage.getItem(storageKey(session, projectId, taskId)); if (raw === null) return null
  const result = validate<PendingPlanReturn>(pendingReference, JSON.parse(raw))
  if (result.actor_id !== session.user.user_id || result.tenant_id !== session.current_context?.tenant_id
      || result.project_id !== projectId || result.task_id !== taskId) fail('CONTENT_MISMATCH', '原人工计划操作与当前本人、企业或任务不一致')
  return result
}
export async function planDecisionBasis(session: MedicalSession, current: CurrentTask, signal: AbortSignal,
  decision: 'RETURN' | 'CONFIRM' = 'RETURN'): Promise<PlanDecisionBaseline> {
  const detail = current.detail
  if (decision === 'CONFIRM' && detail.status !== 'PLAN_PENDING') fail('PLAN_UNCONFIRMED', '确认计划需要任务资料完整并进入计划待确认状态')
  const raw = await request.get('/api/tasks/' + detail.task_id + '/plan-decision-basis', { medical: 'envelope', signal,
    headers: { ...headers(session, detail.project_id, detail.task_id), 'X-Plan-Decision': decision } })
  const response = envelope(raw); const result = validate<PlanDecisionBaseline>(reference + 'Baseline', response.data)
  if (raw.status !== 200 || response.resource_revision !== current.revision || result.expected_revision !== current.revision
      || result.tenant_id !== session.current_context?.tenant_id || result.project_id !== detail.project_id
      || result.task_id !== detail.task_id || result.task_revision !== detail.task_revision || result.snapshot_id !== detail.snapshot_id
      || result.task_status !== detail.status || result.context_revision !== session.current_context.context_revision
      || result.auth_epoch !== session.current_context.auth_epoch || result.governance_epoch !== session.current_context.governance_epoch) {
    fail('REVISION_CONFLICT', '人工决定依据与当前任务、范围或权限水位不一致')
  }
  return result
}
export async function preparePlanReturn(session: MedicalSession, body: PlanReturnRequest): Promise<PendingPlanReturn> {
  validate(reference + 'Request', body)
  if (projectTextIssue(body.reason, 2000) || body.target_revision !== body.baseline_manifest.plan_revision
      || body.expected_revision !== body.baseline_manifest.expected_revision) fail('SCHEMA_INVALID', '人工计划决定需要完整理由和同一固定依据')
  const baseline = body.baseline_manifest; const saved = pendingPlanReturn(session, baseline.project_id, baseline.task_id)
  const context = session.current_context
  if (!context || baseline.tenant_id !== context.tenant_id || baseline.context_revision !== context.context_revision
      || baseline.auth_epoch !== context.auth_epoch || baseline.governance_epoch !== context.governance_epoch) {
    fail('SCOPE_STALE', '人工计划依据需要当前企业、上下文和权限水位')
  }
  const digest = await templateValueDigest(body)
  if (saved) {
    if (saved.body_sha256 !== digest) fail('IDEMPOTENCY_CONFLICT', '原人工计划操作需要保留全部原参数')
    return saved
  }
  const result: PendingPlanReturn = { actor_id: session.user.user_id, tenant_id: baseline.tenant_id, project_id: baseline.project_id,
    task_id: baseline.task_id, target_revision: body.target_revision, expected_revision: body.expected_revision,
    key: crypto.randomUUID(), body_sha256: digest, operation: 'TK-04' }
  validate(pendingReference, result); sessionStorage.setItem(storageKey(session, baseline.project_id, baseline.task_id), JSON.stringify(result)); return result
}
function owned(session: MedicalSession, pending: PendingPlanReturn): void {
  validate(pendingReference, pending); headers(session, pending.project_id, pending.task_id)
  if (pending.actor_id !== session.user.user_id || pending.tenant_id !== session.current_context?.tenant_id)
    fail('ACCESS_DENIED', '原决定需要本人及同一企业的当前许可')
}
function checkedReceipt(session: MedicalSession, pending: PendingPlanReturn, raw: AxiosResponse<unknown>): PlanReturnReceipt {
  owned(session, pending)
  const response = envelope(raw); const result = validate<PlanReturnReceipt>(reference + 'Receipt', response.data)
  const validTransition = result.decision === 'RETURN'
    ? result.resulting_task_revision === result.basis_task_revision && result.resulting_plan_revision === null && result.from_status === result.to_status
    : result.resulting_task_revision !== result.basis_task_revision && result.resulting_plan_revision !== null
      && result.resulting_plan_revision !== result.target_plan_revision && result.from_status === 'PLAN_PENDING' && result.to_status === 'READY'
  if (result.actor_id !== session.user.user_id || result.tenant_id !== pending.tenant_id || result.project_id !== pending.project_id
      || result.task_id !== pending.task_id || result.target_plan_revision !== pending.target_revision
      || result.expected_revision !== pending.expected_revision || !validTransition || response.resource_revision !== result.resulting_revision
      || BigInt(revision(result.expected_revision)) + 1n !== BigInt(revision(result.resulting_revision))) {
    fail('CONTENT_MISMATCH', '人工计划回执与原任务、版本或决定不一致')
  }
  return result
}
export async function submitPlanReturn(session: MedicalSession, pending: PendingPlanReturn, body: PlanReturnRequest): Promise<PlanReturnReceipt> {
  owned(session, pending); validate(reference + 'Request', body)
  if (await templateValueDigest(body) !== pending.body_sha256) fail('IDEMPOTENCY_CONFLICT', '再次提交需要全部原人工计划参数')
  const raw = await request.post('/api/tasks/' + pending.task_id + '/plan-decisions', body, { medical: 'envelope',
    headers: { ...headers(session, pending.project_id, pending.task_id), 'Idempotency-Key': pending.key } })
  const result = checkedReceipt(session, pending, raw)
  if (raw.status !== 200 || result.decision !== body.decision || result.reason !== body.reason || result.basis_task_revision !== body.baseline_manifest.task_revision
      || result.target_plan_sha256 !== body.baseline_manifest.plan_sha256) fail('CONTENT_MISMATCH', '服务器人工计划结果与完整原输入不一致')
  return result
}
export async function originalPlanReturn(session: MedicalSession, pending: PendingPlanReturn, signal: AbortSignal): Promise<PlanReturnReceipt | null> {
  owned(session, pending)
  const raw = await request.get('/api/tasks/' + pending.task_id + '/plan-decision-operations', { medical: 'envelope', signal,
    headers: { ...headers(session, pending.project_id, pending.task_id), 'Idempotency-Key': pending.key } })
  const response = envelope(raw)
  if (raw.status !== 200) fail('CONTENT_MISMATCH', '原人工决定需要明确读取结果')
  return response.data === null ? null : checkedReceipt(session, pending, raw)
}
export function clearPlanReturn(session: MedicalSession, pending: PendingPlanReturn): void {
  const actual = pendingPlanReturn(session, pending.project_id, pending.task_id)
  if (!actual || actual.key !== pending.key || actual.body_sha256 !== pending.body_sha256) fail('IDEMPOTENCY_CONFLICT', '原人工计划操作身份已经变化')
  sessionStorage.removeItem(storageKey(session, pending.project_id, pending.task_id))
}
export async function releaseUnregisteredReturn(session: MedicalSession, pending: PendingPlanReturn, signal: AbortSignal): Promise<void> {
  if (await originalPlanReturn(session, pending, signal) !== null) fail('IDEMPOTENCY_CONFLICT', '原决定已经保存，需要读取原回执')
  const current = await readCurrentTask(session, pending.project_id, pending.task_id, signal)
  const plan = await readTaskPlan(session, pending.project_id, pending.task_id, null, signal)
  if (current.revision !== pending.expected_revision || plan.revision !== pending.expected_revision
      || plan.plan?.plan_revision !== pending.target_revision) fail('REVISION_CONFLICT', '任务或计划已经变化，原操作身份继续保留')
  clearPlanReturn(session, pending)
}
