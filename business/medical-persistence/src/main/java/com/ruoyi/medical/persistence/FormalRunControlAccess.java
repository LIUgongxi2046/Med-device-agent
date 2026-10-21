package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** mTLS run-owned, read-only evidence preparation. Never creates a human session, step or token.
 * Every short SQL callback authenticates the original command again; no transaction spans object I/O. */
public final class FormalRunControlAccess {
 private final JdbcTemplate db;private final TenantTransaction transactions;private final FormalWorkerControlAuthority controls;
 private final ServiceCertificateIdentity services;private final PermissionMapper permissions;private final VersionResourceMapper owners;
 private final ArtifactRegistryMapper registry;private final PermissionSchema json;private final RunTokenAuthoritySchema schema;
 private final Duration maximumDuration;private final int maximumDependencies;
 public FormalRunControlAccess(DataSource data,TenantTransaction transactions,FormalWorkerControlAuthority controls,
   ServiceCertificateIdentity services,PermissionMapper permissions,VersionResourceMapper owners,ArtifactRegistryMapper registry,
   PermissionSchema json,RunTokenAuthoritySchema schema,Duration maximumDuration,int maximumDependencies){
  this(new JdbcTemplate(data),transactions,controls,services,permissions,owners,registry,json,schema,maximumDuration,maximumDependencies);
 }
 FormalRunControlAccess(JdbcTemplate db,TenantTransaction transactions,FormalWorkerControlAuthority controls,
   ServiceCertificateIdentity services,PermissionMapper permissions,VersionResourceMapper owners,ArtifactRegistryMapper registry,
   PermissionSchema json,RunTokenAuthoritySchema schema,Duration maximumDuration,int maximumDependencies){
  this.db=db;db.setQueryTimeout(5);this.transactions=transactions;this.controls=controls;this.services=services;
  this.permissions=permissions;this.owners=owners;this.registry=registry;this.json=json;this.schema=schema;
  if(maximumDuration==null||maximumDuration.isZero()||maximumDuration.isNegative()||maximumDuration.compareTo(Duration.ofMinutes(10))>0
    ||maximumDependencies<1||maximumDependencies>10000)throw new IllegalArgumentException("Explicit bounded control duration and dependency capacity required");
  this.maximumDuration=maximumDuration;this.maximumDependencies=maximumDependencies;
 }
 public <T>T withReads(HttpServletRequest http,String tenant,String run,String command,Function<Reads,T> operation)throws Exception{
  return reads(http,tenant,run,command,null,operation);
 }
 <T>T withLifecycleReads(HttpServletRequest http,String tenant,String run,String command,TaskRunLifecycleAuthority.Operation kind,Function<Reads,T> operation)throws Exception{
  return reads(http,tenant,run,command,Objects.requireNonNull(kind),operation);
 }
 private <T>T reads(HttpServletRequest http,String tenant,String run,String command,TaskRunLifecycleAuthority.Operation kind,Function<Reads,T> operation)throws Exception{
  WireValues.uuid(tenant);WireValues.uuid(run);WireValues.uuid(command);Objects.requireNonNull(operation);
  if(TransactionSynchronizationManager.isActualTransactionActive())deny();
  var service=services.require(http);
  var initial=one(db.query("""
   SELECT r.created_by,r.replay_manifest,o.payload FROM task_run r JOIN outbox_event o ON o.tenant_id=r.tenant_id
     AND o.aggregate_id=r.run_id AND o.event_type='run.start.requested'
   WHERE r.tenant_id=? AND r.run_id=? AND r.execution_kind='TASK' AND r.status IN (%s)
     AND o.payload->>'$.command.command_id'=? LIMIT 2
   """.formatted(kind==null?"'QUEUED','RUNNING'":"'RUNNING','WAITING_INPUT','CANCEL_REQUESTED','SUCCEEDED','FAILED','CANCELLED'"),(rs,n)->new Initial(rs.getString(1),rs.getString(2),rs.getString(3)),tenant,run,command));
  var payload=schema.read("PrincipalManifest",json.canonical(parse(initial.replay()).required("principal_manifest")));
  var definition=parse(initial.replay()).required("execution_policy");String audience=definition.required("worker").required("audience").asText();
  schema.service(payload,service.serviceUri(),audience);
  if(!service.serviceUri().equals(definition.required("worker").required("service_uri").asText()))deny();
  var reads=new Reads(http,tenant,run,command,initial.actor(),audience,json.canonical(parse(initial.replay())),json.canonical(parse(initial.command())),service,kind);
  try{reads.execute(a->null);return operation.apply(reads);}finally{reads.active=false;}
 }
 private record Initial(String actor,String replay,String command){}
 public final class Reads {
  private final HttpServletRequest http;private final String tenant,run,command,actor,audience,replayJson,commandJson;
  private final TaskRunLifecycleAuthority.Operation kind;private final ServiceCertificateIdentity.Verified certificate;private final Thread thread=Thread.currentThread();
  private final long deadline=System.nanoTime()+maximumDuration.toNanos();private boolean active=true;private EnterpriseAccess.Admission currentAdmission;
  private Reads(HttpServletRequest http,String tenant,String run,String command,String actor,String audience,String replayJson,String commandJson,
    ServiceCertificateIdentity.Verified certificate,TaskRunLifecycleAuthority.Operation kind){this.http=http;this.tenant=tenant;this.run=run;this.command=command;this.actor=actor;this.audience=audience;
   this.replayJson=replayJson;this.commandJson=commandJson;this.certificate=certificate;this.kind=kind;}
  public void requireActive(){if(!active||thread!=Thread.currentThread()||System.nanoTime()>=deadline)deny();}
  public String actorId(){requireActive();return actor;}public String tenantId(){requireActive();return tenant;}
  public String contextRevision(){requireActive();return "0";}
  public JsonNode replay(){requireActive();return ThrowingSupplier.of(()->parse(replayJson)).get();}
  public void requireVersion(String id,String sha256){
   requireActive();WireValues.uuid(id);WireValues.hash(sha256);
   var matches=new ArrayList<JsonNode>();for(var item:replay().required("dependency_versions"))if(id.equals(item.required("version_id").asText()))matches.add(item);
   if(matches.size()!=1||!sha256.equals(matches.get(0).required("content_sha256").asText()))deny();
  }
  public String policyVersion(){return replay().required("execution_policy_version").required("version_id").asText();}
  public String policySha256(){return replay().required("execution_policy_version").required("content_sha256").asText();}
  public void requirePublication(String release,String purpose){requireActive();if(!"RUN_CREATION".equals(purpose)||!release.equals(replay().required("agent_release_id").asText()))deny();}
  public <T>T execute(Function<EnterpriseAccess.Admission,T> operation){
   requireActive();
   if(TransactionSynchronizationManager.isActualTransactionActive()) {
    if(currentAdmission==null || currentAdmission.controlProof().reads!=this)deny();
    currentAdmission.controlProof().requireActive();
    try{return operation.apply(currentAdmission);}finally{currentAdmission.controlProof().requireActive();}
   }
   var epochs=one(db.query("SELECT auth_epoch,governance_epoch FROM tenant WHERE tenant_id=? LIMIT 2",(rs,n)->new TenantWatermarks(
    rs.getBigDecimal(1).toBigIntegerExact(),rs.getBigDecimal(2).toBigIntegerExact()),tenant));
   try(var ignored=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ZERO,epochs.authEpoch(),epochs.governanceEpoch()))){
    return transactions.execute(current->ThrowingSupplier.of(()->withControl(control->{
     return ThrowingSupplier.of(()->{
      requireActive();requireOriginal(control);var proof=proof(control);
      var context=new BusinessSessionProtocol.Context(tenant,"0",current.authEpoch().toString(),current.governanceEpoch().toString());
      var admission=new EnterpriseAccess.Admission(proof,context,current);
      currentAdmission=admission;
      try{var result=operation.apply(admission);proof.requireActive();control.requireUnchanged();requireOriginal(control);requireActive();return result;}
      finally{currentAdmission=null;proof.active=false;}
     }).get();
    })).get());
   }
  }
  private <T>T withControl(Function<FormalWorkerControlAuthority.VerifiedControl,T> callback)throws Exception{
   return kind==null?controls.withRunControl(http,run,actor,audience,callback):controls.withLifecycleControl(http,run,actor,audience,kind,callback);
  }
  private void requireOriginal(FormalWorkerControlAuthority.VerifiedControl control)throws Exception{
   var fresh=services.require(http);if(!certificate.serviceUri().equals(fresh.serviceUri())||!certificate.certificateSha256().equals(fresh.certificateSha256())
     ||!certificate.trustRevision().equals(fresh.trustRevision()))deny();
   if(!control.runOnly()||!tenant.equals(control.tenantId())||!actor.equals(control.actorId())||!run.equals(control.runId())
     ||!policyVersion().equals(control.policyVersion())||!policySha256().equals(control.policySha256()))deny();
   var row=one(db.query("""
    SELECT r.replay_manifest,o.payload FROM task_run r JOIN outbox_event o ON o.tenant_id=r.tenant_id AND o.aggregate_id=r.run_id
      AND o.event_type='run.start.requested' WHERE r.tenant_id=? AND r.run_id=? AND r.created_by=?
      AND o.payload->>'$.command.command_id'=? LIMIT 2 FOR UPDATE
    """,(rs,n)->List.of(rs.getString(1),rs.getString(2)),tenant,run,actor,command));
   if(!replayJson.equals(json.canonical(parse(row.get(0))))||!commandJson.equals(json.canonical(parse(row.get(1)))))deny();
  }
  private Proof proof(FormalWorkerControlAuthority.VerifiedControl control)throws Exception{
   var input=replay();var dependencies=input.required("dependency_versions");if(!dependencies.isArray()||dependencies.isEmpty()||dependencies.size()>maximumDependencies)deny();
   var ids=new ArrayList<String>();for(var item:dependencies){String id=item.required("version_id").asText();if(ids.contains(id))deny();ids.add(id);}
   var domain=owners.ownersAcrossDomains(ids,Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());
   var grouped=domain.stream().collect(java.util.stream.Collectors.groupingBy(r->r.owner().versionId()));
   var scopes=new HashSet<PermissionProtocol.Scope>();
   var governance=new HashSet<RunTokenAuthoritySchema.Permission>();
   for(var item:dependencies){String id=item.required("version_id").asText();var version=one(registry.version(id));
    if(!id.equals(version.id())||!item.required("entity_kind").asText().equals(version.entityKind())
      ||!item.required("content_sha256").asText().equals(version.contentSha256())
      ||!Objects.equals(nullable(item.required("project_id")),version.projectId()))deny();
    if("ARTIFACT".equals(version.entityKind()))scopes.add(new PermissionProtocol.Scope(tenant,version.projectId(),"ARTIFACT",id));
    if("TASK_DELEGATION_RULE".equals(version.entityKind())){
     var rule=one(db.query("SELECT project_id,content_sha256 FROM task_delegation_rule WHERE tenant_id=? AND rule_id=? LIMIT 2 FOR UPDATE",
      (rs,n)->List.of(rs.getString(1),rs.getString(2)),tenant,id));
     if(!Objects.equals(version.projectId(),rule.get(0))||!control.projectId().equals(rule.get(0))||!version.contentSha256().equals(rule.get(1)))deny();
     governance.add(new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(tenant,rule.get(0),"PROJECT",rule.get(0)),"task.delegation.read","TASK_DELEGATION"));continue;
    }
    if("RUNTIME_RETENTION_POLICY".equals(version.entityKind())){
     if(!id.equals(input.required("retention").required("policy_revision").asText()))deny();
     scopes.add(RuntimeRetentionAuthority.scope(tenant,version.projectId()));continue;
    }
    if(Set.of("AGENT_PUBLICATION_RECORD","EXPERT_QUALIFICATION","EXPERT_EVIDENCE_RECORD").contains(version.entityKind())){
     String table="AGENT_PUBLICATION_RECORD".equals(version.entityKind())?"agent_publication_record":"expert_qualification_record";
     var original=one(db.query("SELECT project_id,kind,content_sha256 FROM "+table+" WHERE tenant_id=? AND record_id=? LIMIT 2 FOR UPDATE",
      (rs,n)->Arrays.asList(rs.getString(1),rs.getString(2),rs.getString(3)),tenant,id));
     var kinds=switch(version.entityKind()){case "AGENT_PUBLICATION_RECORD"->Set.of("ACCEPTANCE","POLICY","POLICY_DRAFT");case "EXPERT_QUALIFICATION"->Set.of("AUTHOR");default->Set.of("APPROVE","SCORE_BIND");};
     if(!Objects.equals(version.projectId(),original.get(0))||!kinds.contains(original.get(1))||!version.contentSha256().equals(original.get(2)))deny();
     var scope=RuntimeRetentionAuthority.scope(tenant,original.get(0));
     String purpose=Set.of("POLICY","POLICY_DRAFT").contains(original.get(1))?"EVALUATION_RESULTS":"AGENT_PUBLICATION";
     governance.add(new RunTokenAuthoritySchema.Permission(scope,"configuration.read",purpose));continue;
    }
    var owner=one(grouped.getOrDefault(id,List.of())).owner();
    if(!tenant.equals(owner.tenantId())||owner.conversationId()!=null||owner.resourceProjectId()!=null&&!control.projectId().equals(owner.resourceProjectId())
      ||owner.contentSha256()!=null&&!version.contentSha256().equals(owner.contentSha256()))deny();
    scopes.add(new PermissionProtocol.Scope(tenant,owner.resourceProjectId(),owner.resourceKind(),owner.resourceId()));
   }
   var binding=one(permissions.currentBinding(actor));if(!"ACTIVE".equals(binding.status())||!actor.equals(binding.userId()))deny();
   return new Proof(this,control,Set.copyOf(scopes),Set.copyOf(governance),new AuthenticatedIdentityBinding.View(binding.mode(),binding.userId(),binding.issuer(),binding.subject()));
  }
 }
 static final class Proof {
  private final Reads reads;private final FormalWorkerControlAuthority.VerifiedControl control;private final Set<PermissionProtocol.Scope> scopes;private final Set<RunTokenAuthoritySchema.Permission> governance;
  private final AuthenticatedIdentityBinding.View binding;private boolean active=true;
  private Proof(Reads reads,FormalWorkerControlAuthority.VerifiedControl control,Set<PermissionProtocol.Scope> scopes,Set<RunTokenAuthoritySchema.Permission> governance,AuthenticatedIdentityBinding.View binding){
   this.reads=reads;this.control=control;this.scopes=scopes;this.governance=governance;this.binding=binding;}
  void requireActive(){if(!active)deny();reads.requireActive();control.requireUnchanged();}
  void requireVersion(String id,String sha256){requireActive();reads.requireVersion(id,sha256);}
  void acceptedStart(TaskRunAcceptedStart.Transition transition)throws Exception{
   if(!active)deny();reads.requireActive();control.acceptedStart(this,Objects.requireNonNull(transition));requireActive();
  }
  void lifecycleTransition(TaskRunLifecycleAuthority.Transition transition)throws Exception{
   if(!active||reads.kind==null)deny();reads.requireActive();control.lifecycleTransition(this,Objects.requireNonNull(transition));requireActive();
  }
  StartRun startRun(){requireActive();return new StartRun(this);}
  void requireBootstrap(String task,String run){requireActive();if(!reads.run.equals(run)||!reads.replay().required("task_id").asText().equals(task))deny();}
  void require(PermissionProtocol.Scope scope,String action,String purpose){requireActive();if(action==null||!action.endsWith(".read")||!(scopes.contains(scope)||governance.contains(new RunTokenAuthoritySchema.Permission(scope,action,purpose))
      ||"project.read".equals(action)&&scope.equals(new PermissionProtocol.Scope(reads.tenant,control.projectId(),"PROJECT",control.projectId())))
    ||purpose==null||purpose.equals("SIMULATION_EVALUATION"))deny();}
  String actorId(){requireActive();return reads.actorId();}AuthenticatedIdentityBinding.View bindingView(){requireActive();return binding;}
  Object identity(){requireActive();return new ReadIdentity("FORMAL_RUN_CONTROL_READ",reads.tenant,reads.run,reads.command,reads.actor,
    reads.certificate.serviceUri(),reads.certificate.trustRevision(),reads.certificate.certificateSha256(),
    org.apache.commons.codec.digest.DigestUtils.sha256Hex(reads.replayJson),org.apache.commons.codec.digest.DigestUtils.sha256Hex(reads.commandJson));}
 }
 /** Separate exact START_RUN capability; it is not a general permission or human session. */
 static final class StartRun {
  private final Proof proof;private StartRun(Proof proof){this.proof=proof;}
  void requireRelease(EnterpriseAccess.Admission admission,String release){proof.requireActive();if(admission.controlProof()!=proof)deny();proof.reads.requirePublication(release,"RUN_CREATION");}
  void require(EnterpriseAccess.Admission admission,PermissionProtocol.Scope scope,String purpose){
   proof.requireActive();if(!admission.controlWorker()||admission.controlProof()!=proof||!"RUN_CREATION".equals(purpose))deny();
   var replay=proof.reads.replay();if(!proof.control.projectId().equals(replay.required("project_id").asText()))deny();
   if(!scope.equals(new PermissionProtocol.Scope(proof.reads.tenant,replay.required("project_id").asText(),"TASK",replay.required("task_id").asText())))deny();
   proof.requireBootstrap(scope.resource_id(),proof.reads.run);
  }
 }
 public record ReadIdentity(String kind,String tenantId,String runId,String commandId,String actorId,String serviceUri,
   String trustRevision,String certificateSha256,String replaySha256,String commandPayloadSha256) { }
 private JsonNode parse(String value)throws Exception{return com.fasterxml.jackson.databind.json.JsonMapper.builder()
  .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(value);}
 private static String nullable(JsonNode value){return value.isNull()?null:value.asText();}
 private static <T>T one(List<T> rows){if(rows.size()!=1)deny();return rows.get(0);}
 private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
