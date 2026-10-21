package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import com.ruoyi.medical.workflow.TaskRunStageProtocolV2.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** Actual deterministic AT05 dispatch. Retries are original-read-only, never another lease/token. */
public final class TaskStrictFieldExecutionBridge {
    private final TaskDeterministicExecution executions;private final TaskStrictFieldPreparation strictPreparation;private final JdbcTemplate db;private final TaskRunActivityPreparation preparation;private final FormalRunRecoveryAccess recovery;
    private final TaskStrictFieldDispatchReservation commands;private final RunTokenAuthority tokens;private final TaskStrictFieldNode node;
    private final TaskStrictFieldCompletionAuthority completions;private final TaskStrictFieldSchema schema;private final Duration leaseLifetime,tokenLifetime;
    public TaskStrictFieldExecutionBridge(DataSource data,TaskRunActivityPreparation preparation,FormalRunRecoveryAccess recovery,TaskStrictFieldDispatchReservation commands,RunTokenAuthority tokens,TaskStrictFieldNode node,TaskStrictFieldCompletionAuthority completions,TaskStrictFieldSchema schema,TaskStrictFieldPreparation strictPreparation,Duration leaseLifetime,Duration tokenLifetime,TaskDeterministicExecution executions){this.executions=executions;
        this.strictPreparation=strictPreparation;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.preparation=preparation;this.recovery=recovery;this.commands=commands;this.tokens=tokens;this.node=node;this.completions=completions;this.schema=schema;
        if(leaseLifetime==null||tokenLifetime==null||leaseLifetime.isNegative()||leaseLifetime.isZero()||tokenLifetime.isNegative()||tokenLifetime.isZero()||tokenLifetime.compareTo(leaseLifetime)>0)throw new IllegalArgumentException("N03_LIFETIMES_REQUIRED");this.leaseLifetime=leaseLifetime;this.tokenLifetime=tokenLifetime;
    }
    public FieldCompletionV2 execute(HttpServletRequest http,Reference reference,String stageKind,String commandId)throws Exception {
        require(reference,stageKind,commandId);var old=recover(http,reference,stageKind,commandId);if(old.isPresent())return old.get();
        boolean reserved=recovery.execute(http,reference.tenantId(),reference.runId(),a->ThrowingSupplier.of(()->!commands.commands(a,reference).isEmpty()).get());if(reserved)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        try(var owned=preparation.withCurrent(http,reference,c->executions.enter(c,"N03",commandId))){
        var pending=preparation.withCurrent(http,reference,c->{strictPreparation.materialize(c);var rows=db.queryForList("SELECT step_id FROM task_strict_field_input WHERE tenant_id=? AND project_id=? AND run_id=? LIMIT 2 FOR UPDATE",reference.tenantId(),reference.projectId(),reference.runId());if(rows.size()!=1)deny();return commands.reserve(c,http,reference,rows.get(0).get("step_id").toString(),commandId);});
        var input=pending.input();String actor=pending.actor();var lease=tokens.claimStrictFieldReserved(http,new RunTokenAuthority.ClaimRequest(reference.tenantId(),actor,reference.runId(),input.stepId(),pending.revision(),leaseLifetime),pending);
        var token=tokens.issue(http,new RunTokenAuthority.IssueRequest(reference.tenantId(),actor,reference.runId(),input.stepId(),lease.generation(),List.of(TaskStrictFieldNode.ACTION),tokenLifetime));
        var args=TaskStrictFieldDispatchReservation.arguments(input);String actionId=TaskStrictFieldDispatchReservation.actionId(input.tenantId(),input.runId(),input.stepId(),lease.generation());
        var action=new RunTokenAuthority.ActionRequest(input.runId(),input.stepId(),actionId,lease.generation(),schema.hash(input),schema.hash(args),"TOOL",input.toolVersion().version_id(),new PermissionProtocol.Scope(input.tenantId(),input.projectId(),"TASK",input.taskId()),TaskStrictFieldNode.ACTION,TaskStrictFieldNode.PURPOSE);
        node.execute(http,token.token(),action,args);
        return recover(http,reference,stageKind,commandId).orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
        }
    }
    public Optional<FieldCompletionV2> recover(HttpServletRequest http,Reference reference,String stageKind,String commandId)throws Exception {
        require(reference,stageKind,commandId);return recovery.execute(http,reference.tenantId(),reference.runId(),a->ThrowingSupplier.of(()->{
            var inventory=commands.commands(a,reference);if(inventory.isEmpty())return Optional.<FieldCompletionV2>empty();var command=inventory.get(0);if(!command.commandId().equals(commandId))deny();
            var original=commands.original(a,reference,command.stepId(),commandId).orElseThrow();if(!original.claimed())return Optional.<FieldCompletionV2>empty();
            var rows=db.queryForList("SELECT * FROM task_strict_field_result WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",reference.tenantId(),reference.projectId(),reference.runId(),command.stepId(),original.generation());if(rows.isEmpty())return Optional.<FieldCompletionV2>empty();if(rows.size()!=1)deny();var r=rows.get(0);var outputs=db.queryForList("SELECT receipt_sha256 FROM task_strict_field_output_registration WHERE tenant_id=? AND result_id=? LIMIT 2 FOR UPDATE",reference.tenantId(),r.get("result_id"));if(outputs.isEmpty())return Optional.<FieldCompletionV2>empty();if(outputs.size()!=1)deny();
            var value=new FieldCompletionV2("2.0.0","N03","FIELD_TARGET",reference.runId(),command.stepId(),commandId,command.inputId(),command.inputSha256(),r.get("action_id").toString(),original.generation(),r.get("result_id").toString(),r.get("result_sha256").toString(),outputs.get(0).get("receipt_sha256").toString());original.requireCompletion(value);completions.requireOriginal(a,value).requireUnchanged();return Optional.of(value);
        }).get());
    }
    public List<StageCommandV2> originalCommands(EnterpriseAccess.Admission recovery,Reference reference)throws Exception{return commands.commands(recovery,reference);}
    private static void require(Reference reference,String stage,String command){Objects.requireNonNull(reference);WireValues.uuid(command);if(reference.executionKind()!=com.ruoyi.medical.workflow.WorkflowProtocol.ExecutionKind.TASK||!"N03".equals(stage))deny();}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
