import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { taskActionAllowed } from './task-detail'
import { taskIdReference } from './tasks'

export const runStatusLabels = {
  QUEUED: '已经排队', RUNNING: '正在执行', WAITING_INPUT: '等待处理', SUCCEEDED: '已经完成',
  FAILED: '执行失败', CANCEL_REQUESTED: '正在取消', CANCELLED: '已经取消', SUPERSEDED: '已经被新尝试替代'
} as const
export interface RunStep {
  step_id: string; role_version: string; section_id: string | null; status: string; lease_generation: string; revision: string
}
export interface RunWaiting {
  waiting_item_id: string; step_id: string; action_id: string; target_revision: string; reason_code: string; question: string
  requirement_refs: string[]; allowed_resolution_actions: string[]; assignee_id: string; status: string; resolution_id: string | null; revision: string
}
export interface RunInvocation {
  invocation_id: string; step_id: string; action_id: string; status: string; settlement_status: string; currency: string
  settled_amount: string | null; total_tokens: string | null; revision: string
}
export interface RunView {
  run_id: string; task_id: string; task_revision: string; status: keyof typeof runStatusLabels; reason_code: string | null
  steps: RunStep[]; waiting_items: RunWaiting[]; inflight_invocations: RunInvocation[]
  budget: { budget_id: string; budget_revision: string; spent: string; reserved: string; limit: string; currency: string }
  started_at: string | null; ended_at: string | null; revision: string; event_cursor: string | null
}

export async function readRun(session: MedicalSession, projectId: string, taskId: string, runId: string, signal: AbortSignal): Promise<RunView> {
  for (const id of [projectId, taskId, runId]) validate(taskIdReference, id)
  const context = session.current_context
  if (!context || !taskActionAllowed(session, projectId, taskId, 'run.read')) fail('ACCESS_DENIED', '运行读取需要当前任务运行许可')
  validContext(context)
  const raw = await request.get('/api/runs/' + runId, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } })
  const response = envelope(raw); const result = validate<RunView>('urn:medical:run-view:1.0.0#/$defs/View', response.data)
  if (raw.status !== 200 || result.run_id !== runId || result.task_id !== taskId || response.resource_revision !== result.revision) {
    fail('CONTENT_MISMATCH', '运行响应与当前任务、运行或记录修订不一致')
  }
  revision(result.revision)
  const steps = new Set(result.steps.map(item => item.step_id))
  if (steps.size !== result.steps.length) fail('CONTENT_MISMATCH', '运行步骤身份重复')
  for (const group of [result.waiting_items, result.inflight_invocations]) {
    for (const item of group) if (!steps.has(item.step_id)) fail('CONTENT_MISMATCH', '运行子资源引用未知步骤')
  }
  for (const step of result.steps) { revision(step.revision); revision(step.lease_generation) }
  for (const call of result.inflight_invocations) { revision(call.revision); if (call.total_tokens !== null) revision(call.total_tokens) }
  return result
}
