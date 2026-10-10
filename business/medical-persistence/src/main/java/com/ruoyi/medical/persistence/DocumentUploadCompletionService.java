package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceBindings;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.UploadIssuanceMapper;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class DocumentUploadCompletionService {
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();
    private final EnterpriseAccess access;
    private final UploadTransferStore uploads;
    private final UploadTransferMapper transfers;
    private final UploadIssuanceMapper mapper;
    private final UploadAuthorizationAuthority authority;
    private final UploadAuthorizationSchema authorizations;
    private final ArtifactRegistryMapper artifacts;
    private final DependencyLineage lineage;
    private final DocumentBodyDomainAuthority bodies;
    private final DocumentUploadTransferService transfer;
    private final SourceUploadTransport transport;
    private final DocumentUploadPolicy policy;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final SchemaCatalogue schemas;
    private final String manifest;

    public DocumentUploadCompletionService(EnterpriseAccess access, UploadTransferStore uploads, UploadTransferMapper transfers,
            UploadIssuanceMapper mapper, UploadAuthorizationAuthority authority, UploadAuthorizationSchema authorizations,
            ArtifactRegistryMapper artifacts, DependencyLineage lineage, DocumentBodyDomainAuthority bodies,
            DocumentUploadTransferService transfer, SourceUploadTransport transport, DocumentUploadPolicy policy,
            PermissionMapper permissions, PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.access = access; this.uploads = uploads; this.transfers = transfers; this.mapper = mapper;
        this.authority = authority; this.authorizations = authorizations; this.artifacts = artifacts;
        this.lineage = lineage; this.bodies = bodies; this.transfer = transfer; this.transport = transport;
        this.policy = policy; this.permissions = permissions; this.json = json;
        schemas = SchemaCatalogue.fromClasspath();
        byte[] definition;
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("backend-design/interfaces.schema.yaml"))) {
            definition = input.readAllBytes();
        }
        var identity = json.object(); identity.put("schema_version", "1.0.0");
        identity.put("interfaces_schema_sha256", DigestUtils.sha256Hex(definition));
        identity.put("upload_authorization_manifest_sha256", authorizations.manifestSha256());
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    Result complete(ApplicationSessionIdentity.Verified subject, String context, String key, String grantId, byte[] bytes) throws Exception {
        DocumentUploadTransferService.requireOutsideTransaction(subject); json.headers(context, key); WireValues.uuid(grantId);
        var text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Objects.requireNonNull(bytes).clone())).toString();
        var value = JSON.readTree(text);
        if (value == null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var request = TypedSchemaBindings.bind(schemas, InterfaceBindings.UPLOAD_COMPLETION, value);
        String hash = requestHash(grantId, request);
        var initial = transaction(subject, admission -> start(admission, context, key, grantId, request.read(), hash));
        if (initial.replayed() != null) return initial.replayed();
        return transport.completeDocument(subject, initial.upload(), transfer, policy,
            () -> transaction(subject, admission -> { current(admission, initial, context, key, hash); return null; }),
            (reference, content) -> {
                var payload = DocumentBodySemantics.published(schemas, initial.current().publishedSchema(), content, policy.bodyLimits());
                return transaction(subject, admission -> finish(admission, initial, context, key, hash, reference,
                    JSON.readTree(payload.canonicalJson())));
            });
    }

    private Work start(EnterpriseAccess.Admission admission, String context, String key, String grantId,
            InterfaceProtocol.UploadCompletion request, String hash) throws Exception {
        if (!context.equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var values = values(admission, key); values.put("grantId", grantId);
        var found = mapper.documentUploadForGrant(values);
        if (found.size() != 1) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        String id = one(found); var upload = uploads.session(admission, id); var binding = uploads.binding(admission, id);
        if (!grantId.equals(binding.grantId()) || !"DOCUMENT_BODY".equals(binding.purpose())) mismatch();
        if (!request.sha256().equals(upload.expectedSha256()) || !request.sizeBytes().equals(upload.expectedSize().toString())) mismatch();
        if (!request.expectedRevision().equals(binding.expectedRevision().toString())) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        var operations = mapper.completionOperation(values);
        if (!operations.isEmpty()) {
            var operation = one(operations); requireOperation(admission, key, hash, upload, operation);
            if ("COMPLETED".equals(operation.state()))
                return new Work(upload, binding, null, operation, replay(admission, upload, binding, operation));
        }
        var current = writing(admission, upload, binding);
        policy.requireConfigured();
        if (upload.expectedSize().compareTo(policy.maximumBytes()) > 0) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        if (!"application/json".equals(upload.mediaType())) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        if (!upload.receivedSize().equals(upload.expectedSize())) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        UploadOperationRow operation;
        if (operations.isEmpty()) {
            var now = transfers.databaseTime(parameters(admission, id));
            operation = new UploadOperationRow(upload.tenantId(), UUID.randomUUID().toString(), upload.subjectId(), "BX-04", key, hash,
                "PROCESSING", null, null, id, null, "1.0.0", manifest, upload.subjectId(), now, upload.classification(), BigInteger.ONE, now, null, null);
            values.put("row", operation); changed(mapper.startCompletionOperation(values));
            if (!operation.equals(one(mapper.completionOperation(values)))) mismatch();
        } else operation = one(operations);
        return new Work(upload, binding, current, operation, null);
    }

    private UploadAuthorizationAuthority.VerifiedDocument writing(EnterpriseAccess.Admission admission,
            UploadTransferProtocol.SessionRow upload, UploadTransferProtocol.BindingRow binding) throws Exception {
        var grant = one(transfers.grant(parameters(admission, upload.uploadId())));
        var now = transfers.databaseTime(parameters(admission, upload.uploadId()));
        if (!Set.of("ACTIVE", "COMPLETING", "COMPLETED").contains(upload.state()) || !upload.expiresAt().isAfter(now)
                || !Set.of("ISSUED", "UPLOADING").contains(grant.status()) || !grant.expiresAt().isAfter(now))
            throw new TenantAccessException(ErrorCode.ACTION_STALE);
        if (!admission.context().context_revision().equals(binding.contextRevision().toString()))
            throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var fixed = authorizations.requireBinding(binding, grant);
        var current = authority.requireDocument(admission, binding.documentId(), binding.expectedRevision().toString(), fixed);
        if (!current.document().classification().equals(binding.classification())) mismatch();
        return current;
    }

    private UploadAuthorizationAuthority.VerifiedDocument current(EnterpriseAccess.Admission admission, Work original,
            String context, String key, String hash) throws Exception {
        if (!context.equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var operation = one(mapper.completionOperation(values(admission, key)));
        requireOperation(admission, key, hash, original.upload(), operation);
        if (!operation.equals(original.operation())) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        var upload = uploads.session(admission, original.upload().uploadId());
        var binding = uploads.binding(admission, upload.uploadId());
        if (!original.binding().equals(binding) || !upload.objectKey().equals(original.upload().objectKey())
                || !upload.expectedSize().equals(original.upload().expectedSize())
                || !upload.expectedSha256().equals(original.upload().expectedSha256())) mismatch();
        var current = writing(admission, upload, binding);
        if (!current.authorizationSha256().equals(original.current().authorizationSha256()))
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        return current;
    }

    private Result finish(EnterpriseAccess.Admission admission, Work original, String context, String key, String hash,
            InterfaceProtocol.ObjectRef reference, JsonNode body) throws Exception {
        var current = current(admission, original, context, key, hash);
        bodies.require(admission, current, schemas, body);
        var upload = uploads.session(admission, original.upload().uploadId());
        if (!"COMPLETED".equals(upload.state()) || !reference.equals(UploadTransferObjects.decode(upload.objectRef(),
                new InterfaceProtocol.ObjectRef(upload.objectKey(), upload.objectVersion(), upload.contentSha256(),
                    upload.expectedSize().toString(), upload.mediaType())))) mismatch();
        var grant = one(transfers.grant(parameters(admission, upload.uploadId())));
        String artifactId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
        var binding = original.binding(); var now = transfers.databaseTime(parameters(admission, upload.uploadId()));
        var values = values(admission, key); values.put("projectId", binding.projectId()); values.put("artifactId", artifactId);
        values.put("reference", reference); values.put("size", WireValues.revision(reference.sizeBytes()));
        values.put("storedObject", StoredObjectReferences.encode(reference)); values.put("binding", binding);
        values.put("artifactManifest", current.manifest().artifactSchemaManifest()); values.put("now", now);
        values.put("schemaRef", current.manifest().schemaRef());
        changed(mapper.saveArtifactVersion(values)); changed(mapper.saveArtifact(values)); changed(mapper.saveContentBinding(values));
        for (var member : current.dependencies().manifest().members()) {
            values.put("upstreamId", member.upstreamVersionId()); changed(mapper.saveArtifactLineage(values));
        }
        lineage.require(artifactId, current.dependencies().manifest().members(), current.manifest().dependencyRequirement().lineageLimits());
        requireArtifact(binding, current.manifest(), artifactId, reference);
        var identity = json.object(); identity.put("object_key", reference.objectKey()); identity.put("object_version", reference.objectVersion());
        values.put("objectIdentitySha256", DigestUtils.sha256Hex(json.canonical(identity)));
        values.put("grantId", binding.grantId()); values.put("grantRevision", grant.revision()); changed(mapper.completeGrant(values));
        var savedGrant = one(transfers.grant(parameters(admission, upload.uploadId())));
        if (!"COMPLETED".equals(savedGrant.status()) || !artifactId.equals(savedGrant.artifactId())
                || !reference.objectVersion().equals(savedGrant.objectVersion())
                || !values.get("objectIdentitySha256").equals(savedGrant.objectIdentitySha256())
                || !grant.revision().add(BigInteger.ONE).equals(savedGrant.revision())) mismatch();
        var receipt = new InterfaceProtocol.ArtifactReceipt(artifactId, reference.sha256(), current.manifest().schemaRef(), "1");
        TypedSchemaBindings.bind(schemas, InterfaceBindings.ARTIFACT_RECEIPT, json.tree(receipt));
        var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, "1", json.tree(receipt)));
        String canonical = schemas.bind("interfaces", response, "Envelope").canonicalJson();
        values.put("response", canonical); values.put("operationId", original.operation().operationId());
        values.put("requestHash", hash); values.put("uploadId", upload.uploadId()); changed(mapper.completeCompletionOperation(values));
        var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", original.operation().operationId());
        audit.put("tenant_id", binding.tenantId()); audit.put("subject_id", binding.subjectId()); audit.put("project_id", binding.projectId());
        audit.put("document_id", binding.documentId()); audit.put("grant_id", binding.grantId()); audit.put("upload_id", upload.uploadId());
        audit.put("artifact_id", artifactId); audit.put("request_sha256", hash); audit.put("sha256", reference.sha256());
        audit.put("expected_revision", binding.expectedRevision().toString()); audit.put("authorization_sha256", current.authorizationSha256());
        audit.set("response", response);
        var event = json.object(); event.put("schema_version", "1.0.0"); event.put("tenant_id", binding.tenantId());
        event.put("project_id", binding.projectId()); event.put("document_id", binding.documentId()); event.put("artifact_id", artifactId);
        event.put("grant_id", binding.grantId()); event.put("upload_id", upload.uploadId()); event.put("sha256", reference.sha256()); event.put("revision", "1");
        String auditId = UUID.randomUUID().toString(), eventId = UUID.randomUUID().toString();
        changed(permissions.saveAudit(binding.subjectId(), binding.projectId(), auditId, "document.upload.completed", json.canonical(audit),
            requestId, manifest, binding.classification()));
        changed(permissions.saveEvent(binding.subjectId(), eventId, artifactId, "1", "document.upload.completed", json.canonical(event)));
        values.put("auditId", auditId); values.put("eventId", eventId); values.put("requestId", requestId);
        values.put("manifest", manifest); values.put("classification", binding.classification());
        if (!json.canonical(audit).equals(json.canonical(JSON.readTree(one(mapper.completionAudit(values)))))
                || !json.canonical(event).equals(json.canonical(JSON.readTree(one(mapper.completionEvent(values)))))) mismatch();
        var saved = one(mapper.completionOperation(values)); requireOperation(admission, key, hash, upload, saved);
        if (!"COMPLETED".equals(saved.state()) || !canonical.equals(json.canonical(JSON.readTree(saved.responseRef())))) mismatch();
        return new Result(200, response);
    }

    private Result replay(EnterpriseAccess.Admission admission, UploadTransferProtocol.SessionRow upload,
            UploadTransferProtocol.BindingRow binding, UploadOperationRow operation) throws Exception {
        var current = authority.requireVisibleBoundDocument(admission, upload.uploadId());
        var grant = one(transfers.grant(parameters(admission, upload.uploadId())));
        if (!"COMPLETED".equals(upload.state()) || !"COMPLETED".equals(grant.status())) mismatch();
        var response = JSON.readTree(operation.responseRef()); schemas.bind("interfaces", response, "Envelope");
        var receipt = TypedSchemaBindings.bind(schemas, InterfaceBindings.ARTIFACT_RECEIPT, response.required("data")).read();
        var reference = UploadTransferObjects.decode(upload.objectRef(), new InterfaceProtocol.ObjectRef(upload.objectKey(), upload.objectVersion(),
            upload.contentSha256(), upload.expectedSize().toString(), upload.mediaType()));
        if (!receipt.artifactId().equals(grant.artifactId()) || !receipt.sha256().equals(reference.sha256())
                || !receipt.schemaRef().equals(current.manifest().schemaRef()) || !"1".equals(receipt.revision())
                || !"1".equals(response.required("resource_revision").textValue())
                || !reference.objectVersion().equals(grant.objectVersion())) mismatch();
        var identity = json.object(); identity.put("object_key", reference.objectKey()); identity.put("object_version", reference.objectVersion());
        if (!DigestUtils.sha256Hex(json.canonical(identity)).equals(grant.objectIdentitySha256())) mismatch();
        requireArtifact(binding, current.manifest(), grant.artifactId(), reference);
        lineage.require(grant.artifactId(), current.dependencies().manifest().members(), current.manifest().dependencyRequirement().lineageLimits());
        WireValues.uuid(response.required("request_id").textValue()); return new Result(200, response);
    }

    private void requireArtifact(UploadTransferProtocol.BindingRow binding, UploadAuthorizationProtocol.Manifest fixed,
            String id, InterfaceProtocol.ObjectRef reference) throws IOException {
        var expected = new ArtifactRegistryProtocol.ArtifactRow(binding.tenantId(), binding.projectId(), id, "DOCUMENT_BODY",
            StoredObjectReferences.encode(reference), reference.objectKey(), reference.objectVersion(), reference.sha256(),
            WireValues.revision(reference.sizeBytes()), reference.mediaType(), binding.dependencyAccessSetId(), reference.sha256(),
            "1.0.0", fixed.artifactSchemaManifest(), binding.classification());
        var actual = one(artifacts.artifact(id));
        if (!reference.equals(StoredObjectReferences.decode(actual.storedObject(), actual.reference()))) mismatch();
        var normalized = new ArtifactRegistryProtocol.ArtifactRow(actual.tenantId(), actual.projectId(), actual.artifactId(), actual.artifactKind(),
            StoredObjectReferences.encode(reference), actual.objectKey(), actual.objectVersion(), actual.sha256(), actual.sizeBytes(), actual.mediaType(),
            actual.accessSetId(), actual.contentSha256(), actual.schemaVersion(), actual.schemaManifest(), actual.classification());
        if (!expected.equals(normalized) || !new ArtifactRegistryProtocol.BindingRow(binding.tenantId(), binding.projectId(), id, "DOCUMENT",
                binding.documentId(), fixed.schemaRef(), binding.schemaDocumentSha256(), binding.dependencyAccessSetId(), binding.dependencyManifestSha256())
                .equals(one(artifacts.binding(id))) || !new ArtifactRegistryProtocol.VersionRow(id, binding.projectId(), "ARTIFACT", reference.sha256())
                .equals(one(artifacts.version(id)))) mismatch();
    }

    private void requireOperation(EnterpriseAccess.Admission admission, String key, String hash,
            UploadTransferProtocol.SessionRow upload, UploadOperationRow row) {
        if (!hash.equals(row.requestSha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        WireValues.uuid(row.operationId());
        if (!admission.context().tenant_id().equals(row.tenantId()) || !upload.subjectId().equals(row.subjectId())
                || !row.subjectId().equals(row.createdBy()) || !"BX-04".equals(row.operationName()) || !key.equals(row.idempotencyKey())
                || !upload.uploadId().equals(row.targetId()) || !"1.0.0".equals(row.schemaVersion()) || !manifest.equals(row.schemaManifestSha256())
                || !upload.classification().equals(row.classification()) || row.createdAt() == null || row.updatedAt() == null
                || row.updatedAt().isBefore(row.createdAt()) || row.errorCode() != null
                || row.dedupExpiresAt() != null || row.dedupPolicyRevision() != null) mismatch();
        if ("PROCESSING".equals(row.state())) {
            if (!BigInteger.ONE.equals(row.revision()) || row.httpStatus() != null || row.responseRef() != null) mismatch();
        } else if ("COMPLETED".equals(row.state())) {
            if (!BigInteger.TWO.equals(row.revision()) || !Integer.valueOf(200).equals(row.httpStatus()) || row.responseRef() == null) mismatch();
        } else mismatch();
    }
    private String requestHash(String grantId, TypedSchemaBindings.Binding<InterfaceProtocol.UploadCompletion> request) throws IOException {
        var value = json.object(); value.put("method", "POST"); value.put("path", "/api/artifact-upload-grants/" + grantId + "/completions");
        value.putNull("query"); value.put("schema_version", "1.0.0"); value.put("schema_ref", request.payload().schemaRef());
        value.put("schema_sha256", request.payload().schemaSha256()); value.set("body", JSON.readTree(request.payload().canonicalJson()));
        return DigestUtils.sha256Hex(json.canonical(value));
    }
    private static HashMap<String,Object> values(EnterpriseAccess.Admission admission, String key) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); values.put("key", key);
        TenantScope.requireSubjectParameter("com.ruoyi.medical.persistence.mapper.UploadIssuanceMapper.completionOperation", values); return values;
    }
    private static Map<String,Object> parameters(EnterpriseAccess.Admission admission, String uploadId) {
        return Map.of("userId", admission.subject().user().user_id(), "uploadId", uploadId, "lock", true);
    }
    private <T> T transaction(ApplicationSessionIdentity.Verified subject, Checked<T> operation) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> operation.apply(admission)).get());
    }
    @FunctionalInterface private interface Checked<T> { T apply(EnterpriseAccess.Admission admission) throws Exception; }
    private record Work(UploadTransferProtocol.SessionRow upload, UploadTransferProtocol.BindingRow binding,
            UploadAuthorizationAuthority.VerifiedDocument current, UploadOperationRow operation, Result replayed) { }
    record Result(int httpStatus, JsonNode response) {
        Result { if (httpStatus != 200) throw new IllegalArgumentException("制品登记回执状态无效"); response = response.deepCopy(); }
        @Override public JsonNode response() { return response.deepCopy(); }
    }
    private static <T> T one(List<T> rows) { if (rows.size() != 1) mismatch(); return Objects.requireNonNull(rows.get(0)); }
    private static void changed(int count) { if (count != 1) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
