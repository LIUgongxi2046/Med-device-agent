package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.jgrapht.graph.DirectedPseudograph;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskPlanDraftReadGuardsTest {
    private static final String TENANT="10000000-0000-4000-8000-000000000001";
    private static final String PROJECT="10000000-0000-4000-8000-000000000002";
    private static final String ACTOR="10000000-0000-4000-8000-000000000003";
    private static final String ROOT="10000000-0000-4000-8000-000000000004";
    private static final String PARENT="10000000-0000-4000-8000-000000000005";
    private static final String HASH="a".repeat(64);

    @Test void retentionMapsOnlyRealProjectOrEnterpriseOwnerAndExactReadPurpose() {
        for(String project:new String[]{PROJECT,null}) {
            var permission=retention(project,ROOT,HASH,project,HASH);
            assertEquals(RuntimeRetentionAuthority.scope(TENANT,project),permission.scope());
            assertEquals("retention.read",permission.action());
            assertEquals("RUN_CREATION",permission.purpose());
        }
    }

    @Test void retentionRejectsWrongRevisionHashOriginalOwnerAndOriginalDigest() {
        assertThrows(TenantAccessException.class,()->retention(PROJECT,PARENT,HASH,PROJECT,HASH));
        assertThrows(TenantAccessException.class,()->retention(PROJECT,ROOT,"b".repeat(64),PROJECT,HASH));
        assertThrows(TenantAccessException.class,()->retention(PROJECT,ROOT,HASH,null,HASH));
        assertThrows(TenantAccessException.class,()->retention(PROJECT,ROOT,HASH,PROJECT,"b".repeat(64)));
        assertThrows(TenantAccessException.class,()->retention(PARENT,ROOT,HASH,PARENT,HASH));
    }

    private RunTokenAuthoritySchema.Permission retention(String registeredProject,String pinId,String pinHash,
            String originalProject,String originalHash) {
        var pin=new ObjectMapper().createObjectNode().put("policy_revision",pinId).put("content_sha256",pinHash);
        return TaskPlanDraftControlAccess.retentionPermission(TENANT,PROJECT,pin,
                new ArtifactRegistryProtocol.VersionRow(ROOT,registeredProject,"RUNTIME_RETENTION_POLICY",HASH),
                new TaskPlanDraftControlAccess.RetentionPolicyOwner(originalProject,originalHash));
    }

    @Test void planDraftChecksFrozenHashForEveryAncestorAndPropagatesRejection() throws Exception {
        var mapper=mock(VersionResourceMapper.class);
        var resources=mock(ResourceReadAuthority.class);
        var admission=mock(EnterpriseAccess.Admission.class);
        var proof=mock(TaskPlanDraftControlAccess.Proof.class);
        when(admission.planDraftWorker()).thenReturn(true);
        when(admission.planDraftProof()).thenReturn(proof);
        when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(TENANT,"0","1","1"));
        var root=new ArtifactRegistryProtocol.VersionRow(ROOT,PROJECT,"SOURCE_VERSION",HASH);
        var parent=new ArtifactRegistryProtocol.VersionRow(PARENT,PROJECT,"SOURCE_VERSION",HASH);
        var graph=new DirectedPseudograph<String,SourceReadProtocol.ParentRow>(null,null,false);
        graph.addVertex(ROOT);graph.addVertex(PARENT);
        graph.addEdge(ROOT,PARENT,mock(SourceReadProtocol.ParentRow.class));
        var lineage=new DependencyLineage.VerifiedLineage(ROOT,graph,Map.of(ROOT,root,PARENT,parent));
        when(mapper.ownersAcrossDomains(anyList(),anyList())).thenReturn(List.of(owner(ROOT),owner(PARENT)));
        var authority=new VersionResourceAuthority(mapper,resources,mock(ArtifactRegistry.class));
        var context=new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.HISTORY,null);
        try(var scope=TenantScope.open(new TenantScope.Identity(TENANT,ACTOR,BigInteger.ZERO,BigInteger.ONE,BigInteger.ONE))) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
            try {
                authority.require(admission,lineage,"RUN_CREATION",context,Map.of());
                verify(proof).requireVersion(ROOT,HASH);
                verify(proof).requireVersion(PARENT,HASH);
                doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED))
                        .when(proof).requireVersion(PARENT,HASH);
                assertThrows(TenantAccessException.class,()->authority.require(admission,lineage,"RUN_CREATION",context,Map.of()));
            } finally {
                scope.finishTransaction();
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }
    }

    private VersionResourceProtocol.DomainOwnerRow owner(String version) {
        return new VersionResourceProtocol.DomainOwnerRow("SOURCE_VERSION",
                new VersionResourceProtocol.OwnerRow(TENANT,version,PROJECT,PROJECT,"SOURCE",version,HASH,version,null,null,null));
    }
}
