package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Private N04 input authority: real fixed document, committed N02 and N03, current published
 * role/policy/source closure. Prepared proves dependencies only; Verified additionally proves
 * the immutable physical input registration. Neither is a model-send or adoption permission. */
public final class TaskSectionWriterInputAuthority {
    private ScenarioSpecificationService scenarios;
    public TaskSectionWriterInputAuthority withScenarios(ScenarioSpecificationService s){scenarios=Objects.requireNonNull(s);return this;}
    private final JdbcTemplate db;private final TaskSectionWriterSchema json;private final PermissionSchema permissions;
    private final TaskRunGraphService graphs;private final TaskResearchCompletionAuthority research;
    private final TaskStrictFieldWriterReadAuthority fieldOutputs;private final TaskRunReplayContentAuthority contents;
    private final FormalCapabilityIdentityAuthority roles;private final FormalRuntimeCapabilityIdentityAuthority tools;
    private final RunTokenAuthoritySchema tokens;private final ResourceReadAuthority resources;private final ArtifactRegistryMapper registry;private final DocumentInitialRevisionService initialDocuments;
    public TaskSectionWriterInputAuthority(DataSource data,TaskSectionWriterSchema json,PermissionSchema permissions,
            TaskRunGraphService graphs,TaskResearchCompletionAuthority research,TaskStrictFieldWriterReadAuthority fieldOutputs,
            TaskRunReplayContentAuthority contents,FormalCapabilityIdentityAuthority roles,FormalRuntimeCapabilityIdentityAuthority tools,
            RunTokenAuthoritySchema tokens,ResourceReadAuthority resources,ArtifactRegistryMapper registry,DocumentInitialRevisionService initialDocuments){
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.permissions=permissions;this.graphs=graphs;this.research=research;
        this.fieldOutputs=fieldOutputs;this.contents=contents;this.roles=roles;this.tools=tools;this.tokens=tokens;this.resources=resources;this.registry=registry;this.initialDocuments=initialDocuments;
    }
    public Prepared prepare(TaskNodeProductionAuthority.Verified production,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskSectionWriterInput input,byte[] baselineBody)throws Exception {
        return prepare(production,publication,policy,input,baselineBody,null);
    }
    public Prepared prepare(TaskNodeProductionAuthority.Verified production,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskSectionWriterInput input,byte[] baselineBody,DocumentInitialRevisionService.Verified initial)throws Exception {
        var baseline=json.read(baselineBody);var state=inspect(production,publication,policy,input,baseline,initial);
        return new Prepared(production,publication,policy,input,baseline,state,initial);
    }
    public Verified require(EnterpriseAccess.Admission admission,String run,String step,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskNodeProductionAuthority.Verified production)throws Exception {
        if(admission!=production.admission()||!run.equals(production.runId()))deny();
        var row=one(db.queryForList("SELECT * FROM task_section_writer_input WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",production.tenantId(),run,step));
        var input=json.input(str(row,"canonical_input").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var initial=initialDocuments.requireForInput(production,publication,policy,input.documentId(),input.baseDocumentRevision()).orElse(null);
        var prepared=prepare(production,publication,policy,input,str(row,"baseline_json").getBytes(java.nio.charset.StandardCharsets.UTF_8),initial);
        return registered(prepared,row);
    }
    /** Read the actual original input under an exact successor-only reuse capability. */
    public Verified requireOriginal(TaskRunResumeOriginalAuthority.Source source,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        source.requireUnchanged();if(!"N04".equals(source.stageKind()))deny();
        var next=require(source.admission(),source.successorRunId(),source.successorStepId(),publication,policy,source.successor());
        var row=one(db.queryForList("SELECT * FROM task_section_writer_input WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",source.admission().context().tenant_id(),source.originalRunId(),source.originalStepId()));
        var input=json.input(str(row,"canonical_input").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        source.requireOriginal(source.admission(),input.runId(),input.stepId());
        if(!json.hash(input).equals(source.pins().required("original_input_sha256").asText())||!next.inputSha256().equals(source.pins().required("successor_input_sha256").asText()))bad();
        TaskResumeSemanticInputs.require(json.tree(input),next.manifest(),"N04");
        var baseline=json.read(str(row,"baseline_json"));if(!baseline.equals(next.baselineBody()))bad();
        var prepared=new Prepared(source.successor(),publication,policy,input,baseline,next.prepared.state,next.prepared.initial);
        prepared.originalSource=source;prepared.successorInput=next;
        var result=registered(prepared,row);source.requireUnchanged();return result;
    }
    Verified registered(Prepared p,Map<String,Object> row)throws Exception {
        p.requireUnchanged();var i=p.input();var receipt=json.read(str(row,"receipt_json"));
        if(!i.inputId().equals(str(row,"input_id"))||!i.tenantId().equals(str(row,"tenant_id"))||!i.projectId().equals(str(row,"project_id"))
                ||!i.runId().equals(str(row,"run_id"))||!i.stepId().equals(str(row,"step_id"))||!json.hash(i).equals(str(row,"input_sha256"))
                ||!json.canonical(i).equals(str(row,"canonical_input"))||!json.canonical(p.baseline).equals(str(row,"baseline_json"))
                ||!json.hash(receipt).equals(str(row,"receipt_sha256"))||!p.production().actorId().equals(str(row,"created_by")))bad();
        var ar=one(registry.artifact(i.inputId()));var ref=ar.reference();StoredObjectReferences.decode(ar.storedObject(),ref);
        if(!"TASK_SECTION_WRITER_INPUT".equals(ar.artifactKind())||!i.tenantId().equals(ar.tenantId())||!i.projectId().equals(ar.projectId())
                ||!json.hash(i).equals(ref.sha256())||!ref.sha256().equals(ar.contentSha256())||!ref.objectVersion().equals(str(row,"object_version")))bad();
        var binding=one(registry.binding(i.inputId()));
        if(!"TASK".equals(binding.resourceKind())||!i.taskId().equals(binding.resourceId())||!TaskSectionWriterSchema.INPUT_REF.equals(binding.schemaRef())
                ||!ar.accessSetId().equals(binding.accessSetId()))bad();
        if(!json.tree(ref).equals(receipt.required("object_ref"))||!i.inputId().equals(receipt.required("artifact_id").textValue())
                ||!json.hash(i).equals(receipt.required("input_sha256").textValue()))bad();
        var actual=registry.members(ar.accessSetId());if(!new HashSet<>(actual).equals(new HashSet<>(p.state.members())))bad();
        var write=one(db.queryForList("SELECT payload_sha256,canonical_payload,object_version,receipt_sha256 FROM task_section_writer_object WHERE tenant_id=? AND artifact_id=? AND kind='TASK_SECTION_WRITER_INPUT' AND state='REGISTERED' LIMIT 2 FOR UPDATE",i.tenantId(),i.inputId()));
        if(!json.hash(i).equals(str(write,"payload_sha256"))||!json.canonical(i).equals(str(write,"canonical_payload"))||!ref.objectVersion().equals(str(write,"object_version"))
                ||!str(row,"receipt_sha256").equals(str(write,"receipt_sha256")))bad();
        return new Verified(p,ref,ar.accessSetId(),str(row,"receipt_sha256"));
    }
    private State inspect(TaskNodeProductionAuthority.Verified p,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskSectionWriterInput i,JsonNode baseline,DocumentInitialRevisionService.Verified initial)throws Exception {
        lifetime(p);p.requireUnchanged();var a=p.admission();var replay=p.replay().content();
        if(!p.tenantId().equals(i.tenantId())||!p.projectId().equals(i.projectId())||!p.taskId().equals(i.taskId())||!p.runId().equals(i.runId())
                ||!p.replay().rawSha256().equals(i.replaySha256())||!text(replay,"plan_revision").equals(i.planRevision())
                ||!text(replay,"snapshot_id").equals(i.snapshotId())||!text(replay.required("plan"),"content_sha256").equals(i.planSha256())
                ||!policy.versionId().equals(i.executionPolicyVersion().version_id())||!policy.contentSha256().equals(i.executionPolicyVersion().content_sha256())
                ||!publication.executionPolicyId().equals(policy.versionId())||!publication.executionPolicySha256().equals(policy.contentSha256()))bad();
        var node=policy.requireNode("N04",i.roleVersion(),policy.definition().worker().serviceUri(),policy.definition().worker().audience());
        if(node.modelVersion()==null||!node.modelVersion().equals(i.modelVersion()))deny();
        var role=roles.require(a,i.roleVersion(),"ROLE",i.projectId(),"RUN_CREATION");
        var evidenceContract=TaskWriterEvidenceContract.require(role.canonicalDefinition().required("request"),SchemaCatalogue.fromClasspath());
        var literal=tokens.permissions(tokens.read("RoleScope",permissions.canonical(role.canonicalDefinition().required("request").required("tool_scope"))));
        var principal=tokens.permissions(replay.required("principal_manifest"));
        if(!literal.containsAll(i.nodePermissions())||!principal.containsAll(i.nodePermissions()))deny();
        var submission=tools.require(a,i.submitToolVersion(),"TOOL",i.projectId(),"AGENT_PUBLICATION");
        var definition=submission.canonicalDefinition().required("request");var catalogue=SchemaCatalogue.fromClasspath();
        if(!catalogue.schemaRef("tool-inputs","CandidateArgs").equals(text(definition.required("input_schema_ref"),"schema_ref"))
                ||!catalogue.schemaDigest("tool-inputs").equals(text(definition.required("input_schema_ref"),"schema_sha256")))deny();
        boolean selected=false;for(var tool:replay.required("agent_release").required("tool_versions"))if(json.tree(i.submitToolVersion()).equals(tool))selected=true;if(!selected)deny();
        var graph=graphs.require(a,p.runId());if(!graph.requiredStages().contains("N04")||!graph.graphId().equals(i.graphId())||!graph.graphSha256().equals(i.graphSha256()))bad();
        var readObjects=new LinkedHashMap<String,ObjectRef>();var readPayloads=new LinkedHashMap<String,JsonNode>();
        var expected=new ArrayList<TaskNodeInputProtocol.Predecessor>();var evidence=new LinkedHashSet<DatasetProtocol.EvidenceRef>();
        for(var n:graph.nodes())if(i.targetSections().contains(n.sectionId())){
            var proof=research.require(a,p.runId(),n.stepId());proof.requireUnchanged();
            expected.add(new TaskNodeInputProtocol.Predecessor(n.stepId(),proof.resultArtifactId(),proof.resultSha256(),proof.resultObjectVersion(),proof.resultReceiptSha256(),proof.evidenceArtifactId(),proof.evidenceSha256(),proof.evidenceObjectVersion(),proof.evidenceReceiptSha256(),proof.completionReceiptSha256()));
            var bundle=ArtifactEvidenceBindings.bind(catalogue,ArtifactBindings.EVIDENCE_BUNDLE,proof.evidencePayload(),proof.evidenceSchemaRef(),proof.evidenceSchemaSha256()).read();
            if(!bundle.missingRequirementIds().isEmpty()||!bundle.conflictRefs().isEmpty())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
            var evidenceObject=one(registry.artifact(proof.evidenceArtifactId()));StoredObjectReferences.decode(evidenceObject.storedObject(),evidenceObject.reference());if(!proof.evidenceSha256().equals(evidenceObject.sha256())||!proof.evidenceObjectVersion().equals(evidenceObject.objectVersion())||!json.hash(proof.evidencePayload()).equals(evidenceObject.sha256()))bad();
            readObjects.put(proof.evidenceArtifactId(),evidenceObject.reference());readPayloads.put(proof.evidenceArtifactId(),proof.evidencePayload());
            evidence.addAll(bundle.selectedRefs());proof.requireUnchanged();
        }
        if(!expected.equals(i.predecessors()))bad();
        var checked=fieldOutputs.requireForWriter(p,i.fieldCheckArtifactId(),i.fieldCheckReceiptSha256(),publication,policy);checked.requireUnchanged();
        if(!i.fieldCheckObject().equals(checked.outputObject())||!i.planRevision().equals(checked.input().planRevision())||!i.snapshotId().equals(checked.input().snapshotId()))bad();
        var outputRow=one(db.queryForList("SELECT canonical_payload FROM task_strict_field_output_intent WHERE tenant_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",i.tenantId(),i.fieldCheckArtifactId()));var outputPayload=json.read(str(outputRow,"canonical_payload"));if(!json.hash(outputPayload).equals(checked.outputObject().sha256()))bad();readObjects.put(i.fieldCheckArtifactId(),checked.outputObject());readPayloads.put(i.fieldCheckArtifactId(),outputPayload);
        var fields=checked.content();if(!fields.missingRequirementIds().isEmpty()||fields.entries().stream().anyMatch(e->e.result()!=ArtifactProtocol.ComparisonResult.MATCH)
                ||fields.methodCoverage().stream().anyMatch(m->m.result()!=ArtifactProtocol.CoverageResult.COMPLETE))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        fields.entries().forEach(e->evidence.addAll(e.evidenceRefs()));fields.methodCoverage().forEach(m->evidence.addAll(m.evidenceRefs()));
        var closure=contents.require(p);if(!closure.input().rawSha256().equals(i.replaySha256()))bad();
        var sources=new ArrayList<TaskNodeInputProtocol.SourceSelection>();for(var m:replay.required("scope_snapshot").required("members"))sources.add(new TaskNodeInputProtocol.SourceSelection(text(m,"source_version"),text(m,"parse_version"),text(m,"index_version"),text(m,"approval_revision"),text(m,"binding_revision"),text(m,"rights_revision"),policy.definition().businessPurpose()));
        if(!sources.equals(i.fixedSources()))bad();
        if(scenarios==null)deny();var documentScenario=scenarios.requireDocumentScenario(p,publication);evidence.addAll(DocumentScenarioInputs.require(documentScenario,p,sources));
        if(initial==null)resources.require(a,new PermissionProtocol.Scope(i.tenantId(),i.projectId(),"DOCUMENT",i.documentId()),"document.read","DOCUMENT_BODY");
        else {initial.requireInput(p,i.documentId(),i.baseDocumentRevision());initial.requireUnchanged();if(!json.tree(initial.body()).equals(baseline)||!initial.bodySchemaRevision().equals(i.bodySchemaRevision()))bad();}
        var document=one(db.queryForList("SELECT m.* FROM document_revision_meta m JOIN document_head h ON h.tenant_id=m.tenant_id AND h.project_id=m.project_id AND h.document_id=m.document_id WHERE m.tenant_id=? AND m.project_id=? AND m.task_id=? AND m.document_id=? AND m.document_revision=? LIMIT 2 FOR UPDATE",i.tenantId(),i.projectId(),i.taskId(),i.documentId(),i.baseDocumentRevision()));
        if(!i.bodySchemaRevision().equals(str(document,"body_schema_revision"))||!i.planRevision().equals(str(document,"plan_revision"))
                ||!i.snapshotId().equals(str(document,"snapshot_id"))||!i.templateVersion().equals(str(document,"template_version"))||!json.hash(baseline).equals(str(document,"body_sha256")))bad();
        var ar=one(registry.artifact(str(document,"body_artifact_id")));StoredObjectReferences.decode(ar.storedObject(),ar.reference());
        if(!ar.reference().sha256().equals(str(document,"body_sha256"))||!"DOCUMENT_BODY".equals(ar.artifactKind())||!i.projectId().equals(ar.projectId()))bad();
        readObjects.put(str(document,"body_artifact_id"),ar.reference());readPayloads.put(str(document,"body_artifact_id"),baseline.deepCopy());
        var bodyBinding=one(registry.binding(str(document,"body_artifact_id")));
        if(!evidenceContract.bodyRef().equals(bodyBinding.schemaRef())||!evidenceContract.bodySha().equals(bodyBinding.schemaDocumentSha256()))bad();
        DocumentBodySemantics.verify(catalogue,baseline,new DocumentBodySemantics.Limits(10000,10000,100000),bodyBinding.schemaRef(),bodyBinding.schemaDocumentSha256());
        if(!evidenceContract.bodySha().equals(i.bodySchemaSha256())||!i.templateVersion().equals(text(baseline,"template_version")))bad();
        var sectionOwners=new LinkedHashMap<String,String>();for(var row:db.queryForList("SELECT node_id,section_id FROM plan_section_node WHERE tenant_id=? AND project_id=? AND plan_revision=? ORDER BY section_id,position LIMIT 10001 FOR UPDATE",i.tenantId(),i.projectId(),i.planRevision()))if(sectionOwners.putIfAbsent(str(row,"node_id"),str(row,"section_id"))!=null)bad();
        if(sectionOwners.size()>10000||!new HashSet<>(sectionOwners.values()).containsAll(i.targetSections()))bad();
        var targetIds=new HashSet<String>();for(var n:baseline.required("nodes"))if("SECTION".equals(text(n.required("content"),"kind"))){String owner=sectionOwners.get(text(n,"node_id"));if(owner==null)bad();targetIds.add(owner);}if(!targetIds.containsAll(i.targetSections()))bad();
        // Every byte-bearing baseline member remains within the same currently authorized replay closure.
        if(!new HashSet<>(closure.members()).containsAll(registry.members(ar.accessSetId())))deny();
        var signed=TaskSignedContinuationInputs.requireRuntime(db,resources,p);
        if(signed!=null){
            String signedArtifact=text(signed,"body_artifact_id");var original=one(registry.artifact(signedArtifact));
            StoredObjectReferences.decode(original.storedObject(),original.reference());
            if(!"DOCUMENT_BODY".equals(original.artifactKind())||!i.projectId().equals(original.projectId())||!json.tree(original.reference()).equals(signed.required("body_object"))
                    ||!text(signed,"body_sha256").equals(original.sha256())||!new HashSet<>(closure.members()).containsAll(registry.members(original.accessSetId())))deny();
            var signedBody=TaskSignedContinuationInputs.replayBody(replay);readObjects.put(signedArtifact,original.reference());readPayloads.put(signedArtifact,signedBody);
            var signedBinding=one(registry.binding(signedArtifact));
            var typed=DocumentEvidenceBindings.bind(catalogue,InterfaceBindings.DOCUMENT_BODY,signedBody,signedBinding.schemaRef(),signedBinding.schemaDocumentSha256()).read();
            typed.evidenceLinks().forEach(link->evidence.add(link.evidence()));
        }
        checked.requireUnchanged();closure.requireUnchanged();graph.requireUnchanged();publication.requireUnchanged();policy.requireUnchanged();p.requireUnchanged();
        var witness=json.tree(Map.of("production",p.authorizationsSha256(),"field_output_receipt",checked.outputReceiptSha256(),"document",document,
                "role",role.authorizations(),"tool",submission.authorizations(),"predecessors",expected,"content_members",closure.members(),"section_owners",sectionOwners));
        return new State(fields,List.copyOf(evidence),closure.roots(),closure.members(),Map.copyOf(readObjects),Map.copyOf(readPayloads),Map.copyOf(sectionOwners),json.hash(witness),documentScenario,evidenceContract);
    }
    private record State(ArtifactProtocol.FieldCheckResult fields,List<DatasetProtocol.EvidenceRef> evidence,List<String> roots,List<ArtifactRegistryProtocol.MemberRow> members,Map<String,ObjectRef> readObjects,Map<String,JsonNode> readPayloads,Map<String,String> sectionOwners,String hash,JsonNode documentScenario,TaskWriterEvidenceContract evidenceContract){}
    public final class Prepared {
        private TaskRunResumeOriginalAuthority.Source originalSource;private Verified successorInput;
        private final TaskNodeProductionAuthority.Verified production;private final AgentPublicationAuthority.VerifiedPublication publication;private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;
        private final TaskSectionWriterInput input;private final JsonNode baseline;private final State state;private final String inputHash;private final DocumentInitialRevisionService.Verified initial;
        private Prepared(TaskNodeProductionAuthority.Verified p,AgentPublicationAuthority.VerifiedPublication pub,AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskSectionWriterInput input,JsonNode baseline,State state,DocumentInitialRevisionService.Verified initial)throws Exception{this.initial=initial;inputHash=json.hash(input);production=p;publication=pub;this.policy=policy;this.input=input;this.baseline=baseline.deepCopy();this.state=state;}
        public void requireUnchanged()throws Exception {if(originalSource!=null){originalSource.requireOriginal(production.admission(),input.runId(),input.stepId());successorInput.requireUnchanged();TaskResumeSemanticInputs.require(json.tree(input),successorInput.manifest(),"N04");return;}var fresh=inspect(production,publication,policy,input,baseline,initial);if(!state.hash().equals(fresh.hash())||!Objects.equals(state.documentScenario(),fresh.documentScenario()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        public TaskSectionWriterInput input(){lifetime(production);return input;}public TaskNodeProductionAuthority.Verified production(){lifetime(production);return production;}
        public byte[] bytes()throws Exception{requireUnchanged();return json.bytes(input);}public JsonNode baselineBody(){lifetime(production);return baseline.deepCopy();}
        public List<String> contentRoots(){lifetime(production);return state.roots();}public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){lifetime(production);return state.members();}
    }
    public final class Verified {
        TaskRunResumeOriginalAuthority.Source originalSource(){return prepared.originalSource;}
        private final Prepared prepared;private final ObjectRef object;private final String accessSet,receipt;
        private Verified(Prepared p,ObjectRef object,String accessSet,String receipt){prepared=p;this.object=object;this.accessSet=accessSet;this.receipt=receipt;}
        public void requireUnchanged()throws Exception {prepared.requireUnchanged();var rows=db.queryForList("SELECT * FROM task_section_writer_input WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",tenantId(),runId(),stepId());var fresh=registered(prepared,one(rows));if(!object.equals(fresh.object)||!receipt.equals(fresh.receipt)||!accessSet.equals(fresh.accessSet))bad();}
        public TaskSectionWriterInput input(){return prepared.input();}public TaskNodeProductionAuthority.Verified production(){return prepared.production();}public EnterpriseAccess.Admission admission(){return production().admission();}
        public JsonNode manifest(){return json.tree(input());}public byte[] bytes()throws Exception{return prepared.bytes();}public String inputSha256(){lifetime(production());return prepared.inputHash;}
        public String receiptSha256(){lifetime(production());return receipt;}public String artifactId(){return input().inputId();}public String inputId(){return artifactId();}
        public ObjectRef objectReference(){lifetime(production());return object;}public String accessSetId(){lifetime(production());return accessSet;}
        public String tenantId(){return input().tenantId();}public String projectId(){return input().projectId();}public String taskId(){return input().taskId();}public String runId(){return input().runId();}public String stepId(){return input().stepId();}public String snapshotId(){return input().snapshotId();}
        public List<TaskNodeInputProtocol.SourceSelection> fixedSources(){return input().fixedSources();}public List<RunTokenAuthoritySchema.Permission> nodePermissions(){return input().nodePermissions();}
        public JsonNode documentScenario(){return prepared.state.documentScenario()==null?null:prepared.state.documentScenario().deepCopy();}
        public JsonNode baselineBody(){return prepared.baselineBody();}public ArtifactProtocol.FieldCheckResult fieldCheck(){lifetime(production());var nodes=new HashMap<String,JsonNode>();prepared.baseline.required("nodes").forEach(n->nodes.put(n.required("node_id").textValue(),n));var assigned=new HashSet<String>();for(var f:prepared.baseline.required("fields")){String node=f.required("node_id").textValue();var seen=new HashSet<String>();while(node!=null){if(!seen.add(node))bad();var n=nodes.get(node);if(n==null)bad();if("SECTION".equals(n.required("content").required("kind").textValue())){if(input().targetSections().contains(prepared.state.sectionOwners().get(node)))assigned.add(f.required("field_id").textValue());break;}node=n.required("parent_id").isNull()?null:n.required("parent_id").textValue();}}var all=prepared.state.fields();return new ArtifactProtocol.FieldCheckResult(all.targetArtifactId(),all.ruleRevision(),all.entries().stream().filter(e->assigned.contains(e.fieldId())).toList(),all.methodCoverage(),all.missingRequirementIds());}public List<DatasetProtocol.EvidenceRef> evidenceRefs(){lifetime(production());return prepared.state.evidence();}
        public Map<String,String> sectionNodeOwners(){lifetime(production());return prepared.state.sectionOwners();}
        public Map<String,ObjectRef> readObjects(){lifetime(production());return prepared.state.readObjects();}
        public Map<String,JsonNode> expectedReadPayloads(){lifetime(production());var copy=new LinkedHashMap<String,JsonNode>();prepared.state.readPayloads().forEach((k,v)->copy.put(k,v.deepCopy()));return Collections.unmodifiableMap(copy);}
        public TaskWriterEvidenceContract evidenceContract(){lifetime(production());return prepared.state.evidenceContract();}
        public List<JsonNode> evidenceBundles(){lifetime(production());return input().predecessors().stream().<JsonNode>map(p->prepared.state.readPayloads().get(p.evidenceArtifactId()).deepCopy()).toList();}
        public List<String> contentRoots(){return prepared.contentRoots();}public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){return prepared.contentMembers();}
    }
    private static void lifetime(TaskNodeProductionAuthority.Verified p){if(!TransactionSynchronizationManager.isActualTransactionActive()||!TenantScope.requireCurrent().tenantId().equals(p.tenantId())||!TenantScope.requireCurrent().subjectId().equals(p.actorId()))deny();}
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String str(Map<String,Object> row,String key){var v=row.get(key);if(v==null)bad();return v.toString();}private static String text(JsonNode n,String key){return n.required(key).textValue();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
