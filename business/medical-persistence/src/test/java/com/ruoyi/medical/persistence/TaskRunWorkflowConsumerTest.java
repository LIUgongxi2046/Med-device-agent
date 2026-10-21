package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Orchestration doubles only; actual private recovery proofs are exercised separately. */
class TaskRunWorkflowConsumerTest {
    @Test void completedReceiptUsesCurrentReadRecoveryWithoutNewStartOrTemporalHistory()throws Exception{
        var replay=mock(TaskRunReplayBootstrap.class);var start=mock(FormalRunControlAccess.class);var recovery=mock(FormalRunRecoveryAccess.class);
        var authority=mock(TaskRunLaunchRecoveryAuthority.class);var publication=mock(AgentPublicationAuthority.class);
        var policies=mock(AgentExecutionPolicyAuthority.class);var retention=mock(RuntimeRetentionAuthority.class);
        var bootstrap=mock(TaskRunBootstrapAuthority.class);var content=mock(TaskRunReplayContentAuthority.class);
        var identity=mock(IdentityDirectoryPolicy.class);var ledger=mock(TaskRunWorkflowLedger.class);var delivery=mock(TaskRunWorkflowDelivery.class);
        var config=mock(TaskRunWorkflowConfiguration.class);var http=mock(HttpServletRequest.class);var admission=mock(EnterpriseAccess.Admission.class);
        var proof=mock(TaskRunLaunchRecoveryAuthority.Verified.class);var receipt=mock(TaskRunWorkflowLedger.Receipt.class);
        when(recovery.execute(eq(http),eq("tenant"),eq("run"),any())).thenAnswer(a->((Function<EnterpriseAccess.Admission,?>)a.getArgument(3)).apply(admission));
        when(authority.require(admission,http,"run","command")).thenReturn(proof);when(ledger.read(proof)).thenReturn(Optional.of(receipt));
        var consumer=new TaskRunWorkflowConsumer(replay,start,recovery,authority,publication,policies,retention,bootstrap,content,identity,ledger,delivery,config);
        assertSame(receipt,consumer.recover(http,"tenant","run","command"));
        verify(authority).require(admission,http,"run","command");verify(ledger).read(proof);
        verifyNoInteractions(replay,start,publication,policies,retention,bootstrap,content,identity,delivery,config);
        verify(ledger,never()).recover(any());verify(ledger,never()).record(any(),any());
    }
}
