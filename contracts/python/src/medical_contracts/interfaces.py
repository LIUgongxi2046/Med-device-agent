from types import MappingProxyType
from typing import Annotated, ClassVar, Literal, Mapping, Self

from pydantic import AfterValidator, BeforeValidator, ConfigDict, Field, JsonValue, model_validator

from .catalogue import SchemaCatalogue
from .common import COMMON_DEFINITIONS, ArtifactRef, Coverage, EvidenceDecision, Hash, Id, Ids, ImageSelection, NullableHash, NullableId, NullableText, Revision, Selection, SourceScope, TableSelection, TextSelection, Texts, Timestamp
from .dataset import EvidenceRef
from .domain import DomainModel, MeasurementDecimal, NonnegativeInt, PositiveInt, Text, unique_items
from .typed import TypedSchemaBindings


class Envelope(DomainModel):
    schema_version: Literal["1.0.0"]
    request_id: Text
    resource_revision: Revision | None
    data: JsonValue


class ErrorDetail(DomainModel):
    field: str
    code: str


class Error(DomainModel):
    schema_version: Literal["1.0.0"]
    code: str
    message: str
    request_id: str
    resource_revision: Revision | None
    allowed_actions: Texts
    retryable: bool
    operation_id: NullableId
    details: list[ErrorDetail]


class Operation(DomainModel):
    operation_id: Id
    resource_id: NullableId
    status: Literal["PROCESSING", "COMPLETED", "FAILED"]
    status_url: Annotated[str, Field(pattern=r"^/api/operations/")]


class PageInfo(DomainModel):
    page: PositiveInt
    page_size: Annotated[int, Field(ge=1, le=100)]
    total: Revision
    watermark: Revision
    filter_sha256: Hash


class Record(DomainModel):
    id: Id
    revision: Revision
    name: str
    status: Text
    updated_at: Timestamp
    allowed_actions: Texts
    detail: ArtifactRef


class RecordPage(DomainModel):
    items: list[Record]
    page_info: PageInfo


class ContainerContent(DomainModel):
    kind: Literal["DOCUMENT", "SECTION", "PARAGRAPH"]
    title: str


class TextContent(DomainModel):
    kind: Literal["TEXT"]
    text: str
    marks: Annotated[list[Literal["BOLD", "ITALIC", "UNDERLINE", "SUPERSCRIPT", "SUBSCRIPT"]], AfterValidator(unique_items)]


class ImageContent(DomainModel):
    kind: Literal["IMAGE"]
    artifact_id: Id
    sha256: Hash
    alt_text: str
    caption: str


class Cell(DomainModel):
    cell_id: Id
    row_id: Id
    column_id: Id
    row_span: PositiveInt
    column_span: PositiveInt
    node_ids: Ids


class TableContent(DomainModel):
    kind: Literal["TABLE"]
    row_ids: Annotated[Ids, Field(min_length=1)]
    column_ids: Annotated[Ids, Field(min_length=1)]
    cells: list[Cell]
    caption: str


class FormulaContent(DomainModel):
    kind: Literal["FORMULA"]
    latex: str
    description: str
    rendered_artifact_id: NullableId


NodeContent = Annotated[ContainerContent | TextContent | ImageContent | TableContent | FormulaContent, Field(discriminator="kind")]


class Node(DomainModel):
    node_id: Id
    parent_id: NullableId
    ordinal: NonnegativeInt
    field_ids: Ids
    evidence_link_ids: Ids
    content: NodeContent


class FieldValue(DomainModel):
    field_id: Id
    node_id: Id
    field_definition_id: Id
    original_value: str
    decimal_value: MeasurementDecimal | None
    unit: NullableText
    model: NullableText
    condition: NullableText
    evidence_link_ids: Ids


class EvidenceLink(DomainModel):
    evidence_link_id: Id
    node_ids: Annotated[Ids, Field(min_length=1)]
    relation: Literal["SUPPORT", "CONFLICT", "BACKGROUND"]
    evidence: EvidenceRef


class FactUsage(DomainModel):
    fact_version_id: Id
    node_ids: Ids
    field_ids: Ids


class DocumentBody(DomainModel):
    schema_version: Literal["1.0.0"]
    template_version: Id
    nodes: Annotated[list[Node], Field(min_length=1)]
    fields: list[FieldValue]
    evidence_links: list[EvidenceLink]
    fact_usages: list[FactUsage]


class DocumentFieldProjection(DomainModel):
    schema_version: Literal["1.0.0"]
    document_revision: Id
    body_sha256: Hash
    fields: list[FieldValue]


class DocumentEvidenceProjection(DomainModel):
    schema_version: Literal["1.0.0"]
    document_revision: Id
    body_sha256: Hash
    evidence_links: list[EvidenceLink]
    fact_usages: list[FactUsage]


class BodyPage(DomainModel):
    document_revision: Id
    body_sha256: Hash
    nodes: list[Node]
    fields: list[FieldValue]
    evidence_links: list[EvidenceLink]
    fact_usages: list[FactUsage]
    coverage: Coverage


class Stream(DomainModel):
    stream_id: Id
    kind: Literal["RUN", "CONVERSATION"]
    resource_id: Id


class StreamCursor(DomainModel):
    stream: Stream
    sequence: Revision
    cursor: str
    resource_manifest: ArtifactRef


class DocumentSnapshot(DomainModel):
    document_id: Id
    project_id: Id
    document_revision: Id
    revision: Revision
    plan_revision: Id
    template_version: Id
    body: ArtifactRef
    fields: ArtifactRef
    evidence_links: ArtifactRef
    body_schema_revision: Id
    check_ids: Ids
    review_ids: Ids
    signature_ids: Ids
    invalidation_reasons: Texts
    issue_watermark: Revision
    evidence_watermark: Revision
    auth_epoch: Revision
    governance_epoch: Revision
    allowed_actions: Texts
    stream_cursors: list[StreamCursor]


class RevisionInput(DomainModel):
    base_revision: Id
    expected_revision: Revision
    body_artifact_id: Id
    body_sha256: Hash
    body_schema_revision: Id
    reason: Text


class UploadRequest(DomainModel):
    purpose: Literal["DOCUMENT_BODY"]
    document_id: Id
    expected_revision: Revision
    media_type: Literal["application/json"]
    size_bytes: Revision
    sha256: Hash
    schema_ref: Literal["interfaces.schema.yaml#/$defs/DocumentBody"]
    dependency_access_set_id: Id


class UploadGrant(DomainModel):
    grant_id: Id
    upload_id: Id
    upload_url: Annotated[str, Field(pattern=r"^/(api|internal/v1)/uploads/")]
    expires_at: Timestamp
    max_part_bytes: Literal[8388608]


class UploadCompletion(DomainModel):
    sha256: Hash
    size_bytes: Revision
    expected_revision: Revision


class ArtifactReceipt(DomainModel):
    artifact_id: Id
    sha256: Hash
    schema_ref: str
    revision: Revision


class CompareRequest(DomainModel):
    document_id: Id
    left_revision: Id
    right_revision: Id


class CandidateCompareRequest(DomainModel):
    candidate_id: Id
    target_document_revision: Id


class Comparison(DomainModel):
    comparison_id: Id
    comparison_revision: Revision
    left_id: Id
    right_revision: Id
    differences: ArtifactRef
    affected_node_ids: Ids


class CandidateView(DomainModel):
    candidate_id: Id
    revision: Revision
    status: Literal["READY", "ADOPTED", "REJECTED", "STALE"]
    base_document_revision: Id
    target_node_ids: Annotated[Ids, Field(min_length=1)]
    content: ArtifactRef
    original_evidence: ArtifactRef
    new_evidence: ArtifactRef
    check_ids: Ids
    input_sha256: Hash
    snapshot_id: Id
    plan_revision: Id
    allowed_actions: Texts
    adoption_id: NullableId
    adopted_document_revision: NullableId


class OpenDomainModel(DomainModel):
    model_config = ConfigDict(extra="allow")
    __pydantic_extra__: dict[str, JsonValue] = Field(init=False)


class ReviewBaseline(OpenDomainModel):
    document_revision: Id
    expected_revision: Revision
    check_ids: Annotated[Ids, Field(min_length=1)]
    evidence_watermark: Revision
    issue_watermark: Revision


class EvidenceDecisionBatch(ReviewBaseline):
    model_config = ConfigDict(extra="forbid")
    decisions: Annotated[list[EvidenceDecision], Field(min_length=1)]


class ReviewTarget(DomainModel):
    node_id: NullableId
    section_revision: NullableId
    selection: Selection | None
    decision: Literal["CONFIRM", "RETURN"]
    reason: Text
    assignee_id: NullableId


class ReviewBatch(ReviewBaseline):
    model_config = ConfigDict(extra="forbid")
    targets: Annotated[list[ReviewTarget], Field(min_length=1)]


class DecisionBatch(DomainModel):
    batch_id: Id
    document_revision: Id
    revision: Revision
    decision_ids: Annotated[Ids, Field(min_length=1)]


class AnnotationInput(DomainModel):
    document_revision: Id
    selection: Selection
    content: Text
    expected_revision: Revision


class ReauthRequest(DomainModel):
    document_id: Id
    document_revision: Id
    purpose: Text
    signing_policy_revision: Id


class ReauthChallenge(DomainModel):
    challenge_id: Id
    authorization_url: str
    expires_at: Timestamp


class ReauthCompletion(DomainModel):
    callback_receipt_id: Id


class ReauthReceipt(DomainModel):
    reauthentication_ref: Id
    subject_id: Id
    document_id: Id
    document_revision: Id
    purpose: str
    expires_at: Timestamp


class CancelMessage(DomainModel):
    expected_revision: Revision
    reason: Text


class ConversationSnapshot(DomainModel):
    conversation_id: Id
    project_id: Id
    revision: Revision
    status: Literal["ACTIVE", "ARCHIVED", "DELETED"]
    messages: ArtifactRef
    pending_action_ids: Ids
    stream_cursor: StreamCursor


class CredentialWrite(DomainModel):
    config_id: Id
    expected_revision: Revision
    provider_kind: Text
    secret_value: Text = Field(repr=False, json_schema_extra={"writeOnly": True})


class CredentialReceipt(DomainModel):
    credential_reference_id: Id
    secret_version: str
    config_revision: Revision
    verification_id: Id


class VerificationView(DomainModel):
    verification_id: Id
    config_id: Id
    config_revision: Revision
    secret_version: str
    status: Literal["PENDING", "RUNNING", "PASSED", "FAILED", "INVALIDATED"]
    verified_at: Timestamp | None
    endpoint_identity: NullableText
    error_code: NullableText


class ManagementOperation(DomainModel):
    operation_id: str
    method: Literal["GET", "POST", "PUT", "DELETE"]
    path: Annotated[str, Field(pattern=r"^/api/")]
    request_schema_ref: NullableText
    response_schema_ref: str
    side_effect: Literal["NONE", "WRITE", "EXPORT", "SESSION_REVOKE", "JOB_DISPATCH"]
    permission_code: Text


class ManagementModule(DomainModel):
    module_id: Literal["USER", "ROLE", "MENU", "DEPARTMENT", "POST", "DICTIONARY", "PARAMETER", "NOTICE", "ONLINE_USER", "SCHEDULED_JOB", "OPERATION_LOG", "LOGIN_LOG", "SERVICE_MONITOR", "CACHE_MONITOR", "CONNECTION_POOL", "CODE_GENERATOR", "API_DOCUMENTATION", "FORM_BUILDER"]
    binding_status: Literal["BOUND", "UNBOUND", "DISABLED"]
    route_name: NullableText
    component_ref: NullableText
    source_commit: NullableText
    operations: list[ManagementOperation]
    permission_codes: Texts
    unavailable_reason: NullableText

    @model_validator(mode="after")
    def binding_fields_required(self) -> Self:
        if self.binding_status == "BOUND":
            if not self.route_name or not self.component_ref or not self.source_commit or not self.operations or not self.permission_codes or self.unavailable_reason is not None:
                raise ValueError("BOUND 管理模块必须包含组件、源码、操作及权限")
        elif self.operations or not self.unavailable_reason:
            raise ValueError("未启用管理模块必须包含原因并保持操作集合为空")
        return self


class ManagementBindings(DomainModel):
    release_id: Id
    environment_id: Id
    manifest_sha256: Hash
    modules: Annotated[list[ManagementModule], Field(min_length=18, max_length=18)]


class ReportFilters(DomainModel):
    model_config = ConfigDict(serialize_by_alias=True)
    project_id: NullableId
    from_: Timestamp = Field(alias="from")
    to: Timestamp
    actor_id: NullableId
    status: NullableText


class ReportExportRequest(DomainModel):
    report_kind: Literal["USAGE", "AUDIT", "EVALUATION"]
    filters: ReportFilters
    dataset_watermark: Revision
    metric_revision: Id
    format: Literal["CSV", "XLSX"]


class ReportExportView(DomainModel):
    report_id: Id
    status: Literal["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"]
    filter_sha256: Hash
    dataset_watermark: Revision
    metric_revision: Id
    record_count: Revision | None
    export_item_ids: Ids
    error_code: NullableText


class OperationLookup(DomainModel):
    operation_id: Text
    idempotency_key: Annotated[str, Field(min_length=16, max_length=128, pattern=r"^[\x21-\x7e]+$")]


class OperationLookupResult(DomainModel):
    lookup_status: Literal["FOUND", "NOT_FOUND", "EXPIRED"]
    operation: Operation | None


class ReconciliationRequest(DomainModel):
    expected_revision: Revision
    reason: Text
    receipt_artifact_id: NullableId
    receipt_sha256: NullableHash


class StatePayload(DomainModel):
    business_event_type: str
    status: str
    reason_code: NullableText
    read_operation_id: str


class WaitingPayload(DomainModel):
    business_event_type: str
    waiting_item_id: Id
    reason_code: str
    assignee_id: Id
    read_operation_id: Literal["TK-02"]


class DocumentPayload(DomainModel):
    business_event_type: str
    document_revision: Id
    affected_node_ids: Ids
    read_operation_id: Literal["DC-01"]


class InvalidationPayload(DomainModel):
    business_event_type: str
    document_revision: Id
    review_ids: Ids
    reason_code: str
    read_operation_id: Literal["DC-01"]


class GovernancePayload(DomainModel):
    business_event_type: str
    governance_epoch: Revision
    auth_epoch: Revision
    read_operation_id: Literal["KN-06"]


EventPayload = StatePayload | WaitingPayload | DocumentPayload | InvalidationPayload | GovernancePayload


class PublicEvent(DomainModel):
    event_mapping: ClassVar[Mapping[str, tuple[str, type[DomainModel], str]]] = MappingProxyType({
        "run.updated": ("RUN", StatePayload, "RN-02"),
        "step.updated": ("STEP", StatePayload, "RN-02"),
        "waiting_input.created": ("WAITING_ITEM", WaitingPayload, "TK-02"),
        "candidate.updated": ("CANDIDATE", StatePayload, "BX-07"),
        "document.revised": ("DOCUMENT", DocumentPayload, "DC-01"),
        "review.invalidated": ("DOCUMENT", InvalidationPayload, "DC-01"),
        "source.governance_changed": ("SOURCE_VERSION", GovernancePayload, "KN-06"),
        "export.updated": ("EXPORT", StatePayload, "EX-02"),
        "notification.created": ("NOTIFICATION", StatePayload, "OP-02"),
    })
    schema_version: Literal["1.0.0"]
    event_id: Id
    stream: Stream
    sequence: Revision
    event_type: Literal["run.updated", "step.updated", "waiting_input.created", "candidate.updated", "document.revised", "review.invalidated", "source.governance_changed", "export.updated", "notification.created"]
    tenant_id: Id
    project_id: NullableId
    task_id: NullableId
    run_id: NullableId
    resource_kind: Literal["RUN", "STEP", "WAITING_ITEM", "CANDIDATE", "DOCUMENT", "SOURCE_VERSION", "EXPORT", "NOTIFICATION"]
    resource_id: Id
    resource_revision: Revision
    occurred_at: Timestamp
    payload: EventPayload

    @model_validator(mode="after")
    def payload_matches_event(self) -> Self:
        kind, payload_type, operation = self.event_mapping[self.event_type]
        if self.resource_kind != kind or not isinstance(self.payload, payload_type) or self.payload.read_operation_id != operation:
            raise ValueError("公开事件的资源、载荷或读取操作不一致")
        return self


def require_false(value: bool) -> bool:
    if value is not False:
        raise ValueError("候选增量必须明确声明非持久内容")
    return value


class CandidateDelta(DomainModel):
    schema_version: Literal["1.0.0"]
    stream: Stream
    candidate_id: Id
    delta_sequence: Revision
    durable: Annotated[Literal[False], BeforeValidator(require_false)]
    text: str


class StepIdentity(OpenDomainModel):
    schema_version: Literal["1.0.0"]
    run_id: Id
    step_id: Id
    lease_generation: NonnegativeInt


class ActionIdentity(StepIdentity):
    action_id: Id
    input_sha256: Hash


class InputReadRequest(StepIdentity):
    model_config = ConfigDict(extra="forbid")
    input_manifest_id: Id
    kind: Literal["MANIFEST", "TEMPLATE", "ARTIFACT", "RETRIEVAL", "ISSUES"]
    resource_id: Id
    node_ids: Ids
    cursor: NullableText


class ToolRead(DomainModel):
    resource_id: Id
    content: ArtifactRef
    coverage: Coverage


class PlanCandidateRequest(ActionIdentity):
    model_config = ConfigDict(extra="forbid")
    plan_artifact_id: Id


class CandidateReceipt(DomainModel):
    candidate_id: Id
    revision: Revision
    validation_status: Literal["VALID", "WAITING_INPUT"]
    missing_requirement_ids: Texts


class WaitingRequest(ActionIdentity):
    model_config = ConfigDict(extra="forbid")
    target_revision: Id
    reason_code: str
    question: Text
    requirement_refs: Texts
    allowed_resolution_actions: Texts


class WaitingReceipt(DomainModel):
    waiting_item_id: Id
    assignee_id: Id
    status: Literal["WAITING_INPUT"]
    revision: Revision


class CheckResultRequest(ActionIdentity):
    model_config = ConfigDict(extra="forbid")
    check_id: Id
    target_kind: Literal["DOCUMENT", "CANDIDATE"]
    target_revision: Id
    rule_manifest_id: Id
    findings_artifact_id: Id
    coverage: Coverage


class CheckReceipt(DomainModel):
    check_id: Id
    revision: Revision
    issue_ids: Ids
    status: Literal["SUCCEEDED", "WAITING_INPUT", "FAILED"]


class WebAcquisitionRequest(ActionIdentity):
    model_config = ConfigDict(extra="forbid")
    question_id: Id
    approved_query_revision: Id
    network_policy_revision: Id


class ContextRequest(ActionIdentity):
    model_config = ConfigDict(extra="forbid")
    exact_input_artifact_id: Id
    source_manifest_id: Id
    prompt_revision: Id
    tokenizer_revision: str
    token_count: NonnegativeInt
    dependency_access_set_id: Id


class ContextReceipt(DomainModel):
    context_manifest_id: Id
    sha256: Hash


class ObjectRef(DomainModel):
    object_key: Annotated[str, Field(max_length=1024)]
    object_version: Annotated[str, Field(max_length=256)]
    sha256: Hash
    size_bytes: Revision
    media_type: Annotated[str, Field(max_length=128)]


class AgentArtifactRequest(ActionIdentity):
    model_config = ConfigDict(extra="forbid")
    kind: Literal["PlanCandidate", "EvidenceBundle", "FieldCheckResult", "SectionCandidate", "ClaimRecord", "ReviewFindings", "AssemblyManifest", "HandoffRecord", "ContextInput"]
    schema_ref: str
    object_ref: ObjectRef
    dependency_access_set_id: Id


class InvocationReadRequest(StepIdentity):
    model_config = ConfigDict(extra="forbid")
    invocation_id: Id
    action_id: Id


class ContentChunk(DomainModel):
    resource_id: Id
    artifact_id: Id
    schema_ref: str
    media_type: str
    encoding: Literal["UTF8", "BASE64"]
    content: str
    chunk_sha256: Hash
    full_sha256: Hash
    coverage: Coverage
    read_receipt_id: Id


class InternalUploadRequest(ActionIdentity):
    model_config = ConfigDict(extra="forbid")
    purpose: Literal["AGENT_ARTIFACT"]
    media_type: str
    size_bytes: Revision
    sha256: Hash
    schema_ref: str
    dependency_access_set_id: Id


class InternalUploadCompletion(ActionIdentity):
    model_config = ConfigDict(extra="forbid")
    expected_revision: Revision
    sha256: Hash
    size_bytes: Revision


class InternalSearchRequest(ActionIdentity):
    model_config = ConfigDict(extra="forbid")
    query: Text
    snapshot_id: Id
    retrieval_profile_revision: Id
    purpose: Literal["RESEARCH", "VERIFICATION", "EVALUATION"]
    cursor: NullableText


class InternalSearchResult(DomainModel):
    retrieval_id: Id
    status: Literal["FOUND", "EMPTY"]
    evidence_refs: list[EvidenceRef]
    missing_requirement_ids: Ids
    manifest_sha256: Hash
    coverage: Coverage
    read_receipt_id: Id


class InternalSourceReadRequest(StepIdentity):
    model_config = ConfigDict(extra="forbid")
    evidence_ref: EvidenceRef
    purpose: Literal["RESEARCH", "VERIFICATION", "PARSING", "EVALUATION"]
    cursor: NullableText


class InternalSourceReadResult(DomainModel):
    evidence_ref: EvidenceRef
    chunk: ContentChunk


class InterfaceBindings(TypedSchemaBindings):
    def __init__(self, catalogue: SchemaCatalogue):
        super().__init__(catalogue, "interfaces", {
            **COMMON_DEFINITIONS,
            "Envelope": Envelope, "Error": Error, "ErrorDetail": ErrorDetail,
            "Operation": Operation, "PageInfo": PageInfo, "Record": Record, "RecordPage": RecordPage,
            "Node": Node, "ContainerContent": ContainerContent, "TextContent": TextContent,
            "ImageContent": ImageContent, "TableContent": TableContent, "Cell": Cell,
            "FormulaContent": FormulaContent, "FieldValue": FieldValue, "EvidenceLink": EvidenceLink,
            "FactUsage": FactUsage, "DocumentBody": DocumentBody, "DocumentFieldProjection": DocumentFieldProjection,
            "DocumentEvidenceProjection": DocumentEvidenceProjection, "BodyPage": BodyPage,
            "DocumentSnapshot": DocumentSnapshot, "RevisionInput": RevisionInput,
            "UploadRequest": UploadRequest, "UploadGrant": UploadGrant, "UploadCompletion": UploadCompletion,
            "ArtifactReceipt": ArtifactReceipt, "CompareRequest": CompareRequest,
            "CandidateCompareRequest": CandidateCompareRequest, "Comparison": Comparison,
            "CandidateView": CandidateView, "ReviewBaseline": ReviewBaseline,
            "EvidenceDecisionBatch": EvidenceDecisionBatch, "ReviewTarget": ReviewTarget,
            "ReviewBatch": ReviewBatch, "DecisionBatch": DecisionBatch, "AnnotationInput": AnnotationInput,
            "ReauthRequest": ReauthRequest, "ReauthChallenge": ReauthChallenge,
            "ReauthCompletion": ReauthCompletion, "ReauthReceipt": ReauthReceipt,
            "CancelMessage": CancelMessage, "ConversationSnapshot": ConversationSnapshot,
            "CredentialWrite": CredentialWrite, "CredentialReceipt": CredentialReceipt,
            "VerificationView": VerificationView, "ManagementBindings": ManagementBindings,
            "ManagementModule": ManagementModule, "ManagementOperation": ManagementOperation,
            "ReportFilters": ReportFilters, "ReportExportRequest": ReportExportRequest,
            "ReportExportView": ReportExportView, "OperationLookup": OperationLookup,
            "OperationLookupResult": OperationLookupResult, "ReconciliationRequest": ReconciliationRequest,
            "Stream": Stream, "StreamCursor": StreamCursor, "StatePayload": StatePayload,
            "WaitingPayload": WaitingPayload, "DocumentPayload": DocumentPayload,
            "InvalidationPayload": InvalidationPayload, "GovernancePayload": GovernancePayload,
            "PublicEvent": PublicEvent, "CandidateDelta": CandidateDelta,
            "StepIdentity": StepIdentity, "ActionIdentity": ActionIdentity, "InputReadRequest": InputReadRequest,
            "ToolRead": ToolRead, "PlanCandidateRequest": PlanCandidateRequest,
            "CandidateReceipt": CandidateReceipt, "WaitingRequest": WaitingRequest, "WaitingReceipt": WaitingReceipt,
            "CheckResultRequest": CheckResultRequest, "CheckReceipt": CheckReceipt,
            "WebAcquisitionRequest": WebAcquisitionRequest, "ContextRequest": ContextRequest,
            "ContextReceipt": ContextReceipt, "ObjectRef": ObjectRef, "AgentArtifactRequest": AgentArtifactRequest,
            "InvocationReadRequest": InvocationReadRequest, "ContentChunk": ContentChunk,
            "InternalUploadRequest": InternalUploadRequest, "InternalUploadCompletion": InternalUploadCompletion,
            "InternalSearchRequest": InternalSearchRequest, "InternalSearchResult": InternalSearchResult,
            "InternalSourceReadRequest": InternalSourceReadRequest, "InternalSourceReadResult": InternalSourceReadResult,
        })
