package com.ruoyi.medical.persistence;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
/** Explicit N06 physical host enablement. Does not enable document C04 or N07. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.task-candidate-assembly.enabled",havingValue="true",matchIfMissing=false)
public class TaskCandidateAssemblyArtifactConfiguration {
    @Bean TaskCandidateAssemblyCheckService taskCandidateAssemblyCheckService(@Qualifier("masterDataSource")DataSource data,TaskSourceVerifierSchema json)throws Exception{return new TaskCandidateAssemblyCheckService(data,json);}
    @Bean TaskCandidateAssemblyObjectLedger taskCandidateAssemblyObjectLedger(@Qualifier("masterDataSource")DataSource data,TaskSourceVerifierSchema json,DependencyManifestSchema dependencies,TaskCandidateAssemblyProducerAuthority producers)throws Exception{return new TaskCandidateAssemblyObjectLedger(data,json,dependencies,producers);}
    @Bean(name="candidateAssemblyObjectTransport") RuntimeObjectTransport candidateAssemblyObjectTransport(SourceUploadTransport objects,@Value("${medical.task-candidate-assembly.object-directory}")Path directory,@Value("${medical.task-candidate-assembly.maximum-object-bytes}")int maximum)throws Exception{return new RuntimeObjectTransport(objects,directory,maximum);}
    @Bean TaskCandidateAssemblyArtifactProduction taskCandidateAssemblyArtifactProduction(TaskRunActivityPreparation runs,TaskCandidateAssemblyAuthority inputs,TaskCandidateAssemblyProducerAuthority producers,TaskCandidateAssemblyObjectLedger objects,@Qualifier("candidateAssemblyObjectTransport")RuntimeObjectTransport transport,TaskCandidateAssemblyCrossFileAuthority crossFiles,TaskCandidateAssemblyCheckService checks){return new TaskCandidateAssemblyArtifactProduction(runs,inputs,producers,objects,transport,crossFiles,checks);}
}
