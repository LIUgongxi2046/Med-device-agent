package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Run-owned replay preparation only. Requires a private current bootstrap proof, never
 * a leased/fabricated step. The caller must commit the immutable attempt transaction before I/O;
 * existing attempts and reused in-memory attempts permit recovery, never another write.
 * Neither a planned UUID nor an attempt is a registered artifact or workflow receipt. */
public final class TaskRunReplayLedger {
    private final JdbcTemplate db;
    private final TaskRunBootstrapSchema schema;
    private final PermissionSchema json;
    private final int maximumBytes;
    public TaskRunReplayLedger(DataSource data,TaskRunBootstrapSchema schema,PermissionSchema json,int maximumBytes) {
        this(new JdbcTemplate(data),schema,json,maximumBytes);
    }
    TaskRunReplayLedger(JdbcTemplate db,TaskRunBootstrapSchema schema,PermissionSchema json,int maximumBytes) {
        this.db=Objects.requireNonNull(db);this.schema=Objects.requireNonNull(schema);this.json=Objects.requireNonNull(json);
        if(maximumBytes<1)throw new IllegalArgumentException("REPLAY_CAPACITY_REQUIRED");this.maximumBytes=maximumBytes;
    }

    public Intent prepare(TaskRunBootstrapAuthority.Verified proof)throws Exception {
        scope(proof);var replay=proof.replay();String payload=schema.canonical(replay.content());
        byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
        if(bytes.length<1||bytes.length>maximumBytes)fail(ErrorCode.CONTEXT_LIMIT);
        var original=command(proof);var existing=rows(proof);
        if(!existing.isEmpty())return verified(proof,one(existing));
        String write=id(),artifact=id();LocalDateTime now=now();
        var value=json.object().put("schema_version","1.0.0").put("tenant_id",proof.tenantId())
            .put("project_id",proof.projectId()).put("run_id",proof.runId()).put("principal_id",proof.principalId())
            .put("task_id",proof.taskId()).put("initiator_id",proof.actorId()).put("command_id",text(original,"command_id"))
            .put("outbox_event_id",proof.outboxEventId()).put("command_sha256",text(original,"command_sha256"))
            .put("write_id",write).put("write_key",key(proof.runId())).put("planned_artifact_id",artifact)
            .put("replay_schema_ref",RuntimeReplaySchema.REF).put("replay_schema_sha256",replay.schemaSha256())
            .put("replay_sha256",replay.rawSha256()).put("size_bytes",Integer.toString(bytes.length)).put("canonical_payload",payload)
            .put("object_key",objectKey(proof.tenantId(),proof.runId(),write,artifact)).put("media_type","application/json")
            .put("release_id",text(replay.content(),"agent_release_id"))
            .put("publication_approval_sha256",text(replay.content(),"publication_approval_sha256"))
            .put("authority_sha256",proof.authorizationsSha256()).put("classification",proof.classification())
            .put("created_at",now.toInstant(ZoneOffset.UTC).toString());
        value.set("execution_policy",replay.content().required("execution_policy_version"));
        schema.requireReplayIntent(value,replay);String canonical=schema.canonical(value),digest=schema.hash(value);
        changed(db.update("""
            INSERT INTO task_run_replay_intent(tenant_id,project_id,run_id,principal_id,task_id,command_id,outbox_event_id,
              command_sha256,write_id,write_key,planned_artifact_id,object_key,replay_sha256,replay_schema_sha256,size_bytes,
              canonical_payload,intent_json,intent_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),?,'1.0.0',?,?,?,?)
            """,proof.tenantId(),proof.projectId(),proof.runId(),proof.principalId(),proof.taskId(),text(original,"command_id"),
            proof.outboxEventId(),text(original,"command_sha256"),write,key(proof.runId()),artifact,text(value,"object_key"),
            replay.rawSha256(),replay.schemaSha256(),bytes.length,payload,canonical,digest,schema.manifest(),proof.actorId(),now,proof.classification()));
        String witness=witness(value,digest);
        changed(db.update("""
            INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,
              schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES(?,?,?,?,'task.run.replay.prepared',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)
            """,proof.tenantId(),proof.projectId(),id(),proof.actorId(),witness,write,proof.runId(),schema.manifest(),proof.actorId(),now,proof.classification()));
        changed(db.update("""
            INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at)
            VALUES(?,?,?,1,'task.run.replay.prepared',?,?)
            """,proof.tenantId(),write,proof.runId(),witness,now));
        var result=verified(proof,one(rows(proof)));
        if(!canonical.equals(result.canonical))bad();proof.requireUnchanged();return result;
    }
    public Intent requireExisting(TaskRunBootstrapAuthority.Verified proof)throws Exception {scope(proof);return verified(proof,one(rows(proof)));}
    public Attempt attempt(TaskRunBootstrapAuthority.Verified proof,Intent intent)throws Exception{return attempt(proof,intent,true);}
    public Attempt requireAttempt(TaskRunBootstrapAuthority.Verified proof,Intent intent)throws Exception{return attempt(proof,intent,false);}
    private Attempt attempt(TaskRunBootstrapAuthority.Verified proof,Intent expected,boolean create)throws Exception {
        scope(proof);Objects.requireNonNull(expected);var actual=verified(proof,one(rows(proof)));
        if(!expected.canonical.equals(actual.canonical)||!expected.digest.equals(actual.digest))bad();
        var attempts=attempts(proof,actual);if(attempts.size()>1)bad();
        if(!attempts.isEmpty()){
            var attempted=attempts.get(0);if(attempted.isBefore(actual.created)||attempted.isAfter(now()))bad();
            proof.requireUnchanged();return new Attempt(actual,true);
        }
        if(!create)bad();var now=now();if(now.isBefore(actual.created))bad();
        changed(db.update("""
            INSERT INTO task_run_replay_attempt(tenant_id,project_id,run_id,write_id,schema_version,
              schema_manifest_sha256,created_by,created_at,classification)
            VALUES(?,?,?,?,'1.0.0',?,?,?,?)
            """,proof.tenantId(),proof.projectId(),proof.runId(),actual.writeIntent().writeId(),schema.manifest(),proof.actorId(),now,proof.classification()));
        if(!now.equals(one(attempts(proof,actual))))bad();proof.requireUnchanged();return new Attempt(actual,false);
    }
    /** Running node production can only recover the original committed replay attempt.
     * The original authority digest remains pinned in the intent; current production
     * authority is revalidated separately and never rewrites that historical digest. */
    public Attempt requireOriginal(TaskNodeProductionAuthority.Verified proof)throws Exception {
        scope(proof);var intent=verified(proof,one(rows(proof)));
        var at=one(attempts(proof,intent));if(at.isBefore(intent.created)||at.isAfter(now()))bad();
        proof.requireUnchanged();return new Attempt(intent,true);
    }
    public Attempt requireOriginal(TaskNodeProductionAuthority.Verified proof,Intent expected)throws Exception {
        var result=requireOriginal(proof);
        if(expected==null||!expected.canonical.equals(result.intent.canonical)||!expected.digest.equals(result.intent.digest))bad();
        return result;
    }
    private List<LocalDateTime> attempts(TaskNodeProductionBasis p,Intent intent) {
        return db.query("""
            SELECT a.created_at FROM task_run_replay_attempt a JOIN task_run_replay_intent i
              ON i.tenant_id=a.tenant_id AND i.project_id=a.project_id AND i.run_id=a.run_id AND i.write_id=a.write_id
              AND i.created_by=a.created_by AND i.schema_version=a.schema_version
              AND i.schema_manifest_sha256=a.schema_manifest_sha256 AND i.classification=a.classification
            WHERE a.tenant_id=? AND a.project_id=? AND a.run_id=? AND a.write_id=? AND a.created_by=?
              AND a.schema_version='1.0.0' AND a.schema_manifest_sha256=? AND a.classification=? LIMIT 2 FOR UPDATE
            """,(r,n)->r.getTimestamp(1).toLocalDateTime(),p.tenantId(),p.projectId(),p.runId(),intent.writeIntent().writeId(),p.actorId(),schema.manifest(),p.classification());
    }
    private Intent verified(TaskNodeProductionBasis p,Row row)throws Exception {
        scope(p);var value=schema.validate("ReplayIntent",schema.read(row.intent));schema.requireReplayIntent(value,p.replay());
        var original=command(p);String canonical=schema.canonical(value);
        if(!row.tenant.equals(p.tenantId())||!row.project.equals(p.projectId())||!row.run.equals(p.runId())
            ||!row.actor.equals(p.actorId())||!row.classification.equals(p.classification())||!"1.0.0".equals(row.schemaVersion)
            ||!schema.manifest().equals(row.manifest)||!schema.hash(value).equals(row.digest)||row.created.isAfter(now())
            ||!row.created.toInstant(ZoneOffset.UTC).equals(java.time.Instant.parse(text(value,"created_at")))
            ||p instanceof TaskRunBootstrapAuthority.Verified&&!p.authorizationsSha256().equals(text(value,"authority_sha256"))
            ||!row.principal.equals(p.principalId())||!row.task.equals(p.taskId())||!row.event.equals(p.outboxEventId())
            ||!row.command.equals(text(original,"command_id"))||!row.commandHash.equals(text(original,"command_sha256")))bad();
        for(var entry:java.util.Map.of("principal_id",row.principal,"task_id",row.task,"outbox_event_id",row.event,
            "command_id",row.command,"command_sha256",row.commandHash,"write_id",row.write,"write_key",row.key,
            "planned_artifact_id",row.artifact,"object_key",row.objectKey).entrySet())if(!entry.getValue().equals(text(value,entry.getKey())))bad();
        if(!row.payloadHash.equals(text(value,"replay_sha256"))||!row.schemaHash.equals(text(value,"replay_schema_sha256"))
            ||!row.size.equals(text(value,"size_bytes"))||!canonical(row.payload).equals(text(value,"canonical_payload"))
            ||text(value,"canonical_payload").getBytes(StandardCharsets.UTF_8).length>maximumBytes)bad();
        String expected=witness(value,row.digest);
        String audit=one(db.query("""
            SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND created_by=?
              AND action='task.run.replay.prepared' AND result='SUCCESS' AND reason_code IS NULL AND request_id=? AND trace_id=?
              AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? AND created_at=? LIMIT 2 FOR UPDATE
            """,(r,n)->r.getString(1),p.tenantId(),p.projectId(),p.actorId(),p.actorId(),row.write,p.runId(),schema.manifest(),p.classification(),row.created));
        String event=one(db.query("""
            SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1
              AND event_type='task.run.replay.prepared' AND occurred_at=? LIMIT 2 FOR UPDATE
            """,(r,n)->r.getString(1),p.tenantId(),row.write,p.runId(),row.created));
        if(!expected.equals(canonical(audit))||!expected.equals(canonical(event)))bad();
        p.requireUnchanged();return new Intent(canonical,row.digest,value,row.created);
    }
    private List<Row> rows(TaskNodeProductionBasis p) {
        return db.query("""
            SELECT * FROM task_run_replay_intent WHERE tenant_id=? AND project_id=? AND run_id=? LIMIT 2 FOR UPDATE
            """,(r,n)->new Row(r.getString("tenant_id"),r.getString("project_id"),r.getString("run_id"),r.getString("principal_id"),
            r.getString("task_id"),r.getString("command_id"),r.getString("outbox_event_id"),r.getString("command_sha256"),
            r.getString("write_id"),r.getString("write_key"),r.getString("planned_artifact_id"),r.getString("object_key"),
            r.getString("replay_sha256"),r.getString("replay_schema_sha256"),r.getString("size_bytes"),r.getString("canonical_payload"),
            r.getString("intent_json"),r.getString("intent_sha256"),r.getString("schema_version"),r.getString("schema_manifest_sha256"),
            r.getString("created_by"),r.getTimestamp("created_at").toLocalDateTime(),r.getString("classification")),p.tenantId(),p.projectId(),p.runId());
    }
    private JsonNode command(TaskNodeProductionBasis p)throws Exception {
        var payload=p.command();var c=payload.required("command");
        if(!"START_RUN".equals(text(c,"kind"))||!"1.0.0".equals(text(c,"schema_version"))
            ||!p.tenantId().equals(text(c,"tenant_id"))||!p.projectId().equals(text(c,"project_id"))
            ||!p.runId().equals(text(c,"run_id"))||!p.taskId().equals(text(c,"task_id"))||!p.principalId().equals(text(c,"principal_id"))
            ||!p.actorId().equals(text(payload.required("decision"),"actor_id"))
            ||!p.replay().rawSha256().equals(text(c,"replay_sha256"))||!p.replay().schemaSha256().equals(text(c,"replay_schema_sha256"))
            ||!RuntimeReplaySchema.REF.equals(text(c,"replay_schema_ref"))||!(p.tenantId()+"/"+p.runId()).equals(text(c,"workflow_id")))bad();
        var without=payload.deepCopy();((ObjectNode)without.required("command")).remove("command_sha256");
        if(!schema.hash(without).equals(text(c,"command_sha256")))bad();return c;
    }
    private String witness(JsonNode intent,String digest)throws Exception {
        var n=json.object().put("schema_version","1.0.0").put("kind","RUN_REPLAY_PREPARED").put("intent_sha256",digest);
        for(String field:List.of("tenant_id","project_id","run_id","principal_id","task_id","initiator_id","command_id","command_sha256","write_id","planned_artifact_id","replay_sha256","replay_schema_sha256"))n.set(field,intent.required(field));
        return schema.canonical(n);
    }
    private String canonical(String raw)throws Exception{return schema.canonical(schema.read(raw));}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    private static void scope(TaskNodeProductionBasis proof)throws Exception {
        Objects.requireNonNull(proof);proof.requireUnchanged();var s=TenantScope.requireCurrent();
        if(!TransactionSynchronizationManager.isActualTransactionActive()||!s.tenantId().equals(proof.tenantId())||!s.subjectId().equals(proof.actorId()))fail(ErrorCode.ACCESS_DENIED);
    }
    static String key(String run){return DigestUtils.sha256Hex((run+"/RUN/REPLAY_MANIFEST").getBytes(StandardCharsets.US_ASCII));}
    private static String objectKey(String tenant,String run,String write,String artifact){return "runtime/"+tenant+"/"+run+"/"+write+"/"+artifact;}
    private static String id(){return UUID.randomUUID().toString();}
    private static String text(JsonNode n,String field){var v=n.required(field);if(!v.isTextual())bad();return v.textValue();}
    private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}
    private static void changed(int n){if(n!=1)bad();}
    private static void bad(){fail(ErrorCode.CONTENT_MISMATCH);}
    private static void fail(ErrorCode error){throw new TenantAccessException(error);}
    record Row(String tenant,String project,String run,String principal,String task,String command,String event,String commandHash,
        String write,String key,String artifact,String objectKey,String payloadHash,String schemaHash,String size,String payload,
        String intent,String digest,String schemaVersion,String manifest,String actor,LocalDateTime created,String classification){}
    public static final class Intent {
        private final String canonical,digest;private final JsonNode content;private final LocalDateTime created;
        private Intent(String canonical,String digest,JsonNode content,LocalDateTime created){this.canonical=canonical;this.digest=digest;this.content=content.deepCopy();this.created=created;}
        public JsonNode content(){return content.deepCopy();}public String sha256(){return digest;}
        public byte[] bytes(){return text(content,"canonical_payload").getBytes(StandardCharsets.UTF_8);}
        public String plannedArtifactId(){return text(content,"planned_artifact_id");}public String schemaDigest(){return text(content,"replay_schema_sha256");}
        public S3ObjectWriter.WriteIntent writeIntent(){return new S3ObjectWriter.WriteIntent(text(content,"write_id"),text(content,"object_key"),text(content,"replay_sha256"),text(content,"size_bytes"),"application/json");}
    }
    public static final class Attempt implements RuntimeObjectAttempt {
        private final Intent intent;private final boolean prior;private final AtomicBoolean consumed=new AtomicBoolean();
        private Attempt(Intent intent,boolean prior){this.intent=intent;this.prior=prior;}
        public Intent intent(){return intent;}public String tenantId(){return text(intent.content,"tenant_id");}
        public String actorId(){return text(intent.content,"initiator_id");}
        public String expectedObjectKey(){return objectKey(tenantId(),text(intent.content,"run_id"),intent.writeIntent().writeId(),intent.plannedArtifactId());}
        public S3ObjectWriter.WriteIntent writeIntent(){return intent.writeIntent();}public byte[] bytes(){return intent.bytes();}
        public boolean priorAttempt(){return prior;}
        public TransferMode claimTransferMode(){return prior||!consumed.compareAndSet(false,true)?TransferMode.RECOVER_ONLY:TransferMode.INITIAL_WRITE;}
    }
}
