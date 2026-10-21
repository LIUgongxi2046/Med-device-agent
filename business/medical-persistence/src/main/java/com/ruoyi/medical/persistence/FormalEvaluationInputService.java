package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.FormalEvaluationInputProtocol.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.function.ThrowingSupplier;

/** Real SOURCE original -> independent typed input registration, without copying bytes or creating permission. */
@Component @ConditionalOnProperty(prefix="medical.formal-evaluation-input",name="enabled",havingValue="true")
public final class FormalEvaluationInputService {
 public static final String OPERATION="FORMAL-EVALUATION-INPUT-REGISTER",EVENT="formal.evaluation.input_registered";
 static final DependencyLineage.Limits LIMITS=new DependencyLineage.Limits(512,2048);
 private final EnterpriseAccess access;private final FormalEvaluationInputMapper mapper;private final SourceReadMapper sourceRows;
 private final SourceReadAuthority sources;private final ResourceReadAuthority resources;private final SourceUploadTransport transport;
 private final ArtifactRegistry registry;private final ArtifactRegistryMapper artifacts;private final DependencyLineage lineage;
 private final VersionResourceAuthority versions;private final DependencyManifestSchema manifests;private final PermissionSchema json;
 private final FormalEvaluationInputSchema documentSchema;private final FormalEvaluationInputWorkflowSchema schema;private final RetrievalPolicy deployment;
 private record Prepared(SourceReadProtocol.VerifiedSource source,Original original,List<ArtifactRegistryProtocol.MemberRow> members,
  JsonNode closure,PermissionProtocol.GrantRow registerGrant,String digest){}
 public FormalEvaluationInputService(EnterpriseAccess access,FormalEvaluationInputMapper mapper,SourceReadMapper sourceRows,
  SourceReadAuthority sources,ResourceReadAuthority resources,SourceUploadTransport transport,ArtifactRegistry registry,ArtifactRegistryMapper artifacts,
  DependencyLineage lineage,VersionResourceAuthority versions,DependencyManifestSchema manifests,PermissionSchema json,
  FormalEvaluationInputSchema documentSchema,FormalEvaluationInputWorkflowSchema schema,RetrievalPolicy deployment){
  this.access=access;this.mapper=mapper;this.sourceRows=sourceRows;this.sources=sources;this.resources=resources;this.transport=transport;
  this.registry=registry;this.artifacts=artifacts;this.lineage=lineage;this.versions=versions;this.manifests=manifests;this.json=json;
  this.documentSchema=documentSchema;this.schema=schema;this.deployment=deployment;
 }
 /** Bounded metadata-only discovery under the exact caller-selected 079 business purpose. No original GET or registration. */
 public JsonNode candidates(ApplicationSessionIdentity.Verified subject,String context,String purpose,String offset){
  FormalEvaluationInputWorkflowSchema.purpose(purpose);if(offset==null||!offset.matches("0|[1-9][0-9]{0,5}"))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  return access.execute(subject,a->ThrowingSupplier.of(()->{context(a,context);var v=values(a);v.put("purpose",purpose);v.put("offset",Integer.parseInt(offset));var rows=mapper.candidateVersions(v);if(rows.size()>21||new HashSet<>(rows).size()!=rows.size())bad();var items=json.object().arrayNode();
   for(String version:rows.subList(0,Math.min(20,rows.size()))){WireValues.uuid(version);try{var source=available(sourceRows.source(version));var request=json.object().put("schema_version","1.0.0").putNull("project_id").put("rights_revision",source.rightsRevision()).put("purpose",purpose);request.set("source",json.tree(new PermissionProtocol.Basis(version,source.originalSha256())));var checked=prepare(a,context,request,true,null);var item=json.object().put("source_id",checked.source().source().sourceId()).put("rights_revision",checked.source().rights().rightsRevision()).put("size_bytes",checked.original().sizeBytes().toString()).put("media_type",checked.original().mediaType()).put("content_validation","NOT_READ");item.set("source",request.required("source"));items.add(item);
    }catch(TenantAccessException denied){if(!Set.of(ErrorCode.ACCESS_DENIED,ErrorCode.OBJECT_UNAVAILABLE,ErrorCode.SOURCE_MISSING,ErrorCode.SOURCE_NOT_READY,ErrorCode.FILE_TOO_LARGE).contains(denied.code()))throw denied;}}
   var result=json.object().put("schema_version","1.0.0").put("purpose",purpose).put("offset",offset);result.set("items",items);if(rows.size()>20&&Integer.parseInt(offset)<=999979)result.put("next_offset",Integer.toString(Integer.parseInt(offset)+20));else result.putNull("next_offset");return result;
  }).get());
 }
 public JsonNode register(ApplicationSessionIdentity.Verified subject,String context,String key,JsonNode request)throws Exception{
  schema.validate("Register",request);FormalEvaluationInputWorkflowSchema.key(key);PermissionSchema.revision(context);
  Prepared initial=state(subject,context,request,true,null);byte[] raw=readOriginal(subject,context,request,initial,true,null);
  JsonNode document=documentSchema.document(raw);same(request.path("source").path("content_sha256").asText(),WireValues.sha256(raw));
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   var v=values(a);same(a.actorId(),one(mapper.lockActor(v)));v.put("sourceVersion",request.path("source").path("version_id").asText());same(v.get("sourceVersion"),one(mapper.lockSource(v)));
   Prepared current=prepare(a,context,request,true,null);same(initial.digest(),current.digest());String requestHash=schema.operationHash(a.context().tenant_id(),a.actorId(),request);
   v.put("key",key);var prior=mapper.operation(v);if(prior.size()>1)bad();
   if(!prior.isEmpty()){
    var operation=prior.get(0);if(!requestHash.equals(operation.requestSha256()))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
    v.put("artifactId",operation.targetId());var row=one(mapper.registration(v));same(key,row.idempotencyKey());same(a.actorId(),row.createdBy());same(schema.canonical(request),row.canonicalRequest());
    verify(a,row,current,document);return schema.parse(row.receiptJson().getBytes(StandardCharsets.UTF_8));
   }
   String artifact=UUID.randomUUID().toString(),set=UUID.randomUUID().toString(),operation=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString();
   var now=Objects.requireNonNull(mapper.databaseTime(v));String classification=current.source().source().classification();
   var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",a.context().tenant_id(),null,set,current.members());String dependencyJson=schema.canonical(schema.tree(manifest)),dependencyHash=schema.hash(schema.tree(manifest));
   v.put("now",now);v.put("classification",classification);v.put("projectId",null);v.put("accessId",set);v.put("dependencyJson",dependencyJson);v.put("dependencyHash",dependencyHash);v.put("memberCount",current.members().size());v.put("dependencySchema",manifests.manifestSha256());
   v.put("versionId",set);v.put("kind","DEPENDENCY_ACCESS_SET");v.put("hash",dependencyHash);count(mapper.insertVersion(v));count(mapper.insertAccessSet(v));count(mapper.insertManifest(v));
   for(var member:current.members()){v.put("member",member);count(mapper.insertMember(v));}
   v.put("versionId",artifact);v.put("kind","ARTIFACT");v.put("hash",current.original().sha256());count(mapper.insertVersion(v));
   v.put("artifactId",artifact);v.put("reference",current.original().reference());v.put("storedObject",StoredObjectReferences.encode(current.original().reference()));v.put("schemaHash",documentSchema.schemaSha256());v.put("schemaRef",FormalEvaluationInputSchema.REF);v.put("sourceId",current.source().source().sourceId());count(mapper.insertArtifact(v));count(mapper.insertBinding(v));
   v.put("upstream",current.source().source().sourceVersion());for(String consumer:List.of(set,artifact)){v.put("consumer",consumer);count(mapper.insertLineage(v));}
   var auth=json.object().put("identity_mode",a.bindingView().mode());auth.set("source",schema.tree(current.source()));auth.set("artifact_register",schema.tree(current.registerGrant()));auth.set("closure",current.closure());
   var receipt=receipt(a.context().tenant_id(),artifact,operation,a.actorId(),now,current,request);String receiptJson=schema.canonical(receipt);
   var row=new Registration(a.context().tenant_id(),null,artifact,current.source().source().sourceId(),current.source().source().sourceVersion(),current.source().rights().rightsRevision(),request.path("purpose").asText(),set,dependencyHash,current.original().sha256(),current.original().objectVersion(),FormalEvaluationInputSchema.REF,documentSchema.schemaSha256(),schema.canonical(document),schema.canonical(request),schema.hash(request),requestHash,key,context,subject.sessionId(),schema.canonical(auth),schema.hash(auth),receiptJson,operation,audit,"1.0.0",schema.manifest(),a.actorId(),now,classification);
   v.put("row",row);v.put("operationId",operation);v.put("requestHash",requestHash);v.put("response",receiptJson);v.put("operationManifest",schema.manifest());v.put("auditId",audit);v.put("payload",schema.canonical(event(row)));
   count(mapper.saveOperation(v));count(mapper.saveAudit(v));count(mapper.saveEvent(v));count(mapper.insertRegistration(v));
   same(row,one(mapper.registration(v)));verify(a,row,current,document);same(current.digest(),prepare(a,context,request,true,null).digest());return receipt;
  }).get());
 }
 @FunctionalInterface private interface Recheck{void require(EnterpriseAccess.Admission admission)throws Exception;}
 public static final class VerifiedInput{
  private final JsonNode document,receipt;private final Recheck recheck;
  private VerifiedInput(JsonNode document,JsonNode receipt,Recheck recheck){this.document=document.deepCopy();this.receipt=receipt.deepCopy();this.recheck=recheck;}
  public JsonNode document(){return document.deepCopy();}public JsonNode receipt(){return receipt.deepCopy();}
  public void requireCurrent(EnterpriseAccess.Admission admission)throws java.io.IOException{try{recheck.require(admission);}catch(java.io.IOException|RuntimeException failure){throw failure;}catch(Exception failure){throw new java.io.IOException("FORMAL_INPUT_RECHECK_FAILED",failure);}}
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String artifactId)throws Exception{
  return view(readVerified(subject,context,artifactId));
 }
 public VerifiedInput readVerified(ApplicationSessionIdentity.Verified subject,String context,String artifactId)throws Exception{return readVerified(EvidenceReadAccess.human(access,subject),context,artifactId);}
 VerifiedInput readVerified(EvidenceReadAccess subject,String context,String artifactId)throws Exception{
  WireValues.uuid(artifactId);PermissionSchema.revision(context);
  Registration row=subject.execute(a->ThrowingSupplier.of(()->{context(a,context);var v=values(a);v.put("artifactId",artifactId);var stored=available(mapper.registration(v));registry.requireRegistration(a,artifactId,requirement(stored.tenantId(),stored.sourceId(),stored.purpose()));return stored;}).get());
  return readRowVerified(subject,context,row);
 }
 /** Creator-only typed recovery of the original persisted request/receipt; no replay key or session substitution. */
 public JsonNode recover(ApplicationSessionIdentity.Verified subject,String context,String operationId)throws Exception{
  WireValues.uuid(operationId);PermissionSchema.revision(context);
  Registration row=access.execute(subject,a->ThrowingSupplier.of(()->{context(a,context);var v=values(a);v.put("creatorId",a.actorId());v.put("operationId",operationId);var operation=available(mapper.operationById(v));v.put("artifactId",operation.targetId());var stored=one(mapper.registration(v));same(a.actorId(),stored.createdBy());same(operationId,stored.operationId());registry.requireRegistration(a,stored.artifactId(),requirement(stored.tenantId(),stored.sourceId(),stored.purpose()));return stored;}).get());
  return readRow(subject,context,row);
 }
 private JsonNode readRow(ApplicationSessionIdentity.Verified subject,String context,Registration row)throws Exception{return view(readRowVerified(subject,context,row));}
 private JsonNode view(VerifiedInput proof){var result=json.object();result.set("receipt",proof.receipt());result.set("document",proof.document());return schema.validate("Read",result);}
 private VerifiedInput readRowVerified(ApplicationSessionIdentity.Verified subject,String context,Registration row)throws Exception{return readRowVerified(EvidenceReadAccess.human(access,subject),context,row);}
 private VerifiedInput readRowVerified(EvidenceReadAccess subject,String context,Registration row)throws Exception{
  subject.requireVersion(row.artifactId(),row.documentSha256());
  JsonNode request=schema.request(row.canonicalRequest().getBytes(StandardCharsets.UTF_8));var initial=state(subject,context,request,false,row.artifactId());
  JsonNode document=documentSchema.document(readOriginal(subject,context,request,initial,false,row.artifactId()));same(row.documentSha256(),schema.hash(document));
  Recheck recheck=a->{var v=values(a);v.put("artifactId",row.artifactId());same(row,one(mapper.registration(v)));var current=prepare(a,context,request,false,row.artifactId());same(initial.digest(),current.digest());verify(a,row,current,document);};
  return subject.execute(a->ThrowingSupplier.of(()->{recheck.require(a);return new VerifiedInput(document,schema.parse(row.receiptJson().getBytes(StandardCharsets.UTF_8)),recheck);}).get());
 }
 private Prepared state(ApplicationSessionIdentity.Verified subject,String context,JsonNode request,boolean writing,String artifact){return state(EvidenceReadAccess.human(access,subject),context,request,writing,artifact);}
 private Prepared state(EvidenceReadAccess subject,String context,JsonNode request,boolean writing,String artifact){if(writing&&subject.control())denied();subject.requireVersion(request.required("source").required("version_id").asText(),request.required("source").required("content_sha256").asText());return subject.execute(a->ThrowingSupplier.of(()->prepare(a,context,request,writing,artifact)).get());}
 private Prepared prepare(EnterpriseAccess.Admission a,String context,JsonNode request,boolean writing,String artifact)throws Exception{
  context(a,context);schema.validate("Register",request);String sourceVersion=request.path("source").path("version_id").asText(),rights=request.path("rights_revision").asText(),purpose=request.path("purpose").asText();
  var source=sources.require(a,new SourceReadProtocol.ReadRequirement(sourceVersion,rights,null,null,null,null,SourceReadProtocol.Use.REVIEW,purpose,deployment.environment(),deployment.region(),null));
  if(source.source().ownerProjectId()!=null||!"ENTERPRISE".equals(source.source().ownership()))denied();same(purpose,source.rights().purpose());same(request.path("source").path("content_sha256").asText(),source.source().originalSha256());
  var grant=writing?resources.require(a,scope(a.context().tenant_id(),source.source().sourceId()),"artifact.register",purpose):null;
  var gates=sourceRows.gates(sourceVersion);if(gates.isEmpty())notReady();for(var gate:gates)if(!"ACTIVE".equals(gate.lifecycle())||!"RECEIVED".equals(gate.uploadStatus())||!"APPROVED".equals(gate.approvalStatus()))notReady();
  var v=values(a);v.put("sourceVersion",sourceVersion);Original original=one(mapper.original(v));same(a.context().tenant_id(),original.tenantId());same(sourceVersion,original.sourceVersion());same(source.source().originalSha256(),original.sha256());
  if(original.sizeBytes()==null||original.sizeBytes().signum()<=0||original.sizeBytes().compareTo(BigInteger.valueOf(FormalEvaluationInputSchema.MAXIMUM_BYTES))>0)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  if(!Set.of("application/json","text/plain").contains(original.mediaType()))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  if(original.objectKey()==null||!original.objectKey().startsWith("tenants/"+original.tenantId()+"/")||original.objectKey().contains(".."))denied();StoredObjectReferences.encode(original.reference());
  var graph=lineage.inspect(sourceVersion,LIMITS);var root=graph.versions().get(sourceVersion);if(root==null||!"SOURCE_VERSION".equals(root.entityKind()))bad();same(original.sha256(),root.contentSha256());
  // A standalone input has exactly one business SOURCE. No inherited/hidden SOURCE or invented rights mapping.
  var actualSources=new HashSet<String>();for(var version:graph.versions().values()){
   if(version.projectId()!=null)denied();var rows=sourceRows.source(version.id());if(rows.size()>1)bad();
   if(!rows.isEmpty()){if(!"SOURCE_VERSION".equals(version.entityKind()))bad();actualSources.add(version.id());}
   if("SOURCE_VERSION".equals(version.entityKind())&&rows.isEmpty())bad();
  }if(!actualSources.equals(Set.of(sourceVersion)))bad();
  var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();for(String id:new TreeSet<>(graph.versions().keySet())){
   if(!graph.reachableFrom(id).contains(sourceVersion))bad(); // Ancestors without a provable SOURCE-to-rights relationship are unsupported.
   members.add(new ArtifactRegistryProtocol.MemberRow(id,rights));
  }
  var checked=versions.require(a,graph,purpose,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),Map.of());
  var closure=json.object();closure.set("versions",schema.tree(new TreeMap<>(graph.versions())));closure.set("members",schema.tree(members));closure.set("edges",schema.tree(graph.graph().edgeSet().stream().sorted(Comparator.comparing(SourceReadProtocol.ParentRow::consumerVersionId).thenComparing(SourceReadProtocol.ParentRow::upstreamVersionId).thenComparing(SourceReadProtocol.ParentRow::relationKind)).toList()));
  ArtifactRegistryProtocol.Registration registered=artifact==null?null:registry.requireRegistration(a,artifact,requirement(a.context().tenant_id(),source.source().sourceId(),purpose));
  var snapshot=json.object();snapshot.set("source",schema.tree(source));snapshot.set("gates",schema.tree(gates));snapshot.set("original",schema.tree(original));snapshot.set("closure",closure);snapshot.set("current_versions",schema.tree(new TreeMap<>(checked)));snapshot.set("register_grant",schema.tree(grant));snapshot.set("context",schema.tree(a.context()));snapshot.set("watermarks",schema.tree(a.watermarks()));snapshot.put("actor",a.actorId());
  if(registered!=null){snapshot.set("artifact",schema.tree(registered.artifact()));snapshot.set("binding",schema.tree(registered.binding()));snapshot.set("manifest",schema.tree(registered.manifest()));snapshot.set("artifact_grant",schema.tree(registered.artifactGrant()));snapshot.set("resource_grant",schema.tree(registered.resourceGrant()));}
  return new Prepared(source,original,List.copyOf(members),closure,grant,schema.hash(snapshot));
 }
 private byte[] readOriginal(ApplicationSessionIdentity.Verified subject,String context,JsonNode request,Prepared initial,boolean writing,String artifact)throws Exception{return readOriginal(EvidenceReadAccess.human(access,subject),context,request,initial,writing,artifact);}
 private byte[] readOriginal(EvidenceReadAccess subject,String context,JsonNode request,Prepared initial,boolean writing,String artifact)throws Exception{
  if(writing&&subject.control())denied();
  var original=initial.original();S3ObjectReader.Admission guard=()->same(initial.digest(),state(subject,context,request,writing,artifact).digest());
  return subject.readDocument(transport,original.tenantId(),FormalEvaluationInputSchema.MAXIMUM_BYTES,guard,(reader,current)->{
   try(var verified=reader.readVerified(original.reference(),current)){
    same(original.reference(),verified.reference());same(original.sha256(),verified.fullSha256());byte[] bytes=new byte[original.sizeBytes().intValueExact()];
    for(int start=0;start<bytes.length;){int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,bytes.length-start);System.arraycopy(verified.chunk(start,n),0,bytes,start,n);start+=n;}current.requireCurrent();return bytes;
   }
  });
 }
 private ArtifactRegistryProtocol.ReadRequirement requirement(String tenant,String sourceId,String purpose){return new ArtifactRegistryProtocol.ReadRequirement(scope(tenant,sourceId),"source.read",purpose,FormalEvaluationInputSchema.REF,documentSchema.schemaSha256(),documentSchema.schemaSha256(),LIMITS);}
 private JsonNode receipt(String tenant,String artifact,String operation,String actor,java.time.LocalDateTime now,Prepared current,JsonNode request){
  var value=json.object().put("schema_version","1.0.0").putNull("project_id").put("artifact_kind",FormalEvaluationInputSchema.ARTIFACT_KIND).put("operation_id",operation).put("rights_revision",current.source().rights().rightsRevision()).put("purpose",request.path("purpose").asText()).put("created_by",actor).put("created_at",now.toInstant(java.time.ZoneOffset.UTC).toString()).put("input_schema_ref",FormalEvaluationInputSchema.REF).put("input_schema_sha256",documentSchema.schemaSha256());
  value.set("artifact",json.tree(new PermissionProtocol.Basis(artifact,current.original().sha256())));value.set("source",request.path("source"));return schema.validate("Receipt",value);
 }
 private void verify(EnterpriseAccess.Admission a,Registration row,Prepared current,JsonNode document)throws Exception{
  same(a.context().tenant_id(),row.tenantId());same(null,row.projectId());same("1.0.0",row.schemaVersion());same(schema.manifest(),row.schemaManifest());same(FormalEvaluationInputSchema.REF,row.schemaRef());same(documentSchema.schemaSha256(),row.documentSchemaSha256());
  FormalEvaluationInputWorkflowSchema.key(row.idempotencyKey());PermissionSchema.revision(row.requestContext());WireValues.uuid(row.requestSession());WireValues.uuid(row.auditId());
  JsonNode request=schema.request(row.canonicalRequest().getBytes(StandardCharsets.UTF_8));same(row.canonicalRequest(),schema.canonical(request));same(row.requestBodySha256(),schema.hash(request));same(row.operationRequestSha256(),schema.operationHash(row.tenantId(),row.createdBy(),request));
  same(row.sourceVersion(),request.path("source").path("version_id").asText());same(row.documentSha256(),request.path("source").path("content_sha256").asText());same(row.rightsRevision(),request.path("rights_revision").asText());same(row.purpose(),request.path("purpose").asText());
  same(row.sourceId(),current.source().source().sourceId());same(row.sourceVersion(),current.source().source().sourceVersion());same(row.rightsRevision(),current.source().rights().rightsRevision());same(row.objectVersion(),current.original().objectVersion());same(row.classification(),current.source().source().classification());
  same(row.canonicalDocument(),schema.canonical(document));same(row.documentSha256(),schema.hash(document));
  JsonNode auth=schema.parse(row.authorizationJson().getBytes(StandardCharsets.UTF_8));same(row.authorizationJson(),schema.canonical(auth));same(row.authorizationSha256(),schema.hash(auth));same(current.closure(),auth.path("closure"));
  var historic=schema.bind(auth.path("source"),SourceReadProtocol.VerifiedSource.class);same(current.source().source(),historic.source());same(current.source().rights(),historic.rights());
  requireHistoricalGrant(row,auth.path("identity_mode").asText(),historic.sourceGrant(),"source.read");requireHistoricalGrant(row,auth.path("identity_mode").asText(),schema.bind(auth.path("artifact_register"),PermissionProtocol.GrantRow.class),"artifact.register");
  var registered=registry.requireRegistration(a,row.artifactId(),requirement(row.tenantId(),row.sourceId(),row.purpose()));var artifact=registered.artifact();
  same(FormalEvaluationInputSchema.ARTIFACT_KIND,artifact.artifactKind());same(current.original().reference(),StoredObjectReferences.decode(artifact.storedObject(),artifact.reference()));same(row.documentSha256(),artifact.contentSha256());same(row.classification(),artifact.classification());same(row.accessSetId(),artifact.accessSetId());same(row.dependencySha256(),registered.binding().dependencyManifestSha256());same(current.members(),registered.manifest().members());
  // Both derived graph roots must still have the full original ancestor set.
  requireDerivedLineage(row.artifactId(),row.sourceVersion(),current.members());requireDerivedLineage(row.accessSetId(),row.sourceVersion(),current.members());
  var expected=receipt(row.tenantId(),row.artifactId(),row.operationId(),row.createdBy(),row.createdAt(),current,request);JsonNode receipt=schema.parse(row.receiptJson().getBytes(StandardCharsets.UTF_8));same(expected,receipt);same(row.receiptJson(),schema.canonical(receipt));
  var v=values(a);v.put("creatorId",row.createdBy());v.put("operationId",row.operationId());v.put("key",row.idempotencyKey());v.put("artifactId",row.artifactId());v.put("auditId",row.auditId());v.put("now",row.createdAt());v.put("operationManifest",schema.manifest());v.put("classification",row.classification());
  var operation=one(mapper.operationById(v));same(row.operationId(),operation.operationId());same(row.operationRequestSha256(),operation.requestSha256());same("COMPLETED",operation.state());same(Integer.valueOf(201),operation.httpStatus());same(row.artifactId(),operation.targetId());same("1.0.0",operation.schemaVersion());same(schema.manifest(),operation.schemaManifestSha256());same(receipt,schema.parse(operation.responseRef().getBytes(StandardCharsets.UTF_8)));
  same(event(row),schema.parse(one(mapper.audit(v)).getBytes(StandardCharsets.UTF_8)));same(event(row),schema.parse(one(mapper.event(v)).getBytes(StandardCharsets.UTF_8)));
 }
 private void requireDerivedLineage(String consumer,String source,List<ArtifactRegistryProtocol.MemberRow> members){
  var graph=lineage.require(consumer,members,LIMITS);
  // The first contract supports only one standalone SOURCE; exact relation identity is part of registration.
  same(Set.of(new SourceReadProtocol.ParentRow(consumer,source,"FORMAL_INPUT_SOURCE")),graph.graph().edgeSet());
 }
 private void requireHistoricalGrant(Registration row,String mode,PermissionProtocol.GrantRow grant,String action)throws Exception{
  if(grant==null||new PermissionAuthority(json).findResource(scope(row.tenantId(),row.sourceId()),row.createdBy(),mode,action,row.purpose(),List.of(grant),row.createdAt()).isEmpty())bad();
 }
 private JsonNode event(Registration row)throws Exception{
  var event=json.object().put("tenant_id",row.tenantId()).put("artifact_id",row.artifactId()).put("operation_id",row.operationId()).put("audit_id",row.auditId()).put("canonical_request",row.canonicalRequest()).put("request_body_sha256",row.requestBodySha256()).put("operation_request_sha256",row.operationRequestSha256()).put("idempotency_key",row.idempotencyKey()).put("request_context",row.requestContext()).put("request_session",row.requestSession()).put("authorization_sha256",row.authorizationSha256()).put("document_sha256",row.documentSha256()).put("dependency_sha256",row.dependencySha256()).put("workflow_schema_sha256",row.schemaManifest()).put("classification",row.classification());event.set("receipt",schema.parse(row.receiptJson().getBytes(StandardCharsets.UTF_8)));return event;
 }
 static Map<String,Object> values(EnterpriseAccess.Admission a){var v=new HashMap<String,Object>();v.put("userId",a.actorId());return v;}
 private static PermissionProtocol.Scope scope(String tenant,String source){return new PermissionProtocol.Scope(tenant,null,"SOURCE",source);}
 private static void context(EnterpriseAccess.Admission a,String expected){if(!Objects.equals(expected,a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static <T>T available(List<T> rows){if(rows.isEmpty())throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return one(rows);}
 private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}
 private static void same(Object expected,Object actual){if(!Objects.equals(expected,actual))bad();}
 private static void count(int count){if(count!=1)bad();}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
 private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
 private static void notReady(){throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);}
}
