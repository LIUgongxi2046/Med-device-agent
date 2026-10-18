package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

class SimulationRuleVersionMapperTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.SimulationRuleVersionMapper.";
    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String SUBJECT = "00000000-0000-4000-8000-000000000002";
    private static final List<String> READS = List.of("definition", "approvedWorkflow", "parents", "baseParents", "currentOperation", "audit", "event", "auditForOperation", "eventForOperation",
            "approvalOperation", "approval", "approvalAudit", "approvalEvent", "approvalAuditForOperation", "approvalEventForOperation", "approvalBinding", "approvedEvidence");
    private static final List<String> WRITES = List.of("insertVersion", "insertRule", "insertDefinition", "insertLineage", "insertBaseLineage", "saveOperation", "saveApprovalOperation",
            "advanceRule", "advanceDefinition", "advanceGovernance", "saveApproval", "insertBinding", "insertApprovalBinding");
    private Configuration configuration() throws Exception {
        return new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
    }
    private Map<String,Object> values() {
        var result = new HashMap<String,Object>();
        for (String name : List.of("userId", "ruleId", "key", "requestId", "operationId", "eventId", "auditId", "decisionId", "baseId", "authorityGrant", "upstreamId")) result.put(name, SUBJECT);
        result.put("item", new PermissionProtocol.Basis(SUBJECT, "a".repeat(64))); return result;
    }
    @Test void everyFixedReadBypassesCachesAndEveryStatementRequiresTransactionAndSubject() throws Exception {
        var configuration = configuration(); var statements = new java.util.ArrayList<>(READS); statements.addAll(WRITES);
        for (String name : statements) {
            assertTrue(configuration.hasStatement(PREFIX + name));
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(PREFIX + name));
        }
        for (String name : READS) {
            var statement = configuration.getMappedStatement(PREFIX + name);
            assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired()); assertEquals(SqlCommandType.SELECT, statement.getSqlCommandType());
        }
        try (var scope = TenantScope.open(new TenantScope.Identity(TENANT, SUBJECT, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE))) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE, BigInteger.ONE));
                for (String name : statements) {
                    assertDoesNotThrow(() -> TenantScope.requireSqlAccess(PREFIX + name));
                    assertDoesNotThrow(() -> TenantScope.requireSubjectParameter(PREFIX + name, values()));
                    assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(PREFIX + name, Map.of("userId", TENANT)));
                }
                scope.finishTransaction();
            } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        }
    }
    @Test void tenantInterceptorScopesEveryReadAndWriteIncludingNewTables() throws Exception {
        var configuration = configuration(); var statements = new java.util.ArrayList<>(READS); statements.addAll(WRITES);
        try (var scope = TenantScope.open(new TenantScope.Identity(TENANT, SUBJECT, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE))) {
            var interceptor = new ProtectedTenantInterceptor();
            for (String name : statements) {
                String sql = configuration.getMappedStatement(PREFIX + name).getBoundSql(values()).getSql();
                String scoped = interceptor.parserSingle(sql, null);
                assertTrue(scoped.contains(TENANT), name); assertTrue(scoped.contains("tenant_id"), name);
            }
        }
    }
    @Test void updatesOnlyAdvanceStateAndCasRevisionNeverReplaceRuleContent() throws Exception {
        var configuration = configuration();
        String rule = configuration.getMappedStatement(PREFIX + "advanceRule").getBoundSql(values()).getSql().replaceAll("\\s+", " ");
        assertTrue(rule.contains("SET status=?")); assertTrue(rule.contains("AND status=? AND content_sha256=?"));
        String definition = configuration.getMappedStatement(PREFIX + "advanceDefinition").getBoundSql(values()).getSql().replaceAll("\\s+", " ");
        assertTrue(definition.contains("SET revision=?")); assertTrue(definition.contains("AND revision=? AND content_sha256=?"));
        for (String name : WRITES) {
            var statement = configuration.getMappedStatement(PREFIX + name);
            assertNotEquals(SqlCommandType.DELETE, statement.getSqlCommandType());
            if (statement.getSqlCommandType() == SqlCommandType.UPDATE)
                assertFalse(statement.getBoundSql(values()).getSql().split("WHERE")[0].contains("content_json"));
        }
    }
    @Test void ordinaryConsumerAndWriterShareTheSameOriginalRuleTableAndLineageKind() throws Exception {
        var configuration = configuration();
        String insert = configuration.getMappedStatement(PREFIX + "insertRule").getBoundSql(values()).getSql();
        assertTrue(insert.contains("INSERT INTO rule_version")); assertTrue(insert.contains("'DRAFT'")); assertFalse(insert.contains("'APPROVED'"));
        String lineage = configuration.getMappedStatement(PREFIX + "insertLineage").getBoundSql(values()).getSql();
        String consumer = configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.TemplateMapper.ruleParents").getBoundSql(values()).getSql();
        assertTrue(lineage.contains("'RULE_DEFINITION'")); assertTrue(consumer.contains("'RULE_DEFINITION'"));
        String read = configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.TemplateMapper.ruleDefinition").getBoundSql(values()).getSql();
        assertTrue(read.contains("FROM rule_version")); assertTrue(read.contains("version_registry"));
        assertEquals(23, configuration.getMappedStatement(PREFIX + "definition").getResultMaps().get(0).getConstructorResultMappings().size());
    }
    @Test void decisionLedgerIsBoundToOperationActorStateBaselineAndOriginalReceipt() throws Exception {
        var configuration = configuration();
        String save = configuration.getMappedStatement(PREFIX + "saveApproval").getBoundSql(values()).getSql();
        for (String column : List.of("operation_id", "rule_sha256", "from_status", "to_status", "expected_revision", "resulting_revision", "request_json",
                "baseline_sha256", "dependency_authorizations", "receipt_sha256", "authority_grant", "auth_epoch", "governance_epoch", "created_by")) assertTrue(save.contains(column), column);
        String operation = configuration.getMappedStatement(PREFIX + "approvalOperation").getBoundSql(values()).getSql();
        assertTrue(operation.contains("operation_name='SIM-RULE-APPROVE'")); assertTrue(operation.contains("subject_id=?")); assertTrue(operation.contains("idempotency_key=?"));
        String creation = configuration.getMappedStatement(PREFIX + "currentOperation").getBoundSql(values()).getSql();
        assertTrue(creation.contains("operation_name='SIM-RULE-CREATE'"));
    }
    @Test void fixedBindingsAreInsertOnlyAndApprovalRequiresMatchingSidecar() throws Exception {
        var c=configuration();
        String insert=c.getMappedStatement(PREFIX+"insertBinding").getBoundSql(values()).getSql();
        assertTrue(insert.contains("'SIMULATION_EVALUATION'"));assertTrue(insert.contains("binding_sha256"));
        String approved=c.getMappedStatement(PREFIX+"approvedWorkflow").getBoundSql(values()).getSql();
        assertTrue(approved.contains("simulation_rule_approval_binding"));assertTrue(approved.contains("simulation_rule_binding"));
        assertTrue(approved.contains("$.binding_sha256"));assertTrue(approved.contains("simulation.rule.approval_decided"));
        assertFalse(approved.contains("d.rule_version IS NULL"));
        assertTrue(c.getMappedStatement(PREFIX+"definition").getBoundSql(values()).getSql().contains("JOIN simulation_rule_binding"));
    }

}
