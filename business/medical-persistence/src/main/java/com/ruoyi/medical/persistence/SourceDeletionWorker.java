package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceDeletionMapper;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

public final class SourceDeletionWorker {
    private final String workerId=UUID.randomUUID().toString();
    private final SourceDeletionPolicy policy;
    private final SourceDeletionAccess access;
    private final SourceDeletionStores stores;
    private final SourceDeletionMapper mapper;
    private final SourceGovernanceMapper governance;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final SourceGovernanceSchema schema;
    private final JdbcTemplate jdbc;

    SourceDeletionWorker(DataSource data,SourceDeletionPolicy policy,SourceDeletionAccess access,SourceDeletionStores stores,
            SourceDeletionMapper mapper,SourceGovernanceMapper governance,PermissionMapper permissions,
            PermissionSchema json,SourceGovernanceSchema schema) {
        jdbc=new JdbcTemplate(data); this.policy=policy; this.access=access; this.stores=stores;
        this.mapper=mapper; this.governance=governance; this.permissions=permissions; this.json=json; this.schema=schema;
    }

    @Scheduled(fixedDelayString="${medical.source-deletion.polling}",scheduler="sourceDeletionScheduler")
    public void runOnce() throws Exception {
        for(String tenant:policy.tenantIds()) {
            var candidates=jdbc.query("""
                SELECT i.item_id FROM deletion_item i
                JOIN deletion_job j ON j.tenant_id=i.tenant_id AND j.deletion_id=i.deletion_id
                JOIN source_lifecycle_state s ON s.tenant_id=j.tenant_id AND s.source_version=j.target_version
                WHERE i.tenant_id=? AND i.status IN ('QUEUED','RUNNING') AND j.status IN ('QUEUED','RUNNING')
                  AND s.status='PURGE_PENDING' AND (i.lease_owner IS NULL OR i.lease_expires_at<=UTC_TIMESTAMP(6))
                  AND (i.execution_evidence IS NULL OR i.backup_expiry<=UTC_TIMESTAMP(6))
                ORDER BY i.updated_at,i.item_id LIMIT 1
                """,(row,n)->row.getString(1),tenant);
            if(!candidates.isEmpty()) execute(tenant,candidates.get(0));
        }
    }

    private void execute(String tenant,String itemId) throws Exception {
        Claim claim=access.execute(tenant,itemId,admission -> {
            var lease=singleLease(admission.values());
            Object expiry=lease.get("lease_expires_at");
            if(lease.get("lease_owner")!=null && localTime(expiry).isAfter(admission.now()))
                return null;
            var target=SourceDeletionTarget.read(admission.item(),schema);
            var values=admission.values(); values.put("workerId",workerId); one(mapper.claim(values));
            values.put("expectedJobRevision",admission.item().jobRevision()); values.put("jobStatus","RUNNING");
            one(governance.updateDeletionJob(values));
            var saved=singleLease(values);
            BigInteger generation=new BigInteger(Objects.toString(saved.get("generation")));
            recordExecution(admission,"source.deletion_started",generation,null);
            return new Claim(tenant,itemId,target,admission.item().targetSha256(),generation,
                admission.item().itemRevision().add(BigInteger.ONE),admission.item().backupExpiry(),admission.now(),
                saved.get("execution_evidence")==null?null:schema.read(saved.get("execution_evidence").toString()));
        });
        if(claim==null) return;
        var values=new HashMap<String,Object>(); values.put("sourceVersion",claim.target().sourceVersion());
        values.put("parseVersion",claim.target().parseVersion()); values.put("indexVersion",claim.target().indexVersion());
        values.put("objectKey",claim.target().objectKey());
        var guard=new SourceDeletionStores.Guard() {
            @Override public <T> T call(SourceDeletionStores.Action<T> action) {
                return access.execute(tenant,itemId,admission -> {
                    requireClaim(claim,admission); values.put("userId",admission.actor()); values.put("now",admission.now());
                    values.put("deletionId",admission.item().deletionId());
                    if(claim.target().objectKey()!=null) access.requireExclusiveObject(tenant,claim.target(),claim.target().objectKey());
                    T result=action.run();
                    var renewed=admission.values(); renewed.put("workerId",workerId); renewed.put("generation",claim.generation());
                    renewed.put("now",Objects.requireNonNull(permissions.databaseTime())); one(mapper.renew(renewed));
                    return result;
                });
            }
        };
        JsonNode primary=claim.evidence();
        if(primary==null) {
            var details=stores.erase(tenant,claim.target(),guard,access,mapper,values);
            var current=json.object(); current.put("schema_version","1.0.0"); current.put("target_sha256",claim.targetHash());
            current.put("config_revision",policy.revision()); current.put("worker_id",workerId);
            current.put("generation",claim.generation().toString()); current.put("deleted_at",time(claim.started()));
            current.put("query_confirmed_at",time(guard.call(permissions::databaseTime))); current.set("stores",details);
            primary=current;
            String stored=json.canonical(primary);
            access.execute(tenant,itemId,admission -> {
                requireClaim(claim,admission); admission.values().put("evidence",stored); one(mapper.saveEvidence(admission.values()));
                recordExecution(admission,"source.deletion_primary_confirmed",claim.generation(),schema.read(stored)); return null;
            });
        } else {
            if(!claim.targetHash().equals(primary.required("target_sha256").textValue())
                    || !"1.0.0".equals(primary.required("schema_version").textValue())
                    || !policy.revision().equals(primary.required("config_revision").textValue())) mismatch();
        }
        Instant now=guard.call(permissions::databaseTime).toInstant(ZoneOffset.UTC);
        if(claim.backupExpiry().toInstant(ZoneOffset.UTC).isAfter(now)) {
            access.execute(tenant,itemId,admission -> { requireClaim(claim,admission); one(mapper.release(admission.values())); return null; });
            return;
        }
        Instant queried=WireValues.utcTime(primary.required("query_confirmed_at").textValue());
        stores.verifyAbsent(tenant,claim.target(),guard,mapper,values);
        if(guard.call(() -> governance.pendingDeletionItemCount(values)).equals(BigInteger.ONE)) {
            for(var item:guard.call(() -> governance.deletionItems(values))) if("CONFIRMED".equals(item.itemStatus())) {
                var prior=SourceDeletionTarget.read(item,schema); var priorValues=new HashMap<>(values);
                priorValues.put("parseVersion",prior.parseVersion()); priorValues.put("indexVersion",prior.indexVersion());
                priorValues.put("objectKey",prior.objectKey());
                stores.verifyAbsent(tenant,prior,guard,mapper,priorValues);
            }
        }
        JsonNode backup=stores.confirmBackups(tenant,claim.target(),queried,now,guard);
        var evidence=json.object(); evidence.set("primary",primary); evidence.set("backup",backup);
        var receipt=json.object(); receipt.put("deleted_at",primary.required("deleted_at").textValue());
        receipt.put("query_confirmed_at",primary.required("query_confirmed_at").textValue());
        receipt.put("backup_confirmed_at",time(guard.call(permissions::databaseTime)));
        receipt.put("evidence_sha256",DigestUtils.sha256Hex(json.canonical(evidence)));
        String storedReceipt=json.canonical(receipt),storedEvidence=json.canonical(evidence);
        access.execute(tenant,itemId,admission -> {
            requireClaim(claim,admission); admission.values().put("evidence",storedEvidence);
            admission.values().put("receipt",storedReceipt); one(mapper.saveEvidence(admission.values())); one(mapper.saveReceipt(admission.values()));
            confirm(claim,admission,receipt); return null;
        });
    }

    private void requireClaim(Claim claim,SourceDeletionAccess.Admission admission) {
        if(!claim.revision().equals(admission.item().itemRevision()) || !"RUNNING".equals(admission.item().itemStatus())
                || !claim.targetHash().equals(admission.item().targetSha256())) stale();
        var lease=singleLease(admission.values());
        if(!workerId.equals(lease.get("lease_owner")) || !claim.generation().toString().equals(lease.get("generation"))
                || !localTime(lease.get("lease_expires_at")).isAfter(admission.now())) stale();
        admission.values().put("workerId",workerId); admission.values().put("generation",claim.generation());
    }

    private void confirm(Claim claim,SourceDeletionAccess.Admission admission,JsonNode receipt) throws Exception {
        var item=admission.item(); var values=admission.values();
        String stored=mapper.workerReceipt(values);
        if(stored==null || !schema.read(stored).equals(receipt)) mismatch();
        values.put("expectedJobRevision",item.jobRevision()); values.put("expectedLifecycleRevision",item.lifecycleRevision());
        values.put("receiptRef",json.canonical(receipt)); one(governance.confirmDeletionItem(values));
        boolean complete=governance.pendingDeletionItemCount(values).signum()==0;
        if(complete && (governance.blockingReferenceCount(values).signum()!=0 || mapper.additionalReferenceCount(values).signum()!=0))
            throw new TenantAccessException(ErrorCode.REFERENCE_HELD);
        values.put("jobStatus",complete?"COMPLETED":"RUNNING"); one(governance.updateDeletionJob(values));
        if(complete) {
            one(governance.completeLifecycleState(values)); if(governance.completeLifecycleGates(values)<1) mismatch();
        }
        one(mapper.advanceGovernance(values));
        var event=json.object(); event.put("schema_version","1.0.0"); event.put("item_id",item.itemId());
        event.put("deletion_id",item.deletionId()); event.put("target_sha256",item.targetSha256());
        event.put("worker_id",workerId); event.put("generation",claim.generation().toString());
        event.put("config_revision",policy.revision()); event.set("receipt",receipt);
        String request=UUID.randomUUID().toString(),resourceRevision=(complete?item.lifecycleRevision().add(BigInteger.ONE):item.lifecycleRevision()).toString();
        one(permissions.saveAudit(admission.actor(),item.ownerProjectId(),UUID.randomUUID().toString(),
            "source.deletion_confirmed",json.canonical(event),request,schema.manifestSha256(),item.classification()));
        one(permissions.saveEvent(admission.actor(),UUID.randomUUID().toString(),item.sourceVersion(),resourceRevision,
            complete?"source.purged":"source.deletion_confirmed",json.canonical(event)));
        var saved=governance.deletionItems(values).stream().filter(row -> row.itemId().equals(item.itemId())).toList();
        if(saved.size()!=1 || !"CONFIRMED".equals(saved.get(0).itemStatus())
                || !schema.read(saved.get(0).receiptRef()).equals(receipt)
                || !claim.revision().add(BigInteger.ONE).equals(saved.get(0).itemRevision())
                || !resourceRevision.equals(saved.get(0).lifecycleRevision().toString())
                || !(complete?"PURGED":"PURGE_PENDING").equals(saved.get(0).lifecycleStatus())) mismatch();
    }

    private void recordExecution(SourceDeletionAccess.Admission admission,String name,BigInteger generation,JsonNode evidence) throws Exception {
        var item=admission.item(); var event=json.object(); event.put("schema_version","1.0.0");
        event.put("item_id",item.itemId()); event.put("deletion_id",item.deletionId());
        event.put("target_sha256",item.targetSha256()); event.put("worker_id",workerId);
        event.put("generation",generation.toString()); event.put("config_revision",policy.revision());
        if(evidence!=null) event.set("evidence",evidence);
        one(permissions.saveAudit(admission.actor(),item.ownerProjectId(),UUID.randomUUID().toString(),name,
            json.canonical(event),UUID.randomUUID().toString(),schema.manifestSha256(),item.classification()));
        one(permissions.saveEvent(admission.actor(),UUID.randomUUID().toString(),item.sourceVersion(),
            item.lifecycleRevision().toString(),name,json.canonical(event)));
    }

    private Map<String,Object> singleLease(Map<String,Object> values) {
        var leases=mapper.lease(values); if(leases.size()!=1) mismatch(); return leases.get(0);
    }
    private static LocalDateTime localTime(Object value) {
        if(value instanceof LocalDateTime time) return time;
        if(value instanceof java.sql.Timestamp time) return time.toLocalDateTime();
        throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static void one(int count) { if(count!=1) mismatch(); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void stale() { throw new TenantAccessException(ErrorCode.ACTION_STALE); }
    private record Claim(String tenant,String itemId,SourceDeletionTarget target,String targetHash,BigInteger generation,
            BigInteger revision,LocalDateTime backupExpiry,LocalDateTime started,JsonNode evidence) { }
}
