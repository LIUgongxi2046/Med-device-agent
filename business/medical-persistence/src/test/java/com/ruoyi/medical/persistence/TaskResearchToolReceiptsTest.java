package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real action producer/read-result journal/history with explicit JDBC, mTLS token, session,
 * model result and source reader doubles. No provider or process isolation runs in this test. */
class TaskResearchToolReceiptsTest extends TaskNodeInputFixture {
 final TaskProfessionalHostAccess hosts=mock(TaskProfessionalHostAccess.class);final TaskProfessionalHostAccess.Handle host=mock(TaskProfessionalHostAccess.Handle.class);
 final TaskProfessionalSupervisor supervisor=mock(TaskProfessionalSupervisor.class);final TaskProfessionalRuntimeService.Session session=mock(TaskProfessionalRuntimeService.Session.class);final TaskProfessionalSupervisor.Admitted admitted=mock(TaskProfessionalSupervisor.Admitted.class);final TaskNodeInputAuthority.Verified physical=mock(TaskNodeInputAuthority.Verified.class);
 final TaskInvocationService invocations=mock(TaskInvocationService.class);final TaskInvocationService.ToolCall call=mock(TaskInvocationService.ToolCall.class);final TaskResearchToolAccess tools=mock(TaskResearchToolAccess.class);final TaskResearchToolAccess.Handle toolHandle=mock(TaskResearchToolAccess.Handle.class);final RunTokenAuthority tokens=mock(RunTokenAuthority.class);final RunTokenAuthority.Issued issued=mock(RunTokenAuthority.Issued.class);
 final FormalRuntimeCapabilityIdentityAuthority capabilities=mock(FormalRuntimeCapabilityIdentityAuthority.class);final FormalRuntimeCapabilityIdentityAuthority.VerifiedCapability version=mock(FormalRuntimeCapabilityIdentityAuthority.VerifiedCapability.class);final FormalRuntimeCapabilityIdentityAuthority.RegisteredIdentity originalVersion=mock(FormalRuntimeCapabilityIdentityAuthority.RegisteredIdentity.class);
 final TaskResearchSourceReader sources=mock(TaskResearchSourceReader.class);final TaskResearchSourceReader.VerifiedReceipts sourceProof=mock(TaskResearchSourceReader.VerifiedReceipts.class);final TaskResearchRetrievalAuthority retrievals=mock(TaskResearchRetrievalAuthority.class);final TaskResearchRetrievalAuthority.Verified retrieved=mock(TaskResearchRetrievalAuthority.Verified.class);final TaskResearchSourceReader.Read read=mock(TaskResearchSourceReader.Read.class);
 final TaskResearchToolReceipts receipts=new TaskResearchToolReceipts(db,supervisor,capabilities,sources,retrievals,json);final TaskResearchToolBridge bridge=new TaskResearchToolBridge(db,hosts,supervisor,invocations,tools,tokens,receipts,json,new RunTokenAuthoritySchema());
 final TaskResearchCompletionAuthority.Input original=mock(TaskResearchCompletionAuthority.Input.class);final HttpServletRequest http=mock(HttpServletRequest.class);
 final String sid=id(60),modelAction=id(61),invocation=id(62),readId=id(63),content="actual parsed chunk";final JsonNode request,binding,callPins;final DatasetProtocol.EvidenceRef ref;RunTokenAuthority.ActionRequest boundAction;
 TaskResearchToolReceiptsTest()throws Exception{
  var catalogue=SchemaCatalogue.fromClasspath();ref=new DatasetProtocol.EvidenceRef("1.0.0",tenant,project,id(19),id(20),id(21),id(30),DatasetProtocol.LocatorKind.TXT,new DatasetProtocol.TextLocator("TXT","UTF-8","PRESERVE",0,content.length(),1,1),WireValues.sha256(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)),id(23));
  var permission=new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(tenant,project,"SOURCE",id(20)),"source.read","TASK_EXECUTION");
  var replay=json.object();replay.putObject("agent_release").putObject("limits").put("max_tool_calls","2");((com.fasterxml.jackson.databind.node.ObjectNode)replay.required("agent_release")).putArray("tool_versions").add(json.tree(basis(70)));
  var node=(com.fasterxml.jackson.databind.node.ObjectNode)new TaskNodeInputSchema().tree(input);node.set("node_permissions",json.tree(List.of(permission)));String replaySha=hash(replay);node.put("replay_sha256",replaySha);((com.fasterxml.jackson.databind.node.ObjectNode)node.required("replay_artifact")).put("sha256",replaySha);new TaskNodeInputSchema().read(ContentReadAuthorization.canonicalBytes(node));
  when(physical.manifest()).thenReturn(node);when(physical.tenantId()).thenReturn(tenant);when(physical.projectId()).thenReturn(project);when(physical.inputId()).thenReturn(id(71));when(physical.inputSha256()).thenReturn(hash('a'));when(physical.receiptSha256()).thenReturn(hash('b'));
  binding=json.object().put("tenant_id",tenant).put("run_id",run).put("step_id",step).put("action_id",id(72)).put("lease_generation",1).put("input_sha256",hash('a')).put("step_envelope_sha256",hash('c'));
  when(session.admission()).thenReturn(admission);when(session.input()).thenReturn(physical);when(session.sessionId()).thenReturn(sid);when(session.runId()).thenReturn(run);when(session.stepId()).thenReturn(step);when(session.generation()).thenReturn("1");when(session.binding()).thenReturn(binding);
  var deployment=mock(TaskProfessionalRuntimeService.Deployment.class);when(deployment.value()).thenReturn(json.object().put("token_lifetime_seconds",30));when(session.deployment()).thenReturn(deployment);when(admitted.receiptId()).thenReturn(id(73));when(admitted.sha256()).thenReturn(hash('d'));when(supervisor.requireAdmitted(session)).thenReturn(admitted);
  var arguments=json.object().putNull("cursor");arguments.set("evidence_ref",json.tree(ref));request=json.object().put("call_id","actual-tool-call").put("name","read_source").put("arguments_sha256",hash(arguments)).set("arguments",arguments);
  callPins=json.object().put("invocation_id",invocation).put("accounting_sha256",hash('e')).put("result_registration_sha256",hash('f')).put("capture_registration_sha256",hash('a')).put("context_manifest_id",id(74)).put("tool_call_id","actual-tool-call").put("tool_name","read_source").put("arguments_sha256",hash(arguments)).put("session_id",sid).put("supervision_receipt_id",id(73)).put("supervision_sha256",hash('d'));
  when(call.canonicalPins()).thenReturn(callPins);when(invocations.toolCall(eq(session),eq(admitted),eq("actual-tool-call"),eq("read_source"),any())).thenReturn(call);
  var definition=json.object();var published=definition.putObject("request");published.putObject("input_schema_ref").put("schema_ref",catalogue.schemaRef("tool-inputs","ReadArgs")).put("schema_sha256",catalogue.schemaDigest("tool-inputs"));published.putObject("output_schema_ref").put("schema_ref",catalogue.schemaRef("task-tool-read-success",null)).put("schema_sha256",catalogue.schemaDigest("task-tool-read-success"));published.putObject("tool_scope").set("permissions",json.tree(List.of(permission)));
  when(version.version()).thenReturn(basis(70));when(version.canonicalDefinition()).thenReturn(definition);when(capabilities.require(eq(admission),eq(basis(70)),eq("TOOL"),eq(project),eq("AGENT_PUBLICATION"))).thenReturn(version);when(originalVersion.version()).thenReturn(basis(70));when(originalVersion.canonicalDefinition()).thenReturn(definition);when(capabilities.requireRegisteredIdentity(tenant,actor,basis(70),"TOOL",project)).thenReturn(originalVersion);
  when(hosts.execute(eq(host),any())).thenAnswer(c->tx(()->((Function<EnterpriseAccess.Admission,Object>)c.getArgument(1)).apply(admission)));when(hosts.session(host,admission,sid)).thenReturn(session);
  when(issued.token()).thenReturn("explicit-test-token-double");when(tokens.issue(eq(http),any())).thenAnswer(c->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());assertEquals(List.of("source.read"),((RunTokenAuthority.IssueRequest)c.getArgument(1)).actions());return issued;});when(tools.open(eq(http),eq("explicit-test-token-double"),any())).thenAnswer(c->{boundAction=c.getArgument(2);return toolHandle;});
  when(read.content()).thenReturn(content);when(read.evidenceRef()).thenReturn(ref);when(read.readReceiptId()).thenReturn(readId);when(read.complete()).thenReturn(true);when(read.requestedScopeSha256()).thenReturn(hash(ref));when(read.deliveredScopeSha256()).thenReturn(hash(ref));
  when(tools.readSource(eq(toolHandle),eq(ref),isNull())).thenAnswer(c->{var row=new LinkedHashMap<String,Object>();row.put("tenant_id",tenant);row.put("project_id",project);row.put("audit_id",readId);row.put("action",TaskResearchSourceReader.ACTION);row.put("request_id",boundAction.actionId());row.put("subject_id",actor);row.put("created_by",actor);row.put("result","SUCCESS");rows("audit_event").add(row);return read;});
  when(sources.requireReceipts(eq(admission),eq(physical),anyList(),anyList())).thenReturn(sourceProof);when(sources.requireReceipts(eq(admission),eq(original),anyList(),anyList())).thenReturn(sourceProof);when(sourceProof.canonicalPins()).thenReturn(json.object().arrayNode().add(json.object().put("read_receipt_id",readId).put("receipt_sha256",hash('e'))));
  // Explicit v1 fixture: the request has no v2 evidence_contract opt-in.
  when(retrievals.require(eq(session),anyList(),eq(false))).thenReturn(retrieved);when(retrievals.requireOriginal(eq(original),anyList(),eq(false))).thenReturn(retrieved);when(retrieved.canonicalPins()).thenReturn(json.object().arrayNode());when(retrieved.evidenceRefs()).thenReturn(json.object().arrayNode());
  when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(c->selectRows(c.getArgument(0),arguments(c.getArguments())));when(db.query(contains("SELECT COUNT(*)"),any(RowMapper.class),any(Object[].class))).thenAnswer(c->List.of(BigInteger.valueOf(rows("audit_event").stream().filter(r->"task.research.tool.started".equals(r.get("action"))).count())));
  var runRow=new LinkedHashMap<String,Object>();runRow.put("tenant_id",tenant);runRow.put("project_id",project);runRow.put("run_id",run);runRow.put("created_by",actor);runRow.put("execution_kind","TASK");runRow.put("status","RUNNING");runRow.put("replay_manifest",json.canonical(replay));runRow.put("classification","RESTRICTED");rows("task_run").add(runRow);
  rows("step_action_binding").add(new LinkedHashMap<>(Map.of("tenant_id",tenant,"project_id",project,"run_id",run,"step_id",step,"action_id",modelAction,"invocation_id",invocation,"kind","MODEL_CALL","lease_generation","1")));
  var start=json.object().put("session_id",sid).put("isolation_receipt_id",id(73)).put("supervision_sha256",hash('d'));start.set("binding",binding);var sessionRow=new LinkedHashMap<String,Object>();for(var pin:Map.of("tenant_id",tenant,"project_id",project,"run_id",run,"step_id",step,"session_id",sid,"action_id",id(72),"input_sha256",hash('a'),"service_uri","spiffe://test.invalid/worker","created_by",actor,"admission_state","CLOSED").entrySet())sessionRow.put(pin.getKey(),pin.getValue());sessionRow.put("lease_generation",1);sessionRow.put("canonical_start",json.canonical(start));sessionRow.put("start_sha256",hash(start));rows("professional_runtime_session").add(sessionRow);
  when(original.manifest()).thenReturn(node);when(original.tenantId()).thenReturn(tenant);when(original.projectId()).thenReturn(project);when(original.runId()).thenReturn(run);when(original.stepId()).thenReturn(step);when(original.actorId()).thenReturn(actor);when(original.inputId()).thenReturn(id(71));when(original.inputSha256()).thenReturn(hash('a'));when(original.receiptSha256()).thenReturn(hash('b'));when(original.actionId()).thenReturn(id(72));when(original.sessionId()).thenReturn(sid);when(original.generation()).thenReturn(1L);when(original.workerService()).thenReturn("spiffe://test.invalid/worker");when(original.admission()).thenReturn(admission);
 }
 private static Object[] arguments(Object[] values){var rest=Arrays.copyOfRange(values,1,values.length);return rest.length==1&&rest[0] instanceof Object[] a?a:rest;}
 private List<Map<String,Object>> selectRows(String sql,Object[] args){var from=Pattern.compile("FROM ([a-z_]+)").matcher(sql);if(!from.find())throw new AssertionError(sql);var result=new ArrayList<Map<String,Object>>();for(var row:rows(from.group(1))){boolean match=true;int n=0;var conditions=Pattern.compile("([a-z_][a-z0-9_]*)=\\?").matcher(sql);while(conditions.find())if(!Objects.equals(String.valueOf(row.get(conditions.group(1))),String.valueOf(args[n++]))){match=false;break;}if(!match)continue;conditions=Pattern.compile("([a-z_][a-z0-9_]*)='([^']*)'").matcher(sql);while(conditions.find())if(!Objects.equals(row.get(conditions.group(1)),conditions.group(2)))match=false;if(match)result.add(new LinkedHashMap<>(row));}return result;}
 private String hash(Object value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
 JsonNode execute()throws Exception{return bridge.forward(http,host,sid,"AT-02",request);}
 TaskResearchToolReceipts.History history()throws Exception{return tx(()->receipts.requireHistory(session,List.of(call)));}
 @Test void realReadActionAndOriginalJournalProduceTypedResponseAndReplayWithoutAnotherRead()throws Exception{
  var response=execute();assertEquals("TASK_READ_RESULT",response.required("kind").textValue());assertFalse(response.has("artifact_id"));assertEquals(content,response.required("payload").required("content").textValue());assertEquals(modelAction,rows("step_action_binding").get(1).get("origin_action_id"));assertEquals("AT-02",boundAction.kind());assertTrue(FormalTaskNodeAuthority.sameEvidence(response,execute()));verify(tools,times(1)).readSource(eq(toolHandle),eq(ref),isNull());var history=history();assertTrue(FormalTaskNodeAuthority.sameEvidence(response,tx(history::canonicalResponses).get(0).required("response")));assertEquals(invocation,tx(history::canonicalPins).get(0).required("tool_call").required("invocation_id").textValue());
 }
 @Test void actualHistoricalInputVerifiesOriginalResponseWithoutMintingSessionOrToken()throws Exception{
  execute();var h=history();var responses=tx(h::canonicalResponses);var pins=tx(h::canonicalPins);clearInvocations(hosts,tokens,invocations,supervisor);tx(()->{receipts.requireOriginalHistory(original,responses,pins);return null;});verifyNoInteractions(hosts,tokens,invocations,supervisor);verify(retrievals,atLeastOnce()).require(eq(session),eq(List.of()),eq(false));verify(retrievals).requireOriginal(eq(original),eq(List.of()),eq(false));
 }
 @Test void changedModelCallSourceRightsOrResponseBytesCannotReplay()throws Exception{
  execute();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(call).requireUnchanged();assertThrows(TenantAccessException.class,this::execute);doNothing().when(call).requireUnchanged();when(sourceProof.canonicalPins()).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,this::execute);
 }
 @Test void revokedRetrievalAuthorityCannotReplayCurrentOrOriginalReceipts()throws Exception{
  execute();var h=history();var responses=tx(h::canonicalResponses);var pins=tx(h::canonicalPins);
  when(retrievals.require(eq(session),anyList(),eq(false))).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,this::execute).code());
  when(retrievals.requireOriginal(eq(original),anyList(),eq(false))).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->tx(()->{receipts.requireOriginalHistory(original,responses,pins);return null;})).code());
 }
 @Test void missingAuditOrOutboxAndCopiedOtherCallAreRejected()throws Exception{
  execute();rows("outbox_event").removeIf(r->"task.research.tool.result".equals(r.get("event_type")));assertThrows(TenantAccessException.class,this::history);
 }
 @Test void noUnverifiedRawResponseOrHistoryFactoryIsPublic(){
  for(Class<?> type:List.of(TaskResearchToolBridge.Response.class,TaskResearchToolReceipts.Current.class,TaskResearchToolReceipts.History.class))for(var constructor:type.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
 }
 @Test void wrongNamesArgumentsScopesAndUnsupportedCursorFailBeforeTokenOrRead()throws Exception{
  var catalogue=SchemaCatalogue.fromClasspath();for(String tool:List.of("AT-03","AT-10","MODEL_CALL"))assertThrows(TenantAccessException.class,()->TaskResearchToolReceipts.validateRequest(tool,request,catalogue));var changed=(com.fasterxml.jackson.databind.node.ObjectNode)request.deepCopy();changed.put("name","search_sources");assertThrows(TenantAccessException.class,()->TaskResearchToolReceipts.validateRequest("AT-02",changed,catalogue));var args=(com.fasterxml.jackson.databind.node.ObjectNode)request.required("arguments").deepCopy();args.put("cursor","caller-cursor");var cursor=(com.fasterxml.jackson.databind.node.ObjectNode)request.deepCopy();cursor.set("arguments",args);cursor.put("arguments_sha256",hash(args));assertThrows(TenantAccessException.class,()->TaskResearchToolReceipts.validateRequest("AT-02",cursor,catalogue));verifyNoInteractions(tokens,tools);
 }
 @Test void historicalShapeSiblingInputAndForgedResponseHashFailClosed()throws Exception{
  execute();var h=history();var responses=tx(h::canonicalResponses);var pins=tx(h::canonicalPins);var wrong=responses.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)wrong.get(0)).put("tool_call_id","other-call");assertThrows(TenantAccessException.class,()->tx(()->{receipts.requireOriginalHistory(original,wrong,pins);return null;}));when(original.inputSha256()).thenReturn(hash('9'));assertThrows(TenantAccessException.class,()->tx(()->{receipts.requireOriginalHistory(original,responses,pins);return null;}));
 }
 @Test void declaredToolCallLimitIsCumulativeAcrossOriginalStarts()throws Exception{
  for(int n=0;n<2;n++)rows("audit_event").add(new LinkedHashMap<>(Map.of("tenant_id",tenant,"project_id",project,"trace_id",run,"action","task.research.tool.started")));assertThrows(TenantAccessException.class,this::execute);verifyNoInteractions(tokens,tools);
 }
 @Test void iterativeFactualHistoryReusesOriginalResponseAndCurrentSourceGate()throws Exception{
  var response=execute();var ordered=mock(TaskInvocationResults.OrderedHistory.class);var models=json.object().arrayNode();models.addObject().putArray("blocks").addObject().put("type","tool-call").put("id","actual-tool-call");
  when(ordered.modelResults()).thenReturn(models);when(ordered.toolCallPins("actual-tool-call")).thenReturn(callPins);clearInvocations(invocations,tokens,tools);
  var history=tx(()->receipts.requireHistory(session,ordered));assertTrue(FormalTaskNodeAuthority.sameEvidence(response,tx(history::canonicalResponses).get(0).required("response")));verifyNoInteractions(invocations,tokens,tools);
  when(sourceProof.canonicalPins()).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->tx(()->{history.requireUnchanged();return null;}));
 }
 @Test void copiedFactualHistoryToolPinCannotChangeContextOrReceipt()throws Exception{
  execute();var ordered=mock(TaskInvocationResults.OrderedHistory.class);var models=json.object().arrayNode();models.addObject().putArray("blocks").addObject().put("type","tool-call").put("id","actual-tool-call");when(ordered.modelResults()).thenReturn(models);
  var changed=(com.fasterxml.jackson.databind.node.ObjectNode)callPins.deepCopy();changed.put("context_manifest_id",id(90));when(ordered.toolCallPins("actual-tool-call")).thenReturn(changed);assertThrows(TenantAccessException.class,()->tx(()->receipts.requireHistory(session,ordered)));
 }
}
