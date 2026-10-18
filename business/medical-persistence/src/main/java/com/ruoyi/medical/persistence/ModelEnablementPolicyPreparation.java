package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.ModelEnablementPolicyProtocol.*;
import java.math.BigInteger;import java.nio.charset.StandardCharsets;import java.util.*;
import org.springframework.stereotype.Component;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.function.ThrowingSupplier;

/** Independent original policy-source preparation; does not enable a model or contact its provider. */
@Component @ConditionalOnProperty(prefix="medical.model-enablement-policy-preparation",name="enabled",havingValue="true")
public final class ModelEnablementPolicyPreparation {
 public static final String OPERATION="MODEL-ENABLEMENT-POLICY-PREPARE",EVENT="model.enablement.policy_prepared";
 private static final String PURPOSE="MODEL_ENABLEMENT";
 private static final int MAXIMUM_BYTES=2*1024*1024;
 private static final DependencyLineage.Limits LIMITS=new DependencyLineage.Limits(512,2048);
 private final EnterpriseAccess access;private final ModelEnablementPolicyMapper mapper;private final SourceReadMapper sourceRows;
 private final SourceReadAuthority sources;private final ResourceReadAuthority resources;private final SourceUploadTransport transport;
 private final DependencyLineage lineage;private final VersionResourceAuthority versions;private final PermissionSchema json;
 private final ModelConfigurationService models;private final ProjectMapper projectRows;private final ProjectService projects;
 private final ScopeSelectionAuthority selections;private final ScopeSnapshotStore snapshots;private final ModelEnablementPolicySchema schema;private final DocumentUploadPolicy deployment;
 private record Prepared(ModelConfigurationProtocol.Fixed model,ProjectProtocol.VerifiedProfile profile,SourceReadProtocol.VerifiedSource source,
  ScopeSelectionAuthority.Selection selection,Original original,PermissionProtocol.GrantRow grant,JsonNode closure,JsonNode classificationWitness,String classification,String digest){}
 public ModelEnablementPolicyPreparation(EnterpriseAccess access,ModelEnablementPolicyMapper mapper,SourceReadMapper sourceRows,SourceReadAuthority sources,
  ResourceReadAuthority resources,SourceUploadTransport transport,DependencyLineage lineage,VersionResourceAuthority versions,PermissionSchema json,
  ModelConfigurationService models,ProjectMapper projectRows,ProjectService projects,ScopeSelectionAuthority selections,ScopeSnapshotStore snapshots,ModelEnablementPolicySchema schema,DocumentUploadPolicy deployment){
  this.access=access;this.mapper=mapper;this.sourceRows=sourceRows;this.sources=sources;this.resources=resources;this.transport=transport;this.lineage=lineage;this.versions=versions;this.json=json;
  this.models=models;this.projectRows=projectRows;this.projects=projects;this.selections=selections;this.snapshots=snapshots;this.schema=schema;this.deployment=deployment;
 }
 public JsonNode candidate(ApplicationSessionIdentity.Verified subject,String context,String revision){
  WireValues.uuid(revision);PermissionSchema.revision(context);
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   context(a,context);var readable=models.read(a,revision).required("data");String project=readable.required("project_id").textValue();WireValues.uuid(project);
   var fixed=models.requireCurrentExternal(a,revision,PURPOSE,project);var definition=(ModelConfigurationProtocol.Definition)fixed.definition();
   resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"CONFIGURATION",fixed.config_id()),"model.enable",PURPOSE);
   resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project),"project.read",PURPOSE);
   var v=values(a);v.put("projectId",project);String profile=one(projectRows.project(v)).profileRevision();
   var source=one(sourceRows.source(definition.data_policy().policy_basis().version_id()));
   var request=json.object().put("schema_version","1.0.0").put("project_id",project).put("model_config_revision",revision).put("profile_revision",profile).put("rights_revision",source.rightsRevision()).put("business_purpose",PURPOSE);
   request.set("policy_basis",schema.tree(definition.data_policy().policy_basis()));var current=state(a,context,request);
   var result=request.deepCopy();result.put("source_id",current.source().source().sourceId()).put("parse_version",current.source().selection().parseVersion()).put("original_validation","NOT_READ");
   return schema.validate("Candidate",result);
  }).get());
 }
 public JsonNode prepare(ApplicationSessionIdentity.Verified subject,String context,String key,JsonNode request)throws Exception{
  schema.request(schema.canonical(request).getBytes(StandardCharsets.UTF_8));ModelEnablementPolicySchema.key(key);PermissionSchema.revision(context);
  var initial=current(subject,context,request);readOriginal(subject,context,request,initial);
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   var v=values(a);same(a.actorId(),one(mapper.lockActor(v)));v.put("sourceVersion",initial.source().source().sourceVersion());same(v.get("sourceVersion"),one(mapper.lockSource(v)));
   var checked=state(a,context,request);same(initial.digest(),checked.digest());String opHash=schema.operationHash(a.context().tenant_id(),a.actorId(),request);v.put("key",key);
   var prior=mapper.operation(v);if(prior.size()>1)bad();if(!prior.isEmpty()){
    if(!opHash.equals(prior.get(0).requestSha256()))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
    v.put("preparationId",prior.get(0).targetId());var row=one(mapper.stored(v));same(a.actorId(),row.createdBy());same(key,row.originalKey());same(schema.canonical(request),row.canonicalRequest());verify(a,row,checked);return parse(row.receiptJson());
   }
   var now=Objects.requireNonNull(mapper.databaseTime(v));String id=UUID.randomUUID().toString(),operation=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString();
   var snapshot=snapshots.saveForModelEnablement(a,checked.model().project_id(),request.required("profile_revision").asText(),scope(request),checked.model().config_id(),deployment.environment(),deployment.region(),LIMITS);
   var basis=new PermissionProtocol.Basis(snapshot.snapshot().snapshotId(),snapshot.snapshot().manifestHash());
   var evidence=evidence(checked,basis.version_id());policyAuthority().require(a,policyBasis(request),evidence).requireCurrent();
   var authorization=json.object().put("identity_mode",a.bindingView().mode());authorization.set("model_enable",schema.tree(checked.grant()));authorization.set("source",schema.tree(checked.source()));authorization.set("selection",schema.tree(checked.selection()));
   // Store only immutable model identity, never its credential/auth reference or provider configuration.
   authorization.put("model_config_revision",checked.model().model_config_revision()).put("config_id",checked.model().config_id());authorization.set("profile",schema.tree(checked.profile()));authorization.set("closure",checked.closure());authorization.set("classifications",checked.classificationWitness());
   var receipt=receipt(id,operation,a.actorId(),now,checked,request,basis);
   var row=new Stored(a.context().tenant_id(),checked.model().project_id(),id,checked.model().config_id(),checked.model().model_config_revision(),request.required("profile_revision").asText(),checked.source().source().sourceId(),checked.source().source().sourceVersion(),checked.source().rights().rightsRevision(),checked.original().sha256(),StoredObjectReferences.encode(checked.original().reference()),initial.digest(),basis.version_id(),basis.content_sha256(),checked.source().selection().parseVersion(),schema.canonical(request),schema.hash(request),opHash,key,context,subject.sessionId(),schema.canonical(authorization),schema.hash(authorization),schema.canonical(receipt),operation,audit,schema.manifest(),a.actorId(),now,checked.classification());
   writeValues(v,row);count(mapper.insertOperation(v));count(mapper.insertAudit(v));count(mapper.insertEvent(v));count(mapper.insertStored(v));same(row,one(mapper.stored(v)));verify(a,row,checked);same(checked.digest(),state(a,context,request).digest());return receipt;
  }).get());
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String id)throws Exception{return load(subject,context,id,false);}
 public JsonNode recover(ApplicationSessionIdentity.Verified subject,String context,String operation)throws Exception{return load(subject,context,operation,true);}
 private JsonNode load(ApplicationSessionIdentity.Verified subject,String context,String id,boolean recovery)throws Exception{
  WireValues.uuid(id);PermissionSchema.revision(context);deployment.requireConfigured();
  var row=access.execute(subject,a->ThrowingSupplier.of(()->{
   context(a,context);var v=values(a);String preparation=id;if(recovery){v.put("creatorId",a.actorId());v.put("operationId",id);preparation=available(mapper.operationById(v)).targetId();}v.put("preparationId",preparation);var stored=available(mapper.stored(v));if(recovery){same(a.actorId(),stored.createdBy());same(id,stored.operationId());}state(a,context,schema.request(stored.canonicalRequest().getBytes(StandardCharsets.UTF_8)));return stored;
  }).get());
  var request=schema.request(row.canonicalRequest().getBytes(StandardCharsets.UTF_8));var initial=current(subject,context,request);readOriginal(subject,context,request,initial);
  return access.execute(subject,a->ThrowingSupplier.of(()->{var v=values(a);v.put("preparationId",row.preparationId());same(row,one(mapper.stored(v)));var checked=state(a,context,request);same(initial.digest(),checked.digest());verify(a,row,checked);var result=json.object();result.set("receipt",parse(row.receiptJson()));if(recovery){result.set("original_request",request);result.put("original_idempotency_key",row.originalKey()).put("original_context_revision",row.originalContext());}return schema.validate(recovery?"Recovery":"Read",result);}).get());
 }
 private Prepared current(ApplicationSessionIdentity.Verified subject,String context,JsonNode request){return access.execute(subject,a->ThrowingSupplier.of(()->state(a,context,request)).get());}
 private Prepared state(EnterpriseAccess.Admission a,String context,JsonNode request)throws Exception{
  context(a,context);deployment.requireConfigured();schema.validate("Register",request);String project=request.required("project_id").asText();var basis=policyBasis(request);
  var model=models.requireCurrentExternal(a,request.required("model_config_revision").asText(),PURPOSE,project);same(project,model.project_id());same(a.context().tenant_id(),model.tenant_id());
  if(!(model.definition() instanceof ModelConfigurationProtocol.Definition definition))bad();
  same(basis,((ModelConfigurationProtocol.Definition)model.definition()).data_policy().policy_basis());
  var grant=resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"CONFIGURATION",model.config_id()),"model.enable",PURPOSE);
  var profile=projects.fixedProfile(a,project,request.required("profile_revision").asText(),PURPOSE);if(!"ACTIVE".equals(profile.project().status()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  var selection=selections.require(a,project,scope(request),PURPOSE,deployment.environment(),deployment.region(),LIMITS);var source=one(selection.sources());
  same(basis.version_id(),source.source().sourceVersion());same(basis.content_sha256(),source.source().originalSha256());same(request.required("rights_revision").asText(),source.rights().rightsRevision());same(PURPOSE,source.rights().purpose());
  if(!Set.of("PROJECT","ENTERPRISE").contains(source.source().ownership())||"PROJECT".equals(source.source().ownership())&&!project.equals(source.source().ownerProjectId()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  var v=values(a);v.put("sourceVersion",basis.version_id());var original=one(mapper.original(v));same(a.context().tenant_id(),original.tenantId());same(basis.version_id(),original.sourceVersion());same(basis.content_sha256(),original.sha256());
  if(original.sizeBytes()==null||original.sizeBytes().signum()<=0||original.sizeBytes().compareTo(BigInteger.valueOf(MAXIMUM_BYTES))>0||original.sizeBytes().compareTo(deployment.maximumBytes())>0)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  if(original.objectKey()==null||!original.objectKey().startsWith("tenants/"+original.tenantId()+"/")||original.objectKey().contains(".."))bad();StoredObjectReferences.encode(original.reference());
  var graph=lineage.inspect(basis.version_id(),LIMITS);var root=graph.versions().get(basis.version_id());if(root==null||!"SOURCE_VERSION".equals(root.entityKind()))bad();same(basis.content_sha256(),root.contentSha256());
  // First profile supports a standalone policy SOURCE, never hidden upstream business sources.
  if(graph.versions().size()!=1||!graph.graph().edgeSet().isEmpty())throw new TenantAccessException(ErrorCode.MODEL_POLICY_INCOMPLETE);
  var checked=versions.require(a,graph,PURPOSE,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),Map.of());
  var closure=json.object();closure.set("versions",schema.tree(new TreeMap<>(graph.versions())));closure.set("current_versions",schema.tree(new TreeMap<>(checked)));
  v.put("projectId",project);v.put("modelRevision",model.model_config_revision());v.put("profileRevision",request.required("profile_revision").asText());v.put("rightsRevision",source.rights().rightsRevision());v.put("parseVersion",source.selection().parseVersion());v.put("indexVersion",source.selection().indexVersion());v.put("approvalRevision",source.selection().approvalRevision());v.put("bindingRevision",source.selection().bindingRevision());
  var classified=mapper.classifications(v);if(classified.isEmpty())bad();String classification=max(source.source().classification(),source.rights().classification());var kinds=new HashSet<String>();for(var item:classified){if(!kinds.add(item.kind()))bad();classification=max(classification,item.classification());switch(item.kind()){
   case "SOURCE_OWNER"->{same(basis.version_id(),item.id());same(basis.content_sha256(),item.sha256());}
   case "RIGHTS"->{same(source.rights().rightsRevision(),item.id());same(source.rights().contentSha256(),item.sha256());}
   case "PROFILE"->{same(request.required("profile_revision").asText(),item.id());same(profile.sha256(),item.sha256());}
   case "MODEL","MODEL_DEFINITION"->{same(model.model_config_revision(),item.id());same(schema.hash(schema.tree(model)),item.sha256());}
   default->bad();
  }}
  requireClassificationKinds(kinds);var witness=schema.tree(classified);
  var digest=json.object().put("actor",a.actorId()).put("classification",classification);digest.set("model",schema.tree(model));digest.set("profile",schema.tree(profile));digest.set("source",schema.tree(source));digest.set("selection",schema.tree(selection));digest.set("original",schema.tree(original));digest.set("grant",schema.tree(grant));digest.set("closure",closure);digest.set("classifications",witness);digest.set("context",schema.tree(a.context()));digest.set("watermarks",schema.tree(a.watermarks()));
  return new Prepared(model,profile,source,selection,original,grant,closure,witness,classification,schema.hash(digest));
 }
 private static void requireClassificationKinds(Set<String> kinds){if(!kinds.equals(Set.of("SOURCE_OWNER","RIGHTS","MODEL","MODEL_DEFINITION","PROFILE")))bad();}
 private void readOriginal(ApplicationSessionIdentity.Verified subject,String context,JsonNode request,Prepared initial)throws Exception{
  var original=initial.original();S3ObjectReader.Admission guard=()->same(initial.digest(),current(subject,context,request).digest());
  transport.readDocument(subject,original.tenantId(),MAXIMUM_BYTES,guard,(reader,current)->{try(var verified=reader.readVerified(original.reference(),current)){same(original.reference(),verified.reference());same(original.sha256(),verified.fullSha256());for(int start=0;start<original.sizeBytes().intValueExact();){int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,original.sizeBytes().intValueExact()-start);if(verified.chunk(start,n).length!=n)bad();start+=n;}current.requireCurrent();return Boolean.TRUE;}});
 }
 private JsonNode receipt(String id,String operation,String actor,java.time.LocalDateTime now,Prepared current,JsonNode request,PermissionProtocol.Basis basis)throws Exception{
  var value=json.object().put("schema_version","1.0.0").put("preparation_id",id).put("operation_id",operation).put("project_id",current.model().project_id()).put("model_config_revision",current.model().model_config_revision()).put("profile_revision",request.required("profile_revision").asText()).put("business_purpose",PURPOSE).put("request_body_sha256",schema.hash(request)).put("created_by",actor).put("created_at",now.toInstant(java.time.ZoneOffset.UTC).toString());
  value.set("policy_basis",request.required("policy_basis"));value.set("scope_snapshot_basis",schema.tree(basis));value.set("data_policy_evidence",schema.tree(evidence(current,basis.version_id())));return schema.validate("Receipt",value);
 }
 private void verify(EnterpriseAccess.Admission a,Stored row,Prepared current)throws Exception{
  same(a.context().tenant_id(),row.tenantId());same(current.model().project_id(),row.projectId());same(current.model().config_id(),row.configId());same(current.model().model_config_revision(),row.modelRevision());same(current.classification(),row.classification());same(schema.manifest(),row.workflowSha256());
  ModelEnablementPolicySchema.key(row.originalKey());PermissionSchema.revision(row.originalContext());WireValues.uuid(row.originalSession());WireValues.uuid(row.auditId());if(!row.originalAuthorizationSha256().matches("[0-9a-f]{64}"))bad();
  var request=schema.request(row.canonicalRequest().getBytes(StandardCharsets.UTF_8));same(row.canonicalRequest(),schema.canonical(request));same(row.requestSha256(),schema.hash(request));same(row.operationRequestSha256(),schema.operationHash(row.tenantId(),row.createdBy(),request));
  same(row.projectId(),request.required("project_id").asText());same(row.modelRevision(),request.required("model_config_revision").asText());same(row.profileRevision(),request.required("profile_revision").asText());same(row.sourceVersion(),policyBasis(request).version_id());same(row.sourceSha256(),policyBasis(request).content_sha256());same(row.rightsRevision(),request.required("rights_revision").asText());
  same(row.sourceId(),current.source().source().sourceId());same(row.parseVersion(),current.source().selection().parseVersion());same(row.originalObject(),StoredObjectReferences.encode(current.original().reference()));
  var auth=parse(row.authorizationJson());same(row.authorizationJson(),schema.canonical(auth));same(row.authorizationSha256(),schema.hash(auth));same(current.closure(),auth.required("closure"));same(current.classificationWitness(),auth.required("classifications"));same(schema.tree(current.profile()),auth.required("profile"));same(schema.tree(current.selection()),auth.required("selection"));same(row.configId(),auth.required("config_id").asText());same(row.modelRevision(),auth.required("model_config_revision").asText());
  var historic=schema.bind(auth.required("source"),SourceReadProtocol.VerifiedSource.class);same(current.source().source(),historic.source());same(current.source().rights(),historic.rights());
  String mode=auth.required("identity_mode").asText();var authority=new PermissionAuthority(json);if(authority.findResource(new PermissionProtocol.Scope(row.tenantId(),row.projectId(),"CONFIGURATION",row.configId()),row.createdBy(),mode,"model.enable",PURPOSE,List.of(schema.bind(auth.required("model_enable"),PermissionProtocol.GrantRow.class)),row.createdAt()).isEmpty())bad();
  String owner="ENTERPRISE".equals(historic.source().ownership())?null:row.projectId();if(authority.findResource(new PermissionProtocol.Scope(row.tenantId(),owner,"SOURCE",row.sourceId()),row.createdBy(),mode,"source.read",PURPOSE,List.of(historic.sourceGrant()),row.createdAt()).isEmpty())bad();
  var basis=new PermissionProtocol.Basis(row.snapshotId(),row.snapshotSha256());snapshots.readForModelEnablement(a,row.projectId(),row.profileRevision(),scope(request),row.configId(),deployment.environment(),deployment.region(),LIMITS,basis);policyAuthority().require(a,policyBasis(request),evidence(current,row.snapshotId())).requireCurrent();
  var expected=receipt(row.preparationId(),row.operationId(),row.createdBy(),row.createdAt(),current,request,basis);same(expected,parse(row.receiptJson()));same(row.receiptJson(),schema.canonical(expected));
  var v=values(a);writeValues(v,row);v.put("creatorId",row.createdBy());var operation=one(mapper.operationById(v));same(row.operationId(),operation.operationId());same(row.operationRequestSha256(),operation.requestSha256());same("COMPLETED",operation.state());same(Integer.valueOf(201),operation.httpStatus());same(row.preparationId(),operation.targetId());same("1.0.0",operation.schemaVersion());same(schema.manifest(),operation.schemaManifestSha256());same(expected,parse(operation.responseRef()));same(event(row),parse(one(mapper.audit(v))));same(event(row),parse(one(mapper.event(v))));
 }
 private void writeValues(Map<String,Object> v,Stored r)throws Exception{v.put("row",r);v.put("preparationId",r.preparationId());v.put("operationId",r.operationId());v.put("auditId",r.auditId());v.put("key",r.originalKey());v.put("now",r.createdAt());v.put("projectId",r.projectId());v.put("classification",r.classification());v.put("operationManifest",r.workflowSha256());v.put("requestHash",r.operationRequestSha256());v.put("response",r.receiptJson());v.put("payload",schema.canonical(event(r)));}
 private JsonNode event(Stored r)throws Exception{var value=json.object().put("tenant_id",r.tenantId()).put("preparation_id",r.preparationId()).put("operation_id",r.operationId()).put("audit_id",r.auditId()).put("canonical_request",r.canonicalRequest()).put("request_body_sha256",r.requestSha256()).put("operation_request_sha256",r.operationRequestSha256()).put("idempotency_key",r.originalKey()).put("request_context",r.originalContext()).put("request_session",r.originalSession()).put("authorization_sha256",r.authorizationSha256()).put("original_authorization_sha256",r.originalAuthorizationSha256()).put("original_object",r.originalObject()).put("source_sha256",r.sourceSha256()).put("workflow_schema_sha256",r.workflowSha256()).put("classification",r.classification());value.set("receipt",parse(r.receiptJson()));return value;}
 private ModelPolicyBasisAuthority policyAuthority(){return new ModelPolicyBasisAuthority(sources,deployment.environment(),deployment.region());}
 private ModelPolicyBasisAuthority.Evidence evidence(Prepared p,String snapshot){return new ModelPolicyBasisAuthority.Evidence(p.source().source().sourceVersion(),p.source().rights().rightsRevision(),p.model().project_id(),snapshot,p.source().selection().parseVersion());}
 private PermissionProtocol.Basis policyBasis(JsonNode r)throws Exception{return schema.bind(r.required("policy_basis"),PermissionProtocol.Basis.class);}
 private CommonProtocol.SourceScope scope(JsonNode r)throws Exception{return new CommonProtocol.SourceScope(CommonProtocol.ScopeMode.SOURCE_VERSIONS,List.of(),List.of(policyBasis(r).version_id()),false,null,null);}
 private JsonNode parse(String value)throws Exception{return schema.parse(value.getBytes(StandardCharsets.UTF_8));}
 private static String max(String... values){var levels=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");int result=0;for(var value:values){int index=levels.indexOf(value);if(index<0)bad();result=Math.max(result,index);}return levels.get(result);}
 private static Map<String,Object> values(EnterpriseAccess.Admission a){var v=new HashMap<String,Object>();v.put("userId",a.actorId());return v;}
 private static void context(EnterpriseAccess.Admission a,String c){if(!Objects.equals(c,a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}private static <T>T available(List<T> values){if(values.isEmpty())throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return one(values);}
 private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void count(int value){if(value!=1)bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
