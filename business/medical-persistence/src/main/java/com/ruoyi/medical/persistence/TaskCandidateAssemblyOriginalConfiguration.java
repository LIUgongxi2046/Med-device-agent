package com.ruoyi.medical.persistence;
import javax.sql.DataSource;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.task-run.candidate-assembly-v1.enabled","medical.task-candidate-assembly.enabled"},havingValue="true",matchIfMissing=false)
public class TaskCandidateAssemblyOriginalConfiguration {
 @Bean TaskCandidateAssemblyOriginalReader taskCandidateAssemblyOriginalReader(EnterpriseAccess access,@Qualifier("masterDataSource")DataSource data,TaskSourceVerifierSchema json,FormalRunRecoveryAccess recovery,TaskCandidateAssemblyStageJournal stages,TaskCandidateAssemblyObjectLedger objects,@Qualifier("candidateAssemblyObjectTransport")RuntimeObjectTransport transport,SourceReadAuthority sources,SourceReadMapper sourceRows,ResourceReadAuthority grants,DependencyLineage lineage,TemplateDependencyPolicy limits)throws Exception{return new TaskCandidateAssemblyOriginalReader(access,data,json,recovery,stages,objects,transport,sources,sourceRows,grants,lineage,limits);}
}
