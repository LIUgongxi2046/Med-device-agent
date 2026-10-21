package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Actual host AT10 dispatch registration, after genuine candidate persistence. Not a model call. */
public final class TaskPlanDraftSaveAction {
    private final JdbcTemplate db;private final TaskPlanDraftSchema schema;private final PermissionSchema json;
    private final RunTokenAuthoritySchema runtime;private final FormalCapabilityIdentityAuthority capabilities;private final SchemaCatalogue shared;
    public TaskPlanDraftSaveAction(DataSource data,TaskPlanDraftSchema schema,PermissionSchema json,RunTokenAuthoritySchema runtime,
            FormalCapabilityIdentityAuthority capabilities)throws Exception {db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.json=json;this.runtime=runtime;this.capabilities=capabilities;shared=SchemaCatalogue.fromClasspath();}
    Bound bind(TaskPlanDraftIndependentCheck.Checked checked)throws Exception {
        checked.requireUnchanged();var candidate=checked.candidate();var host=candidate.produced().authority();var current=host.current();var j=host.job();
        var requested=new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(j.tenant(),j.project(),"TASK",j.task()),"plan.write","PLAN_MAINTENANCE");
        if(!current.nodePermissions().contains(requested))deny();var tools=new ArrayList<PermissionProtocol.Basis>();
        for(var version:current.current().releaseManifest().toolVersions()){
            var basis=new PermissionProtocol.Basis(version.versionId(),version.contentSha256());var verified=capabilities.require(current.admission(),basis,"TOOL",j.project(),TaskPlanDraftAdmission.PURPOSE);
            var definition=verified.canonicalDefinition().required("request");var input=definition.required("input_schema_ref");var output=definition.required("output_schema_ref");
            if(shared.schemaRef("tool-inputs","PlanArgs").equals(input.required("schema_ref").asText())){
                if(!shared.schemaDigest("tool-inputs").equals(input.required("schema_sha256").asText())||!shared.schemaRef("tool-success",null).equals(output.required("schema_ref").asText())||!shared.schemaDigest("tool-success").equals(output.required("schema_sha256").asText()))bad();
                boolean allowed=false;for(var p:definition.required("tool_scope").required("permissions"))if(FormalTaskNodeAuthority.sameEvidence(json.tree(requested),p))allowed=true;
                if(!allowed)deny();tools.add(basis);
            }verified.requireUnchanged();
        }
        if(tools.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);var tool=tools.get(0);
        String action=derived(candidate.produced().sessionId(),candidate.artifactId()+"/AT-10"),call=derived(action,"call");
        var args=json.object().put("plan_artifact_id",candidate.artifactId());shared.validate("tool-inputs",args,"PlanArgs");String arguments=schema.hash(args);
        var request=json.object().put("schema_version","1.0.0").put("kind","PLAN_DRAFT_HOST_SAVE").put("tool_id","AT-10").put("session_id",candidate.produced().sessionId())
                .put("call_id",call).put("action_id",action).put("host_action_id",host.actionId()).put("tool_version",tool.version_id()).put("tool_sha256",tool.content_sha256())
                .put("candidate_receipt_sha256",candidate.receiptSha256());request.set("arguments",args);
        String requestHash=schema.hash(request);var prior=rows(j,action);
        if(prior.isEmpty()){
            var now=now();var row=meta(j,now,runtime.manifestSha256());row.put("run_id",j.run());row.put("step_id",j.step());row.put("action_id",action);row.put("input_sha256",j.inputHash());
            row.put("origin_run_id",j.run());row.put("origin_step_id",j.step());row.put("origin_action_id",host.actionId());row.put("kind","TOOL_CALL");row.put("arguments_sha256",arguments);
            row.put("tool_version",tool.version_id());row.put("state","BOUND");row.put("operation_id",j.operation());row.put("lease_generation",host.leaseGeneration());row.put("revision",1);row.put("updated_at",now);insert("step_action_binding",row);
            var audit=meta(j,now,schema.manifest());audit.put("audit_id",action);audit.put("subject_id",j.actor());audit.put("action","task.plan_draft.save.dispatched");audit.put("resource_ref",schema.canonical(request));audit.put("result","SUCCESS");audit.put("request_id",call);audit.put("trace_id",j.run());insert("audit_event",audit);
            insert("outbox_event",Map.of("tenant_id",j.tenant(),"event_id",call,"aggregate_id",j.run(),"aggregate_revision",1,"event_type","task.plan_draft.save.dispatched","payload",schema.canonical(request),"occurred_at",now));
        }
        var bound=new Bound(j,host.actionId(),host.leaseGeneration(),candidate.produced().sessionId(),action,call,tool,arguments,requestHash,request);require(bound,checked);return bound;
    }
    void require(Bound b,TaskPlanDraftIndependentCheck.Checked checked)throws Exception {
        checked.requireUnchanged();var c=checked.candidate();var host=c.produced().authority();var j=host.job();if(!j.equals(b.job)||!host.actionId().equals(b.host)||!host.leaseGeneration().equals(b.generation)||!c.produced().sessionId().equals(b.session))bad();
        var row=one(rows(j,b.action));var at=time(row,"created_at");var fields=meta(j,at,runtime.manifestSha256());fields.putAll(Map.of("run_id",j.run(),"step_id",j.step(),"action_id",b.action,"input_sha256",j.inputHash(),"origin_run_id",j.run(),"origin_step_id",j.step(),"origin_action_id",b.host,"kind","TOOL_CALL","arguments_sha256",b.arguments,"tool_version",b.tool.version_id()));fields.put("operation_id",j.operation());fields.put("lease_generation",b.generation);same(fields,row);
        if(!Set.of("BOUND","DISPATCHING","IN_FLIGHT","SETTLED").contains(row.get("state")))bad();
        var audit=meta(j,at,schema.manifest());audit.putAll(Map.of("audit_id",b.action,"subject_id",j.actor(),"action","task.plan_draft.save.dispatched","resource_ref",schema.canonical(b.request),"result","SUCCESS","request_id",b.call,"trace_id",j.run()));same(audit,one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",j.tenant(),b.action)));
        same(Map.of("tenant_id",j.tenant(),"event_id",b.call,"aggregate_id",j.run(),"aggregate_revision",1,"event_type","task.plan_draft.save.dispatched","payload",schema.canonical(b.request),"occurred_at",at),one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",j.tenant(),b.call)));
        if(!b.requestHash.equals(schema.hash(b.request))||!c.artifactId().equals(b.request.required("arguments").required("plan_artifact_id").asText())||!c.receiptSha256().equals(b.request.required("candidate_receipt_sha256").asText()))bad();
    }
    public static final class Bound {
        private final TaskPlanDraftJobs.Job job;private final String host,session,action,call,arguments,requestHash;private final java.math.BigInteger generation;private final PermissionProtocol.Basis tool;private final JsonNode request;
        private Bound(TaskPlanDraftJobs.Job job,String host,java.math.BigInteger generation,String session,String action,String call,PermissionProtocol.Basis tool,String arguments,String requestHash,JsonNode request){this.job=job;this.host=host;this.generation=generation;this.session=session;this.action=action;this.call=call;this.tool=tool;this.arguments=arguments;this.requestHash=requestHash;this.request=request.deepCopy();}
        public RunTokenAuthority.ActionRequest actionRequest(){return new RunTokenAuthority.ActionRequest(job.run(),job.step(),action,generation,job.inputHash(),arguments,"TOOL_CALL",tool.version_id(),new PermissionProtocol.Scope(job.tenant(),job.project(),"TASK",job.task()),"plan.write","PLAN_MAINTENANCE");}
        TaskPlanDraftJobs.Job job(){return job;}String actionId(){return action;}String callId(){return call;}String sessionId(){return session;}String requestSha256(){return requestHash;}JsonNode request(){return request.deepCopy();}
    }
    private List<Map<String,Object>> rows(TaskPlanDraftJobs.Job j,String id){return db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",j.tenant(),j.run(),j.step(),id);}
    private Map<String,Object> meta(TaskPlanDraftJobs.Job j,LocalDateTime at,String hash){var m=new LinkedHashMap<String,Object>();m.put("tenant_id",j.tenant());m.put("project_id",j.project());m.put("created_by",j.actor());m.put("created_at",at);m.put("classification",j.classification());m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",hash);return m;}
    private void insert(String table,Map<String,Object> m){var keys=new ArrayList<>(m.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(m::get).toArray())!=1)bad();}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}private static LocalDateTime time(Map<String,Object> r,String k){var v=r.get(k);if(v instanceof LocalDateTime t)return t;if(v instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    private void same(Map<String,Object> expected,Map<String,Object> actual){for(var e:expected.entrySet()){Object v=actual.get(e.getKey());if(v instanceof java.sql.Timestamp t)v=t.toLocalDateTime();if(e.getValue() instanceof Number&&v instanceof Number){if(!e.getValue().toString().equals(v.toString()))bad();}else if(Set.of("payload","resource_ref","requirement_refs","allowed_resolution_actions").contains(e.getKey())&&e.getValue() instanceof String left&&v instanceof String right){try{if(!schema.read(left).equals(schema.read(right)))bad();}catch(Exception failure){bad();}}else if(!Objects.equals(e.getValue(),v))bad();}}
    private static String derived(String a,String b){return UUID.nameUUIDFromBytes((a+"/"+b).getBytes(StandardCharsets.UTF_8)).toString();}private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
