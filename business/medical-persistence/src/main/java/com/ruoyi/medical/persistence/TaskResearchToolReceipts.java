package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** Independently verifies immutable original tool responses. It has no model invocation service
 * dependency, so current model context can consume history without an authority cycle. */
public final class TaskResearchToolReceipts {
 private static final String ACTION="task.research.tool.result",START="task.research.tool.started";
 private final String manifest;
 private final TaskRegisteredToolReceipts registered;
 private final com.fasterxml.jackson.databind.json.JsonMapper parser=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final JdbcTemplate db;private final TaskProfessionalSupervisor supervisor;private final FormalRuntimeCapabilityIdentityAuthority capabilities;private final TaskResearchSourceReader sources;private final TaskResearchRetrievalAuthority retrievals;private final PermissionSchema json;private final SchemaCatalogue catalogue;
 public TaskResearchToolReceipts(DataSource data,TaskProfessionalSupervisor supervisor,FormalRuntimeCapabilityIdentityAuthority capabilities,TaskResearchSourceReader sources,TaskResearchRetrievalAuthority retrievals,PermissionSchema json)throws Exception{
  this(new JdbcTemplate(data),supervisor,capabilities,sources,retrievals,json);
 }
 public TaskResearchToolReceipts(DataSource data,TaskProfessionalSupervisor supervisor,FormalRuntimeCapabilityIdentityAuthority capabilities,TaskResearchSourceReader sources,TaskResearchRetrievalAuthority retrievals,PermissionSchema json,TaskRegisteredToolReceipts registered)throws Exception{
  this(new JdbcTemplate(data),supervisor,capabilities,sources,retrievals,json,registered);
 }
 TaskResearchToolReceipts(JdbcTemplate db,TaskProfessionalSupervisor supervisor,FormalRuntimeCapabilityIdentityAuthority capabilities,TaskResearchSourceReader sources,TaskResearchRetrievalAuthority retrievals,PermissionSchema json)throws Exception{
  this(db,supervisor,capabilities,sources,retrievals,json,null);
 }
 TaskResearchToolReceipts(JdbcTemplate db,TaskProfessionalSupervisor supervisor,FormalRuntimeCapabilityIdentityAuthority capabilities,TaskResearchSourceReader sources,TaskResearchRetrievalAuthority retrievals,PermissionSchema json,TaskRegisteredToolReceipts registered)throws Exception{
  this.registered=registered;
  this.db=db;db.setQueryTimeout(5);this.supervisor=supervisor;this.capabilities=capabilities;this.sources=sources;this.retrievals=retrievals;this.json=json;catalogue=SchemaCatalogue.fromClasspath();try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-research-tool-receipt.schema.json"))){manifest=WireValues.sha256(in.readAllBytes());}
 }
 static void validateRequest(String tool,JsonNode request,SchemaCatalogue catalogue)throws Exception{
  if(!Set.of("AT-01","AT-02").contains(tool))deny();
  if(SourceEvidenceV2.requested(request.required("arguments"))){SourceEvidenceV2.validateRequest(tool,request,catalogue);return;}
  if(!Set.of("AT-01","AT-02").contains(tool))deny();ProfessionalRuntimeObservations.closed(request,Set.of("call_id","name","arguments","arguments_sha256"));
  String name="AT-01".equals(tool)?"search_sources":"read_source";if(!name.equals(text(request,"name"))||text(request,"call_id").isBlank()||text(request,"call_id").length()>256)deny();
  if(!hash(request.required("arguments")).equals(text(request,"arguments_sha256")))bad();catalogue.validate("tool-inputs",request.required("arguments"),"AT-01".equals(tool)?"SearchArgs":"ReadArgs");if(!request.required("arguments").required("cursor").isNull())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
 }
 Current require(TaskProfessionalRuntimeService.Session session,Call call,TaskInvocationService.ToolCall proof)throws Exception{
  return require(session,call,new ServiceCall(proof));
 }
 private Current require(TaskProfessionalRuntimeService.Session session,Call call,ModelCall proof)throws Exception{
  validateRequest(call.tool,call.request,catalogue);session.requireUnchanged();proof.requireUnchanged();var a=session.admission();var admitted=supervisor.requireAdmitted(session);var pins=proof.canonicalPins();same(session.sessionId(),text(pins,"session_id"));same(admitted.receiptId(),text(pins,"supervision_receipt_id"));same(admitted.sha256(),text(pins,"supervision_sha256"));same(text(call.request,"call_id"),text(pins,"tool_call_id"));same(text(call.request,"name"),text(pins,"tool_name"));same(text(call.request,"arguments_sha256"),text(pins,"arguments_sha256"));
  var input=new TaskNodeInputSchema().read(ContentReadAuthorization.canonicalBytes(session.input().manifest()));var args=call.request.required("arguments");
  if("AT-01".equals(call.tool)&&(!input.questionId().equals(text(args,"question_id"))||!input.questionText().equals(text(args,"query"))))deny();
  var selected=new ArrayList<TaskNodeInputProtocol.SourceSelection>();DatasetProtocol.EvidenceRef evidence=null;
  if("AT-02".equals(call.tool)){evidence=SourceEvidenceBindings.bind(catalogue,args.required("evidence_ref")).read();if(!input.tenantId().equals(evidence.tenantId())||!input.projectId().equals(evidence.projectId()))deny();for(var source:input.fixedSources())if(source.sourceVersion().equals(evidence.sourceVersion()))selected.add(source);if(selected.size()!=1)deny();var source=selected.get(0);if(!source.parseVersion().equals(evidence.parseVersion())||!source.approvalRevision().equals(evidence.approvalRevision()))deny();}
  else selected.addAll(input.fixedSources());
  if(selected.isEmpty())throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
  var permissions=new ArrayList<RunTokenAuthoritySchema.Permission>();for(var source:selected){var matches=input.nodePermissions().stream().filter(p->"SOURCE".equals(p.scope().resource_kind())&&source.sourceVersion().equals(p.scope().resource_id())&&"source.read".equals(p.action())&&source.purpose().equals(p.purpose())).toList();permissions.add(one(matches));}
  var replay=one(db.queryForList("SELECT replay_manifest,classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' AND status='RUNNING' AND ended_at IS NULL AND dispatch_closed_at IS NULL LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),a.actorId()));
  var release=parser.readTree(text(replay,"replay_manifest")).required("agent_release");String inputContract=SourceEvidenceV2.inputs(args),outputContract=SourceEvidenceV2.taskOutputs(args);String expected=catalogue.schemaRef(inputContract,"AT-01".equals(call.tool)?"SearchArgs":"ReadArgs");
  var versions=new ArrayList<FormalRuntimeCapabilityIdentityAuthority.VerifiedCapability>();
  for(var node:release.required("tool_versions")){var version=capabilities.require(a,json.basis(node),"TOOL",input.projectId(),"AGENT_PUBLICATION");var definition=version.canonicalDefinition().required("request");if(expected.equals(text(definition.required("input_schema_ref"),"schema_ref"))){
    if(!catalogue.schemaDigest(inputContract).equals(text(definition.required("input_schema_ref"),"schema_sha256"))||!catalogue.schemaRef(outputContract,null).equals(text(definition.required("output_schema_ref"),"schema_ref"))||!catalogue.schemaDigest(outputContract).equals(text(definition.required("output_schema_ref"),"schema_sha256")))bad();
    var allowed=definition.required("tool_scope").required("permissions");for(var permission:permissions){boolean found=false;for(var grant:allowed)if(FormalTaskNodeAuthority.sameEvidence(json.tree(permission),grant))found=true;if(!found)deny();}versions.add(version);
  }}
  var version=one(versions);version.requireUnchanged();proof.requireUnchanged();session.requireUnchanged();return new Current(session,proof,version,List.copyOf(permissions),call,evidence,text(replay,"classification"),new BigInteger(text(release.required("limits"),"max_tool_calls")));
 }
 JsonNode save(Current current,TaskResearchToolBridge.Response response)throws Exception{
  var old=recover(current);if(old!=null)return old;var s=current.session;var source=sources.requireReceipts(s.admission(),s.input(),response.reads,response.refs).canonicalPins();var retrieval=retrievals.require(s,response.retrievals,SourceEvidenceV2.requested(current.call.request.required("arguments"))).canonicalPins();
  var value=json.object().put("schema_version",SourceEvidenceV2.requested(current.call.request.required("arguments"))?"2.0.0":"1.0.0").put("kind","TASK_READ_RESULT").put("action_id",response.action).put("tool_id",current.call.tool).put("status","SUCCEEDED").put("summary","Exact authorized read; not question completion").putNull("next_cursor");value.set("payload",response.payload);value.putObject("coverage").put("requested_scope_sha256",response.requested).put("delivered_scope_sha256",response.delivered).put("complete",true);
  var receipt=value.putObject("read_receipt");if("AT-01".equals(current.call.tool)){var pin=single(retrieval);receipt.put("kind","RETRIEVAL_OPERATION").put("receipt_id",text(pin,"operation_id")).put("receipt_sha256",text(pin,"operation_receipt_sha256"));}else{var pin=single(source);receipt.put("kind","SOURCE_READ").put("receipt_id",text(pin,"read_receipt_id")).put("receipt_sha256",text(pin,"receipt_sha256"));}
  validateResponse(current,response.payload,response.reads,response.retrievals,response.refs,value,source,retrieval);catalogue.validate(SourceEvidenceV2.taskResponse(value),value,null);String id=receiptId(s.sessionId(),text(current.call.request,"call_id"));var body=json.object().put("schema_version","1.0.0").put("kind","TASK_TOOL_READ_RECEIPT").put("receipt_id",id).put("session_id",s.sessionId()).put("input_id",s.input().inputId()).put("input_sha256",s.input().inputSha256()).put("input_receipt_sha256",s.input().receiptSha256()).put("tool_version",current.version.version().version_id()).put("tool_version_sha256",current.version.version().content_sha256()).put("response_sha256",hash(value));body.set("binding",s.binding());body.set("tool_call",current.tool.canonicalPins());body.set("request",current.call.request);body.set("response",value);body.set("source_receipts",source);body.set("retrieval_receipts",retrieval);body.set("read_receipt_ids",json.tree(response.reads));body.set("retrieval_ids",json.tree(response.retrievals));body.set("selected_refs",json.tree(response.refs));
  var document=json.object().put("receipt_sha256",hash(body));document.set("body",body);String canonical=json.canonical(document);var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
  changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',NULL,?,?,'1.0.0',?,?,?,?)",s.input().tenantId(),s.input().projectId(),id,s.admission().actorId(),ACTION,canonical,response.action,s.runId(),manifest,s.admission().actorId(),now,current.classification));
  changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",s.input().tenantId(),id,s.runId(),ACTION,canonical,now));return Objects.requireNonNull(recover(current));
 }
 JsonNode recover(Current current)throws Exception{
  var s=current.session;String id=receiptId(s.sessionId(),text(current.call.request,"call_id"));var rows=db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action=? AND subject_id=? AND created_by=? AND created_at<=UTC_TIMESTAMP(6) LIMIT 2 FOR UPDATE",s.input().tenantId(),s.input().projectId(),id,ACTION,s.admission().actorId(),s.admission().actorId());if(rows.isEmpty())return null;var row=one(rows);requireStarted(current,false);var document=parser.readTree(text(row,"resource_ref"));ProfessionalRuntimeObservations.closed(document,Set.of("body","receipt_sha256"));var body=document.required("body");if(!hash(body).equals(text(document,"receipt_sha256")))bad();
  ProfessionalRuntimeObservations.closed(body,Set.of("schema_version","kind","receipt_id","session_id","input_id","input_sha256","input_receipt_sha256","tool_version","tool_version_sha256","response_sha256","binding","tool_call","request","response","source_receipts","retrieval_receipts","read_receipt_ids","retrieval_ids","selected_refs"));
  for(var pin:Map.of("schema_version","1.0.0","kind","TASK_TOOL_READ_RECEIPT","receipt_id",id,"session_id",s.sessionId(),"input_id",s.input().inputId(),"input_sha256",s.input().inputSha256(),"input_receipt_sha256",s.input().receiptSha256(),"tool_version",current.version.version().version_id(),"tool_version_sha256",current.version.version().content_sha256()).entrySet())same(pin.getValue(),text(body,pin.getKey()));
  same(s.binding(),body.required("binding"));same(current.tool.canonicalPins(),body.required("tool_call"));same(current.call.request,body.required("request"));same(hash(body.required("response")),text(body,"response_sha256"));catalogue.validate(SourceEvidenceV2.taskOutputs(current.call.request.required("arguments")),body.required("response"),null);
  var refs=new ArrayList<DatasetProtocol.EvidenceRef>();for(var ref:body.required("selected_refs"))refs.add(SourceEvidenceBindings.bind(catalogue,ref).read());var sourcePins=sources.requireReceipts(s.admission(),s.input(),ids(body.required("read_receipt_ids")),refs).canonicalPins();var retrievalPins=retrievals.require(s,ids(body.required("retrieval_ids")),SourceEvidenceV2.requested(current.call.request.required("arguments"))).canonicalPins();same(body.required("source_receipts"),sourcePins);same(body.required("retrieval_receipts"),retrievalPins);validateResponse(current,body.required("response").required("payload"),ids(body.required("read_receipt_ids")),ids(body.required("retrieval_ids")),refs,body.required("response"),sourcePins,retrievalPins);
  same("SUCCESS",row.get("result"));if(row.get("reason_code")!=null)bad();same(s.runId(),row.get("trace_id"));same(text(body.required("response"),"action_id"),row.get("request_id"));same("1.0.0",row.get("schema_version"));same(manifest,row.get("schema_manifest_sha256"));same(current.classification,row.get("classification"));
  var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type=? LIMIT 2",s.input().tenantId(),id,s.runId(),ACTION));same(document,parser.readTree(text(event,"payload")));current.tool.requireUnchanged();current.version.requireUnchanged();s.requireUnchanged();return body.required("response").deepCopy();
 }
 /** Actual original C07 call capabilities are required for history. Caller history bytes are never accepted. */
 public History requireHistory(TaskProfessionalRuntimeService.Session session,List<TaskInvocationService.ToolCall> calls)throws Exception{
  if(calls==null||calls.isEmpty()||calls.size()>256)deny();return requireModelHistory(session,calls.stream().map(c->(ModelCall)new ServiceCall(c)).toList());
 }
 /** The factual model reader is private-constructor evidence, not SDK supplied history. */
 public History requireHistory(TaskProfessionalRuntimeService.Session session,TaskInvocationResults.OrderedHistory history)throws Exception{
  history.requireFor(session);var calls=new ArrayList<ModelCall>();for(var result:history.modelResults())for(var block:result.required("blocks"))if("tool-call".equals(text(block,"type")))calls.add(new OrderedCall(history,text(block,"id")));
  return requireModelHistory(session,calls);
 }
 private History requireModelHistory(TaskProfessionalRuntimeService.Session session,List<ModelCall> calls)throws Exception{
  if(registered!=null)registered.requireNoPendingQuestion(session);
  session.requireUnchanged();if(calls.size()>256)deny();var response=json.object().arrayNode();var pins=json.object().arrayNode();var seen=new HashSet<String>();
  for(var proof:calls){proof.requireUnchanged();var call=proof.canonicalPins();String id=receiptId(session.sessionId(),text(call,"tool_call_id"));if(!seen.add(id))bad();
   if(Set.of("read_artifact","load_skill","ask_user_question","propose_web_acquisition").contains(text(call,"tool_name"))){
    if(registered==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    var recorded=registered.history(session,new TaskRegisteredToolAuthority.CallProof(){public void requireUnchanged()throws Exception{proof.requireUnchanged();}public JsonNode canonicalPins()throws Exception{return proof.canonicalPins();}});
    var entry=response.addObject().put("invocation_id",text(call,"invocation_id")).put("tool_call_id",text(call,"tool_call_id"));entry.set("response",recorded.response());pins.add(recorded.pin());continue;
   }
   var row=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action=? AND subject_id=? AND created_by=? LIMIT 2 FOR UPDATE",session.input().tenantId(),session.input().projectId(),id,ACTION,session.admission().actorId(),session.admission().actorId()));var original=parser.readTree(text(row,"resource_ref"));var body=original.required("body");var request=body.required("request");String name=text(request,"name"),tool="search_sources".equals(name)?"AT-01":"read_source".equals(name)?"AT-02":"UNSUPPORTED";validateRequest(tool,request,catalogue);
   var value=recover(require(session,new Call(tool,request),proof));if(value==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);var entry=response.addObject().put("invocation_id",text(call,"invocation_id")).put("tool_call_id",text(call,"tool_call_id"));entry.set("response",value);var pin=pins.addObject().put("tool_receipt_id",id).put("tool_receipt_sha256",text(original,"receipt_sha256")).put("response_sha256",hash(value));pin.set("tool_call",call);
  }
  session.requireUnchanged();return new History(session,List.copyOf(calls),response,pins);
 }
 public final class History {
  private final TaskProfessionalRuntimeService.Session session;private final List<ModelCall> calls;private final JsonNode responses,pins;
  private History(TaskProfessionalRuntimeService.Session session,List<ModelCall> calls,JsonNode responses,JsonNode pins){this.session=session;this.calls=calls;this.responses=responses.deepCopy();this.pins=pins.deepCopy();}
  public void requireUnchanged()throws Exception{var fresh=requireModelHistory(session,calls);same(responses,fresh.responses);same(pins,fresh.pins);}
  public JsonNode canonicalResponses()throws Exception{requireUnchanged();return responses.deepCopy();}
  public JsonNode canonicalPins()throws Exception{requireUnchanged();return pins.deepCopy();}
 }
 void requireStarted(Current current,boolean create)throws Exception{
  var s=current.session;String id=UUID.nameUUIDFromBytes(("TASK_TOOL_STARTED|"+s.sessionId()+"|"+text(current.call.request,"call_id")).getBytes(StandardCharsets.UTF_8)).toString();
  var body=json.object().put("schema_version","1.0.0").put("kind","TASK_TOOL_READ_STARTED").put("receipt_id",id).put("session_id",s.sessionId()).put("input_id",s.input().inputId()).put("input_sha256",s.input().inputSha256()).put("input_receipt_sha256",s.input().receiptSha256()).put("tool_version",current.version.version().version_id()).put("tool_version_sha256",current.version.version().content_sha256());body.set("binding",s.binding());body.set("tool_call",current.tool.canonicalPins());body.set("request",current.call.request);
  var document=json.object().put("receipt_sha256",hash(body));document.set("body",body);var rows=db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action=? AND subject_id=? AND created_by=? AND result='SUCCESS' AND reason_code IS NULL AND trace_id=? AND request_id=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? LIMIT 2 FOR UPDATE",s.input().tenantId(),s.input().projectId(),id,START,s.admission().actorId(),s.admission().actorId(),s.runId(),id,manifest,current.classification);
  if(rows.isEmpty()){
   if(!create)bad();var count=one(db.query("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND project_id=? AND trace_id=? AND action=?",(r,n)->r.getBigDecimal(1).toBigIntegerExact(),s.input().tenantId(),s.input().projectId(),s.runId(),START));
   if(current.maximumToolCalls.signum()<1||count.compareTo(current.maximumToolCalls)>=0)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
   var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));String canonical=json.canonical(document);
   changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',NULL,?,?,'1.0.0',?,?,?,?)",s.input().tenantId(),s.input().projectId(),id,s.admission().actorId(),START,canonical,id,s.runId(),manifest,s.admission().actorId(),now,current.classification));
   changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",s.input().tenantId(),id,s.runId(),START,canonical,now));requireStarted(current,false);return;
  }
  same(document,parser.readTree(text(one(rows),"resource_ref")));var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type=? LIMIT 2",s.input().tenantId(),id,s.runId(),START));same(document,parser.readTree(text(event,"payload")));current.tool.requireUnchanged();s.requireUnchanged();
 }
 private void validateResponse(Current current,JsonNode payload,List<String> reads,List<String> searches,List<DatasetProtocol.EvidenceRef> refs,JsonNode value,JsonNode source,JsonNode retrieval)throws Exception{
  requireAction(current,value,reads);validatePayload(current.call.tool,current.call.request,current.evidence,payload,reads,searches,refs,value,source,retrieval,retrievals.require(current.session,searches,SourceEvidenceV2.requested(current.call.request.required("arguments"))).evidenceRefs());
 }
 private void validatePayload(String tool,JsonNode request,DatasetProtocol.EvidenceRef expectedEvidence,JsonNode payload,List<String> reads,List<String> searches,List<DatasetProtocol.EvidenceRef> refs,JsonNode value,JsonNode source,JsonNode retrieval,JsonNode actualRefs)throws Exception{
  if(!tool.equals(text(value,"tool_id")))bad();same("Exact authorized read; not question completion",text(value,"summary"));var pin=value.required("read_receipt");String scope;
  if("AT-02".equals(tool)){
   if(reads.size()!=1||refs.size()!=1||!searches.isEmpty())bad();same(json.tree(expectedEvidence),payload.required("evidence_ref"));same(json.tree(refs.get(0)),payload.required("evidence_ref"));same(reads.get(0),text(payload,"read_receipt_id"));
   same(refs.get(0).quoteHash(),WireValues.sha256(text(payload,"content").getBytes(StandardCharsets.UTF_8)));var actual=single(source);same("SOURCE_READ",text(pin,"kind"));same(text(actual,"read_receipt_id"),text(pin,"receipt_id"));same(text(actual,"receipt_sha256"),text(pin,"receipt_sha256"));scope=hash(refs.get(0));
  }else{
   if(searches.size()!=1||!reads.isEmpty()||!refs.isEmpty())bad();same(searches.get(0),text(payload,"retrieval_id"));var actual=single(retrieval);same("RETRIEVAL_OPERATION",text(pin,"kind"));same(text(actual,"operation_id"),text(pin,"receipt_id"));same(text(actual,"operation_receipt_sha256"),text(pin,"receipt_sha256"));
   same(actualRefs,payload.required("evidence_refs"));same(actualRefs.isEmpty()?"EMPTY":"FOUND",text(payload,"match_status"));if(!payload.required("missing_requirement_ids").isEmpty())bad();scope=hash(request.required("arguments"));
  }
  same(scope,text(value.required("coverage"),"requested_scope_sha256"));same(scope,text(value.required("coverage"),"delivered_scope_sha256"));
 }
 private void requireAction(Current current,JsonNode response,List<String> readIds)throws Exception{
  var s=current.session;var i=s.input();requireAction(new ActionScope(i.tenantId(),i.projectId(),s.runId(),s.stepId(),s.sessionId(),s.generation(),s.admission().actorId(),i.inputSha256()),current.call,current.evidence,current.version.version().version_id(),current.tool.canonicalPins(),response,readIds);
 }
 private record ActionScope(String tenant,String project,String run,String step,String session,String generation,String actor,String inputHash){}
 private void requireAction(ActionScope s,Call call,DatasetProtocol.EvidenceRef evidence,String toolVersion,JsonNode toolPins,JsonNode response,List<String> readIds)throws Exception{
  String operation="AT-01".equals(call.tool)?"AT-01:create":"AT-02:read";String target="AT-01".equals(call.tool)?text(call.request.required("arguments"),"question_id"):TaskResearchSourceReader.requestSha256(evidence,null);
  String arguments=TaskResearchToolAccess.argumentsSha256(operation,target);String expected=UUID.nameUUIDFromBytes(("TASK_READ_TOOL|"+s.session+"|"+text(call.request,"call_id")+"|"+operation+"|"+arguments).getBytes(StandardCharsets.UTF_8)).toString();same(expected,text(response,"action_id"));
  var model=one(db.queryForList("SELECT action_id FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND invocation_id=? AND kind='MODEL_CALL' AND lease_generation=? LIMIT 2 FOR UPDATE",s.tenant,s.project,s.run,s.step,text(toolPins,"invocation_id"),s.generation));
  var row=one(db.queryForList("SELECT action_id FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND input_sha256=? AND origin_run_id=? AND origin_step_id=? AND origin_action_id=? AND kind=? AND arguments_sha256=? AND tool_version=? AND state='BOUND' AND lease_generation=? AND created_by=? LIMIT 2 FOR UPDATE",s.tenant,s.project,s.run,s.step,expected,s.inputHash,s.run,s.step,text(model,"action_id"),call.tool,arguments,toolVersion,s.generation,s.actor));same(expected,text(row,"action_id"));
  for(String id:readIds){var original=one(db.queryForList("SELECT request_id FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action=? AND request_id=? AND subject_id=? AND created_by=? AND result='SUCCESS' LIMIT 2",s.tenant,s.project,id,TaskResearchSourceReader.ACTION,expected,s.actor,s.actor));same(expected,text(original,"request_id"));}
 }
 /** Integrity-only historical path. The caller separately verifies the actual076 ordered
  * MODEL_RESULT/tool-call chain and current transmission authority. No session or read grant is minted. */
 public void requireOriginalHistory(TaskResearchCompletionAuthority.Input input,JsonNode responses,JsonNode pins)throws Exception{
  input.requireUnchanged();if(!responses.isArray()||!pins.isArray()||responses.size()!=pins.size()||responses.size()>256)bad();
  if(responses.isEmpty()){input.requireUnchanged();return;}
  var fixed=new TaskNodeInputSchema().read(ContentReadAuthorization.canonicalBytes(input.manifest()));
  var run=one(db.queryForList("SELECT replay_manifest,classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' LIMIT 2",input.tenantId(),input.projectId(),input.runId(),input.actorId()));var replay=parser.readTree(text(run,"replay_manifest"));same(fixed.replaySha256(),hash(replay));String classification=text(run,"classification");
  var session=one(db.queryForList("SELECT canonical_start,start_sha256 FROM professional_runtime_session WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND session_id=? AND action_id=? AND input_sha256=? AND lease_generation=? AND service_uri=? AND created_by=? AND admission_state='CLOSED' LIMIT 2",input.tenantId(),input.projectId(),input.runId(),input.stepId(),input.sessionId(),input.actionId(),input.inputSha256(),input.generation(),input.workerService(),input.actorId()));
  var start=parser.readTree(text(session,"canonical_start"));same(text(session,"start_sha256"),hash(start));same(input.sessionId(),text(start,"session_id"));var binding=start.required("binding");var bound=ProfessionalRuntimeObservations.jsonBinding(binding);same(input.tenantId(),bound.tenant_id());same(input.runId(),bound.run_id());same(input.stepId(),bound.step_id());same(input.actionId(),bound.action_id());same(input.generation(),bound.lease_generation());same(input.inputSha256(),bound.input_sha256());
  var seen=new HashSet<String>();for(int at=0;at<responses.size();at++){
   if(Set.of("AT-03","AT-04","AT-08","AT-09").contains(responses.get(at).required("response").path("tool_id").asText())){
    if(registered==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);var pin=pins.get(at);
    if(!seen.add(text(pin,"tool_receipt_id")))bad();registered.requireOriginalHistory(input,responses.get(at),pin);continue;
   }
   var response=responses.get(at);var pin=pins.get(at);ProfessionalRuntimeObservations.closed(response,Set.of("invocation_id","tool_call_id","response"));ProfessionalRuntimeObservations.closed(pin,Set.of("tool_receipt_id","tool_receipt_sha256","response_sha256","tool_call"));var toolCall=pin.required("tool_call");String callId=text(toolCall,"tool_call_id"),id=receiptId(input.sessionId(),callId);if(!seen.add(id))bad();same(id,text(pin,"tool_receipt_id"));same(callId,text(response,"tool_call_id"));same(text(toolCall,"invocation_id"),text(response,"invocation_id"));same(input.sessionId(),text(toolCall,"session_id"));same(text(start,"isolation_receipt_id"),text(toolCall,"supervision_receipt_id"));same(text(start,"supervision_sha256"),text(toolCall,"supervision_sha256"));
   var value=response.required("response");catalogue.validate(SourceEvidenceV2.taskResponse(value),value,null);same(hash(value),text(pin,"response_sha256"));
   var document=originalAudit(input,id,ACTION,text(value,"action_id"),classification);same(text(pin,"tool_receipt_sha256"),text(document,"receipt_sha256"));var body=document.required("body");
   ProfessionalRuntimeObservations.closed(body,Set.of("schema_version","kind","receipt_id","session_id","input_id","input_sha256","input_receipt_sha256","tool_version","tool_version_sha256","response_sha256","binding","tool_call","request","response","source_receipts","retrieval_receipts","read_receipt_ids","retrieval_ids","selected_refs"));
   for(var expected:Map.of("schema_version","1.0.0","kind","TASK_TOOL_READ_RECEIPT","receipt_id",id,"session_id",input.sessionId(),"input_id",input.inputId(),"input_sha256",input.inputSha256(),"input_receipt_sha256",input.receiptSha256(),"response_sha256",hash(value)).entrySet())same(expected.getValue(),text(body,expected.getKey()));same(binding,body.required("binding"));same(toolCall,body.required("tool_call"));same(value,body.required("response"));
   var request=body.required("request");String tool=text(value,"tool_id");validateRequest(tool,request,catalogue);same(callId,text(request,"call_id"));same(text(toolCall,"tool_name"),text(request,"name"));same(text(toolCall,"arguments_sha256"),text(request,"arguments_sha256"));
   DatasetProtocol.EvidenceRef evidence=null;var selected=new ArrayList<TaskNodeInputProtocol.SourceSelection>();if("AT-01".equals(tool)){same(fixed.questionId(),text(request.required("arguments"),"question_id"));same(fixed.questionText(),text(request.required("arguments"),"query"));selected.addAll(fixed.fixedSources());}else{evidence=SourceEvidenceBindings.bind(catalogue,request.required("arguments").required("evidence_ref")).read();same(input.tenantId(),evidence.tenantId());same(input.projectId(),evidence.projectId());for(var source:fixed.fixedSources())if(source.sourceVersion().equals(evidence.sourceVersion())&&source.parseVersion().equals(evidence.parseVersion())&&source.approvalRevision().equals(evidence.approvalRevision()))selected.add(source);if(selected.size()!=1)bad();}if(selected.isEmpty())bad();
   String inputContract=SourceEvidenceV2.inputs(request.required("arguments")),outputContract=SourceEvidenceV2.taskOutputs(request.required("arguments"));catalogue.validate(outputContract,value,null);var versions=new ArrayList<FormalRuntimeCapabilityIdentityAuthority.RegisteredIdentity>();for(var node:replay.required("agent_release").required("tool_versions")){var version=capabilities.requireRegisteredIdentity(input.tenantId(),input.actorId(),json.basis(node),"TOOL",input.projectId());var definition=version.canonicalDefinition().required("request");if(catalogue.schemaRef(inputContract,"AT-01".equals(tool)?"SearchArgs":"ReadArgs").equals(text(definition.required("input_schema_ref"),"schema_ref"))){same(catalogue.schemaDigest(inputContract),text(definition.required("input_schema_ref"),"schema_sha256"));same(catalogue.schemaRef(outputContract,null),text(definition.required("output_schema_ref"),"schema_ref"));same(catalogue.schemaDigest(outputContract),text(definition.required("output_schema_ref"),"schema_sha256"));for(var source:selected){var permission=one(fixed.nodePermissions().stream().filter(p->"SOURCE".equals(p.scope().resource_kind())&&source.sourceVersion().equals(p.scope().resource_id())&&"source.read".equals(p.action())&&source.purpose().equals(p.purpose())).toList());boolean allowed=false;for(var grant:definition.required("tool_scope").required("permissions"))if(FormalTaskNodeAuthority.sameEvidence(json.tree(permission),grant))allowed=true;if(!allowed)bad();}versions.add(version);}}
   var version=one(versions);same(version.version().version_id(),text(body,"tool_version"));same(version.version().content_sha256(),text(body,"tool_version_sha256"));
   String startedId=UUID.nameUUIDFromBytes(("TASK_TOOL_STARTED|"+input.sessionId()+"|"+callId).getBytes(StandardCharsets.UTF_8)).toString();var started=originalAudit(input,startedId,START,startedId,classification).required("body");var expectedStarted=json.object();for(String field:List.of("schema_version","session_id","input_id","input_sha256","input_receipt_sha256","tool_version","tool_version_sha256","binding","tool_call","request"))expectedStarted.set(field,body.required(field));expectedStarted.put("kind","TASK_TOOL_READ_STARTED").put("receipt_id",startedId);same(expectedStarted,started);
   var refs=new ArrayList<DatasetProtocol.EvidenceRef>();for(var ref:body.required("selected_refs"))refs.add(SourceEvidenceBindings.bind(catalogue,ref).read());var reads=ids(body.required("read_receipt_ids"));var searches=ids(body.required("retrieval_ids"));var sourcePins=sources.requireReceipts(input.admission(),input,reads,refs).canonicalPins();var retrieval=retrievals.requireOriginal(input,searches,SourceEvidenceV2.requested(request.required("arguments")));var retrievalPins=retrieval.canonicalPins();same(sourcePins,body.required("source_receipts"));same(retrievalPins,body.required("retrieval_receipts"));validatePayload(tool,request,evidence,value.required("payload"),reads,searches,refs,value,sourcePins,retrievalPins,retrieval.evidenceRefs());requireAction(new ActionScope(input.tenantId(),input.projectId(),input.runId(),input.stepId(),input.sessionId(),Long.toString(input.generation()),input.actorId(),input.inputSha256()),new Call(tool,request),evidence,version.version().version_id(),toolCall,value,reads);
  }
  input.requireUnchanged();
 }
 private JsonNode originalAudit(TaskResearchCompletionAuthority.Input input,String id,String action,String requestId,String classification)throws Exception{
  var row=one(db.queryForList("SELECT resource_ref,created_at FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action=? AND request_id=? AND trace_id=? AND subject_id=? AND created_by=? AND result='SUCCESS' AND reason_code IS NULL AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? AND created_at<=UTC_TIMESTAMP(6) LIMIT 2",input.tenantId(),input.projectId(),id,action,requestId,input.runId(),input.actorId(),input.actorId(),manifest,classification));var document=parser.readTree(text(row,"resource_ref"));ProfessionalRuntimeObservations.closed(document,Set.of("body","receipt_sha256"));same(hash(document.required("body")),text(document,"receipt_sha256"));same(json.canonical(document),text(row,"resource_ref"));
  var event=one(db.queryForList("SELECT payload,occurred_at FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type=? LIMIT 2",input.tenantId(),id,input.runId(),action));same(document,parser.readTree(text(event,"payload")));same(row.get("created_at"),event.get("occurred_at"));return document;
 }
 interface ModelCall {void requireUnchanged()throws Exception;JsonNode canonicalPins()throws Exception;}
 JsonNode requireModelSend(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,JsonNode pins,String purpose,String provider)throws Exception{
  if(registered==null){for(var pin:pins)if(Set.of("read_artifact","load_skill","ask_user_question","propose_web_acquisition").contains(text(pin.required("tool_call"),"tool_name")))throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return json.object().arrayNode();}
  return registered.requireModelSend(a,input,pins,purpose,provider);
 }
 private record ServiceCall(TaskInvocationService.ToolCall proof) implements ModelCall {
  public void requireUnchanged()throws Exception{proof.requireUnchanged();}public JsonNode canonicalPins()throws Exception{return proof.canonicalPins();}
 }
 private record OrderedCall(TaskInvocationResults.OrderedHistory history,String id) implements ModelCall {
  public void requireUnchanged()throws Exception{history.requireUnchanged();}public JsonNode canonicalPins()throws Exception{return history.toolCallPins(id);}
 }
 static record Call(String tool,JsonNode request){}
 static final class Current {
  final TaskProfessionalRuntimeService.Session session;final ModelCall tool;final FormalRuntimeCapabilityIdentityAuthority.VerifiedCapability version;final List<RunTokenAuthoritySchema.Permission> permissions;final Call call;final DatasetProtocol.EvidenceRef evidence;final String classification;final BigInteger maximumToolCalls;
  private Current(TaskProfessionalRuntimeService.Session session,ModelCall tool,FormalRuntimeCapabilityIdentityAuthority.VerifiedCapability version,List<RunTokenAuthoritySchema.Permission> permissions,Call call,DatasetProtocol.EvidenceRef evidence,String classification,BigInteger maximumToolCalls){this.session=session;this.tool=tool;this.version=version;this.permissions=permissions;this.call=call;this.evidence=evidence;this.classification=classification;this.maximumToolCalls=maximumToolCalls;}
 }
 static String receiptId(String session,String call){WireValues.uuid(session);return UUID.nameUUIDFromBytes(("TASK_TOOL_READ_RESULT|"+session+"|"+call).getBytes(StandardCharsets.UTF_8)).toString();}
 private static List<String> ids(JsonNode values){if(!values.isArray()||values.size()>512)bad();var result=new ArrayList<String>();for(var n:values){WireValues.uuid(n.asText());result.add(n.textValue());}return List.copyOf(result);}
 private static JsonNode single(JsonNode values){if(!values.isArray()||values.size()!=1)bad();return values.get(0);}
 private static String hash(Object n)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(n));}
 private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}private static String text(Map<String,Object> n,String key){if(!(n.get(key) instanceof String))bad();return (String)n.get(key);}
 private static <T>T one(List<T> values){if(values.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return values.get(0);}private static void same(Object a,Object b){if(a instanceof JsonNode x&&b instanceof JsonNode y){if(!FormalTaskNodeAuthority.sameEvidence(x,y))bad();}else if(!Objects.equals(a,b))bad();}private static void changed(int n){if(n!=1)bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
