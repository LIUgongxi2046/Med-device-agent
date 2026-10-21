package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;

/** N04 original scheduler command, physical envelope and dispatch journal. Historical
 * proofs are read-only and never substitute for a current lease or mint a new attempt. */
public final class TaskSectionWriterDispatchJournal {
    public static final String ENVELOPE_REF="urn:medical:task-section-writer-dispatch:1.0.0#/$defs/Envelope";
    private static final Contract CONTRACT=contract();
    public static final String MANIFEST=CONTRACT.manifest;
    private static final String ACTION="task.section_writer.runtime.dispatched";
    private final String runtimeSchemaManifest;private final JdbcTemplate db;private final TaskSectionWriterSchema json;private final TaskRunActivityJournal journal;
    private final TaskRunActivityPreparation preparation;private final FormalRunRecoveryAccess recovery;
    private final TaskRunLaunchRecoveryAuthority launches;private final TaskRunWorkflowLedger workflow;
    private final TaskSectionWriterDispatchReservation reservations;private final RunTokenAuthority tokens;
    private final TaskSectionWriterHostBootstrap bootstrap;private final TaskSectionWriterObjectLedger objects;
    private final TaskSectionWriterProducerAuthority producers;private final RuntimeObjectTransport transport;
    private final TaskSectionWriterDeployment deployment;
    public TaskSectionWriterDispatchJournal(DataSource data,TaskSectionWriterSchema json,PermissionSchema permission,
            TaskRunActivityPreparation preparation,FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority launches,
            TaskRunWorkflowLedger workflow,TaskSectionWriterDispatchReservation reservations,RunTokenAuthority tokens,
            TaskSectionWriterHostBootstrap bootstrap,TaskSectionWriterObjectLedger objects,TaskSectionWriterProducerAuthority producers,
            RuntimeObjectTransport transport,TaskSectionWriterDeployment deployment){
        runtimeSchemaManifest=permission.manifestSha256();db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;journal=new TaskRunActivityJournal(db,permission);this.preparation=preparation;this.recovery=recovery;this.launches=launches;this.workflow=workflow;this.reservations=reservations;this.tokens=tokens;this.bootstrap=bootstrap;this.objects=objects;this.producers=producers;this.transport=transport;this.deployment=deployment;
    }
    /** One claim only. A durable reservation, including a partial or uncertain one,
     * is never permission to run the producer again. */
    public WorkflowProtocol.Dispatch dispatch(HttpServletRequest http,WorkflowProtocol.Reference ref,String step,String command)throws Exception {
        task(ref);WireValues.uuid(step);WireValues.uuid(command);
        var original=recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{
            reference(a,http,ref);var r=reservations.original(a,ref,step,command);if(r.isEmpty())return Optional.<WorkflowProtocol.Dispatch>empty();
            if(!r.get().claimed())unavailable();var value=read(a,ref,step,command);r.get().requireDispatch(value(value));return Optional.of(value(value));
        }).get());
        if(original.isPresent())return original.get();
        var pending=preparation.withCurrent(http,ref,c->reservations.reserve(c,http,ref,step,command));
        var lease=tokens.claimWriterReserved(http,new RunTokenAuthority.ClaimRequest(ref.tenantId(),pending.actor(),ref.runId(),step,pending.revision(),Duration.ofSeconds(deployment.value().required("lease_lifetime_seconds").longValue())),pending);
        var handle=bootstrap.open(http,ref.tenantId(),pending.actor(),ref.runId(),step,lease.generation());
        var attempt=handle.prepare(e->{validate("Envelope",e.document());return objects.prepareEnvelope(e,producers.require(e.authority().input(),e.authority().admission()));});
        S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->handle.prepare(e->{objects.requireEnvelopeTransfer(e,producers.require(e.authority().input(),e.authority().admission()),attempt);return null;})).get();
        var physical=transport.transferVerified(attempt,guard);
        var result=handle.prepare(e->{var registered=objects.registerEnvelope(e,producers.require(e.authority().input(),e.authority().admission()),attempt,physical);return record(ref,command,e,registered);});
        return recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->requireOriginal(a,http,ref,result).dispatch()).get());
    }
    private WorkflowProtocol.Dispatch record(WorkflowProtocol.Reference ref,String command,TaskSectionWriterRuntimeAdapter.Envelope e,TaskSectionWriterObjectLedger.Registered registered)throws Exception {
        e.requireProductionReady();registered.requireUnchanged();var a=e.authority();var i=a.input();var job=a.job();
        same("STEP_ENVELOPE",registered.kind());same(job.run(),ref.runId());same(job.tenant(),ref.tenantId());same(job.project(),ref.projectId());same(e.payloadSha256(),registered.reference().sha256());same(a.actionId(),registered.actionId());same(i.stepId(),registered.stepId());
        var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("kind","TASK_SECTION_WRITER_DISPATCH")
            .put("tenant_id",job.tenant()).put("project_id",job.project()).put("task_id",job.task()).put("run_id",job.run()).put("step_id",job.step()).put("action_id",a.actionId()).put("command_id",command)
            .put("session_id",e.sessionId()).put("lease_generation",a.leaseGeneration().longValueExact()).put("initiator_id",job.actor()).put("classification",job.classification()).put("reference_sha256",json.hash(ref))
            .put("input_artifact_id",i.artifactId()).put("input_sha256",i.inputSha256()).put("input_receipt_sha256",i.receiptSha256()).put("input_object_version",i.objectReference().objectVersion())
            .put("envelope_artifact_id",registered.artifactId()).put("envelope_sha256",registered.reference().sha256()).put("envelope_receipt_sha256",registered.receiptSha256()).put("envelope_object_version",registered.reference().objectVersion())
            .put("worker_service_uri",a.workerService()).put("launch_sha256",json.hash(e.launch()));validate("Dispatch",n);
        String id=id(ref.tenantId(),ref.runId(),job.step());var prior=journal.read(a.admission(),job.project(),job.run(),id,ACTION,key(job.step()),command,json.hash(ref),job.classification(),"1.0.0",MANIFEST);
        if(prior.isEmpty())journal.write(a.admission(),job.project(),job.run(),id,ACTION,key(job.step()),command,json.hash(ref),job.classification(),"1.0.0",MANIFEST,n);else same(n,prior.get());
        e.requireUnchanged();registered.requireUnchanged();return value(n);
    }
    /** Verifies immutable original first, then current exact lease/input/host binding.
     * No actor from an arbitrary caller locator is accepted. */
    public ProfessionalRuntimeObservations.Binding requireWorkflow(HttpServletRequest http,WorkflowProtocol.Reference ref,WorkflowProtocol.Dispatch dispatch)throws Exception {
        var b=recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{var p=requireOriginal(a,http,ref,dispatch);return ProfessionalRuntimeObservations.jsonBinding(p.launch().required("binding"));}).get());
        var handle=bootstrap.open(http,b);handle.prepare(e->{same(b,ProfessionalRuntimeObservations.jsonBinding(e.binding()));same(dispatch.stepEnvelopeSha256(),e.payloadSha256());return null;});return b;
    }
    public List<com.ruoyi.medical.workflow.TaskSectionWriterStageProtocolV1.CommandV1> commands(EnterpriseAccess.Admission a,HttpServletRequest http,WorkflowProtocol.Reference ref)throws Exception{reference(a,http,ref);return reservations.commands(a,ref);}
    public Optional<Original> originalByCommand(EnterpriseAccess.Admission a,HttpServletRequest http,WorkflowProtocol.Reference ref,String step,String command)throws Exception {
        reference(a,http,ref);var reservation=reservations.original(a,ref,step,command);if(reservation.isEmpty()||!reservation.get().claimed())return Optional.empty();
        if(db.queryForList("SELECT operation_id FROM operation_record WHERE tenant_id=? AND operation_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),id(ref.tenantId(),ref.runId(),step)).isEmpty())return Optional.empty();
        var value=read(a,ref,step,command);return Optional.of(requireOriginal(a,http,ref,value(value)));
    }
    public List<Original> dispatched(EnterpriseAccess.Admission a,HttpServletRequest http,WorkflowProtocol.Reference ref)throws Exception {
        var values=new ArrayList<Original>();for(var c:commands(a,http,ref))originalByCommand(a,http,ref,c.stepId(),c.commandId()).ifPresent(values::add);return List.copyOf(values);
    }
    public Original requireOriginal(EnterpriseAccess.Admission a,HttpServletRequest http,WorkflowProtocol.Reference ref,WorkflowProtocol.Dispatch expected)throws Exception {
        task(ref);reference(a,http,ref);same(ref.runId(),expected.runId());var reservation=reservations.original(a,ref,expected.stepId(),expected.commandId()).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));reservation.requireDispatch(expected);
        var n=read(a,ref,expected.stepId(),expected.commandId());same(value(n),expected);var p=new Original(a,http,ref,n);p.requireUnchanged();return p;
    }
    /** One immutable PREPARE response can authorize a process launch. An uncertain
     * worker HTTP response is recovered as an original, never as another launch. */
    public JsonNode openWorkflow(HttpServletRequest http,WorkflowProtocol.Reference ref,WorkflowProtocol.Dispatch d,TaskSectionWriterCandidateRecovery completed)throws Exception {
        var prior=recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{
            var original=requireOriginal(a,http,ref,d);var result=completed.original(original);if(result.isEmpty())return Optional.<JsonNode>empty();
            var launch=(ObjectNode)original.launch();launch.put("state","COMPLETED");launch.set("completion",result.get());return Optional.<JsonNode>of(launch);
        }).get());if(prior.isPresent())return prior.get();
        var binding=requireWorkflow(http,ref,d);var handle=bootstrap.open(http,binding);
        return handle.prepare(e->{var a=e.authority();var j=a.job();String launchId=derived(id(ref.tenantId(),ref.runId(),d.stepId()),"worker-launch");String action="task.section_writer.worker.launch";
            if(!db.queryForList("SELECT session_id FROM professional_runtime_session WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 1 FOR UPDATE",j.tenant(),j.run(),j.step(),a.leaseGeneration()).isEmpty())unavailable();
            if(journal.read(a.admission(),j.project(),j.run(),launchId,action,key(j.step()),d.commandId(),json.hash(d),j.classification(),"1.0.0",MANIFEST).isPresent())unavailable();
            var launch=e.launch();same(d.stepEnvelopeSha256(),e.payloadSha256());journal.write(a.admission(),j.project(),j.run(),launchId,action,key(j.step()),d.commandId(),json.hash(d),j.classification(),"1.0.0",MANIFEST,launch);return launch;
        });
    }
    void requireWorkerLaunch(TaskSectionWriterRuntimeAdapter.Envelope e)throws Exception {
        e.requireUnchanged();var a=e.authority();var job=a.job();String dispatchId=id(job.tenant(),job.run(),job.step());
        var raw=one(db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND operation_id=? LIMIT 2 FOR UPDATE",job.tenant(),dispatchId));var n=json.read(text(raw,"response_ref"));validate("Dispatch",n);
        var d=value(n);same(job.run(),d.runId());same(job.step(),d.stepId());same(a.actionId(),d.actionId());same(a.leaseGeneration().longValueExact(),d.leaseGeneration());same(e.payloadSha256(),d.stepEnvelopeSha256());same(e.inputSha256(),d.inputSha256());same(e.sessionId(),text(n,"session_id"));
        same(n,journal.read(a.admission(),job.project(),job.run(),dispatchId,ACTION,key(job.step()),d.commandId(),text(n,"reference_sha256"),job.classification(),"1.0.0",MANIFEST).orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED)));
        var launch=journal.read(a.admission(),job.project(),job.run(),derived(dispatchId,"worker-launch"),"task.section_writer.worker.launch",key(job.step()),d.commandId(),json.hash(d),job.classification(),"1.0.0",MANIFEST).orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED));same(e.launch(),launch);e.requireUnchanged();
    }
    public Optional<JsonNode> completed(HttpServletRequest http,WorkflowProtocol.Reference ref,WorkflowProtocol.Dispatch d,TaskSectionWriterCandidateRecovery completed)throws Exception {
        return recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->completed.original(requireOriginal(a,http,ref,d))).get());
    }
    /** Current commit and lifecycle have their own proof class; neither is a recovery capability. */
    StageFacts forCommit(FormalTaskNodeAuthority.Verified node,WorkflowProtocol.Reference ref,WorkflowProtocol.Dispatch d)throws Exception{
        node.requireUnchanged();var input=node.currentWriterInput();same(input.runId(),ref.runId());same(input.stepId(),d.stepId());same(input.inputSha256(),d.inputSha256());
        var facts=new StageFacts(node,null,null,ref,d);facts.requireUnchanged();return facts;
    }
    StageFacts forLifecycle(TaskRunLifecycleAuthority.Proof p,String step,long generation)throws Exception{
        p.require(p.operation());var r=p.reference();var row=one(db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND subject_id=? AND target_id=? AND operation_id=? AND operation_name=? LIMIT 2 FOR UPDATE",r.tenantId(),p.admission().actorId(),r.runId(),id(r.tenantId(),r.runId(),step),ACTION));var n=json.read(text(row,"response_ref"));validate("Dispatch",n);var d=value(n);same(step,d.stepId());same(generation,d.leaseGeneration());return forLifecycle(p,d);
    }
    StageFacts forLifecycle(TaskRunLifecycleAuthority.Proof p,WorkflowProtocol.Dispatch d)throws Exception{p.require(p.operation());var facts=new StageFacts(null,p,null,p.reference(),d);facts.requireUnchanged();return facts;}
    StageFacts forConsumer(TaskSectionWriterInputAuthority.Verified input,WorkflowProtocol.Reference ref,WorkflowProtocol.Dispatch d)throws Exception {
        input.requireUnchanged();same(input.tenantId(),ref.tenantId());same(input.projectId(),ref.projectId());same(input.runId(),ref.runId());same(input.stepId(),d.stepId());same(input.inputSha256(),d.inputSha256());
        var facts=new StageFacts(null,null,input,ref,d);facts.requireUnchanged();return facts;
    }
    final class StageFacts {
        private final FormalTaskNodeAuthority.Verified node;private final TaskRunLifecycleAuthority.Proof lifecycle;private final TaskSectionWriterInputAuthority.Verified consumer;private final WorkflowProtocol.Reference ref;private final WorkflowProtocol.Dispatch d;private final JsonNode saved;
        private StageFacts(FormalTaskNodeAuthority.Verified node,TaskRunLifecycleAuthority.Proof lifecycle,TaskSectionWriterInputAuthority.Verified consumer,WorkflowProtocol.Reference ref,WorkflowProtocol.Dispatch d)throws Exception{this.node=node;this.lifecycle=lifecycle;this.consumer=consumer;this.ref=ref;this.d=d;active();saved=readStage().deepCopy();}
        private void active()throws Exception{if(node!=null)node.requireUnchanged();else if(consumer!=null)consumer.requireUnchanged();else lifecycle.require(lifecycle.operation());}
        EnterpriseAccess.Admission admission()throws Exception{active();return node!=null?node.controlAdmission():consumer!=null?consumer.admission():lifecycle.admission();}
        WorkflowProtocol.Reference reference()throws Exception{active();return ref;}WorkflowProtocol.Dispatch dispatch()throws Exception{active();return d;}
        JsonNode envelope()throws Exception{active();return readPrepare(saved).required("envelope").deepCopy();}JsonNode launch()throws Exception{active();return readPrepare(saved).required("launch").deepCopy();}
        void requireUnchanged()throws Exception{active();same(saved,readStage());}
        private JsonNode readStage()throws Exception{
            var a=admission();var identity=(FormalRunControlAccess.ReadIdentity)a.controlProof().identity();var job=one(db.queryForList("SELECT r.task_id,p.principal_id FROM task_run r JOIN run_principal p ON p.tenant_id=r.tenant_id AND p.project_id=r.project_id AND p.run_id=r.run_id AND p.initiator_id=r.created_by WHERE r.tenant_id=? AND r.project_id=? AND r.run_id=? AND r.created_by=? AND r.execution_kind='TASK' LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId(),a.actorId()));
            reservations.requireStageFacts(a,ref,d,text(job,"task_id"),text(job,"principal_id"),consumer==null?null:consumer.originalSource());var n=readFacts(a,ref,d.stepId(),d.commandId(),identity.serviceUri(),text(job,"task_id"));same(d,value(n));return n;
        }
    }
    public final class Original {
        private final EnterpriseAccess.Admission admission;private final HttpServletRequest http;private final WorkflowProtocol.Reference ref;private final JsonNode original;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> tx=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Original(EnterpriseAccess.Admission a,HttpServletRequest http,WorkflowProtocol.Reference ref,JsonNode n){admission=a;this.http=http;this.ref=ref;original=n.deepCopy();if(tx.isEmpty())deny();}
        public void requireUnchanged()throws Exception{active();TaskSectionWriterDispatchJournal.this.reference(admission,http,ref);var d=dispatch();reservations.original(admission,ref,d.stepId(),d.commandId()).orElseThrow().requireDispatch(d);same(original,read(admission,ref,d.stepId(),d.commandId()));}
        private void active(){if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||!TransactionSynchronizationManager.isActualTransactionActive())deny();var current=TransactionSynchronizationManager.getResourceMap();if(current.size()!=tx.size()||tx.entrySet().stream().anyMatch(e->current.get(e.getKey())!=e.getValue()))deny();admission.recoveryProof().requireActive();}
        public WorkflowProtocol.Dispatch dispatch(){active();return value(original);}public EnterpriseAccess.Admission admission(){active();return admission;}public WorkflowProtocol.Reference reference(){active();return ref;}
        public JsonNode envelope()throws Exception{active();return readPrepare(original).required("envelope").deepCopy();}public JsonNode launch()throws Exception{active();return readPrepare(original).required("launch").deepCopy();}
    }
    private JsonNode read(EnterpriseAccess.Admission a,WorkflowProtocol.Reference ref,String step,String command)throws Exception {
        var identity=a.recoveryProof().identity();return readFacts(a,ref,step,command,identity.serviceUri(),identity.taskId());
    }
    private JsonNode readFacts(EnterpriseAccess.Admission a,WorkflowProtocol.Reference ref,String step,String command,String service,String task)throws Exception {
        var row=one(db.queryForList("SELECT classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND execution_kind='TASK' AND created_by=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId(),a.actorId()));
        var n=journal.read(a,ref.projectId(),ref.runId(),id(ref.tenantId(),ref.runId(),step),ACTION,key(step),command,json.hash(ref),text(row,"classification"),"1.0.0",MANIFEST).orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));validate("Dispatch",n);
        for(var pin:Map.of("tenant_id",ref.tenantId(),"project_id",ref.projectId(),"run_id",ref.runId(),"step_id",step,"command_id",command,"initiator_id",a.actorId(),"reference_sha256",json.hash(ref),"worker_service_uri",service).entrySet())same(pin.getValue(),text(n,pin.getKey()));
        same(task,text(n,"task_id"));readPrepare(n);readRegistered(n);return n;
    }
    private JsonNode readPrepare(JsonNode n)throws Exception {
        var r=one(db.queryForList("SELECT * FROM task_section_writer_runtime_prepare WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND session_id=? LIMIT 2 FOR UPDATE",text(n,"tenant_id"),text(n,"project_id"),text(n,"run_id"),text(n,"step_id"),n.required("lease_generation").longValue(),text(n,"session_id")));
        same("1.0.0",text(r,"schema_version"));same(runtimeSchemaManifest,text(r,"schema_manifest_sha256"));same(text(n,"classification"),text(r,"classification"));for(String f:List.of("tenant_id","project_id","run_id","step_id","action_id","session_id","input_artifact_id","input_receipt_sha256","envelope_sha256","launch_sha256"))same(text(n,f),text(r,f));same(text(n,"initiator_id"),text(r,"created_by"));same(n.required("lease_generation").asText(),number(r,"lease_generation"));
        var envelope=json.read(text(r,"envelope_json"));validate("Envelope",envelope);producers.requireOriginalEnvelope(envelope,time(r,"created_at"));same(json.hash(envelope),text(n,"envelope_sha256"));var launch=json.read(text(r,"launch_json"));same(json.hash(launch),text(n,"launch_sha256"));same("PREPARE",text(launch,"state"));same("N04",text(launch,"node_kind"));same("TASK",text(launch,"purpose"));same("SectionWriter",text(launch,"role"));
        for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id","action_id","session_id","input_artifact_id","input_sha256","input_receipt_sha256","worker_service_uri"))same(n.required(f),envelope.required(f));same(n.required("lease_generation"),envelope.required("lease_generation"));same(text(r,"deployment_sha256"),text(envelope,"deployment_sha256"));same(envelope.required("compiled_prompt"),launch.required("prompt"));same(envelope.required("prompt_sha256"),launch.required("prompt_sha256"));same(envelope.required("prompt_provenance"),launch.required("prompt_provenance"));
        same(new ProfessionalRuntimeObservations.Binding(text(n,"tenant_id"),text(n,"run_id"),text(n,"step_id"),text(n,"action_id"),n.required("lease_generation").longValue(),text(n,"input_sha256"),text(n,"envelope_sha256")),ProfessionalRuntimeObservations.jsonBinding(launch.required("binding")));
        var prepared=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("session_id",text(n,"session_id")).put("envelope_sha256",text(n,"envelope_sha256")).put("launch_sha256",text(n,"launch_sha256"));
        same(prepared,json.read(text(one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action='task.section_writer.runtime.prepared' AND request_id=? AND trace_id=? AND subject_id=? AND result='SUCCESS' LIMIT 2 FOR UPDATE",text(n,"tenant_id"),text(n,"project_id"),text(r,"audit_id"),text(n,"session_id"),text(n,"run_id"),text(n,"initiator_id"))),"resource_ref")));
        same(prepared,json.read(text(one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type='task.section_writer.runtime.prepared' AND aggregate_revision=1 LIMIT 2 FOR UPDATE",text(n,"tenant_id"),text(r,"event_id"),text(n,"session_id"))),"payload")));
        var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();result.set("envelope",envelope);result.set("launch",launch);return result;
    }
    private void readRegistered(JsonNode n)throws Exception {
        String tenant=text(n,"tenant_id"),artifact=text(n,"envelope_artifact_id");var row=one(db.queryForList("SELECT * FROM task_section_writer_object WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND artifact_id=? AND kind='STEP_ENVELOPE' AND state='REGISTERED' LIMIT 2 FOR UPDATE",tenant,text(n,"project_id"),text(n,"run_id"),text(n,"step_id"),artifact));
        same(text(n,"envelope_sha256"),text(row,"payload_sha256"));same(text(n,"envelope_object_version"),text(row,"object_version"));same(text(n,"envelope_receipt_sha256"),text(row,"receipt_sha256"));same(text(n,"initiator_id"),text(row,"created_by"));same(MANIFEST,text(row,"schema_manifest_sha256"));
        var receipt=json.read(text(row,"receipt_json"));same(json.hash(receipt),text(n,"envelope_receipt_sha256"));var payload=json.read(text(row,"canonical_payload"));same(json.hash(payload),text(n,"envelope_sha256"));same(readPrepare(n).required("envelope"),payload);
        var intent=one(db.queryForList("SELECT * FROM task_section_writer_object_intent WHERE tenant_id=? AND write_id=? AND artifact_id=? AND kind='STEP_ENVELOPE' LIMIT 2 FOR UPDATE",tenant,text(row,"write_id"),artifact));same(text(row,"canonical_payload"),text(intent,"canonical_payload"));same(text(n,"envelope_sha256"),text(intent,"payload_sha256"));var intentBody=json.read(text(intent,"intent_json"));same(json.hash(intentBody),text(intent,"intent_sha256"));same(json.read(text(intent,"producer_pins_json")),payload.required("producer"));same(payload.required("producer"),intentBody.required("producer_pins"));
        for(String f:List.of("tenant_id","run_id","step_id","action_id","kind","write_id","artifact_id","payload_sha256","object_key"))same(text(intent,f),text(intentBody,f));same(text(intent,"intent_sha256"),text(receipt,"intent_sha256"));same(json.hash(payload.required("producer")),text(receipt,"producer_pins_sha256"));
        var attempt=one(db.queryForList("SELECT * FROM task_section_writer_object_attempt WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",tenant,text(row,"write_id")));same(text(intent,"intent_sha256"),text(attempt,"intent_sha256"));same(text(n,"initiator_id"),text(attempt,"created_by"));
        var object=receipt.required("object_ref");var ref=new InterfaceProtocol.ObjectRef(text(object,"object_key"),text(object,"object_version"),text(object,"sha256"),text(object,"size_bytes"),text(object,"media_type"));same(text(n,"envelope_sha256"),ref.sha256());same(text(n,"envelope_object_version"),ref.objectVersion());same(text(intent,"object_key"),ref.objectKey());same(Integer.toString(json.bytes(payload).length),ref.sizeBytes());same("application/json",ref.mediaType());
        var ar=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND project_id=? AND artifact_id=? AND run_id=? AND artifact_kind='STEP_ENVELOPE' LIMIT 2 FOR UPDATE",tenant,text(n,"project_id"),artifact,text(n,"run_id")));StoredObjectReferences.decode(text(ar,"object_ref"),ref);same(ref.sha256(),text(ar,"content_sha256"));same(text(n,"initiator_id"),text(ar,"created_by"));
        var binding=one(db.queryForList("SELECT * FROM artifact_content_binding WHERE tenant_id=? AND project_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",tenant,text(n,"project_id"),artifact));same(ENVELOPE_REF,text(binding,"schema_ref"));same(MANIFEST,text(binding,"schema_document_sha256"));same("TASK",text(binding,"resource_kind"));same(text(n,"task_id"),text(binding,"resource_id"));same(text(ar,"dependency_access_set_id"),text(binding,"dependency_access_set_id"));
        same(receipt,json.read(text(one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action='task.section_writer.object.registered' AND request_id=? AND trace_id=? AND subject_id=? AND result='SUCCESS' LIMIT 2 FOR UPDATE",tenant,text(n,"project_id"),text(receipt,"audit_id"),text(n,"action_id"),text(n,"run_id"),text(n,"initiator_id"))),"resource_ref")));
        same(receipt,json.read(text(one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type='task.section_writer.object.registered' AND aggregate_revision=1 LIMIT 2 FOR UPDATE",tenant,text(receipt,"event_id"),artifact)),"payload")));
        var input=one(db.queryForList("SELECT * FROM task_section_writer_input WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",tenant,text(n,"project_id"),text(n,"run_id"),text(n,"step_id")));same(text(n,"input_artifact_id"),text(input,"input_id"));same(text(n,"input_sha256"),text(input,"input_sha256"));same(text(n,"input_object_version"),text(input,"object_version"));same(text(n,"input_receipt_sha256"),text(input,"receipt_sha256"));same(text(n,"input_sha256"),json.hash(json.read(text(input,"canonical_input"))));same(text(n,"input_receipt_sha256"),json.hash(json.read(text(input,"receipt_json"))));
        same(json.read(text(input,"producer_pins_json")),payload.required("producer"));same(text(n,"initiator_id"),text(input,"created_by"));
        var inputPayload=json.read(text(input,"canonical_input"));same("N04",text(inputPayload,"node_kind"));for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id"))same(n.required(f),inputPayload.required(f));for(String f:List.of("role_version","model_version","execution_policy_version","plan_revision","snapshot_id"))same(payload.required(f),inputPayload.required(f));
        var inputObject=one(db.queryForList("SELECT * FROM task_section_writer_object WHERE tenant_id=? AND artifact_id=? AND kind='TASK_SECTION_WRITER_INPUT' AND state='REGISTERED' LIMIT 2 FOR UPDATE",tenant,text(n,"input_artifact_id")));same(text(input,"receipt_json"),text(inputObject,"receipt_json"));same(text(input,"canonical_input"),text(inputObject,"canonical_payload"));same(text(n,"input_object_version"),text(inputObject,"object_version"));same(text(n,"input_sha256"),text(inputObject,"payload_sha256"));
        var inputReceipt=json.read(text(input,"receipt_json"));var inputRef=inputReceipt.required("object_ref");var inputArtifact=one(db.queryForList("SELECT object_ref FROM artifact WHERE tenant_id=? AND project_id=? AND artifact_id=? AND run_id=? AND artifact_kind='TASK_SECTION_WRITER_INPUT' LIMIT 2 FOR UPDATE",tenant,text(n,"project_id"),text(n,"input_artifact_id"),text(n,"run_id")));StoredObjectReferences.decode(text(inputArtifact,"object_ref"),new InterfaceProtocol.ObjectRef(text(inputRef,"object_key"),text(inputRef,"object_version"),text(inputRef,"sha256"),text(inputRef,"size_bytes"),text(inputRef,"media_type")));
        same(inputReceipt,json.read(text(one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND audit_id=? AND action='task.section_writer.object.registered' AND subject_id=? AND trace_id=? AND result='SUCCESS' LIMIT 2 FOR UPDATE",tenant,text(inputReceipt,"audit_id"),text(n,"initiator_id"),text(n,"run_id"))),"resource_ref")));
        same(inputReceipt,json.read(text(one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type='task.section_writer.object.registered' AND aggregate_revision=1 LIMIT 2 FOR UPDATE",tenant,text(inputReceipt,"event_id"),text(n,"input_artifact_id"))),"payload")));

    }
    private void reference(EnterpriseAccess.Admission a,HttpServletRequest http,WorkflowProtocol.Reference ref)throws Exception{task(ref);if(!a.recoveryWorker()||a.formalWorker()||a.controlWorker())deny();var identity=a.recoveryProof().identity();a.recoveryProof().requireActive();same(ref.tenantId(),identity.tenantId());same(ref.projectId(),identity.projectId());same(ref.runId(),identity.runId());var p=launches.require(a,http,ref.runId(),identity.commandId());same(ref,workflow.recover(p).reference());if(workflow.read(p).isEmpty())unavailable();p.requireUnchanged();}
    private static WorkflowProtocol.Dispatch value(JsonNode n){return new WorkflowProtocol.Dispatch(text(n,"run_id"),text(n,"step_id"),text(n,"action_id"),text(n,"command_id"),text(n,"input_sha256"),n.required("lease_generation").longValue(),text(n,"envelope_artifact_id"),text(n,"envelope_sha256"));}
    private static String id(String tenant,String run,String step){return derived(tenant+"/"+run+"/"+step,ACTION);}private static String key(String step){return "writer-dispatch:"+step;}
    private static void task(WorkflowProtocol.Reference ref){if(ref.executionKind()!=WorkflowProtocol.ExecutionKind.TASK)deny();}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void unavailable(){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
    private static String text(Map<String,Object> row,String field){return TaskRunActivityJournal.text(row,field);}
    private static String text(JsonNode n,String field){if(!n.required(field).isTextual())bad();return n.required(field).textValue();}
    private record Contract(JsonSchema schema,String manifest){}
    private static Contract contract(){try(var in=Objects.requireNonNull(TaskSectionWriterDispatchJournal.class.getResourceAsStream("/task-section-writer-dispatch.schema.json"))){byte[] bytes=in.readAllBytes();var schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());return new Contract(schema,WireValues.sha256(bytes));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static void validate(String type,JsonNode n){if(!CONTRACT.schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type)).validate(n).isEmpty())bad();}
}
