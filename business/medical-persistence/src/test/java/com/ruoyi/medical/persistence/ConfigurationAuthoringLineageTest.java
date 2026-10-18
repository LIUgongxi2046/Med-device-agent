package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses the actual complete graph traversal and production manifest builder, without persistent fixtures. */
class ConfigurationAuthoringLineageTest {
    private final PermissionSchema json=new PermissionSchema();
    private final ConfigurationAuthoringSchema schema=new ConfigurationAuthoringSchema(json);
    private final SourceReadMapper sources=mock(SourceReadMapper.class);
    private final ArtifactRegistryMapper registry=mock(ArtifactRegistryMapper.class);
    ConfigurationAuthoringLineageTest()throws Exception { }
    private ConfigurationAuthoringService service(int maximum) {
        var limits=new EvaluationDependencyPolicy("TEST","LOCAL",maximum,30,List.of());
        return new ConfigurationAuthoringService(null,null,null,null,null,null,null,null,new DependencyLineage(sources,registry),limits,json,schema,null,null,mock(javax.sql.DataSource.class));
    }
    private void version(int n,String kind,int... parents) {
        String id=EvaluationFixture.id(n);
        when(registry.version(id)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(id,null,kind,"a".repeat(64))));
        when(sources.parents(id)).thenReturn(Arrays.stream(parents).mapToObj(p->new SourceReadProtocol.ParentRow(id,EvaluationFixture.id(p),"TEST_REFERENCE")).toList());
    }
    private <T>T scoped(java.util.function.Supplier<T> action)throws Exception {
        try(var scope=TenantScope.open(new TenantScope.Identity(EvaluationFixture.id(90),EvaluationFixture.id(91),java.math.BigInteger.ONE,java.math.BigInteger.ONE,java.math.BigInteger.ONE))){
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            try{scope.verifyTransaction(new TenantWatermarks(java.math.BigInteger.ONE,java.math.BigInteger.ONE));return action.get();}
            finally{scope.finishTransaction();org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
        }
    }
    @Test void includesSharedAndTransitiveDependenciesExactlyOnce()throws Exception {
        version(1,"CAPABILITY_VERSION",4);version(2,"CAPABILITY_VERSION",4);version(3,"MODEL_CONFIG");version(4,"ARTIFACT",5);version(5,"SOURCE_VERSION");
        var result=scoped(()->service(10).dependencyManifest(List.of(EvaluationFixture.ref(1),EvaluationFixture.ref(2),EvaluationFixture.ref(3))));
        assertEquals(5,result.path("dependencies").size());assertEquals(EvaluationFixture.id(5),result.path("dependencies").get(4).path("version_id").textValue());
        schema.manifest(result);
    }
    @Test void rejectsHashMismatchAndAbsentRegisteredVersion()throws Exception {
        version(1,"CAPABILITY_VERSION");version(2,"CAPABILITY_VERSION");version(3,"MODEL_CONFIG");
        scoped(()->{assertThrows(TenantAccessException.class,()->service(10).dependencyManifest(List.of(new PermissionProtocol.Basis(EvaluationFixture.id(1),"b".repeat(64)),EvaluationFixture.ref(2),EvaluationFixture.ref(3))));return null;});
        when(registry.version(EvaluationFixture.id(3))).thenReturn(List.of());
        scoped(()->{assertThrows(TenantAccessException.class,()->service(10).dependencyManifest(List.of(EvaluationFixture.ref(1),EvaluationFixture.ref(2),EvaluationFixture.ref(3))));return null;});
    }
    @Test void countsNewAgentRootAgainstGraphLimitAndRejectsUpstreamCycle()throws Exception {
        version(1,"CAPABILITY_VERSION");version(2,"CAPABILITY_VERSION");version(3,"MODEL_CONFIG");
        scoped(()->{assertThrows(TenantAccessException.class,()->service(3).dependencyManifest(List.of(EvaluationFixture.ref(1),EvaluationFixture.ref(2),EvaluationFixture.ref(3))));return null;});
        version(1,"CAPABILITY_VERSION",4);version(4,"ARTIFACT",5);version(5,"ARTIFACT",4);
        scoped(()->{assertThrows(TenantAccessException.class,()->service(10).dependencyManifest(List.of(EvaluationFixture.ref(1),EvaluationFixture.ref(2),EvaluationFixture.ref(3))));return null;});
    }
}
