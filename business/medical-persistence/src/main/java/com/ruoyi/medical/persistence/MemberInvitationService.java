package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.identity.InvitationCredential;
import com.ruoyi.medical.persistence.mapper.InvitationMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
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
public final class MemberInvitationService {
    private final EnterpriseAccess enterprise;
    private final InvitationAccess acceptance;
    private final InvitationMapper invitations;
    private final PermissionMapper permissions;
    private final InvitationSchema schema;
    private final PermissionSchema json;
    private final PermissionAuthority authority;
    private final PermissionDecisionService platformBindings;
    private final InvitationResponseCipher cipher;
    private final IdentityDirectoryPolicy policy;
    private final JdbcTemplate jdbc;
    public MemberInvitationService(EnterpriseAccess enterprise, InvitationAccess acceptance, InvitationMapper invitations,
            PermissionMapper permissions, InvitationSchema schema, PermissionSchema json, PermissionAuthority authority,
            PermissionDecisionService platformBindings, InvitationResponseCipher cipher, IdentityDirectoryPolicy policy,
            @Qualifier("masterDataSource") DataSource master) {
        this.enterprise = enterprise; this.acceptance = acceptance; this.invitations = invitations; this.permissions = permissions;
        this.schema = schema; this.json = json; this.authority = authority; this.platformBindings = platformBindings;
        this.cipher = cipher; this.policy = policy; jdbc = new JdbcTemplate(master);
    }

    public JsonNode create(ApplicationSessionIdentity.Verified subject, byte[] body, String context, String key) {
        return enterprise.execute(subject, admission -> checked(() -> create(admission, body, context, key)));
    }
    public JsonNode accept(ApplicationSessionIdentity.Verified subject, String id, byte[] body, String key) {
        var result = acceptance.execute(subject, id, admission -> checked(() -> accept(admission, id, body, key)));
        if (result.rejection() != null) denied(result.rejection());
        return result.response();
    }

    private JsonNode create(EnterpriseAccess.Admission admission, byte[] body, String context, String key) throws Exception {
        var request = schema.create(body); var scope = request.scope();
        String actor = admission.subject().user().user_id(), target = request.identity_binding().user_id();
        if (!scope.tenant_id().equals(admission.context().tenant_id())) denied(ErrorCode.ACCESS_DENIED);
        LocalDateTime now = Objects.requireNonNull(permissions.databaseTime()), expires = PermissionAuthority.time(request.expires_at());
        if (!expires.isAfter(now)) denied(ErrorCode.ACCESS_DENIED);
        verifyScope(scope, request.basis());
        var authorizations = authorize(request.roles(), scope, request.basis(), target,
                admission.subject().binding().view().mode(), permissions.currentDelegations(actor), now, expires);
        for (var item : authorizations) if (!item.condition().identity_modes().contains(request.identity_binding().mode())) denied(ErrorCode.ACCESS_DENIED);
        String sha = DigestUtils.sha256Hex(json.canonical(requestMetadata("/api/member-invitations", json.tree(request), context, admission.subject().sessionId())));
        var saved = invitations.currentOperation(actor, "ID-07", key);
        if (!saved.isEmpty()) {
            var operation = one(saved, ErrorCode.IDEMPOTENCY_CONFLICT);
            verifyOperation(operation, sha, "ID-07", null);
            var invite = one(invitations.currentInvitation(operation.targetId()), ErrorCode.OBJECT_UNAVAILABLE);
            if (!target.equals(invite.userId()) || !actor.equals(invite.createdBy()) || !"PENDING".equals(invite.status())
                    || !invite.expiresAt().isAfter(now)) denied(ErrorCode.ACCESS_DENIED);
            verifyInvitation(invite);
            var targetBinding = one(permissions.targetBinding(target), ErrorCode.ACCESS_DENIED);
            if (!request.identity_binding().equals(view(targetBinding)) || !platformBindings.verifyPlatformBinding(targetBinding)) denied(ErrorCode.ACCESS_DENIED);
            verifyMember(one(permissions.targetMember(target), ErrorCode.ACCESS_DENIED), target, now, "INVITED");
            var original = schema.authorizations(invite.authorizations());
            var replay = authorize(request.roles(), scope, request.basis(), target, admission.subject().binding().view().mode(),
                    originalAuthorities(original, permissions.currentDelegations(actor)), now, invite.expiresAt());
            if (!original.equals(replay)) denied(ErrorCode.SCOPE_STALE);
            JsonNode decoded = cipher.decrypt(operation.responseRef(), aad(scope.tenant_id(), actor, operation.operationId(), sha));
            JsonNode result = schema.readEnvelope(json.canonical(decoded), true);
            var receipt = result.required("data");
            if (!operation.operationId().equals(receipt.required("operation_id").textValue())
                    || !invite.invitationId().equals(receipt.required("invitation_id").textValue())
                    || !target.equals(receipt.required("user_id").textValue())
                    || !InvitationCredential.matches(receipt.required("credential").textValue(), invite.credentialSha256())) {
                throw new IllegalStateException("INVITATION_OPERATION_INVALID");
            }
            return result;
        }
        if (!context.equals(admission.context().context_revision())) denied(ErrorCode.CONTEXT_CHANGED);
        // 当前发布版本只约束新邀请，原邀请持续核查其原批准版本。
        for (var reference : request.roles()) {
            var head = one(invitations.roleHead(reference.role_id()), ErrorCode.SCOPE_STALE);
            if (!head.currentVersion().equals(reference.version_id())) denied(ErrorCode.SCOPE_STALE);
        }
        var existingMembers = permissions.targetMember(target); var existingBindings = permissions.targetBinding(target);
        String identityId, memberId;
        if (existingMembers.isEmpty() && existingBindings.isEmpty()) {
            identityId = UUID.randomUUID().toString(); memberId = UUID.randomUUID().toString();
            var binding = new PermissionProtocol.BindingRow(target, identityId, request.identity_binding().mode(),
                    request.identity_binding().issuer(), request.identity_binding().subject(),
                    request.identity_binding().mode().equals("local") ? target : null, "ACTIVE");
            if (!platformBindings.verifyPlatformBinding(binding)) denied(ErrorCode.ACCESS_DENIED);
            var values = values(actor); values.put("targetId", target); values.put("identityId", identityId); values.put("memberId", memberId);
            values.put("mode", binding.mode()); values.put("localUserId", binding.localUserId()); values.put("issuer", binding.issuer()); values.put("subject", binding.subject());
            requireOne(invitations.insertBinding(values)); requireOne(invitations.insertMember(values));
        } else {
            var member = one(existingMembers, ErrorCode.REVISION_CONFLICT);
            var binding = one(existingBindings, ErrorCode.REVISION_CONFLICT);
            if (!"INVITED".equals(member.status())) denied(ErrorCode.REVISION_CONFLICT);
            verifyMember(member, target, now, "INVITED");
            if (!request.identity_binding().equals(view(binding)) || !platformBindings.verifyPlatformBinding(binding)) denied(ErrorCode.ACCESS_DENIED);
            identityId = binding.identityId(); memberId = member.memberId();
        }
        for (var pending : invitations.pending(target)) {
            if (pending.expiresAt().isAfter(now)) denied(ErrorCode.REVISION_CONFLICT);
            PermissionSchema.next(pending.revision().toString()); requireOne(invitations.expire(pending.invitationId(), pending.revision().toString()));
            publish(actor, pending.invitationId(), scope, "invitation.expired", PermissionSchema.next(pending.revision().toString()),
                    json.tree(Map.of("invitation_id", pending.invitationId(), "status", "EXPIRED", "reason", "邀请有效期结束")), UUID.randomUUID().toString());
        }
        String id = UUID.randomUUID().toString(), operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
        InvitationCredential credential = InvitationCredential.issue();
        var values = values(actor); values.put("invitationId", id); values.put("targetId", target); values.put("identityId", identityId);
        values.put("roles", json.canonicalObject(request.roles())); values.put("scope", json.canonicalObject(scope));
        values.put("authorizations", json.canonicalObject(authorizations)); values.put("basis", request.basis().version_id());
        values.put("credentialSha256", credential.sha256()); values.put("expiresAt", expires);
        requireOne(invitations.insertInvitation(values));
        requireOne(jdbc.update("""
                INSERT INTO platform_invitation_route (user_id,invitation_id,tenant_id,created_by,created_at,classification,revision,updated_at)
                VALUES (?,?,?,?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6))
                """, target, id, scope.tenant_id(), actor, policy.classification()));
        String nextEpoch = advance(admission.watermarks());
        refreshContext(admission, nextEpoch);
        var receipt = json.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
        receipt.put("invitation_id", id); receipt.put("user_id", target); receipt.put("status", "PENDING"); receipt.put("revision", "1");
        receipt.put("expires_at", expires.toInstant(ZoneOffset.UTC).toString()); receipt.put("credential", credential.secret()); receipt.put("auth_epoch", nextEpoch);
        JsonNode response = schema.envelope(receipt, requestId, true);
        saveOperation(actor, "ID-07", operationId, key, sha, id, cipher.encrypt(response, aad(scope.tenant_id(), actor, operationId, sha)), 201);
        var publicReceipt = receipt.deepCopy(); publicReceipt.remove("credential"); publicReceipt.put("member_id", memberId);
        publicReceipt.set("request", json.tree(request)); publicReceipt.set("authorization_manifest", json.tree(authorizations));
        publish(actor, id, scope, "invitation.created", "1", publicReceipt, requestId);
        return response;
    }

    private InvitationProtocol.Outcome accept(InvitationAccess.Admission admission, String id, byte[] body, String key) throws Exception {
        String actor = admission.subject().user().user_id();
        var invite = one(invitations.currentInvitation(id), ErrorCode.ACCESS_DENIED);
        if (!actor.equals(invite.userId())) denied(ErrorCode.ACCESS_DENIED);
        verifyInvitation(invite);
        var binding = one(permissions.targetBinding(actor), ErrorCode.ACCESS_DENIED);
        if (!invite.identityId().equals(binding.identityId()) || !admission.subject().binding().matches(view(binding))
                || !platformBindings.verifyPlatformBinding(binding)) denied(ErrorCode.ACCESS_DENIED);
        var request = schema.accept(body);
        if (!InvitationCredential.matches(request.credential(), invite.credentialSha256())) denied(ErrorCode.ACCESS_DENIED);
        LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
        var scope = json.scope(schema.read(invite.scope().getBytes(StandardCharsets.UTF_8)));
        if (!admission.tenantId().equals(scope.tenant_id())) denied(ErrorCode.ACCESS_DENIED);
        if (!invite.expiresAt().isAfter(now)) {
            if (invite.status().equals("PENDING")) {
                String revision = PermissionSchema.next(invite.revision().toString());
                requireOne(invitations.expire(id, invite.revision().toString())); String epoch = advance(admission.watermarks());
                publish(actor, id, scope, "invitation.expired", revision,
                        json.tree(Map.of("invitation_id", id, "status", "EXPIRED", "revision", revision, "auth_epoch", epoch, "reason", "邀请有效期结束")), UUID.randomUUID().toString());
            }
            return new InvitationProtocol.Outcome(null, ErrorCode.ACCESS_DENIED);
        }
        if (!List.of("PENDING", "ACCEPTED").contains(invite.status())) denied(ErrorCode.ACCESS_DENIED);
        var inviter = one(permissions.targetMember(invite.createdBy()), ErrorCode.ACCESS_DENIED);
        verifyMember(inviter, invite.createdBy(), now, "ACTIVE");
        var inviterBinding = one(permissions.targetBinding(invite.createdBy()), ErrorCode.ACCESS_DENIED);
        if (!platformBindings.verifyPlatformBinding(inviterBinding)) denied(ErrorCode.ACCESS_DENIED);
        var basis = one(permissions.basis(invite.basisVersion()), ErrorCode.CONTENT_MISMATCH);
        var savedBasis = new PermissionProtocol.Basis(basis.id(), basis.sha256());
        verifyScope(scope, savedBasis);
        var original = schema.authorizations(invite.authorizations());
        var authorizations = authorize(schema.roles(invite.roles()), scope, savedBasis, actor, inviterBinding.mode(),
                originalAuthorities(original, invitations.inviterDelegations(invite.createdBy())), now, invite.expiresAt());
        if (!original.equals(authorizations)) denied(ErrorCode.SCOPE_STALE);
        for (var item : authorizations) if (!item.condition().identity_modes().contains(binding.mode())) denied(ErrorCode.ACCESS_DENIED);
        String sha = cipher.requestHmac(requestMetadata("/api/member-invitations/" + id + "/acceptances", json.tree(request), null, null));
        var prior = invitations.currentOperation(actor, "ID-08", key);
        var member = one(permissions.targetMember(actor), ErrorCode.ACCESS_DENIED);
        if (!prior.isEmpty()) {
            var operation = one(prior, ErrorCode.IDEMPOTENCY_CONFLICT); verifyOperation(operation, sha, "ID-08", id);
            if (!invite.status().equals("ACCEPTED")) denied(ErrorCode.CONTEXT_CHANGED);
            verifyMember(member, actor, now, "ACTIVE");
            JsonNode response = schema.readEnvelope(operation.responseRef(), false); var receipt = response.required("data");
            if (!id.equals(receipt.required("invitation_id").textValue()) || !actor.equals(receipt.required("user_id").textValue())
                    || !operation.operationId().equals(receipt.required("operation_id").textValue())
                    || !member.memberId().equals(receipt.required("member_id").textValue())) throw new IllegalStateException("INVITATION_OPERATION_INVALID");
            List<String> ids = new ArrayList<>(); for (JsonNode grant : receipt.required("grants")) ids.add(grant.textValue());
            verifyGrantOrigins(invite, scope, authorizations, ids, actor, now);
            return new InvitationProtocol.Outcome(response, null);
        }
        if (!invite.status().equals("PENDING") || !request.expected_revision().equals(invite.revision().toString())) denied(ErrorCode.REVISION_CONFLICT);
        verifyMember(member, actor, now, "INVITED");
        String nextRevision = PermissionSchema.next(invite.revision().toString()), nextMember = PermissionSchema.next(member.revision().toString());
        requireOne(invitations.acceptInvitation(id, actor, request.expected_revision()));
        requireOne(invitations.activateMember(member.memberId(), actor, member.revision().toString()));
        requireOne(jdbc.update("""
                INSERT INTO platform_member_route (user_id,tenant_id,member_id,created_by,created_at,classification,revision,updated_at)
                VALUES (?,?,?,?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6))
                """, actor, admission.tenantId(), member.memberId(), actor, policy.classification()));
        List<String> grantIds = new ArrayList<>();
        for (var item : authorizations) {
            if (item.action() == null) continue;
            String grantId = UUID.randomUUID().toString();
            var grant = new PermissionProtocol.GrantRow(grantId, actor, scope.project_id(), scope.resource_kind(), scope.resource_id(),
                    item.action(), json.canonicalObject(item.condition()), "ACTIVE", now,
                    item.valid_to() == null ? null : PermissionAuthority.time(item.valid_to()), invite.basisVersion(), "1.0.0", json.manifestSha256(), BigInteger.ONE);
            requireOne(permissions.insertGrant(grant, actor, policy.classification()));
            var origin = values(actor); origin.put("grantId", grantId); origin.put("roleVersion", item.role_version()); origin.put("invitationId", id);
            requireOne(invitations.insertOrigin(origin)); grantIds.add(grantId);
        }
        verifyGrantOrigins(invite, scope, authorizations, grantIds, actor, now);
        String epoch = advance(admission.watermarks()), operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
        var receipt = json.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
        receipt.put("invitation_id", id); receipt.put("user_id", actor); receipt.put("status", "ACCEPTED"); receipt.put("revision", nextRevision);
        receipt.put("member_id", member.memberId()); receipt.put("member_revision", nextMember); receipt.set("grants", json.tree(grantIds)); receipt.put("auth_epoch", epoch);
        JsonNode response = schema.envelope(receipt, requestId, false);
        saveOperation(actor, "ID-08", operationId, key, sha, id, json.canonical(response), 200);
        var audit = receipt.deepCopy(); audit.set("roles", json.tree(schema.roles(invite.roles()))); audit.set("scope", json.tree(scope));
        audit.set("basis", json.tree(savedBasis)); audit.set("authorization_manifest", json.tree(authorizations)); audit.put("inviter_id", invite.createdBy());
        publish(actor, id, scope, "invitation.accepted", nextRevision, audit, requestId);
        return new InvitationProtocol.Outcome(response, null);
    }

    private List<InvitationProtocol.Authorization> authorize(List<InvitationProtocol.RoleReference> references, PermissionProtocol.Scope scope,
            PermissionProtocol.Basis basis, String target, String identityMode, List<PermissionProtocol.GrantRow> grants,
            LocalDateTime now, LocalDateTime expires) throws IOException {
        var roles = references.stream().map(InvitationProtocol.RoleReference::role_id).toList();
        List<InvitationProtocol.Authorization> result = new ArrayList<>();
        for (var reference : references) {
            var role = one(invitations.role(reference.version_id()), ErrorCode.OBJECT_UNAVAILABLE);
            var version = one(permissions.basis(reference.version_id()), ErrorCode.CONTENT_MISMATCH);
            var definition = schema.role(role.definition());
            if (!reference.role_id().equals(role.roleId()) || !reference.role_id().equals(definition.role_id())
                    || !"ACTIVE".equals(role.status()) || !"1.0.0".equals(role.schemaVersion())
                    || !schema.manifestSha256().equals(role.manifest()) || role.approvedAt().isAfter(now)
                    || role.validFrom().isAfter(now) || role.validTo() != null && (role.validTo().isBefore(expires) || !role.validTo().isAfter(now))
                    || !reference.content_sha256().equals(version.sha256())
                    || !version.sha256().equals(DigestUtils.sha256Hex(json.canonicalObject(definition)))
                    || version.ownerProjectId() != null && !Objects.equals(version.ownerProjectId(), scope.project_id())) denied(ErrorCode.CONTENT_MISMATCH);
            var approver = one(permissions.targetMember(role.approvedBy()), ErrorCode.ACCESS_DENIED);
            verifyMember(approver, role.approvedBy(), now, "ACTIVE");
            if (!platformBindings.verifyPlatformBinding(one(permissions.targetBinding(role.approvedBy()), ErrorCode.ACCESS_DENIED))) denied(ErrorCode.ACCESS_DENIED);
            // 空动作保存管理授权依据，接受时保持没有业务权限。
            List<String> actions = definition.actions().isEmpty() ? java.util.Collections.singletonList(null) : definition.actions();
            for (String action : actions) {
                var evaluation = new PermissionProtocol.Request("1.0.0", "GRANT", target, scope, action, null, definition.condition(),
                        null, role.validTo() == null ? null : role.validTo().toInstant(ZoneOffset.UTC).toString(), basis, "0", "成员邀请授权核查");
                var candidates = grants.stream().filter(row -> !now.isBefore(row.validFrom())
                        && (row.validTo() == null || role.validTo() != null && !role.validTo().isAfter(row.validTo())
                            && now.isBefore(row.validTo()))
                        && (row.validTo() == null || !expires.isAfter(row.validTo()))).toList();
                var permitted = authority.requireInvitation(evaluation, identityMode, candidates, roles);
                authority.requireValidity(permitted, now, role.validTo(), now);
                if (permitted.validTo() != null && permitted.validTo().isBefore(expires)) denied(ErrorCode.ACCESS_DENIED);
                result.add(new InvitationProtocol.Authorization(reference.role_id(), reference.version_id(), action, definition.condition(),
                        permitted.grantId(), permitted.revision().toString(), evaluation.valid_to()));
            }
        }
        return List.copyOf(result);
    }

    private void verifyGrantOrigins(InvitationProtocol.Row invite, PermissionProtocol.Scope scope,
            List<InvitationProtocol.Authorization> authorizations, List<String> grantIds, String actor, LocalDateTime now) throws IOException {
        var expected = authorizations.stream().filter(item -> item.action() != null).toList();
        var origins = invitations.origins(invite.invitationId());
        if (expected.size() != grantIds.size() || origins.size() != grantIds.size() || grantIds.stream().distinct().count() != grantIds.size()
                || !origins.stream().map(EnterpriseRoleProtocol.GrantOrigin::grantId).collect(java.util.stream.Collectors.toSet())
                    .equals(new java.util.HashSet<>(grantIds))) denied(ErrorCode.CONTENT_MISMATCH);
        for (int index = 0; index < grantIds.size(); index++) {
            var authorization = expected.get(index); String id = grantIds.get(index);
            var origin = one(origins.stream().filter(item -> item.grantId().equals(id)).toList(), ErrorCode.CONTENT_MISMATCH);
            var grant = one(permissions.currentGrant(id), ErrorCode.SCOPE_STALE);
            if (!origin.invitationId().equals(invite.invitationId()) || !origin.roleVersion().equals(authorization.role_version())
                    || !actor.equals(grant.subjectId()) || !grant.status().equals("ACTIVE") || !grant.revision().equals(BigInteger.ONE)
                    || !Objects.equals(scope.project_id(), grant.projectId()) || !scope.resource_kind().equals(grant.resourceKind())
                    || !Objects.equals(scope.resource_id(), grant.resourceId()) || !authorization.action().equals(grant.action())
                    || !authorization.condition().equals(json.condition(grant.condition())) || !invite.basisVersion().equals(grant.basisVersion())
                    || !"1.0.0".equals(grant.schemaVersion()) || !json.manifestSha256().equals(grant.manifest())
                    || !Objects.equals(authorization.valid_to() == null ? null : PermissionAuthority.time(authorization.valid_to()), grant.validTo())
                    || grant.validFrom().isAfter(now) || grant.validTo() != null && !grant.validTo().isAfter(now)) denied(ErrorCode.SCOPE_STALE);
        }
    }

    private void verifyScope(PermissionProtocol.Scope scope, PermissionProtocol.Basis basis) {
        if (scope.project_id() != null && !List.of("PENDING", "ACTIVE").contains(one(permissions.projectStatus(scope.project_id()), ErrorCode.OBJECT_UNAVAILABLE))) denied(ErrorCode.ACCESS_DENIED);
        if (scope.resource_id() != null) {
            var owner = one(permissions.resourceOwner(scope.resource_kind(), scope.resource_id()), ErrorCode.OBJECT_UNAVAILABLE);
            if (!scope.resource_id().equals(owner.id()) || !Objects.equals(scope.project_id(), owner.projectId())) denied(ErrorCode.ACCESS_DENIED);
        }
        var version = one(permissions.basis(basis.version_id()), ErrorCode.CONTENT_MISMATCH);
        if (!basis.content_sha256().equals(version.sha256()) || version.ownerProjectId() != null && !Objects.equals(version.ownerProjectId(), scope.project_id())) denied(ErrorCode.CONTENT_MISMATCH);
    }
    private static List<PermissionProtocol.GrantRow> originalAuthorities(List<InvitationProtocol.Authorization> manifest,
            List<PermissionProtocol.GrantRow> current) {
        var ids = manifest.stream().map(InvitationProtocol.Authorization::authority_id).collect(java.util.stream.Collectors.toSet());
        var result = current.stream().filter(row -> ids.contains(row.grantId())).toList();
        if (result.size() != ids.size()) denied(ErrorCode.SCOPE_STALE);
        for (var item : manifest) {
            var matches = result.stream().filter(row -> row.grantId().equals(item.authority_id())
                    && row.revision().toString().equals(item.authority_revision())).count();
            if (matches != 1) denied(ErrorCode.SCOPE_STALE);
        }
        return result;
    }
    private void verifyInvitation(InvitationProtocol.Row row) {
        if (!"1.0.0".equals(row.schemaVersion()) || !schema.manifestSha256().equals(row.manifest())) throw new IllegalStateException("INVITATION_SCHEMA_UNAVAILABLE");
    }
    private void verifyMember(TenantMemberRow member, String user, LocalDateTime now, String status) {
        if (!user.equals(member.userId()) || !status.equals(member.status()) || member.validFrom().isAfter(now)
                || member.validTo() != null && !member.validTo().isAfter(now)) denied(ErrorCode.ACCESS_DENIED);
    }
    private void verifyOperation(SelectionOperationRow row, String sha, String operation, String id) {
        if (!sha.equals(row.requestSha256())) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!"COMPLETED".equals(row.state()) || !Integer.valueOf(operation.equals("ID-07") ? 201 : 200).equals(row.httpStatus())
                || !"1.0.0".equals(row.schemaVersion()) || !schema.manifestSha256().equals(row.schemaManifestSha256())
                || id != null && !id.equals(row.targetId())) throw new IllegalStateException("INVITATION_OPERATION_INVALID");
    }
    private JsonNode requestMetadata(String path, JsonNode body, String context, String session) {
        var value = json.object(); value.put("method", "POST"); value.put("path", path); value.put("schema_version", "1.0.0");
        value.putNull("query"); value.set("context_header", json.tree(context)); value.set("session_id", json.tree(session)); value.set("body", body); return value;
    }
    private JsonNode aad(String tenant, String user, String operation, String sha) {
        return json.tree(Map.of("purpose", "INVITATION_RESPONSE", "tenant_id", tenant, "subject_id", user,
                "operation_id", operation, "request_sha256", sha, "schema_manifest_sha256", schema.manifestSha256()));
    }
    private String advance(TenantWatermarks current) {
        String epoch = PermissionSchema.next(current.authEpoch().toString()); requireOne(permissions.advanceAuthEpoch(current.authEpoch().toString())); return epoch;
    }
    private void refreshContext(EnterpriseAccess.Admission admission, String epoch) {
        var context = admission.context();
        requireOne(jdbc.update("""
                UPDATE platform_session_context SET auth_epoch=?,context_revision=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=? AND context_revision=? AND auth_epoch=? AND governance_epoch=?
                """, epoch, PermissionSchema.next(context.context_revision()), admission.contextId(), admission.subject().sessionId(),
                admission.subject().user().user_id(), context.tenant_id(), context.context_revision(), context.auth_epoch(), context.governance_epoch()));
    }
    private Map<String,Object> values(String actor) {
        Map<String,Object> values = new HashMap<>(); values.put("userId", actor); values.put("classification", policy.classification()); values.put("manifest", schema.manifestSha256()); return values;
    }
    private void saveOperation(String actor, String operation, String operationId, String key, String sha, String invitationId, String response, int status) {
        var values = values(actor); values.put("operation", operation); values.put("operationId", operationId); values.put("key", key); values.put("sha256", sha);
        values.put("invitationId", invitationId); values.put("response", response); values.put("httpStatus", status); requireOne(invitations.saveOperation(values));
    }
    private void publish(String actor, String id, PermissionProtocol.Scope scope, String action, String revision, JsonNode data, String requestId) throws IOException {
        var values = values(actor); values.put("invitationId", id); values.put("projectId", scope.project_id()); values.put("auditId", UUID.randomUUID().toString());
        values.put("action", action); values.put("resource", json.canonical(data)); values.put("requestId", requestId); requireOne(invitations.saveAudit(values));
        values.put("eventId", UUID.randomUUID().toString()); values.put("revision", revision); values.put("eventType", action); values.put("payload", json.canonical(data)); requireOne(invitations.saveEvent(values));
    }
    private static AuthenticatedIdentityBinding.View view(PermissionProtocol.BindingRow row) {
        return new AuthenticatedIdentityBinding.View(row.mode(), row.userId(), row.issuer(), row.subject());
    }
    private static <T> T checked(ThrowingSupplier<T> task) {
        return task.get((message, failure) -> failure instanceof com.fasterxml.jackson.core.JsonProcessingException
                ? new SchemaRejection(ErrorCode.SCHEMA_INVALID) : new IllegalStateException(message, failure));
    }
    private static <T> T one(List<T> values, ErrorCode code) { if (values.size() != 1) denied(code); return Objects.requireNonNull(values.get(0)); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    private static void requireOne(int rows) { if (rows != 1) denied(ErrorCode.REVISION_CONFLICT); }
}
