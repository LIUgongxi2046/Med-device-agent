package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Real object transport without a human session. The caller must obtain immutable
 * intent/attempt evidence before invoking and must freshly authenticate each guard
 * using FormalWorkerAccess; no transaction or worker proof is held across object I/O.
 */
public final class RuntimeObjectTransport {
    @FunctionalInterface public interface Writers { S3ObjectWriter open() throws Exception; }
    private final Writers writers;
    private final SourceUploadTransport deployed;
    private final Path directory;
    private final int maximumBytes;
    public RuntimeObjectTransport(Writers writers,Path directory,int maximumBytes)throws Exception {
        this.deployed=null;this.writers=Objects.requireNonNull(writers);this.directory=directory.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if(maximumBytes<1 || !Files.isDirectory(this.directory,LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(this.directory,LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------")))
            throw new IllegalArgumentException("PRIVATE_RUNTIME_OBJECT_DIRECTORY_REQUIRED");
        this.maximumBytes=maximumBytes;
    }
    /** Deployment adapter uses the existing approved object store and bounded shared permits. */
    public RuntimeObjectTransport(SourceUploadTransport deployed,Path directory,int maximumBytes)throws Exception {
        this.deployed=Objects.requireNonNull(deployed);this.writers=null;this.directory=directory.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if(maximumBytes<1||!Files.isDirectory(this.directory,LinkOption.NOFOLLOW_LINKS)||!Files.getPosixFilePermissions(this.directory,LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------")))throw new IllegalArgumentException("PRIVATE_RUNTIME_OBJECT_DIRECTORY_REQUIRED");
        this.maximumBytes=maximumBytes;
    }
    InterfaceProtocol.ObjectRef transfer(RuntimeObjectAttempt attempt,
            S3ObjectReader.Admission current)throws Exception {
        return transferVerified(attempt,current).reference();
    }
    VerifiedTransfer transferVerified(RuntimeObjectAttempt attempt,
            S3ObjectReader.Admission current)throws Exception {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        Objects.requireNonNull(current).requireCurrent();Objects.requireNonNull(attempt);
        validateAttempt(attempt,maximumBytes);
        var immutableIntent=attempt.writeIntent();byte[] exactBytes=attempt.bytes();
        if(exactBytes.length<1 || exactBytes.length>maximumBytes)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        if(!Integer.toString(exactBytes.length).equals(immutableIntent.sizeBytes()) || !WireValues.sha256(exactBytes).equals(immutableIntent.sha256()))
            throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        if(deployed!=null){var actual=deployed.writeRuntimeObject(attempt,directory,maximumBytes,current);current.requireCurrent();return verified(attempt,actual);}
        var mode=attempt.claimTransferMode();
        try(var writer=writers.open()) {
            if(mode==RuntimeObjectAttempt.TransferMode.RECOVER_ONLY) {
                var actual=writer.recoverVerified(immutableIntent,current);current.requireCurrent();return verified(attempt,actual);
            }
            Path file=Files.createTempFile(directory,"runtime-object-",".json",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try {
                Files.write(file,exactBytes);current.requireCurrent();
                var actual=writer.writeVerified(immutableIntent,file,current);current.requireCurrent();return verified(attempt,actual);
            } finally { Files.deleteIfExists(file); }
        }
    }
    static void validateAttempt(RuntimeObjectAttempt attempt,int maximumBytes)throws Exception {
        Objects.requireNonNull(attempt);WireValues.uuid(attempt.tenantId());WireValues.uuid(attempt.actorId());
        var intent=attempt.writeIntent();byte[] bytes=attempt.bytes();
        if(bytes.length<1||bytes.length>maximumBytes)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        if(!attempt.expectedObjectKey().equals(intent.objectKey())||!"application/json".equals(intent.mediaType())||!Integer.toString(bytes.length).equals(intent.sizeBytes())||!WireValues.sha256(bytes).equals(intent.sha256()))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }
    private static VerifiedTransfer verified(RuntimeObjectAttempt attempt,InterfaceProtocol.ObjectRef actual) {
        var intent=attempt.writeIntent();
        if(actual==null || !intent.objectKey().equals(actual.objectKey()) || !intent.sha256().equals(actual.sha256())
            || !intent.sizeBytes().equals(actual.sizeBytes()) || !intent.mediaType().equals(actual.mediaType())
            || actual.objectVersion()==null || actual.objectVersion().isBlank())
            throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        return new VerifiedTransfer(attempt,actual);
    }
    /** Produced only after actual write/recovery readback and the final current guard.
     * This proves transferred bytes and exact object version; registration still rechecks
     * its domain's immutable intent/attempt/current authority in a fresh transaction. */
    public static final class VerifiedTransfer {
        private final RuntimeObjectAttempt attempt;private final InterfaceProtocol.ObjectRef reference;
        private VerifiedTransfer(RuntimeObjectAttempt attempt,InterfaceProtocol.ObjectRef reference){this.attempt=attempt;this.reference=reference;}
        public RuntimeObjectAttempt originalAttempt(){return attempt;}
        public InterfaceProtocol.ObjectRef reference(){return reference;}
        public String rawSha256(){return reference.sha256();}
    }
}
