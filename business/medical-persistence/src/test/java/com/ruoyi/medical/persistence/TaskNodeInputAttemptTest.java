package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TaskNodeInputAttemptTest {
    @AfterEach void clear(){TransactionSynchronizationManager.clear();}
    private TaskNodeInputLedger.Attempt attempt(boolean prior)throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        var constructor=TaskNodeInputLedger.Attempt.class.getDeclaredConstructors()[0];constructor.setAccessible(true);
        return (TaskNodeInputLedger.Attempt)constructor.newInstance(null,new TaskNodeInputLedger.Row(Map.of(),JsonMapper.builder().build().createObjectNode()),"attempt",prior);
    }
    private void finish(boolean commit){
        for(var synchronization:TransactionSynchronizationManager.getSynchronizations()){
            if(commit)synchronization.afterCommit();
            synchronization.afterCompletion(commit?TransactionSynchronization.STATUS_COMMITTED:TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        TransactionSynchronizationManager.clear();
    }
    @Test void initialTransferRequiresCommitAndIsOneUse()throws Exception {
        var attempt=attempt(false);
        assertThrows(TenantAccessException.class,attempt::claimTransferMode);
        finish(true);
        assertEquals(RuntimeObjectAttempt.TransferMode.INITIAL_WRITE,attempt.claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,attempt.claimTransferMode());
    }
    @Test void rollbackNeverPermitsTransfer()throws Exception {
        var attempt=attempt(false);finish(false);
        assertThrows(TenantAccessException.class,attempt::claimTransferMode);
    }
    @Test void recoveredAttemptNeverWritesEvenAfterCommit()throws Exception {
        var attempt=attempt(true);finish(true);
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,attempt.claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,attempt.claimTransferMode());
    }
    @Test void committedAttemptCannotCrossThreadOrTransferInsideTransaction()throws Exception {
        var attempt=attempt(false);finish(true);var failure=new AtomicReference<Throwable>();
        var thread=new Thread(()->{try{attempt.claimTransferMode();}catch(Throwable e){failure.set(e);}});thread.start();thread.join();
        assertInstanceOf(TenantAccessException.class,failure.get());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThrows(TenantAccessException.class,attempt::claimTransferMode);
        TransactionSynchronizationManager.clear();assertEquals(RuntimeObjectAttempt.TransferMode.INITIAL_WRITE,attempt.claimTransferMode());
    }
    @Test void capabilityConstructorsArePrivateAndNullReadbackIsRejected(){
        for(var type:new Class<?>[]{TaskNodeInputLedger.Attempt.class,TaskNodeInputAuthority.Prepared.class,TaskNodeInputAuthority.Verified.class,TaskNodeInputRegistration.Registered.class})
            for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertThrows(TenantAccessException.class,()->TaskNodeInputRegistration.requireTransfer(null,null));
    }
}
