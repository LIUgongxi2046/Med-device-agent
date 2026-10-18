package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Real mapper binding and tenant rewriting; no live database or storage claim. */
class GruRecordExportMapperTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.GruRecordExportMapper.";
    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String ACTOR = "00000000-0000-4000-8000-000000000002";
    private TenantScope scope() { return TenantScope.open(new TenantScope.Identity(TENANT, ACTOR,
        BigInteger.ONE, BigInteger.ONE, BigInteger.ONE)); }

    @Test void everyActualExportStatementEnforcesTenantAndActor() throws Exception {
        var config = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var names = config.getMappedStatementNames().stream().filter(n -> n.startsWith(PREFIX)).collect(Collectors.toSet());
        var expected = Set.of("intent", "intentByOperation", "registration", "registrationByOperation", "insertIntent", "insertRegistration",
            "operation", "operationById", "operationRevision", "startOperation", "markAttempted", "completeOperation", "insertVersion",
            "insertArtifact", "insertLineage", "audit", "event", "attempt", "insertAttempt", "insertBinding", "artifactOwner", "intentByKey", "intentById");
        assertEquals(expected, names.stream().map(n -> n.substring(PREFIX.length())).collect(Collectors.toSet()));
        try (var ignored = scope()) {
            for (String name : names) {
                TenantScope.requireSubjectParameter(name, Map.of("userId", ACTOR));
                assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(name, Map.of()), name);
                assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(name,
                    Map.of("userId", "00000000-0000-4000-8000-000000000003")), name);
                String sql = config.getMappedStatement(name).getBoundSql(new HashMap<>()).getSql();
                String rewritten = new ProtectedTenantInterceptor().parserSingle(sql, null);
                assertTrue(rewritten.contains(TENANT), name);
                assertNotNull(CCJSqlParserUtil.parse(rewritten), name);
            }
        }
    }

    @Test void exportAndTechnicalRecoveryEvidenceCannotBeUpdatedOrDeleted() {
        try (var ignored = scope()) {
            for (String table : List.of("gru_record_export_intent", "gru_record_export_attempt", "gru_record_export",
                    "document_technical_check_intent", "document_technical_check_attempt")) {
                for (String sql : List.of("UPDATE " + table + " SET created_by='x'", "DELETE FROM " + table)) {
                    assertThrows(TenantAccessException.class, () -> new ProtectedTenantInterceptor().parserSingle(sql, null), sql);
                }
            }
        }
    }
}
