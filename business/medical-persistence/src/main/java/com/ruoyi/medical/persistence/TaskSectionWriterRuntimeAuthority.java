package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
/** N04 lease capability over the actual TASK input and current original production authority. */
public final class TaskSectionWriterRuntimeAuthority {
 private final JdbcTemplate db;private final TaskSectionWriterSchema schema;
 public TaskSectionWriterRuntimeAuthority(DataSource data,TaskSectionWriterSchema schema){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;}
 public record Job(String tenant,String project,String task,String run,String step,String actor,String principal,String inputHash,String classification,LocalDateTime created){}
 public Verified requireLeased(EnterpriseAccess.Admission admission,TaskSectionWriterInputAuthority.Verified input,String action,BigInteger generation,String service)throws Exception{
  input.requireUnchanged();if(!admission.context().tenant_id().equals(input.tenantId())||!admission.actorId().equals(input.production().actorId()))deny();WireValues.uuid(action);WireValues.revision(generation.toString());var p=input.production();
  var created=one(db.query("SELECT created_at FROM task_run WHERE tenant_id=? AND run_id=? AND task_id=? AND created_by=? AND execution_kind='TASK' AND status='RUNNING' AND ended_at IS NULL AND dispatch_closed_at IS NULL LIMIT 2 FOR UPDATE",(r,n)->r.getTimestamp(1).toLocalDateTime(),p.tenantId(),p.runId(),p.taskId(),p.actorId()));
  var j=new Job(p.tenantId(),p.projectId(),p.taskId(),p.runId(),input.stepId(),p.actorId(),p.principalId(),input.inputSha256(),p.classification(),created);
  return new Verified(admission,input,j,action,generation,service,leased(j,action,generation,service));
 }
 private String leased(Job j,String action,BigInteger generation,String service)throws Exception{
  var step=one(db.queryForList("SELECT s.tenant_id,s.project_id,s.run_id,s.step_id,n.node_kind,s.input_sha256,s.lease_generation,s.lease_owner FROM run_step s JOIN runtime_node_definition n ON n.tenant_id=s.tenant_id AND n.project_id=s.project_id AND n.run_id=s.run_id AND n.step_id=s.step_id WHERE s.tenant_id=? AND s.project_id=? AND s.run_id=? AND s.step_id=? AND n.node_kind='N04' AND status='RUNNING' AND input_sha256=? AND lease_generation=? AND lease_owner=? AND lease_expires_at>UTC_TIMESTAMP(6) LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),j.inputHash(),generation,service));
  var binding=one(db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND input_sha256=? AND lease_generation=? AND created_by=? LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),action,j.inputHash(),generation,j.actor()));
  if(!Set.of("HOST_CONTROL","MODEL_CALL","TOOL").contains(binding.get("kind"))||!Set.of("BOUND","RESERVED","DISPATCHING","IN_FLIGHT","SETTLED").contains(binding.get("state")))deny();
  var pins=new LinkedHashMap<>(binding);for(String k:List.of("state","invocation_id","revision","updated_at"))pins.remove(k);
  return schema.hash(AgentPublicationWitness.tree(Map.of("job",j,"step",step,"action",pins)));
 }
 public final class Verified{
  private final EnterpriseAccess.Admission admission;private final TaskSectionWriterInputAuthority.Verified input;private final Job job;private final String action,service,fingerprint;private final BigInteger generation;
  private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> resources=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
  private Verified(EnterpriseAccess.Admission admission,TaskSectionWriterInputAuthority.Verified input,Job job,String action,BigInteger generation,String service,String fingerprint){this.admission=admission;this.input=input;this.job=job;this.action=action;this.generation=generation;this.service=service;this.fingerprint=fingerprint;if(resources.isEmpty())deny();}
  public void requireActive(){if(!TransactionSynchronizationManager.isActualTransactionActive()||thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent())deny();var actual=TransactionSynchronizationManager.getResourceMap();if(actual.size()!=resources.size()||resources.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))deny();}
  public void requireUnchanged()throws Exception{requireActive();input.requireUnchanged();if(!fingerprint.equals(leased(job,action,generation,service)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
  public TaskSectionWriterInputAuthority.Verified input(){requireActive();return input;}public EnterpriseAccess.Admission admission(){requireActive();return admission;}public Job job(){requireActive();return job;}
  public String stepId(){return job().step();}public String actionId(){requireActive();return action;}public BigInteger leaseGeneration(){requireActive();return generation;}public String workerService(){requireActive();return service;}public String inputSha256(){return job().inputHash();}
 }
 private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)deny();return rows.get(0);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
