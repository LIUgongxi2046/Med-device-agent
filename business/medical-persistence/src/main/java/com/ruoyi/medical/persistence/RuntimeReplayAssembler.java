package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

/** Produces the existing complete RUN_ADMISSION contract from real fixed inputs.
 * The result proves fixed bytes and current input checks, not execution, a physical
 * object, a step, a model result, or a workflow acknowledgement. */
@Component
public final class RuntimeReplayAssembler {
    private static final String PURPOSE = "RUN_CREATION";
    private final PermissionSchema json;
    private final RuntimeReplaySchema replay;
    private final SchemaCatalogue catalogue;
    private final TaskMapper tasks;
    private final TaskInputSchema inputs;
    private final ScopeSnapshotSchema snapshots;
    private final TemplateApplicabilityService templates;
    private final RunCreateBudget budgets;
    private final PermissionMapper permissions;
    private final SourceReadAuthority sources;
    private final DependencyLineage lineage;
    private final TemplateDependencyPolicy limits;
    private final ResourceReadAuthority resources;
    private final RunTokenAuthoritySchema authority;

    public RuntimeReplayAssembler(PermissionSchema json, RuntimeReplaySchema replay, TaskMapper tasks,
            TaskInputSchema inputs, ScopeSnapshotSchema snapshots, TemplateApplicabilityService templates,
            RunCreateBudget budgets, PermissionMapper permissions, SourceReadAuthority sources,
            DependencyLineage lineage, TemplateDependencyPolicy limits, ResourceReadAuthority resources,
            RunTokenAuthoritySchema authority) throws Exception {
        this.json=json; this.replay=replay; this.tasks=tasks; this.inputs=inputs; this.snapshots=snapshots;
        this.templates=templates; this.budgets=budgets; this.permissions=permissions; this.sources=sources;
        this.lineage=lineage; this.limits=limits; this.resources=resources; this.authority=authority; this.catalogue=SchemaCatalogue.fromClasspath();
    }

    public Assembly assemble(EnterpriseAccess.Admission admission, TaskService.VerifiedTask task,
            TaskPlanWriteProtocol.View plan, AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy, RuntimeRetentionAuthority.Verified retention,
            RunCreateSchema.Create request, String runId, String principalId, JsonNode scope, JsonNode principal) throws IOException {
        return assemble(admission,task,plan,publication,policy,retention,request,runId,principalId,scope,principal,json.object(),List.of());
    }

    /** Extra witnesses are data from a separately verified current authority, not
     * permissions. Every extra immutable version is checked against real registry
     * lineage; the caller must also retain/recheck its private authorization proof. */
    public Assembly assemble(EnterpriseAccess.Admission admission, TaskService.VerifiedTask task,
            TaskPlanWriteProtocol.View plan, AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy, RuntimeRetentionAuthority.Verified retention,
            RunCreateSchema.Create request, String runId, String principalId, JsonNode scope, JsonNode principal,
            JsonNode extraAuthorizations, List<PermissionProtocol.Basis> extraDependencyVersions) throws IOException {
        return assembleSigned(admission,task,plan,publication,policy,retention,request,runId,principalId,scope,principal,extraAuthorizations,extraDependencyVersions,null);
    }
    Assembly assembleSigned(EnterpriseAccess.Admission admission, TaskService.VerifiedTask task,
            TaskPlanWriteProtocol.View plan, AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy, RuntimeRetentionAuthority.Verified retention,
            RunCreateSchema.Create request, String runId, String principalId, JsonNode scope, JsonNode principal,
            JsonNode extraAuthorizations, List<PermissionProtocol.Basis> extraDependencyVersions, TaskSignedContinuationInputs.Verified signed) throws IOException {
        var call=new Inputs(admission,task,plan,publication,policy,retention,request,runId,principalId,scope,principal,
                extraAuthorizations,extraDependencyVersions,signed);
        var result=build(call);
        return new Assembly(this,call,result);
    }

    private record Inputs(EnterpriseAccess.Admission admission, TaskService.VerifiedTask task,
            TaskPlanWriteProtocol.View plan, AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy, RuntimeRetentionAuthority.Verified retention,
            RunCreateSchema.Create request, String runId, String principalId, JsonNode scope, JsonNode principal,
            JsonNode extraAuthorizations,List<PermissionProtocol.Basis> extraDependencyVersions,TaskSignedContinuationInputs.Verified signed) {
        Inputs {
            scope=scope.deepCopy(); principal=principal.deepCopy();
            extraAuthorizations=Objects.requireNonNull(extraAuthorizations).deepCopy();
            if(!extraAuthorizations.isObject())mismatch();
            extraDependencyVersions=List.copyOf(extraDependencyVersions);
        }
    }
    private record Built(SchemaCatalogue.ValidatedPayload payload, RuntimeReplaySchema.VerifiedInputs inputs) { }
    public static final class Assembly {
        private final RuntimeReplayAssembler owner; private final Inputs call; private final Built original;
        private Assembly(RuntimeReplayAssembler owner,Inputs call,Built original){this.owner=owner;this.call=call;this.original=original;}
        public SchemaCatalogue.ValidatedPayload replayManifest(){return original.payload();}
        public RuntimeReplaySchema.VerifiedInputs inputs(){return original.inputs();}
        public void requireUnchanged() throws IOException {
            if(!original.payload().equals(owner.build(call).payload()))fail(ErrorCode.SCOPE_STALE);
        }
    }

    private Built build(Inputs call) throws IOException {
        try {
            var a=call.admission(); var head=call.task().head(); var request=call.request();
            WireValues.uuid(call.runId()); WireValues.uuid(call.principalId());
            if(a.simulationWorker() || !a.context().tenant_id().equals(head.tenantId())
                    || !request.task_revision().equals(head.taskRevision()) || !request.plan_revision().equals(head.planRevision())
                    || !request.snapshot_id().equals(head.snapshotId()) || !"CONFIRMED".equals(call.plan().status()))mismatch();
            var original=inputs.create(call.task().input().requestJson().getBytes(StandardCharsets.UTF_8));
            if(!original.request_sha256().equals(call.task().input().requestHash()))mismatch();
            // A previous document needs a real fixed prior-input producer; never silently discard it.
            if(call.signed()==null)requireFirstRunInputs(original.request());else call.signed().require(a,call.task());
            var stored=one(tasks.snapshot(a.actorId(),head.projectId(),head.snapshotId()));
            if(!head.tenantId().equals(stored.tenantId()) || !head.projectId().equals(stored.projectId())
                    || !head.snapshotId().equals(stored.snapshotId()) || !stored.manifestHash().equals(stored.registryHash()))mismatch();
            var snapshot=snapshots.require(stored.manifest(),stored.manifestHash(),head.tenantId(),head.projectId(),head.snapshotId(),
                    head.profileRevision(),stored.authEpoch(),stored.governanceEpoch(),original.request().scope_selection(),
                    tasks.members(a.actorId(),head.projectId(),head.snapshotId()));
            var policy=call.policy(); policy.requireConsumer(a,head.projectId(),PURPOSE);
            var definition=policy.definition();
            PublishedTaskCreateAdmission.requireSelection(definition,snapshot.members().stream().map(m ->
                    new PermissionProtocol.Basis(m.source_version(),m.original_sha256())).toList());
            if(snapshot.scope_selection().networkEnabled() || !limits.environment().equals(definition.environment())
                    || !limits.region().equals(definition.region()) || "SIMULATION".equals(definition.environment()))fail(ErrorCode.ACCESS_DENIED);
            var checkedScope=authority.scopeManifest(json.canonical(call.scope()),head.tenantId(),head.projectId(),a.actorId(),call.runId(),"TASK");
            var checkedPrincipal=authority.principalManifest(json.canonical(call.principal()),head.tenantId(),head.projectId(),a.actorId(),call.runId(),call.principalId(),checkedScope);
            var entryGrant=resources.require(a,new PermissionProtocol.Scope(head.tenantId(),head.projectId(),"TASK",head.taskId()),"task.run",PURPOSE);
            var principalWitnesses=json.object().arrayNode();
            for(var permission:authority.permissions(checkedPrincipal)) {
                var grant=resources.require(a,permission.scope(),permission.action(),permission.purpose());
                var witness=principalWitnesses.addObject();witness.set("permission",json.tree(permission));
                witness.set("grant",AgentPublicationWitness.tree(grant));
            }
            var sourceWitnesses=json.object().arrayNode();
            for(var member:snapshot.members()) {
                var source=sources.require(a,new SourceReadProtocol.ReadRequirement(member.source_version(),member.rights_revision(),
                        head.projectId(),head.snapshotId(),member.parse_version(),null,SourceReadProtocol.Use.FORMAL,
                        definition.businessPurpose(),definition.environment(),definition.region(),null));
                ScopeSnapshotSchema.requireSource(member,source);
                if(source.selection()==null || !member.index_version().equals(source.selection().indexVersion())
                        || !member.approval_revision().equals(source.selection().approvalRevision())
                        || !member.binding_revision().equals(source.selection().bindingRevision()))mismatch();
                sourceWitnesses.add(AgentPublicationWitness.tree(source));
            }
            var target=templates.requireTarget(a,head.projectId(),head.profileRevision(),call.task().input().documentType(),head.templateVersion(),PURPOSE);
            var budgetValues=new HashMap<String,Object>(); budgetValues.put("userId",a.actorId());
            budgetValues.put("projectId",head.projectId()); budgetValues.put("budgetRevision",request.budget_revision());
            var publication=call.publication(); publication.requireUnchanged();
            var release=publication.manifest();
            if(!head.agentReleaseId().equals(release.manifest().releaseId()))mismatch();
            AgentExecutionPolicyAuthority.requireManifest(definition,release.manifest());
            if(!policy.versionId().equals(publication.executionPolicyId()) || !policy.contentSha256().equals(publication.executionPolicySha256()))mismatch();
            var budget=budgets.require(a,budgetValues,Objects.requireNonNull(permissions.databaseTime()),release.manifest().limits().currency());
            var retention=call.retention(); retention.requireUnchanged();
            var profile=json.object().put("profile_revision",head.profileRevision()).put("content_sha256",target.profile().sha256());
            profile.set("attributes",json.tree(target.profile().attributes())); profile.set("authority_refs",json.tree(target.profile().authorityRefs()));
            var template=json.object().put("template_version",head.templateVersion()).put("content_sha256",target.view().template().content_sha256());
            template.set("content",json.tree(target.view().template().content()));
            var budgetApproval=json.object().put("budget_revision",request.budget_revision()).put("content_sha256",budget.required("content_sha256").asText());
            budgetApproval.set("approval",budget.required("approval").deepCopy());
            var retentionValue=json.object().put("policy_revision",retention.policyRevision()).put("content_sha256",retention.contentSha256());
            retentionValue.set("terms",retention.terms());
            var witnesses=json.object(); witnesses.set("publication",publication.authorizations()); witnesses.set("execution_policy",policy.authorizations());
            witnesses.set("sources",sourceWitnesses); witnesses.set("template",inputs.read(target.dependencyAuthorizations()));
            witnesses.set("run_creation_grant",AgentPublicationWitness.tree(entryGrant));
            witnesses.set("principal_permissions",principalWitnesses);
            if(!call.extraAuthorizations().isEmpty())witnesses.set("additional_authorizations",call.extraAuthorizations().deepCopy());
            witnesses.set("template_grant",AgentPublicationWitness.tree(target.grant())); witnesses.set("retention",AgentPublicationWitness.tree(retention.authorizations()));
            // Budget approval is fixed; current usage was checked, but is not replay input and can change between rechecks.
            witnesses.put("budget_approval_sha256",budget.required("content_sha256").asText());
            var roots=new TreeSet<String>(publication.dependencyVersions()); roots.addAll(policy.dependencyVersions());
            roots.addAll(List.of(head.taskRevision(),head.planRevision(),head.snapshotId(),head.agentReleaseId(),head.profileRevision(),
                    head.templateVersion(),request.budget_revision(),policy.versionId(),retention.policyRevision()));
            release.dependencies().forEach(v->roots.add(v.versionId())); release.artifacts().forEach(v->roots.add(v.artifactId()));
            roots.addAll(snapshot.scope_selection().collectionVersions());
            target.profile().authorityRefs().forEach(v->roots.add(v.version_id()));
            target.view().template().dependency_refs().forEach(v->roots.add(v.version_id()));
            for(var member:snapshot.members())roots.addAll(List.of(member.source_version(),member.parse_version(),member.index_version(),
                    member.approval_revision(),member.binding_revision(),member.rights_revision()));
            for(var version:call.extraDependencyVersions()) {WireValues.uuid(version.version_id());WireValues.hash(version.content_sha256());roots.add(version.version_id());}
            var graph=readGraph(roots);
            requireExtraDependencies(call.extraDependencyVersions(),graph.versions());
            var manifest=compose(json,head.tenantId(),head.projectId(),a.actorId(),head.taskId(),call.runId(),call.principalId(),
                    request,original.request(),json.tree(call.plan()),json.tree(snapshot),inputs.read(release.canonicalJson()),release.sha256(),
                    publication.approvalSha256(),json.tree(definition),new PermissionProtocol.Basis(policy.versionId(),policy.contentSha256()),
                    profile,template,budgetApproval,modelBindings(policy.authorizations()),graph.versions(),graph.edges(),call.scope(),call.principal(),
                    retentionValue,a.context().context_revision(),a.context().auth_epoch(),a.context().governance_epoch(),witnesses);
            if(call.signed()!=null){((com.fasterxml.jackson.databind.node.ArrayNode)manifest.required("prior_inputs")).add(call.signed().prior());TaskSignedContinuationInputs.replayBody(manifest);}
            var payload=catalogue.bindPublished(RuntimeReplaySchema.REF,catalogue.schemaDigest("replay-manifest"),json.canonical(manifest).getBytes(StandardCharsets.UTF_8));
            var checked=replay.read(payload.canonicalBytes(),DigestUtils.sha256Hex(payload.canonicalBytes()),payload.schemaSha256());
            templates.requireUnchanged(a,target); publication.requireUnchanged(); policy.requireUnchanged(); retention.requireUnchanged();
            return new Built(payload,checked);
        } catch(RuntimeException e) {throw e;}
        catch(IOException e) {throw e;}
        catch(Exception e) {throw new IOException("RUN_REPLAY_ASSEMBLY_FAILED",e);}
    }

    private record GraphInputs(List<ArtifactRegistryProtocol.VersionRow> versions,List<SourceReadProtocol.ParentRow> edges) { }
    private GraphInputs readGraph(Set<String> roots) {
        var versions=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();
        var edges=new TreeSet<SourceReadProtocol.ParentRow>(Comparator.comparing(SourceReadProtocol.ParentRow::consumerVersionId)
                .thenComparing(SourceReadProtocol.ParentRow::upstreamVersionId).thenComparing(SourceReadProtocol.ParentRow::relationKind));
        if(roots.size()>limits.maximumVersions())fail(ErrorCode.CONTEXT_LIMIT);
        for(String root:roots) {
            var graph=lineage.inspect(root,limits.limits());
            for(var row:graph.versions().values()) {var previous=versions.putIfAbsent(row.id(),row);if(previous!=null&&!previous.equals(row))mismatch();}
            edges.addAll(graph.graph().edgeSet());
            if(versions.size()>limits.maximumVersions() || edges.size()>limits.limits().maximumRelations())fail(ErrorCode.CONTEXT_LIMIT);
        }
        return new GraphInputs(List.copyOf(versions.values()),List.copyOf(edges));
    }

    static void requireExtraDependencies(List<PermissionProtocol.Basis> requested,List<ArtifactRegistryProtocol.VersionRow> actual) {
        var byId=new HashMap<String,ArtifactRegistryProtocol.VersionRow>();
        for(var version:actual)if(byId.put(version.id(),version)!=null)mismatch();
        var unique=new HashSet<String>();
        for(var expected:requested) {
            WireValues.uuid(expected.version_id());WireValues.hash(expected.content_sha256());
            if(!unique.add(expected.version_id()))mismatch();
            var registered=byId.get(expected.version_id());
            if(registered==null || !expected.content_sha256().equals(registered.contentSha256()))mismatch();
        }
    }

    static void requireFirstRunInputs(TaskProtocol.Create input) {
        if(input.based_on_document_revision()!=null)fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
    static List<JsonNode> modelBindings(JsonNode policyAuthorizations) {
        var values=new TreeMap<String,JsonNode>(); var runtime=policyAuthorizations.required("dependencies").required("runtime");
        if(!runtime.isArray())mismatch();
        for(var node:runtime) {
            if(!node.has("model_revision"))continue;
            var binding=node.required("model_binding");
            String id=binding.required("model_config_revision").asText();
            if(!id.equals(node.required("model_revision").asText()))mismatch();
            var prior=values.putIfAbsent(id,binding.deepCopy());if(prior!=null&&!prior.equals(binding))mismatch();
        }
        if(values.isEmpty())fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        return List.copyOf(values.values());
    }

    /** Deterministic structural assembly only, package-private for isolated schema tests.
     * This function is deliberately not an admission or a proof factory. */
    static ObjectNode compose(PermissionSchema json,String tenant,String project,String actor,String task,String run,String principalId,
            RunCreateSchema.Create request,TaskProtocol.Create input,JsonNode plan,JsonNode snapshot,JsonNode release,String releaseHash,
            String publicationHash,JsonNode policy,PermissionProtocol.Basis policyVersion,JsonNode profile,JsonNode template,JsonNode budget,
            List<JsonNode> models,List<ArtifactRegistryProtocol.VersionRow> versions,List<SourceReadProtocol.ParentRow> edges,
            JsonNode scope,JsonNode principal,JsonNode retention,String context,String auth,String governance,JsonNode witnesses) throws IOException {
        var value=json.object().put("schema_version","1.0.0").put("phase","RUN_ADMISSION")
                .put("tenant_id",tenant).put("project_id",project).put("initiator_id",actor).put("task_id",task)
                .put("run_id",run).put("principal_id",principalId).put("agent_release_id",input.agent_release_id())
                .put("task_revision",request.task_revision()).put("plan_revision",request.plan_revision()).put("snapshot_id",request.snapshot_id())
                .put("budget_revision",request.budget_revision());
        value.set("task_input",json.tree(input)); value.set("plan",plan.deepCopy()); value.set("scope_snapshot",snapshot.deepCopy());
        value.put("scope_snapshot_sha256",DigestUtils.sha256Hex(json.canonical(snapshot)));
        value.set("agent_release",release.deepCopy());value.put("agent_release_sha256",releaseHash);value.put("publication_approval_sha256",publicationHash);
        value.set("execution_policy",policy.deepCopy());value.set("execution_policy_version",json.tree(policyVersion));
        value.set("project_profile",profile.deepCopy());value.set("template",template.deepCopy());value.set("budget_approval",budget.deepCopy());
        var modelValues=value.putArray("model_bindings");models.forEach(m->modelValues.add(m.deepCopy()));
        var dependencies=value.putArray("dependency_versions");
        for(var row:versions) {var v=dependencies.addObject().put("version_id",row.id()).put("content_sha256",row.contentSha256()).put("entity_kind",row.entityKind());
            if(row.projectId()==null)v.putNull("project_id");else v.put("project_id",row.projectId());}
        value.set("dependency_edges",json.tree(edges)); value.set("scope_manifest",scope.deepCopy());value.set("principal_manifest",principal.deepCopy());
        value.set("retention",retention.deepCopy());String canonical=json.canonical(witnesses);
        value.set("authorizations",json.object().put("context_revision",context).put("auth_epoch",auth).put("governance_epoch",governance)
                .put("purpose",PURPOSE).put("canonical_witnesses",canonical).put("witnesses_sha256",DigestUtils.sha256Hex(canonical)));
        value.putArray("prior_inputs");value.put("future_execution_records","NOT_YET_PRODUCED");
        for(String field:List.of("locale","timezone","training_allowed"))value.set(field,policy.required(field).deepCopy());
        value.put("provider_reexecution",models.stream().anyMatch(m->"UNKNOWN".equals(m.required("model_version").asText()))
                ?"ORIGINAL_VERSION_UNAVAILABLE":"EXACT_VERSION_AVAILABLE");
        return value;
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)mismatch();return rows.get(0);}
    private static void mismatch(){fail(ErrorCode.CONTENT_MISMATCH);}
    private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
