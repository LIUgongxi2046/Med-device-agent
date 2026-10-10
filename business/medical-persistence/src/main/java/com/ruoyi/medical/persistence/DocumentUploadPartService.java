package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceUploadMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
final class DocumentUploadPartService {
    private final EnterpriseAccess access;
    private final UploadTransferStore uploads;
    private final UploadAuthorizationAuthority authority;
    private final SourceUploadMapper operations;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final SourceUploadSchema parts;
    private final OperationSchema operationSchema;
    private final SchemaCatalogue schemas;
    private final String manifest;

    DocumentUploadPartService(EnterpriseAccess access, UploadTransferStore uploads, UploadAuthorizationAuthority authority,
            SourceUploadMapper operations, PermissionMapper permissions, PermissionSchema json, SourceUploadSchema parts,
            OperationSchema operationSchema) throws IOException, NoSuchAlgorithmException {
        this.access = Objects.requireNonNull(access); this.uploads = Objects.requireNonNull(uploads);
        this.authority = Objects.requireNonNull(authority); this.operations = Objects.requireNonNull(operations);
        this.permissions = Objects.requireNonNull(permissions); this.json = Objects.requireNonNull(json);
        this.parts = Objects.requireNonNull(parts); this.operationSchema = Objects.requireNonNull(operationSchema);
        schemas = SchemaCatalogue.fromClasspath();
        var identity = json.object(); identity.put("schema_version", "1.0.0"); identity.put("purpose", "DOCUMENT_BODY");
        identity.put("part_schema_sha256", parts.schemaSha256()); identity.put("permission_manifest_sha256", json.manifestSha256());
        identity.put("upload_authorization_manifest_sha256", new UploadAuthorizationSchema().manifestSha256());
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    record Request(String context, String key, String uploadId, long partNo, String sha256, long sizeBytes) {
        Request {
            PermissionSchema.revision(context); WireValues.uuid(uploadId); WireValues.hash(sha256);
            if (key == null || !key.matches("[!-~]{16,128}") || partNo < 1 || partNo > 4294967295L || sizeBytes <= 0
                    || sizeBytes > UploadTransferProtocol.PART_BYTES.longValueExact())
                throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        }
    }

    String start(ApplicationSessionIdentity.Verified subject, Request request) {
        json.headers(request.context(), request.key());
        return transaction(subject, admission -> {
            context(admission, request);
            var part = uploads.partIfPresent(admission, request.uploadId(), request.partNo()).orElse(null);
            var current = require(admission, request, part == null || !"STORED".equals(part.state()));
            var upload = uploads.session(admission, request.uploadId());
            if (!UploadTransferStore.range(upload.expectedSize(), request.partNo()).size().equals(BigInteger.valueOf(request.sizeBytes()))) mismatch();
            if (part != null && !request.sha256().equals(part.sha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            var values = values(admission, request); var previous = operations.partOperation(values);
            if (!previous.isEmpty()) { verify(admission, request, one(previous), current); return upload.tenantId(); }
            values.put("operationId", id()); values.put("now", Objects.requireNonNull(permissions.databaseTime()));
            values.put("classification", upload.classification()); changed(operations.startPartOperation(values));
            verify(admission, request, one(operations.partOperation(values)), current); return upload.tenantId();
        });
    }

    record Response(int status, JsonNode body) {
        Response { body = Objects.requireNonNull(body).deepCopy(); }
        @Override public JsonNode body() { return body.deepCopy(); }
    }

    Response replay(ApplicationSessionIdentity.Verified subject, Request request) {
        return transaction(subject, admission -> {
            context(admission, request);
            var part = uploads.partIfPresent(admission, request.uploadId(), request.partNo()).orElse(null);
            var current = require(admission, request, part == null || !"STORED".equals(part.state()));
            var operation = one(operations.partOperation(values(admission, request))); verify(admission, request, operation, current);
            if ("COMPLETED".equals(operation.state())) return new Response(200, complete(admission, request));
            return new Response(202, operationSchema.operation(new InterfaceProtocol.Operation(operation.operationId(), operation.targetId(),
                    InterfaceProtocol.OperationStatus.PROCESSING, "/api/operations/" + operation.operationId()), id()));
        });
    }

    UploadPartReceiver.Authority receiving(Request request, S3ObjectReader.Admission lease) {
        Objects.requireNonNull(lease);
        return new UploadPartReceiver.Authority() {
            @Override public String require(EnterpriseAccess.Admission admission, String uploadId, boolean writing) throws Exception {
                lease.requireCurrent();
                if (!request.uploadId().equals(uploadId)) mismatch();
                context(admission, request);
                var current = DocumentUploadPartService.this.require(admission, request, writing);
                var operation = one(operations.partOperation(values(admission, request))); verify(admission, request, operation, current);
                lease.requireCurrent(); return current.authorizationSha256() + ":" + operation.operationId() + ":" + operation.requestSha256();
            }
            @Override public void stored(EnterpriseAccess.Admission admission, UploadTransferProtocol.SessionRow previous,
                    UploadTransferProtocol.PartRow saved) throws Exception {
                lease.requireCurrent();
                var current = uploads.session(admission, request.uploadId());
                if (!previous.uploadId().equals(request.uploadId()) || saved.partNo() != request.partNo()
                        || !current.receivedSize().equals(previous.receivedSize().add(saved.sizeBytes()))
                        || !current.revision().equals(previous.revision().add(BigInteger.ONE))) mismatch();
                complete(admission, request); lease.requireCurrent();
            }
        };
    }

    JsonNode receipt(ApplicationSessionIdentity.Verified subject, Request request) {
        return transaction(subject, admission -> complete(admission, request));
    }

    private JsonNode complete(EnterpriseAccess.Admission admission, Request request) throws Exception {
        context(admission, request); var current = require(admission, request, false);
        var values = values(admission, request); var operation = one(operations.partOperation(values)); verify(admission, request, operation, current);
        var part = uploads.part(admission, request.uploadId(), request.partNo());
        if (!"STORED".equals(part.state())) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        if (!request.sha256().equals(part.sha256()) || !BigInteger.valueOf(request.sizeBytes()).equals(part.sizeBytes())) mismatch();
        var data = json.object(); data.put("part_no", request.partNo()); data.put("size_bytes", part.sizeBytes().toString());
        data.put("sha256", part.sha256()); data.put("state", "STORED"); parts.verify("Part", data);
        if ("COMPLETED".equals(operation.state())) {
            var response = parts.readJson(operation.responseRef()); schemas.validate("interfaces", response, "Envelope");
            parts.verify("Part", response.required("data")); WireValues.uuid(response.required("request_id").textValue());
            if (!json.canonical(data).equals(json.canonical(response.required("data")))
                    || !part.revision().toString().equals(response.required("resource_revision").textValue())) mismatch();
            return response.deepCopy();
        }
        String requestId = id(); var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, part.revision().toString(), data));
        schemas.validate("interfaces", response, "Envelope");
        values.put("operationId", operation.operationId()); values.put("response", json.canonical(response)); changed(operations.completePartOperation(values));
        var completed = one(operations.partOperation(values)); verify(admission, request, completed, current);
        if (!"COMPLETED".equals(completed.state()) || !json.canonical(response).equals(json.canonical(parts.readJson(completed.responseRef())))) mismatch();
        var upload = uploads.session(admission, request.uploadId());
        String actor = admission.subject().user().user_id(), auditId = id(), eventId = id();
        var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operation.operationId());
        audit.put("tenant_id", upload.tenantId()); audit.put("project_id", upload.projectId()); audit.put("subject_id", actor);
        audit.put("document_id", current.document().documentId()); audit.put("upload_id", request.uploadId()); audit.put("part_no", request.partNo());
        audit.put("request_sha256", operation.requestSha256()); audit.put("context_revision", admission.context().context_revision());
        audit.put("auth_epoch", admission.watermarks().authEpoch().toString()); audit.put("governance_epoch", admission.watermarks().governanceEpoch().toString());
        audit.put("authorization_sha256", current.authorizationSha256()); audit.set("response", response);
        var event = json.object(); event.put("schema_version", "1.0.0"); event.put("operation_id", operation.operationId());
        event.put("document_id", current.document().documentId()); event.put("upload_id", request.uploadId()); event.put("part_no", request.partNo());
        event.put("revision", upload.revision().toString()); event.put("size_bytes", part.sizeBytes().toString()); event.put("sha256", part.sha256());
        changed(permissions.saveAudit(actor, upload.projectId(), auditId, "document.upload.part.stored", json.canonical(audit), requestId, manifest, upload.classification()));
        changed(permissions.saveEvent(actor, eventId, upload.uploadId(), upload.revision().toString(), "document.upload.part.stored", json.canonical(event)));
        values.put("auditId", auditId); values.put("eventId", eventId); values.put("requestId", requestId); values.put("projectId", upload.projectId());
        values.put("classification", upload.classification()); values.put("uploadRevision", upload.revision());
        if (!json.canonical(audit).equals(json.canonical(parts.readJson(one(operations.documentPartAudit(values)))))
                || !json.canonical(event).equals(json.canonical(parts.readJson(one(operations.documentPartEvent(values)))))) mismatch();
        return response.deepCopy();
    }

    private UploadAuthorizationAuthority.VerifiedDocument require(EnterpriseAccess.Admission admission, Request request, boolean writing)
            throws IOException, NoSuchAlgorithmException {
        var current = writing ? authority.requireBoundDocument(admission, request.uploadId()) : authority.requireVisibleBoundDocument(admission, request.uploadId());
        var upload = uploads.session(admission, request.uploadId());
        if (!"DOCUMENT_BODY".equals(upload.purpose()) || !Objects.equals(upload.projectId(), current.document().projectId())
                || !Objects.equals(upload.classification(), current.document().classification())) mismatch();
        if (writing && !"ACTIVE".equals(upload.state())) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        return current;
    }

    private Map<String, Object> values(EnterpriseAccess.Admission admission, Request request) throws IOException {
        var identity = json.object(); identity.put("method", "PUT"); identity.put("path", "/api/uploads/" + request.uploadId() + "/parts/" + request.partNo());
        identity.putNull("query"); identity.put("schema_version", "1.0.0"); identity.put("schema_sha256", parts.schemaSha256());
        identity.put("purpose", "DOCUMENT_BODY"); identity.put("upload_id", request.uploadId()); identity.put("part_no", request.partNo());
        identity.put("size_bytes", Long.toString(request.sizeBytes())); identity.put("content_sha256", request.sha256());
        var values = new HashMap<String, Object>(); values.put("userId", admission.subject().user().user_id());
        values.put("key", request.key()); values.put("uploadId", request.uploadId()); values.put("manifest", manifest);
        values.put("requestSha256", DigestUtils.sha256Hex(json.canonical(identity))); return values;
    }

    private void verify(EnterpriseAccess.Admission admission, Request request, UploadOperationRow operation,
            UploadAuthorizationAuthority.VerifiedDocument current) throws IOException {
        if (!values(admission, request).get("requestSha256").equals(operation.requestSha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        String actor = admission.subject().user().user_id();
        if (!admission.context().tenant_id().equals(operation.tenantId()) || !actor.equals(operation.subjectId()) || !actor.equals(operation.createdBy())
                || !"KN-02".equals(operation.operationName()) || !request.key().equals(operation.idempotencyKey()) || !request.uploadId().equals(operation.targetId())
                || !"1.0.0".equals(operation.schemaVersion()) || !manifest.equals(operation.schemaManifestSha256())
                || !current.document().classification().equals(operation.classification()) || operation.createdAt() == null || operation.updatedAt() == null
                || operation.updatedAt().isBefore(operation.createdAt()) || operation.errorCode() != null
                || operation.dedupExpiresAt() != null || operation.dedupPolicyRevision() != null) mismatch();
        WireValues.uuid(operation.operationId());
        if ("PROCESSING".equals(operation.state())) {
            if (!BigInteger.ONE.equals(operation.revision()) || operation.httpStatus() != null || operation.responseRef() != null
                    || !operation.createdAt().equals(operation.updatedAt())) mismatch();
        } else if ("COMPLETED".equals(operation.state())) {
            if (!BigInteger.TWO.equals(operation.revision()) || !Integer.valueOf(200).equals(operation.httpStatus()) || operation.responseRef() == null) mismatch();
        } else throw new TenantAccessException(ErrorCode.ACTION_STALE);
    }

    private static void context(EnterpriseAccess.Admission admission, Request request) {
        if (!request.context().equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
    }
    private <T> T transaction(ApplicationSessionIdentity.Verified subject, Action<T> action) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> action.apply(admission)).get());
    }
    @FunctionalInterface private interface Action<T> { T apply(EnterpriseAccess.Admission admission) throws Exception; }
    private static <T> T one(List<T> rows) { if (rows.size() != 1) mismatch(); return rows.get(0); }
    private static void changed(int count) { if (count != 1) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static String id() { return UUID.randomUUID().toString(); }
}
