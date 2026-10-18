package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.SimulationEvaluationPreparationProtocol.Registration;
import com.ruoyi.medical.persistence.mapper.SimulationEvaluationPreparationMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Read-only shared ledger verification; never authorizes source use or manufacturing an artifact. */
public final class SimulationEvaluationPreparationEvidence {
 private SimulationEvaluationPreparationEvidence() { }
 public static void verify(EnterpriseAccess.Admission a,Registration row,SimulationEvaluationPreparationMapper mapper)throws Exception {
  var schema=new SimulationEvaluationPreparationSchema();same(a.context().tenant_id(),row.tenantId());same("1.0.0",row.schemaVersion());same(schema.manifest(),row.schemaManifest());
  var request=schema.request("Register",row.canonicalRequest().getBytes(StandardCharsets.UTF_8));same(canonical(request),row.canonicalRequest());
  same(hash(request),row.requestBodySha256());same(row.kind(),request.required("kind").textValue());same(row.sourceVersion(),request.required("source_version").textValue());
  same(row.rightsRevision(),request.required("rights_revision").textValue());same(row.documentSha256(),request.required("document_sha256").textValue());
  var document=schema.canonicalDocument(row.canonicalDocument().getBytes(StandardCharsets.UTF_8));same(row.documentSha256(),hash(document));
  WireValues.hash(row.operationRequestSha256());WireValues.uuid(row.artifactId());WireValues.uuid(row.createdBy());WireValues.uuid(row.operationId());
  var receipt=schema.validate("Receipt",schema.parse(row.receiptJson().getBytes(StandardCharsets.UTF_8)));
  var expected=JsonMapper.builder().build().createObjectNode().put("schema_version","1.0.0").putNull("project_id").put("kind",row.kind())
   .put("operation_id",row.operationId()).put("source_version",row.sourceVersion()).put("rights_revision",row.rightsRevision())
   .put("production_eligible",false).put("registered",true).put("approved",false).put("created_by",row.createdBy())
   .put("created_at",row.createdAt().toInstant(java.time.ZoneOffset.UTC).toString());
  expected.putObject("artifact").put("version_id",row.artifactId()).put("content_sha256",row.documentSha256());same(expected,receipt);
  var v=new HashMap<String,Object>();v.put("userId",a.actorId());v.put("operationId",row.operationId());v.put("creatorId",row.createdBy());
  v.put("artifactId",row.artifactId());v.put("operationManifest",schema.manifest());v.put("classification",row.classification());
  var op=one(mapper.operationById(v));same(row.operationId(),op.operationId());same(row.artifactId(),op.targetId());same("COMPLETED",op.state());same(Integer.valueOf(201),op.httpStatus());
  same(row.operationRequestSha256(),op.requestSha256());same("1.0.0",op.schemaVersion());same(schema.manifest(),op.schemaManifestSha256());
  same(receipt,schema.parse(op.responseRef().getBytes(StandardCharsets.UTF_8)));
  same(event(row),schema.parse(one(mapper.audit(v)).getBytes(StandardCharsets.UTF_8)));same(event(row),schema.parse(one(mapper.event(v)).getBytes(StandardCharsets.UTF_8)));
 }
 public static JsonNode event(Registration row)throws Exception {
  var json=JsonMapper.builder().build();var event=json.createObjectNode().put("tenant_id",row.tenantId()).put("operation_id",row.operationId()).put("artifact_id",row.artifactId())
   .put("canonical_request",row.canonicalRequest()).put("request_body_sha256",row.requestBodySha256()).put("operation_request_sha256",row.operationRequestSha256())
   .put("document_sha256",row.documentSha256()).put("workflow_schema_sha256",row.schemaManifest()).put("classification",row.classification());
  event.set("receipt",new SimulationEvaluationPreparationSchema().parse(row.receiptJson().getBytes(StandardCharsets.UTF_8)));return event;
 }
 private static String canonical(JsonNode n)throws Exception{return new String(ContentReadAuthorization.canonicalBytes(n),StandardCharsets.UTF_8);}
 private static String hash(JsonNode n)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(n));}
 private static <T>T one(List<T> values){if(values.size()!=1)mismatch();return values.get(0);}
 private static void same(Object a,Object b){if(!Objects.equals(a,b))mismatch();}
 private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
