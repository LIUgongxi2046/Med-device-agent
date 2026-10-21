package com.ruoyi.medical.workflow;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.List;
/** Implementations must verify original start/dispatch and register independent professional evidence. */
@ActivityInterface
public interface FormalEvaluationActivities {
    @ActivityMethod(name="medical.evaluation.await-launch.v1") void awaitLaunch(FormalEvaluationProtocol.Reference reference);
    @ActivityMethod(name="medical.evaluation.execute-node.v1") FormalEvaluationProtocol.NodeReceipt execute(FormalEvaluationProtocol.Reference reference,FormalEvaluationProtocol.Step step);
    @ActivityMethod(name="medical.evaluation.recover-node.v1") FormalEvaluationProtocol.NodeReceipt recover(FormalEvaluationProtocol.Reference reference,FormalEvaluationProtocol.Step step);
    @ActivityMethod(name="medical.evaluation.complete-cell.v1") FormalEvaluationProtocol.CellReceipt complete(FormalEvaluationProtocol.Reference reference,List<FormalEvaluationProtocol.NodeReceipt> receipts);
    @ActivityMethod(name="medical.evaluation.cancel-cell.v1") FormalEvaluationProtocol.CellReceipt cancel(FormalEvaluationProtocol.Reference reference);
}
