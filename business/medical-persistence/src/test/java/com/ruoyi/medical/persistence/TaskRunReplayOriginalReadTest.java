package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Existing replay/registration fixtures with actual original-only reader code.
 * No object-store, SQL isolation or current production-authority acceptance claim. */
class TaskRunReplayOriginalReadTest {
    TaskRunReplayRegistrationTest f;
    TaskNodeProductionAuthority.Verified running;
    @BeforeEach void setup()throws Exception{
        f=new TaskRunReplayRegistrationTest();f.setup();running=mock(TaskNodeProductionAuthority.Verified.class);var p=f.f.proof;
        when(running.admission()).thenAnswer(c->p.admission());when(running.replay()).thenAnswer(c->p.replay());
        when(running.tenantId()).thenAnswer(c->p.tenantId());when(running.projectId()).thenAnswer(c->p.projectId());when(running.runId()).thenAnswer(c->p.runId());
        when(running.taskId()).thenAnswer(c->p.taskId());when(running.actorId()).thenAnswer(c->p.actorId());when(running.principalId()).thenAnswer(c->p.principalId());
        when(running.classification()).thenAnswer(c->p.classification());when(running.outboxEventId()).thenAnswer(c->p.outboxEventId());
        when(running.command()).thenAnswer(c->p.command());when(running.authorizationsSha256()).thenReturn("f".repeat(64));
        when(f.contents.require((TaskNodeProductionBasis)running)).thenReturn(f.content);
        var stored=mock(TaskRunReplayGovernanceLedger.Stored.class);when(stored.sha256()).thenReturn(f.gh);
        when(f.governance.requireStored(eq(running),any(),eq(f.input))).thenReturn(stored);
    }
    @AfterEach void cleanup(){f.cleanup();}
    TaskRunReplayRegistration.Registered read()throws Exception{
        var attempt=f.ledger.requireOriginal(running);assertTrue(attempt.priorAttempt());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,attempt.claimTransferMode());
        return f.registration.readOriginal(running,attempt,f.transfer(attempt,f.object));
    }
    @Test void currentRunningProofReadsPinnedOriginalWithoutQueuedRegistrationGrant()throws Exception{
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.f.proof).requireUnchanged();
        int before=f.f.writes.size();var result=read();assertEquals(f.object,result.reference());assertEquals(before,f.f.writes.size());
        verify(f.db,never()).update(anyString(),any(Object[].class));verify(f.governance,never()).prepare(any(),any(),any(),anyString());
    }
    @Test void originalReaderCannotCreateMissingIntentAttemptOrRegistration()throws Exception{
        var original=f.f.attempted;f.f.attempted=null;assertThrows(TenantAccessException.class,this::read);f.f.attempted=original;
        when(f.db.query(contains("SELECT receipt_json"),any(RowMapper.class),any(Object[].class))).thenReturn(List.of());
        assertThrows(TenantAccessException.class,this::read);assertEquals(4,f.f.writes.size());verify(f.db,never()).update(anyString(),any(Object[].class));
    }
    @Test void originalReaderStillRequiresCurrentProductionAndSourceRights()throws Exception{
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(running).requireUnchanged();assertThrows(TenantAccessException.class,this::read);
        doNothing().when(running).requireUnchanged();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.content).requireUnchanged();
        assertThrows(TenantAccessException.class,this::read);verify(f.db,never()).update(anyString(),any(Object[].class));
    }
    @Test void originalCrossRunOrDifferentPhysicalVersionCannotBeSubstituted()throws Exception{
        when(running.runId()).thenReturn(TaskRunReplayLedgerTest.id(99));assertThrows(RuntimeException.class,this::read);
        when(running.runId()).thenAnswer(c->f.f.proof.runId());
        var attempt=f.ledger.requireOriginal(running);var wrong=new com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef(f.object.objectKey(),"replaced-version",f.object.sha256(),f.object.sizeBytes(),f.object.mediaType());
        assertThrows(TenantAccessException.class,()->f.registration.readOriginal(running,attempt,f.transfer(attempt,wrong)));
    }
    @Test void newTransferAttemptAndTamperedOriginalJournalAreRejected()throws Exception{
        assertFalse(f.attempt.priorAttempt());assertThrows(TenantAccessException.class,()->f.registration.readOriginal(running,f.attempt,f.transfer));
        f.f.event="{}";assertThrows(TenantAccessException.class,this::read);assertEquals(4,f.f.writes.size());
    }
    @Test void runningTypeCannotCallAnyReplayWriter()throws Exception{
        assertThrows(NoSuchMethodException.class,()->TaskRunReplayLedger.class.getMethod("prepare",TaskNodeProductionAuthority.Verified.class));
        assertThrows(NoSuchMethodException.class,()->TaskRunReplayRegistration.class.getMethod("register",TaskNodeProductionAuthority.Verified.class,TaskRunReplayLedger.Attempt.class,RuntimeObjectTransport.VerifiedTransfer.class));
        for(var constructor:TaskRunAcceptedStart.Transition.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    }
}
