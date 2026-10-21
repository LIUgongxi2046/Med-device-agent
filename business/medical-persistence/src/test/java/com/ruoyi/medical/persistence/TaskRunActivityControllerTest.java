package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.workflow.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

/** In-process exact-route tests only; no TLS, provider or workflow connection. */
class TaskRunActivityControllerTest {
    final ServiceCertificateIdentity identity=mock(ServiceCertificateIdentity.class);final TaskRunActivityEndpointFactory factory=mock(TaskRunActivityEndpointFactory.class);final RunActivities activities=mock(RunActivities.class);
    final TaskRunActivitySchema schema=new TaskRunActivitySchema();final TaskRunActivityController controller=new TaskRunActivityController(identity,factory,schema,4096);
    final Reference reference=new Reference("1.0.0",id(1),id(2),id(3),ExecutionKind.TASK,id(4),"a".repeat(64),id(5),9999999999999L,8,8,2,1000,2000,3000,"control","professional");
    @BeforeEach void setup()throws Exception{var certificate=mock(ServiceCertificateIdentity.Verified.class);when(identity.require(any(jakarta.servlet.http.HttpServletRequest.class))).thenReturn(certificate);when(factory.create(any())).thenReturn(activities);}
    MockHttpServletRequest request()throws Exception{var request=new MockHttpServletRequest("POST",TaskRunActivitySchema.ROUTE);request.setContentType("application/json");request.setContent(schema.encode(schema.request(TaskRunActivitySchema.Operation.LOAD,reference)));return request;}
    @Test void exactServiceLoadUsesRequestScopedAdapterAndClosedReply()throws Exception{
        var plan=new Plan(reference.inputSha256(),id(8),State.RUNNING,null,null,List.of());when(activities.load(reference)).thenReturn(plan);var http=request();var response=new MockHttpServletResponse();var reply=controller.activity(http,response);
        assertEquals("no-store",response.getHeader("Cache-Control"));assertEquals(plan,schema.readResponse(schema.encode(reply),TaskRunActivitySchema.Operation.LOAD,reference,Plan.class));verify(factory).create(http);verify(activities).load(reference);
    }
    @Test void browserCookieBearerAndMissingMtlsCannotMintAdapter()throws Exception{
        for(String header:List.of("Origin","Sec-Fetch-Site","Cookie","Authorization")){var http=request();http.addHeader(header,"forbidden");assertThrows(TenantAccessException.class,()->controller.activity(http,new MockHttpServletResponse()));}
        when(identity.require(any(jakarta.servlet.http.HttpServletRequest.class))).thenThrow(new SecurityException("missing"));assertThrows(TenantAccessException.class,()->controller.activity(request(),new MockHttpServletResponse()));verifyNoInteractions(factory,activities);
    }
    @Test void unknownQueryOversizeAndNestedActorNeverReachBusinessBoundary()throws Exception{
        var http=request();http.setQueryString("actor=caller");assertThrows(TenantAccessException.class,()->controller.activity(http,new MockHttpServletResponse()));
        var large=request();large.setContent(new byte[4097]);assertThrows(TenantAccessException.class,()->controller.activity(large,new MockHttpServletResponse()));
        var body=schema.request(TaskRunActivitySchema.Operation.LOAD,reference);((com.fasterxml.jackson.databind.node.ObjectNode)body.required("reference")).put("actor_id",id(9));var extra=request();extra.setContent(schema.encode(body));assertThrows(Exception.class,()->controller.activity(extra,new MockHttpServletResponse()));verifyNoInteractions(factory,activities);
    }
    @Test void routeIsExactPostOnly()throws Exception{
        assertTrue(TaskRunActivitySecurityConfiguration.matcher().matches(request()));for(String route:List.of(TaskRunActivitySchema.ROUTE+"/","/internal/v1/professional-runtime/open","/internal/medical/task-run/activities/other"))assertFalse(TaskRunActivitySecurityConfiguration.matcher().matches(new MockHttpServletRequest("POST",route)));
        assertFalse(TaskRunActivitySecurityConfiguration.matcher().matches(new MockHttpServletRequest("GET",TaskRunActivitySchema.ROUTE)));
    }
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
}
