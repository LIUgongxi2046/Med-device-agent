package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentReviewReturnControllerTest {
    private final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);
    private final DocumentReviewReturnService service=mock(DocumentReviewReturnService.class);
    private final DocumentReviewReturnController controller=new DocumentReviewReturnController(identities,service,new PermissionSchema());
    DocumentReviewReturnControllerTest() throws Exception { }
    @Test void missingDuplicateQueryAndBodyHeadersAreRejectedBeforeIdentityOrBusinessWork() {
        var response=new MockHttpServletResponse(); var request=request(); request.addHeader("X-Document-Revision",DocumentReviewReturnSchemaTest.id(2));
        assertThrows(Exception.class,()->controller.basis(request,response,null,DocumentReviewReturnSchemaTest.id(1)));
        var query=request(); query.setQueryString("all=true");
        assertThrows(Exception.class,()->controller.history(query,response,null,DocumentReviewReturnSchemaTest.id(1)));
        var body=request(); body.setContent("{}".getBytes());
        assertThrows(Exception.class,()->controller.assignments(body,response,null,DocumentReviewReturnSchemaTest.id(1)));
        verifyNoInteractions(identities,service);
    }
    @Test void authenticationFailureNeverReadsReviewHistory() throws Exception {
        var request=request(); when(identities.requireCurrent(request,null)).thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->controller.history(request,new MockHttpServletResponse(),null,DocumentReviewReturnSchemaTest.id(1)));
        verifyNoInteractions(service);
    }
    private MockHttpServletRequest request() {
        var request=new MockHttpServletRequest(); request.addHeader("X-Schema-Version","1.0.0");
        request.addHeader("X-Context-Revision","1"); request.addHeader("X-Document-Revision",DocumentReviewReturnSchemaTest.id(2)); return request;
    }
}
