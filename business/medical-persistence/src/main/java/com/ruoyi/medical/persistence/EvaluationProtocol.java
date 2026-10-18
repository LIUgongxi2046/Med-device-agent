package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/** Evaluation of previously produced outputs. No record here is execution or publication authority. */
public final class EvaluationProtocol {
    private EvaluationProtocol() { }
    public record Expectation(String dimension, List<String> applicable_item_ids, List<String> control_item_ids) {
        public Expectation { applicable_item_ids=List.copyOf(applicable_item_ids); control_item_ids=List.copyOf(control_item_ids); }
    }
    public record Sample(String sample_id, String source_family, String split, String document_type,
            String evidence_class, PermissionProtocol.Basis input_ref, PermissionProtocol.Basis expected_ref, List<Expectation> expectations) {
        public Sample { expectations=List.copyOf(expectations); }
    }
    public record Variance(int repeat_count, String policy, String confidence_interval_method) { }
    public record Create(String schema_version, String project_id, PermissionProtocol.Basis dataset,
            PermissionProtocol.Basis sample_manifest_artifact, List<Sample> samples, List<PermissionProtocol.Basis> candidates,
            PermissionProtocol.Basis rule_revision, List<String> required_dimensions, Variance variance_policy,
            PermissionProtocol.Basis authorized_use_evidence, String environment, String reason) {
        public Create { samples = List.copyOf(samples); candidates = List.copyOf(candidates); required_dimensions = List.copyOf(required_dimensions); }
    }
    public record Candidate(String version_id, String content_sha256, String agent_id, String created_by,
            JsonNode dependency_manifest, JsonNode limits, String schema_version, String schema_manifest_sha256) { }
    public record Definition(String schema_version, String tenant_id, String evaluation_id, Create request,
            List<Candidate> candidate_snapshots, List<String> approved_expectation_samples, String authority_grant_id, String authority_revision, String created_by, String created_at) {
        public Definition { candidate_snapshots = List.copyOf(candidate_snapshots); approved_expectation_samples=List.copyOf(approved_expectation_samples); }
    }
    public record Cell(String sample_id, String agent_version_id, String dimension, int repeat_index) { }
    public record Append(String schema_version, String expected_revision, String definition_sha256, Cell cell,
            PermissionProtocol.Basis input_ref, PermissionProtocol.Basis output_ref, List<PermissionProtocol.Basis> evidence_refs,
            String numerator, String denominator, String unknown_count, String violation_count,
            String control_numerator, String control_denominator, String status, String supersedes, String reason) {
        public Append { evidence_refs = List.copyOf(evidence_refs); }
    }
    public record Score(String schema_version, String score_id, String evaluation_id, String definition_sha256,
            PermissionProtocol.Basis rule_revision, Append observation, String scorer_id, String authority_grant_id,
            String authority_revision, String created_at) { }
    public record Finalize(String schema_version, String expected_revision, String definition_sha256) { }
    public record CellResult(Cell cell, String score_id, String status, String reason, String value) { }
    public record DimensionSummary(String dimension, int expected_cells, int observed_cells, int passed_cells, int failed_cells, int insufficient_cells) { }
    public record Result(String schema_version, String evaluation_id, String definition_sha256, String score_set_sha256,
            List<Score> effective_scores, List<CellResult> cells, List<DimensionSummary> dimensions, List<String> missing_requirements,
            String verdict, boolean production_eligible) {
        public Result { effective_scores = List.copyOf(effective_scores); cells = List.copyOf(cells); dimensions = List.copyOf(dimensions); missing_requirements = List.copyOf(missing_requirements); }
    }
    public record Stored(String tenantId, String projectId, String evaluationId, String definition, String contentHash,
            String registryHash, String projection, String status, BigInteger revision, String resultJson, String resultHash,
            String schemaVersion, String manifest, String createdBy, LocalDateTime createdAt, String classification) { }
    public record StoredScore(String scoreId, String evaluationId, String scoreJson, String contentHash, String projection,
            String schemaVersion, String manifest, String createdBy, LocalDateTime createdAt, String classification) { }
    public record AgentRow(String tenantId, String projectId, String versionId, String contentHash, String registryHash,
            String agentId, String createdBy, String dependencyManifest, String limits, String status, String schemaVersion, String manifest) { }
}
