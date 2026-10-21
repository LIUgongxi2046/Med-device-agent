/** Wire types for task-field-target.schema.json. Runtime validation remains authoritative. */
import type { TemplateConditionResult } from './template-applicability'
import type { TemplateField, TemplateNode, TemplateRepeat, TemplateAttachment } from './template-approval'
export type MappingAuthor = {
  schema_version: "1.0.0"
  project_id: Id
  task_revision: Id
  task_input_sha256: Hash
  plan_revision: Id
  plan_sha256: Hash
  template_version: Id
  template_sha256: Hash
  profile_revision: Id
  scope_snapshot: Id
  expected_revision: Revision
  base_mapping_id: Id | null
  reason: Reason
  mappings: Array<MappingEntry>
}

export type Id = string

export type Hash = string

export type Revision = string

export type Reason = string

export type MappingEntry = {
  entry_key: Id
  field_definition_id: Id
  section_id: Id
  node_id: Id
  row_id: Id | null
  model_ids: Ids
  condition_ids: Ids
  model_context: ContextState
  condition_context: ContextState
  applicability: Applicability
  source_exact: SourceExactRef | null
  question_ids: Ids
}

export type Ids = Array<Id>

export type ContextState = "KNOWN_GLOBAL" | "KNOWN_SCOPED" | "UNRESOLVED"

export type Applicability = "APPLICABLE" | "UNKNOWN"

export type SourceExactRef = {
  source_version: Id
  field_revision: Id
  manifest_sha256: Hash
  ordinal: number
  field_kind: SourceExactKind
}

export type SourceExactKind = "MODEL" | "PTR_NUMBER" | "DOCUMENT_NUMBER" | "STANDARD_NUMBER" | "STANDARD_YEAR"

export type MappingManifest = {
  schema_version: "1.0.0"
  tenant_id: Id
  task_id: Id
  project_id: Id
  task_revision: Id
  task_input_sha256: Hash
  plan_revision: Id
  plan_sha256: Hash
  template_version: Id
  template_sha256: Hash
  profile_revision: Id
  scope_snapshot: Id
  assignment_id: Id
  status: "PROPOSED"
  inventory: Array<InventoryItem>
  mappings: Array<MappingEntry>
  template_nodes: Array<TemplateNode>
  repeat_definitions: Array<TemplateRepeat>
  attachment_requirements: Array<TemplateAttachment>
  condition_results: TemplateConditionResult[]
}

export type InventoryItem = {
  field_definition_id: Id
  section_id: Id | null
  node_id: Id
  question_ids: Ids
  key: string
  name: string
  value_type: string
  required: boolean
  condition: Condition
  repeat_id: Id | null
  row_id_required: boolean
  state: "MISSING_MAPPING" | "MISSING_SOURCE" | "UNRESOLVED_CONTEXT" | "UNKNOWN_CONDITION" | "NOT_APPLICABLE" | "UNSUPPORTED_TYPE" | "UNASSIGNED_SECTION" | "MAPPED"
  entry_keys: Ids
  field_definition: TemplateField
}

export type Condition = {
  mode: "ALWAYS" | "RULE"
  rule_revision: VersionRef | null
  engine_revision: string | null
  input_keys: Array<string>
}

export type VersionRef = {
  version_id: Id
  content_sha256: Hash
}

export type MappingReceipt = {
  schema_version: "1.0.0"
  tenant_id: Id
  project_id: Id
  task_id: Id
  operation_id: Id
  audit_id: Id
  event_id: Id
  request_sha256: Hash
  schema_manifest_sha256: Hash
  created_by: Id
  created_at: Instant
  classification: Classification
  assignment_id: Id
  assignment_sha256: Hash
  task_revision: Id
  plan_revision: Id
  revision: Positive
  status: "PROPOSED"
}

export type Instant = string

export type Classification = "PUBLIC" | "INTERNAL" | "RESTRICTED" | "PERSONAL_HEALTH"

export type Positive = string

export type MappingView = {
  schema_version: "1.0.0"
  manifest: MappingManifest
  assignment_sha256: Hash
  revision: Positive
  is_current: boolean
}

export type Preparation = {
  schema_version: "1.0.0"
  tenant_id: Id
  task_id: Id
  project_id: Id
  task_revision: Id
  task_input_sha256: Hash
  plan_revision: Id
  plan_sha256: Hash
  template_version: Id
  template_sha256: Hash
  profile_revision: Id
  scope_snapshot: Id
  head_revision: Revision
  base_mapping_id: Id | null
  inventory: Array<InventoryItem>
  sections: Array<PlanSection>
  source_choices: Array<SourceChoice>
  current_mapping: MappingView | null
  production: Status
  allowed_actions: Array<"READ" | "WRITE_MAPPING" | "PRODUCE" | "AUTHOR_INPUT">
  template_nodes: Array<TemplateNode>
  repeat_definitions: Array<TemplateRepeat>
  attachment_requirements: Array<TemplateAttachment>
  condition_results: TemplateConditionResult[]
  prerequisites: Array<"CONFIGURATION_REQUIRED" | "MAPPING_WRITE_PERMISSION_REQUIRED" | "PRODUCER_PERMISSION_REQUIRED" | "MAPPING_REQUIRED" | "UNRESOLVED_INVENTORY">
}

export type PlanSection = {
  section_id: Id
  requirement_node_ids: Ids
  question_ids: Ids
}

export type SourceChoice = {
  source_exact: SourceExactRef
  raw_value: string
}

export type Status = {
  schema_version: "1.0.0"
  tenant_id: Id
  project_id: Id
  task_id: Id
  state: "NOT_STARTED" | "INTENT_PERSISTED" | "TRANSFER_UNCERTAIN" | "REGISTERED" | "WAITING_INPUT"
  assignment_id: Id | null
  assignment_sha256: Hash | null
  write_id: Id | null
  artifact_id: Id | null
  receipt: ProduceReceipt | null
  reason: Reason | null
}

export type ProduceReceipt = {
  schema_version: "1.0.0"
  tenant_id: Id
  project_id: Id
  task_id: Id
  operation_id: Id
  audit_id: Id
  event_id: Id
  request_sha256: Hash
  schema_manifest_sha256: Hash
  created_by: Id
  created_at: Instant
  classification: Classification
  registration_id: Id
  write_id: Id
  artifact_id: Id
  artifact_kind: "STRICT_FIELD_TARGET"
  owner_kind: "TASK"
  run_id: null
  document_revision: null
  task_revision: Id
  plan_revision: Id
  assignment_id: Id
  assignment_sha256: Hash
  intent_sha256: Hash
  object_ref: ObjectRef
  source_read_receipt_ids: Ids
  status: "REGISTERED"
}

export type ObjectRef = {
  object_key: string
  object_version: string
  sha256: Hash
  size_bytes: Positive
  media_type: string
}

export type Produce = {
  schema_version: "1.0.0"
  project_id: Id
  task_revision: Id
  plan_revision: Id
  assignment_id: Id
  assignment_sha256: Hash
  expected_revision: Positive
  reason: Reason
}

export type Recovery = {
  schema_version: "1.0.0"
  project_id: Id
  original_idempotency_key: Key
}

export type Key = string

export type InputAuthor = {
  schema_version: "1.0.0"
  project_id: Id
  task_id: Id
  task_revision: Id
  task_input_sha256: Hash
  plan_revision: Id
  plan_sha256: Hash
  assignment_id: Id
  assignment_sha256: Hash
  artifact_id: Id
  artifact_sha256: Hash
  object_version: string
  registration_id: Id
  producing_receipt_sha256: Hash
  release_id: Id
  publication_approval_sha256: Hash
  role_version: VersionRef
  execution_policy: VersionRef
  template_version: Id
  template_sha256: Hash
  artifact_schema_sha256: Hash
  consumer_node_kind: "N03"
  occurrence_ids: Ids
  maximum_read_bytes: Positive
  object_ref: ObjectRef
  expected_revision: "0"
  classification: Classification
  reason: Reason
}

export type InputManifest = {
  schema_version: "1.0.0"
  tenant_id: Id
  capability_id: Id
  project_id: Id
  task_id: Id
  task_revision: Id
  task_input_sha256: Hash
  plan_revision: Id
  plan_sha256: Hash
  assignment_id: Id
  assignment_sha256: Hash
  artifact_id: Id
  artifact_sha256: Hash
  object_version: string
  registration_id: Id
  producing_receipt_sha256: Hash
  release_id: Id
  publication_approval_sha256: Hash
  role_version: VersionRef
  execution_policy: VersionRef
  template_version: Id
  template_sha256: Hash
  artifact_schema_sha256: Hash
  consumer_node_kind: "N03"
  occurrence_ids: Ids
  maximum_read_bytes: Positive
  object_ref: ObjectRef
  artifact_kind: "STRICT_FIELD_TARGET"
  action: "artifact.read"
  purpose: "TASK_FIELD_TARGET_INPUT"
  created_by: Id
  created_at: Instant
  classification: Classification
}

export type InputDecide = {
  schema_version: "1.0.0"
  project_id: Id
  expected_revision: Positive
  capability_sha256: Hash
  decision: "APPROVE" | "DISABLE"
  reason: Reason
}

export type InputReceipt = {
  schema_version: "1.0.0"
  tenant_id: Id
  project_id: Id
  task_id: Id
  operation_id: Id
  audit_id: Id
  event_id: Id
  request_sha256: Hash
  schema_manifest_sha256: Hash
  created_by: Id
  created_at: Instant
  classification: Classification
  capability_id: Id
  capability_sha256: Hash
  decision_id: Id | null
  status: "DRAFT" | "APPROVED" | "DISABLED"
  revision: Positive
}

export type InputView = {
  schema_version: "1.0.0"
  manifest: InputManifest
  capability_sha256: Hash
  status: "DRAFT" | "APPROVED" | "DISABLED"
  revision: Positive
  latest_decision_id: Id | null
  allowed_actions: Array<"READ" | "APPROVE" | "DISABLE">
}

export type InputContext = {
  schema_version: "1.0.0"
  project_id: Id
  task_id: Id
  candidates: Array<InputAuthor>
  capabilities: Array<InputView>
  allowed_actions: Array<"READ" | "AUTHOR">
}
