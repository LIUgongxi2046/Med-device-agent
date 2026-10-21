package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Immutable original075/context/083/099 verifier. It has no live node/input/session
 * authority dependency and cannot mint execution or content-read permission. */
public final class TaskInvocationOriginals {
    private static final String EXACT_INPUT_REF=TaskInvocationContextSchema.EXACT_INPUT_REF;
    private final JdbcTemplate db;private final TaskProfessionalObjectRegistration objects;private final TaskNodeProducerAuthority producers;
    private final FormalInvocationSchema schema;private final TaskInvocationContextSchema exact;private final PermissionSchema json;
    private final com.fasterxml.jackson.databind.json.JsonMapper parser=com.fasterxml.jackson.databind.json.JsonMapper.builder()
        .propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public TaskInvocationOriginals(DataSource data,TaskProfessionalObjectRegistration objects,TaskNodeProducerAuthority producers,
            FormalInvocationSchema schema,TaskInvocationContextSchema exact,PermissionSchema json){
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.objects=objects;this.producers=producers;this.schema=schema;this.exact=exact;this.json=json;
    }
    /** Immutable historical facts; no execution or content-read authority is carried here. */
    public final class Stored {
        private final String context,actor,hash;private final TaskInvocationProtocol.Request request;private final JsonNode dependencies;
        private final TaskProfessionalObjectRegistration.OriginalExactInput original;
        private Stored(String context,String actor,String hash,TaskInvocationProtocol.Request request,JsonNode dependencies,TaskProfessionalObjectRegistration.OriginalExactInput original){this.context=context;this.actor=actor;this.hash=hash;this.request=request;this.dependencies=dependencies.deepCopy();this.original=original;}
        public String contextId(){return context;}public String actorId(){return actor;}public TaskInvocationProtocol.Request request(){return request;}
        public JsonNode dependencies(){return dependencies.deepCopy();}public String connectionVerificationId(){return text(dependencies.required("model_eligibility"),"connection_verification_id");}
        public FormalInvocationPricing.Schedule price()throws Exception{return schema.price(text(dependencies.required("model").required("definition").required("price_basis"),"version_id"),dependencies.required("price"));}
        String hash(){return hash;}
        TaskProfessionalObjectRegistration.OriginalExactInput original(){return original;}
        public long modelRequestOrdinal(){return dependencies.required("model_request").required("ordinal").longValue();}
    }
    Stored requireStored(String tenant,String context)throws Exception {
        return requireStored(tenant,context,TaskInvocationHistoryData.MAXIMUM_ROUNDS+2L);
    }
    private Stored requireStored(String tenant,String context,long beforeOrdinal)throws Exception {
        requireTenant(tenant);WireValues.uuid(context);
        var row=one(db.queryForList("SELECT c.*,v.content_sha256 registry_sha256,v.entity_kind,v.owner_project_id FROM context_manifest c JOIN version_registry v ON v.tenant_id=c.tenant_id AND v.version_id=c.context_manifest_id WHERE c.tenant_id=? AND c.context_manifest_id=? AND c.execution_mode='EXTERNAL' LIMIT 2 FOR UPDATE",tenant,context));
        if(!"CONTEXT_MANIFEST".equals(str(row,"entity_kind"))||!str(row,"project_id").equals(str(row,"owner_project_id"))||!"1.0.0".equals(str(row,"schema_version"))||!exact.sha256().equals(str(row,"schema_manifest_sha256")))bad();
        var source=schema.parse(str(row,"source_manifest"));if(!"TASK".equals(text(source,"execution_kind")))bad();
        var request=parser.treeToValue(source.required("request"),TaskInvocationProtocol.Request.class);var node=request.node();var dependencies=source.required("dependencies");
        var ordinal=dependencies.required("model_request").required("ordinal");
        if(!ordinal.isIntegralNumber()||!ordinal.canConvertToLong()||ordinal.longValue()<1||ordinal.longValue()>=beforeOrdinal)bad();
        if(!tenant.equals(node.tenant_id())||!str(row,"project_id").equals(node.project_id())||!str(row,"classification").equals(node.classification())||!request.prompt_basis().version_id().equals(str(row,"prompt_revision"))||!request.tokenizer_basis().version_id().equals(str(row,"tokenizer_revision")))bad();
        var original=objects.readOriginalExactInput(tenant,request.exact_input().artifactId());original.requireUnchanged();
        if(!original.receiptSha256().equals(text(dependencies,"exact_input_receipt_sha256"))||!original.receipt().equals(dependencies.required("exact_input_receipt"))||!original.reference().sha256().equals(request.exact_input().sha256())||!EXACT_INPUT_REF.equals(request.exact_input().schemaRef()))bad();
        var receipt=original.receipt();for(var pin:Map.of("tenant_id",tenant,"project_id",node.project_id(),"run_id",node.run_id(),"step_id",node.step_id(),"action_id",node.host_action_id(),"session_id",node.session_id(),"lease_generation",node.lease_generation(),"worker_service_uri",node.worker_service_uri(),"created_by",str(row,"created_by")).entrySet())if(!pin.getValue().equals(text(receipt,pin.getKey())))bad();
        var provenance=receipt.required("provenance");for(var fields=provenance.fieldNames();fields.hasNext();){String field=fields.next();if(!provenance.required(field).equals(dependencies.required(field)))bad();}
        if(!node.supervisor_receipt_id().equals(text(provenance,"supervision_receipt_id"))||!node.supervisor_receipt_sha256().equals(text(provenance,"supervision_sha256")))bad();
        if(!str(row,"token_count").equals(text(dependencies.required("tokenization"),"token_count")))bad();
        var document=contextDocument(context,request,source,str(row,"dependency_access_set_id"),str(row,"token_count"));String digest=schema.hash(document);
        if(!digest.equals(str(row,"content_sha256"))||!digest.equals(str(row,"registry_sha256"))||!schema.parse(str(row,"exact_input_ref")).equals(json.tree(request.exact_input()))||!str(row,"dependency_access_set_id").equals(text(receipt.required("content_manifest"),"access_set_id")))bad();
        var audit=one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND created_by=? AND action='task.model_context.registered' AND request_id=? AND trace_id=? AND result='SUCCESS' AND reason_code IS NULL AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? AND created_at=? LIMIT 2",(r,n)->r.getString(1),tenant,node.project_id(),str(row,"created_by"),str(row,"created_by"),context,node.run_id(),exact.sha256(),node.classification(),row.get("created_at")));
        var event=one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type='task.model_context.registered' AND occurred_at=? LIMIT 2",(r,n)->r.getString(1),tenant,context,context,row.get("created_at")));
        if(!document.equals(schema.parse(audit))||!document.equals(schema.parse(event)))bad();
        validateOriginalProjection(original,dependencies,request);
        validateRecordedHistory(request,str(row,"created_by"),dependencies,ordinal.longValue());
        return new Stored(context,str(row,"created_by"),digest,request,dependencies,original);
    }
    void requireOriginalOutput(Stored stored,FormalInvocationOutcomeAuthority.VerifiedOutcome outcome,String kind,byte[] bytes)throws Exception {
        outcome.requireActive();if(!stored.contextId().equals(text(outcome.binding(),"context_manifest_id"))||!stored.request().node().tenant_id().equals(outcome.tenantId()))denied();
        producers.requireOriginalOutput(outcome,kind,bytes);
    }
    void verifyOriginalOutput(Stored stored,String kind,byte[] bytes)throws Exception {
        String invocation=one(db.query("SELECT invocation_id FROM invocation WHERE tenant_id=? AND context_manifest_id=? AND run_id=? AND step_id=? LIMIT 2",(r,n)->r.getString(1),stored.request().node().tenant_id(),stored.contextId(),stored.request().node().run_id(),stored.request().node().step_id()));
        var original=producers.verifyOriginalOutput(stored.request().node().tenant_id(),invocation,kind,bytes);if(!stored.contextId().equals(text(original,"context_id")))bad();
    }
    void requireRecoveryPolicy(Stored stored,FormalInvocationOutcomeAuthority.VerifiedOutcome outcome,RuntimeObjectTransport transport)throws Exception {
        requireOriginalOutput(stored,outcome,"MODEL_RESULT",outcome.bytes());var committed=producers.verifyOriginalOutput(outcome.tenantId(),outcome.invocationId(),"MODEL_RESULT",outcome.bytes());
        if(!FormalTaskNodeAuthority.sameEvidence(committed.required("output_storage"),stored.dependencies.required("output_storage"))||!FormalTaskNodeAuthority.sameEvidence(committed.required("output_retention"),stored.dependencies.required("output_retention")))bad();
        var original=parser.treeToValue(committed.required("output_storage"),RuntimeObjectTransport.StorageBinding.class);transport.requireStorageBinding(original);
        if(outcome.bytes().length>original.maximumBytes())denied();
        var node=stored.request.node();var timing=one(db.queryForList("SELECT r.created_at run_created_at,r.replay_manifest,i.created_at invocation_created_at,a.admitted_at,UTC_TIMESTAMP(6) current_at FROM task_run r JOIN invocation i ON i.tenant_id=r.tenant_id AND i.project_id=r.project_id AND i.run_id=r.run_id JOIN formal_invocation_attempt a ON a.tenant_id=i.tenant_id AND a.invocation_id=i.invocation_id WHERE r.tenant_id=? AND r.project_id=? AND r.run_id=? AND r.created_by=? AND r.execution_kind='TASK' AND i.invocation_id=? AND a.attempt_id=? LIMIT 2",node.tenant_id(),node.project_id(),node.run_id(),stored.actorId(),outcome.invocationId(),outcome.attemptId()));
        var replay=schema.parse(str(timing,"replay_manifest"));if(!schema.hash(replay).equals(text(stored.dependencies,"replay_sha256")))bad();
        java.time.Instant created=instant(timing,"run_created_at"),reserved=instant(timing,"invocation_created_at"),admitted=instant(timing,"admitted_at"),now=instant(timing,"current_at");
        if(created.isAfter(reserved)||reserved.isAfter(admitted)||admitted.isAfter(now))bad();
        TaskInvocationOutputPolicy.requireOriginal(committed.required("output_retention"),node.run_id(),created,replay.required("retention"));
        TaskInvocationOutputPolicy.requireWriteCutoff(text(committed,"write_valid_until"),now);
        outcome.requireActive();
    }
    List<ArtifactRegistryProtocol.MemberRow> contentMembers(Stored stored)throws Exception {
        stored.original.requireUnchanged();var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();for(var member:stored.original.receipt().required("content_manifest").required("members"))members.add(parser.treeToValue(member,ArtifactRegistryProtocol.MemberRow.class));return List.copyOf(members);
    }
    private void validateOriginalProjection(TaskProfessionalObjectRegistration.OriginalExactInput original,JsonNode dependencies,TaskInvocationProtocol.Request request)throws Exception {
        original.requireUnchanged();var receipt=dependencies.required("model_request");var node=request.node();
        var systems=new ArrayList<String>();for(var system:dependencies.required("approved_system_messages")){if(!system.isTextual())bad();systems.add(system.textValue());}
        byte[] observed=Base64.getDecoder().decode(text(receipt,"body_base64"));validateRecordedProjection(observed,receipt,node.session_id(),text(dependencies,"launch_prompt"),systems,original.bytes(),exact,TaskInvocationHistoryData.fromDependencies(dependencies,receipt.required("ordinal").longValue()));
        if(!node.supervisor_receipt_id().equals(text(receipt,"supervision_receipt_id"))||!node.supervisor_receipt_sha256().equals(text(receipt,"supervision_sha256"))||!dependencies.required("host_binding").equals(receipt.required("binding")))bad();
        if(!WireValues.sha256(text(dependencies,"launch_prompt").getBytes(StandardCharsets.UTF_8)).equals(text(dependencies.required("prompt_compilation"),"launch_sha256")))bad();
        var supervision=one(db.queryForList("SELECT * FROM professional_runtime_supervision WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND session_id=? AND phase='ADMITTED' AND receipt_id=? AND evidence_sha256=? AND lease_generation=? AND created_by=? LIMIT 2",node.tenant_id(),node.project_id(),node.run_id(),node.step_id(),node.session_id(),node.supervisor_receipt_id(),node.supervisor_receipt_sha256(),node.lease_generation(),text(original.receipt(),"created_by")));
        var evidence=schema.parse(str(supervision,"canonical_evidence"));if(!schema.hash(evidence).equals(node.supervisor_receipt_sha256())||!receipt.required("binding").equals(evidence.required("binding"))||!text(receipt,"deployment_sha256").equals(text(evidence,"deployment_sha256")))bad();
        boolean peer=false;for(var process:evidence.required("process_unit").required("processes"))if(process.required("pid").equals(receipt.required("peer_pid"))&&process.required("start_ticks").equals(receipt.required("peer_start_ticks"))&&process.required("exe_sha256").equals(receipt.required("peer_exe_sha256")))peer=true;if(!peer)bad();
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",node.tenant_id(),str(supervision,"audit_id")));var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",node.tenant_id(),str(supervision,"event_id")));
        for(String key:List.of("tenant_id","project_id","created_by","created_at","classification","schema_version","schema_manifest_sha256"))if(!Objects.equals(supervision.get(key),audit.get(key)))bad();
        if(!text(original.receipt(),"created_by").equals(str(audit,"subject_id"))||!"task.professional.admitted".equals(str(audit,"action"))||!"SUCCESS".equals(str(audit,"result"))||audit.get("reason_code")!=null||!node.session_id().equals(str(audit,"request_id"))||!node.supervisor_receipt_id().equals(str(audit,"trace_id"))||!evidence.equals(schema.parse(str(audit,"resource_ref"))))bad();
        if(!node.session_id().equals(str(event,"aggregate_id"))||!"1".equals(str(event,"aggregate_revision"))||!"task.professional.admitted".equals(str(event,"event_type"))||!Objects.equals(supervision.get("created_at"),event.get("occurred_at"))||!evidence.equals(schema.parse(str(event,"payload"))))bad();
        var session=one(db.queryForList("SELECT canonical_start,start_sha256 FROM professional_runtime_session WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND session_id=? AND lease_generation=? AND input_sha256=? AND action_id=? AND service_uri=? AND created_by=? LIMIT 2",node.tenant_id(),node.project_id(),node.run_id(),node.step_id(),node.session_id(),node.lease_generation(),node.node_input_sha256(),node.host_action_id(),node.worker_service_uri(),text(original.receipt(),"created_by")));
        var start=schema.parse(str(session,"canonical_start"));if(!schema.hash(start).equals(str(session,"start_sha256"))||!receipt.required("binding").equals(start.required("binding"))||!text(dependencies,"launch_prompt").equals(text(start,"prompt"))||!dependencies.required("prompt_compilation").equals(start.required("prompt_provenance"))||!text(receipt,"deployment_sha256").equals(text(start,"deployment_sha256")))bad();
    }
    /** Data-only integrity checker. It cannot mint a supervisor, package-read or send proof. */
    static void validateRecordedProjection(byte[] observed,JsonNode receipt,String session,String launch,List<String> systems,byte[] exactBytes,TaskInvocationContextSchema schemas)throws Exception {
        validateRecordedProjection(observed,receipt,session,launch,systems,exactBytes,schemas,TaskInvocationHistoryData.empty());
    }
    static void validateRecordedProjection(byte[] observed,JsonNode receipt,String session,String launch,List<String> systems,byte[] exactBytes,TaskInvocationContextSchema schemas,JsonNode history)throws Exception {
        TaskProfessionalSupervisor.validateProjection(observed);var projection=schemas.readProjection(observed);var exact=schemas.readExactInput(exactBytes);
        if(!"1.0.0".equals(text(receipt,"schema_version"))||!"POST".equals(text(receipt,"method"))||!"/v1/model-contexts".equals(text(receipt,"route"))||!session.equals(text(receipt,"session_id"))||!receipt.required("ordinal").isIntegralNumber()||!receipt.required("ordinal").canConvertToLong()
            ||!TaskInvocationContextSchema.PROJECTION_REF.equals(text(receipt,"projection_schema_ref"))||!"1.0.0".equals(text(receipt,"projection_schema_version"))||!schemas.sha256().equals(text(receipt,"projection_schema_sha256"))
            ||!WireValues.sha256(observed).equals(text(receipt,"body_sha256"))||!WireValues.sha256(observed).equals(text(receipt,"raw_body_sha256"))||observed.length!=receipt.required("raw_size_bytes").longValue()||!Arrays.equals(observed,Base64.getDecoder().decode(text(receipt,"body_base64"))))bad();
        var projected=TaskInvocationProjection.conversation(projection,session,launch,systems,projection.required("tools"),history,receipt.required("ordinal").longValue());
        if(!projected.equals(exact)||!Arrays.equals(exactBytes,ContentReadAuthorization.canonicalBytes(exact)))bad();
    }
    /** Strictly ordinal-decreasing original chain. These journal reads are factual only;
     * current Context separately re-runs result/capture accounting and source permissions. */
    private void validateRecordedHistory(TaskInvocationProtocol.Request request,String actor,JsonNode dependencies,long ordinal)throws Exception {
        var history=TaskInvocationHistoryData.fromDependencies(dependencies,ordinal);if(ordinal==1)return;
        var results=history.required("model_results");var last=results.get(results.size()-1);var pin=history.required("model_pins").get(results.size()-1);
        var previous=requireStored(request.node().tenant_id(),text(last,"context_manifest_id"),ordinal);
        if(previous.modelRequestOrdinal()!=ordinal-1||!request.model_config_revision().equals(previous.request().model_config_revision())
            ||!request.model_config_revision().equals(text(last,"model_config_revision")))bad();
        TaskInvocationHistoryData.sameSession(request.node(),actor,previous.request().node(),previous.actorId());
        if(!FormalTaskNodeAuthority.sameEvidence(TaskInvocationHistoryData.prefix(history,results.size()-1),
                TaskInvocationHistoryData.fromDependencies(previous.dependencies(),previous.modelRequestOrdinal())))bad();
        var resultReceipt=recordedModelObject(previous,last,pin,"MODEL_RESULT");
        var captureReceipt=recordedModelObject(previous,null,pin,"PROVIDER_CAPTURE");
        recordedAccounting(previous,pin,resultReceipt,captureReceipt);
        int prefix=TaskInvocationHistoryData.prefix(history,results.size()-1).required("tool_responses").size();
        for(int at=prefix;at<history.required("tool_responses").size();at++)recordedTool(request,actor,dependencies,history.required("tool_responses").get(at),history.required("tool_pins").get(at));
    }
    private JsonNode recordedModelObject(Stored previous,JsonNode expected,JsonNode pin,String kind)throws Exception {
        var node=previous.request().node();String invocation=text(pin,"invocation_id");
        var row=one(db.queryForList("SELECT o.receipt_json,o.receipt_sha256,o.created_at,t.canonical_payload FROM formal_invocation_registered_object o JOIN formal_invocation_object_intent t ON t.tenant_id=o.tenant_id AND t.write_id=o.write_id AND t.invocation_id=o.invocation_id AND t.kind=o.kind JOIN invocation i ON i.tenant_id=o.tenant_id AND i.invocation_id=o.invocation_id AND i.project_id=o.project_id AND i.created_by=o.created_by WHERE o.tenant_id=? AND o.project_id=? AND o.invocation_id=? AND o.kind=? AND o.created_by=? AND o.schema_version='1.0.0' AND o.schema_manifest_sha256=? AND i.context_manifest_id=? AND i.run_id=? AND i.step_id=? AND i.lease_generation=? AND i.status='SUCCEEDED' AND i.settlement_status='SETTLED' LIMIT 2",node.tenant_id(),node.project_id(),invocation,kind,previous.actorId(),schema.manifestSha256(),previous.contextId(),node.run_id(),node.step_id(),node.lease_generation()));
        var receipt=schema.parse(str(row,"receipt_json"));String hash=text(pin,"MODEL_RESULT".equals(kind)?"result_registration_sha256":"capture_registration_sha256");
        if(!hash.equals(str(row,"receipt_sha256"))||!hash.equals(schema.hash(receipt))||!schema.canonical(receipt).equals(str(row,"receipt_json")))bad();
        for(var entry:Map.of("tenant_id",node.tenant_id(),"project_id",node.project_id(),"run_id",node.run_id(),"invocation_id",invocation,"kind",kind,"created_by",previous.actorId(),"input_artifact_id",previous.request().exact_input().artifactId(),"execution_kind","TASK").entrySet())if(!entry.getValue().equals(text(receipt,entry.getKey())))bad();
        byte[] bytes=str(row,"canonical_payload").getBytes(StandardCharsets.UTF_8);var payload=schema.parse(str(row,"canonical_payload"));
        if("MODEL_RESULT".equals(kind))SchemaCatalogue.fromClasspath().validate("model-stream",payload,"ModelResult");
        if(!Arrays.equals(bytes,ContentReadAuthorization.canonicalBytes(payload))||!WireValues.sha256(bytes).equals(text(receipt.required("object_ref"),"sha256"))
            ||!Integer.toString(bytes.length).equals(text(receipt.required("object_ref"),"size_bytes"))||(expected!=null&&!FormalTaskNodeAuthority.sameEvidence(expected,payload)))bad();
        verifyOriginalOutput(previous,kind,bytes);
        requireJournal(node.tenant_id(),node.project_id(),previous.actorId(),"model.result_object_registered",text(receipt,"write_id"),invocation,text(receipt,"write_id"),receipt,row.get("created_at"),schema.manifestSha256());
        return receipt;
    }
    private void recordedAccounting(Stored previous,JsonNode pin,JsonNode result,JsonNode capture)throws Exception {
        var node=previous.request().node();String invocation=text(pin,"invocation_id");
        var row=one(db.queryForList("SELECT receipt_json,receipt_sha256,created_at FROM formal_invocation_accounting WHERE tenant_id=? AND project_id=? AND invocation_id=? AND created_by=? AND schema_version='1.0.0' AND schema_manifest_sha256=? LIMIT 2",node.tenant_id(),node.project_id(),invocation,previous.actorId(),schema.manifestSha256()));
        var receipt=schema.parse(str(row,"receipt_json"));String hash=text(pin,"accounting_sha256");
        if(!hash.equals(str(row,"receipt_sha256"))||!hash.equals(schema.hash(receipt))||!schema.canonical(receipt).equals(str(row,"receipt_json"))
            ||!"SUCCEEDED".equals(text(receipt,"status"))||!"SETTLED".equals(text(receipt,"settlement_status"))||!receipt.required("provider_terminal").asBoolean(false)
            ||!invocation.equals(text(receipt,"invocation_id"))||!node.tenant_id().equals(text(receipt,"tenant_id"))||!node.project_id().equals(text(receipt,"project_id"))
            ||!previous.actorId().equals(text(receipt,"created_by"))||!node.node_input_sha256().equals(text(receipt,"input_sha256")))bad();
        for(String key:List.of("result_registration_sha256","capture_registration_sha256"))if(!text(pin,key).equals(text(receipt,key)))bad();
        if(!FormalTaskNodeAuthority.sameEvidence(result.required("artifact_ref"),receipt.required("result_artifact"))
            ||!FormalTaskNodeAuthority.sameEvidence(capture.required("artifact_ref"),receipt.required("capture_artifact")))bad();
        requireJournal(node.tenant_id(),node.project_id(),previous.actorId(),"model.invocation_accounted",text(receipt,"usage_receipt_id"),invocation,text(receipt,"usage_receipt_id"),receipt,row.get("created_at"),schema.manifestSha256());
    }
    private void recordedTool(TaskInvocationProtocol.Request request,String actor,JsonNode dependencies,JsonNode response,JsonNode pin)throws Exception {
        if(Set.of("read_artifact","load_skill","ask_user_question","propose_web_acquisition").contains(text(pin.required("tool_call"),"tool_name"))){TaskRegisteredToolOriginalHistory.require(db,json,request,actor,dependencies,response,pin);return;}
        var node=request.node();String id=text(pin,"tool_receipt_id");var call=pin.required("tool_call");
        if(!TaskResearchToolReceipts.receiptId(node.session_id(),text(call,"tool_call_id")).equals(id)||!node.session_id().equals(text(call,"session_id"))
            ||!node.supervisor_receipt_id().equals(text(call,"supervision_receipt_id"))||!node.supervisor_receipt_sha256().equals(text(call,"supervision_sha256")))bad();
        String manifest;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-research-tool-receipt.schema.json"))){manifest=WireValues.sha256(in.readAllBytes());}
        var row=one(db.queryForList("SELECT resource_ref,created_at FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action='task.research.tool.result' AND subject_id=? AND created_by=? AND result='SUCCESS' AND reason_code IS NULL AND trace_id=? AND request_id=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? LIMIT 2",node.tenant_id(),node.project_id(),id,actor,actor,node.run_id(),text(response.required("response"),"action_id"),manifest,node.classification()));
        var document=schema.parse(str(row,"resource_ref"));ProfessionalRuntimeObservations.closed(document,Set.of("body","receipt_sha256"));var body=document.required("body");
        SchemaCatalogue.fromClasspath().validate("task-tool-read-success",response.required("response"),null);
        if(!text(pin,"tool_receipt_sha256").equals(text(document,"receipt_sha256"))||!schema.hash(body).equals(text(document,"receipt_sha256"))
            ||!schema.canonical(document).equals(str(row,"resource_ref")))bad();
        for(var entry:Map.of("session_id",node.session_id(),"input_id",node.node_input_artifact_id(),"input_sha256",node.node_input_sha256(),"input_receipt_sha256",node.node_input_receipt_sha256(),"receipt_id",id,"kind","TASK_TOOL_READ_RECEIPT","schema_version","1.0.0","response_sha256",text(pin,"response_sha256")).entrySet())if(!entry.getValue().equals(text(body,entry.getKey())))bad();
        if(!FormalTaskNodeAuthority.sameEvidence(dependencies.required("host_binding"),body.required("binding"))
            ||!FormalTaskNodeAuthority.sameEvidence(call,body.required("tool_call"))||!FormalTaskNodeAuthority.sameEvidence(response.required("response"),body.required("response")))bad();
        var originalRequest=body.required("request");
        if(!text(call,"tool_call_id").equals(text(originalRequest,"call_id"))||!text(call,"tool_name").equals(text(originalRequest,"name"))
            ||!text(call,"arguments_sha256").equals(text(originalRequest,"arguments_sha256"))||!schema.hash(originalRequest.required("arguments")).equals(text(call,"arguments_sha256")))bad();
        var event=one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type='task.research.tool.result' AND occurred_at=? LIMIT 2",(r,n)->r.getString(1),node.tenant_id(),id,node.run_id(),row.get("created_at")));
        if(!document.equals(schema.parse(event)))bad();
    }
    private void requireJournal(String tenant,String project,String actor,String action,String request,String trace,String aggregate,JsonNode expected,Object created,String manifest)throws Exception {
        var audit=one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND created_by=? AND action=? AND request_id=? AND trace_id=? AND result='SUCCESS' AND reason_code IS NULL AND created_at=? AND schema_manifest_sha256=? LIMIT 2",(r,n)->r.getString(1),tenant,project,actor,actor,action,request,trace,created,manifest));
        var event=one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type=? AND occurred_at=? LIMIT 2",(r,n)->r.getString(1),tenant,aggregate,action,created));
        if(!expected.equals(schema.parse(audit))||!expected.equals(schema.parse(event)))bad();
    }
    JsonNode contextDocument(String id,TaskInvocationProtocol.Request request,JsonNode source,String set,String tokens){
        var result=json.object().put("schema_version","1.0.0").put("tenant_id",request.node().tenant_id()).put("project_id",request.node().project_id()).put("context_manifest_id",id).put("execution_kind","TASK").put("prompt_revision",request.prompt_basis().version_id()).put("tokenizer_revision",request.tokenizer_basis().version_id()).put("token_count",tokens).put("dependency_access_set_id",set);
        result.set("exact_input_ref",json.tree(request.exact_input()));result.set("source_manifest",source);return result;
    }
    private static String schemaHash(JsonNode value){try{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}catch(Exception e){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}}
    private static void requireTenant(String tenant){if(!TransactionSynchronizationManager.isActualTransactionActive()||!TenantScope.requireCurrent().tenantId().equals(tenant))denied();}
    private static java.time.Instant instant(Map<String,Object> row,String key){var value=row.get(key);if(value instanceof java.sql.Timestamp t)return t.toInstant();if(value instanceof LocalDateTime t)return t.toInstant(java.time.ZoneOffset.UTC);bad();return null;}
    private static String text(JsonNode n,String key){var v=n.required(key);if(!v.isTextual())bad();return v.textValue();}
    private static String str(Map<String,Object> n,String key){var v=n.get(key);if(v==null)bad();return v.toString();}
    private static <T>T one(List<T> values){if(values.size()!=1||values.get(0)==null)bad();return values.get(0);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}private static void unavailable(){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
}
