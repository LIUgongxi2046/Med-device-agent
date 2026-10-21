package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;

/** Human-authored case package producer. Creates real immutable bytes and lineage before normal
 * registration; an independent person must still approve. Repeating a run/step is original-only recovery. */
public final class FormalEvaluationCaseAuthoring {
 private final EnterpriseAccess access;private final FormalEvaluationJobs jobs;private final FormalEvaluationCaseOriginals cases;
 private final FormalEvaluationStageInputSchema schema;private final PermissionSchema json;private final AgentPublicationDependencies dependencies;
 private final ArtifactRegistryMapper registry;private final DependencyLineage lineage;private final FormalEvaluationStageInputObjects objects;private final RuntimeObjectTransport transport;
 public FormalEvaluationCaseAuthoring(EnterpriseAccess access,FormalEvaluationJobs jobs,FormalEvaluationCaseOriginals cases,FormalEvaluationStageInputSchema schema,PermissionSchema json,AgentPublicationDependencies dependencies,ArtifactRegistryMapper registry,DependencyLineage lineage,FormalEvaluationStageInputObjects objects,RuntimeObjectTransport transport){this.access=access;this.jobs=jobs;this.cases=cases;this.schema=schema;this.json=json;this.dependencies=dependencies;this.registry=registry;this.lineage=lineage;this.objects=objects;this.transport=transport;}
 public JsonNode author(ApplicationSessionIdentity.Verified subject,String context,JsonNode request)throws Exception{
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
  schema.validate("Author",request);
  var authored=access.execute(subject,a->ThrowingSupplier.of(()->prepare(a,context,request)).get());
  var attempt=access.execute(subject,a->ThrowingSupplier.of(()->{authored.requireCurrent(a);var intent=objects.prepare(a,authored);return objects.attempt(a,authored,intent);}).get());
  S3ObjectReader.Admission guard=()->access.execute(subject,a->ThrowingSupplier.of(()->{objects.requireTransferCurrent(a,authored,attempt);return null;}).get());
  var physical=transport.transferVerified(attempt,guard);
  var registered=access.execute(subject,a->ThrowingSupplier.of(()->objects.register(a,authored,attempt,physical)).get());
  var registration=json.object().put("schema_version",authored.version()).put("run_id",authored.job().runId()).put("step_id",authored.node().stepId());
  registration.set("artifact",json.tree(new PermissionProtocol.Basis(registered.artifact().artifactId(),registered.artifact().sha256())));
  // Normal registration performs a new verified physical GET and does not infer approval from this write.
  return cases.register(subject,context,registration);
 }
 private Authored prepare(EnterpriseAccess.Admission a,String context,JsonNode request)throws Exception{
  schema.validate("Author",request);var original=request.required("case");if(!FormalEvaluationStageInputSchema.version(request).equals(FormalEvaluationStageInputSchema.version(original)))bad();cases.requireAuthor(a,context,original);var job=jobs.original(a,text(original,"run_id"));var nodes=job.nodes().stream().filter(n->n.stepId().equals(text(original,"step_id"))).toList();if(nodes.size()!=1)bad();var node=nodes.get(0);
  var roots=new TreeMap<String,PermissionProtocol.Basis>();add(roots,node.node().cell().input());for(var rule:FormalEvaluationCaseOriginals.selectedRules(original).values())add(roots,rule);
  for(var declared:request.required("content_sources")){var b=json.basis(declared);var version=one(registry.version(b.version_id()));if(!"SOURCE_VERSION".equals(version.entityKind()))bad();add(roots,b);}
  collectBases(original.required("node_business_original"),roots,0);
  // Source-bearing identities and saved bodies are mandatory dependencies, not caller-omittable declarations.
  collectVersionReferences(original.required("node_business_original"),roots,0);
  var proof=dependencies.require(a,job.projectId(),List.copyOf(roots.values()),"AGENT_PUBLICATION");
  var selectedSources=new HashSet<String>();for(var selected:request.required("content_sources"))selectedSources.add(text(selected,"version_id"));
  for(var root:roots.values())for(var version:lineage.inspect(root.version_id(),new DependencyLineage.Limits(2048,8192)).versions().values())if("SOURCE_VERSION".equals(version.entityKind())&&!selectedSources.contains(version.id()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  var witness=json.object().put("context_revision",context).put("actor_id",a.actorId());witness.set("request",request.deepCopy());witness.set("authorizations",proof.authorizations());witness.set("roots",json.tree(roots.values()));
  return new Authored(this,context,request,job,node,List.copyOf(roots.values()),ContentReadAuthorization.canonicalBytes(original),witness);
 }
 private void collectBases(JsonNode n,Map<String,PermissionProtocol.Basis> roots,int depth)throws java.io.IOException{if(depth>64)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);if(n.isObject()&&n.has("version_id")&&n.has("content_sha256"))add(roots,json.basis(n));for(var child:n)collectBases(child,roots,depth+1);}
 private void collectVersionReferences(JsonNode n,Map<String,PermissionProtocol.Basis> roots,int depth){if(depth>64)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);if(n.isObject()){for(String key:List.of("source_version","document_revision","body_artifact_id","field_revision","evidence_revision")){var value=n.path(key);if(value.isTextual()){var version=one(registry.version(value.asText()));add(roots,new PermissionProtocol.Basis(version.id(),version.contentSha256()));}}}for(var child:n)collectVersionReferences(child,roots,depth+1);}
 public static final class Authored{
  private final FormalEvaluationCaseAuthoring owner;private final String context;private final JsonNode request,evidence;private final FormalEvaluationJobs.Job job;private final FormalEvaluationJobs.NodeRow node;private final List<PermissionProtocol.Basis> roots;private final byte[] bytes;
  private Authored(FormalEvaluationCaseAuthoring owner,String context,JsonNode request,FormalEvaluationJobs.Job job,FormalEvaluationJobs.NodeRow node,List<PermissionProtocol.Basis> roots,byte[] bytes,JsonNode evidence){this.owner=owner;this.context=context;this.request=request.deepCopy();this.job=job;this.node=node;this.roots=List.copyOf(roots);this.bytes=bytes.clone();this.evidence=evidence.deepCopy();}
  public void requireCurrent(EnterpriseAccess.Admission a)throws Exception{var current=owner.prepare(a,context,request);if(!job.equals(current.job)||!node.equals(current.node)||!roots.equals(current.roots)||!evidence.equals(current.evidence)||!Arrays.equals(bytes,current.bytes))bad();}
  public String version(){return FormalEvaluationStageInputSchema.version(request.required("case"));}public FormalEvaluationJobs.Job job(){return job;}public FormalEvaluationJobs.NodeRow node(){return node;}public byte[] bytes(){return bytes.clone();}public List<PermissionProtocol.Basis> roots(){return roots;}public JsonNode evidence(){return evidence.deepCopy();}
 }
 private static String text(JsonNode n,String k){return n.required(k).asText();}private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}private static void add(Map<String,PermissionProtocol.Basis> values,PermissionProtocol.Basis ref){var old=values.putIfAbsent(ref.version_id(),ref);if(old!=null&&!old.equals(ref))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
