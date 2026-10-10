package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class RunReadTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.RunReadMapper.";
    private static final List<String> READS = List.of("head", "steps", "waiting", "invocations", "budget", "cursor", "stream", "eventCursor", "events", "eventAuthority");

    @Test void persistentSnapshotQueriesExecuteWithActualColumnsAndClosedEnterpriseAccess() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        String id = java.util.UUID.randomUUID().toString();
        var values = new java.util.HashMap<String,Object>();
        for (String name : List.of("userId", "runId", "projectId", "budgetRevision", "streamId", "eventId", "taskId", "id")) values.put(name, id);
        values.put("sequence", "0"); values.put("kind", "SOURCE_VERSION");
        try (var database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
                var session = factory.openSession()) {
            for (String name : READS) {
                assertTrue(factory.getConfiguration().hasStatement(PREFIX + name), "运行读取必须连接全部持久查询");
                var statement = factory.getConfiguration().getMappedStatement(PREFIX + name);
                assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
                var bound = statement.getBoundSql(values);
                try (var query = database.prepareStatement(bound.getSql())) {
                    new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, values, bound).setParameters(query);
                    try (var rows = query.executeQuery()) { assertFalse(rows.next()); assertTrue(rows.getMetaData().getColumnCount() > 0); }
                }
                var failure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                    () -> session.selectList(PREFIX + name, values));
                assertInstanceOf(TenantAccessException.class, failure.getCause());
            }
            for (String kind : List.of("WAITING_ITEM", "CANDIDATE", "DOCUMENT", "EXPORT", "NOTIFICATION")) {
                values.put("kind", kind);
                var statement = factory.getConfiguration().getMappedStatement(PREFIX + "eventAuthority");
                var bound = statement.getBoundSql(values);
                try (var query = database.prepareStatement(bound.getSql())) {
                    new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, values, bound).setParameters(query);
                    try (var rows = query.executeQuery()) { assertFalse(rows.next()); }
                }
            }
        }
    }

    @Test void publishedRunSnapshotRequiresCompleteFieldsAndKeepsUnknownCosts() throws Exception {
        var catalogue = com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath();
        try (var input = getClass().getResourceAsStream("/run-view.schema.json")) {
            assertNotNull(input, "RN-02需要完整封闭输出定义");
            var definition = JsonMapper.builder().build().readTree(input);
            var schema = catalogue.compileApiSchema(definition);
            String id = java.util.UUID.randomUUID().toString();
            var value = JsonMapper.builder().build().createObjectNode();
            value.put("run_id", id); value.put("task_id", id); value.put("task_revision", id);
            value.put("status", "RUNNING"); value.putNull("reason_code"); value.putArray("steps");
            value.putArray("waiting_items"); value.putArray("inflight_invocations");
            var budget = value.putObject("budget"); budget.put("budget_id", id); budget.put("budget_revision", id);
            budget.put("spent", "0.00000000"); budget.put("reserved", "1.23000000"); budget.put("limit", "20.00000000"); budget.put("currency", "CNY");
            value.putNull("started_at"); value.putNull("ended_at"); value.put("revision", "1"); value.putNull("event_cursor");
            assertTrue(schema.validate(value).isEmpty());
            var call = value.withArray("inflight_invocations").addObject();
            call.put("invocation_id", id); call.put("step_id", id); call.put("action_id", id); call.put("status", "UNKNOWN");
            call.put("settlement_status", "PENDING"); call.put("currency", "CNY"); call.putNull("settled_amount");
            call.putNull("total_tokens"); call.put("revision", "1");
            assertTrue(schema.validate(value).isEmpty(), "未知调用必须保留空结算金额与Token数量");
            call.put("settlement_status", "SETTLED"); assertFalse(schema.validate(value).isEmpty());
            call.put("settlement_status", "PENDING"); value.remove("budget"); assertFalse(schema.validate(value).isEmpty());
        }
    }
}
