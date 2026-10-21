package com.ruoyi.medical.persistence;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

/** Real servlet boundary; session/services doubled. Does not claim a deployed CSRF filter. */
class TaskFieldTargetControllerTest {
 final PermissionSchema json=new PermissionSchema();final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);
 final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
 final TaskFieldMappingService mappings=mock(TaskFieldMappingService.class);final TaskFieldTargetService targets=mock(TaskFieldTargetService.class);
 final TaskFieldTargetPreparationService preparation=mock(TaskFieldTargetPreparationService.class);final TaskFieldTargetInputCapabilityService inputs=mock(TaskFieldTargetInputCapabilityService.class);
 final MockMvc mvc;final String task="10000000-0000-4000-8000-000000000001";
 TaskFieldTargetControllerTest()throws Exception{when(identities.requireCurrent(any(),any())).thenReturn(subject);mvc=MockMvcBuilders.standaloneSetup(new TaskFieldTargetController(identities,mappings,targets,preparation,inputs,json)).build();}
 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getPreparation(){return get("/api/tasks/"+task+"/field-target-preparation").header("X-Schema-Version","1.0.0").header("X-Context-Revision","1");}
 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postTo(String suffix){return post("/api/tasks/"+task+"/"+suffix).contentType("application/json").header("X-Schema-Version","1.0.0").header("X-Context-Revision","1").header("X-CSRF-TOKEN","fixture").content("{}");}
 @Test void preparationEnvelopeUsesActualMappingHeadIncludingZero()throws Exception{
  for(String revision:java.util.List.of("0","2")){when(preparation.read(subject,"1",task)).thenReturn(json.object().put("head_revision",revision));mvc.perform(getPreparation()).andExpect(status().isOk()).andExpect(header().string("X-Resource-Revision",revision)).andExpect(header().string("Cache-Control","no-store"));}
 }
 @Test void originalRecoveryHasNoNewIdempotencyKeyAndPreservesNullAbsence()throws Exception{
  mvc.perform(postTo("field-targets/recovery")).andExpect(status().isOk()).andExpect(result->org.junit.jupiter.api.Assertions.assertTrue(new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).required("data").isNull()));
  verify(targets).recover(eq(subject),eq("1"),eq(task),any());
  clearInvocations(targets);mvc.perform(postTo("field-targets/recovery").header("Idempotency-Key","replacement-key-123")).andExpect(status().isUnprocessableEntity());verifyNoInteractions(targets);
 }
 @Test void authorRequiresExplicitOriginalKeyAndReturnsCreated()throws Exception{
  when(mappings.author(eq(subject),eq("1"),eq(task),eq("original-key-123456"),any())).thenReturn(json.object().put("revision","3"));
  mvc.perform(postTo("field-mappings").header("Idempotency-Key","original-key-123456")).andExpect(status().isCreated()).andExpect(header().string("X-Resource-Revision","3"));
  clearInvocations(mappings);mvc.perform(postTo("field-mappings")).andExpect(status().isUnprocessableEntity());verifyNoInteractions(mappings);
 }
 @Test void missingCsrfDuplicateContextAndQueryAreRejectedBeforeService()throws Exception{
  mvc.perform(postTo("field-targets").header("Idempotency-Key","original-key-123456").with(q->{q.removeHeader("X-CSRF-TOKEN");return q;})).andExpect(status().isUnprocessableEntity());
  mvc.perform(getPreparation().header("X-Context-Revision","2")).andExpect(status().isUnprocessableEntity());
  mvc.perform(getPreparation().queryParam("project_id","forged")).andExpect(status().isUnprocessableEntity());verifyNoInteractions(targets,preparation,identities);
 }
 @Test void capabilityDecisionBindsRouteTaskAndCapability()throws Exception{
  String capability="10000000-0000-4000-8000-000000000002";when(inputs.decide(eq(subject),eq("1"),eq(task),eq(capability),eq("decision-key-123456"),any())).thenReturn(json.object().put("revision","2"));
  mvc.perform(postTo("field-target-input-capabilities/"+capability+"/decisions").header("Idempotency-Key","decision-key-123456")).andExpect(status().isOk());verify(inputs).decide(eq(subject),eq("1"),eq(task),eq(capability),eq("decision-key-123456"),any());
 }
 @Test void oversizedBodyDoesNotReachProducer()throws Exception{mvc.perform(postTo("field-targets").header("Idempotency-Key","original-key-123456").content("x".repeat(8388609))).andExpect(status().isPayloadTooLarge());verifyNoInteractions(targets);}
}
