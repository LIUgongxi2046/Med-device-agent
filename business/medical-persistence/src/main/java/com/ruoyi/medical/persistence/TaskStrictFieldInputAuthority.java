package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Private N03 input proofs from real publication, producer, graph, target and comparison approvals. */
public final class TaskStrictFieldInputAuthority {
    private final JdbcTemplate db;private final TaskStrictFieldSchema schema;private final PermissionSchema json;
    private final TaskStrictFieldTargetAuthority targets;private final TaskFieldTargetInputCapabilityService capabilities;
    private final TaskRunGraphService graphs;private final TaskResearchCompletionAuthority completions;
    private final StrictComparisonRunScope scopes;private final StrictComparisonPolicyService policies;
    private final FormalCapabilityIdentityAuthority roles;private final FormalRuntimeCapabilityIdentityAuthority tools;
    private final TaskStrictFieldContextService contexts;private final RunTokenAuthoritySchema tokens;private final VersionResourceMapper owners;
    public TaskStrictFieldInputAuthority(DataSource data,TaskStrictFieldSchema schema,PermissionSchema json,TaskStrictFieldTargetAuthority targets,
            TaskFieldTargetInputCapabilityService capabilities,TaskRunGraphService graphs,TaskResearchCompletionAuthority completions,
            StrictComparisonRunScope scopes,StrictComparisonPolicyService policies,FormalCapabilityIdentityAuthority roles,
            FormalRuntimeCapabilityIdentityAuthority tools,RunTokenAuthoritySchema tokens,VersionResourceMapper owners,TaskStrictFieldContextService contexts){this.contexts=contexts;db=new JdbcTemplate(data);db.setQueryTimeout(5);
        this.schema=schema;this.json=json;this.targets=targets;this.capabilities=capabilities;this.graphs=graphs;this.completions=completions;
        this.scopes=scopes;this.policies=policies;this.roles=roles;this.tools=tools;this.tokens=tokens;this.owners=owners;}
    public Prepared prepare(TaskNodeProductionAuthority.Verified production,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskStrictFieldInput proposed)throws Exception{
        schema.validate(proposed);String witness=inspect(production,publication,policy,proposed);return new Prepared(production,publication,policy,proposed,witness);
    }
    public Prepared prepare(TaskNodeProductionAuthority.Verified production,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,String targetArtifact,String targetReceipt,
            PermissionProtocol.Basis capability,PermissionProtocol.Basis decision,PermissionProtocol.Basis tool,List<TaskStrictFieldInput.PolicyEntry> selectedPolicies)throws Exception {
        production.requireUnchanged();var graph=graphs.require(production.admission(),production.runId());var target=targets.require(production,targetArtifact,targetReceipt);
        var binding=one(policy.definition().bindings().stream().filter(n->"N03".equals(n.nodeKind())).toList());
        var predecessors=new ArrayList<TaskNodeInputProtocol.Predecessor>();for(var node:graph.nodes()){
            var c=completions.require(production.admission(),production.runId(),node.stepId());predecessors.add(new TaskNodeInputProtocol.Predecessor(node.stepId(),c.resultArtifactId(),c.resultSha256(),c.resultObjectVersion(),c.resultReceiptSha256(),c.evidenceArtifactId(),c.evidenceSha256(),c.evidenceObjectVersion(),c.evidenceReceiptSha256(),c.completionReceiptSha256()));c.requireUnchanged();}
        String step=UUID.nameUUIDFromBytes((TaskStrictFieldSchema.inputRef(target.manifest().required("target").required("schema_version").asText())+"|"+production.tenantId()+"|"+production.runId()+"|"+targetArtifact).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var prior=db.queryForList("SELECT input_id FROM task_strict_field_input WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",production.tenantId(),production.runId());String inputId=prior.isEmpty()?UUID.randomUUID().toString():str(one(prior),"input_id");
        var replay=production.replay().content();
        var input=new TaskStrictFieldInput(target.manifest().required("target").required("schema_version").asText(),"N03",production.tenantId(),production.projectId(),production.taskId(),production.runId(),step,inputId,graph.graphId(),graph.graphSha256(),production.replay().rawSha256(),graph.planRevision(),graph.planSha256(),text(replay,"snapshot_id"),binding.roleVersion(),new PermissionProtocol.Basis(policy.versionId(),policy.contentSha256()),tool,targetArtifact,target.reference(),targetReceipt,target.manifest(),capability,decision,selectedPolicies,target.fixedSources(),permissions(replay,binding),predecessors);
        return prepare(production,publication,policy,input);
    }
    public Verified require(EnterpriseAccess.Admission a,String run,String step,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskNodeProductionAuthority.Verified production)throws Exception {
        if(a!=production.admission()||!run.equals(production.runId()))deny();
        var row=one(db.queryForList("SELECT * FROM task_strict_field_input WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),run,step));
        var input=schema.input(str(row,"canonical_input").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(!input.schemaVersion().equals(str(row,"schema_version"))||!schema.hash(input).equals(str(row,"input_sha256"))||!schema.manifest(input.schemaVersion()).equals(str(row,"schema_manifest_sha256"))
                ||!input.inputId().equals(str(row,"input_id"))||!input.runId().equals(run)||!input.stepId().equals(step)
                ||!a.actorId().equals(str(row,"created_by")))bad();
        var receipt=schema.read(str(row,"receipt_json"));if(!schema.hash(receipt).equals(str(row,"receipt_sha256")))bad();
        requireReceipt(input,row,receipt);
        var definition=TaskStrictFieldMaterializer.definition(schema,input,str(row,"input_sha256"),str(row,"receipt_sha256"));
        var node=one(db.queryForList("SELECT * FROM runtime_node_definition WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),run,step));
        if(!input.schemaVersion().equals(str(node,"schema_version"))||!"N03".equals(str(node,"node_kind"))||!definition.equals(schema.read(str(node,"canonical_definition")))||!schema.hash(definition).equals(str(node,"definition_sha256"))||!schema.manifest(input.schemaVersion()).equals(str(node,"schema_manifest_sha256")))bad();
        String witness=inspect(production,publication,policy,input);
        return new Verified(new Prepared(production,publication,policy,input,witness),row,receipt);
    }
    private void requireReceipt(TaskStrictFieldInput input,Map<String,Object> row,JsonNode receipt)throws Exception {
        ProfessionalRuntimeObservations.closed(receipt,Set.of("schema_version","kind","input_id","input_sha256","tenant_id","project_id","task_id","run_id","step_id","target_artifact_id","target_registration_sha256","audit_id","event_id"));
        if(!input.schemaVersion().equals(text(receipt,"schema_version"))||!"TASK_STRICT_FIELD_INPUT".equals(text(receipt,"kind")))bad();
        for(String key:List.of("tenant_id","project_id","task_id","run_id","step_id","input_id","target_artifact_id","target_registration_sha256"))
            if(!schema.tree(input).required(key).equals(receipt.required(key)))bad();
        if(!schema.hash(input).equals(text(receipt,"input_sha256")))bad();
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",input.tenantId(),text(receipt,"audit_id")));
        if(!"task.strict_field.input.registered".equals(str(audit,"action"))||!"SUCCESS".equals(str(audit,"result"))||!receipt.equals(schema.read(str(audit,"resource_ref")))
                ||!input.inputId().equals(str(audit,"request_id"))||!input.runId().equals(str(audit,"trace_id"))||!str(row,"created_by").equals(str(audit,"subject_id")))bad();
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",input.tenantId(),text(receipt,"event_id")));
        if(!"task.strict_field.input.registered".equals(str(event,"event_type"))||!input.inputId().equals(str(event,"aggregate_id"))||!receipt.equals(schema.read(str(event,"payload"))))bad();
    }
    private String inspect(TaskNodeProductionAuthority.Verified p,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskStrictFieldInput input)throws Exception {
        p.requireUnchanged();var a=p.admission();schema.validate(input);var replay=p.replay().content();
        if(!p.tenantId().equals(input.tenantId())||!p.projectId().equals(input.projectId())||!p.taskId().equals(input.taskId())||!p.runId().equals(input.runId())
                ||!p.replay().rawSha256().equals(input.replaySha256())||!text(replay,"plan_revision").equals(input.planRevision())
                ||!text(replay.required("plan"),"content_sha256").equals(input.planSha256())||!text(replay,"snapshot_id").equals(input.snapshotId()))bad();
        if(!policy.versionId().equals(input.executionPolicyVersion().version_id())||!policy.contentSha256().equals(input.executionPolicyVersion().content_sha256())
                ||!publication.executionPolicyId().equals(policy.versionId())||!publication.executionPolicySha256().equals(policy.contentSha256()))bad();
        var binding=policy.requireNode("N03",input.roleVersion(),policy.definition().worker().serviceUri(),policy.definition().worker().audience());
        if(binding.modelVersion()!=null)deny(); // deterministic host route, never a model session shortcut
        var role=roles.require(a,input.roleVersion(),"ROLE",input.projectId(),"RUN_CREATION");
        TaskStrictFieldEvidenceContract.require(role.canonicalDefinition().required("request"),input.schemaVersion());
        var literal=tokens.permissions(tokens.read("RoleScope",json.canonical(role.canonicalDefinition().required("request").required("tool_scope"))));
        var exact=permissions(replay,binding);if(!FormalTaskPermissionAuthority.narrow(literal,exact).equals(input.nodePermissions()))bad();
        var tool=tools.require(a,input.toolVersion(),"TOOL",input.projectId(),"AGENT_PUBLICATION");
        boolean included=false;for(var t:replay.required("agent_release").required("tool_versions"))if(FormalTaskNodeAuthority.sameEvidence(json.tree(input.toolVersion()),t))included=true;if(!included)deny();
        var catalogue=SchemaCatalogue.fromClasspath();var definition=tool.canonicalDefinition().required("request");
        if(!catalogue.schemaRef("tool-inputs","FieldArgs").equals(text(definition.required("input_schema_ref"),"schema_ref"))
                ||!catalogue.schemaDigest("tool-inputs").equals(text(definition.required("input_schema_ref"),"schema_sha256"))
                ||!catalogue.schemaRef("tool-success",null).equals(text(definition.required("output_schema_ref"),"schema_ref"))
                ||!catalogue.schemaDigest("tool-success").equals(text(definition.required("output_schema_ref"),"schema_sha256")))deny();
        var toolPermissions=tokens.permissions(tokens.read("RoleScope",json.canonical(definition.required("tool_scope"))));
        var checkPermission=new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(input.tenantId(),input.projectId(),"TASK",input.taskId()),TaskStrictFieldNode.ACTION,TaskStrictFieldNode.PURPOSE);
        if(!toolPermissions.contains(checkPermission)||!input.nodePermissions().containsAll(toolPermissions)||!literal.containsAll(toolPermissions))deny();
        var graph=graphs.require(a,p.runId());if(!graph.graphId().equals(input.graphId())||!graph.graphSha256().equals(input.graphSha256())||!graph.requiredStages().contains("N03"))bad();
        var expected=new ArrayList<TaskNodeInputProtocol.Predecessor>();for(var node:graph.nodes()){
            var c=completions.require(a,p.runId(),node.stepId());expected.add(new TaskNodeInputProtocol.Predecessor(node.stepId(),c.resultArtifactId(),c.resultSha256(),c.resultObjectVersion(),c.resultReceiptSha256(),c.evidenceArtifactId(),c.evidenceSha256(),c.evidenceObjectVersion(),c.evidenceReceiptSha256(),c.completionReceiptSha256()));c.requireUnchanged();}
        if(!expected.equals(input.predecessors()))bad();
        var target=targets.require(p,input.targetArtifactId(),input.targetRegistrationSha256());
        if(!target.manifest().equals(input.targetManifest())||!target.reference().equals(input.targetObject())||!target.fixedSources().equals(input.fixedSources()))bad();
        JsonNode capabilityWitness;
        if(target.typed()){
            var typed=target.typedTarget();var request=typed.request();
            if(!input.targetInputCapability().equals(new PermissionProtocol.Basis(typed.revisionId(),typed.digest()))||!input.targetInputDecision().equals(typed.decision())
                ||!input.roleVersion().equals(request.roleVersion())||!input.executionPolicyVersion().equals(request.executionPolicyVersion())||!request.releaseId().equals(text(replay,"agent_release_id"))||!request.publicationApprovalSha256().equals(publication.approvalSha256())||Long.parseLong(input.targetObject().sizeBytes())>request.maximumReadBytes())bad();
            var declared=new HashMap<String,PermissionProtocol.Basis>();for(var e:request.entries())declared.put(e.fieldId(),e.comparisonPolicy());for(var selection:input.policies()){var entry=schema.target(input).entries().stream().filter(e->e.entryId().equals(selection.entryId())).findFirst().orElseThrow();if(!selection.policy().equals(declared.remove(entry.fieldId())))bad();}if(!declared.isEmpty())bad();
            typed.requireApproved();capabilityWitness=json.tree(Map.of("target",typed.receipt(),"decision",typed.decision(),"witness",typed.witness()));
        }else capabilityWitness=capabilities.requireFixedRun(a,publication,policy,input.roleVersion(),input.targetInputCapability().version_id(),input.targetInputCapability().content_sha256(),input.taskId(),input.targetInputDecision().version_id(),input.targetInputDecision().content_sha256(),target).authorizations();
        var scope=scopes.require(p);var approvals=json.object().arrayNode();var grouped=new LinkedHashMap<PermissionProtocol.Basis,List<StrictComparisonPolicyDefinition.Combination>>();
        var targetValue=schema.target(input);var entryMap=new HashMap<String,StrictFieldComparisonProtocol.TargetEntry>();for(var e:targetValue.entries())entryMap.put(e.entryId(),e);
        for(var selected:input.policies()){
            var e=entryMap.get(selected.entryId());if(e==null)bad();
            if(selected.sourceContext()==null)throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
            var contextProof=contexts.requireCurrentFixedRun(scope,selected.sourceContext().version_id(),selected.sourceContext().content_sha256());contextProof.requireApproved();
            if(!target.typed()&&!e.provenance().source().evidenceRef().equals(contextProof.evidence()))bad();
            if(target.typed()){var expectedContext=target.typedTarget().request().entries().stream().filter(x->x.fieldId().equals(e.fieldId())).findFirst().orElseThrow().observedContext();if(!expectedContext.equals(selected.sourceContext()))bad();}
            approvals.add(json.tree(Map.of("context",contextProof.receipt(),"decision",contextProof.decision())));

            if(e.modelContext()==StrictFieldComparisonProtocol.ContextState.UNRESOLVED||e.conditionContext()==StrictFieldComparisonProtocol.ContextState.UNRESOLVED)throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
            var c=new StrictComparisonPolicyDefinition.Combination(e.fieldDefinitionId(),context(e.modelContext(),e.modelIds()),context(e.conditionContext(),e.conditionIds()));
            var values=grouped.computeIfAbsent(selected.policy(),ignored->new ArrayList<>());if(!values.contains(c))values.add(c);
        }
        for(var selected:grouped.entrySet()){var approved=policies.requireApprovedFixedRun(scope,selected.getKey().version_id(),selected.getKey().content_sha256());approved.definition().requireCoverage(selected.getValue());
            if(approved.definition().applicability().state()!=StrictComparisonPolicyDefinition.Applicability.APPLICABLE)throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
            approvals.addObject().put("revision",approved.revisionId()).put("digest",approved.digest()).put("decision",approved.decisionId());approved.requireUnchanged();}
        graph.requireUnchanged();target.requireUnchanged();role.requireUnchanged();tool.requireUnchanged();scope.requireUnchanged();p.requireUnchanged();
        return schema.hash(List.of(input,capabilityWitness,approvals,publication.authorizations(),policy.authorizations()));
    }
    private static StrictComparisonPolicyDefinition.Context context(StrictFieldComparisonProtocol.ContextState state,List<String> ids){return new StrictComparisonPolicyDefinition.Context(state==StrictFieldComparisonProtocol.ContextState.KNOWN_GLOBAL?StrictComparisonPolicyDefinition.ContextMode.GLOBAL:StrictComparisonPolicyDefinition.ContextMode.SCOPED,ids);}
    private List<RunTokenAuthoritySchema.Permission> permissions(JsonNode replay,AgentExecutionPolicyProtocol.NodeBinding binding)throws Exception {
        String tenant=text(replay,"tenant_id"),project=text(replay,"project_id"),task=text(replay,"task_id");var requested=new LinkedHashSet<RunTokenAuthoritySchema.Permission>();
        for(var action:binding.actions()){var selections=new ArrayList<PermissionProtocol.Scope>();switch(action.targetSelector()){
            case "TASK"->selections.add(new PermissionProtocol.Scope(tenant,project,"TASK",task));case "PROJECT"->selections.add(new PermissionProtocol.Scope(tenant,project,"PROJECT",project));
            case "CURRENT_EXECUTION_POLICY"->selections.add(owner(tenant,project,json.basis(replay.required("execution_policy_version")),"CONFIGURATION"));
            case "FIXED_VERSION"->selections.add(owner(tenant,project,action.fixedVersion(),action.resourceKind()));
            case "SELECTED_SOURCE"->{for(var member:replay.required("scope_snapshot").required("members"))selections.add(owner(tenant,project,new PermissionProtocol.Basis(text(member,"source_version"),text(member,"original_sha256")),"SOURCE"));}
            case "RUN_REGISTERED_ARTIFACT"->{ }default->deny();}
            for(var target:selections){if(!target.resource_kind().equals(action.resourceKind()))bad();requested.add(new RunTokenAuthoritySchema.Permission(target,action.action(),action.purpose()));}}
        var exact=List.copyOf(requested);RuntimeNodePermissions.intersect(tokens.permissions(replay.required("principal_manifest")),exact,exact.stream().map(RunTokenAuthoritySchema.Permission::action).distinct().toList());return exact;
    }
    private PermissionProtocol.Scope owner(String tenant,String project,PermissionProtocol.Basis basis,String kind){
        if(basis==null)deny();var row=one(owners.ownersAcrossDomains(List.of(basis.version_id()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList())).owner();
        if(!tenant.equals(row.tenantId())||!basis.version_id().equals(row.versionId())||row.contentSha256()!=null&&!basis.content_sha256().equals(row.contentSha256())||!kind.equals(row.resourceKind())||row.versionProjectId()!=null&&!project.equals(row.versionProjectId())||row.resourceProjectId()!=null&&!project.equals(row.resourceProjectId())||row.conversationId()!=null)bad();
        return new PermissionProtocol.Scope(tenant,row.resourceProjectId(),kind,row.resourceId());
    }
    public final class Prepared {
        private final TaskNodeProductionAuthority.Verified production;private final AgentPublicationAuthority.VerifiedPublication publication;
        private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final TaskStrictFieldInput input;private final String witness;
        private Prepared(TaskNodeProductionAuthority.Verified p,AgentPublicationAuthority.VerifiedPublication pub,AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskStrictFieldInput input,String witness){production=p;publication=pub;this.policy=policy;this.input=input;this.witness=witness;}
        public TaskStrictFieldInput input(){return input;}public TaskNodeProductionAuthority.Verified production(){return production;}
        public void requireUnchanged()throws Exception{if(!witness.equals(inspect(production,publication,policy,input)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    }
    public final class Verified {
        private final Prepared prepared;private final Map<String,Object> row;private final JsonNode receipt;
        private Verified(Prepared prepared,Map<String,Object> row,JsonNode receipt){this.prepared=prepared;this.row=Map.copyOf(row);this.receipt=receipt.deepCopy();}
        public void requireUnchanged()throws Exception{prepared.requireUnchanged();var fresh=one(db.queryForList("SELECT * FROM task_strict_field_input WHERE tenant_id=? AND input_id=? LIMIT 2 FOR UPDATE",input().tenantId(),inputId()));if(!row.equals(fresh))bad();requireReceipt(input(),fresh,receipt);}
        public String tenantId(){return input().tenantId();}public String projectId(){return input().projectId();}public String runId(){return input().runId();}public String stepId(){return input().stepId();}public String taskId(){return input().taskId();}public String graphId(){return input().graphId();}
        public TaskStrictFieldInput input(){return prepared.input();}public JsonNode manifest(){return schema.tree(input());}public String inputId(){return input().inputId();}
        public String inputSha256(){return str(row,"input_sha256");}public String receiptSha256(){return str(row,"receipt_sha256");}public String graphSha256(){return input().graphSha256();}
        TaskNodeProductionAuthority.Verified production(){return prepared.production();}
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String text(JsonNode n,String key){return n.required(key).textValue();}
    private static String str(Map<String,Object> n,String key){var v=n.get(key);if(v==null)bad();return v.toString();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
