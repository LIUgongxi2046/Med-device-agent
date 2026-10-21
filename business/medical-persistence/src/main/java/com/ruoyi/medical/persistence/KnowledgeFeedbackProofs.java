package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Consumes originals produced by source/rule governance and TP06. Does not grant or publish anything. */
@Component
public final class KnowledgeFeedbackProofs {
    private final JdbcTemplate db;
    private final SourceReadMapper sourceRows;
    private final SourceReadAuthority sources;
    private final DocumentUploadPolicy storage;
    private final RuleVersionStore rules;
    private final RuleVersionSchema ruleSchema;
    private final SourceGovernanceSchema sourceSchema;
    private final DocumentReadService documents;
    private final ResourceReadAuthority resources;
    private final PermissionMapper permissions;
    private final PermissionAuthority grants;
    private final com.ruoyi.medical.identity.PlatformMemberIdentityStatus identities;
    private final PermissionSchema json;
    private final KnowledgeFeedbackSchema codec;
    private final String impactManifest;
    public KnowledgeFeedbackProofs(DataSource data,SourceReadMapper sourceRows,SourceReadAuthority sources,
            DocumentUploadPolicy storage,RuleVersionStore rules,RuleVersionSchema ruleSchema,SourceGovernanceSchema sourceSchema,
            DocumentReadService documents,ResourceReadAuthority resources,PermissionMapper permissions,
            PermissionAuthority grants,com.ruoyi.medical.identity.PlatformMemberIdentityStatus identities,
            PermissionSchema json,KnowledgeFeedbackSchema codec)throws Exception {
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.sourceRows=sourceRows;this.sources=sources;this.storage=storage;
        this.rules=rules;this.ruleSchema=ruleSchema;this.sourceSchema=sourceSchema;this.documents=documents;
        this.resources=resources;this.permissions=permissions;this.grants=grants;this.identities=identities;this.json=json;this.codec=codec;
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/version-change-impact.schema.json"))){impactManifest=WireValues.sha256(in.readAllBytes());}
    }
    record Version(String id,String hash,String classification,LocalDateTime createdAt,String sourceId,
                   String baseId,JsonNode snapshot) { }
    Version version(EnterpriseAccess.Admission a,String project,String kind,String id)throws Exception {
        WireValues.uuid(id);
        var registry=one(db.queryForList("SELECT owner_project_id,entity_kind,content_sha256 FROM version_registry WHERE tenant_id=? AND version_id=?",tenant(a),id));
        if("SOURCE".equals(kind)) {
            var source=single(sourceRows.source(id));
            if(!tenant(a).equals(source.tenantId())||"SESSION".equals(source.ownership())
                    ||source.ownerProjectId()!=null&&!project.equals(source.ownerProjectId()))denied();
            storage.requireConfigured();var rights=single(sourceRows.rights(source.rightsRevision()));
            // Use the actual approved rights purpose, never a client-supplied substitute.
            var proof=sources.require(a,new SourceReadProtocol.ReadRequirement(id,source.rightsRevision(),project,null,null,null,
                SourceReadProtocol.Use.REVIEW,rights.purpose(),storage.environment(),storage.region(),null));
            if(!"SOURCE_VERSION".equals(str(registry,"entity_kind"))||!source.originalSha256().equals(str(registry,"content_sha256"))
                    ||!Objects.equals(source.ownerProjectId(),registry.get("owner_project_id")))mismatch();
            var row=one(db.queryForList("SELECT created_by,created_at FROM source_version WHERE tenant_id=? AND source_version=?",tenant(a),id));
            String classification=max(source.classification(),proof.rights().classification());
            var snapshot=json.object().put("version_id",id).put("content_sha256",source.originalSha256()).put("kind",kind)
                .put("source_id",source.sourceId()).put("rights_revision",proof.rights().rightsRevision())
                .put("rights_sha256",proof.rights().contentSha256()).put("classification",classification)
                .put("created_by",str(row,"created_by")).put("created_at",instant(time(row.get("created_at"))));
            return new Version(id,source.originalSha256(),classification,time(row.get("created_at")),source.sourceId(),null,snapshot);
        }
        if(!"RULE".equals(kind))KnowledgeFeedbackSchema.invalid();
        var rule=rules.read(a,id);
        if(!"RULE_VERSION".equals(str(registry,"entity_kind"))||registry.get("owner_project_id")!=null||!rule.content_sha256().equals(str(registry,"content_sha256")))mismatch();
        var row=one(db.queryForList("SELECT classification,created_at FROM rule_version WHERE tenant_id=? AND rule_version=?",tenant(a),id));
        var snapshot=json.object().put("version_id",id).put("content_sha256",rule.content_sha256()).put("kind",kind)
            .put("classification",str(row,"classification")).put("created_by",rule.created_by()).put("created_at",instant(time(row.get("created_at"))));
        snapshot.set("base_version",json.tree(rule.base_version()));
        return new Version(id,rule.content_sha256(),str(row,"classification"),time(row.get("created_at")),null,
            rule.base_version()==null?null:rule.base_version().version_id(),snapshot);
    }
    JsonNode origin(EnterpriseAccess.Admission a,String project,JsonNode request)throws Exception {
        var result=json.object();
        String original=KnowledgeFeedbackSchema.nullable(request,"document_revision"),revised=KnowledgeFeedbackSchema.nullable(request,"revised_document_revision");
        String review=KnowledgeFeedbackSchema.nullable(request,"review_id"),returned=KnowledgeFeedbackSchema.nullable(request,"return_id");
        if(original==null) {
            if(revised!=null||review!=null||returned!=null)KnowledgeFeedbackSchema.invalid();
            return result;
        }
        var before=document(a,project,original);result.set("document",before);
        if(revised!=null) {
            if(revised.equals(original))KnowledgeFeedbackSchema.invalid();
            var after=document(a,project,revised);
            var row=one(db.queryForList("SELECT based_on_project_id,based_on_document_id,based_on_document_revision FROM document_revision_meta WHERE tenant_id=? AND project_id=? AND document_revision=?",tenant(a),project,revised));
            if(!original.equals(str(row,"based_on_document_revision"))||!project.equals(str(row,"based_on_project_id"))
                    ||!before.required("document_id").asText().equals(str(row,"based_on_document_id"))
                    ||!before.required("document_id").equals(after.required("document_id")))mismatch();
            result.set("revised_document",after);
        }
        if(review!=null) {
            var row=one(db.queryForList("SELECT decision,reason,actor_id,baseline_manifest,classification FROM review_decision WHERE tenant_id=? AND project_id=? AND review_id=? AND document_revision=?",tenant(a),project,review,original));
            if(!"RETURN".equals(str(row,"decision")))mismatch();
            result.set("review",json.object().put("review_id",review).put("decision","RETURN").put("actor_id",str(row,"actor_id"))
                .put("reason",str(row,"reason")).put("classification",str(row,"classification")).put("baseline_sha256",codec.hash(codec.read(str(row,"baseline_manifest")))));
        }
        if(returned!=null) {
            var row=one(db.queryForList("SELECT decision,reason,actor_id,canonical_request,request_sha256,classification FROM document_revision_return WHERE tenant_id=? AND project_id=? AND return_id=? AND document_revision=?",tenant(a),project,returned,original));
            if(!"RETURN_FOR_REVISION".equals(str(row,"decision"))||!codec.hash(codec.read(str(row,"canonical_request"))).equals(str(row,"request_sha256")))mismatch();
            result.set("return",json.object().put("return_id",returned).put("decision","RETURN_FOR_REVISION").put("actor_id",str(row,"actor_id"))
                .put("reason",str(row,"reason")).put("classification",str(row,"classification")).put("request_sha256",str(row,"request_sha256")));
        }
        return result;
    }
    private JsonNode document(EnterpriseAccess.Admission a,String project,String revision)throws Exception {
        var row=one(db.queryForList("SELECT document_id FROM document_revision_meta WHERE tenant_id=? AND project_id=? AND document_revision=?",tenant(a),project,revision));
        var h=documents.authorizeMetadata(a,a.context().context_revision(),str(row,"document_id"),revision).header();
        if(!project.equals(h.projectId())||!tenant(a).equals(h.tenantId()))denied();
        return json.object().put("document_id",h.documentId()).put("document_revision",revision)
            .put("content_sha256",h.contentSha256()).put("body_sha256",h.bodySha256()).put("classification",h.classification());
    }
    Map<String,Object> proposal(EnterpriseAccess.Admission a,Map<String,Object> feedback,String proposal)throws Exception {
        String project=str(feedback,"project_id"),kind=str(feedback,"kind"),target=str(feedback,"target_version");
        resources.require(a,new PermissionProtocol.Scope(tenant(a),project,"PROJECT",project),"impact.read","VERSION_CHANGE_IMPACT");
        var row=one(db.queryForList("SELECT p.*,s.project_id,s.kind,s.proposal_json,s.proposal_sha256,s.schema_version,s.schema_manifest_sha256 FROM change_proposal p JOIN change_proposal_scope s ON s.tenant_id=p.tenant_id AND s.proposal_id=p.proposal_id WHERE p.tenant_id=? AND p.proposal_id=? FOR UPDATE",tenant(a),proposal));
        var body=codec.read(str(row,"proposal_json"));
        if(!"1.0.0".equals(str(row,"schema_version"))||!impactManifest.equals(str(row,"schema_manifest_sha256"))||!codec.hash(body).equals(str(row,"proposal_sha256")))mismatch();
        for(String field:List.of("project_id","kind","old_version","new_version","reviewer_id","reason"))if(!str(row,field).equals(body.path(field).asText()))mismatch();
        if(!project.equals(str(row,"project_id"))||!kind.equals(str(row,"kind"))||!target.equals(str(row,"old_version"))
                ||target.equals(str(row,"new_version"))||time(row.get("created_at")).isBefore(time(feedback.get("created_at"))))mismatch();
        var old=version(a,project,kind,target);var next=version(a,project,kind,str(row,"new_version"));
        if(!old.hash().equals(str(feedback,"target_sha256"))||next.createdAt().isBefore(time(feedback.get("created_at"))))mismatch();
        if("SOURCE".equals(kind)&&!old.sourceId().equals(next.sourceId())||"RULE".equals(kind)&&!target.equals(next.baseId()))mismatch();
        boundedClassification(feedback,max(old.classification(),next.classification(),str(row,"classification")));
        return row;
    }
    JsonNode accepted(EnterpriseAccess.Admission a,Map<String,Object> feedback,String proposal,String decision)throws Exception {
        var p=proposal(a,feedback,proposal);if(!"ACCEPTED".equals(str(p,"status")))stale();
        var d=one(db.queryForList("SELECT * FROM change_impact_decision WHERE tenant_id=? AND proposal_id=? AND decision_id=? AND decision='ACCEPT' FOR UPDATE",tenant(a),proposal,decision));
        if(!str(feedback,"project_id").equals(str(d,"project_id"))||!str(p,"reviewer_id").equals(str(d,"created_by")))mismatch();
        metadata(d,impactManifest);var request=codec.read(str(d,"decision_json"));
        if(!codec.hash(request).equals(str(d,"decision_sha256"))||!"ACCEPT".equals(request.path("decision").asText())
                ||!str(d,"impact_id").equals(request.path("impact_id").asText())
                ||!PermissionSchema.next(request.required("expected_revision").asText()).equals(str(p,"revision")))mismatch();
        currentReviewer(a,p,d);
        var impact=one(db.queryForList("SELECT i.*,v.content_sha256 AS registered_sha256 FROM impact_manifest i JOIN version_registry v ON v.tenant_id=i.tenant_id AND v.version_id=i.impact_id AND v.entity_kind='IMPACT_MANIFEST' WHERE i.tenant_id=? AND i.proposal_id=? AND i.impact_id=? AND i.complete=TRUE",tenant(a),proposal,str(d,"impact_id")));
        metadata(impact,impactManifest);var snapshot=codec.read(str(impact,"result_ref"));String hash=codec.hash(snapshot);
        if(!hash.equals(str(impact,"content_sha256"))||!hash.equals(str(impact,"registered_sha256"))
                ||!hash.equals(request.path("impact_sha256").asText())||!snapshot.path("complete").asBoolean()
                ||!str(p,"proposal_sha256").equals(snapshot.path("proposal_sha256").asText())
                ||!str(p,"old_version").equals(snapshot.path("old_version").asText())
                ||!str(p,"new_version").equals(snapshot.path("new_version").asText()))mismatch();
        var applied=one(db.queryForList("SELECT * FROM change_impact_application WHERE tenant_id=? AND decision_id=? AND proposal_id=? AND impact_id=?",tenant(a),decision,proposal,str(d,"impact_id")));
        metadata(applied,impactManifest);if(!hash.equals(str(applied,"snapshot_sha256"))||!str(d,"created_by").equals(str(applied,"created_by")))mismatch();
        boundedClassification(feedback,max(str(d,"classification"),str(impact,"classification"),str(applied,"classification")));
        // All manifest documents must have their real application entry. An empty graph remains legitimately empty.
        var expected=new TreeSet<String>();var invalidated=new TreeSet<String>();
        for(var doc:snapshot.required("documents")) {
            String revision=doc.required("document_revision").asText();if(!expected.add(revision))mismatch();
            var header=documents.authorizeMetadata(a,a.context().context_revision(),doc.required("document_id").asText(),revision).header();
            if(!header.projectId().equals(doc.required("project_id").asText()))mismatch();
            for(var review:doc.required("review_decisions"))if("CONFIRM".equals(review.required("decision").asText())) {
                String reviewId=review.required("review_id").asText();if(!invalidated.add(reviewId))mismatch();
                one(db.queryForList("SELECT invalidation_id FROM review_invalidation WHERE tenant_id=? AND project_id=? AND review_id=? AND trigger_version=? AND reason_code='VERSION_CHANGE_IMPACT'",tenant(a),header.projectId(),reviewId,str(d,"impact_id")));
            }
        }
        var storedInvalidations=codec.read(str(applied,"invalidations_json"));if(!storedInvalidations.isArray())mismatch();
        var suppliedInvalidations=new TreeSet<String>();for(var id:storedInvalidations)if(!id.isTextual()||!suppliedInvalidations.add(id.asText()))mismatch();
        if(!invalidated.equals(suppliedInvalidations))mismatch();
        var entries=db.queryForList("SELECT project_id,document_revision,requested_action,reason FROM change_revision_entry WHERE tenant_id=? AND decision_id=? ORDER BY document_revision LIMIT 10001",tenant(a),decision);
        if(entries.size()>10000||entries.size()!=expected.size()||request.required("targets").size()!=expected.size())mismatch();
        var actual=new TreeSet<String>();
        for(var entry:entries) {
            String revision=str(entry,"document_revision");if(!actual.add(revision))mismatch();
            var matched=new ArrayList<JsonNode>();for(var target:request.required("targets"))if(revision.equals(target.path("document_revision").asText()))matched.add(target);
            if(matched.size()!=1||!str(entry,"requested_action").equals(matched.get(0).path("action").asText())||!str(entry,"reason").equals(matched.get(0).path("reason").asText()))mismatch();
        }
        if(!actual.equals(expected))mismatch();
        return json.object().put("proposal_id",proposal).put("proposal_sha256",str(p,"proposal_sha256"))
            .put("revision_version",str(p,"new_version")).put("decision_id",decision).put("decision_sha256",str(d,"decision_sha256"))
            .put("impact_id",str(d,"impact_id")).put("impact_sha256",hash).put("application_recorded_at",instant(time(applied.get("created_at"))))
            .put("affected_document_count",entries.size()).put("downstream_document_confirmation_claimed",false).put("version_transition_performed",false);
    }
    JsonNode approval(EnterpriseAccess.Admission a,Map<String,Object> feedback,String revision,JsonNode request)throws Exception {
        String project=str(feedback,"project_id"),kind=str(feedback,"kind");var revised=version(a,project,kind,revision);
        boundedClassification(feedback,revised.classification());String approval=KnowledgeFeedbackSchema.text(request,"approval_id");
        if("RULE".equals(kind)) {
            if(!request.required("source_selection").isNull())KnowledgeFeedbackSchema.invalid();
            var rule=rules.read(a,revision);if(!"APPROVED".equals(rule.status()))stale();
            var row=one(db.queryForList("SELECT * FROM rule_approval_decision WHERE tenant_id=? AND rule_version=? AND decision_id=? AND decision='APPROVE' AND to_status='APPROVED' AND resulting_revision=?",tenant(a),revision,approval,rule.revision()));
            metadata(row,ruleSchema.manifestSha256());boundedClassification(feedback,str(row,"classification"));
            var envelope=ruleSchema.receipt(str(row,"receipt_json"));var receipt=envelope.required("data");
            var baseline=codec.read(str(row,"baseline_manifest"));var decisionRequest=codec.read(str(row,"request_json"));
            if(!codec.hash(envelope).equals(str(row,"receipt_sha256"))||!codec.hash(baseline).equals(str(row,"baseline_sha256"))
                    ||!revised.hash().equals(str(row,"rule_sha256"))||!approval.equals(receipt.path("decision_id").asText())
                    ||!revision.equals(receipt.path("rule_version").asText())||!revised.hash().equals(receipt.path("content_sha256").asText())
                    ||!rule.revision().equals(receipt.path("revision").asText())||!"APPROVE".equals(receipt.path("decision").asText())
                    ||!str(row,"created_by").equals(receipt.path("actor_id").asText())||!baseline.equals(decisionRequest.path("baseline_manifest"))
                    ||!codec.hash(codec.read(str(row,"dependency_authorizations"))).equals(baseline.path("dependency_authorizations_sha256").asText()))mismatch();
            var operation=one(db.queryForList("SELECT response_ref,schema_manifest_sha256 FROM operation_record WHERE tenant_id=? AND operation_id=? AND target_id=? AND state='COMPLETED' AND http_status=200",tenant(a),str(row,"operation_id"),revision));
            if(!envelope.equals(codec.read(str(operation,"response_ref")))||!ruleSchema.manifestSha256().equals(str(operation,"schema_manifest_sha256")))mismatch();
            return json.object().put("kind","RULE").put("approval_id",approval).put("revision_version",revision)
                .put("content_sha256",revised.hash()).put("receipt_sha256",str(row,"receipt_sha256")).put("current_rule_revision",rule.revision());
        }
        var selection=request.required("source_selection");if(selection.isNull())KnowledgeFeedbackSchema.invalid();
        var source=single(sourceRows.source(revision));var rights=single(sourceRows.rights(source.rightsRevision()));
        sources.requireCandidate(a,new SourceReadProtocol.SnapshotRequirement(revision,source.rightsRevision(),project,
            selection.required("parse_version").asText(),selection.required("index_version").asText(),approval,
            selection.required("binding_revision").asText(),rights.purpose(),storage.environment(),storage.region()));
        var row=one(db.queryForList("SELECT d.*,v.content_sha256 FROM approval_decision d JOIN version_registry v ON v.tenant_id=d.tenant_id AND v.version_id=d.approval_revision AND v.entity_kind='APPROVAL_DECISION' WHERE d.tenant_id=? AND d.source_version=? AND d.parse_version=? AND d.approval_revision=? AND d.decision='APPROVE'",tenant(a),revision,selection.required("parse_version").asText(),approval));
        var original=json.object().put("schema_version","1.0.0").put("tenant_id",tenant(a)).put("source_version",revision)
            .put("parse_version",str(row,"parse_version")).put("approval_revision",approval).put("decision","APPROVE")
            .put("reviewer_id",str(row,"reviewer_id")).put("reviewed_manifest_sha256",str(row,"reviewed_manifest_sha256"))
            .put("reason",str(row,"reason")).put("decided_at",instant(time(row.get("decided_at"))));
        if(!codec.hash(original).equals(str(row,"content_sha256")))mismatch();
        var operations=db.queryForList("SELECT response_ref,schema_manifest_sha256,classification FROM operation_record WHERE tenant_id=? AND target_id=? AND operation_name='KN-09' AND state='COMPLETED' AND http_status=200 AND JSON_UNQUOTE(JSON_EXTRACT(response_ref,'$.data.approval_revision'))=?",tenant(a),revision,approval);
        var operation=one(operations);if(!sourceSchema.manifestSha256().equals(str(operation,"schema_manifest_sha256")))mismatch();
        boundedClassification(feedback,str(operation,"classification"));var receipt=codec.read(str(operation,"response_ref")).required("data");sourceSchema.verify("Receipt",receipt);
        if(!approval.equals(receipt.path("approval_revision").asText())||!revision.equals(receipt.path("source_version").asText())
                ||!"APPROVE".equals(receipt.path("decision").asText())||!"APPROVED".equals(receipt.path("approval_status").asText())
                ||!str(row,"reviewer_id").equals(receipt.path("actor_id").asText())
                ||!selection.required("parse_version").equals(receipt.path("parse_version"))||!selection.required("index_version").equals(receipt.path("index_version")))mismatch();
        return json.object().put("kind","SOURCE").put("approval_id",approval).put("revision_version",revision)
            .put("content_sha256",revised.hash()).put("approval_sha256",str(row,"content_sha256")).set("source_selection",selection);
    }
    private void currentReviewer(EnterpriseAccess.Admission a,Map<String,Object> proposal,Map<String,Object> decision)throws Exception {
        String actor=str(decision,"created_by"),mode=str(decision,"identity_mode");var member=single(permissions.targetMember(actor));var binding=single(permissions.targetBinding(actor));var now=permissions.databaseTime();
        if(!tenant(a).equals(member.tenantId())||!"ACTIVE".equals(member.status())||member.validFrom().isAfter(now)
                ||member.validTo()!=null&&!member.validTo().isAfter(now)||!"ACTIVE".equals(binding.status())||!mode.equals(binding.mode())
                ||!identities.active(new com.ruoyi.medical.identity.AuthenticatedIdentityBinding.View(mode,actor,binding.issuer(),binding.subject())))denied();
        var original=codec.read(str(decision,"authority_json"));var current=permissions.currentGrant(original.required("grant_id").asText());
        if(current.size()!=1||!original.equals(AgentPublicationWitness.tree(current.get(0))))stale();
        grants.requireResource(new PermissionProtocol.Scope(tenant(a),str(proposal,"project_id"),"PROJECT",str(proposal,"project_id")),actor,mode,"impact.decide","VERSION_CHANGE_IMPACT",current,now);
    }
    private static void metadata(Map<String,Object> row,String manifest){if(!"1.0.0".equals(str(row,"schema_version"))||!manifest.equals(str(row,"schema_manifest_sha256")))mismatch();}
    static String max(String... values){var levels=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");int level=0;for(String value:values){int found=levels.indexOf(value);if(found<0)mismatch();level=Math.max(level,found);}return levels.get(level);}
    static void boundedClassification(Map<String,Object> feedback,String classification){if(!str(feedback,"classification").equals(max(str(feedback,"classification"),classification)))denied();}
    static String tenant(EnterpriseAccess.Admission a){return a.context().tenant_id();}
    static String str(Map<String,Object> row,String key){return Objects.toString(row.get(key),null);}
    static Map<String,Object> one(List<Map<String,Object>> rows){return single(rows);}
    private static <T>T single(List<T> rows){if(rows.size()!=1)throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return rows.get(0);}
    static LocalDateTime time(Object value){if(value instanceof LocalDateTime t)return t;if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();mismatch();return null;}
    static String instant(LocalDateTime value){return value.toInstant(ZoneOffset.UTC).toString();}
    static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    static void stale(){throw new TenantAccessException(ErrorCode.REVIEW_STALE);}
    static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
