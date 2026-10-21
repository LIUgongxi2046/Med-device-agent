package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.function.ThrowingSupplier;

/** Actual fixed typed inputs. A caller cannot construct the read proof or substitute a readable generic version. */
@Component public final class FormalEvaluationPolicyInputs {
 private final EnterpriseAccess access;private final EvaluationAcceptanceObjectReader reader;private final EvaluationAcceptanceInputSchema inputs;
 private final EvaluationSchema evaluations;private final AgentPublicationSchema json;private final FormalEvaluationPolicySchema schema;
 private final AgentPublicationDependencies dependencies;private final EvaluationReferences references;private final DependencyLineage lineage;
 private final FormalEvaluationPolicyMapper classifications;private final SourceReadMapper sources;private final ArtifactRegistryMapper artifacts;private final FormalEvaluationInputMapper formalRows;
 private final ObjectProvider<FormalEvaluationInputService> formalInputs;private final FormalAgentCandidateAuthority candidates;
 private final FormalAgentCandidateDependencies candidateDependencies;private final AgentExecutionPolicyAuthority executionPolicies;private final EvaluationRuleAuthority rules;
 public FormalEvaluationPolicyInputs(EnterpriseAccess access,EvaluationAcceptanceObjectReader reader,EvaluationAcceptanceInputSchema inputs,EvaluationSchema evaluations,
  AgentPublicationSchema json,FormalEvaluationPolicySchema schema,AgentPublicationDependencies dependencies,EvaluationReferences references,DependencyLineage lineage,
  SourceReadMapper sources,ArtifactRegistryMapper artifacts,FormalEvaluationInputMapper formalRows,ObjectProvider<FormalEvaluationInputService> formalInputs,
  FormalAgentCandidateAuthority candidates,FormalAgentCandidateDependencies candidateDependencies,AgentExecutionPolicyAuthority executionPolicies,EvaluationRuleAuthority rules,FormalEvaluationPolicyMapper classifications){this.classifications=classifications;this.access=access;this.reader=reader;this.inputs=inputs;this.evaluations=evaluations;this.json=json;this.schema=schema;this.dependencies=dependencies;this.references=references;this.lineage=lineage;this.sources=sources;this.artifacts=artifacts;this.formalRows=formalRows;this.formalInputs=formalInputs;this.candidates=candidates;this.candidateDependencies=candidateDependencies;this.executionPolicies=executionPolicies;this.rules=rules;}
 private static final DependencyLineage.Limits LIMITS=new DependencyLineage.Limits(512,2048);
 public Prepared prepare(ApplicationSessionIdentity.Verified subject,String context,JsonNode author,boolean executionEligible)throws Exception{return prepare(EvidenceReadAccess.human(access,subject),context,author,executionEligible);}
 Prepared prepare(EvidenceReadAccess subject,String context,JsonNode author,boolean executionEligible)throws Exception{
  schema.validate("Author",author);author=author.deepCopy();String project=nullable(author.required("specification"),"project_id");var specification=author.required("specification");var uses=uses(author);
  var dataset=subject.source(reader,context,project,basis(specification.required("dataset")),purpose(uses,basis(specification.required("dataset"))));
  inputs.validate("FormalEvaluationDataset",dataset.document());
  var design=subject.source(reader,context,project,basis(specification.required("sampling_design_evidence")),purpose(uses,basis(specification.required("sampling_design_evidence"))));
  inputs.validate("SamplingDesign",design.document());
  var documents=new LinkedHashMap<String,EvaluationAcceptanceObjectReader.VerifiedDocument>();documents.put(dataset.basis().version_id(),dataset);documents.put(design.basis().version_id(),design);
  var expectedTrees=new LinkedHashMap<String,AgentPublicationDependencies.TypedUse>();var inputDocuments=new LinkedHashMap<String,JsonNode>();var inputProofs=new LinkedHashMap<String,FormalEvaluationInputService.VerifiedInput>();
  for(var entry:dataset.document().required("samples")){
   var input=basis(entry.required("input_ref"));var expected=basis(entry.required("expected_ref"));String inputPurpose=purpose(uses,input);
   if(!inputDocuments.containsKey(input.version_id())){
    // The real 079 service verifies original registration/operation/audit/outbox and performs its own bounded original GET.
    var service=formalInputs.getIfAvailable();if(service==null)throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
    var proof=subject.input(service,context,input.version_id());inputProofs.put(input.version_id(),proof);var actual=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();actual.set("receipt",proof.receipt());actual.set("document",proof.document());if(!input.equals(basis(actual.required("receipt").required("artifact")))||!inputPurpose.equals(actual.required("receipt").required("purpose").asText())||!input.content_sha256().equals(json.hash(actual.required("document"))))bad();inputDocuments.put(input.version_id(),actual.deepCopy());bounded(documents,inputDocuments);
   }
   var child=leaf(input,inputPurpose);var expectedTree=new AgentPublicationDependencies.TypedUse(expected,purpose(uses,expected),List.of(child),true);
   var prior=expectedTrees.putIfAbsent(expected.version_id(),expectedTree);if(prior!=null&&!prior.equals(expectedTree))bad();
   if(!documents.containsKey(expected.version_id())){var document=subject.artifact(reader,context,project,expected,purpose(uses,expected),EvaluationSchema.EXPECTED_ITEMS_REF,List.of(child));evaluations.validate("ExpectedItems",document.document());documents.put(expected.version_id(),document);bounded(documents,inputDocuments);}
  }
  var manifestRef=basis(specification.required("sample_manifest_artifact"));
  var manifest=subject.artifact(reader,context,project,manifestRef,purpose(uses,manifestRef),EvaluationSchema.SAMPLE_MANIFEST_REF,List.copyOf(expectedTrees.values()));documents.put(manifestRef.version_id(),manifest);
  // The 084 wrapper validates and anchors expert_domains; the frozen pure statistical checker receives its unchanged contract.
  var statisticalSpecification=(ObjectNode)specification.deepCopy();statisticalSpecification.remove("expert_domains");
  var checked=inputs.verify(dataset.document(),manifest.document(),design.document(),statisticalSpecification);
  for(var sample:checked.samples())if(!json.canonical(evaluations.expectedItems(sample)).equals(documents.get(sample.expected_ref().version_id()).canonical()))bad();
  JsonNode fixedAuthor=author;var prepared=new Prepared(this,subject,context,project,fixedAuthor,checked,documents,inputDocuments,inputProofs,executionEligible);
  prepared.initial=subject.execute(a->ThrowingSupplier.of(()->prepared.state(a)).get());return prepared;
 }
 public static final class Prepared {
  private final FormalEvaluationPolicyInputs owner;private final EvidenceReadAccess subject;private final String context,project,authorCanonical;
  private final JsonNode author;private final EvaluationAcceptanceInputSchema.CheckedInputs checked;
  private final Map<String,EvaluationAcceptanceObjectReader.VerifiedDocument> documents;private final Map<String,JsonNode> inputDocuments;private final Map<String,FormalEvaluationInputService.VerifiedInput> inputProofs;private final boolean executionEligible;
  private State initial;
  private Prepared(FormalEvaluationPolicyInputs owner,EvidenceReadAccess subject,String context,String project,JsonNode author,EvaluationAcceptanceInputSchema.CheckedInputs checked,Map<String,EvaluationAcceptanceObjectReader.VerifiedDocument> docs,Map<String,JsonNode> inputDocuments,Map<String,FormalEvaluationInputService.VerifiedInput> inputProofs,boolean eligible)throws IOException{this.owner=owner;this.subject=subject;this.context=context;this.project=project;this.author=author.deepCopy();authorCanonical=owner.json.canonical(author);this.checked=checked;this.documents=Map.copyOf(docs);var copy=new TreeMap<String,JsonNode>();inputDocuments.forEach((k,v)->copy.put(k,v.deepCopy()));this.inputDocuments=Collections.unmodifiableMap(copy);this.inputProofs=Map.copyOf(inputProofs);executionEligible=eligible;}
  public JsonNode snapshot(){return initial.snapshot().deepCopy();}public JsonNode authorizations(){return initial.auth().deepCopy();}public List<String> dependencyVersions(){return initial.versions();}
  public EvaluationAcceptanceInputSchema.CheckedInputs checked(){return checked;}
  public void requireConsumer(EnterpriseAccess.Admission a,JsonNode request,boolean eligible)throws IOException{if(!authorCanonical.equals(owner.json.canonical(request))||eligible!=executionEligible)bad();requireCurrent(a);}
  public void requireCurrent(EnterpriseAccess.Admission a)throws IOException{var current=state(a);if(!initial.equals(current))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
  private State state(EnterpriseAccess.Admission a)throws IOException{return owner.state(a,this);}
 }
 private record State(String actor,String tenant,String context,String stateHash,JsonNode snapshot,JsonNode auth,List<String> versions){private State{snapshot=snapshot.deepCopy();auth=auth.deepCopy();versions=List.copyOf(versions);}}
 private State state(EnterpriseAccess.Admission a,Prepared p)throws IOException{
  if(a.simulationWorker()||!a.actorId().equals(p.subject.actorId())||!a.context().context_revision().equals(p.context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
  for(var document:p.documents.values())document.requireCurrent(a);for(var proof:p.inputProofs.values())proof.requireCurrent(a);
  var use=uses(p.author);var consumed=new HashSet<String>();var auth=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();var snapshot=auth.deepCopy();snapshot.put("schema_version","1.0.0").put("input_schema_sha256",inputs.schemaSha256()).put("policy_schema_sha256",schema.schemaSha256());
  var objects=snapshot.putObject("objects");for(var e:new TreeMap<>(p.documents).entrySet()){var d=e.getValue();var n=objects.putObject(e.getKey());n.set("basis",AgentPublicationWitness.tree(d.basis()));n.set("object",AgentPublicationWitness.tree(d.object()));n.put("canonical_sha256",json.hash(d.document()));n.set("document",d.document());consumed.add(e.getKey());}
  var sourceFamilies=new HashMap<String,String>();var all=new TreeSet<String>();var dataAuth=auth.putObject("data");
  for(var sample:p.checked.samples()){
   var input=sample.input_ref();String purpose=purpose(use,input);consumed.add(input.version_id());var rows=formalRows.registration(Map.of("userId",a.actorId(),"artifactId",input.version_id()));var registered=one(rows);
   if(!a.context().tenant_id().equals(registered.tenantId())||!input.content_sha256().equals(registered.documentSha256())||!purpose.equals(registered.purpose())||!FormalEvaluationInputSchema.REF.equals(registered.schemaRef())||!registered.canonicalDocument().equals(json.canonical(p.inputDocuments.get(input.version_id()).required("document"))))bad();
   references.artifact(p.project,input,FormalEvaluationInputSchema.REF);var actual=one(artifacts.artifact(input.version_id()));if(!FormalEvaluationInputSchema.ARTIFACT_KIND.equals(actual.artifactKind()))bad();
   var graph=lineage.inspect(input.version_id(),LIMITS);var actualSources=new TreeMap<String,PermissionProtocol.Basis>();for(var v:graph.versions().values())if("SOURCE_VERSION".equals(v.entityKind()))actualSources.put(v.id(),new PermissionProtocol.Basis(v.id(),v.contentSha256()));
   var entry=p.documents.get(p.checked.dataset().version_id()).document().required("samples");var matching=new ArrayList<JsonNode>();entry.forEach(x->{if(sample.sample_id().equals(x.required("sample_id").asText()))matching.add(x);});var declared=new TreeMap<String,PermissionProtocol.Basis>();for(var v:one(matching).required("source_versions")){var b=basis(v);declared.put(b.version_id(),b);if(!purpose.equals(purpose(use,b)))bad();consumed.add(b.version_id());}
   if(!actualSources.equals(declared))bad();for(var src:actualSources.values()){String old=sourceFamilies.putIfAbsent(src.content_sha256(),sample.source_family());if(old!=null&&!old.equals(sample.source_family()))bad();}
   var proof=dependencies.requireByUse(a,p.project,List.of(new AgentPublicationDependencies.DependencyUse(input,purpose)));dataAuth.set(input.version_id(),proof.authorizations());all.addAll(proof.requiredVersionIds());
   objects.set(input.version_id(),AgentPublicationWitness.tree(Map.of("registration",registered,"object",actual.reference(),"document",p.inputDocuments.get(input.version_id()).required("document"))));
  }
  for(var ref:p.checked.samplingEvidenceSources()){var source=one(sources.source(ref.version_id()));var version=one(artifacts.version(ref.version_id()));if(!"SOURCE_VERSION".equals(version.entityKind())||!ref.content_sha256().equals(source.originalSha256())||!ref.content_sha256().equals(version.contentSha256()))bad();consumed.add(ref.version_id());var proof=dependencies.requireByUse(a,p.project,List.of(new AgentPublicationDependencies.DependencyUse(ref,purpose(use,ref))));dataAuth.set(ref.version_id(),proof.authorizations());all.addAll(proof.requiredVersionIds());}
  // Recheck complete explicit typed trees, not uniform-purpose generic roots.
  var children=new ArrayList<AgentPublicationDependencies.TypedUse>();for(var s:p.checked.samples())children.add(new AgentPublicationDependencies.TypedUse(s.expected_ref(),purpose(use,s.expected_ref()),List.of(leaf(s.input_ref(),purpose(use,s.input_ref()))),true));
  var manifestProof=dependencies.requireTypedTree(a,p.project,new AgentPublicationDependencies.TypedUse(p.checked.sampleManifest(),purpose(use,p.checked.sampleManifest()),children,true));dataAuth.set(p.checked.sampleManifest().version_id(),manifestProof.authorizations());all.addAll(manifestProof.requiredVersionIds());
  for(var ref:List.of(p.checked.dataset(),p.checked.samplingDesign())){var proof=dependencies.requireByUse(a,p.project,List.of(new AgentPublicationDependencies.DependencyUse(ref,purpose(use,ref))));dataAuth.set(ref.version_id(),proof.authorizations());all.addAll(proof.requiredVersionIds());}
  var spec=p.author.required("specification");var rule=rules.require(a,basis(spec.required("rule_revision")),p.executionEligible);var ruleChildren=new ArrayList<AgentPublicationDependencies.TypedUse>();
  var ruleRoot=basis(spec.required("rule_revision"));var ruleTree=ruleTree(a,ruleRoot,rule,use,consumed,children,new HashSet<>(),0);var ruleProof=dependencies.requireTypedTree(a,p.project,ruleTree);auth.set("rule",ruleProof.authorizations());all.addAll(ruleProof.requiredVersionIds());
  for(var expected:p.checked.expectedRefs())if(!rule.request().content().basis_manifest().contains(expected))bad();
  String business=null;var candidateAuth=auth.putObject("candidates");var candidateObjects=snapshot.putObject("candidates");String classification="PUBLIC";
  for(var raw:spec.required("candidate_refs")){var ref=basis(raw);var c=candidates.require(a,ref,p.project,"EVALUATION_RESULTS");classification=maxClassification(classification,c.classification());var ep=c.executionPolicy();JsonNode policyAuth;String next;List<String> versions;
   if(p.executionEligible){var proof=executionPolicies.requireByVersion(a,ep.version_id(),ep.content_sha256(),p.project,c.classification(),"AGENT_PUBLICATION");next=proof.businessPurpose();policyAuth=proof.authorizations();versions=proof.dependencyVersions();}
   else{var proof=executionPolicies.requireHistoricalByVersion(a,ep.version_id(),ep.content_sha256(),p.project,c.classification(),"AGENT_PUBLICATION");next=proof.businessPurpose();policyAuth=proof.authorizations();versions=proof.dependencyVersions();}
   var candidateClosure=p.executionEligible?candidateDependencies.require(a,p.project,ep,c.manifestDefinition(),true):candidateDependencies.requireHistorical(a,p.project,ep,c.manifestDefinition(),c.classification());all.addAll(candidateClosure.versions());candidateAuth.set(ref.version_id()+"_dependencies",candidateClosure.authorizations());if(business!=null&&!business.equals(next))bad();business=next;if(!spec.required("runtime_manifest_sha256").asText().equals(c.runtimeManifestSha256()))bad();candidateObjects.set(ref.version_id(),AgentPublicationWitness.tree(Map.of("manifest",c.manifestDefinition(),"created_by",c.createdBy(),"execution_policy",ep,"classification",c.classification())));candidateAuth.set(ref.version_id(),AgentPublicationWitness.tree(Map.of("read",c.authorizations(),"execution_policy",policyAuth)));all.add(ref.version_id());all.addAll(versions);
  }
  for(var ref:p.checked.inputRefs())if(!Objects.equals(business,purpose(use,ref)))bad();
  if(!consumed.equals(use.keySet()))bad();
  // Classification is actual immutable metadata from the complete checked closure, never from arbitrary document fields.
  var sourceRights=snapshot.putObject("source_rights_classifications");var classified=snapshot.putObject("dependency_classifications");var metadataIds=new TreeSet<String>(all);metadataIds.addAll(consumed);metadataIds.addAll(p.documents.keySet());
  for(String id:metadataIds){var version=one(artifacts.version(id));if(!id.equals(version.id()))bad();if(!Set.of("SOURCE_VERSION","ARTIFACT","RULE_VERSION").contains(version.entityKind()))continue;
   var row=one(classifications.dependencyClassification(Map.of("userId",a.actorId(),"versionId",id)));if(!id.equals(row.versionId())||!version.entityKind().equals(row.entityKind())||!version.contentSha256().equals(row.contentSha256()))bad();classification=maxClassification(classification,row.classification(),row.ownerClassification());classified.set(id,AgentPublicationWitness.tree(row));
   if("SOURCE_VERSION".equals(version.entityKind())){var source=one(sources.source(id));var rights=one(sources.rights(source.rightsRevision()));if(!id.equals(source.sourceVersion())||!id.equals(rights.sourceVersion())||!source.rightsRevision().equals(rights.rightsRevision())||!a.context().tenant_id().equals(rights.tenantId())||!version.contentSha256().equals(source.registeredSha256())||!rights.contentSha256().equals(rights.registeredSha256()))bad();classification=maxClassification(classification,rights.classification());sourceRights.set(id,AgentPublicationWitness.tree(Map.of("rights_revision",rights.rightsRevision(),"content_sha256",rights.contentSha256(),"classification",rights.classification())));}

  }
  snapshot.put("classification",classification);
  snapshot.set("root_uses",p.author.required("root_uses"));snapshot.put("business_purpose",business);snapshot.set("rule",AgentPublicationWitness.tree(rule));
  if(json.canonical(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32*1024*1024)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);String digest=json.hash(Map.of("snapshot",snapshot,"auth",auth,"context",a.context(),"watermarks",a.watermarks()));return new State(a.actorId(),a.context().tenant_id(),p.context,digest,snapshot,auth,List.copyOf(all));
 }
 private AgentPublicationDependencies.TypedUse ruleTree(EnterpriseAccess.Admission a,PermissionProtocol.Basis root,RuleVersionProtocol.Validated rule,Map<String,Use> uses,Set<String> consumed,List<AgentPublicationDependencies.TypedUse> expected,Set<String> path,int depth)throws IOException{
  if(depth>16||!path.add(root.version_id()))bad();consumed.add(root.version_id());var children=new ArrayList<AgentPublicationDependencies.TypedUse>();
  for(var ref:rule.request().content().basis_manifest()){consumed.add(ref.version_id());var matching=expected.stream().filter(x->x.root().equals(ref)).toList();children.add(matching.isEmpty()?leaf(ref,purpose(uses,ref)):matching.get(0));}
  var base=rule.request().base_version();if(base!=null)children.add(ruleTree(a,base,rules.require(a,base,false),uses,consumed,expected,path,depth+1));path.remove(root.version_id());return new AgentPublicationDependencies.TypedUse(root,purpose(uses,root),children,false);
 }
 static String maxClassification(String... values){var levels=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");int maximum=0;for(var value:values){if(value==null)bad();int index=levels.indexOf(value);if(index<0)bad();maximum=Math.max(maximum,index);}return levels.get(maximum);}
 private void bounded(Map<String,EvaluationAcceptanceObjectReader.VerifiedDocument> docs,Map<String,JsonNode> inputs)throws IOException{long bytes=0;for(var d:docs.values())bytes+=d.canonical().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;for(var d:inputs.values())bytes+=json.canonical(d).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(bytes>16*1024*1024)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
 private record Use(PermissionProtocol.Basis version,String purpose){}
 private static Map<String,Use> uses(JsonNode author){var result=new LinkedHashMap<String,Use>();for(var n:author.required("root_uses")){var b=basis(n.required("version"));if(result.putIfAbsent(b.version_id(),new Use(b,n.required("purpose").asText()))!=null)bad();}return result;}
 private static String purpose(Map<String,Use> uses,PermissionProtocol.Basis b){var use=uses.get(b.version_id());if(use==null||!b.equals(use.version()))bad();return use.purpose();}
 private static AgentPublicationDependencies.TypedUse leaf(PermissionProtocol.Basis b,String purpose){return new AgentPublicationDependencies.TypedUse(b,purpose,List.of(),false);}
 static PermissionProtocol.Basis basis(JsonNode n){return new PermissionProtocol.Basis(n.required("version_id").asText(),n.required("content_sha256").asText());}
 private static String nullable(JsonNode n,String k){return n.required(k).isNull()?null:n.required(k).asText();}
 private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
