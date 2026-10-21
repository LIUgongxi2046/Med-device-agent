import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import { canonicalizeEx } from 'json-canonicalize'
import contract from '../../../../business/medical-persistence/src/main/resources/task-field-target.schema.json'
import { fail, validContext } from './protocol'
import type { Context, MedicalSession } from './protocol'
import { sourceDigest } from './content-digest'
import { projectTextIssue } from './project-text'
import type { InputAuthor, InputDecide, InputView, MappingAuthor, MappingEntry, MappingView, Preparation, Produce, Status, SourceExactRef } from './task-field-target-types'
export type * from './task-field-target-types'

const ajv = new Ajv2020({ strictSchema: true, strictTypes: false, allErrors: true })
addFormats(ajv); ajv.addSchema(contract)
export const canonical = (value: unknown): string => canonicalizeEx(value, { strictUndefined: true })
export const detached = <T>(value: T): T => JSON.parse(canonical(value)) as T
export function checked<T>(name: string, value: unknown): T {
  const validator = ajv.getSchema(contract.$id + '#/$defs/' + name)
  if (!validator || !validator(value)) fail('CONTENT_MISMATCH', '字段目标合同不匹配：' + (validator?.errors?.[0]?.instancePath || name))
  return value as T
}
export const inventoryLabels = { MISSING_MAPPING: '尚未映射', MISSING_SOURCE: '缺少可用来源', UNRESOLVED_CONTEXT: '型号或条件上下文待补', UNKNOWN_CONDITION: '适用条件尚未明确', NOT_APPLICABLE: '不适用，保留完整清单', UNSUPPORTED_TYPE: '当前生产器不支持', UNASSIGNED_SECTION: '尚未分配章节', MAPPED: '已映射（提议）' } as const
export const contextLabels = { UNRESOLVED: '尚未明确', KNOWN_GLOBAL: '已明确为全局', KNOWN_SCOPED: '已明确限定身份' } as const
export const sourceKindLabels = { MODEL: '器械型号', PTR_NUMBER: '产品技术要求编号', DOCUMENT_NUMBER: '文件文号', STANDARD_NUMBER: '标准编号', STANDARD_YEAR: '标准年代号' } as const
export const kindLabels = { MAPPING: '保存提议映射', PRODUCE: '登记提议字段目标', INPUT_AUTHOR: '登记输入能力草稿', INPUT_DECIDE: '决定输入能力状态' } as const
export type WriteKind = keyof typeof kindLabels
export type WriteBody = MappingAuthor | Produce | InputAuthor | InputDecide
export const requestSchemas: Record<WriteKind, string> = { MAPPING: 'MappingAuthor', PRODUCE: 'Produce', INPUT_AUTHOR: 'InputAuthor', INPUT_DECIDE: 'InputDecide' }
export interface PendingFieldTarget {
  schema_version: '1.0.0'; actor: string; tenant_id: string; project_id: string; task_id: string
  context: Context; kind: WriteKind; resource_id: string | null; key: string; body: WriteBody; body_sha256: string
}
export function fieldTargetContext(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '请先选择当前企业')
  validContext(session.current_context); return session.current_context
}
export function fieldTargetGuard(reader: () => MedicalSession, signal: AbortSignal) {
  const session = reader(), context = detached(fieldTargetContext(session)), actor = session.user.user_id
  const authority = canonical(session.action_summary)
  const assert = () => { signal.throwIfAborted(); const now = reader()
    if (now !== session || now.user.user_id !== actor || canonical(now.current_context) !== canonical(context) || canonical(now.action_summary) !== authority) fail('CONTEXT_CHANGED', '当前身份或授权上下文已变化，请重新读取') }
  assert(); return { session, context, assert }
}
export function sourceChoiceKey(value: SourceExactRef): string { return canonical(value) }
export function mappingIssue(body: MappingAuthor, preparation?: Preparation): string {
  const validator = ajv.getSchema(contract.$id + '#/$defs/MappingAuthor')
  if (!validator || !validator(body)) return '请完整填写映射身份、固定版本和规范理由'
  if (projectTextIssue(body.reason, 2000)) return '请填写非空、规范的保存理由'
  const keys = new Set<string>(), occurrences = new Set<string>()
  for (const entry of body.mappings) {
    if (keys.has(entry.entry_key)) return '映射条目标识重复'
    keys.add(entry.entry_key)
    for (const [state, ids] of [[entry.model_context, entry.model_ids], [entry.condition_context, entry.condition_ids]] as const) {
      if (state === 'KNOWN_GLOBAL' && ids.length !== 0 || state === 'KNOWN_SCOPED' && ids.length === 0) return '全局上下文不得携带限定身份；限定上下文必须明确列出身份'
    }
    const occurrence = canonical([entry.field_definition_id, entry.row_id, [...entry.model_ids].sort(), [...entry.condition_ids].sort()])
    if (occurrences.has(occurrence)) return '同一字段、行、型号与条件身份重复'
    occurrences.add(occurrence)
    if (preparation) {
      const item = preparation.inventory.find(field => field.field_definition_id === entry.field_definition_id)
      if (!item || item.section_id !== entry.section_id || item.node_id !== entry.node_id || entry.question_ids.some(id => !item.question_ids.includes(id))) return '映射必须使用当前完整清单中的字段、章节、节点与实际研究问题'
      if (item.value_type !== 'TEXT' && entry.source_exact !== null) return '非 TEXT 字段不支持此生产器，不能转换为文字'
      if (item.row_id_required && !entry.row_id) return '重复字段需要明确的实际行身份'
      if (entry.source_exact && !preparation.source_choices.some(choice => sourceChoiceKey(choice.source_exact) === sourceChoiceKey(entry.source_exact!))) return '来源必须选择当前可读取的 SourceExact 固定修订和原序号'
    }
  }
  return ''
}
export function fieldTargetInputIssue(kind: WriteKind, body: unknown): string {
  const validator = ajv.getSchema(contract.$id + '#/$defs/' + requestSchemas[kind])
  if (!validator || !validator(body)) return '请完整填写固定对象、修订与操作理由'
  const input = body as WriteBody
  if (projectTextIssue(input.reason, 2000)) return '请填写非空、规范的操作理由'
  return kind === 'MAPPING' ? mappingIssue(input as MappingAuthor) : ''
}
export function validateWrite(kind: WriteKind, body: WriteBody): void {
  checked(requestSchemas[kind], body)
  if (projectTextIssue(body.reason, 2000)) fail('SCHEMA_INVALID', '请填写完整、规范的操作理由')
  if (kind === 'MAPPING') { const issue = mappingIssue(body as MappingAuthor); if (issue) fail('SCHEMA_INVALID', issue) }
}
export function mappingBody(preparation: Preparation, mappings: MappingEntry[], reason: string): MappingAuthor {
  return { schema_version: '1.0.0', project_id: preparation.project_id, task_revision: preparation.task_revision,
    task_input_sha256: preparation.task_input_sha256, plan_revision: preparation.plan_revision, plan_sha256: preparation.plan_sha256,
    template_version: preparation.template_version, template_sha256: preparation.template_sha256, profile_revision: preparation.profile_revision,
    scope_snapshot: preparation.scope_snapshot, expected_revision: preparation.head_revision, base_mapping_id: preparation.base_mapping_id, reason, mappings: detached(mappings) }
}
export function produceBody(preparation: Preparation, reason: string): Produce {
  const mapping = preparation.current_mapping
  if (!mapping?.is_current || mappingBasisIssue(preparation)) fail('ACTION_STALE', '需要当前任务和计划的固定映射版本')
  return { schema_version: '1.0.0', project_id: preparation.project_id, task_revision: preparation.task_revision,
    plan_revision: preparation.plan_revision, assignment_id: mapping.manifest.assignment_id,
    assignment_sha256: mapping.assignment_sha256, expected_revision: preparation.head_revision, reason }
}
export function mappingBasisIssue(preparation: Preparation): string {
  const mapping = preparation.current_mapping
  if (!mapping) return '尚未保存提议映射'
  if (!mapping.is_current) return '原映射已不是当前任务或计划的有效版本。请核查当前依据并明确保存新提议映射。'
  for (const key of ['project_id', 'task_revision', 'task_input_sha256', 'plan_revision', 'plan_sha256', 'template_version', 'template_sha256', 'profile_revision', 'scope_snapshot'] as const)
    if (mapping.manifest[key] !== preparation[key]) return '原映射固定依据已过期。请核查当前任务、计划、模板与来源快照后明确保存新提议版本。'
  return ''
}
export async function verifyMapping(view: MappingView, tenant: string, project: string, task: string): Promise<void> {
  checked('MappingView', view)
  if (view.manifest.tenant_id !== tenant || view.manifest.project_id !== project || view.manifest.task_id !== task
      || await sourceDigest(view.manifest) !== view.assignment_sha256) fail('CONTENT_MISMATCH', '映射内容、摘要或任务归属不一致')
}
export function verifyStatus(value: Status, tenant: string, project: string, task: string): void {
  checked('Status', value)
  if (value.tenant_id !== tenant || value.project_id !== project || value.task_id !== task
      || (value.state === 'REGISTERED') !== (value.receipt !== null) || (value.state === 'REGISTERED') !== (value.artifact_id !== null)) fail('CONTENT_MISMATCH', '字段目标状态没有对应的真实登记回执')
  if (value.receipt) {
    const r = value.receipt
    if (r.tenant_id !== tenant || r.project_id !== project || r.task_id !== task || r.artifact_id !== value.artifact_id
        || r.assignment_id !== value.assignment_id || r.assignment_sha256 !== value.assignment_sha256 || r.write_id !== value.write_id) fail('CONTENT_MISMATCH', '字段目标登记回执身份不一致')
  }
}
export async function verifyPreparation(value: Preparation, tenant: string, project: string, task: string): Promise<void> {
  checked('Preparation', value)
  if (value.tenant_id !== tenant || value.project_id !== project || value.task_id !== task) fail('CONTENT_MISMATCH', '准备信息与当前企业、项目或任务不一致')
  const fields = new Set<string>(), nodes = new Set(value.template_nodes.map(item => item.node_id)), sections = new Map(value.sections.map(item => [item.section_id, item]))
  if (nodes.size !== value.template_nodes.length || value.template_nodes.some(item => item.parent_id !== null && !nodes.has(item.parent_id))) fail('CONTENT_MISMATCH', '完整目录节点身份不一致')
  if (sections.size !== value.sections.length) fail('CONTENT_MISMATCH', '章节身份重复')
  for (const item of value.inventory) {
    const section = item.section_id === null ? undefined : sections.get(item.section_id)
    if (!nodes.has(item.node_id) || fields.has(item.field_definition_id) || item.section_id !== null && (!section?.requirement_node_ids.includes(item.node_id) || item.question_ids.some(id => !section.question_ids.includes(id))) || item.field_definition.field_id !== item.field_definition_id || item.field_definition.node_id !== item.node_id || item.field_definition.value_type !== item.value_type || item.field_definition.required !== item.required || item.field_definition.repeat_id !== item.repeat_id || item.field_definition.name !== item.name || item.field_definition.key !== item.key || canonical(item.field_definition.condition) !== canonical(item.condition)) fail('CONTENT_MISMATCH', '完整字段清单的章节、节点或真实问题身份不一致')
    fields.add(item.field_definition_id)
  }
  const expectedConditions = new Set(['PROFILE:' + project, 'TEMPLATE:' + value.template_version,
    ...value.template_nodes.map(item => 'NODE:' + item.node_id), ...value.inventory.map(item => 'FIELD:' + item.field_definition_id),
    ...value.attachment_requirements.map(item => 'ATTACHMENT:' + item.attachment_requirement_id)])
  const actualConditions = new Set<string>()
  for (const result of value.condition_results) {
    const key = result.scope + ':' + result.target_id
    if (!expectedConditions.has(key) || actualConditions.has(key) || result.unknown_input_keys.some(key => !result.input_keys.includes(key))
        || (result.status === 'UNKNOWN') !== (result.unknown_input_keys.length > 0)) fail('CONTENT_MISMATCH', '完整适用条件及未知输入不一致')
    actualConditions.add(key)
  }
  if (actualConditions.size !== expectedConditions.size) fail('CONTENT_MISMATCH', '准备响应遗漏适用条件')
  for (const repeat of value.repeat_definitions) if (!nodes.has(repeat.node_id) || repeat.field_ids.some(id => !value.inventory.some(item => item.field_definition_id === id && item.repeat_id === repeat.repeat_id))) fail('CONTENT_MISMATCH', '重复组与完整字段清单不一致')
  if (value.current_mapping) {
    await verifyMapping(value.current_mapping, tenant, project, task)
    if (value.current_mapping.manifest.assignment_id !== value.base_mapping_id
        || value.current_mapping.revision !== value.head_revision) fail('CONTENT_MISMATCH', '当前映射与头修订不一致')
  } else if (value.base_mapping_id !== null) fail('CONTENT_MISMATCH', '准备信息缺少当前映射')
  verifyStatus(value.production, tenant, project, task)
  if (value.production.assignment_id !== null && (value.production.assignment_id !== value.base_mapping_id || value.production.assignment_sha256 !== value.current_mapping?.assignment_sha256)) fail('CONTENT_MISMATCH', '生产状态不是当前映射的实际结果')
}
export async function verifyInput(view: InputView, tenant: string, project: string, task: string): Promise<void> {
  checked('InputView', view)
  if (view.manifest.tenant_id !== tenant || view.manifest.project_id !== project || view.manifest.task_id !== task
      || await sourceDigest(view.manifest) !== view.capability_sha256) fail('CONTENT_MISMATCH', '能力完整声明、摘要或任务归属不一致')
  if (view.manifest.object_ref.sha256 !== view.manifest.artifact_sha256 || view.manifest.object_ref.object_version !== view.manifest.object_version
      || BigInt(view.manifest.maximum_read_bytes) < BigInt(view.manifest.object_ref.size_bytes)) fail('CONTENT_MISMATCH', '能力对象读取版本、摘要及读取字节边界不一致')
  if (view.status === 'DRAFT' && view.latest_decision_id !== null || view.status !== 'DRAFT' && view.latest_decision_id === null) fail('CONTENT_MISMATCH', '能力状态缺少实际决定身份')
}
export function storageKey(session: MedicalSession, project: string, task: string): string {
  checked('Id', project); checked('Id', task)
  return 'medical.task-field-target.pending:' + session.user.user_id + ':' + fieldTargetContext(session).tenant_id + ':' + project + ':' + task
}
export function readPendingFieldTarget(session: MedicalSession, project: string, task: string): PendingFieldTarget | null {
  const raw = sessionStorage.getItem(storageKey(session, project, task)); if (raw === null) return null
  if (new TextEncoder().encode(raw).length > 8 * 1024 * 1024) fail('CONTENT_MISMATCH', '原请求恢复记录超出允许大小')
  const value = JSON.parse(raw) as PendingFieldTarget
  const keys = ['schema_version', 'actor', 'tenant_id', 'project_id', 'task_id', 'context', 'kind', 'resource_id', 'key', 'body', 'body_sha256']
  if (!value || typeof value !== 'object' || Object.keys(value).length !== keys.length || keys.some(key => !Object.hasOwn(value, key))
      || value.schema_version !== '1.0.0' || !Object.hasOwn(requestSchemas, value.kind)) fail('CONTENT_MISMATCH', '原请求记录不完整或已损坏')
  checked('Key', value.key); checked('Hash', value.body_sha256); validContext(value.context); validateWrite(value.kind, value.body)
  if (value.resource_id !== null) checked('Id', value.resource_id)
  if (value.actor !== session.user.user_id || value.tenant_id !== fieldTargetContext(session).tenant_id || value.context.tenant_id !== value.tenant_id
      || value.project_id !== project || value.body.project_id !== project || value.task_id !== task
      || (value.kind === 'INPUT_DECIDE') !== (value.resource_id !== null)) fail('CONTENT_MISMATCH', '原请求与当前本人、企业、项目或任务不一致')
  return detached(value)
}
