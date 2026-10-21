package com.ruoyi.medical.persistence;
import com.ruoyi.medical.workflow.*;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ServiceClientTlsStore;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
/** Separate operator-owned immutable extension file; never changes the original V2 manifest. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled","medical.task-candidate-assembly.enabled","medical.task-run.candidate-assembly-v1.enabled","medical.professional-runtime.section-writer-enabled","medical.task-run.section-writer-v1.enabled"},havingValue="true",matchIfMissing=false)
class TaskRunCandidateAssemblyWorkflowConfiguration {
    @Bean CandidateAssemblyWorkflowExtensionV1 taskRunCandidateAssemblyExtensionV1(@Qualifier("taskRunWorkflowV2Deployment") WaitingWorkflowDeploymentV2 base,@Qualifier("taskRunSourceVerifierExtensionV1") SourceVerifierWorkflowExtensionV1 verifier,@Value("${medical.task-run.candidate-assembly-v1.deployment-file}")Path file,@Value("${medical.task-run.candidate-assembly-v1.deployment-owner}")String owner,@Value("${medical.task-run.candidate-assembly-v1.deployment-sha256}")String expected,@Value("${medical.task-run.candidate-assembly-v1.maximum-deployment-bytes}")int maximum)throws Exception{return read(file,owner,expected,maximum,base,verifier);}
    @Bean(name="taskCandidateAssemblyStageClientV1")
    @ConditionalOnProperty(name="medical.task-run.waiting-v2.client-enabled",havingValue="true",matchIfMissing=false)
    TaskCandidateAssemblyStageActivitiesV1 client(@Qualifier("taskRunActivitiesTls")ServiceClientTlsStore.ContextSource contexts,@Value("${medical.task-run.activities.origin}")URI origin,@Value("${medical.task-run.activities.timeout-millis}")long timeout,@Value("${medical.task-run.activities.maximum-response-bytes}")int maximum){return new TaskCandidateAssemblyStageHttpClientV1(origin,contexts::create,Duration.ofMillis(timeout),maximum,new TaskCandidateAssemblyStageSchemaV1());}
    static CandidateAssemblyWorkflowExtensionV1 read(Path file,String owner,String expected,int maximum,WaitingWorkflowDeploymentV2 base,SourceVerifierWorkflowExtensionV1 verifier)throws Exception{
        WireValues.hash(expected);if(file==null||!file.isAbsolute()||!file.normalize().equals(file)||!file.toRealPath().equals(file)||owner==null||owner.isBlank()||maximum<1||maximum>65536)invalid();
        var principal=file.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner);var before=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);var permissions=Files.getPosixFilePermissions(file,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()<1||before.size()>maximum||!principal.equals(Files.getOwner(file,LinkOption.NOFOLLOW_LINKS))||!Set.of(PosixFilePermissions.fromString("r--------"),PosixFilePermissions.fromString("rw-------")).contains(permissions)||before.fileKey()==null)invalid();
        byte[] bytes;try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){bytes=in.readNBytes(maximum+1);}var after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(bytes.length!=before.size()||!expected.equals(WireValues.sha256(bytes))||!before.fileKey().equals(after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime())||before.size()!=after.size()||!principal.equals(Files.getOwner(file,LinkOption.NOFOLLOW_LINKS))||!permissions.equals(Files.getPosixFilePermissions(file,LinkOption.NOFOLLOW_LINKS)))invalid();
        var extension=CandidateAssemblyWorkflowExtensionV1.load(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());extension.require(base,verifier);return extension;
    }
    private static void invalid(){throw new IllegalArgumentException("EXPLICIT_PINNED_CANDIDATE_ASSEMBLY_EXTENSION_REQUIRED");}
}
