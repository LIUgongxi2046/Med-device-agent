package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;

/** Finishes only a genuinely never-started RN-04 cancellation. The run row is the
 * same dispatch fence locked by launch/claim admission. Any committed launch
 * attempt, even an UNKNOWN/NOT_FOUND result, makes this route ineligible. */
@Service
public final class TaskRunPreRuntimeCancellation {
    private static final Contract CONTRACT=contract();
    private record Contract(String hash,com.networknt.schema.JsonSchema schema){}
    private static Contract contract(){try(var in=Objects.requireNonNull(TaskRunPreRuntimeCancellation.class.getResourceAsStream("/task-pre-runtime-cancellation.schema.json"))){byte[] bytes=in.readAllBytes();var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(new JsonMapper().readTree(bytes),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();return new Contract(WireValues.sha256(bytes),schema);}catch(Exception failure){throw new IllegalStateException("PRE_RUNTIME_CANCELLATION_CONTRACT_REQUIRED",failure);}}
    private static void validate(JsonNode receipt){if(!CONTRACT.schema().validate(receipt).isEmpty())bad();}
    private final EnterpriseAccess access;private final ResourceReadAuthority resources;
    private final JdbcTemplate db;private final PermissionSchema json;
    private final JsonMapper reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public TaskRunPreRuntimeCancellation(EnterpriseAccess access,ResourceReadAuthority resources,
            @Qualifier("masterDataSource") DataSource data,PermissionSchema json){this.access=access;this.resources=resources;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;}
    void finishIfNeverStarted(ApplicationSessionIdentity.Verified subject,String context,String runId){
        access.execute(subject,a->ThrowingSupplier.of(()->{
            if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            String tenant=a.context().tenant_id();var run=one(db.queryForList("SELECT * FROM task_run WHERE tenant_id=? AND run_id=? AND execution_kind='TASK' LIMIT 2 FOR UPDATE",tenant,runId));
            String project=text(run,"project_id"),task=text(run,"task_id");
            var scope=new PermissionProtocol.Scope(tenant,project,"TASK",task);
            var grant=resources.require(a,scope,"task.run","RUN_CANCELLATION");
            if(!"CANCEL_REQUESTED".equals(text(run,"status"))||run.get("started_at")!=null)return null;
            if(run.get("dispatch_closed_at")==null||run.get("ended_at")!=null)bad();
            var pointer=db.queryForList("SELECT * FROM task_active_run WHERE tenant_id=? AND project_id=? AND task_id=? LIMIT 2 FOR UPDATE",tenant,project,task);
            if(pointer.size()!=1||!runId.equals(text(pointer.get(0),"run_id")))return null;
            // A durable attempt is uncertainty, not absence. Never infer no execution
            // from a missing acceptance, expired lease or empty process inventory.
            for(String table:List.of("task_run_workflow_attempt","run_runtime_intent","run_step","step_runtime_attempt","invocation","professional_runtime_session"))
                if(!db.queryForList("SELECT run_id FROM "+table+" WHERE tenant_id=? AND run_id=? LIMIT 1 FOR UPDATE",tenant,runId).isEmpty())return null;
            var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='run.cancel.requested' LIMIT 2 FOR UPDATE",tenant,runId));
            var body=reader.readTree(text(event,"payload"));var c=body.required("command");var decision=body.required("decision");
            same("REQUEST_CANCEL",c.required("kind").asText());same(tenant,c.required("tenant_id").asText());same(project,c.required("project_id").asText());same(runId,c.required("run_id").asText());
            var unhashed=body.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)unhashed.required("command")).remove("command_sha256");same(hash(unhashed),c.required("command_sha256").asText());
            String decisionId=c.required("decision_receipt_id").asText(),command=c.required("command_id").asText();WireValues.uuid(decisionId);WireValues.uuid(command);
            var operation=one(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? AND operation_name='RN-04' AND target_id=? AND state='COMPLETED' AND http_status=200 LIMIT 2 FOR UPDATE",tenant,decisionId,runId));
            same(decision.required("actor_id").asText(),text(operation,"subject_id"));same(hash(decision.required("request")),text(operation,"request_sha256"));
            var response=reader.readTree(text(operation,"response_ref"));same(command,response.required("data").required("command_id").asText());same(runId,response.required("data").required("run").required("run_id").asText());
            var audit=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND action='run.cancel.requested' AND request_id=? AND result='SUCCESS' LIMIT 2 FOR UPDATE",tenant,project,text(operation,"subject_id"),response.required("request_id").asText()));
            var evidence=reader.readTree(text(audit,"resource_ref"));same(decision.required("request"),evidence.required("request"));same(response.required("data"),evidence.required("result"));same(decisionId,evidence.required("operation_id").asText());
            String id=UUID.randomUUID().toString(),revision=PermissionSchema.next(number(run,"revision")),classification=text(run,"classification");
            var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
            var receipt=json.object().put("schema_version","1.0.0").put("kind","TASK_PRE_RUNTIME_CANCELLED").put("receipt_id",id).put("run_id",runId).put("decision_id",decisionId).put("command_id",command).put("status","CANCELLED").put("revision",revision).put("started",false).put("ended_at",now.toInstant(java.time.ZoneOffset.UTC).toString());
            receipt.put("decision_sha256",hash(body));receipt.put("authority_grant_id",grant.grantId()).put("authority_revision",grant.revision().toString());
            validate(receipt);String canonical=json.canonical(receipt),hash=hash(receipt);
            changed(db.update("INSERT INTO task_run_preruntime_termination(tenant_id,project_id,task_id,run_id,receipt_id,decision_id,command_id,receipt_json,receipt_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",tenant,project,task,runId,id,decisionId,command,canonical,hash,CONTRACT.hash(),a.actorId(),now,classification));
            same(grant,resources.require(a,scope,"task.run","RUN_CANCELLATION"));
            changed(db.update("UPDATE task_run SET status='CANCELLED',ended_at=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND run_id=? AND status='CANCEL_REQUESTED' AND started_at IS NULL AND dispatch_closed_at IS NOT NULL AND revision=?",now,now,tenant,project,runId,number(run,"revision")));
            var head=one(db.queryForList("SELECT current_revision,status,revision FROM task_head WHERE tenant_id=? AND project_id=? AND task_id=? LIMIT 2 FOR UPDATE",tenant,project,task));same(text(run,"task_revision"),text(head,"current_revision"));same("CANCEL_REQUESTED",text(head,"status"));
            changed(db.update("UPDATE task_head SET status='CANCELLED',revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND task_id=? AND revision=? AND status='CANCEL_REQUESTED'",now,tenant,project,task,number(head,"revision")));
            changed(db.update("DELETE FROM task_active_run WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND revision=?",tenant,project,task,runId,number(pointer.get(0),"revision")));
            changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'task.run.pre-runtime-cancelled',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",tenant,project,id,a.actorId(),canonical,id,runId,CONTRACT.hash(),a.actorId(),now,classification));
            changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,?,'task.run.pre-runtime-cancelled',?,?)",tenant,id,runId,revision,canonical,now));
            var stream=one(db.queryForList("SELECT stream_id,last_sequence,revision FROM stream_registry WHERE tenant_id=? AND project_id=? AND kind='RUN' AND owner_resource_id=? LIMIT 2 FOR UPDATE",tenant,project,runId));
            String sequence=PermissionSchema.next(number(stream,"last_sequence")),eventId=UUID.randomUUID().toString(),streamId=text(stream,"stream_id");
            var payload=json.object().put("business_event_type","RUN_CANCELLED").put("status","CANCELLED").put("reason_code","USER_CANCELLED").put("read_operation_id","RN-02");
            var published=json.object().put("schema_version","1.0.0").put("event_id",eventId).put("sequence",sequence).put("event_type","run.updated").put("tenant_id",tenant).put("project_id",project).put("task_id",task).put("run_id",runId).put("resource_kind","RUN").put("resource_id",runId).put("resource_revision",revision).put("occurred_at",now.toInstant(java.time.ZoneOffset.UTC).toString());
            published.set("stream",json.object().put("stream_id",streamId).put("kind","RUN").put("resource_id",runId));published.set("payload",payload);
            SchemaCatalogue.fromClasspath().validate("interfaces",published,"PublicEvent");
            changed(db.update("UPDATE stream_registry SET last_sequence=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND stream_id=? AND last_sequence=? AND revision=?",sequence,now,tenant,project,streamId,number(stream,"last_sequence"),number(stream,"revision")));
            changed(db.update("INSERT INTO stream_event(tenant_id,project_id,event_id,stream_id,sequence,task_id,run_id,resource_kind,resource_id,resource_revision,event_type,payload,occurred_at,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,'RUN',?,?,'run.updated',?,?,'1.0.0',?,?,?,?)",tenant,project,eventId,streamId,sequence,task,runId,runId,revision,json.canonical(payload),now,json.manifestSha256(),a.actorId(),now,classification));
            changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,?,'run.updated',?,?)",tenant,eventId,runId,revision,json.canonical(published),now));
            same(grant,resources.require(a,scope,"task.run","RUN_CANCELLATION"));
            return null;
        }).get());
    }
    Optional<JsonNode> original(EnterpriseAccess.Admission a,String runId)throws Exception {
        a.recoveryProof().requireActive();String tenant=a.context().tenant_id();
        var rows=db.queryForList("SELECT * FROM task_run_preruntime_termination WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",tenant,runId);
        if(rows.isEmpty())return Optional.empty();var row=one(rows);var receipt=reader.readTree(text(row,"receipt_json"));validate(receipt);same(CONTRACT.hash(),text(row,"schema_manifest_sha256"));same(hash(receipt),text(row,"receipt_sha256"));same(json.canonical(receipt),text(row,"receipt_json"));
        same(runId,receipt.required("run_id").asText());same(text(row,"receipt_id"),receipt.required("receipt_id").asText());same("TASK_PRE_RUNTIME_CANCELLED",receipt.required("kind").asText());
        var run=one(db.queryForList("SELECT status,started_at,ended_at,revision FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? LIMIT 2 FOR UPDATE",tenant,text(row,"project_id"),runId));same("CANCELLED",text(run,"status"));if(run.get("started_at")!=null||run.get("ended_at")==null)bad();
        var audit=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action='task.run.pre-runtime-cancelled' AND result='SUCCESS' LIMIT 2 FOR UPDATE",tenant,text(row,"project_id"),text(row,"receipt_id"),text(row,"created_by")));same(text(row,"receipt_json"),text(audit,"resource_ref"));
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type='task.run.pre-runtime-cancelled' LIMIT 2 FOR UPDATE",tenant,text(row,"receipt_id"),runId));same(text(row,"receipt_json"),text(event,"payload"));
        a.recoveryProof().requireActive();return Optional.of(receipt);
    }
    private String hash(JsonNode node)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(node));}
}
