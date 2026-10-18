package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;

/** Genuine new formal author record; this proof does not approve execution or publication. */
@Component public final class FormalCapabilityIdentityAuthority {
 private final ConfigurationAuthoringMapper configurations;private final AgentPublicationMapper mapper;private final AgentPublicationJournal journal;
 private final AgentPublicationSchema schema;private final PermissionSchema json;private final AgentPublicationDependencies dependencies;private final ResourceReadAuthority resources;
 public FormalCapabilityIdentityAuthority(ConfigurationAuthoringMapper configurations,AgentPublicationMapper mapper,AgentPublicationJournal journal,AgentPublicationSchema schema,PermissionSchema json,AgentPublicationDependencies dependencies,ResourceReadAuthority resources){this.configurations=configurations;this.mapper=mapper;this.journal=journal;this.schema=schema;this.json=json;this.dependencies=dependencies;this.resources=resources;}
 @FunctionalInterface private interface Current{void check()throws IOException;}
 public static final class VerifiedCapability {
  private final PermissionProtocol.Basis version,artifact;private final String kind;private final JsonNode fixed,authorizations;private final Current current;
  private VerifiedCapability(PermissionProtocol.Basis version,PermissionProtocol.Basis artifact,String kind,JsonNode fixed,JsonNode authorizations,Current current){this.version=version;this.artifact=artifact;this.kind=kind;this.fixed=fixed.deepCopy();this.authorizations=authorizations.deepCopy();this.current=current;}
  public PermissionProtocol.Basis version(){return version;}public PermissionProtocol.Basis sourceArtifact(){return artifact;}public String kind(){return kind;}public String status(){return "DRAFT";}public JsonNode canonicalDefinition(){return fixed.deepCopy();}public JsonNode authorizations(){return authorizations.deepCopy();}public void requireUnchanged()throws IOException{current.check();}
 }
 public VerifiedCapability require(EnterpriseAccess.Admission a,PermissionProtocol.Basis basis,String expectedKind,String project,String purpose)throws IOException {
  if(!Set.of("AGENT_PUBLICATION","MODEL_VERIFICATION","EVALUATION_RESULTS","TASK_CREATION","PLAN_MAINTENANCE","RUN_CREATION").contains(purpose))bad();
  var loaded=loadIdentity(a.context().tenant_id(),a.actorId(),basis,expectedKind,project,false,a);
  resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),loaded.row.projectId(),"CONFIGURATION",loaded.row.configId()),"configuration.read",purpose);
  // Package provenance has one genuine fixed governance purpose. The caller's target action is checked separately above.
  var closure=dependencies.require(a,project,List.of(basis),"AGENT_PUBLICATION");String canonical=schema.canonical(loaded.fixed);
  return new VerifiedCapability(basis,loaded.artifact,expectedKind,loaded.fixed,closure.authorizations(),()->{var now=require(a,basis,expectedKind,project,purpose);if(!canonical.equals(schema.canonical(now.canonicalDefinition()))||!schema.canonical(closure.authorizations()).equals(schema.canonical(now.authorizations())))bad();});
 }
 private record Loaded(ConfigurationAuthoringProtocol.Stored row,PermissionProtocol.Basis artifact,JsonNode fixed){}
 private Loaded loadIdentity(String tenant,String actor,PermissionProtocol.Basis basis,String expectedKind,String project,boolean metadataOnly,EnterpriseAccess.Admission admission)throws IOException {
  if(!Set.of("ROLE","PROMPT","SKILL","TOOL").contains(expectedKind))bad();
  var values=new HashMap<String,Object>();values.put("userId",actor);values.put("agent",false);values.put("entityKind","CAPABILITY_VERSION");values.put("versionId",basis.version_id());
  var row=AgentPublicationJournal.one(configurations.stored(values));var original=AgentPublicationJournal.one(mapper.capabilityAuthor(values));if(metadataOnly)journal.verifyMetadata(tenant,actor,original);else journal.verify(admission,original);AgentPublicationWitness.requireGovernance(original,schema.read(original.authorizationsJson()),"capability.configure","AGENT_PUBLICATION",json);
  if(!tenant.equals(row.tenantId())||!basis.version_id().equals(row.versionId())||!basis.content_sha256().equals(row.contentHash())||!row.contentHash().equals(row.registryHash())||!expectedKind.equals(row.kind())||!"DRAFT".equals(row.status())||!"DRAFT".equals(row.headStatus())||!row.versionId().equals(row.currentVersion())||!"1.0.0".equals(row.schemaVersion())||!schema.manifest().equals(row.manifest())||row.projectId()!=null&&!row.projectId().equals(project))bad();
  if(!"FORMAL_CAPABILITY_AUTHOR".equals(original.kind())||!row.versionId().equals(original.resourceId())||!Objects.equals(row.projectId(),original.projectId())||!row.createdBy().equals(original.createdBy())||!row.createdAt().equals(original.createdAt())||!row.classification().equals(original.classification()))bad();
  var request=schema.validate("FormalCapability",schema.read(original.requestJson()));AgentPublicationWitness.requireRole(original,request,schema.read(original.authorizationsJson()),json);var artifact=basis(request.path("source_artifact"));var parent=AgentPublicationJournal.one(configurations.parents(values));
  if(!"FORMAL_CAPABILITY_SOURCE".equals(parent.relation())||!artifact.version_id().equals(parent.versionId())||!artifact.content_sha256().equals(parent.hash()))bad();
  var fixed=fixed(json,row.tenantId(),row.configId(),row.versionId(),row.createdBy(),row.createdAt(),request);
  if(!schema.hash(fixed).equals(row.contentHash())||!schema.canonical(fixed).equals(original.contentJson()))bad();
  var projection=schema.read(row.projection());if(!row.kind().equals(request.path("kind").asText())||!artifact.content_sha256().equals(projection.path("artifact_digest").asText()))bad();
  for(String field:List.of("input_schema_ref","output_schema_ref","tool_scope"))if(!schema.canonical(request.path(field)).equals(schema.canonical(projection.path(field))))bad();
  requireSchemas(request);return new Loaded(row,artifact,fixed);
 }
 /** A metadata identity only, deliberately a different type from a current content-read proof. */
 public static final class RegisteredIdentity {
  private final PermissionProtocol.Basis version,artifact;private final String kind,configId,digest;private final JsonNode fixed;
  private RegisteredIdentity(PermissionProtocol.Basis version,Loaded loaded,String kind,String digest){this.version=version;artifact=loaded.artifact;this.kind=kind;configId=loaded.row.configId();fixed=loaded.fixed.deepCopy();this.digest=digest;}
  public PermissionProtocol.Basis version(){return version;}public PermissionProtocol.Basis sourceArtifact(){return artifact;}public String kind(){return kind;}public String status(){return "DRAFT";}public String configId(){return configId;}public String metadataSha256(){return digest;}public JsonNode canonicalDefinition(){return fixed.deepCopy();}
 }
 public RegisteredIdentity requireRegisteredIdentity(String tenant,String actor,PermissionProtocol.Basis basis,String expectedKind,String project)throws IOException {
  var scope=TenantScope.requireCurrent();if(!tenant.equals(scope.tenantId())||!actor.equals(scope.subjectId()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  var loaded=loadIdentity(tenant,actor,basis,expectedKind,project,true,null);var values=new HashMap<String,Object>();values.put("userId",actor);values.put("recordId",loaded.artifact.version_id());
  var artifact=AgentPublicationJournal.one(mapper.packageArtifact(values));var binding=AgentPublicationJournal.one(mapper.packageBinding(values));var version=AgentPublicationJournal.one(mapper.version(values));
  if(!tenant.equals(artifact.tenantId())||!tenant.equals(binding.tenantId())||!loaded.artifact.version_id().equals(artifact.artifactId())||!artifact.artifactId().equals(binding.artifactId())||!artifact.artifactId().equals(version.id())||!"ARTIFACT".equals(version.entityKind())||!loaded.artifact.content_sha256().equals(artifact.sha256())||!artifact.sha256().equals(artifact.contentSha256())||!artifact.sha256().equals(version.contentSha256())||!Objects.equals(artifact.projectId(),binding.projectId())||!Objects.equals(artifact.projectId(),version.projectId())||artifact.projectId()!=null&&!artifact.projectId().equals(project)||!artifact.accessSetId().equals(binding.accessSetId())||!"1.0.0".equals(artifact.schemaVersion()))bad();
  StoredObjectReferences.decode(artifact.storedObject(),artifact.reference());
  return new RegisteredIdentity(basis,loaded,expectedKind,schema.hash(Map.of("fixed",loaded.fixed,"artifact",json.tree(artifact),"binding",json.tree(binding),"version",json.tree(version))));
 }
 static JsonNode fixed(PermissionSchema json,String tenant,String config,String version,String actor,java.time.LocalDateTime time,JsonNode request){var n=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("config_id",config).put("version_id",version).put("created_by",actor).put("created_at",time.toInstant(java.time.ZoneOffset.UTC).toString());n.set("request",request);return n;}
 static void requireSchemas(JsonNode request)throws IOException{SchemaCatalogue catalogue;try{catalogue=SchemaCatalogue.fromClasspath();}catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
 for(String field:List.of("input_schema_ref","output_schema_ref")){var r=request.path(field);catalogue.requirePublished(r.path("schema_ref").asText(),r.path("schema_sha256").asText());}}
 static PermissionProtocol.Basis basis(JsonNode n){return new PermissionProtocol.Basis(n.path("version_id").asText(),n.path("content_sha256").asText());}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
