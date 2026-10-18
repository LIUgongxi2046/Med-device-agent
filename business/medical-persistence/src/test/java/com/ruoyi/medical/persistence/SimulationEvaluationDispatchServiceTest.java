package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class SimulationEvaluationDispatchServiceTest {
    final SimulationInvocationSchema schema = new SimulationInvocationSchema();
    final SimulationInvocationService ledger = mock(SimulationInvocationService.class);
    final HttpServletRequest http = mock(HttpServletRequest.class);
    final SimulationInvocationService.DispatchPermit permit = mock(SimulationInvocationService.DispatchPermit.class);
    final byte[] input = resource("admission");
    final SimulationInvocationService.View view;
    SimulationEvaluationDispatchServiceTest() throws Exception {
        String id = schema.request(input).path("binding").path("identity").path("invocation_id").textValue();
        view = new SimulationInvocationService.View(id, "DISPATCHING", 1, "2");
        when(permit.consume()).thenReturn(new String(input, StandardCharsets.UTF_8))
            .thenThrow(new IllegalStateException("SIMULATION_DISPATCH_ALREADY_CONSUMED"));
        when(ledger.beginSend(http, input)).thenReturn(new SimulationInvocationService.Dispatch(view, permit));
    }
    private byte[] resource(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/simulation-ledger/" + name + ".json")) { return stream.readAllBytes(); }
    }
    private SimulationLocalProcessBridge bridge(boolean fail) throws Exception {
        return new SimulationLocalProcessBridge(new SimulationLocalProcessBridge.Configuration(Path.of("/trusted/python"),
            Path.of("/trusted/project"), Path.of("/private/store"), 16_777_216, Duration.ofSeconds(30)), schema, (config, bytes) -> {
                if (fail) throw new java.io.IOException("redacted");
                var node = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version", "1.0.0").put("replayed", false);
                node.put("result_base64", Base64.getEncoder().encodeToString(resource("result")));
                node.put("provenance_base64", Base64.getEncoder().encodeToString(resource("provenance")));
                return schema.canonical(node).getBytes(StandardCharsets.UTF_8);
            });
    }
    @Test void reservedCommittedDispatchThenOriginalServiceFactInOrder() throws Exception {
        var bridge = spy(bridge(false));
        var target = new SimulationEvaluationDispatchService(ledger, bridge, schema);
        var request = schema.request(input); var id = request.path("binding").path("identity");
        var expected = new SimulationInvocationService.Fact(view.invocationId(), "receipt", false, "RECORDED_UNSETTLED");
        when(ledger.recordLateFact(eq(http), eq(id.path("tenant_id").textValue()), eq(view.invocationId()), any(), any())).thenReturn(expected);
        var result = target.execute(http, input);
        assertEquals(view, result.invocation()); assertEquals(expected, result.fact());
        var ordered = inOrder(ledger, bridge);
        ordered.verify(ledger).reserve(http, input); ordered.verify(ledger).beginSend(http, input);
        ordered.verify(bridge).execute(permit);
        ordered.verify(ledger).recordLateFact(eq(http), eq(id.path("tenant_id").textValue()), eq(view.invocationId()),
            aryEq(resource("result")), aryEq(resource("provenance")));
        verify(ledger, never()).cancel(any(), any(byte[].class));
    }
    @Test void replayCannotDispatchOrFabricateFact() throws Exception {
        when(ledger.beginSend(http, input)).thenReturn(new SimulationInvocationService.Dispatch(view, null));
        var bridge = mock(SimulationLocalProcessBridge.class);
        var result = new SimulationEvaluationDispatchService(ledger, bridge, schema).execute(http, input);
        assertEquals(view, result.invocation()); assertNull(result.fact()); verifyNoInteractions(bridge);
        verify(ledger, never()).recordLateFact(any(), any(), any(), any(), any());
    }
    @Test void admissionOrCommitFailureNeverStartsBridge() throws Exception {
        var bridge = mock(SimulationLocalProcessBridge.class);
        when(ledger.beginSend(http, input)).thenThrow(new IllegalStateException("commit uncertain"));
        assertThrows(IllegalStateException.class, () -> new SimulationEvaluationDispatchService(ledger, bridge, schema).execute(http, input));
        verifyNoInteractions(bridge);
        when(ledger.reserve(http, input)).thenThrow(new SecurityException("denied"));
        assertThrows(SecurityException.class, () -> new SimulationEvaluationDispatchService(ledger, bridge, schema).execute(http, input));
        verifyNoInteractions(bridge);
    }
    @Test void executionFailureObservesUnknownAndNeverReleasesOrRetries() throws Exception {
        var target = new SimulationEvaluationDispatchService(ledger, bridge(true), schema);
        assertThrows(SimulationLocalProcessBridge.BridgeFailure.class, () -> target.execute(http, input));
        verify(ledger).observe(http, input, "UNKNOWN"); verify(permit).consume();
        verify(ledger, never()).cancel(any(), any(byte[].class));
        verify(ledger, never()).recordLateFact(any(), any(), any(), any(), any());
    }
    @Test void RevokedObservationDoesNotEraseOriginalUnknownFailure() throws Exception {
        when(ledger.observe(http, input, "UNKNOWN")).thenThrow(new SecurityException("revoked"));
        var target = new SimulationEvaluationDispatchService(ledger, bridge(true), schema);
        var failure = assertThrows(SimulationLocalProcessBridge.BridgeFailure.class, () -> target.execute(http, input));
        assertEquals("SIMULATION_BRIDGE_UNKNOWN", failure.getMessage()); verify(permit).consume();
        verify(ledger, never()).cancel(any(), any(byte[].class));
    }
    private static byte[] aryEq(byte[] bytes) { return org.mockito.AdditionalMatchers.aryEq(bytes); }
}
