package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import com.ruoyi.medical.workflow.TaskRunWaitingProtocol.WaitingCompletionV2;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.ruoyi.medical.persistence.TaskRegisteredToolAuthority.*;

/** Truthful no-output waiting control. Only actual closed SDK/C07/AT08 proofs can
 * produce this immutable journal. It is never a professional SUCCESS artifact. */
public final class TaskRegisteredToolWaitingCompletion {
    static final String ACTION="task.professional.waiting.produced.v2";
    private final JdbcTemplate db;private final PermissionSchema json;private final TaskRunActivityJournal journal;
    private final TaskRegisteredToolAuthority authority;private final TaskRegisteredToolReceipts tools;
    private final TaskInvocationService invocations;private final TaskInvocationRecords records;private final TaskInvocationResults results;
    private final TaskProfessionalSupervisor supervisors;private final TaskProfessionalRuntimeService.Deployment deployment;
    private final TaskRuntimeJournal dispatches;private final TaskResearchRuleInputs rules;private final ResourceReadAuthority resources;
    private final FormalRuntimeCapabilityIdentityAuthority capabilities;private final FormalCapabilityIdentityAuthority roles;
    private final TaskRunWorkflowProtocolBinding protocols;private final TaskRegisteredWaitingSchema schema;
    public TaskRegisteredToolWaitingCompletion(DataSource data,PermissionSchema json,TaskRegisteredToolAuthority authority,TaskRegisteredToolReceipts tools,
            TaskInvocationService invocations,TaskInvocationRecords records,TaskInvocationResults results,TaskProfessionalSupervisor supervisors,
            TaskProfessionalRuntimeService.Deployment deployment,TaskRuntimeJournal dispatches,TaskResearchRuleInputs rules,ResourceReadAuthority resources,
            FormalRuntimeCapabilityIdentityAuthority capabilities,FormalCapabilityIdentityAuthority roles,TaskRunWorkflowProtocolBinding protocols)throws Exception {
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.authority=authority;this.tools=tools;this.invocations=invocations;this.records=records;
        this.results=results;this.supervisors=supervisors;this.deployment=deployment;this.dispatches=dispatches;this.rules=rules;this.resources=resources;
        this.capabilities=capabilities;this.roles=roles;this.protocols=protocols;journal=new TaskRunActivityJournal(db,json);schema=new TaskRegisteredWaitingSchema();
    }
    /** Null means there was no question, not success; the caller then uses unchanged V1 finish. */
    WaitingCompletionV2 produce(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed closure)throws Exception {
        session.requireUnchanged();closure.requireSuccessfulExit();var document=tools.waitingRecord(session);if(document==null)return null;
        var body=document.required("body");var request=body.required("request");var terminal=invocations.settledWaitingForSession(session,closure,text(request,"call_id"),request.required("arguments"));
        var checked=authority.requireClosedWaiting(session,request,terminal,closure);same(body.required("response"),tools.recover(checked));
        var protocol=protocols.requireWaitingV2(session.admission(),session.runId());protocol.requireUnchanged();
        var trace=TaskRegisteredToolWaitingTrace.require(session,closure,request,body.required("response"));var dispatch=dispatches.requireSessionStep(session);
        String id=id("TASK_REGISTERED_WAITING_COMPLETION",session.sessionId(),text(request,"call_id"));var i=session.input();
        var value=json.object().put("schema_version","2.0.0").put("kind","TASK_TOOL_WAITING_COMPLETION").put("receipt_id",id)
                .put("tenant_id",i.tenantId()).put("project_id",i.projectId()).put("actor_id",session.admission().actorId()).put("classification",checked.classification())
                .put("run_id",session.runId()).put("step_id",session.stepId()).put("session_id",session.sessionId()).put("input_id",i.inputId())
                .put("input_sha256",i.inputSha256()).put("input_receipt_sha256",i.receiptSha256()).put("supervision_receipt_id",closure.receiptId())
                .put("supervision_sha256",closure.sha256()).put("deployment_sha256",closure.deploymentSha256()).put("protocol_sha256",protocol.protocolSha256()).put("outcome","WAITING_INPUT");
        value.set("binding",session.binding());value.set("node_input",i.manifest());value.set("tool_document",document);value.set("trace",trace);value.set("invocations",terminal.canonicalPins());
        value.set("session_launch",session.launch());value.set("deployment",session.deployment().value());value.set("dispatch",json.tree(dispatch));value.putArray("outputs");schema.validate(value);
        var receipt=json.object().put("receipt_sha256",hash(value));receipt.set("body",value);
        String source=sourceHash(value);var old=journal.read(session.admission(),i.projectId(),i.runId(),id,ACTION,key(session.sessionId()),session.actionId(),source,checked.classification(),"2.0.0",schema.manifest());
        if(old.isEmpty())journal.write(session.admission(),i.projectId(),i.runId(),id,ACTION,key(session.sessionId()),session.actionId(),source,checked.classification(),"2.0.0",schema.manifest(),receipt);else same(receipt,old.get());
        checked.requireUnchanged();terminal.requireFor(session,closure);protocol.requireUnchanged();return locator(value,hash(value));
    }
    public Produced requireProduced(FormalTaskNodeAuthority.Verified node,WorkflowProtocol.Dispatch original,WaitingCompletionV2 locator)throws Exception {
        Objects.requireNonNull(node).requireUnchanged();var record=read(node.controlAdmission(),locator);requireCurrent(node,original,record);return new Produced(node,original,locator,record);
    }
    public Original requireOriginal(EnterpriseAccess.Admission recovery,WaitingCompletionV2 locator)throws Exception {
        if(!recovery.recoveryWorker()||recovery.controlWorker()||recovery.formalWorker()||recovery.simulationWorker())deny();var proof=recovery.recoveryProof();proof.requireActive();
        var original=dispatches.readHistoricalStep(recovery,locator.runId(),locator.stepId(),locator.leaseGeneration());var record=read(recovery,locator);same(json.tree(original),record.body.required("dispatch"));
        proof.requireActive();return new Original(recovery,locator,record);
    }
    QuestionOriginal requireQuestionOriginal(TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion q)throws Exception {
        q.requireUnchanged();var cause=q.creation().required("cause");String id=id("TASK_REGISTERED_WAITING_COMPLETION",text(cause,"session_id"),text(cause.required("request"),"call_id"));
        var row=one(db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND operation_id=? AND operation_name=? AND subject_id=? LIMIT 2 FOR UPDATE",q.tenantId(),id,ACTION,q.originalActorId()));
        var document=schema.parse(str(row,"response_ref"));var locator=locator(document.required("body"),text(document,"receipt_sha256"));var saved=read(q.admission(),locator,q);
        same(q.waitingItemId(),item(saved.body));var dispatch=dispatches.readQuestionStep(q,locator.stepId(),locator.leaseGeneration());same(json.tree(dispatch),saved.body.required("dispatch"));
        q.requireUnchanged();return new QuestionOriginal(q,locator,saved,dispatch);
    }
    final class QuestionOriginal {
        private final TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion q;private final WaitingCompletionV2 locator;private final Saved saved;private final WorkflowProtocol.Dispatch dispatch;
        private QuestionOriginal(TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion q,WaitingCompletionV2 locator,Saved saved,WorkflowProtocol.Dispatch dispatch){this.q=q;this.locator=locator;this.saved=saved;this.dispatch=dispatch;}
        void requireUnchanged()throws Exception{q.requireUnchanged();same(saved,read(q.admission(),locator,q));same(dispatch,dispatches.readQuestionStep(q,locator.stepId(),locator.leaseGeneration()));}
        WaitingCompletionV2 locator(){return locator;}WorkflowProtocol.Dispatch dispatch(){return dispatch;}JsonNode canonicalCommitRequest()throws Exception{return commit(saved);}String protocolSha256(){return text(saved.body,"protocol_sha256");}
    }
    private record Saved(JsonNode body,String sha256,LocalDateTime createdAt){Saved{body=body.deepCopy();}}
    private Saved read(EnterpriseAccess.Admission a,WaitingCompletionV2 locator)throws Exception {
        return read(a,locator,null);
    }
    private Saved read(EnterpriseAccess.Admission a,WaitingCompletionV2 locator,TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion q)throws Exception {
        if(q==null)active(a);else q.requireUnchanged();String actor=q==null?a.actorId():q.originalActorId();var row=one(db.queryForList("SELECT response_ref,created_at FROM operation_record WHERE tenant_id=? AND operation_id=? AND operation_name=? AND subject_id=? AND created_by=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),locator.waitingReceiptId(),ACTION,actor,actor));
        var document=schema.parse(str(row,"response_ref"));ProfessionalRuntimeObservations.closed(document,Set.of("body","receipt_sha256"));var body=document.required("body");schema.validate(body);
        same(hash(body),text(document,"receipt_sha256"));same(locator,locator(body,text(document,"receipt_sha256")));same(a.context().tenant_id(),text(body,"tenant_id"));same(actor,text(body,"actor_id"));
        var binding=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));same(locator.runId(),binding.run_id());same(locator.stepId(),binding.step_id());same(locator.inputSha256(),binding.input_sha256());same(locator.leaseGeneration(),binding.lease_generation());
        same(document,journal.readOriginalActor(a,actor,text(body,"project_id"),text(body,"run_id"),text(body,"receipt_id"),ACTION,key(text(body,"session_id")),binding.action_id(),sourceHash(body),text(body,"classification"),"2.0.0",schema.manifest()).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH)));
        var tool=body.required("tool_document");if(q==null)tools.requireWaitingOriginal(a,body.required("node_input"),tool);else tools.requireQuestionOriginal(q,body.required("node_input"),tool);
        var t=tool.required("body");for(String f:List.of("tenant_id","project_id","actor_id","run_id","step_id","session_id","input_id","input_sha256","input_receipt_sha256","classification"))same(body.required(f),t.required(f));same(body.required("binding"),t.required("binding"));
        same(id("TASK_REGISTERED_WAITING_COMPLETION",text(body,"session_id"),text(t.required("request"),"call_id")),text(body,"receipt_id"));
        var launch=body.required("session_launch");same(body.required("binding"),launch.required("binding"));same(text(body,"session_id"),text(launch,"session_id"));same(text(body,"deployment_sha256"),text(launch,"deployment_sha256"));
        var pinned=new TaskProfessionalRuntimeService.Deployment(body.required("deployment"),text(body,"deployment_sha256"));same(pinned.value().required("sdk"),launch.required("sdk"));
        TaskRegisteredToolWaitingTrace.requireOriginal(body.required("trace"),text(body,"session_id"),text(launch,"job_directory"),body.required("binding"),t.required("request"),t.required("response"));
        new TaskNodeInputSchema().read(ContentReadAuthorization.canonicalBytes(body.required("node_input")));
        requireDispatchShape(body.required("dispatch"),binding);requireClosedJournal(a,body);requireModels(a,body);var protocol=q==null?protocols.requireWaitingV2(a,locator.runId()):protocols.requireQuestionOriginal(q,locator.runId());same(protocol.protocolSha256(),text(body,"protocol_sha256"));protocol.requireUnchanged();
        return new Saved(body,text(document,"receipt_sha256"),TaskRunActivityJournal.time(row,"created_at"));
    }
    private void requireModels(EnterpriseAccess.Admission a,JsonNode body)throws Exception {
        var b=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));var ids=db.query("SELECT invocation_id FROM invocation WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND created_by=? ORDER BY invocation_id LIMIT 1025",(r,n)->r.getString(1),a.context().tenant_id(),text(body,"project_id"),b.run_id(),b.step_id(),b.lease_generation(),text(body,"actor_id"));
        if(ids.isEmpty()||ids.size()>1024||ids.size()!=body.required("invocations").size())bad();var ordered=new TreeMap<Long,TaskInvocationResults.Result>();
        for(String id:ids){var binding=records.byInvocation(a.context().tenant_id(),id);var n=binding.node();
            for(var p:Map.of("tenant_id",n.tenant_id(),"project_id",n.project_id(),"run_id",n.run_id(),"step_id",n.step_id(),"session_id",n.session_id(),"input_id",n.node_input_artifact_id(),"input_sha256",n.node_input_sha256(),"input_receipt_sha256",n.node_input_receipt_sha256(),"actor_id",binding.actor()).entrySet())same(text(body,p.getKey()),p.getValue());
            same(b.action_id(),n.host_action_id());same(Long.toString(b.lease_generation()),n.lease_generation());same(text(body.required("session_launch"),"isolation_receipt_id"),n.supervisor_receipt_id());same(text(body.required("session_launch"),"supervision_sha256"),n.supervisor_receipt_sha256());
            if(ordered.put(binding.original().modelRequestOrdinal(),results.verifiedResult(binding,id))!=null)bad();
        }
        var pins=json.object().arrayNode();var outputs=new ArrayList<JsonNode>();long ordinal=1;for(var entry:ordered.entrySet()){if(entry.getKey()!=ordinal++)bad();var result=entry.getValue();pins.addObject().put("invocation_id",result.id()).put("accounting_sha256",result.accounting()).put("result_registration_sha256",result.result()).put("capture_registration_sha256",result.capture());outputs.add(result.output());}
        same(pins,body.required("invocations"));var request=body.required("tool_document").required("body").required("request");TaskInvocationService.requireWaitingResult(outputs,text(request,"call_id"),request.required("arguments"),new FormalInvocationSchema());
        var expected=(ObjectNode)pins.get(pins.size()-1).deepCopy();expected.put("context_manifest_id",text(outputs.get(outputs.size()-1),"context_manifest_id")).put("tool_call_id",text(request,"call_id")).put("tool_name","ask_user_question").put("arguments_sha256",hash(request.required("arguments"))).put("session_id",text(body,"session_id")).put("supervision_receipt_id",text(body.required("session_launch"),"isolation_receipt_id")).put("supervision_sha256",text(body.required("session_launch"),"supervision_sha256"));
        same(expected,body.required("tool_document").required("body").required("authority").required("tool_call"));
    }
    private void requireCurrent(FormalTaskNodeAuthority.Verified node,WorkflowProtocol.Dispatch dispatch,Saved saved)throws Exception {
        node.requireUnchanged();var a=node.controlAdmission();var input=node.currentInput();var body=saved.body;var binding=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));
        same(dispatch,dispatches.requireExistingStep(node,binding));same(json.tree(dispatch),body.required("dispatch"));same(input.manifest(),body.required("node_input"));same(input.inputId(),text(body,"input_id"));same(input.receiptSha256(),text(body,"input_receipt_sha256"));
        same(deployment.sha256(),text(body,"deployment_sha256"));supervisors.requireClosedOriginal(a,body.required("binding"),text(body,"session_id"),text(body,"supervision_receipt_id"),text(body,"supervision_sha256"),deployment).requireSuccessfulExit();
        var t=body.required("tool_document").required("body");var pins=t.required("authority");requireNode(text(input.manifest(),"node_kind"),text(pins,"role_name"),"AT-08");
        var role=roles.require(a,json.basis(pins.required("role_version")),"ROLE",input.projectId(),"AGENT_PUBLICATION");same(role.canonicalDefinition(),pins.required("role_definition"));
        var tool=capabilities.require(a,json.basis(pins.required("tool_version")),"TOOL",input.projectId(),"AGENT_PUBLICATION");same(tool.canonicalDefinition(),pins.required("tool_definition"));
        var needed=one(node.nodePermissions().stream().filter(p->"TASK".equals(p.scope().resource_kind())&&input.taskId().equals(p.scope().resource_id())&&"task.run".equals(p.action())&&"TASK_EXECUTION".equals(p.purpose())).toList());resources.require(a,needed.scope(),needed.action(),needed.purpose());
        for(var allowed:List.of(role.canonicalDefinition().required("request").required("tool_scope").required("permissions"),tool.canonicalDefinition().required("request").required("tool_scope").required("permissions"))) {boolean found=false;for(var p:allowed)if(FormalTaskNodeAuthority.sameEvidence(json.tree(needed),p))found=true;if(!found)deny();}
        var fixed=rules.requireNode(node);var witness=t.required("physical").required("witness");
        one(db.queryForList("SELECT waiting_item_id FROM waiting_item WHERE tenant_id=? AND project_id=? AND waiting_item_id=? AND status='WAITING_INPUT' AND revision=1 AND resolution_id IS NULL LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),item(body)));same(fixed.ownerId(),text(witness.required("cause"),"assignee_id"));same(json.tree(fixed.requirementIds()),witness.required("cause").required("fixed_requirement_ids"));TaskRegisteredToolWaitingService.validateQuestion(t.required("request").required("arguments"),input.manifest(),fixed.requirementIds());
        role.requireUnchanged();tool.requireUnchanged();fixed.requireUnchanged();node.requireUnchanged();
    }
    private void requireClosedJournal(EnterpriseAccess.Admission a,JsonNode body)throws Exception {
        var launch=body.required("session_launch");String session=text(body,"session_id");var sessionRow=one(db.queryForList("SELECT * FROM professional_runtime_session WHERE tenant_id=? AND session_id=? AND created_by=? AND admission_state='CLOSED' LIMIT 2 FOR UPDATE",a.context().tenant_id(),session,text(body,"actor_id")));
        same(launch,schema.parse(str(sessionRow,"canonical_start")));same(json.canonical(launch),str(sessionRow,"canonical_start"));same(hash(launch),str(sessionRow,"start_sha256"));
        ProfessionalRuntimeObservations.closed(launch,Set.of("state","purpose","session_id","node_kind","role","runtime_manifest_sha256","deployment_sha256","job_directory","prompt","prompt_sha256","binding","prompt_provenance","sdk","profile_files","expected_inventory","role_version","profile_artifact","isolation_receipt_id","supervision_sha256"));
        same("START",text(launch,"state"));same("TASK",text(launch,"purpose"));same("N02",text(launch,"node_kind"));
        var binding=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));
        for(String f:List.of("tenant_id","project_id","run_id","step_id","input_sha256","classification"))same(text(body,f),str(sessionRow,f));
        same(binding.action_id(),str(sessionRow,"action_id"));same(Long.toString(binding.lease_generation()),str(sessionRow,"lease_generation"));same(binding.step_envelope_sha256(),str(sessionRow,"envelope_sha256"));
        same(text(body.required("deployment"),"worker_service_uri"),str(sessionRow,"service_uri"));WireValues.hash(str(sessionRow,"certificate_sha256"));WireValues.uuid(str(sessionRow,"trust_revision"));
        same("2",str(sessionRow,"revision"));same("1.0.0",str(sessionRow,"schema_version"));same(json.manifestSha256(),str(sessionRow,"schema_manifest_sha256"));
        var opened=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND created_by=? AND action='task.professional.session.opened' AND request_id=? AND trace_id=? LIMIT 2",a.context().tenant_id(),text(body,"project_id"),text(body,"actor_id"),text(body,"actor_id"),session,text(body,"run_id")));
        for(String f:List.of("tenant_id","project_id","created_by","classification","schema_version","schema_manifest_sha256","created_at"))same(sessionRow.get(f),opened.get(f));same("SUCCESS",str(opened,"result"));if(opened.get("reason_code")!=null)bad();same(json.canonical(launch),str(opened,"resource_ref"));
        var openedEvent=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",a.context().tenant_id(),session));same(text(body,"run_id"),str(openedEvent,"aggregate_id"));same("1",str(openedEvent,"aggregate_revision"));same("task.professional.session.opened",str(openedEvent,"event_type"));same(json.canonical(launch),str(openedEvent,"payload"));same(sessionRow.get("created_at"),openedEvent.get("occurred_at"));
        var closed=supervision(a,body,"CLOSED");var admitted=supervision(a,body,"ADMITTED");
        same(text(body,"supervision_receipt_id"),text(closed,"receipt_id"));same(text(body,"supervision_sha256"),hash(closed));same(text(admitted,"receipt_id"),text(closed,"admitted_receipt_id"));same(hash(admitted),text(closed,"admitted_sha256"));
        same(text(admitted,"receipt_id"),text(launch,"isolation_receipt_id"));same(hash(admitted),text(launch,"supervision_sha256"));same(text(body.required("trace"),"archive_sha256"),text(closed.required("session_export"),"sha256"));same((long)Base64.getDecoder().decode(text(body.required("trace"),"archive_content_base64")).length,closed.required("session_export").required("size_bytes").longValue());
        TaskProfessionalSupervisor.validateOriginalAdmission(body.required("binding"),admitted,body.required("deployment"),text(body,"deployment_sha256"),session);
        TaskProfessionalSupervisor.validateOriginalClosure(body.required("binding"),closed,body.required("deployment"),text(body,"deployment_sha256"),session);
        same("EXITED",text(closed.required("exit"),"code"));same(0,closed.required("exit").required("status").intValue());
    }
    private JsonNode supervision(EnterpriseAccess.Admission a,JsonNode body,String phase)throws Exception {
        var row=one(db.queryForList("SELECT * FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),text(body,"session_id"),phase));
        for(String f:List.of("tenant_id","project_id","run_id","step_id","classification","input_sha256","deployment_sha256"))same(text(body,f),str(row,f));same(text(body,"actor_id"),str(row,"created_by"));same("1.0.0",str(row,"schema_version"));same(json.manifestSha256(),str(row,"schema_manifest_sha256"));
        var deployment=body.required("deployment");same(text(deployment,"supervisor_service_uri"),str(row,"supervisor_service_uri"));same(text(deployment,"supervisor_certificate_sha256"),str(row,"certificate_sha256"));same(text(deployment,"supervisor_trust_revision"),str(row,"trust_revision"));same(text(deployment,"deployment_revision"),str(row,"deployment_revision"));
        var evidence=schema.parse(str(row,"canonical_evidence"));same(hash(evidence),str(row,"evidence_sha256"));same(json.canonical(evidence),str(row,"canonical_evidence"));same(phase,text(evidence,"phase"));same(text(evidence,"receipt_id"),str(row,"receipt_id"));same(body.required("binding"),evidence.required("binding"));
        String action="task.professional."+phase.toLowerCase(Locale.ROOT);var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",a.context().tenant_id(),str(row,"audit_id")));
        for(String f:List.of("tenant_id","project_id","created_by","classification","schema_version","schema_manifest_sha256","created_at"))same(row.get(f),audit.get(f));same(text(body,"actor_id"),str(audit,"subject_id"));same(action,str(audit,"action"));same("SUCCESS",str(audit,"result"));if(audit.get("reason_code")!=null)bad();same(text(body,"session_id"),str(audit,"request_id"));same(text(evidence,"receipt_id"),str(audit,"trace_id"));same(evidence,schema.parse(str(audit,"resource_ref")));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",a.context().tenant_id(),str(row,"event_id")));same(text(body,"session_id"),str(event,"aggregate_id"));same("ADMITTED".equals(phase)?"1":"2",str(event,"aggregate_revision"));same(action,str(event,"event_type"));same(row.get("created_at"),event.get("occurred_at"));same(evidence,schema.parse(str(event,"payload")));return evidence;
    }
    private static void requireDispatchShape(JsonNode value,ProfessionalRuntimeObservations.Binding binding) {
        ProfessionalRuntimeObservations.closed(value,Set.of("run_id","step_id","action_id","command_id","input_sha256","lease_generation","step_envelope_artifact_id","step_envelope_sha256"));
        var dispatch=new WorkflowProtocol.Dispatch(text(value,"run_id"),text(value,"step_id"),text(value,"action_id"),text(value,"command_id"),text(value,"input_sha256"),value.required("lease_generation").longValue(),text(value,"step_envelope_artifact_id"),text(value,"step_envelope_sha256"));
        if(!value.required("lease_generation").isIntegralNumber()||!value.required("lease_generation").canConvertToLong())bad();same(binding.run_id(),dispatch.runId());same(binding.step_id(),dispatch.stepId());same(binding.action_id(),dispatch.actionId());same(binding.input_sha256(),dispatch.inputSha256());same(binding.lease_generation(),dispatch.leaseGeneration());same(binding.step_envelope_sha256(),dispatch.stepEnvelopeSha256());
    }
    public final class Produced {
        private final FormalTaskNodeAuthority.Verified node;private final WorkflowProtocol.Dispatch dispatch;private final WaitingCompletionV2 locator;private final Saved saved;
        private Produced(FormalTaskNodeAuthority.Verified node,WorkflowProtocol.Dispatch dispatch,WaitingCompletionV2 locator,Saved saved){this.node=node;this.dispatch=dispatch;this.locator=locator;this.saved=saved;}
        public void requireUnchanged()throws Exception{var now=read(node.controlAdmission(),locator);same(saved,now);requireCurrent(node,dispatch,now);}
        public String waitingItemId(){node.controlAdmission();return item(saved.body);}public String waitingReceiptId(){node.controlAdmission();return locator.waitingReceiptId();}public String waitingReceiptSha256(){node.controlAdmission();return locator.waitingReceiptSha256();}
        public JsonNode canonicalCommitRequest()throws Exception{requireUnchanged();return commit(saved);}public String stepRevision()throws Exception{requireUnchanged();return str(one(db.queryForList("SELECT revision FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",text(saved.body,"tenant_id"),locator.runId(),locator.stepId())),"revision");}
        public String classification(){node.controlAdmission();return text(saved.body,"classification");}public LocalDateTime createdAt(){node.controlAdmission();return saved.createdAt;}
    }
    public final class Original {
        private final EnterpriseAccess.Admission recovery;private final WaitingCompletionV2 locator;private final Saved saved;
        private Original(EnterpriseAccess.Admission recovery,WaitingCompletionV2 locator,Saved saved){this.recovery=recovery;this.locator=locator;this.saved=saved;}
        public void requireUnchanged()throws Exception{recovery.recoveryProof().requireActive();same(saved,read(recovery,locator));same(json.tree(dispatches.readHistoricalStep(recovery,locator.runId(),locator.stepId(),locator.leaseGeneration())),saved.body.required("dispatch"));}
        public String waitingItemId(){recovery.recoveryProof().requireActive();return item(saved.body);}public String waitingReceiptId(){recovery.recoveryProof().requireActive();return locator.waitingReceiptId();}public String waitingReceiptSha256(){recovery.recoveryProof().requireActive();return locator.waitingReceiptSha256();}
        public JsonNode canonicalCommitRequest()throws Exception{requireUnchanged();return commit(saved);}public String classification(){recovery.recoveryProof().requireActive();return text(saved.body,"classification");}public LocalDateTime createdAt(){recovery.recoveryProof().requireActive();return saved.createdAt;}
    }
    private JsonNode commit(Saved saved)throws Exception{var b=saved.body;var binding=ProfessionalRuntimeObservations.jsonBinding(b.required("binding"));var witness=b.required("tool_document").required("body").required("physical").required("witness");var value=json.object().put("schema_version","2.0.0").put("kind","TASK_TOOL_WAITING_COMMIT").put("run_id",binding.run_id()).put("step_id",binding.step_id()).put("input_sha256",binding.input_sha256()).put("lease_generation",binding.lease_generation()).put("dispatch_sha256",hash(b.required("dispatch"))).put("waiting_item_id",item(b)).put("waiting_receipt_id",text(witness,"waiting_item_id")).put("waiting_receipt_sha256",hash(witness)).put("producer_receipt_id",text(b,"receipt_id")).put("producer_receipt_sha256",saved.sha256);value.putArray("outputs");return value;}
    private static String item(JsonNode b){return text(b.required("tool_document").required("body").required("physical").required("witness"),"waiting_item_id");}
    private static WaitingCompletionV2 locator(JsonNode body,String sha){var b=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));return new WaitingCompletionV2("2.0.0","WAITING_CONTROL",b.run_id(),b.step_id(),b.input_sha256(),b.lease_generation(),text(body,"receipt_id"),sha);}
    private static String key(String session){return "waiting-completion-v2:"+session;}
    private static String sourceHash(JsonNode b)throws Exception{return hash(Map.of("binding",b.required("binding"),"tool_receipt_sha256",text(b.required("tool_document"),"receipt_sha256"),"dispatch",b.required("dispatch")));}
    private static void active(EnterpriseAccess.Admission a){if(!TransactionSynchronizationManager.isActualTransactionActive()||a.simulationWorker())deny();if(a.controlWorker())a.controlProof().requireActive();else if(a.formalWorker())a.formalProof().requireActive();else if(a.recoveryWorker())a.recoveryProof().requireActive();else deny();}
}
