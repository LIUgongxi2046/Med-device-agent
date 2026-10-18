package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Explicit present-time legacy reconciliation. Never invents historical invalidation or moves an anchor. */
@Service
public final class AnnotationAnchorReconciliationService {
 static final String OPERATION="RV-06-ANCHOR-RECONCILE-1",ACTION="review.decide",EVENT="annotation.anchor.reconciled",REASON="ANNOTATION_ANCHOR_RECONCILED",PREFIX="LEGACY_ANCHOR_RECONCILIATION: ";
 private final EnterpriseAccess access;private final AnnotationAnchorService anchors;private final AnnotationAnchorMapper ledger;
 private final AnnotationAnchorReconciliationMapper mapper;private final AnnotationThreadMapper threads;private final DocumentReadMapper reads;
 private final PermissionMapper permissions;private final ResourceReadAuthority authority;private final DocumentReadService documents;
 private final AnnotationAnchorReconciliationSchema schema;private final PermissionSchema json;
 public AnnotationAnchorReconciliationService(EnterpriseAccess access,AnnotationAnchorService anchors,AnnotationAnchorMapper ledger,
   AnnotationAnchorReconciliationMapper mapper,AnnotationThreadMapper threads,DocumentReadMapper reads,PermissionMapper permissions,
   ResourceReadAuthority authority,DocumentReadService documents,AnnotationAnchorReconciliationSchema schema,PermissionSchema json){
  this.access=access;this.anchors=anchors;this.ledger=ledger;this.mapper=mapper;this.threads=threads;this.reads=reads;this.permissions=permissions;
  this.authority=authority;this.documents=documents;this.schema=schema;this.json=json;
 }
 public JsonNode reconcile(ApplicationSessionIdentity.Verified subject,String context,String key,String annotationId,byte[] body)throws Exception{
  json.headers(context,key);json.requireId(annotationId);var r=schema.create(body);String hash=schema.requestHash(annotationId,r,context,subject.sessionId());
  var p=anchors.prepare(subject,context,annotationId,r.documentRevision(),ACTION);
  // Include actual current body even when replay names an older target, and every historical anchor's actual source bytes.
  var currentBody=documents.readFixed(subject,context,p.locator().documentId(),p.target().header().currentDocumentRevision());
  var history=transaction(subject,a->{var v=anchors.current(a,context,p,ACTION);v.put("fetchLimit",AnnotationAnchorSchema.MAXIMUM_HISTORY+1);return List.copyOf(ledger.history(v));});
  var sources=new ArrayList<DocumentReadService.FixedRead>();sources.add(currentBody);
  for(var anchor:history){var fixed=documents.readFixed(subject,context,p.locator().documentId(),anchor.documentRevision());sources.add(fixed);anchors.readAnchor(subject,context,fixed,p.origin(),anchor,ACTION,p.clock());}
  if(history.isEmpty())mismatch();String originHash=schema.hash(anchors.anchor(history.get(0)));
  return transaction(subject,a->{var v=anchors.current(a,context,p,ACTION);for(var source:sources)documents.requireCurrent(a,source);
   v.put("fetchLimit",AnnotationAnchorSchema.MAXIMUM_HISTORY+1);if(!history.equals(ledger.history(v)))fail(ErrorCode.SCOPE_STALE);
   operationValues(v,key,hash);var ops=mapper.operation(v);if(ops.size()>1)mismatch();
   // Completed operations precede fresh eligibility: later replies, decisions, anchors or signatures never rewrite their receipt.
   if(!ops.isEmpty())return replay(a,v,p,r,ops.get(0),originHash);
   eligible(p,r,history,v);return finish(a,v,p,r,originHash);
  });
 }
 private JsonNode finish(EnterpriseAccess.Admission a,Map<String,Object> v,AnnotationAnchorService.Proof p,AnnotationAnchorReconciliationProtocol.Create r,String originHash)throws Exception{
  v.put("documentRevision",r.documentRevision());v.put("reviewLimit",AnnotationThreadSchema.MAXIMUM_INVALIDATIONS+1);var reviews=threads.confirmations(v);
  if(reviews.size()>AnnotationThreadSchema.MAXIMUM_INVALIDATIONS)fail(ErrorCode.CONTEXT_LIMIT);var ids=new ArrayList<String>();
  for(var review:reviews){json.requireId(review.reviewId());if(!r.documentRevision().equals(review.documentRevision())||!"CONFIRM".equals(review.decision())||!"DOCUMENT".equals(review.targetKey())||!ids.isEmpty()&&review.reviewId().compareTo(ids.get(ids.size()-1))<=0)mismatch();ids.add(review.reviewId());}
  var now=Objects.requireNonNull(permissions.databaseTime());String revision=PermissionSchema.next(r.expectedRevision()),head=PermissionSchema.next(r.expectedDocumentRevision());
  String issues=PermissionSchema.next(p.target().watermarks().issueWatermark().toString()),transitionId=id(),requestId=id();var witness=grant(a,p);
  v.put("now",now);v.put("operationId",id());v.put("commandJson","{}");one(mapper.startOperation(v));var op=single(mapper.operation(v));operation(a,v,op,false);
  v.put("expected",r.expectedRevision());v.put("resulting",revision);v.put("previousStatus",p.origin().status());v.put("status","REANCHOR_REQUIRED");v.put("replyCount",p.origin().replyCount());
  v.put("anchorId",p.pointer().anchorId());v.put("previousAnchorId",p.pointer().anchorId());v.put("expectedAnchorRevision",r.expectedAnchorRevision());
  v.put("kind","INVALIDATE");v.put("reason",PREFIX+r.reason());v.put("transitionId",transitionId);v.put("expectedRevision",r.expectedDocumentRevision());
  v.put("issueWatermark",p.target().watermarks().issueWatermark());v.put("evidenceWatermark",p.target().watermarks().evidenceWatermark());
  // This is a real registered immutable Selection version, unique to this origin. No synthetic version is created.
  v.put("artifactId",p.origin().selectionArtifactId());
  one(ledger.advanceAnnotation(v));one(ledger.insertTransition(v));one(threads.advanceHead(v));one(threads.advanceIssues(v));
  for(String review:ids){v.put("reviewId",review);v.put("invalidationId",id());one(mapper.invalidateReview(v));}
  var after=single(threads.originalAnnotation(v));if(!after.revision().toString().equals(revision)||!after.status().equals("REANCHOR_REQUIRED")||!after.replyCount().equals(p.origin().replyCount())||!AnnotationAnchorService.sameOrigin(p.origin(),after)||!single(ledger.pointer(v)).equals(p.pointer())||!single(ledger.anchor(v)).equals(p.anchor()))mismatch();
  var saved=single(reads.header(v));var marks=single(reads.watermarks(v));if(!AnnotationThreadService.sameAdvancedHeader(p.target().header(),saved,head)||!saved.documentRevision().equals(r.documentRevision())||!saved.currentDocumentRevision().equals(r.documentRevision())||!marks.issueWatermark().toString().equals(issues)||!marks.evidenceWatermark().equals(p.target().watermarks().evidenceWatermark()))mismatch();
  var d=json.object().put("schema_version","1.0.0").put("document_id",p.locator().documentId()).put("document_revision",r.documentRevision()).put("annotation_id",p.origin().annotationId())
   .put("operation_id",op.operationId()).put("previous_revision",r.expectedRevision()).put("revision",revision).put("previous_status",p.origin().status()).put("status","REANCHOR_REQUIRED")
   .put("previous_document_revision",r.expectedDocumentRevision()).put("document_head_revision",head).put("anchor_id",p.pointer().anchorId()).put("anchor_revision",p.pointer().revision().toString())
   .put("issue_watermark",issues).put("evidence_watermark",marks.evidenceWatermark().toString()).put("transition_id",transitionId).put("actor_id",a.subject().user().user_id())
   .put("created_at",now.toInstant(ZoneOffset.UTC).toString()).put("reason",r.reason()).put("origin_anchor_sha256",originHash);d.set("invalidated_review_ids",json.tree(ids));d.put("receipt_sha256",schema.hash(d));
  verifyTransition(v,d);verifyInvalidations(v,ids);var response=schema.envelope("Receipt",d,requestId,revision);schema.receipt(json.canonical(response));
  v.put("operationRevision",op.revision());v.put("response",json.canonical(response));one(mapper.completeOperation(v));
  var event=event(d);var audit=json.object().put("schema_version","1.0.0").put("operation_id",op.operationId()).put("request_sha256",v.get("requestHash").toString())
   .put("authority_grant_id",witness.grantId()).put("authority_revision",witness.revision().toString());audit.set("after",event);
  one(permissions.saveAudit(a.subject().user().user_id(),p.locator().projectId(),id(),EVENT,json.canonical(audit),requestId,schema.manifestSha256(),p.origin().classification()));
  one(permissions.saveEvent(a.subject().user().user_id(),id(),p.origin().annotationId(),revision,EVENT,json.canonical(event)));
  v.put("requestId",requestId);if(!schema.read(single(mapper.audit(v))).equals(audit)||!schema.read(single(mapper.event(v))).equals(event))mismatch();
  var completed=single(mapper.operation(v));operation(a,v,completed,true);if(!schema.receipt(completed.responseRef()).equals(response))mismatch();grant(a,p);clock(p,v);return response;
 }
 private JsonNode replay(EnterpriseAccess.Admission a,Map<String,Object> v,AnnotationAnchorService.Proof p,AnnotationAnchorReconciliationProtocol.Create r,UploadOperationRow op,String originHash)throws Exception{
  operation(a,v,op,true);var response=schema.receipt(op.responseRef());var d=response.required("data");
  if(!d.required("operation_id").asText().equals(op.operationId())||!d.required("annotation_id").asText().equals(p.origin().annotationId())||!d.required("document_id").asText().equals(p.locator().documentId())
   ||!d.required("document_revision").asText().equals(r.documentRevision())||!d.required("previous_revision").asText().equals(r.expectedRevision())||!d.required("previous_document_revision").asText().equals(r.expectedDocumentRevision())
   ||!d.required("anchor_id").asText().equals(r.expectedAnchorId())||!d.required("anchor_revision").asText().equals(r.expectedAnchorRevision())||!d.required("reason").asText().equals(r.reason())
   ||!Instant.parse(d.required("created_at").asText()).equals(op.createdAt().toInstant(ZoneOffset.UTC))||!d.required("origin_anchor_sha256").asText().equals(originHash)||!d.required("actor_id").asText().equals(a.subject().user().user_id())||p.origin().revision().compareTo(PermissionSchema.revision(d.required("revision").asText()))<0)mismatch();
  v.put("transitionId",d.required("transition_id").asText());v.put("resulting",d.required("revision").asText());v.put("requestId",response.required("request_id").asText());v.put("artifactId",p.origin().selectionArtifactId());
  verifyTransition(v,d);var ids=new ArrayList<String>();d.required("invalidated_review_ids").forEach(n->ids.add(n.asText()));verifyInvalidations(v,ids);
  var audit=schema.read(single(mapper.audit(v)));var event=schema.read(single(mapper.event(v)));
  if(!audit.required("operation_id").asText().equals(op.operationId())||!audit.required("request_sha256").asText().equals(v.get("requestHash"))||!audit.required("after").equals(event)||!event.equals(event(d)))mismatch();clock(p,v);return response;
 }
 private void eligible(AnnotationAnchorService.Proof p,AnnotationAnchorReconciliationProtocol.Create r,List<AnnotationAnchorProtocol.Anchor> history,Map<String,Object> v){
  var h=p.target().header();if("SIGNED".equals(h.status()))fail(ErrorCode.DOCUMENT_SIGNED);if(!"ACTIVE".equals(h.projectStatus()))fail(ErrorCode.ACTION_STALE);
  if(p.origin().revision().compareTo(BigInteger.valueOf(10001))>=0)fail(ErrorCode.CONTEXT_LIMIT);
  if(!Set.of("OPEN","RESOLVED").contains(p.origin().status())||history.size()!=1||p.anchor().previousAnchorId()!=null||!p.pointer().anchorId().equals(p.origin().annotationId())||!p.pointer().revision().equals(BigInteger.ONE)
   ||!ledger.transitions(v).isEmpty()||p.anchor().documentRevision().equals(h.currentDocumentRevision())||!r.documentRevision().equals(h.documentRevision())||!h.documentRevision().equals(h.currentDocumentRevision())
   ||!r.expectedRevision().equals(p.origin().revision().toString())||!r.expectedDocumentRevision().equals(h.revision().toString())||!r.expectedAnchorId().equals(p.pointer().anchorId())||!r.expectedAnchorRevision().equals(p.pointer().revision().toString()))fail(ErrorCode.REVISION_CONFLICT);
 }
 private void verifyTransition(Map<String,Object> v,JsonNode d)throws Exception{
  var t=schema.read(single(ledger.transition(v)));
  for(String key:List.of("transition_id","previous_revision","revision","previous_status","status","anchor_id","document_revision","actor_id"))if(!t.required(key).equals(d.required(key)))mismatch();
  if(!"INVALIDATE".equals(t.required("kind").asText())||!t.required("previous_anchor_id").equals(d.required("anchor_id"))||!t.required("reason").asText().equals(PREFIX+d.required("reason").asText())
   ||!Instant.parse(t.required("created_at").asText()).equals(Instant.parse(d.required("created_at").asText())))mismatch();
 }
 private void verifyInvalidations(Map<String,Object> v,List<String> ids){var rows=mapper.invalidations(v);if(rows.size()!=ids.size())mismatch();var seen=new HashSet<String>();for(int i=0;i<rows.size();i++){var row=rows.get(i);json.requireId(row.invalidationId());if(!seen.add(row.invalidationId())||!row.reviewId().equals(ids.get(i))||!row.triggerVersion().equals(v.get("artifactId"))||!REASON.equals(row.reasonCode()))mismatch();}}
 private ObjectNode event(JsonNode receipt)throws Exception{var event=((ObjectNode)receipt).deepCopy();var reason=event.remove("reason");event.put("reason_sha256",schema.hash(json.object().set("reason",reason)));return event;}
 private void operation(EnterpriseAccess.Admission a,Map<String,Object> v,UploadOperationRow op,boolean completed){
  if(!op.requestSha256().equals(v.get("requestHash")))fail(ErrorCode.IDEMPOTENCY_CONFLICT);
  if(!a.context().tenant_id().equals(op.tenantId())||!a.subject().user().user_id().equals(op.subjectId())||!op.subjectId().equals(op.createdBy())||!v.get("annotationId").equals(op.targetId())||!v.get("key").equals(op.idempotencyKey())||!OPERATION.equals(op.operationName())
   ||!schema.manifestSha256().equals(op.schemaManifestSha256())||!"1.0.0".equals(op.schemaVersion())||!(completed?"COMPLETED":"PROCESSING").equals(op.state())||op.errorCode()!=null||op.revision().signum()<=0
   ||op.createdAt()==null||op.updatedAt()==null||op.updatedAt().isBefore(op.createdAt())||op.dedupExpiresAt()!=null||op.dedupPolicyRevision()!=null||(completed?!Integer.valueOf(201).equals(op.httpStatus()):op.httpStatus()!=null))mismatch();
 }
 private void operationValues(Map<String,Object> v,String key,String hash){v.put("key",key);v.put("requestHash",hash);v.put("manifest",schema.manifestSha256());}
 private PermissionProtocol.GrantRow grant(EnterpriseAccess.Admission a,AnnotationAnchorService.Proof p)throws Exception{return authority.require(a,new PermissionProtocol.Scope(p.locator().tenantId(),p.locator().projectId(),"DOCUMENT",p.locator().documentId()),ACTION,"DOCUMENT_REVIEW");}
 private void clock(AnnotationAnchorService.Proof p,Map<String,Object> v){if(!p.clock().equals(threads.authorizationClock(v)))fail(ErrorCode.SCOPE_STALE);}
 private static String id(){return UUID.randomUUID().toString();}
 private static <T>T single(List<T> rows){if(rows.size()!=1)mismatch();return Objects.requireNonNull(rows.get(0));}
 private static void one(int n){if(n!=1)fail(ErrorCode.REVISION_CONFLICT);}
 private static void mismatch(){fail(ErrorCode.CONTENT_MISMATCH);}
 private static void fail(ErrorCode c){throw new TenantAccessException(c);}
 private <T>T transaction(ApplicationSessionIdentity.Verified s,Checked<T> f){return access.execute(s,a->ThrowingSupplier.of(()->f.apply(a)).get());}
 @FunctionalInterface private interface Checked<T>{T apply(EnterpriseAccess.Admission a)throws Exception;}
}
