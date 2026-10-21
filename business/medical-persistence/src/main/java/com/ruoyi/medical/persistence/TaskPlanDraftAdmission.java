package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Private pre-plan authority. No plan, step, publication, source right or approval is fabricated.
 * Publication must be physically prepared outside the transaction by its existing authority.
 * Budget availability here is admission, NOT paid-action reservation or permission to dispatch.
 */
public final class TaskPlanDraftAdmission {
    static final String PURPOSE = "RUN_CREATION";
    private final TaskService tasks; private final TaskPlanService plans; private final ProjectService projects;
    private final TemplateApplicabilityService templates; private final ResourceReadAuthority resources;
    private final AgentReleaseStore releases; private final AgentReleaseScopeAuthority scopes;
    private final TemplateDependencyPolicy limits; private final IdentityDirectoryPolicy identities;
    private final RunCreateBudget budgets; private final PermissionMapper permissions;
    private final PermissionSchema json; private final TaskPlanDraftSchema schema;
    private final com.ruoyi.medical.persistence.mapper.VersionResourceMapper versionOwners;
    private final DependencyLineage lineage;
    private final RuntimeRetentionAuthority retention;
    public TaskPlanDraftAdmission(TaskService tasks, TaskPlanService plans, ProjectService projects,
            TemplateApplicabilityService templates, ResourceReadAuthority resources, AgentReleaseStore releases,
            AgentReleaseScopeAuthority scopes, TemplateDependencyPolicy limits, IdentityDirectoryPolicy identities,
            RunCreateBudget budgets, PermissionMapper permissions, PermissionSchema json, TaskPlanDraftSchema schema,
            com.ruoyi.medical.persistence.mapper.VersionResourceMapper versionOwners,DependencyLineage lineage,RuntimeRetentionAuthority retention) {
        this.tasks=tasks; this.plans=plans; this.projects=projects; this.templates=templates; this.resources=resources;
        this.releases=releases; this.scopes=scopes; this.limits=limits; this.identities=identities; this.budgets=budgets;
        this.permissions=permissions; this.json=json; this.schema=schema; this.versionOwners=versionOwners;this.lineage=lineage;this.retention=retention;
    }
    Verified require(EnterpriseAccess.Admission admission, String context, String projectId, String taskId,
            TaskPlanDraftSchema.Original original, AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy) throws Exception {
        var snapshot=inspect(admission,context,projectId,taskId,original,publication,policy,true);
        return new Verified(admission,context,projectId,taskId,original,publication,policy,snapshot);
    }
    Verified requireCurrent(EnterpriseAccess.Admission admission,TaskPlanDraftJobs.Job job,
            TaskPlanDraftSchema.Original original,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        if(!admission.planDraftWorker()||!job.equals(admission.planDraftProof().job()))deny();
        var snapshot=inspect(admission,admission.context().context_revision(),job.project(),job.task(),original,publication,policy,false);
        var pinned=schema.read(job.authorityJson());
        // Mutable head revision, accounting counters and current authorization witnesses are not original content.
        for(String field:List.of("input","profile","template","dependency_versions","permissions","budgets","retention"))
            if(!pinned.required(field).equals(snapshot.evidence().required(field)))bad();
        var verified=new Verified(admission,admission.context().context_revision(),job.project(),job.task(),original,publication,policy,snapshot);
        verified.originalEvidence=pinned.deepCopy();return verified;
    }
    JsonNode missingPackageSelections(Verified current,TaskPlanDraftPackagePreflight.Verified packages)throws Exception {
        packages.requireFor(current);var selected=current.sources().stream().filter(source->source.selection()!=null).map(source->source.source().sourceVersion()).collect(java.util.stream.Collectors.toSet());
        var missing=json.object().arrayNode();for(String version:packages.sourceVersions())if(!selected.contains(version))missing.addObject().put("source_version",version).put("requirement","FIXED_SCOPE_SELECTION_REQUIRED").put("resolution","PREPARE_MATERIAL_AND_CREATE_TASK_REVISION");
        return missing;
    }
    /** Only actual physical package receipts can add preflight pins; no source selection is widened. */
    Verified withPackages(Verified current,TaskPlanDraftPackagePreflight.Verified packages)throws Exception {
        if(current.admission().planDraftWorker())deny();packages.requireFor(current);
        var selected=current.sources().stream().filter(source->source.selection()!=null).map(source->source.source().sourceVersion()).collect(java.util.stream.Collectors.toSet());
        if(!selected.containsAll(packages.sourceVersions()))fail(ErrorCode.SOURCE_NOT_READY);
        var result=new Verified(current.admission,current.context,current.project,current.task,current.original,current.publication,current.policy,current.snapshot);
        var pinned=(com.fasterxml.jackson.databind.node.ObjectNode)current.evidence();pinned.set("package_preflight",packages.canonicalPins());result.originalEvidence=pinned;
        current.requireUnchanged();return result;
    }
    private Snapshot inspect(EnterpriseAccess.Admission a,String context,String project,String task,
            TaskPlanDraftSchema.Original original,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,boolean initial)throws Exception {
        current(a,context); WireValues.uuid(project); WireValues.uuid(task);
        var request=original.request(); var fixed=fixed(a,task);var head=fixed.head();
        if(!project.equals(head.projectId())||!a.context().tenant_id().equals(head.tenantId()))deny();
        if(!Set.of("NEEDS_MATERIALS","PLAN_PENDING","READY","FAILED","CANCELLED").contains(head.status()))fail(ErrorCode.REVISION_CONFLICT);
        if(!head.taskRevision().equals(request.taskRevision())||!a.planDraftWorker()&&!head.revision().toString().equals(request.expectedRevision()))fail(ErrorCode.REVISION_CONFLICT);
        if(!head.templateVersion().equals(request.template().version_id())||!head.profileRevision().equals(request.profile().version_id())
                ||!head.agentReleaseId().equals(request.agentReleaseId()))bad();
        var taskScope=new PermissionProtocol.Scope(head.tenantId(),project,"TASK",task);
        var runGrant=a.planDraftWorker()?a.planDraftProof().requireOriginalAction(taskScope,"task.run","TASK_EXECUTION"):resources.require(a,taskScope,"task.run","TASK_EXECUTION");
        var planGrant=a.planDraftWorker()?a.planDraftProof().requireOriginalAction(taskScope,"plan.write","PLAN_MAINTENANCE"):resources.require(a,taskScope,"plan.write","PLAN_MAINTENANCE");
        publication.requireConsumer(a,head.agentReleaseId(),PURPOSE);policy.requireConsumer(a,project,PURPOSE);
        if(!request.executionPolicy().version_id().equals(policy.versionId())||!request.executionPolicy().content_sha256().equals(policy.contentSha256())
                ||!publication.executionPolicyId().equals(policy.versionId())||!publication.executionPolicySha256().equals(policy.contentSha256()))bad();
        AgentExecutionPolicyAuthority.requireManifest(policy.definition(),publication.manifest().manifest());
        var bindings=policy.definition().bindings().stream().filter(b->"N01".equals(b.nodeKind())).toList();
        if(bindings.size()!=1)fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var node=bindings.get(0);
        policy.requireNode("N01",node.roleVersion(),policy.definition().worker().serviceUri(),policy.definition().worker().audience());
        var release=releases.read(a,head.agentReleaseId(),PURPOSE);
        if(!release.content().equals(publication.manifest()))bad();
        var releaseScope=a.planDraftWorker()?scopes.requirePlanDraft(release,taskScope,identities.classification(),limits.limits(),a.planDraftProof()):scopes.require(release,taskScope,"task.run",identities.classification(),limits.limits());
        var target=templates.requireTarget(a,project,head.profileRevision(),fixed.input().documentType(),head.templateVersion(),PURPOSE);
        var profile=projects.fixedProfile(a,project,head.profileRevision(),PURPOSE);
        if(!request.template().content_sha256().equals(target.view().template().content_sha256())
                ||!request.profile().content_sha256().equals(profile.sha256()))bad();
        if(publication.manifest().manifest().templateVersions().stream().noneMatch(t->t.versionId().equals(head.templateVersion())
                &&t.contentSha256().equals(request.template().content_sha256())))deny();
        var sources=a.planDraftWorker()?plans.sourceProofPlanDraft(a,project,task,head.taskRevision(),head.snapshotId(),head.profileRevision(),true,PURPOSE):plans.sourceProof(a,project,task,head.taskRevision(),head.snapshotId(),head.profileRevision(),true,PURPOSE);
        var selected=sources.stream().map(s->new PermissionProtocol.Basis(s.source().sourceVersion(),s.source().originalSha256())).toList();
        PublishedTaskCreateAdmission.requireSelection(policy.definition(),selected);
        var delegated=permissions(a,head,node,policy,sources);
        var now=Objects.requireNonNull(permissions.databaseTime());var budgetEvidence=json.object().arrayNode();
        boolean enterprise=false;
        for(var basis:List.of(request.enterpriseBudget(),request.parentBudget())) {
            var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("projectId",project);values.put("budgetRevision",basis.version_id());
            var row=initial?budgets.require(a,values,now,request.currency()):budgets.requireWithinBounds(a,values,now,request.currency());
            if(!basis.content_sha256().equals(row.required("content_sha256").textValue()))bad();
            if(basis.equals(request.enterpriseBudget())) { if(!row.required("project_id").isNull())deny(); enterprise=true; }
            var approval=row.required("approval").required("limits");
            var available=BudgetSchema.money(approval.required("amount").asText()).subtract(BudgetSchema.money(row.required("spent").asText())).subtract(BudgetSchema.money(row.required("reserved").asText()));
            var availableTokens=new java.math.BigInteger(approval.required("tokens").asText()).subtract(new java.math.BigInteger(row.required("spent_tokens").asText())).subtract(new java.math.BigInteger(row.required("reserved_tokens").asText()));
            if(initial&&(BudgetSchema.money(request.maximumAmount()).compareTo(available)>0||new java.math.BigInteger(request.maximumTokens()).compareTo(availableTokens)>0))fail(ErrorCode.BUDGET_EXCEEDED);
            var pin=json.object();
            for(String field:List.of("tenant_id","project_id","budget_id","budget_revision","content_sha256","approval","currency","schema_version","schema_manifest"))pin.set(field,row.required(field));
            budgetEvidence.add(pin);
        }
        if(!enterprise)deny();
        var retentionProof=retention.requireForRun(a,project,identities.classification(),PURPOSE);
        var retentionPin=json.object().put("policy_revision",retentionProof.policyRevision()).put("content_sha256",retentionProof.contentSha256());
        retentionPin.set("terms",retentionProof.terms());
        var evidence=json.object();evidence.set("retention",retentionPin);evidence.set("retention_authorizations",AgentPublicationWitness.tree(retentionProof.authorizations()));evidence.set("task",AgentPublicationWitness.tree(head));evidence.set("input",AgentPublicationWitness.tree(fixed.input()));
        evidence.set("profile",AgentPublicationWitness.tree(profile));evidence.set("template",AgentPublicationWitness.tree(target.view()));
        evidence.set("dependency_versions",dependencyVersions(a,head,publication,policy,sources,request,retentionProof.policyRevision()));evidence.set("permissions",AgentPublicationWitness.tree(delegated));evidence.set("sources",AgentPublicationWitness.tree(sources));evidence.set("publication",publication.authorizations());evidence.set("policy",policy.authorizations());
        evidence.set("release_scope",AgentPublicationWitness.tree(Map.of("scopes",releaseScope.scopes(),"roles",releaseScope.roles(),"grant",releaseScope.grant())));
        evidence.set("run_grant",AgentPublicationWitness.tree(runGrant));evidence.set("plan_grant",AgentPublicationWitness.tree(planGrant));evidence.set("budgets",budgetEvidence);
        retentionProof.requireUnchanged();templates.requireUnchanged(a,target);scopes.requireUnchanged(releaseScope);publication.requireUnchanged();policy.requireUnchanged();
        var after=fixed(a,task);
        if(!fixed.head().equals(after.head())||!fixed.input().equals(after.input()))fail(ErrorCode.SCOPE_STALE);
        return new Snapshot(fixed,target,profile,evidence,schema.hash(evidence),node,delegated,policy.definition().worker(),publication.manifest().manifest(),sources);
    }
    record Fixed(TaskProtocol.Head head,TaskProtocol.Input input) { }
    private Fixed fixed(EnterpriseAccess.Admission a,String task)throws Exception {
        if(a.planDraftWorker()){var x=tasks.fixedTaskPlanDraft(a,task);return new Fixed(x.head(),x.input());}
        var x=tasks.fixedTask(a,task);return new Fixed(x.head(),x.input());
    }
    private record Snapshot(Fixed task,TemplateApplicabilityService.Target template,
            ProjectProtocol.VerifiedProfile profile,JsonNode evidence,String digest,AgentExecutionPolicyProtocol.NodeBinding node,
            List<RunTokenAuthoritySchema.Permission> delegated,AgentExecutionPolicyProtocol.Worker worker,AgentReleaseProtocol.Manifest releaseManifest,
            List<SourceReadProtocol.VerifiedSource> sources) {
        Snapshot { evidence=evidence.deepCopy(); delegated=List.copyOf(delegated); sources=List.copyOf(sources); }
    }
    final class Verified {
        private final EnterpriseAccess.Admission admission;private final String context,project,task;
        private final TaskPlanDraftSchema.Original original;private final AgentPublicationAuthority.VerifiedPublication publication;
        private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final Snapshot snapshot;private JsonNode originalEvidence;
        private Verified(EnterpriseAccess.Admission admission,String context,String project,String task,TaskPlanDraftSchema.Original original,
                AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,Snapshot snapshot) {
            this.admission=admission;this.context=context;this.project=project;this.task=task;this.original=original;
            this.publication=publication;this.policy=policy;this.snapshot=snapshot;
        }
        void requireUnchanged()throws Exception { if(!snapshot.digest().equals(inspect(admission,context,project,task,original,publication,policy,false).digest()))fail(ErrorCode.SCOPE_STALE); }
        EnterpriseAccess.Admission admission(){return admission;} String project(){return project;}String task(){return task;}String context(){return context;}
        TaskPlanDraftSchema.Original original(){return original;}Fixed fixedTask(){return snapshot.task();}
        TemplateApplicabilityProtocol.TemplateView template(){return snapshot.template().view();}
        JsonNode evidence(){return (originalEvidence==null?snapshot.evidence():originalEvidence).deepCopy();}String digest(){return originalEvidence==null?snapshot.digest():ThrowingDigest.of(schema,originalEvidence);}
        AgentExecutionPolicyProtocol.NodeBinding node(){return snapshot.node();}
        String classification(){return identities.classification();}
        List<RunTokenAuthoritySchema.Permission> permissions(){return snapshot.delegated();}
        AgentExecutionPolicyProtocol.Worker worker(){return snapshot.worker();}
        AgentExecutionPolicyProtocol.Definition policyDefinition(){return policy.definition();}
        JsonNode retention(){return evidence().required("retention").deepCopy();}
        AgentReleaseProtocol.Manifest releaseManifest(){return snapshot.releaseManifest();}
        List<SourceReadProtocol.VerifiedSource> sources(){return snapshot.sources();}
    }
    private JsonNode dependencyVersions(EnterpriseAccess.Admission a,TaskProtocol.Head head,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,
            List<SourceReadProtocol.VerifiedSource> sources,TaskPlanDraftSchema.Start request,String retentionRevision)throws Exception {
        var roots=new TreeSet<String>(publication.dependencyVersions());roots.add(retentionRevision);roots.addAll(policy.dependencyVersions());
        roots.addAll(List.of(policy.versionId(),request.enterpriseBudget().version_id(),request.parentBudget().version_id()));
        roots.addAll(List.of(head.taskRevision(),head.templateVersion(),head.profileRevision(),head.snapshotId(),head.agentReleaseId()));
        for(var source:sources){roots.add(source.source().sourceVersion());roots.add(source.rights().rightsRevision());
            if(source.selection()!=null)roots.addAll(List.of(source.selection().parseVersion(),source.selection().indexVersion(),source.selection().approvalRevision(),source.selection().bindingRevision()));}
        var versions=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();var edges=new HashSet<SourceReadProtocol.ParentRow>();
        for(String root:roots){var graph=lineage.inspect(root,limits.limits());for(var item:graph.versions().entrySet()){
            var old=versions.putIfAbsent(item.getKey(),item.getValue());if(old!=null&&!old.equals(item.getValue()))bad();
        }edges.addAll(graph.graph().edgeSet());if(versions.size()>limits.maximumVersions()||edges.size()>limits.limits().maximumRelations())fail(ErrorCode.CONTEXT_LIMIT);}
        var domainRows=versionOwners.ownersAcrossDomains(List.copyOf(versions.keySet()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());
        var grouped=domainRows.stream().collect(java.util.stream.Collectors.groupingBy(r->r.owner().versionId()));
        var output=json.object().arrayNode();
        for(var version:versions.values()){
            if(version.projectId()!=null&&!head.projectId().equals(version.projectId()))deny();
            var owners=grouped.getOrDefault(version.id(),List.of());
            // Formal publication governance records have their own verified ledger reader, not a generic domain owner.
            boolean governance=Set.of("AGENT_PUBLICATION_RECORD","EXPERT_QUALIFICATION","EXPERT_EVIDENCE_RECORD").contains(version.entityKind());
            if(!governance){if(owners.size()!=1)bad();var owner=owners.get(0).owner();
                if(!head.tenantId().equals(owner.tenantId())||!Objects.equals(version.projectId(),owner.versionProjectId())||owner.conversationId()!=null
                        ||owner.contentSha256()!=null&&!version.contentSha256().equals(owner.contentSha256()))bad();}
            var item=output.addObject().put("version_id",version.id()).put("entity_kind",version.entityKind()).put("content_sha256",version.contentSha256());
            if(version.projectId()==null)item.putNull("project_id");else item.put("project_id",version.projectId());
        }
        return output;
    }
    private List<RunTokenAuthoritySchema.Permission> permissions(EnterpriseAccess.Admission a,TaskProtocol.Head head,
            AgentExecutionPolicyProtocol.NodeBinding node,AgentExecutionPolicyAuthority.VerifiedPolicy policy,
            List<SourceReadProtocol.VerifiedSource> sources)throws Exception {
        var result=new LinkedHashSet<RunTokenAuthoritySchema.Permission>();
        for(var binding:node.actions()) {
            if("plan.confirm".equals(binding.action()))deny();
            var targets=new ArrayList<PermissionProtocol.Scope>();
            switch(binding.targetSelector()) {
                case "TASK" -> targets.add(new PermissionProtocol.Scope(head.tenantId(),head.projectId(),"TASK",head.taskId()));
                case "PROJECT" -> targets.add(new PermissionProtocol.Scope(head.tenantId(),head.projectId(),"PROJECT",head.projectId()));
                case "SELECTED_SOURCE" -> sources.forEach(s->targets.add(new PermissionProtocol.Scope(head.tenantId(),s.source().ownerProjectId(),"SOURCE",s.source().sourceId())));
                case "FIXED_VERSION", "CURRENT_EXECUTION_POLICY" -> {
                    var fixed="CURRENT_EXECUTION_POLICY".equals(binding.targetSelector())?new PermissionProtocol.Basis(policy.versionId(),policy.contentSha256()):binding.fixedVersion();
                    if(fixed==null)bad();
                    var rows=versionOwners.ownersAcrossDomains(List.of(fixed.version_id()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());
                    if(rows.size()!=1)bad();var owner=rows.get(0).owner();
                    if(!head.tenantId().equals(owner.tenantId())||!fixed.version_id().equals(owner.versionId())||!fixed.content_sha256().equals(owner.contentSha256()))bad();
                    targets.add(new PermissionProtocol.Scope(owner.tenantId(),owner.resourceProjectId(),owner.resourceKind(),owner.resourceId()));
                }
                case "RUN_REGISTERED_ARTIFACT" -> {
                    // Future artifacts are admitted only through a dedicated run-derived producer capability.
                    // No wildcard/fabricated artifact grant is inserted into the principal at start.
                    continue;
                }
                default -> { deny(); }
            }
            for(var target:targets) {
                if(!binding.resourceKind().equals(target.resource_kind()))bad();
                if(a.planDraftWorker()&&!binding.action().endsWith(".read"))a.planDraftProof().requireOriginalAction(target,binding.action(),binding.purpose());
                else resources.require(a,target,binding.action(),binding.purpose());
                result.add(new RunTokenAuthoritySchema.Permission(target,binding.action(),binding.purpose()));
            }
        }
        if(result.isEmpty())fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        return List.copyOf(result);
    }
    static void current(EnterpriseAccess.Admission a,String context) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()||a.simulationWorker()||a.recoveryWorker()
                ||!a.context().tenant_id().equals(TenantScope.requireCurrent().tenantId())||!a.actorId().equals(TenantScope.requireCurrent().subjectId()))deny();
        if(!a.context().context_revision().equals(context))fail(ErrorCode.CONTEXT_CHANGED);
    }
    private static final class ThrowingDigest {static String of(TaskPlanDraftSchema schema,JsonNode node){try{return schema.hash(node);}catch(Exception e){throw new IllegalStateException(e);}}}
    private static void bad(){fail(ErrorCode.CONTENT_MISMATCH);}private static void deny(){fail(ErrorCode.ACCESS_DENIED);}
    private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
