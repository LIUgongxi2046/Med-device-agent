package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.workflow.WorkflowProtocol.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;

/** Current service control authority, not a recovery or human admission. A proof
 * permits exactly one operation family and remains bound to the real transaction.
 * It never claims a lease, creates a token, launches a host, or reserves money. */
public final class TaskRunLifecycleAuthority {
    enum Operation {
        FAILURE(false),DEPENDENCY_FAILED(false),FINISH_SUCCEEDED(false),FINISH_FAILED(false),
        REQUEST_CANCEL(true),CONSUME_CANCEL(true),FINISH_CANCELLED(true);
        private final boolean cancellation;Operation(boolean c){cancellation=c;}
        boolean cancellation(){return cancellation;}String purpose(){return cancellation?"RUN_CANCELLATION":"TASK_EXECUTION";}
    }
    @FunctionalInterface interface Action<T>{T apply(Proof proof)throws Exception;}
    private final JdbcTemplate db;private final FormalRunControlAccess controls;private final AgentPublicationAuthority publications;
    private final AgentExecutionPolicyAuthority policies;private final RuntimeRetentionAuthority retention;private final IdentityDirectoryPolicy identity;
    private final TaskRunLaunchRecoveryAuthority launches;private final TaskRunWorkflowLedger workflow;private final TaskRunGraphService graphs;private final PermissionSchema json;
    public TaskRunLifecycleAuthority(DataSource data,FormalRunControlAccess controls,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,RuntimeRetentionAuthority retention,IdentityDirectoryPolicy identity,
            TaskRunLaunchRecoveryAuthority launches,TaskRunWorkflowLedger workflow,TaskRunGraphService graphs,PermissionSchema json){
        this(new JdbcTemplate(data),controls,publications,policies,retention,identity,launches,workflow,graphs,json);
    }
    TaskRunLifecycleAuthority(JdbcTemplate db,FormalRunControlAccess controls,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,RuntimeRetentionAuthority retention,IdentityDirectoryPolicy identity,
            TaskRunLaunchRecoveryAuthority launches,TaskRunWorkflowLedger workflow,TaskRunGraphService graphs,PermissionSchema json){
        this.db=db;db.setQueryTimeout(5);this.controls=controls;this.publications=publications;this.policies=policies;this.retention=retention;this.identity=identity;this.launches=launches;this.workflow=workflow;this.graphs=graphs;this.json=json;
    }
    <T>T execute(HttpServletRequest http,Reference ref,Operation operation,Action<T> action)throws Exception{
        if(ref.executionKind()!=ExecutionKind.TASK)deny();
        String command=one(db.query("SELECT payload->>'$.command.command_id' FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='run.start.requested' LIMIT 2",(r,n)->r.getString(1),ref.tenantId(),ref.runId()));
        return controls.withLifecycleReads(http,ref.tenantId(),ref.runId(),command,operation,reads->ThrowingSupplier.of(()->{
            var prepared=publications.prepareControl(reads,reads.replay().required("agent_release_id").asText(),"RUN_CREATION");
            return reads.execute(a->ThrowingSupplier.of(()->{
                var replay=reads.replay();var publication=publications.require(a,replay.required("agent_release_id").asText(),"RUN_CREATION",prepared);
                var policy=policies.requireByVersion(a,reads.policyVersion(),reads.policySha256(),ref.projectId(),identity.classification(),"RUN_CREATION");
                var pin=replay.required("retention");var kept=retention.requireFixedForRun(a,pin.required("policy_revision").asText(),pin.required("content_sha256").asText(),ref.projectId(),identity.classification(),"RUN_CREATION");
                same(ref.policyRevision(),policy.versionId());same(replay.required("execution_policy"),json.tree(policy.definition()));same(replay.required("agent_release"),json.tree(publication.manifest().manifest()));same(replay.required("publication_approval_sha256").asText(),publication.approvalSha256());
                var launch=launches.require(a,http,ref.runId(),command);same(ref,workflow.recover(launch).reference());if(workflow.read(launch).isEmpty())deny();
                var graph=graphs.require(a,ref.runId());var row=row(a,ref);if(!Set.of("RUNNING","WAITING_INPUT","CANCEL_REQUESTED").contains(text(row,"status"))||row.get("ended_at")!=null||row.get("started_at")==null)deny();
                if(!operation.cancellation()&&(!"RUNNING".equals(row.get("status"))||row.get("dispatch_closed_at")!=null))deny();
                var proof=new Proof(a,ref,operation,publication,policy,kept,launch,graph,row);
                try{proof.require(operation);T result=action.apply(proof);proof.require(operation);return result;}finally{proof.active=false;}
            }).get());
        }).get());
    }
    private Map<String,Object> row(EnterpriseAccess.Admission a,Reference ref){return one(db.queryForList("SELECT * FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId(),a.actorId()));}
    final class Proof {
        private final EnterpriseAccess.Admission admission;private final Reference ref;private final Set<Operation> allowed;
        private final AgentPublicationAuthority.VerifiedPublication publication;private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final RuntimeRetentionAuthority.Verified kept;
        private final TaskRunLaunchRecoveryAuthority.Verified launch;private final TaskRunGraphService.Verified graph;
        private Map<String,Object> expected;private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();
        private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());private boolean active=true;
        private Proof(EnterpriseAccess.Admission a,Reference ref,Operation operation,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,RuntimeRetentionAuthority.Verified kept,TaskRunLaunchRecoveryAuthority.Verified launch,TaskRunGraphService.Verified graph,Map<String,Object> row){this.admission=a;this.ref=ref;allowed=Set.of(operation);this.publication=publication;this.policy=policy;this.kept=kept;this.launch=launch;this.graph=graph;expected=new HashMap<>(row);if(transaction.isEmpty())deny();TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){active=false;}});}
        private void active(){if(!active||thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||!TransactionSynchronizationManager.isActualTransactionActive())deny();var actual=TransactionSynchronizationManager.getResourceMap();if(actual.size()!=transaction.size()||transaction.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))deny();}
        void require(Operation op)throws Exception{active();if(!allowed.contains(op))deny();admission.controlProof().requireActive();publication.requireUnchanged();policy.requireUnchanged();kept.requireUnchanged();launch.requireUnchanged();graph.requireUnchanged();same(expected,row(admission,ref));}
        AgentPublicationAuthority.VerifiedPublication publication(){active();return publication;}AgentExecutionPolicyAuthority.VerifiedPolicy policy(){active();return policy;}
        Operation operation(){active();return allowed.iterator().next();}TaskRunLaunchRecoveryAuthority.Verified originalLaunch(){active();return launch;}
        EnterpriseAccess.Admission admission(){active();return admission;}Reference reference(){active();return ref;}TaskRunGraphService.Verified graph(){active();return graph;}
        String taskId(){active();return text(expected,"task_id");}String classification(){active();return text(expected,"classification");}String status(){active();return text(expected,"status");}
        String revision(){active();return number(expected,"revision");}LocalDateTime now(){active();return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
        JsonNode authority(){active();var n=json.object().put("operation",allowed.iterator().next().name()).put("purpose",allowed.iterator().next().purpose());n.set("publication",publication.authorizations());n.set("policy",policy.authorizations());return n;}
        /** The only permitted mutation is performed here; callers cannot mint Transition. */
        void changeRun(Operation op,State next,String reason)throws Exception{
            require(op);if(reason==null||!reason.matches("[A-Z][A-Z0-9_]{0,63}"))deny();
            boolean cancellation=next==State.CANCEL_REQUESTED;boolean terminal=Set.of(State.SUCCEEDED,State.FAILED,State.CANCELLED).contains(next);
            if(cancellation&&!Set.of(Operation.REQUEST_CANCEL,Operation.CONSUME_CANCEL).contains(op)||terminal&&op!=switch(next){case SUCCEEDED->Operation.FINISH_SUCCEEDED;case FAILED->Operation.FINISH_FAILED;case CANCELLED->Operation.FINISH_CANCELLED;default->throw new IllegalArgumentException();}||!cancellation&&!terminal)deny();
            if(next==State.CANCELLED&&!"CANCEL_REQUESTED".equals(status())||cancellation&&"CANCEL_REQUESTED".equals(status()))deny();
            var control=admission.controlProof();String actor=admission.actorId();var now=now();var revision=new BigInteger(revision());if(revision.signum()<1||revision.bitLength()>64||revision.equals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)))deny();
            changed(db.update("UPDATE task_run SET status=?,reason_code=?,dispatch_closed_at=COALESCE(dispatch_closed_at,?),ended_at=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND status=? AND revision=? AND ended_at IS NULL",next.name(),reason,now,terminal?now:null,now,ref.tenantId(),ref.projectId(),ref.runId(),admission.actorId(),status(),revision));
            var after=one(db.queryForList("SELECT * FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId(),actor));var permitted=new HashMap<>(expected);permitted.put("status",next.name());permitted.put("reason_code",reason);permitted.put("dispatch_closed_at",after.get("dispatch_closed_at"));permitted.put("ended_at",after.get("ended_at"));permitted.put("revision",after.get("revision"));permitted.put("updated_at",after.get("updated_at"));same(permitted,after);same(revision.add(BigInteger.ONE).toString(),number(after,"revision"));same(now,time(after,"updated_at"));if(terminal)same(now,time(after,"ended_at"));if(expected.get("dispatch_closed_at")==null)same(now,time(after,"dispatch_closed_at"));else same(expected.get("dispatch_closed_at"),after.get("dispatch_closed_at"));
            var transition=new Transition(control,op,ref,status(),next.name(),expected.get("dispatch_closed_at")==null?null:time(expected,"dispatch_closed_at"),time(after,"dispatch_closed_at"),terminal?now:null);
            control.lifecycleTransition(transition);expected=new HashMap<>(after);project(op,next,reason,now,actor);require(op);
        }
        /** Only the exact current active pointer may move the task head. A newer run
         * is never overwritten by completion of an older run. */
        private void project(Operation op,State next,String reason,LocalDateTime at,String actor)throws Exception{
            require(op);var pointers=db.queryForList("SELECT * FROM task_active_run WHERE tenant_id=? AND project_id=? AND task_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),taskId());if(pointers.size()>1)bad();
            if(!pointers.isEmpty()&&ref.runId().equals(text(pointers.get(0),"run_id"))){var pointer=pointers.get(0);var head=one(db.queryForList("SELECT current_revision,status,revision FROM task_head WHERE tenant_id=? AND project_id=? AND task_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),taskId()));same(text(expected,"task_revision"),text(head,"current_revision"));
                if(!Set.of("READY","RUNNING","CANCEL_REQUESTED").contains(text(head,"status")))deny();String target=next==State.SUCCEEDED?"REVIEW_PENDING":next.name();
                if(!target.equals(text(head,"status")))changed(db.update("UPDATE task_head SET status=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND task_id=? AND current_revision=? AND status=? AND revision=?",target,at,ref.tenantId(),ref.projectId(),taskId(),text(head,"current_revision"),text(head,"status"),number(head,"revision")));
                if(Set.of(State.SUCCEEDED,State.FAILED,State.CANCELLED).contains(next))changed(db.update("DELETE FROM task_active_run WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND revision=?",ref.tenantId(),ref.projectId(),taskId(),ref.runId(),number(pointer,"revision")));
            }
            var stream=one(db.queryForList("SELECT stream_id,last_sequence,revision FROM stream_registry WHERE tenant_id=? AND project_id=? AND kind='RUN' AND owner_resource_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId()));String sequence=PermissionSchema.next(number(stream,"last_sequence")),eventId=derived(ref.runId(),"lifecycle-public:"+revision());
            var payload=json.object().put("business_event_type","RUN_"+next.name()).put("status",next.name()).put("reason_code",reason).put("read_operation_id","RN-02");
            var event=json.object().put("schema_version","1.0.0").put("event_id",eventId).put("sequence",sequence).put("event_type","run.updated").put("tenant_id",ref.tenantId()).put("project_id",ref.projectId()).put("task_id",taskId()).put("run_id",ref.runId()).put("resource_kind","RUN").put("resource_id",ref.runId()).put("resource_revision",revision()).put("occurred_at",at.toInstant(java.time.ZoneOffset.UTC).toString());event.set("stream",json.object().put("stream_id",text(stream,"stream_id")).put("kind","RUN").put("resource_id",ref.runId()));event.set("payload",payload);com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath().validate("interfaces",event,"PublicEvent");
            changed(db.update("UPDATE stream_registry SET last_sequence=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND stream_id=? AND kind='RUN' AND owner_resource_id=? AND last_sequence=? AND revision=?",sequence,at,ref.tenantId(),ref.projectId(),text(stream,"stream_id"),ref.runId(),number(stream,"last_sequence"),number(stream,"revision")));
            changed(db.update("INSERT INTO stream_event(tenant_id,project_id,event_id,stream_id,sequence,task_id,run_id,resource_kind,resource_id,resource_revision,event_type,payload,occurred_at,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,'RUN',?,?,'run.updated',?,?,'1.0.0',?,?,?,?)",ref.tenantId(),ref.projectId(),eventId,text(stream,"stream_id"),sequence,taskId(),ref.runId(),ref.runId(),revision(),json.canonical(payload),at,json.manifestSha256(),actor,at,classification()));
            changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,?,'run.updated',?,?)",ref.tenantId(),eventId,ref.runId(),revision(),json.canonical(event),at));
        }

    }
    static final class Transition {
        private final FormalRunControlAccess.Proof proof;private final Operation operation;private final Reference ref;private final String before,after;private final LocalDateTime closedBefore,closedAfter,endedAfter;private boolean used;
        private final Thread thread=Thread.currentThread();private final Map<Object,Object> resources=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Transition(FormalRunControlAccess.Proof proof,Operation operation,Reference ref,String before,String after,LocalDateTime closedBefore,LocalDateTime closedAfter,LocalDateTime endedAfter){this.proof=proof;this.operation=operation;this.ref=ref;this.before=before;this.after=after;this.closedBefore=closedBefore;this.closedAfter=closedAfter;this.endedAfter=endedAfter;}
        void claim(FormalRunControlAccess.Proof caller,Operation op,RunTokenAuthorityStore.Run old,RunTokenAuthorityStore.Run fresh){
            if(used||caller!=proof||op!=operation||thread!=Thread.currentThread()||!TransactionSynchronizationManager.isActualTransactionActive())deny();var actual=TransactionSynchronizationManager.getResourceMap();if(resources.isEmpty()||resources.size()!=actual.size()||resources.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))deny();
            same(ref.tenantId(),old.tenant());same(ref.projectId(),old.project());same(ref.runId(),old.id());same(before,old.status());same(after,fresh.status());same(null,old.ended());same(closedBefore,old.closed());same(closedAfter,fresh.closed());same(endedAfter,fresh.ended());
            same(new RunTokenAuthorityStore.Run(old.tenant(),old.project(),old.id(),old.kind(),old.scope(),old.schema(),old.manifest(),old.principal(),old.actor(),old.capabilities(),old.principalStatus(),old.principalSchema(),old.principalManifest(),old.classification(),old.revision(),old.executionActor(),fresh.status(),fresh.ended(),fresh.closed(),old.superseded(),old.task()),fresh);used=true;
        }
    }
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
