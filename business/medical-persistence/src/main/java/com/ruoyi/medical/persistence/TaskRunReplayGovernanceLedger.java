package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Immutable fixed runtime provenance, separate from SOURCE-rights access manifests.
 * Stored is deliberately an integrity result only. A content reader must also require
 * every current governance owner grant and every current source/content read guard. */
public final class TaskRunReplayGovernanceLedger {
 private final JdbcTemplate jdbc;private final PermissionSchema json;
 private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public TaskRunReplayGovernanceLedger(DataSource data,PermissionSchema json){this(new JdbcTemplate(data),json);}
 TaskRunReplayGovernanceLedger(JdbcTemplate jdbc,PermissionSchema json){this.jdbc=jdbc;this.json=json;}
 Stored prepare(TaskRunBootstrapAuthority.Verified a,TaskRunReplayLedger.Intent intent,RuntimeReplaySchema.VerifiedInputs verified,String classification)throws Exception {
  var expected=expected(a,intent,verified);var i=a;
  if(!classification.equals(one(jdbc.query("SELECT classification FROM task_run_replay_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=? FOR UPDATE",(r,n)->r.getString(1),i.tenantId(),i.projectId(),i.runId(),intent.writeIntent().writeId(),i.actorId()))))bad();
  var existing=read(a,intent.writeIntent().writeId());
  if(!existing.isEmpty())return verify(a,expected,one(existing));
  count(jdbc.update("""
   INSERT INTO task_run_replay_governance_manifest(tenant_id,project_id,run_id,write_id,principal_id,replay_sha256,canonical_manifest,manifest_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification)
   VALUES(?,?,?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
   """,i.tenantId(),i.projectId(),i.runId(),intent.writeIntent().writeId(),i.principalId(),verified.rawSha256(),expected.canonical(),expected.sha256(),verified.schemaSha256(),i.actorId(),classification));
  var content=verified.content();int ordinal=0;
  for(var v:content.required("dependency_versions"))count(jdbc.update("""
   INSERT INTO task_run_replay_governance_member(tenant_id,project_id,run_id,write_id,member_ordinal,upstream_version_id,upstream_project_id,entity_kind,content_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification)
   VALUES(?,?,?,?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
   """,i.tenantId(),i.projectId(),i.runId(),intent.writeIntent().writeId(),ordinal++,v.required("version_id").asText(),nullable(v.required("project_id")),v.required("entity_kind").asText(),v.required("content_sha256").asText(),verified.schemaSha256(),i.actorId(),classification));
  ordinal=0;for(var e:content.required("dependency_edges"))count(jdbc.update("""
   INSERT INTO task_run_replay_governance_edge(tenant_id,project_id,run_id,write_id,edge_ordinal,consumer_version_id,upstream_version_id,relation_kind,schema_version,schema_manifest_sha256,created_by,created_at,classification)
   VALUES(?,?,?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
   """,i.tenantId(),i.projectId(),i.runId(),intent.writeIntent().writeId(),ordinal++,e.required("consumer_version_id").asText(),e.required("upstream_version_id").asText(),e.required("relation_kind").asText(),verified.schemaSha256(),i.actorId(),classification));
  return verify(a,expected,one(read(a,intent.writeIntent().writeId())));
 }
 Stored requireStored(TaskNodeProductionBasis a,TaskRunReplayLedger.Intent intent,RuntimeReplaySchema.VerifiedInputs verified)throws Exception{return verify(a,expected(a,intent,verified),one(read(a,intent.writeIntent().writeId())));}
 private Stored expected(TaskNodeProductionBasis a,TaskRunReplayLedger.Intent intent,RuntimeReplaySchema.VerifiedInputs verified)throws Exception {
  var i=a;var value=verified.content();a.requireUnchanged();if(!java.util.Arrays.equals(intent.bytes(),ContentReadAuthorization.canonicalBytes(a.replay().content())))bad();
  if(!verified.runId().equals(i.runId())||!verified.rawSha256().equals(intent.writeIntent().sha256())||!WireValues.sha256(intent.bytes()).equals(verified.rawSha256())
    ||!i.tenantId().equals(value.required("tenant_id").asText())||!i.projectId().equals(value.required("project_id").asText())||!i.actorId().equals(value.required("initiator_id").asText())||!i.principalId().equals(value.required("principal_id").asText()))bad();
  var m=json.object().put("schema_version","1.0.0").put("tenant_id",i.tenantId()).put("project_id",i.projectId()).put("run_id",i.runId()).put("principal_id",i.principalId()).put("write_id",intent.writeIntent().writeId()).put("replay_sha256",verified.rawSha256());
  m.set("versions",value.required("dependency_versions").deepCopy());m.set("edges",value.required("dependency_edges").deepCopy());
  String canonical=json.canonical(m);return new Stored(intent.writeIntent().writeId(),canonical,WireValues.sha256(canonical.getBytes(StandardCharsets.UTF_8)),verified.schemaSha256());
 }
 private List<Stored> read(TaskNodeProductionBasis a,String write){var i=a;return jdbc.query("""
  SELECT write_id,canonical_manifest,manifest_sha256,schema_manifest_sha256 FROM task_run_replay_governance_manifest
  WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND principal_id=? AND created_by=? AND schema_version='1.0.0' AND replay_sha256=? AND classification=? AND created_at<=UTC_TIMESTAMP(6) FOR UPDATE
  """,(r,n)->new Stored(r.getString(1),canonical(r.getString(2)),r.getString(3),r.getString(4)),i.tenantId(),i.projectId(),i.runId(),write,i.principalId(),i.actorId(),a.replay().rawSha256(),a.classification());}
 private Stored verify(TaskNodeProductionBasis a,Stored expected,Stored actual)throws Exception {
  if(!expected.equals(actual)||!WireValues.sha256(actual.canonical().getBytes(StandardCharsets.UTF_8)).equals(actual.sha256()))bad();
  var i=a;var m=reader.readTree(expected.canonical());
  var members=jdbc.query("""
   SELECT member_ordinal,upstream_version_id,upstream_project_id,entity_kind,content_sha256 FROM task_run_replay_governance_member
   WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? AND created_at<=UTC_TIMESTAMP(6) ORDER BY member_ordinal FOR UPDATE
   """,(r,n)->new Member(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)),i.tenantId(),i.projectId(),i.runId(),actual.writeId(),i.actorId(),actual.schemaSha256(),a.classification());
  var edges=jdbc.query("""
   SELECT edge_ordinal,consumer_version_id,upstream_version_id,relation_kind FROM task_run_replay_governance_edge
   WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? AND created_at<=UTC_TIMESTAMP(6) ORDER BY edge_ordinal FOR UPDATE
   """,(r,n)->new Edge(r.getInt(1),r.getString(2),r.getString(3),r.getString(4)),i.tenantId(),i.projectId(),i.runId(),actual.writeId(),i.actorId(),actual.schemaSha256(),a.classification());
  // Metadata predicates must never hide an extra immutable member or edge.
  // Compare the entire run/write population, including malformed/future rows,
  // before the exact ordered content comparison below.
  if(total(a,actual.writeId(),"task_run_replay_governance_member")!=members.size()
    ||total(a,actual.writeId(),"task_run_replay_governance_edge")!=edges.size())bad();
  verifyRows(m,members,edges);
  return actual;
 }
 private int total(TaskNodeProductionBasis a,String write,String table){
  return one(jdbc.query("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=?",
    (r,n)->r.getInt(1),a.tenantId(),a.projectId(),a.runId(),write));
 }
 static void verifyRows(JsonNode manifest,List<Member> members,List<Edge> edges){
  var versions=manifest.required("versions");var expectedEdges=manifest.required("edges");if(versions.size()!=members.size()||expectedEdges.size()!=edges.size())bad();
  for(int n=0;n<members.size();n++){var v=versions.get(n);var r=members.get(n);if(r.ordinal()!=n||!r.id().equals(v.required("version_id").asText())||!Objects.equals(r.project(),nullable(v.required("project_id")))||!r.kind().equals(v.required("entity_kind").asText())||!r.sha().equals(v.required("content_sha256").asText()))bad();}
  for(int n=0;n<edges.size();n++){var e=expectedEdges.get(n);var r=edges.get(n);if(r.ordinal()!=n||!r.consumer().equals(e.required("consumer_version_id").asText())||!r.upstream().equals(e.required("upstream_version_id").asText())||!r.kind().equals(e.required("relation_kind").asText()))bad();}
 }
 record Member(int ordinal,String id,String project,String kind,String sha){}record Edge(int ordinal,String consumer,String upstream,String kind){}
 static final class Stored {private final String writeId,canonical,sha256,schemaSha256;private Stored(String writeId,String canonical,String sha256,String schemaSha256){this.writeId=writeId;this.canonical=canonical;this.sha256=sha256;this.schemaSha256=schemaSha256;}String writeId(){return writeId;}String canonical(){return canonical;}String sha256(){return sha256;}String schemaSha256(){return schemaSha256;}@Override public boolean equals(Object other){return other instanceof Stored s&&writeId.equals(s.writeId)&&canonical.equals(s.canonical)&&sha256.equals(s.sha256)&&schemaSha256.equals(s.schemaSha256);}@Override public int hashCode(){return Objects.hash(writeId,canonical,sha256,schemaSha256);}}
 private String canonical(String value){try{return json.canonical(reader.readTree(value));}catch(Exception e){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}}
 private static String nullable(JsonNode n){return n.isNull()?null:n.asText();}private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}private static void count(int n){if(n!=1)bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
