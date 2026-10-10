package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.persistence.mapper.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class DocumentUploadPreparationTest {
    private static final String NAME = "com.ruoyi.medical.persistence.DocumentUploadPreparation";

    private Class<?> implementation() throws Exception {
        assertNotNull(getClass().getClassLoader().getResource(NAME.replace('.', '/') + ".class"),
            "服务器需要使用实际上传请求、当前领域依据与固定读取要求准备完整许可");
        return Class.forName(NAME);
    }

    private byte[] actualDataset() throws Exception {
        return Files.readAllBytes(Path.of(System.getProperty("medical.projectRoot")).resolve("data-design/sample_dataset.json"));
    }

    private Object preparation(Class<?> type, org.apache.ibatis.session.SqlSession session) throws Exception {
        var permissions = session.getMapper(PermissionMapper.class);
        var resource = new ResourceReadAuthority(permissions, new PermissionAuthority(new PermissionSchema()), new PermissionSchema());
        var sourceMapper = session.getMapper(SourceReadMapper.class); var registryMapper = session.getMapper(ArtifactRegistryMapper.class);
        var source = new SourceReadAuthority(sourceMapper, permissions, resource, new SourceRightsSchema());
        var manifests = new DependencyManifestSchema(); var lineage = new DependencyLineage(sourceMapper, registryMapper);
        var registry = new ArtifactRegistry(registryMapper, resource, manifests, lineage);
        var versions = new VersionResourceAuthority(session.getMapper(VersionResourceMapper.class), resource, registry);
        var dependencies = new DependencyAccessAuthority(session.getMapper(DependencyAccessMapper.class), registryMapper,
            manifests, lineage, new DependencySourceAuthority(sourceMapper, source, versions));
        var uploads = session.getMapper(UploadTransferMapper.class); var authorizations = new UploadAuthorizationSchema();
        var basis = new DocumentUploadBasisAuthority(session.getMapper(DocumentUploadBasisMapper.class), uploads, registryMapper, sourceMapper,
            resource, source, new ScopeSnapshotSchema(new TaskInputSchema(new PermissionSchema())));
        var authority = new UploadAuthorizationAuthority(uploads, resource, dependencies, authorizations, basis);
        return type.getConstructor(DocumentUploadBasisAuthority.class, DependencyAccessMapper.class, UploadAuthorizationAuthority.class,
            UploadTransferMapper.class, UploadAuthorizationSchema.class).newInstance(basis, session.getMapper(DependencyAccessMapper.class), authority, uploads, authorizations);
    }

    @Test void preparationRejectsMissingActualAdmissionBeforeReadingOrAllocatingBusinessObjects() throws Exception {
        var type = implementation(); var settings = Class.forName(NAME + "$Settings");
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        try (var session = factory.openSession()) {
            var service = preparation(type, session);
            var failure = assertThrows(InvocationTargetException.class, () -> type.getDeclaredMethod("prepare",
                EnterpriseAccess.Admission.class, byte[].class, settings).invoke(service, null, null, null));
            assertEquals(ErrorCode.ACCESS_DENIED, ((TenantAccessException) failure.getCause()).code());
        }
    }

    @Test void actualPublicDatasetCannotBecomeAValidUploadRequest() throws Exception {
        var type = implementation(); var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        try (var session = factory.openSession()) {
            var service = preparation(type, session); var bytes = actualDataset();
            var failure = assertThrows(InvocationTargetException.class,
                () -> type.getDeclaredMethod("request", byte[].class).invoke(service, bytes));
            assertEquals(ErrorCode.SCHEMA_INVALID, ((SchemaRejection) failure.getCause()).code());
        }
    }

    @Test void requestEncodingAndCompleteJsonAreCheckedBeforeSchemaBinding() throws Exception {
        var type = implementation(); var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        try (var session = factory.openSession()) {
            var service = preparation(type, session); var request = type.getDeclaredMethod("request", byte[].class);
            byte[] original = actualDataset(); byte[] utf16 = new String(original, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_16);
            var encoding = assertThrows(InvocationTargetException.class, () -> request.invoke(service, utf16));
            assertInstanceOf(java.nio.charset.CharacterCodingException.class, encoding.getCause());
            var repeated = new java.io.ByteArrayOutputStream(); repeated.write(original); repeated.write(original);
            var trailing = assertThrows(InvocationTargetException.class, () -> request.invoke(service, repeated.toByteArray()));
            assertInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class, trailing.getCause());
        }
    }

    @Test void serverSettingsCannotInferReadRequirementsPublicationCapacityOrDeadline() throws Exception {
        implementation(); var type = Class.forName(NAME + "$Settings");
        var constructor = type.getDeclaredConstructor(DocumentUploadBasisProtocol.Settings.class, DependencyLineage.Limits.class,
            String.class, java.math.BigInteger.class, long.class, Map.class);
        var failure = assertThrows(InvocationTargetException.class, () -> constructor.newInstance(null, null, null, null, 0L, null));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }
}
