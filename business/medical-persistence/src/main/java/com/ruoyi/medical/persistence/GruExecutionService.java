package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Separate execution ledger: historical RECORD_ONLY messages and their exports never change meaning.
 * No model-provided confirmation, project-fact write, inferred permission or fabricated provider receipt. */
@Service
@ConditionalOnProperty(name="medical.gru-records.enabled",havingValue="true")
public final class GruExecutionService {
 private final GruExecutionSchema contract;private final GruPresentationMemory preferences;
 private final EnterpriseAccess access; private final GruService records; private final GruContextAuthority authority;
 private final JdbcTemplate db; private final PermissionSchema json; private final PermissionMapper permissions;
 private final RetrievalService retrieval; private final TaskService tasks; private final RunCreateService starts;
 private final RunReadService runs; private final RunCancellationService cancellations;
 private final ObjectProvider<DocumentTechnicalCheckService> checks;
 private final ObjectProvider<DocumentCandidateAdoptionEntryService> adoption;
 private final JsonMapper codec=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 public GruExecutionService(EnterpriseAccess access,GruService records,GruContextAuthority authority,
  @Qualifier("masterDataSource") DataSource data,PermissionSchema json,PermissionMapper permissions,RetrievalService retrieval,
  TaskService tasks,RunCreateService starts,RunReadService runs,RunCancellationService cancellations,
  ObjectProvider<DocumentTechnicalCheckService> checks,ObjectProvider<DocumentCandidateAdoptionEntryService> adoption,GruPresentationMemory preferences)throws Exception{this.preferences=preferences;contract=new GruExecutionSchema(json);
  this.access=access;this.records=records;this.authority=authority;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;
  this.permissions=permissions;this.retrieval=retrieval;this.tasks=tasks;this.starts=starts;this.runs=runs;
  this.cancellations=cancellations;this.checks=checks;this.adoption=adoption;
 }
 public JsonNode parse(byte[] bytes)throws Exception {if(bytes.length==0||bytes.length>65536)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return codec.readTree(bytes);}
 public JsonNode propose(ApplicationSessionIdentity.Verified user,String context,String conversation,String key,JsonNode request)throws Exception {
  contract.validate("Request",request);closed(request,"schema_version","intent","text","target_version","parameters");version(request);SimulationInputArtifactSchema.key(key);
  String intent=text(request,"intent");if(!Set.of("QUERY","RETRIEVE","CHECK","PROPOSE_EDIT","ADOPT_EDIT").contains(intent))invalid();
  String message=text(request,"text");if(message.isBlank()||message.length()>8000)invalid();
  var proof=records.exportProof(user,context,conversation,null);same(proof.context().documentRevision(),text(request,"target_version"));
  validateParameters(request,proof);
  return tx(user,a->{guard(a,context,proof,true);
   if("ADOPT_EDIT".equals(intent)){var params=request.required("parameters");var saved=one(db.queryForList("SELECT arguments_json,arguments_sha256,document_id,base_document_revision FROM document_adoption_preview WHERE tenant_id=? AND created_by=? AND candidate_id=? AND pending_action_id=?",a.context().tenant_id(),a.actorId(),text(params,"candidate_id"),text(params.required("confirmation"),"pending_action_id")));same(read(str(saved,"arguments_json")),params.required("arguments"));same(hash(params.required("arguments")),str(saved,"arguments_sha256"));same(str(saved,"arguments_sha256"),text(params.required("confirmation"),"arguments_sha256"));same(proof.context().documentId(),str(saved,"document_id"));same(proof.context().documentRevision(),str(saved,"base_document_revision"));}
   String hash=hash(request);
   var old=db.queryForList("SELECT * FROM gru_execution_action WHERE tenant_id=? AND owner_id=? AND conversation_id=? AND idempotency_key=? FOR UPDATE",a.context().tenant_id(),a.actorId(),conversation,key);
   if(!old.isEmpty()){var row=one(old);same(hash,str(row,"request_sha256"));return view(row);}
   String id=UUID.randomUUID().toString();var now=Objects.requireNonNull(permissions.databaseTime());
   db.update("INSERT INTO gru_execution_action (tenant_id,owner_id,conversation_id,action_id,idempotency_key,request_json,request_sha256,state,revision,created_at,expires_at,origin_session_id,created_by,classification,schema_version,schema_manifest_sha256) VALUES (?,?,?,?,?,?,?,'PENDING',1,?,?,?,?,?,'1.0.0',?)",
    a.context().tenant_id(),a.actorId(),conversation,id,key,canonical(request),hash,now,now.plusMinutes(30),user.sessionId(),a.actorId(),proof.context().classification(),contract.manifest());
   event(a,proof,id,"gru.action.proposed",request);return view(row(a,conversation,id));
  });
 }
 /** Produces the full exact adoption arguments from the genuine DC03 preview; clients never guess them. */
 public JsonNode prepareAdoption(ApplicationSessionIdentity.Verified user,String context,String conversation,String key,JsonNode input)throws Exception {
  closed(input,"schema_version","text","candidate_id","preview");version(input);SimulationInputArtifactSchema.key(key);
  var proof=records.exportProof(user,context,conversation,null);var previewInput=codec.treeToValue(input.required("preview"),DocumentCandidateAdoptionProtocol.PreviewInput.class);
  same(proof.context().documentId(),previewInput.documentId());same(proof.context().documentRevision(),previewInput.baseDocumentRevision());
  tx(user,a->{guard(a,context,proof,true);return true;});
  var preview=required(adoption).preview(user,context,"gru-preview-"+WireValues.sha256(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),text(input,"candidate_id"),previewInput);
  var arguments=tx(user,a->{guard(a,context,proof,true);var saved=one(db.queryForList("SELECT arguments_json,arguments_sha256 FROM document_adoption_preview WHERE tenant_id=? AND created_by=? AND candidate_id=? AND pending_action_id=?",a.context().tenant_id(),a.actorId(),text(input,"candidate_id"),text(preview,"pending_action_id")));var args=read(str(saved,"arguments_json"));same(hash(args),str(saved,"arguments_sha256"));same(str(saved,"arguments_sha256"),text(preview,"arguments_sha256"));return args;});
  var params=json.object().put("candidate_id",text(input,"candidate_id"));params.set("arguments",arguments);params.set("confirmation",json.object().put("schema_version","1.0.0").put("pending_action_id",text(preview,"pending_action_id")).put("arguments_sha256",text(preview,"arguments_sha256")).put("expected_action_revision",text(preview,"action_revision")).put("confirm",true));
  var request=json.object().put("schema_version","1.0.0").put("intent","ADOPT_EDIT").put("text",text(input,"text")).put("target_version",proof.context().documentRevision());request.set("parameters",params);
  return propose(user,context,conversation,key,request);
 }
 public JsonNode confirm(ApplicationSessionIdentity.Verified user,String context,String conversation,String action,JsonNode request)throws Exception {
  contract.validate("Confirmation",request);closed(request,"schema_version","confirm","request_sha256","target_version","parameters");version(request);
  if(!request.required("confirm").isBoolean()||!request.required("confirm").booleanValue())invalid();
  var proof=records.exportProof(user,context,conversation,null);
  boolean dispatch=tx(user,a->{guard(a,context,proof,true);var row=row(a,conversation,action);var original=read(str(row,"request_json"));
   same(str(row,"request_sha256"),text(request,"request_sha256"));same(original.required("parameters"),request.required("parameters"));same(original.required("target_version"),request.required("target_version"));
   if(!"PENDING".equals(str(row,"state")))return false;
   var now=Objects.requireNonNull(permissions.databaseTime());if(!now.isBefore(time(row.get("expires_at"))))throw new TenantAccessException(ErrorCode.ACTION_STALE);
   same(proof.context().documentRevision(),text(request,"target_version"));
   preferences.capture(a,proof,action);
   changed(db.update("UPDATE gru_execution_action SET state='STARTING',revision=revision+1,confirmation_json=?,confirmed_at=? WHERE tenant_id=? AND owner_id=? AND action_id=? AND state='PENDING'",canonical(request),now,a.context().tenant_id(),a.actorId(),action));
   event(a,proof,action,"gru.action.confirmed",request);return true;});
  if(dispatch)return execute(user,context,conversation,action,proof);
  return read(user,context,conversation,action);
 }
 /** A replay only invokes the original service's idempotent operation, never new parameters/key. */
 public JsonNode recover(ApplicationSessionIdentity.Verified user,String context,String conversation,String action)throws Exception {
  var proof=records.exportProof(user,context,conversation,null);var row=tx(user,a->{guard(a,context,proof,false);return row(a,conversation,action);});
  if("STARTING".equals(str(row,"state")))return execute(user,context,conversation,action,proof);
  return read(user,context,conversation,action);
 }
 private JsonNode execute(ApplicationSessionIdentity.Verified user,String context,String conversation,String action,GruService.ExportProof proof)throws Exception {
  var row=tx(user,a->{guard(a,context,proof,false);authority.authorize(a,context,read(proof.context().canonicalRequest()),true);var v=row(a,conversation,action);same("STARTING",str(v,"state"));return v;});
  var request=read(str(row,"request_json"));var parameters=request.required("parameters");String intent=text(request,"intent"),key="gru-"+action;
  JsonNode result;String state="COMPLETED";
  switch(intent){
   case "RETRIEVE" -> result=tx(user,a->{guard(a,context,proof,true);var fixed=fixed(a,context,proof,action,text(parameters,"query"));
    var receipt=retrieval.create(a,context,fixed.retrievalKey(),new RetrievalProtocol.SearchInput("1.0.0",proof.context().projectId(),fixed.questionText(),"VERIFICATION"),fixed.identity(),fixed);
    String id=receipt.required("data").required("resource_id").asText();var out=json.object();out.set("receipt",receipt);out.set("result",retrieval.read(a,context,id,new RetrievalProtocol.PageQuery(1,100),fixed));return out;});
   case "CHECK" -> {var recovered=required(checks).recovery(user,context,proof.context().documentId(),key);
    if("COMPLETED".equals(text(recovered,"state"))){same(hash(parameters),text(recovered,"request_body_sha256"));result=recovered.required("receipt");}
    else {if(!Set.of("NOT_FOUND","PROCESSING").contains(text(recovered,"state")))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);same(user.sessionId(),str(row,"origin_session_id"));result=required(checks).create(user,context,proof.context().documentId(),key,bytes(parameters));}
   }
   case "QUERY","PROPOSE_EDIT" -> {
    tx(user,a->{guard(a,context,proof,true);var task=tasks.fixedTask(a,text(parameters,"task_id"));same(proof.context().projectId(),task.head().projectId());same(proof.context().snapshotId(),task.head().snapshotId());same(proof.context().documentRevision(),task.input().basedOnDocumentRevision());same(text(request,"text"),task.input().goal());return true;});
    result=starts.create(user,context,key,text(parameters,"task_id"),bytes(parameters.required("run")));state="RUNNING";
   }
   case "ADOPT_EDIT" -> {var candidate=required(adoption).read(user,context,text(parameters,"candidate_id"));same(proof.context().documentId(),text(candidate,"document_id"));same(proof.context().documentRevision(),text(candidate,"base_document_revision"));result=required(adoption).confirm(user,context,key,text(parameters,"candidate_id"),codec.treeToValue(parameters.required("confirmation"),DocumentCandidateAdoptionProtocol.ConfirmInput.class));}
   default -> throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  }
  final String next=state;final JsonNode output=result;
  // Downstream checks/adoption may legitimately change document read metadata. Reacquire current authority.
  final var committedProof=records.exportProof(user,context,conversation,null);
  tx(user,a->{guard(a,context,committedProof,false);var current=row(a,conversation,action);
   if(!"STARTING".equals(str(current,"state"))){same(read(str(current,"result_json")),output);return true;}
   changed(db.update("UPDATE gru_execution_action SET state=?,result_json=?,result_sha256=?,revision=revision+1 WHERE tenant_id=? AND owner_id=? AND action_id=? AND state='STARTING'",next,canonical(output),hash(output),a.context().tenant_id(),a.actorId(),action));
   event(a,committedProof,action,"gru.execution."+next.toLowerCase(java.util.Locale.ROOT),output);return true;});
  return read(user,context,conversation,action);
 }
 public JsonNode read(ApplicationSessionIdentity.Verified user,String context,String conversation,String action)throws Exception {
  var proof=records.exportProof(user,context,conversation,null);var row=tx(user,a->{guard(a,context,proof,false);return row(a,conversation,action);});
  var request=read(str(row,"request_json"));String state=str(row,"state");JsonNode current=null;
  if(row.get("result_json")!=null){var receipt=read(str(row,"result_json"));same(hash(receipt),str(row,"result_sha256"));String intent=text(request,"intent");
   if(Set.of("QUERY","PROPOSE_EDIT").contains(intent)){
    String run=receipt.required("data").required("run_id").asText();current=runs.read(user,context,run);
    // This is a real runtime projection, not model answer text or inferred provider success.
    String status=text(current.required("data"),"status");
    if("SUCCEEDED".equals(status)){var bundle=json.object();bundle.set("run",current);bundle.set("candidate",required(adoption).materialize(user,context,run));current=bundle;}
   }else if("CHECK".equals(intent))current=required(checks).read(user,context,text(receipt,"check_id"));
   else if("RETRIEVE".equals(intent)){String id=receipt.required("receipt").required("data").required("resource_id").asText();current=tx(user,a->{guard(a,context,proof,false);return retrieval.read(a,context,id,new RetrievalProtocol.PageQuery(1,100),fixed(a,context,proof,action,text(request.required("parameters"),"query")));});}
   else if("ADOPT_EDIT".equals(intent))current=required(adoption).confirm(user,context,"gru-"+action,text(request.required("parameters"),"candidate_id"),codec.treeToValue(request.required("parameters").required("confirmation"),DocumentCandidateAdoptionProtocol.ConfirmInput.class));
  }
  final JsonNode observed=current;
  return tx(user,a->{guard(a,context,proof,false);var fresh=row(a,conversation,action);var out=(ObjectNode)view(fresh);
   if(observed!=null){out.set("current_result",observed);var agent=out.putObject("agent_message");agent.put("sender_kind","AGENT").put("content_kind","VERIFIED_TOOL_RESULT").put("action_id",action);agent.set("content",observed);
    String status=observed.path("run").path("data").path("status").asText(observed.path("data").path("status").asText());
    if(Set.of("SUCCEEDED","FAILED","CANCELLED").contains(status)&&!Set.of("COMPLETED","FAILED","CANCELLED").contains(str(fresh,"state"))){String terminal="SUCCEEDED".equals(status)?"COMPLETED":status;
     changed(db.update("UPDATE gru_execution_action SET state=?,revision=revision+1 WHERE tenant_id=? AND owner_id=? AND action_id=? AND revision=?",terminal,a.context().tenant_id(),a.actorId(),action,fresh.get("revision")));event(a,proof,action,"gru.execution."+terminal.toLowerCase(java.util.Locale.ROOT),observed);out.put("state",terminal).put("revision",new java.math.BigInteger(str(fresh,"revision")).add(java.math.BigInteger.ONE).toString());}
    out.set("agent_message",saveMessage(a,proof,action,stable(observed)));out.set("presentation",preferences.present(a,proof,action,text(request,"intent"),text(out,"state"),stable(observed)));contract.validate("Action",out);
   }
   return out;
  });
 }
 public JsonNode cancel(ApplicationSessionIdentity.Verified user,String context,String conversation,String action,JsonNode request)throws Exception {
  closed(request,"schema_version","expected_revision","reason");version(request);PermissionSchema.revision(text(request,"expected_revision"));if(text(request,"reason").isBlank()||text(request,"reason").length()>2048)invalid();var proof=records.exportProof(user,context,conversation,null);
  var row=tx(user,a->{guard(a,context,proof,true);var v=row(a,conversation,action);
   if("PENDING".equals(str(v,"state"))){same(v.get("revision").toString(),text(request,"expected_revision"));changed(db.update("UPDATE gru_execution_action SET state='CANCELLED',revision=revision+1 WHERE tenant_id=? AND owner_id=? AND action_id=? AND state='PENDING'",a.context().tenant_id(),a.actorId(),action));event(a,proof,action,"gru.action.cancelled",request);return row(a,conversation,action);}return v;});
  if("RUNNING".equals(str(row,"state"))){String run=read(str(row,"result_json")).required("data").required("run_id").asText();return cancellations.cancel(user,context,"gru-cancel-"+action,run,bytes(request));}
  // STARTING is an uncertain downstream outcome. It must be recovered before cancellation can target it.
  if("STARTING".equals(str(row,"state")))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
  return view(row);
 }
 public JsonNode history(ApplicationSessionIdentity.Verified user,String context,String conversation)throws Exception {
  var proof=records.exportProof(user,context,conversation,null);
  return tx(user,a->{guard(a,context,proof,false);var out=json.object().put("schema_version","1.0.0");var items=out.putArray("actions");
   for(var row:db.queryForList("SELECT * FROM gru_execution_action WHERE tenant_id=? AND owner_id=? AND conversation_id=? ORDER BY created_at,action_id LIMIT 100",a.context().tenant_id(),a.actorId(),conversation))items.add(view(row));return out;});
 }
 /** Current owner memory is separate from facts, permissions and immutable task inputs. */
 public JsonNode memory(ApplicationSessionIdentity.Verified user,String context,String conversation,JsonNode request)throws Exception {
  var proof=records.exportProof(user,context,conversation,null);
  return tx(user,a->{guard(a,context,proof,request!=null);return request==null?preferences.list(a,proof):preferences.create(a,proof,request);});
 }
 public JsonNode changeMemory(ApplicationSessionIdentity.Verified user,String context,String conversation,String memory,JsonNode request,boolean remove)throws Exception {
  var proof=records.exportProof(user,context,conversation,null);return tx(user,a->{guard(a,context,proof,true);return preferences.change(a,proof,memory,request,remove);});
 }
 private void validateParameters(JsonNode request,GruService.ExportProof proof){var p=request.required("parameters");switch(text(request,"intent")){
  case "QUERY","PROPOSE_EDIT" -> {closed(p,"task_id","run");WireValues.uuid(text(p,"task_id"));same(proof.context().snapshotId(),text(p.required("run"),"snapshot_id"));}
  case "RETRIEVE" -> {closed(p,"query");String q=text(p,"query");if(q.length()<2||q.length()>500)invalid();}
  case "CHECK" -> {closed(p,"schema_version","document_revision","expected_revision","reason");same(proof.context().documentRevision(),text(p,"document_revision"));}
  case "ADOPT_EDIT" -> {closed(p,"candidate_id","confirmation","arguments");WireValues.uuid(text(p,"candidate_id"));}
  default -> invalid();}}
 private TaskResearchToolAccess.Fixed fixed(EnterpriseAccess.Admission a,String context,GruService.ExportProof proof,String action,String query)throws Exception {
  var manifest=authority.verify(a,context,read(proof.context().canonicalRequest()),proof.document(),false);
  var sources=manifest.members().stream().map(m->new TaskNodeInputProtocol.SourceSelection(m.source_version(),m.parse_version(),m.index_version(),m.approval_revision(),m.binding_revision(),m.rights_revision(),"VERIFICATION")).toList();
  return new TaskResearchToolAccess.Fixed(a.context().tenant_id(),proof.context().projectId(),a.actorId(),proof.context().snapshotId(),action,query,sources,hash(json.object().put("gru_action",action).put("snapshot_id",proof.context().snapshotId())));
 }
 private void guard(EnterpriseAccess.Admission a,String context,GruService.ExportProof proof,boolean write)throws Exception {same(context,a.context().context_revision());records.requireExportProof(a,context,proof,false);authority.verify(a,context,read(proof.context().canonicalRequest()),proof.document(),write);}
 private Map<String,Object> row(EnterpriseAccess.Admission a,String conversation,String action){WireValues.uuid(action);return one(db.queryForList("SELECT * FROM gru_execution_action WHERE tenant_id=? AND owner_id=? AND conversation_id=? AND action_id=? FOR UPDATE",a.context().tenant_id(),a.actorId(),conversation,action));}
 private JsonNode view(Map<String,Object> row)throws Exception {same("1.0.0",str(row,"schema_version"));same(contract.manifest(),str(row,"schema_manifest_sha256"));same(str(row,"owner_id"),str(row,"created_by"));var request=read(str(row,"request_json"));same(hash(request),str(row,"request_sha256"));var out=json.object().put("schema_version","1.0.0").put("action_id",str(row,"action_id")).put("conversation_id",str(row,"conversation_id")).put("state",str(row,"state")).put("revision",str(row,"revision")).put("request_sha256",str(row,"request_sha256"));out.set("request",request);out.put("owner_id",str(row,"owner_id")).put("tenant_id",str(row,"tenant_id")).put("expires_at",time(row.get("expires_at")).toInstant(java.time.ZoneOffset.UTC).toString());out.put("explicit_confirmation_required",true);contract.validate("Action",out);return out;}
 private JsonNode stable(JsonNode value){if(value.has("data"))return value.required("data").deepCopy();var copy=value.deepCopy();if(copy.isObject()&&copy.path("run").has("data"))((ObjectNode)copy).set("run",copy.required("run").required("data").deepCopy());return copy;}
 private JsonNode saveMessage(EnterpriseAccess.Admission a,GruService.ExportProof proof,String action,JsonNode result)throws Exception {
  String digest=hash(result);var rows=db.queryForList("SELECT * FROM gru_execution_message WHERE tenant_id=? AND owner_id=? AND action_id=? AND result_sha256=?",a.context().tenant_id(),a.actorId(),action,digest);
  if(rows.isEmpty()){String id=UUID.randomUUID().toString();changed(db.update("INSERT INTO gru_execution_message (tenant_id,owner_id,conversation_id,action_id,message_id,result_json,result_sha256,created_at,created_by,classification,schema_version,schema_manifest_sha256) VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(6),?,?,'1.0.0',?)",a.context().tenant_id(),a.actorId(),proof.context().conversationId(),action,id,canonical(result),digest,a.actorId(),proof.context().classification(),contract.manifest()));event(a,proof,id,"gru.agent.message",result);
   rows=db.queryForList("SELECT * FROM gru_execution_message WHERE tenant_id=? AND owner_id=? AND action_id=? AND result_sha256=?",a.context().tenant_id(),a.actorId(),action,digest);}
  var saved=one(rows);same(result,read(str(saved,"result_json")));same(contract.manifest(),str(saved,"schema_manifest_sha256"));same(proof.context().classification(),str(saved,"classification"));
  var message=json.object().put("schema_version","1.0.0").put("sender_kind","AGENT").put("message_id",str(saved,"message_id")).put("conversation_id",proof.context().conversationId()).put("action_id",action).put("content_kind","VERIFIED_TOOL_RESULT").put("content_sha256",digest).put("created_at",time(saved.get("created_at")).toInstant(java.time.ZoneOffset.UTC).toString());message.set("content",result);return message;
 }
 public JsonNode summarize(ApplicationSessionIdentity.Verified user,String context,String conversation)throws Exception {
  var proof=records.exportProof(user,context,conversation,null);var lines=new ArrayList<String>();int length=0;
  // Exact user quotations, not project knowledge or model-inferred instructions.
  for(var message:proof.messages()){String quote=read(message.contentRef()).required("message").required("content").asText();String line="USER: "+quote;if(length+line.length()>7900)break;lines.add(line);length+=line.length()+1;}
  String summary=lines.isEmpty()?"No recorded user messages.":String.join("\n",lines);
  return memory(user,context,conversation,json.object().put("schema_version","1.0.0").put("kind","SUMMARY").put("text",summary));
 }
 private void event(EnterpriseAccess.Admission a,GruService.ExportProof proof,String id,String kind,JsonNode payload)throws Exception {String encoded=canonical(payload),request=UUID.randomUUID().toString();changed(permissions.saveAudit(a.actorId(),proof.context().projectId(),UUID.randomUUID().toString(),kind,encoded,request,contract.manifest(),proof.context().classification()));changed(permissions.saveEvent(a.actorId(),request,id,"1",kind,encoded));}
 private <T>T tx(ApplicationSessionIdentity.Verified user,Work<T> work){return access.execute(user,a->ThrowingSupplier.of(()->work.run(a)).get());}
 @FunctionalInterface private interface Work<T>{T run(EnterpriseAccess.Admission a)throws Exception;}
 private JsonNode read(String text)throws Exception{return codec.readTree(text);}private String canonical(JsonNode value)throws Exception{return json.canonical(value);}private byte[] bytes(JsonNode value)throws Exception{return canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);}private String hash(JsonNode value)throws Exception{return WireValues.sha256(bytes(value));}
 private static java.time.LocalDateTime time(Object value){return value instanceof java.sql.Timestamp t?t.toLocalDateTime():(java.time.LocalDateTime)value;}
 private static <T>T required(ObjectProvider<T> provider){T value=provider.getIfAvailable();if(value==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return value;}
 private static Map<String,Object> one(List<Map<String,Object>> rows){if(rows.size()!=1)throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return rows.get(0);}private static String str(Map<String,Object> row,String key){return Objects.requireNonNull(row.get(key)).toString();}
 private static void version(JsonNode n){same("1.0.0",text(n,"schema_version"));}private static String text(JsonNode n,String key){if(!n.path(key).isTextual())invalid();return n.required(key).textValue();}
 private static void closed(JsonNode n,String...fields){ProfessionalRuntimeObservations.closed(n,Set.of(fields));}private static void changed(int n){if(n!=1)throw new TenantAccessException(ErrorCode.ACTION_STALE);}private static void same(Object a,Object b){if(!Objects.equals(a,b))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
