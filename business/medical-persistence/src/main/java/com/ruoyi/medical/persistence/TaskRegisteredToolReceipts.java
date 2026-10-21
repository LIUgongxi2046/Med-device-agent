package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.medical.persistence.TaskRegisteredToolAuthority.*;

/** Immutable server receipts for the registered tool adapters. No invocation-service
 * dependency: model history can verify original reads without an authority cycle. */
public final class TaskRegisteredToolReceipts {
    private ApprovedWebAcquisitionService web;
    @org.springframework.beans.factory.annotation.Autowired void approvedWeb(ApprovedWebAcquisitionService web){this.web=web;}
    static final String ACTION="task.registered_tool.result",START="task.research.tool.started";
    private final JdbcTemplate db;private final PermissionSchema json;private final TaskRegisteredToolSchema schema;
    private final SchemaCatalogue catalogue;private final TaskRegisteredToolAuthority authority;
    private final TaskProfessionalPredecessorEvidence predecessors;private final TaskInvocationPackages packages;
    private final TaskRegisteredToolWaitingService waiting;private final RunTokenAuthoritySchema actions;
    private final FormalRuntimeCapabilityIdentityAuthority capabilities;private final FormalCapabilityIdentityAuthority roles;
    private final TaskProfessionalObjectRegistration objects;private final TaskProfessionalSupervisor supervisor;
    private final TaskProfessionalRuntimeService.Deployment deployment;
    public TaskRegisteredToolReceipts(DataSource data,PermissionSchema json,TaskRegisteredToolAuthority authority,
            TaskProfessionalPredecessorEvidence predecessors,TaskInvocationPackages packages,TaskRegisteredToolWaitingService waiting,
            RunTokenAuthoritySchema actions,FormalRuntimeCapabilityIdentityAuthority capabilities,FormalCapabilityIdentityAuthority roles,
            TaskProfessionalObjectRegistration objects,TaskProfessionalSupervisor supervisor,TaskProfessionalRuntimeService.Deployment deployment)throws Exception {
        this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.authority=authority;this.predecessors=predecessors;
        this.packages=packages;this.waiting=waiting;this.actions=actions;this.capabilities=capabilities;this.roles=roles;this.objects=objects;
        this.supervisor=supervisor;this.deployment=deployment;schema=new TaskRegisteredToolSchema();catalogue=SchemaCatalogue.fromClasspath();
    }
    void start(TaskRegisteredToolAuthority.Checked current)throws Exception {
        current.requireUnchanged();var s=current.session();var input=s.input();String call=text(current.request(),"call_id");
        String started=id("TASK_REGISTERED_TOOL_STARTED",s.sessionId(),call);var body=base(current,started,"TASK_REGISTERED_TOOL_STARTED");
        var document=document(body);var existing=read(s.admission(),input.projectId(),s.runId(),started,START,current.classification(),started,false);
        if(existing==null) {
            var count=one(db.query("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND project_id=? AND trace_id=? AND action=?",
                    (r,n)->r.getBigDecimal(1).toBigIntegerExact(),input.tenantId(),input.projectId(),s.runId(),START));
            if(current.maximumCalls().signum()<1||count.compareTo(current.maximumCalls())>=0)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            persist(current,started,START,started,document);
        } else same(document,existing);
        bindAction(current,true);current.requireUnchanged();
    }
    JsonNode save(TaskRegisteredToolAuthority.Checked current,TaskRegisteredToolBridge.Output output)throws Exception {
        var old=recover(current);if(old!=null)return old;requireStarted(current);var s=current.session();
        String receipt=receiptId(s.sessionId(),text(current.request(),"call_id")),scope=hash(current.request().required("arguments"));
        var response=json.object().put("schema_version","1.0.0").put("action_id",current.actionId()).put("tool_id",current.tool())
                .put("status","AT-08".equals(current.tool())?"WAITING_INPUT":"SUCCEEDED").put("artifact_id",output.artifact)
                .put("resource_revision",output.revision).put("summary","AT-08".equals(current.tool())?
                        "Question saved for the frozen responsible owner; no approval or continuation":"AT-09".equals(current.tool())?"Captured candidates await rights and source approval; not formal evidence":"Exact authorized content read; not a completion determination")
                .putNull("next_cursor");
        response.putObject("coverage").put("requested_scope_sha256",scope).put("delivered_scope_sha256",scope).put("complete",true);
        response.set("payload",output.payload);catalogue.validate("tool-success",response,null);
        var body=base(current,receipt,"TASK_REGISTERED_TOOL_RESULT");body.put("response_sha256",hash(response));
        body.set("response",response);body.set("physical",output.physical);
        validateResponse(body);requirePhysical(current,body);persist(current,receipt,ACTION,current.actionId(),document(body));
        return Objects.requireNonNull(recover(current));
    }
    JsonNode recover(TaskRegisteredToolAuthority.Checked current)throws Exception {
        current.requireUnchanged();var s=current.session();String receipt=receiptId(s.sessionId(),text(current.request(),"call_id"));
        var original=read(s.admission(),s.input().projectId(),s.runId(),receipt,ACTION,current.classification(),current.actionId(),false);
        if(original==null)return null;requireStarted(current);var body=original.required("body");
        var expected=base(current,receipt,"TASK_REGISTERED_TOOL_RESULT");
        for(var fields=expected.fields();fields.hasNext();) {var field=fields.next();same(field.getValue(),body.required(field.getKey()));}
        validateResponse(body);requirePhysical(current,body);current.requireUnchanged();return body.required("response").deepCopy();
    }
    private void requirePhysical(TaskRegisteredToolAuthority.Checked current,JsonNode body)throws Exception {
        var physical=body.required("physical");String kind=text(physical,"kind");var s=current.session();
        if("AT-03".equals(current.tool())) {
            ProfessionalRuntimeObservations.closed(physical,Set.of("kind","pins"));same("PREDECESSOR_READ",kind);
            predecessors.requireCurrentPins(s.admission(),s.input(),physical.required("pins"),null,null);
            verifyPredecessorOriginal(s.admission(),s.input().manifest(),body);
        } else if("AT-04".equals(current.tool())) {
            ProfessionalRuntimeObservations.closed(physical,Set.of("kind","pins"));same("SKILL_READ",kind);
            packages.requireReviewedPins(s.admission(),s.input().projectId(),physical.required("pins"));
            same(json.tree(current.content()),physical.required("pins").required("artifact"));
            verifySkillContent(body);
        } else if("AT-09".equals(current.tool())) {
            ProfessionalRuntimeObservations.closed(physical,Set.of("kind","witness"));same("WEB_ACQUISITION",kind);
            if(web==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            web.requireToolReceipt(current,physical.required("witness"));
            ApprovedWebAcquisitionService.requireOriginalReceipt(db,json,s.input().tenantId(),s.input().projectId(),s.admission().actorId(),body);
        } else {
            ProfessionalRuntimeObservations.closed(physical,Set.of("kind","witness"));same("WAITING_ITEM",kind);
            waiting.requireCurrent(current,physical.required("witness"));
            same(text(physical.required("witness"),"waiting_item_id"),text(body.required("response").required("payload"),"resource_id"));
        }
    }
    private void validateResponse(JsonNode body)throws Exception {
        schema.validate("Result",body);String tool=tool(text(body.required("request"),"name"));
        validateRequest(tool,body.required("request"),catalogue);var response=body.required("response");catalogue.validate("tool-success",response,null);
        same(hash(response),text(body,"response_sha256"));same(tool,text(response,"tool_id"));
        same(id("TASK_REGISTERED_TOOL_ACTION",text(body,"session_id"),text(body.required("request"),"call_id")),text(response,"action_id"));
        same("AT-08".equals(tool)?"WAITING_INPUT":"SUCCEEDED",text(response,"status"));
        String scope=hash(body.required("request").required("arguments"));same(scope,text(response.required("coverage"),"requested_scope_sha256"));
        same(scope,text(response.required("coverage"),"delivered_scope_sha256"));
        if(!response.required("coverage").required("complete").booleanValue()||!response.required("next_cursor").isNull())bad();
        var payload=response.required("payload");
        if("AT-08".equals(tool)){
            same("RECEIPT",text(payload,"kind"));same("WAITING_ITEM",text(payload,"resource_kind"));same("WAITING_INPUT",text(payload,"resource_state"));
            same(text(body,"input_id"),text(response,"artifact_id"));if(!response.required("resource_revision").isNull()||!payload.required("check_ids").isEmpty())bad();
        } else if("AT-09".equals(tool)) {
            same("RECEIPT",text(payload,"kind"));same("WEB_ACQUISITION",text(payload,"resource_kind"));same("CAPTURED_PENDING_REVIEW",text(payload,"resource_state"));
            same(text(body,"input_id"),text(response,"artifact_id"));same(text(body.required("request").required("arguments"),"approved_query_revision"),text(response,"resource_revision"));
            same(text(body.required("physical").required("witness"),"acquisition_id"),text(payload,"resource_id"));if(!payload.required("check_ids").isEmpty())bad();
        } else {
            same("AT-03".equals(tool)?"ARTIFACT":"SKILL",text(payload,"kind"));
            same(WireValues.sha256(text(payload,"content").getBytes(StandardCharsets.UTF_8)),text(payload,"content_sha256"));
            same(text(body,"receipt_id"),text(payload,"read_receipt_id"));
            if("AT-03".equals(tool))same(text(body.required("request").required("arguments"),"artifact_id"),text(response,"artifact_id"));
            else same(text(body.required("request").required("arguments"),"skill_version"),text(response,"resource_revision"));
        }
    }
    private void verifySkillContent(JsonNode body)throws Exception {
        var pins=body.required("physical").required("pins");var skill=body.required("authority").required("skill");
        same(skill.required("definition").required("request").required("source_artifact"),pins.required("artifact"));
        var payload=body.required("response").required("payload");same(text(pins.required("artifact"),"version_id"),text(body.required("response"),"artifact_id"));
        same(text(pins.required("artifact"),"content_sha256"),text(payload,"content_sha256"));
        same(text(pins.required("object_ref"),"mediaType"),text(payload,"media_type"));
        if("application/json".equals(text(payload,"media_type"))) {
            same(text(pins,"schema_ref"),text(payload,"content_schema_ref"));
            catalogue.bindPublished(text(pins,"schema_ref"),text(pins,"schema_sha256"),text(payload,"content").getBytes(StandardCharsets.UTF_8));
        } else if(!payload.required("content_schema_ref").isNull())bad();
    }
    private void verifyPredecessorOriginal(EnterpriseAccess.Admission a,JsonNode input,JsonNode body)throws Exception {
        var predecessor=exactPredecessor(input,text(body.required("request").required("arguments"),"artifact_id"));
        same(predecessor,body.required("authority").required("predecessor"));var actual=objects.readOriginalProduced(a,text(predecessor,"evidence_artifact_id"),"EVIDENCE_BUNDLE",supervisor,deployment);
        actual.requireUnchanged();TaskProfessionalPredecessorEvidence.requireOriginal(input,a.actorId(),predecessor,actual.receipt(),actual.reference(),actual.receiptSha256());
        var payload=body.required("response").required("payload");same("application/json",text(payload,"media_type"));
        same(catalogue.schemaRef("artifacts","EvidenceBundle"),text(payload,"content_schema_ref"));
        same(text(predecessor,"evidence_sha256"),text(payload,"content_sha256"));same(text(predecessor,"evidence_artifact_id"),text(body.required("response"),"resource_revision"));
        if(!Arrays.equals(actual.bytes(),text(payload,"content").getBytes(StandardCharsets.UTF_8)))bad();
        catalogue.validate("artifacts",schema.parse(text(payload,"content")),"EvidenceBundle");
    }
    private ObjectNode base(TaskRegisteredToolAuthority.Checked c,String id,String kind){
        var s=c.session();var i=s.input();var result=json.object().put("schema_version","1.0.0").put("kind",kind).put("receipt_id",id)
                .put("tenant_id",i.tenantId()).put("project_id",i.projectId()).put("actor_id",s.admission().actorId()).put("run_id",s.runId())
                .put("step_id",s.stepId()).put("session_id",s.sessionId()).put("input_id",i.inputId()).put("input_sha256",i.inputSha256())
                .put("input_receipt_sha256",i.receiptSha256()).put("classification",c.classification());
        result.set("binding",s.binding());result.set("request",c.request());result.set("authority",c.canonicalPins());return result;
    }
    private JsonNode document(JsonNode body)throws Exception{var result=json.object().put("receipt_sha256",hash(body));result.set("body",body);schema.validate("Document",result);return result;}
    private void requireStarted(TaskRegisteredToolAuthority.Checked current)throws Exception {
        var s=current.session();String id=id("TASK_REGISTERED_TOOL_STARTED",s.sessionId(),text(current.request(),"call_id"));
        same(document(base(current,id,"TASK_REGISTERED_TOOL_STARTED")),read(s.admission(),s.input().projectId(),s.runId(),id,START,current.classification(),id,true));
        bindAction(current,false);
    }
    private void bindAction(TaskRegisteredToolAuthority.Checked current,boolean create)throws Exception {
        var s=current.session();var i=s.input();var call=current.canonicalPins().required("tool_call");
        var origin=one(db.queryForList("SELECT action_id FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND invocation_id=? AND kind='MODEL_CALL' AND lease_generation=? LIMIT 2 FOR UPDATE",
                i.tenantId(),i.projectId(),s.runId(),s.stepId(),text(call,"invocation_id"),s.generation()));
        var rows=db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",
                i.tenantId(),i.projectId(),s.runId(),s.stepId(),current.actionId());
        String version=text(current.canonicalPins().required("tool_version"),"version_id"),arguments=text(current.request(),"arguments_sha256");
        if(rows.isEmpty()) {
            if(!create)bad();var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
            changed(db.update("INSERT INTO step_action_binding(tenant_id,project_id,run_id,step_id,action_id,input_sha256,origin_run_id,origin_step_id,origin_action_id,kind,arguments_sha256,tool_version,state,lease_generation,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'BOUND',?,'1.0.0',?,?,?,?,1,?)",
                    i.tenantId(),i.projectId(),s.runId(),s.stepId(),current.actionId(),i.inputSha256(),s.runId(),s.stepId(),str(origin,"action_id"),current.tool(),arguments,version,s.generation(),actions.manifestSha256(),s.admission().actorId(),now,current.classification(),now));
            bindAction(current,false);return;
        }
        var row=one(rows);for(var pin:Map.ofEntries(Map.entry("input_sha256",i.inputSha256()),Map.entry("origin_run_id",s.runId()),Map.entry("origin_step_id",s.stepId()),
                Map.entry("origin_action_id",str(origin,"action_id")),Map.entry("kind",current.tool()),Map.entry("arguments_sha256",arguments),Map.entry("tool_version",version),
                Map.entry("state","BOUND"),Map.entry("lease_generation",s.generation()),Map.entry("created_by",s.admission().actorId()),
                Map.entry("schema_version","1.0.0"),Map.entry("schema_manifest_sha256",actions.manifestSha256()),Map.entry("classification",current.classification())).entrySet())same(pin.getValue(),str(row,pin.getKey()));
        if(row.get("invocation_id")!=null)bad();
    }
    private void persist(TaskRegisteredToolAuthority.Checked current,String id,String action,String requestId,JsonNode document)throws Exception {
        var s=current.session();var i=s.input();var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
        String canonical=json.canonical(document);
        changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',NULL,?,?,'1.0.0',?,?,?,?)",
                i.tenantId(),i.projectId(),id,s.admission().actorId(),action,canonical,requestId,s.runId(),schema.manifest(),s.admission().actorId(),now,current.classification()));
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",
                i.tenantId(),id,s.runId(),action,canonical,now));
    }
    JsonNode read(EnterpriseAccess.Admission a,String project,String run,String id,String action,String classification,String requestId,boolean required)throws Exception {
        return readIdentity(a.context().tenant_id(),a.actorId(),project,run,id,action,classification,requestId,required);
    }
    private JsonNode readIdentity(String tenant,String actor,String project,String run,String id,String action,String classification,String requestId,boolean required)throws Exception {
        var rows=db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action=? AND subject_id=? AND created_by=? AND result='SUCCESS' AND reason_code IS NULL AND trace_id=? AND request_id=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? AND created_at<=UTC_TIMESTAMP(6) LIMIT 2 FOR UPDATE",
                tenant,project,id,action,actor,actor,run,requestId,schema.manifest(),classification);
        if(rows.isEmpty()&&!required)return null;var row=one(rows);var value=schema.validate("Document",schema.parse(str(row,"resource_ref")));
        same(hash(value.required("body")),text(value,"receipt_sha256"));same(json.canonical(value),str(row,"resource_ref"));
        var event=one(db.queryForList("SELECT payload,occurred_at FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type=? LIMIT 2",
                tenant,id,run,action));
        same(value,schema.parse(str(event,"payload")));same(row.get("created_at"),event.get("occurred_at"));return value;
    }
    static String receiptId(String session,String call){return id("TASK_REGISTERED_TOOL_RESULT",session,call);}
    record Recorded(JsonNode response,JsonNode pin) {}
    void requireNoPendingQuestion(TaskProfessionalRuntimeService.Session session){
        var i=session.input();
        if(!db.queryForList("SELECT waiting_item_id FROM waiting_item WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND reason_code='ASK_USER_QUESTION' LIMIT 1",
                i.tenantId(),i.projectId(),i.runId(),i.stepId()).isEmpty())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
    }
    /** OrderedHistory/ToolCall is supplied by the factual C07 reader, never by the SDK. */
    Recorded history(TaskProfessionalRuntimeService.Session session,TaskRegisteredToolAuthority.CallProof call)throws Exception {
        call.requireUnchanged();var pins=call.canonicalPins();String tool=tool(text(pins,"tool_name"));
        // A question concludes this session's tool loop. It cannot be replayed into a new model request.
        if("AT-08".equals(tool))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        String id=receiptId(session.sessionId(),text(pins,"tool_call_id"));
        var row=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action=? AND subject_id=? AND created_by=? LIMIT 2",
                session.input().tenantId(),session.input().projectId(),id,ACTION,session.admission().actorId(),session.admission().actorId()));
        var original=schema.validate("Document",schema.parse(str(row,"resource_ref")));var body=original.required("body");
        var checked=authority.require(session,tool,body.required("request"),call);var response=recover(checked);if(response==null)bad();
        var pin=json.object().put("tool_receipt_id",id).put("tool_receipt_sha256",text(original,"receipt_sha256")).put("response_sha256",hash(response));
        pin.set("tool_call",pins);return new Recorded(response,pin);
    }
    /** Original immutable recovery is not a current content grant and never rereads network bytes. */
    void requireOriginalHistory(TaskResearchCompletionAuthority.Input input,JsonNode entry,JsonNode pin)throws Exception {
        input.requireUnchanged();var response=entry.required("response");String tool=text(response,"tool_id");
        if(!Set.of("AT-03","AT-04","AT-09").contains(tool))deny();
        var call=pin.required("tool_call");String callId=text(call,"tool_call_id"),id=receiptId(input.sessionId(),callId);
        same(id,text(pin,"tool_receipt_id"));same(callId,text(entry,"tool_call_id"));same(text(call,"invocation_id"),text(entry,"invocation_id"));
        var document=read(input.admission(),input.projectId(),input.runId(),id,ACTION,
                classification(input.admission(),input.projectId(),input.runId()),text(response,"action_id"),true);
        same(text(document,"receipt_sha256"),text(pin,"tool_receipt_sha256"));var body=document.required("body");
        validateResponse(body);same(response,body.required("response"));same(hash(response),text(pin,"response_sha256"));
        same(call,body.required("authority").required("tool_call"));same(callId,text(body.required("request"),"call_id"));
        same(input.tenantId(),text(body,"tenant_id"));same(input.projectId(),text(body,"project_id"));same(input.actorId(),text(body,"actor_id"));
        same(input.runId(),text(body,"run_id"));same(input.stepId(),text(body,"step_id"));same(input.sessionId(),text(body,"session_id"));
        same(input.inputId(),text(body,"input_id"));same(input.inputSha256(),text(body,"input_sha256"));same(input.receiptSha256(),text(body,"input_receipt_sha256"));
        var binding=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));
        same(input.actionId(),binding.action_id());same(input.generation(),binding.lease_generation());same(input.inputSha256(),binding.input_sha256());
        requireOriginalDefinitions(input.admission(),input.projectId(),input.manifest(),body);
        String start=id("TASK_REGISTERED_TOOL_STARTED",input.sessionId(),callId);
        var started=read(input.admission(),input.projectId(),input.runId(),start,START,text(body,"classification"),start,true).required("body");
        var expected=(ObjectNode)body.deepCopy();for(String field:List.of("response","response_sha256","physical"))expected.remove(field);
        expected.put("kind","TASK_REGISTERED_TOOL_STARTED").put("receipt_id",start);same(expected,started);
        if("AT-03".equals(tool))verifyPredecessorOriginal(input.admission(),input.manifest(),body);else if("AT-09".equals(tool))ApprovedWebAcquisitionService.requireOriginalReceipt(db,json,input.tenantId(),input.projectId(),input.actorId(),body);else verifySkillContent(body);
        requireOriginalAction(input.admission(),body);input.requireUnchanged();
    }
    JsonNode waitingRecord(TaskProfessionalRuntimeService.Session session)throws Exception {
        session.requireUnchanged();var i=session.input();
        var rows=db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND trace_id=? AND action=? AND subject_id=? AND JSON_UNQUOTE(JSON_EXTRACT(resource_ref,'$.body.session_id'))=? AND JSON_UNQUOTE(JSON_EXTRACT(resource_ref,'$.body.response.tool_id'))='AT-08' LIMIT 2 FOR UPDATE",i.tenantId(),i.projectId(),i.runId(),ACTION,session.admission().actorId(),session.sessionId());
        if(rows.isEmpty())return null;var document=schema.validate("Document",schema.parse(str(one(rows),"resource_ref")));
        var body=document.required("body");var actual=read(session.admission(),i.projectId(),i.runId(),text(body,"receipt_id"),ACTION,text(body,"classification"),text(body.required("response"),"action_id"),true);same(document,actual);validateResponse(body);return document;
    }
    void requireWaitingOriginal(EnterpriseAccess.Admission a,JsonNode input,JsonNode document)throws Exception {
        requireWaitingIdentity(a.context().tenant_id(),a.actorId(),input,document);
    }
    void requireQuestionOriginal(TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion q,JsonNode input,JsonNode document)throws Exception {
        q.requireUnchanged();requireWaitingIdentity(q.tenantId(),q.originalActorId(),input,document);
        same(q.creation(),document.required("body").required("physical").required("witness"));q.requireUnchanged();
    }
    private void requireWaitingIdentity(String tenant,String actor,JsonNode input,JsonNode document)throws Exception {
        schema.validate("Document",document);same(hash(document.required("body")),text(document,"receipt_sha256"));var body=document.required("body");validateResponse(body);same("AT-08",text(body.required("response"),"tool_id"));
        same(document,readIdentity(tenant,actor,text(body,"project_id"),text(body,"run_id"),text(body,"receipt_id"),ACTION,text(body,"classification"),text(body.required("response"),"action_id"),true));
        same(hash(input),text(body,"input_sha256"));same(tenant,text(body,"tenant_id"));same(actor,text(body,"actor_id"));
        requireOriginalDefinitions(tenant,actor,text(body,"project_id"),input,body);requireOriginalAction(tenant,actor,body);
        String start=id("TASK_REGISTERED_TOOL_STARTED",text(body,"session_id"),text(body.required("request"),"call_id"));
        var expected=(ObjectNode)body.deepCopy();for(String f:List.of("response","response_sha256","physical"))expected.remove(f);expected.put("kind","TASK_REGISTERED_TOOL_STARTED").put("receipt_id",start);
        same(expected,readIdentity(tenant,actor,text(body,"project_id"),text(body,"run_id"),start,START,text(body,"classification"),start,true).required("body"));
        ProfessionalRuntimeObservations.closed(body.required("physical"),Set.of("kind","witness"));same("WAITING_ITEM",text(body.required("physical"),"kind"));
        var witness=body.required("physical").required("witness");waiting.requireCreationIdentity(tenant,actor,witness);same(witness.required("cause").required("node_input"),input);
        same(body.required("authority"),witness.required("cause").required("authority"));same(body.required("request"),witness.required("cause").required("request"));same(text(witness,"waiting_item_id"),text(body.required("response").required("payload"),"resource_id"));
    }
    private void requireOriginalDefinitions(EnterpriseAccess.Admission a,String project,JsonNode input,JsonNode body)throws Exception {requireOriginalDefinitions(a.context().tenant_id(),a.actorId(),project,input,body);}
    private void requireOriginalDefinitions(String tenant,String actor,String project,JsonNode input,JsonNode body)throws Exception {
        var pins=body.required("authority");same(text(input,"replay_sha256"),text(pins,"replay_sha256"));
        same(input.required("role_version"),pins.required("role_version"));
        var role=roles.requireRegisteredIdentity(tenant,actor,json.basis(pins.required("role_version")),"ROLE",project);
        same(role.canonicalDefinition(),pins.required("role_definition"));
        var tool=capabilities.requireRegisteredIdentity(tenant,actor,json.basis(pins.required("tool_version")),"TOOL",project);
        same(tool.canonicalDefinition(),pins.required("tool_definition"));
        if("AT-04".equals(text(body.required("response"),"tool_id"))) {
            var skill=capabilities.requireRegisteredIdentity(tenant,actor,json.basis(pins.required("skill").required("version")),"SKILL",project);
            same(skill.canonicalDefinition(),pins.required("skill").required("definition"));
        }
    }
    private void requireOriginalAction(EnterpriseAccess.Admission a,JsonNode body){requireOriginalAction(a.context().tenant_id(),a.actorId(),body);}
    private void requireOriginalAction(String tenant,String actor,JsonNode body){
        var binding=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));var response=body.required("response");
        var call=body.required("authority").required("tool_call");
        var origin=one(db.queryForList("SELECT action_id FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND invocation_id=? AND kind='MODEL_CALL' AND lease_generation=? LIMIT 2",
                tenant,text(body,"project_id"),text(body,"run_id"),text(body,"step_id"),text(call,"invocation_id"),binding.lease_generation()));
        one(db.queryForList("SELECT action_id FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND origin_run_id=? AND origin_step_id=? AND origin_action_id=? AND kind=? AND arguments_sha256=? AND tool_version=? AND input_sha256=? AND state='BOUND' AND lease_generation=? AND created_by=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? LIMIT 2",
                tenant,text(body,"project_id"),text(body,"run_id"),text(body,"step_id"),text(response,"action_id"),
                text(body,"run_id"),text(body,"step_id"),str(origin,"action_id"),text(response,"tool_id"),text(body.required("request"),"arguments_sha256"),
                text(body.required("authority").required("tool_version"),"version_id"),text(body,"input_sha256"),binding.lease_generation(),actor,actions.manifestSha256(),text(body,"classification")));
    }
    /** All tool content is separately authorized for the actual model provider at send time. */
    JsonNode requireModelSend(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,JsonNode pins,String purpose,String provider)throws Exception {
        input.requireUnchanged();var result=json.object().arrayNode();
        if(!db.queryForList("SELECT waiting_item_id FROM waiting_item WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND reason_code='ASK_USER_QUESTION' LIMIT 1",
                input.tenantId(),input.projectId(),input.runId(),input.stepId()).isEmpty())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        for(var pin:pins) {
            String name=text(pin.required("tool_call"),"tool_name");if(!Set.of("read_artifact","load_skill","ask_user_question","propose_web_acquisition").contains(name))continue;
            if("ask_user_question".equals(name))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
            String session=text(pin.required("tool_call"),"session_id"),call=text(pin.required("tool_call"),"tool_call_id");
            String receipt=receiptId(session,call);same(receipt,text(pin,"tool_receipt_id"));
            var body=read(a,input.projectId(),input.runId(),receipt,ACTION,classification(a,input.projectId(),input.runId()),
                    id("TASK_REGISTERED_TOOL_ACTION",session,call),true).required("body");
            same(hash(body),text(pin,"tool_receipt_sha256"));same(body.required("authority").required("tool_call"),pin.required("tool_call"));
            same(input.inputId(),text(body,"input_id"));same(input.receiptSha256(),text(body,"input_receipt_sha256"));
            validateResponse(body);
            if("propose_web_acquisition".equals(name)) {
                if(web==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
                web.requireHistoryCurrent(a,input,body);result.add(body.required("physical").required("witness"));continue;
            }
            if("load_skill".equals(name))result.add(packages.requireCurrentPins(a,input.projectId(),body.required("physical").required("pins"),purpose,provider,input));
            else result.add(predecessors.requireCurrentPins(a,input,body.required("physical").required("pins"),purpose,provider));
        }
        input.requireUnchanged();return result;
    }
    private String classification(EnterpriseAccess.Admission a,String project,String run){
        return str(one(db.queryForList("SELECT classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' LIMIT 2",
                a.context().tenant_id(),project,run,a.actorId())),"classification");
    }
    private static void changed(int rows){if(rows!=1)bad();}
}
