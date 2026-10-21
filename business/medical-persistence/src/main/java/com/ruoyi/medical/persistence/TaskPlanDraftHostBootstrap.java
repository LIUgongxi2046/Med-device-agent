package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.workflow.PlanDraftProtocol;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;
/** Initial lease comes only from real accepted N01 workflow. Reentry uses the same
 * original lease/action, never reclaims or restarts an uncertain process. */
public final class TaskPlanDraftHostBootstrap {
 private final JdbcTemplate db;private final TaskPlanDraftControlAccess controls;private final TaskPlanDraftLaunchLedger launches;private final RunTokenAuthority tokens;private final TaskPlanDraftHostAccess hosts;private final TaskPlanDraftDeployment deployment;private final ServiceCertificateIdentity identities;
 public TaskPlanDraftHostBootstrap(DataSource data,TaskPlanDraftControlAccess controls,TaskPlanDraftLaunchLedger launches,RunTokenAuthority tokens,TaskPlanDraftHostAccess hosts,TaskPlanDraftDeployment deployment,ServiceCertificateIdentity identities){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.controls=controls;this.launches=launches;this.tokens=tokens;this.hosts=hosts;this.deployment=deployment;this.identities=identities;}
 public <T>T open(HttpServletRequest http,PlanDraftProtocol.Reference reference,TaskPlanDraftHostAccess.Operation<T> operation)throws Exception{return enter(http,reference.tenantId(),reference.runId(),reference,null,operation);}
 public <T>T existing(HttpServletRequest http,JsonNode binding,TaskPlanDraftHostAccess.Operation<T> operation)throws Exception{
  ProfessionalRuntimeObservations.closed(binding,Set.of("tenant_id","run_id","step_id","action_id","lease_generation","input_sha256","step_envelope_sha256"));return enter(http,text(binding,"tenant_id"),text(binding,"run_id"),null,binding,operation);
 }
 private <T>T enter(HttpServletRequest http,String tenant,String run,PlanDraftProtocol.Reference expected,JsonNode binding,TaskPlanDraftHostAccess.Operation<T> operation)throws Exception {
  var service=identities.require(http);if(!deployment.workerService().equals(service.serviceUri()))deny();
  String command=one(db.query("SELECT operation_id FROM task_plan_draft_job WHERE tenant_id=? AND run_id=? LIMIT 2",(r,n)->r.getString(1),tenant,run));
  return controls.withReads(http,tenant,run,command,reads->ThrowingSupplier.of(()->{
   var original=reads.execute(a->ThrowingSupplier.of(()->launches.recoveryOriginal(tenant,run)).get());var reference=original.reference();var j=reads.job();
   if(expected!=null&&!expected.equals(reference))deny();
   reads.execute(a->{if(one(db.query("SELECT COUNT(*) FROM task_plan_draft_launch_receipt r JOIN system_job_run j ON j.tenant_id=r.tenant_id AND j.run_id=r.run_id WHERE r.tenant_id=? AND r.run_id=? AND r.workflow_id=? AND r.reference_sha256=? AND j.workflow_id=r.workflow_id AND j.status='RUNNING' AND j.started_at IS NOT NULL AND j.ended_at IS NULL",(r,n)->r.getInt(1),tenant,run,reference.workflowId(),original.referenceHash()))!=1)deny();return null;});
   BigInteger generation;
   if(binding==null){
    var revision=reads.execute(a->one(db.query("SELECT revision FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? AND status='READY' AND lease_owner IS NULL AND lease_generation=0 LIMIT 2",(r,n)->r.getBigDecimal(1).toBigIntegerExact(),tenant,run,j.step())));
    var lease=tokens.claimStep(http,new RunTokenAuthority.ClaimRequest(tenant,j.actor(),run,j.step(),revision,Duration.ofSeconds(deployment.value().required("lease_lifetime_seconds").longValue())));generation=lease.generation();
   }else{
    if(!j.step().equals(text(binding,"step_id"))||!j.action().equals(text(binding,"action_id"))||!j.inputHash().equals(text(binding,"input_sha256")))deny();generation=binding.required("lease_generation").bigIntegerValue();if(generation.signum()<=0)deny();
   }
   var permissions=new RunTokenAuthoritySchema().permissions(reads.admission().required("principal_manifest"));var actions=new TreeSet<String>();for(var p:permissions)if(p.action().endsWith(".read")||"task.run".equals(p.action()))actions.add(p.action());if(!actions.contains("task.run"))deny();
   var signed=tokens.issue(http,new RunTokenAuthority.IssueRequest(tenant,j.actor(),run,j.step(),generation,List.copyOf(actions),Duration.ofSeconds(deployment.value().required("token_lifetime_seconds").longValue())));
   var action=new RunTokenAuthority.ActionRequest(run,j.step(),j.action(),generation,j.inputHash(),j.inputHash(),"HOST_CONTROL","PLAN_DRAFT_HOST_V1",new PermissionProtocol.Scope(tenant,j.project(),"TASK",j.task()),"task.run","TASK_EXECUTION");
   return hosts.withHost(http,tenant,run,command,signed.token(),action,host->{if(binding!=null)host.prepare(e->{if(!e.binding().equals(binding))deny();return null;});return operation.apply(host);});
  }).get());
 }
 private static String text(JsonNode n,String k){if(!n.required(k).isTextual())deny();return n.required(k).textValue();}private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)deny();return rows.get(0);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
