package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.ProjectMapper;
import java.util.*;
import java.io.IOException;

/** Independent EVALUATION entry into the shared real source-selection/snapshot store. No Task is fabricated. */
public final class FormalEvaluationExecutionScopes {
 private final ScopeSnapshotStore snapshots;private final ProjectMapper projects;private final DependencyLineage.Limits limits;
 public FormalEvaluationExecutionScopes(ScopeSnapshotStore snapshots,ProjectMapper projects,DependencyLineage.Limits limits){this.snapshots=snapshots;this.projects=projects;this.limits=limits;}
 Prepared save(EnterpriseAccess.Admission a,FormalEvaluationExecutionInputs.Prepared inputs)throws IOException {
  var current=inputs.requireCurrent(a);var policy=current.policy();
  if(!"FIXED_SOURCE_VERSIONS".equals(policy.sourceSelection().mode())||policy.networkPolicy().externalRequestsAllowed())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
  var sources=policy.sourceSelection().sources();var scope=new CommonProtocol.SourceScope(CommonProtocol.ScopeMode.SOURCE_VERSIONS,List.of(),sources.stream().map(PermissionProtocol.Basis::version_id).sorted().toList(),false,null,null);
  // Every original input SOURCE must be in the independently approved selection. Expected answers are never sent as prompt content.
  for(var sample:current.definition().request().samples()){var source=inputs.receipt(a,sample.sample_id()).required("source");var basis=new PermissionProtocol.Basis(source.required("version_id").asText(),source.required("content_sha256").asText());if(!sources.contains(basis))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
  var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("projectId",current.projectId());var rows=projects.project(values);if(rows.size()!=1)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
  var project=rows.get(0);if(!a.context().tenant_id().equals(project.tenantId())||!current.projectId().equals(project.projectId())||!"ACTIVE".equals(project.status()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  var saved=snapshots.saveForEvaluation(a,current.projectId(),project.profileRevision(),scope,current.request().evaluationId(),policy.businessPurpose(),policy.environment(),policy.region(),limits);
  current.requireUnchanged();inputs.requireCurrent(a);var prepared=new Prepared(this,inputs,current.projectId(),project.profileRevision(),scope,saved);prepared.requireCurrent(a);return prepared;
 }
 Prepared read(EnterpriseAccess.Admission a,FormalEvaluationExecutionInputs.Prepared inputs,PermissionProtocol.Basis snapshot,String profile)throws IOException {
  var current=inputs.requireCurrent(a);var policy=current.policy();if(!"FIXED_SOURCE_VERSIONS".equals(policy.sourceSelection().mode())||policy.networkPolicy().externalRequestsAllowed())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
  var selected=new CommonProtocol.SourceScope(CommonProtocol.ScopeMode.SOURCE_VERSIONS,List.of(),policy.sourceSelection().sources().stream().map(PermissionProtocol.Basis::version_id).sorted().toList(),false,null,null);
  var stored=snapshots.readForEvaluation(a,current.projectId(),profile,selected,current.request().evaluationId(),policy.businessPurpose(),policy.environment(),policy.region(),limits,snapshot);
  var proof=new Prepared(this,inputs,current.projectId(),profile,selected,stored);proof.requireCurrent(a);return proof;
 }
 public static final class Prepared {
  private final FormalEvaluationExecutionScopes owner;private final FormalEvaluationExecutionInputs.Prepared inputs;
  private final String project,profile;private final CommonProtocol.SourceScope scope;private final ScopeSnapshotStore.Stored original;
  private Prepared(FormalEvaluationExecutionScopes owner,FormalEvaluationExecutionInputs.Prepared inputs,String project,String profile,CommonProtocol.SourceScope scope,ScopeSnapshotStore.Stored original){this.owner=owner;this.inputs=inputs;this.project=project;this.profile=profile;this.scope=scope;this.original=original;}
  public PermissionProtocol.Basis snapshot(){return new PermissionProtocol.Basis(original.snapshot().snapshotId(),original.snapshot().manifestHash());}
  public String profileRevision(){return profile;}public TaskProtocol.ScopeSnapshotManifest manifest(){return original.manifest();}
  public List<String> dependencyVersions(){return original.dependencyVersions();}
  public void requireCurrent(EnterpriseAccess.Admission a)throws IOException{var current=inputs.requireCurrent(a);var policy=current.policy();var fresh=owner.snapshots.readForEvaluation(a,project,profile,scope,current.request().evaluationId(),policy.businessPurpose(),policy.environment(),policy.region(),owner.limits,snapshot());if(!original.equals(fresh))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 }
}
