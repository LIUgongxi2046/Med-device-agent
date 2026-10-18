package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.util.*;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Actual mapper/tenant SQL compilation only; no MySQL connection is attempted. */
class AnnotationDecisionMapperTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.AnnotationDecisionMapper.";
    private static final List<String> READS = List.of("parent", "history", "historicalAnnotation", "authorizationClock", "decisionCount", "replyCount", "decisions", "decision", "operation", "audit", "event", "invalidations");
    private static final List<String> WRITES = List.of("advanceParent", "advanceHead", "advanceIssues", "invalidateReview", "insertDecision", "saveOperation");
    @Test void allQueriesAreTenantSubjectGuardedUncachedAndParameterized() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var all = new ArrayList<>(READS); all.addAll(WRITES);
        for (String name : all) {
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(PREFIX + name));
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(PREFIX + name, Map.of("userId", AnnotationReplyServiceTest.ACTOR)));
        }
        var values = new HashMap<String,Object>(); values.put("userId", AnnotationReplyServiceTest.ACTOR); values.put("fetchLimit", 1001);
        values.put("reason", "private reply ' OR 1=1");
        try (var scope = TenantScope.open(new TenantScope.Identity(AnnotationReplyServiceTest.TENANT, AnnotationReplyServiceTest.ACTOR,
                BigInteger.ONE, BigInteger.ONE, BigInteger.ONE))) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE, BigInteger.ONE));
                for (String name : all) {
                    var statement = configuration.getMappedStatement(PREFIX + name); assertNotEquals(SqlCommandType.DELETE, statement.getSqlCommandType());
                    if (READS.contains(name)) { assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired()); }
                    TenantScope.requireSqlAccess(PREFIX + name); TenantScope.requireSubjectParameter(PREFIX + name, values);
                    assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(PREFIX + name, Map.of("userId", AnnotationReplyServiceTest.TENANT)));
                    String sql = statement.getBoundSql(values).getSql(); assertFalse(sql.contains("private reply"));
                    String scoped = new ProtectedTenantInterceptor().parserSingle(sql, null);
                    assertTrue(scoped.contains(AnnotationReplyServiceTest.TENANT), name);
                    assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(scoped), name);
                }
            } finally { scope.finishTransaction(); TransactionSynchronizationManager.setActualTransactionActive(false); }
        }
        String parent = configuration.getMappedStatement(PREFIX + "parent").getBoundSql(values).getSql();
        assertTrue(parent.contains("m.document_revision=k.document_revision")); assertTrue(parent.contains("h.document_id=m.document_id")); assertTrue(parent.contains("FOR UPDATE"));
        assertEquals(11, configuration.getMappedStatement(PREFIX + "parent").getResultMaps().get(0).getConstructorResultMappings().size());
        assertEquals(17, configuration.getMappedStatement(PREFIX + "decision").getResultMaps().get(0).getConstructorResultMappings().size());
    }
    @Test void decisionsAreImmutableAndBothConcurrencyIdentitiesAreGuarded() throws Exception {
        var config=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var v=Map.<String,Object>of();
        String parent=config.getMappedStatement(PREFIX+"advanceParent").getBoundSql(v).getSql();
        assertTrue(parent.contains("status=?")); assertTrue(parent.contains("revision=?"));
        String head=config.getMappedStatement(PREFIX+"advanceHead").getBoundSql(v).getSql();
        assertTrue(head.contains("status<>'SIGNED'")); assertTrue(head.contains("current_document_revision=?")); assertTrue(head.contains("revision=?"));
        String issues=config.getMappedStatement(PREFIX+"advanceIssues").getBoundSql(v).getSql(); assertTrue(issues.contains("issue_watermark=issue_watermark+1"));
        try(var scope=TenantScope.open(new TenantScope.Identity(AnnotationReplyServiceTest.TENANT,AnnotationReplyServiceTest.ACTOR,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))) {
            assertThrows(TenantAccessException.class,()->new ProtectedTenantInterceptor().parserSingle("UPDATE annotation_decision SET reason='edited' WHERE decision_id='x'",null));
            assertThrows(TenantAccessException.class,()->new ProtectedTenantInterceptor().parserSingle("DELETE FROM annotation_decision WHERE decision_id='x'",null));
        }
    }
    @Test void temporalGuardCoversBothGrantAndSourceValidityBoundaries() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        String sql = configuration.getMappedStatement(PREFIX + "authorizationClock").getBoundSql(Map.of()).getSql();
        assertTrue(sql.contains("permission_grant")); assertTrue(sql.contains("rights_revision"));
        assertEquals(4, sql.split("UTC_TIMESTAMP\\(6\\)", -1).length - 1);
        assertTrue(sql.contains("valid_from")); assertTrue(sql.contains("valid_to"));
    }
}
