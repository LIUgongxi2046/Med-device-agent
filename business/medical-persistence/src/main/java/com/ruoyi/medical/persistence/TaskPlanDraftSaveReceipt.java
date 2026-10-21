package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** AT10 saves only a candidate and a real human waiting item. No confirmation or TASK launch. */
public final class TaskPlanDraftSaveReceipt {
    private final JdbcTemplate db;private final TaskPlanDraftSchema schema;private final PermissionSchema json;
    private final TaskPlanDraftSaveAction actions;private final SchemaCatalogue shared;
    public TaskPlanDraftSaveReceipt(DataSource data,TaskPlanDraftSchema schema,PermissionSchema json,TaskPlanDraftSaveAction actions)throws Exception{
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.json=json;this.actions=actions;shared=SchemaCatalogue.fromClasspath();
    }
    Saved save(TaskPlanDraftWorkerAccess.Action authenticated,TaskPlanDraftSaveAction.Bound call,TaskPlanDraftIndependentCheck.Checked checked)throws Exception {
        require(authenticated,call,checked);var j=call.job();var prior=rows(j,call);if(!prior.isEmpty())return verify(authenticated,call,checked,one(prior));
        String waiting=derived(call.callId(),"waiting"),audit=derived(call.callId(),"receipt-audit"),event=derived(call.callId(),"receipt-event");var at=now();var candidate=checked.candidate();
        String owner=candidate.produced().authority().current().current().fixedTask().head().ownerId();if(owner==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var item=meta(j,at);item.put("waiting_item_id",waiting);item.put("run_id",j.run());item.put("step_id",j.step());item.put("action_id",call.actionId());item.put("target_revision",candidate.artifactId());item.put("reason_code","CONFIRM_PLAN");
        item.put("question","Review this saved plan candidate, resolve missing items, then confirm or reject the plan.");item.put("requirement_refs",schema.canonical(candidate.candidate().required("requirement_node_ids")));
        item.put("allowed_resolution_actions",schema.canonical(json.tree(List.of("plan.write","plan.confirm"))));item.put("assignee_id",owner);item.put("status","WAITING_INPUT");item.put("resolution_id",null);item.put("revision",1);item.put("updated_at",at);insert("waiting_item",item);
        var receipt=json.object().put("schema_version","1.0.0").put("tenant_id",j.tenant()).put("project_id",j.project()).put("task_id",j.task()).put("task_revision",j.taskRevision()).put("run_id",j.run()).put("actor_id",j.actor())
                .put("step_id",j.step()).put("action_id",call.actionId()).put("lease_generation",authenticated.runtime().leaseGeneration().toString()).put("session_id",call.sessionId()).put("call_id",call.callId())
                .put("input_sha256",j.inputHash()).put("request_sha256",call.requestSha256()).put("artifact_id",candidate.artifactId()).put("candidate_receipt_sha256",candidate.receiptSha256()).put("check_receipt_sha256",checked.receiptSha256())
                .put("waiting_item_id",waiting).put("status","WAITING_INPUT").put("created_at",at.toInstant(ZoneOffset.UTC).toString());
        schema.validate("ToolReceipt",receipt);String raw=schema.canonical(receipt),hash=schema.hash(receipt);
        var log=meta(j,at);log.put("audit_id",audit);log.put("subject_id",j.actor());log.put("action","task.plan_draft.save.completed");log.put("resource_ref",raw);log.put("result","SUCCESS");log.put("request_id",call.callId());log.put("trace_id",j.run());insert("audit_event",log);
        insert("outbox_event",Map.of("tenant_id",j.tenant(),"event_id",event,"aggregate_id",j.run(),"aggregate_revision",1,"event_type","task.plan_draft.save.completed","payload",raw,"occurred_at",at));
        var row=meta(j,at);row.put("task_id",j.task());row.put("run_id",j.run());row.put("session_id",call.sessionId());row.put("call_id",call.callId());row.put("request_sha256",call.requestSha256());row.put("candidate_receipt_sha256",candidate.receiptSha256());row.put("check_receipt_sha256",checked.receiptSha256());row.put("receipt_json",raw);row.put("receipt_sha256",hash);row.put("audit_id",audit);row.put("event_id",event);insert("task_plan_draft_tool_receipt",row);
        return verify(authenticated,call,checked,one(rows(j,call)));
    }
    private void require(TaskPlanDraftWorkerAccess.Action action,TaskPlanDraftSaveAction.Bound call,TaskPlanDraftIndependentCheck.Checked checked)throws Exception {
        action.requireActive();actions.require(call,checked);var token=action.token();var runtime=action.runtime();var j=call.job();
        if(!runtime.job().equals(j)||!token.tenantId().equals(j.tenant())||!token.runId().equals(j.run())||!token.stepId().equals(j.step())||!token.actionId().equals(call.actionId())
                ||!runtime.actionId().equals(call.actionId())||!token.initiatorId().equals(j.actor())||!token.principalId().equals(j.principal())||!token.leaseGeneration().equals(runtime.leaseGeneration()))bad();
        var permission=new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(j.tenant(),j.project(),"TASK",j.task()),"plan.write","PLAN_MAINTENANCE");if(!token.delegatedPermissions().contains(permission))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
    private Saved verify(TaskPlanDraftWorkerAccess.Action action,TaskPlanDraftSaveAction.Bound call,TaskPlanDraftIndependentCheck.Checked checked,Map<String,Object> row)throws Exception {
        require(action,call,checked);var j=call.job();var at=time(row,"created_at");var receipt=schema.validate("ToolReceipt",schema.read((String)row.get("receipt_json")));String raw=schema.canonical(receipt);
        if(!schema.hash(receipt).equals(row.get("receipt_sha256"))||!raw.equals(row.get("receipt_json")))bad();
        var fields=meta(j,at);fields.putAll(Map.of("task_id",j.task(),"run_id",j.run(),"session_id",call.sessionId(),"call_id",call.callId(),"request_sha256",call.requestSha256(),"candidate_receipt_sha256",checked.candidate().receiptSha256(),"check_receipt_sha256",checked.receiptSha256()));same(fields,row);
        var expected=new LinkedHashMap<String,String>();expected.putAll(Map.of("tenant_id",j.tenant(),"project_id",j.project(),"task_id",j.task(),"task_revision",j.taskRevision(),"run_id",j.run(),"actor_id",j.actor(),"step_id",j.step(),"action_id",call.actionId(),"session_id",call.sessionId(),"call_id",call.callId()));
        expected.putAll(Map.of("lease_generation",action.runtime().leaseGeneration().toString(),"input_sha256",j.inputHash(),"request_sha256",call.requestSha256(),"artifact_id",checked.candidate().artifactId(),"candidate_receipt_sha256",checked.candidate().receiptSha256(),"check_receipt_sha256",checked.receiptSha256(),"waiting_item_id",derived(call.callId(),"waiting"),"status","WAITING_INPUT","created_at",at.toInstant(ZoneOffset.UTC).toString()));for(var e:expected.entrySet())if(!e.getValue().equals(receipt.required(e.getKey()).asText()))bad();
        var waiting=one(db.queryForList("SELECT * FROM waiting_item WHERE tenant_id=? AND waiting_item_id=? LIMIT 2 FOR UPDATE",j.tenant(),receipt.required("waiting_item_id").asText()));
        var item=meta(j,at);item.putAll(Map.of("waiting_item_id",receipt.required("waiting_item_id").asText(),"run_id",j.run(),"step_id",j.step(),"action_id",call.actionId(),"target_revision",checked.candidate().artifactId(),"reason_code","CONFIRM_PLAN","status","WAITING_INPUT"));item.put("resolution_id",null);item.put("revision",1);item.put("updated_at",at);
        item.put("question","Review this saved plan candidate, resolve missing items, then confirm or reject the plan.");item.put("requirement_refs",schema.canonical(checked.candidate().candidate().required("requirement_node_ids")));item.put("allowed_resolution_actions",schema.canonical(json.tree(List.of("plan.write","plan.confirm"))));item.put("assignee_id",checked.candidate().produced().authority().current().current().fixedTask().head().ownerId());same(item,waiting);
        var log=meta(j,at);log.putAll(Map.of("audit_id",derived(call.callId(),"receipt-audit"),"subject_id",j.actor(),"action","task.plan_draft.save.completed","resource_ref",raw,"result","SUCCESS","request_id",call.callId(),"trace_id",j.run()));same(log,one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",j.tenant(),row.get("audit_id"))));
        same(Map.of("tenant_id",j.tenant(),"event_id",derived(call.callId(),"receipt-event"),"aggregate_id",j.run(),"aggregate_revision",1,"event_type","task.plan_draft.save.completed","payload",raw,"occurred_at",at),one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",j.tenant(),row.get("event_id"))));
        action.requireActive();return new Saved(receipt,success(call,checked,receipt));
    }
    private JsonNode success(TaskPlanDraftSaveAction.Bound call,TaskPlanDraftIndependentCheck.Checked checked,JsonNode receipt)throws Exception {
        var response=json.object().put("schema_version","1.0.0").put("action_id",call.actionId()).put("tool_id","AT-10").put("status","WAITING_INPUT").put("artifact_id",checked.candidate().artifactId()).putNull("resource_revision").put("summary","Plan candidate saved; human review required.").putNull("next_cursor");
        response.putObject("coverage").put("requested_scope_sha256",call.requestSha256()).put("delivered_scope_sha256",call.requestSha256()).put("complete",true);
        var payload=response.putObject("payload").put("kind","RECEIPT").put("resource_id",checked.candidate().artifactId()).put("resource_kind","PLAN_CANDIDATE").put("resource_state","WAITING_INPUT");payload.putArray("check_ids").add(checked.receipt().required("check_id").asText());shared.validate("tool-success",response,null);return response;
    }
    public static final class Saved {private final JsonNode receipt,response;private Saved(JsonNode receipt,JsonNode response){this.receipt=receipt.deepCopy();this.response=response.deepCopy();}JsonNode receipt(){return receipt.deepCopy();}public JsonNode response(){return response.deepCopy();}}
    private List<Map<String,Object>> rows(TaskPlanDraftJobs.Job j,TaskPlanDraftSaveAction.Bound c){return db.queryForList("SELECT * FROM task_plan_draft_tool_receipt WHERE tenant_id=? AND session_id=? AND call_id=? LIMIT 2 FOR UPDATE",j.tenant(),c.sessionId(),c.callId());}
    private Map<String,Object> meta(TaskPlanDraftJobs.Job j,LocalDateTime at){var m=new LinkedHashMap<String,Object>();m.put("tenant_id",j.tenant());m.put("project_id",j.project());m.put("created_by",j.actor());m.put("created_at",at);m.put("classification",j.classification());m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",schema.manifest());return m;}
    private void insert(String table,Map<String,Object> m){var keys=new ArrayList<>(m.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(m::get).toArray())!=1)bad();}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}private static LocalDateTime time(Map<String,Object> r,String k){var v=r.get(k);if(v instanceof LocalDateTime t)return t;if(v instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    private void same(Map<String,Object> expected,Map<String,Object> actual){for(var e:expected.entrySet()){Object v=actual.get(e.getKey());if(v instanceof java.sql.Timestamp t)v=t.toLocalDateTime();if(e.getValue() instanceof Number&&v instanceof Number){if(!e.getValue().toString().equals(v.toString()))bad();}else if(Set.of("payload","resource_ref","requirement_refs","allowed_resolution_actions").contains(e.getKey())&&e.getValue() instanceof String left&&v instanceof String right){try{if(!schema.read(left).equals(schema.read(right)))bad();}catch(Exception failure){bad();}}else if(!Objects.equals(e.getValue(),v))bad();}}
    private static String derived(String a,String b){return UUID.nameUUIDFromBytes((a+"/"+b).getBytes(StandardCharsets.UTF_8)).toString();}private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
