package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** Durable original execution admission registration. It creates no run, lease, invocation or success.
 * Creation is atomic under EnterpriseAccess's tenant transaction. Recovery rereads original audit/outbox.
 * The returned registration is data, not executable authority or completed professional evidence. */
public final class FormalEvaluationExecutionRegistrations {
 private final EnterpriseAccess access;private final FormalEvaluationExecutionInputs inputs;
 private final FormalEvaluationModelPreparation modelPreparation;private final FormalEvaluationExecutionScopes scopes;private final PermissionSchema json;private final JdbcTemplate db;
 private final JsonMapper decoder=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public FormalEvaluationExecutionRegistrations(EnterpriseAccess access,FormalEvaluationExecutionInputs inputs,FormalEvaluationExecutionScopes scopes,FormalEvaluationModelPreparation modelPreparation,PermissionSchema json,DataSource data){this(access,inputs,scopes,modelPreparation,json,new JdbcTemplate(data));}
 FormalEvaluationExecutionRegistrations(EnterpriseAccess access,FormalEvaluationExecutionInputs inputs,FormalEvaluationExecutionScopes scopes,FormalEvaluationModelPreparation modelPreparation,PermissionSchema json,JdbcTemplate db){this.modelPreparation=Objects.requireNonNull(modelPreparation);this.access=access;this.inputs=inputs;this.scopes=scopes;this.json=json;this.db=db;db.setQueryTimeout(5);}
 public record Registration(String executionId,String requestSha256,int expectedCells,int expectedNodes){}
 public Registration register(ApplicationSessionIdentity.Verified actor,String context,String key,FormalEvaluationExecutionProtocol.Start request)throws Exception{
  json.headers(context,key);var prepared=inputs.prepare(actor,context,request);
  return access.execute(actor,a->ThrowingSupplier.of(()->register(a,key,prepared)).get());
 }
 Registration register(EnterpriseAccess.Admission a,String key,FormalEvaluationExecutionInputs.Prepared prepared)throws Exception{
  FormalEvaluationExecutionAdmission.current(a);if(key==null||!key.matches("[!-~]{16,128}"))throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
  String tenant=a.context().tenant_id(),actor=a.actorId();var admitted=prepared.requireCurrent(a);
  String request=json.canonical(json.tree(admitted.request())),requestHash=hash(request);
  // Same original actor lock serializes both idempotency key and candidate uniqueness races.
  if(db.queryForList("SELECT user_id FROM tenant_member WHERE tenant_id=? AND user_id=? FOR UPDATE",tenant,actor).size()!=1)denied();
  var old=read(a,key,requestHash);if(old.isPresent())return old.get();
  var sameTarget=db.queryForList("SELECT execution_id FROM formal_evaluation_execution WHERE tenant_id=? AND evaluation_id=? AND candidate_version=? LIMIT 2",tenant,admitted.request().evaluationId(),admitted.request().candidate().version_id());
  if(!sameTarget.isEmpty())throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
  var snapshot=scopes.save(a,prepared);snapshot.requireCurrent(a);admitted=prepared.requireCurrent(a);
  var modelProofs=new ArrayList<FormalEvaluationModelPreparation.Prepared>();for(var node:admitted.nodes())if(node.model()!=null)modelProofs.add(modelPreparation.require(a,prepared,snapshot,node));
  String execution=id(),auditId=id(),eventId=id();var times=db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime());if(times.size()!=1)bad();LocalDateTime now=times.get(0);
  int cells=(int)admitted.nodes().stream().map(FormalEvaluationExecutionProtocol.Node::cell).distinct().count();
  var admission=(com.fasterxml.jackson.databind.node.ObjectNode)decoder.readTree(admitted.canonical());
  var modelDocuments=admission.putArray("model_preparations");for(var proof:modelProofs)modelDocuments.add(decoder.readTree(proof.canonical()));
  admission.set("source_snapshot",json.tree(snapshot.snapshot()));admission.put("profile_revision",snapshot.profileRevision());
  String admissionJson=json.canonical(admission);var row=new LinkedHashMap<String,Object>();
  row.put("tenant_id",tenant);row.put("project_id",admitted.projectId());row.put("execution_id",execution);row.put("evaluation_id",admitted.request().evaluationId());row.put("definition_sha256",admitted.request().definitionSha256());
  row.put("candidate_version",admitted.request().candidate().version_id());row.put("candidate_sha256",admitted.request().candidate().content_sha256());row.put("policy_version",admitted.request().executionPolicy().version_id());row.put("policy_sha256",admitted.request().executionPolicy().content_sha256());row.put("idempotency_key",key);
  row.put("canonical_request",request);row.put("request_sha256",requestHash);row.put("canonical_admission",admissionJson);row.put("admission_sha256",hash(admissionJson));row.put("runtime_manifest_sha256",admitted.nodes().get(0).cell().runtimeManifestSha256());row.put("expected_cells",cells);row.put("expected_nodes",admitted.nodes().size());row.put("audit_id",auditId);row.put("event_id",eventId);
  row.put("schema_version","1.0.0");row.put("schema_manifest_sha256",json.manifestSha256());row.put("created_by",actor);row.put("created_at",now);row.put("classification",admitted.classification());
  var event=FormalEvaluationExecutionLedger.event(row,json);String eventJson=json.canonical(event);
  // FK parents are persisted first in the same transaction. This event attests registration only.
  changed(db.update("INSERT INTO audit_event (tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,'evaluation.execution_prepared',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",tenant,admitted.projectId(),auditId,actor,eventJson,execution,execution,json.manifestSha256(),actor,now,admitted.classification()));
  changed(db.update("INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES (?,?,?,1,'evaluation.execution_prepared',?,?)",tenant,eventId,execution,eventJson,now));
  var keys=new ArrayList<>(row.keySet());changed(db.update("INSERT INTO formal_evaluation_execution ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(row::get).toArray()));
  snapshot.requireCurrent(a);prepared.requireCurrent(a);for(var proof:modelProofs)proof.requireCurrent(a);
  return read(a,key,requestHash).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
 }
 /** Original registration lookup only; does not create missing preparation. */
 Registration requireOriginal(EnterpriseAccess.Admission a,String key,FormalEvaluationExecutionInputs.Prepared inputs)throws Exception{
  FormalEvaluationExecutionAdmission.current(a);var admitted=inputs.requireCurrent(a);String requestHash=hash(json.canonical(json.tree(admitted.request())));
  return read(a,key,requestHash).orElseThrow(()->new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE));
 }
 private Optional<Registration> read(EnterpriseAccess.Admission a,String key,String requestHash)throws Exception{
  var rows=db.queryForList("SELECT * FROM formal_evaluation_execution WHERE tenant_id=? AND created_by=? AND idempotency_key=? LIMIT 2",a.context().tenant_id(),a.actorId(),key);if(rows.size()>1)bad();if(rows.isEmpty())return Optional.empty();var row=rows.get(0);
  if(!requestHash.equals(row.get("request_sha256")))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
  if(!requestHash.equals(hash(text(row,"canonical_request")))||!hash(text(row,"canonical_admission")).equals(row.get("admission_sha256"))||!"1.0.0".equals(row.get("schema_version"))||!json.manifestSha256().equals(row.get("schema_manifest_sha256")))bad();
  var event=FormalEvaluationExecutionLedger.event(row,json);String tenant=a.context().tenant_id(),execution=text(row,"execution_id");
  var audit=db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND audit_id=? AND project_id=? AND subject_id=? AND created_by=? AND action='evaluation.execution_prepared' AND result='SUCCESS' AND request_id=? AND trace_id=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND created_at=? AND classification=? LIMIT 2",tenant,row.get("audit_id"),row.get("project_id"),a.actorId(),a.actorId(),execution,execution,json.manifestSha256(),row.get("created_at"),row.get("classification"));
  var emitted=db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type='evaluation.execution_prepared' AND occurred_at=? LIMIT 2",tenant,row.get("event_id"),execution,row.get("created_at"));
  if(audit.size()!=1||emitted.size()!=1||!event.equals(decoder.readTree(text(audit.get(0),"resource_ref")))||!event.equals(decoder.readTree(text(emitted.get(0),"payload"))))bad();
  return Optional.of(new Registration(execution,requestHash,((Number)row.get("expected_cells")).intValue(),((Number)row.get("expected_nodes")).intValue()));
 }
 private static String text(Map<String,Object> row,String k){if(!(row.get(k) instanceof String s))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);return s;}
 private static String id(){return UUID.randomUUID().toString();}private static String hash(String s){return org.apache.commons.codec.digest.DigestUtils.sha256Hex(s);}
 private static void changed(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
