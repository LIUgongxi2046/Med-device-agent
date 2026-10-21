package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.workflow.WorkflowProtocol.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** Tests actual private proof lifetime after admission. Reflection is test-only;
 * no fixture claims to prove the mocked publication/SQL/mTLS boundaries. */
class TaskRunLifecycleProofTest {
    TenantScope scope;Object key=new Object();JdbcTemplate db;EnterpriseAccess.Admission admission;FormalRunControlAccess.Proof control;TaskRunLifecycleAuthority.Proof proof;Map<String,Object> row;
    @BeforeEach void setup()throws Exception{
        String tenant=id(),actor=id();scope=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE));TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.bindResource(key,new Object());TransactionSynchronizationManager.initSynchronization();
        var ref=new Reference("1.0.0",tenant,id(),id(),ExecutionKind.TASK,id(),"a".repeat(64),id(),9999999999999L,10,10,1,1000,1000,1000,"control","professional");db=mock(JdbcTemplate.class);admission=mock(EnterpriseAccess.Admission.class);control=mock(FormalRunControlAccess.Proof.class);when(admission.actorId()).thenReturn(actor);when(admission.controlProof()).thenReturn(control);row=new HashMap<>();row.put("status","RUNNING");row.put("run_id",ref.runId());row.put("revision",1);when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(i->List.of(new HashMap<>(row)));
        var owner=new TaskRunLifecycleAuthority(db,null,null,null,null,null,null,null,null,new PermissionSchema());var ctor=TaskRunLifecycleAuthority.Proof.class.getDeclaredConstructors()[0];ctor.setAccessible(true);
        proof=(TaskRunLifecycleAuthority.Proof)ctor.newInstance(owner,admission,ref,TaskRunLifecycleAuthority.Operation.FAILURE,mock(AgentPublicationAuthority.VerifiedPublication.class),mock(AgentExecutionPolicyAuthority.VerifiedPolicy.class),mock(RuntimeRetentionAuthority.Verified.class),mock(TaskRunLaunchRecoveryAuthority.Verified.class),mock(TaskRunGraphService.Verified.class),new HashMap<>(row));
    }
    @AfterEach void close(){if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization();if(TransactionSynchronizationManager.hasResource(key))TransactionSynchronizationManager.unbindResource(key);TransactionSynchronizationManager.setActualTransactionActive(false);if(scope!=null)scope.close();}
    @Test void proofAllowsOnlyItsOriginalOperationAndCannotBorrowCancellation()throws Exception{proof.require(TaskRunLifecycleAuthority.Operation.FAILURE);assertThrows(TenantAccessException.class,()->proof.require(TaskRunLifecycleAuthority.Operation.REQUEST_CANCEL));assertThrows(TenantAccessException.class,()->proof.require(TaskRunLifecycleAuthority.Operation.FINISH_SUCCEEDED));}
    @Test void actualRevocationBeforeWriteFails(){doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(control).requireActive();assertThrows(TenantAccessException.class,()->proof.require(TaskRunLifecycleAuthority.Operation.FAILURE));verify(db,never()).update(anyString(),any(Object[].class));}
    @Test void unannouncedRunRevisionOrStatusChangeCannotBeRebased(){row.put("revision",2);row.put("status","CANCEL_REQUESTED");assertThrows(TenantAccessException.class,()->proof.require(TaskRunLifecycleAuthority.Operation.FAILURE));}
    @Test void replacedResourceAndRollbackInvalidatePrivateProof(){TransactionSynchronizationManager.unbindResource(key);TransactionSynchronizationManager.bindResource(key,new Object());assertThrows(TenantAccessException.class,()->proof.require(TaskRunLifecycleAuthority.Operation.FAILURE));for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);assertThrows(TenantAccessException.class,()->proof.require(TaskRunLifecycleAuthority.Operation.FAILURE));}
    @Test void commitAndCrossThreadCannotEscapeProof()throws Exception{var failure=new AtomicReference<Throwable>();var worker=new Thread(()->{try{proof.require(TaskRunLifecycleAuthority.Operation.FAILURE);}catch(Throwable e){failure.set(e);}});worker.start();worker.join();assertInstanceOf(TenantAccessException.class,failure.get());for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);assertThrows(TenantAccessException.class,()->proof.require(TaskRunLifecycleAuthority.Operation.FAILURE));}
    static String id(){return UUID.randomUUID().toString();}
}
