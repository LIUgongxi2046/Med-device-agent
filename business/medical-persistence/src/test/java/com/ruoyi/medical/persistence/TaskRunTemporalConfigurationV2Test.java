package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ServiceClientTlsStore;
import com.ruoyi.medical.workflow.WaitingWorkflowDeploymentV2;
import io.grpc.*;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** No network, worker, service or real credential material is opened. */
class TaskRunTemporalConfigurationV2Test {
    final ServiceClientTlsStore.ContextSource tls=mock(ServiceClientTlsStore.ContextSource.class);
    TaskRunTemporalChannelV2 channel(){return new TaskRunTemporalChannelV2(URI.create("https://temporal.example.test:7233"),tls,Duration.ofSeconds(1),Duration.ofSeconds(70),1048576,4);}
    @Test void explicitTlsTargetAndBoundsAreMandatory(){
        for(String origin:List.of("http://temporal.example.test:7233","https://temporal.example.test","https://user@temporal.example.test:7233","https://temporal.example.test:7233/path"))assertThrows(IllegalArgumentException.class,()->new TaskRunTemporalChannelV2(URI.create(origin),tls,Duration.ofSeconds(1),Duration.ofSeconds(70),1048576,4));
        assertThrows(IllegalArgumentException.class,()->new TaskRunTemporalChannelV2(URI.create("https://temporal.example.test:7233"),tls,Duration.ZERO,Duration.ofSeconds(70),1048576,4));verifyNoInteractions(tls);
    }
    @Test void everyRpcRechecksProtectedTlsAndFailsClosedWithoutIt()throws Exception{
        when(tls.create()).thenThrow(new java.security.GeneralSecurityException("not configured"));var channel=channel();
        for(int i=0;i<2;i++){
            var listener=mock(ClientCall.Listener.class);channel.newCall(mock(MethodDescriptor.class),CallOptions.DEFAULT).start(listener,new Metadata());
            verify(listener).onClose(argThat(status->((Status)status).getCode()==Status.Code.UNAVAILABLE),any());
        }
        verify(tls,times(2)).create();channel.close();assertTrue(channel.isShutdown());assertTrue(channel.isTerminated());
    }
    @Test void shutdownRejectsBeforeReadingAnyTlsMaterial(){
        var channel=channel();channel.shutdownNow();var listener=mock(ClientCall.Listener.class);
        channel.newCall(mock(MethodDescriptor.class),CallOptions.DEFAULT).start(listener,new Metadata());verifyNoInteractions(tls);assertTrue(channel.isTerminated());
    }
    @Test void namespaceMismatchCannotCreateClient(){
        var pinned=mock(TaskRunWorkflowV2Configuration.Pinned.class);var protocol=mock(WaitingWorkflowDeploymentV2.class);when(pinned.protocol()).thenReturn(protocol);when(protocol.namespace()).thenReturn("explicit-v2");
        var stubs=mock(WorkflowServiceStubs.class);assertThrows(IllegalArgumentException.class,()->new TaskRunTemporalConfigurationV2().client(stubs,pinned,"arbitrary-v1"));verifyNoInteractions(stubs);
    }
}
