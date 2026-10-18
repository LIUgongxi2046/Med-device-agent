package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runtime-only governance owner checks. No SOURCE rights or content admission is
 * inferred from a version registry entry. Call alongside the complete content guard. */
public final class RuntimeGovernanceCurrentAuthority {
 private final JdbcTemplate jdbc;private final VersionResourceMapper owners;private final ArtifactRegistryMapper registry;private final SourceReadMapper lineage;private final ResourceReadAuthority resources;private final PermissionSchema json;
 public RuntimeGovernanceCurrentAuthority(DataSource data,VersionResourceMapper owners,ArtifactRegistryMapper registry,SourceReadMapper lineage,ResourceReadAuthority resources,PermissionSchema json){this(new JdbcTemplate(data),owners,registry,lineage,resources,json);}
 RuntimeGovernanceCurrentAuthority(JdbcTemplate jdbc,VersionResourceMapper owners,ArtifactRegistryMapper registry,SourceReadMapper lineage,ResourceReadAuthority resources,PermissionSchema json){this.jdbc=jdbc;this.owners=owners;this.registry=registry;this.lineage=lineage;this.resources=resources;this.json=json;}
 Verified require(EnterpriseAccess.Admission a,RuntimeReplaySchema.VerifiedInputs input,AgentExecutionPolicyRegisteredAuthority.RegisteredPolicyIdentity policy,RuntimeRetentionAuthority.Verified retention)throws Exception {
  var i=a.formalProof().identity();var value=input.content();policy.requireUnchanged();retention.requireUnchanged();
  if(!i.run().equals(input.runId())||!policy.versionId().equals(value.required("execution_policy_version").required("version_id").asText())||!policy.contentSha256().equals(value.required("execution_policy_version").required("content_sha256").asText())
    ||!retention.policyRevision().equals(value.required("retention").required("policy_revision").asText())||!retention.contentSha256().equals(value.required("retention").required("content_sha256").asText()))bad();
  var nodes=jdbc.query("""
   SELECT n.node_kind,n.role_version,n.role_sha256 FROM runtime_node_definition n JOIN run_step s
   ON s.tenant_id=n.tenant_id AND s.project_id=n.project_id AND s.run_id=n.run_id AND s.step_id=n.step_id AND s.role_version=n.role_version
   WHERE n.tenant_id=? AND n.project_id=? AND n.run_id=? AND n.step_id=? AND n.policy_version=? AND n.policy_sha256=? AND n.created_by=? FOR UPDATE
   """,(r,n)->new Node(r.getString(1),r.getString(2),r.getString(3)),i.tenant(),i.project(),i.run(),i.step(),policy.versionId(),policy.contentSha256(),i.actor());
  var node=one(nodes);var approved=policy.requireNode(node.kind(),new PermissionProtocol.Basis(node.role(),node.sha()),i.workerService(),policy.worker().audience());
  var all=value.required("dependency_versions");var ids=new ArrayList<String>();for(var v:all)ids.add(v.required("version_id").asText());
  if(ids.isEmpty()||ids.size()>4096||new HashSet<>(ids).size()!=ids.size())bad();
  var domainRows=owners.ownersAcrossDomains(ids,Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());
  var byId=domainRows.stream().collect(java.util.stream.Collectors.groupingBy(r->r.owner().versionId()));
  var actualEdges=new HashSet<SourceReadProtocol.ParentRow>();
  for(var v:all){String id=v.required("version_id").asText(),kind=v.required("entity_kind").asText(),sha=v.required("content_sha256").asText(),project=nullable(v.required("project_id"));
   var registered=one(registry.version(id));if(!id.equals(registered.id())||!kind.equals(registered.entityKind())||!sha.equals(registered.contentSha256())||!Objects.equals(project,registered.projectId()))bad();
   PermissionProtocol.Scope scope;String action;
   if(id.equals(policy.versionId())){if(!"AGENT_EXECUTION_POLICY".equals(kind)||!policy.ownsVersion(id,sha))bad();scope=policy.ownerScope();var actual=one(byId.getOrDefault(id,List.of()));var owner=actual.owner();if(!"AGENT_EXECUTION_POLICY".equals(actual.domain())||!i.tenant().equals(owner.tenantId())||!id.equals(owner.versionId())||!Objects.equals(project,owner.versionProjectId())||!Objects.equals(project,scope.project_id())||!Objects.equals(scope.project_id(),owner.resourceProjectId())||!scope.resource_kind().equals(owner.resourceKind())||!scope.resource_id().equals(owner.resourceId())||!sha.equals(owner.contentSha256())||owner.sourceVersion()!=null||owner.conversationId()!=null||owner.conversationOwnerId()!=null||owner.conversationStatus()!=null)bad();action="configuration.read";}
   else if(id.equals(retention.policyRevision())){if(!byId.getOrDefault(id,List.of()).isEmpty()||!"RUNTIME_RETENTION_POLICY".equals(kind))bad();scope=retention.ownerScope();if(!Objects.equals(project,scope.project_id()))bad();action="retention.read";}
   else {var actual=one(byId.getOrDefault(id,List.of()));var owner=actual.owner();if(!i.tenant().equals(owner.tenantId())||!Objects.equals(project,owner.versionProjectId())||owner.contentSha256()!=null&&!sha.equals(owner.contentSha256())||owner.conversationId()!=null)bad();
    scope=new PermissionProtocol.Scope(owner.tenantId(),owner.resourceProjectId(),owner.resourceKind(),owner.resourceId());action=action(actual.domain(),owner.resourceKind());}
   if(!i.tenant().equals(scope.tenant_id())||scope.project_id()!=null&&!i.project().equals(scope.project_id()))bad();
   var matches=approved.actions().stream().filter(b->b.resourceKind().equals(scope.resource_kind())&&b.action().equals(action)&&matches(b,id,sha,scope,value,policy)).toList();if(matches.isEmpty())deny();
   for(var binding:matches)resources.require(a,scope,action,binding.purpose());
   for(var edge:lineage.parents(id)){if(!id.equals(edge.consumerVersionId())||!ids.contains(edge.upstreamVersionId())||!actualEdges.add(edge))bad();}
  }
  var expectedEdges=new HashSet<SourceReadProtocol.ParentRow>();for(var e:value.required("dependency_edges"))if(!expectedEdges.add(new SourceReadProtocol.ParentRow(e.required("consumer_version_id").asText(),e.required("upstream_version_id").asText(),e.required("relation_kind").asText())))bad();
  if(!expectedEdges.equals(actualEdges))bad();policy.requireUnchanged();retention.requireUnchanged();return new Verified(this,a,input,policy,retention,json.canonical(value));
 }
 private static boolean matches(AgentExecutionPolicyProtocol.ActionBinding b,String id,String sha,PermissionProtocol.Scope scope,JsonNode value,AgentExecutionPolicyRegisteredAuthority.RegisteredPolicyIdentity policy){return switch(b.targetSelector()){
  case "FIXED_VERSION"->b.fixedVersion()!=null&&id.equals(b.fixedVersion().version_id())&&sha.equals(b.fixedVersion().content_sha256());
  case "TASK"->"TASK".equals(scope.resource_kind())&&value.required("task_id").asText().equals(scope.resource_id());
  case "PROJECT"->"PROJECT".equals(scope.resource_kind())&&value.required("project_id").asText().equals(scope.resource_id());
  case "CURRENT_EXECUTION_POLICY"->policy.ownsVersion(id,sha)&&scope.equals(policy.ownerScope())&&"configuration.read".equals(b.action())&&"TASK_EXECUTION".equals(b.purpose());
  case "SELECTED_SOURCE"->"SOURCE".equals(scope.resource_kind())&&java.util.stream.StreamSupport.stream(value.required("scope_snapshot").required("members").spliterator(),false).anyMatch(member->scope.resource_id().equals(member.required("source_id").asText()));
  default->false;
 };}
 private static String action(String domain,String kind){return switch(domain){case "COLLECTION_VERSION"->"collection.read";case "SHARED_FACT"->"fact.read";case "DEPENDENCY_ACCESS_SET"->"dependency.read";case "CONTEXT_MANIFEST"->"context.read";default->switch(kind){case "SOURCE"->"source.read";case "PROJECT"->"project.read";case "TASK"->"task.read";case "CONFIGURATION"->"configuration.read";case "TEMPLATE"->"template.read";case "RULE"->"rule.read";case "BUDGET"->"budget.read";default->throw new TenantAccessException(ErrorCode.ACCESS_DENIED);};};}
 public static final class Verified {private final RuntimeGovernanceCurrentAuthority owner;private final EnterpriseAccess.Admission admission;private final RuntimeReplaySchema.VerifiedInputs input;private final AgentExecutionPolicyRegisteredAuthority.RegisteredPolicyIdentity policy;private final RuntimeRetentionAuthority.Verified retention;private final String canonical;
  private Verified(RuntimeGovernanceCurrentAuthority owner,EnterpriseAccess.Admission admission,RuntimeReplaySchema.VerifiedInputs input,AgentExecutionPolicyRegisteredAuthority.RegisteredPolicyIdentity policy,RuntimeRetentionAuthority.Verified retention,String canonical){this.owner=owner;this.admission=admission;this.input=input;this.policy=policy;this.retention=retention;this.canonical=canonical;}
  void requireUnchanged()throws Exception{if(!canonical.equals(owner.require(admission,input,policy,retention).canonical))bad();}RuntimeReplaySchema.VerifiedInputs input(){admission.formalProof().requireActive();return input;}
 }
 record Node(String kind,String role,String sha){}private static String nullable(JsonNode n){return n.isNull()?null:n.asText();}private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
