package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public final class RuleVersionProtocol {
    private RuleVersionProtocol() { }
    public record VersionInput(String schema_version, PermissionProtocol.Basis base_version, String content_schema_ref,
            TemplateApplicabilityProtocol.RuleContent content, String reason) { }
    public record Validated(VersionInput request, String canonical_content, String content_sha256,
            List<PermissionProtocol.Basis> dependency_refs) {
        public Validated { dependency_refs = List.copyOf(dependency_refs); }
    }
    public record Stored(String tenant_id, String rule_version, String status, String revision,
            TemplateApplicabilityProtocol.RuleContent content, String content_sha256, PermissionProtocol.Basis base_version,
            String reason, String authority_grant_id, String authority_revision, String created_by, String created_at,
            List<PermissionProtocol.Basis> dependency_refs) {
        public Stored { dependency_refs = List.copyOf(dependency_refs); }
    }
    public record StorageRow(String tenantId, String ruleVersion, String engineRevision, String expression, String basisManifest,
            String status, String contentHash, String registryHash, String schemaVersion, String manifest,
            String createdBy, LocalDateTime createdAt, String classification, String contentJson,
            String baseVersion, String baseHash, String reason, String authorityGrant, BigInteger authorityRevision, BigInteger revision) { }
    public record Baseline(String schema_version, String rule_version, String content_sha256,
            String definition_manifest_sha256, List<PermissionProtocol.Basis> dependency_refs,
            String dependency_authorizations_sha256) {
        public Baseline { dependency_refs = List.copyOf(dependency_refs); }
    }
    public record Decide(String schema_version, String target_revision, String expected_revision,
            String decision, String reason, Baseline baseline_manifest) { }
}
