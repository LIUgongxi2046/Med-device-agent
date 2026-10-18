package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.update.Update;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class PermissionStructureTest {
    @Test
    void sharedEnvelopeUsesPublishedFieldNamesForPermissionsAndInvitations() throws Exception {
        var schema = new PermissionSchema();
        String requestId = java.util.UUID.randomUUID().toString();
        var payload = schema.object();
        var value = schema.tree(new com.ruoyi.medical.contracts.InterfaceProtocol.Envelope("1.0.0", requestId, null, payload));
        com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath().validate("interfaces", value, "Envelope");
        assertEquals(requestId, value.required("request_id").textValue());
        assertEquals("1.0.0", value.required("schema_version").textValue());
        assertTrue(value.required("resource_revision").isNull());
    }
    @Test
    void schemaRejectsMissingBodyAndUnsignedOverflow() throws Exception {
        var schema = new PermissionSchema();
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class, () -> schema.request(new byte[0])).code());
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class, () -> schema.request("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8))).code());
        assertThrows(SchemaRejection.class, () -> schema.headers("18446744073709551616", null));
        assertThrows(SchemaRejection.class, () -> PermissionSchema.next("18446744073709551615"));
        assertThrows(SchemaRejection.class, () -> schema.condition("{}"));
    }

    @Test
    void resourceQueriesAndEpochUpdateUsePublishedDatabaseColumns() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        Map<String, String> queries = new HashMap<>();
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var statement = configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceOwner");
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        for (String kind : List.of("ENTERPRISE", "PROJECT", "MEMBER", "SOURCE", "TASK", "DOCUMENT", "ARTIFACT", "CONFIGURATION", "TEMPLATE", "RULE", "BUDGET", "EVALUATION", "AUDIT", "REPORT")) {
            Map<String, Object> parameters = new HashMap<>(); parameters.put("kind", kind); parameters.put("resourceId", null);
            var parsed = CCJSqlParserUtil.parse(statement.getBoundSql(parameters).getSql());
            if ("CONFIGURATION".equals(kind)) {
                var union=assertInstanceOf(net.sf.jsqlparser.statement.select.SetOperationList.class,parsed);
                assertEquals(2,union.getSelects().size());
                var tables=new java.util.HashSet<String>();
                for(var branch:union.getSelects()) {
                    var select=assertInstanceOf(PlainSelect.class,branch);
                    tables.add(assertInstanceOf(net.sf.jsqlparser.schema.Table.class,select.getFromItem()).getName());
                    assertNotNull(select.getWhere());
                }
                assertEquals(java.util.Set.of("configuration_head","agent_execution_policy_version"),tables);
            } else assertNotNull(assertInstanceOf(PlainSelect.class,parsed).getFromItem());
            queries.put(kind, statement.getBoundSql(parameters).getSql());
        }
        var epoch = configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.PermissionMapper.advanceAuthEpoch");
        var update = assertInstanceOf(Update.class, CCJSqlParserUtil.parse(epoch.getBoundSql(Map.of("epoch", "0")).getSql()));
        assertNotNull(update.getWhere()); queries.put("AUTH_EPOCH", epoch.getBoundSql(Map.of("epoch", "0")).getSql());
        Path output = root.resolve(".implementation-work/permission-query-columns.json");
        Files.write(output, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(queries));
        var process = new ProcessBuilder(System.getProperty("medical.test.python", root.resolve(".lld-data-work/venv/bin/python").toString()),
                root.resolve("tests/check_permission_sql_columns.py").toString(), output.toString()).directory(root.toFile()).inheritIO().start();
        assertEquals(0, process.waitFor());
    }
}
