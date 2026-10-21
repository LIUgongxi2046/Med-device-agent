package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Human read of the registered typed result. This is never an approval or a current legal conclusion. */
@Service public final class NonclinicalPrinciplesRiskResults {
 private final EnterpriseAccess access;private final TaskService tasks;private final AgentPublicationDependencies dependencies;private final PermissionSchema json;private final JdbcTemplate db;
 public NonclinicalPrinciplesRiskResults(EnterpriseAccess access,TaskService tasks,AgentPublicationDependencies dependencies,PermissionSchema json,DataSource data){this.access=access;this.tasks=tasks;this.dependencies=dependencies;this.json=json;db=new JdbcTemplate(data);db.setQueryTimeout(5);}
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String task,String run,String action)throws Exception{
  WireValues.uuid(task);WireValues.uuid(run);WireValues.uuid(action);
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
   if(a.simulationWorker()||a.formalWorker()||a.controlWorker())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
   var fixed=tasks.fixedTask(a,task);String tenant=a.context().tenant_id(),project=fixed.head().projectId();
   var rows=db.queryForList("SELECT o.artifact_id,o.payload_sha256,o.canonical_payload,o.receipt_json,o.receipt_sha256 FROM task_source_verifier_object o JOIN task_source_verifier_submission s ON s.tenant_id=o.tenant_id AND s.run_id=o.run_id AND s.step_id=o.step_id AND s.action_id=o.action_id AND s.findings_artifact_id=o.artifact_id JOIN task_run r ON r.tenant_id=o.tenant_id AND r.project_id=o.project_id AND r.run_id=o.run_id WHERE o.tenant_id=? AND o.project_id=? AND r.task_id=? AND o.run_id=? AND o.action_id=? AND o.kind='REVIEW_FINDINGS' AND o.state='REGISTERED'",tenant,project,task,run,action);
   if(rows.size()!=1)throw new TenantAccessException(ErrorCode.STEP_NOT_READY);var row=rows.get(0);var parser=new TaskSourceVerifierSchema();var receipt=parser.read(row.get("receipt_json").toString());
   if(!parser.hash(receipt).equals(row.get("receipt_sha256").toString())||!parser.hash(parser.read(row.get("canonical_payload").toString())).equals(row.get("payload_sha256").toString()))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
   for(var pair:Map.of("tenant_id",tenant,"project_id",project,"run_id",run,"action_id",action,"artifact_id",row.get("artifact_id").toString(),"payload_sha256",row.get("payload_sha256").toString()).entrySet())if(!pair.getValue().equals(receipt.required(pair.getKey()).asText()))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
   var audits=db.queryForList("SELECT resource_ref,result FROM audit_event WHERE tenant_id=? AND audit_id=?",tenant,receipt.required("audit_id").asText());
   var events=db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=?",tenant,receipt.required("event_id").asText());
   if(audits.size()!=1||events.size()!=1||!"SUCCESS".equals(audits.get(0).get("result"))||!receipt.equals(parser.read(audits.get(0).get("resource_ref").toString()))||!receipt.equals(parser.read(events.get(0).get("payload").toString())))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
   var comparison=receipt.required("provenance").required("normalization").get("nonclinical_principles_risk");if(comparison==null)throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
   // Reauthorize both the actual artifact and the approved scenario's entire current dependency closure.
   var spec=comparison.required("specification");var checked=dependencies.requireByUse(a,project,List.of(new AgentPublicationDependencies.DependencyUse(new PermissionProtocol.Basis(row.get("artifact_id").toString(),row.get("payload_sha256").toString()),"SOURCE_VERIFICATION"),new AgentPublicationDependencies.DependencyUse(new PermissionProtocol.Basis(spec.required("version_id").asText(),spec.required("content_sha256").asText()),"SOURCE_VERIFICATION")));
   var result=json.object().put("task_id",task).put("run_id",run).put("action_id",action).put("findings_artifact_id",row.get("artifact_id").toString()).put("historical_machine_result",true).put("legal_approval",false).put("human_review_required",true);result.set("nonclinical_principles_risk",comparison.deepCopy());result.set("current_read_authorizations",checked.authorizations());
   return result;
  }).get());
 }
}
