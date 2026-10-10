import request from '@/utils/request'
import { envelope, fail, revision, validate } from './protocol'
import type { Context, Listing, Project } from './protocol'

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
