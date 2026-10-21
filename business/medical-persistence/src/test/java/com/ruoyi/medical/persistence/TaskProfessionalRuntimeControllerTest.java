package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual bounded HTTP parsing/dispatch with an explicit independently authenticated gate double. */
class TaskProfessionalRuntimeControllerTest {
    final JsonMapper json=new JsonMapper();final TaskProfessionalSupervisorGate gate=mock(TaskProfessionalSupervisorGate.class);
    final TaskProfessionalRuntimeController controller=new TaskProfessionalRuntimeController(gate);
    MockHttpServletRequest request(String body){var request=new MockHttpServletRequest();request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));return request;}
    @Test void exactRequestMustReachRealGateAndDoesNotMintWorkerToken()throws Exception {
        var request=request("{\"binding\":{}}");var response=new MockHttpServletResponse();var expected=json.createObjectNode().put("admission","CURRENT");when(gate.check(eq(request),any())).thenReturn(expected);
        assertSame(expected,controller.supervisor(request,response));assertEquals("no-store",response.getHeader("Cache-Control"));verify(gate).check(eq(request),eq(json.readTree("{\"binding\":{}}")));
    }
    @Test void gateDenialIsNeverConvertedIntoAdmission()throws Exception {
        var request=request("{}");when(gate.check(eq(request),any())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->controller.supervisor(request,new MockHttpServletResponse()));
    }
    @Test void duplicateFieldsAndTrailingValuesNeverReachGate()throws Exception {
        for(String body:List.of("{\"a\":1,\"a\":2}","{} {}",""))assertThrows(Exception.class,()->controller.supervisor(request(body),new MockHttpServletResponse()));verifyNoInteractions(gate);
    }
    @Test void boundedBodyAndQueryCannotChangeSupervisorAuthority()throws Exception {
        var query=request("{}");query.setQueryString("allow=true");assertThrows(TenantAccessException.class,()->controller.supervisor(query,new MockHttpServletResponse()));
        assertThrows(TenantAccessException.class,()->controller.plan(request(" ".repeat(32769)),new MockHttpServletResponse()));verifyNoInteractions(gate);
    }
    @Test void planUsesIndependentPlanReaderNotGateRelease()throws Exception {
        var request=request("{}");var response=new MockHttpServletResponse();var expected=json.createObjectNode().put("registered_envelope_base64","e30=");when(gate.plan(eq(request),any())).thenReturn(expected);
        assertSame(expected,controller.plan(request,response));verify(gate,never()).check(any(),any());assertEquals("no-store",response.getHeader("Cache-Control"));
    }
    @Test void denialRemainsExplicitNoncacheableWithoutCallerSuccess()throws Exception {
        var response=new MockHttpServletResponse();controller.denied(new SecurityException("test mTLS rejected"),response);assertEquals(403,response.getStatus());assertEquals("ACCESS_DENIED",json.readTree(response.getContentAsByteArray()).required("error").textValue());assertEquals("no-store",response.getHeader("Cache-Control"));
    }
}
