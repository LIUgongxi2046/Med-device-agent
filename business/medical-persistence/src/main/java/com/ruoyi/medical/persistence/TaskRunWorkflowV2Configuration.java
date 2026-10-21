package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.workflow.WaitingWorkflowDeploymentV2;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Existing operator-owned bytes only. Explicit host V2 opt-in does not enable
 * endpoint, Temporal worker, relay, launcher, RN01, or any execution by itself. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled"},havingValue="true",matchIfMissing=false)
class TaskRunWorkflowV2Configuration {
    record Pinned(TaskRunWorkflowConfiguration launch,WaitingWorkflowDeploymentV2 protocol){}
    @Bean Pinned taskRunWorkflowV2Pinned(@Value("${medical.task-run.waiting-v2.deployment-file}")Path file,@Value("${medical.task-run.waiting-v2.deployment-owner}")String owner,@Value("${medical.task-run.waiting-v2.deployment-sha256}")String expected,@Value("${medical.task-run.waiting-v2.maximum-deployment-bytes}")int maximum)throws Exception{return read(file,owner,expected,maximum);}
    @Bean WaitingWorkflowDeploymentV2 taskRunWorkflowV2Deployment(Pinned pinned){return pinned.protocol();}
    @Bean(name="taskRunWorkflowV2Launch")TaskRunWorkflowConfiguration taskRunWorkflowV2Launch(Pinned pinned){return pinned.launch();}
    @Bean TaskRunWorkflowProtocolBinding taskRunWorkflowProtocolBinding(@Qualifier("masterDataSource")DataSource data,PermissionSchema json,TaskRunBootstrapSchema schema,TaskRunWorkflowLedger ledger){return new TaskRunWorkflowProtocolBinding(data,json,schema,ledger);}
    @Bean TaskRunSectionWriterProtocolBinding taskRunSectionWriterProtocolBinding(TaskRunWorkflowProtocolBinding base){return base.writers();}
    @Bean TaskRunSourceVerifierProtocolBinding taskRunSourceVerifierProtocolBinding(TaskRunWorkflowProtocolBinding base){return base.verifiers();}
    static Pinned read(Path file,String owner,String expected,int maximum)throws Exception{
        WireValues.hash(expected);if(file==null||!file.isAbsolute()||!file.normalize().equals(file)||!file.toRealPath().equals(file)||owner==null||owner.isBlank()||maximum<1||maximum>65536)invalid();
        var principal=file.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner);var before=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);var permissions=Files.getPosixFilePermissions(file,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()<1||before.size()>maximum||!principal.equals(Files.getOwner(file,LinkOption.NOFOLLOW_LINKS))||!Set.of(PosixFilePermissions.fromString("r--------"),PosixFilePermissions.fromString("rw-------")).contains(permissions)||before.fileKey()==null)invalid();
        byte[] bytes;try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){bytes=in.readNBytes(maximum+1);}var after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(bytes.length!=before.size()||!expected.equals(WireValues.sha256(bytes))||!before.fileKey().equals(after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime())||before.size()!=after.size()||!principal.equals(Files.getOwner(file,LinkOption.NOFOLLOW_LINKS))||!permissions.equals(Files.getPosixFilePermissions(file,LinkOption.NOFOLLOW_LINKS)))invalid();
        var reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();var value=reader.readTree(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());ProfessionalRuntimeObservations.closed(value,Set.of("schema_version","launch","protocol"));if(!"2.0.0".equals(value.required("schema_version").textValue()))invalid();
        var launch=TaskRunWorkflowConfiguration.load(value.required("launch").toString());var protocol=WaitingWorkflowDeploymentV2.load(value.required("protocol").toString());if(!launch.namespace().equals(protocol.namespace())||!launch.controlTaskQueue().equals(protocol.controlTaskQueue())||!launch.professionalTaskQueue().equals(protocol.professionalTaskQueue()))invalid();return new Pinned(launch,protocol);
    }
    private static void invalid(){throw new IllegalArgumentException("EXPLICIT_PINNED_WORKFLOW_V2_DEPLOYMENT_REQUIRED");}
}
