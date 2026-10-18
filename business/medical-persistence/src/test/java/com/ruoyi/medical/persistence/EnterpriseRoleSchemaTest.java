package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.IdentityBindingSchema;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnterpriseRoleSchemaTest {
    @Test
    void closedRoleInputsRejectAbsentFieldsDuplicatesAndTrailingContent() throws Exception {
        var json = new PermissionSchema(); var invitations = new InvitationSchema(json, new IdentityBindingSchema());
        var schema = new EnterpriseRoleSchema(json, invitations);
        assertThrows(SchemaRejection.class, () -> schema.publish("{}".getBytes(StandardCharsets.UTF_8), UUID.randomUUID().toString()));
        assertThrows(SchemaRejection.class, () -> schema.revoke("{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> schema.revoke("{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> schema.revoke("{} {}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class, () -> schema.verify("Role", json.object()));
        assertThrows(SchemaRejection.class, () -> schema.verify("Receipt", json.object()));
    }

    @Test
    void roleManifestDoesNotChangeOriginalInvitationOrPermissionSchemas() throws Exception {
        var json = new PermissionSchema(); var invitations = new InvitationSchema(json, new IdentityBindingSchema());
        var before = invitations.manifestSha256(); var permission = json.manifestSha256();
        var roles = new EnterpriseRoleSchema(json, invitations);
        assertEquals(before, invitations.manifestSha256()); assertEquals(permission, json.manifestSha256());
        assertTrue(roles.manifestSha256().matches("[0-9a-f]{64}"));
        assertNotEquals(before, roles.manifestSha256()); assertNotEquals(permission, roles.manifestSha256());
    }

    @Test
    void everyRoleQueryUsesActualPublishedColumnsAndNativeSqlParser() throws Exception {
        var root = java.nio.file.Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new org.springframework.jdbc.datasource.DriverManagerDataSource()).getConfiguration();
        var parameters = new java.util.HashMap<String,Object>(); parameters.put("roles", java.util.List.of(UUID.randomUUID().toString()));
        parameters.put("after", UUID.randomUUID().toString()); parameters.put("limit", 1);
        java.util.Map<String,String> statements = new java.util.TreeMap<>();
        for (String name : java.util.List.of("managers", "head", "heads", "publication", "insertVersion", "insertRole", "insertPublication", "insertHead",
                "advanceHead", "revokeVersion", "pendingInvitations", "activeRoleGrants", "revokeInvitation", "advanceEpochs", "currentOperation", "saveOperation")) {
            String sql = configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.EnterpriseRoleMapper." + name).getBoundSql(parameters).getSql();
            assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql)); statements.put(name, sql);
        }
        var output = root.resolve(".implementation-work/role-query-columns.json");
        java.nio.file.Files.write(output, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(statements));
        var process = new ProcessBuilder(System.getProperty("medical.test.python", root.resolve(".lld-data-work/venv/bin/python").toString()), root.resolve("tests/check_role_sql_columns.py").toString(), output.toString())
                .directory(root.toFile()).inheritIO().start();
        assertEquals(0, process.waitFor());
    }
}
