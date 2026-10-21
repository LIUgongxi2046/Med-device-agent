package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** Accepted-start, immutable graph and real N02 input preparation. Every object
 * segment is outside SQL and rechecks current authority; original attempts are GET
 * recovery only. No provider call, invented predecessor, lease or final outcome. */
public final class TaskRunActivityPreparation {
    private org.springframework.beans.factory.ObjectProvider<TaskRunSuccessReuse> reuse;
    @org.springframework.beans.factory.annotation.Autowired void reuse(org.springframework.beans.factory.ObjectProvider<TaskRunSuccessReuse> value){reuse=value;}
    private final JdbcTemplate db;
    private final FormalRunControlAccess control;
    private final AgentPublicationAuthority publications;
    private final AgentExecutionPolicyAuthority policies;
    private final RuntimeRetentionAuthority retention;
    private final IdentityDirectoryPolicy identity;
    private final TaskRunBootstrapAuthority bootstrap;
    private final TaskNodeProductionAuthority production;
    private final TaskRunAcceptedStart accepted;
    private final TaskRunReplayLedger replayLedger;
    private final TaskRunReplayRegistration replayRegistration;
    private final TaskRunReplayContentAuthority content;
    private final TaskRunGraphService graphs;
    private final TaskNodeProducerAuthority producers;
    private final TaskNodeInputAuthority inputs;
    private final TaskNodeInputLedger inputLedger;
    private final TaskNodeInputRegistration inputRegistration;
    private org.springframework.beans.factory.ObjectProvider<TaskRunAssemblySuccessReuse> assemblyReuse;
    @org.springframework.beans.factory.annotation.Autowired void assemblyReuse(org.springframework.beans.factory.ObjectProvider<TaskRunAssemblySuccessReuse> value){assemblyReuse=value;}
    void prepareAssemblyReuse(HttpServletRequest http,Reference ref)throws Exception {assemblyReuse.getObject().prepare(http,ref);}
    private final TaskNodeMaterializer materializer;
    private final TaskRuntimeJournal journal;
    private final RuntimeObjectTransport objects;

    public TaskRunActivityPreparation(DataSource data,FormalRunControlAccess control,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,RuntimeRetentionAuthority retention,IdentityDirectoryPolicy identity,
            TaskRunBootstrapAuthority bootstrap,TaskNodeProductionAuthority production,TaskRunAcceptedStart accepted,
            TaskRunReplayLedger replayLedger,TaskRunReplayRegistration replayRegistration,TaskRunReplayContentAuthority content,
            TaskRunGraphService graphs,TaskNodeProducerAuthority producers,TaskNodeInputAuthority inputs,
            TaskNodeInputLedger inputLedger,TaskNodeInputRegistration inputRegistration,TaskNodeMaterializer materializer,
            TaskRuntimeJournal journal,RuntimeObjectTransport objects){
        this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.control=Objects.requireNonNull(control);
        this.publications=Objects.requireNonNull(publications);this.policies=Objects.requireNonNull(policies);
        this.retention=Objects.requireNonNull(retention);this.identity=Objects.requireNonNull(identity);
        this.bootstrap=Objects.requireNonNull(bootstrap);this.production=Objects.requireNonNull(production);this.accepted=Objects.requireNonNull(accepted);
        this.replayLedger=Objects.requireNonNull(replayLedger);this.replayRegistration=Objects.requireNonNull(replayRegistration);this.content=Objects.requireNonNull(content);
        this.graphs=Objects.requireNonNull(graphs);this.producers=Objects.requireNonNull(producers);this.inputs=Objects.requireNonNull(inputs);
        this.inputLedger=Objects.requireNonNull(inputLedger);this.inputRegistration=Objects.requireNonNull(inputRegistration);this.materializer=Objects.requireNonNull(materializer);
        this.journal=Objects.requireNonNull(journal);this.objects=Objects.requireNonNull(objects);
    }
    @FunctionalInterface public interface Operation<T>{T apply(Current current)throws Exception;}

    public <T>T prepare(HttpServletRequest http,Reference reference,Operation<T> operation)throws Exception{
        String command=command(reference);
        return control.withReads(http,reference.tenantId(),reference.runId(),command,reads->ThrowingSupplier.of(()->{
            var prepared=publications.prepareControl(reads,reads.replay().required("agent_release_id").asText(),"RUN_CREATION");
            String state=reads.execute(a->status(a,reference));
            if("QUEUED".equals(state)){
                var attempt=reads.execute(a->ThrowingSupplier.of(()->{
                    var p=queued(reads,a,prepared,reference);var original=replayLedger.requireExisting(p);
                    return replayLedger.requireAttempt(p,original);
                }).get());
                // Existing acceptance is mandatory before even physical preparation; no new launch can be made here.
                reads.execute(a->ThrowingSupplier.of(()->{requireAcceptanceLocator(a,reference);return null;}).get());
                var transfer=objects.transferVerified(attempt,()->reads.execute(a->ThrowingSupplier.of(()->{
                    var p=queued(reads,a,prepared,reference);replayLedger.requireAttempt(p,attempt.intent());content.require(p).requireUnchanged();return null;
                }).get()));
                reads.execute(a->ThrowingSupplier.of(()->{
                    var p=queued(reads,a,prepared,reference);var original=replayRegistration.readVerified(p,attempt,transfer);
                    var graph=graphs.prepare(p,original);graph.requireUnchanged();accepted.accept(p,http,reference);return null;
                }).get());
            }else if(!"RUNNING".equals(state))deny();
            var replayAttempt=reads.execute(a->ThrowingSupplier.of(()->replayLedger.requireOriginal(current(reads,a,prepared,http,reference).production)).get());
            var replayTransfer=objects.transferVerified(replayAttempt,()->reads.execute(a->ThrowingSupplier.of(()->{
                var c=current(reads,a,prepared,http,reference);replayLedger.requireOriginal(c.production,replayAttempt.intent());content.require(c.production).requireUnchanged();return null;
            }).get()));
            var registered=reads.execute(a->ThrowingSupplier.of(()->{
                var c=current(reads,a,prepared,http,reference);var r=replayRegistration.readOriginal(c.production,replayAttempt,replayTransfer);
                journal.prepareRun(c.production,c.graph).requireUnchanged();return r;
            }).get());
            var questions=reads.execute(a->ThrowingSupplier.of(()->readyQuestions(current(reads,a,prepared,http,reference))).get());
            for(String question:questions){
                var attempt=reads.execute(a->ThrowingSupplier.of(()->inputLedger.prepare(input(current(reads,a,prepared,http,reference),registered,question),inputKey(reference.runId(),question))).get());
                var transfer=objects.transferVerified(attempt,()->reads.execute(a->ThrowingSupplier.of(()->{
                    inputLedger.requireTransfer(input(current(reads,a,prepared,http,reference),registered,question),attempt);return null;
                }).get()));
                reads.execute(a->ThrowingSupplier.of(()->{
                    var p=input(current(reads,a,prepared,http,reference),registered,question);
                    inputRegistration.register(p,attempt,transfer);materializer.materialize(p,attempt);if(reuse!=null)reuse.getObject().materializeResearch(current(reads,a,prepared,http,reference),p.input().stepId());return null;
                }).get());
            }
            return reads.execute(a->ThrowingSupplier.of(()->{
                var c=current(reads,a,prepared,http,reference);if(reuse!=null)reuse.getObject().materializeField(c);var result=operation.apply(c);c.requireUnchanged();return result;
            }).get());
        }).get());
    }
    /** Current dispatch/commit authority only. This never rematerializes inputs or changes run state. */
    public <T>T withCurrent(HttpServletRequest http,Reference reference,Operation<T> operation)throws Exception{
        return control.withReads(http,reference.tenantId(),reference.runId(),command(reference),reads->ThrowingSupplier.of(()->{
            var prepared=publications.prepareControl(reads,reads.replay().required("agent_release_id").asText(),"RUN_CREATION");
            return reads.execute(a->ThrowingSupplier.of(()->{var c=current(reads,a,prepared,http,reference);var result=operation.apply(c);c.requireUnchanged();return result;}).get());
        }).get());
    }
    private Current current(FormalRunControlAccess.Reads reads,EnterpriseAccess.Admission a,
            AgentPublicationAuthority.PreparedPublication prepared,HttpServletRequest http,Reference reference)throws Exception{
        var p=policies(reads,a,prepared,reference);
        var proof=production.require(a,p.publication,p.policy,p.retention,reference.runId());content.require(proof).requireUnchanged();
        var receipt=accepted.require(proof,http,reference);var graph=graphs.require(a,reference.runId());
        if(graph.nodes().size()>reference.maximumSteps())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        return new Current(a,proof,p.publication,p.policy,graph,receipt);
    }
    private TaskRunBootstrapAuthority.Verified queued(FormalRunControlAccess.Reads reads,EnterpriseAccess.Admission a,
            AgentPublicationAuthority.PreparedPublication prepared,Reference reference)throws Exception{
        var p=policies(reads,a,prepared,reference);var proof=bootstrap.require(a,p.publication,p.policy,p.retention,reference.runId());
        content.require(proof).requireUnchanged();return proof;
    }
    private Policies policies(FormalRunControlAccess.Reads reads,EnterpriseAccess.Admission a,
            AgentPublicationAuthority.PreparedPublication prepared,Reference reference)throws Exception{
        var replay=reads.replay();same(reference.tenantId(),a.context().tenant_id());same(reference.projectId(),replay.required("project_id").asText());same(reference.runId(),replay.required("run_id").asText());
        var publication=publications.require(a,replay.required("agent_release_id").asText(),"RUN_CREATION",prepared);
        var policy=policies.requireByVersion(a,reads.policyVersion(),reads.policySha256(),reference.projectId(),identity.classification(),"RUN_CREATION");
        same(reference.policyRevision(),policy.versionId());var fixed=replay.required("retention");
        var retained=retention.requireFixedForRun(a,fixed.required("policy_revision").asText(),fixed.required("content_sha256").asText(),reference.projectId(),identity.classification(),"RUN_CREATION");
        return new Policies(publication,policy,retained);
    }
    private record Policies(AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,RuntimeRetentionAuthority.Verified retention){}
    public final class Current {
        private final EnterpriseAccess.Admission admission;private final TaskNodeProductionAuthority.Verified production;
        private final AgentPublicationAuthority.VerifiedPublication publication;private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;
        private final TaskRunGraphService.Verified graph;private final JsonNode start;
        private Current(EnterpriseAccess.Admission a,TaskNodeProductionAuthority.Verified production,AgentPublicationAuthority.VerifiedPublication publication,
                AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskRunGraphService.Verified graph,JsonNode start){this.admission=a;this.production=production;this.publication=publication;this.policy=policy;this.graph=graph;this.start=start.deepCopy();}
        EnterpriseAccess.Admission admission(){return admission;}TaskNodeProductionAuthority.Verified production(){return production;}
        AgentPublicationAuthority.VerifiedPublication publication(){return publication;}AgentExecutionPolicyAuthority.VerifiedPolicy policy(){return policy;}
        TaskRunGraphService.Verified graph(){return graph;}JsonNode acceptedStart(){return start.deepCopy();}
        void requireUnchanged()throws Exception{production.requireUnchanged();publication.requireUnchanged();policy.requireUnchanged();graph.requireUnchanged();}
    }
    private TaskNodeInputAuthority.Prepared input(Current c,TaskRunReplayRegistration.Registered replay,String question)throws Exception{
        var node=c.graph.question(question);var selected=producer(c,node);
        var proof=producers.require(c.production,replay,c.graph,c.publication,c.policy,selected.id,selected.hash,question);
        if(selected.decision!=null){same(selected.decision,proof.decisionId());same(selected.decisionHash,proof.decisionSha256());}
        return inputs.prepare(c.production,replay,c.graph,proof,question);
    }
    private record Producer(String id,String hash,String decision,String decisionHash){}
    private Producer producer(Current c,TaskRunGraphService.Node node)throws Exception{
        var b=c.production;var original=db.queryForList("SELECT producer_rule_id,producer_sha256,producer_decision_id,producer_decision_sha256 FROM task_node_input_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND created_by=? LIMIT 2 FOR UPDATE",b.tenantId(),b.projectId(),b.runId(),node.stepId(),b.actorId());
        if(!original.isEmpty()){var row=one(original);return new Producer(text(row,"producer_rule_id"),text(row,"producer_sha256"),text(row,"producer_decision_id"),text(row,"producer_decision_sha256"));}
        var binding=one(c.policy.definition().bindings().stream().filter(n->"N02".equals(n.nodeKind())).toList());
        // Exact-one discovery is not authority. The separate 098 verifier checks the
        // complete immutable rule and current author/independent approver evidence.
        var candidates=db.queryForList("""
            SELECT r.rule_id,r.content_sha256 FROM task_node_producer_rule r
            JOIN task_node_producer_state s ON s.tenant_id=r.tenant_id AND s.project_id=r.project_id AND s.task_id=r.task_id AND s.rule_id=r.rule_id
            WHERE r.tenant_id=? AND r.project_id=? AND r.task_id=? AND r.plan_revision=? AND r.plan_sha256=?
              AND r.role_version=? AND r.role_sha256=? AND r.policy_version=? AND r.policy_sha256=?
              AND r.scope_snapshot=? AND r.source_manifest_sha256=? AND r.release_id=?
              AND r.publication_approval_sha256=? AND r.budget_revision=?
              AND JSON_CONTAINS(r.question_ids,JSON_QUOTE(?)) AND s.status='APPROVED'
            ORDER BY r.rule_id LIMIT 2 FOR UPDATE
            """,b.tenantId(),b.projectId(),b.taskId(),c.graph.planRevision(),c.graph.planSha256(),binding.roleVersion().version_id(),binding.roleVersion().content_sha256(),
            c.policy.versionId(),c.policy.contentSha256(),b.replay().snapshotId(),b.replay().content().required("scope_snapshot_sha256").asText(),
            b.replay().content().required("agent_release_id").asText(),b.replay().content().required("publication_approval_sha256").asText(),b.replay().content().required("budget_revision").asText(),node.questionId());
        var selected=one(candidates);return new Producer(text(selected,"rule_id"),text(selected,"content_sha256"),null,null);
    }
    private List<String> readyQuestions(Current c)throws Exception{
        var ready=new ArrayList<String>();var b=c.production;
        for(var node:c.graph.nodes()){
            var existing=db.queryForList("SELECT step_id FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",b.tenantId(),b.projectId(),b.runId(),node.stepId());
            if(existing.size()>1)deny();if(!existing.isEmpty())continue;
            boolean complete=true;
            for(String predecessor:node.dependsOnStepIds()){
                var rows=db.query("SELECT status FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",(r,n)->r.getString(1),b.tenantId(),b.projectId(),b.runId(),predecessor);
                if(rows.size()>1)deny();if(rows.isEmpty()||!"SUCCEEDED".equals(rows.get(0))){complete=false;break;}
            }
            if(complete){c.graph.requirePredecessors(node.questionId());ready.add(node.questionId());}
        }
        c.requireUnchanged();return List.copyOf(ready);
    }
    private void requireAcceptanceLocator(EnterpriseAccess.Admission a,Reference reference){
        // A cheap missing-receipt guard only. TaskRunAcceptedStart subsequently verifies all
        // exact immutable ledger fields; presence here never authorizes the transition.
        one(db.query("SELECT launch_id FROM task_run_workflow_acceptance WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? LIMIT 2 FOR UPDATE",(r,n)->r.getString(1),a.context().tenant_id(),reference.projectId(),reference.runId(),a.actorId()));
    }
    private String command(Reference reference){Objects.requireNonNull(reference);if(reference.executionKind()!=com.ruoyi.medical.workflow.WorkflowProtocol.ExecutionKind.TASK)deny();return one(db.query("SELECT command_id FROM task_run_workflow_launch WHERE tenant_id=? AND project_id=? AND run_id=? LIMIT 2",(r,n)->r.getString(1),reference.tenantId(),reference.projectId(),reference.runId()));}
    private String status(EnterpriseAccess.Admission a,Reference reference){return one(db.query("SELECT status FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' LIMIT 2 FOR UPDATE",(r,n)->r.getString(1),a.context().tenant_id(),reference.projectId(),reference.runId(),a.actorId()));}
    static String inputKey(String run,String question){return "task-n02-input:"+run+":"+question;}
    private static String text(Map<String,Object> row,String key){var value=row.get(key);if(!(value instanceof String))deny();return (String)value;}
    private static <T>T one(List<T> rows){if(rows.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return rows.get(0);}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
