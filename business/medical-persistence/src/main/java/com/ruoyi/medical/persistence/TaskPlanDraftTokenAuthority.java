package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;
/** Exact prepared-publication transaction adapter for N01 worker token authority. Its
 * thread-local is a private scoped capability, never a caller-set flag or persisted approval. */
public final class TaskPlanDraftTokenAuthority {
    private final JdbcTemplate db;private final TaskPlanDraftControlAccess controls;private final AgentPublicationAuthority publications;
    private final AgentExecutionPolicyAuthority policies;private final TaskPlanDraftAdmission admissions;private final TaskPlanDraftJobAuthority jobs;
    private final TaskPlanDraftJobs ledger;private final TaskPlanDraftSchema schema;private final TaskPlanDraftRuntimeAuthority runtime;
    private final ThreadLocal<Current> active=new ThreadLocal<>();
    public TaskPlanDraftTokenAuthority(DataSource data,TaskPlanDraftControlAccess controls,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,TaskPlanDraftAdmission admissions,TaskPlanDraftJobAuthority jobs,
            TaskPlanDraftJobs ledger,TaskPlanDraftSchema schema,TaskPlanDraftRuntimeAuthority runtime){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.controls=controls;this.publications=publications;this.policies=policies;this.admissions=admissions;this.jobs=jobs;this.ledger=ledger;this.schema=schema;this.runtime=runtime;}
    boolean supports(String tenant,String actor,String run){WireValues.uuid(tenant);WireValues.uuid(actor);WireValues.uuid(run);var rows=db.query("SELECT d.operation_id FROM task_plan_draft_job d JOIN system_job_run j ON j.tenant_id=d.tenant_id AND j.run_id=d.run_id WHERE d.tenant_id=? AND d.created_by=? AND d.run_id=? AND j.execution_kind='SYSTEM_JOB' AND j.job_type='PLAN_DRAFT' AND j.target_kind='TASK' AND j.target_id=d.task_id AND j.target_revision=d.task_revision LIMIT 2",(r,n)->r.getString(1),tenant,actor,run);if(rows.size()>1)bad();return rows.size()==1;}
    <T>T execute(HttpServletRequest http,String tenant,String actor,String run,String step,BigInteger expectedAuth,Operation<T> operation)throws Exception {
        if(TransactionSynchronizationManager.isActualTransactionActive()||active.get()!=null)deny();
        var op=one(db.query("SELECT operation_id FROM task_plan_draft_job WHERE tenant_id=? AND created_by=? AND run_id=? LIMIT 2",(r,n)->r.getString(1),tenant,actor,run));
        return controls.withReads(http,tenant,run,op,reads->ThrowingSupplier.of(()->{
            var prepared=publications.preparePlanDraft(reads,reads.admission().required("agent_release_id").textValue(),"RUN_CREATION");
            return reads.execute(a->ThrowingSupplier.of(()->{
                var job=reads.job();if(!actor.equals(job.actor())||!step.equals(job.step()))deny();
                if(expectedAuth!=null&&!expectedAuth.toString().equals(a.context().auth_epoch()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
                var original=ledger.original(job);var basis=original.request().executionPolicy();
                var policy=policies.requireByVersion(a,basis.version_id(),basis.content_sha256(),job.project(),"RUN_CREATION");
                var publication=publications.require(a,original.request().agentReleaseId(),"RUN_CREATION",prepared);
                var admitted=admissions.requireCurrent(a,job,original,publication,policy);
                var current=jobs.require(admitted,job);var worker=current.worker();var bootstrap=runtime.requireBootstrap(current,worker.serviceUri(),worker.audience());
                var context=new Current(current,bootstrap,worker.serviceUri(),worker.audience(),TenantScope.requireCurrent(),Map.copyOf(TransactionSynchronizationManager.getResourceMap()));active.set(context);
                try{var result=operation.apply(new TenantWatermarks(new BigInteger(a.context().auth_epoch()),new BigInteger(a.context().governance_epoch())));context.require();bootstrap.requireUnchanged();return result;}finally{active.remove();}
            }).get());
        }).get());
    }
    TaskPlanDraftRuntimeAuthority.Bootstrap require(String tenant,String actor,String run,String step,String roleHash,String service,String audience)throws Exception {
        var c=current();var j=c.job.job();if(!tenant.equals(j.tenant())||!actor.equals(j.actor())||!run.equals(j.run())||!step.equals(j.step())||!roleHash.equals(c.bootstrap.roleRawSha())||!service.equals(c.service)||!audience.equals(c.audience))deny();c.bootstrap.requireUnchanged();return c.bootstrap;
    }
    void requireAdmission(RunTokenAuthority.Admission a)throws Exception {var c=current();var j=c.job.job();if(!j.tenant().equals(a.tenantId())||!j.project().equals(a.projectId())||!j.actor().equals(a.initiatorId())||!j.run().equals(a.runId())||!j.step().equals(a.stepId())||!j.principal().equals(a.principalId())||!c.service.equals(a.serviceUri())||!c.bootstrap.nodePermissions().containsAll(a.delegatedPermissions()))deny();c.bootstrap.requireUnchanged();}
    TaskPlanDraftRuntimeAuthority.Verified leased(RunTokenAuthority.ActionRequest action)throws Exception {
        var c=current();var j=c.job.job();if(!j.run().equals(action.runId())||!j.step().equals(action.stepId())||!j.inputHash().equals(action.inputSha256()))deny();return runtime.requireLeased(c.job,action.actionId(),action.generation(),c.service,c.audience);
    }
    void bindClaimedHost(RunTokenAuthorityStore.Run run,RunTokenAuthorityStore.Step step)throws Exception {
        var c=current();var j=c.job.job();if(!j.run().equals(run.id())||!j.step().equals(step.id())||!c.service.equals(step.owner()))deny();
        var changed=db.update("UPDATE step_action_binding SET lease_generation=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND kind='HOST_CONTROL' AND tool_version='PLAN_DRAFT_HOST_V1' AND input_sha256=? AND arguments_sha256=? AND state='BOUND' AND invocation_id IS NULL AND lease_generation<?",step.generation(),j.tenant(),j.project(),j.run(),j.step(),j.action(),j.inputHash(),j.inputHash(),step.generation());
        if(changed!=1)bad();c.bootstrap.requireUnchanged();
    }
    private Current current(){var c=active.get();if(c==null)deny();c.require();return c;}
    private record Current(TaskPlanDraftJobAuthority.Verified job,TaskPlanDraftRuntimeAuthority.Bootstrap bootstrap,String service,String audience,TenantScope scope,Map<Object,Object> resources){void require(){if(!TransactionSynchronizationManager.isActualTransactionActive()||scope!=TenantScope.requireCurrent()||resources.isEmpty())deny();var now=TransactionSynchronizationManager.getResourceMap();if(now.size()!=resources.size()||resources.entrySet().stream().anyMatch(e->now.get(e.getKey())!=e.getValue()))deny();}}
    @FunctionalInterface interface Operation<T>{T apply(TenantWatermarks watermarks)throws Exception;}
    private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
