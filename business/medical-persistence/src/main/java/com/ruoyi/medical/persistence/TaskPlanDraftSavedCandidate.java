package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** Independent exact-version physical reread for a real current human, without an old lease. */
public final class TaskPlanDraftSavedCandidate {
    private final JdbcTemplate db;private final EnterpriseAccess access;private final TaskPlanDraftHistoricalAccess historical;
    private final TaskPlanDraftSupervisor supervisor;private final TaskPlanDraftDeployment deployment;private final TaskPlanDraftInvocationService invocations;
    private final TaskPlanDraftCandidateNormalizer normalizer;private final TaskPlanDraftSchema schema;private final PermissionSchema json;
    private final TaskPlanDraftCandidateRegistration candidates;private final ArtifactRegistry registry;private final ArtifactSourceAuthority sourceAuthority;private final SourceReadMapper sources;
    private final TemplateDependencyPolicy limits;private final SourceUploadTransport transport;private final SchemaCatalogue shared;
    public TaskPlanDraftSavedCandidate(DataSource data,EnterpriseAccess access,TaskPlanDraftHistoricalAccess historical,TaskPlanDraftSupervisor supervisor,
            TaskPlanDraftDeployment deployment,TaskPlanDraftInvocationService invocations,TaskPlanDraftCandidateNormalizer normalizer,TaskPlanDraftSchema schema,
            PermissionSchema json,TaskPlanDraftCandidateRegistration candidates,ArtifactRegistry registry,ArtifactSourceAuthority sourceAuthority,SourceReadMapper sources,TemplateDependencyPolicy limits,SourceUploadTransport transport)throws Exception {
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.access=access;this.historical=historical;this.supervisor=supervisor;this.deployment=deployment;this.invocations=invocations;this.normalizer=normalizer;this.schema=schema;this.json=json;this.candidates=candidates;this.registry=registry;this.sourceAuthority=sourceAuthority;this.sources=sources;this.limits=limits;this.transport=transport;shared=SchemaCatalogue.fromClasspath();
    }
    Saved read(ApplicationSessionIdentity.Verified subject,String context,String project,String task,String run)throws Exception {
        var first=access.execute(subject,a->ThrowingSupplier.of(()->inspect(historical.require(a,context,project,task,run))).get());
        S3ObjectReader.Admission guard=()->access.execute(subject,a->ThrowingSupplier.of(()->{same(first,inspect(historical.require(a,context,project,task,run)));return null;}).get());
        byte[] bytes=transport.readDocument(subject,first.job.tenant(),1048576,guard,(reader,lease)->{
            S3ObjectReader.TimedAdmission bounded=new S3ObjectReader.TimedAdmission(){public java.time.Duration remainingTime(){return lease.remainingTime();}public void requireCurrent(){lease.requireCurrent();guard.requireCurrent();lease.requireCurrent();}};try(var actual=reader.readVerified(first.reference,bounded)){byte[] value=actual.chunk(0,Integer.parseInt(first.reference.sizeBytes()));bounded.requireCurrent();if(!WireValues.sha256(value).equals(first.reference.sha256())||!Arrays.equals(value,first.bytes))bad();return value;}
        });guard.requireCurrent();String read=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();
        var witness=access.execute(subject,a->ThrowingSupplier.of(()->{
            var proof=historical.require(a,context,project,task,run);var actual=inspect(proof);same(first,actual);var at=now();var value=json.object().put("kind","PLAN_DRAFT_CANDIDATE_EXACT_READ").put("read_id",read).put("event_id",event)
                    .put("tenant_id",first.job.tenant()).put("project_id",project).put("task_id",task).put("run_id",run).put("reader_id",a.actorId()).put("artifact_id",first.artifact)
                    .put("object_sha256",WireValues.sha256(bytes)).put("candidate_receipt_sha256",schema.hash(first.receipt)).put("authority_sha256",proof.authoritySha256()).put("created_at",at.toInstant(ZoneOffset.UTC).toString());value.set("object_ref",json.tree(first.reference));
            journal(proof,read,event,value,at);return value;
        }).get());return new Saved(subject,context,project,task,run,first,bytes,witness);
    }
    private Snapshot inspect(TaskPlanDraftHistoricalAccess.Verified human)throws Exception {
        human.requireUnchanged();var j=human.job();var row=one(db.queryForList("SELECT * FROM task_plan_draft_candidate WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? LIMIT 2",j.tenant(),j.project(),j.task(),j.run()));
        var receipt=schema.validate("CandidateReceipt",schema.read(str(row,"receipt_json")));String receiptHash=schema.hash(receipt),artifact=str(row,"artifact_id");
        if(!receiptHash.equals(row.get("receipt_sha256"))||!schema.canonical(receipt).equals(row.get("receipt_json")))bad();
        for(var e:Map.of("tenant_id",j.tenant(),"project_id",j.project(),"task_id",j.task(),"task_revision",j.taskRevision(),"run_id",j.run(),"actor_id",j.actor(),"input_sha256",j.inputHash(),"artifact_id",artifact).entrySet())if(!e.getValue().equals(receipt.required(e.getKey()).asText()))bad();
        for(String family:List.of("invocation","supervision","normalization")){var pins=schema.read(receipt.required(family+"_pins_json").asText());if(!schema.hash(pins).equals(receipt.required(family+"_pins_sha256").asText()))bad();}
        var supervision=schema.read(receipt.required("supervision_pins_json").asText());var closure=supervisor.requireOriginalClosure(human,receipt.required("session_id").asText(),supervision.required("receipt_id").asText(),supervision.required("sha256").asText(),deployment);
        var original=invocations.requireOriginal(human,schema.read(receipt.required("invocation_pins_json").asText()));if(!closure.sessionId().equals(original.sessionId()))bad();
        var result=original.output();var proposed=schema.read(result.required("blocks").get(0).required("text").asText());var normalized=normalizer.normalize(human,proposed);
        if(!normalized.pins().equals(schema.read(receipt.required("normalization_pins_json").asText()))||!schema.hash(normalized.candidate()).equals(receipt.required("candidate_sha256").asText()))bad();
        var requirement=new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(j.tenant(),j.project(),"TASK",j.task()),"task.read","TASK_READING",shared.schemaRef("artifacts",null),shared.schemaDigest("artifacts"),shared.schemaDigest("artifacts"),limits.limits());
        var registered=registry.requireRegistration(human.admission(),artifact,requirement);var manifest=registered.manifest();
        if(!manifest.access_set_id().equals(original.accessSetId())||!json.tree(manifest).equals(original.dependencyManifest()))bad();
        var leaves=new ArrayList<SourceReadProtocol.ReadRequirement>();
        for(String version:registered.lineage().versions().keySet()){
            var source=sources.source(version);if(source.size()>1)bad();if(source.isEmpty())continue;var actual=source.get(0);
            var member=one(manifest.members().stream().filter(m->m.upstreamVersionId().equals(version)).toList());
            leaves.add(new SourceReadProtocol.ReadRequirement(version,member.rightsRevision(),j.project(),null,null,null,SourceReadProtocol.Use.REVIEW,"PLAN_MAINTENANCE",limits.environment(),limits.region(),null));
        }
        sourceAuthority.require(human.admission(),artifact,requirement,leaves,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),Map.of());
        var ref=registered.artifact().reference();if(!ref.sha256().equals(receipt.required("object_sha256").asText())||!ref.objectVersion().equals(receipt.required("object_version").asText())||!registered.artifact().accessSetId().equals(receipt.required("access_set_id").asText()))bad();
        var intent=one(db.queryForList("SELECT * FROM task_plan_draft_object_intent WHERE tenant_id=? AND run_id=? AND write_id=? AND artifact_id=? AND object_kind='PLAN_CANDIDATE' LIMIT 2",j.tenant(),j.run(),receipt.required("write_id").asText(),artifact));
        var attempt=one(db.queryForList("SELECT * FROM task_plan_draft_object_attempt WHERE tenant_id=? AND run_id=? AND write_id=? LIMIT 2",j.tenant(),j.run(),receipt.required("write_id").asText()));
        if(!receipt.required("intent_sha256").asText().equals(intent.get("intent_sha256"))||!intent.get("intent_sha256").equals(attempt.get("intent_sha256"))||!ref.objectKey().equals(intent.get("object_key")))bad();
        var preparedAudit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",j.tenant(),intent.get("audit_id")));
        var preparedEvent=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",j.tenant(),intent.get("event_id")));
        var prepared=schema.read(str(preparedAudit,"resource_ref"));
        if(!schema.hash(prepared).equals(intent.get("intent_sha256"))||!prepared.equals(schema.read(str(preparedEvent,"payload")))||!"task.plan_draft.candidate.prepared".equals(preparedAudit.get("action"))||!"SUCCESS".equals(preparedAudit.get("result"))||!j.actor().equals(preparedAudit.get("subject_id"))||!j.actor().equals(preparedAudit.get("created_by"))||!"task.plan_draft.candidate.prepared".equals(preparedEvent.get("event_type"))||!j.run().equals(preparedEvent.get("aggregate_id")))bad();
        for(var e:Map.of("tenant_id",j.tenant(),"project_id",j.project(),"task_id",j.task(),"run_id",j.run(),"write_id",receipt.required("write_id").asText(),"artifact_id",artifact,"payload_sha256",ref.sha256(),"object_key",ref.objectKey(),"input_sha256",j.inputHash()).entrySet())if(!e.getValue().equals(prepared.required(e.getKey()).asText()))bad();
        var producer=prepared.required("producer");var admitted=schema.read(j.authorityJson());
        if(!"N01_FIXED_PLAN_PRODUCER".equals(producer.required("kind").asText())||!j.run().equals(producer.required("run_id").asText())||!schema.hash(admitted).equals(producer.required("authority_sha256").asText())||!admitted.required("retention").equals(producer.required("retention"))||!admitted.required("sources").equals(producer.required("source_closure")))bad();
        byte[] bytes=str(intent,"payload").getBytes(StandardCharsets.UTF_8);if(!WireValues.sha256(bytes).equals(ref.sha256())||!Integer.toString(bytes.length).equals(ref.sizeBytes()))bad();var full=schema.read(str(intent,"payload"));TypedSchemaBindings.bind(shared,ArtifactBindings.ARTIFACT,full).read();
        if(!normalized.candidate().equals(full.required("output").required("content"))||!"PLAN_CANDIDATE".equals(full.required("output").required("artifact_kind").asText())||!original.accessSetId().equals(full.required("dependency_access_set_id").asText()))bad();
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",j.tenant(),row.get("audit_id")));var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",j.tenant(),row.get("event_id")));
        if(!j.actor().equals(audit.get("subject_id"))||!j.actor().equals(audit.get("created_by"))||!"task.plan_draft.candidate.registered".equals(audit.get("action"))||!"SUCCESS".equals(audit.get("result"))||!receipt.equals(schema.read(str(audit,"resource_ref")))||!j.run().equals(event.get("aggregate_id"))||!"task.plan_draft.candidate.registered".equals(event.get("event_type"))||!receipt.equals(schema.read(str(event,"payload"))))bad();
        candidates.verifyOriginalMetadata(human,original,receipt,ref);closure.requireUnchanged();original.requireUnchanged();human.requireUnchanged();return new Snapshot(j,artifact,receipt,normalized.candidate(),ref,bytes);
    }
    public final class Saved {
        private final ApplicationSessionIdentity.Verified subject;private final String context,project,task,run;private final Snapshot snapshot;private final byte[] bytes;private final JsonNode witness;
        private Saved(ApplicationSessionIdentity.Verified subject,String context,String project,String task,String run,Snapshot snapshot,byte[] bytes,JsonNode witness){this.subject=subject;this.context=context;this.project=project;this.task=task;this.run=run;this.snapshot=snapshot;this.bytes=bytes.clone();this.witness=witness.deepCopy();}
        Verified require(EnterpriseAccess.Admission a)throws Exception {
            if(!subject.sessionId().equals(a.subject().sessionId())||!subject.user().user_id().equals(a.actorId()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            var human=historical.require(a,context,project,task,run);same(snapshot,inspect(human));if(!WireValues.sha256(bytes).equals(snapshot.reference.sha256()))bad();
            var audit=one(db.queryForList("SELECT resource_ref,subject_id,action,result FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",snapshot.job.tenant(),witness.required("read_id").asText()));
            if(!a.actorId().equals(audit.get("subject_id"))||!"task.plan_draft.candidate.read".equals(audit.get("action"))||!"SUCCESS".equals(audit.get("result"))||!witness.equals(schema.read(str(audit,"resource_ref"))))bad();
            var event=one(db.queryForList("SELECT payload,event_type,aggregate_id FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",snapshot.job.tenant(),witness.required("event_id").asText()));if(!"task.plan_draft.candidate.read".equals(event.get("event_type"))||!snapshot.job.run().equals(event.get("aggregate_id"))||!witness.equals(schema.read(str(event,"payload"))))bad();return new Verified(this,human);
        }
    }
    public final class Verified {
        private final Saved saved;private final TaskPlanDraftHistoricalAccess.Verified human;private Verified(Saved saved,TaskPlanDraftHistoricalAccess.Verified human){this.saved=saved;this.human=human;}
        void requireUnchanged()throws Exception{human.requireUnchanged();saved.require(human.admission());}TaskPlanDraftHistoricalAccess.Verified human(){return human;}TaskPlanDraftJobs.Job job(){return saved.snapshot.job;}String artifactId(){return saved.snapshot.artifact;}JsonNode receipt(){return saved.snapshot.receipt.deepCopy();}String receiptSha256()throws Exception{return schema.hash(receipt());}JsonNode candidate(){return saved.snapshot.candidate.deepCopy();}
    }
    private record Snapshot(TaskPlanDraftJobs.Job job,String artifact,JsonNode receipt,JsonNode candidate,InterfaceProtocol.ObjectRef reference,byte[] bytes){Snapshot{receipt=receipt.deepCopy();candidate=candidate.deepCopy();bytes=bytes.clone();}}
    private void same(Snapshot a,Snapshot b){if(!a.job.equals(b.job)||!a.artifact.equals(b.artifact)||!a.receipt.equals(b.receipt)||!a.candidate.equals(b.candidate)||!a.reference.equals(b.reference)||!Arrays.equals(a.bytes,b.bytes))bad();}
    private void journal(TaskPlanDraftHistoricalAccess.Verified proof,String read,String event,JsonNode witness,LocalDateTime at)throws Exception {var j=proof.job();var a=proof.admission();String raw=schema.canonical(witness);int n=db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'task.plan_draft.candidate.read',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",j.tenant(),j.project(),read,a.actorId(),raw,read,j.run(),schema.manifest(),a.actorId(),at,j.classification());if(n!=1)bad();if(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,'task.plan_draft.candidate.read',?,?)",j.tenant(),event,j.run(),raw,at)!=1)bad();}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}private static String str(Map<String,Object> row,String key){if(!(row.get(key) instanceof String))bad();return (String)row.get(key);}private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
