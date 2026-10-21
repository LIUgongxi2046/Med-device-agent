package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Original N02 waiting evidence and responsible-owner acknowledgement. Not a human checkpoint.
 * REQUIRE_NEW_RUN marks only the waiting item STALE. It never signals, queues or modifies a run,
 * changes frozen facts, approves content, grants access, or creates a replacement execution.
 * Deliberately not auto-enabled: runtime composition must retain the existing disabled RN01 gate. */
public final class TaskResearchWaitingService {
    static final String CREATED="task.research.waiting.created",DECIDED="task.research.waiting.new_run_required";
    static final String CREATE_OPERATION="TASK-RESEARCH-WAITING-CREATE",DECIDE_OPERATION="TASK-RESEARCH-WAITING-DECIDE";
    static final String READ="work_item.read",RESOLVE="work_item.resolve",READ_PURPOSE="WORK_ITEM_READING",RESOLVE_PURPOSE="WORK_ITEM_RESOLUTION";
    private static final String QUESTION="The responsible owner must acknowledge that an approved revision and a new run are required. This does not verify the research result.";
    private final EnterpriseAccess access;private final ResourceReadAuthority resources;private final JdbcTemplate db;private final PermissionSchema json;
    private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;private final String manifest;
    public TaskResearchWaitingService(EnterpriseAccess access,DataSource data,ResourceReadAuthority resources,PermissionSchema json)throws Exception {this(access,new JdbcTemplate(data),resources,json);}
    TaskResearchWaitingService(EnterpriseAccess access,JdbcTemplate db,ResourceReadAuthority resources,PermissionSchema json)throws Exception {
        this.access=Objects.requireNonNull(access);this.db=Objects.requireNonNull(db);this.resources=Objects.requireNonNull(resources);this.json=Objects.requireNonNull(json);db.setQueryTimeout(5);
        byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-research-waiting.schema.json"))){bytes=in.readAllBytes();}
        manifest=WireValues.sha256(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
    }
    /** Only the evaluator's privately constructed, currently verified cause can create waiting work. */
    public String create(TaskResearchCompletionEvaluation.WaitingCause proof)throws Exception {
        Objects.requireNonNull(proof).requireUnchanged();var candidate=proof.candidate();var session=candidate.session();var a=session.admission();current(a);
        var cause=cause(proof);String tenant=text(cause,"tenant_id"),waiting=waitingId(cause);
        var existing=db.queryForList("SELECT * FROM waiting_item WHERE tenant_id=? AND waiting_item_id=? FOR UPDATE",tenant,waiting);
        if(!existing.isEmpty()){requireOriginal(proof,waiting);return waiting;}
        verifyTarget(cause);var now=now();String operation=derived(waiting,"operation"),event=derived(waiting,"event");
        var receipt=receipt(cause,waiting,operation,waiting,event,"WAITING_INPUT","1",null,hash(cause),a.actorId(),now);
        var witness=json.object();witness.set("cause",cause);witness.set("request",cause);witness.set("receipt",receipt);
        witness.set("source_read_receipts",candidate.sourceReceipts().canonicalPins());witness.set("model_invocations",candidate.invocationPins());
        var row=metadata(cause,a.actorId(),now);row.put("waiting_item_id",waiting);for(String f:List.of("run_id","step_id","action_id"))row.put(f,text(cause,f));
        row.put("target_revision",text(cause,"input_artifact_id"));row.put("reason_code","REQUIRE_NEW_RUN");row.put("question",QUESTION);
        row.put("requirement_refs",json.canonical(cause.required("requirement_ids")));row.put("allowed_resolution_actions",json.canonical(json.tree(List.of(RESOLVE))));
        row.put("assignee_id",text(cause,"assignee_id"));row.put("status","WAITING_INPUT");row.put("resolution_id",null);row.put("revision","1");row.put("updated_at",now);
        insert("waiting_item",row);journal(cause,CREATE_OPERATION,text(cause,"action_id"),CREATED,witness,receipt);
        requireOriginal(proof,waiting);return waiting;
    }
    public void requireOriginal(TaskResearchCompletionEvaluation.WaitingCause proof,String waiting)throws Exception {
        Objects.requireNonNull(proof).requireUnchanged();WireValues.uuid(waiting);var expected=cause(proof);same(waitingId(expected),waiting);
        var loaded=load(text(expected,"tenant_id"),waiting);same(expected,loaded.cause());proof.requireUnchanged();
    }
    /** Fresh scheduler commit only. The private original input supplies current control admission;
     * returned canonical metadata is not a source-read, model-call, or human-approval capability.
     * Historical original receipt replay continues to use requireOriginal without reopening work. */
    JsonNode requireProduced(TaskResearchCompletionAuthority.Input input,String waitingId,String candidateSha256,
            JsonNode sourcePins,JsonNode invocationPins,List<String> checkIds)throws Exception {
        Objects.requireNonNull(input).requireUnchanged();var a=input.admission();current(a);
        if(!a.controlWorker()||a.formalWorker()||a.simulationWorker()||a.recoveryWorker())deny();
        WireValues.uuid(waitingId);WireValues.hash(candidateSha256);Objects.requireNonNull(sourcePins);Objects.requireNonNull(invocationPins);Objects.requireNonNull(checkIds);
        if(checkIds.size()>512||new HashSet<>(checkIds).size()!=checkIds.size())bad();for(String id:checkIds)WireValues.uuid(id);
        var row=load(input.tenantId(),waitingId);var cause=row.cause();
        if(!"WAITING_INPUT".equals(string(row.row(),"status")))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        for(var pin:Map.ofEntries(Map.entry("tenant_id",input.tenantId()),Map.entry("project_id",input.projectId()),
                Map.entry("run_id",input.runId()),Map.entry("step_id",input.stepId()),Map.entry("action_id",input.actionId()),
                Map.entry("session_id",input.sessionId()),Map.entry("lease_generation",Long.toString(input.generation())),
                Map.entry("input_artifact_id",input.inputId()),Map.entry("input_sha256",input.inputSha256()),
                Map.entry("input_receipt_sha256",input.receiptSha256()),Map.entry("question_id",input.questionId()),
                Map.entry("created_by",input.actorId()),Map.entry("candidate_sha256",candidateSha256)).entrySet())same(pin.getValue(),text(cause,pin.getKey()));
        var manifest=input.manifest();
        for(String field:List.of("tenant_id","project_id","task_id","run_id","step_id","question_id","question_key","section_id",
                "plan_revision","plan_sha256","graph_id","graph_sha256","replay_sha256","snapshot_id","snapshot_sha256"))same(manifest.required(field),cause.required(field));
        same(sourcePins,cause.required("source_read_receipts"));same(invocationPins,cause.required("model_invocations"));
        var actualChecks=new ArrayList<String>();for(var check:cause.required("completion_checks"))actualChecks.add(text(check,"check_id"));same(List.copyOf(checkIds),List.copyOf(actualChecks));
        // load() already checks the exact frozen, confirmed section owner, not a caller or actor fallback.
        if(db.queryForList("SELECT user_id FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE' AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6)) FOR UPDATE",input.tenantId(),text(cause,"assignee_id")).size()!=1)
            throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        input.requireUnchanged();current(a);return cause.deepCopy();
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String waiting) {
        WireValues.uuid(waiting);return access.execute(subject,a->checked(()->{human(a,context);var row=load(a.context().tenant_id(),waiting);var read=authority(a,row,READ,READ_PURPOSE);
            var view=view(row);var actions=view.putArray("allowed_decisions");
            if("WAITING_INPUT".equals(string(row.row(),"status"))&&resources.find(a,scope(row),RESOLVE,RESOLVE_PURPOSE).isPresent())actions.add("REQUIRE_NEW_RUN");
            same(read,authority(a,row,READ,READ_PURPOSE));return validate("View",view);
        }));
    }
    public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String waiting,String key,byte[] bytes)throws Exception {
        WireValues.uuid(waiting);key(key);var request=request("Decision",bytes);sameRoute(waiting,request);
        return access.execute(subject,a->checked(()->decision(a,context,waiting,key,request,false)));
    }
    public JsonNode recover(ApplicationSessionIdentity.Verified subject,String context,String waiting,byte[] bytes)throws Exception {
        WireValues.uuid(waiting);var request=request("Recovery",bytes);var original=request.required("original_request");sameRoute(waiting,original);
        return access.execute(subject,a->checked(()->decision(a,context,waiting,text(request,"original_idempotency_key"),original,true)));
    }
    private JsonNode decision(EnterpriseAccess.Admission a,String context,String waiting,String key,JsonNode request,boolean recovery)throws Exception {
        human(a,context);var row=load(a.context().tenant_id(),waiting);var read=authority(a,row,READ,READ_PURPOSE);var resolve=authority(a,row,RESOLVE,RESOLVE_PURPOSE);
        var prior=db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? FOR UPDATE",a.context().tenant_id(),a.actorId(),DECIDE_OPERATION,key);
        if(!prior.isEmpty()) {
            var operation=one(prior);if(!hash(request).equals(string(operation,"request_sha256"))||!waiting.equals(operation.get("target_id")))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            var receipt=validate("Receipt",parse(string(operation,"response_ref")));var witness=journalRead(row.cause(),DECIDE_OPERATION,key,DECIDED,receipt);
            same(request,witness.required("request"));same(row.cause(),witness.required("cause"));verifyDecision(row,receipt,witness);
            same(read,authority(a,row,READ,READ_PURPOSE));same(resolve,authority(a,row,RESOLVE,RESOLVE_PURPOSE));return receipt;
        }
        if(recovery)throw new TenantAccessException(ErrorCode.SOURCE_MISSING);
        if(!"WAITING_INPUT".equals(string(row.row(),"status"))||!text(request,"expected_revision").equals(string(row.row(),"revision")))conflict();
        same(hash(row.cause()),text(request,"cause_sha256"));String decision=UUID.randomUUID().toString(),operation=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();var now=now();
        var receipt=receipt(row.cause(),waiting,operation,audit,event,"STALE","2",decision,hash(request),a.actorId(),now);
        var witness=json.object();witness.set("cause",row.cause());witness.set("request",request);witness.set("receipt",receipt);
        witness.set("read_grant",TaskFieldAuthorityWitness.tree(read));witness.set("resolve_grant",TaskFieldAuthorityWitness.tree(resolve));
        witness.put("auth_epoch",a.watermarks().authEpoch().toString()).put("governance_epoch",a.watermarks().governanceEpoch().toString());
        witness.put("identity_mode",a.bindingView().mode());
        var version=new LinkedHashMap<String,Object>();version.put("tenant_id",a.context().tenant_id());version.put("owner_project_id",text(row.cause(),"project_id"));version.put("version_id",decision);version.put("entity_kind","TASK_RESEARCH_WAITING_DECISION");version.put("content_sha256",hash(witness));insert("version_registry",version);
        changed(db.update("UPDATE waiting_item SET status='STALE',resolution_id=?,revision=2,updated_at=? WHERE tenant_id=? AND waiting_item_id=? AND status='WAITING_INPUT' AND revision=? AND resolution_id IS NULL",decision,now,a.context().tenant_id(),waiting,text(request,"expected_revision")));
        journal(row.cause(),DECIDE_OPERATION,key,DECIDED,witness,receipt);
        var updated=load(a.context().tenant_id(),waiting);same(read,authority(a,updated,READ,READ_PURPOSE));same(resolve,authority(a,updated,RESOLVE,RESOLVE_PURPOSE));return receipt;
    }
    private ObjectNode cause(TaskResearchCompletionEvaluation.WaitingCause proof)throws Exception {
        proof.requireUnchanged();var candidate=proof.candidate();var session=candidate.session();var input=session.input();var fixed=input.manifest();var values=proof.inputs();
        if(values.ownerId()==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var run=one(db.queryForList("SELECT * FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? FOR UPDATE",input.tenantId(),input.projectId(),input.runId()));
        var cause=json.object().put("schema_version","1.0.0").put("kind","TASK_RESEARCH_WAITING").put("tenant_id",input.tenantId()).put("project_id",input.projectId()).put("task_id",input.taskId()).put("run_id",input.runId()).put("step_id",input.stepId()).put("action_id",session.actionId()).put("lease_generation",session.generation()).put("session_id",session.sessionId())
            .put("input_artifact_id",input.inputId()).put("input_sha256",input.inputSha256()).put("input_receipt_sha256",input.receiptSha256()).put("candidate_sha256",candidate.candidateSha256())
            .put("supervision_receipt_id",candidate.supervision().receiptId()).put("supervision_sha256",candidate.supervision().sha256()).put("deployment_sha256",candidate.supervision().deploymentSha256())
            .put("profile_revision",values.profileId()).put("profile_sha256",values.profileSha256()).put("assignee_id",values.ownerId()).put("created_by",session.admission().actorId()).put("classification",string(run,"classification"));
        for(String f:List.of("plan_revision","plan_sha256","graph_id","graph_sha256","replay_sha256","snapshot_id","snapshot_sha256","question_id","question_key","section_id"))cause.set(f,fixed.required(f).deepCopy());
        cause.set("reason_codes",json.tree(proof.reasons()));cause.set("requirement_ids",json.tree(values.requirementIds()));cause.set("completion_checks",proof.receiptPins());
        var rules=cause.putArray("rule_versions");for(var rule:values.checks())rules.addObject().put("rule_version",rule.ruleId()).put("rule_sha256",rule.ruleSha256());
        cause.put("rule_authority_sha256",hash(values.witness()));cause.set("source_read_receipts",candidate.sourceReceipts().canonicalPins());cause.set("model_invocations",candidate.invocationPins());cause.put("source_read_receipts_sha256",hash(cause.required("source_read_receipts")));cause.put("model_invocation_receipts_sha256",hash(cause.required("model_invocations")));
        validate("Cause",cause);proof.requireUnchanged();return cause;
    }
    private record Loaded(Map<String,Object> row,JsonNode cause,JsonNode created){}
    private Loaded load(String tenant,String waiting)throws Exception {
        var row=one(db.queryForList("SELECT * FROM waiting_item WHERE tenant_id=? AND waiting_item_id=? FOR UPDATE",tenant,waiting));
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? FOR UPDATE",tenant,waiting));
        var original=parse(string(audit,"resource_ref"));ProfessionalRuntimeObservations.closed(original,Set.of("cause","request","receipt","source_read_receipts","model_invocations"));var cause=validate("Cause",original.required("cause"));var created=validate("Receipt",original.required("receipt"));
        same(waitingId(cause),waiting);same(tenant,text(cause,"tenant_id"));same(cause,original.required("request"));
        same(waiting,text(created,"waiting_item_id"));same(waiting,text(created,"audit_id"));same(derived(waiting,"operation"),text(created,"operation_id"));same(derived(waiting,"event"),text(created,"event_id"));
        same(cause.required("source_read_receipts"),original.required("source_read_receipts"));same(cause.required("model_invocations"),original.required("model_invocations"));
        same(text(cause,"source_read_receipts_sha256"),hash(cause.required("source_read_receipts")));same(text(cause,"model_invocation_receipts_sha256"),hash(cause.required("model_invocations")));
        same(original,journalRead(cause,CREATE_OPERATION,text(cause,"action_id"),CREATED,created));
        same("WAITING_INPUT",text(created,"status"));same("1",text(created,"revision"));same(hash(cause),text(created,"request_sha256"));same(text(cause,"created_by"),text(created,"created_by"));
        if(!created.required("resolution_id").isNull())bad();
        for(String f:List.of("tenant_id","project_id","run_id","step_id","action_id","assignee_id","created_by","classification"))same(text(cause,f),string(row,f));
        same(waiting,string(row,"waiting_item_id"));same(text(cause,"input_artifact_id"),string(row,"target_revision"));same("REQUIRE_NEW_RUN",string(row,"reason_code"));same(QUESTION,string(row,"question"));
        same(cause.required("requirement_ids"),parse(string(row,"requirement_refs")));same(json.tree(List.of(RESOLVE)),parse(string(row,"allowed_resolution_actions")));
        same("1.0.0",string(row,"schema_version"));same(manifest,string(row,"schema_manifest_sha256"));same(text(created,"created_at"),instant(time(row,"created_at")));
        verifyTarget(cause);var loaded=new Loaded(row,cause,created);String status=string(row,"status");
        if("WAITING_INPUT".equals(status)){same("1",string(row,"revision"));if(row.get("resolution_id")!=null)bad();same(time(row,"created_at"),time(row,"updated_at"));}
        else if("STALE".equals(status)) {
            same("2",string(row,"revision"));String resolution=string(row,"resolution_id");var operations=db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND target_id=? AND operation_name=? FOR UPDATE",tenant,waiting,DECIDE_OPERATION);
            var op=one(operations);var receipt=validate("Receipt",parse(string(op,"response_ref")));same(resolution,text(receipt,"resolution_id"));
            var witness=journalRead(cause,DECIDE_OPERATION,string(op,"idempotency_key"),DECIDED,receipt);verifyDecision(loaded,receipt,witness);
        } else bad();return loaded;
    }
    private void verifyTarget(JsonNode cause)throws Exception {
        String tenant=text(cause,"tenant_id"),project=text(cause,"project_id"),run=text(cause,"run_id"),step=text(cause,"step_id"),target=text(cause,"input_artifact_id");
        var registered=one(db.queryForList("SELECT * FROM task_node_input_registered WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND artifact_id=? FOR UPDATE",tenant,project,run,step,target));
        same(text(cause,"task_id"),string(registered,"task_id"));same(text(cause,"input_sha256"),string(registered,"payload_sha256"));same(text(cause,"input_receipt_sha256"),string(registered,"receipt_sha256"));
        same(text(cause,"input_receipt_sha256"),hash(parse(string(registered,"receipt_json"))));
        var version=one(db.queryForList("SELECT * FROM version_registry WHERE tenant_id=? AND version_id=? FOR UPDATE",tenant,target));same(project,string(version,"owner_project_id"));same("ARTIFACT",string(version,"entity_kind"));same(text(cause,"input_sha256"),string(version,"content_sha256"));
        var artifact=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND project_id=? AND run_id=? AND artifact_id=? FOR UPDATE",tenant,project,run,target));same("TASK_NODE_INPUT",string(artifact,"artifact_kind"));same(text(cause,"input_sha256"),string(artifact,"sha256"));
        var binding=one(db.queryForList("SELECT * FROM artifact_content_binding WHERE tenant_id=? AND artifact_id=? FOR UPDATE",tenant,target));same(project,string(binding,"project_id"));same("TASK",string(binding,"resource_kind"));same(text(cause,"task_id"),string(binding,"resource_id"));same(TaskNodeInputSchema.REF,string(binding,"schema_ref"));
        var action=one(db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? FOR UPDATE",tenant,project,run,step,text(cause,"action_id")));
        same("HOST_CONTROL",string(action,"kind"));same(text(cause,"input_sha256"),string(action,"input_sha256"));same(text(cause,"lease_generation"),string(action,"lease_generation"));if(action.get("invocation_id")!=null)bad();
        same(hash(Map.of("operation","N02_HOST_CONTROL","run_id",run,"step_id",step,"input_artifact_id",target,"input_sha256",text(cause,"input_sha256"),"input_receipt_sha256",text(cause,"input_receipt_sha256"))),string(action,"arguments_sha256"));
        var task=one(db.queryForList("SELECT * FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? FOR UPDATE",tenant,project,run));same("TASK",string(task,"execution_kind"));same(text(cause,"task_id"),string(task,"task_id"));same(text(cause,"classification"),string(task,"classification"));same(text(cause,"created_by"),string(task,"created_by"));
        var replay=parse(string(task,"replay_manifest"));same(text(cause,"replay_sha256"),hash(replay));var plan=replay.required("plan").required("content");same(text(cause,"plan_sha256"),hash(plan));same(text(cause,"plan_revision"),text(replay,"plan_revision"));
        var sections=new ArrayList<JsonNode>();for(var section:plan.required("sections"))if(text(cause,"section_id").equals(text(section,"section_id")))sections.add(section);same(text(cause,"assignee_id"),text(one(sections),"owner_id"));
        var questions=new ArrayList<JsonNode>();for(var question:plan.required("questions"))if(text(cause,"question_key").equals(text(question,"question_key")))questions.add(question);same(text(cause,"section_id"),text(one(questions),"section_id"));
        same(text(cause,"profile_revision"),text(replay.required("project_profile"),"profile_revision"));same(text(cause,"profile_sha256"),text(replay.required("project_profile"),"content_sha256"));
        verifyReceiptPins(cause);
    }
    /** Historical receipt metadata only. These checks never produce content-read authority. */
    private void verifyReceiptPins(JsonNode cause)throws Exception {
        String tenant=text(cause,"tenant_id"),project=text(cause,"project_id"),actor=text(cause,"created_by");
        var closure=one(db.queryForList("SELECT * FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase=? FOR UPDATE",tenant,text(cause,"session_id"),"CLOSED"));
        for(String field:List.of("project_id","run_id","step_id","lease_generation","created_by","input_sha256","deployment_sha256"))same(text(cause,field),string(closure,field));
        same(text(cause,"supervision_receipt_id"),string(closure,"receipt_id"));same(text(cause,"supervision_sha256"),string(closure,"evidence_sha256"));same(text(cause,"supervision_sha256"),hash(parse(string(closure,"canonical_evidence"))));
        for(var pin:cause.required("source_read_receipts")) {
            var row=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? FOR UPDATE",tenant,text(pin,"read_receipt_id")));
            var sourceSchema=new TaskSourceReadReceiptSchema(json);var receipt=sourceSchema.read(string(row,"resource_ref"));var body=receipt.required("body");var identity=body.required("identity");
            same(text(pin,"receipt_sha256"),text(receipt,"receipt_sha256"));same(text(pin,"read_receipt_id"),text(body,"receipt_id"));
            same(project,string(row,"project_id"));same(actor,string(row,"subject_id"));same(actor,string(row,"created_by"));same(actor,text(identity,"actor_id"));same(TaskResearchSourceReader.ACTION,string(row,"action"));
            same("SUCCESS",string(row,"result"));if(row.get("reason_code")!=null)bad();same("1.0.0",string(row,"schema_version"));same(sourceSchema.manifest(),string(row,"schema_manifest_sha256"));
            same(text(cause,"run_id"),string(row,"trace_id"));same(text(body,"action_id"),string(row,"request_id"));same(text(body,"created_at"),instant(time(row,"created_at")));
            for(String field:List.of("input_sha256","input_receipt_sha256","session_id","lease_generation"))same(text(cause,field),text(identity,field));same(text(cause,"input_artifact_id"),text(identity,"input_id"));
            for(String field:List.of("tenant_id","project_id","task_id","run_id","step_id"))same(text(cause,field),text(identity.required("node_input"),field));
        }
        for(var pin:cause.required("model_invocations")) {
            String invocation=text(pin,"invocation_id");var row=one(db.queryForList("SELECT * FROM invocation WHERE tenant_id=? AND invocation_id=? FOR UPDATE",tenant,invocation));
            for(String field:List.of("project_id","run_id","step_id","lease_generation","created_by"))same(text(cause,field),string(row,field));same("SUCCEEDED",string(row,"status"));same("SETTLED",string(row,"settlement_status"));
            var account=one(db.queryForList("SELECT * FROM formal_invocation_accounting WHERE tenant_id=? AND invocation_id=? FOR UPDATE",tenant,invocation));
            var receipt=parse(string(account,"receipt_json"));same(text(pin,"accounting_sha256"),string(account,"receipt_sha256"));same(text(pin,"accounting_sha256"),hash(receipt));same(tenant,text(receipt,"tenant_id"));same(invocation,text(receipt,"invocation_id"));same(project,text(receipt,"project_id"));same(actor,text(receipt,"created_by"));
            same("SUCCEEDED",text(receipt,"status"));same("SETTLED",text(receipt,"settlement_status"));if(!receipt.required("provider_terminal").isBoolean()||!receipt.required("provider_terminal").booleanValue())bad();
            for(String kind:List.of("MODEL_RESULT","PROVIDER_CAPTURE")) {
                String hashField=kind.equals("MODEL_RESULT")?"result_registration_sha256":"capture_registration_sha256";
                var registered=one(db.queryForList("SELECT * FROM formal_invocation_registered_object WHERE tenant_id=? AND invocation_id=? AND kind=? FOR UPDATE",tenant,invocation,kind));
                var value=parse(string(registered,"receipt_json"));same(text(pin,hashField),string(registered,"receipt_sha256"));same(text(pin,hashField),hash(value));same(text(pin,hashField),text(receipt,hashField));same(tenant,text(value,"tenant_id"));same(invocation,text(value,"invocation_id"));same(kind,text(value,"kind"));
            }
        }
        for(var pin:cause.required("completion_checks")) {
            var row=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? FOR UPDATE",tenant,text(pin,"check_id")));var receipt=parse(string(row,"resource_ref"));
            same(text(pin,"receipt_sha256"),hash(receipt));same(TaskResearchCompletionEvaluation.ACTION,string(row,"action"));same(TaskResearchCompletionEvaluation.MANIFEST,string(row,"schema_manifest_sha256"));same(project,string(row,"project_id"));same(actor,string(row,"subject_id"));same(actor,string(row,"created_by"));same("SUCCESS",string(row,"result"));if(row.get("reason_code")!=null)bad();
            for(String field:List.of("tenant_id","project_id","run_id","step_id","action_id","lease_generation","question_id","session_id","input_artifact_id","input_sha256","input_receipt_sha256","candidate_sha256","profile_revision","profile_sha256"))same(text(cause,field),text(receipt,field));
            for(String field:List.of("check_id","rule_version","outcome"))same(text(pin,field),text(receipt,field));same(cause.required("source_read_receipts"),receipt.required("source_read_receipts"));same(cause.required("model_invocations"),receipt.required("invocations"));
        }
    }
    private void verifyDecision(Loaded row,JsonNode receipt,JsonNode witness)throws Exception {
        ProfessionalRuntimeObservations.closed(witness,Set.of("cause","request","receipt","read_grant","resolve_grant","auth_epoch","governance_epoch","identity_mode"));same(row.cause(),witness.required("cause"));var request=validate("Decision",witness.required("request"));same(hash(request),text(receipt,"request_sha256"));same(hash(row.cause()),text(request,"cause_sha256"));
        same(text(row.created(),"waiting_item_id"),text(request,"waiting_item_id"));same("1",text(request,"expected_revision"));same("STALE",text(receipt,"status"));same("2",text(receipt,"revision"));same(text(row.cause(),"assignee_id"),text(receipt,"created_by"));
        same(string(row.row(),"resolution_id"),text(receipt,"resolution_id"));same(instant(time(row.row(),"updated_at")),text(receipt,"created_at"));
        var version=one(db.queryForList("SELECT * FROM version_registry WHERE tenant_id=? AND version_id=? FOR UPDATE",text(row.cause(),"tenant_id"),text(receipt,"resolution_id")));
        same(text(row.cause(),"project_id"),string(version,"owner_project_id"));same("TASK_RESEARCH_WAITING_DECISION",string(version,"entity_kind"));same(hash(witness),string(version,"content_sha256"));
        WireValues.revision(text(witness,"auth_epoch"));WireValues.revision(text(witness,"governance_epoch"));
        for(String field:List.of("read_grant","resolve_grant"))if(!witness.required(field).isObject()||witness.required(field).isEmpty())bad();
        if(!Set.of("local","oidc").contains(text(witness,"identity_mode")))bad();
    }
    private PermissionProtocol.GrantRow authority(EnterpriseAccess.Admission a,Loaded row,String action,String purpose)throws Exception {
        if(!a.actorId().equals(text(row.cause(),"assignee_id")))deny();
        if(db.queryForList("SELECT user_id FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE' AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6)) FOR UPDATE",a.context().tenant_id(),a.actorId()).size()!=1)deny();
        return resources.require(a,scope(row),action,purpose);
    }
    private PermissionProtocol.Scope scope(Loaded row){return new PermissionProtocol.Scope(text(row.cause(),"tenant_id"),text(row.cause(),"project_id"),"TASK",text(row.cause(),"task_id"));}
    private ObjectNode view(Loaded row)throws Exception {
        var result=json.object().put("schema_version","1.0.0").put("waiting_item_id",string(row.row(),"waiting_item_id")).put("status",string(row.row(),"status")).put("revision",string(row.row(),"revision")).put("cause_sha256",hash(row.cause()));
        result.set("resolution_id",json.tree(row.row().get("resolution_id")));result.put("required_next_step","REQUIRE_NEW_RUN").put("can_resume_frozen_run",false).put("human_checkpoint_approved",false);
        for(String field:List.of("tenant_id","project_id","task_id","run_id","step_id","action_id","lease_generation","session_id","input_artifact_id","input_sha256","input_receipt_sha256","candidate_sha256","supervision_receipt_id","supervision_sha256","deployment_sha256","plan_revision","plan_sha256","profile_revision","profile_sha256","snapshot_id","snapshot_sha256","question_id","section_id","assignee_id","reason_codes","requirement_ids","completion_checks","rule_versions","source_read_receipts","model_invocations","source_read_receipts_sha256","model_invocation_receipts_sha256"))result.set(field,row.cause().required(field).deepCopy());
        return result;
    }
    private ObjectNode receipt(JsonNode cause,String waiting,String operation,String audit,String event,String status,String revision,String resolution,String requestHash,String actor,LocalDateTime now)throws Exception {
        var value=json.object().put("schema_version","1.0.0").put("waiting_item_id",waiting).put("operation_id",operation).put("audit_id",audit).put("event_id",event).put("status",status).put("revision",revision).put("cause_sha256",hash(cause)).put("request_sha256",requestHash).put("schema_manifest_sha256",manifest).put("created_by",actor).put("created_at",instant(now)).put("required_next_step","REQUIRE_NEW_RUN").put("can_resume_frozen_run",false).put("human_checkpoint_approved",false);
        for(String field:List.of("tenant_id","project_id","task_id","run_id","step_id","action_id","input_artifact_id","classification"))value.set(field,cause.required(field).deepCopy());value.set("resolution_id",json.tree(resolution));validate("Receipt",value);return value;
    }
    private void journal(JsonNode cause,String name,String key,String action,JsonNode witness,JsonNode receipt)throws Exception {
        var now=LocalDateTime.ofInstant(Instant.parse(text(receipt,"created_at")),ZoneOffset.UTC);var op=metadata(cause,text(receipt,"created_by"),now);op.remove("project_id");
        op.put("operation_id",text(receipt,"operation_id"));op.put("subject_id",text(receipt,"created_by"));op.put("operation_name",name);op.put("idempotency_key",key);op.put("request_sha256",text(receipt,"request_sha256"));op.put("state","COMPLETED");op.put("http_status",200);op.put("response_ref",json.canonical(receipt));op.put("target_id",text(receipt,"waiting_item_id"));op.put("error_code",null);op.put("revision",1);op.put("updated_at",now);insert("operation_record",op);
        var audit=metadata(cause,text(receipt,"created_by"),now);audit.put("audit_id",text(receipt,"audit_id"));audit.put("subject_id",text(receipt,"created_by"));audit.put("action",action);audit.put("resource_ref",json.canonical(witness));audit.put("result","SUCCESS");audit.put("reason_code",null);audit.put("request_id",text(receipt,"operation_id"));audit.put("trace_id",text(cause,"run_id"));insert("audit_event",audit);
        var outbox=new LinkedHashMap<String,Object>();outbox.put("tenant_id",text(cause,"tenant_id"));outbox.put("event_id",text(receipt,"event_id"));outbox.put("aggregate_id",text(receipt,"waiting_item_id"));outbox.put("aggregate_revision",text(receipt,"revision"));outbox.put("event_type",action);outbox.put("payload",json.canonical(witness));outbox.put("occurred_at",now);insert("outbox_event",outbox);
    }
    private JsonNode journalRead(JsonNode cause,String name,String key,String action,JsonNode receipt)throws Exception {
        for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id","action_id","input_artifact_id","classification"))same(text(cause,f),text(receipt,f));same(hash(cause),text(receipt,"cause_sha256"));same(manifest,text(receipt,"schema_manifest_sha256"));
        String tenant=text(cause,"tenant_id"),actor=text(receipt,"created_by");var now=LocalDateTime.ofInstant(Instant.parse(text(receipt,"created_at")),ZoneOffset.UTC);
        var op=one(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? FOR UPDATE",tenant,text(receipt,"operation_id")));
        metadata(op,cause,actor,now,false);for(var e:Map.ofEntries(Map.entry("subject_id",actor),Map.entry("operation_name",name),Map.entry("idempotency_key",key),Map.entry("request_sha256",text(receipt,"request_sha256")),Map.entry("state","COMPLETED"),Map.entry("http_status","200"),Map.entry("target_id",text(receipt,"waiting_item_id")),Map.entry("revision","1"),Map.entry("response_ref",json.canonical(receipt))).entrySet())same(e.getValue(),string(op,e.getKey()));same(now,time(op,"updated_at"));if(op.get("error_code")!=null)bad();
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? FOR UPDATE",tenant,text(receipt,"audit_id")));metadata(audit,cause,actor,now,true);
        same(actor,string(audit,"subject_id"));same(action,string(audit,"action"));same("SUCCESS",string(audit,"result"));same(text(receipt,"operation_id"),string(audit,"request_id"));same(text(cause,"run_id"),string(audit,"trace_id"));if(audit.get("reason_code")!=null)bad();var witness=parse(string(audit,"resource_ref"));same(receipt,witness.required("receipt"));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? FOR UPDATE",tenant,text(receipt,"event_id")));same(tenant,string(event,"tenant_id"));same(text(receipt,"waiting_item_id"),string(event,"aggregate_id"));same(text(receipt,"revision"),string(event,"aggregate_revision"));same(action,string(event,"event_type"));same(json.canonical(witness),string(event,"payload"));same(now,time(event,"occurred_at"));return witness;
    }
    private LinkedHashMap<String,Object> metadata(JsonNode cause,String actor,LocalDateTime now){var row=new LinkedHashMap<String,Object>();row.put("tenant_id",text(cause,"tenant_id"));row.put("project_id",text(cause,"project_id"));row.put("created_by",actor);row.put("created_at",now);row.put("classification",text(cause,"classification"));row.put("schema_version","1.0.0");row.put("schema_manifest_sha256",manifest);return row;}
    private void metadata(Map<String,Object> row,JsonNode cause,String actor,LocalDateTime now,boolean project){for(var e:metadata(cause,actor,now).entrySet()){if(!project&&e.getKey().equals("project_id"))continue;if(e.getKey().equals("created_at"))same(now,time(row,"created_at"));else same(e.getValue(),row.get(e.getKey()));}}
    private void insert(String table,LinkedHashMap<String,Object> row){if(!Set.of("waiting_item","version_registry","operation_record","audit_event","outbox_event").contains(table))throw new IllegalArgumentException();changed(db.update("INSERT INTO "+table+"("+String.join(",",row.keySet())+") VALUES("+String.join(",",Collections.nCopies(row.size(),"?"))+")",row.values().toArray()));}
    private JsonNode request(String definition,byte[] bytes)throws Exception{if(bytes==null||bytes.length==0||bytes.length>16384)invalid();return validate(definition,parser.readTree(bytes));}
    private JsonNode validate(String definition,JsonNode value){if(!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())invalid();return value;}
    private JsonNode parse(String value)throws Exception{if(value.length()>4194304)bad();return parser.readTree(value);}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    private static void human(EnterpriseAccess.Admission a,String context){current(a);PermissionSchema.revision(context);if(a.formalWorker()||a.controlWorker()||a.recoveryWorker()||a.simulationWorker())deny();if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
    private static void current(EnterpriseAccess.Admission a){if(a==null||!TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.getResourceMap().isEmpty())deny();var scope=TenantScope.requireCurrent();if(!scope.tenantId().equals(a.context().tenant_id())||!scope.subjectId().equals(a.actorId()))deny();}
    private static String waitingId(JsonNode cause){return derived(text(cause,"tenant_id"),text(cause,"run_id")+"|"+text(cause,"step_id")+"|"+text(cause,"action_id"));}
    private static String derived(String id,String purpose){return UUID.nameUUIDFromBytes(("TASK_RESEARCH_WAITING|"+id+"|"+purpose).getBytes(StandardCharsets.UTF_8)).toString();}
    private static String hash(Object value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
    private static String text(JsonNode node,String key){if(!node.required(key).isTextual())bad();return node.required(key).textValue();}
    private static String string(Map<String,Object> row,String key){if(row.get(key)==null)bad();return row.get(key).toString();}
    private static LocalDateTime time(Map<String,Object> row,String key){var value=row.get(key);if(value instanceof LocalDateTime t)return t;if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    private static String instant(LocalDateTime now){return now.toInstant(ZoneOffset.UTC).toString();}
    private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}private static <T>T checked(ThrowingSupplier<T> action){return action.get();}
    private static void key(String key){if(key==null||!key.matches("[!-~]{16,128}"))invalid();}private static void sameRoute(String waiting,JsonNode request){if(!waiting.equals(text(request,"waiting_item_id")))deny();}
    private static void same(Object expected,Object actual){if(!Objects.equals(expected,actual))bad();}private static void changed(int count){if(count!=1)conflict();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void conflict(){throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
