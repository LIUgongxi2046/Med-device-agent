package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Original-only PLAN_DRAFT input transfer. An intent/planned UUID is not a registered artifact.
 * Every physical-I/O guard must reacquire current admission and requireAttempt in a new transaction.
 * Candidate output is deliberately not accepted as a caller byte[]; its actual host producer is separate.
 */
public final class TaskPlanDraftObjectLedger {
    private final JdbcTemplate db; private final TaskPlanDraftSchema schema; private final TaskPlanDraftJobs jobs;private final TaskPlanDraftProducerAuthority producers;private final TaskPlanDraftJobAuthority authority;
    public TaskPlanDraftObjectLedger(DataSource data, TaskPlanDraftSchema schema, TaskPlanDraftJobs jobs,TaskPlanDraftProducerAuthority producers,TaskPlanDraftJobAuthority authority) {
        this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.jobs=jobs;this.producers=producers;this.authority=authority;
    }
    Intent prepareInput(TaskPlanDraftAdmission.Verified proof,TaskPlanDraftJobs.Job expected)throws Exception {
        var job=current(proof,expected);var payload=jobs.input(job);schema.validate("InputManifest",payload);
        String canonical=schema.canonical(payload);byte[] bytes=canonical.getBytes(StandardCharsets.UTF_8);
        if(bytes.length==0||bytes.length>1048576)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        producers.require(authority.require(proof,job)).requireBytes("PLAN_DRAFT_INPUT",bytes.length);
        if(!WireValues.sha256(bytes).equals(job.inputHash()))bad();
        var existing=rows(job);
        if(!existing.isEmpty())return read(job,one(existing));
        String write=id(),artifact=id(),audit=id(),event=id();var now=now();
        var document=identity(job).put("write_id",write).put("artifact_id",artifact).put("object_kind","PLAN_DRAFT_INPUT")
                .put("object_key",objectKey(job.tenant(),job.run(),write,artifact)).put("payload_sha256",job.inputHash())
                .put("size_bytes",Integer.toString(bytes.length)).put("created_at",now.toInstant(ZoneOffset.UTC).toString());
        String digest=schema.hash(document),witness=schema.canonical(document);
        journal(job,audit,event,"task.plan_draft.object.prepared",write,witness,now);
        changed(db.update("""
            INSERT INTO task_plan_draft_object_intent(tenant_id,project_id,task_id,run_id,write_id,step_id,artifact_id,object_kind,
              action_id,lease_generation,object_key,payload,payload_sha256,size_bytes,intent_sha256,audit_id,event_id,
              schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES(?,?,?,?,?,NULL,?,'PLAN_DRAFT_INPUT',NULL,NULL,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)
            """,job.tenant(),job.project(),job.task(),job.run(),write,artifact,text(document,"object_key"),canonical,job.inputHash(),bytes.length,
                digest,audit,event,schema.manifest(),job.actor(),now,job.classification()));
        var result=read(job,one(rows(job)));if(!result.digest.equals(digest))bad();proof.requireUnchanged();return result;
    }
    Attempt attemptInput(TaskPlanDraftAdmission.Verified proof,TaskPlanDraftJobs.Job expected,Intent intended)throws Exception {
        var job=current(proof,expected);var intent=read(job,one(rows(job)));same(intent,intended);
        var attempts=attempts(job,intent);
        if(!attempts.isEmpty()) {verifyAttempt(job,intent,one(attempts));return new Attempt(intent,true);}
        var now=now();if(now.isBefore(intent.created))bad();
        changed(db.update("""
            INSERT INTO task_plan_draft_object_attempt(tenant_id,project_id,task_id,run_id,write_id,intent_sha256,
              schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES(?,?,?,?,?,?,'1.0.0',?,?,?,?)
            """,job.tenant(),job.project(),job.task(),job.run(),intent.write,digest(intent),schema.manifest(),job.actor(),now,job.classification()));
        verifyAttempt(job,intent,one(attempts(job,intent)));proof.requireUnchanged();return new Attempt(intent,false);
    }
    void requireAttempt(TaskPlanDraftAdmission.Verified proof,TaskPlanDraftJobs.Job expected,Intent intended)throws Exception {
        var job=current(proof,expected);var intent=read(job,one(rows(job)));same(intent,intended);
        verifyAttempt(job,intent,one(attempts(job,intent)));proof.requireUnchanged();
    }
    Intent prepareCandidate(TaskPlanDraftRuntimeAdapter.Produced produced)throws Exception {
        produced.requireUnchanged();var authority=produced.authority();var job=authority.job();
        byte[] bytes=produced.bytes();var payload=schema.read(new String(bytes,StandardCharsets.UTF_8));
        com.ruoyi.medical.contracts.TypedSchemaBindings.bind(com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath(),com.ruoyi.medical.contracts.ArtifactBindings.ARTIFACT,payload).read();
        if(bytes.length==0||bytes.length>1048576||!Arrays.equals(bytes,schema.canonical(payload).getBytes(StandardCharsets.UTF_8))
                ||!"PLAN_CANDIDATE".equals(payload.required("output").required("artifact_kind").asText()))bad();
        var existing=candidateRows(job);if(!existing.isEmpty())return readCandidate(produced,one(existing));
        String write=id(),artifact=id(),audit=id(),event=id();var now=now();String sha=WireValues.sha256(bytes);
        var document=candidateDocument(produced,write,artifact,sha,bytes.length,now);String digest=schema.hash(document);
        journal(job,audit,event,"task.plan_draft.candidate.prepared",write,schema.canonical(document),now);
        changed(db.update("""
            INSERT INTO task_plan_draft_object_intent(tenant_id,project_id,task_id,run_id,write_id,step_id,artifact_id,object_kind,
              action_id,lease_generation,object_key,payload,payload_sha256,size_bytes,intent_sha256,audit_id,event_id,
              schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES(?,?,?,?,?,?,?,'PLAN_CANDIDATE',?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)
            """,job.tenant(),job.project(),job.task(),job.run(),write,authority.stepId(),artifact,authority.actionId(),authority.leaseGeneration(),
                text(document,"object_key"),schema.canonical(payload),sha,bytes.length,digest,audit,event,schema.manifest(),job.actor(),now,job.classification()));
        produced.requireUnchanged();return readCandidate(produced,one(candidateRows(job)));
    }
    Attempt attemptCandidate(TaskPlanDraftRuntimeAdapter.Produced produced,Intent intended)throws Exception {
        produced.requireUnchanged();var job=produced.authority().job();var intent=readCandidate(produced,one(candidateRows(job)));same(intent,intended);
        var prior=attempts(job,intent);if(!prior.isEmpty()){verifyAttempt(job,intent,one(prior));return new Attempt(intent,true);}
        var now=now();if(now.isBefore(intent.created))bad();
        changed(db.update("INSERT INTO task_plan_draft_object_attempt(tenant_id,project_id,task_id,run_id,write_id,intent_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'1.0.0',?,?,?,?)",
                job.tenant(),job.project(),job.task(),job.run(),intent.write,intent.digest,schema.manifest(),job.actor(),now,job.classification()));
        verifyAttempt(job,intent,one(attempts(job,intent)));produced.requireUnchanged();return new Attempt(intent,false);
    }
    void requireCandidateAttempt(TaskPlanDraftRuntimeAdapter.Produced produced,Intent expected)throws Exception {
        produced.requireUnchanged();var job=produced.authority().job();var intent=readCandidate(produced,one(candidateRows(job)));same(intent,expected);
        verifyAttempt(job,intent,one(attempts(job,intent)));produced.requireUnchanged();
    }
    private List<Map<String,Object>> candidateRows(TaskPlanDraftJobs.Job job){return db.queryForList("SELECT * FROM task_plan_draft_object_intent WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND object_kind='PLAN_CANDIDATE' LIMIT 2 FOR UPDATE",job.tenant(),job.project(),job.task(),job.run());}
    private com.fasterxml.jackson.databind.node.ObjectNode candidateDocument(TaskPlanDraftRuntimeAdapter.Produced produced,String write,String artifact,String sha,int size,LocalDateTime now)throws Exception {
        var authority=produced.authority();var job=authority.job();var producer=producers.require(authority.current());producer.requireBytes("PLAN_CANDIDATE",size);var doc=identity(job).put("input_sha256",job.inputHash()).put("write_id",write).put("artifact_id",artifact).put("object_kind","PLAN_CANDIDATE")
                .put("step_id",authority.stepId()).put("action_id",authority.actionId()).put("lease_generation",authority.leaseGeneration().toString())
                .put("session_id",produced.sessionId()).put("invocation_pins_sha256",schema.hash(produced.invocationPins())).put("normalization_pins_sha256",schema.hash(produced.normalizationPins()))
                .put("object_key",objectKey(job.tenant(),job.run(),write,artifact)).put("payload_sha256",sha).put("size_bytes",Integer.toString(size)).put("created_at",now.toInstant(ZoneOffset.UTC).toString());
        doc.set("producer",producer.canonicalPins());return doc;
    }
    private Intent readCandidate(TaskPlanDraftRuntimeAdapter.Produced produced,Map<String,Object> row)throws Exception {
        produced.requireUnchanged();var authority=produced.authority();var job=authority.job();metadata(job,row);var created=time(row,"created_at");
        byte[] actual=produced.bytes();String payload=str(row,"payload"),write=str(row,"write_id"),artifact=str(row,"artifact_id");
        WireValues.uuid(write);WireValues.uuid(artifact);
        if(!Arrays.equals(actual,payload.getBytes(StandardCharsets.UTF_8))||!authority.stepId().equals(row.get("step_id"))||!authority.actionId().equals(row.get("action_id"))
                ||!authority.leaseGeneration().toString().equals(row.get("lease_generation").toString())||!"PLAN_CANDIDATE".equals(row.get("object_kind"))
                ||!Integer.toString(actual.length).equals(row.get("size_bytes").toString())||!WireValues.sha256(actual).equals(row.get("payload_sha256"))
                ||!objectKey(job.tenant(),job.run(),write,artifact).equals(row.get("object_key"))||created.isBefore(job.created())||created.isAfter(now()))bad();
        var doc=candidateDocument(produced,write,artifact,WireValues.sha256(actual),actual.length,created);String digest=schema.hash(doc);
        if(!digest.equals(row.get("intent_sha256")))bad();
        verifyJournal(job,str(row,"audit_id"),str(row,"event_id"),"task.plan_draft.candidate.prepared",write,schema.canonical(doc),created);
        return new Intent(job,write,artifact,payload,digest,created);
    }
    private TaskPlanDraftJobs.Job current(TaskPlanDraftAdmission.Verified proof,TaskPlanDraftJobs.Job expected)throws Exception {
        TaskPlanDraftAdmission.current(proof.admission(),proof.context());proof.requireUnchanged();
        var job=jobs.byRun(proof.admission().context().tenant_id(),expected.run());
        if(!job.equals(expected)||!job.actor().equals(proof.admission().actorId())||!job.project().equals(proof.project())
                ||!job.task().equals(proof.task())||!job.taskRevision().equals(proof.fixedTask().head().taskRevision())
                ||!schema.canonical(proof.evidence()).equals(job.authorityJson()))bad();
        producers.require(authority.require(proof,job)).requireUnchanged();return job;
    }
    private List<Map<String,Object>> rows(TaskPlanDraftJobs.Job job) {
        return db.queryForList("SELECT * FROM task_plan_draft_object_intent WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND object_kind='PLAN_DRAFT_INPUT' LIMIT 2 FOR UPDATE",
                job.tenant(),job.project(),job.task(),job.run());
    }
    private List<Map<String,Object>> attempts(TaskPlanDraftJobs.Job job,Intent intent) {
        return db.queryForList("SELECT * FROM task_plan_draft_object_attempt WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND write_id=? LIMIT 2",
                job.tenant(),job.project(),job.task(),job.run(),intent.write);
    }
    private Intent read(TaskPlanDraftJobs.Job job,Map<String,Object> row)throws Exception {
        metadata(job,row);String write=str(row,"write_id"),artifact=str(row,"artifact_id"),payload=str(row,"payload");
        WireValues.uuid(write);WireValues.uuid(artifact);var created=time(row,"created_at");
        if(row.get("step_id")!=null||row.get("action_id")!=null||row.get("lease_generation")!=null||!"PLAN_DRAFT_INPUT".equals(row.get("object_kind"))
                ||created.isBefore(job.created())||created.isAfter(now()))bad();
        var parsed=schema.validate("InputManifest",schema.read(payload));
        if(!payload.equals(schema.canonical(parsed))||!parsed.equals(jobs.input(job)))bad();
        byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
        if(bytes.length==0||bytes.length>1048576||!job.inputHash().equals(WireValues.sha256(bytes))||!job.inputHash().equals(row.get("payload_sha256"))
                ||!Integer.toString(bytes.length).equals(row.get("size_bytes").toString())||!objectKey(job.tenant(),job.run(),write,artifact).equals(row.get("object_key")))bad();
        var document=identity(job).put("write_id",write).put("artifact_id",artifact).put("object_kind","PLAN_DRAFT_INPUT")
                .put("object_key",str(row,"object_key")).put("payload_sha256",job.inputHash()).put("size_bytes",Integer.toString(bytes.length))
                .put("created_at",created.toInstant(ZoneOffset.UTC).toString());
        String digest=schema.hash(document);if(!digest.equals(row.get("intent_sha256")))bad();
        verifyJournal(job,str(row,"audit_id"),str(row,"event_id"),"task.plan_draft.object.prepared",write,schema.canonical(document),created);
        return new Intent(job,write,artifact,payload,digest,created);
    }
    private void verifyAttempt(TaskPlanDraftJobs.Job job,Intent intent,Map<String,Object> row) {
        metadata(job,row);var time=time(row,"created_at");
        if(!intent.write.equals(row.get("write_id"))||!intent.digest.equals(row.get("intent_sha256"))||time.isBefore(intent.created)||time.isAfter(now()))bad();
    }
    private void metadata(TaskPlanDraftJobs.Job job,Map<String,Object> row) {
        if(!job.tenant().equals(row.get("tenant_id"))||!job.project().equals(row.get("project_id"))||!job.task().equals(row.get("task_id"))
                ||!job.run().equals(row.get("run_id"))||!job.actor().equals(row.get("created_by"))||!job.classification().equals(row.get("classification"))
                ||!"1.0.0".equals(row.get("schema_version"))||!schema.manifest().equals(row.get("schema_manifest_sha256")))bad();
    }
    private com.fasterxml.jackson.databind.node.ObjectNode identity(TaskPlanDraftJobs.Job job) {
        return ((com.fasterxml.jackson.databind.node.ObjectNode)schema.tree(Map.of("schema_version","1.0.0","tenant_id",job.tenant(),"project_id",job.project(),
                "task_id",job.task(),"run_id",job.run(),"actor_id",job.actor())));
    }
    private void journal(TaskPlanDraftJobs.Job j,String audit,String event,String action,String request,String witness,LocalDateTime now) {
        changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",
                j.tenant(),j.project(),audit,j.actor(),action,witness,request,j.run(),schema.manifest(),j.actor(),now,j.classification()));
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",j.tenant(),event,j.run(),action,witness,now));
    }
    private void verifyJournal(TaskPlanDraftJobs.Job j,String audit,String event,String action,String request,String witness,LocalDateTime now)throws Exception {
        var audits=db.queryForList("SELECT resource_ref,created_at FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action=? AND result='SUCCESS' AND request_id=? AND trace_id=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND created_by=? AND classification=? LIMIT 2",
                j.tenant(),j.project(),audit,j.actor(),action,request,j.run(),schema.manifest(),j.actor(),j.classification());
        var a=one(audits);if(!schema.read(witness).equals(schema.read(str(a,"resource_ref")))||!now.equals(time(a,"created_at")))bad();
        var events=db.queryForList("SELECT payload,occurred_at FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type=? LIMIT 2",j.tenant(),event,j.run(),action);
        var e=one(events);if(!schema.read(witness).equals(schema.read(str(e,"payload")))||!now.equals(time(e,"occurred_at")))bad();
    }
    static final class Intent {
        private final TaskPlanDraftJobs.Job job;private final String write,artifact,payload,digest;private final LocalDateTime created;
        private Intent(TaskPlanDraftJobs.Job job,String write,String artifact,String payload,String digest,LocalDateTime created){this.job=job;this.write=write;this.artifact=artifact;this.payload=payload;this.digest=digest;this.created=created;}
        String artifactId(){return artifact;}String sha256(){return org.apache.commons.codec.digest.DigestUtils.sha256Hex(payload.getBytes(StandardCharsets.UTF_8));}String intentSha256(){return digest;}
        byte[] bytes(){return payload.getBytes(StandardCharsets.UTF_8);}
        S3ObjectWriter.WriteIntent writeIntent(){return new S3ObjectWriter.WriteIntent(write,objectKey(job.tenant(),job.run(),write,artifact),sha256(),Integer.toString(bytes().length),"application/json");}
    }
    public static final class Attempt implements RuntimeObjectAttempt {
        private final Intent intent;private final boolean prior;private final AtomicBoolean used=new AtomicBoolean();
        private Attempt(Intent intent,boolean prior){this.intent=intent;this.prior=prior;}
        public String tenantId(){return intent.job.tenant();}public String actorId(){return intent.job.actor();}
        public String expectedObjectKey(){return objectKey(tenantId(),intent.job.run(),intent.write,intent.artifact);}
        public S3ObjectWriter.WriteIntent writeIntent(){return intent.writeIntent();}public byte[] bytes(){return intent.bytes();}
        public boolean priorAttempt(){return prior;}public TransferMode claimTransferMode(){return prior||!used.compareAndSet(false,true)?TransferMode.RECOVER_ONLY:TransferMode.INITIAL_WRITE;}
        Intent intent(){return intent;}
    }
    private static String objectKey(String tenant,String run,String write,String artifact){return "runtime/"+tenant+"/"+run+"/plan-draft/"+write+"/"+artifact;}
    private static String digest(Intent i){return i.digest;}private static void same(Intent a,Intent b){if(b==null||!a.job.equals(b.job)||!a.write.equals(b.write)||!a.digest.equals(b.digest))bad();}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    private static LocalDateTime time(Map<String,Object> row,String key){var value=row.get(key);if(value instanceof LocalDateTime t)return t;if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    private static String str(Map<String,Object> row,String key){if(!(row.get(key) instanceof String))bad();return (String)row.get(key);}
    private static String text(JsonNode node,String key){return node.required(key).textValue();}private static String id(){return UUID.randomUUID().toString();}
    private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void changed(int n){if(n!=1)bad();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
