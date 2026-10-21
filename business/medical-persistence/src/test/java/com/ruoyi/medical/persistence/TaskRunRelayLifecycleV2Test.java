package com.ruoyi.medical.persistence;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No scheduler, HTTP server or Temporal runtime is launched by these tests. */
class TaskRunRelayLifecycleV2Test {
    static final String TENANT=TaskRunOutboxRelayV2Test.TENANT,EVENT=TaskRunOutboxRelayV2Test.EVENT;
    @Test void rejectsUnboundedPollingAndShutdown(){
        var client=mock(TaskRunRelayHttpClientV2.class);
        assertThrows(IllegalArgumentException.class,()->new TaskRunRelayLifecycleV2(client,TENANT,Duration.ZERO,Duration.ofSeconds(1),3));
        assertThrows(IllegalArgumentException.class,()->new TaskRunRelayLifecycleV2(client,TENANT,Duration.ofSeconds(1),Duration.ofMinutes(3),3));
        assertThrows(IllegalArgumentException.class,()->new TaskRunRelayLifecycleV2(client,TENANT,Duration.ofSeconds(1),Duration.ofSeconds(1),101));
        verifyNoInteractions(client);
    }
    @Test void stopBeforeStartDoesNotCreateThreadsOrPermitRestart(){
        var client=mock(TaskRunRelayHttpClientV2.class);var lifecycle=new TaskRunRelayLifecycleV2(client,TENANT,Duration.ofSeconds(1),Duration.ofSeconds(1),3);
        var callback=mock(Runnable.class);lifecycle.stop(callback);assertFalse(lifecycle.isRunning());assertThrows(IllegalStateException.class,lifecycle::start);verify(callback).run();verifyNoInteractions(client);
    }
    @Test void httpClientRequiresExplicitHttpsAndBounds(){
        var tls=mock(com.ruoyi.medical.identity.ServiceClientTlsStore.ContextSource.class);
        for(String origin:List.of("http://example.test","https://user@example.test","https://example.test/other","https://example.test/?query=1"))assertThrows(IllegalArgumentException.class,()->new TaskRunRelayHttpClientV2(java.net.URI.create(origin),tls,Duration.ofSeconds(1),4096));
        assertThrows(IllegalArgumentException.class,()->new TaskRunRelayHttpClientV2(java.net.URI.create("https://example.test"),tls,Duration.ZERO,4096));verifyNoInteractions(tls);
    }
    @Test void pendingAndAmbiguousDeliveryRetainExactEvent()throws Exception{
        var client=mock(TaskRunRelayHttpClientV2.class);var lifecycle=new TaskRunRelayLifecycleV2(client,TENANT,Duration.ofSeconds(1),Duration.ofSeconds(1),3);
        var executor=mock(java.util.concurrent.ScheduledExecutorService.class);set(lifecycle,"executor",executor);set(lifecycle,"running",true);
        when(client.candidates(TENANT,null)).thenReturn(List.of(EVENT));
        when(client.deliver(TENANT,EVENT)).thenThrow(new java.io.IOException("ambiguous")).thenReturn(false,true);
        tick(lifecycle);tick(lifecycle);tick(lifecycle);
        verify(client,times(1)).candidates(TENANT,null);verify(client,times(3)).deliver(TENANT,EVENT);
        when(client.candidates(TENANT,EVENT)).thenReturn(List.of());tick(lifecycle);verify(client).candidates(TENANT,EVENT);
        verifyNoInteractions(executor);
    }
    @Test void boundedFailuresStopInsteadOfReplacingOperation()throws Exception{
        var client=mock(TaskRunRelayHttpClientV2.class);var lifecycle=new TaskRunRelayLifecycleV2(client,TENANT,Duration.ofSeconds(1),Duration.ofSeconds(1),2);
        var executor=mock(java.util.concurrent.ScheduledExecutorService.class);set(lifecycle,"executor",executor);set(lifecycle,"running",true);
        when(client.candidates(TENANT,null)).thenReturn(List.of(EVENT));when(client.deliver(TENANT,EVENT)).thenThrow(new java.io.IOException("unavailable"));
        tick(lifecycle);tick(lifecycle);tick(lifecycle);assertFalse(lifecycle.isRunning());verify(executor).shutdown();verify(client,times(2)).deliver(TENANT,EVENT);
        assertThrows(IllegalStateException.class,lifecycle::start);
    }
    private static void set(Object target,String name,Object value)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    private static void tick(TaskRunRelayLifecycleV2 target)throws Exception{var method=TaskRunRelayLifecycleV2.class.getDeclaredMethod("tick");method.setAccessible(true);method.invoke(target);}
    @Test void strictEmptyPageAndPendingResponseNeverAdvanceAReceipt()throws Exception{
        var json=com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        var page=json.createObjectNode().put("schema_version","2.0.0").putNull("next_after_event_id");page.putArray("event_ids");
        assertEquals(List.of(),TaskRunRelayHttpClientV2.readCandidates(page,null));
        page.remove("next_after_event_id");assertThrows(RuntimeException.class,()->TaskRunRelayHttpClientV2.readCandidates(page,null));
        var pending=json.createObjectNode().put("schema_version","2.0.0").put("tenant_id",TENANT).put("event_id",EVENT).put("state","SIGNALLED_NOT_CONSUMED").putNull("receipt");
        assertFalse(TaskRunRelayHttpClientV2.readDelivered(pending,TENANT,EVENT));
        pending.put("event_id",TENANT);assertThrows(RuntimeException.class,()->TaskRunRelayHttpClientV2.readDelivered(pending,TENANT,EVENT));
    }
}
