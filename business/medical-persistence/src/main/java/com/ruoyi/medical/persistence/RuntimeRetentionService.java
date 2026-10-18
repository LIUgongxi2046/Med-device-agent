package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;import java.io.*;import java.time.*;import java.util.*;
/** Atomic author with original-key read-only recovery. No external effects in this transaction. */
public final class RuntimeRetentionService {
 private final EnterpriseAccess access;private final RuntimeRetentionMapper mapper;private final PermissionMapper permissions;private final ResourceReadAuthority resources;private final RuntimeRetentionAuthority authority;private final RuntimeRetentionSchema schema;private final PermissionSchema json;private final RuntimeRetentionDecisions decisions;
 public RuntimeRetentionService(EnterpriseAccess access,RuntimeRetentionMapper mapper,PermissionMapper permissions,ResourceReadAuthority resources,RuntimeRetentionAuthority authority,RuntimeRetentionSchema schema,PermissionSchema json,RuntimeRetentionDecisions decisions){this.access=access;this.mapper=mapper;this.permissions=permissions;this.resources=resources;this.authority=authority;this.schema=schema;this.json=json;this.decisions=decisions;}
 public JsonNode approve(ApplicationSessionIdentity.Verified actor,String context,String key,byte[] body){RuntimeRetentionSchema.key(key);return access.execute(actor,a->checked(()->{
  current(a,context);var request=schema.request("RetentionRequest",body);RuntimeRetentionCancellation.requireNotCancelled(mapper,schema,a,"APPROVE",key,request);String project=RuntimeRetentionAuthority.nullable(request.required("project_id"));
  var grant=resources.require(a,RuntimeRetentionAuthority.scope(a.context().tenant_id(),project),"retention.approve","RUNTIME_RETENTION_APPROVAL");
  var original=mapper.byKey(Map.of("userId",a.actorId(),"key",key));if(original.size()>1)reject(ErrorCode.CONTENT_MISMATCH);if(!original.isEmpty()){
   var saved=original.get(0);if(!saved.creator().equals(a.actorId())||!saved.key().equals(key))reject(ErrorCode.CONTENT_MISMATCH);if(!saved.requestHash().equals(schema.hash(request)))reject(ErrorCode.IDEMPOTENCY_CONFLICT);authority.verify(saved);return schema.read(saved.receiptJson());}
  var now=permissions.databaseTime();if(!now.toInstant(ZoneOffset.UTC).isBefore(Instant.parse(request.required("effective_until").textValue())))reject(ErrorCode.SCHEMA_INVALID);
  if(project!=null&&!List.of("ACTIVE","PENDING").contains(RuntimeRetentionAuthority.one(permissions.projectStatus(project))))reject(ErrorCode.ACCESS_DENIED);
  String id=UUID.randomUUID().toString(),operation=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();
  var fixed=json.object();fixed.put("schema_version","1.0.0");fixed.put("tenant_id",a.context().tenant_id());fixed.put("policy_revision",id);fixed.set("request",request);fixed.put("approved_by",a.actorId());fixed.put("approved_at",now.toInstant(ZoneOffset.UTC).toString());fixed.set("approval_grant",json.tree(Map.of("grant_id",grant.grantId(),"revision",grant.revision().toString())));schema.validate("RetentionFixed",fixed);
  var receipt=json.object();receipt.put("schema_version","1.0.0");receipt.put("policy_revision",id);receipt.put("operation_id",operation);receipt.put("request_body_sha256",schema.hash(request));receipt.put("content_sha256",schema.hash(fixed));receipt.put("status","APPROVED");schema.validate("RetentionReceipt",receipt);
  var v=new HashMap<String,Object>();v.put("userId",a.actorId());v.put("projectId",project);v.put("policyRevision",id);v.put("fixedJson",schema.canonical(fixed));v.put("requestJson",schema.canonical(request));v.put("requestHash",schema.hash(request));v.put("contentHash",schema.hash(fixed));v.put("operationId",operation);v.put("key",key);v.put("now",now);v.put("classification",request.required("classification").textValue());v.put("schemaVersion","1.0.0");v.put("manifest",schema.manifest());v.put("auditId",audit);v.put("eventId",event);v.put("receiptJson",schema.canonical(receipt));v.put("sessionId",actor.sessionId());v.put("contextJson",schema.canonical(a.context()));v.put("effectiveFrom",LocalDateTime.ofInstant(Instant.parse(request.required("effective_from").textValue()),ZoneOffset.UTC));v.put("effectiveUntil",LocalDateTime.ofInstant(Instant.parse(request.required("effective_until").textValue()),ZoneOffset.UTC));
  var witness=authority.approvalWitness(receipt,actor.sessionId(),(String)v.get("contextJson"),key,now.toInstant(ZoneOffset.UTC).toString());
  one(mapper.insertVersion(v));one(mapper.insertOperation(v));one(permissions.saveAudit(a.actorId(),project,audit,"runtime.retention.approved",schema.canonical(witness),operation,schema.manifest(),(String)v.get("classification")));one(permissions.saveEvent(a.actorId(),event,id,"1","runtime.retention.approved",schema.canonical(witness)));one(mapper.insertPolicy(v));one(mapper.initializeState(v));decisions.replace(a,request,id,key,operation,receipt);
  authority.verify(RuntimeRetentionAuthority.one(mapper.fixed(RuntimeRetentionAuthority.parameters("policyRevision",id))));
  var after=resources.require(a,RuntimeRetentionAuthority.scope(a.context().tenant_id(),project),"retention.approve","RUNTIME_RETENTION_APPROVAL");if(!grant.equals(after))reject(ErrorCode.SCOPE_STALE);return receipt;
 }));}
 public JsonNode recover(ApplicationSessionIdentity.Verified actor,String context,String key){RuntimeRetentionSchema.key(key);return access.execute(actor,a->checked(()->{
  current(a,context);var rows=mapper.byKey(Map.of("userId",a.actorId(),"key",key));if(rows.size()>1)reject(ErrorCode.CONTENT_MISMATCH);if(rows.isEmpty())return json.object().put("schema_version","1.0.0").put("status","NOT_FOUND").putNull("receipt");
  var row=rows.get(0);if(!row.creator().equals(a.actorId())||!row.key().equals(key))reject(ErrorCode.CONTENT_MISMATCH);resources.require(a,RuntimeRetentionAuthority.scope(a.context().tenant_id(),row.projectId()),"retention.read","RUNTIME_RETENTION_READING");authority.verify(row);var result=json.object().put("schema_version","1.0.0").put("status","FOUND");result.set("receipt",schema.read(row.receiptJson()));return result;
 }));}
 private static void current(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))reject(ErrorCode.CONTEXT_CHANGED);}
 private static void one(int count){if(count!=1)reject(ErrorCode.REVISION_CONFLICT);}
 private interface Work<T>{T get()throws IOException;}
 private static <T>T checked(Work<T> work){try{return work.get();}catch(IOException e){throw new SchemaRejection(ErrorCode.CONTENT_MISMATCH);}}
 private static void reject(ErrorCode code){throw new TenantAccessException(code);}
}
