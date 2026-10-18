package com.ruoyi.medical.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Once-only local pipe consumer; does not create SQL success, artifacts or settlement. */
public final class SimulationLocalProcessBridge {
    private static final int MAX_RECORD = 16_777_216;
    private final SimulationInvocationSchema schema;
    private final Configuration configuration;
    private final Runner runner;
    private final Runner recoveryRunner;

    /** Trusted deployment configuration only. None of these values comes from request JSON. */
    public record Configuration(Path pythonExecutable, Path projectRoot, Path storeDirectory,
                                int maximumRecordBytes, Duration deadline) {
        public Configuration {
            Objects.requireNonNull(pythonExecutable); Objects.requireNonNull(projectRoot);
            Objects.requireNonNull(storeDirectory); Objects.requireNonNull(deadline);
            if (!pythonExecutable.isAbsolute() || !projectRoot.isAbsolute() || !storeDirectory.isAbsolute()
                    || maximumRecordBytes < 1 || maximumRecordBytes > MAX_RECORD
                    || deadline.isNegative() || deadline.isZero() || deadline.compareTo(Duration.ofMinutes(5)) > 0)
                throw new IllegalArgumentException("SIMULATION_BRIDGE_CONFIGURATION_INVALID");
        }
        List<String> command() {
            return List.of(pythonExecutable.toString(), "-I", "-m", "medical_model_gateway.simulation_bridge",
                "--project-root", projectRoot.toString(), "--store-directory", storeDirectory.toString(),
                "--maximum-record-bytes", Integer.toString(maximumRecordBytes));
        }
        List<String> command(boolean recovery) {
            if (!recovery) return command();
            var arguments = new java.util.ArrayList<>(command()); arguments.add("--reconcile-only");
            return List.copyOf(arguments);
        }
    }

    public SimulationLocalProcessBridge(Configuration configuration) throws Exception {
        this(configuration, new SimulationInvocationSchema(),
            (config, input) -> runProcess(config, input, false), (config, input) -> runProcess(config, input, true));
    }
    SimulationLocalProcessBridge(Configuration configuration, SimulationInvocationSchema schema, Runner runner) {
        this(configuration, schema, runner, (config, input) -> { throw new IOException("SIMULATION_RECOVERY_NOT_CONFIGURED"); });
    }
    SimulationLocalProcessBridge(Configuration configuration, SimulationInvocationSchema schema, Runner runner, Runner recoveryRunner) {
        this.configuration = Objects.requireNonNull(configuration);
        this.schema = Objects.requireNonNull(schema); this.runner = Objects.requireNonNull(runner);
        this.recoveryRunner = Objects.requireNonNull(recoveryRunner);
    }

    /** Consumes before any process start. Every exception is ambiguous; callers must never resend. */
    public LocalFact execute(SimulationInvocationService.DispatchPermit permit) throws BridgeFailure {
        Objects.requireNonNull(permit);
        // A duplicate consume remains a programming error and cannot invoke a process.
        return invoke(permit.consume(), runner);
    }
    /** A distinct original-service proof permits only read-only journal reconciliation. */
    public LocalFact reconcile(SimulationLocalRecoveryService.RecoveryPermit permit) throws BridgeFailure {
        Objects.requireNonNull(permit);
        return invoke(permit.consume(), recoveryRunner);
    }
    private LocalFact invoke(String canonical, Runner operation) throws BridgeFailure {
        try {
            byte[] input = canonical.getBytes(StandardCharsets.UTF_8);
            var request = schema.request(input);
            byte[] output = operation.run(configuration, input);
            if (output == null || output.length == 0 || output.length > outputLimit(configuration))
                throw new IOException("SIMULATION_BRIDGE_RESPONSE_INVALID");
            var envelope = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build().readTree(output);
            Set<String> keys = new HashSet<>(); envelope.fieldNames().forEachRemaining(keys::add);
            if (!envelope.isObject() || !keys.equals(Set.of("schema_version", "result_base64", "provenance_base64", "replayed"))
                    || !"1.0.0".equals(envelope.path("schema_version").textValue())
                    || !envelope.path("replayed").isBoolean()
                    || !envelope.path("result_base64").isTextual() || !envelope.path("provenance_base64").isTextual())
                throw new IOException("SIMULATION_BRIDGE_RESPONSE_INVALID");
            byte[] result = decode(envelope.path("result_base64").textValue());
            byte[] provenance = decode(envelope.path("provenance_base64").textValue());
            schema.receipt(request, result, provenance);
            return new LocalFact(result, provenance, envelope.path("replayed").booleanValue());
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            // Do not retain arbitrary process exception messages or content in logs.
            throw new BridgeFailure();
        }
    }

    private byte[] decode(String value) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(value);
        if (bytes.length == 0 || bytes.length > configuration.maximumRecordBytes()
                || !Base64.getEncoder().encodeToString(bytes).equals(value))
            throw new IOException("SIMULATION_BRIDGE_RESPONSE_INVALID");
        return bytes;
    }

    public static final class LocalFact {
        private final byte[] result, provenance; private final boolean replayed;
        private LocalFact(byte[] result, byte[] provenance, boolean replayed) {
            this.result = result.clone(); this.provenance = provenance.clone(); this.replayed = replayed;
        }
        public byte[] resultBytes() { return result.clone(); }
        public byte[] provenanceBytes() { return provenance.clone(); }
        public boolean replayed() { return replayed; }
        @Override public String toString() { return "SimulationLocalFact[redacted,unsettled]"; }
    }
    public static final class BridgeFailure extends Exception {
        private BridgeFailure() { super("SIMULATION_BRIDGE_UNKNOWN"); }
    }
    @FunctionalInterface interface Runner { byte[] run(Configuration config, byte[] input) throws Exception; }

    private static byte[] runProcess(Configuration config, byte[] input, boolean recovery) throws Exception {
        validatePaths(config);
        var builder = new ProcessBuilder(config.command(recovery));
        builder.directory(config.projectRoot().toFile());
        builder.environment().clear(); builder.environment().put("LANG", "C.UTF-8");
        Process process = builder.start();
        var workers = Executors.newFixedThreadPool(3, runnable -> {
            var thread = new Thread(runnable, "simulation-local-pipe"); thread.setDaemon(true); return thread;
        });
        try {
            var stdout = workers.submit(() -> bounded(process.getInputStream(), outputLimit(config)));
            var stderr = workers.submit(() -> bounded(process.getErrorStream(), 4096));
            var stdin = workers.submit(() -> {
                try (var stream = process.getOutputStream()) { stream.write(input); }
                return Boolean.TRUE;
            });
            if (!process.waitFor(config.deadline().toMillis(), TimeUnit.MILLISECONDS))
                throw new IOException("SIMULATION_BRIDGE_TIMEOUT");
            stdin.get(1, TimeUnit.SECONDS);
            byte[] errors = stderr.get(1, TimeUnit.SECONDS);
            byte[] result = stdout.get(1, TimeUnit.SECONDS);
            if (process.exitValue() != 0 || errors.length != 0) throw new IOException("SIMULATION_BRIDGE_PROCESS_FAILED");
            return result;
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            try {
                process.getInputStream().close(); process.getErrorStream().close(); process.getOutputStream().close();
            } finally { workers.shutdownNow(); }
        }
    }
    private static int outputLimit(Configuration config) {
        return Math.toIntExact(2L * ((config.maximumRecordBytes() + 2L) / 3L * 4L) + 256L);
    }
    private static byte[] bounded(InputStream stream, int maximum) throws IOException {
        try (stream) {
            byte[] value = stream.readNBytes(maximum + 1);
            if (value.length > maximum) throw new IOException("SIMULATION_BRIDGE_OUTPUT_TOO_LARGE");
            return value;
        }
    }
    private static void validatePaths(Configuration config) throws IOException {
        if (!Files.isRegularFile(config.pythonExecutable()) || !Files.isExecutable(config.pythonExecutable())
                || !Files.isDirectory(config.projectRoot())
                || !Files.isDirectory(config.storeDirectory(), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("SIMULATION_BRIDGE_PATH_INVALID");
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(config.storeDirectory(), LinkOption.NOFOLLOW_LINKS);
        if (!permissions.equals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE))) throw new IOException("SIMULATION_BRIDGE_STORE_NOT_PRIVATE");
        // Python additionally verifies actual open descriptors, owner, links and mode.
    }
}
