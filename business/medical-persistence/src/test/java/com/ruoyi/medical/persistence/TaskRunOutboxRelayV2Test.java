package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import io.temporal.client.WorkflowClient;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TaskRunOutboxRelayV2Test {
    static final String TENANT="00000000-0000-4000-8000-000000000001",RUN="00000000-0000-4000-8000-000000000002",COMMAND="00000000-0000-4000-8000-000000000003",EVENT="00000000-0000-4000-8000-000000000004";
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final ServiceCertificateIdentity identity=mock(ServiceCertificateIdentity.class);
    final TaskRunWorkflowConsumerV2 consumer=mock(TaskRunWorkflowConsumerV2.class);
    final FormalRunRecoveryAccess recovery=mock(FormalRunRecoveryAccess.class);
    final TaskRunLaunchRecoveryAuthority authority=mock(TaskRunLaunchRecoveryAuthority.class);
    final WorkflowClient client=mock(WorkflowClient.class);
    TaskRunOutboxRelayV2 relay(){return new TaskRunOutboxRelayV2(db,identity,consumer,recovery,authority,mock(TaskRunWorkflowLedger.class),mock(TaskRunWorkflowProtocolBinding.class),mock(TaskRunLifecycleService.class),client,10);}
    @Test void missingOutboxEventNeverStartsOrSignals()throws Exception{
        when(db.queryForList(anyString(),eq(TENANT),eq(EVENT))).thenReturn(List.of());
        assertThrows(TenantAccessException.class,()->relay().deliver(new MockHttpServletRequest(),TENANT,EVENT));verifyNoInteractions(consumer,client);
    }
    @Test void unrecognizedCommandCannotBecomeResume()throws Exception{
        when(db.queryForList(anyString(),eq(TENANT),eq(EVENT))).thenReturn(List.of(Map.of("aggregate_id",RUN,"event_type","run.resume.requested","payload",payload("RESOLVE_WAITING"))));
        assertThrows(TenantAccessException.class,()->relay().deliver(new MockHttpServletRequest(),TENANT,EVENT));verifyNoInteractions(consumer,client);
    }
    @Test void startRelaysOnlyExactOriginalEventAndCommand()throws Exception{
        var http=new MockHttpServletRequest();var a=mock(EnterpriseAccess.Admission.class);var p=mock(TaskRunLaunchRecoveryAuthority.Verified.class);
        when(db.queryForList(anyString(),eq(TENANT),eq(EVENT))).thenReturn(List.of(Map.of("aggregate_id",RUN,"event_type","run.start.requested","payload",payload("START_RUN"))));
        doAnswer(invocation->((Function<EnterpriseAccess.Admission,Object>)invocation.getArgument(3)).apply(a)).when(recovery).execute(eq(http),eq(TENANT),eq(RUN),any());
        when(authority.require(a,http,RUN,COMMAND)).thenReturn(p);when(p.outboxEventId()).thenReturn(EVENT);
        var receipt=mock(TaskRunWorkflowLedger.Receipt.class);var content=JsonMapper.builder().build().createObjectNode().put("accepted",true);when(receipt.content()).thenReturn(content);when(consumer.consume(http,TENANT,RUN,COMMAND)).thenReturn(receipt);
        assertEquals(content,relay().deliver(http,TENANT,EVENT).required("receipt"));verify(consumer).consume(http,TENANT,RUN,COMMAND);verifyNoInteractions(client);
        reset(consumer);when(p.outboxEventId()).thenReturn(COMMAND);assertThrows(TenantAccessException.class,()->relay().deliver(http,TENANT,EVENT));verifyNoInteractions(consumer);
    }
    static String payload(String kind){return "{\"command\":{\"tenant_id\":\""+TENANT+"\",\"run_id\":\""+RUN+"\",\"command_id\":\""+COMMAND+"\",\"kind\":\""+kind+"\"}}";}
}
