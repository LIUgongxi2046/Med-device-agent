package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Immutable user-declared formatting and evidence-bound human verification. Not a regulator approval. */
@Service
public final class ExportLayoutService {
 private final EnterpriseAccess access; private final ExportMapper exports; private final PermissionMapper permissions;
 private final ResourceReadAuthority authority; private final ArtifactRegistryMapper artifacts; private final ExportSchema schema;
 private final JdbcTemplate db;
 public ExportLayoutService(EnterpriseAccess access,ExportMapper exports,PermissionMapper permissions,
   ResourceReadAuthority authority,ArtifactRegistryMapper artifacts,ExportSchema schema,DataSource source){
  this.access=access;this.exports=exports;this.permissions=permissions;this.authority=authority;this.artifacts=artifacts;this.schema=schema;
  db=new JdbcTemplate(source);db.setQueryTimeout(5);
 }
 public JsonNode author(ApplicationSessionIdentity.Verified subject,String context,String documentId,String key,byte[] bytes){
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   current(a,context);WireValues.uuid(documentId);var request=schema.read(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));schema.verify("LayoutProfile",request);
   var doc=one(exports.document(Map.of("userId",a.actorId(),"documentId",documentId,"documentRevision",request.required("document_revision").textValue())));
   grant(a,doc,"artifact.export");same(doc.formatProfileRevision(),request.required("format_profile_revision").textValue());
   var template=schema.read(doc.formatProfile());same(template.required("numbering_rule_revision").required("version_id").textValue(),request.required("numbering_rule_revision").textValue());
   if(request.required("channel_specification_artifact_id").isNull()!=request.required("channel_specification_sha256").isNull())bad();
   if(!request.required("channel_specification_artifact_id").isNull()) evidence(a,doc.projectId(),request.required("channel_specification_artifact_id").textValue(),request.required("channel_specification_sha256").textValue());
   String canonical=schema.canonical(request),hash=WireValues.sha256(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var old=db.queryForList("SELECT * FROM export_layout_profile WHERE tenant_id=? AND created_by=? AND request_key=? FOR UPDATE",a.context().tenant_id(),a.actorId(),key);
   if(!old.isEmpty()){var row=one(old);same(documentId,row.get("document_id"));same(hash,row.get("content_sha256"));return profileView(row);}
   String id=UUID.randomUUID().toString();var now=permissions.databaseTime();
   db.update("INSERT INTO version_registry(tenant_id,version_id,owner_project_id,entity_kind,content_sha256) VALUES(?,?,?,'EXPORT_LAYOUT_PROFILE',?)",doc.tenantId(),id,doc.projectId(),hash);
   db.update("INSERT INTO export_layout_profile(tenant_id,project_id,profile_id,document_id,document_revision,request_key,profile_json,content_sha256,specification_artifact_id,specification_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",doc.tenantId(),doc.projectId(),id,documentId,doc.documentRevision(),key,canonical,hash,request.required("channel_specification_artifact_id").textValue(),request.required("channel_specification_sha256").textValue(),schema.manifestSha256(),a.actorId(),now,doc.classification());
   audit(a,doc.projectId(),id,"export.layout.authored",hash,doc.classification());
   return profileView(one(db.queryForList("SELECT * FROM export_layout_profile WHERE tenant_id=? AND profile_id=?",doc.tenantId(),id)));
  }).get());
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String document,String revision,String profile){
  return access.execute(subject,a->ThrowingSupplier.of(()->{current(a,context);var doc=one(exports.document(Map.of("userId",a.actorId(),"documentId",document,"documentRevision",revision)));grant(a,doc,"document.read");return profileView(profile(a,doc,profile));}).get());
 }
 JsonNode consume(EnterpriseAccess.Admission a,ExportProtocol.DocumentRow doc,String profile)throws Exception{
  if(profile==null)return null;return schema.read(profile(a,doc,profile).get("profile_json").toString());
 }
 private Map<String,Object> profile(EnterpriseAccess.Admission a,ExportProtocol.DocumentRow doc,String profile)throws Exception{
  WireValues.uuid(profile);var row=one(db.queryForList("SELECT * FROM export_layout_profile WHERE tenant_id=? AND project_id=? AND document_id=? AND document_revision=? AND profile_id=? FOR UPDATE",a.context().tenant_id(),doc.projectId(),doc.documentId(),doc.documentRevision(),profile));
  var value=schema.read(row.get("profile_json").toString());schema.verify("LayoutProfile",value);same(row.get("content_sha256"),WireValues.sha256(schema.canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));same(doc.formatProfileRevision(),value.required("format_profile_revision").textValue());
  if(!value.required("channel_specification_artifact_id").isNull())evidence(a,doc.projectId(),value.required("channel_specification_artifact_id").textValue(),value.required("channel_specification_sha256").textValue());return row;
 }
 public JsonNode verify(ApplicationSessionIdentity.Verified subject,String context,String itemId,String key,byte[] bytes){
  return access.execute(subject,a->ThrowingSupplier.of(()->{current(a,context);WireValues.uuid(itemId);var request=schema.read(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));schema.verify("VerificationInput",request);
   var item=one(exports.itemForUpdate(Map.of("userId",a.actorId(),"exportItemId",itemId)));var manifest=one(exports.manifest(Map.of("userId",a.actorId(),"exportId",item.exportId())));
   var doc=one(exports.document(Map.of("userId",a.actorId(),"documentId",item.documentId(),"documentRevision",item.documentRevision())));grant(a,doc,"review.decide");
   String hash=WireValues.sha256(schema.canonical(request).getBytes(java.nio.charset.StandardCharsets.UTF_8));var old=db.queryForList("SELECT * FROM export_layout_verification WHERE tenant_id=? AND created_by=? AND request_key=? FOR UPDATE",a.context().tenant_id(),a.actorId(),key);
   if(!old.isEmpty()){var row=one(old);same(itemId,row.get("export_item_id"));same(hash,row.get("content_sha256"));return verificationView(row);}
   var prior=db.queryForList("SELECT verification_id,verification_no FROM export_layout_verification WHERE tenant_id=? AND export_item_id=? ORDER BY verification_no DESC LIMIT 1 FOR UPDATE",a.context().tenant_id(),itemId);
   if(!Objects.equals(prior.isEmpty()?null:prior.get(0).get("verification_id"),request.required("previous_verification_id").textValue()))throw new TenantAccessException(ErrorCode.ACTION_STALE);
   long verificationNo=prior.isEmpty()?1:((Number)prior.get(0).get("verification_no")).longValue()+1;
   same("SUCCEEDED",item.status());same(item.revision().toString(),request.required("expected_revision").textValue());same(manifest.channelProfileRevision(),request.required("channel_profile_revision").textValue());
   var profile=consume(a,doc,manifest.channelProfileRevision());var validation=schema.read(item.validationRef());same(validation.required("sha256").textValue(),request.required("output_sha256").textValue());
   evidence(a,doc.projectId(),item.artifactId(),request.required("output_sha256").textValue());
   String channel=request.required("channel_check").textValue(),visual=request.required("visual_check").textValue();
   String evidenceId=request.required("evidence_artifact_id").isNull()?null:request.required("evidence_artifact_id").textValue();String evidenceHash=request.required("evidence_sha256").isNull()?null:request.required("evidence_sha256").textValue();
   if(("APPROVED".equals(channel)||"PASSED".equals(visual))&&(evidenceId==null||evidenceHash==null))bad();
   if("APPROVED".equals(channel)&&(profile==null||profile.required("channel_name").isNull()||profile.required("channel_specification_artifact_id").isNull()))bad();
   if((evidenceId==null)!=(evidenceHash==null))bad();if(evidenceId!=null)evidence(a,doc.projectId(),evidenceId,evidenceHash);
   String id=UUID.randomUUID().toString();db.update("INSERT INTO export_layout_verification(tenant_id,project_id,verification_id,export_item_id,verification_no,request_key,request_json,content_sha256,output_sha256,output_artifact_id,profile_id,channel_check,visual_check,evidence_artifact_id,evidence_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",doc.tenantId(),doc.projectId(),id,itemId,verificationNo,key,schema.canonical(request),hash,request.required("output_sha256").textValue(),item.artifactId(),manifest.channelProfileRevision(),channel,visual,evidenceId,evidenceHash,schema.manifestSha256(),a.actorId(),permissions.databaseTime(),doc.classification());
   audit(a,doc.projectId(),id,"export.layout.verified",hash,doc.classification());return verificationView(one(db.queryForList("SELECT * FROM export_layout_verification WHERE tenant_id=? AND verification_id=?",doc.tenantId(),id)));
  }).get());
 }
 public JsonNode verifications(ApplicationSessionIdentity.Verified subject,String context,String itemId){
  return access.execute(subject,a->ThrowingSupplier.of(()->{current(a,context);var item=one(exports.itemForUpdate(Map.of("userId",a.actorId(),"exportItemId",itemId)));var doc=one(exports.document(Map.of("userId",a.actorId(),"documentId",item.documentId(),"documentRevision",item.documentRevision())));grant(a,doc,"export.read");var result=schema.object();result.put("channel_check","UNKNOWN");result.put("visual_check","NOT_RUN");var list=result.putArray("items");for(var row:db.queryForList("SELECT * FROM export_layout_verification WHERE tenant_id=? AND export_item_id=? ORDER BY verification_no LIMIT 1001",a.context().tenant_id(),itemId))list.add(verificationView(row));return result;}).get());
 }
 JsonNode latest(EnterpriseAccess.Admission a,String item)throws Exception{
  var rows=db.queryForList("SELECT * FROM export_layout_verification WHERE tenant_id=? AND export_item_id=? ORDER BY verification_no DESC LIMIT 1 FOR UPDATE",a.context().tenant_id(),item);
  return rows.isEmpty()?null:verificationView(rows.get(0));
 }
 boolean releasable(EnterpriseAccess.Admission a,ExportProtocol.ManifestRow manifest,ExportProtocol.ItemRow item)throws Exception{
  if(!"FORMAL_BODY".equals(manifest.purpose()))return true;
  var rows=db.queryForList("SELECT * FROM export_layout_verification WHERE tenant_id=? AND export_item_id=? ORDER BY verification_no DESC LIMIT 1 FOR UPDATE",a.context().tenant_id(),item.exportItemId());
  if(rows.size()!=1)return false;var row=rows.get(0);verificationView(row);
  if(!"APPROVED".equals(row.get("channel_check"))||!"PASSED".equals(row.get("visual_check")))return false;
  same(manifest.channelProfileRevision(),row.get("profile_id"));same(schema.read(item.validationRef()).required("sha256").textValue(),row.get("output_sha256"));evidence(a,item.projectId(),row.get("evidence_artifact_id").toString(),row.get("evidence_sha256").toString());return true;
 }
 private JsonNode profileView(Map<String,Object> row)throws Exception{var out=schema.object();out.put("profile_id",row.get("profile_id").toString());out.put("sha256",row.get("content_sha256").toString());out.put("channel_check","UNKNOWN");out.set("profile",schema.read(row.get("profile_json").toString()));return out;}
 private JsonNode verificationView(Map<String,Object> row)throws Exception{var request=schema.read(row.get("request_json").toString());schema.verify("VerificationInput",request);for(String field:List.of("output_sha256","channel_check","visual_check","evidence_artifact_id","evidence_sha256"))if(!Objects.equals(row.get(field),request.required(field).textValue()))bad();same(row.get("profile_id"),request.required("channel_profile_revision").textValue());same(row.get("content_sha256"),WireValues.sha256(schema.canonical(request).getBytes(java.nio.charset.StandardCharsets.UTF_8)));var out=schema.object();out.put("verification_id",row.get("verification_id").toString());out.put("actor_id",row.get("created_by").toString());out.put("created_at",row.get("created_at").toString());out.set("verification",request);return out;}
 private void evidence(EnterpriseAccess.Admission a,String project,String artifact,String hash)throws Exception{var row=one(artifacts.artifact(artifact));same(a.context().tenant_id(),row.tenantId());if(row.projectId()!=null)same(project,row.projectId());if(hash!=null)same(hash,row.sha256());authority.require(a,new PermissionProtocol.Scope(row.tenantId(),row.projectId(),"ARTIFACT",artifact),"artifact.read","EXPORT_VERIFICATION");}
 private void grant(EnterpriseAccess.Admission a,ExportProtocol.DocumentRow doc,String action)throws Exception{same(a.context().tenant_id(),doc.tenantId());var scope=new PermissionProtocol.Scope(doc.tenantId(),doc.projectId(),"DOCUMENT",doc.documentId());authority.require(a,scope,"document.read","EXPORT_LAYOUT");if(!"document.read".equals(action))authority.require(a,scope,action,"EXPORT_LAYOUT");}
 private void audit(EnterpriseAccess.Admission a,String project,String target,String action,String hash,String classification)throws Exception{if(permissions.saveAudit(a.actorId(),project,UUID.randomUUID().toString(),action,schema.canonical(schema.tree(Map.of("target_id",target,"sha256",hash))),UUID.randomUUID().toString(),schema.manifestSha256(),classification)!=1)bad();}
 private static void current(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void same(Object a,Object b){if(a==null||!a.equals(b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
