package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises real ordered-history reader/proof logic against explicit factual-result and
 * authenticated-session doubles. Not a live database/provider integration claim. */
class TaskInvocationHistoryTest {
    final TaskInvocationFixture f=new TaskInvocationFixture();
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final TaskInvocationRecords records=mock(TaskInvocationRecords.class);
    final TaskProfessionalRuntimeService.Session session=mock(TaskProfessionalRuntimeService.Session.class);
    final TaskProfessionalSupervisor.Admitted supervisor=mock(TaskProfessionalSupervisor.Admitted.class);
    final TaskProfessionalSupervisor.ModelRequest observed=mock(TaskProfessionalSupervisor.ModelRequest.class);
    final TaskNodeInputAuthority.Verified input=mock(TaskNodeInputAuthority.Verified.class);
    final TaskInvocationResults results;
    final String invocation=TaskInvocationFixture.id(101);
    final JsonNode binding=f.schema.parse("{\"session\":\"original\"}");
    final ObjectNode output;
    TaskInvocationHistoryTest()throws Exception{
        var node=f.request.node();when(session.admission()).thenReturn(f.admission);when(session.input()).thenReturn(input);
        when(session.state()).thenReturn("OPEN");when(session.sessionId()).thenReturn(node.session_id());when(session.runId()).thenReturn(node.run_id());
        when(session.stepId()).thenReturn(node.step_id());when(session.actionId()).thenReturn(node.host_action_id());when(session.generation()).thenReturn(node.lease_generation());when(session.workerService()).thenReturn(node.worker_service_uri());when(session.binding()).thenReturn(binding);
        when(input.tenantId()).thenReturn(node.tenant_id());when(input.projectId()).thenReturn(node.project_id());when(input.taskId()).thenReturn(node.task_id());
        when(input.inputId()).thenReturn(node.node_input_artifact_id());when(input.inputSha256()).thenReturn(node.node_input_sha256());when(input.receiptSha256()).thenReturn(node.node_input_receipt_sha256());
        when(supervisor.sessionId()).thenReturn(node.session_id());when(supervisor.binding()).thenReturn(binding);when(supervisor.receiptId()).thenReturn(node.supervisor_receipt_id());when(supervisor.sha256()).thenReturn(node.supervisor_receipt_sha256());
        when(observed.sessionId()).thenReturn(node.session_id());when(observed.binding()).thenReturn(binding);when(observed.ordinal()).thenReturn(2L);
        when(f.stored.modelRequestOrdinal()).thenReturn(1L);when(records.byInvocation(f.tenant,invocation)).thenReturn(f.binding);
        var data=new TaskInvocationConversationTest();data.round(1,1);output=(ObjectNode)data.history.required("model_results").get(0).deepCopy();output.put("context_manifest_id",f.contextId);
        results=spy(new TaskInvocationResults(db,records,mock(FormalInvocationSettlement.class),mock(FormalInvocationObjectRegistration.class),mock(FormalProviderCaptureEvidence.class),f.schema));
        doAnswer(c->new TaskInvocationResults.Result(invocation,output.deepCopy(),"a".repeat(64),"b".repeat(64),"c".repeat(64))).when(results).verifiedResult(f.binding,invocation);
        when(db.query(startsWith("SELECT i.invocation_id"),any(RowMapper.class),any(Object[].class))).thenReturn(List.of(invocation));
    }
    TaskInvocationResults.OrderedHistory history()throws Exception{return results.orderedBefore(session,supervisor,observed);}
    @Test void persistedOrderedResultsProducePrivateToolCallPins()throws Exception{
        var history=history();assertEquals(invocation,history.modelResults().get(0).required("invocation_id").textValue());
        var call=history.toolCallPins("call-1-0");assertEquals(f.contextId,call.required("context_manifest_id").textValue());assertEquals(session.sessionId(),call.required("session_id").textValue());
        verify(db,atLeastOnce()).query(startsWith("SELECT i.invocation_id"),any(RowMapper.class),eq(f.tenant),eq(f.project),eq(f.run),eq(f.step),eq("1"),eq(f.actor),eq(2L));
        for(var constructor:TaskInvocationResults.OrderedHistory.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }
    @Test void firstCallHasNoFabricatedHistory()throws Exception{
        when(observed.ordinal()).thenReturn(1L);when(db.query(startsWith("SELECT i.invocation_id"),any(RowMapper.class),any(Object[].class))).thenReturn(List.of());
        var history=history();assertTrue(history.modelResults().isEmpty());assertTrue(history.modelPins().isEmpty());verifyNoInteractions(records);
    }
    @Test void missingDuplicatedAndNoncontiguousRoundsCannotBeUsed()throws Exception{
        when(observed.ordinal()).thenReturn(3L);assertThrows(TenantAccessException.class,this::history);
        when(db.query(startsWith("SELECT i.invocation_id"),any(RowMapper.class),any(Object[].class))).thenReturn(List.of(invocation,invocation));assertThrows(TenantAccessException.class,this::history);
        when(observed.ordinal()).thenReturn(2L);when(db.query(startsWith("SELECT i.invocation_id"),any(RowMapper.class),any(Object[].class))).thenReturn(List.of(invocation));when(f.stored.modelRequestOrdinal()).thenReturn(2L);assertThrows(TenantAccessException.class,this::history);
    }
    @Test void sameRunOtherSessionOrTenantDoesNotConferHistoryAuthority()throws Exception{
        when(session.sessionId()).thenReturn(TaskInvocationFixture.id(900));assertThrows(TenantAccessException.class,this::history);
        when(session.sessionId()).thenReturn(f.request.node().session_id());when(input.tenantId()).thenReturn(TaskInvocationFixture.id(901));
        when(records.byInvocation(TaskInvocationFixture.id(901),invocation)).thenReturn(f.binding);assertThrows(TenantAccessException.class,this::history);
    }
    @Test void pendingOrTerminalNonToolResultCannotAuthorizeNextRound()throws Exception{
        doThrow(new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE)).when(results).verifiedResult(f.binding,invocation);assertThrows(TenantAccessException.class,this::history);
        doReturn(new TaskInvocationResults.Result(invocation,output,"a".repeat(64),"b".repeat(64),"c".repeat(64))).when(results).verifiedResult(f.binding,invocation);
        ((ObjectNode)output.required("reason")).put("kind","stop");assertThrows(TenantAccessException.class,this::history);
    }
    @Test void resultTamperingAndSessionRevocationInvalidateExistingProof()throws Exception{
        var history=history();((ObjectNode)output.required("blocks").get(0)).put("text","rewritten");assertThrows(TenantAccessException.class,history::requireUnchanged);
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(session).requireUnchanged();assertThrows(TenantAccessException.class,this::history);
    }
    @Test void outputFromAnotherContextOrModelIsRejected()throws Exception{
        output.put("context_manifest_id",TaskInvocationFixture.id(902));assertThrows(TenantAccessException.class,this::history);
        output.put("context_manifest_id",f.contextId).put("model_config_revision",TaskInvocationFixture.id(903));assertThrows(TenantAccessException.class,this::history);
    }
    @Test void historyBoundsAndUnknownCallsFailClosed()throws Exception{
        var proof=history();assertThrows(TenantAccessException.class,()->proof.toolCallPins("invented-call"));
        when(observed.ordinal()).thenReturn(258L);assertThrows(TenantAccessException.class,this::history);
    }
}
