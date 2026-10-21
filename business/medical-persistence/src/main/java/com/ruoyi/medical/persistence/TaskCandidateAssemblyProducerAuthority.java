package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Closed N06 host producer. The original ROLE/078/093 evidence and independent
 * producer approval are revalidated by the common governance service. No C07 grant. */
public final class TaskCandidateAssemblyProducerAuthority {
    private final TaskNodeProducerSchema schema;private final TaskNodeProducerService rules;private final PermissionSchema json;private final JdbcTemplate db;
    public TaskCandidateAssemblyProducerAuthority(TaskNodeProducerService rules,PermissionSchema json,DataSource data){try{schema=new TaskNodeProducerSchema(TaskNodeProducerProfile.ASSEMBLY);}catch(java.io.IOException e){throw new IllegalStateException(e);}this.rules=rules;this.json=json;db=new JdbcTemplate(data);db.setQueryTimeout(5);}
    public Verified require(TaskRunActivityPreparation.Current current,TaskCandidateAssemblyAuthority.Verified input)throws Exception {
        current.requireUnchanged();input.requireUnchanged();if(current.production()!=input.production())deny();var p=input.production();
        var existing=db.queryForList("SELECT producer_pins_json FROM task_candidate_assembly_object_intent WHERE tenant_id=? AND run_id=? AND step_id=? ORDER BY kind LIMIT 1 FOR UPDATE",p.tenantId(),p.runId(),input.stepId());
        String id,sha,decision=null,decisionSha=null;
        if(!existing.isEmpty()){var pins=read(str(existing.get(0),"producer_pins_json"));id=text(pins,"producer_rule_id");sha=text(pins,"producer_sha256");decision=text(pins,"producer_decision_id");decisionSha=text(pins,"producer_decision_sha256");}
        else {var candidates=db.queryForList("SELECT r.rule_id,r.content_sha256 FROM task_candidate_assembly_producer_rule r JOIN task_candidate_assembly_producer_state s ON s.tenant_id=r.tenant_id AND s.rule_id=r.rule_id WHERE r.tenant_id=? AND r.project_id=? AND r.task_id=? AND r.plan_revision=? AND s.status='APPROVED' LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),text(input.input(),"plan_revision"));var row=one(candidates);id=str(row,"rule_id");sha=str(row,"content_sha256");}
        var checked=rules.requireCurrent(current.admission(),current.publication(),current.policy(),json.basis(input.input().required("role_version")),id,sha,p.taskId(),decision,decisionSha);
        var proof=new Verified(current,input,checked);proof.requireUnchanged();return proof;
    }
    public final class Verified {
        private final TaskRunActivityPreparation.Current current;private final TaskCandidateAssemblyAuthority.Verified input;private final TaskNodeProducerService.Checked original;
        private Verified(TaskRunActivityPreparation.Current c,TaskCandidateAssemblyAuthority.Verified i,TaskNodeProducerService.Checked o){current=c;input=i;original=o;}
        public void requireUnchanged()throws Exception {
            current.requireUnchanged();input.requireUnchanged();var p=input.production();var fresh=rules.requireCurrent(current.admission(),current.publication(),current.policy(),json.basis(input.input().required("role_version")),original.capabilityId(),original.capabilityHash(),p.taskId(),original.decisionId(),original.decisionHash());
            if(!fresh.fingerprint().equals(original.fingerprint()))deny();var m=original.manifest();if(!"N06".equals(text(m,"producer_node_kind"))||!text(input.input(),"plan_revision").equals(text(m,"plan_revision"))||!text(input.input(),"plan_sha256").equals(text(m,"plan_sha256"))||!text(input.input(),"snapshot_id").equals(text(m,"scope_snapshot")))deny();
            for(var section:input.input().required("assigned_section_ids"))if(!TaskNodeProducerAuthority.contains(m.required("section_ids"),section.asText()))deny();rules.requireReplayPins(m,p.replay().content());
        }
        public void requireFor(TaskCandidateAssemblyAuthority.Verified exact)throws Exception {requireUnchanged();exact.requireUnchanged();if(exact!=input)deny();}
        public void requireBytes(String kind,long size)throws Exception {
            requireUnchanged();var m=original.manifest();if(size<1||!TaskNodeProducerProfile.ASSEMBLY.kinds().contains(kind)||!TaskNodeProducerAuthority.contains(m.required("artifact_kinds"),kind)||!TaskNodeProducerAuthority.contains(m.required("operations"),"NODE_PRODUCE"))deny();
            TaskNodeProducerService.requireReadBound(text(m,"maximum_bytes"),Long.toString(size));var p=input.production();
            one(db.queryForList("SELECT rule_id FROM task_candidate_assembly_producer_rule WHERE tenant_id=? AND rule_id=? FOR UPDATE",p.tenantId(),original.capabilityId()));
            long count=db.queryForObject("SELECT COUNT(*) FROM task_candidate_assembly_object_intent WHERE tenant_id=? AND JSON_UNQUOTE(JSON_EXTRACT(producer_pins_json,'$.producer_rule_id'))=?",Long.class,p.tenantId(),original.capabilityId());
            long same=db.queryForObject("SELECT COUNT(*) FROM task_candidate_assembly_object_intent WHERE tenant_id=? AND run_id=? AND step_id=? AND kind=?",Long.class,p.tenantId(),p.runId(),input.stepId(),kind);
            if(java.math.BigInteger.valueOf(count+(same==0?1:0)).compareTo(new java.math.BigInteger(text(m,"maximum_artifacts")))>0)deny();
        }
        public void requireFamily(java.util.Map<String,Integer> missing)throws Exception {
            requireUnchanged();for(var entry:missing.entrySet())requireBytes(entry.getKey(),entry.getValue());var p=input.production();
            one(db.queryForList("SELECT rule_id FROM task_candidate_assembly_producer_rule WHERE tenant_id=? AND rule_id=? FOR UPDATE",p.tenantId(),original.capabilityId()));
            long count=db.queryForObject("SELECT COUNT(*) FROM task_candidate_assembly_object_intent WHERE tenant_id=? AND JSON_UNQUOTE(JSON_EXTRACT(producer_pins_json,'$.producer_rule_id'))=?",Long.class,p.tenantId(),original.capabilityId());
            if(java.math.BigInteger.valueOf(count+missing.size()).compareTo(new java.math.BigInteger(text(original.manifest(),"maximum_artifacts")))>0)deny();
        }
        public JsonNode canonicalPins()throws Exception {requireUnchanged();return pins(original);}
    }
    private JsonNode pins(TaskNodeProducerService.Checked c){var n=json.object().put("producer_rule_id",c.capabilityId()).put("producer_sha256",c.capabilityHash()).put("producer_decision_id",c.decisionId()).put("producer_decision_sha256",c.decisionHash());n.set("manifest",c.manifest().deepCopy());var a=json.object();a.set("rule",c.authorizations().required(c.authorizations().has("rule")?"rule":"capability").deepCopy());a.set("approval",c.authorizations().required("approval").deepCopy());n.set("authorizations",a);return n;}

    /** Historical write evidence only, never a capability for another PUT or new intent. */
    void requireOriginal(JsonNode pins,TaskCandidateAssemblyAuthority.Verified input,java.time.LocalDateTime at,String kind,int size)throws Exception {
        input.requireUnchanged();var p=input.production();var commitment=(com.fasterxml.jackson.databind.node.ObjectNode)pins.deepCopy();commitment.put("tenant_id",p.tenantId()).put("project_id",p.projectId());var m=originalProducer(commitment);
        if(!m.equals(pins.required("manifest"))||!"N06".equals(text(m,"producer_node_kind"))||!p.taskId().equals(text(m,"task_id"))||!text(input.input(),"plan_revision").equals(text(m,"plan_revision"))||!text(input.input(),"snapshot_id").equals(text(m,"scope_snapshot"))||!TaskNodeProducerProfile.ASSEMBLY.kinds().contains(kind)||!TaskNodeProducerAuthority.contains(m.required("artifact_kinds"),kind)||!TaskNodeProducerAuthority.contains(m.required("operations"),"NODE_PRODUCE"))deny();
        for(var section:input.input().required("assigned_section_ids"))if(!TaskNodeProducerAuthority.contains(m.required("section_ids"),section.asText()))deny();
        TaskNodeProducerService.requireReadBound(text(m,"maximum_bytes"),Integer.toString(size));var instant=at.toInstant(java.time.ZoneOffset.UTC);if(instant.isBefore(java.time.Instant.parse(text(m,"valid_from")))||!instant.isBefore(java.time.Instant.parse(text(m,"valid_until"))))deny();
    }
    void requireOriginal(JsonNode pins,TaskCandidateAssemblyStageJournal.Original original,java.time.LocalDateTime at,String kind,int size)throws Exception {
        original.requireUnchanged();var commitment=(com.fasterxml.jackson.databind.node.ObjectNode)pins.deepCopy();commitment.put("tenant_id",original.tenantId()).put("project_id",original.projectId());var m=originalProducer(commitment);var input=original.input();
        if(!m.equals(pins.required("manifest"))||!"N06".equals(text(m,"producer_node_kind"))||!original.taskId().equals(text(m,"task_id"))||!text(input,"plan_revision").equals(text(m,"plan_revision"))||!text(input,"snapshot_id").equals(text(m,"scope_snapshot"))||!TaskNodeProducerProfile.ASSEMBLY.kinds().contains(kind)||!TaskNodeProducerAuthority.contains(m.required("artifact_kinds"),kind)||!TaskNodeProducerAuthority.contains(m.required("operations"),"NODE_PRODUCE"))deny();
        for(var section:input.required("assigned_section_ids"))if(!TaskNodeProducerAuthority.contains(m.required("section_ids"),section.asText()))deny();TaskNodeProducerService.requireReadBound(text(m,"maximum_bytes"),Integer.toString(size));var instant=at.toInstant(java.time.ZoneOffset.UTC);if(instant.isBefore(java.time.Instant.parse(text(m,"valid_from")))||!instant.isBefore(java.time.Instant.parse(text(m,"valid_until"))))deny();
    }
    private static String hash(JsonNode value)throws Exception{return com.ruoyi.medical.contracts.WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
 private JsonNode originalProducer(JsonNode commitment)throws Exception {
  String tenant=text(commitment,"tenant_id"),project=text(commitment,"project_id"),rule=text(commitment,"producer_rule_id");
  var rules=db.queryForList("SELECT r.*,v.content_sha256 registry_sha256 FROM task_candidate_assembly_producer_rule r JOIN version_registry v ON v.tenant_id=r.tenant_id AND v.version_id=r.rule_id AND v.owner_project_id=r.project_id AND v.entity_kind='TASK_NODE_PRODUCER_RULE' WHERE r.tenant_id=? AND r.project_id=? AND r.rule_id=? LIMIT 2",tenant,project,rule);
  if(rules.size()!=1)deny();var row=rules.get(0);var fixed=read(str(row,"fixed_json"));var manifest=schema.validate("InputManifest",fixed.required("manifest"));schema.validate("InputAuthor",fixed.required("request"));if(!schema.manifest().equals(str(row,"schema_manifest_sha256")))deny();
  if(!FormalTaskNodeAuthority.sameEvidence(fixed,commitment.required("authorizations").required("rule"))||!hash(manifest).equals(text(commitment,"producer_sha256"))||!str(row,"content_sha256").equals(text(commitment,"producer_sha256"))||!str(row,"registry_sha256").equals(text(commitment,"producer_sha256"))||!tenant.equals(text(manifest,"tenant_id"))||!project.equals(text(manifest,"project_id"))||!rule.equals(text(manifest,"rule_id")))deny();
  originalProducerJournal(row,fixed,"task.node_producer.write","AUTHOR");
  var decisions=db.queryForList("SELECT * FROM task_candidate_assembly_producer_decision WHERE tenant_id=? AND project_id=? AND rule_id=? AND decision_id=? AND decision='APPROVE' LIMIT 2",tenant,project,rule,text(commitment,"producer_decision_id"));
  if(decisions.size()!=1)deny();var decision=decisions.get(0);var body=read(str(decision,"decision_json"));if(str(decision,"created_by").equals(str(row,"created_by"))||!schema.manifest().equals(str(decision,"schema_manifest_sha256")))deny();
  if(!FormalTaskNodeAuthority.sameEvidence(body,commitment.required("authorizations").required("approval"))||!hash(body).equals(text(commitment,"producer_decision_sha256"))||!str(decision,"content_sha256").equals(text(commitment,"producer_decision_sha256"))||!"APPROVED".equals(text(body,"status")))deny();
  originalProducerJournal(decision,body,"task.node_producer.approve","DECIDE");return manifest;
 }
 private void originalProducerJournal(Map<String,Object> row,JsonNode body,String action,String kind)throws Exception {
  String tenant=str(row,"tenant_id"),actor=str(row,"created_by");var receipt=read(str(row,"receipt_json"));
  var operations=db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_id=? AND operation_name=? AND idempotency_key=? AND request_sha256=? AND state='COMPLETED' AND http_status=200 AND target_id=? AND error_code IS NULL AND created_by=? AND created_at=? AND schema_version='1.0.0' AND schema_manifest_sha256=? LIMIT 2",tenant,actor,str(row,"operation_id"),"TASK-CANDIDATE-ASSEMBLY-PRODUCER-"+kind,str(row,"idempotency_key"),str(row,"request_sha256"),str(row,"rule_id"),actor,row.get("created_at"),str(row,"schema_manifest_sha256"));
  var audits=db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND created_by=? AND action=? AND result='SUCCESS' AND reason_code IS NULL AND request_id=? AND trace_id=? AND created_at=? AND classification=? AND schema_version='1.0.0' AND schema_manifest_sha256=? LIMIT 2",tenant,str(row,"project_id"),str(row,"audit_id"),actor,actor,action,str(row,"operation_id"),str(row,"operation_id"),row.get("created_at"),str(row,"classification"),str(row,"schema_manifest_sha256"));
  var events=db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=? AND event_type=? AND occurred_at=? LIMIT 2",tenant,str(row,"event_id"),str(row,"rule_id"),text(receipt,"revision"),action,row.get("created_at"));
  if(operations.size()!=1||audits.size()!=1||events.size()!=1||!FormalTaskNodeAuthority.sameEvidence(receipt,read(str(operations.get(0),"response_ref")))||!FormalTaskNodeAuthority.sameEvidence(body,read(str(audits.get(0),"resource_ref")))||!FormalTaskNodeAuthority.sameEvidence(body,read(str(events.get(0),"payload"))))deny();
 }

    private static JsonNode read(String value)throws Exception{return com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(value);}
    private static String text(JsonNode n,String k){var v=n.required(k);if(!v.isTextual())deny();return v.textValue();}private static String str(Map<String,Object> r,String k){var v=r.get(k);if(v==null)deny();return v.toString();}private static <T>T one(List<T> rows){if(rows.size()!=1)deny();return rows.get(0);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
