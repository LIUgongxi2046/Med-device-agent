package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
/** Real DRAFT agent producer. No evaluation score, acceptance or publication is generated here. */
@Component public final class FormalAgentCandidateService {
 private final FormalAgentCandidateMapper mapper;private final AgentPublicationMapper rows;private final AgentPublicationJournal journal;private final FormalAgentPublicationSchema schema;
 private final FormalAgentCandidateStore store;private final FormalAgentCandidateDependencies dependencies;private final ResourceReadAuthority resources;private final PermissionSchema json;private final IdentityDirectoryPolicy settings;
 public FormalAgentCandidateService(FormalAgentCandidateMapper mapper,AgentPublicationMapper rows,AgentPublicationJournal journal,FormalAgentPublicationSchema schema,FormalAgentCandidateStore store,FormalAgentCandidateDependencies dependencies,ResourceReadAuthority resources,PermissionSchema json,IdentityDirectoryPolicy settings){this.mapper=mapper;this.rows=rows;this.journal=journal;this.schema=schema;this.store=store;this.dependencies=dependencies;this.resources=resources;this.json=json;this.settings=settings;}
 public JsonNode author(EnterpriseAccess.Admission a,String key,byte[] bytes)throws IOException {
  if(a.simulationWorker())deny();var request=schema.request("Author",bytes);String project=request.path("project_id").isNull()?null:request.path("project_id").asText();
  var grant=resources.require(a,governance(a,project),"agent.configure","AGENT_PUBLICATION");var prior=journal.prior(a,"AUTHOR",key,request);
  if(prior!=null){store.read(a,new PermissionProtocol.Basis(prior.resourceId(),prior.contentSha256()),"AGENT_PUBLICATION");return schema.read(prior.receiptJson());}
  var policy=FormalAgentCandidateDependencies.basis(request.required("execution_policy"));var manifest=request.required("manifest");var checked=dependencies.require(a,project,policy,manifest,false);
  var v=journal.values(a);String config=id(),version=id(),record=id();var now=(java.time.LocalDateTime)v.get("now");String classification=settings.classification();
  var fixed=FormalAgentCandidateStore.fixed(a.context().tenant_id(),config,version,record,a.actorId(),now,classification,request,json);schema.validate("FixedCandidate",fixed);String hash=schema.hash(fixed);
  v.put("projectId",project);v.put("configId",config);v.put("versionId",version);v.put("headKind","AGENT");v.put("name",request.path("name").asText());v.put("entityKind","AGENT_VERSION");v.put("contentHash",hash);v.put("dependencyManifest",schema.canonical(manifest));v.put("limits",schema.canonical(manifest.required("limits")));v.put("manifest",schema.manifest());v.put("classification",classification);
  one(rows.insertHead(v));one(rows.insertVersion(v));one(rows.insertAgent(v));one(rows.initializeHead(v));
  for(String upstream:FormalAgentCandidateStore.directParents(manifest,policy.version_id())){v.put("upstreamId",upstream);v.put("relation","FORMAL_AGENT_DEPENDENCY");one(rows.insertLineage(v));}
  v.put("versionId",record);v.put("entityKind","AGENT_PUBLICATION_RECORD");one(rows.insertVersion(v));var auth=json.object();auth.set("governance",AgentPublicationWitness.tree(grant));auth.put("identity_mode",a.bindingView().mode());auth.set("dependencies",checked.authorizations());
  var ledger=journal.save(a,project,record,"AUTHOR",version,key,request,fixed,auth,classification,now);
  v.put("versionId",version);v.put("authorRecordId",record);v.put("policyVersionId",policy.version_id());v.put("policySha256",policy.content_sha256());v.put("fixedJson",schema.canonical(fixed));one(mapper.insertCandidate(v));
  var loaded=store.read(a,new PermissionProtocol.Basis(version,hash),"AGENT_PUBLICATION");if(!loaded.binding().fixedJson().equals(schema.canonical(fixed)))bad();
  var current=dependencies.require(a,project,policy,manifest,false);if(!schema.hash(current.authorizations()).equals(schema.hash(checked.authorizations())))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
  return schema.read(ledger.receiptJson());
 }
 public JsonNode read(EnterpriseAccess.Admission a,String recordId)throws IOException {
  var original=journal.read(a,recordId);if(!"AUTHOR".equals(original.kind()))deny();var loaded=store.read(a,new PermissionProtocol.Basis(original.resourceId(),original.contentSha256()),"AGENT_PUBLICATION");
  var result=json.object().put("schema_version","1.0.0").put("status",loaded.agent().status()).put("revision",loaded.agent().revision().toString()).put("production_eligible",false);result.set("fixed",loaded.fixed());return schema.validate("CandidateView",result);
 }
 public JsonNode receipt(EnterpriseAccess.Admission a,String recordId,String operation)throws IOException {var original=journal.read(a,recordId);if(!original.createdBy().equals(a.actorId())||!original.operationId().equals(operation))deny();read(a,recordId);return schema.read(original.receiptJson());}
 static PermissionProtocol.Scope governance(EnterpriseAccess.Admission a,String project){return new PermissionProtocol.Scope(a.context().tenant_id(),project,project==null?"ENTERPRISE":"PROJECT",project==null?a.context().tenant_id():project);}
 private static String id(){return UUID.randomUUID().toString();}private static void one(int n){AgentPublicationJournal.one(n);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
