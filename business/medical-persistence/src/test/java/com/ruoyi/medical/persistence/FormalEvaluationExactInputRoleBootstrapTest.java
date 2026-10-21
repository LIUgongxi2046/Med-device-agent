package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real existing ROLE authoring/journal/identity methods and real grant evaluation.
 * Existing storage fixture doubles are reused; original package closure and EVAL root
 * remain named boundaries. No evaluation run or independently approved policy is fabricated. */
class FormalEvaluationExactInputRoleBootstrapTest {
    final PolicyStorageFixture storage = new PolicyStorageFixture();
    final String project = PolicyStorageFixture.id(701);
    final List<PermissionProtocol.GrantRow> grants = new ArrayList<>();
    final ResourceReadAuthority resources = new ResourceReadAuthority(
            storage.p, new PermissionAuthority(storage.json), storage.json);
    String evaluation;

    FormalEvaluationExactInputRoleBootstrapTest() throws Exception {
        grants.add(storage.grant("capability.configure", "AGENT_PUBLICATION"));
        grants.add(storage.grant("configuration.read", "AGENT_PUBLICATION"));
        grants.add(storage.grant("configuration.read", "EVALUATION_RESULTS"));
        grants.add(storage.grant("artifact.register", "EVALUATION_RESULTS"));
        when(storage.p.resourceGrants(eq(storage.actor), anyString())).thenAnswer(invocation -> grants.stream()
                .filter(grant -> grant.action().equals(invocation.getArgument(1))).toList());
        when(storage.p.resourceOwner(anyString(), anyString())).thenAnswer(invocation -> {
            String kind = invocation.getArgument(0), id = invocation.getArgument(1);
            boolean existing = "PROJECT".equals(kind) && project.equals(id)
                    || "CONFIGURATION".equals(kind) && storage.caps.values().stream().anyMatch(row -> row.configId().equals(id))
                    || "EVALUATION".equals(kind) && evaluation != null && evaluation.equals(id);
            return existing ? List.of(new PermissionProtocol.OwnerRow(id, project)) : List.of();
        });
        // Delegate the fixture's authority boundary to the actual authorizer.
        doAnswer(invocation -> resources.require(storage.a, invocation.getArgument(1),
                invocation.getArgument(2), invocation.getArgument(3)))
                .when(storage.resources).require(eq(storage.a), any(), anyString(), anyString());
    }

    ObjectNode request(String kind, String resource) throws Exception {
        var request = storage.capability().put("project_id", project);
        var scope = storage.json.object().put("schema_version", "1.0.0");
        scope.putArray("permissions").add(storage.json.tree(new RunTokenAuthoritySchema.Permission(
                new PermissionProtocol.Scope(PolicyStorageFixture.id(1), project, kind, resource),
                "artifact.register", "EVALUATION_RESULTS")));
        request.set("tool_scope", scope);
        return request;
    }

    @Test void exactFutureEvaluationRoleCannotBeAuthoredAgainstNonexistentOwner() throws Exception {
        String future = UUID.randomUUID().toString();
        assertNull(evaluation);
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> storage.scoped(() -> storage.capabilities.author(storage.a,
                        "future-evaluation-role-key", request("EVALUATION", future)))).code());
        assertTrue(storage.caps.isEmpty());
        assertTrue(storage.records.isEmpty());
    }

    @Test void originalProjectRoleIsAuthoredBeforeEvaluationExistsThenConsumedUnchanged() throws Exception {
        assertNull(evaluation);
        var receipt = storage.scoped(() -> storage.capabilities.author(storage.a,
                "project-registration-role-key", request("PROJECT", project)));
        var record = storage.records.get(receipt.required("resource_id").asText());
        var roleBasis = new PermissionProtocol.Basis(record.resourceId(), record.contentSha256());
        String originalFixed = record.contentJson();
        assertFalse(originalFixed.contains("EVALUATION\""));
        assertEquals(1, storage.caps.size());

        // The domain row appears only after immutable ROLE authoring. The root is an
        // explicit test boundary; its role basis is the genuinely authored identity above.
        evaluation = UUID.randomUUID().toString();
        var root = mock(FormalEvaluationExecutionAdmission.Prepared.class);
        var start = new FormalEvaluationExecutionProtocol.Start("1.0.0", evaluation, PolicyStorageFixture.H,
                "1", new PermissionProtocol.Basis(PolicyStorageFixture.id(702), PolicyStorageFixture.H),
                new PermissionProtocol.Basis(PolicyStorageFixture.id(703), PolicyStorageFixture.H),
                new PermissionProtocol.Basis(PolicyStorageFixture.id(704), PolicyStorageFixture.H),
                new PermissionProtocol.Basis(PolicyStorageFixture.id(705), PolicyStorageFixture.H));
        var cell = new FormalEvaluationExecutionProtocol.Cell(evaluation, PolicyStorageFixture.H,
                start.candidate(), start.executionPolicy(), PolicyStorageFixture.id(706), PolicyStorageFixture.id(707),
                "ACCEPTANCE", 0, start.enterpriseBudget(), start.parentBudget(), PolicyStorageFixture.H);
        var node = new FormalEvaluationExecutionProtocol.Node(cell, "N01", roleBasis,
                start.enterpriseBudget(), start.parentBudget(), PolicyStorageFixture.H);
        when(root.request()).thenReturn(start);
        when(root.projectId()).thenReturn(project);
        when(root.nodes()).thenReturn(List.of(node));
        var authorization = new FormalEvaluationExactInputObjectAuthorization(resources, storage.identities,
                new RunTokenAuthoritySchema(), storage.json);
        var witness = storage.scoped(() -> authorization.require(storage.a, root, node));
        assertEquals(evaluation, witness.at("/permission/scope/resource_id").asText());
        assertEquals(project, witness.at("/role_permission/scope/resource_id").asText());
        assertEquals(storage.json.tree(roleBasis), witness.required("role_version"));
        assertEquals(originalFixed, storage.records.get(record.recordId()).contentJson());
        assertEquals(1, storage.caps.size());
        assertEquals(1, storage.records.size());
        verify(storage.p, atLeastOnce()).resourceOwner("PROJECT", project);
        verify(storage.p, atLeastOnce()).resourceOwner("EVALUATION", evaluation);
    }
}
