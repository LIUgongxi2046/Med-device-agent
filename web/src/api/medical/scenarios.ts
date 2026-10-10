import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { projectActionAllowed } from './project-write'
import { templateValueDigest } from './template-approval'

export interface PlannedScenario {
  scenario_id: string; name: string; purpose: string; status: 'PLANNED'; required_definition_fields: string[]; task_entry: null
}
export interface ScenarioMetadata {
  configuration_id: string; version_id: string; project_id: string | null; name: string | null; status: string
  content_sha256: string; configuration_revision: string; definition_status: 'UNVERIFIED'; missing_requirements: string[]; task_entry: null
}
export interface ScenarioPage {
  schema_version: '1.0.0'; tenant_id: string; context_revision: string; auth_epoch: string; governance_epoch: string
  project_id: string | null; document_type: string | null; limit: number; total: string; items: ScenarioMetadata[]
  next_after: string | null; filter_sha256: string; dataset_watermark: string; planned_requirements: PlannedScenario[]
}
export interface ScenarioQuery { limit: number; project_id?: string; document_type?: string; after?: string; dataset_watermark?: string }
const schema = 'urn:medical:scenario-catalogue:1.0.0#/$defs/'

export async function listScenarios(session: MedicalSession, query: ScenarioQuery, previous: ScenarioPage | null, signal: AbortSignal): Promise<ScenarioPage> {
  const context = session.current_context
  if (!context || query.project_id !== undefined && !projectActionAllowed(session, 'project.read', query.project_id))
    fail('ACCESS_DENIED', '场景目录需要当前企业身份与指定项目读取许可')
  validContext(context); validate(schema + 'Query', query)
  if (query.after !== undefined && (!previous || previous.next_after !== query.after || previous.dataset_watermark !== query.dataset_watermark
      || previous.limit !== query.limit || previous.project_id !== (query.project_id ?? null) || previous.document_type !== (query.document_type ?? null)))
    fail('CURSOR_INVALID', '场景目录下一页需要原查询范围与水位')
  const response = envelope(await request.get('/api/scenarios', { medical: 'envelope', signal, params: query,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const page = validate<ScenarioPage>(schema + 'Page', response.data)
  const total = BigInt(revision(page.total)); revision(page.auth_epoch); revision(page.governance_epoch)
  const filter = { schema_version: '1.0.0', tenant_id: context.tenant_id, actor_id: session.user.user_id, context_revision: context.context_revision,
    auth_epoch: page.auth_epoch, governance_epoch: page.governance_epoch, project_id: query.project_id ?? null, document_type: query.document_type ?? null, limit: query.limit }
  if (response.resource_revision !== null || page.tenant_id !== context.tenant_id || page.context_revision !== context.context_revision
      || page.project_id !== (query.project_id ?? null) || page.document_type !== (query.document_type ?? null) || page.limit !== query.limit
      || page.items.length > query.limit || BigInt(page.items.length) > total || page.filter_sha256 !== await templateValueDigest(filter)
      || query.dataset_watermark !== undefined && page.dataset_watermark !== query.dataset_watermark
      || previous !== null && (page.dataset_watermark !== previous.dataset_watermark || page.filter_sha256 !== previous.filter_sha256))
    fail('CONTENT_MISMATCH', '场景目录与当前主体、查询或原水位不一致')
  let last = query.after
  for (const item of page.items) {
    if (revision(item.configuration_revision) === '0' || last !== undefined && item.version_id <= last
        || query.project_id !== undefined && item.project_id !== null && item.project_id !== query.project_id)
      fail('CONTENT_MISMATCH', '场景版本、归属或查询顺序不一致')
    last = item.version_id
  }
  if (page.next_after !== null && (page.items.length !== query.limit || page.next_after !== last))
    fail('CONTENT_MISMATCH', '场景目录下一页需要当前最后版本')
  return page
}
