package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SimulationRuleVersionMapper;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Service-path tests with explicit mocked authority/storage boundaries; these are not database transaction or customer approval evidence. */
class SimulationRuleVersionServiceTest {
    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String USER = "00000000-0000-4000-8000-000000000002";
    private static final String BASIS = "00000000-0000-4000-8000-000000000003";
    private static final String GRANT = "00000000-0000-4000-8000-000000000004";
    private static final String HASH = "a".repeat(64);
    private static final String CREATE_KEY = "unit-test-create-key";
    private static final String DECIDE_KEY = "unit-test-decide-key";
    private final LocalDateTime now = LocalDateTime.parse("2026-10-16T00:00:00");
    private final SimulationRuleVersionMapper mapper = mock(SimulationRuleVersionMapper.class);
    private final PermissionMapper permissions = mock(PermissionMapper.class);
    private final SimulationRuleDependencies dependencies = mock(SimulationRuleDependencies.class);
    private final EnterpriseAccess access = mock(EnterpriseAccess.class);
    private final ResourceReadAuthority authority = mock(ResourceReadAuthority.class);
    private final ApplicationSessionIdentity.Verified subject = mock(ApplicationSessionIdentity.Verified.class);
    private final IdentityDirectoryPolicy policy = new IdentityDirectoryPolicy("UNIT_TEST");
    private final Map<String,SelectionOperationRow> creates = new HashMap<>(), decisions = new HashMap<>();
    private final Map<String,String> audits = new HashMap<>(), events = new HashMap<>();
    private final Map<String,JsonNode> decisionRows = new HashMap<>();
    private Map<String,Object> definition;
    private final Map<String,String> approvalBindings=new HashMap<>();
    private String status;
    private String revision;
    private EnterpriseAccess.Admission admission;
    private PermissionSchema json;
    private SimulationRuleVersionSchema schema;
    private RuleExpression engine;
    private SimulationRuleVersionService service;
    private SimulationRuleVersionStore store;
    private SimulationRuleVersionApprovalService approval;

    @BeforeEach void setup() throws Exception {
        json = new PermissionSchema(); engine = new RuleExpression(); schema = new SimulationRuleVersionSchema(json, new TemplateApplicabilitySchema(json), engine);
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(USER, "unit", "Unit test", null)); when(subject.sessionId()).thenReturn("unit-test-session");
        admission = new EnterpriseAccess.Admission(subject, GRANT, new BusinessSessionProtocol.Context(TENANT, "1", "1", "1"), new TenantWatermarks(BigInteger.ONE, BigInteger.ONE));
        when(access.execute(eq(subject), any())).thenAnswer(invocation -> scoped(invocation.getArgument(1)));
        when(permissions.databaseTime()).thenReturn(now);
        var grant = new PermissionProtocol.GrantRow(GRANT, USER, null, "ENTERPRISE", TENANT, "rule.write", "{}", "ACTIVE", null, null, BASIS, "1.0.0", HASH, BigInteger.ONE);
        when(authority.require(any(), any(), anyString(), anyString())).thenReturn(grant);
        when(dependencies.requireRule(any(), any())).thenReturn(List.of());
        when(dependencies.snapshot(any(), anyList(), eq(json))).thenAnswer(invocation -> {
            var snapshot = json.object(); snapshot.put("context_revision", admission.context().context_revision());
            snapshot.put("auth_epoch", "1"); snapshot.put("governance_epoch", admission.context().governance_epoch()); snapshot.putArray("dependencies"); return snapshot;
        });
        when(mapper.insertVersion(anyMap())).thenReturn(1); when(mapper.insertRule(anyMap())).thenReturn(1);
        when(mapper.insertDefinition(anyMap())).thenAnswer(invocation -> { definition = new HashMap<>(invocation.<Map<String,Object>>getArgument(0)); status = "DRAFT"; revision = "1"; return 1; });
        when(mapper.insertBinding(anyMap())).thenAnswer(invocation -> {definition.putAll(invocation.<Map<String,Object>>getArgument(0));return 1;});
        when(mapper.insertApprovalBinding(anyMap())).thenAnswer(invocation -> {var v=invocation.<Map<String,Object>>getArgument(0);approvalBindings.put((String)v.get("decisionId"),(String)v.get("bindingHash"));return 1;});
        when(mapper.approvalBinding(anyMap())).thenAnswer(invocation -> {var v=invocation.<Map<String,Object>>getArgument(0);var h=approvalBindings.get(v.get("decisionId"));return h==null?List.of():List.of(h);});
        when(mapper.insertLineage(anyMap())).thenReturn(1); when(mapper.insertBaseLineage(anyMap())).thenReturn(1);
        when(mapper.definition(anyMap())).thenAnswer(invocation -> definition == null ? List.of() : List.of(row()));
        when(mapper.approvedWorkflow(anyMap())).thenAnswer(call -> "APPROVED".equals(status)?List.of((String)definition.get("ruleId")):List.of());
        when(mapper.approvedEvidence(anyMap())).thenAnswer(call -> evidence());
        when(mapper.parents(anyMap())).thenReturn(List.of(BASIS)); when(mapper.baseParents(anyMap())).thenReturn(List.of());
        when(mapper.currentOperation(anyMap())).thenAnswer(invocation -> operation(creates, invocation.getArgument(0)));
        when(mapper.approvalOperation(anyMap())).thenAnswer(invocation -> operation(decisions, invocation.getArgument(0)));
        when(mapper.saveOperation(anyMap())).thenAnswer(invocation -> saveOperation(creates, invocation.getArgument(0), 201));
        when(mapper.saveApprovalOperation(anyMap())).thenAnswer(invocation -> saveOperation(decisions, invocation.getArgument(0), 200));
        when(mapper.advanceRule(anyMap())).thenAnswer(invocation -> { var values = invocation.<Map<String,Object>>getArgument(0); if (!status.equals(values.get("oldStatus"))) return 0; status = (String) values.get("newStatus"); return 1; });
        when(mapper.advanceDefinition(anyMap())).thenAnswer(invocation -> { var values = invocation.<Map<String,Object>>getArgument(0); if (!revision.equals(values.get("expected"))) return 0; revision = (String) values.get("resulting"); return 1; });
        when(mapper.advanceGovernance(anyMap())).thenReturn(1);
        when(mapper.saveApproval(anyMap())).thenAnswer(invocation -> { var values = invocation.<Map<String,Object>>getArgument(0); decisionRows.put((String) values.get("decisionId"), decision(values)); return 1; });
        when(mapper.approval(anyMap())).thenAnswer(invocation -> { var values = invocation.<Map<String,Object>>getArgument(0); var value = decisionRows.get(values.get("decisionId")); return value == null ? List.of() : List.of(json.canonical(value)); });
        when(permissions.saveAudit(anyString(), isNull(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            audits.put(invocation.getArgument(3) + ":" + invocation.getArgument(5), invocation.getArgument(4)); return 1;
        });
        when(permissions.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            events.put(invocation.getArgument(4) + ":" + invocation.getArgument(3), invocation.getArgument(5)); return 1;
        });
        when(mapper.audit(anyMap())).thenAnswer(invocation -> audit("simulation.rule.draft_created", invocation.getArgument(0)));
        when(mapper.auditForOperation(anyMap())).thenAnswer(invocation -> audit("simulation.rule.draft_created", invocation.getArgument(0)));
        when(mapper.event(anyMap())).thenAnswer(invocation -> List.of(events.get("simulation.rule.draft_created:1")));
        when(mapper.eventForOperation(anyMap())).thenAnswer(invocation -> List.of(events.get("simulation.rule.draft_created:1")));
        when(mapper.approvalAudit(anyMap())).thenAnswer(invocation -> audit("simulation.rule.approval_decided", invocation.getArgument(0)));
        when(mapper.approvalAuditForOperation(anyMap())).thenAnswer(invocation -> audit("simulation.rule.approval_decided", invocation.getArgument(0)));
        when(mapper.approvalEvent(anyMap())).thenAnswer(invocation -> List.of(events.get("simulation.rule.approval_decided:" + invocation.<Map<String,Object>>getArgument(0).get("resulting"))));
        when(mapper.approvalEventForOperation(anyMap())).thenAnswer(invocation -> List.of(events.get("simulation.rule.approval_decided:" + invocation.<Map<String,Object>>getArgument(0).get("resulting"))));
        var datasource = mock(DataSource.class); var connection = mock(Connection.class); var statement = mock(PreparedStatement.class);
        when(datasource.getConnection()).thenReturn(connection); when(connection.prepareStatement(anyString())).thenReturn(statement); when(statement.executeUpdate()).thenReturn(1);
        store = new SimulationRuleVersionStore(mapper, permissions, authority, dependencies, schema, json, policy);
        service = new SimulationRuleVersionService(access, mapper, permissions, authority, dependencies, schema, store, json, policy);
        approval = new SimulationRuleVersionApprovalService(access, mapper, store, dependencies, schema, authority, permissions, json, policy, datasource);
    }
    private Object scoped(Function<EnterpriseAccess.Admission,Object> operation) {
        try (var scope = TenantScope.open(new TenantScope.Identity(TENANT, USER, new BigInteger(admission.context().context_revision()), admission.watermarks().authEpoch(), admission.watermarks().governanceEpoch()))) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { scope.verifyTransaction(admission.watermarks()); return operation.apply(admission); }
            finally { scope.finishTransaction(); TransactionSynchronizationManager.setActualTransactionActive(false); }
        }
    }
    private List<SelectionOperationRow> operation(Map<String,SelectionOperationRow> operations, Map<String,Object> values) {
        var row = operations.get(values.get("key")); return row == null ? List.of() : List.of(row);
    }
    private int saveOperation(Map<String,SelectionOperationRow> operations, Map<String,Object> values, int status) {
        operations.put((String) values.get("key"), new SelectionOperationRow((String) values.get("operationId"), (String) values.get("requestHash"), "COMPLETED", status,
                (String) values.get("response"), (String) values.get("ruleId"), "1.0.0", schema.manifestSha256())); return 1;
    }
    private List<String> audit(String action, Map<String,Object> values) { var value = audits.get(action + ":" + values.get("requestId")); return value == null ? List.of() : List.of(value); }
    private SimulationRuleVersionProtocol.StorageRow row() {
        return new SimulationRuleVersionProtocol.StorageRow(TENANT, (String) definition.get("ruleId"), (String) definition.get("engine"), (String) definition.get("expression"),
                (String) definition.get("basis"), status, (String) definition.get("hash"), (String) definition.get("hash"), "1.0.0", (String) definition.get("manifest"),
                USER, now, policy.classification(), (String) definition.get("content"), null, null, (String) definition.get("reason"), GRANT, BigInteger.ONE, new BigInteger(revision), (String)definition.get("binding"), (String)definition.get("bindingHash"), (String)definition.get("bindingManifest"));
    }
    private List<SimulationRuleVersionProtocol.ApprovalEvidenceRow> evidence() throws Exception {
        var records=decisionRows.values().stream().filter(d -> d.path("resulting_revision").asText().equals(revision)).toList();
        if(records.size()!=1)return List.of();var d=records.get(0);
        var operation=decisions.values().stream().filter(o->o.operationId().equals(d.path("operation_id").asText())).findFirst().orElseThrow();
        var response=schema.read(operation.responseRef());String requestId=response.required("request_id").textValue();
        String binding=approvalBindings.get(d.path("decision_id").asText());if(binding==null
            || !audits.containsKey("simulation.rule.approval_decided:"+requestId) || !events.containsKey("simulation.rule.approval_decided:"+revision))return List.of();
        return List.of(new SimulationRuleVersionProtocol.ApprovalEvidenceRow(json.canonical(d),operation.responseRef(),
            audits.get("simulation.rule.approval_decided:"+requestId),events.get("simulation.rule.approval_decided:"+revision),
            binding,USER,now,policy.classification()));
    }
    private Object consume(String id) {
        return scoped(a->org.springframework.util.function.ThrowingSupplier.of(()->store.requireEvaluation(a,new PermissionProtocol.Basis(id,(String)definition.get("hash")),true)).get());
    }
    private JsonNode decision(Map<String,Object> values) throws Exception {
        var result = json.object();
        for (var pair : Map.ofEntries(Map.entry("decision_id", "decisionId"), Map.entry("operation_id", "operationId"), Map.entry("rule_version", "ruleId"),
                Map.entry("rule_sha256", "hash"), Map.entry("decision", "decision"), Map.entry("from_status", "oldStatus"), Map.entry("to_status", "newStatus"),
                Map.entry("expected_revision", "expected"), Map.entry("resulting_revision", "resulting"), Map.entry("reason", "reason"), Map.entry("baseline_sha256", "baselineHash"),
                Map.entry("receipt_sha256", "responseHash"), Map.entry("authority_grant", "authorityGrant"), Map.entry("authority_revision", "authorityRevision"),
                Map.entry("auth_epoch", "authEpoch"), Map.entry("governance_epoch", "governanceEpoch"), Map.entry("schema_manifest_sha256", "operationManifest"),
                Map.entry("created_by", "userId"), Map.entry("classification", "classification")).entrySet()) result.put(pair.getKey(), (String) values.get(pair.getValue()));
        result.put("schema_version", "1.0.0"); result.put("created_at", "2026-10-16T00:00:00.000000Z");
        for (var pair : Map.of("request", "request", "baseline_manifest", "baseline", "dependency_authorizations", "dependencies", "receipt", "response").entrySet())
            result.set(pair.getKey(), schema.read((String) values.get(pair.getValue()))); return result;
    }
    private byte[] input() throws Exception {
        var basis = new PermissionProtocol.Basis(BASIS, HASH);
        var expression = new TemplateApplicabilityProtocol.Expression("1.0.0", RuleExpression.ENGINE, "#attributes['value'] == 'ACTIVE'", List.of("value"), List.of());
        return json.canonicalObject(new SimulationRuleVersionProtocol.VersionInput("1.0.0", null, SimulationRuleVersionSchema.CONTENT_REF,
                new TemplateApplicabilityProtocol.RuleContent("1.0.0", RuleExpression.ENGINE, expression, List.of(basis)), "Synthetic service-path test only", SimulationRuleVersionSchema.USE, false)).getBytes(StandardCharsets.UTF_8);
    }
    private String create() throws Exception { return service.create(subject, "1", CREATE_KEY, input()).required("data").required("rule").required("rule_version").textValue(); }
    private ObjectNode decide(String id) throws Exception {
        var preview = approval.baseline(subject, "1", id).required("data"); var value = json.object(); value.put("schema_version", "1.0.0"); value.put("target_revision", id);
        value.put("expected_revision", preview.required("rule").required("revision").textValue()); value.put("decision", "APPROVE"); value.put("reason", "Synthetic service-path test only");
        value.set("baseline_manifest", preview.required("baseline_manifest")); return value;
    }
    private byte[] bytes(JsonNode value) throws Exception { return json.canonical(value).getBytes(StandardCharsets.UTF_8); }

    @Test void saveReadApproveAndReplayPreserveOriginalContentAndCompleteAuditOutbox() throws Exception {
        var initial = service.create(subject, "1", CREATE_KEY, input()); String id = initial.required("data").required("rule").required("rule_version").textValue();
        assertEquals("DRAFT", status); assertEquals("1", revision); assertEquals(1, audits.size()); assertEquals(1, events.size());
        assertEquals(initial, service.create(subject, "1", CREATE_KEY, input())); verify(mapper, times(1)).insertRule(anyMap());
        assertEquals(initial.required("data").required("rule"), service.read(subject, "1", id).required("data").required("rule"));
        var request = decide(id); var result = approval.decide(subject, "1", DECIDE_KEY, id, bytes(request));
        assertEquals("APPROVED", status); assertEquals("2", revision); assertEquals(2, audits.size()); assertEquals(2, events.size());
        assertEquals("2", result.required("data").required("current_context").required("governance_epoch").textValue());
        assertEquals(initial.required("data").required("rule").required("content_sha256"), result.required("data").required("content_sha256"));
        admission = new EnterpriseAccess.Admission(subject, GRANT, new BusinessSessionProtocol.Context(TENANT, "2", "1", "2"), new TenantWatermarks(BigInteger.ONE, BigInteger.TWO));
        assertEquals(result, approval.decide(subject, "1", DECIDE_KEY, id, bytes(request)));
        assertEquals(initial, service.create(subject, "1", CREATE_KEY, input()));
        assertEquals(ErrorCode.CONTEXT_CHANGED, assertThrows(TenantAccessException.class,
                () -> service.create(subject, "1", "new-stale-context-key", input())).code());
        verify(mapper, times(1)).advanceRule(anyMap()); verify(mapper, times(1)).saveApproval(anyMap());
        assertEquals(initial.required("data").required("rule").required("binding_sha256"), result.required("data").required("binding_sha256"));
        assertEquals("SIMULATION_EVALUATION", result.required("data").required("dependency_use").textValue());
        assertFalse(result.required("data").required("production_eligible").booleanValue());
        verify(authority, atLeastOnce()).require(any(), any(), eq("rule.write"), eq("TEMPLATE_MAINTENANCE"));
        verify(authority, atLeastOnce()).require(any(), any(), eq("rule.approve"), eq("TEMPLATE_MAINTENANCE"));
    }
    @Test void draftCreationNeverCallsApprovalAndMutationWithSameKeyIsRejected() throws Exception {
        create(); verify(mapper, never()).saveApproval(anyMap()); verify(mapper, never()).advanceRule(anyMap());
        var changed = (ObjectNode) schema.read(new String(input(), StandardCharsets.UTF_8)); changed.put("reason", "Changed payload");
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, assertThrows(TenantAccessException.class, () -> service.create(subject, "1", CREATE_KEY, bytes(changed))).code());
    }
    @Test void staleCasOrBaselineAndRevokedEvidenceCannotAdvanceApproval() throws Exception {
        String id = create(); var request = decide(id); request.put("expected_revision", "2");
        assertEquals(ErrorCode.REVISION_CONFLICT, assertThrows(TenantAccessException.class, () -> approval.decide(subject, "1", DECIDE_KEY, id, bytes(request))).code());
        request.put("expected_revision", "1"); ((ObjectNode) request.required("baseline_manifest")).put("dependency_authorizations_sha256", "b".repeat(64));
        assertEquals(ErrorCode.SCOPE_STALE, assertThrows(TenantAccessException.class, () -> approval.decide(subject, "1", DECIDE_KEY, id, bytes(request))).code());
        when(dependencies.requireRule(any(), any())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> approval.decide(subject, "1", DECIDE_KEY, id, bytes(request))).code());
        verify(mapper, never()).advanceRule(anyMap()); assertEquals("DRAFT", status);
    }
    @Test void replayRechecksEvidenceRightsAndOriginalCreationAudit() throws Exception {
        create(); audits.clear(); assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> service.create(subject, "1", CREATE_KEY, input())).code());
        when(dependencies.requireRule(any(), any())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> service.create(subject, "1", CREATE_KEY, input())).code());
        verify(mapper, times(1)).insertRule(anyMap());
    }
    @Test void noSuccessReceiptWhenAuditOrOutboxWriteFails() throws Exception {
        when(permissions.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(0);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, this::create).code());
        // Production EnterpriseAccess/TenantTransaction owns rollback; this mock test deliberately does not claim transaction proof.
    }
    @Test void bindingTamperingBlocksReadAndReplayWithoutLegacyFallback() throws Exception {
        String id=create();definition.put("bindingHash",HASH);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.read(subject,"1",id)).code());
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.create(subject,"1",CREATE_KEY,input())).code());
        verify(mapper,never()).advanceRule(anyMap());
    }
    @Test void wrongBindingBaselineAndMissingApprovalSidecarFailClosed() throws Exception {
        String id=create();var request=decide(id);((ObjectNode)request.required("baseline_manifest")).put("binding_sha256",HASH);
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,()->approval.decide(subject,"1",DECIDE_KEY,id,bytes(request))).code());
        verify(mapper,never()).advanceRule(anyMap());
        var good=decide(id);approval.decide(subject,"1",DECIDE_KEY,id,bytes(good));approvalBindings.clear();
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->approval.decide(subject,"1",DECIDE_KEY,id,bytes(good))).code());
    }
    @Test void governanceGrantDenialStopsBeforeDraftAndDependencyReadDoesNotAuthorizeWrite() throws Exception {
        when(authority.require(any(),any(),eq("rule.write"),eq("TEMPLATE_MAINTENANCE"))).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,this::create).code());
        verify(mapper,never()).insertRule(anyMap());verify(mapper,never()).insertBinding(anyMap());
    }

    @Test void consumptionRecomputesAllApprovalDigestsAndCompleteAuditOutbox() throws Exception {
        String id=create();var request=decide(id);var response=approval.decide(subject,"1",DECIDE_KEY,id,bytes(request));
        assertInstanceOf(RuleVersionProtocol.Validated.class,consume(id));
        String decisionId=response.required("data").required("decision_id").textValue();var original=decisionRows.get(decisionId).deepCopy();
        for(String field:List.of("dependency_authorizations","baseline_manifest","baseline_sha256","receipt","receipt_sha256","resulting_revision")) {
            var changed=(ObjectNode)original.deepCopy();
            switch(field) {
                case "dependency_authorizations" -> ((ObjectNode)changed.required(field)).put("auth_epoch","999");
                case "baseline_manifest" -> ((ObjectNode)changed.required(field)).put("dependency_authorizations_sha256",HASH);
                case "receipt" -> ((ObjectNode)changed.required(field).required("data")).put("actor_id",BASIS);
                case "resulting_revision" -> changed.put(field,"3");
                default -> changed.put(field,HASH);
            }
            decisionRows.put(decisionId,changed);
            assertThrows(TenantAccessException.class,()->consume(id),field);
            decisionRows.put(decisionId,original);
        }
        String auditKey="simulation.rule.approval_decided:"+response.required("request_id").textValue();String originalAudit=audits.get(auditKey);
        for(String target:List.of("before","after","decision","operation_id")) {
            var changed=(ObjectNode)schema.read(originalAudit);
            if(target.equals("operation_id"))changed.put(target,BASIS);
            else if(target.equals("decision"))((ObjectNode)changed.required(target)).put("reason","damaged historical audit");
            else ((ObjectNode)changed.required(target)).put("status","DISABLED");
            audits.put(auditKey,json.canonical(changed));assertThrows(TenantAccessException.class,()->consume(id),target);audits.put(auditKey,originalAudit);
        }
        String eventKey="simulation.rule.approval_decided:2",originalEvent=events.get(eventKey);var event=(ObjectNode)schema.read(originalEvent);
        ((ObjectNode)event.required("decision")).put("reason","damaged outbox");events.put(eventKey,json.canonical(event));
        assertThrows(TenantAccessException.class,()->consume(id));events.put(eventKey,originalEvent);
        var old=decisions.get(DECIDE_KEY);var altered=(ObjectNode)schema.read(old.responseRef());altered.put("request_id",BASIS);
        decisions.put(DECIDE_KEY,new SelectionOperationRow(old.operationId(),old.requestSha256(),old.state(),old.httpStatus(),json.canonical(altered),old.targetId(),old.schemaVersion(),old.schemaManifestSha256()));
        assertThrows(TenantAccessException.class,()->consume(id));decisions.put(DECIDE_KEY,old);
        assertInstanceOf(RuleVersionProtocol.Validated.class,consume(id));
    }
    @Test void historicalReplayChecksExactTransitionAuditButSurvivesLaterLegitimateReturn() throws Exception {
        String id=create();var approveRequest=decide(id);var original=approval.decide(subject,"1",DECIDE_KEY,id,bytes(approveRequest));
        String auditKey="simulation.rule.approval_decided:"+original.required("request_id").textValue();String saved=audits.get(auditKey);
        for(String target:List.of("before","after","operation_id")) {
            var changed=(ObjectNode)schema.read(saved);
            if(target.equals("operation_id"))changed.put(target,BASIS);else ((ObjectNode)changed.required(target)).put("status","DISABLED");
            audits.put(auditKey,json.canonical(changed));
            assertThrows(TenantAccessException.class,()->approval.decide(subject,"1",DECIDE_KEY,id,bytes(approveRequest)),target);audits.put(auditKey,saved);
        }
        var returned=decide(id);returned.put("decision","RETURN");approval.decide(subject,"1","return-test-key",id,bytes(returned));
        assertEquals("RETURNED",status);assertEquals("3",revision);
        assertEquals(original,approval.decide(subject,"1",DECIDE_KEY,id,bytes(approveRequest)));
    }

    @Test void originalWorkerProofAllowsReadonlyRuleConsumptionWithoutAnyUserSession() throws Exception {
        String id=create();var request=decide(id);approval.decide(subject,"1",DECIDE_KEY,id,bytes(request));
        // The worker authority boundary is an explicit double, not a manufactured authenticated user.
        var proof=mock(SimulationWorkerAccess.Proof.class);when(proof.actorId()).thenReturn(USER);
        admission=new EnterpriseAccess.Admission(proof,new BusinessSessionProtocol.Context(TENANT,"1","1","1"),new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
        assertThrows(TenantAccessException.class,()->admission.subject());
        assertInstanceOf(RuleVersionProtocol.Validated.class,consume(id));
        verify(proof,atLeastOnce()).actorId();
        verify(mapper,times(1)).insertRule(anyMap());verify(mapper,times(1)).saveApproval(anyMap());
    }

    @Test void actualResourceAuthorityKeepsWorkerPurposeBoundaryAndRequiresRealSimulationReadGrant() throws Exception {
        String id=create();var request=decide(id);approval.decide(subject,"1",DECIDE_KEY,id,bytes(request));
        var proof=mock(SimulationWorkerAccess.Proof.class);when(proof.actorId()).thenReturn(USER);
        when(proof.bindingView()).thenReturn(new com.ruoyi.medical.identity.AuthenticatedIdentityBinding.View("local",USER,null,null));
        admission=new EnterpriseAccess.Admission(proof,new BusinessSessionProtocol.Context(TENANT,"1","1","1"),new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
        var realAuthority=new ResourceReadAuthority(permissions,new PermissionAuthority(json),json);
        store=new SimulationRuleVersionStore(mapper,permissions,realAuthority,dependencies,schema,json,policy);
        var scope=new PermissionProtocol.Scope(TENANT,null,"RULE",id);
        when(permissions.resourceOwner("RULE",id)).thenReturn(List.of(new PermissionProtocol.OwnerRow(id,null)));
        String simCondition=json.canonicalObject(new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of("SIMULATION_EVALUATION"),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),List.of(),List.of())));
        assertEquals(List.of("SIMULATION_EVALUATION"),json.condition(simCondition).purposes());
        var simGrant=new PermissionProtocol.GrantRow(GRANT,USER,null,"RULE",id,"rule.read",simCondition,"ACTIVE",now.minusSeconds(1),null,BASIS,"1.0.0",json.manifestSha256(),BigInteger.ONE);
        when(permissions.resourceGrants(USER,"rule.read")).thenReturn(List.of(simGrant));
        assertInstanceOf(RuleVersionProtocol.Validated.class,consume(id));
        for(String purpose:List.of("TEMPLATE_MAINTENANCE","EVALUATION_RESULTS"))
            assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->scoped(a->org.springframework.util.function.ThrowingSupplier.of(()->realAuthority.require(a,scope,"rule.read",purpose)).get())).code());
        for(String action:List.of("rule.write","rule.approve"))
            assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->scoped(a->org.springframework.util.function.ThrowingSupplier.of(()->realAuthority.require(a,scope,action,"SIMULATION_EVALUATION")).get())).code());
        String oldCondition=json.canonicalObject(new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of("TEMPLATE_MAINTENANCE"),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),List.of(),List.of())));
        assertEquals(List.of("TEMPLATE_MAINTENANCE"),json.condition(oldCondition).purposes());
        when(permissions.resourceGrants(USER,"rule.read")).thenReturn(List.of(new PermissionProtocol.GrantRow(GRANT,USER,null,"RULE",id,"rule.read",oldCondition,"ACTIVE",now.minusSeconds(1),null,BASIS,"1.0.0",json.manifestSha256(),BigInteger.ONE)));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->consume(id)).code());
        verify(mapper,times(1)).insertRule(anyMap());verify(mapper,times(1)).saveApproval(anyMap());
    }

}
