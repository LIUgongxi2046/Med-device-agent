package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual complete graph traversal; only the already authenticated receipt/current-rights boundary is doubled. */
class TaskFieldTargetContentAuthorityTest {
    final String tenant=id(1),project=id(2),task=id(3),mapping=id(4),source=id(5),parse=id(6),rights=id(7),receipt=id(8),entry=id(9),hash="a".repeat(64);
    final PermissionSchema json=new PermissionSchema();
    final TaskFieldSourceReceiptStore receipts=mock(TaskFieldSourceReceiptStore.class);
    final ArtifactRegistryMapper registry=mock(ArtifactRegistryMapper.class);
    final SourceReadMapper graph=mock(SourceReadMapper.class);
    final VersionResourceMapper owners=mock(VersionResourceMapper.class);
    final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
    final List<VersionResourceProtocol.DomainOwnerRow> ownerRows=new ArrayList<>();
    ObjectNode manifest,read;TaskFieldTargetContentAuthority authority;TenantScope scope;
    TaskFieldTargetContentAuthorityTest() throws Exception { }
    @BeforeEach void setup() throws Exception {
        when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(tenant,"1","1","1"));
        manifest=json.object();manifest.putObject("target").putObject("identity").put("task_id",task);
        manifest.putArray("entry_bindings").addObject().put("entry_key",entry).put("source_read_receipt_id",receipt);
        manifest.putArray("source_lineage").addObject().put("version_id",parse).put("content_sha256",hash);
        read=json.object().put("task_id",task).put("entry_key",entry).put("source_version",source).put("rights_revision",rights);
        var stored=mock(TaskFieldSourceReceiptStore.Stored.class);when(stored.manifest()).thenAnswer(inv->read.deepCopy());
        when(receipts.requireHistorical(admission,receipt,mapping,"task.field_target_input.read")).thenReturn(stored);
        for(String version:List.of(source,parse)) {
            String kind=version.equals(source)?"SOURCE_VERSION":"PARSE_VERSION";
            when(registry.version(version)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(version,project,kind,hash)));
            ownerRows.add(new VersionResourceProtocol.DomainOwnerRow(kind,new VersionResourceProtocol.OwnerRow(tenant,version,project,project,"SOURCE",id(20),hash,source,null,null,null)));
        }
        when(owners.ownersAcrossDomains(anyList(),anyList())).thenAnswer(inv->List.copyOf(ownerRows));
        when(graph.parents(parse)).thenReturn(List.of(new SourceReadProtocol.ParentRow(parse,source,"PARSED_FROM")));
        authority=new TaskFieldTargetContentAuthority(receipts,new DependencyLineage(graph,registry),()->new DependencyLineage.Limits(10,10),owners);
        scope=TenantScope.open(new TenantScope.Identity(tenant,id(11),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE));
        TransactionSynchronizationManager.setActualTransactionActive(true);scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
    }
    @AfterEach void cleanup(){if(scope!=null){scope.finishTransaction();scope.close();}TransactionSynchronizationManager.setActualTransactionActive(false);}
    TaskFieldTargetContentAuthority.Content current() throws Exception{return authority.require(admission,manifest,mapping,"task.field_target_input.read");}
    @Test void completeAncestryUsesOnlyActualReceiptRights() throws Exception {
        var result=current();assertEquals(List.of(parse),result.roots());assertEquals(2,result.members().size());
        assertTrue(result.members().stream().allMatch(m->rights.equals(m.rightsRevision())));
        assertEquals(Set.of(source,parse),result.versions().keySet());
    }
    @Test void revokedCurrentReceiptStopsBeforeLineage() throws Exception {
        when(receipts.requireHistorical(admission,receipt,mapping,"task.field_target_input.read")).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,this::current);verifyNoInteractions(registry);
    }
    @Test void unrelatedGovernanceAncestorCannotBorrowSourceRights() {
        var old=ownerRows.get(0);var o=old.owner();ownerRows.set(0,new VersionResourceProtocol.DomainOwnerRow(old.domain(),
                new VersionResourceProtocol.OwnerRow(tenant,o.versionId(),project,project,"PROJECT",project,hash,null,null,null,null)));
        assertThrows(TenantAccessException.class,this::current);
    }
    @Test void sameOwnerWrongPinnedHashIsRejected() {
        when(registry.version(parse)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(parse,project,"PARSE_VERSION","b".repeat(64))));
        assertThrows(TenantAccessException.class,this::current);
    }
    @Test void duplicateReceiptOrCrossTaskEntryCannotBeReused() {
        ((com.fasterxml.jackson.databind.node.ArrayNode)manifest.required("entry_bindings")).add(manifest.at("/entry_bindings/0").deepCopy());
        assertThrows(TenantAccessException.class,this::current);
        ((com.fasterxml.jackson.databind.node.ArrayNode)manifest.required("entry_bindings")).remove(1);read.put("task_id",id(90));
        assertThrows(TenantAccessException.class,this::current);
    }
    @Test void sessionOwnerCannotEnterFormalContentClosure() {
        var old=ownerRows.get(0);var o=old.owner();ownerRows.set(0,new VersionResourceProtocol.DomainOwnerRow(old.domain(),
                new VersionResourceProtocol.OwnerRow(tenant,o.versionId(),project,project,"SOURCE",id(20),hash,source,id(81),id(82),"ACTIVE")));
        assertThrows(TenantAccessException.class,this::current);
    }
    @Test void absentOperationalBoundsFailBeforeAnyRead() {
        authority=new TaskFieldTargetContentAuthority(receipts,new DependencyLineage(graph,registry),()->{throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);},owners);
        assertThrows(TenantAccessException.class,this::current);verifyNoInteractions(receipts,registry);
    }
    @Test void actualEmptyInventoryHasEmptyContentNotInventedRoots() throws Exception {
        manifest.putArray("entry_bindings");manifest.putArray("source_lineage");var result=current();
        assertTrue(result.members().isEmpty());assertTrue(result.roots().isEmpty());verifyNoInteractions(receipts,registry,owners);
    }
    static String id(int n){return TaskFieldTargetSchemaTest.id(n);}
}
