package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;

/** Durable enter/exit of the actual server activity body. A private ticket can
 * record only its own finally exit, never a provider settlement or object result.
 * Lost worker/process exit remains OPEN/UNKNOWN; timeouts never close it. */
@org.springframework.stereotype.Component
final class TaskDeterministicExecution {
    private static final String MANIFEST=org.apache.commons.codec.digest.DigestUtils.sha256Hex("TASK_DETERMINISTIC_EXECUTION_V1:private-owner-finally:OPEN-is-UNKNOWN:no-lease-inference:no-provider-accounting");
    private final JdbcTemplate db;private final PermissionSchema json;private final TransactionTemplate transactions;
    private static final JsonMapper READER=new JsonMapper();
    TaskDeterministicExecution(@org.springframework.beans.factory.annotation.Qualifier("masterDataSource") DataSource data,PermissionSchema json){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;transactions=new TransactionTemplate(new DataSourceTransactionManager(data));}
    Ticket enter(TaskRunActivityPreparation.Current c,String stage,String command)throws Exception{
        c.requireUnchanged();var p=c.production();if(!Set.of("N03","N06").contains(stage)||!c.graph().requiredStages().contains(stage))bad();com.ruoyi.medical.contracts.WireValues.uuid(command);
        var existing=db.queryForList("SELECT execution_id FROM task_deterministic_execution WHERE tenant_id=? AND run_id=? AND stage_kind=? AND command_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.runId(),stage,command);if(!existing.isEmpty())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        String id=UUID.randomUUID().toString();var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
        var started=json.object().put("schema_version","1.0.0").put("kind","DETERMINISTIC_ACTIVITY_ENTERED").put("execution_id",id).put("tenant_id",p.tenantId()).put("project_id",p.projectId()).put("run_id",p.runId()).put("stage_kind",stage).put("command_id",command).put("created_by",p.actorId()).put("created_at",now.toString());String raw=json.canonical(started),hash=hash(started);
        changed(db.update("INSERT INTO task_deterministic_execution(tenant_id,project_id,run_id,stage_kind,command_id,execution_id,state,started_json,started_sha256,created_by,classification,created_at,schema_version,schema_manifest_sha256) VALUES(?,?,?,?,?,?,'OPEN',?,?,?,?,?,'1.0.0',?)",p.tenantId(),p.projectId(),p.runId(),stage,command,id,raw,hash,p.actorId(),p.classification(),now,MANIFEST));
        event(p.tenantId(),p.projectId(),p.runId(),p.actorId(),p.classification(),id,"task.deterministic.entered",raw,now);c.requireUnchanged();return new Ticket(p.tenantId(),p.projectId(),p.runId(),p.actorId(),p.classification(),stage,command,id,hash);
    }
    final class Ticket implements AutoCloseable {
        private final String tenant,project,run,actor,classification,stage,command,id,start;private boolean closed;private final Thread owner=Thread.currentThread();
        private Ticket(String tenant,String project,String run,String actor,String classification,String stage,String command,String id,String start){this.tenant=tenant;this.project=project;this.run=run;this.actor=actor;this.classification=classification;this.stage=stage;this.command=command;this.id=id;this.start=start;}
        @Override public void close(){if(Thread.currentThread()!=owner)bad();if(closed)return;
            if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())bad();
            transactions.executeWithoutResult(status->{try{
                var row=one(db.queryForList("SELECT * FROM task_deterministic_execution WHERE tenant_id=? AND run_id=? AND execution_id=? LIMIT 2 FOR UPDATE",tenant,run,id));same("OPEN",text(row,"state"));same(start,text(row,"started_sha256"));same(actor,text(row,"created_by"));
                var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
                var receipt=json.object().put("schema_version","1.0.0").put("kind","DETERMINISTIC_ACTIVITY_EXITED").put("execution_id",id).put("tenant_id",tenant).put("project_id",project).put("run_id",run).put("stage_kind",stage).put("command_id",command).put("started_sha256",start).put("closed_at",now.toString()).put("business_success_asserted",false).put("physical_write_outcome","NOT_ASSERTED");String raw=json.canonical(receipt);
                changed(db.update("UPDATE task_deterministic_execution SET state='CLOSED',closed_json=?,closed_sha256=?,closed_at=? WHERE tenant_id=? AND run_id=? AND execution_id=? AND state='OPEN' AND started_sha256=?",raw,hash(receipt),now,tenant,run,id,start));event(tenant,project,run,actor,classification,derived(id,"closed"),"task.deterministic.exited",raw,now);
            }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("DETERMINISTIC_EXIT_NOT_RECORDED",e);}});closed=true;
        }
    }
    static JsonNode facts(JdbcTemplate db,PermissionSchema json,TaskRunLifecycleAuthority.Proof p)throws Exception {
        var ref=p.reference();int maximum=Math.addExact(ref.maximumSteps(),4);var out=json.object();var entries=out.putArray("executions");boolean exited=true;
        var rows=db.queryForList("SELECT * FROM task_deterministic_execution WHERE tenant_id=? AND project_id=? AND run_id=? ORDER BY execution_id LIMIT ? FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId(),maximum+1);if(rows.size()>maximum)bad();
        for(var row:rows){String id=text(row,"execution_id");same(p.admission().actorId(),text(row,"created_by"));same(p.classification(),text(row,"classification"));same("1.0.0",text(row,"schema_version"));same(MANIFEST,text(row,"schema_manifest_sha256"));var started=READER.readTree(text(row,"started_json"));same(hash(started),text(row,"started_sha256"));same(id,started.required("execution_id").asText());same(ref.runId(),started.required("run_id").asText());same("1.0.0",started.required("schema_version").asText());same("DETERMINISTIC_ACTIVITY_ENTERED",started.required("kind").asText());for(String field:List.of("tenant_id","project_id","stage_kind","command_id","created_by"))same(text(row,field),started.required(field).asText());verifyEvent(db,row,id,"task.deterministic.entered",started);
            var entry=json.object();entry.set("entered",started);if("OPEN".equals(text(row,"state"))){exited=false;entry.putNull("exited");}else{same("CLOSED",text(row,"state"));var receipt=READER.readTree(text(row,"closed_json"));same(hash(receipt),text(row,"closed_sha256"));same(id,receipt.required("execution_id").asText());same(text(row,"started_sha256"),receipt.required("started_sha256").asText());same(false,receipt.required("business_success_asserted").booleanValue());same("NOT_ASSERTED",receipt.required("physical_write_outcome").asText());same("1.0.0",receipt.required("schema_version").asText());same("DETERMINISTIC_ACTIVITY_EXITED",receipt.required("kind").asText());for(String field:List.of("tenant_id","project_id","run_id","stage_kind","command_id"))same(text(row,field),receipt.required(field).asText());verifyEvent(db,row,derived(id,"closed"),"task.deterministic.exited",receipt);entry.set("exited",receipt);}entries.add(entry);
        }
        // Existing N06 dispatch without an owned exit is uncertainty (including old deployments).
        var assembly=db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND target_id=? AND operation_name=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.runId(),TaskCandidateAssemblyStageJournal.DISPATCH);
        for(var dispatched:assembly){String command=READER.readTree(text(dispatched,"response_ref")).required("command_id").asText();if(rows.stream().noneMatch(r->"N06".equals(text(r,"stage_kind"))&&command.equals(text(r,"command_id"))))exited=false;}
        out.put("activities_exited",exited);return out;
    }
    private static void verifyEvent(JdbcTemplate db,Map<String,Object> row,String id,String action,JsonNode expected)throws Exception{
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action=? AND result='SUCCESS' LIMIT 2 FOR UPDATE",text(row,"tenant_id"),text(row,"project_id"),derived(id,"audit"),text(row,"created_by"),action));
        same(MANIFEST,text(audit,"schema_manifest_sha256"));same("1.0.0",text(audit,"schema_version"));same(text(row,"created_by"),text(audit,"created_by"));same(text(row,"classification"),text(audit,"classification"));same(id,text(audit,"request_id"));same(text(row,"run_id"),text(audit,"trace_id"));same(expected,READER.readTree(text(audit,"resource_ref")));
        Object instant=row.get("task.deterministic.entered".equals(action)?"created_at":"closed_at");same(instant,audit.get("created_at"));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type=? LIMIT 2 FOR UPDATE",text(row,"tenant_id"),derived(id,"event"),text(row,"run_id"),action));same(expected,READER.readTree(text(event,"payload")));same("1",number(event,"aggregate_revision"));same(instant,event.get("occurred_at"));
    }
    private void event(String tenant,String project,String run,String actor,String classification,String id,String action,String raw,java.time.LocalDateTime now){changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",tenant,project,derived(id,"audit"),actor,action,raw,id,run,MANIFEST,actor,now,classification));changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",tenant,derived(id,"event"),run,action,raw,now));}
    private static String hash(JsonNode n)throws Exception{return com.ruoyi.medical.contracts.WireValues.sha256(ContentReadAuthorization.canonicalBytes(n));}
}
