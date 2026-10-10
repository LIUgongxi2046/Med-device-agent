package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.ResourceProtocol;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskListSchemaTest {
    private static Connection database;
    private static TaskListSchema schema;
    private static PermissionSchema json;
    private static String owner;
    private static String username;
    private static LocalDateTime now;

    @BeforeAll static void actualDatabaseAndSchema() throws Exception {
        json = new PermissionSchema(); schema = new TaskListSchema(json, new TaskInputSchema(json));
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        try (var statement = database.createStatement(); var rows = statement.executeQuery(
                "SELECT user_id,username,UTC_TIMESTAMP(6) FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
            assertTrue(rows.next()); owner = rows.getString(1); username = rows.getString(2);
            now = rows.getObject(3, LocalDateTime.class); assertFalse(rows.next());
        }
    }

    @Test void currentOwnerAndEveryPublishedStatusUseOriginalSharedDefinitions() {
        var defaults = schema.query(Map.of()); assertEquals(1, defaults.page()); assertEquals(20, defaults.pageSize());
        for (var status : ResourceProtocol.TaskStatus.values()) {
            var value = schema.query(Map.of("status", new String[]{status.name()}, "owner_id", new String[]{owner},
                "updated_from", new String[]{now.atOffset(ZoneOffset.UTC).toString()},
                "updated_to", new String[]{now.atOffset(ZoneOffset.UTC).toString()}));
            assertEquals(status.name(), value.status()); assertEquals(owner, value.ownerId());
            assertEquals(now, value.updatedFrom()); assertEquals(now, value.updatedTo());
        }
    }

    @Test void originalPageInfoValidatesActualEmptyDatabaseCountsWithoutCreatingBusinessRecords() throws Exception {
        String sql = "SELECT CAST(COALESCE(SUM(revision),0) AS CHAR),COUNT(*) FROM task_head";
        String watermark; String total;
        try (var statement = database.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next()); watermark = rows.getString(1); total = rows.getString(2);
            assertEquals("0", total); assertFalse(rows.next());
        }
        String hash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(sql);
        var query = schema.query(Map.of("page", new String[]{"2"}, "dataset_watermark", new String[]{watermark}, "filter_sha256", new String[]{hash}));
        assertEquals(2, query.page()); assertEquals(watermark, query.watermark()); assertEquals(hash, query.filterSha256());
        // 当前空表只验证Schema复用，不表示已经取得企业业务授权。
        var info = json.object(); info.put("page", 1); info.put("page_size", 20); info.put("total", total);
        info.put("watermark", watermark); info.put("filter_sha256", hash);
        var page = json.object(); page.putArray("items"); page.set("page_info", info);
        assertEquals(watermark, schema.envelope(page, owner, watermark).required("resource_revision").textValue());
        page.put("unknown", owner);
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class, () -> schema.verify("Page", page)).code());
    }

    @Test void closedFiltersAndIncompletePagesFailBeforeEnterpriseQueries() {
        for (var parameters : java.util.List.of(
                Map.of("page", new String[]{"0"}), Map.of("page", new String[]{"2"}),
                Map.of("page", new String[]{"1", "1"}), Map.of("page_size", new String[]{"101"}),
                Map.of("owner_id", new String[]{username}), Map.of("status", new String[]{username}),
                Map.of("dataset_watermark", new String[]{"0"}), Map.of("updated_from", new String[]{username}),
                Map.of("unknown", new String[]{owner}),
                Map.of("updated_from", new String[]{now.atOffset(ZoneOffset.UTC).toString()}, "updated_to", new String[]{now.minusNanos(1000).atOffset(ZoneOffset.UTC).toString()}),
                Map.of("updated_from", new String[]{now.plusNanos(1).atOffset(ZoneOffset.UTC).toString()}))) {
            assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class, () -> schema.query(parameters)).code());
        }
        var page = json.object(); page.putArray("items"); page.set("page_info", json.object());
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class, () -> schema.verify("Page", page)).code());
    }

    @Test void taskListItemKeepsPublishedDetailAndActualBusinessMetadata() throws Exception {
        com.fasterxml.jackson.databind.JsonNode document;
        try (var input = getClass().getResourceAsStream("/task-list.schema.json")) {
            assertNotNull(input); document = JsonMapper.builder().build().readTree(input);
        }
        var item = document.path("$defs").path("Item");
        assertTrue(item.isObject(), "列表项必须包含原详情和实际目标、文档类型与时间");
        assertFalse(item.required("additionalProperties").booleanValue());
        assertEquals(json.tree(java.util.List.of("detail", "document_type", "goal", "updated_at")), item.required("required"));
        assertEquals("https://schemas.medical-agent.invalid/1.0.0/backend-design/resource-details.schema.yaml#/$defs/TaskDetail",
            item.required("properties").required("detail").required("$ref").textValue());
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class, () -> schema.verify("Item", json.object())).code());
    }

    @AfterAll static void closeDatabase() throws Exception { if (database != null && !database.isClosed()) database.close(); }
}
