package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Isolated immutable-read/current-transmission boundaries with explicit JDBC and
 * already-verified receipt doubles. No tool/model/permission is manufactured. */
class TaskRegisteredToolHistoryBoundaryTest {
 final TaskInvocationFixture f=new TaskInvocationFixture();final PermissionSchema json=new PermissionSchema();
 final TaskInvocationPackages packages=mock(TaskInvocationPackages.class);final TaskProfessionalPredecessorEvidence predecessors=mock(TaskProfessionalPredecessorEvidence.class);
 final TaskNodeInputAuthority.Verified input=mock(TaskNodeInputAuthority.Verified.class);
 TaskRegisteredToolHistoryBoundaryTest()throws Exception{when(input.tenantId()).thenReturn(f.tenant);when(input.projectId()).thenReturn(f.project);when(input.runId()).thenReturn(f.run);when(input.stepId()).thenReturn(f.step);when(input.inputId()).thenReturn(f.request.node().node_input_artifact_id());when(input.receiptSha256()).thenReturn(f.hash);}
 TaskRegisteredToolReceipts receipts()throws Exception{return new TaskRegisteredToolReceipts(mock(DataSource.class),json,mock(TaskRegisteredToolAuthority.class),predecessors,packages,mock(TaskRegisteredToolWaitingService.class),new RunTokenAuthoritySchema(),mock(FormalRuntimeCapabilityIdentityAuthority.class),mock(FormalCapabilityIdentityAuthority.class),mock(TaskProfessionalObjectRegistration.class),mock(TaskProfessionalSupervisor.class),mock(TaskProfessionalRuntimeService.Deployment.class));}
 @Test void immutableReceiptRequiresMatchingAuditHashCanonicalBytesAndOutbox()throws Exception{
  var body=json.object().put("schema_version","1.0.0").put("kind","TASK_REGISTERED_TOOL_STARTED");for(String key:List.of("receipt_id","tenant_id","project_id","actor_id","run_id","step_id","session_id","input_id","input_sha256","input_receipt_sha256","classification"))body.put(key,"fixed-test");for(String key:List.of("binding","request","authority"))body.putObject(key);
  var document=json.object().put("receipt_sha256",TaskRegisteredToolAuthority.hash(body));document.set("body",body);var at=java.time.LocalDateTime.of(2026,10,20,0,0);var row=new HashMap<String,Object>(Map.of("resource_ref",json.canonical(document),"created_at",at));var event=new HashMap<String,Object>(Map.of("payload",json.canonical(document),"occurred_at",at));
  try(var construction=mockConstruction(JdbcTemplate.class,(db,ctx)->when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(c->List.of(((String)c.getArgument(0)).contains("FROM audit_event")?row:event)))){
   var receipts=receipts();assertEquals(document,receipts.read(f.admission,f.project,f.run,TaskInvocationFixture.id(80),TaskRegisteredToolReceipts.START,"INTERNAL","fixed-test",true));
   event.put("payload","{}");assertThrows(TenantAccessException.class,()->receipts.read(f.admission,f.project,f.run,TaskInvocationFixture.id(80),TaskRegisteredToolReceipts.START,"INTERNAL","fixed-test",true));event.put("payload",json.canonical(document));event.put("occurred_at",at.plusSeconds(1));assertThrows(TenantAccessException.class,()->receipts.read(f.admission,f.project,f.run,TaskInvocationFixture.id(80),TaskRegisteredToolReceipts.START,"INTERNAL","fixed-test",true));
   event.put("occurred_at",at);document.put("receipt_sha256","0".repeat(64));row.put("resource_ref",json.canonical(document));assertThrows(TenantAccessException.class,()->receipts.read(f.admission,f.project,f.run,TaskInvocationFixture.id(80),TaskRegisteredToolReceipts.START,"INTERNAL","fixed-test",true));
  }
 }
 @Test void at08NeverContinuesThroughOriginalHistoryOrModelSend()throws Exception{
  var pin=json.object();pin.putObject("tool_call").put("tool_name","ask_user_question");var db=mock(JdbcTemplate.class);
  assertThrows(TenantAccessException.class,()->TaskRegisteredToolOriginalHistory.require(db,json,f.request,f.actor,json.object(),json.object(),pin));verifyNoInteractions(db);
  try(var construction=mockConstruction(JdbcTemplate.class,(sql,ctx)->when(sql.queryForList(anyString(),any(Object[].class))).thenReturn(List.of()))){var receipts=receipts();var pins=json.object().arrayNode().add(pin);assertThrows(TenantAccessException.class,()->receipts.requireModelSend(f.admission,input,pins,"DOCUMENT_BODY","deepseek"));verifyNoInteractions(packages,predecessors);}
 }
 @Test void originalOtherSessionAndCurrentRevocationAreIndependentDenials()throws Exception{
  var db=mock(JdbcTemplate.class);var pin=json.object().put("tool_receipt_id",TaskRegisteredToolReceipts.receiptId(f.request.node().session_id(),"actual-call"));pin.putObject("tool_call").put("tool_name","read_artifact").put("tool_call_id","actual-call").put("session_id",TaskInvocationFixture.id(999));
  assertThrows(TenantAccessException.class,()->TaskRegisteredToolOriginalHistory.require(db,json,f.request,f.actor,json.object(),json.object(),pin));verifyNoInteractions(db);
  try(var construction=mockConstruction(JdbcTemplate.class)){var receipts=receipts();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(input).requireUnchanged();assertThrows(TenantAccessException.class,()->receipts.requireModelSend(f.admission,input,json.object().arrayNode(),"DOCUMENT_BODY","deepseek"));verifyNoInteractions(packages,predecessors);}
 }
 @Test void unresolvedQuestionBlocksEvenAnEmptyNextRequestHistory()throws Exception{
  try(var construction=mockConstruction(JdbcTemplate.class,(db,ctx)->when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("waiting_item_id",TaskInvocationFixture.id(80)))))){var receipts=receipts();assertThrows(TenantAccessException.class,()->receipts.requireModelSend(f.admission,input,json.object().arrayNode(),"DOCUMENT_BODY","deepseek"));verifyNoInteractions(packages,predecessors);}
 }
}
