package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceUploadMapper;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
@EnableConfigurationProperties(SourceUploadPolicy.class)
public final class SourceUploadService implements UploadPartReceiver.Authority {
    private final EnterpriseAccess access;
    private final SourceUploadMapper mapper;
    private final UploadTransferMapper transfers;
    private final UploadTransferStore uploads;
    private final PermissionMapper permissions;
    private final PermissionAuthority authority;
    private final SourceUploadSchema schema;
    private final PermissionSchema json;
    private final SourceUploadPolicy policy;
    private final OperationSchema operationSchema;
    private final SchemaCatalogue shared;
    private final SourceCompletionObjects completionObjects;
    private final String manifest;

    public SourceUploadService(EnterpriseAccess access, SourceUploadMapper mapper, UploadTransferMapper transfers,
            UploadTransferStore uploads, PermissionMapper permissions, PermissionAuthority authority,
            SourceUploadSchema schema, PermissionSchema json, SourceUploadPolicy policy, OperationSchema operationSchema) throws IOException, NoSuchAlgorithmException {
        this.access = access; this.mapper = mapper; this.transfers = transfers; this.uploads = uploads;
        this.permissions = permissions; this.authority = authority; this.schema = schema; this.json = json; this.policy = policy;
        this.operationSchema = operationSchema;
        completionObjects = new SourceCompletionObjects();
        shared = SchemaCatalogue.fromClasspath();
        var identity = json.object(); identity.put("schema_version", "1.0.0"); identity.put("source_upload_schema_sha256", schema.schemaSha256());
        identity.put("permission_manifest_sha256", json.manifestSha256()); identity.set("settings", json.tree(policy));
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    JsonNode create(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] bytes) throws IOException {
        json.headers(context, key); var request = schema.create(bytes, policy.maximumBytes());
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> issue(admission, context, key, request)).get());
    }

    String manifestSha256() { return manifest; }

    JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String uploadId, long afterPart, int limit) {
        WireValues.uuid(uploadId); PermissionSchema.revision(context);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> registration(admission,context,uploadId,afterPart,limit)).get());
    }

    private JsonNode registration(EnterpriseAccess.Admission admission,String context,String uploadId,long afterPart,int limit) throws IOException {
            context(admission, context); var values = values(admission); values.put("uploadId", uploadId);
            var saved = one(mapper.declaration(values)); var request = stored(admission, saved);
            requireAuthority(admission, request.content(), permissions.databaseTime());
            var session = uploads.session(admission, uploadId); var source = one(transfers.source(values));
            requireCorrespondence(saved, request, source, session);
            var data = json.object(); data.put("upload_id", uploadId); data.set("request", request.content());
            data.put("received_size", source.receivedSize().toString()); data.put("upload_status", source.status());
            data.put("transfer_state", session.state()); data.put("max_part_bytes", session.maxPartBytes().toString());
            data.put("expires_at", time(session.expiresAt())); var parts = data.putArray("parts");
            for (var part : uploads.parts(admission, uploadId, afterPart, limit)) {
                var item = parts.addObject(); item.put("part_no", part.partNo()); item.put("size_bytes", part.sizeBytes().toString());
                item.put("sha256", part.sha256()); item.put("state", part.state());
            }
            if (parts.size() == limit) data.put("next_part", parts.get(parts.size()-1).required("part_no").longValue());
            else data.putNull("next_part");
            return envelope("Registration", data, id(), source.revision().toString());
    }

    StatusSnapshot status(EnterpriseAccess.Admission admission,String context,String uploadId) throws IOException {
        var registration=registration(admission,context,uploadId,0,1);
        var values=values(admission); values.put("uploadId",uploadId); var operations=mapper.completionState(values);
        if(operations.isEmpty()) {
            if("RECEIVED".equals(registration.required("data").required("upload_status").textValue())
                    || "COMPLETING".equals(registration.required("data").required("transfer_state").textValue())) mismatch();
            return new StatusSnapshot(registration,null,null);
        }
        var operation=one(operations); values.put("operationId",operation.operationId()); values.put("key",operation.idempotencyKey());
        var command=schema.completionCommand(one(mapper.completionCommand(values)));
        var declaration=stored(admission,one(mapper.declaration(values)));
        var request=schema.completion(json.canonical(command.required("request")).getBytes(StandardCharsets.UTF_8),policy.maximumBytes(),declaration);
        values.put("requestSha256",completionRequestHash(uploadId,request)); completionReplay(admission,values,operation,request);
        JsonNode result=null;
        if("COMPLETED".equals(operation.state())) result=schema.completionResult(one(mapper.completionResult(values)));
        if("FAILED".equals(operation.state())) result=schema.completionResult(one(mapper.completionFailure(values)));
        var view=json.object(); view.put("operation_id",operation.operationId()); view.put("operation_state",operation.state());
        for(String name:List.of("result_status","source_id","source_version","registered_rights_revision","parse_version","attempt_id","run_id","error_code","reason"))
            view.putNull(name);
        if(result!=null) {
            view.set("result_status",result.required("status"));
            for(String name:List.of("source_id","source_version","parse_version","attempt_id","run_id","error_code","reason"))
                if(result.has(name)) view.set(name,result.required(name));
            if(result.has("rights_revision")) view.set("registered_rights_revision",result.required("rights_revision"));
        }
        return new StatusSnapshot(registration,view,result);
    }
    record StatusSnapshot(JsonNode registration,JsonNode completion,JsonNode result) { }

    private JsonNode issue(EnterpriseAccess.Admission admission, String context, String key,
            SourceUploadSchema.ValidatedCreate request) throws IOException {
        context(admission, context); var values = values(admission); values.put("key", key);
        JsonNode body = request.content(); LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
        var grant = requireAuthority(admission, body, now); String hash = requestHash(request);
        var previous = mapper.currentOperation(values);
        if (!previous.isEmpty()) return replay(admission, values, one(previous), request, hash);
        maintainer(body.required("provenance").required("maintainer_id").textValue(), now);
        String actor = admission.subject().user().user_id(), tenant = admission.context().tenant_id();
        String project = body.required("project_id").textValue(), uploadId = id(), operationId = id(), requestId = id();
        String classification = body.required("classification").textValue();
        LocalDateTime expires = now.plusSeconds(policy.expiresAfterSeconds());
        if (grant.validTo() != null && grant.validTo().isBefore(expires)) expires = grant.validTo();
        if (!expires.isAfter(now)) denied();
        String objectKey = "tenants/" + tenant + "/source-uploads/" + uploadId + "/original";
        var source = new UploadTransferProtocol.SourceRow(tenant, project, uploadId, body.required("batch_id").textValue(),
            nullable(body, "session_id"), body.required("ownership").textValue(), body.required("file_name").textValue(),
            request.sizeBytes(), BigInteger.ZERO, nullable(body, "sha256"), null, null, "PENDING", expires, "1.0.0", manifest,
            actor, now, classification, BigInteger.ONE, now);
        var session = new UploadTransferProtocol.SessionRow(tenant, project, uploadId, actor, "SOURCE", uploadId, null,
            request.sizeBytes(), nullable(body, "sha256"), BigInteger.ZERO, body.required("media_type").textValue(), objectKey,
            DigestUtils.sha256Hex(objectKey), UploadTransferProtocol.PART_BYTES, expires, "ACTIVE", null, null, null,
            "1.0.0", manifest, actor, now, classification, BigInteger.ONE, now);
        var data = json.object(); data.put("upload_id", uploadId); data.put("max_part_bytes", UploadTransferProtocol.PART_BYTES.toString());
        data.put("expires_at", time(expires)); var response = envelope("Created", data, requestId, "1");
        var operation = new UploadOperationRow(tenant, operationId, actor, "KN-01", key, hash, "COMPLETED", 201,
            json.canonical(response), uploadId, null, "1.0.0", manifest, actor, now, classification, BigInteger.ONE, now, null, null);
        var declaration = new SourceUploadProtocol.Declaration(tenant, project, uploadId, operationId, nullable(body, "source_id"),
            body.required("version_intent").textValue(), body.required("provenance").required("maintainer_id").textValue(), request.canonicalJson(),
            hash, request.schemaRef(), request.schemaSha256(), grant.grantId(), grant.revision(), new BigInteger(context),
            admission.watermarks().authEpoch(), admission.watermarks().governanceEpoch(), "1.0.0", manifest, actor, now, classification);
        values.put("row", source); changed(mapper.saveUpload(values));
        uploads.createSourceSession(admission, session);
        values.put("operation", operation); changed(mapper.saveOperation(values));
        values.put("declaration", declaration); changed(mapper.saveDeclaration(values));
        values.put("uploadId", uploadId);
        var saved = one(mapper.declaration(values)); requireCorrespondence(saved, stored(admission, saved), one(transfers.source(values)), uploads.session(admission, uploadId));
        if (!declaration.equals(normalized(saved)) || !operation.equals(normalized(one(mapper.currentOperation(values))))) mismatch();
        var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operationId); audit.put("upload_id", uploadId);
        audit.put("project_id", project); audit.put("tenant_id", tenant); audit.put("subject_id", actor); audit.put("request_sha256", hash);
        audit.put("authority_grant", grant.grantId()); audit.put("authority_revision", grant.revision().toString());
        audit.put("context_revision", context); audit.put("auth_epoch", admission.watermarks().authEpoch().toString());
        audit.put("governance_epoch", admission.watermarks().governanceEpoch().toString()); audit.set("request", body); audit.set("response", response);
        var event = json.object(); event.put("schema_version", "1.0.0"); event.put("operation_id", operationId); event.put("upload_id", uploadId);
        event.put("project_id", project); event.put("revision", "1"); event.put("expires_at", time(expires));
        String auditId = id(), eventId = id();
        changed(permissions.saveAudit(actor, project, auditId, "source.upload.created", json.canonical(audit), requestId, manifest, classification));
        changed(permissions.saveEvent(actor, eventId, uploadId, "1", "source.upload.created", json.canonical(event)));
        values.put("auditId", auditId); values.put("eventId", eventId); values.put("requestId", requestId); values.put("projectId", project);
        values.put("manifest", manifest); values.put("classification", classification);
        if (!json.canonical(audit).equals(json.canonical(schema.readJson(one(mapper.audit(values)))))
                || !json.canonical(event).equals(json.canonical(schema.readJson(one(mapper.event(values)))))) mismatch();
        return schema.readJson(operation.responseRef());
    }

    JsonNode requestCompletion(ApplicationSessionIdentity.Verified subject, String context, String key, String uploadId, byte[] bytes) {
        json.headers(context, key); WireValues.uuid(uploadId);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            context(admission, context); String authorization = require(admission, uploadId, false);
            var values = values(admission); values.put("uploadId", uploadId); values.put("key", key); values.put("manifest", manifest);
            var declaration = one(mapper.declaration(values));
            var request = schema.completion(bytes, policy.maximumBytes(), stored(admission, declaration));
            String hash = completionRequestHash(uploadId,request); values.put("requestSha256", hash);
            var previous = mapper.completionOperation(values);
            if (!previous.isEmpty()) return completionReplay(admission, values, one(previous), request);
            var source = one(transfers.source(values)); var session = uploads.session(admission, uploadId);
            if (!source.revision().equals(request.expectedRevision())) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
            if (!"ACTIVE".equals(session.state()) || !session.expiresAt().isAfter(permissions.databaseTime()))
                throw new TenantAccessException(ErrorCode.ACTION_STALE);
            var storedParts = completionParts(admission, uploadId, request);
            var completing = uploads.beginCompletion(admission, uploadId, session.revision().toString());
            String operationId = id(), requestId = id(), auditId = id(), eventId = id();
            values.put("operationId", operationId); values.put("now", permissions.databaseTime()); values.put("classification", source.classification());
            changed(mapper.startCompletionOperation(values));
            var response = operationSchema.operation(new InterfaceProtocol.Operation(operationId, uploadId,
                InterfaceProtocol.OperationStatus.PROCESSING, "/api/operations/" + operationId), requestId);
            // 事务保存完整处理命令；接收回执保持PROCESSING，扫描和解析消费者负责后续结果。
            var command = json.object(); command.put("schema_version", "1.0.0"); command.put("operation_id", operationId);
            command.put("event_id", eventId); command.put("consumer_id", SourceUploadProtocol.COMPLETION_CONSUMER);
            command.put("upload_id", uploadId); command.put("source_revision", source.revision().toString());
            command.put("session_revision", completing.revision().toString()); command.put("request_sha256", hash);
            command.put("schema_ref", request.schemaRef()); command.put("schema_document_sha256", request.schemaSha256());
            command.put("schema_manifest_sha256", manifest); command.put("authority_sha256", authorization);
            command.put("subject_id", subject.user().user_id()); command.put("application_session_id", subject.sessionId());
            command.set("context", json.tree(admission.context()));
            var watermarks=json.object(); watermarks.put("auth_epoch",admission.watermarks().authEpoch().toString());
            watermarks.put("governance_epoch",admission.watermarks().governanceEpoch().toString()); command.set("watermarks",watermarks);
            command.set("request", request.content()); command.set("stored_parts", storedParts); command.set("response", response);
            schema.completionCommand(json.canonical(command));
            var audit = command.deepCopy(); audit.put("tenant_id", admission.context().tenant_id()); audit.put("project_id", source.projectId());
            changed(permissions.saveAudit(subject.user().user_id(), source.projectId(), auditId, "source.upload.completion.requested",
                json.canonical(audit), requestId, manifest, source.classification()));
            changed(permissions.saveEvent(subject.user().user_id(), eventId, uploadId, source.revision().toString(),
                "source.upload.completion.requested", json.canonical(command)));
            values.put("eventId", eventId); values.put("consumerId", SourceUploadProtocol.COMPLETION_CONSUMER);
            changed(mapper.saveCompletionDelivery(values));
            var delivery = one(mapper.completionDelivery(values));
            if (!"PENDING".equals(delivery.status()) || !BigInteger.ZERO.equals(delivery.attemptNo())
                    || !BigInteger.ZERO.equals(delivery.leaseGeneration()) || !BigInteger.ONE.equals(delivery.revision())
                    || delivery.leaseOwner() != null || delivery.leaseUntil() != null || delivery.lastError() != null
                    || !values.get("now").equals(delivery.nextAttemptAt()) || !values.get("now").equals(delivery.createdAt())
                    || !values.get("now").equals(delivery.updatedAt()) || !"1.0.0".equals(delivery.schemaVersion())
                    || !manifest.equals(delivery.schemaManifestSha256())) mismatch();
            values.put("auditId", auditId); values.put("projectId", source.projectId()); values.put("requestId", requestId);
            if (!json.canonical(audit).equals(json.canonical(schema.readJson(one(mapper.completionAudit(values)))))) mismatch();
            return completionReplay(admission, values, one(mapper.completionOperation(values)), request);
        }).get());
    }

    private JsonNode completionParts(EnterpriseAccess.Admission admission, String uploadId, SourceUploadSchema.ValidatedCompletion request) throws IOException {
        var parts = json.object().arrayNode(); long after = 0;
        while (true) {
            var page = uploads.parts(admission, uploadId, after, 100);
            if (page.isEmpty()) break;
            for (var row : page) {
                if (!"STORED".equals(row.state())) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
                if (parts.size() >= request.parts().size()) mismatch();
                var expected = request.parts().get(parts.size());
                if (row.partNo() != expected.partNo() || !row.sizeBytes().equals(expected.sizeBytes()) || !row.sha256().equals(expected.sha256())) mismatch();
                var object = UploadTransferObjects.decode(row.objectRef(), new InterfaceProtocol.ObjectRef(row.objectKey(), row.objectVersion(),
                    row.sha256(), row.sizeBytes().toString(), "application/octet-stream"));
                var part = parts.addObject(); part.put("part_no", row.partNo()); part.put("size_bytes", row.sizeBytes().toString());
                part.put("sha256", row.sha256()); part.put("write_id", row.writeId()); part.put("revision", row.revision().toString());
                part.set("object_ref", json.tree(object)); after = row.partNo();
            }
        }
        if (parts.size() != request.parts().size()) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        completionObjects.verify(parts,request.sizeBytes(),request.sha256(),"tenants/"+admission.context().tenant_id()
            +"/uploads/"+uploadId+"/parts",policy.maximumBytes().longValueExact());
        return parts;
    }

    private String completionRequestHash(String uploadId,SourceUploadSchema.ValidatedCompletion request) throws IOException {
        var identity=json.object(); identity.put("method","POST"); identity.put("path","/api/uploads/"+uploadId+"/completions");
        identity.putNull("query"); identity.put("schema_version","1.0.0"); identity.put("schema_ref",request.schemaRef());
        identity.put("schema_sha256",request.schemaSha256()); identity.set("body",request.content());
        return DigestUtils.sha256Hex(json.canonical(identity));
    }

    private JsonNode completionReplay(EnterpriseAccess.Admission admission, Map<String,Object> values, UploadOperationRow operation,
            SourceUploadSchema.ValidatedCompletion request) throws IOException {
        if (!values.get("requestSha256").equals(operation.requestSha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        String actor = admission.subject().user().user_id(), uploadId = (String) values.get("uploadId");
        boolean completed="COMPLETED".equals(operation.state());
        boolean failed="FAILED".equals(operation.state()),terminal=completed || failed;
        if (!admission.context().tenant_id().equals(operation.tenantId()) || !actor.equals(operation.subjectId()) || !actor.equals(operation.createdBy())
                || !"KN-03".equals(operation.operationName()) || !values.get("key").equals(operation.idempotencyKey()) || !uploadId.equals(operation.targetId())
                || !"1.0.0".equals(operation.schemaVersion()) || !manifest.equals(operation.schemaManifestSha256())
                || operation.createdAt() == null || operation.updatedAt()==null || operation.updatedAt().isBefore(operation.createdAt())
                || !failed && operation.errorCode()!=null || failed && !ErrorCode.PROCESSING_FAILED.name().equals(operation.errorCode())
                || operation.dedupExpiresAt() != null || operation.dedupPolicyRevision() != null
                || !terminal && (!"PROCESSING".equals(operation.state()) || !BigInteger.ONE.equals(operation.revision())
                    || !operation.createdAt().equals(operation.updatedAt()) || operation.httpStatus()!=null || operation.responseRef()!=null)
                || terminal && (!BigInteger.TWO.equals(operation.revision()) || !Integer.valueOf(202).equals(operation.httpStatus())
                    || operation.responseRef()==null)) mismatch();
        WireValues.uuid(operation.operationId()); values.put("operationId", operation.operationId());
        var source = one(transfers.source(values)); var session = uploads.session(admission, uploadId);
        if (!(completed?"COMPLETED":failed?"FAILED":"COMPLETING").equals(session.state())
                || !(completed?"RECEIVED":failed?"FAILED":"UPLOADING").equals(source.status())
                || !operation.classification().equals(source.classification())) mismatch();
        var command = schema.completionCommand(one(mapper.completionCommand(values)));
        if (!"1.0.0".equals(command.required("schema_version").textValue()) || !operation.operationId().equals(command.required("operation_id").textValue())
                || !operation.tenantId().equals(command.required("context").required("tenant_id").textValue())
                || !uploadId.equals(command.required("upload_id").textValue()) || !operation.requestSha256().equals(command.required("request_sha256").textValue())
                || !request.schemaRef().equals(command.required("schema_ref").textValue()) || !request.schemaSha256().equals(command.required("schema_document_sha256").textValue())
                || !manifest.equals(command.required("schema_manifest_sha256").textValue()) || !actor.equals(command.required("subject_id").textValue())
                || !source.revision().subtract(terminal?BigInteger.ONE:BigInteger.ZERO).toString().equals(command.required("source_revision").textValue())
                || !session.revision().subtract(terminal?BigInteger.ONE:BigInteger.ZERO).toString().equals(command.required("session_revision").textValue())
                || !request.canonicalJson().equals(json.canonical(command.required("request")))
                || !json.canonical(completionParts(admission, uploadId, request)).equals(json.canonical(command.required("stored_parts")))) mismatch();
        WireValues.hash(command.required("authority_sha256").textValue()); WireValues.uuid(command.required("application_session_id").textValue());
        String eventId = command.required("event_id").textValue(); WireValues.uuid(eventId);
        if (!SourceUploadProtocol.COMPLETION_CONSUMER.equals(command.required("consumer_id").textValue())) mismatch();
        values.put("eventId", eventId); values.put("consumerId", SourceUploadProtocol.COMPLETION_CONSUMER);
        values.put("resultManifest",schema.completionResultManifest());
        var delivery = one(mapper.completionDelivery(values));
        // 原请求重放核验持久身份；租约和投递结果由消费者独立维护。
        if (!operation.tenantId().equals(delivery.tenantId()) || !eventId.equals(delivery.eventId())
                || !SourceUploadProtocol.COMPLETION_CONSUMER.equals(delivery.consumerId()) || !actor.equals(delivery.createdBy())
                || !operation.createdAt().equals(delivery.createdAt()) || !operation.classification().equals(delivery.classification())
                || !"1.0.0".equals(delivery.schemaVersion()) || !manifest.equals(delivery.schemaManifestSha256())
                || delivery.revision() == null || delivery.revision().signum() <= 0
                || delivery.updatedAt() == null || delivery.updatedAt().isBefore(delivery.createdAt())) mismatch();
        var response = command.required("response"); WireValues.uuid(response.required("request_id").textValue());
        var expected = operationSchema.operation(new InterfaceProtocol.Operation(operation.operationId(), uploadId,
            InterfaceProtocol.OperationStatus.PROCESSING, "/api/operations/" + operation.operationId()), response.required("request_id").textValue());
        if (!json.canonical(expected).equals(json.canonical(response))) mismatch();
        if(completed) {
            var result=schema.completionResult(one(mapper.completionResult(values)));
            if(!"DELIVERED".equals(delivery.status()) || delivery.leaseOwner()!=null || delivery.leaseUntil()!=null
                    || !json.canonical(response).equals(json.canonical(schema.readJson(operation.responseRef())))
                    || !uploadId.equals(result.required("upload_id").textValue())
                    || !operation.operationId().equals(result.required("operation_id").textValue())
                    || !eventId.equals(result.required("event_id").textValue())
                    || !SourceUploadProtocol.COMPLETION_CONSUMER.equals(result.required("consumer_id").textValue())
                    || !DigestUtils.sha256Hex(json.canonical(command)).equals(result.required("input_sha256").textValue())
                    || !request.sha256().equals(source.actualSha256()) || source.objectRef()==null || session.objectRef()==null
                    || !json.canonical(result.required("original_object_ref")).equals(json.canonical(schema.readJson(source.objectRef())))
                    || !json.canonical(result.required("original_object_ref")).equals(json.canonical(schema.readJson(session.objectRef())))) mismatch();
        }
        if(failed) {
            var result=schema.completionResult(one(mapper.completionFailure(values)));
            if(!"FAILED".equals(delivery.status()) || delivery.leaseOwner()!=null || delivery.leaseUntil()!=null
                    || !json.canonical(response).equals(json.canonical(schema.readJson(operation.responseRef())))
                    || !"FAILED".equals(result.required("status").textValue())
                    || !ErrorCode.PROCESSING_FAILED.name().equals(result.required("error_code").textValue())
                    || !uploadId.equals(result.required("upload_id").textValue())
                    || !operation.operationId().equals(result.required("operation_id").textValue())
                    || !eventId.equals(result.required("event_id").textValue())
                    || !delivery.lastError().equals(result.required("reason").textValue())
                    || !SourceUploadProtocol.COMPLETION_CONSUMER.equals(result.required("consumer_id").textValue())
                    || !DigestUtils.sha256Hex(json.canonical(command)).equals(result.required("input_sha256").textValue())
                    || source.actualSha256()!=null || source.objectRef()!=null || session.objectRef()!=null) mismatch();
        }
        return schema.readJson(json.canonical(response));
    }

    @Override public String require(EnterpriseAccess.Admission admission, String uploadId, boolean writing) throws IOException {
        var values = values(admission); values.put("uploadId", uploadId);
        var declaration = one(mapper.declaration(values)); var request = stored(admission, declaration);
        var now = Objects.requireNonNull(permissions.databaseTime());
        var grant = requireAuthority(admission, request.content(), now);
        var session = uploads.session(admission, uploadId); var source = one(transfers.source(values));
        requireCorrespondence(declaration, request, source, session);
        if (writing && (!"ACTIVE".equals(session.state()) || !session.expiresAt().isAfter(now)))
            throw new TenantAccessException(ErrorCode.ACTION_STALE);
        var identity = json.object(); identity.put("schema_version", "1.0.0"); identity.put("upload_id", uploadId);
        identity.put("request_sha256", declaration.requestSha256()); identity.put("source_upload_manifest", manifest);
        identity.set("context", json.tree(admission.context())); identity.set("watermarks", json.tree(admission.watermarks()));
        identity.set("identity_binding", json.tree(admission.subject().binding().view()));
        identity.put("grant_id", grant.grantId()); identity.put("grant_revision", grant.revision().toString());
        identity.put("grant_subject_id", grant.subjectId()); identity.put("grant_project_id", grant.projectId());
        identity.put("grant_resource_kind", grant.resourceKind()); identity.put("grant_resource_id", grant.resourceId());
        identity.put("grant_action", grant.action()); identity.put("grant_condition", grant.condition()); identity.put("grant_status", grant.status());
        identity.put("grant_basis_version", grant.basisVersion()); identity.put("grant_schema_version", grant.schemaVersion());
        identity.put("grant_schema_manifest", grant.manifest());
        identity.put("grant_valid_from", time(grant.validFrom()));
        if (grant.validTo() == null) identity.putNull("grant_valid_to"); else identity.put("grant_valid_to", time(grant.validTo()));
        identity.put("subject_id", admission.subject().user().user_id()); identity.put("session_id", admission.subject().sessionId());
        return DigestUtils.sha256Hex(json.canonical(identity));
    }

    @Override public void stored(EnterpriseAccess.Admission admission, UploadTransferProtocol.SessionRow previous,
            UploadTransferProtocol.PartRow part) throws IOException {
        var values = values(admission); values.put("uploadId", previous.uploadId()); var before = one(transfers.source(values));
        var session = uploads.session(admission, previous.uploadId());
        if (!before.receivedSize().equals(previous.receivedSize()) || !session.receivedSize().equals(previous.receivedSize().add(part.sizeBytes()))
                || !java.util.Set.of("PENDING", "UPLOADING").contains(before.status())) mismatch();
        values.put("revision", before.revision()); values.put("previousSize", before.receivedSize());
        values.put("receivedSize", session.receivedSize()); changed(mapper.receive(values));
        var declaration = one(mapper.declaration(values));
        var after = one(transfers.source(values)); requireCorrespondence(declaration, stored(admission, declaration), after, session);
        if (!after.revision().equals(before.revision().add(BigInteger.ONE)) || !"UPLOADING".equals(after.status())) mismatch();
    }

    record PartRequest(String context, String key, String uploadId, long partNo, String sha256, long sizeBytes) { }

    String startPart(ApplicationSessionIdentity.Verified subject, PartRequest request) {
        json.headers(request.context(), request.key()); WireValues.uuid(request.uploadId()); WireValues.hash(request.sha256());
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            context(admission, request.context());
            var part = uploads.partIfPresent(admission, request.uploadId(), request.partNo()).orElse(null);
            require(admission, request.uploadId(), part == null || !"STORED".equals(part.state()));
            var session = uploads.session(admission, request.uploadId());
            if (UploadTransferStore.range(session.expectedSize(), request.partNo()).size().longValueExact() != request.sizeBytes())
                throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            if (part != null && !request.sha256().equals(part.sha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            var values = partValues(admission, request); var previous = mapper.partOperation(values);
            if (!previous.isEmpty()) { requirePartOperation(admission, request, one(previous)); return admission.context().tenant_id(); }
            var source = one(transfers.source(values)); values.put("operationId", id()); values.put("now", permissions.databaseTime());
            values.put("classification", source.classification()); changed(mapper.startPartOperation(values));
            requirePartOperation(admission, request, one(mapper.partOperation(values))); return admission.context().tenant_id();
        }).get());
    }

    record PartReplay(int status, JsonNode body) { }

    PartReplay partReplay(ApplicationSessionIdentity.Verified subject, PartRequest request) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            context(admission, request.context());
            var part = uploads.partIfPresent(admission, request.uploadId(), request.partNo()).orElse(null);
            require(admission, request.uploadId(), part == null || !"STORED".equals(part.state()));
            var operation = one(mapper.partOperation(partValues(admission, request))); requirePartOperation(admission, request, operation);
            if ("COMPLETED".equals(operation.state())) return new PartReplay(200, completePart(admission, request));
            return new PartReplay(202, operationSchema.operation(new InterfaceProtocol.Operation(operation.operationId(), operation.targetId(),
                InterfaceProtocol.OperationStatus.valueOf(operation.state()), "/api/operations/" + operation.operationId()), id()));
        }).get());
    }

    UploadPartReceiver.Authority partAuthority(PartRequest request, S3ObjectReader.Admission receiving) {
        Objects.requireNonNull(receiving);
        return new UploadPartReceiver.Authority() {
            @Override public String require(EnterpriseAccess.Admission admission, String uploadId, boolean writing) throws IOException {
                receiving.requireCurrent();
                if (!request.uploadId().equals(uploadId)) mismatch();
                context(admission, request.context());
                String permission = SourceUploadService.this.require(admission, uploadId, writing);
                var operation = one(mapper.partOperation(partValues(admission, request)));
                requirePartOperation(admission, request, operation);
                receiving.requireCurrent();
                return permission + ":" + operation.operationId() + ":" + operation.requestSha256();
            }
            @Override public void stored(EnterpriseAccess.Admission admission, UploadTransferProtocol.SessionRow previous,
                    UploadTransferProtocol.PartRow saved) throws IOException {
                SourceUploadService.this.stored(admission, previous, saved);
                completePart(admission, request);
            }
        };
    }

    JsonNode partReceipt(ApplicationSessionIdentity.Verified subject, PartRequest request) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            context(admission, request.context()); require(admission, request.uploadId(), false);
            return completePart(admission, request);
        }).get());
    }

    private JsonNode completePart(EnterpriseAccess.Admission admission, PartRequest request) throws IOException {
        context(admission, request.context()); require(admission, request.uploadId(), false);
        var values = partValues(admission, request); var operation = one(mapper.partOperation(values));
        requirePartOperation(admission, request, operation);
        var part = uploads.part(admission, request.uploadId(), request.partNo());
        if (!"STORED".equals(part.state())) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        if (!request.sha256().equals(part.sha256()) || part.sizeBytes().longValueExact() != request.sizeBytes()) mismatch();
        var data = json.object(); data.put("part_no", request.partNo()); data.put("size_bytes", part.sizeBytes().toString());
        data.put("sha256", part.sha256()); data.put("state", part.state());
        if ("COMPLETED".equals(operation.state())) {
            var response = schema.readJson(operation.responseRef()); shared.validate("interfaces", response, "Envelope");
            schema.verify("Part", response.required("data")); WireValues.uuid(response.required("request_id").textValue());
            if (!json.canonical(data).equals(json.canonical(response.required("data")))
                    || !part.revision().toString().equals(response.required("resource_revision").textValue())) mismatch();
            return schema.readJson(json.canonical(response));
        }
        var source = one(transfers.source(values)); String requestId = id();
        var response = envelope("Part", data, requestId, part.revision().toString());
        values.put("operationId", operation.operationId()); values.put("response", json.canonical(response));
        changed(mapper.completePartOperation(values));
        var completed = one(mapper.partOperation(values)); requirePartOperation(admission, request, completed);
        if (!"COMPLETED".equals(completed.state()) || !json.canonical(response).equals(json.canonical(schema.readJson(completed.responseRef())))) mismatch();
        String actor = admission.subject().user().user_id(), auditId = id(), eventId = id();
        var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operation.operationId());
        audit.put("tenant_id", source.tenantId()); audit.put("project_id", source.projectId()); audit.put("subject_id", actor);
        audit.put("upload_id", request.uploadId()); audit.put("part_no", request.partNo()); audit.put("request_sha256", operation.requestSha256());
        audit.put("context_revision", admission.context().context_revision()); audit.put("auth_epoch", admission.watermarks().authEpoch().toString());
        audit.put("governance_epoch", admission.watermarks().governanceEpoch().toString());
        audit.put("authority_sha256", require(admission, request.uploadId(), false)); audit.set("response", response);
        var event = json.object(); event.put("schema_version", "1.0.0"); event.put("operation_id", operation.operationId());
        event.put("upload_id", request.uploadId()); event.put("part_no", request.partNo()); event.put("revision", source.revision().toString());
        event.put("size_bytes", part.sizeBytes().toString()); event.put("sha256", part.sha256());
        changed(permissions.saveAudit(actor, source.projectId(), auditId, "source.upload.part.stored", json.canonical(audit), requestId, manifest, source.classification()));
        changed(permissions.saveEvent(actor, eventId, request.uploadId(), source.revision().toString(), "source.upload.part.stored", json.canonical(event)));
        values.put("auditId", auditId); values.put("eventId", eventId); values.put("requestId", requestId); values.put("projectId", source.projectId());
        values.put("classification", source.classification()); values.put("sourceRevision", source.revision());
        if (!json.canonical(audit).equals(json.canonical(schema.readJson(one(mapper.partAudit(values)))))
                || !json.canonical(event).equals(json.canonical(schema.readJson(one(mapper.partEvent(values)))))) mismatch();
        return schema.readJson(completed.responseRef());
    }

    private Map<String,Object> partValues(EnterpriseAccess.Admission admission, PartRequest request) throws IOException {
        var identity = json.object(); identity.put("method", "PUT"); identity.put("path", "/api/uploads/" + request.uploadId() + "/parts/" + request.partNo());
        identity.putNull("query"); identity.put("schema_version", "1.0.0"); identity.put("schema_sha256", schema.schemaSha256());
        identity.put("upload_id", request.uploadId()); identity.put("part_no", request.partNo());
        identity.put("size_bytes", Long.toString(request.sizeBytes())); identity.put("content_sha256", request.sha256());
        var values = values(admission); values.put("key", request.key()); values.put("uploadId", request.uploadId());
        values.put("requestSha256", DigestUtils.sha256Hex(json.canonical(identity))); values.put("manifest", manifest); return values;
    }

    private void requirePartOperation(EnterpriseAccess.Admission admission, PartRequest request, UploadOperationRow operation) throws IOException {
        var values = partValues(admission, request);
        if (!values.get("requestSha256").equals(operation.requestSha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        String actor = admission.subject().user().user_id();
        if (!admission.context().tenant_id().equals(operation.tenantId()) || !actor.equals(operation.subjectId()) || !actor.equals(operation.createdBy())
                || !"KN-02".equals(operation.operationName()) || !request.key().equals(operation.idempotencyKey()) || !request.uploadId().equals(operation.targetId())
                || !"1.0.0".equals(operation.schemaVersion()) || !manifest.equals(operation.schemaManifestSha256())
                || operation.createdAt() == null || operation.updatedAt() == null || operation.updatedAt().isBefore(operation.createdAt())
                || operation.errorCode() != null || operation.dedupExpiresAt() != null || operation.dedupPolicyRevision() != null) mismatch();
        WireValues.uuid(operation.operationId());
        if ("PROCESSING".equals(operation.state())) {
            if (!BigInteger.ONE.equals(operation.revision()) || operation.httpStatus() != null || operation.responseRef() != null
                    || !operation.createdAt().equals(operation.updatedAt())) mismatch();
        } else if ("COMPLETED".equals(operation.state())) {
            if (!BigInteger.TWO.equals(operation.revision()) || !Integer.valueOf(200).equals(operation.httpStatus()) || operation.responseRef() == null) mismatch();
        } else throw new TenantAccessException(ErrorCode.ACTION_STALE);
        var source = one(transfers.source(values));
        if (!source.classification().equals(operation.classification())) mismatch();
    }

    private JsonNode replay(EnterpriseAccess.Admission admission, Map<String,Object> values, UploadOperationRow saved,
            SourceUploadSchema.ValidatedCreate request, String hash) throws IOException {
        if (!hash.equals(saved.requestSha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        String actor = admission.subject().user().user_id();
        if (!admission.context().tenant_id().equals(saved.tenantId()) || !actor.equals(saved.subjectId()) || !actor.equals(saved.createdBy())
                || !"KN-01".equals(saved.operationName()) || !values.get("key").equals(saved.idempotencyKey())
                || !"COMPLETED".equals(saved.state()) || !Integer.valueOf(201).equals(saved.httpStatus()) || saved.errorCode() != null
                || !"1.0.0".equals(saved.schemaVersion()) || !manifest.equals(saved.schemaManifestSha256())
                || !BigInteger.ONE.equals(saved.revision()) || saved.createdAt() == null || !saved.createdAt().equals(saved.updatedAt())
                || saved.dedupExpiresAt() != null || saved.dedupPolicyRevision() != null) mismatch();
        WireValues.uuid(saved.operationId()); WireValues.uuid(saved.targetId()); values.put("uploadId", saved.targetId());
        var declaration = one(mapper.declaration(values)); var stored = stored(admission, declaration);
        if (!request.canonicalJson().equals(stored.canonicalJson()) || !saved.operationId().equals(declaration.operationId())
                || !saved.classification().equals(declaration.classification())) mismatch();
        var session = uploads.session(admission, saved.targetId()); requireCorrespondence(declaration, stored, one(transfers.source(values)), session);
        var response = schema.readJson(saved.responseRef()); shared.validate("interfaces", response, "Envelope"); schema.verify("Created", response.required("data"));
        WireValues.uuid(response.required("request_id").textValue());
        if (!saved.targetId().equals(response.required("data").required("upload_id").textValue())
                || !time(session.expiresAt()).equals(response.required("data").required("expires_at").textValue())
                || !"1".equals(response.required("resource_revision").textValue())) mismatch();
        return schema.readJson(json.canonical(response));
    }

    private PermissionProtocol.GrantRow requireAuthority(EnterpriseAccess.Admission admission, JsonNode body, LocalDateTime now) throws IOException {
        return requireAuthority(admission.context().tenant_id(),admission.subject().user().user_id(),admission.subject().binding().view().mode(),body,now);
    }

    PermissionProtocol.GrantRow requireAuthority(String tenant,String actor,String mode,JsonNode body,LocalDateTime now) throws IOException {
        Objects.requireNonNull(now); String project = body.required("project_id").textValue();
        if (!"ACTIVE".equals(one(permissions.projectStatus(project)))) denied();
        var values = new HashMap<String,Object>(); values.put("userId",actor); values.put("projectId", project); values.put("sourceId", nullable(body, "source_id"));
        values.put("sessionId", nullable(body, "session_id"));
        String ownership = body.required("ownership").textValue(), source = nullable(body, "source_id");
        if ("SESSION".equals(ownership) && !Objects.equals(values.get("sessionId"), one(mapper.conversation(values)))) denied();
        String kind = "ENTERPRISE".equals(ownership) ? "ENTERPRISE" : "PROJECT";
        String resource = "ENTERPRISE".equals(ownership) ? tenant : project, ownerProject = "ENTERPRISE".equals(ownership) ? null : project;
        if ("NEW_VERSION".equals(body.required("version_intent").textValue())) {
            var existing = one(mapper.sourceOwner(values));
            if (!source.equals(existing.sourceId()) || !ownership.equals(existing.ownership())
                    || !Objects.equals(ownerProject, existing.ownerProjectId())
                    || !body.required("classification").textValue().equals(existing.classification())) denied();
            if ("SESSION".equals(ownership) && !Objects.equals(values.get("sessionId"), one(mapper.sourceSession(values)))) denied();
            kind = "SOURCE"; resource = source;
        }
        return authority.requireResource(new PermissionProtocol.Scope(tenant, ownerProject, kind, resource),
            actor, mode, "source.write", "SOURCE_UPLOAD",permissions.resourceGrants(actor, "source.write"), now);
    }

    void maintainer(String user, LocalDateTime now) {
        var member = one(permissions.targetMember(user));
        if (!"ACTIVE".equals(member.status()) || member.validFrom() == null || member.validFrom().isAfter(now)
                || member.validTo() != null && !member.validTo().isAfter(now)) denied();
    }

    private SourceUploadSchema.ValidatedCreate stored(EnterpriseAccess.Admission admission, SourceUploadProtocol.Declaration saved) throws IOException {
        return storedDeclaration(admission.context().tenant_id(),admission.subject().user().user_id(),saved);
    }

    SourceUploadSchema.ValidatedCreate storedDeclaration(String tenantId,String actorId,SourceUploadProtocol.Declaration saved) throws IOException {
        var request = schema.create(saved.requestJson().getBytes(StandardCharsets.UTF_8), policy.maximumBytes()); JsonNode body = request.content();
        WireValues.uuid(saved.operationId()); WireValues.uuid(saved.authorityGrant()); WireValues.revision(saved.authorityRevision().toString());
        if (saved.authorityRevision().signum() <= 0 || saved.createdAt() == null || saved.contextRevision() == null || saved.authEpoch() == null
                || saved.governanceEpoch() == null || !tenantId.equals(saved.tenantId())
                || !actorId.equals(saved.createdBy()) || !saved.projectId().equals(body.required("project_id").textValue())
                || !Objects.equals(saved.sourceId(), nullable(body, "source_id")) || !saved.versionIntent().equals(body.required("version_intent").textValue())
                || !saved.maintainerId().equals(body.required("provenance").required("maintainer_id").textValue())
                || !requestHash(request).equals(saved.requestSha256()) || !request.schemaRef().equals(saved.schemaRef())
                || !request.schemaSha256().equals(saved.schemaDocumentSha256()) || !"1.0.0".equals(saved.schemaVersion())
                || !manifest.equals(saved.schemaManifestSha256()) || !saved.classification().equals(body.required("classification").textValue())) mismatch();
        WireValues.revision(saved.contextRevision().toString()); WireValues.revision(saved.authEpoch().toString()); WireValues.revision(saved.governanceEpoch().toString());
        return request;
    }

    void requireCorrespondence(SourceUploadProtocol.Declaration declaration, SourceUploadSchema.ValidatedCreate request,
            UploadTransferProtocol.SourceRow source, UploadTransferProtocol.SessionRow session) {
        JsonNode body = request.content();
        if (!declaration.uploadId().equals(source.uploadId()) || !source.uploadId().equals(session.uploadId()) || !"SOURCE".equals(session.purpose())
                || !declaration.projectId().equals(source.projectId()) || !body.required("batch_id").textValue().equals(source.batchId())
                || !Objects.equals(nullable(body, "session_id"), source.sessionId()) || !body.required("ownership").textValue().equals(source.ownership())
                || !body.required("file_name").textValue().equals(source.fileName()) || !request.sizeBytes().equals(source.expectedSize())
                || !Objects.equals(nullable(body, "sha256"), source.expectedSha256()) || !source.receivedSize().equals(session.receivedSize())
                || !body.required("media_type").textValue().equals(session.mediaType()) || !declaration.classification().equals(source.classification())
                || !declaration.createdBy().equals(source.createdBy()) || !declaration.createdAt().equals(source.createdAt())
                || !manifest.equals(source.schemaManifestSha256()) || !manifest.equals(session.schemaManifestSha256())) mismatch();
    }

    private String requestHash(SourceUploadSchema.ValidatedCreate request) throws IOException {
        var value = json.object(); value.put("method", "POST"); value.put("path", "/api/uploads"); value.putNull("query");
        value.put("schema_version", "1.0.0"); value.put("schema_ref", request.schemaRef()); value.put("schema_sha256", request.schemaSha256());
        value.set("body", request.content()); return DigestUtils.sha256Hex(json.canonical(value));
    }
    private JsonNode envelope(String kind, JsonNode data, String requestId, String revision) {
        schema.verify(kind, data); WireValues.revision(revision);
        var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, data)); shared.validate("interfaces", response, "Envelope"); return response;
    }
    private SourceUploadProtocol.Declaration normalized(SourceUploadProtocol.Declaration row) throws IOException {
        return new SourceUploadProtocol.Declaration(row.tenantId(), row.projectId(), row.uploadId(), row.operationId(), row.sourceId(), row.versionIntent(),
            row.maintainerId(), json.canonical(schema.readJson(row.requestJson())), row.requestSha256(), row.schemaRef(), row.schemaDocumentSha256(), row.authorityGrant(),
            row.authorityRevision(), row.contextRevision(), row.authEpoch(), row.governanceEpoch(), row.schemaVersion(), row.schemaManifestSha256(), row.createdBy(), row.createdAt(), row.classification());
    }
    private UploadOperationRow normalized(UploadOperationRow row) throws IOException {
        return new UploadOperationRow(row.tenantId(), row.operationId(), row.subjectId(), row.operationName(), row.idempotencyKey(), row.requestSha256(), row.state(),
            row.httpStatus(), json.canonical(schema.readJson(row.responseRef())), row.targetId(), row.errorCode(), row.schemaVersion(), row.schemaManifestSha256(), row.createdBy(),
            row.createdAt(), row.classification(), row.revision(), row.updatedAt(), row.dedupExpiresAt(), row.dedupPolicyRevision());
    }
    private static Map<String,Object> values(EnterpriseAccess.Admission admission) { var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); return values; }
    private static void context(EnterpriseAccess.Admission admission, String context) { if (!context.equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED); }
    private static String nullable(JsonNode value, String field) { var node = value.get(field); return node == null || node.isNull() ? null : node.textValue(); }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static <T> T one(List<T> rows) { if (rows.size() != 1) denied(); return Objects.requireNonNull(rows.get(0)); }
    private static void changed(int count) { if (count != 1) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
