package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.KnowledgeFeedbackProofs.*;

/** PRD 10.1(7): human feedback is queued, never silently promoted to knowledge/rule authority. */
@Service
public final class KnowledgeFeedbackService {
    private final EnterpriseAccess access;
    private final ResourceReadAuthority resources;
    private final KnowledgeFeedbackProofs proofs;
    private final KnowledgeFeedbackSchema codec;
    private final PermissionSchema json;
    private final JdbcTemplate db;
    public KnowledgeFeedbackService(EnterpriseAccess access,ResourceReadAuthority resources,KnowledgeFeedbackProofs proofs,
            KnowledgeFeedbackSchema codec,PermissionSchema json,DataSource data){
        this.access=access;this.resources=resources;this.proofs=proofs;this.codec=codec;this.json=json;db=new JdbcTemplate(data);db.setQueryTimeout(5);
    }
    JsonNode create(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes)throws Exception {
        json.headers(context,key);var request=codec.request("Create",bytes);String project=text(request,"project_id"),kind=text(request,"kind"),target=text(request,"target_version");
        return tx(subject,a->{
            authorize(a,context,project,"feedback.create");var targetProof=proofs.version(a,project,kind,target);
            var origin=proofs.origin(a,project,request);String hash=requestHash("CREATE",null,request);
            var replay=replay(a,key,hash);if(replay!=null)return replay;
            var projectRow=one(db.queryForList("SELECT classification FROM project WHERE tenant_id=? AND project_id=?",tenant(a),project));
            String classification=max(str(projectRow,"classification"),targetProof.classification());
            for(var value:origin)classification=max(classification,value.required("classification").asText());
            String feedback=UUID.randomUUID().toString();var now=now();
            var original=json.object().put("schema_version","1.0.0").put("feedback_id",feedback).put("project_id",project)
                .put("actor_id",a.actorId()).put("recorded_at",instant(now)).put("classification",classification);
            original.set("request",request);original.set("target",targetProof.snapshot());original.set("origin",origin);
            codec.validate("Original",original);String originalHash=codec.hash(original);
            changed(db.update("""
                INSERT INTO feedback(tenant_id,project_id,feedback_id,target_version,document_revision,reason,actor_id,status,created_by,created_at,classification,revision,updated_at)
                VALUES(?,?,?,?,?,?,?,'PENDING_REVIEW',?,?,?,1,?)
                """,tenant(a),project,feedback,target,nullable(request,"document_revision"),text(request,"reason"),a.actorId(),a.actorId(),now,classification,now));
            var values=meta(a,now,classification);values.put("project_id",project);values.put("feedback_id",feedback);values.put("kind",kind);
            values.put("target_version",target);values.put("target_sha256",targetProof.hash());values.put("document_revision",nullable(request,"document_revision"));
            values.put("revised_document_revision",nullable(request,"revised_document_revision"));values.put("review_id",nullable(request,"review_id"));values.put("return_id",nullable(request,"return_id"));
            values.put("original_json",codec.canonical(original));values.put("original_sha256",originalHash);insert("knowledge_feedback_origin",values);
            var f=load(a,feedback);return append(a,f,null,"PENDING_REVIEW",text(request,"reason"),null,null,null,null,null,key,hash,now);
        });
    }
    JsonNode linkProposal(ApplicationSessionIdentity.Verified subject,String context,String feedback,String key,byte[] bytes)throws Exception {
        return mutate(subject,context,feedback,key,bytes,"LinkProposal","PROPOSAL_LINKED",(a,f,prior,request)->{
            var p=proofs.proposal(a,f,text(request,"proposal_id"));
            if(!Set.of("PROPOSED","ACCEPTED").contains(str(p,"status")))stale();
            return references(str(p,"proposal_id"),str(p,"new_version"),null,null,null);
        });
    }
    JsonNode linkReview(ApplicationSessionIdentity.Verified subject,String context,String feedback,String key,byte[] bytes)throws Exception {
        return mutate(subject,context,feedback,key,bytes,"LinkReview","REVIEWED",(a,f,prior,request)->{
            String proposal=text(prior,"proposal_id"),decision=text(request,"decision_id");
            var actual=proofs.accepted(a,f,proposal,decision);
            if(!text(prior,"revision_version").equals(text(actual,"revision_version")))mismatch();
            return references(proposal,text(prior,"revision_version"),decision,actual,null);
        });
    }
    JsonNode close(ApplicationSessionIdentity.Verified subject,String context,String feedback,String key,byte[] bytes)throws Exception {
        return mutate(subject,context,feedback,key,bytes,"Close","CLOSED",(a,f,prior,request)->{
            var change=proofs.accepted(a,f,text(prior,"proposal_id"),text(prior,"decision_id"));
            if(!change.equals(prior.required("change_proof")))stale();
            var approval=proofs.approval(a,f,text(prior,"revision_version"),request);
            return references(text(prior,"proposal_id"),text(prior,"revision_version"),text(prior,"decision_id"),change,approval);
        });
    }
    JsonNode reject(ApplicationSessionIdentity.Verified subject,String context,String feedback,String key,byte[] bytes)throws Exception {
        return mutate(subject,context,feedback,key,bytes,"Reject","REJECTED",(a,f,prior,request)->
            references(nullable(prior,"proposal_id"),nullable(prior,"revision_version"),null,null,null));
    }
    private JsonNode mutate(ApplicationSessionIdentity.Verified subject,String context,String feedback,String key,byte[] bytes,String definition,String state,Mutation mutation)throws Exception {
        WireValues.uuid(feedback);json.headers(context,key);var request=codec.request(definition,bytes);PermissionSchema.revision(text(request,"expected_revision"));
        String hash=requestHash(definition,feedback,request);
        return tx(subject,a->{
            var f=load(a,feedback);authorize(a,context,str(f,"project_id"),"feedback.manage");authorizeOriginal(a,f);
            var replay=replay(a,key,hash);if(replay!=null)return replay;
            if(!str(f,"revision").equals(text(request,"expected_revision")))throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
            var prior=current(a,f);authorizeReferences(a,f,prior);requireTransition(str(f,"status"),state);var refs=mutation.apply(a,f,prior,request);
            var now=now();var receipt=append(a,f,prior,state,text(request,"reason"),nullable(refs,"proposal_id"),nullable(refs,"revision_version"),
                nullable(refs,"decision_id"),refs.required("change_proof"),refs.required("approval_proof"),key,hash,now);
            changed(db.update("UPDATE feedback SET status=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND feedback_id=? AND revision=? AND status=?",
                state,now,tenant(a),str(f,"project_id"),feedback,str(f,"revision"),str(f,"status")));
            return receipt;
        });
    }
    JsonNode history(ApplicationSessionIdentity.Verified subject,String context,String feedback){
        WireValues.uuid(feedback);return tx(subject,a->{
            var f=load(a,feedback);authorize(a,context,str(f,"project_id"),"feedback.read");authorizeOriginal(a,f);
            var rows=db.queryForList("SELECT * FROM knowledge_feedback_event WHERE tenant_id=? AND feedback_id=? ORDER BY resulting_revision LIMIT 1001",tenant(a),feedback);
            if(rows.size()>1000)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            var events=json.object().arrayNode();JsonNode previous=null;
            for(var row:rows){var receipt=receipt(row);requireChain(f,previous,receipt);authorizeReferences(a,f,receipt);events.add(receipt);previous=receipt;}
            if(previous==null||!str(f,"revision").equals(text(previous,"revision"))||!str(f,"status").equals(text(previous,"state")))mismatch();
            var result=json.object().put("schema_version","1.0.0").put("feedback_id",feedback).put("history_only",true).put("revision",str(f,"revision"));
            result.set("original",original(f));result.set("events",events);return result;
        });
    }
    JsonNode list(ApplicationSessionIdentity.Verified subject,String context,String project,String after){
        WireValues.uuid(project);if(after!=null)WireValues.uuid(after);return tx(subject,a->{
            authorize(a,context,project,"feedback.read");
            var ids=db.queryForList("SELECT o.feedback_id FROM knowledge_feedback_origin o WHERE o.tenant_id=? AND o.project_id=? AND (? IS NULL OR o.feedback_id>?) ORDER BY o.feedback_id LIMIT 101",tenant(a),project,after,after);
            var items=json.object().arrayNode();
            for(int i=0;i<Math.min(100,ids.size());i++){
                var f=load(a,str(ids.get(i),"feedback_id"));authorizeOriginal(a,f);var current=current(a,f);authorizeReferences(a,f,current);items.add(current);
            }
            var result=json.object().put("schema_version","1.0.0").put("project_id",project).put("history_only",true).put("complete",ids.size()<=100);
            result.put("next_after_id",ids.size()>100?str(ids.get(99),"feedback_id"):null);result.set("items",items);return result;
        });
    }
    static void requireTransition(String from,String to){
        boolean allowed=switch(from){case "PENDING_REVIEW"->Set.of("PROPOSAL_LINKED","REJECTED").contains(to);case "PROPOSAL_LINKED"->Set.of("REVIEWED","REJECTED").contains(to);case "REVIEWED"->"CLOSED".equals(to);default->false;};
        if(!allowed)throw new TenantAccessException(ErrorCode.ACTION_STALE);
    }
    private JsonNode append(EnterpriseAccess.Admission a,Map<String,Object> f,JsonNode prior,String state,String reason,String proposal,String revised,String decision,JsonNode change,JsonNode approval,String key,String requestHash,LocalDateTime now)throws Exception {
        String expected=prior==null?"0":text(prior,"revision"),revision=PermissionSchema.next(expected),previousHash=prior==null?null:codec.hash(prior);
        var result=json.object().put("schema_version","1.0.0").put("feedback_id",str(f,"feedback_id")).put("project_id",str(f,"project_id"))
            .put("kind",str(f,"kind")).put("target_version",str(f,"target_version")).put("target_sha256",str(f,"target_sha256"))
            .put("original_sha256",str(f,"original_sha256")).put("revision",revision).put("state",state).put("reason",reason)
            .put("proposal_id",proposal).put("revision_version",revised).put("decision_id",decision).put("previous_receipt_sha256",previousHash)
            .put("actor_id",a.actorId()).put("recorded_at",instant(now)).put("classification",str(f,"classification"))
            .put("closure_scope","KNOWLEDGE_RULE_FEEDBACK_ONLY").put("downstream_document_confirmation_claimed",false);
        result.set("change_proof",change==null?json.object().nullNode():change);result.set("approval_proof",approval==null?json.object().nullNode():approval);
        codec.validate("Receipt",result);requireChain(f,prior,result);
        var values=meta(a,now,str(f,"classification"));values.put("project_id",str(f,"project_id"));values.put("event_id",UUID.randomUUID().toString());
        values.put("feedback_id",str(f,"feedback_id"));values.put("expected_revision",expected);values.put("resulting_revision",revision);values.put("state",state);
        values.put("proposal_id",proposal);values.put("revision_version",revised);values.put("decision_id",decision);
        values.put("source_approval_revision",approval!=null&&!approval.isNull()&&"SOURCE".equals(str(f,"kind"))?text(approval,"approval_id"):null);
        values.put("rule_approval_decision_id",approval!=null&&!approval.isNull()&&"RULE".equals(str(f,"kind"))?text(approval,"approval_id"):null);
        values.put("idempotency_key",key);values.put("request_sha256",requestHash);values.put("previous_receipt_sha256",previousHash);
        values.put("receipt_json",codec.canonical(result));values.put("receipt_sha256",codec.hash(result));insert("knowledge_feedback_event",values);
        var audit=meta(a,now,str(f,"classification"));audit.put("project_id",str(f,"project_id"));audit.put("audit_id",UUID.randomUUID().toString());audit.put("subject_id",a.actorId());
        audit.put("action","knowledge_feedback."+state.toLowerCase(Locale.ROOT));audit.put("resource_ref",codec.canonical(result));audit.put("result","SUCCESS");audit.put("request_id",str(f,"feedback_id"));audit.put("trace_id",UUID.randomUUID().toString());insert("audit_event",audit);
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,?,?,?,?)",
            tenant(a),UUID.randomUUID().toString(),str(f,"feedback_id"),revision,"knowledge_feedback."+state.toLowerCase(Locale.ROOT),codec.canonical(result),now));
        return result;
    }
    private Map<String,Object> load(EnterpriseAccess.Admission a,String id)throws Exception {
        var f=one(db.queryForList("SELECT f.*,o.kind,o.target_sha256,o.original_json,o.original_sha256,o.schema_version,o.schema_manifest_sha256 FROM feedback f JOIN knowledge_feedback_origin o ON o.tenant_id=f.tenant_id AND o.project_id=f.project_id AND o.feedback_id=f.feedback_id AND o.target_version=f.target_version WHERE f.tenant_id=? AND f.feedback_id=? FOR UPDATE",tenant(a),id));
        var original=original(f);var request=original.required("request");
        if(!id.equals(text(original,"feedback_id"))||!str(f,"project_id").equals(text(original,"project_id"))
                ||!str(f,"project_id").equals(text(request,"project_id"))||!str(f,"kind").equals(text(request,"kind"))||!str(f,"target_version").equals(text(request,"target_version"))
                ||!Objects.equals(str(f,"document_revision"),nullable(request,"document_revision"))
                ||!str(f,"target_sha256").equals(original.required("target").path("content_sha256").asText())
                ||!str(f,"reason").equals(text(request,"reason"))||!str(f,"created_by").equals(text(original,"actor_id"))
                ||!str(f,"actor_id").equals(str(f,"created_by"))||!str(f,"classification").equals(text(original,"classification"))
                ||!instant(time(f.get("created_at"))).equals(text(original,"recorded_at"))||f.get("proposal_id")!=null)mismatch();
        return f;
    }
    private JsonNode original(Map<String,Object> f)throws Exception {
        var original=codec.read(str(f,"original_json"));codec.validate("Original",original);
        if(!"1.0.0".equals(str(f,"schema_version"))||!codec.manifest().equals(str(f,"schema_manifest_sha256"))||!codec.hash(original).equals(str(f,"original_sha256")))mismatch();return original;
    }
    private void authorizeOriginal(EnterpriseAccess.Admission a,Map<String,Object> f)throws Exception {
        var target=proofs.version(a,str(f,"project_id"),str(f,"kind"),str(f,"target_version"));if(!target.hash().equals(str(f,"target_sha256")))mismatch();boundedClassification(f,target.classification());
        var original=original(f);if(!proofs.origin(a,str(f,"project_id"),original.required("request")).equals(original.required("origin")))mismatch();
    }
    private void authorizeReferences(EnterpriseAccess.Admission a,Map<String,Object> f,JsonNode receipt)throws Exception {
        String revised=nullable(receipt,"revision_version");if(revised!=null)boundedClassification(f,proofs.version(a,str(f,"project_id"),str(f,"kind"),revised).classification());
    }
    private JsonNode current(EnterpriseAccess.Admission a,Map<String,Object> f)throws Exception {
        var receipt=receipt(one(db.queryForList("SELECT * FROM knowledge_feedback_event WHERE tenant_id=? AND feedback_id=? AND resulting_revision=?",tenant(a),str(f,"feedback_id"),str(f,"revision"))));
        if(!str(f,"status").equals(text(receipt,"state"))||!str(f,"feedback_id").equals(text(receipt,"feedback_id"))||!str(f,"original_sha256").equals(text(receipt,"original_sha256")))mismatch();return receipt;
    }
    private JsonNode replay(EnterpriseAccess.Admission a,String key,String hash)throws Exception {
        var rows=db.queryForList("SELECT * FROM knowledge_feedback_event WHERE tenant_id=? AND created_by=? AND idempotency_key=?",tenant(a),a.actorId(),key);
        if(rows.isEmpty())return null;var row=one(rows);if(!hash.equals(str(row,"request_sha256")))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        var receipt=receipt(row);var f=load(a,text(receipt,"feedback_id"));authorize(a,a.context().context_revision(),str(f,"project_id"),"feedback.read");authorizeOriginal(a,f);authorizeReferences(a,f,receipt);return receipt;
    }
    private JsonNode receipt(Map<String,Object> row)throws Exception {
        var receipt=codec.read(str(row,"receipt_json"));codec.validate("Receipt",receipt);
        if(!"1.0.0".equals(str(row,"schema_version"))||!codec.manifest().equals(str(row,"schema_manifest_sha256"))||!codec.hash(receipt).equals(str(row,"receipt_sha256")))mismatch();
        for(String key:List.of("feedback_id","project_id","state","proposal_id","revision_version","decision_id","previous_receipt_sha256","classification"))if(!Objects.equals(str(row,key),nullable(receipt,key)))mismatch();
        if(!str(row,"resulting_revision").equals(text(receipt,"revision"))||!str(row,"created_by").equals(text(receipt,"actor_id"))||!instant(time(row.get("created_at"))).equals(text(receipt,"recorded_at")))mismatch();
        var approval=receipt.required("approval_proof");
        String sourceApproval=approval.isNull()||!"SOURCE".equals(text(receipt,"kind"))?null:text(approval,"approval_id");
        String ruleApproval=approval.isNull()||!"RULE".equals(text(receipt,"kind"))?null:text(approval,"approval_id");
        if(!Objects.equals(str(row,"source_approval_revision"),sourceApproval)||!Objects.equals(str(row,"rule_approval_decision_id"),ruleApproval))mismatch();return receipt;
    }
    private void requireChain(Map<String,Object> f,JsonNode previous,JsonNode next)throws Exception {
        if(!str(f,"feedback_id").equals(text(next,"feedback_id"))||!str(f,"project_id").equals(text(next,"project_id"))||!str(f,"kind").equals(text(next,"kind"))||!str(f,"classification").equals(text(next,"classification"))||!str(f,"target_version").equals(text(next,"target_version"))||!str(f,"target_sha256").equals(text(next,"target_sha256"))||!str(f,"original_sha256").equals(text(next,"original_sha256")))mismatch();
        if(previous==null){if(!"1".equals(text(next,"revision"))||!"PENDING_REVIEW".equals(text(next,"state"))||nullable(next,"previous_receipt_sha256")!=null)mismatch();return;}
        requireTransition(text(previous,"state"),text(next,"state"));
        if(!PermissionSchema.next(text(previous,"revision")).equals(text(next,"revision"))||!codec.hash(previous).equals(text(next,"previous_receipt_sha256")))mismatch();
        if(!"PROPOSAL_LINKED".equals(text(next,"state")))for(String key:List.of("proposal_id","revision_version"))if(!next.required(key).equals(previous.required(key)))mismatch();
        if("CLOSED".equals(text(next,"state"))&&(!next.required("decision_id").equals(previous.required("decision_id"))||!next.required("change_proof").equals(previous.required("change_proof"))))mismatch();
    }
    private JsonNode references(String proposal,String revision,String decision,JsonNode change,JsonNode approval){var result=json.object().put("proposal_id",proposal).put("revision_version",revision).put("decision_id",decision);result.set("change_proof",change==null?json.object().nullNode():change);result.set("approval_proof",approval==null?json.object().nullNode():approval);return result;}
    private void authorize(EnterpriseAccess.Admission a,String context,String project,String action)throws Exception {StrictComparisonAuthority.human(a,context);resources.require(a,new PermissionProtocol.Scope(tenant(a),project,"PROJECT",project),action,"BUSINESS_OPERATIONS");}
    private String requestHash(String operation,String feedback,JsonNode request)throws Exception {return codec.hash(json.object().put("operation",operation).put("feedback_id",feedback).set("request",request));}
    private Map<String,Object> meta(EnterpriseAccess.Admission a,LocalDateTime now,String classification){return new LinkedHashMap<>(Map.of("tenant_id",tenant(a),"schema_version","1.0.0","schema_manifest_sha256",codec.manifest(),"created_by",a.actorId(),"created_at",now,"classification",classification));}
    private void insert(String table,Map<String,Object> values){var keys=new ArrayList<>(values.keySet());changed(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(values::get).toArray()));}
    private LocalDateTime now(){return Objects.requireNonNull(db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class));}
    private static String text(JsonNode n,String k){return KnowledgeFeedbackSchema.text(n,k);}
    private static String nullable(JsonNode n,String k){return KnowledgeFeedbackSchema.nullable(n,k);}
    private static void changed(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
    private <T>T tx(ApplicationSessionIdentity.Verified subject,Work<T> work){return access.execute(subject,a->ThrowingSupplier.of(()->work.run(a)).get());}
    @FunctionalInterface private interface Work<T>{T run(EnterpriseAccess.Admission a)throws Exception;}
    @FunctionalInterface private interface Mutation{JsonNode apply(EnterpriseAccess.Admission a,Map<String,Object> feedback,JsonNode prior,JsonNode request)throws Exception;}
}
