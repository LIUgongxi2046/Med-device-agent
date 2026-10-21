package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.ruoyi.medical.persistence.FormalEvaluationContextSchemaTest.*;

/** Servlet contracts with explicit authenticated-session and domain doubles; no deployed-session claim. */
class FormalEvaluationContextControllerTest {
    final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final FormalEvaluationContextService service=mock(FormalEvaluationContextService.class);
    final PermissionSchema json=new PermissionSchema();
    final MockMvc mvc;
    FormalEvaluationContextControllerTest()throws Exception {
        when(identities.requireCurrent(any(),any())).thenReturn(subject);
        var receipt=json.tree(new FormalEvaluationContextStore.Receipt(b(1),"APPROVED","2",id(5)));
        when(service.author(any(),anyString(),anyString(),any())).thenReturn(receipt);
        when(service.decide(any(),anyString(),anyString(),any())).thenReturn(receipt);
        when(service.recover(any(),anyString(),any())).thenReturn(receipt);
        when(service.query(any(),anyString(),any())).thenReturn(json.object().put("schema_version","1.0.0"));
        when(service.read(any(),anyString(),anyString())).thenReturn(json.object().put("revision","3"));
        mvc=MockMvcBuilders.standaloneSetup(new FormalEvaluationContextController(identities,new org.springframework.beans.factory.support.StaticListableBeanFactory(java.util.Map.of("service",service)).getBeanProvider(FormalEvaluationContextService.class),json)).build();
    }
    MockHttpServletRequestBuilder postRequest(String route) { return post(route).header("X-Schema-Version","1.0.0").header("X-Context-Revision","1").header("X-CSRF-TOKEN","csrf-token").contentType("application/json").content("{}"); }
    @Test void authorPassesOriginalKeyBodyAndVerifiedIdentityWith201()throws Exception {
        mvc.perform(postRequest("/api/formal-evaluation-context-bindings").header("Idempotency-Key","original-context-key"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Resource-Revision","2"));
        verify(service).author(eq(subject),eq("1"),eq("original-context-key"),eq("{}".getBytes()));
    }
    @Test void independentDecisionUsesSeparateRouteAndOriginalKey()throws Exception {
        mvc.perform(postRequest("/api/formal-evaluation-context-decisions").header("Idempotency-Key","original-decision-key")).andExpect(status().isCreated());
        verify(service).decide(eq(subject),eq("1"),eq("original-decision-key"),eq("{}".getBytes()));
    }
    @Test void readOnlyRecoveryDoesNotInvokeAuthorOrDecision()throws Exception {
        mvc.perform(postRequest("/api/formal-evaluation-context-bindings/recovery")).andExpect(status().isOk()).andExpect(header().string("X-Resource-Revision","2"));
        verify(service).recover(eq(subject),eq("1"),eq("{}".getBytes()));verify(service,never()).author(any(),anyString(),anyString(),any());verify(service,never()).decide(any(),anyString(),anyString(),any());
    }
    @Test void readonlyPostsRejectIdempotencyKeyAndRequireCsrf()throws Exception {
        for(String route:List.of("query","recovery")) {
            mvc.perform(postRequest("/api/formal-evaluation-context-bindings/"+route).header("Idempotency-Key","original-context-key")).andExpect(status().isUnprocessableEntity());
            mvc.perform(post("/api/formal-evaluation-context-bindings/"+route).header("X-Schema-Version","1.0.0").header("X-Context-Revision","1").contentType("application/json").content("{}")).andExpect(status().isUnprocessableEntity());
        }
        verifyNoInteractions(service);
    }
    @Test void duplicateSchemaContextAndCsrfAreRejectedBeforeService()throws Exception {
        for(String header:List.of("X-Schema-Version","X-Context-Revision","X-CSRF-TOKEN"))
            mvc.perform(postRequest("/api/formal-evaluation-context-bindings").header(header,"duplicate").header("Idempotency-Key","original-context-key")).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }
    @Test void queryParametersAndOversizeQueryCannotReachStore()throws Exception {
        mvc.perform(postRequest("/api/formal-evaluation-context-bindings/query?offset=0")).andExpect(status().isUnprocessableEntity());
        mvc.perform(postRequest("/api/formal-evaluation-context-bindings/query").content(" ".repeat(4097))).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(service);
    }
    @Test void getRejectsBodyAndIdempotencyKey()throws Exception {
        String route="/api/formal-evaluation-context-bindings/"+id(5);
        mvc.perform(get(route).header("X-Schema-Version","1.0.0").header("X-Context-Revision","1").content("{}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(route).header("X-Schema-Version","1.0.0").header("X-Context-Revision","1").header("Idempotency-Key","original-context-key")).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }
    @Test void identityFailureDoesNotReachService()throws Exception {
        when(identities.requireCurrent(any(),any())).thenThrow(new org.springframework.security.authentication.BadCredentialsException("test"));
        mvc.perform(postRequest("/api/formal-evaluation-context-bindings/recovery")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
    @Test void enabledRouteWithAbsentServiceReports503WithoutBreakingApplication()throws Exception {
        var empty=new org.springframework.beans.factory.support.StaticListableBeanFactory();
        var local=MockMvcBuilders.standaloneSetup(new FormalEvaluationContextController(identities,empty.getBeanProvider(FormalEvaluationContextService.class),json)).build();
        local.perform(postRequest("/api/formal-evaluation-context-bindings/recovery")).andExpect(status().isServiceUnavailable());
    }
    @Test void absentIdentityWinsOverMissingOptionalReadService()throws Exception {
        when(identities.requireCurrent(any(),any())).thenThrow(new org.springframework.security.authentication.BadCredentialsException("revoked"));
        var empty=new org.springframework.beans.factory.support.StaticListableBeanFactory();
        var local=MockMvcBuilders.standaloneSetup(new FormalEvaluationContextController(identities,empty.getBeanProvider(FormalEvaluationContextService.class),json)).build();
        local.perform(get("/api/formal-evaluation-context-bindings/"+id(5)).header("X-Schema-Version","1.0.0").header("X-Context-Revision","1")).andExpect(status().isUnauthorized());
    }
    @Test void domainBlockersKeepTruthfulStatuses()throws Exception {
        for(var pair:List.of(new Object[]{ErrorCode.ACCESS_DENIED,403},new Object[]{ErrorCode.OBJECT_UNAVAILABLE,404},new Object[]{ErrorCode.IDEMPOTENCY_CONFLICT,409},new Object[]{ErrorCode.DEPENDENCY_UNAVAILABLE,503})) {
            doThrow(new TenantAccessException((ErrorCode)pair[0])).when(service).recover(any(),anyString(),any());
            mvc.perform(postRequest("/api/formal-evaluation-context-bindings/recovery")).andExpect(status().is((Integer)pair[1]));
        }
    }
}
