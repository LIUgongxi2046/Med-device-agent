package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** The canonical invocation/action ledger, never a separate simulation dispatch queue. */
final class SimulationInvocationStore {
    private final JdbcTemplate jdbc;
    private final SimulationInvocationSchema schema;
    SimulationInvocationStore(DataSource data,SimulationInvocationSchema schema) { jdbc=new JdbcTemplate(data); this.schema=schema; }
    String tenant() { TenantScope.requireSqlAccess("SimulationInvocationStore"); return TenantScope.requireCurrent().tenantId(); }
    LocalDateTime now() { tenant(); return Objects.requireNonNull(jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class)); }
    Map<String,Object> authority(String id) { return single(jdbc.queryForList("SELECT * FROM simulation_execution_authority WHERE tenant_id=? AND authority_id=? FOR UPDATE",tenant(),id)); }
    Map<String,Object> action(JsonNode id) {
        return single(jdbc.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? FOR UPDATE",
            tenant(),text(id,"run_id"),text(id,"step_id"),text(id,"action_id")));
    }
    Map<String,Object> step(JsonNode id) {
        return single(jdbc.queryForList("SELECT * FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? FOR UPDATE",tenant(),text(id,"run_id"),text(id,"step_id")));
    }
    Map<String,Object> job(String id) { return single(jdbc.queryForList("SELECT * FROM system_job_run WHERE tenant_id=? AND run_id=? FOR UPDATE",tenant(),id)); }
    Map<String,Object> evaluation(String id) { return single(jdbc.queryForList("SELECT * FROM evaluation_batch WHERE tenant_id=? AND evaluation_id=? FOR UPDATE",tenant(),id)); }
    Map<String,Object> principal(String id) { return single(jdbc.queryForList("SELECT * FROM run_principal WHERE tenant_id=? AND principal_id=? FOR UPDATE",tenant(),id)); }
    Map<String,Object> config(String id) { return single(jdbc.queryForList("SELECT * FROM model_config_revision WHERE tenant_id=? AND model_config_revision=? FOR UPDATE",tenant(),id)); }
    Map<String,Object> context(String id) { return single(jdbc.queryForList("SELECT * FROM context_manifest WHERE tenant_id=? AND context_manifest_id=? FOR UPDATE",tenant(),id)); }
    Map<String,Object> retention(String id) { return single(jdbc.queryForList("SELECT * FROM simulation_retention_policy WHERE tenant_id=? AND policy_id=? FOR UPDATE",tenant(),id)); }
    Map<String,Object> account(String id) { return single(jdbc.queryForList("SELECT * FROM simulation_quota_account WHERE tenant_id=? AND account_id=? FOR UPDATE",tenant(),id)); }
    List<Map<String,Object>> invocationForAction(JsonNode id) {
        return jdbc.queryForList("SELECT * FROM invocation WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? FOR UPDATE",tenant(),text(id,"run_id"),text(id,"step_id"),text(id,"action_id"));
    }
    Map<String,Object> invocation(String id) { return single(jdbc.queryForList("SELECT * FROM invocation WHERE tenant_id=? AND invocation_id=? FOR UPDATE",tenant(),id)); }
    Map<String,Object> provenance(String id) { return single(jdbc.queryForList("SELECT * FROM simulation_invocation_provenance WHERE tenant_id=? AND invocation_id=? FOR UPDATE",tenant(),id)); }
    void reserveAccount(Map<String,Object> account,long units) {
        one(jdbc.update("""
            UPDATE simulation_quota_account SET reserved_units=reserved_units+?,reserved_calls=reserved_calls+1,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND account_id=? AND revision=? AND revision<9007199254740991 AND status='ACTIVE'
              AND ?<=max_units-spent_units-reserved_units AND reserved_calls<max_calls-spent_calls
            """,units,tenant(),str(account,"account_id"),account.get("revision"),units));
    }
    void reserve(JsonNode request,String configId,String actor,String classification,long units) throws IOException {
        var proof=request.required("authority_proof"); var binding=request.required("binding"); var id=binding.required("identity");
        String invocation=text(id,"invocation_id"),project=nullable(id,"project_id");
        one(jdbc.update("""
            INSERT INTO invocation (tenant_id,project_id,invocation_id,run_id,step_id,action_id,config_id,config_revision,
              context_manifest_id,request_parameters,input_sha256,status,settlement_status,dispatch_generation,lease_generation,
              schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at,execution_mode)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,'RESERVED','PENDING',0,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6),'SIMULATION')
            """,tenant(),project,invocation,text(id,"run_id"),text(id,"step_id"),text(id,"action_id"),configId,text(id,"model_config_revision"),
            text(id,"context_manifest_id"),schema.canonical(request.required("request_parameters")),text(proof,"input_sha256"),proof.path("lease_generation").longValue(),schema.manifest(),actor,classification));
        one(jdbc.update("""
            INSERT INTO simulation_invocation_provenance (tenant_id,project_id,invocation_id,authority_id,binding,exact_input,request_parameters,simulation_case,settings,
              binding_sha256,retention_deadline,schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
            """,tenant(),project,invocation,text(proof,"authority_id"),schema.canonical(binding),schema.canonical(request.required("exact_input")),
            schema.canonical(request.required("request_parameters")),schema.canonical(request.required("simulation_case")),schema.canonical(request.required("settings")),
            schema.hash(binding),PermissionAuthority.time(text(proof,"retention_deadline")),schema.manifest(),actor,classification));
        for(String field:List.of("enterprise_account_id","parent_account_id")) one(jdbc.update("""
            INSERT INTO simulation_quota_reservation (tenant_id,project_id,invocation_id,account_id,reserved_units,reserved_calls,status,revision,updated_at,created_by,created_at,classification)
            VALUES (?,?,?,?,?,1,'HELD',1,UTC_TIMESTAMP(6),?,UTC_TIMESTAMP(6),?)
            """,tenant(),project,invocation,text(proof,field),units,actor,classification));
        one(jdbc.update("""
            UPDATE step_action_binding SET invocation_id=?,state='RESERVED',revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND state='BOUND' AND invocation_id IS NULL
            """,invocation,tenant(),text(id,"run_id"),text(id,"step_id"),text(id,"action_id")));
        event(request,"simulation.invocation.reserved",actor,classification);
    }
    boolean dispatch(Map<String,Object> invocation,JsonNode request,String actor,String classification) throws IOException {
        int changed=jdbc.update("""
            UPDATE invocation SET status='DISPATCHING',dispatch_generation=1,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND invocation_id=? AND execution_mode='SIMULATION' AND status='RESERVED' AND dispatch_generation=0 AND revision=?
            """,tenant(),str(invocation,"invocation_id"),invocation.get("revision"));
        if(changed==0) return false; one(changed);
        var id=request.required("binding").required("identity");
        one(jdbc.update("""
            UPDATE step_action_binding SET state='DISPATCHING',revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND invocation_id=? AND state='RESERVED'
            """,tenant(),text(id,"run_id"),text(id,"step_id"),text(id,"action_id"),text(id,"invocation_id")));
        event(request,"simulation.invocation.dispatching",actor,classification); return true;
    }
    void observe(Map<String,Object> invocation,JsonNode request,String status,String actor,String classification) throws IOException {
        one(jdbc.update("UPDATE invocation SET status=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND invocation_id=? AND revision=? AND execution_mode='SIMULATION'",
            status,tenant(),str(invocation,"invocation_id"),invocation.get("revision")));
        var id=request.required("binding").required("identity");
        one(jdbc.update("UPDATE step_action_binding SET state=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND invocation_id=?",
            status,tenant(),text(id,"run_id"),text(id,"step_id"),text(id,"action_id"),text(id,"invocation_id")));
        event(request,"simulation.invocation."+status.toLowerCase(java.util.Locale.ROOT),actor,classification);
    }
    void release(JsonNode request,long units) {
        var proof=request.required("authority_proof"); String invocation=text(request.required("binding").required("identity"),"invocation_id");
        for(String field:List.of("enterprise_account_id","parent_account_id")) {
            String account=text(proof,field);
            one(jdbc.update("UPDATE simulation_quota_account SET reserved_units=reserved_units-?,reserved_calls=reserved_calls-1,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND account_id=? AND reserved_units>=? AND reserved_calls>=1 AND revision<9007199254740991",units,tenant(),account,units));
            one(jdbc.update("UPDATE simulation_quota_reservation SET status='RELEASED',charged_units=0,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND invocation_id=? AND account_id=? AND status='HELD' AND reserved_units=? AND reserved_calls=1",tenant(),invocation,account,units));
        }
    }
    void requireHeld(JsonNode request,long units) {
        var proof=request.required("authority_proof"); String invocation=text(request.required("binding").required("identity"),"invocation_id");
        var rows=jdbc.queryForList("SELECT * FROM simulation_quota_reservation WHERE tenant_id=? AND invocation_id=? FOR UPDATE",tenant(),invocation);
        if(rows.size()!=2) denied();
        var expected=java.util.Set.of(text(proof,"enterprise_account_id"),text(proof,"parent_account_id"));
        if(!rows.stream().map(row->str(row,"account_id")).collect(java.util.stream.Collectors.toSet()).equals(expected)) denied();
        for(var row:rows) if(!"HELD".equals(str(row,"status"))||count(row,"reserved_units")!=units||count(row,"reserved_calls")!=1||row.get("charged_units")!=null) denied();
    }
    Map<String,Object> factOwner(String tenant,String invocation) {
        com.ruoyi.medical.contracts.WireValues.uuid(tenant); com.ruoyi.medical.contracts.WireValues.uuid(invocation);
        return single(jdbc.queryForList("""
            SELECT a.created_by,a.service_uri FROM simulation_invocation_provenance p
            JOIN simulation_execution_authority a ON a.tenant_id=p.tenant_id AND a.authority_id=p.authority_id
            WHERE p.tenant_id=? AND p.invocation_id=?
            """,tenant,invocation));
    }
    long retainedFactBytes(String invocation)throws IOException {
        var rows=jdbc.queryForList("SELECT payload FROM simulation_invocation_fact WHERE tenant_id=? AND invocation_id=? AND kind='LATE_RESULT' FOR UPDATE",tenant(),invocation);
        if(rows.isEmpty())return 0;
        return schema.canonical(schema.parse(str(single(rows),"payload"))).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
    Map<String,Object> factRow(String invocation) {
        return single(jdbc.queryForList("SELECT * FROM simulation_invocation_fact WHERE tenant_id=? AND invocation_id=? AND kind='LATE_RESULT' FOR UPDATE",tenant(),invocation));
    }
    void requireFact(JsonNode request,JsonNode payload) throws IOException {
        var row=factRow(text(request.required("binding").required("identity"),"invocation_id"));
        if(!schema.hash(payload).equals(str(row,"payload_sha256"))||!schema.canonical(payload).equals(schema.canonical(schema.parse(str(row,"payload")))))
            throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
    }
    void requireSettledFact(JsonNode request,JsonNode payload,Map<String,Object> invocation)throws IOException {
        var id=request.required("binding").required("identity");
        var row=single(jdbc.queryForList("SELECT * FROM simulation_quota_settlement WHERE tenant_id=? AND invocation_id=? FOR UPDATE",tenant(),text(id,"invocation_id")));
        if(!text(payload,"receipt_id").equals(str(row,"receipt_id"))||!text(payload,"result_sha256").equals(str(row,"result_sha256"))
            ||!text(payload,"provenance_sha256").equals(str(row,"provenance_sha256"))||!str(invocation,"status").equals(str(row,"execution_status"))
            ||!Objects.equals(str(invocation,"result_artifact_id"),str(row,"result_artifact_id"))||str(row,"result_artifact_id")==null
            ||!Objects.equals(str(invocation,"result_sha256"),str(row,"result_sha256"))||!"SIMULATION".equals(str(row,"execution_mode")))
            throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }
    boolean fact(JsonNode request,JsonNode payload,String actor,String classification) throws IOException {
        String invocation=text(request.required("binding").required("identity"),"invocation_id"),hash=schema.hash(payload);
        var prior=jdbc.queryForList("SELECT payload_sha256,payload FROM simulation_invocation_fact WHERE tenant_id=? AND invocation_id=? AND kind='LATE_RESULT' FOR UPDATE",tenant(),invocation);
        if(!prior.isEmpty()) {
            var original=single(prior);
            if(!hash.equals(str(original,"payload_sha256"))||!schema.canonical(payload).equals(schema.canonical(schema.parse(str(original,"payload")))))
                throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return false;
        }
        one(jdbc.update("""
            INSERT INTO simulation_invocation_fact (tenant_id,project_id,fact_id,invocation_id,kind,payload,payload_sha256,received_at,
              schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES (?,?,?,?,'LATE_RESULT',?,?,UTC_TIMESTAMP(6),'1.0.0',?,?,UTC_TIMESTAMP(6),?)
            """,tenant(),nullable(request.required("authority_proof"),"project_id"),UUID.randomUUID().toString(),invocation,schema.canonical(payload),hash,schema.manifest(),actor,classification));
        event(request,"simulation.invocation.late_fact_recorded",actor,classification); return true;
    }
    void event(JsonNode request,String event,String actor,String classification) throws IOException {
        var id=request.required("binding").required("identity"); String invocation=text(id,"invocation_id");
        // Metadata carries binding hashes/identities, never exact prompts or fixture output.
        var metadata=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(); metadata.put("schema_version","1.0.0");
        metadata.set("identity",id); metadata.put("binding_sha256",schema.hash(request.required("binding"))); metadata.put("event",event);
        String detail=schema.canonical(metadata),audit=UUID.randomUUID().toString();
        one(jdbc.update(RunTokenAuthorityStore.AUDIT,tenant(),nullable(id,"project_id"),audit,actor,event,detail,invocation,invocation,schema.manifest(),actor,classification));
        one(jdbc.update("""
            INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at,published_at)
            VALUES (?,?,?,(SELECT revision FROM invocation WHERE tenant_id=? AND invocation_id=?),?,?,UTC_TIMESTAMP(6),NULL)
            """,tenant(),UUID.randomUUID().toString(),invocation,tenant(),invocation,event,detail));
    }
    static String text(JsonNode node,String key) { return node.required(key).textValue(); }
    static String nullable(JsonNode node,String key) { return node.required(key).isNull()?null:text(node,key); }
    static String str(Map<String,Object> row,String key) { return row.get(key)==null?null:row.get(key).toString(); }
    static long count(Map<String,Object> row,String key) { try { long value=new java.math.BigInteger(str(row,key)).longValueExact(); if(value<0||value>SimulationInvocationSchema.MAX) denied(); return value; } catch(ArithmeticException|NumberFormatException failure) { denied(); return 0; } }
    static <T>T single(List<T> values) { if(values.size()!=1) denied(); return values.get(0); }
    static void one(int count) { if(count!=1) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT); }
    static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
}
