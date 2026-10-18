package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.util.*;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Actual MyBatis/JSqlParser/tenant guard compilation. No SQL is executed. */
class AnnotationThreadMapperTest {
    static final String PREFIX="com.ruoyi.medical.persistence.mapper.AnnotationThreadMapper.";
    static final List<String> READS=List.of("authorizationClock","artifactDocumentRevision","annotationCount","annotations","annotation","confirmations","invalidations","operation","audit","event");
    static final List<String> WRITES=List.of("startOperation","updateCommand","completeOperation","saveVersion","saveArtifact","saveBinding","saveLineage","insertAnnotation","advanceHead","advanceIssues","invalidateReview");
    @Test void allStatementsAreSubjectTenantScopedBoundAndUncached()throws Exception{
        var configuration=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var all=new ArrayList<>(READS);all.addAll(WRITES);var values=new HashMap<String,Object>();values.put("userId",AnnotationThreadServiceTest.ACTOR);values.put("fetchLimit",101);values.put("reviewLimit",1001);values.put("status",null);values.put("content","secret text OR 1=1");
        for(String name:all){assertThrows(TenantAccessException.class,()->TenantScope.requireSqlAccess(PREFIX+name));assertThrows(TenantAccessException.class,()->TenantScope.requireSubjectParameter(PREFIX+name,values));}
        try(var scope=TenantScope.open(new TenantScope.Identity(AnnotationThreadServiceTest.TENANT,AnnotationThreadServiceTest.ACTOR,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
            TransactionSynchronizationManager.setActualTransactionActive(true);try{
                scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
                for(String name:all){var statement=configuration.getMappedStatement(PREFIX+name);TenantScope.requireSubjectParameter(PREFIX+name,values);TenantScope.requireSqlAccess(PREFIX+name);
                    assertThrows(TenantAccessException.class,()->TenantScope.requireSubjectParameter(PREFIX+name,Map.of("userId",AnnotationThreadServiceTest.TENANT)));
                    assertNotEquals(SqlCommandType.DELETE,statement.getSqlCommandType());if(READS.contains(name)){assertFalse(statement.isUseCache());assertTrue(statement.isFlushCacheRequired());}
                    String sql=statement.getBoundSql(values).getSql();assertFalse(sql.contains("secret text"));String scoped=new ProtectedTenantInterceptor().parserSingle(sql,null);
                    assertTrue(scoped.contains(AnnotationThreadServiceTest.TENANT),name);assertDoesNotThrow(()->net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(scoped),name);
                }scope.finishTransaction();
            }finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
        }
        assertEquals(19,configuration.getMappedStatement(PREFIX+"annotation").getResultMaps().get(0).getConstructorResultMappings().size());
    }
    @Test void realObjectBindingWholeConfirmationAndHeadIssueCasAreExplicit()throws Exception{
        var configuration=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        java.util.function.Function<String,String> sql=name->configuration.getMappedStatement(PREFIX+name).getBoundSql(Map.of()).getSql().replaceAll("\\s+"," ");
        assertTrue(sql.apply("saveArtifact").contains("'SELECTION'"));assertTrue(sql.apply("saveArtifact").contains("object_version"));assertTrue(sql.apply("saveBinding").contains("'DOCUMENT'"));
        assertTrue(sql.apply("advanceHead").contains("revision=? AND status<>'SIGNED'"));assertFalse(sql.apply("advanceHead").split("WHERE")[0].contains("current_document_revision="));
        assertTrue(sql.apply("advanceIssues").contains("issue_watermark=? AND evidence_watermark=?"));
        assertTrue(sql.apply("confirmations").contains("r.decision='CONFIRM' AND r.target_key='DOCUMENT'"));assertTrue(sql.apply("confirmations").contains("NOT EXISTS"));
        assertTrue(sql.apply("invalidateReview").contains("'ANNOTATION_OPENED'"));assertFalse(sql.apply("invalidateReview").contains("UPDATE"));
        assertTrue(sql.apply("operation").contains("operation_name='RV-04-THREAD-1'"));assertTrue(sql.apply("operation").contains("FOR UPDATE"));
    }
}
