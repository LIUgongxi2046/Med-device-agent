package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** RN-05 is the only producer of successor authority. Terminal evidence is not
 * inferred from a lease, and an UNKNOWN invocation is never redispatched. */
@Service
public final class RunContinuationService {
    private final EnterpriseAccess access;private final ResourceReadAuthority resources;private final TaskService tasks;
    private final RunCreateService creation;private final PermissionSchema json;private final IdentityDirectoryPolicy identity;private final JdbcTemplate db;
    private final JsonMapper reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final String manifest;private final com.networknt.schema.JsonSchema contract;
    public RunContinuationService(EnterpriseAccess access,ResourceReadAuthority resources,TaskService tasks,RunCreateService creation,
            PermissionSchema json,IdentityDirectoryPolicy identity,@Qualifier("masterDataSource")DataSource data)throws Exception {
        this.access=access;this.resources=resources;this.tasks=tasks;this.creation=creation;this.json=json;this.identity=identity;db=new JdbcTemplate(data);db.setQueryTimeout(5);
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/run-continuation.schema.json"))){byte[] bytes=in.readAllBytes();manifest=WireValues.sha256(bytes);
            contract=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());contract.initializeValidators();}
    }
    private org.springframework.beans.factory.ObjectProvider<com.ruoyi.medical.workflow.WaitingWorkflowDeploymentV2> reuseDeployment;
    private boolean reuseIngress;
    @org.springframework.beans.factory.annotation.Autowired void reuseDeployment(org.springframework.beans.factory.ObjectProvider<com.ruoyi.medical.workflow.WaitingWorkflowDeploymentV2> deployment,@org.springframework.beans.factory.annotation.Value("${medical.task-run.waiting-v2.server-enabled:false}") boolean ingress){reuseDeployment=deployment;reuseIngress=ingress;}
    static final class Authority {
        private final RunContinuationService owner;private final String actor,context,parent,key,operation,attempt;
        private final TaskService.VerifiedTask task;private final JsonNode input,originalReplay;private final RunCreateSchema.Create request;
        private Authority(RunContinuationService owner,EnterpriseAccess.Admission a,TaskService.VerifiedTask task,String parent,String key,JsonNode input,JsonNode replay,String attempt){
            this.owner=owner;actor=a.actorId();context=a.context().context_revision();this.task=task;this.parent=parent;this.key=key;this.input=input.deepCopy();originalReplay=replay.deepCopy();
            this.attempt=attempt;operation=UUID.randomUUID().toString();request=new RunCreateSchema.Create("1.0.0",input.required("task_revision").asText(),input.required("plan_revision").asText(),input.required("snapshot_id").asText(),input.required("budget_revision").asText(),input.required("expected_revision").asText());
        }
        void requirePrepared(TaskService.VerifiedTask actual,RunCreateSchema.Create r){if((!task.head().equals(actual.head())||!task.input().equals(actual.input()))||!request.equals(r))fail(ErrorCode.PLAN_STALE);}
        void requireCurrent(EnterpriseAccess.Admission a,TaskService.VerifiedTask actual,RunCreateSchema.Create r)throws IOException {
            requirePrepared(actual,r);same(actor,a.actorId());same(context,a.context().context_revision());
            try {owner.require(a,parent,input,actual);same(originalReplay,owner.replay(a,parent));}catch(IOException e){throw e;}catch(RuntimeException e){throw e;}catch(Exception e){throw new IOException("CONTINUATION_AUTHORITY_RECHECK_FAILED",e);}
        }
        void requireReplay(JsonNode replay){
            String mode=input.required("mode").asText();
            if(!"RERUN_UPDATED".equals(mode)){
                for(String field:List.of("agent_release_id","task_input","model_bindings","execution_policy_version","execution_policy","project_profile","template","budget_approval"))same(originalReplay.required(field),replay.required(field));
                // An exact model revision and reported version are both mandatory. No alias fallback.
                for(var binding:replay.required("model_bindings"))if("UNKNOWN".equals(binding.required("model_version").asText()))fail(ErrorCode.MODEL_VERSION_UNAVAILABLE);
                same(originalReplay.required("scope_snapshot").required("members"),replay.required("scope_snapshot").required("members"));
                same(originalReplay.required("plan").required("content"),replay.required("plan").required("content"));
            }
        }
        String taskId(){return task.head().taskId();}String parentRunId(){return parent;}String attempt(){return attempt;}RunCreateSchema.Create request(){return request;}
        void record(EnterpriseAccess.Admission a,JsonNode response,JsonNode replay)throws IOException {
            try{owner.record(a,this,response,replay);}catch(IOException e){throw e;}catch(RuntimeException e){throw e;}catch(Exception e){throw new IOException("CONTINUATION_WRITE_FAILED",e);}
        }
    }
    public JsonNode continueRun(ApplicationSessionIdentity.Verified subject,String context,String key,String parent,byte[] body){
        json.headers(context,key);WireValues.uuid(parent);
        var input=ThrowingSupplier.of(()->{var n=reader.readTree(body);if(n==null||!contract.validate(n).isEmpty())fail(ErrorCode.SCHEMA_INVALID);return n;}).get();
        var prepared=access.execute(subject,a->ThrowingSupplier.of(()->{
            current(a,context);var run=run(a,parent);String task=text(run,"task_id");var fixed=tasks.fixedTask(a,task);grant(a,fixed.head());
            var old=original(a,parent,key,hash(input));if(old!=null)return new Preparation(old,null);
            require(a,parent,input,fixed);var replay=replay(a,parent);
            var count=one(db.query("SELECT COALESCE(MAX(attempt_no),0)+1 FROM task_run WHERE tenant_id=? AND project_id=? AND task_id=? AND task_revision=? FOR UPDATE",(r,n)->r.getBigDecimal(1).toBigIntegerExact().toString(),a.context().tenant_id(),fixed.head().projectId(),task,input.required("task_revision").asText()));
            return new Preparation(null,new Authority(this,a,fixed,parent,key,input,replay,count));
        }).get());
        if(prepared.original()!=null)return prepared.original();
        return creation.continueRun(subject,context,key,prepared.authority());
    }
    private record Preparation(JsonNode original,Authority authority){}
    public JsonNode original(ApplicationSessionIdentity.Verified subject,String context,String key,String parent){
        json.headers(context,key);WireValues.uuid(parent);return access.execute(subject,a->ThrowingSupplier.of(()->{
            current(a,context);var row=run(a,parent);grant(a,tasks.fixedTask(a,text(row,"task_id")).head());var saved=original(a,parent,key,null);
            return saved==null?json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),null,json.tree(null))):saved;
        }).get());
    }
    private void require(EnterpriseAccess.Admission a,String parent,JsonNode input,TaskService.VerifiedTask fixed)throws Exception {
        var old=run(a,parent);var head=fixed.head();same(head.taskId(),text(old,"task_id"));same(head.projectId(),text(old,"project_id"));grant(a,head);
        if(!Set.of("SUCCEEDED","FAILED","CANCELLED").contains(text(old,"status"))||old.get("ended_at")==null||old.get("dispatch_closed_at")==null)fail(ErrorCode.CANCELLATION_UNCONFIRMED);
        if(old.get("superseded_by_run_id")!=null)fail(ErrorCode.REVISION_CONFLICT);
        if(!db.queryForList("SELECT run_id FROM task_active_run WHERE tenant_id=? AND project_id=? AND task_id=? LIMIT 1 FOR UPDATE",a.context().tenant_id(),head.projectId(),head.taskId()).isEmpty())fail(ErrorCode.STEP_NOT_READY);
        FormalInvocationCancellation.requireQuiescentTask(db,a.context().tenant_id(),head.projectId(),head.taskId(),ErrorCode.INVOCATION_UNKNOWN);
        same(head.revision().toString(),input.required("expected_revision").asText());same(head.taskRevision(),input.required("task_revision").asText());same(head.planRevision(),input.required("plan_revision").asText());same(head.snapshotId(),input.required("snapshot_id").asText());
        String mode=input.required("mode").asText();var replay=replay(a,parent);
        if("RESUME".equals(mode)){
            same(text(old,"task_revision"),head.taskRevision());same(replay.required("plan_revision").asText(),head.planRevision());same(replay.required("snapshot_id").asText(),head.snapshotId());
            if(!Set.of("FAILED","CANCELLED").contains(head.status()))fail(ErrorCode.PLAN_STALE);
            // A saved success must be imported through its original-only producer,
            // never converted into permission to call the provider a second time.
            if(!db.queryForList("SELECT step_id FROM run_step WHERE tenant_id=? AND run_id=? AND status='SUCCEEDED' LIMIT 1 FOR UPDATE",a.context().tenant_id(),parent).isEmpty()&&(reuseDeployment==null||reuseDeployment.getIfAvailable()==null||!reuseIngress))fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }else{
            RunCreateSchema.requireReady(head,new RunCreateSchema.Create("1.0.0",head.taskRevision(),head.planRevision(),head.snapshotId(),input.required("budget_revision").asText(),head.revision().toString()));
            if(text(old,"task_revision").equals(head.taskRevision()))fail(ErrorCode.PLAN_STALE);
            requireRevisionLineage(a,head,input.required("input_decision_id").asText(),text(old,"task_revision"));
        }
        if(!"RERUN_UPDATED".equals(mode))same(text(old,"budget_revision"),input.required("budget_revision").asText());
        // A new budget revision may raise the same account's ceiling; it cannot reset prior charges.
        var accounts=db.queryForList("SELECT budget_id FROM budget_revision WHERE tenant_id=? AND budget_revision IN (?,?) FOR UPDATE",a.context().tenant_id(),text(old,"budget_revision"),input.required("budget_revision").asText());
        if(accounts.isEmpty()||accounts.stream().map(r->text(r,"budget_id")).distinct().count()!=1)fail(ErrorCode.BUDGET_EXCEEDED);
    }
    private void requireRevisionLineage(EnterpriseAccess.Admission a,TaskProtocol.Head head,String operation,String basis)throws Exception {
        WireValues.uuid(operation);var row=one(db.queryForList("SELECT * FROM task_input_continuation WHERE tenant_id=? AND project_id=? AND task_id=? AND operation_id=? AND operation_name='TK-06' LIMIT 2 FOR UPDATE",a.context().tenant_id(),head.projectId(),head.taskId(),operation));
        same(basis,text(row,"basis_task_revision"));var receipt=reader.readTree(text(row,"receipt_json"));same(hash(receipt),text(row,"receipt_sha256"));same(operation,receipt.required("operation_id").asText());
        same(text(row,"resulting_task_revision"),receipt.required("resulting_task_revision").asText());
        String cursor=head.taskRevision();var seen=new HashSet<String>();boolean found=false;
        while(cursor!=null&&seen.add(cursor)&&seen.size()<=128){if(cursor.equals(text(row,"resulting_task_revision"))){found=true;break;}
            var revision=one(db.queryForList("SELECT parent_revision FROM task_revision WHERE tenant_id=? AND project_id=? AND task_id=? AND task_revision=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),head.projectId(),head.taskId(),cursor));cursor=(String)revision.get("parent_revision");}
        if(!found)fail(ErrorCode.LINEAGE_REVIEW_REQUIRED);
        var decision=one(db.queryForList("SELECT decision,resulting_task_revision,resulting_plan_revision FROM task_plan_decision WHERE tenant_id=? AND project_id=? AND task_id=? AND resulting_task_revision=? AND resulting_plan_revision=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),head.projectId(),head.taskId(),head.taskRevision(),head.planRevision()));same("CONFIRM",text(decision,"decision"));
    }
    private void record(EnterpriseAccess.Admission a,Authority p,JsonNode response,JsonNode replay)throws Exception {
        String tenant=a.context().tenant_id(),run=response.required("data").required("run_id").asText(),project=p.task.head().projectId();
        var receipt=json.object().put("schema_version","1.0.0").put("operation_id",p.operation).put("parent_run_id",p.parent).put("run_id",run).put("task_id",p.taskId()).put("mode",p.input.required("mode").asText()).put("original_replay_sha256",hash(p.originalReplay)).put("replay_sha256",hash(replay));
        receipt.set("request",p.input);receipt.set("created_run",response);String saved=json.canonical(receipt);
        changed(db.update("INSERT INTO operation_record(tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,'RN-05',?,?,'COMPLETED',202,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6))",tenant,p.operation,a.actorId(),p.key,hash(p.input),json.canonical(response),p.parent,manifest,a.actorId(),identity.classification()));
        changed(db.update("INSERT INTO task_run_continuation(tenant_id,project_id,task_id,parent_run_id,run_id,operation_id,mode,receipt_json,receipt_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)",tenant,project,p.taskId(),p.parent,run,p.operation,p.input.required("mode").asText(),saved,hash(receipt),manifest,a.actorId(),identity.classification()));
        changed(db.update("UPDATE task_run SET superseded_by_run_id=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND project_id=? AND run_id=? AND superseded_by_run_id IS NULL AND status IN ('SUCCEEDED','FAILED','CANCELLED') AND ended_at IS NOT NULL AND dispatch_closed_at IS NOT NULL",run,tenant,project,p.parent));
        changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'run.continuation.accepted',?,'SUCCESS',?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)",tenant,project,p.operation,a.actorId(),saved,p.operation,p.parent,manifest,a.actorId(),identity.classification()));
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,'run.continuation.accepted',?,UTC_TIMESTAMP(6))",tenant,p.operation,run,saved));
        requireSuccessor(db,a,run);
    }
    private JsonNode original(EnterpriseAccess.Admission a,String parent,String key,String expected)throws Exception {
        var rows=db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name='RN-05' AND idempotency_key=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),a.actorId(),key);if(rows.isEmpty())return null;
        var row=one(rows);same(parent,text(row,"target_id"));same("COMPLETED",text(row,"state"));same(manifest,text(row,"schema_manifest_sha256"));if(expected!=null&&!expected.equals(text(row,"request_sha256")))fail(ErrorCode.IDEMPOTENCY_CONFLICT);
        var response=reader.readTree(text(row,"response_ref"));requireSuccessor(db,a,response.required("data").required("run_id").asText());
        var link=one(db.queryForList("SELECT receipt_json FROM task_run_continuation WHERE tenant_id=? AND operation_id=? LIMIT 2",a.context().tenant_id(),text(row,"operation_id")));var receipt=reader.readTree(text(link,"receipt_json"));same(response,receipt.required("created_run"));same(text(row,"request_sha256"),hash(receipt.required("request")));return response;
    }
    /** Invoked at real launch, accepted-start, dispatch and runtime gates. This
     * permits only an immutable genuine RN-05 successor, not any parent-run row. */
    static void requireSuccessor(JdbcTemplate db,EnterpriseAccess.Admission a,String run)throws Exception {
        var row=one(db.queryForList("SELECT project_id,task_id,parent_run_id,replay_manifest FROM task_run WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),run));if(row.get("parent_run_id")==null)return;
        var link=one(db.queryForList("SELECT * FROM task_run_continuation WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND parent_run_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),text(row,"project_id"),text(row,"task_id"),run,text(row,"parent_run_id")));
        var parser=new JsonMapper();var receipt=parser.readTree(text(link,"receipt_json"));same(WireValues.sha256(ContentReadAuthorization.canonicalBytes(receipt)),text(link,"receipt_sha256"));same(run,receipt.required("run_id").asText());same(text(row,"parent_run_id"),receipt.required("parent_run_id").asText());same(WireValues.sha256(ContentReadAuthorization.canonicalBytes(parser.readTree(text(row,"replay_manifest")))),receipt.required("replay_sha256").asText());
        var parent=one(db.queryForList("SELECT status,ended_at,dispatch_closed_at,superseded_by_run_id,replay_manifest FROM task_run WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),text(row,"project_id"),text(row,"task_id"),text(row,"parent_run_id")));
        if(!Set.of("SUCCEEDED","FAILED","CANCELLED").contains(text(parent,"status"))||parent.get("ended_at")==null||parent.get("dispatch_closed_at")==null)fail(ErrorCode.CANCELLATION_UNCONFIRMED);same(run,text(parent,"superseded_by_run_id"));same(WireValues.sha256(ContentReadAuthorization.canonicalBytes(parser.readTree(text(parent,"replay_manifest")))),receipt.required("original_replay_sha256").asText());
        var operation=one(db.queryForList("SELECT request_sha256,response_ref,subject_id FROM operation_record WHERE tenant_id=? AND operation_id=? AND operation_name='RN-05' AND target_id=? AND state='COMPLETED' LIMIT 2",a.context().tenant_id(),text(link,"operation_id"),text(row,"parent_run_id")));
        same(text(link,"created_by"),text(operation,"subject_id"));same(WireValues.sha256(ContentReadAuthorization.canonicalBytes(receipt.required("request"))),text(operation,"request_sha256"));same(receipt.required("created_run"),parser.readTree(text(operation,"response_ref")));
        var audit=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action='run.continuation.accepted' AND result='SUCCESS' LIMIT 2",a.context().tenant_id(),text(row,"project_id"),text(link,"operation_id")));same(receipt,parser.readTree(text(audit,"resource_ref")));
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type='run.continuation.accepted' LIMIT 2",a.context().tenant_id(),text(link,"operation_id"),run));same(receipt,parser.readTree(text(event,"payload")));
    }
    private Map<String,Object> run(EnterpriseAccess.Admission a,String run){return one(db.queryForList("SELECT * FROM task_run WHERE tenant_id=? AND run_id=? AND execution_kind='TASK' LIMIT 2 FOR UPDATE",a.context().tenant_id(),run));}
    private JsonNode replay(EnterpriseAccess.Admission a,String run)throws Exception{return reader.readTree(text(run(a,run),"replay_manifest"));}
    private void grant(EnterpriseAccess.Admission a,TaskProtocol.Head head)throws IOException {resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),head.projectId(),"TASK",head.taskId()),"task.run","RUN_CONTINUATION");}
    private static void current(EnterpriseAccess.Admission a,String context){same(context,a.context().context_revision());}
    private String hash(JsonNode node)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(node));}
    private static String text(Map<String,Object> row,String key){var value=row.get(key);if(value==null)fail(ErrorCode.CONTENT_MISMATCH);return value.toString();}
    private static <T>T one(List<T> rows){if(rows.size()!=1)fail(ErrorCode.CONTENT_MISMATCH);return rows.get(0);}
    private static void changed(int n){if(n!=1)fail(ErrorCode.REVISION_CONFLICT);}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))fail(ErrorCode.CONTENT_MISMATCH);}
    private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
