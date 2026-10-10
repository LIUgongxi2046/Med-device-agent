package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectProtocol {
    private ProjectProtocol() { }
    public record Attribute(String key, String status, JsonNode value, List<PermissionProtocol.Basis> authority_refs, String reason) { }
    public record Create(String schema_version, String name, String responsible_user_id, List<Attribute> attributes,
            PermissionProtocol.Basis creation_basis, String reason, boolean approval_attested) { }
    public record Revise(String schema_version, String expected_revision, String based_on_profile_revision,
            List<Attribute> attributes, String reason, boolean approval_attested) { }
    public record Decide(String schema_version, String decision, String expected_revision, String reason, boolean approval_attested) { }
    public record Row(String tenantId, String projectId, String status, BigInteger revision, String name,
            String responsibleUserId, String profileRevision, String creationBasis, String creationBasisSha256,
            String creationReason, String authorityGrant, BigInteger authorityRevision, String schemaVersion, String manifest,
            String createdBy, LocalDateTime createdAt, LocalDateTime updatedAt, BigInteger metadataRevision) { }
    public record Profile(String profileRevision, String attributes, String authorityRefs, String sha256,
            String schemaVersion, String manifest, String createdBy, LocalDateTime createdAt) { }
    public record VerifiedProfile(Row project, String sha256, List<Attribute> attributes, List<PermissionProtocol.Basis> authorityRefs) {
        public VerifiedProfile { attributes = List.copyOf(attributes); authorityRefs = List.copyOf(authorityRefs); }
    }
    public record Run(String runId, String status, BigInteger revision) { }
    public record Waiting(String id, String projectId, String runId, String targetRevision, String reasonCode,
            String question, String requirements, String resolutionActions, String assigneeId, String status,
            BigInteger revision, String schemaVersion, LocalDateTime updatedAt) { }
    public record Access(boolean enterprise, List<String> projects) {
        public Access { projects = List.copyOf(projects); }
        public boolean permits(String project) { return enterprise || project != null && projects.contains(project); }
        public boolean available() { return enterprise || !projects.isEmpty(); }
    }
    public record Query(int limit, String after, String name, String status, String responsible, String project) { }
}
