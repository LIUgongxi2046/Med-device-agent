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

/** Only actual settled C07 tool calls may bind independent AT01/02 read actions. */
public final class TaskResearchToolBridge {
 private final JdbcTemplate db;private final TaskProfessionalHostAccess hosts;private final TaskProfessionalSupervisor supervisor;private final TaskInvocationService invocations;private final TaskResearchToolAccess tools;private final RunTokenAuthority tokens;private final PermissionSchema json;private final RunTokenAuthoritySchema tokenSchema;private final SchemaCatalogue catalogue;private final TaskResearchToolReceipts receipts;
 public TaskResearchToolBridge(DataSource data,TaskProfessionalHostAccess hosts,TaskProfessionalSupervisor supervisor,TaskInvocationService invocations,TaskResearchToolAccess tools,RunTokenAuthority tokens,TaskResearchToolReceipts receipts,PermissionSchema json,RunTokenAuthoritySchema tokenSchema)throws Exception{
  this(new JdbcTemplate(data),hosts,supervisor,invocations,tools,tokens,receipts,json,tokenSchema);
 }
 TaskResearchToolBridge(JdbcTemplate db,TaskProfessionalHostAccess hosts,TaskProfessionalSupervisor supervisor,TaskInvocationService invocations,TaskResearchToolAccess tools,RunTokenAuthority tokens,TaskResearchToolReceipts receipts,PermissionSchema json,RunTokenAuthoritySchema tokenSchema)throws Exception{
  this.db=db;db.setQueryTimeout(5);this.hosts=hosts;this.supervisor=supervisor;this.invocations=invocations;this.tools=tools;this.tokens=tokens;this.receipts=receipts;this.json=json;this.tokenSchema=tokenSchema;catalogue=SchemaCatalogue.fromClasspath();
 }
 public JsonNode forward(HttpServletRequest http,TaskProfessionalHostAccess.Handle host,String sessionId,String toolId,JsonNode request)throws Exception{
  TaskResearchToolReceipts.validateRequest(toolId,request,catalogue);var call=new TaskResearchToolReceipts.Call(toolId,request.deepCopy());
  var existing=hosts.execute(host,a->ThrowingSupplier.of(()->receipts.recover(current(host,a,sessionId,call))).get());if(existing!=null)return existing;
  Response result="AT-01".equals(toolId)?search(http,host,sessionId,call):read(http,host,sessionId,call);
  return hosts.execute(host,a->ThrowingSupplier.of(()->receipts.save(current(host,a,sessionId,call),result)).get());
 }
 private TaskResearchToolReceipts.Current current(TaskProfessionalHostAccess.Handle host,EnterpriseAccess.Admission a,String id,TaskResearchToolReceipts.Call call)throws Exception{
  var session=hosts.session(host,a,id);var admitted=supervisor.requireAdmitted(session);var proof=invocations.toolCall(session,admitted,text(call.request(),"call_id"),text(call.request(),"name"),call.request().required("arguments"));return receipts.require(session,call,proof);
 }
 private Bound bind(TaskProfessionalHostAccess.Handle host,String session,TaskResearchToolReceipts.Call call,String operation,String target)throws Exception{
  return hosts.execute(host,a->ThrowingSupplier.of(()->{var current=current(host,a,session,call);receipts.requireStarted(current,true);var s=current.session;var input=s.input();var permission=current.permissions.get(0);String arguments=TaskResearchToolAccess.argumentsSha256(operation,target);
   String id=UUID.nameUUIDFromBytes(("TASK_READ_TOOL|"+session+"|"+text(call.request(),"call_id")+"|"+operation+"|"+arguments).getBytes(StandardCharsets.UTF_8)).toString();
   var model=one(db.queryForList("SELECT action_id FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND invocation_id=? AND kind='MODEL_CALL' AND lease_generation=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),s.runId(),s.stepId(),text(current.tool.canonicalPins(),"invocation_id"),s.generation()));
   var rows=db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),s.runId(),s.stepId(),id);
   String toolVersion=current.version.version().version_id();
   if(rows.isEmpty()){var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));changed(db.update("INSERT INTO step_action_binding(tenant_id,project_id,run_id,step_id,action_id,input_sha256,origin_run_id,origin_step_id,origin_action_id,kind,arguments_sha256,tool_version,state,lease_generation,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'BOUND',?,'1.0.0',?,?,?,?,1,?)",input.tenantId(),input.projectId(),s.runId(),s.stepId(),id,input.inputSha256(),s.runId(),s.stepId(),text(model,"action_id"),call.tool(),arguments,toolVersion,s.generation(),tokenSchema.manifestSha256(),a.actorId(),now,current.classification,now));}
   var row=one(db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),s.runId(),s.stepId(),id));
   for(var pin:Map.ofEntries(Map.entry("input_sha256",input.inputSha256()),Map.entry("origin_run_id",s.runId()),Map.entry("origin_step_id",s.stepId()),Map.entry("origin_action_id",text(model,"action_id")),Map.entry("kind",call.tool()),Map.entry("arguments_sha256",arguments),Map.entry("tool_version",toolVersion),Map.entry("state","BOUND"),Map.entry("created_by",a.actorId()),Map.entry("schema_version","1.0.0"),Map.entry("schema_manifest_sha256",tokenSchema.manifestSha256()),Map.entry("classification",current.classification)).entrySet())same(pin.getValue(),row.get(pin.getKey()));same(s.generation(),String.valueOf(row.get("lease_generation")));if(row.get("invocation_id")!=null)bad();
   current.tool.requireUnchanged();s.requireUnchanged();return new Bound(input.tenantId(),a.actorId(),new RunTokenAuthority.ActionRequest(s.runId(),s.stepId(),id,new BigInteger(s.generation()),input.inputSha256(),arguments,call.tool(),toolVersion,permission.scope(),permission.action(),permission.purpose()),Duration.ofSeconds(s.deployment().value().required("token_lifetime_seconds").longValue()));
  }).get());
 }
 private TaskResearchToolAccess.Handle open(HttpServletRequest http,Bound bound)throws Exception{var token=tokens.issue(http,new RunTokenAuthority.IssueRequest(bound.tenant,bound.actor,bound.action.runId(),bound.action.stepId(),bound.action.generation(),List.of("source.read"),bound.lifetime));return tools.open(http,token.token(),bound.action);}
 private Response search(HttpServletRequest http,TaskProfessionalHostAccess.Handle host,String session,TaskResearchToolReceipts.Call call)throws Exception{
  var question=call.request().required("arguments");var created=bind(host,session,call,"AT-01:create",text(question,"question_id"));var operation=tools.createRetrieval(open(http,created));String retrieval=text(operation.required("data"),"resource_id");WireValues.uuid(retrieval);var refs=json.object().arrayNode();int page=1,total=-1;
  while(true){if(page>10)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);var bound=bind(host,session,call,"AT-01:read",retrieval+":"+page+":100");var response=tools.readRetrieval(open(http,bound),retrieval,new RetrievalProtocol.PageQuery(page,100)).required("data");var info=response.required("page_info");int actual=new BigInteger(text(info,"total")).intValueExact();if(actual<0||actual>1000||total>=0&&total!=actual)bad();total=actual;
   for(var hit:response.required("items")){var evidence=json.object().put("schema_version",SourceEvidenceV2.requested(question)?"2.0.0":"1.0.0").put("tenant_id",created.tenant).put("project_id",text(response,"project_id"));for(String f:List.of("source_id","source_version","parse_version","chunk_id","locator_kind","locator","quote_hash","approval_revision"))evidence.set(f,hit.required(f));SourceEvidenceBindings.bind(catalogue,evidence).read();refs.add(evidence);}
   if(info.required("next_page").isNull())break;if(info.required("next_page").intValue()!=++page)bad();
  }
  if(refs.size()!=total)bad();var payload=json.object().put("kind","SEARCH").put("retrieval_id",retrieval).put("match_status",refs.isEmpty()?"EMPTY":"FOUND");payload.set("evidence_refs",refs);payload.putArray("missing_requirement_ids");return new Response(created.action.actionId(),payload,List.of(retrieval),List.of(),List.of(),hash(question),hash(question));
 }
 private Response read(HttpServletRequest http,TaskProfessionalHostAccess.Handle host,String session,TaskResearchToolReceipts.Call call)throws Exception{
  var evidence=SourceEvidenceBindings.bind(catalogue,call.request().required("arguments").required("evidence_ref")).read();var bound=bind(host,session,call,"AT-02:read",TaskResearchSourceReader.requestSha256(evidence,null));var read=tools.readSource(open(http,bound),evidence,null);
  var payload=json.object().put("kind","SOURCE").put("content",read.content()).put("read_receipt_id",read.readReceiptId());payload.set("evidence_ref",json.tree(read.evidenceRef()));if(!read.complete())bad();return new Response(bound.action.actionId(),payload,List.of(),List.of(read.readReceiptId()),List.of(evidence),read.requestedScopeSha256(),read.deliveredScopeSha256());
 }
 private record Bound(String tenant,String actor,RunTokenAuthority.ActionRequest action,Duration lifetime){}
 static final class Response {
  final String action;final JsonNode payload;final List<String> retrievals,reads;final List<DatasetProtocol.EvidenceRef> refs;final String requested,delivered;
  private Response(String action,JsonNode payload,List<String> retrievals,List<String> reads,List<DatasetProtocol.EvidenceRef> refs,String requested,String delivered){this.action=action;this.payload=payload.deepCopy();this.retrievals=List.copyOf(retrievals);this.reads=List.copyOf(reads);this.refs=List.copyOf(refs);this.requested=requested;this.delivered=delivered;}
 }
 private static String hash(Object n)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(n));}
 private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}private static String text(Map<String,Object> n,String key){if(!(n.get(key) instanceof String))bad();return (String)n.get(key);}
 private static <T>T one(List<T> values){if(values.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return values.get(0);}private static void same(Object a,Object b){if(a instanceof JsonNode x&&b instanceof JsonNode y){if(!FormalTaskNodeAuthority.sameEvidence(x,y))bad();}else if(!Objects.equals(a,b))bad();}private static void changed(int n){if(n!=1)bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
