package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** RUNNING-only bridge. It accepts the real private production proof, never caller pins. */
public final class StrictComparisonRunScope {
    private final TaskService tasks;private final TaskPlanService plans;private final TemplateApplicabilityService templates;private final StrictComparisonCodec codec;
    StrictComparisonRunScope(TaskService tasks,TaskPlanService plans,TemplateApplicabilityService templates,StrictComparisonCodec codec){this.tasks=tasks;this.plans=plans;this.templates=templates;this.codec=codec;}
    public Verified require(TaskNodeProductionAuthority.Verified production)throws Exception {
        Objects.requireNonNull(production).requireUnchanged();var state=resolve(production);return new Verified(production,state);
    }
    private record State(StrictComparisonProtocol.Scope scope,TemplateApplicabilityService.Target template,List<SourceReadProtocol.VerifiedSource> sources,JsonNode witness) { }
    private State resolve(TaskNodeProductionAuthority.Verified production)throws Exception {
        production.requireUnchanged();var a=production.admission();var replay=production.replay().content();
        var task=tasks.fixedTask(a,production.taskId());var h=task.head();
        if(!production.projectId().equals(h.projectId())||!production.tenantId().equals(h.tenantId())||!replay.required("task_revision").asText().equals(h.taskRevision())||!replay.required("plan_revision").asText().equals(h.planRevision())||!replay.required("snapshot_id").asText().equals(h.snapshotId())||!replay.required("plan").required("profile_revision").asText().equals(h.profileRevision())||!replay.required("plan").required("template_version").asText().equals(h.templateVersion()))stale();
        var plan=plans.readFixed(a,task,h.planRevision(),true);
        if(!"CONFIRMED".equals(plan.status())||!plan.contentSha256().equals(replay.required("plan").required("content_sha256").asText())||!codec.tree(plan.content()).equals(replay.required("plan").required("content")))stale();
        var template=templates.requireTarget(a,h.projectId(),h.profileRevision(),task.input().documentType(),h.templateVersion(),"PLAN_MAINTENANCE");
        if(!template.view().template().content_sha256().equals(replay.required("template").required("content_sha256").asText())||!template.profile().sha256().equals(replay.required("project_profile").required("content_sha256").asText()))stale();
        var sources=plans.sourceProof(a,h.projectId(),h.taskId(),h.taskRevision(),h.snapshotId(),h.profileRevision(),true);
        var scope=new StrictComparisonProtocol.Scope(h.taskId(),h.taskRevision(),h.planRevision(),h.templateVersion(),h.snapshotId());
        var witness=codec.tree(Map.of("run_id",production.runId(),"principal_id",production.principalId(),"replay_sha256",production.replay().rawSha256(),"scope",scope,"template",TaskFieldAuthorityWitness.tree(Map.of("view",template.view(),"grant",template.grant(),"dependencies",template.dependencyAuthorizations(),"profile",template.profile())),"sources",TaskFieldAuthorityWitness.tree(sources),"production_authorizations_sha256",production.authorizationsSha256()));
        production.requireUnchanged();return new State(scope,template,sources,witness);
    }
    public final class Verified {
        private final TaskNodeProductionAuthority.Verified production;private final State state;
        private Verified(TaskNodeProductionAuthority.Verified production,State state){this.production=production;this.state=state;}
        public StrictComparisonProtocol.Scope scope(){return state.scope();}
        EnterpriseAccess.Admission admission(){return production.admission();}
        String project(){return production.projectId();}
        TemplateApplicabilityService.Target template(){return state.template();}
        List<SourceReadProtocol.VerifiedSource> sources(){return state.sources();}
        JsonNode witness(){return state.witness().deepCopy();}
        public void requireUnchanged()throws Exception {if(!state.witness().equals(resolve(production).witness()))stale();}
    }
    private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
