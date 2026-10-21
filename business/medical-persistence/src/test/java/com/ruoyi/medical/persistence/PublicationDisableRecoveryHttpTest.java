package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP controller -> outer service -> original private-history/journal stack. CSRF remains root middleware. */
class PublicationDisableRecoveryHttpTest {
 PublicationDisableRecoveryTest f;MockMvc mvc;
 @BeforeEach void setup()throws Exception{f=new PublicationDisableRecoveryTest();f.setup();f.disable();var identities=mock(ApplicationSessionIdentity.class);when(identities.requireCurrent(any(),any())).thenReturn(f.history.subject);mvc=MockMvcBuilders.standaloneSetup(new FormalAgentPublicationController(identities,mock(FormalAgentPublicationService.class),f.history.service,f.f.json)).build();}
 @AfterEach void cleanup()throws Exception{f.cleanup();}
 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(){return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/formal-agent-publications/disable/recovery").header("X-Schema-Version","1.0.0").header("X-Context-Revision","1").contentType("application/json");}
 @Test void exactRecoveryRouteReturnsBoundCompletedReceiptAndNoStore()throws Exception{var response=mvc.perform(post().content(f.bytes(f.outer()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Schema-Version","1.0.0")).andReturn().getResponse();var value=f.f.formal.read(response.getContentAsString()).required("data");assertEquals("COMPLETED",value.path("state").asText());assertEquals(f.key,value.path("original_key").asText());assertEquals("3",value.path("resulting_revision").asText());assertEquals("PUBLICATION",value.path("receipt").path("kind").asText());f.noWrites();}
 @Test void suppliedMutationKeyIsRejectedBeforeReadingOriginal()throws Exception{mvc.perform(post().header("Idempotency-Key","forbidden-new-operation").content(f.bytes(f.outer()))).andExpect(status().isUnprocessableEntity());verifyNoInteractions(f.published.writer);f.noWrites();}
 @Test void queryParametersAndDuplicateContextRejected()throws Exception{mvc.perform(post().queryParam("retry","true").content(f.bytes(f.outer()))).andExpect(status().isUnprocessableEntity());mvc.perform(post().header("X-Context-Revision","1").content(f.bytes(f.outer()))).andExpect(status().isUnprocessableEntity());verifyNoInteractions(f.published.writer);f.noWrites();}
 @Test void unknownFieldsAndMalformedJsonFailClosed()throws Exception{mvc.perform(post().content(f.bytes(f.outer().put("retry",true)))).andExpect(status().isUnprocessableEntity());mvc.perform(post().content("{}{}" )).andExpect(status().isUnprocessableEntity());verifyNoInteractions(f.published.writer);f.noWrites();}
 @Test void changedBodyAndContextDoNotReturnSuccess()throws Exception{f.request.put("reason","Different body");mvc.perform(post().content(f.bytes(f.outer()))).andExpect(status().isConflict());mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/formal-agent-publications/disable/recovery").header("X-Schema-Version","1.0.0").header("X-Context-Revision","2").contentType("application/json").content(f.bytes(f.outer()))).andExpect(status().isConflict());f.noWrites();}
 @Test void boundedBodyRejectedBeforeReadingOriginal()throws Exception{mvc.perform(post().content(new byte[FormalPublicationDisableRecoverySchema.MAXIMUM_BYTES+1])).andExpect(status().isPayloadTooLarge());verifyNoInteractions(f.published.writer);f.noWrites();}
}
