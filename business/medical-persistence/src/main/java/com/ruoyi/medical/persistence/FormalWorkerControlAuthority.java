package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Metadata-only proof for policy verification before worker content admission.
 * No EnterpriseAccess.Admission, content read, action token, lease or grant is created.
 * The callback is only invoked with a real mTLS identity and locked persisted run/node.
 */
public final class FormalWorkerControlAuthority {
 private final JdbcTemplate jdbc;private final RunTokenAuthorityStore store;private final RunTokenAuthoritySchema schema;
 private final ServiceCertificateIdentity services;private final PermissionMapper permissions;private final PermissionAuthority grants;
 public FormalWorkerControlAuthority(DataSource data,RunTokenAuthoritySchema schema,ServiceCertificateIdentity services,PermissionMapper permissions,PermissionAuthority grants){this.grants=Objects.requireNonNull(grants);jdbc=new JdbcTemplate(data);store=new RunTokenAuthorityStore(data);this.schema=schema;this.services=services;this.permissions=permissions;}
 FormalWorkerControlAuthority(JdbcTemplate jdbc,RunTokenAuthorityStore store,RunTokenAuthoritySchema schema,ServiceCertificateIdentity services,PermissionMapper permissions,PermissionAuthority grants){this.jdbc=jdbc;this.store=store;this.schema=schema;this.services=services;this.permissions=permissions;this.grants=grants;}
 <T>T withRunControl(HttpServletRequest http,String run,String actor,String audience,Function<VerifiedControl,T> operation)throws Exception{
  return withControl(http,run,actor,null,audience,operation);
 }
 <T>T withControl(HttpServletRequest http,String run,String actor,String step,String audience,Function<VerifiedControl,T> operation)throws Exception{
  var identity=read(http,run,actor,step,audience);var proof=new VerifiedControl(this,http,identity);
  try{var result=operation.apply(proof);proof.requireUnchanged();return result;}finally{proof.active=false;}
 }
 private Snapshot read(HttpServletRequest http,String runId,String actor,String stepId,String audience)throws Exception{
  var scope=TenantScope.requireCurrent();if(!TransactionSynchronizationManager.isActualTransactionActive()||!scope.subjectId().equals(actor))deny();
  var service=services.require(http);var run=store.run(runId,actor);
  if(!"TASK".equals(run.kind())||run.project()==null||run.task()==null||!run.actor().equals(actor)||!run.executionActor().equals(actor)
    ||!"ACTIVE".equals(run.principalStatus())||!(stepId==null?List.of("QUEUED","RUNNING").contains(run.status()):"RUNNING".equals(run.status()))||run.ended()!=null||run.closed()!=null||run.superseded()!=null
    ||!scope.tenantId().equals(run.tenant())||!schema.manifestSha256().equals(run.manifest())||!schema.manifestSha256().equals(run.principalManifest()))deny();
  var manifest=schema.scopeManifest(run.scope(),run.tenant(),run.project(),actor,run.id(),run.kind());
  var principal=schema.principalManifest(run.capabilities(),run.tenant(),run.project(),actor,run.id(),run.principal(),manifest);
  schema.service(principal,service.serviceUri(),audience);
  var binding=one(permissions.currentBinding(actor));if(!actor.equals(binding.userId())||!"ACTIVE".equals(binding.status()))deny();
  List<String> accounts;
  if("local".equals(binding.mode())){if(!actor.equals(binding.localUserId()))deny();accounts=jdbc.query("SELECT user_id FROM platform_local_account WHERE user_id=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getString(1),actor);}
  else if("oidc".equals(binding.mode())){if(binding.localUserId()!=null)deny();accounts=jdbc.query("SELECT user_id FROM platform_identity WHERE identity_id=? AND user_id=? AND issuer=? AND subject=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getString(1),binding.identityId(),actor,binding.issuer(),binding.subject());}
  else{deny();return null;}
  if(accounts.size()!=1||!"ACTIVE".equals(one(permissions.projectStatus(run.project()))))deny();
  one(jdbc.query("SELECT user_id FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE' AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6)) FOR UPDATE",(r,n)->r.getString(1),run.tenant(),actor));
  grants.requireResource(new PermissionProtocol.Scope(run.tenant(),run.project(),"TASK",run.task()),actor,binding.mode(),"task.run","TASK_EXECUTION",permissions.resourceGrants(actor,"task.run"),store.now());
  var saved=one(jdbc.query("""
    SELECT r.replay_manifest,o.payload FROM task_run r JOIN outbox_event o ON o.tenant_id=r.tenant_id AND o.aggregate_id=r.run_id AND o.event_type='run.start.requested'
    WHERE r.tenant_id=? AND r.project_id=? AND r.run_id=? AND r.created_by=? AND r.execution_kind='TASK' LIMIT 2 FOR UPDATE
    """,(r,n)->List.of(r.getString(1),r.getString(2)),run.tenant(),run.project(),run.id(),actor));
  var reader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
  var savedReplay=reader.readTree(saved.get(0));var start=reader.readTree(saved.get(1));var startCommand=start.required("command");
  if(!"START_RUN".equals(startCommand.required("kind").asText())||!"1.0.0".equals(startCommand.required("schema_version").asText())
    ||!run.task().equals(startCommand.required("task_id").asText())||!(run.tenant()+"/"+run.id()).equals(startCommand.required("workflow_id").asText())
    ||!schema.sha256(savedReplay).equals(startCommand.required("replay_sha256").asText())||!run.id().equals(startCommand.required("run_id").asText())
    ||!run.principal().equals(startCommand.required("principal_id").asText())||!run.tenant().equals(startCommand.required("tenant_id").asText())
    ||!run.project().equals(startCommand.required("project_id").asText())||!actor.equals(start.required("decision").required("actor_id").asText()))deny();
  var original=start.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)original.required("command")).remove("command_sha256");
  if(!schema.sha256(original).equals(startCommand.required("command_sha256").asText()))deny();
  var policyIdentity=savedReplay.required("execution_policy_version");
  if(stepId==null)return new Snapshot(run,null,new Node(null,null,null,policyIdentity.required("version_id").asText(),policyIdentity.required("content_sha256").asText(),null,null,null,null,null,null,schema.canonical(savedReplay)),service.serviceUri(),service.certificateSha256(),service.trustRevision(),audience,binding.mode(),savedReplay.required("execution_policy").required("business_purpose").asText());
  var node=one(jdbc.query("""
    SELECT n.node_kind,n.role_version,n.role_sha256,n.policy_version,n.policy_sha256,
      n.canonical_definition,n.definition_sha256,s.input_sha256,s.status,s.lease_generation,s.lease_owner,
      r.replay_manifest
    FROM runtime_node_definition n
    JOIN run_step s ON s.tenant_id=n.tenant_id AND s.project_id=n.project_id AND s.run_id=n.run_id AND s.step_id=n.step_id AND s.role_version=n.role_version
    JOIN capability_version c ON c.tenant_id=n.tenant_id AND c.capability_version=n.role_version AND c.content_sha256=n.role_sha256 AND c.kind='ROLE'
    JOIN version_registry v ON v.tenant_id=c.tenant_id AND v.version_id=c.capability_version AND v.content_sha256=c.content_sha256 AND v.owner_project_id <=> c.project_id AND v.entity_kind='CAPABILITY_VERSION'
    JOIN task_run r ON r.tenant_id=n.tenant_id AND r.project_id=n.project_id AND r.run_id=n.run_id
    WHERE n.tenant_id=? AND n.project_id=? AND n.run_id=? AND n.step_id=? AND n.created_by=?
      AND (c.project_id IS NULL OR c.project_id=n.project_id) FOR UPDATE
    """,(r,n)->new Node(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getString(12)),run.tenant(),run.project(),run.id(),stepId,actor));
  var definition=schema.tree(new com.fasterxml.jackson.databind.ObjectMapper().readTree(node.definition()));
  if(!schema.sha256(definition).equals(node.definitionSha()))deny();
  var replay=reader.readTree(node.replay());if(!replay.equals(savedReplay))deny();var policy=replay.required("execution_policy_version");
  if(!node.policy().equals(policy.required("version_id").asText())||!node.policySha().equals(policy.required("content_sha256").asText())
    ||!List.of("READY","RUNNING").contains(node.status()))deny();
  return new Snapshot(run,stepId,node,service.serviceUri(),service.certificateSha256(),service.trustRevision(),audience,binding.mode(),savedReplay.required("execution_policy").required("business_purpose").asText());
 }
 private record Node(String kind,String role,String roleSha,String policy,String policySha,String definition,String definitionSha,String input,String status,String generation,String owner,String replay){}
 private record Snapshot(RunTokenAuthorityStore.Run run,String step,Node node,String service,String certificateSha,String trustRevision,String audience,String mode,String purpose){}
 public static final class VerifiedControl {
  private final FormalWorkerControlAuthority owner;private final HttpServletRequest http;private final Snapshot value;
  private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();
  private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());private boolean active=true;
  private VerifiedControl(FormalWorkerControlAuthority owner,HttpServletRequest http,Snapshot value){this.owner=owner;this.http=http;this.value=value;if(transaction.isEmpty())deny();}
  private void active(){if(!active||thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||!TransactionSynchronizationManager.isActualTransactionActive())deny();var current=TransactionSynchronizationManager.getResourceMap();if(current.size()!=transaction.size()||transaction.entrySet().stream().anyMatch(e->current.get(e.getKey())!=e.getValue()))deny();}
  public void requireUnchanged(){active();try{if(!value.equals(owner.read(http,value.run().id(),value.run().actor(),value.step(),value.audience())))deny();}catch(RuntimeException e){throw e;}catch(Exception e){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}}
  public void requireLiteralMetadataPermission(PermissionProtocol.Scope resource,String purpose){
   requireUnchanged();if(!"CONFIGURATION".equals(resource.resource_kind())||!value.run().tenant().equals(resource.tenant_id()))deny();
   try{var run=value.run();var declared=owner.schema.scopeManifest(run.scope(),run.tenant(),run.project(),run.actor(),run.id(),run.kind());
    var principal=owner.schema.principalManifest(run.capabilities(),run.tenant(),run.project(),run.actor(),run.id(),run.principal(),declared);
    if(!owner.schema.permissions(principal).contains(new RunTokenAuthoritySchema.Permission(resource,"configuration.read",purpose)))deny();
   }catch(RuntimeException e){throw e;}catch(Exception e){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
  }
  public String tenantId(){active();return value.run().tenant();}public String projectId(){active();return value.run().project();}public String actorId(){active();return value.run().actor();}public String principalId(){active();return value.run().principal();}public String runId(){active();return value.run().id();}public String stepId(){active();return value.step();}
  public boolean runOnly(){active();return value.step()==null;}public String nodeKind(){active();if(runOnly())deny();return value.node().kind();}public String roleVersion(){active();if(runOnly())deny();return value.node().role();}public String roleContentSha256(){active();if(runOnly())deny();return value.node().roleSha();}public String roleRawSha(){return roleContentSha256();}public String policyVersion(){active();return value.node().policy();}public String policySha256(){active();return value.node().policySha();}
  public String serviceUri(){active();return value.service();}public String audience(){active();return value.audience();}public String businessPurpose(){active();return value.purpose();}public String identityMode(){active();return value.mode();}
 }
 private static <T>T one(List<T> values){if(values.size()!=1)deny();return values.get(0);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
