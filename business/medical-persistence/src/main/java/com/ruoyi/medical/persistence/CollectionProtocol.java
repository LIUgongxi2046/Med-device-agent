package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public final class CollectionProtocol {
    private CollectionProtocol() { }
    public record MemberSelection(String source_version, String parse_version) { }
    public record Content(String schema_version, String title, String purpose, List<MemberSelection> members) {
        public Content { members = List.copyOf(members); }
    }
    public record Create(String schema_version, String owner_project_id, Content content, String reason) { }
    public record VersionInput(String schema_version, String expected_revision, PermissionProtocol.Basis base_version,
            String schema_ref, Content content, String reason) { }
    public record Member(String source_id, String source_version, String parse_version, String rights_revision,
            String original_sha256, String parse_sha256) { }
    public record Manifest(String schema_version, String tenant_id, String collection_id, String collection_version,
            String owner_project_id, String title, String purpose, List<Member> members, PermissionProtocol.Basis base_version,
            String revision, String reason, String authority_grant_id, String authority_revision, String created_by, String created_at) {
        public Manifest { members = List.copyOf(members); }
    }
    public record View(String schema_version, Manifest manifest, String content_sha256, String schema_ref,
            String schema_manifest_sha256, List<String> allowed_actions) {
        public View { allowed_actions = List.copyOf(allowed_actions); }
    }
    public record Created(String schema_version, String operation_id, View collection) { }
    public record Head(String tenantId, String collectionId, String projectId, String title, String currentVersion,
            String createdBy, LocalDateTime createdAt, String classification, BigInteger revision, LocalDateTime updatedAt) { }
    public record Version(String tenantId, String versionId, String collectionId, String purpose, String manifest,
            String contentSha256, String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt,
            String classification, String registeredSha256, String registeredProjectId) { }
    public record Parse(String tenantId, String sourceVersion, String parseVersion, String contentSha256, String status,
            String registeredSha256, String registeredProjectId) { }
    public record StoredMember(String tenantId, String versionId, String sourceVersion, String parseVersion,
            String createdBy, LocalDateTime createdAt, String classification) { }
    public record Query(int page, int pageSize, String watermark) { }
}
