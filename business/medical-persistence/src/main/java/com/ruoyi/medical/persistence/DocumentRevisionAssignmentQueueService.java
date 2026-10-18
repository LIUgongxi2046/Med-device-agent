package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.DocumentReviewReturnMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Current user's separate revision queue; never synthesizes agent run or step identifiers. */
@Service
public final class DocumentRevisionAssignmentQueueService {
    private final EnterpriseAccess access;
    private final DocumentReadService documents;
    private final ResourceReadAuthority authority;
    private final DocumentReviewReturnMapper mapper;
    private final DocumentReviewReturnService returns;
    private final DocumentReviewReturnSchema receipts;
    private final DocumentRevisionAssignmentQueueSchema schema;
    private final DocumentRevisionAssignmentCursor cursors;
    private final PermissionSchema json;
    public DocumentRevisionAssignmentQueueService(EnterpriseAccess access, DocumentReadService documents,
            ResourceReadAuthority authority, DocumentReviewReturnMapper mapper, DocumentReviewReturnService returns,
            DocumentReviewReturnSchema receipts, DocumentRevisionAssignmentQueueSchema schema,
            DocumentRevisionAssignmentCursor cursors, PermissionSchema json) {
        this.access=access; this.documents=documents; this.authority=authority; this.mapper=mapper;
        this.returns=returns; this.receipts=receipts; this.schema=schema; this.cursors=cursors; this.json=json;
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, int limit, String cursor) {
        PermissionSchema.revision(context);
        if (limit < 1 || limit > 20) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        if (cursor != null) json.requireId(cursor);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!context.equals(admission.context().context_revision())) fail(ErrorCode.CONTEXT_CHANGED);
            var ctx=admission.context();
            var owner=new DocumentRevisionAssignmentCursor.Owner(ctx.tenant_id(), subject.user().user_id(), subject.sessionId(),
                    context, ctx.auth_epoch(), ctx.governance_epoch(), limit);
            String after=cursors.resolve(cursor,owner);
            var query=new HashMap<String,Object>(); query.put("userId",subject.user().user_id());
            query.put("after",after); query.put("fetchLimit",limit+1);
            var candidates=mapper.myAssignments(query);
            if (candidates.size()>limit+1) fail(ErrorCode.CONTEXT_LIMIT);
            String previous=after;
            for (var candidate:candidates) {
                json.requireId(candidate.assignmentId());
                if (candidate.assignmentId().compareTo(previous)<=0 || !subject.user().user_id().equals(candidate.assigneeId())) fail(ErrorCode.CONTENT_MISMATCH);
                previous=candidate.assignmentId();
            }
            var items=json.object().arrayNode();
            var proofs=new LinkedHashMap<String,DocumentReadService.MetadataRead>();
            var witnesses=new LinkedHashMap<String,List<PermissionProtocol.GrantRow>>();
            String last=after;
            int scanned=Math.min(limit,candidates.size());
            for (int i=0; i<scanned; i++) {
                var candidate=candidates.get(i);
                json.requireId(candidate.assignmentId());
                if (candidate.assignmentId().compareTo(last)<=0 || !subject.user().user_id().equals(candidate.assigneeId())) fail(ErrorCode.CONTENT_MISMATCH);
                last=candidate.assignmentId();
                String key=candidate.documentId()+":"+candidate.documentRevision();
                DocumentReadService.MetadataRead proof;
                try {
                    var scope=new PermissionProtocol.Scope(ctx.tenant_id(),candidate.projectId(),"TASK",candidate.taskId());
                    var grants=List.of(authority.require(admission,scope,"task.read","TASK_READING"),
                            authority.require(admission,scope,"work_item.read","WORK_ITEM_READING"));
                    proof=proofs.get(key);
                    if (proof==null) proof=documents.authorizeMetadata(admission,context,candidate.documentId(),candidate.documentRevision());
                    var h=proof.header();
                    if (!ctx.tenant_id().equals(h.tenantId()) || !candidate.projectId().equals(h.projectId())
                            || !candidate.documentId().equals(h.documentId()) || !candidate.documentRevision().equals(h.documentRevision())
                            || !candidate.taskId().equals(h.taskId()) || !candidate.taskRevision().equals(h.taskRevision())) fail(ErrorCode.CONTENT_MISMATCH);
                    proofs.put(key,proof); witnesses.put(candidate.taskId(),grants);
                } catch (TenantAccessException denied) {
                    if (denied.code()==ErrorCode.ACCESS_DENIED || denied.code()==ErrorCode.SOURCE_NOT_READY || denied.code()==ErrorCode.SOURCE_MISSING) continue;
                    throw denied;
                }
                var values=new HashMap<String,Object>(); values.put("userId",subject.user().user_id());
                values.put("projectId",candidate.projectId()); values.put("documentId",candidate.documentId());
                values.put("documentRevision",candidate.documentRevision()); values.put("taskId",candidate.taskId());
                values.put("operationId",candidate.operationId()); values.put("returnId",candidate.returnId());
                var stored=one(mapper.stored(values));
                if (!candidate.assignmentId().equals(stored.assignmentId()) || !candidate.returnId().equals(stored.returnId())
                        || !candidate.operationId().equals(stored.operationId())) fail(ErrorCode.CONTENT_MISMATCH);
                var decision=returns.historyReceipt(values,stored,candidate.documentId()).required("data").required("decision");
                if (!receipts.read(one(mapper.decision(values))).equals(decision)) fail(ErrorCode.CONTENT_MISMATCH);
                if (!ctx.tenant_id().equals(decision.required("baseline_manifest").required("tenant_id").asText())
                        || !candidate.projectId().equals(decision.required("baseline_manifest").required("project_id").asText())) fail(ErrorCode.CONTENT_MISMATCH);
                var assignment=decision.required("assignment");
                if (!candidate.assigneeId().equals(assignment.required("assignee_id").asText())
                        || !candidate.taskId().equals(assignment.required("task_id").asText())
                        || !candidate.taskRevision().equals(assignment.required("task_revision").asText())
                        || !candidate.documentRevision().equals(assignment.required("document_revision").asText())) fail(ErrorCode.CONTENT_MISMATCH);
                var task=one(mapper.task(values));
                if (!candidate.taskId().equals(task.taskId())) fail(ErrorCode.CONTENT_MISMATCH);
                boolean active=task.taskRevision().equals(candidate.taskRevision()) && "RETURNED".equals(task.status());
                String state=!active?"INACTIVE":proof.header().currentDocumentRevision().equals(candidate.documentRevision())?"OPEN":"REVISION_AVAILABLE";
                var view=json.object().put("effective_state",state).put("resubmission_available",false)
                        .put("resubmission_blocker","CURRENT_CHECK_VERIFIER_UNAVAILABLE")
                        .put("current_document_revision",proof.header().currentDocumentRevision())
                        .put("current_task_revision",task.taskRevision()).put("task_status",task.status());
                view.set("assignment",assignment);
                var item=json.object().put("project_id",candidate.projectId()); item.set("decision",decision); item.set("view",view); items.add(item);
            }
            // Reuse each fixed metadata proof within this page, then revalidate current source and resource authority.
            for (var proof:proofs.values()) {
                documents.requireMetadataCurrent(admission,proof);
                var h=proof.header(); var scope=new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"TASK",h.taskId());
                var grants=List.of(authority.require(admission,scope,"task.read","TASK_READING"),
                        authority.require(admission,scope,"work_item.read","WORK_ITEM_READING"));
                if (!grants.equals(witnesses.get(h.taskId()))) fail(ErrorCode.SCOPE_STALE);
            }
            String next=candidates.size()>limit?cursors.issue(owner,last):null;
            var data=json.object().put("schema_version","1.0.0").put("tenant_id",ctx.tenant_id())
                    .put("actor_id",subject.user().user_id()).put("context_revision",context)
                    .put("auth_epoch",ctx.auth_epoch()).put("governance_epoch",ctx.governance_epoch())
                    .put("revision",context).put("limit",limit).put("next_cursor",next).put("complete",next==null);
            data.set("items",items); data.put("listing_sha256",receipts.hash(data));
            return schema.envelope(data,UUID.randomUUID().toString());
        }).get());
    }
    private static <T> T one(List<T> rows) { if(rows.size()!=1) fail(ErrorCode.CONTENT_MISMATCH); return rows.get(0); }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
