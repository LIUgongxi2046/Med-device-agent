package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.ModelConfigurationMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Isolated service/authority tests. In-memory mappers and mocked JDBC do not verify MySQL transactions. */
class ModelConfigurationServiceTest {
    private static final String TENANT = "562eb234-c6fd-4578-a2d8-cb82641835f0";
    private static final String ACTOR = "665233e9-6368-44bf-8ceb-2c57841ea9e3";
    private static final String PRICE = "e7f81bc6-2825-4394-a179-2cfe66363702";
    private static final String POLICY = "b20944cb-57bd-435f-95fc-61c28d5fb77f";
    private static final String HASH = "a".repeat(64);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 0, 0);
    private PermissionSchema json;
    private ModelConfigurationSchema schema;
    private PermissionMapper permissions;
    private ApplicationSessionIdentity.Verified subject;
    private MemoryStore store;
    private ModelConfigurationService service;
    private BusinessSessionProtocol.Context context;
    private BigInteger governance;
    private boolean writeAllowed;
    private boolean readAllowed;
    private boolean failCas;
    private boolean failOutbox;

    @BeforeEach @SuppressWarnings({"unchecked", "rawtypes"}) void prepareIsolatedService() throws Exception {
        json = new PermissionSchema(); schema = new ModelConfigurationSchema(json); permissions = mock(PermissionMapper.class);
        subject = mock(ApplicationSessionIdentity.Verified.class); var binding = mock(AuthenticatedIdentityBinding.class);
        when(binding.view()).thenReturn(new AuthenticatedIdentityBinding.View("local", ACTOR, null, null));
        when(subject.binding()).thenReturn(binding); when(subject.sessionId()).thenReturn("isolated-test-session");
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(ACTOR, "fixture", "Fixture", "USER"));
        context = new BusinessSessionProtocol.Context(TENANT, "1", "1", "1"); governance = BigInteger.ONE;
        writeAllowed = true; readAllowed = true; store = new MemoryStore();
        store.registry.put(PRICE, new PermissionProtocol.VersionRow(PRICE, null, HASH));
        store.registry.put(POLICY, new PermissionProtocol.VersionRow(POLICY, null, HASH));
        when(permissions.databaseTime()).thenReturn(NOW);
        when(permissions.basis(anyString())).thenAnswer(call -> {
            var row = store.registry.get(call.getArgument(0)); return row == null ? List.of() : List.of(row);
        });
        when(permissions.projectStatus(anyString())).thenReturn(List.of("ACTIVE"));
        when(permissions.resourceOwner(anyString(), anyString())).thenAnswer(call -> {
            String kind = call.getArgument(0), id = call.getArgument(1);
            if (kind.equals("ENTERPRISE") && id.equals(TENANT)) return List.of(new PermissionProtocol.OwnerRow(TENANT, null));
            if (kind.equals("CONFIGURATION") && store.heads.containsKey(id)) return List.of(new PermissionProtocol.OwnerRow(id, store.heads.get(id).projectId()));
            return List.of();
        });
        when(permissions.resourceGrants(eq(ACTOR), anyString())).thenAnswer(call -> {
            String action = call.getArgument(1);
            boolean allowed = action.equals("model.configure") ? writeAllowed : action.equals("configuration.read") && readAllowed;
            return allowed ? List.of(grant(action)) : List.of();
        });
        when(permissions.saveAudit(anyString(), nullable(String.class), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(call -> { store.audits.put(call.getArgument(2), call.getArgument(4)); return 1; });
        when(permissions.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(call -> { if (failOutbox) throw new IllegalStateException("INJECTED_OUTBOX_FAILURE"); store.events.put(call.getArgument(1), call.getArgument(5)); return 1; });
        var watermarks = mock(TenantWatermarkMapper.class);
        when(watermarks.lockCurrentTenant()).thenAnswer(call -> new TenantWatermarks(BigInteger.ONE, governance));
        var access = mock(EnterpriseAccess.class);
        when(access.execute(eq(subject), any())).thenAnswer(call -> {
            var admissionContext = context; var before = store.snapshot(); var oldGovernance = governance;
            Function<EnterpriseAccess.Admission,Object> operation = call.getArgument(1);
            var identity = new TenantScope.Identity(context.tenant_id(), ACTOR, new BigInteger(context.context_revision()), BigInteger.ONE, governance);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try (var scope = TenantScope.open(identity)) {
                var epochs = new TenantWatermarks(BigInteger.ONE, governance); scope.verifyTransaction(epochs);
                try { return operation.apply(new EnterpriseAccess.Admission(subject, "fixture-context", admissionContext, epochs)); }
                catch (RuntimeException failure) { store.restore(before); context = admissionContext; governance = oldGovernance; throw failure; }
                finally { scope.finishTransaction(); }
            } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        });
        // Mock only the platform-context JDBC calls; schemas and both authorization classes are real.
        try (MockedConstruction<JdbcTemplate> construction = mockConstruction(JdbcTemplate.class, (jdbc, ignored) -> {
            when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(call -> {
                Object[] args = (Object[]) call.getRawArguments()[1];
                if (!context.context_revision().equals(args[6]) || !context.auth_epoch().equals(args[7]) || !context.governance_epoch().equals(args[8])) return 0;
                context = new BusinessSessionProtocol.Context((String) args[5], (String) args[0], context.auth_epoch(), (String) args[1]); return 1;
            });
            when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(call -> List.of(context));
        })) {
            service = new ModelConfigurationService(access, store, permissions,
                    new ResourceReadAuthority(permissions, new PermissionAuthority(json), json), watermarks, json, schema,
                    new IdentityDirectoryPolicy("INTERNAL"), mock(DataSource.class));
            assertEquals(1, construction.constructed().size());
        }
    }

    @Test void authorizedSaveAndReadPreserveFixedContentAndSecretFreeReceipts() throws Exception {
        var request = request(); var response = save(request, "1", "fixture-key-000001"); var receipt = response.required("data");
        assertEquals("DRAFT", receipt.required("status").textValue()); assertEquals("2", context.context_revision());
        assertEquals(1, store.rows.size()); assertEquals(1, store.heads.size()); assertEquals(1, store.operations.size());
        assertEquals(1, store.audits.size()); assertEquals(1, store.events.size()); assertEquals(2, store.parents.values().iterator().next().size());
        String version = receipt.required("model_config_revision").textValue();
        var view = service.read(subject, "2", version).required("data");
        assertEquals(version, view.required("model_config_revision").textValue());
        assertEquals("REGISTRY_REFERENCE_ONLY", view.required("dependency_validation").textValue());
        assertEquals(schema.publicDefinition(schema.save(bytes(request)).definition()), view.required("definition"));
        for (String serialized : List.of(response.toString(), view.toString(), store.audits.toString(), store.events.toString())) {
            assertFalse(serialized.contains("secret://")); assertFalse(serialized.contains("fixture-secret-v1"));
        }
        assertTrue(store.rows.get(version).definition().contains("fixture-secret-v1"), "Internal fixed record retains the explicitly supplied reference version");
    }

    @Test void identicalRetryAfterHeadMovementReturnsOriginalVersionWithoutWriting() throws Exception {
        var first = request(); var original = save(first, "1", "fixture-key-000001"); var data = original.required("data");
        var append = append(first, data); ((ObjectNode) append.required("definition")).put("model_version", "fixture-v2");
        var second = save(append, "2", "fixture-key-000002"); assertEquals("2", second.required("data").required("revision").textValue());
        int writes = store.writes; var replay = save(first, "1", "fixture-key-000001");
        assertEquals(original, replay); assertEquals(writes, store.writes); assertEquals(2, store.rows.size());
        var history = service.read(subject, "3", data.required("model_config_revision").textValue()).required("data");
        assertEquals("fixture-v1", history.required("definition").required("model_version").textValue());
        assertEquals(second.required("data").required("model_config_revision"), history.required("current_model_revision"));
    }

    @Test void reusedIdempotencyKeyWithDifferentPayloadIsRejected() throws Exception {
        var request = request(); save(request, "1", "fixture-key-000001"); request.put("reason", "different explicit request");
        var failure = assertThrows(TenantAccessException.class, () -> save(request, "1", "fixture-key-000001"));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, failure.code()); assertEquals(1, store.rows.size()); assertEquals(1, store.events.size());
    }

    @Test void staleContextAndRevisionConflictCannotAppend() throws Exception {
        var first = request(); var created = save(first, "1", "fixture-key-000001"); var append = append(first, created.required("data"));
        assertEquals(ErrorCode.CONTEXT_CHANGED, assertThrows(TenantAccessException.class, () -> save(append, "1", "fixture-key-000002")).code());
        append.put("expected_revision", "2");
        assertEquals(ErrorCode.REVISION_CONFLICT, assertThrows(TenantAccessException.class, () -> save(append, "2", "fixture-key-000002")).code());
        assertEquals(1, store.rows.size()); assertEquals("2", context.context_revision());
    }

    @Test void currentWriteAndReadPermissionsAreIndependentAndRetriesRecheckRevocation() throws Exception {
        var request = request(); var created = save(request, "1", "fixture-key-000001"); String version = created.required("data").required("model_config_revision").textValue();
        readAllowed = false;
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> service.read(subject, "2", version)).code());
        readAllowed = true; writeAllowed = false;
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> save(request, "1", "fixture-key-000001")).code());
        assertEquals(List.of("configuration.read"), new com.fasterxml.jackson.databind.ObjectMapper().convertValue(
                service.read(subject, "2", version).required("data").required("allowed_actions"), List.class));
        assertEquals(1, store.rows.size());
    }

    @Test void pausedOrArchivedProjectKeepsReadWithoutAdvertisingWrites() throws Exception {
        String project = UUID.randomUUID().toString();
        when(permissions.resourceOwner("PROJECT", project)).thenReturn(List.of(new PermissionProtocol.OwnerRow(project, project)));
        var first = request(); first.put("project_id", project);
        var receipt = save(first, "1", "fixture-key-000001").required("data");
        var append = append(first, receipt);
        for (String state : List.of("PAUSED", "ARCHIVED")) {
            when(permissions.projectStatus(project)).thenReturn(List.of(state));
            var view = service.read(subject, "2", receipt.required("model_config_revision").textValue()).required("data");
            assertEquals(List.of("configuration.read"), new com.fasterxml.jackson.databind.ObjectMapper().convertValue(view.required("allowed_actions"), List.class));
            assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> save(append, "2", "fixture-key-000002")).code());
        }
        assertEquals(1, store.rows.size());
    }

    @Test void otherTenantContextCannotReadKnownFixedRevisionEvenIfMapperReturnsIt() throws Exception {
        var created = save(request(), "1", "fixture-key-000001"); String version = created.required("data").required("model_config_revision").textValue();
        context = new BusinessSessionProtocol.Context(UUID.randomUUID().toString(), "2", "1", "2");
        // The fake deliberately returns the row; real permission authority must still refuse it.
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> service.read(subject, "2", version)).code());
    }

    @Test void missingWrongHashAndCrossProjectDependenciesNeverCreateDrafts() throws Exception {
        store.registry.remove(PRICE);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> save(request(), "1", "fixture-key-000001")).code());
        store.registry.put(PRICE, new PermissionProtocol.VersionRow(PRICE, null, "b".repeat(64)));
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> save(request(), "1", "fixture-key-000001")).code());
        store.registry.put(PRICE, new PermissionProtocol.VersionRow(PRICE, UUID.randomUUID().toString(), HASH));
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> save(request(), "1", "fixture-key-000001")).code());
        assertTrue(store.rows.isEmpty()); assertTrue(store.operations.isEmpty()); assertTrue(store.audits.isEmpty());
    }

    @Test void casAndOutboxFailuresPropagateThroughTransactionBoundary() throws Exception {
        var request = request(); var created = save(request, "1", "fixture-key-000001"); var append = append(request, created.required("data"));
        failCas = true;
        assertEquals(ErrorCode.REVISION_CONFLICT, assertThrows(TenantAccessException.class, () -> save(append, "2", "fixture-key-000002")).code());
        assertEquals(1, store.rows.size()); assertEquals(1, store.operations.size()); assertEquals("2", context.context_revision());
        failCas = false; failOutbox = true;
        assertThrows(IllegalStateException.class, () -> save(append, "2", "fixture-key-000002"));
        assertEquals(1, store.rows.size()); assertEquals(1, store.operations.size()); assertEquals(1, store.audits.size());
        assertEquals("2", context.context_revision()); assertEquals(BigInteger.TWO, governance);
    }

    @Test void corruptedContentAndMissingLineageAreRejectedOnReadAndReplay() throws Exception {
        var request = request(); var created = save(request, "1", "fixture-key-000001"); String version = created.required("data").required("model_config_revision").textValue();
        store.parents.get(version).remove(PRICE);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> service.read(subject, "2", version)).code());
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> save(request, "1", "fixture-key-000001")).code());
        store.parents.get(version).add(PRICE); store.registry.put(POLICY, new PermissionProtocol.VersionRow(POLICY, null, "b".repeat(64)));
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> service.read(subject, "2", version)).code());
    }

    @Test void simulationCreatesCredentialFreeDraftWithOnlyDeclaredCaseBinding() throws Exception {
        store.registry.clear(); var request=SimulationModelConfigurationFixtures.request(json);
        var receipt=saveSimulation(request,"1","simulation-key-000001").required("data");
        String version=receipt.required("model_config_revision").textValue();
        assertEquals("SIMULATION",receipt.required("execution_mode").textValue()); assertFalse(receipt.required("production_eligible").booleanValue());
        var view=service.readSimulation(subject,"2",version).required("data");
        assertEquals("DRAFT",view.required("status").textValue()); assertEquals("DECLARED_SIMULATION_BINDING_ONLY",view.required("dependency_validation").textValue());
        assertEquals("DECLARED_ONLY",view.at("/definition/case_binding/validation").textValue());
        assertEquals(request.required("definition"),view.required("definition")); assertEquals(1,store.registry.size());
        assertTrue(store.parents.getOrDefault(version,new TreeSet<>()).isEmpty()); verify(permissions,never()).basis(anyString());
        for(String key:List.of("auth_ref","base_url","credential_reference_id","credential_revision","price_basis","currency","data_policy"))
            assertFalse(view.required("definition").has(key),key);
        assertEquals(schema.manifestSha256(true),store.rows.get(version).manifest());
        assertEquals("SIMULATION",store.heads.get(receipt.required("config_id").textValue()).executionMode());
    }
    @Test void simulationAppendAndOldRequestReplayPreserveExactHistory() throws Exception {
        var first=SimulationModelConfigurationFixtures.request(json); var original=saveSimulation(first,"1","simulation-key-000001");
        var next=append(first,original.required("data")); ((ObjectNode)next.at("/definition/case_binding")).put("case_sha256","b".repeat(64));
        var second=saveSimulation(next,"2","simulation-key-000002"); int writes=store.writes;
        assertEquals(original,saveSimulation(first,"1","simulation-key-000001")); assertEquals(writes,store.writes);
        String firstId=original.at("/data/model_config_revision").textValue(),secondId=second.at("/data/model_config_revision").textValue();
        assertEquals(Set.of(firstId),store.parents.get(secondId));
        var history=service.readSimulation(subject,"3",firstId).required("data");
        assertEquals("a".repeat(64),history.at("/definition/case_binding/case_sha256").textValue());
        assertEquals(secondId,history.required("current_model_revision").textValue());
        verify(permissions,never()).basis(eq(SimulationModelConfigurationFixtures.CASE));
    }
    @Test void separateEndpointsAndConfigModesCannotBeConvertedOrReplayedAcrossModes() throws Exception {
        var sim=SimulationModelConfigurationFixtures.request(json); var created=saveSimulation(sim,"1","simulation-key-000001");
        String id=created.at("/data/model_config_revision").textValue();
        assertEquals(ErrorCode.OBJECT_UNAVAILABLE,assertThrows(TenantAccessException.class,()->service.read(subject,"2",id)).code());
        var externalAppend=append(request(),created.required("data"));
        assertThrows(TenantAccessException.class,()->save(externalAppend,"2","external-key-000001"));
        assertEquals(1,store.rows.size());
        var external=request(); var externalReceipt=save(external,"2","external-key-000002");
        assertEquals(ErrorCode.OBJECT_UNAVAILABLE,assertThrows(TenantAccessException.class,()->service.readSimulation(subject,"3",externalReceipt.at("/data/model_config_revision").textValue())).code());
        var simAppend=append(sim,externalReceipt.required("data"));
        assertThrows(TenantAccessException.class,()->saveSimulation(simAppend,"3","simulation-key-000002"));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,assertThrows(TenantAccessException.class,()->saveSimulation(sim,"2","external-key-000002")).code());
        assertEquals(2,store.rows.size());
    }
    @Test void simulationCaseHashMutationAndInvalidManifestFailVerifiedReread() throws Exception {
        var request=SimulationModelConfigurationFixtures.request(json); var receipt=saveSimulation(request,"1","simulation-key-000001");
        String id=receipt.at("/data/model_config_revision").textValue(); var row=store.rows.get(id);
        var projection=(ObjectNode)schema.read(row.projection()); projection.put("model_id","SIMULATION:changed");
        store.rows.put(id,new ModelConfigurationProtocol.Stored(row.tenantId(),row.projectId(),row.configId(),row.versionId(),row.definition(),
                json.canonical(projection),row.contentHash(),row.registryHash(),row.status(),row.schemaVersion(),row.manifest(),row.createdBy(),row.createdAt(),row.classification(),row.executionMode()));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.readSimulation(subject,"2",id)).code());
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->saveSimulation(request,"1","simulation-key-000001")).code());
        store.rows.put(id,new ModelConfigurationProtocol.Stored(row.tenantId(),row.projectId(),row.configId(),row.versionId(),row.definition(),
                row.projection(),row.contentHash(),row.registryHash(),row.status(),row.schemaVersion(),schema.manifestSha256(),row.createdBy(),row.createdAt(),row.classification(),row.executionMode()));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.readSimulation(subject,"2",id)).code());
    }
    @Test void simulationReadsRejectCorruptedPublishedHeadEvenWhenRevisionIsDraft() throws Exception {
        var request=SimulationModelConfigurationFixtures.request(json);var receipt=saveSimulation(request,"1","simulation-key-000001");
        var row=store.heads.get(receipt.at("/data/config_id").textValue());
        store.heads.put(row.configId(),new ModelConfigurationProtocol.Head(row.tenantId(),row.projectId(),row.configId(),row.currentVersion(),
                "PUBLISHED",row.revision(),row.classification(),row.executionMode()));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->service.readSimulation(subject,"2",row.currentVersion())).code());
    }
    @Test void simulationReplayCannotLoseItsModeMarkersEvenWithIntactOperationManifest() throws Exception {
        var request=SimulationModelConfigurationFixtures.request(json); saveSimulation(request,"1","simulation-key-000001");
        var operation=store.operations.get("simulation-key-000001"); var response=(ObjectNode)schema.read(operation.responseRef());
        ((ObjectNode)response.required("data")).remove(List.of("execution_mode","production_eligible"));
        store.operations.put("simulation-key-000001",new SelectionOperationRow(operation.operationId(),operation.requestSha256(),operation.state(),
                operation.httpStatus(),json.canonical(response),operation.targetId(),operation.schemaVersion(),operation.schemaManifestSha256()));
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->saveSimulation(request,"1","simulation-key-000001"));
    }
    @Test void simulationRetriesRecheckPermissionsAndCasOutboxFailuresRollback() throws Exception {
        var first=SimulationModelConfigurationFixtures.request(json); var receipt=saveSimulation(first,"1","simulation-key-000001");
        var append=append(first,receipt.required("data")); failCas=true;
        assertEquals(ErrorCode.REVISION_CONFLICT,assertThrows(TenantAccessException.class,()->saveSimulation(append,"2","simulation-key-000002")).code());
        failCas=false; failOutbox=true;
        assertThrows(IllegalStateException.class,()->saveSimulation(append,"2","simulation-key-000002"));
        assertEquals(1,store.rows.size());assertEquals(1,store.events.size());assertEquals(1,store.operations.size());assertEquals("2",context.context_revision());
        failOutbox=false;writeAllowed=false;
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->saveSimulation(first,"1","simulation-key-000001")).code());
        readAllowed=false;
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.readSimulation(subject,"2",receipt.at("/data/model_config_revision").textValue())).code());
    }
    private JsonNode saveSimulation(ObjectNode value,String header,String key) throws Exception {return service.saveSimulation(subject,header,key,bytes(value));}

    private JsonNode save(ObjectNode value, String header, String key) throws Exception { return service.save(subject, header, key, bytes(value)); }
    private byte[] bytes(ObjectNode value) throws Exception { return json.canonical(value).getBytes(StandardCharsets.UTF_8); }
    private ObjectNode request() {
        var definition = new ModelConfigurationProtocol.Definition("fixture-provider", "https://provider.invalid/v1", "fixture-region", "fixture-model", "fixture-v1",
                "secret://engineering/reference", UUID.randomUUID().toString(), "fixture-secret-v1",
                new ModelConfigurationProtocol.Limits("128000", "8000", "2", "10", "100000", 60, false, List.of("none"), 0),
                new PermissionProtocol.Basis(PRICE, HASH), "CNY",
                new ModelConfigurationProtocol.DataPolicy(List.of("INTERNAL"), List.of("fixture-region"), false, "0", new PermissionProtocol.Basis(POLICY, HASH)),
                "urn:medical:model-parameters:1.0.0", HASH, "1.0.0");
        return (ObjectNode) json.tree(new ModelConfigurationProtocol.Save("1.0.0", null, null, "0", null, definition, "Engineering fixture, not provider approval"));
    }
    private ObjectNode append(ObjectNode first, JsonNode receipt) {
        var value = first.deepCopy(); value.set("config_id", receipt.required("config_id")); value.set("expected_revision", receipt.required("revision"));
        value.set("based_on_revision", json.tree(new PermissionProtocol.Basis(receipt.required("model_config_revision").textValue(), receipt.required("content_sha256").textValue()))); return value;
    }
    private PermissionProtocol.GrantRow grant(String action) throws Exception {
        String condition = json.canonicalObject(new PermissionProtocol.Condition("1.0.0", List.of("local"), List.of("MODEL_CONFIGURATION"),
                new PermissionProtocol.Delegation(List.of(), List.of(), List.of(), null, null)));
        return new PermissionProtocol.GrantRow(UUID.nameUUIDFromBytes(action.getBytes(StandardCharsets.UTF_8)).toString(), ACTOR, null,
                "ENTERPRISE", TENANT, action, condition, "ACTIVE", NOW.minusDays(1), null, POLICY, "1.0.0", json.manifestSha256(), BigInteger.ONE);
    }

    private final class MemoryStore implements ModelConfigurationMapper {
        Map<String,ModelConfigurationProtocol.Head> heads = new HashMap<>();
        Map<String,ModelConfigurationProtocol.Stored> rows = new HashMap<>();
        Map<String,PermissionProtocol.VersionRow> registry = new HashMap<>();
        Map<String,SortedSet<String>> parents = new HashMap<>();
        Map<String,SelectionOperationRow> operations = new HashMap<>();
        Map<String,String> audits = new HashMap<>(), events = new HashMap<>();
        int writes;
        Snapshot snapshot() { var copied = new HashMap<String,SortedSet<String>>(); parents.forEach((key, value) -> copied.put(key, new TreeSet<>(value)));
            return new Snapshot(new HashMap<>(heads), new HashMap<>(rows), new HashMap<>(registry), copied, new HashMap<>(operations), new HashMap<>(audits), new HashMap<>(events), writes); }
        void restore(Snapshot state) { heads=state.heads; rows=state.rows; registry=state.registry; parents=state.parents; operations=state.operations; audits=state.audits; events=state.events; writes=state.writes; }
        public List<ModelConfigurationProtocol.Head> head(Map<String,Object> values) { var row=heads.get(values.get("configId")); return row == null ? List.of() : List.of(row); }
        public List<ModelConfigurationProtocol.Stored> stored(Map<String,Object> values) { var row=rows.get(values.get("versionId")); return row == null ? List.of() : List.of(row); }
        public List<String> parents(Map<String,Object> values) { return List.copyOf(parents.getOrDefault(values.get("versionId"), new TreeSet<>())); }
        public int insertHead(Map<String,Object> values) { writes++; heads.put((String)values.get("configId"), new ModelConfigurationProtocol.Head(context.tenant_id(),
                (String)values.get("projectId"), (String)values.get("configId"), null, "DRAFT", BigInteger.ONE, "INTERNAL", (String)values.get("mode"))); return 1; }
        public int insertVersion(Map<String,Object> values) { writes++; registry.put((String)values.get("versionId"), new PermissionProtocol.VersionRow((String)values.get("versionId"),
                (String)values.get("projectId"), (String)values.get("contentHash"))); return 1; }
        public int insertModel(Map<String,Object> values) { writes++; return 1; }
        public int insertDefinition(Map<String,Object> values) {
            writes++; var definition=(ModelConfigurationProtocol.ConfigurationDefinition)values.get("definition");
            var projection=schema.projection(definition);
            try { rows.put((String)values.get("versionId"), new ModelConfigurationProtocol.Stored(context.tenant_id(), (String)values.get("projectId"),
                    (String)values.get("configId"), (String)values.get("versionId"), (String)values.get("content"), json.canonical(projection),
                    (String)values.get("contentHash"), registry.get(values.get("versionId")).sha256(), "DRAFT", "1.0.0", (String)values.get("manifest"), ACTOR, NOW, "INTERNAL", (String)values.get("mode"))); }
            catch (Exception failure) { throw new IllegalStateException(failure); } return 1;
        }
        public int insertLineage(Map<String,Object> values) { writes++; parents.computeIfAbsent((String)values.get("versionId"), ignored -> new TreeSet<>()).add((String)values.get("upstreamId")); return 1; }
        public int initializeHead(Map<String,Object> values) { return move(values, false); }
        public int advanceHead(Map<String,Object> values) { if(failCas) return 0; return move(values, true); }
        private int move(Map<String,Object> values, boolean append) { var old=heads.get(values.get("configId"));
            if(append && (!old.currentVersion().equals(values.get("oldVersion")) || !old.revision().toString().equals(values.get("expected")))) return 0;
            writes++; heads.put(old.configId(), new ModelConfigurationProtocol.Head(old.tenantId(), old.projectId(), old.configId(), (String)values.get("versionId"),
                    "DRAFT", append ? old.revision().add(BigInteger.ONE) : BigInteger.ONE, old.classification(), old.executionMode())); return 1; }
        public List<SelectionOperationRow> currentOperation(Map<String,Object> values) { var row=operations.get(values.get("key")); return row == null ? List.of() : List.of(row); }
        public int saveOperation(Map<String,Object> values) { writes++; operations.put((String)values.get("key"), new SelectionOperationRow((String)values.get("operationId"),
                (String)values.get("requestHash"), "COMPLETED", 201, (String)values.get("response"), (String)values.get("versionId"), "1.0.0", (String)values.get("manifest"))); return 1; }
        public int advanceGovernance(Map<String,Object> values) { if (!governance.toString().equals(values.get("governanceEpoch"))) return 0; writes++; governance=governance.add(BigInteger.ONE); return 1; }
        public List<String> audit(Map<String,Object> values) { return audits.containsKey(values.get("auditId")) ? List.of(audits.get(values.get("auditId"))) : List.of(); }
        public List<String> event(Map<String,Object> values) { return events.containsKey(values.get("eventId")) ? List.of(events.get(values.get("eventId"))) : List.of(); }
    }
    private record Snapshot(Map<String,ModelConfigurationProtocol.Head> heads, Map<String,ModelConfigurationProtocol.Stored> rows,
            Map<String,PermissionProtocol.VersionRow> registry, Map<String,SortedSet<String>> parents,
            Map<String,SelectionOperationRow> operations, Map<String,String> audits, Map<String,String> events, int writes) { }
}
