package com.ruoyi.medical.workflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.*;
/** One immutable matrix cell, retaining every unexecuted node obligation. Never retries a paid execution. */
public final class FormalEvaluationWorkflowImpl implements FormalEvaluationWorkflow {
    public FormalEvaluationProtocol.CellReceipt run(FormalEvaluationProtocol.Reference r) {
        var control=Workflow.newActivityStub(FormalEvaluationActivities.class,options(r,r.controlTaskQueue()));
        var professional=Workflow.newActivityStub(FormalEvaluationActivities.class,options(r,r.professionalTaskQueue()));
        var receipts=new ArrayList<FormalEvaluationProtocol.NodeReceipt>();
        try {
            // The actual started event can precede persistence of its acceptance receipt.
            // Read-only polling closes that race without prematurely dispatching a node.
            for(;;){try{control.awaitLaunch(r);break;}catch(ActivityFailure pending){Workflow.sleep(Duration.ofMillis(r.recoveryPollMillis()));}}
            for(var step:r.steps()) {
                FormalEvaluationProtocol.NodeReceipt receipt;
                try {receipt=professional.execute(r,step);}catch(ActivityFailure unknown){receipt=recover(control,r,step);}
                receipt.require(r,step);
                while("UNKNOWN".equals(receipt.state())) {
                    Workflow.sleep(Duration.ofMillis(r.recoveryPollMillis()));
                    receipt=recover(control,r,step);receipt.require(r,step);
                }
                receipts.add(receipt);
                if(!"SUCCEEDED".equals(receipt.state()))break;
            }
            return require(r,control.complete(r,List.copyOf(receipts)));
        } catch(CanceledFailure cancelled) {
            final FormalEvaluationProtocol.CellReceipt[] result=new FormalEvaluationProtocol.CellReceipt[1];
            Workflow.newDetachedCancellationScope(()->{
                for(;;){
                    try {result[0]=require(r,control.cancel(r));if("CANCELLED".equals(result[0].state()))break;if(!"UNKNOWN".equals(result[0].state()))throw new IllegalArgumentException("EVALUATION_CANCEL_TERMINAL_MISMATCH");}
                    catch(ActivityFailure pending){/* Original process/settlement evidence may still be arriving. */}
                    Workflow.sleep(Duration.ofMillis(r.recoveryPollMillis()));
                }
            }).run();
            require(r,result[0]);throw cancelled;
        }
    }
    private static FormalEvaluationProtocol.NodeReceipt recover(FormalEvaluationActivities control,FormalEvaluationProtocol.Reference r,FormalEvaluationProtocol.Step step){for(;;){try{return control.recover(r,step);}catch(ActivityFailure pending){Workflow.sleep(Duration.ofMillis(r.recoveryPollMillis()));}}}
    private static ActivityOptions options(FormalEvaluationProtocol.Reference r,String queue){return ActivityOptions.newBuilder().setTaskQueue(queue).setStartToCloseTimeout(Duration.ofMillis(r.activityTimeoutMillis())).setHeartbeatTimeout(Duration.ofMillis(r.heartbeatTimeoutMillis())).setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build()).build();}
    private static FormalEvaluationProtocol.CellReceipt require(FormalEvaluationProtocol.Reference r,FormalEvaluationProtocol.CellReceipt c){if(c==null||!r.runId().equals(c.runId())||!r.admissionSha256().equals(c.admissionSha256()))throw new IllegalArgumentException("EVALUATION_ORIGINAL_CELL_RECEIPT_REQUIRED");return c;}
}
