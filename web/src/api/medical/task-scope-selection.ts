import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { projectActionAllowed } from './project-write'
import { templateValueDigest } from './template-approval'

export type TaskScopeKind = 'SOURCE' | 'COLLECTION'
export interface TaskSourceChoice {
  kind: 'SOURCE'; version_id: string; source_id: string; rights_revision: string; parse_version: string; index_version: string
  approval_revision: string; binding_revision: string; gate_revision: string; original_sha256: string
}
export interface TaskCollectionChoice {
  kind: 'COLLECTION'; version_id: string; collection_id: string; title: string; content_sha256: string; member_count: number; source_scope_sha256: string
}
export type TaskScopeChoice = TaskSourceChoice | TaskCollectionChoice
export interface TaskScopeChoices {
  schema_version: '1.0.0'; tenant_id: string; project_id: string; context_revision: string; auth_epoch: string; governance_epoch: string
  scope_kind: TaskScopeKind; limit: number; items: TaskScopeChoice[]; next_after: string | null; filter_sha256: string; dataset_watermark: string
}
export interface TaskScopeQuery { limit: number; after?: string; dataset_watermark?: string }
const schema = 'urn:medical:task-scope-selection:1.0.0#/$defs/'

export async function listTaskScopeChoices(session: MedicalSession, project: string, kind: TaskScopeKind, query: TaskScopeQuery,
    previous: TaskScopeChoices | null, signal: AbortSignal): Promise<TaskScopeChoices> {
  const context = session.current_context
  if (!context || !projectActionAllowed(session, 'task.create', project) || !projectActionAllowed(session, 'project.read', project))
    fail('ACCESS_DENIED', '资料选择需要当前项目读取与创建任务许可')
  validContext(context); validate(schema + 'Query', query)
  if (query.after !== undefined && (!previous || previous.scope_kind !== kind || previous.project_id !== project
      || query.after !== previous.next_after || query.dataset_watermark !== previous.dataset_watermark || query.limit !== previous.limit))
    fail('CURSOR_INVALID', '资料分页需要原范围、下一页和同一水位')
  const response = envelope(await request.get('/api/projects/' + project + '/task-creation-' + (kind === 'SOURCE' ? 'sources' : 'collections'),
    { medical: 'envelope', signal, params: query, headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const page = validate<TaskScopeChoices>(schema + 'Page', response.data)
  revision(page.auth_epoch); revision(page.governance_epoch)
  const filter = { schema_version: '1.0.0', tenant_id: context.tenant_id, project_id: project, actor_id: session.user.user_id,
    context_revision: context.context_revision, auth_epoch: page.auth_epoch, governance_epoch: page.governance_epoch, scope_kind: kind, limit: query.limit }
  if (response.resource_revision !== null || page.tenant_id !== context.tenant_id || page.project_id !== project
      || page.context_revision !== context.context_revision || page.scope_kind !== kind || page.limit !== query.limit
      || page.items.length > query.limit || page.filter_sha256 !== await templateValueDigest(filter)
      || query.dataset_watermark !== undefined && page.dataset_watermark !== query.dataset_watermark
      || previous !== null && (page.dataset_watermark !== previous.dataset_watermark || page.filter_sha256 !== previous.filter_sha256))
    fail('CONTENT_MISMATCH', '资料查询与当前企业、项目或原水位不一致')
  let last = query.after
  for (const item of page.items) {
    if (item.kind !== kind || last !== undefined && item.version_id <= last || item.kind === 'SOURCE' && revision(item.gate_revision) === '0')
      fail('CONTENT_MISMATCH', '资料版本类型、分页顺序或当前修订不一致')
    last = item.version_id
  }
  if (page.next_after !== null && (page.items.length !== query.limit || page.next_after !== last))
    fail('CONTENT_MISMATCH', '资料下一页需要当前页面的最后版本')
  return page
}
