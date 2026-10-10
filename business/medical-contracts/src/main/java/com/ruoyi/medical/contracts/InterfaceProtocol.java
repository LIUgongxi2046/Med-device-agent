package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.ruoyi.medical.contracts.CommonProtocol.*;

public final class InterfaceProtocol {
    private InterfaceProtocol() { }
    public enum OperationStatus { PROCESSING, COMPLETED, FAILED }
    public enum ContainerKind { DOCUMENT, SECTION, PARAGRAPH }
    public enum Mark { BOLD, ITALIC, UNDERLINE, SUPERSCRIPT, SUBSCRIPT }
    public enum Relation { SUPPORT, CONFLICT, BACKGROUND }
    public enum CandidateStatus { READY, ADOPTED, REJECTED, STALE }
    public enum ReviewDecision { CONFIRM, RETURN }
    public enum ConversationStatus { ACTIVE, ARCHIVED, DELETED }
    public enum VerificationStatus { PENDING, RUNNING, PASSED, FAILED, INVALIDATED }
    public enum ModuleId { USER, ROLE, MENU, DEPARTMENT, POST, DICTIONARY, PARAMETER, NOTICE, ONLINE_USER, SCHEDULED_JOB, OPERATION_LOG, LOGIN_LOG, SERVICE_MONITOR, CACHE_MONITOR, CONNECTION_POOL, CODE_GENERATOR, API_DOCUMENTATION, FORM_BUILDER }
    public enum BindingStatus { BOUND, UNBOUND, DISABLED }
    public enum Method { GET, POST, PUT, DELETE }
    public enum SideEffect { NONE, WRITE, EXPORT, SESSION_REVOKE, JOB_DISPATCH }
    public enum ReportKind { USAGE, AUDIT, EVALUATION }
    public enum ReportFormat { CSV, XLSX }
    public enum ReportStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }
    public enum LookupStatus { FOUND, NOT_FOUND, EXPIRED }
    public enum StreamKind { RUN, CONVERSATION }
    public enum ResourceKind { RUN, STEP, WAITING_ITEM, CANDIDATE, DOCUMENT, SOURCE_VERSION, EXPORT, NOTIFICATION }
    public enum EventType {
        @JsonProperty("run.updated") RUN_UPDATED,
        @JsonProperty("step.updated") STEP_UPDATED,
        @JsonProperty("waiting_input.created") WAITING_INPUT_CREATED,
        @JsonProperty("candidate.updated") CANDIDATE_UPDATED,
        @JsonProperty("document.revised") DOCUMENT_REVISED,
        @JsonProperty("review.invalidated") REVIEW_INVALIDATED,
        @JsonProperty("source.governance_changed") SOURCE_GOVERNANCE_CHANGED,
        @JsonProperty("export.updated") EXPORT_UPDATED,
        @JsonProperty("notification.created") NOTIFICATION_CREATED
    }
    public enum InputKind { MANIFEST, TEMPLATE, ARTIFACT, RETRIEVAL, ISSUES }
    public enum ValidationStatus { VALID, WAITING_INPUT }
    public enum TargetKind { DOCUMENT, CANDIDATE }
    public enum CheckStatus { SUCCEEDED, WAITING_INPUT, FAILED }
    public enum ArtifactKind { PlanCandidate, EvidenceBundle, FieldCheckResult, SectionCandidate, ClaimRecord, ReviewFindings, AssemblyManifest, HandoffRecord, ContextInput }
    public enum Encoding { UTF8, BASE64 }
    public enum SearchPurpose { RESEARCH, VERIFICATION, EVALUATION }
    public enum ReadPurpose { RESEARCH, VERIFICATION, PARSING, EVALUATION }
    public enum SearchStatus { FOUND, EMPTY }

    public record Envelope(String schemaVersion, String requestId, String resourceRevision, JsonNode data) {
        public Envelope { constant(schemaVersion, "1.0.0"); optionalRevisions(resourceRevision); data = data.deepCopy(); }
        @Override public JsonNode data() { return data.deepCopy(); }
    }
    public record Error(String schemaVersion, String code, String message, String requestId, String resourceRevision,
                        List<String> allowedActions, boolean retryable, String operationId, List<ErrorDetail> details) {
        public Error { constant(schemaVersion, "1.0.0"); optionalRevisions(resourceRevision); nullableIds(operationId); allowedActions = List.copyOf(allowedActions); details = List.copyOf(details); }
    }
    public record ErrorDetail(String field, String code) { }
    public record Operation(String operationId, String resourceId, OperationStatus status, String statusUrl) {
        public Operation { ids(operationId); nullableIds(resourceId); }
    }
    public record PageInfo(long page, long pageSize, String total, String watermark, String filterSha256) {
        public PageInfo { revisions(total, watermark); }
    }
    public record Record(String id, String revision, String name, String status, String updatedAt, List<String> allowedActions, ArtifactRef detail) {
        public Record { ids(id); revisions(revision); times(updatedAt); allowedActions = List.copyOf(allowedActions); }
    }
    public record RecordPage(List<Record> items, PageInfo pageInfo) { public RecordPage { items = List.copyOf(items); } }
    public record Node(String nodeId, String parentId, long ordinal, List<String> fieldIds, List<String> evidenceLinkIds, NodeContent content) {
        public Node { ids(nodeId); nullableIds(parentId); fieldIds = copyIds(fieldIds); evidenceLinkIds = copyIds(evidenceLinkIds); }
    }
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = ContainerContent.class, name = "DOCUMENT"), @JsonSubTypes.Type(value = ContainerContent.class, name = "SECTION"),
        @JsonSubTypes.Type(value = ContainerContent.class, name = "PARAGRAPH"), @JsonSubTypes.Type(value = TextContent.class, name = "TEXT"),
        @JsonSubTypes.Type(value = ImageContent.class, name = "IMAGE"), @JsonSubTypes.Type(value = TableContent.class, name = "TABLE"), @JsonSubTypes.Type(value = FormulaContent.class, name = "FORMULA")})
    public sealed interface NodeContent permits ContainerContent, TextContent, ImageContent, TableContent, FormulaContent { }
    public record ContainerContent(ContainerKind kind, String title) implements NodeContent { }
    public record TextContent(String kind, String text, List<Mark> marks) implements NodeContent {
        public TextContent { constant(kind, "TEXT"); marks = List.copyOf(marks); }
    }
    public record ImageContent(String kind, String artifactId, String sha256, String altText, String caption) implements NodeContent {
        public ImageContent { constant(kind, "IMAGE"); ids(artifactId); }
    }
    public record TableContent(String kind, List<String> rowIds, List<String> columnIds, List<Cell> cells, String caption) implements NodeContent {
        public TableContent { constant(kind, "TABLE"); rowIds = copyIds(rowIds); columnIds = copyIds(columnIds); cells = List.copyOf(cells); }
    }
    public record Cell(String cellId, String rowId, String columnId, long rowSpan, long columnSpan, List<String> nodeIds) {
        public Cell { ids(cellId, rowId, columnId); nodeIds = copyIds(nodeIds); }
    }
    public record FormulaContent(String kind, String latex, String description, String renderedArtifactId) implements NodeContent {
        public FormulaContent { constant(kind, "FORMULA"); nullableIds(renderedArtifactId); }
    }
    public record FieldValue(String fieldId, String nodeId, String fieldDefinitionId, String originalValue, String decimalValue,
                             String unit, String model, String condition, List<String> evidenceLinkIds) {
        public FieldValue { ids(fieldId, nodeId, fieldDefinitionId); if (decimalValue != null) WireValues.decimal(decimalValue, 38, 12); evidenceLinkIds = copyIds(evidenceLinkIds); }
    }
    public record EvidenceLink(String evidenceLinkId, List<String> nodeIds, Relation relation, DatasetProtocol.EvidenceRef evidence) {
        public EvidenceLink { ids(evidenceLinkId); nodeIds = copyIds(nodeIds); }
    }
    public record FactUsage(String factVersionId, List<String> nodeIds, List<String> fieldIds) {
        public FactUsage { ids(factVersionId); nodeIds = copyIds(nodeIds); fieldIds = copyIds(fieldIds); }
    }
    public record DocumentBody(String schemaVersion, String templateVersion, List<Node> nodes, List<FieldValue> fields,
                               List<EvidenceLink> evidenceLinks, List<FactUsage> factUsages) {
        public DocumentBody { constant(schemaVersion, "1.0.0"); ids(templateVersion); nodes = List.copyOf(nodes); fields = List.copyOf(fields); evidenceLinks = List.copyOf(evidenceLinks); factUsages = List.copyOf(factUsages); }
    }
    public record DocumentFieldProjection(String schemaVersion, String documentRevision, String bodySha256, List<FieldValue> fields) {
        public DocumentFieldProjection { constant(schemaVersion, "1.0.0"); ids(documentRevision); WireValues.hash(bodySha256); fields = List.copyOf(fields); }
    }
    public record DocumentEvidenceProjection(String schemaVersion, String documentRevision, String bodySha256,
            List<EvidenceLink> evidenceLinks, List<FactUsage> factUsages) {
        public DocumentEvidenceProjection {
            constant(schemaVersion, "1.0.0"); ids(documentRevision); WireValues.hash(bodySha256);
            evidenceLinks = List.copyOf(evidenceLinks); factUsages = List.copyOf(factUsages);
        }
    }
    public record BodyPage(String documentRevision, String bodySha256, List<Node> nodes, List<FieldValue> fields,
                           List<EvidenceLink> evidenceLinks, List<FactUsage> factUsages, Coverage coverage) {
        public BodyPage { ids(documentRevision); nodes = List.copyOf(nodes); fields = List.copyOf(fields); evidenceLinks = List.copyOf(evidenceLinks); factUsages = List.copyOf(factUsages); }
    }
    public record DocumentSnapshot(String documentId, String projectId, String documentRevision, String revision, String planRevision, String templateVersion,
                                   ArtifactRef body, ArtifactRef fields, ArtifactRef evidenceLinks, String bodySchemaRevision, List<String> checkIds,
                                   List<String> reviewIds, List<String> signatureIds, List<String> invalidationReasons, String issueWatermark,
                                   String evidenceWatermark, String authEpoch, String governanceEpoch, List<String> allowedActions, List<StreamCursor> streamCursors) {
        public DocumentSnapshot { ids(documentId, projectId, documentRevision, planRevision, templateVersion, bodySchemaRevision); revisions(revision, issueWatermark, evidenceWatermark, authEpoch, governanceEpoch); checkIds = copyIds(checkIds); reviewIds = copyIds(reviewIds); signatureIds = copyIds(signatureIds); invalidationReasons = List.copyOf(invalidationReasons); allowedActions = List.copyOf(allowedActions); streamCursors = List.copyOf(streamCursors); }
    }
    public record RevisionInput(String baseRevision, String expectedRevision, String bodyArtifactId, String bodySha256, String bodySchemaRevision, String reason) {
        public RevisionInput { ids(baseRevision, bodyArtifactId, bodySchemaRevision); revisions(expectedRevision); }
    }
    public record UploadRequest(String purpose, String documentId, String expectedRevision, String mediaType, String sizeBytes, String sha256, String schemaRef, String dependencyAccessSetId) {
        public UploadRequest { constant(purpose, "DOCUMENT_BODY"); constant(mediaType, "application/json"); constant(schemaRef, "interfaces.schema.yaml#/$defs/DocumentBody"); ids(documentId, dependencyAccessSetId); revisions(expectedRevision, sizeBytes); }
    }
    public record UploadGrant(String grantId, String uploadId, String uploadUrl, String expiresAt, long maxPartBytes) {
        public UploadGrant { ids(grantId, uploadId); times(expiresAt); if (maxPartBytes != 8388608) invalid(); }
    }
    public record UploadCompletion(String sha256, String sizeBytes, String expectedRevision) { public UploadCompletion { revisions(sizeBytes, expectedRevision); } }
    public record ArtifactReceipt(String artifactId, String sha256, String schemaRef, String revision) { public ArtifactReceipt { ids(artifactId); revisions(revision); } }
    public record CompareRequest(String documentId, String leftRevision, String rightRevision) { public CompareRequest { ids(documentId, leftRevision, rightRevision); } }
    public record CandidateCompareRequest(String candidateId, String targetDocumentRevision) { public CandidateCompareRequest { ids(candidateId, targetDocumentRevision); } }
    public record Comparison(String comparisonId, String comparisonRevision, String leftId, String rightRevision, ArtifactRef differences, List<String> affectedNodeIds) {
        public Comparison { ids(comparisonId, leftId, rightRevision); revisions(comparisonRevision); affectedNodeIds = copyIds(affectedNodeIds); }
    }
    public record CandidateView(String candidateId, String revision, CandidateStatus status, String baseDocumentRevision, List<String> targetNodeIds,
                                ArtifactRef content, ArtifactRef originalEvidence, ArtifactRef newEvidence, List<String> checkIds, String inputSha256,
                                String snapshotId, String planRevision, List<String> allowedActions, String adoptionId, String adoptedDocumentRevision) {
        public CandidateView { ids(candidateId, baseDocumentRevision, snapshotId, planRevision); nullableIds(adoptionId, adoptedDocumentRevision); revisions(revision); targetNodeIds = copyIds(targetNodeIds); checkIds = copyIds(checkIds); allowedActions = List.copyOf(allowedActions); }
    }
    // 三个继承基准的原始 Schema 允许额外属性，保留内容后仍执行完整往返校验。
    public abstract static class OpenObject {
        private final Map<String, JsonNode> additional = new LinkedHashMap<>();
        @JsonAnySetter private void additional(String name, JsonNode value) { additional.put(name, value.deepCopy()); }
        @JsonAnyGetter public final Map<String, JsonNode> additionalProperties() {
            Map<String, JsonNode> result = new LinkedHashMap<>();
            additional.forEach((name, value) -> result.put(name, value.deepCopy()));
            return result;
        }
    }
    public static final class ReviewBaseline extends OpenObject {
        private final String documentRevision, expectedRevision, evidenceWatermark, issueWatermark;
        private final List<String> checkIds;
        @JsonCreator public ReviewBaseline(@JsonProperty("document_revision") String documentRevision,
            @JsonProperty("expected_revision") String expectedRevision, @JsonProperty("check_ids") List<String> checkIds,
            @JsonProperty("evidence_watermark") String evidenceWatermark, @JsonProperty("issue_watermark") String issueWatermark) {
            ids(documentRevision); revisions(expectedRevision, evidenceWatermark, issueWatermark);
            this.documentRevision = documentRevision; this.expectedRevision = expectedRevision; this.checkIds = copyIds(checkIds); this.evidenceWatermark = evidenceWatermark; this.issueWatermark = issueWatermark;
        }
        @JsonProperty("document_revision") public String documentRevision() { return documentRevision; }
        @JsonProperty("expected_revision") public String expectedRevision() { return expectedRevision; }
        @JsonProperty("check_ids") public List<String> checkIds() { return checkIds; }
        @JsonProperty("evidence_watermark") public String evidenceWatermark() { return evidenceWatermark; }
        @JsonProperty("issue_watermark") public String issueWatermark() { return issueWatermark; }
    }
    public record EvidenceDecisionBatch(String documentRevision, String expectedRevision, List<String> checkIds, String evidenceWatermark,
                                        String issueWatermark, List<EvidenceDecision> decisions) {
        public EvidenceDecisionBatch { ids(documentRevision); revisions(expectedRevision, evidenceWatermark, issueWatermark); checkIds = copyIds(checkIds); decisions = List.copyOf(decisions); }
    }
    public record ReviewTarget(String nodeId, String sectionRevision, Selection selection, ReviewDecision decision, String reason, String assigneeId) {
        public ReviewTarget { nullableIds(nodeId, sectionRevision, assigneeId); }
    }
    public record ReviewBatch(String documentRevision, String expectedRevision, List<String> checkIds, String evidenceWatermark, String issueWatermark, List<ReviewTarget> targets) {
        public ReviewBatch { ids(documentRevision); revisions(expectedRevision, evidenceWatermark, issueWatermark); checkIds = copyIds(checkIds); targets = List.copyOf(targets); }
    }
    public record DecisionBatch(String batchId, String documentRevision, String revision, List<String> decisionIds) {
        public DecisionBatch { ids(batchId, documentRevision); revisions(revision); decisionIds = copyIds(decisionIds); }
    }
    public record AnnotationInput(String documentRevision, Selection selection, String content, String expectedRevision) {
        public AnnotationInput { ids(documentRevision); revisions(expectedRevision); }
    }
    public record ReauthRequest(String documentId, String documentRevision, String purpose, String signingPolicyRevision) {
        public ReauthRequest { ids(documentId, documentRevision, signingPolicyRevision); }
    }
    public record ReauthChallenge(String challengeId, String authorizationUrl, String expiresAt) { public ReauthChallenge { ids(challengeId); times(expiresAt); } }
    public record ReauthCompletion(String callbackReceiptId) { public ReauthCompletion { ids(callbackReceiptId); } }
    public record ReauthReceipt(String reauthenticationRef, String subjectId, String documentId, String documentRevision, String purpose, String expiresAt) {
        public ReauthReceipt { ids(reauthenticationRef, subjectId, documentId, documentRevision); times(expiresAt); }
    }
    public record CancelMessage(String expectedRevision, String reason) { public CancelMessage { revisions(expectedRevision); } }
    public record ConversationSnapshot(String conversationId, String projectId, String revision, ConversationStatus status, ArtifactRef messages,
                                       List<String> pendingActionIds, StreamCursor streamCursor) {
        public ConversationSnapshot { ids(conversationId, projectId); revisions(revision); pendingActionIds = copyIds(pendingActionIds); }
    }
    public record CredentialWrite(String configId, String expectedRevision, String providerKind, String secretValue) {
        public CredentialWrite { ids(configId); revisions(expectedRevision); }
        @Override public String toString() { return "CredentialWrite[secretValue=REDACTED]"; }
    }
    public record CredentialReceipt(String credentialReferenceId, String secretVersion, String configRevision, String verificationId) {
        public CredentialReceipt { ids(credentialReferenceId, verificationId); revisions(configRevision); }
    }
    public record VerificationView(String verificationId, String configId, String configRevision, String secretVersion,
                                   VerificationStatus status, String verifiedAt, String endpointIdentity, String errorCode) {
        public VerificationView { ids(verificationId, configId); revisions(configRevision); if (verifiedAt != null) times(verifiedAt); }
    }
    public record ManagementBindings(String releaseId, String environmentId, String manifestSha256, List<ManagementModule> modules) {
        public ManagementBindings { ids(releaseId, environmentId); modules = List.copyOf(modules); }
    }
    public record ManagementModule(ModuleId moduleId, BindingStatus bindingStatus, String routeName, String componentRef, String sourceCommit,
                                   List<ManagementOperation> operations, List<String> permissionCodes, String unavailableReason) {
        public ManagementModule {
            operations = List.copyOf(operations); permissionCodes = List.copyOf(permissionCodes);
            if (bindingStatus == null) invalid();
            if (bindingStatus == BindingStatus.BOUND) {
                if (empty(routeName) || empty(componentRef) || empty(sourceCommit) || operations.isEmpty() || permissionCodes.isEmpty() || unavailableReason != null) invalid();
            } else if (!operations.isEmpty() || empty(unavailableReason)) invalid();
        }
    }
    public record ManagementOperation(String operationId, Method method, String path, String requestSchemaRef,
                                      String responseSchemaRef, SideEffect sideEffect, String permissionCode) { }
    public record ReportFilters(String projectId, String from, String to, String actorId, String status) {
        public ReportFilters { nullableIds(projectId, actorId); times(from, to); }
    }
    public record ReportExportRequest(ReportKind reportKind, ReportFilters filters, String datasetWatermark, String metricRevision, ReportFormat format) {
        public ReportExportRequest { revisions(datasetWatermark); ids(metricRevision); }
    }
    public record ReportExportView(String reportId, ReportStatus status, String filterSha256, String datasetWatermark,
                                   String metricRevision, String recordCount, List<String> exportItemIds, String errorCode) {
        public ReportExportView { ids(reportId, metricRevision); revisions(datasetWatermark); optionalRevisions(recordCount); exportItemIds = copyIds(exportItemIds); }
    }
    public record OperationLookup(String operationId, String idempotencyKey) { }
    public record OperationLookupResult(LookupStatus lookupStatus, Operation operation) { }
    public record ReconciliationRequest(String expectedRevision, String reason, String receiptArtifactId, String receiptSha256) {
        public ReconciliationRequest { revisions(expectedRevision); nullableIds(receiptArtifactId); }
    }
    public record Stream(String streamId, StreamKind kind, String resourceId) { public Stream { ids(streamId, resourceId); } }
    public record StreamCursor(Stream stream, String sequence, String cursor, ArtifactRef resourceManifest) { public StreamCursor { revisions(sequence); } }
    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({@JsonSubTypes.Type(StatePayload.class), @JsonSubTypes.Type(WaitingPayload.class), @JsonSubTypes.Type(DocumentPayload.class),
        @JsonSubTypes.Type(InvalidationPayload.class), @JsonSubTypes.Type(GovernancePayload.class)})
    public sealed interface EventPayload permits StatePayload, WaitingPayload, DocumentPayload, InvalidationPayload, GovernancePayload { String readOperationId(); }
    public record StatePayload(String businessEventType, String status, String reasonCode, String readOperationId) implements EventPayload { }
    public record WaitingPayload(String businessEventType, String waitingItemId, String reasonCode, String assigneeId, String readOperationId) implements EventPayload {
        public WaitingPayload { ids(waitingItemId, assigneeId); constant(readOperationId, "TK-02"); }
    }
    public record DocumentPayload(String businessEventType, String documentRevision, List<String> affectedNodeIds, String readOperationId) implements EventPayload {
        public DocumentPayload { ids(documentRevision); affectedNodeIds = copyIds(affectedNodeIds); constant(readOperationId, "DC-01"); }
    }
    public record InvalidationPayload(String businessEventType, String documentRevision, List<String> reviewIds, String reasonCode, String readOperationId) implements EventPayload {
        public InvalidationPayload { ids(documentRevision); reviewIds = copyIds(reviewIds); constant(readOperationId, "DC-01"); }
    }
    public record GovernancePayload(String businessEventType, String governanceEpoch, String authEpoch, String readOperationId) implements EventPayload {
        public GovernancePayload { revisions(governanceEpoch, authEpoch); constant(readOperationId, "KN-06"); }
    }
    public record PublicEvent(String schemaVersion, String eventId, Stream stream, String sequence, EventType eventType, String tenantId,
                              String projectId, String taskId, String runId, ResourceKind resourceKind, String resourceId,
                              String resourceRevision, String occurredAt, EventPayload payload) {
        public PublicEvent {
            constant(schemaVersion, "1.0.0"); ids(eventId, tenantId, resourceId); nullableIds(projectId, taskId, runId); revisions(sequence, resourceRevision); times(occurredAt);
            if (eventType == null || payload == null) invalid();
            EventMapping mapping = eventMapping(eventType);
            if (resourceKind != mapping.resourceKind() || !mapping.payloadType().isInstance(payload)) invalid();
            constant(payload.readOperationId(), mapping.readOperationId());
        }
    }
    public record EventMapping(ResourceKind resourceKind, Class<? extends EventPayload> payloadType, String readOperationId) { }
    public static EventMapping eventMapping(EventType type) {
        return switch (type) {
            case RUN_UPDATED -> new EventMapping(ResourceKind.RUN, StatePayload.class, "RN-02");
            case STEP_UPDATED -> new EventMapping(ResourceKind.STEP, StatePayload.class, "RN-02");
            case WAITING_INPUT_CREATED -> new EventMapping(ResourceKind.WAITING_ITEM, WaitingPayload.class, "TK-02");
            case CANDIDATE_UPDATED -> new EventMapping(ResourceKind.CANDIDATE, StatePayload.class, "BX-07");
            case DOCUMENT_REVISED -> new EventMapping(ResourceKind.DOCUMENT, DocumentPayload.class, "DC-01");
            case REVIEW_INVALIDATED -> new EventMapping(ResourceKind.DOCUMENT, InvalidationPayload.class, "DC-01");
            case SOURCE_GOVERNANCE_CHANGED -> new EventMapping(ResourceKind.SOURCE_VERSION, GovernancePayload.class, "KN-06");
            case EXPORT_UPDATED -> new EventMapping(ResourceKind.EXPORT, StatePayload.class, "EX-02");
            case NOTIFICATION_CREATED -> new EventMapping(ResourceKind.NOTIFICATION, StatePayload.class, "OP-02");
        };
    }
    public record CandidateDelta(String schemaVersion, Stream stream, String candidateId, String deltaSequence, boolean durable, String text) {
        public CandidateDelta { constant(schemaVersion, "1.0.0"); ids(candidateId); revisions(deltaSequence); if (durable) invalid(); }
    }
    public static final class StepIdentity extends OpenObject {
        private final String schemaVersion, runId, stepId;
        private final long leaseGeneration;
        @JsonCreator public StepIdentity(@JsonProperty("schema_version") String schemaVersion, @JsonProperty("run_id") String runId,
            @JsonProperty("step_id") String stepId, @JsonProperty("lease_generation") long leaseGeneration) {
            step(schemaVersion, runId, stepId, leaseGeneration); this.schemaVersion = schemaVersion; this.runId = runId; this.stepId = stepId; this.leaseGeneration = leaseGeneration;
        }
        @JsonProperty("schema_version") public String schemaVersion() { return schemaVersion; }
        @JsonProperty("run_id") public String runId() { return runId; }
        @JsonProperty("step_id") public String stepId() { return stepId; }
        @JsonProperty("lease_generation") public long leaseGeneration() { return leaseGeneration; }
    }
    public static final class ActionIdentity extends OpenObject {
        private final String schemaVersion, runId, stepId, actionId, inputSha256;
        private final long leaseGeneration;
        @JsonCreator public ActionIdentity(@JsonProperty("schema_version") String schemaVersion, @JsonProperty("run_id") String runId,
            @JsonProperty("step_id") String stepId, @JsonProperty("lease_generation") long leaseGeneration,
            @JsonProperty("action_id") String actionId, @JsonProperty("input_sha256") String inputSha256) {
            step(schemaVersion, runId, stepId, leaseGeneration); ids(actionId); this.schemaVersion = schemaVersion; this.runId = runId; this.stepId = stepId;
            this.leaseGeneration = leaseGeneration; this.actionId = actionId; this.inputSha256 = inputSha256;
        }
        @JsonProperty("schema_version") public String schemaVersion() { return schemaVersion; }
        @JsonProperty("run_id") public String runId() { return runId; }
        @JsonProperty("step_id") public String stepId() { return stepId; }
        @JsonProperty("lease_generation") public long leaseGeneration() { return leaseGeneration; }
        @JsonProperty("action_id") public String actionId() { return actionId; }
        @JsonProperty("input_sha256") public String inputSha256() { return inputSha256; }
    }
    public record InputReadRequest(String schemaVersion, String runId, String stepId, long leaseGeneration, String inputManifestId,
                                   InputKind kind, String resourceId, List<String> nodeIds, String cursor) {
        public InputReadRequest { step(schemaVersion, runId, stepId, leaseGeneration); ids(inputManifestId, resourceId); nodeIds = copyIds(nodeIds); }
    }
    public record ToolRead(String resourceId, ArtifactRef content, Coverage coverage) { public ToolRead { ids(resourceId); } }
    public record PlanCandidateRequest(String schemaVersion, String runId, String stepId, long leaseGeneration, String actionId, String inputSha256, String planArtifactId) {
        public PlanCandidateRequest { action(schemaVersion, runId, stepId, leaseGeneration, actionId); ids(planArtifactId); }
    }
    public record CandidateReceipt(String candidateId, String revision, ValidationStatus validationStatus, List<String> missingRequirementIds) {
        public CandidateReceipt { ids(candidateId); revisions(revision); missingRequirementIds = List.copyOf(missingRequirementIds); }
    }
    public record WaitingRequest(String schemaVersion, String runId, String stepId, long leaseGeneration, String actionId, String inputSha256,
                                 String targetRevision, String reasonCode, String question, List<String> requirementRefs, List<String> allowedResolutionActions) {
        public WaitingRequest { action(schemaVersion, runId, stepId, leaseGeneration, actionId); ids(targetRevision); requirementRefs = List.copyOf(requirementRefs); allowedResolutionActions = List.copyOf(allowedResolutionActions); }
    }
    public record WaitingReceipt(String waitingItemId, String assigneeId, String status, String revision) {
        public WaitingReceipt { ids(waitingItemId, assigneeId); constant(status, "WAITING_INPUT"); revisions(revision); }
    }
    public record CheckResultRequest(String schemaVersion, String runId, String stepId, long leaseGeneration, String actionId, String inputSha256,
                                     String checkId, TargetKind targetKind, String targetRevision, String ruleManifestId, String findingsArtifactId, Coverage coverage) {
        public CheckResultRequest { action(schemaVersion, runId, stepId, leaseGeneration, actionId); ids(checkId, targetRevision, ruleManifestId, findingsArtifactId); }
    }
    public record CheckReceipt(String checkId, String revision, List<String> issueIds, CheckStatus status) {
        public CheckReceipt { ids(checkId); revisions(revision); issueIds = copyIds(issueIds); }
    }
    public record WebAcquisitionRequest(String schemaVersion, String runId, String stepId, long leaseGeneration, String actionId, String inputSha256,
                                        String questionId, String approvedQueryRevision, String networkPolicyRevision) {
        public WebAcquisitionRequest { action(schemaVersion, runId, stepId, leaseGeneration, actionId); ids(questionId, approvedQueryRevision, networkPolicyRevision); }
    }
    public record ContextRequest(String schemaVersion, String runId, String stepId, long leaseGeneration, String actionId, String inputSha256,
                                 String exactInputArtifactId, String sourceManifestId, String promptRevision, String tokenizerRevision, long tokenCount, String dependencyAccessSetId) {
        public ContextRequest { action(schemaVersion, runId, stepId, leaseGeneration, actionId); ids(exactInputArtifactId, sourceManifestId, promptRevision, dependencyAccessSetId); }
    }
    public record ContextReceipt(String contextManifestId, String sha256) { public ContextReceipt { ids(contextManifestId); } }
    public record ObjectRef(String objectKey, String objectVersion, String sha256, String sizeBytes, String mediaType) {
        public ObjectRef {
            WireValues.boundedString(objectKey, 1024);
            WireValues.boundedString(objectVersion, 256);
            WireValues.hash(sha256);
            revisions(sizeBytes);
            WireValues.boundedString(mediaType, 128);
        }
    }
    public record AgentArtifactRequest(String schemaVersion, String runId, String stepId, long leaseGeneration, String actionId, String inputSha256,
                                       ArtifactKind kind, String schemaRef, ObjectRef objectRef, String dependencyAccessSetId) {
        public AgentArtifactRequest { action(schemaVersion, runId, stepId, leaseGeneration, actionId); ids(dependencyAccessSetId); }
    }
    public record InvocationReadRequest(String schemaVersion, String runId, String stepId, long leaseGeneration, String invocationId, String actionId) {
        public InvocationReadRequest { step(schemaVersion, runId, stepId, leaseGeneration); ids(invocationId, actionId); }
    }
    public record ContentChunk(String resourceId, String artifactId, String schemaRef, String mediaType, Encoding encoding, String content,
                               String chunkSha256, String fullSha256, Coverage coverage, String readReceiptId) {
        public ContentChunk { ids(resourceId, artifactId, readReceiptId); }
    }
    public record InternalUploadRequest(String schemaVersion, String runId, String stepId, long leaseGeneration, String actionId, String inputSha256,
                                        String purpose, String mediaType, String sizeBytes, String sha256, String schemaRef, String dependencyAccessSetId) {
        public InternalUploadRequest { action(schemaVersion, runId, stepId, leaseGeneration, actionId); constant(purpose, "AGENT_ARTIFACT"); revisions(sizeBytes); ids(dependencyAccessSetId); }
    }
    public record InternalUploadCompletion(String schemaVersion, String runId, String stepId, long leaseGeneration, String actionId, String inputSha256,
                                           String expectedRevision, String sha256, String sizeBytes) {
        public InternalUploadCompletion { action(schemaVersion, runId, stepId, leaseGeneration, actionId); revisions(expectedRevision, sizeBytes); }
    }
    public record InternalSearchRequest(String schemaVersion, String runId, String stepId, long leaseGeneration, String actionId, String inputSha256,
                                        String query, String snapshotId, String retrievalProfileRevision, SearchPurpose purpose, String cursor) {
        public InternalSearchRequest { action(schemaVersion, runId, stepId, leaseGeneration, actionId); ids(snapshotId, retrievalProfileRevision); }
    }
    public record InternalSearchResult(String retrievalId, SearchStatus status, List<DatasetProtocol.EvidenceRef> evidenceRefs, List<String> missingRequirementIds,
                                       String manifestSha256, Coverage coverage, String readReceiptId) {
        public InternalSearchResult { ids(retrievalId, readReceiptId); evidenceRefs = List.copyOf(evidenceRefs); missingRequirementIds = copyIds(missingRequirementIds); }
    }
    public record InternalSourceReadRequest(String schemaVersion, String runId, String stepId, long leaseGeneration, DatasetProtocol.EvidenceRef evidenceRef, ReadPurpose purpose, String cursor) {
        public InternalSourceReadRequest { step(schemaVersion, runId, stepId, leaseGeneration); }
    }
    public record InternalSourceReadResult(DatasetProtocol.EvidenceRef evidenceRef, ContentChunk chunk) { }

    private static void step(String schemaVersion, String runId, String stepId, long leaseGeneration) {
        constant(schemaVersion, "1.0.0"); ids(runId, stepId); if (leaseGeneration < 0 || leaseGeneration > 9007199254740991L) invalid();
    }
    private static void action(String schemaVersion, String runId, String stepId, long leaseGeneration, String actionId) {
        step(schemaVersion, runId, stepId, leaseGeneration); ids(actionId);
    }
    private static void optionalRevisions(String... values) { for (String value : values) if (value != null) revisions(value); }
    private static boolean empty(String value) { return value == null || value.isEmpty(); }
    private static void invalid() { throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED"); }
}
