package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Transactional producer for actual-save CHECK_DOCUMENT work. An intent is not a run or a result. */
@Component
public final class DocumentReviewJobService {
    private final JdbcTemplate db;private final ResourceReadAuthority resources;
    private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
    public DocumentReviewJobService(@Qualifier("masterDataSource")DataSource data,ResourceReadAuthority resources){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.resources=resources;}
    /** Called only in the save transaction after its original operation and actual body have committed logically. */
    JsonNode enqueueSaved(EnterpriseAccess.Admission a,String document,String revision,String operationId)throws Exception {
        if(!TransactionSynchronizationManager.isActualTransactionActive())deny();
        String tenant=a.context().tenant_id(),actor=a.actorId();
        var operation=one(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? AND subject_id=? AND target_id=? AND state='COMPLETED' LIMIT 2 FOR UPDATE",tenant,operationId,actor,document));
        if(!Set.of("DC-02","DC-03").contains(str(operation,"operation_name")))deny();
        var response=json.read(str(operation,"response_ref"));same(revision,response.required("data").required("document_revision").asText());
        var saved=one(db.queryForList("SELECT m.*,h.current_document_revision FROM document_revision_meta m JOIN document_head h ON h.tenant_id=m.tenant_id AND h.project_id=m.project_id AND h.document_id=m.document_id WHERE m.tenant_id=? AND m.document_id=? AND m.document_revision=? LIMIT 2 FOR UPDATE",tenant,document,revision));
        same(revision,str(saved,"current_document_revision"));String project=str(saved,"project_id");
        resources.require(a,new PermissionProtocol.Scope(tenant,project,"DOCUMENT",document),"document.check","DOCUMENT_BODY");
        var payload=json.tree(Map.of("schema_version","1.0.0","job_type","CHECK_DOCUMENT","execution_kind","SYSTEM_JOB","target_kind","DOCUMENT","tenant_id",tenant,"project_id",project,"document_id",document,"document_revision",revision,"operation_id",operationId,"initiator_id",actor));
        var p=(com.fasterxml.jackson.databind.node.ObjectNode)payload;
        p.put("original_operation",str(operation,"operation_name")).put("original_idempotency_key",str(operation,"idempotency_key")).put("original_request_sha256",str(operation,"request_sha256")).put("body_sha256",str(saved,"body_sha256")).put("body_artifact_id",str(saved,"body_artifact_id")).put("task_id",str(saved,"task_id")).put("task_revision",str(saved,"task_revision")).put("plan_revision",str(saved,"plan_revision")).put("snapshot_id",str(saved,"snapshot_id")).put("auth_epoch",a.context().auth_epoch()).put("governance_epoch",a.context().governance_epoch());
        String intent=UUID.nameUUIDFromBytes(("N07/"+tenant+"/"+operationId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();p.put("intent_id",intent);
        String hash=json.hash(payload);var prior=db.queryForList("SELECT intent_json,intent_sha256 FROM document_review_intent WHERE tenant_id=? AND operation_id=? LIMIT 2 FOR UPDATE",tenant,operationId);
        if(!prior.isEmpty()){var row=one(prior);same(hash,str(row,"intent_sha256"));same(payload,json.read(str(row,"intent_json")));return payload.deepCopy();}
        String event=id(),audit=id();var at=db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);String manifest=WireValues.sha256("N07_SAVED_DOCUMENT_INTENT_V1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var row=meta(tenant,project,actor,str(saved,"classification"),at,manifest);
        row.putAll(Map.of("intent_id",intent,"operation_id",operationId,"document_id",document,"document_revision",revision,"intent_json",json.canonical(payload),"intent_sha256",hash,"audit_id",audit,"event_id",event));insert("document_review_intent",row);
        var log=meta(tenant,project,actor,str(saved,"classification"),at,manifest);log.putAll(Map.of("audit_id",audit,"subject_id",actor,"action","document.review.requested","resource_ref",json.canonical(payload),"result","SUCCESS","request_id",operationId,"trace_id",intent));insert("audit_event",log);
        insert("outbox_event",Map.of("tenant_id",tenant,"event_id",event,"aggregate_id",intent,"aggregate_revision",1,"event_type","document.check_document.requested","payload",json.canonical(payload),"occurred_at",at));
        // Only this saved revision enters pending. Existing checks and human decisions are not promoted.
        db.update("UPDATE section_revision s JOIN document_section d ON d.tenant_id=s.tenant_id AND d.project_id=s.project_id AND d.section_revision=s.section_revision SET s.status='DRAFT_CHECK_PENDING' WHERE d.tenant_id=? AND d.project_id=? AND d.document_revision=?",tenant,project,revision);
        return payload.deepCopy();
    }
    /** Immutable command inspection for the dispatch consumer; current admission must be checked separately. */
    JsonNode original(EnterpriseAccess.Admission a,String intentId)throws Exception {
        var r=one(db.queryForList("SELECT * FROM document_review_intent WHERE tenant_id=? AND intent_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),intentId));
        var payload=json.read(str(r,"intent_json"));same(json.hash(payload),str(r,"intent_sha256"));
        resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),str(r,"project_id"),"DOCUMENT",str(r,"document_id")),"document.check","DOCUMENT_BODY");
        same(a.actorId(),payload.required("initiator_id").asText());
        var operation=one(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? AND subject_id=? AND target_id=? AND state='COMPLETED' LIMIT 2 FOR UPDATE",a.context().tenant_id(),str(r,"operation_id"),a.actorId(),str(r,"document_id")));
        same(str(operation,"operation_name"),payload.required("original_operation").asText());same(str(operation,"idempotency_key"),payload.required("original_idempotency_key").asText());same(str(operation,"request_sha256"),payload.required("original_request_sha256").asText());same(str(r,"document_revision"),json.read(str(operation,"response_ref")).required("data").required("document_revision").asText());
        var audit=one(db.queryForList("SELECT subject_id,resource_ref,result FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),str(r,"audit_id")));same(a.actorId(),str(audit,"subject_id"));same("SUCCESS",str(audit,"result"));same(payload,json.read(str(audit,"resource_ref")));
        var event=one(db.queryForList("SELECT event_type,payload FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),str(r,"event_id")));same("document.check_document.requested",str(event,"event_type"));same(payload,json.read(str(event,"payload")));return payload;
    }
    private static Map<String,Object> meta(String tenant,String project,String actor,String classification,LocalDateTime at,String manifest){var row=new LinkedHashMap<String,Object>();row.put("tenant_id",tenant);row.put("project_id",project);row.put("created_by",actor);row.put("created_at",at);row.put("classification",classification);row.put("schema_version","1.0.0");row.put("schema_manifest_sha256",manifest);return row;}
    private void insert(String table,Map<String,Object> values){var keys=new ArrayList<>(values.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(values::get).toArray())!=1)bad();}
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String str(Map<String,Object> row,String key){var value=row.get(key);if(value==null)bad();return value.toString();}private static String id(){return UUID.randomUUID().toString();}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
