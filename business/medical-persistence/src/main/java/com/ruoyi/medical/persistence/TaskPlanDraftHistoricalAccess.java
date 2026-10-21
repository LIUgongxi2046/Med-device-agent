package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Human use of historical N01 facts. The current human is never the old worker/initiator.
 * This proves current fixed task/template/profile/selected-source access; saved candidate
 * bytes still require their separate full registered dependency/physical-read authority.
 */
public final class TaskPlanDraftHistoricalAccess {
    private final TaskPlanDraftJobs jobs;private final TaskService tasks;private final TaskPlanService plans;
    private final TemplateApplicabilityService templates;private final ProjectService projects;private final ResourceReadAuthority resources;
    private final TaskPlanDraftSchema schema;private final PermissionSchema json;
    public TaskPlanDraftHistoricalAccess(TaskPlanDraftJobs jobs,TaskService tasks,TaskPlanService plans,TemplateApplicabilityService templates,
            ProjectService projects,ResourceReadAuthority resources,TaskPlanDraftSchema schema,PermissionSchema json){this.jobs=jobs;this.tasks=tasks;this.plans=plans;this.templates=templates;this.projects=projects;this.resources=resources;this.schema=schema;this.json=json;}
    Verified require(EnterpriseAccess.Admission a,String context,String project,String task,String run)throws Exception {
        var inspected=inspect(a,context,project,task,run);return new Verified(a,context,project,task,run,inspected);
    }
    private Snapshot inspect(EnterpriseAccess.Admission a,String context,String project,String task,String run)throws Exception {
        TaskPlanDraftAdmission.current(a,context);if(a.formalWorker()||a.controlWorker()||a.planDraftWorker())deny();
        WireValues.uuid(project);WireValues.uuid(task);WireValues.uuid(run);var job=jobs.byRun(a.context().tenant_id(),run);
        if(!project.equals(job.project())||!task.equals(job.task()))deny();var original=jobs.original(job);var fixed=tasks.fixedTask(a,task);var h=fixed.head();
        if(!project.equals(h.projectId())||!job.taskRevision().equals(h.taskRevision())||!original.request().template().version_id().equals(h.templateVersion())
                ||!original.request().profile().version_id().equals(h.profileRevision()))throw new TenantAccessException(ErrorCode.RESULT_VERSION_MISMATCH);
        var grant=resources.require(a,new PermissionProtocol.Scope(h.tenantId(),project,"TASK",task),"task.read","TASK_READING");
        var target=templates.requireTarget(a,project,h.profileRevision(),fixed.input().documentType(),h.templateVersion(),"PLAN_MAINTENANCE");
        var profile=projects.fixedProfile(a,project,h.profileRevision(),"PLAN_MAINTENANCE");
        if(!original.request().template().content_sha256().equals(target.view().template().content_sha256())||!original.request().profile().content_sha256().equals(profile.sha256()))bad();
        var sources=plans.sourceProof(a,project,task,h.taskRevision(),h.snapshotId(),h.profileRevision(),true,"PLAN_MAINTENANCE");
        var admitted=schema.read(job.admissionJson());if(!h.snapshotId().equals(admitted.required("scope_snapshot_id").asText()))bad();
        var evidence=json.object();evidence.set("operator",AgentPublicationWitness.tree(a.bindingView()));evidence.set("context",AgentPublicationWitness.tree(a.context()));evidence.set("grant",AgentPublicationWitness.tree(grant));
        evidence.set("head",AgentPublicationWitness.tree(h));evidence.set("input",AgentPublicationWitness.tree(fixed.input()));evidence.set("template",AgentPublicationWitness.tree(target.view()));evidence.set("profile",AgentPublicationWitness.tree(profile));evidence.set("sources",AgentPublicationWitness.tree(sources));
        templates.requireUnchanged(a,target);return new Snapshot(job,fixed,target.view(),List.copyOf(sources),evidence,schema.hash(evidence));
    }
    private record Snapshot(TaskPlanDraftJobs.Job job,TaskService.VerifiedTask task,TemplateApplicabilityProtocol.TemplateView template,
            List<SourceReadProtocol.VerifiedSource> sources,JsonNode evidence,String hash){Snapshot{evidence=evidence.deepCopy();}}
    public final class Verified {
        private final EnterpriseAccess.Admission admission;private final String context,project,task,run;private final Snapshot snapshot;
        private final Thread thread=Thread.currentThread();private final TenantScope tenant=TenantScope.requireCurrent();private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Verified(EnterpriseAccess.Admission admission,String context,String project,String task,String run,Snapshot snapshot){this.admission=admission;this.context=context;this.project=project;this.task=task;this.run=run;this.snapshot=snapshot;if(transaction.isEmpty())deny();}
        public void requireUnchanged()throws Exception {if(!TransactionSynchronizationManager.isActualTransactionActive()||thread!=Thread.currentThread()||tenant!=TenantScope.requireCurrent())deny();var actual=TransactionSynchronizationManager.getResourceMap();if(actual.size()!=transaction.size()||transaction.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))deny();var current=inspect(admission,context,project,task,run);if(!snapshot.job().equals(current.job())||!snapshot.hash().equals(current.hash()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        TaskPlanDraftJobs.Job job(){return snapshot.job();}EnterpriseAccess.Admission admission(){return admission;}TaskService.VerifiedTask fixedTask(){return snapshot.task();}
        TemplateApplicabilityProtocol.TemplateView template(){return snapshot.template();}List<SourceReadProtocol.VerifiedSource> sources(){return snapshot.sources();}JsonNode evidence(){return snapshot.evidence().deepCopy();}String authoritySha256(){return snapshot.hash();}
    }
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
