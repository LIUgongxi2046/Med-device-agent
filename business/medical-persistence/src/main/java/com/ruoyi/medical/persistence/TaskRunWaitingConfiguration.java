package com.ruoyi.medical.persistence;
import com.ruoyi.medical.workflow.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled","medical.task-run.waiting-v2.server-enabled"},havingValue="true",matchIfMissing=false)
class TaskRunWaitingConfiguration {
    @Bean(name="taskRunWaitingServerSchema")TaskRunWaitingSchema schema(){return new TaskRunWaitingSchema();}
    @Bean TaskRunWaitingCommitService taskRunWaitingCommitService(@Qualifier("masterDataSource")DataSource data,PermissionSchema json,TaskRuntimeJournal runtime,TaskRegisteredToolWaitingCompletion producers,TaskRunWorkflowProtocolBinding protocols){return new TaskRunWaitingCommitService(data,json,runtime,producers,protocols);}
    @Bean TaskRunWaitingEndpointFactory taskRunWaitingEndpointFactory(TaskRunActivityPreparation preparation,FormalRunRecoveryAccess recovery,FormalTaskNodeAuthority nodes,TaskRunLaunchRecoveryAuthority launchAuthority,TaskRunWorkflowLedger launches,TaskRunWorkflowProtocolBinding protocols,TaskRunDispatchReservation reservations,TaskRuntimeJournal runtime,TaskRunWaitingCommitService commits,WaitingWorkflowDeploymentV2 deployment){return new TaskRunWaitingEndpointFactory(preparation,recovery,nodes,launchAuthority,launches,protocols,reservations,runtime,commits,deployment);}
    @Bean(name="taskRunRecoveryServerSchemaV2")TaskRunRecoverySchemaV2 recoverySchema(){return new TaskRunRecoverySchemaV2();}
    @Bean TaskRunRecoveryEndpointFactoryV2 taskRunRecoveryEndpointFactoryV2(@Qualifier("masterDataSource")DataSource data,TaskRunActivityPreparation preparation,TaskNodeInputAuthority inputs,FormalTaskNodeAuthority nodes,FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority launchAuthority,TaskRunWorkflowLedger launches,TaskRunWorkflowProtocolBinding protocols,TaskRunDispatchReservation reservations,TaskRuntimeJournal runtime,TaskResearchCommitService commits,TaskRunWaitingCommitService waiting,TaskResearchCompletionAuthority results,TaskRegisteredToolWaitingCompletion waitingResults,WaitingWorkflowDeploymentV2 deployment,PermissionSchema json,TaskRunLifecycleService lifecycle,TaskRunGraphService graphs,TaskRunStageCommitServiceV2 stages,TaskStrictFieldExecutionBridge stageExecution){return new TaskRunRecoveryEndpointFactoryV2(data,preparation,inputs,nodes,recovery,launchAuthority,launches,protocols,reservations,runtime,commits,waiting,results,waitingResults,deployment,json,lifecycle,graphs,stages,stageExecution);}
}
