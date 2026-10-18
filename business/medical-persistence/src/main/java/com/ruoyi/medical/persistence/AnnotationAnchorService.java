package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Human-only fixed-version reanchor. Original annotation and 058 decision identity never mutate. */
@Service
public final class AnnotationAnchorService {
 private static final String ACTION="review.decide",PURPOSE="DOCUMENT_REVIEW",OPERATION="RV-06-REANCHOR-1";
 private final EnterpriseAccess access;private final AnnotationAnchorMapper mapper;private final AnnotationThreadMapper threads;
 private final DocumentReadMapper reads;private final PermissionMapper permissions;private final ResourceReadAuthority authority;
 private final DocumentReadService documents;private final AnnotationThreadService selections;private final AnnotationDecisionService decisions;
 private final SourceUploadTransport transport;private final DocumentUploadPolicy storage;private final AnnotationAnchorSchema schema;
 private final AnnotationThreadSchema threadSchema;private final PermissionSchema json;
 public AnnotationAnchorService(EnterpriseAccess access,AnnotationAnchorMapper mapper,AnnotationThreadMapper threads,DocumentReadMapper reads,
   PermissionMapper permissions,ResourceReadAuthority authority,DocumentReadService documents,AnnotationThreadService selections,
   AnnotationDecisionService decisions,SourceUploadTransport transport,DocumentUploadPolicy storage,AnnotationAnchorSchema schema,AnnotationThreadSchema threadSchema,PermissionSchema json){
  this.access=access;this.mapper=mapper;this.threads=threads;this.reads=reads;this.permissions=permissions;this.authority=authority;this.documents=documents;
  this.selections=selections;this.decisions=decisions;this.transport=transport;this.storage=storage;this.schema=schema;this.threadSchema=threadSchema;this.json=json;
 }
 public JsonNode reanchor(ApplicationSessionIdentity.Verified subject,String context,String key,String annotationId,byte[] body)throws Exception{
  json.headers(context,key);json.requireId(annotationId);var request=schema.create(body);String hash=schema.requestHash(annotationId,request,context,subject.sessionId());
  var initial=transaction(subject,a->{context(a,context);var v=values(subject,annotationId);var loc=single(mapper.locator(v));grant(a,loc,ACTION);
   var rows=mapper.operation(withOperation(v,key,hash));if(rows.size()>1)mismatch();if(rows.isEmpty())return null;operation(a,rows.get(0),key,hash,annotationId);return rows.get(0);});
  if(initial!=null&&"COMPLETED".equals(initial.state()))return replay(subject,context,key,annotationId,hash,request,initial);
  var proof=prepare(subject,context,annotationId,request.documentRevision(),ACTION);
  var selected=DocumentSelectionSemantics.verify(threadSchema.shared(),request.documentRevision(),proof.target().body(),request.selection());
  byte[] bytes=selected.canonicalJson().getBytes(StandardCharsets.UTF_8);if(bytes.length>maximumBytes())fail(ErrorCode.FILE_TOO_LARGE);
  if(bytes.length+json.canonical(json.object().put("reason",request.reason())).getBytes(StandardCharsets.UTF_8).length+4096>AnnotationThreadSchema.MAXIMUM_ANNOTATION_BYTES)fail(ErrorCode.CONTEXT_LIMIT);
  var command=transaction(subject,a->{var v=current(a,context,proof,ACTION);requireNew(proof,request);withOperation(v,key,hash);
   var rows=mapper.operation(v);if(rows.size()>1)mismatch();if(!rows.isEmpty()){operation(a,rows.get(0),key,hash,annotationId);return command(rows.get(0),proof,request,selected);}
   if(historyCount(v)>=AnnotationAnchorSchema.MAXIMUM_HISTORY)fail(ErrorCode.CONTEXT_LIMIT);
   String artifact=id();var intent=new S3ObjectWriter.WriteIntent(id(),"documents/"+proof.locator().tenantId()+"/"+proof.locator().documentId()+"/"+request.documentRevision()+"/annotations/"+artifact,selected.sha256(),Integer.toString(bytes.length),"application/json");
   var c=new AnnotationAnchorProtocol.Command(id(),artifact,id(),id(),proofHash(proof),proof.clock(),intent,false,null);
   v.put("operationId",id());v.put("commandJson",json.canonicalObject(c));v.put("now",permissions.databaseTime());one(mapper.startOperation(v));return c;});
  var writing=transaction(subject,a->{var v=current(a,context,proof,ACTION);requireNew(proof,request);withOperation(v,key,hash);var op=single(mapper.operation(v));operation(a,op,key,hash,annotationId);
   var c=command(op,proof,request,selected);if(!c.equals(command))fail(ErrorCode.SCOPE_STALE);if(!c.writing())update(v,op,withReference(c,true,null));return c;});
  // Actual immutable S3 bytes are written AND reread by writeVerified/recoverVerified, outside the transaction.
  var reference=transport.writeDocumentProjection(subject,proof.locator().tenantId(),writing.intent(),bytes,writing.writing(),maximumBytes(),()->transaction(subject,a->{current(a,context,proof,ACTION);requireNew(proof,request);return null;}));
  transaction(subject,a->{var v=current(a,context,proof,ACTION);requireNew(proof,request);withOperation(v,key,hash);var op=single(mapper.operation(v));operation(a,op,key,hash,annotationId);
   var c=command(op,proof,request,selected);requireReference(c.intent(),reference);if(!c.writing()||c.reference()!=null&&!c.reference().equals(reference))mismatch();update(v,op,withReference(c,true,reference));return null;});
  return transaction(subject,a->finish(a,context,key,hash,request,proof,selected));
 }
 private JsonNode finish(EnterpriseAccess.Admission admission,String context,String key,String hash,AnnotationAnchorProtocol.Create r,Proof p,DocumentSelectionSemantics.Verified selected)throws Exception{
  var v=current(admission,context,p,ACTION);requireNew(p,r);withOperation(v,key,hash);var op=single(mapper.operation(v));operation(admission,op,key,hash,p.origin().annotationId());var c=command(op,p,r,selected);
  if(!c.writing()||c.reference()==null)mismatch();requireReference(c.intent(),c.reference());
  if(historyCount(v)>=AnnotationAnchorSchema.MAXIMUM_HISTORY)fail(ErrorCode.CONTEXT_LIMIT);
  v.put("documentRevision",r.documentRevision());v.put("reviewLimit",AnnotationThreadSchema.MAXIMUM_INVALIDATIONS+1);var reviews=threads.confirmations(v);
  if(reviews.size()>AnnotationThreadSchema.MAXIMUM_INVALIDATIONS)fail(ErrorCode.CONTEXT_LIMIT);var reviewIds=new ArrayList<String>();
  for(var review:reviews){json.requireId(review.reviewId());if(!r.documentRevision().equals(review.documentRevision())||!"CONFIRM".equals(review.decision())||!"DOCUMENT".equals(review.targetKey())||!reviewIds.isEmpty()&&review.reviewId().compareTo(reviewIds.get(reviewIds.size()-1))<=0)mismatch();reviewIds.add(review.reviewId());}
  var now=Objects.requireNonNull(permissions.databaseTime());String result=PermissionSchema.next(r.expectedRevision()),head=PermissionSchema.next(r.expectedDocumentRevision());
  String issues=PermissionSchema.next(p.target().watermarks().issueWatermark().toString());var witness=grant(admission,p.locator(),ACTION);
  v.put("now",now);v.put("anchorId",c.anchorId());v.put("artifactId",c.artifactId());v.put("anchorNodeId",selected.anchorNodeId());v.put("selectionJson",selected.canonicalJson());v.put("selectionSha256",selected.sha256());
  v.put("reference",c.reference());v.put("storedObject",StoredObjectReferences.encode(c.reference()));v.put("accessSetId",p.target().dependencies().access_set_id());v.put("dependencyHash",schema.hash(p.target().dependencies()));
  v.put("artifactManifest",p.target().bodyIntent().artifact().artifactSchemaManifest());v.put("interfacesHash",p.target().bodyIntent().artifact().schemaDocumentSha256());v.put("selectionSchema",threadSchema.shared().schemaRef("interfaces","Selection"));
  v.put("previousAnchorId",p.pointer().anchorId());v.put("expectedAnchorRevision",r.expectedAnchorRevision());v.put("expected",r.expectedRevision());v.put("resulting",result);v.put("previousStatus",p.origin().status());v.put("status","OPEN");
  v.put("replyCount",p.origin().replyCount());v.put("reason",r.reason());v.put("kind","REANCHOR");v.put("transitionId",c.transitionId());v.put("expectedRevision",r.expectedDocumentRevision());
  v.put("issueWatermark",p.target().watermarks().issueWatermark());v.put("evidenceWatermark",p.target().watermarks().evidenceWatermark());
  one(threads.saveVersion(v));one(threads.saveArtifact(v));one(threads.saveBinding(v));for(var member:p.target().dependencies().members()){v.put("upstreamId",member.upstreamVersionId());one(threads.saveLineage(v));}
  var registered=selections.registration(admission,p.target(),c.artifactId(),selected.sha256());if(!registered.registration().artifact().reference().equals(c.reference()))mismatch();
  one(mapper.insertAnchor(v));one(mapper.advanceAnnotation(v));one(mapper.advancePointer(v));one(mapper.insertTransition(v));one(threads.advanceHead(v));one(threads.advanceIssues(v));
  for(String review:reviewIds){v.put("reviewId",review);v.put("invalidationId",id());one(mapper.invalidateReview(v));}
  var after=single(threads.originalAnnotation(v));var pointer=single(mapper.pointer(v));var anchor=single(mapper.anchor(v));
  if(!after.revision().toString().equals(result)||!after.status().equals("OPEN")||!after.replyCount().equals(p.origin().replyCount())||!sameOrigin(p.origin(),after)
    ||!pointer.anchorId().equals(c.anchorId())||!pointer.revision().toString().equals(PermissionSchema.next(r.expectedAnchorRevision())))mismatch();
  var saved=single(reads.header(v));var marks=single(reads.watermarks(v));if(!AnnotationThreadService.sameAdvancedHeader(p.target().header(),saved,head)||!saved.documentRevision().equals(r.documentRevision())||!saved.currentDocumentRevision().equals(r.documentRevision())
   ||!saved.bodySha256().equals(p.target().header().bodySha256())||!marks.issueWatermark().toString().equals(issues)||!marks.evidenceWatermark().equals(p.target().watermarks().evidenceWatermark()))mismatch();
  var transition=schema.read(single(mapper.transition(v)));
  if(!transition.required("kind").asText().equals("REANCHOR")||!transition.required("previous_revision").asText().equals(r.expectedRevision())||!transition.required("revision").asText().equals(result)
   ||!transition.required("previous_status").asText().equals("REANCHOR_REQUIRED")||!transition.required("status").asText().equals("OPEN")||!transition.required("anchor_id").asText().equals(c.anchorId())
   ||!transition.required("previous_anchor_id").asText().equals(p.pointer().anchorId())||!transition.required("document_revision").asText().equals(r.documentRevision())||!transition.required("actor_id").asText().equals(admission.subject().user().user_id())||!transition.required("reason").asText().equals(r.reason()))mismatch();
  var item=anchor(anchor);if(!item.required("selection").equals(threadSchema.read(selected.canonicalJson()))||!r.reason().equals(anchor.reason())||!anchor.actorId().equals(admission.subject().user().user_id())||!anchor.annotationRevision().toString().equals(result))mismatch();
  verifyInvalidations(v,reviewIds);var receipt=base(p.locator().documentId(),r.documentRevision(),result).put("annotation_id",p.origin().annotationId()).put("status","OPEN")
   .put("operation_id",op.operationId()).put("previous_revision",r.expectedRevision()).put("previous_document_revision",r.expectedDocumentRevision()).put("document_head_revision",head)
   .put("previous_anchor_id",p.pointer().anchorId()).put("previous_anchor_revision",r.expectedAnchorRevision()).put("anchor_revision",pointer.revision().toString())
   .put("issue_watermark",issues).put("evidence_watermark",marks.evidenceWatermark().toString());receipt.set("effective_anchor",item);receipt.put("effective_anchor_sha256",schema.hash(item));receipt.set("invalidated_review_ids",json.tree(reviewIds));receipt.put("receipt_sha256",schema.hash(receipt));
  var response=schema.envelope("Receipt",receipt,c.requestId(),result);schema.receipt(json.canonical(response));v.put("operationId",op.operationId());v.put("operationRevision",op.revision());v.put("response",json.canonical(response));one(mapper.completeOperation(v));
  var event=event(receipt,c.transitionId());var audit=json.object().put("schema_version","1.0.0").put("operation_id",op.operationId()).put("request_sha256",hash)
   .put("authority_grant_id",witness.grantId()).put("authority_revision",witness.revision().toString());audit.set("after",event);
  one(permissions.saveAudit(admission.subject().user().user_id(),p.locator().projectId(),id(),"annotation.reanchored",json.canonical(audit),c.requestId(),schema.manifestSha256(),p.origin().classification()));
  one(permissions.saveEvent(admission.subject().user().user_id(),id(),p.origin().annotationId(),result,"annotation.reanchored",json.canonical(event)));
  v.put("requestId",c.requestId());if(!schema.read(single(mapper.audit(v))).equals(audit)||!schema.read(single(mapper.event(v))).equals(event))mismatch();
  var completed=single(mapper.operation(v));operation(admission,completed,key,hash,p.origin().annotationId());if(!"COMPLETED".equals(completed.state())||!schema.receipt(completed.responseRef()).equals(response))mismatch();
  grant(admission,p.locator(),ACTION);clock(p.clock(),v);return response;
 }
 Proof prepare(ApplicationSessionIdentity.Verified subject,String context,String annotationId,String targetRevision,String action)throws Exception{
  json.requireId(annotationId);PermissionSchema.revision(context);
  var captured=transaction(subject,a->{context(a,context);var v=values(subject,annotationId);var loc=single(mapper.locator(v));grant(a,loc,action);v.put("projectId",loc.projectId());
   var origin=single(threads.originalAnnotation(v));var pointer=single(mapper.pointer(v));v.put("anchorId",pointer.anchorId());var anchor=single(mapper.anchor(v));
   v.put("anchorId",annotationId);var originalAnchor=single(mapper.anchor(v));requireOrigin(originalAnchor,origin);
   return new Captured(loc,origin,pointer,anchor,Objects.requireNonNull(threads.authorizationClock(v)));});
  var original=documents.readFixed(subject,context,captured.locator().documentId(),captured.origin().documentRevision());
  var effective=captured.anchor().documentRevision().equals(original.header().documentRevision())?original:documents.readFixed(subject,context,captured.locator().documentId(),captured.anchor().documentRevision());
  var target=targetRevision==null||targetRevision.equals(effective.header().documentRevision())?effective:targetRevision.equals(original.header().documentRevision())?original:documents.readFixed(subject,context,captured.locator().documentId(),targetRevision);
  var proof=new Proof(captured.locator(),captured.origin(),captured.pointer(),captured.anchor(),original,effective,target,captured.clock());
  transaction(subject,a->{current(a,context,proof,action);return null;});
  selections.readSelection(subject,context,original,proof.origin(),action,proof.clock());readAnchor(subject,context,effective,proof.origin(),proof.anchor(),action,proof.clock());
  transaction(subject,a->{current(a,context,proof,action);return null;});return proof;
 }
 Map<String,Object> current(EnterpriseAccess.Admission a,String context,Proof p,String action)throws Exception{
  context(a,context);grant(a,p.locator(),action);documents.requireCurrent(a,p.original());documents.requireCurrent(a,p.effective());documents.requireCurrent(a,p.target());
  var v=values(a.subject(),p.origin().annotationId());v.put("projectId",p.locator().projectId());v.put("documentId",p.locator().documentId());v.put("classification",p.origin().classification());v.put("manifest",schema.manifestSha256());
  if(!single(mapper.locator(v)).equals(p.locator())||!single(threads.originalAnnotation(v)).equals(p.origin())||!single(mapper.pointer(v)).equals(p.pointer()))fail(ErrorCode.SCOPE_STALE);
  v.put("anchorId",p.pointer().anchorId());if(!single(mapper.anchor(v)).equals(p.anchor()))fail(ErrorCode.SCOPE_STALE);clock(p.clock(),v);
  for(var fixed:List.of(p.original(),p.effective(),p.target()))if(!fixed.header().tenantId().equals(p.locator().tenantId())||!fixed.header().projectId().equals(p.locator().projectId())||!fixed.header().documentId().equals(p.locator().documentId())||!fixed.header().classification().equals(p.origin().classification()))mismatch();
  decisions.verifyLifecycle(new AnnotationReplyProtocol.Parent(p.locator().tenantId(),p.locator().projectId(),p.origin().annotationId(),p.locator().documentId(),p.anchor().documentRevision(),p.effective().header().currentDocumentRevision(),p.effective().header().bodySha256(),p.effective().header().revision(),p.origin().revision(),p.origin().status(),p.origin().classification()),p.origin().documentRevision(),v);
  verifyLedger(v,p);return v;
 }
 private void requireNew(Proof p,AnnotationAnchorProtocol.Create r){
  if(p.origin().revision().compareTo(BigInteger.valueOf(10001))>=0)fail(ErrorCode.CONTEXT_LIMIT);
  var h=p.target().header();if("SIGNED".equals(h.status()))fail(ErrorCode.DOCUMENT_SIGNED);if(!"ACTIVE".equals(h.projectStatus()))fail(ErrorCode.ACTION_STALE);
  if(!"REANCHOR_REQUIRED".equals(p.origin().status())||!r.expectedRevision().equals(p.origin().revision().toString())||!r.expectedAnchorId().equals(p.pointer().anchorId())
   ||!r.expectedAnchorRevision().equals(p.pointer().revision().toString())||!r.expectedDocumentRevision().equals(h.revision().toString())||!r.documentRevision().equals(h.documentRevision())||!h.documentRevision().equals(h.currentDocumentRevision()))fail(ErrorCode.REVISION_CONFLICT);
 }
 private JsonNode replay(ApplicationSessionIdentity.Verified subject,String context,String key,String id,String hash,AnnotationAnchorProtocol.Create r,UploadOperationRow initial)throws Exception{
  var response=schema.receipt(initial.responseRef());var d=response.required("data");var saved=d.required("effective_anchor");
  if(!d.required("operation_id").asText().equals(initial.operationId())||!d.required("annotation_id").asText().equals(id)||!d.required("document_revision").asText().equals(r.documentRevision())
   ||!d.required("previous_revision").asText().equals(r.expectedRevision())||!d.required("previous_document_revision").asText().equals(r.expectedDocumentRevision())
   ||!d.required("previous_anchor_id").asText().equals(r.expectedAnchorId())||!d.required("previous_anchor_revision").asText().equals(r.expectedAnchorRevision())
   ||!saved.required("reason").asText().equals(r.reason())||!saved.required("actor_id").asText().equals(subject.user().user_id())||!json.canonical(saved.required("selection")).equals(json.canonicalObject(r.selection())))mismatch();
  var p=prepare(subject,context,id,r.documentRevision(),ACTION);
  var historic=transaction(subject,a->{var v=current(a,context,p,ACTION);withOperation(v,key,hash);var op=single(mapper.operation(v));operation(a,op,key,hash,id);if(!op.equals(initial))fail(ErrorCode.SCOPE_STALE);
   v.put("anchorId",saved.required("anchor_id").asText());var anchor=single(mapper.anchor(v));if(!anchor(anchor).equals(saved))mismatch();return anchor;});
  readAnchor(subject,context,p.target(),p.origin(),historic,ACTION,p.clock());
  var previousAnchor=transaction(subject,a->{var v=current(a,context,p,ACTION);v.put("anchorId",historic.previousAnchorId());return single(mapper.anchor(v));});
  var previousFixed=documents.readFixed(subject,context,p.locator().documentId(),previousAnchor.documentRevision());
  readAnchor(subject,context,previousFixed,p.origin(),previousAnchor,ACTION,p.clock());
  return transaction(subject,a->{var v=current(a,context,p,ACTION);withOperation(v,key,hash);var op=single(mapper.operation(v));operation(a,op,key,hash,id);if(!op.equals(initial))fail(ErrorCode.SCOPE_STALE);
   documents.requireCurrent(a,previousFixed);v.put("anchorId",previousAnchor.anchorId());if(!single(mapper.anchor(v)).equals(previousAnchor))fail(ErrorCode.SCOPE_STALE);
   v.put("anchorId",historic.anchorId());if(!single(mapper.anchor(v)).equals(historic)||p.origin().revision().compareTo(historic.annotationRevision())<0)mismatch();
   v.put("requestId",response.required("request_id").asText());v.put("resulting",d.required("revision").asText());v.put("artifactId",historic.selectionArtifactId());
   var audit=schema.read(single(mapper.audit(v)));var event=schema.read(single(mapper.event(v)));String transitionId=event.required("transition_id").asText();v.put("transitionId",transitionId);
   if(!audit.required("operation_id").asText().equals(op.operationId())||!audit.required("request_sha256").asText().equals(hash)||!audit.required("after").equals(event)||!event.equals(event(d,transitionId)))mismatch();
   var transition=schema.read(single(mapper.transition(v)));if(!transition.required("kind").asText().equals("REANCHOR")||!transition.required("revision").equals(d.required("revision"))
    ||!transition.required("previous_revision").equals(d.required("previous_revision"))||!transition.required("anchor_id").equals(saved.required("anchor_id"))||!transition.required("previous_anchor_id").equals(d.required("previous_anchor_id"))
    ||!transition.required("document_revision").equals(d.required("document_revision"))||!transition.required("previous_status").asText().equals("REANCHOR_REQUIRED")||!transition.required("status").asText().equals("OPEN")
    ||!transition.required("actor_id").equals(saved.required("actor_id"))||!transition.required("reason").equals(saved.required("reason")))mismatch();
   var ids=new ArrayList<String>();d.required("invalidated_review_ids").forEach(x->ids.add(x.asText()));verifyInvalidations(v,ids);clock(p.clock(),v);return response;});
 }
 public JsonNode list(ApplicationSessionIdentity.Verified subject,String context,String documentId,String revision)throws Exception{
  json.requireId(documentId);json.requireId(revision);PermissionSchema.revision(context);
  var fixed=documents.readFixed(subject,context,documentId,revision);
  var locators=transaction(subject,a->{context(a,context);documents.requireCurrent(a,fixed);var v=values(subject,null);v.put("projectId",fixed.header().projectId());v.put("documentId",documentId);v.put("fetchLimit",AnnotationThreadSchema.MAXIMUM_ANNOTATIONS+1);
   var loc=new AnnotationAnchorProtocol.Locator(fixed.header().tenantId(),fixed.header().projectId(),documentId,null,null);grant(a,loc,"annotation.read");
   Long count=mapper.documentCount(v);if(count==null||count<0)mismatch();if(count>AnnotationThreadSchema.MAXIMUM_ANNOTATIONS)fail(ErrorCode.CONTEXT_LIMIT);var rows=mapper.documentAnnotations(v);if(rows.size()!=count)mismatch();return rows;});
  var proofs=new ArrayList<Proof>();var items=json.object().arrayNode();
  for(var loc:locators){var p=prepare(subject,context,loc.annotationId(),revision,"annotation.read");proofs.add(p);
   if(!revision.equals(fixed.header().currentDocumentRevision())&&!revision.equals(p.origin().documentRevision())&&!revision.equals(p.anchor().documentRevision()))continue;
   var item=json.object();item.set("annotation",selections.annotation(p.origin(),p.original(),threadSchema.read(p.origin().selectionJson())));item.set("effective_anchor",anchor(p.anchor()));item.put("anchor_revision",p.pointer().revision().toString());
   item.put("location_status","REANCHOR_REQUIRED".equals(p.origin().status())?"REANCHOR_REQUIRED":revision.equals(fixed.header().currentDocumentRevision())&&p.anchor().documentRevision().equals(revision)?"CURRENT_VERSION":"ORIGINAL_VERSION");items.add(item);
  }
  return transaction(subject,a->{context(a,context);documents.requireCurrent(a,fixed);grant(a,new AnnotationAnchorProtocol.Locator(fixed.header().tenantId(),fixed.header().projectId(),documentId,null,null),"annotation.read");for(var p:proofs)current(a,context,p,"annotation.read");
   var v=values(subject,null);v.put("projectId",fixed.header().projectId());v.put("documentId",documentId);v.put("fetchLimit",AnnotationThreadSchema.MAXIMUM_ANNOTATIONS+1);
   if(!locators.equals(mapper.documentAnnotations(v))||!Long.valueOf(locators.size()).equals(mapper.documentCount(v)))fail(ErrorCode.SCOPE_STALE);
   var d=base(documentId,revision,fixed.header().revision().toString()).put("issue_watermark",fixed.watermarks().issueWatermark().toString()).put("evidence_watermark",fixed.watermarks().evidenceWatermark().toString()).put("complete",true).put("total",Integer.toString(items.size()));d.set("items",items);d.put("listing_sha256",schema.hash(d));return schema.envelope("Listing",d,id(),fixed.header().revision().toString());});
 }
 public JsonNode history(ApplicationSessionIdentity.Verified subject,String context,String annotationId)throws Exception{
  var p=prepare(subject,context,annotationId,null,"annotation.read");
  var rows=transaction(subject,a->{var v=current(a,context,p,"annotation.read");long count=historyCount(v);if(count>AnnotationAnchorSchema.MAXIMUM_HISTORY)fail(ErrorCode.CONTEXT_LIMIT);v.put("fetchLimit",AnnotationAnchorSchema.MAXIMUM_HISTORY+1);var result=mapper.history(v);if(result.size()!=count)mismatch();return result;});
  var items=json.object().arrayNode();var fixeds=new ArrayList<DocumentReadService.FixedRead>();String previous=null;BigInteger previousRevision=BigInteger.ZERO;
  for(var row:rows){if(previous==null)requireOrigin(row,p.origin());else if(!previous.equals(row.previousAnchorId())||row.annotationRevision().compareTo(previousRevision)<=0)mismatch();
   var fixed=documents.readFixed(subject,context,p.locator().documentId(),row.documentRevision());fixeds.add(fixed);readAnchor(subject,context,fixed,p.origin(),row,"annotation.read",p.clock());items.add(anchor(row));previous=row.anchorId();previousRevision=row.annotationRevision();}
  if(!p.pointer().anchorId().equals(previous)||!p.pointer().revision().equals(BigInteger.valueOf(rows.size())))mismatch();
  var lifecycle=decisions.lifecycle(subject,context,annotationId);
  return transaction(subject,a->{var v=current(a,context,p,"annotation.read");for(var fixed:fixeds)documents.requireCurrent(a,fixed);v.put("fetchLimit",AnnotationAnchorSchema.MAXIMUM_HISTORY+1);if(!rows.equals(mapper.history(v))||historyCount(v)!=rows.size())fail(ErrorCode.SCOPE_STALE);
   var d=base(p.locator().documentId(),p.anchor().documentRevision(),p.origin().revision().toString()).put("annotation_id",annotationId).put("status",p.origin().status()).put("anchor_revision",p.pointer().revision().toString()).put("effective_anchor_id",p.pointer().anchorId()).put("complete",true).put("total",Integer.toString(rows.size()));
   d.set("items",items);d.set("lifecycle",lifecycle);d.put("listing_sha256",schema.hash(d));return schema.envelope("History",d,id(),p.origin().revision().toString());});
 }
 private void verifyLedger(Map<String,Object> v,Proof p)throws Exception {
  v.put("fetchLimit",AnnotationAnchorSchema.MAXIMUM_HISTORY+1);var anchors=mapper.history(v);if(anchors.isEmpty()||anchors.size()>AnnotationAnchorSchema.MAXIMUM_HISTORY||historyCount(v)!=anchors.size())mismatch();
  requireOrigin(anchors.get(0),p.origin());var byId=new HashMap<String,AnnotationAnchorProtocol.Anchor>();for(var anchor:anchors)if(byId.put(anchor.anchorId(),anchor)!=null)mismatch();
  var transitions=mapper.transitions(v);if(transitions.size()>10000)fail(ErrorCode.CONTEXT_LIMIT);String cursor=p.origin().annotationId();int reanchors=0;BigInteger previous=BigInteger.ONE;
  for(String raw:transitions){var t=schema.read(raw);BigInteger revision=PermissionSchema.revision(t.required("revision").asText());
   if(!t.required("document_id").asText().equals(p.locator().documentId())||revision.compareTo(previous)<=0||revision.compareTo(p.origin().revision())>0||!PermissionSchema.next(t.required("previous_revision").asText()).equals(revision.toString())||!cursor.equals(t.required("previous_anchor_id").asText()))mismatch();
   if(t.required("kind").asText().equals("INVALIDATE")){if(!cursor.equals(t.required("anchor_id").asText())||!"REANCHOR_REQUIRED".equals(t.required("status").asText()))mismatch();}
   else if(t.required("kind").asText().equals("REANCHOR")){var next=byId.get(t.required("anchor_id").asText());if(next==null||!cursor.equals(next.previousAnchorId())||!next.annotationRevision().equals(revision)||!next.documentRevision().equals(t.required("document_revision").asText())||!next.actorId().equals(t.required("actor_id").asText())||!Objects.equals(next.reason(),t.required("reason").asText())||!"REANCHOR_REQUIRED".equals(t.required("previous_status").asText())||!"OPEN".equals(t.required("status").asText()))mismatch();cursor=next.anchorId();reanchors++;}
   else mismatch();previous=revision;
  }
  if(reanchors+1!=anchors.size()||!cursor.equals(p.pointer().anchorId())||!p.pointer().revision().equals(BigInteger.valueOf(anchors.size())))mismatch();
 }
 JsonNode readAnchor(ApplicationSessionIdentity.Verified s,String context,DocumentReadService.FixedRead fixed,AnnotationThreadProtocol.Row origin,AnnotationAnchorProtocol.Anchor anchor,String action,String clock)throws Exception{
  if(!anchor.annotationId().equals(origin.annotationId())||!anchor.tenantId().equals(origin.tenantId())||!anchor.projectId().equals(origin.projectId())||!anchor.documentRevision().equals(fixed.header().documentRevision())||!anchor.classification().equals(fixed.header().classification())||!anchor.actorId().equals(anchor.createdBy()))mismatch();
  var projected=new AnnotationThreadProtocol.Row(origin.tenantId(),origin.projectId(),origin.annotationId(),anchor.documentRevision(),anchor.nodeId(),anchor.selectionJson(),anchor.selectionArtifactId(),anchor.selectionSha256(),origin.status(),origin.content(),origin.actorId(),origin.schemaVersion(),origin.schemaManifest(),origin.createdBy(),origin.createdAt(),origin.classification(),origin.revision(),origin.updatedAt(),origin.replyCount());
  var selection=selections.readSelection(s,context,fixed,projected,action,clock);
  transaction(s,a->{context(a,context);documents.requireCurrent(a,fixed);clock(clock,values(s,origin.annotationId()));var registered=selections.registration(a,fixed,anchor.selectionArtifactId(),anchor.selectionSha256());if(!registered.registration().artifact().reference().objectVersion().equals(anchor.selectionObjectVersion()))mismatch();return null;});return selection;
 }
 ObjectNode anchor(AnnotationAnchorProtocol.Anchor a)throws Exception{
  if(!"1.0.0".equals(a.schemaVersion())||!(a.previousAnchorId()==null?threadSchema.manifestSha256():schema.manifestSha256()).equals(a.schemaManifest()))mismatch();
  var n=json.object().put("anchor_id",a.anchorId()).put("document_revision",a.documentRevision()).put("selection_artifact_id",a.selectionArtifactId()).put("selection_sha256",a.selectionSha256())
    .put("annotation_revision",a.annotationRevision().toString()).put("actor_id",a.actorId()).put("created_at",a.createdAt().toInstant(ZoneOffset.UTC).toString());
  if(a.reason()==null)n.putNull("reason");else n.put("reason",a.reason());if(a.previousAnchorId()==null)n.putNull("previous_anchor_id");else n.put("previous_anchor_id",a.previousAnchorId());n.set("selection",threadSchema.read(a.selectionJson()));schema.anchor(n);return n;
 }
 private void requireOrigin(AnnotationAnchorProtocol.Anchor a,AnnotationThreadProtocol.Row r)throws Exception{
  if(!a.anchorId().equals(r.annotationId())||!a.annotationId().equals(r.annotationId())||!a.tenantId().equals(r.tenantId())||!a.projectId().equals(r.projectId())||!a.documentRevision().equals(r.documentRevision())
   ||!a.nodeId().equals(r.nodeId())||!a.selectionArtifactId().equals(r.selectionArtifactId())||!a.selectionSha256().equals(r.selectionSha256())||!threadSchema.read(a.selectionJson()).equals(threadSchema.read(r.selectionJson()))
   ||a.previousAnchorId()!=null||a.reason()!=null||!a.annotationRevision().equals(BigInteger.ONE)||!a.actorId().equals(r.actorId())||!a.createdBy().equals(r.createdBy())||!a.createdAt().equals(r.createdAt())||!a.classification().equals(r.classification()))mismatch();
 }
 private AnnotationAnchorProtocol.Command command(UploadOperationRow op,Proof p,AnnotationAnchorProtocol.Create r,DocumentSelectionSemantics.Verified selected)throws Exception{
  if(!"PROCESSING".equals(op.state())||op.httpStatus()!=null)fail(ErrorCode.REVISION_CONFLICT);var c=schema.command(op.responseRef());
  if(!c.authorizationSha256().equals(proofHash(p))||!c.authorizationClock().equals(p.clock()))fail(ErrorCode.SCOPE_STALE);
  for(String value:List.of(c.anchorId(),c.artifactId(),c.requestId(),c.transitionId()))json.requireId(value);
  if(!c.intent().sha256().equals(selected.sha256())||!c.intent().sizeBytes().equals(Integer.toString(selected.canonicalJson().getBytes(StandardCharsets.UTF_8).length))||!"application/json".equals(c.intent().mediaType())
   ||!c.intent().objectKey().equals("documents/"+p.locator().tenantId()+"/"+p.locator().documentId()+"/"+r.documentRevision()+"/annotations/"+c.artifactId())||c.reference()!=null&&!c.writing())mismatch();
  if(c.reference()!=null)requireReference(c.intent(),c.reference());return c;
 }
 private String proofHash(Proof p)throws Exception{return schema.hash(List.of(p.locator(),selections.annotation(p.origin(),p.original(),threadSchema.read(p.origin().selectionJson())),p.origin().updatedAt().toString(),p.pointer(),anchor(p.anchor()),p.anchor().selectionObjectVersion(),p.original().digest(),p.effective().digest(),p.target().digest(),p.clock()));}
 private void update(Map<String,Object> v,UploadOperationRow op,AnnotationAnchorProtocol.Command c)throws Exception{
  v.put("operationId",op.operationId());v.put("operationRevision",op.revision());v.put("now",permissions.databaseTime());v.put("commandJson",json.canonicalObject(c));one(mapper.updateCommand(v));if(!schema.command(single(mapper.operation(v)).responseRef()).equals(c))mismatch();
 }
 private void operation(EnterpriseAccess.Admission a,UploadOperationRow op,String key,String hash,String annotationId){
  if(!hash.equals(op.requestSha256()))fail(ErrorCode.IDEMPOTENCY_CONFLICT);
  if(!a.context().tenant_id().equals(op.tenantId())||!a.subject().user().user_id().equals(op.subjectId())||!op.subjectId().equals(op.createdBy())||!annotationId.equals(op.targetId())||!key.equals(op.idempotencyKey())||!OPERATION.equals(op.operationName())
   ||!schema.manifestSha256().equals(op.schemaManifestSha256())||!"1.0.0".equals(op.schemaVersion())||!Set.of("PROCESSING","COMPLETED").contains(op.state())||op.errorCode()!=null||op.revision().signum()<=0
   ||op.createdAt()==null||op.updatedAt()==null||op.updatedAt().isBefore(op.createdAt())||op.dedupExpiresAt()!=null||op.dedupPolicyRevision()!=null||"COMPLETED".equals(op.state())&&!Integer.valueOf(201).equals(op.httpStatus()))mismatch();
 }
 private void verifyInvalidations(Map<String,Object> v,List<String> expected){var rows=mapper.invalidations(v);if(rows.size()!=expected.size())mismatch();var ids=new HashSet<String>();for(int i=0;i<rows.size();i++){var row=rows.get(i);json.requireId(row.invalidationId());if(!ids.add(row.invalidationId())||!row.reviewId().equals(expected.get(i))||!row.triggerVersion().equals(v.get("artifactId"))||!row.reasonCode().equals("ANNOTATION_REANCHORED"))mismatch();}}
 private ObjectNode event(JsonNode receipt,String transitionId){var event=((ObjectNode)receipt).deepCopy();var anchor=event.remove("effective_anchor");event.put("anchor_id",anchor.required("anchor_id").asText()).put("selection_artifact_id",anchor.required("selection_artifact_id").asText()).put("transition_id",transitionId);return event;}
 static boolean sameOrigin(AnnotationThreadProtocol.Row a,AnnotationThreadProtocol.Row b){return new AnnotationThreadProtocol.Row(a.tenantId(),a.projectId(),a.annotationId(),a.documentRevision(),a.nodeId(),a.selectionJson(),a.selectionArtifactId(),a.selectionSha256(),b.status(),a.content(),a.actorId(),a.schemaVersion(),a.schemaManifest(),a.createdBy(),a.createdAt(),a.classification(),b.revision(),b.updatedAt(),b.replyCount()).equals(b);}
 private PermissionProtocol.GrantRow grant(EnterpriseAccess.Admission a,AnnotationAnchorProtocol.Locator loc,String action)throws Exception{
  if(!a.context().tenant_id().equals(loc.tenantId()))fail(ErrorCode.ACCESS_DENIED);return authority.require(a,new PermissionProtocol.Scope(loc.tenantId(),loc.projectId(),"DOCUMENT",loc.documentId()),action,PURPOSE);}
 private Map<String,Object> values(ApplicationSessionIdentity.Verified s,String annotationId){var v=new HashMap<String,Object>();v.put("userId",s.user().user_id());v.put("annotationId",annotationId);return v;}
 private Map<String,Object> withOperation(Map<String,Object> v,String key,String hash){v.put("key",key);v.put("requestHash",hash);v.put("manifest",schema.manifestSha256());return v;}
 private ObjectNode base(String documentId,String documentRevision,String revision){return json.object().put("schema_version","1.0.0").put("document_id",documentId).put("document_revision",documentRevision).put("revision",revision);}
 private long historyCount(Map<String,Object> v){Long n=mapper.historyCount(v);if(n==null||n<0)mismatch();return n;}
 private void clock(String expected,Map<String,Object> v){if(!expected.equals(threads.authorizationClock(v)))fail(ErrorCode.SCOPE_STALE);}
 private int maximumBytes(){storage.requireConfigured();return Math.min(AnnotationAnchorSchema.MAXIMUM_REQUEST_BYTES,storage.maximumBytes().intValueExact());}
 private static AnnotationAnchorProtocol.Command withReference(AnnotationAnchorProtocol.Command c,boolean writing,InterfaceProtocol.ObjectRef ref){return new AnnotationAnchorProtocol.Command(c.anchorId(),c.artifactId(),c.requestId(),c.transitionId(),c.authorizationSha256(),c.authorizationClock(),c.intent(),writing,ref);}
 private static void requireReference(S3ObjectWriter.WriteIntent i,InterfaceProtocol.ObjectRef r){if(r==null||!i.objectKey().equals(r.objectKey())||!i.sha256().equals(r.sha256())||!i.sizeBytes().equals(r.sizeBytes())||!i.mediaType().equals(r.mediaType())||r.objectVersion()==null||r.objectVersion().isBlank()||"null".equals(r.objectVersion()))mismatch();}
 private static void context(EnterpriseAccess.Admission a,String c){if(!c.equals(a.context().context_revision()))fail(ErrorCode.CONTEXT_CHANGED);}
 private record Captured(AnnotationAnchorProtocol.Locator locator,AnnotationThreadProtocol.Row origin,AnnotationAnchorProtocol.Pointer pointer,AnnotationAnchorProtocol.Anchor anchor,String clock){}
 record Proof(AnnotationAnchorProtocol.Locator locator,AnnotationThreadProtocol.Row origin,AnnotationAnchorProtocol.Pointer pointer,AnnotationAnchorProtocol.Anchor anchor,DocumentReadService.FixedRead original,DocumentReadService.FixedRead effective,DocumentReadService.FixedRead target,String clock){}
 private static String id(){return UUID.randomUUID().toString();}
 private static <T>T single(List<T> rows){if(rows.size()!=1)mismatch();return Objects.requireNonNull(rows.get(0));}
 private static void one(int n){if(n!=1)fail(ErrorCode.REVISION_CONFLICT);}
 private static void mismatch(){fail(ErrorCode.CONTENT_MISMATCH);}
 private static void fail(ErrorCode c){throw new TenantAccessException(c);}
 private <T>T transaction(ApplicationSessionIdentity.Verified s,Checked<T> work){return access.execute(s,a->ThrowingSupplier.of(()->work.apply(a)).get());}
 @FunctionalInterface private interface Checked<T>{T apply(EnterpriseAccess.Admission a)throws Exception;}
}
