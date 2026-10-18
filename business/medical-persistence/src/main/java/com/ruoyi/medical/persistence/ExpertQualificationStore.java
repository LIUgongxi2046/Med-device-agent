package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.ExpertQualificationMapper;
import com.ruoyi.medical.persistence.ExpertQualificationProtocol.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
/** Immutable journal and generic-operation reconciliation. Every map is bound to the current actor. */
@Component @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="medical.expert-qualification",name="enabled",havingValue="true") public final class ExpertQualificationStore {
 private final ExpertQualificationMapper mapper;private final ExpertQualificationSchema schema;private final PermissionSchema permissions;private final EvaluationSchema evaluations;
 public ExpertQualificationStore(ExpertQualificationMapper mapper,ExpertQualificationSchema schema,PermissionSchema permissions,EvaluationSchema evaluations){this.mapper=mapper;this.schema=schema;this.permissions=permissions;this.evaluations=evaluations;}
 static Map<String,Object> values(EnterpriseAccess.Admission a){var scope=TenantScope.requireCurrent();if(!scope.tenantId().equals(a.context().tenant_id())||!scope.subjectId().equals(a.actorId())||a.simulationWorker())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);var v=new HashMap<String,Object>();v.put("userId",a.actorId());return v;}
 Journal prior(EnterpriseAccess.Admission a,String kind,String key,JsonNode request)throws IOException{
  var v=values(a);v.put("operationName",operation(kind));v.put("key",key);var rows=mapper.operation(v);if(rows.size()>1)bad();if(rows.isEmpty())return null;
  var op=rows.get(0);if(!schema.operationHash(a.context().tenant_id(),a.actorId(),kind,request).equals(op.requestSha256()))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
  Journal row=read(a,op.targetId());same(a.actorId(),row.createdBy());same(kind,row.kind());same(key,row.idempotencyKey());same(schema.canonical(request),row.requestJson());return row;
 }
 Journal read(EnterpriseAccess.Admission a,String id)throws IOException{var v=values(a);v.put("recordId",id);var row=available(mapper.journal(v));verify(a,row);return row;}
 Journal save(EnterpriseAccess.Admission a,String project,String recordId,String qualification,String kind,String key,String context,String session,JsonNode request,JsonNode content,JsonNode authorizations,String classification,LocalDateTime now)throws IOException{
  ExpertQualificationSchema.key(key);PermissionSchema.revision(context);WireValues.uuid(session);validateContent(kind,content);
  if(prior(a,kind,key,request)!=null)bad();String op=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();
  String contentHash=schema.hash(content),bodyHash=schema.hash(request);PermissionProtocol.Basis qualificationBasis=kind.equals("AUTHOR")?new PermissionProtocol.Basis(qualification,contentHash):schema.bind(request.path("qualification"),PermissionProtocol.Basis.class);
  var receipt=receipt(recordId,kind,op,contentHash,qualificationBasis,bodyHash,qualificationRevision(kind,request),a.actorId(),now);
  Journal row=new Journal(a.context().tenant_id(),project,recordId,kind,qualification,op,key,schema.operationHash(a.context().tenant_id(),a.actorId(),kind,request),bodyHash,schema.canonical(request),context,session,schema.canonical(content),contentHash,schema.canonical(authorizations),schema.hash(authorizations),schema.canonical(receipt),schema.hash(receipt),audit,event,"1.0.0",schema.manifest(),a.actorId(),now,classification);
  var v=values(a);v.put("row",row);v.put("operationName",operation(kind));v.put("eventType",eventType(kind));v.put("payload",schema.canonical(event(row)));v.put("entityKind",kind.equals("AUTHOR")?"EXPERT_QUALIFICATION":"EXPERT_EVIDENCE_RECORD");
  count(mapper.insertVersion(v));count(mapper.saveOperation(v));count(mapper.saveAudit(v));count(mapper.saveEvent(v));count(mapper.insertJournal(v));same(row,read(a,recordId));return row;
 }
 void verify(EnterpriseAccess.Admission a,Journal row)throws IOException{
  same(a.context().tenant_id(),row.tenantId());same("1.0.0",row.schemaVersion());same(schema.manifest(),row.schemaManifest());ExpertQualificationSchema.key(row.idempotencyKey());PermissionSchema.revision(row.requestContext());WireValues.uuid(row.requestSession());
  var request=schema.validate(ExpertQualificationSchema.requestKind(row.kind()),parse(row.requestJson()));var content=parse(row.contentJson());validateContent(row.kind(),content);var auth=parse(row.authorizationsJson());var receipt=schema.validate("Receipt",parse(row.receiptJson()));
  same(row.requestJson(),schema.canonical(request));same(row.bodyHash(),schema.hash(request));same(row.requestHash(),schema.operationHash(row.tenantId(),row.createdBy(),row.kind(),request));same(row.contentJson(),schema.canonical(content));same(row.contentHash(),schema.hash(content));same(row.authorizationsJson(),schema.canonical(auth));same(row.authorizationsHash(),schema.hash(auth));same(row.receiptJson(),schema.canonical(receipt));same(row.receiptHash(),schema.hash(receipt));
  PermissionProtocol.Basis basis=row.kind().equals("AUTHOR")?new PermissionProtocol.Basis(row.qualificationId(),row.contentHash()):schema.bind(request.path("qualification"),PermissionProtocol.Basis.class);
  same(receipt(row.recordId(),row.kind(),row.operationId(),row.contentHash(),basis,row.bodyHash(),qualificationRevision(row.kind(),request),row.createdBy(),row.createdAt()),receipt);
  if(row.kind().equals("AUTHOR")){same(row.recordId(),row.qualificationId());same(row.qualificationId(),content.path("qualification_id").asText());same(row.createdBy(),content.path("created_by").asText());same(time(row.createdAt()),content.path("created_at").asText());same(request,content.path("request"));same(row.tenantId(),content.path("tenant_id").asText());same(row.projectId(),textOrNull(request.path("project_id")));}
  else if(row.kind().equals("SCORE_BIND")){same(request,content.path("request"));same(row.createdBy(),content.path("bound_by").asText());same(time(row.createdAt()),content.path("bound_at").asText());}
  else{same(request,content.path("request"));same(row.kind(),content.path("decision").asText());same(row.createdBy(),content.path("decided_by").asText());same(time(row.createdAt()),content.path("decided_at").asText());same(request.path("qualification"),content.path("qualification"));}
  String action=row.kind().equals("AUTHOR")?"agent.configure":row.kind().equals("SCORE_BIND")?"evaluation.score":"agent.publish";
  var scope=row.kind().equals("SCORE_BIND")?new PermissionProtocol.Scope(row.tenantId(),row.projectId(),"EVALUATION",request.path("evaluation_id").asText()):scope(row.tenantId(),row.projectId());
  var grant=schema.bind(auth.path("governance"),PermissionProtocol.GrantRow.class);String purpose=row.kind().equals("SCORE_BIND")?"EVALUATION_RESULTS":"AGENT_PUBLICATION";
  if(new PermissionAuthority(permissions).findResource(scope,row.createdBy(),auth.path("identity_mode").asText(),action,purpose,List.of(grant),row.createdAt()).isEmpty())bad();
  var v=values(a);v.put("row",row);v.put("recordId",row.recordId());v.put("operationId",row.operationId());v.put("creatorId",row.createdBy());v.put("key",row.idempotencyKey());v.put("operationName",operation(row.kind()));v.put("eventType",eventType(row.kind()));
  var version=one(mapper.version(v));same(row.recordId(),version.id());same(row.projectId(),version.projectId());same(row.contentHash(),version.contentSha256());same(row.kind().equals("AUTHOR")?"EXPERT_QUALIFICATION":"EXPERT_EVIDENCE_RECORD",version.entityKind());
  var op=one(mapper.operationById(v));same(row.operationId(),op.operationId());same(row.requestHash(),op.requestSha256());same(row.recordId(),op.targetId());same("COMPLETED",op.state());same(Integer.valueOf(201),op.httpStatus());same("1.0.0",op.schemaVersion());same(schema.manifest(),op.schemaManifestSha256());same(receipt,parse(op.responseRef()));same(event(row),parse(one(mapper.audit(v))));same(event(row),parse(one(mapper.event(v))));
 }
 private void validateContent(String kind,JsonNode content)throws IOException{
  schema.validate(kind.equals("AUTHOR")?"Definition":kind.equals("SCORE_BIND")?"BoundScore":"Decision",content);
  if(kind.equals("AUTHOR"))ExpertQualificationSchema.semantics(schema.bind(content.path("request"),Author.class));
  if(kind.equals("SCORE_BIND"))evaluations.score(schema.canonical(content.path("score")));
 }
 JsonNode parse(String value)throws IOException{return schema.parse(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 private ObjectNode receipt(String id,String kind,String op,String hash,PermissionProtocol.Basis qualification,String requestHash,String revision,String actor,LocalDateTime now){var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("kind",kind).put("operation_id",op).put("request_body_sha256",requestHash).put("qualification_revision",revision).put("created_by",actor).put("created_at",time(now));n.set("record",schema.tree(new PermissionProtocol.Basis(id,hash)));n.set("qualification",schema.tree(qualification));schema.validate("Receipt",n);return n;}
 private JsonNode event(Journal r){var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();n.set("journal",schema.tree(r));return n;}
 static PermissionProtocol.Scope scope(String tenant,String project){return new PermissionProtocol.Scope(tenant,project,project==null?"ENTERPRISE":"PROJECT",project==null?tenant:project);}
 private static String qualificationRevision(String kind,JsonNode request){return kind.equals("AUTHOR")?"1":kind.equals("SCORE_BIND")?"2":new java.math.BigInteger(request.path("expected_revision").asText()).add(java.math.BigInteger.ONE).toString();}
 static String operation(String kind){ExpertQualificationSchema.requestKind(kind);return "EXPERT-QUALIFICATION-"+kind;}
 static String eventType(String kind){return "expert.qualification."+kind.toLowerCase(Locale.ROOT);}
 static String time(LocalDateTime now){return now.toInstant(ZoneOffset.UTC).toString();}
 static String textOrNull(JsonNode node){return node.isNull()?null:node.asText();}
 static <T>T available(List<T> rows){if(rows.isEmpty())throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return one(rows);}
 static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}static void count(int count){if(count!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
 static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
