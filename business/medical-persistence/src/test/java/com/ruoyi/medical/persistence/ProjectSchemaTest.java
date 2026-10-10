package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectSchemaTest {
    @Test
    void absentDuplicateAndTrailingInputsAreRejected() throws Exception {
        var json = new PermissionSchema(); var schema = new ProjectSchema(json);
        assertThrows(SchemaRejection.class, () -> schema.create("{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class, () -> schema.revise("{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class, () -> schema.decide("{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.create("{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.decide("{} {}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class, () -> schema.verify("Project", json.object()));
        assertThrows(SchemaRejection.class, () -> schema.verify("Receipt", json.object()));
    }

    @Test
    void projectStatesFollowEveryPublishedDecisionCombination() {
        var approved = Map.of("ACTIVATE", Map.of("PENDING", "ACTIVE"), "PAUSE", Map.of("ACTIVE", "PAUSED"),
                "ARCHIVE", Map.of("PENDING", "ARCHIVED", "ACTIVE", "ARCHIVED", "PAUSED", "ARCHIVED"),
                "RESTORE", Map.of("PAUSED", "ACTIVE", "ARCHIVED", "ACTIVE"));
        for (String status : List.of("PENDING", "ACTIVE", "PAUSED", "ARCHIVED")) for (String decision : approved.keySet()) {
            if (approved.get(decision).containsKey(status)) assertEquals(approved.get(decision).get(status), ProjectService.nextStatus(status, decision));
            else assertEquals(ErrorCode.REVISION_CONFLICT, assertThrows(TenantAccessException.class, () -> ProjectService.nextStatus(status, decision)).code());
        }
    }

    @Test
    void projectSchemaRetainsOriginalPermissionManifest() throws Exception {
        var permissions = new PermissionSchema(); var before = permissions.manifestSha256(); var projects = new ProjectSchema(permissions);
        assertEquals(before, permissions.manifestSha256()); assertTrue(projects.manifestSha256().matches("[0-9a-f]{64}"));
        assertNotEquals(before, projects.manifestSha256());
    }

    @Test
    void allProjectQueriesUseNativeParsersAndPublishedColumns() throws Exception {
        var root = java.nio.file.Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new org.springframework.jdbc.datasource.DriverManagerDataSource()).getConfiguration();
        var values = new java.util.HashMap<String,Object>(); values.put("enterprise", false);
        values.put("allowedProjects", List.of(UUID.randomUUID().toString())); values.put("limit", 1);
        for (String key : List.of("after", "name", "status", "responsible", "oldProfile", "projectId")) values.put(key, UUID.randomUUID().toString());
        var statements = new java.util.TreeMap<String,String>();
        for (String name : List.of("grants", "project", "profile", "profileRegistry", "projects", "projectCount", "insertProject", "insertVersion", "insertProfile",
                "insertRecord", "advanceProfile", "advanceProject", "advanceRecord", "saveDecision", "decision", "currentOperation", "saveOperation",
                "advanceGovernance", "taskCount", "tasks", "runCount", "runs", "waitingCount", "waiting", "audit", "event")) {
            String sql = configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.ProjectMapper." + name).getBoundSql(values).getSql();
            assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql)); statements.put(name, sql);
        }
        var output = root.resolve(".implementation-work/project-query-columns.json");
        java.nio.file.Files.write(output, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(statements));
        var process = new ProcessBuilder(root.resolve(".lld-data-work/venv/bin/python").toString(), root.resolve("tests/check_project_sql_columns.py").toString(), output.toString())
                .directory(root.toFile()).redirectErrorStream(true)
                .redirectOutput(root.resolve(".implementation-work/project-query-columns-output.log").toFile()).start();
        assertEquals(0, process.waitFor());
    }
}
