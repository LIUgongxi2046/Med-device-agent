package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;

/** Read-only verification of an actual KN-RIGHTS-01 approval and its durable witnesses. */
final class SimulationAuthorizedUseRightsVerifier {
    private SimulationAuthorizedUseRightsVerifier() { }

    /** All reviewed evidence metadata and storage identities remain internal; only snapshot is public. */
    record EvidenceIdentity(PermissionProtocol.Basis ref, ObjectRef object, JsonNode manifest, JsonNode authorization) {
        EvidenceIdentity { manifest=manifest.deepCopy(); authorization=authorization.deepCopy(); }
        @Override public JsonNode manifest() { return manifest.deepCopy(); }
        @Override public JsonNode authorization() { return authorization.deepCopy(); }
    }
    record Verified(ObjectNode snapshot, List<EvidenceIdentity> evidence, JsonNode reviewPolicy) {
        Verified { snapshot=snapshot.deepCopy(); evidence=List.copyOf(evidence); reviewPolicy=reviewPolicy.deepCopy(); }
        @Override public ObjectNode snapshot() { return snapshot.deepCopy(); }
        @Override public JsonNode reviewPolicy() { return reviewPolicy.deepCopy(); }
    }

    static Verified verify(PermissionSchema json, SourceRightsDecisionSchema decisions, SourceRightsSchema rightsSchema,
            SourceReadProtocol.SourceRow source, SourceReadProtocol.RightsRow rights, JsonNode ledger) throws IOException {
        try { return verifyLedger(json,decisions,rightsSchema,source,rights,ledger); }
        catch (IOException | RuntimeException failure) { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    }

    private static Verified verifyLedger(PermissionSchema json, SourceRightsDecisionSchema decisions,
            SourceRightsSchema rightsSchema, SourceReadProtocol.SourceRow source, SourceReadProtocol.RightsRow rights,
            JsonNode ledger) throws IOException {
        Objects.requireNonNull(source); Objects.requireNonNull(rights);
        var current=rightsSchema.verify(rights);
        equal(source.tenantId(),rights.tenantId()); equal(source.sourceVersion(),rights.sourceVersion());
        equal(source.rightsRevision(),rights.rightsRevision()); equal(source.originalSha256(),source.registeredSha256());
        equal(source.ownerProjectId(),source.registeredProjectId()); equal(source.ownerProjectId(),rights.registeredProjectId());
        equal(source.classification(),rights.classification()); equal("APPROVED",rights.status());
        require(List.of("ENTERPRISE","PROJECT","SESSION").contains(source.ownership()));
        require("ENTERPRISE".equals(source.ownership())==(source.ownerProjectId()==null));

        var state=object(ledger,"state"); var decision=object(ledger,"decision"); var operation=object(ledger,"operation");
        for(var row:List.of(state,decision,operation)) {
            equal(source.tenantId(),text(row,"tenant_id")); equal("1.0.0",text(row,"schema_version"));
            equal(source.classification(),text(row,"classification"));
        }
        equal(source.sourceVersion(),text(state,"source_version")); equal(rights.rightsRevision(),text(state,"rights_revision"));
        equal(rights.schemaManifest(),text(state,"schema_manifest_sha256"));
        String revision=revision(state,"revision"),decisionId=text(state,"last_decision_id"); WireValues.uuid(decisionId);
        equal(decisionId,text(decision,"decision_id")); equal(source.sourceVersion(),text(decision,"source_version"));
        equal("APPROVE",text(decision,"decision")); equal("APPROVED",text(decision,"rights_status"));
        equal(rights.rightsRevision(),text(decision,"to_rights_revision"));
        String previous=text(decision,"from_rights_revision"),expected=revision(decision,"expected_revision");
        WireValues.uuid(previous); require(!previous.equals(rights.rightsRevision()));
        equal(PermissionSchema.next(expected),revision); equal(revision,revision(decision,"resulting_revision"));
        equal(decisions.manifestSha256(),text(decision,"schema_manifest_sha256"));
        equal(decisions.manifestSha256(),text(operation,"schema_manifest_sha256"));
        equal(text(decision,"operation_id"),text(operation,"operation_id"));
        equal("KN-RIGHTS-01",text(operation,"operation_name")); equal("COMPLETED",text(operation,"state"));
        require(node(operation,"http_status").isIntegralNumber() && node(operation,"http_status").intValue()==200);
        require(node(operation,"error_code").isNull()); equal(source.sourceVersion(),text(operation,"target_id"));
        String actor=text(decision,"created_by"); WireValues.uuid(actor);
        equal(actor,text(operation,"subject_id")); equal(actor,text(operation,"created_by"));
        String decidedAt=time(decision,"created_at"); equal(decidedAt,time(operation,"created_at"));
        equal(decidedAt,time(state,"updated_at"));

        var receipt=decisions.receipt(json.canonical(object(decision,"receipt_ref")));
        equal(receipt,decisions.receipt(json.canonical(object(operation,"response_ref"))));
        var result=object(receipt,"data");
        equal(decisionId,text(result,"decision_id")); equal(text(operation,"operation_id"),text(result,"operation_id"));
        equal(source.sourceVersion(),text(result,"source_version")); equal("APPROVE",text(result,"decision"));
        equal("APPROVED",text(result,"rights_status")); equal(revision,text(result,"revision"));
        equal(revision,text(receipt,"resource_revision")); equal(actor,text(result,"actor_id"));
        equal(decidedAt,time(result,"decided_at"));
        equal(json.tree(new PermissionProtocol.Basis(rights.rightsRevision(),rights.contentSha256())),object(result,"rights"));
        equal(previous,text(object(result,"base_rights"),"version_id"));

        var request=object(decision,"request_manifest");
        equal(DigestUtils.sha256Hex(json.canonical(request)),text(operation,"request_sha256"));
        var input=decisions.decision(json.canonical(object(request,"body")).getBytes(StandardCharsets.UTF_8));
        equal("APPROVE",input.decision()); equal(expected,input.expected_revision());
        equal(previous,input.base_rights().version_id()); equal(json.tree(input.base_rights()),object(result,"base_rights"));
        equal(input.reason(),text(decision,"reason"));
        String context=revision(request,"context_header"),session=text(request,"session_id"); WireValues.uuid(session);
        equal(decisions.requestManifest(source.tenantId(),source.sourceVersion(),input,context,session),request);
        var reviewed=object(decision,"reviewed_manifest");
        String reviewedHash=DigestUtils.sha256Hex(json.canonical(reviewed));
        equal(reviewedHash,text(decision,"reviewed_manifest_sha256")); equal(reviewedHash,input.reviewed_manifest_sha256());
        equal(reviewedHash,text(result,"reviewed_manifest_sha256")); equal("1.0.0",text(reviewed,"schema_version"));
        equal(decisions.manifestSha256(),text(reviewed,"schema_manifest_sha256"));
        equal("APPROVE",text(reviewed,"decision")); equal(json.tree(input.proposal()),object(reviewed,"proposal"));
        equal(expected,revision(reviewed,"revision")); equal("ACTIVE",text(reviewed,"lifecycle"));
        equal(actor,text(reviewed,"actor_id")); equal(session,text(reviewed,"session_id"));
        var reviewedSource=(ObjectNode)json.tree(source); reviewedSource.put("rights_revision",previous);
        equal(reviewedSource,object(reviewed,"source"));
        verifyPreviousRights(json,rightsSchema,source,input.base_rights(),reviewed);
        verifyContext(source.tenantId(),context,object(reviewed,"context"),object(result,"current_context"));

        // The approved payload is reconstructed from the reviewed proposal; policy-only or bootstrap rows cannot substitute.
        var proposal=json.tree(input.proposal()); var proposed=current.deepCopy();
        for(String field:List.of("purpose","allowed_actions","providers","regions")) proposed.set(field,node(proposal,field));
        for(String field:List.of("valid_from","valid_to")) {
            var value=node(proposal,field); if(value.isNull()) proposed.putNull(field); else proposed.put(field,Instant.parse(value.textValue()).toString());
        }
        var proposedEvidence=object(proposal,"evidence_manifest").deepCopy();
        proposedEvidence.set("authorization_evidence",node(object(current,"evidence_manifest"),"authorization_evidence"));
        proposed.set("evidence_manifest",proposedEvidence); equal(current,proposed);

        var proofs=array(decision,"evidence_proof"); decisions.proofs(proofs);
        var publicEvidence=array(result,"evidence"); var authorizations=array(reviewed,"evidence_authorizations");
        var storedObjects=array(object(current,"evidence_manifest"),"authorization_evidence");
        var proposedRefs=input.proposal().evidence_manifest().authorization_evidence();
        require(!proposedRefs.isEmpty() && proofs.size()==proposedRefs.size() && proofs.size()==publicEvidence.size()
            && proofs.size()==authorizations.size() && proofs.size()==storedObjects.size());
        var evidence=new ArrayList<EvidenceIdentity>(); var ids=new HashSet<String>(); var identities=new HashSet<ObjectRef>();
        for(int index=0;index<proofs.size();index++) {
            var proof=proofs.get(index); var manifest=object(proof,"evidence"); var authorization=authorizations.get(index);
            var ref=proposedRefs.get(index); equal(json.tree(ref),object(manifest,"artifact"));
            equal(manifest,publicEvidence.get(index)); equal(manifest,object(authorization,"evidence"));
            var binding=object(authorization,"binding"); var dependencies=object(authorization,"dependency_manifest");
            object(authorization,"versions"); object(authorization,"sources");
            equal(source.tenantId(),text(binding,"tenant_id")); equal(ref.version_id(),text(binding,"artifact_id"));
            equal(text(manifest,"schema_ref"),text(binding,"schema_ref"));
            equal(text(manifest,"schema_document_sha256"),text(binding,"schema_document_sha256"));
            equal("1.0.0",text(dependencies,"schema_version")); equal(source.tenantId(),text(dependencies,"tenant_id"));
            equal(node(binding,"project_id"),node(dependencies,"project_id"));
            equal(text(binding,"access_set_id"),text(dependencies,"access_set_id"));
            equal(DigestUtils.sha256Hex(json.canonical(dependencies)),text(binding,"dependency_manifest_sha256"));
            var storage=object(proof,"object_ref"); equal(storage,object(authorization,"object_ref")); equal(storage,storedObjects.get(index));
            equal(ref.content_sha256(),text(storage,"sha256")); equal(ref.content_sha256(),text(proof,"full_sha256"));
            var object=new ObjectRef(text(storage,"object_key"),text(storage,"object_version"),text(storage,"sha256"),
                text(storage,"size_bytes"),text(storage,"media_type"));
            require(ids.add(ref.version_id()) && identities.add(object));
            evidence.add(new EvidenceIdentity(ref,object,manifest,authorization));
        }
        verifyWitnesses(json,decisions,source,ledger,receipt,result,actor,revision);
        // Commit exact private authorization metadata and byte proofs without exposing their identities or contents.
        var witness=json.object(); witness.put("schema_version","1.0.0");
        witness.set("evidence_authorizations",authorizations); witness.set("evidence_proof",proofs);
        var snapshot=json.object(); snapshot.set("rights",node(result,"rights").deepCopy()); snapshot.put("decision_id",decisionId);
        snapshot.put("witness_sha256",DigestUtils.sha256Hex(json.canonical(witness)));
        snapshot.put("evidence_count",Integer.toString(evidence.size())); return new Verified(snapshot,evidence,object(reviewed,"policy"));
    }

    private static void verifyPreviousRights(PermissionSchema json,SourceRightsSchema schema,SourceReadProtocol.SourceRow source,
            PermissionProtocol.Basis base,JsonNode reviewed) throws IOException {
        var payload=object(reviewed,"rights"); String status=text(reviewed,"rights_status");
        require(List.of("PENDING_REVIEW","APPROVED","REVOKED","EXPIRED").contains(status));
        equal(base.content_sha256(),DigestUtils.sha256Hex(json.canonical(payload)));
        var old=new SourceReadProtocol.RightsRow(source.tenantId(),base.version_id(),source.sourceVersion(),text(payload,"purpose"),
            json.canonical(array(payload,"allowed_actions")),json.canonical(array(payload,"providers")),json.canonical(array(payload,"regions")),
            nullableTime(payload,"valid_from"),nullableTime(payload,"valid_to"),status,json.canonical(object(payload,"evidence_manifest")),
            base.content_sha256(),text(payload,"schema_version"),text(reviewed,"rights_schema_manifest_sha256"),source.classification(),
            base.content_sha256(),source.ownerProjectId());
        equal(payload,schema.verify(old));
    }

    private static void verifyContext(String tenant,String context,JsonNode reviewed,JsonNode result) {
        equal(tenant,text(reviewed,"tenant_id")); equal(tenant,text(result,"tenant_id"));
        equal(context,revision(reviewed,"context_revision")); equal(PermissionSchema.next(context),revision(result,"context_revision"));
        equal(text(reviewed,"auth_epoch"),text(result,"auth_epoch"));
        equal(PermissionSchema.next(text(reviewed,"governance_epoch")),text(result,"governance_epoch"));
    }

    private static void verifyWitnesses(PermissionSchema json,SourceRightsDecisionSchema decisions,SourceReadProtocol.SourceRow source,
            JsonNode ledger,JsonNode receipt,JsonNode result,String actor,String revision) {
        var audits=array(ledger,"audits"); var events=array(ledger,"outbox"); require(audits.size()==1 && events.size()==1);
        var audit=audits.get(0); var event=events.get(0); WireValues.uuid(text(audit,"audit_id")); WireValues.uuid(text(event,"event_id"));
        equal(source.tenantId(),text(audit,"tenant_id")); equal(json.tree(source.ownerProjectId()),node(audit,"project_id"));
        equal(actor,text(audit,"subject_id")); equal(actor,text(audit,"created_by")); equal("source.rights_decided",text(audit,"action"));
        equal(result,object(audit,"resource_ref")); equal("SUCCESS",text(audit,"result")); require(node(audit,"reason_code").isNull());
        equal(text(receipt,"request_id"),text(audit,"request_id")); equal(text(receipt,"request_id"),text(audit,"trace_id"));
        equal("1.0.0",text(audit,"schema_version")); equal(decisions.manifestSha256(),text(audit,"schema_manifest_sha256"));
        equal(source.classification(),text(audit,"classification")); equal(source.tenantId(),text(event,"tenant_id"));
        equal(source.sourceVersion(),text(event,"aggregate_id")); equal(revision,revision(event,"aggregate_revision"));
        equal("source.rights_decided",text(event,"event_type"));
        var payload=json.object(); payload.put("schema_version","1.0.0"); payload.put("schema_manifest_sha256",decisions.manifestSha256());
        payload.set("decision",result); equal(payload,object(event,"payload"));
    }

    private static JsonNode node(JsonNode value,String field) {
        require(value!=null && value.isObject() && value.has(field)); return value.get(field);
    }
    private static ObjectNode object(JsonNode value,String field) { var item=node(value,field); require(item.isObject()); return (ObjectNode)item; }
    private static JsonNode array(JsonNode value,String field) { var item=node(value,field); require(item.isArray()); return item; }
    private static String text(JsonNode value,String field) { var item=node(value,field); require(item.isTextual() && !item.textValue().isEmpty()); return item.textValue(); }
    private static String revision(JsonNode value,String field) { String result=text(value,field); WireValues.revision(result); return result; }
    private static String time(JsonNode value,String field) { String result=text(value,field); WireValues.utcTime(result); return Instant.parse(result).toString(); }
    private static LocalDateTime nullableTime(JsonNode value,String field) {
        return node(value,field).isNull()?null:LocalDateTime.ofInstant(Instant.parse(time(value,field)),ZoneOffset.UTC);
    }
    private static void equal(Object expected,Object actual) { require(Objects.equals(expected,actual)); }
    private static void require(boolean condition) { if(!condition) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
