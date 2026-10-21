package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Human feedback resolution is not clinical approval. Every claimed milestone consumes its actual original. */
@Service
public final class OperationsFeedbackService {
    private final EnterpriseAccess access;
    private final ResourceReadAuthority resources;
    private final FormalAgentCandidateStore candidates;
    private final FormalAgentAcceptanceStore acceptances;
    private final ObjectProvider<AgentPublicationAuthority> publications;
    private final JdbcTemplate db;
    private final PermissionSchema json;
    private final BusinessOperationsSchema codec=new BusinessOperationsSchema();
    private final String manifest;
    private final com.networknt.schema.JsonSchema schema;
    public OperationsFeedbackService(EnterpriseAccess access,ResourceReadAuthority resources,FormalAgentCandidateStore candidates,
            FormalAgentAcceptanceStore acceptances,ObjectProvider<AgentPublicationAuthority> publications,DataSource data,PermissionSchema json)throws Exception{
        this.access=access;this.resources=resources;this.candidates=candidates;this.acceptances=acceptances;this.publications=publications;this.json=json;
        db=new JdbcTemplate(data);db.setQueryTimeout(10);
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/business-operations-feedback.schema.json"))){
            byte[] bytes=input.readAllBytes();manifest=WireValues.sha256(bytes);
            schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012)
                .getSchema(new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
    }
    JsonNode create(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes)throws Exception{
        var request=codec.request(bytes,Set.of("project_id","target_version","reason"));
        String project=BusinessOperationsSchema.id(request,"project_id"),target=BusinessOperationsSchema.id(request,"target_version");reason(request);json.headers(context,key);
        String hash=codec.hash(json.object().put("operation","CREATE").set("request",request));
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            authorize(a,context,project,"feedback.create");
            var original=candidate(a,project,target);var replay=replay(a,key,hash);if(replay!=null)return replay;
            String id=UUID.randomUUID().toString();var now=now();String classification=original.binding().classification();
            changed(db.update("""
                INSERT INTO feedback(tenant_id,project_id,feedback_id,target_version,reason,actor_id,status,created_by,created_at,classification,revision,updated_at)
                VALUES(?,?,?,?,?,?,'OPEN',?,?,?,1,?)
                """,tenant(a),project,id,target,reason(request),a.actorId(),a.actorId(),now,classification,now));
            return append(a,project,id,target,"0","OPEN",reason(request),null,null,null,key,hash,classification,now);
        }).get());
    }
    JsonNode transition(ApplicationSessionIdentity.Verified subject,String context,String feedback,String key,byte[] bytes)throws Exception{
        WireValues.uuid(feedback);json.headers(context,key);
        var request=codec.request(bytes,Set.of("expected_revision","state","reason","revision_version","acceptance_id","release_id"));
        String expected=BusinessOperationsSchema.text(request,"expected_revision"),state=BusinessOperationsSchema.text(request,"state");PermissionSchema.revision(expected);reason(request);
        String revision=nullableId(request,"revision_version"),acceptance=nullableId(request,"acceptance_id"),release=nullableId(request,"release_id");
        if(!Set.of("REVIEWED","REVISION_LINKED","EVALUATED","PUBLISHED","CLOSED","REJECTED").contains(state))BusinessOperationsSchema.invalid();
        String hash=codec.hash(json.object().put("operation","TRANSITION").put("feedback_id",feedback).set("request",request));
        // Validate feedback scope and exact requested milestone BEFORE preparing physical publication evidence.
        var originalReceipt=access.execute(subject,a->ThrowingSupplier.of(()->{var f=load(a,feedback);authorize(a,context,str(f,"project_id"),"feedback.manage");return replay(a,key,hash);}).get());
        if(originalReceipt!=null)return originalReceipt;
        AgentPublicationAuthority publication=null;AgentPublicationAuthority.PreparedPublication prepared=null;
        if(Set.of("PUBLISHED","CLOSED").contains(state)){
            if(release==null)BusinessOperationsSchema.invalid();publication=publications.getIfAvailable();
            if(publication==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            prepared=publication.prepare(subject,context,release,"AGENT_PUBLICATION",false);
        }
        final var publisher=publication;final var proof=prepared;
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            var f=load(a,feedback);String project=str(f,"project_id"),target=str(f,"target_version");authorize(a,context,project,"feedback.manage");
            var replay=replay(a,key,hash);if(replay!=null)return replay;
            if(!expected.equals(str(f,"revision")))throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
            var previous=one(db.queryForList("SELECT receipt_json,receipt_sha256,schema_version,schema_manifest_sha256 FROM operations_feedback_event WHERE tenant_id=? AND feedback_id=? AND resulting_revision=?",tenant(a),feedback,expected));
            var prior=receipt(previous);if(!str(f,"status").equals(prior.path("state").asText()))mismatch();
            requireTransition(str(f,"status"),state);
            if(!"REVISION_LINKED".equals(state)&&!Objects.equals(revision,nullable(prior,"revision_version")))BusinessOperationsSchema.invalid();
            if(!"EVALUATED".equals(state)&&!Objects.equals(acceptance,nullable(prior,"acceptance_id")))BusinessOperationsSchema.invalid();
            if(!"PUBLISHED".equals(state)&&!Objects.equals(release,nullable(prior,"release_id")))BusinessOperationsSchema.invalid();
            if("REVISION_LINKED".equals(state)){
                if(revision==null || revision.equals(target))BusinessOperationsSchema.invalid();
                var revised=candidate(a,project,revision);
                if(!str(f,"classification").equals(revised.binding().classification()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
                if(revised.binding().createdAt().isBefore(time(f.get("created_at"))))throw new TenantAccessException(ErrorCode.ACTION_STALE);
            }
            if("EVALUATED".equals(state)){
                if(acceptance==null)BusinessOperationsSchema.invalid();
                var row=one(db.queryForList("SELECT content_sha256 FROM formal_agent_acceptance WHERE tenant_id=? AND project_id=? AND acceptance_id=?",tenant(a),project,acceptance));
                var verified=acceptances.metadata(a,new PermissionProtocol.Basis(acceptance,str(row,"content_sha256")),"AGENT_PUBLICATION");
                if(!revision.equals(verified.row().candidateVersionId()) || !"ACCEPT".equals(verified.row().decision()))mismatch();
            }
            if(publisher!=null){
                var published=publisher.require(a,release,"AGENT_PUBLICATION",proof);var m=published.manifest().manifest();
                var actual=one(db.queryForList("SELECT acceptance_id,evaluation_id,candidate_version_id FROM formal_agent_acceptance WHERE tenant_id=? AND project_id=? AND release_id=?",tenant(a),project,release));
                if(!revision.equals(m.agentVersionId())||!acceptance.equals(str(actual,"acceptance_id"))||!m.evaluationId().equals(str(actual,"evaluation_id"))||!revision.equals(str(actual,"candidate_version_id")))mismatch();
                published.requireUnchanged();
            }
            var now=now();var receipt=append(a,project,feedback,target,expected,state,reason(request),revision,acceptance,release,key,hash,str(f,"classification"),now);
            changed(db.update("UPDATE feedback SET status=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND feedback_id=? AND revision=? AND status=?",state,now,tenant(a),feedback,expected,str(f,"status")));
            return receipt;
        }).get());
    }
    JsonNode list(ApplicationSessionIdentity.Verified subject,String context,String project,String after){
        WireValues.uuid(project);if(after!=null)WireValues.uuid(after);
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            authorize(a,context,project,"feedback.read");
            var rows=db.queryForList("""
                SELECT f.feedback_id,e.receipt_json,e.receipt_sha256,e.schema_version,e.schema_manifest_sha256
                FROM feedback f JOIN operations_feedback_event e ON e.tenant_id=f.tenant_id AND e.project_id=f.project_id AND e.feedback_id=f.feedback_id AND e.resulting_revision=f.revision
                WHERE f.tenant_id=? AND f.project_id=? AND (? IS NULL OR f.feedback_id>?) ORDER BY f.feedback_id LIMIT 101
                """,tenant(a),project,after,after);
            var items=json.object().arrayNode();for(int i=0;i<Math.min(rows.size(),100);i++){var value=receipt(rows.get(i));if(!project.equals(value.path("project_id").asText())||!str(rows.get(i),"feedback_id").equals(value.path("feedback_id").asText()))mismatch();items.add(value);}
            var result=json.object().put("schema_version","1.0.0").put("project_id",project).put("complete",rows.size()<=100);result.set("items",items);
            if(rows.size()>100)result.put("next_after_id",str(rows.get(99),"feedback_id"));else result.putNull("next_after_id");return result;
        }).get());
    }
    JsonNode history(ApplicationSessionIdentity.Verified subject,String context,String feedback){
        WireValues.uuid(feedback);return access.execute(subject,a->ThrowingSupplier.of(()->{
            var f=load(a,feedback);authorize(a,context,str(f,"project_id"),"feedback.read");
            var rows=db.queryForList("SELECT receipt_json,receipt_sha256,schema_version,schema_manifest_sha256 FROM operations_feedback_event WHERE tenant_id=? AND feedback_id=? ORDER BY resulting_revision LIMIT 1001",tenant(a),feedback);
            if(rows.size()>1000)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);var items=json.object().arrayNode();for(var row:rows){var value=receipt(row);if(!feedback.equals(value.path("feedback_id").asText())||!str(f,"project_id").equals(value.path("project_id").asText())||!str(f,"target_version").equals(value.path("target_version").asText())||!Integer.toString(items.size()+1).equals(value.path("revision").asText()))mismatch();items.add(value);}
            if(items.isEmpty())throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
            if(!str(f,"revision").equals(items.get(items.size()-1).path("revision").asText())||!str(f,"status").equals(items.get(items.size()-1).path("state").asText()))mismatch();
            var result=json.object().put("schema_version","1.0.0").put("feedback_id",feedback).put("revision",str(f,"revision"));
            result.set("events",items);
            return result;
        }).get());
    }
    static void requireTransition(String from,String to){
        boolean allowed=switch(from){case "OPEN"->Set.of("REVIEWED","REJECTED").contains(to);case "REVIEWED"->Set.of("REVISION_LINKED","REJECTED").contains(to);case "REVISION_LINKED"->"EVALUATED".equals(to);case "EVALUATED"->"PUBLISHED".equals(to);case "PUBLISHED"->"CLOSED".equals(to);default->false;};
        if(!allowed)throw new TenantAccessException(ErrorCode.ACTION_STALE);
    }
    private JsonNode append(EnterpriseAccess.Admission a,String project,String id,String target,String expected,String state,String reason,String revision,String acceptance,String release,String key,String hash,String classification,LocalDateTime now)throws Exception{
        var result=json.object().put("schema_version","1.0.0").put("feedback_id",id).put("project_id",project).put("target_version",target)
            .put("state",state).put("revision",PermissionSchema.next(expected)).put("reason",reason).put("revision_version",revision).put("acceptance_id",acceptance).put("release_id",release)
            .put("actor_id",a.actorId()).put("recorded_at",now.toInstant(ZoneOffset.UTC).toString());
        if(!schema.validate(result).isEmpty())BusinessOperationsSchema.invalid();String raw=codec.canonical(result);
        changed(db.update("""
            INSERT INTO operations_feedback_event(tenant_id,project_id,event_id,feedback_id,expected_revision,resulting_revision,state,revision_version,acceptance_id,release_id,idempotency_key,request_sha256,receipt_json,receipt_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)
            """,tenant(a),project,UUID.randomUUID().toString(),id,expected,PermissionSchema.next(expected),state,revision,acceptance,release,key,hash,raw,codec.hash(result),manifest,a.actorId(),now,classification));
        changed(db.update("""
            INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES(?,?,?,?,'feedback.transitioned',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)
            """,tenant(a),project,UUID.randomUUID().toString(),a.actorId(),raw,id,id,manifest,a.actorId(),now,classification));
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,?,'feedback.transitioned',?,?)",
            tenant(a),UUID.randomUUID().toString(),id,PermissionSchema.next(expected),raw,now));
        return result;
    }
    private FormalAgentCandidateStore.Loaded candidate(EnterpriseAccess.Admission a,String project,String version)throws Exception{
        var row=one(db.queryForList("SELECT content_sha256 FROM formal_agent_candidate_binding WHERE tenant_id=? AND project_id=? AND version_id=?",tenant(a),project,version));
        return candidates.read(a,new PermissionProtocol.Basis(version,str(row,"content_sha256")),"AGENT_PUBLICATION");
    }
    private JsonNode replay(EnterpriseAccess.Admission a,String key,String hash)throws Exception{
        var rows=db.queryForList("SELECT request_sha256,receipt_json,receipt_sha256,schema_version,schema_manifest_sha256 FROM operations_feedback_event WHERE tenant_id=? AND created_by=? AND idempotency_key=?",tenant(a),a.actorId(),key);
        if(rows.isEmpty())return null;var row=one(rows);if(!hash.equals(str(row,"request_sha256")))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);return receipt(row);
    }
    private JsonNode receipt(Map<String,Object> row)throws Exception{var value=codec.read(str(row,"receipt_json"));if(!"1.0.0".equals(str(row,"schema_version"))||!manifest.equals(str(row,"schema_manifest_sha256"))||!codec.hash(value).equals(str(row,"receipt_sha256"))||!schema.validate(value).isEmpty())mismatch();return value;}
    private Map<String,Object> load(EnterpriseAccess.Admission a,String id){return one(db.queryForList("""
        SELECT f.* FROM feedback f WHERE f.tenant_id=? AND f.feedback_id=?
          AND EXISTS(SELECT 1 FROM operations_feedback_event e WHERE e.tenant_id=f.tenant_id AND e.project_id=f.project_id AND e.feedback_id=f.feedback_id)
        FOR UPDATE
        """,tenant(a),id));}
    private void authorize(EnterpriseAccess.Admission a,String context,String project,String action)throws Exception{
        if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        resources.require(a,new PermissionProtocol.Scope(tenant(a),project,"PROJECT",project),action,"BUSINESS_OPERATIONS");
    }
    private static String reason(JsonNode n){String value=BusinessOperationsSchema.text(n,"reason");if(value.length()>4000)BusinessOperationsSchema.invalid();return value;}
    private static String nullableId(JsonNode n,String key){if(n.required(key).isNull())return null;return BusinessOperationsSchema.id(n,key);}
    private static String nullable(JsonNode n,String key){return n.path(key).isNull()?null:n.path(key).asText();}
    private static String tenant(EnterpriseAccess.Admission a){return a.context().tenant_id();}
    private static String str(Map<String,Object> row,String key){return Objects.toString(row.get(key),null);}
    private static Map<String,Object> one(List<Map<String,Object>> rows){if(rows.size()!=1)throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return rows.get(0);}
    private static LocalDateTime time(Object value){if(value instanceof LocalDateTime t)return t;if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private LocalDateTime now(){return Objects.requireNonNull(db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class));}
    private static void changed(int count){if(count!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
