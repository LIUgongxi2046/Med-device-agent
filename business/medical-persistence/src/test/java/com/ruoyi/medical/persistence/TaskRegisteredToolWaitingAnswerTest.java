package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.workflow.TaskRunWaitingProtocol.WaitingCompletionV2;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real closed request, answer journal, idempotency and STALE CAS against an explicit
 * stateful JDBC double. Separate original/owner/business proofs are named doubles;
 * this does not assert live CSRF, SQL transactions or real run execution. */
class TaskRegisteredToolWaitingAnswerTest extends TaskNodeInputFixture {
 final EnterpriseAccess access=mock(EnterpriseAccess.class);final ApplicationSessionIdentity.Verified user=mock(ApplicationSessionIdentity.Verified.class);
 final TaskRegisteredToolWaitingAnswerAuthority authority=mock(TaskRegisteredToolWaitingAnswerAuthority.class);final TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion question=mock(TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion.class);final TaskRegisteredToolWaitingAnswerAuthority.Original original=mock(TaskRegisteredToolWaitingAnswerAuthority.Original.class);
 final TaskRegisteredToolWaitingNewRun links=mock(TaskRegisteredToolWaitingNewRun.class);final TaskRunWaitingCommitService commits=mock(TaskRunWaitingCommitService.class);final TaskRegisteredToolWaitingAnswerService service;final ObjectNode request;final String waiting=id(90),newRun=id(91),key="registered-answer-original-key-0001";
 TaskRegisteredToolWaitingAnswerTest()throws Exception{
  when(admission.controlWorker()).thenReturn(false);when(admission.watermarks()).thenReturn(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));when(admission.bindingView()).thenReturn(new AuthenticatedIdentityBinding.View("local",actor,null,null));
  when(access.execute(eq(user),any())).thenAnswer(c->tx(()->c.<Function<EnterpriseAccess.Admission,Object>>getArgument(1).apply(admission)));
  when(authority.require(admission,"1",waiting)).thenReturn(question);when(authority.original(question)).thenReturn(original);when(question.admission()).thenReturn(admission);when(question.tenantId()).thenReturn(tenant);when(question.projectId()).thenReturn(project);when(question.runId()).thenReturn(run);when(question.taskId()).thenReturn(task);when(question.waitingItemId()).thenReturn(waiting);when(question.classification()).thenReturn("RESTRICTED");when(question.creation()).thenReturn(json.object().put("cause_sha256",hash('c')));when(question.grantPins()).thenReturn(json.object().put("named_test_boundary","actual-human-owner-read-resolve-task"));
  var locator=new WaitingCompletionV2("2.0.0","WAITING_CONTROL",run,step,hash('a'),1,id(92),hash('b'));when(original.locator()).thenReturn(locator);when(commits.requireAnswerOriginal(original,locator)).thenReturn(json.object().put("named_test_boundary","original-committed-ask-no-output"));when(links.require(eq(question),any())).thenReturn(json.object().put("named_test_boundary","original-confirm-and-rn01-link"));
  request=json.object().put("schema_version","1.0.0").put("waiting_item_id",waiting).put("expected_revision","1").put("cause_sha256",hash('c')).put("decision","LINK_CONFIRMED_REVISION_AND_NEW_RUN").put("answer_text","Revised sources were submitted through the separate business workflow.").put("reason","Link the confirmed revision and its actual new run.");request.putObject("plan_confirmation").put("decision_id",id(93)).put("receipt_sha256",hash('d'));request.putObject("target_run").put("run_id",newRun).put("command_id",id(94)).put("creation_operation_id",id(95)).put("creation_receipt_sha256",hash('e')).put("replay_sha256",hash('f'));
  var row=new LinkedHashMap<String,Object>();row.put("tenant_id",tenant);row.put("project_id",project);row.put("waiting_item_id",waiting);row.put("status","WAITING_INPUT");row.put("revision",1);row.put("resolution_id",null);rows("waiting_item").add(row);
  doAnswer(c->selectRows(c.getArgument(0),args(c.getArguments()))).when(db).queryForList(anyString(),any(Object[].class));
  doAnswer(c->{var a=args(c.getArguments());var r=only("waiting_item");if(!"WAITING_INPUT".equals(r.get("status"))||r.get("resolution_id")!=null)return 0;r.put("status","STALE");r.put("resolution_id",a[0]);r.put("revision",2);r.put("updated_at",a[1]);return 1;}).when(db).update(startsWith("UPDATE waiting_item SET status='STALE'"),any(Object[].class));
  service=new TaskRegisteredToolWaitingAnswerService(db,access,authority,links,commits,json);
 }
 private List<Map<String,Object>> selectRows(String sql,Object[] args){var from=Pattern.compile("FROM ([a-z_]+)").matcher(sql);if(!from.find())throw new AssertionError(sql);var out=new ArrayList<Map<String,Object>>();for(var row:rows(from.group(1))){boolean match=true;int n=0;var m=Pattern.compile("([a-z_][a-z0-9_]*)=\\?").matcher(sql);while(m.find())if(!Objects.equals(String.valueOf(row.get(m.group(1))),String.valueOf(args[n++]))){match=false;break;}m=Pattern.compile("([a-z_][a-z0-9_]*)='([^']*)'").matcher(sql);while(m.find())if(!Objects.equals(row.get(m.group(1)),m.group(2)))match=false;if(match)out.add(new LinkedHashMap<>(row));}return out;}
 private static Object[] args(Object[] all){var rest=Arrays.copyOfRange(all,1,all.length);return rest.length==1&&rest[0] instanceof Object[] a?a:rest;}
 private byte[] bytes(JsonNode n)throws Exception{return json.canonical(n).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
 JsonNode answer()throws Exception{return service.answer(user,"1",waiting,key,bytes(request));}
 @Test void atomicAnswerLinksActualProofsAndOriginalReplayDoesNotCreateRunOrApproval()throws Exception{var first=answer();assertEquals("STALE",only("waiting_item").get("status"));assertFalse(first.required("can_resume_frozen_run").booleanValue());assertFalse(first.required("answer_is_approved_fact").booleanValue());assertFalse(first.required("human_checkpoint_approved").booleanValue());assertEquals(first,answer());for(String table:List.of("operation_record","audit_event","outbox_event","version_registry"))assertEquals(1,rows(table).size());assertTrue(rows("task_run").isEmpty());assertTrue(rows("artifact").isEmpty());}
 @Test void tamperedAuditOutboxAndRegistryCannotRecover()throws Exception{answer();String audit=(String)only("audit_event").get("resource_ref");only("audit_event").put("resource_ref","{}");assertThrows(TenantAccessException.class,this::answer);only("audit_event").put("resource_ref",audit);String event=(String)only("outbox_event").get("payload");only("outbox_event").put("payload","{}");assertThrows(TenantAccessException.class,this::answer);only("outbox_event").put("payload",event);only("version_registry").put("content_sha256",hash('0'));assertThrows(TenantAccessException.class,this::answer);}
 @Test void changedAnswerSameKeyConflictsAndRevokedOwnerCannotReplay()throws Exception{answer();request.put("answer_text","Different response");assertThrows(TenantAccessException.class,this::answer);doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(authority).require(admission,"1",waiting);assertThrows(TenantAccessException.class,this::answer);assertEquals(1,rows("operation_record").size());}
 @Test void missingOriginalCommitOrRealBusinessChangeNeverWrites()throws Exception{doThrow(new TenantAccessException(ErrorCode.SOURCE_MISSING)).when(links).require(eq(question),any());assertThrows(TenantAccessException.class,this::answer);assertTrue(rows("operation_record").isEmpty());assertEquals("WAITING_INPUT",only("waiting_item").get("status"));}
 @Test void recoveryMissingReceiptNeverCreatesAnswer()throws Exception{var r=json.object().put("schema_version","1.0.0").put("original_idempotency_key",key);r.set("original_request",request);assertThrows(TenantAccessException.class,()->service.recover(user,"1",waiting,bytes(r)));assertTrue(rows("operation_record").isEmpty());verifyNoInteractions(links);}
}
