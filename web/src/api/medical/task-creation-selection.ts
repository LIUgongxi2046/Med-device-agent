import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { projectActionAllowed } from './project-write'
import { templateValueDigest } from './template-approval'

export interface TaskCreationOwner { member_id: string; user_id: string; member_revision: string }
export interface TaskCreationOwners {
  schema_version: '1.0.0'; tenant_id: string; project_id: string; context_revision: string; auth_epoch: string; governance_epoch: string
  limit: number; items: TaskCreationOwner[]; next_after: string | null; dataset_watermark: string
}
export interface TaskCreationOwnerQuery { limit: number; after?: string; dataset_watermark?: string }
const schema = 'urn:medical:task-creation-selection:1.0.0#/$defs/'
export async function listTaskCreationOwners(session: MedicalSession, project: string, query: TaskCreationOwnerQuery,
    previous: TaskCreationOwners | null, signal: AbortSignal): Promise<TaskCreationOwners> {
  const context = session.current_context
  if (!context || !projectActionAllowed(session, 'task.create', project) || !projectActionAllowed(session, 'project.read', project))
    fail('ACCESS_DENIED', '责任人员查询需要当前企业身份及项目创建任务许可')
  validContext(context); validate(schema + 'Query', query)
  if (query.after !== undefined && (!previous || query.after !== previous.next_after || query.dataset_watermark !== previous.dataset_watermark))
    fail('CURSOR_INVALID', '责任人员分页需要原查询的下一页及同一水位')
  const response = envelope(await request.get('/api/projects/' + project + '/task-creation-owners', { medical: 'envelope', signal, params: query,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const page = validate<TaskCreationOwners>(schema + 'Owners', response.data)
  revision(page.auth_epoch); revision(page.governance_epoch)
  const filter = { schema_version: '1.0.0', tenant_id: context.tenant_id, project_id: project, actor_id: session.user.user_id,
    context_revision: context.context_revision, auth_epoch: page.auth_epoch, governance_epoch: page.governance_epoch, limit: query.limit }
  if (response.resource_revision !== null || page.tenant_id !== context.tenant_id || page.project_id !== project
      || page.context_revision !== context.context_revision || page.limit !== query.limit || page.items.length > query.limit
      || page.dataset_watermark !== await templateValueDigest(filter)
      || query.dataset_watermark !== undefined && page.dataset_watermark !== query.dataset_watermark
      || previous !== null && (page.dataset_watermark !== previous.dataset_watermark || page.project_id !== previous.project_id)
      || new Set(page.items.map(item => item.user_id)).size !== page.items.length)
    fail('CONTENT_MISMATCH', '责任人员查询与当前企业、项目或固定水位不一致')
  let last = query.after
  for (const item of page.items) {
    if (revision(item.member_revision) === '0' || last !== undefined && item.user_id <= last
        || page.next_after !== null && item.user_id > page.next_after)
      fail('CONTENT_MISMATCH', '责任人员身份、成员修订或分页顺序不一致')
    last = item.user_id
  }
  if (page.next_after !== null && query.after !== undefined && page.next_after <= query.after)
    fail('CONTENT_MISMATCH', '责任人员下一页需要推进原游标')
  return page
}
