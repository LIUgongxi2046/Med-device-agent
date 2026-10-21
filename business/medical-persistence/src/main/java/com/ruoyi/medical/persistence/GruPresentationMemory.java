package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Typed, non-model presentation preferences. Free text and summaries never become executable instructions.
 * Formal template/project output always stays byte-for-byte canonical; preferences affect only Gru's wrapper. */
@Component
@ConditionalOnProperty(name="medical.gru-records.enabled",havingValue="true")
public final class GruPresentationMemory {
 private final JdbcTemplate db;private final PermissionSchema json;private final PermissionMapper permissions;
 private final GruExecutionSchema schema;private final JsonMapper codec=new JsonMapper();
 public GruPresentationMemory(@Qualifier("masterDataSource")DataSource data,PermissionSchema json,PermissionMapper permissions)throws Exception{db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.permissions=permissions;schema=new GruExecutionSchema(json);}
 JsonNode list(EnterpriseAccess.Admission a,GruService.ExportProof proof)throws Exception {
  var out=json.object().put("schema_version","1.0.0").put("authority","PRESENTATION_ONLY").put("project_fact",false).put("action_permission",false).put("owner_id",a.actorId()).put("tenant_id",a.context().tenant_id()).put("conversation_id",proof.context().conversationId());var items=out.putArray("items");
  for(var row:db.queryForList("SELECT * FROM gru_owner_memory WHERE tenant_id=? AND owner_id=? AND conversation_id=? AND status='ACTIVE' ORDER BY updated_at DESC,memory_id LIMIT 100",a.context().tenant_id(),a.actorId(),proof.context().conversationId()))items.add(view(row));return out;
 }
 JsonNode create(EnterpriseAccess.Admission a,GruService.ExportProof proof,JsonNode input)throws Exception {
  schema.validate("Memory",input);Integer active=db.queryForObject("SELECT COUNT(*) FROM gru_owner_memory WHERE tenant_id=? AND owner_id=? AND conversation_id=? AND status='ACTIVE'",Integer.class,a.context().tenant_id(),a.actorId(),proof.context().conversationId());if(active==null||active>=100)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);String id=UUID.randomUUID().toString();var presentation=input.has("presentation")?input.required("presentation"):defaults();
  if(!"HABIT".equals(text(input,"kind")))presentation=defaults();
  var refs=json.object().put("conversation_id",proof.context().conversationId()).put("conversation_revision",proof.revision().toString());var messages=refs.putArray("messages");
  if("SUMMARY".equals(text(input,"kind")))for(var message:proof.messages())messages.add(json.object().put("message_id",message.messageId()).put("content_sha256",message.contentHash()));
  var now=Objects.requireNonNull(permissions.databaseTime());
  changed(db.update("INSERT INTO gru_owner_memory (tenant_id,owner_id,conversation_id,memory_id,kind,content,presentation_json,presentation_sha256,source_refs_json,source_refs_sha256,status,revision,created_at,updated_at,created_by,classification,schema_version,schema_manifest_sha256) VALUES (?,?,?,?,?,?,?,?,?,?,'ACTIVE',1,?,?,?,?,'1.0.0',?)",a.context().tenant_id(),a.actorId(),proof.context().conversationId(),id,text(input,"kind"),text(input,"text"),canonical(presentation),hash(presentation),canonical(refs),hash(refs),now,now,a.actorId(),proof.context().classification(),schema.manifest()));
  audit(a,proof,id,"gru.owner_memory.saved",view(row(a,proof,id)));return list(a,proof);
 }
 JsonNode change(EnterpriseAccess.Admission a,GruService.ExportProof proof,String id,JsonNode input,boolean remove)throws Exception {
  schema.validate(remove?"MemoryRemove":"MemoryEdit",input);var old=row(a,proof,id);same(text(input,"expected_revision"),str(old,"revision"));same("ACTIVE",str(old,"status"));
  var before=view(old);String state=remove?"REMOVED":"ACTIVE";var now=Objects.requireNonNull(permissions.databaseTime());
  if(remove)changed(db.update("UPDATE gru_owner_memory SET status='REMOVED',revision=revision+1,updated_at=? WHERE tenant_id=? AND owner_id=? AND conversation_id=? AND memory_id=? AND revision=? AND status='ACTIVE'",now,a.context().tenant_id(),a.actorId(),proof.context().conversationId(),id,old.get("revision")));
  else {same("HABIT",str(old,"kind"));var presentation=input.required("presentation");changed(db.update("UPDATE gru_owner_memory SET content=?,presentation_json=?,presentation_sha256=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND owner_id=? AND conversation_id=? AND memory_id=? AND revision=? AND status='ACTIVE'",text(input,"text"),canonical(presentation),hash(presentation),now,a.context().tenant_id(),a.actorId(),proof.context().conversationId(),id,old.get("revision")));}
  var evidence=json.object().put("status",state);evidence.set("before",before);evidence.set("after",view(row(a,proof,id)));evidence.set("request",input);audit(a,proof,id,remove?"gru.owner_memory.removed":"gru.owner_memory.updated",evidence);return list(a,proof);
 }
 /** Captured exactly once at explicit confirmation; it is not an execution permission. */
 void capture(EnterpriseAccess.Admission a,GruService.ExportProof proof,String action)throws Exception {
  var rows=db.queryForList("SELECT context_json,context_sha256 FROM gru_execution_presentation_context WHERE tenant_id=? AND owner_id=? AND action_id=?",a.context().tenant_id(),a.actorId(),action);
  if(!rows.isEmpty()){var row=one(rows);same(hash(read(str(row,"context_json"))),str(row,"context_sha256"));return;}
  var context=basis(proof);var pins=context.putArray("habit_pins");
  for(var row:db.queryForList("SELECT * FROM gru_owner_memory WHERE tenant_id=? AND owner_id=? AND conversation_id=? AND kind='HABIT' AND status='ACTIVE' ORDER BY updated_at,memory_id LIMIT 100",a.context().tenant_id(),a.actorId(),proof.context().conversationId())){
   var memory=view(row);pins.add(json.object().put("memory_id",text(memory,"memory_id")).put("revision",text(memory,"revision")).put("presentation_sha256",str(row,"presentation_sha256")));}
  changed(db.update("INSERT INTO gru_execution_presentation_context (tenant_id,owner_id,conversation_id,action_id,context_json,context_sha256,created_by,created_at,classification,schema_version,schema_manifest_sha256) VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(6),?,'1.0.0',?)",a.context().tenant_id(),a.actorId(),proof.context().conversationId(),action,canonical(context),hash(context),a.actorId(),proof.context().classification(),schema.manifest()));
 }
 JsonNode present(EnterpriseAccess.Admission a,GruService.ExportProof proof,String action,String intent,String state,JsonNode original)throws Exception {
  var saved=one(db.queryForList("SELECT * FROM gru_execution_presentation_context WHERE tenant_id=? AND owner_id=? AND conversation_id=? AND action_id=?",a.context().tenant_id(),a.actorId(),proof.context().conversationId(),action));
  var context=read(str(saved,"context_json"));same(hash(context),str(saved,"context_sha256"));same(schema.manifest(),str(saved,"schema_manifest_sha256"));same(proof.document().header().templateVersion(),text(context,"template_version"));same(proof.context().projectId(),text(context,"project_id"));same(proof.context().documentRevision(),text(context,"document_revision"));same(proof.document().header().bodySha256(),text(context,"canonical_document_sha256"));
  var effective=defaults();var used=json.object().arrayNode();var excluded=json.object().arrayNode();
  // Revalidate deletions and edits every consumption. New habits do not silently join an already-fixed execution.
  for(var pin:context.required("habit_pins")){var current=db.queryForList("SELECT * FROM gru_owner_memory WHERE tenant_id=? AND owner_id=? AND conversation_id=? AND memory_id=? AND kind='HABIT' AND status='ACTIVE' AND revision=? AND presentation_sha256=?",a.context().tenant_id(),a.actorId(),proof.context().conversationId(),text(pin,"memory_id"),text(pin,"revision"),text(pin,"presentation_sha256"));
   if(current.isEmpty()){excluded.add(text(pin,"memory_id"));continue;}var memory=one(current);view(memory);effective=(ObjectNode)read(str(memory,"presentation_json"));used.add(pin.deepCopy());}
  String layout=text(effective,"layout"),detail=text(effective,"detail");var lines=new ArrayList<String>();lines.add("Execution: "+intent);lines.add("Status: "+state);
  if("FULL".equals(detail)){lines.add("Target document revision: "+proof.context().documentRevision());lines.add("Verified result SHA-256: "+hash(original));}
  String formatted="BULLETS".equals(layout)?"- "+String.join("\n- ",lines):String.join(". ",lines)+".";
  var out=json.object().put("schema_version","1.0.0").put("context_sha256",str(saved,"context_sha256")).put("scope","GRU_RESULT_WRAPPER_ONLY").put("text",formatted).put("canonical_result_unchanged",true).put("model_instruction",false).put("authority","PRESENTATION_ONLY");
  out.set("effective",effective);out.set("applied_habit_pins",used);out.set("excluded_habit_ids",excluded);out.set("priority",context.required("priority"));out.put("template_version",text(context,"template_version"));out.put("project_id",text(context,"project_id"));schema.validate("PresentationResult",out);return out;
 }
 private ObjectNode basis(GruService.ExportProof proof){var out=json.object().put("schema_version","1.0.0").put("policy_version","GRU_PRESENTATION_V1").put("scope","GRU_RESULT_WRAPPER_ONLY").put("project_id",proof.context().projectId()).put("template_version",proof.document().header().templateVersion()).put("document_revision",proof.context().documentRevision()).put("canonical_document_sha256",proof.document().header().bodySha256());out.putArray("priority").add("PUBLISHED_TEMPLATE").add("FIXED_PROJECT_CONVENTIONS").add("PERSONAL_PRESENTATION");return out;}
 private ObjectNode defaults(){return json.object().put("layout","PARAGRAPH").put("detail","FULL");}
 private JsonNode view(Map<String,Object> row)throws Exception {same("1.0.0",str(row,"schema_version"));same(schema.manifest(),str(row,"schema_manifest_sha256"));same(str(row,"owner_id"),str(row,"created_by"));var p=read(str(row,"presentation_json"));schema.validate("PresentationPreference",p);same(hash(p),str(row,"presentation_sha256"));var out=json.object().put("memory_id",str(row,"memory_id")).put("kind",str(row,"kind")).put("content",str(row,"content")).put("revision",str(row,"revision")).put("status",str(row,"status"));out.set("presentation",p);var refs=read(str(row,"source_refs_json"));same(hash(refs),str(row,"source_refs_sha256"));same(str(row,"conversation_id"),text(refs,"conversation_id"));out.set("source_refs",refs);out.put("evidence_authority",false);return out;}
 private Map<String,Object> row(EnterpriseAccess.Admission a,GruService.ExportProof proof,String id){WireValues.uuid(id);return one(db.queryForList("SELECT * FROM gru_owner_memory WHERE tenant_id=? AND owner_id=? AND conversation_id=? AND memory_id=? FOR UPDATE",a.context().tenant_id(),a.actorId(),proof.context().conversationId(),id));}
 private void audit(EnterpriseAccess.Admission a,GruService.ExportProof proof,String id,String kind,JsonNode value)throws Exception {String operation=UUID.randomUUID().toString();changed(permissions.saveAudit(a.actorId(),proof.context().projectId(),UUID.randomUUID().toString(),kind,canonical(value),operation,schema.manifest(),proof.context().classification()));changed(permissions.saveEvent(a.actorId(),operation,id,"1",kind,canonical(value)));}
 private JsonNode read(String value)throws Exception{return codec.readTree(value);}private String canonical(JsonNode value)throws Exception{return json.canonical(value);}private String hash(JsonNode value)throws Exception{return WireValues.sha256(canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 private static String str(Map<String,Object> r,String k){return Objects.requireNonNull(r.get(k)).toString();}private static String text(JsonNode n,String k){return n.required(k).textValue();}private static Map<String,Object> one(List<Map<String,Object>> rows){if(rows.size()!=1)throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return rows.get(0);}private static void changed(int n){if(n!=1)throw new TenantAccessException(ErrorCode.ACTION_STALE);}private static void same(Object x,Object y){if(!Objects.equals(x,y))throw new TenantAccessException(ErrorCode.ACTION_STALE);}
}
