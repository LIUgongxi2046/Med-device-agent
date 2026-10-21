package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TaskNodeProducerRejectionBoundaryTest {
 TaskNodeProducerRejectionBoundaryTest() throws Exception { }
 final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);final TaskNodeProducerService service=mock(TaskNodeProducerService.class);
 final TaskNodeProducerController controller=new TaskNodeProducerController(identities,service,new PermissionSchema());
 final String task="11111111-1111-4111-8111-111111111111",key="first-dispatch-key-000001";final byte[] body="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
 MockHttpServletRequest request(){var q=new MockHttpServletRequest("POST","/api/tasks/"+task+"/node-producer-rules");q.addHeader("X-Schema-Version","1.0.0");q.addHeader("X-Context-Revision","1");q.addHeader("X-CSRF-TOKEN","boundary-test-only");q.addHeader("Idempotency-Key",key);q.setContent(body);return q;}
 @Test void directBusinessRejectionBindsOnlyThisAttempt()throws Exception {
  var q=request();var identity=mock(ApplicationSessionIdentity.Verified.class);when(identities.requireCurrent(q,null)).thenReturn(identity);
  when(service.author(eq(identity),eq("1"),eq(task),eq(key),any())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));var s=new MockHttpServletResponse();
  assertThrows(TenantAccessException.class,()->controller.authorInput(task,q,s,null));assertEquals("REJECTED_THIS_ATTEMPT",s.getHeader("X-Task-Node-Producer-Outcome"));assertEquals(task,s.getHeader("X-Task-Node-Producer-Task"));assertEquals("AUTHOR",s.getHeader("X-Task-Node-Producer-Operation"));assertEquals(key,s.getHeader("X-Task-Node-Producer-Key"));assertEquals(WireValues.sha256(body),s.getHeader("X-Task-Node-Producer-Request-Sha256"));
 }
 @Test void nestedCommitOrTransportFailureDoesNotAssertRollback()throws Exception {
  var q=request();var identity=mock(ApplicationSessionIdentity.Verified.class);when(identities.requireCurrent(q,null)).thenReturn(identity);when(service.author(eq(identity),eq("1"),eq(task),eq(key),any())).thenThrow(new IllegalStateException("uncertain",new TenantAccessException(ErrorCode.ACCESS_DENIED)));var s=new MockHttpServletResponse();assertThrows(IllegalStateException.class,()->controller.authorInput(task,q,s,null));assertNull(s.getHeader("X-Task-Node-Producer-Outcome"));
 }
 @Test void nonWhitelistedIntegrityErrorsStayPending()throws Exception {
  var q=request();var identity=mock(ApplicationSessionIdentity.Verified.class);when(identities.requireCurrent(q,null)).thenReturn(identity);when(service.author(eq(identity),eq("1"),eq(task),eq(key),any())).thenThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH));var s=new MockHttpServletResponse();assertThrows(TenantAccessException.class,()->controller.authorInput(task,q,s,null));assertNull(s.getHeader("X-Task-Node-Producer-Outcome"));
 }
 @Test void directStaleAuthorityRejectionIsExplicitlyBound()throws Exception {
  var q=request();var identity=mock(ApplicationSessionIdentity.Verified.class);when(identities.requireCurrent(q,null)).thenReturn(identity);when(service.author(eq(identity),eq("1"),eq(task),eq(key),any())).thenThrow(new TenantAccessException(ErrorCode.SCOPE_STALE));var s=new MockHttpServletResponse();var failure=assertThrows(TenantAccessException.class,()->controller.authorInput(task,q,s,null));controller.failure(failure,s);assertEquals(409,s.getStatus());assertEquals("REJECTED_THIS_ATTEMPT",s.getHeader("X-Task-Node-Producer-Outcome"));assertEquals(WireValues.sha256(body),s.getHeader("X-Task-Node-Producer-Request-Sha256"));
 }

}
