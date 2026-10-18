package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentRevisionAssignmentQueueControllerTest {
    @Test void duplicateHeadersUnknownParametersAndBodiesNeverReachIdentityOrQueue() {
        var identities=mock(ApplicationSessionIdentity.class);var service=mock(DocumentRevisionAssignmentQueueService.class);
        var controller=new DocumentRevisionAssignmentQueueController(identities,service,mock(DocumentReviewReturnController.class));
        var duplicate=request();duplicate.addHeader("X-Context-Revision","2");
        var unknown=request();unknown.addParameter("assignee_id","someone-else");
        var repeated=request();repeated.addParameter("limit","1","2");
        var body=request();body.setContent("{}".getBytes());
        for(var request:java.util.List.of(duplicate,unknown,repeated,body))
            assertThrows(Exception.class,()->controller.read(request,new MockHttpServletResponse(),null));
        verifyNoInteractions(identities,service);
    }
    private MockHttpServletRequest request(){var r=new MockHttpServletRequest();r.addHeader("X-Schema-Version","1.0.0");r.addHeader("X-Context-Revision","1");return r;}
}
