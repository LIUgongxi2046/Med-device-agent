package com.ruoyi.medical.contracts;

import java.util.ArrayList;
import java.util.List;
import static com.ruoyi.medical.contracts.TypedSchemaBindings.definition;
import com.ruoyi.medical.contracts.TypedSchemaBindings.Definition;

public final class InterfaceBindings {
    private InterfaceBindings() { }
    public static final Definition<InterfaceProtocol.Envelope> ENVELOPE = definition("interfaces", "Envelope", InterfaceProtocol.Envelope.class);
    public static final Definition<InterfaceProtocol.Error> ERROR = definition("interfaces", "Error", InterfaceProtocol.Error.class);
    public static final Definition<InterfaceProtocol.ErrorDetail> ERROR_DETAIL = definition("interfaces", "ErrorDetail", InterfaceProtocol.ErrorDetail.class);
    public static final Definition<InterfaceProtocol.Operation> OPERATION = definition("interfaces", "Operation", InterfaceProtocol.Operation.class);
    public static final Definition<InterfaceProtocol.PageInfo> PAGE_INFO = definition("interfaces", "PageInfo", InterfaceProtocol.PageInfo.class);
    public static final Definition<InterfaceProtocol.Record> RECORD = definition("interfaces", "Record", InterfaceProtocol.Record.class);
    public static final Definition<InterfaceProtocol.RecordPage> RECORD_PAGE = definition("interfaces", "RecordPage", InterfaceProtocol.RecordPage.class);
    public static final Definition<InterfaceProtocol.Node> NODE = definition("interfaces", "Node", InterfaceProtocol.Node.class);
    public static final Definition<InterfaceProtocol.ContainerContent> CONTAINER_CONTENT = definition("interfaces", "ContainerContent", InterfaceProtocol.ContainerContent.class);
    public static final Definition<InterfaceProtocol.TextContent> TEXT_CONTENT = definition("interfaces", "TextContent", InterfaceProtocol.TextContent.class);
    public static final Definition<InterfaceProtocol.ImageContent> IMAGE_CONTENT = definition("interfaces", "ImageContent", InterfaceProtocol.ImageContent.class);
    public static final Definition<InterfaceProtocol.TableContent> TABLE_CONTENT = definition("interfaces", "TableContent", InterfaceProtocol.TableContent.class);
    public static final Definition<InterfaceProtocol.Cell> CELL = definition("interfaces", "Cell", InterfaceProtocol.Cell.class);
    public static final Definition<InterfaceProtocol.FormulaContent> FORMULA_CONTENT = definition("interfaces", "FormulaContent", InterfaceProtocol.FormulaContent.class);
    public static final Definition<InterfaceProtocol.FieldValue> FIELD_VALUE = definition("interfaces", "FieldValue", InterfaceProtocol.FieldValue.class);
    public static final Definition<InterfaceProtocol.EvidenceLink> EVIDENCE_LINK = definition("interfaces", "EvidenceLink", InterfaceProtocol.EvidenceLink.class);
    public static final Definition<InterfaceProtocol.FactUsage> FACT_USAGE = definition("interfaces", "FactUsage", InterfaceProtocol.FactUsage.class);
    public static final Definition<InterfaceProtocol.DocumentBody> DOCUMENT_BODY_V2 = definition("document-body-v2", "DocumentBody", InterfaceProtocol.DocumentBody.class);
    public static final Definition<InterfaceProtocol.UploadRequest> UPLOAD_REQUEST_V2 = definition("document-body-v2", "UploadRequest", InterfaceProtocol.UploadRequest.class);
    public static final Definition<InterfaceProtocol.DocumentBody> DOCUMENT_BODY = definition("interfaces", "DocumentBody", InterfaceProtocol.DocumentBody.class);
    public static final Definition<InterfaceProtocol.DocumentFieldProjection> DOCUMENT_FIELD_PROJECTION = definition("interfaces", "DocumentFieldProjection", InterfaceProtocol.DocumentFieldProjection.class);
    public static final Definition<InterfaceProtocol.DocumentEvidenceProjection> DOCUMENT_EVIDENCE_PROJECTION = definition("interfaces", "DocumentEvidenceProjection", InterfaceProtocol.DocumentEvidenceProjection.class);
    public static final Definition<InterfaceProtocol.BodyPage> BODY_PAGE = definition("interfaces", "BodyPage", InterfaceProtocol.BodyPage.class);
    public static final Definition<InterfaceProtocol.DocumentSnapshot> DOCUMENT_SNAPSHOT = definition("interfaces", "DocumentSnapshot", InterfaceProtocol.DocumentSnapshot.class);
    public static final Definition<InterfaceProtocol.RevisionInput> REVISION_INPUT = definition("interfaces", "RevisionInput", InterfaceProtocol.RevisionInput.class);
    public static final Definition<InterfaceProtocol.UploadRequest> UPLOAD_REQUEST = definition("interfaces", "UploadRequest", InterfaceProtocol.UploadRequest.class);
    public static final Definition<InterfaceProtocol.UploadGrant> UPLOAD_GRANT = definition("interfaces", "UploadGrant", InterfaceProtocol.UploadGrant.class);
    public static final Definition<InterfaceProtocol.UploadCompletion> UPLOAD_COMPLETION = definition("interfaces", "UploadCompletion", InterfaceProtocol.UploadCompletion.class);
    public static final Definition<InterfaceProtocol.ArtifactReceipt> ARTIFACT_RECEIPT = definition("interfaces", "ArtifactReceipt", InterfaceProtocol.ArtifactReceipt.class);
    public static final Definition<InterfaceProtocol.CompareRequest> COMPARE_REQUEST = definition("interfaces", "CompareRequest", InterfaceProtocol.CompareRequest.class);
    public static final Definition<InterfaceProtocol.CandidateCompareRequest> CANDIDATE_COMPARE_REQUEST = definition("interfaces", "CandidateCompareRequest", InterfaceProtocol.CandidateCompareRequest.class);
    public static final Definition<InterfaceProtocol.Comparison> COMPARISON = definition("interfaces", "Comparison", InterfaceProtocol.Comparison.class);
    public static final Definition<InterfaceProtocol.CandidateView> CANDIDATE_VIEW = definition("interfaces", "CandidateView", InterfaceProtocol.CandidateView.class);
    public static final Definition<InterfaceProtocol.ReviewBaseline> REVIEW_BASELINE = definition("interfaces", "ReviewBaseline", InterfaceProtocol.ReviewBaseline.class);
    public static final Definition<InterfaceProtocol.EvidenceDecisionBatch> EVIDENCE_DECISION_BATCH = definition("interfaces", "EvidenceDecisionBatch", InterfaceProtocol.EvidenceDecisionBatch.class);
    public static final Definition<InterfaceProtocol.ReviewTarget> REVIEW_TARGET = definition("interfaces", "ReviewTarget", InterfaceProtocol.ReviewTarget.class);
    public static final Definition<InterfaceProtocol.ReviewBatch> REVIEW_BATCH = definition("interfaces", "ReviewBatch", InterfaceProtocol.ReviewBatch.class);
    public static final Definition<InterfaceProtocol.DecisionBatch> DECISION_BATCH = definition("interfaces", "DecisionBatch", InterfaceProtocol.DecisionBatch.class);
    public static final Definition<InterfaceProtocol.AnnotationInput> ANNOTATION_INPUT = definition("interfaces", "AnnotationInput", InterfaceProtocol.AnnotationInput.class);
    public static final Definition<InterfaceProtocol.ReauthRequest> REAUTH_REQUEST = definition("interfaces", "ReauthRequest", InterfaceProtocol.ReauthRequest.class);
    public static final Definition<InterfaceProtocol.ReauthChallenge> REAUTH_CHALLENGE = definition("interfaces", "ReauthChallenge", InterfaceProtocol.ReauthChallenge.class);
    public static final Definition<InterfaceProtocol.ReauthCompletion> REAUTH_COMPLETION = definition("interfaces", "ReauthCompletion", InterfaceProtocol.ReauthCompletion.class);
    public static final Definition<InterfaceProtocol.ReauthReceipt> REAUTH_RECEIPT = definition("interfaces", "ReauthReceipt", InterfaceProtocol.ReauthReceipt.class);
    public static final Definition<InterfaceProtocol.CancelMessage> CANCEL_MESSAGE = definition("interfaces", "CancelMessage", InterfaceProtocol.CancelMessage.class);
    public static final Definition<InterfaceProtocol.ConversationSnapshot> CONVERSATION_SNAPSHOT = definition("interfaces", "ConversationSnapshot", InterfaceProtocol.ConversationSnapshot.class);
    public static final Definition<InterfaceProtocol.CredentialWrite> CREDENTIAL_WRITE = definition("interfaces", "CredentialWrite", InterfaceProtocol.CredentialWrite.class);
    public static final Definition<InterfaceProtocol.CredentialReceipt> CREDENTIAL_RECEIPT = definition("interfaces", "CredentialReceipt", InterfaceProtocol.CredentialReceipt.class);
    public static final Definition<InterfaceProtocol.VerificationView> VERIFICATION_VIEW = definition("interfaces", "VerificationView", InterfaceProtocol.VerificationView.class);
    public static final Definition<InterfaceProtocol.ManagementBindings> MANAGEMENT_BINDINGS = definition("interfaces", "ManagementBindings", InterfaceProtocol.ManagementBindings.class);
    public static final Definition<InterfaceProtocol.ManagementModule> MANAGEMENT_MODULE = definition("interfaces", "ManagementModule", InterfaceProtocol.ManagementModule.class);
    public static final Definition<InterfaceProtocol.ManagementOperation> MANAGEMENT_OPERATION = definition("interfaces", "ManagementOperation", InterfaceProtocol.ManagementOperation.class);
    public static final Definition<InterfaceProtocol.ReportFilters> REPORT_FILTERS = definition("interfaces", "ReportFilters", InterfaceProtocol.ReportFilters.class);
    public static final Definition<InterfaceProtocol.ReportExportRequest> REPORT_EXPORT_REQUEST = definition("interfaces", "ReportExportRequest", InterfaceProtocol.ReportExportRequest.class);
    public static final Definition<InterfaceProtocol.ReportExportView> REPORT_EXPORT_VIEW = definition("interfaces", "ReportExportView", InterfaceProtocol.ReportExportView.class);
    public static final Definition<InterfaceProtocol.OperationLookup> OPERATION_LOOKUP = definition("interfaces", "OperationLookup", InterfaceProtocol.OperationLookup.class);
    public static final Definition<InterfaceProtocol.OperationLookupResult> OPERATION_LOOKUP_RESULT = definition("interfaces", "OperationLookupResult", InterfaceProtocol.OperationLookupResult.class);
    public static final Definition<InterfaceProtocol.ReconciliationRequest> RECONCILIATION_REQUEST = definition("interfaces", "ReconciliationRequest", InterfaceProtocol.ReconciliationRequest.class);
    public static final Definition<InterfaceProtocol.Stream> STREAM = definition("interfaces", "Stream", InterfaceProtocol.Stream.class);
    public static final Definition<InterfaceProtocol.StreamCursor> STREAM_CURSOR = definition("interfaces", "StreamCursor", InterfaceProtocol.StreamCursor.class);
    public static final Definition<InterfaceProtocol.StatePayload> STATE_PAYLOAD = definition("interfaces", "StatePayload", InterfaceProtocol.StatePayload.class);
    public static final Definition<InterfaceProtocol.WaitingPayload> WAITING_PAYLOAD = definition("interfaces", "WaitingPayload", InterfaceProtocol.WaitingPayload.class);
    public static final Definition<InterfaceProtocol.DocumentPayload> DOCUMENT_PAYLOAD = definition("interfaces", "DocumentPayload", InterfaceProtocol.DocumentPayload.class);
    public static final Definition<InterfaceProtocol.InvalidationPayload> INVALIDATION_PAYLOAD = definition("interfaces", "InvalidationPayload", InterfaceProtocol.InvalidationPayload.class);
    public static final Definition<InterfaceProtocol.GovernancePayload> GOVERNANCE_PAYLOAD = definition("interfaces", "GovernancePayload", InterfaceProtocol.GovernancePayload.class);
    public static final Definition<InterfaceProtocol.PublicEvent> PUBLIC_EVENT = definition("interfaces", "PublicEvent", InterfaceProtocol.PublicEvent.class);
    public static final Definition<InterfaceProtocol.CandidateDelta> CANDIDATE_DELTA = definition("interfaces", "CandidateDelta", InterfaceProtocol.CandidateDelta.class);
    public static final Definition<InterfaceProtocol.StepIdentity> STEP_IDENTITY = definition("interfaces", "StepIdentity", InterfaceProtocol.StepIdentity.class);
    public static final Definition<InterfaceProtocol.ActionIdentity> ACTION_IDENTITY = definition("interfaces", "ActionIdentity", InterfaceProtocol.ActionIdentity.class);
    public static final Definition<InterfaceProtocol.InputReadRequest> INPUT_READ_REQUEST = definition("interfaces", "InputReadRequest", InterfaceProtocol.InputReadRequest.class);
    public static final Definition<InterfaceProtocol.ToolRead> TOOL_READ = definition("interfaces", "ToolRead", InterfaceProtocol.ToolRead.class);
    public static final Definition<InterfaceProtocol.PlanCandidateRequest> PLAN_CANDIDATE_REQUEST = definition("interfaces", "PlanCandidateRequest", InterfaceProtocol.PlanCandidateRequest.class);
    public static final Definition<InterfaceProtocol.CandidateReceipt> CANDIDATE_RECEIPT = definition("interfaces", "CandidateReceipt", InterfaceProtocol.CandidateReceipt.class);
    public static final Definition<InterfaceProtocol.WaitingRequest> WAITING_REQUEST = definition("interfaces", "WaitingRequest", InterfaceProtocol.WaitingRequest.class);
    public static final Definition<InterfaceProtocol.WaitingReceipt> WAITING_RECEIPT = definition("interfaces", "WaitingReceipt", InterfaceProtocol.WaitingReceipt.class);
    public static final Definition<InterfaceProtocol.CheckResultRequest> CHECK_RESULT_REQUEST = definition("interfaces", "CheckResultRequest", InterfaceProtocol.CheckResultRequest.class);
    public static final Definition<InterfaceProtocol.CheckReceipt> CHECK_RECEIPT = definition("interfaces", "CheckReceipt", InterfaceProtocol.CheckReceipt.class);
    public static final Definition<InterfaceProtocol.WebAcquisitionRequest> WEB_ACQUISITION_REQUEST = definition("interfaces", "WebAcquisitionRequest", InterfaceProtocol.WebAcquisitionRequest.class);
    public static final Definition<InterfaceProtocol.ContextRequest> CONTEXT_REQUEST = definition("interfaces", "ContextRequest", InterfaceProtocol.ContextRequest.class);
    public static final Definition<InterfaceProtocol.ContextReceipt> CONTEXT_RECEIPT = definition("interfaces", "ContextReceipt", InterfaceProtocol.ContextReceipt.class);
    public static final Definition<InterfaceProtocol.ObjectRef> OBJECT_REF = definition("interfaces", "ObjectRef", InterfaceProtocol.ObjectRef.class);
    public static final Definition<InterfaceProtocol.AgentArtifactRequest> AGENT_ARTIFACT_REQUEST = definition("interfaces", "AgentArtifactRequest", InterfaceProtocol.AgentArtifactRequest.class);
    public static final Definition<InterfaceProtocol.InvocationReadRequest> INVOCATION_READ_REQUEST = definition("interfaces", "InvocationReadRequest", InterfaceProtocol.InvocationReadRequest.class);
    public static final Definition<InterfaceProtocol.ContentChunk> CONTENT_CHUNK = definition("interfaces", "ContentChunk", InterfaceProtocol.ContentChunk.class);
    public static final Definition<InterfaceProtocol.InternalUploadRequest> INTERNAL_UPLOAD_REQUEST = definition("interfaces", "InternalUploadRequest", InterfaceProtocol.InternalUploadRequest.class);
    public static final Definition<InterfaceProtocol.InternalUploadCompletion> INTERNAL_UPLOAD_COMPLETION = definition("interfaces", "InternalUploadCompletion", InterfaceProtocol.InternalUploadCompletion.class);
    public static final Definition<InterfaceProtocol.InternalSearchRequest> INTERNAL_SEARCH_REQUEST = definition("interfaces", "InternalSearchRequest", InterfaceProtocol.InternalSearchRequest.class);
    public static final Definition<InterfaceProtocol.InternalSearchResult> INTERNAL_SEARCH_RESULT = definition("interfaces", "InternalSearchResult", InterfaceProtocol.InternalSearchResult.class);
    public static final Definition<InterfaceProtocol.InternalSourceReadRequest> INTERNAL_SOURCE_READ_REQUEST = definition("interfaces", "InternalSourceReadRequest", InterfaceProtocol.InternalSourceReadRequest.class);
    public static final Definition<InterfaceProtocol.InternalSourceReadResult> INTERNAL_SOURCE_READ_RESULT = definition("interfaces", "InternalSourceReadResult", InterfaceProtocol.InternalSourceReadResult.class);
    public static List<Definition<?>> definitions() {
        List<Definition<?>> result = new ArrayList<>(CommonBindings.definitions());
        result.addAll(List.of(ENVELOPE, ERROR, ERROR_DETAIL, OPERATION, PAGE_INFO, RECORD, RECORD_PAGE, NODE,
            CONTAINER_CONTENT, TEXT_CONTENT, IMAGE_CONTENT, TABLE_CONTENT, CELL, FORMULA_CONTENT, FIELD_VALUE, EVIDENCE_LINK,
            FACT_USAGE, DOCUMENT_BODY, DOCUMENT_FIELD_PROJECTION, DOCUMENT_EVIDENCE_PROJECTION, BODY_PAGE, DOCUMENT_SNAPSHOT, REVISION_INPUT, UPLOAD_REQUEST, UPLOAD_GRANT, UPLOAD_COMPLETION,
            ARTIFACT_RECEIPT, COMPARE_REQUEST, CANDIDATE_COMPARE_REQUEST, COMPARISON, CANDIDATE_VIEW, REVIEW_BASELINE,
            EVIDENCE_DECISION_BATCH, REVIEW_TARGET, REVIEW_BATCH, DECISION_BATCH, ANNOTATION_INPUT, REAUTH_REQUEST, REAUTH_CHALLENGE,
            REAUTH_COMPLETION, REAUTH_RECEIPT, CANCEL_MESSAGE, CONVERSATION_SNAPSHOT, CREDENTIAL_WRITE, CREDENTIAL_RECEIPT,
            VERIFICATION_VIEW, MANAGEMENT_BINDINGS, MANAGEMENT_MODULE, MANAGEMENT_OPERATION, REPORT_FILTERS, REPORT_EXPORT_REQUEST,
            REPORT_EXPORT_VIEW, OPERATION_LOOKUP, OPERATION_LOOKUP_RESULT, RECONCILIATION_REQUEST, STREAM, STREAM_CURSOR,
            STATE_PAYLOAD, WAITING_PAYLOAD, DOCUMENT_PAYLOAD, INVALIDATION_PAYLOAD, GOVERNANCE_PAYLOAD, PUBLIC_EVENT, CANDIDATE_DELTA,
            STEP_IDENTITY, ACTION_IDENTITY, INPUT_READ_REQUEST, TOOL_READ, PLAN_CANDIDATE_REQUEST, CANDIDATE_RECEIPT, WAITING_REQUEST,
            WAITING_RECEIPT, CHECK_RESULT_REQUEST, CHECK_RECEIPT, WEB_ACQUISITION_REQUEST, CONTEXT_REQUEST, CONTEXT_RECEIPT, OBJECT_REF,
            AGENT_ARTIFACT_REQUEST, INVOCATION_READ_REQUEST, CONTENT_CHUNK, INTERNAL_UPLOAD_REQUEST, INTERNAL_UPLOAD_COMPLETION,
            INTERNAL_SEARCH_REQUEST, INTERNAL_SEARCH_RESULT, INTERNAL_SOURCE_READ_REQUEST, INTERNAL_SOURCE_READ_RESULT));
        return List.copyOf(result);
    }
}
