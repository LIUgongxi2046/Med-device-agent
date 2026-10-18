package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.util.*;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationMapperTest {
    private static final String PREFIX="com.ruoyi.medical.persistence.mapper.EvaluationMapper.";
    private static final List<String> READS=List.of("definition","candidate","configurationStates","simulationPrompt","scores","parents","currentOperation","audit","event");
    private static final List<String> WRITES=List.of("insertVersion","insertBatch","insertDefinition","insertLineage","insertScore","insertObservation","advanceBatch","insertResult","saveOperation");
    private Configuration configuration()throws Exception{return new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();}
    private Map<String,Object> values(){var v=new HashMap<String,Object>();v.put("userId",EvaluationFixture.id(2));v.put("versionIds",List.of(EvaluationFixture.id(10),EvaluationFixture.id(11)));return v;}
    @Test void readsBypassCachesAndEveryStatementRequiresVerifiedTenantTransactionAndSubject()throws Exception{
        var c=configuration();var all=new ArrayList<>(READS);all.addAll(WRITES);
        for(String name:all)assertThrows(TenantAccessException.class,()->TenantScope.requireSqlAccess(PREFIX+name));
        for(String name:READS){var s=c.getMappedStatement(PREFIX+name);assertFalse(s.isUseCache());assertTrue(s.isFlushCacheRequired());}
        try(var scope=TenantScope.open(new TenantScope.Identity(EvaluationFixture.id(1),EvaluationFixture.id(2),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
            TransactionSynchronizationManager.setActualTransactionActive(true);try{
                scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
                for(String name:all){assertDoesNotThrow(()->TenantScope.requireSqlAccess(PREFIX+name));assertDoesNotThrow(()->TenantScope.requireSubjectParameter(PREFIX+name,values()));
                    assertThrows(TenantAccessException.class,()->TenantScope.requireSubjectParameter(PREFIX+name,Map.of("userId",EvaluationFixture.id(3))));}scope.finishTransaction();
            }finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
        }
    }
    @Test void tenantInterceptorScopesAllNewTablesAndNoStatementCanChangeHistory()throws Exception{
        var c=configuration();var all=new ArrayList<>(READS);all.addAll(WRITES);
        try(var scope=TenantScope.open(new TenantScope.Identity(EvaluationFixture.id(1),EvaluationFixture.id(2),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
            var interceptor=new ProtectedTenantInterceptor();for(String name:all){String sql=c.getMappedStatement(PREFIX+name).getBoundSql(values()).getSql();
                assertTrue(interceptor.parserSingle(sql,null).contains(EvaluationFixture.id(1)),name);
                assertNotEquals(SqlCommandType.DELETE,c.getMappedStatement(PREFIX+name).getSqlCommandType());
            }
        }
        String advance=c.getMappedStatement(PREFIX+"advanceBatch").getBoundSql(values()).getSql();assertTrue(advance.contains("revision=?"));assertTrue(advance.contains("content_sha256=?"));assertTrue(advance.contains("status IN ('QUEUED','RUNNING')"));
        assertFalse(advance.split("WHERE")[0].contains("content_sha256="));assertFalse(advance.contains("definition_json"));
    }
    @Test void originalScoresArePreservedAndMissingSidecarsCannotDisappear()throws Exception{
        var c=configuration();String scores=c.getMappedStatement(PREFIX+"scores").getBoundSql(values()).getSql();
        assertTrue(scores.contains("FROM evaluation_score s LEFT JOIN evaluation_score_observation"));assertTrue(scores.contains("LIMIT 50001"));
        String candidate=c.getMappedStatement(PREFIX+"candidate").getBoundSql(values()).getSql();assertFalse(candidate.contains("release_record"));assertFalse(candidate.contains("'PUBLISHED'"));
        String result=c.getMappedStatement(PREFIX+"insertResult").getBoundSql(values()).getSql();assertTrue(result.contains("production_eligible"));assertTrue(result.contains("#{") == false);assertTrue(result.contains(",0,"));
    }
    @Test void simulationCandidateReadsActualKindsHeadStatesAndPromptWithoutPublicationOrWrites()throws Exception {
        var c=configuration();
        String states=c.getMappedStatement(PREFIX+"configurationStates").getBoundSql(values()).getSql();
        assertTrue(states.contains("FROM capability_version"));assertTrue(states.contains("FROM model_config_revision"));
        assertTrue(states.contains("FROM agent_version"));assertTrue(states.contains("h.status AS head_status"));
        assertFalse(states.contains("release_record"));assertFalse(states.contains("'PUBLISHED'"));
        assertEquals(SqlCommandType.SELECT,c.getMappedStatement(PREFIX+"configurationStates").getSqlCommandType());
        String prompt=c.getMappedStatement(PREFIX+"simulationPrompt").getBoundSql(values()).getSql();
        assertTrue(prompt.contains("prompt_revision"));assertTrue(prompt.contains("execution_mode='SIMULATION'"));assertTrue(prompt.contains("FOR UPDATE"));
    }

}
