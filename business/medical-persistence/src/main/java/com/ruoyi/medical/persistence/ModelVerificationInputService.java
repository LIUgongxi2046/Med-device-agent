package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.ModelVerificationInputProtocol.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.function.ThrowingSupplier;

/** Real SOURCE original -> independent typed input registration, without copying bytes or creating permission. */
@Component @ConditionalOnProperty(prefix="medical.model-verification-input",name="enabled",havingValue="true")
public final class ModelVerificationInputService {
 public static final String OPERATION="MODEL-VERIFICATION-INPUT-PREPARE",EVENT="model.verification.input_prepared";
 static final DependencyLineage.Limits LIMITS=new DependencyLineage.Limits(512,2048);
 private final EnterpriseAccess access;private final ModelVerificationInputMapper mapper;private final SourceReadMapper sourceRows;
 private final SourceReadAuthority sources;private final ResourceReadAuthority resources;private final SourceUploadTransport transport;
 private final ArtifactRegistry registry;private final ArtifactRegistryMapper artifacts;private final DependencyLineage lineage;
 private final VersionResourceAuthority versions;private final DependencyManifestSchema manifests;private final PermissionSchema json;
 private final ModelConfigurationService models;private final ProjectService projects;private final ScopeSelectionAuthority selections;private final ScopeSnapshotStore snapshots;
 private final ModelVerificationInputSchema documentSchema;private final ModelVerificationInputWorkflowSchema schema;private final DocumentUploadPolicy deployment;
 private record Prepared(SourceReadProtocol.VerifiedSource source,Original original,List<ArtifactRegistryProtocol.MemberRow> members,
  JsonNode closure,PermissionProtocol.GrantRow registerGrant,JsonNode model,ProjectProtocol.VerifiedProfile profile,ScopeSelectionAuthority.Selection selection,PermissionProtocol.GrantRow verifyGrant,String digest){}
 public ModelVerificationInputService(EnterpriseAccess access,ModelVerificationInputMapper mapper,SourceReadMapper sourceRows,
  SourceReadAuthority sources,ResourceReadAuthority resources,SourceUploadTransport transport,ArtifactRegistry registry,ArtifactRegistryMapper artifacts,
  DependencyLineage lineage,VersionResourceAuthority versions,DependencyManifestSchema manifests,PermissionSchema json,
  ModelVerificationInputSchema documentSchema,ModelVerificationInputWorkflowSchema schema,DocumentUploadPolicy deployment,ModelConfigurationService models,ProjectService projects,ScopeSelectionAuthority selections,ScopeSnapshotStore snapshots){
  this.access=access;this.mapper=mapper;this.sourceRows=sourceRows;this.sources=sources;this.resources=resources;this.transport=transport;
  this.registry=registry;this.artifacts=artifacts;this.lineage=lineage;this.versions=versions;this.manifests=manifests;this.json=json;
  this.models=models;this.projects=projects;this.selections=selections;this.snapshots=snapshots;this.documentSchema=documentSchema;this.schema=schema;this.deployment=deployment;
 }
 public JsonNode register(ApplicationSessionIdentity.Verified subject,String context,String key,JsonNode request)throws Exception{
  schema.request(schema.canonical(request).getBytes(StandardCharsets.UTF_8));ModelVerificationInputWorkflowSchema.key(key);PermissionSchema.revision(context);
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
   String project=request.path("project_id").asText();
   var snapshot=snapshots.saveForModelVerification(a,project,request.path("profile_revision").asText(),scope(request),current.model().required("config_id").asText(),request.path("business_purpose").asText(),deployment.environment(),deployment.region(),LIMITS);
   var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",a.context().tenant_id(),project,set,current.members());String dependencyJson=schema.canonical(schema.tree(manifest)),dependencyHash=schema.hash(schema.tree(manifest));
   v.put("now",now);v.put("classification",classification);v.put("projectId",project);v.put("accessId",set);v.put("dependencyJson",dependencyJson);v.put("dependencyHash",dependencyHash);v.put("memberCount",current.members().size());v.put("dependencySchema",manifests.manifestSha256());
   v.put("versionId",set);v.put("kind","DEPENDENCY_ACCESS_SET");v.put("hash",dependencyHash);count(mapper.insertVersion(v));count(mapper.insertAccessSet(v));count(mapper.insertManifest(v));
   for(var member:current.members()){v.put("member",member);count(mapper.insertMember(v));}
   v.put("versionId",artifact);v.put("kind","ARTIFACT");v.put("hash",current.original().sha256());count(mapper.insertVersion(v));
   v.put("artifactId",artifact);v.put("reference",current.original().reference());v.put("storedObject",StoredObjectReferences.encode(current.original().reference()));v.put("schemaHash",documentSchema.schemaSha256());v.put("artifactManifest",deployment.artifactSchemaManifest());v.put("schemaRef",ModelVerificationInputSchema.REF);v.put("sourceId",current.source().source().sourceId());count(mapper.insertArtifact(v));count(mapper.insertBinding(v));
   v.put("upstream",current.source().source().sourceVersion());for(String consumer:List.of(set,artifact)){v.put("consumer",consumer);count(mapper.insertLineage(v));}
   var auth=json.object().put("identity_mode",a.bindingView().mode());auth.set("source",schema.tree(current.source()));auth.set("artifact_register",schema.tree(current.registerGrant()));auth.set("closure",current.closure());
   auth.set("model_verify",schema.tree(current.verifyGrant()));auth.set("model",current.model());auth.set("profile",schema.tree(current.profile()));auth.set("selection",schema.tree(current.selection()));
   var snapshotBasis=new PermissionProtocol.Basis(snapshot.snapshot().snapshotId(),snapshot.snapshot().manifestHash());
   var receipt=receipt(a.context().tenant_id(),artifact,operation,a.actorId(),now,current,request,snapshotBasis);String receiptJson=schema.canonical(receipt);
   var row=new Registration(a.context().tenant_id(),project,artifact,current.source().source().sourceId(),current.source().source().sourceVersion(),current.source().rights().rightsRevision(),request.path("business_purpose").asText(),set,dependencyHash,current.original().sha256(),current.original().objectVersion(),ModelVerificationInputSchema.REF,documentSchema.schemaSha256(),schema.canonical(document),schema.canonical(request),schema.hash(request),requestHash,key,context,subject.sessionId(),schema.canonical(auth),schema.hash(auth),receiptJson,operation,audit,"1.0.0",schema.manifest(),a.actorId(),now,classification,snapshotBasis.version_id(),snapshotBasis.content_sha256(),request.path("profile_revision").asText(),request.path("model_config_revision").asText(),current.model().required("config_id").asText(),deployment.artifactSchemaManifest());
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
 public VerifiedInput readVerified(ApplicationSessionIdentity.Verified subject,String context,String artifactId)throws Exception{
  deployment.requireConfigured();WireValues.uuid(artifactId);PermissionSchema.revision(context);
  Registration row=access.execute(subject,a->ThrowingSupplier.of(()->{context(a,context);var v=values(a);v.put("artifactId",artifactId);var stored=available(mapper.registration(v));registry.requireRegistration(a,artifactId,requirement(stored.tenantId(),stored.projectId(),stored.sourceId(),stored.purpose()));return stored;}).get());
  return readRowVerified(subject,context,row);
 }
 /** Creator-only typed recovery of the original persisted request/receipt; no replay key or session substitution. */
 public JsonNode recover(ApplicationSessionIdentity.Verified subject,String context,String operationId)throws Exception{
  deployment.requireConfigured();WireValues.uuid(operationId);PermissionSchema.revision(context);
  Registration row=access.execute(subject,a->ThrowingSupplier.of(()->{context(a,context);var v=values(a);v.put("creatorId",a.actorId());v.put("operationId",operationId);var operation=available(mapper.operationById(v));v.put("artifactId",operation.targetId());var stored=one(mapper.registration(v));same(a.actorId(),stored.createdBy());same(operationId,stored.operationId());registry.requireRegistration(a,stored.artifactId(),requirement(stored.tenantId(),stored.projectId(),stored.sourceId(),stored.purpose()));return stored;}).get());
  var result=(com.fasterxml.jackson.databind.node.ObjectNode)readRow(subject,context,row);result.set("original_request",schema.request(row.canonicalRequest().getBytes(StandardCharsets.UTF_8)));result.put("original_idempotency_key",row.idempotencyKey());result.put("original_context_revision",row.requestContext());return schema.validate("Recovery",result);
 }
 private JsonNode readRow(ApplicationSessionIdentity.Verified subject,String context,Registration row)throws Exception{return view(readRowVerified(subject,context,row));}
 private JsonNode view(VerifiedInput proof){var result=json.object();result.set("receipt",proof.receipt());result.set("document",proof.document());return schema.validate("Read",result);}
 private VerifiedInput readRowVerified(ApplicationSessionIdentity.Verified subject,String context,Registration row)throws Exception{
  JsonNode request=schema.request(row.canonicalRequest().getBytes(StandardCharsets.UTF_8));var initial=state(subject,context,request,false,row.artifactId());
  JsonNode document=documentSchema.document(readOriginal(subject,context,request,initial,false,row.artifactId()));same(row.documentSha256(),schema.hash(document));
  Recheck recheck=a->{var v=values(a);v.put("artifactId",row.artifactId());same(row,one(mapper.registration(v)));var current=prepare(a,context,request,false,row.artifactId());same(initial.digest(),current.digest());verify(a,row,current,document);};
  return access.execute(subject,a->ThrowingSupplier.of(()->{recheck.require(a);return new VerifiedInput(document,schema.parse(row.receiptJson().getBytes(StandardCharsets.UTF_8)),recheck);}).get());
 }
 private Prepared state(ApplicationSessionIdentity.Verified subject,String context,JsonNode request,boolean writing,String artifact){return access.execute(subject,a->ThrowingSupplier.of(()->prepare(a,context,request,writing,artifact)).get());}
 private Prepared prepare(EnterpriseAccess.Admission a,String context,JsonNode request,boolean writing,String artifact)throws Exception{
  context(a,context);deployment.requireConfigured();schema.validate("Register",request);String project=request.path("project_id").asText();String sourceVersion=request.path("source").path("version_id").asText(),rights=request.path("rights_revision").asText(),purpose=request.path("business_purpose").asText();
  var model=models.read(a,request.path("model_config_revision").asText()).required("data");
  same(project,model.required("project_id").asText());same(request.path("model_config_revision").asText(),model.required("current_model_revision").asText());
  var verifyGrant=resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"CONFIGURATION",model.required("config_id").asText()),"model.verify","MODEL_VERIFICATION");
  var profile=projects.fixedProfile(a,project,request.path("profile_revision").asText(),purpose);if(!"ACTIVE".equals(profile.project().status()))denied();
  var selection=selections.require(a,project,scope(request),purpose,deployment.environment(),deployment.region(),LIMITS);
  if(selection.sources().size()>deployment.maximumSources())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  var matches=selection.sources().stream().filter(item->sourceVersion.equals(item.source().sourceVersion())).toList();var source=one(matches);
  if(!project.equals(source.source().ownerProjectId())||!"PROJECT".equals(source.source().ownership()))denied();
  same(rights,source.rights().rightsRevision());same(purpose,source.rights().purpose());same(request.path("source").path("content_sha256").asText(),source.source().originalSha256());
  var grant=writing?resources.require(a,scope(a.context().tenant_id(),project,source.source().sourceId()),"artifact.register",purpose):null;
  var gates=sourceRows.gates(sourceVersion);if(gates.isEmpty())notReady();for(var gate:gates)if(!"ACTIVE".equals(gate.lifecycle())||!"RECEIVED".equals(gate.uploadStatus())||!"APPROVED".equals(gate.approvalStatus()))notReady();
  var v=values(a);v.put("sourceVersion",sourceVersion);Original original=one(mapper.original(v));same(a.context().tenant_id(),original.tenantId());same(sourceVersion,original.sourceVersion());same(source.source().originalSha256(),original.sha256());
  if(original.sizeBytes()==null||original.sizeBytes().signum()<=0||original.sizeBytes().compareTo(BigInteger.valueOf(ModelVerificationInputSchema.MAXIMUM_BYTES))>0||original.sizeBytes().compareTo(deployment.maximumBytes())>0)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  if(!Set.of("application/json","text/plain").contains(original.mediaType()))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  if(original.objectKey()==null||!original.objectKey().startsWith("tenants/"+original.tenantId()+"/")||original.objectKey().contains(".."))denied();StoredObjectReferences.encode(original.reference());
  var graph=lineage.inspect(sourceVersion,LIMITS);var root=graph.versions().get(sourceVersion);if(root==null||!"SOURCE_VERSION".equals(root.entityKind()))bad();same(original.sha256(),root.contentSha256());
  // A standalone input has exactly one business SOURCE. No inherited/hidden SOURCE or invented rights mapping.
  var actualSources=new HashSet<String>();for(var version:graph.versions().values()){
   if(version.projectId()!=null&&!project.equals(version.projectId()))denied();var rows=sourceRows.source(version.id());if(rows.size()>1)bad();
   if(!rows.isEmpty()){if(!"SOURCE_VERSION".equals(version.entityKind()))bad();actualSources.add(version.id());}
   if("SOURCE_VERSION".equals(version.entityKind())&&rows.isEmpty())bad();
  }if(!actualSources.equals(Set.of(sourceVersion)))bad();
  var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();for(String id:new TreeSet<>(graph.versions().keySet())){
   if(!graph.reachableFrom(id).contains(sourceVersion))bad(); // Ancestors without a provable SOURCE-to-rights relationship are unsupported.
   members.add(new ArtifactRegistryProtocol.MemberRow(id,rights));
  }
  var checked=versions.require(a,graph,purpose,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),Map.of());
  var closure=json.object();closure.set("versions",schema.tree(new TreeMap<>(graph.versions())));closure.set("members",schema.tree(members));closure.set("edges",schema.tree(graph.graph().edgeSet().stream().sorted(Comparator.comparing(SourceReadProtocol.ParentRow::consumerVersionId).thenComparing(SourceReadProtocol.ParentRow::upstreamVersionId).thenComparing(SourceReadProtocol.ParentRow::relationKind)).toList()));
  if(artifact!=null){v.put("artifactId",artifact);var stored=one(mapper.registration(v));same(project,stored.projectId());same(request.path("profile_revision").asText(),stored.profileRevision());same(model.required("config_id").asText(),stored.configId());snapshots.readForModelVerification(a,project,stored.profileRevision(),scope(request),stored.configId(),purpose,deployment.environment(),deployment.region(),LIMITS,new PermissionProtocol.Basis(stored.snapshotId(),stored.snapshotSha256()));}
  ArtifactRegistryProtocol.Registration registered=artifact==null?null:registry.requireRegistration(a,artifact,requirement(a.context().tenant_id(),project,source.source().sourceId(),purpose));
  var snapshot=json.object();snapshot.set("source",schema.tree(source));snapshot.set("gates",schema.tree(gates));snapshot.set("original",schema.tree(original));snapshot.set("closure",closure);snapshot.set("current_versions",schema.tree(new TreeMap<>(checked)));snapshot.set("register_grant",schema.tree(grant));snapshot.set("context",schema.tree(a.context()));snapshot.set("watermarks",schema.tree(a.watermarks()));snapshot.put("actor",a.actorId());
  if(registered!=null){snapshot.set("artifact",schema.tree(registered.artifact()));snapshot.set("binding",schema.tree(registered.binding()));snapshot.set("manifest",schema.tree(registered.manifest()));snapshot.set("artifact_grant",schema.tree(registered.artifactGrant()));snapshot.set("resource_grant",schema.tree(registered.resourceGrant()));}
  snapshot.set("model",model);snapshot.set("verify_grant",schema.tree(verifyGrant));snapshot.set("profile",schema.tree(profile));snapshot.set("selection",schema.tree(selection));
  return new Prepared(source,original,List.copyOf(members),closure,grant,model,profile,selection,verifyGrant,schema.hash(snapshot));
 }
 private byte[] readOriginal(ApplicationSessionIdentity.Verified subject,String context,JsonNode request,Prepared initial,boolean writing,String artifact)throws Exception{
  var original=initial.original();S3ObjectReader.Admission guard=()->same(initial.digest(),state(subject,context,request,writing,artifact).digest());
  return transport.readDocument(subject,original.tenantId(),ModelVerificationInputSchema.MAXIMUM_BYTES,guard,(reader,current)->{
   try(var verified=reader.readVerified(original.reference(),current)){
    same(original.reference(),verified.reference());same(original.sha256(),verified.fullSha256());byte[] bytes=new byte[original.sizeBytes().intValueExact()];
    for(int start=0;start<bytes.length;){int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,bytes.length-start);System.arraycopy(verified.chunk(start,n),0,bytes,start,n);start+=n;}current.requireCurrent();return bytes;
   }
  });
 }
 private ArtifactRegistryProtocol.ReadRequirement requirement(String tenant,String project,String sourceId,String purpose){return new ArtifactRegistryProtocol.ReadRequirement(scope(tenant,project,sourceId),"source.read",purpose,ModelVerificationInputSchema.REF,documentSchema.schemaSha256(),deployment.artifactSchemaManifest(),LIMITS);}
 private JsonNode receipt(String tenant,String artifact,String operation,String actor,java.time.LocalDateTime now,Prepared current,JsonNode request,PermissionProtocol.Basis snapshotBasis)throws Exception{
  var value=json.object().put("schema_version","1.0.0").put("project_id",request.path("project_id").asText()).put("artifact_kind",ModelVerificationInputSchema.ARTIFACT_KIND).put("operation_id",operation).put("rights_revision",current.source().rights().rightsRevision()).put("business_purpose",request.path("business_purpose").asText()).put("created_by",actor).put("created_at",now.toInstant(java.time.ZoneOffset.UTC).toString()).put("input_schema_ref",ModelVerificationInputSchema.REF).put("input_schema_sha256",documentSchema.schemaSha256());
  value.set("artifact",json.tree(new PermissionProtocol.Basis(artifact,current.original().sha256())));value.set("source",request.path("source"));
  String range=ObjectContentPaging.rangeDigest(ObjectContentPaging.objectDigest(current.original().reference()),0,current.original().sizeBytes().longValueExact());
  var exact=json.object().put("artifact_id",artifact).put("sha256",current.original().sha256()).put("schema_ref",ModelVerificationInputSchema.REF);exact.set("coverage",json.object().put("selection_digest",range).put("delivered_digest",range).put("complete",true).putNull("next_cursor"));value.set("exact_input",exact);value.set("scope_snapshot_basis",json.tree(snapshotBasis));value.put("profile_revision",request.path("profile_revision").asText());value.put("model_config_revision",request.path("model_config_revision").asText());value.put("request_body_sha256",uncheckedHash(request));return schema.validate("Receipt",value);
 }
 private void verify(EnterpriseAccess.Admission a,Registration row,Prepared current,JsonNode document)throws Exception{
  same(a.context().tenant_id(),row.tenantId());same(current.source().source().ownerProjectId(),row.projectId());same(deployment.artifactSchemaManifest(),row.artifactManifest());same("1.0.0",row.schemaVersion());same(schema.manifest(),row.schemaManifest());same(ModelVerificationInputSchema.REF,row.schemaRef());same(documentSchema.schemaSha256(),row.documentSchemaSha256());
  ModelVerificationInputWorkflowSchema.key(row.idempotencyKey());PermissionSchema.revision(row.requestContext());WireValues.uuid(row.requestSession());WireValues.uuid(row.auditId());
  JsonNode request=schema.request(row.canonicalRequest().getBytes(StandardCharsets.UTF_8));same(row.canonicalRequest(),schema.canonical(request));same(row.requestBodySha256(),schema.hash(request));same(row.operationRequestSha256(),schema.operationHash(row.tenantId(),row.createdBy(),request));
  same(row.projectId(),request.path("project_id").asText());same(row.profileRevision(),request.path("profile_revision").asText());same(row.modelRevision(),request.path("model_config_revision").asText());
  same(row.sourceVersion(),request.path("source").path("version_id").asText());same(row.documentSha256(),request.path("source").path("content_sha256").asText());same(row.rightsRevision(),request.path("rights_revision").asText());same(row.purpose(),request.path("business_purpose").asText());
  same(row.sourceId(),current.source().source().sourceId());same(row.sourceVersion(),current.source().source().sourceVersion());same(row.rightsRevision(),current.source().rights().rightsRevision());same(row.objectVersion(),current.original().objectVersion());same(row.classification(),current.source().source().classification());
  same(row.canonicalDocument(),schema.canonical(document));same(row.documentSha256(),schema.hash(document));
  JsonNode auth=schema.parse(row.authorizationJson().getBytes(StandardCharsets.UTF_8));same(row.authorizationJson(),schema.canonical(auth));same(row.authorizationSha256(),schema.hash(auth));same(current.closure(),auth.path("closure"));
  var historic=schema.bind(auth.path("source"),SourceReadProtocol.VerifiedSource.class);same(current.source().source(),historic.source());same(current.source().rights(),historic.rights());
  requireHistoricalGrant(row,auth.path("identity_mode").asText(),historic.sourceGrant(),"source.read");requireHistoricalGrant(row,auth.path("identity_mode").asText(),schema.bind(auth.path("artifact_register"),PermissionProtocol.GrantRow.class),"artifact.register");
  same(row.configId(),current.model().required("config_id").asText());
  var originalVerify=schema.bind(auth.path("model_verify"),PermissionProtocol.GrantRow.class);
  if(new PermissionAuthority(json).findResource(new PermissionProtocol.Scope(row.tenantId(),row.projectId(),"CONFIGURATION",row.configId()),row.createdBy(),auth.path("identity_mode").asText(),"model.verify","MODEL_VERIFICATION",List.of(originalVerify),row.createdAt()).isEmpty())bad();
  snapshots.readForModelVerification(a,row.projectId(),row.profileRevision(),scope(request),row.configId(),row.purpose(),deployment.environment(),deployment.region(),LIMITS,new PermissionProtocol.Basis(row.snapshotId(),row.snapshotSha256()));
  var registered=registry.requireRegistration(a,row.artifactId(),requirement(row.tenantId(),row.projectId(),row.sourceId(),row.purpose()));var artifact=registered.artifact();
  same(ModelVerificationInputSchema.ARTIFACT_KIND,artifact.artifactKind());same(current.original().reference(),StoredObjectReferences.decode(artifact.storedObject(),artifact.reference()));same(row.documentSha256(),artifact.contentSha256());same(row.classification(),artifact.classification());same(row.accessSetId(),artifact.accessSetId());same(row.dependencySha256(),registered.binding().dependencyManifestSha256());same(current.members(),registered.manifest().members());
  // Both derived graph roots must still have the full original ancestor set.
  requireDerivedLineage(row.artifactId(),row.sourceVersion(),current.members());requireDerivedLineage(row.accessSetId(),row.sourceVersion(),current.members());
  var expected=receipt(row.tenantId(),row.artifactId(),row.operationId(),row.createdBy(),row.createdAt(),current,request,new PermissionProtocol.Basis(row.snapshotId(),row.snapshotSha256()));JsonNode receipt=schema.parse(row.receiptJson().getBytes(StandardCharsets.UTF_8));same(expected,receipt);same(row.receiptJson(),schema.canonical(receipt));
  var v=values(a);v.put("creatorId",row.createdBy());v.put("operationId",row.operationId());v.put("key",row.idempotencyKey());v.put("artifactId",row.artifactId());v.put("auditId",row.auditId());v.put("now",row.createdAt());v.put("operationManifest",schema.manifest());v.put("classification",row.classification());v.put("projectId",row.projectId());
  var operation=one(mapper.operationById(v));same(row.operationId(),operation.operationId());same(row.operationRequestSha256(),operation.requestSha256());same("COMPLETED",operation.state());same(Integer.valueOf(201),operation.httpStatus());same(row.artifactId(),operation.targetId());same("1.0.0",operation.schemaVersion());same(schema.manifest(),operation.schemaManifestSha256());same(receipt,schema.parse(operation.responseRef().getBytes(StandardCharsets.UTF_8)));
  same(event(row),schema.parse(one(mapper.audit(v)).getBytes(StandardCharsets.UTF_8)));same(event(row),schema.parse(one(mapper.event(v)).getBytes(StandardCharsets.UTF_8)));
 }
 private void requireDerivedLineage(String consumer,String source,List<ArtifactRegistryProtocol.MemberRow> members){
  var graph=lineage.require(consumer,members,LIMITS);
  // The first contract supports only one standalone SOURCE; exact relation identity is part of registration.
  same(Set.of(new SourceReadProtocol.ParentRow(consumer,source,"MODEL_VERIFICATION_INPUT_SOURCE")),graph.graph().edgeSet());
 }
 private void requireHistoricalGrant(Registration row,String mode,PermissionProtocol.GrantRow grant,String action)throws Exception{
  if(grant==null||new PermissionAuthority(json).findResource(scope(row.tenantId(),row.projectId(),row.sourceId()),row.createdBy(),mode,action,row.purpose(),List.of(grant),row.createdAt()).isEmpty())bad();
 }
 private JsonNode event(Registration row)throws Exception{
  var event=json.object().put("tenant_id",row.tenantId()).put("artifact_id",row.artifactId()).put("operation_id",row.operationId()).put("audit_id",row.auditId()).put("canonical_request",row.canonicalRequest()).put("request_body_sha256",row.requestBodySha256()).put("operation_request_sha256",row.operationRequestSha256()).put("idempotency_key",row.idempotencyKey()).put("request_context",row.requestContext()).put("request_session",row.requestSession()).put("authorization_sha256",row.authorizationSha256()).put("document_sha256",row.documentSha256()).put("dependency_sha256",row.dependencySha256()).put("workflow_schema_sha256",row.schemaManifest()).put("classification",row.classification());event.set("receipt",schema.parse(row.receiptJson().getBytes(StandardCharsets.UTF_8)));return event;
 }
 private CommonProtocol.SourceScope scope(JsonNode request)throws Exception{return schema.bind(request.required("scope_selection"),CommonProtocol.SourceScope.class);}
 private String uncheckedHash(JsonNode value){try{return schema.hash(value);}catch(Exception e){throw new IllegalStateException(e);}}
 static Map<String,Object> values(EnterpriseAccess.Admission a){var v=new HashMap<String,Object>();v.put("userId",a.actorId());return v;}
 private static PermissionProtocol.Scope scope(String tenant,String project,String source){return new PermissionProtocol.Scope(tenant,project,"SOURCE",source);}
 private static void context(EnterpriseAccess.Admission a,String expected){if(!Objects.equals(expected,a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static <T>T available(List<T> rows){if(rows.isEmpty())throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return one(rows);}
 private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}
 private static void same(Object expected,Object actual){if(!Objects.equals(expected,actual))bad();}
 private static void count(int count){if(count!=1)bad();}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
 private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
 private static void notReady(){throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);}
}
