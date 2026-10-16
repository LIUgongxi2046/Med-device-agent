package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import com.ruoyi.medical.persistence.mapper.SourceRightsMapper;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
@EnableConfigurationProperties(SourceRightsPolicy.class)
public final class SourceRightsService {
    private final EnterpriseAccess access;
    private final ResourceReadAuthority authority;
    private final SourceReadMapper sources;
    private final SourceRightsMapper mapper;
    private final SourceGovernanceMapper governance;
    private final PermissionMapper permissions;
    private final SourceRightsSchema rights;
    private final SourceRightsDecisionSchema schema;
    private final SourceRightsEvidenceAuthority evidence;
    private final SourceRightsPolicy policy;
    private final PermissionSchema json;
    private final SourceUploadTransport transport;
    private final JdbcTemplate jdbc;

    public SourceRightsService(EnterpriseAccess access,ResourceReadAuthority authority,SourceReadMapper sources,
            SourceRightsMapper mapper,SourceGovernanceMapper governance,PermissionMapper permissions,
            SourceRightsSchema rights,SourceRightsDecisionSchema schema,SourceRightsEvidenceAuthority evidence,
            SourceRightsPolicy policy,PermissionSchema json,SourceUploadTransport transport,@Qualifier("masterDataSource") DataSource data) {
        this.access=access; this.authority=authority; this.sources=sources; this.mapper=mapper; this.governance=governance;
        this.permissions=permissions; this.rights=rights; this.schema=schema; this.evidence=evidence;
        this.policy=policy; this.json=json; this.transport=transport; jdbc=new JdbcTemplate(data);
    }

    JsonNode preview(ApplicationSessionIdentity.Verified subject,String context,String sourceVersion,SourceRightsProtocol.PreviewInput input) {
        policy.requireConfigured(); WireValues.uuid(sourceVersion);
        return access.execute(subject,admission -> ThrowingSupplier.of(() -> {
            current(admission,context); var prepared=prepare(admission,sourceVersion,input.decision(),input.proposal());
            var value=publicPreview(prepared,input); return schema.envelope("Preview",value,id(),prepared.state().revision().toString());
        }).get());
    }

    JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String key,String sourceVersion,
            SourceRightsProtocol.DecisionInput input) throws Exception {
        policy.requireConfigured(); WireValues.uuid(sourceVersion); DocumentUploadTransferService.requireOutsideTransaction(subject);
        var work=access.execute(subject,admission -> ThrowingSupplier.of(() -> {
            requireHead(admission,sourceVersion); var values=values(admission,sourceVersion,key,input,context);
            var prior=mapper.operation(values);
            if(!prior.isEmpty()) return new Work(null,replay(admission,single(prior),values,input));
            current(admission,context); var prepared=prepare(admission,sourceVersion,input.decision(),input.proposal());
            requireBaseline(prepared,input); return new Work(prepared,null);
        }).get());
        if(work.replay()!=null) return work.replay();
        var prepared=work.prepared();
        S3ObjectReader.Admission check=() -> access.execute(subject,admission -> ThrowingSupplier.of(() -> {
            current(admission,context); var actual=prepare(admission,sourceVersion,input.decision(),input.proposal());
            requireBaseline(actual,input); if(!prepared.sha256().equals(actual.sha256())) denied(ErrorCode.SCOPE_STALE); return Boolean.TRUE;
        }).get());
        var proofs=json.tree(List.of());
        if(!prepared.evidence().isEmpty()) {
            proofs=transport.readDocument(subject,prepared.source().tenantId(),policy.maximumEvidenceBytes(),check,(reader,admission) -> {
                var complete=json.object().putArray("proofs");
                for(var item:prepared.evidence()) {
                    var verified=SourceRightsEvidenceBytes.read(item.object(),reader,admission);
                    var proof=json.object(); proof.put("schema_version","1.0.0"); proof.set("evidence",item.manifest().deepCopy());
                    proof.set("object_ref",json.tree(verified.reference())); proof.put("full_sha256",verified.fullSha256());
                    proof.put("verified_at",verified.verifiedAt()); complete.add(proof);
                }
                admission.requireCurrent(); return complete;
            });
        }
        JsonNode verifiedProofs=proofs;
        return access.execute(subject,admission -> ThrowingSupplier.of(() -> {
            requireHead(admission,sourceVersion); var values=values(admission,sourceVersion,key,input,context);
            var prior=mapper.operation(values);
            if(!prior.isEmpty()) return replay(admission,single(prior),values,input);
            current(admission,context); var actual=prepare(admission,sourceVersion,input.decision(),input.proposal());
            requireBaseline(actual,input); if(!prepared.sha256().equals(actual.sha256())) denied(ErrorCode.SCOPE_STALE);
            return commit(admission,actual,input,values,verifiedProofs);
        }).get());
    }

    private SourceRightsProtocol.Prepared prepare(EnterpriseAccess.Admission admission,String sourceVersion,String decision,
            SourceRightsProtocol.Proposal proposal) throws Exception {
        var head=requireHead(admission,sourceVersion); var source=head.source(); var currentRights=head.rights(); var state=head.state();
        if("APPROVE".equals(decision)) {
            if(!"ACTIVE".equals(state.lifecycle())) denied(ErrorCode.OBJECT_UNAVAILABLE);
            if(Boolean.TRUE.equals(policy.separateReviewer()) && admission.subject().user().user_id().equals(state.sourceCreatedBy()))
                denied(ErrorCode.ACCESS_DENIED);
            Objects.requireNonNull(proposal);
            var now=Objects.requireNonNull(permissions.databaseTime());
            var from=utc(proposal.valid_from()); var to=utc(proposal.valid_to());
            if(to!=null && !to.isAfter(now) || from!=null && to!=null && !to.isAfter(from)
                    || !proposal.regions().contains(policy.region())
                    || !proposal.evidence_manifest().environments().contains(policy.environment())
                    || !proposal.evidence_manifest().classifications().contains(source.classification())) denied(ErrorCode.ACCESS_DENIED);
        } else if("RETURN".equals(decision)) {
            if(!"PENDING_REVIEW".equals(currentRights.status()) || !"ACTIVE".equals(state.lifecycle())) denied(ErrorCode.REVISION_CONFLICT);
        } else if("REVOKE".equals(decision)) {
            if(!"APPROVED".equals(currentRights.status())) denied(ErrorCode.REVISION_CONFLICT);
        } else throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var selected=evidence.require(admission,proposal); var manifest=head.manifest().deepCopy();
        manifest.put("decision",decision); manifest.set("proposal",json.tree(proposal));
        var authorizations=manifest.putArray("evidence_authorizations");
        for(var item:selected) authorizations.add(authorization(item));
        return new SourceRightsProtocol.Prepared(source,currentRights,state,selected,manifest,DigestUtils.sha256Hex(json.canonical(manifest)));
    }

    private Head requireHead(EnterpriseAccess.Admission admission,String sourceVersion) throws Exception {
        var source=single(sources.source(sourceVersion)); var state=single(mapper.state(baseValues(admission,sourceVersion)));
        var currentRights=single(sources.rights(source.rightsRevision())); var payload=rights.verify(currentRights);
        if(!source.tenantId().equals(admission.context().tenant_id()) || !source.originalSha256().equals(source.registeredSha256())
                || !List.of("ENTERPRISE","PROJECT","SESSION").contains(source.ownership())
                || "ENTERPRISE".equals(source.ownership())!=(source.ownerProjectId()==null)
                || !Objects.equals(source.ownerProjectId(),source.registeredProjectId())
                || !Objects.equals(source.ownerProjectId(),currentRights.registeredProjectId())
                || !source.sourceVersion().equals(currentRights.sourceVersion()) || !source.tenantId().equals(currentRights.tenantId())
                || !source.sourceVersion().equals(state.sourceVersion()) || !source.tenantId().equals(state.tenantId())
                || !source.rightsRevision().equals(state.rightsRevision()) || !source.classification().equals(state.classification())
                || !"1.0.0".equals(state.schemaVersion()) || !currentRights.schemaManifest().equals(state.schemaManifest())) mismatch();
        if(!List.of("PENDING_REVIEW","APPROVED","REVOKED","EXPIRED").contains(currentRights.status())) mismatch();
        if("PURGED".equals(state.lifecycle())) denied(ErrorCode.OBJECT_UNAVAILABLE);
        if("SESSION".equals(source.ownership()) && (state.conversationId()==null || !"ACTIVE".equals(state.conversationStatus())
                || !admission.subject().user().user_id().equals(state.conversationOwnerId()))) denied(ErrorCode.ACCESS_DENIED);
        var scope=new PermissionProtocol.Scope(source.tenantId(),source.ownerProjectId(),"SOURCE",source.sourceId());
        var read=authority.require(admission,scope,"source.read",SourceRightsEvidenceAuthority.PURPOSE);
        var approve=authority.require(admission,scope,"source.approve",SourceRightsEvidenceAuthority.PURPOSE);
        PermissionProtocol.GrantRow project=null;
        if(source.ownerProjectId()!=null) {
            project=authority.require(admission,new PermissionProtocol.Scope(source.tenantId(),source.ownerProjectId(),"PROJECT",source.ownerProjectId()),
                "project.read",SourceRightsEvidenceAuthority.PURPOSE);
            var statuses=permissions.projectStatus(source.ownerProjectId());
            if(statuses.size()!=1 || !List.of("PENDING","ACTIVE").contains(statuses.get(0))) denied(ErrorCode.OBJECT_UNAVAILABLE);
        }
        var manifest=json.object(); manifest.put("schema_version","1.0.0"); manifest.set("source",json.tree(source));
        manifest.set("rights",payload); manifest.put("rights_status",currentRights.status());
        manifest.put("rights_schema_manifest_sha256",currentRights.schemaManifest());
        manifest.put("revision",state.revision().toString()); manifest.put("lifecycle",state.lifecycle());
        manifest.put("source_created_by",state.sourceCreatedBy()); manifest.put("updated_at",time(state.updatedAt()));
        manifest.set("conversation_id",json.tree(state.conversationId())); manifest.set("conversation_owner_id",json.tree(state.conversationOwnerId()));
        manifest.set("conversation_status",json.tree(state.conversationStatus()));
        if(state.lastDecisionId()==null) manifest.putNull("last_decision_id"); else manifest.put("last_decision_id",state.lastDecisionId());
        manifest.set("context",json.tree(admission.context())); manifest.put("actor_id",admission.subject().user().user_id());
        manifest.put("session_id",admission.subject().sessionId()); manifest.set("read_grant",grant(read)); manifest.set("approval_grant",grant(approve));
        manifest.set("project_grant",grant(project));
        manifest.set("policy",json.tree(policy)); manifest.put("schema_manifest_sha256",schema.manifestSha256());
        return new Head(source,currentRights,state,manifest);
    }

    private JsonNode commit(EnterpriseAccess.Admission admission,SourceRightsProtocol.Prepared prepared,SourceRightsProtocol.DecisionInput input,
            Map<String,Object> values,JsonNode proof) throws Exception {
        schema.proofs(proof); if(proof.size()!=prepared.evidence().size()) mismatch();
        for(int index=0;index<proof.size();index++) {
            var expected=prepared.evidence().get(index); var actual=proof.get(index);
            if(!"1.0.0".equals(actual.required("schema_version").textValue()) || !actual.required("evidence").equals(expected.manifest())
                    || !actual.required("object_ref").equals(json.tree(expected.object()))
                    || !expected.object().sha256().equals(actual.required("full_sha256").textValue())) mismatch();
            Instant.parse(actual.required("verified_at").textValue());
        }
        var now=Objects.requireNonNull(permissions.databaseTime()); var source=prepared.source();
        String rightsRevision=source.rightsRevision(),rightsSha=prepared.rights().contentSha256();
        String rightsManifest=prepared.rights().schemaManifest(),status=prepared.rights().status();
        String operationId=id(),decisionId=id(),revision=PermissionSchema.next(input.expected_revision());
        values.put("now",now); values.put("decisionId",decisionId); values.put("operationId",operationId);
        values.put("classification",source.classification()); values.put("targetProjectId",source.ownerProjectId());
        values.put("baseRightsRevision",source.rightsRevision()); values.put("baseRightsSha256",prepared.rights().contentSha256());
        values.put("originalSha256",source.originalSha256());
        if("APPROVE".equals(input.decision())) {
            rightsRevision=id(); var payload=rightsPayload(source,rightsRevision,input.proposal(),prepared.evidence());
            rightsSha=DigestUtils.sha256Hex(json.canonical(payload)); rightsManifest=rights.manifestSha256(); status="APPROVED";
            var proposal=input.proposal(); var originalEvidence=payload.required("evidence_manifest");
            var row=new SourceReadProtocol.RightsRow(source.tenantId(),rightsRevision,source.sourceVersion(),proposal.purpose(),
                json.canonical(payload.required("allowed_actions")),json.canonical(payload.required("providers")),json.canonical(payload.required("regions")),
                utc(proposal.valid_from()),utc(proposal.valid_to()),status,json.canonical(originalEvidence),rightsSha,"1.0.0",rightsManifest,
                source.classification(),rightsSha,source.ownerProjectId());
            rights.verify(row); values.put("rightsRevision",rightsRevision); values.put("rightsSha256",rightsSha);
            values.put("rightsSchemaManifest",rightsManifest); values.put("rightsPurpose",proposal.purpose());
            values.put("allowedActions",row.allowedActions()); values.put("providers",row.providers()); values.put("regions",row.regions());
            values.put("validFrom",row.validFrom()); values.put("validTo",row.validTo()); values.put("rightsEvidence",row.evidenceManifest());
            one(governance.insertSubmissionRightsRegistry(values)); one(mapper.insertRights(values)); one(mapper.replaceSourceRights(values));
            if("APPROVED".equals(prepared.rights().status())) one(mapper.revokeRights(values));
            for(var item:prepared.evidence()) {
                values.put("evidenceId",item.authorization().registration().artifact().artifactId()); one(mapper.insertEvidenceLineage(values));
            }
            if(!rights.verify(single(sources.rights(rightsRevision))).equals(payload)) mismatch();
        } else if("REVOKE".equals(input.decision())) {
            one(mapper.revokeRights(values)); status="REVOKED";
        }
        values.put("rightsRevision",rightsRevision); values.put("rightsSchemaManifest",rightsManifest);
        values.put("expectedRevision",input.expected_revision()); values.put("resultingRevision",revision);
        values.put("rightsStatus",status); values.put("decision",input.decision()); values.put("reason",input.reason());
        values.put("reviewedManifest",json.canonical(prepared.manifest())); values.put("reviewedSha256",prepared.sha256());
        values.put("evidenceProof",json.canonical(proof)); values.put("governanceEpoch",admission.context().governance_epoch());
        var context=advanceContext(admission,values); var result=json.object(); result.put("schema_version","1.0.0");
        result.put("operation_id",operationId); result.put("decision_id",decisionId); result.put("source_version",source.sourceVersion());
        result.set("base_rights",json.tree(input.base_rights())); result.set("rights",json.tree(new PermissionProtocol.Basis(rightsRevision,rightsSha)));
        result.put("decision",input.decision()); result.put("rights_status",status); result.put("revision",revision);
        result.put("reviewed_manifest_sha256",prepared.sha256()); result.set("evidence",publicEvidence(prepared.evidence()));
        result.put("actor_id",admission.subject().user().user_id()); result.put("decided_at",time(now)); result.set("current_context",context);
        String requestId=id(); var response=schema.envelope("Receipt",result,requestId,revision);
        values.put("response",json.canonical(response)); values.put("schemaManifest",schema.manifestSha256());
        one(mapper.saveOperation(values)); one(mapper.insertDecision(values)); one(mapper.advanceState(values));
        one(permissions.saveAudit(admission.subject().user().user_id(),source.ownerProjectId(),id(),"source.rights_decided",
            json.canonical(result),requestId,schema.manifestSha256(),source.classification()));
        var event=json.object(); event.put("schema_version","1.0.0"); event.put("schema_manifest_sha256",schema.manifestSha256());
        event.set("decision",result); one(permissions.saveEvent(admission.subject().user().user_id(),id(),source.sourceVersion(),revision,
            "source.rights_decided",json.canonical(event)));
        var state=single(mapper.state(values)); var stored=single(sources.rights(rightsRevision)); rights.verify(stored);
        if(!state.revision().equals(new BigInteger(revision)) || !decisionId.equals(state.lastDecisionId())
                || !rightsRevision.equals(state.rightsRevision()) || !status.equals(stored.status())
                || !rightsRevision.equals(single(sources.source(source.sourceVersion())).rightsRevision())) mismatch();
        if(!replay(admission,single(mapper.operation(values)),values,input).equals(response)) mismatch();
        return response;
    }

    private JsonNode replay(EnterpriseAccess.Admission admission,SourceRightsProtocol.OperationRow stored,
            Map<String,Object> values,SourceRightsProtocol.DecisionInput input) throws Exception {
        if(!values.get("requestHash").equals(stored.requestSha256())) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if(!admission.subject().user().user_id().equals(stored.subjectId()) || !values.get("sourceVersion").equals(stored.targetId())
                || !"COMPLETED".equals(stored.state()) || !Integer.valueOf(200).equals(stored.httpStatus())
                || !"1.0.0".equals(stored.schemaVersion()) || !schema.manifestSha256().equals(stored.schemaManifest())) mismatch();
        var request=read(stored.requestManifest());
        if(!json.canonical(request).equals(values.get("requestManifest")) || !read(stored.reviewedManifest()).isObject()
                || !DigestUtils.sha256Hex(json.canonical(read(stored.reviewedManifest()))).equals(input.reviewed_manifest_sha256())) mismatch();
        var response=schema.receipt(stored.response()); var result=response.required("data");
        if(!schema.receipt(stored.decisionResponse()).equals(response)
                || !stored.fromRightsRevision().equals(result.required("base_rights").required("version_id").textValue())
                || !stored.toRightsRevision().equals(result.required("rights").required("version_id").textValue())
                || !stored.resultingRevision().toString().equals(result.required("revision").textValue())
                || !stored.decision().equals(result.required("decision").textValue())
                || !stored.rightsStatus().equals(result.required("rights_status").textValue())
                || !stored.createdBy().equals(result.required("actor_id").textValue())
                || !stored.createdBy().equals(stored.subjectId()) || !time(stored.createdAt()).equals(result.required("decided_at").textValue())) mismatch();
        if(!stored.operationId().equals(result.required("operation_id").textValue())
                || !stored.decisionId().equals(result.required("decision_id").textValue())
                || !values.get("sourceVersion").equals(result.required("source_version").textValue())
                || !input.decision().equals(result.required("decision").textValue())
                || !json.tree(input.base_rights()).equals(result.required("base_rights"))
                || !PermissionSchema.next(input.expected_revision()).equals(result.required("revision").textValue())
                || !result.required("revision").equals(response.required("resource_revision"))) mismatch();
        var saved=single(sources.rights(result.required("rights").required("version_id").textValue())); rights.verify(saved);
        if(!saved.sourceVersion().equals(stored.targetId()) || !saved.contentSha256().equals(result.required("rights").required("content_sha256").textValue())) mismatch();
        var proof=read(stored.evidenceProof()); schema.proofs(proof); if(proof.size()!=result.required("evidence").size()) mismatch();
        for(int index=0;index<proof.size();index++) {
            var item=proof.get(index); if(!item.required("evidence").equals(result.required("evidence").get(index))
                    || !item.required("full_sha256").equals(item.required("object_ref").required("sha256"))) mismatch();
        }
        return response;
    }

    private ObjectNode authorization(SourceRightsProtocol.Evidence evidence) throws Exception {
        var verified=evidence.authorization(); var registration=verified.registration(); var result=json.object();
        result.set("evidence",evidence.manifest().deepCopy()); result.set("object_ref",json.tree(evidence.object()));
        result.set("binding",json.tree(registration.binding())); result.set("dependency_manifest",json.tree(registration.manifest()));
        result.set("artifact_grant",grant(registration.artifactGrant())); result.set("resource_grant",grant(registration.resourceGrant()));
        var versions=result.putObject("versions");
        for(var entry:verified.versions().entrySet()) {
            var value=json.object(); value.set("version",json.tree(entry.getValue().version())); value.set("owner",json.tree(entry.getValue().owner()));
            value.set("resource_grant",grant(entry.getValue().resourceGrant())); value.set("project_grant",grant(entry.getValue().versionProjectGrant()));
            var artifact=entry.getValue().artifactRegistration();
            if(artifact!=null) { value.set("artifact_binding",json.tree(artifact.binding())); value.set("artifact_grant",grant(artifact.artifactGrant())); }
            versions.set(entry.getKey(),value);
        }
        var origins=result.putObject("sources");
        for(var entry:verified.sources().entrySet()) {
            var value=json.object(); value.set("source",json.tree(entry.getValue().source())); value.set("rights",rights.verify(entry.getValue().rights()));
            value.set("read_grant",grant(entry.getValue().sourceGrant())); value.set("project_grant",grant(entry.getValue().projectGrant()));
            origins.set(entry.getKey(),value);
        }
        return result;
    }

    private JsonNode grant(PermissionProtocol.GrantRow grant) throws Exception {
        if(grant==null) return json.tree(null);
        var value=json.object(); value.put("grant_id",grant.grantId()); value.put("revision",grant.revision().toString());
        value.put("basis_version",grant.basisVersion()); value.put("schema_version",grant.schemaVersion());
        value.put("schema_manifest_sha256",grant.manifest()); value.put("condition_sha256",DigestUtils.sha256Hex(grant.condition()));
        value.put("status",grant.status()); return value;
    }
    private ObjectNode rightsPayload(SourceReadProtocol.SourceRow source,String revision,SourceRightsProtocol.Proposal proposal,
            List<SourceRightsProtocol.Evidence> selected) {
        var body=json.object(); body.put("schema_version","1.0.0"); body.put("tenant_id",source.tenantId());
        body.put("source_version",source.sourceVersion()); body.put("rights_revision",revision); body.put("purpose",proposal.purpose());
        body.set("allowed_actions",json.tree(proposal.allowed_actions())); body.set("providers",json.tree(proposal.providers()));
        body.set("regions",json.tree(proposal.regions()));
        body.set("valid_from",json.tree(proposal.valid_from()==null?null:time(utc(proposal.valid_from()))));
        body.set("valid_to",json.tree(proposal.valid_to()==null?null:time(utc(proposal.valid_to()))));
        body.put("classification",source.classification()); var proof=json.object(); proof.put("owner",proposal.evidence_manifest().owner());
        proof.put("license",proposal.evidence_manifest().license()); proof.set("environments",json.tree(proposal.evidence_manifest().environments()));
        proof.set("classifications",json.tree(proposal.evidence_manifest().classifications())); var refs=proof.putArray("authorization_evidence");
        for(var item:selected) refs.add(json.tree(item.object())); body.set("evidence_manifest",proof); return body;
    }
    private JsonNode publicPreview(SourceRightsProtocol.Prepared prepared,SourceRightsProtocol.PreviewInput input) {
        var source=prepared.source(); var value=json.object(); value.put("schema_version","1.0.0"); value.put("source_id",source.sourceId());
        value.put("source_version",source.sourceVersion()); value.set("owner_project_id",json.tree(source.ownerProjectId()));
        value.put("ownership",source.ownership()); value.put("classification",source.classification()); value.put("original_sha256",source.originalSha256());
        value.set("base_rights",json.tree(new PermissionProtocol.Basis(source.rightsRevision(),prepared.rights().contentSha256())));
        value.put("rights_status",prepared.rights().status()); value.put("revision",prepared.state().revision().toString());
        value.put("decision",input.decision()); value.set("proposal",json.tree(input.proposal())); value.set("evidence",publicEvidence(prepared.evidence()));
        value.put("reviewed_manifest_sha256",prepared.sha256()); return value;
    }
    private JsonNode publicEvidence(List<SourceRightsProtocol.Evidence> selected) {
        var value=json.object().putArray("evidence"); for(var item:selected) value.add(item.manifest().deepCopy()); return value;
    }
    private JsonNode advanceContext(EnterpriseAccess.Admission admission,Map<String,Object> values) {
        String revision=PermissionSchema.next(admission.context().context_revision()),epoch=PermissionSchema.next(admission.context().governance_epoch());
        one(governance.advanceGovernance(values));
        one(jdbc.update("""
            UPDATE platform_session_context SET context_revision=?,governance_epoch=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=? AND context_revision=? AND auth_epoch=? AND governance_epoch=?
            """,revision,epoch,admission.contextId(),admission.subject().sessionId(),admission.subject().user().user_id(),
            admission.context().tenant_id(),admission.context().context_revision(),admission.context().auth_epoch(),admission.context().governance_epoch()));
        var value=json.object(); value.put("tenant_id",admission.context().tenant_id()); value.put("context_revision",revision);
        value.put("auth_epoch",admission.context().auth_epoch()); value.put("governance_epoch",epoch); return value;
    }
    private Map<String,Object> values(EnterpriseAccess.Admission admission,String source,String key,SourceRightsProtocol.DecisionInput input,String context) throws Exception {
        var values=baseValues(admission,source); values.put("key",key);
        values.put("requestHash",schema.requestHash(admission.context().tenant_id(),source,input,context,admission.subject().sessionId()));
        values.put("requestManifest",json.canonical(schema.requestManifest(admission.context().tenant_id(),source,input,context,admission.subject().sessionId()))); return values;
    }
    private static Map<String,Object> baseValues(EnterpriseAccess.Admission admission,String source) {
        var values=new HashMap<String,Object>(); values.put("userId",admission.subject().user().user_id()); values.put("sourceVersion",source); return values;
    }
    private static void requireBaseline(SourceRightsProtocol.Prepared prepared,SourceRightsProtocol.DecisionInput input) {
        if(!input.expected_revision().equals(prepared.state().revision().toString())) denied(ErrorCode.REVISION_CONFLICT);
        if(!input.base_rights().version_id().equals(prepared.rights().rightsRevision())
                || !input.base_rights().content_sha256().equals(prepared.rights().contentSha256())
                || !input.reviewed_manifest_sha256().equals(prepared.sha256())) denied(ErrorCode.SCOPE_STALE);
    }
    private JsonNode read(String value) throws Exception {
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(value);
    }
    private static LocalDateTime utc(String value) { return value==null?null:LocalDateTime.ofInstant(Instant.parse(value),ZoneOffset.UTC); }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static void current(EnterpriseAccess.Admission admission,String context) { if(!admission.context().context_revision().equals(context)) denied(ErrorCode.CONTEXT_CHANGED); }
    private static <T> T single(List<T> rows) { if(rows.size()!=1) denied(ErrorCode.OBJECT_UNAVAILABLE); return rows.get(0); }
    private static void one(int count) { if(count!=1) mismatch(); }
    private static void mismatch() { denied(ErrorCode.CONTENT_MISMATCH); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    private record Head(SourceReadProtocol.SourceRow source,SourceReadProtocol.RightsRow rights,SourceRightsProtocol.StateRow state,ObjectNode manifest) { }
    private record Work(SourceRightsProtocol.Prepared prepared,JsonNode replay) { }
}
