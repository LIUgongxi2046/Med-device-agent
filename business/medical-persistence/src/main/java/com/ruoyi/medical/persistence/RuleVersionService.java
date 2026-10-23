package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.RuleVersionMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class RuleVersionService {
    private final EnterpriseAccess access;
    private final RuleVersionMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;
    private final TemplateDependencyAuthority dependencies;
    private final RuleVersionSchema schema;
    private final RuleVersionStore store;
    private final PermissionSchema json;
    private final IdentityDirectoryPolicy policy;
    private final RuleBusinessDefinitionStore business;

    public RuleVersionService(EnterpriseAccess access, RuleVersionMapper mapper, PermissionMapper permissions,
            ResourceReadAuthority authority, TemplateDependencyAuthority dependencies, RuleVersionSchema schema,
            RuleVersionStore store, PermissionSchema json, IdentityDirectoryPolicy policy, RuleBusinessDefinitionStore business) {
        this.access = access; this.mapper = mapper; this.permissions = permissions; this.authority = authority;
        this.dependencies = dependencies; this.schema = schema; this.store = store; this.json = json; this.policy = policy;this.business=business;
    }

    public JsonNode create(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] body) {
        return create(subject,context,key,body,false);
    }
    public JsonNode createBusiness(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] body){
        return create(subject,context,key,body,true);
    }
    private JsonNode create(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] body,boolean businessRequest){
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            var witness = authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null,
                    "ENTERPRISE", admission.context().tenant_id()), "rule.write", TemplateDependencyAuthority.PURPOSE);
            var combined=businessRequest?business.createInput(body):null;
            var definition=combined==null?null:combined.required("definition");
            var request = schema.version(combined==null?body:json.canonical(combined.required("rule")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Object completeRequest=definition==null?request.request():Map.of("rule",request.request(),"business_definition",definition,"business_schema_sha256",business.manifest());
            var values = values(admission, key, schema.hash(null, completeRequest, context, subject.sessionId()));
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
                if(definition!=null){var businessRecord=business.read(admission,store.originalCreation(admission,operation.targetId(),request));if(businessRecord==null||!businessRecord.required("definition").equals(definition)||!businessRecord.equals(audit.required("business_definition"))||!businessRecord.equals(event.required("business_definition")))mismatch();}
                return original;
            }
            if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var stored = store.save(admission, request);
            if (!stored.authority_grant_id().equals(witness.grantId()) || !stored.authority_revision().equals(witness.revision().toString())) mismatch();
            String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
            var response = schema.created(stored, operationId, requestId);
            values.put("operationId", operationId); values.put("ruleId", stored.rule_version()); values.put("response", json.canonical(response));
            one(mapper.saveOperation(values));
            var businessRecord=definition==null?null:business.save(admission,stored,definition,operationId);
            var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operationId);
            audit.set("request", json.tree(request.request())); audit.putNull("before"); audit.set("after", response.required("data"));
            audit.put("authority_grant_id", witness.grantId()); audit.put("authority_revision", witness.revision().toString());
            var authorization = dependencies.snapshot(admission, proofs, json); audit.set("dependency_authorizations", authorization);
            var payload = json.object(); payload.put("schema_version", "1.0.0"); payload.set("creation", response.required("data"));
            payload.set("dependency_authorizations", authorization);
            if(businessRecord!=null){audit.set("business_definition",businessRecord);payload.set("business_definition",businessRecord);}
            String auditId = UUID.randomUUID().toString(), eventId = UUID.randomUUID().toString();
            one(permissions.saveAudit(subject.user().user_id(), null, auditId, "rule.draft_created", json.canonical(audit),
                    requestId, schema.manifestSha256(), policy.classification()));
            one(permissions.saveEvent(subject.user().user_id(), eventId, stored.rule_version(), stored.revision(),
                    "rule.draft_created", json.canonical(payload)));
            values.put("auditId", auditId); values.put("eventId", eventId); values.put("requestId", requestId);
            if (!schema.same(schema.read(single(mapper.audit(values))), audit)
                    || !schema.same(schema.read(single(mapper.event(values))), payload)) mismatch();
            var operation = single(mapper.currentOperation(values));
            if (!operation.operationId().equals(operationId) || !operation.requestSha256().equals(values.get("requestHash"))
                    || !operation.targetId().equals(stored.rule_version()) || !schema.creationReceipt(operation.responseRef()).equals(response)) mismatch();
            store.originalCreation(admission, stored.rule_version(), request);
            if(businessRecord!=null&&!businessRecord.equals(business.read(admission,stored)))mismatch();
            return response;
        }).get());
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String id) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            return schema.readView(store.read(admission, id), UUID.randomUUID().toString());
        }).get());
    }
    public JsonNode readBusiness(ApplicationSessionIdentity.Verified subject,String context,String id){
        return access.execute(subject,admission->ThrowingSupplier.of(()->{
            if(!admission.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var scope=new PermissionProtocol.Scope(admission.context().tenant_id(),null,"RULE",id);
            var rule=authority.find(admission,scope,"rule.read",TemplateDependencyAuthority.PURPOSE).isPresent()?store.read(admission,id):store.approvalTarget(admission,id);var definition=business.read(admission,rule);
            return json.tree(new com.ruoyi.medical.contracts.InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),rule.revision(),json.tree(definition)));
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
