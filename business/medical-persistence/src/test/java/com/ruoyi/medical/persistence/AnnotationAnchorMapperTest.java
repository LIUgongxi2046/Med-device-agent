package com.ruoyi.medical.persistence;
import java.math.BigInteger;
import java.util.*;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Actual MyBatis/JSqlParser/tenant-guard SQL compilation. No connection or SQL execution. */
class AnnotationAnchorMapperTest {
 static final String PREFIX="com.ruoyi.medical.persistence.mapper.AnnotationAnchorMapper.";
 static final List<String> READS=List.of("locator","documentAnnotations","documentCount","pointer","anchor","history","historyCount","transition","transitions","operation","invalidations","audit","event");
 static final List<String> WRITES=List.of("insertAnchor","insertPointer","advancePointer","advanceAnnotation","insertTransition","startOperation","updateCommand","completeOperation","invalidateReview");
 @Test void everyStatementIsBoundTenantSubjectScopedAndReadsAreUncached()throws Exception{
  var configuration=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();var names=new ArrayList<>(READS);names.addAll(WRITES);var v=new HashMap<String,Object>();v.put("userId",AnnotationAnchorServiceTest.ACTOR);v.put("fetchLimit",101);v.put("reason","private reason OR 1=1");
  for(String name:names){assertThrows(TenantAccessException.class,()->TenantScope.requireSqlAccess(PREFIX+name));assertThrows(TenantAccessException.class,()->TenantScope.requireSubjectParameter(PREFIX+name,v));}
  try(var scope=TenantScope.open(new TenantScope.Identity(AnnotationAnchorServiceTest.TENANT,AnnotationAnchorServiceTest.ACTOR,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
   TransactionSynchronizationManager.setActualTransactionActive(true);scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
   try{for(String name:names){var statement=configuration.getMappedStatement(PREFIX+name);TenantScope.requireSqlAccess(PREFIX+name);TenantScope.requireSubjectParameter(PREFIX+name,v);
    assertThrows(TenantAccessException.class,()->TenantScope.requireSubjectParameter(PREFIX+name,Map.of("userId",AnnotationAnchorServiceTest.TENANT)));
    assertNotEquals(SqlCommandType.DELETE,statement.getSqlCommandType());if(READS.contains(name)){assertFalse(statement.isUseCache());assertTrue(statement.isFlushCacheRequired());}
    String sql=statement.getBoundSql(v).getSql();assertFalse(sql.contains("private reason"));String scoped=new ProtectedTenantInterceptor().parserSingle(sql,null);assertTrue(scoped.contains(AnnotationAnchorServiceTest.TENANT),name);assertDoesNotThrow(()->net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(scoped),name);
   }}finally{scope.finishTransaction();TransactionSynchronizationManager.setActualTransactionActive(false);}
  }
  assertEquals(19,configuration.getMappedStatement(PREFIX+"anchor").getResultMaps().get(0).getConstructorResultMappings().size());
 }
 @Test void immutableOriginIndependentPointerAndDurableOperationCasAreExplicit()throws Exception{
  var configuration=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();java.util.function.Function<String,String>sql=n->configuration.getMappedStatement(PREFIX+n).getBoundSql(Map.of()).getSql().replaceAll("\\s+"," ");
  String update=sql.apply("advanceAnnotation");assertFalse(update.split("WHERE")[0].contains("document_revision"));assertFalse(update.split("WHERE")[0].contains("selection"));assertTrue(update.contains("revision=? AND status=?"));assertTrue(update.contains("annotation_reply"));assertTrue(update.contains("e.anchor_id=? AND e.revision=?"));
  assertTrue(sql.apply("advancePointer").contains("anchor_id=? AND revision=?"));assertTrue(sql.apply("insertAnchor").contains("selection_object_version"));assertTrue(sql.apply("insertAnchor").contains("schema_manifest_sha256"));
  assertTrue(sql.apply("operation").contains("RV-06-REANCHOR-1"));assertTrue(sql.apply("completeOperation").contains("state='PROCESSING' AND revision=?"));assertTrue(sql.apply("invalidateReview").contains("ANNOTATION_REANCHORED"));
  for(String n:List.of("insertAnchor","insertTransition"))assertEquals(SqlCommandType.INSERT,configuration.getMappedStatement(PREFIX+n).getSqlCommandType());
 }
}
