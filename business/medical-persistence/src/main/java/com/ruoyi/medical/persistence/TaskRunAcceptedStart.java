package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** The only TASK QUEUED-to-RUNNING transition in the activity boundary. An actual,
 * original Temporal acceptance must already have been committed. This never starts
 * Temporal, creates a node, leases a step, issues a token, or calls a provider. */
public final class TaskRunAcceptedStart {
    static final String ACTION = "task.run.accepted-start";
    private static final Contract CONTRACT = contract();
    static final String MANIFEST = CONTRACT.hash();
    private static final BigInteger MAX_REVISION = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private final JdbcTemplate db;
    private final TaskRunLaunchRecoveryAuthority recoveries;
    private final TaskRunWorkflowLedger launches;
    private final TaskRunBootstrapSchema json;

    public TaskRunAcceptedStart(DataSource data, TaskRunLaunchRecoveryAuthority recoveries,
            TaskRunWorkflowLedger launches, TaskRunBootstrapSchema json) {
        this(new JdbcTemplate(data), recoveries, launches, json);
    }
    TaskRunAcceptedStart(JdbcTemplate db, TaskRunLaunchRecoveryAuthority recoveries,
            TaskRunWorkflowLedger launches, TaskRunBootstrapSchema json) {
        this.db = Objects.requireNonNull(db); db.setQueryTimeout(5);
        this.recoveries = Objects.requireNonNull(recoveries);
        this.launches = Objects.requireNonNull(launches); this.json = Objects.requireNonNull(json);
    }

    public JsonNode accept(TaskRunBootstrapAuthority.Verified authority,
            HttpServletRequest http, Reference reference) throws Exception {
        authority.requireUnchanged(); var a = authority.admission(); active(a);var control=a.controlProof();
        String tenant=authority.tenantId(), project=authority.projectId(), task=authority.taskId(), run=authority.runId(), actor=authority.actorId(), classification=authority.classification();
        var acceptance = acceptance(authority, http, reference);
        var before = run(authority); RunContinuationService.requireSuccessor(db,a,run); requireLive(before, "QUEUED");
        if (before.get("started_at") != null) bad();
        var existing = operations(authority, acceptance.id());
        if (!existing.isEmpty()) bad(); // A witness without its atomic state transition is corrupt.
        var now = one(db.query("SELECT UTC_TIMESTAMP(6)", (r,n) -> r.getTimestamp(1).toLocalDateTime()));
        BigInteger revision = number(before, "revision");
        if (revision.signum() < 1 || revision.compareTo(MAX_REVISION) >= 0 || now.isBefore(acceptance.at())) bad();
        var receipt = json.read("{}").deepCopy();
        var value = (com.fasterxml.jackson.databind.node.ObjectNode) receipt;
        value.put("schema_version", "1.0.0").put("kind", "TASK_RUN_ACCEPTED_START")
            .put("receipt_id", acceptance.id()).put("tenant_id", authority.tenantId())
            .put("project_id", authority.projectId()).put("task_id", authority.taskId())
            .put("run_id", authority.runId()).put("principal_id", authority.principalId())
            .put("initiator_id", authority.actorId()).put("command_id", acceptance.command())
            .put("launch_id", acceptance.launch()).put("temporal_run_id", acceptance.temporalRun())
            .put("workflow_acceptance_sha256", acceptance.hash()).put("reference_sha256", acceptance.referenceHash())
            .put("previous_revision", revision.toString()).put("running_revision", revision.add(BigInteger.ONE).toString())
            .put("started_at", now.toInstant(ZoneOffset.UTC).toString());
        validate(value);String canonical = json.canonical(value);
        authority.requireUnchanged();
        changed(db.update("""
            UPDATE task_run SET status='RUNNING',started_at=?,revision=revision+1,updated_at=?
            WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND created_by=?
              AND execution_kind='TASK' AND status='QUEUED' AND started_at IS NULL
              AND ended_at IS NULL AND dispatch_closed_at IS NULL
              AND superseded_by_run_id IS NULL AND workflow_id=? AND revision=?
            """, now, now, authority.tenantId(), authority.projectId(), authority.taskId(), authority.runId(),
            authority.actorId(), reference.workflowId(), revision));
        // No caller can mint this transition. It authorizes exactly this one state rebase,
        // on this callback/transaction/thread, while all other control pins stay identical.
        var transition = new Transition(control, tenant, project, run, actor);
        control.acceptedStart(transition);
        write(tenant, project, run, actor, classification, acceptance, now, canonical);
        return verify(a, tenant, project, task, run, actor, classification, acceptance,
            run(a, project, run), one(operations(a, project, run, acceptance.id())));
    }

    /** Original-only read after start. This cannot accept a replacement start or write a journal. */
    public JsonNode require(TaskNodeProductionAuthority.Verified authority,
            HttpServletRequest http, Reference reference) throws Exception {
        authority.requireUnchanged(); var a = authority.admission(); active(a);
        var acceptance = acceptance(authority, http, reference);
        var result = verify(a, authority.tenantId(), authority.projectId(), authority.taskId(), authority.runId(), authority.actorId(),
            authority.classification(), acceptance, run(authority), one(operations(authority, acceptance.id())));
        authority.requireUnchanged(); return result;
    }

    /** Integrity-only observation under a current human question proof. A queued
     * creation/launch intent is never presented as a started replacement run. */
    JsonNode questionStart(TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion q,
            TaskRunWorkflowLedger.QuestionLaunch observed,JsonNode saved)throws Exception {
        q.requireUnchanged();var replay=saved.required("replay_manifest");
        String target=replay.required("run_id").textValue(),actor=replay.required("initiator_id").textValue();
        same(q.tenantId(),replay.required("tenant_id").textValue());same(q.projectId(),replay.required("project_id").textValue());
        same(q.taskId(),replay.required("task_id").textValue());if(target.equals(q.runId()))bad();
        var row=one(db.queryForList("SELECT * FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' LIMIT 2 FOR UPDATE",q.tenantId(),q.projectId(),target,actor));
        if(observed.acceptance()==null){if(row.get("started_at")!=null)bad();q.requireUnchanged();return null;}
        var intent=observed.intent();var receipt=observed.acceptance();json.requireAcceptance(intent.content(),receipt.content());
        var accepted=new Acceptance(derived(intent.launchId(),"accepted-start"),intent.launchId(),intent.commandId(),receipt.temporalRunId(),
            receipt.sha256(),json.hash(intent.content().required("reference")),
            LocalDateTime.ofInstant(java.time.Instant.parse(receipt.content().required("accepted_at").textValue()),ZoneOffset.UTC));
        var found=db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? AND subject_id=? AND target_id=? LIMIT 2 FOR UPDATE",q.tenantId(),accepted.id(),actor,target);
        if(found.isEmpty()){if(row.get("started_at")!=null)bad();q.requireUnchanged();return null;}
        var result=verifyJournal(q.tenantId(),q.projectId(),q.taskId(),target,actor,q.classification(),accepted,row,one(found));
        q.requireUnchanged();return result;
    }

    private Acceptance acceptance(TaskNodeProductionBasis authority, HttpServletRequest http, Reference reference) throws Exception {
        active(authority.admission()); Objects.requireNonNull(reference);
        same(authority.tenantId(), reference.tenantId()); same(authority.projectId(), reference.projectId());
        same(authority.runId(), reference.runId()); same("TASK", reference.executionKind().name());
        String command = authority.command().required("command").required("command_id").asText();
        var proof = recoveries.require(authority.admission(), http, authority.runId(), command);
        var intent = launches.recover(proof);
        same(reference, intent.reference()); same(command, intent.commandId());
        var receipt = launches.read(proof).orElseThrow(() -> new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
        json.requireAcceptance(intent.content(), receipt.content()); same(json.hash(receipt.content()), receipt.sha256());
        same(receipt.temporalRunId(), receipt.content().required("temporal_run_id").asText());
        proof.requireUnchanged(); authority.requireUnchanged();
        return new Acceptance(derived(intent.launchId(), "accepted-start"), intent.launchId(), command, receipt.temporalRunId(),
            receipt.sha256(), json.hash(intent.content().required("reference")),
            LocalDateTime.ofInstant(java.time.Instant.parse(receipt.content().required("accepted_at").asText()), ZoneOffset.UTC));
    }
    private record Acceptance(String id, String launch, String command, String temporalRun, String hash, String referenceHash, LocalDateTime at) { }

    private JsonNode verify(EnterpriseAccess.Admission a, String tenant, String project, String task, String run, String actor,
            String classification, Acceptance accepted, Map<String,Object> row, Map<String,Object> op) throws Exception {
        active(a);RunContinuationService.requireSuccessor(db,a,run);requireLive(row, "RUNNING");
        return verifyJournal(tenant,project,task,run,actor,classification,accepted,row,op);
    }
    private JsonNode verifyJournal(String tenant,String project,String task,String run,String actor,
            String classification,Acceptance accepted,Map<String,Object> row,Map<String,Object> op)throws Exception {
        var value = json.read(text(op, "response_ref"));validate(value);
        var expectedFields = Set.of("schema_version","kind","receipt_id","tenant_id","project_id","task_id","run_id","principal_id","initiator_id","command_id","launch_id","temporal_run_id","workflow_acceptance_sha256","reference_sha256","previous_revision","running_revision","started_at");
        var actualFields = new HashSet<String>(); value.fieldNames().forEachRemaining(actualFields::add);
        if (!expectedFields.equals(actualFields)) bad();
        for (var pin : Map.ofEntries(Map.entry("schema_version","1.0.0"), Map.entry("kind","TASK_RUN_ACCEPTED_START"),
                Map.entry("receipt_id",accepted.id()),Map.entry("tenant_id",tenant),Map.entry("project_id",project),Map.entry("task_id",task),
                Map.entry("run_id",run),Map.entry("initiator_id",actor),Map.entry("command_id",accepted.command()),
                Map.entry("launch_id",accepted.launch()),Map.entry("temporal_run_id",accepted.temporalRun()),
                Map.entry("workflow_acceptance_sha256",accepted.hash()),Map.entry("reference_sha256",accepted.referenceHash())).entrySet())
            same(pin.getValue(), value.required(pin.getKey()).textValue());
        var principal = one(db.query("SELECT principal_id FROM run_principal WHERE tenant_id=? AND project_id=? AND run_id=? AND initiator_id=? LIMIT 2 FOR UPDATE",(r,n)->r.getString(1),tenant,project,run,actor));
        same(principal, value.required("principal_id").textValue());
        BigInteger prior = new BigInteger(value.required("previous_revision").textValue());
        BigInteger running = new BigInteger(value.required("running_revision").textValue());
        if (prior.signum() < 1 || !prior.add(BigInteger.ONE).equals(running) || running.compareTo(MAX_REVISION)>0 || number(row,"revision").compareTo(running)<0) bad();
        var at = LocalDateTime.ofInstant(java.time.Instant.parse(value.required("started_at").textValue()),ZoneOffset.UTC);
        same(at, time(row,"started_at")); if (at.isBefore(accepted.at())) bad();
        String canonical = json.canonical(value);
        for (var pin : Map.ofEntries(Map.entry("tenant_id",tenant),Map.entry("subject_id",actor),Map.entry("operation_id",accepted.id()),
                Map.entry("operation_name",ACTION),Map.entry("idempotency_key","task-accepted-start:"+run),Map.entry("request_sha256",accepted.hash()),
                Map.entry("state","COMPLETED"),Map.entry("response_ref",canonical),Map.entry("target_id",run),Map.entry("schema_version","1.0.0"),
                Map.entry("schema_manifest_sha256",MANIFEST),Map.entry("created_by",actor),Map.entry("classification",classification)).entrySet()) same(pin.getValue(),op.get(pin.getKey()));
        same(BigInteger.valueOf(200), number(op,"http_status"));same(BigInteger.ONE,number(op,"revision"));same(at,time(op,"created_at"));same(at,time(op,"updated_at"));
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",tenant,derived(accepted.id(),"audit")));
        for(var pin:Map.ofEntries(Map.entry("tenant_id",tenant),Map.entry("project_id",project),Map.entry("subject_id",actor),Map.entry("action",ACTION),
                Map.entry("resource_ref",canonical),Map.entry("result","SUCCESS"),Map.entry("request_id",accepted.command()),Map.entry("trace_id",run),
                Map.entry("schema_version","1.0.0"),Map.entry("schema_manifest_sha256",MANIFEST),Map.entry("created_by",actor),Map.entry("classification",classification)).entrySet()) same(pin.getValue(),audit.get(pin.getKey()));
        if(audit.get("reason_code")!=null)bad();same(at,time(audit,"created_at"));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",tenant,derived(accepted.id(),"event")));
        same(run,event.get("aggregate_id"));same(running,number(event,"aggregate_revision"));same(ACTION,event.get("event_type"));same(canonical,event.get("payload"));same(at,time(event,"occurred_at"));
        // Re-read the complete original journal on every retry; the hash is not a capability.
        return value.deepCopy();
    }
    private void write(String tenant,String project,String run,String actor,String classification,Acceptance accepted,LocalDateTime now,String canonical){
        changed(db.update("INSERT INTO operation_record(tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,'COMPLETED',200,?,?,'1.0.0',?,?,?,?,1,?)",tenant,accepted.id(),actor,ACTION,"task-accepted-start:"+run,accepted.hash(),canonical,run,MANIFEST,actor,now,classification,now));
        changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',NULL,?,?,'1.0.0',?,?,?,?)",tenant,project,derived(accepted.id(),"audit"),actor,ACTION,canonical,accepted.command(),run,MANIFEST,actor,now,classification));
        try {var value=json.read(canonical);changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,?,?,?,?)",tenant,derived(accepted.id(),"event"),run,new BigInteger(value.required("running_revision").asText()),ACTION,canonical,now));}catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private Map<String,Object> run(TaskNodeProductionBasis p){return run(p.admission(),p.projectId(),p.runId());}
    private Map<String,Object> run(EnterpriseAccess.Admission a,String project,String run){return one(db.queryForList("SELECT * FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' LIMIT 2 FOR UPDATE",a.context().tenant_id(),project,run,a.actorId()));}
    private List<Map<String,Object>> operations(TaskNodeProductionBasis p,String receipt){return operations(p.admission(),p.projectId(),p.runId(),receipt);}
    private List<Map<String,Object>> operations(EnterpriseAccess.Admission a,String project,String run,String receipt){return db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? AND subject_id=? AND target_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),receipt,a.actorId(),run);}
    private static void requireLive(Map<String,Object> row,String status){same(status,row.get("status"));for(String key:List.of("ended_at","dispatch_closed_at","superseded_by_run_id"))if(row.get(key)!=null)bad();}
    private static void active(EnterpriseAccess.Admission a){if(!TransactionSynchronizationManager.isActualTransactionActive()||!a.controlWorker())deny();a.controlProof().requireActive();}
    static String derived(String id,String part){return UUID.nameUUIDFromBytes((id+"|"+part).getBytes(StandardCharsets.US_ASCII)).toString();}
    private static String text(Map<String,Object> row,String field){var value=row.get(field);if(!(value instanceof String))bad();return (String)value;}
    private static BigInteger number(Map<String,Object> row,String field){return new java.math.BigDecimal(String.valueOf(row.get(field))).toBigIntegerExact();}
    private static LocalDateTime time(Map<String,Object> row,String field){var v=row.get(field);if(v instanceof LocalDateTime t)return t;if(v instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}
    private static void changed(int count){if(count!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}

    private record Contract(com.networknt.schema.JsonSchema validator,String hash) { }
    private static Contract contract(){
        try(var in=Objects.requireNonNull(TaskRunAcceptedStart.class.getResourceAsStream("/task-run-accepted-start.schema.json"))){
            byte[] bytes=in.readAllBytes();var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);
            var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012)
                .getSchema(node,com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
            schema.initializeValidators();return new Contract(schema,org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes));
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    private static void validate(JsonNode value){if(!CONTRACT.validator().validate(value).isEmpty())bad();}

    /** Private minting and one-use callback capability, not a public status mutation API. */
    static final class Transition {
        private final FormalRunControlAccess.Proof control;
        private final String tenant,project,run,actor;
        private final Thread thread=Thread.currentThread();
        private final TenantScope scope=TenantScope.requireCurrent();
        private final Map<Object,Object> tx=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private boolean used;
        private Transition(FormalRunControlAccess.Proof control,String tenant,String project,String run,String actor){this.control=control;this.tenant=tenant;this.project=project;this.run=run;this.actor=actor;if(tx.isEmpty())deny();}
        void claim(FormalRunControlAccess.Proof proof,RunTokenAuthorityStore.Run before,RunTokenAuthorityStore.Run after){
            if(used||control!=proof||thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||!TransactionSynchronizationManager.isActualTransactionActive())deny();
            var current=TransactionSynchronizationManager.getResourceMap();if(current.size()!=tx.size()||tx.entrySet().stream().anyMatch(e->current.get(e.getKey())!=e.getValue()))deny();
            same(tenant,before.tenant());same(project,before.project());same(run,before.id());same(actor,before.actor());same("QUEUED",before.status());same("RUNNING",after.status());
            var expected=new RunTokenAuthorityStore.Run(before.tenant(),before.project(),before.id(),before.kind(),before.scope(),before.schema(),before.manifest(),before.principal(),before.actor(),before.capabilities(),before.principalStatus(),before.principalSchema(),before.principalManifest(),before.classification(),before.revision(),before.executionActor(),"RUNNING",before.ended(),before.closed(),before.superseded(),before.task());
            same(expected,after);used=true;
        }
    }
}
