package com.ruoyi.medical.persistence;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.Authentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FormalEvaluationPolicyControllerTest {
 final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);final EvaluationAcceptancePolicyService service=mock(EvaluationAcceptancePolicyService.class);final FormalEvaluationPolicySchema schema=new FormalEvaluationPolicySchema();final PermissionSchema json=new PermissionSchema();final FormalEvaluationPolicyController controller=new FormalEvaluationPolicyController(identities,service,schema,json);
 FormalEvaluationPolicyControllerTest()throws Exception{}
 MockHttpServletRequest request(){var q=new MockHttpServletRequest();q.addHeader("X-Schema-Version","1.0.0");q.addHeader("X-Context-Revision","1");q.addHeader("X-CSRF-TOKEN","synthetic-token");q.addHeader("Idempotency-Key","formal-policy-controller-test");return q;}
 @Test void gateIsExplicitDefaultOff(){var gate=FormalEvaluationPolicyController.class.getAnnotation(ConditionalOnProperty.class);assertEquals("medical.formal-evaluation-policy",gate.prefix());assertFalse(gate.matchIfMissing());assertEquals("true",gate.havingValue());}
 @Test void missingCsrfHeaderStopsWriteBeforeIdentity()throws Exception{var q=request();q.removeHeader("X-CSRF-TOKEN");assertThrows(SchemaRejection.class,()->controller.author(q,new MockHttpServletResponse(),mock(Authentication.class)));verifyNoInteractions(identities,service);}
 @Test void duplicateSchemaHeaderRejected()throws Exception{var q=request();q.addHeader("X-Schema-Version","1.0.0");assertThrows(SchemaRejection.class,()->controller.author(q,new MockHttpServletResponse(),null));verifyNoInteractions(service);}
 @Test void queryParametersCannotChangeTarget()throws Exception{var q=request();q.setQueryString("approved=true");assertThrows(SchemaRejection.class,()->controller.author(q,new MockHttpServletResponse(),null));verifyNoInteractions(service);}
 @Test void oversizeBodyStopsBeforeService()throws Exception{var q=request();q.setContent(new byte[FormalEvaluationPolicySchema.MAXIMUM_BYTES+1]);assertThrows(TenantAccessException.class,()->controller.author(q,new MockHttpServletResponse(),null));verifyNoInteractions(service);}
 @Test void identityFailureCannotReachService()throws Exception{var q=request();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(identities).requireCurrent(any(),any());assertThrows(TenantAccessException.class,()->controller.author(q,new MockHttpServletResponse(),null));verifyNoInteractions(service);}
 @Test void readBodyAndMalformedIdentifiersRejected()throws Exception{var q=request();q.setContent(new byte[]{1});assertThrows(SchemaRejection.class,()->controller.read(q,new MockHttpServletResponse(),null,PolicyStorageFixture.id(1)));verifyNoInteractions(service);}
 @Test void exactOriginalReceiptRouteRemainsAuthenticated()throws Exception{var q=request();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(identities).requireCurrent(any(),any());assertThrows(TenantAccessException.class,()->controller.recover(q,new MockHttpServletResponse(),null,PolicyStorageFixture.id(1),PolicyStorageFixture.id(2)));verifyNoInteractions(service);}
}
