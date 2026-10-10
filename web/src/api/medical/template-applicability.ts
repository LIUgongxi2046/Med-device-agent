import request from '@/utils/request'
import { canonicalizeEx } from 'json-canonicalize'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Basis, MedicalSession, Project } from './protocol'
import { projectActionAllowed } from './project-write'
import type { ReviewTemplate, TemplateCondition } from './template-approval'
import type { TaskRequirement, taskDocumentLabels } from './tasks'

export type DocumentType = keyof typeof taskDocumentLabels
export type Applicability = 'APPLICABLE' | 'NOT_APPLICABLE' | 'UNKNOWN'
export interface TemplateConditionResult {
  scope: 'PROFILE' | 'TEMPLATE' | 'NODE' | 'FIELD' | 'ATTACHMENT'; target_id: string
  rule_revision: Basis | null; engine_revision: string | null; input_keys: string[]
  status: Applicability; unknown_input_keys: string[]
}
export interface ApplicableTemplate {
  template: ReviewTemplate & { status: 'APPROVED' }; applicability: Applicability
  conditions: TemplateConditionResult[]; missing_requirements: TaskRequirement[]; dependency_authorizations_sha256: string
}
export interface TemplateQuery {
  document_type: DocumentType; profile_revision: string; page: number; page_size: number
  dataset_watermark?: string; filter_sha256?: string
}
export interface ApplicableTemplatePage {
  schema_version: '1.0.0'; project_id: string; profile_revision: string; profile_sha256: string; document_type: DocumentType
  items: ApplicableTemplate[]; missing_requirements: TaskRequirement[]
  page_info: { page: number; page_size: number; total: string; watermark: string; filter_sha256: string }
}
export const applicabilityLabels: Record<Applicability, string> = { APPLICABLE: '适用', NOT_APPLICABLE: '不适用', UNKNOWN: '待确认' }
export const conditionScopeLabels: Record<TemplateConditionResult['scope'], string> = {
  PROFILE: '项目属性', TEMPLATE: '模板', NODE: '目录节点', FIELD: '字段', ATTACHMENT: '附件'
}
const reference = 'urn:medical:template-applicability:1.0.0#/$defs/'
function canonical(value: unknown): string { return canonicalizeEx(value, { strictUndefined: true }) }
async function digest(value: unknown): Promise<string> {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonical(value)))
  return Array.from(new Uint8Array(bytes), byte => byte.toString(16).padStart(2, '0')).join('')
}

function checkConditions(item: ApplicableTemplate, projectId: string): void {
  const content = item.template.content
  const expected = new Map<string, TemplateCondition | null>([
    ['PROFILE:' + projectId, null], ['TEMPLATE:' + item.template.template_version,
      validate<TemplateCondition>('urn:medical:templates:1.0.0#/$defs/Condition', content.condition_schema.applicability)]
  ])
  for (const node of content.nodes) expected.set('NODE:' + node.node_id, node.condition)
  for (const field of content.field_definitions) expected.set('FIELD:' + field.field_id, field.condition)
  for (const attachment of content.attachment_requirements) expected.set('ATTACHMENT:' + attachment.attachment_requirement_id, attachment.condition)
  const seen = new Set<string>()
  for (const condition of item.conditions) {
    const key = condition.scope + ':' + condition.target_id
    if (!expected.has(key) || seen.has(key) || condition.unknown_input_keys.some(name => !condition.input_keys.includes(name))
        || condition.status === 'UNKNOWN' && condition.unknown_input_keys.length === 0
        || condition.status !== 'UNKNOWN' && condition.unknown_input_keys.length !== 0) {
      fail('CONTENT_MISMATCH', '适用条件的对象、输入或判断状态不一致')
    }
    seen.add(key)
    const source = expected.get(key)
    if (source !== null && source !== undefined && (canonical(source.rule_revision) !== canonical(condition.rule_revision)
        || source.engine_revision !== condition.engine_revision || canonical(source.input_keys) !== canonical(condition.input_keys)
        || source.mode === 'ALWAYS' && condition.status !== 'APPLICABLE')) {
      fail('CONTENT_MISMATCH', '适用条件与完整模板中的固定规则不一致')
    }
  }
  if (seen.size !== expected.size || item.applicability === 'NOT_APPLICABLE') fail('CONTENT_MISMATCH', '模板适用响应缺少完整条件或包含不兼容模板')
}

export async function listApplicableTemplates(session: MedicalSession, project: Project, query: TemplateQuery,
  previous: ApplicableTemplatePage | null, signal: AbortSignal): Promise<ApplicableTemplatePage> {
  const context = session.current_context
  if (!context || !projectActionAllowed(session, 'project.read', project.project_id)) fail('ACCESS_DENIED', '查询适用模板需要当前企业身份及项目读取权限')
  validContext(context); validate(reference + 'Query', query)
  if (project.tenant_id !== context.tenant_id || query.profile_revision !== project.profile.profile_revision) {
    fail('CONTENT_MISMATCH', '模板查询与当前项目及固定属性版本不一致')
  }
  if (query.dataset_watermark !== undefined) revision(query.dataset_watermark)
  if (query.page > 1 && (!previous || previous.project_id !== project.project_id || previous.profile_revision !== query.profile_revision
      || previous.document_type !== query.document_type || previous.page_info.page_size !== query.page_size
      || previous.page_info.watermark !== query.dataset_watermark || previous.page_info.filter_sha256 !== query.filter_sha256)) {
    fail('SCOPE_STALE', '模板分页需要同一项目属性、文档类型和授权集合')
  }
  const response = envelope(await request.get('/api/projects/' + project.project_id + '/applicable-templates', {
    medical: 'envelope', signal, params: query,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision }
  }))
  const result = validate<ApplicableTemplatePage>(reference + 'Page', response.data)
  const info = result.page_info
  revision(info.total); revision(info.watermark)
  if (response.resource_revision !== project.revision || result.project_id !== project.project_id
      || result.profile_revision !== query.profile_revision || result.profile_sha256 !== project.profile.content_sha256
      || result.document_type !== query.document_type || info.page !== query.page || info.page_size !== query.page_size
      || query.dataset_watermark !== undefined && (info.watermark !== query.dataset_watermark || info.filter_sha256 !== query.filter_sha256)
      || query.page > 1 && previous !== null && info.total !== previous.page_info.total) {
    fail('CONTENT_MISMATCH', '适用模板响应与项目、属性、文档类型或分页范围不一致')
  }
  const total = BigInt(info.total)
  if (total > BigInt(Number.MAX_SAFE_INTEGER) || (total + BigInt(info.page_size) - 1n) / BigInt(info.page_size) > 2147483647n) {
    fail('REVISION_LIMIT', '模板数量超出分页组件和接口支持范围')
  }
  const remaining = total - BigInt(info.page - 1) * BigInt(info.page_size)
  const expected = remaining <= 0n ? 0n : remaining < BigInt(info.page_size) ? remaining : BigInt(info.page_size)
  if (BigInt(result.items.length) !== expected || new Set(result.items.map(item => item.template.template_version)).size !== result.items.length
      || total === 0n && !result.missing_requirements.some(item => item.reason_code === 'SOURCE_MISSING')) {
    fail('CONTENT_MISMATCH', '适用模板数量、唯一身份或缺项说明不一致')
  }
  let preceding: string | null = null
  for (const item of result.items) {
    const template = item.template
    if (revision(template.revision) === '0' || revision(template.authority_revision) === '0'
        || template.tenant_id !== context.tenant_id || template.content.document_type !== query.document_type
        || await digest(template.content) !== template.content_sha256
        || preceding !== null && template.template_version <= preceding) {
      fail('CONTENT_MISMATCH', '核准模板的企业、版本、内容摘要或顺序不一致')
    }
    checkConditions(item, project.project_id)
    preceding = template.template_version
  }
  return result
}
