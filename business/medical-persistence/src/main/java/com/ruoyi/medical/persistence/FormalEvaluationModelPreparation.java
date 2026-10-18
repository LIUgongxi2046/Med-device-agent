package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.io.IOException;

/** Independent evaluation model preparation. This private proof is not a worker lease or dispatch permit.
 * Real model eligibility, role narrowing, source MODEL_SEND and evaluation.run are checked independently.
 * No MODEL_VERIFICATION job or model.verify permission is used as evaluation identity. */
public final class FormalEvaluationModelPreparation {
 private final ModelEligibilityAuthority eligibility;private final ModelConfigurationService models;
 private final FormalCapabilityIdentityAuthority capabilities;private final SourceReadAuthority sources;
 private final ResourceReadAuthority resources;private final RunTokenAuthoritySchema runtime;private final PermissionSchema json;
 public FormalEvaluationModelPreparation(ModelEligibilityAuthority eligibility,ModelConfigurationService models,
   FormalCapabilityIdentityAuthority capabilities,SourceReadAuthority sources,ResourceReadAuthority resources,RunTokenAuthoritySchema runtime,PermissionSchema json){this.eligibility=eligibility;this.models=models;this.capabilities=capabilities;this.sources=sources;this.resources=resources;this.runtime=runtime;this.json=json;}
 Prepared require(EnterpriseAccess.Admission a,FormalEvaluationExecutionInputs.Prepared inputs,
   FormalEvaluationExecutionScopes.Prepared snapshot,FormalEvaluationExecutionProtocol.Node node)throws IOException{
  FormalEvaluationExecutionAdmission.current(a);var execution=inputs.requireCurrent(a);snapshot.requireCurrent(a);
  if(node==null||!execution.nodes().contains(node)||node.model()==null||node.tokenizer()==null)denied();
  var binding=execution.policy().bindings().stream().filter(n->n.nodeKind().equals(node.nodeKind())).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
  if(!node.model().equals(binding.modelVersion())||!node.role().equals(binding.roleVersion())||!node.tokenizer().equals(binding.tokenizerVersion()))bad();
  var model=eligibility.require(a,node.model().version_id(),"RUN_CREATION",execution.projectId());
  if(!node.model().content_sha256().equals(model.contentSha256()))bad();
  var fixed=models.requireCurrentExternal(a,node.model().version_id(),"RUN_CREATION",execution.projectId());
  var definition=(ModelConfigurationProtocol.Definition)fixed.definition();
  if(!execution.policy().region().equals(definition.region()))throw new TenantAccessException(ErrorCode.MODEL_POLICY_INCOMPLETE);
  model.requireDataUse(execution.classification(),definition.region(),execution.policy().trainingAllowed());model.requireParameters(binding.generationParameters());
  var modelScope=new PermissionProtocol.Scope(a.context().tenant_id(),fixed.project_id(),"CONFIGURATION",fixed.config_id());
  var modelGrant=resources.require(a,modelScope,"model.send","EVALUATION_RESULTS");
  var role=capabilities.require(a,node.role(),"ROLE",execution.projectId(),"EVALUATION_RESULTS");
  var roleScope=runtime.read("RoleScope",json.canonical(role.canonicalDefinition().required("request").required("tool_scope")));
  var required=new ArrayList<RunTokenAuthoritySchema.Permission>();required.add(new RunTokenAuthoritySchema.Permission(modelScope,"model.send","EVALUATION_RESULTS"));
  var sourceProofs=json.object();var selected=new HashSet<>(execution.policy().sourceSelection().sources().stream().map(PermissionProtocol.Basis::version_id).toList());
  // Full approved source selection, not an arbitrary single source or EVALUATION_RESULTS purpose conversion.
  for(var member:snapshot.manifest().members()){
   if(!selected.remove(member.source_version()))bad();
   var requirement=new SourceReadProtocol.ReadRequirement(member.source_version(),member.rights_revision(),execution.projectId(),snapshot.snapshot().version_id(),member.parse_version(),null,SourceReadProtocol.Use.MODEL_SEND,execution.policy().businessPurpose(),execution.policy().environment(),definition.region(),definition.provider());
   var checked=sources.require(a,requirement);model.requireDataUse(checked.source().classification(),definition.region(),execution.policy().trainingAllowed());
   sourceProofs.set(member.source_version(),AgentPublicationWitness.tree(checked));
   var scope=new PermissionProtocol.Scope(a.context().tenant_id(),checked.source().ownerProjectId(),"SOURCE",checked.source().sourceId());
   required.add(new RunTokenAuthoritySchema.Permission(scope,"source.read",execution.policy().businessPurpose()));
   required.add(new RunTokenAuthoritySchema.Permission(scope,"source.external.process",execution.policy().businessPurpose()));
  }
  if(!selected.isEmpty())bad();
  required.add(new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(a.context().tenant_id(),execution.projectId(),"PROJECT",execution.projectId()),"project.read",execution.policy().businessPurpose()));
  var unique=List.copyOf(new LinkedHashSet<>(required));
  for(var permission:unique){boolean contained=false;for(var allowed:roleScope.required("permissions"))if(json.tree(permission).equals(allowed)){contained=true;break;}if(!contained)denied();}
  var proof=json.object();proof.set("node",json.tree(node));proof.set("model",json.tree(model.safeReplayBinding()));proof.set("model_send",AgentPublicationWitness.tree(modelGrant));proof.set("sources",sourceProofs);proof.set("role",role.authorizations());proof.set("permissions",json.tree(unique));proof.set("snapshot",json.tree(snapshot.snapshot()));proof.set("parameters",binding.generationParameters());
  model.requireUnchanged();role.requireUnchanged();snapshot.requireCurrent(a);inputs.requireCurrent(a);
  return new Prepared(this,a,inputs,snapshot,node,model.safeReplayBinding(),unique,json.canonical(proof));
 }
 public static final class Prepared {
  private final FormalEvaluationModelPreparation owner;private final EnterpriseAccess.Admission admission;private final FormalEvaluationExecutionInputs.Prepared inputs;private final FormalEvaluationExecutionScopes.Prepared scope;
  private final FormalEvaluationExecutionProtocol.Node node;private final ModelEligibilityBinding model;private final List<RunTokenAuthoritySchema.Permission> permissions;private final String canonical;
  private Prepared(FormalEvaluationModelPreparation owner,EnterpriseAccess.Admission admission,FormalEvaluationExecutionInputs.Prepared inputs,FormalEvaluationExecutionScopes.Prepared scope,FormalEvaluationExecutionProtocol.Node node,ModelEligibilityBinding model,List<RunTokenAuthoritySchema.Permission> permissions,String canonical){this.owner=owner;this.admission=admission;this.inputs=inputs;this.scope=scope;this.node=node;this.model=model;this.permissions=List.copyOf(permissions);this.canonical=canonical;}
  public FormalEvaluationExecutionProtocol.Node node(){return node;}public ModelEligibilityBinding model(){return model;}
  List<RunTokenAuthoritySchema.Permission> permissions(){return permissions;}String canonical(){return canonical;}
  public void requireCurrent(EnterpriseAccess.Admission a)throws IOException{if(a!=admission)denied();if(!canonical.equals(owner.require(a,inputs,scope,node).canonical))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 }
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
