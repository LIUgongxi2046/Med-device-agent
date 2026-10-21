package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Metadata boundary fixtures only; these do not attest any genuine publication or professional outcome. */
class TaskSourcePurposeAuthorityTest {
    static class Fixture {
        final EnterpriseAccess.Admission admission = mock(EnterpriseAccess.Admission.class);
        final AgentReleaseStore releases = mock(AgentReleaseStore.class);
        final AgentPublicationAuthority publication = mock(AgentPublicationAuthority.class);
        final AgentExecutionPolicyStore policies = mock(AgentExecutionPolicyStore.class);
        final TaskProtocol.ValidatedCreate input = mock(TaskProtocol.ValidatedCreate.class, RETURNS_DEEP_STUBS);
        final AgentReleaseStore.Stored release = mock(AgentReleaseStore.Stored.class, RETURNS_DEEP_STUBS);
        final AgentExecutionPolicyStore.Loaded loaded = mock(AgentExecutionPolicyStore.Loaded.class, RETURNS_DEEP_STUBS);
        final TaskSourcePurposeAuthority resolver;
        final PermissionProtocol.Basis reference = new PermissionProtocol.Basis("00000000-0000-4000-8000-000000000001", "a".repeat(64));
        Fixture() throws Exception {
            var beans = new StaticListableBeanFactory(); beans.addBean("publication", publication);
            resolver = new TaskSourcePurposeAuthority(releases, beans.getBeanProvider(AgentPublicationAuthority.class), policies);
            when(input.request().agent_release_id()).thenReturn("release"); when(input.request().project_id()).thenReturn("project");
            when(releases.read(admission, "release", "TASK_CREATION")).thenReturn(release);
            when(publication.taskSourcePolicyReference(admission, release, "TASK_CREATION")).thenReturn(reference);
            when(policies.read(admission, reference.version_id(), "TASK_CREATION")).thenReturn(loaded);
            when(loaded.fixed().definitionSha256()).thenReturn(reference.content_sha256());
            when(loaded.state().status()).thenReturn("APPROVED");
            var definition = loaded.fixed().definition();
            when(definition.projectId()).thenReturn("project"); when(definition.businessPurpose()).thenReturn("DOCUMENT_BODY");
            when(definition.allowedClassifications()).thenReturn(List.of("INTERNAL"));
            when(definition.retrievalProfiles()).thenReturn(List.of()); when(definition.bindings()).thenReturn(List.of());
            when(definition.sourceSelection().version()).thenReturn(reference);
            var manifest = release.content().manifest();
            when(manifest.retrievalProfiles()).thenReturn(List.of()); when(manifest.roleVersions()).thenReturn(List.of());
            when(manifest.modelConfigRevisions()).thenReturn(List.of());
            when(manifest.releaseScope().sourceScopePolicy()).thenReturn(new AgentReleaseProtocol.VersionRef(reference.version_id(), reference.content_sha256()));
            when(manifest.releaseScope().projectMode()).thenReturn("PROJECTS");
            when(manifest.releaseScope().projectIds()).thenReturn(List.of("project"));
            when(manifest.releaseScope().allowedClassifications()).thenReturn(List.of("INTERNAL"));
        }
        String read() throws Exception { return resolver.requireHistorical(admission, input, "TASK_CREATION"); }
    }
    @Test void exactApprovedMetadataResolvesWithoutRequiringCurrentExecutionProof() throws Exception {
        var f = new Fixture(); assertEquals("DOCUMENT_BODY", f.read());
        verify(f.publication).taskSourcePolicyReference(f.admission, f.release, "TASK_CREATION");
        verifyNoMoreInteractions(f.publication);
        verify(f.releases).requireUnchanged(f.release);
        verify(f.policies, never()).approval(any(EnterpriseAccess.Admission.class), any());
    }
    @Test void disabledPolicyStillIdentifiesHistoricalDataUse() throws Exception {
        var f = new Fixture(); when(f.loaded.state().status()).thenReturn("DISABLED");
        assertEquals("DOCUMENT_BODY", f.read());
    }
    @Test void wrongPolicyHashCannotSubstituteDataUse() throws Exception {
        var f = new Fixture(); when(f.loaded.fixed().definitionSha256()).thenReturn("b".repeat(64));
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, f::read).code());
    }
    @Test void otherProjectAndNeverApprovedPolicyReject() throws Exception {
        var f = new Fixture(); when(f.loaded.fixed().definition().projectId()).thenReturn("other-project");
        assertThrows(TenantAccessException.class, f::read);
        when(f.loaded.fixed().definition().projectId()).thenReturn("project"); when(f.loaded.state().status()).thenReturn("DRAFT");
        assertThrows(TenantAccessException.class, f::read);
    }
    @Test void wrongBusinessPurposeCannotBeInferredFromSourceRights() throws Exception {
        var f = new Fixture(); when(f.loaded.fixed().definition().businessPurpose()).thenReturn("TASK_CREATION");
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, f::read).code());
    }
    @Test void unrelatedEntryPurposeRejectsBeforeMetadataAccess() throws Exception {
        var f = new Fixture();
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> f.resolver.requireHistorical(f.admission, f.input, "UNRELATED")).code());
        verifyNoInteractions(f.publication, f.policies, f.releases);
    }
    @Test void changedReleaseReadFailsInsteadOfChangingPurpose() throws Exception {
        var f = new Fixture(); doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(f.releases).requireUnchanged(f.release);
        assertEquals(ErrorCode.SCOPE_STALE, assertThrows(TenantAccessException.class, f::read).code());
    }
}
