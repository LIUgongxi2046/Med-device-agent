package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnterpriseRegistrationReviewSchemaTest {
    private EnterpriseRegistrationReviewSchema schema() throws Exception {
        return new EnterpriseRegistrationReviewSchema(new EnterpriseRegistrationSchema(), new PermissionSchema());
    }
    @Test void actualEmptyReviewQueueUsesPublishedEnvelope() throws Exception {
        var schema = schema(); var page = schema.object();
        page.put("schema_version", "1.0.0"); page.putArray("items"); page.putNull("next_cursor");
        var response = schema.envelope("Page", page, UUID.randomUUID().toString(), null);
        assertEquals(page, response.required("data")); assertTrue(response.required("resource_revision").isNull());
    }
    @Test void missingApprovalInputIsRejected() throws Exception {
        var schema = schema();
        assertThrows(SchemaRejection.class, () -> schema.request("{}".getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString()));
        assertThrows(SchemaRejection.class, () -> schema.request("null".getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString()));
    }
    @Test void keyAndSizeAreRequired() throws Exception {
        var schema = schema();
        assertThrows(SchemaRejection.class, () -> schema.request("{}".getBytes(StandardCharsets.UTF_8), null));
        assertThrows(SchemaRejection.class, () -> schema.request(new byte[65537], UUID.randomUUID().toString()));
    }
    @Test void nativeParserRejectsDuplicatesAndTrailingContent() throws Exception {
        var schema = schema();
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.request(
                "{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString()));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.request("{} {}".getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString()));
    }
    @Test void policyManifestIncludesCurrentDependentSchemas() throws Exception {
        var schema = schema(); assertTrue(schema.manifest().matches("[0-9a-f]{64}"));
        assertNotEquals(new EnterpriseRegistrationSchema().manifest(), schema.manifest());
    }
    @Test void missingPersistedApplicationCannotValidateReviewDetail() throws Exception {
        var schema = schema(); var detail = schema.object(); detail.put("schema_version", "1.0.0");
        detail.set("application", schema.object()); detail.putNull("review"); detail.put("status", "PENDING_REVIEW"); detail.put("revision", "1");
        assertThrows(SchemaRejection.class, () -> schema.verify("Detail", detail));
    }
    @Test void allProvisioningQueriesUsePublishedColumnsAndInjectedEnterprise() throws Exception {
        var root = java.nio.file.Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new org.springframework.jdbc.datasource.DriverManagerDataSource()).getConfiguration();
        var statements = new java.util.TreeMap<String,String>();
        for (var method : com.ruoyi.medical.persistence.mapper.EnterpriseProvisioningMapper.class.getDeclaredMethods()) {
            var statement = configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.EnterpriseProvisioningMapper." + method.getName());
            String sql = statement.getBoundSql(new java.util.HashMap<>()).getSql();
            assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql)); statements.put(method.getName(), sql);
        }
        assertEquals(9, statements.size());
        var output = root.resolve(".implementation-work/provisioning-query-columns.json");
        java.nio.file.Files.write(output, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(statements));
        var process = new ProcessBuilder(System.getProperty("medical.test.python", root.resolve(".lld-data-work/venv/bin/python").toString()), root.resolve("tests/check_provisioning_sql_columns.py").toString(), output.toString())
                .directory(root.toFile()).inheritIO().start();
        assertEquals(0, process.waitFor());
    }
}
