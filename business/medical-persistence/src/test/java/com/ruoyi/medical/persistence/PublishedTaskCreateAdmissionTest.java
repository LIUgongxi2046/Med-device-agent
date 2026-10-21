package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Synthetic boundary tests, not evidence that any real release was approved. */
class PublishedTaskCreateAdmissionTest {
    private static PermissionProtocol.Basis ref(String hash) {
        return new PermissionProtocol.Basis("00000000-0000-4000-8000-000000000001", hash.repeat(64));
    }
    private static AgentExecutionPolicyProtocol.Definition policy(String mode, boolean network,
            List<PermissionProtocol.Basis> sources) {
        var policy = mock(AgentExecutionPolicyProtocol.Definition.class);
        when(policy.sourceSelection()).thenReturn(new AgentExecutionPolicyProtocol.SourceSelection(ref("c"), mode, sources));
        when(policy.networkPolicy()).thenReturn(new AgentExecutionPolicyProtocol.NetworkPolicy(ref("d"), List.of(), network));
        return policy;
    }
    private static PublishedTaskCreateAdmission unavailable() {
        return new PublishedTaskCreateAdmission(new StaticListableBeanFactory().getBeanProvider(AgentPublicationAuthority.class),
                null, null, null, null, null, null);
    }
    @Test void exactSelectedSubsetIsAllowedButDoesNotAuthorizeAdditionalSources() {
        assertDoesNotThrow(() -> PublishedTaskCreateAdmission.requireSelection(policy("FIXED_SOURCE_VERSIONS", false,
                List.of(ref("a"))), List.of(ref("a"))));
        assertDoesNotThrow(() -> PublishedTaskCreateAdmission.requireSelection(policy("FIXED_SOURCE_VERSIONS", false,
                List.of(ref("a"))), List.of()));
    }
    @Test void changedSourceDigestCannotReuseApprovedVersionIdentity() {
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> PublishedTaskCreateAdmission.requireSelection(policy("FIXED_SOURCE_VERSIONS", false,
                        List.of(ref("a"))), List.of(ref("b")))).code());
    }
    @Test void unapprovedSourceAndDuplicateSourceReject() {
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> PublishedTaskCreateAdmission.requireSelection(policy("FIXED_SOURCE_VERSIONS", false,
                        List.of()), List.of(ref("a")))).code());
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
                () -> PublishedTaskCreateAdmission.requireSelection(policy("FIXED_SOURCE_VERSIONS", false,
                        List.of(ref("a"))), List.of(ref("a"), ref("a")))).code());
    }
    @Test void networkMetadataIsNotActualQueryApproval() {
        assertEquals(ErrorCode.NETWORK_SCOPE_DENIED, assertThrows(TenantAccessException.class,
                () -> PublishedTaskCreateAdmission.requireSelection(policy("FIXED_SOURCE_VERSIONS", true,
                        List.of()), List.of())).code());
    }
    @Test void unsupportedSelectionModeDoesNotDefaultToAllSources() {
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class,
                () -> PublishedTaskCreateAdmission.requireSelection(policy("ALL", false,
                        List.of()), List.of())).code());
    }
    @Test void absentPublicationProviderAndUnpreparedCallsRemainClosed() {
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class,
                () -> unavailable().prepare(null, "1", null)).code());
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class,
                () -> unavailable().require(null, null, null, null, null)).code());
    }
    @Test void objectPreparationInsideWriteTransactionIsForbidden() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                    () -> unavailable().prepare(null, "1", null)).code());
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
    @Test void legacyImplementationsDoNotAcquireAnImplicitPreparationPass() {
        TaskCreateReleaseAdmission legacy = (a, i, t, s, r) -> { throw new AssertionError("Must not run"); };
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class,
                () -> legacy.prepare(null, "1", null)).code());
    }
    @Test void preparedEmptySelectionConsumesPublicationAndRechecksWithoutObjectIoInTransaction() throws Exception {
        var authority = mock(AgentPublicationAuthority.class);
        var beans = new StaticListableBeanFactory(); beans.addBean("publication", authority);
        var policies = mock(AgentExecutionPolicyAuthority.class);
        var scopes = mock(AgentReleaseScopeAuthority.class);
        var selections = mock(ScopeSelectionAuthority.class);
        var limits = mock(TemplateDependencyPolicy.class);
        when(limits.environment()).thenReturn("test"); when(limits.region()).thenReturn("test-region");
        when(limits.maximumVersions()).thenReturn(100);
        var json = new PermissionSchema();
        var scenarios = mock(ScenarioSpecificationService.class);
        var scenario = mock(ScenarioSpecificationService.Proof.class);
        when(scenario.dependencyVersions()).thenReturn(List.of());
        when(scenario.authorizations()).thenReturn(json.object());
        when(scenarios.requireTask(any(), any(), any(), any())).thenReturn(scenario);
        var consumer = new PublishedTaskCreateAdmission(beans.getBeanProvider(AgentPublicationAuthority.class),
                policies, scopes, selections, limits, json, scenarios);
        var input = mock(TaskProtocol.ValidatedCreate.class, RETURNS_DEEP_STUBS);
        when(input.request().agent_release_id()).thenReturn("release");
        when(input.request().project_id()).thenReturn("project");
        when(input.request().template_version()).thenReturn("template");
        var prepared = mock(AgentPublicationAuthority.PreparedPublication.class);
        when(authority.prepare(null, "1", "release", "TASK_CREATION", false)).thenReturn(prepared);
        var admission = mock(EnterpriseAccess.Admission.class);
        var scope = mock(AgentReleaseScopeAuthority.VerifiedScope.class, RETURNS_DEEP_STUBS);
        when(scope.action()).thenReturn("task.create"); when(scope.classification()).thenReturn("INTERNAL");
        when(scope.target().project_id()).thenReturn("project");
        when(scope.release().release().releaseId()).thenReturn("release");
        when(scope.release().release().manifestArtifactId()).thenReturn("manifest-artifact");
        var template = mock(TemplateApplicabilityService.Target.class, RETURNS_DEEP_STUBS);
        when(template.purpose()).thenReturn("TASK_CREATION"); when(template.admission()).thenReturn(admission);
        when(template.view().template().content_sha256()).thenReturn("a".repeat(64));
        var publication = mock(AgentPublicationAuthority.VerifiedPublication.class, RETURNS_DEEP_STUBS);
        when(authority.require(admission, "release", "TASK_CREATION", prepared)).thenReturn(publication);
        var fixedManifest = publication.manifest();
        when(scope.release().content()).thenReturn(fixedManifest);
        when(publication.executionPolicyId()).thenReturn("policy");
        when(publication.executionPolicySha256()).thenReturn("b".repeat(64));
        when(publication.authorizations()).thenReturn(json.object().put("verified", true));
        var verifiedPolicy = mock(AgentExecutionPolicyAuthority.VerifiedPolicy.class);
        when(policies.requireByVersion(admission, "policy", "b".repeat(64), "project", "INTERNAL", "TASK_CREATION"))
                .thenReturn(verifiedPolicy);
        when(policies.requireByVersion(admission, "policy", "b".repeat(64), "project", "TASK_CREATION"))
                .thenReturn(verifiedPolicy);
        var definition = policy("FIXED_SOURCE_VERSIONS", false, List.of());
        when(definition.environment()).thenReturn("test"); when(definition.region()).thenReturn("test-region");
        when(definition.allowedClassifications()).thenReturn(List.of("INTERNAL"));
        when(verifiedPolicy.definition()).thenReturn(definition);
        when(verifiedPolicy.authorizations()).thenReturn(json.object().put("approved", true));
        var manifest = publication.manifest().manifest();
        when(manifest.releaseScope().sourceScopePolicy()).thenReturn(new AgentReleaseProtocol.VersionRef(ref("c").version_id(), ref("c").content_sha256()));
        when(manifest.releaseScope().allowedClassifications()).thenReturn(List.of("INTERNAL"));
        when(manifest.templateVersions()).thenReturn(List.of(new AgentReleaseProtocol.VersionRef("template", "a".repeat(64))));
        var selection = new ScopeSelectionAuthority.Selection(List.of(), List.of(), java.util.Map.of());
        when(selections.requireForTaskCreation(admission, input, verifiedPolicy, "test", "test-region", null)).thenReturn(selection);
        var preparedConsumer = consumer.prepare(null, "1", input);
        verify(authority).prepare(null, "1", "release", "TASK_CREATION", false);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(verifiedPolicy, preparedConsumer.sourcePolicy(admission, input));
            assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
                    () -> preparedConsumer.sourcePolicy(admission, mock(TaskProtocol.ValidatedCreate.class))).code());
            var result = preparedConsumer.require(admission, input, template, selection, scope);
            assertTrue(result.missingRequirements().isEmpty());
            assertTrue(result.dependencyVersions().containsAll(List.of("release", "manifest-artifact")));
            assertEquals("PUBLISHED_TASK_CREATE_ADMISSION", result.authorizations().get(0).path("kind").asText());
            result.requireUnchanged(); verify(publication).requireUnchanged(); verify(verifiedPolicy).requireUnchanged();
            verify(scopes).requireUnchanged(scope);
            // Creation itself adds task:sources and NEEDS_MATERIALS. No success/READY claim is made here.
            ((com.fasterxml.jackson.databind.node.ArrayNode) result.authorizations()).removeAll();
            assertFalse(result.authorizations().isEmpty());
            doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(publication).requireUnchanged();
            assertEquals(ErrorCode.SCOPE_STALE, assertThrows(TenantAccessException.class, result::requireUnchanged).code());
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verify(authority, times(1)).prepare(null, "1", "release", "TASK_CREATION", false);
    }

    @Test void requestedNetworkScopeIsRejectedBeforePublicationPreparation() throws Exception {
        var authority = mock(AgentPublicationAuthority.class);
        var beans = new StaticListableBeanFactory(); beans.addBean("publication", authority);
        var consumer = new PublishedTaskCreateAdmission(beans.getBeanProvider(AgentPublicationAuthority.class), null, null, null, null, null, null);
        var input = mock(TaskProtocol.ValidatedCreate.class, RETURNS_DEEP_STUBS);
        when(input.request().scope_selection().networkEnabled()).thenReturn(true);
        assertEquals(ErrorCode.NETWORK_SCOPE_DENIED, assertThrows(TenantAccessException.class,
                () -> consumer.prepare(null, "1", input)).code());
        verifyNoInteractions(authority);
    }

}
