package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.github.pagehelper.dialect.helper.MySqlDialect;
import com.github.pagehelper.parser.defaults.DefaultCountSqlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class ProjectPaginationTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.ProjectMapper.";
    private static final List<String> READS = List.of("projects", "waiting", "tasks", "runs");
    private static Connection database;
    private static SqlSessionFactory factory;
    private static String owner;

    @BeforeAll static void actualDatabase() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        database.setAutoCommit(false);
        factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource(
                database.getMetaData().getURL(), credentials.getProperty("user"), credentials.getProperty("password")));
        try (var query = database.createStatement(); var rows = query.executeQuery(
                "SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
            assertTrue(rows.next()); owner = rows.getString(1); assertFalse(rows.next());
        }
    }

    @Test void projectActionsReadPublishedTaskListPermissionOnActualDatabase() throws Exception {
        var values = Map.<String,Object>of("userId", owner);
        var statement = factory.getConfiguration().getMappedStatement(PREFIX + "grants");
        var bound = statement.getBoundSql(values);
        assertTrue(bound.getSql().contains("'project.task.read'"), "项目动作必须读取任务列表规定的原许可");
        try (var query = database.prepareStatement(bound.getSql())) {
            new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, values, bound).setParameters(query);
            try (var rows = query.executeQuery()) { assertFalse(rows.next()); }
        }
    }

    @Test void frameworkPagesEveryProjectQueryOnActualDatabase() throws Exception {
        assertTrue(factory.getConfiguration().getInterceptors().stream().anyMatch(PageInterceptor.class::isInstance));
        for (String name : READS) for (boolean filtered : List.of(false, true)) for (int page : List.of(1, 2)) {
            var values = parameters(filtered); var statement = factory.getConfiguration().getMappedStatement(PREFIX + name);
            var bound = statement.getBoundSql(values);
            assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
            var sql = new MySqlDialect().getPageSql(bound.getSql(), new Page<Object>(page, 2), new CacheKey());
            assertDoesNotThrow(() -> {
                try (var query = database.prepareStatement(sql)) {
                    int index = bind(query, bound, values);
                    if (page != 1) query.setInt(index++, (page - 1) * 2);
                    query.setInt(index, 2);
                    try (var rows = query.executeQuery()) {
                        assertFalse(rows.next());
                        var mapping = statement.getResultMaps().get(0);
                        if (mapping.getType().isRecord()) {
                            assertEquals(mapping.getType().getRecordComponents().length, rows.getMetaData().getColumnCount());
                            var constructor = mapping.getConstructorResultMappings();
                            for (int ordinal = 0; ordinal < constructor.size(); ordinal++)
                                assertEquals(constructor.get(ordinal).getColumn(), rows.getMetaData().getColumnLabel(ordinal + 1));
                        } else assertEquals(1, rows.getMetaData().getColumnCount());
                    }
                }
            }, "原框架必须能够执行项目分页查询：" + name);
        }
    }

    @Test void frameworkCountsKeepOriginalFiltersOnActualDatabase() throws Exception {
        var counts = Map.of("projects", "projectCount", "waiting", "waitingCount", "tasks", "taskCount", "runs", "runCount");
        for (String name : READS) for (boolean filtered : List.of(false, true)) {
            var values = parameters(filtered); var query = factory.getConfiguration().getMappedStatement(PREFIX + name).getBoundSql(values);
            var original = factory.getConfiguration().getMappedStatement(PREFIX + counts.get(name)).getBoundSql(values);
            long expected = count(original.getSql(), original, values);
            long actual = assertDoesNotThrow(() -> count(new DefaultCountSqlParser().getSmartCountSql(query.getSql(), "0"), query, values));
            assertEquals(expected, actual); assertEquals(0, actual);
        }
    }

    @Test void frameworkPaginationPreservesEnterpriseProtectionAndClearsThreadState() {
        for (String name : READS) for (boolean count : List.of(false, true)) {
            try (var session = factory.openSession()) {
                var failure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                        () -> PageHelper.startPage(1, 2, count).doSelectPageInfo(() -> session.selectList(PREFIX + name, parameters(true))));
                assertInstanceOf(TenantAccessException.class, failure.getCause());
                assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED, ((TenantAccessException) failure.getCause()).code());
                assertNull(PageHelper.getLocalPage());
            }
        }
    }

    private static Map<String,Object> parameters(boolean filtered) {
        var values = new HashMap<String,Object>(); values.put("userId", owner); values.put("projectId", owner);
        values.put("enterprise", false); values.put("allowedProjects", List.of(owner)); values.put("limit", 3);
        if (filtered) {
            values.put("after", owner); values.put("name", owner); values.put("status", "ACTIVE");
            values.put("responsible", owner); values.put("oldProfile", owner);
        }
        return values;
    }

    private static int bind(java.sql.PreparedStatement query, org.apache.ibatis.mapping.BoundSql bound,
            Map<String,Object> values) throws Exception {
        int index = 1;
        for (var mapping : bound.getParameterMappings()) {
            String key = mapping.getProperty();
            query.setObject(index++, bound.hasAdditionalParameter(key) ? bound.getAdditionalParameter(key) : values.get(key));
        }
        return index;
    }

    private static long count(String sql, org.apache.ibatis.mapping.BoundSql bound, Map<String,Object> values) throws Exception {
        try (var query = database.prepareStatement(sql)) {
            bind(query, bound, values);
            try (var rows = query.executeQuery()) { assertTrue(rows.next()); long result = rows.getLong(1); assertFalse(rows.next()); return result; }
        }
    }

    @AfterAll static void closeDatabase() throws Exception { if (database != null && !database.isClosed()) database.close(); }
}
