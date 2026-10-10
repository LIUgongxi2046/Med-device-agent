package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class ContentReadReceiptTest {
    private static Connection database;
    private static String owner;
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.ContentReadMapper.";

    @BeforeAll static void actualDatabase() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = new ObjectMapper().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        database.setReadOnly(true); database.setAutoCommit(false);
        try (var query = database.createStatement(); var rows = query.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
            assertTrue(rows.next()); owner = rows.getString(1); assertFalse(rows.next());
        }
    }

    @Test void actualReceiptHeaderRangeAndAuditCanBeReReadWithExactColumns() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        for (String name : List.of("header", "range", "audit")) {
            assertTrue(configuration.hasStatement(PREFIX + name), "实际读取必须保存并重读完整回执、范围和审计");
            var statement = configuration.getMappedStatement(PREFIX + name);
            assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
            var bound = statement.getBoundSql(Map.of("userId", owner, "receiptId", owner, "auditId", owner));
            try (var actual = database.prepareStatement(bound.getSql())) {
                for (int index = 0; index < bound.getParameterMappings().size(); index++) actual.setString(index + 1, owner);
                try (var rows = actual.executeQuery()) {
                    assertFalse(rows.next());
                    var constructor = statement.getResultMaps().get(0).getConstructorResultMappings();
                    var components = statement.getResultMaps().get(0).getType().getRecordComponents();
                    assertEquals(components.length, rows.getMetaData().getColumnCount(), name);
                    assertEquals(components.length, constructor.size(), name);
                    for (int index = 0; index < components.length; index++) {
                        assertEquals(components[index].getType(), constructor.get(index).getJavaType(), name);
                        assertEquals(constructor.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1), name);
                    }
                }
            }
        }
    }

    @Test void receiptReadsAndWritesRequireCurrentSubjectAndEnterpriseTransaction() {
        for (String name : List.of("header", "range", "audit", "databaseTime", "saveHeader", "saveRange", "saveAudit")) {
            var failure = assertThrows(TenantAccessException.class,
                () -> TenantScope.requireSubjectParameter(PREFIX + name, Map.of("userId", owner)));
            assertEquals(ErrorCode.ACCESS_DENIED, failure.code(), name);
            var transactionFailure = assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(PREFIX + name));
            assertEquals(ErrorCode.ACCESS_DENIED, transactionFailure.code(), name);
        }
    }

    @Test void receiptInsertsUseEveryActualColumnAndServerInjectedTenant() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        for (var mapping : Map.of("saveHeader", "content_read_receipt", "saveRange", "content_read_range", "saveAudit", "audit_event").entrySet()) {
            var statement = configuration.getMappedStatement(PREFIX + mapping.getKey());
            var bound = statement.getBoundSql(Map.of("userId", owner, "header", Map.of(), "range", Map.of(), "audit", Map.of()));
            var insert = (net.sf.jsqlparser.statement.insert.Insert) net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(bound.getSql());
            assertEquals(mapping.getValue(), insert.getTable().getName());
            var columns = insert.getColumns().stream().map(net.sf.jsqlparser.schema.Column::getColumnName).collect(java.util.stream.Collectors.toSet());
            assertFalse(columns.contains("tenant_id")); assertFalse(columns.contains("project_key"));
            var actualColumns = new java.util.HashSet<String>();
            try (var query = database.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name NOT IN ('tenant_id','project_key')")) {
                query.setString(1, mapping.getValue());
                try (var rows = query.executeQuery()) { while (rows.next()) actualColumns.add(rows.getString(1)); }
            }
            assertEquals(actualColumns, columns, mapping.getKey());
            assertTrue(bound.getParameterMappings().stream().filter(parameter -> parameter.getProperty().equals("userId")).count() >= 1);
        }
    }

    @Test void actualDatabaseTimeAndUnsignedMaximumKeepExactCanonicalValues() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var statement = configuration.getMappedStatement(PREFIX + "databaseTime");
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        java.time.LocalDateTime actualTime;
        java.math.BigInteger actualMaximum;
        try (var query = database.createStatement(); var rows = query.executeQuery("SELECT CAST(18446744073709551615 AS UNSIGNED),UTC_TIMESTAMP(6)")) {
            assertTrue(rows.next()); actualMaximum = rows.getBigDecimal(1).toBigIntegerExact();
            actualTime = rows.getObject(2, java.time.LocalDateTime.class); assertFalse(rows.next());
        }
        var bytes = ContentReadAuthorization.canonicalBytes(Map.of("epoch", actualMaximum, "time", actualTime, "subject_id", owner));
        var node = new ObjectMapper().readTree(bytes);
        assertTrue(node.required("epoch").isTextual()); assertEquals("18446744073709551615", node.required("epoch").textValue());
        assertEquals(actualTime.toInstant(java.time.ZoneOffset.UTC).toString(), node.required("time").textValue());
        assertEquals(owner, node.required("subject_id").textValue());
        assertArrayEquals(bytes, new org.erdtman.jcs.JsonCanonicalizer(bytes).getEncodedUTF8());
    }

    @Test void authorizationDigestCannotBeCreatedWithoutActualEnterpriseAdmission() {
        var failure = assertThrows(TenantAccessException.class, () -> ContentReadAuthorization.digest(null, null, null));
        assertEquals(ErrorCode.ACCESS_DENIED, failure.code());
    }

    @AfterAll static void closeDatabase() throws Exception { if (database != null) database.close(); }
}
