package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Human annotation disposition only. Never creates confirmations, signatures, issue decisions or task transitions. */
@Service
public final class AnnotationDecisionService {
    static final int MAXIMUM_LIFECYCLE_EVENTS=10000;
    // Longest kind/status and two 20-digit revisions serialize to 165 ASCII bytes, plus array separators.
    static final int MAXIMUM_LIFECYCLE_BYTES=MAXIMUM_LIFECYCLE_EVENTS*166+1;
    private final EnterpriseAccess access;
    private final AnnotationDecisionMapper mapper;
    private final AnnotationThreadMapper threads;
    private final DocumentReadMapper reads;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;
    private final DocumentReadService documents;
    private final AnnotationThreadService selections;
    private final AnnotationDecisionSchema schema;
    private final PermissionSchema json;
    public AnnotationDecisionService(EnterpriseAccess access, AnnotationDecisionMapper mapper, AnnotationThreadMapper threads,
            DocumentReadMapper reads, PermissionMapper permissions, ResourceReadAuthority authority, DocumentReadService documents,
            AnnotationThreadService selections, AnnotationDecisionSchema schema, PermissionSchema json) {
        this.access=access; this.mapper=mapper; this.threads=threads; this.reads=reads; this.permissions=permissions;
        this.authority=authority; this.documents=documents; this.selections=selections; this.schema=schema; this.json=json;
    }
    public JsonNode list(ApplicationSessionIdentity.Verified subject, String context, String annotationId) throws Exception {
        var proof=prepare(subject,context,annotationId,"annotation.read");
        return transaction(subject, admission -> {
            var values=values(subject,proof,null,null); var parent=current(admission,context,proof,values,"annotation.read");
            long count=count(values); if(count>AnnotationDecisionSchema.MAXIMUM_DECISIONS) denied(ErrorCode.CONTEXT_LIMIT);
            values.put("fetchLimit",AnnotationDecisionSchema.MAXIMUM_DECISIONS+1); var rows=mapper.decisions(values);
            if(rows.size()!=count) mismatch(); var items=json.object().arrayNode(); var ids=new HashSet<String>();
            BigInteger previous=BigInteger.ZERO;
            for(var row:rows) { if(!ids.add(row.decisionId()) || row.revision().compareTo(previous)<=0 || row.revision().compareTo(parent.revision())>0) mismatch();
                items.add(decision(row,parent,proof.origin().documentRevision())); previous=row.revision(); }
            verifyHistory(parent,rows,values);
            var data=base(parent); data.put("location_status",parent.documentRevision().equals(parent.currentDocumentRevision())?"CURRENT_VERSION":"ORIGINAL_VERSION");
            data.put("total",Long.toString(count)).put("complete",true).set("items",items); data.put("listing_sha256",schema.hash(data));
            var response=schema.envelope("Listing",data,id(),parent.revision().toString()); clock(proof,values); return response;
        });
    }
    /** Safe lifecycle evidence for the independently versioned anchor-history endpoint. */
    public com.fasterxml.jackson.databind.node.ArrayNode lifecycle(ApplicationSessionIdentity.Verified subject,String context,String annotationId) throws Exception {
        var proof=prepare(subject,context,annotationId,"annotation.read");
        return transaction(subject,admission->{
            var values=values(subject,proof,null,null); var parent=current(admission,context,proof,values,"annotation.read");
            long count=count(values); if(count>AnnotationDecisionSchema.MAXIMUM_DECISIONS) denied(ErrorCode.CONTEXT_LIMIT);
            values.put("fetchLimit",AnnotationDecisionSchema.MAXIMUM_DECISIONS+1); var rows=mapper.decisions(values);
            if(rows.size()!=count) mismatch();
            for(var row:rows) decision(row,parent,proof.origin().documentRevision());
            var result=json.object().arrayNode();
            for(var item:verifyHistory(parent,rows,values)) result.add(json.object().put("kind",item.kind())
                .put("previous_revision",item.previousRevision().toString()).put("revision",item.revision().toString())
                .put("previous_status",item.previousStatus()).put("status",item.status()));
            clock(proof,values); return result;
        });
    }
    public JsonNode append(ApplicationSessionIdentity.Verified subject,String context,String key,String annotationId,byte[] body) throws Exception {
        json.headers(context,key); json.requireId(annotationId); var request=schema.create(body);
        String hash=schema.requestHash(annotationId,request,context,subject.sessionId()); var proof=prepare(subject,context,annotationId,"review.decide");
        // A replay may name an intermediate anchor: verify its real fixed source as well as origin/current.
        var historicalAnchor=transaction(subject,a->{
            var v=values(subject,proof,key,hash); current(a,context,proof,v,"review.decide"); var ops=mapper.operation(v);
            if(ops.size()>1) mismatch();
            if(ops.isEmpty() || !hash.equals(ops.get(0).requestSha256())) return (AnnotationThreadProtocol.Row)null;
            var receipt=schema.receipt(ops.get(0).responseRef()).required("data");
            v.put("historicalDocumentRevision",receipt.required("document_revision").asText()); v.put("historicalRevision",receipt.required("previous_revision").asText());
            return single(mapper.historicalAnnotation(v));
        });
        var historical=historicalAnchor==null ? null : documents.readFixed(subject,context,proof.parent().documentId(),historicalAnchor.documentRevision());
        if(historical!=null) selections.readSelection(subject,context,historical,historicalAnchor,"review.decide",proof.clock());
        return transaction(subject,admission -> {
            var values=values(subject,proof,key,hash); var parent=current(admission,context,proof,values,"review.decide");
            if(historical!=null) {
                documents.requireCurrent(admission,historical);
                values.put("historicalDocumentRevision",historicalAnchor.documentRevision()); values.put("historicalRevision",request.expected_revision());
                if(!historicalAnchor.equals(single(mapper.historicalAnnotation(values)))) denied(ErrorCode.SCOPE_STALE);
            }
            var operations=mapper.operation(values); if(operations.size()>1) mismatch();
            if(!operations.isEmpty()) { var response=replay(admission,parent,values,operations.get(0),request); clock(proof,values); return response; }
            if(parent.revision().compareTo(BigInteger.valueOf(MAXIMUM_LIFECYCLE_EVENTS+1L))>=0) denied(ErrorCode.CONTEXT_LIMIT);
            var header=proof.fixed().header();
            if("SIGNED".equals(header.status())) denied(ErrorCode.DOCUMENT_SIGNED);
            if(!"ACTIVE".equals(header.projectStatus())) denied(ErrorCode.ACTION_STALE);
            if(!request.document_revision().equals(parent.documentRevision()) || !parent.documentRevision().equals(parent.currentDocumentRevision())
                || !request.expected_revision().equals(parent.revision().toString())
                || !request.expected_document_revision().equals(parent.documentHeadRevision().toString())) denied(ErrorCode.REVISION_CONFLICT);
            String expectedStatus=request.decision().equals("RESOLVE")?"OPEN":"RESOLVED", status=request.decision().equals("RESOLVE")?"RESOLVED":"OPEN";
            if(!expectedStatus.equals(parent.status())) denied(ErrorCode.REVISION_CONFLICT);
            long before=count(values); if(before>=AnnotationDecisionSchema.MAXIMUM_DECISIONS) denied(ErrorCode.CONTEXT_LIMIT);
            verifyLifecycle(parent,proof.origin().documentRevision(),values);
            var witness=grant(admission,parent,"review.decide"); var now=Objects.requireNonNull(permissions.databaseTime());
            String resulting=PermissionSchema.next(request.expected_revision()), head=PermissionSchema.next(request.expected_document_revision());
            String issue=PermissionSchema.next(proof.fixed().watermarks().issueWatermark().toString());
            values.put("reviewLimit",AnnotationThreadSchema.MAXIMUM_INVALIDATIONS+1); var reviews=threads.confirmations(values);
            if(reviews.size()>AnnotationThreadSchema.MAXIMUM_INVALIDATIONS) denied(ErrorCode.CONTEXT_LIMIT);
            var reviewIds=json.object().arrayNode(); var seen=new HashSet<String>();
            for(var review:reviews) { json.requireId(review.reviewId());
                if(!seen.add(review.reviewId()) || !parent.documentRevision().equals(review.documentRevision()) || !"CONFIRM".equals(review.decision()) || !"DOCUMENT".equals(review.targetKey())) mismatch();
                reviewIds.add(review.reviewId()); }
            values.put("expected",request.expected_revision()); values.put("resulting",resulting); values.put("expectedDocumentRevision",request.expected_document_revision());
            values.put("issueWatermark",proof.fixed().watermarks().issueWatermark().toString()); values.put("evidenceWatermark",proof.fixed().watermarks().evidenceWatermark().toString());
            values.put("parentStatus",parent.status()); values.put("status",status); values.put("decision",request.decision()); values.put("reason",request.reason());
            values.put("now",now); values.put("decisionId",id()); values.put("operationId",id()); values.put("requestId",id());
            one(mapper.advanceHead(values)); one(mapper.advanceParent(values)); one(mapper.advanceIssues(values)); one(mapper.insertDecision(values));
            for(var review:reviews) { values.put("reviewId",review.reviewId()); values.put("invalidationId",id()); one(mapper.invalidateReview(values));
                var saved=single(mapper.invalidations(values));
                if(!saved.invalidationId().equals(values.get("invalidationId")) || !saved.reviewId().equals(review.reviewId())
                    || !saved.triggerVersion().equals(parent.documentRevision()) || !saved.reasonCode().equals("ANNOTATION_DECIDED")) mismatch(); }
            var after=single(mapper.parent(values));
            if(!after.equals(new AnnotationReplyProtocol.Parent(parent.tenantId(),parent.projectId(),parent.annotationId(),parent.documentId(),parent.documentRevision(),
                parent.currentDocumentRevision(),parent.bodySha256(),new BigInteger(head),new BigInteger(resulting),status,parent.classification())) || count(values)!=before+1) mismatch();
            var marks=single(reads.watermarks(values));
            if(!marks.issueWatermark().toString().equals(issue) || !marks.evidenceWatermark().equals(proof.fixed().watermarks().evidenceWatermark())) mismatch();
            var item=decision(single(mapper.decision(values)),after,proof.origin().documentRevision());
            if(!item.required("reason").asText().equals(request.reason()) || !item.required("actor_id").asText().equals(subject.user().user_id())
                || !item.required("created_at").asText().equals(time(now)) || !item.required("decision_id").asText().equals(values.get("decisionId"))
                || !item.required("decision").asText().equals(request.decision()) || !item.required("previous_revision").asText().equals(request.expected_revision())) mismatch();
            var receipt=base(after).put("operation_id",values.get("operationId").toString()).put("previous_revision",request.expected_revision())
                .put("previous_document_revision",request.expected_document_revision()).put("document_head_revision",head)
                .put("issue_watermark",issue).put("evidence_watermark",marks.evidenceWatermark().toString());
            receipt.set("invalidated_review_ids",reviewIds); receipt.set("decision",item); receipt.put("decision_sha256",schema.hash(item));
            var response=schema.envelope("Receipt",receipt,values.get("requestId").toString(),resulting); schema.receipt(json.canonical(response));
            values.put("response",json.canonical(response)); one(mapper.saveOperation(values));
            var event=event(receipt); var audit=json.object().put("schema_version","1.0.0").put("operation_id",values.get("operationId").toString())
                .put("request_sha256",hash).put("authority_grant_id",witness.grantId()).put("authority_revision",witness.revision().toString()); audit.set("after",event);
            one(permissions.saveAudit(subject.user().user_id(),parent.projectId(),id(),"annotation.decided",json.canonical(audit),values.get("requestId").toString(),schema.manifestSha256(),parent.classification()));
            one(permissions.saveEvent(subject.user().user_id(),id(),annotationId,resulting,"annotation.decided",json.canonical(event)));
            verifyOperation(values,single(mapper.operation(values)),response);
            if(!schema.read(single(mapper.audit(values))).equals(audit) || !schema.read(single(mapper.event(values))).equals(event)) mismatch();
            clock(proof,values); return response;
        });
    }
    private Proof prepare(ApplicationSessionIdentity.Verified subject,String context,String annotationId,String action) throws Exception {
        json.requireId(annotationId); PermissionSchema.revision(context);
        var initial=transaction(subject,admission -> { context(admission,context); var values=new HashMap<String,Object>(); values.put("userId",subject.user().user_id()); values.put("annotationId",annotationId);
            var parent=parent(admission,annotationId,values); grant(admission,parent,action); return parent; });
        var fixed=documents.readFixed(subject,context,initial.documentId(),initial.documentRevision());
        var proof=transaction(subject,admission -> { context(admission,context); documents.requireCurrent(admission,fixed);
            var values=new HashMap<String,Object>(); values.put("userId",subject.user().user_id()); values.put("annotationId",annotationId);
            var parent=parent(admission,annotationId,values); grant(admission,parent,action);
            if(!initial.equals(parent) || !parent.documentId().equals(fixed.header().documentId()) || !parent.projectId().equals(fixed.header().projectId())
                || !parent.documentRevision().equals(fixed.header().documentRevision()) || !parent.bodySha256().equals(fixed.header().bodySha256())
                || !parent.documentHeadRevision().equals(fixed.header().revision())) denied(ErrorCode.SCOPE_STALE);
            values.put("projectId",parent.projectId()); values.put("documentRevision",parent.documentRevision());
            return new Proof(fixed,parent,single(threads.effectiveAnnotation(values)),single(threads.originalAnnotation(values)),null,Objects.requireNonNull(mapper.authorizationClock(values))); });
        // Reads and verifies the complete actual immutable Selection object, binding, digest and anchor against the complete fixed body.
        selections.readSelection(subject,context,fixed,proof.annotation(),action,proof.clock());
        var originalFixed=proof.origin().documentRevision().equals(fixed.header().documentRevision())?fixed:
            documents.readFixed(subject,context,initial.documentId(),proof.origin().documentRevision());
        if(originalFixed!=fixed) selections.readSelection(subject,context,originalFixed,proof.origin(),action,proof.clock());
        return new Proof(fixed,proof.parent(),proof.annotation(),proof.origin(),originalFixed,proof.clock());
    }
    private AnnotationReplyProtocol.Parent current(EnterpriseAccess.Admission admission,String context,Proof proof,Map<String,Object> values,String action) throws Exception {
        context(admission,context); documents.requireCurrent(admission,proof.fixed()); documents.requireCurrent(admission,proof.originalFixed());
        var parent=parent(admission,proof.parent().annotationId(),values); grant(admission,parent,action); clock(proof,values);
        if(!parent.equals(proof.parent()) || !single(threads.effectiveAnnotation(values)).equals(proof.annotation()) || !single(threads.originalAnnotation(values)).equals(proof.origin())) denied(ErrorCode.SCOPE_STALE); return parent;
    }
    private AnnotationReplyProtocol.Parent parent(EnterpriseAccess.Admission admission,String annotationId,Map<String,Object> values) throws Exception {
        var p=single(mapper.parent(values)); if(!admission.context().tenant_id().equals(p.tenantId()) || !annotationId.equals(p.annotationId())) denied(ErrorCode.ACCESS_DENIED); return p;
    }
    private JsonNode replay(EnterpriseAccess.Admission admission,AnnotationReplyProtocol.Parent parent,Map<String,Object> values,SelectionOperationRow operation,AnnotationDecisionProtocol.Create request) throws Exception {
        if(!operation.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        var response=schema.receipt(operation.responseRef()); verifyOperation(values,operation,response); var receipt=response.required("data");
        if(!receipt.required("document_id").asText().equals(parent.documentId()) || !receipt.required("document_revision").asText().equals(request.document_revision())
            || !receipt.required("previous_revision").asText().equals(request.expected_revision()) || !receipt.required("previous_document_revision").asText().equals(request.expected_document_revision())
            || parent.revision().compareTo(new BigInteger(receipt.required("revision").asText()))<0
            || parent.documentHeadRevision().compareTo(new BigInteger(receipt.required("document_head_revision").asText()))<0) mismatch();
        values.put("decisionId",receipt.required("decision").required("decision_id").asText()); var item=decision(single(mapper.decision(values)),parent,values.get("originalDocumentRevision").toString());
        if(!item.equals(receipt.required("decision")) || !item.required("reason").asText().equals(request.reason())
            || !item.required("decision").asText().equals(request.decision()) || !item.required("actor_id").asText().equals(admission.subject().user().user_id())) mismatch();
        values.put("requestId",response.required("request_id").asText()); values.put("resulting",receipt.required("revision").asText());
        var audit=schema.read(single(mapper.audit(values))); var event=event(receipt);
        if(!audit.required("operation_id").asText().equals(operation.operationId()) || !audit.required("request_sha256").asText().equals(operation.requestSha256())
            || !audit.required("after").equals(event) || !schema.read(single(mapper.event(values))).equals(event)) mismatch();
        values.put("documentRevision",receipt.required("document_revision").asText());
        for(var review:receipt.required("invalidated_review_ids")) { values.put("reviewId",review.asText()); var row=single(mapper.invalidations(values));
            if(!row.reviewId().equals(review.asText()) || !row.triggerVersion().equals(receipt.required("document_revision").asText()) || !row.reasonCode().equals("ANNOTATION_DECIDED")) mismatch(); }
        return response;
    }
    private void verifyOperation(Map<String,Object> values,SelectionOperationRow operation,JsonNode response) throws Exception {
        if(!Objects.equals(operation.requestSha256(),values.get("requestHash")) || !Objects.equals(operation.targetId(),values.get("annotationId"))
            || !"COMPLETED".equals(operation.state()) || !Integer.valueOf(201).equals(operation.httpStatus()) || !"1.0.0".equals(operation.schemaVersion())
            || !schema.manifestSha256().equals(operation.schemaManifestSha256()) || !operation.operationId().equals(response.required("data").required("operation_id").asText())
            || !response.required("data").required("annotation_id").asText().equals(values.get("annotationId")) || !schema.receipt(operation.responseRef()).equals(response)) mismatch();
    }
    private JsonNode decision(AnnotationDecisionProtocol.Row row,AnnotationReplyProtocol.Parent parent,String originRevision) {
        if(!row.tenantId().equals(parent.tenantId()) || !row.projectId().equals(parent.projectId()) || !row.annotationId().equals(parent.annotationId())
            || !row.documentRevision().equals(originRevision) || !row.actorId().equals(row.createdBy()) || !row.classification().equals(parent.classification())
            || !"1.0.0".equals(row.schemaVersion()) || !schema.manifestSha256().equals(row.schemaManifest())) mismatch();
        AnnotationDecisionSchema.content(row.reason());
        var item=json.object().put("decision_id",row.decisionId()).put("annotation_id",row.annotationId()).put("actor_id",row.actorId()).put("created_at",time(row.createdAt()))
            .put("decision",row.decision()).put("reason",row.reason()).put("previous_status",row.previousStatus()).put("status",row.status())
            .put("previous_revision",row.previousRevision().toString()).put("revision",row.revision().toString()); schema.validate("Decision",item); schema.transition(item); return item;
    }
    /** Transaction-local integrity hook. Caller must already hold current document/annotation proofs and tenant admission. */
    List<AnnotationDecisionProtocol.History> verifyLifecycle(AnnotationReplyProtocol.Parent parent,String originalDocumentRevision,Map<String,Object> values) {
        long count=count(values); if(count>AnnotationDecisionSchema.MAXIMUM_DECISIONS) denied(ErrorCode.CONTEXT_LIMIT);
        values.put("fetchLimit",AnnotationDecisionSchema.MAXIMUM_DECISIONS+1); var rows=mapper.decisions(values);
        if(rows.size()!=count) mismatch(); var ids=new HashSet<String>();
        for(var row:rows) { if(!ids.add(row.decisionId())) mismatch(); decision(row,parent,originalDocumentRevision); }
        return verifyHistory(parent,rows,values);
    }
    /** Every annotation revision after creation has an immutable witness, including unchanged-status replies. */
    private List<AnnotationDecisionProtocol.History> verifyHistory(AnnotationReplyProtocol.Parent parent,List<AnnotationDecisionProtocol.Row> decisions,Map<String,Object> values) {
        final int maximum=MAXIMUM_LIFECYCLE_EVENTS;
        if(parent.revision().compareTo(BigInteger.valueOf(maximum+1L))>0) denied(ErrorCode.CONTEXT_LIMIT);
        values.put("historyLimit",maximum+1); var events=new ArrayList<>(mapper.history(values));
        if(events.size()>maximum) denied(ErrorCode.CONTEXT_LIMIT);
        Long replyCount=mapper.replyCount(values);
        if(replyCount==null || replyCount<0 || replyCount!=events.stream().filter(e->"REPLY".equals(e.kind())).count()) mismatch();
        for(var row:decisions) events.add(new AnnotationDecisionProtocol.History("DECISION",row.previousRevision(),row.revision(),row.previousStatus(),row.status()));
        events.sort(Comparator.comparing(AnnotationDecisionProtocol.History::revision));
        BigInteger revision=BigInteger.ONE; String status="OPEN";
        for(var event:events) {
            if(!revision.equals(event.previousRevision()) || !revision.add(BigInteger.ONE).equals(event.revision()) || !status.equals(event.previousStatus())) mismatch();
            switch(event.kind()) {
                case "INVALIDATE" -> { if(!Set.of("OPEN","RESOLVED","RETURNED","REANCHOR_REQUIRED").contains(status) || !"REANCHOR_REQUIRED".equals(event.status())) mismatch(); }
                case "REANCHOR" -> { if(!"REANCHOR_REQUIRED".equals(status) || !"OPEN".equals(event.status())) mismatch(); }
                case "REPLY" -> { if(!status.equals(event.status())) mismatch(); }
                case "DECISION" -> { if(!("OPEN".equals(status)&&"RESOLVED".equals(event.status()) || "RESOLVED".equals(status)&&"OPEN".equals(event.status()))) mismatch(); }
                default -> mismatch();
            }
            revision=event.revision(); status=event.status();
        }
        if(!revision.equals(parent.revision()) || !status.equals(parent.status())) mismatch();
        return List.copyOf(events);
    }
    private ObjectNode base(AnnotationReplyProtocol.Parent parent) { return json.object().put("schema_version","1.0.0").put("annotation_id",parent.annotationId())
        .put("document_id",parent.documentId()).put("document_revision",parent.documentRevision()).put("revision",parent.revision().toString()).put("status",parent.status()); }
    private ObjectNode event(JsonNode receipt) { var e=((ObjectNode)receipt).deepCopy(); var d=e.remove("decision");
        for(String key:List.of("decision_id","actor_id","created_at","previous_status")) e.set(key,d.required(key)); e.set("action",d.required("decision")); schema.validate("Event",e); return e; }
    private PermissionProtocol.GrantRow grant(EnterpriseAccess.Admission a,AnnotationReplyProtocol.Parent p,String action) throws Exception {
        return authority.require(a,new PermissionProtocol.Scope(p.tenantId(),p.projectId(),"DOCUMENT",p.documentId()),action,"DOCUMENT_REVIEW"); }
    private Map<String,Object> values(ApplicationSessionIdentity.Verified s,Proof p,String key,String hash) { var v=new HashMap<String,Object>();
        v.put("userId",s.user().user_id()); v.put("annotationId",p.parent().annotationId()); v.put("projectId",p.parent().projectId()); v.put("documentId",p.parent().documentId());
        v.put("originalDocumentRevision",p.origin().documentRevision()); v.put("documentRevision",p.parent().documentRevision()); v.put("classification",p.parent().classification()); v.put("manifest",schema.manifestSha256()); v.put("key",key); v.put("requestHash",hash); return v; }
    private long count(Map<String,Object> v) { Long n=mapper.decisionCount(v); if(n==null || n<0) mismatch(); return n; }
    private void clock(Proof p,Map<String,Object> v) { if(!p.clock().equals(mapper.authorizationClock(v))) denied(ErrorCode.SCOPE_STALE); }
    private static void context(EnterpriseAccess.Admission a,String c) { if(!a.context().context_revision().equals(c)) denied(ErrorCode.CONTEXT_CHANGED); }
    private record Proof(DocumentReadService.FixedRead fixed,AnnotationReplyProtocol.Parent parent,AnnotationThreadProtocol.Row annotation,AnnotationThreadProtocol.Row origin,DocumentReadService.FixedRead originalFixed,String clock) { }
    private static String id() { return UUID.randomUUID().toString(); }
    private static String time(LocalDateTime t) { return t.toInstant(ZoneOffset.UTC).toString(); }
    private static <T> T single(List<T> rows) { if(rows.size()!=1) mismatch(); return Objects.requireNonNull(rows.get(0)); }
    private static void one(int n) { if(n!=1) denied(ErrorCode.REVISION_CONFLICT); }
    private static void mismatch() { denied(ErrorCode.CONTENT_MISMATCH); }
    private static void denied(ErrorCode c) { throw new TenantAccessException(c); }
    private <T> T transaction(ApplicationSessionIdentity.Verified s,Checked<T> f) { return access.execute(s,a->ThrowingSupplier.of(()->f.apply(a)).get()); }
    @FunctionalInterface private interface Checked<T> { T apply(EnterpriseAccess.Admission a) throws Exception; }
}
