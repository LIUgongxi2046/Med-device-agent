import type { JsonValue } from './catalogue.js'
import type { EvidenceRef } from './dataset.js'
import type { ArtifactRef, Coverage, EvidenceDecision, Hash, Id, Ids, NullableHash, NullableId, NullableText, Revision, Selection, Texts, Timestamp } from './interfaces-common.js'
export type { Id, Revision, Hash, NullableId, NullableHash, NullableText, Ids, Texts, Timestamp, Coverage, ArtifactRef, Selection, TextSelection, ImageSelection, TableSelection, SourceScope, EvidenceDecision } from './interfaces-common.js'

// 字段及联合条件来自 interfaces.schema.yaml；数值范围与格式由运行校验执行。
export interface Envelope { schema_version: '1.0.0'; request_id: string; resource_revision: Revision | null; data: JsonValue }
export interface Error { schema_version: '1.0.0'; code: string; message: string; request_id: string; resource_revision: Revision | null; allowed_actions: Texts; retryable: boolean; operation_id: NullableId; details: ErrorDetail[] }
export interface ErrorDetail { field: string; code: string }
export interface Operation { operation_id: Id; resource_id: NullableId; status: 'PROCESSING' | 'COMPLETED' | 'FAILED'; status_url: string }
export interface PageInfo { page: number; page_size: number; total: Revision; watermark: Revision; filter_sha256: Hash }
export interface Record { id: Id; revision: Revision; name: string; status: string; updated_at: Timestamp; allowed_actions: Texts; detail: ArtifactRef }
export interface RecordPage { items: Record[]; page_info: PageInfo }
export interface Node { node_id: Id; parent_id: NullableId; ordinal: number; field_ids: Ids; evidence_link_ids: Ids; content: ContainerContent | TextContent | ImageContent | TableContent | FormulaContent }
export interface ContainerContent { kind: 'DOCUMENT' | 'SECTION' | 'PARAGRAPH'; title: string }
export interface TextContent { kind: 'TEXT'; text: string; marks: ('BOLD' | 'ITALIC' | 'UNDERLINE' | 'SUPERSCRIPT' | 'SUBSCRIPT')[] }
export interface ImageContent { kind: 'IMAGE'; artifact_id: Id; sha256: Hash; alt_text: string; caption: string }
export interface TableContent { kind: 'TABLE'; row_ids: Ids; column_ids: Ids; cells: Cell[]; caption: string }
export interface Cell { cell_id: Id; row_id: Id; column_id: Id; row_span: number; column_span: number; node_ids: Ids }
export interface FormulaContent { kind: 'FORMULA'; latex: string; description: string; rendered_artifact_id: NullableId }
export interface FieldValue { field_id: Id; node_id: Id; field_definition_id: Id; original_value: string; decimal_value: string | null; unit: NullableText; model: NullableText; condition: NullableText; evidence_link_ids: Ids }
export interface EvidenceLink { evidence_link_id: Id; node_ids: Ids; relation: 'SUPPORT' | 'CONFLICT' | 'BACKGROUND'; evidence: EvidenceRef }
export interface FactUsage { fact_version_id: Id; node_ids: Ids; field_ids: Ids }
export interface DocumentBody { schema_version: '1.0.0'; template_version: Id; nodes: Node[]; fields: FieldValue[]; evidence_links: EvidenceLink[]; fact_usages: FactUsage[] }
export interface DocumentFieldProjection { schema_version: '1.0.0'; document_revision: Id; body_sha256: Hash; fields: FieldValue[] }
export interface DocumentEvidenceProjection { schema_version: '1.0.0'; document_revision: Id; body_sha256: Hash; evidence_links: EvidenceLink[]; fact_usages: FactUsage[] }
export interface BodyPage { document_revision: Id; body_sha256: Hash; nodes: Node[]; fields: FieldValue[]; evidence_links: EvidenceLink[]; fact_usages: FactUsage[]; coverage: Coverage }
export interface DocumentSnapshot {
  document_id: Id; project_id: Id; document_revision: Id; revision: Revision; plan_revision: Id; template_version: Id
  body: ArtifactRef; fields: ArtifactRef; evidence_links: ArtifactRef; body_schema_revision: Id; check_ids: Ids; review_ids: Ids; signature_ids: Ids
  invalidation_reasons: Texts; issue_watermark: Revision; evidence_watermark: Revision; auth_epoch: Revision; governance_epoch: Revision; allowed_actions: Texts; stream_cursors: StreamCursor[]
}
export interface RevisionInput { base_revision: Id; expected_revision: Revision; body_artifact_id: Id; body_sha256: Hash; body_schema_revision: Id; reason: string }
export interface UploadRequest { purpose: 'DOCUMENT_BODY'; document_id: Id; expected_revision: Revision; media_type: 'application/json'; size_bytes: Revision; sha256: Hash; schema_ref: 'interfaces.schema.yaml#/$defs/DocumentBody'; dependency_access_set_id: Id }
export interface UploadGrant { grant_id: Id; upload_id: Id; upload_url: string; expires_at: Timestamp; max_part_bytes: 8388608 }
export interface UploadCompletion { sha256: Hash; size_bytes: Revision; expected_revision: Revision }
export interface ArtifactReceipt { artifact_id: Id; sha256: Hash; schema_ref: string; revision: Revision }
export interface CompareRequest { document_id: Id; left_revision: Id; right_revision: Id }
export interface CandidateCompareRequest { candidate_id: Id; target_document_revision: Id }
export interface Comparison { comparison_id: Id; comparison_revision: Revision; left_id: Id; right_revision: Id; differences: ArtifactRef; affected_node_ids: Ids }
export interface CandidateView { candidate_id: Id; revision: Revision; status: 'READY' | 'ADOPTED' | 'REJECTED' | 'STALE'; base_document_revision: Id; target_node_ids: Ids; content: ArtifactRef; original_evidence: ArtifactRef; new_evidence: ArtifactRef; check_ids: Ids; input_sha256: Hash; snapshot_id: Id; plan_revision: Id; allowed_actions: Texts; adoption_id: NullableId; adopted_document_revision: NullableId }
export interface ReviewBaseline { document_revision: Id; expected_revision: Revision; check_ids: Ids; evidence_watermark: Revision; issue_watermark: Revision }
export interface EvidenceDecisionBatch extends ReviewBaseline { decisions: EvidenceDecision[] }
export interface ReviewTarget { node_id: NullableId; section_revision: NullableId; selection: Selection | null; decision: 'CONFIRM' | 'RETURN'; reason: string; assignee_id: NullableId }
export interface ReviewBatch extends ReviewBaseline { targets: ReviewTarget[] }
export interface DecisionBatch { batch_id: Id; document_revision: Id; revision: Revision; decision_ids: Ids }
export interface AnnotationInput { document_revision: Id; selection: Selection; content: string; expected_revision: Revision }
export interface ReauthRequest { document_id: Id; document_revision: Id; purpose: string; signing_policy_revision: Id }
export interface ReauthChallenge { challenge_id: Id; authorization_url: string; expires_at: Timestamp }
export interface ReauthCompletion { callback_receipt_id: Id }
export interface ReauthReceipt { reauthentication_ref: Id; subject_id: Id; document_id: Id; document_revision: Id; purpose: string; expires_at: Timestamp }
export interface CancelMessage { expected_revision: Revision; reason: string }
export interface ConversationSnapshot { conversation_id: Id; project_id: Id; revision: Revision; status: 'ACTIVE' | 'ARCHIVED' | 'DELETED'; messages: ArtifactRef; pending_action_ids: Ids; stream_cursor: StreamCursor }
export interface CredentialWrite { config_id: Id; expected_revision: Revision; provider_kind: string; secret_value: string }
export interface CredentialReceipt { credential_reference_id: Id; secret_version: string; config_revision: Revision; verification_id: Id }
export interface VerificationView { verification_id: Id; config_id: Id; config_revision: Revision; secret_version: string; status: 'PENDING' | 'RUNNING' | 'PASSED' | 'FAILED' | 'INVALIDATED'; verified_at: Timestamp | null; endpoint_identity: NullableText; error_code: NullableText }
export interface ManagementBindings { release_id: Id; environment_id: Id; manifest_sha256: Hash; modules: ManagementModule[] }
export type ManagementModule = {
  module_id: 'USER' | 'ROLE' | 'MENU' | 'DEPARTMENT' | 'POST' | 'DICTIONARY' | 'PARAMETER' | 'NOTICE' | 'ONLINE_USER' | 'SCHEDULED_JOB' | 'OPERATION_LOG' | 'LOGIN_LOG' | 'SERVICE_MONITOR' | 'CACHE_MONITOR' | 'CONNECTION_POOL' | 'CODE_GENERATOR' | 'API_DOCUMENTATION' | 'FORM_BUILDER'
} & (
  { binding_status: 'BOUND'; route_name: string; component_ref: string; source_commit: string; operations: [ManagementOperation, ...ManagementOperation[]]; permission_codes: [string, ...string[]]; unavailable_reason: null }
  | { binding_status: 'UNBOUND' | 'DISABLED'; route_name: NullableText; component_ref: NullableText; source_commit: NullableText; operations: []; permission_codes: Texts; unavailable_reason: string }
)
export interface ManagementOperation { operation_id: string; method: 'GET' | 'POST' | 'PUT' | 'DELETE'; path: string; request_schema_ref: NullableText; response_schema_ref: string; side_effect: 'NONE' | 'WRITE' | 'EXPORT' | 'SESSION_REVOKE' | 'JOB_DISPATCH'; permission_code: string }
export interface ReportFilters { project_id: NullableId; from: Timestamp; to: Timestamp; actor_id: NullableId; status: NullableText }
export interface ReportExportRequest { report_kind: 'USAGE' | 'AUDIT' | 'EVALUATION'; filters: ReportFilters; dataset_watermark: Revision; metric_revision: Id; format: 'CSV' | 'XLSX' }
export interface ReportExportView { report_id: Id; status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'; filter_sha256: Hash; dataset_watermark: Revision; metric_revision: Id; record_count: Revision | null; export_item_ids: Ids; error_code: NullableText }
export interface OperationLookup { operation_id: string; idempotency_key: string }
export interface OperationLookupResult { lookup_status: 'FOUND' | 'NOT_FOUND' | 'EXPIRED'; operation: Operation | null }
export interface ReconciliationRequest { expected_revision: Revision; reason: string; receipt_artifact_id: NullableId; receipt_sha256: NullableHash }
export interface Stream { stream_id: Id; kind: 'RUN' | 'CONVERSATION'; resource_id: Id }
export interface StreamCursor { stream: Stream; sequence: Revision; cursor: string; resource_manifest: ArtifactRef }
export interface StatePayload { business_event_type: string; status: string; reason_code: NullableText; read_operation_id: string }
export interface WaitingPayload { business_event_type: string; waiting_item_id: Id; reason_code: string; assignee_id: Id; read_operation_id: 'TK-02' }
export interface DocumentPayload { business_event_type: string; document_revision: Id; affected_node_ids: Ids; read_operation_id: 'DC-01' }
export interface InvalidationPayload { business_event_type: string; document_revision: Id; review_ids: Ids; reason_code: string; read_operation_id: 'DC-01' }
export interface GovernancePayload { business_event_type: string; governance_epoch: Revision; auth_epoch: Revision; read_operation_id: 'KN-06' }
export type PublicEvent = {
  schema_version: '1.0.0'; event_id: Id; stream: Stream; sequence: Revision; tenant_id: Id; project_id: NullableId; task_id: NullableId; run_id: NullableId; resource_id: Id; resource_revision: Revision; occurred_at: Timestamp
} & (
  { event_type: 'run.updated'; resource_kind: 'RUN'; payload: StatePayload & { read_operation_id: 'RN-02' } }
  | { event_type: 'step.updated'; resource_kind: 'STEP'; payload: StatePayload & { read_operation_id: 'RN-02' } }
  | { event_type: 'waiting_input.created'; resource_kind: 'WAITING_ITEM'; payload: WaitingPayload }
  | { event_type: 'candidate.updated'; resource_kind: 'CANDIDATE'; payload: StatePayload & { read_operation_id: 'BX-07' } }
  | { event_type: 'document.revised'; resource_kind: 'DOCUMENT'; payload: DocumentPayload }
  | { event_type: 'review.invalidated'; resource_kind: 'DOCUMENT'; payload: InvalidationPayload }
  | { event_type: 'source.governance_changed'; resource_kind: 'SOURCE_VERSION'; payload: GovernancePayload }
  | { event_type: 'export.updated'; resource_kind: 'EXPORT'; payload: StatePayload & { read_operation_id: 'EX-02' } }
  | { event_type: 'notification.created'; resource_kind: 'NOTIFICATION'; payload: StatePayload & { read_operation_id: 'OP-02' } }
)
export interface CandidateDelta { schema_version: '1.0.0'; stream: Stream; candidate_id: Id; delta_sequence: Revision; durable: false; text: string }
export interface StepIdentity { schema_version: '1.0.0'; run_id: Id; step_id: Id; lease_generation: number }
export interface ActionIdentity extends StepIdentity { action_id: Id; input_sha256: Hash }
export interface InputReadRequest extends StepIdentity { input_manifest_id: Id; kind: 'MANIFEST' | 'TEMPLATE' | 'ARTIFACT' | 'RETRIEVAL' | 'ISSUES'; resource_id: Id; node_ids: Ids; cursor: NullableText }
export interface ToolRead { resource_id: Id; content: ArtifactRef; coverage: Coverage }
export interface PlanCandidateRequest extends ActionIdentity { plan_artifact_id: Id }
export interface CandidateReceipt { candidate_id: Id; revision: Revision; validation_status: 'VALID' | 'WAITING_INPUT'; missing_requirement_ids: Texts }
export interface WaitingRequest extends ActionIdentity { target_revision: Id; reason_code: string; question: string; requirement_refs: Texts; allowed_resolution_actions: Texts }
export interface WaitingReceipt { waiting_item_id: Id; assignee_id: Id; status: 'WAITING_INPUT'; revision: Revision }
export interface CheckResultRequest extends ActionIdentity { check_id: Id; target_kind: 'DOCUMENT' | 'CANDIDATE'; target_revision: Id; rule_manifest_id: Id; findings_artifact_id: Id; coverage: Coverage }
export interface CheckReceipt { check_id: Id; revision: Revision; issue_ids: Ids; status: 'SUCCEEDED' | 'WAITING_INPUT' | 'FAILED' }
export interface WebAcquisitionRequest extends ActionIdentity { question_id: Id; approved_query_revision: Id; network_policy_revision: Id }
export interface ContextRequest extends ActionIdentity { exact_input_artifact_id: Id; source_manifest_id: Id; prompt_revision: Id; tokenizer_revision: string; token_count: number; dependency_access_set_id: Id }
export interface ContextReceipt { context_manifest_id: Id; sha256: Hash }
export interface ObjectRef { object_key: string; object_version: string; sha256: Hash; size_bytes: Revision; media_type: string }
export interface AgentArtifactRequest extends ActionIdentity { kind: 'PlanCandidate' | 'EvidenceBundle' | 'FieldCheckResult' | 'SectionCandidate' | 'ClaimRecord' | 'ReviewFindings' | 'AssemblyManifest' | 'HandoffRecord' | 'ContextInput'; schema_ref: string; object_ref: ObjectRef; dependency_access_set_id: Id }
export interface InvocationReadRequest extends StepIdentity { invocation_id: Id; action_id: Id }
export interface ContentChunk { resource_id: Id; artifact_id: Id; schema_ref: string; media_type: string; encoding: 'UTF8' | 'BASE64'; content: string; chunk_sha256: Hash; full_sha256: Hash; coverage: Coverage; read_receipt_id: Id }
export interface InternalUploadRequest extends ActionIdentity { purpose: 'AGENT_ARTIFACT'; media_type: string; size_bytes: Revision; sha256: Hash; schema_ref: string; dependency_access_set_id: Id }
export interface InternalUploadCompletion extends ActionIdentity { expected_revision: Revision; sha256: Hash; size_bytes: Revision }
export interface InternalSearchRequest extends ActionIdentity { query: string; snapshot_id: Id; retrieval_profile_revision: Id; purpose: 'RESEARCH' | 'VERIFICATION' | 'EVALUATION'; cursor: NullableText }
export interface InternalSearchResult { retrieval_id: Id; status: 'FOUND' | 'EMPTY'; evidence_refs: EvidenceRef[]; missing_requirement_ids: Ids; manifest_sha256: Hash; coverage: Coverage; read_receipt_id: Id }
export interface InternalSourceReadRequest extends StepIdentity { evidence_ref: EvidenceRef; purpose: 'RESEARCH' | 'VERIFICATION' | 'PARSING' | 'EVALUATION'; cursor: NullableText }
export interface InternalSourceReadResult { evidence_ref: EvidenceRef; chunk: ContentChunk }
