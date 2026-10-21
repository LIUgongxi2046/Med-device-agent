package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.*;
import com.ruoyi.medical.workflow.TaskSourceVerifierStageProtocolV1.*;
import jakarta.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;

/** Review-only N05 COMMIT. Current node authority writes; original recovery only reads.
 * Only complete passing reviews satisfy N05; never N06, adoption or run success. */
public final class TaskSourceVerifierStageCommitService {
    static final String ACTION="task.source_verifier.stage.committed.v1";
    static final String MANIFEST=manifest();
    private final JdbcTemplate db;private final TaskSourceVerifierSchema json;private final TaskRunActivityJournal journal;
    private final TaskRunSourceVerifierProtocolBinding protocols;private final TaskRunGraphService graphs;private final TaskSourceVerifierReviewRecovery reviews;private final TaskSourceVerifierDispatchJournal dispatches;
    private final JsonMapper reader=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).build();
    public TaskSourceVerifierStageCommitService(DataSource data,TaskSourceVerifierSchema json,PermissionSchema permission,TaskRunGraphService graphs,TaskSourceVerifierReviewRecovery reviews,TaskSourceVerifierDispatchJournal dispatches,TaskRunSourceVerifierProtocolBinding protocols){this.protocols=protocols;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.graphs=graphs;this.reviews=reviews;this.dispatches=dispatches;journal=new TaskRunActivityJournal(db,permission);}
    CommitV1 commit(FormalTaskNodeAuthority.Verified node,WorkflowProtocol.Reference ref,DispatchedV1 original,ReviewCompletedV1 expected)throws Exception{
        node.requireUnchanged();protocols.requireAccepted(node.controlAdmission(),ref.runId()).requireUnchanged();expected.require(ref,original);var input=node.currentVerifierInput();var i=input.input();var d=original.dispatch();same(i.runId(),ref.runId());same(i.stepId(),d.stepId());same(input.inputSha256(),d.inputSha256());same(i.targetSections(),original.command().chapterIds());same(i.candidateId(),expected.candidateId());same(i.bodyArtifactId(),expected.targetRevision());same(i.bodyObject().sha256(),expected.targetSha256());
        var actual=reviews.stageFacts(dispatches.forCommit(node,ref,d)).orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));same(json.tree(expected),actual);var a=node.controlAdmission();var graph=graphs.require(a,ref.runId());same(graph.graphId(),i.graphId());same(graph.graphSha256(),i.graphSha256());if(!graph.requiredStages().contains("N05"))bad();
        var old=read(a,ref,original);if(old.isPresent()){same(expected,old.get().review());verifyStep(ref,old.get());return old.get();}
        var row=step(ref,d.stepId());same("RUNNING",text(row,"status"));same(d.inputSha256(),text(row,"input_sha256"));same(Long.toString(d.leaseGeneration()),number(row,"lease_generation"));if(row.get("lease_owner")==null||row.get("lease_expires_at")==null)bad();
        var body=body(ref,original,expected,graph.graphId(),graph.graphSha256());String id=derived(d.commandId(),ACTION);var result=new CommitV1("1.0.0",original,expected,id,json.hash(body));var witness=json.tree(Map.of("body",body,"receipt",result));
        input.requireUnchanged();node.requireUnchanged();graph.requireUnchanged();same(actual,reviews.stageFacts(dispatches.forCommit(node,ref,d)).orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE)));
        journal.write(a,ref.projectId(),ref.runId(),id,ACTION,key(d.stepId()),d.commandId(),json.hash(original),classification(a,ref),"1.0.0",MANIFEST,witness);
        changed(db.update("UPDATE run_step SET status=?,output_ref='[]',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND status='RUNNING' AND revision=? AND input_sha256=? AND lease_generation=? AND lease_owner=? AND lease_expires_at>UTC_TIMESTAMP(6)",expected.passed()?"SUCCEEDED":"WAITING_INPUT",ref.tenantId(),ref.projectId(),ref.runId(),d.stepId(),number(row,"revision"),d.inputSha256(),d.leaseGeneration(),text(row,"lease_owner")));return result;
    }
    Optional<CommitV1> recover(EnterpriseAccess.Admission a,HttpServletRequest http,WorkflowProtocol.Reference ref,DispatchedV1 original)throws Exception{
        var proof=dispatches.requireOriginal(a,http,ref,original.dispatch());var saved=read(a,ref,original);if(saved.isEmpty())return Optional.empty();same(json.tree(saved.get().review()),reviews.original(proof).orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE)));verifyStep(ref,saved.get());proof.requireUnchanged();return saved;
    }
    InventoryV1 inventory(EnterpriseAccess.Admission a,HttpServletRequest http,WorkflowProtocol.Reference ref)throws Exception{
        var graph=graphs.requireOriginal(a,ref.runId());var assigned=assignments(a,ref,graph.graphId(),graph.graphSha256());var commands=dispatches.commands(a,http,ref);var originals=new ArrayList<DispatchedV1>();var committed=new ArrayList<CommitV1>();
        for(var proof:dispatches.dispatched(a,http,ref)){var d=proof.dispatch();var command=commands.stream().filter(c->c.stepId().equals(d.stepId())).findFirst().orElseThrow();var original=new DispatchedV1(command,d);originals.add(original);recover(a,http,ref,original).ifPresent(committed::add);proof.requireUnchanged();}
        graph.requireUnchanged();return new InventoryV1("1.0.0",ref.runId(),graph.graphId(),graph.graphSha256(),obligation(ref,graph.graphId(),graph.graphSha256()),graph.nodes().stream().map(TaskRunGraphService.Node::sectionId).distinct().sorted().toList(),assigned,commands,originals,committed);
    }
    /** Lifecycle only consumes genuine COMMIT witnesses. It does not coerce these
     * into ordinary Step Receipt or give a review permission to adopt itself. */
    List<CommitV1> loadLifecycle(TaskRunLifecycleAuthority.Proof proof)throws Exception{
        proof.require(proof.operation());var ref=proof.reference();var result=new ArrayList<CommitV1>();var rows=db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND subject_id=? AND target_id=? AND operation_name=? ORDER BY operation_id LIMIT ? FOR UPDATE",ref.tenantId(),proof.admission().actorId(),ref.runId(),ACTION,ref.maximumSteps()+1);if(rows.size()>ref.maximumSteps())bad();if(!rows.isEmpty())protocols.requireAccepted(proof.admission(),ref.runId()).requireUnchanged();
        for(var row:rows){var witness=json.read(text(row,"response_ref"));var receipt=decode(witness);var saved=read(proof.admission(),ref,receipt.original()).orElseThrow();same(receipt,saved);same(proof.graph().graphId(),witness.required("body").required("graph_id").asText());same(proof.graph().graphSha256(),witness.required("body").required("graph_sha256").asText());
            var submission=one(db.queryForList("SELECT receipt_json,receipt_sha256,created_by FROM task_source_verifier_submission WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId(),receipt.review().stepId(),receipt.review().actionId()));var review=json.read(text(submission,"receipt_json"));same(json.tree(receipt.review()),review);same(json.hash(review),text(submission,"receipt_sha256"));same(proof.admission().actorId(),text(submission,"created_by"));
            same(json.tree(receipt.review()),reviews.stageFacts(dispatches.forLifecycle(proof,receipt.original().dispatch())).orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE)));verifyStep(ref,receipt);result.add(receipt);
        }proof.require(proof.operation());return List.copyOf(result);
    }
    boolean satisfiedLifecycle(TaskRunLifecycleAuthority.Proof p,List<CommitV1> receipts)throws Exception{
        var required=new HashSet<>(p.graph().nodes().stream().map(TaskRunGraphService.Node::sectionId).toList());var assigned=assignments(p.admission(),p.reference(),p.graph().graphId(),p.graph().graphSha256());var seen=new HashSet<String>();for(var s:assigned){var receipt=receipts.stream().filter(r->r.original().command().ready().equals(s)).findFirst();if(receipt.isEmpty()||!receipt.get().review().passed()||s.chapterIds().stream().anyMatch(c->!seen.add(c)))return false;}return !required.isEmpty()&&required.equals(seen)&&assigned.size()==receipts.size();
    }
    /** Current downstream consumer proof. It reads an actual stage journal, never creates a commit. */
    ForConsumer requireForConsumer(TaskSourceVerifierInputAuthority.Verified input)throws Exception {
        return new ForConsumer(input,consumerCommit(input));
    }
    private CommitV1 consumerCommit(TaskSourceVerifierInputAuthority.Verified input)throws Exception {
        input.requireUnchanged();var i=input.input();
        var rows=db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND subject_id=? AND target_id=? AND operation_name=? AND idempotency_key=? LIMIT 2 FOR UPDATE",i.tenantId(),input.admission().actorId(),i.runId(),ACTION,key(i.stepId()));
        var witness=json.read(text(one(rows),"response_ref"));var decoded=decode(witness);
        var ref=reader.treeToValue(witness.required("body").required("reference"),WorkflowProtocol.Reference.class);
        same(i.tenantId(),ref.tenantId());same(i.projectId(),ref.projectId());same(i.runId(),ref.runId());same(i.stepId(),decoded.review().stepId());same(i.candidateId(),decoded.review().candidateId());same(i.bodyArtifactId(),decoded.review().targetRevision());same(i.bodyObject().sha256(),decoded.review().targetSha256());
        protocols.requireAccepted(input.admission(),ref.runId()).requireUnchanged();
        var saved=read(input.admission(),ref,decoded.original()).orElseThrow(()->new TenantAccessException(ErrorCode.STEP_NOT_READY));same(decoded,saved);
        same(i.graphId(),witness.required("body").required("graph_id").asText());same(i.graphSha256(),witness.required("body").required("graph_sha256").asText());
        var facts=dispatches.forConsumer(input,ref,saved.original().dispatch());same(json.tree(saved.review()),reviews.stageFacts(facts).orElseThrow(()->new TenantAccessException(ErrorCode.STEP_NOT_READY)));
        if(!saved.review().passed())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);verifyStep(ref,saved);input.requireUnchanged();return saved;
    }
    final class ForConsumer {
        private final TaskSourceVerifierInputAuthority.Verified input;private final CommitV1 receipt;
        private ForConsumer(TaskSourceVerifierInputAuthority.Verified input,CommitV1 receipt){this.input=input;this.receipt=receipt;}
        void requireUnchanged()throws Exception{same(receipt,consumerCommit(input));}
        CommitV1 receipt(){return receipt;}
        TaskRunResumeOriginalAuthority.Source source(){var source=input.originalSource();if(source==null)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return source;}
        JsonNode originalReceipt(){return json.tree(receipt);}
        String originalReceiptId(){return receipt.receiptId();}
        String originalReceiptSha256()throws Exception{return json.hash(originalReceipt());}

        TaskSourceVerifierDispatchJournal.StageFacts facts()throws Exception {
            requireUnchanged();var row=one(db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND operation_id=? LIMIT 2 FOR UPDATE",input.tenantId(),receipt.receiptId()));
            var witness=json.read(text(row,"response_ref"));var ref=reader.treeToValue(witness.required("body").required("reference"),WorkflowProtocol.Reference.class);
            return dispatches.forConsumer(input,ref,receipt.original().dispatch());
        }
    }
    private List<ReadyStepV1> assignments(EnterpriseAccess.Admission a,WorkflowProtocol.Reference ref,String graph,String graphHash)throws Exception{
        var rows=db.queryForList("SELECT i.*,s.status AS step_status,s.input_sha256 AS step_input,s.section_id,n.canonical_definition,n.definition_sha256 FROM task_source_verifier_input i JOIN run_step s ON s.tenant_id=i.tenant_id AND s.project_id=i.project_id AND s.run_id=i.run_id AND s.step_id=i.step_id JOIN runtime_node_definition n ON n.tenant_id=i.tenant_id AND n.project_id=i.project_id AND n.run_id=i.run_id AND n.step_id=i.step_id AND n.node_kind='N05' WHERE i.tenant_id=? AND i.project_id=? AND i.run_id=? ORDER BY i.step_id LIMIT ? FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId(),ref.maximumSteps()+1);if(rows.size()>ref.maximumSteps())bad();var result=new ArrayList<ReadyStepV1>();for(var row:rows){var input=json.input(text(row,"canonical_input").getBytes(java.nio.charset.StandardCharsets.UTF_8));same(a.actorId(),text(row,"created_by"));same(ref.tenantId(),input.tenantId());same(ref.projectId(),input.projectId());same(ref.runId(),input.runId());same(graph,input.graphId());same(graphHash,input.graphSha256());same(json.hash(input),text(row,"input_sha256"));same(text(row,"input_sha256"),text(row,"step_input"));same(json.hash(json.read(text(row,"receipt_json"))),text(row,"receipt_sha256"));if(input.targetSections().size()!=1)bad();same(input.targetSections().get(0),text(row,"section_id"));var node=TaskSourceVerifierMaterializer.definition(json,input,text(row,"input_sha256"),text(row,"receipt_sha256"));same(node,json.read(text(row,"canonical_definition")));same(json.hash(node),text(row,"definition_sha256"));result.add(new ReadyStepV1(input.stepId(),text(row,"input_sha256"),input.targetSections()));}return List.copyOf(result);
    }
    private Optional<CommitV1> read(EnterpriseAccess.Admission a,WorkflowProtocol.Reference ref,DispatchedV1 original)throws Exception{
        original.command().require(ref);var d=original.dispatch();var value=journal.read(a,ref.projectId(),ref.runId(),derived(d.commandId(),ACTION),ACTION,key(d.stepId()),d.commandId(),json.hash(original),classification(a,ref),"1.0.0",MANIFEST);if(value.isEmpty())return Optional.empty();var result=decode(value.get());result.require(ref);same(original,result.original());var b=value.get().required("body");same(json.tree(ref),b.required("reference"));same(body(ref,original,result.review(),b.required("graph_id").asText(),b.required("graph_sha256").asText()),b);return Optional.of(result);
    }
    private CommitV1 decode(JsonNode witness)throws Exception{if(!witness.isObject()||witness.size()!=2||!witness.has("body")||!witness.has("receipt"))bad();var result=reader.treeToValue(witness.required("receipt"),CommitV1.class);same(derived(result.original().command().commandId(),ACTION),result.receiptId());same(json.hash(witness.required("body")),result.receiptSha256());return result;}
    private JsonNode body(WorkflowProtocol.Reference ref,DispatchedV1 original,ReviewCompletedV1 review,String graph,String graphHash)throws Exception{var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("kind","TASK_N05_CHAPTER_COMMIT").put("graph_id",graph).put("graph_sha256",graphHash).put("obligation_sha256",obligation(ref,graph,graphHash));n.set("reference",json.tree(ref));n.set("original",json.tree(original));n.set("review",json.tree(review));return n;}
    private String obligation(WorkflowProtocol.Reference ref,String graph,String hash)throws Exception{return json.hash(Map.of("schema_version","1.0.0","kind","TASK_REQUIRED_STAGE","tenant_id",ref.tenantId(),"project_id",ref.projectId(),"run_id",ref.runId(),"graph_id",graph,"graph_sha256",hash,"stage_kind","N05"));}
    private void verifyStep(WorkflowProtocol.Reference ref,CommitV1 c)throws Exception{var d=c.original().dispatch();var row=step(ref,d.stepId());same(c.review().passed()?"SUCCEEDED":"WAITING_INPUT",text(row,"status"));same(d.inputSha256(),text(row,"input_sha256"));same(Long.toString(d.leaseGeneration()),number(row,"lease_generation"));same(json.tree(List.of()),json.read(text(row,"output_ref")));}
    private Map<String,Object> step(WorkflowProtocol.Reference r,String step){return one(db.queryForList("SELECT * FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",r.tenantId(),r.projectId(),r.runId(),step));}
    private String classification(EnterpriseAccess.Admission a,WorkflowProtocol.Reference r){return one(db.query("SELECT classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? LIMIT 2 FOR UPDATE",(rs,n)->rs.getString(1),r.tenantId(),r.projectId(),r.runId(),a.actorId()));}
    private static String key(String step){return "source-verifier-stage:"+step;}
    private static String manifest(){try{return WireValues.sha256("TASK_N05_CHAPTER_COMMIT:1.0.0:original-dispatch+exact-at07-independent-review+coverage+assigned-chapters".getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
}
