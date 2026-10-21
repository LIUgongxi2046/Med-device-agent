package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;import com.ruoyi.medical.persistence.mapper.DocumentComparisonMapper.*;
import com.ruoyi.medical.persistence.DocumentComparisonProtocol.Command;
import java.math.BigInteger;import java.time.*;import java.util.*;
import org.springframework.stereotype.Component;import org.springframework.boot.context.properties.EnableConfigurationProperties;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.util.function.ThrowingSupplier;
/** Synchronous fixed-input structural comparison. No worker/job, model, approval or document mutation. */
@Component
@EnableConfigurationProperties(DocumentComparisonPolicy.class)
@ConditionalOnProperty(prefix="medical.document-comparison",name="enabled",havingValue="true")
public final class DocumentComparisonService{
 private final EnterpriseAccess access;private final DocumentComparisonMapper mapper;private final DocumentReadService documents;private final PermissionMapper permissions;
 private final DependencyAccessMapper accessSets;private final ResourceReadAuthority resources;private final ArtifactRegistryMapper registry;private final DependencyManifestSchema dependencies;private final DependencyLineage lineage;
 private final SourceUploadTransport transport;private final DocumentComparisonPolicy policy;private final PermissionSchema json;private final DocumentComparisonSchema schema;private final DocumentStructuralComparisonEngine engine;
 public DocumentComparisonService(EnterpriseAccess access,DocumentComparisonMapper mapper,DocumentReadService documents,PermissionMapper permissions,ResourceReadAuthority resources,ArtifactRegistryMapper registry,DependencyAccessMapper accessSets,DependencyManifestSchema dependencies,DependencyLineage lineage,SourceUploadTransport transport,DocumentComparisonPolicy policy,PermissionSchema json)throws Exception{
  this.access=access;this.mapper=mapper;this.documents=documents;this.permissions=permissions;this.resources=resources;this.registry=registry;this.accessSets=accessSets;this.dependencies=dependencies;this.lineage=lineage;this.transport=transport;this.policy=policy;this.json=json;schema=new DocumentComparisonSchema();engine=new DocumentStructuralComparisonEngine(schema.core);
 }
 private record Proofs(DocumentReadService.FixedRead left,DocumentReadService.FixedRead right){}
 public JsonNode create(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] raw)throws Exception{
  policy.requireConfigured();PermissionSchema.revision(context);DocumentComparisonSchema.key(key);var request=schema.request(raw,policy.maximumInputBytes());String bodyHash=schema.hash(request),requestHash=requestHash(request);
  var original=tx(subject,a->{current(a,context);return lookup(a,key);});
  if(original!=null){same(requestHash,original.requestSha256());if("COMPLETED".equals(original.state()))return read(subject,context,original.targetId()).required("comparison");}
  var proof=proofs(subject,context,request);
  Intent intent;
  if(original==null){
   JsonNode core=engine.compare(request,proof.left().body(),proof.right().body(),policy.limits(),proof.left().bodySchemaRef(),proof.left().bodySchemaSha256(),proof.right().bodySchemaRef(),proof.right().bodySchemaSha256());
   intent=tx(subject,a->start(a,context,subject.sessionId(),key,request,requestHash,bodyHash,proof,core));
  }else{var op=original;intent=tx(subject,a->{var v=values(a);v.put("comparisonId",op.targetId());var row=one(mapper.intent(v));verifyIntent(a,row,proof);return row;});}
  // Recompute once against actual complete fixed bodies. A digest alone cannot establish full coverage.
  engine.verifyRegisteredAgainstInputs(request,proof.left().body(),proof.right().body(),schema.read(intent.canonicalCore()),policy.limits(),proof.left().bodySchemaRef(),proof.left().bodySchemaSha256(),proof.right().bodySchemaRef(),proof.right().bodySchemaSha256());
  Command command=tx(subject,a->{guard(a,context,subject.sessionId(),intent,proof);return command(a,intent,key);});
  final InterfaceProtocol.ObjectRef verifiedOutput;
  if(command.reference()==null){
   boolean recover=command.attempted();Command attempted=new Command(command.intentSha256(),true,null);
   tx(subject,a->{guard(a,context,subject.sessionId(),intent,proof);if(!recover)markAttempt(a,intent,key,command,attempted);else same(command,command(a,intent,key));return null;});
   var output=writeIntent(intent);var reference=transport.writeDocumentProjection(subject,intent.tenantId(),output,intent.canonicalCore().getBytes(java.nio.charset.StandardCharsets.UTF_8),recover,policy.maximumResultBytes(),()->tx(subject,a->{guard(a,context,subject.sessionId(),intent,proof);return null;}));
   verifyReference(output,reference);verifiedOutput=reference;var stored=new Command(command.intentSha256(),true,reference);
   tx(subject,a->{guard(a,context,subject.sessionId(),intent,proof);advance(a,intent,key,attempted,stored);return null;});
  }else{
   // A persisted mutable command cannot attest that an object exists. Probe the original immutable intent read-only.
   var recovered=transport.writeDocumentProjection(subject,intent.tenantId(),writeIntent(intent),intent.canonicalCore().getBytes(java.nio.charset.StandardCharsets.UTF_8),true,policy.maximumResultBytes(),()->tx(subject,a->{guard(a,context,subject.sessionId(),intent,proof);return null;}));
   same(command.reference(),recovered);verifiedOutput=recovered;
  }
  return tx(subject,a->finish(a,context,subject.sessionId(),key,intent,proof,verifiedOutput));
 }
 private Intent start(EnterpriseAccess.Admission a,String context,String session,String key,InterfaceProtocol.CompareRequest request,String requestHash,String bodyHash,Proofs proof,JsonNode core)throws Exception{
  requireProofs(a,context,request,proof);schema.core.validate(core);same(schema.tree(request),core.required("request"));
  var v=values(a);v.put("key",key);var existing=lookup(a,key);if(existing!=null){var op=existing;same(requestHash,op.requestSha256());same("PROCESSING",op.state());v.put("comparisonId",op.targetId());var row=one(mapper.intent(v));verifyIntent(a,row,proof);return row;}
  String comparison=id(),operation=id(),manifest=id(),set=id(),artifact=id(),write=id(),startEvent=id();var now=(LocalDateTime)v.get("now");var deadline=now.plusSeconds(policy.storageSeconds());
  String project=proof.left().header().projectId(),classification=classification(proof.left().header().classification(),proof.right().header().classification());
  var members=union(proof);var union=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",a.context().tenant_id(),project,set,members);String depJson=schema.canonical(union),depHash=schema.hash(union),coreJson=schema.canonical(core),coreHash=schema.hash(core);
  var input=input(a.context().tenant_id(),project,comparison,operation,manifest,set,artifact,write,startEvent,context,session,key,schema.tree(request),bodyHash,requestHash,proof,depHash,coreHash,a.actorId(),now,deadline,classification);
  var row=new Intent(a.context().tenant_id(),project,comparison,operation,manifest,set,artifact,write,startEvent,request.documentId(),request.leftRevision(),request.rightRevision(),context,session,key,schema.canonical(request),bodyHash,requestHash,schema.canonical(input),schema.hash(input),depJson,depHash,coreJson,coreHash,"1.0.0",schema.manifest(),a.actorId(),now,deadline,classification);
  base(v,row);v.put("versionId",set);v.put("versionKind","DEPENDENCY_ACCESS_SET");v.put("versionHash",depHash);count(mapper.insertVersion(v));
  v.put("accessId",set);v.put("dependencyJson",depJson);v.put("dependencyHash",depHash);v.put("memberCount",members.size());v.put("dependencySchema",dependencies.manifestSha256());count(mapper.insertAccessSet(v));count(mapper.insertAccessManifest(v));
  for(var member:members){v.put("member",member);count(mapper.insertAccessMember(v));v.put("consumerId",set);v.put("upstreamId",member.upstreamVersionId());count(mapper.insertLineage(v));}
  v.put("versionId",manifest);v.put("versionKind","DOCUMENT_COMPARISON_INPUT");v.put("versionHash",row.inputSha256());count(mapper.insertVersion(v));
  v.put("commandJson",schema.canonical(new Command(schema.hash(row),false,null)));v.put("requestHash",requestHash);v.put("row",row);count(mapper.startOperation(v));count(mapper.insertIntent(v));
  JsonNode event=startEvent(row);count(permissions.saveAudit(a.actorId(),project,id(),"document.comparison.started",schema.canonical(event),operation,schema.manifest(),classification));count(permissions.saveEvent(a.actorId(),startEvent,comparison,"1","document.comparison.started",schema.canonical(event)));
  verifyIntent(a,one(mapper.intent(v)),proof);return row;
 }
 private JsonNode finish(EnterpriseAccess.Admission a,String context,String session,String key,Intent row,Proofs proof,InterfaceProtocol.ObjectRef verifiedOutput)throws Exception{
  guard(a,context,session,row,proof);var command=command(a,row,key);same(verifiedOutput,command.reference());if(!command.attempted()||command.reference()==null)bad();verifyReference(writeIntent(row),command.reference());
  var v=values(a);base(v,row);v.put("key",key);v.put("requestHash",row.operationRequestSha256());v.put("operationRevision",one(mapper.operationRevision(v)));
  v.put("versionId",row.artifactId());v.put("versionKind","ARTIFACT");v.put("versionHash",row.coreSha256());count(mapper.insertVersion(v));
  v.put("reference",command.reference());v.put("storedObject",StoredObjectReferences.encode(command.reference()));v.put("artifactKind","DOCUMENT_COMPARISON");v.put("documentRevision",row.rightRevision());v.put("rightRevision",row.rightRevision());
  v.put("schemaRef",schema.core.schemaRef(schema.read(row.canonicalCore())));v.put("schemaHash",schema.core.schemaSha256(schema.read(row.canonicalCore())));v.put("dependencyHash",row.dependencySha256());count(mapper.insertArtifact(v));count(mapper.insertBinding(v));
  for(var member:union(proof)){v.put("consumerId",row.artifactId());v.put("upstreamId",member.upstreamVersionId());count(mapper.insertLineage(v));}
  var core=schema.read(row.canonicalCore());var coverage=new CommonProtocol.Coverage(core.at("/coverage/selection_sha256").textValue(),core.at("/coverage/delivered_sha256").textValue(),true,null);
  var receipt=schema.receipt(schema.tree(new InterfaceProtocol.Comparison(row.comparisonId(),"1",row.leftRevision(),row.rightRevision(),new CommonProtocol.ArtifactRef(row.artifactId(),row.coreSha256(),schema.core.schemaRef(core),coverage),ids(core.required("affected_node_ids")))));
  var completed=new Registration(row.tenantId(),row.projectId(),row.comparisonId(),row.operationId(),row.artifactId(),schema.hash(row),schema.canonical(command.reference()),schema.canonical(receipt),row.coreSha256(),"1.0.0",schema.manifest(),row.createdBy(),(LocalDateTime)v.get("now"),row.classification());
  v.put("row",completed);count(mapper.insertRegistration(v));v.put("response",schema.canonical(receipt));count(mapper.completeOperation(v));
  var event=completedEvent(completed,verifyAttempt(a,row));count(permissions.saveAudit(a.actorId(),row.projectId(),id(),"document.comparison.completed",schema.canonical(event),row.operationId(),schema.manifest(),row.classification()));count(permissions.saveEvent(a.actorId(),row.operationId(),row.comparisonId(),"2","document.comparison.completed",schema.canonical(event)));
  same(completed,one(mapper.registration(v)));verifyCompleted(a,row,completed,proof,false);return receipt;
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String comparison)throws Exception{
  policy.requireConfigured();WireValues.uuid(comparison);PermissionSchema.revision(context);
  Intent row=tx(subject,a->{current(a,context);var v=values(a);v.put("comparisonId",comparison);return one(mapper.intent(v));});
  var request=schema.decode(row.canonicalRequest(),InterfaceProtocol.CompareRequest.class);var proof=proofs(subject,context,request);
  Registration completed=tx(subject,a->{requireProofs(a,context,request,proof);var v=values(a);base(v,row);var found=mapper.registration(v);if(found.isEmpty())throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);var result=one(found);verifyCompleted(a,row,result,proof,true);return result;});
  // Real complete authenticated inputs establish complete record provenance; not just a matching hash.
  engine.verifyRegisteredAgainstInputs(request,proof.left().body(),proof.right().body(),schema.read(row.canonicalCore()),policy.limits(),proof.left().bodySchemaRef(),proof.left().bodySchemaSha256(),proof.right().bodySchemaRef(),proof.right().bodySchemaSha256());
  var reference=schema.decode(completed.reference(),InterfaceProtocol.ObjectRef.class);
  JsonNode result=transport.readDocument(subject,row.tenantId(),policy.maximumResultBytes(),()->tx(subject,a->{requireProofs(a,context,request,proof);verifyCompleted(a,row,completed,proof,true);return null;}),(reader,admission)->{
   try(var verified=reader.readVerified(reference,admission)){
    same(reference,verified.reference());same(row.coreSha256(),verified.fullSha256());int size=WireValues.revision(reference.sizeBytes()).intValueExact();if(size>policy.maximumResultBytes())throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=new byte[size];
    for(int offset=0;offset<size;){int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);System.arraycopy(verified.chunk(offset,n),0,bytes,offset,n);offset+=n;}
    String raw=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);same(row.canonicalCore(),raw);var actual=engine.verifyRegisteredAgainstInputs(request,proof.left().body(),proof.right().body(),schema.read(raw),policy.limits(),proof.left().bodySchemaRef(),proof.left().bodySchemaSha256(),proof.right().bodySchemaRef(),proof.right().bodySchemaSha256());same(raw,schema.canonical(actual));admission.requireCurrent();return actual;
   }
  });
  return tx(subject,a->{requireProofs(a,context,request,proof);verifyCompleted(a,row,completed,proof,true);var view=json.object().put("schema_version","1.0.0").put("current_document_revision",proof.right().header().currentDocumentRevision()).put("document_head_revision",proof.right().header().revision().toString()).put("left_is_current",row.leftRevision().equals(proof.left().header().currentDocumentRevision())).put("right_is_current",row.rightRevision().equals(proof.right().header().currentDocumentRevision()));view.set("comparison",schema.receipt(schema.read(completed.receiptJson())));view.set("result",result);return schema.view("Read",view);});
 }
 public JsonNode recovery(ApplicationSessionIdentity.Verified subject,String context,String key)throws Exception{
  policy.requireConfigured();PermissionSchema.revision(context);DocumentComparisonSchema.key(key);
  var op=tx(subject,a->{current(a,context);return lookup(a,key);});
  var result=json.object().put("schema_version","1.0.0");if(op==null)return schema.view("Recovery",result.put("state","NOT_FOUND").putNull("request_body_sha256").putNull("comparison_id").putNull("comparison"));
  var row=tx(subject,a->{current(a,context);var v=values(a);v.put("comparisonId",op.targetId());return one(mapper.intent(v));});var request=schema.decode(row.canonicalRequest(),InterfaceProtocol.CompareRequest.class);var proof=proofs(subject,context,request);
  tx(subject,a->{requireProofs(a,context,request,proof);verifyIntent(a,row,proof);same(row.operationId(),op.operationId());same(row.operationRequestSha256(),op.requestSha256());if("PROCESSING".equals(op.state()))command(a,row,key);return null;});
  result.put("state",op.state()).put("request_body_sha256",row.requestBodySha256()).put("comparison_id",row.comparisonId());
  if("COMPLETED".equals(op.state()))result.set("comparison",read(subject,context,row.comparisonId()).required("comparison"));else{same("PROCESSING",op.state());result.putNull("comparison");}return schema.view("Recovery",result);
 }
 private Proofs proofs(ApplicationSessionIdentity.Verified subject,String context,InterfaceProtocol.CompareRequest r)throws Exception{return new Proofs(documents.readFixed(subject,context,r.documentId(),r.leftRevision()),documents.readFixed(subject,context,r.documentId(),r.rightRevision()));}
 private void requireProofs(EnterpriseAccess.Admission a,String context,InterfaceProtocol.CompareRequest r,Proofs p)throws Exception{
  current(a,context);policy.requireConfigured();documents.requireCurrent(a,p.left());documents.requireCurrent(a,p.right());
  same(a.context().tenant_id(),p.left().header().tenantId());same(a.context().tenant_id(),p.right().header().tenantId());same(p.left().header().projectId(),p.right().header().projectId());same(r.documentId(),p.left().header().documentId());same(r.documentId(),p.right().header().documentId());same(r.leftRevision(),p.left().header().documentRevision());same(r.rightRevision(),p.right().header().documentRevision());
  resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),p.left().header().projectId(),"DOCUMENT",r.documentId()),"document.read","DOCUMENT_BODY");
 }
 private void guard(EnterpriseAccess.Admission a,String context,String session,Intent row,Proofs proof)throws Exception{
  same(row.requestContext(),context);same(row.requestSession(),session);same(row.createdBy(),a.actorId());requireProofs(a,context,schema.decode(row.canonicalRequest(),InterfaceProtocol.CompareRequest.class),proof);verifyIntent(a,row,proof);
  if(!permissions.databaseTime().isBefore(row.deadline()))throw new TenantAccessException(ErrorCode.ACTION_STALE);
 }
 private void verifyIntent(EnterpriseAccess.Admission a,Intent row,Proofs proof)throws Exception{
  same(a.context().tenant_id(),row.tenantId());same(proof.left().header().projectId(),row.projectId());same(schema.manifest(),row.schemaManifest());same("1.0.0",row.schemaVersion());
  if(row.createdAt()==null||row.deadline()==null||!row.deadline().isAfter(row.createdAt()))bad();
  var request=schema.request(row.canonicalRequest().getBytes(java.nio.charset.StandardCharsets.UTF_8),policy.maximumInputBytes());same(row.canonicalRequest(),schema.canonical(request));same(row.requestBodySha256(),schema.hash(request));same(row.operationRequestSha256(),requestHash(request));
  same(row.documentId(),request.documentId());same(row.leftRevision(),request.leftRevision());same(row.rightRevision(),request.rightRevision());same(row.canonicalCore(),schema.canonical(schema.core.validate(schema.read(row.canonicalCore()))));same(row.coreSha256(),schema.hash(schema.read(row.canonicalCore())));
  var core=schema.read(row.canonicalCore());same(schema.tree(request),core.required("request"));same(proof.left().header().bodySha256(),core.required("left_body_sha256").textValue());same(proof.right().header().bodySha256(),core.required("right_body_sha256").textValue());
  same(schema.tree(policy.limits()),core.required("limits"));
  var expected=input(row.tenantId(),row.projectId(),row.comparisonId(),row.operationId(),row.manifestId(),row.accessSetId(),row.artifactId(),row.writeId(),row.startEventId(),row.requestContext(),row.requestSession(),row.idempotencyKey(),schema.tree(request),row.requestBodySha256(),row.operationRequestSha256(),proof,row.dependencySha256(),row.coreSha256(),row.createdBy(),row.createdAt(),row.deadline(),row.classification());
  same(row.inputManifest(),schema.canonical(expected));same(row.inputSha256(),schema.hash(expected));
  var v=values(a);base(v,row);same(row,one(mapper.intent(v)));var version=one(registry.version(row.manifestId()));same(row.manifestId(),version.id());same("DOCUMENT_COMPARISON_INPUT",version.entityKind());same(row.projectId(),version.projectId());same(row.inputSha256(),version.contentSha256());
  var manifestRow=one(registry.manifest(row.accessSetId()));var manifest=dependencies.verify(manifestRow,registry.members(row.accessSetId()));
  var expectedManifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",row.tenantId(),row.projectId(),row.accessSetId(),union(proof));same(expectedManifest,manifest);same(row.dependencyManifest(),schema.canonical(manifest));same(row.dependencySha256(),schema.hash(manifest));
  var accessHeader=one(accessSets.header(row.accessSetId()));same(row.tenantId(),accessHeader.tenantId());same(row.projectId(),accessHeader.projectId());same(row.accessSetId(),accessHeader.accessSetId());same(row.dependencySha256(),accessHeader.manifestSha256());same(row.dependencySha256(),accessHeader.contentSha256());same(row.createdBy(),accessHeader.createdBy());same(row.createdAt(),accessHeader.createdAt());same(row.classification(),accessHeader.classification());
  var setVersion=one(registry.version(row.accessSetId()));same(row.accessSetId(),setVersion.id());same("DEPENDENCY_ACCESS_SET",setVersion.entityKind());same(row.projectId(),setVersion.projectId());same(row.dependencySha256(),setVersion.contentSha256());lineage.require(row.accessSetId(),manifest.members(),policy.lineageLimits());
  same(startEvent(row),schema.read(one(mapper.startAudit(v))));same(startEvent(row),schema.read(one(mapper.startEvent(v))));
  var op=one(mapper.operationById(v));same(row.operationId(),op.operationId());same(row.comparisonId(),op.targetId());same(row.operationRequestSha256(),op.requestSha256());same("1.0.0",op.schemaVersion());same(schema.manifest(),op.schemaManifestSha256());if(!Set.of("PROCESSING","COMPLETED").contains(op.state()))bad();
 }
 private void verifyCompleted(EnterpriseAccess.Admission a,Intent row,Registration completed,Proofs proof,boolean grant)throws Exception{
  verifyIntent(a,row,proof);same(row.tenantId(),completed.tenantId());same(row.projectId(),completed.projectId());same(row.comparisonId(),completed.comparisonId());same(row.operationId(),completed.operationId());same(row.artifactId(),completed.artifactId());same(schema.hash(row),completed.intentSha256());same(row.coreSha256(),completed.coreSha256());same(row.schemaManifest(),completed.schemaManifest());same("1.0.0",completed.schemaVersion());var completedAttempt=verifyAttempt(a,row);same(row.createdBy(),completed.createdBy());same(row.classification(),completed.classification());if(completed.createdAt()==null||completed.createdAt().isBefore(completedAttempt.createdAt())||!completed.createdAt().isBefore(row.deadline()))bad();
  var reference=schema.decode(completed.reference(),InterfaceProtocol.ObjectRef.class);same(completed.reference(),schema.canonical(reference));verifyReference(writeIntent(row),reference);
  var receipt=schema.receipt(schema.read(completed.receiptJson()));same(completed.receiptJson(),schema.canonical(receipt));var core=schema.read(row.canonicalCore());
  var expected=new InterfaceProtocol.Comparison(row.comparisonId(),"1",row.leftRevision(),row.rightRevision(),new CommonProtocol.ArtifactRef(row.artifactId(),row.coreSha256(),schema.core.schemaRef(core),new CommonProtocol.Coverage(core.at("/coverage/selection_sha256").textValue(),core.at("/coverage/delivered_sha256").textValue(),true,null)),ids(core.required("affected_node_ids")));same(schema.tree(expected),receipt);
  var artifact=one(registry.artifact(row.artifactId()));same(reference,artifact.reference());same(reference,StoredObjectReferences.decode(artifact.storedObject(),reference));same(row.tenantId(),artifact.tenantId());same(row.projectId(),artifact.projectId());same(row.artifactId(),artifact.artifactId());same("DOCUMENT_COMPARISON",artifact.artifactKind());same(row.coreSha256(),artifact.contentSha256());same(row.accessSetId(),artifact.accessSetId());same("1.0.0",artifact.schemaVersion());same(schema.manifest(),artifact.schemaManifest());same(row.classification(),artifact.classification());
  var binding=one(registry.binding(row.artifactId()));same(row.tenantId(),binding.tenantId());same(row.projectId(),binding.projectId());same(row.artifactId(),binding.artifactId());same("DOCUMENT",binding.resourceKind());same(row.documentId(),binding.resourceId());same(schema.core.schemaRef(core),binding.schemaRef());same(schema.core.schemaSha256(core),binding.schemaDocumentSha256());same(row.accessSetId(),binding.accessSetId());same(row.dependencySha256(),binding.dependencyManifestSha256());
  var version=one(registry.version(row.artifactId()));same(row.artifactId(),version.id());same("ARTIFACT",version.entityKind());same(row.projectId(),version.projectId());same(row.coreSha256(),version.contentSha256());lineage.require(row.artifactId(),union(proof),policy.lineageLimits());
  var v=values(a);base(v,row);same(completed,one(mapper.registration(v)));var owner=one(mapper.artifactOwner(v));same(row.rightRevision(),owner.documentRevision());if(owner.runId()!=null)bad();
  var op=one(mapper.operationById(v));same("COMPLETED",op.state());same(Integer.valueOf(200),op.httpStatus());same(receipt,schema.read(op.responseRef()));
  same(completedEvent(completed,completedAttempt),schema.read(one(mapper.finalAudit(v))));same(completedEvent(completed,completedAttempt),schema.read(one(mapper.finalEvent(v))));
  if(grant)resources.require(a,new PermissionProtocol.Scope(row.tenantId(),row.projectId(),"ARTIFACT",row.artifactId()),"artifact.read","DOCUMENT_BODY");
 }
 private ObjectNode input(String tenant,String project,String comparison,String operation,String manifest,String set,String artifact,String write,String event,String context,String session,String key,JsonNode request,String bodyHash,String requestHash,Proofs proof,String dependencyHash,String coreHash,String actor,LocalDateTime created,LocalDateTime deadline,String classification)throws Exception{
  var value=json.object().put("schema_version","1.0.0").put("kind","FIXED_DOCUMENT_COMPARISON_INTENT").put("tenant_id",tenant).put("project_id",project).put("comparison_id",comparison).put("operation_id",operation).put("manifest_id",manifest).put("access_set_id",set).put("artifact_id",artifact).put("write_id",write).put("start_event_id",event).put("request_context",context).put("request_session",session).put("idempotency_key",key).put("request_body_sha256",bodyHash).put("operation_request_sha256",requestHash).put("dependency_sha256",dependencyHash).put("core_sha256",coreHash).put("schema_manifest_sha256",schema.manifest()).put("actor_id",actor).put("created_at",utc(created)).put("deadline",utc(deadline)).put("storage_seconds",policy.storageSeconds()).put("classification",classification);
  if(!created.plusSeconds(policy.storageSeconds()).equals(deadline))bad();same(classification,classification(proof.left().header().classification(),proof.right().header().classification()));
  value.set("request",request);value.set("left",fixed(proof.left()));value.set("right",fixed(proof.right()));value.set("limits",schema.tree(policy.limits()));return value;
 }
 private JsonNode fixed(DocumentReadService.FixedRead proof)throws Exception{
  var header=(ObjectNode)schema.tree(proof.header());for(String volatileField:List.of("current_document_revision","revision","status","project_status","issue_watermark"))header.remove(volatileField);
  // Preserve immutable stored identities, not a snapshot of mutable review/head state.
  header.put("dependency_manifest_sha256",schema.hash(proof.dependencies()));return header;
 }
 private List<ArtifactRegistryProtocol.MemberRow> union(Proofs proof){
  var members=new TreeMap<String,ArtifactRegistryProtocol.MemberRow>();for(var side:List.of(proof.left(),proof.right()))for(var member:side.dependencies().members()){
   var previous=members.putIfAbsent(member.upstreamVersionId(),member);if(previous!=null&&!previous.rightsRevision().equals(member.rightsRevision()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
  }if(members.size()+1>policy.maximumVersions())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return List.copyOf(members.values());
 }
 private S3ObjectWriter.WriteIntent writeIntent(Intent row)throws Exception{return new S3ObjectWriter.WriteIntent(row.writeId(),"documents/"+row.tenantId()+"/"+row.documentId()+"/comparisons/"+row.comparisonId()+"/"+row.artifactId(),row.coreSha256(),Integer.toString(row.canonicalCore().getBytes(java.nio.charset.StandardCharsets.UTF_8).length),"application/json");}
 private SelectionOperationRow lookup(EnterpriseAccess.Admission a,String key)throws Exception{
  var v=values(a);v.put("key",key);var intents=mapper.intentByKey(v);var operations=mapper.operation(v);
  if(intents.isEmpty()&&operations.isEmpty())return null;
  var row=one(intents);var operation=one(operations);same(a.context().tenant_id(),row.tenantId());same(a.actorId(),row.createdBy());same(key,row.idempotencyKey());same(row.operationId(),operation.operationId());same(row.comparisonId(),operation.targetId());same(row.operationRequestSha256(),operation.requestSha256());return operation;
 }
 private Command command(EnterpriseAccess.Admission a,Intent row,String key)throws Exception{
  same(row.idempotencyKey(),key);var v=values(a);base(v,row);v.put("key",key);var op=one(mapper.operation(v));same(row.operationId(),op.operationId());same(row.comparisonId(),op.targetId());same(row.operationRequestSha256(),op.requestSha256());same("PROCESSING",op.state());same("1.0.0",op.schemaVersion());same(schema.manifest(),op.schemaManifestSha256());
  var command=schema.decode(op.responseRef(),Command.class);same(schema.read(op.responseRef()),schema.tree(command));same(schema.hash(row),command.intentSha256());v.put("requestHash",row.operationRequestSha256());var revision=one(mapper.operationRevision(v));if(command.attempted()?revision.compareTo(BigInteger.TWO)<0:!BigInteger.ONE.equals(revision))bad();var attempts=mapper.attempt(v);if(command.attempted())verifyAttempt(a,row);else if(!attempts.isEmpty())bad();if(command.reference()!=null){if(!command.attempted()||revision.compareTo(BigInteger.valueOf(3))<0)bad();verifyReference(writeIntent(row),command.reference());}return command;
 }
 private Attempt verifyAttempt(EnterpriseAccess.Admission a,Intent row)throws Exception{
  var v=values(a);base(v,row);var attempt=one(mapper.attempt(v));same(row.tenantId(),attempt.tenantId());same(row.projectId(),attempt.projectId());same(row.comparisonId(),attempt.comparisonId());same(row.operationId(),attempt.operationId());same(row.writeId(),attempt.writeId());same(schema.hash(row),attempt.intentSha256());same("1.0.0",attempt.schemaVersion());same(row.schemaManifest(),attempt.schemaManifest());same(row.createdBy(),attempt.createdBy());same(row.classification(),attempt.classification());if(attempt.createdAt()==null||attempt.createdAt().isBefore(row.createdAt())||!attempt.createdAt().isBefore(row.deadline()))bad();return attempt;
 }
 private void markAttempt(EnterpriseAccess.Admission a,Intent row,String key,Command previous,Command next)throws Exception{
  same(previous,command(a,row,key));if(previous.attempted()||previous.reference()!=null||!next.attempted()||next.reference()!=null)bad();
  var v=values(a);base(v,row);v.put("key",key);v.put("requestHash",row.operationRequestSha256());v.put("operationRevision",one(mapper.operationRevision(v)));
  var attempt=new Attempt(row.tenantId(),row.projectId(),row.comparisonId(),row.operationId(),row.writeId(),schema.hash(row),"1.0.0",schema.manifest(),row.createdBy(),(LocalDateTime)v.get("now"),row.classification());v.put("row",attempt);count(mapper.insertAttempt(v));
  v.put("commandJson",schema.canonical(next));count(mapper.updateCommand(v));same(next,command(a,row,key));
 }
 private void advance(EnterpriseAccess.Admission a,Intent row,String key,Command previous,Command next)throws Exception{
  same(previous,command(a,row,key));var v=values(a);base(v,row);v.put("key",key);v.put("requestHash",row.operationRequestSha256());v.put("operationRevision",one(mapper.operationRevision(v)));v.put("commandJson",schema.canonical(next));count(mapper.updateCommand(v));same(next,command(a,row,key));
 }
 private JsonNode startEvent(Intent row)throws Exception{return json.object().put("schema_version","1.0.0").put("comparison_id",row.comparisonId()).put("operation_id",row.operationId()).put("intent_sha256",schema.hash(row)).put("input_sha256",row.inputSha256()).put("request_body_sha256",row.requestBodySha256()).put("execution_mode","SYNC_REQUEST");}
 private JsonNode completedEvent(Registration row,Attempt attempt)throws Exception{var result=json.object().put("schema_version","1.0.0").put("comparison_id",row.comparisonId()).put("operation_id",row.operationId()).put("attempt_sha256",schema.hash(attempt)).put("registration_sha256",schema.hash(row)).put("intent_sha256",row.intentSha256());result.set("comparison",schema.receipt(schema.read(row.receiptJson())));return result;}
 private String requestHash(InterfaceProtocol.CompareRequest request)throws Exception{return schema.hash(Map.of("method","POST","path","/api/document-comparisons","schema_manifest",schema.manifest(),"body",request));}
 private void base(Map<String,Object> v,Intent row){v.put("projectId",row.projectId());v.put("comparisonId",row.comparisonId());v.put("operationId",row.operationId());v.put("creatorId",row.createdBy());v.put("startEventId",row.startEventId());v.put("artifactId",row.artifactId());v.put("accessSetId",row.accessSetId());v.put("documentId",row.documentId());v.put("documentRevision",row.rightRevision());v.put("rightRevision",row.rightRevision());v.put("classification",row.classification());v.put("manifest",schema.manifest());}
 private Map<String,Object> values(EnterpriseAccess.Admission a){var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("now",permissions.databaseTime());return values;}
 private <T>T tx(ApplicationSessionIdentity.Verified subject,Checked<T> work){return access.execute(subject,a->ThrowingSupplier.of(()->work.apply(a)).get());}
 @FunctionalInterface private interface Checked<T>{T apply(EnterpriseAccess.Admission admission)throws Exception;}
 private static List<String> ids(JsonNode values){var ids=new ArrayList<String>();for(var value:values)ids.add(value.textValue());return ids;}
 private static void current(EnterpriseAccess.Admission a,String context){if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static String classification(String... values){var levels=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");int highest=0;for(String value:values){int rank=levels.indexOf(value);if(rank<0)bad();highest=Math.max(highest,rank);}return levels.get(highest);}
 private static String utc(LocalDateTime time){return time.toInstant(ZoneOffset.UTC).toString();}private static String id(){return UUID.randomUUID().toString();}
 private static void verifyReference(S3ObjectWriter.WriteIntent intent,InterfaceProtocol.ObjectRef ref){same(intent.objectKey(),ref.objectKey());same(intent.sha256(),ref.sha256());same(intent.sizeBytes(),ref.sizeBytes());same(intent.mediaType(),ref.mediaType());}
 private static <T>T one(List<T> values){if(values.size()!=1||values.get(0)==null)bad();return values.get(0);}private static void count(int n){if(n!=1)bad();}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
