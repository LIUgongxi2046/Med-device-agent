package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;

/** Disposes deterministic obligations, not business results or process exits.
 * The run-row fence serializes against every registration/commit authority. An
 * object PUT already outside SQL can remain UNKNOWN; it is never adopted here. */
final class TaskDeterministicStageCancellation {
    static final String ACTION="task.run.deterministic.disposed.v1";
    private static final String MANIFEST=org.apache.commons.codec.digest.DigestUtils.sha256Hex("TASK_DETERMINISTIC_DISPOSAL_V1:closed-run-fence:N03:N06:no-provider:no-session:no-success:no-object-adoption");
    private static final JsonMapper READER=new JsonMapper();
    static boolean fenced(JdbcTemplate db,TaskRunLifecycleAuthority.Proof p,String step)throws Exception {
        p.require(p.operation());if(!"CANCEL_REQUESTED".equals(p.status()))return false;
        var ref=p.reference();var definitions=db.queryForList("SELECT canonical_definition,definition_sha256 FROM runtime_node_definition WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND node_kind='N03' LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId(),step);
        if(definitions.isEmpty())return false;var definition=one(definitions);var value=READER.readTree(text(definition,"canonical_definition"));same(com.ruoyi.medical.contracts.WireValues.sha256(ContentReadAuthorization.canonicalBytes(value)),text(definition,"definition_sha256"));
        if(!p.graph().requiredStages().contains("N03"))bad();
        noExternal(db,p,step);
        var input=one(db.queryForList("SELECT canonical_input,input_sha256,receipt_json,receipt_sha256,created_by FROM task_strict_field_input WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId(),step));
        var canonical=READER.readTree(text(input,"canonical_input"));var receipt=READER.readTree(text(input,"receipt_json"));
        same(com.ruoyi.medical.contracts.WireValues.sha256(ContentReadAuthorization.canonicalBytes(canonical)),text(input,"input_sha256"));same(com.ruoyi.medical.contracts.WireValues.sha256(ContentReadAuthorization.canonicalBytes(receipt)),text(input,"receipt_sha256"));
        same("N03",canonical.required("node_kind").asText());same(ref.runId(),canonical.required("run_id").asText());same(step,canonical.required("step_id").asText());same(p.admission().actorId(),text(input,"created_by"));
        var audit=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action='task.strict_field.input.registered' AND result='SUCCESS' LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),receipt.required("audit_id").asText(),p.admission().actorId()));same(receipt,READER.readTree(text(audit,"resource_ref")));
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND event_type='task.strict_field.input.registered' LIMIT 2 FOR UPDATE",ref.tenantId(),receipt.required("event_id").asText()));same(receipt,READER.readTree(text(event,"payload")));
        var run=one(db.queryForList("SELECT dispatch_closed_at FROM task_run WHERE tenant_id=? AND run_id=? AND status='CANCEL_REQUESTED' LIMIT 2 FOR UPDATE",ref.tenantId(),ref.runId()));if(run.get("dispatch_closed_at")==null)bad();
        // A legacy claimed deterministic activity without an actual exit witness stays unknown.
        var executions=db.queryForList("SELECT state FROM task_deterministic_execution WHERE tenant_id=? AND run_id=? AND stage_kind='N03' LIMIT 2 FOR UPDATE",ref.tenantId(),ref.runId());return executions.size()==1&&"CLOSED".equals(text(executions.get(0),"state"));
    }
    static void noExternal(JdbcTemplate db,TaskRunLifecycleAuthority.Proof p,String step){var r=p.reference();for(String table:List.of("professional_runtime_session","invocation","task_section_writer_runtime_prepare","task_source_verifier_runtime_prepare"))if(!db.queryForList("SELECT step_id FROM "+table+" WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 1 FOR UPDATE",r.tenantId(),r.runId(),step).isEmpty())bad();}
    static JsonNode dispose(JdbcTemplate db,PermissionSchema json,TaskRunLifecycleAuthority.Proof p,String stage,String step,JsonNode original)throws Exception {
        p.require(TaskRunLifecycleAuthority.Operation.REQUEST_CANCEL);same("CANCEL_REQUESTED",p.status());if(!Set.of("N03","N06").contains(stage)||!p.graph().requiredStages().contains(stage))bad();noExternal(db,p,step);
        var ref=p.reference();var fence=one(db.queryForList("SELECT dispatch_closed_at FROM task_run WHERE tenant_id=? AND run_id=? AND status='CANCEL_REQUESTED' LIMIT 2 FOR UPDATE",ref.tenantId(),ref.runId()));if(fence.get("dispatch_closed_at")==null)bad();
        var request=json.object().put("run_id",ref.runId()).put("step_id",step).put("stage_kind",stage).put("graph_sha256",p.graph().graphSha256());request.set("original",original);
        var value=json.object().put("schema_version","1.0.0").put("kind","DETERMINISTIC_STAGE_DISPOSAL").put("disposition","CLOSED_FENCE_DISPOSAL").put("stage_satisfied",false).put("process_exit_asserted",false).put("physical_write_outcome","NOT_ASSERTED").put("dispatch_closed_at",time(fence,"dispatch_closed_at").toString());value.set("request",request);
        var journal=new TaskRunActivityJournal(db,json);String id=derived(ref.runId(),ACTION+":"+stage+":"+step),key=ACTION+":"+ref.runId()+":"+stage+":"+step;
        var saved=journal.read(p.admission(),ref.projectId(),ref.runId(),id,ACTION,key,step,journal.hash(request),p.classification(),"1.0.0",MANIFEST);if(saved.isPresent())same(value,saved.get());else journal.write(p.admission(),ref.projectId(),ref.runId(),id,ACTION,key,step,journal.hash(request),p.classification(),"1.0.0",MANIFEST,value);p.require(p.operation());return value;
    }
}
