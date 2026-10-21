package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** N05 private input proof: fixed saved candidate and complete approved original rule set.
 * Writer output is the object under review, never the source of independent review authority. */
public final class TaskSourceVerifierInputAuthority {
    private ScenarioSpecificationService scenarios;
    public TaskSourceVerifierInputAuthority withScenarios(ScenarioSpecificationService value){this.scenarios=Objects.requireNonNull(value);return this;}
    private final JdbcTemplate db;private final TaskSourceVerifierSchema json;private final PermissionSchema permissions;
    private final TaskSourceVerifierTargetAuthority targets;private final TaskSourceVerifierRules rules;private final TaskRunGraphService graphs;
    private final FormalCapabilityIdentityAuthority roles;private final FormalRuntimeCapabilityIdentityAuthority tools;private final RunTokenAuthoritySchema tokens;private final ArtifactRegistryMapper registry;
    public TaskSourceVerifierInputAuthority(DataSource data,TaskSourceVerifierSchema json,PermissionSchema permissions,TaskSourceVerifierTargetAuthority targets,
        TaskSourceVerifierRules rules,TaskRunGraphService graphs,FormalCapabilityIdentityAuthority roles,FormalRuntimeCapabilityIdentityAuthority tools,
        RunTokenAuthoritySchema tokens,ArtifactRegistryMapper registry){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.permissions=permissions;this.targets=targets;this.rules=rules;this.graphs=graphs;this.roles=roles;this.tools=tools;this.tokens=tokens;this.registry=registry;}
    public Prepared prepare(TaskNodeProductionAuthority.Verified p,AgentPublicationAuthority.VerifiedPublication publication,
        AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskSourceVerifierInput input)throws Exception {
        json.input(json.bytes(input));return new Prepared(p,publication,policy,input,inspect(p,publication,policy,input));
    }
    public Verified require(EnterpriseAccess.Admission a,String run,String step,AgentPublicationAuthority.VerifiedPublication publication,
        AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskNodeProductionAuthority.Verified production)throws Exception {
        if(a!=production.admission()||!run.equals(production.runId()))deny();
        var row=one(db.queryForList("SELECT * FROM task_source_verifier_input WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",production.tenantId(),run,step));
        var input=json.input(str(row,"canonical_input").getBytes(java.nio.charset.StandardCharsets.UTF_8));return registered(prepare(production,publication,policy,input),row);
    }
    /** Read the actual original input under an exact successor-only reuse capability. */
    public Verified requireOriginal(TaskRunResumeOriginalAuthority.Source source,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        source.requireUnchanged();if(!"N05".equals(source.stageKind()))deny();
        var next=require(source.admission(),source.successorRunId(),source.successorStepId(),publication,policy,source.successor());
        var row=one(db.queryForList("SELECT * FROM task_source_verifier_input WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",source.admission().context().tenant_id(),source.originalRunId(),source.originalStepId()));
        var input=json.input(str(row,"canonical_input").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        source.requireOriginal(source.admission(),input.runId(),input.stepId());
        if(!json.hash(input).equals(source.pins().required("original_input_sha256").asText())||!next.inputSha256().equals(source.pins().required("successor_input_sha256").asText()))bad();
        TaskResumeSemanticInputs.require(json.tree(input),next.manifest(),"N05");
        var prepared=new Prepared(source.successor(),publication,policy,input,next.prepared.state);
        prepared.originalSource=source;prepared.successorInput=next;
        var result=registered(prepared,row);source.requireUnchanged();return result;
    }
    Verified registered(Prepared p,Map<String,Object> row)throws Exception {
        p.requireUnchanged();var i=p.input;var receipt=json.read(str(row,"receipt_json"));same(json.hash(i),str(row,"input_sha256"));same(json.canonical(i),str(row,"canonical_input"));same(p.production.actorId(),str(row,"created_by"));same(i.inputId(),str(row,"input_id"));same(json.hash(receipt),str(row,"receipt_sha256"));same(json.manifest(),str(row,"schema_manifest_sha256"));for(String key:List.of("tenant_id","project_id","run_id","step_id","task_id"))same(json.tree(i).required(key).asText(),str(row,key));journal(p.production,receipt);
        var artifact=one(registry.artifact(i.inputId()));same("TASK_SOURCE_VERIFIER_INPUT",artifact.artifactKind());same(i.tenantId(),artifact.tenantId());same(i.projectId(),artifact.projectId());same(json.hash(i),artifact.reference().sha256());same(artifact.reference().sha256(),artifact.contentSha256());StoredObjectReferences.decode(artifact.storedObject(),artifact.reference());same(json.tree(artifact.reference()),receipt.required("object_ref"));
        var binding=one(registry.binding(i.inputId()));same("TASK",binding.resourceKind());same(i.taskId(),binding.resourceId());same(TaskSourceVerifierSchema.INPUT_REF,binding.schemaRef());same(artifact.accessSetId(),binding.accessSetId());
        if(!new HashSet<>(registry.members(artifact.accessSetId())).equals(new HashSet<>(p.state.target.contentMembers())))bad();
        var write=one(db.queryForList("SELECT payload_sha256,canonical_payload,receipt_sha256 FROM task_source_verifier_object WHERE tenant_id=? AND artifact_id=? AND kind='TASK_SOURCE_VERIFIER_INPUT' AND state='REGISTERED' LIMIT 2 FOR UPDATE",i.tenantId(),i.inputId()));same(json.hash(i),str(write,"payload_sha256"));same(json.canonical(i),str(write,"canonical_payload"));same(str(row,"receipt_sha256"),str(write,"receipt_sha256"));
        var rule=one(db.queryForList("SELECT * FROM task_source_verifier_rule_set WHERE tenant_id=? AND project_id=? AND revision_id=? AND run_id=? LIMIT 2 FOR UPDATE",i.tenantId(),i.projectId(),i.ruleRevision(),i.runId()));same(i.ruleManifestSha256(),str(rule,"content_sha256"));same(p.state.rules.manifest(i.ruleRevision()),json.read(str(rule,"manifest_json")));
        var version=one(registry.version(i.ruleRevision()));same("SOURCE_VERIFIER_RULE_SET",version.entityKind());same(i.projectId(),version.projectId());same(i.ruleManifestSha256(),version.contentSha256());var ruleReceipt=json.read(str(rule,"receipt_json"));same(json.hash(ruleReceipt),str(rule,"receipt_sha256"));same(i.ruleManifestSha256(),text(ruleReceipt,"content_sha256"));same(p.state.rules.manifest(i.ruleRevision()),ruleReceipt.required("manifest"));journal(p.production,ruleReceipt);
        var parents=new HashSet<String>();for(var edge:db.queryForList("SELECT upstream_version_id,relation_kind FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? FOR UPDATE",i.tenantId(),i.ruleRevision())){same("SOURCE_VERIFIER_RULE_MEMBER",str(edge,"relation_kind"));if(!parents.add(str(edge,"upstream_version_id")))bad();}same(new HashSet<>(i.requiredRules().stream().map(PermissionProtocol.Basis::version_id).toList()),parents);
        return new Verified(p,artifact.reference(),artifact.accessSetId(),str(row,"receipt_sha256"));
    }
    private void journal(TaskNodeProductionAuthority.Verified p,JsonNode receipt)throws Exception {
        var audit=one(db.queryForList("SELECT resource_ref,result,subject_id FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",p.tenantId(),text(receipt,"audit_id")));same("SUCCESS",str(audit,"result"));same(p.actorId(),str(audit,"subject_id"));same(receipt,json.read(str(audit,"resource_ref")));
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",p.tenantId(),text(receipt,"event_id")));same(receipt,json.read(str(event,"payload")));
    }
    private State inspect(TaskNodeProductionAuthority.Verified p,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskSourceVerifierInput i)throws Exception {
        p.requireUnchanged();var replay=p.replay().content();same(p.tenantId(),i.tenantId());same(p.projectId(),i.projectId());same(p.taskId(),i.taskId());same(p.runId(),i.runId());same(p.replay().rawSha256(),i.replaySha256());same(text(replay,"plan_revision"),i.planRevision());same(text(replay,"snapshot_id"),i.snapshotId());same(text(replay.required("plan"),"content_sha256"),i.planSha256());
        same(policy.versionId(),i.executionPolicyVersion().version_id());same(policy.contentSha256(),i.executionPolicyVersion().content_sha256());same(publication.executionPolicyId(),policy.versionId());same(publication.executionPolicySha256(),policy.contentSha256());
        var node=policy.requireNode("N05",i.roleVersion(),policy.definition().worker().serviceUri(),policy.definition().worker().audience());if(node.modelVersion()==null||!node.modelVersion().equals(i.modelVersion()))deny();
        var role=roles.require(p.admission(),i.roleVersion(),"ROLE",i.projectId(),"RUN_CREATION");
        var evidenceContract=TaskReviewEvidenceContract.require(role.canonicalDefinition().required("request"),SchemaCatalogue.fromClasspath());
        var literal=tokens.permissions(tokens.read("RoleScope",permissions.canonical(role.canonicalDefinition().required("request").required("tool_scope"))));
        if(!literal.containsAll(i.nodePermissions())||!tokens.permissions(replay.required("principal_manifest")).containsAll(i.nodePermissions()))deny();
        var tool=tools.require(p.admission(),i.submitToolVersion(),"TOOL",i.projectId(),"AGENT_PUBLICATION");var definition=tool.canonicalDefinition().required("request");var catalogue=SchemaCatalogue.fromClasspath();same(catalogue.schemaRef("tool-inputs","ReviewArgs"),text(definition.required("input_schema_ref"),"schema_ref"));same(catalogue.schemaDigest("tool-inputs"),text(definition.required("input_schema_ref"),"schema_sha256"));
        boolean selected=false;for(var v:replay.required("agent_release").required("tool_versions"))if(json.tree(i.submitToolVersion()).equals(v))selected=true;if(!selected)deny();
        var graph=graphs.require(p.admission(),p.runId());same(graph.graphId(),i.graphId());same(graph.graphSha256(),i.graphSha256());if(!graph.requiredStages().contains("N05"))bad();
        var target=targets.require(p,i.candidateId(),publication,policy);if(evidenceContract.v2()!=target.writerInput(publication,policy).evidenceContract().v2())bad();same(target.candidateArtifactId(),i.candidateArtifactId());same(target.candidateReceiptSha256(),i.candidateReceiptSha256());same(target.readObjects().get(i.candidateArtifactId()),i.candidateObject());same(target.bodyArtifactId(),i.bodyArtifactId());same(target.bodyObject(),i.bodyObject());same(target.writerStepId(),i.writerStepId());same(target.candidate().targetSections(),i.targetSections());same(List.copyOf(target.claims().keySet()),i.requiredClaimIds());
        var originalRules=rules.require(p);same(originalRules.bases(),i.requiredRules());same(json.hash(originalRules.manifest(i.ruleRevision())),i.ruleManifestSha256());
        var sources=new ArrayList<TaskNodeInputProtocol.SourceSelection>();for(var m:replay.required("scope_snapshot").required("members"))sources.add(new TaskNodeInputProtocol.SourceSelection(text(m,"source_version"),text(m,"parse_version"),text(m,"index_version"),text(m,"approval_revision"),text(m,"binding_revision"),text(m,"rights_revision"),policy.definition().businessPurpose()));same(sources,i.fixedSources());
        target.requireUnchanged();originalRules.requireUnchanged();graph.requireUnchanged();publication.requireUnchanged();policy.requireUnchanged();p.requireUnchanged();
        if(scenarios==null)deny();JsonNode comparison=scenarios.requireRegulatory(p,publication);var comparisonEvidence=new ArrayList<DatasetProtocol.EvidenceRef>();
        if(comparison!=null){
            var regulatorySelection=new RegulatoryComparisonSchema().read(comparison.required("selection"));
            comparisonEvidence.addAll(new RegulatoryComparisonSchema().evidence(regulatorySelection));
            for(var ref:comparisonEvidence)if(!p.tenantId().equals(ref.tenantId())||!p.projectId().equals(ref.projectId()))deny();
            for(var edition:List.of(regulatorySelection.baseline(),regulatorySelection.target())){
                var chosen=sources.stream().filter(x->edition.source().version_id().equals(x.sourceVersion())&&edition.parseVersion().equals(x.parseVersion())&&edition.approvalRevision().equals(x.approvalRevision())&&edition.rightsRevision().equals(x.rightsRevision())).toList();if(chosen.size()!=1)deny();
            }
        }
        JsonNode documentScenario=scenarios.requireDocumentScenario(p,publication);comparisonEvidence.addAll(DocumentScenarioInputs.require(documentScenario,p,sources));
        String witness=json.hash(Map.of("production",p.authorizationsSha256(),"target_receipt",target.candidateReceiptSha256(),"target_body",target.bodySha256(),"rules",originalRules.witness(),"role",role.authorizations(),"tool",tool.authorizations(),"content_members",target.contentMembers()));return new State(target,originalRules,witness,comparison,List.copyOf(comparisonEvidence),documentScenario,evidenceContract);
    }
    private record State(TaskSourceVerifierTargetAuthority.Verified target,TaskSourceVerifierRules.Verified rules,String hash,JsonNode comparison,List<DatasetProtocol.EvidenceRef> comparisonEvidence,JsonNode documentScenario,TaskReviewEvidenceContract evidenceContract){}
    public final class Prepared {
        private TaskRunResumeOriginalAuthority.Source originalSource;private Verified successorInput;
        private final TaskNodeProductionAuthority.Verified production;private final AgentPublicationAuthority.VerifiedPublication publication;private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final TaskSourceVerifierInput input;private final State state;private final String inputHash;
        private Prepared(TaskNodeProductionAuthority.Verified p,AgentPublicationAuthority.VerifiedPublication pub,AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskSourceVerifierInput input,State state)throws Exception{inputHash=json.hash(input);production=p;publication=pub;this.policy=policy;this.input=input;this.state=state;}
        public void requireUnchanged()throws Exception{if(originalSource!=null){originalSource.requireOriginal(production.admission(),input.runId(),input.stepId());successorInput.requireUnchanged();TaskResumeSemanticInputs.require(json.tree(input),successorInput.manifest(),"N05");return;}production.requireUnchanged();var fresh=inspect(production,publication,policy,input);if(!state.hash.equals(fresh.hash)||!Objects.equals(state.comparison,fresh.comparison)||!Objects.equals(state.documentScenario,fresh.documentScenario)||!state.evidenceContract.equals(fresh.evidenceContract))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        public TaskSourceVerifierInput input(){production.admission();return input;}public TaskNodeProductionAuthority.Verified production(){production.admission();return production;}
        public byte[] bytes()throws Exception{requireUnchanged();return json.bytes(input);}public List<String> contentRoots(){return state.target.contentRoots();}public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){return state.target.contentMembers();}
        public JsonNode ruleManifest(){return state.rules.manifest(input.ruleRevision());}
    }
    public final class Verified {
        TaskRunResumeOriginalAuthority.Source originalSource(){return prepared.originalSource;}
        private final Prepared prepared;private final InterfaceProtocol.ObjectRef object;private final String accessSet,receipt;
        private Verified(Prepared p,InterfaceProtocol.ObjectRef object,String accessSet,String receipt){prepared=p;this.object=object;this.accessSet=accessSet;this.receipt=receipt;}
        public void requireUnchanged()throws Exception{prepared.requireUnchanged();var fresh=registered(prepared,one(db.queryForList("SELECT * FROM task_source_verifier_input WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",tenantId(),runId(),stepId())));same(object,fresh.object);same(receipt,fresh.receipt);same(accessSet,fresh.accessSet);}
        public TaskReviewEvidenceContract evidenceContract(){return prepared.state.evidenceContract;}
        public TaskSourceVerifierInput input(){return prepared.input();}public TaskNodeProductionAuthority.Verified production(){return prepared.production();}public EnterpriseAccess.Admission admission(){return production().admission();}
        public JsonNode manifest(){return json.tree(input());}public byte[] bytes()throws Exception{return prepared.bytes();}public String inputSha256(){production().admission();return prepared.inputHash;}
        public String receiptSha256(){production().admission();return receipt;}public String artifactId(){return input().inputId();}public String inputId(){return artifactId();}public InterfaceProtocol.ObjectRef objectReference(){production().admission();return object;}public String accessSetId(){production().admission();return accessSet;}
        public String tenantId(){return input().tenantId();}public String projectId(){return input().projectId();}public String taskId(){return input().taskId();}public String runId(){return input().runId();}public String stepId(){return input().stepId();}public String snapshotId(){return input().snapshotId();}
        public List<TaskNodeInputProtocol.SourceSelection> fixedSources(){return input().fixedSources();}public List<RunTokenAuthoritySchema.Permission> nodePermissions(){return input().nodePermissions();}
        public List<String> contentRoots(){return prepared.contentRoots();}public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){return prepared.contentMembers();}
        public TaskSourceVerifierTargetAuthority.Verified target(){return prepared.state.target;}public TaskSourceVerifierRules.Verified rules(){return prepared.state.rules;}
        public Map<String,InterfaceProtocol.ObjectRef> readObjects(){return target().readObjects();}public Map<String,JsonNode> expectedReadPayloads(){return target().expectedReadPayloads();}
        public JsonNode documentScenario(){return prepared.state.documentScenario==null?null:prepared.state.documentScenario.deepCopy();}
        public JsonNode regulatoryComparison(){return prepared.state.comparison==null?null:prepared.state.comparison.deepCopy();}
        public List<DatasetProtocol.EvidenceRef> evidenceRefs(){var refs=new LinkedHashSet<>(target().evidenceRefs());refs.addAll(prepared.state.comparisonEvidence);return List.copyOf(refs);}
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String str(Map<String,Object> row,String key){var v=row.get(key);if(v==null)bad();return v.toString();}private static String text(JsonNode n,String k){if(!n.required(k).isTextual())bad();return n.required(k).textValue();}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
