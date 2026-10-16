package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceIndexMapper;
import com.ruoyi.medical.persistence.mapper.SourceProcessingMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.util.function.ThrowingSupplier;

final class SourceIndexWorker {
    private final String owner=UUID.randomUUID().toString();
    private final SourceIndexAccess access;
    private final SourceIndexMapper mapper;
    private final SourceProcessingMapper parses;
    private final PermissionMapper permissions;
    private final SourceUploadTransport storage;
    private final SourceIndexPolicy policy;
    private final PermissionSchema json;
    private final SourceIndexManifest manifests;

    SourceIndexWorker(SourceIndexAccess access,SourceIndexMapper mapper,SourceProcessingMapper parses,PermissionMapper permissions,
            SourceUploadTransport storage,SourceIndexPolicy policy,PermissionSchema json) throws Exception {
        this.access=access; this.mapper=mapper; this.parses=parses; this.permissions=permissions;
        this.storage=storage; this.policy=policy; this.json=json; manifests=new SourceIndexManifest();
    }
    List<SourceIndexProtocol.Job> pending() { return access.pending(); }
    void run(SourceIndexProtocol.Job job) { ThrowingSupplier.of(() -> { execute(job); return null; }).get(); }

    void execute(SourceIndexProtocol.Job job) throws Exception {
        var claimed=claim(job); if(claimed==null) return;
        var guard=new Guard(job,claimed.build(),claimed.delivery()); guard.requireCurrent();
        var reference=guard.reference();
        byte[] bytes=storage.readSourceJob(job.tenant(),guard.build.createdBy(),policy.maximumBundleBytes(),guard,(reader,admission) -> {
            try(var verified=reader.readVerified(reference,admission)) {
                byte[] result=new byte[new BigInteger(reference.sizeBytes()).intValueExact()];
                for(int start=0;start<result.length;) {
                    int size=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,result.length-start);
                    System.arraycopy(verified.chunk(start,size),0,result,start,size); start+=size;
                }
                return result;
            }
        });
        guard.requireCurrent();
        var bundle=access.execute(job,admission -> {
            guard.require(admission); var parsed=manifests.verifyParsed(admission.row(),bytes);
            if(!parsed.required("processor_manifest").equals(manifests.read((String)admission.materialized().get("processorManifest")))
                    || !parsed.required("issues").equals(manifests.read((String)admission.materialized().get("failureManifest")).required("issues"))) mismatch();
            return parsed;
        });
        var chunks=manifests.chunks(bundle,policy.maximumChunks());
        complete(guard,bundle,chunks,reference);
    }

    private Claimed claim(SourceIndexProtocol.Job job) {
        return access.execute(job,admission -> {
            var values=admission.values(); var builds=mapper.build(values); var deliveries=mapper.delivery(values);
            if(deliveries.isEmpty()) {
                if(!builds.isEmpty()) mismatch(); changed(mapper.initializeDelivery(values)); deliveries=mapper.delivery(values);
            }
            var delivery=one(deliveries); verifyDelivery(admission,delivery);
            SourceIndexProtocol.Build build=builds.isEmpty()?null:one(builds);
            if(build!=null) verifyBuild(admission,build);
            if(List.of("DELIVERED","FAILED").contains(delivery.status())) {
                if(build==null || !List.of("READY","FAILED").contains(build.status())) mismatch();
                if(!manifests.receipt(build,"READY".equals(build.status())).equals(manifests.read(one(mapper.receipt(values))))
                        || !Objects.equals(build.resultRef(),one(mapper.receipt(values)))) mismatch();
                return null;
            }
            if(!delivery.nextAttemptAt().isAfter(admission.now())
                    && ("PENDING".equals(delivery.status()) || "DELIVERING".equals(delivery.status())
                        && delivery.leaseUntil()!=null && !delivery.leaseUntil().isAfter(admission.now()))) {
                leaseValues(values,delivery);
                if(delivery.attemptNo().compareTo(BigInteger.valueOf(3))>=0
                        || build!=null && !build.deadline().isAfter(admission.now())) {
                    if(build==null) mismatch(); fail(admission,build); return null;
                }
                LocalDateTime deadline=build==null?admission.now().plus(policy.timeout()):build.deadline();
                values.put("workerId",owner); values.put("leaseUntil",minimum(admission.now().plus(policy.lease()),deadline));
                changed(mapper.claim(values));
                var claimed=one(mapper.delivery(values)); verifyDelivery(admission,claimed);
                if(!owner.equals(claimed.leaseOwner()) || !"DELIVERING".equals(claimed.status())
                        || !delivery.leaseGeneration().add(BigInteger.ONE).equals(claimed.leaseGeneration())
                        || !delivery.revision().add(BigInteger.ONE).equals(claimed.revision())
                        || !delivery.attemptNo().add(BigInteger.ONE).equals(claimed.attemptNo())) mismatch();
                if(build==null) {
                    var chunks=currentChunks(values); String index=UUID.randomUUID().toString();
                    var manifest=manifests.manifest(admission.row(),job.event(),admission.command(),index,chunks,policy);
                    values.put("indexVersion",index); values.put("manifest",manifests.canonical(manifest));
                    values.put("manifestSha256",manifests.hash(manifest)); values.put("count",chunks.size()); values.put("deadline",deadline);
                    changed(mapper.saveVersion(values)); changed(mapper.saveIndex(values)); changed(mapper.saveBuild(values));
                    values.put("upstreamId",admission.row().parseVersion()); values.put("relationKind","INDEX_INPUT"); changed(mapper.saveLineage(values));
                    values.put("upstreamId",values.get("indexConfigRevision")); values.put("relationKind","INDEX_CONFIGURATION"); changed(mapper.saveLineage(values));
                    build=one(mapper.build(values)); verifyBuild(admission,build); verifyIndex(values,build,"BUILDING");
                    changed(parses.advanceGovernance(values));
                }
                if(!"BUILDING".equals(build.status())) mismatch();
                event(admission,"source.index.started",json.tree(Map.of("index_version",build.indexVersion(),
                    "lease_generation",claimed.leaseGeneration().toString())));
                return new Claimed(build,claimed);
            }
            throw new TenantAccessException(ErrorCode.LEASE_STALE);
        });
    }

    private JsonNode currentChunks(Map<String,Object> values) throws Exception {
        var chunks=manifests.projected(mapper.chunks(values),policy.maximumChunks());
        var count=BigInteger.valueOf(chunks.size());
        if(!count.equals(mapper.rawChunkCount(values)) || !count.equals(parses.chunkCount(values)) || !count.equals(parses.nodeCount(values))) mismatch();
        return chunks;
    }

    private void complete(Guard guard,JsonNode bundle,JsonNode chunks,ObjectRef object) {
        access.execute(guard.job,admission -> {
            guard.require(admission); var values=admission.values(); buildValues(values,guard.build); leaseValues(values,guard.delivery);
            var expected=manifests.manifest(admission.row(),guard.job.event(),admission.command(),guard.build.indexVersion(),chunks,policy);
            if(!expected.equals(manifests.read(guard.build.manifest())) || !chunks.equals(currentChunks(values))
                    || mapper.itemCount(values).signum()!=0) mismatch();
            verifyStoredBundle(values,bundle,object); verifyIndex(values,guard.build,"BUILDING");
            int ordinal=0;
            for(var item:expected.required("items")) {
                if(ordinal++%100==0) guard.renewInTransaction(admission);
                values.put("chunkId",item.required("chunk").required("chunk_id").textValue());
                values.put("pointId",item.required("point_id").textValue()); changed(mapper.saveItem(values));
            }
            guard.renewInTransaction(admission); verifyItems(values,expected);
            values.put("count",chunks.size()); changed(mapper.readyIndex(values)); verifyIndex(values,guard.build,"READY");
            var receipt=manifests.receipt(guard.build,true); values.put("resultRef",manifests.canonical(receipt)); values.put("resultStatus","READY");
            changed(mapper.completeBuild(values)); changed(mapper.completeDelivery(values)); changed(mapper.saveReceipt(values));
            changed(parses.advanceGovernance(values)); event(admission,"source.index.completed",receipt);
            var after=one(mapper.build(values)); var delivered=one(mapper.delivery(values));
            if(!"READY".equals(after.status()) || !guard.build.revision().add(BigInteger.ONE).equals(after.revision())
                    || !receipt.equals(manifests.read(after.resultRef())) || !receipt.equals(manifests.read(one(mapper.receipt(values))))
                    || !"DELIVERED".equals(delivered.status()) || delivered.leaseOwner()!=null || delivered.leaseUntil()!=null) mismatch();
            return null;
        });
    }

    private void verifyStoredBundle(Map<String,Object> values,JsonNode bundle,ObjectRef object) throws Exception {
        var rows=mapper.chunks(values); if(rows.size()!=bundle.required("nodes").size()) mismatch();
        for(int ordinal=0;ordinal<rows.size();ordinal++) {
            var row=rows.get(ordinal); var node=bundle.required("nodes").get(ordinal); var content=manifests.read((String)row.get("contentRef"));
            if(!node.required("node_kind").textValue().equals(row.get("nodeKind"))
                    || !json.tree(object).equals(content.required("object_ref"))
                    || !node.required("node_id").equals(content.required("node_id"))
                    || !node.required("ordinal").equals(content.required("ordinal"))
                    || !node.required("quote_hash").equals(content.required("quote_hash"))
                    || !node.required("heading_path").equals(content.required("heading_path"))) mismatch();
        }
    }

    private void verifyItems(Map<String,Object> values,JsonNode manifest) {
        var stored=mapper.items(values); var expected=manifest.required("items");
        if(stored.size()!=expected.size() || !BigInteger.valueOf(expected.size()).equals(mapper.itemCount(values))) mismatch();
        for(int ordinal=0;ordinal<stored.size();ordinal++) {
            var row=stored.get(ordinal); var item=expected.get(ordinal);
            if(!item.required("chunk").required("chunk_id").textValue().equals(row.get("chunkId"))
                    || !item.required("point_id").textValue().equals(row.get("pointId"))
                    || !Integer.toString(ordinal).equals(row.get("ordinal").toString()) || !"READY".equals(row.get("status"))
                    || row.get("vectorSha256")!=null || row.get("invocationId")!=null || row.get("verifiedAt")==null
                    || !values.get("userId").equals(row.get("createdBy")) || !values.get("classification").equals(row.get("classification"))) mismatch();
        }
    }

    private void verifyIndex(Map<String,Object> values,SourceIndexProtocol.Build build,String status) throws Exception {
        buildValues(values,build); var index=one(mapper.index(values)); int count=manifests.read(build.manifest()).required("items").size();
        if(!"FULLTEXT".equals(index.get("mode")) || !status.equals(index.get("status")) || index.get("modelConfigRevision")!=null
                || index.get("dimension")!=null || !Objects.equals(values.get("ownerProjectId"),index.get("ownerProjectId"))
                || !values.get("configSha256").equals(index.get("configSha256")) || !build.manifestSha256().equals(index.get("manifestSha256"))
                || !build.manifestSha256().equals(index.get("registeredSha256"))
                || !Integer.toString(count).equals(index.get("expectedCount").toString())
                || !Integer.toString("READY".equals(status)?count:0).equals(index.get("verifiedCount").toString())) mismatch();
    }

    private void verifyBuild(SourceIndexAccess.Admission admission,SourceIndexProtocol.Build build) throws Exception {
        var row=admission.row();
        if(!row.tenantId().equals(build.tenantId()) || !row.projectId().equals(build.projectId()) || !row.createdBy().equals(build.createdBy())
                || !row.classification().equals(build.classification()) || !row.attemptId().equals(build.attemptId())
                || !row.sourceVersion().equals(build.sourceVersion()) || !row.parseVersion().equals(build.parseVersion())
                || !row.rightsRevision().equals(build.rightsRevision()) || !admission.values().get("eventId").equals(build.eventId())
                || !admission.values().get("inputSha256").equals(build.inputSha256())
                || !admission.values().get("indexConfigRevision").equals(build.configRevision())
                || !manifests.sha256().equals(build.schemaManifestSha256()) || build.revision().signum()<=0
                || !build.startedAt().plus(policy.timeout()).equals(build.deadline())) mismatch();
        var manifest=manifests.read(build.manifest()); manifests.require("Manifest",manifest);
        if(!manifests.hash(manifest).equals(build.manifestSha256())) mismatch();
        var chunks=json.object().arrayNode();
        for(var item:manifest.required("items")) chunks.add(item.required("chunk"));
        var current=manifests.manifest(row,build.eventId(),admission.command(),build.indexVersion(),chunks,policy);
        if(!manifest.equals(current)) mismatch();
    }

    private void verifyDelivery(SourceIndexAccess.Admission admission,SourceUploadProtocol.CompletionDelivery delivery) {
        if(!admission.row().tenantId().equals(delivery.tenantId()) || !admission.row().createdBy().equals(delivery.createdBy())
                || !admission.values().get("eventId").equals(delivery.eventId()) || !SourceIndexProtocol.CONSUMER.equals(delivery.consumerId())
                || !"1.0.0".equals(delivery.schemaVersion()) || !manifests.sha256().equals(delivery.schemaManifestSha256())
                || !admission.row().classification().equals(delivery.classification()) || delivery.revision().signum()<=0
                || delivery.attemptNo().signum()<0 || delivery.leaseGeneration().signum()<0
                || delivery.createdAt()==null || delivery.updatedAt().isBefore(delivery.createdAt())) mismatch();
    }

    private void fail(SourceIndexAccess.Admission admission,SourceIndexProtocol.Build build) throws Exception {
        var values=admission.values(); buildValues(values,build);
        if(!"BUILDING".equals(build.status()) || mapper.itemCount(values).signum()!=0) mismatch();
        var receipt=manifests.receipt(build,false); values.put("resultRef",manifests.canonical(receipt)); values.put("resultStatus","FAILED");
        changed(mapper.failIndex(values)); changed(mapper.completeBuild(values)); changed(mapper.failDelivery(values)); changed(mapper.saveReceipt(values));
        changed(parses.advanceGovernance(values)); event(admission,"source.index.failed",receipt);
        var after=one(mapper.build(values)); var delivered=one(mapper.delivery(values));
        if(!"FAILED".equals(after.status()) || !"FAILED".equals(delivered.status())
                || !receipt.equals(manifests.read(after.resultRef())) || !receipt.equals(manifests.read(one(mapper.receipt(values))))) mismatch();
    }

    private void event(SourceIndexAccess.Admission admission,String type,JsonNode receipt) throws Exception {
        var body=json.object(); body.put("schema_version","1.0.0"); body.put("schema_manifest_sha256",manifests.sha256());
        body.put("event_id",(String)admission.values().get("eventId")); body.put("source_version",admission.row().sourceVersion());
        body.put("parse_version",admission.row().parseVersion()); body.set("result",receipt);
        changed(permissions.saveAudit(admission.row().createdBy(),admission.row().projectId(),UUID.randomUUID().toString(),type,
            manifests.canonical(body),UUID.randomUUID().toString(),manifests.sha256(),admission.row().classification()));
        var delivery=one(mapper.delivery(admission.values()));
        changed(permissions.saveEvent(admission.row().createdBy(),UUID.randomUUID().toString(),delivery.eventId(),
            delivery.revision().toString(),type,manifests.canonical(body)));
    }

    private record Claimed(SourceIndexProtocol.Build build,SourceUploadProtocol.CompletionDelivery delivery) { }
    private final class Guard implements S3ObjectReader.Admission {
        private final SourceIndexProtocol.Job job;
        private final SourceIndexProtocol.Build build;
        private SourceUploadProtocol.CompletionDelivery delivery;
        Guard(SourceIndexProtocol.Job job,SourceIndexProtocol.Build build,SourceUploadProtocol.CompletionDelivery delivery) {
            this.job=job; this.build=build; this.delivery=delivery;
        }
        @Override public void requireCurrent() {
            access.execute(job,admission -> { require(admission); renewInTransaction(admission); return null; });
        }
        void require(SourceIndexAccess.Admission admission) throws Exception {
            var current=one(mapper.build(admission.values())); var lease=one(mapper.delivery(admission.values()));
            verifyBuild(admission,current); verifyDelivery(admission,lease);
            if(!build.equals(current) || !delivery.equals(lease) || !"BUILDING".equals(build.status())
                    || !"DELIVERING".equals(lease.status()) || !owner.equals(lease.leaseOwner())
                    || lease.leaseUntil()==null || !lease.leaseUntil().isAfter(admission.now()) || !build.deadline().isAfter(admission.now())) stale();
            var currentManifest=manifests.manifest(admission.row(),job.event(),admission.command(),build.indexVersion(),
                currentChunks(admission.values()),policy);
            if(!currentManifest.equals(manifests.read(build.manifest()))) mismatch();
        }
        void renewInTransaction(SourceIndexAccess.Admission admission) {
            var values=admission.values(); LocalDateTime now=Objects.requireNonNull(permissions.databaseTime());
            if(!build.deadline().isAfter(now)) stale();
            leaseValues(values,delivery); values.put("now",now); values.put("leaseUntil",minimum(now.plus(policy.lease()),build.deadline()));
            changed(mapper.renew(values)); var renewed=one(mapper.delivery(values));
            if(!owner.equals(renewed.leaseOwner()) || !delivery.leaseGeneration().equals(renewed.leaseGeneration())
                    || !delivery.attemptNo().equals(renewed.attemptNo()) || !delivery.revision().add(BigInteger.ONE).equals(renewed.revision())) mismatch();
            delivery=renewed; leaseValues(values,delivery);
        }
        ObjectRef reference() {
            return access.execute(job,admission -> {
                require(admission); var row=admission.materialized(); var reference=json.object();
                reference.put("object_key",(String)row.get("artifactKey")); reference.put("object_version",(String)row.get("artifactVersion"));
                reference.put("sha256",(String)row.get("contentSha256")); reference.put("size_bytes",row.get("artifactSizeBytes").toString());
                reference.put("media_type","application/json");
                if(!reference.equals(manifests.read(admission.row().resultRef()).required("object_ref"))) mismatch();
                var object=new ObjectRef(reference.required("object_key").textValue(),reference.required("object_version").textValue(),
                    reference.required("sha256").textValue(),reference.required("size_bytes").textValue(),"application/json");
                if(!object.objectKey().startsWith("tenants/"+job.tenant()+"/source-parsed/"+build.sourceVersion()+"/"+build.parseVersion()+"/")
                        || new BigInteger(object.sizeBytes()).signum()<=0
                        || new BigInteger(object.sizeBytes()).compareTo(BigInteger.valueOf(policy.maximumBundleBytes()))>0) mismatch();
                return object;
            });
        }
    }

    private static void leaseValues(Map<String,Object> values,SourceUploadProtocol.CompletionDelivery delivery) {
        values.put("workerId",delivery.leaseOwner()); values.put("generation",delivery.leaseGeneration()); values.put("leaseRevision",delivery.revision());
    }
    private static void buildValues(Map<String,Object> values,SourceIndexProtocol.Build build) {
        values.put("indexVersion",build.indexVersion()); values.put("manifestSha256",build.manifestSha256()); values.put("buildRevision",build.revision());
    }
    private static LocalDateTime minimum(LocalDateTime first,LocalDateTime second) { return first.isBefore(second)?first:second; }
    private static <T> T one(List<T> rows) { if(rows.size()!=1) mismatch(); return rows.get(0); }
    private static void changed(int count) { if(count!=1) stale(); }
    private static void stale() { throw new TenantAccessException(ErrorCode.LEASE_STALE); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
