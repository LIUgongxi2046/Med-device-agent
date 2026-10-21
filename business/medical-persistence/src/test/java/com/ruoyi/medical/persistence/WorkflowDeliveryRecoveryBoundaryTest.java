package com.ruoyi.medical.persistence;

import com.ruoyi.medical.workflow.WorkflowDelivery;
import com.ruoyi.medical.workflow.WorkflowProtocol.*;
import io.temporal.api.common.v1.*;
import io.temporal.api.history.v1.*;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.*;
import io.temporal.client.WorkflowClient;
import io.temporal.common.converter.DefaultDataConverter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.TaskRunBootstrapSchemaTest.id;

/** Mocked SDK boundary only: no Temporal server or paid provider is contacted. */
class WorkflowDeliveryRecoveryBoundaryTest {
    final WorkflowClient client=mock(WorkflowClient.class,RETURNS_DEEP_STUBS);
    final Reference reference=new Reference("1.0.0",id(1),id(2),id(3),ExecutionKind.TASK,id(4),"a".repeat(64),id(5),1800000000000L,10,3,2,1000,2000,3000,"fixture-control","fixture-professional");
    final WorkflowExecution execution=WorkflowExecution.newBuilder().setWorkflowId(reference.workflowId()).setRunId(id(6)).build();
    final io.temporal.common.converter.DataConverter converter=DefaultDataConverter.STANDARD_INSTANCE;
    WorkflowDeliveryRecoveryBoundaryTest(){
        when(client.getOptions().getNamespace()).thenReturn("fixture-namespace");when(client.getOptions().getDataConverter()).thenReturn(converter);
        when(client.getWorkflowServiceStubs().blockingStub().describeWorkflowExecution(any())).thenReturn(DescribeWorkflowExecutionResponse.newBuilder().setWorkflowExecutionInfo(WorkflowExecutionInfo.newBuilder().setExecution(execution)).build());
        history(reference,id(7),true);
    }
    void history(Reference input,String command,boolean memo){
        var attributes=WorkflowExecutionStartedEventAttributes.newBuilder().setWorkflowType(WorkflowType.newBuilder().setName("medical.task.run"))
            .setOriginalExecutionRunId(id(6)).setInput(converter.toPayloads(input).orElseThrow());
        if(memo)attributes.setMemo(Memo.newBuilder().putFields("medical.start_command_id",converter.toPayload(command).orElseThrow()));
        when(client.getWorkflowServiceStubs().blockingStub().getWorkflowExecutionHistory(any())).thenReturn(GetWorkflowExecutionHistoryResponse.newBuilder().setHistory(
            History.newBuilder().addEvents(HistoryEvent.newBuilder().setWorkflowExecutionStartedEventAttributes(attributes))).build());
    }
    @Test void closedOrUnknownResultQueriesOriginalHistoryAndActualRunOnly(){
        var acceptance=new WorkflowDelivery(client).recover(reference,id(7));assertEquals(id(6),acceptance.temporalRunId());assertEquals(reference.workflowId(),acceptance.workflowId());
        verify(client.getWorkflowServiceStubs().blockingStub(),never()).startWorkflowExecution(any());
        verify(client.getWorkflowServiceStubs().blockingStub()).getWorkflowExecutionHistory(argThat(r->r.getNamespace().equals("fixture-namespace")&&r.getExecution().equals(execution)));
    }
    @Test void missingOrDifferentCommandEvidenceIsRejected(){
        history(reference,id(7),false);assertThrows(IllegalArgumentException.class,()->new WorkflowDelivery(client).recover(reference,id(7)));
        history(reference,id(8),true);assertThrows(IllegalArgumentException.class,()->new WorkflowDelivery(client).recover(reference,id(7)));
        verify(client.getWorkflowServiceStubs().blockingStub(),never()).startWorkflowExecution(any());
    }
    @Test void changedInputAndUnknownExecutionNeverCauseReplacementStart(){
        var changed=new Reference(reference.schemaVersion(),reference.tenantId(),reference.projectId(),reference.runId(),reference.executionKind(),reference.inputManifestId(),"b".repeat(64),reference.policyRevision(),reference.deadlineEpochMillis(),10,3,2,1000,2000,3000,reference.controlTaskQueue(),reference.professionalTaskQueue());
        history(changed,id(7),true);assertThrows(IllegalArgumentException.class,()->new WorkflowDelivery(client).recover(reference,id(7)));
        when(client.getWorkflowServiceStubs().blockingStub().describeWorkflowExecution(any())).thenThrow(io.grpc.Status.NOT_FOUND.asRuntimeException());
        assertThrows(io.grpc.StatusRuntimeException.class,()->new WorkflowDelivery(client).recover(reference,id(7)));
        verify(client.getWorkflowServiceStubs().blockingStub(),never()).startWorkflowExecution(any());
    }
}
