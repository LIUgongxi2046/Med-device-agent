package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.ruoyi.medical.persistence.TaskRegisteredToolAuthority.*;

/** Actual bounded model-question work, not a completion check, approval, or resumed run.
 * All inserts share the admitted host transaction; replay only checks the original. */
public final class TaskRegisteredToolWaitingService {
    static final String ACTION="task.registered_tool.waiting.created",OPERATION="TASK-REGISTERED-TOOL-WAITING";
    private final JdbcTemplate db;private final TaskResearchRuleInputs inputs;private final PermissionSchema json;
    private final TaskRegisteredToolSchema schema;
    public TaskRegisteredToolWaitingService(DataSource data,TaskResearchRuleInputs inputs,PermissionSchema json)throws Exception {
        this(new JdbcTemplate(data),inputs,json);
    }
    TaskRegisteredToolWaitingService(JdbcTemplate db,TaskResearchRuleInputs inputs,PermissionSchema json)throws Exception {
        this.db=db;db.setQueryTimeout(5);this.inputs=inputs;this.json=json;schema=new TaskRegisteredToolSchema();
    }
    JsonNode create(TaskRegisteredToolAuthority.Checked proof)throws Exception {
        proof.requireUnchanged();current(proof.session().admission());if(!"AT-08".equals(proof.tool()))deny();
        if(!"OPEN".equals(proof.session().state()))deny();
        var session=proof.session();var fixed=inputs.require(session);fixed.requireUnchanged();
        var args=proof.request().required("arguments");validateQuestion(args,session.input().manifest(),fixed.requirementIds());
        if(fixed.ownerId()==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        String waiting=waitingId(session.sessionId(),text(proof.request(),"call_id"));
        var old=db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action=? LIMIT 2 FOR UPDATE",
                session.input().tenantId(),session.input().projectId(),waiting,ACTION);
        if(!old.isEmpty()) {var witness=schema.parse(str(one(old),"resource_ref"));requireCurrent(proof,witness);return witness;}
        var duplicates=db.queryForList("SELECT waiting_item_id FROM waiting_item WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND reason_code='ASK_USER_QUESTION' LIMIT 2 FOR UPDATE",
                session.input().tenantId(),session.input().projectId(),session.runId(),session.stepId());
        if(!duplicates.isEmpty())throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
        var cause=cause(proof,fixed.ownerId(),fixed.requirementIds());
        var value=json.object().put("schema_version","1.0.0").put("kind","TASK_TOOL_WAITING_ITEM")
                .put("waiting_item_id",waiting).put("operation_id",derived(waiting,"operation"))
                .put("audit_id",waiting).put("event_id",derived(waiting,"event"))
                .put("created_at",now.toInstant(ZoneOffset.UTC).toString()).put("status","WAITING_INPUT")
                .put("revision","1").put("cause_sha256",hash(cause));
        value.set("cause",cause);schema.validate("WaitingItem",value);
        var row=metadata(proof,now);row.put("waiting_item_id",waiting);row.put("run_id",session.runId());row.put("step_id",session.stepId());
        row.put("action_id",proof.actionId());row.put("target_revision",session.input().inputId());row.put("reason_code","ASK_USER_QUESTION");
        row.put("question",text(args,"prompt_text"));row.put("requirement_refs",json.canonical(args.required("requirement_ids")));
        row.put("allowed_resolution_actions",json.canonical(json.tree(List.of("work_item.resolve"))));row.put("assignee_id",fixed.ownerId());
        row.put("status","WAITING_INPUT");row.put("resolution_id",null);row.put("revision","1");row.put("updated_at",now);insert("waiting_item",row);
        var operation=metadata(proof,now);operation.remove("project_id");operation.put("operation_id",text(value,"operation_id"));
        operation.put("subject_id",session.admission().actorId());operation.put("operation_name",OPERATION);operation.put("idempotency_key",proof.actionId());
        operation.put("request_sha256",hash(cause));operation.put("state","COMPLETED");operation.put("http_status",200);
        operation.put("response_ref",json.canonical(value));operation.put("target_id",waiting);operation.put("error_code",null);
        operation.put("revision","1");operation.put("updated_at",now);insert("operation_record",operation);
        var audit=metadata(proof,now);audit.put("audit_id",waiting);audit.put("subject_id",session.admission().actorId());audit.put("action",ACTION);
        audit.put("resource_ref",json.canonical(value));audit.put("result","SUCCESS");audit.put("reason_code",null);
        audit.put("request_id",text(value,"operation_id"));audit.put("trace_id",session.runId());insert("audit_event",audit);
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",
                session.input().tenantId(),text(value,"event_id"),waiting,ACTION,json.canonical(value),now));
        fixed.requireUnchanged();requireCurrent(proof,value);return value;
    }
    void requireCurrent(TaskRegisteredToolAuthority.Checked proof,JsonNode witness)throws Exception {
        proof.requireUnchanged();current(proof.session().admission());var fixed=inputs.require(proof.session());
        if(fixed.ownerId()==null)deny();validateQuestion(proof.request().required("arguments"),proof.session().input().manifest(),fixed.requirementIds());
        same(cause(proof,fixed.ownerId(),fixed.requirementIds()),witness.required("cause"));
        requireOriginal(proof.session().admission(),witness);
        var i=proof.session().input();one(db.queryForList("SELECT waiting_item_id FROM waiting_item WHERE tenant_id=? AND project_id=? AND waiting_item_id=? AND status='WAITING_INPUT' AND revision=1 AND resolution_id IS NULL LIMIT 2 FOR UPDATE",i.tenantId(),i.projectId(),text(witness,"waiting_item_id")));
        fixed.requireUnchanged();proof.requireUnchanged();
    }
    /** Integrity-only recovery; it creates no waiting item, grant, session, or continuation. */
    void requireOriginal(EnterpriseAccess.Admission a,JsonNode witness)throws Exception {
        current(a);requireCreationIdentity(a.context().tenant_id(),a.actorId(),witness);
    }
    /** Integrity only. Caller must already hold its specific private worker or human
     * question authority. This method grants no read or execution permission. */
    void requireCreationIdentity(String tenant,String actor,JsonNode witness)throws Exception {
schema.validate("WaitingItem",witness);var cause=witness.required("cause");
        same(tenant,text(cause,"tenant_id"));same(actor,text(cause,"created_by"));
        String waiting=text(witness,"waiting_item_id");same(waitingId(text(cause,"session_id"),text(cause.required("request"),"call_id")),waiting);
        same(hash(cause),text(witness,"cause_sha256"));same(waiting,text(witness,"audit_id"));
        same(derived(waiting,"operation"),text(witness,"operation_id"));same(derived(waiting,"event"),text(witness,"event_id"));
        var row=one(db.queryForList("SELECT * FROM waiting_item WHERE tenant_id=? AND waiting_item_id=? LIMIT 2 FOR UPDATE",tenant,waiting));
        for(String f:List.of("tenant_id","project_id","run_id","step_id","action_id","assignee_id","classification","created_by"))same(text(cause,f),str(row,f));
        same(text(cause,"input_id"),str(row,"target_revision"));same("ASK_USER_QUESTION",str(row,"reason_code"));
        same(text(cause.required("request").required("arguments"),"prompt_text"),str(row,"question"));
        same(cause.required("request").required("arguments").required("requirement_ids"),schema.parse(str(row,"requirement_refs")));
        same(json.tree(List.of("work_item.resolve")),schema.parse(str(row,"allowed_resolution_actions")));
        // Mutable work resolution is not part of the immutable original creation receipt.
        same("WAITING_INPUT",text(witness,"status"));same("1",text(witness,"revision"));
        same("1.0.0",str(row,"schema_version"));same(schema.manifest(),str(row,"schema_manifest_sha256"));
        same(Instant.parse(text(witness,"created_at")),time(row,"created_at").toInstant(ZoneOffset.UTC));
        var operation=one(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? AND operation_name=? AND target_id=? LIMIT 2 FOR UPDATE",
                tenant,text(witness,"operation_id"),OPERATION,waiting));
        same(actor,str(operation,"subject_id"));same(actor,str(operation,"created_by"));same(text(cause,"action_id"),str(operation,"idempotency_key"));
        same(hash(cause),str(operation,"request_sha256"));same("COMPLETED",str(operation,"state"));same("200",str(operation,"http_status"));
        same(witness,schema.parse(str(operation,"response_ref")));same(schema.manifest(),str(operation,"schema_manifest_sha256"));
        same("1.0.0",str(operation,"schema_version"));same(text(cause,"classification"),str(operation,"classification"));same("1",str(operation,"revision"));
        if(operation.get("error_code")!=null)bad();same(time(row,"created_at"),time(operation,"created_at"));same(time(operation,"created_at"),time(operation,"updated_at"));
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action=? LIMIT 2 FOR UPDATE",
                tenant,text(cause,"project_id"),waiting,ACTION));
        same(witness,schema.parse(str(audit,"resource_ref")));same(actor,str(audit,"subject_id"));same(actor,str(audit,"created_by"));
        same(text(witness,"operation_id"),str(audit,"request_id"));same(text(cause,"run_id"),str(audit,"trace_id"));same("SUCCESS",str(audit,"result"));
        same(schema.manifest(),str(audit,"schema_manifest_sha256"));same("1.0.0",str(audit,"schema_version"));same(text(cause,"classification"),str(audit,"classification"));
        if(audit.get("reason_code")!=null)bad();same(time(row,"created_at"),time(audit,"created_at"));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type=? LIMIT 2",
                tenant,text(witness,"event_id"),waiting,ACTION));
        same(witness,schema.parse(str(event,"payload")));same(time(row,"created_at"),time(event,"occurred_at"));
    }
    private ObjectNode cause(TaskRegisteredToolAuthority.Checked proof,String owner,List<String> requirements)throws Exception {
        var s=proof.session();var input=s.input();var value=json.object().put("tenant_id",input.tenantId()).put("project_id",input.projectId())
                .put("run_id",s.runId()).put("step_id",s.stepId()).put("action_id",proof.actionId()).put("session_id",s.sessionId())
                .put("input_id",input.inputId()).put("input_sha256",input.inputSha256()).put("input_receipt_sha256",input.receiptSha256())
                .put("assignee_id",owner).put("created_by",s.admission().actorId()).put("classification",proof.classification());
        value.set("binding",s.binding());value.set("node_input",input.manifest());value.set("request",proof.request());
        value.set("authority",proof.canonicalPins());value.set("fixed_requirement_ids",json.tree(requirements));return value;
    }
    static void validateQuestion(JsonNode args,JsonNode input,List<String> requirements) {
        if(!Set.of("NEED_EVIDENCE","CLARIFY_APPLICABILITY").contains(text(args,"question_type"))
                ||!args.required("evidence_refs").isEmpty())deny();
        if(text(args,"prompt_text").isBlank()||text(args,"prompt_text").getBytes(StandardCharsets.UTF_8).length>4096)
            throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var sections=args.required("target_section_ids");if(sections.size()!=1||!text(input,"section_id").equals(sections.get(0).textValue()))deny();
        var selected=args.required("requirement_ids");if(selected.size()>64)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var seen=new HashSet<String>();for(var requirement:selected)if(!requirement.isTextual()||!requirements.contains(requirement.textValue())||!seen.add(requirement.textValue()))deny();
    }
    private Map<String,Object> metadata(TaskRegisteredToolAuthority.Checked p,LocalDateTime now){
        var i=p.session().input();var row=new LinkedHashMap<String,Object>();row.put("tenant_id",i.tenantId());row.put("project_id",i.projectId());
        row.put("schema_version","1.0.0");row.put("schema_manifest_sha256",schema.manifest());row.put("created_by",p.session().admission().actorId());
        row.put("created_at",now);row.put("classification",p.classification());return row;
    }
    private void insert(String table,Map<String,Object> row){changed(db.update("INSERT INTO "+table+"("+String.join(",",row.keySet())+") VALUES("+String.join(",",Collections.nCopies(row.size(),"?"))+")",row.values().toArray()));}
    static String waitingId(String session,String call){return id("TASK_REGISTERED_TOOL_WAITING",session,call);}
    static String derived(String waiting,String suffix){return UUID.nameUUIDFromBytes((waiting+"|"+suffix).getBytes(StandardCharsets.UTF_8)).toString();}
    private static void current(EnterpriseAccess.Admission a){if(!TransactionSynchronizationManager.isActualTransactionActive()||a.simulationWorker()||!(a.formalWorker()||a.controlWorker()||a.recoveryWorker()))deny();if(a.formalWorker())a.formalProof().requireActive();else if(a.controlWorker())a.controlProof().requireActive();else a.recoveryProof().requireActive();}
    private static LocalDateTime time(Map<String,Object> row,String field){var value=row.get(field);if(value instanceof java.sql.Timestamp v)return v.toLocalDateTime();if(value instanceof LocalDateTime v)return v;bad();return null;}
    private static void changed(int rows){if(rows!=1)bad();}
}
