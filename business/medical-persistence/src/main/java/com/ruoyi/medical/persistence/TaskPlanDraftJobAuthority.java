package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import java.util.Objects;

/** Binds an actual immutable job to freshly admitted fixed inputs. A run UUID alone is not authority.
 * This initial entry consumes human-start admission; a worker must supply a genuine dedicated
 * PLAN_DRAFT control authority, never a synthetic human session or the unrelated TASK graph proof.
 */
public final class TaskPlanDraftJobAuthority {
    private final TaskPlanDraftJobs jobs;private final TaskPlanDraftSchema schema;
    public TaskPlanDraftJobAuthority(TaskPlanDraftJobs jobs,TaskPlanDraftSchema schema){this.jobs=Objects.requireNonNull(jobs);this.schema=Objects.requireNonNull(schema);}
    Verified require(TaskPlanDraftAdmission.Verified current,TaskPlanDraftJobs.Job expected)throws Exception {
        check(current,expected);return new Verified(current,expected);
    }
    private void check(TaskPlanDraftAdmission.Verified current,TaskPlanDraftJobs.Job expected)throws Exception {
        current.requireUnchanged();var job=jobs.byRun(current.admission().context().tenant_id(),expected.run());
        if(!job.equals(expected)||!job.actor().equals(current.admission().actorId())||!job.task().equals(current.task())||!job.project().equals(current.project())
                ||!job.taskRevision().equals(current.fixedTask().head().taskRevision())||!schema.canonical(current.evidence()).equals(job.authorityJson()))bad();
    }
    public final class Verified {
        private final TaskPlanDraftAdmission.Verified current;private final TaskPlanDraftJobs.Job job;
        private Verified(TaskPlanDraftAdmission.Verified current,TaskPlanDraftJobs.Job job){this.current=current;this.job=job;}
        public void requireUnchanged()throws Exception{check(current,job);}
        EnterpriseAccess.Admission admission(){return current.admission();}TaskPlanDraftJobs.Job job(){return job;}
        List<RunTokenAuthoritySchema.Permission> nodePermissions(){return current.permissions();}
        AgentExecutionPolicyProtocol.Worker worker(){return current.worker();}
        TaskPlanDraftAdmission.Verified current(){return current;}
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
