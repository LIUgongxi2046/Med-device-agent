package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Atomic N03 database input and READY node, only after private current input authority. */
public final class TaskStrictFieldMaterializer {
    private final JdbcTemplate db;private final TaskStrictFieldSchema schema;private final RunTokenAuthoritySchema tokens;
    public TaskStrictFieldMaterializer(DataSource data,TaskStrictFieldSchema schema,RunTokenAuthoritySchema tokens){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.tokens=tokens;}
    static JsonNode definition(TaskStrictFieldSchema schema,TaskStrictFieldInput input,String hash,String receipt){
        var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version",input.schemaVersion()).put("kind","TASK_N03_NODE");var raw=schema.tree(input);
        for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id","graph_id","graph_sha256","role_version","execution_policy_version","node_kind","node_permissions","input_id","target_artifact_id"))n.set(f,raw.required(f));
        return n.put("input_sha256",hash).put("input_receipt_sha256",receipt);
    }
    public JsonNode materialize(TaskStrictFieldInputAuthority.Prepared proof)throws Exception {
        proof.requireUnchanged();var input=proof.input();var p=proof.production();String hash=schema.hash(input);
        var existing=db.queryForList("SELECT * FROM task_strict_field_input WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.runId());
        if(!existing.isEmpty()){var row=one(existing);if(!hash.equals(str(row,"input_sha256"))||!schema.canonical(input).equals(str(row,"canonical_input")))bad();
            requireNode(input,hash,str(row,"receipt_sha256"));proof.requireUnchanged();return schema.read(str(row,"receipt_json"));}
        String audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();var now=db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);
        var receipt=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version",input.schemaVersion()).put("kind","TASK_STRICT_FIELD_INPUT").put("input_sha256",hash).put("audit_id",audit).put("event_id",event);
        var raw=schema.tree(input);for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id","input_id","target_artifact_id","target_registration_sha256"))receipt.set(f,raw.required(f));
        String canonical=schema.canonical(receipt),receiptHash=schema.hash(receipt);
        changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'task.strict_field.input.registered',?,'SUCCESS',?,?,?,?,?,?,?)",input.tenantId(),input.projectId(),audit,p.actorId(),canonical,input.inputId(),input.runId(),input.schemaVersion(),schema.manifest(input.schemaVersion()),p.actorId(),now,p.classification()));
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,'task.strict_field.input.registered',?,?)",input.tenantId(),event,input.inputId(),canonical,now));
        changed(db.update("INSERT INTO task_strict_field_input(tenant_id,project_id,task_id,run_id,step_id,input_id,input_sha256,target_artifact_id,target_registration_sha256,canonical_input,receipt_json,receipt_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",input.tenantId(),input.projectId(),input.taskId(),input.runId(),input.stepId(),input.inputId(),hash,input.targetArtifactId(),input.targetRegistrationSha256(),schema.canonical(input),canonical,receiptHash,audit,event,input.schemaVersion(),schema.manifest(input.schemaVersion()),p.actorId(),now,p.classification()));
        var definition=definition(schema,input,hash,receiptHash);String nodeJson=schema.canonical(definition),nodeHash=schema.hash(definition);
        changed(db.update("INSERT INTO run_step(tenant_id,project_id,run_id,step_id,role_version,section_id,input_sha256,output_ref,status,lease_generation,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,NULL,?,NULL,'READY',0,'1.0.0',?,?,?,?,1,?)",input.tenantId(),input.projectId(),input.runId(),input.stepId(),input.roleVersion().version_id(),hash,tokens.manifestSha256(),p.actorId(),now,p.classification(),now));
        changed(db.update("INSERT INTO runtime_node_definition(tenant_id,project_id,run_id,step_id,node_kind,role_version,role_sha256,policy_version,policy_sha256,canonical_definition,definition_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'N03',?,?,?,?,?,?,?,?,?,?,?)",input.tenantId(),input.projectId(),input.runId(),input.stepId(),input.roleVersion().version_id(),input.roleVersion().content_sha256(),input.executionPolicyVersion().version_id(),input.executionPolicyVersion().content_sha256(),nodeJson,nodeHash,input.schemaVersion(),schema.manifest(input.schemaVersion()),p.actorId(),now,p.classification()));
        requireNode(input,hash,receiptHash);proof.requireUnchanged();return receipt;
    }
    private void requireNode(TaskStrictFieldInput input,String hash,String receipt)throws Exception {
        var step=one(db.queryForList("SELECT input_sha256,role_version,section_id FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),input.stepId()));
        if(!hash.equals(str(step,"input_sha256"))||!input.roleVersion().version_id().equals(str(step,"role_version"))||step.get("section_id")!=null)bad();
        var node=one(db.queryForList("SELECT node_kind,canonical_definition,definition_sha256 FROM runtime_node_definition WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),input.stepId()));var expected=definition(schema,input,hash,receipt);
        if(!"N03".equals(str(node,"node_kind"))||!schema.hash(expected).equals(str(node,"definition_sha256"))||!expected.equals(schema.read(str(node,"canonical_definition"))))bad();
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String str(Map<String,Object> n,String key){var v=n.get(key);if(v==null)bad();return v.toString();}
    private static void changed(int n){if(n!=1)bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
