package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskProfessionalPackageAuthoringV2ControllerTest {
    final PermissionSchema json=new PermissionSchema();final TaskProfessionalPackageAuthoringSchema schema=new TaskProfessionalPackageAuthoringSchema(json);
    final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);final TaskProfessionalPackageAuthoringService original=mock(TaskProfessionalPackageAuthoringService.class),service=mock(TaskProfessionalPackageAuthoringService.class);
    final Authentication auth=mock(Authentication.class);final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);final TaskProfessionalPackageAuthoringV2Controller controller;
    final String project=TaskProfessionalPackageAuthoringSchemaTest.id(2);
    TaskProfessionalPackageAuthoringV2ControllerTest()throws Exception{when(original.v2()).thenReturn(service);controller=new TaskProfessionalPackageAuthoringV2Controller(identities,original,schema);}
    MockHttpServletRequest request(){var q=new MockHttpServletRequest();q.addHeader("X-Schema-Version","1.0.0");q.addHeader("X-Context-Revision","1");return q;}
    @Test void routeSelectionIsExplicitAndCannotChangeTheSharedHttpVersion()throws Exception {
        var routes=new TreeSet<String>();for(var method:TaskProfessionalPackageAuthoringV2Controller.class.getDeclaredMethods()){var get=method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);var post=method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);if(get!=null)routes.addAll(List.of(get.value()));if(post!=null)routes.addAll(List.of(post.value()));}
        assertEquals(Set.of("/api/projects/{projectId}/task-professional-package-v2-preparation","/api/projects/{projectId}/task-professional-packages-v2","/api/projects/{projectId}/task-professional-packages-v2/{operationId}","/api/projects/{projectId}/task-professional-package-v2-recoveries"),routes);
        var q=request();q.removeHeader("X-Schema-Version");q.addHeader("X-Schema-Version","2.0.0");assertEquals(ErrorCode.SCHEMA_UNSUPPORTED,assertThrows(SchemaRejection.class,()->controller.preparation(q,new MockHttpServletResponse(),auth,project)).code());verifyNoInteractions(service,identities);
    }
    @Test void v2PayloadIsReturnedInTheFrozenVersionOneEnvelope()throws Exception {
        var q=request();when(identities.requireCurrent(q,auth)).thenReturn(subject);var envelope=schema.v2().envelope(json.object().put("schema_version","2.0.0"),"1");when(service.preparation(subject,"1",project)).thenReturn(new TaskProfessionalPackageAuthoringService.Response(200,envelope,null));var r=new MockHttpServletResponse();assertEquals(envelope,controller.preparation(q,r,auth,project));assertEquals("1.0.0",r.getHeader("X-Schema-Version"));assertEquals("1.0.0",envelope.required("schema_version").textValue());assertEquals("2.0.0",envelope.at("/data/schema_version").textValue());assertNull(r.getHeader("X-Task-Package-Outcome"));
    }
    @Test void exactDurableV2ProofCannotBeMistakenForV1Proof()throws Exception {
        var f=new TaskProfessionalPackageAuthoringV2Fixture(TaskProfessionalPackageAuthoringSchemaTest.id(1),project,TaskProfessionalPackageAuthoringSchemaTest.id(3));var proof=f.tx(()->f.ledger.reject(f.a,"1",project,"v2-controller-rejection-key",new byte[]{1},ErrorCode.SCHEMA_INVALID));var r=new MockHttpServletResponse();assertNull(TaskProfessionalPackageAuthoringController.respond(r,new TaskProfessionalPackageAuthoringService.Response(422,null,proof),schema.v2()));assertEquals("1.0.0",r.getHeader("X-Schema-Version"));assertEquals("TASK-PROFESSIONAL-PACKAGE-V2",r.getHeader("X-Task-Package-Operation"));assertNotEquals("TASK-PROFESSIONAL-PACKAGE",r.getHeader("X-Task-Package-Operation"));assertEquals("V2",r.getHeader("X-Task-Package-Profile"));assertEquals("/api/projects/"+project+"/task-professional-packages-v2",r.getHeader("X-Task-Package-Route"));assertEquals(f.schema.manifest(),r.getHeader("X-Task-Package-Contract-Sha256"));var error=schema.parse(r.getContentAsByteArray());SchemaCatalogue.fromClasspath().validate("interfaces",error,"Error");assertTrue(error.required("operation_id").isNull());assertEquals("1.0.0",error.required("schema_version").textValue());
        var bad=((com.fasterxml.jackson.databind.node.ObjectNode)proof.deepCopy()).put("authoring_route","/other");assertThrows(TenantAccessException.class,()->TaskProfessionalPackageAuthoringController.respond(new MockHttpServletResponse(),new TaskProfessionalPackageAuthoringService.Response(422,null,bad),schema.v2()));
    }
    @Test void authorizationAndHeaderGuardsStillRunBeforeBodyOrProof()throws Exception {
        var q=spy(request());q.addHeader("Idempotency-Key","v2-controller-original-key");q.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));when(identities.requireCurrent(q,auth)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->controller.create(q,new MockHttpServletResponse(),auth,project));verify(q,never()).getInputStream();verifyNoInteractions(service);
        var recovery=request();recovery.addHeader("Idempotency-Key","not-allowed-on-recovery");assertThrows(SchemaRejection.class,()->controller.recover(recovery,new MockHttpServletResponse(),auth,project));var r=new MockHttpServletResponse();controller.denied(new TenantAccessException(ErrorCode.ACCESS_DENIED),r);assertNull(r.getHeader("X-Task-Package-Outcome"));assertNull(r.getHeader("X-Task-Package-Profile"));assertEquals("1.0.0",r.getHeader("X-Schema-Version"));
    }
}
