package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.ruoyi.medical.persistence.FormalEvaluationContextProtocol.*;
import static com.ruoyi.medical.persistence.FormalEvaluationContextSchemaTest.*;

/** Actual servlet -> service -> 088 journal path. Session, tenant transaction and SQL are explicit doubles. */
class FormalEvaluationContextReadEntryTest {
    final FormalEvaluationContextReadAuthority reads=mock(FormalEvaluationContextReadAuthority.class);
    final FormalEvaluationContextStoreTest f=new FormalEvaluationContextStoreTest();
    final EnterpriseAccess access=mock(EnterpriseAccess.class);
    final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final MockMvc mvc;
    FormalEvaluationContextReadEntryTest()throws Exception {
        when(identities.requireCurrent(any(),any())).thenReturn(subject);when(subject.sessionId()).thenReturn("session-a");
        when(access.execute(eq(subject),any())).thenAnswer(i->f.tx(()->((Function<EnterpriseAccess.Admission,Object>)i.getArgument(1)).apply(f.a)));
        var service=new FormalEvaluationContextService(access,f.store,new FormalEvaluationContextHttpSchema(),new AgentAuthoringCursor(),f.json,reads);
        mvc=MockMvcBuilders.standaloneSetup(new FormalEvaluationContextController(identities,new org.springframework.beans.factory.support.StaticListableBeanFactory(java.util.Map.of("service",service)).getBeanProvider(FormalEvaluationContextService.class),f.json)).build();
    }
    MockHttpServletRequestBuilder request(String path,byte[] body) {return post(path).header("X-Schema-Version","1.0.0").header("X-Context-Revision","1").header("X-CSRF-TOKEN","session-csrf").contentType("application/json").content(body);}
    byte[] author()throws Exception{return f.bytes(new Author("1.0.0",definition(),"Explicit context binding"));}
    JsonNode create()throws Exception{return f.schema.read(mvc.perform(request("/api/formal-evaluation-context-bindings",author()).header("Idempotency-Key","author-original-key")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).required("data");}
    byte[] recovery(String key,JsonNode original)throws Exception {var q=f.json.object().put("schema_version","1.0.0").put("kind","AUTHOR").put("original_key",key);q.set("request",original);return f.bytes(q);}
    @Test void actualHttpAuthorReviewReadAndRecoveryPreserveOriginalReceipt()throws Exception {
        var original=create();var basis=new PermissionProtocol.Basis(original.required("binding").required("version_id").asText(),original.required("binding").required("content_sha256").asText());
        f.actor=id(103);mvc.perform(request("/api/formal-evaluation-context-decisions",f.bytes(new Decide("1.0.0",basis,"1","APPROVE","Independent review"))).header("Idempotency-Key","review-original-key")).andExpect(status().isCreated()).andExpect(header().string("X-Resource-Revision","2"));
        var view=f.schema.read(mvc.perform(get("/api/formal-evaluation-context-bindings/"+basis.version_id()).header("X-Schema-Version","1.0.0").header("X-Context-Revision","1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).required("data");
        assertEquals("APPROVED",view.required("status").asText());assertEquals(1,view.required("decisions").size());
        f.actor=id(101);clearInvocations(f.db);
        var recovered=f.schema.read(mvc.perform(request("/api/formal-evaluation-context-bindings/recovery",recovery("author-original-key",f.schema.read(new String(author(),java.nio.charset.StandardCharsets.UTF_8))))).andExpect(status().isOk()).andExpect(header().string("X-Resource-Revision","1")).andReturn().getResponse().getContentAsString()).required("data");
        assertEquals(original,recovered);verify(f.db,never()).update(anyString(),any(Object[].class));assertEquals(2,f.rows("operation_record").size());
    }
    @Test void actualHttpRecoveryCannotAuthorMissingOperation()throws Exception {
        mvc.perform(request("/api/formal-evaluation-context-bindings/recovery",recovery("missing-original-key",f.schema.read(new String(author(),java.nio.charset.StandardCharsets.UTF_8))))).andExpect(status().isNotFound());
        verify(f.db,never()).update(anyString(),any(Object[].class));assertTrue(f.rows("formal_evaluation_context_version").isEmpty());
    }
    @Test void actualHttpAuthorCannotApproveOwnContext()throws Exception {
        var original=create();var basis=new PermissionProtocol.Basis(original.required("binding").required("version_id").asText(),original.required("binding").required("content_sha256").asText());
        mvc.perform(request("/api/formal-evaluation-context-decisions",f.bytes(new Decide("1.0.0",basis,"1","APPROVE","Self review"))).header("Idempotency-Key","review-original-key")).andExpect(status().isForbidden());
        assertEquals(1,f.rows("operation_record").size());
    }
    @Test void actualHttpMalformedJsonAndPathAre422()throws Exception {
        mvc.perform(request("/api/formal-evaluation-context-bindings","{".getBytes()).header("Idempotency-Key","author-original-key")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/formal-evaluation-context-bindings/not-a-uuid").header("X-Schema-Version","1.0.0").header("X-Context-Revision","1")).andExpect(status().isUnprocessableEntity());
        assertTrue(f.rows("operation_record").isEmpty());
    }
}
