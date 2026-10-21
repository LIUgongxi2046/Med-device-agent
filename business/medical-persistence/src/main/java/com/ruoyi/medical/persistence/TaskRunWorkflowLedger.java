package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Immutable launch/attempt/acceptance ledger. A committed attempt permits one physical start;
 * all subsequent deliveries are query-only, including an ambiguous NOT_FOUND. No run status,
 * step, provider result or outbox acknowledgement is manufactured by this ledger. */
public final class TaskRunWorkflowLedger {
    private final JdbcTemplate db;
    private final TaskRunBootstrapSchema schema;
    private final JsonMapper json=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    public TaskRunWorkflowLedger(DataSource data,TaskRunBootstrapSchema schema){this(new JdbcTemplate(data),schema);}
    TaskRunWorkflowLedger(JdbcTemplate db,TaskRunBootstrapSchema schema){this.db=Objects.requireNonNull(db);this.schema=Objects.requireNonNull(schema);db.setQueryTimeout(5);}

    public Intent prepare(TaskRunBootstrapAuthority.Verified p,TaskRunReplayRegistration.Registered registered,
            TaskRunWorkflowConfiguration configuration)throws Exception {
        var c=context(p);c.check();Objects.requireNonNull(registered);Objects.requireNonNull(configuration);
        var registration=registration(c);
        if(!registered.receipt().equals(registration)||!schema.hash(registration).equals(registered.receiptSha256())
                ||!registered.artifactId().equals(text(registration,"artifact_id"))
                ||!registered.reference().sha256().equals(text(registration.required("object_ref"),"sha256")))bad();
        String service=text(c.replay.required("execution_policy").required("worker"),"service_uri");
        if(!service.equals(configuration.serviceUri()))bad();
        var existing=rows(c);
        if(!existing.isEmpty()){
            var result=verify(c,one(existing));requireConfiguration(result,configuration);return result;
        }
        var now=now();var reference=configuration.reference(registered,now.toInstant(ZoneOffset.UTC).toEpochMilli());
        var value=json.createObjectNode().put("schema_version","1.0.0");
        for(String field:IDENTITY)value.set(field,registration.required(field));
        value.put("launch_id",UUID.randomUUID().toString()).put("command_sha256",text(registration,"command_sha256"))
            .put("replay_registration_sha256",registered.receiptSha256()).put("workflow_id",reference.workflowId())
            .put("created_at",now.toInstant(ZoneOffset.UTC).toString()).put("temporal_namespace",configuration.namespace())
            .put("service_uri",configuration.serviceUri()).put("operational_revision",configuration.revision())
            .put("operational_sha256",schema.hash(configuration));
        var ref=json.valueToTree(reference);value.set("reference",ref);value.put("reference_sha256",schema.hash(ref));
        schema.requireLaunch(registration,value);
        var values=metadata(c,now);values.put("launch_id",text(value,"launch_id"));values.put("command_id",c.commandId());
        values.put("command_sha256",text(c.command,"command_sha256"));values.put("workflow_id",reference.workflowId());
        values.put("reference_json",schema.canonical(ref));values.put("reference_sha256",schema.hash(ref));
        values.put("intent_json",schema.canonical(value));values.put("intent_sha256",schema.hash(value));
        insert("task_run_workflow_launch",values);c.check();return verify(c,one(rows(c)));
    }
    public Attempt attempt(TaskRunBootstrapAuthority.Verified p,Intent expected)throws Exception {
        var c=context(p);var actual=verify(c,one(rows(c)));same(expected,actual);
        var attempted=attempts(c,actual);
        if(!attempted.isEmpty()){one(attempted);c.check();return new Attempt(actual,true);}
        var now=now();if(now.isBefore(actual.created))bad();var values=metadata(c,now);
        values.put("launch_id",actual.launchId());insert("task_run_workflow_attempt",values);
        one(attempts(c,actual));c.check();return new Attempt(actual,false);
    }
    /** Fresh new-start authorization before I/O. This is not used after an actual start. */
    public StartPermit authorizeStart(TaskRunBootstrapAuthority.Verified p,Attempt attempt,TaskRunWorkflowConfiguration configuration)throws Exception {
        if(attempt.recoveryOnly)bad();
        var c=context(p);var actual=verify(c,one(rows(c)));same(actual,attempt.intent);one(attempts(c,actual));
        requireConfiguration(actual,configuration);
        if(actual.reference.deadlineEpochMillis()<=now().toInstant(ZoneOffset.UTC).toEpochMilli())bad();c.check();return new StartPermit(attempt);
    }
    /** Probe only existing attempted launches. A committed intent without an attempt still needs live start authority. */
    public Optional<Intent> attempted(TaskRunLaunchRecoveryAuthority.Verified p)throws Exception {
        var c=context(p);var rows=rows(c);if(rows.isEmpty()){c.check();return Optional.empty();}
        var intent=verify(c,one(rows));var attempts=attempts(c,intent);
        if(attempts.isEmpty()){if(!receipts(c).isEmpty())bad();c.check();return Optional.empty();}
        one(attempts);c.check();return Optional.of(intent);
    }
    public Intent recover(TaskRunLaunchRecoveryAuthority.Verified p)throws Exception {
        var c=context(p);var intent=verify(c,one(rows(c)));one(attempts(c,intent));c.check();return intent;
    }
    /** A fresh current-read authorization committed immediately before query-only transport. */
    public RecoveryPermit authorizeRecovery(TaskRunLaunchRecoveryAuthority.Verified proof)throws Exception {
        return new RecoveryPermit(recover(proof));
    }
    /** Only actual WorkflowDelivery evidence can mint Received. It is never a caller-made receipt. */
    public Receipt record(TaskRunLaunchRecoveryAuthority.Verified p,TaskRunWorkflowDelivery.Received received)throws Exception {
        var c=context(p);var intent=verify(c,one(rows(c)));same(intent,received.intent());one(attempts(c,intent));
        var actual=received.acceptance();
        if(!intent.reference.workflowId().equals(actual.workflowId())||!intent.reference.inputSha256().equals(actual.inputSha256()))bad();
        var existing=receipts(c);
        if(!existing.isEmpty()){
            var result=verifyReceipt(c,intent,one(existing));
            if(!result.temporalRunId().equals(actual.temporalRunId()))bad();return result;
        }
        var now=now();var value=json.createObjectNode().put("schema_version","1.0.0");
        for(String field:IDENTITY)value.set(field,intent.content.required(field));
        for(String field:List.of("launch_id","workflow_id","reference_sha256"))value.set(field,intent.content.required(field));
        value.put("launch_intent_sha256",intent.digest).put("temporal_run_id",actual.temporalRunId())
            .put("input_sha256",actual.inputSha256()).put("accepted_at",now.toInstant(ZoneOffset.UTC).toString());
        schema.requireAcceptance(intent.content,value);var values=metadata(c,now);values.put("launch_id",intent.launchId());
        values.put("temporal_run_id",actual.temporalRunId());values.put("receipt_json",schema.canonical(value));values.put("receipt_sha256",schema.hash(value));
        insert("task_run_workflow_acceptance",values);c.check();return verifyReceipt(c,intent,one(receipts(c)));
    }
    public Optional<Receipt> read(TaskRunLaunchRecoveryAuthority.Verified p)throws Exception {
        var c=context(p);var intent=verify(c,one(rows(c)));one(attempts(c,intent));var values=receipts(c);
        return values.isEmpty()?Optional.empty():Optional.of(verifyReceipt(c,intent,one(values)));
    }
    /** Read-only continuation observation for an authorized question owner. The caller
     * has verified the independent confirmed-revision/run-creation link. No launch
     * permit, worker admission, or execution authority is returned. */
    QuestionLaunch questionLaunch(TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion q,
            JsonNode saved)throws Exception {
        q.requireUnchanged();var replay=saved.required("replay_manifest");
        var command=saved.required("command").required("command");
        for(var pin:Map.of("tenant_id",q.tenantId(),"project_id",q.projectId(),"task_id",q.taskId()).entrySet())
            if(!pin.getValue().equals(text(replay,pin.getKey())))bad();
        String target=text(replay,"run_id"),actor=text(replay,"initiator_id");
        if(target.equals(q.runId())||!actor.equals(text(saved,"created_by")))bad();
        var c=new Context(q.tenantId(),q.projectId(),target,actor,q.classification(),command,replay,null,q::requireUnchanged);
        var found=rows(c);
        if(found.isEmpty()) {if(!receipts(c).isEmpty())bad();c.check();return new QuestionLaunch(null,null);}
        var intent=verify(c,one(found));var attempted=attempts(c,intent);var accepted=receipts(c);
        if(accepted.isEmpty()){c.check();return new QuestionLaunch(intent,null);}
        one(attempted);var receipt=verifyReceipt(c,intent,one(accepted));c.check();return new QuestionLaunch(intent,receipt);
    }
    record QuestionLaunch(Intent intent,Receipt acceptance) { }

    private Intent verify(Context c,Row r)throws Exception {
        c.check();var value=schema.read(r.content);var registration=registration(c);schema.requireLaunch(registration,value);
        if(!schema.hash(value).equals(r.hash)||!schema.hash(value.required("reference")).equals(r.referenceHash)
                ||!schema.canonical(value.required("reference")).equals(schema.canonical(schema.read(r.reference)))
                ||!r.launch.equals(text(value,"launch_id"))||!r.command.equals(c.commandId())
                ||!r.commandHash.equals(text(c.command,"command_sha256"))||!r.commandHash.equals(text(value,"command_sha256"))
                ||!r.workflow.equals(text(value,"workflow_id"))||!r.created.equals(time(value,"created_at")))bad();
        if(!text(c.replay.required("execution_policy").required("worker"),"service_uri").equals(text(value,"service_uri")))bad();
        var reference=json.treeToValue(value.required("reference"),Reference.class);c.check();return new Intent(value,r.hash,reference,r.created);
    }
    private JsonNode registration(Context c)throws Exception {
        var row=one(db.query("""
            SELECT receipt_json,receipt_sha256,artifact_id,object_sha256,object_version,created_at FROM task_run_replay_registration
            WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND classification=? AND schema_version='1.0.0'
              AND schema_manifest_sha256=? LIMIT 2 FOR UPDATE
            """,(r,n)->new RegistrationRow(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getTimestamp(6).toLocalDateTime()),
            c.tenant,c.project,c.run,c.actor,c.classification,schema.manifest()));
        var value=schema.validate("ReplayRegistrationReceipt",schema.read(row.json));
        if(c.authorityHash!=null&&!c.authorityHash.equals(text(value,"authority_sha256")))bad();
        if(!schema.hash(value).equals(row.hash)||!row.artifact.equals(text(value,"artifact_id"))
                ||!row.objectHash.equals(text(value.required("object_ref"),"sha256"))||!row.version.equals(text(value.required("object_ref"),"object_version"))
                ||!row.created.equals(time(value,"registered_at"))||!c.classification.equals(text(value,"classification")))bad();
        for(String field:IDENTITY)if(!text(value,field).equals(field.equals("command_id")?c.commandId():text(c.replay,field)))bad();
        if(!text(c.command,"command_sha256").equals(text(value,"command_sha256"))
                ||!text(c.command,"replay_sha256").equals(row.objectHash))bad();
        return value;
    }
    private Receipt verifyReceipt(Context c,Intent intent,ReceiptRow row)throws Exception {
        var value=schema.read(row.json);schema.requireAcceptance(intent.content,value);
        if(!schema.hash(value).equals(row.hash)||!row.run.equals(text(value,"temporal_run_id"))
                ||!row.launch.equals(intent.launchId())||!row.created.equals(time(value,"accepted_at")))bad();
        c.check();return new Receipt(row.run,value,row.hash);
    }
    private List<Row> rows(Context c){return db.query("""
        SELECT launch_id,command_id,command_sha256,workflow_id,reference_json,reference_sha256,intent_json,intent_sha256,created_at
        FROM task_run_workflow_launch WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND classification=?
          AND schema_version='1.0.0' AND schema_manifest_sha256=? LIMIT 2 FOR UPDATE
        """,(r,n)->new Row(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getTimestamp(9).toLocalDateTime()),
        c.tenant,c.project,c.run,c.actor,c.classification,schema.manifest());}
    private List<LocalDateTime> attempts(Context c,Intent i)throws Exception {
        var result=db.query("""
            SELECT created_at FROM task_run_workflow_attempt WHERE tenant_id=? AND project_id=? AND run_id=? AND launch_id=?
              AND created_by=? AND classification=? AND schema_version='1.0.0' AND schema_manifest_sha256=? LIMIT 2 FOR UPDATE
            """,(r,n)->r.getTimestamp(1).toLocalDateTime(),c.tenant,c.project,c.run,i.launchId(),c.actor,c.classification,schema.manifest());
        for(var created:result)if(created.isBefore(i.created)||created.isAfter(now()))bad();return result;
    }
    private List<ReceiptRow> receipts(Context c){return db.query("""
        SELECT launch_id,temporal_run_id,receipt_json,receipt_sha256,created_at FROM task_run_workflow_acceptance
        WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND classification=?
          AND schema_version='1.0.0' AND schema_manifest_sha256=? LIMIT 2 FOR UPDATE
        """,(r,n)->new ReceiptRow(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getTimestamp(5).toLocalDateTime()),
        c.tenant,c.project,c.run,c.actor,c.classification,schema.manifest());}
    private void requireConfiguration(Intent intent,TaskRunWorkflowConfiguration config)throws Exception {
        if(!schema.hash(config).equals(text(intent.content,"operational_sha256"))||!config.revision().equals(text(intent.content,"operational_revision"))
                ||!config.namespace().equals(intent.namespace())||!config.serviceUri().equals(text(intent.content,"service_uri")))bad();
        var ref=intent.reference;
        if(!config.controlTaskQueue().equals(ref.controlTaskQueue())||!config.professionalTaskQueue().equals(ref.professionalTaskQueue())
                ||config.maximumCommands()!=ref.maximumCommands()||config.maximumSteps()!=ref.maximumSteps()||config.maximumParallelSteps()!=ref.maximumParallelSteps()
                ||config.readTimeoutMillis()!=ref.readTimeoutMillis()||config.stepTimeoutMillis()!=ref.stepTimeoutMillis()||config.scheduleTimeoutMillis()!=ref.scheduleTimeoutMillis()
                ||Math.addExact(intent.created.toInstant(ZoneOffset.UTC).toEpochMilli(),config.runTimeoutMillis())!=ref.deadlineEpochMillis())bad();
    }
    private static Context context(TaskRunBootstrapAuthority.Verified p)throws Exception {p.requireUnchanged();return new Context(p.tenantId(),p.projectId(),p.runId(),p.actorId(),p.classification(),p.command().required("command"),p.replay().content(),Objects.requireNonNull(p.authorizationsSha256()),p::requireUnchanged);}
    private static Context context(TaskRunLaunchRecoveryAuthority.Verified p)throws Exception {p.requireUnchanged();return new Context(p.tenantId(),p.projectId(),p.runId(),p.actorId(),p.classification(),p.command().required("command"),p.replay().content(),null,p::requireUnchanged);}
    private record Context(String tenant,String project,String run,String actor,String classification,JsonNode command,JsonNode replay,String authorityHash,Check guard){String commandId(){return text(command,"command_id");}void check()throws Exception{if(!TransactionSynchronizationManager.isActualTransactionActive())bad();guard.run();}}
    @FunctionalInterface private interface Check{void run()throws Exception;}
    private Map<String,Object> metadata(Context c,LocalDateTime now){var m=new LinkedHashMap<String,Object>();m.put("tenant_id",c.tenant);m.put("project_id",c.project);m.put("run_id",c.run);m.put("created_by",c.actor);m.put("classification",c.classification);m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",schema.manifest());m.put("created_at",now);return m;}
    private void insert(String table,Map<String,Object> values){var columns=new ArrayList<>(values.keySet());if(db.update("INSERT INTO "+table+"("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(columns.size(),"?"))+")",columns.stream().map(values::get).toArray())!=1)bad();}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    private static void same(Intent a,Intent b){if(a==null||b==null||!a.digest.equals(b.digest)||!a.content.equals(b.content))bad();}
    private static String text(JsonNode n,String field){return n.required(field).asText();}
    private static LocalDateTime time(JsonNode n,String field){return LocalDateTime.ofInstant(java.time.Instant.parse(text(n,field)),ZoneOffset.UTC);}
    private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static final List<String> IDENTITY=List.of("tenant_id","project_id","run_id","principal_id","task_id","initiator_id","command_id");
    private record Row(String launch,String command,String commandHash,String workflow,String reference,String referenceHash,String content,String hash,LocalDateTime created){}
    private record RegistrationRow(String json,String hash,String artifact,String objectHash,String version,LocalDateTime created){}
    private record ReceiptRow(String launch,String run,String json,String hash,LocalDateTime created){}
    public static final class Intent {
        private final JsonNode content;private final String digest;private final Reference reference;private final LocalDateTime created;
        private Intent(JsonNode content,String digest,Reference reference,LocalDateTime created){this.content=content.deepCopy();this.digest=digest;this.reference=reference;this.created=created;}
        public Reference reference(){return reference;}public String commandId(){return text(content,"command_id");}public String launchId(){return text(content,"launch_id");}
        public String namespace(){return text(content,"temporal_namespace");}public JsonNode content(){return content.deepCopy();}
    }
    public static final class Attempt {
        private final Intent intent;private final boolean recoveryOnly;private final AtomicBoolean used=new AtomicBoolean();
        private Attempt(Intent intent,boolean recoveryOnly){this.intent=intent;this.recoveryOnly=recoveryOnly;}
        public Intent intent(){return intent;}public boolean recoveryOnly(){return recoveryOnly;}
        boolean claim(){if(TransactionSynchronizationManager.isActualTransactionActive())bad();return !recoveryOnly&&used.compareAndSet(false,true);}
    }
    public static final class StartPermit {
        private final Attempt attempt;private boolean committed;private long committedNanos;private final Thread thread=Thread.currentThread();
        private StartPermit(Attempt attempt){
            this.attempt=attempt;
            if(!TransactionSynchronizationManager.isSynchronizationActive())bad();
            TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                public void afterCommit(){committedNanos=System.nanoTime();committed=true;}
            });
        }
        Attempt attempt(){if(!committed||thread!=Thread.currentThread()||System.nanoTime()-committedNanos>=java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(attempt.intent.reference.readTimeoutMillis()))bad();return attempt;}
    }
    public static final class RecoveryPermit {
        private final Intent intent;private boolean committed;private long committedNanos;private final Thread thread=Thread.currentThread();private final AtomicBoolean used=new AtomicBoolean();
        private RecoveryPermit(Intent intent){
            this.intent=intent;
            if(!TransactionSynchronizationManager.isSynchronizationActive())bad();
            TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                public void afterCommit(){committedNanos=System.nanoTime();committed=true;}
            });
        }
        Intent intent(){return intent;}
        Intent claim(){
            if(TransactionSynchronizationManager.isActualTransactionActive()||!committed||thread!=Thread.currentThread()
                    ||System.nanoTime()-committedNanos>=java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(intent.reference.readTimeoutMillis())||!used.compareAndSet(false,true))
                throw new IllegalStateException("CURRENT_COMMITTED_RECOVERY_AUTHORITY_REQUIRED");
            return intent;
        }
    }
    public static final class Receipt {
        private final String temporalRunId,sha256;private final JsonNode content;
        private Receipt(String run,JsonNode content,String sha256){this.temporalRunId=run;this.content=content.deepCopy();this.sha256=sha256;}
        public String temporalRunId(){return temporalRunId;}public String sha256(){return sha256;}public JsonNode content(){return content.deepCopy();}
    }
}
