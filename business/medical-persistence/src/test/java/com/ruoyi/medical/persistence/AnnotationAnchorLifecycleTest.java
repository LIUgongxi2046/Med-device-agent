package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.*;
import static com.ruoyi.medical.persistence.AnnotationAnchorServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the real lifecycle and complete decision-chain verifier against offline mapper doubles. */
class AnnotationAnchorLifecycleTest {
    Fixture fixture;
    @BeforeEach void setup() throws Exception { fixture = new Fixture(); }

    @Test void repeatedBodyChangesRecordOpenToRequiredAndRequiredToRequiredWithoutMovingOrigin() throws Exception {
        var f = fixture.f;
        var origin = f.store.origin;
        var pointer = f.store.pointer;
        var anchors = Map.copyOf(f.store.anchors);
        assertEquals(1, fixture.invalidate(NEW));
        f.store.current = NEW;
        assertEquals(1, fixture.invalidate(id(60)));
        assertEquals(BigInteger.valueOf(3), f.store.origin.revision());
        assertEquals("REANCHOR_REQUIRED", f.store.origin.status());
        assertEquals(origin.documentRevision(), f.store.origin.documentRevision());
        assertEquals(origin.selectionJson(), f.store.origin.selectionJson());
        assertEquals(origin.content(), f.store.origin.content());
        assertEquals(origin.actorId(), f.store.origin.actorId());
        assertEquals(pointer, f.store.pointer);
        assertEquals(anchors, f.store.anchors);
        assertEquals(2, f.store.transitions.size());
        assertEquals("OPEN", f.store.transitions.get(0).required("previous_status").asText());
        assertEquals("REANCHOR_REQUIRED", f.store.transitions.get(1).required("previous_status").asText());
        assertEquals(id(60), f.store.transitions.get(1).required("document_revision").asText());
        assertEquals(2, f.store.events.size());
        verify(f.permissions, times(2)).saveEvent(eq(ACTOR), anyString(), eq(ANNOTATION), anyString(), eq("annotation.anchor.invalidated"), anyString());
        verify(f.mapper, never()).insertAnchor(anyMap());
        verify(f.mapper, never()).advancePointer(anyMap());
    }
    @Test void preserves058DecisionAndReplyHistoryAndDoesNotInventHistoricalActors() throws Exception {
        var f = fixture.f;
        var decision = new AnnotationDecisionProtocol.Row(TENANT, PROJECT, id(90), ANNOTATION, OLD,
            "RESOLVE", "Historic decision", "OPEN", "RESOLVED", BigInteger.ONE, BigInteger.TWO,
            id(91), id(91), NOW, "INTERNAL", "1.0.0", new AnnotationDecisionSchema(f.json).manifestSha256());
        f.store.decisions.add(decision);
        f.store.replies.add(new AnnotationDecisionProtocol.History("REPLY", BigInteger.TWO, BigInteger.valueOf(3), "RESOLVED", "RESOLVED"));
        f.store.origin = row(f.store.origin, "RESOLVED", BigInteger.valueOf(3), BigInteger.ONE);
        fixture.invalidate(NEW);
        assertEquals(List.of(decision), f.store.decisions);
        assertEquals(id(91), f.store.decisions.get(0).actorId());
        assertEquals(1, f.store.replies.size());
        assertEquals(BigInteger.ONE, f.store.origin.replyCount());
        assertEquals("RESOLVED", f.store.transitions.get(0).required("previous_status").asText());
        verify(f.permissions).saveEvent(eq(ACTOR), anyString(), eq(ANNOTATION), eq("4"), eq("annotation.anchor.invalidated"), anyString());
    }
    @Test void incompleteChainFailsBeforeAnyMutation() throws Exception {
        var f = fixture.f;
        f.store.origin = row(f.store.origin, "REANCHOR_REQUIRED", BigInteger.TWO, BigInteger.ZERO);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> fixture.invalidate(NEW)).code());
        verify(f.mapper, never()).advanceAnnotation(anyMap());
        verify(f.mapper, never()).insertTransition(anyMap());
        assertTrue(f.store.events.isEmpty());
    }
    @Test void rejectsCountMismatchAndDocumentOrLifecycleCapacityBeforeMutation() throws Exception {
        var f = fixture.f;
        when(f.mapper.documentCount(anyMap())).thenReturn(2L);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> fixture.invalidate(NEW)).code());
        when(f.mapper.documentCount(anyMap())).thenReturn((long)AnnotationThreadSchema.MAXIMUM_ANNOTATIONS + 1);
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class, () -> fixture.invalidate(NEW)).code());
        when(f.mapper.documentCount(anyMap())).thenReturn(1L);
        f.store.origin = row(f.store.origin, "REANCHOR_REQUIRED", BigInteger.valueOf(10001), BigInteger.ZERO);
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class, () -> fixture.invalidate(NEW)).code());
        verify(f.mapper, never()).advanceAnnotation(anyMap());
    }

    /** Shared fixture delegates storage behavior to the service fixture, but invokes real production lifecycle code. */
    static final class Fixture {
        final AnnotationAnchorServiceTest f = new AnnotationAnchorServiceTest();
        final AnnotationAnchorLifecycle lifecycle;
        final EnterpriseAccess.Admission admission;
        Fixture() throws Exception {
            f.setup();
            f.store.origin = row(f.store.origin, "OPEN", BigInteger.ONE, BigInteger.ZERO);
            f.store.transitions.clear();
            f.store.current = OLD;
            var parents = mock(AnnotationReplyMapper.class);
            when(parents.parent(anyMap())).thenAnswer(c -> List.of(new AnnotationReplyProtocol.Parent(
                TENANT, PROJECT, ANNOTATION, DOC, OLD, f.store.current, HASH, f.store.head,
                f.store.origin.revision(), f.store.origin.status(), "INTERNAL")));
            var decisions = mock(AnnotationDecisionMapper.class);
            when(decisions.decisionCount(anyMap())).thenAnswer(c -> (long)f.store.decisions.size());
            when(decisions.decisions(anyMap())).thenAnswer(c -> f.store.decisions);
            when(decisions.replyCount(anyMap())).thenAnswer(c -> (long)f.store.replies.size());
            when(decisions.history(anyMap())).thenAnswer(c -> {
                var history = new ArrayList<>(f.store.replies);
                for (var n : f.store.transitions) history.add(new AnnotationDecisionProtocol.History(
                    n.required("kind").asText(), new BigInteger(n.required("previous_revision").asText()),
                    new BigInteger(n.required("revision").asText()), n.required("previous_status").asText(), n.required("status").asText()));
                return history;
            });
            var verifier = new AnnotationDecisionService(mock(EnterpriseAccess.class), decisions, f.threads,
                mock(DocumentReadMapper.class), f.permissions,
                new ResourceReadAuthority(f.permissions, new PermissionAuthority(f.json), f.json), f.documents,
                f.selections, new AnnotationDecisionSchema(f.json), f.json);
            lifecycle = new AnnotationAnchorLifecycle(f.mapper, f.threads, f.permissions, f.json, parents, verifier);
            admission = new EnterpriseAccess.Admission(f.subject, id(13), f.context, new TenantWatermarks(BigInteger.ONE, BigInteger.ONE));
        }
        Map<String,Object> values(String revision) {
            var values = new HashMap<String,Object>();
            values.put("documentId", DOC); values.put("projectId", PROJECT);
            values.put("documentRevision", revision); values.put("userId", ACTOR);
            return values;
        }
        int invalidate(String revision) throws Exception { return lifecycle.invalidate(admission, values(revision)); }
    }
}
