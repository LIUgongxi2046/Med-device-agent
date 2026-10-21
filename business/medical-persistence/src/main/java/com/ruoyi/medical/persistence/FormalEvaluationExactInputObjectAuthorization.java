package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.Objects;

/**
 * Independent registration permission for the derived EVALUATION-owned object.
 * evaluation.run admits the original definition; it does not imply this write.
 * This is a session-side preparation operation, not a run-token or node-action producer.
 */
public final class FormalEvaluationExactInputObjectAuthorization {
    private static final String PURPOSE = "EVALUATION_RESULTS";
    private static final String ACTION = "artifact.register";

    private final ResourceReadAuthority resources;
    private final FormalCapabilityIdentityAuthority capabilities;
    private final RunTokenAuthoritySchema roleSchema;
    private final PermissionSchema json;

    public FormalEvaluationExactInputObjectAuthorization(
            ResourceReadAuthority resources,
            FormalCapabilityIdentityAuthority capabilities,
            RunTokenAuthoritySchema roleSchema,
            PermissionSchema json) {
        this.resources = resources;
        this.capabilities = capabilities;
        this.roleSchema = roleSchema;
        this.json = json;
    }

    /**
     * Inside the current tenant transaction. Resolve the original evaluation owner,
     * consume an existing current actor grant and require the fixed N01 project permission.
     * The full grant row is evidence, never a permission supplied by the caller.
     */
    JsonNode require(EnterpriseAccess.Admission admission,
            FormalEvaluationExecutionAdmission.Prepared root,
            FormalEvaluationExecutionProtocol.Node node) throws Exception {
        FormalEvaluationExecutionAdmission.current(admission);
        if (!"N01".equals(node.nodeKind()) || !root.nodes().contains(node)
                || !root.request().evaluationId().equals(node.cell().evaluationId())) {
            throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        }
        var scope = new PermissionProtocol.Scope(admission.context().tenant_id(),
                root.projectId(), "EVALUATION", root.request().evaluationId());
        var permission = new RunTokenAuthoritySchema.Permission(scope, ACTION, PURPOSE);
        // The ROLE is authored before EvaluationService creates the evaluation UUID.
        // This consumer requires this one exact project-level preparation permission;
        // it does not make RoleScope membership generally inherit ancestor scopes.
        if (root.projectId() == null) {
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        }
        var rolePermission = new RunTokenAuthoritySchema.Permission(
                new PermissionProtocol.Scope(admission.context().tenant_id(), root.projectId(),
                        "PROJECT", root.projectId()), ACTION, PURPOSE);
        var grant = resources.require(admission, scope, ACTION, PURPOSE);
        var role = capabilities.require(admission, node.role(), "ROLE", root.projectId(), PURPOSE);
        var narrowed = roleSchema.read("RoleScope", json.canonical(
                role.canonicalDefinition().required("request").required("tool_scope")));
        boolean allowed = false;
        for (var entry : narrowed.required("permissions")) {
            if (json.tree(rolePermission).equals(entry)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        }

        var witness = json.object().put("identity_mode", admission.bindingView().mode());
        witness.set("permission", json.tree(permission));
        witness.set("role_permission", json.tree(rolePermission));
        witness.set("grant", AgentPublicationWitness.tree(grant));
        witness.set("role_version", json.tree(node.role()));
        witness.set("role_scope", narrowed);
        witness.set("role_authorizations", role.authorizations());
        root.requireUnchanged();
        role.requireUnchanged();
        // A same-transaction reread must retain the exact grant, not another matching row.
        if (!Objects.equals(grant, resources.require(admission, scope, ACTION, PURPOSE))) {
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        }
        return witness;
    }
}
