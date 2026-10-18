package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.util.*;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Actual mapper/tenant SQL compilation only; no MySQL connection is attempted. */
class AnnotationReplyMapperTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.AnnotationReplyMapper.";
    private static final List<String> READS = List.of("parent", "originalDocumentRevision", "authorizationClock", "replyCount", "replies", "reply", "operation", "audit", "event");
    private static final List<String> WRITES = List.of("advanceParent", "insertReply", "saveOperation");
    @Test void allQueriesAreTenantSubjectGuardedUncachedAndParameterized() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var all = new ArrayList<>(READS); all.addAll(WRITES);
        for (String name : all) {
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(PREFIX + name));
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(PREFIX + name, Map.of("userId", AnnotationReplyServiceTest.ACTOR)));
        }
        var values = new HashMap<String,Object>(); values.put("userId", AnnotationReplyServiceTest.ACTOR); values.put("fetchLimit", 1001);
        values.put("content", "private reply ' OR 1=1");
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
                scope.finishTransaction();
            } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        }
        String parent = configuration.getMappedStatement(PREFIX + "parent").getBoundSql(values).getSql();
        assertTrue(parent.contains("m.document_revision=k.document_revision")); assertTrue(parent.contains("h.document_id=m.document_id")); assertTrue(parent.contains("FOR UPDATE"));
        assertEquals(11, configuration.getMappedStatement(PREFIX + "parent").getResultMaps().get(0).getConstructorResultMappings().size());
        assertEquals(9, configuration.getMappedStatement(PREFIX + "reply").getResultMaps().get(0).getConstructorResultMappings().size());
    }
    @Test void replyCasCannotModifyStateAndReadsCannotSilentlyTruncate() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var values = Map.<String,Object>of("fetchLimit", 1001);
        String advance = configuration.getMappedStatement(PREFIX + "advanceParent").getBoundSql(values).getSql().replaceAll("\\s+", " ");
        assertTrue(advance.contains("SET revision=revision+1,updated_at=?"));
        assertTrue(advance.contains("document_revision=?")); assertTrue(advance.contains("AND revision=? AND status=?"));
        assertFalse(advance.split("WHERE")[0].contains("status=")); assertFalse(advance.split("WHERE")[0].contains("selection="));
        String replies = configuration.getMappedStatement(PREFIX + "replies").getBoundSql(values).getSql();
        assertTrue(replies.contains("ORDER BY created_at,reply_id LIMIT ?")); assertFalse(replies.contains("OFFSET"));
        String count = configuration.getMappedStatement(PREFIX + "replyCount").getBoundSql(values).getSql();
        assertTrue(count.contains("COUNT(*)")); assertTrue(count.contains("project_id=? AND annotation_id=?"));
        String operation = configuration.getMappedStatement(PREFIX + "operation").getBoundSql(values).getSql();
        assertTrue(operation.contains("subject_id=? AND operation_name='RV-05-REPLY-1' AND idempotency_key=?")); assertTrue(operation.contains("FOR UPDATE"));
    }
    @Test void temporalGuardCoversBothGrantAndSourceValidityBoundaries() throws Exception {
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        String sql = configuration.getMappedStatement(PREFIX + "authorizationClock").getBoundSql(Map.of()).getSql();
        assertTrue(sql.contains("permission_grant")); assertTrue(sql.contains("rights_revision"));
        assertEquals(4, sql.split("UTC_TIMESTAMP\\(6\\)", -1).length - 1);
        assertTrue(sql.contains("valid_from")); assertTrue(sql.contains("valid_to"));
    }
}
