package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real source lineage/manifest closure; current 097 authority and owner database are explicit doubles. */
class TaskProfessionalContentAuthorityTest {
    TaskRunReplayContentAuthorityTest graph;
    TaskNodeInputAuthority.Verified input;
    TaskProfessionalContentAuthority service;
    String artifact,set;
    @BeforeEach void setup()throws Exception {
        graph=new TaskRunReplayContentAuthorityTest();graph.setup();var f=graph.f;artifact=f.id(91);set=f.id(92);input=mock(TaskNodeInputAuthority.Verified.class);
        when(input.tenantId()).thenReturn(f.tenant);when(input.projectId()).thenReturn(f.project);when(input.inputId()).thenReturn(artifact);
        when(input.fixedSources()).thenReturn(List.of(new TaskNodeInputProtocol.SourceSelection(graph.source,graph.parse,graph.index,f.id(75),f.id(76),graph.rights,"TASK_EXECUTION")));
        var object=new InterfaceProtocol.ObjectRef("original/input","original-version","a".repeat(64),"2","application/json");when(input.objectReference()).thenReturn(object);
        when(graph.registry.artifact(artifact)).thenReturn(List.of(new ArtifactRegistryProtocol.ArtifactRow(f.tenant,f.project,artifact,"TASK_NODE_INPUT","{}",object.objectKey(),object.objectVersion(),object.sha256(),BigInteger.TWO,object.mediaType(),set,object.sha256(),"1.0.0","a".repeat(64),"INTERNAL")));
        when(graph.registry.version(artifact)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(artifact,f.project,"ARTIFACT",object.sha256())));
        var roots=List.of(graph.source,graph.parse,graph.index);when(graph.graph.parents(artifact)).thenReturn(roots.stream().map(id->new SourceReadProtocol.ParentRow(artifact,id,"RUNTIME_CONTENT_INPUT")).toList());
        var members=roots.stream().map(id->new ArtifactRegistryProtocol.MemberRow(id,graph.rights)).toList();var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",f.tenant,f.project,set,members);var json=new PermissionSchema();var schemas=new DependencyManifestSchema();String digest=TaskProfessionalObjectGovernance.hash(manifest);
        when(graph.registry.members(set)).thenReturn(members);when(graph.registry.manifest(set)).thenReturn(List.of(new ArtifactRegistryProtocol.ManifestRow(f.tenant,f.project,set,json.canonical(json.tree(manifest)),digest,digest,BigInteger.valueOf(3),"1.0.0",schemas.manifestSha256())));
        service=new TaskProfessionalContentAuthority(graph.registry,graph.owners,schemas,new DependencyLineage(graph.graph,graph.registry),new DependencyLineage.Limits(512,2048));
    }
    @AfterEach void cleanup(){graph.cleanup();}
    @Test void sourceParseAndIndexUseOnlyOriginal097SourceRights()throws Exception {
        var current=service.require(input);assertEquals(List.of(graph.source,graph.parse,graph.index),current.roots());assertEquals(3,current.members().size());assertTrue(current.members().stream().allMatch(m->graph.rights.equals(m.rightsRevision())));current.requireUnchanged();
    }
    @Test void configurationAncestorCannotBorrowSourceRights(){when(graph.owners.ownersAcrossDomains(anyList(),anyList())).thenReturn(List.of());assertThrows(TenantAccessException.class,()->service.require(input));}
    @Test void changed097RightsCannotReuseOriginalManifest(){when(input.fixedSources()).thenReturn(List.of(new TaskNodeInputProtocol.SourceSelection(graph.source,graph.parse,graph.index,graph.f.id(75),graph.f.id(76),graph.f.id(99),"TASK_EXECUTION")));assertThrows(TenantAccessException.class,()->service.require(input));}
    @Test void sourceRevocationInvalidatesExistingPrivateContentProof()throws Exception{var current=service.require(input);doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(input).requireUnchanged();assertThrows(TenantAccessException.class,current::requireUnchanged);}
    @Test void extraOrChangedContentRelationshipCannotReuseProof()throws Exception{var current=service.require(input);when(graph.graph.parents(graph.index)).thenReturn(List.of(new SourceReadProtocol.ParentRow(graph.index,graph.parse,"SUBSTITUTED")));assertThrows(TenantAccessException.class,current::requireUnchanged);}
    @Test void missingOriginalInputDirectRootIsDenied(){when(graph.graph.parents(artifact)).thenReturn(List.of(new SourceReadProtocol.ParentRow(artifact,graph.index,"RUNTIME_CONTENT_INPUT")));assertThrows(TenantAccessException.class,()->service.require(input));}
}
