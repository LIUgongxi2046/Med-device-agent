package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;

/** Exact approved-node permission resolution. Authored DRAFT identity alone is never execution authority.
 * No project-to-resource wildcard or future artifact permission is inferred here. */
@Component
public final class FormalTaskPermissionAuthority {
    private final FormalCapabilityIdentityAuthority capabilities;
    private final VersionResourceMapper owners;
    private final TaskMapper tasks;
    private final ScopeSnapshotSchema snapshots;
    private final TaskInputSchema taskInputs;
    private final ResourceReadAuthority resources;
    private final RunTokenAuthoritySchema schema;
    private final PermissionSchema json;
    private final org.springframework.beans.factory.ObjectProvider<TaskDelegationService> delegations;
    private final org.springframework.beans.factory.ObjectProvider<ModelEligibilityAuthority> models;

    public FormalTaskPermissionAuthority(FormalCapabilityIdentityAuthority capabilities, VersionResourceMapper owners,
            TaskMapper tasks, ScopeSnapshotSchema snapshots, TaskInputSchema taskInputs,
            ResourceReadAuthority resources, RunTokenAuthoritySchema schema, PermissionSchema json,
            org.springframework.beans.factory.ObjectProvider<TaskDelegationService> delegations) {
        this(capabilities,owners,tasks,snapshots,taskInputs,resources,schema,json,delegations,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public FormalTaskPermissionAuthority(FormalCapabilityIdentityAuthority capabilities,VersionResourceMapper owners,
            TaskMapper tasks,ScopeSnapshotSchema snapshots,TaskInputSchema taskInputs,ResourceReadAuthority resources,
            RunTokenAuthoritySchema schema,PermissionSchema json,org.springframework.beans.factory.ObjectProvider<TaskDelegationService> delegations,
            org.springframework.beans.factory.ObjectProvider<ModelEligibilityAuthority> models) {
        this.capabilities=capabilities; this.owners=owners; this.tasks=tasks; this.snapshots=snapshots;
        this.taskInputs=taskInputs; this.resources=resources; this.schema=schema; this.json=json; this.delegations=delegations; this.models=models;
    }

    public Verified require(EnterpriseAccess.Admission admission, TaskService.VerifiedTask task,
            AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy, String runId, String principalId, String budgetRevision) throws Exception {
        WireValues.uuid(runId); WireValues.uuid(principalId);
        var head=task.head(); String tenant=admission.context().tenant_id(), project=head.projectId();
        if (!tenant.equals(head.tenantId()) || admission.simulationWorker()) deny();
        policy.requireConsumer(admission, project, "RUN_CREATION"); publication.requireUnchanged();
        if (!publication.executionPolicyId().equals(policy.versionId())
                || !publication.executionPolicySha256().equals(policy.contentSha256())) mismatch();
        AgentExecutionPolicyAuthority.requireManifest(policy.definition(), publication.manifest().manifest());
        var row=one(tasks.snapshot(admission.actorId(),project,head.snapshotId()));
        var input=taskInputs.create(task.input().requestJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var selected=snapshots.require(row.manifest(),row.manifestHash(),tenant,project,head.snapshotId(),
                head.profileRevision(),row.authEpoch(),row.governanceEpoch(),input.request().scope_selection(),
                tasks.members(admission.actorId(),project,head.snapshotId()));
        var sourceRefs=selected.members().stream().map(m->new PermissionProtocol.Basis(m.source_version(),m.original_sha256())).toList();
        PublishedTaskCreateAdmission.requireSelection(policy.definition(),sourceRefs);
        PublishedTaskCreateAdmission.requireRequestScope(input);
        var policyScope=new PermissionProtocol.Scope(tenant,policy.definition().projectId(),"CONFIGURATION",policy.policyId());
        var resolved=new LinkedHashMap<String,List<RunTokenAuthoritySchema.Permission>>();
        var roleProofs=new ArrayList<FormalCapabilityIdentityAuthority.VerifiedCapability>();
        var witnesses=json.object().arrayNode();
        var delegationWitnesses=json.object();var delegationRefs=new ArrayList<PermissionProtocol.Basis>();
        var delegatedPermissions=new LinkedHashSet<RunTokenAuthoritySchema.Permission>();
        for(var node:policy.definition().bindings()) {
            var role=capabilities.require(admission,node.roleVersion(),"ROLE",project,"RUN_CREATION");
            var roleScope=schema.read("RoleScope",json.canonical(role.canonicalDefinition().required("request").required("tool_scope")));
            var literal=schema.permissions(roleScope);
            var requested=new LinkedHashSet<RunTokenAuthoritySchema.Permission>();
            for(var binding:node.actions()) {
                var targets=new ArrayList<PermissionProtocol.Scope>();
                switch(binding.targetSelector()) {
                    case "TASK" -> targets.add(new PermissionProtocol.Scope(tenant,project,"TASK",head.taskId()));
                    case "PROJECT" -> targets.add(new PermissionProtocol.Scope(tenant,project,"PROJECT",project));
                    case "CURRENT_EXECUTION_POLICY" -> targets.add(policyScope);
                    case "SELECTED_SOURCE" -> {
                        for(var source:selected.members()) targets.add(owner(tenant,project,
                                new PermissionProtocol.Basis(source.source_version(),source.original_sha256()),"SOURCE"));
                    }
                    case "FIXED_VERSION" -> targets.add(owner(tenant,project,binding.fixedVersion(),binding.resourceKind()));
                    // No token authority follows from registration. Host bytes require a separate exact 098 proof.
                    case "RUN_REGISTERED_ARTIFACT" -> requested.addAll(registeredArtifactTokenPermissions());
                    default -> deny();
                }
                for(var target:targets) {
                    if(!binding.resourceKind().equals(target.resource_kind())) mismatch();
                    requested.add(new RunTokenAuthoritySchema.Permission(target,binding.action(),binding.purpose()));
                }
            }
            var permitted=new LinkedHashSet<>(literal);
            if(node.modelVersion()!=null || !permitted.containsAll(requested)) {
                var service=delegations.getIfAvailable();if(service==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
                var delegation=service.requireForTask(admission,publication,policy,node.roleVersion(),head.taskId(),runId,budgetRevision);
                var approved=delegation.deriveTaskPermissions();permitted.addAll(approved);delegatedPermissions.addAll(approved);
                if(node.modelVersion()!=null){
                    var authority=models==null?null:models.getIfAvailable();if(authority==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
                    var model=authority.require(admission,node.modelVersion().version_id(),"RUN_CREATION",project);
                    if(!node.modelVersion().content_sha256().equals(model.contentSha256()))mismatch();
                    var target=owner(tenant,project,node.modelVersion(),"CONFIGURATION");
                    if(!model.configId().equals(target.resource_id()))mismatch();
                    model.requireParameters(node.generationParameters());
                    for(var declared:TaskModelNodePermissions.declared(node.modelVersion(),target,delegation.authorizations())){
                        var actual=delegation.deriveModelPermission(model,declared.purpose());if(!declared.equals(actual))mismatch();
                        requested.add(actual);permitted.add(actual);delegatedPermissions.add(actual);
                    }
                    model.requireUnchanged();
                }
                var bounds=publication.manifest().manifest().limits();
                delegation.requireBudget(bounds.currency(),bounds.maxCostAmount(),bounds.maxTotalTokens(),bounds.maxModelRequests());
                delegationWitnesses.set(node.nodeKind(),delegation.authorizations());
                delegationRefs.add(new PermissionProtocol.Basis(delegation.ruleId(),delegation.contentSha256()));
            }
            var exact=narrow(List.copyOf(permitted),List.copyOf(requested));
            if(resolved.put(node.nodeKind(),exact)!=null) mismatch();
            var witness=witnesses.addObject().put("node_kind",node.nodeKind()).put("role_version",node.roleVersion().version_id())
                    .put("role_sha256",node.roleVersion().content_sha256());
            witness.set("permissions",json.tree(exact)); witness.set("role_authorizations",role.authorizations());
            var current=witness.putArray("current_permissions");
            for(var permission:exact) current.add(AgentPublicationWitness.tree(resources.require(admission,permission.scope(),permission.action(),permission.purpose())));
            role.requireUnchanged(); roleProofs.add(role);
        }
        var all=new LinkedHashSet<RunTokenAuthoritySchema.Permission>(); resolved.values().forEach(all::addAll);all.addAll(delegatedPermissions);
        if(all.isEmpty()) deny();
        var scope=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",project)
                .put("initiator_id",admission.actorId()).put("run_id",runId).put("execution_kind","TASK");
        scope.set("resources",json.tree(all.stream().map(RunTokenAuthoritySchema.Permission::scope).distinct().toList()));
        var principal=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",project)
                .put("initiator_id",admission.actorId()).put("run_id",runId).put("principal_id",principalId)
                .put("scope_sha256",schema.sha256(scope));
        principal.putArray("services").addObject().put("service_uri",policy.definition().worker().serviceUri())
                .put("audience",policy.definition().worker().audience());
        principal.set("permissions",json.tree(all));
        schema.scopeManifest(json.canonical(scope),tenant,project,admission.actorId(),runId,"TASK");
        schema.principalManifest(json.canonical(principal),tenant,project,admission.actorId(),runId,principalId,scope);
        publication.requireUnchanged(); policy.requireUnchanged();
        return new Verified(admission,task,publication,policy,runId,principalId,scope,principal,witnesses,resolved,budgetRevision,delegationWitnesses,delegationRefs);
    }

    /** Exact literal containment, not inheritance. A broader project scope does not name a task or artifact. */
    static List<RunTokenAuthoritySchema.Permission> registeredArtifactTokenPermissions() {
        return List.of();
    }

    static List<RunTokenAuthoritySchema.Permission> narrow(List<RunTokenAuthoritySchema.Permission> literal,
            List<RunTokenAuthoritySchema.Permission> requested) {
        if(literal==null || requested==null || requested.isEmpty() || new HashSet<>(literal).size()!=literal.size()
                || new HashSet<>(requested).size()!=requested.size() || !literal.containsAll(requested)) deny();
        return List.copyOf(requested);
    }

    private PermissionProtocol.Scope owner(String tenant,String project,PermissionProtocol.Basis reference,String kind) {
        Objects.requireNonNull(reference);
        var row=one(owners.ownersAcrossDomains(List.of(reference.version_id()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList())).owner();
        if(!tenant.equals(row.tenantId()) || !reference.version_id().equals(row.versionId())
                || row.versionProjectId()!=null&&!project.equals(row.versionProjectId())
                || row.resourceProjectId()!=null&&!project.equals(row.resourceProjectId())
                || !kind.equals(row.resourceKind()) || row.contentSha256()!=null&&!reference.content_sha256().equals(row.contentSha256())
                || row.conversationId()!=null) mismatch();
        return new PermissionProtocol.Scope(tenant,row.resourceProjectId(),kind,row.resourceId());
    }

    public final class Verified {
        private final EnterpriseAccess.Admission admission;
        private final TaskService.VerifiedTask task;
        private final AgentPublicationAuthority.VerifiedPublication publication;
        private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;
        private final String run,principalId,budget;
        private final JsonNode delegationWitnesses;private final List<PermissionProtocol.Basis> delegationRefs;
        private final JsonNode scope,principal,witnesses;
        private final Map<String,List<RunTokenAuthoritySchema.Permission>> nodes;
        private Verified(EnterpriseAccess.Admission admission,TaskService.VerifiedTask task,
                AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,
                String run,String principalId,JsonNode scope,JsonNode principal,JsonNode witnesses,
                Map<String,List<RunTokenAuthoritySchema.Permission>> nodes,String budget,JsonNode delegationWitnesses,List<PermissionProtocol.Basis> delegationRefs) {
            this.admission=admission;this.task=task;this.publication=publication;this.policy=policy;this.run=run;
            this.principalId=principalId;this.scope=scope.deepCopy();this.principal=principal.deepCopy();
            this.witnesses=witnesses.deepCopy();this.nodes=Map.copyOf(nodes);this.budget=budget;this.delegationWitnesses=delegationWitnesses.deepCopy();this.delegationRefs=List.copyOf(new LinkedHashSet<>(delegationRefs));
        }
        public JsonNode delegationAuthorizations(){return delegationWitnesses.deepCopy();}
        public List<PermissionProtocol.Basis> delegationVersions(){return delegationRefs;}
        public JsonNode scopeManifest(){return scope.deepCopy();}
        public JsonNode principalManifest(){return principal.deepCopy();}
        public JsonNode authorizations(){return witnesses.deepCopy();}
        public List<RunTokenAuthoritySchema.Permission> nodePermissions(String nodeKind){
            var result=nodes.get(nodeKind); if(result==null) deny(); return result;
        }
        public void requireUnchanged() throws Exception {
            var fresh=require(admission,task,publication,policy,run,principalId,budget);
            if(!scope.equals(fresh.scope)||!principal.equals(fresh.principal)||!witnesses.equals(fresh.witnesses)||!nodes.equals(fresh.nodes)||!delegationWitnesses.equals(fresh.delegationWitnesses)||!delegationRefs.equals(fresh.delegationRefs))
                throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        }
    }
    private static <T>T one(List<T> values){if(values.size()!=1)mismatch();return values.get(0);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
