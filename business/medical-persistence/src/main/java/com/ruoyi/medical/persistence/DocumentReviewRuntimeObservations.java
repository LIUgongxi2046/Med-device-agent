package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** Original-service reconciliation only; works after user revocation without granting new actions. */
public final class DocumentReviewRuntimeObservations {
 private final ServiceCertificateIdentity identities;private final TenantTransaction transactions;private final JdbcTemplate db;private final PermissionSchema json;
 public DocumentReviewRuntimeObservations(ServiceCertificateIdentity identities,TenantTransaction transactions,DataSource data,PermissionSchema json){this(identities,transactions,new JdbcTemplate(data),json);}
 DocumentReviewRuntimeObservations(ServiceCertificateIdentity identities,TenantTransaction transactions,JdbcTemplate db,PermissionSchema json){this.identities=identities;this.transactions=transactions;this.db=db;db.setQueryTimeout(5);this.json=json;}
 record Binding(String tenant_id,String run_id,String step_id,String action_id,long lease_generation,String input_sha256,String step_envelope_sha256){
  Binding{for(var value:List.of(tenant_id,run_id,step_id,action_id))WireValues.uuid(value);WireValues.hash(input_sha256);WireValues.hash(step_envelope_sha256);if(lease_generation<1)bad();}
 }
 record Session(String project,String actor,String service,String certificate,String trust,String state,long revision,String start,String startHash){}
 public JsonNode close(HttpServletRequest http,JsonNode request)throws Exception{return record(http,request,"ADMISSION_CLOSED");}
 public JsonNode failure(HttpServletRequest http,JsonNode request)throws Exception{return record(http,request,"HOST_FAILURE");}
 /** Recording an untrusted finish observation is intentionally not WorkflowProtocol.Completion. */
 public void finishObservation(HttpServletRequest http,JsonNode request)throws Exception{record(http,request,"HOST_FINISH");}
 private JsonNode record(HttpServletRequest http,JsonNode request,String kind)throws Exception{
  validate(request,kind);var service=identities.require(http);var b=jsonBinding(request.required("binding"));String session=request.required("session_id").asText();WireValues.uuid(session);
  var original=one(db.query("SELECT created_by FROM professional_runtime_session WHERE tenant_id=? AND session_id=? AND run_id=? AND step_id=? AND lease_generation=? AND service_uri=? AND certificate_sha256=? AND trust_revision=? LIMIT 2",(r,n)->r.getString(1),b.tenant_id(),session,b.run_id(),b.step_id(),b.lease_generation(),service.serviceUri(),service.certificateSha256(),service.trustRevision()));
  var epochs=one(db.query("SELECT auth_epoch,governance_epoch FROM tenant WHERE tenant_id=? LIMIT 2",(r,n)->new TenantWatermarks(r.getBigDecimal(1).toBigIntegerExact(),r.getBigDecimal(2).toBigIntegerExact()),b.tenant_id()));
  try(var scope=TenantScope.open(new TenantScope.Identity(b.tenant_id(),original,BigInteger.ZERO,epochs.authEpoch(),epochs.governanceEpoch()))){return transactions.execute(ignored->ThrowingSupplier.of(()->{
   var row=one(db.query("SELECT project_id,created_by,service_uri,certificate_sha256,trust_revision,admission_state,revision,canonical_start,start_sha256 FROM professional_runtime_session WHERE tenant_id=? AND session_id=? AND run_id=? AND step_id=? AND action_id=? AND lease_generation=? AND input_sha256=? AND envelope_sha256=? LIMIT 2 FOR UPDATE",(r,n)->new Session(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getLong(7),r.getString(8),r.getString(9)),b.tenant_id(),session,b.run_id(),b.step_id(),b.action_id(),b.lease_generation(),b.input_sha256(),b.step_envelope_sha256()));
   if(!row.actor().equals(original)||!row.service().equals(service.serviceUri())||!row.certificate().equals(service.certificateSha256())||!row.trust().equals(service.trustRevision())||!row.startHash().equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(row.start())))denied();
   var start=new com.fasterxml.jackson.databind.ObjectMapper().readTree(row.start());if(!FormalTaskNodeAuthority.sameEvidence(start.required("binding"),request.required("binding"))||!session.equals(start.required("session_id").asText())||!"SYSTEM_JOB".equals(start.required("purpose").asText())||!"N07".equals(start.required("node_kind").asText()))bad();
   if(one(db.query("SELECT COUNT(*) FROM document_review_execution d JOIN system_job_run j ON j.tenant_id=d.tenant_id AND j.run_id=d.run_id WHERE d.tenant_id=? AND d.run_id=? AND d.project_id=? AND d.created_by=? AND j.job_type='CHECK_DOCUMENT' AND j.execution_kind='SYSTEM_JOB' AND j.target_kind='DOCUMENT' AND j.target_id=d.document_id AND j.target_revision=d.document_revision",(r,n)->r.getInt(1),b.tenant_id(),b.run_id(),row.project(),original))!=1)denied();
   String bytes=json.canonical(request),hash=org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
   var previous=db.query("SELECT observation_id,canonical_observation FROM professional_runtime_observation WHERE tenant_id=? AND session_id=? AND kind=? AND observation_sha256=? LIMIT 2",(r,n)->Map.entry(r.getString(1),r.getString(2)),b.tenant_id(),session,kind,hash);
   if(previous.size()>1||!previous.isEmpty()&&!previous.get(0).getValue().equals(bytes))bad();
   String observation;if(previous.isEmpty()){observation=UUID.randomUUID().toString();oneChanged(db.update("INSERT INTO professional_runtime_observation (tenant_id,session_id,observation_id,kind,canonical_observation,observation_sha256,created_by,created_at) VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",b.tenant_id(),session,observation,kind,bytes,hash,original));}else observation=previous.get(0).getKey();
   if("OPEN".equals(row.state()))oneChanged(db.update("UPDATE professional_runtime_session SET admission_state='CLOSED',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND session_id=? AND admission_state='OPEN' AND revision=?",b.tenant_id(),session,row.revision()));else if(!"CLOSED".equals(row.state()))bad();
   var after=identities.require(http);if(!after.serviceUri().equals(service.serviceUri())||!after.certificateSha256().equals(service.certificateSha256())||!after.trustRevision().equals(service.trustRevision()))denied();
   var response=json.object();response.set("binding",request.required("binding"));response.put("session_id",session);response.put("admission","CLOSED");if(!"ADMISSION_CLOSED".equals(kind)){response.put("observation_id",observation);response.put("reconciliation","REQUIRED");}return response;
  }).get());}
 }
 /** Read-only original status after revocation/expiry. No lease, token, provider
  * retry or unknown-cost release is granted by this original-service reader. */
 public JsonNode status(HttpServletRequest http,JsonNode request)throws Exception {
  closed(request,Set.of("binding","session_id"));var b=jsonBinding(request.required("binding"));String session=text(request,"session_id");WireValues.uuid(session);var service=identities.require(http);
  var row=one(db.queryForList("SELECT s.admission_state,s.canonical_start,s.start_sha256,j.status execution_status FROM professional_runtime_session s JOIN document_review_execution d ON d.tenant_id=s.tenant_id AND d.run_id=s.run_id JOIN system_job_run j ON j.tenant_id=d.tenant_id AND j.run_id=d.run_id WHERE s.tenant_id=? AND s.session_id=? AND s.run_id=? AND s.step_id=? AND s.action_id=? AND s.lease_generation=? AND s.input_sha256=? AND s.envelope_sha256=? AND s.service_uri=? AND s.certificate_sha256=? AND s.trust_revision=? AND j.job_type='CHECK_DOCUMENT' LIMIT 2",b.tenant_id(),session,b.run_id(),b.step_id(),b.action_id(),b.lease_generation(),b.input_sha256(),b.step_envelope_sha256(),service.serviceUri(),service.certificateSha256(),service.trustRevision()));
  var start=new com.fasterxml.jackson.databind.ObjectMapper().readTree((String)row.get("canonical_start"));if(!Objects.equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex((String)row.get("canonical_start")),row.get("start_sha256"))||!"SYSTEM_JOB".equals(start.required("purpose").asText())||!"N07".equals(start.required("node_kind").asText())||!FormalTaskNodeAuthority.sameEvidence(start.required("binding"),request.required("binding")))bad();
  var out=json.object().put("session_id",session).put("admission",(String)row.get("admission_state")).put("execution_status",(String)row.get("execution_status"));out.set("binding",request.required("binding").deepCopy());var observations=out.putArray("observations");
  for(var original:db.queryForList("SELECT observation_id,kind,canonical_observation,observation_sha256 FROM professional_runtime_observation WHERE tenant_id=? AND session_id=? ORDER BY created_at,observation_id LIMIT 101",b.tenant_id(),session)){if(observations.size()>=100)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);String raw=(String)original.get("canonical_observation");if(!Objects.equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(raw),original.get("observation_sha256")))bad();var evidence=new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);validate(evidence,(String)original.get("kind"));if(!FormalTaskNodeAuthority.sameEvidence(evidence.required("binding"),request.required("binding")))bad();var item=observations.addObject().put("observation_id",(String)original.get("observation_id")).put("kind",(String)original.get("kind")).put("sha256",(String)original.get("observation_sha256"));if(evidence.has("reason"))item.put("reason",evidence.required("reason").textValue());}
  out.put("reconciliation",observations.isEmpty()?"NO_HOST_FAILURE_RECORDED":"REQUIRED");out.put("process_exit","NOT_ATTESTED_BY_HOST_OBSERVATION");var after=identities.require(http);if(!after.serviceUri().equals(service.serviceUri())||!after.certificateSha256().equals(service.certificateSha256())||!after.trustRevision().equals(service.trustRevision()))denied();return out;
 }
 static void validate(JsonNode request,String kind){
  Set<String> fields=switch(kind){case "ADMISSION_CLOSED"->Set.of("binding","session_id","reason");case "HOST_FAILURE"->Set.of("binding","session_id","reason","host_process_exited");case "HOST_FINISH"->Set.of("binding","session_id","observation","files");default->throw new IllegalArgumentException();};closed(request,fields);jsonBinding(request.required("binding"));WireValues.uuid(text(request,"session_id"));
  if(!"HOST_FINISH".equals(kind)){String reason=text(request,"reason");if(reason.isBlank()||reason.length()>256)bad();}
  if("HOST_FAILURE".equals(kind)&&!request.required("host_process_exited").isBoolean())bad();
  if("HOST_FINISH".equals(kind)){var files=request.required("files");if(!files.isArray()||files.size()>256)bad();for(var file:files){closed(file,Set.of("path","sha256","size_bytes"));String path=text(file,"path");if(path.isBlank()||path.length()>2048||path.startsWith("/")||path.contains("..")||path.contains("\\"))bad();WireValues.hash(text(file,"sha256"));if(!file.required("size_bytes").isIntegralNumber()||!file.required("size_bytes").canConvertToLong()||file.required("size_bytes").longValue()<0)bad();}
   var observation=request.required("observation");closed(observation,Set.of("session_id","binding","finish_reason","final_response"));if(!observation.required("session_id").equals(request.required("session_id"))||!FormalTaskNodeAuthority.sameEvidence(observation.required("binding"),request.required("binding")))bad();if(!observation.required("finish_reason").isTextual())bad();
  }
 }
 static Binding jsonBinding(JsonNode b){closed(b,Set.of("tenant_id","run_id","step_id","action_id","lease_generation","input_sha256","step_envelope_sha256"));if(!b.required("lease_generation").isIntegralNumber()||!b.required("lease_generation").canConvertToLong())bad();return new Binding(text(b,"tenant_id"),text(b,"run_id"),text(b,"step_id"),text(b,"action_id"),b.required("lease_generation").longValue(),text(b,"input_sha256"),text(b,"step_envelope_sha256"));}
 static void closed(JsonNode n,Set<String> fields){if(!n.isObject())bad();var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!actual.equals(fields))bad();}
 private static String text(JsonNode n,String field){if(!n.required(field).isTextual())bad();return n.required(field).textValue();}
 private static <T>T one(List<T> rows){if(rows.size()!=1)denied();return rows.get(0);}private static void oneChanged(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
 private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
