package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.util.*;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationAuthoringMapperTest {
    private static final String PREFIX="com.ruoyi.medical.persistence.mapper.ConfigurationAuthoringMapper.";
    private static final List<String> READS=List.of("stored","parents","currentOperation","audit","event");
    private static final List<String> WRITES=List.of("insertHead","insertVersion","insertCapability","insertAgent","insertLineage","initializeHead","saveOperation","advanceGovernance");
    private Configuration configuration()throws Exception{return new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();}
    private Map<String,Object> values(boolean agent){var v=new HashMap<String,Object>();v.put("userId",EvaluationFixture.id(2));v.put("agent",agent);return v;}
    @Test void everyStatementRequiresVerifiedTenantAndExactSubjectAndReadsAvoidCaches()throws Exception {
        var c=configuration();var all=new ArrayList<>(READS);all.addAll(WRITES);
        for(String name:all)assertThrows(TenantAccessException.class,()->TenantScope.requireSqlAccess(PREFIX+name));
        for(String name:READS){var statement=c.getMappedStatement(PREFIX+name);assertFalse(statement.isUseCache());assertTrue(statement.isFlushCacheRequired());}
        try(var scope=TenantScope.open(new TenantScope.Identity(EvaluationFixture.id(1),EvaluationFixture.id(2),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
                for(String name:all){assertDoesNotThrow(()->TenantScope.requireSqlAccess(PREFIX+name));assertDoesNotThrow(()->TenantScope.requireSubjectParameter(PREFIX+name,values(false)));
                    assertThrows(TenantAccessException.class,()->TenantScope.requireSubjectParameter(PREFIX+name,Map.of("userId",EvaluationFixture.id(3))));}
            }finally{scope.finishTransaction();TransactionSynchronizationManager.setActualTransactionActive(false);}
        }
    }
    @Test void bothConcreteKindsAreTenantScopedAndNoVersionUpdateOrPublishExists()throws Exception {
        var c=configuration();var all=new ArrayList<>(READS);all.addAll(WRITES);
        try(var scope=TenantScope.open(new TenantScope.Identity(EvaluationFixture.id(1),EvaluationFixture.id(2),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
            var interceptor=new ProtectedTenantInterceptor();
            for(boolean agent:List.of(false,true))for(String name:all){var statement=c.getMappedStatement(PREFIX+name);String sql=statement.getBoundSql(values(agent)).getSql();
                assertTrue(interceptor.parserSingle(sql,null).contains(EvaluationFixture.id(1)),name);
                assertNotEquals(SqlCommandType.DELETE,statement.getSqlCommandType());assertFalse(sql.contains("PUBLISHED"));assertFalse(sql.contains("release_record"));
                if(statement.getSqlCommandType()==SqlCommandType.UPDATE)assertTrue(Set.of("initializeHead","advanceGovernance").contains(name));
            }
        }
        for(boolean agent:List.of(false,true)){
            String sql=c.getMappedStatement(PREFIX+"stored").getBoundSql(values(agent)).getSql();assertTrue(sql.contains("r.owner_project_id <=> v.project_id"));
            assertTrue(sql.contains("v.revision=1"));assertTrue(sql.contains("FOR UPDATE"));
        }
        assertFalse(c.getMappedStatement(PREFIX+"parents").getBoundSql(values(false)).getSql().contains("relation_kind="));
    }
}
