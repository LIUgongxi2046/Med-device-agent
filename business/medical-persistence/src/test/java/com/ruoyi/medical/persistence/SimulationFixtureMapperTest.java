package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper;
import java.io.Reader;
import java.util.HashMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationFixtureMapperTest {
    @Test void genuineSourceLookupAndImmutableInsertHaveNoCallerObjectReferences() throws Exception {
        var config=new Configuration();
        try(Reader reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/medical-mappers/SimulationFixtureMapper.xml"),java.nio.charset.StandardCharsets.UTF_8)) {
            new XMLMapperBuilder(reader,config,"SimulationFixtureMapper.xml",config.getSqlFragments()).parse();
        }
        String namespace=SimulationFixtureMapper.class.getName()+".";
        var values=new HashMap<String,Object>(); values.put("sourceVersion","test");
        String original=config.getMappedStatement(namespace+"original").getBoundSql(values).getSql();
        assertTrue(original.contains("FROM source_version")); assertFalse(original.contains("${"));
        String context=config.getMappedStatement(namespace+"context").getBoundSql(values).getSql();
        assertTrue(context.contains("JOIN version_registry")); assertTrue(context.contains("c.token_count IS NULL"));
        assertTrue(context.contains("c.source_manifest")); assertTrue(context.contains("c.prompt_revision"));
        assertFalse(config.hasStatement(namespace+"updateRegistration")); assertFalse(config.hasStatement(namespace+"deleteRegistration"));
    }
    @Test void allContextAndFixtureWritesAcceptServerInjectedTenant()throws Exception {
        var config=new Configuration();
        try(Reader reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/medical-mappers/SimulationFixtureMapper.xml"),java.nio.charset.StandardCharsets.UTF_8)) {
            new XMLMapperBuilder(reader,config,"SimulationFixtureMapper.xml",config.getSqlFragments()).parse();
        }
        String tenant="00000000-0000-0000-0000-000000000001",actor="00000000-0000-0000-0000-000000000002";
        try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor,java.math.BigInteger.ONE,java.math.BigInteger.ONE,java.math.BigInteger.ONE))){
            var interceptor=new ProtectedTenantInterceptor();
            for(String statement:java.util.List.of("insertRegistration","insertContextVersion","insertContextAccessSet","insertContextDependencyManifest",
                    "insertContextDependencyMember","insertContextLineage","insertSimulationContext","insertSimulationContextGraph")){
                String sql=config.getMappedStatement(SimulationFixtureMapper.class.getName()+"."+statement).getBoundSql(new HashMap<>()).getSql();
                String protectedSql=interceptor.parserSingle(sql,null);
                assertTrue(protectedSql.contains("tenant_id"),statement);assertTrue(protectedSql.contains(tenant),statement);
            }
        }
    }
    @Test void missingPrivateReadProofCannotBeSubstitutedByCallerJson() throws Exception {
        var sources=new SimulationInvocationSources(org.mockito.Mockito.mock(EnterpriseAccess.class),
            org.mockito.Mockito.mock(SourceReadAuthority.class),org.mockito.Mockito.mock(com.ruoyi.medical.persistence.mapper.SourceReadMapper.class),
            org.mockito.Mockito.mock(SourceRightsSchema.class),org.mockito.Mockito.mock(SourceUploadTransport.class),
            org.mockito.Mockito.mock(SimulationFixtureMapper.class),org.mockito.Mockito.mock(com.ruoyi.medical.persistence.mapper.PermissionMapper.class),
            org.mockito.Mockito.mock(ResourceReadAuthority.class),org.mockito.Mockito.mock(ModelConfigurationService.class),new PermissionSchema(),
            org.mockito.Mockito.mock(DependencyAccessAuthority.class),new RetrievalPolicy("test-deployment","test-region",10));
        assertThrows(TenantAccessException.class,()->sources.require(null,null,null,null));
    }
}
