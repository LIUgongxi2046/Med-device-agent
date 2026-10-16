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

class VersionResourceAuthorityTest {
    private static Connection database;
    private static String owner;
    private static final String STATEMENT = "com.ruoyi.medical.persistence.mapper.VersionResourceMapper.owners";
    private static final List<String> DOMAINS = List.of("SOURCE_VERSION", "SOURCE_EXACT_REVISION", "EXACT_RETRIEVAL", "PARSE_VERSION", "INDEX_MANIFEST", "APPROVAL_DECISION",
        "RIGHTS_REVISION", "SOURCE_BINDING", "COLLECTION_VERSION", "PROJECT_PROFILE", "SCOPE_SNAPSHOT", "TEMPLATE_VERSION",
        "RULE_VERSION", "TASK_REVISION", "PLAN_REVISION", "DOCUMENT_REVISION", "SECTION_REVISION", "SHARED_FACT",
        "ARTIFACT", "DEPENDENCY_ACCESS_SET", "CONTEXT_MANIFEST", "CANDIDATE", "CAPABILITY_VERSION", "MODEL_CONFIG",
        "AGENT_VERSION", "RELEASE_RECORD", "RELEASE_SCOPE", "CONNECTOR_CONFIG", "EVALUATION", "TASK_SUMMARY",
        "BUDGET_REVISION", "CONVERSATION_MESSAGE", "ROLE_DEFINITION", "SIGNATURE", "EXPORT_MANIFEST", "MANAGEMENT_RELEASE",
        "IMPACT_MANIFEST", "APPLICABILITY_DECISION");

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

    @Test void concreteVersionOwnersUseActualDomainTablesAndExactConstructorColumns() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        assertTrue(configuration.hasStatement(STATEMENT), "派生版本必须关联实际领域资源，不能使用版本UUID替代资源UUID");
        var statement = configuration.getMappedStatement(STATEMENT);
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        for (String domain : DOMAINS) {
            var bound = statement.getBoundSql(Map.of("id", owner, "domain", domain));
            try (var actual = database.prepareStatement(bound.getSql())) {
                for (int index = 0; index < bound.getParameterMappings().size(); index++) actual.setString(index + 1, owner);
                try (var rows = actual.executeQuery()) {
                    assertFalse(rows.next());
                    var constructor = statement.getResultMaps().get(0).getConstructorResultMappings();
                    var components = statement.getResultMaps().get(0).getType().getRecordComponents();
                    assertEquals(components.length, rows.getMetaData().getColumnCount(), domain);
                    assertEquals(components.length, constructor.size(), domain);
                    for (int index = 0; index < components.length; index++) {
                        assertEquals(components[index].getType(), constructor.get(index).getJavaType(), domain);
                        assertEquals(constructor.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1), domain);
                    }
                }
            }
        }
    }

    @Test void versionResourceQueriesRequireCurrentEnterpriseTransaction() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        assertTrue(configuration.hasStatement(STATEMENT), "派生领域查询必须存在并接受企业事务检查");
        var failure = assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(STATEMENT));
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED, failure.code());
        var batchFailure = assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(STATEMENT + "AcrossDomains"));
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED, batchFailure.code());
    }

    @Test void bulkOwnerQueryChecksAllDomainsWithExactInputIdentities() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var statement = configuration.getMappedStatement(STATEMENT + "AcrossDomains");
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        var parameters = Map.of("ids", List.of(owner, java.util.UUID.randomUUID().toString()), "domains", DOMAINS);
        var bound = statement.getBoundSql(parameters);
        try (var query = database.prepareStatement(bound.getSql())) {
            new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, parameters, bound).setParameters(query);
            try (var rows = query.executeQuery()) {
                assertFalse(rows.next());
                assertEquals(12, rows.getMetaData().getColumnCount());
                assertEquals("domain", rows.getMetaData().getColumnLabel(1));
                assertEquals("version_id", rows.getMetaData().getColumnLabel(3));
                assertEquals("resource_id", rows.getMetaData().getColumnLabel(7));
            }
        }
        assertEquals(DOMAINS.size() * 3, bound.getParameterMappings().size());
    }

    @Test void taskDocumentAndConfigurationVersionsRetainDistinctResourceIdentity() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var statement = configuration.getMappedStatement(STATEMENT);
        var expected = Map.of("TASK_REVISION", "task_id", "PLAN_REVISION", "task_id", "DOCUMENT_REVISION", "document_id",
            "MODEL_CONFIG", "config_id", "CAPABILITY_VERSION", "config_id", "AGENT_VERSION", "agent_id", "BUDGET_REVISION", "budget_id");
        for (var domain : expected.entrySet()) {
            var sql = statement.getBoundSql(Map.of("id", owner, "domain", domain.getKey()));
            var select = (net.sf.jsqlparser.statement.select.PlainSelect) net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql.getSql());
            var resource = select.getSelectItems().stream().filter(item -> item.getAlias() != null && item.getAlias().getName().equals("resource_id")).toList();
            var version = select.getSelectItems().stream().filter(item -> item.getAlias() != null && item.getAlias().getName().equals("version_id")).toList();
            assertEquals(1, resource.size()); assertEquals(1, version.size());
            var resourceColumn = (net.sf.jsqlparser.schema.Column) resource.get(0).getExpression();
            var versionColumn = (net.sf.jsqlparser.schema.Column) version.get(0).getExpression();
            assertEquals(domain.getValue(), resourceColumn.getColumnName());
            assertNotEquals(resourceColumn.getColumnName(), versionColumn.getColumnName());
        }
    }

    @Test void actualVersionServiceAndArtifactConsumerRequireEnterpriseAdmission() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        try (var session = factory.openSession()) {
            var permissionSchema = new PermissionSchema();
            var resources = new ResourceReadAuthority(session.getMapper(com.ruoyi.medical.persistence.mapper.PermissionMapper.class),
                new PermissionAuthority(permissionSchema), permissionSchema);
            var registryMapper = session.getMapper(com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper.class);
            var sourceMapper = session.getMapper(com.ruoyi.medical.persistence.mapper.SourceReadMapper.class);
            var lineage = new DependencyLineage(sourceMapper, registryMapper);
            var registry = new ArtifactRegistry(registryMapper, resources, new DependencyManifestSchema(), lineage);
            var versions = new VersionResourceAuthority(session.getMapper(com.ruoyi.medical.persistence.mapper.VersionResourceMapper.class), resources, registry);
            var failure = assertThrows(TenantAccessException.class, () -> versions.require(null, null, "ARTIFACT_READING", null, Map.of()));
            assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED, failure.code());
            var sources = new ArtifactSourceAuthority(registry, new DependencySourceAuthority(sourceMapper, new SourceReadAuthority(sourceMapper,
                session.getMapper(com.ruoyi.medical.persistence.mapper.PermissionMapper.class), resources, new SourceRightsSchema()), versions));
            var consumerFailure = assertThrows(TenantAccessException.class,
                () -> sources.require(null, owner, null, List.of(), null, Map.of()));
            assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED, consumerFailure.code());
        }
    }

    @Test void conversationContextRequiresCanonicalIdentityAndExplicitUse() {
        assertThrows(NullPointerException.class, () -> new VersionResourceProtocol.ReadContext(null, owner));
        assertThrows(IllegalArgumentException.class, () -> new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.HISTORY, owner.toUpperCase(java.util.Locale.ROOT)));
    }

    @AfterAll static void closeDatabase() throws Exception { if (database != null) database.close(); }
}
