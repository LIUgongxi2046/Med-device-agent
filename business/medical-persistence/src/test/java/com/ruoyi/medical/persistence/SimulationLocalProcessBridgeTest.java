package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SimulationLocalProcessBridgeTest {
    private final SimulationInvocationSchema schema = new SimulationInvocationSchema();
    private final SimulationLocalProcessBridge.Configuration config = new SimulationLocalProcessBridge.Configuration(
        Path.of("/trusted/python"), Path.of("/trusted/project"), Path.of("/private/store"), 16_777_216, Duration.ofSeconds(30));
    SimulationLocalProcessBridgeTest() throws Exception { }
    private byte[] bytes(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/simulation-ledger/" + name + ".json")) {
            return stream.readAllBytes();
        }
    }
    private SimulationInvocationService.DispatchPermit permit() throws Exception {
        var permit = mock(SimulationInvocationService.DispatchPermit.class);
        when(permit.consume()).thenReturn(new String(bytes("admission"), StandardCharsets.UTF_8))
            .thenThrow(new IllegalStateException("SIMULATION_DISPATCH_ALREADY_CONSUMED"));
        return permit;
    }
    private byte[] response() throws Exception {
        var json = new ObjectMapper().createObjectNode();
        json.put("schema_version", "1.0.0").put("replayed", false);
        json.put("result_base64", Base64.getEncoder().encodeToString(bytes("result")));
        json.put("provenance_base64", Base64.getEncoder().encodeToString(bytes("provenance")));
        return new ObjectMapper().writeValueAsBytes(json);
    }
    @Test void consumesCommittedPermitExactlyOnceAndValidatesOriginalFact() throws Exception {
        var calls = new AtomicInteger();
        var bridge = new SimulationLocalProcessBridge(config, schema, (configuration, input) -> {
            calls.incrementAndGet(); assertArrayEquals(bytes("admission"), input); return response();
        });
        var permit = permit(); var fact = bridge.execute(permit);
        assertArrayEquals(bytes("result"), fact.resultBytes());
        assertArrayEquals(bytes("provenance"), fact.provenanceBytes());
        assertFalse(fact.replayed()); assertFalse(fact.toString().contains("SIM-A"));
        byte[] changed = fact.resultBytes(); changed[0] = 0;
        assertArrayEquals(bytes("result"), fact.resultBytes());
        assertThrows(IllegalStateException.class, () -> bridge.execute(permit));
        assertEquals(1, calls.get());
    }
    @Test void processFailureRemainsUnknownAndCannotBeRetriedWithPermit() throws Exception {
        var calls = new AtomicInteger();
        var bridge = new SimulationLocalProcessBridge(config, schema, (configuration, input) -> {
            calls.incrementAndGet(); throw new java.io.IOException("PRIVATE_CONTENT");
        });
        var permit = permit();
        var failure = assertThrows(SimulationLocalProcessBridge.BridgeFailure.class, () -> bridge.execute(permit));
        assertEquals("SIMULATION_BRIDGE_UNKNOWN", failure.getMessage()); assertNull(failure.getCause());
        assertThrows(IllegalStateException.class, () -> bridge.execute(permit)); assertEquals(1, calls.get());
    }
    @Test void fabricatedResultAndUnexpectedEnvelopeFieldsAreRejected() throws Exception {
        var mapper = new ObjectMapper();
        for (boolean extra : new boolean[]{false, true}) {
            var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(response());
            if (extra) envelope.put("artifact_id", "invented");
            else envelope.put("result_base64", Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8)));
            var bridge = new SimulationLocalProcessBridge(config, schema, (configuration, input) -> mapper.writeValueAsBytes(envelope));
            assertThrows(SimulationLocalProcessBridge.BridgeFailure.class, () -> bridge.execute(permit()));
        }
    }
    @Test void duplicateTrailingInvalidAndOversizeOutputRejected() throws Exception {
        byte[] valid = response();
        for (byte[] output : new byte[][]{null, new byte[0], "{} {}".getBytes(StandardCharsets.UTF_8),
            "{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8), new byte[]{(byte)255}}) {
            var bridge = new SimulationLocalProcessBridge(config, schema, (configuration, input) -> output);
            assertThrows(SimulationLocalProcessBridge.BridgeFailure.class, () -> bridge.execute(permit()));
        }
        var small = new SimulationLocalProcessBridge.Configuration(config.pythonExecutable(), config.projectRoot(),
            config.storeDirectory(), 1, config.deadline());
        var bridge = new SimulationLocalProcessBridge(small, schema, (configuration, input) -> valid);
        assertThrows(SimulationLocalProcessBridge.BridgeFailure.class, () -> bridge.execute(permit()));
    }
    @Test void commandIsExplicitIsolatedModuleWithoutShellOrRequestArguments() {
        assertEquals(java.util.List.of("/trusted/python", "-I", "-m", "medical_model_gateway.simulation_bridge",
            "--project-root", "/trusted/project", "--store-directory", "/private/store", "--maximum-record-bytes", "16777216"), config.command());
        assertThrows(IllegalArgumentException.class, () -> new SimulationLocalProcessBridge.Configuration(
            Path.of("python"), config.projectRoot(), config.storeDirectory(), 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new SimulationLocalProcessBridge.Configuration(
            config.pythonExecutable(), config.projectRoot(), config.storeDirectory(), 16_777_217, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new SimulationLocalProcessBridge.Configuration(
            config.pythonExecutable(), config.projectRoot(), config.storeDirectory(), 1, Duration.ofMinutes(6)));
    }
}
