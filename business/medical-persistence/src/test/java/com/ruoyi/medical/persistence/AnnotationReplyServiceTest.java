package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.AnnotationReplyMapper;
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
class AnnotationReplyServiceTest {
    static final String TENANT = id(1), ACTOR = id(2), PROJECT = id(3), DOCUMENT = id(4), DOCUMENT_REVISION = id(5), ANNOTATION = id(6);
    private static final String HASH = "a".repeat(64), KEY = "reply-test-key-000001";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 0, 0);
    private PermissionSchema json;
    private AnnotationReplySchema schema;
    private AnnotationReplyService service;
    private PermissionMapper permissions;
    private DocumentReadService documents;
    private ApplicationSessionIdentity.Verified subject;
    private BusinessSessionProtocol.Context context;
    private TenantWatermarks watermarks;
    private MemoryStore store;
    private String authorizationClock;
    private boolean canRead, canWrite, sourceDenied, failCas, failOutbox, expireAtCommit;
    private Runnable onDocumentRead;

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        json = new PermissionSchema(); schema = new AnnotationReplySchema(json); store = new MemoryStore();
        canRead = canWrite = true; authorizationClock = "NONE"; onDocumentRead = () -> { };
        context = new BusinessSessionProtocol.Context(TENANT, "1", "1", "1"); watermarks = new TenantWatermarks(BigInteger.ONE, BigInteger.ONE);
        subject = mock(ApplicationSessionIdentity.Verified.class); var binding = mock(AuthenticatedIdentityBinding.class);
        when(subject.user()).thenReturn(new BusinessSessionProtocol.User(ACTOR, "fixture", "Fixture", "USER"));
        when(subject.sessionId()).thenReturn("reply-test-session"); when(subject.binding()).thenReturn(binding);
        when(binding.view()).thenReturn(new AuthenticatedIdentityBinding.View("local", ACTOR, null, null));
        permissions = mock(PermissionMapper.class); when(permissions.databaseTime()).thenReturn(NOW);
        when(permissions.resourceOwner(eq("DOCUMENT"), eq(DOCUMENT))).thenReturn(List.of(new PermissionProtocol.OwnerRow(DOCUMENT, PROJECT)));
        when(permissions.resourceGrants(eq(ACTOR), anyString())).thenAnswer(call -> {
            String action = call.getArgument(1); return (action.equals("annotation.read") && canRead || action.equals("annotation.write") && canWrite)
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
        documents = mock(DocumentReadService.class);
        when(documents.snapshotFixed(eq(subject), anyString(), anyString(), anyString())).thenAnswer(call -> {
            if (sourceDenied) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            String revision = call.getArgument(3);
            var data = json.object().put("document_id", DOCUMENT).put("project_id", PROJECT).put("document_revision", revision)
                    .put("revision", store.parent.documentHeadRevision().toString()).put("auth_epoch", watermarks.authEpoch().toString())
                    .put("governance_epoch", watermarks.governanceEpoch().toString());
            data.set("body", json.object().put("sha256", HASH)); var response = json.object(); response.set("data", data);
            onDocumentRead.run(); return response;
        });
        service = new AnnotationReplyService(access, store, permissions, new ResourceReadAuthority(permissions, new PermissionAuthority(json), json), documents, schema, json);
    }

    @Test void savesActualReplyAndReReadsCompleteListWithExactTextAuthorTimeAndDigest() throws Exception {
        String text = " 保留原文 😀\n 需要复核\t "; var response = append("1", text, KEY); var receipt = response.required("data");
        assertEquals("2", receipt.required("revision").asText()); assertEquals("OPEN", receipt.required("status").asText());
        assertEquals(text, receipt.required("reply").required("content").asText()); assertEquals(ACTOR, receipt.required("reply").required("actor_id").asText());
        assertEquals("2026-10-18T00:00:00Z", receipt.required("reply").required("created_at").asText());
        assertEquals(1, store.rows.size()); assertEquals(1, store.operations.size()); assertEquals(1, store.audits.size()); assertEquals(1, store.events.size());
        var view = service.list(subject, "1", ANNOTATION).required("data"); assertTrue(view.required("complete").asBoolean());
        assertEquals("1", view.required("total").asText()); assertEquals("CURRENT_VERSION", view.required("location_status").asText());
        assertEquals(receipt.required("reply"), view.required("items").get(0));
        var unhashed = ((com.fasterxml.jackson.databind.node.ObjectNode) view).deepCopy(); unhashed.remove("listing_sha256");
        assertEquals(schema.hash(unhashed), view.required("listing_sha256").asText());
        for (String log : List.of(store.audits.toString(), store.events.toString())) { assertFalse(log.contains("content")); assertFalse(log.contains("需要复核")); }
        verify(documents, times(2)).snapshotFixed(subject, "1", DOCUMENT, DOCUMENT_REVISION);
    }
    @Test void replyNeverResolvesReopensOrReanchorsAnyExistingState() throws Exception {
        int revision = 1;
        for (String status : List.of("OPEN", "RESOLVED", "RETURNED", "REANCHOR_REQUIRED")) {
            store.parent = parent(status, BigInteger.valueOf(revision), DOCUMENT_REVISION, DOCUMENT_REVISION, BigInteger.ONE);
            var result = append(Integer.toString(revision), "状态保持", KEY + revision).required("data"); revision++;
            assertEquals(status, result.required("status").asText()); assertEquals(status, store.parent.status());
        }
    }
    @Test void originalIdempotentResponseSurvivesLaterRepliesWithoutNewWrites() throws Exception {
        var original = append("1", "原回复", KEY); append("2", "第二条", KEY + "2"); int writes = store.writes;
        assertEquals(original, append("1", "原回复", KEY)); assertEquals(writes, store.writes); assertEquals(2, store.rows.size());
    }
    @Test void replyAfterTwoReanchorsUsesEffectiveBodyAndOriginalReplayStaysImmutable() throws Exception {
        var original=append("1","original",KEY); String target=id(96);
        store.parent=parent("OPEN",BigInteger.valueOf(6),target,target,BigInteger.valueOf(7));
        var response=service.append(subject,"1",KEY+"current",ANNOTATION,input(target,"6","new"));
        assertEquals(target,response.required("data").required("document_revision").asText());
        int writes=store.writes; assertEquals(original,append("1","original",KEY)); assertEquals(writes,store.writes);
        verify(documents,atLeastOnce()).snapshotFixed(subject,"1",DOCUMENT,target);
        verify(documents,atLeastOnce()).snapshotFixed(subject,"1",DOCUMENT,DOCUMENT_REVISION);
        sourceDenied=true; denial(ErrorCode.ACCESS_DENIED,()->append("1","original",KEY));
    }
    @Test void lifecycleCapacityStopsNewRepliesButPreservesExactReplay() throws Exception {
        var original=append("1","original",KEY);
        store.parent=parent("OPEN",BigInteger.valueOf(10001),DOCUMENT_REVISION,DOCUMENT_REVISION,BigInteger.ONE);
        assertEquals(original,append("1","original",KEY)); int writes=store.writes;
        denial(ErrorCode.CONTEXT_LIMIT,()->append("10001","new",KEY+"next")); assertEquals(writes,store.writes);
    }
    @Test void differentContentOrBoundDocumentCannotReuseAnIdempotencyKey() throws Exception {
        append("1", "原回复", KEY);
        denial(ErrorCode.IDEMPOTENCY_CONFLICT, () -> append("1", "更改内容", KEY));
        denial(ErrorCode.IDEMPOTENCY_CONFLICT, () -> service.append(subject, "1", KEY, ANNOTATION, input(id(8), "1", "原回复")));
        assertEquals(1, store.rows.size());
    }
    @Test void wrongRevisionWrongFixedDocumentAndStaleContextNeverWrite() throws Exception {
        denial(ErrorCode.REVISION_CONFLICT, () -> append("2", "内容", KEY));
        denial(ErrorCode.REVISION_CONFLICT, () -> service.append(subject, "1", KEY, ANNOTATION, input(id(8), "1", "内容")));
        denial(ErrorCode.CONTEXT_CHANGED, () -> service.append(subject, "2", KEY, ANNOTATION, input(DOCUMENT_REVISION, "1", "内容")));
        assertTrue(store.rows.isEmpty()); assertTrue(store.operations.isEmpty());
    }
    @Test void readAndWritePermissionsAreIndependentAndReplayRechecksBothSourcesAndWriteGrant() throws Exception {
        var original = append("1", "内容", KEY); canWrite = false;
        denial(ErrorCode.ACCESS_DENIED, () -> append("1", "内容", KEY));
        assertEquals("1", service.list(subject, "1", ANNOTATION).required("data").required("total").asText());
        canWrite = true; canRead = false; denial(ErrorCode.ACCESS_DENIED, () -> service.list(subject, "1", ANNOTATION));
        assertEquals(original, append("1", "内容", KEY));
        sourceDenied = true;
        denial(ErrorCode.ACCESS_DENIED, () -> append("1", "内容", KEY));
        canRead = true; denial(ErrorCode.ACCESS_DENIED, () -> service.list(subject, "1", ANNOTATION));
        assertEquals(1, store.rows.size());
    }
    @Test void foreignTenantParentCannotBeExposedEvenIfMapperReturnsIt() {
        context = new BusinessSessionProtocol.Context(id(99), "1", "1", "1");
        denial(ErrorCode.ACCESS_DENIED, () -> service.list(subject, "1", ANNOTATION));
        verifyNoInteractions(documents);
    }
    @Test void epochsContextSessionAndDocumentMovementDuringSourceReadFailClosed() throws Exception {
        onDocumentRead = () -> watermarks = new TenantWatermarks(BigInteger.TWO, BigInteger.ONE);
        denial(ErrorCode.SCOPE_STALE, () -> append("1", "内容", KEY)); watermarks = new TenantWatermarks(BigInteger.ONE, BigInteger.ONE);
        onDocumentRead = () -> context = new BusinessSessionProtocol.Context(TENANT, "2", "1", "1");
        denial(ErrorCode.CONTEXT_CHANGED, () -> append("1", "内容", KEY)); context = new BusinessSessionProtocol.Context(TENANT, "1", "1", "1");
        onDocumentRead = () -> when(subject.sessionId()).thenReturn("another-session");
        denial(ErrorCode.SCOPE_STALE, () -> append("1", "内容", KEY)); when(subject.sessionId()).thenReturn("reply-test-session");
        onDocumentRead = () -> store.parent = parent("OPEN", BigInteger.ONE, DOCUMENT_REVISION, id(9), BigInteger.TWO);
        denial(ErrorCode.SCOPE_STALE, () -> append("1", "内容", KEY)); assertTrue(store.rows.isEmpty());
    }
    @Test void timedGrantOrSourceExpiryWithoutEpochChangeRejectsReadAndWrite() throws Exception {
        onDocumentRead = () -> authorizationClock = "2026-10-18T00:00:01.000000Z";
        denial(ErrorCode.SCOPE_STALE, () -> append("1", "内容", KEY)); assertTrue(store.rows.isEmpty());
        authorizationClock = "NONE"; denial(ErrorCode.SCOPE_STALE, () -> service.list(subject, "1", ANNOTATION));
    }
    @Test void expiryAtFinalAuditOutboxStageRollsBackThroughSameBoundary() throws Exception {
        expireAtCommit = true; denial(ErrorCode.SCOPE_STALE, () -> append("1", "内容", KEY));
        assertTrue(store.rows.isEmpty()); assertTrue(store.operations.isEmpty()); assertTrue(store.audits.isEmpty());
        assertTrue(store.events.isEmpty()); assertEquals(BigInteger.ONE, store.parent.revision());
    }
    @Test void failedCasOrOutboxCannotLeaveReplyOrPartialOperationInRollbackShim() throws Exception {
        failCas = true; denial(ErrorCode.REVISION_CONFLICT, () -> append("1", "内容", KEY));
        assertTrue(store.rows.isEmpty()); failCas = false; failOutbox = true;
        assertThrows(IllegalStateException.class, () -> append("1", "内容", KEY));
        assertTrue(store.rows.isEmpty()); assertTrue(store.operations.isEmpty()); assertTrue(store.audits.isEmpty()); assertTrue(store.events.isEmpty());
        assertEquals(BigInteger.ONE, store.parent.revision());
    }
    @Test void originalVersionRemainsExplicitAndReplyDoesNotReanchorIt() throws Exception {
        store.parent = parent("REANCHOR_REQUIRED", BigInteger.ONE, DOCUMENT_REVISION, id(9), BigInteger.TWO);
        append("1", "历史位置说明", KEY);
        var view = service.list(subject, "1", ANNOTATION).required("data");
        assertEquals("ORIGINAL_VERSION", view.required("location_status").asText()); assertEquals(DOCUMENT_REVISION, view.required("document_revision").asText());
        assertEquals("REANCHOR_REQUIRED", view.required("status").asText());
    }
    @Test void originalReceiptCanBeReadAfterExistingAnnotationWasReanchoredElsewhereInSameDocument() throws Exception {
        var original = append("1", "原回复", KEY);
        store.parent = parent("OPEN", BigInteger.valueOf(3), id(9), id(9), BigInteger.TWO);
        assertEquals(original, append("1", "原回复", KEY)); assertEquals(1, store.rows.size());
        verify(documents, times(2)).snapshotFixed(subject, "1", DOCUMENT, DOCUMENT_REVISION);
    }
    @Test void completeListRejectsCapacityOverflowCountMismatchAndForeignRowsRatherThanTruncating() throws Exception {
        store.forcedCount = 1001L; denial(ErrorCode.CONTEXT_LIMIT, () -> service.list(subject, "1", ANNOTATION));
        store.forcedCount = 1000L; denial(ErrorCode.CONTEXT_LIMIT, () -> append("1", "内容", KEY));
        store.forcedCount = 1L; denial(ErrorCode.CONTENT_MISMATCH, () -> service.list(subject, "1", ANNOTATION));
        store.forcedCount = null; var response = append("1", "内容", KEY); String replyId = response.required("data").required("reply").required("reply_id").asText();
        var row = store.rows.get(replyId); store.rows.put(replyId, new AnnotationReplyProtocol.Row(id(99), PROJECT, replyId, ANNOTATION, row.content(), ACTOR, ACTOR, NOW, "INTERNAL"));
        denial(ErrorCode.CONTENT_MISMATCH, () -> service.list(subject, "1", ANNOTATION));
    }
    @Test void completeListHasStableCreatedTimeThenUuidOrderAndEmptyIsReallyEmpty() throws Exception {
        var empty = service.list(subject, "1", ANNOTATION).required("data"); assertTrue(empty.required("items").isEmpty()); assertEquals("0", empty.required("total").asText());
        append("1", "一", KEY); append("2", "二", KEY + "2");
        var first = service.list(subject, "1", ANNOTATION).required("data"); var again = service.list(subject, "1", ANNOTATION).required("data");
        assertEquals(first, again); assertTrue(first.required("items").get(0).required("reply_id").asText().compareTo(first.required("items").get(1).required("reply_id").asText()) < 0);
    }
    @Test void corruptReplyOrMissingAuditOrEventCannotReplaySavedSuccess() throws Exception {
        append("1", "内容", KEY); var events = new HashMap<>(store.events); store.events.clear();
        denial(ErrorCode.CONTENT_MISMATCH, () -> append("1", "内容", KEY)); store.events.putAll(events);
        var audits = new HashMap<>(store.audits); store.audits.clear(); denial(ErrorCode.CONTENT_MISMATCH, () -> append("1", "内容", KEY)); store.audits.putAll(audits);
        String id = store.rows.keySet().iterator().next(); var row = store.rows.get(id);
        store.rows.put(id, new AnnotationReplyProtocol.Row(TENANT, PROJECT, id, ANNOTATION, "篡改", ACTOR, ACTOR, NOW, "INTERNAL"));
        denial(ErrorCode.CONTENT_MISMATCH, () -> append("1", "内容", KEY));
    }
    @Test void resourceGrantScopeAndPurposeAreDocumentBound() throws Exception {
        when(permissions.resourceGrants(ACTOR, "annotation.write")).thenReturn(List.of(new PermissionProtocol.GrantRow(id(33), ACTOR, PROJECT,
                "DOCUMENT", id(44), "annotation.write", grant("annotation.write").condition(), "ACTIVE", null, null, id(55), "1.0.0", json.manifestSha256(), BigInteger.ONE)));
        denial(ErrorCode.ACCESS_DENIED, () -> append("1", "内容", KEY)); assertTrue(store.rows.isEmpty());
    }

    private JsonNode append(String expected, String content, String key) throws Exception { return service.append(subject, "1", key, ANNOTATION, input(DOCUMENT_REVISION, expected, content)); }
    private byte[] input(String revision, String expected, String content) throws Exception {
        return json.canonicalObject(new AnnotationReplyProtocol.Create(revision, expected, content)).getBytes(StandardCharsets.UTF_8);
    }
    private PermissionProtocol.GrantRow grant(String action) throws Exception {
        var condition = json.canonicalObject(new PermissionProtocol.Condition("1.0.0", List.of("local"), List.of("DOCUMENT_REVIEW"),
                new PermissionProtocol.Delegation(List.of(), List.of(), List.of(), null, null)));
        return new PermissionProtocol.GrantRow(id(30), ACTOR, PROJECT, "DOCUMENT", DOCUMENT, action, condition, "ACTIVE", NOW.minusDays(1), null,
                id(31), "1.0.0", json.manifestSha256(), BigInteger.ONE);
    }
    static String id(int value) { return String.format("00000000-0000-4000-8000-%012d", value); }
    private AnnotationReplyProtocol.Parent parent(String status, BigInteger revision, String fixed, String current, BigInteger headRevision) {
        return new AnnotationReplyProtocol.Parent(TENANT, PROJECT, ANNOTATION, DOCUMENT, fixed, current, HASH, headRevision, revision, status, "INTERNAL");
    }
    private static void denial(ErrorCode code, org.junit.jupiter.api.function.Executable work) { assertEquals(code, assertThrows(TenantAccessException.class, work).code()); }

    private final class MemoryStore implements AnnotationReplyMapper {
        AnnotationReplyProtocol.Parent parent = AnnotationReplyServiceTest.this.parent("OPEN", BigInteger.ONE, DOCUMENT_REVISION, DOCUMENT_REVISION, BigInteger.ONE);
        Map<String,AnnotationReplyProtocol.Row> rows = new HashMap<>(); Map<String,SelectionOperationRow> operations = new HashMap<>();
        Map<String,String> audits = new HashMap<>(), events = new HashMap<>(); Long forcedCount; int writes;
        @Override public List<AnnotationReplyProtocol.Parent> parent(Map<String,Object> values) { return ANNOTATION.equals(values.get("annotationId")) ? List.of(parent) : List.of(); }
        @Override public String originalDocumentRevision(Map<String,Object> values) { return DOCUMENT_REVISION; }
        @Override public String authorizationClock(Map<String,Object> values) { return authorizationClock; }
        @Override public Long replyCount(Map<String,Object> values) { return forcedCount == null ? (long) rows.size() : forcedCount; }
        @Override public List<AnnotationReplyProtocol.Row> replies(Map<String,Object> values) {
            return rows.values().stream().sorted(Comparator.comparing(AnnotationReplyProtocol.Row::createdAt).thenComparing(AnnotationReplyProtocol.Row::replyId))
                    .limit((Integer) values.get("fetchLimit")).toList();
        }
        @Override public List<AnnotationReplyProtocol.Row> reply(Map<String,Object> values) { var row = rows.get(values.get("replyId")); return row == null ? List.of() : List.of(row); }
        @Override public int insertReply(Map<String,Object> values) {
            writes++; var id = (String) values.get("replyId");
            rows.put(id, new AnnotationReplyProtocol.Row(TENANT, (String) values.get("projectId"), id, (String) values.get("annotationId"),
                    (String) values.get("content"), (String) values.get("userId"), (String) values.get("userId"), (LocalDateTime) values.get("now"), (String) values.get("classification"))); return 1;
        }
        @Override public int advanceParent(Map<String,Object> values) {
            assertEquals(DOCUMENT_REVISION,values.get("originalDocumentRevision")); assertEquals(parent.documentRevision(),values.get("documentRevision"));
            if (failCas || !parent.revision().toString().equals(values.get("expected")) || !parent.status().equals(values.get("parentStatus"))) return 0;
            parent = AnnotationReplyServiceTest.this.parent(parent.status(), parent.revision().add(BigInteger.ONE), parent.documentRevision(), parent.currentDocumentRevision(), parent.documentHeadRevision()); writes++; return 1;
        }
        @Override public List<SelectionOperationRow> operation(Map<String,Object> values) { var operation = operations.get(values.get("key")); return operation == null ? List.of() : List.of(operation); }
        @Override public int saveOperation(Map<String,Object> values) {
            writes++; operations.put((String) values.get("key"), new SelectionOperationRow((String) values.get("operationId"), (String) values.get("requestHash"), "COMPLETED", 201,
                    (String) values.get("response"), (String) values.get("annotationId"), "1.0.0", (String) values.get("manifest"))); return 1;
        }
        @Override public List<String> audit(Map<String,Object> values) { var row = audits.get(values.get("requestId")); return row == null ? List.of() : List.of(row); }
        @Override public List<String> event(Map<String,Object> values) { var row = events.get(values.get("resulting")); return row == null ? List.of() : List.of(row); }
        Snapshot snapshot() { return new Snapshot(parent, new HashMap<>(rows), new HashMap<>(operations), new HashMap<>(audits), new HashMap<>(events), writes); }
        void restore(Snapshot state) { parent = state.parent(); rows = state.rows(); operations = state.operations(); audits = state.audits(); events = state.events(); writes = state.writes(); }
    }
    private record Snapshot(AnnotationReplyProtocol.Parent parent, Map<String,AnnotationReplyProtocol.Row> rows,
            Map<String,SelectionOperationRow> operations, Map<String,String> audits, Map<String,String> events, int writes) { }
}
