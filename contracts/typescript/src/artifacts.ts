import type { JsonValue, SchemaCatalogue } from './catalogue.js'
import type { EvidenceRef } from './dataset.js'
import { decimalValue, utcTime, uuidValue } from './wire.js'
export type { EvidenceRef } from './dataset.js'

// 原始产物 Schema 独立限定数字和身份，EvidenceRef 保持登记包的文本身份。
export type Id = string
export type NullableId = Id | null
export type Ids = Id[]
export type Text = string
export type NullableText = Text | null
export type Hash = string
export type Decimal = string
export type Key = string
export type Keys = Key[]
export type EvidenceRefs = EvidenceRef[]
export interface ObjectRef { object_key: Text; object_version: Text; sha256: Hash; size_bytes: number; media_type: Text }
export type ArtifactOutput = PlanOutput | EvidenceOutput | FieldOutput | SectionOutput | ClaimOutput | ReviewOutput | AssemblyOutput | HandoffOutput
export interface PlanOutput { artifact_kind: 'PLAN_CANDIDATE'; content: PlanCandidate }
export interface EvidenceOutput { artifact_kind: 'EVIDENCE_BUNDLE'; content: EvidenceBundle }
export interface FieldOutput { artifact_kind: 'FIELD_CHECK_RESULT'; content: FieldCheckResult }
export interface SectionOutput { artifact_kind: 'SECTION_CANDIDATE'; content: SectionCandidate }
export interface ClaimOutput { artifact_kind: 'CLAIM_RECORD'; content: ClaimRecord }
export interface ReviewOutput { artifact_kind: 'REVIEW_FINDINGS'; content: ReviewFindings }
export interface AssemblyOutput { artifact_kind: 'ASSEMBLY_MANIFEST'; content: AssemblyManifest }
export interface HandoffOutput { artifact_kind: 'HANDOFF_RECORD'; content: HandoffRecord }
export interface PlanCandidate { template_version: Id; profile_revision: Id; requirement_node_ids: Ids; sections: PlanSection[]; questions: ResearchQuestion[]; pending_condition_ids: Ids }
export interface PlanSection { section_id: Id; requirement_node_ids: Ids; goal: Text; owner_id: NullableId; method: 'DETERMINISTIC' | 'MODEL_ASSISTED' | 'HUMAN_PROVIDED'; depends_on_section_ids: Ids; completion_rule_ids: Ids }
export interface ResearchQuestion { question_key: Key; section_id: Id; question: Text; requirement_ids: Ids; completion_rule_ids: Ids; depends_on_question_keys: Keys }
export interface EvidenceLink { evidence_ref: EvidenceRef; relation: 'SUPPORT' | 'CONFLICT' | 'BACKGROUND' }
export interface EvidenceBundle { retrieval_ids: Ids; question_coverage: QuestionCoverage[]; selected_refs: EvidenceRefs; conflict_refs: EvidenceRefs; missing_requirement_ids: Ids; read_receipt_ids: Ids }
export interface QuestionCoverage { question_id: Id; status: 'VERIFIED' | 'INSUFFICIENT' | 'CONFLICT'; evidence_links: EvidenceLink[]; completion_check_ids: Ids; missing_requirement_ids: Ids }
export type FieldValue = {
  unit: NullableText; selected_ids: Ids; object_ref: ObjectRef | null
} & (
  { value_kind: 'DECIMAL'; raw_text: Text; decimal_value: Decimal; precision: number }
  | { value_kind: 'TEXT' | 'LONG_TEXT' | 'DATE' | 'ENUM' | 'MULTISELECT' | 'RELATION' | 'TABLE' | 'IMAGE' | 'FORMULA' | 'FILE' | 'EMPTY'; raw_text: NullableText; decimal_value: Decimal | null; precision: number | null }
)
export interface FieldCheckResult { target_artifact_id: Id; rule_revision: Id; entries: FieldComparison[]; method_coverage: MethodCoverage[]; missing_requirement_ids: Ids }
export interface FieldComparison { field_id: Id; row_id: NullableId; model_ids: Ids; condition_ids: Ids; source_value: FieldValue; candidate_value: FieldValue; comparison_rule_id: Id; conversion_rule_id: NullableId; result: 'MATCH' | 'CONFLICT' | 'MISSING' | 'UNKNOWN_CONDITION' | 'NOT_APPLICABLE'; evidence_refs: EvidenceRefs }
export interface MethodCoverage { indicator_id: Id; model_id: Id; condition_id: Id; method_ids: Ids; evidence_refs: EvidenceRefs; result: 'COMPLETE' | 'INCOMPLETE' | 'UNKNOWN_CONDITION' }
export interface TextAnchor { kind: 'TEXT'; node_id: Id; start: number; end: number; text_sha256: Hash }
export interface StructuredAnchor { kind: 'STRUCTURED'; node_id: Id; row_id: NullableId; field_id: NullableId; content_sha256: Hash }
export type Anchor = TextAnchor | StructuredAnchor
export type ClaimRecord = { claim_id: Id; anchor: Anchor } & (
  { claim_kind: 'PRODUCT_FACT' | 'REGULATORY_REQUIREMENT' | 'METHOD_REQUIREMENT' | 'STUDY_RESULT'; evidence_links: [EvidenceLink, ...EvidenceLink[]] }
  | { claim_kind: 'TRANSITION'; evidence_links: EvidenceLink[] }
)
export interface NodeManifestItem { node_id: Id; parent_id: NullableId; section_id: Id; kind: 'ROOT' | 'SECTION' | 'PARAGRAPH' | 'TEXT' | 'TABLE' | 'ROW' | 'CELL' | 'IMAGE' | 'FORMULA' | 'LIST' | 'ITEM' | 'ATTACHMENT'; field_id: NullableId; row_id: NullableId; content_sha256: Hash }
export interface FieldAssignment { field_id: Id; row_id: NullableId; node_id: Id; fact_version: NullableId; value: FieldValue; evidence_refs: EvidenceRefs }
export interface AttachmentBinding { requirement_id: Id; node_id: Id; source_version: Id; evidence_refs: EvidenceRefs }
export interface IssueProposal { issue_key: Key; code: Text; severity_code: Text; rule_id: Id; anchor: Anchor; claim_ids: Ids; message: Text; evidence_refs: EvidenceRefs }
export interface SectionDraft { body_schema_revision: Id; body_json: Text; claim_records: ClaimRecord[]; field_assignments: FieldAssignment[]; attachment_bindings: AttachmentBinding[]; issue_proposals: IssueProposal[] }
export interface SectionCandidate { base_document_revision: Id; target_sections: Ids; body_object_ref: ObjectRef; body_schema_revision: Id; node_manifest: NodeManifestItem[]; claim_records: ClaimRecord[]; field_assignments: FieldAssignment[]; attachment_bindings: AttachmentBinding[]; issue_proposals: IssueProposal[] }
export interface ClaimCheck { claim_id: Id; access_check: 'PASS' | 'FAIL'; locator_check: 'PASS' | 'FAIL' | 'BLOCKED'; support_check: 'SUPPORTED' | 'CONTRADICTED' | 'INSUFFICIENT' | 'NOT_APPLICABLE'; rule_ids: Ids; read_receipt_ids: Ids; rationale: Text }
export interface RuleCheck { rule_id: Id; target_node_ids: Ids; result: 'PASS' | 'FAIL' | 'BLOCKED' | 'NOT_APPLICABLE'; evidence_refs: EvidenceRefs; rationale: Text }
export interface ReviewFindings { target_kind: 'CANDIDATE' | 'DOCUMENT'; target_revision: Id; target_sha256: Hash; rule_revision: Id; required_claim_ids: Ids; required_rule_ids: Ids; claim_checks: ClaimCheck[]; rule_checks: RuleCheck[]; issue_proposals: IssueProposal[]; coverage_complete: boolean }
export interface SectionVersion { section_id: Id; version_kind: 'CANDIDATE' | 'DOCUMENT'; version_id: Id; ordinal: number; content_sha256: Hash }
export type AssemblyManifest = {
  requirement_node_ids: Ids; fact_versions: Ids; evidence_refs: EvidenceRefs; rule_revision: Id; check_ids: Ids; review_ids: Ids; unresolved_issue_ids: Ids; format_profile_revision: Id
} & (
  { target_kind: 'DOCUMENT'; document_revision: Id; sections: (SectionVersion & { version_kind: 'DOCUMENT' })[] }
  | { target_kind: 'CANDIDATE_SET'; document_revision: NullableId; sections: SectionVersion[] }
)
export interface FailedAction { action_id: Id; error_code: Text; receipt_id: Id }
export interface RemainingLimits { wall_time_seconds: number; model_requests: number; tool_calls: number; research_rounds: number; revision_rounds: number; total_tokens: number; cost_amount: Decimal; currency: string }
export interface HandoffRecord { completed_action_refs: Ids; artifact_refs: Ids; evidence_refs: EvidenceRefs; unresolved_item_ids: Ids; pending_decision_refs: Ids; failed_action_refs: FailedAction[]; inflight_invocation_ids: Ids; budget_revision: Id; remaining_limits: RemainingLimits; created_at: string }
export type ProfessionalArtifact = {
  schema_version: '1.0.0'; tenant_id: Id; run_id: Id; step_id: Id; input_manifest_sha256: Hash; target_section_ids: Ids; dependency_access_set_id: Id; content_sha256: Hash; output: ArtifactOutput
} & (
  { execution_kind: 'TASK'; project_id: Id; plan_revision: Id; snapshot_id: Id }
  | { execution_kind: 'SYSTEM_JOB'; project_id: NullableId; plan_revision: NullableId; snapshot_id: NullableId }
)

export interface ArtifactDefinitions {
  Id: Id; NullableId: NullableId; Ids: Ids; Text: Text; NullableText: NullableText; Hash: Hash; Decimal: Decimal; Key: Key; Keys: Keys; EvidenceRef: EvidenceRef; EvidenceRefs: EvidenceRefs
  ObjectRef: ObjectRef; ArtifactOutput: ArtifactOutput; PlanOutput: PlanOutput; EvidenceOutput: EvidenceOutput; FieldOutput: FieldOutput; SectionOutput: SectionOutput; ClaimOutput: ClaimOutput; ReviewOutput: ReviewOutput; AssemblyOutput: AssemblyOutput; HandoffOutput: HandoffOutput
  PlanCandidate: PlanCandidate; PlanSection: PlanSection; ResearchQuestion: ResearchQuestion; EvidenceLink: EvidenceLink; EvidenceBundle: EvidenceBundle; QuestionCoverage: QuestionCoverage; FieldValue: FieldValue; FieldCheckResult: FieldCheckResult; FieldComparison: FieldComparison; MethodCoverage: MethodCoverage
  TextAnchor: TextAnchor; StructuredAnchor: StructuredAnchor; Anchor: Anchor; ClaimRecord: ClaimRecord; NodeManifestItem: NodeManifestItem; FieldAssignment: FieldAssignment; AttachmentBinding: AttachmentBinding; IssueProposal: IssueProposal; SectionDraft: SectionDraft; SectionCandidate: SectionCandidate
  ClaimCheck: ClaimCheck; RuleCheck: RuleCheck; ReviewFindings: ReviewFindings; SectionVersion: SectionVersion; AssemblyManifest: AssemblyManifest; FailedAction: FailedAction; RemainingLimits: RemainingLimits; HandoffRecord: HandoffRecord
}
export const ARTIFACT_DEFINITIONS = [
  'Id', 'NullableId', 'Ids', 'Text', 'NullableText', 'Hash', 'Decimal', 'Key', 'Keys', 'EvidenceRef', 'EvidenceRefs', 'ObjectRef', 'ArtifactOutput', 'PlanOutput', 'EvidenceOutput', 'FieldOutput', 'SectionOutput', 'ClaimOutput', 'ReviewOutput', 'AssemblyOutput', 'HandoffOutput',
  'PlanCandidate', 'PlanSection', 'ResearchQuestion', 'EvidenceLink', 'EvidenceBundle', 'QuestionCoverage', 'FieldValue', 'FieldCheckResult', 'FieldComparison', 'MethodCoverage', 'TextAnchor', 'StructuredAnchor', 'Anchor', 'ClaimRecord', 'NodeManifestItem', 'FieldAssignment', 'AttachmentBinding', 'IssueProposal', 'SectionDraft', 'SectionCandidate', 'ClaimCheck', 'RuleCheck', 'ReviewFindings', 'SectionVersion', 'AssemblyManifest', 'FailedAction', 'RemainingLimits', 'HandoffRecord',
] as const satisfies readonly (keyof ArtifactDefinitions)[]

export const ARTIFACT_WIRE_FIELDS = {
  Id: ['tenant_id', 'project_id', 'run_id', 'step_id', 'plan_revision', 'snapshot_id', 'dependency_access_set_id', 'template_version', 'profile_revision', 'section_id', 'owner_id', 'question_id', 'target_artifact_id', 'rule_revision', 'field_id', 'row_id', 'comparison_rule_id', 'conversion_rule_id', 'indicator_id', 'model_id', 'condition_id', 'node_id', 'claim_id', 'parent_id', 'fact_version', 'requirement_id', 'source_version', 'rule_id', 'body_schema_revision', 'base_document_revision', 'target_revision', 'version_id', 'document_revision', 'format_profile_revision', 'action_id', 'receipt_id', 'budget_revision'],
  Ids: ['target_section_ids', 'requirement_node_ids', 'pending_condition_ids', 'depends_on_section_ids', 'completion_rule_ids', 'requirement_ids', 'retrieval_ids', 'missing_requirement_ids', 'read_receipt_ids', 'completion_check_ids', 'selected_ids', 'model_ids', 'condition_ids', 'method_ids', 'claim_ids', 'target_sections', 'rule_ids', 'target_node_ids', 'required_claim_ids', 'required_rule_ids', 'fact_versions', 'check_ids', 'review_ids', 'unresolved_issue_ids', 'completed_action_refs', 'artifact_refs', 'unresolved_item_ids', 'pending_decision_refs', 'inflight_invocation_ids'],
  EvidenceRef: ['evidence_ref'],
  EvidenceRefs: ['selected_refs', 'conflict_refs', 'evidence_refs'],
} as const
const ids = new Set<string>(ARTIFACT_WIRE_FIELDS.Id)
const arrays = new Set<string>(ARTIFACT_WIRE_FIELDS.Ids)
const evidence = new Set<string>([...ARTIFACT_WIRE_FIELDS.EvidenceRef, ...ARTIFACT_WIRE_FIELDS.EvidenceRefs])

export function validateArtifactWire(value: JsonValue): void {
  if (Array.isArray(value)) { for (const child of value) validateArtifactWire(child); return }
  if (value === null || typeof value !== 'object') return
  for (const [field, child] of Object.entries(value)) {
    // 原始 Schema 已验证 EvidenceRef，文本来源身份不能转换为生产 UUID。
    if (evidence.has(field)) continue
    if (child !== null && ids.has(field)) uuidValue(child)
    if (arrays.has(field)) {
      if (!Array.isArray(child)) throw new Error('身份集合必须使用数组')
      for (const id of child) uuidValue(id)
    }
    if (field === 'decimal_value' && child !== null) decimalValue(child, 38, 12)
    if (field === 'cost_amount') decimalValue(child, 24, 8)
    if (field === 'created_at') utcTime(child)
    validateArtifactWire(child)
  }
}
export interface ArtifactBinding<T> { readonly schemaRef: string; readonly schemaSha256: string; readonly canonicalJson: string; read(): T }
export function bindArtifact<K extends keyof ArtifactDefinitions>(catalogue: SchemaCatalogue, definition: K, value: unknown): ArtifactBinding<ArtifactDefinitions[K]>
export function bindArtifact(catalogue: SchemaCatalogue, definition: undefined, value: unknown): ArtifactBinding<ProfessionalArtifact>
export function bindArtifact(catalogue: SchemaCatalogue, definition: keyof ArtifactDefinitions | undefined, value: unknown): ArtifactBinding<unknown> {
  const saved = catalogue.bind('artifacts', value, definition)
  const parsed = JSON.parse(saved.canonicalJson) as JsonValue
  if ((definition === 'Id' || definition === 'NullableId') && parsed !== null) uuidValue(parsed)
  if (definition === 'Ids') {
    if (!Array.isArray(parsed)) throw new Error('身份集合必须使用数组')
    for (const id of parsed) uuidValue(id)
  }
  if (definition === 'Decimal') decimalValue(parsed, 38, 12)
  if (definition !== 'EvidenceRef' && definition !== 'EvidenceRefs') validateArtifactWire(parsed)
  return Object.freeze({ ...saved, read: (): unknown => JSON.parse(saved.canonicalJson) })
}
