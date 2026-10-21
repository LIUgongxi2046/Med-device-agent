package com.ruoyi.medical.persistence;
import com.ruoyi.medical.workflow.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.task-run.candidate-assembly-v1.enabled","medical.task-candidate-assembly.enabled","medical.task-run.waiting-v2.server-enabled"},havingValue="true",matchIfMissing=false)
class TaskCandidateAssemblyStageConfiguration {
 @Bean(name="taskCandidateAssemblyStageSchemaV1") TaskCandidateAssemblyStageSchemaV1 schema(){return new TaskCandidateAssemblyStageSchemaV1();}
 @Bean TaskCandidateAssemblyStageJournal taskCandidateAssemblyStageJournal(@Qualifier("masterDataSource")DataSource data,PermissionSchema permissions,TaskSourceVerifierSchema json,TaskRunWorkflowProtocolBinding protocols){return new TaskCandidateAssemblyStageJournal(data,permissions,json,protocols);}
 @Bean TaskCandidateAssemblyStageEndpointFactory taskCandidateAssemblyStageEndpointFactory(TaskRunActivityPreparation runs,TaskCandidateAssemblyAuthority inputs,TaskCandidateAssemblyCrossFileAuthority crossFiles,TaskCandidateAssemblyArtifactProduction production,TaskCandidateAssemblyOriginalReader originals,TaskCandidateAssemblyStageJournal stages,FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority launches,TaskRunWorkflowLedger workflow,TaskRunWorkflowProtocolBinding protocols,TaskDeterministicExecution executions){return new TaskCandidateAssemblyStageEndpointFactory(runs,inputs,crossFiles,production,originals,stages,recovery,launches,workflow,protocols,executions);}
}
