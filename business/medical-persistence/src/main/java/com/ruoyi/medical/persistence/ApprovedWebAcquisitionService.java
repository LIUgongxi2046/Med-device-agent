package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Approved acquisition is NOT a source approval. Raw originals remain quarantined until
 * the existing upload, rights evidence/decision, processing and source-approval paths finish. */
@Component
public final class ApprovedWebAcquisitionService {
 static final String MANIFEST=manifest();
 private final EnterpriseAccess access;private final ResourceReadAuthority resources;private final PermissionAuthority grants;
 private final PermissionMapper permissions;private final PermissionSchema json;private final JdbcTemplate db;
 private final ObjectProvider<ApprovedWebGateway> provider;private final SourceUploadService uploads;private final SourceUploadTransport transport;
 private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public ApprovedWebAcquisitionService(EnterpriseAccess access,ResourceReadAuthority resources,PermissionAuthority grants,PermissionMapper permissions,
  PermissionSchema json,@Qualifier("masterDataSource")DataSource data,ObjectProvider<ApprovedWebGateway> provider,SourceUploadService uploads,SourceUploadTransport transport){
  this.access=access;this.resources=resources;this.grants=grants;this.permissions=permissions;this.json=json;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.provider=provider;this.uploads=uploads;this.transport=transport;
 }
 public JsonNode author(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes){return access.execute(subject,a->checked(()->{
  context(a,context);json.headers(context,key);var body=parse(bytes);validateDefinition(body);String project=text(body,"project_id");require(a,project,"source.write");one(db.queryForList("SELECT question_id FROM research_question WHERE tenant_id=? AND project_id=? AND question_id=? LIMIT 2",tenant(a),project,text(body,"question_id")));
  String id=id("WEB_QUERY",a.actorId(),key),canonical=json.canonical(body),hash=hash(body);var previous=db.queryForList("SELECT * FROM approved_web_query WHERE tenant_id=? AND query_revision=? FOR UPDATE",tenant(a),id);
  if(previous.isEmpty())changed(db.update("INSERT INTO approved_web_query(tenant_id,project_id,query_revision,question_id,canonical_definition,definition_sha256,state,revision,created_by,created_at,classification,schema_version,schema_manifest_sha256) VALUES(?,?,?,?,?,?,'DRAFT',1,?,UTC_TIMESTAMP(6),?,'1.0.0',?)",tenant(a),project,id,text(body,"question_id"),canonical,hash,a.actorId(),text(body,"classification"),MANIFEST));
  else if(!hash.equals(str(one(previous),"definition_sha256")))bad();return queryView(a,id);
 }));}
 public JsonNode query(ApplicationSessionIdentity.Verified subject,String context,String id){return access.execute(subject,a->checked(()->{context(a,context);return queryView(a,id);}));}
 public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String revision,String key,byte[] bytes){return access.execute(subject,a->checked(()->{
  context(a,context);json.headers(context,key);var b=parse(bytes);closed(b,"decision","definition_sha256","expected_revision","reason");String decision=text(b,"decision");if(!Set.of("APPROVED","REVOKED").contains(decision))bad();
  var row=queryRow(a,revision);var grant=require(a,str(row,"project_id"),"source.approve");if(!str(row,"definition_sha256").equals(text(b,"definition_sha256")))bad();
  String reason=text(b,"reason");if(reason.isBlank()||reason.length()>4096)bad();String decisionId=id("WEB_DECISION",a.actorId(),key);
  var old=db.queryForList("SELECT * FROM approved_web_query_decision WHERE tenant_id=? AND decision_id=?",tenant(a),decisionId);
  if(!old.isEmpty()){var prior=one(old);if(!revision.equals(str(prior,"query_revision"))||!decision.equals(str(prior,"decision"))||!reason.equals(str(prior,"reason"))||!text(b,"definition_sha256").equals(str(prior,"definition_sha256")))bad();return queryView(a,revision);}
  if(!str(row,"revision").equals(text(b,"expected_revision")))stale();if("APPROVED".equals(decision)&&!"DRAFT".equals(str(row,"state"))||"REVOKED".equals(decision)&&!"APPROVED".equals(str(row,"state")))stale();
  validateDefinition(parse(str(row,"canonical_definition").getBytes(StandardCharsets.UTF_8)));
  changed(db.update("INSERT INTO approved_web_query_decision(tenant_id,project_id,query_revision,decision_id,decision,definition_sha256,actor_id,identity_mode,grant_id,grant_revision,reason,created_at,created_by,classification,schema_version,schema_manifest_sha256) VALUES(?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),?,?,'1.0.0',?)",tenant(a),str(row,"project_id"),revision,decisionId,decision,text(b,"definition_sha256"),a.actorId(),a.bindingView().mode(),grant.grantId(),grant.revision(),reason,a.actorId(),str(row,"classification"),MANIFEST));
  changed(db.update("UPDATE approved_web_query SET state=?,revision=revision+1 WHERE tenant_id=? AND query_revision=? AND revision=?",decision,tenant(a),revision,text(b,"expected_revision")));return queryView(a,revision);
 }));}
 private JsonNode queryView(EnterpriseAccess.Admission a,String id)throws Exception{var row=queryRow(a,id);require(a,str(row,"project_id"),"source.read");var out=json.object().put("query_revision",id).put("definition_sha256",str(row,"definition_sha256")).put("state",str(row,"state")).put("revision",str(row,"revision"));out.set("definition",parse(str(row,"canonical_definition").getBytes(StandardCharsets.UTF_8)));var definition=out.required("definition");out.put("outbound_query",text(definition,"query")+" ("+String.join(" OR ",strings(definition.required("domains")).stream().map(d->"site:"+d).toList())+")");out.put("provider","BRAVE_SEARCH").put("provider_endpoint",ApprovedWebGateway.ENDPOINT).put("formal_source_approval",false);return out;}
 private Map<String,Object> queryRow(EnterpriseAccess.Admission a,String id){WireValues.uuid(id);return metadata(one(db.queryForList("SELECT * FROM approved_web_query WHERE tenant_id=? AND query_revision=? LIMIT 2 FOR UPDATE",tenant(a),id)));}
 private JsonNode approved(EnterpriseAccess.Admission a,String revision,String project,String question)throws Exception{
  var row=queryRow(a,revision);if(!project.equals(str(row,"project_id"))||!question.equals(str(row,"question_id"))||!"APPROVED".equals(str(row,"state")))deny();
  if(!"ACTIVE".equals(one(permissions.projectStatus(project))))deny();
  var body=parse(str(row,"canonical_definition").getBytes(StandardCharsets.UTF_8));if(!hash(body).equals(str(row,"definition_sha256")))bad();validateDefinition(body);
  if(!permissions.databaseTime().toInstant(ZoneOffset.UTC).isBefore(Instant.parse(text(body,"expires_at"))))stale();
  var decision=metadata(one(db.queryForList("SELECT * FROM approved_web_query_decision WHERE tenant_id=? AND query_revision=? AND decision='APPROVED' LIMIT 2",tenant(a),revision)));
  if(!str(row,"definition_sha256").equals(str(decision,"definition_sha256")))bad();String actor=str(decision,"actor_id");var member=one(permissions.targetMember(actor));var now=permissions.databaseTime();
  if(!"ACTIVE".equals(member.status())||member.validFrom()==null||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now))deny();
  // Approval is live authority, not merely a historical signature or a caller's UUID.
  var binding=one(permissions.targetBinding(actor));if(!"ACTIVE".equals(binding.status())||!binding.mode().equals(str(decision,"identity_mode")))deny();
  var actual=grants.requireResource(new PermissionProtocol.Scope(tenant(a),project,"PROJECT",project),actor,str(decision,"identity_mode"),"source.approve","WEB_ACQUISITION",permissions.currentGrant(str(decision,"grant_id")),now);
  if(!actual.grantId().equals(str(decision,"grant_id"))||!actual.revision().toString().equals(str(decision,"grant_revision")))stale();
  return body;
 }
 public JsonNode execute(ApplicationSessionIdentity.Verified subject,String context,String revision,String key,boolean recovery)throws Exception{
  json.headers(context,key);var initial=access.execute(subject,a->checked(()->{context(a,context);var q=queryRow(a,revision);require(a,str(q,"project_id"),"source.write");return start(a,revision,str(q,"project_id"),str(q,"question_id"),id("WEB_ACQUISITION",a.actorId(),key),null);}));
  ApprovedWebGateway.Current current=()->access.execute(subject,a->checked(()->{context(a,context);require(a,initial.project(),"source.write");same(initial,start(a,revision,initial.project(),initial.question(),initial.id(),null));return null;}));
  try{var raw=access.execute(subject,a->checked(()->{context(a,context);require(a,initial.project(),"source.write");return original(a,initial);}));if(raw==null)raw=gateway().execute(initial.request(),recovery,current);final var result=raw;return access.execute(subject,a->checked(()->{context(a,context);require(a,initial.project(),"source.write");return finish(a,initial,result);}));}
  catch(Exception failure){try{access.execute(subject,a->{markUnknown(a,initial);return null;});}catch(Exception stale){failure.addSuppressed(stale);}throw failure;}
 }
 record Work(String tenant,String project,String question,String id,String revision,String definitionHash,ApprovedWebGateway.Request request){}
 Work startTool(TaskRegisteredToolAuthority.Checked c)throws Exception{c.requireUnchanged();if(!"AT-09".equals(c.tool()))deny();var s=c.session();var args=c.request().required("arguments");if(!s.input().questionId().equals(text(args,"question_id")))deny();return start(s.admission(),text(args,"approved_query_revision"),s.input().projectId(),s.input().questionId(),c.actionId(),c.canonicalPins());}
 private Work start(EnterpriseAccess.Admission a,String revision,String project,String question,String acquisition,JsonNode toolPins)throws Exception{
  var definition=approved(a,revision,project,question);String hash=hash(definition);var request=new ApprovedWebGateway.Request(tenant(a),acquisition,text(definition,"query"),strings(definition.required("domains")),definition.required("maximum_results").intValue(),text(definition,"credential_reference_id"),text(definition,"credential_revision"));
  var identity=json.object().put("query_revision",revision).put("definition_sha256",hash);identity.set("request",json.tree(request));identity.set("tool_pins",toolPins==null?json.tree(null):toolPins);
  String requestHash=hash(identity);var rows=db.queryForList("SELECT * FROM approved_web_acquisition WHERE tenant_id=? AND acquisition_id=? FOR UPDATE",tenant(a),acquisition);
  if(rows.isEmpty())changed(db.update("INSERT INTO approved_web_acquisition(tenant_id,project_id,acquisition_id,query_revision,definition_sha256,actor_id,state,request_sha256,created_at,updated_at,created_by,classification,schema_version,schema_manifest_sha256) VALUES(?,?,?,?,?,?,'STARTED',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,?,'1.0.0',?)",tenant(a),project,acquisition,revision,hash,a.actorId(),requestHash,a.actorId(),text(definition,"classification"),MANIFEST));
  else{var row=one(rows);if(!requestHash.equals(str(row,"request_sha256"))||!a.actorId().equals(str(row,"actor_id")))bad();}
  return new Work(tenant(a),project,question,acquisition,revision,hash,request);
 }
 ApprovedWebGateway gateway(){var result=provider.getIfAvailable();if(result==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return result;}
 JsonNode originalTool(TaskRegisteredToolAuthority.Checked checked,Work work)throws Exception{same(work,startTool(checked));return original(checked.session().admission(),work);}
 private JsonNode original(EnterpriseAccess.Admission a,Work work)throws Exception{var row=acquisition(a,work.id());if(row.get("result_json")==null)return null;var raw=parse(str(row,"result_json").getBytes(StandardCharsets.UTF_8));if(!hash(raw).equals(str(row,"result_sha256")))bad();return raw;}
 JsonNode finishTool(TaskRegisteredToolAuthority.Checked checked,Work work,JsonNode raw)throws Exception{same(work,startTool(checked));return finish(checked.session().admission(),work,raw);}
 private JsonNode finish(EnterpriseAccess.Admission a,Work work,JsonNode raw)throws Exception{
  if(!work.definitionHash().equals(hash(approved(a,work.revision(),work.project(),work.question()))))stale();if(!work.id().equals(text(raw,"acquisition_id")))bad();
  String resultHash=hash(raw),canonical=json.canonical(raw);var row=acquisition(a,work.id());
  if(row.get("result_sha256")!=null){if(!resultHash.equals(str(row,"result_sha256")))bad();return acquisitionView(a,work.id(),false);}
  changed(db.update("UPDATE approved_web_acquisition SET state=?,result_json=?,result_sha256=?,failure=?,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND acquisition_id=? AND result_json IS NULL",raw.required("complete").booleanValue()?"CAPTURED_PENDING_REVIEW":"UNKNOWN",canonical,resultHash,raw.required("complete").booleanValue()?null:"INCOMPLETE_OR_FAILED_CAPTURE",tenant(a),work.id()));
  int ordinal=0;for(var capture:raw.required("captures")){ordinal++;if(!capture.required("complete").booleanValue()||capture.required("status").intValue()!=200||capture.required("size_bytes").intValue()==0)continue;
   String candidate=id("WEB_CANDIDATE",work.id(),Integer.toString(ordinal));changed(db.update("INSERT INTO approved_web_candidate(tenant_id,project_id,candidate_id,acquisition_id,ordinal,original_sha256,capture_sha256,state,created_by,created_at,classification,schema_version,schema_manifest_sha256) VALUES(?,?,?,?,?,?,?,'PENDING_RIGHTS',?,UTC_TIMESTAMP(6),?,'1.0.0',?)",tenant(a),work.project(),candidate,work.id(),ordinal,text(capture,"sha256"),hash(capture),a.actorId(),str(row,"classification"),MANIFEST));}
  return acquisitionView(a,work.id(),false);
 }
 void unknownTool(TaskRegisteredToolAuthority.Checked c,Work work){markUnknown(c.session().admission(),work);}
 private void markUnknown(EnterpriseAccess.Admission a,Work work){db.update("UPDATE approved_web_acquisition SET state='UNKNOWN',failure='NO_COMMITTED_COMPLETE_RESULT',updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND acquisition_id=? AND actor_id=? AND result_json IS NULL",tenant(a),work.id(),a.actorId());}
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String id,boolean originals){return access.execute(subject,a->checked(()->{context(a,context);var row=acquisition(a,id);require(a,str(row,"project_id"),"source.read");return acquisitionView(a,id,originals);}));}
 private Map<String,Object> acquisition(EnterpriseAccess.Admission a,String id){WireValues.uuid(id);return metadata(one(db.queryForList("SELECT * FROM approved_web_acquisition WHERE tenant_id=? AND acquisition_id=? LIMIT 2 FOR UPDATE",tenant(a),id)));}
 private JsonNode acquisitionView(EnterpriseAccess.Admission a,String id,boolean originals)throws Exception{
  var row=acquisition(a,id);var result=json.object().put("acquisition_id",id).put("query_revision",str(row,"query_revision")).put("state",str(row,"state")).put("formal_evidence",false);result.set("failure",json.tree(row.get("failure")));result.set("result_sha256",json.tree(row.get("result_sha256")));var candidates=result.putArray("candidates");
  for(var c:db.queryForList("SELECT * FROM approved_web_candidate WHERE tenant_id=? AND acquisition_id=? ORDER BY ordinal",tenant(a),id)){
   var candidate=json.object();for(String field:List.of("candidate_id","original_sha256","capture_sha256","state","upload_id"))candidate.set(field,json.tree(c.get(field)));
   try{candidate.put("media_type",media(capture(a,c))).put("source_import_supported",true);}catch(TenantAccessException unsupported){if(unsupported.code()!=ErrorCode.FILE_TYPE_UNSUPPORTED)throw unsupported;candidate.put("source_import_supported",false).put("import_blocker","SOURCE_FORMAT_NOT_SUPPORTED");}
   if(c.get("upload_id")!=null)candidate.put("source_lifecycle_url","/api/uploads/"+str(c,"upload_id")+"/status");
   candidates.add(candidate);
  }
  if(row.get("result_json")!=null){var raw=parse(str(row,"result_json").getBytes(StandardCharsets.UTF_8));if(!hash(raw).equals(str(row,"result_sha256")))bad();if(originals)result.set("originals",raw);}
  return result;
 }
 JsonNode requireToolReceipt(TaskRegisteredToolAuthority.Checked c,JsonNode witness)throws Exception{var work=startTool(c);var row=acquisition(c.session().admission(),work.id());if(!"CAPTURED_PENDING_REVIEW".equals(str(row,"state")))bad();var expected=json.object().put("acquisition_id",work.id()).put("query_revision",work.revision()).put("definition_sha256",work.definitionHash()).put("result_sha256",str(row,"result_sha256"));same(expected,witness);return expected;}
 JsonNode toolWitness(TaskRegisteredToolAuthority.Checked c)throws Exception{var w=startTool(c);var row=acquisition(c.session().admission(),w.id());return requireToolReceipt(c,json.object().put("acquisition_id",w.id()).put("query_revision",w.revision()).put("definition_sha256",w.definitionHash()).put("result_sha256",str(row,"result_sha256")));}
 /** The user supplies real provenance and a rights declaration. This method never invents
  * a license, publisher, status verification, or formal approval from a successful GET. */
 public JsonNode ingest(ApplicationSessionIdentity.Verified subject,String context,String candidate,byte[] declaration)throws Exception{
  var initial=access.execute(subject,a->checked(()->{
   context(a,context);var c=candidate(a,candidate);require(a,str(c,"project_id"),"source.write");var original=capture(a,c);var body=parse(declaration);
   if(!str(c,"classification").equals(text(body,"classification"))||!str(c,"project_id").equals(text(body,"project_id"))||!"NEW_SOURCE".equals(text(body,"version_intent"))||!"PROJECT".equals(text(body,"ownership"))
     ||!text(original,"sha256").equals(text(body,"sha256"))||!Integer.toString(original.required("size_bytes").intValue()).equals(text(body,"size_bytes")))bad();
   String media=media(original);if(!media.equals(text(body,"media_type")))bad();var provenance=body.required("provenance");
   if(!text(original,"url").equals(text(provenance,"original_url"))||!text(original,"url").equals(text(provenance,"final_url"))||!text(original,"captured_at").equals(text(provenance,"acquired_at"))
      ||!str(c,"created_by").equals(text(provenance,"acquired_by"))||!"APPROVED_WEB_CAPTURE".equals(text(provenance,"acquisition_method")))bad();
   String requestHash=hash(body);if(c.get("import_sha256")!=null&&!requestHash.equals(str(c,"import_sha256")))bad();
   if(c.get("import_sha256")==null)changed(db.update("UPDATE approved_web_candidate SET state='IMPORTING',import_request=?,import_sha256=? WHERE tenant_id=? AND candidate_id=? AND import_sha256 IS NULL",json.canonical(body),requestHash,tenant(a),candidate));
   return original;
  }));
  String createKey=id("WEB_UPLOAD_CREATE",candidate,subject.user().user_id());
  var created=uploads.create(subject,context,createKey,declaration);String upload=text(created.required("data"),"upload_id");
  access.execute(subject,a->checked(()->{context(a,context);var c=candidate(a,candidate);require(a,str(c,"project_id"),"source.write");if(c.get("upload_id")!=null&&!upload.equals(str(c,"upload_id")))bad();if(c.get("upload_id")==null)changed(db.update("UPDATE approved_web_candidate SET upload_id=? WHERE tenant_id=? AND candidate_id=? AND upload_id IS NULL",upload,tenant(a),candidate));return null;}));
  byte[] bytes=Base64.getDecoder().decode(text(initial,"body_base64"));if(!WireValues.sha256(bytes).equals(text(initial,"sha256")))bad();
  var beforePart=uploads.read(subject,context,upload,0,100);boolean stored=false;
  for(var part:beforePart.required("data").required("parts"))if(part.required("part_no").intValue()==1&&"STORED".equals(text(part,"state"))&&text(initial,"sha256").equals(text(part,"sha256")))stored=true;
  if(!stored)transport.receiveCapturedSource(subject,context,id("WEB_UPLOAD_PART",candidate,subject.user().user_id()),upload,bytes);
  var registration=uploads.read(subject,context,upload,0,100);var completion=json.object().put("schema_version","1.0.0").put("expected_revision",text(registration,"resource_revision"))
    .put("version_intent","NEW_SOURCE").put("size_bytes",Integer.toString(bytes.length)).put("sha256",text(initial,"sha256"));
  completion.putArray("parts").addObject().put("part_no",1).put("size_bytes",Integer.toString(bytes.length)).put("sha256",text(initial,"sha256"));
  var fixedCompletion=access.execute(subject,a->checked(()->{context(a,context);var c=candidate(a,candidate);require(a,str(c,"project_id"),"source.write");
   if(c.get("completion_request")!=null)return parse(str(c,"completion_request").getBytes(StandardCharsets.UTF_8));
   changed(db.update("UPDATE approved_web_candidate SET completion_request=? WHERE tenant_id=? AND candidate_id=? AND completion_request IS NULL",json.canonical(completion),tenant(a),candidate));return (JsonNode)completion;
  }));
  var receipt=uploads.requestCompletion(subject,context,id("WEB_UPLOAD_COMPLETION",candidate,subject.user().user_id()),upload,ContentReadAuthorization.canonicalBytes(fixedCompletion));
  return access.execute(subject,a->checked(()->{context(a,context);var c=candidate(a,candidate);require(a,str(c,"project_id"),"source.write");db.update("UPDATE approved_web_candidate SET state='UPLOAD_PENDING_GOVERNANCE' WHERE tenant_id=? AND candidate_id=? AND upload_id=?",tenant(a),candidate,upload);
   var result=json.object().put("candidate_id",candidate).put("upload_id",upload).put("state","UPLOAD_PENDING_GOVERNANCE").put("formal_evidence",false);result.set("completion",receipt);result.put("next_step","Read upload status, complete rights evidence and rights decisions, then normal source approval. Capture is not approval.");return result;}));
 }
 private Map<String,Object> candidate(EnterpriseAccess.Admission a,String id){WireValues.uuid(id);return metadata(one(db.queryForList("SELECT * FROM approved_web_candidate WHERE tenant_id=? AND candidate_id=? LIMIT 2 FOR UPDATE",tenant(a),id)));}
 private JsonNode capture(EnterpriseAccess.Admission a,Map<String,Object> candidate)throws Exception{var acquisition=acquisition(a,str(candidate,"acquisition_id"));var raw=parse(str(acquisition,"result_json").getBytes(StandardCharsets.UTF_8));if(!hash(raw).equals(str(acquisition,"result_sha256")))bad();var capture=raw.required("captures").get(((Number)candidate.get("ordinal")).intValue()-1);if(!hash(capture).equals(str(candidate,"capture_sha256"))||!text(capture,"sha256").equals(str(candidate,"original_sha256"))||!capture.required("complete").booleanValue()||capture.required("status").intValue()!=200)bad();return capture;}
 private static String media(JsonNode capture){var values=new ArrayList<String>();for(var h:capture.required("headers"))if("content-type".equalsIgnoreCase(text(h,"name")))values.add(text(h,"value").split(";",2)[0].trim().toLowerCase(Locale.ROOT));if(values.size()!=1)throw new TenantAccessException(ErrorCode.FILE_TYPE_UNSUPPORTED);String media=values.get(0);if(!Set.of("application/pdf","text/plain","text/html").contains(media))throw new TenantAccessException(ErrorCode.FILE_TYPE_UNSUPPORTED);return media;}
 void requireHistoryCurrent(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,JsonNode body)throws Exception {
  var args=body.required("request").required("arguments");if(!input.questionId().equals(text(args,"question_id")))deny();
  var definition=approved(a,text(args,"approved_query_revision"),input.projectId(),input.questionId());
  if(!hash(definition).equals(text(body.required("physical").required("witness"),"definition_sha256")))bad();
  requireOriginalReceipt(db,json,tenant(a),input.projectId(),a.actorId(),body);
 }
 static void requireOriginalReceipt(JdbcTemplate db,PermissionSchema json,String tenant,String project,String actor,JsonNode body)throws Exception {
  var physical=body.required("physical");closed(physical,"kind","witness");if(!"WEB_ACQUISITION".equals(text(physical,"kind")))bad();var witness=physical.required("witness");closed(witness,"acquisition_id","query_revision","definition_sha256","result_sha256");
  var row=one(db.queryForList("SELECT * FROM approved_web_acquisition WHERE tenant_id=? AND project_id=? AND acquisition_id=? AND actor_id=? AND state='CAPTURED_PENDING_REVIEW' LIMIT 2",tenant,project,text(witness,"acquisition_id"),actor));
  for(String field:List.of("query_revision","definition_sha256","result_sha256"))if(!str(row,field).equals(text(witness,field)))bad();
  var original=JsonMapper.builder().build().readTree(str(row,"result_json"));if(!hash(original).equals(str(row,"result_sha256")))bad();
  var response=body.required("response");var payload=response.required("payload");
  if(!"AT-09".equals(text(response,"tool_id"))||!"SUCCEEDED".equals(text(response,"status"))||!"RECEIPT".equals(text(payload,"kind"))||!"WEB_ACQUISITION".equals(text(payload,"resource_kind"))||!"CAPTURED_PENDING_REVIEW".equals(text(payload,"resource_state"))
    ||!text(witness,"acquisition_id").equals(text(payload,"resource_id"))||!payload.required("check_ids").isEmpty()||!text(body,"input_id").equals(text(response,"artifact_id"))||!text(witness,"query_revision").equals(text(response,"resource_revision"))
    ||!text(witness,"query_revision").equals(text(body.required("request").required("arguments"),"approved_query_revision")))bad();
 }
 private void validateDefinition(JsonNode b){closed(b,"schema_version","project_id","question_id","query","domains","maximum_results","credential_reference_id","credential_revision","purpose","expires_at","classification");if(!"1.0.0".equals(text(b,"schema_version")))bad();WireValues.uuid(text(b,"project_id"));WireValues.uuid(text(b,"question_id"));WireValues.uuid(text(b,"credential_reference_id"));String revision=text(b,"credential_revision");if(revision.isBlank()||revision.length()>128)bad();String query=text(b,"query");if(query.isBlank()||query.length()>2000||query.codePoints().anyMatch(Character::isISOControl))bad();var domains=strings(b.required("domains"));if(domains.isEmpty()||domains.size()>10||new HashSet<>(domains).size()!=domains.size())bad();domains.forEach(ApprovedWebGateway::domain);var count=b.required("maximum_results");if(!count.isIntegralNumber()||!count.canConvertToInt()||count.intValue()<1||count.intValue()>10)bad();if(!"WEB_ACQUISITION".equals(text(b,"purpose")))bad();WireValues.utcTime(text(b,"expires_at"));if(!Set.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH").contains(text(b,"classification")))bad();}
 private PermissionProtocol.GrantRow require(EnterpriseAccess.Admission a,String project,String action)throws Exception{return resources.require(a,new PermissionProtocol.Scope(tenant(a),project,"PROJECT",project),action,"WEB_ACQUISITION");}
 private JsonNode parse(byte[] bytes)throws Exception{if(bytes.length==0||bytes.length>128*1024*1024)bad();return parser.readTree(bytes);}
 private static List<String> strings(JsonNode n){if(!n.isArray())bad();var values=new ArrayList<String>();for(var x:n){if(!x.isTextual())bad();values.add(x.textValue());}return List.copyOf(values);}
 private static void context(EnterpriseAccess.Admission a,String context){PermissionSchema.revision(context);if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);if(a.formalWorker()||a.simulationWorker()||a.evaluationWorker())deny();}
 private static String tenant(EnterpriseAccess.Admission a){String tenant=a.context().tenant_id();if(!tenant.equals(TenantScope.requireCurrent().tenantId()))deny();return tenant;}
 private static String text(JsonNode n,String key){var v=n.required(key);if(!v.isTextual())bad();return v.textValue();}
 private static String str(Map<String,Object> r,String key){var v=r.get(key);if(v==null)bad();return v.toString();}
 private static String hash(Object n)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(n));}
 private static String id(String kind,String actor,String key){return UUID.nameUUIDFromBytes((kind+"|"+actor+"|"+key).getBytes(StandardCharsets.UTF_8)).toString();}
 private static void closed(JsonNode n,String... fields){ProfessionalRuntimeObservations.closed(n,Set.of(fields));}
 private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}
 private static <T>T one(List<T> rows){if(rows.size()!=1)deny();return rows.get(0);}
 private static Map<String,Object> metadata(Map<String,Object> row){if(!"1.0.0".equals(str(row,"schema_version"))||!MANIFEST.equals(str(row,"schema_manifest_sha256")))bad();return row;}
 private static String manifest(){try(var in=ApprovedWebAcquisitionService.class.getResourceAsStream("/approved-web-acquisition.schema.json")){if(in==null)throw new IllegalStateException("WEB_SCHEMA_MISSING");return WireValues.sha256(in.readAllBytes());}catch(java.io.IOException | java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static <T>T checked(ThrowingSupplier<T> call){return call.get();}
 private static void changed(int n){if(n!=1)stale();}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
