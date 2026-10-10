package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class AgentReleaseStoreTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.AgentReleaseMapper.";

    @Test void currentReleaseScopeRequiresActualAudienceAndScopeQueries() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        for (String name : List.of("scopes", "roleOrigins"))
            assertTrue(factory.getConfiguration().hasStatement(PREFIX + name), "当前发布范围需要实际范围和岗位来源核查");
        assertNotNull(getClass().getClassLoader().getResource("com/ruoyi/medical/persistence/AgentReleaseScopeAuthority.class"));
    }

    @Test void actualReferenceDatasetCannotDeclareAReleaseAudience() throws Exception {
        byte[] definition;
        try (var input = getClass().getResourceAsStream("/agent-release.schema.json")) {
            assertNotNull(input); definition = input.readAllBytes();
        }
        var schema = new AgentReleaseManifestSchema(definition, com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath());
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var actual = Files.readAllBytes(root.resolve("data-design/sample_dataset.json"));
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () -> schema.bindScope(actual));
    }

    @Test void fixedReleaseReaderRequiresCompleteProtectedMappings() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        for (String name : List.of("release", "agent", "evaluation", "scopes", "roleOrigins"))
            assertTrue(factory.getConfiguration().hasStatement(PREFIX + name), "固定发布需要原发布、Agent和评测记录读取");
        assertNotNull(getClass().getClassLoader().getResource("com/ruoyi/medical/persistence/AgentReleaseStore.class"));
    }

    @Test void actualDatabaseQueriesPreserveEveryMappedColumnWithoutCreatingBusinessRecords() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        for (String name : List.of("release", "agent", "evaluation"))
            assertTrue(factory.getConfiguration().hasStatement(PREFIX + name), "发布固定引用需要实际读取语句");
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials)) {
            String actor;
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
                assertTrue(rows.next()); actor = rows.getString(1); assertFalse(rows.next());
            }
            for (String name : List.of("release", "agent", "evaluation", "scopes", "roleOrigins")) {
                var statement = factory.getConfiguration().getMappedStatement(PREFIX + name);
                assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
                var values = Map.of("userId", actor, "id", actor, "projectId", actor, "roleIds", List.of(actor), "action", "task.create", "limit", 2);
                var bound = statement.getBoundSql(values);
                try (var prepared = connection.prepareStatement(bound.getSql())) {
                    new DefaultParameterHandler(statement, values, bound).setParameters(prepared);
                    try (var rows = prepared.executeQuery()) {
                        var columns = statement.getResultMaps().get(0).getConstructorResultMappings();
                        assertEquals(columns.size(), rows.getMetaData().getColumnCount());
                        for (int index = 0; index < columns.size(); index++)
                            assertEquals(columns.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                        assertFalse(rows.next(), "平台账户身份不能代替业务发布或评测身份");
                    }
                }
            }
            try (var session = factory.openSession()) {
                for (String name : List.of("release", "agent", "evaluation", "scopes", "roleOrigins")) {
                    var error = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                        () -> session.selectList(PREFIX + name, Map.of("userId", actor, "id", actor, "projectId", actor,
                            "roleIds", List.of(actor), "action", "task.create", "limit", 2)));
                    assertEquals(ErrorCode.ACCESS_DENIED, assertInstanceOf(TenantAccessException.class, error.getCause()).code());
                }
            }
        }
    }
}
