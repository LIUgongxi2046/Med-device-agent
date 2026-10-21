package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.time.ZoneOffset;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.TaskRegisteredToolAuthority.*;

/** One atomic answer record + independently proven new-run link + STALE CAS.
 * An answer never becomes an approved fact or a signal to resume the old run. */
public final class TaskRegisteredToolWaitingAnswerService {
 static final String ACTION="task.registered_tool.waiting.answer.linked";
 private final EnterpriseAccess access;private final TaskRegisteredToolWaitingAnswerAuthority authority;private final TaskRegisteredToolWaitingNewRun links;private final TaskRunWaitingCommitService commits;private final PermissionSchema json;private final JdbcTemplate db;private final TaskRunActivityJournal journal;private final TaskRegisteredToolWaitingAnswerSchema schema;
 public TaskRegisteredToolWaitingAnswerService(DataSource data,EnterpriseAccess access,TaskRegisteredToolWaitingAnswerAuthority authority,TaskRegisteredToolWaitingNewRun links,TaskRunWaitingCommitService commits,PermissionSchema json)throws Exception{this(new JdbcTemplate(data),access,authority,links,commits,json);}
 TaskRegisteredToolWaitingAnswerService(JdbcTemplate db,EnterpriseAccess access,TaskRegisteredToolWaitingAnswerAuthority authority,TaskRegisteredToolWaitingNewRun links,TaskRunWaitingCommitService commits,PermissionSchema json)throws Exception{this.db=db;db.setQueryTimeout(5);this.access=access;this.authority=authority;this.links=links;this.commits=commits;this.json=json;journal=new TaskRunActivityJournal(db,json);schema=new TaskRegisteredToolWaitingAnswerSchema();}
 public JsonNode read(ApplicationSessionIdentity.Verified user,String context,String waiting){
    WireValues.uuid(waiting);return access.execute(user,a->ThrowingSupplier.of(()->{
        var q=authority.require(a,context,waiting);var row=row(q);var original=authority.original(q);
        commits.requireAnswerOriginal(original,original.locator());JsonNode continuation=json.tree(null);
        if("STALE".equals(str(row,"status"))){
            var saved=one(db.queryForList("SELECT response_ref,idempotency_key FROM operation_record WHERE tenant_id=? AND operation_id=? AND operation_name=? AND subject_id=? LIMIT 2 FOR UPDATE",q.tenantId(),str(row,"resolution_id"),ACTION,a.actorId()));
            var witness=new com.fasterxml.jackson.databind.json.JsonMapper().readTree(str(saved,"response_ref"));
            var request=schema.validate("Request",witness.required("request"));
            answer(a,context,waiting,str(saved,"idempotency_key"),request,true);
            continuation=links.continuation(q,request);
        }else if(!"WAITING_INPUT".equals(str(row,"status")))bad();
        var c=q.creation().required("cause");
        var v=json.object().put("schema_version","1.0.0").put("waiting_item_id",waiting).put("status",str(row,"status"))
                .put("revision",str(row,"revision")).put("cause_sha256",text(q.creation(),"cause_sha256"))
                .put("can_resume_frozen_run",false).put("answer_is_approved_fact",false)
                .put("required_decision","LINK_CONFIRMED_REVISION_AND_NEW_RUN");
        v.set("resolution_id",json.tree(row.get("resolution_id")));v.set("question",c.required("request").required("arguments"));
        for(String f:List.of("project_id","run_id","step_id","assignee_id"))v.set(f,c.required(f));
        v.put("task_id",q.taskId());v.set("continuation",continuation);q.requireUnchanged();return v;
    }).get());
 }
 public JsonNode answer(ApplicationSessionIdentity.Verified user,String context,String waiting,String key,byte[] bytes)throws Exception{WireValues.uuid(waiting);json.headers(context,key);var request=schema.read("Request",bytes);same(waiting,text(request,"waiting_item_id"));return access.execute(user,a->ThrowingSupplier.of(()->answer(a,context,waiting,key,request,false)).get());}
 public JsonNode recover(ApplicationSessionIdentity.Verified user,String context,String waiting,byte[] bytes)throws Exception{WireValues.uuid(waiting);var r=schema.read("Recovery",bytes);String key=text(r,"original_idempotency_key");json.headers(context,key);same(waiting,text(r.required("original_request"),"waiting_item_id"));return access.execute(user,a->ThrowingSupplier.of(()->answer(a,context,waiting,key,r.required("original_request"),true)).get());}
 private JsonNode answer(EnterpriseAccess.Admission a,String context,String waiting,String key,JsonNode request,boolean recovery)throws Exception{
    if(text(request,"answer_text").isBlank()||text(request,"reason").isBlank())throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);var q=authority.require(a,context,waiting);var original=authority.original(q);var committed=commits.requireAnswerOriginal(original,original.locator());same(text(q.creation(),"cause_sha256"),text(request,"cause_sha256"));
    var existing=db.queryForList("SELECT operation_id,request_sha256 FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? LIMIT 2 FOR UPDATE",q.tenantId(),a.actorId(),ACTION,key);
    if(!existing.isEmpty()){var op=one(existing);if(!hash(request).equals(str(op,"request_sha256")))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);var witness=journal.read(a,q.projectId(),q.runId(),str(op,"operation_id"),ACTION,key,waiting,hash(request),q.classification(),"1.0.0",schema.manifest()).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));ProfessionalRuntimeObservations.closed(witness,Set.of("schema_version","kind","request","receipt","original_waiting_commit","new_run_link","current_grants","auth_epoch","governance_epoch","identity_mode"));same("1.0.0",text(witness,"schema_version"));same("TASK_QUESTION_ANSWER_LINK",text(witness,"kind"));same(request,witness.required("request"));same(committed,witness.required("original_waiting_commit"));same(links.require(q,request),witness.required("new_run_link"));var receipt=schema.validate("Receipt",witness.required("receipt"));same(str(op,"operation_id"),text(receipt,"decision_id"));verifyReceipt(q,request,receipt);var row=row(q);same("STALE",str(row,"status"));same("2",str(row,"revision"));same(text(receipt,"decision_id"),str(row,"resolution_id"));same(java.time.Instant.parse(text(receipt,"created_at")),TaskRunActivityJournal.time(row,"updated_at").toInstant(ZoneOffset.UTC));one(db.queryForList("SELECT version_id FROM version_registry WHERE tenant_id=? AND owner_project_id=? AND version_id=? AND entity_kind='TASK_QUESTION_ANSWER_LINK' AND content_sha256=? LIMIT 2 FOR UPDATE",q.tenantId(),q.projectId(),text(receipt,"decision_id"),hash(witness)));WireValues.revision(text(witness,"auth_epoch"));WireValues.revision(text(witness,"governance_epoch"));if(!Set.of("local","oidc").contains(text(witness,"identity_mode"))||!witness.required("current_grants").isObject()||witness.required("current_grants").isEmpty())bad();q.requireUnchanged();return receipt;}
    if(recovery)throw new TenantAccessException(ErrorCode.SOURCE_MISSING);var row=row(q);if(!"WAITING_INPUT".equals(str(row,"status"))||!text(request,"expected_revision").equals(str(row,"revision"))||row.get("resolution_id")!=null)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
    var link=links.require(q,request);String id=UUID.randomUUID().toString();var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));var receipt=json.object().put("schema_version","1.0.0").put("waiting_item_id",waiting).put("decision_id",id).put("original_run_id",q.runId()).put("target_run_id",text(request.required("target_run"),"run_id")).put("request_sha256",hash(request)).put("cause_sha256",text(request,"cause_sha256")).put("status","STALE").put("revision","2").put("created_by",a.actorId()).put("created_at",now.toInstant(ZoneOffset.UTC).toString()).put("can_resume_frozen_run",false).put("answer_is_approved_fact",false).put("human_checkpoint_approved",false);verifyReceipt(q,request,receipt);
    var witness=json.object().put("schema_version","1.0.0").put("kind","TASK_QUESTION_ANSWER_LINK").put("auth_epoch",a.watermarks().authEpoch().toString()).put("governance_epoch",a.watermarks().governanceEpoch().toString()).put("identity_mode",a.bindingView().mode());witness.set("request",request);witness.set("receipt",receipt);witness.set("original_waiting_commit",committed);witness.set("new_run_link",link);witness.set("current_grants",q.grantPins());
    journal.write(a,q.projectId(),q.runId(),id,ACTION,key,waiting,hash(request),q.classification(),"1.0.0",schema.manifest(),witness);TaskRunActivityJournal.changed(db.update("INSERT INTO version_registry(tenant_id,owner_project_id,version_id,entity_kind,content_sha256) VALUES(?,?,?,?,?)",q.tenantId(),q.projectId(),id,"TASK_QUESTION_ANSWER_LINK",hash(witness)));
    TaskRunActivityJournal.changed(db.update("UPDATE waiting_item SET status='STALE',resolution_id=?,revision=2,updated_at=? WHERE tenant_id=? AND project_id=? AND waiting_item_id=? AND status='WAITING_INPUT' AND revision=1 AND resolution_id IS NULL",id,now,q.tenantId(),q.projectId(),waiting));q.requireUnchanged();return receipt;
 }
 private void verifyReceipt(TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion q,JsonNode request,JsonNode receipt)throws Exception{schema.validate("Receipt",receipt);same(q.waitingItemId(),text(receipt,"waiting_item_id"));same(q.runId(),text(receipt,"original_run_id"));same(text(request.required("target_run"),"run_id"),text(receipt,"target_run_id"));same(hash(request),text(receipt,"request_sha256"));same(text(q.creation(),"cause_sha256"),text(receipt,"cause_sha256"));same(q.admission().actorId(),text(receipt,"created_by"));}
 private Map<String,Object> row(TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion q){return one(db.queryForList("SELECT * FROM waiting_item WHERE tenant_id=? AND project_id=? AND waiting_item_id=? LIMIT 2 FOR UPDATE",q.tenantId(),q.projectId(),q.waitingItemId()));}
}
