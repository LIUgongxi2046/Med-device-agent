package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Private run-owned bootstrap proof. This is not a step/token or an mTLS-to-session adapter.
 * A caller must already possess actual current publication/policy and authenticated admission.
 * Session-free publication preparation remains a separate required worker integration. */
public final class TaskRunBootstrapAuthority {
 private final JdbcTemplate db;
 private final PermissionSchema json;
 private final ResourceReadAuthority resources;
 private final RunTokenAuthoritySchema tokens;
 private final RuntimeReplaySchema replays;
 private final TaskDelegationService delegations;private final AgentReleaseStore releases;private final AgentReleaseScopeAuthority releaseScopes;private final DependencyLineage.Limits scopeLimits;
 public TaskRunBootstrapAuthority(DataSource data,PermissionSchema json,ResourceReadAuthority resources,
   RunTokenAuthoritySchema tokens,RuntimeReplaySchema replays,TaskDelegationService delegations,AgentReleaseStore releases,AgentReleaseScopeAuthority releaseScopes,DependencyLineage.Limits scopeLimits){this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.resources=resources;this.tokens=tokens;this.replays=replays;this.delegations=delegations;this.releases=releases;this.releaseScopes=releaseScopes;this.scopeLimits=scopeLimits;}

 public Verified require(EnterpriseAccess.Admission admission,AgentPublicationAuthority.VerifiedPublication publication,
   AgentExecutionPolicyAuthority.VerifiedPolicy policy,RuntimeRetentionAuthority.Verified retention,String runId)throws Exception{
  if(!TransactionSynchronizationManager.isActualTransactionActive()||admission.simulationWorker()||admission.recoveryWorker())deny();
  var snapshot=read(admission,publication,policy,retention,runId);
  return new Verified(admission,publication,policy,retention,runId,snapshot);
 }
 private record Snapshot(RuntimeReplaySchema.VerifiedInputs replay,JsonNode command,String event,String classification,String digest){}
 private Snapshot read(EnterpriseAccess.Admission a,AgentPublicationAuthority.VerifiedPublication publication,
   AgentExecutionPolicyAuthority.VerifiedPolicy policy,RuntimeRetentionAuthority.Verified retention,String run)throws Exception{
  RunContinuationService.requireSuccessor(db,a,run);
  var rows=db.query("""
   SELECT r.task_id,r.project_id,r.replay_manifest,r.classification,o.event_id,o.payload,
     p.principal_id,p.capability_manifest,e.scope_manifest,r.schema_manifest_sha256,p.schema_manifest_sha256,e.schema_manifest_sha256
   FROM task_run r JOIN execution_registry e ON e.tenant_id=r.tenant_id AND e.project_id=r.project_id AND e.run_id=r.run_id
   JOIN run_principal p ON p.tenant_id=r.tenant_id AND p.project_id=r.project_id AND p.run_id=r.run_id AND p.initiator_id=r.created_by
   JOIN outbox_event o ON o.tenant_id=r.tenant_id AND o.aggregate_id=r.run_id AND o.event_type='run.start.requested'
   WHERE r.tenant_id=? AND r.run_id=? AND r.created_by=? AND r.execution_kind='TASK' AND e.execution_kind='TASK'
     AND r.status='QUEUED' AND r.started_at IS NULL AND r.ended_at IS NULL AND r.dispatch_closed_at IS NULL
     AND r.superseded_by_run_id IS NULL AND p.status='ACTIVE' LIMIT 2 FOR UPDATE
   """,(rs,n)->new Row(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),
     rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12)),a.context().tenant_id(),run,a.actorId());
  if(rows.size()!=1)deny();var row=rows.get(0);String tenant=a.context().tenant_id();
  for(String manifest:List.of(row.runManifest(),row.principalManifest(),row.scopeManifest()))if(!tokens.manifestSha256().equals(manifest))bad();
  var command=jsonRead(row.command());var c=command.required("command");
  var content=jsonRead(row.replay());byte[] bytes=json.canonical(content).getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var replay=replays.read(bytes,c.required("replay_sha256").asText(),c.required("replay_schema_sha256").asText());
  if(!RuntimeReplaySchema.REF.equals(c.required("replay_schema_ref").asText()))bad();
  var original=command.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)original.required("command")).remove("command_sha256");
  if(!tokens.sha256(original).equals(c.required("command_sha256").asText())||!"START_RUN".equals(c.required("kind").asText())
    ||!"1.0.0".equals(c.required("schema_version").asText())||!tenant.equals(c.required("tenant_id").asText())
    ||!row.project().equals(c.required("project_id").asText())||!row.task().equals(c.required("task_id").asText())
    ||!run.equals(c.required("run_id").asText())||!row.principal().equals(c.required("principal_id").asText())
    ||!(tenant+"/"+run).equals(c.required("workflow_id").asText())||!a.actorId().equals(command.required("decision").required("actor_id").asText()))bad();
  for(var field:Map.of("tenant_id",tenant,"project_id",row.project(),"task_id",row.task(),"run_id",run,"principal_id",row.principal(),"initiator_id",a.actorId()).entrySet())
   if(!field.getValue().equals(content.required(field.getKey()).asText()))bad();
  var scope=tokens.scopeManifest(row.scope(),tenant,row.project(),a.actorId(),run,"TASK");
  var principal=tokens.principalManifest(row.capabilities(),tenant,row.project(),a.actorId(),run,row.principal(),scope);
  if(!scope.equals(content.required("scope_manifest"))||!principal.equals(content.required("principal_manifest")))bad();
  publication.requireConsumer(a,content.required("agent_release_id").asText(),"RUN_CREATION");
  policy.requireConsumer(a,row.project(),"RUN_CREATION");
  if(!publication.approvalSha256().equals(content.required("publication_approval_sha256").asText())
    ||!publication.executionPolicyId().equals(policy.versionId())||!publication.executionPolicySha256().equals(policy.contentSha256())
    ||!policy.versionId().equals(content.required("execution_policy_version").required("version_id").asText())
    ||!policy.contentSha256().equals(content.required("execution_policy_version").required("content_sha256").asText())
    ||!json.tree(policy.definition()).equals(content.required("execution_policy"))||!json.tree(publication.manifest().manifest()).equals(content.required("agent_release")))bad();
  retention.requireUnchanged();
  if(!retention.policyRevision().equals(content.required("retention").required("policy_revision").asText())
    ||!retention.contentSha256().equals(content.required("retention").required("content_sha256").asText()))bad();
  var taskScope=new PermissionProtocol.Scope(tenant,row.project(),"TASK",row.task());
  var storedRelease=releases.read(a,content.required("agent_release_id").asText(),"RUN_CREATION");
  if(!storedRelease.content().equals(publication.manifest()))bad();
  var currentScope=a.controlWorker()?releaseScopes.requireStartRun(storedRelease,taskScope,row.classification(),scopeLimits,a.controlProof().startRun())
    :releaseScopes.require(storedRelease,taskScope,"task.run",row.classification(),scopeLimits);
  var witnesses=json.object().arrayNode();
  for(String action:List.of("task.run")){
   var expected=new RunTokenAuthoritySchema.Permission(taskScope,action,"TASK_EXECUTION");
   if(!tokens.permissions(principal).contains(expected))deny();
   if(a.controlWorker())a.controlProof().requireBootstrap(row.task(),run);
   else witnesses.add(AgentPublicationWitness.tree(resources.require(a,taskScope,action,"TASK_EXECUTION")));
  }
  replayRegistration(a,publication,policy,replay,run).require(a,row.task(),run,"REPLAY_MANIFEST");
  publication.requireUnchanged();policy.requireUnchanged();
  var identity=json.object();identity.set("publication",publication.authorizations());identity.set("policy",policy.authorizations());
  releaseScopes.requireUnchanged(currentScope);
  identity.set("release_scope",AgentPublicationWitness.tree(Map.of("rows",currentScope.scopes(),"roles",currentScope.roles(),"grant",currentScope.grant())));
  identity.set("current_grants",witnesses);identity.set("retention",AgentPublicationWitness.tree(retention.authorizations()));
  identity.put("command_sha256",c.required("command_sha256").asText()).put("replay_sha256",replay.rawSha256());
  return new Snapshot(replay,command,row.event(),row.classification(),tokens.sha256(identity));
 }
 private record Row(String task,String project,String replay,String classification,String event,String command,String principal,
   String capabilities,String scope,String runManifest,String principalManifest,String scopeManifest){}
 private JsonNode jsonRead(String raw)throws Exception{return com.fasterxml.jackson.databind.json.JsonMapper.builder()
  .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(raw);}
 private TaskDelegationService.ReplayRegistration replayRegistration(EnterpriseAccess.Admission a,AgentPublicationAuthority.VerifiedPublication publication,
   AgentExecutionPolicyAuthority.VerifiedPolicy policy,RuntimeReplaySchema.VerifiedInputs replay,String run)throws Exception{
  var content=replay.content();var pinned=jsonRead(content.required("authorizations").required("canonical_witnesses").asText()).required("additional_authorizations");
  var proofs=new ArrayList<TaskDelegationService.ReplayRegistration>();var seen=new HashSet<String>();
  for(var node:policy.definition().bindings()){
   if(!pinned.has(node.nodeKind()))continue;
   var original=pinned.required(node.nodeKind());
   var current=delegations.requireForTask(a,publication,policy,node.roleVersion(),replay.taskId(),run,content.required("budget_revision").asText());
   if(!original.equals(current.authorizations()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
   boolean register=false;for(var operation:original.required("rule").required("request").required("operations"))
    if("TASK".equals(operation.required("selector").asText())&&"artifact.register".equals(operation.required("action").asText())&&"TASK_EXECUTION".equals(operation.required("purpose").asText()))register=true;
   if(register&&seen.add(current.ruleId()))proofs.add(current.requireReplayRegistration("TASK_EXECUTION"));
  }
  if(proofs.size()!=1)deny();return proofs.get(0);
 }
 public final class Verified implements TaskNodeProductionBasis {
  private final EnterpriseAccess.Admission admission;private final AgentPublicationAuthority.VerifiedPublication publication;
  private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final RuntimeRetentionAuthority.Verified retention;
  private final String run;private final Snapshot snapshot;private final Thread thread=Thread.currentThread();
  private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
  private Verified(EnterpriseAccess.Admission a,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,
    RuntimeRetentionAuthority.Verified retention,String run,Snapshot snapshot){this.admission=a;this.publication=publication;this.policy=policy;this.retention=retention;this.run=run;this.snapshot=snapshot;if(transaction.isEmpty())deny();}
  private void active(){if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||!TransactionSynchronizationManager.isActualTransactionActive())deny();
   var actual=TransactionSynchronizationManager.getResourceMap();if(actual.size()!=transaction.size()||transaction.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))deny();}
  public void requireReplayRegistration()throws Exception{active();replayRegistration(admission,publication,policy,snapshot.replay(),run).require(admission,taskId(),run,"REPLAY_MANIFEST");}
  public EnterpriseAccess.Admission admission(){active();return admission;}
  public RuntimeReplaySchema.VerifiedInputs replay(){active();return snapshot.replay();}
  public String runId(){active();return run;}public String taskId(){return replay().taskId();}
  public String principalId(){return replay().content().required("principal_id").asText();}
  public String tenantId(){active();return admission.context().tenant_id();}public String projectId(){return replay().content().required("project_id").asText();}
  public String actorId(){active();return admission.actorId();}public String classification(){active();return snapshot.classification();}
  public JsonNode command(){active();return snapshot.command().deepCopy();}public String outboxEventId(){active();return snapshot.event();}
  public String authorizationsSha256(){active();return snapshot.digest();}
  public void requireUnchanged()throws Exception{active();var fresh=read(admission,publication,policy,retention,run);
   if(!snapshot.digest().equals(fresh.digest())||!snapshot.replay().rawSha256().equals(fresh.replay().rawSha256())
     ||!snapshot.command().equals(fresh.command())||!snapshot.event().equals(fresh.event())||!snapshot.classification().equals(fresh.classification()))
    throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 }
 private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
