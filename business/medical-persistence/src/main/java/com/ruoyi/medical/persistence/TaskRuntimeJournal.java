package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Genuine TASK producers for the existing 075 journal. These rows record already
 * registered originals, never create replay/input/envelope objects, lease a step, or
 * start execution. Recovery is read-only and cannot manufacture a missing original. */
public final class TaskRuntimeJournal {
    private static final String RUN_ACTION="task.run.runtime.prepared", STEP_ACTION="task.step.runtime.dispatched";
    private final JdbcTemplate db;
    private final PermissionSchema json;
    private final TaskRunGraphService graphs;
    private final TaskRunBootstrapSchema bootstrap;
    private final RuntimeReplaySchema replays;
    private final JsonSchema schema;
    private final String manifest;
    private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public TaskRuntimeJournal(DataSource data,PermissionSchema json,TaskRunGraphService graphs)throws Exception {
        this(new JdbcTemplate(data),json,graphs);
    }
    TaskRuntimeJournal(JdbcTemplate db,PermissionSchema json,TaskRunGraphService graphs)throws Exception {
        this.db=Objects.requireNonNull(db);db.setQueryTimeout(5);this.json=Objects.requireNonNull(json);
        this.graphs=Objects.requireNonNull(graphs);bootstrap=new TaskRunBootstrapSchema();replays=new RuntimeReplaySchema(json);
        byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-runtime-journal.schema.json"))){bytes=in.readAllBytes();}
        manifest=DigestUtils.sha256Hex(bytes);
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        definition("RunIntent").initializeValidators();definition("StepDispatch").initializeValidators();
    }

    public RunIntent prepareRun(TaskNodeProductionBasis authority,TaskRunGraphService.Verified graph)throws Exception {
        return run(authority,graph,true);
    }
    public RunIntent requireRun(TaskNodeProductionBasis authority,TaskRunGraphService.Verified graph)throws Exception {
        return run(authority,graph,false);
    }
    private RunIntent run(TaskNodeProductionBasis authority,TaskRunGraphService.Verified graph,boolean create)throws Exception {
        Objects.requireNonNull(authority).requireUnchanged();var a=authority.admission();current(a,authority.runId());
        var source=runSource(a,graph);
        same(authority.tenantId(),text(source,"tenant_id"));same(authority.projectId(),text(source,"project_id"));
        same(authority.runId(),text(source,"run_id"));same(authority.taskId(),text(source,"task_id"));
        same(authority.principalId(),text(source,"principal_id"));same(authority.actorId(),text(source,"initiator_id"));
        same(authority.classification(),text(source,"classification"));same(authority.replay().rawSha256(),text(source,"replay_sha256"));
        var command=authority.command().required("command");same(command.required("command_id"),source.required("command_id"));same(command.required("command_sha256"),source.required("command_sha256"));
        var rows=runRows(source);
        if(rows.isEmpty()){
            if(!create)bad();var value=source.deepCopy();value.put("kind","TASK_RUN_RUNTIME_INTENT").put("intent_id",id());stamp(value);
            validate("RunIntent",value);writeRun(value);
        }else if(rows.size()!=1)bad();
        var value=verifyRun(source,one(runRows(source)));authority.requireUnchanged();graph.requireUnchanged();
        return new RunIntent(a,authority,graph,value);
    }

    /** The command is the scheduler's original dispatch command, not a generated surrogate. */
    public Dispatch prepareStep(TaskProfessionalRuntimeService.Envelope envelope,TaskProfessionalObjectRegistration.Registered registered,String commandId)throws Exception {
        WireValues.uuid(commandId);return step(envelope,registered,commandId,true);
    }
    public Dispatch requireStep(TaskProfessionalRuntimeService.Envelope envelope,TaskProfessionalObjectRegistration.Registered registered,String commandId)throws Exception {
        WireValues.uuid(commandId);return step(envelope,registered,commandId,false);
    }
    /** Host recovery must recover the already-persisted scheduler command; absence is fatal. */
    public Dispatch requireStep(TaskProfessionalRuntimeService.Envelope envelope,TaskProfessionalObjectRegistration.Registered registered)throws Exception {
        return step(envelope,registered,null,false);
    }
    private Dispatch step(TaskProfessionalRuntimeService.Envelope envelope,TaskProfessionalObjectRegistration.Registered registered,String commandId,boolean create)throws Exception {
        var source=stepSource(envelope,registered);var rows=stepRows(source);
        if(rows.isEmpty()){
            if(!create||commandId==null)bad();
            // A command cannot be reused for another step or generation, even when the
            // caller presents an otherwise valid new lease and registered envelope.
            if(!db.queryForList("SELECT command_id FROM step_runtime_attempt WHERE tenant_id=? AND project_id=? AND run_id=? AND command_id=? LIMIT 2 FOR UPDATE",text(source,"tenant_id"),text(source,"project_id"),text(source,"run_id"),commandId).isEmpty())conflict();
            var value=source.deepCopy();value.put("kind","TASK_STEP_RUNTIME_ATTEMPT").put("command_id",commandId);stamp(value);
            validate("StepDispatch",value);writeStep(value);
        }else if(rows.size()!=1)bad();
        var value=verifyStep(source,one(stepRows(source)));if(commandId!=null&&!commandId.equals(text(value,"command_id")))conflict();
        envelope.requireProductionReady();registered.requireUnchanged();
        return new Dispatch(envelope.admission(),envelope,registered,value);
    }

    /** Read-only worker bootstrap under current ROLE/078/093/097 authority. This
     * never creates a lease, action, attempt, session or content capability. */
    WorkflowProtocol.Dispatch requireExistingStep(FormalTaskNodeAuthority.Verified node,ProfessionalRuntimeObservations.Binding b)throws Exception {
        Objects.requireNonNull(node).requireUnchanged();var a=node.controlAdmission();var input=node.currentInput();input.requireUnchanged();
        current(a,b.run_id());same(b.tenant_id(),input.tenantId());same(b.run_id(),input.runId());same(b.step_id(),input.stepId());same(b.input_sha256(),input.inputSha256());
        same(TaskHostControlBootstrap.actionId(b.tenant_id(),b.run_id(),b.step_id(),java.math.BigInteger.valueOf(b.lease_generation())),b.action_id());
        var graph=graphs.require(a,b.run_id());var origin=runSource(a,graph);var runIntent=verifyRun(origin,one(runRows(origin)));
        same(graph.graphId(),input.graphId());same(graph.graphSha256(),input.graphSha256());
        var source=json.object().put("schema_version","1.0.0");for(String f:List.of("tenant_id","project_id","task_id","run_id","principal_id","initiator_id","graph_id","graph_sha256","classification"))source.set(f,origin.required(f));
        source.put("step_id",input.stepId()).put("lease_generation",b.lease_generation()).put("action_id",b.action_id()).put("input_artifact_id",input.inputId()).put("input_sha256",input.inputSha256())
            .put("input_object_version",input.objectReference().objectVersion()).put("input_receipt_sha256",input.receiptSha256()).put("run_intent_id",text(runIntent,"intent_id")).put("run_intent_sha256",hash(runIntent));
        var original=one(stepRows(source));var lease=one(db.queryForList("SELECT * FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),input.stepId()));
        pins(lease,Map.of("status","RUNNING","input_sha256",input.inputSha256()));same(Long.toString(b.lease_generation()),s(lease,"lease_generation"));if(!now().isBefore(time(lease,"lease_expires_at")))deny();
        var action=one(db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),input.stepId(),b.action_id()));
        String arguments=TaskProfessionalRuntimeService.hostArguments(input);pins(action,Map.of("kind","HOST_CONTROL","state","BOUND","input_sha256",input.inputSha256(),"arguments_sha256",arguments,"created_by",a.actorId()));same(Long.toString(b.lease_generation()),s(action,"lease_generation"));if(action.get("invocation_id")!=null)deny();
        source.put("worker_service_uri",s(lease,"lease_owner")).put("host_arguments_sha256",arguments).put("host_tool_version",s(action,"tool_version"));
        String artifact=s(original,"envelope_artifact_id");var registration=one(db.queryForList("SELECT * FROM runtime_registered_object WHERE tenant_id=? AND project_id=? AND run_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),artifact));
        var receipt=read(s(registration,"receipt_json"));same(hash(receipt),s(registration,"receipt_sha256"));same(b.step_envelope_sha256(),s(registration,"object_sha256"));
        for(var pin:Map.ofEntries(Map.entry("schema_version","2.0.0"),Map.entry("kind","STEP_ENVELOPE"),Map.entry("schema_ref",TaskProfessionalRuntimeService.ENVELOPE_REF),Map.entry("tenant_id",input.tenantId()),Map.entry("project_id",input.projectId()),Map.entry("task_id",input.taskId()),Map.entry("run_id",input.runId()),Map.entry("step_id",input.stepId()),Map.entry("action_id",b.action_id()),Map.entry("lease_generation",Long.toString(b.lease_generation())),Map.entry("worker_service_uri",s(lease,"lease_owner")),Map.entry("input_artifact_id",input.inputId()),Map.entry("input_sha256",input.inputSha256()),Map.entry("input_receipt_sha256",input.receiptSha256()),Map.entry("principal_id",text(origin,"principal_id")),Map.entry("created_by",a.actorId()),Map.entry("classification",text(origin,"classification")),Map.entry("artifact_id",artifact),Map.entry("write_id",s(registration,"write_id"))).entrySet())same(pin.getValue(),text(receipt,pin.getKey()));
        var object=receipt.required("object_ref");same(b.step_envelope_sha256(),text(object,"sha256"));same(s(registration,"object_version"),text(object,"object_version"));
        var objectIntent=one(db.queryForList("SELECT * FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),s(registration,"write_id")));
        pins(objectIntent,Map.of("schema_version","2.0.0","write_kind","STEP_ENVELOPE","schema_ref",TaskProfessionalRuntimeService.ENVELOPE_REF,"step_id",input.stepId(),"planned_artifact_id",artifact,"object_sha256",b.step_envelope_sha256(),"created_by",a.actorId(),"classification",text(origin,"classification")));
        var payload=read(s(objectIntent,"canonical_payload"));same(b.step_envelope_sha256(),hash(payload));
        for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id","action_id","session_id","input_artifact_id","input_sha256","input_receipt_sha256","worker_service_uri"))same(receipt.required(f),payload.required(f));same(b.lease_generation(),payload.required("lease_generation").longValue());
        if(!payload.required("compiled_prompt").isTextual()||payload.required("compiled_prompt").textValue().isBlank()||!payload.required("prompt_provenance").isObject())bad();WireValues.hash(text(payload,"prompt_sha256"));
        var ar=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND project_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),artifact));pins(ar,Map.of("run_id",input.runId(),"artifact_kind","STEP_ENVELOPE","content_sha256",b.step_envelope_sha256(),"created_by",a.actorId(),"classification",text(origin,"classification")));
        for(String f:List.of("object_key","object_version","sha256","size_bytes","media_type"))same(text(object,f),s(ar,f));StoredObjectReferences.decode(s(ar,"object_ref"),new ObjectRef(text(object,"object_key"),text(object,"object_version"),text(object,"sha256"),text(object,"size_bytes"),text(object,"media_type")));
        pins(registration,Map.of("schema_version","1.0.0","created_by",a.actorId(),"classification",text(origin,"classification")));same(s(objectIntent,"schema_manifest_sha256"),s(registration,"schema_manifest_sha256"));
        sourceJournal(registration,"runtime.professional.registered",s(registration,"write_id"),artifact,s(registration,"write_id"),receipt,s(registration,"schema_manifest_sha256"));
        source.put("session_id",text(receipt,"session_id")).put("envelope_artifact_id",artifact).put("envelope_sha256",b.step_envelope_sha256()).put("envelope_object_version",text(object,"object_version")).put("envelope_receipt_sha256",hash(receipt));
        var verified=verifyStep(source,original);node.requireUnchanged();input.requireUnchanged();return dispatchValue(verified);
    }

    /** Existing immutable dispatch for an actual admitted host session. No lease/action is created. */
    WorkflowProtocol.Dispatch requireSessionStep(TaskProfessionalRuntimeService.Session session)throws Exception {
        session.requireUnchanged();var a=session.admission();var input=session.input();var b=ProfessionalRuntimeObservations.jsonBinding(session.binding());
        var source=json.object().put("schema_version","1.0.0").put("tenant_id",input.tenantId()).put("project_id",input.projectId()).put("task_id",input.taskId())
            .put("run_id",input.runId()).put("step_id",input.stepId()).put("action_id",session.actionId()).put("lease_generation",b.lease_generation())
            .put("initiator_id",a.actorId()).put("session_id",session.sessionId()).put("input_artifact_id",input.inputId()).put("input_sha256",input.inputSha256())
            .put("input_receipt_sha256",input.receiptSha256()).put("envelope_sha256",b.step_envelope_sha256()).put("worker_service_uri",session.workerService());
        var value=verifyStep(source,one(stepRows(source)));session.requireUnchanged();return dispatchValue(value);
    }

    /** Factual recovery only. A current exact-run read proof is not execution
     * authority, and this method deliberately does not consult live leases/nodes. */
    WorkflowProtocol.Dispatch requireHistoricalStep(EnterpriseAccess.Admission recovery,WorkflowProtocol.Dispatch expected)throws Exception {
        Objects.requireNonNull(expected);var original=readHistoricalStep(recovery,expected.runId(),expected.stepId(),expected.leaseGeneration());
        same(original,expected);return original;
    }
    /** Locator fields select an immutable original; none become execution grants. */
    WorkflowProtocol.Dispatch readHistoricalStep(EnterpriseAccess.Admission recovery,String run,String step,long generation)throws Exception {
        Objects.requireNonNull(recovery);if(!TransactionSynchronizationManager.isActualTransactionActive()||!recovery.recoveryWorker()||recovery.simulationWorker()||recovery.formalWorker()||recovery.controlWorker())deny();
        var proof=recovery.recoveryProof();proof.requireActive();var identity=proof.identity();var scope=TenantScope.requireCurrent();
        same("FORMAL_RUN_LAUNCH_RECOVERY",identity.kind());same(scope.tenantId(),identity.tenantId());same(scope.subjectId(),identity.actorId());
        same(identity.tenantId(),recovery.context().tenant_id());same(identity.actorId(),recovery.actorId());same(identity.runId(),run);
        proof.requireRun(run,identity.commandId());proof.require(new PermissionProtocol.Scope(identity.tenantId(),identity.projectId(),"TASK",identity.taskId()),"run.read","RUN_READING");
        WireValues.uuid(step);if(generation<=0)deny();
        var source=json.object().put("schema_version","1.0.0").put("tenant_id",identity.tenantId()).put("project_id",identity.projectId()).put("task_id",identity.taskId())
            .put("run_id",run).put("principal_id",identity.principalId()).put("initiator_id",identity.actorId()).put("command_id",identity.commandId())
            .put("command_sha256",identity.commandSha256()).put("replay_sha256",identity.replaySha256());
        var runIntent=verifyRun(source,one(runRows(source)));
        proof.requireOriginal(run,text(runIntent,"command_id"),text(runIntent,"replay_sha256"),text(runIntent,"command_sha256"));
        // Source linkage comes from the independently verified run original; the
        // complete dispatch schema/hash/row/audit/outbox are shared with live use.
        source.remove(List.of("command_id","command_sha256","replay_sha256"));
        for(String f:List.of("graph_id","graph_sha256","classification"))source.set(f,runIntent.required(f));
        source.put("step_id",step).put("lease_generation",generation).put("run_intent_id",text(runIntent,"intent_id")).put("run_intent_sha256",hash(runIntent)).put("worker_service_uri",identity.serviceUri());
        var original=verifyStep(source,one(stepRows(source)));proof.requireActive();
        return dispatchValue(original);
    }

    /** Lifecycle facts only. Its private operation proof is independent of leases
     * and cannot be used for host/provider admission or materialization. */
    WorkflowProtocol.Dispatch readLifecycleStep(TaskRunLifecycleAuthority.Proof p,String step,long generation)throws Exception {
        p.require(p.operation());WireValues.uuid(step);if(generation<1)deny();var launch=p.originalLaunch();var replay=launch.replay();
        var source=json.object().put("schema_version","1.0.0").put("tenant_id",launch.tenantId()).put("project_id",launch.projectId()).put("task_id",launch.taskId())
            .put("run_id",launch.runId()).put("principal_id",launch.principalId()).put("initiator_id",launch.actorId()).put("command_id",launch.commandId())
            .put("command_sha256",text(launch.command().required("command"),"command_sha256")).put("replay_sha256",replay.rawSha256());
        var runIntent=verifyRun(source,one(runRows(source)));source.remove(List.of("command_id","command_sha256","replay_sha256"));
        for(String f:List.of("graph_id","graph_sha256","classification"))source.set(f,runIntent.required(f));
        source.put("step_id",step).put("lease_generation",generation).put("run_intent_id",text(runIntent,"intent_id")).put("run_intent_sha256",hash(runIntent))
            .put("worker_service_uri",text(replay.content().required("execution_policy").required("worker"),"service_uri"));
        var original=verifyStep(source,one(stepRows(source)));p.require(p.operation());return dispatchValue(original);
    }

    /** Human answer linkage uses its genuine owner proof and original writer ID;
     * no Admission is manufactured and no execution permission is transferred. */
    WorkflowProtocol.Dispatch readQuestionStep(TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion q,String step,long generation)throws Exception{
        q.requireUnchanged();WireValues.uuid(step);if(generation<1)deny();same(q.tenantId(),q.admission().context().tenant_id());
        var row=one(db.queryForList("SELECT r.replay_manifest,o.payload FROM task_run r JOIN outbox_event o ON o.tenant_id=r.tenant_id AND o.aggregate_id=r.run_id AND o.event_type='run.start.requested' WHERE r.tenant_id=? AND r.project_id=? AND r.run_id=? AND r.created_by=? AND r.execution_kind='TASK' LIMIT 2 FOR UPDATE",q.tenantId(),q.projectId(),q.runId(),q.originalActorId()));
        var body=read(s(row,"payload"));var command=body.required("command");var unhashed=body.deepCopy();((ObjectNode)unhashed.required("command")).remove("command_sha256");same(hash(unhashed),text(command,"command_sha256"));same(q.originalActorId(),text(body.required("decision"),"actor_id"));same("START_RUN",text(command,"kind"));
        var replay=read(s(row,"replay_manifest"));var parsed=replays.read(canonical(replay).getBytes(StandardCharsets.UTF_8),text(command,"replay_sha256"),text(command,"replay_schema_sha256"));same(RuntimeReplaySchema.REF,text(command,"replay_schema_ref"));same(q.tenantId(),text(replay,"tenant_id"));same(q.projectId(),text(replay,"project_id"));same(q.runId(),text(replay,"run_id"));same(q.originalActorId(),text(replay,"initiator_id"));
        var source=json.object().put("schema_version","1.0.0");for(String f:List.of("tenant_id","project_id","task_id","run_id","principal_id","initiator_id"))source.set(f,replay.required(f));source.put("command_id",text(command,"command_id")).put("command_sha256",text(command,"command_sha256")).put("replay_sha256",parsed.rawSha256());
        var runIntent=verifyRun(source,one(runRows(source)));source.remove(List.of("command_id","command_sha256","replay_sha256"));for(String f:List.of("graph_id","graph_sha256","classification"))source.set(f,runIntent.required(f));same(q.classification(),text(runIntent,"classification"));
        source.put("step_id",step).put("lease_generation",generation).put("run_intent_id",text(runIntent,"intent_id")).put("run_intent_sha256",hash(runIntent)).put("worker_service_uri",text(replay.required("execution_policy").required("worker"),"service_uri"));
        var original=verifyStep(source,one(stepRows(source)));q.requireUnchanged();return dispatchValue(original);
    }

    /** Read the registered 092 original, its actual artifact version and journals.
     * Both queued bootstrap and running production use the same immutable identity. */
    private ObjectNode runSource(EnterpriseAccess.Admission a,TaskRunGraphService.Verified graph)throws Exception {
        Objects.requireNonNull(graph).requireUnchanged();current(a,graph.runId());
        same(a.context().tenant_id(),graph.tenantId());
        var run=one(db.queryForList("SELECT * FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? LIMIT 2 FOR UPDATE",graph.tenantId(),graph.projectId(),graph.runId()));
        RunContinuationService.requireSuccessor(db,a,graph.runId());
        same("TASK",s(run,"execution_kind"));same(a.actorId(),s(run,"created_by"));same(graph.taskId(),s(run,"task_id"));
        if(!Set.of("QUEUED","RUNNING").contains(s(run,"status"))||run.get("ended_at")!=null||run.get("dispatch_closed_at")!=null||run.get("superseded_by_run_id")!=null||a.formalWorker()&&!"RUNNING".equals(s(run,"status")))deny();
        var replay=read(s(run,"replay_manifest"));same(graph.replaySha256(),hash(replay));
        var intentRow=one(db.queryForList("SELECT * FROM task_run_replay_intent WHERE tenant_id=? AND project_id=? AND run_id=? LIMIT 2 FOR UPDATE",graph.tenantId(),graph.projectId(),graph.runId()));
        var replayIntent=bootstrap.validate("ReplayIntent",read(s(intentRow,"intent_json")));
        var parsed=replays.read(canonical(replay).getBytes(StandardCharsets.UTF_8),graph.replaySha256(),text(replayIntent,"replay_schema_sha256"));
        bootstrap.requireReplayIntent(replayIntent,parsed);same(hash(replayIntent),s(intentRow,"intent_sha256"));
        var registration=one(db.queryForList("SELECT * FROM task_run_replay_registration WHERE tenant_id=? AND project_id=? AND run_id=? LIMIT 2 FOR UPDATE",graph.tenantId(),graph.projectId(),graph.runId()));
        var receipt=read(s(registration,"receipt_json"));bootstrap.requireRegistration(replayIntent,receipt);same(hash(receipt),s(registration,"receipt_sha256"));
        String actor=a.actorId(),classification=s(run,"classification"),write=text(replayIntent,"write_id");
        for(var row:List.of(intentRow,registration)){
            pins(row,Map.of("tenant_id",graph.tenantId(),"project_id",graph.projectId(),"run_id",graph.runId(),"write_id",write,"schema_version","1.0.0","schema_manifest_sha256",bootstrap.manifest(),"created_by",actor,"classification",classification));
        }
        same(actor,text(replayIntent,"initiator_id"));same(classification,text(receipt,"classification"));
        same(time(replayIntent,"created_at"),time(intentRow,"created_at"));same(time(receipt,"registered_at"),time(registration,"created_at"));
        if(time(intentRow,"created_at").isAfter(time(registration,"created_at"))||time(registration,"created_at").isAfter(now()))bad();
        for(String field:List.of("principal_id","task_id","command_id","command_sha256","outbox_event_id","write_key","planned_artifact_id","object_key","replay_sha256","replay_schema_sha256"))same(text(replayIntent,field),s(intentRow,field));
        same(replay,read(s(intentRow,"canonical_payload")));same(text(replayIntent,"size_bytes"),s(intentRow,"size_bytes"));
        var attempt=one(db.queryForList("SELECT * FROM task_run_replay_attempt WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? LIMIT 2 FOR UPDATE",graph.tenantId(),graph.projectId(),graph.runId(),write));
        for(String field:List.of("tenant_id","project_id","run_id","write_id","schema_version","schema_manifest_sha256","created_by","classification"))same(intentRow.get(field),attempt.get(field));
        if(time(attempt,"created_at").isBefore(time(intentRow,"created_at"))||time(attempt,"created_at").isAfter(time(registration,"created_at")))bad();
        var object=receipt.required("object_ref");String artifact=text(receipt,"artifact_id");
        pins(registration,Map.of("artifact_id",artifact,"object_sha256",graph.replaySha256(),"object_version",text(object,"object_version")));
        var ar=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND project_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",graph.tenantId(),graph.projectId(),artifact));
        pins(ar,Map.of("tenant_id",graph.tenantId(),"project_id",graph.projectId(),"run_id",graph.runId(),"artifact_id",artifact,"artifact_kind","REPLAY_MANIFEST","created_by",actor,"classification",classification,"schema_version","1.0.0","schema_manifest_sha256",text(replayIntent,"replay_schema_sha256")));
        for(String field:List.of("object_key","object_version","sha256","size_bytes","media_type"))same(text(object,field),s(ar,field));
        same(graph.replaySha256(),s(ar,"content_sha256"));same(time(registration,"created_at"),time(ar,"created_at"));
        StoredObjectReferences.decode(s(ar,"object_ref"),new ObjectRef(text(object,"object_key"),text(object,"object_version"),text(object,"sha256"),text(object,"size_bytes"),text(object,"media_type")));
        var version=one(db.queryForList("SELECT * FROM version_registry WHERE tenant_id=? AND version_id=? LIMIT 2 FOR UPDATE",graph.tenantId(),artifact));pins(version,Map.of("owner_project_id",graph.projectId(),"entity_kind","ARTIFACT","content_sha256",graph.replaySha256()));
        var binding=one(db.queryForList("SELECT * FROM artifact_content_binding WHERE tenant_id=? AND project_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",graph.tenantId(),graph.projectId(),artifact));pins(binding,Map.of("resource_kind","TASK","resource_id",graph.taskId(),"schema_ref",RuntimeReplaySchema.REF,"schema_document_sha256",text(replayIntent,"replay_schema_sha256"),"dependency_access_set_id",s(ar,"dependency_access_set_id")));
        // Registration and preparation journals are independent immutable witnesses.
        sourceJournal(registration,"task.run.replay.registered",write,artifact,write,receipt,text(replayIntent,"replay_schema_sha256"));
        var prepared=json.object().put("schema_version","1.0.0").put("kind","RUN_REPLAY_PREPARED").put("intent_sha256",hash(replayIntent));
        for(String f:List.of("tenant_id","project_id","run_id","principal_id","task_id","initiator_id","command_id","command_sha256","write_id","planned_artifact_id","replay_sha256","replay_schema_sha256"))prepared.set(f,replayIntent.required(f));
        sourceJournal(intentRow,"task.run.replay.prepared",write,write,graph.runId(),prepared,bootstrap.manifest());
        var start=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",graph.tenantId(),text(replayIntent,"outbox_event_id")));
        pins(start,Map.of("aggregate_id",graph.runId(),"event_type","run.start.requested"));var startPayload=read(s(start,"payload"));var command=startPayload.required("command");
        same(text(replayIntent,"command_id"),text(command,"command_id"));same(text(replayIntent,"command_sha256"),text(command,"command_sha256"));
        var unhashed=startPayload.deepCopy();((ObjectNode)unhashed.required("command")).remove("command_sha256");same(hash(unhashed),text(command,"command_sha256"));
        same(actor,text(startPayload.required("decision"),"actor_id"));same("START_RUN",text(command,"kind"));same("1.0.0",text(command,"schema_version"));same(graph.tenantId()+"/"+graph.runId(),text(command,"workflow_id"));
        for(String f:List.of("tenant_id","project_id","task_id","run_id","principal_id"))same(replay.required(f),command.required(f));
        same(graph.replaySha256(),text(command,"replay_sha256"));same(RuntimeReplaySchema.REF,text(command,"replay_schema_ref"));same(text(replayIntent,"replay_schema_sha256"),text(command,"replay_schema_sha256"));
        var value=json.object().put("schema_version","1.0.0");
        for(String f:List.of("tenant_id","project_id","task_id","run_id","principal_id","initiator_id"))value.set(f,replay.required(f));
        same(graph.principalId(),text(value,"principal_id"));same(graph.taskId(),text(value,"task_id"));
        value.put("command_id",text(command,"command_id")).put("command_sha256",text(command,"command_sha256"))
            .put("replay_manifest_id",artifact).put("replay_sha256",graph.replaySha256()).put("replay_object_version",text(object,"object_version"))
            .put("replay_receipt_sha256",hash(receipt)).put("replay_write_id",write).put("replay_intent_sha256",hash(replayIntent))
            .put("graph_id",graph.graphId()).put("graph_sha256",graph.graphSha256()).put("plan_revision",graph.planRevision()).put("plan_sha256",graph.planSha256()).put("classification",classification);
        graph.requireUnchanged();current(a,graph.runId());return value;
    }

    private ObjectNode stepSource(TaskProfessionalRuntimeService.Envelope envelope,TaskProfessionalObjectRegistration.Registered registered)throws Exception {
        Objects.requireNonNull(envelope).requireProductionReady();Objects.requireNonNull(registered).requireUnchanged();
        var a=envelope.admission();current(a,envelope.runId());if(!a.formalWorker())deny();var i=a.formalProof().identity();var input=envelope.input();input.requireUnchanged();
        var graph=graphs.require(a,envelope.runId());var runSource=runSource(a,graph);var runIntent=verifyRun(runSource,one(runRows(runSource)));
        same(i.run(),input.runId());same(i.step(),input.stepId());same(i.tenant(),input.tenantId());same(i.project(),input.projectId());same(graph.graphId(),input.graphId());same(graph.graphSha256(),input.graphSha256());
        same("STEP_ENVELOPE",registered.kind());same("2.0.0",registered.pin("schema_version"));same(TaskProfessionalRuntimeService.ENVELOPE_REF,registered.pin("schema_ref"));same(text(runSource,"classification"),registered.pin("classification"));same(envelope.payloadSha256(),registered.payloadSha256());same(envelope.sessionId(),registered.sessionId());
        same(envelope.actionId(),registered.actionId());same(envelope.runId(),registered.runId());same(envelope.stepId(),registered.stepId());same(envelope.leaseGeneration(),registered.leaseGeneration());
        same(envelope.workerService(),registered.workerService());same(input.inputId(),registered.inputId());same(input.inputSha256(),registered.inputSha256());same(input.receiptSha256(),registered.inputReceiptSha256());
        same(hash(read(new String(envelope.bytes(),StandardCharsets.UTF_8))),registered.reference().sha256());
        same(i.actor(),registered.pin("created_by"));same(i.principal(),registered.pin("principal_id"));same(i.tenant(),registered.pin("tenant_id"));same(i.project(),registered.pin("project_id"));same(input.taskId(),registered.pin("task_id"));
        var lease=one(db.queryForList("SELECT * FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),i.step()));
        pins(lease,Map.of("status","RUNNING","input_sha256",input.inputSha256(),"lease_owner",i.workerService()));same(i.generation(),s(lease,"lease_generation"));if(!now().isBefore(time(lease,"lease_expires_at")))deny();
        String arguments=TaskProfessionalRuntimeService.hostArguments(input),tool=text(envelope.deployment().value(),"host_tool_version");
        var action=one(db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),i.step(),i.action()));
        pins(action,Map.of("kind","HOST_CONTROL","state","BOUND","input_sha256",input.inputSha256(),"arguments_sha256",arguments,"tool_version",tool));same(i.generation(),s(action,"lease_generation"));if(action.get("invocation_id")!=null)deny();
        var value=json.object().put("schema_version","1.0.0");for(String f:List.of("tenant_id","project_id","task_id","run_id","principal_id","initiator_id","graph_id","graph_sha256","classification"))value.set(f,runSource.required(f));
        value.put("step_id",i.step()).put("lease_generation",Long.parseLong(i.generation())).put("action_id",i.action()).put("session_id",envelope.sessionId())
            .put("input_artifact_id",input.inputId()).put("input_sha256",input.inputSha256()).put("input_object_version",input.objectReference().objectVersion()).put("input_receipt_sha256",input.receiptSha256())
            .put("envelope_artifact_id",registered.artifactId()).put("envelope_sha256",registered.payloadSha256()).put("envelope_object_version",registered.reference().objectVersion()).put("envelope_receipt_sha256",registered.receiptSha256())
            .put("run_intent_id",text(runIntent,"intent_id")).put("run_intent_sha256",hash(runIntent)).put("worker_service_uri",i.workerService()).put("host_arguments_sha256",arguments).put("host_tool_version",tool);
        envelope.requireProductionReady();registered.requireUnchanged();input.requireUnchanged();return value;
    }

    private void writeRun(ObjectNode n)throws Exception {
        var row=metadata(n);for(String f:List.of("run_id","intent_id","plan_revision","replay_manifest_id","replay_sha256","replay_object_version"))row.put(f,text(n,f));
        row.put("canonical_intent",canonical(n));row.put("intent_sha256",hash(n));insert("run_runtime_intent",row);journal(n,RUN_ACTION,text(n,"intent_id"),text(n,"run_id"),1);
    }
    private void writeStep(ObjectNode n)throws Exception {
        var row=metadata(n);for(String f:List.of("run_id","step_id","command_id","action_id","envelope_artifact_id","envelope_sha256","envelope_object_version","input_sha256","worker_service_uri"))row.put(f,text(n,f));
        row.put("lease_generation",n.required("lease_generation").longValue());row.put("canonical_dispatch",canonical(n));row.put("dispatch_sha256",hash(n));insert("step_runtime_attempt",row);journal(n,STEP_ACTION,text(n,"command_id"),text(n,"step_id"),n.required("lease_generation").longValue());
    }
    private ObjectNode verifyRun(ObjectNode source,Map<String,Object> row)throws Exception {
        var n=verify("RunIntent",source,row,"canonical_intent","intent_sha256");
        for(String f:List.of("run_id","intent_id","plan_revision","replay_manifest_id","replay_sha256","replay_object_version"))same(text(n,f),s(row,f));
        verifyJournal(n,RUN_ACTION,text(n,"intent_id"),text(n,"run_id"),1);return n;
    }
    private ObjectNode verifyStep(ObjectNode source,Map<String,Object> row)throws Exception {
        var n=verify("StepDispatch",source,row,"canonical_dispatch","dispatch_sha256");
        for(String f:List.of("run_id","step_id","command_id","action_id","envelope_artifact_id","envelope_sha256","envelope_object_version","input_sha256","worker_service_uri"))same(text(n,f),s(row,f));
        same(n.required("lease_generation").asText(),s(row,"lease_generation"));verifyJournal(n,STEP_ACTION,text(n,"command_id"),text(n,"step_id"),n.required("lease_generation").longValue());return n;
    }
    private ObjectNode verify(String type,ObjectNode source,Map<String,Object> row,String document,String digest)throws Exception {
        var parsed=read(s(row,document));validate(type,parsed);var n=(ObjectNode)parsed;
        same(hash(n),s(row,digest));for(var fields=source.fields();fields.hasNext();){var f=fields.next();same(f.getValue(),n.required(f.getKey()));}
        for(var entry:metadata(n).entrySet())same(entry.getValue(),row.get(entry.getKey()));if(time(n,"created_at").isAfter(now()))bad();return n;
    }
    private void stamp(ObjectNode n){n.put("audit_id",id()).put("event_id",id()).put("created_at",now().toInstant(ZoneOffset.UTC).toString());}
    private LinkedHashMap<String,Object> metadata(JsonNode n){var row=new LinkedHashMap<String,Object>();for(String f:List.of("tenant_id","project_id","classification"))row.put(f,text(n,f));row.put("created_by",text(n,"initiator_id"));row.put("created_at",time(n,"created_at"));row.put("schema_version","1.0.0");row.put("schema_manifest_sha256",manifest);return row;}
    private void journal(JsonNode n,String action,String request,String aggregate,long revision)throws Exception {
        var audit=metadata(n);audit.put("audit_id",text(n,"audit_id"));audit.put("subject_id",text(n,"initiator_id"));audit.put("action",action);audit.put("resource_ref",canonical(n));audit.put("result","SUCCESS");audit.put("request_id",request);audit.put("trace_id",text(n,"run_id"));insert("audit_event",audit);
        insert("outbox_event",new LinkedHashMap<>(Map.of("tenant_id",text(n,"tenant_id"),"event_id",text(n,"event_id"),"aggregate_id",aggregate,"aggregate_revision",revision,"event_type",action,"payload",canonical(n),"occurred_at",time(n,"created_at"))));
    }
    private void verifyJournal(JsonNode n,String action,String request,String aggregate,long revision)throws Exception {
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",text(n,"tenant_id"),text(n,"audit_id")));
        for(var e:metadata(n).entrySet())same(e.getValue(),audit.get(e.getKey()));pins(audit,Map.of("subject_id",text(n,"initiator_id"),"action",action,"result","SUCCESS","request_id",request,"trace_id",text(n,"run_id")));if(audit.get("reason_code")!=null)bad();same(n,read(s(audit,"resource_ref")));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",text(n,"tenant_id"),text(n,"event_id")));
        pins(event,Map.of("tenant_id",text(n,"tenant_id"),"aggregate_id",aggregate,"event_type",action));same(Long.toString(revision),s(event,"aggregate_revision"));same(time(n,"created_at"),time(event,"occurred_at"));same(n,read(s(event,"payload")));
    }
    private void sourceJournal(Map<String,Object> original,String action,String request,String eventId,String aggregate,JsonNode content,String schemaManifest)throws Exception {
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND project_id=? AND action=? AND request_id=? LIMIT 2 FOR UPDATE",s(original,"tenant_id"),s(original,"project_id"),action,request));
        for(String f:List.of("tenant_id","project_id","created_by","created_at","classification","schema_version"))same(original.get(f),audit.get(f));pins(audit,Map.of("schema_manifest_sha256",schemaManifest,"subject_id",s(original,"created_by"),"trace_id",s(original,"run_id"),"result","SUCCESS"));if(audit.get("reason_code")!=null)bad();same(content,read(s(audit,"resource_ref")));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",s(original,"tenant_id"),eventId));pins(event,Map.of("aggregate_id",aggregate,"event_type",action));same("1",s(event,"aggregate_revision"));same(time(original,"created_at"),time(event,"occurred_at"));same(content,read(s(event,"payload")));
    }
    private List<Map<String,Object>> runRows(JsonNode n){return db.queryForList("SELECT * FROM run_runtime_intent WHERE tenant_id=? AND project_id=? AND run_id=? LIMIT 2 FOR UPDATE",text(n,"tenant_id"),text(n,"project_id"),text(n,"run_id"));}
    private List<Map<String,Object>> stepRows(JsonNode n){return db.queryForList("SELECT * FROM step_runtime_attempt WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",text(n,"tenant_id"),text(n,"project_id"),text(n,"run_id"),text(n,"step_id"),n.required("lease_generation").longValue());}

    /** Current-transaction proofs cannot escape or turn stored hashes into authority. */
    private abstract static class Bound {
        final EnterpriseAccess.Admission admission;final String run;private final Thread thread=Thread.currentThread();
        private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> resources=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        Bound(EnterpriseAccess.Admission a,String run){this.admission=a;this.run=run;if(resources.isEmpty())deny();active();}
        final void active(){current(admission,run);if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent())deny();var current=TransactionSynchronizationManager.getResourceMap();if(current.size()!=resources.size()||resources.entrySet().stream().anyMatch(e->current.get(e.getKey())!=e.getValue()))deny();}
    }
    public final class RunIntent extends Bound {
        private final TaskNodeProductionBasis authority;private final TaskRunGraphService.Verified graph;private final ObjectNode value;
        private RunIntent(EnterpriseAccess.Admission a,TaskNodeProductionBasis authority,TaskRunGraphService.Verified graph,ObjectNode value){super(a,authority.runId());this.authority=authority;this.graph=graph;this.value=value.deepCopy();}
        public void requireUnchanged()throws Exception{active();same(value,requireRun(authority,graph).value);}
        public String intentId(){active();return text(value,"intent_id");}public String sha256()throws Exception{active();return hash(value);}
        public JsonNode content(){active();return value.deepCopy();}
    }
    public final class Dispatch extends Bound {
        private final TaskProfessionalRuntimeService.Envelope envelope;private final TaskProfessionalObjectRegistration.Registered registered;private final ObjectNode value;
        private Dispatch(EnterpriseAccess.Admission a,TaskProfessionalRuntimeService.Envelope envelope,TaskProfessionalObjectRegistration.Registered registered,ObjectNode value){super(a,envelope.runId());this.envelope=envelope;this.registered=registered;this.value=value.deepCopy();}
        public void requireUnchanged()throws Exception{active();same(value,requireStep(envelope,registered,text(value,"command_id")).value);}
        public WorkflowProtocol.Dispatch dispatch(){active();return dispatchValue(value);}
        public String sha256()throws Exception{active();return hash(value);}public JsonNode content(){active();return value.deepCopy();}
    }
    private static WorkflowProtocol.Dispatch dispatchValue(JsonNode value){return new WorkflowProtocol.Dispatch(text(value,"run_id"),text(value,"step_id"),text(value,"action_id"),text(value,"command_id"),text(value,"input_sha256"),value.required("lease_generation").longValue(),text(value,"envelope_artifact_id"),text(value,"envelope_sha256"));}
    private static void current(EnterpriseAccess.Admission a,String run){
        if(!TransactionSynchronizationManager.isActualTransactionActive()||a.simulationWorker()||a.recoveryWorker())deny();var scope=TenantScope.requireCurrent();same(scope.tenantId(),a.context().tenant_id());same(scope.subjectId(),a.actorId());
        if(a.formalWorker()){var i=a.formalProof().identity();a.formalProof().requireActive();if(!"TASK".equals(i.executionKind())||!run.equals(i.run()))deny();}
        else if(a.controlWorker())a.controlProof().requireActive();else deny();
    }
    private JsonSchema definition(String type){return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type));}
    private void validate(String type,JsonNode value){if(!definition(type).validate(value).isEmpty())bad();}
    private JsonNode read(String raw)throws IOException{return parser.readTree(raw);}private String canonical(JsonNode value)throws IOException{return new String(ContentReadAuthorization.canonicalBytes(value),StandardCharsets.UTF_8);}private String hash(JsonNode value)throws IOException{return DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(value));}
    private void insert(String table,LinkedHashMap<String,Object> row){if(db.update("INSERT INTO "+table+"("+String.join(",",row.keySet())+") VALUES("+String.join(",",Collections.nCopies(row.size(),"?"))+")",row.values().toArray())!=1)bad();}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(rs,n)->rs.getTimestamp(1).toLocalDateTime()));}
    private static LocalDateTime time(JsonNode node,String key){return LocalDateTime.ofInstant(java.time.Instant.parse(text(node,key)),ZoneOffset.UTC);}
    private static LocalDateTime time(Map<String,Object> row,String key){var value=row.get(key);if(value instanceof LocalDateTime t)return t;if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    private static void pins(Map<String,Object> row,Map<String,String> values){for(var e:values.entrySet())same(e.getValue(),s(row,e.getKey()));}
    private static String text(JsonNode n,String key){var value=n.required(key);if(!value.isTextual())bad();return value.textValue();}
    private static String s(Map<String,Object> row,String key){var value=row.get(key);if(value==null)bad();return String.valueOf(value);}
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String id(){return UUID.randomUUID().toString();}
    private static void same(Object left,Object right){
        if(left instanceof JsonNode x&&right instanceof JsonNode y){if(!FormalTaskNodeAuthority.sameEvidence(x,y))bad();return;}
        if(left instanceof java.sql.Timestamp t)left=t.toLocalDateTime();if(right instanceof java.sql.Timestamp t)right=t.toLocalDateTime();
        if(!Objects.equals(left,right))bad();
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void conflict(){throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);}
}
