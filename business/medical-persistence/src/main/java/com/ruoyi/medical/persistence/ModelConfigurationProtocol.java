package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/** Fixed draft metadata only. These records never authorize a provider call. */
public final class ModelConfigurationProtocol {
    private ModelConfigurationProtocol() { }
    public record Limits(String context_window, String max_output_tokens, String max_concurrency,
            String requests_per_minute, String tokens_per_minute, int deadline_seconds, boolean temperature_enabled,
            List<String> reasoning_efforts, int stop_limit) {
        public Limits { reasoning_efforts = List.copyOf(reasoning_efforts); }
    }
    public record DataPolicy(List<String> allowed_classifications, List<String> allowed_regions, boolean allow_training,
            String retention_days, PermissionProtocol.Basis policy_basis) {
        public DataPolicy { allowed_classifications = List.copyOf(allowed_classifications); allowed_regions = List.copyOf(allowed_regions); }
    }
    @com.fasterxml.jackson.annotation.JsonTypeInfo(use=com.fasterxml.jackson.annotation.JsonTypeInfo.Id.DEDUCTION)
    @com.fasterxml.jackson.annotation.JsonSubTypes({
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(Definition.class),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(SimulationDefinition.class)
    })
    public sealed interface ConfigurationDefinition permits Definition, SimulationDefinition {
        Limits limits();
        default String auth_ref() { return null; }
    }
    public record CaseBinding(String case_id, String case_sha256, String validation) { }
    public record SimulationDefinition(String execution_mode, String simulator_id, String simulator_version,
            String model_id, String model_version, CaseBinding case_binding, Limits limits,
            String provenance_schema_ref, String provenance_schema_sha256, boolean production_eligible,
            String provider_charge_amount, String charge_basis) implements ConfigurationDefinition {
        @Override public String toString() { return "SimulationModelConfigurationDefinition[redacted]"; }
    }
    public record Definition(String provider, String base_url, String region, String model_id, String model_version,
            String auth_ref, String credential_reference_id, String credential_revision, Limits limits,
            PermissionProtocol.Basis price_basis, String currency, DataPolicy data_policy,
            String request_parameter_schema_ref, String request_parameter_schema_sha256, String model_protocol_revision) implements ConfigurationDefinition {
        @Override public String toString() { return "ModelConfigurationDefinition[redacted]"; }
    }
    public record Save(String schema_version, String project_id, String config_id, String expected_revision,
            PermissionProtocol.Basis based_on_revision, ConfigurationDefinition definition, String reason) {
        @Override public String toString() { return "ModelConfigurationSave[redacted]"; }
    }
    public record Fixed(String schema_version, String tenant_id, String project_id, String config_id,
            String model_config_revision, PermissionProtocol.Basis based_on_revision, ConfigurationDefinition definition, String reason,
            String authority_grant_id, String authority_revision, String created_by, String created_at) {
        @Override public String toString() { return "ModelConfigurationFixed[redacted]"; }
    }
    public record Head(String tenantId, String projectId, String configId, String currentVersion, String status,
            BigInteger revision, String classification, String executionMode) {
        public Head(String tenantId, String projectId, String configId, String currentVersion, String status,
                BigInteger revision, String classification) {
            this(tenantId, projectId, configId, currentVersion, status, revision, classification, "EXTERNAL");
        }
    }
    public record Stored(String tenantId, String projectId, String configId, String versionId, String definition,
            String projection, String contentHash, String registryHash, String status, String schemaVersion,
            String manifest, String createdBy, LocalDateTime createdAt, String classification, String executionMode) {
        public Stored(String tenantId, String projectId, String configId, String versionId, String definition,
                String projection, String contentHash, String registryHash, String status, String schemaVersion,
                String manifest, String createdBy, LocalDateTime createdAt, String classification) {
            this(tenantId, projectId, configId, versionId, definition, projection, contentHash, registryHash, status,
                    schemaVersion, manifest, createdBy, createdAt, classification, "EXTERNAL");
        }
        @Override public String toString() { return "ModelConfigurationStored[redacted]"; }
    }
}
