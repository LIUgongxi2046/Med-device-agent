package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.CollectionMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Isolated orchestration doubles only: no publication, approval or business grant is manufactured. */
class TaskScopeSelectionServiceTest {
    static String id(int n) { return String.format("00000000-0000-4000-8000-%012d", n); }
    final EnterpriseAccess access = mock(EnterpriseAccess.class);
    final ResourceReadAuthority resources = mock(ResourceReadAuthority.class);
    final PermissionMapper permissions = mock(PermissionMapper.class);
    final ScopeSelectionAuthority selections = mock(ScopeSelectionAuthority.class);
    final CollectionAuthority collections = mock(CollectionAuthority.class);
    final CollectionMapper mapper = mock(CollectionMapper.class);
    final AgentExecutionPolicyAuthority policies = mock(AgentExecutionPolicyAuthority.class);
    final ObjectProvider<AgentPublicationAuthority> provider = mock(ObjectProvider.class);
    final AgentPublicationAuthority publication = mock(AgentPublicationAuthority.class);
    final ApplicationSessionIdentity.Verified subject = mock(ApplicationSessionIdentity.Verified.class, RETURNS_DEEP_STUBS);
    final PermissionSchema json = new PermissionSchema();
    final TaskScopeSelectionSchema schema = new TaskScopeSelectionSchema(json, new TaskInputSchema(json));
    final TemplateDependencyPolicy limits = new TemplateDependencyPolicy("test", "test-region", 100, 100, List.of());
    final TaskScopeSelectionService service = new TaskScopeSelectionService(access, resources, permissions, selections,
            collections, mapper, limits, json, schema, provider, policies);
    TaskScopeSelectionServiceTest() throws Exception { }
    TaskScopeSelectionSchema.Query query(int release) { return new TaskScopeSelectionSchema.Query(id(release), 20, null, null); }

    @Test void unavailablePublicationFailsClosedBeforeEnterpriseSelection() {
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class,
                () -> service.choices(subject, "7", id(1), "SOURCE", query(9))).code());
        verifyNoInteractions(access, selections, policies);
    }
    @Test void publicationPreparationCannotRunInsideTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> service.choices(subject, "7", id(1), "SOURCE", query(9))).code()); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(provider, access);
    }
    EnterpriseAccess.Admission admission;
    AgentPublicationAuthority.VerifiedPublication published;
    AgentExecutionPolicyAuthority.VerifiedPolicy execution;
    @SuppressWarnings("unchecked")
    void prepareFixture() throws Exception {
        admission = new EnterpriseAccess.Admission(subject, id(2), new BusinessSessionProtocol.Context(id(3), "7", "2", "5"),
                new TenantWatermarks(BigInteger.TWO, BigInteger.valueOf(5)));
        when(subject.user().user_id()).thenReturn(id(4));
        when(provider.getIfAvailable()).thenReturn(publication);
        var prepared = mock(AgentPublicationAuthority.PreparedPublication.class);
        when(publication.prepare(eq(subject), eq("7"), anyString(), eq("TASK_CREATION"), eq(false))).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return prepared;
        });
        when(access.execute(eq(subject), any())).thenAnswer(call -> {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { return ((Function<EnterpriseAccess.Admission,Object>) call.getArgument(1)).apply(admission); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        });
        var grant = mock(PermissionProtocol.GrantRow.class);
        when(resources.require(eq(admission), any(), anyString(), eq("TASK_CREATION"))).thenReturn(grant);
        when(permissions.projectStatus(id(1))).thenReturn(List.of("ACTIVE"));
        published = mock(AgentPublicationAuthority.VerifiedPublication.class, RETURNS_DEEP_STUBS);
        when(publication.require(eq(admission), anyString(), eq("TASK_CREATION"), same(prepared))).thenReturn(published);
        when(published.executionPolicyId()).thenReturn(id(5)); when(published.executionPolicySha256()).thenReturn("a".repeat(64));
        execution = mock(AgentExecutionPolicyAuthority.VerifiedPolicy.class);
        var definition = new AgentExecutionPolicySchema(json).materialize(new AgentExecutionPolicySchemaTest().input());
        when(execution.definition()).thenReturn(definition);
        when(policies.requireByVersion(admission, id(5), "a".repeat(64), id(1), "TASK_CREATION")).thenReturn(execution);
        var manifest = published.manifest().manifest();
        when(manifest.releaseScope()).thenReturn(new AgentReleaseProtocol.ReleaseScope(id(3), "PROJECTS", List.of(id(1)), "ENTERPRISE", List.of(), List.of(),
                new AgentReleaseProtocol.VersionRef(definition.sourceSelection().version().version_id(), definition.sourceSelection().version().content_sha256()), List.of("INTERNAL")));
        when(manifest.retrievalProfiles()).thenReturn(List.of()); when(manifest.modelConfigRevisions()).thenReturn(List.of());
        when(manifest.roleVersions()).thenReturn(List.of(new AgentReleaseProtocol.VersionRef(id(2), "a".repeat(64))));
        when(selections.requireForTaskCreation(eq(admission), eq(id(1)), any(), same(execution), eq("test"), eq("test-region"), any()))
                .thenReturn(new ScopeSelectionAuthority.Selection(List.of(), List.of(), java.util.Map.of()));
    }
    @Test void releaseBoundPageUsesVerifiedPolicyAndCannotReplayOtherReleaseWatermark() throws Exception {
        prepareFixture();
        var first = service.choices(subject, "7", id(1), "SOURCE", query(9)).required("data");
        var second = service.choices(subject, "7", id(1), "SOURCE", query(10)).required("data");
        assertEquals(id(9), first.required("agent_release_id").asText());
        assertNotEquals(first.required("filter_sha256"), second.required("filter_sha256"));
        assertNotEquals(first.required("dataset_watermark"), second.required("dataset_watermark"));
        assertEquals(ErrorCode.CURSOR_EXPIRED, assertThrows(TenantAccessException.class, () -> service.choices(subject, "7", id(1), "SOURCE",
                new TaskScopeSelectionSchema.Query(id(10), 20, null, first.required("dataset_watermark").asText()))).code());
        verify(published, times(2)).requireUnchanged();
        verify(execution, atLeast(2)).requireConsumer(admission, id(1), "TASK_CREATION");
        var order = inOrder(publication, access); order.verify(publication).prepare(subject, "7", id(9), "TASK_CREATION", false); order.verify(access).execute(eq(subject), any());
    }
    SourceReadProtocol.VerifiedSource source() {
        var source = new SourceReadProtocol.SourceRow(id(3), id(20), id(21), id(1), "PROJECT", "INTERNAL", id(22),
                "a".repeat(64), "a".repeat(64), id(1));
        var row = new SourceReadProtocol.SelectionRow(id(3), id(1), null, id(20), id(21), id(23), id(24), id(25), id(26),
                "READY", "READY", BigInteger.ONE, BigInteger.ONE, id(25), "RECEIVED", "APPROVED", "ACTIVE", BigInteger.ONE,
                "APPROVED", "a".repeat(64), "APPLICABLE", "ACTIVE");
        return new SourceReadProtocol.VerifiedSource(source, null, row, null, null, null, null);
    }
    void allowSource(String hash) {
        var d = execution.definition();
        when(execution.businessPurpose()).thenReturn("DOCUMENT_BODY");
        when(execution.definition()).thenReturn(new AgentExecutionPolicyProtocol.Definition(d.schemaVersion(), d.projectId(), d.businessPurpose(),
                d.profileEvidencePurpose(), d.allowedClassifications(), new AgentExecutionPolicyProtocol.SourceSelection(d.sourceSelection().version(),
                "FIXED_SOURCE_VERSIONS", List.of(new PermissionProtocol.Basis(id(21), hash))), d.retrievalProfiles(), d.networkPolicy(), d.worker(),
                d.bindings(), d.locale(), d.timezone(), d.environment(), d.region(), d.trainingAllowed(), d.validFrom(), d.validUntil()));
    }
    @Test void nonemptySourcePageUsesRealTypedIntersectionAndRejectsDifferentDigest() throws Exception {
        prepareFixture(); allowSource("a".repeat(64));
        var sourceMapper = mock(com.ruoyi.medical.persistence.mapper.SourceReadMapper.class);
        var sourceAuthority = mock(SourceReadAuthority.class);
        var typed = new ScopeSelectionAuthority(sourceMapper, sourceAuthority, resources, collections);
        var actualService = new TaskScopeSelectionService(access, resources, permissions, typed, collections, mapper, limits, json, schema, provider, policies);
        var verified = source();
        var unrelated = mock(SourceReadProtocol.SelectionRow.class);
        when(unrelated.tenantId()).thenReturn(id(3)); when(unrelated.projectId()).thenReturn(id(1)); when(unrelated.sourceVersion()).thenReturn(id(99));
        when(sourceMapper.scopeCandidates(eq(id(4)), eq(id(1)), eq("PROJECT_APPROVED"), anyList(), anyList(), eq(101)))
                .thenReturn(List.of(verified.selection(), unrelated));
        when(sourceMapper.source(id(21))).thenReturn(List.of(verified.source()));
        var grant = mock(PermissionProtocol.GrantRow.class);
        when(resources.require(eq(admission), any(), anyString(), eq("DOCUMENT_BODY"))).thenReturn(grant);
        when(resources.find(eq(admission), any(), eq("source.read"), anyString())).thenReturn(java.util.Optional.of(grant));
        when(sourceAuthority.requireCandidate(eq(admission), any())).thenReturn(verified);
        try (var tenant = TenantScope.open(new TenantScope.Identity(id(3), id(4), BigInteger.valueOf(7), BigInteger.TWO, BigInteger.valueOf(5)))) {
            tenant.verifyTransaction(new TenantWatermarks(BigInteger.TWO, BigInteger.valueOf(5)));
            try {
                var page = actualService.choices(subject, "7", id(1), "SOURCE", query(9)).required("data");
                assertEquals(1, page.required("items").size()); assertEquals(id(21), page.at("/items/0/version_id").asText());
                assertEquals("a".repeat(64), page.at("/items/0/original_sha256").asText());
                verify(sourceMapper, never()).source(id(99));
                verify(sourceAuthority).requireCandidate(eq(admission), argThat(r -> "DOCUMENT_BODY".equals(r.purpose())));
                verify(resources, atLeastOnce()).require(eq(admission), argThat(r -> "SOURCE".equals(r.resource_kind())), eq("source.read"), eq("TASK_CREATION"));
                allowSource("b".repeat(64));
                assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                        () -> actualService.choices(subject, "7", id(1), "SOURCE", query(9))).code());
            } finally { tenant.finishTransaction(); }
        }
    }
    @Test void collectionPageKeepsWholeFixedMembershipAndExcludesForeignMemberOrHash() throws Exception {
        prepareFixture(); allowSource("a".repeat(64));
        var version = mock(CollectionProtocol.Version.class);
        when(version.tenantId()).thenReturn(id(3)); when(version.registeredProjectId()).thenReturn(id(1));
        when(version.versionId()).thenReturn(id(30)); when(version.contentSha256()).thenReturn("c".repeat(64));
        when(mapper.selectionVersions(anyMap())).thenReturn(List.of(version));
        when(resources.find(eq(admission), any(), eq("collection.read"), eq("TASK_CREATION")))
                .thenReturn(java.util.Optional.of(mock(PermissionProtocol.GrantRow.class)));
        var proof = mock(CollectionAuthority.Proof.class, RETURNS_DEEP_STUBS);
        when(proof.version()).thenReturn(version); when(proof.head().collectionId()).thenReturn(id(31));
        when(proof.manifest().title()).thenReturn("Approved collection");
        var member = new CollectionProtocol.Member(id(20), id(21), id(23), id(22), "a".repeat(64), "d".repeat(64));
        when(proof.manifest().members()).thenReturn(List.of(member));
        when(collections.require(eq(admission), eq(id(30)), eq("DOCUMENT_BODY"), eq("test"), eq("test-region"), any())).thenReturn(proof);
        when(selections.requireForTaskCreation(eq(admission), eq(id(1)), any(), same(execution), eq("test"), eq("test-region"), any()))
                .thenReturn(new ScopeSelectionAuthority.Selection(List.of(source()), List.of(), java.util.Map.of()));
        var page = service.choices(subject, "7", id(1), "COLLECTION", query(9)).required("data");
        assertEquals(1, page.required("items").size()); assertEquals(id(30), page.at("/items/0/version_id").asText());
        assertEquals(1, page.at("/items/0/member_count").asInt()); assertEquals(id(9), page.required("agent_release_id").asText());
        clearInvocations(selections);
        when(proof.manifest().members()).thenReturn(List.of(member, new CollectionProtocol.Member(id(40), id(41), id(42), id(43), "a".repeat(64), "d".repeat(64))));
        assertTrue(service.choices(subject, "7", id(1), "COLLECTION", query(9)).at("/data/items").isEmpty());
        when(proof.manifest().members()).thenReturn(List.of(new CollectionProtocol.Member(id(20), id(21), id(23), id(22), "b".repeat(64), "d".repeat(64))));
        assertTrue(service.choices(subject, "7", id(1), "COLLECTION", query(9)).at("/data/items").isEmpty());
        verifyNoInteractions(selections);
    }

}
