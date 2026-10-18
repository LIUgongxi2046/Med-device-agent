package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Component;

/** Immutable original-key evidence shared by authoring/acceptance/publication. It never grants authority. */
@Component public final class AgentPublicationJournal {
 private final AgentPublicationMapper mapper;private final PermissionMapper permissions;private final AgentPublicationSchema schema;private final PermissionSchema json;
 public AgentPublicationJournal(AgentPublicationMapper mapper,PermissionMapper permissions,AgentPublicationSchema schema,PermissionSchema json){this.mapper=mapper;this.permissions=permissions;this.schema=schema;this.json=json;}
 Map<String,Object> values(EnterpriseAccess.Admission a){var v=new HashMap<String,Object>();v.put("userId",a.actorId());v.put("now",Objects.requireNonNull(permissions.databaseTime()));return v;}
 AgentPublicationMapper.Journal prior(EnterpriseAccess.Admission a,String kind,String key,JsonNode request)throws IOException{
  SimulationInputArtifactSchema.key(key);var v=values(a);v.put("kind",kind);v.put("key",key);v.put("creatorId",a.actorId());v.put("operationName","AGENT-PUBLICATION-"+kind);
  var rows=mapper.journalByKey(v);var ops=mapper.operation(v);if(rows.size()>1||ops.size()>1||rows.isEmpty()!=ops.isEmpty())bad();
  if(rows.isEmpty())return null;var row=rows.get(0);verify(a,row);
  if(!row.requestSha256().equals(schema.requestHash(kind,request)))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);return row;
 }
 AgentPublicationMapper.Journal read(EnterpriseAccess.Admission a,String id)throws IOException{WireValues.uuid(id);var v=values(a);v.put("recordId",id);var row=one(mapper.journal(v));verify(a,row);return row;}
 AgentPublicationMapper.Journal save(EnterpriseAccess.Admission a,String project,String id,String kind,String resource,String key,JsonNode request,JsonNode content,JsonNode authorizations,String classification)throws IOException{
  return save(a,project,id,kind,resource,key,request,content,authorizations,classification,Objects.requireNonNull(permissions.databaseTime()));
 }
 AgentPublicationMapper.Journal save(EnterpriseAccess.Admission a,String project,String id,String kind,String resource,String key,JsonNode request,JsonNode content,JsonNode authorizations,String classification,LocalDateTime now)throws IOException{
  if(now==null||now.isAfter(Objects.requireNonNull(permissions.databaseTime())))bad();
  if(prior(a,kind,key,request)!=null)bad();String op=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();
  var receipt=schema.receipt(op,id,kind,schema.hash(content),schema.hash(request));
  var row=new AgentPublicationMapper.Journal(a.context().tenant_id(),project,id,kind,resource,op,key,schema.requestHash(kind,request),schema.canonical(request),schema.canonical(content),schema.hash(content),schema.canonical(authorizations),schema.hash(authorizations),schema.canonical(receipt),schema.hash(receipt),audit,event,"1.0.0",schema.manifest(),a.actorId(),now,classification);
  var v=values(a);v.put("row",row);v.put("operationName","AGENT-PUBLICATION-"+kind);one(mapper.insertOperation(v));
  String evidence=schema.canonical(evidence(row));one(permissions.saveAudit(a.actorId(),project,audit,"agent.publication."+kind.toLowerCase(Locale.ROOT),evidence,op,schema.manifest(),classification));
  one(permissions.saveEvent(a.actorId(),event,id,"1","agent.publication."+kind.toLowerCase(Locale.ROOT),evidence));one(mapper.insertJournal(v));
  var actual=read(a,id);if(!row.equals(actual))bad();return actual;
 }
 public void verify(EnterpriseAccess.Admission a,AgentPublicationMapper.Journal row)throws IOException{verifyRow(a.context().tenant_id(),a.actorId(),row);}
 /** Only verifies immutable metadata under an already authenticated control-plane SQL scope. No content permission is issued. */
 public void verifyMetadata(String tenant,String actor,AgentPublicationMapper.Journal row)throws IOException{
  var scope=TenantScope.requireCurrent();if(!scope.tenantId().equals(tenant)||!scope.subjectId().equals(actor))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  verifyRow(tenant,actor,row);
 }
 private void verifyRow(String tenant,String actor,AgentPublicationMapper.Journal row)throws IOException{
  if(!tenant.equals(row.tenantId())||!"1.0.0".equals(row.schemaVersion())||!schema.manifest().equals(row.schemaManifest())||row.createdAt()==null)bad();
  WireValues.uuid(row.recordId());WireValues.uuid(row.resourceId());WireValues.uuid(row.createdBy());SimulationInputArtifactSchema.key(row.idempotencyKey());
  var request=schema.read(row.requestJson());var content=schema.read(row.contentJson());var auth=schema.read(row.authorizationsJson());var receipt=schema.validate("Receipt",schema.read(row.receiptJson()));
  if(!schema.canonical(request).equals(row.requestJson())||!schema.requestHash(row.kind(),request).equals(row.requestSha256())||!schema.canonical(content).equals(row.contentJson())||!schema.hash(content).equals(row.contentSha256())||!schema.canonical(auth).equals(row.authorizationsJson())||!schema.hash(auth).equals(row.authorizationsSha256())||!schema.canonical(receipt).equals(row.receiptJson())||!schema.hash(receipt).equals(row.receiptSha256()))bad();
  same(schema.receipt(row.operationId(),row.recordId(),row.kind(),row.contentSha256(),schema.hash(request)),receipt);
  var v=new HashMap<String,Object>();v.put("userId",actor);v.put("creatorId",row.createdBy());v.put("key",row.idempotencyKey());v.put("kind",row.kind());v.put("operationName","AGENT-PUBLICATION-"+row.kind());v.put("row",row);v.put("recordId",row.recordId());
  var registered=one(mapper.version(v));if(!row.recordId().equals(registered.id())||!Objects.equals(row.projectId(),registered.projectId())||!"AGENT_PUBLICATION_RECORD".equals(registered.entityKind())||!row.contentSha256().equals(registered.contentSha256()))bad();
  if(!row.equals(one(mapper.journalByKey(v))))bad();var op=one(mapper.operation(v));
  if(!row.operationId().equals(op.operationId())||!row.recordId().equals(op.targetId())||!row.requestSha256().equals(op.requestSha256())||!"COMPLETED".equals(op.state())||!Integer.valueOf(201).equals(op.httpStatus())||!"1.0.0".equals(op.schemaVersion())||!schema.manifest().equals(op.schemaManifestSha256()))bad();
  same(receipt,schema.read(op.responseRef()));var expected=evidence(row);same(expected,schema.read(one(mapper.audit(v))));same(expected,schema.read(one(mapper.event(v))));
 }
 private JsonNode evidence(AgentPublicationMapper.Journal r)throws IOException{
  var e=json.object().put("schema_version","1.0.0").put("kind",r.kind()).put("record_id",r.recordId()).put("resource_id",r.resourceId()).put("operation_id",r.operationId()).put("idempotency_key_sha256",org.apache.commons.codec.digest.DigestUtils.sha256Hex(r.idempotencyKey().getBytes(java.nio.charset.StandardCharsets.US_ASCII))).put("request_sha256",r.requestSha256()).put("content_sha256",r.contentSha256()).put("created_by",r.createdBy()).put("created_at",r.createdAt().toString()).put("classification",r.classification());
  e.set("request",schema.read(r.requestJson()));e.set("content",schema.read(r.contentJson()));e.set("authorizations",schema.read(r.authorizationsJson()));e.set("receipt",schema.read(r.receiptJson()));return e;
 }
 private void same(JsonNode x,JsonNode y)throws IOException{if(!schema.canonical(x).equals(schema.canonical(y)))bad();}
 static <T>T one(List<T> x){if(x.size()!=1)bad();return x.get(0);}static void one(int n){if(n!=1)bad();}static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
