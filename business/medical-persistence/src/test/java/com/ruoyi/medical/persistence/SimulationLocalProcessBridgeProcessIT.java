package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Objects;
import java.util.Map;
import java.util.TreeMap;
import java.util.Base64;
import jakarta.servlet.http.HttpServletRequest;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Explicit local-process component check. Needs installed Python package, not a server/DB. */
class SimulationLocalProcessBridgeProcessIT {
    @TempDir Path temporary;
    private byte[] fixture(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/simulation-ledger/" + name + ".json")) {
            return stream.readAllBytes();
        }
    }
    @Test void installedIsolatedPythonReturnsVerifiedOriginalFactThroughActualJavaPipes() throws Exception {
        Path python = Path.of(Objects.requireNonNull(System.getProperty("simulation.bridge.python"),
            "Explicit installed Python required for local process check"));
        Path project = Path.of(Objects.requireNonNull(System.getProperty("simulation.bridge.project"),
            "Explicit project schema root required for local process check"));
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rwx------"));
        var bridge = new SimulationLocalProcessBridge(new SimulationLocalProcessBridge.Configuration(
            python, project, temporary, 16_777_216, Duration.ofSeconds(30)));
        // Only the permit boundary is a test double; Python process, pipes, storage and hashes are real.
        var permit = mock(SimulationInvocationService.DispatchPermit.class);
        when(permit.consume()).thenReturn(new String(fixture("admission"), StandardCharsets.UTF_8))
            .thenThrow(new IllegalStateException("SIMULATION_DISPATCH_ALREADY_CONSUMED"));
        var result = bridge.execute(permit);
        assertArrayEquals(fixture("result"), result.resultBytes());
        assertArrayEquals(fixture("provenance"), result.provenanceBytes());
        assertFalse(result.replayed());
        assertThrows(IllegalStateException.class, () -> bridge.execute(permit));
        try (var paths = Files.list(temporary)) { assertEquals(1, paths.count()); }
    }
    private SimulationLocalProcessBridge realBridge(Path store) throws Exception {
        Files.setPosixFilePermissions(store, PosixFilePermissions.fromString("rwx------"));
        return new SimulationLocalProcessBridge(new SimulationLocalProcessBridge.Configuration(
            Path.of(Objects.requireNonNull(System.getProperty("simulation.bridge.python"))),
            Path.of(Objects.requireNonNull(System.getProperty("simulation.bridge.project"))),
            store, 16_777_216, Duration.ofSeconds(30)));
    }
    /** Includes directory names and every file's bytes, so added journals/locks are visible. */
    private Map<String, String> snapshot(Path root) throws Exception {
        var result = new TreeMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                result.put(root.relativize(path).toString(), Files.isDirectory(path) ? "DIRECTORY"
                    : Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            }
        }
        return result;
    }
    @Test void installedPythonReconcilesOriginalJournalReadonlyThroughServiceCreatedPermit() throws Exception {
        var schema = new SimulationInvocationSchema();
        var original = schema.request(fixture("admission"));
        String tenant = original.path("binding").path("identity").path("tenant_id").textValue();
        String invocation = original.path("binding").path("identity").path("invocation_id").textValue();
        var bridge = spy(realBridge(temporary));
        // Dispatch authority is a deliberate double; actual Python execution creates the journal.
        var dispatch = mock(SimulationInvocationService.DispatchPermit.class);
        when(dispatch.consume()).thenReturn(schema.canonical(original));
        var executed = bridge.execute(dispatch);
        assertFalse(executed.replayed());
        var before = snapshot(temporary);
        assertTrue(before.keySet().stream().anyMatch(path -> path.endsWith("commit.json")));

        // No database, mTLS or grant verification is claimed: only this ledger boundary is doubled.
        var ledger = mock(SimulationInvocationService.class);
        var http = mock(HttpServletRequest.class);
        when(ledger.originalFactTransaction(eq(http), eq(tenant), eq(invocation), any())).thenAnswer(call -> {
            SimulationInvocationService.FactOperation<?> operation = call.getArgument(3);
            return operation.apply(original, Map.of());
        });
        var expected = new SimulationInvocationService.Fact(invocation, "receipt", false, "RECORDED_UNSETTLED");
        when(ledger.recordLateFact(eq(http), eq(tenant), eq(invocation), any(), any())).thenReturn(expected);
        doAnswer(call -> {
            var recovered = (SimulationLocalProcessBridge.LocalFact) call.callRealMethod();
            assertTrue(recovered.replayed());
            assertArrayEquals(executed.resultBytes(), recovered.resultBytes());
            assertArrayEquals(executed.provenanceBytes(), recovered.provenanceBytes());
            return recovered;
        }).when(bridge).reconcile(any());

        assertEquals(expected, new SimulationLocalRecoveryService(ledger, bridge, schema).reconcile(http, tenant, invocation));
        var permit = ArgumentCaptor.forClass(SimulationLocalRecoveryService.RecoveryPermit.class);
        var result = ArgumentCaptor.forClass(byte[].class);
        var provenance = ArgumentCaptor.forClass(byte[].class);
        var order = inOrder(ledger, bridge);
        order.verify(ledger).originalFactTransaction(eq(http), eq(tenant), eq(invocation), any());
        order.verify(bridge).reconcile(permit.capture());
        order.verify(ledger).recordLateFact(eq(http), eq(tenant), eq(invocation), result.capture(), provenance.capture());
        assertArrayEquals(fixture("result"), result.getValue());
        assertArrayEquals(fixture("provenance"), provenance.getValue());
        assertEquals(before, snapshot(temporary));
        assertThrows(IllegalStateException.class, () -> bridge.reconcile(permit.getValue()));
        assertEquals(before, snapshot(temporary));
        verify(bridge, times(1)).execute(dispatch);
        verify(ledger, never()).reserve(any(), any(byte[].class));
        verify(ledger, never()).beginSend(any(), any(byte[].class));
        verifyNoMoreInteractions(ledger);
    }
    @Test void installedPythonReadonlyRecoveryRejectsMissingJournalWithoutCreatingDirectories() throws Exception {
        var schema = new SimulationInvocationSchema();
        var original = schema.request(fixture("admission"));
        String tenant = original.path("binding").path("identity").path("tenant_id").textValue();
        String invocation = original.path("binding").path("identity").path("invocation_id").textValue();
        var bridge = spy(realBridge(temporary));
        var before = snapshot(temporary);
        assertEquals(1, before.size()); // Only the already-created private store root exists.
        var ledger = mock(SimulationInvocationService.class);
        var http = mock(HttpServletRequest.class);
        when(ledger.originalFactTransaction(eq(http), eq(tenant), eq(invocation), any())).thenAnswer(call -> {
            SimulationInvocationService.FactOperation<?> operation = call.getArgument(3);
            return operation.apply(original, Map.of());
        });
        assertThrows(SimulationLocalProcessBridge.BridgeFailure.class,
            () -> new SimulationLocalRecoveryService(ledger, bridge, schema).reconcile(http, tenant, invocation));
        assertEquals(before, snapshot(temporary));
        verify(bridge).reconcile(any());
        verify(bridge, never()).execute(any());
        verify(ledger).originalFactTransaction(eq(http), eq(tenant), eq(invocation), any());
        verify(ledger, never()).reserve(any(), any(byte[].class));
        verify(ledger, never()).beginSend(any(), any(byte[].class));
        verify(ledger, never()).recordLateFact(any(), any(), any(), any(), any());
        verifyNoMoreInteractions(ledger);
    }

}
