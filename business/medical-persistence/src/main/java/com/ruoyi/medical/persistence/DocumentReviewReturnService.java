package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.DocumentReviewReturnMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.util.*;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Independent whole-document return for revision. Never creates a formal review decision or passing check. */
@Service
public final class DocumentReviewReturnService {
    static final String OPERATION = "DOCUMENT-RETURN-FOR-REVISION-1";
    private final EnterpriseAccess access;
    private final DocumentReadService documents;
    private final DocumentReviewReturnMapper mapper;
    private final ResourceReadAuthority authority;
    private final PermissionMapper permissions;
    private final TaskPlanService plans;
    private final PermissionAuthority targetAuthority;
    private final DocumentReviewReturnSchema schema;
    private final PermissionSchema json;
    public DocumentReviewReturnService(EnterpriseAccess access, DocumentReadService documents,
            DocumentReviewReturnMapper mapper, ResourceReadAuthority authority, PermissionMapper permissions,
            TaskPlanService plans, PermissionAuthority targetAuthority, DocumentReviewReturnSchema schema, PermissionSchema json) {
        this.access=access; this.documents=documents; this.mapper=mapper; this.authority=authority;
        this.permissions=permissions; this.plans=plans; this.targetAuthority=targetAuthority; this.schema=schema; this.json=json;
    }

    public JsonNode basis(ApplicationSessionIdentity.Verified subject, String context, String documentId, String revision) throws Exception {
        var fixed=documents.readFixed(subject,context,documentId,revision);
        return transaction(subject, admission -> {
            current(admission,context,fixed); var values=values(subject,fixed);
            return schema.envelope("Basis",basis(admission,fixed,values),id());
        });
    }

    public JsonNode history(ApplicationSessionIdentity.Verified subject, String context, String documentId, String revision) throws Exception {
        var fixed=documents.readFixed(subject,context,documentId,revision);
        return transaction(subject, admission -> {
            readCurrent(admission,context,fixed); var values=values(subject,fixed);
            var items=json.object().arrayNode();
            for(var row:bounded(mapper.assignments(values))) {
                var receipt=historyReceipt(values,row,documentId).required("data");
                values.put("returnId",row.returnId());
                if(!schema.read(single(mapper.decision(values))).equals(receipt.required("decision"))) mismatch();
                var item=json.object(); item.set("decision",receipt.required("decision"));
                item.set("invalidated_review_ids",receipt.required("invalidated_review_ids")); items.add(item);
            }
            return listing("History",fixed,items);
        });
    }

    public JsonNode assignees(ApplicationSessionIdentity.Verified subject,String context,String documentId,String revision) throws Exception {
        var fixed=documents.readFixed(subject,context,documentId,revision);
        return transaction(subject,admission -> {
            current(admission,context,fixed); var values=values(subject,fixed); var items=json.object().arrayNode(); var seen=new HashSet<String>();
            for(var candidate:bounded(mapper.assigneeCandidates(values))) {
                if(!fixed.header().tenantId().equals(candidate.tenantId()) || !seen.add(candidate.userId())) mismatch();
                json.requireId(candidate.userId()); json.requireId(candidate.memberId());
                try { targetEligibility(admission,fixed,candidate.userId(),values); }
                catch(TenantAccessException unavailable) { if(unavailable.code()==ErrorCode.ACCESS_DENIED) continue; throw unavailable; }
                items.add(json.object().put("member_id",candidate.memberId()).put("user_id",candidate.userId())
                        .put("member_revision",candidate.revision().toString()).putNull("display_name"));
            }
            return listing("Assignees",fixed,items);
        });
    }

    public JsonNode assignments(ApplicationSessionIdentity.Verified subject, String context, String documentId, String revision) throws Exception {
        var fixed=documents.readFixed(subject,context,documentId,revision);
        return transaction(subject, admission -> {
            readCurrent(admission,context,fixed);
            authority.require(admission,new PermissionProtocol.Scope(fixed.header().tenantId(),fixed.header().projectId(),
                    "TASK",fixed.header().taskId()),"task.read","TASK_READING");
            authority.require(admission,new PermissionProtocol.Scope(fixed.header().tenantId(),fixed.header().projectId(),
                    "TASK",fixed.header().taskId()),"work_item.read","WORK_ITEM_READING");
            var values=values(subject,fixed); var items=json.object().arrayNode(); var task=single(mapper.task(values));
            for(var row:bounded(mapper.assignments(values))) {
                var receipt=historyReceipt(values,row,documentId); var assignment=receipt.required("data").required("decision").required("assignment");
                if(!fixed.header().documentRevision().equals(assignment.required("document_revision").asText())) mismatch();
                boolean active=task.taskRevision().equals(assignment.required("task_revision").asText()) && "RETURNED".equals(task.status());
                String state=!active?"INACTIVE":fixed.header().currentDocumentRevision().equals(assignment.required("document_revision").asText())
                        ?"OPEN":"REVISION_AVAILABLE";
                var view=json.object().put("effective_state",state).put("resubmission_available",false)
                        .put("resubmission_blocker","CURRENT_CHECK_VERIFIER_UNAVAILABLE")
                        .put("current_document_revision",fixed.header().currentDocumentRevision()).put("current_task_revision",task.taskRevision())
                        .put("task_status",task.status()); view.set("assignment",assignment); items.add(view);
            }
            return listing("Assignments",fixed,items);
        });
    }

    public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String key,String documentId,byte[] body) throws Exception {
        json.headers(context,key); json.requireId(documentId); var request=schema.request(body);
        var target=request;
        var fixed=documents.readFixed(subject,context,documentId,request.required("document_revision").asText());
        String hash=schema.requestHash(documentId,request);
        return transaction(subject, admission -> {
            current(admission,context,fixed); var values=values(subject,fixed); values.put("key",key); values.put("requestHash",hash);
            var operations=mapper.operation(values); if(operations.size()>1) mismatch();
            if(!operations.isEmpty()) {
                if(!hash.equals(operations.get(0).requestSha256())) fail(ErrorCode.IDEMPOTENCY_CONFLICT);
                return replay(values,operations.get(0),documentId);
            }
            var basis=basis(admission,fixed,values);
            if(!basis.required("return_available").booleanValue()) {
                for(var blocker:basis.required("blockers")) if("DOCUMENT_SIGNED".equals(blocker.asText())) fail(ErrorCode.DOCUMENT_SIGNED);
                fail(ErrorCode.ACTION_STALE);
            }
            if(!basis.equals(request.required("basis"))) fail(ErrorCode.REVISION_CONFLICT);
            var assignee=targetEligibility(admission,fixed,target.required("assignee_id").asText(),values);
            var witness=grant(admission,fixed); var now=Objects.requireNonNull(permissions.databaseTime());
            String operationId=id(),returnId=id(),assignmentId=id(),requestId=id();
            String expected=basis.required("revision").asText(), expectedTask=basis.required("task_head_revision").asText();
            String resulting=PermissionSchema.next(expected), resultingTask=PermissionSchema.next(expectedTask);
            values.put("expectedDocument",expected); values.put("expectedTask",expectedTask);
            values.put("resultingDocument",resulting); values.put("operationId",operationId);
            values.put("returnId",returnId); values.put("assignmentId",assignmentId); values.put("requestId",requestId);
            values.put("assigneeId",target.required("assignee_id").asText()); values.put("reason",target.required("reason").asText());
            values.put("now",now); values.put("basis",json.canonical(basis));
            var witnessDocument=json.object();witnessDocument.set("review",grantSnapshot(List.of(witness)).get(0));witnessDocument.set("assignee",grantSnapshot(assignee));
            var storedRequest=json.object().put("schema_version","1.0.0").put("authority_witness_sha256",schema.hash(witnessDocument));
            storedRequest.set("request",request);storedRequest.set("authority_witness",witnessDocument);values.put("request",json.canonical(storedRequest));
            var invalidated=json.object().arrayNode();
            for(var review:bounded(mapper.confirmations(values))) { json.requireId(review); invalidated.add(review); }
            var assignment=json.object().put("assignment_id",assignmentId).put("kind","DOCUMENT_RETURN_FOR_REVISION")
                    .put("return_id",returnId).put("document_id",documentId)
                    .put("document_revision",fixed.header().documentRevision()).put("task_id",fixed.header().taskId())
                    .put("task_revision",fixed.header().taskRevision()).put("assignee_id",target.required("assignee_id").asText())
                    .put("reason",target.required("reason").asText()).put("status","WAITING_INPUT").put("revision","1")
                    .put("created_at",time(now));
            var decision=json.object().put("return_id",returnId).put("document_id",documentId)
                    .put("document_revision",fixed.header().documentRevision()).put("decision","RETURN_FOR_REVISION")
                    .put("reason",target.required("reason").asText()).put("actor_id",subject.user().user_id())
                    .put("assignee_id",target.required("assignee_id").asText()).put("decided_at",time(now));
            decision.set("baseline_manifest",basis); decision.set("assignment",assignment);
            var receipt=json.object().put("schema_version","1.0.0").put("operation_id",operationId).put("document_id",documentId)
                    .put("document_revision",fixed.header().documentRevision()).put("previous_revision",expected).put("revision",resulting)
                    .put("previous_task_revision",expectedTask).put("task_head_revision",resultingTask).put("task_status","RETURNED")
                    .put("request_sha256",hash).put("decision_sha256",schema.hash(decision));
            receipt.set("decision",decision); receipt.set("invalidated_review_ids",invalidated);
            var response=schema.envelope("Receipt",receipt,requestId); schema.receipt(json.canonical(response));
            values.put("response",json.canonical(response));
            // All writes occur in this EnterpriseAccess transaction. Any failed CAS rolls back every inserted row.
            one(mapper.saveOperation(values)); one(mapper.insertDecision(values)); one(mapper.insertAssignment(values));
            for(var review:invalidated) {
                values.put("invalidatedReviewId",review.asText()); values.put("invalidationId",id()); one(mapper.invalidate(values));
            }
            one(mapper.advanceDocument(values)); one(mapper.advanceTask(values));
            var event=event(receipt); var audit=audit(receipt,storedRequest);
            one(permissions.saveAudit(subject.user().user_id(),fixed.header().projectId(),id(),"document.returned_for_revision",
                    json.canonical(audit),requestId,schema.manifestSha256(),fixed.header().classification()));
            one(permissions.saveEvent(subject.user().user_id(),id(),documentId,resulting,"document.returned_for_revision",json.canonical(event)));
            if(!schema.read(single(mapper.audit(values))).equals(audit) || !schema.read(single(mapper.event(values))).equals(event)) mismatch();
            if(!replay(values,single(mapper.operation(values)),documentId).equals(response)) mismatch();
            if(!assignee.equals(targetEligibility(admission,fixed,target.required("assignee_id").asText(),values))
                    || !witness.equals(grant(admission,fixed))) fail(ErrorCode.SCOPE_STALE);
            return response;
        });
    }

    public JsonNode recover(ApplicationSessionIdentity.Verified subject,String context,String key,String documentId,String revision) throws Exception {
        json.headers(context,key); var fixed=documents.readFixed(subject,context,documentId,revision);
        return transaction(subject, admission -> {
            readCurrent(admission,context,fixed); var values=values(subject,fixed); values.put("key",key);
            var rows=mapper.operation(values); if(rows.size()>1) mismatch();
            if(rows.isEmpty()) return json.tree(new com.ruoyi.medical.contracts.InterfaceProtocol.Envelope("1.0.0",id(),
                    fixed.header().revision().toString(),com.fasterxml.jackson.databind.node.NullNode.instance));
            var result=replay(values,rows.get(0),documentId);
            if(!revision.equals(result.required("data").required("document_revision").asText())) mismatch(); return result;
        });
    }

    private ObjectNode basis(EnterpriseAccess.Admission admission,DocumentReadService.FixedRead fixed,Map<String,Object> values) throws Exception {
        var header=fixed.header(); var task=single(mapper.task(values)); var checks=json.object().arrayNode();
        var seen=new HashSet<String>();
        for(var raw:bounded(mapper.checks(values))) {
            var check=schema.read(raw); schema.validate("Check",check);
            if(!seen.add(check.required("check_id").asText())) mismatch();
            if(check.required("record_complete").booleanValue() && (!"SUCCEEDED".equals(check.required("status").asText())
                    || !check.required("invalidation_reason").isNull() || check.required("coverage_artifact_id").isNull()
                    || check.required("findings_artifact_id").isNull())) mismatch();
            checks.add(check);
        }
        var blockers=json.object().arrayNode();
        if(!header.documentRevision().equals(header.currentDocumentRevision())) blockers.add("DOCUMENT_NOT_CURRENT");
        if(!"ACTIVE".equals(header.projectStatus())) blockers.add("PROJECT_NOT_ACTIVE");
        if("SIGNED".equals(header.status()) || !bounded(mapper.signatures(values)).isEmpty()) blockers.add("DOCUMENT_SIGNED");
        if(!"REVIEW_PENDING".equals(task.status())) blockers.add("TASK_NOT_REVIEW_PENDING");
        if(!header.taskRevision().equals(task.taskRevision())) blockers.add("TASK_REVISION_CHANGED");
        var result=json.object().put("schema_version","1.0.0").put("tenant_id",header.tenantId()).put("project_id",header.projectId())
                .put("document_id",header.documentId()).put("document_revision",header.documentRevision()).put("body_sha256",header.bodySha256())
                .put("task_id",task.taskId()).put("task_revision",header.taskRevision()).put("task_head_revision",task.revision().toString())
                .put("task_status",task.status()).put("revision",header.revision().toString())
                .put("context_revision",admission.context().context_revision()).put("auth_epoch",admission.context().auth_epoch())
                .put("governance_epoch",admission.context().governance_epoch()).put("document_read_sha256",fixed.digest())
                .put("return_available",blockers.isEmpty());
        result.set("checks",checks); result.set("blockers",blockers);
        result.put("formal_review_available",false).put("formal_check_currentness","NOT_VERIFIED");
        result.put("basis_sha256",schema.hash(result)); schema.validate("Basis",result); return result;
    }
    JsonNode stored(DocumentReviewReturnProtocol.Stored row,String documentId) throws Exception {
        if(!schema.manifestSha256().equals(row.manifest())) mismatch();
        var internal=internalRequest(row);var request=schema.request(json.canonical(internal.required("request")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(!schema.requestHash(documentId,request).equals(row.requestHash())) mismatch();
        var response=schema.receipt(row.receipt()); var value=response.required("data"); var decision=value.required("decision");
        if(!row.operationId().equals(value.required("operation_id").asText()) || !documentId.equals(value.required("document_id").asText())
                || !row.returnId().equals(decision.required("return_id").asText())
                || !row.assignmentId().equals(decision.required("assignment").required("assignment_id").asText())
                || !request.required("basis").equals(decision.required("baseline_manifest"))
                || !row.requestHash().equals(value.required("request_sha256").asText())) mismatch();
        var target=request;
        if(!target.required("reason").equals(decision.required("reason")) || !target.required("assignee_id").equals(decision.required("assignee_id"))) mismatch();
        return response;
    }
    /** Current reader stays the admission subject; original creator is constrained by immutable SQL joins. */
    JsonNode historyReceipt(Map<String,Object> values, DocumentReviewReturnProtocol.Stored row, String documentId) throws Exception {
        var response=stored(row,documentId); var data=response.required("data");
        values.put("returnId",row.returnId()); values.put("operationId",row.operationId());
        values.put("requestId",response.required("request_id").asText());
        values.put("resultingDocument",data.required("revision").asText()); values.put("manifest",schema.manifestSha256());
        if(!schema.read(single(mapper.decision(values))).equals(data.required("decision"))) mismatch();
        var operation=single(mapper.historyOperation(values));
        if(!row.operationId().equals(operation.operationId()) || !documentId.equals(operation.targetId())
                || !"COMPLETED".equals(operation.state()) || !Integer.valueOf(200).equals(operation.httpStatus())
                || !"1.0.0".equals(operation.schemaVersion()) || !schema.manifestSha256().equals(operation.schemaManifestSha256())
                || !row.requestHash().equals(operation.requestSha256()) || !response.equals(schema.receipt(operation.responseRef()))) mismatch();
        var audit=schema.read(single(mapper.historyAudit(values))); var event=event(data);
        if(!audit.equals(audit(data,internalRequest(row))) || !schema.read(single(mapper.event(values))).equals(event)) mismatch();
        verifyInvalidations(values,data);
        return response;
    }
    private JsonNode replay(Map<String,Object> values,SelectionOperationRow operation,String documentId) throws Exception {
        if(!documentId.equals(operation.targetId()) || !"COMPLETED".equals(operation.state()) || !Integer.valueOf(200).equals(operation.httpStatus())
                || !"1.0.0".equals(operation.schemaVersion()) || !schema.manifestSha256().equals(operation.schemaManifestSha256())) mismatch();
        values.put("operationId",operation.operationId()); var row=single(mapper.stored(values)); var response=stored(row,documentId);
        values.put("returnId",row.returnId());
        if(!schema.read(single(mapper.decision(values))).equals(response.required("data").required("decision"))) mismatch();
        if(!row.requestHash().equals(operation.requestSha256()) || !response.equals(schema.receipt(operation.responseRef()))) mismatch();
        values.put("requestId",response.required("request_id").asText()); values.put("resultingDocument",response.required("data").required("revision").asText());
        var audit=schema.read(single(mapper.audit(values))); var event=event(response.required("data"));
        if(!audit.equals(audit(response.required("data"),internalRequest(row))) || !schema.read(single(mapper.event(values))).equals(event)) mismatch();
        verifyInvalidations(values,response.required("data"));
        return response;
    }
    private JsonNode internalRequest(DocumentReviewReturnProtocol.Stored row) throws Exception {
        var value=schema.read(row.request());
        if(!value.isObject() || value.size()!=4 || !"1.0.0".equals(value.path("schema_version").asText())
                || !value.has("request") || !value.has("authority_witness") || !value.has("authority_witness_sha256")) mismatch();
        var witnesses=value.required("authority_witness");
        if(!witnesses.isObject() || witnesses.size()!=2 || !witnesses.has("review") || !witnesses.path("assignee").isArray()
                || witnesses.required("assignee").size()!=3 || !schema.hash(witnesses).equals(value.required("authority_witness_sha256").asText())) mismatch();
        var fields=Set.of("grant_id","subject_id","project_id","resource_kind","resource_id","action","condition","status","valid_from","valid_to","basis_version","schema_version","manifest","revision");
        var snapshots=new ArrayList<JsonNode>();snapshots.add(witnesses.required("review"));witnesses.required("assignee").forEach(snapshots::add);
        for(var snapshot:snapshots){var names=new HashSet<String>();snapshot.fieldNames().forEachRemaining(names::add);if(!snapshot.isObject() || !fields.equals(names))mismatch();json.requireId(snapshot.required("grant_id").asText());PermissionSchema.revision(snapshot.required("revision").asText());}
        if(!json.canonical(value).equals(row.request()))mismatch();return value;
    }
    private ObjectNode audit(JsonNode receipt,JsonNode internal) throws Exception {
        var witness=internal.required("authority_witness");var review=witness.required("review");
        var result=json.object().put("schema_version","1.0.0").put("operation_id",receipt.required("operation_id").asText())
                .put("request_sha256",receipt.required("request_sha256").asText())
                .put("authority_grant_id",review.required("grant_id").asText()).put("authority_revision",review.required("revision").asText())
                .put("assignee_authority_sha256",schema.hash(witness.required("assignee")))
                .put("authority_witness_sha256",schema.hash(witness));
        result.set("authority_witness",witness);result.set("after",event(receipt));return result;
    }
    private void verifyInvalidations(Map<String,Object> values,JsonNode receipt) {
        var expected=new TreeSet<String>();for(var id:receipt.required("invalidated_review_ids"))if(!expected.add(id.asText()))mismatch();
        var actual=bounded(mapper.invalidations(values));if(actual.size()!=expected.size() || !expected.equals(new TreeSet<>(actual)))mismatch();
    }
    private ObjectNode event(JsonNode receipt) {
        return json.object().put("schema_version","1.0.0").put("operation_id",receipt.required("operation_id").asText())
                .put("document_id",receipt.required("document_id").asText()).put("document_revision",receipt.required("document_revision").asText())
                .put("return_id",receipt.required("decision").required("return_id").asText())
                .put("assignment_id",receipt.required("decision").required("assignment").required("assignment_id").asText())
                .put("revision",receipt.required("revision").asText()).put("task_status","RETURNED");
    }
    private JsonNode listing(String kind,DocumentReadService.FixedRead fixed,ArrayNode items) throws Exception {
        var data=json.object().put("schema_version","1.0.0").put("document_id",fixed.header().documentId())
                .put("document_revision",fixed.header().documentRevision()).put("revision",fixed.header().revision().toString())
                .put("total",Integer.toString(items.size())).put("complete",true); data.set("items",items);
        data.put("listing_sha256",schema.hash(data)); return schema.envelope(kind,data,id());
    }
    private void current(EnterpriseAccess.Admission admission,String context,DocumentReadService.FixedRead fixed) throws Exception {
        if(!context.equals(admission.context().context_revision())) fail(ErrorCode.CONTEXT_CHANGED);
        readCurrent(admission,context,fixed); grant(admission,fixed);
    }
    private void readCurrent(EnterpriseAccess.Admission admission,String context,DocumentReadService.FixedRead fixed) throws Exception {
        if(!context.equals(admission.context().context_revision())) fail(ErrorCode.CONTEXT_CHANGED);
        documents.requireCurrent(admission,fixed);
    }
    private List<PermissionProtocol.GrantRow> targetEligibility(EnterpriseAccess.Admission admission,DocumentReadService.FixedRead fixed,
            String assignee,Map<String,Object> values) throws Exception {
        // The current reviewer remains the transaction subject; this inspects bounded target eligibility only.
        var taskGrant=plans.creationOwner(admission,fixed.header().projectId(),fixed.header().taskId(),assignee);
        var binding=single(permissions.targetBinding(assignee));
        if(!assignee.equals(binding.userId()) || !"ACTIVE".equals(binding.status())) fail(ErrorCode.ACCESS_DENIED);
        values.put("assigneeId",assignee); var now=Objects.requireNonNull(permissions.databaseTime());
        values.put("targetAction","document.read");
        var documentGrant=targetAuthority.requireResource(new PermissionProtocol.Scope(fixed.header().tenantId(),fixed.header().projectId(),
                "DOCUMENT",fixed.header().documentId()),assignee,binding.mode(),"document.read","DOCUMENT_BODY",bounded(mapper.targetGrants(values)),now);
        values.put("targetAction","work_item.read");
        var workGrant=targetAuthority.requireResource(new PermissionProtocol.Scope(fixed.header().tenantId(),fixed.header().projectId(),
                "TASK",fixed.header().taskId()),assignee,binding.mode(),"work_item.read","WORK_ITEM_READING",bounded(mapper.targetGrants(values)),now);
        return List.of(taskGrant,documentGrant,workGrant);
    }
    private ArrayNode grantSnapshot(List<PermissionProtocol.GrantRow> grants) throws Exception {
        var witnesses = json.object().arrayNode();
        for (var grant : grants) {
            witnesses.add(json.object().put("grant_id", grant.grantId()).put("subject_id", grant.subjectId())
                    .put("project_id", grant.projectId()).put("resource_kind", grant.resourceKind())
                    .put("resource_id", grant.resourceId()).put("action", grant.action())
                    .put("condition", grant.condition()).put("status", grant.status())
                    .put("valid_from", grant.validFrom() == null ? null : grant.validFrom().toString())
                    .put("valid_to", grant.validTo() == null ? null : grant.validTo().toString())
                    .put("basis_version", grant.basisVersion()).put("schema_version", grant.schemaVersion())
                    .put("manifest", grant.manifest()).put("revision", grant.revision().toString()));
        }
        return witnesses;
    }
    private PermissionProtocol.GrantRow grant(EnterpriseAccess.Admission admission,DocumentReadService.FixedRead fixed) throws Exception {
        return authority.require(admission,new PermissionProtocol.Scope(fixed.header().tenantId(),fixed.header().projectId(),
                "DOCUMENT",fixed.header().documentId()),"review.decide","DOCUMENT_REVIEW");
    }
    private Map<String,Object> values(ApplicationSessionIdentity.Verified subject,DocumentReadService.FixedRead fixed) {
        var h=fixed.header(); var v=new HashMap<String,Object>(); v.put("userId",subject.user().user_id()); v.put("projectId",h.projectId());
        v.put("documentId",h.documentId()); v.put("documentRevision",h.documentRevision()); v.put("taskId",h.taskId()); v.put("taskRevision",h.taskRevision());
        v.put("evidenceWatermark",fixed.watermarks().evidenceWatermark().toString()); v.put("issueWatermark",fixed.watermarks().issueWatermark().toString());
        v.put("manifest",schema.manifestSha256()); v.put("classification",h.classification()); return v;
    }
    private <T> T transaction(ApplicationSessionIdentity.Verified subject,ThrowingTransaction<T> work) {
        return access.execute(subject,admission -> ThrowingSupplier.of(() -> work.run(admission)).get());
    }
    @FunctionalInterface private interface ThrowingTransaction<T> { T run(EnterpriseAccess.Admission admission) throws Exception; }
    private static <T> List<T> bounded(List<T> rows) { if(rows.size()>DocumentReviewReturnSchema.LIMIT) fail(ErrorCode.CONTEXT_LIMIT); return rows; }
    private static <T> T single(List<T> rows) { if(rows.size()!=1) mismatch(); return rows.get(0); }
    private static void one(int count) { if(count!=1) fail(ErrorCode.REVISION_CONFLICT); }
    private static String time(java.time.LocalDateTime value) {
        return java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd\'T\'HH:mm:ss.SSSSSS\'Z\'").format(value);
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static void mismatch() { fail(ErrorCode.CONTENT_MISMATCH); }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
