import type { DocumentBody, EvidenceLink, FieldValue } from '../../../../contracts/typescript/src/interfaces'
import type { InventoryItem, MappingEntry, Preparation, SourceChoice } from './task-field-target-types'

/** Presentation-only facts. This is not an N03 result or a field-decision DTO. */
export type VerificationState = 'CONFLICT' | 'MISSING' | 'UNKNOWN'
export type VerificationRow = {
  key: string; name: string; field: FieldValue | null; definition: InventoryItem | null
  evidence: EvidenceLink[]; missingEvidenceIds: string[]; mappings: MappingEntry[]
  state: VerificationState; reasons: string[]
}
export const verificationLabels: Record<VerificationState, string> = {
  CONFLICT: '存在冲突引用', MISSING: '内容或依据待补', UNKNOWN: '专业核验未知'
}
export function sourceForMapping(entry: MappingEntry, choices: SourceChoice[]): SourceChoice | undefined {
  const ref = entry.source_exact
  if (!ref) return undefined
  return choices.find(({ source_exact: x }) => x.source_version === ref.source_version && x.field_revision === ref.field_revision
    && x.manifest_sha256 === ref.manifest_sha256 && x.ordinal === ref.ordinal && x.field_kind === ref.field_kind)
}
export function verificationRows(body: DocumentBody, preparation?: Preparation | null): VerificationRow[] {
  // An unrelated template must never label current fields or supply apparent source matches.
  const current = preparation?.template_version === body.template_version ? preparation : null
  const inventory = current?.inventory ?? []
  const mapping = current?.current_mapping
  const currentMappings = mapping?.is_current && mapping.manifest.template_version === body.template_version
    && mapping.manifest.task_revision === current?.task_revision && mapping.manifest.plan_revision === current?.plan_revision
    ? mapping.manifest.mappings : []
  function row(field: FieldValue | null, definition: InventoryItem | null): VerificationRow {
    const ids = field?.evidence_link_ids ?? []
    const evidence = body.evidence_links.filter(link => ids.includes(link.evidence_link_id))
    const missingEvidenceIds = ids.filter(id => !evidence.some(link => link.evidence_link_id === id))
    const reasons: string[] = []
    if (!field) reasons.push('固定清单中有此字段，当前正文尚无对应值')
    else if (!field.original_value.trim()) reasons.push('正文原始值为空')
    if (!ids.length) reasons.push('未关联字段证据')
    if (missingEvidenceIds.length) reasons.push('部分证据引用在当前正文中不可用')
    if (definition && definition.state !== 'MAPPED') reasons.push('提议清单状态：' + definition.state)
    const conflict = evidence.some(link => link.relation === 'CONFLICT')
    if (conflict) reasons.unshift('当前正文存在标记为冲突的证据关系；不等于专业核验结论')
    const mappings = currentMappings.filter(entry =>
      entry.field_definition_id === (field?.field_definition_id ?? definition?.field_definition_id)
      && entry.node_id === (field?.node_id ?? definition?.node_id))
    return { key: field ? 'field:' + field.field_id : 'definition:' + definition!.field_definition_id,
      name: definition?.name ?? '正文结构化字段', field, definition, evidence, missingEvidenceIds, mappings,
      state: conflict ? 'CONFLICT' : reasons.length ? 'MISSING' : 'UNKNOWN', reasons }
  }
  const result = body.fields.map(field => row(field, inventory.find(item => item.field_definition_id === field.field_definition_id && item.node_id === field.node_id) ?? null))
  for (const definition of inventory) {
    if (!body.fields.some(field => field.field_definition_id === definition.field_definition_id && field.node_id === definition.node_id)) result.push(row(null, definition))
  }
  return result
}
export function filterVerificationRows(rows: VerificationRow[], query: string, state: VerificationState | 'ALL'): VerificationRow[] {
  const text = query.trim().toLocaleLowerCase()
  return rows.filter(row => (state === 'ALL' || row.state === state) && (!text || [row.name, row.field?.field_id,
    row.field?.field_definition_id, row.definition?.field_definition_id, row.field?.original_value,
    row.field?.model, row.field?.condition, row.field?.unit].some(value => value?.toLocaleLowerCase().includes(text))))
}

/** Local form state only; never serialized to a business API. */
export const handlingIntentLabels = {
  CONFIRM_PROPOSAL: '拟确认字段（仅表达意向）',
  NEEDS_EVIDENCE: '请求补证（本地草稿）',
  CONFLICT: '记录冲突（本地草稿）',
  NOT_APPLICABLE: '拟标记不适用（仅表达意向）'
} as const
export type HandlingIntent = keyof typeof handlingIntentLabels
export type FieldHandlingDraft = { target: string; intent: HandlingIntent | ''; reason: string; notes: string; applicabilityBasis: string }
export function emptyFieldHandlingDraft(): FieldHandlingDraft { return { target: '', intent: '', reason: '', notes: '', applicabilityBasis: '' } }
export function handlingDraftIssues(draft: FieldHandlingDraft, row: VerificationRow | null): string[] {
  const issues: string[] = []
  const targets = row ? [...(row.field ? [row.field.field_id] : []), ...row.mappings.map(item => item.entry_key)] : []
  if (!targets.includes(draft.target)) issues.push('请选择当前字段或当前提议映射，不能借用其他字段身份。')
  if (!Object.hasOwn(handlingIntentLabels, draft.intent)) issues.push('请选择处理意向。')
  if (!draft.reason.trim() || draft.reason.length > 2000) issues.push('请填写 1 至 2000 字的处理理由。')
  if (draft.notes.length > 4000) issues.push('补充备注不能超过 4000 字。')
  if (draft.intent === 'NOT_APPLICABLE' && (!draft.applicabilityBasis.trim() || draft.applicabilityBasis.length > 2000)) issues.push('拟不适用须填写 1 至 2000 字的条件与人工依据说明；文字不代表依据已核准。')
  return issues
}

/** Authority is supplied by the mounted editor's current session, never by typed IDs. */
export type VerificationNavigationContext = { projectId: string; taskId: string; canReadMapping: boolean; canReadSourceExact: boolean }
export function fieldTargetsDestination(context?: VerificationNavigationContext | null) {
  if (!context?.canReadMapping || !context.projectId?.trim() || !context.taskId?.trim()) return null
  return { name: 'MedicalTaskFieldTargets', params: { projectId: context.projectId, taskId: context.taskId } }
}
export function sourceExactDestination(entry: MappingEntry, body: DocumentBody, preparation?: Preparation | null, context?: VerificationNavigationContext | null) {
  if (!context?.canReadSourceExact || !context.projectId?.trim() || !context.taskId?.trim()
    || preparation?.project_id !== context.projectId || preparation.task_id !== context.taskId
    || !preparation.allowed_actions.includes('READ') || !sourceForMapping(entry, preparation.source_choices)
    || !verificationRows(body, preparation).some(row => row.mappings.includes(entry))) return null
  return { name: 'MedicalSourceExactFields', params: { sourceVersion: entry.source_exact!.source_version } }
}
export function hasFieldHandlingEdits(draft: FieldHandlingDraft): boolean {
  return Boolean(draft.intent || draft.reason || draft.notes || draft.applicabilityBasis)
}
