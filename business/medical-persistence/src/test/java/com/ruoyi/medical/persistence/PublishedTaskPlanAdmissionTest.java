package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Synthetic authority boundaries only, not production approval or SQL rollback evidence. */
class PublishedTaskPlanAdmissionTest {
    private static PublishedTaskPlanAdmission unavailable() {
        return new PublishedTaskPlanAdmission(new StaticListableBeanFactory().getBeanProvider(AgentPublicationAuthority.class),
                null, null, null, null, null, null, null);
    }
    @Test void missingAndUnpreparedProvidersStayClosed() {
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class,
                () -> unavailable().prepare(null, "1", null, null)).code());
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class,
                () -> unavailable().require(null, null, null, null)).code());
        TaskPlanReleaseAdmission old = (a,t,d,s) -> { throw new AssertionError(); };
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class,
                () -> old.prepare(null, "1", null, null)).code());
    }
    @Test void preparationCannotReadObjectsInsideTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                    () -> unavailable().prepare(null, "1", null, null)).code());
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
    static class Fixture {
        final AgentPublicationAuthority publication = mock(AgentPublicationAuthority.class);
        final AgentExecutionPolicyAuthority policies = mock(AgentExecutionPolicyAuthority.class);
        final AgentReleaseScopeAuthority scopes = mock(AgentReleaseScopeAuthority.class);
        final TaskPlanService plans = mock(TaskPlanService.class);
        final TemplateVersionStore templates = mock(TemplateVersionStore.class);
        final TaskInputSchema inputs = mock(TaskInputSchema.class);
        final TemplateDependencyPolicy limits = mock(TemplateDependencyPolicy.class);
        final TaskService.VerifiedTask task = mock(TaskService.VerifiedTask.class, RETURNS_DEEP_STUBS);
        final TaskPlanWriteProtocol.View draft = mock(TaskPlanWriteProtocol.View.class);
        final EnterpriseAccess.Admission admission = mock(EnterpriseAccess.Admission.class);
        final AgentReleaseScopeAuthority.VerifiedScope scope = mock(AgentReleaseScopeAuthority.VerifiedScope.class, RETURNS_DEEP_STUBS);
        final AgentPublicationAuthority.VerifiedPublication published = mock(AgentPublicationAuthority.VerifiedPublication.class, RETURNS_DEEP_STUBS);
        final AgentExecutionPolicyAuthority.VerifiedPolicy policy = mock(AgentExecutionPolicyAuthority.VerifiedPolicy.class);
        final TaskProtocol.ValidatedCreate input = mock(TaskProtocol.ValidatedCreate.class, RETURNS_DEEP_STUBS);
        final PublishedTaskPlanAdmission consumer;
        Fixture() throws Exception {
            var beans = new StaticListableBeanFactory(); beans.addBean("publication", publication);
            var json = new PermissionSchema();
            consumer = new PublishedTaskPlanAdmission(beans.getBeanProvider(AgentPublicationAuthority.class), policies,
                    scopes, plans, templates, inputs, limits, json);
            when(task.input().requestJson()).thenReturn("{}"); when(inputs.create(any(byte[].class))).thenReturn(input);
            when(task.head().agentReleaseId()).thenReturn("release"); when(task.head().taskId()).thenReturn("task");
            when(task.head().projectId()).thenReturn("project"); when(task.head().tenantId()).thenReturn("tenant");
            when(task.head().status()).thenReturn("PLAN_PENDING"); when(draft.status()).thenReturn("DRAFT");
            when(task.head().taskRevision()).thenReturn("revision"); when(draft.taskRevision()).thenReturn("revision");
            when(task.head().templateVersion()).thenReturn("template"); when(draft.templateVersion()).thenReturn("template");
            when(task.head().snapshotId()).thenReturn("snapshot"); when(draft.snapshotId()).thenReturn("snapshot");
            when(task.head().profileRevision()).thenReturn("profile"); when(draft.profileRevision()).thenReturn("profile");
            when(scope.action()).thenReturn("plan.confirm"); when(scope.classification()).thenReturn("INTERNAL");
            when(scope.target().project_id()).thenReturn("project"); when(scope.target().tenant_id()).thenReturn("tenant");
            when(scope.target().resource_kind()).thenReturn("TASK"); when(scope.target().resource_id()).thenReturn("task");
            when(scope.release().release().releaseId()).thenReturn("release");
            when(scope.release().release().manifestArtifactId()).thenReturn("manifest");
            var prepared = mock(AgentPublicationAuthority.PreparedPublication.class);
            when(publication.prepare(null,"1","release","PLAN_MAINTENANCE",false)).thenReturn(prepared);
            when(publication.require(admission,"release","PLAN_MAINTENANCE",prepared)).thenReturn(published);
            var fixedManifest = published.manifest(); when(scope.release().content()).thenReturn(fixedManifest);
            when(published.executionPolicyId()).thenReturn("policy"); when(published.executionPolicySha256()).thenReturn("b".repeat(64));
            when(policies.requireByVersion(admission,"policy","b".repeat(64),"project","INTERNAL","PLAN_MAINTENANCE")).thenReturn(policy);
            var definition = mock(AgentExecutionPolicyProtocol.Definition.class);
            var source = new PermissionProtocol.Basis("00000000-0000-4000-8000-000000000001", "c".repeat(64));
            when(definition.sourceSelection()).thenReturn(new AgentExecutionPolicyProtocol.SourceSelection(source,"FIXED_SOURCE_VERSIONS",List.of()));
            when(definition.networkPolicy()).thenReturn(new AgentExecutionPolicyProtocol.NetworkPolicy(source,List.of(),false));
            when(definition.allowedClassifications()).thenReturn(List.of("INTERNAL"));
            when(definition.environment()).thenReturn("test"); when(definition.region()).thenReturn("region");
            when(policy.definition()).thenReturn(definition); when(policy.businessPurpose()).thenReturn("DOCUMENT_BODY");
            when(limits.environment()).thenReturn("test"); when(limits.region()).thenReturn("region"); when(limits.maximumVersions()).thenReturn(100);
            var manifest = published.manifest().manifest();
            when(manifest.releaseScope().sourceScopePolicy()).thenReturn(new AgentReleaseProtocol.VersionRef(source.version_id(), source.content_sha256()));
            when(manifest.releaseScope().allowedClassifications()).thenReturn(List.of("INTERNAL"));
            when(manifest.templateVersions()).thenReturn(List.of(new AgentReleaseProtocol.VersionRef("template","a".repeat(64))));
            var template = mock(TemplateProtocol.Stored.class); when(template.content_sha256()).thenReturn("a".repeat(64));
            when(templates.historicalTarget(admission,"template","PLAN_MAINTENANCE")).thenReturn(template);
            when(published.authorizations()).thenReturn(json.object().put("synthetic_publication",true));
            when(policy.authorizations()).thenReturn(json.object().put("synthetic_policy",true));
        }
        TaskPlanReleaseAdmission prepared() throws Exception { return consumer.prepare(null,"1",task,draft); }
    }
    @Test void genuineAuthorityInterfacesAreConsumedAndRecheckedWithoutRepeatedPreparation() throws Exception {
        var f = new Fixture(); var prepared = f.prepared();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            var proof = prepared.require(f.admission,f.task,f.draft,f.scope);
            assertEquals("PUBLISHED_TASK_PLAN_ADMISSION",proof.authorizations().get(0).path("kind").asText());
            assertTrue(proof.dependencyVersions().containsAll(List.of("release","manifest")));
            ((com.fasterxml.jackson.databind.node.ArrayNode)proof.authorizations()).removeAll(); assertFalse(proof.authorizations().isEmpty());
            proof.requireUnchanged(); verify(f.published).requireUnchanged(); verify(f.policy).requireUnchanged(); verify(f.scopes).requireUnchanged(f.scope);
            doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(f.policy).requireUnchanged();
            assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,proof::requireUnchanged).code());
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verify(f.publication,times(1)).prepare(null,"1","release","PLAN_MAINTENANCE",false);
    }
    @Test void changedTaskOrDraftCannotConsumePreparedProof() throws Exception {
        var f = new Fixture(); var prepared = f.prepared();
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,
                () -> prepared.require(f.admission,mock(TaskService.VerifiedTask.class,RETURNS_DEEP_STUBS),f.draft,f.scope)).code());
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,
                () -> prepared.require(f.admission,f.task,mock(TaskPlanWriteProtocol.View.class),f.scope)).code());
        verify(f.publication,never()).require(any(),any(),any(),any());
    }
    @Test void taskNetworkAndWrongActionFailWithoutExecutableProof() throws Exception {
        var f = new Fixture(); when(f.input.request().scope_selection().networkEnabled()).thenReturn(true);
        assertEquals(ErrorCode.NETWORK_SCOPE_DENIED,assertThrows(TenantAccessException.class,f::prepared).code());
        verifyNoInteractions(f.publication);
        when(f.input.request().scope_selection().networkEnabled()).thenReturn(false); var prepared=f.prepared();
        when(f.scope.action()).thenReturn("task.create");
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,
                () -> prepared.require(f.admission,f.task,f.draft,f.scope)).code());
    }
    @Test void disabledPolicyAndWrongEnvironmentFailClosed() throws Exception {
        var f=new Fixture(); var prepared=f.prepared();
        doThrow(new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE)).when(f.policies)
                .requireByVersion(any(),any(),any(),any(),any(),any());
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,
                () -> prepared.require(f.admission,f.task,f.draft,f.scope)).code());
        var g=new Fixture(); when(g.limits.region()).thenReturn("wrong-region"); var ready=g.prepared();
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,
                () -> ready.require(g.admission,g.task,g.draft,g.scope)).code());
    }
    @Test void networkPolicyAndTemplateMismatchReject() throws Exception {
        var f=new Fixture(); var ref=new PermissionProtocol.Basis("00000000-0000-4000-8000-000000000001","a".repeat(64));
        when(f.policy.definition().networkPolicy()).thenReturn(new AgentExecutionPolicyProtocol.NetworkPolicy(ref,List.of(),true));
        var prepared=f.prepared();
        assertEquals(ErrorCode.NETWORK_SCOPE_DENIED,assertThrows(TenantAccessException.class,
                () -> prepared.require(f.admission,f.task,f.draft,f.scope)).code());
        var g=new Fixture(); when(g.published.manifest().manifest().templateVersions()).thenReturn(List.of()); var ready=g.prepared();
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,
                () -> ready.require(g.admission,g.task,g.draft,g.scope)).code());
    }
    @Test void publicationRevocationAndFixedSourceDriftCannotPassRecheck() throws Exception {
        var f=new Fixture(); var proof=f.prepared().require(f.admission,f.task,f.draft,f.scope);
        doThrow(new TenantAccessException(ErrorCode.SCOPE_STALE)).when(f.published).requireUnchanged();
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,proof::requireUnchanged).code());
        var g=new Fixture(); var checked=g.prepared().require(g.admission,g.task,g.draft,g.scope);
        when(g.plans.sourceProof(g.admission,"project","task","revision","snapshot","profile",true))
                .thenReturn(List.of(mock(SourceReadProtocol.VerifiedSource.class)));
        assertEquals(ErrorCode.SCOPE_STALE,assertThrows(TenantAccessException.class,checked::requireUnchanged).code());
    }
    @Test void fixedSourceOutsidePolicyIsNotAdmitted() throws Exception {
        var f=new Fixture(); var source=mock(SourceReadProtocol.VerifiedSource.class,RETURNS_DEEP_STUBS);
        when(source.source().sourceVersion()).thenReturn("00000000-0000-4000-8000-000000000001");
        when(source.source().originalSha256()).thenReturn("e".repeat(64));
        when(f.plans.sourceProof(f.admission,"project","task","revision","snapshot","profile",true)).thenReturn(List.of(source));
        var ready=f.prepared();
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,
                () -> ready.require(f.admission,f.task,f.draft,f.scope)).code());
    }

}
