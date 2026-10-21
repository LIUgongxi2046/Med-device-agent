package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fixed Replay registration only after real readback. A reader must supply freshly
 * verified original bytes, both dependency guards, and the original recovery transfer.
 * No generic ArtifactRegistry access can bypass the governance ledger. */
public final class TaskRunReplayRegistration {
 private final JdbcTemplate db;private final PermissionSchema json;private final TaskRunReplayLedger ledger;private final TaskRunReplayGovernanceLedger governance;private final ArtifactRegistryMapper registry;private final DependencyManifestSchema manifests;private final DependencyLineage lineage;private final DependencyLineage.Limits limits;private final TaskRunReplayContentAuthority contents;private final TaskRunBootstrapSchema schema;
 private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public TaskRunReplayRegistration(DataSource data,PermissionSchema json,TaskRunReplayLedger ledger,TaskRunReplayGovernanceLedger governance,ArtifactRegistryMapper registry,DependencyManifestSchema manifests,DependencyLineage lineage,DependencyLineage.Limits limits,TaskRunReplayContentAuthority contents,TaskRunBootstrapSchema schema){this(new JdbcTemplate(data),json,ledger,governance,registry,manifests,lineage,limits,contents,schema);}
 TaskRunReplayRegistration(JdbcTemplate db,PermissionSchema json,TaskRunReplayLedger ledger,TaskRunReplayGovernanceLedger governance,ArtifactRegistryMapper registry,DependencyManifestSchema manifests,DependencyLineage lineage,DependencyLineage.Limits limits,TaskRunReplayContentAuthority contents,TaskRunBootstrapSchema schema){this.db=db;db.setQueryTimeout(5);this.json=json;this.ledger=ledger;this.governance=governance;this.registry=registry;this.manifests=manifests;this.lineage=lineage;this.limits=limits;this.contents=contents;this.schema=schema;}
 public Registered register(TaskRunBootstrapAuthority.Verified a,TaskRunReplayLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer transfer)throws Exception {
  var content=contents.require(a);String classification=a.classification();
  require(a,attempt,transfer,content);var i=a;a.requireReplayRegistration();var intent=attempt.intent();var object=transfer.reference();if(!classification.equals(one(db.query("SELECT classification FROM task_run_replay_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND created_by=? FOR UPDATE",(rs,n)->rs.getString(1),i.tenantId(),i.projectId(),i.runId(),intent.writeIntent().writeId(),i.actorId()))))bad();
  var existing=rows(a,intent);if(!existing.isEmpty())return verify(a,attempt,transfer,content,one(existing));
  if(!registry.artifact(intent.plannedArtifactId()).isEmpty()||!db.query("SELECT event_id FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",(rs,n)->rs.getString(1),i.tenantId(),intent.plannedArtifactId()).isEmpty())bad();
  var stored=governance.prepare(a,intent,a.replay(),classification);
  var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));String set=UUID.randomUUID().toString(),artifact=intent.plannedArtifactId();
  var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",i.tenantId(),i.projectId(),set,content.members());String manifestJson=json.canonical(json.tree(manifest)),hash=hash(manifestJson);
  version(i,set,"DEPENDENCY_ACCESS_SET",hash);var access=metadata(i,classification,now,false,intent.schemaDigest());access.put("project_id",i.projectId());access.put("access_set_id",set);access.put("manifest_sha256",hash);access.put("content_sha256",hash);insert("dependency_access_set",access);
  var m=metadata(i,classification,now,true,manifests.manifestSha256());m.put("project_id",i.projectId());m.put("access_set_id",set);m.put("manifest_json",manifestJson);m.put("manifest_sha256",hash);m.put("content_sha256",hash);m.put("member_count",content.members().size());insert("dependency_access_manifest",m);
  for(var member:content.members()){var v=metadata(i,classification,now,false,intent.schemaDigest());v.put("access_set_id",set);v.put("upstream_version_id",member.upstreamVersionId());v.put("rights_revision",member.rightsRevision());insert("dependency_access_member",v);}
  version(i,artifact,"ARTIFACT",object.sha256());var ar=metadata(i,classification,now,true,intent.schemaDigest());ar.put("project_id",i.projectId());ar.put("artifact_id",artifact);ar.put("run_id",i.runId());ar.put("artifact_kind","REPLAY_MANIFEST");ar.put("object_ref",StoredObjectReferences.encode(object));ar.put("object_key",object.objectKey());ar.put("object_version",object.objectVersion());ar.put("sha256",object.sha256());ar.put("size_bytes",object.sizeBytes());ar.put("media_type",object.mediaType());ar.put("dependency_access_set_id",set);ar.put("content_sha256",object.sha256());insert("artifact",ar);
  String task=a.replay().content().required("task_id").asText();var binding=metadata(i,classification,now,false,intent.schemaDigest());binding.put("project_id",i.projectId());binding.put("artifact_id",artifact);binding.put("resource_kind","TASK");binding.put("resource_id",task);binding.put("schema_ref",RuntimeReplaySchema.REF);binding.put("schema_document_sha256",intent.schemaDigest());binding.put("dependency_access_set_id",set);binding.put("dependency_manifest_sha256",hash);insert("artifact_content_binding",binding);
  for(String root:content.roots())insert("lineage_edge",Map.of("tenant_id",i.tenantId(),"consumer_version_id",artifact,"upstream_version_id",root,"relation_kind","RUNTIME_CONTENT_INPUT","actor_id",i.actorId(),"created_at",now));
  var receipt=json.object().put("schema_version","1.0.0").put("artifact_id",artifact)
    .put("intent_sha256",intent.sha256()).put("governance_sha256",stored.sha256())
    .put("authority_sha256",a.authorizationsSha256()).put("classification",classification)
    .put("registered_at",now.toInstant(java.time.ZoneOffset.UTC).toString());
  for(String field:List.of("tenant_id","project_id","run_id","principal_id","task_id","initiator_id","command_id","command_sha256","write_id","replay_schema_ref","replay_schema_sha256","execution_policy"))receipt.set(field,intent.content().required(field));
  receipt.set("object_ref",json.tree(object));receipt.set("content_manifest",json.tree(manifest));receipt.set("content_roots",json.tree(content.roots()));
  schema.requireRegistration(intent.content(),receipt);String canonical=json.canonical(receipt),digest=hash(canonical);
  var record=metadata(i,classification,now,true,schema.manifest());record.put("project_id",i.projectId());record.put("run_id",i.runId());record.put("write_id",intent.writeIntent().writeId());record.put("artifact_id",artifact);record.put("object_sha256",object.sha256());record.put("object_version",object.objectVersion());record.put("receipt_json",canonical);record.put("receipt_sha256",digest);insert("task_run_replay_registration",record);
  var audit=metadata(i,classification,now,true,intent.schemaDigest());audit.put("project_id",i.projectId());audit.put("audit_id",UUID.randomUUID().toString());audit.put("subject_id",i.actorId());audit.put("action","task.run.replay.registered");audit.put("resource_ref",canonical);audit.put("result","SUCCESS");audit.put("request_id",intent.writeIntent().writeId());audit.put("trace_id",i.runId());insert("audit_event",audit);
  insert("outbox_event",Map.of("tenant_id",i.tenantId(),"event_id",artifact,"aggregate_id",intent.writeIntent().writeId(),"aggregate_revision",1,"event_type","task.run.replay.registered","payload",canonical,"occurred_at",now));
  return verify(a,attempt,transfer,content,one(rows(a,intent)));
 }
 /** Every successful read has just recovered and verified the original physical version.
  * No new attempt/registration rows are created by this path. */
 public Registered readVerified(TaskRunBootstrapAuthority.Verified a,TaskRunReplayLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer transfer)throws Exception{
  var content=contents.require(a);require(a,attempt,transfer,content);
  return verify(a,attempt,transfer,content,one(rows(a,attempt.intent())));
 }
 /** Running production gets only original registered bytes. No PUT, attempt creation,
  * governance insertion, replay registration or launch is reachable from this method. */
 public Registered readOriginal(TaskNodeProductionAuthority.Verified a,TaskRunReplayLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer transfer)throws Exception{
  if(!attempt.priorAttempt())bad();var content=contents.require(a);require(a,attempt,transfer,content);
  return verify(a,attempt,transfer,content,one(rows(a,attempt.intent())));
 }
 private void require(TaskNodeProductionBasis a,TaskRunReplayLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer transfer,TaskRunReplayContentAuthority.Verified content)throws Exception{
  requireTransfer(attempt,transfer);
  if(!attempt.writeIntent().sha256().equals(transfer.rawSha256())||!content.input().rawSha256().equals(a.replay().rawSha256())||!a.replay().rawSha256().equals(transfer.rawSha256()))bad();
  if(a instanceof TaskRunBootstrapAuthority.Verified queued){queued.requireReplayRegistration();ledger.requireAttempt(queued,attempt.intent());}
  else if(a instanceof TaskNodeProductionAuthority.Verified running){if(!attempt.priorAttempt())bad();ledger.requireOriginal(running,attempt.intent());}
  else bad();
  content.requireUnchanged();a.requireUnchanged();
 }
 static void requireTransfer(TaskRunReplayLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer transfer){
  if(attempt==null||transfer==null||transfer.originalAttempt()!=attempt)bad();
  var expected=attempt.writeIntent();var actual=transfer.reference();
  if(actual==null||!expected.objectKey().equals(actual.objectKey())||!expected.sha256().equals(actual.sha256())
    ||!expected.sizeBytes().equals(actual.sizeBytes())||!expected.mediaType().equals(actual.mediaType())
    ||actual.objectVersion()==null||actual.objectVersion().isBlank())bad();
 }
 private Registered verify(TaskNodeProductionBasis a,TaskRunReplayLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer transfer,TaskRunReplayContentAuthority.Verified content,Row row)throws Exception{
  require(a,attempt,transfer,content);var i=a;var intent=attempt.intent();var object=transfer.reference();var r=schema.read(row.receipt());
  schema.requireRegistration(intent.content(),r);
  if(!hash(json.canonical(r)).equals(row.digest())||!object.objectVersion().equals(row.objectVersion())
    ||!row.created().equals(LocalDateTime.ofInstant(java.time.Instant.parse(r.required("registered_at").asText()),java.time.ZoneOffset.UTC).toString())
    ||!row.classification().equals(r.required("classification").asText())||!a.classification().equals(row.classification())
    ||!intent.content().required("authority_sha256").equals(r.required("authority_sha256"))
    ||a instanceof TaskRunBootstrapAuthority.Verified&&!a.authorizationsSha256().equals(r.required("authority_sha256").asText()))bad();
  String artifact=intent.plannedArtifactId(),task=a.taskId();if(!json.tree(object).equals(r.required("object_ref")))bad();
  var governanceProof=governance.requireStored(a,intent,a.replay());if(!governanceProof.sha256().equals(r.required("governance_sha256").asText()))bad();
  String classification=r.required("classification").asText();var created=LocalDateTime.ofInstant(java.time.Instant.parse(r.required("registered_at").asText()),java.time.ZoneOffset.UTC);
  var ar=one(registry.artifact(artifact));var binding=one(registry.binding(artifact));StoredObjectReferences.decode(ar.storedObject(),object);
  if(!i.tenantId().equals(ar.tenantId())||!i.projectId().equals(ar.projectId())||!artifact.equals(ar.artifactId())||!"REPLAY_MANIFEST".equals(ar.artifactKind())||!object.equals(ar.reference())||!object.sha256().equals(ar.contentSha256())||!"1.0.0".equals(ar.schemaVersion())||!intent.schemaDigest().equals(ar.schemaManifest())||!classification.equals(ar.classification()))bad();
  var manifestRow=one(registry.manifest(ar.accessSetId()));var manifest=manifests.verify(manifestRow,registry.members(ar.accessSetId()));if(!i.tenantId().equals(manifest.tenant_id())||!i.projectId().equals(manifest.project_id())||!ar.accessSetId().equals(manifest.access_set_id()))bad();if(!json.canonical(json.tree(manifest)).equals(json.canonical(r.required("content_manifest")))||!manifest.members().equals(content.members())||!json.canonical(json.tree(content.roots())).equals(json.canonical(r.required("content_roots"))))bad();
  if(!i.tenantId().equals(binding.tenantId())||!i.projectId().equals(binding.projectId())||!artifact.equals(binding.artifactId())||!"TASK".equals(binding.resourceKind())||!task.equals(binding.resourceId())||!RuntimeReplaySchema.REF.equals(binding.schemaRef())||!intent.schemaDigest().equals(binding.schemaDocumentSha256())||!ar.accessSetId().equals(binding.accessSetId())||!manifestRow.manifestSha256().equals(binding.dependencyManifestSha256()))bad();
  for(var v:List.of(Map.entry(artifact,object.sha256()),Map.entry(ar.accessSetId(),manifestRow.manifestSha256()))){var version=one(registry.version(v.getKey()));if(!v.getKey().equals(version.id())||!i.projectId().equals(version.projectId())||!v.getValue().equals(version.contentSha256())||!(v.getKey().equals(artifact)?"ARTIFACT":"DEPENDENCY_ACCESS_SET").equals(version.entityKind()))bad();}
  lineage.require(artifact,content.members(),limits);
  for(String table:List.of("artifact_content_binding","dependency_access_set","dependency_access_manifest")){String idColumn=table.equals("artifact_content_binding")?"artifact_id":"access_set_id";String id=table.equals("artifact_content_binding")?artifact:ar.accessSetId();if(one(db.query("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=? AND project_id=? AND "+idColumn+"=? AND created_by=? AND created_at=? AND classification=?",(rs,n)->rs.getInt(1),i.tenantId(),i.projectId(),id,i.actorId(),created,classification))!=1)bad();}
  if(one(db.query("SELECT COUNT(*) FROM dependency_access_member WHERE tenant_id=? AND access_set_id=? AND created_by=? AND created_at=? AND classification=?",(rs,n)->rs.getInt(1),i.tenantId(),ar.accessSetId(),i.actorId(),created,classification))!=content.members().size())bad();
  if(one(db.query("SELECT COUNT(*) FROM artifact WHERE tenant_id=? AND project_id=? AND artifact_id=? AND run_id=? AND document_revision IS NULL AND created_by=? AND created_at=? AND classification=?",(rs,n)->rs.getInt(1),i.tenantId(),i.projectId(),artifact,i.runId(),i.actorId(),created,classification))!=1)bad();
  var roots=db.query("SELECT upstream_version_id FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? AND relation_kind='RUNTIME_CONTENT_INPUT' AND actor_id=? ORDER BY upstream_version_id",(rs,n)->rs.getString(1),i.tenantId(),artifact,i.actorId());if(!roots.equals(content.roots())||one(db.query("SELECT COUNT(*) FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=?",(rs,n)->rs.getInt(1),i.tenantId(),artifact))!=roots.size())bad();
  String canonical=json.canonical(r);var audit=one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND created_by=? AND action='task.run.replay.registered' AND result='SUCCESS' AND reason_code IS NULL AND request_id=? AND trace_id=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND created_at=? AND classification=? LIMIT 2",(rs,n)->rs.getString(1),i.tenantId(),i.projectId(),i.actorId(),i.actorId(),intent.writeIntent().writeId(),i.runId(),intent.schemaDigest(),created,classification));
  var event=one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type='task.run.replay.registered' AND occurred_at=? LIMIT 2",(rs,n)->rs.getString(1),i.tenantId(),artifact,intent.writeIntent().writeId(),created));if(!canonical.equals(json.canonical(reader.readTree(audit)))||!canonical.equals(json.canonical(reader.readTree(event))))bad();
  content.requireUnchanged();a.requireUnchanged();return new Registered(artifact,object,row.digest(),r);
 }
 private List<Row> rows(TaskNodeProductionBasis a,TaskRunReplayLedger.Intent intent){var i=a;return db.query("SELECT receipt_json,receipt_sha256,object_version,created_at,classification FROM task_run_replay_registration WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? AND artifact_id=? AND object_sha256=? AND created_by=? AND schema_version='1.0.0' AND schema_manifest_sha256=? LIMIT 2 FOR UPDATE",(r,n)->new Row(r.getString(1),r.getString(2),r.getString(3),r.getTimestamp(4).toLocalDateTime().toString(),r.getString(5)),i.tenantId(),i.projectId(),i.runId(),intent.writeIntent().writeId(),intent.plannedArtifactId(),intent.writeIntent().sha256(),i.actorId(),schema.manifest());}
 public static final class Registered {private final String artifactId,receiptSha256;private final ObjectRef reference;private final JsonNode receipt;private Registered(String artifactId,ObjectRef reference,String receiptSha256,JsonNode receipt){this.receipt=receipt.deepCopy();this.artifactId=artifactId;this.reference=reference;this.receiptSha256=receiptSha256;}public String artifactId(){return artifactId;}public ObjectRef reference(){return reference;}public String receiptSha256(){return receiptSha256;}public JsonNode receipt(){return receipt.deepCopy();}}
 record Row(String receipt,String digest,String objectVersion,String created,String classification){}
 private void version(TaskRunBootstrapAuthority.Verified i,String id,String kind,String digest){insert("version_registry",Map.of("tenant_id",i.tenantId(),"owner_project_id",i.projectId(),"version_id",id,"entity_kind",kind,"content_sha256",digest));}
 private Map<String,Object> metadata(TaskRunBootstrapAuthority.Verified i,String classification,LocalDateTime time,boolean schema,String digest){var result=new LinkedHashMap<String,Object>();result.put("tenant_id",i.tenantId());result.put("created_by",i.actorId());result.put("created_at",time);result.put("classification",classification);if(schema){result.put("schema_version","1.0.0");result.put("schema_manifest_sha256",digest);}return result;}
 private void insert(String table,Map<String,Object> values){var columns=new ArrayList<>(values.keySet());if(db.update("INSERT INTO "+table+"("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(columns.size(),"?"))+")",columns.stream().map(values::get).toArray())!=1)bad();}
 private static String hash(String value)throws java.security.NoSuchAlgorithmException{return WireValues.sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
