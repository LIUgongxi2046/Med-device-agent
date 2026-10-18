package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.RuleVersionMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Current formal rule identity and original approval evidence. Never substitutes template governance grants. */
public final class FormalInvocationRules {
 private final RuleVersionMapper rows; private final RuleVersionSchema schema; private final PermissionSchema json;
 private final ResourceReadAuthority resources; private final AgentPublicationDependencies dependencies; private final JdbcTemplate jdbc;
 public FormalInvocationRules(RuleVersionMapper rows,RuleVersionSchema schema,PermissionSchema json,AgentPublicationDependencies dependencies,ResourceReadAuthority resources,DataSource data){this.resources=resources;this.rows=rows;this.schema=schema;this.json=json;this.dependencies=dependencies;this.jdbc=new JdbcTemplate(data);jdbc.setQueryTimeout(5);}
 public final class Verified {
  private final EnterpriseAccess.Admission admission;private final String project,digest;private final List<PermissionProtocol.Basis> bases;private final JsonNode manifest;
  private Verified(EnterpriseAccess.Admission a,String project,List<PermissionProtocol.Basis> bases,JsonNode manifest)throws IOException{this.admission=a;this.project=project;this.bases=List.copyOf(bases);this.manifest=manifest.deepCopy();this.digest=hash(manifest);}
  public JsonNode manifest(){return manifest.deepCopy();}public void requireCurrent()throws IOException{if(!digest.equals(require(admission,project,bases).digest))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 }
 public Verified require(EnterpriseAccess.Admission a,String project,List<PermissionProtocol.Basis> bases)throws IOException{
  if(bases==null||bases.isEmpty()||bases.size()>64||new HashSet<>(bases).size()!=bases.size())bad();
  var closure=dependencies.require(a,project,bases,TemplateDependencyAuthority.PURPOSE);var manifest=json.object().put("schema_version","1.0.0").put("purpose","MODEL_VERIFICATION");var entries=manifest.putArray("rules");
  for(var basis:bases){resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),null,"RULE",basis.version_id()),"rule.read","MODEL_VERIFICATION");var v=new HashMap<String,Object>();v.put("userId",a.actorId());v.put("ruleId",basis.version_id());if(!rows.simulationBinding(v).isEmpty())bad();var r=one(rows.definition(v));
   if(!a.context().tenant_id().equals(r.tenantId())||!basis.version_id().equals(r.ruleVersion())||!basis.content_sha256().equals(r.contentHash())||!r.contentHash().equals(r.registryHash())||!"APPROVED".equals(r.status())||!"1.0.0".equals(r.schemaVersion())||!schema.contentManifestSha256().equals(r.manifest())||r.revision().intValue()<2||r.authorityRevision().signum()<=0)bad();
   var base=r.baseVersion()==null?null:new PermissionProtocol.Basis(r.baseVersion(),r.baseHash());if(r.baseVersion()==null&&r.baseHash()!=null)bad();
   var request=json.object().put("schema_version","1.0.0").put("content_schema_ref",RuleVersionSchema.CONTENT_REF).put("reason",r.reason());request.set("base_version",json.tree(base));request.set("content",schema.read(r.contentJson()));var valid=schema.version(json.canonical(request).getBytes(StandardCharsets.UTF_8));
   if(!valid.content_sha256().equals(r.contentHash())||!valid.request().content().engine_revision().equals(r.engineRevision())||!json.tree(valid.request().content().expression()).equals(schema.read(r.expression()))||!json.tree(valid.request().content().basis_manifest()).equals(schema.read(r.basisManifest()))||!rows.parents(v).equals(valid.request().content().basis_manifest().stream().map(PermissionProtocol.Basis::version_id).sorted().toList())||!rows.baseParents(v).equals(base==null?List.of():List.of(base.version_id())))bad();
   var stored=new RuleVersionProtocol.Stored(r.tenantId(),r.ruleVersion(),r.status(),r.revision().toString(),valid.request().content(),r.contentHash(),base,r.reason(),r.authorityGrant(),r.authorityRevision().toString(),r.createdBy(),DatabaseTimestamp.format(r.createdAt()),valid.dependency_refs());
   var decision=approval(a,stored);var entry=json.object();entry.set("version",json.tree(basis));entry.put("decision_id",text(decision,"decision_id")).put("approval_sha256",hash(decision));entry.set("definition",json.tree(stored));entries.add(entry);
  }
  manifest.set("current_authorizations",closure.authorizations());return new Verified(a,project,bases,manifest);
 }
 private JsonNode approval(EnterpriseAccess.Admission a,RuleVersionProtocol.Stored actual)throws IOException{
  String tenant=a.context().tenant_id();var d=schema.read(one(jdbc.query("""
   SELECT JSON_OBJECT('decision_id',decision_id,'operation_id',operation_id,'rule_version',rule_version,
    'rule_sha256',rule_sha256,'decision',decision,'from_status',from_status,'to_status',to_status,
    'expected_revision',CAST(expected_revision AS CHAR),'resulting_revision',CAST(resulting_revision AS CHAR),'reason',reason,
    'request',request_json,'baseline_manifest',baseline_manifest,'baseline_sha256',baseline_sha256,
    'dependency_authorizations',dependency_authorizations,'receipt',receipt_json,'receipt_sha256',receipt_sha256,
    'authority_grant',authority_grant,'authority_revision',CAST(authority_revision AS CHAR),'auth_epoch',CAST(auth_epoch AS CHAR),
    'governance_epoch',CAST(governance_epoch AS CHAR),'schema_version',schema_version,'schema_manifest_sha256',schema_manifest_sha256,
    'created_by',created_by,'created_at',DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%fZ'),'classification',classification)
   FROM rule_approval_decision WHERE tenant_id=? AND rule_version=? AND resulting_revision=? LIMIT 2
   """,(r,n)->r.getString(1),tenant,actual.rule_version(),actual.revision())));
  var request=schema.decide(json.canonical(d.required("request")).getBytes(StandardCharsets.UTF_8));var baseline=request.baseline_manifest();
  if(!"APPROVE".equals(request.decision())||!"APPROVE".equals(text(d,"decision"))||!"APPROVED".equals(text(d,"to_status"))||!List.of("DRAFT","RETURNED").contains(text(d,"from_status"))||!actual.rule_version().equals(request.target_revision())||!actual.rule_version().equals(text(d,"rule_version"))||!actual.content_sha256().equals(text(d,"rule_sha256"))||!actual.revision().equals(text(d,"resulting_revision"))||!PermissionSchema.next(request.expected_revision()).equals(actual.revision())||!request.expected_revision().equals(text(d,"expected_revision"))||!request.reason().equals(text(d,"reason")))bad();
  if(!baseline.rule_version().equals(actual.rule_version())||!baseline.content_sha256().equals(actual.content_sha256())||!baseline.definition_manifest_sha256().equals(schema.contentManifestSha256())||!baseline.dependency_refs().equals(actual.dependency_refs())||!json.tree(baseline).equals(d.required("baseline_manifest"))||!hash(d.required("baseline_manifest")).equals(text(d,"baseline_sha256"))||!hash(d.required("dependency_authorizations")).equals(baseline.dependency_authorizations_sha256()))bad();
  var operation=schema.read(one(jdbc.query("SELECT JSON_OBJECT('request_sha256',request_sha256,'response',response_ref) FROM operation_record WHERE tenant_id=? AND operation_id=? AND subject_id=? AND created_by=? AND operation_name='TP-04' AND target_id=? AND state='COMPLETED' AND http_status=200 AND schema_version='1.0.0' AND schema_manifest_sha256=? LIMIT 2",(r,n)->r.getString(1),tenant,text(d,"operation_id"),text(d,"created_by"),text(d,"created_by"),actual.rule_version(),schema.manifestSha256())));
  // Legacy TP-04 request hash includes an unpersisted session ID; validate its format, never pretend to reconstruct it.
  WireValues.hash(text(operation,"request_sha256"));
  var receipt=schema.receipt(json.canonical(operation.required("response")));var data=receipt.required("data");
  if(!receipt.equals(d.required("receipt"))||!hash(receipt).equals(text(d,"receipt_sha256"))||!text(d,"operation_id").equals(text(data,"operation_id"))||!text(d,"decision_id").equals(text(data,"decision_id"))||!text(d,"created_by").equals(text(data,"actor_id"))||!actual.rule_version().equals(text(data,"rule_version"))||!actual.content_sha256().equals(text(data,"content_sha256"))||!actual.revision().equals(text(data,"revision"))||!"APPROVED".equals(text(data,"status"))||!"APPROVE".equals(text(data,"decision"))||!tenant.equals(text(data.required("current_context"),"tenant_id"))||!"1.0.0".equals(text(d,"schema_version"))||!schema.manifestSha256().equals(text(d,"schema_manifest_sha256")))bad();
  for(String k:List.of("authority_revision","auth_epoch","governance_epoch"))if(PermissionSchema.revision(text(d,k)).signum()<=0)bad();WireValues.uuid(text(d,"authority_grant"));
  if(!java.time.Instant.parse(text(d,"created_at")).equals(java.time.Instant.parse(text(data,"decided_at"))))bad();
  var after=(ObjectNode)json.tree(actual);var before=after.deepCopy().put("status",text(d,"from_status")).put("revision",text(d,"expected_revision"));var audit=json.object().put("schema_version","1.0.0").put("operation_id",text(d,"operation_id"));audit.set("request",d.required("request"));audit.set("before",before);audit.set("after",after);audit.set("decision",d);var event=json.object().put("schema_version","1.0.0");event.set("decision",d);
  var auditRow=schema.read(one(jdbc.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id IS NULL AND subject_id=? AND created_by=? AND action='rule.approval_decided' AND result='SUCCESS' AND reason_code IS NULL AND request_id=? AND trace_id=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? LIMIT 2",(r,n)->r.getString(1),tenant,text(d,"created_by"),text(d,"created_by"),text(receipt,"request_id"),text(receipt,"request_id"),schema.manifestSha256(),text(d,"classification"))));
  var eventRow=schema.read(one(jdbc.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND aggregate_revision=? AND event_type='rule.approval_decided' LIMIT 2",(r,n)->r.getString(1),tenant,actual.rule_version(),actual.revision())));if(!audit.equals(auditRow)||!event.equals(eventRow))bad();return d;
 }
 private String hash(Object n)throws IOException{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(n));}
 private static String text(JsonNode n,String k){var v=n.required(k);if(!v.isTextual())bad();return v.textValue();}private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
