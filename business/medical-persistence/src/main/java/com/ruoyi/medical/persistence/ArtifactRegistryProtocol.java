package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.math.BigInteger;
import java.util.List;

public final class ArtifactRegistryProtocol {
    private ArtifactRegistryProtocol() { }
    public record ArtifactRow(String tenantId, String projectId, String artifactId, String artifactKind, String storedObject,
            String objectKey, String objectVersion, String sha256, BigInteger sizeBytes, String mediaType, String accessSetId,
            String contentSha256, String schemaVersion, String schemaManifest, String classification) {
        ObjectRef reference() { return new ObjectRef(objectKey, objectVersion, sha256, sizeBytes.toString(), mediaType); }
    }
    public record BindingRow(String tenantId, String projectId, String artifactId, String resourceKind, String resourceId,
            String schemaRef, String schemaDocumentSha256, String accessSetId, String dependencyManifestSha256) { }
    public record ManifestRow(String tenantId, String projectId, String accessSetId, String storedManifest, String manifestSha256,
            String contentSha256, BigInteger memberCount, String schemaVersion, String schemaManifest) { }
    public record MemberRow(String upstreamVersionId, String rightsRevision) { }
    public record VersionRow(String id, String projectId, String entityKind, String contentSha256) { }
    public record DependencyManifest(String schema_version, String tenant_id, String project_id, String access_set_id, List<MemberRow> members) {
        public DependencyManifest { members = List.copyOf(members); }
    }
    public record ReadRequirement(PermissionProtocol.Scope scope, String action, String purpose, String schemaRef,
            String schemaDocumentSha256, String artifactSchemaManifest, DependencyLineage.Limits lineageLimits) { }
    // 登记核验结果只供后续来源授权处理；取得该结果不允许传输内容。
    record Registration(ArtifactRow artifact, BindingRow binding, DependencyManifest manifest,
            PermissionProtocol.GrantRow artifactGrant, PermissionProtocol.GrantRow resourceGrant,
            DependencyLineage.VerifiedLineage lineage) { }
}
