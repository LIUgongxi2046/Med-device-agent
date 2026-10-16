package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.util.function.ThrowingSupplier;

final class SourceCompletionWorker {
    private final SourceCompletionAccess access;
    private final SourceCompletionLeaseStore leases;
    private final SourceCompletionCheckpointStore checkpoints;
    private final SourceCompletionRegistration registration;
    private final SourceCompletionExecution execution;
    private final SourceUploadTransport transport;
    private final SourceProcessingPolicy policy;
    private final PermissionSchema json;
    private final SourceCompletionScanResult scanner;
    private final SourceCompletionObjects originals;
    private final Path workspace;

    SourceCompletionWorker(SourceCompletionAccess access,SourceCompletionLeaseStore leases,SourceCompletionCheckpointStore checkpoints,
            SourceCompletionRegistration registration,SourceCompletionExecution execution,SourceUploadTransport transport,SourceProcessingPolicy policy,
            SourceUploadTransportPolicy storage,PermissionSchema json) throws Exception {
        this.access=access; this.leases=leases; this.checkpoints=checkpoints; this.registration=registration;
        this.execution=execution; this.transport=transport; this.policy=policy; this.json=json; scanner=new SourceCompletionScanResult(); originals=new SourceCompletionObjects();
        Path root=SourceProcessingResult.privateDirectory(storage.storageDirectory().toRealPath());
        workspace=Files.createDirectories(root.resolve("source-completion"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        SourceProcessingResult.privateDirectory(workspace); SourceProcessingRuntime.verify(policy);
    }

    List<SourceCompletionAccess.Job> pending() { return access.pending(); }
    void run(SourceCompletionAccess.Job job) { ThrowingSupplier.of(() -> { execute(job); return null; }).get(); }

    void execute(SourceCompletionAccess.Job job) throws Exception {
        var initial=access.execute(job,admission -> {
            if(execution.expire(admission)) return null;
            var lease=leases.claim(job.upload(),job.operation());
            return new Initial(lease,admission,execution.start(admission,lease));
        });
        if(initial==null) return;
        var guard=new Guard(job,initial.lease(),initial.deadline());
        Path work=Files.createTempDirectory(workspace,"completion-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        try {
            guard.requireCurrent(); SourceProcessingRuntime.verify(policy);
            var checkpoint=access.execute(job,admission -> checkpoints.read(guard.lease));
            boolean recovering=checkpoint!=null; Path original=null;
            if(!recovering) {
                var admission=initial.admission();
                var manifest=originals.verify(admission.command().required("stored_parts"),admission.completion().sizeBytes(),admission.completion().sha256(),
                    "tenants/"+job.tenant()+"/uploads/"+job.upload()+"/parts",policy.maximumSourceBytes());
                byte[] bytes=transport.assembleSourceJob(job.tenant(),admission.declaration().createdBy(),job.upload(),manifest,policy.maximumSourceBytes(),guard);
                String extension=org.apache.commons.io.FilenameUtils.getExtension(admission.request().content().required("file_name").textValue());
                original=work.resolve("original."+extension.toLowerCase(java.util.Locale.ROOT)); save(original,bytes);
                Path output=Files.createDirectory(work.resolve("output"),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                var command=json.object(); command.put("schema_version","1.0.0"); command.put("event_id",guard.lease.row().eventId());
                command.put("operation_id",job.operation()); command.put("upload_id",job.upload());
                command.put("worker_id",guard.lease.row().leaseOwner()); command.put("lease_generation",guard.lease.row().leaseGeneration().toString());
                command.put("source_path",original.toString()); command.put("original_sha256",admission.completion().sha256());
                command.put("size_bytes",admission.completion().sizeBytes().toString());
                command.put("scanner_release_path",admission.binding().scannerReleasePath().toString());
                command.put("scanner_release_sha256",admission.binding().scannerReleaseSha256()); command.put("output_directory",output.toString());
                Path commandPath=work.resolve("command.json"),resultPath=work.resolve("scan-receipt.json");
                save(commandPath,scanner.canonical(command).getBytes(StandardCharsets.UTF_8));
                int exit=SourceProcessingRuntime.scan(policy,commandPath,resultPath,work,guard);
                if(exit!=0) {
                    access.execute(job,admitted -> { execution.fail(admitted,guard.lease,"SOURCE_VALIDATION_FAILED",exit); return null; });
                    throw new IllegalStateException("上传完成扫描进程返回失败，退出状态："+exit);
                }
                var result=scanner.verify(command,scanner.read(new String(SourceProcessingResult.privateBytes(resultPath,524288),StandardCharsets.UTF_8)));
                var intent=new S3ObjectWriter.WriteIntent(UUID.randomUUID().toString(),admission.session().objectKey(),admission.completion().sha256(),
                    admission.completion().sizeBytes().toString(),admission.session().mediaType());
                checkpoint=access.execute(job,current -> checkpoints.save(guard.lease,result.command(),result.receipt(),intent));
            }
            var proof=scanner.verify(scanner.read(checkpoint.scanCommand()),scanner.read(checkpoint.scanReceipt()));
            var current=access.execute(job,admission -> admission);
            if(!current.binding().scannerReleaseSha256().equals(proof.command().required("scanner_release_sha256").textValue())) mismatch();
            var intent=SourceCompletionCheckpointStore.intent(scanner.read(checkpoint.writeIntent()));
            ObjectRef object=transport.writeSourceOriginalJob(job.tenant(),current.declaration().createdBy(),job.upload(),intent,original,work,
                recovering,policy.maximumSourceBytes(),guard);
            var before=checkpoint;
            var verified=access.execute(job,admission -> checkpoints.verifyOriginal(guard.lease,before,object));
            var duplicate=access.execute(job,registration::duplicate);
            if(duplicate!=null) {
                var reference=new ObjectRef(duplicate.objectKey(),duplicate.objectVersion(),duplicate.originalSha256(),
                    duplicate.sizeBytes().toString(),duplicate.mediaType());
                transport.readSourceJob(job.tenant(),current.declaration().createdBy(),policy.maximumSourceBytes(),guard,(reader,admission) -> {
                    try(var existing=reader.readVerified(reference,admission)) {
                        if(!existing.reference().equals(reference)) mismatch();
                    }
                    return null;
                });
            }
            guard.requireCurrent(); SourceProcessingRuntime.verify(policy);
            access.execute(job,admission -> { registration.register(admission,guard.lease,verified,object,duplicate); return null; });
        } finally { remove(work); }
    }

    private final class Guard implements S3ObjectReader.Admission {
        private final SourceCompletionAccess.Job job;
        private final LocalDateTime deadline;
        private SourceCompletionLeaseStore.Lease lease;
        Guard(SourceCompletionAccess.Job job,SourceCompletionLeaseStore.Lease lease,LocalDateTime deadline) { this.job=job; this.lease=lease; this.deadline=deadline; }
        @Override public void requireCurrent() {
            var renewed=access.execute(job,admission -> {
                if(!deadline.isAfter(admission.now())) { execution.fail(admission,lease,"EXECUTION_DEADLINE",null); return null; }
                return leases.renew(lease);
            });
            if(renewed==null) throw new TenantAccessException(ErrorCode.ACTION_STALE);
            lease=renewed;
        }
    }
    private record Initial(SourceCompletionLeaseStore.Lease lease,SourceCompletionAccess.Admission admission,LocalDateTime deadline) { }
    private static void save(Path path,byte[] bytes) throws Exception {
        Files.createFile(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(path,bytes,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
    }
    private static void remove(Path work) throws Exception {
        Files.walkFileTree(work,new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes) throws java.io.IOException { Files.delete(path); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path path,java.io.IOException error) throws java.io.IOException {
                if(error!=null) throw error; Files.delete(path); return FileVisitResult.CONTINUE;
            }
        });
    }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
