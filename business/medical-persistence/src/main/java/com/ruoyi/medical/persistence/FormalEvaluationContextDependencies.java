package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.ruoyi.medical.contracts.*;import java.io.IOException;import java.util.*;
import static com.ruoyi.medical.persistence.FormalEvaluationContextProtocol.*;
/** Real candidate/policy/capability closure. Does not execute an assembler or approve a context plan. */
public final class FormalEvaluationContextDependencies {
 private final FormalAgentCandidateAuthority candidates;private final AgentExecutionPolicyAuthority policies;private final FormalCapabilityIdentityAuthority capabilities;
 private final FormalRuntimeCapabilityIdentityAuthority components;private final SchemaCatalogue catalogue;private final PermissionSchema json;
 public FormalEvaluationContextDependencies(FormalAgentCandidateAuthority candidates,AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority capabilities,FormalRuntimeCapabilityIdentityAuthority components,SchemaCatalogue catalogue,PermissionSchema json){this.candidates=candidates;this.policies=policies;this.capabilities=capabilities;this.components=components;this.catalogue=catalogue;this.json=json;}
 Checked require(EnterpriseAccess.Admission a,Definition definition)throws IOException{
  FormalEvaluationExecutionAdmission.current(a);FormalEvaluationContextSchema.check(definition);
  var candidate=candidates.require(a,definition.candidate(),definition.projectId(),"EVALUATION_RESULTS");if(!candidate.executionPolicy().equals(definition.executionPolicy())||"DISABLED".equals(candidate.status()))denied();
  var policy=policies.requireByVersion(a,definition.executionPolicy().version_id(),definition.executionPolicy().content_sha256(),definition.projectId(),candidate.classification(),"RUN_CREATION");
  var expected=new TreeMap<String,AgentExecutionPolicyProtocol.NodeBinding>();for(var node:policy.definition().bindings())if(node.modelVersion()!=null&&expected.put(node.nodeKind(),node)!=null)bad();
  if(expected.size()!=definition.nodeBindings().size())bad();JsonNode manifest=candidate.manifestDefinition();
  var auth=json.object();auth.set("candidate",candidate.authorizations());auth.set("policy",policy.authorizations());var nodeAuth=auth.putArray("nodes");var refs=new TreeMap<String,PermissionProtocol.Basis>();add(refs,definition.candidate());add(refs,definition.executionPolicy());
  for(var node:definition.nodeBindings()){
   var bound=expected.remove(node.nodeKind());if(bound==null||!bound.roleVersion().equals(node.role()))bad();
   member(manifest,"role_versions",node.role());member(manifest,"prompt_revisions",node.prompt());member(manifest,"tool_versions",node.contextAssembler());
   var role=capabilities.require(a,node.role(),"ROLE",definition.projectId(),"EVALUATION_RESULTS");var prompt=capabilities.require(a,node.prompt(),"PROMPT",definition.projectId(),"EVALUATION_RESULTS");var assembler=components.require(a,node.contextAssembler(),"TOOL",definition.projectId(),"EVALUATION_RESULTS");
   catalogue.requirePublished(node.inputSchema().schemaRef(),node.inputSchema().schemaSha256());catalogue.requirePublished(node.outputSchema().schemaRef(),node.outputSchema().schemaSha256());
   var request=assembler.canonicalDefinition().required("request");sameSchema(node.inputSchema(),request.required("input_schema_ref"));sameSchema(node.outputSchema(),request.required("output_schema_ref"));
   schemaMember(manifest,node.contextAssembler().version_id(),"INPUT",node.inputSchema());schemaMember(manifest,node.contextAssembler().version_id(),"OUTPUT",node.outputSchema());
   var item=json.object();item.set("binding",json.tree(node));item.set("role",role.authorizations());item.set("prompt",prompt.authorizations());item.set("assembler",assembler.authorizations());item.set("assembler_source",json.tree(assembler.sourceArtifact()));nodeAuth.add(item);
   for(var ref:List.of(node.role(),node.prompt(),node.contextAssembler()))add(refs,ref);role.requireUnchanged();prompt.requireUnchanged();assembler.requireUnchanged();
  }
  if(!expected.isEmpty())bad();candidate.requireUnchanged();policy.requireUnchanged();
  return new Checked(this,a,definition,candidate.createdBy(),candidate.classification(),List.copyOf(refs.values()),auth,json.canonical(auth));
 }
 static void member(JsonNode manifest,String field,PermissionProtocol.Basis ref){int count=0;for(var n:manifest.required(field))if(ref.version_id().equals(n.required("version_id").textValue())&&ref.content_sha256().equals(n.required("content_sha256").textValue()))count++;if(count!=1)bad();}
 static void sameSchema(Schema expected,JsonNode actual){if(!expected.schemaRef().equals(actual.required("schema_ref").textValue())||!expected.schemaSha256().equals(actual.required("schema_sha256").textValue()))bad();}
 static void schemaMember(JsonNode manifest,String consumer,String direction,Schema expected){int count=0;for(var n:manifest.required("input_output_schema_refs"))if(consumer.equals(n.required("consumer_version_id").textValue())&&direction.equals(n.required("direction").textValue())){sameSchema(expected,n.required("schema"));count++;}if(count!=1)bad();}
 public static final class Checked {
  private final FormalEvaluationContextDependencies owner;private final EnterpriseAccess.Admission admission;private final Definition definition;private final String candidateAuthor,classification,canonical;private final List<PermissionProtocol.Basis> versions;private final JsonNode auth;
  private Checked(FormalEvaluationContextDependencies owner,EnterpriseAccess.Admission a,Definition d,String author,String classification,List<PermissionProtocol.Basis> versions,JsonNode auth,String canonical){this.owner=owner;admission=a;definition=d;candidateAuthor=author;this.classification=classification;this.versions=List.copyOf(versions);this.auth=auth.deepCopy();this.canonical=canonical;}
  String candidateAuthor(){return candidateAuthor;}String classification(){return classification;}List<PermissionProtocol.Basis> versions(){return versions;}JsonNode authorizations(){return auth.deepCopy();}
  void requireCurrent()throws IOException{var now=owner.require(admission,definition);if(!canonical.equals(now.canonical))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 }
 private static void add(Map<String,PermissionProtocol.Basis> refs,PermissionProtocol.Basis b){var old=refs.putIfAbsent(b.version_id(),b);if(old!=null&&!old.equals(b))bad();}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
