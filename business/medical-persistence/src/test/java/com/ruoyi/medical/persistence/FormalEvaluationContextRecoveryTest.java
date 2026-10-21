package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.FormalEvaluationContextProtocol.*;
import static com.ruoyi.medical.persistence.FormalEvaluationContextSchemaTest.*;

/** Real 088 journal and recovery logic; SQL/current authorities are explicit isolated doubles. */
class FormalEvaluationContextRecoveryTest {
    final FormalEvaluationContextReadAuthority reads=mock(FormalEvaluationContextReadAuthority.class);
    final FormalEvaluationContextStoreTest f = new FormalEvaluationContextStoreTest();
    FormalEvaluationContextRecoveryTest() throws Exception { }
    byte[] authorRequest() throws Exception { return f.bytes(new Author("1.0.0", definition(), "Explicit context binding")); }
    byte[] approvalRequest(FormalEvaluationContextStore.Receipt original) throws Exception {
        return f.bytes(new Decide("1.0.0", original.binding(), "1", "APPROVE", "Independent review"));
    }
    @Test void originalAuthorReceiptSurvivesLaterReviewWithoutWritesOrFreshWriteGrant() throws Exception {
        var author = f.author(); f.approve(author); f.actor = id(101);
        clearInvocations(f.db, f.resources);
        var receipt = f.tx(() -> f.store.recover(f.a, "AUTHOR", "author-original-key", authorRequest(),reads));
        assertEquals(author, receipt); assertEquals("DRAFT", receipt.status());
        verify(f.db, never()).update(anyString(), any(Object[].class));
        verify(f.resources, never()).require(any(), any(), eq("agent.configure"), anyString());
        verify(f.resources, never()).require(any(), any(), eq("agent.publish"), anyString());
        verify(f.resources).require(eq(f.a), argThat(s -> s.project_id().equals(definition().projectId())), eq("project.read"), eq("AGENT_PUBLICATION"));
    }
    @Test void originalApprovalReceiptSurvivesDisable() throws Exception {
        var original = f.author(); var approval = f.approve(original);
        f.tx(() -> f.store.decide(f.a, "disable-original-key", f.bytes(new Decide("1.0.0", original.binding(), "2", "DISABLE", "Current stop"))));
        clearInvocations(f.db);
        assertEquals(approval, f.tx(() -> f.store.recover(f.a, "DECISION", "review-original-key", approvalRequest(original),reads)));
        verify(f.db, never()).update(anyString(), any(Object[].class));
    }
    @Test void missingOriginalNeverCreatesAnything() throws Exception {
        clearInvocations(f.db);
        var error = assertThrows(TenantAccessException.class, () -> f.tx(() -> f.store.recover(f.a, "AUTHOR", "missing-original-key", authorRequest(),reads)));
        assertEquals(ErrorCode.OBJECT_UNAVAILABLE, error.code());
        verify(f.db, never()).update(anyString(), any(Object[].class));
        assertTrue(f.rows("operation_record").isEmpty());
    }
    @Test void wrongActorCannotRecoverOriginalOperation() throws Exception {
        f.author(); f.actor = id(333);
        var error = assertThrows(TenantAccessException.class, () -> f.tx(() -> f.store.recover(f.a, "AUTHOR", "author-original-key", authorRequest(),reads)));
        assertEquals(ErrorCode.OBJECT_UNAVAILABLE, error.code());
    }
    @Test void alteredOriginalBodyIsConflict() throws Exception {
        f.author();
        var changed = f.bytes(new Author("1.0.0", definition(), "Different original intent"));
        var error = assertThrows(TenantAccessException.class, () -> f.tx(() -> f.store.recover(f.a, "AUTHOR", "author-original-key", changed,reads)));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, error.code());
    }
    @Test void alteredDecisionRevisionIsNotAReplay() throws Exception {
        var original = f.author(); f.approve(original);
        var changed = f.bytes(new Decide("1.0.0", original.binding(), "2", "APPROVE", "Independent review"));
        var error = assertThrows(TenantAccessException.class, () -> f.tx(() -> f.store.recover(f.a, "DECISION", "review-original-key", changed,reads)));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, error.code());
    }
    @Test void currentExecutionIneligibilityDoesNotBlockReadOnlyOriginalReceipt()throws Exception {
        var original=f.author();
        when(f.dependencies.require(eq(f.a),any())).thenThrow(new TenantAccessException(ErrorCode.SOURCE_NOT_READY));
        clearInvocations(f.dependencies);
        assertEquals(original,f.tx(()->f.store.recover(f.a,"AUTHOR","author-original-key",authorRequest(),reads)));
        assertEquals("DRAFT",f.tx(()->f.store.readView(f.a,original.binding().version_id(),reads)).required("status").asText());
        verifyNoInteractions(f.dependencies);verify(reads,times(2)).require(eq(f.a),eq(definition()));
    }
    @Test void currentDependencyRevocationBlocksOriginalRecovery() throws Exception {
        f.author(); doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(reads).require(eq(f.a),any());
        assertThrows(TenantAccessException.class, () -> f.tx(() -> f.store.recover(f.a, "AUTHOR", "author-original-key", authorRequest(),reads)));
    }
    @Test void currentProjectReadRevocationBlocksOriginalRecovery() throws Exception {
        f.author(); when(f.resources.require(eq(f.a), any(), eq("project.read"), anyString())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class, () -> f.tx(() -> f.store.recover(f.a, "AUTHOR", "author-original-key", authorRequest(),reads)));
    }
    @Test void missingAuditDoesNotReturnStoredReceiptAlone() throws Exception {
        f.author(); f.rows("audit_event").clear();
        assertThrows(TenantAccessException.class, () -> f.tx(() -> f.store.recover(f.a, "AUTHOR", "author-original-key", authorRequest(),reads)));
    }
    @Test void tamperedOperationReceiptFailsClosed() throws Exception {
        f.author(); f.rows("operation_record").get(0).put("response_ref", "{}");
        assertThrows(TenantAccessException.class, () -> f.tx(() -> f.store.recover(f.a, "AUTHOR", "author-original-key", authorRequest(),reads)));
    }
    @Test void readViewIncludesHashOriginalReasonAndIndependentDecisionWithoutGrants() throws Exception {
        var original = f.author(); f.approve(original);
        var view = f.tx(() -> f.store.readView(f.a, original.binding().version_id(),reads));
        new FormalEvaluationContextHttpSchema().validate("View", view);
        assertEquals(original.binding().content_sha256(), view.required("binding").required("content_sha256").asText());
        assertEquals("Explicit context binding", view.required("author_reason").asText());
        assertEquals(id(103), view.required("decisions").get(0).required("actor_id").asText());
        assertFalse(view.toString().contains("authorizations")); assertFalse(view.has("production_eligible"));
    }
    @Test void seekIsExactProjectAndBounded() throws Exception {
        String first=id(401), second=id(402);
        when(f.db.queryForList(startsWith("SELECT version_id FROM formal_evaluation_context_version"), any(Object[].class)))
                .thenReturn(List.of(Map.of("version_id", first), Map.of("version_id", second)));
        assertEquals(List.of(first, second), f.tx(() -> f.store.seek(f.a, id(1), id(0), 1)));
        verify(f.db).queryForList("SELECT version_id FROM formal_evaluation_context_version WHERE tenant_id=? AND project_id=? AND version_id>? ORDER BY version_id LIMIT ?", f.tenant, id(1), id(0), 2);
    }
    @Test void seekRejectsUnboundedLimitAndNonIncreasingDatabaseRows() throws Exception {
        assertThrows(RuntimeException.class, () -> f.tx(() -> f.store.seek(f.a, id(1), id(0), 21)));
        when(f.db.queryForList(startsWith("SELECT version_id FROM formal_evaluation_context_version"), any(Object[].class)))
                .thenReturn(List.of(Map.of("version_id", id(402)), Map.of("version_id", id(401))));
        assertThrows(TenantAccessException.class, () -> f.tx(() -> f.store.seek(f.a, id(1), id(0), 2)));
    }
}
