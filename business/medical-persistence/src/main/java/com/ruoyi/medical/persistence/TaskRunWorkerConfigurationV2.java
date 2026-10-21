package com.ruoyi.medical.persistence;

import com.ruoyi.medical.workflow.*;
import io.temporal.client.WorkflowClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Default-off isolated V2 worker. All actual HTTP handlers and protected client
 * material must exist. No RN01 admission, V1 queue, or credentials are created. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled","medical.task-run.activities.client-enabled","medical.task-run.waiting-v2.client-enabled","medical.task-run.waiting-v2.worker-enabled"},havingValue="true",matchIfMissing=false)
class TaskRunWorkerConfigurationV2 {
    @Bean TaskRunWorkerLifecycleV2 taskRunWorkerLifecycleV2(
            @Qualifier("taskRunWorkflowClientV2")WorkflowClient client,TaskRunWorkflowV2Configuration.Pinned pinned,
            @Qualifier("taskRunActivitiesClient")RunActivities activities,@Qualifier("taskRunWaitingClient")TaskRunWaitingActivities waiting,
            @Qualifier("taskRunRecoveryClientV2")TaskRunRecoveryActivitiesV2 recovery,@Qualifier("taskRunStageClientV2")TaskRunStageActivitiesV2 stages,
            @Value("${medical.task-run.section-writer-v1.enabled:false}")boolean writerEnabled,@Qualifier("taskRunSectionWriterExtensionV1") org.springframework.beans.factory.ObjectProvider<SectionWriterWorkflowExtensionV1> extension,@Qualifier("taskSectionWriterStageClientV1")org.springframework.beans.factory.ObjectProvider<TaskSectionWriterStageActivitiesV1> writer,
            @Value("${medical.task-run.source-verifier-v1.enabled:false}")boolean verifierEnabled,@Qualifier("taskRunSourceVerifierExtensionV1") org.springframework.beans.factory.ObjectProvider<SourceVerifierWorkflowExtensionV1> verifier,@Qualifier("taskSourceVerifierStageClientV1") org.springframework.beans.factory.ObjectProvider<TaskSourceVerifierStageActivitiesV1> verifierStages,
            @Value("${medical.task-run.candidate-assembly-v1.enabled:false}")boolean assemblyEnabled,@Qualifier("taskRunCandidateAssemblyExtensionV1")org.springframework.beans.factory.ObjectProvider<CandidateAssemblyWorkflowExtensionV1> assembly,@Qualifier("taskCandidateAssemblyStageClientV1")org.springframework.beans.factory.ObjectProvider<TaskCandidateAssemblyStageActivitiesV1> assemblyStages,
            @Value("${medical.task-run.waiting-v2.worker.workflow-threads}")int workflowThreads,
            @Value("${medical.task-run.waiting-v2.worker.activity-threads}")int activityThreads,
            @Value("${medical.task-run.waiting-v2.worker.shutdown-timeout}")Duration shutdown){
        if(workflowThreads<1||workflowThreads>256||activityThreads<1||activityThreads>256)
            throw new IllegalArgumentException("BOUNDED_V2_WORKER_CAPACITY_REQUIRED");
        if(assemblyEnabled){if(!writerEnabled||!verifierEnabled)throw new IllegalArgumentException("ASSEMBLY_REQUIRES_VERIFIER");return new TaskRunWorkerLifecycleV2(WorkflowWorkerV2.create(client,pinned.protocol(),extension.getObject(),verifier.getObject(),assembly.getObject(),activities,waiting,recovery,stages,writer.getObject(),verifierStages.getObject(),assemblyStages.getObject(),workflowThreads,activityThreads),shutdown);}
        if(verifierEnabled){if(!writerEnabled)throw new IllegalArgumentException("SOURCE_VERIFIER_REQUIRES_SECTION_WRITER");return new TaskRunWorkerLifecycleV2(WorkflowWorkerV2.create(client,pinned.protocol(),extension.getObject(),verifier.getObject(),activities,waiting,recovery,stages,writer.getObject(),verifierStages.getObject(),workflowThreads,activityThreads),shutdown);}
        if(writerEnabled)return new TaskRunWorkerLifecycleV2(WorkflowWorkerV2.create(client,pinned.protocol(),extension.getObject(),activities,waiting,recovery,stages,writer.getObject(),workflowThreads,activityThreads),shutdown);
        return new TaskRunWorkerLifecycleV2(WorkflowWorkerV2.create(client,pinned.protocol(),activities,waiting,recovery,stages,workflowThreads,activityThreads),shutdown);
    }
}
