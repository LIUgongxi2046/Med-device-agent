package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import java.io.IOException;
import java.util.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Current physical086/approved078 consumer. A QUEUED admission is not execution success. */
public final class PublishedRunCreateAdmission implements RunCreateAdmission {
 private static final String PURPOSE="RUN_CREATION";
 private final ObjectProvider<AgentPublicationAuthority> publications;
 private final ObjectProvider<RuntimeRetentionAuthority> retentions;
 private final AgentExecutionPolicyAuthority policies;
 private final AgentReleaseScopeAuthority scopes;
 private final FormalTaskPermissionAuthority permissions;
 private final RuntimeReplayAssembler replay;
 private final TemplateDependencyPolicy limits;
 private final IdentityDirectoryPolicy identity;
 private final ObjectProvider<TaskCandidateAssemblyLaunchInputs> assemblyInputs;
 private TaskSignedContinuationInputs signedInputs;
 void signedInputs(TaskSignedContinuationInputs value){signedInputs=Objects.requireNonNull(value);}
 public PublishedRunCreateAdmission(ObjectProvider<AgentPublicationAuthority> publications,ObjectProvider<RuntimeRetentionAuthority> retentions,
   AgentExecutionPolicyAuthority policies,AgentReleaseScopeAuthority scopes,FormalTaskPermissionAuthority permissions,
   RuntimeReplayAssembler replay,TemplateDependencyPolicy limits,IdentityDirectoryPolicy identity){
  this(publications,retentions,policies,scopes,permissions,replay,limits,identity,null);
 }
 public PublishedRunCreateAdmission(ObjectProvider<AgentPublicationAuthority> publications,ObjectProvider<RuntimeRetentionAuthority> retentions,
   AgentExecutionPolicyAuthority policies,AgentReleaseScopeAuthority scopes,FormalTaskPermissionAuthority permissions,
   RuntimeReplayAssembler replay,TemplateDependencyPolicy limits,IdentityDirectoryPolicy identity,
   ObjectProvider<TaskCandidateAssemblyLaunchInputs> assemblyInputs){
  this.assemblyInputs=assemblyInputs;
  this.publications=publications;this.retentions=retentions;this.policies=policies;this.scopes=scopes;
  this.permissions=permissions;this.replay=replay;this.limits=limits;this.identity=identity;
 }
 @Override public RunCreateAdmission prepare(ApplicationSessionIdentity.Verified subject,String context,TaskService.VerifiedTask task,RunCreateSchema.Create request)throws IOException {
  return prepareBound(subject,context,task,request,null);
 }
 @Override public RunCreateAdmission prepareContinuation(ApplicationSessionIdentity.Verified subject,String context,TaskService.VerifiedTask task,RunCreateSchema.Create request,RunContinuationService.Authority continuation)throws IOException {
  continuation.requirePrepared(task,request);return prepareBound(subject,context,task,request,continuation);
 }
 private RunCreateAdmission prepareBound(ApplicationSessionIdentity.Verified subject,String context,TaskService.VerifiedTask task,RunCreateSchema.Create request,RunContinuationService.Authority continuation)throws IOException {
  if(TransactionSynchronizationManager.isActualTransactionActive())fail(ErrorCode.ACCESS_DENIED);
  var authority=publications.getIfAvailable();if(authority==null)fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
  if(continuation==null)RunCreateSchema.requireReady(task.head(),request);else continuation.requirePrepared(task,request);
  try{
   // Even NOT_REQUIRED is established from the actual fixed template, never guessed
   // from an absent runtime component. Legacy construction therefore stays fail-closed.
   var inputs=assemblyInputs==null?null:assemblyInputs.getIfAvailable();
   if(inputs==null)fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
   var fixedInputs=continuation==null?inputs.prepare(subject,context,task,request):inputs.prepareContinuation(subject,context,task,request,continuation);
   var signed=signedInputs==null?null:signedInputs.prepare(subject,context,task);
   if(task.input().basedOnDocumentRevision()!=null&&signed==null)fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
   var original=authority.prepare(subject,context,task.head().agentReleaseId(),PURPOSE,false);
   return (a,actual,plan,scope,r,run,principal)->{
    if(!task.head().equals(actual.head())||!task.input().equals(actual.input())||!request.equals(r))fail(ErrorCode.PLAN_STALE);
    if(continuation!=null)continuation.requireCurrent(a,actual,r);
    return prepared(authority,original,inputs,fixedInputs,signed,a,actual,plan,scope,r,run,principal);
   };
  }catch(RuntimeException e){throw e;}catch(Exception e){throw new IOException("RUN_PUBLICATION_PREPARATION_FAILED",e);}
 }
 @Override public Verified require(EnterpriseAccess.Admission a,TaskService.VerifiedTask task,TaskPlanWriteProtocol.View plan,
   AgentReleaseScopeAuthority.VerifiedScope scope,RunCreateSchema.Create request,String run,String principal){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
 private Verified prepared(AgentPublicationAuthority authority,AgentPublicationAuthority.PreparedPublication original,TaskCandidateAssemblyLaunchInputs inputs,TaskCandidateAssemblyLaunchInputs.Prepared fixedInputs,TaskSignedContinuationInputs.Prepared signed,EnterpriseAccess.Admission a,
   TaskService.VerifiedTask task,TaskPlanWriteProtocol.View plan,AgentReleaseScopeAuthority.VerifiedScope scope,
   RunCreateSchema.Create request,String run,String principal)throws IOException{
  try{
   var head=task.head();
   if(a.simulationWorker()||scope.release().admission()!=a||!PURPOSE.equals(scope.release().purpose())||!"task.run".equals(scope.action())
     ||!head.taskId().equals(scope.target().resource_id())||!head.projectId().equals(scope.target().project_id())||!"TASK".equals(scope.target().resource_kind()))fail(ErrorCode.ACCESS_DENIED);
   var publication=authority.require(a,head.agentReleaseId(),PURPOSE,original);
   if(!publication.manifest().equals(scope.release().content()))fail(ErrorCode.CONTENT_MISMATCH);
   var policy=policies.requireByVersion(a,publication.executionPolicyId(),publication.executionPolicySha256(),head.projectId(),identity.classification(),PURPOSE);
   AgentExecutionPolicyAuthority.requireManifest(policy.definition(),publication.manifest().manifest());
   if(!limits.environment().equals(policy.definition().environment())||!limits.region().equals(policy.definition().region())||"SIMULATION".equals(policy.definition().environment()))fail(ErrorCode.ACCESS_DENIED);
   var retentionAuthority=retentions.getIfAvailable();if(retentionAuthority==null)fail(ErrorCode.RETENTION_UNCONFIGURED);
   var retention=retentionAuthority.requireForRun(a,head.projectId(),identity.classification(),PURPOSE);
   var permissionProof=permissions.require(a,task,publication,policy,run,principal,request.budget_revision());
   var crossFileInputs=inputs.require(a,task,publication,policy,fixedInputs);
   var signedProof=signedInputs==null?null:signedInputs.require(a,task,signed,permissionProof.principalManifest());
   var witnesses=mergeAuthorizations(permissionProof.delegationAuthorizations(),crossFileInputs.authorizations());
   var versions=mergeVersions(permissionProof.delegationVersions(),crossFileInputs.dependencyVersions());
   if(signedProof!=null){((ObjectNode)witnesses).set("signed_document_context",signedProof.witness());versions=mergeVersions(versions,signedProof.dependencies());}
   var assembled=replay.assembleSigned(a,task,plan,publication,policy,retention,request,run,principal,permissionProof.scopeManifest(),permissionProof.principalManifest(),witnesses,versions,signedProof);
   return new Verified(){
    public SchemaCatalogue.ValidatedPayload replayManifest(){return assembled.replayManifest();}
    public JsonNode scopeManifest(){return permissionProof.scopeManifest();}
    public JsonNode principalManifest(){return permissionProof.principalManifest();}
    public String retentionPolicyRevision(){return retention.policyRevision();}
    public void requireUnchanged()throws IOException{try{
     publication.requireUnchanged();policy.requireUnchanged();scopes.requireUnchanged(scope);retention.requireUnchanged();permissionProof.requireUnchanged();crossFileInputs.requireUnchanged();if(signedProof!=null)signedProof.require(a,task);assembled.requireUnchanged();
    }catch(RuntimeException e){throw e;}catch(Exception e){throw new IOException("RUN_PUBLICATION_RECHECK_FAILED",e);}}
   };
  }catch(RuntimeException e){throw e;}catch(IOException e){throw e;}catch(Exception e){throw new IOException("RUN_PUBLICATION_ADMISSION_FAILED",e);}
 }
 static JsonNode mergeAuthorizations(JsonNode existing,JsonNode crossFile){
  if(existing==null||!existing.isObject()||crossFile==null||!crossFile.isObject()
    ||existing.has("candidate_assembly_cross_file_inputs"))fail(ErrorCode.CONTENT_MISMATCH);
  ObjectNode result=existing.deepCopy();result.set("candidate_assembly_cross_file_inputs",crossFile.deepCopy());return result;
 }
 static List<PermissionProtocol.Basis> mergeVersions(List<PermissionProtocol.Basis> existing,List<PermissionProtocol.Basis> crossFile){
  var result=new LinkedHashMap<String,PermissionProtocol.Basis>();
  for(var list:List.of(existing,crossFile))for(var version:list){
   var old=result.putIfAbsent(version.version_id(),version);
   if(old!=null&&!old.equals(version))fail(ErrorCode.CONTENT_MISMATCH);
  }
  return List.copyOf(result.values());
 }
 private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
