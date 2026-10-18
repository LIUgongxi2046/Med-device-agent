package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SimulationRuleVersionMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class SimulationRuleVersionService {
    private final EnterpriseAccess access;
    private final SimulationRuleVersionMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;
    private final SimulationRuleDependencies dependencies;
    private final SimulationRuleVersionSchema schema;
    private final SimulationRuleVersionStore store;
    private final PermissionSchema json;
    private final IdentityDirectoryPolicy policy;

    public SimulationRuleVersionService(EnterpriseAccess access, SimulationRuleVersionMapper mapper, PermissionMapper permissions,
            ResourceReadAuthority authority, SimulationRuleDependencies dependencies, SimulationRuleVersionSchema schema,
            SimulationRuleVersionStore store, PermissionSchema json, IdentityDirectoryPolicy policy) {
        this.access = access; this.mapper = mapper; this.permissions = permissions; this.authority = authority;
        this.dependencies = dependencies; this.schema = schema; this.store = store; this.json = json; this.policy = policy;
    }

    public JsonNode create(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] body) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            var witness = authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null,
                    "ENTERPRISE", admission.context().tenant_id()), "rule.write", TemplateDependencyAuthority.PURPOSE);
            var request = schema.version(body); var values = values(admission, key, schema.hash(null, request.request(), context, subject.sessionId()));
            var prior = mapper.currentOperation(values);
            if (prior.size() > 1) mismatch();
            if (!prior.isEmpty() && !prior.get(0).requestSha256().equals(values.get("requestHash")))
                throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            var proofs = dependencies.requireRule(admission, request);
            if (!prior.isEmpty()) {
                var operation = prior.get(0);
                if (!operation.state().equals("COMPLETED") || !Objects.equals(operation.httpStatus(), 201)
                        || !operation.schemaVersion().equals("1.0.0") || !operation.schemaManifestSha256().equals(schema.manifestSha256())) mismatch();
                var original = schema.creationReceipt(operation.responseRef()); var receipt = original.required("data");
                if (!receipt.required("operation_id").textValue().equals(operation.operationId())
                        || !receipt.required("rule").required("rule_version").textValue().equals(operation.targetId())) mismatch();
                var actual = json.tree(store.originalCreation(admission, operation.targetId(), request));
                var recorded = receipt.required("rule").deepCopy();
                // 状态和治理修订可以推进，原创建内容与原响应继续保持固定身份。
                ((ObjectNode) actual).remove(List.of("status", "revision")); ((ObjectNode) recorded).remove(List.of("status", "revision"));
                if (!actual.equals(recorded)) mismatch();
                values.put("operationId", operation.operationId()); values.put("ruleId", operation.targetId());
                values.put("requestId", original.required("request_id").textValue());
                var audit = schema.read(single(mapper.auditForOperation(values)));
                var event = schema.read(single(mapper.eventForOperation(values)));
                if (!audit.required("request").equals(json.tree(request.request())) || !audit.required("after").equals(receipt)
                        || !audit.required("operation_id").textValue().equals(operation.operationId())
                        || !event.required("creation").equals(receipt)
                        || !event.required("dependency_authorizations").equals(audit.required("dependency_authorizations"))) mismatch();
                return original;
            }
            if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var stored = store.save(admission, request);
            if (!stored.authority_grant_id().equals(witness.grantId()) || !stored.authority_revision().equals(witness.revision().toString())) mismatch();
            String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
            var response = schema.created(stored, operationId, requestId);
            values.put("operationId", operationId); values.put("ruleId", stored.rule_version()); values.put("response", json.canonical(response));
            one(mapper.saveOperation(values));
            var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operationId);
            audit.set("request", json.tree(request.request())); audit.putNull("before"); audit.set("after", response.required("data"));
            audit.put("authority_grant_id", witness.grantId()); audit.put("authority_revision", witness.revision().toString());
            var authorization = dependencies.snapshot(admission, proofs, json); audit.set("dependency_authorizations", authorization);
            var payload = json.object(); payload.put("schema_version", "1.0.0"); payload.set("creation", response.required("data"));
            payload.set("dependency_authorizations", authorization);
            String auditId = UUID.randomUUID().toString(), eventId = UUID.randomUUID().toString();
            one(permissions.saveAudit(subject.user().user_id(), null, auditId, "simulation.rule.draft_created", json.canonical(audit),
                    requestId, schema.manifestSha256(), policy.classification()));
            one(permissions.saveEvent(subject.user().user_id(), eventId, stored.rule_version(), stored.revision(),
                    "simulation.rule.draft_created", json.canonical(payload)));
            values.put("auditId", auditId); values.put("eventId", eventId); values.put("requestId", requestId);
            if (!schema.read(single(mapper.audit(values))).equals(audit)
                    || !schema.read(single(mapper.event(values))).equals(payload)) mismatch();
            var operation = single(mapper.currentOperation(values));
            if (!operation.operationId().equals(operationId) || !operation.requestSha256().equals(values.get("requestHash"))
                    || !operation.targetId().equals(stored.rule_version()) || !schema.creationReceipt(operation.responseRef()).equals(response)) mismatch();
            store.originalCreation(admission, stored.rule_version(), request);
            return response;
        }).get());
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String id) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            return schema.readView(store.read(admission, id), UUID.randomUUID().toString());
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
