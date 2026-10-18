package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SimulationLocalRecoveryServiceTest {
    final SimulationInvocationSchema schema = new SimulationInvocationSchema();
    final SimulationInvocationService ledger = mock(SimulationInvocationService.class);
    final HttpServletRequest http = mock(HttpServletRequest.class);
    final String tenant = "00000000-0000-0000-0000-000000000002";
    final String invocation;
    final com.fasterxml.jackson.databind.JsonNode original;
    SimulationLocalRecoveryServiceTest() throws Exception {
        original = schema.request(resource("admission"));
        invocation = original.path("binding").path("identity").path("invocation_id").textValue();
        when(ledger.originalFactTransaction(eq(http), eq(tenant), eq(invocation), any())).thenAnswer(call -> {
            SimulationInvocationService.FactOperation<?> operation = call.getArgument(3);
            return operation.apply(original, Map.of());
        });
    }
    byte[] resource(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/simulation-ledger/" + name + ".json")) { return input.readAllBytes(); }
    }
    @Test void originalServiceCommitPrecedesPrivateReadonlyPermitAndFactIsReauthenticated() throws Exception {
        var count = new AtomicInteger();
        var bridge = spy(new SimulationLocalProcessBridge(new SimulationLocalProcessBridge.Configuration(Path.of("/trusted/python"),
            Path.of("/trusted/project"), Path.of("/private/store"), 16_777_216, Duration.ofSeconds(30)), schema,
            (config, input) -> { throw new AssertionError("Execution is forbidden during recovery"); },
            (config, input) -> {
                count.incrementAndGet(); assertEquals(schema.canonical(original), new String(input, StandardCharsets.UTF_8));
                var response = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version", "1.0.0").put("replayed", true);
                response.put("result_base64", Base64.getEncoder().encodeToString(resource("result")));
                response.put("provenance_base64", Base64.getEncoder().encodeToString(resource("provenance")));
                return schema.canonical(response).getBytes(StandardCharsets.UTF_8);
            }));
        var expected = new SimulationInvocationService.Fact(invocation, "receipt", false, "RECORDED_UNSETTLED");
        when(ledger.recordLateFact(eq(http), eq(tenant), eq(invocation), any(), any())).thenReturn(expected);
        assertEquals(expected, new SimulationLocalRecoveryService(ledger, bridge, schema).reconcile(http, tenant, invocation));
        var permit = ArgumentCaptor.forClass(SimulationLocalRecoveryService.RecoveryPermit.class);
        var ordered = inOrder(ledger, bridge);
        ordered.verify(ledger).originalFactTransaction(eq(http), eq(tenant), eq(invocation), any());
        ordered.verify(bridge).reconcile(permit.capture());
        ordered.verify(ledger).recordLateFact(eq(http), eq(tenant), eq(invocation), any(), any());
        assertEquals(1, count.get()); assertThrows(IllegalStateException.class, () -> bridge.reconcile(permit.getValue()));
        verify(ledger, never()).reserve(any(), any(byte[].class)); verify(ledger, never()).beginSend(any(), any(byte[].class));
    }
    @Test void failedCommitOrRevokedOriginalAuthorityNeverCreatesRecoveryPermission() throws Exception {
        var bridge = mock(SimulationLocalProcessBridge.class);
        doAnswer(call -> {
            SimulationInvocationService.FactOperation<?> operation = call.getArgument(3);
            operation.apply(original, Map.of()); throw new IllegalStateException("commit uncertain");
        }).when(ledger).originalFactTransaction(eq(http), eq(tenant), eq(invocation), any());
        assertThrows(IllegalStateException.class, () -> new SimulationLocalRecoveryService(ledger, bridge, schema).reconcile(http, tenant, invocation));
        verifyNoInteractions(bridge); verify(ledger, never()).recordLateFact(any(), any(), any(), any(), any());
    }
    @Test void recoveryCommandCanOnlyAddReadonlyModeToFixedIsolatedCommand() {
        var config = new SimulationLocalProcessBridge.Configuration(Path.of("/trusted/python"), Path.of("/trusted/project"),
            Path.of("/private/store"), 4096, Duration.ofSeconds(30));
        var args = config.command(true);
        assertEquals("--reconcile-only", args.get(args.size() - 1));
        assertEquals(config.command(), args.subList(0, args.size() - 1));
        for (var constructor : SimulationLocalRecoveryService.RecoveryPermit.class.getDeclaredConstructors())
            assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }
}
