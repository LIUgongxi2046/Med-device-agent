package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

class SimulationFeatureAvailabilityControllerTest {
    private HttpServletRequest request() {
        var request = mock(HttpServletRequest.class);
        when(request.getHeaders("X-Schema-Version")).thenReturn(Collections.enumeration(List.of("1.0.0")));
        when(request.getHeaders("X-Context-Revision")).thenReturn(Collections.enumeration(List.of("7")));
        return request;
    }
    @Test void currentAuthenticatedIdentityPrecedesAvailabilityRead() throws Exception {
        var identities = mock(ApplicationSessionIdentity.class); var service = mock(SimulationFeatureAvailability.class);
        var subject = mock(ApplicationSessionIdentity.Verified.class); var request = request(); var response = mock(HttpServletResponse.class);
        var json = new PermissionSchema(); var data = json.object(); data.put("test", "isolated mocked service boundary");
        when(identities.requireCurrent(request, null)).thenReturn(subject); when(service.read(subject, "7")).thenReturn(data);
        var result = new SimulationFeatureAvailabilityController(identities, service, json).read(request, response, null);
        var order = inOrder(identities, service); order.verify(identities).requireCurrent(request, null); order.verify(service).read(subject, "7");
        assertEquals("7", result.required("resource_revision").textValue()); assertEquals(data, result.required("data"));
        verify(response).setHeader("Cache-Control", "no-store"); verify(response).setHeader("X-Schema-Version", "1.0.0");
        verify(response).setHeader("X-Request-Id", result.required("request_id").textValue());
    }
    @Test void unauthenticatedRequestCannotReadFlags() throws Exception {
        var identities = mock(ApplicationSessionIdentity.class); var service = mock(SimulationFeatureAvailability.class); var request = request();
        when(identities.requireCurrent(request, null)).thenThrow(new AuthenticationCredentialsNotFoundException("not authenticated"));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> new SimulationFeatureAvailabilityController(identities, service, new PermissionSchema()).read(request, mock(HttpServletResponse.class), null));
        verifyNoInteractions(service);
    }
    @Test void missingDuplicateSchemaQueryAndBodyAreRejectedBeforeIdentityRead() throws Exception {
        for (int mode = 0; mode < 5; mode++) {
            var identities = mock(ApplicationSessionIdentity.class); var service = mock(SimulationFeatureAvailability.class); var request = request();
            if (mode == 0) when(request.getHeaders("X-Schema-Version")).thenReturn(Collections.emptyEnumeration());
            if (mode == 1) when(request.getHeaders("X-Schema-Version")).thenReturn(Collections.enumeration(List.of("1.0.0", "1.0.0")));
            if (mode == 2) when(request.getHeaders("X-Context-Revision")).thenReturn(Collections.enumeration(List.of("7", "7")));
            if (mode == 3) when(request.getQueryString()).thenReturn("property=secret");
            if (mode == 4) when(request.getContentLengthLong()).thenReturn(1L);
            var controller = new SimulationFeatureAvailabilityController(identities, service, new PermissionSchema());
            assertThrows(SchemaRejection.class, () -> controller.read(request, mock(HttpServletResponse.class), null));
            verifyNoInteractions(identities, service);
        }
    }
}
