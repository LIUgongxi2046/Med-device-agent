package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperationSchemaTest {
    @Test
    void closedLookupRejectsAbsentDuplicateTrailingAndInvalidKeys() throws Exception {
        var schema = new OperationSchema(new PermissionSchema());
        for (String value : List.of("{}", "null", "[]", "", "{\"operation_id\":\"PJ-02\",\"idempotency_key\":\"\"}"))
            assertThrows(SchemaRejection.class, () -> schema.lookup(value.getBytes(StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> schema.lookup("{\"operation_id\":\"PJ-02\",\"operation_id\":\"PJ-02\"}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.lookup("{} {}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void missingAndExpiredResultsRetainNullOperationAndNoResourceRevision() throws Exception {
        var schema = new OperationSchema(new PermissionSchema());
        for (var status : List.of(InterfaceProtocol.LookupStatus.NOT_FOUND, InterfaceProtocol.LookupStatus.EXPIRED)) {
            var result = schema.result(status, null, UUID.randomUUID().toString());
            assertTrue(result.required("resource_revision").isNull());
            assertTrue(result.required("data").required("operation").isNull());
            assertEquals(status.name(), result.required("data").required("lookup_status").textValue());
        }
        assertThrows(SchemaRejection.class, () -> schema.result(InterfaceProtocol.LookupStatus.FOUND, null, UUID.randomUUID().toString()));
        assertThrows(SchemaRejection.class, () -> schema.operation(null, UUID.randomUUID().toString()));
    }

    @Test
    void operationMapperCannotReadWithoutCurrentSubjectScope() {
        for (String name : List.of("lookup", "operation", "readers"))
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(
                    "com.ruoyi.medical.persistence.mapper.OperationMapper." + name, Map.of()));
    }

    @Test
    void operationQueriesUseNativeParsersPublishedColumnsAndSafeMetadata() throws Exception {
        var root = java.nio.file.Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new org.springframework.jdbc.datasource.DriverManagerDataSource()).getConfiguration();
        var mapping = configuration.getResultMap("com.ruoyi.medical.persistence.mapper.OperationMapper.operation");
        Class<?>[] parameters = mapping.getConstructorResultMappings().stream().map(org.apache.ibatis.mapping.ResultMapping::getJavaType).toArray(Class<?>[]::new);
        assertNotNull(OperationRow.class.getDeclaredConstructor(parameters));
        var statements = new java.util.TreeMap<String,String>();
        for (String name : List.of("lookup", "operation", "readers")) {
            String sql = configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.OperationMapper." + name).getBoundSql(Map.of()).getSql();
            assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
            assertFalse(sql.contains("response_ref")); assertFalse(sql.contains("request_sha256")); statements.put(name, sql);
        }
        var output = root.resolve(".implementation-work/operation-query-columns.json");
        java.nio.file.Files.write(output, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(statements));
        var process = new ProcessBuilder(root.resolve(".lld-data-work/venv/bin/python").toString(), root.resolve("tests/check_operation_sql_columns.py").toString(), output.toString())
                .directory(root.toFile()).inheritIO().start();
        assertEquals(0, process.waitFor());
    }
}
