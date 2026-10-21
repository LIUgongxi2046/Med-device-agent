package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
/** Actual outbox-backed N01 consumer boundary. The separately deployed mTLS worker
 * lists bounded original commands and invokes launch; a browser never impersonates it. */
public final class TaskPlanDraftLaunchConsumer {
 private final JdbcTemplate db;private final TaskPlanDraftControlAccess controls;private final TaskPlanDraftLaunchService launches;private final ServiceCertificateIdentity identities;private final TaskPlanDraftDeployment deployment;private final TaskPlanDraftSchema schema;
 public TaskPlanDraftLaunchConsumer(DataSource data,TaskPlanDraftControlAccess controls,TaskPlanDraftLaunchService launches,ServiceCertificateIdentity identities,TaskPlanDraftDeployment deployment,TaskPlanDraftSchema schema){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.controls=controls;this.launches=launches;this.identities=identities;this.deployment=deployment;this.schema=schema;}
 public JsonNode queued(HttpServletRequest http,String tenant)throws Exception {
  service(http);WireValues.uuid(tenant);var rows=db.queryForList("SELECT d.run_id,d.operation_id FROM task_plan_draft_job d JOIN system_job_run j ON j.tenant_id=d.tenant_id AND j.run_id=d.run_id JOIN outbox_event e ON e.tenant_id=d.tenant_id AND e.event_id=d.event_id AND e.aggregate_id=d.run_id AND e.event_type='task.plan_draft_queued' WHERE d.tenant_id=? AND j.job_type='PLAN_DRAFT' AND j.status='QUEUED' ORDER BY d.created_at,d.run_id LIMIT 100",tenant);
  var result=new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode();
  for(var row:rows){String run=(String)row.get("run_id"),op=(String)row.get("operation_id");controls.withReads(http,tenant,run,op,reads->reads.execute(a->{var services=reads.admission().required("principal_manifest").required("services");if(!services.isArray()||services.size()!=1||!deployment.workerService().equals(services.get(0).required("service_uri").textValue()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return null;}));result.addObject().put("tenant_id",tenant).put("run_id",run).put("original_operation_id",op);}
  service(http);return result;
 }
 public JsonNode launch(HttpServletRequest http,String tenant,String run,String operation)throws Exception{service(http);WireValues.uuid(tenant);WireValues.uuid(run);WireValues.uuid(operation);return launches.consume(http,tenant,run,operation);}
 private void service(HttpServletRequest http)throws Exception{if(!deployment.workerService().equals(identities.require(http).serviceUri()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
