import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { taskIdReference } from './tasks'
import type { TaskDetail } from './tasks'

export const taskDetailReference = 'https://schemas.medical-agent.invalid/1.0.0/backend-design/resource-details.schema.yaml#/$defs/TaskDetail'
export interface CurrentTask { detail: TaskDetail; revision: string }

export function taskReadAllowed(session: MedicalSession | null, projectId: string, taskId: string): boolean {
  return taskActionAllowed(session, projectId, taskId, 'task.read')
}

export function taskActionAllowed(session: MedicalSession | null, projectId: string, taskId: string, action: string): boolean {
  if (!session?.current_context) return false
  return session.action_summary.some(item => {
    if (item.action !== action || item.project_id !== null && item.project_id !== projectId) return false
    return item.resource_kind === 'ENTERPRISE' && item.project_id === null && item.resource_id === session.current_context?.tenant_id
      || item.resource_kind === 'PROJECT' && item.project_id === projectId && item.resource_id === projectId
      || item.resource_kind === 'TASK' && (item.resource_id === null || item.resource_id === taskId)
  })
}

export async function readCurrentTask(session: MedicalSession, projectId: string, taskId: string, signal: AbortSignal): Promise<CurrentTask> {
  validate(taskIdReference, projectId); validate(taskIdReference, taskId)
  const context = session.current_context
  if (!context || !taskReadAllowed(session, projectId, taskId)) fail('ACCESS_DENIED', '读取任务需要当前企业身份及任务读取权限')
  validContext(context)
  const raw = await request.get('/api/tasks/' + taskId, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } })
  const response = envelope(raw)
  const detail = validate<TaskDetail>(taskDetailReference, response.data)
  if (raw.status !== 200 || response.resource_revision === null || revision(response.resource_revision) === '0'
      || detail.task_id !== taskId || detail.project_id !== projectId) fail('CONTENT_MISMATCH', '任务响应与当前项目、任务或修订不一致')
  for (const id of [detail.task_id, detail.task_revision, detail.project_id, detail.owner_id, detail.plan_revision, detail.snapshot_id,
    ...detail.document_ids, ...detail.waiting_item_ids, ...detail.active_run_ids]) {
    if (id !== null) validate(taskIdReference, id)
  }
  if (new Set(detail.missing_requirements.map(item => item.requirement_id)).size !== detail.missing_requirements.length) {
    fail('CONTENT_MISMATCH', '任务缺项包含重复标识')
  }
  for (const item of detail.missing_requirements) {
    if (item.node_id !== null) validate(taskIdReference, item.node_id)
    if (item.field_id !== null) validate(taskIdReference, item.field_id)
  }
  for (const ids of [detail.document_ids, detail.waiting_item_ids, detail.active_run_ids]) {
    if (new Set(ids).size !== ids.length) fail('CONTENT_MISMATCH', '任务关联对象包含重复标识')
  }
  if (detail.status === 'NEEDS_MATERIALS' && detail.missing_requirements.length === 0
      || ['READY', 'RUNNING', 'REVIEW_PENDING', 'SIGN_PENDING', 'SIGNED'].includes(detail.status)
        && (detail.plan_revision === null || detail.missing_requirements.length !== 0)) {
    fail('CONTENT_MISMATCH', '任务状态与已确认计划或缺项记录不一致')
  }
  return { detail, revision: response.resource_revision }
}
