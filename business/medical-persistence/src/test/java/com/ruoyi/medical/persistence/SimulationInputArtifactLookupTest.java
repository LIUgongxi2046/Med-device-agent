package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationInputArtifactLookupTest {
    static String id(int n){return SimulationInputArtifactServiceTest.id(n);}
    final SimulationInputArtifactServiceTest f=new SimulationInputArtifactServiceTest();
    SimulationInputArtifactLookupTest()throws Exception { }
    JsonNode input(){var node=f.json.object().put("schema_version","1.0.0").put("original_key",SimulationInputArtifactServiceTest.KEY);node.set("original_request",f.request.deepCopy());return node;}
    JsonNode lookup()throws Exception{return f.service.lookup(f.subject,"2",id(3),input());}
    void current(){when(f.subject.sessionId()).thenReturn(id(90));when(f.a.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"2","2","2"));}
    void readOnly()throws java.io.IOException {
        verify(f.mapper,never()).insertArtifact(anyMap());verify(f.mapper,never()).insertBinding(anyMap());verify(f.mapper,never()).insertLink(anyMap());verify(f.mapper,never()).insertOperation(anyMap());
        verify(f.mapper,never()).lockFixture(anyMap());verifyNoInteractions(f.permissions);
        verify(f.resources,never()).require(any(),any(),eq("artifact.register"),anyString());
    }
    @Test void currentContextAndSessionRecoverExactReceiptWithoutWriteOrProducerGrant()throws Exception {
        var receipt=f.register();current();clearInvocations(f.mapper,f.resources,f.permissions);
        var result=lookup();assertEquals("FOUND",result.path("status").asText());assertEquals(receipt,result.path("receipt"));readOnly();
    }
    @Test void absentCurrentActorKeyReturnsNotFoundWithoutReadingObject()throws Exception {
        current();assertEquals("NOT_FOUND",lookup().path("status").asText());verifyNoInteractions(f.sources);readOnly();
    }
    @Test void wrongActorOrTenantCannotBorrowSavedOperation()throws Exception {
        f.register();current();when(f.a.actorId()).thenReturn(id(99));assertThrows(TenantAccessException.class,this::lookup);
        when(f.a.actorId()).thenReturn(id(10));when(f.a.context()).thenReturn(new BusinessSessionProtocol.Context(id(99),"2","2","2"));assertThrows(TenantAccessException.class,this::lookup);
    }
    @Test void alteredOriginalParametersFailBeforeObjectRead()throws Exception {
        f.register();current();clearInvocations(f.sources);f.request.withObject("/request_parameters").put("changed",true);
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,assertThrows(TenantAccessException.class,this::lookup).code());verifyNoInteractions(f.sources);
    }
    @Test void storageJsonFormattingIsNotAssumedCanonicalButStoredDigestMustMatch()throws Exception {
        f.register();current();var saved=f.operation;
        String pretty=new com.fasterxml.jackson.databind.ObjectMapper().readTree(saved.canonicalRequest()).toPrettyString();
        f.operation=new SimulationInputArtifactService.Operation(saved.tenantId(),saved.actorId(),saved.key(),saved.requestHash(),saved.receipt(),saved.schemaHash(),saved.schemaVersion(),saved.createdBy(),pretty,saved.bodyHash());
        assertEquals("FOUND",lookup().path("status").asText());
        f.operation=new SimulationInputArtifactService.Operation(saved.tenantId(),saved.actorId(),saved.key(),saved.requestHash(),saved.receipt(),saved.schemaHash(),saved.schemaVersion(),saved.createdBy(),pretty,"c".repeat(64));
        assertThrows(TenantAccessException.class,this::lookup);
    }
    @Test void sourceRevocationAndChangedObjectStillBlockLookup()throws Exception {
        f.register();current();clearInvocations(f.mapper,f.resources,f.permissions);
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.sources).revalidateForContext(f.a,f.prepared);assertThrows(TenantAccessException.class,this::lookup);readOnly();
        doNothing().when(f.sources).revalidateForContext(f.a,f.prepared);
        when(f.prepared.inputOriginal()).thenReturn(new SimulationInvocationSources.Original(id(1),id(7),SimulationInputArtifactServiceTest.RAW,f.original.objectKey(),"changed",f.original.sizeBytes(),f.original.mediaType()));
        assertThrows(TenantAccessException.class,this::lookup);readOnly();
    }
    @Test void enterpriseSourceSupportsProjectArtifactThroughRealConfigurationOwner()throws Exception {
        when(f.sourceRows.source(id(7))).thenReturn(List.of(new SourceReadProtocol.SourceRow(id(1),id(27),id(7),null,"ENTERPRISE","INTERNAL",id(8),SimulationInputArtifactServiceTest.RAW,SimulationInputArtifactServiceTest.RAW,null)));
        f.register();assertEquals(id(2),f.artifact.projectId());assertEquals("CONFIGURATION",f.binding.resourceKind());assertEquals(id(40),f.binding.resourceId());
        verify(f.resources,atLeastOnce()).require(eq(f.a),eq(new PermissionProtocol.Scope(id(1),null,"SOURCE",id(27))),eq("artifact.register"),eq("SIMULATION_EVALUATION"));
        current();clearInvocations(f.resources);assertEquals("FOUND",lookup().path("status").asText());
        verify(f.resources).require(eq(f.a),eq(new PermissionProtocol.Scope(id(1),null,"SOURCE",id(27))),eq("source.read"),eq("SIMULATION_EVALUATION"));
        verify(f.resources).require(eq(f.a),eq(new PermissionProtocol.Scope(id(1),id(2),"CONFIGURATION",id(40))),eq("configuration.read"),eq("SIMULATION_EVALUATION"));
        verify(f.resources,never()).require(eq(f.a),eq(new PermissionProtocol.Scope(id(1),id(2),"SOURCE",id(27))),anyString(),anyString());
    }
    @Test void legacySourceBindingOnlyWorksForSameScopeAndOtherKindsNeverWork()throws Exception {
        f.register();var old=f.binding;f.binding=new ArtifactRegistryProtocol.BindingRow(id(1),id(2),old.artifactId(),"SOURCE",id(27),old.schemaRef(),old.schemaDocumentSha256(),old.accessSetId(),old.dependencyManifestSha256());
        f.service.requireRegisteredInput(f.a,f.prepared,f.ref());
        when(f.sourceRows.source(id(7))).thenReturn(List.of(new SourceReadProtocol.SourceRow(id(1),id(27),id(7),null,"ENTERPRISE","INTERNAL",id(8),SimulationInputArtifactServiceTest.RAW,SimulationInputArtifactServiceTest.RAW,null)));
        assertThrows(TenantAccessException.class,()->f.service.requireRegisteredInput(f.a,f.prepared,f.ref()));
        f.binding=new ArtifactRegistryProtocol.BindingRow(id(1),id(2),old.artifactId(),"DOCUMENT",id(27),old.schemaRef(),old.schemaDocumentSha256(),old.accessSetId(),old.dependencyManifestSha256());
        assertThrows(TenantAccessException.class,()->f.service.requireRegisteredInput(f.a,f.prepared,f.ref()));
    }
    @Test void lookupHttpUsesCurrentCsrfContextAndNoNewIdempotencyHeader()throws Exception {
        var identities=mock(com.ruoyi.medical.identity.ApplicationSessionIdentity.class);var service=mock(SimulationInputArtifactService.class);
        var request=mock(jakarta.servlet.http.HttpServletRequest.class);var response=mock(jakarta.servlet.http.HttpServletResponse.class);
        for(var header:Map.of("X-Schema-Version","1.0.0","X-Context-Revision","2","X-CSRF-TOKEN","csrf").entrySet())
            when(request.getHeaders(header.getKey())).thenAnswer(inv->Collections.enumeration(List.of(header.getValue())));
        when(identities.requireCurrent(request,null)).thenReturn(f.subject);
        var stream=mock(jakarta.servlet.ServletInputStream.class);when(request.getInputStream()).thenReturn(stream);
        when(stream.readNBytes(131073)).thenReturn(f.json.canonical(input()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(service.lookup(eq(f.subject),eq("2"),eq(id(3)),any())).thenReturn(new SimulationInputArtifactLookupSchema().result(null));
        var controller=new SimulationInputArtifactController(identities,service,f.json);
        assertEquals("NOT_FOUND",controller.lookup(request,response,null,id(3)).path("data").path("status").asText());
        verify(request,never()).getHeaders("Idempotency-Key");verify(response).setHeader("Cache-Control","no-store");
        when(request.getHeaders("X-CSRF-TOKEN")).thenReturn(Collections.enumeration(List.of("a","b")));
        assertThrows(SchemaRejection.class,()->controller.lookup(request,response,null,id(3)));
        verify(service,times(1)).lookup(any(),anyString(),anyString(),any());
    }
    @Test void maximumOriginalRequestCanBeWrappedButOverlargeOriginalIsRejected()throws Exception {
        var parameters=f.request.withObject("/request_parameters");parameters.put("padding","");
        int base=ContentReadAuthorization.canonicalBytes(f.request).length;
        parameters.put("padding","x".repeat(65536-base));
        byte[] original=ContentReadAuthorization.canonicalBytes(f.request);assertEquals(65536,original.length);
        assertNotNull(new SimulationInputArtifactSchema().request(original));
        byte[] wrapped=ContentReadAuthorization.canonicalBytes(input());assertTrue(wrapped.length>65536);
        assertNotNull(new SimulationInputArtifactLookupSchema().request(wrapped));
        parameters.put("padding","x".repeat(65537-base));
        assertThrows(SchemaRejection.class,()->new SimulationInputArtifactLookupSchema().request(ContentReadAuthorization.canonicalBytes(input())));
    }
    @Test void lookupEnvelopeHasIndependentBoundForDeclaredAndStreamingBodies()throws Exception {
        var request=mock(jakarta.servlet.http.HttpServletRequest.class);when(request.getContentLengthLong()).thenReturn(131073L);
        assertThrows(TenantAccessException.class,()->SimulationInputArtifactController.lookupBody(request));verify(request,never()).getInputStream();
        when(request.getContentLengthLong()).thenReturn(-1L);var stream=mock(jakarta.servlet.ServletInputStream.class);when(request.getInputStream()).thenReturn(stream);
        when(stream.readNBytes(131073)).thenReturn(new byte[131073]);assertThrows(TenantAccessException.class,()->SimulationInputArtifactController.lookupBody(request));
        assertThrows(SchemaRejection.class,()->new SimulationInputArtifactLookupSchema().request(new byte[131073]));
    }
    @Test void preparedConfigurationIdProjectOrRevisionCannotBeSubstituted()throws Exception {
        f.register();current();
        for(var config:List.of(
            f.json.object().put("project_id",id(2)).put("model_config_revision",id(4)).put("config_id",id(99)),
            f.json.object().put("project_id",id(99)).put("model_config_revision",id(4)).put("config_id",id(40)),
            f.json.object().put("project_id",id(2)).put("model_config_revision",id(99)).put("config_id",id(40)))){
            when(f.prepared.configuration()).thenReturn(config);
            assertThrows(TenantAccessException.class,this::lookup);
        }
    }
    @Test void operationDeletionBetweenPreparationAndFinalReadFailsClosed()throws Exception {
        f.register();current();clearInvocations(f.mapper,f.resources,f.permissions);
        when(f.sources.prepareForContext(eq(f.subject),any())).thenAnswer(inv->{f.operation=null;return f.prepared;});
        assertThrows(TenantAccessException.class,this::lookup);readOnly();
    }
    @Test void operationMutationBetweenPreparationAndFinalReadFailsClosed()throws Exception {
        f.register();current();clearInvocations(f.mapper,f.resources,f.permissions);
        when(f.sources.prepareForContext(eq(f.subject),any())).thenAnswer(inv->{var op=f.operation;
            f.operation=new SimulationInputArtifactService.Operation(op.tenantId(),op.actorId(),op.key(),"d".repeat(64),op.receipt(),op.schemaHash(),op.schemaVersion(),op.createdBy(),op.canonicalRequest(),op.bodyHash());return f.prepared;});
        assertThrows(TenantAccessException.class,this::lookup);readOnly();
    }
    @Test void lookupSchemaIsClosedAndReusesOriginalStrictRequest()throws Exception {
        var schema=new SimulationInputArtifactLookupSchema();assertNotNull(schema.validateRequest(input()));
        var bad=input().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)bad).put("old_session_id",id(99));assertThrows(SchemaRejection.class,()->schema.validateRequest(bad));
        var badOriginal=input().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)badOriginal.required("original_request")).put("grant",true);assertThrows(SchemaRejection.class,()->schema.validateRequest(badOriginal));
        assertEquals("NOT_FOUND",schema.result(null).path("status").asText());assertFalse(schema.result(null).toString().contains("EXPIRED"));
    }
}
