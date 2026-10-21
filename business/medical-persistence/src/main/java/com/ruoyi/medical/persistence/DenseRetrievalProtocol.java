package com.ruoyi.medical.persistence;

import java.util.List;

/** Immutable, explicit embedding/index authoring; never supplies vectors or provider receipts. */
public final class DenseRetrievalProtocol {
    private DenseRetrievalProtocol() { }
    public record Configuration(String schema_version, String project_id, String model_revision,
            String mode, int dimension, int maximum_chunks, int maximum_input_bytes,
            String input_price_per_million, PermissionProtocol.Basis price_basis, String reason) { }
    public record Source(String source_version, String parse_version, String snapshot_id,
            String approval_revision, String rights_revision, String purpose) { }
    public record Build(String schema_version, String config_revision, Source source,
            List<PermissionProtocol.Basis> budget_bases) { }
    public record Search(String schema_version, String config_revision, List<String> index_versions,
            String query, int limit, List<PermissionProtocol.Basis> budget_bases) { }
}
