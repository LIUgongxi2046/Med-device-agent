package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Immutable source byte receipts. Only the reader's privately minted delivery proof can create one. */
@Component
public final class TaskFieldSourceReceiptStore {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final TaskFieldTargetSchema schema;
    private final PermissionSchema json;
    private final TaskFieldSourceAuthority authority;
    private static final Set<String> JSON_COLUMNS=Set.of("original_object","parsed_object","processing_command","processing_result","authority_manifest","receipt_manifest");
    @org.springframework.beans.factory.annotation.Autowired
    public TaskFieldSourceReceiptStore(@Qualifier("masterDataSource") DataSource data,TaskFieldTargetSchema schema,
            PermissionSchema json,TaskFieldSourceAuthority authority) {
        this(new JdbcTemplate(data),new NamedParameterJdbcTemplate(data),schema,json,authority);
    }
    TaskFieldSourceReceiptStore(JdbcTemplate jdbc,NamedParameterJdbcTemplate named,TaskFieldTargetSchema schema,
            PermissionSchema json,TaskFieldSourceAuthority authority) {
        this.jdbc=jdbc;this.named=named;this.schema=schema;this.json=json;this.authority=authority;
    }

    Stored save(EnterpriseAccess.Admission admission,TaskFieldSourceAuthority.Proof proof,TaskFieldSourceReader.Bytes bytes) throws Exception {
        current(admission);Objects.requireNonNull(bytes).require(proof);
        var previous=find(admission,proof,null);if(previous!=null)return previous;
        String id=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString();
        var now=Objects.requireNonNull(jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class));
        String created=now.toInstant(ZoneOffset.UTC).toString();
        var manifest=manifest(proof,id,audit,admission.actorId(),created,proof.authoritySha256());schema.validate("SourceReadReceipt",manifest);
        String hash=schema.hash(manifest);var witness=witness(manifest,hash);
        one(jdbc.update("""
            INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,
              schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES(?,?,?,?,'task.field_source.read',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)
            """,proof.task().tenantId(),proof.task().projectId(),audit,admission.actorId(),schema.canonical(witness),id,
                proof.task().taskId(),schema.manifest(),admission.actorId(),now,proof.classification()));
        var values=columns(proof,manifest,hash); values.put("created_at",now);
        String columns=String.join(",",values.keySet());
        String parameters=values.keySet().stream().map(name -> JSON_COLUMNS.contains(name)?"CAST(:"+name+" AS JSON)":":"+name).collect(Collectors.joining(","));
        one(named.update("INSERT INTO task_field_source_read_receipt("+columns+") VALUES("+parameters+")",values));
        var stored=find(admission,proof,id);if(stored==null || !stored.sha256().equals(hash)) fail(ErrorCode.CONTENT_MISMATCH);return stored;
    }

    Stored find(EnterpriseAccess.Admission admission,TaskFieldSourceAuthority.Proof proof,String receiptId) throws Exception {
        current(admission);
        if(!proof.task().tenantId().equals(admission.context().tenant_id())) fail(ErrorCode.ACCESS_DENIED);
        var rows=jdbc.queryForList("""
            SELECT * FROM task_field_source_read_receipt
            WHERE tenant_id=? AND project_id=? AND task_id=? AND mapping_revision=? AND actor_id=? AND request_sha256=?
            """+(receiptId==null?"":" AND receipt_id=?")+" LIMIT 2 FOR UPDATE",
            arguments(proof,admission.actorId(),receiptId));
        if(rows.isEmpty())return null;
        return verify(admission,proof,TaskFieldSourceAuthority.one(rows),admission.actorId());
    }

    /** Revalidates a real historical receipt without claiming the current approver read its bytes. */
    Stored requireHistorical(EnterpriseAccess.Admission admission,String receiptId,String mappingRevision) throws Exception {
        return requireHistorical(admission,receiptId,mappingRevision,"task.field_target_input.read");
    }
    Stored requireHistorical(EnterpriseAccess.Admission admission,String receiptId,String mappingRevision,String action) throws Exception {
        current(admission);WireValues.uuid(receiptId);WireValues.uuid(mappingRevision);
        if(!Set.of("task.field_target.produce","task.field_target_input.read","task.field_target_input.write","task.field_target_input.approve").contains(action)) fail(ErrorCode.ACCESS_DENIED);
        var row=TaskFieldSourceAuthority.one(jdbc.queryForList("""
            SELECT * FROM task_field_source_read_receipt WHERE tenant_id=? AND receipt_id=? AND mapping_revision=? LIMIT 2 FOR UPDATE
            """,admission.context().tenant_id(),receiptId,mappingRevision));
        var request=new TaskFieldSourceAuthority.Request(text(row,"task_id"),mappingRevision,text(row,"mapping_sha256"),text(row,"entry_key"));
        var proof="task.field_target.produce".equals(action)
            ?authority.require(admission,admission.context().context_revision(),request)
            :authority.require(admission,admission.context().context_revision(),request,action);
        return verify(admission,proof,row,text(row,"actor_id"));
    }

    private Stored verify(EnterpriseAccess.Admission admission,TaskFieldSourceAuthority.Proof proof,Map<String,Object> row,String originalActor) throws Exception {
        String id=text(row,"receipt_id"),audit=text(row,"audit_id"),created=TaskFieldSourceAuthority.time(row,"created_at");
        var manifest=schema.read(text(row,"receipt_manifest"));schema.validate("SourceReadReceipt",manifest);
        String hash=schema.hash(manifest);var originalAuthority=schema.read(text(row,"authority_manifest"));
        String authorityHash=schema.hash(originalAuthority);
        if(!hash.equals(text(row,"receipt_sha256")) || !authorityHash.equals(text(row,"authority_sha256"))
                || !originalActor.equals(text(row,"created_by")) || !originalActor.equals(originalAuthority.required("actor_id").textValue())
                || !"task.field_target.produce".equals(originalAuthority.required("action").textValue())
                || !json.tree(proof.request()).equals(originalAuthority.required("request"))
                || !manifest.equals(manifest(proof,id,audit,originalActor,created,authorityHash))) fail(ErrorCode.CONTENT_MISMATCH);
        var expected=columns(proof,manifest,hash);
        for(var entry:expected.entrySet()) {
            if(entry.getKey().equals("authority_manifest")) continue;
            if(JSON_COLUMNS.contains(entry.getKey())) {
                if(!schema.read(entry.getValue().toString()).equals(schema.read(text(row,entry.getKey())))) fail(ErrorCode.CONTENT_MISMATCH);
            } else if(entry.getValue() instanceof Number) {
                if(!entry.getValue().toString().equals(Objects.requireNonNull(row.get(entry.getKey())).toString())) fail(ErrorCode.CONTENT_MISMATCH);
            } else if(!Objects.equals(entry.getValue(),row.get(entry.getKey()))) fail(ErrorCode.CONTENT_MISMATCH);
        }
        for(String name:List.of("tenant_id","project_id","task_revision","task_input_sha256","plan_revision","plan_sha256","snapshot_id",
                "template_version","template_sha256","profile_revision","source_exact","field_sha256","original_object","parsed_object",
                "processing_project_id","processing_attempt_id","processing_command_sha256","processing_result_sha256","processor_manifest_sha256","purpose","environment","region")) {
            if(!proof.authority().required(name).equals(originalAuthority.required(name))) fail(ErrorCode.CONTENT_MISMATCH);
        }
        var auditRow=TaskFieldSourceAuthority.one(jdbc.queryForList("""
            SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=?
              AND created_by=? AND created_at=? AND classification=? AND action='task.field_source.read' AND result='SUCCESS'
              AND request_id=? AND trace_id=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND reason_code IS NULL LIMIT 2 FOR UPDATE
            """,proof.task().tenantId(),proof.task().projectId(),audit,originalActor,originalActor,row.get("created_at"),proof.classification(),
                id,proof.task().taskId(),schema.manifest()));
        if(!schema.read(text(auditRow,"resource_ref")).equals(witness(manifest,hash))) fail(ErrorCode.CONTENT_MISMATCH);
        return new Stored(id,hash,manifest);
    }

    private ObjectNode manifest(TaskFieldSourceAuthority.Proof proof,String id,String audit,String actor,String created,String authorityHash) throws Exception {
        var task=proof.task();var source=proof.source();var selected=source.selection();var request=proof.request();
        var value=json.object();value.put("schema_version","1.0.0");value.put("tenant_id",task.tenantId());value.put("project_id",task.projectId());
        value.put("task_id",task.taskId());value.put("task_revision",task.taskRevision());value.put("plan_revision",task.planRevision());
        value.put("snapshot_id",task.snapshotId());value.put("mapping_revision",request.mappingRevision());value.put("mapping_sha256",request.mappingSha256());
        value.put("entry_key",request.entryKey());value.put("receipt_id",id);value.put("audit_id",audit);value.put("actor_id",actor);
        value.put("request_sha256",requestHash(proof,actor));value.put("source_version",source.source().sourceVersion());
        value.put("parse_version",selected.parseVersion());value.put("index_version",selected.indexVersion());
        value.put("approval_revision",selected.approvalRevision());value.put("rights_revision",source.rights().rightsRevision());
        value.put("binding_revision",selected.bindingRevision());value.set("source_exact",proof.exactRef());
        value.set("original_read",completeRead(proof.processing().original()));value.set("parsed_read",completeRead(proof.processing().parsed()));
        value.put("processing_project_id",proof.processing().projectId());value.put("processing_attempt_id",proof.processing().attempt());value.put("processing_command_sha256",schema.hash(proof.processing().command()));
        value.put("processing_result_sha256",schema.hash(proof.processing().result()));value.put("purpose",source.rights().purpose());
        value.put("environment",proof.authority().required("environment").textValue());value.put("region",proof.authority().required("region").textValue());
        value.put("authority_sha256",authorityHash);value.put("created_by",actor);value.put("created_at",created);value.put("classification",proof.classification());return value;
    }

    private JsonNode completeRead(com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef reference) {
        var result=json.object();result.set("object_ref",json.tree(reference));result.put("start","0");result.put("length",reference.sizeBytes());
        result.put("full_sha256",reference.sha256());result.put("delivered_sha256",reference.sha256());return result;
    }
    private LinkedHashMap<String,Object> columns(TaskFieldSourceAuthority.Proof proof,JsonNode manifest,String hash) throws Exception {
        var values=new LinkedHashMap<String,Object>();
        for(String name:List.of("tenant_id","receipt_id","audit_id","project_id","task_id","task_revision","plan_revision","snapshot_id","mapping_revision",
                "mapping_sha256","entry_key","actor_id","request_sha256","source_version","parse_version","processing_project_id","processing_attempt_id","rights_revision","index_version",
                "approval_revision","binding_revision","purpose","environment","region","authority_sha256","schema_version","created_by","classification"))
            values.put(name,manifest.required(name).textValue());
        values.put("field_revision",proof.exactRef().required("field_revision").textValue());
        values.put("field_manifest_sha256",proof.exactRef().required("manifest_sha256").textValue());values.put("field_ordinal",proof.exactRef().required("ordinal").intValue());
        values.put("original_object",schema.canonical(json.tree(proof.processing().original())));values.put("parsed_object",schema.canonical(json.tree(proof.processing().parsed())));
        values.put("processing_command",schema.canonical(proof.processing().command()));values.put("processing_result",schema.canonical(proof.processing().result()));
        values.put("authority_manifest",schema.canonical(proof.authority()));values.put("receipt_manifest",schema.canonical(manifest));
        values.put("receipt_sha256",hash);values.put("schema_manifest_sha256",schema.manifest());return values;
    }
    private String requestHash(TaskFieldSourceAuthority.Proof proof,String actor) throws Exception {
        var value=json.object();value.put("schema_version","1.0.0");value.put("tenant_id",proof.task().tenantId());value.put("actor_id",actor);
        value.put("operation","TASK_FIELD_SOURCE_READ");value.set("request",json.tree(proof.request()));return schema.hash(value);
    }
    private Object[] arguments(TaskFieldSourceAuthority.Proof proof,String actor,String receiptId) throws Exception {
        var result=new java.util.ArrayList<Object>(List.of(proof.task().tenantId(),proof.task().projectId(),proof.task().taskId(),
                proof.request().mappingRevision(),actor,requestHash(proof,actor)));if(receiptId!=null)result.add(receiptId);return result.toArray();
    }
    private JsonNode witness(JsonNode receipt,String hash) {
        var value=json.object();value.put("receipt_id",receipt.required("receipt_id").textValue());value.put("receipt_sha256",hash);
        value.put("task_id",receipt.required("task_id").textValue());value.put("mapping_revision",receipt.required("mapping_revision").textValue());
        value.put("entry_key",receipt.required("entry_key").textValue());value.put("actor_id",receipt.required("actor_id").textValue());return value;
    }
    static final class Stored {
        private final String id,sha256;private final JsonNode manifest;
        private Stored(String id,String sha256,JsonNode manifest){this.id=id;this.sha256=sha256;this.manifest=manifest.deepCopy();}
        String id(){return id;}String sha256(){return sha256;}JsonNode manifest(){return manifest.deepCopy();}
    }
    private static void current(EnterpriseAccess.Admission admission) {
        if(!TransactionSynchronizationManager.isActualTransactionActive() || !TenantScope.requireCurrent().tenantId().equals(admission.context().tenant_id())
                || admission.simulationWorker() || admission.formalWorker() || admission.controlWorker() || admission.recoveryWorker()) fail(ErrorCode.ACCESS_DENIED);
    }
    private static String text(Map<String,Object> row,String name){return TaskFieldSourceAuthority.text(row,name);}
    private static void one(int count){if(count!=1)fail(ErrorCode.REVISION_CONFLICT);}
    private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
