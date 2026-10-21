package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual ResourceReadAuthority/PermissionAuthority; database rows and private original ROLE are doubles. */
class ExactInputAuthorizationFixture extends ExactInputObjectFixture {
    final PermissionMapper permissionRows = mock(PermissionMapper.class);
    final List<PermissionProtocol.GrantRow> grants = new ArrayList<>();
    final FormalCapabilityIdentityAuthority capabilities = mock(FormalCapabilityIdentityAuthority.class);
    final FormalCapabilityIdentityAuthority.VerifiedCapability role = mock(FormalCapabilityIdentityAuthority.VerifiedCapability.class);
    final FormalEvaluationExecutionAdmission.Prepared root = mock(FormalEvaluationExecutionAdmission.Prepared.class);
    final ResourceReadAuthority resources = new ResourceReadAuthority(permissionRows, new PermissionAuthority(json), json);
    final FormalEvaluationExactInputObjectAuthorization authorization = new FormalEvaluationExactInputObjectAuthorization(
            resources, capabilities, new RunTokenAuthoritySchema(), json);
    JsonNode roleScope;

    ExactInputAuthorizationFixture() throws Exception {
        when(admission.bindingView()).thenReturn(new AuthenticatedIdentityBinding.View("local", actor, null, actor));
        when(permissionRows.databaseTime()).thenAnswer(invocation -> now);
        when(permissionRows.resourceOwner("EVALUATION", evaluation)).thenReturn(
                List.of(new PermissionProtocol.OwnerRow(evaluation, project)));
        when(permissionRows.resourceGrants(eq(actor), anyString())).thenAnswer(invocation -> grants.stream()
                .filter(grant -> grant.action().equals(invocation.getArgument(1))).toList());
        grants.add(grant("EVALUATION", evaluation, project, "artifact.register", "EVALUATION_RESULTS", actor, 1));
        when(root.projectId()).thenReturn(project);
        when(root.request()).thenReturn(checked.original().request().start());
        when(root.nodes()).thenReturn(List.of(checked.node()));
        when(capabilities.require(admission, checked.node().role(), "ROLE", project, "EVALUATION_RESULTS")).thenReturn(role);
        roleScope = scope("PROJECT", project, project, "artifact.register", "EVALUATION_RESULTS");
        when(role.canonicalDefinition()).thenAnswer(invocation -> {
            var fixed = json.object();
            fixed.putObject("request").set("tool_scope", roleScope.deepCopy());
            return fixed;
        });
        when(role.authorizations()).thenAnswer(invocation -> json.object().put("boundary", "original role proof double"));
    }

    PermissionProtocol.GrantRow grant(String kind, String resource, String grantProject,
            String action, String purpose, String subject, int revision) throws Exception {
        var condition = new PermissionProtocol.Condition("1.0.0", List.of("local"), List.of(purpose),
                new PermissionProtocol.Delegation(List.of(), List.of(), List.of(), null, null));
        return new PermissionProtocol.GrantRow(id(800), subject, grantProject, kind, resource, action,
                json.canonicalObject(condition), "ACTIVE", now.minusDays(1), now.plusDays(1), id(801),
                "1.0.0", json.manifestSha256(), BigInteger.valueOf(revision));
    }

    JsonNode scope(String kind, String resource, String ownerProject, String action, String purpose) {
        var value = json.object().put("schema_version", "1.0.0");
        value.putArray("permissions").add(json.tree(new RunTokenAuthoritySchema.Permission(
                new PermissionProtocol.Scope(tenant, ownerProject, kind, resource), action, purpose)));
        return value;
    }

    JsonNode authorize() throws Exception {
        return tx(() -> authorization.require(admission, root, checked.node()));
    }
}
