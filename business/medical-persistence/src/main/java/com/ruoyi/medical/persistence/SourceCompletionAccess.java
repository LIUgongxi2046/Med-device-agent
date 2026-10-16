package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import com.ruoyi.medical.persistence.mapper.SourceUploadMapper;
import com.ruoyi.medical.persistence.mapper.TenantMemberMapper;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

final class SourceCompletionAccess {
    private final JdbcTemplate jdbc;
    private final TenantTransaction transactions;
    private final PermissionMapper permissions;
    private final TenantMemberMapper members;
    private final SourceUploadMapper uploads;
    private final UploadTransferMapper transfers;
    private final SourceUploadService service;
    private final SourceUploadSchema schema;
    private final PermissionSchema json;
    private final SourceCompletionPolicy completion;
    private final SourceProcessingPolicy processing;
    private final SourceGovernanceMapper governance;
    private final PermissionAuthority authority;
    private final SourceProcessingResult results;
    private final OperationSchema operations;

    SourceCompletionAccess(DataSource data,TenantTransaction transactions,PermissionMapper permissions,TenantMemberMapper members,
            SourceUploadMapper uploads,UploadTransferMapper transfers,SourceUploadService service,SourceUploadSchema schema,
            PermissionSchema json,SourceCompletionPolicy completion,SourceProcessingPolicy processing,
            SourceGovernanceMapper governance,PermissionAuthority authority) {
        jdbc=new JdbcTemplate(data); this.transactions=transactions; this.permissions=permissions; this.members=members;
        this.uploads=uploads; this.transfers=transfers; this.service=service; this.schema=schema; this.json=json;
        this.completion=completion; this.processing=processing; this.governance=governance; this.authority=authority;
        results=ThrowingSupplier.of(SourceProcessingResult::new).get();
        operations=ThrowingSupplier.of(() -> new OperationSchema(json)).get();
        for(var binding:completion.bindings()) {
            if(!processing.tenantIds().contains(binding.tenantId())) denied(ErrorCode.ACCESS_DENIED);
            processing.binding(binding.configRevision());
        }
    }

    List<Job> pending() {
        var jobs=new java.util.ArrayList<Job>();
        for(var binding:completion.bindings()) {
            jobs.addAll(jdbc.query("""
                SELECT e.aggregate_id,JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.operation_id'))
                FROM outbox_delivery d JOIN outbox_event e ON e.tenant_id=d.tenant_id AND e.event_id=d.event_id
                JOIN source_upload_request r ON r.tenant_id=e.tenant_id AND r.upload_id=e.aggregate_id
                WHERE d.tenant_id=? AND r.project_id=? AND d.consumer_id='SOURCE_UPLOAD_COMPLETION'
                  AND e.event_type='source.upload.completion.requested'
                  AND d.next_attempt_at<=UTC_TIMESTAMP(6)
                  AND (d.status='PENDING' OR d.status='DELIVERING' AND d.lease_until<=UTC_TIMESTAMP(6))
                ORDER BY d.created_at,d.event_id LIMIT 10
                """,(row,n)->new Job(binding.tenantId(),row.getString(1),row.getString(2)),binding.tenantId(),binding.projectId()));
            if(jobs.size()>=100) return List.copyOf(jobs.subList(0,100));
        }
        return List.copyOf(jobs);
    }

    <T> T execute(Job job,Operation<T> action) {
        WireValues.uuid(job.tenant()); WireValues.uuid(job.upload()); WireValues.uuid(job.operation());
        var identity=one(jdbc.query("""
            SELECT r.created_by,r.project_id,t.auth_epoch,t.governance_epoch FROM source_upload_request r
            JOIN tenant t ON t.tenant_id=r.tenant_id
            WHERE r.tenant_id=? AND r.upload_id=?
            """,(row,n)->new Identity(row.getString(1),row.getString(2),new BigInteger(row.getString(3)),
                new BigInteger(row.getString(4))),job.tenant(),job.upload()));
        var configured=completion.binding(job.tenant(),identity.project());
        try(var scope=TenantScope.open(new TenantScope.Identity(job.tenant(),identity.actor(),BigInteger.ZERO,identity.auth(),identity.governance()))) {
            return transactions.execute(watermarks -> ThrowingSupplier.of(() -> {
                var values=new HashMap<String,Object>(); values.put("userId",identity.actor()); values.put("uploadId",job.upload());
                values.put("operationId",job.operation()); var declaration=one(uploads.declaration(values));
                var request=service.storedDeclaration(job.tenant(),identity.actor(),declaration);
                var binding=one(permissions.currentBinding(identity.actor()));
                List<String> accounts;
                if("local".equals(binding.mode())) {
                    if(!identity.actor().equals(binding.localUserId())) denied(ErrorCode.ACCESS_DENIED);
                    accounts=jdbc.query("SELECT user_id FROM platform_local_account WHERE user_id=? AND status='ACTIVE' FOR UPDATE",
                        (row,n)->row.getString(1),identity.actor());
                } else if("oidc".equals(binding.mode())) {
                    if(binding.localUserId()!=null) denied(ErrorCode.ACCESS_DENIED);
                    accounts=jdbc.query("""
                        SELECT user_id FROM platform_identity WHERE identity_id=? AND user_id=? AND issuer=? AND subject=?
                        AND status='ACTIVE' FOR UPDATE
                        """,(row,n)->row.getString(1),binding.identityId(),identity.actor(),binding.issuer(),binding.subject());
                } else throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
                one(accounts); LocalDateTime now=Objects.requireNonNull(permissions.databaseTime());
                String memberId=one(jdbc.query("SELECT member_id FROM tenant_member WHERE tenant_id=? AND user_id=? FOR UPDATE",
                    (row,n)->row.getString(1),job.tenant(),identity.actor()));
                var member=one(members.currentMember(identity.actor(),memberId));
                if(!"ACTIVE".equals(member.status()) || member.validFrom()==null || member.validFrom().isAfter(now)
                        || member.validTo()!=null && !member.validTo().isAfter(now)) denied(ErrorCode.ACCESS_DENIED);
                service.requireAuthority(job.tenant(),identity.actor(),binding.mode(),request.content(),now);
                service.maintainer(declaration.maintainerId(),now);
                var source=one(transfers.source(values)); var session=one(transfers.session(values));
                service.requireCorrespondence(declaration,request,source,session);
                if(!"COMPLETING".equals(session.state()) || !"UPLOADING".equals(source.status())
                        || !source.receivedSize().equals(source.expectedSize())) denied(ErrorCode.ACTION_STALE);
                var command=schema.completionCommand(one(uploads.completionCommand(values)));
                if(!"1.0.0".equals(command.required("schema_version").textValue())
                        || !job.tenant().equals(command.required("context").required("tenant_id").textValue())
                        || !identity.actor().equals(command.required("subject_id").textValue())
                        || !job.upload().equals(command.required("upload_id").textValue())
                        || !job.operation().equals(command.required("operation_id").textValue())
                        || !SourceUploadProtocol.COMPLETION_CONSUMER.equals(command.required("consumer_id").textValue())
                        || !service.manifestSha256().equals(command.required("schema_manifest_sha256").textValue())
                        || !source.revision().toString().equals(command.required("source_revision").textValue())
                        || !session.revision().toString().equals(command.required("session_revision").textValue())) denied(ErrorCode.CONTENT_MISMATCH);
                var completed=schema.completion(json.canonical(command.required("request")).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    source.expectedSize(),request);
                if(!completed.expectedRevision().equals(source.revision())
                        || !schema.schemaSha256().equals(command.required("schema_document_sha256").textValue())
                        || !completed.schemaRef().equals(command.required("schema_ref").textValue())) denied(ErrorCode.CONTENT_MISMATCH);
                var identityBody=json.object(); identityBody.put("method","POST");
                identityBody.put("path","/api/uploads/"+job.upload()+"/completions"); identityBody.putNull("query");
                identityBody.put("schema_version","1.0.0"); identityBody.put("schema_ref",completed.schemaRef());
                identityBody.put("schema_sha256",completed.schemaSha256()); identityBody.set("body",completed.content());
                String requestSha256=org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(identityBody));
                var operation=one(uploads.completionOperationById(values));
                if(!requestSha256.equals(command.required("request_sha256").textValue())
                        || !requestSha256.equals(operation.requestSha256()) || !job.operation().equals(operation.operationId())
                        || !job.upload().equals(operation.targetId()) || !identity.actor().equals(operation.subjectId())
                        || !identity.actor().equals(operation.createdBy()) || !job.tenant().equals(operation.tenantId())
                        || !"KN-03".equals(operation.operationName()) || !"PROCESSING".equals(operation.state())
                        || !BigInteger.ONE.equals(operation.revision()) || !"1.0.0".equals(operation.schemaVersion())
                        || !service.manifestSha256().equals(operation.schemaManifestSha256())
                        || !operation.classification().equals(source.classification()) || operation.httpStatus()!=null
                        || operation.responseRef()!=null || operation.errorCode()!=null || operation.dedupExpiresAt()!=null
                        || operation.dedupPolicyRevision()!=null || !operation.createdAt().equals(operation.updatedAt())) denied(ErrorCode.CONTENT_MISMATCH);
                var response=command.required("response");
                var expectedResponse=operations.operation(new com.ruoyi.medical.contracts.InterfaceProtocol.Operation(
                    job.operation(),job.upload(),com.ruoyi.medical.contracts.InterfaceProtocol.OperationStatus.PROCESSING,
                    "/api/operations/"+job.operation()),response.required("request_id").textValue());
                if(!expectedResponse.equals(response)) denied(ErrorCode.CONTENT_MISMATCH);
                values.put("configRevision",configured.configRevision()); var capability=one(governance.processingConfig(values));
                var runtime=processing.binding(configured.configRevision());
                if(!"PUBLISHED".equals(capability.status()) || !runtime.configSha256().equals(capability.registeredSha256())
                        || !runtime.configSha256().equals(capability.contentSha256())
                        || capability.projectId()!=null && !identity.project().equals(capability.projectId())) denied(ErrorCode.OBJECT_UNAVAILABLE);
                results.requireCapability(capability,runtime,processing);
                authority.requireResource(new PermissionProtocol.Scope(job.tenant(),capability.projectId(),"CONFIGURATION",capability.configId()),
                    identity.actor(),binding.mode(),"configuration.read","SOURCE_PROCESSING",permissions.resourceGrants(identity.actor(),"configuration.read"),now);
                return action.apply(new Admission(declaration,request,completed,source,session,command,runtime,now,watermarks));
            }).get());
        }
    }

    record Job(String tenant,String upload,String operation) { }
    record Admission(SourceUploadProtocol.Declaration declaration,SourceUploadSchema.ValidatedCreate request,
        SourceUploadSchema.ValidatedCompletion completion,UploadTransferProtocol.SourceRow source,UploadTransferProtocol.SessionRow session,
        JsonNode command,SourceProcessingPolicy.Binding binding,LocalDateTime now,TenantWatermarks watermarks) { }
    private record Identity(String actor,String project,BigInteger auth,BigInteger governance) { }
    @FunctionalInterface interface Operation<T> { T apply(Admission admission) throws Exception; }
    private static <T> T one(List<T> rows) { if(rows.size()!=1) denied(ErrorCode.ACCESS_DENIED); return rows.get(0); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
}
