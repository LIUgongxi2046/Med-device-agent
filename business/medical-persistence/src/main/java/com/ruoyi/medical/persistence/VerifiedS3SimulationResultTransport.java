package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Uses the existing private object service and shared transfer admission, without a user session. */
final class VerifiedS3SimulationResultTransport implements SimulationResultTransport {
    private final SourceUploadTransport storage;
    private final int maximumBytes;

    VerifiedS3SimulationResultTransport(SourceUploadTransport storage, int maximumBytes) {
        if (maximumBytes <= 0) throw new IllegalArgumentException("SIMULATION_RESULT_CAPACITY_REQUIRED");
        this.storage = Objects.requireNonNull(storage);
        this.maximumBytes = maximumBytes;
    }

    @Override public ObjectRef write(String tenantId, String actorId, S3ObjectWriter.WriteIntent intent,
            byte[] canonicalBytes, boolean recover, S3ObjectReader.Admission current) throws Exception {
        byte[] snapshot = Objects.requireNonNull(canonicalBytes).clone();
        validate(tenantId, actorId, intent, snapshot, maximumBytes, current);
        return storage.writeSimulationResult(tenantId, actorId, intent, snapshot, recover, maximumBytes, current);
    }

    static void validate(String tenantId, String actorId, S3ObjectWriter.WriteIntent intent, byte[] bytes,
            int maximumBytes, S3ObjectReader.Admission current) throws java.security.NoSuchAlgorithmException {
        if (TransactionSynchronizationManager.isActualTransactionActive()) denied();
        WireValues.uuid(tenantId); WireValues.uuid(actorId);
        Objects.requireNonNull(intent); Objects.requireNonNull(bytes); Objects.requireNonNull(current).requireCurrent();
        String[] parts = intent.objectKey().split("/", -1);
        if (parts.length != 5 || !"tenants".equals(parts[0]) || !tenantId.equals(parts[1])
                || !"simulation-results".equals(parts[2])) denied();
        try { WireValues.uuid(parts[3]); WireValues.uuid(parts[4]); }
        catch (IllegalArgumentException invalid) { denied(); }
        if (maximumBytes <= 0) throw new IllegalArgumentException("SIMULATION_RESULT_CAPACITY_REQUIRED");
        if (bytes.length > maximumBytes) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        if (!"application/json".equals(intent.mediaType()) || !Integer.toString(bytes.length).equals(intent.sizeBytes())
                || !WireValues.sha256(bytes).equals(intent.sha256()))
            throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }

    /** Boundary-testable orchestration; the real writer performs metadata HEAD and exact-version GET/hash verification. */
    static ObjectRef writeVerified(S3ObjectWriter writer, Path directory, S3ObjectWriter.WriteIntent intent,
            byte[] bytes, boolean recover, S3ObjectReader.Admission current) throws Exception {
        current.requireCurrent();
        // An ambiguous prior PUT is recovered by HEAD/GET only; never replay the PUT here.
        if (recover) return writer.recoverVerified(intent, current);
        Path file = Files.createTempFile(directory, "simulation-result-", ".json",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            Files.write(file, bytes);
            current.requireCurrent();
            return writer.writeVerified(intent, file, current);
        } finally { Files.deleteIfExists(file); }
    }

    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
}
