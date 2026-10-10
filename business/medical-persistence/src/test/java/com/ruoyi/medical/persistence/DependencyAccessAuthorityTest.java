package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class DependencyAccessAuthorityTest {
    private static Connection database;
    private static String owner;
    private static final String STATEMENT = "com.ruoyi.medical.persistence.mapper.DependencyAccessMapper.header";

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

    @Test void dependencyHeaderCanBeCheckedBeforeAnArtifactExists() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        assertTrue(configuration.hasStatement(STATEMENT), "独立依赖核验必须读取实际依赖清单主体，不依赖先行Artifact登记");
        var statement = configuration.getMappedStatement(STATEMENT);
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        var bound = statement.getBoundSql(Map.of("id", owner));
        try (var actual = database.prepareStatement(bound.getSql())) {
            actual.setString(1, owner);
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

    @Test void dependencyHeaderRequiresCurrentEnterpriseTransaction() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        var configuration = factory.getConfiguration();
        assertTrue(configuration.hasStatement(STATEMENT), "独立依赖查询必须存在并接受企业事务保护");
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(STATEMENT)).code());
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(com.ruoyi.medical.persistence.mapper.DependencyAccessMapper.class);
            var failure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, () -> mapper.header(owner));
            assertInstanceOf(TenantAccessException.class, failure.getCause());
            assertEquals(ErrorCode.ACCESS_DENIED, ((TenantAccessException) failure.getCause()).code());
        }
    }

    @Test void independentDependencyAndSharedSourceServicesRequireEnterpriseAdmission() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        try (var session = factory.openSession()) {
            var permissionMapper = session.getMapper(com.ruoyi.medical.persistence.mapper.PermissionMapper.class);
            var permissionSchema = new PermissionSchema();
            var resources = new ResourceReadAuthority(permissionMapper, new PermissionAuthority(permissionSchema), permissionSchema);
            var registryMapper = session.getMapper(com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper.class);
            var sourceMapper = session.getMapper(com.ruoyi.medical.persistence.mapper.SourceReadMapper.class);
            var lineage = new DependencyLineage(sourceMapper, registryMapper);
            var manifests = new DependencyManifestSchema();
            var registry = new ArtifactRegistry(registryMapper, resources, manifests, lineage);
            var versions = new VersionResourceAuthority(session.getMapper(com.ruoyi.medical.persistence.mapper.VersionResourceMapper.class), resources, registry);
            var sources = new DependencySourceAuthority(sourceMapper,
                new SourceReadAuthority(sourceMapper, permissionMapper, resources, new SourceRightsSchema()), versions);
            var dependencies = new DependencyAccessAuthority(session.getMapper(com.ruoyi.medical.persistence.mapper.DependencyAccessMapper.class),
                registryMapper, manifests, lineage, sources);
            assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> dependencies.require(null, owner, null, List.of(), null, Map.of())).code());
            assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> sources.require(null, null, null, null, "DOCUMENT_EDITING", List.of(), null, Map.of())).code());
        }
    }

    @Test void dependencyReadRequirementRequiresFixedPurposeAndExplicitCapacity() {
        var limits = new DependencyLineage.Limits(1, 1);
        assertThrows(IllegalArgumentException.class, () -> new DependencyAccessProtocol.ReadRequirement(owner.toUpperCase(java.util.Locale.ROOT), "DOCUMENT_EDITING", limits));
        assertThrows(IllegalArgumentException.class, () -> new DependencyAccessProtocol.ReadRequirement(owner, "document_editing", limits));
        assertThrows(NullPointerException.class, () -> new DependencyAccessProtocol.ReadRequirement(owner, "DOCUMENT_EDITING", null));
    }

    @AfterAll static void closeDatabase() throws Exception { if (database != null) database.close(); }
}
