package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceIndexMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
@EnableConfigurationProperties({SourceIndexViewPolicy.class,SourceUploadStatusPolicy.class})
public final class SourceIndexViewService {
    private final EnterpriseAccess access;
    private final SourceGovernanceService governance;
    private final PermissionAuthority authority;
    private final PermissionMapper permissions;
    private final SourceIndexMapper mapper;
    private final SourceIndexViewPolicy policy;
    private final SourceUploadStatusPolicy reviewPolicy;
    private final SourceIndexViewSchema schema;
    private final PermissionSchema json;
    private final SourceIndexManifest manifests;

    public SourceIndexViewService(EnterpriseAccess access,SourceGovernanceService governance,PermissionAuthority authority,
            PermissionMapper permissions,SourceIndexMapper mapper,SourceIndexViewPolicy policy,
            SourceUploadStatusPolicy reviewPolicy,SourceIndexViewSchema schema,PermissionSchema json) throws Exception {
        this.access=access; this.governance=governance; this.authority=authority; this.permissions=permissions;
        this.mapper=mapper; this.policy=policy; this.schema=schema; this.json=json; manifests=new SourceIndexManifest();
        this.reviewPolicy=reviewPolicy;
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String sourceVersion,String projectId) {
        return access.execute(subject,admission -> ThrowingSupplier.of(() -> {
            if(!context.equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            policy.requireConfigured(); reviewPolicy.requireConfigured();
            WireValues.uuid(sourceVersion); WireValues.uuid(projectId);
            authority.requireProject(admission.context().tenant_id(),admission.subject().binding().view().mode(),projectId,
                "project.read","PROJECT_READING",permissions.resourceGrants(subject.user().user_id(),"project.read"));
            var metadata=governance.uploadStatus(admission,sourceVersion,null,reviewPolicy.maximumReviews());
            var values=new HashMap<String,Object>(); values.put("userId",subject.user().user_id());
            values.put("sourceVersion",sourceVersion); values.put("projectId",projectId); values.put("fetchLimit",policy.maximumBuilds()+1);
            var rows=mapper.views(values); if(rows.size()>policy.maximumBuilds()) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            var items=json.object().arrayNode(); var ids=new java.util.HashSet<String>();
            for(var row:rows) {
                if(!admission.context().tenant_id().equals(row.tenantId()) || !projectId.equals(row.projectId())
                        || !sourceVersion.equals(row.sourceVersion()) || !ids.add(row.indexVersion())) mismatch();
                items.add(item(row));
            }
            var identity=json.object(); identity.put("source_version",sourceVersion); identity.put("project_id",projectId); identity.set("items",items);
            var result=json.object(); result.put("schema_version","1.0.0"); result.put("source_id",metadata.head().sourceId());
            result.put("source_version",sourceVersion); result.put("project_id",projectId); result.put("total",Integer.toString(items.size()));
            result.put("listing_sha256",manifests.hash(identity)); result.set("items",items);
            return schema.envelope(result,UUID.randomUUID().toString());
        }).get());
    }

    private JsonNode item(SourceIndexProtocol.View row) throws Exception {
        for(String id:java.util.List.of(row.eventId(),row.parseVersion(),row.indexVersion())) WireValues.uuid(id);
        for(String hash:java.util.List.of(row.manifestSha256(),row.indexSha256(),row.registeredSha256(),row.inputSha256())) WireValues.hash(hash);
        WireValues.revision(row.revision().toString()); WireValues.revision(row.expectedCount().toString()); WireValues.revision(row.verifiedCount().toString());
        if(!"FULLTEXT".equals(row.indexMode()) || row.revision().signum()<=0 || row.expectedCount().signum()<=0
                || row.verifiedCount().signum()<0 || row.verifiedCount().compareTo(row.expectedCount())>0
                || !row.manifestSha256().equals(row.indexSha256()) || !row.manifestSha256().equals(row.registeredSha256())
                || !manifests.sha256().equals(row.schemaManifestSha256()) || !row.schemaManifestSha256().equals(row.deliverySchema())
                || !row.createdBy().equals(row.deliveryCreator()) || !row.deadline().isAfter(row.startedAt())
                || row.updatedAt().isBefore(row.startedAt())) mismatch();
        var result=json.object(); result.put("event_id",row.eventId()); result.put("parse_version",row.parseVersion()); result.put("index_version",row.indexVersion());
        result.put("mode",row.indexMode()); result.put("build_status",row.buildStatus()); result.put("index_status",row.indexStatus());
        result.put("delivery_status",row.deliveryStatus()); result.put("expected_count",row.expectedCount().toString());
        result.put("verified_count",row.verifiedCount().toString()); result.put("started_at",time(row.startedAt()));
        result.put("execution_deadline",time(row.deadline())); result.put("updated_at",time(row.updatedAt())); result.put("revision",row.revision().toString());
        if("BUILDING".equals(row.buildStatus())) {
            if(row.resultRef()!=null || row.receiptRef()!=null || row.receiptInputSha256()!=null || row.receiptSchema()!=null || row.receiptCreator()!=null
                    || row.verifiedCount().signum()!=0 || !java.util.List.of("BUILDING","WITHDRAWN").contains(row.indexStatus())
                    || !java.util.List.of("PENDING","DELIVERING").contains(row.deliveryStatus())) mismatch();
            result.putNull("result");
        } else {
            if(!Objects.equals(row.createdBy(),row.receiptCreator()) || !Objects.equals(row.schemaManifestSha256(),row.receiptSchema())
                    || !Objects.equals(row.inputSha256(),row.receiptInputSha256())) mismatch();
            var receipt=manifests.read(Objects.requireNonNull(row.resultRef())); manifests.require("Receipt",receipt);
            if(!receipt.equals(manifests.read(Objects.requireNonNull(row.receiptRef())))
                    || !row.eventId().equals(receipt.required("event_id").textValue())
                    || !row.sourceVersion().equals(receipt.required("source_version").textValue())
                    || !row.parseVersion().equals(receipt.required("parse_version").textValue())
                    || !row.indexVersion().equals(receipt.required("index_version").textValue())
                    || !row.inputSha256().equals(receipt.required("input_sha256").textValue())
                    || !row.manifestSha256().equals(receipt.required("manifest_sha256").textValue())
                    || !row.buildStatus().equals(receipt.required("status").textValue())
                    || !row.expectedCount().toString().equals(receipt.required("expected_count").asText())
                    || !row.verifiedCount().toString().equals(receipt.required("verified_count").asText())) mismatch();
            if("READY".equals(row.buildStatus())) {
                if(!row.expectedCount().equals(row.verifiedCount()) || !"DELIVERED".equals(row.deliveryStatus())
                        || !java.util.List.of("READY","WITHDRAWN").contains(row.indexStatus()) || !receipt.required("failure_code").isNull()) mismatch();
            } else if(!"FAILED".equals(row.buildStatus()) || row.verifiedCount().signum()!=0
                    || !java.util.List.of("FAILED","WITHDRAWN").contains(row.indexStatus())
                    || !java.util.List.of("FAILED","PENDING").contains(row.deliveryStatus())
                    || !"INDEX_LIMIT".equals(receipt.required("failure_code").textValue())) mismatch();
            var publicResult=json.object(); publicResult.put("status",row.buildStatus()); publicResult.put("expected_count",row.expectedCount().toString());
            publicResult.put("verified_count",row.verifiedCount().toString()); publicResult.set("failure_code",receipt.required("failure_code"));
            result.set("result",publicResult);
        }
        schema.require("Build",result); return result;
    }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
