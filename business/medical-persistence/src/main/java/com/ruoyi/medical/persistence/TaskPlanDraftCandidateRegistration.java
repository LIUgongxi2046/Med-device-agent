package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Saves only genuine N01 host Produced output after original exact-version physical readback.
 * It never creates a plan revision, check result, approval, or human decision.
 */
public final class TaskPlanDraftCandidateRegistration {
    private final JdbcTemplate db;private final TaskPlanDraftSchema schema;private final PermissionSchema json;
    private final TaskPlanDraftObjectLedger ledger;private final TaskPlanDraftContentAuthority content;private final SchemaCatalogue catalogue;
    public TaskPlanDraftCandidateRegistration(DataSource data,TaskPlanDraftSchema schema,PermissionSchema json,
            TaskPlanDraftObjectLedger ledger,TaskPlanDraftContentAuthority content)throws Exception{
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.json=json;this.ledger=ledger;this.content=content;catalogue=SchemaCatalogue.fromClasspath();
    }
    Registered register(TaskPlanDraftRuntimeAdapter.Produced produced,TaskPlanDraftObjectRegistration.Registered input,
            TaskPlanDraftObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        require(produced,input,attempt,physical);var authority=produced.authority();var j=authority.job();var ancestry=content.require(authority.current());
        var old=rows(j);if(!old.isEmpty())return verify(produced,input,attempt,physical,ancestry,one(old));
        String artifact=attempt.intent().artifactId(),audit=id(),event=id();var now=now();var object=physical.reference();
        var full=schema.read(new String(produced.bytes(),StandardCharsets.UTF_8));var candidate=full.required("output").required("content");
        String candidateHash=schema.hash(candidate),schemaHash=catalogue.schemaDigest("artifacts"),set=produced.dependencyManifest().required("access_set_id").asText();
        var inputManifest=produced.dependencyManifest();String manifestHash=schema.hash(inputManifest);
        insert("version_registry",Map.of("tenant_id",j.tenant(),"version_id",artifact,"owner_project_id",j.project(),"entity_kind","ARTIFACT","content_sha256",object.sha256()));
        var ar=artifact(j,now,artifact,object,set,schemaHash);ar.put("object_ref",StoredObjectReferences.encode(object));insert("artifact",ar);
        insert("artifact_content_binding",binding(j,now,artifact,set,manifestHash,schemaHash));
        for(String root:produced.contentRoots())insert("lineage_edge",Map.of("tenant_id",j.tenant(),"consumer_version_id",artifact,"upstream_version_id",root,"relation_kind","PLAN_DRAFT_CANDIDATE_SOURCE","actor_id",j.actor(),"created_at",now));
        var reg=json.object();reg.set("artifact",json.tree(artifact(j,now,artifact,object,set,schemaHash)));reg.set("binding",json.tree(binding(j,now,artifact,set,manifestHash,schemaHash)));reg.set("dependency_manifest",inputManifest);reg.set("roots",json.tree(produced.contentRoots()));
        var receipt=json.object().put("schema_version","1.0.0").put("tenant_id",j.tenant()).put("project_id",j.project()).put("task_id",j.task()).put("task_revision",j.taskRevision())
                .put("run_id",j.run()).put("actor_id",j.actor()).put("step_id",authority.stepId()).put("action_id",authority.actionId()).put("lease_generation",authority.leaseGeneration().toString())
                .put("session_id",produced.sessionId()).put("input_sha256",j.inputHash()).put("artifact_id",artifact).put("object_sha256",object.sha256()).put("object_version",object.objectVersion())
                .put("candidate_sha256",candidateHash).put("schema_sha256",schemaHash).put("access_set_id",set).put("registration_receipt_sha256",schema.hash(reg))
                .put("write_id",attempt.writeIntent().writeId()).put("intent_sha256",attempt.intent().intentSha256())
                .put("normalization_pins_json",schema.canonical(produced.normalizationPins())).put("normalization_pins_sha256",schema.hash(produced.normalizationPins()))
                .put("supervision_pins_json",schema.canonical(produced.supervisionPins())).put("supervision_pins_sha256",schema.hash(produced.supervisionPins()))
                .put("invocation_pins_json",schema.canonical(produced.invocationPins())).put("invocation_pins_sha256",schema.hash(produced.invocationPins())).put("created_at",now.toInstant(ZoneOffset.UTC).toString());
        schema.validate("CandidateReceipt",receipt);String raw=schema.canonical(receipt),hash=schema.hash(receipt);
        journal(j,audit,event,artifact,raw,now);
        var record=meta(j,now,true);record.put("task_id",j.task());record.put("run_id",j.run());record.put("artifact_id",artifact);record.put("write_id",attempt.writeIntent().writeId());record.put("candidate_sha256",candidateHash);
        record.put("receipt_json",raw);record.put("receipt_sha256",hash);record.put("audit_id",audit);record.put("event_id",event);insert("task_plan_draft_candidate",record);
        return verify(produced,input,attempt,physical,ancestry,one(rows(j)));
    }
    private void require(TaskPlanDraftRuntimeAdapter.Produced produced,TaskPlanDraftObjectRegistration.Registered input,
            TaskPlanDraftObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical)throws Exception {
        produced.requireUnchanged();input.requireUnchanged();ledger.requireCandidateAttempt(produced,attempt.intent());
        var j=produced.authority().job();if(!j.equals(input.job())||physical==null||physical.originalAttempt()!=attempt
                ||!attempt.writeIntent().sha256().equals(physical.rawSha256())||!attempt.writeIntent().objectKey().equals(physical.reference().objectKey())
                ||!attempt.writeIntent().sizeBytes().equals(physical.reference().sizeBytes())||!"application/json".equals(physical.reference().mediaType())
                ||!Arrays.equals(attempt.bytes(),produced.bytes()))bad();
        var full=schema.read(new String(produced.bytes(),StandardCharsets.UTF_8));TypedSchemaBindings.bind(catalogue,ArtifactBindings.ARTIFACT,full).read();
        if(!produced.dependencyManifest().required("access_set_id").asText().equals(full.required("dependency_access_set_id").asText())||!"PLAN_CANDIDATE".equals(full.required("output").required("artifact_kind").asText()))bad();
    }
    private Registered verify(TaskPlanDraftRuntimeAdapter.Produced produced,TaskPlanDraftObjectRegistration.Registered input,
            TaskPlanDraftObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical,TaskPlanDraftContentAuthority.Verified ancestry,Map<String,Object> row)throws Exception {
        require(produced,input,attempt,physical);var j=produced.authority().job();var at=time(row,"created_at");var object=physical.reference();var authority=produced.authority();
        String artifact=attempt.intent().artifactId();var receipt=schema.validate("CandidateReceipt",schema.read(str(row,"receipt_json")));
        var full=schema.read(new String(produced.bytes(),StandardCharsets.UTF_8));var candidate=full.required("output").required("content");String candidateHash=schema.hash(candidate),schemaHash=catalogue.schemaDigest("artifacts");
        if(at.isBefore(j.created())||at.isAfter(now())||!candidateHash.equals(row.get("candidate_sha256"))||!artifact.equals(row.get("artifact_id"))||!attempt.writeIntent().writeId().equals(row.get("write_id"))
                ||!schema.canonical(receipt).equals(row.get("receipt_json"))||!schema.hash(receipt).equals(row.get("receipt_sha256")))bad();
        var fields=new LinkedHashMap<String,String>();fields.putAll(Map.of("tenant_id",j.tenant(),"project_id",j.project(),"task_id",j.task(),"task_revision",j.taskRevision(),"run_id",j.run(),"actor_id",j.actor(),"input_sha256",j.inputHash(),"artifact_id",artifact));
        fields.putAll(Map.of("step_id",authority.stepId(),"action_id",authority.actionId(),"lease_generation",authority.leaseGeneration().toString(),"session_id",produced.sessionId(),"object_sha256",object.sha256(),"object_version",object.objectVersion(),"candidate_sha256",candidateHash,"schema_sha256",schemaHash,"access_set_id",produced.dependencyManifest().required("access_set_id").asText()));
        fields.putAll(Map.of("write_id",attempt.writeIntent().writeId(),"intent_sha256",attempt.intent().intentSha256(),"invocation_pins_json",schema.canonical(produced.invocationPins()),"invocation_pins_sha256",schema.hash(produced.invocationPins()),"created_at",at.toInstant(ZoneOffset.UTC).toString()));
        fields.put("normalization_pins_json",schema.canonical(produced.normalizationPins()));fields.put("normalization_pins_sha256",schema.hash(produced.normalizationPins()));
        fields.put("supervision_pins_json",schema.canonical(produced.supervisionPins()));fields.put("supervision_pins_sha256",schema.hash(produced.supervisionPins()));
        for(var e:fields.entrySet())if(!e.getValue().equals(text(receipt,e.getKey())))bad();
        var metadata=meta(j,at,true);metadata.put("task_id",j.task());metadata.put("run_id",j.run());same(metadata,row);
        var ar=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND artifact_id=? LIMIT 2",j.tenant(),artifact));same(artifact(j,at,artifact,object,produced.dependencyManifest().required("access_set_id").asText(),schemaHash),ar);StoredObjectReferences.decode(str(ar,"object_ref"),object);
        var b=binding(j,at,artifact,produced.dependencyManifest().required("access_set_id").asText(),schema.hash(produced.dependencyManifest()),schemaHash);same(b,one(db.queryForList("SELECT * FROM artifact_content_binding WHERE tenant_id=? AND artifact_id=? LIMIT 2",j.tenant(),artifact)));
        same(Map.of("tenant_id",j.tenant(),"version_id",artifact,"owner_project_id",j.project(),"entity_kind","ARTIFACT","content_sha256",object.sha256()),one(db.queryForList("SELECT * FROM version_registry WHERE tenant_id=? AND version_id=? LIMIT 2",j.tenant(),artifact)));
        var roots=db.queryForList("SELECT * FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? ORDER BY upstream_version_id LIMIT 1025",j.tenant(),artifact);if(roots.size()!=produced.contentRoots().size())bad();
        for(int i=0;i<roots.size();i++)same(Map.of("tenant_id",j.tenant(),"consumer_version_id",artifact,"upstream_version_id",produced.contentRoots().get(i),"relation_kind","PLAN_DRAFT_CANDIDATE_SOURCE","actor_id",j.actor(),"created_at",at),roots.get(i));
        var reg=json.object();reg.set("artifact",json.tree(artifact(j,at,artifact,object,produced.dependencyManifest().required("access_set_id").asText(),schemaHash)));reg.set("binding",json.tree(b));reg.set("dependency_manifest",produced.dependencyManifest());reg.set("roots",json.tree(produced.contentRoots()));
        if(!schema.hash(reg).equals(text(receipt,"registration_receipt_sha256")))bad();verifyJournal(j,str(row,"audit_id"),str(row,"event_id"),artifact,schema.canonical(receipt),at);
        ancestry.requireUnchanged();return new Registered(produced,input,attempt,physical,ancestry,receipt,candidate);
    }
    void verifyOriginalMetadata(TaskPlanDraftHistoricalAccess.Verified human,TaskPlanDraftInvocationService.OriginalTerminal terminal,
            JsonNode receipt,InterfaceProtocol.ObjectRef object)throws Exception {
        human.requireUnchanged();terminal.requireUnchanged();var j=human.job();var row=one(rows(j));var at=time(row,"created_at");String artifact=text(receipt,"artifact_id"),set=terminal.accessSetId(),schemaHash=catalogue.schemaDigest("artifacts");
        if(!schema.canonical(receipt).equals(row.get("receipt_json"))||!schema.hash(receipt).equals(row.get("receipt_sha256"))||!text(receipt,"candidate_sha256").equals(row.get("candidate_sha256"))
                ||!artifact.equals(row.get("artifact_id"))||!text(receipt,"write_id").equals(row.get("write_id"))||!at.toInstant(ZoneOffset.UTC).toString().equals(text(receipt,"created_at")))bad();
        var metadata=meta(j,at,true);metadata.put("task_id",j.task());metadata.put("run_id",j.run());same(metadata,row);
        var ar=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND artifact_id=? LIMIT 2",j.tenant(),artifact));same(artifact(j,at,artifact,object,set,schemaHash),ar);StoredObjectReferences.decode(str(ar,"object_ref"),object);
        var b=binding(j,at,artifact,set,schema.hash(terminal.dependencyManifest()),schemaHash);same(b,one(db.queryForList("SELECT * FROM artifact_content_binding WHERE tenant_id=? AND artifact_id=? LIMIT 2",j.tenant(),artifact)));
        var roots=db.queryForList("SELECT * FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? ORDER BY upstream_version_id LIMIT 10001",j.tenant(),artifact);if(roots.size()!=terminal.contentRoots().size())bad();
        for(int i=0;i<roots.size();i++)same(Map.of("tenant_id",j.tenant(),"consumer_version_id",artifact,"upstream_version_id",terminal.contentRoots().get(i),"relation_kind","PLAN_DRAFT_CANDIDATE_SOURCE","actor_id",j.actor(),"created_at",at),roots.get(i));
        var reg=json.object();reg.set("artifact",json.tree(artifact(j,at,artifact,object,set,schemaHash)));reg.set("binding",json.tree(b));reg.set("dependency_manifest",terminal.dependencyManifest());reg.set("roots",json.tree(terminal.contentRoots()));
        if(!schema.hash(reg).equals(text(receipt,"registration_receipt_sha256")))bad();verifyJournal(j,str(row,"audit_id"),str(row,"event_id"),artifact,schema.canonical(receipt),at);terminal.requireUnchanged();human.requireUnchanged();
    }
    public final class Registered {
        private final TaskPlanDraftRuntimeAdapter.Produced produced;private final TaskPlanDraftObjectRegistration.Registered input;
        private final TaskPlanDraftObjectLedger.Attempt attempt;private final RuntimeObjectTransport.VerifiedTransfer physical;private final TaskPlanDraftContentAuthority.Verified ancestry;
        private final JsonNode receipt,candidate;
        private Registered(TaskPlanDraftRuntimeAdapter.Produced produced,TaskPlanDraftObjectRegistration.Registered input,TaskPlanDraftObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical,TaskPlanDraftContentAuthority.Verified ancestry,JsonNode receipt,JsonNode candidate){this.produced=produced;this.input=input;this.attempt=attempt;this.physical=physical;this.ancestry=ancestry;this.receipt=receipt.deepCopy();this.candidate=candidate.deepCopy();}
        void requireUnchanged()throws Exception{var fresh=verify(produced,input,attempt,physical,ancestry,one(rows(job())));if(!receipt.equals(fresh.receipt))bad();}
        TaskPlanDraftJobs.Job job(){return produced.authority().job();}String artifactId(){return attempt.intent().artifactId();}String receiptSha256()throws Exception{return schema.hash(receipt);}
        JsonNode receipt(){return receipt.deepCopy();}JsonNode candidate(){return candidate.deepCopy();}TaskPlanDraftRuntimeAdapter.Produced produced(){return produced;}
    }
    private List<Map<String,Object>> rows(TaskPlanDraftJobs.Job j){return db.queryForList("SELECT * FROM task_plan_draft_candidate WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.task(),j.run());}
    private Map<String,Object> artifact(TaskPlanDraftJobs.Job j,LocalDateTime at,String id,InterfaceProtocol.ObjectRef o,String set,String digest){var m=meta(j,at,true);m.put("schema_manifest_sha256",digest);m.put("artifact_id",id);m.put("run_id",j.run());m.put("document_revision",null);m.put("artifact_kind","PLAN_CANDIDATE");m.put("object_key",o.objectKey());m.put("object_version",o.objectVersion());m.put("sha256",o.sha256());m.put("size_bytes",o.sizeBytes());m.put("media_type",o.mediaType());m.put("dependency_access_set_id",set);m.put("content_sha256",o.sha256());return m;}
    private Map<String,Object> binding(TaskPlanDraftJobs.Job j,LocalDateTime at,String id,String set,String hash,String digest){var m=meta(j,at,false);m.put("artifact_id",id);m.put("resource_kind","TASK");m.put("resource_id",j.task());m.put("schema_ref",catalogue.schemaRef("artifacts",null));m.put("schema_document_sha256",digest);m.put("dependency_access_set_id",set);m.put("dependency_manifest_sha256",hash);return m;}
    private Map<String,Object> meta(TaskPlanDraftJobs.Job j,LocalDateTime at,boolean fields){var m=new LinkedHashMap<String,Object>();m.put("tenant_id",j.tenant());m.put("project_id",j.project());m.put("created_by",j.actor());m.put("created_at",at);m.put("classification",j.classification());if(fields){m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",schema.manifest());}return m;}
    private void journal(TaskPlanDraftJobs.Job j,String audit,String event,String artifact,String receipt,LocalDateTime at){var log=meta(j,at,true);log.put("audit_id",audit);log.put("subject_id",j.actor());log.put("action","task.plan_draft.candidate.registered");log.put("resource_ref",receipt);log.put("result","SUCCESS");log.put("request_id",artifact);log.put("trace_id",j.run());insert("audit_event",log);insert("outbox_event",Map.of("tenant_id",j.tenant(),"event_id",event,"aggregate_id",j.run(),"aggregate_revision",1,"event_type","task.plan_draft.candidate.registered","payload",receipt,"occurred_at",at));}
    private void verifyJournal(TaskPlanDraftJobs.Job j,String audit,String event,String artifact,String receipt,LocalDateTime at){var log=meta(j,at,true);log.put("audit_id",audit);log.put("subject_id",j.actor());log.put("action","task.plan_draft.candidate.registered");log.put("resource_ref",receipt);log.put("result","SUCCESS");log.put("request_id",artifact);log.put("trace_id",j.run());same(log,one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",j.tenant(),audit)));same(Map.of("tenant_id",j.tenant(),"event_id",event,"aggregate_id",j.run(),"aggregate_revision",1,"event_type","task.plan_draft.candidate.registered","payload",receipt,"occurred_at",at),one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",j.tenant(),event)));}
    private void insert(String table,Map<String,Object> m){var keys=new ArrayList<>(m.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(m::get).toArray())!=1)bad();}
    private void same(Map<String,Object> wanted,Map<String,Object> actual){for(var e:wanted.entrySet()){var v=actual.get(e.getKey());if(e.getValue() instanceof LocalDateTime t){if(!t.equals(time(actual,e.getKey())))bad();}else if(e.getValue()!=null&&v!=null&&(e.getValue() instanceof Number||v instanceof Number)){if(!e.getValue().toString().equals(v.toString()))bad();}else if(Set.of("payload","resource_ref","requirement_refs","allowed_resolution_actions").contains(e.getKey())&&e.getValue() instanceof String left&&v instanceof String right){try{if(!schema.read(left).equals(schema.read(right)))bad();}catch(Exception failure){bad();}}else if(!Objects.equals(e.getValue(),v))bad();}}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}private static LocalDateTime time(Map<String,Object> r,String k){var v=r.get(k);if(v instanceof LocalDateTime t)return t;if(v instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    private static String str(Map<String,Object> r,String k){if(!(r.get(k) instanceof String))bad();return (String)r.get(k);}private static String text(JsonNode n,String k){if(!n.required(k).isTextual())bad();return n.required(k).textValue();}
    private static String id(){return UUID.randomUUID().toString();}private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
