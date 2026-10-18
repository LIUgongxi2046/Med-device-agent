package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.bind.annotation.PostMapping;
import static org.junit.jupiter.api.Assertions.*;

class RunCreateBoundaryTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.RunCreateMapper.";
    private static final List<String> READS = List.of("operation", "activeRun", "attempts", "budget", "created");
    private static final List<String> WRITES = List.of("insertRegistry", "insertRun", "insertPrincipal", "insertActiveRun", "advanceTask", "insertStream", "saveCommand", "saveOperation");
    private final PermissionSchema json = new PermissionSchema();
    private final RunCreateSchema schema = new RunCreateSchema(json);
    RunCreateBoundaryTest() throws Exception { }

    @Test void closedRequestPreservesAllPinnedVersionsAndUnsignedRevision() throws Exception {
        var input = request(); var raw = bytes(input);
        var parsed = schema.request(raw);
        assertEquals(input.get("task_revision").textValue(), parsed.task_revision());
        assertEquals(input.get("plan_revision").textValue(), parsed.plan_revision());
        assertEquals(input.get("snapshot_id").textValue(), parsed.snapshot_id());
        assertEquals(input.get("budget_revision").textValue(), parsed.budget_revision());
        input.put("expected_revision", "18446744073709551615"); assertNotNull(schema.request(bytes(input)));
        input.put("expected_revision", "18446744073709551616"); assertThrows(SchemaRejection.class, () -> schema.request(bytes(input)));
    }

    @Test void rejectsUntrustedTenantIdentityAndIncompleteOrMalformedRequests() throws Exception {
        for (String field : List.of("schema_version", "task_revision", "plan_revision", "snapshot_id", "budget_revision", "expected_revision")) {
            var missing = request(); missing.remove(field); assertThrows(SchemaRejection.class, () -> schema.request(bytes(missing)), field);
        }
        for (String field : List.of("tenant_id", "user_id", "status", "replay_manifest", "principal_manifest", "plan_confirmed")) {
            var extra = request(); extra.put(field, id()); assertThrows(SchemaRejection.class, () -> schema.request(bytes(extra)), field);
        }
        var invalid = request(); invalid.put("budget_revision", "not-a-uuid"); assertThrows(SchemaRejection.class, () -> schema.request(bytes(invalid)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.request("{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.request((json.canonical(request()) + " {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class, () -> schema.request("null".getBytes(StandardCharsets.UTF_8)));
    }

    @Test void onlyCurrentConfirmedReadyTaskCanStartAndNoOverflow() throws Exception {
        var input = schema.request(bytes(request())); var head = head(input);
        assertDoesNotThrow(() -> RunCreateSchema.requireReady(head, input));
        for (String status : List.of("PLAN_PENDING", "NEEDS_MATERIALS", "RUNNING", "CANCEL_REQUESTED", "CANCELLED", "SUCCEEDED")) {
            var other = new TaskProtocol.Head(head.tenantId(), head.projectId(), head.taskId(), status, head.revision(), head.ownerId(), head.taskRevision(),
                head.profileRevision(), head.snapshotId(), head.templateVersion(), head.agentReleaseId(), head.planRevision(), head.contentHash(), head.registryHash(), head.updatedAt());
            assertEquals(ErrorCode.PLAN_UNCONFIRMED, assertThrows(TenantAccessException.class, () -> RunCreateSchema.requireReady(other, input)).code());
        }
        var stale = new RunCreateSchema.Create("1.0.0", id(), input.plan_revision(), input.snapshot_id(), input.budget_revision(), input.expected_revision());
        assertEquals(ErrorCode.PLAN_STALE, assertThrows(TenantAccessException.class, () -> RunCreateSchema.requireReady(head, stale)).code());
        var revision = new RunCreateSchema.Create("1.0.0", input.task_revision(), input.plan_revision(), input.snapshot_id(), input.budget_revision(), "2");
        assertEquals(ErrorCode.REVISION_CONFLICT, assertThrows(TenantAccessException.class, () -> RunCreateSchema.requireReady(head, revision)).code());
        assertEquals(ErrorCode.REVISION_LIMIT, assertThrows(TenantAccessException.class, () -> RunCreateSchema.next("18446744073709551615")).code());
    }

    @Test void canonicalIdempotencyHashBindsTaskAndEveryPinnedVersion() throws Exception {
        var original = request(); var request = schema.request(bytes(original)); String task = id(), hash = schema.requestHash(task, request);
        assertEquals(hash, schema.requestHash(task, schema.request(bytes(original))));
        assertNotEquals(hash, schema.requestHash(id(), request));
        for (String field : List.of("task_revision", "plan_revision", "snapshot_id", "budget_revision", "expected_revision")) {
            var changed = original.deepCopy(); changed.put(field, field.equals("expected_revision") ? "2" : id());
            assertNotEquals(hash, schema.requestHash(task, schema.request(bytes(changed))), field);
        }
    }

    @Test void acceptanceIsQueuedAndProcessingAndNeverExecutionSuccess() throws Exception {
        var data = receipt(); var response = schema.receipt(data, id());
        assertEquals(response, schema.response(json.canonical(response)));
        assertEquals("1", response.required("resource_revision").textValue());
        for (String status : List.of("RUNNING", "SUCCEEDED", "FAILED", "CANCELLED")) {
            var wrong = data.deepCopy().put("status", status); assertThrows(SchemaRejection.class, () -> schema.receipt(wrong, id()));
        }
        var complete = data.deepCopy(); ((ObjectNode)complete.required("operation")).put("status", "COMPLETED");
        assertThrows(SchemaRejection.class, () -> schema.receipt(complete, id()));
        var unrelated = data.deepCopy(); ((ObjectNode)unrelated.required("operation")).put("resource_id", id());
        assertThrows(SchemaRejection.class, () -> schema.receipt(unrelated, id()));
        var redirected = data.deepCopy(); ((ObjectNode)redirected.required("operation")).put("status_url", "/api/operations/" + id());
        assertThrows(SchemaRejection.class, () -> schema.receipt(redirected, id()));
    }

    @Test void missingActualReleaseConsumerFailsClosed() {
        var factory = new StaticListableBeanFactory();
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class,
            () -> RunCreateService.requireProvider(factory.getBeanProvider(RunCreateAdmission.class))).code());
    }

    @Test void budgetIncludesExistingReservationsAndUnknownUnsettledWork() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 16, 0, 0);
        var limits = new BudgetProtocol.Limits("10.00", "1000", "10", "10", "10", "2026-10-17T00:00:00Z");
        var account = json.object().put("spent", "2.50").put("reserved", "1.25").put("spent_tokens", "200")
            .put("reserved_tokens", "100").put("spent_calls", "2").put("reserved_calls", "1");
        assertDoesNotThrow(() -> RunCreateBudget.requireAvailable(account, limits, now));
        for (String field : List.of("reserved", "reserved_tokens", "reserved_calls")) {
            var exhausted = account.deepCopy(); exhausted.put(field, switch (field) { case "reserved" -> "7.50"; case "reserved_tokens" -> "800"; default -> "8"; });
            assertEquals(ErrorCode.BUDGET_EXCEEDED, assertThrows(TenantAccessException.class, () -> RunCreateBudget.requireAvailable(exhausted, limits, now)).code());
        }
        assertEquals(ErrorCode.BUDGET_EXCEEDED, assertThrows(TenantAccessException.class,
            () -> RunCreateBudget.requireAvailable(account, limits, now.plusDays(1))).code());
    }

    @Test void mapperStatementsArePersistedScopedAndNeverUseCachedAdmission() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        String actor = id(); var values = new java.util.HashMap<String,Object>();
        for (String name : List.of("userId", "runId", "projectId", "taskId", "taskRevision", "budgetRevision", "commandId")) values.put(name, actor);
        for (String name : READS) {
            var statement = factory.getConfiguration().getMappedStatement(PREFIX + name); assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
            assertTrue(statement.getBoundSql(values).getSql().contains("FOR UPDATE"));
            try (var session = factory.openSession()) {
                var error = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, () -> session.selectList(PREFIX + name, values));
                assertInstanceOf(TenantAccessException.class, error.getCause());
            }
        }
        for (String name : WRITES) assertTrue(factory.getConfiguration().hasStatement(PREFIX + name));
        try (var ignored = TenantScope.open(new TenantScope.Identity(id(), actor, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE))) {
            var interceptor = new ProtectedTenantInterceptor();
            for (String name : java.util.stream.Stream.concat(READS.stream(), WRITES.stream()).toList()) {
                String sql = factory.getConfiguration().getMappedStatement(PREFIX + name).getBoundSql(values).getSql();
                assertTrue(interceptor.parserSingle(sql, null).contains("tenant_id"), name + " must receive server tenant scoping");
                assertDoesNotThrow(() -> TenantScope.requireSubjectParameter(PREFIX + name, values));
                assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                    () -> TenantScope.requireSubjectParameter(PREFIX + name, Map.of("userId", id()))).code());
            }
        }
        String insert = factory.getConfiguration().getMappedStatement(PREFIX + "insertRun").getBoundSql(values).getSql();
        assertTrue(insert.contains("'QUEUED'")); assertFalse(insert.contains("'SUCCEEDED'"));
        String cas = factory.getConfiguration().getMappedStatement(PREFIX + "advanceTask").getBoundSql(values).getSql();
        assertTrue(cas.contains("revision=?") && cas.contains("status='READY'")); assertFalse(cas.contains("status='RUNNING'"));
    }

    @Test void publicCreationRejectsQueriesAndDuplicateHeadersBeforeIdentityOrWrite() throws Exception {
        var controller = new RunCreateController(null, null);
        var method = RunCreateController.class.getMethod("create", jakarta.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class,
            org.springframework.security.core.Authentication.class, String.class, String.class, String.class, String.class, byte[].class);
        assertArrayEquals(new String[]{"/api/tasks/{taskId}/runs"}, method.getAnnotation(PostMapping.class).value());
        assertThrows(SchemaRejection.class, () -> controller.create(http("tenant_id=" + id(), false), null, null, id(), "1.0.0", "1", id(), bytes(request())));
        assertThrows(SchemaRejection.class, () -> controller.create(http(null, true), null, null, id(), "1.0.0", "1", id(), bytes(request())));
        assertEquals(ErrorCode.SCHEMA_UNSUPPORTED, assertThrows(SchemaRejection.class,
            () -> controller.create(null, null, null, id(), "2.0.0", "1", id(), bytes(request()))).code());
    }

    private static jakarta.servlet.http.HttpServletRequest http(String query, boolean duplicate) {
        return (jakarta.servlet.http.HttpServletRequest)java.lang.reflect.Proxy.newProxyInstance(RunCreateBoundaryTest.class.getClassLoader(),
            new Class<?>[]{jakarta.servlet.http.HttpServletRequest.class}, (object, method, args) -> switch (method.getName()) {
                case "getQueryString" -> query;
                case "getHeaders" -> java.util.Collections.enumeration(duplicate ? List.of("1", "2") : List.of("1"));
                default -> throw new AssertionError("Unexpected HTTP access: " + method.getName());
            });
    }
    private ObjectNode request() { return json.object().put("schema_version", "1.0.0").put("task_revision", id()).put("plan_revision", id())
        .put("snapshot_id", id()).put("budget_revision", id()).put("expected_revision", "1"); }
    private ObjectNode receipt() {
        var input = request(); var data = json.object().put("run_id", id()).put("task_id", id()).put("command_id", id()).put("status", "QUEUED").put("revision", "1");
        for (String name : List.of("task_revision", "plan_revision", "snapshot_id", "budget_revision")) data.set(name, input.required(name));
        String operation = id(); data.set("operation", json.object().put("operation_id", operation).put("resource_id", data.get("run_id").textValue())
            .put("status", "PROCESSING").put("status_url", "/api/operations/" + operation)); return data;
    }
    private TaskProtocol.Head head(RunCreateSchema.Create request) {
        return new TaskProtocol.Head(id(), id(), id(), "READY", BigInteger.ONE, id(), request.task_revision(), id(), request.snapshot_id(), id(), id(),
            request.plan_revision(), "a".repeat(64), "a".repeat(64), LocalDateTime.of(2026, 10, 16, 0, 0));
    }
    private byte[] bytes(JsonNode value) throws Exception { return json.canonical(value).getBytes(StandardCharsets.UTF_8); }
    private static String id() { return UUID.randomUUID().toString(); }
}
