package com.ruoyi.medical.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.worker.WorkerOptions;
import io.temporal.worker.WorkflowImplementationOptions;
import java.util.Objects;

public final class WorkflowWorker {
    private WorkflowWorker() { }
    public static WorkerFactory create(WorkflowClient client, String queue, RunActivities activities, int workflowThreads, int activityThreads) {
        Objects.requireNonNull(client); Objects.requireNonNull(activities);
        if (queue == null || queue.isBlank() || workflowThreads < 1 || activityThreads < 1) throw new IllegalArgumentException("WORKER_CAPACITY_REQUIRED");
        var factory = WorkerFactory.newInstance(client, WorkerFactoryOptions.newBuilder().setMaxWorkflowThreadCount(workflowThreads).build());
        var worker = factory.newWorker(queue, WorkerOptions.newBuilder().setMaxConcurrentActivityExecutionSize(activityThreads)
            .setMaxConcurrentWorkflowTaskExecutionSize(workflowThreads).build());
        worker.registerWorkflowImplementationTypes(WorkflowImplementationOptions.newBuilder().setFailWorkflowExceptionTypes(RuntimeException.class).build(), TaskWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities); return factory;
    }
}
