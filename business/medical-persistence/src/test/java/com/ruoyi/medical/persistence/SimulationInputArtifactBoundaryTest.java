package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationInputArtifactBoundaryTest {
    final SimulationInputArtifactSchema schema=new SimulationInputArtifactSchema();
    final PermissionSchema json=new PermissionSchema();
    SimulationInputArtifactBoundaryTest()throws Exception { }
    byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    String valid(){return "{\"schema_version\":\"1.0.0\",\"project_id\":null,\"model_config_revision\":\"00000000-0000-0000-0000-000000000004\",\"request_parameters\":{}}";}
    @Test void strictSchemaRejectsCallerObjectGrantFieldsDuplicateKeysAndUnsafeNumbers()throws Exception {
        assertNotNull(schema.request(bytes(valid())));
        for(String suffix:List.of(",\"object_ref\":{}",",\"purpose\":\"OTHER\"",",\"tenant_id\":null",",\"schema_version\":\"1.0.0\""))
            assertThrows(SchemaRejection.class,()->schema.request(bytes(valid().substring(0,valid().length()-1)+suffix+"}")));
        assertThrows(SchemaRejection.class,()->schema.request(bytes(valid().replace("\"request_parameters\":{}","\"request_parameters\":{\"x\":9007199254740992}"))));
        assertThrows(SchemaRejection.class,()->schema.request(bytes(valid()+" {}")));
        assertThrows(SchemaRejection.class,()->schema.request(new byte[65537]));
    }
    @Test void requestIdentityBindsRouteBodySessionContextAndExtensionSchema()throws Exception {
        var body=schema.request(bytes(valid()));String first=schema.requestHash("one",body,"1","session");
        assertNotEquals(first,schema.requestHash("two",body,"1","session"));assertNotEquals(first,schema.requestHash("one",body,"2","session"));
        assertNotEquals(first,schema.requestHash("one",body,"1","other"));
        assertEquals(64,schema.manifest().length());
        for(String key:Arrays.asList(null,"short","key with whitespace and length"))assertThrows(SchemaRejection.class,()->SimulationInputArtifactSchema.key(key));
    }
    @Test void profileIsFixedToKnownSourceSchemaAndDoesNotRepresentGrant()throws Exception {
        var profile=SimulationInputArtifactProfile.profile();assertEquals("CONFIGURATION",profile.resourceKind());assertEquals("configuration.read",profile.action());
        assertEquals("source.read",SimulationInputArtifactProfile.profile("SOURCE").action());
        assertThrows(TenantAccessException.class,()->SimulationInputArtifactProfile.profile("DOCUMENT"));
        assertEquals(SchemaCatalogue.fromClasspath().schemaRef("simulation-execution","ExactInput"),profile.schemaRef());
        assertEquals(SchemaCatalogue.fromClasspath().schemaDigest("simulation-execution"),profile.schemaDocumentSha256());
    }
    HttpServletRequest http(){
        var request=mock(HttpServletRequest.class);
        for(var e:Map.of("X-Schema-Version","1.0.0","X-Context-Revision","1","Idempotency-Key","registration-key-001","X-CSRF-TOKEN","csrf-test").entrySet())
            when(request.getHeaders(e.getKey())).thenAnswer(inv->Collections.enumeration(List.of(e.getValue())));
        return request;
    }
    @Test void queryDuplicateHeadersAndMissingCsrfFailBeforeIdentity()throws Exception {
        var identities=mock(ApplicationSessionIdentity.class);var service=mock(SimulationInputArtifactService.class);
        var controller=new SimulationInputArtifactController(identities,service,json);var response=mock(HttpServletResponse.class);
        for(String header:List.of("X-Schema-Version","X-Context-Revision","Idempotency-Key","X-CSRF-TOKEN")){
            var request=http();when(request.getHeaders(header)).thenReturn(Collections.enumeration(List.of("duplicate","duplicate")));
            assertThrows(SchemaRejection.class,()->controller.register(request,response,null,SimulationInputArtifactServiceTest.id(3)));
        }
        var missing=http();when(missing.getHeaders("X-CSRF-TOKEN")).thenReturn(Collections.emptyEnumeration());
        assertThrows(SchemaRejection.class,()->controller.register(missing,response,null,SimulationInputArtifactServiceTest.id(3)));
        var query=http();when(query.getQueryString()).thenReturn("tenant_id=untrusted");
        assertThrows(SchemaRejection.class,()->controller.register(query,response,null,SimulationInputArtifactServiceTest.id(3)));
        verifyNoInteractions(identities,service);
    }
    @Test void bodyBoundAppliesToDeclaredAndUnknownLengths()throws Exception {
        var request=http();when(request.getContentLengthLong()).thenReturn(65537L);
        assertThrows(TenantAccessException.class,()->SimulationInputArtifactController.body(request));verify(request,never()).getInputStream();
        when(request.getContentLengthLong()).thenReturn(-1L);var stream=mock(ServletInputStream.class);when(request.getInputStream()).thenReturn(stream);
        when(stream.readNBytes(65537)).thenReturn(new byte[65537]);assertThrows(TenantAccessException.class,()->SimulationInputArtifactController.body(request));
    }
    @Test void malformedRequestNeverLeaksInputAndErrorsAreNoStore()throws Exception {
        var response=mock(HttpServletResponse.class);var bytes=new java.io.ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ServletOutputStream(){public boolean isReady(){return true;}public void setWriteListener(jakarta.servlet.WriteListener listener){}public void write(int v){bytes.write(v);}});
        new SimulationInputArtifactController(null,null,json).failed(new RuntimeException("private object path",new TenantAccessException(ErrorCode.ACCESS_DENIED)),response);
        verify(response).setStatus(403);verify(response).setHeader("Cache-Control","no-store");assertFalse(bytes.toString().contains("private object"));
    }
    @Test void mapperHasActorScopedRecoveryNoMutatingExistingLinkAndNoTenantBypass()throws Exception {
        String xml;
        try(var input=getClass().getResourceAsStream("/medical-mappers/SimulationInputArtifactMapper.xml")){xml=new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8);}
        var config=new org.apache.ibatis.session.Configuration();
        new org.apache.ibatis.builder.xml.XMLMapperBuilder(new java.io.StringReader(xml),config,"simulation-input-test",config.getSqlFragments()).parse();
        String namespace="com.ruoyi.medical.persistence.mapper.SimulationInputArtifactMapper.";
        for(String statement:List.of("lockFixture","link","insertArtifact","insertBinding","insertLink","operation","insertOperation"))assertTrue(config.hasStatement(namespace+statement));
        assertTrue(xml.contains("schema_manifest_sha256,schema_version,created_by"));
        assertTrue(xml.contains("FOR UPDATE"));assertTrue(xml.contains("actor_id=#{userId} AND idempotency_key=#{key}"));
        assertFalse(xml.contains("<update"));assertFalse(xml.contains("<delete"));assertFalse(xml.contains("INSERT IGNORE"));assertFalse(xml.contains("${"));
    }
}
