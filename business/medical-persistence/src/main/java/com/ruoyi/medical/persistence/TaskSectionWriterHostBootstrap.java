package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Server-owned HOST_CONTROL binding and token. No caller permission list, model.send or session impersonation. */
public final class TaskSectionWriterHostBootstrap {
 private final JdbcTemplate db;private final FormalTaskNodeAuthority nodes;private final RunTokenAuthority tokens;
 private final TaskSectionWriterHostAccess hosts;private final ServiceCertificateIdentity services;
 private final TaskSectionWriterDeployment deployment;private final PermissionSchema json;private final RunTokenAuthoritySchema schema;
 public TaskSectionWriterHostBootstrap(DataSource data,FormalTaskNodeAuthority nodes,RunTokenAuthority tokens,TaskSectionWriterHostAccess hosts,
   ServiceCertificateIdentity services,TaskSectionWriterDeployment deployment,PermissionSchema json,RunTokenAuthoritySchema schema){
  this(new JdbcTemplate(data),nodes,tokens,hosts,services,deployment,json,schema);
 }
 TaskSectionWriterHostBootstrap(JdbcTemplate db,FormalTaskNodeAuthority nodes,RunTokenAuthority tokens,TaskSectionWriterHostAccess hosts,
   ServiceCertificateIdentity services,TaskSectionWriterDeployment deployment,PermissionSchema json,RunTokenAuthoritySchema schema){
  this.db=db;db.setQueryTimeout(5);this.nodes=nodes;this.tokens=tokens;this.hosts=hosts;this.services=services;this.deployment=deployment;this.json=json;this.schema=schema;
 }
 /** Claim uses the existing exact current mTLS, ROLE/078/093 and cumulative-budget authority. */
 public RunTokenAuthority.Lease claim(HttpServletRequest http,String tenant,String actor,String run,String step,BigInteger revision,Duration lifetime)throws Exception {
  return tokens.claimStep(http,new RunTokenAuthority.ClaimRequest(tenant,actor,run,step,revision,lifetime));
 }
 public TaskSectionWriterHostAccess.Handle open(HttpServletRequest http,String tenant,String actor,String run,String step,BigInteger generation)throws Exception {
  var service=service(http);var bound=bound(http,tenant,actor,run,step,generation,service,null,null,null);return issue(http,tenant,actor,run,step,generation,bound);
 }
 /** Worker routes supply the already-dispatched binding, never an actor or command. */
 public TaskSectionWriterHostAccess.Handle open(HttpServletRequest http,ProfessionalRuntimeObservations.Binding binding)throws Exception {
  var service=service(http);String actor=actor(binding);var bound=bound(http,binding.tenant_id(),actor,binding.run_id(),binding.step_id(),BigInteger.valueOf(binding.lease_generation()),service,binding,null,null);
  return issue(http,binding.tenant_id(),actor,binding.run_id(),binding.step_id(),BigInteger.valueOf(binding.lease_generation()),bound);
 }
 private ServiceCertificateIdentity.Verified service(HttpServletRequest http)throws Exception {var service=services.require(http);if(!service.serviceUri().equals(deployment.workerService()))deny();return service;}
 private String actor(ProfessionalRuntimeObservations.Binding binding){Objects.requireNonNull(binding);var actor=one(db.queryForList("SELECT created_by FROM task_run WHERE tenant_id=? AND run_id=? AND execution_kind='TASK' LIMIT 2",binding.tenant_id(),binding.run_id())).get("created_by");if(!(actor instanceof String value))deny();return (String)actor;}
 private Bound bound(HttpServletRequest http,String tenant,String actor,String run,String step,BigInteger generation,ServiceCertificateIdentity.Verified service,ProfessionalRuntimeObservations.Binding existing,WorkflowProtocol.Reference expectedReference,WorkflowProtocol.Dispatch expectedDispatch)throws Exception {
  WireValues.uuid(actor);if(existing!=null)same(actionId(tenant,run,step,generation),existing.action_id());
  var bound=nodes.execute(http,tenant,actor,run,step,null,false,(watermarks,node)->{
   var a=node.controlAdmission();var input=node.currentWriterInput();input.requireUnchanged();
   var primary=new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(tenant,input.projectId(),"TASK",input.taskId()),"task.run","TASK_EXECUTION");
   if(!node.nodePermissions().contains(primary))deny();
   var actions=hostActions(node.nodePermissions());
   String commandId=null;if(existing!=null){same(input.inputSha256(),existing.input_sha256());}
   var lease=one(db.queryForList("SELECT s.*,r.classification run_classification FROM run_step s JOIN task_run r ON r.tenant_id=s.tenant_id AND r.project_id=s.project_id AND r.run_id=s.run_id WHERE s.tenant_id=? AND s.run_id=? AND s.step_id=? AND s.status='RUNNING' AND s.lease_generation=? AND s.lease_owner=? AND s.lease_expires_at>UTC_TIMESTAMP(6) AND r.status='RUNNING' AND r.ended_at IS NULL AND r.dispatch_closed_at IS NULL AND r.created_by=? LIMIT 2 FOR UPDATE",tenant,run,step,generation,service.serviceUri(),actor));
   same(input.inputSha256(),lease.get("input_sha256"));String action=actionId(tenant,run,step,generation);String arguments=input.inputSha256(),tool=deployment.value().required("host_tool_version").textValue();
   var rows=db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",tenant,run,step,action);
   if(rows.isEmpty()){
    if(existing!=null)deny();
    var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
    changed(db.update("INSERT INTO step_action_binding(tenant_id,project_id,run_id,step_id,action_id,input_sha256,origin_run_id,origin_step_id,origin_action_id,kind,arguments_sha256,tool_version,state,lease_generation,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,?,?,?,'HOST_CONTROL',?,?,'BOUND',?,'1.0.0',?,?,?,?,1,?)",tenant,input.projectId(),run,step,action,input.inputSha256(),run,step,action,arguments,tool,generation,schema.manifestSha256(),actor,now,lease.get("run_classification"),now));
   }
   var row=one(db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",tenant,run,step,action));
   for(var pin:Map.ofEntries(Map.entry("tenant_id",tenant),Map.entry("project_id",input.projectId()),Map.entry("run_id",run),Map.entry("step_id",step),Map.entry("action_id",action),Map.entry("input_sha256",input.inputSha256()),Map.entry("origin_run_id",run),Map.entry("origin_step_id",step),Map.entry("origin_action_id",action),Map.entry("kind","HOST_CONTROL"),Map.entry("arguments_sha256",arguments),Map.entry("tool_version",tool),Map.entry("state","BOUND"),Map.entry("schema_version","1.0.0"),Map.entry("schema_manifest_sha256",schema.manifestSha256()),Map.entry("created_by",actor)).entrySet())same(pin.getValue(),row.get(pin.getKey()));
   same(generation.toString(),String.valueOf(row.get("lease_generation")));same(lease.get("run_classification"),row.get("classification"));if(row.get("invocation_id")!=null)deny();
   input.requireUnchanged();node.requireUnchanged();return new Bound(new RunTokenAuthority.ActionRequest(run,step,action,generation,input.inputSha256(),arguments,"HOST_CONTROL",tool,primary.scope(),primary.action(),primary.purpose()),actions,commandId);
  });
  var after=service(http);same(service.serviceUri(),after.serviceUri());same(service.certificateSha256(),after.certificateSha256());same(service.trustRevision(),after.trustRevision());return bound;
 }
 private TaskSectionWriterHostAccess.Handle issue(HttpServletRequest http,String tenant,String actor,String run,String step,BigInteger generation,Bound bound)throws Exception {
  var token=tokens.issue(http,new RunTokenAuthority.IssueRequest(tenant,actor,run,step,generation,bound.actions(),Duration.ofSeconds(deployment.value().required("token_lifetime_seconds").longValue())));
  return hosts.open(http,token.token(),bound.action());
 }
 static List<String> hostActions(List<RunTokenAuthoritySchema.Permission> permissions){
  var actions=new TreeSet<String>();for(var permission:permissions)if(permission.action().endsWith(".read")||"task.run".equals(permission.action()))actions.add(permission.action());
  if(!actions.contains("task.run")||actions.contains("model.send"))deny();return List.copyOf(actions);
 }
 static String actionId(String tenant,String run,String step,BigInteger generation){for(String id:List.of(tenant,run,step))WireValues.uuid(id);WireValues.revision(generation.toString());if(generation.signum()<=0)deny();return UUID.nameUUIDFromBytes(("TASK_SECTION_WRITER_HOST_CONTROL|"+tenant+"|"+run+"|"+step+"|"+generation).getBytes(StandardCharsets.US_ASCII)).toString();}
 private record Bound(RunTokenAuthority.ActionRequest action,List<String> actions,String commandId){}
 private static <T>T one(List<T> rows){if(rows.size()!=1)deny();return rows.get(0);}private static void same(Object a,Object b){if(!Objects.equals(a,b))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void changed(int n){if(n!=1)deny();}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
