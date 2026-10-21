package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Genuine TASK C07 orchestration. HOST_CONTROL only binds a requested action. A separately
 * issued model.send token, actual exact-input/context, 097,083,099 and 098 are all required.
 * Existing 076 one-shot send, original-object recovery and accounting are shared unchanged. */
public final class TaskInvocationService {
    private final FormalWorkerAccess workers;private final RunTokenAuthority tokens;private final TaskInvocationContext contexts;
    private final TaskInvocationRecords records;private final FormalInvocationReservations reservations;
    private final FormalInvocationDispatch dispatch;private final FormalInvocationReceiptObjects receiptObjects;
    private final FormalInvocationSettlement settlement;private final FormalInvocationObjectRegistration objects;
    private final FormalInvocationCancellation cancellation;private final FormalProviderCaptureEvidence captures;private final FormalInvocationSchema schema;private final RunTokenAuthoritySchema tokenSchema;
    private final JdbcTemplate db;private final TaskInvocationResults resultHistory;
    public TaskInvocationService(DataSource data,FormalWorkerAccess workers,RunTokenAuthority tokens,TaskInvocationContext contexts,
            TaskInvocationRecords records,FormalInvocationReservations reservations,FormalInvocationDispatch dispatch,
            FormalInvocationReceiptObjects receiptObjects,FormalInvocationSettlement settlement,FormalInvocationObjectRegistration objects,
            FormalProviderCaptureEvidence captures,FormalInvocationSchema schema,RunTokenAuthoritySchema tokenSchema)throws Exception {
        this(data,workers,tokens,contexts,records,reservations,dispatch,receiptObjects,settlement,objects,captures,schema,tokenSchema,null);
    }
    public TaskInvocationService(DataSource data,FormalWorkerAccess workers,RunTokenAuthority tokens,TaskInvocationContext contexts,
            TaskInvocationRecords records,FormalInvocationReservations reservations,FormalInvocationDispatch dispatch,
            FormalInvocationReceiptObjects receiptObjects,FormalInvocationSettlement settlement,FormalInvocationObjectRegistration objects,
            FormalProviderCaptureEvidence captures,FormalInvocationSchema schema,RunTokenAuthoritySchema tokenSchema,FormalInvocationCancellation cancellation)throws Exception {
        this.cancellation=cancellation;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.workers=workers;this.tokens=tokens;this.contexts=contexts;this.records=records;
        this.reservations=reservations;this.dispatch=dispatch;this.receiptObjects=receiptObjects;this.settlement=settlement;
        this.objects=objects;this.captures=captures;this.schema=schema;this.tokenSchema=tokenSchema;resultHistory=new TaskInvocationResults(data,records,settlement,objects,captures,schema);
    }
    /** The return value is data, not a token or model permission. UUIDs are derived only from
     * the already registered immutable context so retries select the original action. */
    public BoundAction bindAction(TaskProfessionalRuntimeService.Session session,
            TaskProfessionalSupervisor.Admitted supervisor,TaskInvocationContext.Registered context)throws Exception {
        session.requireUnchanged();supervisor.requireUnchanged();var a=session.admission();context.requireCurrent(a);
        var request=context.request();var node=request.node();requireSession(node,session);
        if(!"OPEN".equals(session.state())||!supervisor.sessionId().equals(session.sessionId())
                ||!supervisor.binding().equals(session.binding())||!supervisor.receiptId().equals(node.supervisor_receipt_id())
                ||!supervisor.sha256().equals(node.supervisor_receipt_sha256()))denied();
        String action=actionId(context.contextId());var binding=records.bind(a,context,action);
        var existing=db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",node.tenant_id(),node.run_id(),node.step_id(),action);
        if(existing.isEmpty()){
            var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
            changed(db.update("INSERT INTO step_action_binding (tenant_id,project_id,run_id,step_id,action_id,input_sha256,origin_run_id,origin_step_id,origin_action_id,kind,arguments_sha256,tool_version,state,lease_generation,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES (?,?,?,?,?,?,?,?,?,'MODEL_CALL',?,?,'BOUND',?,'1.0.0',?,?,?,?,1,?)",
                    node.tenant_id(),node.project_id(),node.run_id(),node.step_id(),action,node.node_input_sha256(),node.run_id(),node.step_id(),action,binding.requestHash(),request.model_config_revision(),node.lease_generation(),tokenSchema.manifestSha256(),a.actorId(),now,node.classification(),now));
        }
        var row=one(db.queryForList("SELECT action_id FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND input_sha256=? AND origin_run_id=run_id AND origin_step_id=step_id AND origin_action_id=action_id AND kind='MODEL_CALL' AND arguments_sha256=? AND tool_version=? AND lease_generation=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND created_by=? AND classification=? LIMIT 2 FOR UPDATE",
                node.tenant_id(),node.project_id(),node.run_id(),node.step_id(),action,node.node_input_sha256(),binding.requestHash(),request.model_config_revision(),node.lease_generation(),tokenSchema.manifestSha256(),a.actorId(),node.classification()));
        if(!action.equals(row.get("action_id")))bad();context.requireCurrent(a);session.requireUnchanged();supervisor.requireUnchanged();
        var model=context.model();var requestAction=new RunTokenAuthority.ActionRequest(node.run_id(),node.step_id(),action,new BigInteger(node.lease_generation()),node.node_input_sha256(),binding.requestHash(),"MODEL_CALL",request.model_config_revision(),
                new PermissionProtocol.Scope(node.tenant_id(),model.project_id(),"CONFIGURATION",model.config_id()),"model.send",request.business_purpose());
        return new BoundAction(requestAction,context.contextId(),node.tenant_id(),a.actorId(),context.requiredActions(),request.request_parameters());
    }
    static String actionId(String context){WireValues.uuid(context);return UUID.nameUUIDFromBytes(("TASK_C07_MODEL_CALL:"+context).getBytes(StandardCharsets.US_ASCII)).toString();}

    public Prepared prepare(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,String contextId)throws Exception {
        outside();requireModelAction(action);WireValues.uuid(contextId);
        var initial=workers.executeAction(http,token,action,(a,w)->ThrowingSupplier.of(()->{
            var current=contexts.requireCurrent(a,contextId);var binding=records.bind(a,current,action.actionId());
            requireModelIdentity(a,w,action,current,binding);return new Snapshot(binding,body(current),schema.parse(schema.canonical(current.model())),current.authorizationSha256(),current.price(),current.credentialReferenceId(),current.credentialRevision(),current.sendAuthorizations());
        }).get());
        var result=new Prepared(http,token,action,initial);result.requireCurrentOutside();return result;
    }
    public Prepared prepareInvocation(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,String invocation)throws Exception {
        outside();requireModelAction(action);WireValues.uuid(invocation);
        String context=workers.executeAction(http,token,action,(a,w)->ThrowingSupplier.of(()->{
            var original=records.byInvocation(a.context().tenant_id(),invocation);
            if(!original.action().equals(action.actionId())||!original.run().equals(action.runId())||!original.step().equals(action.stepId())||!original.actor().equals(a.actorId()))denied();return original.context();
        }).get());return prepare(http,token,action,context);
    }
    public FormalInvocationReservations.Reserved reserveExpected(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,String model,String context,JsonNode parameters)throws Exception {
        var prepared=prepare(http,token,action,context);
        if(!prepared.binding.model().equals(model)||!FormalTaskNodeAuthority.sameEvidence(prepared.binding.original().request().request_parameters(),parameters))bad();return reserve(prepared);
    }
    public DispatchCall dispatchCall(Prepared prepared,String invocation)throws Exception {
        outside();prepared.requireCurrentOutside();WireValues.uuid(invocation);
        workers.executeAction(prepared.http,prepared.token,prepared.action,(a,w)->ThrowingSupplier.of(()->{
            prepared.requireCurrent(a,w);var actual=records.byInvocation(a.context().tenant_id(),invocation);
            if(!actual.action().equals(prepared.binding.action())||!actual.context().equals(prepared.binding.context())||!actual.requestHash().equals(prepared.binding.requestHash()))denied();return null;
        }).get());return new DispatchCall(prepared,invocation);
    }
    /** One-shot process command, not a provider permission. Python must independently obtain
     * the existing076 begin-send permit using the original exact MODEL_CALL token. */
    public final class DispatchCall {
        private final Prepared prepared;private final String invocation;private final java.util.concurrent.atomic.AtomicBoolean consumed=new java.util.concurrent.atomic.AtomicBoolean();
        private DispatchCall(Prepared prepared,String invocation){this.prepared=prepared;this.invocation=invocation;}
        public String invocationId(){return invocation;}
        byte[] consume()throws Exception {
            outside();prepared.requireCurrentOutside();if(!consumed.compareAndSet(false,true))denied();var a=prepared.action;var scope=a.scope();
            var action=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("run_id",a.runId()).put("step_id",a.stepId()).put("action_id",a.actionId()).put("lease_generation",a.generation().toString()).put("input_sha256",a.inputSha256()).put("arguments_sha256",a.argumentsSha256()).put("kind",a.kind()).put("tool_version",a.toolVersion()).put("action",a.action()).put("purpose",a.purpose());
            action.set("scope",new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(scope));
            var wire=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0").put("mode","DISPATCH").put("invocation_id",invocation).put("run_token",prepared.token);wire.set("worker_action",action);
            return ContentReadAuthorization.canonicalBytes(wire);
        }
        @Override public String toString(){return "TaskInvocationDispatchCall[redacted]";}
    }
    /** Token minting remains subject to FormalTaskNodeAuthority, original093 and principal intersection. */
    public Prepared issueAndPrepare(HttpServletRequest http,BoundAction bound,Duration lifetime)throws Exception {
        outside();Objects.requireNonNull(bound);var action=bound.action;requireModelAction(action);
        var token=tokens.issue(http,new RunTokenAuthority.IssueRequest(bound.tenant,bound.actor,action.runId(),action.stepId(),action.generation(),bound.requiredActions,lifetime));
        return prepare(http,token.token(),action,bound.context);
    }
    /** Bounded metadata copied from genuine context preparation, never authorization itself. */
    public static final class BoundAction {
        private final RunTokenAuthority.ActionRequest action;private final String context,tenant,actor;private final List<String> requiredActions;private final JsonNode parameters;
        private BoundAction(RunTokenAuthority.ActionRequest action,String context,String tenant,String actor,List<String> requiredActions,JsonNode parameters){
            this.action=action;this.context=context;this.tenant=tenant;this.actor=actor;this.requiredActions=List.copyOf(requiredActions);this.parameters=parameters.deepCopy();
            if(!this.requiredActions.contains("model.send")||new HashSet<>(this.requiredActions).size()!=this.requiredActions.size())denied();
        }
        public RunTokenAuthority.ActionRequest action(){return action;}public String contextId(){return context;}
        public JsonNode streamRequest(){var result=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0").put("run_id",action.runId()).put("step_id",action.stepId()).put("action_id",action.actionId()).put("context_manifest_id",context).put("model_config_revision",action.toolVersion()).put("lease_generation",action.generation());result.set("request_parameters",parameters.deepCopy());return result;}
    }
    private record Snapshot(TaskInvocationRecords.Binding binding,JsonNode body,JsonNode model,String authorization,FormalInvocationPricing.Schedule price,String credential,String credentialRevision,JsonNode sendAuthorizations){}
    public final class Prepared {
        private final HttpServletRequest http;private final String token;private final RunTokenAuthority.ActionRequest action;
        private final TaskInvocationRecords.Binding binding;private final JsonNode body,model,sendAuthorizations;private final String bodyHash,authorization;
        private final FormalInvocationPricing.Schedule price;private final String credential,credentialRevision;
        private Prepared(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,Snapshot snapshot)throws Exception {
            this.http=http;this.token=token;this.action=action;binding=snapshot.binding();body=snapshot.body().deepCopy();bodyHash=schema.hash(body);
            model=snapshot.model().deepCopy();sendAuthorizations=snapshot.sendAuthorizations().deepCopy();authorization=snapshot.authorization();price=snapshot.price();
            credential=snapshot.credential();credentialRevision=snapshot.credentialRevision();
        }
        TaskInvocationRecords.Binding binding(){return binding;}JsonNode request()throws Exception{return schema.parse(binding.request());}
        JsonNode model(){return model.deepCopy();}FormalInvocationPricing.Schedule price(){return price;}
        String credentialReferenceId(){return credential;}String credentialRevision(){return credentialRevision;}String authorizationSha256(){return authorization;}
        public JsonNode providerBody(){return body.deepCopy();}public String canonicalProviderBodySha256(){return bodyHash;}
        public void requireCurrentOutside()throws Exception {outside();workers.executeAction(http,token,action,(a,w)->ThrowingSupplier.of(()->{requireCurrent(a,w);return null;}).get());}
        public void requireCurrent(EnterpriseAccess.Admission a,RunTokenAuthority.Admission worker)throws Exception {
            var current=contexts.requireCurrent(a,binding.context());var fresh=records.bind(a,current,action.actionId());requireModelIdentity(a,worker,action,current,fresh);
            if(!binding.request().equals(fresh.request())||!binding.dependencies().equals(fresh.dependencies())||!authorization.equals(current.authorizationSha256())
                    ||!model.equals(schema.parse(schema.canonical(current.model())))||!body.equals(body(current))||!FormalTaskNodeAuthority.sameEvidence(sendAuthorizations,current.sendAuthorizations()))stale();
        }
        JsonNode sendAuthorizations(){return sendAuthorizations.deepCopy();}
        JsonNode outputCommitment(EnterpriseAccess.Admission a,String invocation)throws Exception {
            var current=contexts.requireCurrent(a,binding.context());return contexts.outputCommitment(a,current,invocation);
        }
    }
    private static JsonNode body(TaskInvocationContext.Registered current)throws Exception {
        var fixed=current.model();if(!(fixed.definition() instanceof ModelConfigurationProtocol.Definition))denied();
        return current.providerBody();
    }
    private static void requireModelAction(RunTokenAuthority.ActionRequest action){if(action==null||!"MODEL_CALL".equals(action.kind())||!"model.send".equals(action.action())||!"CONFIGURATION".equals(action.scope().resource_kind()))denied();}
    private static void requireModelIdentity(EnterpriseAccess.Admission a,RunTokenAuthority.Admission worker,RunTokenAuthority.ActionRequest action,TaskInvocationContext.Registered current,TaskInvocationRecords.Binding binding)throws Exception {
        requireModelAction(action);var request=current.request();var node=request.node();var model=current.model();
        if(!a.formalWorker()||!"TASK".equals(a.formalProof().identity().executionKind())||!binding.actor().equals(a.actorId())
                ||!node.tenant_id().equals(worker.tenantId())||!node.project_id().equals(worker.projectId())||!node.run_id().equals(worker.runId())
                ||!node.principal_id().equals(worker.principalId())||!node.step_id().equals(worker.stepId())||!node.lease_generation().equals(worker.leaseGeneration().toString())
                ||!node.worker_service_uri().equals(worker.serviceUri())||!actionId(current.contextId()).equals(action.actionId())
                ||!action.actionId().equals(worker.actionId())||!node.node_input_sha256().equals(action.inputSha256())||!binding.requestHash().equals(action.argumentsSha256())
                ||!request.model_config_revision().equals(action.toolVersion())||!request.business_purpose().equals(action.purpose())
                ||!new PermissionProtocol.Scope(node.tenant_id(),model.project_id(),"CONFIGURATION",model.config_id()).equals(action.scope())
                ||!worker.delegatedPermissions().contains(new RunTokenAuthoritySchema.Permission(action.scope(),"model.send",request.business_purpose())))denied();
        current.requireCurrent(a);
    }
    public FormalInvocationReservations.Reserved reserve(Prepared prepared)throws Exception {
        outside();return workers.executeAction(prepared.http,prepared.token,prepared.action,(a,w)->ThrowingSupplier.of(()->reservations.reserveTask(a,w,prepared)).get());
    }
    public Optional<FormalInvocationDispatch.Permit> beginDispatch(Prepared prepared,String invocation)throws Exception {
        outside();return workers.executeAction(prepared.http,prepared.token,prepared.action,(a,w)->ThrowingSupplier.of(()->dispatch.beginTask(prepared.http,a,w,invocation,prepared)).get());
    }
    public JsonNode cancel(Prepared prepared,String invocation,String key,String reason)throws Exception {
        outside();if(cancellation==null)unavailable();return workers.executeAction(prepared.http,prepared.token,prepared.action,(a,w)->ThrowingSupplier.of(()->{
            prepared.requireCurrent(a,w);var actual=records.byInvocation(a.context().tenant_id(),invocation);
            if(!actual.action().equals(prepared.binding.action())||!actual.context().equals(prepared.binding.context())||!actual.requestHash().equals(prepared.binding.requestHash()))denied();
            return cancellation.cancelTask(a,actual,invocation,key,reason);
        }).get());
    }
    public FormalInvocationSettlement.Recorded recordOutcome(HttpServletRequest http,String tenant,String actor,String invocation,byte[] result,byte[] capture)throws Exception {
        outside();return receiptObjects.recordTask(http,tenant,actor,invocation,result,capture,settlement::record);
    }

    public JsonNode status(TaskProfessionalRuntimeService.Session session,String invocation)throws Exception {
        session.requireUnchanged();WireValues.uuid(invocation);var a=session.admission();var binding=records.byInvocation(a.context().tenant_id(),invocation);requireSession(binding.node(),session);contexts.requireResultRead(a,binding.original());
        var row=one(db.query("SELECT status,settlement_status FROM invocation WHERE tenant_id=? AND invocation_id=? AND run_id=? AND step_id=? AND action_id=? AND created_by=? LIMIT 2",(r,n)->List.of(r.getString(1),r.getString(2)),binding.tenant(),invocation,binding.run(),binding.step(),binding.action(),binding.actor()));
        var status=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("invocation_id",invocation).put("origin_action_id",binding.action()).put("context_manifest_id",binding.context()).put("model_config_revision",binding.model()).put("execution_status",row.get(0)).put("settlement_status",row.get(1));
        status.putNull("provider_request_id").putNull("result_artifact_id").putNull("result_sha256").putNull("failure_code");
        if("SETTLED".equals(row.get(1))&&Set.of("SUCCEEDED","FAILED","CANCELLED").contains(row.get(0))){
            var result=resultHistory.verifiedAnyResult(binding,invocation);var object=objects.registered(binding.tenant(),invocation,"MODEL_RESULT").orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
            status.set("provider_request_id",result.output().required("provider_request_id"));status.put("result_artifact_id",object.artifact().artifactId()).put("result_sha256",object.artifact().sha256());
            if("error".equals(result.output().required("reason").path("kind").asText()))status.set("failure_code",result.output().required("reason").required("failure").required("code"));
        }
        SchemaCatalogue.fromClasspath().validate("model-stream",status,"InvocationStatus");session.requireUnchanged();contexts.requireResultRead(a,binding.original());return status;
    }
    public JsonNode readResult(TaskProfessionalRuntimeService.Session session,String artifact)throws Exception {
        session.requireUnchanged();WireValues.uuid(artifact);var a=session.admission();String invocation=one(db.query("SELECT invocation_id FROM formal_invocation_registered_object WHERE tenant_id=? AND artifact_id=? AND kind='MODEL_RESULT' LIMIT 2",(r,n)->r.getString(1),a.context().tenant_id(),artifact));
        var status=status(session,invocation);if(!artifact.equals(status.required("result_artifact_id").textValue()))denied();var binding=records.byInvocation(a.context().tenant_id(),invocation);var result=resultHistory.verifiedAnyResult(binding,invocation).output();
        if(!WireValues.sha256(ContentReadAuthorization.canonicalBytes(result)).equals(status.required("result_sha256").textValue()))bad();contexts.requireResultRead(a,binding.original());return result.deepCopy();
    }
    public String contextForInvocation(TaskProfessionalRuntimeService.Session session,String invocation)throws Exception {
        session.requireUnchanged();var binding=records.byInvocation(session.admission().context().tenant_id(),invocation);requireSession(binding.node(),session);contexts.requireResultRead(session.admission(),binding.original());return binding.context();
    }
    public Terminal settledForSession(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed supervisor)throws Exception {
        session.requireUnchanged();supervisor.requireUnchanged();if(!"CLOSED".equals(session.state())||!session.sessionId().equals(supervisor.sessionId())||!session.binding().equals(supervisor.binding()))denied();
        var evidence=results(session,true);return new Terminal(session,supervisor,evidence);
    }
    /** A real final ask-user tool result is not a model stop or an EvidenceBundle.
     * This separate proof never changes Terminal's existing final-text requirement. */
    public WaitingTerminal settledWaitingForSession(TaskProfessionalRuntimeService.Session session,
            TaskProfessionalSupervisor.Closed supervisor,String callId,JsonNode arguments)throws Exception {
        return new WaitingTerminal(session,supervisor,callId,arguments,waitingResults(session,supervisor,callId,arguments));
    }
    private Results waitingResults(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed supervisor,
            String callId,JsonNode arguments)throws Exception {
        session.requireUnchanged();supervisor.requireUnchanged();supervisor.requireSuccessfulExit();
        if(!"CLOSED".equals(session.state())||!session.sessionId().equals(supervisor.sessionId())
                ||!FormalTaskNodeAuthority.sameEvidence(session.binding(),supervisor.binding()))denied();
        var evidence=results(session,false);
        requireWaitingResult(evidence.results(),callId,arguments,schema);
        session.requireUnchanged();supervisor.requireUnchanged();return evidence;
    }
    static void requireWaitingResult(List<JsonNode> results,String callId,JsonNode arguments,FormalInvocationSchema schema)throws Exception {
        if(results==null||results.isEmpty()||results.size()>1024)denied();
        var unique=new HashSet<String>();int asking=0;
        for(var result:results){if(!"tool-calls".equals(text(result.required("reason"),"kind")))denied();for(var block:result.required("blocks"))if("tool-call".equals(text(block,"type"))){if(!unique.add(text(block,"id")))denied();if("ask_user_question".equals(text(block,"name")))asking++;}}
        if(asking!=1)denied();
        var last=results.get(results.size()-1);var blocks=last.required("blocks");
        // Initial bounded path: no concurrent sibling tool whose completion might be dropped.
        if(!blocks.isArray()||blocks.size()!=1||!"tool-call".equals(text(blocks.get(0),"type")))denied();
        verifyToolCall(List.of(last),callId,"ask_user_question",arguments,schema);
    }
    /** Exclusive final ask admission, before any waiting write. Earlier/unsettled or
     * sibling tool calls cannot leave falsely usable waiting work. */
    public WaitingToolCall waitingToolCall(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervisor,String id,JsonNode arguments)throws Exception {
        var actual=toolCall(session,supervisor,id,"ask_user_question",arguments);var evidence=results(session,false);requireWaitingResult(evidence.results(),id,arguments,schema);
        return new WaitingToolCall(session,actual,id,arguments,evidence);
    }
    public final class WaitingToolCall {
        private final TaskProfessionalRuntimeService.Session session;private final ToolCall actual;private final String id;private final JsonNode arguments;private final Results original;
        private WaitingToolCall(TaskProfessionalRuntimeService.Session session,ToolCall actual,String id,JsonNode arguments,Results original){this.session=session;this.actual=actual;this.id=id;this.arguments=arguments.deepCopy();this.original=original;}
        public void requireUnchanged()throws Exception{actual.requireUnchanged();var now=results(session,false);requireWaitingResult(now.results(),id,arguments,schema);if(!FormalTaskNodeAuthority.sameEvidence(original.pins(),now.pins())||!FormalTaskNodeAuthority.sameEvidence(original.output(),now.output()))stale();}
        public JsonNode canonicalPins()throws Exception{requireUnchanged();return actual.canonicalPins();}
    }
    public final class WaitingTerminal {
        private final TaskProfessionalRuntimeService.Session session;private final TaskProfessionalSupervisor.Closed closure;
        private final String callId;private final JsonNode arguments;private final Results original;
        private WaitingTerminal(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed closure,
                String callId,JsonNode arguments,Results original){this.session=session;this.closure=closure;this.callId=callId;this.arguments=arguments.deepCopy();this.original=original;}
        public void requireUnchanged()throws Exception {
            var fresh=waitingResults(session,closure,callId,arguments);
            if(!FormalTaskNodeAuthority.sameEvidence(original.pins(),fresh.pins())
                    ||!FormalTaskNodeAuthority.sameEvidence(original.output(),fresh.output()))stale();
        }
        public JsonNode canonicalPins()throws Exception{requireUnchanged();return original.pins().deepCopy();}
        public JsonNode canonicalCallPins()throws Exception{
            requireUnchanged();var result=(com.fasterxml.jackson.databind.node.ObjectNode)original.pins().get(original.pins().size()-1).deepCopy();
            return result.put("context_manifest_id",text(original.output(),"context_manifest_id")).put("tool_call_id",callId)
                    .put("tool_name","ask_user_question").put("arguments_sha256",schema.hash(arguments)).put("session_id",session.sessionId())
                    .put("supervision_receipt_id",text(session.launch(),"isolation_receipt_id"))
                    .put("supervision_sha256",text(session.launch(),"supervision_sha256"));
        }
        public void requireFor(TaskProfessionalRuntimeService.Session current,TaskProfessionalSupervisor.Closed closed)throws Exception{
            requireUnchanged();current.requireUnchanged();closed.requireUnchanged();
            if(current.admission()!=session.admission()||!current.sessionId().equals(session.sessionId())
                    ||!FormalTaskNodeAuthority.sameEvidence(current.binding(),session.binding())
                    ||!closed.receiptId().equals(closure.receiptId())||!closed.sha256().equals(closure.sha256()))denied();
        }
    }
    public final class Terminal {
        private final TaskProfessionalRuntimeService.Session session;private final TaskProfessionalSupervisor.Closed supervisor;private final Results evidence;
        private Terminal(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed supervisor,Results evidence){this.session=session;this.supervisor=supervisor;this.evidence=evidence;}
        public void requireUnchanged()throws Exception {session.requireUnchanged();supervisor.requireUnchanged();var fresh=results(session,true);if(!evidence.pins().equals(fresh.pins())||!evidence.output().equals(fresh.output()))stale();}
        public void requireFor(TaskProfessionalRuntimeService.Session current,TaskProfessionalSupervisor.Closed closure)throws Exception {
            requireUnchanged();current.requireUnchanged();closure.requireUnchanged();
            if(session.admission()!=current.admission()||!session.sessionId().equals(current.sessionId())||!FormalTaskNodeAuthority.sameEvidence(session.binding(),current.binding())
                ||!supervisor.receiptId().equals(closure.receiptId())||!supervisor.sha256().equals(closure.sha256())||!closure.sessionId().equals(current.sessionId())||!FormalTaskNodeAuthority.sameEvidence(closure.binding(),current.binding()))denied();
        }
        public List<String> invocationIds()throws Exception{requireUnchanged();return evidence.ids();}
        public JsonNode canonicalPins()throws Exception{requireUnchanged();return evidence.pins().deepCopy();}
        public JsonNode output()throws Exception{requireUnchanged();return evidence.output().deepCopy();}
        public boolean terminal()throws Exception{requireUnchanged();return true;}
        public void requireToolCall(String id,String name,JsonNode arguments)throws Exception{requireUnchanged();verifyToolCall(evidence.results(),id,name,arguments,schema);}
    }
    /** Latest actual captured model request, never tool-call provenance supplied by host. */
    public ToolCall toolCall(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervisor,String id,String name,JsonNode arguments)throws Exception {
        var pins=toolCallPins(session,supervisor,null,id,name,arguments);return new ToolCall(session,supervisor,id,name,arguments,pins);
    }
    public void requireToolCall(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervisor,String id,String name,JsonNode arguments)throws Exception {toolCall(session,supervisor,id,name,arguments).requireUnchanged();}
    public final class ToolCall {
        private final TaskProfessionalRuntimeService.Session session;private final TaskProfessionalSupervisor.Admitted supervision;private final String id,name;private final JsonNode arguments,pins;
        private ToolCall(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervision,String id,String name,JsonNode arguments,JsonNode pins){this.session=session;this.supervision=supervision;this.id=id;this.name=name;this.arguments=arguments.deepCopy();this.pins=pins.deepCopy();}
        public void requireUnchanged()throws Exception {if(!FormalTaskNodeAuthority.sameEvidence(pins,toolCallPins(session,supervision,text(pins,"invocation_id"),id,name,arguments)))stale();}
        public JsonNode canonicalPins()throws Exception {requireUnchanged();return pins.deepCopy();}
    }
    public ToolCall originalToolCall(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervisor,String invocation,String id,String name,JsonNode arguments)throws Exception {WireValues.uuid(invocation);return new ToolCall(session,supervisor,id,name,arguments,toolCallPins(session,supervisor,invocation,id,name,arguments));}
    private JsonNode toolCallPins(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervisor,String invocation,String id,String name,JsonNode arguments)throws Exception {
        session.requireUnchanged();supervisor.requireUnchanged();if(!"OPEN".equals(session.state())||!session.sessionId().equals(supervisor.sessionId())||!FormalTaskNodeAuthority.sameEvidence(session.binding(),supervisor.binding()))denied();
        JsonNode output,pin;if(invocation==null){var current=results(session,false);output=current.output();pin=current.pins().get(current.pins().size()-1);}else{var binding=records.byInvocation(session.input().tenantId(),invocation);requireSession(binding.node(),session);contexts.requireResultRead(session.admission(),binding.original());var actual=resultHistory.verifiedResult(binding,invocation);output=actual.output();pin=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("invocation_id",actual.id()).put("accounting_sha256",actual.accounting()).put("result_registration_sha256",actual.result()).put("capture_registration_sha256",actual.capture());}verifyToolCall(List.of(output),id,name,arguments,schema);
        var result=((com.fasterxml.jackson.databind.node.ObjectNode)pin.deepCopy()).put("context_manifest_id",text(output,"context_manifest_id")).put("tool_call_id",id).put("tool_name",name).put("arguments_sha256",schema.hash(arguments)).put("session_id",session.sessionId()).put("supervision_receipt_id",supervisor.receiptId()).put("supervision_sha256",supervisor.sha256());
        session.requireUnchanged();supervisor.requireUnchanged();return result;
    }
    private record Results(List<String> ids,JsonNode pins,List<JsonNode> results,JsonNode output){}
    private Results results(TaskProfessionalRuntimeService.Session session,boolean requireFinal)throws Exception {
        session.requireUnchanged();var a=session.admission();var input=session.input();
        var ids=db.query("SELECT invocation_id FROM invocation WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? AND created_by=? ORDER BY invocation_id LIMIT 1025",
                (r,n)->r.getString(1),input.tenantId(),input.projectId(),session.runId(),session.stepId(),session.generation(),a.actorId());
        if(ids.isEmpty()||ids.size()>1024)unavailable();var ordered=new TreeMap<Long,TaskInvocationResults.Result>();
        for(String invocation:ids){
            var binding=records.byInvocation(input.tenantId(),invocation);requireSession(binding.node(),session);contexts.requireResultRead(a,binding.original());
            var result=resultHistory.verifiedResult(binding,invocation);
            if(ordered.put(binding.original().modelRequestOrdinal(),result)!=null)bad();
        }
        long ordinal=1;for(long actual:ordered.keySet())if(actual!=ordinal++)bad();
        var values=List.copyOf(ordered.values());if(requireFinal&&(!"stop".equals(text(values.get(values.size()-1).output().required("reason"),"kind"))
                ||values.subList(0,values.size()-1).stream().anyMatch(r->!"tool-calls".equals(r.output().required("reason").required("kind").textValue()))))unavailable();
        ArrayNode pins=new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode();for(var result:values)pins.addObject().put("invocation_id",result.id()).put("accounting_sha256",result.accounting()).put("result_registration_sha256",result.result()).put("capture_registration_sha256",result.capture());
        session.requireUnchanged();return new Results(values.stream().map(TaskInvocationResults.Result::id).toList(),pins,values.stream().map(TaskInvocationResults.Result::output).toList(),values.get(values.size()-1).output());
    }
    /** Compatibility entry; wiring of graph consumers uses independent TaskInvocationResults. */
    public JsonNode historicalTerminalPins(TaskResearchCompletionAuthority.Input input,List<String> ids)throws Exception{return resultHistory.historicalTerminalPins(input,ids);}
    static void verifyToolCall(List<JsonNode> results,String id,String name,JsonNode arguments,FormalInvocationSchema schema)throws Exception {
        if(id==null||id.isBlank()||name==null||name.isBlank()||arguments==null||!arguments.isObject())denied();int matches=0;
        for(var result:results)for(var block:result.required("blocks"))if("tool-call".equals(block.path("type").asText())&&id.equals(block.path("id").asText())){
            if(!"tool-calls".equals(result.required("reason").required("kind").textValue())||!name.equals(text(block,"name"))
                    ||!schema.canonical(arguments).equals(schema.canonical(schema.parse(text(block,"arguments")))))denied();matches++;
        }
        if(matches!=1)denied();
    }
    private static void requireSession(TaskInvocationProtocol.Node node,TaskProfessionalRuntimeService.Session session)throws Exception {
        if(!node.session_id().equals(session.sessionId())||!node.run_id().equals(session.runId())||!node.step_id().equals(session.stepId())||!node.host_action_id().equals(session.actionId())
                ||!node.lease_generation().equals(session.generation())||!node.worker_service_uri().equals(session.workerService())||!node.node_input_artifact_id().equals(session.input().inputId())
                ||!node.node_input_sha256().equals(session.input().inputSha256())||!node.node_input_receipt_sha256().equals(session.input().receiptSha256()))denied();
    }
    private static String text(JsonNode n,String key){var value=n.required(key);if(!value.isTextual())bad();return value.textValue();}
    private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}
    private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())denied();}
    private static void changed(int count){if(count!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}private static void unavailable(){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
}
