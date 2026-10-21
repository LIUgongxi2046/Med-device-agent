package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.ExportMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class ExportService {
    private final ExportExecutionPolicy executionPolicy;
    private final ExportLayoutService layouts;
    private final org.springframework.beans.factory.ObjectProvider<DocumentSigningService> signing;
    private final EnterpriseAccess access;
    private final ExportMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority resources;
    private final ArtifactRegistryMapper artifacts;
    private final SourceUploadTransport transport;
    private final DocumentUploadPolicy capacity;
    private final ApplicationSessionIdentity identities;
    private final ExportSchema schema;
    private final DocumentReadService documents;

    public ExportService(EnterpriseAccess access, ExportMapper mapper, PermissionMapper permissions,
            ResourceReadAuthority resources, ArtifactRegistryMapper artifacts, SourceUploadTransport transport,
            DocumentUploadPolicy capacity, ApplicationSessionIdentity identities, ExportSchema schema, ExportExecutionPolicy executionPolicy,
            ExportLayoutService layouts, org.springframework.beans.factory.ObjectProvider<DocumentSigningService> signing, DocumentReadService documents) {
        this.documents = documents;
        this.access = access; this.mapper = mapper; this.permissions = permissions; this.resources = resources;
        this.artifacts = artifacts; this.transport = transport; this.executionPolicy = executionPolicy; this.layouts = layouts; this.signing = signing; this.capacity = capacity; this.identities = identities; this.schema = schema;
    }

    public JsonNode create(ApplicationSessionIdentity.Verified subject, String context, String key,
            String documentId, ExportProtocol.CreateInput input) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(documentId); validateFormats(input);
            var values = values(admission); values.put("documentId", documentId); values.put("documentRevision", input.document_revision());
            values.put("purpose", input.purpose()); values.put("key", key); values.put("operationName", "EX-01");
            values.put("requestHash", schema.requestHash("/api/documents/" + documentId + "/exports", input, context, subject.sessionId()));
            var document = single(mapper.document(values)); requireDocument(admission, document, documentId, input.purpose(), "artifact.export");
            if (!input.template_version().equals(document.templateVersion())
                    || !input.format_profile_revision().equals(document.formatProfileRevision()) || !"ACTIVE".equals(document.projectStatus())) stale();
            values.put("projectId", document.projectId());
            var prior = mapper.operation(values);
            if (prior.size() > 1) mismatch();
            if (!prior.isEmpty()) return replay(single(prior), values.get("requestHash").toString(), 202);
            var profile = schema.read(document.formatProfile()); requireProfile(profile, document, input);
            requireVersion(values, document.formatProfileRevision(), document.projectId(), profile.required("format_revision").required("content_sha256").textValue());
            requireProfileArtifacts(admission, profile, document);
            layouts.consume(admission, document, input.channel_profile_revision());
            var reviews = schema.object().arrayNode();
            for (var row : mapper.reviewSnapshot(values)) reviews.add(schema.read(row));
            String reviewHash = DigestUtils.sha256Hex(schema.canonical(reviews));
            if (!reviewHash.equals(input.review_manifest_sha256())) mismatch();
            ExportProtocol.SignatureRow signature = null;
            if ("FORMAL_BODY".equals(input.purpose())) {
                if (reviews.isEmpty() || !reviews.findValuesAsText("decision").stream().allMatch("CONFIRM"::equals)) stale();
                var signatures = mapper.signature(values); if (signatures.size() != 1) stale(); signature = signatures.get(0);
                requireSigned(admission, document, signature.signatureId());
                if (!admission.watermarks().authEpoch().equals(signature.authEpoch())
                        || !admission.watermarks().governanceEpoch().equals(signature.governanceEpoch())) stale();
            }
            String exportId = id(), operationId = id(); LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            var itemIds = new ArrayList<String>(); input.formats().forEach(format -> itemIds.add(id()));
            var manifest = schema.object(); manifest.put("schema_version", "1.0.0"); manifest.put("export_id", exportId);
            manifest.put("document_id", documentId); manifest.put("document_revision", input.document_revision());
            manifest.put("body_sha256", document.bodySha256()); manifest.put("purpose", input.purpose());
            manifest.put("template_version", input.template_version()); manifest.put("format_profile_revision", document.formatProfileRevision());
            if (input.channel_profile_revision() == null) manifest.putNull("channel_profile_revision");
            else manifest.put("channel_profile_revision", input.channel_profile_revision());
            manifest.put("review_manifest_sha256", reviewHash); manifest.set("review_snapshot", reviews);
            if (signature == null) manifest.putNull("signature_id"); else manifest.put("signature_id", signature.signatureId());
            var requested = manifest.putArray("items");
            for (int index = 0; index < input.formats().size(); index++) {
                var item = requested.addObject(); item.put("export_item_id", itemIds.get(index));
                item.put("format", input.formats().get(index)); item.put("attempt_no", "1");
            }
            String canonicalManifest = schema.canonical(manifest), manifestHash = DigestUtils.sha256Hex(canonicalManifest);
            String fontHash = DigestUtils.sha256Hex(schema.canonical(profile.required("font_artifacts")));
            values.put("exportId", exportId); values.put("operationId", operationId); values.put("targetId", exportId);
            values.put("projectId", document.projectId()); values.put("documentRevision", input.document_revision());
            values.put("templateVersion", input.template_version()); values.put("dependencyAccessSetId", document.dependencyAccessSetId());
            values.put("reviewManifestHash", reviewHash); values.put("formatProfileRevision", document.formatProfileRevision());
            values.put("channelProfileRevision", input.channel_profile_revision()); values.put("signatureId", signature == null ? null : signature.signatureId());
            values.put("manifest", canonicalManifest); values.put("manifestHash", manifestHash); values.put("schemaManifest", schema.manifestSha256());
            values.put("classification", document.classification()); values.put("now", now);
            values.put("converterRevision", executionPolicy.converterRevision()); values.put("fontManifestHash", fontHash);
            var view = view(exportId, documentId, input, document.formatProfileRevision(), manifestHash, now, itemIds);
            var receipt = receipt(operationId, view, subject.user().user_id(), now);
            var response = schema.envelope("Receipt", receipt, id(), "1"); values.put("response", schema.canonical(response));
            one(mapper.saveOperation(values)); one(mapper.saveVersion(values)); one(mapper.saveManifest(values));
            for (int index = 0; index < input.formats().size(); index++) {
                values.put("exportItemId", itemIds.get(index)); values.put("format", input.formats().get(index)); values.put("attemptNo", 1);
                one(mapper.saveItem(values));
            }
            var audit = schema.object(); audit.put("operation_id", operationId); audit.set("export", view); audit.set("request", schema.tree(input));
            one(permissions.saveAudit(subject.user().user_id(), document.projectId(), id(), "export.requested",
                    schema.canonical(audit), response.required("request_id").textValue(), schema.manifestSha256(), document.classification()));
            var event = schema.object(); event.put("export_id", exportId); event.put("document_revision", input.document_revision());
            event.put("status", "QUEUED"); event.put("operation_id", operationId);
            one(permissions.saveEvent(subject.user().user_id(), id(), exportId, "1", "export.updated", schema.canonical(event)));
            var stored = single(mapper.manifest(values));
            if (!manifestHash.equals(stored.manifestSha256()) || mapper.items(values).size() != input.formats().size()) mismatch();
            return response;
        }).get());
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String exportId) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(exportId); var values = values(admission); values.put("exportId", exportId);
            var manifest = single(mapper.manifest(values)); requireExport(admission, manifest, "export.read");
            var items = mapper.items(values); if (items.isEmpty()) mismatch();
            var view = view(admission, manifest, items);
            return schema.envelope("View", view, id(), manifest.revision().toString());
        }).get());
    }

    public JsonNode history(ApplicationSessionIdentity.Verified subject, String context, String documentId,
            String documentRevision, int page, int size) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(documentId); WireValues.uuid(documentRevision);
            var values = values(admission); values.put("documentId", documentId); values.put("documentRevision", documentRevision);
            var document = single(mapper.document(values)); requireDocument(admission, document, documentId, "EXPORT_HISTORY", "export.read");
            values.put("limit", size); values.put("offset", Math.multiplyExact(page - 1, size));
            long total = single(mapper.historyCount(values)); var entries = schema.object().arrayNode();
            for (var manifest : mapper.history(values)) {
                requireExport(admission, manifest, "export.read"); values.put("exportId", manifest.exportId());
                var items = mapper.items(values); if (items.isEmpty()) mismatch(); entries.add(view(admission, manifest, items));
            }
            var result = schema.object(); result.put("schema_version", "1.0.0"); result.put("document_id", documentId);
            result.put("document_revision", documentRevision); result.put("page", Integer.toString(page));
            result.put("size", Integer.toString(size)); result.put("total", Long.toString(total));
            result.put("has_more", Math.multiplyExact((long) page, size) < total); result.set("items", entries);
            schema.verify("History", result); return schema.envelope("History", result, id(), null);
        }).get());
    }

    public JsonNode basis(ApplicationSessionIdentity.Verified subject, String context, String documentId, String documentRevision) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(documentId); WireValues.uuid(documentRevision);
            var values = values(admission); values.put("documentId", documentId); values.put("documentRevision", documentRevision);
            values.put("purpose", "FORMAL_BODY"); var document = single(mapper.document(values));
            values.put("projectId", document.projectId());
            requireDocument(admission, document, documentId, "EXPORT_PREPARATION", "artifact.export");
            var profile = schema.read(document.formatProfile());
            var reviews = schema.object().arrayNode();
            for (var row : mapper.reviewSnapshot(values)) reviews.add(schema.read(row));
            String reviewHash = DigestUtils.sha256Hex(schema.canonical(reviews));
            boolean returned = reviews.findValuesAsText("decision").stream().anyMatch("RETURN"::equals);
            String reviewStatus = reviews.isEmpty() ? "UNREVIEWED" : returned ? "RETURNED" : "CONFIRMED";
            var signatures = mapper.signature(values); boolean signed = signatures.size() == 1;
            if (signed) {
                try { requireSigned(admission, document, signatures.get(0).signatureId()); }
                catch (TenantAccessException failure) { signed = false; }
            }
            var result = schema.object(); result.put("schema_version", "1.0.0"); result.put("document_id", documentId);
            result.put("document_revision", documentRevision); result.put("template_version", document.templateVersion());
            result.put("format_profile_revision", document.formatProfileRevision()); result.put("review_manifest_sha256", reviewHash);
            result.put("review_status", reviewStatus); result.put("formal_signature_ready", signed);
            var formats = result.putArray("allowed_formats");
            for (var format : profile.required("output_types")) formats.add(format.textValue());
            formats.add("MARKDOWN"); schema.verify("Basis", result);
            return schema.envelope("Basis", result, id(), null);
        }).get());
    }

    public JsonNode retry(ApplicationSessionIdentity.Verified subject, String context, String key,
            String exportItemId, ExportProtocol.RetryInput input) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(exportItemId); var values = values(admission);
            values.put("exportItemId", exportItemId); values.put("key", key); values.put("operationName", "EX-04");
            values.put("requestHash", schema.requestHash("/api/export-items/" + exportItemId + "/attempts", input, context, subject.sessionId()));
            var original = single(mapper.itemForUpdate(values)); values.put("exportId", original.exportId());
            var manifest = single(mapper.manifest(values)); requireExport(admission, manifest, "artifact.export");
            var prior = mapper.operation(values); if (prior.size() > 1) mismatch();
            if (!prior.isEmpty()) return replay(single(prior), values.get("requestHash").toString(), 202);
            if (!input.expected_revision().equals(original.revision().toString())
                    || !List.of("FAILED", "CANCELLED").contains(original.status())) stale();
            var allItems = mapper.items(values);
            if (allItems.stream().anyMatch(row -> row.format().equals(original.format())
                    && ("SUCCEEDED".equals(row.status()) || row.attemptNo().compareTo(original.attemptNo()) > 0))) stale();
            BigInteger attempt = allItems.stream().filter(row -> row.format().equals(original.format())).map(ExportProtocol.ItemRow::attemptNo)
                    .max(Comparator.naturalOrder()).orElseThrow().add(BigInteger.ONE);
            String newItemId = id(), operationId = id(); LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            values.put("operationId", operationId); values.put("targetId", newItemId); values.put("projectId", manifest.projectId());
            values.put("exportItemId", newItemId); values.put("format", original.format()); values.put("attemptNo", attempt);
            values.put("converterRevision", original.converterRevision()); values.put("fontManifestHash", original.fontManifestSha256());
            values.put("schemaManifest", schema.manifestSha256()); values.put("classification", manifest.classification()); values.put("now", now);
            values.put("manifestRevision", manifest.revision());
            var item = itemView(newItemId, original.format(), attempt, "QUEUED", null, null, null, false, BigInteger.ONE, now, now);
            var currentItems = schema.object().arrayNode();
            for (var row : allItems) currentItems.add(itemView(row, false)); currentItems.add(item);
            var view = view(manifest, currentItems, "QUEUED", manifest.revision().add(BigInteger.ONE), now);
            var receipt = receipt(operationId, view, subject.user().user_id(), now);
            var response = schema.envelope("Receipt", receipt, id(), view.required("revision").textValue());
            values.put("response", schema.canonical(response)); one(mapper.saveOperation(values)); one(mapper.saveItem(values));
            one(mapper.updateManifestForRetry(values));
            var audit = schema.object(); audit.put("operation_id", operationId); audit.put("retried_item_id", exportItemId);
            audit.put("new_item_id", newItemId); audit.put("reason", input.reason()); audit.set("export", view);
            one(permissions.saveAudit(subject.user().user_id(), manifest.projectId(), id(), "export.retry_requested",
                    schema.canonical(audit), response.required("request_id").textValue(), schema.manifestSha256(), manifest.classification()));
            var event = schema.object(); event.put("export_id", manifest.exportId()); event.put("export_item_id", newItemId);
            event.put("status", "QUEUED"); event.put("operation_id", operationId);
            one(permissions.saveEvent(subject.user().user_id(), id(), manifest.exportId(), view.required("revision").textValue(),
                    "export.updated", schema.canonical(event)));
            return response;
        }).get());
    }

    public ExportProtocol.Download content(ApplicationSessionIdentity.Verified subject, String context,
            String exportItemId, String range) throws Exception {
        return content(subject, context, exportItemId, range, false);
    }

    public ExportProtocol.Download content(ApplicationSessionIdentity.Verified subject, String context, String exportItemId, String range, boolean review) throws Exception {
        var initial = authorizeDownload(subject, context, exportItemId, review);
        var fixed = documents.readFixed(subject, context, initial.item().documentId(), initial.item().documentRevision());
        long size = initial.artifact().sizeBytes().longValueExact(); long[] selected = range(range, size);
        var bytes = transport.readDocument(subject, initial.item().tenantId(), Math.toIntExact(size),
                () -> { documents.requireCurrent(subject, fixed); requireDownload(subject, context, exportItemId, initial.authorization(), review); }, (reader, admission) -> {
                    try (var verified = reader.readVerified(initial.artifact().reference(), admission)) {
                        byte[] value = verified.chunk(selected[0], Math.toIntExact(selected[1] - selected[0]));
                        admission.requireCurrent(); if (!initial.artifact().sha256().equals(verified.fullSha256())) mismatch(); return value;
                    }
                });
        documents.requireCurrent(subject, fixed);
        requireDownload(subject, context, exportItemId, initial.authorization(), review);
        String extension = initial.item().format().toLowerCase(java.util.Locale.ROOT);
        return new ExportProtocol.Download(bytes, initial.artifact().mediaType(), exportItemId + "." + extension,
                selected[0], selected[1], size, initial.artifact().sha256(), initial.artifact().artifactId(), exportItemId);
    }

    private DownloadState authorizeDownload(ApplicationSessionIdentity.Verified subject, String context, String exportItemId, boolean review) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var values = values(admission); values.put("exportItemId", exportItemId);
            var item = single(mapper.itemForUpdate(values)); values.put("exportId", item.exportId()); var manifest = single(mapper.manifest(values));
            requireExport(admission, manifest, "export.read");
            if (!"SUCCEEDED".equals(item.status()) || item.artifactId() == null || item.validationArtifactId() == null) stale();
            if (review) resources.require(admission, new PermissionProtocol.Scope(item.tenantId(), item.projectId(), "DOCUMENT", item.documentId()), "review.decide", "EXPORT_LAYOUT");
            else if (!layouts.releasable(admission, manifest, item)) throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
            var artifactGrant = resources.require(admission, new PermissionProtocol.Scope(item.tenantId(), item.projectId(), "ARTIFACT", item.artifactId()),
                    "artifact.read", "EXPORT_DOWNLOAD");
            resources.require(admission, new PermissionProtocol.Scope(item.tenantId(), item.projectId(), "ARTIFACT", item.validationArtifactId()),
                    "artifact.read", "EXPORT_DOWNLOAD");
            var artifact = single(artifacts.artifact(item.artifactId()));
            if (!item.projectId().equals(artifact.projectId()) || !item.documentRevision().equals(manifest.documentRevision())) mismatch();
            String authorization = DigestUtils.sha256Hex(schema.canonical(schema.tree(Map.of(
                    "context_revision", admission.context().context_revision(), "auth_epoch", admission.watermarks().authEpoch().toString(),
                    "governance_epoch", admission.watermarks().governanceEpoch().toString(), "item_revision", item.revision().toString(),
                    "artifact_id", artifact.artifactId(), "artifact_sha256", artifact.sha256(), "grant_id", artifactGrant.grantId(),
                    "grant_revision", artifactGrant.revision().toString()))));
            return new DownloadState(item, artifact, authorization);
        }).get());
    }
    private void requireDownload(ApplicationSessionIdentity.Verified subject, String context, String id, String expected, boolean review) {
        if (!expected.equals(authorizeDownload(subject, context, id, review).authorization())) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
    }

    private JsonNode view(EnterpriseAccess.Admission admission, ExportProtocol.ManifestRow manifest, List<ExportProtocol.ItemRow> items) throws Exception {
        var array = schema.object().arrayNode();
        for (var item : items) {
            boolean downloadable = false;
            if ("SUCCEEDED".equals(item.status()) && item.artifactId() != null && item.validationArtifactId() != null) {
                resources.require(admission, new PermissionProtocol.Scope(item.tenantId(), item.projectId(), "ARTIFACT", item.artifactId()), "artifact.read", "EXPORT_DOWNLOAD");
                resources.require(admission, new PermissionProtocol.Scope(item.tenantId(), item.projectId(), "ARTIFACT", item.validationArtifactId()), "artifact.read", "EXPORT_DOWNLOAD");
                downloadable = layouts.releasable(admission, manifest, item);
            }
            var rendered = (com.fasterxml.jackson.databind.node.ObjectNode) itemView(item, downloadable);
            var verification = layouts.latest(admission, item.exportItemId());
            if (verification != null) rendered.set("layout_verification", verification);
            array.add(rendered);
        }
        return view(manifest, array, manifest.status(), manifest.revision(), manifest.updatedAt());
    }
    private JsonNode view(ExportProtocol.ManifestRow manifest, JsonNode items, String status, BigInteger revision, LocalDateTime updated) {
        var view = schema.object(); view.put("schema_version", "1.0.0"); view.put("export_id", manifest.exportId());
        view.put("document_id", manifest.documentId()); view.put("document_revision", manifest.documentRevision());
        view.put("purpose", manifest.purpose()); view.put("status", status); view.put("template_version", manifest.templateVersion());
        view.put("review_manifest_sha256", manifest.reviewManifestSha256()); view.put("format_profile_revision", manifest.formatProfileRevision());
        if (manifest.channelProfileRevision() == null) view.putNull("channel_profile_revision"); else view.put("channel_profile_revision", manifest.channelProfileRevision());
        view.put("manifest_sha256", manifest.manifestSha256()); view.put("revision", revision.toString()); view.set("items", items);
        view.put("created_at", time(manifest.createdAt())); view.put("updated_at", time(updated)); schema.verify("View", view); return view;
    }
    private JsonNode view(String exportId, String documentId, ExportProtocol.CreateInput input, String formatRevision,
            String manifestHash, LocalDateTime now, List<String> itemIds) {
        var items = schema.object().arrayNode();
        for (int index = 0; index < itemIds.size(); index++)
            items.add(itemView(itemIds.get(index), input.formats().get(index), BigInteger.ONE, "QUEUED", null, null, null, false, BigInteger.ONE, now, now));
        var view = schema.object(); view.put("schema_version", "1.0.0"); view.put("export_id", exportId);
        view.put("document_id", documentId); view.put("document_revision", input.document_revision()); view.put("purpose", input.purpose());
        view.put("status", "QUEUED"); view.put("template_version", input.template_version());
        view.put("review_manifest_sha256", input.review_manifest_sha256()); view.put("format_profile_revision", formatRevision);
        if (input.channel_profile_revision() == null) view.putNull("channel_profile_revision"); else view.put("channel_profile_revision", input.channel_profile_revision());
        view.put("manifest_sha256", manifestHash); view.put("revision", "1"); view.set("items", items);
        view.put("created_at", time(now)); view.put("updated_at", time(now)); schema.verify("View", view); return view;
    }
    private JsonNode itemView(ExportProtocol.ItemRow item, boolean downloadable) throws java.io.IOException {
        return itemView(item.exportItemId(), item.format(), item.attemptNo(), item.status(), item.artifactId(),
                item.validationArtifactId(), item.validationRef() == null ? null : schema.read(item.validationRef()),
                downloadable, item.revision(), item.createdAt(), item.updatedAt());
    }
    private JsonNode itemView(String id, String format, BigInteger attempt, String status, String artifactId,
            String validationId, JsonNode validation, boolean downloadable, BigInteger revision, LocalDateTime created, LocalDateTime updated) {
        var item = schema.object(); item.put("export_item_id", id); item.put("format", format); item.put("attempt_no", attempt.toString());
        item.put("status", status); if (artifactId == null) item.putNull("artifact_id"); else item.put("artifact_id", artifactId);
        if (validationId == null) item.putNull("validation_artifact_id"); else item.put("validation_artifact_id", validationId);
        if (validation == null) item.putNull("validation"); else item.set("validation", validation);
        item.put("downloadable", downloadable); item.put("revision", revision.toString()); item.put("created_at", time(created)); item.put("updated_at", time(updated));
        schema.verify("Item", item); return item;
    }
    private JsonNode receipt(String operationId, JsonNode view, String actor, LocalDateTime now) {
        var receipt = schema.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
        receipt.set("export", view); receipt.put("actor_id", actor); receipt.put("accepted_at", time(now)); return receipt;
    }
    private JsonNode replay(SelectionOperationRow operation, String hash, int status) throws Exception {
        if (!hash.equals(operation.requestSha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!"COMPLETED".equals(operation.state()) || !Objects.equals(status, operation.httpStatus())
                || !"1.0.0".equals(operation.schemaVersion()) || !schema.manifestSha256().equals(operation.schemaManifestSha256())) mismatch();
        return schema.receipt(operation.responseRef());
    }
    private void requireDocument(EnterpriseAccess.Admission admission, ExportProtocol.DocumentRow document, String id,
            String purpose, String action) throws Exception {
        if (!admission.context().tenant_id().equals(document.tenantId()) || !id.equals(document.documentId())) mismatch();
        var scope = new PermissionProtocol.Scope(document.tenantId(), document.projectId(), "DOCUMENT", id);
        resources.require(admission, scope, "document.read", purpose); resources.require(admission, scope, action, purpose);
    }
    private void requireExport(EnterpriseAccess.Admission admission, ExportProtocol.ManifestRow manifest, String action) throws Exception {
        var document = single(mapper.document(new HashMap<>(Map.of("userId", admission.subject().user().user_id(),
                "documentId", manifest.documentId(), "documentRevision", manifest.documentRevision()))));
        requireDocument(admission, document, manifest.documentId(), manifest.purpose(), action);
        if ("FORMAL_BODY".equals(manifest.purpose())) requireSigned(admission, document, manifest.signatureId());
    }
    private void requireSigned(EnterpriseAccess.Admission admission, ExportProtocol.DocumentRow document, String signature) throws Exception {
        var service = signing.getIfAvailable();
        if (service == null || signature == null) throw new TenantAccessException(ErrorCode.SIGNING_POLICY_UNCONFIGURED);
        service.requireExportCurrent(admission, document.documentId(), document.documentRevision(), signature);
    }
    private void requireProfile(JsonNode profile, ExportProtocol.DocumentRow document, ExportProtocol.CreateInput input) {
        if (!document.formatProfileRevision().equals(profile.required("format_revision").required("version_id").textValue())) mismatch();
        var outputs = profile.required("output_types");
        for (var format : input.formats()) if (!"MARKDOWN".equals(format)) {
            boolean present = false; for (var output : outputs) if (format.equals(output.textValue())) present = true;
            if (!present) throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
        }
    }
    private void requireProfileArtifacts(EnterpriseAccess.Admission admission, JsonNode profile,
            ExportProtocol.DocumentRow document) throws Exception {
        var refs = new ArrayList<JsonNode>(); refs.add(profile.required("format_revision"));
        profile.required("font_artifacts").forEach(refs::add);
        for (int index = 0; index < refs.size(); index++) {
            var ref = refs.get(index);
            var artifact = single(artifacts.artifact(ref.required("version_id").textValue()));
            if (!admission.context().tenant_id().equals(artifact.tenantId())
                    || artifact.projectId() != null && !document.projectId().equals(artifact.projectId())
                    || !ref.required("content_sha256").textValue().equals(artifact.sha256())
                    || index == 0 && !"application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(artifact.mediaType())
                    || index > 0 && !java.util.Set.of("font/ttf", "font/otf", "application/x-font-ttf", "application/vnd.ms-opentype").contains(artifact.mediaType())) mismatch();
            resources.require(admission, new PermissionProtocol.Scope(artifact.tenantId(), artifact.projectId(), "ARTIFACT", artifact.artifactId()),
                    "artifact.read", "EXPORT_INPUT");
        }
    }
    private void requireVersion(Map<String,Object> values, String id, String project, String hash) {
        values.put("versionId", id); var version = single(mapper.version(values));
        if (!Objects.equals(version.ownerProjectId(), project) && version.ownerProjectId() != null || hash != null && !hash.equals(version.contentSha256())) mismatch();
    }
    private static void validateFormats(ExportProtocol.CreateInput input) {
        if (input.formats().contains("MARKDOWN") && !"INTERNAL_EXCHANGE".equals(input.purpose()))
            throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
    private static long[] range(String value, long size) {
        if (size < 1 || size > Integer.MAX_VALUE) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        if (value == null) return new long[]{0, size};
        if (!value.matches("bytes=(?:0|[1-9][0-9]*)-(?:0|[1-9][0-9]*)")) throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
        String[] ends = value.substring(6).split("-", -1); long start = Long.parseLong(ends[0]), inclusive = Long.parseLong(ends[1]);
        if (inclusive < start || inclusive >= size) throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return new long[]{start, Math.addExact(inclusive, 1)};
    }
    private static Map<String,Object> values(EnterpriseAccess.Admission admission) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); return values;
    }
    private static void current(EnterpriseAccess.Admission admission, String context) {
        if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
    }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static <T> T single(List<T> rows) { if (rows.size() != 1) mismatch(); return rows.get(0); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void stale() { throw new TenantAccessException(ErrorCode.ACTION_STALE); }
    private record DownloadState(ExportProtocol.ItemRow item, ArtifactRegistryProtocol.ArtifactRow artifact, String authorization) { }
}
