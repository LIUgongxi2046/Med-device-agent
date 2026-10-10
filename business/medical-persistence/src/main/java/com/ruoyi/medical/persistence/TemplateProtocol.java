package com.ruoyi.medical.persistence;

import java.util.List;
import java.math.BigInteger;
import java.time.LocalDateTime;

public final class TemplateProtocol {
    private TemplateProtocol() { }

    public record VersionInput(String schema_version, PermissionProtocol.Basis base_version, String schema_ref,
            Content content, String reason) { }
    public record Content(String schema_version, String document_type, String application_path, boolean ivd,
            String directory_profile, List<Node> nodes, List<Field> field_definitions, List<Repeat> repeat_definitions,
            List<Attachment> attachment_requirements, List<CrossDocumentRule> cross_document_rules,
            ConditionSchema condition_schema, List<PermissionProtocol.Basis> basis_manifest, FormatProfile format_profile) {
        public Content {
            nodes = List.copyOf(nodes); field_definitions = List.copyOf(field_definitions);
            repeat_definitions = List.copyOf(repeat_definitions); attachment_requirements = List.copyOf(attachment_requirements);
            cross_document_rules = List.copyOf(cross_document_rules); basis_manifest = List.copyOf(basis_manifest);
        }
    }
    public record Condition(String mode, PermissionProtocol.Basis rule_revision, String engine_revision, List<String> input_keys) {
        public Condition { input_keys = List.copyOf(input_keys); }
    }
    public record ConditionSchema(List<String> attribute_keys, Condition applicability, String unknown_result) {
        public ConditionSchema { attribute_keys = List.copyOf(attribute_keys); }
    }
    public record EvidenceRule(String mode, List<String> authority_source_types, List<PermissionProtocol.Basis> verification_rule_revisions,
            PermissionProtocol.Basis calculation_rule_revision, List<String> input_field_ids,
            boolean require_original_locator, boolean require_human_verification) {
        public EvidenceRule {
            authority_source_types = List.copyOf(authority_source_types);
            verification_rule_revisions = List.copyOf(verification_rule_revisions); input_field_ids = List.copyOf(input_field_ids);
        }
    }
    public record Node(String node_id, String parent_id, String official_number, String title, int ordinal, Condition condition,
            boolean required, String content_mode, EvidenceRule evidence_rule, List<PermissionProtocol.Basis> basis_refs,
            String nature, List<String> content_requirements) {
        public Node { basis_refs = List.copyOf(basis_refs); content_requirements = List.copyOf(content_requirements); }
    }
    public record Constraints(Integer max_length, Integer precision, String minimum, String maximum, String unit,
            List<PermissionProtocol.Basis> range_basis, List<Option> enum_options, List<String> nullable_reasons) {
        public Constraints {
            range_basis = List.copyOf(range_basis); enum_options = List.copyOf(enum_options); nullable_reasons = List.copyOf(nullable_reasons);
        }
    }
    public record Option(String value, String label, List<PermissionProtocol.Basis> basis_refs) {
        public Option { basis_refs = List.copyOf(basis_refs); }
    }
    public record OutputMapping(String node_id, String repeat_id, String column_key, boolean internal_only, int ordinal) { }
    public record Field(String field_id, String node_id, String key, String name, String description, String value_type,
            Constraints constraints, Condition condition, boolean required, EvidenceRule evidence_rule,
            OutputMapping output_mapping, String repeat_id, String attachment_requirement_id, List<PermissionProtocol.Basis> basis_refs) {
        public Field { basis_refs = List.copyOf(basis_refs); }
    }
    public record Repeat(String repeat_id, String node_id, String name, List<String> field_ids, String min_count,
            String max_count, List<PermissionProtocol.Basis> count_basis, boolean row_id_required) {
        public Repeat { field_ids = List.copyOf(field_ids); count_basis = List.copyOf(count_basis); }
    }
    public record Attachment(String attachment_requirement_id, String node_id, String name, String kind, String mode,
            Condition condition, boolean required, String min_count, String max_count, List<PermissionProtocol.Basis> count_basis,
            List<String> allowed_media_types, EvidenceRule evidence_rule, int ordinal) {
        public Attachment { count_basis = List.copyOf(count_basis); allowed_media_types = List.copyOf(allowed_media_types); }
    }
    public record CrossDocumentRule(String rule_id, PermissionProtocol.Basis rule_revision, List<String> field_ids,
            List<String> related_document_types, String severity) {
        public CrossDocumentRule { field_ids = List.copyOf(field_ids); related_document_types = List.copyOf(related_document_types); }
    }
    public record NodeDisplay(String node_id, String not_applicable_display, PermissionProtocol.Basis rule_revision) { }
    public record FormatProfile(PermissionProtocol.Basis format_revision, List<String> output_types,
            PermissionProtocol.Basis numbering_rule_revision, List<PermissionProtocol.Basis> font_artifacts,
            List<NodeDisplay> node_displays, String attachment_order) {
        public FormatProfile {
            output_types = List.copyOf(output_types); font_artifacts = List.copyOf(font_artifacts); node_displays = List.copyOf(node_displays);
        }
    }
    public record Validated(VersionInput request, String canonical_content, String content_sha256,
            String schema_manifest_sha256, List<PermissionProtocol.Basis> dependency_refs) {
        public Validated { dependency_refs = List.copyOf(dependency_refs); }
    }
    public record StorageRow(String tenantId, String templateVersion, String documentType, String applicationPath,
            String status, String contentJson, String contentSha256, String registeredSha256, String baseVersion,
            String baseVersionSha256, String reason, String authorityGrant, BigInteger authorityRevision,
            String schemaVersion, String manifest, String createdBy, LocalDateTime createdAt, String classification,
            BigInteger revision, String basisManifest, String formatProfile, String conditionSchema) { }
    public record Projection(String id, String content, String schemaVersion, String manifest, String createdBy,
            LocalDateTime createdAt, String classification) { }
    public record Stored(String tenant_id, String template_version, String status, String revision, Content content,
            String content_sha256, PermissionProtocol.Basis base_version, String reason, String authority_grant_id,
            String authority_revision, String created_by, String created_at, List<PermissionProtocol.Basis> dependency_refs) {
        public Stored { dependency_refs = List.copyOf(dependency_refs); }
    }
}
