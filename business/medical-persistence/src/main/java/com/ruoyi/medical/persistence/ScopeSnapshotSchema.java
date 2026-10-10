package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.CommonBindings;
import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class ScopeSnapshotSchema {
    private final TaskInputSchema inputs;
    private final SchemaCatalogue shared;

    public ScopeSnapshotSchema(TaskInputSchema inputs) throws IOException, java.security.NoSuchAlgorithmException {
        this.inputs = inputs; shared = SchemaCatalogue.fromClasspath();
    }

    TaskProtocol.ScopeSnapshotManifest require(String content, String sha256, String tenant, String project, String snapshot,
            String profile, BigInteger authEpoch, BigInteger governanceEpoch, CommonProtocol.SourceScope expectedScope,
            List<DocumentUploadBasisProtocol.MemberRow> members) throws IOException {
        var value = inputs.read(content);
        var bound = shared.bind("tasks", value, "ScopeSnapshotManifest");
        if (!DigestUtils.sha256Hex(bound.canonicalBytes()).equals(sha256)) mismatch();
        var manifest = inputs.snapshot(value);
        var scope = TypedSchemaBindings.bind(shared, CommonBindings.SOURCE_SCOPE, value.required("scope_selection")).read();
        WireValues.uuid(manifest.tenant_id()); WireValues.uuid(manifest.project_id()); WireValues.uuid(manifest.snapshot_id());
        WireValues.uuid(manifest.profile_revision());
        var auth = WireValues.revision(manifest.auth_epoch()); var governance = WireValues.revision(manifest.governance_epoch());
        if (!tenant.equals(manifest.tenant_id()) || !project.equals(manifest.project_id()) || !snapshot.equals(manifest.snapshot_id())
                || !profile.equals(manifest.profile_revision()) || !authEpoch.equals(auth) || !governanceEpoch.equals(governance)
                || !scope.equals(manifest.scope_selection()) || expectedScope != null && !expectedScope.equals(scope)
                || members.size() != manifest.members().size()) mismatch();
        var versions = new HashSet<String>(); String previous = null;
        for (int index = 0; index < members.size(); index++) {
            var actual = members.get(index); var fixed = manifest.members().get(index);
            for (String id : List.of(fixed.source_id(), fixed.source_version(), fixed.parse_version(), fixed.index_version(),
                    fixed.approval_revision(), fixed.binding_revision(), fixed.rights_revision())) WireValues.uuid(id);
            WireValues.hash(fixed.original_sha256());
            // 清单和关联表保存同一有序完整集合；任何成员缺失或版本变化立即拒绝。
            if (!tenant.equals(actual.tenantId()) || !project.equals(actual.projectId()) || !snapshot.equals(actual.snapshotId())
                    || !fixed.source_id().equals(actual.sourceId()) || !fixed.source_version().equals(actual.sourceVersion())
                    || !fixed.parse_version().equals(actual.parseVersion()) || !fixed.index_version().equals(actual.indexVersion())
                    || !fixed.approval_revision().equals(actual.approvalRevision()) || !fixed.binding_revision().equals(actual.bindingRevision())
                    || !versions.add(fixed.source_version()) || previous != null && previous.compareTo(fixed.source_version()) >= 0) mismatch();
            previous = fixed.source_version();
        }
        if (scope.mode() == CommonProtocol.ScopeMode.SOURCE_VERSIONS
                || scope.mode() == CommonProtocol.ScopeMode.COMBINED && scope.collectionVersions().isEmpty()) {
            if (!versions.equals(new HashSet<>(scope.sourceVersions()))) mismatch();
        } else if (scope.mode() == CommonProtocol.ScopeMode.COLLECTION_VERSIONS && scope.collectionVersions().isEmpty()) {
            if (!versions.isEmpty()) mismatch();
        } else if (scope.mode() == CommonProtocol.ScopeMode.COMBINED && !versions.containsAll(scope.sourceVersions())) mismatch();
        return manifest;
    }

    static void requireSource(TaskProtocol.ScopeSnapshotMember member, SourceReadProtocol.VerifiedSource source) {
        Objects.requireNonNull(member); Objects.requireNonNull(source);
        if (!member.source_id().equals(source.source().sourceId()) || !member.source_version().equals(source.source().sourceVersion())
                || !member.original_sha256().equals(source.source().originalSha256())) mismatch();
        if (!member.rights_revision().equals(source.rights().rightsRevision())) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
    }

    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
