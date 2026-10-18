package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.SimulationInvocationStore.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Producer writes canonical runtime rows; the job table only binds an immutable evaluation cell. */
final class SimulationEvaluationStore {
    private final JdbcTemplate jdbc;
    SimulationEvaluationStore(DataSource data){jdbc=new JdbcTemplate(Objects.requireNonNull(data));}
    private String tenant(){TenantScope.requireSqlAccess("SimulationEvaluationStore");return TenantScope.requireCurrent().tenantId();}
    LocalDateTime now(){tenant();return Objects.requireNonNull(jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class));}
    List<Map<String,Object>> cell(JsonNode request){return jdbc.queryForList("""
        SELECT * FROM simulation_evaluation_job WHERE tenant_id=? AND evaluation_id=? AND sample_id=? AND agent_version_id=? AND repeat_index=? FOR UPDATE
        """,tenant(),text(request,"evaluation_id"),text(request,"sample_id"),text(request,"agent_version_id"),request.path("repeat_index").intValue());}
    Map<String,Object> job(String run){return single(jdbc.queryForList("SELECT * FROM simulation_evaluation_job WHERE tenant_id=? AND run_id=? FOR UPDATE",tenant(),run));}
    Map<String,Object> evaluation(String id){return single(jdbc.queryForList("SELECT * FROM evaluation_batch WHERE tenant_id=? AND evaluation_id=? FOR UPDATE",tenant(),id));}
    String versionHash(String version){return single(jdbc.queryForList("SELECT content_sha256 FROM version_registry WHERE tenant_id=? AND version_id=? FOR UPDATE",tenant(),version)).get("content_sha256").toString();}
    List<Map<String,Object>> account(String id){return jdbc.queryForList("SELECT * FROM simulation_quota_account WHERE tenant_id=? AND account_id=? FOR UPDATE",tenant(),id);}
    List<Map<String,Object>> retention(String id){return jdbc.queryForList("SELECT * FROM simulation_retention_policy WHERE tenant_id=? AND policy_id=? FOR UPDATE",tenant(),id);}
    void insert(String table,Map<String,Object> values){
        tenant();if(!Set.of("execution_registry","system_job_run","run_principal","run_step","step_action_binding","simulation_evaluation_job",
                "simulation_scope_manifest","version_registry","simulation_quota_account","simulation_retention_policy","simulation_execution_authority","principal_token").contains(table))throw new IllegalArgumentException("INVALID_PRODUCER_TABLE");
        if(!tenant().equals(values.get("tenant_id")))denied();
        for(String key:values.keySet())if(!key.matches("[a-z][a-z_0-9]*"))throw new IllegalArgumentException("INVALID_PRODUCER_COLUMN");
        one(jdbc.update("INSERT INTO "+table+" ("+String.join(",",values.keySet())+") VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+")",values.values().toArray()));
    }
    void claim(Map<String,Object> job,LocalDateTime expiry){
        String run=str(job,"run_id"),step=str(job,"step_id");
        one(jdbc.update("""
            UPDATE run_step SET status='RUNNING',lease_owner=?,lease_expires_at=?,lease_generation=1,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND run_id=? AND step_id=? AND status='READY' AND revision=1 AND lease_generation=0 AND lease_owner IS NULL AND lease_expires_at IS NULL
            """,str(job,"service_uri"),expiry,tenant(),run,step));
        one(jdbc.update("""
            UPDATE step_action_binding SET lease_generation=1,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND state='BOUND' AND lease_generation=0 AND revision=1 AND invocation_id IS NULL
            """,tenant(),run,step,str(job,"action_id")));
        one(jdbc.update("""
            UPDATE system_job_run SET status='RUNNING',started_at=UTC_TIMESTAMP(6),revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND run_id=? AND status='QUEUED' AND started_at IS NULL AND ended_at IS NULL AND workflow_id IS NULL AND job_type='SIMULATION_EVALUATION'
            """,tenant(),run));
        one(jdbc.update("""
            UPDATE simulation_evaluation_job SET status='CLAIMED',revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND run_id=? AND status='READY' AND revision=?
            """,tenant(),run,job.get("revision")));
    }
    void issued(Map<String,Object> job,String authority,String request){
        one(jdbc.update("""
            UPDATE simulation_evaluation_job SET status='ISSUED',authority_id=?,admission_json=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND run_id=? AND status='CLAIMED' AND revision=? AND authority_id IS NULL AND admission_json IS NULL
            """,authority,request,tenant(),str(job,"run_id"),job.get("revision")));
    }
    void event(String project,String actor,String run,String action,String payload,String manifest,String classification){
        long revision=1;
        if(action.startsWith("simulation.evaluation."))revision=count(job(run),"revision");
        String id=UUID.randomUUID().toString();
        one(jdbc.update("""
            INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES(?,?,?,?,?,?,'SUCCESS',NULL,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
            """,tenant(),project,id,actor,action,payload,id,id,manifest,actor,classification));
        one(jdbc.update("""
            INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at,published_at)
            VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6),NULL)
            """,tenant(),UUID.randomUUID().toString(),run,revision,action,payload));
    }
    static Map<String,Object> row(Object... pairs){var value=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)value.put((String)pairs[i],pairs[i+1]);return value;}
    static void one(int count){if(count!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
}
