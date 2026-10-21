package com.ruoyi.medical.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.*;
import java.util.Objects;

/** Opt-in factory only: no component/default switch and no silent V1 worker change. */
public final class WorkflowWorkerV2 {
    private WorkflowWorkerV2(){}
    public static WorkerFactory create(WorkflowClient client,WaitingWorkflowDeploymentV2 deployment,RunActivities activities,TaskRunWaitingActivities waiting,TaskRunRecoveryActivitiesV2 recovery,TaskRunStageActivitiesV2 stages,
            int workflowThreads,int activityThreads){
        Objects.requireNonNull(client);Objects.requireNonNull(deployment);Objects.requireNonNull(activities);Objects.requireNonNull(waiting);Objects.requireNonNull(recovery);Objects.requireNonNull(stages);
        if(!deployment.namespace().equals(client.getOptions().getNamespace())||workflowThreads<1||activityThreads<1)throw new IllegalArgumentException("WAITING_V2_WORKER_CONFIGURATION_REQUIRED");
        var factory=WorkerFactory.newInstance(client,WorkerFactoryOptions.newBuilder().setMaxWorkflowThreadCount(workflowThreads).build());
        var worker=factory.newWorker(deployment.controlTaskQueue(),WorkerOptions.newBuilder().setMaxConcurrentActivityExecutionSize(activityThreads).setMaxConcurrentWorkflowTaskExecutionSize(workflowThreads).build());
        worker.registerWorkflowImplementationFactory(TaskWorkflow.class,()->new TaskWorkflowWaitingV2Impl(deployment),WorkflowImplementationOptions.newBuilder().setFailWorkflowExceptionTypes(RuntimeException.class).build());
        worker.registerActivitiesImplementations(activities,waiting,recovery,stages);return factory;
    }
    public static WorkerFactory create(WorkflowClient client,WaitingWorkflowDeploymentV2 deployment,SectionWriterWorkflowExtensionV1 extension,RunActivities activities,TaskRunWaitingActivities waiting,TaskRunRecoveryActivitiesV2 recovery,TaskRunStageActivitiesV2 stages,TaskSectionWriterStageActivitiesV1 writer,int workflowThreads,int activityThreads){
        Objects.requireNonNull(extension).require(deployment);Objects.requireNonNull(writer);Objects.requireNonNull(activities);Objects.requireNonNull(waiting);Objects.requireNonNull(recovery);Objects.requireNonNull(stages);
        if(!deployment.namespace().equals(client.getOptions().getNamespace())||workflowThreads<1||activityThreads<1)throw new IllegalArgumentException("WAITING_V2_WORKER_CONFIGURATION_REQUIRED");
        var factory=WorkerFactory.newInstance(client,WorkerFactoryOptions.newBuilder().setMaxWorkflowThreadCount(workflowThreads).build());
        var worker=factory.newWorker(deployment.controlTaskQueue(),WorkerOptions.newBuilder().setMaxConcurrentActivityExecutionSize(activityThreads).setMaxConcurrentWorkflowTaskExecutionSize(workflowThreads).build());
        worker.registerWorkflowImplementationFactory(TaskWorkflow.class,()->new TaskWorkflowWaitingV2Impl(deployment,extension),WorkflowImplementationOptions.newBuilder().setFailWorkflowExceptionTypes(RuntimeException.class).build());
        worker.registerActivitiesImplementations(activities,waiting,recovery,stages,writer);return factory;
    }

    public static WorkerFactory create(WorkflowClient client,WaitingWorkflowDeploymentV2 deployment,SectionWriterWorkflowExtensionV1 extension,SourceVerifierWorkflowExtensionV1 verifier,RunActivities activities,TaskRunWaitingActivities waiting,TaskRunRecoveryActivitiesV2 recovery,TaskRunStageActivitiesV2 stages,TaskSectionWriterStageActivitiesV1 writer,TaskSourceVerifierStageActivitiesV1 verifierStages,int workflowThreads,int activityThreads){
        Objects.requireNonNull(extension).require(deployment);Objects.requireNonNull(verifier).require(deployment,extension);Objects.requireNonNull(verifierStages);Objects.requireNonNull(writer);Objects.requireNonNull(activities);Objects.requireNonNull(waiting);Objects.requireNonNull(recovery);Objects.requireNonNull(stages);
        if(!deployment.namespace().equals(client.getOptions().getNamespace())||workflowThreads<1||activityThreads<1)throw new IllegalArgumentException("WAITING_V2_WORKER_CONFIGURATION_REQUIRED");
        var factory=WorkerFactory.newInstance(client,WorkerFactoryOptions.newBuilder().setMaxWorkflowThreadCount(workflowThreads).build());
        var worker=factory.newWorker(deployment.controlTaskQueue(),WorkerOptions.newBuilder().setMaxConcurrentActivityExecutionSize(activityThreads).setMaxConcurrentWorkflowTaskExecutionSize(workflowThreads).build());
        worker.registerWorkflowImplementationFactory(TaskWorkflow.class,()->new TaskWorkflowWaitingV2Impl(deployment,extension,verifier),WorkflowImplementationOptions.newBuilder().setFailWorkflowExceptionTypes(RuntimeException.class).build());
        worker.registerActivitiesImplementations(activities,waiting,recovery,stages,writer,verifierStages);return factory;
    }
    public static WorkerFactory create(WorkflowClient client,WaitingWorkflowDeploymentV2 deployment,SectionWriterWorkflowExtensionV1 extension,SourceVerifierWorkflowExtensionV1 verifier,CandidateAssemblyWorkflowExtensionV1 assembly,RunActivities activities,TaskRunWaitingActivities waiting,TaskRunRecoveryActivitiesV2 recovery,TaskRunStageActivitiesV2 stages,TaskSectionWriterStageActivitiesV1 writer,TaskSourceVerifierStageActivitiesV1 verifierStages,TaskCandidateAssemblyStageActivitiesV1 assemblyStages,int workflowThreads,int activityThreads){
        Objects.requireNonNull(assembly).require(deployment,verifier);Objects.requireNonNull(assemblyStages);Objects.requireNonNull(extension).require(deployment);Objects.requireNonNull(verifier).require(deployment,extension);Objects.requireNonNull(verifierStages);Objects.requireNonNull(writer);Objects.requireNonNull(activities);Objects.requireNonNull(waiting);Objects.requireNonNull(recovery);Objects.requireNonNull(stages);
        if(!deployment.namespace().equals(client.getOptions().getNamespace())||workflowThreads<1||activityThreads<1)throw new IllegalArgumentException("WAITING_V2_WORKER_CONFIGURATION_REQUIRED");
        var factory=WorkerFactory.newInstance(client,WorkerFactoryOptions.newBuilder().setMaxWorkflowThreadCount(workflowThreads).build());
        var worker=factory.newWorker(deployment.controlTaskQueue(),WorkerOptions.newBuilder().setMaxConcurrentActivityExecutionSize(activityThreads).setMaxConcurrentWorkflowTaskExecutionSize(workflowThreads).build());
        worker.registerWorkflowImplementationFactory(TaskWorkflow.class,()->new TaskWorkflowWaitingV2Impl(deployment,extension,verifier,assembly),WorkflowImplementationOptions.newBuilder().setFailWorkflowExceptionTypes(RuntimeException.class).build());
        worker.registerActivitiesImplementations(activities,waiting,recovery,stages,writer,verifierStages,assemblyStages);return factory;
    }
}
