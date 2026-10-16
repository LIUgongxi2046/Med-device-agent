package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceCompletionMapper;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import com.ruoyi.medical.persistence.mapper.SourceProcessingMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import com.ruoyi.medical.persistence.mapper.SourceUploadMapper;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="medical.source-completion",name="enabled",havingValue="true")
public final class SourceCompletionRegistration {
    private final SourceCompletionLeaseStore leases;
    private final SourceCompletionCheckpointStore checkpoints;
    private final SourceCompletionMapper mapper;
    private final SourceGovernanceMapper governance;
    private final SourceProcessingMapper processing;
    private final SourceUploadMapper uploads;
    private final UploadTransferMapper transfers;
    private final SourceReadMapper sources;
    private final SourceRightsSchema rights;
    private final PermissionMapper permissions;
    private final PermissionAuthority authority;
    private final PermissionSchema json;
    private final SourceProcessingPolicy policy;
    private final SourceProcessingResult results;
    private final SourceUploadSchema schema;

    SourceCompletionRegistration(SourceCompletionLeaseStore leases,SourceCompletionCheckpointStore checkpoints,
            SourceCompletionMapper mapper,SourceGovernanceMapper governance,SourceProcessingMapper processing,
            SourceUploadMapper uploads,UploadTransferMapper transfers,SourceReadMapper sources,SourceRightsSchema rights,
            PermissionMapper permissions,PermissionAuthority authority,PermissionSchema json,SourceProcessingPolicy policy,SourceUploadSchema schema) throws Exception {
        this.leases=leases; this.checkpoints=checkpoints; this.mapper=mapper; this.governance=governance; this.processing=processing;
        this.uploads=uploads; this.transfers=transfers; this.sources=sources; this.rights=rights; this.permissions=permissions;
        this.authority=authority; this.json=json; this.policy=policy; this.schema=schema; results=new SourceProcessingResult();
    }

    void register(SourceCompletionAccess.Admission admission,SourceCompletionLeaseStore.Lease lease,
            SourceCompletionCheckpointStore.Row checkpoint,ObjectRef original,Candidate verifiedDuplicate) throws Exception {
        leases.requireCurrent(lease);
        if(!checkpoint.equals(checkpoints.read(lease)) || !"ORIGINAL_VERIFIED".equals(checkpoint.status())
                || !json.canonical(json.tree(original)).equals(json.canonical(results.read(checkpoint.objectRef())))) mismatch();
        String tenant=lease.row().tenantId(),actor=lease.row().createdBy(); var body=admission.request().content();
        String project=admission.declaration().projectId(),ownership=body.required("ownership").textValue();
        String ownerProject="ENTERPRISE".equals(ownership)?null:project;
        var values=new HashMap<String,Object>(); values.put("userId",actor); values.put("projectId",project); values.put("targetProjectId",ownerProject);
        values.put("ownerProjectId",ownerProject); values.put("uploadId",lease.uploadId()); values.put("operationId",lease.operationId());
        values.put("eventId",lease.row().eventId()); values.put("consumerId",lease.row().consumerId());
        values.put("now",admission.now()); values.put("classification",admission.declaration().classification());
        values.put("artifactSha256",original.sha256()); values.put("sizeBytes",WireValues.revision(original.sizeBytes()));
        values.put("objectKey",original.objectKey()); values.put("objectVersion",original.objectVersion()); values.put("mediaType",original.mediaType());
        values.put("ownership",ownership); values.put("sessionId",nullable(body,"session_id"));
        String ownershipScope="ENTERPRISE".equals(ownership)?tenant:"PROJECT".equals(ownership)?project:nullable(body,"session_id");
        WireValues.uuid(ownershipScope); values.put("ownershipScopeId",ownershipScope);
        var candidates=mapper.duplicate(values); if(candidates.size()>1) mismatch();
        Candidate duplicate=null;
        if(!candidates.isEmpty() && sameDeclaration(body,candidates.get(0))) {
            duplicate=candidates.get(0); requireDuplicate(admission,duplicate,tenant,actor,ownerProject);
        }
        if(!Objects.equals(duplicate,verifiedDuplicate)) mismatch();
        var receipt=json.object(); receipt.put("schema_version","1.0.0"); receipt.put("upload_id",lease.uploadId());
        receipt.put("operation_id",lease.operationId()); receipt.put("event_id",lease.row().eventId());
        receipt.put("consumer_id",lease.row().consumerId()); receipt.put("input_sha256",DigestUtils.sha256Hex(lease.command()));
        receipt.put("ownership_scope_id",ownershipScope); receipt.put("original_sha256",original.sha256()); receipt.set("original_object_ref",json.tree(original));
        if(duplicate!=null) {
            receipt.put("status","DUPLICATE"); receipt.put("source_id",duplicate.sourceId()); receipt.put("source_version",duplicate.sourceVersion());
            receipt.put("rights_revision",duplicate.rightsRevision()); receipt.putNull("parse_version"); receipt.putNull("attempt_id"); receipt.putNull("run_id");
            values.put("sourceId",duplicate.sourceId()); values.put("sourceVersion",duplicate.sourceVersion());
            values.put("rightsRevision",duplicate.rightsRevision()); values.put("parseVersion",null); values.put("attemptId",null);
        } else {
            String sourceId="NEW_VERSION".equals(body.required("version_intent").textValue())?nullable(body,"source_id"):id();
            if("NEW_VERSION".equals(body.required("version_intent").textValue())) {
                values.put("sourceId",sourceId); var owner=one(uploads.sourceOwner(values));
                if(!sourceId.equals(owner.sourceId()) || !ownership.equals(owner.ownership())
                        || !Objects.equals(ownerProject,owner.ownerProjectId()) || !values.get("classification").equals(owner.classification())) mismatch();
            }
            String sourceVersion=id(),rightsRevision=id(),parseVersion=id(),attempt=id(),run=id();
            values.put("sourceId",sourceId); values.put("sourceVersion",sourceVersion); values.put("rightsRevision",rightsRevision);
            values.put("parseVersion",parseVersion); values.put("attemptId",attempt); values.put("runId",run);
            values.put("versionNo",mapper.versionNumber(values).add(BigInteger.ONE)); WireValues.revision(values.get("versionNo").toString());
            values.put("title",body.required("file_name").textValue()); values.put("publisherVersion",nullable(body.required("provenance"),"publisher_version"));
            values.put("maintainerId",admission.declaration().maintainerId()); values.put("category",null);
            var provenance=(ObjectNode)body.required("provenance").deepCopy(); provenance.put("upload_id",lease.uploadId());
            provenance.put("upload_request_sha256",admission.declaration().requestSha256());
            provenance.set("rights_declaration",body.required("rights_declaration").deepCopy()); values.put("provenance",json.canonical(provenance));
            values.put("schemaManifest",results.manifest()); pendingRights(tenant,ownerProject,sourceVersion,rightsRevision,body,values);
            if("NEW_SOURCE".equals(body.required("version_intent").textValue())) changed(governance.insertSubmissionSource(values));
            changed(governance.insertSubmissionSourceVersionRegistry(values)); changed(mapper.saveSourceVersion(values));
            changed(governance.insertSubmissionRightsRegistry(values)); changed(governance.insertSubmissionRights(values));
            changed(governance.insertSubmissionRightsState(values));
            changed(governance.insertSubmissionLifecycle(values));
            if("NEW_SOURCE".equals(body.required("version_intent").textValue())) {
                changed(governance.insertSubmissionOrganization(values));
                if("SESSION".equals(ownership)) changed(mapper.saveSessionSource(values));
            }
            if(candidates.isEmpty()) changed(mapper.saveDedup(values));
            var processor=json.object(); processor.put("schema_version","1.0.0"); processor.put("config_revision",admission.binding().configRevision());
            processor.put("config_sha256",admission.binding().configSha256()); values.put("processorManifest",json.canonical(processor));
            var request=json.object(); request.put("schema_version","1.0.0"); request.put("source_version",sourceVersion);
            request.put("project_id",project); request.put("config_revision",admission.binding().configRevision());
            request.put("stage",admission.binding().indexConfigRevision()==null?"PARSE_ONLY":"PARSE_AND_INDEX");
            request.put("expected_revision","1"); values.put("requestManifest",json.canonical(request));
            var scope=request.deepCopy(); scope.put("upload_id",lease.uploadId()); scope.put("event_id",lease.row().eventId());
            scope.put("input_sha256",DigestUtils.sha256Hex(lease.command())); values.put("scopeManifest",json.canonical(scope));
            values.put("configRevision",admission.binding().configRevision());
            changed(governance.insertParseVersion(values)); changed(governance.insertSourceGate(values));
            changed(governance.insertExecution(values)); changed(governance.insertProcessingAttempt(values));
            var savedSource=one(sources.source(sourceVersion)); var savedRights=one(sources.rights(rightsRevision)); rights.verify(savedRights);
            var savedAttempt=one(processing.current(values));
            if(!sourceId.equals(savedSource.sourceId()) || !original.sha256().equals(savedSource.originalSha256())
                    || !original.sha256().equals(savedSource.registeredSha256()) || !"PENDING_REVIEW".equals(savedRights.status())
                    || !parseVersion.equals(savedAttempt.parseVersion()) || !attempt.equals(savedAttempt.attemptId())
                    || !run.equals(savedAttempt.runId()) || !"QUEUED".equals(savedAttempt.status()) || !"QUEUED".equals(savedAttempt.parseStatus())
                    || !"ACTIVE".equals(savedAttempt.lifecycle()) || !original.objectKey().equals(savedAttempt.objectKey())
                    || !original.objectVersion().equals(savedAttempt.objectVersion()) || !original.sizeBytes().equals(savedAttempt.sizeBytes().toString())) mismatch();
            receipt.put("status","REGISTERED"); receipt.put("source_id",sourceId); receipt.put("source_version",sourceVersion);
            receipt.put("rights_revision",rightsRevision); receipt.put("parse_version",parseVersion); receipt.put("attempt_id",attempt); receipt.put("run_id",run);
        }
        values.put("objectRef",json.canonical(json.tree(original))); values.put("expected",admission.session().revision());
        values.put("contentSha256",original.sha256()); changed(transfers.complete(values));
        values.put("sourceRevision",admission.source().revision()); changed(mapper.completeUpload(values));
        values.put("response",json.canonical(admission.command().required("response")));
        values.put("requestSha256",admission.command().required("request_sha256").textValue()); changed(mapper.completeOperation(values));
        schema.completionResult(json.canonical(receipt)); values.put("schemaManifest",schema.completionResultManifest());
        values.put("inputSha256",DigestUtils.sha256Hex(lease.command())); values.put("resultRef",json.canonical(receipt)); changed(mapper.saveReceipt(values));
        changed(mapper.saveUploadResult(values));
        values.put("leaseOwner",lease.row().leaseOwner()); values.put("generation",lease.row().leaseGeneration()); values.put("leaseRevision",lease.row().revision());
        changed(mapper.completeDelivery(values)); values.put("governanceEpoch",admission.watermarks().governanceEpoch()); changed(processing.advanceGovernance(values));
        String requestId=id(); changed(permissions.saveAudit(actor,project,id(),"source.upload.completed",json.canonical(receipt),requestId,schema.completionResultManifest(),admission.declaration().classification()));
        changed(permissions.saveEvent(actor,id(),lease.uploadId(),admission.source().revision().add(BigInteger.ONE).toString(),"source.upload.completed",json.canonical(receipt)));
        if(!json.canonical(receipt).equals(json.canonical(schema.completionResult(one(mapper.receipt(values)))))) mismatch();
        var source=one(transfers.source(values)); var session=one(transfers.session(values));
        if(!"RECEIVED".equals(source.status()) || !"COMPLETED".equals(session.state()) || !original.sha256().equals(source.actualSha256())
                || !admission.source().revision().add(BigInteger.ONE).equals(source.revision())
                || !admission.session().revision().add(BigInteger.ONE).equals(session.revision())
                || !json.canonical(json.tree(original)).equals(json.canonical(results.read(source.objectRef())))) mismatch();
    }

    private void pendingRights(String tenant,String ownerProject,String sourceVersion,String revision,JsonNode declaration,Map<String,Object> values) throws Exception {
        var input=declaration.required("rights_declaration"); var evidence=json.object();
        evidence.put("owner",declaration.required("provenance").required("owner").textValue()); evidence.put("license",input.required("license").textValue());
        evidence.putArray("environments").add(policy.environment()); evidence.putArray("classifications").add(declaration.required("classification").textValue());
        evidence.putArray("authorization_evidence");
        var body=json.object(); body.put("schema_version","1.0.0"); body.put("tenant_id",tenant); body.put("source_version",sourceVersion); body.put("rights_revision",revision);
        body.put("purpose",input.required("purpose").textValue()); body.set("allowed_actions",input.required("allowed_actions").deepCopy());
        body.set("providers",input.required("providers").deepCopy()); body.set("regions",input.required("regions").deepCopy());
        body.set("valid_from",input.required("valid_from").deepCopy()); body.set("valid_to",input.required("valid_to").deepCopy());
        body.set("evidence_manifest",evidence); body.put("classification",declaration.required("classification").textValue());
        LocalDateTime from=time(input,"valid_from"),to=time(input,"valid_to");
        if(from!=null) body.put("valid_from",from.toInstant(ZoneOffset.UTC).toString());
        if(to!=null) body.put("valid_to",to.toInstant(ZoneOffset.UTC).toString());
        String hash=DigestUtils.sha256Hex(json.canonical(body));
        rights.verify(new SourceReadProtocol.RightsRow(tenant,revision,sourceVersion,input.required("purpose").textValue(),
            json.canonical(input.required("allowed_actions")),json.canonical(input.required("providers")),json.canonical(input.required("regions")),
            from,to,"PENDING_REVIEW",json.canonical(evidence),hash,"1.0.0",rights.pendingManifestSha256(),
            declaration.required("classification").textValue(),hash,ownerProject));
        values.put("rightsPurpose",input.required("purpose").textValue()); values.put("allowedActions",json.canonical(input.required("allowed_actions")));
        values.put("providers",json.canonical(input.required("providers"))); values.put("regions",json.canonical(input.required("regions")));
        values.put("validFrom",from); values.put("validTo",to); values.put("rightsEvidence",json.canonical(evidence));
        values.put("rightsSha256",hash); values.put("rightsSchemaManifest",rights.pendingManifestSha256());
    }

    private boolean sameDeclaration(JsonNode body,Candidate candidate) throws Exception {
        if("NEW_VERSION".equals(body.required("version_intent").textValue()) && !candidate.sourceId().equals(nullable(body,"source_id"))) return false;
        if(!body.required("ownership").textValue().equals(candidate.ownership()) || !body.required("classification").textValue().equals(candidate.classification())
                || !body.required("media_type").textValue().equals(candidate.mediaType()) || !body.required("size_bytes").textValue().equals(candidate.sizeBytes().toString())) return false;
        var provenance=results.read(candidate.provenance());
        if(!body.required("rights_declaration").equals(provenance.get("rights_declaration"))) return false;
        var fields=body.required("provenance").fieldNames();
        while(fields.hasNext()) { String name=fields.next(); if(!body.required("provenance").required(name).equals(provenance.get(name))) return false; }
        return true;
    }

    Candidate duplicate(SourceCompletionAccess.Admission admission) throws Exception {
        String tenant=TenantScope.requireCurrent().tenantId(),actor=TenantScope.requireCurrent().subjectId();
        var body=admission.request().content(); String ownership=body.required("ownership").textValue();
        String project=admission.declaration().projectId(),ownerProject="ENTERPRISE".equals(ownership)?null:project;
        var values=new HashMap<String,Object>(); values.put("userId",actor);
        values.put("ownershipScopeId","ENTERPRISE".equals(ownership)?tenant:"PROJECT".equals(ownership)?project:nullable(body,"session_id"));
        values.put("artifactSha256",admission.completion().sha256());
        var candidates=mapper.duplicate(values); if(candidates.size()>1) mismatch();
        if(candidates.isEmpty() || !sameDeclaration(body,candidates.get(0))) return null;
        var candidate=candidates.get(0); requireDuplicate(admission,candidate,tenant,actor,ownerProject); return candidate;
    }

    private void requireDuplicate(SourceCompletionAccess.Admission admission,Candidate candidate,String tenant,String actor,String ownerProject) throws Exception {
        if(!Objects.equals(ownerProject,candidate.ownerProjectId()) || !candidate.originalSha256().equals(admission.completion().sha256())) mismatch();
        var source=one(sources.source(candidate.sourceVersion())); var permission=one(permissions.currentBinding(actor)); var license=one(sources.rights(candidate.rightsRevision()));
        if(!tenant.equals(source.tenantId()) || !candidate.sourceId().equals(source.sourceId())
                || !candidate.originalSha256().equals(source.originalSha256()) || !source.originalSha256().equals(source.registeredSha256())
                || !Objects.equals(candidate.ownerProjectId(),source.registeredProjectId())) mismatch();
        authority.requireResource(new PermissionProtocol.Scope(tenant,candidate.ownerProjectId(),"SOURCE",candidate.sourceId()),actor,permission.mode(),
            "source.read",license.purpose(),permissions.resourceGrants(actor,"source.read"),admission.now());
        String project=admission.declaration().projectId(),session=nullable(admission.request().content(),"session_id");
        authority.requireResource(new PermissionProtocol.Scope(tenant,project,"PROJECT",project),actor,permission.mode(),
            "project.read",license.purpose(),permissions.resourceGrants(actor,"project.read"),admission.now());
        if("SESSION".equals(candidate.ownership())) {
            var conversation=one(sources.conversation(session).stream().filter(row -> candidate.sourceId().equals(row.sourceId())).toList());
            if(!tenant.equals(conversation.tenantId()) || !project.equals(conversation.projectId())
                    || !session.equals(conversation.conversationId()) || !actor.equals(conversation.ownerId())
                    || !"ACTIVE".equals(conversation.status())) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        }
        rights.require(license,source,new SourceReadProtocol.ReadRequirement(candidate.sourceVersion(),candidate.rightsRevision(),admission.declaration().projectId(),
            null,null,nullable(admission.request().content(),"session_id"),SourceReadProtocol.Use.REVIEW,license.purpose(),policy.environment(),policy.region(),null),admission.now());
        var gates=sources.gates(candidate.sourceVersion());
        if(gates.isEmpty() || gates.stream().anyMatch(gate -> !"ACTIVE".equals(gate.lifecycle())))
            throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);
    }

    private static LocalDateTime time(JsonNode value,String name) { return value.required(name).isNull()?null:LocalDateTime.ofInstant(WireValues.utcTime(value.required(name).textValue()),ZoneOffset.UTC); }
    private static String nullable(JsonNode value,String name) { JsonNode item=value.get(name); return item==null || item.isNull()?null:item.textValue(); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static <T> T one(List<T> rows) { if(rows.size()!=1) mismatch(); return rows.get(0); }
    private static void changed(int count) { if(count!=1) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    public record Candidate(String sourceId,String sourceVersion,String ownerProjectId,String ownership,String classification,String rightsRevision,
        String originalSha256,String objectKey,String objectVersion,String mediaType,BigInteger sizeBytes,String provenance) { }
}
