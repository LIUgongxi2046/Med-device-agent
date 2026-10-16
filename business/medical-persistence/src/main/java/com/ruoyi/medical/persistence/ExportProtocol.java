package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public final class ExportProtocol {
    private ExportProtocol() { }

    public record CreateInput(String schema_version, String document_revision, String purpose, List<String> formats,
            String template_version, String review_manifest_sha256, String format_profile_revision,
            String channel_profile_revision) {
        public CreateInput { formats = List.copyOf(formats); }
    }
    public record RetryInput(String schema_version, String expected_revision, String reason) { }
    public record ProcessInput(String schema_version, String expected_revision) { }
    public record DocumentRow(String tenantId, String projectId, String documentId, String documentRevision,
            String currentDocumentRevision, String status, String projectStatus, String templateVersion,
            String dependencyAccessSetId, String bodyArtifactId, String bodySha256, String classification, String formatProfile,
            String formatProfileRevision) { }
    public record SignatureRow(String signatureId, String manifestSha256, BigInteger authEpoch,
            BigInteger governanceEpoch) { }
    public record VersionRow(String versionId, String ownerProjectId, String entityKind, String contentSha256) { }
    public record ManifestRow(String tenantId, String projectId, String exportId, String documentId,
            String documentRevision, String purpose, String manifest, String manifestSha256, String status,
            String signatureId, String operationId, String templateVersion, String dependencyAccessSetId,
            String dependencyManifestSha256, String reviewManifestSha256, String formatProfileRevision, String channelProfileRevision,
            String schemaVersion, String schemaManifestSha256, String createdBy, LocalDateTime createdAt,
            String classification, BigInteger revision, LocalDateTime updatedAt) { }
    public record ItemRow(String tenantId, String projectId, String exportItemId, String exportId,
            String documentId, String documentRevision, String format, BigInteger attemptNo, String status,
            String objectRef, String artifactId, String validationRef, String validationArtifactId,
            String converterRevision, String fontManifestSha256, String schemaVersion,
            String schemaManifestSha256, String createdBy, LocalDateTime createdAt, String classification,
            BigInteger revision, LocalDateTime updatedAt) { }
    public record Download(byte[] bytes, String mediaType, String fileName, long start, long end, long size,
            String sha256, String artifactId, String exportItemId) {
        public Download { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
