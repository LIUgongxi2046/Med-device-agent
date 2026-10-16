import request from '@/utils/request'
import { envelope, fail, revision, validate } from './protocol'
import type { Context, Listing, MedicalSession, Project } from './protocol'
import { projectActionAllowed } from './project-write'

export type WorkItemStatus = 'WAITING_INPUT' | 'RESOLVED' | 'EXPIRED' | 'CANCELLED' | 'STALE'
export interface WorkItem {
  waiting_item_id: string
  project_id: string | null
  run_id: string
  target_revision: string
  reason_code: string
  question: string
  requirement_refs: unknown[]
  allowed_resolution_actions: unknown[]
  assignee_id: string
  status: WorkItemStatus
  revision: string
  updated_at: string
}
export interface WorkItemListing {
  schema_version: '1.0.0'
  items: WorkItem[]
  total: string
  next_after: string | null
}
export interface WorkItemFilter {
  limit: number
  after?: string
  project_id?: string
  assignee_id?: string
  status?: WorkItemStatus
}

function checkProject(project: Project, context: Context): void {
  if (project.tenant_id !== context.tenant_id) fail('CONTENT_MISMATCH', '项目响应与当前企业不一致')
  revision(project.revision)
  for (const count of Object.values(project.children)) if (count !== null) revision(count)
  if (new Set(project.profile.attributes.map(attribute => attribute.key)).size !== project.profile.attributes.length) fail('SCHEMA_INVALID', '项目属性标识重复')
}

export async function listProjects(context: Context, filters: { name?: string; status?: string; after?: string }, signal: AbortSignal): Promise<Listing> {
  const value = envelope(await request.get('/api/projects', { medical: 'envelope', signal, params: { limit: '50', ...filters },
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const listing = validate<Listing>('urn:medical:projects:1.0.0#/$defs/Listing', value.data)
  revision(listing.total)
  if (value.resource_revision !== null || new Set(listing.projects.map(project => project.project_id)).size !== listing.projects.length) {
    fail('SCHEMA_INVALID', '项目集合版本或标识无效')
  }
  for (const project of listing.projects) checkProject(project, context)
  return listing
}

export async function readProject(context: Context, projectId: string, signal: AbortSignal): Promise<Project> {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(projectId)) fail('SCHEMA_INVALID', '项目标识无效')
  const value = envelope(await request.get('/api/projects/' + projectId, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const project = validate<Project>('urn:medical:projects:1.0.0#/$defs/Project', value.data)
  checkProject(project, context)
  if (project.project_id !== projectId || value.resource_revision !== project.revision) fail('CONTENT_MISMATCH', '项目响应与请求对象或版本不一致')
  return project
}

export async function listWorkItems(session: MedicalSession, filter: WorkItemFilter, signal: AbortSignal): Promise<WorkItemListing> {
  const context = session.current_context
  if (!context || !projectActionAllowed(session, 'work_item.read', filter.project_id)) {
    fail('ACCESS_DENIED', '待办读取需要当前企业身份及待办读取许可')
  }
  if (!Number.isInteger(filter.limit) || filter.limit < 1 || filter.limit > 100) fail('SCHEMA_INVALID', '待办数量范围无效')
  const value = envelope(await request.get('/api/work-items', { medical: 'envelope', signal, params: filter,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const listing = validate<WorkItemListing>('urn:medical:projects:1.0.0#/$defs/WaitingListing', value.data)
  revision(listing.total)
  if (value.resource_revision !== null || new Set(listing.items.map(item => item.waiting_item_id)).size !== listing.items.length) {
    fail('SCHEMA_INVALID', '待办集合版本或标识无效')
  }
  for (const item of listing.items) {
    revision(item.revision)
    if (filter.project_id !== undefined && item.project_id !== filter.project_id
        || filter.assignee_id !== undefined && item.assignee_id !== filter.assignee_id
        || filter.status !== undefined && item.status !== filter.status) {
      fail('CONTENT_MISMATCH', '待办响应与当前查询范围不一致')
    }
  }
  if (listing.next_after !== null && (listing.items.length !== filter.limit
      || listing.next_after !== listing.items[listing.items.length - 1]?.waiting_item_id)) {
    fail('CONTENT_MISMATCH', '待办下一页游标与当前结果不一致')
  }
  return listing
}
