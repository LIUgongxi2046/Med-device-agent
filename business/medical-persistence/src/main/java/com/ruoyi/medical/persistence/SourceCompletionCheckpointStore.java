package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceCompletionMapper;
import com.ruoyi.medical.persistence.mapper.SourceUploadMapper;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class SourceCompletionCheckpointStore {
    private final SourceCompletionLeaseStore leases;
    private final SourceCompletionMapper mapper;
    private final SourceUploadMapper uploads;
    private final UploadTransferMapper transfers;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final SourceUploadSchema uploadSchema;
    private final SourceUploadPolicy policy;
    private final SourceUploadService service;
    private final SourceCompletionScanResult scanner;

    public SourceCompletionCheckpointStore(SourceCompletionLeaseStore leases,SourceCompletionMapper mapper,
            SourceUploadMapper uploads,UploadTransferMapper transfers,PermissionMapper permissions,PermissionSchema json,
            SourceUploadSchema uploadSchema,SourceUploadPolicy policy,SourceUploadService service) throws IOException {
        this.leases=leases; this.mapper=mapper; this.uploads=uploads; this.transfers=transfers;
        this.permissions=permissions; this.json=json; this.uploadSchema=uploadSchema; this.policy=policy; this.service=service;
        scanner=new SourceCompletionScanResult();
    }

    Row read(SourceCompletionLeaseStore.Lease lease) throws IOException {
        var values=values(lease); var rows=mapper.checkpoint(values);
        if(rows.isEmpty()) return null;
        var row=one(rows); verify(lease,row,values); return row;
    }

    Row save(SourceCompletionLeaseStore.Lease lease,JsonNode scanCommand,JsonNode scanReceipt,
            S3ObjectWriter.WriteIntent intent) throws IOException {
        var values=values(lease);
        if(!mapper.checkpoint(values).isEmpty()) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        var scan=scanner.verify(scanCommand,scanReceipt); var original=scanner.read(lease.command());
        var declaration=one(uploads.declaration(values));
        var declared=service.storedDeclaration(lease.row().tenantId(),lease.row().createdBy(),declaration);
        var request=uploadSchema.completion(scanner.canonical(original.required("request")).getBytes(StandardCharsets.UTF_8),policy.maximumBytes(),declared);
        if(!lease.row().eventId().equals(scan.command().required("event_id").textValue())
                || !lease.operationId().equals(scan.command().required("operation_id").textValue())
                || !lease.uploadId().equals(scan.command().required("upload_id").textValue())
                || !lease.row().leaseOwner().equals(scan.command().required("worker_id").textValue())
                || !lease.row().leaseGeneration().toString().equals(scan.command().required("lease_generation").textValue())
                || !request.sha256().equals(scan.command().required("original_sha256").textValue())
                || !request.sizeBytes().toString().equals(scan.command().required("size_bytes").textValue())) mismatch();
        requireIntent(lease,intent,declared.content().required("media_type").textValue(),request.sha256(),request.sizeBytes().toString());
        values.put("scanCommand",scanner.canonical(scan.command())); values.put("scanReceipt",scanner.canonical(scan.receipt()));
        values.put("writeIntent",json.canonical(json.tree(intent))); values.put("manifest",scanner.manifest());
        values.put("now",permissions.databaseTime()); changed(mapper.saveCheckpoint(values));
        var saved=one(mapper.checkpoint(values)); verify(lease,saved,values);
        if(!BigInteger.ONE.equals(saved.revision()) || !saved.createdAt().equals(saved.updatedAt())
                || !values.get("now").equals(saved.createdAt()) || !"WRITING".equals(saved.status()) || saved.objectRef()!=null
                || !scanner.canonical(scan.command()).equals(scanner.canonical(scanner.read(saved.scanCommand())))
                || !scanner.canonical(scan.receipt()).equals(scanner.canonical(scanner.read(saved.scanReceipt())))) mismatch();
        return saved;
    }

    Row verifyOriginal(SourceCompletionLeaseStore.Lease lease,Row checkpoint,ObjectRef object) throws IOException {
        var values=values(lease); var before=one(mapper.checkpoint(values));
        verify(lease,before,values); if(!before.equals(checkpoint)) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        var intent=intent(scanner.read(before.writeIntent()));
        if(!object.objectKey().equals(intent.objectKey()) || !object.sha256().equals(intent.sha256())
                || !object.sizeBytes().equals(intent.sizeBytes()) || !object.mediaType().equals(intent.mediaType())) mismatch();
        if("ORIGINAL_VERIFIED".equals(before.status())) {
            if(!json.canonical(json.tree(object)).equals(scanner.canonical(scanner.read(before.objectRef())))) mismatch();
            return before;
        }
        values.put("objectRef",json.canonical(json.tree(object))); values.put("revision",before.revision());
        values.put("now",permissions.databaseTime()); changed(mapper.verifyOriginal(values));
        var after=one(mapper.checkpoint(values)); verify(lease,after,values);
        if(!"ORIGINAL_VERIFIED".equals(after.status()) || !before.revision().add(BigInteger.ONE).equals(after.revision())
                || !before.createdAt().equals(after.createdAt()) || !before.writeIntent().equals(after.writeIntent())
                || !before.scanCommand().equals(after.scanCommand()) || !before.scanReceipt().equals(after.scanReceipt())
                || !before.inputSha256().equals(after.inputSha256())
                || !json.canonical(json.tree(object)).equals(scanner.canonical(scanner.read(after.objectRef())))) mismatch();
        return after;
    }

    private Map<String,Object> values(SourceCompletionLeaseStore.Lease lease) throws IOException {
        leases.requireCurrent(lease); var values=new HashMap<String,Object>();
        values.put("userId",TenantScope.requireCurrent().subjectId()); values.put("uploadId",lease.uploadId());
        values.put("operationId",lease.operationId()); values.put("eventId",lease.row().eventId()); values.put("consumerId",lease.row().consumerId());
        var declaration=one(uploads.declaration(values));
        if(!lease.row().tenantId().equals(declaration.tenantId()) || !values.get("userId").equals(declaration.createdBy())
                || !lease.uploadId().equals(declaration.uploadId()) || !service.manifestSha256().equals(declaration.schemaManifestSha256())) mismatch();
        WireValues.uuid(declaration.projectId()); values.put("projectId",declaration.projectId());
        values.put("classification",declaration.classification()); values.put("leaseOwner",lease.row().leaseOwner());
        values.put("generation",lease.row().leaseGeneration()); values.put("leaseRevision",lease.row().revision());
        values.put("inputSha256",DigestUtils.sha256Hex(lease.command())); return values;
    }

    private void verify(SourceCompletionLeaseStore.Lease lease,Row row,Map<String,Object> values) throws IOException {
        if(!lease.row().tenantId().equals(row.tenantId()) || !values.get("projectId").equals(row.projectId())
                || !lease.uploadId().equals(row.uploadId()) || !lease.operationId().equals(row.operationId())
                || !lease.row().eventId().equals(row.eventId()) || !lease.row().consumerId().equals(row.consumerId())
                || !values.get("userId").equals(row.createdBy()) || !values.get("inputSha256").equals(row.inputSha256())
                || !values.get("classification").equals(row.classification()) || !"1.0.0".equals(row.schemaVersion())
                || !scanner.manifest().equals(row.manifest()) || row.revision()==null || row.revision().signum()<=0
                || row.createdAt()==null || row.updatedAt()==null || row.updatedAt().isBefore(row.createdAt())
                || !List.of("WRITING","ORIGINAL_VERIFIED").contains(row.status())
                || "WRITING".equals(row.status())!=(row.objectRef()==null)) mismatch();
        WireValues.revision(row.revision().toString());
        var scan=scanner.verify(scanner.read(row.scanCommand()),scanner.read(row.scanReceipt()));
        if(!lease.row().eventId().equals(scan.command().required("event_id").textValue())
                || !lease.operationId().equals(scan.command().required("operation_id").textValue())
                || !lease.uploadId().equals(scan.command().required("upload_id").textValue())) mismatch();
        var declaration=one(uploads.declaration(values));
        var declared=service.storedDeclaration(lease.row().tenantId(),lease.row().createdBy(),declaration);
        var command=uploadSchema.completionCommand(lease.command());
        var completion=uploadSchema.completion(scanner.canonical(command.required("request")).getBytes(StandardCharsets.UTF_8),policy.maximumBytes(),declared);
        if(!completion.sha256().equals(scan.command().required("original_sha256").textValue())
                || !completion.sizeBytes().toString().equals(scan.command().required("size_bytes").textValue())
                || !org.apache.commons.io.FilenameUtils.getExtension(declared.content().required("file_name").textValue()).equalsIgnoreCase(
                    org.apache.commons.io.FilenameUtils.getExtension(scan.command().required("source_path").textValue()))) mismatch();
        var session=one(transfers.session(values)); var intent=intent(scanner.read(row.writeIntent()));
        requireIntent(lease,intent,session.mediaType(),scan.command().required("original_sha256").textValue(),scan.command().required("size_bytes").textValue());
        if(!intent.objectKey().equals(session.objectKey())) mismatch();
        if(row.objectRef()!=null) {
            var object=scanner.read(row.objectRef());
            if(object.size()!=5 || !intent.objectKey().equals(object.required("object_key").textValue())
                    || !intent.sha256().equals(object.required("sha256").textValue())
                    || !intent.sizeBytes().equals(object.required("size_bytes").textValue())
                    || !intent.mediaType().equals(object.required("media_type").textValue())) mismatch();
            new ObjectRef(object.required("object_key").textValue(),object.required("object_version").textValue(),
                object.required("sha256").textValue(),object.required("size_bytes").textValue(),object.required("media_type").textValue());
        }
    }

    static S3ObjectWriter.WriteIntent intent(JsonNode value) {
        if(value.size()!=5) mismatch();
        return new S3ObjectWriter.WriteIntent(value.required("write_id").textValue(),value.required("object_key").textValue(),
            value.required("sha256").textValue(),value.required("size_bytes").textValue(),value.required("media_type").textValue());
    }
    private static void requireIntent(SourceCompletionLeaseStore.Lease lease,S3ObjectWriter.WriteIntent intent,String media,String digest,String size) {
        Objects.requireNonNull(intent);
        if(!("tenants/"+lease.row().tenantId()+"/source-uploads/"+lease.uploadId()+"/original").equals(intent.objectKey())
                || !digest.equals(intent.sha256()) || !size.equals(intent.sizeBytes()) || !media.equals(intent.mediaType())) mismatch();
    }
    private static <T> T one(List<T> rows) { if(rows.size()!=1) mismatch(); return rows.get(0); }
    private static void changed(int count) { if(count!=1) throw new TenantAccessException(ErrorCode.LEASE_STALE); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }

    public record Row(String tenantId,String projectId,String uploadId,String operationId,String eventId,String consumerId,
        String inputSha256,String scanCommand,String scanReceipt,String writeIntent,String objectRef,String status,
        String schemaVersion,String manifest,String createdBy,LocalDateTime createdAt,String classification,
        BigInteger revision,LocalDateTime updatedAt) { }
}
