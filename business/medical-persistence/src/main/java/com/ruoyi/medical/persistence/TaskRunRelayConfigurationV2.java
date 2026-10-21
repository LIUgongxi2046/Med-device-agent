package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.*;
import io.temporal.client.WorkflowClient;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Explicit relay composition only. A named operator-provided Temporal client is
 * required; this never creates credentials, worker service identities, or RN01 admission. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled","medical.task-run.waiting-v2.relay-enabled"},havingValue="true",matchIfMissing=false)
class TaskRunRelayConfigurationV2 {
    @Bean TaskRunReplayBootstrap taskRunReplayBootstrapV2(FormalRunControlAccess controls,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,RuntimeRetentionAuthority retention,TaskRunBootstrapAuthority authority,
            TaskRunReplayLedger ledger,@Qualifier("taskNodeObjectTransport")RuntimeObjectTransport objects,
            TaskRunReplayRegistration registration,IdentityDirectoryPolicy identity,TaskRunReplayContentAuthority content){
        return new TaskRunReplayBootstrap(controls,publications,policies,retention,authority,ledger,objects,registration,identity,content);
    }
    @Bean TaskRunWorkflowDeliveryV2 taskRunWorkflowDeliveryV2(@Qualifier("taskRunWorkflowClientV2")WorkflowClient client){return new TaskRunWorkflowDeliveryV2(client);}
    @Bean TaskRunWorkflowConsumerV2 taskRunWorkflowConsumerV2(TaskRunReplayBootstrap replays,FormalRunControlAccess starts,FormalRunRecoveryAccess recovery,
            TaskRunLaunchRecoveryAuthority recoveries,AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies,
            RuntimeRetentionAuthority retention,TaskRunBootstrapAuthority authority,TaskRunReplayContentAuthority content,IdentityDirectoryPolicy identity,
            TaskRunWorkflowLedger ledger,TaskRunWorkflowDeliveryV2 delivery,TaskRunWorkflowV2Configuration.Pinned pinned,TaskRunWorkflowProtocolBinding protocols,@Value("${medical.task-run.section-writer-v1.enabled:false}")boolean writerEnabled,@Qualifier("taskRunSectionWriterExtensionV1") org.springframework.beans.factory.ObjectProvider<com.ruoyi.medical.workflow.SectionWriterWorkflowExtensionV1> extension,@Value("${medical.task-run.source-verifier-v1.enabled:false}")boolean verifierEnabled,@Qualifier("taskRunSourceVerifierExtensionV1") org.springframework.beans.factory.ObjectProvider<com.ruoyi.medical.workflow.SourceVerifierWorkflowExtensionV1> verifier,@Value("${medical.task-run.candidate-assembly-v1.enabled:false}")boolean assemblyEnabled,@Qualifier("taskRunCandidateAssemblyExtensionV1")org.springframework.beans.factory.ObjectProvider<com.ruoyi.medical.workflow.CandidateAssemblyWorkflowExtensionV1> assembly){
        if(assemblyEnabled){if(!verifierEnabled||!writerEnabled)throw new IllegalArgumentException("ASSEMBLY_REQUIRES_VERIFIER");return new TaskRunWorkflowConsumerV2(replays,starts,recovery,recoveries,publications,policies,retention,authority,content,identity,ledger,delivery,pinned.launch(),protocols,pinned.protocol(),extension.getObject()).verifier(verifier.getObject()).assembly(assembly.getObject());}
        if(verifierEnabled){if(!writerEnabled)throw new IllegalArgumentException("SOURCE_VERIFIER_REQUIRES_SECTION_WRITER");return new TaskRunWorkflowConsumerV2(replays,starts,recovery,recoveries,publications,policies,retention,authority,content,identity,ledger,delivery,pinned.launch(),protocols,pinned.protocol(),extension.getObject()).verifier(verifier.getObject());}
        if(writerEnabled)return new TaskRunWorkflowConsumerV2(replays,starts,recovery,recoveries,publications,policies,retention,authority,content,identity,ledger,delivery,pinned.launch(),protocols,pinned.protocol(),extension.getObject());
        return new TaskRunWorkflowConsumerV2(replays,starts,recovery,recoveries,publications,policies,retention,authority,content,identity,ledger,delivery,pinned.launch(),protocols,pinned.protocol());
    }
    @Bean TaskRunOutboxRelayV2 taskRunOutboxRelayV2(@Qualifier("masterDataSource")DataSource data,
            @Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identities,TaskRunWorkflowConsumerV2 starts,FormalRunRecoveryAccess recovery,
            TaskRunLaunchRecoveryAuthority authority,TaskRunWorkflowLedger ledger,TaskRunWorkflowProtocolBinding protocols,TaskRunLifecycleService lifecycle,
            @Qualifier("taskRunWorkflowClientV2")WorkflowClient client,@Value("${medical.task-run.waiting-v2.relay.maximum-page-size}")int maximum){
        return new TaskRunOutboxRelayV2(data,identities,starts,recovery,authority,ledger,protocols,lifecycle,client,maximum);
    }
}
