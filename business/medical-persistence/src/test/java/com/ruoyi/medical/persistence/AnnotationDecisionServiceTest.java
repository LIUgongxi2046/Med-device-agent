package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real schemas and resource authorities, isolated storage/source reader and rollback shim. No database or actual source read is claimed. */
class AnnotationDecisionServiceTest {
    static final String TENANT = id(1), ACTOR = id(2), PROJECT = id(3), DOCUMENT = id(4), DOCUMENT_REVISION = id(5), ANNOTATION = id(6);
    private static final String HASH = "a".repeat(64), KEY = "reply-test-key-000001";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 0, 0);
    private PermissionSchema json;
    private AnnotationDecisionSchema schema;
    private AnnotationDecisionService service;
    private PermissionMapper permissions;
    private DocumentReadService documents;
    private ApplicationSessionIdentity.Verified subject;
    private BusinessSessionProtocol.Context context;
    private TenantWatermarks watermarks;
    private MemoryStore store;
    private String authorizationClock, deniedRevision;
    private boolean canRead, canWrite, sourceDenied, failCas, failOutbox, expireAtCommit;
    private Runnable onDocumentRead;
    private AnnotationThreadMapper threads;
    private AnnotationThreadService selections;
    private boolean signed, inactive, badSelection, failHead, failInvalidation;
    private Runnable onSelection = () -> { };


    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        json = new PermissionSchema(); schema = new AnnotationDecisionSchema(json); store = new MemoryStore();
        canRead = canWrite = true; authorizationClock = "NONE"; onDocumentRead = () -> { };
        context = new BusinessSessionProtocol.Context(TENANT, "1", "1", "1"); watermarks = new TenantWatermarks(BigInteger.ONE, BigInteger.ONE);
        subject = mock(ApplicationSessionIdentity.Verified.class); var binding = mock(AuthenticatedIdentityBinding.class);
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(ACTOR, "fixture", "Fixture", "USER"));
        when(subject.sessionId()).thenReturn("reply-test-session"); when(subject.binding()).thenReturn(binding);
        when(binding.view()).thenReturn(new AuthenticatedIdentityBinding.View("local", ACTOR, null, null));
        permissions = mock(PermissionMapper.class); when(permissions.databaseTime()).thenReturn(NOW);
        when(permissions.resourceOwner(eq("DOCUMENT"), eq(DOCUMENT))).thenReturn(List.of(new PermissionProtocol.OwnerRow(DOCUMENT, PROJECT)));
        when(permissions.resourceGrants(eq(ACTOR), anyString())).thenAnswer(call -> {
            String action = call.getArgument(1); return (action.equals("annotation.read") && canRead || action.equals("review.decide") && canWrite)
                    ? List.of(grant(action)) : List.of();
        });
        when(permissions.saveAudit(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(call -> { store.audits.put(call.getArgument(5), call.getArgument(4)); return 1; });
        when(permissions.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(call -> {
                    if (failOutbox) throw new IllegalStateException("INJECTED_OUTBOX_FAILURE");
                    store.events.put(call.getArgument(3), call.getArgument(5));
                    if (expireAtCommit) authorizationClock = "2026-10-18T00:00:01.000000Z";
                    return 1;
                });
        var access = mock(EnterpriseAccess.class);
        when(access.execute(eq(subject), any())).thenAnswer(call -> {
            var backup = store.snapshot(); Function<EnterpriseAccess.Admission,Object> work = call.getArgument(1);
            var identity = new TenantScope.Identity(context.tenant_id(), subject.user().user_id(), new BigInteger(context.context_revision()), watermarks.authEpoch(), watermarks.governanceEpoch());
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try (var scope = TenantScope.open(identity)) {
                scope.verifyTransaction(watermarks);
                try { return work.apply(new EnterpriseAccess.Admission(subject, "fixture-context", context, watermarks)); }
                catch (RuntimeException failure) { store.restore(backup); throw failure; }
                finally { scope.finishTransaction(); }
            } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        });
        documents = mock(DocumentReadService.class); threads=mock(AnnotationThreadMapper.class); selections=mock(AnnotationThreadService.class);
        var reads=mock(DocumentReadMapper.class);
        when(reads.watermarks(anyMap())).thenAnswer(c->List.of(new DocumentReadProtocol.Watermarks(store.issue,BigInteger.ONE)));
        when(threads.annotation(anyMap())).thenAnswer(c->List.of(annotation()));
        when(threads.originalAnnotation(anyMap())).thenAnswer(c->List.of(annotation()));
        when(threads.effectiveAnnotation(anyMap())).thenAnswer(c->List.of(effectiveAnnotation()));
        when(threads.confirmations(anyMap())).thenAnswer(c->store.invalidations.isEmpty()?List.of(new AnnotationThreadProtocol.Review(id(90),DOCUMENT_REVISION,"CONFIRM","DOCUMENT")):List.of());
        when(documents.readFixed(eq(subject),anyString(),anyString(),anyString())).thenAnswer(call -> {
            if(sourceDenied || Objects.equals(deniedRevision,call.getArgument(3))) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            var fixed=mock(DocumentReadService.FixedRead.class); var header=mock(DocumentReadProtocol.Header.class);
            when(header.documentId()).thenReturn(DOCUMENT); when(header.projectId()).thenReturn(PROJECT); when(header.documentRevision()).thenReturn(call.getArgument(3));
            when(header.bodySha256()).thenReturn(HASH); when(header.revision()).thenReturn(store.parent.documentHeadRevision());
            when(header.status()).thenReturn(signed?"SIGNED":"DRAFT"); when(header.projectStatus()).thenReturn(inactive?"ARCHIVED":"ACTIVE");
            when(fixed.header()).thenReturn(header); when(fixed.watermarks()).thenReturn(new DocumentReadProtocol.Watermarks(store.issue,BigInteger.ONE));
            onDocumentRead.run(); return fixed;
        });
        when(selections.readSelection(eq(subject),anyString(),any(),any(),anyString(),anyString())).thenAnswer(c->{
            if(badSelection) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); onSelection.run(); return json.object(); });
        service = new AnnotationDecisionService(access,store,threads,reads,permissions,new ResourceReadAuthority(permissions,new PermissionAuthority(json),json),documents,selections,schema,json);
    }
    @Test void resolvesReopensAndPreservesHumanHistoryWithAtomicBaselines() throws Exception {
        var resolved=append("1","1","RESOLVE"," 保留理由 😀 ",KEY).required("data");
        assertEquals("RESOLVED",store.parent.status()); assertEquals("2",resolved.required("document_head_revision").asText());
        assertEquals("2",resolved.required("issue_watermark").asText()); assertEquals(1,store.invalidations.size());
        var reopened=append("2","2","REOPEN","新的证据",KEY+"2").required("data");
        assertEquals("OPEN",store.parent.status()); assertEquals("3",reopened.required("issue_watermark").asText());
        var listing=service.list(subject,"1",ANNOTATION).required("data"); assertEquals(2,listing.required("items").size());
        assertEquals(resolved.required("decision"),listing.required("items").get(0));
        assertEquals(" 保留理由 😀 ",listing.required("items").get(0).required("reason").asText());
        assertEquals(ACTOR,listing.required("items").get(0).required("actor_id").asText());
        assertEquals("2026-10-18T00:00:00Z",listing.required("items").get(0).required("created_at").asText());
        for(String log:List.of(store.audits.toString(),store.events.toString())) { assertFalse(log.contains("保留理由")); assertFalse(log.contains("reason")); }
        verify(selections,times(3)).readSelection(eq(subject),eq("1"),any(),any(),anyString(),anyString());
    }
    @Test void replayReturnsOriginalReceiptAfterReopenAndLaterReplyWithoutWriting() throws Exception {
        var original=append("1","1","RESOLVE","reason",KEY); append("2","2","REOPEN","reopen",KEY+"2");
        store.parent=parent("OPEN",BigInteger.valueOf(4),DOCUMENT_REVISION,DOCUMENT_REVISION,BigInteger.valueOf(3));
        int writes=store.writes; assertEquals(original,append("1","1","RESOLVE","reason",KEY)); assertEquals(writes,store.writes);
    }
    @Test void staleAnnotationHeadOriginalVersionAndInvalidTransitionsNeverWrite() throws Exception {
        denial(ErrorCode.REVISION_CONFLICT,()->append("2","1","RESOLVE","reason",KEY));
        denial(ErrorCode.REVISION_CONFLICT,()->append("1","2","RESOLVE","reason",KEY));
        denial(ErrorCode.REVISION_CONFLICT,()->append("1","1","REOPEN","reason",KEY));
        store.parent=parent("OPEN",BigInteger.ONE,DOCUMENT_REVISION,id(99),BigInteger.ONE);
        denial(ErrorCode.REVISION_CONFLICT,()->append("1","1","RESOLVE","reason",KEY)); assertEquals(0,store.writes);
    }
    @Test void signedInactiveAndWrongDocumentRejectFreshWrites() throws Exception {
        signed=true; denial(ErrorCode.DOCUMENT_SIGNED,()->append("1","1","RESOLVE","reason",KEY)); signed=false;
        inactive=true; denial(ErrorCode.ACTION_STALE,()->append("1","1","RESOLVE","reason",KEY)); inactive=false;
        denial(ErrorCode.REVISION_CONFLICT,()->service.append(subject,"1",KEY,ANNOTATION,input(id(99),"1","1","RESOLVE","reason"))); assertEquals(0,store.writes);
    }
    @Test void explicitDecisionAuthorityRequiredAndWrongResourcePurposeDenied() throws Exception {
        canWrite=false; denial(ErrorCode.ACCESS_DENIED,()->append("1","1","RESOLVE","reason",KEY));
        when(permissions.resourceGrants(ACTOR,"review.decide")).thenReturn(List.of(grant("annotation.write")));
        denial(ErrorCode.ACCESS_DENIED,()->append("1","1","RESOLVE","reason",KEY));
        var g=grant("review.decide"); when(permissions.resourceGrants(ACTOR,"review.decide")).thenReturn(List.of(new PermissionProtocol.GrantRow(g.grantId(),ACTOR,PROJECT,"DOCUMENT",id(99),g.action(),g.condition(),g.status(),g.validFrom(),g.validTo(),g.basisVersion(),g.schemaVersion(),g.manifest(),g.revision())));
        denial(ErrorCode.ACCESS_DENIED,()->append("1","1","RESOLVE","reason",KEY));
    }
    @Test void replyRaceAndContextRaceDuringCompleteSelectionReadFailClosed() throws Exception {
        onSelection=()->store.parent=parent("OPEN",BigInteger.TWO,DOCUMENT_REVISION,DOCUMENT_REVISION,BigInteger.ONE);
        denial(ErrorCode.SCOPE_STALE,()->append("1","1","RESOLVE","reason",KEY)); assertEquals(0,store.writes);
        onSelection=()->context=new BusinessSessionProtocol.Context(TENANT,"2","1","1");
        denial(ErrorCode.CONTEXT_CHANGED,()->append("2","1","RESOLVE","reason",KEY)); assertEquals(0,store.writes);
    }
    @Test void sourceSelectionAndExpiryDenyFreshAndReplay() throws Exception {
        sourceDenied=true; denial(ErrorCode.ACCESS_DENIED,()->append("1","1","RESOLVE","reason",KEY)); sourceDenied=false;
        badSelection=true; denial(ErrorCode.CONTENT_MISMATCH,()->append("1","1","RESOLVE","reason",KEY)); badSelection=false;
        onSelection=()->authorizationClock="crossed"; denial(ErrorCode.SCOPE_STALE,()->append("1","1","RESOLVE","reason",KEY));
        onSelection=()->{}; append("1","1","RESOLVE","reason",KEY); canWrite=false;
        denial(ErrorCode.ACCESS_DENIED,()->append("1","1","RESOLVE","reason",KEY)); canWrite=true; sourceDenied=true;
        denial(ErrorCode.ACCESS_DENIED,()->append("1","1","RESOLVE","reason",KEY));
    }
    @Test void transactionRollbackRestoresEveryRecordAfterCasOutboxInvalidationAndFinalClockFailure() throws Exception {
        failCas=true; denial(ErrorCode.REVISION_CONFLICT,()->append("1","1","RESOLVE","reason",KEY)); assertClean(); failCas=false;
        failHead=true; denial(ErrorCode.REVISION_CONFLICT,()->append("1","1","RESOLVE","reason",KEY)); assertClean(); failHead=false;
        failInvalidation=true; denial(ErrorCode.REVISION_CONFLICT,()->append("1","1","RESOLVE","reason",KEY)); assertClean(); failInvalidation=false;
        failOutbox=true; assertThrows(Exception.class,()->append("1","1","RESOLVE","reason",KEY)); assertClean(); failOutbox=false;
        expireAtCommit=true; denial(ErrorCode.SCOPE_STALE,()->append("1","1","RESOLVE","reason",KEY)); assertClean();
    }
    @Test void exactReplayRejectsDifferentReasonActionOrBoundHeadAndMissingAudit() throws Exception {
        append("1","1","RESOLVE","reason",KEY);
        denial(ErrorCode.IDEMPOTENCY_CONFLICT,()->append("1","1","RESOLVE","changed",KEY));
        denial(ErrorCode.IDEMPOTENCY_CONFLICT,()->append("1","1","REOPEN","reason",KEY));
        denial(ErrorCode.IDEMPOTENCY_CONFLICT,()->append("1","2","RESOLVE","reason",KEY));
        store.audits.clear(); denial(ErrorCode.CONTENT_MISMATCH,()->append("1","1","RESOLVE","reason",KEY));
    }
    @Test void boundedHistoryRejectsOverflowRatherThanTruncating() throws Exception {
        store.forcedCount=100L; denial(ErrorCode.CONTEXT_LIMIT,()->append("1","1","RESOLVE","reason",KEY));
        store.forcedCount=101L; denial(ErrorCode.CONTEXT_LIMIT,()->service.list(subject,"1",ANNOTATION)); assertEquals(0,store.writes);
    }
    @Test void unchangedBodyProofAndAnnotationSelectionAreRevalidatedBeforeAnyWrite() throws Exception {
        doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(documents).requireCurrent(any(EnterpriseAccess.Admission.class),any());
        denial(ErrorCode.SCOPE_STALE,()->append("1","1","RESOLVE","reason",KEY)); assertEquals(0,store.writes);
    }
    @Test void realTimedGrantExpiryAndWrongPurposeDenyWithoutSubstitutingWritePermission() throws Exception {
        var original=append("1","1","RESOLVE","reason",KEY); var g=grant("review.decide");
        var expired=new PermissionProtocol.GrantRow(g.grantId(),ACTOR,PROJECT,"DOCUMENT",DOCUMENT,g.action(),g.condition(),"ACTIVE",NOW.minusDays(1),NOW,g.basisVersion(),g.schemaVersion(),g.manifest(),g.revision());
        when(permissions.resourceGrants(ACTOR,"review.decide")).thenReturn(List.of(expired));
        denial(ErrorCode.ACCESS_DENIED,()->append("1","1","RESOLVE","reason",KEY));
        denial(ErrorCode.ACCESS_DENIED,()->append("2","2","REOPEN","reason",KEY+"2"));
        var condition=json.canonicalObject(new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of("DOCUMENT_BODY"),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),null,null)));
        when(permissions.resourceGrants(ACTOR,"review.decide")).thenReturn(List.of(new PermissionProtocol.GrantRow(g.grantId(),ACTOR,PROJECT,"DOCUMENT",DOCUMENT,g.action(),condition,"ACTIVE",g.validFrom(),g.validTo(),g.basisVersion(),g.schemaVersion(),g.manifest(),g.revision())));
        denial(ErrorCode.ACCESS_DENIED,()->append("2","2","REOPEN","reason",KEY+"2"));
        assertEquals(1,store.rows.size());
    }
    @Test void returnedAndReanchorRequiredNeverBecomeSyntheticResolvedStates() throws Exception {
        for(String status:List.of("RETURNED","REANCHOR_REQUIRED")) {
            store.parent=parent(status,BigInteger.ONE,DOCUMENT_REVISION,DOCUMENT_REVISION,BigInteger.ONE);
            denial(ErrorCode.REVISION_CONFLICT,()->append("1","1","RESOLVE","reason",KEY));
            denial(ErrorCode.REVISION_CONFLICT,()->append("1","1","REOPEN","reason",KEY));
        } assertEquals(0,store.writes);
    }
    @Test void signedHistoricalReplayKeepsOriginalReceiptAndRemainsReadOnly() throws Exception {
        var original=append("1","1","RESOLVE","reason",KEY); signed=true;
        store.parent=parent("RESOLVED",BigInteger.TWO,DOCUMENT_REVISION,id(99),BigInteger.TEN);
        int writes=store.writes; assertEquals(original,append("1","1","RESOLVE","reason",KEY)); assertEquals(writes,store.writes);
        assertEquals("ORIGINAL_VERSION",service.list(subject,"1",ANNOTATION).required("data").required("location_status").asText());
    }
    @Test void corruptedHistoryReasonStateChainAndParentStatusFailIntegrityChecks() throws Exception {
        append("1","1","RESOLVE","reason",KEY); var first=store.rows.values().iterator().next();
        store.rows.put(first.decisionId(),new AnnotationDecisionProtocol.Row(first.tenantId(),first.projectId(),first.decisionId(),first.annotationId(),first.documentRevision(),first.decision(),"tampered",first.previousStatus(),first.status(),first.previousRevision(),first.revision(),first.actorId(),first.createdBy(),first.createdAt(),first.classification(),first.schemaVersion(),first.schemaManifest()));
        denial(ErrorCode.CONTENT_MISMATCH,()->append("1","1","RESOLVE","reason",KEY)); store.rows.put(first.decisionId(),first);
        store.parent=parent("OPEN",BigInteger.TWO,DOCUMENT_REVISION,DOCUMENT_REVISION,BigInteger.TWO);
        denial(ErrorCode.CONTENT_MISMATCH,()->service.list(subject,"1",ANNOTATION));
        store.parent=parent("RESOLVED",BigInteger.TWO,DOCUMENT_REVISION,DOCUMENT_REVISION,BigInteger.TWO);
        append("2","2","REOPEN","second",KEY+"2"); var second=store.rows.values().stream().filter(x->x.revision().equals(BigInteger.valueOf(3))).findFirst().orElseThrow();
        store.rows.put(second.decisionId(),new AnnotationDecisionProtocol.Row(second.tenantId(),second.projectId(),second.decisionId(),second.annotationId(),second.documentRevision(),"RESOLVE",second.reason(),"OPEN","RESOLVED",second.previousRevision(),second.revision(),second.actorId(),second.createdBy(),second.createdAt(),second.classification(),second.schemaVersion(),second.schemaManifest()));
        denial(ErrorCode.CONTENT_MISMATCH,()->service.list(subject,"1",ANNOTATION));
    }
    @Test void reanchoredDecisionUsesEffectiveBodyButKeepsOriginForeignKeyAndOriginalReplay() throws Exception {
        var original=append("1","1","RESOLVE","original reason",KEY);
        store.lifecycle.add(new AnnotationDecisionProtocol.History("INVALIDATE",BigInteger.TWO,BigInteger.valueOf(3),"RESOLVED","REANCHOR_REQUIRED"));
        store.lifecycle.add(new AnnotationDecisionProtocol.History("REANCHOR",BigInteger.valueOf(3),BigInteger.valueOf(4),"REANCHOR_REQUIRED","OPEN"));
        String target=id(96); store.parent=parent("OPEN",BigInteger.valueOf(4),target,target,BigInteger.valueOf(4));
        var resolved=service.append(subject,"1",KEY+"new",ANNOTATION,input(target,"4","4","RESOLVE","new reason"));
        assertEquals(target,resolved.required("data").required("document_revision").asText());
        assertTrue(store.rows.values().stream().allMatch(r->r.documentRevision().equals(DOCUMENT_REVISION)));
        assertEquals(2,service.list(subject,"1",ANNOTATION).required("data").required("items").size());
        assertEquals(4,service.lifecycle(subject,"1",ANNOTATION).size());
        int writes=store.writes; assertEquals(original,append("1","1","RESOLVE","original reason",KEY)); assertEquals(writes,store.writes);
        verify(documents,atLeastOnce()).readFixed(subject,"1",DOCUMENT,DOCUMENT_REVISION);
        verify(documents,atLeastOnce()).readFixed(subject,"1",DOCUMENT,target);
        store.lifecycle.add(new AnnotationDecisionProtocol.History("INVALIDATE",BigInteger.valueOf(5),BigInteger.valueOf(6),"RESOLVED","REANCHOR_REQUIRED"));
        store.lifecycle.add(new AnnotationDecisionProtocol.History("REANCHOR",BigInteger.valueOf(6),BigInteger.valueOf(7),"REANCHOR_REQUIRED","OPEN"));
        store.parent=parent("OPEN",BigInteger.valueOf(7),id(97),id(97),BigInteger.valueOf(7));
        assertEquals(resolved,service.append(subject,"1",KEY+"new",ANNOTATION,input(target,"4","4","RESOLVE","new reason")));
        assertEquals(writes,store.writes);
        deniedRevision=target;
        denial(ErrorCode.ACCESS_DENIED,()->service.append(subject,"1",KEY+"new",ANNOTATION,input(target,"4","4","RESOLVE","new reason")));
        deniedRevision=null;
        store.lifecycle.remove(0); denial(ErrorCode.CONTENT_MISMATCH,()->service.list(subject,"1",ANNOTATION));
    }
    @Test void lifecycleRequiresReplyWitnessAndRejectsDuplicateOrMissingRevisions() throws Exception {
        append("1","1","RESOLVE","reason",KEY);
        store.parent=parent("RESOLVED",BigInteger.valueOf(3),DOCUMENT_REVISION,DOCUMENT_REVISION,BigInteger.TWO);
        denial(ErrorCode.CONTENT_MISMATCH,()->service.lifecycle(subject,"1",ANNOTATION));
        var reply=new AnnotationDecisionProtocol.History("REPLY",BigInteger.TWO,BigInteger.valueOf(3),"RESOLVED","RESOLVED");
        store.lifecycle.add(reply); assertEquals(2,service.lifecycle(subject,"1",ANNOTATION).size());
        store.lifecycle.add(reply); denial(ErrorCode.CONTENT_MISMATCH,()->service.lifecycle(subject,"1",ANNOTATION));
    }
    @Test void lifecycleCapacityStopsNewWritesButPreservesExactReplay() throws Exception {
        var original=append("1","1","RESOLVE","reason",KEY);
        store.parent=parent("RESOLVED",BigInteger.valueOf(10001),DOCUMENT_REVISION,DOCUMENT_REVISION,BigInteger.TWO);
        assertEquals(original,append("1","1","RESOLVE","reason",KEY)); int writes=store.writes;
        denial(ErrorCode.CONTEXT_LIMIT,()->append("10001","2","REOPEN","reason",KEY+"next")); assertEquals(writes,store.writes);
    }
    @Test void lifecycleAggregateBudgetCoversMaximumSerializedRows() throws Exception {
        var longest=json.object().put("kind","INVALIDATE").put("previous_revision","9".repeat(20)).put("revision","9".repeat(20))
            .put("previous_status","REANCHOR_REQUIRED").put("status","REANCHOR_REQUIRED");
        assertEquals(165,json.canonical(longest).getBytes(StandardCharsets.UTF_8).length);
        var all=json.object().arrayNode(); for(int i=0;i<AnnotationDecisionService.MAXIMUM_LIFECYCLE_EVENTS;i++) all.add(longest);
        assertEquals(AnnotationDecisionService.MAXIMUM_LIFECYCLE_BYTES,json.canonical(all).getBytes(StandardCharsets.UTF_8).length);
    }
    @Test void persistedReplyWithoutLifecycleWitnessFailsEvenWhenParentRevisionIsUnchanged() throws Exception {
        store.forcedReplyCount=1L;
        denial(ErrorCode.CONTENT_MISMATCH,()->service.lifecycle(subject,"1",ANNOTATION));
        denial(ErrorCode.CONTENT_MISMATCH,()->service.list(subject,"1",ANNOTATION));
    }
    private void assertClean() { assertEquals("OPEN",store.parent.status()); assertEquals(BigInteger.ONE,store.parent.revision()); assertEquals(BigInteger.ONE,store.parent.documentHeadRevision());
        assertEquals(BigInteger.ONE,store.issue); assertTrue(store.rows.isEmpty()); assertTrue(store.operations.isEmpty()); assertTrue(store.audits.isEmpty()); assertTrue(store.events.isEmpty()); assertTrue(store.invalidations.isEmpty()); assertEquals(0,store.writes); }
    private JsonNode append(String expected,String head,String action,String reason,String key) throws Exception { return service.append(subject,"1",key,ANNOTATION,input(DOCUMENT_REVISION,expected,head,action,reason)); }
    private byte[] input(String fixed,String expected,String head,String action,String reason) throws Exception { return json.canonicalObject(new AnnotationDecisionProtocol.Create(fixed,expected,head,action,reason)).getBytes(StandardCharsets.UTF_8); }
    private PermissionProtocol.GrantRow grant(String action) throws Exception {
        var condition=json.canonicalObject(new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of("DOCUMENT_REVIEW"),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),null,null)));
        return new PermissionProtocol.GrantRow(id(30),ACTOR,PROJECT,"DOCUMENT",DOCUMENT,action,condition,"ACTIVE",NOW.minusDays(1),null,id(31),"1.0.0",json.manifestSha256(),BigInteger.ONE);
    }
    static String id(int n) { return String.format("00000000-0000-4000-8000-%012d",n); }
    private AnnotationReplyProtocol.Parent parent(String status,BigInteger revision,String fixed,String current,BigInteger head) { return new AnnotationReplyProtocol.Parent(TENANT,PROJECT,ANNOTATION,DOCUMENT,fixed,current,HASH,head,revision,status,"INTERNAL"); }
    private AnnotationThreadProtocol.Row effectiveAnnotation() { var r=annotation(); return new AnnotationThreadProtocol.Row(r.tenantId(),r.projectId(),r.annotationId(),store.parent.documentRevision(),r.nodeId(),r.selectionJson(),r.selectionArtifactId(),r.selectionSha256(),r.status(),r.content(),r.actorId(),r.schemaVersion(),r.schemaManifest(),r.createdBy(),r.createdAt(),r.classification(),r.revision(),r.updatedAt(),r.replyCount()); }
    private AnnotationThreadProtocol.Row annotation() { return new AnnotationThreadProtocol.Row(TENANT,PROJECT,ANNOTATION,DOCUMENT_REVISION,id(70),"{}",id(71),HASH,store.parent.status(),"original",ACTOR,"1.0.0",HASH,ACTOR,NOW,"INTERNAL",store.parent.revision(),NOW,BigInteger.ZERO); }
    private static void denial(ErrorCode code,org.junit.jupiter.api.function.Executable work) { assertEquals(code,assertThrows(TenantAccessException.class,work).code()); }
    private final class MemoryStore implements AnnotationDecisionMapper {
        AnnotationReplyProtocol.Parent parent=AnnotationDecisionServiceTest.this.parent("OPEN",BigInteger.ONE,DOCUMENT_REVISION,DOCUMENT_REVISION,BigInteger.ONE);
        BigInteger issue=BigInteger.ONE; Long forcedCount; int writes;
        Map<String,AnnotationDecisionProtocol.Row> rows=new HashMap<>(); Map<String,SelectionOperationRow> operations=new HashMap<>();
        Map<String,String> audits=new HashMap<>(),events=new HashMap<>(); Map<String,AnnotationThreadProtocol.Invalidation> invalidations=new HashMap<>();
        public List<AnnotationReplyProtocol.Parent> parent(Map<String,Object> v) { return List.of(parent); }
        public String authorizationClock(Map<String,Object> v) { return authorizationClock; }
        public List<AnnotationThreadProtocol.Row> historicalAnnotation(Map<String,Object> v) { var r=annotation(); return List.of(new AnnotationThreadProtocol.Row(r.tenantId(),r.projectId(),r.annotationId(),v.get("historicalDocumentRevision").toString(),r.nodeId(),r.selectionJson(),r.selectionArtifactId(),r.selectionSha256(),r.status(),r.content(),r.actorId(),r.schemaVersion(),r.schemaManifest(),r.createdBy(),r.createdAt(),r.classification(),r.revision(),r.updatedAt(),r.replyCount())); }
        public List<AnnotationDecisionProtocol.History> history(Map<String,Object> v) { return lifecycle; }
        List<AnnotationDecisionProtocol.History> lifecycle=new ArrayList<>();
        Long forcedReplyCount;
        public Long replyCount(Map<String,Object> v) { return forcedReplyCount==null?lifecycle.stream().filter(e->"REPLY".equals(e.kind())).count():forcedReplyCount; }
        public Long decisionCount(Map<String,Object> v) { return forcedCount==null?(long)rows.size():forcedCount; }
        public List<AnnotationDecisionProtocol.Row> decisions(Map<String,Object> v) { return rows.values().stream().sorted(Comparator.comparing(AnnotationDecisionProtocol.Row::revision)).limit((Integer)v.get("fetchLimit")).toList(); }
        public List<AnnotationDecisionProtocol.Row> decision(Map<String,Object> v) { var r=rows.get(v.get("decisionId")); return r==null?List.of():List.of(r); }
        public int insertDecision(Map<String,Object> v) { assertEquals(DOCUMENT_REVISION,v.get("originalDocumentRevision")); assertEquals(parent.documentRevision(),v.get("documentRevision")); writes++; String id=(String)v.get("decisionId"); rows.put(id,new AnnotationDecisionProtocol.Row(TENANT,PROJECT,id,ANNOTATION,DOCUMENT_REVISION,(String)v.get("decision"),(String)v.get("reason"),(String)v.get("parentStatus"),(String)v.get("status"),new BigInteger((String)v.get("expected")),new BigInteger((String)v.get("resulting")),ACTOR,ACTOR,(LocalDateTime)v.get("now"),"INTERNAL","1.0.0",schema.manifestSha256())); return 1; }
        public int advanceParent(Map<String,Object> v) { if(failCas || !parent.revision().toString().equals(v.get("expected")) || !parent.status().equals(v.get("parentStatus"))) return 0;
            writes++; parent=AnnotationDecisionServiceTest.this.parent((String)v.get("status"),parent.revision().add(BigInteger.ONE),parent.documentRevision(),parent.currentDocumentRevision(),parent.documentHeadRevision()); return 1; }
        public int advanceHead(Map<String,Object> v) { if(failHead || !parent.documentHeadRevision().toString().equals(v.get("expectedDocumentRevision"))) return 0;
            writes++; parent=AnnotationDecisionServiceTest.this.parent(parent.status(),parent.revision(),parent.documentRevision(),parent.currentDocumentRevision(),parent.documentHeadRevision().add(BigInteger.ONE)); return 1; }
        public int advanceIssues(Map<String,Object> v) { if(!issue.toString().equals(v.get("issueWatermark"))) return 0; writes++; issue=issue.add(BigInteger.ONE); return 1; }
        public int invalidateReview(Map<String,Object> v) { if(failInvalidation) return 0; writes++; invalidations.put((String)v.get("reviewId"),new AnnotationThreadProtocol.Invalidation((String)v.get("invalidationId"),(String)v.get("reviewId"),DOCUMENT_REVISION,"ANNOTATION_DECIDED")); return 1; }
        public List<AnnotationThreadProtocol.Invalidation> invalidations(Map<String,Object> v) { var r=invalidations.get(v.get("reviewId")); return r==null?List.of():List.of(r); }
        public List<SelectionOperationRow> operation(Map<String,Object> v) { var r=operations.get(v.get("key")); return r==null?List.of():List.of(r); }
        public int saveOperation(Map<String,Object> v) { writes++; operations.put((String)v.get("key"),new SelectionOperationRow((String)v.get("operationId"),(String)v.get("requestHash"),"COMPLETED",201,(String)v.get("response"),ANNOTATION,"1.0.0",schema.manifestSha256())); return 1; }
        public List<String> audit(Map<String,Object> v) { var r=audits.get(v.get("requestId")); return r==null?List.of():List.of(r); }
        public List<String> event(Map<String,Object> v) { var r=events.get(v.get("resulting")); return r==null?List.of():List.of(r); }
        Snapshot snapshot() { return new Snapshot(parent,issue,new HashMap<>(rows),new HashMap<>(operations),new HashMap<>(audits),new HashMap<>(events),new HashMap<>(invalidations),writes); }
        void restore(Snapshot s) { parent=s.parent(); issue=s.issue(); rows=s.rows(); operations=s.operations(); audits=s.audits(); events=s.events(); invalidations=s.invalidations(); writes=s.writes(); }
    }
    private record Snapshot(AnnotationReplyProtocol.Parent parent,BigInteger issue,Map<String,AnnotationDecisionProtocol.Row> rows,Map<String,SelectionOperationRow> operations,Map<String,String> audits,Map<String,String> events,Map<String,AnnotationThreadProtocol.Invalidation> invalidations,int writes) { }
}
