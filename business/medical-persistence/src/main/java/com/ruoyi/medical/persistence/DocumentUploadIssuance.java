package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceBindings;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.UploadIssuanceMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class DocumentUploadIssuance {
    private static final String STATEMENT = "com.ruoyi.medical.persistence.mapper.UploadIssuanceMapper.currentOperation";
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();
    private final DocumentUploadPreparation preparation;
    private final UploadTransferStore uploads;
    private final UploadAuthorizationAuthority authority;
    private final UploadIssuanceMapper mapper;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final SchemaCatalogue schemas;
    private final String manifest;

    public DocumentUploadIssuance(DocumentUploadPreparation preparation, UploadTransferStore uploads,
            UploadAuthorizationAuthority authority, UploadIssuanceMapper mapper, PermissionMapper permissions,
            PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.preparation = Objects.requireNonNull(preparation); this.uploads = Objects.requireNonNull(uploads);
        this.authority = Objects.requireNonNull(authority); this.mapper = Objects.requireNonNull(mapper);
        this.permissions = Objects.requireNonNull(permissions); this.json = Objects.requireNonNull(json);
        schemas = SchemaCatalogue.fromClasspath();
        byte[] definition;
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("backend-design/interfaces.schema.yaml"))) {
            definition = input.readAllBytes();
        }
        var published = schemas.requirePublished(schemas.schemaRef("interfaces", "UploadGrant"), DigestUtils.sha256Hex(definition));
        var identity = json.object(); identity.put("schema_version", "1.0.0");
        identity.put("interfaces_schema_sha256", published.schemaSha256());
        identity.put("upload_authorization_manifest_sha256", new UploadAuthorizationSchema().manifestSha256());
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    // 外层领域服务必须通过EnterpriseAccess执行；所有Mapper写入共享当前短事务。
    Result issue(EnterpriseAccess.Admission admission, String context, String key, byte[] bytes,
            DocumentUploadPreparation.Settings settings) throws IOException, NoSuchAlgorithmException {
        return issueCurrent(admission, context, key, bytes, () -> settings);
    }

    @FunctionalInterface interface SettingsSource {
        DocumentUploadPreparation.Settings require() throws IOException, NoSuchAlgorithmException;
    }

    Result issueCurrent(EnterpriseAccess.Admission admission, String context, String key, byte[] bytes,
            SettingsSource settings) throws IOException, NoSuchAlgorithmException {
        if (admission == null) denied(); TenantScope.requireSqlAccess(STATEMENT);
        json.headers(context, key);
        if (!admission.context().tenant_id().equals(TenantScope.requireCurrent().tenantId())) denied();
        if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); values.put("key", key);
        TenantScope.requireSubjectParameter(STATEMENT, values);
        var request = preparation.request(bytes); String hash = requestHash(request);
        var previous = mapper.currentOperation(values);
        if (!previous.isEmpty()) return replay(admission, values, one(previous), request, hash);

        var prepared = preparation.prepare(admission, bytes, Objects.requireNonNull(settings.require()));
        if (!request.payload().equals(prepared.request().payload())) mismatch();
        var binding = uploads.createArtifactSession(admission, prepared.grant(), prepared.session(), prepared.binding());
        var current = authority.requireBoundDocument(admission, binding.uploadId());
        if (!current.authorizationSha256().equals(prepared.authority().authorizationSha256()))
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
        var data = new InterfaceProtocol.UploadGrant(binding.grantId(), binding.uploadId(), uploadUrl(binding.uploadId()),
            prepared.grant().expiresAt().toInstant(ZoneOffset.UTC).toString(), UploadTransferProtocol.PART_BYTES.longValueExact());
        TypedSchemaBindings.bind(schemas, InterfaceBindings.UPLOAD_GRANT, json.tree(data));
        var envelope = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, prepared.binding().expectedRevision().toString(), json.tree(data)));
        var canonical = schemas.bind("interfaces", envelope, "Envelope").canonicalJson();
        var now = prepared.grant().createdAt();
        var operation = new UploadOperationRow(binding.tenantId(), operationId, binding.subjectId(), "BX-03", key, hash, "COMPLETED", 201,
            canonical, binding.uploadId(), null, "1.0.0", manifest, binding.subjectId(), now, binding.classification(), BigInteger.ONE, now, null, null);
        values.put("row", operation); changed(mapper.saveOperation(values));

        var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operationId);
        audit.put("tenant_id", binding.tenantId()); audit.put("subject_id", binding.subjectId()); audit.put("project_id", binding.projectId());
        audit.put("document_id", binding.documentId()); audit.put("grant_id", binding.grantId()); audit.put("upload_id", binding.uploadId());
        audit.put("expected_revision", binding.expectedRevision().toString()); audit.put("context_revision", binding.contextRevision().toString());
        audit.put("request_sha256", hash); audit.set("request", JSON.readTree(request.payload().canonicalJson())); audit.set("response", envelope);
        audit.put("authorization_sha256", binding.authorizationSha256()); audit.set("authorization_manifest", JSON.readTree(binding.authorizationManifest()));
        audit.put("authority_grant_id", prepared.authority().resourceGrant().grantId());
        audit.put("authority_revision", prepared.authority().resourceGrant().revision().toString());
        String auditId = UUID.randomUUID().toString(), eventId = UUID.randomUUID().toString();
        changed(permissions.saveAudit(binding.subjectId(), binding.projectId(), auditId, "upload.issued", json.canonical(audit),
            requestId, manifest, binding.classification()));
        var event = json.object(); event.put("schema_version", "1.0.0"); event.put("operation_id", operationId);
        event.put("tenant_id", binding.tenantId()); event.put("project_id", binding.projectId()); event.put("document_id", binding.documentId());
        event.put("upload_id", binding.uploadId()); event.put("grant_id", binding.grantId()); event.put("revision", "1");
        event.put("authorization_sha256", binding.authorizationSha256()); event.put("expires_at", data.expiresAt());
        changed(permissions.saveEvent(binding.subjectId(), eventId, binding.uploadId(), "1", "upload.issued", json.canonical(event)));
        values.put("projectId", binding.projectId()); values.put("uploadId", binding.uploadId()); values.put("auditId", auditId);
        values.put("eventId", eventId); values.put("requestId", requestId); values.put("manifest", manifest); values.put("classification", binding.classification());
        if (!json.canonical(audit).equals(json.canonical(JSON.readTree(one(mapper.audit(values)))))
                || !json.canonical(event).equals(json.canonical(JSON.readTree(one(mapper.event(values)))))
                || !operation.equals(normalized(one(mapper.currentOperation(values))))) mismatch();
        return new Result(operationId, 201, envelope, false);
    }

    private Result replay(EnterpriseAccess.Admission admission, Map<String,Object> values, UploadOperationRow saved,
            TypedSchemaBindings.Binding<InterfaceProtocol.UploadRequest> request, String hash) throws IOException, NoSuchAlgorithmException {
        if (!Objects.equals(saved.requestSha256(), hash)) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        WireValues.uuid(saved.operationId()); WireValues.uuid(saved.targetId());
        if (!admission.context().tenant_id().equals(saved.tenantId()) || !values.get("userId").equals(saved.subjectId())
                || !saved.subjectId().equals(saved.createdBy()) || !"BX-03".equals(saved.operationName()) || !values.get("key").equals(saved.idempotencyKey())
                || !"COMPLETED".equals(saved.state()) || !Integer.valueOf(201).equals(saved.httpStatus()) || saved.errorCode() != null
                || !"1.0.0".equals(saved.schemaVersion()) || !manifest.equals(saved.schemaManifestSha256())
                || saved.createdAt() == null || saved.updatedAt() == null || saved.updatedAt().isBefore(saved.createdAt())
                || saved.classification() == null || saved.classification().isBlank() || saved.revision() == null || saved.revision().signum() <= 0) mismatch();
        if ((saved.dedupExpiresAt() == null) != (saved.dedupPolicyRevision() == null)) mismatch();
        if (saved.dedupExpiresAt() != null) {
            WireValues.uuid(saved.dedupPolicyRevision());
            if (!saved.dedupExpiresAt().isAfter(saved.createdAt())) mismatch();
            if (!saved.dedupExpiresAt().isAfter(Objects.requireNonNull(permissions.databaseTime())))
                throw new TenantAccessException(ErrorCode.ACTION_STALE);
        }
        // 重放保留原许可和原截止时间；新发送继续执行独立当前写入核查。
        authority.requireVisibleBoundDocument(admission, saved.targetId());
        var upload = uploads.session(admission, saved.targetId()); var binding = uploads.binding(admission, saved.targetId());
        var original = request.read();
        if (!"DOCUMENT_BODY".equals(upload.purpose()) || !original.documentId().equals(binding.documentId())
                || !original.expectedRevision().equals(binding.expectedRevision().toString())
                || !original.dependencyAccessSetId().equals(binding.dependencyAccessSetId())
                || !original.sizeBytes().equals(upload.expectedSize().toString()) || !original.sha256().equals(upload.expectedSha256())
                || !original.mediaType().equals(upload.mediaType()) || !request.payload().schemaSha256().equals(binding.schemaDocumentSha256())
                || !saved.classification().equals(binding.classification())) mismatch();
        var envelope = JSON.readTree(Objects.requireNonNull(saved.responseRef())); schemas.bind("interfaces", envelope, "Envelope");
        var data = TypedSchemaBindings.bind(schemas, InterfaceBindings.UPLOAD_GRANT, envelope.required("data")).read();
        if (!binding.grantId().equals(data.grantId()) || !saved.targetId().equals(data.uploadId())
                || !uploadUrl(saved.targetId()).equals(data.uploadUrl()) || !upload.expiresAt().toInstant(ZoneOffset.UTC).toString().equals(data.expiresAt())
                || !original.expectedRevision().equals(envelope.required("resource_revision").textValue())) mismatch();
        WireValues.uuid(envelope.required("request_id").textValue());
        return new Result(saved.operationId(), 201, envelope, true);
    }

    private String requestHash(TypedSchemaBindings.Binding<InterfaceProtocol.UploadRequest> request) throws IOException {
        var value = json.object(); value.put("method", "POST"); value.put("path", "/api/artifact-upload-grants"); value.putNull("query");
        value.put("schema_version", "1.0.0"); value.put("schema_ref", request.payload().schemaRef());
        value.put("schema_sha256", request.payload().schemaSha256()); value.put("target_id", request.read().documentId());
        value.set("body", JSON.readTree(request.payload().canonicalJson())); return DigestUtils.sha256Hex(json.canonical(value));
    }

    private static String uploadUrl(String uploadId) { return "/api/uploads/" + uploadId + "/parts/"; }
    private UploadOperationRow normalized(UploadOperationRow row) throws IOException {
        return new UploadOperationRow(row.tenantId(), row.operationId(), row.subjectId(), row.operationName(), row.idempotencyKey(),
            row.requestSha256(), row.state(), row.httpStatus(), json.canonical(JSON.readTree(Objects.requireNonNull(row.responseRef()))), row.targetId(),
            row.errorCode(), row.schemaVersion(), row.schemaManifestSha256(), row.createdBy(), row.createdAt(), row.classification(),
            row.revision(), row.updatedAt(), row.dedupExpiresAt(), row.dedupPolicyRevision());
    }
    record Result(String operationId, int httpStatus, JsonNode response, boolean replayed) {
        Result { WireValues.uuid(operationId); if (httpStatus != 201) throw new IllegalArgumentException("许可回执状态必须对应原创建事务"); response = response.deepCopy(); }
        @Override public JsonNode response() { return response.deepCopy(); }
    }
    private static <T> T one(List<T> rows) { if (rows.size() != 1) mismatch(); return Objects.requireNonNull(rows.get(0)); }
    private static void changed(int count) { if (count != 1) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
