package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.HashMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationArtifactReadMapperTest {
    @Test void readOnlyUncachedSettlementQueryUsesActualMembershipAndTenantProtectsAllJoinedTables()throws Exception {
        var config=new Configuration();
        try(var in=new InputStreamReader(getClass().getResourceAsStream("/medical-mappers/SimulationFixtureMapper.xml"),java.nio.charset.StandardCharsets.UTF_8)){
            new XMLMapperBuilder(in,config,"SimulationFixtureMapper.xml",config.getSqlFragments()).parse();
        }
        var statement=config.getMappedStatement(SimulationFixtureMapper.class.getName()+".settledArtifact");
        assertFalse(statement.isUseCache());assertTrue(statement.isFlushCacheRequired());
        var parameters=new HashMap<String,Object>();parameters.put("artifactId",EvaluationFixture.id(1));parameters.put("schemaHash","a".repeat(64));parameters.put("userId",EvaluationFixture.id(2));
        String sql=statement.getBoundSql(parameters).getSql();
        for(String table:java.util.List.of("simulation_quota_settlement","invocation","simulation_invocation_provenance","simulation_execution_authority","simulation_invocation_fact","simulation_result_persistence"))assertTrue(sql.contains(table));
        assertTrue(sql.contains("s.result_artifact_id=? OR s.provenance_artifact_id=?"));assertTrue(sql.contains("w.status='COMPLETED'"));
        assertFalse(sql.contains("FOR UPDATE"));assertFalse(sql.contains("${"));
        assertEquals(SimulationArtifactReadGuard.Settled.class.getRecordComponents().length,statement.getResultMaps().get(0).getConstructorResultMappings().size());
        try(var scope=TenantScope.open(new TenantScope.Identity(EvaluationFixture.id(1),EvaluationFixture.id(2),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
            String secured=new ProtectedTenantInterceptor().parserSingle(sql,null);
            for(String alias:java.util.List.of("s","i","p","a","rs","f","w"))assertTrue(secured.contains(alias+".tenant_id = '"+EvaluationFixture.id(1)+"'"),secured);
            var retention=config.getMappedStatement(SimulationFixtureMapper.class.getName()+".artifactRetention");
            assertFalse(retention.isUseCache());assertTrue(retention.isFlushCacheRequired());
            String retentionSql=retention.getBoundSql(parameters).getSql();assertTrue(retentionSql.contains("r.status='ACTIVE'"));assertTrue(retentionSql.contains("r.retain_until>UTC_TIMESTAMP(6)"));
            assertFalse(retentionSql.contains("a.status='ACTIVE'"));assertFalse(retentionSql.contains("expires_at"));
            new ProtectedTenantInterceptor().parserSingle(retentionSql,null);
        }
    }
    @Test void productionRegistryDependencyIsLazyAndLegacyConstructorCannotAccidentallyEnableNewReads()throws Exception {
        var constructors=ArtifactRegistry.class.getConstructors();
        assertTrue(java.util.Arrays.stream(constructors).anyMatch(c->c.getParameterCount()==4));
        var production=java.util.Arrays.stream(constructors).filter(c->c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)).findFirst().orElseThrow();
        assertEquals(org.springframework.beans.factory.ObjectProvider.class,production.getParameterTypes()[4]);
    }
}
