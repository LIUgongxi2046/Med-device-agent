package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Component;

/** Author real metadata against an already genuinely registered immutable package artifact. */
@Component public final class FormalRuntimeCapabilityAuthoringService {
 private final AgentPublicationMapper mapper;private final AgentPublicationJournal journal;private final FormalRuntimeCapabilitySchema schema;private final PermissionSchema json;
 private final AgentPublicationDependencies dependencies;private final ResourceReadAuthority resources;private final EvaluationReferences references;
 private final FormalRuntimeCapabilityIdentityAuthority identities;private final IdentityDirectoryPolicy settings;
 public FormalRuntimeCapabilityAuthoringService(AgentPublicationMapper mapper,AgentPublicationJournal journal,FormalRuntimeCapabilitySchema schema,PermissionSchema json,AgentPublicationDependencies dependencies,ResourceReadAuthority resources,EvaluationReferences references,FormalRuntimeCapabilityIdentityAuthority identities,IdentityDirectoryPolicy settings){this.mapper=mapper;this.journal=journal;this.schema=schema;this.json=json;this.dependencies=dependencies;this.resources=resources;this.references=references;this.identities=identities;this.settings=settings;}
 public JsonNode author(EnterpriseAccess.Admission a,String key,JsonNode request)throws IOException {
  if(a.simulationWorker())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);schema.validate("FormalCapability",request);FormalRuntimeCapabilityIdentityAuthority.requireSchemas(request);
  String project=request.path("project_id").isNull()?null:request.path("project_id").asText();
  var grant=resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,project==null?"ENTERPRISE":"PROJECT",project==null?a.context().tenant_id():project),"capability.configure","AGENT_PUBLICATION");
  var prior=journal.prior(a,"AUTHOR",key,request);if(prior!=null){identities.require(a,new PermissionProtocol.Basis(prior.resourceId(),prior.contentSha256()),request.path("kind").asText(),project,"AGENT_PUBLICATION");return schema.read(prior.receiptJson());}
  var artifact=FormalRuntimeCapabilityIdentityAuthority.basis(request.path("source_artifact"));references.artifact(project,artifact);var deps=dependencies.require(a,project,List.of(artifact),"AGENT_PUBLICATION");
  var roleGrants=json.object().putArray("entries");
  for(var permission:request.path("tool_scope").path("permissions")){
   var scope=AgentPublicationWitness.scope(permission.path("scope"));
   if(!a.context().tenant_id().equals(scope.tenant_id())||scope.project_id()!=null&&!Objects.equals(project,scope.project_id()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
   var witness=resources.require(a,scope,permission.path("action").asText(),permission.path("purpose").asText());
   var entry=json.object();entry.set("permission",permission);entry.set("grant",AgentPublicationWitness.tree(witness));roleGrants.add(entry);
  }
  var v=journal.values(a);String config=UUID.randomUUID().toString(),version=UUID.randomUUID().toString(),record=UUID.randomUUID().toString();LocalDateTime now=(LocalDateTime)v.get("now");
  var fixed=FormalRuntimeCapabilityIdentityAuthority.fixed(json,a.context().tenant_id(),config,version,a.actorId(),now,request);String hash=schema.hash(fixed);
  v.put("projectId",project);v.put("configId",config);v.put("versionId",version);v.put("headKind","CAPABILITY");v.put("name",null);v.put("entityKind","CAPABILITY_VERSION");v.put("contentHash",hash);v.put("kind",request.path("kind").asText());v.put("inputSchema",schema.canonical(request.path("input_schema_ref")));v.put("outputSchema",schema.canonical(request.path("output_schema_ref")));v.put("toolScope",schema.canonical(request.path("tool_scope")));v.put("artifactDigest",artifact.content_sha256());v.put("manifest",schema.manifest());v.put("classification",settings.classification());
  AgentPublicationJournal.one(mapper.insertHead(v));AgentPublicationJournal.one(mapper.insertVersion(v));AgentPublicationJournal.one(mapper.insertCapability(v));AgentPublicationJournal.one(mapper.initializeHead(v));v.put("upstreamId",artifact.version_id());v.put("relation","FORMAL_CAPABILITY_SOURCE");AgentPublicationJournal.one(mapper.insertLineage(v));
  v.put("versionId",record);v.put("entityKind","AGENT_PUBLICATION_RECORD");AgentPublicationJournal.one(mapper.insertVersion(v));var auth=json.object();auth.set("governance",AgentPublicationWitness.tree(grant));auth.put("identity_mode",a.bindingView().mode());auth.set("dependencies",deps.authorizations());auth.set("role_permissions",roleGrants);
  var row=journal.save(a,project,record,"AUTHOR",version,key,request,fixed,auth,settings.classification(),now);
  identities.require(a,new PermissionProtocol.Basis(version,hash),request.path("kind").asText(),project,"AGENT_PUBLICATION");return schema.read(row.receiptJson());
 }
}
