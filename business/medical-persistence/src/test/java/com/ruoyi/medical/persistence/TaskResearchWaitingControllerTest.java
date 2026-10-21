package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Controller header/current-session boundary only. CSRF token validity is enforced by the real
 * application SecurityFilterChain, not claimed by these direct-controller unit tests. */
class TaskResearchWaitingControllerTest {
    final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);
    final TaskResearchWaitingService service=mock(TaskResearchWaitingService.class);
    final PermissionSchema json=new PermissionSchema();
    final TaskResearchWaitingController controller=new TaskResearchWaitingController(identities,service,json);
    TaskResearchWaitingControllerTest()throws Exception { }
    final Authentication auth=mock(Authentication.class);final String waiting=TaskResearchWaitingServiceTest.id(90);
    @Test void readUsesCurrentIdentityAndNoStoreWithoutWorkerBypass()throws Exception {
        var q=request(false);var s=new MockHttpServletResponse();var identity=mock(ApplicationSessionIdentity.Verified.class);
        when(identities.requireCurrent(q,auth)).thenReturn(identity);when(service.read(identity,"1",waiting)).thenReturn(json.object().put("revision","1"));
        assertNotNull(controller.read(waiting,q,s,auth));verify(identities).requireCurrent(q,auth);verify(service).read(identity,"1",waiting);assertEquals("no-store",s.getHeader("Cache-Control"));
    }
    @Test void decisionRequiresSingleCsrfAndIdempotencyHeadersBeforeCallingWriter()throws Exception {
        var q=request(true);var s=new MockHttpServletResponse();q.addHeader("Idempotency-Key","original-owner-key-00001");q.setContent("{}".getBytes());
        assertThrows(SchemaRejection.class,()->controller.decide(waiting,q,s,auth));verifyNoInteractions(service);
        q.addHeader("X-CSRF-TOKEN","token-boundary-only");q.addHeader("Idempotency-Key","other-original-key-0001");
        assertThrows(SchemaRejection.class,()->controller.decide(waiting,q,s,auth));verifyNoInteractions(service);
    }
    @Test void recoveryRejectsNewIdempotencyHeaderAndReadRejectsQueryOrBody()throws Exception {
        var q=request(true);q.addHeader("X-CSRF-TOKEN","token-boundary-only");q.addHeader("Idempotency-Key","new-recovery-key-00001");q.setContent("{}".getBytes());
        assertThrows(SchemaRejection.class,()->controller.recover(waiting,q,new MockHttpServletResponse(),auth));
        var read=request(false);read.setQueryString("actor=arbitrary");assertThrows(SchemaRejection.class,()->controller.read(waiting,read,new MockHttpServletResponse(),auth));verifyNoInteractions(service);
    }
    MockHttpServletRequest request(boolean post){var q=new MockHttpServletRequest(post?"POST":"GET","/api/work-items/"+waiting+"/research");q.addHeader("X-Schema-Version","1.0.0");q.addHeader("X-Context-Revision","1");return q;}
}
