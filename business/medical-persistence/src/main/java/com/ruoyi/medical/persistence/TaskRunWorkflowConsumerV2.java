package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.util.function.ThrowingSupplier;

/** Explicit V2 START_RUN consumer. No default registration or RN01 enablement.
 * Existing attempts are query-only using their original protocol pins, even when current
 * execution permissions have been revoked. A V1 launch is never upgraded in place. */
public final class TaskRunWorkflowConsumerV2 {
    private final TaskRunReplayBootstrap replays;
    private final FormalRunControlAccess starts;
    private final FormalRunRecoveryAccess recovery;
    private final TaskRunLaunchRecoveryAuthority recoveryAuthority;
    private final AgentPublicationAuthority publications;
    private final AgentExecutionPolicyAuthority policies;
    private final RuntimeRetentionAuthority retention;
    private final TaskRunBootstrapAuthority authority;
    private final TaskRunReplayContentAuthority content;
    private final IdentityDirectoryPolicy identity;
    private final TaskRunWorkflowLedger ledger;
    private final TaskRunWorkflowDeliveryV2 delivery;
    private final TaskRunWorkflowProtocolBinding protocols;
    private final com.ruoyi.medical.workflow.WaitingWorkflowDeploymentV2 deployment;
    private final TaskRunWorkflowConfiguration configuration;
    private com.ruoyi.medical.workflow.SectionWriterWorkflowExtensionV1 extension;
    private com.ruoyi.medical.workflow.CandidateAssemblyWorkflowExtensionV1 assembly;
    TaskRunWorkflowConsumerV2 assembly(com.ruoyi.medical.workflow.CandidateAssemblyWorkflowExtensionV1 value){Objects.requireNonNull(value).require(deployment,Objects.requireNonNull(verifier));this.assembly=value;return this;}
    private com.ruoyi.medical.workflow.SourceVerifierWorkflowExtensionV1 verifier;
    TaskRunWorkflowConsumerV2 verifier(com.ruoyi.medical.workflow.SourceVerifierWorkflowExtensionV1 value){Objects.requireNonNull(value).require(deployment,extension);this.verifier=value;return this;}
    public TaskRunWorkflowConsumerV2(TaskRunReplayBootstrap replays,FormalRunControlAccess starts,FormalRunRecoveryAccess recovery,
            TaskRunLaunchRecoveryAuthority recoveryAuthority,AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies,
            RuntimeRetentionAuthority retention,TaskRunBootstrapAuthority authority,TaskRunReplayContentAuthority content,
            IdentityDirectoryPolicy identity,TaskRunWorkflowLedger ledger,TaskRunWorkflowDeliveryV2 delivery,TaskRunWorkflowConfiguration configuration,TaskRunWorkflowProtocolBinding protocols,com.ruoyi.medical.workflow.WaitingWorkflowDeploymentV2 deployment){
        this.replays=Objects.requireNonNull(replays);this.starts=Objects.requireNonNull(starts);this.recovery=Objects.requireNonNull(recovery);
        this.recoveryAuthority=Objects.requireNonNull(recoveryAuthority);this.publications=Objects.requireNonNull(publications);this.policies=Objects.requireNonNull(policies);
        this.retention=Objects.requireNonNull(retention);this.authority=Objects.requireNonNull(authority);this.content=Objects.requireNonNull(content);
        this.identity=Objects.requireNonNull(identity);this.ledger=Objects.requireNonNull(ledger);this.delivery=Objects.requireNonNull(delivery);this.configuration=Objects.requireNonNull(configuration);this.protocols=Objects.requireNonNull(protocols);this.deployment=Objects.requireNonNull(deployment);
        if(!configuration.namespace().equals(deployment.namespace())||!configuration.controlTaskQueue().equals(deployment.controlTaskQueue())||!configuration.professionalTaskQueue().equals(deployment.professionalTaskQueue()))throw new IllegalArgumentException("EXPLICIT_V2_LAUNCH_CONFIGURATION_MISMATCH");
    }
    public TaskRunWorkflowConsumerV2(TaskRunReplayBootstrap replays,FormalRunControlAccess starts,FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority recoveryAuthority,AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies,RuntimeRetentionAuthority retention,TaskRunBootstrapAuthority authority,TaskRunReplayContentAuthority content,IdentityDirectoryPolicy identity,TaskRunWorkflowLedger ledger,TaskRunWorkflowDeliveryV2 delivery,TaskRunWorkflowConfiguration configuration,TaskRunWorkflowProtocolBinding protocols,com.ruoyi.medical.workflow.WaitingWorkflowDeploymentV2 deployment,com.ruoyi.medical.workflow.SectionWriterWorkflowExtensionV1 extension){this(replays,starts,recovery,recoveryAuthority,publications,policies,retention,authority,content,identity,ledger,delivery,configuration,protocols,deployment);this.extension=Objects.requireNonNull(extension);extension.require(deployment);}
    public TaskRunWorkflowLedger.Receipt consume(HttpServletRequest http,String tenant,String run,String command)throws Exception {
        // Terminal/unknown-result recovery is deliberately selected BEFORE live execution preparation.
        var attempted=recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->ledger.attempted(recoveryAuthority.require(a,http,run,command))).get());
        if(attempted.isPresent())return recover(http,tenant,run,command);
        var registered=replays.consume(http,tenant,run,command);
        var received=starts.withReads(http,tenant,run,command,reads->ThrowingSupplier.of(()->{
            var prepared=publications.prepareControl(reads,reads.replay().required("agent_release_id").asText(),"RUN_CREATION");
            var intent=reads.execute(a->ThrowingSupplier.of(()->{var p=current(reads,a,prepared,run);var i=ledger.prepare(p,registered,configuration);protocols.prepare(p,i,deployment,extension,verifier,assembly);return i;}).get());
            var attempt=reads.execute(a->ThrowingSupplier.of(()->ledger.attempt(current(reads,a,prepared,run),intent)).get());
            if(attempt.recoveryOnly())return recoverDelivery(http,tenant,run,command);
            var permit=reads.execute(a->ThrowingSupplier.of(()->protocols.authorizeStart(current(reads,a,prepared,run),attempt,configuration,deployment)).get());
            // Both intent and attempt are committed. No SQL transaction spans start().
            return delivery.deliver(permit);
        }).get());
        // The workflow may already have completed. Never reacquire new-execution permission here.
        return recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->protocols.record(a,recoveryAuthority.require(a,http,run,command),received)).get());
    }
    public TaskRunWorkflowLedger.Receipt recover(HttpServletRequest http,String tenant,String run,String command)throws Exception {
        var recorded=recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->protocols.read(a,recoveryAuthority.require(a,http,run,command))).get());
        if(recorded.isPresent())return recorded.get();
        var received=recoverDelivery(http,tenant,run,command);
        return recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->protocols.record(a,recoveryAuthority.require(a,http,run,command),received)).get());
    }
    private TaskRunWorkflowDeliveryV2.Received recoverDelivery(HttpServletRequest http,String tenant,String run,String command)throws Exception {
        var intent=recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->protocols.authorizeRecovery(a,recoveryAuthority.require(a,http,run,command))).get());
        return delivery.recover(intent);
    }
    private TaskRunBootstrapAuthority.Verified current(FormalRunControlAccess.Reads reads,EnterpriseAccess.Admission admission,
            AgentPublicationAuthority.PreparedPublication prepared,String run)throws Exception {
        var replay=reads.replay();String project=replay.required("project_id").asText();
        var publication=publications.require(admission,replay.required("agent_release_id").asText(),"RUN_CREATION",prepared);
        var policy=policies.requireByVersion(admission,reads.policyVersion(),reads.policySha256(),project,identity.classification(),"RUN_CREATION");
        var fixed=replay.required("retention");
        var retained=retention.requireFixedForRun(admission,fixed.required("policy_revision").asText(),fixed.required("content_sha256").asText(),project,identity.classification(),"RUN_CREATION");
        var proof=authority.require(admission,publication,policy,retained,run);content.require(proof).requireUnchanged();return proof;
    }
}
