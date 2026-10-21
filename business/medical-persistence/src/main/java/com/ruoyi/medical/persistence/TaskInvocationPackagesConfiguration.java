package com.ruoyi.medical.persistence;
import com.ruoyi.medical.persistence.mapper.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
/** Shared physical package transport; enabling N07 does not enable the TASK execution lane. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnExpression("${medical.professional-runtime.task-enabled:false} or ${medical.professional-runtime.section-writer-enabled:false} or ${medical.professional-runtime.source-verifier-enabled:false} or ${medical.professional-runtime.document-review-enabled:false}")
public class TaskInvocationPackagesConfiguration {
 @Bean TaskInvocationPackages taskInvocationPackages(ArtifactRegistryMapper artifacts,SourceReadMapper sources,DependencyLineage lineage,ArtifactSourceAuthority authority,AgentPublicationDependencies governance,EvaluationReferences profiles,EvaluationDependencyPolicy policy,SourceUploadTransport transport){return new TaskInvocationPackages(artifacts,sources,lineage,authority,governance,profiles,policy,transport);}
}
