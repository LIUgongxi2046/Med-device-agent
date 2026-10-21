package com.ruoyi.medical.workflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import com.ruoyi.medical.contracts.WireValues;
import java.util.Map;
import java.util.Objects;
/** Actual Temporal start plus independent original started-event reread. Unknown starts
 * recover by query only, including NOT_FOUND; no caller can construct Accepted. */
public final class DocumentCheckDelivery {
    private final WorkflowClient client;
    private static final String COMMAND="medical.check_document.command_id";
    public DocumentCheckDelivery(WorkflowClient client){this.client=Objects.requireNonNull(client);}
    public Accepted start(DocumentCheckProtocol.Reference reference,String command){
        WireValues.uuid(command);
        var options=WorkflowOptions.newBuilder().setWorkflowId(reference.workflowId()).setTaskQueue(reference.controlTaskQueue())
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                .setRequestId(command).setMemo(Map.of(COMMAND,command)).build();
        var stub=client.newWorkflowStub(DocumentCheckWorkflow.class,options);var execution=WorkflowClient.start(stub::run,reference);
        require(reference,execution,command);return new Accepted(reference,command,execution.getRunId());
    }
    public Accepted recover(DocumentCheckProtocol.Reference reference,String command){
        WireValues.uuid(command);
        var response=client.getWorkflowServiceStubs().blockingStub().describeWorkflowExecution(DescribeWorkflowExecutionRequest.newBuilder()
                .setNamespace(client.getOptions().getNamespace()).setExecution(WorkflowExecution.newBuilder().setWorkflowId(reference.workflowId())).build());
        var execution=response.getWorkflowExecutionInfo().getExecution();require(reference,execution,command);return new Accepted(reference,command,execution.getRunId());
    }
    private void require(DocumentCheckProtocol.Reference reference,WorkflowExecution execution,String command){
        if(!reference.workflowId().equals(execution.getWorkflowId()))bad();WireValues.uuid(execution.getRunId());
        var history=client.getWorkflowServiceStubs().blockingStub().getWorkflowExecutionHistory(GetWorkflowExecutionHistoryRequest.newBuilder()
                .setNamespace(client.getOptions().getNamespace()).setExecution(execution).setMaximumPageSize(1).build()).getHistory();
        if(history.getEventsCount()<1||!history.getEvents(0).hasWorkflowExecutionStartedEventAttributes())bad();var started=history.getEvents(0).getWorkflowExecutionStartedEventAttributes();
        if(!"medical.system.check-document.v1".equals(started.getWorkflowType().getName())||!started.hasInput()||started.getInput().getPayloadsCount()!=1
                ||!started.hasMemo()||!started.getMemo().containsFields(COMMAND)||!execution.getRunId().equals(started.getOriginalExecutionRunId())
                ||!started.getContinuedExecutionRunId().isEmpty()||!reference.controlTaskQueue().equals(started.getTaskQueue().getName()))bad();
        var converter=client.getOptions().getDataConverter();if(!command.equals(converter.fromPayload(started.getMemo().getFieldsOrThrow(COMMAND),String.class,String.class))
                ||!reference.equals(converter.fromPayload(started.getInput().getPayloads(0),DocumentCheckProtocol.Reference.class,DocumentCheckProtocol.Reference.class)))bad();
    }
    public static final class Accepted {
        private final DocumentCheckProtocol.Reference reference;private final String command,temporalRun;
        private Accepted(DocumentCheckProtocol.Reference reference,String command,String temporalRun){this.reference=reference;this.command=command;this.temporalRun=temporalRun;}
        public DocumentCheckProtocol.Reference reference(){return reference;}public String commandId(){return command;}public String temporalRunId(){return temporalRun;}
    }
    private static void bad(){throw new IllegalArgumentException("CHECK_DOCUMENT_LAUNCH_EVIDENCE_MISMATCH");}
}
