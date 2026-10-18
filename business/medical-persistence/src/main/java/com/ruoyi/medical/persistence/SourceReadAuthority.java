package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class SourceReadAuthority {
    private final SourceReadMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;
    private final SourceRightsSchema rights;

    public SourceReadAuthority(SourceReadMapper mapper, PermissionMapper permissions, ResourceReadAuthority authority, SourceRightsSchema rights) {
        this.mapper = mapper; this.permissions = permissions; this.authority = authority; this.rights = rights;
    }

    SourceReadProtocol.VerifiedSource require(EnterpriseAccess.Admission admission, SourceReadProtocol.ReadRequirement requirement) throws IOException {
        Objects.requireNonNull(admission); Objects.requireNonNull(requirement);
        if(admission.formalWorker())admission.formalProof().requireActive();
        TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.SourceReadMapper.source");
        var source = single(mapper.source(requirement.sourceVersion()));
        if (!admission.context().tenant_id().equals(source.tenantId()) || !source.sourceVersion().equals(requirement.sourceVersion())) denied();
        if (!source.originalSha256().equals(source.registeredSha256())
                || !Objects.equals(source.ownerProjectId(), source.registeredProjectId())) mismatch();
        // A formal run is not the initiator's private conversation session, including REVIEW/HISTORY.
        if(admission.formalWorker() && "SESSION".equals(source.ownership()))denied();
        var scope = new PermissionProtocol.Scope(source.tenantId(), source.ownerProjectId(), "SOURCE", source.sourceId());
        var sourceGrant = authority.require(admission, scope, "source.read", requirement.purpose());
        PermissionProtocol.GrantRow projectGrant = null;
        if (requirement.projectId() != null) projectGrant = authority.require(admission,
            new PermissionProtocol.Scope(source.tenantId(), requirement.projectId(), "PROJECT", requirement.projectId()), "project.read", requirement.purpose());
        if (!Set.of("ENTERPRISE", "PROJECT", "SESSION").contains(source.ownership())
                || "ENTERPRISE".equals(source.ownership()) != (source.ownerProjectId() == null)) mismatch();
        if (!"ENTERPRISE".equals(source.ownership()) && !Objects.equals(source.ownerProjectId(), requirement.projectId())) denied();
        if (requirement.use() == SourceReadProtocol.Use.TEMPLATE && !"ENTERPRISE".equals(source.ownership())) denied();
        var currentRights = single(mapper.rights(requirement.rightsRevision()));
        rights.require(currentRights, source, requirement, permissions.databaseTime());
        var gates = mapper.gates(source.sourceVersion());
        if (gates.isEmpty()) throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
        for (var gate : gates) {
            if (Set.of("PURGE_PENDING", "PURGED").contains(gate.lifecycle())) denied();
            if (!Set.of("ACTIVE", "RECYCLED").contains(gate.lifecycle())) mismatch();
            WireValues.revision(gate.revision().toString());
        }
        SourceReadProtocol.ConversationRow conversation = null;
        if ("SESSION".equals(source.ownership())) {
            if(admission.simulationWorker()) denied();
            if (requirement.conversationId() == null || requirement.use() == SourceReadProtocol.Use.FORMAL
                    || requirement.use() == SourceReadProtocol.Use.MODEL_SEND) denied();
            conversation = single(mapper.conversation(requirement.conversationId()).stream()
                .filter(row -> row.sourceId().equals(source.sourceId())).toList());
            if (!source.tenantId().equals(conversation.tenantId()) || !requirement.projectId().equals(conversation.projectId())
                    || !requirement.conversationId().equals(conversation.conversationId()) || !"ACTIVE".equals(conversation.status())
                    || !admission.actorId().equals(conversation.ownerId())) denied();
        } else if (requirement.conversationId() != null) denied();
        SourceReadProtocol.SelectionRow selection = null;
        if (requirement.snapshotId() != null) {
            selection = single(mapper.selection(source.sourceVersion(), requirement.projectId(), requirement.snapshotId(), requirement.parseVersion()));
            if (!source.tenantId().equals(selection.tenantId()) || !source.sourceId().equals(selection.sourceId())
                    || !source.sourceVersion().equals(selection.sourceVersion()) || !requirement.projectId().equals(selection.projectId())
                    || !requirement.snapshotId().equals(selection.snapshotId()) || !requirement.parseVersion().equals(selection.parseVersion())) mismatch();
            if (requirement.use() == SourceReadProtocol.Use.FORMAL || requirement.use() == SourceReadProtocol.Use.MODEL_SEND) requireReady(selection);
        }
        PermissionProtocol.GrantRow externalGrant = null;
        if (requirement.use() == SourceReadProtocol.Use.MODEL_SEND) externalGrant = authority.require(admission, scope, "source.external.process", requirement.purpose());
        return new SourceReadProtocol.VerifiedSource(source, currentRights, selection, conversation, sourceGrant, projectGrant, externalGrant);
    }

    private static void requireReady(SourceReadProtocol.SelectionRow row) {
        if (!"RECEIVED".equals(row.uploadStatus()) || !"APPROVED".equals(row.approvalStatus()) || !"ACTIVE".equals(row.lifecycle())
                || !row.approvalRevision().equals(row.currentApprovalRevision()) || !"APPROVE".equals(row.decision())
                || !"COMPLETE".equals(row.parseStatus()) || !"READY".equals(row.indexStatus())
                || row.expectedCount().signum() <= 0 || !row.expectedCount().equals(row.verifiedCount())
                || !"APPLICABLE".equals(row.applicability()) || !"ACTIVE".equals(row.projectStatus())) {
            throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
        }
        WireValues.uuid(row.parseVersion()); WireValues.uuid(row.indexVersion()); WireValues.uuid(row.approvalRevision());
        WireValues.uuid(row.bindingRevision()); WireValues.hash(row.reviewedManifestSha256());
        WireValues.revision(row.gateRevision().toString()); WireValues.revision(row.expectedCount().toString());
    }

    SourceReadProtocol.VerifiedSource requireCandidate(EnterpriseAccess.Admission admission,
            SourceReadProtocol.SnapshotRequirement requirement) throws IOException {
        Objects.requireNonNull(admission); Objects.requireNonNull(requirement);
        String statement = "com.ruoyi.medical.persistence.mapper.SourceReadMapper.snapshotCandidate";
        TenantScope.requireSqlAccess(statement);
        TenantScope.requireSubjectParameter(statement, java.util.Map.of("userId", admission.actorId()));
        // 尚未保存的快照没有身份；原组件核查当前读取授权，正式用途继续核查 ENTERPRISE_USE。
        var readRequirement = new SourceReadProtocol.ReadRequirement(requirement.sourceVersion(), requirement.rightsRevision(),
                requirement.projectId(), null, null, null, SourceReadProtocol.Use.REVIEW, requirement.purpose(),
                requirement.environment(), requirement.region(), null);
        var verified = require(admission, readRequirement);
        rights.requireEnterpriseUse(verified.rights());
        var candidates = mapper.snapshotCandidate(requirement.sourceVersion(), requirement.projectId(), requirement.parseVersion(),
                requirement.indexVersion(), requirement.approvalRevision(), requirement.bindingRevision(), admission.actorId());
        if (candidates.isEmpty()) throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
        if (candidates.size() != 1) mismatch();
        var selection = candidates.get(0);
        if (selection.snapshotId() != null || !admission.context().tenant_id().equals(selection.tenantId())
                || !verified.source().sourceId().equals(selection.sourceId()) || !requirement.sourceVersion().equals(selection.sourceVersion())
                || !requirement.projectId().equals(selection.projectId()) || !requirement.parseVersion().equals(selection.parseVersion())
                || !requirement.indexVersion().equals(selection.indexVersion()) || !requirement.approvalRevision().equals(selection.approvalRevision())
                || !requirement.bindingRevision().equals(selection.bindingRevision())) mismatch();
        requireReady(selection);
        var current = require(admission, readRequirement);
        rights.requireEnterpriseUse(current.rights());
        if (!current.source().equals(verified.source()) || !current.rights().equals(verified.rights()))
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        return new SourceReadProtocol.VerifiedSource(current.source(), current.rights(), selection, null,
                current.sourceGrant(), current.projectGrant(), null);
    }
    private static <T> T single(List<T> rows) { if (rows.size() != 1) denied(); return rows.get(0); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
}
