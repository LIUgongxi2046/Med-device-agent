package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual servlet route and shared boundary, service/identity named doubles; no security-filter claim. */
class FormalEvaluationPlanPreparationContentControllerTest {
    final PermissionSchema json=new PermissionSchema();final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final FormalEvaluationPlanPreparationContentService service=mock(FormalEvaluationPlanPreparationContentService.class);
    final ObjectProvider<FormalEvaluationPlanPreparationContentService> provider=mock(ObjectProvider.class);
    final MockMvc mvc;
    FormalEvaluationPlanPreparationContentControllerTest()throws Exception {when(provider.getIfUnique()).thenReturn(service);when(identities.requireCurrent(any(),any())).thenReturn(subject);when(service.read(any(),anyString(),any())).thenReturn(json.object().put("fixture",true));mvc=MockMvcBuilders.standaloneSetup(new FormalEvaluationPlanPreparationContentController(identities,provider,json)).build();}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(){return post("/api/formal-evaluation-plan-preparations/content").contentType("application/json").header("X-Schema-Version","1.0.0").header("X-Context-Revision","1").header("X-CSRF-TOKEN","fixture-csrf").content("{}");}
    @Test void readRouteUsesCurrentIdentityNoStoreAndFixedEnvelope()throws Exception {mvc.perform(request()).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Resource-Revision","1"));verify(service).read(eq(subject),eq("1"),eq("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    @Test void readRouteRejectsIdempotencyQueryDuplicateHeadersAndMissingCsrf()throws Exception {
        mvc.perform(request().header("Idempotency-Key","original-fixture-key")).andExpect(status().isUnprocessableEntity());mvc.perform(request().queryParam("artifact_id","forged")).andExpect(status().isUnprocessableEntity());
        for(String h:java.util.List.of("X-Schema-Version","X-Context-Revision","X-CSRF-TOKEN"))mvc.perform(request().header(h,"duplicate")).andExpect(status().isUnprocessableEntity());
        mvc.perform(request().with(q->{q.removeHeader("X-CSRF-TOKEN");return q;})).andExpect(status().isUnprocessableEntity());verifyNoInteractions(service,provider,identities);
    }
    @Test void dependencyUnavailableRemainsSeparateFromIdentityAndContentDenial()throws Exception {
        when(provider.getIfUnique()).thenReturn(null);mvc.perform(request()).andExpect(status().isServiceUnavailable());verify(identities).requireCurrent(any(),any());
        when(identities.requireCurrent(any(),any())).thenThrow(new BadCredentialsException("revoked"));clearInvocations(provider);mvc.perform(request()).andExpect(status().isUnauthorized());verifyNoInteractions(provider);
    }
    @Test void oversizedBodyRejectedBeforeContentService()throws Exception {mvc.perform(request().content("x".repeat(1048577))).andExpect(status().isPayloadTooLarge());verifyNoInteractions(service);}
}
