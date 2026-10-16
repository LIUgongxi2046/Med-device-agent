package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceUploadMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class SourceCompletionLeaseStore {
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final BigInteger MAX_ATTEMPTS = BigInteger.valueOf(6);
    private final String owner = UUID.randomUUID().toString();
    private final SourceUploadMapper mapper;
    private final PermissionMapper permissions;
    private final PermissionSchema json;

    public SourceCompletionLeaseStore(SourceUploadMapper mapper, PermissionMapper permissions, PermissionSchema json) {
        this.mapper = Objects.requireNonNull(mapper); this.permissions = Objects.requireNonNull(permissions);
        this.json = Objects.requireNonNull(json);
    }

    public Lease claim(String uploadId, String operationId) throws IOException {
        var values = values(uploadId, operationId);
        String command = command(values);
        var before = row(values); LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
        if (before.attemptNo().compareTo(MAX_ATTEMPTS) >= 0) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        boolean recovering = "DELIVERING".equals(before.status());
        if (before.nextAttemptAt().isAfter(now)
                || !recovering && (!"PENDING".equals(before.status()) || before.leaseOwner() != null || before.leaseUntil() != null)
                || recovering && (before.leaseOwner() == null || before.leaseUntil() == null || before.leaseUntil().isAfter(now))) stale();
        values.put("revision", before.revision()); values.put("generation", before.leaseGeneration());
        values.put("leaseOwner", owner); values.put("now", now); values.put("leaseUntil", now.plusSeconds(30));
        WireValues.revision(before.revision().add(BigInteger.ONE).toString());
        WireValues.revision(before.leaseGeneration().add(BigInteger.ONE).toString());
        changed(mapper.claimCompletionDelivery(values));
        var after = row(values);
        if (!"DELIVERING".equals(after.status()) || !owner.equals(after.leaseOwner())
                || !now.plusSeconds(30).equals(after.leaseUntil()) || !now.equals(after.updatedAt())
                || !before.attemptNo().add(BigInteger.ONE).equals(after.attemptNo())
                || !before.leaseGeneration().add(BigInteger.ONE).equals(after.leaseGeneration())
                || !before.revision().add(BigInteger.ONE).equals(after.revision())
                || !sameOrigin(before, after) || !command.equals(command(values))) mismatch();
        // 到期领取仅恢复原命令；调用方必须查询原目标结果，不能据此重发未知动作。
        return new Lease(uploadId, operationId, command, after, recovering);
    }

    public Lease renew(Lease lease) throws IOException {
        Objects.requireNonNull(lease);
        var values = values(lease.uploadId, lease.operationId);
        if (!owner.equals(lease.row.leaseOwner()) || !TenantScope.requireCurrent().tenantId().equals(lease.row.tenantId())
                || !TenantScope.requireCurrent().subjectId().equals(lease.row.createdBy())) stale();
        if (!lease.command.equals(command(values))) mismatch();
        var before = row(values); LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
        if (!before.equals(lease.row) || !"DELIVERING".equals(before.status()) || !owner.equals(before.leaseOwner())
                || before.leaseUntil() == null || !before.leaseUntil().isAfter(now)) stale();
        WireValues.revision(before.revision().add(BigInteger.ONE).toString());
        values.put("revision", before.revision()); values.put("generation", before.leaseGeneration());
        values.put("attempt", before.attemptNo()); values.put("leaseOwner", owner);
        values.put("now", now); values.put("leaseUntil", now.plusSeconds(30));
        changed(mapper.renewCompletionDelivery(values));
        var after = row(values);
        if (!"DELIVERING".equals(after.status()) || !owner.equals(after.leaseOwner())
                || !now.plusSeconds(30).equals(after.leaseUntil()) || !now.equals(after.updatedAt())
                || !before.attemptNo().equals(after.attemptNo()) || !before.leaseGeneration().equals(after.leaseGeneration())
                || !before.revision().add(BigInteger.ONE).equals(after.revision())
                || !sameOrigin(before, after) || !lease.command.equals(command(values))) mismatch();
        return new Lease(lease.uploadId, lease.operationId, lease.command, after, lease.recoverExisting);
    }

    void requireCurrent(Lease lease) throws IOException {
        Objects.requireNonNull(lease);
        var values=values(lease.uploadId,lease.operationId);
        if(!lease.command.equals(command(values)) || !lease.row.equals(row(values))) mismatch();
        LocalDateTime now=Objects.requireNonNull(permissions.databaseTime());
        if(!owner.equals(lease.row.leaseOwner()) || !"DELIVERING".equals(lease.row.status())
                || lease.row.leaseUntil()==null || !lease.row.leaseUntil().isAfter(now)) stale();
    }

    private String command(Map<String,Object> values) throws IOException {
        var body = JSON.readTree(one(mapper.completionCommand(values)));
        String eventId = body.required("event_id").textValue(); WireValues.uuid(eventId);
        var scope = TenantScope.requireCurrent();
        if (!"1.0.0".equals(body.required("schema_version").textValue())
                || !values.get("uploadId").equals(body.required("upload_id").textValue())
                || !values.get("operationId").equals(body.required("operation_id").textValue())
                || !SourceUploadProtocol.COMPLETION_CONSUMER.equals(body.required("consumer_id").textValue())
                || !scope.subjectId().equals(body.required("subject_id").textValue())
                || !scope.tenantId().equals(body.required("context").required("tenant_id").textValue())) mismatch();
        values.put("eventId", eventId); values.put("consumerId", SourceUploadProtocol.COMPLETION_CONSUMER);
        String manifest=body.required("schema_manifest_sha256").textValue(); WireValues.hash(manifest); values.put("manifest",manifest);
        return json.canonical(body);
    }

    private SourceUploadProtocol.CompletionDelivery row(Map<String,Object> values) {
        var row = one(mapper.completionDelivery(values)); var scope = TenantScope.requireCurrent();
        if (!scope.tenantId().equals(row.tenantId()) || !scope.subjectId().equals(row.createdBy())
                || !values.get("eventId").equals(row.eventId()) || !values.get("consumerId").equals(row.consumerId())
                || !"1.0.0".equals(row.schemaVersion()) || !values.get("manifest").equals(row.schemaManifestSha256())
                || row.revision() == null || row.revision().signum() <= 0 || row.createdAt() == null || row.updatedAt() == null
                || row.updatedAt().isBefore(row.createdAt()) || row.nextAttemptAt() == null
                || row.attemptNo() == null || row.leaseGeneration() == null) mismatch();
        WireValues.revision(row.revision().toString()); WireValues.revision(row.attemptNo().toString());
        WireValues.revision(row.leaseGeneration().toString());
        return row;
    }

    private static Map<String,Object> values(String uploadId, String operationId) {
        WireValues.uuid(uploadId); WireValues.uuid(operationId);
        var values = new HashMap<String,Object>(); values.put("uploadId", uploadId); values.put("operationId", operationId);
        values.put("userId", TenantScope.requireCurrent().subjectId()); return values;
    }

    private static boolean sameOrigin(SourceUploadProtocol.CompletionDelivery before, SourceUploadProtocol.CompletionDelivery after) {
        return before.tenantId().equals(after.tenantId()) && before.eventId().equals(after.eventId())
            && before.consumerId().equals(after.consumerId()) && before.createdBy().equals(after.createdBy())
            && before.createdAt().equals(after.createdAt()) && before.classification().equals(after.classification())
            && before.schemaVersion().equals(after.schemaVersion()) && before.schemaManifestSha256().equals(after.schemaManifestSha256())
            && before.nextAttemptAt().equals(after.nextAttemptAt()) && Objects.equals(before.lastError(), after.lastError());
    }

    private static <T> T one(List<T> rows) { if (rows.size() != 1) throw new TenantAccessException(ErrorCode.ACCESS_DENIED); return Objects.requireNonNull(rows.get(0)); }
    private static void changed(int count) { if (count != 1) stale(); }
    private static void stale() { throw new TenantAccessException(ErrorCode.LEASE_STALE); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }

    public static final class Lease {
        private final String uploadId, operationId, command;
        private final SourceUploadProtocol.CompletionDelivery row;
        private final boolean recoverExisting;
        private Lease(String uploadId, String operationId, String command, SourceUploadProtocol.CompletionDelivery row, boolean recoverExisting) {
            this.uploadId = uploadId; this.operationId = operationId; this.command = command; this.row = row; this.recoverExisting = recoverExisting;
        }
        public SourceUploadProtocol.CompletionDelivery row() { return row; }
        public String uploadId() { return uploadId; }
        public String operationId() { return operationId; }
        public String command() { return command; }
        public boolean recoverExisting() { return recoverExisting; }
    }
}
