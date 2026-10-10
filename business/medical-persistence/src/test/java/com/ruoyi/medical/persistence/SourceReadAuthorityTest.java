package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class SourceReadAuthorityTest {
    private static Connection database;
    private static String owner;
    private static Properties credentials;

    @BeforeAll static void actualDatabase() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = new ObjectMapper().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        database.setReadOnly(true); database.setAutoCommit(false);
        try (var query = database.createStatement(); var rows = query.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
            assertTrue(rows.next()); owner = rows.getString(1); assertFalse(rows.next());
        }
    }

    @Test void sourceVersionsRightsSelectionsAndConversationsReadActualDatabase() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        for (String query : List.of("source", "rights", "selection", "gates", "conversation", "parents")) {
            var name = "com.ruoyi.medical.persistence.mapper.SourceReadMapper." + query;
            assertTrue(configuration.hasStatement(name), "来源读取必须查询原版本、当前门槛及全部传递关系");
            var statement = configuration.getMappedStatement(name);
            assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
            var bound = statement.getBoundSql(Map.of("id", owner, "projectId", owner, "snapshotId", owner, "parseVersion", owner));
            try (var actual = database.prepareStatement(bound.getSql())) {
                for (int parameter = 0; parameter < bound.getParameterMappings().size(); parameter++) actual.setString(parameter + 1, owner);
                try (var rows = actual.executeQuery()) {
                    assertFalse(rows.next());
                    var constructor = statement.getResultMaps().get(0).getConstructorResultMappings();
                    var components = statement.getResultMaps().get(0).getType().getRecordComponents();
                    assertEquals(components.length, rows.getMetaData().getColumnCount()); assertEquals(components.length, constructor.size());
                    for (int index = 0; index < components.length; index++) {
                        assertEquals(components[index].getType(), constructor.get(index).getJavaType());
                        assertEquals(constructor.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                    }
                }
            }
        }
    }

    @Test void sourceRightsRequireClosedPublishedSchema() throws Exception {
        var resource = getClass().getClassLoader().getResource("source-rights.schema.json");
        assertNotNull(resource, "权利版本需要明确所有者、环境、分类及授权依据");
        var json = new ObjectMapper(); var document = json.readTree(resource);
        var schema = com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012)
            .getSchema(document, com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        assertFalse(document.required("additionalProperties").booleanValue());
        for (String invalid : List.of("{}", "[]", "null")) assertFalse(schema.validate(json.readTree(invalid)).isEmpty());
    }

    @Test void sourceQueriesRemainDeniedWithoutEnterpriseTransaction() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        for (String query : List.of("source", "rights", "selection", "gates", "conversation", "parents")) {
            var id = "com.ruoyi.medical.persistence.mapper.SourceReadMapper." + query;
            assertTrue(configuration.hasStatement(id), "受保护来源查询必须存在");
            var failure = assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(id));
            assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED, failure.code());
        }
    }

    @Test void snapshotCandidatesReadEveryFixedVersionFromActualDatabase() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        String name = "com.ruoyi.medical.persistence.mapper.SourceReadMapper.snapshotCandidate";
        assertTrue(configuration.hasStatement(name), "创建快照之前必须核查实际固定版本，不能借用已经保存的快照身份");
        var statement = configuration.getMappedStatement(name);
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        var parameters = Map.of("id", owner, "projectId", owner, "parseVersion", owner,
                "indexVersion", owner, "approvalRevision", owner, "bindingRevision", owner, "userId", owner);
        var bound = statement.getBoundSql(parameters);
        assertEquals(List.of("id", "projectId", "parseVersion", "indexVersion", "approvalRevision", "bindingRevision"),
                bound.getParameterMappings().stream().map(org.apache.ibatis.mapping.ParameterMapping::getProperty).toList());
        // 锁定读取使用独立事务；仅执行原查询，不创建客户记录。
        try (var candidateDatabase = DriverManager.getConnection(database.getMetaData().getURL(), credentials)) {
            candidateDatabase.setAutoCommit(false);
            try (var query = candidateDatabase.prepareStatement(bound.getSql())) {
                for (int index = 0; index < bound.getParameterMappings().size(); index++) query.setString(index + 1, owner);
                try (var rows = query.executeQuery()) {
                    assertFalse(rows.next(), "实际平台身份没有企业来源，不能被当作获准资料");
                    var constructor = statement.getResultMaps().get(0).getConstructorResultMappings();
                    assertEquals(SourceReadProtocol.SelectionRow.class, statement.getResultMaps().get(0).getType());
                    assertEquals(constructor.size(), rows.getMetaData().getColumnCount());
                    for (int index = 0; index < constructor.size(); index++)
                        assertEquals(constructor.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                }
            }
        }
    }

    @Test void snapshotCandidateInputsCannotOmitAnyFixedVersionOrUsageContext() {
        for (int missing = 0; missing < 10; missing++) {
            String[] values = {owner, owner, owner, owner, owner, owner, owner, "TASK_PLANNING", "LOCAL", "LOCAL"};
            values[missing] = null;
            assertThrows(RuntimeException.class, () -> new SourceReadProtocol.SnapshotRequirement(values[0], values[1], values[2],
                    values[3], values[4], values[5], values[6], values[7], values[8], values[9]));
        }
    }

    @Test void sourceScopeModesExecuteActualSelectionAndPreserveExplicitEmptySets() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        String name = "com.ruoyi.medical.persistence.mapper.SourceReadMapper.scopeCandidates";
        assertTrue(factory.getConfiguration().hasStatement(name), "四种资料模式需要实际版本选择查询");
        var mapped = factory.getConfiguration().getMappedStatement(name);
        for (var mode : com.ruoyi.medical.contracts.CommonProtocol.ScopeMode.values()) for (boolean explicit : List.of(false, true)) {
            var parameters = Map.<String,Object>of("userId", owner, "projectId", owner, "mode", mode.name(),
                    "sourceVersions", explicit && (mode.name().equals("SOURCE_VERSIONS") || mode.name().equals("COMBINED")) ? List.of(owner) : List.of(),
                    "collectionVersions", explicit && (mode.name().equals("COLLECTION_VERSIONS") || mode.name().equals("COMBINED")) ? List.of(owner) : List.of(), "limit", 2);
            var bound = mapped.getBoundSql(parameters);
            assertFalse(mapped.isUseCache()); assertTrue(mapped.isFlushCacheRequired());
            if (!explicit && mode != com.ruoyi.medical.contracts.CommonProtocol.ScopeMode.PROJECT_APPROVED)
                assertTrue(bound.getSql().contains("1=0"), "空显式范围必须保持空集合");
            try (var connection = DriverManager.getConnection(database.getMetaData().getURL(), credentials)) {
                connection.setAutoCommit(false);
                try (var query = connection.prepareStatement(bound.getSql())) {
                    for (int index = 0; index < bound.getParameterMappings().size(); index++) {
                        var property = bound.getParameterMappings().get(index).getProperty();
                        query.setObject(index + 1, bound.hasAdditionalParameter(property) ? bound.getAdditionalParameter(property) : parameters.get(property));
                    }
                    try (var rows = query.executeQuery()) {
                        assertFalse(rows.next());
                        var constructor = mapped.getResultMaps().get(0).getConstructorResultMappings();
                        assertEquals(SourceReadProtocol.SelectionRow.class, mapped.getResultMaps().get(0).getType());
                        assertEquals(constructor.size(), rows.getMetaData().getColumnCount());
                        for (int index = 0; index < constructor.size(); index++)
                            assertEquals(constructor.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                    }
                }
            }
        }
        try (var session = factory.openSession()) {
            var failure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                    () -> session.selectList(name, Map.of("userId", owner, "projectId", owner, "mode", "PROJECT_APPROVED",
                            "sourceVersions", List.of(), "collectionVersions", List.of(), "limit", 2)));
            assertInstanceOf(TenantAccessException.class, failure.getCause());
        }
    }

    @Test void collectionMembershipReadsFrozenVersionsFromActualDatabase() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        String name = "com.ruoyi.medical.persistence.mapper.SourceReadMapper.collectionMembers";
        assertTrue(factory.getConfiguration().hasStatement(name), "资料集选择必须读取固定成员版本");
        var mapped = factory.getConfiguration().getMappedStatement(name);
        var bound = mapped.getBoundSql(Map.of("userId", owner, "id", owner, "limit", 2));
        try (var connection = DriverManager.getConnection(database.getMetaData().getURL(), credentials)) {
            connection.setAutoCommit(false);
            try (var query = connection.prepareStatement(bound.getSql())) {
                query.setString(1, owner); query.setInt(2, 2);
                try (var rows = query.executeQuery()) {
                    assertFalse(rows.next());
                    var constructor = mapped.getResultMaps().get(0).getConstructorResultMappings();
                    assertEquals(constructor.size(), rows.getMetaData().getColumnCount());
                    for (int index = 0; index < constructor.size(); index++)
                        assertEquals(constructor.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                }
            }
        }
        try (var session = factory.openSession()) {
            var failure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                    () -> session.selectList(name, Map.of("userId", owner, "id", owner, "limit", 2)));
            assertInstanceOf(TenantAccessException.class, failure.getCause());
        }
    }

    @Test void snapshotCandidateQueriesCannotRunWithoutVerifiedTenantAndSubject() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        String name = "com.ruoyi.medical.persistence.mapper.SourceReadMapper.snapshotCandidate";
        assertTrue(factory.getConfiguration().hasStatement(name), "快照候选读取必须由原租户拦截器保护");
        var failure = assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(name, Map.of("userId", owner)));
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED, failure.code());
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED,
                assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(name)).code());
        try (var session = factory.openSession()) {
            var rejected = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                    () -> session.selectList(name, Map.of("id", owner, "projectId", owner, "parseVersion", owner,
                            "indexVersion", owner, "approvalRevision", owner, "bindingRevision", owner, "userId", owner)));
            assertInstanceOf(TenantAccessException.class, rejected.getCause());
        }
    }

    @Test void actualRightsValidatorRejectsIncompleteAndMalformedAuthority() throws Exception {
        var schema = new SourceRightsSchema();
        for (String body : List.of("{}", "null", "[]")) {
            var row = new SourceReadProtocol.RightsRow(null, null, null, null, "[]", "[]", "[]", null, null, null,
                body, null, "1.0.0", schema.manifestSha256(), null, null, null);
            var failure = assertThrows(TenantAccessException.class, () -> schema.verify(row));
            assertEquals(com.ruoyi.medical.contracts.ErrorCode.CONTENT_MISMATCH, failure.code());
        }
        for (String body : List.of("{", "{} {}", "{\"owner\":\"\",\"owner\":\"\"}")) {
            var row = new SourceReadProtocol.RightsRow(null, null, null, null, "[]", "[]", "[]", null, null, null,
                body, null, "1.0.0", schema.manifestSha256(), null, null, null);
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.verify(row));
        }
    }

    @Test void actualOwnerIdentityCannotSupplyMissingSourceSelectionOrExternalTarget() {
        for (var use : SourceReadProtocol.Use.values()) {
            assertThrows(IllegalArgumentException.class, () -> new SourceReadProtocol.ReadRequirement(owner, owner, null, owner,
                null, null, use, "SOURCE_READING", "LOCAL", "LOCAL", null));
        }
        assertThrows(IllegalArgumentException.class, () -> new SourceReadProtocol.ReadRequirement(owner, owner, owner, owner,
            owner, null, SourceReadProtocol.Use.MODEL_SEND, "MODEL_PROCESSING", "LOCAL", "LOCAL", null));
    }

    @Test void dependencyClosureCannotReadActualOwnerIdentityWithoutEnterpriseTransaction() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        try (var session = factory.openSession()) {
            var service = new DependencyLineage(session.getMapper(com.ruoyi.medical.persistence.mapper.SourceReadMapper.class),
                session.getMapper(com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper.class));
            var failure = assertThrows(TenantAccessException.class,
                () -> service.require(owner, List.of(), new DependencyLineage.Limits(1, 1)));
            assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED, failure.code());
        }
    }

    @Test void dependencyClosureCapacityMustBeExplicitAndPositive() {
        assertThrows(IllegalArgumentException.class, () -> new DependencyLineage.Limits(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new DependencyLineage.Limits(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new DependencyLineage.Limits(-1, -1));
    }

    @AfterAll static void closeDatabase() throws Exception { if (database != null) database.close(); }
}
