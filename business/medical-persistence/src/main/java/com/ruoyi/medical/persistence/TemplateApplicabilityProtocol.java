package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ResourceProtocol;
import java.time.LocalDateTime;
import java.util.List;

public final class TemplateApplicabilityProtocol {
    private TemplateApplicabilityProtocol() { }
    public record Constant(String key, JsonNode value, List<PermissionProtocol.Basis> basis_refs) {
        public Constant { basis_refs = List.copyOf(basis_refs); }
    }
    public record Expression(String schema_version, String engine_revision, String expression, List<String> input_keys, List<Constant> constants) {
        public Expression { input_keys = List.copyOf(input_keys); constants = List.copyOf(constants); }
    }
    public record RuleContent(String schema_version, String engine_revision, Expression expression, List<PermissionProtocol.Basis> basis_manifest) {
        public RuleContent { basis_manifest = List.copyOf(basis_manifest); }
    }
    public record Result(String status, List<String> unknown_input_keys) {
        public Result { unknown_input_keys = List.copyOf(unknown_input_keys); }
    }
    public record RuleRow(String tenantId, String ruleVersion, String engineRevision, String expression, String basisManifest,
            String status, String contentHash, String registryHash, String schemaVersion, String manifest,
            String createdBy, LocalDateTime createdAt, String classification) { }
    public record Query(String document_type, String profile_revision, int page, int page_size, String dataset_watermark, String filter_sha256) { }
    public record ConditionResult(String scope, String target_id, PermissionProtocol.Basis rule_revision, String engine_revision,
            List<String> input_keys, String status, List<String> unknown_input_keys) {
        public ConditionResult { input_keys = List.copyOf(input_keys); unknown_input_keys = List.copyOf(unknown_input_keys); }
    }
    public record LegacyTemplateView(TemplateProtocol.Stored template, String applicability, List<ConditionResult> conditions,
            List<ResourceProtocol.Requirement> missing_requirements, String dependency_authorizations_sha256) {
        public LegacyTemplateView { conditions=List.copyOf(conditions); missing_requirements=List.copyOf(missing_requirements); }
    }
    /** Internal/application projection, never emitted directly by the legacy list endpoint. */
    public record TemplateView(TemplateProtocol.Stored template, String applicability, List<ConditionResult> conditions,
            List<ResourceProtocol.Requirement> missing_requirements, String dependency_authorizations_sha256,
            List<ConditionResult> machine_conditions, List<JsonNode> human_decisions) {
        public TemplateView { conditions = List.copyOf(conditions); missing_requirements = List.copyOf(missing_requirements);
            machine_conditions = List.copyOf(machine_conditions); human_decisions = human_decisions.stream().map(n -> (JsonNode)n.deepCopy()).toList(); }
        public TemplateView(TemplateProtocol.Stored template,String applicability,List<ConditionResult> conditions,
                List<ResourceProtocol.Requirement> missing,String authorization) {
            this(template,applicability,conditions,missing,authorization,conditions,List.of());
        }
    }
}
