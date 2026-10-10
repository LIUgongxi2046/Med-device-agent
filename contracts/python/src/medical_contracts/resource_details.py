from typing import Literal

from .catalogue import SchemaCatalogue
from .common import COMMON_DEFINITIONS, ArtifactRef, Coverage, EvidenceDecision, Id, Ids, NullableId, NullableText, Selection, SourceScope, Texts
from .domain import DomainModel, Hash, NonnegativeInt, Revision, Text, Timestamp
from .typed import TypedSchemaBindings


class Requirement(DomainModel):
    requirement_id: str
    node_id: NullableId
    field_id: NullableId
    reason_code: str
    allowed_actions: Texts


class TaskDetail(DomainModel):
    task_id: Id
    task_revision: Id
    project_id: Id
    owner_id: Id
    status: Literal["NEEDS_MATERIALS", "PLAN_PENDING", "READY", "RUNNING", "REVIEW_PENDING", "SIGN_PENDING", "SIGNED", "FAILED", "RETURNED", "CANCEL_REQUESTED", "CANCELLED"]
    plan_revision: NullableId
    snapshot_id: NullableId
    document_ids: Ids
    missing_requirements: list[Requirement]
    waiting_item_ids: Ids
    active_run_ids: Ids


class CheckDetail(DomainModel):
    check_id: Id
    target_kind: Literal["DOCUMENT", "CANDIDATE"]
    target_id: Id
    target_revision: Id
    input_sha256: Hash
    rule_manifest_id: Id
    status: Literal["QUEUED", "RUNNING", "SUCCEEDED", "WAITING_INPUT", "FAILED", "CANCELLED"]
    coverage: Coverage
    findings: ArtifactRef
    issue_ids: Ids
    evidence_watermark: Revision
    issue_watermark: Revision
    invalidation_reason: NullableText


class EvidenceDecisionDetail(DomainModel):
    decision_id: Id
    batch_id: Id
    document_revision: Id
    actor_id: Id
    decided_at: Timestamp
    decision: EvidenceDecision
    check_ids: Ids
    evidence_watermark: Revision
    issue_watermark: Revision
    validity: Literal["VALID", "INVALIDATED"]
    invalidation_reason: NullableText


class AnnotationDetail(DomainModel):
    annotation_id: Id
    document_revision: Id
    selection: Selection
    content: str
    actor_id: Id
    created_at: Timestamp
    status: Literal["OPEN", "RESOLVED", "RETURNED"]
    location_status: Literal["CURRENT_VERSION", "ORIGINAL_VERSION"]
    reply_count: Revision


class ReplyDetail(DomainModel):
    reply_id: Id
    annotation_id: Id
    actor_id: Id
    created_at: Timestamp
    content: Text


class SignatureDetail(DomainModel):
    signature_id: Id
    document_id: Id
    document_revision: Id
    signer_id: Id
    signed_at: Timestamp
    purpose: str
    policy_revision: Id
    review_ids: Ids
    check_ids: Ids
    manifest_sha256: Hash
    auth_epoch: Revision
    governance_epoch: Revision


class Message(DomainModel):
    message_id: Id
    conversation_id: Id
    project_id: NullableId
    document_revision: NullableId
    selection: Selection | None
    scope: SourceScope
    sender_kind: Literal["USER", "AGENT", "SYSTEM"]
    content: str
    run_id: NullableId
    operation_id: NullableId
    candidate_ids: Ids
    created_at: Timestamp


class ConfigurationDetail(DomainModel):
    config_id: Id
    kind: Literal["AGENT", "CAPABILITY", "MODEL", "CONNECTOR"]
    version: Id
    content: ArtifactRef
    parameter_schema_ref: Text
    dependency_version_ids: Ids
    approval_ids: Ids
    release_ids: Ids
    verification_ids: Ids
    credential_reference_id: NullableId
    endpoint_identity: NullableText


class EvaluationDetail(DomainModel):
    evaluation_id: Id
    sample_manifest: ArtifactRef
    rule_revision: Id
    release_candidate_ids: Ids
    score_ids: Ids
    status: Literal["QUEUED", "RUNNING", "COMPLETED", "FAILED", "INSUFFICIENT_EVIDENCE"]
    missing_requirements: Texts


class ScoreDetail(DomainModel):
    score_id: Id
    evaluation_id: Id
    sample_id: Id
    agent_version_id: Id
    dimension: str
    value: NullableText
    numerator: NullableText
    denominator: NullableText
    scorer_id: Id
    rule_revision: Id
    input: ArtifactRef
    output: ArtifactRef | None
    evidence: ArtifactRef
    supersedes: NullableId
    status: Literal["SCORED", "UNKNOWN", "BLOCKED"]


class FeedbackDetail(DomainModel):
    feedback_id: Id
    task_id: NullableId
    document_revision: NullableId
    target_version: Id
    content: str
    actor_id: Id
    proposal_id: NullableId
    decision_ids: Ids


class CollectionDetail(DomainModel):
    collection_id: Id
    current_version: NullableId
    version_ids: Ids
    members: ArtifactRef


class FolderDetail(DomainModel):
    folder_id: Id
    parent_id: NullableId
    path_ids: Ids
    child_count: Revision


class TemplateNode(DomainModel):
    node_id: Id
    parent_id: NullableId
    ordinal: NonnegativeInt
    title: str
    required: bool
    condition: Literal["APPLICABLE", "NOT_APPLICABLE", "UNKNOWN"]
    condition_rule_revision: NullableId
    evidence_link_ids: Ids
    field_ids: Ids
    attachment_requirement_ids: Ids
    decision_ids: Ids


class TemplateDetail(DomainModel):
    template_version: Id
    document_type: str
    nodes: list[TemplateNode]
    field_definitions: ArtifactRef
    decision_ids: Ids


class Impact(DomainModel):
    resource_kind: Literal["DOCUMENT", "FIELD", "REVIEW", "INDEX", "APPLICATION_NODE"]
    resource_id: Id
    resource_revision: Revision
    reason_code: str
    required_action: str
    status: Literal["PENDING", "CONFIRMED", "COMPLETED", "BLOCKED"]


class ChangeProposalDetail(DomainModel):
    proposal_id: Id
    old_version: Id
    new_version: Id
    decision_ids: Ids
    impacts: list[Impact]
    application_source_links: ArtifactRef
    coverage: Coverage


class Difference(DomainModel):
    node_id: Id
    kind: Literal["ADDED", "REMOVED", "CHANGED", "UNCHANGED"]
    left_selection: Selection | None
    right_selection: Selection | None
    field_ids: Ids
    added_evidence_link_ids: Ids
    removed_evidence_link_ids: Ids
    changed_support_link_ids: Ids
    affected_check_ids: Ids


MessageList = list[Message]
DifferenceList = list[Difference]


class ResourceBindings(TypedSchemaBindings):
    common_definitions = COMMON_DEFINITIONS

    def __init__(self, catalogue: SchemaCatalogue):
        super().__init__(catalogue, "resource-details", {
            "Id": Id, "Ids": Ids, "NullableId": NullableId, "Revision": Revision,
            "ArtifactRef": ArtifactRef, "Texts": Texts, "TaskDetail": TaskDetail,
            "Requirement": Requirement, "CheckDetail": CheckDetail, "EvidenceDecisionDetail": EvidenceDecisionDetail,
            "AnnotationDetail": AnnotationDetail, "ReplyDetail": ReplyDetail, "SignatureDetail": SignatureDetail,
            "Message": Message, "MessageList": MessageList, "ConfigurationDetail": ConfigurationDetail,
            "EvaluationDetail": EvaluationDetail, "ScoreDetail": ScoreDetail, "FeedbackDetail": FeedbackDetail,
            "CollectionDetail": CollectionDetail, "FolderDetail": FolderDetail, "TemplateDetail": TemplateDetail,
            "TemplateNode": TemplateNode, "ChangeProposalDetail": ChangeProposalDetail, "Impact": Impact,
            "DifferenceList": DifferenceList, "Difference": Difference,
        })
