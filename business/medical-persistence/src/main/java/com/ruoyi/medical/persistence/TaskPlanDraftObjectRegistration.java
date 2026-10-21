package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Concrete input artifact/access-set/lineage and immutable audit/outbox receipt after real exact GET.
 * The private Registered capability cannot be constructed from a UUID, a JSON receipt or mock success.
 */
public final class TaskPlanDraftObjectRegistration {
    public static final String KIND="PLAN_DRAFT_INPUT";
    public static final String REF="urn:medical:task-plan-draft:1.0.0#/$defs/InputManifest";
    private final JdbcTemplate db;private final TaskPlanDraftSchema schema;private final PermissionSchema json;
    private final DependencyManifestSchema dependencies;private final TaskPlanDraftObjectLedger ledger;private final TaskPlanDraftContentAuthority content;
    public TaskPlanDraftObjectRegistration(DataSource data,TaskPlanDraftSchema schema,PermissionSchema json,
            DependencyManifestSchema dependencies,TaskPlanDraftObjectLedger ledger,TaskPlanDraftContentAuthority content){
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.json=json;this.dependencies=dependencies;this.ledger=ledger;this.content=content;
    }
    Registered register(TaskPlanDraftJobAuthority.Verified proof,TaskPlanDraftObjectLedger.Attempt attempt,
            RuntimeObjectTransport.VerifiedTransfer transfer)throws Exception {
        require(proof,attempt,transfer);var ancestry=content.require(proof);var j=proof.job();var intent=attempt.intent();
        var existing=events(j,intent.artifactId());if(!existing.isEmpty())return verify(proof,attempt,transfer,ancestry,one(existing));
        if(!db.queryForList("SELECT artifact_id FROM artifact WHERE tenant_id=? AND artifact_id=? LIMIT 2",j.tenant(),intent.artifactId()).isEmpty())bad();
        var now=now();String access=id(),audit=id(),artifact=intent.artifactId();var object=transfer.reference();
        var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",j.tenant(),j.project(),access,ancestry.members());
        String manifestRaw=schema.canonical(manifest),manifestHash=schema.hash(manifest);
        version(j,access,"DEPENDENCY_ACCESS_SET",manifestHash);version(j,artifact,"ARTIFACT",object.sha256());
        var set=meta(j,now,false);set.put("access_set_id",access);set.put("manifest_sha256",manifestHash);set.put("content_sha256",manifestHash);insert("dependency_access_set",set);
        var full=meta(j,now,true);full.put("schema_manifest_sha256",dependencies.manifestSha256());full.put("access_set_id",access);full.put("manifest_json",manifestRaw);full.put("manifest_sha256",manifestHash);full.put("content_sha256",manifestHash);full.put("member_count",ancestry.members().size());insert("dependency_access_manifest",full);
        for(var member:ancestry.members()){var m=meta(j,now,false);m.remove("project_id");m.put("access_set_id",access);m.put("upstream_version_id",member.upstreamVersionId());m.put("rights_revision",member.rightsRevision());insert("dependency_access_member",m);}
        var ar=artifact(j,now,artifact,object,access);ar.put("object_ref",StoredObjectReferences.encode(object));insert("artifact",ar);
        insert("artifact_content_binding",binding(j,now,artifact,access,manifestHash));
        for(String root:ancestry.roots())insert("lineage_edge",Map.of("tenant_id",j.tenant(),"consumer_version_id",artifact,"upstream_version_id",root,"relation_kind","PLAN_DRAFT_INPUT_SOURCE","actor_id",j.actor(),"created_at",now));
        var receipt=json.object().put("schema_version","1.0.0").put("tenant_id",j.tenant()).put("project_id",j.project()).put("task_id",j.task())
                .put("run_id",j.run()).put("actor_id",j.actor()).put("kind",KIND).put("artifact_id",artifact).put("write_id",intent.writeIntent().writeId())
                .put("intent_sha256",intent.intentSha256()).put("input_sha256",j.inputHash()).put("authority_sha256",schema.hash(schema.read(j.authorityJson())))
                .put("schema_ref",REF).put("schema_sha256",schema.manifest()).put("audit_id",audit).put("event_id",artifact).put("created_at",now.toInstant(ZoneOffset.UTC).toString());
        receipt.set("object_ref",json.tree(object));receipt.set("dependency_manifest",json.tree(manifest));receipt.set("roots",json.tree(ancestry.roots()));
        String raw=schema.canonical(receipt);
        var log=meta(j,now,true);log.put("audit_id",audit);log.put("subject_id",j.actor());log.put("action","task.plan_draft.input.registered");log.put("resource_ref",raw);log.put("result","SUCCESS");log.put("request_id",intent.writeIntent().writeId());log.put("trace_id",j.run());insert("audit_event",log);
        insert("outbox_event",Map.of("tenant_id",j.tenant(),"event_id",artifact,"aggregate_id",j.run(),"aggregate_revision",1,"event_type","task.plan_draft.input.registered","payload",raw,"occurred_at",now));
        ancestry.requireUnchanged();return verify(proof,attempt,transfer,ancestry,one(events(j,artifact)));
    }
    Registered readVerified(TaskPlanDraftJobAuthority.Verified proof,TaskPlanDraftObjectLedger.Attempt attempt,
            RuntimeObjectTransport.VerifiedTransfer transfer)throws Exception {
        if(!attempt.priorAttempt())bad();require(proof,attempt,transfer);
        return verify(proof,attempt,transfer,content.require(proof),one(events(proof.job(),attempt.intent().artifactId())));
    }
    private void require(TaskPlanDraftJobAuthority.Verified p,TaskPlanDraftObjectLedger.Attempt a,RuntimeObjectTransport.VerifiedTransfer t)throws Exception {
        p.requireUnchanged();ledger.requireAttempt(p.current(),p.job(),a.intent());
        if(t==null||t.originalAttempt()!=a||!a.writeIntent().sha256().equals(t.rawSha256())||!a.expectedObjectKey().equals(t.reference().objectKey())
                ||!a.writeIntent().sizeBytes().equals(t.reference().sizeBytes())||!a.writeIntent().mediaType().equals(t.reference().mediaType())
                ||t.reference().objectVersion()==null||t.reference().objectVersion().isBlank())bad();
    }
    private Registered verify(TaskPlanDraftJobAuthority.Verified proof,TaskPlanDraftObjectLedger.Attempt attempt,
            RuntimeObjectTransport.VerifiedTransfer transfer,TaskPlanDraftContentAuthority.Verified ancestry,Map<String,Object> event)throws Exception {
        require(proof,attempt,transfer);var j=proof.job();var intent=attempt.intent();var object=transfer.reference();
        var receipt=schema.read(str(event,"payload"));if(receipt.size()!=20||!schema.canonical(receipt).equals(event.get("payload")))bad();
        var at=time(event,"occurred_at");if(at.isBefore(j.created())||at.isAfter(now()))bad();
        var expected=Map.of("tenant_id",j.tenant(),"project_id",j.project(),"task_id",j.task(),"run_id",j.run(),"actor_id",j.actor(),"kind",KIND,"artifact_id",intent.artifactId(),"write_id",intent.writeIntent().writeId(),"intent_sha256",intent.intentSha256(),"input_sha256",j.inputHash());
        for(var pin:expected.entrySet())if(!pin.getValue().equals(text(receipt,pin.getKey())))bad();
        if(!"1.0.0".equals(text(receipt,"schema_version"))||!REF.equals(text(receipt,"schema_ref"))||!schema.manifest().equals(text(receipt,"schema_sha256"))
                ||!schema.hash(schema.read(j.authorityJson())).equals(text(receipt,"authority_sha256"))||!json.tree(object).equals(receipt.required("object_ref"))
                ||!at.toInstant(ZoneOffset.UTC).toString().equals(text(receipt,"created_at"))||!intent.artifactId().equals(text(receipt,"event_id"))||!json.tree(ancestry.roots()).equals(receipt.required("roots")))bad();
        var manifest=receipt.required("dependency_manifest");String set=text(manifest,"access_set_id"),hash=schema.hash(manifest);
        if(!json.tree(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",j.tenant(),j.project(),set,ancestry.members())).equals(manifest))bad();
        var ar=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND artifact_id=? LIMIT 2",j.tenant(),intent.artifactId()));
        same(artifact(j,at,intent.artifactId(),object,set),ar);StoredObjectReferences.decode(str(ar,"object_ref"),object);
        same(binding(j,at,intent.artifactId(),set,hash),one(db.queryForList("SELECT * FROM artifact_content_binding WHERE tenant_id=? AND artifact_id=? LIMIT 2",j.tenant(),intent.artifactId())));
        var access=meta(j,at,false);access.put("access_set_id",set);access.put("manifest_sha256",hash);access.put("content_sha256",hash);
        same(access,one(db.queryForList("SELECT * FROM dependency_access_set WHERE tenant_id=? AND access_set_id=? LIMIT 2",j.tenant(),set)));
        var declared=meta(j,at,true);declared.put("schema_manifest_sha256",dependencies.manifestSha256());declared.put("access_set_id",set);declared.put("manifest_sha256",hash);declared.put("content_sha256",hash);declared.put("member_count",ancestry.members().size());
        var full=one(db.queryForList("SELECT * FROM dependency_access_manifest WHERE tenant_id=? AND access_set_id=? LIMIT 2",j.tenant(),set));same(declared,full);if(!manifest.equals(schema.read(str(full,"manifest_json"))))bad();
        var members=db.queryForList("SELECT * FROM dependency_access_member WHERE tenant_id=? AND access_set_id=? ORDER BY upstream_version_id LIMIT 1025",j.tenant(),set);
        if(members.size()!=ancestry.members().size())bad();
        for(int i=0;i<members.size();i++){var m=meta(j,at,false);m.remove("project_id");m.put("access_set_id",set);m.put("upstream_version_id",ancestry.members().get(i).upstreamVersionId());m.put("rights_revision",ancestry.members().get(i).rightsRevision());same(m,members.get(i));}
        var roots=db.queryForList("SELECT * FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? ORDER BY upstream_version_id LIMIT 1025",j.tenant(),intent.artifactId());
        if(roots.size()!=ancestry.roots().size())bad();for(int i=0;i<roots.size();i++)same(Map.of("tenant_id",j.tenant(),"consumer_version_id",intent.artifactId(),"upstream_version_id",ancestry.roots().get(i),"relation_kind","PLAN_DRAFT_INPUT_SOURCE","actor_id",j.actor(),"created_at",at),roots.get(i));
        for(var pin:List.of(Map.entry(set,hash),Map.entry(intent.artifactId(),object.sha256())))same(Map.of("tenant_id",j.tenant(),"version_id",pin.getKey(),"owner_project_id",j.project(),"entity_kind",pin.getKey().equals(set)?"DEPENDENCY_ACCESS_SET":"ARTIFACT","content_sha256",pin.getValue()),one(db.queryForList("SELECT * FROM version_registry WHERE tenant_id=? AND version_id=? LIMIT 2",j.tenant(),pin.getKey())));
        var audit=meta(j,at,true);audit.put("audit_id",text(receipt,"audit_id"));audit.put("subject_id",j.actor());audit.put("action","task.plan_draft.input.registered");audit.put("resource_ref",schema.canonical(receipt));audit.put("result","SUCCESS");audit.put("request_id",intent.writeIntent().writeId());audit.put("trace_id",j.run());
        same(audit,one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",j.tenant(),text(receipt,"audit_id"))));
        ancestry.requireUnchanged();return new Registered(proof,attempt,transfer,ancestry,receipt,schema.hash(receipt),set);
    }
    public final class Registered {
        private final TaskPlanDraftJobAuthority.Verified proof;private final TaskPlanDraftObjectLedger.Attempt attempt;
        private final RuntimeObjectTransport.VerifiedTransfer transfer;private final TaskPlanDraftContentAuthority.Verified ancestry;
        private final JsonNode receipt;private final String digest,set;
        private Registered(TaskPlanDraftJobAuthority.Verified proof,TaskPlanDraftObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer transfer,TaskPlanDraftContentAuthority.Verified ancestry,JsonNode receipt,String digest,String set){this.proof=proof;this.attempt=attempt;this.transfer=transfer;this.ancestry=ancestry;this.receipt=receipt.deepCopy();this.digest=digest;this.set=set;}
        public void requireUnchanged()throws Exception{var fresh=verify(proof,attempt,transfer,ancestry,one(events(job(),artifactId())));if(!digest.equals(fresh.digest))bad();}
        TaskPlanDraftJobs.Job job(){return proof.job();}public String artifactId(){return attempt.intent().artifactId();}public String receiptSha256(){return digest;}
        public ObjectRef reference(){return transfer.reference();}public byte[] bytes(){return attempt.bytes();}public String accessSetId(){return set;}JsonNode receipt(){return receipt.deepCopy();}
    }
    private Map<String,Object> artifact(TaskPlanDraftJobs.Job j,LocalDateTime at,String artifact,ObjectRef o,String access){var m=meta(j,at,true);m.put("artifact_id",artifact);m.put("run_id",j.run());m.put("document_revision",null);m.put("artifact_kind",KIND);m.put("object_key",o.objectKey());m.put("object_version",o.objectVersion());m.put("sha256",o.sha256());m.put("size_bytes",o.sizeBytes());m.put("media_type",o.mediaType());m.put("dependency_access_set_id",access);m.put("content_sha256",o.sha256());return m;}
    private Map<String,Object> binding(TaskPlanDraftJobs.Job j,LocalDateTime at,String artifact,String access,String hash){var m=meta(j,at,false);m.put("artifact_id",artifact);m.put("resource_kind","TASK");m.put("resource_id",j.task());m.put("schema_ref",REF);m.put("schema_document_sha256",schema.manifest());m.put("dependency_access_set_id",access);m.put("dependency_manifest_sha256",hash);return m;}
    private List<Map<String,Object>> events(TaskPlanDraftJobs.Job j,String artifact){return db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type='task.plan_draft.input.registered' LIMIT 2",j.tenant(),artifact,j.run());}
    private Map<String,Object> meta(TaskPlanDraftJobs.Job j,LocalDateTime at,boolean withSchema){var m=new LinkedHashMap<String,Object>();m.put("tenant_id",j.tenant());m.put("project_id",j.project());m.put("created_by",j.actor());m.put("created_at",at);m.put("classification",j.classification());if(withSchema){m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",schema.manifest());}return m;}
    private void version(TaskPlanDraftJobs.Job j,String id,String kind,String hash){insert("version_registry",Map.of("tenant_id",j.tenant(),"version_id",id,"owner_project_id",j.project(),"entity_kind",kind,"content_sha256",hash));}
    private void insert(String table,Map<String,Object> values){var keys=new ArrayList<>(values.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(values::get).toArray())!=1)bad();}
    private void same(Map<String,Object> wanted,Map<String,Object> actual){for(var e:wanted.entrySet()){var value=actual.get(e.getKey());if(e.getValue() instanceof LocalDateTime t){if(!t.equals(time(actual,e.getKey())))bad();}else if(e.getValue()!=null&&value!=null&&(e.getValue() instanceof Number||value instanceof Number)){if(!e.getValue().toString().equals(value.toString()))bad();}else if(Set.of("payload","resource_ref","requirement_refs","allowed_resolution_actions").contains(e.getKey())&&e.getValue() instanceof String left&&value instanceof String right){try{if(!schema.read(left).equals(schema.read(right)))bad();}catch(Exception failure){bad();}}else if(!Objects.equals(e.getValue(),value))bad();}}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    private static LocalDateTime time(Map<String,Object> r,String k){var v=r.get(k);if(v instanceof LocalDateTime t)return t;if(v instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    private static String text(JsonNode n,String k){var v=n.required(k);if(!v.isTextual())bad();return v.textValue();}private static String str(Map<String,Object> r,String k){if(!(r.get(k) instanceof String))bad();return (String)r.get(k);}
    private static String id(){return UUID.randomUUID().toString();}private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
