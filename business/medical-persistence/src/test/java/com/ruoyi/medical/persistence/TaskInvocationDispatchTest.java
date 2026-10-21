package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.function.BiFunction;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real TASK preparation/orchestration and shared076 CAS/permit. Explicit provider doubles,
 * authenticated authority/SQL fixtures only; no live provider, database or isolation claim. */
class TaskInvocationDispatchTest {
    TaskInvocationFixture f;TaskInvocationService service;FormalInvocationDispatch dispatch;JdbcTemplate db;
    FormalWorkerAccess workers;HttpServletRequest http;ServiceCertificateIdentity services;TenantScope scope;
    String manifest,status="RESERVED",generation="0",lease="1",invocation=TaskInvocationFixture.id(3);boolean attempted;
    @BeforeEach void setup()throws Exception {
        f=new TaskInvocationFixture();scope=TenantScope.open(new TenantScope.Identity(f.tenant,f.actor,BigInteger.ZERO,BigInteger.ONE,BigInteger.ONE));
        db=mock(JdbcTemplate.class);workers=mock(FormalWorkerAccess.class);http=mock(HttpServletRequest.class);services=mock(ServiceCertificateIdentity.class);
        var actual=mock(ServiceCertificateIdentity.Verified.class);when(services.require(http)).thenReturn(actual);when(actual.serviceUri()).thenReturn("spiffe://test/gateway");when(actual.certificateSha256()).thenReturn(f.hash);when(actual.trustRevision()).thenReturn(TaskInvocationFixture.id(22));
        when(workers.executeAction(eq(http),eq("token"),any(),any())).thenAnswer(call->{
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try{return ((BiFunction<EnterpriseAccess.Admission,RunTokenAuthority.Admission,?>)call.getArgument(3)).apply(f.admission,f.worker);}
            finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
        });
        dispatch=new FormalInvocationDispatch(db,services,f.schema,60);
        service=new TaskInvocationService(mock(DataSource.class),workers,mock(RunTokenAuthority.class),f.contexts,f.records,
                mock(FormalInvocationReservations.class),dispatch,mock(FormalInvocationReceiptObjects.class),mock(FormalInvocationSettlement.class),
                mock(FormalInvocationObjectRegistration.class),mock(FormalProviderCaptureEvidence.class),f.schema,mock(RunTokenAuthoritySchema.class));
        when(db.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.startsWith("SELECT status")){var type=Class.forName(FormalInvocationDispatch.class.getName()+"$Row");var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);return List.of(constructor.newInstance(status,generation,lease,TaskInvocationFixture.id(52),TaskInvocationFixture.id(53),manifest,manifest==null?null:f.schema.hash(f.schema.parse(manifest))));}
            if(sql.contains("FROM formal_invocation_attempt"))return attempted?List.of(TaskInvocationFixture.id(17)):List.of();
            if(sql.startsWith("SELECT lease_expires_at"))return List.of(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
            if(sql.startsWith("SELECT actual_send_manifest"))return List.of(manifest);
            throw new AssertionError(sql);
        });
        when(db.query(eq("SELECT UTC_TIMESTAMP(6)"),any(RowMapper.class))).thenReturn(List.of(LocalDateTime.now(ZoneOffset.UTC)));
        when(db.update(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);
            if(sql.startsWith("UPDATE invocation")){manifest=call.getArgument(1);status="DISPATCHING";generation="1";}
            if(sql.startsWith("INSERT INTO formal_invocation_attempt"))attempted=true;return 1;
        });
        clearInvocations(db); // constructor timeout configuration is not an attempted SQL read/write
    }
    @AfterEach void close(){scope.close();TransactionSynchronizationManager.clear();}
    static final class ProviderDouble {
        int sends;JsonNode received;
        void send(FormalInvocationDispatch.Permit permit,boolean loseReply)throws Exception {
            received=permit.claimProviderBody();sends++;
            if(loseReply)throw new IOException("Explicit fixture: provider accepted, response delivery unknown");
        }
    }
    @Test void genuineTaskPreparationUsesExistingSingleUseDispatch()throws Exception {
        var prepared=service.prepare(http,"token",f.action,f.contextId);
        assertEquals(f.connection,prepared.binding().verification());assertTrue(prepared.binding().task());
        var permit=service.beginDispatch(prepared,invocation).orElseThrow();var provider=new ProviderDouble();provider.send(permit,false);
        assertEquals(f.body,provider.received);assertEquals(1,provider.sends);
        assertThrows(TenantAccessException.class,()->provider.send(permit,false));assertEquals(1,provider.sends);
        assertTrue(service.beginDispatch(prepared,invocation).isEmpty());
        verify(db,times(1)).update(startsWith("INSERT INTO formal_invocation_attempt"),any(Object[].class));
    }
    @Test void unknownProviderDeliveryNeverAuthorizesAnotherSend()throws Exception {
        var prepared=service.prepare(http,"token",f.action,f.contextId);var permit=service.beginDispatch(prepared,invocation).orElseThrow();var provider=new ProviderDouble();
        assertThrows(IOException.class,()->provider.send(permit,true));assertTrue(service.beginDispatch(prepared,invocation).isEmpty());
        assertThrows(TenantAccessException.class,()->provider.send(permit,false));assertEquals(1,provider.sends);
    }
    @Test void hostControlProofCannotBeUsedAsModelAction()throws Exception {
        var a=f.action;var host=new RunTokenAuthority.ActionRequest(a.runId(),a.stepId(),a.actionId(),a.generation(),a.inputSha256(),a.argumentsSha256(),"HOST_CONTROL",a.toolVersion(),a.scope(),"task.run",a.purpose());
        assertThrows(TenantAccessException.class,()->service.prepare(http,"token",host,f.contextId));verifyNoInteractions(workers);verifyNoInteractions(db);
    }
    @Test void currentModelGrantIntersectionCannotBeSkipped()throws Exception {
        when(f.worker.delegatedPermissions()).thenReturn(List.of());assertThrows(TenantAccessException.class,()->service.prepare(http,"token",f.action,f.contextId));
        verifyNoInteractions(db);
    }
    @Test void wrongLeaseOrInputFailsBeforeSend()throws Exception {
        when(f.worker.leaseGeneration()).thenReturn(BigInteger.TWO);assertThrows(TenantAccessException.class,()->service.prepare(http,"token",f.action,f.contextId));verifyNoInteractions(db);
    }
    @Test void sourceOrModelRevocationBetweenPreparationAndActualSendIsRechecked()throws Exception {
        var prepared=service.prepare(http,"token",f.action,f.contextId);var permit=service.beginDispatch(prepared,invocation).orElseThrow();var provider=new ProviderDouble();
        when(f.contexts.requireCurrent(f.admission,f.contextId)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->provider.send(permit,false));assertEquals(0,provider.sends);
    }
    @Test void changedExactProviderBodyIsNotReplacedDuringDispatch()throws Exception {
        var prepared=service.prepare(http,"token",f.action,f.contextId);when(f.context.providerBody()).thenReturn(f.schema.parse("{\"model\":\"other\"}"));
        assertThrows(TenantAccessException.class,()->service.beginDispatch(prepared,invocation));verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void rollbackOfMutableStatusCannotEraseImmutableAttempt()throws Exception {
        var prepared=service.prepare(http,"token",f.action,f.contextId);service.beginDispatch(prepared,invocation).orElseThrow();status="RESERVED";generation="0";
        assertThrows(TenantAccessException.class,()->service.beginDispatch(prepared,invocation));
        verify(db,times(1)).update(startsWith("INSERT INTO formal_invocation_attempt"),any(Object[].class));
    }
}
