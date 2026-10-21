package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.util.function.ThrowingSupplier;

/** Durable START_RUN consumer. Intentionally not a bean: workflow launch is not RN-01 readiness;
 * actual RunActivities and node producers must exist before deployment enables the worker. */
public final class TaskRunWorkflowConsumer {
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
    private final TaskRunWorkflowDelivery delivery;
    private final TaskRunWorkflowConfiguration configuration;
    public TaskRunWorkflowConsumer(TaskRunReplayBootstrap replays,FormalRunControlAccess starts,FormalRunRecoveryAccess recovery,
            TaskRunLaunchRecoveryAuthority recoveryAuthority,AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies,
            RuntimeRetentionAuthority retention,TaskRunBootstrapAuthority authority,TaskRunReplayContentAuthority content,
            IdentityDirectoryPolicy identity,TaskRunWorkflowLedger ledger,TaskRunWorkflowDelivery delivery,TaskRunWorkflowConfiguration configuration){
        this.replays=Objects.requireNonNull(replays);this.starts=Objects.requireNonNull(starts);this.recovery=Objects.requireNonNull(recovery);
        this.recoveryAuthority=Objects.requireNonNull(recoveryAuthority);this.publications=Objects.requireNonNull(publications);this.policies=Objects.requireNonNull(policies);
        this.retention=Objects.requireNonNull(retention);this.authority=Objects.requireNonNull(authority);this.content=Objects.requireNonNull(content);
        this.identity=Objects.requireNonNull(identity);this.ledger=Objects.requireNonNull(ledger);this.delivery=Objects.requireNonNull(delivery);this.configuration=Objects.requireNonNull(configuration);
    }
    public TaskRunWorkflowLedger.Receipt consume(HttpServletRequest http,String tenant,String run,String command)throws Exception {
        // Terminal/unknown-result recovery is deliberately selected BEFORE live execution preparation.
        var attempted=recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->ledger.attempted(recoveryAuthority.require(a,http,run,command))).get());
        if(attempted.isPresent())return recover(http,tenant,run,command);
        var registered=replays.consume(http,tenant,run,command);
        var received=starts.withReads(http,tenant,run,command,reads->ThrowingSupplier.of(()->{
            var prepared=publications.prepareControl(reads,reads.replay().required("agent_release_id").asText(),"RUN_CREATION");
            var intent=reads.execute(a->ThrowingSupplier.of(()->ledger.prepare(current(reads,a,prepared,run),registered,configuration)).get());
            var attempt=reads.execute(a->ThrowingSupplier.of(()->ledger.attempt(current(reads,a,prepared,run),intent)).get());
            if(attempt.recoveryOnly())return recoverDelivery(http,tenant,run,command);
            var permit=reads.execute(a->ThrowingSupplier.of(()->ledger.authorizeStart(current(reads,a,prepared,run),attempt,configuration)).get());
            // Both intent and attempt are committed. No SQL transaction spans start().
            return delivery.deliver(permit);
        }).get());
        // The workflow may already have completed. Never reacquire new-execution permission here.
        return recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->ledger.record(recoveryAuthority.require(a,http,run,command),received)).get());
    }
    public TaskRunWorkflowLedger.Receipt recover(HttpServletRequest http,String tenant,String run,String command)throws Exception {
        var recorded=recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->ledger.read(recoveryAuthority.require(a,http,run,command))).get());
        if(recorded.isPresent())return recorded.get();
        var received=recoverDelivery(http,tenant,run,command);
        return recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->ledger.record(recoveryAuthority.require(a,http,run,command),received)).get());
    }
    private TaskRunWorkflowDelivery.Received recoverDelivery(HttpServletRequest http,String tenant,String run,String command)throws Exception {
        var intent=recovery.execute(http,tenant,run,a->ThrowingSupplier.of(()->ledger.authorizeRecovery(recoveryAuthority.require(a,http,run,command))).get());
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
