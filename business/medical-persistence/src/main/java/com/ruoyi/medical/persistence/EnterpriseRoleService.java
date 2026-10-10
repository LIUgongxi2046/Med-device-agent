package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.EnterpriseRoleMapper;
import com.ruoyi.medical.persistence.mapper.InvitationMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class EnterpriseRoleService {
    private final EnterpriseAccess access;
    private final EnterpriseRoleMapper roles;
    private final InvitationMapper invitations;
    private final PermissionMapper permissions;
    private final EnterpriseRoleSchema schema;
    private final InvitationSchema invitationSchema;
    private final PermissionSchema json;
    private final PermissionAuthority authority;
    private final IdentityDirectoryPolicy policy;
    private final JdbcTemplate jdbc;

    public EnterpriseRoleService(EnterpriseAccess access, EnterpriseRoleMapper roles, InvitationMapper invitations,
            PermissionMapper permissions, EnterpriseRoleSchema schema, InvitationSchema invitationSchema,
            PermissionSchema json, PermissionAuthority authority, IdentityDirectoryPolicy policy,
            @Qualifier("masterDataSource") DataSource master) {
        this.access = access; this.roles = roles; this.invitations = invitations; this.permissions = permissions;
        this.schema = schema; this.invitationSchema = invitationSchema; this.json = json; this.authority = authority;
        this.policy = policy; jdbc = new JdbcTemplate(master);
    }

    public JsonNode list(ApplicationSessionIdentity.Verified subject, String context, String after, int limit) {
        return access.execute(subject, admission -> checked(() -> {
            current(admission, context);
            var managers = authority.roleManagers(admission.context().tenant_id(), mode(admission), roles.managers(actor(admission)));
            java.util.Set<String> allowed = new java.util.TreeSet<>();
            for (var grant : managers) allowed.addAll(json.condition(grant.condition()).delegation().role_ids());
            var heads = allowed.isEmpty() ? List.<EnterpriseRoleProtocol.Head>of() : roles.heads(List.copyOf(allowed), after, limit+1);
            var items = json.tree(List.of()).deepCopy();
            for (var head : heads.subList(0, Math.min(limit, heads.size()))) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) items).add(item(admission, head.roleId(), head.currentVersion()));
            }
            var data = json.object(); data.put("schema_version", "1.0.0"); data.set("roles", items);
            data.set("next_after", json.tree(heads.size() > limit ? heads.get(limit-1).roleId() : null));
            return schema.envelope("Listing", data, UUID.randomUUID().toString(), null);
        }));
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String roleId, String versionId) {
        return access.execute(subject, admission -> checked(() -> {
            current(admission, context); management(admission, roleId, null, null, null);
            JsonNode data = item(admission, roleId, versionId);
            return schema.envelope("Role", data, UUID.randomUUID().toString(), data.required("revision").textValue());
        }));
    }

    public JsonNode publish(ApplicationSessionIdentity.Verified subject, String context, String key, String roleId, byte[] bytes) {
        return access.execute(subject, admission -> checked(() -> {
            var request = schema.publish(bytes, roleId);
            var from = PermissionAuthority.time(request.valid_from());
            var to = request.valid_to() == null ? null : PermissionAuthority.time(request.valid_to());
            var witness = management(admission, roleId, request.definition(), from, to);
            basis(request.approval_basis());
            String path = "/api/enterprise-roles/" + roleId + "/versions";
            String sha = schema.hash(path, request, context, subject.sessionId());
            var saved = roles.currentOperation(actor(admission), "ROLE-03", key);
            if (!saved.isEmpty()) return replay(admission, one(saved, ErrorCode.IDEMPOTENCY_CONFLICT), sha, roleId, "PUBLISH", null);
            current(admission, context);
            var heads = roles.head(roleId);
            if (heads.size() > 1) throw new IllegalStateException("ROLE_HEAD_DUPLICATE");
            String previous = heads.isEmpty() ? "0" : heads.get(0).revision().toString();
            if (!request.expected_head_revision().equals(previous)) denied(ErrorCode.REVISION_CONFLICT);
            PermissionSchema.next(previous);
            String version = UUID.randomUUID().toString();
            LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            var values = values(admission); values.put("roleId", roleId); values.put("versionId", version); values.put("now", now);
            values.put("definition", json.canonicalObject(request.definition()));
            values.put("contentSha256", DigestUtils.sha256Hex((String) values.get("definition")));
            values.put("validFrom", from); values.put("validTo", to); values.put("name", request.name());
            values.put("basis", request.approval_basis().version_id()); values.put("basisHash", request.approval_basis().content_sha256());
            values.put("reason", request.reason()); values.put("authorityGrant", witness.grantId()); values.put("authorityRevision", witness.revision().toString());
            values.put("invitationManifest", invitationSchema.manifestSha256()); values.put("headRevision", previous);
            JsonNode before = heads.isEmpty() ? json.tree(null) : item(admission, roleId, heads.get(0).currentVersion());
            requireOne(roles.insertVersion(values)); requireOne(roles.insertRole(values)); requireOne(roles.insertPublication(values));
            requireOne(heads.isEmpty() ? roles.insertHead(values) : roles.advanceHead(values));
            JsonNode stored = item(admission, roleId, version);
            if (!stored.required("name").textValue().equals(request.name()) || !stored.required("definition").equals(json.tree(request.definition()))
                    || !stored.required("approval_basis").equals(json.tree(request.approval_basis()))
                    || !stored.required("reason").textValue().equals(request.reason()) || !stored.required("authority_grant").textValue().equals(witness.grantId())
                    || !stored.required("authority_revision").textValue().equals(witness.revision().toString())
                    || !stored.required("approved_by").textValue().equals(actor(admission))
                    || !stored.required("valid_from").textValue().equals(from.toInstant(ZoneOffset.UTC).toString())
                    || !stored.required("valid_to").equals(json.tree(to == null ? null : to.toInstant(ZoneOffset.UTC).toString()))
                    || !stored.required("head").required("revision").textValue().equals(PermissionSchema.next(previous))) {
                throw new IllegalStateException("ROLE_PUBLICATION_READ_MISMATCH");
            }
            return complete(admission, key, sha, "ROLE-03", "PUBLISH", stored, before, json.tree(request), witness, List.of(), List.of());
        }));
    }

    public JsonNode revoke(ApplicationSessionIdentity.Verified subject, String context, String key, String roleId, String versionId, byte[] bytes) {
        return access.execute(subject, admission -> checked(() -> {
            var request = schema.revoke(bytes); var witness = management(admission, roleId, null, null, null);
            String path = "/api/enterprise-roles/" + roleId + "/versions/" + versionId + "/revocations";
            String sha = schema.hash(path, request, context, subject.sessionId());
            var saved = roles.currentOperation(actor(admission), "ROLE-04", key);
            if (!saved.isEmpty()) return replay(admission, one(saved, ErrorCode.IDEMPOTENCY_CONFLICT), sha, roleId, "REVOKE", versionId);
            current(admission, context);
            JsonNode before = item(admission, roleId, versionId);
            if (!before.required("status").textValue().equals("ACTIVE") || !before.required("revision").textValue().equals(request.expected_revision())) denied(ErrorCode.REVISION_CONFLICT);
            PermissionSchema.next(request.expected_revision());
            var pending = roles.pendingInvitations(versionId); var grants = roles.activeRoleGrants(versionId);
            for (var invite : pending) {
                if (!"1.0.0".equals(invite.schemaVersion()) || !invitationSchema.manifestSha256().equals(invite.manifest())
                        || invitationSchema.roles(invite.roles()).stream().noneMatch(ref -> ref.version_id().equals(versionId) && ref.role_id().equals(roleId))) {
                    throw new IllegalStateException("ROLE_INVITATION_INVALID");
                }
                PermissionSchema.next(invite.revision().toString());
            }
            for (var grant : grants) PermissionSchema.next(grant.revision().toString());
            requireOne(roles.revokeVersion(versionId, request.expected_revision()));
            List<String> invitationIds = new ArrayList<>(), grantIds = new ArrayList<>();
            for (var invite : pending) {
                requireOne(roles.revokeInvitation(invite.invitationId(), invite.revision().toString(), request.reason())); invitationIds.add(invite.invitationId());
                var stored = one(invitations.currentInvitation(invite.invitationId()), ErrorCode.CONTENT_MISMATCH);
                if (!List.of("REVOKED", "EXPIRED").contains(stored.status())
                        || !PermissionSchema.next(invite.revision().toString()).equals(stored.revision().toString())) throw new IllegalStateException("ROLE_INVITATION_READ_MISMATCH");
            }
            for (var grant : grants) {
                requireOne(permissions.revokeGrant(grant.grantId(), grant.subjectId(), grant.revision().toString())); grantIds.add(grant.grantId());
                var stored = one(permissions.currentGrant(grant.grantId()), ErrorCode.CONTENT_MISMATCH);
                if (!stored.status().equals("REVOKED") || !PermissionSchema.next(grant.revision().toString()).equals(stored.revision().toString())) {
                    throw new IllegalStateException("ROLE_GRANT_READ_MISMATCH");
                }
            }
            JsonNode stored = item(admission, roleId, versionId);
            if (!stored.required("status").textValue().equals("REVOKED")
                    || !stored.required("revision").textValue().equals(PermissionSchema.next(request.expected_revision()))) throw new IllegalStateException("ROLE_REVOCATION_READ_MISMATCH");
            return complete(admission, key, sha, "ROLE-04", "REVOKE", stored, before, json.tree(request), witness, grantIds, invitationIds);
        }));
    }

    private JsonNode replay(EnterpriseAccess.Admission admission, SelectionOperationRow saved, String sha, String roleId, String decision, String expectedVersion) throws IOException {
        if (!sha.equals(saved.requestSha256())) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!saved.state().equals("COMPLETED") || !Integer.valueOf(decision.equals("PUBLISH") ? 201 : 200).equals(saved.httpStatus())
                || !saved.schemaVersion().equals("1.0.0") || !saved.schemaManifestSha256().equals(schema.manifestSha256())) throw new IllegalStateException("ROLE_OPERATION_INVALID");
        JsonNode response = schema.readReceipt(saved.responseRef()), receipt = response.required("data"), original = receipt.required("role");
        if (!saved.operationId().equals(receipt.required("operation_id").textValue()) || !decision.equals(receipt.required("decision").textValue())
                || !roleId.equals(original.required("role_id").textValue()) || !saved.targetId().equals(original.required("version_id").textValue())
                || expectedVersion != null && !expectedVersion.equals(saved.targetId())) throw new IllegalStateException("ROLE_OPERATION_INVALID");
        JsonNode current = item(admission, roleId, saved.targetId());
        if (!current.required("status").equals(original.required("status")) || !current.required("revision").equals(original.required("revision"))) denied(ErrorCode.SCOPE_STALE);
        if (decision.equals("REVOKE") && (!roles.pendingInvitations(saved.targetId()).isEmpty() || !roles.activeRoleGrants(saved.targetId()).isEmpty())) {
            throw new IllegalStateException("ROLE_REVOCATION_INCOMPLETE");
        }
        return response;
    }

    private JsonNode complete(EnterpriseAccess.Admission admission, String key, String sha, String operation, String decision,
            JsonNode role, JsonNode before, JsonNode request, PermissionProtocol.GrantRow witness, List<String> grants, List<String> invitations) throws IOException {
        String auth = PermissionSchema.next(admission.context().auth_epoch()), governance = PermissionSchema.next(admission.context().governance_epoch());
        String context = PermissionSchema.next(admission.context().context_revision()), actor = actor(admission);
        requireOne(roles.advanceEpochs(admission.context().auth_epoch(), admission.context().governance_epoch()));
        requireOne(jdbc.update("""
                UPDATE platform_session_context SET context_revision=?,auth_epoch=?,governance_epoch=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=? AND context_revision=? AND auth_epoch=? AND governance_epoch=?
                """, context, auth, governance, admission.contextId(), admission.subject().sessionId(), actor, admission.context().tenant_id(),
                admission.context().context_revision(), admission.context().auth_epoch(), admission.context().governance_epoch()));
        var current = json.object(); current.put("tenant_id", admission.context().tenant_id()); current.put("context_revision", context);
        current.put("auth_epoch", auth); current.put("governance_epoch", governance);
        String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString(), version = role.required("version_id").textValue();
        var receipt = json.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId); receipt.put("decision", decision);
        receipt.set("role", role); receipt.set("revoked_grants", json.tree(grants)); receipt.set("terminated_invitations", json.tree(invitations)); receipt.set("current_context", current);
        JsonNode response = schema.envelope("Receipt", receipt, requestId, role.required("revision").textValue());
        var values = values(admission); values.put("operationId", operationId); values.put("operation", operation); values.put("key", key); values.put("sha256", sha);
        values.put("response", json.canonical(response)); values.put("versionId", version); values.put("httpStatus", decision.equals("PUBLISH") ? 201 : 200);
        requireOne(roles.saveOperation(values));
        var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operationId); audit.put("operator_id", actor);
        audit.set("request", request); audit.set("before", before); audit.set("after", receipt);
        audit.put("authority_grant_id", witness.grantId()); audit.put("authority_revision", witness.revision().toString());
        String action = decision.equals("PUBLISH") ? "role.published" : "role.revoked";
        requireOne(permissions.saveAudit(actor, null, UUID.randomUUID().toString(), action, json.canonical(audit), requestId, schema.manifestSha256(), policy.classification()));
        requireOne(permissions.saveEvent(actor, UUID.randomUUID().toString(), version, role.required("revision").textValue(), action, json.canonical(receipt)));
        var stored = one(roles.currentOperation(actor, operation, key), ErrorCode.CONTENT_MISMATCH);
        if (!sha.equals(stored.requestSha256()) || !operationId.equals(stored.operationId())
                || !schema.readReceipt(stored.responseRef()).equals(response)) throw new IllegalStateException("ROLE_OPERATION_READ_MISMATCH");
        return response;
    }

    private JsonNode item(EnterpriseAccess.Admission admission, String roleId, String versionId) throws IOException {
        var row = one(invitations.role(versionId), ErrorCode.OBJECT_UNAVAILABLE);
        var definition = invitationSchema.role(row.definition());
        var version = one(permissions.basis(versionId), ErrorCode.CONTENT_MISMATCH);
        var publication = one(roles.publication(versionId), ErrorCode.CONTENT_MISMATCH);
        var head = one(roles.head(roleId), ErrorCode.CONTENT_MISMATCH);
        if (!roleId.equals(row.roleId()) || !roleId.equals(definition.role_id()) || version.ownerProjectId() != null
                || !row.schemaVersion().equals("1.0.0") || !row.manifest().equals(invitationSchema.manifestSha256())
                || !publication.schemaVersion().equals("1.0.0") || !publication.manifest().equals(schema.manifestSha256())
                || !version.sha256().equals(DigestUtils.sha256Hex(json.canonicalObject(definition)))) denied(ErrorCode.CONTENT_MISMATCH);
        var headRole = one(invitations.role(head.currentVersion()), ErrorCode.CONTENT_MISMATCH);
        if (!headRole.roleId().equals(roleId)) throw new IllegalStateException("ROLE_HEAD_INVALID");
        var approvedBasis = new PermissionProtocol.Basis(publication.approvalBasis(), publication.approvalBasisSha256()); basis(approvedBasis);
        var value = json.object(); value.put("schema_version", "1.0.0"); value.put("tenant_id", admission.context().tenant_id());
        value.put("role_id", roleId); value.put("version_id", versionId); value.put("name", publication.name()); value.set("definition", json.tree(definition));
        value.put("status", row.status()); value.put("revision", row.revision().toString()); value.put("content_sha256", version.sha256());
        value.put("approved_by", row.approvedBy()); value.put("approved_at", row.approvedAt().toInstant(ZoneOffset.UTC).toString());
        value.put("valid_from", row.validFrom().toInstant(ZoneOffset.UTC).toString()); value.set("valid_to", json.tree(row.validTo() == null ? null : row.validTo().toInstant(ZoneOffset.UTC).toString()));
        value.set("approval_basis", json.tree(approvedBasis)); value.put("reason", publication.reason());
        value.put("authority_grant", publication.authorityGrant()); value.put("authority_revision", publication.authorityRevision().toString());
        var current = json.object(); current.put("current_version", head.currentVersion()); current.put("revision", head.revision().toString()); value.set("head", current);
        schema.verify("Role", value); return value;
    }

    private PermissionProtocol.GrantRow management(EnterpriseAccess.Admission admission, String roleId,
            InvitationProtocol.RoleDefinition definition, LocalDateTime from, LocalDateTime to) throws IOException {
        return authority.requireRole(admission.context().tenant_id(), mode(admission), roles.managers(actor(admission)), roleId,
                definition, from, to, Objects.requireNonNull(permissions.databaseTime()));
    }
    private void basis(PermissionProtocol.Basis basis) {
        var value = one(permissions.basis(basis.version_id()), ErrorCode.CONTENT_MISMATCH);
        if (value.ownerProjectId() != null || !value.sha256().equals(basis.content_sha256())) denied(ErrorCode.CONTENT_MISMATCH);
    }
    private Map<String,Object> values(EnterpriseAccess.Admission admission) {
        var values = new HashMap<String,Object>(); values.put("userId", actor(admission)); values.put("classification", policy.classification()); values.put("manifest", schema.manifestSha256()); return values;
    }
    private static String actor(EnterpriseAccess.Admission admission) { return admission.subject().user().user_id(); }
    private static String mode(EnterpriseAccess.Admission admission) { return admission.subject().binding().view().mode(); }
    private static void current(EnterpriseAccess.Admission admission, String header) { if (!header.equals(admission.context().context_revision())) denied(ErrorCode.CONTEXT_CHANGED); }
    private static <T> T checked(ThrowingSupplier<T> work) {
        return work.get((message, failure) -> failure instanceof com.fasterxml.jackson.core.JsonProcessingException
                ? new SchemaRejection(ErrorCode.SCHEMA_INVALID) : new IllegalStateException(message, failure));
    }
    private static <T> T one(List<T> rows, ErrorCode code) { if (rows.size() != 1) denied(code); return Objects.requireNonNull(rows.get(0)); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    private static void requireOne(int rows) { if (rows != 1) denied(ErrorCode.REVISION_CONFLICT); }
}
