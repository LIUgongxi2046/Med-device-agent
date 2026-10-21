package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.workflow.RunActivities;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

/** Request-scoped adapter over real accepted-start, dispatch, commit and lifecycle
 * authorities. Missing stage producers and unsupported original command kinds fail
 * closed; N03–N06 obligations cannot become synthetic successes. */
public final class TaskRunActivitiesAdapter implements RunActivities {
    private final HttpServletRequest http;
    private final JdbcTemplate db;
    private final TaskRunActivityPreparation preparation;
    private final TaskNodeInputAuthority inputs;
    private final FormalTaskNodeAuthority nodes;
    private final FormalRunRecoveryAccess recovery;
    private final TaskRunLaunchRecoveryAuthority launches;
    private final TaskRunWorkflowLedger workflow;
    private final TaskRuntimeJournal journal;
    private final TaskResearchCommitService commits;
    private final TaskRunDispatchReservation reservations;
    private final RunTokenAuthority tokens;
    private final TaskHostControlBootstrap host;
    private final TaskProfessionalHostOrchestrator orchestrator;
    private final PermissionSchema json;
    private final Duration leaseLifetime;private final TaskRunLifecycleService lifecycle;

    public TaskRunActivitiesAdapter(HttpServletRequest http,DataSource data,TaskRunActivityPreparation preparation,
            TaskNodeInputAuthority inputs,FormalTaskNodeAuthority nodes,FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority launches,
            TaskRunWorkflowLedger workflow,TaskRuntimeJournal journal,TaskResearchCommitService commits,TaskRunDispatchReservation reservations,
            RunTokenAuthority tokens,TaskHostControlBootstrap host,TaskProfessionalHostOrchestrator orchestrator,PermissionSchema json,Duration leaseLifetime,TaskRunLifecycleService lifecycle){
        this.lifecycle=Objects.requireNonNull(lifecycle);this.http=Objects.requireNonNull(http);db=new JdbcTemplate(data);db.setQueryTimeout(5);this.preparation=Objects.requireNonNull(preparation);this.inputs=Objects.requireNonNull(inputs);
        this.nodes=Objects.requireNonNull(nodes);this.recovery=Objects.requireNonNull(recovery);this.launches=Objects.requireNonNull(launches);this.workflow=Objects.requireNonNull(workflow);this.journal=Objects.requireNonNull(journal);
        this.commits=Objects.requireNonNull(commits);this.reservations=Objects.requireNonNull(reservations);this.tokens=Objects.requireNonNull(tokens);this.host=Objects.requireNonNull(host);this.orchestrator=Objects.requireNonNull(orchestrator);this.json=Objects.requireNonNull(json);
        this.leaseLifetime=Objects.requireNonNull(leaseLifetime);if(leaseLifetime.isZero()||leaseLifetime.isNegative()||leaseLifetime.getNano()!=0)throw new IllegalArgumentException("leaseLifetime");
    }
    @Override public Plan load(Reference reference){return ThrowingSupplier.of(()->{
        task(reference);var snapshot=preparation.prepare(http,reference,c->snapshot(c,reference));var receipts=new HashMap<String,Receipt>();
        if(snapshot.steps.stream().anyMatch(s->!"READY".equals(s.status))){
            recovery.execute(http,reference.tenantId(),reference.runId(),a->ThrowingSupplier.of(()->{
                requireReference(a,reference);
                for(var s:snapshot.steps){
                    if("READY".equals(s.status))continue;
                    // Workflow v1 cannot load an unresolved RUNNING Step without
                    // treating its receipt as terminal. Never relabel it QUEUED.
                    if(!Set.of("SUCCEEDED","WAITING_INPUT","FAILED").contains(s.status))unavailable();
                    var receipt=("FAILED".equals(s.status)?lifecycle.loadFailure(a,reference,s.step):commits.loadCommitted(a,reference.runId(),s.step)).orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
                    same(s.status,receipt.status().name());same(s.input,receipt.inputSha256());same(s.generation,receipt.leaseGeneration());receipts.put(s.step,receipt);
                }return null;
            }).get());
        }
        // A concurrent claim/completion cannot make a stale READY snapshot become a
        // new scheduler authorization. Re-read under actual current run authority.
        preparation.withCurrent(http,reference,c->{same(snapshot,snapshot(c,reference));return null;});
        var steps=new ArrayList<Step>();for(var s:snapshot.steps){var receipt=receipts.get(s.step);
            if(receipt==null)steps.add(new Step(s.step,s.input,s.dependencies,true,State.QUEUED,0,null,null,List.of()));
            else steps.add(new Step(s.step,s.input,s.dependencies,true,receipt.status(),receipt.leaseGeneration(),receipt.receiptId(),receipt.waitingItemId(),receipt.outputs()));
        }
        return new Plan(reference.inputSha256(),snapshot.plan,State.RUNNING,null,null,steps,snapshot.planned,snapshot.graph,snapshot.graphHash,snapshot.required);
    }).get();}
    @Override public Dispatch dispatch(Reference reference,String stepId,String commandId){return ThrowingSupplier.of(()->{
        task(reference);WireValues.uuid(stepId);WireValues.uuid(commandId);
        var original=recovery.execute(http,reference.tenantId(),reference.runId(),a->ThrowingSupplier.of(()->{
            requireReference(a,reference);var reserved=reservations.original(a,reference,stepId,commandId);
            if(reserved.isEmpty()||!reserved.get().claimed())return Optional.<Dispatch>empty();
            // Only the exact original journal can recover a dispatch. Missing partial
            // envelope/session journals do not permit token reissue or another claim.
            var value=journal.readHistoricalStep(a,reference.runId(),stepId,reserved.get().generation());reserved.get().requireDispatch(value);return Optional.of(value);
        }).get());
        if(original.isPresent())return original.get();
        var pending=preparation.withCurrent(http,reference,c->reservations.reserve(c,http,reference,stepId,commandId));
        var lease=tokens.claimReserved(http,new RunTokenAuthority.ClaimRequest(reference.tenantId(),pending.actor(),reference.runId(),stepId,pending.revision(),leaseLifetime),pending);
        // This path is entered exactly once after the new lease/command claim commits.
        // Retry after an uncertain open/prepare is original-read-only above.
        var handle=host.open(http,reference.tenantId(),pending.actor(),reference.runId(),stepId,lease.generation());
        var value=orchestrator.prepare(handle,commandId).dispatch();
        return recovery.execute(http,reference.tenantId(),reference.runId(),a->ThrowingSupplier.of(()->{
            requireReference(a,reference);var r=reservations.original(a,reference,stepId,commandId).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
            r.requireDispatch(value);same(value,journal.requireHistoricalStep(a,value));return value;
        }).get());
    }).get();}
    @Override public Receipt commit(Reference reference,Dispatch dispatch,Completion completion){return ThrowingSupplier.of(()->{
        task(reference);Objects.requireNonNull(dispatch);Objects.requireNonNull(completion);same(reference.runId(),dispatch.runId());
        var original=recovery.execute(http,reference.tenantId(),reference.runId(),a->ThrowingSupplier.of(()->{
            requireReference(a,reference);var reserved=reservations.original(a,reference,dispatch.stepId(),dispatch.commandId()).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));reserved.requireDispatch(dispatch);
            same(dispatch,journal.requireHistoricalStep(a,dispatch));var prior=commits.loadCommitted(a,dispatch.runId(),dispatch.stepId());
            return prior.isEmpty()?Optional.<Receipt>empty():Optional.of(commits.recover(a,dispatch,completion));
        }).get());
        if(original.isPresent())return original.get();
        String actor=preparation.withCurrent(http,reference,c->c.admission().actorId());
        return nodes.execute(http,reference.tenantId(),actor,reference.runId(),dispatch.stepId(),null,false,(watermarks,node)->commits.commit(node,dispatch,completion));
    }).get();}
    private Snapshot snapshot(TaskRunActivityPreparation.Current current,Reference reference)throws Exception{
        current.requireUnchanged();var graph=current.graph();var steps=new ArrayList<Materialized>();var planned=new ArrayList<PlannedStep>();
        for(var node:graph.nodes()){
            planned.add(new PlannedStep(node.stepId(),node.dependsOnStepIds(),true));
            var rows=db.queryForList("SELECT status,input_sha256,lease_generation FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",reference.tenantId(),reference.projectId(),reference.runId(),node.stepId());
            if(rows.isEmpty())continue;var row=one(rows);var input=inputs.require(current.admission(),reference.runId(),node.stepId(),current.publication(),current.policy());input.requireUnchanged();
            same(graph.graphId(),input.graphId());same(graph.graphSha256(),input.graphSha256());same(input.inputSha256(),row.get("input_sha256"));
            String status=text(row,"status");long generation=new BigInteger(String.valueOf(row.get("lease_generation"))).longValueExact();
            if("READY".equals(status)&&generation!=0)unavailable();
            steps.add(new Materialized(node.stepId(),input.inputSha256(),node.dependsOnStepIds(),status,generation));
        }
        var obligations=new ArrayList<RequiredStage>();for(String stage:graph.requiredStages()){
            var binding=json.object().put("schema_version","1.0.0").put("kind","TASK_REQUIRED_STAGE").put("tenant_id",graph.tenantId()).put("project_id",graph.projectId())
                .put("run_id",graph.runId()).put("graph_id",graph.graphId()).put("graph_sha256",graph.graphSha256()).put("stage_kind",stage);
            obligations.add(new RequiredStage(stage,org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(binding)),null,null));
        }
        // No later-stage receipts are supported in this first adapter slice. If they
        // exist, a future loader must verify them, never silently mask them as pending.
        current.requireUnchanged();return new Snapshot(graph.planRevision(),graph.graphId(),graph.graphSha256(),List.copyOf(planned),List.copyOf(steps),List.copyOf(obligations));
    }
    private record Materialized(String step,String input,List<String> dependencies,String status,long generation){private Materialized{dependencies=List.copyOf(dependencies);}}
    private record Snapshot(String plan,String graph,String graphHash,List<PlannedStep> planned,List<Materialized> steps,List<RequiredStage> required){}
    private void requireReference(EnterpriseAccess.Admission a,Reference reference)throws Exception{
        var identity=a.recoveryProof().identity();var authority=launches.require(a,http,reference.runId(),identity.commandId());
        var launch=workflow.recover(authority);same(reference,launch.reference());if(workflow.read(authority).isEmpty())unavailable();authority.requireUnchanged();
    }
    @Override public Receipt failure(Reference r,Dispatch d,String reason){task(r);return ThrowingSupplier.of(()->lifecycle.failure(http,r,d,reason)).get();}
    @Override public Receipt dependencyFailed(Reference r,String step){task(r);return ThrowingSupplier.of(()->lifecycle.dependencyFailed(http,r,step)).get();}
    @Override public CommandReceipt consume(Reference r,Command c){task(r);return ThrowingSupplier.of(()->lifecycle.consume(http,r,c)).get();}
    @Override public Termination cancellation(Reference r,String command,String reason){task(r);return ThrowingSupplier.of(()->lifecycle.cancellation(http,r,command,reason)).get();}
    @Override public FinalReceipt finish(Reference r,State requested,String reason,List<Receipt> receipts,List<String> commands){task(r);return ThrowingSupplier.of(()->lifecycle.finish(http,r,requested,reason,receipts,commands)).get();}
    private static void task(Reference r){if(Objects.requireNonNull(r).executionKind()!=ExecutionKind.TASK)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    private static <T>T one(List<T> rows){if(rows.size()!=1)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);return rows.get(0);}
    private static String text(Map<String,Object> row,String f){Object v=row.get(f);if(!(v instanceof String))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);return (String)v;}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void unavailable(){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
}
