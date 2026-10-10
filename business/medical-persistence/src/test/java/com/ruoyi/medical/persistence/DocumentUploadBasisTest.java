package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class DocumentUploadBasisTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.DocumentUploadBasisMapper.";
    private static final Map<String,String> TABLES = Map.of("task", "task_head", "taskRevision", "task_revision",
        "plan", "plan_revision", "snapshot", "scope_snapshot", "members", "scope_member");

    @Test void currentTaskPlanSnapshotAndAllMembersUseCompleteActualDatabaseFields() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = new ObjectMapper().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        try (var database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials)) {
            database.setReadOnly(true); database.setAutoCommit(false); String owner;
            try (var query = database.createStatement(); var rows = query.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
                assertTrue(rows.next()); owner = rows.getString(1); assertFalse(rows.next());
            }
            var parameters = Map.<String,Object>of("userId", owner, "documentId", owner, "limit", 1L, "lock", false);
            for (var entry : TABLES.entrySet()) {
                assertTrue(configuration.hasStatement(PREFIX + entry.getKey()), "上传依据必须读取实际任务、计划及快照的全部字段");
                var statement = configuration.getMappedStatement(PREFIX + entry.getKey());
                assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
                var bound = statement.getBoundSql(parameters);
                try (var query = database.prepareStatement(bound.getSql())) {
                    new DefaultParameterHandler(statement, parameters, bound).setParameters(query);
                    try (var rows = query.executeQuery()) {
                        assertFalse(rows.next()); var mappings = statement.getResultMaps().get(0).getConstructorResultMappings();
                        var components = statement.getResultMaps().get(0).getType().getRecordComponents();
                        assertEquals(components.length, mappings.size()); assertEquals(components.length, rows.getMetaData().getColumnCount());
                        for (int index = 0; index < components.length; index++) {
                            assertEquals(components[index].getType(), mappings.get(index).getJavaType());
                            assertEquals(mappings.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1));
                        }
                        try (var physical = database.prepareStatement("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION")) {
                            physical.setString(1, entry.getValue()); var columns = new java.util.ArrayList<String>();
                            try (var actual = physical.executeQuery()) { while (actual.next()) columns.add(actual.getString(1)); }
                            assertEquals(columns, mappings.stream().map(org.apache.ibatis.mapping.ResultMapping::getColumn).toList());
                        }
                    }
                }
            }
            for (String table : List.of("tenant", "project", "task_head", "task_revision", "scope_snapshot", "scope_member", "artifact_upload_grant")) {
                try (var query = database.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(rows.next()); assertEquals(0, rows.getLong(1)); assertFalse(rows.next());
                }
            }
        }
    }

    @Test void basisQueriesRequireActualSubjectAndVerifiedEnterpriseTransaction() {
        for (String name : TABLES.keySet()) {
            assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> TenantScope.requireSubjectParameter(PREFIX + name, Map.of())).code());
            assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> TenantScope.requireSqlAccess(PREFIX + name)).code());
        }
    }

    @Test void basisAuthorityRejectsMissingActualAdmissionBeforeReadingBusinessData() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        try (var session = factory.openSession()) {
            var permissions = session.getMapper(com.ruoyi.medical.persistence.mapper.PermissionMapper.class);
            var schema = new PermissionSchema();
            var resource = new ResourceReadAuthority(permissions, new PermissionAuthority(schema), schema);
            var sourceMapper = session.getMapper(com.ruoyi.medical.persistence.mapper.SourceReadMapper.class);
            var authority = new DocumentUploadBasisAuthority(session.getMapper(com.ruoyi.medical.persistence.mapper.DocumentUploadBasisMapper.class),
                session.getMapper(com.ruoyi.medical.persistence.mapper.UploadTransferMapper.class),
                session.getMapper(com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper.class), sourceMapper, resource,
                new SourceReadAuthority(sourceMapper, permissions, resource, new SourceRightsSchema()),
                new ScopeSnapshotSchema(new TaskInputSchema(schema)));
            assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> authority.require(null, null, null, null)).code());
        }
    }

    @Test void sourcePolicyDoesNotInferEnvironmentRegionOrCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentUploadBasisProtocol.Settings(null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new DocumentUploadBasisProtocol.Settings("", "", Integer.MAX_VALUE));
    }

    @Test void fixedUploadBindingCannotBeCreatedWithoutActualVerifiedBasis() throws Exception {
        var methods = java.util.Arrays.stream(DocumentUploadBasisAuthority.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("bind")).toList();
        assertEquals(1, methods.size(), "上传许可必须固定实际核验的当前任务、计划、快照及全部来源依据");
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
            () -> methods.get(0).invoke(null, null, null));
        assertEquals(ErrorCode.ACCESS_DENIED, ((TenantAccessException) failure.getCause()).code());
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
            () -> DocumentUploadBasisAuthority.requireBinding(null, null, null, null)).code());
        assertThrows(IllegalArgumentException.class, () -> new DocumentUploadBasisProtocol.Binding(null, null, null, null, null, null));
    }
}
