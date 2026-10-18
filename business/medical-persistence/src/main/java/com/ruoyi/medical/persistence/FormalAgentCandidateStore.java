package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
/** Exact authored fixed candidate. This store never returns a publication or execution permit. */
@Component public final class FormalAgentCandidateStore {
 private final FormalAgentCandidateMapper mapper;private final AgentPublicationMapper rows;private final AgentPublicationJournal journal;private final FormalAgentPublicationSchema schema;private final PermissionSchema json;private final ResourceReadAuthority resources;
 public FormalAgentCandidateStore(FormalAgentCandidateMapper mapper,AgentPublicationMapper rows,AgentPublicationJournal journal,FormalAgentPublicationSchema schema,PermissionSchema json,ResourceReadAuthority resources){this.mapper=mapper;this.rows=rows;this.journal=journal;this.schema=schema;this.json=json;this.resources=resources;}
 record Loaded(FormalAgentCandidateMapper.Binding binding,AgentPublicationMapper.Agent agent,JsonNode fixed,PermissionProtocol.GrantRow readGrant){}
 Loaded read(EnterpriseAccess.Admission a,PermissionProtocol.Basis basis,String purpose)throws IOException {
  var v=values(a,basis.version_id());var b=AgentPublicationJournal.one(mapper.candidate(v));var agent=AgentPublicationJournal.one(rows.agent(v));
  var grant=resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),agent.projectId(),"CONFIGURATION",agent.agentId()),"configuration.read",purpose);
  var fixed=schema.validate("FixedCandidate",schema.read(b.fixedJson()));var original=journal.read(a,b.authorRecordId());
  var request=schema.validate("Author",schema.read(original.requestJson()));
  if(!a.context().tenant_id().equals(b.tenantId())||!b.tenantId().equals(agent.tenantId())||!b.versionId().equals(basis.version_id())||!b.versionId().equals(agent.versionId())||!b.contentSha256().equals(basis.content_sha256())||!b.contentSha256().equals(agent.contentSha256())||!b.contentSha256().equals(schema.hash(fixed))||!Objects.equals(b.projectId(),agent.projectId())||!schema.manifest().equals(b.schemaManifest())||!schema.manifest().equals(agent.schemaManifest())||!"1.0.0".equals(b.schemaVersion())||!"1.0.0".equals(agent.schemaVersion())||!b.fixedJson().equals(schema.canonical(fixed)))bad();
  if(!"AUTHOR".equals(original.kind())||!b.versionId().equals(original.resourceId())||!b.createdBy().equals(original.createdBy())||!b.createdAt().equals(original.createdAt())||!Objects.equals(b.projectId(),original.projectId())||!b.classification().equals(original.classification())||!b.fixedJson().equals(original.contentJson()))bad();
  AgentPublicationWitness.requireGovernance(original,schema.read(original.authorizationsJson()),"agent.configure","AGENT_PUBLICATION",json);
  var rebuilt=fixed(a.context().tenant_id(),agent.agentId(),b.versionId(),b.authorRecordId(),b.createdBy(),b.createdAt(),b.classification(),request,json);
  if(!schema.canonical(rebuilt).equals(b.fixedJson())||!b.policyVersionId().equals(fixed.path("execution_policy").path("version_id").asText())||!b.policySha256().equals(fixed.path("execution_policy").path("content_sha256").asText())||!agent.name().equals(fixed.path("name").asText())||!agent.createdBy().equals(b.createdBy())||!agent.createdAt().equals(b.createdAt())||!agent.classification().equals(b.classification())||!schema.canonical(fixed.path("manifest")).equals(agent.dependencyManifest())||!schema.canonical(fixed.path("manifest").path("limits")).equals(agent.limits()))bad();
  if(!Set.of("DRAFT","VALIDATING","EVALUATING","REVIEW_PENDING","PUBLISHED","DISABLED").contains(agent.status())||agent.revision().signum()<=0)bad();
  var expected=directParents(fixed.path("manifest"),b.policyVersionId()).stream().map(id->"FORMAL_AGENT_DEPENDENCY:"+id).toList();var actual=mapper.parents(v);if(!expected.equals(actual)||actual.size()!=new HashSet<>(actual).size())bad();
  return new Loaded(b,agent,fixed,grant);
 }
 static JsonNode fixed(String tenant,String agent,String version,String record,String actor,java.time.LocalDateTime now,String classification,JsonNode request,PermissionSchema json){var n=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("agent_id",agent).put("version_id",version).put("author_record_id",record).put("created_by",actor).put("created_at",now.toInstant(java.time.ZoneOffset.UTC).toString()).put("classification",classification);for(String field:List.of("project_id","name","execution_policy","manifest"))n.set(field,request.required(field));return n;}
 static List<String> directParents(JsonNode manifest,String policy){var ids=new TreeSet<String>();ids.add(policy);walkRefs(manifest,ids);return List.copyOf(ids);}
 private static void walkRefs(JsonNode node,Set<String> ids){if(node.isObject()){if(node.has("version_id")&&node.has("content_sha256"))ids.add(node.required("version_id").asText());if(node.has("artifact_id")&&node.has("sha256"))ids.add(node.required("artifact_id").asText());node.elements().forEachRemaining(n->walkRefs(n,ids));}else if(node.isArray())node.forEach(n->walkRefs(n,ids));}
 static Map<String,Object> values(EnterpriseAccess.Admission a,String version){var v=new HashMap<String,Object>();v.put("userId",a.actorId());v.put("versionId",version);return v;}
 static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
