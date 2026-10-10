package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class DocumentUploadIssuanceTest {
    private static final String NAME = "com.ruoyi.medical.persistence.DocumentUploadIssuance";
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.UploadIssuanceMapper.";
    private static Connection database;
    private static String owner;

    @BeforeAll static void actualDatabase() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = new ObjectMapper().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        database.setAutoCommit(false);
        try (var query = database.createStatement(); var rows = query.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
            assertTrue(rows.next()); owner = rows.getString(1); assertFalse(rows.next());
        }
    }

    private org.apache.ibatis.session.SqlSessionFactory factory() throws Exception {
        return new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
    }

    @Test void issuanceRejectsMissingActualAdmissionBeforeAnyBusinessWrite() throws Exception {
        assertNotNull(getClass().getClassLoader().getResource(NAME.replace('.', '/') + ".class"),
            "上传许可必须在实际企业事务保存原幂等记录、审计和事件");
        var type = Class.forName(NAME); var mapperType = Class.forName("com.ruoyi.medical.persistence.mapper.UploadIssuanceMapper");
        try (var session = factory().openSession()) {
            var permissions = session.getMapper(PermissionMapper.class); var json = new PermissionSchema();
            var resource = new ResourceReadAuthority(permissions, new PermissionAuthority(json), json);
            var sourceMapper = session.getMapper(SourceReadMapper.class); var registryMapper = session.getMapper(ArtifactRegistryMapper.class);
            var source = new SourceReadAuthority(sourceMapper, permissions, resource, new SourceRightsSchema());
            var manifests = new DependencyManifestSchema(); var lineage = new DependencyLineage(sourceMapper, registryMapper);
            var registry = new ArtifactRegistry(registryMapper, resource, manifests, lineage);
            var versions = new VersionResourceAuthority(session.getMapper(VersionResourceMapper.class), resource, registry);
            var dependencyMapper = session.getMapper(DependencyAccessMapper.class);
            var dependencies = new DependencyAccessAuthority(dependencyMapper, registryMapper, manifests, lineage,
                new DependencySourceAuthority(sourceMapper, source, versions));
            var uploads = session.getMapper(UploadTransferMapper.class); var authorizations = new UploadAuthorizationSchema();
            var basis = new DocumentUploadBasisAuthority(session.getMapper(DocumentUploadBasisMapper.class), uploads, registryMapper, sourceMapper,
                resource, source, new ScopeSnapshotSchema(new TaskInputSchema(json)));
            var authority = new UploadAuthorizationAuthority(uploads, resource, dependencies, authorizations, basis);
            var preparation = new DocumentUploadPreparation(basis, dependencyMapper, authority, uploads, authorizations);
            var service = type.getConstructor(DocumentUploadPreparation.class, UploadTransferStore.class, UploadAuthorizationAuthority.class,
                mapperType, PermissionMapper.class, PermissionSchema.class).newInstance(preparation, new UploadTransferStore(uploads, authorizations),
                authority, session.getMapper(mapperType), permissions, json);
            var failure = assertThrows(InvocationTargetException.class, () -> type.getDeclaredMethod("issue", EnterpriseAccess.Admission.class,
                String.class, String.class, byte[].class, DocumentUploadPreparation.Settings.class).invoke(service, null, null, null, null, null));
            assertEquals(ErrorCode.ACCESS_DENIED, ((TenantAccessException) failure.getCause()).code());
        }
    }

    @Test void originalOperationReadMapsEveryActualPhysicalColumn() throws Exception {
        var configuration = factory().getConfiguration();
        assertTrue(configuration.hasStatement(PREFIX + "currentOperation"), "上传重复请求必须完整读取原幂等记录");
        var statement = configuration.getMappedStatement(PREFIX + "currentOperation");
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        var values = Map.<String,Object>of("userId", owner, "key", owner); var bound = statement.getBoundSql(values);
        assertTrue(bound.getSql().contains("FOR UPDATE"));
        try (var query = database.prepareStatement(bound.getSql())) {
            new DefaultParameterHandler(statement, values, bound).setParameters(query);
            try (var rows = query.executeQuery()) {
                assertFalse(rows.next()); var columns = rows.getMetaData();
                var mapping = statement.getResultMaps().get(0); var components = mapping.getType().getRecordComponents();
                assertEquals(20, columns.getColumnCount()); assertEquals(20, components.length);
                var arguments = mapping.getConstructorResultMappings(); assertEquals(20, arguments.size());
                for (int index = 0; index < components.length; index++) {
                    assertEquals(arguments.get(index).getColumn(), columns.getColumnLabel(index + 1));
                    assertEquals(components[index].getType(), arguments.get(index).getJavaType());
                }
            }
        }
    }

    @Test void operationAuditAndEventRequireCurrentSubjectAndEnterpriseTransaction() {
        for (String name : List.of("currentOperation", "saveOperation", "audit", "event", "documentUploadForGrant", "completionOperation",
                "startCompletionOperation", "completeCompletionOperation", "saveArtifactVersion", "saveArtifact", "saveContentBinding",
                "saveArtifactLineage", "completeGrant", "completionAudit", "completionEvent", "sourceChunk")) {
            var denied = assertThrows(TenantAccessException.class,
                () -> TenantScope.requireSubjectParameter(PREFIX + name, Map.of("userId", owner)));
            assertEquals(ErrorCode.ACCESS_DENIED, denied.code());
            var transaction = assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(PREFIX + name));
            assertEquals(ErrorCode.ACCESS_DENIED, transaction.code());
        }
    }

    @Test void actualCompletionReadsUseExistingTablesAndTypedOriginalColumns() throws Exception {
        var configuration = factory().getConfiguration();
        var values = new java.util.HashMap<String,Object>();
        for (String key : List.of("userId", "key", "grantId", "projectId", "auditId", "eventId", "artifactId", "requestId",
                "sourceVersion", "parseVersion", "chunkId")) values.put(key, owner);
        values.put("manifest", org.apache.commons.codec.digest.DigestUtils.sha256Hex(
            Files.readAllBytes(Path.of(System.getProperty("medical.projectRoot")).resolve("backend-design/interfaces.schema.yaml"))));
        values.put("classification", "D0");
        for (String name : List.of("documentUploadForGrant", "completionOperation", "completionAudit", "completionEvent", "sourceChunk")) {
            var statement = configuration.getMappedStatement(PREFIX + name);
            var bound = statement.getBoundSql(values);
            try (var query = database.prepareStatement(bound.getSql())) {
                new DefaultParameterHandler(statement, values, bound).setParameters(query);
                try (var rows = query.executeQuery()) {
                    assertFalse(rows.next());
                    var mapping = statement.getResultMaps().get(0);
                    if (mapping.getType().isRecord()) {
                        var arguments = mapping.getConstructorResultMappings();
                        assertEquals(arguments.size(), rows.getMetaData().getColumnCount());
                        for (int index = 0; index < arguments.size(); index++)
                            assertEquals(arguments.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                    } else assertEquals(1, rows.getMetaData().getColumnCount());
                }
            }
        }
    }

    @Test void actualAuditAndEventQueriesReadOriginalIdentityWithoutWriting() throws Exception {
        var configuration = factory().getConfiguration();
        var values = Map.<String,Object>of("userId", owner, "projectId", owner, "auditId", owner, "eventId", owner,
            "uploadId", owner, "requestId", owner, "manifest", org.apache.commons.codec.digest.DigestUtils.sha256Hex(
                Files.readAllBytes(Path.of(System.getProperty("medical.projectRoot")).resolve("backend-design/interfaces.schema.yaml"))),
            "classification", "D0");
        for (String name : List.of("audit", "event")) {
            var statement = configuration.getMappedStatement(PREFIX + name);
            assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
            var bound = statement.getBoundSql(values); assertTrue(bound.getSql().contains("FOR UPDATE"));
            try (var query = database.prepareStatement(bound.getSql())) {
                new DefaultParameterHandler(statement, values, bound).setParameters(query);
                try (var rows = query.executeQuery()) { assertFalse(rows.next()); assertEquals(1, rows.getMetaData().getColumnCount()); }
            }
        }
    }

    @Test void actualDocumentReadsRetainFixedRevisionAndUseThePublishedPhysicalColumns() throws Exception {
        String prefix = "com.ruoyi.medical.persistence.mapper.DocumentReadMapper.";
        var configuration = factory().getConfiguration();
        var values = Map.<String,Object>of("userId", owner, "projectId", owner, "documentId", owner,
            "documentRevision", owner, "snapshotId", owner);
        for (String name : List.of("header", "projections", "watermarks", "checks", "reviews", "signatures", "invalidationReasons", "streams", "sourceCount")) {
            assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> TenantScope.requireSubjectParameter(prefix + name, values)).code());
            var statement = configuration.getMappedStatement(prefix + name); var bound = statement.getBoundSql(values);
            assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
            try (var query = database.prepareStatement(bound.getSql())) {
                new DefaultParameterHandler(statement, values, bound).setParameters(query);
                try (var rows = query.executeQuery()) {
                    var mapping = statement.getResultMaps().get(0); var metadata = rows.getMetaData();
                    if (name.equals("sourceCount")) { assertTrue(rows.next()); assertEquals(0, rows.getLong(1)); }
                    else assertFalse(rows.next());
                    if (mapping.getType().isRecord()) {
                        var arguments = mapping.getConstructorResultMappings();
                        assertEquals(arguments.size(), metadata.getColumnCount());
                        var components = mapping.getType().getRecordComponents();
                        for (int index = 0; index < arguments.size(); index++) {
                            assertEquals(arguments.get(index).getColumn(), metadata.getColumnLabel(index + 1));
                            assertEquals(components[index].getType(), arguments.get(index).getJavaType());
                        }
                    } else assertEquals(1, metadata.getColumnCount());
                }
            }
        }
    }

    @Test void savedRequestIdentityIncludesAllPhysicalColumnsAndNeverCreatesCustomerRows() throws Exception {
        var configuration = factory().getConfiguration();
        assertTrue(configuration.hasStatement(PREFIX + "saveOperation"), "许可、操作、审计及Outbox必须一起保存");
        var statement = configuration.getMappedStatement(PREFIX + "saveOperation");
        var sql = statement.getBoundSql(Map.of("userId", owner)).getSql();
        var insert = (net.sf.jsqlparser.statement.insert.Insert) net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql);
        assertEquals("operation_record", insert.getTable().getName());
        var actual = insert.getColumns().stream().map(net.sf.jsqlparser.schema.Column::getColumnName).collect(java.util.stream.Collectors.toSet());
        var expected = new java.util.HashSet<String>();
        try (var query = database.createStatement(); var rows = query.executeQuery("SHOW COLUMNS FROM operation_record")) {
            while (rows.next()) if (!rows.getString("Field").equals("tenant_id")) expected.add(rows.getString("Field"));
        }
        assertEquals(expected, actual);
        for (String table : List.of("tenant", "project", "artifact_upload_grant", "object_upload_session", "artifact_upload_binding", "artifact")) {
            try (var query = database.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM " + table)) {
                assertTrue(rows.next()); assertEquals(0, rows.getLong(1)); assertFalse(rows.next());
            }
        }
    }

    @AfterAll static void closeDatabase() throws Exception { if (database != null) database.close(); }
}
