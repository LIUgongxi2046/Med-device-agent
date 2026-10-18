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
public final class RuntimeGovernanceLedger {
 private final JdbcTemplate jdbc;private final PermissionSchema json;private final RuntimeReplayOriginalAuthority originals;
 private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public RuntimeGovernanceLedger(DataSource data,PermissionSchema json,RuntimeReplayOriginalAuthority originals){this(new JdbcTemplate(data),json,originals);}
 RuntimeGovernanceLedger(JdbcTemplate jdbc,PermissionSchema json,RuntimeReplayOriginalAuthority originals){this.jdbc=jdbc;this.json=json;this.originals=Objects.requireNonNull(originals);}
 Stored prepare(EnterpriseAccess.Admission a,RuntimeObjectLedger.Intent intent,RuntimeReplaySchema.VerifiedInputs verified,String classification)throws Exception {
  var expected=expected(a,intent,verified);var i=a.formalProof().identity();
  if(!classification.equals(one(jdbc.query("SELECT classification FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=? FOR UPDATE",(r,n)->r.getString(1),i.tenant(),i.project(),i.run(),intent.writeIntent().writeId(),i.actor()))))bad();
  var existing=read(a,intent.writeIntent().writeId());
  if(!existing.isEmpty())return verify(a,expected,one(existing));
  count(jdbc.update("""
   INSERT INTO runtime_governance_manifest(tenant_id,project_id,run_id,write_id,principal_id,replay_sha256,canonical_manifest,manifest_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification)
   VALUES(?,?,?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
   """,i.tenant(),i.project(),i.run(),intent.writeIntent().writeId(),i.principal(),verified.rawSha256(),expected.canonical(),expected.sha256(),verified.schemaSha256(),i.actor(),classification));
  var content=verified.content();int ordinal=0;
  for(var v:content.required("dependency_versions"))count(jdbc.update("""
   INSERT INTO runtime_governance_member(tenant_id,project_id,run_id,write_id,member_ordinal,upstream_version_id,upstream_project_id,entity_kind,content_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification)
   VALUES(?,?,?,?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
   """,i.tenant(),i.project(),i.run(),intent.writeIntent().writeId(),ordinal++,v.required("version_id").asText(),nullable(v.required("project_id")),v.required("entity_kind").asText(),v.required("content_sha256").asText(),verified.schemaSha256(),i.actor(),classification));
  ordinal=0;for(var e:content.required("dependency_edges"))count(jdbc.update("""
   INSERT INTO runtime_governance_edge(tenant_id,project_id,run_id,write_id,edge_ordinal,consumer_version_id,upstream_version_id,relation_kind,schema_version,schema_manifest_sha256,created_by,created_at,classification)
   VALUES(?,?,?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
   """,i.tenant(),i.project(),i.run(),intent.writeIntent().writeId(),ordinal++,e.required("consumer_version_id").asText(),e.required("upstream_version_id").asText(),e.required("relation_kind").asText(),verified.schemaSha256(),i.actor(),classification));
  return verify(a,expected,one(read(a,intent.writeIntent().writeId())));
 }
 Stored requireStored(EnterpriseAccess.Admission a,RuntimeObjectLedger.Intent intent,RuntimeReplaySchema.VerifiedInputs verified)throws Exception{return verify(a,expected(a,intent,verified),one(read(a,intent.writeIntent().writeId())));}
 private Stored expected(EnterpriseAccess.Admission a,RuntimeObjectLedger.Intent intent,RuntimeReplaySchema.VerifiedInputs verified)throws Exception {
  var i=a.formalProof().identity();var value=verified.content();originals.require(a,intent.bytes());
  if(!verified.runId().equals(i.run())||!verified.rawSha256().equals(intent.writeIntent().sha256())||!WireValues.sha256(intent.bytes()).equals(verified.rawSha256())
    ||!i.tenant().equals(value.required("tenant_id").asText())||!i.project().equals(value.required("project_id").asText())||!i.actor().equals(value.required("initiator_id").asText())||!i.principal().equals(value.required("principal_id").asText()))bad();
  var m=json.object().put("schema_version","1.0.0").put("tenant_id",i.tenant()).put("project_id",i.project()).put("run_id",i.run()).put("principal_id",i.principal()).put("write_id",intent.writeIntent().writeId()).put("replay_sha256",verified.rawSha256());
  m.set("versions",value.required("dependency_versions").deepCopy());m.set("edges",value.required("dependency_edges").deepCopy());
  String canonical=json.canonical(m);return new Stored(intent.writeIntent().writeId(),canonical,WireValues.sha256(canonical.getBytes(StandardCharsets.UTF_8)),verified.schemaSha256());
 }
 private List<Stored> read(EnterpriseAccess.Admission a,String write){var i=a.formalProof().identity();return jdbc.query("""
  SELECT write_id,canonical_manifest,manifest_sha256,schema_manifest_sha256 FROM runtime_governance_manifest
  WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND principal_id=? AND created_by=? AND schema_version='1.0.0' FOR UPDATE
  """,(r,n)->new Stored(r.getString(1),canonical(r.getString(2)),r.getString(3),r.getString(4)),i.tenant(),i.project(),i.run(),write,i.principal(),i.actor());}
 private Stored verify(EnterpriseAccess.Admission a,Stored expected,Stored actual)throws Exception {
  if(!expected.equals(actual)||!WireValues.sha256(actual.canonical().getBytes(StandardCharsets.UTF_8)).equals(actual.sha256()))bad();
  var i=a.formalProof().identity();var m=reader.readTree(expected.canonical());
  var members=jdbc.query("""
   SELECT member_ordinal,upstream_version_id,upstream_project_id,entity_kind,content_sha256 FROM runtime_governance_member
   WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=? AND schema_version='1.0.0' AND schema_manifest_sha256=? ORDER BY member_ordinal FOR UPDATE
   """,(r,n)->new Member(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)),i.tenant(),i.project(),i.run(),actual.writeId(),i.actor(),actual.schemaSha256());
  var edges=jdbc.query("""
   SELECT edge_ordinal,consumer_version_id,upstream_version_id,relation_kind FROM runtime_governance_edge
   WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=? AND schema_version='1.0.0' AND schema_manifest_sha256=? ORDER BY edge_ordinal FOR UPDATE
   """,(r,n)->new Edge(r.getInt(1),r.getString(2),r.getString(3),r.getString(4)),i.tenant(),i.project(),i.run(),actual.writeId(),i.actor(),actual.schemaSha256());
  verifyRows(m,members,edges);
  return actual;
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
