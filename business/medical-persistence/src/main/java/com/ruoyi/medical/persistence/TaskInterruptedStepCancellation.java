package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.workflow.WorkflowProtocol.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;
/** Disposes a claimed step under independently proven quiescence. Never invents a Dispatch or a successful stage result. */
final class TaskInterruptedStepCancellation {
 static final String ACTION="task.run.interrupted.cancelled.v1";
 private static final byte[] SCHEMA=resource();
 private static final com.networknt.schema.JsonSchema VALIDATOR=validator();
 private static final String MANIFEST=org.apache.commons.codec.digest.DigestUtils.sha256Hex(SCHEMA);
 static boolean eligible(JdbcTemplate db,TaskRunLifecycleAuthority.Proof p,String step)throws Exception{
  p.require(p.operation());if(!"CANCEL_REQUESTED".equals(p.status())||p.graph().nodes().stream().noneMatch(n->n.stepId().equals(step)))return false;
  var ref=p.reference();var run=one(db.queryForList("SELECT dispatch_closed_at FROM task_run WHERE tenant_id=? AND run_id=? AND status='CANCEL_REQUESTED' LIMIT 2 FOR UPDATE",ref.tenantId(),ref.runId()));if(run.get("dispatch_closed_at")==null)return false;
  // N02 launch must first persist its immutable envelope intent. Checking every
  // intent is deliberately stricter than checking a missing session after spawn.
  for(String table:List.of("runtime_object_intent","professional_runtime_session","invocation","task_section_writer_runtime_prepare","task_source_verifier_runtime_prepare"))
   if(!db.queryForList("SELECT step_id FROM "+table+" WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 1 FOR UPDATE",ref.tenantId(),ref.runId(),step).isEmpty())return false;
  return true;
 }
 static Receipt write(JdbcTemplate db,PermissionSchema json,TaskRunLifecycleAuthority.Proof p,String step,TaskRunTerminationFacts.Verified facts)throws Exception{
  facts.requireUnchanged();if(!facts.invocationsTerminal()||!facts.processesExited())bad();boolean never=eligible(db,p,step);var ref=p.reference();var row=one(db.queryForList("SELECT * FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.runId(),step));same("RUNNING",text(row,"status"));long generation=Long.parseLong(number(row,"lease_generation"));if(generation<1)bad();String id=derived(step,ACTION+":"+ref.runId());
  var result=new Receipt(step,text(row,"input_sha256"),generation,State.CANCELLED,id,null,List.of());var journal=new TaskRunActivityJournal(db,json);var request=json.object().put("run_id",ref.runId()).put("step_id",step).put("lease_generation",generation).put("input_sha256",result.inputSha256());
  var value=json.object().put("schema_version","1.0.0").put("kind",never?"NEVER_LAUNCHED":"INDEPENDENT_TERMINATION");value.set("termination",facts.evidence());value.set("request",request);value.set("result",json.tree(result));value.set("authority",p.authority());
  if(!VALIDATOR.validate(value).isEmpty())bad();
  journal.write(p.admission(),ref.projectId(),ref.runId(),id,ACTION,"never-launched:"+step,step,journal.hash(request),p.classification(),"1.0.0",MANIFEST,value);
  changed(db.update("UPDATE run_step SET status='CANCELLED',output_ref='[]',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND step_id=? AND status='RUNNING' AND revision=? AND lease_generation=? AND input_sha256=?",ref.tenantId(),ref.runId(),step,number(row,"revision"),generation,result.inputSha256()));return result;
 }
 static Optional<Receipt> read(JdbcTemplate db,PermissionSchema json,EnterpriseAccess.Admission a,Reference ref,String step)throws Exception{
  String id=derived(step,ACTION+":"+ref.runId());var rows=db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND operation_id=? AND operation_name=? LIMIT 2 FOR UPDATE",ref.tenantId(),id,ACTION);if(rows.isEmpty())return Optional.empty();
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var value=mapper.readTree(text(one(rows),"response_ref"));var request=value.required("request");var run=one(db.queryForList("SELECT classification FROM task_run WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.runId()));var journal=new TaskRunActivityJournal(db,json);var saved=journal.read(a,ref.projectId(),ref.runId(),id,ACTION,"never-launched:"+step,step,journal.hash(request),text(run,"classification"),"1.0.0",MANIFEST).orElseThrow();if(!VALIDATOR.validate(saved).isEmpty())bad();if(!Set.of("NEVER_LAUNCHED","INDEPENDENT_TERMINATION").contains(saved.required("kind").asText()))bad();same(ref.runId(),request.required("run_id").asText());same(step,request.required("step_id").asText());var result=mapper.treeToValue(saved.required("result"),Receipt.class);same(id,result.receiptId());same(step,result.stepId());same(State.CANCELLED,result.status());same(request.required("input_sha256").asText(),result.inputSha256());same(request.required("lease_generation").longValue(),result.leaseGeneration());return Optional.of(result);
 }
 private static com.networknt.schema.JsonSchema validator(){try{var result=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(new com.fasterxml.jackson.databind.ObjectMapper().readTree(SCHEMA),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());result.initializeValidators();return result;}catch(java.io.IOException e){throw new IllegalStateException(e);}}
 private static byte[] resource(){try(var in=Objects.requireNonNull(TaskInterruptedStepCancellation.class.getResourceAsStream("/task-interrupted-step-cancellation.schema.json"))){return in.readAllBytes();}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
