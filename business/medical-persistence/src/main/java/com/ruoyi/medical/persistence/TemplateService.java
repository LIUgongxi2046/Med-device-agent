package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class TemplateService {
    private final EnterpriseAccess access;
    private final TemplateMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;
    private final TemplateDependencyAuthority dependencies;
    private final TemplateDefinitionSchema definitions;
    private final TemplateOperationSchema schema;
    private final TemplateVersionStore store;
    private final PermissionSchema json;
    private final IdentityDirectoryPolicy policy;

    public TemplateService(EnterpriseAccess access, TemplateMapper mapper, PermissionMapper permissions,
            ResourceReadAuthority authority, TemplateDependencyAuthority dependencies, TemplateDefinitionSchema definitions,
            TemplateOperationSchema schema, TemplateVersionStore store, PermissionSchema json, IdentityDirectoryPolicy policy) {
        this.access = access; this.mapper = mapper; this.permissions = permissions; this.authority = authority;
        this.dependencies = dependencies; this.definitions = definitions; this.schema = schema; this.store = store; this.json = json; this.policy = policy;
    }

    public JsonNode create(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] body) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var witness = authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null,
                    "ENTERPRISE", admission.context().tenant_id()), "template.write", TemplateDependencyAuthority.PURPOSE);
            var request = definitions.version(body); var values = values(admission, key, schema.hash(request.request(), context, subject.sessionId()));
            var prior = mapper.currentOperation(values);
            if (prior.size() > 1) mismatch();
            if (!prior.isEmpty() && !prior.get(0).requestSha256().equals(values.get("requestHash")))
                throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            var proofs = dependencies.require(admission, request);
            if (!prior.isEmpty()) {
                var operation = prior.get(0);
                if (!operation.state().equals("COMPLETED") || !Objects.equals(operation.httpStatus(), 201)
                        || !operation.schemaVersion().equals("1.0.0") || !operation.schemaManifestSha256().equals(schema.manifestSha256())) mismatch();
                var original = schema.receipt(operation.responseRef()); var receipt = original.required("data");
                if (!receipt.required("operation_id").textValue().equals(operation.operationId())
                        || !receipt.required("template").required("template_version").textValue().equals(operation.targetId())) mismatch();
                var actual = json.tree(store.originalCreation(admission, operation.targetId(), request, proofs));
                var recorded = receipt.required("template").deepCopy();
                // 状态和治理修订可以推进，原创建内容与原响应继续保持固定身份。
                ((ObjectNode) actual).remove(List.of("status", "revision")); ((ObjectNode) recorded).remove(List.of("status", "revision"));
                if (!actual.equals(recorded)) mismatch();
                return original;
            }
            var stored = store.save(admission, request, proofs);
            if (!stored.authority_grant_id().equals(witness.grantId()) || !stored.authority_revision().equals(witness.revision().toString())) mismatch();
            String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
            var response = schema.created(stored, operationId, requestId);
            values.put("operationId", operationId); values.put("templateId", stored.template_version()); values.put("response", json.canonical(response));
            one(mapper.saveOperation(values));
            var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operationId);
            audit.set("request", json.tree(request.request())); audit.putNull("before"); audit.set("after", response.required("data"));
            audit.put("authority_grant_id", witness.grantId()); audit.put("authority_revision", witness.revision().toString());
            var authorization = dependencies.snapshot(admission, proofs, json); audit.set("dependency_authorizations", authorization);
            var payload = json.object(); payload.put("schema_version", "1.0.0"); payload.set("creation", response.required("data"));
            payload.set("dependency_authorizations", authorization);
            String auditId = UUID.randomUUID().toString(), eventId = UUID.randomUUID().toString();
            one(permissions.saveAudit(subject.user().user_id(), null, auditId, "template.draft_created", json.canonical(audit),
                    requestId, schema.manifestSha256(), policy.classification()));
            one(permissions.saveEvent(subject.user().user_id(), eventId, stored.template_version(), stored.revision(),
                    "template.draft_created", json.canonical(payload)));
            values.put("auditId", auditId); values.put("eventId", eventId); values.put("requestId", requestId);
            if (!schema.read(single(mapper.audit(values))).equals(audit)
                    || !schema.read(single(mapper.event(values))).equals(payload)) mismatch();
            var operation = single(mapper.currentOperation(values));
            if (!operation.operationId().equals(operationId) || !operation.requestSha256().equals(values.get("requestHash"))
                    || !operation.targetId().equals(stored.template_version()) || !schema.receipt(operation.responseRef()).equals(response)) mismatch();
            store.originalCreation(admission, stored.template_version(), request, proofs);
            return response;
        }).get());
    }

    private Map<String,Object> values(EnterpriseAccess.Admission admission, String key, String hash) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); values.put("key", key);
        values.put("requestHash", hash); values.put("operationManifest", schema.manifestSha256());
        values.put("classification", policy.classification()); values.put("now", Objects.requireNonNull(permissions.databaseTime())); return values;
    }
    private static <T> T single(List<T> rows) { if (rows.size() != 1) mismatch(); return rows.get(0); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
