package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UploadAuthorizationTest {
    private static final String PROTOCOL = "com.ruoyi.medical.persistence.UploadAuthorizationProtocol$Manifest";
    private static final Map<String, Class<?>> TYPES = Map.of(
        "SourceRequirement", SourceReadProtocol.ReadRequirement.class,
        "ArtifactRequirement", ArtifactRegistryProtocol.ReadRequirement.class,
        "DependencyRequirement", DependencyAccessProtocol.ReadRequirement.class,
        "ReadContext", VersionResourceProtocol.ReadContext.class,
        "Scope", PermissionProtocol.Scope.class,
        "LineageLimits", DependencyLineage.Limits.class,
        "BasisSettings", DocumentUploadBasisProtocol.Settings.class,
        "DocumentBasis", DocumentUploadBasisProtocol.Binding.class);

    private com.fasterxml.jackson.databind.JsonNode schema() throws Exception {
        var resource = getClass().getClassLoader().getResource("upload-authorization.schema.json");
        assertNotNull(resource, "上传授权必须有完整封闭Schema");
        try (var input = resource.openStream()) { return new ObjectMapper().readTree(input); }
    }

    @Test void allExistingAuthorizationRequirementsHaveCompleteClosedDefinitions() throws Exception {
        var document = schema();
        for (var entry : TYPES.entrySet()) {
            var definition = document.required("$defs").required(entry.getKey());
            assertEquals("object", definition.required("type").textValue());
            assertFalse(definition.required("additionalProperties").booleanValue());
            var fields = new java.util.HashSet<String>(); definition.required("properties").fieldNames().forEachRemaining(fields::add);
            var required = new java.util.HashSet<String>(); definition.required("required").forEach(field -> required.add(field.textValue()));
            assertEquals(fields, required, entry.getKey());
            var original = Arrays.stream(entry.getValue().getRecordComponents()).map(component ->
                com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE.translate(component.getName()))
                .collect(java.util.stream.Collectors.toSet());
            assertEquals(original, fields, entry.getKey());
        }
    }

    @Test void manifestKeepsOriginalIdentityWatermarksSchemaDependencyAndCompleteSelection() throws Exception {
        var document = schema(); var resource = getClass().getClassLoader().getResource(PROTOCOL.replace('.', '/') + ".class");
        assertNotNull(resource, "完整上传授权必须保留服务器固定选择");
        var manifest = Class.forName(PROTOCOL);
        var fields = Arrays.stream(manifest.getRecordComponents()).map(component ->
            com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE.translate(component.getName()))
            .collect(java.util.stream.Collectors.toSet());
        var expected = Set.of("schema_version", "tenant_id", "subject_id", "project_id", "purpose", "auth_epoch", "governance_epoch",
            "schema_ref", "schema_document_sha256", "artifact_schema_manifest", "dependency_access_set_id", "dependency_manifest_sha256",
            "dependency_requirement", "sources", "context", "upstream_artifacts", "document_basis");
        assertEquals(expected, fields);
        var declared = new java.util.HashSet<String>(); document.required("properties").fieldNames().forEachRemaining(declared::add);
        assertEquals(expected, declared); assertFalse(document.required("additionalProperties").booleanValue());
        var required = new java.util.HashSet<String>(); document.required("required").forEach(field -> required.add(field.textValue()));
        assertEquals(expected, required);
        assertEquals(List.class, manifest.getDeclaredMethod("sources").getReturnType());
        assertEquals(Map.class, manifest.getDeclaredMethod("upstreamArtifacts").getReturnType());
        assertEquals(String.class, manifest.getDeclaredMethod("authEpoch").getReturnType());
        assertEquals(String.class, manifest.getDeclaredMethod("governanceEpoch").getReturnType());
    }

    @Test void actualPublicDatasetCannotBecomeUploadAuthorization() throws Exception {
        var resource = getClass().getClassLoader().getResource("com/ruoyi/medical/persistence/UploadAuthorizationSchema.class");
        assertNotNull(resource, "上传保存必须校验真实正文的授权类型");
        var type = Class.forName("com.ruoyi.medical.persistence.UploadAuthorizationSchema"); var validator = type.getConstructor().newInstance();
        var bytes = Files.readAllBytes(Path.of(System.getProperty("medical.projectRoot")).resolve("data-design/sample_dataset.json"));
        var hash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(new org.erdtman.jcs.JsonCanonicalizer(bytes).getEncodedUTF8());
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () ->
            type.getDeclaredMethod("decode", String.class, String.class).invoke(validator, new String(bytes, java.nio.charset.StandardCharsets.UTF_8), hash));
        assertInstanceOf(TenantAccessException.class, failure.getCause());
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.CONTENT_MISMATCH, ((TenantAccessException) failure.getCause()).code());
    }

    @Test void currentDocumentQueryReadsEveryHeadColumnAndActualProjectStatus() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = new ObjectMapper().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new java.util.Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new org.springframework.jdbc.datasource.DriverManagerDataSource());
        String name = "com.ruoyi.medical.persistence.mapper.UploadTransferMapper.document";
        assertTrue(factory.getConfiguration().hasStatement(name), "上传需要读取当前文稿及项目状态");
        var statement = factory.getConfiguration().getMappedStatement(name);
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        try (var database = java.sql.DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials)) {
            database.setReadOnly(true); database.setAutoCommit(false);
            String owner;
            try (var query = database.createStatement(); var rows = query.executeQuery(
                    "SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
                assertTrue(rows.next()); owner = rows.getString(1); assertFalse(rows.next());
            }
            var parameters = Map.<String,Object>of("userId", owner, "documentId", java.util.UUID.randomUUID().toString(), "lock", false);
            var bound = statement.getBoundSql(parameters);
            try (var query = database.prepareStatement(bound.getSql())) {
                new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement, parameters, bound).setParameters(query);
                try (var rows = query.executeQuery()) {
                    assertFalse(rows.next()); var columns = statement.getResultMaps().get(0).getConstructorResultMappings();
                    assertEquals(12, columns.size()); assertEquals(12, rows.getMetaData().getColumnCount());
                    var components = statement.getResultMaps().get(0).getType().getRecordComponents();
                    assertEquals(columns.size(), components.length);
                    for (int index = 0; index < columns.size(); index++) {
                        assertEquals(columns.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                        assertEquals(components[index].getType(), columns.get(index).getJavaType());
                    }
                }
            }
            try (var query = database.createStatement(); var rows = query.executeQuery(
                    "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='document_head' ORDER BY ORDINAL_POSITION")) {
                var physical = new java.util.ArrayList<String>(); while (rows.next()) physical.add(rows.getString(1));
                assertEquals(statement.getResultMaps().get(0).getConstructorResultMappings().subList(0, 11).stream()
                    .map(org.apache.ibatis.mapping.ResultMapping::getColumn).toList(), physical);
            }
            assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED,
                assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(name, parameters)).code());
        }
    }

    @Test void documentTransferRejectsMissingApplicationIdentityBeforeObjectIo() throws Exception {
        String name = "com.ruoyi.medical.persistence.DocumentUploadTransferService";
        assertNotNull(getClass().getClassLoader().getResource(name.replace('.', '/') + ".class"), "文稿实际传输必须使用当前应用身份和独立短事务");
        var method = Class.forName(name).getDeclaredMethod("requireOutsideTransaction", com.ruoyi.medical.identity.ApplicationSessionIdentity.Verified.class);
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(null, (Object) null));
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED, ((TenantAccessException) failure.getCause()).code());
    }

    @Test void documentUploadAuthorityRejectsMissingActualAdmission() throws Exception {
        String name = "com.ruoyi.medical.persistence.UploadAuthorizationAuthority";
        assertNotNull(getClass().getClassLoader().getResource(name.replace('.', '/') + ".class"), "文稿上传需要当前授权核查");
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new org.springframework.jdbc.datasource.DriverManagerDataSource());
        try (var session = factory.openSession()) {
            var permissions = session.getMapper(com.ruoyi.medical.persistence.mapper.PermissionMapper.class);
            var resource = new ResourceReadAuthority(permissions, new PermissionAuthority(new PermissionSchema()), new PermissionSchema());
            var sourceMapper = session.getMapper(com.ruoyi.medical.persistence.mapper.SourceReadMapper.class);
            var registryMapper = session.getMapper(com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper.class);
            var manifests = new DependencyManifestSchema(); var lineage = new DependencyLineage(sourceMapper, registryMapper);
            var registry = new ArtifactRegistry(registryMapper, resource, manifests, lineage);
            var versions = new VersionResourceAuthority(session.getMapper(com.ruoyi.medical.persistence.mapper.VersionResourceMapper.class), resource, registry);
            var sources = new DependencySourceAuthority(sourceMapper,
                new SourceReadAuthority(sourceMapper, permissions, resource, new SourceRightsSchema()), versions);
            var dependencies = new DependencyAccessAuthority(session.getMapper(com.ruoyi.medical.persistence.mapper.DependencyAccessMapper.class),
                registryMapper, manifests, lineage, sources);
            var bases = new DocumentUploadBasisAuthority(session.getMapper(com.ruoyi.medical.persistence.mapper.DocumentUploadBasisMapper.class),
                session.getMapper(com.ruoyi.medical.persistence.mapper.UploadTransferMapper.class), registryMapper, sourceMapper, resource,
                new SourceReadAuthority(sourceMapper, permissions, resource, new SourceRightsSchema()),
                new ScopeSnapshotSchema(new TaskInputSchema(new PermissionSchema())));
            var type = Class.forName(name);
            var authority = type.getConstructor(com.ruoyi.medical.persistence.mapper.UploadTransferMapper.class,
                ResourceReadAuthority.class, DependencyAccessAuthority.class, UploadAuthorizationSchema.class, DocumentUploadBasisAuthority.class).newInstance(
                    session.getMapper(com.ruoyi.medical.persistence.mapper.UploadTransferMapper.class), resource, dependencies, new UploadAuthorizationSchema(), bases);
            for (String method : List.of("requireDocument", "requireBoundDocument", "requireVisibleBoundDocument")) {
                var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> {
                    if (method.equals("requireDocument")) type.getDeclaredMethod(method,
                        EnterpriseAccess.Admission.class, String.class, String.class, Class.forName(PROTOCOL)).invoke(authority, null, null, null, null);
                    else type.getDeclaredMethod(method, EnterpriseAccess.Admission.class, String.class).invoke(authority, null, null);
                });
                assertInstanceOf(TenantAccessException.class, failure.getCause());
                assertEquals(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED, ((TenantAccessException) failure.getCause()).code());
            }
        }
    }
}
