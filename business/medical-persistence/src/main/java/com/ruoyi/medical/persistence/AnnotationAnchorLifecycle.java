package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Called only inside the authorized body-save transaction. Every state change has an immutable ledger event. */
@Service
public final class AnnotationAnchorLifecycle {
 private final AnnotationAnchorMapper anchors;private final AnnotationThreadMapper threads;private final PermissionMapper permissions;private final PermissionSchema json;private final AnnotationReplyMapper parents;private final AnnotationDecisionService decisions;
 public AnnotationAnchorLifecycle(AnnotationAnchorMapper anchors,AnnotationThreadMapper threads,PermissionMapper permissions,PermissionSchema json,AnnotationReplyMapper parents,AnnotationDecisionService decisions){this.anchors=anchors;this.threads=threads;this.permissions=permissions;this.json=json;this.parents=parents;this.decisions=decisions;}
 int invalidate(EnterpriseAccess.Admission admission,Map<String,Object> input)throws Exception{
  var v=new HashMap<>(input);v.put("fetchLimit",AnnotationThreadSchema.MAXIMUM_ANNOTATIONS+1);
  Long count=anchors.documentCount(v);if(count==null||count<0)fail(ErrorCode.CONTENT_MISMATCH);
  if(count>AnnotationThreadSchema.MAXIMUM_ANNOTATIONS)fail(ErrorCode.CONTEXT_LIMIT);
  var rows=anchors.documentAnnotations(v);if(rows.size()!=count)fail(ErrorCode.CONTENT_MISMATCH);
  for(var locator:rows){
   if(!locator.tenantId().equals(admission.context().tenant_id())||!locator.documentId().equals(v.get("documentId"))||!locator.projectId().equals(v.get("projectId")))fail(ErrorCode.CONTENT_MISMATCH);
   v.put("annotationId",locator.annotationId());var original=single(threads.originalAnnotation(v));if(original.revision().compareTo(java.math.BigInteger.valueOf(10001))>=0)fail(ErrorCode.CONTEXT_LIMIT);var pointer=single(anchors.pointer(v));
   v.put("anchorId",pointer.anchorId());var anchor=single(anchors.anchor(v));
   if(!anchor.annotationId().equals(original.annotationId())||!anchor.tenantId().equals(original.tenantId())||!anchor.projectId().equals(original.projectId()))fail(ErrorCode.CONTENT_MISMATCH);
   var parent=single(parents.parent(v));
   if(!parent.tenantId().equals(locator.tenantId())||!parent.projectId().equals(locator.projectId())||!parent.documentId().equals(locator.documentId())||!parent.annotationId().equals(original.annotationId())||!parent.revision().equals(original.revision())||!parent.status().equals(original.status())||!parent.documentRevision().equals(anchor.documentRevision()))fail(ErrorCode.CONTENT_MISMATCH);
   decisions.verifyLifecycle(parent,original.documentRevision(),v);
   v.put("expected",original.revision().toString());v.put("resulting",PermissionSchema.next(original.revision().toString()));v.put("previousStatus",original.status());v.put("status","REANCHOR_REQUIRED");
   v.put("previousAnchorId",pointer.anchorId());v.put("expectedAnchorRevision",pointer.revision().toString());v.put("replyCount",original.replyCount());
   v.put("kind","INVALIDATE");v.put("reason","DOCUMENT_REVISION_SAVED");v.put("transitionId",UUID.randomUUID().toString());
   one(anchors.advanceAnnotation(v));one(anchors.insertTransition(v));
   var transition=new com.fasterxml.jackson.databind.ObjectMapper().readTree(single(anchors.transition(v)));
   if(!transition.required("kind").asText().equals("INVALIDATE")||!transition.required("revision").asText().equals(v.get("resulting"))||!transition.required("previous_revision").asText().equals(v.get("expected"))||!transition.required("document_revision").asText().equals(v.get("documentRevision"))||!transition.required("anchor_id").asText().equals(pointer.anchorId())||!transition.required("previous_anchor_id").asText().equals(pointer.anchorId())||!transition.required("previous_status").asText().equals(original.status())||!transition.required("status").asText().equals("REANCHOR_REQUIRED"))fail(ErrorCode.CONTENT_MISMATCH);
   var event=json.object().put("schema_version","1.0.0").put("annotation_id",original.annotationId()).put("document_id",locator.documentId())
     .put("document_revision",v.get("documentRevision").toString()).put("previous_revision",original.revision().toString()).put("revision",v.get("resulting").toString())
     .put("previous_status",original.status()).put("status","REANCHOR_REQUIRED").put("anchor_id",pointer.anchorId()).put("transition_id",v.get("transitionId").toString());
   one(permissions.saveEvent(admission.subject().user().user_id(),UUID.randomUUID().toString(),original.annotationId(),v.get("resulting").toString(),"annotation.anchor.invalidated",json.canonical(event)));
   var after=single(threads.originalAnnotation(v));if(!after.status().equals("REANCHOR_REQUIRED")||!after.revision().toString().equals(v.get("resulting"))||!after.replyCount().equals(original.replyCount())||!single(anchors.pointer(v)).equals(pointer))fail(ErrorCode.CONTENT_MISMATCH);
  }
  return rows.size();
 }
 private static <T>T single(List<T> rows){if(rows.size()!=1)fail(ErrorCode.CONTENT_MISMATCH);return rows.get(0);}
 private static void one(int n){if(n!=1)fail(ErrorCode.REVISION_CONFLICT);}
 private static void fail(ErrorCode c){throw new TenantAccessException(c);}
}
