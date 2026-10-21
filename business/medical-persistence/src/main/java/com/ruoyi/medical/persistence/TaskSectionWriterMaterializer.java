package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** A READY N04 is created only from an actually registered, physically read-back input.
 * One immutable step per chapter avoids broadening the writer's assigned scope. */
public final class TaskSectionWriterMaterializer {
    private final JdbcTemplate db; private final TaskSectionWriterSchema schema; private final RunTokenAuthoritySchema tokens;
    public TaskSectionWriterMaterializer(DataSource data,TaskSectionWriterSchema schema,RunTokenAuthoritySchema tokens){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.tokens=tokens;}
    static JsonNode definition(TaskSectionWriterSchema schema,TaskSectionWriterInput i,String hash,String receipt){
        var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("kind","TASK_N04_NODE");var raw=schema.tree(i);
        for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id","graph_id","graph_sha256","role_version","execution_policy_version","node_kind","node_permissions","input_id","target_sections","document_id","base_document_revision"))n.set(f,raw.required(f));
        return n.put("input_sha256",hash).put("input_receipt_sha256",receipt);
    }
    public JsonNode materialize(TaskSectionWriterInputAuthority.Verified proof)throws Exception {
        proof.requireUnchanged();var i=proof.input();if(i.targetSections().size()!=1)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        var existing=db.queryForList("SELECT step_id FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",i.tenantId(),i.projectId(),i.runId(),i.stepId());
        if(existing.isEmpty()){
            var p=proof.production();var now=db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);var node=definition(schema,i,proof.inputSha256(),proof.receiptSha256());
            changed(db.update("INSERT INTO run_step(tenant_id,project_id,run_id,step_id,role_version,section_id,input_sha256,output_ref,status,lease_generation,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,?,NULL,'READY',0,'1.0.0',?,?,?,?,1,?)",i.tenantId(),i.projectId(),i.runId(),i.stepId(),i.roleVersion().version_id(),i.targetSections().get(0),proof.inputSha256(),tokens.manifestSha256(),p.actorId(),now,p.classification(),now));
            changed(db.update("INSERT INTO runtime_node_definition(tenant_id,project_id,run_id,step_id,node_kind,role_version,role_sha256,policy_version,policy_sha256,canonical_definition,definition_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'N04',?,?,?,?,?,?,'1.0.0',?,?,?,?)",i.tenantId(),i.projectId(),i.runId(),i.stepId(),i.roleVersion().version_id(),i.roleVersion().content_sha256(),i.executionPolicyVersion().version_id(),i.executionPolicyVersion().content_sha256(),schema.canonical(node),schema.hash(node),schema.manifest(),p.actorId(),now,p.classification()));
        }
        requireNode(proof);proof.requireUnchanged();return definition(schema,i,proof.inputSha256(),proof.receiptSha256());
    }
    public void requireNode(TaskSectionWriterInputAuthority.Verified p)throws Exception{
        p.requireUnchanged();var i=p.input();var step=one(db.queryForList("SELECT input_sha256,role_version,section_id FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",i.tenantId(),i.projectId(),i.runId(),i.stepId()));
        if(!p.inputSha256().equals(step.get("input_sha256"))||!i.roleVersion().version_id().equals(step.get("role_version"))||i.targetSections().size()!=1||!i.targetSections().get(0).equals(step.get("section_id")))bad();
        var node=one(db.queryForList("SELECT node_kind,canonical_definition,definition_sha256 FROM runtime_node_definition WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",i.tenantId(),i.projectId(),i.runId(),i.stepId()));var expected=definition(schema,i,p.inputSha256(),p.receiptSha256());
        if(!"N04".equals(node.get("node_kind"))||!schema.hash(expected).equals(node.get("definition_sha256"))||!expected.equals(schema.read(node.get("canonical_definition").toString())))bad();
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void changed(int n){if(n!=1)bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
