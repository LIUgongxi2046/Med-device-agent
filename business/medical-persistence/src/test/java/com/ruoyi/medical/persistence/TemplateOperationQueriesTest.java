package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class TemplateOperationQueriesTest {
    @Test void actualOperationAuditAndEventQueriesUseExistingDatabaseColumns() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        try (var database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials)) {
            database.setReadOnly(true); database.setAutoCommit(false);
            String owner, revision;
            try (var query = database.createStatement(); var rows = query.executeQuery("SELECT user_id,credential_revision FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
                assertTrue(rows.next()); owner = rows.getString(1); revision = rows.getString(2); assertFalse(rows.next());
            }
            var parameters = new HashMap<String,Object>();
            for (String name : List.of("userId", "key", "auditId", "eventId", "requestId", "templateId", "decisionId", "operationId")) parameters.put(name, owner);
            parameters.put("resulting", revision);
            parameters.put("classification", "LOCAL_AUTH_METADATA");
            parameters.put("operationManifest", new TemplateOperationSchema(new PermissionSchema(),
                    new TemplateDefinitionSchema(new PermissionSchema(), RegistrationDirectory.fromClasspath())).manifestSha256());
            for (String name : List.of("currentOperation", "audit", "event", "approvalOperation", "approval", "approvalAudit", "approvalEvent",
                    "approvalAuditForOperation", "approvalEventForOperation")) {
                String id = "com.ruoyi.medical.persistence.mapper.TemplateMapper." + name;
                var statement = configuration.getMappedStatement(id); assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
                var bound = statement.getBoundSql(parameters);
                try (var query = database.prepareStatement(bound.getSql())) {
                    for (int index = 0; index < bound.getParameterMappings().size(); index++)
                        query.setObject(index + 1, parameters.get(bound.getParameterMappings().get(index).getProperty()));
                    try (var rows = query.executeQuery()) { assertFalse(rows.next()); }
                }
                var denied = assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(id));
                assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED, denied.code());
            }
            database.rollback();
        }
    }
}
