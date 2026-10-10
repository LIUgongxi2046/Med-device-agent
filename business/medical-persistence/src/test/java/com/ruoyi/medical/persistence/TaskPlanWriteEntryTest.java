package com.ruoyi.medical.persistence;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;
import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.ErrorCode;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import static org.junit.jupiter.api.Assertions.*;

class TaskPlanWriteEntryTest {
    @Test void exposesActualPlanDraftSaveAndFixedVersionRead() {
        var methods = Arrays.asList(TaskController.class.getDeclaredMethods());
        assertTrue(methods.stream().filter(method -> method.isAnnotationPresent(PostMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(PostMapping.class).value()))
                .anyMatch("/api/tasks/{taskId}/plan-revisions"::equals), "计划草稿需要公开保存入口");
        assertTrue(methods.stream().filter(method -> method.isAnnotationPresent(GetMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(GetMapping.class).value()))
                .anyMatch("/api/tasks/{taskId}/plan-revisions/{planRevision}"::equals), "保存版本需要独立重新读取入口");
        assertTrue(methods.stream().filter(method -> method.isAnnotationPresent(GetMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(GetMapping.class).value()))
                .anyMatch("/api/tasks/{taskId}/plan-revisions"::equals), "刷新页面需要取得当前维护计划");
        for (String path : List.of("/api/tasks/{taskId}/plan-edit-basis", "/api/tasks/{taskId}/plan-operations")) {
            assertTrue(methods.stream().filter(method -> method.isAnnotationPresent(GetMapping.class))
                    .flatMap(method -> Arrays.stream(method.getAnnotation(GetMapping.class).value())).anyMatch(path::equals),
                    "计划编辑与原保存需要公开读取入口");
        }
    }

    @Test void completePublishedPlanViewPreservesContentAndRejectsExtraFields() throws Exception {
        var shared = SchemaCatalogue.fromClasspath(); var json = new PermissionSchema();
        var catalogue = RegistrationDirectory.fromClasspath().catalogue();
        var nodeIds = catalogue.nodes().stream().map(entry -> UUID.nameUUIDFromBytes((catalogue.profileId() + ":" + entry.officialNumber())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()).toList();
        String task = id(), revision = id(), plan = id(), template = id(), profile = id();
        var content = new ArtifactProtocol.PlanCandidate(template, profile, nodeIds,
                List.of(new ArtifactProtocol.PlanSection(id(), nodeIds, "计划结构与原目录检查", null,
                        ArtifactProtocol.Method.DETERMINISTIC, List.of(), List.of())), List.of(), List.of());
        var view = new TaskPlanWriteProtocol.View("1.0.0", id(), id(), task, revision, plan, id(), template, profile,
                "DRAFT", content, DigestUtils.sha256Hex(json.canonicalObject(content)), plan, "1");
        var value = json.tree(view); var bound = shared.bind("task-plan-view", value, "View");
        assertEquals(value, com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(bound.canonicalJson()));
        assertEquals(json.tree(content), value.required("content"));
        var extra = value.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) extra).put("approval_attested", true);
        assertThrows(SchemaRejection.class, () -> shared.bind("task-plan-view", extra, "View"));
        var incomplete = value.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) incomplete).putNull("current_plan_head_revision");
        assertThrows(SchemaRejection.class, () -> shared.bind("task-plan-view", incomplete, "View"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) incomplete).putNull("current_plan_revision");
        assertNotNull(shared.bind("task-plan-view", incomplete, "View"));
    }

    @Test void ownerQualificationUsesActualDatabaseColumnsAndProtectedSubject() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        var secrets = json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        var statement = factory.getConfiguration().getMappedStatement("com.ruoyi.medical.persistence.mapper.TaskPlanWriteMapper.ownerGrants");
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials)) {
            String actor;
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
                assertTrue(rows.next()); actor = rows.getString(1); assertFalse(rows.next());
            }
            var values = Map.of("userId", actor, "ownerId", id()); var bound = statement.getBoundSql(values);
            try (var prepared = connection.prepareStatement(bound.getSql())) {
                new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, values, bound).setParameters(prepared);
                try (var rows = prepared.executeQuery()) {
                    var constructor = statement.getResultMaps().get(0).getConstructorResultMappings();
                    assertEquals(constructor.size(), rows.getMetaData().getColumnCount());
                    for (int index = 0; index < constructor.size(); index++) assertEquals(constructor.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                    assertFalse(rows.next(), "未登记的责任人员不能取得任务读取许可");
                }
            }
        }
        try (var session = factory.openSession()) {
            var error = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, () -> session.selectList(statement.getId(), Map.of("userId", id(), "ownerId", id())));
            assertEquals(ErrorCode.ACCESS_DENIED, assertInstanceOf(TenantAccessException.class, error.getCause()).code());
        }
    }

    private static String id() { return UUID.randomUUID().toString(); }
}
