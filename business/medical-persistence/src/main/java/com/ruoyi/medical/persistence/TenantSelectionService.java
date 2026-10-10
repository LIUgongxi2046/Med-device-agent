package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.BusinessSessionSchema;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.TenantMemberMapper;
import com.ruoyi.medical.persistence.mapper.TenantSelectionMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class TenantSelectionService {
    private final JdbcTemplate jdbc;
    private final ApplicationSessionIdentity identities;
    private final BusinessSessionDirectory directory;
    private final MemberDiscovery discovery;
    private final TenantTransaction transactions;
    private final TenantMemberMapper members;
    private final TenantSelectionMapper operations;
    private final BusinessSessionSchema schema;
    private final IdentityDirectoryPolicy policy;

    public TenantSelectionService(@Qualifier("masterDataSource") DataSource master, ApplicationSessionIdentity identities,
            BusinessSessionDirectory directory, MemberDiscovery discovery, TenantTransaction transactions,
            TenantMemberMapper members, TenantSelectionMapper operations, BusinessSessionSchema schema, IdentityDirectoryPolicy policy) {
        jdbc = new JdbcTemplate(master); this.identities = identities; this.directory = directory;
        this.discovery = discovery; this.transactions = transactions; this.members = members;
        this.operations = operations; this.schema = schema; this.policy = policy;
    }

    public JsonNode select(ApplicationSessionIdentity.Verified subject, BusinessSessionProtocol.SelectionRequest request,
            String header, String key) throws IOException {
        if (!request.expected_context_revision().equals(header)) denied(ErrorCode.CONTEXT_CHANGED);
        var route = directory.route(subject, request.tenant_id());
        var discovered = discovery.read(subject, route);
        if (discovered == null || !discovered.member().status().equals("ACTIVE")) denied(ErrorCode.ACCESS_DENIED);
        String sha256 = schema.requestSha256(request, header, subject.sessionId());
        var identity = new TenantScope.Identity(request.tenant_id(), subject.user().user_id(), new BigInteger(header),
                discovered.watermarks().authEpoch(), discovered.watermarks().governanceEpoch());
        try (var scope = TenantScope.open(identity)) {
            // 标准事务回调传播检查异常；任一保存失败都会终止并撤销当前事务。
            return transactions.execute(watermarks -> ThrowingSupplier.of(() -> {
                identities.lockCurrent(subject);
                if (!route.equals(directory.route(subject, request.tenant_id()))) denied(ErrorCode.SCOPE_STALE);
                var active = members.currentMember(subject.user().user_id(), route.memberId());
                if (active.size() != 1 || !active.get(0).status().equals("ACTIVE")
                        || !subject.user().user_id().equals(active.get(0).userId())
                        || !request.tenant_id().equals(active.get(0).tenantId())) denied(ErrorCode.ACCESS_DENIED);
                directory.initializeContext(subject);
                var context = jdbc.queryForObject("""
                        SELECT context_id,tenant_id,context_revision,auth_epoch,governance_epoch
                        FROM platform_session_context WHERE session_id=? AND user_id=? FOR UPDATE
                        """, (row, ordinal) -> new LockedContext(row.getString("context_id"), row.getString("tenant_id"),
                        row.getString("context_revision"), row.getString("auth_epoch"), row.getString("governance_epoch")),
                        subject.sessionId(), subject.user().user_id());
                Objects.requireNonNull(context); WireValues.uuid(context.id());
                var previous = operations.currentOperation(subject.user().user_id(), key);
                if (previous.size() > 1) throw new IllegalStateException("SELECTION_OPERATION_DUPLICATE");
                if (!previous.isEmpty()) {
                    var saved = previous.get(0);
                    if (!sha256.equals(saved.requestSha256())) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
                    if (!"COMPLETED".equals(saved.state()) || !Integer.valueOf(200).equals(saved.httpStatus())
                            || !request.tenant_id().equals(saved.targetId()) || !"1.0.0".equals(saved.schemaVersion())
                            || !schema.manifestSha256().equals(saved.schemaManifestSha256())) {
                        throw new IllegalStateException("SELECTION_OPERATION_INVALID");
                    }
                    JsonNode result = schema.readSelectionEnvelope(saved.responseRef());
                    JsonNode receipt = result.required("data");
                    if (!saved.operationId().equals(receipt.required("operation_id").textValue())) {
                        throw new IllegalStateException("SELECTION_OPERATION_INVALID");
                    }
                    JsonNode selected = receipt.required("current_context");
                    if (!request.tenant_id().equals(selected.required("tenant_id").textValue())
                            || !request.tenant_id().equals(context.tenantId())
                            || !context.revision().equals(selected.required("context_revision").textValue())) denied(ErrorCode.CONTEXT_CHANGED);
                    if (!watermarks.authEpoch().toString().equals(context.authEpoch())
                            || !watermarks.governanceEpoch().toString().equals(context.governanceEpoch())
                            || !context.authEpoch().equals(selected.required("auth_epoch").textValue())
                            || !context.governanceEpoch().equals(selected.required("governance_epoch").textValue())) denied(ErrorCode.SCOPE_STALE);
                    return result;
                }
                if (!header.equals(context.revision())) denied(ErrorCode.CONTEXT_CHANGED);
                String revision = new BigInteger(header).add(BigInteger.ONE).toString();
                WireValues.revision(revision);
                String operationId = UUID.randomUUID().toString();
                String requestId = UUID.randomUUID().toString();
                var selected = new BusinessSessionProtocol.Context(request.tenant_id(), revision,
                        watermarks.authEpoch().toString(), watermarks.governanceEpoch().toString());
                JsonNode result = schema.selectionEnvelope(new BusinessSessionProtocol.SelectionReceipt("1.0.0", operationId, selected), requestId);
                requireOne(jdbc.update("""
                        UPDATE platform_session_context SET tenant_id=?,context_revision=?,auth_epoch=?,governance_epoch=?,
                            revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                        WHERE session_id=? AND user_id=? AND context_revision=? AND revision<18446744073709551615
                        """, request.tenant_id(), revision, selected.auth_epoch(), selected.governance_epoch(),
                        subject.sessionId(), subject.user().user_id(), header));
                requireOne(operations.saveOperation(subject.user().user_id(), operationId, key, sha256, schema.canonical(result),
                        request.tenant_id(), schema.manifestSha256(), policy.classification()));
                requireOne(operations.saveAudit(subject.user().user_id(), UUID.randomUUID().toString(), schema.audit(
                        new BusinessSessionProtocol.SelectionAudit("1.0.0", "SESSION_CONTEXT", request.tenant_id(),
                        subject.user().user_id(), subject.sessionId(), revision)), requestId, schema.manifestSha256(), policy.classification()));
                requireOne(operations.saveEvent(subject.user().user_id(), UUID.randomUUID().toString(), context.id(), revision, schema.event(
                        new BusinessSessionProtocol.SelectionEvent("1.0.0", "session.context_changed", operationId,
                        subject.user().user_id(), subject.sessionId(), selected))));
                return result;
            }).get());
        }
    }

    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    private static void requireOne(int count) { if (count != 1) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED); }
    private record LockedContext(String id, String tenantId, String revision, String authEpoch, String governanceEpoch) { }
}
