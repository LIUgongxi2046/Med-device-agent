package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Independent immutable FIELD_TARGET result registration. Not IA-04 and never a professional PASS. */
public final class TaskStrictFieldResultRegistration {
    private final JdbcTemplate db;private final TaskStrictFieldSchema schema;private final StrictFieldComparisonSchema comparison;
    private final StrictComparisonRunScope scopes;private final StrictComparisonPolicyService policies;private final StrictFieldComparisonEngine.Limits limits;
    private final TaskStrictFieldSourceReader sources;
    public TaskStrictFieldResultRegistration(DataSource data,TaskStrictFieldSchema schema,StrictFieldComparisonSchema comparison,
            StrictComparisonRunScope scopes,StrictComparisonPolicyService policies,TaskStrictFieldSourceReader sources,StrictFieldComparisonEngine.Limits limits){
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.comparison=comparison;this.scopes=scopes;this.policies=policies;this.sources=sources;this.limits=limits;}
    JsonNode register(TaskStrictFieldNode.Current c,StrictFieldComparisonProtocol.Request request,TaskStrictFieldCheck.Evaluation evaluation,
            List<TaskStrictFieldSourceReader.Read> reads,TaskStrictFieldSourceReader sourceReader)throws Exception {
        if(sourceReader!=sources)deny();c.requireUnchanged();var existing=recover(c);if(existing!=null)return existing;
        requireCalculation(c,request,evaluation,reads);var input=c.input().input();
        String id=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();
        var body=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0")
            .put("kind","TASK_STRICT_FIELD_RESULT").put("target_kind","FIELD_TARGET").put("target_artifact_id",input.targetArtifactId())
            .put("target_object_sha256",input.targetObject().sha256()).put("target_registration_sha256",input.targetRegistrationSha256())
            .put("result_id",id).put("professional_status","UNKNOWN").put("disposition",evaluation.disposition().name());
        body.set("policies",policySnapshot(c));body.set("identity",c.identity());body.set("request",schema.tree(request));body.set("evaluation",schema.tree(evaluation));
        var sourcePins=body.putArray("source_reads");for(var read:reads){var pin=sourcePins.addObject().put("entry_id",read.entryId());if(read.receiptId()==null){pin.putNull("receipt_id");pin.putNull("receipt_sha256");}else{pin.put("receipt_id",read.receiptId());pin.put("receipt_sha256",read.receiptSha256());}}
        String hash=schema.hash(body);var receipt=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0")
            .put("kind","TASK_STRICT_FIELD_RESULT_RECEIPT").put("target_kind","FIELD_TARGET").put("result_id",id).put("result_sha256",hash)
            .put("target_artifact_id",input.targetArtifactId()).put("input_id",input.inputId()).put("input_sha256",c.input().inputSha256())
            .put("action_id",c.actionId()).put("lease_generation",c.generation()).put("disposition",evaluation.disposition().name())
            .put("audit_id",audit).put("event_id",event);
        String canonical=schema.canonical(receipt),receiptHash=schema.hash(receipt);var now=db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);
        changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'task.strict_field.result.registered',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",
            input.tenantId(),input.projectId(),audit,c.actorId(),canonical,id,input.runId(),schema.manifest(),c.actorId(),now,c.classification()));
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,'task.strict_field.result.registered',?,?)",input.tenantId(),event,id,canonical,now));
        changed(db.update("INSERT INTO task_strict_field_result(tenant_id,project_id,run_id,step_id,input_id,input_sha256,action_id,lease_generation,result_id,target_kind,target_artifact_id,disposition,result_json,result_sha256,receipt_json,receipt_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,'FIELD_TARGET',?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",
            input.tenantId(),input.projectId(),input.runId(),input.stepId(),input.inputId(),c.input().inputSha256(),c.actionId(),c.generation(),id,input.targetArtifactId(),evaluation.disposition().name(),schema.canonical(body),hash,canonical,receiptHash,audit,event,schema.manifest(),c.actorId(),now,c.classification()));
        c.requireUnchanged();return Objects.requireNonNull(recover(c));
    }
    JsonNode recover(TaskStrictFieldNode.Current c)throws Exception {
        c.requireUnchanged();var input=c.input().input();var rows=db.queryForList("SELECT * FROM task_strict_field_result WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",input.tenantId(),input.runId(),input.stepId(),c.actionId(),c.generation());
        if(rows.isEmpty())return null;var row=one(rows);var body=schema.read(str(row,"result_json"));var receipt=schema.read(str(row,"receipt_json"));
        if(!schema.hash(body).equals(str(row,"result_sha256"))||!schema.hash(receipt).equals(str(row,"receipt_sha256"))||!schema.manifest().equals(str(row,"schema_manifest_sha256"))
                ||!c.identity().equals(body.required("identity"))||!"FIELD_TARGET".equals(text(body,"target_kind"))||!"UNKNOWN".equals(text(body,"professional_status"))
                ||!input.targetArtifactId().equals(text(body,"target_artifact_id"))||!input.targetRegistrationSha256().equals(text(body,"target_registration_sha256"))
                ||!input.targetObject().sha256().equals(text(body,"target_object_sha256")))bad();
        for(var pin:Map.of("input_id",input.inputId(),"input_sha256",c.input().inputSha256(),"action_id",c.actionId(),"lease_generation",c.generation(),"target_artifact_id",input.targetArtifactId(),"created_by",c.actorId()).entrySet())if(!pin.getValue().equals(str(row,pin.getKey())))bad();
        for(String key:List.of("result_id","result_sha256","input_id","input_sha256","action_id","lease_generation","target_artifact_id","disposition","audit_id","event_id"))if(!str(row,key).equals(text(receipt,key)))bad();
        var sourceReads=new ArrayList<TaskStrictFieldSourceReader.Read>();
        for(var pin:body.required("source_reads")){
            if(pin.required("receipt_id").isNull()){if(!pin.required("receipt_sha256").isNull())bad();sourceReads.add(sources.requireMissing(c,text(pin,"entry_id")));continue;}
            var read=sources.requireReceipt(c,text(pin,"receipt_id"));if(!read.entryId().equals(text(pin,"entry_id"))||!read.receiptSha256().equals(text(pin,"receipt_sha256")))bad();sourceReads.add(read);}
        var request=comparison.request(body.required("request"));var evaluation=readEvaluation(body.required("evaluation"));requireCalculation(c,request,evaluation,sourceReads);
        if(!evaluation.disposition().name().equals(str(row,"disposition"))||!policySnapshot(c).equals(body.required("policies")))bad();
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",input.tenantId(),str(row,"audit_id")));
        if(!"task.strict_field.result.registered".equals(str(audit,"action"))||!"SUCCESS".equals(str(audit,"result"))||!receipt.equals(schema.read(str(audit,"resource_ref")))||!c.actorId().equals(str(audit,"subject_id")))bad();
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",input.tenantId(),str(row,"event_id")));
        if(!"task.strict_field.result.registered".equals(str(event,"event_type"))||!str(row,"result_id").equals(str(event,"aggregate_id"))||!receipt.equals(schema.read(str(event,"payload"))))bad();
        c.requireUnchanged();return receipt.deepCopy();
    }
    private JsonNode policySnapshot(TaskStrictFieldNode.Current current)throws Exception{
        var run=scopes.require(current.input().production());var values=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();var seen=new HashSet<PermissionProtocol.Basis>();
        for(var entry:current.input().input().policies())if(seen.add(entry.policy())){var approved=policies.requireApprovedFixedRun(run,entry.policy().version_id(),entry.policy().content_sha256());var decision=one(db.queryForList("SELECT content_sha256,entity_kind FROM version_registry WHERE tenant_id=? AND version_id=? FOR UPDATE",current.input().input().tenantId(),approved.decisionId()));if(!"STRICT_COMPARISON_DECISION".equals(str(decision,"entity_kind")))bad();values.add(schema.tree(Map.of("revision",approved.revisionId(),"digest",approved.digest(),"decision_id",approved.decisionId(),"decision_sha256",str(decision,"content_sha256"),"definition",approved.definition())));approved.requireUnchanged();}run.requireUnchanged();return values;
    }
    private TaskStrictFieldCheck.Evaluation readEvaluation(JsonNode node)throws Exception {
        ProfessionalRuntimeObservations.closed(node,Set.of("disposition","comparison","waiting_entries"));var ids=new ArrayList<String>();for(var id:node.required("waiting_entries")){WireValues.uuid(id.textValue());ids.add(id.textValue());}
        return new TaskStrictFieldCheck.Evaluation(TaskStrictFieldCheck.Disposition.valueOf(text(node,"disposition")),node.required("comparison").isNull()?null:comparison.result(node.required("comparison")),ids);
    }
    private void requireCalculation(TaskStrictFieldNode.Current c,StrictFieldComparisonProtocol.Request request,TaskStrictFieldCheck.Evaluation evaluation,List<TaskStrictFieldSourceReader.Read> reads)throws Exception {
        var input=c.input().input();var target=schema.target(input);if(reads.size()!=target.entries().size()||reads.stream().map(TaskStrictFieldSourceReader.Read::entryId).distinct().count()!=reads.size())bad();
        var scope=scopes.require(c.input().production());var definitions=new LinkedHashMap<PermissionProtocol.Basis,TaskStrictFieldCheck.Policy>();var expected=new ArrayList<StrictFieldComparisonProtocol.EntryRequest>();
        for(var selection:input.policies()){
            var approved=policies.requireApprovedFixedRun(scope,selection.policy().version_id(),selection.policy().content_sha256());definitions.putIfAbsent(selection.policy(),new TaskStrictFieldCheck.Policy(approved.revisionId(),approved.digest(),approved.definition()));
            var entry=target.entries().stream().filter(e->e.entryId().equals(selection.entryId())).findFirst().orElseThrow();var read=reads.stream().filter(r->r.entryId().equals(selection.entryId())).findFirst().orElseThrow();sources.requireCurrent(c,read);
            expected.add(new StrictFieldComparisonProtocol.EntryRequest(entry.entryId(),comparison.hash(entry),new StrictFieldComparisonProtocol.Rule(approved.revisionId(),approved.revisionId(),approved.digest(),approved.definition().operator().name()),read.source(),read.observation(approved.definition())));approved.requireUnchanged();}
        var actualRequest=new StrictFieldComparisonProtocol.Request(target.schemaVersion(),comparison.hash(target),target.identity(),expected);if(!actualRequest.equals(request))bad();
        var actual=new TaskStrictFieldCheck(comparison,StrictComparisonPolicyService.implementation()).evaluate(target,actualRequest,List.copyOf(definitions.values()),limits);
        if(!actual.equals(evaluation)||actual.disposition()==TaskStrictFieldCheck.Disposition.DENIED)bad();scope.requireUnchanged();c.requireUnchanged();
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String text(JsonNode n,String key){return n.required(key).textValue();}
    private static String str(Map<String,Object> n,String key){var v=n.get(key);if(v==null)bad();return v.toString();}
    private static void changed(int n){if(n!=1)bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
