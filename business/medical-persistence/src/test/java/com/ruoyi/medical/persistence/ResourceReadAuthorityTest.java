package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class ResourceReadAuthorityTest {
    private static Connection database;
    private static String userId;
    private static final List<String> KINDS = List.of("ENTERPRISE", "PROJECT", "MEMBER", "SOURCE", "TASK", "DOCUMENT",
        "ARTIFACT", "CONFIGURATION", "TEMPLATE", "RULE", "BUDGET", "EVALUATION", "AUDIT", "REPORT");

    @BeforeAll static void currentDatabaseAndActualOwner() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var json = new ObjectMapper();
        var secrets = json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        database.setReadOnly(true); database.setAutoCommit(false);
        try (var statement = database.createStatement(); var result = statement.executeQuery("SELECT DATABASE(),@@server_uuid,VERSION()")) {
            assertTrue(result.next()); assertEquals("medical_local", result.getString(1));
            assertEquals("5ecfb746-be18-11f1-95e4-d822203bc633", result.getString(2)); assertEquals("8.4.3", result.getString(3));
            assertFalse(result.next());
        }
        try (var statement = database.createStatement(); var result = statement.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN' ORDER BY user_id")) {
            assertTrue(result.next()); userId = result.getString(1); assertFalse(result.next());
        }
        try (var statement = database.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM permission_grant")) {
            assertTrue(result.next()); assertEquals(BigInteger.ZERO, result.getBigDecimal(1).toBigIntegerExact());
        }
    }

    @Test void actualOwnerWithoutEnterpriseGrantsCannotReadAnyResourceKind() throws Exception {
        var authority = new PermissionAuthority(new PermissionSchema());
        var now = LocalDateTime.now(ZoneOffset.UTC);
        // 实际平台身份用于越权请求；没有创建对应企业或业务资源。
        for (String kind : KINDS) {
            String project = kind.equals("PROJECT") ? userId : null;
            var scope = new PermissionProtocol.Scope(userId, project, kind, userId);
            var failure = assertThrows(TenantAccessException.class,
                () -> authority.requireResource(scope, userId, "local", "artifact.read", "ARTIFACT_READING", List.of(), now));
            assertEquals(ErrorCode.ACCESS_DENIED, failure.code());
        }
    }

    @Test void resourceGrantsAreQueriedFreshByActualSubjectActionAndDatabaseTime() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        String name = "com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceGrants";
        assertTrue(configuration.hasStatement(name), "具体资源读取必须查询当前有效授权");
        var statement = configuration.getMappedStatement(name);
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        var parameters = new HashMap<String,Object>(); parameters.put("userId", userId); parameters.put("action", "artifact.read");
        var sql = statement.getBoundSql(parameters); assertEquals(2, sql.getParameterMappings().size());
        try (var query = database.prepareStatement(sql.getSql())) {
            query.setString(1, userId); query.setString(2, "artifact.read");
            try (var rows = query.executeQuery()) { assertFalse(rows.next()); }
        }
        String text = sql.getSql();
        assertTrue(text.contains("valid_from <= UTC_TIMESTAMP(6)"));
        assertTrue(text.contains("valid_to > UTC_TIMESTAMP(6)"));
        assertTrue(text.contains("ORDER BY grant_id"));
    }

    @Test void allResourceOwnersUseActualCurrentDatabaseWithoutCustomerRecords() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var statement = configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceOwner");
        for (String kind : KINDS) {
            var parameters = new HashMap<String,Object>(); parameters.put("kind", kind); parameters.put("resourceId", userId);
            var sql = statement.getBoundSql(parameters);
            try (var query = database.prepareStatement(sql.getSql())) {
                query.setString(1, userId);
                try (var rows = query.executeQuery()) { assertFalse(rows.next()); }
            }
        }
    }

    @Test void resourceGrantSubjectQueryRequiresVerifiedEnterpriseTransaction() {
        String name = "com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceGrants";
        var failure = assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(name, java.util.Map.of("userId", userId)));
        assertEquals(ErrorCode.ACCESS_DENIED, failure.code());
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(name)).code());
    }

    @AfterAll static void closeActualReadOnlyDatabase() throws Exception { if (database != null) database.close(); }
}
