package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import static com.ruoyi.medical.contracts.TypedSchemaBindings.definition;
import com.ruoyi.medical.contracts.TypedSchemaBindings.Definition;
import com.ruoyi.medical.contracts.TypedSchemaBindings.ScalarWire;

public final class ResourceBindings {
    private ResourceBindings() { }
    public static final Definition<String> ID = definition("resource-details", "Id", String.class, ScalarWire.UUID);
    public static final Definition<List<String>> IDS = definition("resource-details", "Ids", new TypeReference<List<String>>() { }, ScalarWire.UUIDS);
    public static final Definition<String> NULLABLE_ID = definition("resource-details", "NullableId", String.class, ScalarWire.NULLABLE_UUID);
    public static final Definition<String> REVISION = definition("resource-details", "Revision", String.class, ScalarWire.REVISION);
    public static final Definition<CommonProtocol.ArtifactRef> ARTIFACT_REF = definition("resource-details", "ArtifactRef", CommonProtocol.ArtifactRef.class);
    public static final Definition<List<String>> TEXTS = definition("resource-details", "Texts", new TypeReference<List<String>>() { }, ScalarWire.NONE);
    public static final Definition<ResourceProtocol.TaskDetail> TASK_DETAIL = definition("resource-details", "TaskDetail", ResourceProtocol.TaskDetail.class);
    public static final Definition<ResourceProtocol.Requirement> REQUIREMENT = definition("resource-details", "Requirement", ResourceProtocol.Requirement.class);
    public static final Definition<ResourceProtocol.CheckDetail> CHECK_DETAIL = definition("resource-details", "CheckDetail", ResourceProtocol.CheckDetail.class);
    public static final Definition<ResourceProtocol.EvidenceDecisionDetail> EVIDENCE_DECISION_DETAIL = definition("resource-details", "EvidenceDecisionDetail", ResourceProtocol.EvidenceDecisionDetail.class);
    public static final Definition<ResourceProtocol.AnnotationDetail> ANNOTATION_DETAIL = definition("resource-details", "AnnotationDetail", ResourceProtocol.AnnotationDetail.class);
    public static final Definition<ResourceProtocol.ReplyDetail> REPLY_DETAIL = definition("resource-details", "ReplyDetail", ResourceProtocol.ReplyDetail.class);
    public static final Definition<ResourceProtocol.SignatureDetail> SIGNATURE_DETAIL = definition("resource-details", "SignatureDetail", ResourceProtocol.SignatureDetail.class);
    public static final Definition<ResourceProtocol.Message> MESSAGE = definition("resource-details", "Message", ResourceProtocol.Message.class);
    public static final Definition<List<ResourceProtocol.Message>> MESSAGE_LIST = definition("resource-details", "MessageList", new TypeReference<List<ResourceProtocol.Message>>() { }, ScalarWire.NONE);
    public static final Definition<ResourceProtocol.ConfigurationDetail> CONFIGURATION_DETAIL = definition("resource-details", "ConfigurationDetail", ResourceProtocol.ConfigurationDetail.class);
    public static final Definition<ResourceProtocol.EvaluationDetail> EVALUATION_DETAIL = definition("resource-details", "EvaluationDetail", ResourceProtocol.EvaluationDetail.class);
    public static final Definition<ResourceProtocol.ScoreDetail> SCORE_DETAIL = definition("resource-details", "ScoreDetail", ResourceProtocol.ScoreDetail.class);
    public static final Definition<ResourceProtocol.FeedbackDetail> FEEDBACK_DETAIL = definition("resource-details", "FeedbackDetail", ResourceProtocol.FeedbackDetail.class);
    public static final Definition<ResourceProtocol.CollectionDetail> COLLECTION_DETAIL = definition("resource-details", "CollectionDetail", ResourceProtocol.CollectionDetail.class);
    public static final Definition<ResourceProtocol.FolderDetail> FOLDER_DETAIL = definition("resource-details", "FolderDetail", ResourceProtocol.FolderDetail.class);
    public static final Definition<ResourceProtocol.TemplateDetail> TEMPLATE_DETAIL = definition("resource-details", "TemplateDetail", ResourceProtocol.TemplateDetail.class);
    public static final Definition<ResourceProtocol.TemplateNode> TEMPLATE_NODE = definition("resource-details", "TemplateNode", ResourceProtocol.TemplateNode.class);
    public static final Definition<ResourceProtocol.ChangeProposalDetail> CHANGE_PROPOSAL_DETAIL = definition("resource-details", "ChangeProposalDetail", ResourceProtocol.ChangeProposalDetail.class);
    public static final Definition<ResourceProtocol.Impact> IMPACT = definition("resource-details", "Impact", ResourceProtocol.Impact.class);
    public static final Definition<List<ResourceProtocol.Difference>> DIFFERENCE_LIST = definition("resource-details", "DifferenceList", new TypeReference<List<ResourceProtocol.Difference>>() { }, ScalarWire.NONE);
    public static final Definition<ResourceProtocol.Difference> DIFFERENCE = definition("resource-details", "Difference", ResourceProtocol.Difference.class);
    public static List<Definition<?>> definitions() {
        return List.of(ID, IDS, NULLABLE_ID, REVISION, ARTIFACT_REF, TEXTS, TASK_DETAIL, REQUIREMENT, CHECK_DETAIL, EVIDENCE_DECISION_DETAIL,
            ANNOTATION_DETAIL, REPLY_DETAIL, SIGNATURE_DETAIL, MESSAGE, MESSAGE_LIST, CONFIGURATION_DETAIL, EVALUATION_DETAIL,
            SCORE_DETAIL, FEEDBACK_DETAIL, COLLECTION_DETAIL, FOLDER_DETAIL, TEMPLATE_DETAIL, TEMPLATE_NODE, CHANGE_PROPOSAL_DETAIL,
            IMPACT, DIFFERENCE_LIST, DIFFERENCE);
    }
}
