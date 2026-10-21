package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.workflow.TaskRunStageProtocolV2.FieldCompletionV2;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
/** Current downstream read authority for a genuinely committed physical N03 output, without its former lease. */
public final class TaskStrictFieldWriterReadAuthority {
    private TaskRunResumeOriginalAuthority resumeOriginals;private TaskStrictFieldInputAuthority inputs;
    public TaskStrictFieldWriterReadAuthority withOriginalReaders(TaskRunResumeOriginalAuthority originals,TaskStrictFieldInputAuthority inputs){this.resumeOriginals=Objects.requireNonNull(originals);this.inputs=Objects.requireNonNull(inputs);return this;}

    private final JdbcTemplate db;private final TaskStrictFieldOutputLedger outputs;private final TaskRunStageCommitServiceV2 commits;
    private final TaskStrictFieldTargetAuthority targets;private final StrictComparisonRunScope scopes;private final StrictComparisonPolicyService policies;
    private final TaskStrictFieldSchema json;private final ResourceReadAuthority resources;private final DependencyLineage lineage;private final VersionResourceAuthority versions;
    private final StrictComparisonStore policyStore;private final ArtifactRegistryMapper artifacts;private final SourceReadMapper sourceRows;private final SourceReadAuthority sources;private final EvaluationReferences profiles;private final TaskFieldTargetPolicy bounds;
    public TaskStrictFieldWriterReadAuthority(DataSource data,TaskStrictFieldOutputLedger outputs,TaskRunStageCommitServiceV2 commits,TaskStrictFieldTargetAuthority targets,StrictComparisonRunScope scopes,StrictComparisonPolicyService policies,TaskStrictFieldSchema json,ResourceReadAuthority resources,DependencyLineage lineage,VersionResourceAuthority versions,ArtifactRegistryMapper artifacts,SourceReadMapper sourceRows,SourceReadAuthority sources,EvaluationReferences profiles,TaskFieldTargetPolicy bounds,StrictComparisonStore policyStore){this.policyStore=policyStore;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.outputs=outputs;this.commits=commits;this.targets=targets;this.scopes=scopes;this.policies=policies;this.json=json;this.resources=resources;this.lineage=lineage;this.versions=versions;this.artifacts=artifacts;this.sourceRows=sourceRows;this.sources=sources;this.profiles=profiles;this.bounds=bounds;}
    public ForWriter requireForWriter(TaskNodeProductionAuthority.Verified production,String artifactId,String receiptSha256)throws Exception{return new ForWriter(production,artifactId,receiptSha256,null,null,null,inspect(production,artifactId,receiptSha256,null,null,null));}
    public ForWriter requireForWriter(TaskNodeProductionAuthority.Verified p,String artifact,String receipt,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception{
        var rows=db.queryForList("SELECT r.step_id FROM task_run_success_reuse r JOIN task_strict_field_output_registration o ON o.tenant_id=r.tenant_id AND o.run_id=r.original_run_id AND o.step_id=r.original_step_id WHERE r.tenant_id=? AND r.run_id=? AND r.stage_kind='N03' AND o.artifact_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.runId(),artifact);
        if(rows.isEmpty())return requireForWriter(p,artifact,receipt);
        if(resumeOriginals==null)deny();var original=requireOriginal(resumeOriginals.requireSaved(p,one(rows).get("step_id").toString()),publication,policy);if(!artifact.equals(original.outputArtifactId())||!receipt.equals(original.outputReceiptSha256()))bad();return original;
    }
    public ForWriter requireOriginal(TaskRunResumeOriginalAuthority.Source source,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception{
        source.requireUnchanged();if(!"N03".equals(source.stageKind()))deny();var p=source.successor();
        var row=one(db.queryForList("SELECT artifact_id,receipt_sha256 FROM task_strict_field_output_registration WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),source.originalRunId(),source.originalStepId()));
        String artifact=row.get("artifact_id").toString(),receipt=row.get("receipt_sha256").toString();
        return new ForWriter(p,artifact,receipt,source,publication,policy,inspect(p,artifact,receipt,source,publication,policy));
    }
    private record State(TaskStrictFieldInput input,TaskStrictFieldOutputLedger.Registered output,TaskRunStageCommitServiceV2.ForConsumer committed,JsonNode witness){}
    private State inspect(TaskNodeProductionAuthority.Verified p,String artifact,String receiptHash,TaskRunResumeOriginalAuthority.Source source,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy executionPolicy)throws Exception{
        String originalRun=source==null?p.runId():source.originalRunId();if(source!=null)source.requireUnchanged();
        p.requireUnchanged();WireValues.uuid(artifact);WireValues.hash(receiptHash);var a=p.admission();resources.require(a,new PermissionProtocol.Scope(p.tenantId(),p.projectId(),"TASK",p.taskId()),"task.field_check_result.read",TaskStrictFieldNode.PURPOSE);
        var row=one(db.queryForList("SELECT * FROM task_strict_field_output_registration WHERE tenant_id=? AND project_id=? AND run_id=? AND task_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),originalRun,p.taskId(),artifact));if(!receiptHash.equals(row.get("receipt_sha256")))bad();var receipt=json.read(row.get("receipt_json").toString());var output=outputs.original(a,receipt.required("result_receipt"));if(!receiptHash.equals(output.receiptSha256())||!artifact.equals(output.artifactId()))bad();
        var inputRow=one(db.queryForList("SELECT * FROM task_strict_field_input WHERE tenant_id=? AND project_id=? AND run_id=? AND input_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),originalRun,receipt.required("input_id").asText()));var input=json.input(inputRow.get("canonical_input").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));if(source==null&&!input.replaySha256().equals(p.replay().rawSha256())||!json.hash(input).equals(receipt.required("input_sha256").asText())||!input.taskId().equals(p.taskId()))bad();
        var reservation=one(db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND operation_id=? FOR UPDATE",p.tenantId(),TaskStrictFieldDispatchReservation.reservationId(p.tenantId(),originalRun,input.stepId())));String command=json.read(reservation.get("response_ref").toString()).required("request").required("command_id").asText();var locator=new FieldCompletionV2("2.0.0","N03","FIELD_TARGET",originalRun,input.stepId(),command,input.inputId(),receipt.required("input_sha256").asText(),receipt.required("action_id").asText(),Long.parseLong(receipt.required("lease_generation").asText()),receipt.required("result_id").asText(),receipt.required("result_sha256").asText(),receiptHash);
        if(source!=null){source.requireOriginal(a,input.runId(),input.stepId());if(inputs==null||publication==null||executionPolicy==null)deny();var next=inputs.require(a,p.runId(),source.successorStepId(),publication,executionPolicy,p);TaskResumeSemanticInputs.require(json.tree(input),next.manifest(),"N03");if(!json.hash(input).equals(source.pins().required("original_input_sha256").asText())||!next.inputSha256().equals(source.pins().required("successor_input_sha256").asText()))bad();next.requireUnchanged();}
        var committed=source==null?commits.requireForConsumer(p,locator):commits.requireForConsumer(source,locator);if(!committed.receipt().satisfied())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var target=targets.require(p,input.targetArtifactId(),input.targetRegistrationSha256());if(!target.manifest().equals(input.targetManifest())||!target.reference().equals(input.targetObject()))bad();var scope=scopes.require(p);var selected=new TreeMap<String,String>();var selectedDecisions=new TreeMap<String,String>();var approvalWitness=new ArrayList<JsonNode>();for(var entry:input.policies()){var policy=policies.requireApprovedFixedRun(scope,entry.policy().version_id(),entry.policy().content_sha256());selected.put(policy.revisionId(),policy.digest());selectedDecisions.put(policy.revisionId(),policy.decisionId());approvalWitness.add(json.tree(Map.of("revision",policy.revisionId(),"digest",policy.digest(),"decision",policy.decisionId())));policy.requireUnchanged();}
        var rule=one(db.queryForList("SELECT manifest_json,content_sha256 FROM task_strict_field_rule_set WHERE tenant_id=? AND revision_id=? FOR UPDATE",p.tenantId(),output.ruleRevision()));var ruleBody=json.read(rule.get("manifest_json").toString());if(!json.hash(ruleBody).equals(receipt.required("rule_sha256").asText())||!json.hash(ruleBody).equals(rule.get("content_sha256")))bad();var actual=new TreeMap<String,String>();for(var policy:ruleBody.required("policies")){if(actual.put(policy.required("revision").asText(),policy.required("digest").asText())!=null||!Objects.equals(selectedDecisions.get(policy.required("revision").asText()),policy.required("decision_id").asText()))bad();}if(!actual.equals(selected))bad();
        var pins=new TreeMap<String,String>();pins.put(output.ruleRevision(),receipt.required("rule_sha256").asText());
        // These non-replay pins come only from actual fixed input policies and the independently
        // authenticated original rule-set. No arbitrary ancestor becomes a capability.
        for(var snapshot:ruleBody.required("policies")){
            String id=snapshot.required("revision").asText();var original=policyStore.read(a,p.projectId(),StrictComparisonStore.Kind.POLICY,id);
            if(!original.digest().equals(selected.get(id)))bad();pins.put(id,original.digest());
            String decision=snapshot.required("decision_id").asText(),digest=snapshot.required("decision_sha256").asText();var seen=new HashSet<String>();
            while(decision!=null){if(!seen.add(decision)||seen.size()>bounds.lineageLimits().maximumVersions())bad();
                var prior=policyStore.read(a,p.projectId(),StrictComparisonStore.Kind.DECISION,decision);
                if(!id.equals(prior.parentId())||digest!=null&&!digest.equals(prior.digest()))bad();
                String previous=pins.put(decision,prior.digest());if(previous!=null&&!previous.equals(prior.digest()))bad();
                var priorId=prior.body().required("baseline").required("state").required("decision_id");decision=priorId.isNull()?null:priorId.asText();digest=null;
            }
        }
        var read=new CommittedRead(p,output,committed,Map.copyOf(pins),source);
        var artifactGrant=resources.requireStrictFieldOutput(a,new PermissionProtocol.Scope(p.tenantId(),p.projectId(),"ARTIFACT",artifact),"artifact.read",TaskStrictFieldNode.PURPOSE,read);
        var graph=lineage.inspect(output.ruleRevision(),bounds.lineageLimits());var artifactRequirements=new HashMap<String,ArtifactRegistryProtocol.ReadRequirement>();var sourceProofs=new HashMap<String,SourceReadProtocol.VerifiedSource>();var policy=p.replay().content().required("execution_policy");for(var version:graph.versions().values()){
            if(version.projectId()!=null&&!p.projectId().equals(version.projectId()))deny();var sourceRecords=sourceRows.source(version.id());if(sourceRecords.size()>1)bad();if(!sourceRecords.isEmpty()){var pin=one(input.fixedSources().stream().filter(s->s.sourceVersion().equals(version.id())).toList());sourceProofs.put(version.id(),sources.require(a,new SourceReadProtocol.ReadRequirement(pin.sourceVersion(),pin.rightsRevision(),p.projectId(),input.snapshotId(),pin.parseVersion(),null,SourceReadProtocol.Use.FORMAL,pin.purpose(),policy.required("environment").asText(),policy.required("region").asText(),null)));}
            if("ARTIFACT".equals(version.entityKind())){var binding=one(artifacts.binding(version.id()));var profile=profiles.profile(binding.schemaRef(),TaskStrictFieldNode.PURPOSE,binding.resourceKind());artifactRequirements.put(version.id(),new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(p.tenantId(),binding.projectId(),binding.resourceKind(),binding.resourceId()),profile.action(),TaskStrictFieldNode.PURPOSE,profile.schemaRef(),profile.schemaDocumentSha256(),profile.artifactSchemaManifest(),bounds.lineageLimits()));}
        }
        var verified=versions.requireStrictFieldOutput(a,graph,TaskStrictFieldNode.PURPOSE,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.FORMAL,null),artifactRequirements,read);for(var v:verified.values())if(v.owner().sourceVersion()!=null){var sourceProof=sourceProofs.get(v.owner().sourceVersion());if(sourceProof==null||!graph.reachableFrom(v.version().id()).contains(sourceProof.source().sourceVersion()))bad();}
        var outputGraph=lineage.inspect(artifact,bounds.lineageLimits());for(var member:artifacts.members(one(artifacts.artifact(artifact)).accessSetId())){var rights=one(sourceRows.rights(member.rightsRevision()));var pin=one(input.fixedSources().stream().filter(s->s.sourceVersion().equals(rights.sourceVersion())&&s.rightsRevision().equals(member.rightsRevision())).toList());sources.require(a,new SourceReadProtocol.ReadRequirement(pin.sourceVersion(),pin.rightsRevision(),p.projectId(),input.snapshotId(),pin.parseVersion(),null,SourceReadProtocol.Use.FORMAL,pin.purpose(),policy.required("environment").asText(),policy.required("region").asText(),null));if(!outputGraph.reachableFrom(member.upstreamVersionId()).contains(rights.sourceVersion()))bad();}
        target.requireUnchanged();scope.requireUnchanged();committed.requireUnchanged();p.requireUnchanged();return new State(input,output,committed,json.tree(Map.of("commit",committed.receipt(),"output",output.receipt(),"policies",approvalWitness,"versions",TaskFieldAuthorityWitness.tree(verified),"source",TaskFieldAuthorityWitness.tree(sourceProofs),"artifact_grant",TaskFieldAuthorityWitness.tree(artifactGrant))));
    }
    /** Only this authority can mint it after original output and SATISFIED stage COMMIT verification.
     * It is not a replacement Admission and cannot enlarge the generic control read scope. */
    static final class CommittedRead {
        private final TaskNodeProductionAuthority.Verified production;private final TaskRunResumeOriginalAuthority.Source source;
        private final TaskStrictFieldOutputLedger.Registered output;
        private final TaskRunStageCommitServiceV2.ForConsumer committed;
        private final Map<String,String> governancePins;
        private CommittedRead(TaskNodeProductionAuthority.Verified production,TaskStrictFieldOutputLedger.Registered output,
                TaskRunStageCommitServiceV2.ForConsumer committed,Map<String,String> pins,TaskRunResumeOriginalAuthority.Source source){this.source=source;this.production=production;this.output=output;this.committed=committed;this.governancePins=pins;}
        private void current(EnterpriseAccess.Admission admission)throws java.io.IOException {
            if(admission!=production.admission())deny();
            try{production.requireUnchanged();output.requireUnchanged();committed.requireUnchanged();if(!committed.receipt().satisfied()
                    ||!(source==null?production.runId():source.originalRunId()).equals(committed.completion().runId())
                    ||!output.receiptSha256().equals(committed.completion().producerReceiptSha256()))bad();}
            catch(RuntimeException e){throw e;}catch(Exception e){throw new java.io.IOException("Committed strict-field output recheck failed",e);}
        }
        void requireResource(EnterpriseAccess.Admission a,PermissionProtocol.Scope scope,String action,String purpose)throws java.io.IOException {
            current(a);if(!new PermissionProtocol.Scope(production.tenantId(),production.projectId(),"ARTIFACT",output.artifactId()).equals(scope)
                    ||!"artifact.read".equals(action)||!TaskStrictFieldNode.PURPOSE.equals(purpose))deny();
        }
        void requireGovernanceRoot(EnterpriseAccess.Admission a,DependencyLineage.VerifiedLineage graph,String purpose)throws java.io.IOException {
            current(a);if(!output.ruleRevision().equals(graph.root())||!TaskStrictFieldNode.PURPOSE.equals(purpose))deny();
        }
        void requireVersion(EnterpriseAccess.Admission a,ArtifactRegistryProtocol.VersionRow version)throws java.io.IOException {
            current(a);String digest=governancePins.get(version.id());
            if(digest==null){a.controlProof().requireVersion(version.id(),version.contentSha256());return;}
            if(!digest.equals(version.contentSha256())||!production.projectId().equals(version.projectId())
                    ||!(output.ruleRevision().equals(version.id())?"STRICT_FIELD_RULE_SET".equals(version.entityKind()):Set.of("STRICT_COMPARISON_POLICY","STRICT_COMPARISON_DECISION").contains(version.entityKind())))bad();
        }
    }
    public final class ForWriter {
        private final TaskNodeProductionAuthority.Verified production;private final String artifact,hash;private final State state;private final TaskRunResumeOriginalAuthority.Source source;private final AgentPublicationAuthority.VerifiedPublication publication;private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;
        private ForWriter(TaskNodeProductionAuthority.Verified production,String artifact,String hash,TaskRunResumeOriginalAuthority.Source source,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,State state){this.source=source;this.publication=publication;this.policy=policy;this.production=production;this.artifact=artifact;this.hash=hash;this.state=state;}
        public void requireUnchanged()throws Exception{var fresh=inspect(production,artifact,hash,source,publication,policy);if(!state.witness.equals(fresh.witness))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        public TaskRunResumeOriginalAuthority.Source source(){if(source==null)deny();return source;}
        public JsonNode originalReceipt(){return new com.fasterxml.jackson.databind.json.JsonMapper().valueToTree(state.committed.receipt());}
        public String originalReceiptId(){return state.committed.receipt().receiptId();}
        public String originalReceiptSha256()throws Exception{return json.hash(originalReceipt());}
        public ArtifactProtocol.FieldCheckResult content(){return state.output.content();}public String targetArtifactId(){return state.input.targetArtifactId();}public String outputArtifactId(){return artifact;}public ObjectRef outputObject(){return state.output.reference();}public String outputReceiptSha256(){return hash;}public TaskStrictFieldInput input(){return state.input;}
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
