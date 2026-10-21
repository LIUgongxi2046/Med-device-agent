package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real bounded waiting inserts and immutable replay against explicit JDBC and
 * private current-authority doubles. This is not a live permission or model claim. */
class TaskRegisteredToolWaitingServiceTest extends TaskNodeInputFixture {
 final TaskRegisteredToolAuthority.Checked checked=mock(TaskRegisteredToolAuthority.Checked.class);
 final TaskProfessionalRuntimeService.Session session=mock(TaskProfessionalRuntimeService.Session.class);
 final TaskNodeInputAuthority.Verified physical=mock(TaskNodeInputAuthority.Verified.class);
 final TaskResearchRuleInputs rules=mock(TaskResearchRuleInputs.class);
 final TaskResearchRuleInputs.Verified fixed=mock(TaskResearchRuleInputs.Verified.class);
 final TaskRegisteredToolWaitingService waiting=new TaskRegisteredToolWaitingService(db,rules,json);
 final String sid=id(80),owner=id(81),requirement=id(82);final JsonNode request;
 TaskRegisteredToolWaitingServiceTest()throws Exception {
  var args=json.object().put("question_type","NEED_EVIDENCE").put("prompt_text","Which document supports this requirement?");args.putArray("target_section_ids").add(input.sectionId());args.putArray("requirement_ids").add(requirement);args.putArray("evidence_refs");
  var value=json.object().put("call_id","actual-question").put("name","ask_user_question").put("arguments_sha256",TaskRegisteredToolAuthority.hash(args));value.set("arguments",args);request=value;
  when(checked.session()).thenReturn(session);when(checked.tool()).thenReturn("AT-08");when(checked.request()).thenReturn(request);when(checked.actionId()).thenReturn(id(83));when(checked.classification()).thenReturn("RESTRICTED");when(checked.canonicalPins()).thenReturn(json.object().put("explicit_test_authority_boundary","current-c07-role-tool"));
  when(session.admission()).thenReturn(admission);when(session.input()).thenReturn(physical);when(session.sessionId()).thenReturn(sid);when(session.runId()).thenReturn(run);when(session.stepId()).thenReturn(step);when(session.state()).thenReturn("OPEN");when(session.binding()).thenReturn(json.object().put("explicit_test_authority_boundary","current-host-binding"));
  when(physical.tenantId()).thenReturn(tenant);when(physical.projectId()).thenReturn(project);when(physical.runId()).thenReturn(run);when(physical.stepId()).thenReturn(step);when(physical.inputId()).thenReturn(id(84));when(physical.inputSha256()).thenReturn(hash('a'));when(physical.receiptSha256()).thenReturn(hash('b'));when(physical.manifest()).thenReturn(schema.tree(input));
  when(rules.require(session)).thenReturn(fixed);when(fixed.ownerId()).thenReturn(owner);when(fixed.requirementIds()).thenReturn(List.of(requirement));
  doAnswer(c->select(c.getArgument(0),arguments(c.getArguments()))).when(db).queryForList(anyString(),any(Object[].class));
 }
 private static Object[] arguments(Object[] all){var rest=Arrays.copyOfRange(all,1,all.length);return rest.length==1&&rest[0] instanceof Object[] a?a:rest;}
 private List<Map<String,Object>> select(String sql,Object[] args){
  var from=Pattern.compile("FROM ([a-z_]+)").matcher(sql);if(!from.find())throw new AssertionError(sql);var out=new ArrayList<Map<String,Object>>();
  for(var row:rows(from.group(1))){boolean match=true;int n=0;var conditions=Pattern.compile("([a-z_][a-z0-9_]*)=\\?").matcher(sql);while(conditions.find())if(!Objects.equals(String.valueOf(row.get(conditions.group(1))),String.valueOf(args[n++]))){match=false;break;}
   if(!match)continue;conditions=Pattern.compile("([a-z_][a-z0-9_]*)='([^']*)'").matcher(sql);while(conditions.find())if(!Objects.equals(row.get(conditions.group(1)),conditions.group(2)))match=false;
   conditions=Pattern.compile("([a-z_][a-z0-9_]*)=([0-9]+)\\b").matcher(sql);while(conditions.find())if(!conditions.group(2).equals(String.valueOf(row.get(conditions.group(1)))))match=false;if(sql.contains("resolution_id IS NULL")&&row.get("resolution_id")!=null)match=false;if(match)out.add(new LinkedHashMap<>(row));}
  return out;
 }
 JsonNode create()throws Exception{return tx(()->waiting.create(checked));}
 @Test void writesOneRealItemAuditOutboxAndIdempotentOriginal()throws Exception{
  var original=create();assertEquals(owner,only("waiting_item").get("assignee_id"));assertEquals("ASK_USER_QUESTION",only("waiting_item").get("reason_code"));assertEquals(original,create());for(String table:List.of("waiting_item","operation_record","audit_event","outbox_event"))assertEquals(1,rows(table).size());assertTrue(rows("artifact").isEmpty());assertTrue(rows("run_step").isEmpty());
 }
 @Test void auditAndOutboxTamperingCannotBecomeOriginalReceipt()throws Exception{
  var original=create();only("audit_event").put("resource_ref","{}");assertThrows(TenantAccessException.class,()->tx(()->{waiting.requireOriginal(admission,original);return null;}));only("audit_event").put("resource_ref",json.canonical(original));only("outbox_event").put("payload","{}");assertThrows(TenantAccessException.class,()->tx(()->{waiting.requireOriginal(admission,original);return null;}));
 }
 @Test void alteredAggregateRevisionCannotBecomeOriginalReceipt()throws Exception{
  var original=create();only("outbox_event").put("aggregate_revision",2);
  assertThrows(TenantAccessException.class,()->tx(()->{waiting.requireOriginal(admission,original);return null;}));
 }
 @Test void currentResolutionAndRevocationDenyButOriginalRecoveryStaysFactual()throws Exception{
  var original=create();only("waiting_item").put("status","STALE");only("waiting_item").put("revision","2");only("waiting_item").put("resolution_id",id(90));assertThrows(TenantAccessException.class,()->tx(()->{waiting.requireCurrent(checked,original);return null;}));assertDoesNotThrow(()->tx(()->{waiting.requireOriginal(admission,original);return null;}));
  doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(checked).requireUnchanged();assertThrows(TenantAccessException.class,this::create);
 }
 @Test void missingOwnerClosedSessionAndUnsettledCallCannotCreateAnyItem()throws Exception{
  when(fixed.ownerId()).thenReturn(null);assertThrows(TenantAccessException.class,this::create);when(fixed.ownerId()).thenReturn(owner);when(session.state()).thenReturn("CLOSED");assertThrows(TenantAccessException.class,this::create);when(session.state()).thenReturn("OPEN");doThrow(new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE)).when(checked).requireUnchanged();assertThrows(TenantAccessException.class,this::create);assertTrue(rows("waiting_item").isEmpty());
 }
}
