package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TaskInvocationProducerGateTest {
    @Test void producerDenialPrecedesEveryIntentAndPhysicalTransfer()throws Exception {
        var outcomes=mock(FormalInvocationOutcomeAuthority.class);var captures=mock(FormalProviderCaptureEvidence.class);
        var ledger=mock(FormalInvocationObjectLedger.class);var transport=mock(RuntimeObjectTransport.class);
        var registrations=mock(FormalInvocationObjectRegistration.class);var proof=mock(FormalInvocationOutcomeAuthority.VerifiedOutcome.class);
        var capture=mock(FormalProviderCaptureEvidence.Verified.class);var http=mock(HttpServletRequest.class);byte[] result="{}".getBytes(),raw="{}".getBytes();
        when(captures.require(proof,raw)).thenReturn(capture);
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(registrations).requireProduction(proof,capture);
        when(outcomes.execute(eq(http),anyString(),anyString(),anyString(),any(byte[].class),any())).thenAnswer(call->((FormalInvocationOutcomeAuthority.Work<?>)call.getArgument(5)).apply(proof));
        var service=new FormalInvocationReceiptObjects(outcomes,captures,ledger,transport,registrations);
        assertThrows(TenantAccessException.class,()->service.record(http,TaskInvocationFixture.id(1),TaskInvocationFixture.id(2),TaskInvocationFixture.id(3),result,raw,(a,b,c,d)->null));
        verifyNoInteractions(ledger,transport);verify(registrations,never()).register(any(),any(),any());
    }
}
