package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.RuleVersionMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class RuleVersionApprovalService {
    private final EnterpriseAccess access;
    private final RuleVersionMapper mapper;
    private final RuleVersionStore store;
    private final TemplateDependencyAuthority dependencies;
    private final RuleVersionSchema schema;
    private final ResourceReadAuthority authority;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final IdentityDirectoryPolicy policy;
    private final JdbcTemplate jdbc;
    private final RuleBusinessDefinitionStore business;

    public RuleVersionApprovalService(EnterpriseAccess access, RuleVersionMapper mapper, RuleVersionStore store,
            TemplateDependencyAuthority dependencies, RuleVersionSchema schema,
            ResourceReadAuthority authority, PermissionMapper permissions, PermissionSchema json, IdentityDirectoryPolicy policy,
            @Qualifier("masterDataSource") DataSource master,RuleBusinessDefinitionStore business) {
        this.access = access; this.mapper = mapper; this.store = store; this.dependencies = dependencies;
        this.schema = schema; this.authority = authority; this.permissions = permissions;
        this.json = json; this.policy = policy; jdbc = new JdbcTemplate(master);this.business=business;
    }

    public JsonNode baseline(ApplicationSessionIdentity.Verified subject, String context, String id) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(id); var prepared = prepare(admission, id);
            return schema.preview(prepared.stored(), prepared.baseline(), UUID.randomUUID().toString());
        }).get());
    }

    public JsonNode decide(ApplicationSessionIdentity.Verified subject, String context, String key, String id, byte[] body) {
        return decide(subject,context,key,id,body,null);
    }
    public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String key,String id,byte[] body,String businessHash){
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            WireValues.uuid(id);
            var witness = authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null,
                    "RULE", id), "rule.approve", TemplateDependencyAuthority.PURPOSE);
            var request = schema.decide(body);
            var businessRecord=business.approval(admission,store.approvalTarget(admission,id),businessHash);
            if (!id.equals(request.target_revision())) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            var values = new HashMap<String,Object>(); values.put("userId", subject.user().user_id()); values.put("key", key);
            if(businessRecord!=null)values.put("businessHash",businessHash);
            values.put("requestHash", schema.hash(id,businessRecord==null?request:Map.of("decision",request,"business_definition_sha256",businessHash,"business_schema_sha256",business.manifest()), context, subject.sessionId())); values.put("ruleId", id);
            values.put("operationManifest", schema.manifestSha256()); values.put("classification", policy.classification());
            var prior = mapper.approvalOperation(values);
            if (prior.size() > 1) mismatch();
            if (!prior.isEmpty() && !prior.get(0).requestSha256().equals(values.get("requestHash")))
                throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            var prepared = prepare(admission, id);
            if (!prior.isEmpty()) return replay(values, prior.get(0), request, prepared.stored());
            current(admission, context);
            var before = prepared.stored();
            if (!request.expected_revision().equals(before.revision())) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
            if (!prepared.baseline().equals(request.baseline_manifest())) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            String status = nextStatus(before.status(), request.decision());
            String revision = PermissionSchema.next(before.revision());
            values.put("hash", before.content_sha256()); values.put("expected", before.revision()); values.put("resulting", revision);
            values.put("oldStatus", before.status()); values.put("newStatus", status); values.put("decision", request.decision());
            one(mapper.advanceRule(values)); one(mapper.advanceDefinition(values));
            var after = store.approvalTarget(admission, id);
            if (!after.status().equals(status) || !after.revision().equals(revision) || !after.content().equals(before.content())
                    || !after.content_sha256().equals(before.content_sha256()) || !after.dependency_refs().equals(before.dependency_refs())) mismatch();
            var now = Objects.requireNonNull(permissions.databaseTime());
            values.put("now", now); values.put("classification", policy.classification()); values.put("operationManifest", schema.manifestSha256());
            values.put("authEpoch", admission.watermarks().authEpoch().toString()); values.put("governanceEpoch", admission.watermarks().governanceEpoch().toString());
            var resultingContext = advanceContext(admission, values);
            String operationId = UUID.randomUUID().toString(), decisionId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
            var receipt = json.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
            receipt.put("decision_id", decisionId); receipt.put("rule_version", id); receipt.put("decision", request.decision());
            receipt.put("status", status); receipt.put("revision", revision); receipt.put("content_sha256", after.content_sha256());
            receipt.put("actor_id", subject.user().user_id()); receipt.put("decided_at", time(now)); receipt.set("current_context", resultingContext);
            receipt.set("allowed_actions", json.tree(List.of())); var response = schema.envelope(receipt, requestId);
            values.put("operationId", operationId); values.put("decisionId", decisionId); values.put("response", json.canonical(response));
            values.put("responseHash", DigestUtils.sha256Hex((String) values.get("response"))); values.put("reason", request.reason());
            values.put("request", json.canonicalObject(request)); values.put("baseline", json.canonicalObject(prepared.baseline()));
            values.put("baselineHash", DigestUtils.sha256Hex((String) values.get("baseline")));
            values.put("dependencies", json.canonical(prepared.authorizations())); values.put("authorityGrant", witness.grantId());
            values.put("authorityRevision", witness.revision().toString());
            one(mapper.saveApprovalOperation(values)); one(mapper.saveApproval(values));
            var decision = schema.read(single(mapper.approval(values)));
            verifyDecision(decision, values, request, response);
            var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operationId);
            audit.set("request", json.tree(request)); audit.set("before", json.tree(before)); audit.set("after", json.tree(after));
            audit.set("decision", decision); var payload = json.object(); payload.put("schema_version", "1.0.0"); payload.set("decision", decision);
            if(businessRecord!=null){audit.set("business_definition",businessRecord);payload.set("business_definition",businessRecord);}
            values.put("auditId", UUID.randomUUID().toString()); values.put("eventId", UUID.randomUUID().toString()); values.put("requestId", requestId);
            one(permissions.saveAudit(subject.user().user_id(), null, (String) values.get("auditId"), "rule.approval_decided",
                    json.canonical(audit), requestId, schema.manifestSha256(), policy.classification()));
            one(permissions.saveEvent(subject.user().user_id(), (String) values.get("eventId"), id, revision, "rule.approval_decided", json.canonical(payload)));
            if (!schema.same(schema.read(single(mapper.approvalAudit(values))), audit) || !schema.same(schema.read(single(mapper.approvalEvent(values))), payload)) mismatch();
            if (!replay(values, single(mapper.approvalOperation(values)), request, after).equals(response)) mismatch();
            return response;
        }).get());
    }

    private Prepared prepare(EnterpriseAccess.Admission admission, String id) throws IOException {
        var stored = store.approvalTarget(admission, id);
        var validated = store.validated(stored);
        if (!validated.content_sha256().equals(stored.content_sha256()) || !validated.dependency_refs().equals(stored.dependency_refs())) mismatch();
        var proofs = dependencies.requireRule(admission, validated); var snapshot = dependencies.snapshot(admission, proofs, json);
        var baseline = new RuleVersionProtocol.Baseline("1.0.0", id, stored.content_sha256(), schema.contentManifestSha256(),
                stored.dependency_refs(), DigestUtils.sha256Hex(json.canonical(snapshot)));
        schema.baseline(baseline); return new Prepared(stored, baseline, snapshot);
    }

    private JsonNode replay(Map<String,Object> values, SelectionOperationRow operation, RuleVersionProtocol.Decide request,
            RuleVersionProtocol.Stored actual) throws IOException {
        if (!operation.requestSha256().equals(values.get("requestHash")) || !operation.targetId().equals(values.get("ruleId"))
                || !operation.state().equals("COMPLETED") || !Objects.equals(operation.httpStatus(), 200)
                || !operation.schemaVersion().equals("1.0.0") || !operation.schemaManifestSha256().equals(schema.manifestSha256())) mismatch();
        var response = schema.receipt(operation.responseRef()); var receipt = response.required("data");
        if (!receipt.required("operation_id").textValue().equals(operation.operationId())
                || !receipt.required("rule_version").textValue().equals(actual.rule_version())
                || !receipt.required("content_sha256").textValue().equals(actual.content_sha256())
                || !receipt.required("actor_id").textValue().equals(values.get("userId"))
                || !receipt.required("current_context").required("tenant_id").textValue().equals(actual.tenant_id())
                || !receipt.required("revision").textValue().equals(PermissionSchema.next(request.expected_revision()))) mismatch();
        values.put("operationId", operation.operationId()); values.put("decisionId", receipt.required("decision_id").textValue());
        var decision = schema.read(single(mapper.approval(values)));
        if (!decision.required("request").equals(json.tree(request)) || !decision.required("receipt").equals(response)
                || !decision.required("receipt_sha256").textValue().equals(DigestUtils.sha256Hex(json.canonical(response)))
                || !decision.required("baseline_manifest").equals(json.tree(request.baseline_manifest()))
                || !decision.required("baseline_sha256").textValue().equals(DigestUtils.sha256Hex(json.canonicalObject(request.baseline_manifest())))
                || !decision.required("decision").textValue().equals(request.decision())
                || !decision.required("created_by").textValue().equals(values.get("userId"))
                || !decision.required("rule_sha256").textValue().equals(actual.content_sha256())
                || !decision.required("expected_revision").textValue().equals(request.expected_revision())
                || !decision.required("reason").textValue().equals(request.reason())
                || !decision.required("to_status").equals(receipt.required("status"))
                || !decision.required("resulting_revision").equals(receipt.required("revision"))) mismatch();
        if (!DigestUtils.sha256Hex(json.canonical(decision.required("dependency_authorizations")))
                .equals(request.baseline_manifest().dependency_authorizations_sha256())) mismatch();
        values.put("requestId", response.required("request_id").textValue()); values.put("resulting", receipt.required("revision").textValue());
        var audit = schema.read(single(mapper.approvalAuditForOperation(values)));
        var event = schema.read(single(mapper.approvalEventForOperation(values)));
        if (!audit.required("decision").equals(decision) || !audit.required("request").equals(json.tree(request))
                || !event.required("decision").equals(decision)) mismatch();
        var businessHash=values.get("businessHash");
        if(businessHash!=null&&(!audit.required("business_definition").required("definition_sha256").asText().equals(businessHash)||!audit.required("business_definition").equals(event.required("business_definition"))))mismatch();
        return response;
    }

    private void verifyDecision(JsonNode value, Map<String,Object> parameters, RuleVersionProtocol.Decide request, JsonNode response) throws IOException {
        var expected = json.object();
        for (var pair : Map.ofEntries(Map.entry("decision_id", "decisionId"), Map.entry("operation_id", "operationId"),
                Map.entry("rule_version", "ruleId"), Map.entry("rule_sha256", "hash"), Map.entry("decision", "decision"),
                Map.entry("from_status", "oldStatus"), Map.entry("to_status", "newStatus"), Map.entry("expected_revision", "expected"),
                Map.entry("resulting_revision", "resulting"), Map.entry("reason", "reason"), Map.entry("baseline_sha256", "baselineHash"),
                Map.entry("receipt_sha256", "responseHash"), Map.entry("authority_grant", "authorityGrant"), Map.entry("authority_revision", "authorityRevision"),
                Map.entry("auth_epoch", "authEpoch"), Map.entry("governance_epoch", "governanceEpoch"), Map.entry("schema_manifest_sha256", "operationManifest"),
                Map.entry("created_by", "userId"), Map.entry("classification", "classification")).entrySet()) expected.put(pair.getKey(), (String) parameters.get(pair.getValue()));
        expected.put("schema_version", "1.0.0"); expected.put("created_at", time((LocalDateTime) parameters.get("now")));
        expected.set("request", json.tree(request)); expected.set("baseline_manifest", json.tree(request.baseline_manifest()));
        expected.set("dependency_authorizations", schema.read((String) parameters.get("dependencies"))); expected.set("receipt", response);
        // MySQL固定微秒格式；使用成熟时间类型比较同一实际时间。
        if (!LocalDateTime.parse(value.required("created_at").textValue(), java.time.format.DateTimeFormatter.ISO_DATE_TIME)
                .equals(parameters.get("now"))) mismatch();
        expected.put("created_at", value.required("created_at").textValue());
        if (!schema.same(value, expected)) mismatch();
    }

    private JsonNode advanceContext(EnterpriseAccess.Admission admission, Map<String,Object> values) {
        String governance = PermissionSchema.next(admission.watermarks().governanceEpoch().toString());
        String revision = PermissionSchema.next(admission.context().context_revision()); one(mapper.advanceGovernance(values));
        one(jdbc.update("""
                UPDATE platform_session_context SET context_revision=?,governance_epoch=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=? AND context_revision=? AND auth_epoch=? AND governance_epoch=?
                """, revision, governance, admission.contextId(), admission.subject().sessionId(), admission.subject().user().user_id(),
                admission.context().tenant_id(), admission.context().context_revision(), admission.context().auth_epoch(), admission.context().governance_epoch()));
        var current = json.object(); current.put("tenant_id", admission.context().tenant_id()); current.put("context_revision", revision);
        current.put("auth_epoch", admission.watermarks().authEpoch().toString()); current.put("governance_epoch", governance); return current;
    }
    static String nextStatus(String status, String decision) { return TemplateApprovalService.nextStatus(status, decision); }
    private static void current(EnterpriseAccess.Admission admission, String context) { if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED); }
    private static String time(LocalDateTime value) { return DatabaseTimestamp.format(value); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static <T> T single(List<T> rows) { if (rows.size() != 1) mismatch(); return rows.get(0); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private record Prepared(RuleVersionProtocol.Stored stored, RuleVersionProtocol.Baseline baseline, JsonNode authorizations) { }
}
