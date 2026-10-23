import request from '@/utils/request'
import { canonicalizeEx } from 'json-canonicalize'
import { envelope, fail, validate } from './protocol'
import type { Context, MedicalSession } from './protocol'
import type { TemplateStatus } from './template-approval'

export interface TemplateSummary {
  tenant_id: string; template_version: string; document_type: string; application_path: string; ivd: boolean
  status: TemplateStatus; revision: string; reason: string; created_by: string; created_at: string; content_sha256: string
}
export interface TemplateDirectory {
  schema_version: '1.0.0'; tenant_id: string; current_context: Context; items: TemplateSummary[]; next_after: string | null
}
export const templateDocumentLabels: Record<string, string> = {
  PRODUCT_TECHNICAL_REQUIREMENTS: '产品技术要求', REGISTRATION_DIRECTORY: '注册文档目录', OVERVIEW: '综述资料',
  INSTRUCTIONS_FOR_USE: '说明书', LABEL: '标签样稿', ESSENTIAL_PRINCIPLES: '基本原则清单', RISK_MANAGEMENT: '风险管理',
  RESEARCH_REPORT: '研究报告', CLINICAL_EVALUATION: '临床评价', QUALITY_SYSTEM: '质量体系', CHANGE_REGISTRATION: '变更资料',
  RENEWAL_REGISTRATION: '延续资料', REGISTRATION_DOSSIER: '注册资料总稿'
}
export async function readTemplateDirectory(session: MedicalSession, query: { limit: number; q?: string; after?: string }, signal: AbortSignal): Promise<TemplateDirectory> {
  if (!session.current_context) fail('ACCESS_DENIED', '模板目录需要当前工作组织')
  validate('urn:medical:configuration-discovery:1.0.0#/$defs/Query', query)
  const value = envelope(await request.get('/api/templates', { medical: 'envelope', signal, params: query,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': session.current_context.context_revision } }))
  const directory = validate<TemplateDirectory>('urn:medical:configuration-discovery:1.0.0#/$defs/TemplateList', value.data)
  if (directory.tenant_id !== session.current_context.tenant_id || value.resource_revision !== session.current_context.context_revision
      || canonicalizeEx(directory.current_context) !== canonicalizeEx(session.current_context)) fail('CONTEXT_CHANGED', '模板目录与当前组织不一致')
  let previous: string | null = null
  for (const item of directory.items) {
    if (item.tenant_id !== directory.tenant_id || previous !== null && item.template_version <= previous) fail('CONTENT_MISMATCH', '模板目录的组织或顺序不一致')
    previous = item.template_version
  }
  return directory
}
