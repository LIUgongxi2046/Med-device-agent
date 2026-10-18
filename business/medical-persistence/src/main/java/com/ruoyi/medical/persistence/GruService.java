package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;
/** Persistent owner-only work records. A completed persistence operation never means an executed question. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="medical.gru-records",name="enabled",havingValue="true")
public final class GruService {
 private static final String CREATE="GRU-RECORD-CREATE",APPEND="GRU-RECORD-APPEND";
 private final EnterpriseAccess access;private final GruMapper mapper;private final PermissionMapper permissions;
 private final DocumentReadService documents;private final GruContextAuthority authority;private final PermissionSchema json;private final GruSchema schema;
 public GruService(EnterpriseAccess access,GruMapper mapper,PermissionMapper permissions,DocumentReadService documents,GruContextAuthority authority,PermissionSchema json)throws Exception{
  this.access=access;this.mapper=mapper;this.permissions=permissions;this.documents=documents;this.authority=authority;this.json=json;schema=new GruSchema();
 }
 /** Package-only verified prefix proof for the narrow RECORD_ONLY export domain. */
 record ExportProof(GruProtocol.Context context,DocumentReadService.FixedRead document,BigInteger revision,List<GruProtocol.Message> messages) {
  ExportProof { messages=List.copyOf(messages); }
 }
 ExportProof exportProof(ApplicationSessionIdentity.Verified subject,String context,String conversation,BigInteger revision)throws Exception {
  var initial=context(subject,context,conversation,false);
  var proof=documents.readFixed(subject,context,initial.documentId(),initial.documentRevision());
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   var v=values(a);v.put("conversationId",conversation);same(initial,one(mapper.context(v)));var h=head(a,conversation);
   var fixed=authority.verify(a,context,schema.read(initial.canonicalRequest()),proof,false);verifyContext(a,initial,h,proof,fixed);
   var rows=history(a,initial,h,proof);var selected=revision==null?h.revision():revision;
   if(selected.signum()<=0||selected.compareTo(h.revision())>0||selected.compareTo(BigInteger.valueOf(GruSchema.MAX_MESSAGES+1L))>0)
    throw new TenantAccessException(ErrorCode.ACTION_STALE);
   return new ExportProof(initial,proof,selected,rows.subList(0,selected.intValueExact()-1));
  }).get());
 }
 void requireExportProof(EnterpriseAccess.Admission a,String context,ExportProof proof,boolean exactHead)throws Exception {
  var c=proof.context();var v=values(a);v.put("conversationId",c.conversationId());same(c,one(mapper.context(v)));var h=head(a,c.conversationId());
  var fixed=authority.verify(a,context,schema.read(c.canonicalRequest()),proof.document(),false);verifyContext(a,c,h,proof.document(),fixed);
  if(exactHead&&!h.revision().equals(proof.revision())||h.revision().compareTo(proof.revision())<0)throw new TenantAccessException(ErrorCode.ACTION_STALE);
  var rows=history(a,c,h,proof.document());same(proof.messages(),rows.subList(0,proof.revision().intValueExact()-1));
 }
 JsonNode exportMessage(GruProtocol.Message row)throws Exception{return message(row);}
 public JsonNode fixedContext(ApplicationSessionIdentity.Verified subject,String context,String document,String revision)throws Exception {
  WireValues.uuid(document);WireValues.uuid(revision);PermissionSchema.revision(context);
  var proof=documents.readFixed(subject,context,document,revision);
  return access.execute(subject,a->ThrowingSupplier.of(()->authority.describe(a,context,proof)).get());
 }
 public JsonNode create(ApplicationSessionIdentity.Verified subject,String context,String key,JsonNode request)throws Exception{
  schema.validate("Create",request);SimulationInputArtifactSchema.key(key);PermissionSchema.revision(context);
  access.execute(subject,a->ThrowingSupplier.of(()->{authority.authorize(a,context,request,true);return true;}).get());
  var proof=documents.readFixed(subject,context,text(request,"document_id"),text(request,"document_revision"));
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   var fixed=authority.verify(a,context,request,proof,true);var v=values(a);v.put("key",key);v.put("operationName",CREATE);
   String requestHash=schema.requestHash("/api/gru-conversations",request,context,subject.sessionId());var existing=mapper.operation(v);if(existing.size()>1)mismatch();
   if(!existing.isEmpty()){
    var op=existing.get(0);sameRequest(requestHash,op);v.put("conversationId",op.targetId());var saved=one(mapper.context(v));
    same(schema.canonical(request),saved.canonicalRequest());verifyContext(a,saved,head(a,saved.conversationId()),proof,fixed);return schema.read(saved.receiptJson());
   }
   var now=Objects.requireNonNull(permissions.databaseTime());String conversation=id(),operation=id();
   var receipt=receipt(conversation,null,operation,BigInteger.ONE,schema.hash(request));var h=proof.header();
   var row=new GruProtocol.Context(a.context().tenant_id(),conversation,h.projectId(),h.documentId(),h.documentRevision(),h.snapshotId(),
    schema.canonical(request.required("scope")),schema.hash(fixed),h.accessSetId(),schema.canonical(request),schema.hash(request),requestHash,operation,
    schema.canonical(receipt),"1.0.0",schema.manifest(),a.actorId(),now,h.classification());
   v.put("row",row);v.put("conversationId",conversation);v.put("projectId",h.projectId());v.put("documentId",h.documentId());v.put("now",now);v.put("classification",h.classification());
   count(mapper.insertConversation(v));saveOperation(a,v,CREATE,key,requestHash,operation,conversation,receipt);count(mapper.insertContext(v));
   saveEvidence(a,operation,conversation,"gru.record.created",event(row),h.projectId(),h.classification());
   same(row,one(mapper.context(v)));verifyContext(a,row,head(a,conversation),proof,fixed);authority.verify(a,context,request,proof,true);return receipt;
  }).get());
 }
 public JsonNode append(ApplicationSessionIdentity.Verified subject,String context,String conversation,String key,JsonNode request)throws Exception{
  WireValues.uuid(conversation);schema.validate("Append",request);SimulationInputArtifactSchema.key(key);
  var initial=context(subject,context,conversation,true);var create=schema.read(initial.canonicalRequest());
  sameContext(create,request);var proof=documents.readFixed(subject,context,initial.documentId(),initial.documentRevision());schema.selection(initial.documentRevision(),proof.body(),request.required("selection"));
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   var v=values(a);v.put("conversationId",conversation);same(initial,one(mapper.context(v)));var h=head(a,conversation);
   var fixed=authority.verify(a,context,create,proof,true);verifyContext(a,initial,h,proof,fixed);var history=history(a,initial,h,proof);
   String requestHash=schema.requestHash("/api/gru-conversations/"+conversation+"/messages",request,context,subject.sessionId());
   v.put("key",key);v.put("operationName",APPEND);var existing=mapper.operation(v);if(existing.size()>1)mismatch();
   if(!existing.isEmpty()){
    var op=existing.get(0);sameRequest(requestHash,op);var row=history.stream().filter(m->m.operationId().equals(op.operationId())).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
    same(schema.canonical(request),row.canonicalRequest());return schema.read(row.receiptJson());
   }
   if(!"ACTIVE".equals(h.status())||!h.revision().toString().equals(text(request,"expected_revision")))throw new TenantAccessException(ErrorCode.ACTION_STALE);
   if(history.size()>=GruSchema.MAX_MESSAGES)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
   var now=Objects.requireNonNull(permissions.databaseTime());String message=id(),operation=id();var sequence=h.revision().add(BigInteger.ONE);
   var value=json.object().put("message_id",message).put("conversation_id",conversation).put("project_id",initial.projectId()).put("document_revision",initial.documentRevision())
    .put("sender_kind","USER").put("content",text(request,"text")).putNull("run_id").put("operation_id",operation).put("created_at",utc(now));
   value.set("selection",request.required("selection"));value.set("scope",request.required("scope"));value.putArray("candidate_ids");schema.message(value);
   var inline=json.object().put("schema_version","1.0.0").put("storage_kind","INLINE_CANONICAL_MESSAGE").put("content_sha256",schema.hash(value));inline.set("message",value);
   var receipt=receipt(conversation,message,operation,sequence,schema.hash(request));String classification=initial.classification();
   var row=new GruProtocol.Message(initial.tenantId(),initial.projectId(),conversation,message,initial.snapshotId(),initial.documentRevision(),schema.canonical(request.required("selection")),
    schema.canonical(inline),schema.hash(value),initial.accessSetId(),operation,"[]","USER","1.0.0",schema.manifest(),a.actorId(),now,classification,sequence,
    schema.canonical(request),schema.hash(request),requestHash,schema.canonical(receipt),text(request,"intent"),"NOT_EXECUTED");
   v.put("message",row);v.put("row",initial);v.put("projectId",initial.projectId());v.put("now",now);v.put("classification",classification);
   v.put("expectedRevision",h.revision());v.put("nextRevision",sequence);v.put("messageId",message);v.put("hash",row.contentHash());
   count(mapper.advanceConversation(v));saveOperation(a,v,APPEND,key,requestHash,operation,conversation,receipt);
   count(mapper.insertVersion(v));count(mapper.insertMessage(v));count(mapper.insertMessageRecord(v));
   for(String upstream:parents(proof)){v.put("upstream",upstream);count(mapper.insertLineage(v));}
   saveEvidence(a,operation,message,"gru.message.recorded",event(row),initial.projectId(),classification);
   var after=head(a,conversation);same(sequence,after.revision());history(a,initial,after,proof);authority.verify(a,context,create,proof,true);return receipt;
  }).get());
 }
 public JsonNode recoverAppend(ApplicationSessionIdentity.Verified subject,String context,String conversation,JsonNode recovery)throws Exception{
  WireValues.uuid(conversation);schema.validate("RecoveryRequest",recovery);var request=recovery.required("request");String key=text(recovery,"idempotency_key");
  var initial=context(subject,context,conversation,false);var create=schema.read(initial.canonicalRequest());sameContext(create,request);
  var proof=documents.readFixed(subject,context,initial.documentId(),initial.documentRevision());schema.selection(initial.documentRevision(),proof.body(),request.required("selection"));
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   var v=values(a);v.put("conversationId",conversation);v.put("key",key);
   // Current locking reads share the exact head used by append CAS. No mutation or operation is recorded here.
   var locked=available(mapper.lockHead(v));same(locked,head(a,conversation));same(initial,one(mapper.context(v)));
   var fixed=authority.verify(a,context,create,proof,false);verifyContext(a,initial,locked,proof,fixed);var rows=history(a,initial,locked,proof);
   var operations=mapper.recoveryOperation(v);if(operations.size()>1)mismatch();
   String hash=schema.hash(request),status="UNKNOWN";JsonNode receipt=null;
   if(!operations.isEmpty()){
    var op=operations.get(0);if(!conversation.equals(op.targetId()))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
    var row=rows.stream().filter(m->m.operationId().equals(op.operationId())).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
    if(!hash.equals(row.requestBodyHash())||!schema.canonical(request).equals(row.canonicalRequest()))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
    same(schema.read(row.receiptJson()),schema.read(op.responseRef()));same(row.operationRequestHash(),op.requestSha256());same("1.0.0",op.schemaVersion());same(schema.manifest(),op.schemaManifestSha256());same("COMPLETED",op.state());same(Integer.valueOf(201),op.httpStatus());
    receipt=schema.read(row.receiptJson());status="FOUND";
   }else if(locked.revision().compareTo(new BigInteger(text(request,"expected_revision")))>0)status="NOT_APPLIED_STALE";
   var result=json.object().put("schema_version","1.0.0").put("status",status).put("actor_id",a.actorId()).put("conversation_id",conversation)
    .put("idempotency_key",key).put("request_body_sha256",hash).put("expected_revision",text(request,"expected_revision"))
    .put("current_revision",locked.revision().toString()).put("checked_at",utc(Objects.requireNonNull(permissions.databaseTime())));
   result.set("current_context",json.tree(a.context()));result.set("receipt",json.tree(receipt));return schema.response("Recovery",result);
  }).get());
 }
 public JsonNode receipt(ApplicationSessionIdentity.Verified subject,String context,String conversation,String operation)throws Exception {
  WireValues.uuid(operation);return readInternal(subject,context,conversation,1,1,null,operation);
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String conversation,int page,int size,String watermark)throws Exception{
  return readInternal(subject,context,conversation,page,size,watermark,null);
 }
 private JsonNode readInternal(ApplicationSessionIdentity.Verified subject,String context,String conversation,int page,int size,String watermark,String operation)throws Exception{
  WireValues.uuid(conversation);if(page<1||size<1||size>100)invalid();
  var initial=context(subject,context,conversation,false);var request=schema.read(initial.canonicalRequest());
  var proof=documents.readFixed(subject,context,initial.documentId(),initial.documentRevision());
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   var v=values(a);v.put("conversationId",conversation);same(initial,one(mapper.context(v)));var h=head(a,conversation);var fixed=authority.verify(a,context,request,proof,false);
   verifyContext(a,initial,h,proof,fixed);var rows=history(a,initial,h,proof);
   if(operation!=null){
    if(operation.equals(initial.operationId()))return schema.read(initial.receiptJson());
    for(var row:rows)if(operation.equals(row.operationId()))return schema.read(row.receiptJson());
    throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);
   }
   var identity=json.object().put("conversation_id",conversation).put("revision",h.revision().toString()).put("actor_id",a.actorId())
    .put("context_revision",context).put("auth_epoch",a.context().auth_epoch()).put("governance_epoch",a.context().governance_epoch());
   identity.set("message_hashes",json.tree(rows.stream().map(GruProtocol.Message::contentHash).toList()));String current=schema.hash(identity);
   if(watermark!=null&&!watermark.equals(current))throw new TenantAccessException(ErrorCode.CURSOR_EXPIRED);
   long start=(long)(page-1)*size;if(start>rows.size()&&page!=1)throw new TenantAccessException(ErrorCode.CURSOR_INVALID);int from=(int)Math.min(start,rows.size()),end=Math.min(from+size,rows.size());
   var result=view(initial,h);var items=result.putArray("messages");var records=result.putArray("message_records");
   for(var row:rows.subList(from,end)){items.add(message(row));records.add(json.object().put("message_id",row.messageId()).put("intent",row.intent()).put("execution_state","NOT_EXECUTED").put("revision",row.sequence().toString()));}
   var paging=result.putObject("page_info");paging.put("page",page);paging.put("page_size",size);paging.put("total",Integer.toString(rows.size()));paging.put("dataset_watermark",current);
   if(end<rows.size())paging.put("next_page",page+1);else paging.putNull("next_page");return schema.response("Read",result);
  }).get());
 }
 public JsonNode list(ApplicationSessionIdentity.Verified subject,String context,String project)throws Exception{
  WireValues.uuid(project);PermissionSchema.revision(context);
  var ids=access.execute(subject,a->ThrowingSupplier.of(()->{authority.project(a,context,project);var v=values(a);v.put("projectId",project);return List.copyOf(mapper.listed(v));}).get());
  if(ids.size()>100)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);var items=json.object().arrayNode();
  var proofs=new HashMap<String,DocumentReadService.FixedRead>();
  if(new HashSet<>(ids).size()!=ids.size())mismatch();
  for(String id:ids){try {
   var initial=context(subject,context,id,false);var request=schema.read(initial.canonicalRequest());
   String identity=initial.tenantId()+":"+initial.projectId()+":"+initial.documentId()+":"+initial.documentRevision();
   var proof=proofs.get(identity);if(proof==null){proof=documents.readFixed(subject,context,initial.documentId(),initial.documentRevision());proofs.put(identity,proof);}
   var fixedProof=proof;
   var item=access.execute(subject,a->ThrowingSupplier.of(()->{
    var v=values(a);v.put("conversationId",id);same(initial,one(mapper.context(v)));var h=head(a,id);
    documents.requireCurrent(a,fixedProof);var fixed=authority.verify(a,context,request,fixedProof,false);verifyContext(a,initial,h,fixedProof,fixed);
    var counts=one(mapper.messageCounts(v));same(counts.messages(),counts.records());
    if(counts.messages().signum()<0||counts.messages().compareTo(BigInteger.valueOf(GruSchema.MAX_MESSAGES))>0)mismatch();
    same(h.revision(),counts.messages().add(BigInteger.ONE));
    if(!h.equals(head(a,id)))throw new TenantAccessException(ErrorCode.ACTION_STALE);
    return view(initial,h);
   }).get());items.add(item);
  }catch(TenantAccessException e){if(e.code()!=ErrorCode.ACCESS_DENIED&&e.code()!=ErrorCode.OBJECT_UNAVAILABLE)throw e;}}
  var result=json.object().put("schema_version","1.0.0").put("project_id",project).put("mode","RECORD_ONLY");result.set("items",items);return schema.response("List",result);
 }
 private GruProtocol.Context context(ApplicationSessionIdentity.Verified subject,String context,String conversation,boolean writing){
  return access.execute(subject,a->ThrowingSupplier.of(()->{var v=values(a);v.put("conversationId",conversation);var c=available(mapper.context(v));head(a,conversation);
   authority.authorize(a,context,schema.request("Create",c.canonicalRequest().getBytes(java.nio.charset.StandardCharsets.UTF_8)),writing);return c;}).get());
 }
 private GruProtocol.Head head(EnterpriseAccess.Admission a,String conversation){var v=values(a);v.put("conversationId",conversation);var h=available(mapper.head(v));
  same(a.context().tenant_id(),h.tenantId());same(a.actorId(),h.ownerId());same(conversation,h.conversationId());if(!"ACTIVE".equals(h.status()))throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return h;}
 private void verifyContext(EnterpriseAccess.Admission a,GruProtocol.Context c,GruProtocol.Head h,DocumentReadService.FixedRead proof,TaskProtocol.ScopeSnapshotManifest fixed)throws Exception{
  same(c.tenantId(),h.tenantId());same(c.projectId(),h.projectId());same(c.documentId(),h.documentId());same(c.conversationId(),h.conversationId());same(c.createdBy(),h.ownerId());same(c.createdBy(),h.createdBy());same(c.createdAt(),h.createdAt());same(c.classification(),h.classification());
  same("1.0.0",c.schemaVersion());same(schema.manifest(),c.schemaManifest());var request=schema.request("Create",c.canonicalRequest().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  same(c.canonicalRequest(),schema.canonical(request));same(c.requestBodyHash(),schema.hash(request));same(c.projectId(),text(request,"project_id"));same(c.documentId(),text(request,"document_id"));
  same(c.documentRevision(),text(request,"document_revision"));same(c.snapshotId(),text(request,"scope_snapshot_id"));same(c.scopeJson(),schema.canonical(request.required("scope")));
  same(c.scopeManifestHash(),schema.hash(fixed));same(c.accessSetId(),proof.header().accessSetId());same(c.classification(),proof.header().classification());
  same(receipt(c.conversationId(),null,c.operationId(),BigInteger.ONE,c.requestBodyHash()),schema.read(c.receiptJson()));
  verifyEvidence(a,c.operationId(),CREATE,c.conversationId(),c.operationRequestHash(),c.receiptJson(),event(c),c.createdBy(),c.projectId(),c.classification(),"gru.record.created");
 }
 private List<GruProtocol.Message> history(EnterpriseAccess.Admission a,GruProtocol.Context c,GruProtocol.Head h,DocumentReadService.FixedRead proof)throws Exception{
  var v=values(a);v.put("conversationId",c.conversationId());var rows=mapper.messages(v);if(rows.size()>GruSchema.MAX_MESSAGES)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  if(!h.revision().equals(BigInteger.valueOf(rows.size()+1L)))mismatch();BigInteger sequence=BigInteger.ONE;
  for(var row:rows){sequence=sequence.add(BigInteger.ONE);same(sequence,row.sequence());same(c.tenantId(),row.tenantId());same(c.projectId(),row.projectId());same(c.conversationId(),row.conversationId());same(c.createdBy(),row.createdBy());
   same(c.documentRevision(),row.documentRevision());same(c.snapshotId(),row.snapshotId());same(c.accessSetId(),row.accessSetId());same(c.classification(),row.classification());same("USER",row.senderKind());same("NOT_EXECUTED",row.executionState());same("[]",row.candidateIds());same("1.0.0",row.schemaVersion());same(schema.manifest(),row.schemaManifest());
   v.put("messageId",row.messageId());v.put("projectId",c.projectId());v.put("creatorId",row.createdBy());
   same(row.contentHash(),one(mapper.version(v)));same(parents(proof),new TreeSet<>(mapper.parents(v)));
   var request=schema.request("Append",row.canonicalRequest().getBytes(java.nio.charset.StandardCharsets.UTF_8));same(row.canonicalRequest(),schema.canonical(request));same(row.requestBodyHash(),schema.hash(request));sameContext(schema.read(c.canonicalRequest()),request);
   same(sequence.subtract(BigInteger.ONE).toString(),text(request,"expected_revision"));same(row.intent(),text(request,"intent"));schema.selection(c.documentRevision(),proof.body(),request.required("selection"));
   var message=message(row);same(row.contentHash(),schema.hash(message));same(row.messageId(),text(message,"message_id"));same(c.conversationId(),text(message,"conversation_id"));same(c.projectId(),text(message,"project_id"));same(c.documentRevision(),text(message,"document_revision"));same(row.operationId(),text(message,"operation_id"));same(utc(row.createdAt()),text(message,"created_at"));
   same(request.required("scope"),message.required("scope"));same(request.required("selection"),message.required("selection"));same(schema.canonical(request.required("selection")),row.selectionJson());same(text(request,"text"),text(message,"content"));
   same(receipt(c.conversationId(),row.messageId(),row.operationId(),sequence,row.requestBodyHash()),schema.read(row.receiptJson()));verifyEvidence(a,row.operationId(),APPEND,c.conversationId(),row.operationRequestHash(),row.receiptJson(),event(row),row.createdBy(),row.projectId(),row.classification(),"gru.message.recorded");
  }return List.copyOf(rows);
 }
 private JsonNode message(GruProtocol.Message row)throws Exception{
  var content=schema.read(row.contentRef());if(!content.isObject()||content.size()!=4) mismatch();same("1.0.0",text(content,"schema_version"));same("INLINE_CANONICAL_MESSAGE",text(content,"storage_kind"));same(row.contentHash(),text(content,"content_sha256"));return schema.message(content.required("message"));
 }
 private com.fasterxml.jackson.databind.node.ObjectNode view(GruProtocol.Context c,GruProtocol.Head h)throws Exception{
  var result=json.object().put("schema_version","1.0.0").put("conversation_id",c.conversationId()).put("project_id",c.projectId()).put("document_id",c.documentId())
   .put("document_revision",c.documentRevision()).put("scope_snapshot_id",c.snapshotId()).put("revision",h.revision().toString()).put("status",h.status()).put("owner_id",h.ownerId())
   .put("mode","RECORD_ONLY").put("execution_state","NOT_EXECUTED").put("created_at",utc(c.createdAt()));result.set("scope",schema.read(c.scopeJson()));schema.response("View",result);return result;
 }
 private JsonNode receipt(String conversation,String message,String operation,BigInteger revision,String requestBodyHash){var r=json.object().put("schema_version","1.0.0").put("conversation_id",conversation).put("operation_id",operation).put("revision",revision.toString()).put("execution_state","NOT_EXECUTED").put("request_body_sha256",requestBodyHash);r.set("message_id",json.tree(message));return schema.response("Receipt",r);}
 private JsonNode event(GruProtocol.Context c)throws Exception{var e=json.object().put("conversation_id",c.conversationId()).put("request_body_sha256",c.requestBodyHash()).put("operation_request_sha256",c.operationRequestHash()).put("scope_manifest_sha256",c.scopeManifestHash()).put("schema_manifest_sha256",c.schemaManifest());e.set("request",schema.read(c.canonicalRequest()));e.set("receipt",schema.read(c.receiptJson()));return e;}
 private JsonNode event(GruProtocol.Message m)throws Exception{var e=json.object().put("message_id",m.messageId()).put("conversation_id",m.conversationId()).put("request_body_sha256",m.requestBodyHash()).put("operation_request_sha256",m.operationRequestHash()).put("content_sha256",m.contentHash()).put("schema_manifest_sha256",m.schemaManifest());e.set("request",schema.read(m.canonicalRequest()));e.set("receipt",schema.read(m.receiptJson()));return e;}
 private void saveOperation(EnterpriseAccess.Admission a,Map<String,Object> v,String name,String key,String hash,String op,String target,JsonNode receipt)throws Exception{
  v.put("operationName",name);v.put("key",key);v.put("requestHash",hash);v.put("operationId",op);v.put("targetId",target);v.put("response",schema.canonical(receipt));v.put("manifest",schema.manifest());count(mapper.saveOperation(v));
 }
 private void saveEvidence(EnterpriseAccess.Admission a,String operation,String target,String action,JsonNode event,String project,String classification)throws Exception{
  String content=schema.canonical(event);count(permissions.saveAudit(a.actorId(),project,id(),action,content,operation,schema.manifest(),classification));count(permissions.saveEvent(a.actorId(),operation,target,"1",action,content));
 }
 private void verifyEvidence(EnterpriseAccess.Admission a,String operation,String name,String target,String hash,String receipt,JsonNode event,String creator,String project,String classification,String action)throws Exception{
  var v=values(a);v.put("operationId",operation);v.put("operationName",name);v.put("creatorId",creator);v.put("projectId",project);v.put("classification",classification);v.put("manifest",schema.manifest());v.put("action",action);v.put("targetId",action.equals("gru.message.recorded")?text(event,"message_id"):target);
  var op=one(mapper.operationById(v));same(operation,op.operationId());same(target,op.targetId());same(hash,op.requestSha256());same("COMPLETED",op.state());same(Integer.valueOf(201),op.httpStatus());same("1.0.0",op.schemaVersion());same(schema.manifest(),op.schemaManifestSha256());same(schema.read(receipt),schema.read(op.responseRef()));
  same(event,schema.read(one(mapper.audit(v))));same(event,schema.read(one(mapper.event(v))));
 }
 private static void sameContext(JsonNode create,JsonNode request){for(String name:List.of("document_revision","scope_snapshot_id","scope"))same(create.required(name),request.required(name));}
 private static Set<String> parents(DocumentReadService.FixedRead proof){
  var result=new TreeSet<>(proof.dependencies().members().stream().map(ArtifactRegistryProtocol.MemberRow::upstreamVersionId).toList());result.add(proof.header().documentRevision());return result;
 }
 private static String utc(LocalDateTime t){return t.toInstant(java.time.ZoneOffset.UTC).toString();}
 private static String text(JsonNode n,String f){return n.required(f).textValue();}
 private static String id(){return UUID.randomUUID().toString();}
 private static Map<String,Object> values(EnterpriseAccess.Admission a){var v=new HashMap<String,Object>();v.put("userId",a.actorId());return v;}
 private static <T>T available(List<T> rows){if(rows.isEmpty())throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return one(rows);}
 private static <T>T one(List<T> rows){if(rows.size()!=1)mismatch();return rows.get(0);}
 private static void same(Object a,Object b){if(!Objects.equals(a,b))mismatch();}
 private static void sameRequest(String hash,SelectionOperationRow row){if(!hash.equals(row.requestSha256()))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);}
 private static void count(int count){if(count!=1)mismatch();}
 private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
