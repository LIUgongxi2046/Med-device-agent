package com.ruoyi.medical.persistence;
import com.ruoyi.medical.workflow.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.task-run.waiting-v2.server-enabled","medical.task-run.section-writer-v1.enabled","medical.professional-runtime.section-writer-enabled"},havingValue="true",matchIfMissing=false)
class TaskSectionWriterStageConfiguration {
    @Bean(name="taskSectionWriterStageSchemaV1")TaskSectionWriterStageSchemaV1 schema(){return new TaskSectionWriterStageSchemaV1();}
    @Bean TaskSectionWriterLifecycleFacts taskSectionWriterLifecycleFacts(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterDispatchJournal dispatches,TaskSectionWriterInvocationRecords invocations,TaskSectionWriterSupervisor supervisor,TaskSectionWriterSchema json,TaskRunSectionWriterProtocolBinding protocols){return new TaskSectionWriterLifecycleFacts(data,dispatches,invocations,supervisor,json,protocols);}
    @Bean TaskSectionWriterStageCommitService taskSectionWriterStageCommitService(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterSchema json,PermissionSchema permission,TaskRunGraphService graphs,TaskSectionWriterCandidateRecovery candidates,TaskSectionWriterDispatchJournal dispatches,TaskRunSectionWriterProtocolBinding protocols){return new TaskSectionWriterStageCommitService(data,json,permission,graphs,candidates,dispatches,protocols);}
    @Bean TaskSectionWriterStageEndpointFactory taskSectionWriterStageEndpointFactory(TaskSectionWriterPreparation materializer,TaskRunActivityPreparation current,FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority launches,TaskRunWorkflowLedger workflow,TaskRunWorkflowProtocolBinding protocols,TaskRunSectionWriterProtocolBinding writerProtocols,FormalTaskNodeAuthority nodes,TaskSectionWriterDispatchJournal dispatches,TaskSectionWriterCandidateRecovery candidates,TaskSectionWriterStageCommitService commits,WaitingWorkflowDeploymentV2 base,SectionWriterWorkflowExtensionV1 extension){return new TaskSectionWriterStageEndpointFactory(materializer,current,recovery,launches,workflow,protocols,writerProtocols,nodes,dispatches,candidates,commits,base,extension);}
}
