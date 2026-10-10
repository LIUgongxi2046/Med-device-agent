package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class ScopeSnapshotStoreTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.ScopeSnapshotWriteMapper.";
    private static final List<String> WRITES = List.of("insertVersion", "insertSnapshot", "insertMember", "insertLineage");

    @Test void providesImmutableSnapshotWritesWithProtectedActualMappings() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        for (String name : WRITES) assertTrue(factory.getConfiguration().hasStatement(PREFIX + name),
                "任务来源快照需要完整版本、成员和引用保存");
        assertNotNull(getClass().getClassLoader().getResource("com/ruoyi/medical/persistence/ScopeSnapshotStore.class"));
        try (var session = factory.openSession()) {
            for (String name : WRITES) {
                var failure = assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                        () -> session.insert(PREFIX + name, Map.of("userId", id())));
                assertEquals(ErrorCode.ACCESS_DENIED, assertInstanceOf(TenantAccessException.class, failure.getCause()).code());
            }
        }
    }

    @Test void savesAndReopensExplicitEmptyScopeInActualDatabaseWithoutExpandingSources() throws Exception {
        var unconfigured = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        assertTrue(unconfigured.getConfiguration().hasStatement(PREFIX + "insertSnapshot"), "来源快照需要实际保存入口");
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "medical_runtime", secrets.required("database_password").textValue());
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(source);
        var jdbc = new JdbcTemplate(source);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        var json = new PermissionSchema(); var inputs = new TaskInputSchema(json); var schema = new ScopeSnapshotSchema(inputs);
        String tenant = id(), project = id(), snapshot = id(), profile = id(), actor = id();
        var scope = new CommonProtocol.SourceScope(CommonProtocol.ScopeMode.SOURCE_VERSIONS, List.of(), List.of(), false, null, null);
        var manifest = new TaskProtocol.ScopeSnapshotManifest("1.0.0", tenant, project, snapshot, profile, scope, "1", "1", List.of());
        String content = json.canonicalObject(manifest), hash = DigestUtils.sha256Hex(content);
        transaction.execute(status -> {
            status.setRollbackOnly();
            jdbc.update("INSERT INTO tenant(tenant_id,auth_epoch,governance_epoch) VALUES(?,1,1)", tenant);
            jdbc.update("INSERT INTO project(tenant_id,project_id,status,revision) VALUES(?,?,'ACTIVE',1)", tenant, project);
            var now = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", java.time.LocalDateTime.class);
            try (var identity = TenantScope.open(new TenantScope.Identity(tenant, actor, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE))) {
                identity.verifyTransaction(new TenantWatermarks(BigInteger.ONE, BigInteger.ONE));
                try (var session = factory.openSession(org.apache.ibatis.session.ExecutorType.SIMPLE)) {
                    var values = new HashMap<String,Object>();
                    values.put("userId", actor); values.put("projectId", project); values.put("snapshotId", snapshot);
                    values.put("manifest", content); values.put("hash", hash); values.put("authEpoch", "1");
                    values.put("governanceEpoch", "1"); values.put("now", now);
                    assertEquals(1, session.insert(PREFIX + "insertVersion", values));
                    assertEquals(1, session.insert(PREFIX + "insertSnapshot", values));
                    var mapper = session.getMapper(com.ruoyi.medical.persistence.mapper.TaskMapper.class);
                    var saved = mapper.snapshot(actor, project, snapshot);
                    assertEquals(1, saved.size()); var actual = saved.get(0);
                    assertEquals(tenant, actual.tenantId()); assertEquals(project, actual.projectId());
                    assertEquals(hash, actual.manifestHash()); assertEquals(hash, actual.registryHash()); assertEquals(now, actual.createdAt());
                    assertTrue(mapper.members(actor, project, snapshot).isEmpty());
                    assertEquals(manifest, org.springframework.util.function.ThrowingSupplier.of(() -> schema.require(actual.manifest(), hash,
                            tenant, project, snapshot, profile, BigInteger.ONE, BigInteger.ONE, scope, List.of())).get());
                    values.put("userId", id());
                    assertEquals(ErrorCode.ACCESS_DENIED, assertInstanceOf(TenantAccessException.class,
                            assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                                    () -> session.insert(PREFIX + "insertVersion", values)).getCause()).code());
                } finally { identity.finishTransaction(); }
            }
            return null;
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tenant WHERE tenant_id=?", Integer.class, tenant));
    }

    private static String id() { return UUID.randomUUID().toString(); }
}
