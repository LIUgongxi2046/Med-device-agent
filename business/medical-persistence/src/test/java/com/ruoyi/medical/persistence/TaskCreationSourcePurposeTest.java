package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.CommonProtocol;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.VerificationInputFixture.*;

/** Real source rights, resource authorities and request/snapshot schemas; explicit mapper/policy-proof doubles. */
class TaskCreationSourcePurposeTest {
    VerificationInputFixture f; TaskInputSchema inputs;
    AgentExecutionPolicyAuthority.VerifiedPolicy policy; AgentExecutionPolicyProtocol.Definition definition;
    @BeforeEach void setup() throws Exception {
        f = new VerificationInputFixture(); inputs = new TaskInputSchema(f.json);
        entryGrant("task.create"); entryGrant("project.read"); entryGrant("source.read");
        var profile = f.projects.fixedProfile(f.admission, PROJECT, PROFILE, PURPOSE);
        when(f.projects.fixedProfile(f.admission, PROJECT, PROFILE, "TASK_CREATION")).thenReturn(profile);
        policy = mock(AgentExecutionPolicyAuthority.VerifiedPolicy.class); definition = mock(AgentExecutionPolicyProtocol.Definition.class);
        when(policy.definition()).thenReturn(definition); when(policy.businessPurpose()).thenReturn(PURPOSE);
        when(definition.environment()).thenReturn("OFFLINE_TEST"); when(definition.region()).thenReturn("LOCAL");
        when(definition.networkPolicy()).thenReturn(new AgentExecutionPolicyProtocol.NetworkPolicy(
                new PermissionProtocol.Basis(id(251), hash("network")), List.of(), false));
        when(definition.sourceSelection()).thenReturn(new AgentExecutionPolicyProtocol.SourceSelection(
                new PermissionProtocol.Basis(id(250), hash("selection")), "FIXED_SOURCE_VERSIONS",
                List.of(new PermissionProtocol.Basis(SOURCE, f.sourceRows.get(SOURCE).originalSha256()))));
    }
    @AfterEach void close() throws Exception { f.close(); }
    TaskProtocol.ValidatedCreate request() throws Exception {
        var r = f.json.object().put("schema_version", "1.0.0").put("project_id", PROJECT).put("profile_revision", PROFILE)
                .put("document_type", "PRODUCT_TECHNICAL_REQUIREMENTS").put("template_version", id(201))
                .put("agent_release_id", id(202)).put("owner_id", ACTOR).put("goal", "Explicit task source-purpose regression")
                .putNull("based_on_document_revision");
        r.set("scope_selection", f.request().required("scope_selection"));
        return inputs.create(f.json.canonical(r).getBytes(StandardCharsets.UTF_8));
    }
    ScopeSnapshotStore.Stored save() throws Exception {
        return f.transaction(() -> f.snapshotStore.saveForTaskCreation(f.admission, request(), policy,
                "OFFLINE_TEST", "LOCAL", ModelVerificationInputService.LIMITS));
    }
    void entryGrant(String action) throws Exception {
        var c = new PermissionProtocol.Condition("1.0.0", List.of("local"), List.of("TASK_CREATION"),
                new PermissionProtocol.Delegation(List.of(), List.of(), List.of(), null, null));
        f.grants.add(new PermissionProtocol.GrantRow(id(2000 + f.grants.size()), ACTOR, null, "ENTERPRISE", TENANT,
                action, f.json.canonicalObject(c), "ACTIVE", f.now.minusDays(1), f.now.plusDays(1),
                id(999), "1.0.0", f.json.manifestSha256(), BigInteger.ONE));
    }
    void removeEntry(String action) { f.grants.removeIf(g -> g.action().equals(action) && g.condition().contains("TASK_CREATION")); }
    void deniedSave() { assertThrows(TenantAccessException.class, this::save); assertTrue(f.snapshots.isEmpty()); }
    @Test void savesNonemptyDocumentBodyRightsWithSeparateTaskEntryGrants() throws Exception {
        var r = save(); assertEquals(1, r.manifest().members().size()); assertEquals(SOURCE, r.manifest().members().get(0).source_version());
        assertEquals(RIGHTS, r.manifest().members().get(0).rights_revision()); assertEquals("DOCUMENT_BODY", r.selection().sources().get(0).rights().purpose());
        assertEquals(2, r.selection().entryGrants().size());
        assertTrue(r.selection().entryGrants().values().stream().allMatch(g -> g.condition().contains("TASK_CREATION")));
        assertTrue(r.selection().sources().get(0).sourceGrant().condition().contains("DOCUMENT_BODY"));
        verify(policy, atLeast(2)).requireConsumer(f.admission, PROJECT, "TASK_CREATION");
        verify(f.projects, atLeast(2)).fixedProfile(f.admission, PROJECT, PROFILE, "TASK_CREATION"); assertEquals(1, f.snapshots.size());
    }
    @Test void taskCreationRightsCannotMasqueradeAsDocumentBody() throws Exception {
        byte[] original = f.served.clone(); f.source(SOURCE, "TASK_CREATION"); f.bytes(original); deniedSave();
    }
    @Test void missingTaskCreateDoesNotFallBackToBusinessGrant() throws Exception { removeEntry("task.create"); f.grant("task.create"); deniedSave(); }
    @Test void missingTaskSourceReadStillDenies() { removeEntry("source.read"); deniedSave(); }
    @Test void missingTaskProjectReadStillDenies() { removeEntry("project.read"); deniedSave(); }
    @Test void missingBusinessSourceReadDoesNotFallBackToEntryGrant() {
        f.grants.removeIf(g -> g.action().equals("source.read") && !g.condition().contains("TASK_CREATION")); deniedSave();
    }
    @Test void mismatchedPolicyConsumerRejectsBeforeWrites() {
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(policy).requireConsumer(f.admission, PROJECT, "TASK_CREATION"); deniedSave();
    }
    @Test void wrongEnvironmentRejectsBeforeWrites() { when(definition.environment()).thenReturn("OTHER"); deniedSave(); }
    @Test void unsupportedBusinessPurposeRejectsEvenMatchingCurrentRights() throws Exception {
        when(policy.businessPurpose()).thenReturn("MODEL_VERIFICATION");
        byte[] original = f.served.clone(); f.source(SOURCE, "MODEL_VERIFICATION"); f.bytes(original); deniedSave();
    }
    @Test void explicitSourceOutsideFixedPolicyRejects() {
        when(definition.sourceSelection()).thenReturn(new AgentExecutionPolicyProtocol.SourceSelection(
                new PermissionProtocol.Basis(id(250), hash("selection")), "FIXED_SOURCE_VERSIONS", List.of())); deniedSave();
    }
    @Test void implicitCandidatesOutsideFixedPolicyAreFilteredBeforeRights() throws Exception {
        when(definition.sourceSelection()).thenReturn(new AgentExecutionPolicyProtocol.SourceSelection(
                new PermissionProtocol.Basis(id(250), hash("selection")), "FIXED_SOURCE_VERSIONS", List.of()));
        var selections = new ScopeSelectionAuthority(f.sources, f.sourceAuthority, f.resources, mock(CollectionAuthority.class));
        var scope = new CommonProtocol.SourceScope(CommonProtocol.ScopeMode.PROJECT_APPROVED, List.of(), List.of(), false, null, null);
        var result = f.transaction(() -> selections.requireForTaskCreation(f.admission, PROJECT, scope, policy, "OFFLINE_TEST", "LOCAL", ModelVerificationInputService.LIMITS));
        assertTrue(result.sources().isEmpty()); verify(f.sources, never()).rights(anyString());
    }
    @Test void collectionHeadEntryGrantIsNotReplacedByBusinessProof() throws Exception {
        var collections = mock(CollectionAuthority.class); var proof = mock(CollectionAuthority.Proof.class);
        var head = mock(CollectionProtocol.Head.class); when(head.projectId()).thenReturn(PROJECT); when(proof.head()).thenReturn(head);
        when(collections.require(eq(f.admission), eq(id(260)), eq(PURPOSE), eq("OFFLINE_TEST"), eq("LOCAL"), any())).thenReturn(proof);
        var selections = new ScopeSelectionAuthority(f.sources, f.sourceAuthority, f.resources, collections);
        var scope = new CommonProtocol.SourceScope(CommonProtocol.ScopeMode.COLLECTION_VERSIONS, List.of(id(260)), List.of(), false, null, null);
        assertThrows(TenantAccessException.class, () -> f.transaction(() -> selections.requireForTaskCreation(
                f.admission, PROJECT, scope, policy, "OFFLINE_TEST", "LOCAL", ModelVerificationInputService.LIMITS)));
        verify(proof, never()).lineage();
    }
    ScopeSelectionAuthority.Selection selectCollection() throws Exception {
        String collectionId = id(261), versionId = id(260);
        entryGrant("collection.read"); f.grant("collection.read");
        var collections = mock(CollectionAuthority.class); var proof = mock(CollectionAuthority.Proof.class);
        var head = mock(CollectionProtocol.Head.class); when(head.projectId()).thenReturn(PROJECT); when(proof.head()).thenReturn(head);
        var graph = new org.jgrapht.graph.DirectedPseudograph<String,SourceReadProtocol.ParentRow>(SourceReadProtocol.ParentRow.class);
        graph.addVertex(versionId); graph.addVertex(SOURCE);
        var version = new ArtifactRegistryProtocol.VersionRow(versionId, PROJECT, "COLLECTION_VERSION", hash("collection"));
        when(proof.lineage()).thenReturn(new DependencyLineage.VerifiedLineage(versionId, graph, Map.of(versionId, version, SOURCE, f.versions.get(SOURCE))));
        var owner = new VersionResourceProtocol.OwnerRow(TENANT, versionId, PROJECT, PROJECT, "PROJECT", PROJECT, hash("collection"), null, null, null, null);
        var grant = f.grants.stream().filter(g -> g.action().equals("collection.read") && !g.condition().contains("TASK_CREATION")).findFirst().orElseThrow();
        when(proof.versions()).thenReturn(Map.of(versionId, new VersionResourceAuthority.VerifiedVersion(version,
                VersionResourceProtocol.Domain.COLLECTION_VERSION, owner, grant, null, null)));
        var manifest = mock(CollectionProtocol.Manifest.class); when(manifest.tenant_id()).thenReturn(TENANT);
        when(manifest.members()).thenReturn(List.of(new CollectionProtocol.Member(SOURCE_ID, SOURCE, PARSE, RIGHTS,
                f.sourceRows.get(SOURCE).originalSha256(), hash("parse")))); when(proof.manifest()).thenReturn(manifest);
        when(collections.require(eq(f.admission), eq(versionId), eq(PURPOSE), eq("OFFLINE_TEST"), eq("LOCAL"), any())).thenReturn(proof);
        var selections = new ScopeSelectionAuthority(f.sources, f.sourceAuthority, f.resources, collections);
        var scope = new CommonProtocol.SourceScope(CommonProtocol.ScopeMode.COLLECTION_VERSIONS, List.of(versionId), List.of(), false, null, null);
        return f.transaction(() -> selections.requireForTaskCreation(f.admission, PROJECT, scope, policy,
                "OFFLINE_TEST", "LOCAL", ModelVerificationInputService.LIMITS));
    }
    @Test void nonemptyCollectionRetainsItsWholeFixedMemberAndEntryGrants() throws Exception {
        var result = selectCollection(); assertEquals(1, result.sources().size()); assertEquals(1, result.collectionMembers().size());
        assertEquals(SOURCE, result.collectionMembers().get(0).sourceVersion()); assertEquals(3, result.entryGrants().size());
    }
    @Test void collectionMemberOutsidePolicyRejectsWholeCollection() throws Exception {
        when(definition.sourceSelection()).thenReturn(new AgentExecutionPolicyProtocol.SourceSelection(
                new PermissionProtocol.Basis(id(250), hash("selection")), "FIXED_SOURCE_VERSIONS", List.of()));
        assertThrows(TenantAccessException.class, this::selectCollection); verify(f.sources, never()).rights(anyString());
    }
    @Test void collectionMemberWithDifferentFixedHashRejectsWholeCollection() throws Exception {
        when(definition.sourceSelection()).thenReturn(new AgentExecutionPolicyProtocol.SourceSelection(
                new PermissionProtocol.Basis(id(250), hash("selection")), "FIXED_SOURCE_VERSIONS",
                List.of(new PermissionProtocol.Basis(SOURCE, hash("different fixed original")))));
        assertThrows(TenantAccessException.class, this::selectCollection); verify(f.sources, never()).rights(anyString());
    }
    @Test void typedSnapshotScopeCannotEnableNetworkWithoutQueryApproval() throws Exception {
        var selections = new ScopeSelectionAuthority(f.sources, f.sourceAuthority, f.resources, mock(CollectionAuthority.class));
        var scope = new CommonProtocol.SourceScope(CommonProtocol.ScopeMode.SOURCE_VERSIONS, List.of(), List.of(SOURCE), true, id(270), id(271));
        assertEquals(ErrorCode.NETWORK_SCOPE_DENIED, assertThrows(TenantAccessException.class,
                () -> f.transaction(() -> selections.requireForTaskCreation(f.admission, PROJECT, scope, policy,
                        "OFFLINE_TEST", "LOCAL", ModelVerificationInputService.LIMITS))).code());
        assertTrue(f.snapshots.isEmpty()); verify(f.sources, never()).rights(anyString());
    }
    @Test void typedSnapshotRejectsPolicyExternalRequestsEvenWithLocalSelectedScope() throws Exception {
        when(definition.networkPolicy()).thenReturn(new AgentExecutionPolicyProtocol.NetworkPolicy(
                new PermissionProtocol.Basis(id(251), hash("network")), List.of("https://example.test"), true));
        assertEquals(ErrorCode.NETWORK_SCOPE_DENIED, assertThrows(TenantAccessException.class, this::save).code());
        assertTrue(f.snapshots.isEmpty());
    }
    @Test void entryRevocationDuringReadbackRejects() throws Exception {
        when(f.snapshotRows.parents(anyMap())).thenAnswer(i -> { Map<String,Object> v=i.getArgument(0); removeEntry("source.read"); return f.snapshotParents.getOrDefault(v.get("snapshotId"), List.of()); });
        assertThrows(TenantAccessException.class, this::save);
    }
    @Test void entryGrantRevisionIsIncludedInSelectionEquality() throws Exception {
        when(f.snapshotRows.parents(anyMap())).thenAnswer(i -> {
            Map<String,Object> v=i.getArgument(0);
            for (int n=0; n<f.grants.size(); n++) { var g=f.grants.get(n);
                if (g.action().equals("source.read") && g.condition().contains("TASK_CREATION"))
                    f.grants.set(n, new PermissionProtocol.GrantRow(g.grantId(), g.subjectId(), g.projectId(), g.resourceKind(), g.resourceId(),
                            g.action(), g.condition(), g.status(), g.validFrom(), g.validTo(), g.basisVersion(), g.schemaVersion(), g.manifest(), g.revision().add(BigInteger.ONE)));
            }
            return f.snapshotParents.getOrDefault(v.get("snapshotId"), List.of());
        });
        assertEquals(ErrorCode.SCOPE_STALE, assertThrows(TenantAccessException.class, this::save).code());
    }
}
