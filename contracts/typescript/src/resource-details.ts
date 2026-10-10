import type { JsonValue, SchemaCatalogue } from './catalogue.js'
import type {
  ArtifactRef, Coverage, EvidenceDecision, Id, Ids, NullableId, NullableText, Revision, Selection, SourceScope, Texts, Timestamp,
} from './interfaces-common.js'
import { unsignedRevision, utcTime, uuidValue } from './wire.js'

export type { ArtifactRef, Id, Ids, NullableId, Revision, Texts } from './interfaces-common.js'

// 全部字段依据唯一 resource-details.schema.yaml，空值保持原有含义。
export interface TaskDetail {
  task_id: Id
  task_revision: Id
  project_id: Id
  owner_id: Id
  status: 'NEEDS_MATERIALS' | 'PLAN_PENDING' | 'READY' | 'RUNNING' | 'REVIEW_PENDING' | 'SIGN_PENDING' | 'SIGNED' | 'FAILED' | 'RETURNED' | 'CANCEL_REQUESTED' | 'CANCELLED'
  plan_revision: NullableId
  snapshot_id: NullableId
  document_ids: Ids
  missing_requirements: Requirement[]
  waiting_item_ids: Ids
  active_run_ids: Ids
}

export interface Requirement {
  requirement_id: string
  node_id: NullableId
  field_id: NullableId
  reason_code: string
  allowed_actions: Texts
}

export interface CheckDetail {
  check_id: Id
  target_kind: 'DOCUMENT' | 'CANDIDATE'
  target_id: Id
  target_revision: Id
  input_sha256: string
  rule_manifest_id: Id
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'WAITING_INPUT' | 'FAILED' | 'CANCELLED'
  coverage: Coverage
  findings: ArtifactRef
  issue_ids: Ids
  evidence_watermark: Revision
  issue_watermark: Revision
  invalidation_reason: NullableText
}

export interface EvidenceDecisionDetail {
  decision_id: Id
  batch_id: Id
  document_revision: Id
  actor_id: Id
  decided_at: Timestamp
  decision: EvidenceDecision
  check_ids: Ids
  evidence_watermark: Revision
  issue_watermark: Revision
  validity: 'VALID' | 'INVALIDATED'
  invalidation_reason: NullableText
}

export interface AnnotationDetail {
  annotation_id: Id
  document_revision: Id
  selection: Selection
  content: string
  actor_id: Id
  created_at: Timestamp
  status: 'OPEN' | 'RESOLVED' | 'RETURNED'
  location_status: 'CURRENT_VERSION' | 'ORIGINAL_VERSION'
  reply_count: Revision
}

export interface ReplyDetail {
  reply_id: Id
  annotation_id: Id
  actor_id: Id
  created_at: Timestamp
  content: string
}

export interface SignatureDetail {
  signature_id: Id
  document_id: Id
  document_revision: Id
  signer_id: Id
  signed_at: Timestamp
  purpose: string
  policy_revision: Id
  review_ids: Ids
  check_ids: Ids
  manifest_sha256: string
  auth_epoch: Revision
  governance_epoch: Revision
}

export interface Message {
  message_id: Id
  conversation_id: Id
  project_id: NullableId
  document_revision: NullableId
  selection: Selection | null
  scope: SourceScope
  sender_kind: 'USER' | 'AGENT' | 'SYSTEM'
  content: string
  run_id: NullableId
  operation_id: NullableId
  candidate_ids: Ids
  created_at: Timestamp
}

export type MessageList = Message[]

export interface ConfigurationDetail {
  config_id: Id
  kind: 'AGENT' | 'CAPABILITY' | 'MODEL' | 'CONNECTOR'
  version: Id
  content: ArtifactRef
  parameter_schema_ref: string
  dependency_version_ids: Ids
  approval_ids: Ids
  release_ids: Ids
  verification_ids: Ids
  credential_reference_id: NullableId
  endpoint_identity: NullableText
}

export interface EvaluationDetail {
  evaluation_id: Id
  sample_manifest: ArtifactRef
  rule_revision: Id
  release_candidate_ids: Ids
  score_ids: Ids
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'INSUFFICIENT_EVIDENCE'
  missing_requirements: Texts
}

export interface ScoreDetail {
  score_id: Id
  evaluation_id: Id
  sample_id: Id
  agent_version_id: Id
  dimension: string
  value: NullableText
  numerator: NullableText
  denominator: NullableText
  scorer_id: Id
  rule_revision: Id
  input: ArtifactRef
  output: ArtifactRef | null
  evidence: ArtifactRef
  supersedes: NullableId
  status: 'SCORED' | 'UNKNOWN' | 'BLOCKED'
}

export interface FeedbackDetail {
  feedback_id: Id
  task_id: NullableId
  document_revision: NullableId
  target_version: Id
  content: string
  actor_id: Id
  proposal_id: NullableId
  decision_ids: Ids
}

export interface CollectionDetail {
  collection_id: Id
  current_version: NullableId
  version_ids: Ids
  members: ArtifactRef
}

export interface FolderDetail {
  folder_id: Id
  parent_id: NullableId
  path_ids: Ids
  child_count: Revision
}

export interface TemplateDetail {
  template_version: Id
  document_type: string
  nodes: TemplateNode[]
  field_definitions: ArtifactRef
  decision_ids: Ids
}

export interface TemplateNode {
  node_id: Id
  parent_id: NullableId
  ordinal: number
  title: string
  required: boolean
  condition: 'APPLICABLE' | 'NOT_APPLICABLE' | 'UNKNOWN'
  condition_rule_revision: NullableId
  evidence_link_ids: Ids
  field_ids: Ids
  attachment_requirement_ids: Ids
  decision_ids: Ids
}

export interface ChangeProposalDetail {
  proposal_id: Id
  old_version: Id
  new_version: Id
  decision_ids: Ids
  impacts: Impact[]
  application_source_links: ArtifactRef
  coverage: Coverage
}

export interface Impact {
  resource_kind: 'DOCUMENT' | 'FIELD' | 'REVIEW' | 'INDEX' | 'APPLICATION_NODE'
  resource_id: Id
  resource_revision: Revision
  reason_code: string
  required_action: string
  status: 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'BLOCKED'
}

export type DifferenceList = Difference[]

export interface Difference {
  node_id: Id
  kind: 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED'
  left_selection: Selection | null
  right_selection: Selection | null
  field_ids: Ids
  added_evidence_link_ids: Ids
  removed_evidence_link_ids: Ids
  changed_support_link_ids: Ids
  affected_check_ids: Ids
}

export interface ResourceDetailsDefinitions {
  Id: Id; Ids: Ids; NullableId: NullableId; Revision: Revision; ArtifactRef: ArtifactRef; Texts: Texts
  TaskDetail: TaskDetail; Requirement: Requirement; CheckDetail: CheckDetail; EvidenceDecisionDetail: EvidenceDecisionDetail
  AnnotationDetail: AnnotationDetail; ReplyDetail: ReplyDetail; SignatureDetail: SignatureDetail; Message: Message; MessageList: MessageList
  ConfigurationDetail: ConfigurationDetail; EvaluationDetail: EvaluationDetail; ScoreDetail: ScoreDetail; FeedbackDetail: FeedbackDetail
  CollectionDetail: CollectionDetail; FolderDetail: FolderDetail; TemplateDetail: TemplateDetail; TemplateNode: TemplateNode
  ChangeProposalDetail: ChangeProposalDetail; Impact: Impact; DifferenceList: DifferenceList; Difference: Difference
}

export const RESOURCE_DETAILS_DEFINITIONS = [
  'Id', 'Ids', 'NullableId', 'Revision', 'ArtifactRef', 'Texts', 'TaskDetail', 'Requirement', 'CheckDetail', 'EvidenceDecisionDetail',
  'AnnotationDetail', 'ReplyDetail', 'SignatureDetail', 'Message', 'MessageList', 'ConfigurationDetail', 'EvaluationDetail', 'ScoreDetail',
  'FeedbackDetail', 'CollectionDetail', 'FolderDetail', 'TemplateDetail', 'TemplateNode', 'ChangeProposalDetail', 'Impact', 'DifferenceList', 'Difference',
] as const satisfies readonly (keyof ResourceDetailsDefinitions)[]

// 这些字段采用后端 §3.1 的表示，包含详情所引用的全部公共对象字段。
export const RESOURCE_DETAILS_WIRE_FIELDS = Object.freeze({
  Id: [
    'artifact_id', 'document_revision', 'node_id', 'image_artifact_id', 'network_policy_revision', 'approved_query_revision',
    'evidence_link_id', 'rule_revision', 'task_id', 'task_revision', 'project_id', 'owner_id', 'plan_revision', 'snapshot_id',
    'field_id', 'check_id', 'target_id', 'target_revision', 'rule_manifest_id', 'decision_id', 'batch_id', 'actor_id',
    'annotation_id', 'reply_id', 'signature_id', 'document_id', 'signer_id', 'policy_revision', 'message_id', 'conversation_id',
    'run_id', 'operation_id', 'config_id', 'version', 'credential_reference_id', 'evaluation_id', 'score_id', 'sample_id',
    'agent_version_id', 'scorer_id', 'supersedes', 'feedback_id', 'target_version', 'proposal_id', 'collection_id',
    'current_version', 'folder_id', 'parent_id', 'template_version', 'condition_rule_revision', 'old_version', 'new_version', 'resource_id',
  ],
  Ids: [
    'row_ids', 'column_ids', 'cell_ids', 'collection_versions', 'source_versions', 'document_ids', 'waiting_item_ids', 'active_run_ids',
    'issue_ids', 'check_ids', 'review_ids', 'candidate_ids', 'dependency_version_ids', 'approval_ids', 'release_ids', 'verification_ids',
    'release_candidate_ids', 'score_ids', 'decision_ids', 'version_ids', 'path_ids', 'evidence_link_ids', 'field_ids',
    'attachment_requirement_ids', 'added_evidence_link_ids', 'removed_evidence_link_ids', 'changed_support_link_ids', 'affected_check_ids',
  ],
  Revision: ['evidence_watermark', 'issue_watermark', 'reply_count', 'auth_epoch', 'governance_epoch', 'child_count', 'resource_revision'],
  Timestamp: ['decided_at', 'created_at', 'signed_at'],
})

const idFields = new Set<string>(RESOURCE_DETAILS_WIRE_FIELDS.Id)
const idsFields = new Set<string>(RESOURCE_DETAILS_WIRE_FIELDS.Ids)
const revisionFields = new Set<string>(RESOURCE_DETAILS_WIRE_FIELDS.Revision)
const timestampFields = new Set<string>(RESOURCE_DETAILS_WIRE_FIELDS.Timestamp)

function validateWireFields(value: JsonValue): void {
  if (Array.isArray(value)) {
    for (const child of value) validateWireFields(child)
    return
  }
  if (value === null || typeof value !== 'object') return
  for (const [field, child] of Object.entries(value)) {
    if (idFields.has(field) && child !== null) uuidValue(child)
    if (idsFields.has(field)) {
      if (!Array.isArray(child)) throw new Error('UUID 集合必须使用数组')
      for (const id of child) uuidValue(id)
    }
    if (revisionFields.has(field)) unsignedRevision(child)
    if (timestampFields.has(field)) utcTime(child)
    validateWireFields(child)
  }
}

export interface ResourceDetailsBinding<K extends keyof ResourceDetailsDefinitions> {
  readonly definition: K
  readonly schemaRef: string
  readonly schemaSha256: string
  readonly canonicalJson: string
  read(): ResourceDetailsDefinitions[K]
}

export function bindResourceDetails<K extends keyof ResourceDetailsDefinitions>(
  catalogue: SchemaCatalogue, definition: K, value: unknown,
): ResourceDetailsBinding<K> {
  const saved = catalogue.bind('resource-details', value, definition)
  const parsed: JsonValue = JSON.parse(saved.canonicalJson) as JsonValue
  if (definition === 'Id' || (definition === 'NullableId' && parsed !== null)) uuidValue(parsed)
  if (definition === 'Revision') unsignedRevision(parsed)
  if (definition === 'Ids') {
    if (!Array.isArray(parsed)) throw new Error('UUID 集合必须使用数组')
    for (const id of parsed) uuidValue(id)
  }
  validateWireFields(parsed)
  return Object.freeze({
    ...saved, definition,
    read: (): ResourceDetailsDefinitions[K] => JSON.parse(saved.canonicalJson) as ResourceDetailsDefinitions[K],
  })
}

export function decodeResourceDetails<K extends keyof ResourceDetailsDefinitions>(
  catalogue: SchemaCatalogue, definition: K, value: unknown,
): ResourceDetailsDefinitions[K] {
  return bindResourceDetails(catalogue, definition, value).read()
}
