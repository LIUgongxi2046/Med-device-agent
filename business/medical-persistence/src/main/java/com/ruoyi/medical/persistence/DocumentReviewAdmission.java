package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Actual saved-body, release, rule, budget and current grant admission. No TASK run is reopened. */
public final class DocumentReviewAdmission {
    public static final String PURPOSE="RUN_CREATION";
    private final FormalCapabilityIdentityAuthority capabilities;private final DocumentReviewRules rules;private final ResourceReadAuthority resources;private final SourceReadAuthority sources;
    private final AgentReleaseStore releases;private final AgentReleaseScopeAuthority scopes;private final TemplateDependencyPolicy limits;
    private final RunCreateBudget budgets;private final PermissionMapper permissions;private final VersionResourceMapper owners;
    private final ArtifactRegistryMapper versions;private final RuntimeRetentionAuthority retention;
    private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
    public DocumentReviewAdmission(DocumentReviewRules rules,ResourceReadAuthority resources,SourceReadAuthority sources,
            AgentReleaseStore releases,AgentReleaseScopeAuthority scopes,TemplateDependencyPolicy limits,RunCreateBudget budgets,
            PermissionMapper permissions,VersionResourceMapper owners,ArtifactRegistryMapper versions,RuntimeRetentionAuthority retention,FormalCapabilityIdentityAuthority capabilities){
        this.capabilities=capabilities;this.rules=rules;this.resources=resources;this.sources=sources;this.releases=releases;this.scopes=scopes;this.limits=limits;
        this.budgets=budgets;this.permissions=permissions;this.owners=owners;this.versions=versions;this.retention=retention;
    }
    public record Selection(String agentReleaseId,PermissionProtocol.Basis executionPolicy,PermissionProtocol.Basis enterpriseBudget,
            PermissionProtocol.Basis parentBudget,String currency){public Selection{WireValues.uuid(agentReleaseId);Objects.requireNonNull(executionPolicy);Objects.requireNonNull(enterpriseBudget);Objects.requireNonNull(parentBudget);if(currency==null||!currency.matches("[A-Z]{3}"))deny();}}
    Verified require(EnterpriseAccess.Admission a,DocumentReviewAuthority.Verified document,Selection selection,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        return new Verified(a,document,selection,publication,policy,inspect(a,document,selection,publication,policy,true));
    }
    Verified requireCurrent(EnterpriseAccess.Admission a,DocumentReviewExecutionService.Job job,DocumentReviewAuthority.Verified document,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        var admitted=json.read(job.admissionJson());var selection=new Selection(text(admitted,"agent_release_id"),basis(admitted.required("execution_policy")),basis(admitted.required("enterprise_budget")),basis(admitted.required("parent_budget")),text(admitted,"currency"));
        var snapshot=inspect(a,document,selection,publication,policy,false);
        if(!job.tenant().equals(a.context().tenant_id())||!job.actor().equals(a.actorId())||!job.document().equals(document.header().documentId())||!job.documentRevision().equals(document.header().documentRevision())||!json.read(job.baselineJson()).equals(document.baseline()))stale();
        for(String field:List.of("document_baseline","rules_manifest","dependency_versions","permissions","node_binding","policy_definition","release_manifest","retention","budgets"))if(!admitted.required(field).equals(snapshot.evidence.required(field)))stale();
        return new Verified(a,document,selection,publication,policy,snapshot);
    }
    private Snapshot inspect(EnterpriseAccess.Admission a,DocumentReviewAuthority.Verified document,Selection selected,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,boolean initial)throws Exception {
        if(!TransactionSynchronizationManager.isActualTransactionActive()||!a.context().tenant_id().equals(TenantScope.requireCurrent().tenantId())||!a.actorId().equals(TenantScope.requireCurrent().subjectId()))deny();
        document.requireCurrent(a);var h=document.header();var ruleProof=rules.require(a,document);
        publication.requireConsumer(a,selected.agentReleaseId(),PURPOSE);policy.requireConsumer(a,h.projectId(),PURPOSE);
        if(!selected.executionPolicy().version_id().equals(policy.versionId())||!selected.executionPolicy().content_sha256().equals(policy.contentSha256())||!publication.executionPolicyId().equals(policy.versionId())||!publication.executionPolicySha256().equals(policy.contentSha256()))bad();
        AgentExecutionPolicyAuthority.requireManifest(policy.definition(),publication.manifest().manifest());
        var nodes=policy.definition().bindings().stream().filter(n->"N07".equals(n.nodeKind())).toList();if(nodes.size()!=1)deny();var node=nodes.get(0);
        if(node.modelVersion()==null||node.tokenizerVersion()==null)deny();policy.requireNode("N07",node.roleVersion(),policy.definition().worker().serviceUri(),policy.definition().worker().audience());
        var release=releases.read(a,selected.agentReleaseId(),PURPOSE);
        var releaseScope=scopes.require(release,new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"TASK",h.taskId()),"task.run",h.classification(),limits.limits());
        if(!publication.manifest().manifest().equals(release.content().manifest()))bad();
        var selectedSources=new ArrayList<SourceReadProtocol.VerifiedSource>();for(var requirement:document.bodyIntent().sources())selectedSources.add(sources.require(a,requirement));
        PublishedTaskCreateAdmission.requireSelection(policy.definition(),selectedSources.stream().map(s->new PermissionProtocol.Basis(s.source().sourceVersion(),s.source().originalSha256())).toList());
        var allowed=new LinkedHashSet<RunTokenAuthoritySchema.Permission>();
        for(var binding:node.actions()){
            var targets=new ArrayList<PermissionProtocol.Scope>();
            switch(binding.targetSelector()){
                case "TASK"->targets.add(new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"TASK",h.taskId()));
                case "PROJECT"->targets.add(new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"PROJECT",h.projectId()));
                case "SELECTED_SOURCE"->selectedSources.forEach(s->targets.add(new PermissionProtocol.Scope(h.tenantId(),s.source().ownerProjectId(),"SOURCE",s.source().sourceId())));
                case "CURRENT_EXECUTION_POLICY","FIXED_VERSION"->{var pin="CURRENT_EXECUTION_POLICY".equals(binding.targetSelector())?selected.executionPolicy():binding.fixedVersion();if(pin==null)deny();var owned=owners.ownersAcrossDomains(List.of(pin.version_id()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());if(owned.size()!=1)deny();var owner=owned.get(0).owner();if(!h.tenantId().equals(owner.tenantId())||!pin.content_sha256().equals(owner.contentSha256()))bad();targets.add(new PermissionProtocol.Scope(h.tenantId(),owner.resourceProjectId(),owner.resourceKind(),owner.resourceId()));}
                case "RUN_REGISTERED_ARTIFACT"->{continue;}
                default->throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            }
            for(var target:targets){if(!target.resource_kind().equals(binding.resourceKind()))bad();if(Set.of("document.write","document.adopt","review.approve","plan.confirm").contains(binding.action()))deny();if(a.documentReviewWorker())a.documentReviewProof().requireOriginalAction(target,binding.action(),binding.purpose());else resources.require(a,target,binding.action(),binding.purpose());allowed.add(new RunTokenAuthoritySchema.Permission(target,binding.action(),binding.purpose()));}
        }
        // N07 C07 permission derives only from the selected fixed model, the published role's
        // exact tool scope, and the initiator's current grant. V1 policy selectors are unchanged.
        var modelOwners=owners.ownersAcrossDomains(List.of(node.modelVersion().version_id()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());
        if(modelOwners.size()!=1)deny();var modelOwner=modelOwners.get(0).owner();
        if(!h.tenantId().equals(modelOwner.tenantId())||!"CONFIGURATION".equals(modelOwner.resourceKind())||!node.modelVersion().content_sha256().equals(modelOwner.contentSha256())||modelOwner.resourceProjectId()!=null&&!h.projectId().equals(modelOwner.resourceProjectId()))deny();
        var modelPermission=new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(h.tenantId(),modelOwner.resourceProjectId(),"CONFIGURATION",modelOwner.resourceId()),"model.send",policy.definition().businessPurpose());
        var role=capabilities.require(a,node.roleVersion(),"ROLE",h.projectId(),PURPOSE);var roleScope=role.canonicalDefinition().required("request").required("tool_scope");
        boolean roleAllows=false;for(var permission:roleScope.required("permissions"))if(permission.equals(json.tree(modelPermission)))roleAllows=true;if(!roleAllows)deny();
        if(a.documentReviewWorker())a.documentReviewProof().requireOriginalAction(modelPermission.scope(),modelPermission.action(),modelPermission.purpose());else resources.require(a,modelPermission.scope(),modelPermission.action(),modelPermission.purpose());
        role.requireUnchanged();allowed.add(modelPermission);
        // Fixed private N07 target capability comes only from the physically verified saved-body authority, not a new interpretation of an old published role.
        for(String action:List.of("document.read","document.check")){var target=new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"DOCUMENT",h.documentId());resources.require(a,target,action,"DOCUMENT_BODY");allowed.add(new RunTokenAuthoritySchema.Permission(target,action,"DOCUMENT_BODY"));}
        var budgetPins=json.tree(List.of());var array=(com.fasterxml.jackson.databind.node.ArrayNode)budgetPins;
        for(var basis:List.of(selected.enterpriseBudget(),selected.parentBudget())){
            var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("projectId",h.projectId());values.put("budgetRevision",basis.version_id());
            var row=initial?budgets.require(a,values,permissions.databaseTime(),selected.currency()):budgets.requireWithinBounds(a,values,permissions.databaseTime(),selected.currency());
            if(!basis.content_sha256().equals(text(row,"content_sha256"))||basis.equals(selected.enterpriseBudget())&&!row.required("project_id").isNull())bad();
            var pin=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();for(String field:List.of("tenant_id","project_id","budget_id","budget_revision","content_sha256","approval","currency","schema_version","schema_manifest"))pin.set(field,row.required(field));array.add(pin);
        }
        var retained=retention.requireForRun(a,h.projectId(),h.classification(),PURPOSE);
        var dependencyIds=new TreeSet<>(publication.dependencyVersions());dependencyIds.addAll(policy.dependencyVersions());dependencyIds.addAll(List.of(h.documentRevision(),h.bodyArtifactId(),h.taskRevision(),h.planRevision(),h.templateVersion(),h.snapshotId(),selected.enterpriseBudget().version_id(),selected.parentBudget().version_id(),retained.policyRevision()));
        document.bodyIntent().sources().forEach(s->{dependencyIds.add(s.sourceVersion());dependencyIds.add(s.rightsRevision());if(s.parseVersion()!=null)dependencyIds.add(s.parseVersion());});dependencyIds.addAll(ruleProof.ruleIds());
        var dependencies=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        for(String id:dependencyIds){var rows=versions.version(id);if(rows.size()!=1)bad();var row=rows.get(0);if(row.projectId()!=null&&!h.projectId().equals(row.projectId()))deny();dependencies.addObject().put("version_id",id).put("content_sha256",row.contentSha256());}
        var evidence=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();evidence.set("document_baseline",document.baseline());evidence.set("rules_manifest",ruleProof.manifest());evidence.set("dependency_versions",dependencies);evidence.set("permissions",json.tree(allowed));evidence.set("node_binding",json.tree(node));evidence.set("policy_definition",json.tree(policy.definition()));evidence.set("release_manifest",json.tree(publication.manifest().manifest()));evidence.set("budgets",budgetPins);
        evidence.set("retention",json.tree(Map.of("policy_revision",retained.policyRevision(),"content_sha256",retained.contentSha256(),"terms",retained.terms())));
        ruleProof.requireCurrent(a);retained.requireUnchanged();scopes.requireUnchanged(releaseScope);publication.requireUnchanged();policy.requireUnchanged();document.requireCurrent(a);
        return new Snapshot(ruleProof,List.copyOf(allowed),node,evidence,json.hash(evidence));
    }
    private record Snapshot(DocumentReviewRules.Verified rules,List<RunTokenAuthoritySchema.Permission> permissions,AgentExecutionPolicyProtocol.NodeBinding node,JsonNode evidence,String hash){}
    public final class Verified {
        private final EnterpriseAccess.Admission admission;private final DocumentReviewAuthority.Verified document;private final Selection selected;private final AgentPublicationAuthority.VerifiedPublication publication;private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final Snapshot snapshot;
        private Verified(EnterpriseAccess.Admission a,DocumentReviewAuthority.Verified d,Selection s,AgentPublicationAuthority.VerifiedPublication p,AgentExecutionPolicyAuthority.VerifiedPolicy policy,Snapshot snapshot){admission=a;document=d;selected=s;publication=p;this.policy=policy;this.snapshot=snapshot;}
        public void requireUnchanged()throws Exception{if(!snapshot.hash.equals(inspect(admission,document,selected,publication,policy,false).hash))stale();}
        public EnterpriseAccess.Admission admission(){return admission;}public DocumentReviewAuthority.Verified document(){return document;}public DocumentReviewRules.Verified rules(){return snapshot.rules;}
        public JsonNode evidence(){return snapshot.evidence.deepCopy();}public List<RunTokenAuthoritySchema.Permission> permissions(){return snapshot.permissions;}public AgentExecutionPolicyProtocol.NodeBinding node(){return snapshot.node;}public AgentExecutionPolicyProtocol.Worker worker(){return policy.definition().worker();}
        public AgentPublicationAuthority.VerifiedPublication publication(){return publication;}public AgentExecutionPolicyAuthority.VerifiedPolicy policy(){return policy;}public PermissionProtocol.Basis enterpriseBudget(){return selected.enterpriseBudget();}public PermissionProtocol.Basis parentBudget(){return selected.parentBudget();}public String classification(){return document.header().classification();}public Selection selection(){return selected;}
    }
    private static PermissionProtocol.Basis basis(JsonNode n){return new PermissionProtocol.Basis(text(n,"version_id"),text(n,"content_sha256"));}
    private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
