package com.ruoyi.medical.persistence;
import com.ruoyi.medical.workflow.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.task-run.waiting-v2.server-enabled","medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled","medical.task-strict-field.enabled"},havingValue="true",matchIfMissing=false)
class TaskRunStageConfigurationV2 {
    @Bean(name="taskRunStageServerSchemaV2")TaskRunStageSchemaV2 schema(){return new TaskRunStageSchemaV2();}
    @Bean TaskRunStageCommitServiceV2 taskRunStageCommitServiceV2(@Qualifier("masterDataSource")DataSource data,PermissionSchema json,TaskStrictFieldCompletionAuthority results,TaskRunGraphService graphs,TaskRunWorkflowProtocolBinding protocols){return new TaskRunStageCommitServiceV2(data,json,results,graphs,protocols);}
    @Bean TaskRunStageEndpointFactoryV2 taskRunStageEndpointFactoryV2(TaskRunActivityPreparation preparation,FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority launchAuthority,TaskRunWorkflowLedger launches,TaskRunWorkflowProtocolBinding protocols,FormalTaskNodeAuthority nodes,TaskStrictFieldExecutionBridge execution,TaskRunStageCommitServiceV2 commits,WaitingWorkflowDeploymentV2 deployment){return new TaskRunStageEndpointFactoryV2(preparation,recovery,launchAuthority,launches,protocols,nodes,execution,commits,deployment);}
}
