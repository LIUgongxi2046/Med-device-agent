package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Deferred by user instruction: HTTP routing/headers only, no services or database started. */
final class KnowledgeFeedbackControllerTest {
    @Test void closeRoutesOnlyToKnowledgeFeedbackAndRetainsExplicitBody()throws Exception {
        var service=mock(KnowledgeFeedbackService.class);var identity=mock(ApplicationSessionIdentity.class);
        var json=mock(PermissionSchema.class);var controller=new KnowledgeFeedbackController(service,identity,json);
        var request=mock(HttpServletRequest.class);var response=mock(HttpServletResponse.class);var auth=mock(Authentication.class);
        var subject=mock(ApplicationSessionIdentity.Verified.class);
        when(request.getHeaders("X-Schema-Version")).thenReturn(Collections.enumeration(java.util.List.of("1.0.0")));
        when(request.getHeaders("X-Context-Revision")).thenReturn(Collections.enumeration(java.util.List.of("7")));
        when(request.getHeaders("Idempotency-Key")).thenReturn(Collections.enumeration(java.util.List.of("knowledge-close-key-001")));
        when(identity.requireCurrent(request,auth)).thenReturn(subject);
        byte[] body="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);String id="00000000-0000-4000-8000-000000000001";
        when(service.close(subject,"7",id,"knowledge-close-key-001",body)).thenReturn(JsonNodeFactory.instance.objectNode());
        controller.close(request,response,auth,id,body);
        verify(service).close(subject,"7",id,"knowledge-close-key-001",body);
        verify(response).setHeader("Cache-Control","no-store");verify(response).setHeader("X-Schema-Version","1.0.0");
    }
    @Test void unsupportedHeaderNeverInvokesFeedbackService()throws Exception {
        var service=mock(KnowledgeFeedbackService.class);var identity=mock(ApplicationSessionIdentity.class);
        var controller=new KnowledgeFeedbackController(service,identity,mock(PermissionSchema.class));
        var request=mock(HttpServletRequest.class);when(request.getHeaders("X-Schema-Version")).thenReturn(Collections.enumeration(java.util.List.of("2.0.0")));
        assertThrows(SchemaRejection.class,()->controller.create(request,mock(HttpServletResponse.class),mock(Authentication.class),new byte[0]));
        verifyNoInteractions(service,identity);
    }
}
