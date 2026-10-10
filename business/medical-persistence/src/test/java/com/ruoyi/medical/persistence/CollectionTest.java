package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
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

class CollectionTest {
    private static Connection database;
    private static String owner;
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.CollectionMapper.";
    private static final List<String> READS = List.of("head", "version", "versions", "parse", "members", "operation", "audit", "event");

    @BeforeAll static void actualDatabase() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        database.setAutoCommit(false);
        try (var query = database.createStatement(); var rows = query.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
            assertTrue(rows.next()); owner = rows.getString(1); assertFalse(rows.next());
        }
    }

    @Test void collectionDefinitionsArePublishedAndRejectIncompleteContent() throws Exception {
        var catalogue = SchemaCatalogue.fromClasspath(); var empty = JsonMapper.builder().build().createObjectNode();
        for (String definition : List.of("Create", "VersionInput", "Manifest", "View", "Created", "Page", "Audit", "Event")) {
            assertEquals("urn:medical:collections:1.0.0#/$defs/" + definition,
                    assertDoesNotThrow(() -> catalogue.schemaRef("collections", definition), "资料集完整定义需要固定发布"));
            assertThrows(SchemaRejection.class, () -> catalogue.validate("collections", empty, definition));
        }
    }

    @Test void fixedCollectionQueriesReadActualDatabaseAndCompleteMappings() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        var values = Map.<String,Object>of("userId", owner, "collectionId", owner, "versionId", owner, "sourceVersion", owner,
                "parseVersion", owner, "operationName", "KN-11", "key", owner, "auditId", owner, "eventId", owner, "limit", 2);
        for (String name : READS) {
            assertTrue(factory.getConfiguration().hasStatement(PREFIX + name), "资料集版本需要完整实际查询");
            var mapped = factory.getConfiguration().getMappedStatement(PREFIX + name);
            assertFalse(mapped.isUseCache()); assertTrue(mapped.isFlushCacheRequired());
            var bound = mapped.getBoundSql(values);
            try (var query = database.prepareStatement(bound.getSql())) {
                for (int index = 0; index < bound.getParameterMappings().size(); index++)
                    query.setObject(index + 1, values.get(bound.getParameterMappings().get(index).getProperty()));
                try (var rows = query.executeQuery()) {
                    assertFalse(rows.next());
                    var result = mapped.getResultMaps().get(0);
                    if (result.getType().isRecord()) {
                        assertEquals(result.getType().getRecordComponents().length, rows.getMetaData().getColumnCount());
                        var constructor = result.getConstructorResultMappings();
                        assertEquals(constructor.size(), rows.getMetaData().getColumnCount());
                        for (int index = 0; index < constructor.size(); index++)
                            assertEquals(constructor.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                    }
                }
            }
        }
    }

    @Test void collectionQueriesRequireOriginalEnterpriseAndSubjectProtection() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        try (var session = factory.openSession()) {
            for (String name : READS) {
                assertTrue(factory.getConfiguration().hasStatement(PREFIX + name));
                var failure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                        () -> session.selectList(PREFIX + name, Map.of("userId", owner)));
                assertInstanceOf(TenantAccessException.class, failure.getCause());
            }
        }
    }

    @Test void collectionPaginationUsesFrameworkPageHelper() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        assertTrue(configuration.getInterceptors().stream().anyMatch(com.github.pagehelper.PageInterceptor.class::isInstance),
                "资料集分页需要使用原PageHelper组件");
    }

    @Test void frameworkPaginationDeniesUnscopedCountAndClearsItsThreadState() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        try (var session = factory.openSession()) {
            var failure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, () ->
                    com.github.pagehelper.PageHelper.startPage(1, 20).doSelectPageInfo(() ->
                            session.selectList(PREFIX + "versions", Map.of("userId", owner, "collectionId", owner))));
            assertInstanceOf(TenantAccessException.class, failure.getCause());
            assertNull(com.github.pagehelper.PageHelper.getLocalPage());
        }
    }

    @Test void frameworkCountAndPageQueriesExecuteOnActualDatabase() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        var bound = factory.getConfiguration().getMappedStatement(PREFIX + "versions").getBoundSql(Map.of("collectionId", owner));
        var countSql = new com.github.pagehelper.parser.defaults.DefaultCountSqlParser().getSmartCountSql(bound.getSql(), "0");
        try (var query = database.prepareStatement(countSql)) {
            query.setString(1, owner);
            try (var rows = query.executeQuery()) { assertTrue(rows.next()); assertEquals(0, rows.getLong(1)); assertFalse(rows.next()); }
        }
        var pageSql = new com.github.pagehelper.dialect.helper.MySqlDialect().getPageSql(bound.getSql(),
                new com.github.pagehelper.Page<CollectionProtocol.Version>(2, 20), new org.apache.ibatis.cache.CacheKey());
        try (var query = database.prepareStatement(pageSql)) {
            query.setString(1, owner); query.setInt(2, 20); query.setInt(3, 20);
            try (var rows = query.executeQuery()) { assertFalse(rows.next()); assertEquals(13, rows.getMetaData().getColumnCount()); }
        }
    }

    @Test void completeWireRecordsMatchPublishedFields() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot"));
        var document = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve("business/medical-persistence/src/main/resources/collections.schema.json")));
        for (var type : List.of(CollectionProtocol.MemberSelection.class, CollectionProtocol.Content.class, CollectionProtocol.Create.class,
                CollectionProtocol.VersionInput.class, CollectionProtocol.Member.class, CollectionProtocol.Manifest.class,
                CollectionProtocol.View.class, CollectionProtocol.Created.class)) {
            var expected = new java.util.TreeSet<String>(); document.required("$defs").required(type.getSimpleName()).required("properties").fieldNames().forEachRemaining(expected::add);
            var actual = new java.util.TreeSet<String>();
            for (var field : type.getRecordComponents()) actual.add(field.getName());
            assertEquals(expected, actual);
        }
    }

    @Test void malformedBodiesAndPaginationAreRejectedAtTheBoundary() throws Exception {
        var schema = new CollectionSchema(new PermissionSchema());
        var root = Path.of(System.getProperty("medical.projectRoot")); byte[] realDocument = Files.readAllBytes(root.resolve("data-design/sample_dataset.json"));
        assertThrows(SchemaRejection.class, () -> schema.create(realDocument));
        assertThrows(SchemaRejection.class, () -> schema.version(realDocument));
        assertThrows(SchemaRejection.class, () -> schema.manifest("{}"));
        for (var invalid : List.of(Map.of("page", new String[]{"0"}), Map.of("page", new String[]{"2"}),
                Map.of("page_size", new String[]{"101"}), Map.of("page", new String[]{"1", "2"}),
                Map.of("owner_id", new String[]{owner}), Map.of("dataset_watermark", new String[]{"18446744073709551616"})))
            assertThrows(SchemaRejection.class, () -> schema.query(invalid));
        assertEquals(new CollectionProtocol.Query(1, 20, null), schema.query(Map.of()));
        assertEquals(new CollectionProtocol.Query(2, 100, "1"), schema.query(Map.of("page", new String[]{"2"},
                "page_size", new String[]{"100"}, "dataset_watermark", new String[]{"1"})));
    }

    @AfterAll static void closeDatabase() throws Exception { if (database != null && !database.isClosed()) database.close(); }
}
