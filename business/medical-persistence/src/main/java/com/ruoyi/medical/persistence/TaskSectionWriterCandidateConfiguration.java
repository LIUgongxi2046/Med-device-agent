package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import javax.sql.DataSource;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.professional-runtime.section-writer-enabled",havingValue="true",matchIfMissing=false)
public class TaskSectionWriterCandidateConfiguration {
    @Bean TaskSectionWriterSchema taskSectionWriterSchema(){return new TaskSectionWriterSchema();}
    @Bean TaskSectionWriterInputAuthority taskSectionWriterInputAuthority(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterSchema schema,PermissionSchema json,TaskRunGraphService graphs,TaskResearchCompletionAuthority research,TaskStrictFieldWriterReadAuthority fields,TaskRunReplayContentAuthority content,FormalCapabilityIdentityAuthority roles,FormalRuntimeCapabilityIdentityAuthority tools,RunTokenAuthoritySchema tokens,ResourceReadAuthority resources,ArtifactRegistryMapper registry,DocumentInitialRevisionService initial,ScenarioSpecificationService scenarios){return new TaskSectionWriterInputAuthority(data,schema,json,graphs,research,fields,content,roles,tools,tokens,resources,registry,initial).withScenarios(scenarios);}
    @Bean TaskSectionWriterNormalizer taskSectionWriterNormalizer(TaskSectionWriterSchema schema,@Value("${medical.professional-runtime.section-writer.maximum-body-nodes}")int nodes,@Value("${medical.professional-runtime.section-writer.maximum-table-cells}")int cells,@Value("${medical.professional-runtime.section-writer.maximum-relationships}")int relationships)throws Exception{return new TaskSectionWriterNormalizer(schema,new DocumentBodySemantics.Limits(nodes,cells,relationships));}
    @Bean TaskSectionWriterCandidateService taskSectionWriterCandidateService(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterSchema schema,TaskSectionWriterNormalizer normalizer)throws Exception{return new TaskSectionWriterCandidateService(data,schema,normalizer);}
    @Bean TaskSectionWriterObjectLedger taskSectionWriterObjectLedger(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterSchema schema,DependencyManifestSchema dependencies,TaskSectionWriterInputAuthority inputs){return new TaskSectionWriterObjectLedger(data,schema,dependencies,inputs);}
    @Bean(name="sectionWriterObjectTransport") RuntimeObjectTransport sectionWriterObjectTransport(SourceUploadTransport objects,TaskSectionWriterDeployment deployment,@Value("${medical.professional-runtime.section-writer.object-directory}")Path directory)throws Exception{return new RuntimeObjectTransport(objects,directory,deployment.maximumBytes());}
    @Bean TaskSectionWriterFinishOrchestrator taskSectionWriterFinishOrchestrator(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterRuntimeAdapter runtime,TaskSectionWriterSupervisor supervisor,TaskSectionWriterInvocationService invocations,TaskSectionWriterCandidateService candidates,TaskSectionWriterObjectLedger objects,@Qualifier("sectionWriterObjectTransport")RuntimeObjectTransport transport,TaskSectionWriterProducerAuthority producers,RunTokenAuthority tokens,RunTokenAuthoritySchema tokenSchema,TaskSectionWriterWorkerAccess workers,TaskSectionWriterSchema schema){return new TaskSectionWriterFinishOrchestrator(data,runtime,supervisor,invocations,candidates,objects,transport,producers,tokens,tokenSchema,workers,schema);}
    @Bean TaskSectionWriterCandidateRecovery taskSectionWriterCandidateRecovery(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterSchema schema,TaskSectionWriterInvocationService invocations,FormalCapabilityIdentityAuthority roles){return new TaskSectionWriterCandidateRecovery(data,schema,invocations,roles);}

}
