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
@ConditionalOnProperty(name="medical.professional-runtime.source-verifier-enabled",havingValue="true",matchIfMissing=false)
public class TaskSourceVerifierRuntimeConfiguration {
 @Bean TaskSourceVerifierDeployment taskSourceVerifierDeployment(@Value("${medical.professional-runtime.source-verifier.deployment-file}")Path file,@Value("${medical.professional-runtime.source-verifier.deployment-owner}")String owner,@Value("${medical.professional-runtime.source-verifier.deployment-sha256}")String hash,@Value("${medical.professional-runtime.source-verifier.maximum-deployment-bytes}")int maximum)throws Exception{return readDeployment(file,owner,hash,maximum);}
    static TaskSourceVerifierDeployment readDeployment(Path file,String owner,String expected,int maximum)throws Exception {
        if(file==null||!file.isAbsolute()||!file.normalize().equals(file)||owner==null||owner.isBlank()||maximum<1||maximum>2097152||!file.toRealPath().equals(file))throw new IllegalArgumentException("PINNED_PROFESSIONAL_DEPLOYMENT_REQUIRED");
        var principal=file.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner);
        var before=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()<1||before.size()>maximum||!Files.getOwner(file,LinkOption.NOFOLLOW_LINKS).equals(principal))throw new IllegalArgumentException("OWNED_PROFESSIONAL_DEPLOYMENT_REQUIRED");
        var permissions=Files.getPosixFilePermissions(file,LinkOption.NOFOLLOW_LINKS);if(!Set.of(PosixFilePermissions.fromString("r--------"),PosixFilePermissions.fromString("rw-------")).contains(permissions))throw new IllegalArgumentException("PRIVATE_PROFESSIONAL_DEPLOYMENT_REQUIRED");
        byte[] bytes;try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){bytes=input.readNBytes(maximum+1);}var after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(bytes.length!=before.size()||bytes.length>maximum||!Objects.equals(before.fileKey(),after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime())||before.size()!=after.size())throw new IllegalArgumentException("PROFESSIONAL_DEPLOYMENT_CHANGED");
        var json=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        return new TaskSourceVerifierDeployment(json.readTree(bytes),expected);
    }

 @Bean TaskSourceVerifierRuntimeAuthority taskSourceVerifierRuntimeAuthority(@Qualifier("masterDataSource")DataSource data,TaskSourceVerifierSchema schema){return new TaskSourceVerifierRuntimeAuthority(data,schema);}
 @Bean TaskSourceVerifierWorkerAccess taskSourceVerifierWorkerAccess(FormalWorkerAccess workers,TaskSourceVerifierRuntimeAuthority authority){return new TaskSourceVerifierWorkerAccess(workers,authority);}
 @Bean TaskSourceVerifierPromptCompiler taskSourceVerifierPromptCompiler(FormalCapabilityIdentityAuthority capabilities,PermissionSchema json)throws Exception{return new TaskSourceVerifierPromptCompiler(capabilities,json);}
 @Bean TaskSourceVerifierRuntimeAdapter taskSourceVerifierRuntimeAdapter(@Qualifier("masterDataSource")DataSource data,TaskSourceVerifierSchema schema,PermissionSchema json,TaskSourceVerifierDeployment deployment,@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identities,TaskSourceVerifierProducerAuthority producers){return new TaskSourceVerifierRuntimeAdapter(data,schema,json,deployment,identities,producers);}
 @Bean TaskSourceVerifierSupervisor taskSourceVerifierSupervisor(@Qualifier("masterDataSource")DataSource data,PermissionSchema json,@Qualifier("sourceVerifierSupervisorTrust")ServiceCertificateIdentity.TrustSource trust,@Qualifier("sourceVerifierSupervisorTls")ServiceClientTlsStore.ContextSource tls){return new TaskSourceVerifierSupervisor(data,json,new ServiceCertificateIdentity(trust),trust,tls);}
 @Bean TaskSourceVerifierHostAccess taskSourceVerifierHostAccess(TaskSourceVerifierWorkerAccess workers,SourceUploadTransport transport,TaskSourceVerifierDeployment deployment,TaskInvocationPackages packages,TaskSourceVerifierPromptCompiler compiler,TaskSourceVerifierRuntimeAdapter runtime,RunTokenAuthority tokens,TaskSourceVerifierContextReads reads,TaskSourceVerifierProducerAuthority producers){return new TaskSourceVerifierHostAccess(workers,transport,deployment,packages,compiler,runtime,tokens,reads,producers);}
 @Bean TaskSourceVerifierHostOrchestrator taskSourceVerifierHostOrchestrator(TaskSourceVerifierRuntimeAdapter runtime,TaskSourceVerifierSupervisor supervisor){return new TaskSourceVerifierHostOrchestrator(runtime,supervisor);}
 @Bean TaskSourceVerifierHostBootstrap taskSourceVerifierHostBootstrap(@Qualifier("masterDataSource")DataSource data,FormalTaskNodeAuthority nodes,RunTokenAuthority tokens,TaskSourceVerifierHostAccess hosts,@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identities,TaskSourceVerifierDeployment deployment,PermissionSchema json,RunTokenAuthoritySchema schema){return new TaskSourceVerifierHostBootstrap(data,nodes,tokens,hosts,identities,deployment,json,schema);}
 @Bean TaskSourceVerifierSupervisorGate taskSourceVerifierSupervisorGate(@Qualifier("masterDataSource")DataSource data,TenantTransaction transactions,TaskSourceVerifierSupervisorGovernance governance,TaskSourceVerifierDeployment deployment,@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity services,TaskSourceVerifierSchema schema,TaskInvocationPackages packages,TaskSourceVerifierProducerAuthority producers){return new TaskSourceVerifierSupervisorGate(data,transactions,governance,deployment,services,schema,packages,producers);}
 @Bean TaskSourceVerifierSupervisorGovernance taskSourceVerifierSupervisorGovernance(@Qualifier("masterDataSource")DataSource data,PermissionMapper permissions,PermissionAuthority grants,PlatformMemberIdentityStatus identities,PermissionSchema json,@Qualifier("taskSourceVerifierProducerSchema")TaskNodeProducerSchema producerSchema,TaskDelegationSchema delegationSchema){return new TaskSourceVerifierSupervisorGovernance(data,permissions,grants,identities,json,producerSchema,delegationSchema);}
 @Bean(name="sourceVerifierRuntimeObservations") ProfessionalRuntimeObservations sourceVerifierRuntimeObservations(@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identities,TenantTransaction transactions,@Qualifier("masterDataSource")DataSource data,PermissionSchema json){return new ProfessionalRuntimeObservations(identities,transactions,data,json);}
 @Bean TaskSourceVerifierContextReads taskSourceVerifierContextReads(@Qualifier("masterDataSource")DataSource data,SourceUploadTransport transport,SourceReadAuthority sources,TaskFieldSourceAuthority processing,TaskResearchSourceReader literals,PermissionSchema json,TaskSourceVerifierProducerAuthority producers){return new TaskSourceVerifierContextReads(data,transport,sources,processing,literals,json,producers);}
}
