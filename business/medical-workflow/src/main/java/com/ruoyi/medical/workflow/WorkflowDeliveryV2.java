package com.ruoyi.medical.workflow;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.*;
import io.temporal.api.workflowservice.v1.*;
import io.temporal.client.*;
import java.util.Objects;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

/** Real Temporal start/query boundary with immutable explicit V2 memo pins. Original
 * recovery is query-only including NOT_FOUND and never falls back to a V1 start. */
public final class WorkflowDeliveryV2 {
    private final CandidateAssemblyWorkflowExtensionV1 assembly;private final SourceVerifierWorkflowExtensionV1 verifier;private final WorkflowClient client;private final WaitingWorkflowDeploymentV2 deployment;private final SectionWriterWorkflowExtensionV1 extension;
    public WorkflowDeliveryV2(WorkflowClient client,WaitingWorkflowDeploymentV2 deployment){this(client,deployment,null);}
    public WorkflowDeliveryV2(WorkflowClient client,WaitingWorkflowDeploymentV2 deployment,SectionWriterWorkflowExtensionV1 extension){this(client,deployment,extension,null);}
    public WorkflowDeliveryV2(WorkflowClient client,WaitingWorkflowDeploymentV2 deployment,SectionWriterWorkflowExtensionV1 extension,SourceVerifierWorkflowExtensionV1 verifier){this(client,deployment,extension,verifier,null);}
    public WorkflowDeliveryV2(WorkflowClient client,WaitingWorkflowDeploymentV2 deployment,SectionWriterWorkflowExtensionV1 extension,SourceVerifierWorkflowExtensionV1 verifier,CandidateAssemblyWorkflowExtensionV1 assembly){this.assembly=assembly;if(assembly!=null)assembly.require(deployment,Objects.requireNonNull(verifier));this.verifier=verifier;if(verifier!=null)verifier.require(deployment,Objects.requireNonNull(extension));this.extension=extension;if(extension!=null)extension.require(deployment);this.client=Objects.requireNonNull(client);this.deployment=Objects.requireNonNull(deployment);if(!namespace().equals(deployment.namespace()))invalid();}
    public String namespace(){return client.getOptions().getNamespace();}
    public String protocolSha256(){return deployment.canonicalSha256();}
    public WorkflowDelivery.Acceptance start(Reference reference,String command){
        deployment.require(reference,namespace());var options=WorkflowOptions.newBuilder().setWorkflowId(reference.workflowId()).setTaskQueue(reference.controlTaskQueue())
            .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE).setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
            .setRequestId(command).setMemo(memo(command)).build();
        var workflow=client.newWorkflowStub(TaskWorkflow.class,options);var execution=WorkflowClient.start(workflow::run,reference);require(reference,execution,command);return acceptance(reference,execution);
    }
    public WorkflowDelivery.Acceptance recover(Reference reference,String command){
        deployment.require(reference,namespace());com.ruoyi.medical.contracts.WireValues.uuid(command);var response=client.getWorkflowServiceStubs().blockingStub().describeWorkflowExecution(DescribeWorkflowExecutionRequest.newBuilder().setNamespace(namespace()).setExecution(WorkflowExecution.newBuilder().setWorkflowId(reference.workflowId())).build());
        var execution=response.getWorkflowExecutionInfo().getExecution();require(reference,execution,command);return acceptance(reference,execution);
    }
    private void require(Reference reference,WorkflowExecution execution,String command){
        if(!reference.workflowId().equals(execution.getWorkflowId()))invalid();com.ruoyi.medical.contracts.WireValues.uuid(execution.getRunId());
        var history=client.getWorkflowServiceStubs().blockingStub().getWorkflowExecutionHistory(GetWorkflowExecutionHistoryRequest.newBuilder().setNamespace(namespace()).setExecution(execution).setMaximumPageSize(1).build()).getHistory();
        if(history.getEventsCount()<1||!history.getEvents(0).hasWorkflowExecutionStartedEventAttributes())invalid();var started=history.getEvents(0).getWorkflowExecutionStartedEventAttributes();
        if(!deployment.workflowType().equals(started.getWorkflowType().getName())||!started.hasInput()||started.getInput().getPayloadsCount()!=1||!started.hasMemo()||!execution.getRunId().equals(started.getOriginalExecutionRunId())||!started.getContinuedExecutionRunId().isEmpty()||!reference.controlTaskQueue().equals(started.getTaskQueue().getName()))invalid();
        var converter=client.getOptions().getDataConverter();for(var entry:memo(command).entrySet()){
            if(!started.getMemo().containsFields(entry.getKey())||!entry.getValue().equals(converter.fromPayload(started.getMemo().getFieldsOrThrow(entry.getKey()),String.class,String.class)))invalid();
        }
        if(extension==null&&(started.getMemo().containsFields(SectionWriterWorkflowExtensionV1.CONFIG_MEMO)||started.getMemo().containsFields(SectionWriterWorkflowExtensionV1.HASH_MEMO)))invalid();
        if(verifier==null&&(started.getMemo().containsFields(SourceVerifierWorkflowExtensionV1.CONFIG_MEMO)||started.getMemo().containsFields(SourceVerifierWorkflowExtensionV1.HASH_MEMO)))invalid();
        if(assembly==null&&(started.getMemo().containsFields(CandidateAssemblyWorkflowExtensionV1.CONFIG_MEMO)||started.getMemo().containsFields(CandidateAssemblyWorkflowExtensionV1.HASH_MEMO)))invalid();
        if(!reference.equals(converter.fromPayload(started.getInput().getPayloads(0),Reference.class,Reference.class)))invalid();
    }
    private java.util.Map<String,Object> memo(String command){var value=new java.util.LinkedHashMap<String,Object>(deployment.memo(command));if(extension!=null)value.putAll(extension.memo());if(verifier!=null)value.putAll(verifier.memo());if(assembly!=null)value.putAll(assembly.memo());return value;}
    private static WorkflowDelivery.Acceptance acceptance(Reference reference,WorkflowExecution execution){return new WorkflowDelivery.Acceptance(reference.workflowId(),execution.getRunId(),reference.inputSha256());}
    private static void invalid(){throw new IllegalArgumentException("WAITING_V2_ORIGINAL_START_MISMATCH");}
}
