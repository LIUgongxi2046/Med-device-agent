package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Current, exact downstream authority for already committed independent N05 results.
 * Original receipts alone cannot authorize reads. No N05 lease or new invocation is minted. */
public final class TaskSourceVerifierCommittedReviewAuthority {
    private TaskRunResumeOriginalAuthority resumeOriginals;
    public TaskSourceVerifierCommittedReviewAuthority withOriginalReaders(TaskRunResumeOriginalAuthority value){resumeOriginals=Objects.requireNonNull(value);return this;}

    private final JdbcTemplate db;private final TaskSourceVerifierSchema json;private final TaskSourceVerifierInputAuthority inputs;
    private final TaskSourceVerifierStageCommitService commits;private final TaskSourceVerifierReviewRecovery originals;
    private final ResourceReadAuthority resources;private final ArtifactRegistryMapper artifacts;
    public TaskSourceVerifierCommittedReviewAuthority(DataSource data,TaskSourceVerifierSchema json,TaskSourceVerifierInputAuthority inputs,
            TaskSourceVerifierStageCommitService commits,TaskSourceVerifierReviewRecovery originals,ResourceReadAuthority resources,ArtifactRegistryMapper artifacts){
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.inputs=inputs;this.commits=commits;this.originals=originals;this.resources=resources;this.artifacts=artifacts;
    }
    public Verified require(TaskNodeProductionAuthority.Verified production,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,String candidateId)throws Exception {
        WireValues.uuid(candidateId);return new Verified(production,publication,policy,candidateId,inspect(production,publication,policy,candidateId));
    }
    private record State(TaskSourceVerifierInputAuthority.Verified input,ArtifactProtocol.ReviewFindings findings,
            TaskSourceVerifierReviewRecovery.ConsumerOutputs outputs,JsonNode witness){}
    private State inspect(TaskNodeProductionAuthority.Verified production,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,String candidateId)throws Exception {
        production.requireUnchanged();
        String step=TaskSourceVerifierPreparation.stepId(production.runId(),candidateId);
        TaskSourceVerifierInputAuthority.Verified input;
        if(TaskRunSuccessReuse.exists(db,production.tenantId(),production.runId(),step)){
            if(resumeOriginals==null)deny();var source=resumeOriginals.requireSaved(production,step);
            input=inputs.requireOriginal(source,publication,policy);
        }else input=inputs.require(production.admission(),production.runId(),step,publication,policy,production);
        same(candidateId,input.input().candidateId());var committed=commits.requireForConsumer(input);
        var saved=originals.consumerOutputs(committed.facts());same(json.tree(committed.receipt().review()),saved.receipt());
        var artifact=ArtifactEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.ARTIFACT,saved.findings(),input.evidenceContract().artifactRef(null),input.evidenceContract().artifactSha()).read();
        if(!(artifact.output() instanceof ArtifactProtocol.ReviewOutput))bad();var findings=((ArtifactProtocol.ReviewOutput)artifact.output()).content();
        if(!findings.coverageComplete()||!findings.issueProposals().isEmpty()||!"SUCCEEDED".equals(text(saved.receipt(),"status")))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var marks=one(db.queryForList("SELECT issue_watermark,evidence_watermark FROM document_revision_state WHERE tenant_id=? AND project_id=? AND document_revision=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.target().candidate().baseDocumentRevision()));
        var check=one(db.queryForList("SELECT issue_watermark,evidence_watermark,input_watermarks,status,scope_manifest_id FROM check_run WHERE tenant_id=? AND project_id=? AND check_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),text(saved.receipt(),"check_id")));
        for(String key:List.of("issue_watermark","evidence_watermark"))if(!str(marks,key).equals(str(check,key)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        same(json.hash(marks),json.hash(json.read(str(check,"input_watermarks"))));same("SUCCEEDED",str(check,"status"));same(input.snapshotId(),str(check,"scope_manifest_id"));
        // The only non-replay capability is this exact pair of registered output identities.
        var read=new CommittedRead(input,committed,saved);
        var grants=new ArrayList<JsonNode>();
        for(String key:List.of("findings_artifact_id","coverage_artifact_id")){
            String id=text(saved.receipt(),key);var row=one(artifacts.artifact(id));same(input.tenantId(),row.tenantId());same(input.projectId(),row.projectId());
            var binding=one(artifacts.binding(id));same("TASK",binding.resourceKind());same(input.taskId(),binding.resourceId());same(row.accessSetId(),binding.accessSetId());
            same(new HashSet<>(input.contentMembers()),new HashSet<>(artifacts.members(row.accessSetId())));
            var grant=resources.requireSourceVerifierOutput(production.admission(),new PermissionProtocol.Scope(input.tenantId(),input.projectId(),"ARTIFACT",id),"artifact.read",TaskSourceVerifierReviewService.PURPOSE,read);
            grants.add(AgentPublicationWitness.tree(grant));
        }
        var witness=json.tree(Map.of("commit",committed.receipt(),"review",saved.receipt(),"findings_receipt",saved.findingsReceipt(),"coverage_receipt",saved.coverageReceipt(),"watermarks",marks,"grants",grants));
        input.requireUnchanged();committed.requireUnchanged();return new State(input,findings,saved,witness);
    }
    /** Private minting follows actual current input, physical originals and passing stage commit.
     * A generic control admission never learns these output IDs as an expanded scope. */
    static final class CommittedRead {
        private final TaskSourceVerifierInputAuthority.Verified input;private final TaskSourceVerifierStageCommitService.ForConsumer committed;
        private final TaskSourceVerifierReviewRecovery.ConsumerOutputs outputs;
        private CommittedRead(TaskSourceVerifierInputAuthority.Verified input,TaskSourceVerifierStageCommitService.ForConsumer committed,TaskSourceVerifierReviewRecovery.ConsumerOutputs outputs){this.input=input;this.committed=committed;this.outputs=outputs;}
        void requireResource(EnterpriseAccess.Admission admission,PermissionProtocol.Scope scope,String action,String purpose)throws java.io.IOException {
            if(admission!=input.admission())deny();
            try{input.requireUnchanged();committed.requireUnchanged();}catch(RuntimeException e){throw e;}catch(Exception e){throw new java.io.IOException("Committed independent review recheck failed",e);}
            if(!input.tenantId().equals(scope.tenant_id())||!input.projectId().equals(scope.project_id())||!"ARTIFACT".equals(scope.resource_kind())
                    ||!Set.of(text(outputs.receipt(),"findings_artifact_id"),text(outputs.receipt(),"coverage_artifact_id")).contains(scope.resource_id())
                    ||!"artifact.read".equals(action)||!TaskSourceVerifierReviewService.PURPOSE.equals(purpose))deny();
        }
    }
    public final class Verified {
        private final TaskNodeProductionAuthority.Verified production;private final AgentPublicationAuthority.VerifiedPublication publication;
        private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final String candidate;private final State state;
        private Verified(TaskNodeProductionAuthority.Verified p,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,String candidate,State state){production=p;this.publication=publication;this.policy=policy;this.candidate=candidate;this.state=state;}
        public void requireUnchanged()throws Exception{if(!state.witness.equals(inspect(production,publication,policy,candidate).witness))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        public TaskSourceVerifierTargetAuthority.Verified target(){return state.input.target();}
        public ArtifactProtocol.ReviewFindings findings(){return state.findings;}
        public String checkId(){return text(state.outputs.receipt(),"check_id");}
        public String findingsArtifactId(){return text(state.outputs.receipt(),"findings_artifact_id");}
        public String coverageArtifactId(){return text(state.outputs.receipt(),"coverage_artifact_id");}
        public String receiptSha256()throws Exception{return json.hash(state.outputs.receipt());}
        public JsonNode pins(){return state.witness.deepCopy();}
        public JsonNode coverage(){return state.outputs.coverage().deepCopy();}
        public Map<String,InterfaceProtocol.ObjectRef> readObjects(){return Map.of(findingsArtifactId(),state.outputs.findingsObject(),coverageArtifactId(),state.outputs.coverageObject());}
        public Map<String,JsonNode> expectedReadPayloads(){return Map.of(findingsArtifactId(),state.outputs.findings().deepCopy(),coverageArtifactId(),state.outputs.coverage().deepCopy());}
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String str(Map<String,Object> row,String key){return Objects.requireNonNull(row.get(key)).toString();}
    private static String text(JsonNode n,String key){return n.required(key).textValue();}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
