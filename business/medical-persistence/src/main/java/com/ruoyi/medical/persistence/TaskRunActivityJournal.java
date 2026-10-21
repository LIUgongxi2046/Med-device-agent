package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Package-private immutable journal mechanics only. Every caller must first hold
 * its specific private production/received proof; this utility grants no authority. */
final class TaskRunActivityJournal {
    private final JdbcTemplate db;private final PermissionSchema json;
    private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    TaskRunActivityJournal(JdbcTemplate db,PermissionSchema json){this.db=db;this.json=json;}
    Optional<JsonNode> read(EnterpriseAccess.Admission a,String project,String run,String id,String action,String key,String requestId,String requestHash,String classification,String version,String manifest)throws Exception{
        return readOriginalActor(a,a.actorId(),project,run,id,action,key,requestId,requestHash,classification,version,manifest);
    }
    /** Factual mechanics only. Caller must hold its separate private original proof;
     * the actual current admission/tenant actor is never replaced. */
    Optional<JsonNode> readOriginalActor(EnterpriseAccess.Admission a,String originalActor,String project,String run,String id,String action,String key,String requestId,String requestHash,String classification,String version,String manifest)throws Exception{
        active(a);var rows=db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),id);if(rows.isEmpty())return Optional.empty();var op=one(rows);var value=reader.readTree(text(op,"response_ref"));String canonical=json.canonical(value),tenant=a.context().tenant_id(),actor=originalActor;
        for(var pin:Map.ofEntries(Map.entry("tenant_id",tenant),Map.entry("operation_id",id),Map.entry("subject_id",actor),Map.entry("operation_name",action),Map.entry("idempotency_key",key),Map.entry("request_sha256",requestHash),Map.entry("state","COMPLETED"),Map.entry("response_ref",canonical),Map.entry("target_id",run),Map.entry("schema_version",version),Map.entry("schema_manifest_sha256",manifest),Map.entry("created_by",actor),Map.entry("classification",classification)).entrySet())same(pin.getValue(),op.get(pin.getKey()));
        same("200",number(op,"http_status"));same("1",number(op,"revision"));if(op.get("error_code")!=null||op.get("dedup_expires_at")!=null||op.get("dedup_policy_revision")!=null)bad();var at=time(op,"created_at");same(at,time(op,"updated_at"));
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",tenant,derived(id,"audit")));
        for(var pin:Map.ofEntries(Map.entry("tenant_id",tenant),Map.entry("project_id",project),Map.entry("audit_id",derived(id,"audit")),Map.entry("subject_id",actor),Map.entry("action",action),Map.entry("resource_ref",canonical),Map.entry("result","SUCCESS"),Map.entry("request_id",requestId),Map.entry("trace_id",run),Map.entry("schema_version",version),Map.entry("schema_manifest_sha256",manifest),Map.entry("created_by",actor),Map.entry("classification",classification)).entrySet())same(pin.getValue(),audit.get(pin.getKey()));if(audit.get("reason_code")!=null)bad();same(at,time(audit,"created_at"));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",tenant,derived(id,"event")));same(tenant,event.get("tenant_id"));same(derived(id,"event"),event.get("event_id"));same(id,event.get("aggregate_id"));same("1",number(event,"aggregate_revision"));same(action,event.get("event_type"));same(canonical,event.get("payload"));same(at,time(event,"occurred_at"));
        return Optional.of(value);
    }
    void write(EnterpriseAccess.Admission a,String project,String run,String id,String action,String key,String requestId,String requestHash,String classification,String version,String manifest,JsonNode value)throws Exception{
        active(a);String tenant=a.context().tenant_id(),actor=a.actorId(),canonical=json.canonical(value);LocalDateTime at=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
        changed(db.update("INSERT INTO operation_record(tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,'COMPLETED',200,?,?,?,?,?,?,?,1,?)",tenant,id,actor,action,key,requestHash,canonical,run,version,manifest,actor,at,classification,at));
        changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',NULL,?,?,?,?,?,?,?)",tenant,project,derived(id,"audit"),actor,action,canonical,requestId,run,version,manifest,actor,at,classification));
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",tenant,derived(id,"event"),id,action,canonical,at));
    }
    String hash(JsonNode n)throws Exception{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(n));}
    static String derived(String id,String part){return UUID.nameUUIDFromBytes((id+"|"+part).getBytes(StandardCharsets.US_ASCII)).toString();}
    private static void active(EnterpriseAccess.Admission a){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);var scope=TenantScope.requireCurrent();same(scope.tenantId(),a.context().tenant_id());same(scope.subjectId(),a.actorId());}
    static String text(Map<String,Object> row,String f){Object v=row.get(f);if(!(v instanceof String))bad();return (String)v;}
    static String number(Map<String,Object> row,String f){return new BigDecimal(String.valueOf(row.get(f))).toBigIntegerExact().toString();}
    static LocalDateTime time(Map<String,Object> row,String f){Object v=row.get(f);if(v instanceof LocalDateTime t)return t;if(v instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}static void changed(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
