package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual owner-scope/permission/source readers over fixture rows; no DB/S3 or real rights validation claimed. */
class SimulationInputArtifactScopeAuthorityTest {
    static String id(int n){return SimulationInputArtifactServiceTest.id(n);}
    final SimulationInputArtifactServiceTest f=new SimulationInputArtifactServiceTest();
    final Map<String,List<PermissionProtocol.GrantRow>> grants=new HashMap<>();
    final ResourceReadAuthority authority=new ResourceReadAuthority(f.permissions,new PermissionAuthority(f.json),f.json);
    final SourceReadAuthority sourceAuthority=new SourceReadAuthority(f.sourceRows,f.permissions,authority,mock(SourceRightsSchema.class));
    SimulationInputArtifactScopeAuthorityTest()throws Exception {
        when(f.a.bindingView()).thenReturn(new AuthenticatedIdentityBinding.View("local",id(10),null,null));
        when(f.sourceRows.source(id(7))).thenReturn(List.of(new SourceReadProtocol.SourceRow(id(1),id(27),id(7),null,"ENTERPRISE","INTERNAL",id(8),SimulationInputArtifactServiceTest.RAW,SimulationInputArtifactServiceTest.RAW,null)));
        when(f.sourceRows.rights(id(8))).thenReturn(List.of(mock(SourceReadProtocol.RightsRow.class)));
        when(f.sourceRows.gates(id(7))).thenReturn(List.of(new SourceReadProtocol.GateRow(null,"RECEIVED","APPROVED","ACTIVE",BigInteger.ONE)));
        when(f.permissions.resourceGrants(eq(id(10)),anyString())).thenAnswer(inv->grants.getOrDefault(inv.getArgument(1),List.of()));
        when(f.permissions.resourceOwner("SOURCE",id(27))).thenReturn(List.of(new PermissionProtocol.OwnerRow(id(27),null)));
        when(f.permissions.resourceOwner("CONFIGURATION",id(40))).thenReturn(List.of(new PermissionProtocol.OwnerRow(id(40),id(2))));
        when(f.permissions.resourceOwner("PROJECT",id(2))).thenReturn(List.of(new PermissionProtocol.OwnerRow(id(2),id(2))));
    }
    void grant(String kind,String project,String resource,String action)throws Exception {
        var condition=new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of("SIMULATION_EVALUATION"),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),List.of(),List.of()));
        grants.put(action,List.of(new PermissionProtocol.GrantRow(id(100+grants.size()),id(10),project,kind,resource,action,
            f.json.canonicalObject(condition),"ACTIVE",LocalDateTime.of(2025,1,1,0,0),null,id(4),"1.0.0",f.json.manifestSha256(),BigInteger.ONE)));
    }
    SimulationInputArtifactService consumer()throws Exception {
        return new SimulationInputArtifactService(f.access,f.sources,f.fixtures,f.mapper,f.sourceRows,f.artifacts,authority,f.permissions,f.json,f.lineage,f.references,f.manifests,f.dependencies,
            new RetrievalPolicy("TEST","local",10),new EvaluationDependencyPolicy("TEST","local",512,2048,List.of()));
    }
    @Test void actualOwnerAndGrantReadersAllowEnterpriseSourceProjectConfigurationAndRejectRevocation()throws Exception {
        // Existing registration is prepared with fixture doubles; the read below uses actual authority implementations.
        f.register();when(f.permissions.resourceOwner("ARTIFACT",f.link.artifactId())).thenReturn(List.of(new PermissionProtocol.OwnerRow(f.link.artifactId(),id(2))));
        grant("SOURCE",null,id(27),"source.read");grant("PROJECT",id(2),id(2),"project.read");
        grant("CONFIGURATION",id(2),id(40),"configuration.read");grant("ARTIFACT",id(2),f.link.artifactId(),"artifact.read");
        var service=consumer();
        doAnswer(inv->{sourceAuthority.require(f.a,new SourceReadProtocol.ReadRequirement(id(7),id(8),id(2),null,null,null,
            SourceReadProtocol.Use.REVIEW,"SIMULATION_EVALUATION","TEST","local",null));return null;}).when(f.sources).revalidateForContext(f.a,f.prepared);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try(var scope=TenantScope.open(new TenantScope.Identity(id(1),id(10),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){
            scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
            try {
                service.requireRegisteredInput(f.a,f.prepared,f.ref());
                assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->authority.require(f.a,
                    new PermissionProtocol.Scope(id(1),id(2),"SOURCE",id(27)),"source.read","SIMULATION_EVALUATION")).code());
                var config=grants.remove("configuration.read");
                assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.requireRegisteredInput(f.a,f.prepared,f.ref())).code());
                grants.put("configuration.read",config);grants.remove("project.read");
                assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->service.requireRegisteredInput(f.a,f.prepared,f.ref())).code());
            } finally {scope.finishTransaction();}
        } finally {TransactionSynchronizationManager.setActualTransactionActive(false);}
    }
}
