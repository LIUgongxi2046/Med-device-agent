package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.util.*;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Compiles real mapper SQL and tenant guards without opening a database connection. */
class AnnotationAnchorReconciliationMapperTest {
    static final String PREFIX = "com.ruoyi.medical.persistence.mapper.AnnotationAnchorReconciliationMapper.";
    static final List<String> READS = List.of("operation", "audit", "event", "invalidations");
    static final List<String> WRITES = List.of("startOperation", "completeOperation", "invalidateReview");

    @Test void allStatementsRequireTenantSubjectAndCompileWithBoundParameters() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var names = new ArrayList<>(READS); names.addAll(WRITES);
        var values = new HashMap<String,Object>(); values.put("userId", AnnotationAnchorServiceTest.ACTOR);
        values.put("reason", "private reason OR 1=1"); values.put("response", "private response");
        for (String name : names) {
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(PREFIX + name));
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(PREFIX + name, values));
        }
        try (var scope = TenantScope.open(new TenantScope.Identity(AnnotationAnchorServiceTest.TENANT,
                AnnotationAnchorServiceTest.ACTOR, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE))) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE, BigInteger.ONE));
            try {
                for (String name : names) {
                    var statement = configuration.getMappedStatement(PREFIX + name);
                    TenantScope.requireSqlAccess(PREFIX + name); TenantScope.requireSubjectParameter(PREFIX + name, values);
                    assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(PREFIX + name,
                        Map.of("userId", AnnotationAnchorServiceTest.TENANT)));
                    assertNotEquals(SqlCommandType.DELETE, statement.getSqlCommandType());
                    if (READS.contains(name)) { assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired()); }
                    String sql = statement.getBoundSql(values).getSql();
                    assertFalse(sql.contains("private reason")); assertFalse(sql.contains("private response"));
                    String scoped = new ProtectedTenantInterceptor().parserSingle(sql, null);
                    assertTrue(scoped.contains(AnnotationAnchorServiceTest.TENANT), name);
                    assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(scoped), name);
                }
            } finally { scope.finishTransaction(); TransactionSynchronizationManager.setActualTransactionActive(false); }
        }
    }

    @Test void independentIdempotencyNamespaceAndImmutableOriginPointerAreExplicit() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        java.util.function.Function<String,String> sql = name -> configuration.getMappedStatement(PREFIX + name)
            .getBoundSql(Map.of()).getSql().replaceAll("\\s+", " ");
        for (String name : List.of("operation", "startOperation", "completeOperation")) {
            assertTrue(sql.apply(name).contains("RV-06-ANCHOR-RECONCILE-1"), name);
            assertFalse(sql.apply(name).contains("RV-06-REANCHOR-1"), name);
        }
        String complete = sql.apply("completeOperation");
        assertTrue(complete.contains("state='PROCESSING' AND revision=?"));
        for (String clause : List.of("subject_id=?", "operation_id=?", "target_id=?", "request_sha256=?"))
            assertTrue(complete.contains(clause), clause);
        assertTrue(sql.apply("audit").contains("annotation.anchor.reconciled"));
        assertTrue(sql.apply("event").contains("annotation.anchor.reconciled"));
        assertTrue(sql.apply("invalidateReview").contains("ANNOTATION_ANCHOR_RECONCILED"));
        assertTrue(sql.apply("invalidations").contains("ANNOTATION_ANCHOR_RECONCILED"));
        for (String name : WRITES) {
            String text = sql.apply(name).toLowerCase(Locale.ROOT);
            assertFalse(text.matches("(?s).*\\b(update|into) annotation(?:_anchor|_effective_anchor)?\\b.*"), name);
        }
        String advance = configuration.getMappedStatement(AnnotationAnchorMapperTest.PREFIX + "advanceAnnotation")
            .getBoundSql(Map.of()).getSql().replaceAll("\\s+", " ");
        String updates = advance.split("WHERE")[0];
        for (String immutable : List.of("document_revision", "selection", "anchor_id", "content", "actor_id"))
            assertFalse(updates.contains(immutable), immutable);
        assertTrue(advance.contains("revision=? AND status=?"));
        assertTrue(advance.contains("e.anchor_id=? AND e.revision=?"));
        assertEquals(SqlCommandType.INSERT, configuration.getMappedStatement(PREFIX + "invalidateReview").getSqlCommandType());
    }
}
