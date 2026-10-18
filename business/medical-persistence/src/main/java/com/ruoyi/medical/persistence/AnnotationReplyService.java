package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.AnnotationReplyMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Persisted RV-05 replies. This service never decides, resolves, reanchors, or signs an annotation/document. */
@Service
public final class AnnotationReplyService {
    private static final String PURPOSE = "DOCUMENT_REVIEW";
    private static final String EVENT = "annotation.replied";
    private final EnterpriseAccess access;
    private final AnnotationReplyMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;
    private final DocumentReadService documents;
    private final AnnotationReplySchema schema;
    private final PermissionSchema json;

    public AnnotationReplyService(EnterpriseAccess access, AnnotationReplyMapper mapper, PermissionMapper permissions,
            ResourceReadAuthority authority, DocumentReadService documents, AnnotationReplySchema schema, PermissionSchema json) {
        this.access = access; this.mapper = mapper; this.permissions = permissions; this.authority = authority;
        this.documents = documents; this.schema = schema; this.json = json;
    }

    public JsonNode list(ApplicationSessionIdentity.Verified subject, String context, String annotationId) throws Exception {
        var proof = prepare(subject, context, annotationId, null, "annotation.read");
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            var values = values(subject, annotationId); var parent = current(admission, context, proof, values, "annotation.read");
            values.put("projectId", parent.projectId()); values.put("fetchLimit", AnnotationReplySchema.MAXIMUM_REPLIES + 1);
            long count = count(values);
            if (count > AnnotationReplySchema.MAXIMUM_REPLIES) denied(ErrorCode.CONTEXT_LIMIT);
            var rows = mapper.replies(values);
            if (rows.size() > AnnotationReplySchema.MAXIMUM_REPLIES) denied(ErrorCode.CONTEXT_LIMIT);
            if (count != rows.size()) mismatch();
            var items = json.object().arrayNode(); var ids = new HashSet<String>(); AnnotationReplyProtocol.Row previous = null;
            for (var row : rows) {
                var item = reply(row, parent);
                if (!ids.add(row.replyId()) || previous != null && (row.createdAt().isBefore(previous.createdAt())
                        || row.createdAt().equals(previous.createdAt()) && row.replyId().compareTo(previous.replyId()) <= 0)) mismatch();
                items.add(item); previous = row;
            }
            var data = base(parent, parent.revision().toString());
            data.put("location_status", parent.documentRevision().equals(parent.currentDocumentRevision()) ? "CURRENT_VERSION" : "ORIGINAL_VERSION");
            data.put("total", Long.toString(count)); data.put("complete", true); data.set("items", items);
            data.put("listing_sha256", schema.hash(data));
            var response = schema.envelope("Listing", data, id(), parent.revision().toString());
            clockCurrent(proof, values); return response;
        }).get());
    }

    public JsonNode append(ApplicationSessionIdentity.Verified subject, String context, String key, String annotationId, byte[] body) throws Exception {
        json.headers(context, key); json.requireId(annotationId);
        var request = schema.create(body);
        String requestHash = schema.requestHash(annotationId, request, context, subject.sessionId());
        var proof = prepare(subject, context, annotationId, request.document_revision(), "annotation.write");
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            var values = values(subject, annotationId); var parent = current(admission, context, proof, values, "annotation.write");
            values.put("projectId", parent.projectId()); values.put("classification", parent.classification());
            values.put("manifest", schema.manifestSha256()); values.put("key", key); values.put("requestHash", requestHash);
            var operations = mapper.operation(values); if (operations.size() > 1) mismatch();
            if (!operations.isEmpty()) {
                var response = replay(admission, parent, values, operations.get(0), request);
                clockCurrent(proof, values); return response;
            }
            if(parent.revision().compareTo(BigInteger.valueOf(AnnotationDecisionService.MAXIMUM_LIFECYCLE_EVENTS+1L))>=0) denied(ErrorCode.CONTEXT_LIMIT);
            if (!parent.documentRevision().equals(request.document_revision()) || !parent.revision().toString().equals(request.expected_revision()))
                denied(ErrorCode.REVISION_CONFLICT);
            long beforeCount = count(values); if (beforeCount >= AnnotationReplySchema.MAXIMUM_REPLIES) denied(ErrorCode.CONTEXT_LIMIT);
            var witness = require(admission, parent, "annotation.write");
            String resulting = PermissionSchema.next(request.expected_revision()), replyId = id(), operationId = id(), requestId = id();
            LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            values.put("replyId", replyId); values.put("content", request.content()); values.put("now", now);
            values.put("expected", request.expected_revision()); values.put("resulting", resulting); values.put("parentStatus", parent.status());
            values.put("originalDocumentRevision",proof.originalDocumentRevision()); values.put("documentRevision", parent.documentRevision()); values.put("operationId", operationId); values.put("requestId", requestId);
            one(mapper.advanceParent(values)); one(mapper.insertReply(values));
            var after = parent(admission, annotationId, values);
            if (!after.equals(new AnnotationReplyProtocol.Parent(parent.tenantId(), parent.projectId(), parent.annotationId(), parent.documentId(),
                    parent.documentRevision(), parent.currentDocumentRevision(), parent.bodySha256(), parent.documentHeadRevision(),
                    new BigInteger(resulting), parent.status(), parent.classification())) || count(values) != beforeCount + 1) mismatch();
            var item = reply(single(mapper.reply(values)), after);
            if (!item.required("content").asText().equals(request.content()) || !item.required("actor_id").asText().equals(subject.user().user_id())
                    || !item.required("created_at").asText().equals(time(now)) || !item.required("reply_id").asText().equals(replyId)) mismatch();
            var receipt = base(after, resulting); receipt.put("operation_id", operationId); receipt.put("previous_revision", request.expected_revision());
            receipt.set("reply", item); receipt.put("reply_sha256", schema.hash(item));
            var response = schema.envelope("Receipt", receipt, requestId, resulting);
            values.put("response", json.canonical(response)); one(mapper.saveOperation(values));
            // Keep free text solely in the classified reply/operation records. Audit and outbox carry safe metadata and a digest.
            var event = event(receipt); var audit = json.object().put("schema_version", "1.0.0").put("operation_id", operationId)
                    .put("request_sha256", requestHash).put("authority_grant_id", witness.grantId())
                    .put("authority_revision", witness.revision().toString()); audit.set("after", event);
            one(permissions.saveAudit(subject.user().user_id(), parent.projectId(), id(), EVENT, json.canonical(audit), requestId,
                    schema.manifestSha256(), parent.classification()));
            one(permissions.saveEvent(subject.user().user_id(), id(), annotationId, resulting, EVENT, json.canonical(event)));
            verifyOperation(values, single(mapper.operation(values)), response);
            if (!schema.read(single(mapper.audit(values))).equals(audit) || !schema.read(single(mapper.event(values))).equals(event)) mismatch();
            clockCurrent(proof, values); return response;
        }).get());
    }

    private Proof prepare(ApplicationSessionIdentity.Verified subject, String context, String annotationId, String documentRevision, String action) throws Exception {
        json.requireId(annotationId); PermissionSchema.revision(context);
        var initial = access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            context(admission, context); var values = values(subject, annotationId); var parent = parent(admission, annotationId, values);
            require(admission, parent, action);
            return new Proof(parent, admission.contextId(), admission.context(), admission.watermarks(), subject.user().user_id(), subject.sessionId(),
                    Objects.requireNonNull(mapper.authorizationClock(values)), documentRevision == null ? parent.documentRevision() : documentRevision, Objects.requireNonNull(mapper.originalDocumentRevision(values)), null);
        }).get());
        // The existing reader checks the fixed body, all upstream artifacts, current sources and timed rights.
        // It performs storage I/O outside this service's final short transaction.
        var snapshot = documents.snapshotFixed(subject, context, initial.parent().documentId(), initial.documentRevision()).required("data");
        if (!initial.parent().documentId().equals(snapshot.required("document_id").asText())
                || !initial.parent().projectId().equals(snapshot.required("project_id").asText())
                || !initial.documentRevision().equals(snapshot.required("document_revision").asText())
                || !initial.parent().documentHeadRevision().toString().equals(snapshot.required("revision").asText())
                || !initial.watermarks().authEpoch().toString().equals(snapshot.required("auth_epoch").asText())
                || !initial.watermarks().governanceEpoch().toString().equals(snapshot.required("governance_epoch").asText())
                || initial.parent().documentRevision().equals(initial.documentRevision())
                    && !initial.parent().bodySha256().equals(snapshot.required("body").required("sha256").asText())) denied(ErrorCode.SCOPE_STALE);
        // Historical replay still needs the independent effective and immutable-origin source proofs.
        for(String revision:new LinkedHashSet<>(List.of(initial.parent().documentRevision(),initial.originalDocumentRevision()))) {
            if(revision.equals(initial.documentRevision())) continue;
            var other=documents.snapshotFixed(subject,context,initial.parent().documentId(),revision).required("data");
            if(!initial.parent().documentId().equals(other.required("document_id").asText())
                || !initial.parent().projectId().equals(other.required("project_id").asText())
                || !revision.equals(other.required("document_revision").asText())
                || !initial.parent().documentHeadRevision().toString().equals(other.required("revision").asText())
                || !initial.watermarks().authEpoch().toString().equals(other.required("auth_epoch").asText())
                || !initial.watermarks().governanceEpoch().toString().equals(other.required("governance_epoch").asText())
                || revision.equals(initial.parent().documentRevision()) && !initial.parent().bodySha256().equals(other.required("body").required("sha256").asText())) denied(ErrorCode.SCOPE_STALE);
        }
        return new Proof(initial.parent(), initial.contextId(), initial.context(), initial.watermarks(), initial.actor(), initial.session(),
                initial.authorizationClock(), initial.documentRevision(), initial.originalDocumentRevision(), snapshot.deepCopy());
    }

    private AnnotationReplyProtocol.Parent current(EnterpriseAccess.Admission admission, String context, Proof proof,
            Map<String,Object> values, String action) throws IOException {
        context(admission, context);
        if (!admission.contextId().equals(proof.contextId()) || !admission.context().equals(proof.context())
                || !admission.watermarks().equals(proof.watermarks()) || !admission.subject().user().user_id().equals(proof.actor())
                || !admission.subject().sessionId().equals(proof.session())) denied(ErrorCode.SCOPE_STALE);
        var parent = parent(admission, proof.parent().annotationId(), values); var before = proof.parent();
        if(!proof.originalDocumentRevision().equals(mapper.originalDocumentRevision(values))) denied(ErrorCode.SCOPE_STALE);
        if (!parent.projectId().equals(before.projectId()) || !parent.documentId().equals(before.documentId())
                || !parent.documentRevision().equals(before.documentRevision()) || !parent.currentDocumentRevision().equals(before.currentDocumentRevision())
                || !parent.bodySha256().equals(before.bodySha256()) || !parent.documentHeadRevision().equals(before.documentHeadRevision())
                || !parent.classification().equals(before.classification())) denied(ErrorCode.SCOPE_STALE);
        require(admission, parent, action); clockCurrent(proof, values); return parent;
    }

    private AnnotationReplyProtocol.Parent parent(EnterpriseAccess.Admission admission, String annotationId, Map<String,Object> values) {
        var rows = mapper.parent(values); if (rows.size() != 1) denied(ErrorCode.ACCESS_DENIED);
        var parent = rows.get(0);
        if (!admission.context().tenant_id().equals(parent.tenantId()) || !annotationId.equals(parent.annotationId())) denied(ErrorCode.ACCESS_DENIED);
        for (String value : List.of(parent.projectId(), parent.documentId(), parent.documentRevision(), parent.currentDocumentRevision())) json.requireId(value);
        WireValues.hash(parent.bodySha256());
        if (PermissionSchema.revision(parent.revision().toString()).signum() <= 0
                || PermissionSchema.revision(parent.documentHeadRevision().toString()).signum() <= 0
                || !Set.of("OPEN", "RESOLVED", "RETURNED", "REANCHOR_REQUIRED").contains(parent.status())
                || parent.classification() == null || parent.classification().isBlank()) mismatch();
        return parent;
    }

    private JsonNode replay(EnterpriseAccess.Admission admission, AnnotationReplyProtocol.Parent parent, Map<String,Object> values,
            SelectionOperationRow operation, AnnotationReplyProtocol.Create request) throws IOException {
        if (!Objects.equals(operation.requestSha256(), values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        var response = schema.receipt(operation.responseRef()); verifyOperation(values, operation, response); var receipt = response.required("data");
        if (!receipt.required("document_id").asText().equals(parent.documentId())
                || !receipt.required("document_revision").asText().equals(request.document_revision())
                || !receipt.required("previous_revision").asText().equals(request.expected_revision())
                || parent.revision().compareTo(PermissionSchema.revision(receipt.required("revision").asText())) < 0) mismatch();
        values.put("replyId", receipt.required("reply").required("reply_id").asText());
        var stored = reply(single(mapper.reply(values)), parent);
        if (!stored.equals(receipt.required("reply")) || !stored.required("content").asText().equals(request.content())
                || !stored.required("actor_id").asText().equals(admission.subject().user().user_id())) mismatch();
        values.put("requestId", response.required("request_id").asText()); values.put("resulting", receipt.required("revision").asText());
        var audit = schema.read(single(mapper.audit(values))); var event = event(receipt);
        if (!audit.required("operation_id").asText().equals(operation.operationId())
                || !audit.required("request_sha256").asText().equals(operation.requestSha256())
                || !audit.required("after").equals(event) || !schema.read(single(mapper.event(values))).equals(event)) mismatch();
        return response;
    }

    private void verifyOperation(Map<String,Object> values, SelectionOperationRow operation, JsonNode response) throws IOException {
        if (!Objects.equals(operation.requestSha256(), values.get("requestHash")) || !Objects.equals(operation.targetId(), values.get("annotationId"))
                || !"COMPLETED".equals(operation.state()) || !Integer.valueOf(201).equals(operation.httpStatus())
                || !"1.0.0".equals(operation.schemaVersion()) || !schema.manifestSha256().equals(operation.schemaManifestSha256())
                || !operation.operationId().equals(response.required("data").required("operation_id").asText())
                || !response.required("data").required("annotation_id").asText().equals(values.get("annotationId"))
                || !schema.receipt(operation.responseRef()).equals(response)) mismatch();
    }

    private JsonNode reply(AnnotationReplyProtocol.Row row, AnnotationReplyProtocol.Parent parent) {
        if (!parent.tenantId().equals(row.tenantId()) || !parent.projectId().equals(row.projectId())
                || !parent.annotationId().equals(row.annotationId()) || !row.actorId().equals(row.createdBy())
                || !parent.classification().equals(row.classification())) mismatch();
        AnnotationReplySchema.content(row.content());
        var value = json.object().put("reply_id", row.replyId()).put("annotation_id", row.annotationId()).put("actor_id", row.actorId())
                .put("created_at", time(row.createdAt())).put("content", row.content());
        schema.validate("Reply", value); return value;
    }
    private ObjectNode base(AnnotationReplyProtocol.Parent parent, String revision) {
        return json.object().put("schema_version", "1.0.0").put("annotation_id", parent.annotationId()).put("document_id", parent.documentId())
                .put("document_revision", parent.documentRevision()).put("revision", revision).put("status", parent.status());
    }
    private JsonNode event(JsonNode receipt) {
        var event = ((ObjectNode) receipt).deepCopy(); var reply = event.remove("reply");
        event.put("reply_id", reply.required("reply_id").asText()); event.put("actor_id", reply.required("actor_id").asText());
        event.put("created_at", reply.required("created_at").asText()); schema.validate("Event", event); return event;
    }
    private long count(Map<String,Object> values) {
        Long count = mapper.replyCount(values); if (count == null || count < 0) mismatch(); return count;
    }
    private PermissionProtocol.GrantRow require(EnterpriseAccess.Admission admission, AnnotationReplyProtocol.Parent parent, String action) throws IOException {
        return authority.require(admission, new PermissionProtocol.Scope(parent.tenantId(), parent.projectId(), "DOCUMENT", parent.documentId()), action, PURPOSE);
    }
    private void clockCurrent(Proof proof, Map<String,Object> values) {
        if (!proof.authorizationClock().equals(mapper.authorizationClock(values))) denied(ErrorCode.SCOPE_STALE);
    }
    private Map<String,Object> values(ApplicationSessionIdentity.Verified subject, String annotationId) {
        var values = new HashMap<String,Object>(); values.put("userId", subject.user().user_id()); values.put("annotationId", annotationId); return values;
    }
    private static void context(EnterpriseAccess.Admission admission, String context) {
        if (!admission.context().context_revision().equals(context)) denied(ErrorCode.CONTEXT_CHANGED);
    }
    private record Proof(AnnotationReplyProtocol.Parent parent, String contextId, BusinessSessionProtocol.Context context,
            TenantWatermarks watermarks, String actor, String session, String authorizationClock, String documentRevision, String originalDocumentRevision, JsonNode snapshot) { }
    private static String id() { return UUID.randomUUID().toString(); }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static <T> T single(List<T> rows) { if (rows.size() != 1) mismatch(); return Objects.requireNonNull(rows.get(0)); }
    private static void one(int count) { if (count != 1) denied(ErrorCode.REVISION_CONFLICT); }
    private static void mismatch() { denied(ErrorCode.CONTENT_MISMATCH); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
}
