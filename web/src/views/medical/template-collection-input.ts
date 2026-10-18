import { schemaIssues } from '@/api/medical/protocol'
import type { Basis } from '@/api/medical/protocol'
import type { TemplateCreateBody } from '@/api/medical/template-create'
import type { CollectionBody } from '@/api/medical/collections'
import { projectTextIssue } from '@/api/medical/project-text'
import { ruleConstantJsonIssue } from '@/api/medical/rule-values'

/** Unsent draft inputs only. Strict response, pending identity and fixed-reference checks remain unchanged. */
export const draftJsonIssue = ruleConstantJsonIssue
const templateSchema = 'urn:medical:templates:1.0.0#/$defs/'
export function templateDefinitionInputIssue(value: unknown, kind: string, identity: string): string {
  if (!Array.isArray(value)) return '模板定义需要完整数组'
  if (value.some(item => schemaIssues(templateSchema + kind, item).length)) return '请补全当前定义的必需字段并检查格式'
  const ids = value.map(item => item[identity])
  return new Set(ids).size !== ids.length ? '模板定义的稳定标识不能重复' : ''
}
export function templateCreateInputIssue(input: unknown): string {
  if (schemaIssues(templateSchema + 'VersionInput', input).length) return '完整模板定义未通过字段规范，请核查必需内容与格式'
  const body = input as TemplateCreateBody
  if (projectTextIssue(body.reason, 2000)) return '请填写规范的模板创建理由'
  for (const item of [...body.content.repeat_definitions, ...body.content.attachment_requirements]) {
    if (BigInt(item.min_count) > 18446744073709551615n || item.max_count !== null && BigInt(item.max_count) > 18446744073709551615n) return '模板计数超出允许范围，请保留原值并核查'
  }
  const refs = new Map<string, string>()
  function visit(value: unknown): string {
    if (value === null || typeof value !== 'object') return ''
    if (!Array.isArray(value) && Object.hasOwn(value, 'version_id')) {
      if (schemaIssues(templateSchema + 'Basis', value).length) return '请核查依据版本及完整摘要格式'
      const basis = value as Basis, prior = refs.get(basis.version_id)
      if (prior !== undefined && prior !== basis.content_sha256) return '同一依据版本填写了不同内容摘要，请核查原始依据'
      refs.set(basis.version_id, basis.content_sha256)
    }
    for (const child of Object.values(value)) { const issue = visit(child); if (issue) return issue }
    return ''
  }
  return visit(body)
}
export function collectionInputIssue(input: unknown, collectionId: string | null): string {
  if (schemaIssues('urn:medical:collections:1.0.0#/$defs/' + (collectionId === null ? 'Create' : 'VersionInput'), input).length) return '请补全资料集参数并检查字段格式'
  const body = input as CollectionBody
  if (projectTextIssue(body.reason, 2000) || projectTextIssue(body.content.title, 256)) return '请填写规范的资料集名称和理由'
  if (new Set(body.content.members.map(item => item.source_version)).size !== body.content.members.length) return '同一来源版本不能重复加入资料集'
  // expected_revision/base_version are fixed recovery state, checked only by the original strict domain layer.
  return ''
}
