package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Fault-injected service boundary tests, not database/process-concurrency evidence. */
class SimulationInvocationServiceTest {
    final SimulationInvocationSchema schema=new SimulationInvocationSchema();
    final EnterpriseAccess access=mock(EnterpriseAccess.class);
    final SimulationInvocationStore store=mock(SimulationInvocationStore.class);
    final SimulationInvocationAuthority authority=mock(SimulationInvocationAuthority.class);
    final SimulationInvocationSources sources=mock(SimulationInvocationSources.class);
    final ServiceCertificateIdentity services=mock(ServiceCertificateIdentity.class);
    final ServiceCertificateIdentity.Verified service=mock(ServiceCertificateIdentity.Verified.class);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
    final HttpServletRequest http=mock(HttpServletRequest.class);
    final Map<String,Object> row=new HashMap<>();
    final Map<String,Object> enterprise=new HashMap<>(),parent=new HashMap<>();
    final AtomicBoolean transactionReturned=new AtomicBoolean();
    final SimulationInvocationService target=new SimulationInvocationService(access,store,schema,authority,sources,services);
    JsonNode request; byte[] bytes;
    SimulationInvocationServiceTest()throws Exception{}
    @BeforeEach void setup()throws Exception {
        bytes=SimulationInvocationSchemaTest.resource("admission"); request=schema.request(bytes);
        var id=request.required("binding").required("identity"); var proof=request.required("authority_proof");
        when(services.require(http)).thenReturn(service); when(service.serviceUri()).thenReturn("spiffe://medical/simulation");
        when(service.certificateSha256()).thenReturn("a".repeat(64)); when(service.trustRevision()).thenReturn("1");
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(proof.path("actor_id").asText(),"simulation","simulation","USER"));
        when(admission.actorId()).thenReturn(proof.path("actor_id").asText());
        when(sources.prepare(subject,request)).thenReturn(mock(SimulationInvocationSources.VerifiedSources.class));
        var action=new HashMap<String,Object>(); action.put("state","BOUND");
        when(authority.require(eq(http),eq(admission),any(),eq(service))).thenReturn(new SimulationInvocationAuthority.Checked("config","INTERNAL",action));
        when(access.execute(eq(subject),any())).thenAnswer(call->{ Object result=((Function<EnterpriseAccess.Admission,Object>)call.getArgument(1)).apply(admission); transactionReturned.set(true); return result; });
        for(String field:java.util.List.of("invocation_id","project_id","run_id","step_id","action_id","context_manifest_id")) row.put(field,id.path(field).asText());
        row.put("config_revision",id.path("model_config_revision").asText()); row.put("input_sha256",proof.path("input_sha256").asText());
        row.put("status","RESERVED"); row.put("dispatch_generation",0); row.put("revision",1); row.put("execution_mode","SIMULATION"); row.put("classification","INTERNAL");
        when(store.invocation(anyString())).thenAnswer(call->new HashMap<>(row));
        var provenance=new HashMap<String,Object>(); for(String field:java.util.List.of("binding","exact_input","request_parameters","simulation_case","settings")) provenance.put(field,schema.canonical(request.required(field)));
        provenance.put("authority_id",proof.path("authority_id").asText()); provenance.put("binding_sha256",schema.hash(request.required("binding"))); provenance.put("schema_manifest_sha256",schema.manifest());
        when(store.provenance(anyString())).thenReturn(provenance);
        when(store.authority(anyString())).thenReturn(Map.of("proof",schema.canonical(proof),"proof_sha256",schema.hash(proof)));
        long units=request.path("binding").path("exact_input").path("units").longValue()+4000;
        enterprise.put("reserved_units",units); enterprise.put("reserved_calls",1); parent.putAll(enterprise);
        when(store.account(proof.path("enterprise_account_id").asText())).thenReturn(enterprise); when(store.account(proof.path("parent_account_id").asText())).thenReturn(parent);
        when(store.dispatch(any(),any(),anyString(),anyString())).thenAnswer(call->{ assertFalse(transactionReturned.get());row.put("status","DISPATCHING");row.put("dispatch_generation",1);row.put("revision",2);return true; });
    }
    @Test void workerRequiresRestrictedAuthorityBeforeSourceReadsWithoutSessionFallback() throws Exception {
        var workers=mock(SimulationWorkerAccess.class);
        when(workers.execute(eq(http),anyString(),anyString(),any())).thenAnswer(call->((Function<EnterpriseAccess.Admission,Object>)call.getArgument(3)).apply(admission));
        var workerTarget=new SimulationInvocationService(access,store,schema,authority,sources,services,workers);
        when(authority.require(http,admission,request,service)).thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->workerTarget.beginSend(http,bytes));
        verify(sources,never()).prepare(eq(workers),any(),anyString(),anyString(),any(),any());
        verifyNoInteractions(access);verify(store,never()).dispatch(any(),any(),anyString(),anyString());
    }
    @Test void disabledWorkerAdmissionNeverFallsBackToAUserSession() {
        assertThrows(TenantAccessException.class,()->target.reserve(http,bytes));verifyNoInteractions(access);
    }
    @Test void onlyCommitReturnCreatesOneUsePermit()throws Exception {
        var result=target.beginSend(http,subject,bytes); assertTrue(transactionReturned.get()); assertNotNull(result.permit());
        assertEquals(schema.canonical(request),result.permit().consume()); assertThrows(IllegalStateException.class,result.permit()::consume);
        verify(store).dispatch(any(),any(),anyString(),anyString());
    }
    @Test void commitFailureOrLostAcknowledgementDoesNotReturnPermit()throws Exception {
        when(access.execute(eq(subject),any())).thenAnswer(call->{ ((Function<EnterpriseAccess.Admission,Object>)call.getArgument(1)).apply(admission);throw new IllegalStateException("COMMIT_ACK_LOST"); });
        assertThrows(IllegalStateException.class,()->target.beginSend(http,subject,bytes)); assertEquals("DISPATCHING",row.get("status"));
        when(access.execute(eq(subject),any())).thenAnswer(call->((Function<EnterpriseAccess.Admission,Object>)call.getArgument(1)).apply(admission));
        assertNull(target.beginSend(http,subject,bytes).permit()); verify(store,times(1)).dispatch(any(),any(),anyString(),anyString());
    }
    @Test void existingDispatchedUnknownCancelledAndTerminalStatesNeverResend()throws Exception {
        for(String state:java.util.List.of("DISPATCHING","IN_FLIGHT","UNKNOWN","CANCEL_REQUESTED","SUCCEEDED","FAILED","CANCELLED")) {
            row.put("status",state);row.put("dispatch_generation",1); assertNull(target.beginSend(http,subject,bytes).permit());
        }
        verify(store,never()).dispatch(any(),any(),anyString(),anyString());
    }
    @Test void failedCasDoesNotCreatePermit()throws Exception {
        when(store.dispatch(any(),any(),anyString(),anyString())).thenReturn(false);
        assertNull(target.beginSend(http,subject,bytes).permit());
    }
    @Test void currentSourcesAndAuthorityRecheckedBeforeCommit()throws Exception {
        target.beginSend(http,subject,bytes); verify(authority,times(2)).require(eq(http),eq(admission),any(),eq(service));
        verify(sources,times(2)).require(eq(admission),any(),any(),any());
    }
    @Test void missingAggregateReservationFailsBeforeDispatch()throws Exception {
        enterprise.put("reserved_units",0); assertThrows(TenantAccessException.class,()->target.beginSend(http,subject,bytes));
        verify(store,never()).dispatch(any(),any(),anyString(),anyString());
    }
    @Test void enterpriseAndParentLocksPrecedeHeldVerificationAndCas()throws Exception {
        target.beginSend(http,subject,bytes);var order=inOrder(store);
        order.verify(store).account(request.path("authority_proof").path("enterprise_account_id").asText());
        order.verify(store).account(request.path("authority_proof").path("parent_account_id").asText());
        order.verify(store).requireHeld(any(),anyLong());order.verify(store).dispatch(any(),any(),anyString(),anyString());
    }
    @Test void unsentCancellationReleasesOnlyOriginalReservations()throws Exception {
        target.cancel(http,subject,bytes);verify(store).release(any(),anyLong());verify(store).observe(any(),any(),eq("CANCELLED"),anyString(),anyString());
        verify(store,never()).dispatch(any(),any(),anyString(),anyString());
    }
    @Test void unknownCancellationKeepsQuotaAndCannotProveTermination()throws Exception {
        row.put("status","UNKNOWN");row.put("dispatch_generation",1);target.cancel(http,subject,bytes);
        verify(store,never()).release(any(),anyLong());verify(store).observe(any(),any(),eq("CANCEL_REQUESTED"),anyString(),anyString());
    }
    @Test void changedRequestCannotReuseInvocation()throws Exception {
        row.put("input_sha256","f".repeat(64));assertThrows(TenantAccessException.class,()->target.beginSend(http,subject,bytes));
        verify(store,never()).dispatch(any(),any(),anyString(),anyString());
    }
    @Test void sourceFailureBeforeTransactionNeverTouchesLedger()throws Exception {
        when(sources.prepare(subject,request)).thenThrow(new IllegalStateException("READ_REVOKED"));
        assertThrows(IllegalStateException.class,()->target.beginSend(http,subject,bytes));verifyNoInteractions(access,store);
    }
}
