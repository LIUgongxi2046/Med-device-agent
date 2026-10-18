package com.ruoyi.medical.persistence;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
class SimulationEvaluationPreparationMapperTest {
 static final String P="com.ruoyi.medical.persistence.mapper.SimulationEvaluationPreparationMapper.";
 static final List<String> READS=List.of("registration","lockSource","operation","operationById","audit","event");
 static final List<String> WRITES=List.of("insertRegistration","insertArtifact","insertBinding","saveOperation");
 @Test void statementsAreTenantScopedUncachedAndSubjectBound()throws Exception {
  var c=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
  var all=new ArrayList<>(READS);all.addAll(WRITES);var values=new HashMap<String,Object>();values.put("userId",EvaluationFixture.id(2));
  for(String name:all){assertTrue(c.hasStatement(P+name));assertThrows(TenantAccessException.class,()->TenantScope.requireSqlAccess(P+name));}
  for(String name:READS){var s=c.getMappedStatement(P+name);assertFalse(s.isUseCache());assertTrue(s.isFlushCacheRequired());}
  try(var scope=TenantScope.open(new TenantScope.Identity(EvaluationFixture.id(1),EvaluationFixture.id(2),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
   TransactionSynchronizationManager.setActualTransactionActive(true);
   try {scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));for(String name:all){TenantScope.requireSqlAccess(P+name);TenantScope.requireSubjectParameter(P+name,values);
    String scoped=new ProtectedTenantInterceptor().parserSingle(c.getMappedStatement(P+name).getBoundSql(values).getSql(),null);assertTrue(scoped.contains(EvaluationFixture.id(1)),name);
   }}finally{scope.finishTransaction();TransactionSynchronizationManager.setActualTransactionActive(false);}
  }
 }
 @Test void commonLookupLedgerAndHistoricalCreatorAreExplicitAndNoUpdatesExist()throws Exception {
  var c=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();var v=new HashMap<String,Object>();
  String sql=c.getMappedStatement(P+"saveOperation").getBoundSql(v).getSql();assertTrue(sql.contains("operation_record"));assertTrue(sql.contains("'SIM-EVALUATION-PREPARE'"));assertTrue(sql.contains("target_id"));
  String binding=c.getMappedStatement(P+"insertBinding").getBoundSql(v).getSql();assertTrue(binding.contains("'SOURCE'"));assertTrue(binding.contains("NULL,"));
  assertEquals(17,c.getMappedStatement(P+"registration").getResultMaps().get(0).getConstructorResultMappings().size());
  for(String name:WRITES)assertEquals(org.apache.ibatis.mapping.SqlCommandType.INSERT,c.getMappedStatement(P+name).getSqlCommandType());
 }
}
