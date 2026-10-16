package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
@EnableConfigurationProperties(SourceUploadStatusPolicy.class)
public final class SourceUploadStatusService {
    private final EnterpriseAccess access;
    private final SourceUploadService uploads;
    private final SourceGovernanceService governance;
    private final SourceGovernanceMapper attempts;
    private final SourceReadMapper sources;
    private final SourceRightsSchema rights;
    private final SourceUploadStatusSchema schema;
    private final SourceUploadStatusPolicy policy;
    private final PermissionSchema json;

    public SourceUploadStatusService(EnterpriseAccess access,SourceUploadService uploads,SourceGovernanceService governance,
            SourceGovernanceMapper attempts,SourceReadMapper sources,SourceRightsSchema rights,SourceUploadStatusSchema schema,
            SourceUploadStatusPolicy policy,PermissionSchema json) {
        this.access=access; this.uploads=uploads; this.governance=governance; this.attempts=attempts; this.sources=sources;
        this.rights=rights; this.schema=schema; this.policy=policy; this.json=json;
    }

    JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String upload) {
        policy.requireConfigured(); WireValues.uuid(upload); WireValues.revision(context);
        return access.execute(subject,admission -> ThrowingSupplier.of(() -> {
            var snapshot=uploads.status(admission,context,upload); var registration=snapshot.registration();
            var value=json.object(); value.put("schema_version","1.0.0"); value.put("upload_id",upload);
            value.set("upload_revision",registration.required("resource_revision"));
            for(String name:List.of("received_size","upload_status","transfer_state")) value.set(name,registration.required("data").required(name));
            value.set("completion",snapshot.completion()==null?json.tree(null):snapshot.completion()); value.putNull("source");
            value.putNull("attempt");
            if(snapshot.result()!=null && !"FAILED".equals(snapshot.result().required("status").textValue())) {
                var result=snapshot.result(); String sourceVersion=result.required("source_version").textValue();
                String parse=result.required("parse_version").isNull()?null:result.required("parse_version").textValue();
                var status=governance.uploadStatus(admission,sourceVersion,parse,policy.maximumReviews());
                var head=status.head(); var source=single(sources.source(sourceVersion)); var current=single(sources.rights(source.rightsRevision()));
                rights.verify(current);
                if(!source.tenantId().equals(admission.context().tenant_id()) || !source.sourceId().equals(result.required("source_id").textValue())
                        || !source.sourceId().equals(head.sourceId()) || !source.sourceVersion().equals(head.sourceVersion())
                        || !source.originalSha256().equals(result.required("original_sha256").textValue())
                        || !source.originalSha256().equals(source.registeredSha256()) || !source.originalSha256().equals(head.originalSha256())
                        || !java.util.Objects.equals(source.ownerProjectId(),source.registeredProjectId())
                        || !java.util.Objects.equals(source.ownerProjectId(),current.registeredProjectId())
                        || !java.util.Objects.equals(source.ownerProjectId(),head.ownerProjectId())
                        || !source.rightsRevision().equals(head.rightsRevision()) || !source.rightsRevision().equals(current.rightsRevision())
                        || !source.sourceVersion().equals(current.sourceVersion()) || !source.tenantId().equals(current.tenantId())
                        || !source.classification().equals(current.classification()) || !head.rightsStatus().equals(current.status())) mismatch();
                var details=json.object(); details.put("source_id",source.sourceId()); details.put("source_version",sourceVersion);
                details.put("original_sha256",source.originalSha256());
                details.set("current_rights",json.tree(new PermissionProtocol.Basis(current.rightsRevision(),current.contentSha256())));
                details.put("rights_status",current.status()); details.put("lifecycle",status.lifecycle()); details.set("reviews",status.reviews());
                value.set("source",details);
                if(!result.required("attempt_id").isNull()) {
                    var row=single(attempts.processingAttempt(Map.of("userId",subject.user().user_id(),
                        "attemptId",result.required("attempt_id").textValue())));
                    if(!row.tenantId().equals(admission.context().tenant_id()) || !row.sourceVersion().equals(sourceVersion)
                            || !row.parseVersion().equals(parse) || !row.runId().equals(result.required("run_id").textValue())
                            || !"PARSE".equals(row.jobType()) || !row.parseStatus().equals(head.parseStatus())
                            || !row.createdBy().equals(subject.user().user_id()) || !row.classification().equals(source.classification())) mismatch();
                    var attempt=json.object(); attempt.put("attempt_id",row.attemptId()); attempt.put("run_id",row.runId());
                    attempt.put("config_revision",row.configRevision()); attempt.put("status",row.status());
                    attempt.put("revision",row.revision().toString()); attempt.put("updated_at",row.updatedAt().toInstant(java.time.ZoneOffset.UTC).toString());
                    attempt.set("failure_manifest",com.fasterxml.jackson.databind.json.JsonMapper.builder()
                        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(row.failureManifest()));
                    value.set("attempt",attempt);
                }
            }
            return schema.envelope(value,registration.required("resource_revision").textValue());
        }).get());
    }

    private static <T> T single(List<T> rows) { if(rows.size()!=1) throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE); return rows.get(0); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
