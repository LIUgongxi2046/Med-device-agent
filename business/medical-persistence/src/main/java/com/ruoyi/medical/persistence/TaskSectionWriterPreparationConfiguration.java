package com.ruoyi.medical.persistence;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.professional-runtime.section-writer-enabled",havingValue="true",matchIfMissing=false)
public class TaskSectionWriterPreparationConfiguration {
    @Bean DocumentInitialRevisionService documentInitialRevisionService(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterSchema json,TemplateVersionStore templates,TaskRunReplayContentAuthority contents,DependencyManifestSchema dependencies,TaskSectionWriterProducerAuthority producers)throws Exception{return new DocumentInitialRevisionService(data,json,templates,contents,dependencies,producers);}
    @Bean TaskSectionWriterMaterializer taskSectionWriterMaterializer(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterSchema json,RunTokenAuthoritySchema tokens){return new TaskSectionWriterMaterializer(data,json,tokens);}
    @Bean TaskSectionWriterPreparation taskSectionWriterPreparation(@Qualifier("masterDataSource")DataSource data,TaskRunActivityPreparation runs,DocumentInitialRevisionService documents,TaskSectionWriterInputAuthority inputs,TaskSectionWriterProducerAuthority producers,TaskSectionWriterObjectLedger objects,TaskSectionWriterMaterializer nodes,@Qualifier("sectionWriterObjectTransport")RuntimeObjectTransport transport,TaskSectionWriterSchema json,TaskResearchCompletionAuthority research,TaskStrictFieldWriterReadAuthority fields,FormalCapabilityIdentityAuthority roles,FormalRuntimeCapabilityIdentityAuthority tools,RunTokenAuthoritySchema tokens,PermissionSchema permissions){return new TaskSectionWriterPreparation(data,runs,documents,inputs,producers,objects,nodes,transport,json,research,fields,roles,tools,tokens,permissions);}
}
