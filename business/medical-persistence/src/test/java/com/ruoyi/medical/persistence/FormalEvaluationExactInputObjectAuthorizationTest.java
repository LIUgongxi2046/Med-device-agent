package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** These tests execute real action/scope/purpose/actor/expiry grant evaluation and real RoleScope validation. */
class FormalEvaluationExactInputObjectAuthorizationTest extends ExactInputAuthorizationFixture {
    FormalEvaluationExactInputObjectAuthorizationTest() throws Exception { }

    @Test void originalEvaluationOwnerGrantAndExactRolePermissionAreBothRetained() throws Exception {
        var witness = authorize();
        assertEquals(AgentPublicationWitness.tree(grants.get(0)), witness.required("grant"));
        assertEquals(roleScope, witness.required("role_scope"));
        assertEquals(project, witness.at("/role_permission/scope/resource_id").asText());
        assertEquals("PROJECT", witness.at("/role_permission/scope/resource_kind").asText());
        assertEquals(evaluation, witness.at("/permission/scope/resource_id").asText());
        assertEquals("EVALUATION", witness.at("/permission/scope/resource_kind").asText());
        assertEquals("EVALUATION_RESULTS", witness.at("/permission/purpose").asText());
        verify(permissionRows, times(2)).resourceOwner("EVALUATION", evaluation);
        verify(permissionRows, times(2)).resourceGrants(actor, "artifact.register");
        verify(root).requireUnchanged();
        verify(role).requireUnchanged();
    }

    @Test void evaluationRunDoesNotImplyArtifactRegistration() throws Exception {
        grants.clear();
        grants.add(grant("EVALUATION", evaluation, project, "evaluation.run", "EVALUATION_RESULTS", actor, 1));
        denied();
        verifyNoInteractions(capabilities);
    }

    @Test void sourceTaskArtifactAndAnotherEvaluationGrantsCannotBeBorrowed() throws Exception {
        for (String kind : List.of("SOURCE", "TASK", "ARTIFACT", "CONFIGURATION")) {
            grants.clear();
            grants.add(grant(kind, evaluation, project, "artifact.register", "EVALUATION_RESULTS", actor, 1));
            denied();
        }
        grants.clear();
        grants.add(grant("EVALUATION", id(999), project, "artifact.register", "EVALUATION_RESULTS", actor, 1));
        denied();
    }

    @Test void anotherActorProjectOrPurposeCannotAuthorizeThisWrite() throws Exception {
        for (var grant : List.of(
                grant("EVALUATION", evaluation, project, "artifact.register", "DOCUMENT_BODY", actor, 1),
                grant("EVALUATION", evaluation, project, "artifact.register", "EVALUATION_RESULTS", id(999), 1),
                grant("EVALUATION", evaluation, id(999), "artifact.register", "EVALUATION_RESULTS", actor, 1))) {
            grants.clear();
            grants.add(grant);
            denied();
        }
    }

    @Test void missingOrWrongActualDomainOwnerRejectsEvenMatchingGrant() throws Exception {
        when(permissionRows.resourceOwner("EVALUATION", evaluation)).thenReturn(List.of());
        denied();
        when(permissionRows.resourceOwner("EVALUATION", evaluation)).thenReturn(
                List.of(new PermissionProtocol.OwnerRow(evaluation, id(999))));
        denied();
    }

    @Test void expiredOrRevokedCurrentGrantFails() throws Exception {
        now = now.plusDays(2);
        denied();
        grants.clear();
        denied();
    }

    @Test void exactRoleActionOwnerProjectAndPurposeCannotBeWidened() throws Exception {
        for (JsonNode wrong : List.of(
                scope("PROJECT", project, project, "evaluation.run", "EVALUATION_RESULTS"),
                scope("SOURCE", project, project, "artifact.register", "EVALUATION_RESULTS"),
                scope("PROJECT", id(999), project, "artifact.register", "EVALUATION_RESULTS"),
                scope("PROJECT", project, id(999), "artifact.register", "EVALUATION_RESULTS"),
                scope("PROJECT", project, project, "artifact.register", "DOCUMENT_BODY"),
                scope("EVALUATION", evaluation, project, "artifact.register", "EVALUATION_RESULTS"))) {
            roleScope = wrong;
            denied();
        }
    }

    @Test void existingEnterpriseGrantStillRequiresExactNarrowedRole() throws Exception {
        grants.clear();
        grants.add(grant("ENTERPRISE", tenant, null, "artifact.register", "EVALUATION_RESULTS", actor, 1));
        assertEquals("ENTERPRISE", authorize().at("/grant/resource_kind").asText());
        roleScope = scope("ENTERPRISE", tenant, null, "artifact.register", "EVALUATION_RESULTS");
        denied();
    }

    @Test void roleChangeAndSameTransactionGrantReplacementAreRechecked() throws Exception {
        doAnswer(invocation -> {
            grants.clear();
            grants.add(grant("EVALUATION", evaluation, project, "artifact.register", "EVALUATION_RESULTS", actor, 2));
            return null;
        }).when(role).requireUnchanged();
        assertEquals(ErrorCode.SCOPE_STALE, assertThrows(TenantAccessException.class, this::authorize).code());
    }

    @Test void noTenantTransactionCannotMintAuthorizationEvidence() {
        assertThrows(TenantAccessException.class, () -> authorization.require(admission, root, checked.node()));
        verifyNoInteractions(permissionRows, capabilities);
    }

    private void denied() {
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, this::authorize).code());
    }
}
