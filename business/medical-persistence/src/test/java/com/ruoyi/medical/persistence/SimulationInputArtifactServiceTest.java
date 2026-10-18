package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import org.jgrapht.graph.DirectedPseudograph;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationInputArtifactServiceTest {
    static String id(int n){return String.format("00000000-0000-0000-0000-%012d",n);}
    static final String RAW="a".repeat(64),CANONICAL="b".repeat(64),KEY="register-input-key-001";
    final PermissionSchema json=new PermissionSchema();
    final EnterpriseAccess access=mock(EnterpriseAccess.class);
    final EnterpriseAccess.Admission a=mock(EnterpriseAccess.Admission.class);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final SimulationInvocationSources sources=mock(SimulationInvocationSources.class);
    final SimulationInvocationSources.PreparedFixture prepared=mock(SimulationInvocationSources.PreparedFixture.class);
    final SimulationFixtureMapper fixtures=mock(SimulationFixtureMapper.class);
    final SimulationInputArtifactMapper mapper=mock(SimulationInputArtifactMapper.class);
    final SourceReadMapper sourceRows=mock(SourceReadMapper.class);
    final ArtifactRegistryMapper artifacts=mock(ArtifactRegistryMapper.class);
    final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
    final PermissionMapper permissions=mock(PermissionMapper.class);
    final DependencyLineage lineage=mock(DependencyLineage.class);
    final EvaluationReferences references=mock(EvaluationReferences.class);
    final DependencyManifestSchema manifests=new DependencyManifestSchema();
    final DependencyAccessAuthority dependencies=mock(DependencyAccessAuthority.class);
    final Map<String,ArtifactRegistryProtocol.VersionRow> versions=new HashMap<>();
    final List<ArtifactRegistryProtocol.MemberRow> members=new ArrayList<>();
    final SimulationInvocationSources.Registration registration=new SimulationInvocationSources.Registration(id(1),id(2),id(3),id(4),id(5),id(6),id(7),id(8),CANONICAL,CANONICAL,RAW,RAW,id(9),"1.0.0",RAW,id(10),LocalDateTime.of(2026,1,1,0,0),"INTERNAL");
    final SimulationInvocationSources.Original original=new SimulationInvocationSources.Original(id(1),id(7),RAW,"tenants/"+id(1)+"/input","object-version-1",BigInteger.valueOf(128),"application/json");
    final SimulationInputArtifactService service;
    final com.fasterxml.jackson.databind.node.ObjectNode request=json.object().put("schema_version","1.0.0").put("project_id",id(2)).put("model_config_revision",id(4));
    ArtifactRegistryProtocol.ArtifactRow artifact;
    ArtifactRegistryProtocol.BindingRow binding;
    ArtifactRegistryProtocol.ManifestRow manifest;
    SimulationInputArtifactService.Link link;
    SimulationInputArtifactService.Operation operation;
    SimulationInputArtifactServiceTest()throws Exception {
        request.set("request_parameters",json.object());
        when(subject.sessionId()).thenReturn(id(11));when(a.actorId()).thenReturn(id(10));
        when(a.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));
        when(access.execute(eq(subject),any())).thenAnswer(inv->{Function<EnterpriseAccess.Admission,Object> fn=inv.getArgument(1);return fn.apply(a);});
        when(fixtures.registration(anyMap())).thenReturn(List.of(registration));when(mapper.lockFixture(anyMap())).thenReturn(List.of(id(9)));
        when(sources.prepareForContext(eq(subject),any())).thenReturn(prepared);when(prepared.registration()).thenReturn(registration);when(prepared.configuration()).thenReturn(json.object().put("project_id",id(2)).put("model_config_revision",id(4)).put("config_id",id(40)));when(prepared.inputOriginal()).thenReturn(original);
        for(int n:List.of(5,7)){
            var source=new SourceReadProtocol.SourceRow(id(1),id(n+20),id(n),id(2),"PROJECT","INTERNAL",id(n+1),RAW,RAW,id(2));
            when(sourceRows.source(id(n))).thenReturn(List.of(source));
            var graph=new DirectedPseudograph<String,SourceReadProtocol.ParentRow>(null,null,false);graph.addVertex(id(n));
            when(lineage.inspect(eq(id(n)),any())).thenReturn(new DependencyLineage.VerifiedLineage(id(n),graph,Map.of(id(n),new ArtifactRegistryProtocol.VersionRow(id(n),id(2),"SOURCE",RAW))));
        }
        when(permissions.databaseTime()).thenReturn(LocalDateTime.of(2026,1,1,0,0));
        when(fixtures.insertContextVersion(anyMap())).thenAnswer(inv->{Map<String,Object> v=inv.getArgument(0);versions.put((String)v.get("versionId"),new ArtifactRegistryProtocol.VersionRow((String)v.get("versionId"),id(2),(String)v.get("kind"),(String)v.get("hash")));return 1;});
        when(fixtures.insertContextAccessSet(anyMap())).thenReturn(1);
        when(fixtures.insertContextDependencyManifest(anyMap())).thenAnswer(inv->{Map<String,Object> v=inv.getArgument(0);manifest=new ArtifactRegistryProtocol.ManifestRow(id(1),id(2),(String)v.get("accessId"),(String)v.get("dependencyJson"),(String)v.get("hash"),(String)v.get("hash"),BigInteger.valueOf((Integer)v.get("memberCount")),"1.0.0",(String)v.get("dependencySchema"));return 1;});
        when(fixtures.insertContextDependencyMember(anyMap())).thenAnswer(inv->{Map<String,Object> v=inv.getArgument(0);members.add((ArtifactRegistryProtocol.MemberRow)v.get("member"));return 1;});
        when(fixtures.insertContextLineage(anyMap())).thenReturn(1);
        when(mapper.insertArtifact(anyMap())).thenAnswer(inv->{Map<String,Object> v=inv.getArgument(0);var ref=(InterfaceProtocol.ObjectRef)v.get("reference");artifact=new ArtifactRegistryProtocol.ArtifactRow(id(1),id(2),(String)v.get("artifactId"),"SIMULATION_EXACT_INPUT",(String)v.get("storedObject"),ref.objectKey(),ref.objectVersion(),ref.sha256(),new BigInteger(ref.sizeBytes()),ref.mediaType(),(String)v.get("accessId"),ref.sha256(),"1.0.0",(String)v.get("schemaHash"),(String)v.get("classification"));return 1;});
        when(mapper.insertBinding(anyMap())).thenAnswer(inv->{Map<String,Object> v=inv.getArgument(0);binding=new ArtifactRegistryProtocol.BindingRow(id(1),id(2),(String)v.get("artifactId"),"CONFIGURATION",(String)v.get("configId"),(String)v.get("schemaRef"),(String)v.get("schemaHash"),(String)v.get("accessId"),(String)v.get("dependencyHash"));return 1;});
        when(mapper.insertLink(anyMap())).thenAnswer(inv->{Map<String,Object> v=inv.getArgument(0);link=new SimulationInputArtifactService.Link(id(1),id(2),id(3),id(4),id(7),id(8),(String)v.get("artifactId"));return 1;});
        when(mapper.link(anyMap())).thenAnswer(inv->link==null?List.of():List.of(link));
        when(mapper.operation(anyMap())).thenAnswer(inv->operation==null?List.of():List.of(operation));
        when(mapper.insertOperation(anyMap())).thenAnswer(inv->{Map<String,Object> v=inv.getArgument(0);operation=new SimulationInputArtifactService.Operation(id(1),id(10),(String)v.get("key"),(String)v.get("requestHash"),(String)v.get("receipt"),(String)v.get("operationSchema"),"1.0.0",id(10),(String)v.get("canonicalRequest"),(String)v.get("bodyHash"));return 1;});
        when(artifacts.artifact(anyString())).thenAnswer(inv->List.of(artifact));when(artifacts.binding(anyString())).thenAnswer(inv->List.of(binding));
        when(artifacts.version(anyString())).thenAnswer(inv->List.of(versions.get(inv.getArgument(0))));
        when(artifacts.manifest(anyString())).thenAnswer(inv->List.of(manifest));when(artifacts.members(anyString())).thenAnswer(inv->List.copyOf(members));
        when(permissions.saveAudit(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(1);
        when(permissions.saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(1);
        service=new SimulationInputArtifactService(access,sources,fixtures,mapper,sourceRows,artifacts,resources,permissions,json,lineage,references,manifests,dependencies,new RetrievalPolicy("TEST","local",10),new EvaluationDependencyPolicy("TEST","local",512,2048,List.of()));
    }
    com.fasterxml.jackson.databind.JsonNode register()throws Exception{return service.register(subject,"1",id(3),KEY,request);}
    PermissionProtocol.Basis ref(){return new PermissionProtocol.Basis(link.artifactId(),RAW);}
    @Test void registersOriginalObjectRawDigestAndCompleteSourcesExactlyOnce()throws Exception {
        var receipt=register();assertEquals(RAW,receipt.path("input_ref").path("content_sha256").asText());assertNotEquals(id(7),link.artifactId());
        assertEquals(original.reference(),StoredObjectReferences.decode(artifact.storedObject(),artifact.reference()));
        assertEquals(Set.of(id(5),id(7)),new HashSet<>(members.stream().map(ArtifactRegistryProtocol.MemberRow::upstreamVersionId).toList()));
        assertEquals(receipt,register());verify(mapper,times(1)).insertArtifact(anyMap());verify(mapper,times(1)).insertOperation(anyMap());
        verify(permissions,times(1)).saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
        verify(sources,atLeast(4)).revalidateForContext(a,prepared);
    }
    @Test void consumerUsesReadGrantsAndNeverProducerGrantOrWrites()throws Exception {
        register();clearInvocations(mapper,resources,permissions);service.requireRegisteredInput(a,prepared,ref());
        verify(resources).require(eq(a),eq(new PermissionProtocol.Scope(id(1),id(2),"SOURCE",id(27))),eq("source.read"),eq("SIMULATION_EVALUATION"));
        verify(resources).require(eq(a),eq(new PermissionProtocol.Scope(id(1),id(2),"ARTIFACT",link.artifactId())),eq("artifact.read"),eq("SIMULATION_EVALUATION"));
        verify(resources,never()).require(any(),any(),eq("artifact.register"),anyString());verify(mapper,never()).insertArtifact(anyMap());verifyNoInteractions(permissions);
    }
    @Test void sourceIdCanonicalDigestWrongProjectAndWrongObjectVersionFail()throws Exception {
        register();assertThrows(TenantAccessException.class,()->service.requireRegisteredInput(a,prepared,new PermissionProtocol.Basis(id(7),RAW)));
        assertThrows(TenantAccessException.class,()->service.requireRegisteredInput(a,prepared,new PermissionProtocol.Basis(link.artifactId(),CANONICAL)));
        var old=link;link=new SimulationInputArtifactService.Link(id(1),id(99),id(3),id(4),id(7),id(8),old.artifactId());
        assertThrows(TenantAccessException.class,()->service.requireRegisteredInput(a,prepared,ref()));link=old;
        when(prepared.inputOriginal()).thenReturn(new SimulationInvocationSources.Original(id(1),id(7),RAW,original.objectKey(),"different-version",original.sizeBytes(),original.mediaType()));
        assertThrows(TenantAccessException.class,()->service.requireRegisteredInput(a,prepared,ref()));
    }
    @Test void changedContextBodyOrSessionCannotReplayOriginalKey()throws Exception {
        register();request.putObject("request_parameters").put("changed",true);
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,assertThrows(TenantAccessException.class,this::register).code());
        request.set("request_parameters",json.object());when(subject.sessionId()).thenReturn(id(99));assertThrows(TenantAccessException.class,this::register);
        assertEquals(ErrorCode.CONTEXT_CHANGED,assertThrows(TenantAccessException.class,()->service.register(subject,"2",id(3),KEY,request)).code());
        verify(mapper,times(1)).insertArtifact(anyMap());
    }
    @Test void revokedSourcesAndReadGrantsBlockReplayAndConsumption()throws Exception {
        register();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(sources).revalidateForContext(a,prepared);
        assertThrows(TenantAccessException.class,this::register);assertThrows(TenantAccessException.class,()->service.requireRegisteredInput(a,prepared,ref()));
        doNothing().when(sources).revalidateForContext(a,prepared);
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(resources).require(eq(a),any(),eq("artifact.read"),anyString());
        assertThrows(TenantAccessException.class,()->service.requireRegisteredInput(a,prepared,ref()));
    }
    @Test void currentRightsAndTenantMismatchFailClosed()throws Exception {
        register();when(sourceRows.source(id(7))).thenReturn(List.of(new SourceReadProtocol.SourceRow(id(1),id(27),id(7),id(2),"PROJECT","INTERNAL",id(88),RAW,RAW,id(2))));
        assertThrows(TenantAccessException.class,this::register);
        when(a.context()).thenReturn(new BusinessSessionProtocol.Context(id(99),"1","1","1"));assertThrows(TenantAccessException.class,this::register);
    }
    @Test void missingLineageOrAuditFailureCannotReturnSuccessfulReceipt()throws Exception {
        doThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH)).when(lineage).require(anyString(),anyList(),any());
        assertThrows(TenantAccessException.class,this::register);verify(mapper,never()).insertOperation(anyMap());
        // Mocks do not emulate database rollback. Real transaction rollback remains an integration test.
    }
    @Test void changedArtifactSchemaAndForgedSavedReceiptAreRejected()throws Exception {
        register();var saved=binding;
        binding=new ArtifactRegistryProtocol.BindingRow(id(1),id(2),link.artifactId(),"SOURCE",id(27),"urn:unknown",RAW,saved.accessSetId(),saved.dependencyManifestSha256());
        assertThrows(TenantAccessException.class,()->service.requireRegisteredInput(a,prepared,ref()));binding=saved;
        var altered=(com.fasterxml.jackson.databind.node.ObjectNode)new com.fasterxml.jackson.databind.ObjectMapper().readTree(operation.receipt());
        altered.withObject("/input_ref").put("content_sha256",CANONICAL);
        operation=new SimulationInputArtifactService.Operation(operation.tenantId(),operation.actorId(),operation.key(),operation.requestHash(),json.canonical(altered),operation.schemaHash(),operation.schemaVersion(),operation.createdBy(),operation.canonicalRequest(),operation.bodyHash());
        assertThrows(TenantAccessException.class,this::register);
    }
    @Test void recoveryMetadataRequiresCurrentActorAndFixedSchemaVersion()throws Exception {
        register();var saved=operation;
        operation=new SimulationInputArtifactService.Operation(saved.tenantId(),saved.actorId(),saved.key(),saved.requestHash(),saved.receipt(),saved.schemaHash(),"2.0.0",saved.createdBy(),saved.canonicalRequest(),saved.bodyHash());
        assertThrows(TenantAccessException.class,this::register);
        operation=new SimulationInputArtifactService.Operation(saved.tenantId(),saved.actorId(),saved.key(),saved.requestHash(),saved.receipt(),saved.schemaHash(),saved.schemaVersion(),id(99),saved.canonicalRequest(),saved.bodyHash());
        assertThrows(TenantAccessException.class,this::register);
    }
    @Test void failedAuditPropagatesInsideTransaction()throws Exception {
        when(permissions.saveAudit(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(0);
        assertThrows(TenantAccessException.class,this::register);verify(permissions,never()).saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
}
