package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.AnnotationThreadMapper;
import com.ruoyi.medical.persistence.mapper.DocumentReadMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Actual fixed Selection objects and additive inline views. Does not implement frozen RV-04/BX-11 Record contracts. */
@Service
public final class AnnotationThreadService {
    private static final String PURPOSE = "DOCUMENT_REVIEW", EVENT = "annotation.created", OPERATION = "RV-04-THREAD-1";
    private final EnterpriseAccess access;
    private final AnnotationThreadMapper mapper;
    private final com.ruoyi.medical.persistence.mapper.AnnotationAnchorMapper anchors;
    private final DocumentReadMapper reads;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority resources;
    private final DocumentReadService documents;
    private final ArtifactSourceAuthority artifacts;
    private final ArtifactContentService content;
    private final SourceUploadTransport transport;
    private final DocumentUploadPolicy storage;
    private final AnnotationThreadSchema schema;
    private final PermissionSchema json;

    public AnnotationThreadService(EnterpriseAccess access, AnnotationThreadMapper mapper, DocumentReadMapper reads,
            PermissionMapper permissions, ResourceReadAuthority resources, DocumentReadService documents,
            ArtifactSourceAuthority artifacts, ArtifactContentService content, SourceUploadTransport transport,
            DocumentUploadPolicy storage, AnnotationThreadSchema schema, PermissionSchema json, com.ruoyi.medical.persistence.mapper.AnnotationAnchorMapper anchors) {
        this.access = access; this.mapper = mapper; this.reads = reads; this.permissions = permissions; this.resources = resources;
        this.documents = documents; this.artifacts = artifacts; this.content = content; this.transport = transport;
        this.storage = storage; this.schema = schema; this.json = json; this.anchors = anchors;
    }

    public JsonNode create(ApplicationSessionIdentity.Verified subject, String context, String key, String documentId, byte[] bytes) throws Exception {
        json.headers(context, key); json.requireId(documentId); var input = schema.create(bytes);
        String requestHash = schema.requestHash(documentId, input, context, subject.sessionId());
        var existing = transaction(subject, admission -> {
            requireContext(admission, context); var owner = single(permissions.resourceOwner("DOCUMENT", documentId));
            if (owner.projectId() == null) denied(ErrorCode.ACCESS_DENIED);
            resources.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), owner.projectId(), "DOCUMENT", documentId), "annotation.write", PURPOSE);
            var rows = mapper.operation(values(subject, documentId, input.documentRevision(), key, requestHash));
            if (rows.size() > 1) mismatch();
            if (rows.isEmpty()) return null;
            var operation = rows.get(0); requireOperation(admission, operation, key, documentId, requestHash); return operation;
        });
        if (existing != null && "COMPLETED".equals(existing.state())) return replay(subject, context, key, documentId, requestHash, input, existing);
        var fixed = documents.readFixed(subject, context, documentId, input.documentRevision());
        var selected = DocumentSelectionSemantics.verify(schema.shared(), input.documentRevision(), fixed.body(), input.selection());
        requireItemCapacity(input, selected);
        byte[] selectionBytes = selected.canonicalJson().getBytes(StandardCharsets.UTF_8);
        if (selectionBytes.length > maximumObjectBytes()) denied(ErrorCode.FILE_TOO_LARGE);
        var command = transaction(subject, admission -> start(admission, context, key, requestHash, fixed, input, selected));
        var writing = transaction(subject, admission -> {
            var operation = inProgress(admission, context, key, requestHash, fixed, input, selected); var saved = command(operation, fixed, input, selected);
            if (!saved.writing()) update(admission, key, requestHash, fixed, operation, withReference(saved, true, null));
            return saved;
        });
        // A durable write identity exists before I/O. Retry verifies/reuses the same immutable S3 object.
        var reference = transport.writeDocumentProjection(subject, fixed.header().tenantId(), writing.intent(), selectionBytes,
                writing.writing(), maximumObjectBytes(), () -> transaction(subject, admission -> {
                    inProgress(admission, context, key, requestHash, fixed, input, selected); return null;
                }));
        transaction(subject, admission -> {
            var operation = inProgress(admission, context, key, requestHash, fixed, input, selected);
            var saved = command(operation, fixed, input, selected);
            if (!saved.writing() || saved.reference() != null && !saved.reference().equals(reference)) mismatch();
            requireReference(saved.intent(), reference); update(admission, key, requestHash, fixed, operation, withReference(saved, true, reference)); return null;
        });
        return transaction(subject, admission -> finish(admission, context, key, requestHash, fixed, input, selected));
    }

    public JsonNode list(ApplicationSessionIdentity.Verified subject, String context, String documentId, String documentRevision, String status) throws Exception {
        json.requireId(documentId); json.requireId(documentRevision); PermissionSchema.revision(context); requireStatus(status);
        authorize(subject, context, documentId, "annotation.read");
        var fixed = documents.readFixed(subject, context, documentId, documentRevision);
        var captured = transaction(subject, admission -> {
            require(admission, context, fixed, "annotation.read"); var values = listValues(subject, fixed, status);
            long count = count(values); if (count > AnnotationThreadSchema.MAXIMUM_ANNOTATIONS) denied(ErrorCode.CONTEXT_LIMIT);
            var rows = mapper.annotations(values); if (count != rows.size()) mismatch();
            validateRows(rows, fixed, status); return new Listing(rows, clock(values));
        });
        var items = json.object().arrayNode(); int totalBytes = 0;
        for (var row : captured.rows()) {
            var selection = readSelection(subject, context, fixed, row, "annotation.read", captured.clock());
            var item = annotation(row, fixed, selection); totalBytes = Math.addExact(totalBytes, json.canonical(item).getBytes(StandardCharsets.UTF_8).length);
            if (totalBytes > AnnotationThreadSchema.MAXIMUM_LIST_BYTES) denied(ErrorCode.CONTEXT_LIMIT); items.add(item);
        }
        return transaction(subject, admission -> {
            require(admission, context, fixed, "annotation.read"); var values = listValues(subject, fixed, status);
            clockCurrent(captured.clock(), values);
            if (count(values) != captured.rows().size() || !captured.rows().equals(mapper.annotations(values))) denied(ErrorCode.SCOPE_STALE);
            var data = base(fixed.header(), fixed.header().revision().toString(), fixed.watermarks());
            if (status == null) data.putNull("status_filter"); else data.put("status_filter", status);
            data.put("total", Integer.toString(items.size())).put("complete", true).set("items", items);
            data.put("listing_sha256", schema.hash(data)); var response = schema.envelope("Listing", data, id(), fixed.header().revision().toString());
            clockCurrent(captured.clock(), values); return response;
        });
    }

    private AnnotationThreadProtocol.Command start(EnterpriseAccess.Admission admission, String context, String key, String hash,
            DocumentReadService.FixedRead fixed, InterfaceProtocol.AnnotationInput input, DocumentSelectionSemantics.Verified selected) throws Exception {
        requireNew(admission, context, fixed, input); var values = values(admission.subject(), fixed, key, hash);
        var rows = mapper.operation(values); if (rows.size() > 1) mismatch();
        if (!rows.isEmpty()) {
            var operation = rows.get(0); requireOperation(admission, operation, key, fixed.header().documentId(), hash);
            var saved = command(operation, fixed, input, selected); clockCurrent(saved.authorizationClock(), values); return saved;
        }
        if (documentCount(values) >= AnnotationThreadSchema.MAXIMUM_ANNOTATIONS || count(values) >= AnnotationThreadSchema.MAXIMUM_ANNOTATIONS) denied(ErrorCode.CONTEXT_LIMIT);
        String artifactId = id(), annotationId = id(); byte[] bytes = selected.canonicalJson().getBytes(StandardCharsets.UTF_8);
        var intent = new S3ObjectWriter.WriteIntent(id(), "documents/" + fixed.header().tenantId() + "/" + fixed.header().documentId()
                + "/" + fixed.header().documentRevision() + "/annotations/" + artifactId,
                selected.sha256(), Integer.toString(bytes.length), "application/json");
        var command = new AnnotationThreadProtocol.Command(annotationId, artifactId, id(), input.documentRevision(), input.expectedRevision(),
                fixed.header().bodySha256(), fixed.digest(), clock(values), selected.sha256(), intent, false, null);
        values.put("operationId", id()); values.put("commandJson", json.canonicalObject(command)); values.put("now", permissions.databaseTime());
        changed(mapper.startOperation(values)); var saved = single(mapper.operation(values));
        requireOperation(admission, saved, key, fixed.header().documentId(), hash);
        if (!command.equals(command(saved, fixed, input, selected))) mismatch(); return command;
    }

    private UploadOperationRow inProgress(EnterpriseAccess.Admission admission, String context, String key, String hash,
            DocumentReadService.FixedRead fixed, InterfaceProtocol.AnnotationInput input, DocumentSelectionSemantics.Verified selected) throws Exception {
        requireNew(admission, context, fixed, input); var values = values(admission.subject(), fixed, key, hash);
        var operation = single(mapper.operation(values)); requireOperation(admission, operation, key, fixed.header().documentId(), hash);
        clockCurrent(command(operation, fixed, input, selected).authorizationClock(), values); return operation;
    }

    private JsonNode finish(EnterpriseAccess.Admission admission, String context, String key, String hash,
            DocumentReadService.FixedRead fixed, InterfaceProtocol.AnnotationInput input, DocumentSelectionSemantics.Verified selected) throws Exception {
        var operation = inProgress(admission, context, key, hash, fixed, input, selected); var command = command(operation, fixed, input, selected);
        if (command.reference() == null || !command.writing()) mismatch(); requireReference(command.intent(), command.reference());
        var values = values(admission.subject(), fixed, key, hash); long before = count(values);
        if (documentCount(values) >= AnnotationThreadSchema.MAXIMUM_ANNOTATIONS || before >= AnnotationThreadSchema.MAXIMUM_ANNOTATIONS) denied(ErrorCode.CONTEXT_LIMIT);
        values.put("reviewLimit", AnnotationThreadSchema.MAXIMUM_INVALIDATIONS + 1);
        var reviews = mapper.confirmations(values); if (reviews.size() > AnnotationThreadSchema.MAXIMUM_INVALIDATIONS) denied(ErrorCode.CONTEXT_LIMIT);
        var reviewIds = new ArrayList<String>();
        for (var review : reviews) {
            json.requireId(review.reviewId());
            if (!input.documentRevision().equals(review.documentRevision()) || !"CONFIRM".equals(review.decision()) || !"DOCUMENT".equals(review.targetKey())
                    || !reviewIds.isEmpty() && review.reviewId().compareTo(reviewIds.get(reviewIds.size() - 1)) <= 0) mismatch();
            reviewIds.add(review.reviewId());
        }
        var now = Objects.requireNonNull(permissions.databaseTime()); String resulting = PermissionSchema.next(input.expectedRevision());
        String issues = PermissionSchema.next(fixed.watermarks().issueWatermark().toString());
        var bodyIntent = fixed.bodyIntent(); var dependencies = fixed.dependencies();
        values.put("now", now); values.put("annotationId", command.annotationId()); values.put("artifactId", command.artifactId());
        values.put("selectionJson", selected.canonicalJson()); values.put("selectionSha256", selected.sha256()); values.put("anchorNodeId", selected.anchorNodeId());
        values.put("content", input.content()); values.put("reference", command.reference()); values.put("storedObject", StoredObjectReferences.encode(command.reference()));
        values.put("accessSetId", dependencies.access_set_id()); values.put("dependencyHash", schema.hash(dependencies));
        values.put("artifactManifest", bodyIntent.artifact().artifactSchemaManifest()); values.put("interfacesHash", bodyIntent.artifact().schemaDocumentSha256());
        values.put("selectionSchema", schema.shared().schemaRef("interfaces", "Selection")); values.put("expectedRevision", input.expectedRevision());
        values.put("issueWatermark", fixed.watermarks().issueWatermark()); values.put("evidenceWatermark", fixed.watermarks().evidenceWatermark());
        changed(mapper.saveVersion(values)); changed(mapper.saveArtifact(values)); changed(mapper.saveBinding(values));
        for (var member : dependencies.members()) { values.put("upstreamId", member.upstreamVersionId()); changed(mapper.saveLineage(values)); }
        var registered = registration(admission, fixed, command.artifactId(), selected.sha256());
        if (!command.reference().equals(registered.registration().artifact().reference())) mismatch();
        changed(mapper.insertAnnotation(values));
        values.put("anchorId", command.annotationId()); values.put("previousAnchorId", null); values.put("resulting", "1"); values.put("reason", null);
        changed(anchors.insertAnchor(values)); changed(anchors.insertPointer(values));
        var originAnchor = single(anchors.anchor(values)); var originPointer = single(anchors.pointer(values));
        if (!originPointer.anchorId().equals(command.annotationId()) || !BigInteger.ONE.equals(originPointer.revision())
                || !originAnchor.anchorId().equals(command.annotationId()) || !originAnchor.annotationId().equals(command.annotationId())
                || !originAnchor.documentRevision().equals(input.documentRevision()) || !originAnchor.nodeId().equals(selected.anchorNodeId())
                || !originAnchor.selectionArtifactId().equals(command.artifactId()) || !originAnchor.selectionSha256().equals(selected.sha256())
                || !originAnchor.selectionObjectVersion().equals(command.reference().objectVersion()) || !schema.read(originAnchor.selectionJson()).equals(schema.read(selected.canonicalJson()))
                || originAnchor.previousAnchorId() != null || originAnchor.reason() != null || !BigInteger.ONE.equals(originAnchor.annotationRevision())
                || !originAnchor.actorId().equals(admission.subject().user().user_id()) || !originAnchor.createdBy().equals(originAnchor.actorId())
                || !now.equals(originAnchor.createdAt()) || !fixed.header().classification().equals(originAnchor.classification())
                || !"1.0.0".equals(originAnchor.schemaVersion()) || !schema.manifestSha256().equals(originAnchor.schemaManifest())) mismatch();
        changed(mapper.advanceHead(values)); changed(mapper.advanceIssues(values));
        for (String reviewId : reviewIds) { values.put("reviewId", reviewId); values.put("invalidationId", id()); changed(mapper.invalidateReview(values)); }
        var after = single(mapper.annotation(values));
        if (count(values) != before + 1 || !"OPEN".equals(after.status()) || !BigInteger.ONE.equals(after.revision()) || after.replyCount().signum() != 0
                || !input.content().equals(after.content()) || !now.equals(after.createdAt()) || !now.equals(after.updatedAt())
                || !admission.subject().user().user_id().equals(after.actorId())) mismatch();
        requireRow(after, fixed); var savedHeader = single(reads.header(values)); var savedMarks = single(reads.watermarks(values));
        if (!sameAdvancedHeader(fixed.header(), savedHeader, resulting)
                || !issues.equals(savedMarks.issueWatermark().toString()) || !fixed.watermarks().evidenceWatermark().equals(savedMarks.evidenceWatermark())) mismatch();
        verifyInvalidations(values, command.artifactId(), reviewIds);
        var item = annotation(after, fixed, schema.read(selected.canonicalJson()));
        var receipt = base(savedHeader, resulting, savedMarks); receipt.put("operation_id", operation.operationId()).put("previous_revision", input.expectedRevision());
        receipt.set("annotation", item); receipt.put("annotation_sha256", schema.hash(item)); receipt.set("invalidated_review_ids", json.tree(reviewIds));
        var response = schema.envelope("Receipt", receipt, command.requestId(), resulting);
        values.put("operationId", operation.operationId()); values.put("operationRevision", operation.revision()); values.put("response", json.canonical(response));
        changed(mapper.completeOperation(values));
        var event = event(receipt); var witness = grant(admission, fixed.header(), "annotation.write");
        var audit = json.object().put("schema_version", "1.0.0").put("operation_id", operation.operationId()).put("request_sha256", hash)
                .put("authority_grant_id", witness.grantId()).put("authority_revision", witness.revision().toString()); audit.set("after", event);
        changed(permissions.saveAudit(admission.subject().user().user_id(), fixed.header().projectId(), id(), EVENT, json.canonical(audit),
                command.requestId(), schema.manifestSha256(), fixed.header().classification()));
        changed(permissions.saveEvent(admission.subject().user().user_id(), id(), command.annotationId(), "1", EVENT, json.canonical(event)));
        values.put("requestId", command.requestId()); var completed = single(mapper.operation(values));
        requireOperation(admission, completed, key, fixed.header().documentId(), hash);
        if (!"COMPLETED".equals(completed.state()) || !response.equals(schema.receipt(completed.responseRef()))
                || !audit.equals(schema.read(single(mapper.audit(values)))) || !event.equals(schema.read(single(mapper.event(values))))) mismatch();
        // Own head/watermark changes are expected. Recheck timed permissions without comparing the old document digest.
        grant(admission, savedHeader, "annotation.write"); clockCurrent(command.authorizationClock(), values); return response;
    }

    private JsonNode replay(ApplicationSessionIdentity.Verified subject, String context, String key, String documentId, String hash,
            InterfaceProtocol.AnnotationInput input, UploadOperationRow initial) throws Exception {
        var response = schema.receipt(initial.responseRef()); var receipt = response.required("data"); var original = receipt.required("annotation");
        if (!initial.operationId().equals(receipt.required("operation_id").asText()) || !documentId.equals(receipt.required("document_id").asText()) || !input.documentRevision().equals(receipt.required("document_revision").asText())
                || !input.expectedRevision().equals(receipt.required("previous_revision").asText()) || !input.content().equals(original.required("content").asText())
                || !json.canonicalObject(input.selection()).equals(json.canonical(original.required("selection"))) || !subject.user().user_id().equals(original.required("actor_id").asText())
                || !"OPEN".equals(original.required("status").asText()) || !"1".equals(original.required("revision").asText())
                || !"0".equals(original.required("reply_count").asText())) mismatch();
        var fixed = documents.readFixed(subject, context, documentId, input.documentRevision());
        var captured = transaction(subject, admission -> {
            require(admission, context, fixed, "annotation.write"); var values = values(subject, fixed, key, hash);
            var operation = single(mapper.operation(values)); requireOperation(admission, operation, key, documentId, hash);
            if (!initial.equals(operation)) denied(ErrorCode.SCOPE_STALE);
            values.put("annotationId", original.required("annotation_id").asText()); var row = single(mapper.annotation(values)); requireRow(row, fixed);
            return new Replay(row, clock(values));
        });
        var selection = readSelection(subject, context, fixed, captured.row(), "annotation.write", captured.clock());
        var actual = annotation(captured.row(), fixed, selection);
        for (String field : List.of("annotation_id", "document_revision", "selection", "selection_artifact_id", "selection_sha256", "content", "actor_id", "created_at"))
            if (!original.required(field).equals(actual.required(field))) mismatch();
        return transaction(subject, admission -> {
            require(admission, context, fixed, "annotation.write"); var values = values(subject, fixed, key, hash);
            var operation = single(mapper.operation(values)); requireOperation(admission, operation, key, documentId, hash);
            if (!initial.equals(operation)) denied(ErrorCode.SCOPE_STALE);
            values.put("annotationId", captured.row().annotationId()); values.put("artifactId", captured.row().selectionArtifactId());
            values.put("requestId", response.required("request_id").asText());
            if (!captured.row().equals(single(mapper.annotation(values))) || fixed.header().revision().compareTo(PermissionSchema.revision(receipt.required("revision").asText())) < 0)
                denied(ErrorCode.SCOPE_STALE);
            var ids = new ArrayList<String>(); receipt.required("invalidated_review_ids").forEach(value -> ids.add(value.asText()));
            verifyInvalidations(values, captured.row().selectionArtifactId(), ids);
            var audit = schema.read(single(mapper.audit(values))); var event = event(receipt);
            if (!audit.required("operation_id").asText().equals(operation.operationId()) || !audit.required("request_sha256").asText().equals(hash)
                    || !audit.required("after").equals(event) || !event.equals(schema.read(single(mapper.event(values))))) mismatch();
            clockCurrent(captured.clock(), values); return response;
        });
    }

    JsonNode readSelection(ApplicationSessionIdentity.Verified subject, String context, DocumentReadService.FixedRead fixed,
            AnnotationThreadProtocol.Row row, String action, String clock) throws Exception {
        requireRow(row, fixed); var expected = DocumentSelectionSemantics.verify(schema.shared(), fixed.header().documentRevision(), fixed.body(), schema.selection(schema.read(row.selectionJson())));
        if (!row.selectionSha256().equals(expected.sha256()) || !row.nodeId().equals(expected.anchorNodeId())) mismatch();
        var intent = selectionIntent(fixed, row.selectionArtifactId());
        var bytes = transport.readDocument(subject, fixed.header().tenantId(), maximumObjectBytes(), () -> transaction(subject, admission -> {
            require(admission, context, fixed, action); clockCurrent(clock, values(subject, fixed, null, null));
            registration(admission, fixed, row.selectionArtifactId(), expected.sha256()); return null;
        }), (reader, lease) -> content.readVerifiedComplete(subject, intent, reader, lease, maximumObjectBytes(), id()).jsonPayload().canonicalBytes());
        if (!Arrays.equals(bytes, expected.canonicalJson().getBytes(StandardCharsets.UTF_8))) mismatch();
        return schema.readUtf8(bytes);
    }

    ArtifactSourceAuthority.VerifiedSources registration(EnterpriseAccess.Admission admission, DocumentReadService.FixedRead fixed,
            String artifactId, String selectionHash) throws Exception {
        var ownership = values(admission.subject(), fixed, null, null); ownership.put("artifactId", artifactId);
        if (!fixed.header().documentRevision().equals(single(mapper.artifactDocumentRevision(ownership)))) mismatch();
        var intent = selectionIntent(fixed, artifactId); var registered = artifacts.require(admission, artifactId, intent.artifact(), intent.sources(), intent.context(), intent.upstreamArtifacts());
        var artifact = registered.registration().artifact(); var binding = registered.registration().binding();
        if (!"SELECTION".equals(artifact.artifactKind()) || !selectionHash.equals(artifact.sha256()) || !fixed.header().classification().equals(artifact.classification())
                || !fixed.header().accessSetId().equals(artifact.accessSetId()) || !fixed.dependencies().equals(registered.registration().manifest())
                || !fixed.header().documentId().equals(binding.resourceId()) || !"DOCUMENT".equals(binding.resourceKind())) mismatch();
        return registered;
    }
    private ContentReadProtocol.ReadIntent selectionIntent(DocumentReadService.FixedRead fixed, String artifactId) {
        var body = fixed.bodyIntent(); var requirement = body.artifact();
        var selected = new ArtifactRegistryProtocol.ReadRequirement(requirement.scope(), requirement.action(), requirement.purpose(),
                schema.shared().schemaRef("interfaces", "Selection"), requirement.schemaDocumentSha256(), requirement.artifactSchemaManifest(), requirement.lineageLimits());
        return new ContentReadProtocol.ReadIntent(artifactId, body.contextRevision(), selected, body.sources(), body.context(), body.upstreamArtifacts());
    }

    private void requireItemCapacity(InterfaceProtocol.AnnotationInput input, DocumentSelectionSemantics.Verified selected) throws IOException {
        // Bound every future view, including the longest valid status/revision/reply count. 100 such
        // items fit comfortably inside the 8 MiB complete-list envelope; successful creates remain readable.
        String id = "00000000-0000-4000-8000-000000000000", maximum = "18446744073709551615";
        var value = json.object().put("annotation_id", id).put("document_revision", input.documentRevision()).put("revision", maximum)
                .put("selection_artifact_id", id).put("selection_sha256", selected.sha256()).put("content", input.content())
                .put("actor_id", id).put("created_at", "9999-12-31T23:59:59.999999Z").put("status", "REANCHOR_REQUIRED")
                .put("location_status", "ORIGINAL_VERSION").put("reply_count", maximum);
        value.set("selection", json.tree(selected.selection()));
        if (json.canonical(value).getBytes(StandardCharsets.UTF_8).length > AnnotationThreadSchema.MAXIMUM_ANNOTATION_BYTES)
            denied(ErrorCode.CONTEXT_LIMIT);
    }

    ObjectNode annotation(AnnotationThreadProtocol.Row row, DocumentReadService.FixedRead fixed, JsonNode selection) throws IOException {
        requireRow(row, fixed);
        var item = json.object().put("annotation_id", row.annotationId()).put("document_revision", row.documentRevision()).put("revision", row.revision().toString())
                .put("selection_artifact_id", row.selectionArtifactId()).put("selection_sha256", row.selectionSha256()).put("content", row.content())
                .put("actor_id", row.actorId()).put("created_at", time(row.createdAt())).put("status", row.status())
                .put("location_status", row.documentRevision().equals(fixed.header().currentDocumentRevision()) ? "CURRENT_VERSION" : "ORIGINAL_VERSION")
                .put("reply_count", row.replyCount().toString()); item.set("selection", selection); schema.annotation(item); return item;
    }
    private void requireRow(AnnotationThreadProtocol.Row row, DocumentReadService.FixedRead fixed) {
        if (!fixed.header().tenantId().equals(row.tenantId()) || !fixed.header().projectId().equals(row.projectId())
                || !fixed.header().documentRevision().equals(row.documentRevision()) || !row.actorId().equals(row.createdBy())
                || !fixed.header().classification().equals(row.classification()) || !"1.0.0".equals(row.schemaVersion())
                || !schema.manifestSha256().equals(row.schemaManifest()) || row.revision().signum() <= 0 || row.replyCount().signum() < 0
                || row.createdAt() == null || row.updatedAt() == null || row.updatedAt().isBefore(row.createdAt())) mismatch();
        for (String id : List.of(row.annotationId(), row.nodeId(), row.selectionArtifactId(), row.actorId())) json.requireId(id);
        PermissionSchema.revision(row.revision().toString()); PermissionSchema.revision(row.replyCount().toString()); requireStatus(row.status());
        AnnotationReplySchema.content(row.content()); WireValues.hash(row.selectionSha256());
    }
    private void validateRows(List<AnnotationThreadProtocol.Row> rows, DocumentReadService.FixedRead fixed, String status) {
        var ids = new HashSet<String>(); AnnotationThreadProtocol.Row previous = null;
        if (rows.size() > AnnotationThreadSchema.MAXIMUM_ANNOTATIONS) denied(ErrorCode.CONTEXT_LIMIT);
        for (var row : rows) {
            requireRow(row, fixed);
            if (!ids.add(row.annotationId()) || status != null && !status.equals(row.status()) || previous != null &&
                    (row.createdAt().isBefore(previous.createdAt()) || row.createdAt().equals(previous.createdAt()) && row.annotationId().compareTo(previous.annotationId()) <= 0)) mismatch();
            previous = row;
        }
    }
    private void verifyInvalidations(Map<String,Object> values, String artifactId, List<String> reviews) {
        var rows = mapper.invalidations(values); var ids = new HashSet<String>();
        if (rows.size() != reviews.size()) mismatch();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i); json.requireId(row.invalidationId());
            if (!ids.add(row.invalidationId()) || !reviews.get(i).equals(row.reviewId()) || !artifactId.equals(row.triggerVersion()) || !"ANNOTATION_OPENED".equals(row.reasonCode())) mismatch();
        }
    }
    private void requireNew(EnterpriseAccess.Admission admission, String context, DocumentReadService.FixedRead fixed, InterfaceProtocol.AnnotationInput input) throws Exception {
        require(admission, context, fixed, "annotation.write"); var header = fixed.header();
        if ("SIGNED".equals(header.status())) denied(ErrorCode.DOCUMENT_SIGNED);
        if (!"ACTIVE".equals(header.projectStatus())) denied(ErrorCode.ACTION_STALE);
        if (!input.documentRevision().equals(header.documentRevision()) || !header.documentRevision().equals(header.currentDocumentRevision())
                || !input.expectedRevision().equals(header.revision().toString())) denied(ErrorCode.REVISION_CONFLICT);
    }
    private void require(EnterpriseAccess.Admission admission, String context, DocumentReadService.FixedRead fixed, String action) throws Exception {
        requireContext(admission, context); grant(admission, fixed.header(), action); documents.requireCurrent(admission, fixed);
    }
    private PermissionProtocol.GrantRow grant(EnterpriseAccess.Admission admission, DocumentReadProtocol.Header header, String action) throws IOException {
        if (!admission.context().tenant_id().equals(header.tenantId())) denied(ErrorCode.ACCESS_DENIED);
        return resources.require(admission, new PermissionProtocol.Scope(header.tenantId(), header.projectId(), "DOCUMENT", header.documentId()), action, PURPOSE);
    }
    private void authorize(ApplicationSessionIdentity.Verified subject, String context, String documentId, String action) {
        transaction(subject, admission -> { requireContext(admission, context); var owner = single(permissions.resourceOwner("DOCUMENT", documentId));
            if (owner.projectId() == null) denied(ErrorCode.ACCESS_DENIED);
            resources.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), owner.projectId(), "DOCUMENT", documentId), action, PURPOSE); return null; });
    }
    private AnnotationThreadProtocol.Command command(UploadOperationRow operation, DocumentReadService.FixedRead fixed,
            InterfaceProtocol.AnnotationInput input, DocumentSelectionSemantics.Verified selected) throws IOException {
        if (!"PROCESSING".equals(operation.state()) || operation.httpStatus() != null) denied(ErrorCode.REVISION_CONFLICT);
        var command = schema.command(operation.responseRef());
        for (String id : List.of(command.annotationId(), command.artifactId(), command.requestId())) json.requireId(id);
        if (!input.documentRevision().equals(command.documentRevision()) || !input.expectedRevision().equals(command.expectedRevision())
                || !fixed.header().bodySha256().equals(command.bodySha256()) || !fixed.digest().equals(command.authorizationSha256())) denied(ErrorCode.SCOPE_STALE);
        if (!selected.sha256().equals(command.selectionSha256()) || !selected.sha256().equals(command.intent().sha256())
                || !Integer.toString(selected.canonicalJson().getBytes(StandardCharsets.UTF_8).length).equals(command.intent().sizeBytes())
                || !"application/json".equals(command.intent().mediaType()) || !command.intent().objectKey().equals("documents/" + fixed.header().tenantId()
                    + "/" + fixed.header().documentId() + "/" + fixed.header().documentRevision() + "/annotations/" + command.artifactId())
                || command.reference() != null && !command.writing()) mismatch();
        if (command.reference() != null) requireReference(command.intent(), command.reference()); return command;
    }
    private void update(EnterpriseAccess.Admission admission, String key, String hash, DocumentReadService.FixedRead fixed,
            UploadOperationRow operation, AnnotationThreadProtocol.Command command) throws IOException {
        var values = values(admission.subject(), fixed, key, hash); values.put("operationId", operation.operationId()); values.put("operationRevision", operation.revision());
        values.put("now", permissions.databaseTime()); values.put("commandJson", json.canonicalObject(command)); changed(mapper.updateCommand(values));
        var after = single(mapper.operation(values)); requireOperation(admission, after, key, fixed.header().documentId(), hash);
        if (!command.equals(schema.command(after.responseRef()))) mismatch();
    }
    private void requireOperation(EnterpriseAccess.Admission admission, UploadOperationRow row, String key, String documentId, String hash) {
        if (!hash.equals(row.requestSha256())) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!admission.context().tenant_id().equals(row.tenantId()) || !admission.subject().user().user_id().equals(row.subjectId())
                || !row.subjectId().equals(row.createdBy()) || !key.equals(row.idempotencyKey()) || !documentId.equals(row.targetId())
                || !OPERATION.equals(row.operationName()) || !"1.0.0".equals(row.schemaVersion()) || !schema.manifestSha256().equals(row.schemaManifestSha256())
                || !Set.of("PROCESSING", "COMPLETED").contains(row.state()) || row.revision().signum() <= 0 || row.errorCode() != null
                || row.createdAt() == null || row.updatedAt() == null || row.updatedAt().isBefore(row.createdAt())
                || row.dedupExpiresAt() != null || row.dedupPolicyRevision() != null
                || "COMPLETED".equals(row.state()) && !Integer.valueOf(201).equals(row.httpStatus())) mismatch();
    }
    private Map<String,Object> values(ApplicationSessionIdentity.Verified subject, DocumentReadService.FixedRead fixed, String key, String hash) {
        var values = values(subject, fixed.header().documentId(), fixed.header().documentRevision(), key, hash);
        values.put("projectId", fixed.header().projectId()); values.put("classification", fixed.header().classification());
        values.put("manifest", schema.manifestSha256()); return values;
    }
    private Map<String,Object> values(ApplicationSessionIdentity.Verified subject, String documentId, String revision, String key, String hash) {
        var values = new HashMap<String,Object>(); values.put("userId", subject.user().user_id()); values.put("documentId", documentId);
        values.put("documentRevision", revision); values.put("key", key); values.put("requestHash", hash); values.put("status", null); return values;
    }
    private Map<String,Object> listValues(ApplicationSessionIdentity.Verified subject, DocumentReadService.FixedRead fixed, String status) {
        var values = values(subject, fixed, null, null); values.put("status", status); values.put("fetchLimit", AnnotationThreadSchema.MAXIMUM_ANNOTATIONS + 1); return values;
    }
    private ObjectNode base(DocumentReadProtocol.Header header, String revision, DocumentReadProtocol.Watermarks marks) {
        return json.object().put("schema_version", "1.0.0").put("document_id", header.documentId()).put("document_revision", header.documentRevision())
                .put("revision", revision).put("issue_watermark", marks.issueWatermark().toString()).put("evidence_watermark", marks.evidenceWatermark().toString());
    }
    private ObjectNode event(JsonNode receipt) {
        var value = ((ObjectNode)receipt).deepCopy(); var annotation = value.remove("annotation");
        for (String name : List.of("annotation_id", "selection_artifact_id", "selection_sha256", "actor_id", "created_at")) value.set(name, annotation.required(name));
        value.set("annotation_revision", annotation.required("revision")); schema.validate("Event", value); return value;
    }
    static boolean sameAdvancedHeader(DocumentReadProtocol.Header before, DocumentReadProtocol.Header after, String revision) {
        return new DocumentReadProtocol.Header(before.tenantId(), before.projectId(), before.documentId(), before.documentRevision(), before.currentDocumentRevision(),
                new BigInteger(revision), before.status(), before.projectStatus(), before.taskId(), before.taskRevision(), before.snapshotId(), before.profileRevision(),
                before.bodyArtifactId(), before.bodySha256(), before.objectKey(), before.objectVersion(), before.bodySchemaRevision(), before.planRevision(),
                before.templateVersion(), before.accessSetId(), before.issueWatermark(), before.contentSha256(), before.createdBy(), before.createdAt(), before.classification()).equals(after);
    }
    private static AnnotationThreadProtocol.Command withReference(AnnotationThreadProtocol.Command c, boolean writing, InterfaceProtocol.ObjectRef reference) {
        return new AnnotationThreadProtocol.Command(c.annotationId(), c.artifactId(), c.requestId(), c.documentRevision(), c.expectedRevision(), c.bodySha256(),
                c.authorizationSha256(), c.authorizationClock(), c.selectionSha256(), c.intent(), writing, reference);
    }
    private static void requireReference(S3ObjectWriter.WriteIntent intent, InterfaceProtocol.ObjectRef reference) {
        if (reference == null || !intent.objectKey().equals(reference.objectKey()) || !intent.sha256().equals(reference.sha256())
                || !intent.sizeBytes().equals(reference.sizeBytes()) || !intent.mediaType().equals(reference.mediaType())
                || reference.objectVersion() == null || reference.objectVersion().isBlank() || "null".equals(reference.objectVersion())) mismatch();
    }
    private int maximumObjectBytes() { storage.requireConfigured(); return Math.min(AnnotationThreadSchema.MAXIMUM_REQUEST_BYTES, storage.maximumBytes().intValueExact()); }
    private long documentCount(Map<String,Object> values) { Long value = anchors.documentCount(values); if (value == null || value < 0) mismatch(); return value; }
    private long count(Map<String,Object> values) { Long value = mapper.annotationCount(values); if (value == null || value < 0) mismatch(); return value; }
    private String clock(Map<String,Object> values) { return Objects.requireNonNull(mapper.authorizationClock(values)); }
    private void clockCurrent(String expected, Map<String,Object> values) { if (!expected.equals(clock(values))) denied(ErrorCode.SCOPE_STALE); }
    private static void requireContext(EnterpriseAccess.Admission admission, String context) { if (!context.equals(admission.context().context_revision())) denied(ErrorCode.CONTEXT_CHANGED); }
    private static void requireStatus(String status) { if (status != null && !Set.of("OPEN", "RESOLVED", "RETURNED", "REANCHOR_REQUIRED").contains(status)) denied(ErrorCode.SCHEMA_INVALID); }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static <T> T single(List<T> values) { if (values.size() != 1) denied(ErrorCode.ACCESS_DENIED); return Objects.requireNonNull(values.get(0)); }
    private static void changed(int rows) { if (rows != 1) denied(ErrorCode.REVISION_CONFLICT); }
    private static void mismatch() { denied(ErrorCode.CONTENT_MISMATCH); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    private <T> T transaction(ApplicationSessionIdentity.Verified subject, Checked<T> work) { return access.execute(subject, admission -> ThrowingSupplier.of(() -> work.apply(admission)).get()); }
    @FunctionalInterface private interface Checked<T> { T apply(EnterpriseAccess.Admission admission) throws Exception; }
    private record Listing(List<AnnotationThreadProtocol.Row> rows, String clock) { Listing { rows = List.copyOf(rows); } }
    private record Replay(AnnotationThreadProtocol.Row row, String clock) { }
}
