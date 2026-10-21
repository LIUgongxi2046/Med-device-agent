package com.ruoyi.medical.persistence;

import java.util.Objects;
import java.util.function.Function;
import org.springframework.util.function.ThrowingSupplier;

/** Fixed PLAN_DRAFT read composition. Callbacks consume authority; they cannot mint it. */
public final class TaskPlanDraftCurrentAccess {
    private final TaskPlanDraftJobs jobs;private final TaskPlanDraftAdmission admission;private final TaskPlanDraftJobAuthority authority;
    private final AgentPublicationAuthority publications;private final AgentExecutionPolicyAuthority policies;
    public TaskPlanDraftCurrentAccess(TaskPlanDraftJobs jobs,TaskPlanDraftAdmission admission,TaskPlanDraftJobAuthority authority,
            AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies){this.jobs=jobs;this.admission=admission;this.authority=authority;this.publications=publications;this.policies=policies;}
    Prepared prepare(TaskPlanDraftControlAccess.Reads reads)throws Exception {
        reads.requireActive();var job=reads.execute(a->ThrowingSupplier.of(()->jobs.byRun(reads.tenantId(),reads.runId())).get());
        var original=reads.execute(a->ThrowingSupplier.of(()->jobs.original(job)).get());
        var publication=publications.preparePlanDraft(reads,original.request().agentReleaseId(),TaskPlanDraftAdmission.PURPOSE);
        var result=new Prepared(reads,job,original,publication);result.execute(v->{return null;});return result;
    }
    final class Prepared {
        private final TaskPlanDraftControlAccess.Reads reads;private final TaskPlanDraftJobs.Job job;private final TaskPlanDraftSchema.Original original;
        private final AgentPublicationAuthority.PreparedPublication publication;
        private Prepared(TaskPlanDraftControlAccess.Reads reads,TaskPlanDraftJobs.Job job,TaskPlanDraftSchema.Original original,AgentPublicationAuthority.PreparedPublication publication){this.reads=reads;this.job=job;this.original=original;this.publication=publication;}
        <T>T execute(Function<TaskPlanDraftJobAuthority.Verified,T> consumer){Objects.requireNonNull(consumer);return reads.execute(a->ThrowingSupplier.of(()->{
            var release=publications.require(a,original.request().agentReleaseId(),TaskPlanDraftAdmission.PURPOSE,publication);
            var basis=original.request().executionPolicy();var policy=policies.requireByVersion(a,basis.version_id(),basis.content_sha256(),job.project(),TaskPlanDraftAdmission.PURPOSE);
            var verified=authority.require(admission.requireCurrent(a,job,original,release,policy),job);var result=consumer.apply(verified);verified.requireUnchanged();return result;
        }).get());}
        void requireCurrent(){execute(v->null);}TaskPlanDraftControlAccess.Reads reads(){reads.requireActive();return reads;}TaskPlanDraftJobs.Job job(){reads.requireActive();return job;}
    }
}
