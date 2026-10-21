package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.workflow.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.professional-runtime.section-writer-enabled",havingValue="true",matchIfMissing=false)
public class TaskSectionWriterRuntimeConfiguration {
 @Bean TaskSectionWriterDispatchReservation taskSectionWriterDispatchReservation(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterInputAuthority inputs,@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity services,PermissionSchema json){return new TaskSectionWriterDispatchReservation(data,inputs,services,json);}
 @Bean TaskSectionWriterDispatchJournal taskSectionWriterDispatchJournal(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterSchema schema,PermissionSchema json,TaskRunActivityPreparation preparation,FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority launches,TaskRunWorkflowLedger workflow,TaskSectionWriterDispatchReservation reservations,RunTokenAuthority tokens,TaskSectionWriterHostBootstrap bootstrap,TaskSectionWriterObjectLedger objects,TaskSectionWriterProducerAuthority producers,@Qualifier("sectionWriterObjectTransport")RuntimeObjectTransport transport,TaskSectionWriterDeployment deployment){return new TaskSectionWriterDispatchJournal(data,schema,json,preparation,recovery,launches,workflow,reservations,tokens,bootstrap,objects,producers,transport,deployment);}
 @Bean TaskSectionWriterDeployment taskSectionWriterDeployment(@Value("${medical.professional-runtime.section-writer.deployment-file}")Path file,@Value("${medical.professional-runtime.section-writer.deployment-owner}")String owner,@Value("${medical.professional-runtime.section-writer.deployment-sha256}")String hash,@Value("${medical.professional-runtime.section-writer.maximum-deployment-bytes}")int maximum)throws Exception{return readDeployment(file,owner,hash,maximum);}
    static TaskSectionWriterDeployment readDeployment(Path file,String owner,String expected,int maximum)throws Exception {
        if(file==null||!file.isAbsolute()||!file.normalize().equals(file)||owner==null||owner.isBlank()||maximum<1||maximum>2097152||!file.toRealPath().equals(file))throw new IllegalArgumentException("PINNED_PROFESSIONAL_DEPLOYMENT_REQUIRED");
        var principal=file.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner);
        var before=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()<1||before.size()>maximum||!Files.getOwner(file,LinkOption.NOFOLLOW_LINKS).equals(principal))throw new IllegalArgumentException("OWNED_PROFESSIONAL_DEPLOYMENT_REQUIRED");
        var permissions=Files.getPosixFilePermissions(file,LinkOption.NOFOLLOW_LINKS);if(!Set.of(PosixFilePermissions.fromString("r--------"),PosixFilePermissions.fromString("rw-------")).contains(permissions))throw new IllegalArgumentException("PRIVATE_PROFESSIONAL_DEPLOYMENT_REQUIRED");
        byte[] bytes;try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){bytes=input.readNBytes(maximum+1);}var after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(bytes.length!=before.size()||bytes.length>maximum||!Objects.equals(before.fileKey(),after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime())||before.size()!=after.size())throw new IllegalArgumentException("PROFESSIONAL_DEPLOYMENT_CHANGED");
        var json=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        return new TaskSectionWriterDeployment(json.readTree(bytes),expected);
    }

 @Bean TaskSectionWriterRuntimeAuthority taskSectionWriterRuntimeAuthority(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterSchema schema){return new TaskSectionWriterRuntimeAuthority(data,schema);}
 @Bean TaskSectionWriterWorkerAccess taskSectionWriterWorkerAccess(FormalWorkerAccess workers,TaskSectionWriterRuntimeAuthority authority){return new TaskSectionWriterWorkerAccess(workers,authority);}
 @Bean TaskSectionWriterPromptCompiler taskSectionWriterPromptCompiler(FormalCapabilityIdentityAuthority capabilities,PermissionSchema json)throws Exception{return new TaskSectionWriterPromptCompiler(capabilities,json);}
 @Bean TaskSectionWriterRuntimeAdapter taskSectionWriterRuntimeAdapter(@Qualifier("masterDataSource")DataSource data,TaskSectionWriterSchema schema,PermissionSchema json,TaskSectionWriterDeployment deployment,@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identities,TaskSectionWriterProducerAuthority producers){return new TaskSectionWriterRuntimeAdapter(data,schema,json,deployment,identities,producers);}
 @Bean TaskSectionWriterSupervisor taskSectionWriterSupervisor(@Qualifier("masterDataSource")DataSource data,PermissionSchema json,@Qualifier("sectionWriterSupervisorTrust")ServiceCertificateIdentity.TrustSource trust,@Qualifier("sectionWriterSupervisorTls")ServiceClientTlsStore.ContextSource tls){return new TaskSectionWriterSupervisor(data,json,new ServiceCertificateIdentity(trust),trust,tls);}
 @Bean TaskSectionWriterHostAccess taskSectionWriterHostAccess(TaskSectionWriterWorkerAccess workers,SourceUploadTransport transport,TaskSectionWriterDeployment deployment,TaskInvocationPackages packages,TaskSectionWriterPromptCompiler compiler,TaskSectionWriterRuntimeAdapter runtime,RunTokenAuthority tokens,TaskSectionWriterContextReads reads,TaskSectionWriterProducerAuthority producers){return new TaskSectionWriterHostAccess(workers,transport,deployment,packages,compiler,runtime,tokens,reads,producers);}
 @Bean TaskSectionWriterHostOrchestrator taskSectionWriterHostOrchestrator(TaskSectionWriterRuntimeAdapter runtime,TaskSectionWriterSupervisor supervisor){return new TaskSectionWriterHostOrchestrator(runtime,supervisor);}
 @Bean TaskSectionWriterHostBootstrap taskSectionWriterHostBootstrap(@Qualifier("masterDataSource")DataSource data,FormalTaskNodeAuthority nodes,RunTokenAuthority tokens,TaskSectionWriterHostAccess hosts,@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identities,TaskSectionWriterDeployment deployment,PermissionSchema json,RunTokenAuthoritySchema schema){return new TaskSectionWriterHostBootstrap(data,nodes,tokens,hosts,identities,deployment,json,schema);}
 @Bean TaskSectionWriterSupervisorGate taskSectionWriterSupervisorGate(@Qualifier("masterDataSource")DataSource data,TenantTransaction transactions,TaskSectionWriterSupervisorGovernance governance,TaskSectionWriterDeployment deployment,@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity services,TaskSectionWriterSchema schema,TaskInvocationPackages packages,TaskSectionWriterProducerAuthority producers){return new TaskSectionWriterSupervisorGate(data,transactions,governance,deployment,services,schema,packages,producers);}
 @Bean TaskSectionWriterSupervisorGovernance taskSectionWriterSupervisorGovernance(@Qualifier("masterDataSource")DataSource data,PermissionMapper permissions,PermissionAuthority grants,PlatformMemberIdentityStatus identities,PermissionSchema json,@Qualifier("taskSectionWriterProducerSchema")TaskNodeProducerSchema producerSchema,TaskDelegationSchema delegationSchema){return new TaskSectionWriterSupervisorGovernance(data,permissions,grants,identities,json,producerSchema,delegationSchema);}
 @Bean(name="sectionWriterRuntimeObservations") ProfessionalRuntimeObservations sectionWriterRuntimeObservations(@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identities,TenantTransaction transactions,@Qualifier("masterDataSource")DataSource data,PermissionSchema json){return new ProfessionalRuntimeObservations(identities,transactions,data,json);}
 @Bean TaskSectionWriterContextReads taskSectionWriterContextReads(SourceUploadTransport transport,SourceReadAuthority sources,TaskFieldSourceAuthority processing,TaskResearchSourceReader literals,PermissionSchema json,TaskSectionWriterProducerAuthority producers){return new TaskSectionWriterContextReads(transport,sources,processing,literals,json,producers);}
}
