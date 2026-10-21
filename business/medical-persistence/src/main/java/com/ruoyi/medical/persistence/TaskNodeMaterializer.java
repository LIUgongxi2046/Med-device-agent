package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts a real READY node only after exact physical input registration and current
 * private producer checks. No predicted output/input hashes or future step rows. */
public final class TaskNodeMaterializer {
    private final JdbcTemplate db;private final PermissionSchema json;private final TaskNodeInputSchema inputs;
    private final TaskNodeInputLedger ledger;private final TaskNodeInputRegistration registrations;private final RunTokenAuthoritySchema tokens;
    public TaskNodeMaterializer(DataSource data,PermissionSchema json,TaskNodeInputSchema inputs,TaskNodeInputLedger ledger,TaskNodeInputRegistration registrations,RunTokenAuthoritySchema tokens){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.inputs=inputs;this.ledger=ledger;this.registrations=registrations;this.tokens=tokens;}
    static JsonNode definition(PermissionSchema json,TaskNodeInputProtocol.Input input,String artifact,String hash,String receipt){
        var n=json.object().put("schema_version","1.0.0").put("kind","TASK_N02_NODE");
        var raw=json.tree(input);for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id","graph_id","graph_sha256","question_id","role_version","execution_policy_version","node_kind","node_permissions"))n.set(f,raw.required(f));
        return n.put("input_artifact_id",artifact).put("input_sha256",hash).put("input_receipt_sha256",receipt);
    }
    public void materialize(TaskNodeInputAuthority.Prepared proof,TaskNodeInputLedger.Attempt attempt)throws Exception{
        proof.requireUnchanged();ledger.require(proof,attempt);var a=proof.bootstrap().admission();
        var registered=registrations.readStored(a,attempt).orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
        var input=proof.input();if(!inputs.tree(input).equals(registered.input())||!attempt.artifactId().equals(registered.artifactId()))bad();
        var definition=definition(json,input,registered.artifactId(),registered.object().sha256(),registered.receiptSha256());
        String canonical=json.canonical(definition),digest=inputs.hash(definition);var b=proof.bootstrap();
        var existing=db.queryForList("SELECT * FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.runId(),input.stepId());
        if(existing.isEmpty()){
            var now=db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);
            changed(db.update("INSERT INTO run_step(tenant_id,project_id,run_id,step_id,role_version,section_id,input_sha256,output_ref,status,lease_generation,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,?,NULL,'READY',0,'1.0.0',?,?,?,?,1,?)",input.tenantId(),input.projectId(),input.runId(),input.stepId(),input.roleVersion().version_id(),input.sectionId(),registered.object().sha256(),tokens.manifestSha256(),b.actorId(),now,b.classification(),now));
            changed(db.update("INSERT INTO runtime_node_definition(tenant_id,project_id,run_id,step_id,node_kind,role_version,role_sha256,policy_version,policy_sha256,canonical_definition,definition_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'N02',?,?,?,?,?,?,'1.0.0',?,?,?,?)",input.tenantId(),input.projectId(),input.runId(),input.stepId(),input.roleVersion().version_id(),input.roleVersion().content_sha256(),input.executionPolicyVersion().version_id(),input.executionPolicyVersion().content_sha256(),canonical,digest,inputs.manifest(),b.actorId(),now,b.classification()));
            String audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();
            changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'task.node.materialized',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",input.tenantId(),input.projectId(),audit,b.actorId(),canonical,input.stepId(),input.runId(),inputs.manifest(),b.actorId(),now,b.classification()));
            changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,'task.node.materialized',?,?)",input.tenantId(),event,input.stepId(),canonical,now));
        }
        var step=one(db.queryForList("SELECT * FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.runId(),input.stepId()));
        var node=one(db.queryForList("SELECT * FROM runtime_node_definition WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.runId(),input.stepId()));
        for(String f:List.of("tenant_id","project_id","run_id","step_id","created_by","created_at","classification"))if(!Objects.equals(step.get(f),node.get(f)))bad();
        if(!input.projectId().equals(step.get("project_id"))||!b.actorId().equals(step.get("created_by"))||!b.classification().equals(step.get("classification"))||!input.roleVersion().version_id().equals(step.get("role_version"))||!input.sectionId().equals(step.get("section_id"))||!registered.object().sha256().equals(step.get("input_sha256"))||!tokens.manifestSha256().equals(step.get("schema_manifest_sha256"))||!"1.0.0".equals(step.get("schema_version")))bad();
        if(!definition.equals(inputs.readTree(String.valueOf(node.get("canonical_definition"))))||!digest.equals(node.get("definition_sha256"))||!inputs.manifest().equals(node.get("schema_manifest_sha256"))||!"1.0.0".equals(node.get("schema_version"))||!"N02".equals(node.get("node_kind"))||!input.roleVersion().version_id().equals(node.get("role_version"))||!input.roleVersion().content_sha256().equals(node.get("role_sha256"))||!input.executionPolicyVersion().version_id().equals(node.get("policy_version"))||!input.executionPolicyVersion().content_sha256().equals(node.get("policy_sha256")))bad();
        var audits=db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND request_id=? AND action='task.node.materialized'",input.tenantId(),input.stepId());var audit=one(audits);
        if(!canonical.equals(audit.get("resource_ref"))||!input.runId().equals(audit.get("trace_id"))||!b.actorId().equals(audit.get("subject_id"))||!"SUCCESS".equals(audit.get("result"))||audit.get("reason_code")!=null)bad();
        for(String f:List.of("tenant_id","project_id","created_by","created_at","classification","schema_version","schema_manifest_sha256"))if(!Objects.equals(node.get(f),audit.get(f)))bad();
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='task.node.materialized'",input.tenantId(),input.stepId()));
        if(!canonical.equals(event.get("payload"))||((Number)event.get("aggregate_revision")).longValue()!=1||!Objects.equals(node.get("created_at"),event.get("occurred_at")))bad();
        proof.requireUnchanged();
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void changed(int n){if(n!=1)bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
