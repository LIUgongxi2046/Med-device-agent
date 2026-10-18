package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentAuthoringControllerTest {
 final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);final AgentAuthoringOperations operations=mock(AgentAuthoringOperations.class);final Authentication authentication=mock(Authentication.class);final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
 final AgentAuthoringController controller=new AgentAuthoringController(identities,operations);
 MockHttpServletRequest request(){var q=new MockHttpServletRequest();q.addHeader("X-Schema-Version","1.0.0");q.addHeader("X-Context-Revision","7");q.addHeader("Idempotency-Key","author-operation-key-001");q.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));return q;}
 @Test void verifiesSessionBeforeReadingBoundedBody()throws Exception{var q=spy(request());var response=new MockHttpServletResponse();when(identities.requireCurrent(q,authentication)).thenReturn(subject);var json=new PermissionSchema();when(operations.write(eq(subject),eq("7"),eq("author-operation-key-001"),any(),eq("FORMAL_CAPABILITY_AUTHOR"))).thenReturn(json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),"1",json.object())));controller.capability(q,response,authentication);var order=inOrder(identities,q);order.verify(identities).requireCurrent(q,authentication);order.verify(q).getInputStream();assertEquals("no-store",response.getHeader("Cache-Control"));}
 @Test void deniedSessionNeverReadsRequestBody()throws Exception{var q=spy(request());when(identities.requireCurrent(q,authentication)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->controller.capability(q,new MockHttpServletResponse(),authentication));verify(q,never()).getInputStream();verifyNoInteractions(operations);}
 @Test void duplicateContextHeaderRejected()throws Exception{var q=request();q.addHeader("X-Context-Revision","8");assertThrows(SchemaRejection.class,()->controller.capability(q,new MockHttpServletResponse(),authentication));verifyNoInteractions(identities,operations);}
 @Test void excessiveBodyRejectedAfterIdentityBeforeService()throws Exception{var q=request();q.setContent(new byte[AgentPublicationSchema.MAXIMUM_BYTES+1]);when(identities.requireCurrent(q,authentication)).thenReturn(subject);assertEquals(ErrorCode.FILE_TOO_LARGE,assertThrows(TenantAccessException.class,()->controller.capability(q,new MockHttpServletResponse(),authentication)).code());verifyNoInteractions(operations);}
 @Test void detailRejectsUnexpectedBody()throws Exception{assertThrows(SchemaRejection.class,()->controller.read(request(),new MockHttpServletResponse(),authentication,"00000000-0000-4000-8000-000000000001"));verifyNoInteractions(identities,operations);}
 @Test void queryIsReadOnlyAndDoesNotRequireOrCreateIdempotencyOperation()throws Exception{var q=request();q.removeHeader("Idempotency-Key");when(identities.requireCurrent(q,authentication)).thenReturn(subject);var json=new PermissionSchema();when(operations.query(eq(subject),eq("7"),any())).thenReturn(json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),"1",json.object())));controller.query(q,new MockHttpServletResponse(),authentication);verify(operations).query(eq(subject),eq("7"),any());verify(operations,never()).write(any(),anyString(),anyString(),any(),anyString());}
 @Test void noStatisticsPolicyWriteRouteIsRegistered(){for(var method:AgentAuthoringController.class.getDeclaredMethods()){var post=method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);if(post!=null)for(var route:post.value())assertFalse(route.contains("acceptance-policy")||route.contains("acceptance-policies"));}}
}
