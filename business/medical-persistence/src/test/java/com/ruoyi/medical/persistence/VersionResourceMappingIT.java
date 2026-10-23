package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class VersionResourceMappingIT {
    private static DriverManagerDataSource source;
    private static SqlSessionTemplate session;
    private static String tenant, actor, rule;
    private static TenantWatermarks watermarks;
    private static final List<String> artifacts = new ArrayList<>();
    private static final List<String> ruleBases = new ArrayList<>();

    @BeforeAll static void actualRegisteredMaterials() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var json = JsonMapper.builder().build();
        var secrets = json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var assets = json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/acceptance-template-assets.json")));
        var workflow = json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/acceptance-template-workflow.json")));
        assertEquals("REGISTERED", assets.required("state").textValue());
        tenant = assets.required("tenant_id").textValue();
        rule = workflow.required("rule_version").textValue();
        for (var asset : assets.required("assets")) {
            assertEquals("REGISTERED", asset.required("state").textValue());
            artifacts.add(asset.required("artifact_id").textValue());
        }
        for (var basis : workflow.at("/rule_input/content/basis_manifest")) ruleBases.add(basis.required("version_id").textValue());
        assertEquals(4, artifacts.size());
        assertEquals(2, ruleBases.size());
        source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", "medical_runtime", secrets.required("database_password").textValue());
        var db = new JdbcTemplate(source);
        actor = db.queryForObject("SELECT a.user_id FROM platform_local_account a JOIN tenant_member m ON m.user_id=a.user_id AND m.tenant_id=? AND m.status='ACTIVE' WHERE a.platform_role='PLATFORM_ADMIN' AND a.status='ACTIVE'", String.class, tenant);
        watermarks = db.queryForObject("SELECT auth_epoch,governance_epoch FROM tenant WHERE tenant_id=?", (row, index) -> new TenantWatermarks(new BigInteger(row.getString(1)), new BigInteger(row.getString(2))), tenant);
        session = new SqlSessionTemplate(new MedicalPersistenceConfiguration().medicalSqlSessionFactory(source));
    }

    private List<VersionResourceProtocol.DomainOwnerRow> read(List<String> ids) {
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        return Objects.requireNonNull(transaction.execute(status -> {
            status.setRollbackOnly();
            try (var scope = TenantScope.open(new TenantScope.Identity(tenant, actor, BigInteger.ONE, watermarks.authEpoch(), watermarks.governanceEpoch()))) {
                scope.verifyTransaction(session.getMapper(TenantWatermarkMapper.class).lockCurrentTenant());
                try {
                    return session.getMapper(VersionResourceMapper.class).ownersAcrossDomains(ids,
                        Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());
                } finally { scope.finishTransaction(); }
            }
        }));
    }

    @Test void everyActualArtifactKeepsItsOwnDomainIdentity() {
        var rows = read(artifacts);
        assertEquals(artifacts.size(), rows.size());
        assertEquals(artifacts.stream().sorted().toList(), rows.stream().map(row -> row.owner().versionId()).sorted().toList());
        assertTrue(rows.stream().allMatch(row -> row.domain().equals("ARTIFACT") && row.owner().tenantId().equals(tenant)));
    }

    @Test void approvedRuleAndBothActualBasisArtifactsRemainReadableTogether() {
        var ids = new ArrayList<>(ruleBases); ids.add(rule);
        var rows = read(ids);
        assertEquals(ids.size(), rows.size());
        assertEquals(ids.stream().sorted().toList(), rows.stream().map(row -> row.owner().versionId()).sorted().toList());
        assertEquals(1, rows.stream().filter(row -> row.domain().equals("RULE_VERSION")).count());
        assertEquals(2, rows.stream().filter(row -> row.domain().equals("ARTIFACT")).count());
    }
}
