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

class ArtifactRegistryReadTest {
    private static Connection database;
    private static String owner;

    @BeforeAll static void actualReadOnlyDatabase() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = new ObjectMapper().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        database.setReadOnly(true); database.setAutoCommit(false);
        try (var query = database.createStatement(); var result = query.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
            assertTrue(result.next()); owner = result.getString(1); assertFalse(result.next());
        }
    }

    @Test void allRegisteredArtifactQueriesExecuteAgainstActualDatabase() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        for (String query : List.of("artifact", "binding", "manifest", "members", "version")) {
            String name = "com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper." + query;
            assertTrue(configuration.hasStatement(name), "Artifact必须读取固定登记及完整依赖");
            var statement = configuration.getMappedStatement(name);
            assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
            var sql = statement.getBoundSql(Map.of("id", owner));
            assertEquals(1, sql.getParameterMappings().size());
            try (var actual = database.prepareStatement(sql.getSql())) {
                actual.setString(1, owner);
                try (var rows = actual.executeQuery()) {
                    assertFalse(rows.next());
                    var constructor = statement.getResultMaps().get(0).getConstructorResultMappings();
                    var components = statement.getResultMaps().get(0).getType().getRecordComponents();
                    assertEquals(components.length, constructor.size());
                    assertEquals(components.length, rows.getMetaData().getColumnCount());
                    for (int index = 0; index < components.length; index++) {
                        assertEquals(components[index].getType(), constructor.get(index).getJavaType());
                        assertEquals(constructor.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                    }
                }
            }
        }
    }

    @Test void dependencyManifestValidationExistsAndRejectsMissingRegistration() throws Exception {
        var type = getClass().getClassLoader().getResource("dependency-access-manifest.schema.json");
        assertNotNull(type, "完整依赖必须使用固定Schema核验");
        var document = new ObjectMapper().readTree(type);
        var schema = com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012)
            .getSchema(document, com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        for (String missing : List.of("{}", "null", "[]")) assertFalse(schema.validate(new ObjectMapper().readTree(missing)).isEmpty());
        assertFalse(document.required("additionalProperties").booleanValue());
        assertEquals(5, document.required("required").size());
    }

    @Test void actualDependencyValidatorRejectsAbsentBodiesAndMatureJsonFailures() throws Exception {
        var schema = new DependencyManifestSchema();
        for (String body : List.of("{}", "null", "[]")) {
            var row = new ArtifactRegistryProtocol.ManifestRow(null, null, null, body, null, null, java.math.BigInteger.ZERO,
                "1.0.0", schema.manifestSha256());
            var failure = assertThrows(TenantAccessException.class, () -> schema.verify(row, List.of()));
            assertEquals(com.ruoyi.medical.contracts.ErrorCode.CONTENT_MISMATCH, failure.code());
        }
        for (String body : List.of("{} {}", "{", "{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}")) {
            var row = new ArtifactRegistryProtocol.ManifestRow(null, null, null, body, null, null, java.math.BigInteger.ZERO,
                "1.0.0", schema.manifestSha256());
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.verify(row, List.of()));
        }
    }

    @AfterAll static void closeReadOnlyDatabase() throws Exception { if (database != null) database.close(); }
}
