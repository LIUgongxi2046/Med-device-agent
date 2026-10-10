from typing import Annotated, Literal, Self

from pydantic import AfterValidator, Field, model_validator

from .catalogue import SchemaCatalogue
from .dataset import EvidenceRef
from .domain import DomainModel, Hash, MeasurementDecimal, MoneyDecimal, NonnegativeInt, PositiveInt, Text, Timestamp, WireId, unique_items
from .typed import TypedSchemaBindings


Id = WireId
NullableId = Id | None
Ids = Annotated[list[Id], AfterValidator(unique_items)]
NullableText = Text | None
Decimal = MeasurementDecimal
Key = Annotated[str, Field(pattern=r"^[A-Za-z][A-Za-z0-9_-]*$")]
Keys = Annotated[list[Key], AfterValidator(unique_items)]
EvidenceRefs = list[EvidenceRef]


class ObjectRef(DomainModel):
    object_key: Text
    object_version: Text
    sha256: Hash
    size_bytes: PositiveInt
    media_type: Text


class PlanSection(DomainModel):
    section_id: Id
    requirement_node_ids: Ids
    goal: Text
    owner_id: NullableId
    method: Literal["DETERMINISTIC", "MODEL_ASSISTED", "HUMAN_PROVIDED"]
    depends_on_section_ids: Ids
    completion_rule_ids: Ids


class ResearchQuestion(DomainModel):
    question_key: Key
    section_id: Id
    question: Text
    requirement_ids: Ids
    completion_rule_ids: Ids
    depends_on_question_keys: Keys


class PlanCandidate(DomainModel):
    template_version: Id
    profile_revision: Id
    requirement_node_ids: Ids
    sections: Annotated[list[PlanSection], Field(min_length=1)]
    questions: list[ResearchQuestion]
    pending_condition_ids: Ids


class EvidenceLink(DomainModel):
    evidence_ref: EvidenceRef
    relation: Literal["SUPPORT", "CONFLICT", "BACKGROUND"]


class QuestionCoverage(DomainModel):
    question_id: Id
    status: Literal["VERIFIED", "INSUFFICIENT", "CONFLICT"]
    evidence_links: list[EvidenceLink]
    completion_check_ids: Ids
    missing_requirement_ids: Ids


class EvidenceBundle(DomainModel):
    retrieval_ids: Ids
    question_coverage: list[QuestionCoverage]
    selected_refs: EvidenceRefs
    conflict_refs: EvidenceRefs
    missing_requirement_ids: Ids
    read_receipt_ids: Ids


class FieldValue(DomainModel):
    value_kind: Literal["TEXT", "LONG_TEXT", "DATE", "DECIMAL", "ENUM", "MULTISELECT", "RELATION", "TABLE", "IMAGE", "FORMULA", "FILE", "EMPTY"]
    raw_text: NullableText
    decimal_value: Decimal | None
    precision: NonnegativeInt | None
    unit: NullableText
    selected_ids: Ids
    object_ref: ObjectRef | None

    @model_validator(mode="after")
    def decimal_fields_required(self) -> Self:
        if self.value_kind == "DECIMAL" and (self.raw_text is None or self.decimal_value is None or self.precision is None):
            raise ValueError("DECIMAL 字段必须包含原始文本、数值和精度")
        return self


class FieldComparison(DomainModel):
    field_id: Id
    row_id: NullableId
    model_ids: Ids
    condition_ids: Ids
    source_value: FieldValue
    candidate_value: FieldValue
    comparison_rule_id: Id
    conversion_rule_id: NullableId
    result: Literal["MATCH", "CONFLICT", "MISSING", "UNKNOWN_CONDITION", "NOT_APPLICABLE"]
    evidence_refs: EvidenceRefs


class MethodCoverage(DomainModel):
    indicator_id: Id
    model_id: Id
    condition_id: Id
    method_ids: Ids
    evidence_refs: EvidenceRefs
    result: Literal["COMPLETE", "INCOMPLETE", "UNKNOWN_CONDITION"]


class FieldCheckResult(DomainModel):
    target_artifact_id: Id
    rule_revision: Id
    entries: list[FieldComparison]
    method_coverage: list[MethodCoverage]
    missing_requirement_ids: Ids


class TextAnchor(DomainModel):
    kind: Literal["TEXT"]
    node_id: Id
    start: NonnegativeInt
    end: PositiveInt
    text_sha256: Hash


class StructuredAnchor(DomainModel):
    kind: Literal["STRUCTURED"]
    node_id: Id
    row_id: NullableId
    field_id: NullableId
    content_sha256: Hash


Anchor = Annotated[TextAnchor | StructuredAnchor, Field(discriminator="kind")]


class ClaimRecord(DomainModel):
    claim_id: Id
    anchor: Anchor
    claim_kind: Literal["PRODUCT_FACT", "REGULATORY_REQUIREMENT", "METHOD_REQUIREMENT", "STUDY_RESULT", "TRANSITION"]
    evidence_links: list[EvidenceLink]

    @model_validator(mode="after")
    def evidence_required(self) -> Self:
        if self.claim_kind != "TRANSITION" and not self.evidence_links:
            raise ValueError("事实与要求必须包含证据关联")
        return self


class NodeManifestItem(DomainModel):
    node_id: Id
    parent_id: NullableId
    section_id: Id
    kind: Literal["ROOT", "SECTION", "PARAGRAPH", "TEXT", "TABLE", "ROW", "CELL", "IMAGE", "FORMULA", "LIST", "ITEM", "ATTACHMENT"]
    field_id: NullableId
    row_id: NullableId
    content_sha256: Hash


class FieldAssignment(DomainModel):
    field_id: Id
    row_id: NullableId
    node_id: Id
    fact_version: NullableId
    value: FieldValue
    evidence_refs: EvidenceRefs


class AttachmentBinding(DomainModel):
    requirement_id: Id
    node_id: Id
    source_version: Id
    evidence_refs: EvidenceRefs


class IssueProposal(DomainModel):
    issue_key: Key
    code: Text
    severity_code: Text
    rule_id: Id
    anchor: Anchor
    claim_ids: Ids
    message: Text
    evidence_refs: EvidenceRefs


class SectionDraft(DomainModel):
    body_schema_revision: Id
    body_json: Text
    claim_records: list[ClaimRecord]
    field_assignments: list[FieldAssignment]
    attachment_bindings: list[AttachmentBinding]
    issue_proposals: list[IssueProposal]


class SectionCandidate(DomainModel):
    base_document_revision: Id
    target_sections: Annotated[list[Id], Field(min_length=1), AfterValidator(unique_items)]
    body_object_ref: ObjectRef
    body_schema_revision: Id
    node_manifest: Annotated[list[NodeManifestItem], Field(min_length=1)]
    claim_records: list[ClaimRecord]
    field_assignments: list[FieldAssignment]
    attachment_bindings: list[AttachmentBinding]
    issue_proposals: list[IssueProposal]


class ClaimCheck(DomainModel):
    claim_id: Id
    access_check: Literal["PASS", "FAIL"]
    locator_check: Literal["PASS", "FAIL", "BLOCKED"]
    support_check: Literal["SUPPORTED", "CONTRADICTED", "INSUFFICIENT", "NOT_APPLICABLE"]
    rule_ids: Ids
    read_receipt_ids: Ids
    rationale: Text


class RuleCheck(DomainModel):
    rule_id: Id
    target_node_ids: Ids
    result: Literal["PASS", "FAIL", "BLOCKED", "NOT_APPLICABLE"]
    evidence_refs: EvidenceRefs
    rationale: Text


class ReviewFindings(DomainModel):
    target_kind: Literal["CANDIDATE", "DOCUMENT"]
    target_revision: Id
    target_sha256: Hash
    rule_revision: Id
    required_claim_ids: Ids
    required_rule_ids: Ids
    claim_checks: list[ClaimCheck]
    rule_checks: list[RuleCheck]
    issue_proposals: list[IssueProposal]
    coverage_complete: bool


class SectionVersion(DomainModel):
    section_id: Id
    version_kind: Literal["CANDIDATE", "DOCUMENT"]
    version_id: Id
    ordinal: NonnegativeInt
    content_sha256: Hash


class AssemblyManifest(DomainModel):
    target_kind: Literal["CANDIDATE_SET", "DOCUMENT"]
    document_revision: NullableId
    sections: Annotated[list[SectionVersion], Field(min_length=1)]
    requirement_node_ids: Ids
    fact_versions: Ids
    evidence_refs: EvidenceRefs
    rule_revision: Id
    check_ids: Ids
    review_ids: Ids
    unresolved_issue_ids: Ids
    format_profile_revision: Id

    @model_validator(mode="after")
    def document_versions_required(self) -> Self:
        if self.target_kind == "DOCUMENT" and (self.document_revision is None or any(section.version_kind != "DOCUMENT" for section in self.sections)):
            raise ValueError("DOCUMENT 组装必须引用文档版本和文档章节版本")
        return self


class FailedAction(DomainModel):
    action_id: Id
    error_code: Text
    receipt_id: Id


class RemainingLimits(DomainModel):
    wall_time_seconds: NonnegativeInt
    model_requests: NonnegativeInt
    tool_calls: NonnegativeInt
    research_rounds: NonnegativeInt
    revision_rounds: NonnegativeInt
    total_tokens: NonnegativeInt
    cost_amount: Annotated[MoneyDecimal, Field(pattern=r"^(0|[1-9][0-9]*)(\.[0-9]+)?$")]
    currency: Annotated[str, Field(pattern=r"^[A-Z]{3}$")]


class HandoffRecord(DomainModel):
    completed_action_refs: Ids
    artifact_refs: Ids
    evidence_refs: EvidenceRefs
    unresolved_item_ids: Ids
    pending_decision_refs: Ids
    failed_action_refs: list[FailedAction]
    inflight_invocation_ids: Ids
    budget_revision: Id
    remaining_limits: RemainingLimits
    created_at: Timestamp


class PlanOutput(DomainModel):
    artifact_kind: Literal["PLAN_CANDIDATE"]
    content: PlanCandidate


class EvidenceOutput(DomainModel):
    artifact_kind: Literal["EVIDENCE_BUNDLE"]
    content: EvidenceBundle


class FieldOutput(DomainModel):
    artifact_kind: Literal["FIELD_CHECK_RESULT"]
    content: FieldCheckResult


class SectionOutput(DomainModel):
    artifact_kind: Literal["SECTION_CANDIDATE"]
    content: SectionCandidate


class ClaimOutput(DomainModel):
    artifact_kind: Literal["CLAIM_RECORD"]
    content: ClaimRecord


class ReviewOutput(DomainModel):
    artifact_kind: Literal["REVIEW_FINDINGS"]
    content: ReviewFindings


class AssemblyOutput(DomainModel):
    artifact_kind: Literal["ASSEMBLY_MANIFEST"]
    content: AssemblyManifest


class HandoffOutput(DomainModel):
    artifact_kind: Literal["HANDOFF_RECORD"]
    content: HandoffRecord


ArtifactOutput = Annotated[PlanOutput | EvidenceOutput | FieldOutput | SectionOutput | ClaimOutput | ReviewOutput | AssemblyOutput | HandoffOutput, Field(discriminator="artifact_kind")]


class ProfessionalArtifact(DomainModel):
    schema_version: Literal["1.0.0"]
    execution_kind: Literal["TASK", "SYSTEM_JOB"]
    tenant_id: Id
    project_id: NullableId
    run_id: Id
    step_id: Id
    input_manifest_sha256: Hash
    plan_revision: NullableId
    snapshot_id: NullableId
    target_section_ids: Ids
    dependency_access_set_id: Id
    content_sha256: Hash
    output: ArtifactOutput

    @model_validator(mode="after")
    def task_context_required(self) -> Self:
        if self.execution_kind == "TASK" and (self.project_id is None or self.plan_revision is None or self.snapshot_id is None):
            raise ValueError("TASK 产物必须关联项目、计划版本和资料快照")
        return self


class ArtifactBindings(TypedSchemaBindings):
    def __init__(self, catalogue: SchemaCatalogue):
        super().__init__(catalogue, "artifacts", {
            "Id": Id, "NullableId": NullableId, "Ids": Ids, "Text": Text,
            "NullableText": NullableText, "Hash": Hash, "Decimal": Decimal,
            "Key": Key, "Keys": Keys, "EvidenceRef": EvidenceRef, "EvidenceRefs": EvidenceRefs,
            "ObjectRef": ObjectRef, "ArtifactOutput": ArtifactOutput,
            "PlanOutput": PlanOutput, "EvidenceOutput": EvidenceOutput, "FieldOutput": FieldOutput,
            "SectionOutput": SectionOutput, "ClaimOutput": ClaimOutput, "ReviewOutput": ReviewOutput,
            "AssemblyOutput": AssemblyOutput, "HandoffOutput": HandoffOutput,
            "PlanCandidate": PlanCandidate, "PlanSection": PlanSection, "ResearchQuestion": ResearchQuestion,
            "EvidenceLink": EvidenceLink, "EvidenceBundle": EvidenceBundle, "QuestionCoverage": QuestionCoverage,
            "FieldValue": FieldValue, "FieldCheckResult": FieldCheckResult, "FieldComparison": FieldComparison,
            "MethodCoverage": MethodCoverage, "TextAnchor": TextAnchor, "StructuredAnchor": StructuredAnchor,
            "Anchor": Anchor, "ClaimRecord": ClaimRecord, "NodeManifestItem": NodeManifestItem,
            "FieldAssignment": FieldAssignment, "AttachmentBinding": AttachmentBinding, "IssueProposal": IssueProposal,
            "SectionDraft": SectionDraft, "SectionCandidate": SectionCandidate, "ClaimCheck": ClaimCheck,
            "RuleCheck": RuleCheck, "ReviewFindings": ReviewFindings, "SectionVersion": SectionVersion,
            "AssemblyManifest": AssemblyManifest, "FailedAction": FailedAction,
            "RemainingLimits": RemainingLimits, "HandoffRecord": HandoffRecord,
        }, ProfessionalArtifact)
