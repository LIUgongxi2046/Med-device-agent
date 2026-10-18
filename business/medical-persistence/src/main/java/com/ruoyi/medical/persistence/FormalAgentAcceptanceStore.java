package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;import java.util.*;import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
/** Complete original decision metadata only. Current professional execution evidence is independently required by the acceptance authority. */
@Component public final class FormalAgentAcceptanceStore {
 private final FormalAgentAcceptanceMapper mapper;private final AgentPublicationJournal journal;private final FormalAgentCandidateStore candidates;
 private final FormalAgentPublicationSchema schema;private final PermissionSchema json;private final AgentReleaseManifestSchema manifests;private final ResourceReadAuthority resources;
 public FormalAgentAcceptanceStore(FormalAgentAcceptanceMapper mapper,AgentPublicationJournal journal,FormalAgentCandidateStore candidates,FormalAgentPublicationSchema schema,PermissionSchema json,ResourceReadAuthority resources)throws Exception {this.mapper=mapper;this.journal=journal;this.candidates=candidates;this.schema=schema;this.json=json;this.resources=resources;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/agent-release.schema.json"))){manifests=new AgentReleaseManifestSchema(in.readAllBytes(),SchemaCatalogue.fromClasspath());}}
 record Loaded(FormalAgentAcceptanceMapper.Acceptance row,AgentPublicationMapper.Journal decision,JsonNode content,AgentReleaseProtocol.Validated manifest,FormalAgentCandidateStore.Loaded candidate,PermissionProtocol.GrantRow readGrant){}
 Loaded metadata(EnterpriseAccess.Admission a,PermissionProtocol.Basis ref,String purpose)throws IOException {
  var v=FormalAgentCandidateStore.values(a,null);v.put("acceptanceId",ref.version_id());var row=AgentPublicationJournal.one(mapper.acceptance(v));if("REJECT".equals(row.decision()))throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
  var candidate=new PermissionProtocol.Basis(row.candidateVersionId(),row.candidateSha256());var c=candidates.read(a,candidate,purpose);
  var original=journal.read(a,row.acceptanceId());var content=schema.validate("Accepted",schema.read(original.contentJson()));var request=schema.validate("Accept",schema.read(original.requestJson()));
  if(!a.context().tenant_id().equals(row.tenantId())||!row.acceptanceId().equals(ref.version_id())||!row.contentSha256().equals(ref.content_sha256())||!row.contentSha256().equals(original.contentSha256())||!"ACCEPTANCE".equals(original.kind())||!row.candidateVersionId().equals(original.resourceId())||!row.createdBy().equals(original.createdBy())||!row.createdAt().equals(original.createdAt())||!Objects.equals(row.projectId(),original.projectId())||!row.classification().equals(original.classification())||!"1.0.0".equals(row.schemaVersion())||!schema.manifest().equals(row.schemaManifest()))bad();
  AgentPublicationWitness.requireGovernance(original,schema.read(original.authorizationsJson()),"agent.publish","AGENT_PUBLICATION",json);
  if(!Objects.equals(c.binding().projectId(),row.projectId())||c.binding().createdBy().equals(row.createdBy())||row.createdAt().isBefore(c.binding().createdAt()))bad();
  if(!row.acceptanceId().equals(content.path("acceptance_id").asText())||!row.tenantId().equals(content.path("tenant_id").asText())||!Objects.equals(row.projectId(),nullable(content,"project_id"))||!candidate.equals(FormalAgentCandidateDependencies.basis(content.path("candidate")))||!candidate.equals(FormalAgentCandidateDependencies.basis(request.path("candidate")))||!row.decision().equals(content.path("decision").asText())||!row.decision().equals(request.path("decision").asText())||!row.createdBy().equals(content.path("accepted_by").asText())||!row.createdAt().toInstant(java.time.ZoneOffset.UTC).toString().equals(content.path("accepted_at").asText())||!row.classification().equals(content.path("classification").asText()))bad();
  for(String key:List.of("evaluation","statistics_policy"))if(!schema.canonical(request.required(key)).equals(schema.canonical(content.required(key))))bad();
  if(!c.binding().policyVersionId().equals(content.path("execution_policy").path("version_id").asText())||!c.binding().policySha256().equals(content.path("execution_policy").path("content_sha256").asText()))bad();
  var manifest=manifests.bind(schema.canonical(content.required("manifest")).getBytes(StandardCharsets.UTF_8));var m=manifest.manifest();
  if(!m.tenantId().equals(row.tenantId())||!m.releaseId().equals(row.releaseId())||!m.agentVersionId().equals(row.candidateVersionId())||!m.agentVersionSha256().equals(row.candidateSha256())||!m.evaluationId().equals(row.evaluationId())||!m.evaluationSha256().equals(row.evaluationSha256())||!manifest.sha256().equals(row.manifestSha256())||!manifest.sha256().equals(content.path("manifest_sha256").asText())||!new PermissionProtocol.Basis(row.evaluationId(),row.evaluationSha256()).equals(FormalAgentCandidateDependencies.basis(content.path("evaluation"))))bad();
  var source=content.required("manifest").deepCopy();var candidateManifest=(com.fasterxml.jackson.databind.node.ObjectNode)source;for(String key:List.of("tenant_id","release_id","agent_version_id","agent_version_sha256","evaluation_id","evaluation_sha256"))candidateManifest.remove(key);
  if(!schema.canonical(candidateManifest).equals(schema.canonical(c.fixed().required("manifest"))))bad();
  var read=resources.require(a,new PermissionProtocol.Scope(row.tenantId(),row.projectId(),row.projectId()==null?"ENTERPRISE":"PROJECT",row.projectId()==null?row.tenantId():row.projectId()),"configuration.read",purpose);
  return new Loaded(row,original,content,manifest,c,read);
 }
 private static String nullable(JsonNode n,String key){return n.path(key).isNull()?null:n.path(key).asText();}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
