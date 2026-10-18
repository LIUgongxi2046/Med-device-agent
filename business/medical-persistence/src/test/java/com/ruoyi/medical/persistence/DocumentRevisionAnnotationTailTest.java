package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.DocumentRevisionMapper;
import java.math.BigInteger;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.ruoyi.medical.persistence.AnnotationAnchorServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual body-save tail and lifecycle with a rollback shim, not PostgreSQL transaction acceptance. */
class DocumentRevisionAnnotationTailTest {
    AnnotationAnchorLifecycleTest.Fixture fixture;
    DocumentRevisionMapper writes;
    DocumentRevisionService service;
    boolean failHead;

    @BeforeEach void setup() throws Exception {
        fixture = new AnnotationAnchorLifecycleTest.Fixture();
        writes = mock(DocumentRevisionMapper.class);
        service = new DocumentRevisionService(null, writes, null, mock(com.ruoyi.medical.persistence.mapper.UploadTransferMapper.class), null, mock(UploadAuthorizationSchema.class), null, null,
            null, null, null, null, fixture.f.permissions, null, null, fixture.f.json, fixture.lifecycle);
        when(writes.advanceAnnotationIssues(anyMap())).thenAnswer(c -> {
            java.util.Map<String,Object> values = c.getArgument(0);
            fixture.f.store.issue = fixture.f.store.issue.add(BigInteger.valueOf((int)values.get("annotationIssues")));
            return 1;
        });
        when(writes.advanceHead(anyMap())).thenAnswer(c -> {
            if (failHead) return 0;
            fixture.f.store.head = fixture.f.store.head.add(BigInteger.ONE);
            java.util.Map<String,Object> values = c.getArgument(0);
            fixture.f.store.current = values.get("documentRevision").toString();
            return 1;
        });
        fixture.f.store.issue = BigInteger.ZERO;
    }
    @AfterEach void resetTransactionFlag() { TransactionSynchronizationManager.setActualTransactionActive(false); }

    @Test void refusesTailWithoutActiveTransactionBeforeMutatingAnything() {
        assertEquals(ErrorCode.SCOPE_STALE, assertThrows(TenantAccessException.class,
            () -> service.invalidateAnnotationsAndAdvanceHead(fixture.admission, fixture.values(NEW))).code());
        verifyNoInteractions(writes);
        verify(fixture.f.mapper, never()).advanceAnnotation(anyMap());
    }
    @Test void savedBodyAdvancesHeadAndIssueWatermarkWithImmutableInvalidation() throws Exception {
        assertEquals(1, transaction(NEW));
        assertEquals(BigInteger.TWO, fixture.f.store.head);
        assertEquals(BigInteger.ONE, fixture.f.store.issue);
        assertEquals(BigInteger.TWO, fixture.f.store.origin.revision());
        assertEquals(1, fixture.f.store.transitions.size());
        assertEquals(1, fixture.f.store.events.size());
        var order = inOrder(fixture.f.mapper, fixture.f.permissions, writes);
        order.verify(fixture.f.mapper).advanceAnnotation(anyMap());
        order.verify(fixture.f.mapper).insertTransition(anyMap());
        order.verify(fixture.f.permissions).saveEvent(anyString(), anyString(), anyString(), anyString(), eq("annotation.anchor.invalidated"), anyString());
        order.verify(writes).advanceAnnotationIssues(anyMap());
        order.verify(writes).advanceHead(anyMap());
    }
    @Test void emptyDocumentAdvancesHeadWithoutIssueWrite() throws Exception {
        doReturn(0L).when(fixture.f.mapper).documentCount(anyMap());
        doReturn(java.util.List.of()).when(fixture.f.mapper).documentAnnotations(anyMap());
        assertEquals(0, transaction(NEW));
        assertEquals(BigInteger.TWO, fixture.f.store.head);
        assertEquals(BigInteger.ZERO, fixture.f.store.issue);
        verify(writes, never()).advanceAnnotationIssues(anyMap());
        assertTrue(fixture.f.store.transitions.isEmpty());
    }
    @Test void headCasFailureRollsBackAnnotationTransitionEventAndIssueTogether() {
        failHead = true;
        assertEquals(ErrorCode.REVISION_CONFLICT, assertThrows(TenantAccessException.class, () -> transaction(NEW)).code());
        assertRolledBack();
        verify(writes).advanceAnnotationIssues(anyMap());
        verify(writes).advanceHead(anyMap());
    }
    @Test void eventFailureRollsBackAnnotationAndTransitionBeforeHeadOrIssueWrite() {
        fixture.f.failOutbox = true;
        assertThrows(IllegalStateException.class, () -> transaction(NEW));
        assertRolledBack();
        verifyNoInteractions(writes);
    }
    @Test void issueCasFailureRollsBackEventAndPreventsHeadAdvance() {
        doReturn(0).when(writes).advanceAnnotationIssues(anyMap());
        assertEquals(ErrorCode.REVISION_CONFLICT, assertThrows(TenantAccessException.class, () -> transaction(NEW)).code());
        assertRolledBack();
        verify(writes, never()).advanceHead(anyMap());
    }
    private int transaction(String revision) throws Exception {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        var before = fixture.f.store.copy();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            return service.invalidateAnnotationsAndAdvanceHead(fixture.admission, fixture.values(revision));
        } catch (Exception | Error failure) {
            fixture.f.store = before;
            throw failure;
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
    private void assertRolledBack() {
        assertEquals(BigInteger.ONE, fixture.f.store.head);
        assertEquals(OLD, fixture.f.store.current);
        assertEquals(BigInteger.ZERO, fixture.f.store.issue);
        assertEquals(BigInteger.ONE, fixture.f.store.origin.revision());
        assertEquals("OPEN", fixture.f.store.origin.status());
        assertEquals(OLD, fixture.f.store.origin.documentRevision());
        assertEquals(ANNOTATION, fixture.f.store.pointer.anchorId());
        assertTrue(fixture.f.store.events.isEmpty());
        assertTrue(fixture.f.store.transitions.isEmpty());
    }
}
