package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
/** Current human task-read capability only; original actor is a factual pin, never a substituted admission. */
final class TaskCandidateAssemblyHumanReadAuthority {
 private final JdbcTemplate db;private final ResourceReadAuthority resources;
 TaskCandidateAssemblyHumanReadAuthority(DataSource data,ResourceReadAuthority resources){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.resources=resources;}
 Verified require(EnterpriseAccess.Admission a,String run)throws Exception{var row=inspect(a,run);return new Verified(a,run,row);}
 private Map<String,Object> inspect(EnterpriseAccess.Admission a,String run)throws Exception{
  if(a.subject()==null||!TransactionSynchronizationManager.isActualTransactionActive())deny();
  var rows=db.queryForList("SELECT tenant_id,project_id,task_id,created_by FROM task_run WHERE tenant_id=? AND run_id=? AND execution_kind='TASK' LIMIT 2 FOR UPDATE",a.context().tenant_id(),run);if(rows.size()!=1)deny();var row=rows.get(0);
  resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),row.get("project_id").toString(),"TASK",row.get("task_id").toString()),"task.read","DOCUMENT_BODY");return Map.copyOf(row);
 }
 final class Verified {
  private final EnterpriseAccess.Admission admission;private final String run;private final Map<String,Object> original;private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());private boolean ended;
  private Verified(EnterpriseAccess.Admission a,String run,Map<String,Object> row){admission=a;this.run=run;original=row;if(transaction.isEmpty()||!TransactionSynchronizationManager.isSynchronizationActive())deny();TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCompletion(int status){ended=true;}});}
  void require(EnterpriseAccess.Admission a)throws Exception{var now=TransactionSynchronizationManager.getResourceMap();if(a!=admission||ended||thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||transaction.size()!=now.size()||transaction.entrySet().stream().anyMatch(e->now.get(e.getKey())!=e.getValue())||!original.equals(inspect(a,run)))deny();}
  EnterpriseAccess.Admission admission(){return admission;}String runId(){return run;}String originalActorId(){return original.get("created_by").toString();}String projectId(){return original.get("project_id").toString();}String taskId(){return original.get("task_id").toString();}
 }
 private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
