package com.ruoyi.medical.persistence;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.task-run.candidate-assembly-v1.enabled","medical.task-candidate-assembly.enabled"},havingValue="true")
public class CandidateSetCheckReadConfiguration {
 @Bean CandidateSetCheckReadService candidateSetCheckReadService(@Qualifier("masterDataSource")DataSource data,EnterpriseAccess access,ResourceReadAuthority resources,TaskCandidateAssemblyStageJournal stages,TaskCandidateAssemblyOriginalReader originals,DocumentReadService documents){return new CandidateSetCheckReadService(data,access,resources,stages,originals,documents);}
}
