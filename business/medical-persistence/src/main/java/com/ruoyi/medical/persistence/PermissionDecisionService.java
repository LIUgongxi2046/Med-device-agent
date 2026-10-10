package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityBindingSchema;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class PermissionDecisionService {
    private final EnterpriseAccess access;
    private final PermissionMapper mapper;
    private final PermissionSchema schema;
    private final PermissionAuthority authority;
    private final IdentityBindingSchema bindings;
    private final IdentityDirectoryPolicy policy;
    private final JdbcTemplate jdbc;

    public PermissionDecisionService(EnterpriseAccess access, PermissionMapper mapper, PermissionSchema schema,
            PermissionAuthority authority, IdentityBindingSchema bindings, IdentityDirectoryPolicy policy,
            @Qualifier("masterDataSource") DataSource master) {
        this.access = access; this.mapper = mapper; this.schema = schema; this.authority = authority;
        this.bindings = bindings; this.policy = policy; jdbc = new JdbcTemplate(master);
    }

    public JsonNode decide(ApplicationSessionIdentity.Verified subject, byte[] body, String contextHeader, String key) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> execute(admission, body, contextHeader, key))
                .get((message, failure) -> failure instanceof com.fasterxml.jackson.core.JsonProcessingException
                        ? new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID)
                        : new java.io.UncheckedIOException((IOException) failure)));
    }

    private JsonNode execute(EnterpriseAccess.Admission admission, byte[] body, String contextHeader, String key) throws IOException {
        var request = schema.request(body); var scope = request.scope();
        if (!scope.tenant_id().equals(admission.context().tenant_id())) denied(ErrorCode.ACCESS_DENIED);
        String actor = admission.subject().user().user_id();
        String sha256 = schema.requestSha256(request, contextHeader, admission.subject().sessionId());
        var delegation = authority.require(request, admission.subject().binding().view().mode(), mapper.currentDelegations(actor));
        var target = one(mapper.targetMember(request.subject_id()), ErrorCode.OBJECT_UNAVAILABLE);
        var binding = one(mapper.targetBinding(request.subject_id()), ErrorCode.ACCESS_DENIED);
        if (!scope.tenant_id().equals(target.tenantId()) || !request.subject_id().equals(target.userId())
                || !target.userId().equals(binding.userId())) denied(ErrorCode.ACCESS_DENIED);
        boolean platformActive = verifyPlatformBinding(binding);
        verifyResources(request, target, false);
        var prior = mapper.currentOperation(actor, key);
        if (prior.size() > 1) throw new IllegalStateException("PERMISSION_OPERATION_DUPLICATE");
        if (!prior.isEmpty()) {
            var saved = prior.get(0);
            if (!sha256.equals(saved.requestSha256())) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
            if (!"COMPLETED".equals(saved.state()) || !Integer.valueOf(200).equals(saved.httpStatus())
                    || !request.subject_id().equals(saved.targetId()) || !"1.0.0".equals(saved.schemaVersion())
                    || !schema.manifestSha256().equals(saved.schemaManifestSha256())) throw new IllegalStateException("PERMISSION_OPERATION_INVALID");
            JsonNode result = schema.readEnvelope(saved.responseRef()); JsonNode receipt = result.required("data");
            if (!saved.operationId().equals(receipt.required("operation_id").textValue())
                    || !request.decision().equals(receipt.required("decision").textValue())
                    || !request.subject_id().equals(receipt.required("subject_id").textValue())
                    || !schema.tree(scope).equals(receipt.required("scope"))) throw new IllegalStateException("PERMISSION_OPERATION_INVALID");
            JsonNode previousTarget = receipt.required(request.decision().equals("DISABLE_MEMBER") ? "member" : "grant");
            if (request.decision().equals("DISABLE_MEMBER") && !target.memberId().equals(previousTarget.required("id").textValue())
                    || request.decision().equals("REVOKE") && !request.grant_id().equals(previousTarget.required("id").textValue())) {
                throw new IllegalStateException("PERMISSION_OPERATION_INVALID");
            }
            return result;
        }
        if (!contextHeader.equals(admission.context().context_revision())) denied(ErrorCode.CONTEXT_CHANGED);
        verifyResources(request, target, true);
        LocalDateTime now = Objects.requireNonNull(mapper.databaseTime());
        String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
        String targetId, targetRevision, targetStatus;
        int revoked = 0, terminated = 0;
        JsonNode before, savedGrant = schema.tree(null);
        switch (request.decision()) {
            case "GRANT" -> {
                if (!platformActive || !"ACTIVE".equals(target.status()) || target.validFrom().isAfter(now)
                        || target.validTo() != null && !target.validTo().isAfter(now)
                        || !request.condition().identity_modes().contains(binding.mode())) denied(ErrorCode.ACCESS_DENIED);
                LocalDateTime from = request.valid_from() == null ? now : PermissionAuthority.time(request.valid_from());
                LocalDateTime to = request.valid_to() == null ? null : PermissionAuthority.time(request.valid_to());
                authority.requireValidity(delegation, from, to, now);
                targetId = UUID.randomUUID().toString(); targetRevision = "1"; targetStatus = "ACTIVE"; before = schema.tree(null);
                var grant = new PermissionProtocol.GrantRow(targetId, request.subject_id(), scope.project_id(), scope.resource_kind(),
                        scope.resource_id(), request.action(), schema.canonicalObject(request.condition()), targetStatus, from, to,
                        request.basis().version_id(), "1.0.0", schema.manifestSha256(), BigInteger.ONE);
                requireOne(mapper.insertGrant(grant, actor, policy.classification()));
                savedGrant = grantMetadata(grant);
            }
            case "REVOKE" -> {
                var grant = one(mapper.currentGrant(request.grant_id()), ErrorCode.OBJECT_UNAVAILABLE);
                if (!request.subject_id().equals(grant.subjectId()) || !Objects.equals(scope.project_id(), grant.projectId())
                        || !scope.resource_kind().equals(grant.resourceKind()) || !Objects.equals(scope.resource_id(), grant.resourceId())
                        || !request.action().equals(grant.action())) denied(ErrorCode.ACCESS_DENIED);
                if (!"ACTIVE".equals(grant.status()) || !request.expected_revision().equals(grant.revision().toString())) denied(ErrorCode.REVISION_CONFLICT);
                targetId = grant.grantId(); targetRevision = PermissionSchema.next(grant.revision().toString()); targetStatus = "REVOKED";
                before = grantMetadata(grant);
                requireOne(mapper.revokeGrant(targetId, request.subject_id(), request.expected_revision())); revoked = 1;
            }
            case "DISABLE_MEMBER" -> {
                if (!List.of("ACTIVE", "INVITED").contains(target.status()) || !request.expected_revision().equals(target.revision().toString())) denied(ErrorCode.REVISION_CONFLICT);
                targetId = target.memberId(); targetRevision = PermissionSchema.next(target.revision().toString()); targetStatus = "DISABLED";
                before = memberMetadata(target);
                requireOne(mapper.disableMember(targetId, request.subject_id(), request.expected_revision()));
                revoked = mapper.revokeMemberGrants(request.subject_id());
                terminated = mapper.terminateInvitations(request.subject_id(), request.reason());
            }
            default -> throw new IllegalStateException("PERMISSION_DECISION_INVALID");
        }
        String nextEpoch = PermissionSchema.next(admission.watermarks().authEpoch().toString());
        String nextContext = PermissionSchema.next(admission.context().context_revision());
        requireOne(mapper.advanceAuthEpoch(admission.watermarks().authEpoch().toString()));
        requireOne(jdbc.update("""
                UPDATE platform_session_context SET context_revision=?,auth_epoch=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=? AND context_revision=? AND auth_epoch=? AND governance_epoch=?
                """, nextContext, nextEpoch, admission.contextId(), admission.subject().sessionId(), actor, scope.tenant_id(),
                admission.context().context_revision(), admission.context().auth_epoch(), admission.context().governance_epoch()));
        var targetReceipt = schema.object(); targetReceipt.put("id", targetId); targetReceipt.put("status", targetStatus); targetReceipt.put("revision", targetRevision);
        var current = schema.object(); current.put("tenant_id", scope.tenant_id()); current.put("context_revision", nextContext);
        current.put("auth_epoch", nextEpoch); current.put("governance_epoch", admission.context().governance_epoch());
        var receipt = schema.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
        receipt.put("decision", request.decision()); receipt.put("subject_id", request.subject_id()); receipt.set("scope", schema.tree(scope));
        boolean disable = request.decision().equals("DISABLE_MEMBER");
        receipt.set("grant", disable ? schema.tree(null) : targetReceipt); receipt.set("member", disable ? targetReceipt : schema.tree(null));
        receipt.put("revoked_grants", Integer.toString(revoked)); receipt.put("terminated_invitations", Integer.toString(terminated)); receipt.set("current_context", current);
        JsonNode result = schema.envelope(receipt, requestId, targetRevision);
        requireOne(mapper.saveOperation(actor, operationId, key, sha256, schema.canonical(result), request.subject_id(), schema.manifestSha256(), policy.classification()));
        var audit = schema.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operationId);
        audit.put("decision", request.decision()); audit.put("operator_id", actor); audit.put("subject_id", request.subject_id());
        audit.set("scope", schema.tree(scope)); audit.set("basis", schema.tree(request.basis())); audit.put("reason", request.reason());
        audit.set("request", schema.tree(request)); audit.set("created_grant", savedGrant);
        audit.set("before", before); audit.set("after", receipt); audit.put("previous_auth_epoch", admission.context().auth_epoch());
        audit.put("auth_epoch", nextEpoch); audit.put("authority_grant_id", delegation.grantId()); audit.put("authority_revision", delegation.revision().toString());
        requireOne(mapper.saveAudit(actor, scope.project_id(), UUID.randomUUID().toString(), disable ? "member.disable" : "permission." + request.decision().toLowerCase(java.util.Locale.ROOT),
                schema.canonical(audit), requestId, schema.manifestSha256(), policy.classification()));
        var event = schema.object(); event.put("schema_version", "1.0.0"); event.put("operation_id", operationId);
        event.put("operator_id", actor); event.set("decision", receipt);
        requireOne(mapper.saveEvent(actor, UUID.randomUUID().toString(), targetId, targetRevision,
                disable ? "member.disabled" : "permission.changed", schema.canonical(event)));
        return result;
    }

    private void verifyResources(PermissionProtocol.Request request, TenantMemberRow target, boolean fresh) {
        var scope = request.scope();
        if (scope.project_id() != null) {
            String status = one(mapper.projectStatus(scope.project_id()), ErrorCode.OBJECT_UNAVAILABLE);
            if (fresh && request.decision().equals("GRANT") && !List.of("PENDING", "ACTIVE").contains(status)) denied(ErrorCode.ACCESS_DENIED);
        }
        if (scope.resource_id() != null) {
            var owner = one(mapper.resourceOwner(scope.resource_kind(), scope.resource_id()), ErrorCode.OBJECT_UNAVAILABLE);
            if (!scope.resource_id().equals(owner.id()) || !Objects.equals(scope.project_id(), owner.projectId())) denied(ErrorCode.ACCESS_DENIED);
            if (request.decision().equals("DISABLE_MEMBER") && !target.memberId().equals(owner.id())) denied(ErrorCode.ACCESS_DENIED);
        }
        var basis = one(mapper.basis(request.basis().version_id()), ErrorCode.OBJECT_UNAVAILABLE);
        if (!request.basis().content_sha256().equals(basis.sha256())
                || basis.ownerProjectId() != null && !basis.ownerProjectId().equals(scope.project_id())) denied(ErrorCode.CONTENT_MISMATCH);
    }

    boolean verifyPlatformBinding(PermissionProtocol.BindingRow binding) throws IOException {
        if (!"ACTIVE".equals(binding.status())) denied(ErrorCode.ACCESS_DENIED);
        var view = schema.object(); view.put("mode", binding.mode()); view.put("user_id", binding.userId());
        view.set("issuer", schema.tree(binding.issuer())); view.set("subject", schema.tree(binding.subject()));
        bindings.readBinding(schema.canonical(view).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (binding.mode().equals("local")) {
            if (!binding.userId().equals(binding.localUserId())) denied(ErrorCode.ACCESS_DENIED);
            String status = one(jdbc.query("SELECT status FROM platform_local_account WHERE user_id=? FOR UPDATE",
                    (row, ordinal) -> row.getString(1), binding.localUserId()), ErrorCode.ACCESS_DENIED);
            if (!List.of("ACTIVE", "DISABLED").contains(status)) denied(ErrorCode.ACCESS_DENIED);
            return status.equals("ACTIVE");
        }
        if (binding.localUserId() != null) denied(ErrorCode.ACCESS_DENIED);
        String status = one(jdbc.query("""
                SELECT status FROM platform_identity WHERE user_id=? AND issuer=? AND subject=? FOR UPDATE
                """, (row, ordinal) -> row.getString(1), binding.userId(), binding.issuer(), binding.subject()), ErrorCode.ACCESS_DENIED);
        if (!List.of("ACTIVE", "DISABLED").contains(status)) denied(ErrorCode.ACCESS_DENIED);
        return status.equals("ACTIVE");
    }

    private JsonNode grantMetadata(PermissionProtocol.GrantRow row) throws IOException {
        var node = schema.object(); node.put("grant_id", row.grantId()); node.put("subject_id", row.subjectId());
        node.put("status", row.status()); node.put("revision", row.revision().toString()); node.put("basis_version", row.basisVersion());
        node.set("project_id", schema.tree(row.projectId())); node.put("resource_kind", row.resourceKind());
        node.set("resource_id", schema.tree(row.resourceId())); node.put("action", row.action());
        node.put("valid_from", row.validFrom().toInstant(java.time.ZoneOffset.UTC).toString());
        node.set("valid_to", schema.tree(row.validTo() == null ? null : row.validTo().toInstant(java.time.ZoneOffset.UTC).toString()));
        node.set("condition", schema.tree(schema.condition(row.condition()))); node.put("schema_version", row.schemaVersion());
        node.put("schema_manifest_sha256", row.manifest()); return node;
    }
    private JsonNode memberMetadata(TenantMemberRow row) {
        var node = schema.object(); node.put("member_id", row.memberId()); node.put("user_id", row.userId()); node.put("status", row.status());
        node.put("revision", row.revision().toString()); return node;
    }
    private static <T> T one(List<T> rows, ErrorCode code) { if (rows.size() != 1) denied(code); return Objects.requireNonNull(rows.get(0)); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    private static void requireOne(int rows) { if (rows != 1) denied(ErrorCode.REVISION_CONFLICT); }
}
