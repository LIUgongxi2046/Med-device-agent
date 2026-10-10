package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
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

class TaskReadTest {
    private static Connection database;
    private static DriverManagerDataSource dataSource;
    private static String owner;
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.TaskMapper.";
    private static final List<String> READS = List.of("head", "input", "snapshot", "members", "plan", "documents", "runs", "waiting");

    @BeforeAll static void actualDatabase() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        dataSource = new DriverManagerDataSource(database.getMetaData().getURL(), credentials.getProperty("user"), credentials.getProperty("password"));
        database.setAutoCommit(false);
        try (var statement = database.createStatement(); var rows = statement.executeQuery(
                "SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
            assertTrue(rows.next()); owner = rows.getString(1); assertFalse(rows.next());
        }
    }

    @Test void fixedTaskQueriesExecuteAgainstActualDatabaseWithCompleteMappings() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        for (String name : READS) {
            assertTrue(configuration.hasStatement(PREFIX + name), "任务读取需要真实固定版本与子资源查询");
            var statement = configuration.getMappedStatement(PREFIX + name);
            assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
            var values = Map.of("userId", owner, "taskId", owner, "projectId", owner, "taskRevision", owner,
                    "snapshotId", owner, "planRevision", owner);
            var bound = statement.getBoundSql(values);
            try (var actual = database.prepareStatement(bound.getSql())) {
                for (int index = 1; index <= bound.getParameterMappings().size(); index++) actual.setString(index, owner);
                try (var rows = actual.executeQuery()) {
                    assertFalse(rows.next());
                    var mapping = statement.getResultMaps().get(0);
                    if (mapping.getType().isRecord()) {
                        var fields = mapping.getType().getRecordComponents();
                        var constructor = mapping.getConstructorResultMappings();
                        assertEquals(fields.length, rows.getMetaData().getColumnCount());
                        assertEquals(fields.length, constructor.size());
                        for (int index = 0; index < fields.length; index++) {
                            assertEquals(fields[index].getType(), constructor.get(index).getJavaType());
                            assertEquals(constructor.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                        }
                    }
                }
            }
        }
    }

    @Test void everyTaskQueryRequiresVerifiedEnterpriseTransaction() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        try (var session = factory.openSession()) {
            for (String name : READS) {
                assertTrue(factory.getConfiguration().hasStatement(PREFIX + name));
                var failure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, () -> session.selectList(PREFIX + name,
                        Map.of("userId", owner, "taskId", owner, "projectId", owner, "taskRevision", owner,
                                "snapshotId", owner, "planRevision", owner)));
                assertInstanceOf(TenantAccessException.class, failure.getCause());
            }
        }
    }

    @Test void missingRequirementsHaveActualImmutableStorageFields() throws Exception {
        try (var statement = database.prepareStatement("""
                SELECT COLUMN_NAME,DATA_TYPE,IS_NULLABLE FROM information_schema.columns
                 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='task_input_revision'
                   AND COLUMN_NAME IN ('missing_requirements','requirements_sha256') ORDER BY COLUMN_NAME
                """ ); var rows = statement.executeQuery()) {
            assertTrue(rows.next(), "缺项不能使用默认空集合代替真实保存内容");
            assertEquals("missing_requirements", rows.getString(1)); assertEquals("json", rows.getString(2)); assertEquals("NO", rows.getString(3));
            assertTrue(rows.next()); assertEquals("requirements_sha256", rows.getString(1));
            assertEquals("char", rows.getString(2)); assertEquals("NO", rows.getString(3)); assertFalse(rows.next());
        }
    }

    @Test void taskListFiltersAndDatasetRevisionExecuteAgainstActualEmptyProject() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(dataSource).getConfiguration();
        java.time.LocalDateTime now;
        try (var statement = database.createStatement(); var rows = statement.executeQuery("SELECT UTC_TIMESTAMP(6)")) {
            assertTrue(rows.next()); now = rows.getObject(1, java.time.LocalDateTime.class);
        }
        for (boolean all : List.of(false, true)) for (var status : com.ruoyi.medical.contracts.ResourceProtocol.TaskStatus.values()) {
            var values = new java.util.HashMap<String,Object>();
            values.put("userId", owner); values.put("projectId", owner); values.put("allResources", all);
            values.put("resourceIds", List.of(owner)); values.put("status", status.name()); values.put("ownerId", owner);
            values.put("updatedFrom", now); values.put("updatedTo", now);
            for (String name : List.of("listed", "datasetRevision", "visibleCount")) {
                assertTrue(configuration.hasStatement(PREFIX + name), "项目展示计数必须使用原任务授权范围");
                var statement = configuration.getMappedStatement(PREFIX + name);
                assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
                var bound = statement.getBoundSql(values);
                try (var actual = database.prepareStatement(bound.getSql())) {
                    new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, values, bound).setParameters(actual);
                    try (var rows = actual.executeQuery()) {
                        assertEquals(1, rows.getMetaData().getColumnCount());
                        if (!name.equals("listed")) { assertTrue(rows.next()); assertEquals("0", rows.getString(1)); }
                        assertFalse(rows.next());
                    }
                }
            }
        }
    }

    @Test void taskListCountingRequiresEnterpriseAdmissionAndClearsOriginalPagination() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(dataSource);
        var values = new java.util.HashMap<String,Object>();
        values.put("userId", owner); values.put("projectId", owner); values.put("allResources", false); values.put("resourceIds", List.of());
        try (var session = factory.openSession()) {
            var failure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, () ->
                com.github.pagehelper.PageHelper.startPage(1, 20).doSelectPageInfo(() -> session.selectList(PREFIX + "listed", values)));
            assertInstanceOf(TenantAccessException.class, failure.getCause());
            assertNull(com.github.pagehelper.PageHelper.getLocalPage());
            var revision = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, () -> session.selectOne(PREFIX + "datasetRevision", values));
            assertInstanceOf(TenantAccessException.class, revision.getCause());
            var visible = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, () -> session.selectOne(PREFIX + "visibleCount", values));
            assertInstanceOf(TenantAccessException.class, visible.getCause());
        }
    }

    @Test void snapshotManifestHasPublishedClosedSchemaAndFixedSourceVersions() throws Exception {
        var catalogue = com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath();
        assertEquals("urn:medical:tasks:1.0.0#/$defs/ScopeSnapshotManifest",
                assertDoesNotThrow(() -> catalogue.schemaRef("tasks", "ScopeSnapshotManifest"),
                    "来源快照需要固定发布定义"));
        var failure = assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> catalogue.validate("tasks", JsonMapper.builder().build().createObjectNode(), "ScopeSnapshotManifest"));
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.SCHEMA_INVALID, failure.code());
        assertEquals("urn:medical:tasks:1.0.0#/$defs/ScopeSnapshotMember",
                catalogue.schemaRef("tasks", "ScopeSnapshotMember"));
    }

    @Test void originalSpringMapperScanRegistersTaskQueriesWithActualDataSource() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(javax.sql.DataSource.class, () -> dataSource);
            context.register(MedicalPersistenceConfiguration.class);
            context.refresh();
            var mapper = assertDoesNotThrow(() -> context.getBean(com.ruoyi.medical.persistence.mapper.TaskMapper.class),
                    "任务查询必须通过原Spring注册进入真实应用");
            var failure = assertThrows(org.mybatis.spring.MyBatisSystemException.class, () -> mapper.head(owner, owner));
            assertInstanceOf(org.apache.ibatis.exceptions.PersistenceException.class, failure.getCause());
            assertInstanceOf(TenantAccessException.class, failure.getCause().getCause());
        }
    }

    @Test void businessConsumerRejectsIncompleteManifestBeforeUsingMembers() throws Exception {
        var snapshots = new ScopeSnapshotSchema(new TaskInputSchema(new PermissionSchema()));
        var failure = assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> snapshots.require("{}", null, null, null, null, null, null, null, null, List.of()));
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.SCHEMA_INVALID, failure.code());
    }

    @AfterAll static void closeDatabase() throws Exception { if (database != null && !database.isClosed()) database.close(); }
}
