package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import static org.junit.jupiter.api.Assertions.*;

class TaskPlanDecisionEntryTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.TaskPlanWriteMapper.";

    @Test void exposesPlanDecisionAndExplicitOriginalOperationRead() {
        var methods = List.of(TaskController.class.getDeclaredMethods());
        assertTrue(methods.stream().filter(method -> method.isAnnotationPresent(PostMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(PostMapping.class).value()))
                .anyMatch("/api/tasks/{taskId}/plan-decisions"::equals), "人工决定需要公开提交入口");
        for (String path : List.of("/api/tasks/{taskId}/plan-decision-basis", "/api/tasks/{taskId}/plan-decision-operations"))
            assertTrue(methods.stream().filter(method -> method.isAnnotationPresent(GetMapping.class))
                    .flatMap(method -> Arrays.stream(method.getAnnotation(GetMapping.class).value())).anyMatch(path::equals),
                    "人工决定需要当前依据和原操作读取");
    }

    @Test void actualDecisionReadPreservesCompleteColumnsAndProtectedSubject() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials)) {
            String actor;
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
                assertTrue(rows.next()); actor = rows.getString(1); assertFalse(rows.next());
            }
            var values = Map.of("userId", actor, "projectId", actor, "taskId", actor, "operationId", actor, "taskRevision", actor);
            for (String name : List.of("decision", "decisionTask")) {
                assertTrue(factory.getConfiguration().hasStatement(PREFIX + name), "人工决定需要完整持久记录重新读取");
                var statement = factory.getConfiguration().getMappedStatement(PREFIX + name);
                assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
                var bound = statement.getBoundSql(values);
                try (var prepared = connection.prepareStatement(bound.getSql())) {
                    new DefaultParameterHandler(statement, values, bound).setParameters(prepared);
                    try (var rows = prepared.executeQuery()) {
                        var columns = statement.getResultMaps().get(0).getConstructorResultMappings();
                        assertEquals("decision".equals(name) ? 31 : 18, columns.size()); assertEquals(columns.size(), rows.getMetaData().getColumnCount());
                        for (int index = 0; index < columns.size(); index++)
                            assertEquals(columns.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                        assertFalse(rows.next(), "平台账户不能取得业务人工决定记录");
                    }
                }
                try (var session = factory.openSession()) {
                    var error = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, () -> session.selectList(statement.getId(), values));
                    assertEquals(ErrorCode.ACCESS_DENIED, assertInstanceOf(TenantAccessException.class, error.getCause()).code());
                }
            }
        }
    }

    @Test void actualReferenceDatasetCannotBeSubmittedAsAnAdministrativeDecision() throws Exception {
        var json = new PermissionSchema(); var schema = new TaskPlanDecisionSchema(json, new TaskInputSchema(json));
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var failure = assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.request(Files.readAllBytes(root.resolve("data-design/sample_dataset.json"))));
        assertEquals(ErrorCode.SCHEMA_INVALID, failure.code());
    }

    @Test void emptyOrTrailingRequestsCannotProduceAdministrativeApproval() throws Exception {
        var json = new PermissionSchema(); var schema = new TaskPlanDecisionSchema(json, new TaskInputSchema(json));
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> schema.request("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> schema.request("{} {}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test void authorizedSimulationReturnPreservesTaskAndRejectsConflictingDecisionFields() throws Exception {
        var json = new PermissionSchema(); var schema = new TaskPlanDecisionSchema(json, new TaskInputSchema(json));
        String tenant = id(), project = id(), task = id(), taskRevision = id(), plan = id(), actor = id();
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        String actualHash = DigestUtils.sha256Hex(Files.readAllBytes(root.resolve("data-design/sample_dataset.json")));
        String authorizationsHash = DigestUtils.sha256Hex(json.canonical(json.tree(List.of())));
        var baseline = new TaskPlanDecisionProtocol.Baseline("1.0.0", tenant, project, task, taskRevision, plan, actualHash,
                id(), id(), id(), actualHash, actualHash, actualHash, actualHash, "PLAN_PENDING", "1", "1", "1", "1", "1", authorizationsHash);
        var request = new TaskPlanDecisionProtocol.Request("1.0.0", plan, "1", "RETURN", "SIMULATION 退回任务计划", baseline);
        assertEquals(request, schema.request(json.canonicalObject(request).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var conflict = (com.fasterxml.jackson.databind.node.ObjectNode) json.tree(request); conflict.put("target_revision", id());
        var wrongTarget = conflict;
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> schema.request(json.canonical(wrongTarget).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        conflict = (com.fasterxml.jackson.databind.node.ObjectNode) json.tree(request); conflict.put("decision", "APPROVE");
        var unavailable = conflict;
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> schema.request(json.canonical(unavailable).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var receipt = new TaskPlanDecisionProtocol.Receipt("1.0.0", id(), id(), tenant, project, task, plan, actualHash,
                taskRevision, taskRevision, null, "RETURN", "PLAN_PENDING", "PLAN_PENDING", "1", "2", actor,
                java.time.Instant.now().toString(), request.reason());
        var response = schema.envelope(receipt, id()); assertEquals(response, schema.response(json.canonical(response)));
        var falseState = new TaskPlanDecisionProtocol.Receipt("1.0.0", receipt.operationId(), receipt.decisionId(), tenant, project, task, plan, actualHash,
                taskRevision, id(), null, "RETURN", "PLAN_PENDING", "PLAN_PENDING", "1", "2", actor, receipt.decidedAt(), receipt.reason());
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> schema.envelope(falseState, id()));
        var falseRevision = new TaskPlanDecisionProtocol.Receipt("1.0.0", receipt.operationId(), receipt.decisionId(), tenant, project, task, plan, actualHash,
                taskRevision, taskRevision, null, "RETURN", "PLAN_PENDING", "PLAN_PENDING", "1", "3", actor, receipt.decidedAt(), receipt.reason());
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> schema.envelope(falseRevision, id()));
    }

    @Test void confirmationRequiresNewTaskAndPlanVersionsAndReadyState() throws Exception {
        var json = new PermissionSchema(); var schema = new TaskPlanDecisionSchema(json, new TaskInputSchema(json));
        String tenant = id(), project = id(), task = id(), input = id(), draft = id(), actor = id();
        String hash = DigestUtils.sha256Hex(Files.readAllBytes(Path.of(System.getProperty("medical.projectRoot")).resolve("data-design/sample_dataset.json")));
        var baseline = new TaskPlanDecisionProtocol.Baseline("1.0.0", tenant, project, task, input, draft, hash,
                id(), id(), id(), hash, hash, hash, hash, "PLAN_PENDING", "3", "2", "1", "1", "1", hash);
        var request = new TaskPlanDecisionProtocol.Request("1.0.0", draft, "3", "CONFIRM", "SIMULATION 确认任务计划", baseline);
        assertEquals(request, schema.request(json.canonicalObject(request).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var receipt = new TaskPlanDecisionProtocol.Receipt("1.0.0", id(), id(), tenant, project, task, draft, hash,
                input, id(), id(), "CONFIRM", "PLAN_PENDING", "READY", "3", "4", actor,
                java.time.Instant.now().toString(), request.reason());
        assertEquals(schema.envelope(receipt, receipt.operationId()), schema.response(json.canonical(schema.envelope(receipt, receipt.operationId()))));
        var unchangedInput = new TaskPlanDecisionProtocol.Receipt("1.0.0", receipt.operationId(), receipt.decisionId(), tenant, project, task, draft, hash,
                input, input, receipt.resultingPlanRevision(), "CONFIRM", "PLAN_PENDING", "READY", "3", "4", actor, receipt.decidedAt(), receipt.reason());
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> schema.envelope(unchangedInput, id()));
        var unchangedPlan = new TaskPlanDecisionProtocol.Receipt("1.0.0", receipt.operationId(), receipt.decisionId(), tenant, project, task, draft, hash,
                input, receipt.resultingTaskRevision(), draft, "CONFIRM", "PLAN_PENDING", "READY", "3", "4", actor, receipt.decidedAt(), receipt.reason());
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> schema.envelope(unchangedPlan, id()));
        var missing = (com.fasterxml.jackson.databind.node.ObjectNode) json.tree(request);
        ((com.fasterxml.jackson.databind.node.ObjectNode) missing.required("baseline_manifest")).put("task_status", "NEEDS_MATERIALS");
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> schema.request(json.canonical(missing).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static String id() { return UUID.randomUUID().toString(); }
}
