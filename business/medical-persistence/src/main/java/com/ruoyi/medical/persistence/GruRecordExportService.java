package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.mapper.GruRecordExportMapper.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Owner-only export of a complete fixed USER-record prefix. No model execution is implied.
 * The immutable intent and attempt ledger are the authority for original content and external writes.
 * Generic artifact readers cannot substitute for this domain's original-message evidence checks.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="medical",name={"gru-records.enabled","gru-record-export.enabled"},havingValue="true")
@org.springframework.boot.context.properties.EnableConfigurationProperties(GruRecordExportPolicy.class)
public final class GruRecordExportService {
 private static final String PURPOSE="GRU_RECORD_EXPORT",EVENT="gru.record_export.completed";
 private final EnterpriseAccess access;private final GruService records;private final GruRecordExportMapper mapper;
 private final PermissionMapper permissions;private final ResourceReadAuthority resources;private final ArtifactRegistryMapper artifacts;
 private final DependencyLineage lineage;private final SourceUploadTransport transport;private final GruRecordExportPolicy policy;
 private final PermissionSchema json;private final GruRecordExportSchema schema;private final JsonMapper codec=JsonMapper.builder().propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).build();
 public GruRecordExportService(EnterpriseAccess access,GruService records,GruRecordExportMapper mapper,PermissionMapper permissions,
  ResourceReadAuthority resources,ArtifactRegistryMapper artifacts,DependencyLineage lineage,SourceUploadTransport transport,
  GruRecordExportPolicy policy,PermissionSchema json)throws Exception {
  this.access=access;this.records=records;this.mapper=mapper;this.permissions=permissions;this.resources=resources;this.artifacts=artifacts;
  this.lineage=lineage;this.transport=transport;this.policy=policy;this.json=json;schema=new GruRecordExportSchema();policy.requireConfigured();
 }
 public JsonNode basis(ApplicationSessionIdentity.Verified subject,String context,String conversation)throws Exception {
  WireValues.uuid(conversation);var proof=records.exportProof(subject,context,conversation,null);
  return tx(subject,a->{authorize(a,context,proof,true);return basis(a,proof);});
 }
 public JsonNode create(ApplicationSessionIdentity.Verified subject,String context,String conversation,String key,byte[] bytes)throws Exception {
  WireValues.uuid(conversation);GruRecordExportSchema.key(key);PermissionSchema.revision(context);var request=schema.request(bytes);
  same(conversation,text(request.required("basis"),"conversation_id"));
  var proof=records.exportProof(subject,context,conversation,new BigInteger(text(request.required("basis"),"conversation_revision")));
  var intent=tx(subject,a->{
   authorize(a,context,proof,false);var v=values(a);v.put("key",key);
   var old=mapper.intentByKey(v);var operations=mapper.operation(v);bounded(old);bounded(operations);
   String requestHash=schema.requestHash(route(conversation),request,context,subject.sessionId());
   if(!old.isEmpty()) {
    var i=old.get(0);if(!requestHash.equals(i.operationRequestSha256())||!schema.canonical(request).equals(i.canonicalRequest()))conflict();
    verifyIntent(a,context,i,proof);return i;
   }
   if(!operations.isEmpty())bad();
   authorize(a,context,proof,true);same(request.required("basis"),basis(a,proof));
   var document=document(request.required("basis"),proof);byte[] content=schema.canonical(document).getBytes(StandardCharsets.UTF_8);
   if(content.length>policy.maximumBytes())throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
   var now=now();String export=id(),artifact=id(),operation=id();var c=proof.context();
   var write=new S3ObjectWriter.WriteIntent(id(),"gru-record-exports/"+c.tenantId()+"/"+export+"/"+artifact,WireValues.sha256(content),Integer.toString(content.length),"application/json");
   var i=new Intent(c.tenantId(),c.projectId(),export,conversation,a.actorId(),proof.revision(),c.documentId(),c.documentRevision(),c.snapshotId(),
    schema.canonical(request),schema.hash(request),requestHash,operation,key,subject.sessionId(),schema.canonical(a.context()),schema.canonical(request.required("basis")),
    schema.canonical(document.required("message_manifest")),text(document,"message_manifest_sha256"),BigInteger.valueOf(proof.messages().size()),
    new String(content,StandardCharsets.UTF_8),write.sha256(),BigInteger.valueOf(content.length),schema.canonical(write),artifact,c.accessSetId(),schema.hash(proof.document().dependencies()),
    "1.0.0",schema.manifest(),a.actorId(),now,now.plusSeconds(policy.maximumSeconds()),c.classification());
   v=values(a,i);v.put("row",i);v.put("now",now);v.put("commandJson",schema.canonical(command(i)));count(mapper.startOperation(v));count(mapper.insertIntent(v));
   verifyIntent(a,context,i,proof);return i;
  });
  var completed=tx(subject,a->{verifyIntent(a,context,intent,proof);var rows=mapper.registration(values(a,intent));bounded(rows);if(rows.isEmpty())return null;
   verifyRegistration(a,context,intent,rows.get(0),proof,false);return schema.read(rows.get(0).receiptJson());});
  if(completed!=null)return completed;
  // Only a positively committed unique attempt grants first-write authority. A failed/uncertain tx exits here.
  boolean recover=tx(subject,a->{verifyIntent(a,context,intent,proof);writeWindow(intent);same(subject.sessionId(),intent.sessionId());
   var v=values(a,intent);var attempts=mapper.attempt(v);bounded(attempts);if(!attempts.isEmpty()){verifyAttempt(intent,attempts.get(0));same(BigInteger.TWO,one(mapper.operationRevision(v)));return true;}
   same(BigInteger.ONE,one(mapper.operationRevision(v)));v.put("now",now());count(mapper.insertAttempt(v));count(mapper.markAttempted(v));return false;});
  var write=writeIntent(intent);byte[] content=intent.canonicalContent().getBytes(StandardCharsets.UTF_8);
  var reference=transport.writeDocumentProjection(subject,intent.tenantId(),write,content,recover,policy.maximumBytes(),
   ()->tx(subject,a->{verifyWriting(a,context,intent,proof);return null;}));
  return tx(subject,a->{
   verifyIntent(a,context,intent,proof);reference(write,reference);var v=values(a,intent);var existing=mapper.registration(v);bounded(existing);
   if(!existing.isEmpty()){
    verifyRegistration(a,context,intent,existing.get(0),proof,false);
    // A concurrent completion must attest the very same version verified by THIS I/O.
    // Matching bytes/key alone cannot turn a different storage version into this receipt.
    same(reference,codec.treeToValue(schema.read(existing.get(0).objectReference()),InterfaceProtocol.ObjectRef.class));
    return schema.read(existing.get(0).receiptJson());
   }
   verifyWriting(a,context,intent,proof);var now=now();v.put("now",now);v.put("reference",reference);v.put("storedObject",StoredObjectReferences.encode(reference));
   v.put("schemaRef",schema.ref("Document"));v.put("schemaHash",schema.schemaDocumentSha256());v.put("dependencyHash",intent.dependencyManifestSha256());
   count(mapper.insertVersion(v));count(mapper.insertArtifact(v));count(mapper.insertBinding(v));
   for(var member:proof.document().dependencies().members()){v.put("upstreamId",member.upstreamVersionId());count(mapper.insertLineage(v));}
   var receipt=receipt(intent,now);var row=new Registration(intent.tenantId(),intent.projectId(),intent.exportId(),intent.operationId(),intent.artifactId(),intent.accessSetId(),
    intent.dependencyManifestSha256(),StoredObjectReferences.encode(reference),intent.contentSha256(),intent.sizeBytes(),schema.canonical(receipt),id(),id(),"1.0.0",schema.manifest(),a.actorId(),now,intent.classification());
   var event=event(intent,row);count(permissions.saveAudit(a.actorId(),intent.projectId(),row.auditEventId(),EVENT,schema.canonical(event),intent.operationId(),schema.manifest(),intent.classification()));
   count(permissions.saveEvent(a.actorId(),row.outboxEventId(),intent.exportId(),"1",EVENT,schema.canonical(event)));
   v.put("row",row);count(mapper.insertRegistration(v));v.put("response",row.receiptJson());count(mapper.completeOperation(v));
   verifyRegistration(a,context,intent,row,proof,false);return receipt;
  });
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String export)throws Exception {
  var i=load(subject,context,export);var proof=records.exportProof(subject,context,i.conversationId(),i.conversationRevision());
  return tx(subject,a->{var row=available(mapper.registration(values(a,i)));verifyRegistration(a,context,i,row,proof,false);
   var value=json.object().put("schema_version","1.0.0").put("status","AVAILABLE");value.set("receipt",schema.read(row.receiptJson()));value.set("current_context",json.tree(a.context()));return schema.response("View",value);});
 }
 public JsonNode recovery(ApplicationSessionIdentity.Verified subject,String context,String conversation,String key)throws Exception {
  WireValues.uuid(conversation);GruRecordExportSchema.key(key);
  var currentProof=records.exportProof(subject,context,conversation,null);
  var i=tx(subject,a->{authorize(a,context,currentProof,false);var v=values(a);v.put("key",key);var rows=mapper.intentByKey(v);bounded(rows);
   if(rows.isEmpty()){if(!mapper.operation(v).isEmpty())bad();return null;}same(conversation,rows.get(0).conversationId());return rows.get(0);});
  var proof=i==null?currentProof:records.exportProof(subject,context,conversation,i.conversationRevision());
  return tx(subject,a->{authorize(a,context,proof,false);var result=json.object().put("schema_version","1.0.0").put("conversation_id",conversation).put("idempotency_key",key);
   result.set("current_context",json.tree(a.context()));result.putNull("request_body_sha256");result.putNull("receipt");
   if(i==null){var v=values(a);v.put("key",key);if(!mapper.intentByKey(v).isEmpty()||!mapper.operation(v).isEmpty())throw new TenantAccessException(ErrorCode.ACTION_STALE);result.put("status","NOT_FOUND");}
   else{verifyIntent(a,context,i,proof);result.put("request_body_sha256",i.requestBodySha256());var rows=mapper.registration(values(a,i));bounded(rows);
    if(rows.isEmpty())result.put("status","PROCESSING");else{verifyRegistration(a,context,i,rows.get(0),proof,false);result.put("status","COMPLETED");result.set("receipt",schema.read(rows.get(0).receiptJson()));}}
   return schema.response("Recovery",result);
  });
 }
 public record Download(byte[] bytes,String sha256) {public Download{bytes=bytes.clone();}@Override public byte[] bytes(){return bytes.clone();}}
 public Download content(ApplicationSessionIdentity.Verified subject,String context,String export)throws Exception {
  var i=load(subject,context,export);var proof=records.exportProof(subject,context,i.conversationId(),i.conversationRevision());
  var row=tx(subject,a->{var r=available(mapper.registration(values(a,i)));verifyRegistration(a,context,i,r,proof,true);return r;});
  var ref=codec.treeToValue(schema.read(row.objectReference()),InterfaceProtocol.ObjectRef.class);
  return transport.readDocument(subject,i.tenantId(),policy.maximumBytes(),()->tx(subject,a->{verifyRegistration(a,context,i,row,proof,true);return null;}),(reader,current)->{
   try(var actual=reader.readVerified(ref,current)){
    same(ref,actual.reference());same(i.contentSha256(),actual.fullSha256());int length=i.sizeBytes().intValueExact();
    if(length>policy.maximumBytes())throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=new byte[length];
    for(int start=0;start<length;){int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,length-start);System.arraycopy(actual.chunk(start,n),0,bytes,start,n);start+=n;}
    same(i.canonicalContent(),new String(bytes,StandardCharsets.UTF_8));same(i.contentSha256(),WireValues.sha256(bytes));current.requireCurrent();return new Download(bytes,i.contentSha256());
   }
  });
 }
 private Intent load(ApplicationSessionIdentity.Verified subject,String context,String export){WireValues.uuid(export);return tx(subject,a->{current(a,context);var v=values(a);v.put("exportId",export);return available(mapper.intentById(v));});}
 private void authorize(EnterpriseAccess.Admission a,String context,GruService.ExportProof proof,boolean exact)throws Exception {
  policy.requireConfigured();current(a,context);records.requireExportProof(a,context,proof,exact);var c=proof.context();
  resources.require(a,new PermissionProtocol.Scope(c.tenantId(),c.projectId(),"DOCUMENT",c.documentId()),"conversation.export",PURPOSE);
  var members=proof.document().dependencies().members();
  if((long)members.size()+1L>policy.maximumNodes())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  // Preflight the exact reused source closure before any intent/attempt/external write. The new
  // export root points to every real manifest member, so count those edges plus all upstream edges.
  var originalRoot=proof.document().header().bodyArtifactId();
  var graph=lineage.require(originalRoot,members,new DependencyLineage.Limits(policy.maximumNodes(),policy.maximumRelationships()));
  long edges=members.size()+graph.graph().edgeSet().stream().filter(e->!originalRoot.equals(e.consumerVersionId())).count();
  if(edges>policy.maximumRelationships())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
 }
 private JsonNode basis(EnterpriseAccess.Admission a,GruService.ExportProof proof)throws Exception {
  var c=proof.context();var value=json.object().put("schema_version","1.0.0").put("tenant_id",c.tenantId()).put("project_id",c.projectId())
   .put("conversation_id",c.conversationId()).put("owner_id",c.createdBy()).put("conversation_revision",proof.revision().toString())
   .put("document_id",c.documentId()).put("document_revision",c.documentRevision()).put("body_sha256",proof.document().header().bodySha256())
   .put("scope_snapshot_id",c.snapshotId()).put("scope_sha256",schema.hash(schema.read(c.scopeJson()))).put("mode","RECORD_ONLY").put("execution_state","NOT_EXECUTED")
   .put("message_count",proof.messages().size()).put("complete",true).putNull("next_cursor");
  var manifest=value.putArray("message_manifest");for(var message:proof.messages())manifest.add(json.object().put("message_id",message.messageId()).put("sequence",message.sequence().toString()).put("content_sha256",message.contentHash()));
  value.put("message_manifest_sha256",schema.hash(manifest));value.set("current_context",json.tree(a.context()));return schema.response("Basis",value);
 }
 private JsonNode document(JsonNode basis,GruService.ExportProof proof)throws Exception {
  ObjectNode value=basis.deepCopy();value.remove("current_context");value.put("purpose",PURPOSE).put("format","JSON");var messages=value.putArray("messages");
  for(var row:proof.messages()){var full=records.exportMessage(row);messages.add(json.object().put("message_id",row.messageId()).put("sequence",row.sequence().toString())
   .put("text",text(full,"content")).put("intent",row.intent()).put("execution_state",row.executionState()).put("created_at",text(full,"created_at")).put("content_sha256",row.contentHash()));}
  return schema.response("Document",value);
 }
 private void verifyIntent(EnterpriseAccess.Admission a,String context,Intent i,GruService.ExportProof proof)throws Exception {
  authorize(a,context,proof,false);same(i,one(mapper.intent(values(a,i))));same(a.actorId(),i.ownerId());same(a.actorId(),i.createdBy());same(a.context().tenant_id(),i.tenantId());
  same(i,one(mapper.intentByKey(values(a,i))));same("1.0.0",i.schemaVersion());same(schema.manifest(),i.schemaManifest());same(i.classification(),proof.context().classification());
  var request=schema.validate("Request",schema.read(i.canonicalRequest()));same(i.canonicalRequest(),schema.canonical(request));same(i.requestBodySha256(),schema.hash(request));
  var originalContext=schema.read(i.contextJson());same(i.tenantId(),text(originalContext,"tenant_id"));same(originalContext,request.required("basis").required("current_context"));
  same(i.operationRequestSha256(),schema.requestHash(route(i.conversationId()),request,text(originalContext,"context_revision"),i.sessionId()));
  var expected=(ObjectNode)basis(a,proof);expected.set("current_context",originalContext);same(expected,request.required("basis"));same(i.basisJson(),schema.canonical(expected));
  same(i.projectId(),proof.context().projectId());same(i.conversationId(),proof.context().conversationId());same(i.documentId(),proof.context().documentId());same(i.documentRevision(),proof.context().documentRevision());same(i.snapshotId(),proof.context().snapshotId());same(i.conversationRevision(),proof.revision());
  same(i.messageManifestJson(),schema.canonical(expected.required("message_manifest")));same(i.messageManifestSha256(),text(expected,"message_manifest_sha256"));same(i.messageCount(),BigInteger.valueOf(proof.messages().size()));
  same(i.canonicalContent(),schema.canonical(document(expected,proof)));same(i.contentSha256(),WireValues.sha256(i.canonicalContent().getBytes(StandardCharsets.UTF_8)));same(i.sizeBytes(),BigInteger.valueOf(i.canonicalContent().getBytes(StandardCharsets.UTF_8).length));
  if(i.sizeBytes().signum()<=0||i.sizeBytes().compareTo(BigInteger.valueOf(policy.maximumBytes()))>0)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  same(i.accessSetId(),proof.document().header().accessSetId());same(i.dependencyManifestSha256(),schema.hash(proof.document().dependencies()));
  if(i.createdAt()==null||i.deadline()==null||!i.deadline().isAfter(i.createdAt())||i.createdAt().isAfter(now()))bad();
  var write=writeIntent(i);same(i.writeIntentJson(),schema.canonical(write));same(i.contentSha256(),write.sha256());same(i.sizeBytes().toString(),write.sizeBytes());same("application/json",write.mediaType());
  same("gru-record-exports/"+i.tenantId()+"/"+i.exportId()+"/"+i.artifactId(),write.objectKey());WireValues.uuid(write.writeId());
  var v=values(a,i);var op=one(mapper.operation(v));same(op,one(mapper.operationById(v)));same(i.operationId(),op.operationId());same(i.exportId(),op.targetId());same(i.operationRequestSha256(),op.requestSha256());same(i.schemaVersion(),op.schemaVersion());same(i.schemaManifest(),op.schemaManifestSha256());
  var revision=one(mapper.operationRevision(v));var attempts=mapper.attempt(v);bounded(attempts);var registrations=mapper.registration(v);bounded(registrations);
  if(attempts.isEmpty()){same(BigInteger.ONE,revision);same("PROCESSING",op.state());same(null,op.httpStatus());same(command(i),schema.read(op.responseRef()));if(!registrations.isEmpty())bad();}
  else{verifyAttempt(i,attempts.get(0));if(registrations.isEmpty()){same(BigInteger.TWO,revision);same("PROCESSING",op.state());same(null,op.httpStatus());same(command(i),schema.read(op.responseRef()));}
   else{same(BigInteger.valueOf(3),revision);same("COMPLETED",op.state());same(Integer.valueOf(201),op.httpStatus());same(schema.read(registrations.get(0).receiptJson()),schema.read(op.responseRef()));}}
 }
 private void verifyWriting(EnterpriseAccess.Admission a,String context,Intent i,GruService.ExportProof proof)throws Exception {
  verifyIntent(a,context,i,proof);writeWindow(i);var v=values(a,i);verifyAttempt(i,one(mapper.attempt(v)));same(BigInteger.TWO,one(mapper.operationRevision(v)));
 }
 private void verifyAttempt(Intent i,Attempt t){same(i.tenantId(),t.tenantId());same(i.projectId(),t.projectId());same(i.exportId(),t.exportId());same(i.operationId(),t.operationId());same(i.ownerId(),t.createdBy());same(i.schemaVersion(),t.schemaVersion());same(i.schemaManifest(),t.schemaManifest());same(i.classification(),t.classification());same(t.attemptedAt(),t.createdAt());if(t.attemptedAt()==null||t.attemptedAt().isAfter(now())||t.attemptedAt().isBefore(i.createdAt())||!t.attemptedAt().isBefore(i.deadline()))bad();}
 private void verifyRegistration(EnterpriseAccess.Admission a,String context,Intent i,Registration r,GruService.ExportProof proof,boolean download)throws Exception {
  verifyIntent(a,context,i,proof);same(r,one(mapper.registration(values(a,i))));same(i.tenantId(),r.tenantId());same(i.projectId(),r.projectId());same(i.exportId(),r.exportId());same(i.operationId(),r.operationId());same(i.artifactId(),r.artifactId());same(i.accessSetId(),r.accessSetId());same(i.dependencyManifestSha256(),r.dependencyManifestSha256());same(i.contentSha256(),r.contentSha256());same(i.sizeBytes(),r.sizeBytes());same(i.ownerId(),r.createdBy());same(i.classification(),r.classification());same(i.schemaVersion(),r.schemaVersion());same(i.schemaManifest(),r.schemaManifest());
  if(r.createdAt()==null||r.createdAt().isBefore(one(mapper.attempt(values(a,i))).attemptedAt())||r.createdAt().isAfter(i.deadline())||r.createdAt().isAfter(now()))bad();
  same(receipt(i,r.createdAt()),schema.response("Receipt",schema.read(r.receiptJson())));var ref=codec.treeToValue(schema.read(r.objectReference()),InterfaceProtocol.ObjectRef.class);reference(writeIntent(i),ref);
  var artifact=one(artifacts.artifact(i.artifactId()));same(ref,artifact.reference());same(ref,StoredObjectReferences.decode(artifact.storedObject(),artifact.reference()));same(i.tenantId(),artifact.tenantId());same(i.projectId(),artifact.projectId());same(i.artifactId(),artifact.artifactId());same("GRU_RECORD_EXPORT",artifact.artifactKind());same(i.contentSha256(),artifact.contentSha256());same(i.accessSetId(),artifact.accessSetId());same(i.schemaVersion(),artifact.schemaVersion());same(i.schemaManifest(),artifact.schemaManifest());same(i.classification(),artifact.classification());
  var binding=one(artifacts.binding(i.artifactId()));same(i.tenantId(),binding.tenantId());same(i.projectId(),binding.projectId());same(i.artifactId(),binding.artifactId());same("DOCUMENT",binding.resourceKind());same(i.documentId(),binding.resourceId());same(schema.ref("Document"),binding.schemaRef());same(schema.schemaDocumentSha256(),binding.schemaDocumentSha256());same(i.accessSetId(),binding.accessSetId());same(i.dependencyManifestSha256(),binding.dependencyManifestSha256());
  var version=one(artifacts.version(i.artifactId()));same(i.artifactId(),version.id());same(i.projectId(),version.projectId());same("ARTIFACT",version.entityKind());same(i.contentSha256(),version.contentSha256());
  var v=values(a,i);var owner=one(mapper.artifactOwner(v));same(i.documentRevision(),owner.documentRevision());same(null,owner.runId());lineage.require(i.artifactId(),proof.document().dependencies().members(),new DependencyLineage.Limits(policy.maximumNodes(),policy.maximumRelationships()));
  v.put("auditEventId",r.auditEventId());v.put("outboxEventId",r.outboxEventId());var evidence=event(i,r);same(evidence,schema.read(one(mapper.audit(v))));same(evidence,schema.read(one(mapper.event(v))));
  if(download)resources.require(a,new PermissionProtocol.Scope(i.tenantId(),i.projectId(),"ARTIFACT",i.artifactId()),"artifact.read",PURPOSE);
 }
 private JsonNode receipt(Intent i,LocalDateTime now)throws Exception{return schema.response("Receipt",json.object().put("schema_version","1.0.0").put("export_id",i.exportId()).put("operation_id",i.operationId()).put("conversation_id",i.conversationId()).put("conversation_revision",i.conversationRevision().toString()).put("document_id",i.documentId()).put("document_revision",i.documentRevision()).put("message_manifest_sha256",i.messageManifestSha256()).put("request_body_sha256",i.requestBodySha256()).put("artifact_id",i.artifactId()).put("content_sha256",i.contentSha256()).put("size_bytes",i.sizeBytes().toString()).put("media_type","application/json").put("format","JSON").put("mode","RECORD_ONLY").put("execution_state","NOT_EXECUTED").put("created_at",utc(now)));}
 private JsonNode command(Intent i)throws Exception{return json.object().put("schema_version","1.0.0").put("export_id",i.exportId()).put("intent_sha256",schema.hash(i));}
 private JsonNode event(Intent i,Registration r)throws Exception{var node=json.object().put("schema_version","1.0.0").put("intent_sha256",schema.hash(i)).put("registration_sha256",schema.hash(r));node.set("receipt",schema.read(r.receiptJson()));return node;}
 private S3ObjectWriter.WriteIntent writeIntent(Intent i)throws Exception{return codec.treeToValue(schema.read(i.writeIntentJson()),S3ObjectWriter.WriteIntent.class);}
 private static void reference(S3ObjectWriter.WriteIntent w,InterfaceProtocol.ObjectRef r){same(w.objectKey(),r.objectKey());same(w.sha256(),r.sha256());same(w.sizeBytes(),r.sizeBytes());same(w.mediaType(),r.mediaType());if(r.objectVersion()==null||r.objectVersion().isBlank())bad();}
 private void writeWindow(Intent i){if(!now().isBefore(i.deadline()))throw new TenantAccessException(ErrorCode.ACTION_STALE);}
 private LocalDateTime now(){return Objects.requireNonNull(permissions.databaseTime());}
 private static Map<String,Object> values(EnterpriseAccess.Admission a){var v=new HashMap<String,Object>();v.put("userId",a.actorId());return v;}
 private Map<String,Object> values(EnterpriseAccess.Admission a,Intent i){var v=values(a);v.put("projectId",i.projectId());v.put("exportId",i.exportId());v.put("operationId",i.operationId());v.put("key",i.idempotencyKey());v.put("requestHash",i.operationRequestSha256());v.put("sessionId",i.sessionId());v.put("artifactId",i.artifactId());v.put("documentId",i.documentId());v.put("documentRevision",i.documentRevision());v.put("accessSetId",i.accessSetId());v.put("contentSha256",i.contentSha256());v.put("manifest",i.schemaManifest());v.put("classification",i.classification());return v;}
 private interface Work<T>{T run(EnterpriseAccess.Admission a)throws Exception;}
 private <T>T tx(ApplicationSessionIdentity.Verified subject,Work<T> work){return access.execute(subject,a->ThrowingSupplier.of(()->work.run(a)).get());}
 private static void current(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static String route(String c){return "/api/gru-conversations/"+c+"/exports";}
 private static String utc(LocalDateTime t){return t.toInstant(java.time.ZoneOffset.UTC).toString();}
 private static String text(JsonNode n,String key){return n.required(key).textValue();}
 private static String id(){return UUID.randomUUID().toString();}
 private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}
 private static void bounded(List<?> rows){if(rows.size()>1)bad();}
 private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}
 private static <T>T available(List<T> rows){if(rows.isEmpty())throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return one(rows);}
 private static void count(int n){if(n!=1)bad();}
 private static void conflict(){throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
