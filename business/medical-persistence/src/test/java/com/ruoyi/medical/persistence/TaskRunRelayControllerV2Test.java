package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskRunRelayControllerV2Test {
    @Test void rejectsBrowserBeforeParsingOrRelay(){
        var relay=mock(TaskRunOutboxRelayV2.class);var identities=mock(ServiceCertificateIdentity.class);
        var controller=new TaskRunRelayControllerV2(relay,identities);var request=new MockHttpServletRequest();request.addHeader("Cookie","session=not-authority");
        assertThrows(TenantAccessException.class,()->controller.relay(request,new MockHttpServletResponse()));verifyNoInteractions(relay,identities);
    }
    @Test void rejectsMissingCertificateBeforeRelay(){
        var relay=mock(TaskRunOutboxRelayV2.class);var identities=mock(ServiceCertificateIdentity.class);
        var controller=new TaskRunRelayControllerV2(relay,identities);var request=new MockHttpServletRequest();
        assertThrows(TenantAccessException.class,()->controller.relay(request,new MockHttpServletResponse()));verifyNoInteractions(relay);
    }
    @Test void originalListAcceptsExplicitNullCursorAndReturnsClosedEmptyPage()throws Exception{
        var relay=mock(TaskRunOutboxRelayV2.class);var identities=mock(ServiceCertificateIdentity.class);var request=new MockHttpServletRequest();
        when(identities.require(request)).thenReturn(mock(ServiceCertificateIdentity.Verified.class));
        request.setContent(("{\"schema_version\":\"2.0.0\",\"operation\":\"LIST_ORIGINALS\",\"tenant_id\":\""+TaskRunOutboxRelayV2Test.TENANT+"\",\"after_event_id\":null}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(relay.candidates(request,TaskRunOutboxRelayV2Test.TENANT,null)).thenReturn(java.util.List.of());
        var result=new TaskRunRelayControllerV2(relay,identities).relay(request,new MockHttpServletResponse());
        assertEquals(0,result.required("event_ids").size());assertTrue(result.required("next_after_event_id").isNull());
    }
}
