package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.SimulationEvaluationPreparationProtocol.Registration;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Bounded, enterprise-only zero-copy registration. Never writes originals or creates approvals. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="medical.simulation-evaluation-preparation",name="enabled",havingValue="true")
public final class SimulationEvaluationPreparationService {
 static final String PURPOSE="SIMULATION_EVALUATION", OPERATION="SIM-EVALUATION-PREPARE", EVENT="simulation.evaluation.preparation_registered";
 private static final DependencyLineage.Limits LIMITS=new DependencyLineage.Limits(512,2048);
 private final EnterpriseAccess access; private final SimulationEvaluationPreparationMapper mapper;
 private final SimulationFixtureMapper fixtures; private final SourceReadMapper sourceRows; private final SourceReadAuthority sourceAuthority;
 private final SourceUploadTransport transport; private final ResourceReadAuthority resources; private final PermissionMapper permissions;
 private final ArtifactRegistryMapper artifacts; private final EvaluationReferences references; private final DependencyLineage lineage;
 private final DependencyManifestSchema manifests; private final PermissionSchema json; private final EvaluationSchema evaluation;
 private final RetrievalPolicy deployment; private final SimulationAuthorizedUseEvidenceAuthority evidence;
 private final SimulationEvaluationPreparationSchema schema;
 private record Document(JsonNode value,List<PermissionProtocol.Basis> refs) { }
 private record Closure(List<ArtifactRegistryProtocol.MemberRow> members,List<String> roots,String classification,String digest) { }
 private record Prepared(SourceReadProtocol.VerifiedSource source,SimulationInvocationSources.Original original,
                         Document document,Closure closure,String digest) { }
 public SimulationEvaluationPreparationService(EnterpriseAccess access,SimulationEvaluationPreparationMapper mapper,SimulationFixtureMapper fixtures,
  SourceReadMapper sourceRows,SourceReadAuthority sourceAuthority,SourceUploadTransport transport,ResourceReadAuthority resources,
  PermissionMapper permissions,ArtifactRegistryMapper artifacts,EvaluationReferences references,DependencyLineage lineage,
  DependencyManifestSchema manifests,PermissionSchema json,EvaluationSchema evaluation,RetrievalPolicy deployment,
  SimulationAuthorizedUseEvidenceAuthority evidence)throws Exception {
  this.access=access;this.mapper=mapper;this.fixtures=fixtures;this.sourceRows=sourceRows;this.sourceAuthority=sourceAuthority;
  this.transport=transport;this.resources=resources;this.permissions=permissions;this.artifacts=artifacts;this.references=references;
  this.lineage=lineage;this.manifests=manifests;this.json=json;this.evaluation=evaluation;this.deployment=deployment;this.evidence=evidence;
  schema=new SimulationEvaluationPreparationSchema();
 }
 public JsonNode preview(ApplicationSessionIdentity.Verified subject,String context,JsonNode request)throws Exception {
  schema.validate("Preview",request);
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   context(a,context);String kind=request.required("kind").textValue();JsonNode spec=request.required("specification");
   var doc=document(a,kind,kind.equals("AUTHORIZED_USE_EVIDENCE")?evidence.preview(a,spec):spec);
   closure(a,doc.refs());String canonical=canonical(doc.value());if(canonical.getBytes(StandardCharsets.UTF_8).length>SimulationEvaluationPreparationSchema.MAX_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
   var result=json.object().put("schema_version","1.0.0").putNull("project_id").put("kind",kind)
    .put("registered",false).put("approved",false).put("production_eligible",false)
    .put("canonical_json",canonical).put("document_sha256",hash(doc.value()));
   return result;
  }).get());
 }
 public JsonNode register(ApplicationSessionIdentity.Verified subject,String context,String key,JsonNode request)throws Exception {
  schema.validate("Register",request);SimulationEvaluationPreparationSchema.key(key);PermissionSchema.revision(context);
  String requestHash=requestHash(subject,context,request);String kind=request.required("kind").textValue();
  // Admission precedes object GET. The guard repeats source, context and complete dependency authorization.
  var initial=state(subject,context,request,null,true);
  byte[] raw=readOriginal(subject,context,request,initial,true);
  JsonNode document=schema.canonicalDocument(raw);
  same(request.required("document_sha256").textValue(),WireValues.sha256(raw));
  var prepared=state(subject,context,request,document,true);
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   var current=prepare(a,context,request,document,true);same(prepared.digest(),current.digest());
   var values=values(a);values.put("sourceVersion",current.source().source().sourceVersion());
   same(current.source().source().sourceVersion(),one(mapper.lockSource(values)));
   same(current.digest(),prepare(a,context,request,document,true).digest());
   values.put("key",key);var existing=mapper.operation(values);if(existing.size()>1)mismatch();
   if(!existing.isEmpty()){
    var op=existing.get(0);if(!requestHash.equals(op.requestSha256()))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
    values.put("artifactId",op.targetId());var registration=one(mapper.registration(values));
    same(canonical(request),registration.canonicalRequest());verify(a,registration,current);return readJson(registration.receiptJson());
   }
   String artifact=UUID.randomUUID().toString(),set=UUID.randomUUID().toString(),operation=UUID.randomUUID().toString();
   var profile=SimulationEvaluationPreparationProfile.profile(kind);var closure=current.closure();
   var dependency=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",a.context().tenant_id(),null,set,closure.members());
   String depJson=canonical(json.tree(dependency)),depHash=hash(json.tree(dependency));
   values.put("projectId",null);values.put("now",Objects.requireNonNull(permissions.databaseTime()));values.put("classification",closure.classification());
   values.put("versionId",set);values.put("kind","DEPENDENCY_ACCESS_SET");values.put("hash",depHash);count(fixtures.insertContextVersion(values));
   values.put("accessId",set);values.put("dependencyJson",depJson);values.put("memberCount",closure.members().size());values.put("dependencySchema",manifests.manifestSha256());
   count(fixtures.insertContextAccessSet(values));count(fixtures.insertContextDependencyManifest(values));
   for(var member:closure.members()){values.put("member",member);count(fixtures.insertContextDependencyMember(values));}
   values.put("relationKind","SIMULATION_PREPARATION_DEPENDENCY");
   for(String consumer:List.of(set,artifact)){
    if(consumer.equals(artifact)){
     values.put("versionId",artifact);values.put("kind","ARTIFACT");values.put("hash",current.original().sha256());count(fixtures.insertContextVersion(values));
     values.put("artifactId",artifact);values.put("artifactKind",SimulationEvaluationPreparationProfile.artifactKind(kind));values.put("reference",current.original().reference());
     values.put("storedObject",StoredObjectReferences.encode(current.original().reference()));values.put("schemaHash",profile.schemaDocumentSha256());
     values.put("schemaRef",profile.schemaRef());values.put("sourceId",current.source().source().sourceId());values.put("dependencyHash",depHash);
     count(mapper.insertArtifact(values));count(mapper.insertBinding(values));
    }
    values.put("consumer",consumer);for(String root:closure.roots()){values.put("upstream",root);count(fixtures.insertContextLineage(values));}
   }
   var receipt=json.object().put("schema_version","1.0.0").putNull("project_id").put("kind",kind).put("operation_id",operation)
    .put("source_version",current.source().source().sourceVersion()).put("rights_revision",current.source().rights().rightsRevision())
    .put("production_eligible",false).put("registered",true).put("approved",false);
   receipt.set("artifact",json.tree(new PermissionProtocol.Basis(artifact,current.original().sha256())));
   receipt.put("created_by",a.actorId());receipt.put("created_at",((java.time.LocalDateTime)values.get("now")).toInstant(java.time.ZoneOffset.UTC).toString());
   var row=new Registration(a.context().tenant_id(),artifact,kind,current.source().source().sourceVersion(),current.source().rights().rightsRevision(),
    canonical(document),current.original().sha256(),canonical(request),hash(request),requestHash,canonical(receipt),operation,"1.0.0",schema.manifest(),a.actorId(),
    (java.time.LocalDateTime)values.get("now"),closure.classification());
   values.put("row",row);count(mapper.insertRegistration(values));
   values.put("operationId",operation);values.put("requestHash",requestHash);values.put("response",row.receiptJson());values.put("operationManifest",schema.manifest());
   count(mapper.saveOperation(values));
   String payload=canonical(event(row));count(permissions.saveAudit(a.actorId(),null,UUID.randomUUID().toString(),EVENT,payload,operation,schema.manifest(),closure.classification()));
   count(permissions.saveEvent(a.actorId(),operation,artifact,"1",EVENT,payload));
   same(row,one(mapper.registration(values)));verify(a,row,current);
   same(current.digest(),prepare(a,context,request,document,true).digest());return receipt;
  }).get());
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String artifactId)throws Exception {
  WireValues.uuid(artifactId);PermissionSchema.revision(context);
  Registration row=access.execute(subject,a->{context(a,context);var v=values(a);v.put("artifactId",artifactId);var stored=one(mapper.registration(v));return ThrowingSupplier.of(()->{references.require(a,null,List.of(new PermissionProtocol.Basis(stored.artifactId(),stored.documentSha256())),PURPOSE);return stored;}).get();});
  JsonNode request=schema.request("Register",row.canonicalRequest().getBytes(StandardCharsets.UTF_8));
  var initial=state(subject,context,request,null,false);byte[] raw=readOriginal(subject,context,request,initial,false);
  JsonNode document=schema.canonicalDocument(raw);same(row.documentSha256(),WireValues.sha256(raw));same(row.canonicalDocument(),canonical(document));
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   var v=values(a);v.put("artifactId",artifactId);same(row,one(mapper.registration(v)));
   var current=prepare(a,context,request,document,false);verify(a,row,current);
   var result=json.object();result.set("receipt",readJson(row.receiptJson()));result.set("document",document);return result;
  }).get());
 }
 private Prepared state(ApplicationSessionIdentity.Verified subject,String context,JsonNode request,JsonNode document,boolean writing){
  return access.execute(subject,a->ThrowingSupplier.of(()->prepare(a,context,request,document,writing)).get());
 }
 private Prepared prepare(EnterpriseAccess.Admission a,String context,JsonNode request,JsonNode document,boolean writing)throws Exception {
  context(a,context);String sourceId=request.required("source_version").textValue(),rights=request.required("rights_revision").textValue();
  var source=sourceAuthority.require(a,new SourceReadProtocol.ReadRequirement(sourceId,rights,null,null,null,null,SourceReadProtocol.Use.REVIEW,
    PURPOSE,deployment.environment(),deployment.region(),null));
  if(source.source().ownerProjectId()!=null||!"ENTERPRISE".equals(source.source().ownership()))denied();
  if(writing)resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),null,"SOURCE",source.source().sourceId()),"artifact.register",PURPOSE);
  var gates=sourceRows.gates(sourceId);if(gates.isEmpty())denied();
  for(var gate:gates)if(!"ACTIVE".equals(gate.lifecycle())||!"RECEIVED".equals(gate.uploadStatus())||!"APPROVED".equals(gate.approvalStatus()))throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
  var v=values(a);v.put("sourceVersion",sourceId);var original=one(fixtures.original(v));
  same(a.context().tenant_id(),original.tenantId());same(sourceId,original.sourceVersion());same(source.source().originalSha256(),original.sha256());
  same(request.required("document_sha256").textValue(),original.sha256());
  if(original.sizeBytes()==null||original.sizeBytes().signum()<=0||original.sizeBytes().compareTo(BigInteger.valueOf(SimulationEvaluationPreparationSchema.MAX_BYTES))>0)
   throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  // Existing upload accepts UTF-8 .txt. MIME never substitutes for canonical-byte and fixed JSON Schema validation.
  if(!Set.of("application/json","text/plain").contains(original.mediaType()))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  if(!original.objectKey().startsWith("tenants/"+original.tenantId()+"/")||original.objectKey().contains(".."))denied();
  var doc=document==null?new Document(null,List.of()):document(a,request.required("kind").textValue(),document);
  var roots=new ArrayList<>(doc.refs());roots.add(new PermissionProtocol.Basis(sourceId,source.source().originalSha256()));var closure=closure(a,roots);
  var identity=new LinkedHashMap<String,Object>();identity.put("source",source);identity.put("gates",gates);identity.put("original",original);
  identity.put("context",a.context());identity.put("watermarks",a.watermarks());identity.put("actor",a.actorId());identity.put("closure",closure);
  return new Prepared(source,original,doc,closure,WireValues.sha256(ContentReadAuthorization.canonicalBytes(identity)));
 }
 private byte[] readOriginal(ApplicationSessionIdentity.Verified subject,String context,JsonNode request,Prepared initial,boolean writing)throws Exception {
  var original=initial.original();S3ObjectReader.Admission guard=()->same(initial.digest(),state(subject,context,request,null,writing).digest());
  return transport.readDocument(subject,original.tenantId(),SimulationEvaluationPreparationSchema.MAX_BYTES,guard,(reader,current)->{
   try(var verified=reader.readVerified(original.reference(),current)){
    same(original.reference(),verified.reference());same(original.sha256(),verified.fullSha256());byte[] bytes=new byte[original.sizeBytes().intValueExact()];
    for(int start=0;start<bytes.length;){int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,bytes.length-start);System.arraycopy(verified.chunk(start,n),0,bytes,start,n);start+=n;}
    current.requireCurrent();return bytes;
   }
  });
 }
 private Document document(EnterpriseAccess.Admission a,String kind,JsonNode value)throws Exception {
  var refs=new ArrayList<PermissionProtocol.Basis>();
  switch(kind){
   case "EXPECTED_ITEMS" -> {
    evaluation.validate("ExpectedItems",value);var input=basis(value.required("input_ref"));references.artifact(null,input,SimulationInputArtifactProfile.profile().schemaRef());refs.add(input);
    validateExpectations(value.required("expectations"));
   }
   case "SAMPLE_MANIFEST" -> {
    evaluation.validate("SampleManifest",value);if(value.isEmpty())invalid();var ids=new HashSet<String>();var families=new HashMap<String,String>();var inputs=new HashMap<String,String>();
    for(var sample:value){
     if(!"SIMULATION".equals(sample.required("evidence_class").textValue())||!ids.add(sample.required("sample_id").textValue()))invalid();
     var typed=new com.fasterxml.jackson.databind.ObjectMapper().treeToValue(sample,EvaluationProtocol.Sample.class);var expected=evaluation.expectedItems(typed);
     same(hash(expected),typed.expected_ref().content_sha256());validateExpectations(sample.required("expectations"));
     String split=families.putIfAbsent(typed.source_family(),typed.split());if(split!=null&&!split.equals(typed.split()))invalid();
     String family=inputs.putIfAbsent(typed.input_ref().version_id(),typed.source_family());if(family!=null&&!family.equals(typed.source_family()))invalid();
     references.artifact(null,typed.input_ref(),SimulationInputArtifactProfile.profile().schemaRef());references.artifact(null,typed.expected_ref(),EvaluationSchema.EXPECTED_ITEMS_REF);
     refs.add(typed.input_ref());refs.add(typed.expected_ref());
    }
   }
   case "AUTHORIZED_USE_EVIDENCE" -> refs.addAll(evidence.verifySourceDocument(a,value));
   default -> invalid();
  }
  references.require(a,null,refs,PURPOSE);return new Document(value,List.copyOf(refs));
 }
 private void validateExpectations(JsonNode values){
  var dimensions=new HashSet<String>();for(var e:values){String dimension=e.required("dimension").textValue();
   if(!dimensions.add(dimension)||!dimension.equals("EVAL-11")&&!e.required("control_item_ids").isEmpty())invalid();}
 }
 private Closure closure(EnterpriseAccess.Admission a,List<PermissionProtocol.Basis> refs)throws Exception {
  references.require(a,null,refs,PURPOSE);var roots=new TreeMap<String,PermissionProtocol.Basis>();
  for(var ref:refs){var prior=roots.putIfAbsent(ref.version_id(),ref);if(prior!=null&&!prior.equals(ref))mismatch();}
  var all=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();var graphs=new ArrayList<DependencyLineage.VerifiedLineage>();
  var edges=new HashSet<SourceReadProtocol.ParentRow>();for(var ref:roots.values()){
   var graph=lineage.inspect(ref.version_id(),LIMITS);graphs.add(graph);edges.addAll(graph.graph().edgeSet());
   same(ref.content_sha256(),graph.versions().get(ref.version_id()).contentSha256());
   for(var version:graph.versions().values()){if(version.projectId()!=null)denied();var old=all.putIfAbsent(version.id(),version);if(old!=null&&!old.equals(version))mismatch();}
  }
  if(all.size()>512||edges.size()>2048)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  var sources=new TreeMap<String,SourceReadProtocol.SourceRow>();String classification="PUBLIC";
  for(var version:all.values()){
   var rows=sourceRows.source(version.id());if(rows.size()>1)mismatch();if(!rows.isEmpty()){
    var source=rows.get(0);if(source.ownerProjectId()!=null||!"ENTERPRISE".equals(source.ownership()))denied();
    var gates=sourceRows.gates(version.id());if(gates.isEmpty())throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
    for(var gate:gates)if(!"ACTIVE".equals(gate.lifecycle())||!"RECEIVED".equals(gate.uploadStatus())||!"APPROVED".equals(gate.approvalStatus()))throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
    sources.put(version.id(),source);
    classification=SimulationContextDependencies.maximum(classification,source.classification());
   }
   if(version.entityKind().equals("ARTIFACT"))classification=SimulationContextDependencies.maximum(classification,one(artifacts.artifact(version.id())).classification());
  }
  var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();for(String id:all.keySet()){
   var reachable=new TreeSet<String>();for(var graph:graphs)if(graph.versions().containsKey(id))reachable.addAll(graph.reachableFrom(id));
   String source=reachable.stream().filter(sources::containsKey).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
   members.add(new ArtifactRegistryProtocol.MemberRow(id,sources.get(source).rightsRevision()));
  }
  var proof=json.object();proof.set("versions",json.tree(all));proof.set("sources",json.tree(sources));proof.set("members",json.tree(members));
  proof.set("edges",json.tree(edges.stream().sorted(Comparator.comparing(SourceReadProtocol.ParentRow::consumerVersionId).thenComparing(SourceReadProtocol.ParentRow::upstreamVersionId).thenComparing(SourceReadProtocol.ParentRow::relationKind)).toList()));
  return new Closure(List.copyOf(members),List.copyOf(roots.keySet()),classification,hash(proof));
 }
 private void verify(EnterpriseAccess.Admission a,Registration row,Prepared current)throws Exception {
  same(a.context().tenant_id(),row.tenantId());same(schema.manifest(),row.schemaManifest());
  JsonNode request=schema.request("Register",row.canonicalRequest().getBytes(StandardCharsets.UTF_8));same(canonical(request),row.canonicalRequest());same(hash(request),row.requestBodySha256());
  same(request.required("kind").textValue(),row.kind());same(request.required("source_version").textValue(),row.sourceVersion());same(request.required("rights_revision").textValue(),row.rightsRevision());
  same(row.sourceVersion(),current.source().source().sourceVersion());same(row.rightsRevision(),current.source().rights().rightsRevision());
  same(row.canonicalDocument(),canonical(current.document().value()));same(row.documentSha256(),hash(current.document().value()));same(row.classification(),current.closure().classification());
  var artifact=one(artifacts.artifact(row.artifactId()));var binding=one(artifacts.binding(row.artifactId()));var profile=SimulationEvaluationPreparationProfile.profile(row.kind());
  same(row.tenantId(),artifact.tenantId());same(null,artifact.projectId());same(SimulationEvaluationPreparationProfile.artifactKind(row.kind()),artifact.artifactKind());same(current.original().reference(),StoredObjectReferences.decode(artifact.storedObject(),artifact.reference()));
  same(row.documentSha256(),artifact.contentSha256());same(row.classification(),artifact.classification());same(profile.artifactSchemaManifest(),artifact.schemaManifest());same("1.0.0",artifact.schemaVersion());
  same(row.tenantId(),binding.tenantId());same(null,binding.projectId());same("SOURCE",binding.resourceKind());same(current.source().source().sourceId(),binding.resourceId());
  same(profile.schemaRef(),binding.schemaRef());same(profile.schemaDocumentSha256(),binding.schemaDocumentSha256());same(artifact.accessSetId(),binding.accessSetId());
  var manifest=one(artifacts.manifest(artifact.accessSetId()));same(row.tenantId(),manifest.tenantId());same(null,manifest.projectId());same(binding.dependencyManifestSha256(),manifest.manifestSha256());
  same(current.closure().members(),manifests.verify(manifest,artifacts.members(artifact.accessSetId())).members());lineage.require(row.artifactId(),current.closure().members(),LIMITS);
  references.require(a,null,List.of(new PermissionProtocol.Basis(row.artifactId(),row.documentSha256())),PURPOSE);
  SimulationEvaluationPreparationEvidence.verify(a,row,mapper);
 }
 private JsonNode event(Registration row)throws Exception{return SimulationEvaluationPreparationEvidence.event(row);}
 private String requestHash(ApplicationSessionIdentity.Verified subject,String context,JsonNode request)throws Exception {
  var value=json.object().put("route","/api/simulation-evaluation-preparations").put("context_revision",context).put("session_id",subject.sessionId()).put("schema_manifest",schema.manifest());value.set("body",request);return hash(value);
 }
 private JsonNode readJson(String text)throws java.io.IOException{return schema.parse(text.getBytes(StandardCharsets.UTF_8));}
 private PermissionProtocol.Basis basis(JsonNode n){return new PermissionProtocol.Basis(n.required("version_id").textValue(),n.required("content_sha256").textValue());}
 private String canonical(JsonNode node)throws Exception{return new String(ContentReadAuthorization.canonicalBytes(node),StandardCharsets.UTF_8);}
 private String hash(JsonNode node)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(node));}
 private static Map<String,Object> values(EnterpriseAccess.Admission a){var v=new HashMap<String,Object>();v.put("userId",a.actorId());return v;}
 private static void context(EnterpriseAccess.Admission a,String context){if(!Objects.equals(context,a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static <T>T one(List<T> rows){if(rows.size()!=1)mismatch();return rows.get(0);}
 private static void same(Object expected,Object actual){if(!Objects.equals(expected,actual))mismatch();}
 private static void count(int count){if(count!=1)mismatch();}
 private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
 private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
