package com.ruoyi.medical.persistence;

import java.util.List;

public final class TemplateApprovalProtocol {
    private TemplateApprovalProtocol() { }
    public record Baseline(String schema_version, String template_version, String content_sha256,
            String definition_manifest_sha256, List<PermissionProtocol.Basis> dependency_refs,
            String dependency_authorizations_sha256) {
        public Baseline { dependency_refs = List.copyOf(dependency_refs); }
    }
    public record Decide(String schema_version, String target_revision, String expected_revision,
            String decision, String reason, Baseline baseline_manifest) { }
}
