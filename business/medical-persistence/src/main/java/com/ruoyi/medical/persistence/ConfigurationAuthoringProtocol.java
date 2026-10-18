package com.ruoyi.medical.persistence;

import java.time.LocalDateTime;

/** Immutable identity is reconstructed from concrete version columns and typed direct lineage. */
public final class ConfigurationAuthoringProtocol {
    private ConfigurationAuthoringProtocol() { }
    public record Stored(String tenantId,String projectId,String configId,String versionId,String kind,String name,
            String projection,String contentHash,String registryHash,String status,String headStatus,String currentVersion,
            String schemaVersion,String manifest,String createdBy,LocalDateTime createdAt,String classification) { }
    public record Parent(String versionId,String relation,String hash) { }
}
