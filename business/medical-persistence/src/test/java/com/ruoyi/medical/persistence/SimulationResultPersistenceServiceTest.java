package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Boundary doubles: no database, object server, provider, or physical concurrency is claimed. */
class SimulationResultPersistenceServiceTest {
    final SimulationInvocationSchema schema=new SimulationInvocationSchema();
    final SimulationResultSchema results=new SimulationResultSchema(schema);
    final SimulationInvocationService ledger=mock(SimulationInvocationService.class);
    final SimulationResultStore store=mock(SimulationResultStore.class);
    final SimulationResultTransport transport=mock(SimulationResultTransport.class);
    final HttpServletRequest http=mock(HttpServletRequest.class);
    final AtomicReference<SimulationResultStore.Work> work=new AtomicReference<>();
    final AtomicBoolean transaction=new AtomicBoolean();
    final SimulationResultPersistenceService target=new SimulationResultPersistenceService(ledger,store,results,transport);
    String tenant,invocation;com.fasterxml.jackson.databind.JsonNode request,payload;
    final SimulationInvocationService.View terminal=new SimulationInvocationService.View("id","SUCCEEDED",1,"3");
    SimulationResultPersistenceServiceTest()throws Exception{}
    @BeforeEach void setup()throws Exception {
        request=schema.request(SimulationInvocationSchemaTest.resource("admission"));
        payload=schema.receipt(request,SimulationInvocationSchemaTest.resource("result"),SimulationInvocationSchemaTest.resource("provenance"));
        tenant=request.path("binding").path("identity").path("tenant_id").textValue();invocation=request.path("binding").path("identity").path("invocation_id").textValue();
        var row=new HashMap<String,Object>();row.put("fact_id",UUID.randomUUID().toString());row.put("payload_sha256",schema.hash(payload));row.put("status","PREPARED");row.put("invocation_id",invocation);
        work.set(new SimulationResultStore.Work(request,payload,row,results.create(request,payload),request.path("authority_proof").path("actor_id").textValue(),"INTERNAL"));
        when(ledger.originalFactTransaction(eq(http),eq(tenant),eq(invocation),any())).thenAnswer(call->{
            assertFalse(transaction.getAndSet(true));try{return ((SimulationInvocationService.FactOperation<?>)call.getArgument(3)).apply(request,Map.of());}finally{transaction.set(false);}
        });
        when(store.prepare(any(),any())).thenAnswer(call->{assertTrue(transaction.get());return work.get();});
        when(store.save(any(),any(),anyString())).thenAnswer(call->{
            assertTrue(transaction.get());var prior=(SimulationResultStore.Work)call.getArgument(0);var row2=new HashMap<>(prior.operation());row2.put("status",call.getArgument(2));
            var next=new SimulationResultStore.Work(prior.request(),prior.payload(),row2,call.getArgument(1),prior.actor(),prior.classification());work.set(next);return next;
        });
        when(transport.write(anyString(),anyString(),any(),any(),anyBoolean(),any())).thenAnswer(call->{
            assertFalse(transaction.get());var intent=(S3ObjectWriter.WriteIntent)call.getArgument(2);var guard=(S3ObjectReader.Admission)call.getArgument(5);guard.requireCurrent();
            assertTrue(work.get().command().items().stream().filter(i->i.intent().equals(intent)).findFirst().orElseThrow().writing());
            return new ObjectRef(intent.objectKey(),"version-1",intent.sha256(),intent.sizeBytes(),intent.mediaType());
        });
        when(store.finish(any(),any())).thenAnswer(call->{assertTrue(transaction.get());assertEquals("VERIFIED",work.get().operation().get("status"));return terminal;});
    }
    @Test void durableIntentsCommitBeforeTwoWritesAndFinalization()throws Exception {
        assertEquals(terminal,target.persist(http,tenant,invocation));
        verify(transport,times(2)).write(eq(tenant),anyString(),any(),any(),eq(false),any());verify(store).finish(any(),any());
        assertTrue(work.get().command().items().stream().allMatch(i->i.reference()!=null));
    }
    @Test void uncertainFirstWriteNeverFinalizesAndRetryOnlyRecovers()throws Exception {
        doThrow(new IllegalStateException("object write uncertain")).when(transport).write(anyString(),anyString(),any(),any(),eq(false),any());
        assertThrows(IllegalStateException.class,()->target.persist(http,tenant,invocation));verify(store,never()).finish(any(),any());
        assertTrue(work.get().command().items().get(0).writing());assertNull(work.get().command().items().get(0).reference());
        // Recovery still failing cannot release held quota or create artifacts.
        doThrow(new IllegalStateException("not yet observable")).when(transport).write(anyString(),anyString(),any(),any(),eq(true),any());
        assertThrows(IllegalStateException.class,()->target.persist(http,tenant,invocation));
        verify(transport,times(1)).write(anyString(),anyString(),any(),any(),eq(false),any());verify(transport,times(1)).write(anyString(),anyString(),any(),any(),eq(true),any());verify(store,never()).finish(any(),any());
    }
    @Test void secondObjectFailureLeavesVerifiedFirstButNoSettlement()throws Exception {
        var second=work.get().command().items().get(1).intent();
        doThrow(new IllegalStateException("sidecar unavailable")).when(transport).write(anyString(),anyString(),eq(second),any(),anyBoolean(),any());
        assertThrows(IllegalStateException.class,()->target.persist(http,tenant,invocation));assertNotNull(work.get().command().items().get(0).reference());verify(store,never()).finish(any(),any());
    }
    @Test void fabricatedVersionOrMismatchingHashNeverRegisters()throws Exception {
        doAnswer(call->{var i=(S3ObjectWriter.WriteIntent)call.getArgument(2);return new ObjectRef(i.objectKey(),"null",i.sha256(),i.sizeBytes(),i.mediaType());}).when(transport).write(anyString(),anyString(),any(),any(),anyBoolean(),any());
        assertThrows(TenantAccessException.class,()->target.persist(http,tenant,invocation));verify(store,never()).finish(any(),any());
    }
    @Test void revocationDuringObjectGuardStopsWithoutFinalization()throws Exception {
        doAnswer(call->{
            doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(store).prepare(any(),any());
            ((S3ObjectReader.Admission)call.getArgument(5)).requireCurrent();return null;
        }).when(transport).write(anyString(),anyString(),any(),any(),anyBoolean(),any());
        assertThrows(TenantAccessException.class,()->target.persist(http,tenant,invocation));verify(store,never()).finish(any(),any());
    }
    @Test void failureInsideAtomicFinalizationIsNeverReportedAsComplete()throws Exception {
        doThrow(new IllegalStateException("second account CAS rejected; transaction must roll back")).when(store).finish(any(),any());
        assertThrows(IllegalStateException.class,()->target.persist(http,tenant,invocation));
        verify(store,times(1)).finish(any(),any());assertEquals("VERIFIED",work.get().operation().get("status"));
    }
    @Test void byteQuotaSettlementPreservesUnknownFixtureUsage()throws Exception {
        var a=results.accounting(request,payload);assertEquals("UNKNOWN",a.usageOrigin());assertTrue(a.fixtureUsage().isNull());assertEquals("SUCCEEDED",a.status());
        assertEquals(results.bytes(payload,"result").length,a.output());assertEquals(a.input()+a.output(),a.charged());
        assertNotEquals(0,a.charged());
    }
    @Test void durableCommandRoundTripAndOriginalScopeValidation()throws Exception {
        var command=work.get().command();assertEquals(command,results.decode(results.encode(command)));results.require(command,request,payload);
        var wrong=request.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)wrong.path("binding").path("identity")).put("invocation_id",UUID.randomUUID().toString());
        assertThrows(TenantAccessException.class,()->results.require(command,wrong,payload));
    }
}
