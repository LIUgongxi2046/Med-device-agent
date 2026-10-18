package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Collections;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class SimulationEvaluationDispatchControllerTest {
    @Test void reportsUnsettledFactWithoutReturningResultOrPermission() throws Exception {
        var service = mock(SimulationEvaluationDispatchService.class);
        var request = mock(HttpServletRequest.class); var response = mock(HttpServletResponse.class);
        when(request.getHeaders("X-Schema-Version")).thenReturn(Collections.enumeration(List.of("1.0.0")));
        var stream = mock(jakarta.servlet.ServletInputStream.class); when(request.getInputStream()).thenReturn(stream);
        byte[] input = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(stream.readNBytes(16_777_217)).thenReturn(input);
        when(service.execute(request, input)).thenReturn(new SimulationEvaluationDispatchService.Result(
            new SimulationInvocationService.View("id", "DISPATCHING", 1, "2"),
            new SimulationInvocationService.Fact("id", "receipt", false, "RECORDED_UNSETTLED")));
        var result = new SimulationEvaluationDispatchController(service).execute(request, response);
        assertEquals("DISPATCHING", result.path("status").textValue());
        assertEquals("RECORDED_UNSETTLED", result.path("fact_status").textValue());
        assertEquals("PENDING", result.path("settlement_status").textValue()); assertFalse(result.path("production_eligible").booleanValue());
        assertFalse(result.has("token")); assertFalse(result.has("permit")); assertFalse(result.has("result"));
        verify(response).setHeader("Cache-Control", "no-store");
    }
    @Test void missingDuplicateOrWrongSchemaCannotInvokeService() throws Exception {
        for (var values : List.of(List.<String>of(), List.of("1.0.0", "1.0.0"), List.of("invalid"))) {
            var request = mock(HttpServletRequest.class); var service = mock(SimulationEvaluationDispatchService.class);
            when(request.getHeaders("X-Schema-Version")).thenReturn(Collections.enumeration(values));
            assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class, () ->
                new SimulationEvaluationDispatchController(service).execute(request, mock(HttpServletResponse.class)));
            verifyNoInteractions(service); verify(request, never()).getInputStream();
        }
    }
    @Test void unknownExecutionUsesExistingPublishedUnknownCodeWithoutExceptionContent() throws Exception {
        var response = mock(HttpServletResponse.class); var bytes = new java.io.ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            public boolean isReady() { return true; }
            public void setWriteListener(jakarta.servlet.WriteListener listener) { }
            public void write(int value) { bytes.write(value); }
        });
        new SimulationEvaluationDispatchController(null).unknown(mock(SimulationLocalProcessBridge.BridgeFailure.class), response);
        verify(response).setStatus(409); assertTrue(bytes.toString().contains("INVOCATION_UNKNOWN"));
    }
}
