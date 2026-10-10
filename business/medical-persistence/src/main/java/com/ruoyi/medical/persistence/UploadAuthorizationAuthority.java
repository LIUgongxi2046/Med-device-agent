package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class UploadAuthorizationAuthority {
    private static final String DOCUMENT_SCHEMA = "interfaces.schema.yaml#/$defs/DocumentBody";
    private static final String PURPOSE = "DOCUMENT_BODY";
    private static final String STATEMENT = "com.ruoyi.medical.persistence.mapper.UploadTransferMapper.document";
    private final UploadTransferMapper mapper;
    private final ResourceReadAuthority resources;
    private final DependencyAccessAuthority dependencies;
    private final UploadAuthorizationSchema authorizations;
    private final DocumentUploadBasisAuthority bases;
    private final UploadTransferStore uploads;
    private final SchemaCatalogue schemas;

    public UploadAuthorizationAuthority(UploadTransferMapper mapper, ResourceReadAuthority resources,
            DependencyAccessAuthority dependencies, UploadAuthorizationSchema authorizations, DocumentUploadBasisAuthority bases) throws IOException, NoSuchAlgorithmException {
        this.mapper = Objects.requireNonNull(mapper); this.resources = Objects.requireNonNull(resources);
        this.dependencies = Objects.requireNonNull(dependencies); this.authorizations = Objects.requireNonNull(authorizations);
        this.bases = Objects.requireNonNull(bases);
        uploads = new UploadTransferStore(mapper, authorizations); schemas = SchemaCatalogue.fromClasspath();
    }

    // Manifest由服务器固定领域选择产生；公开UploadRequest不能指定来源权限或发布Schema摘要。
    VerifiedDocument requireDocument(EnterpriseAccess.Admission admission, String documentId, String expectedRevision,
            UploadAuthorizationProtocol.Manifest manifest) throws IOException, NoSuchAlgorithmException {
        return requireDocument(admission, documentId, expectedRevision, manifest, true);
    }

    private VerifiedDocument requireDocument(EnterpriseAccess.Admission admission, String documentId, String expectedRevision,
            UploadAuthorizationProtocol.Manifest manifest, boolean writing) throws IOException, NoSuchAlgorithmException {
        if (admission == null) denied();
        TenantScope.requireSqlAccess(STATEMENT); Objects.requireNonNull(manifest);
        if (!TenantScope.requireCurrent().tenantId().equals(admission.context().tenant_id())) denied();
        authorizations.encode(manifest); WireValues.uuid(documentId); var expected = WireValues.revision(expectedRevision);
        if (!admission.context().tenant_id().equals(manifest.tenantId())
                || !admission.subject().user().user_id().equals(manifest.subjectId())) denied();
        if (writing && (!admission.watermarks().authEpoch().toString().equals(manifest.authEpoch())
                || !admission.watermarks().governanceEpoch().toString().equals(manifest.governanceEpoch())))
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        if (!PURPOSE.equals(manifest.purpose()) || !PURPOSE.equals(manifest.dependencyRequirement().purpose())
                || !java.util.Set.of(DOCUMENT_SCHEMA, schemas.schemaRef("interfaces", "DocumentBody")).contains(manifest.schemaRef()) || manifest.projectId() == null
                || manifest.context().use() == SourceReadProtocol.Use.MODEL_SEND) denied();
        var published = schemas.requirePublished(schemas.schemaRef("interfaces", "DocumentBody"), manifest.schemaDocumentSha256());
        // 当前写入许可必须在读取文稿状态和判断版本之前核查。
        var grant = resources.require(admission, new PermissionProtocol.Scope(manifest.tenantId(), manifest.projectId(), "DOCUMENT", documentId),
            writing ? "document.write" : "document.read", PURPOSE);
        var parameters = Map.<String,Object>of("userId", admission.subject().user().user_id(), "documentId", documentId, "lock", true);
        TenantScope.requireSubjectParameter(STATEMENT, parameters);
        var documents = mapper.document(parameters); if (documents.size() != 1) denied();
        var document = documents.get(0);
        if (!manifest.tenantId().equals(document.tenantId()) || !manifest.projectId().equals(document.projectId())
                || !documentId.equals(document.documentId())) denied();
        WireValues.uuid(document.taskId()); WireValues.uuid(document.createdBy());
        if (document.currentDocumentRevision() != null) WireValues.uuid(document.currentDocumentRevision());
        WireValues.revision(document.revision().toString()); WireValues.boundedString(document.status(), 24);
        WireValues.boundedString(document.classification(), 24);
        if (document.status().isEmpty() || document.classification().isEmpty() || document.createdAt() == null
                || document.updatedAt() == null || document.updatedAt().isBefore(document.createdAt())) mismatch();
        if (writing && !"ACTIVE".equals(document.projectStatus())) denied();
        // 签发状态和资源revision分别核查，正文版本UUID保持独立。
        if (writing && "SIGNED".equals(document.status())) throw new TenantAccessException(ErrorCode.DOCUMENT_SIGNED);
        if (writing && !expected.equals(document.revision())) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        var basis = writing ? bases.require(admission, documentId, expectedRevision, manifest.documentBasis().settings()) : null;
        if (writing && !document.equals(basis.document())) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        var verified = dependencies.require(admission, manifest.dependencyAccessSetId(), manifest.dependencyRequirement(),
            manifest.sources(), manifest.context(), manifest.upstreamArtifacts());
        if (!manifest.dependencyManifestSha256().equals(verified.header().manifestSha256())) mismatch();
        if (writing) DocumentUploadBasisAuthority.requireBinding(basis, manifest.documentBasis(), verified, manifest.sources());
        return new VerifiedDocument(document, grant, verified, published, manifest, basis,
            digest(admission, document, grant, verified, published, manifest, basis));
    }

    VerifiedDocument requireBoundDocument(EnterpriseAccess.Admission admission, String uploadId) throws IOException, NoSuchAlgorithmException {
        return requireBoundDocument(admission, uploadId, true);
    }

    VerifiedDocument requireVisibleBoundDocument(EnterpriseAccess.Admission admission, String uploadId) throws IOException, NoSuchAlgorithmException {
        // 已保存回执使用当前读取许可；旧期限和文稿后续revision不触发第二次写入。
        return requireBoundDocument(admission, uploadId, false);
    }

    private VerifiedDocument requireBoundDocument(EnterpriseAccess.Admission admission, String uploadId, boolean writing) throws IOException, NoSuchAlgorithmException {
        if (admission == null) denied();
        var binding = uploads.binding(admission, uploadId);
        if (!PURPOSE.equals(binding.purpose())) denied();
        var upload = uploads.session(admission, uploadId);
        var parameters = Map.<String,Object>of("userId", admission.subject().user().user_id(), "uploadId", uploadId, "lock", true);
        var now = mapper.databaseTime(parameters);
        if (writing && (!java.util.Set.of("ACTIVE", "COMPLETING").contains(upload.state()) || !upload.expiresAt().isAfter(now)))
            throw new TenantAccessException(ErrorCode.ACTION_STALE);
        if (writing && !admission.context().context_revision().equals(binding.contextRevision().toString()))
            throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var grants = mapper.grant(parameters);
        if (grants.size() != 1) denied();
        var grant = grants.get(0);
        if (writing && (!java.util.Set.of("ISSUED", "UPLOADING").contains(grant.status()) || !grant.expiresAt().isAfter(now)))
            throw new TenantAccessException(ErrorCode.ACTION_STALE);
        var manifest = authorizations.requireBinding(binding, grant);
        return requireDocument(admission, binding.documentId(), binding.expectedRevision().toString(), manifest, writing);
    }

    private static String digest(EnterpriseAccess.Admission admission, UploadTransferProtocol.DocumentRow document,
            PermissionProtocol.GrantRow grant, DependencyAccessProtocol.VerifiedAccess verified,
            SchemaCatalogue.PublishedSchema published, UploadAuthorizationProtocol.Manifest manifest,
            DocumentUploadBasisProtocol.Basis basis) throws IOException, NoSuchAlgorithmException {
        var value = new LinkedHashMap<String,Object>(); value.put("schema_version", "1.0.0");
        value.put("subject_id", admission.subject().user().user_id()); value.put("session_id", admission.subject().sessionId());
        value.put("identity_binding", admission.subject().binding().view()); value.put("context_id", admission.contextId());
        value.put("context", admission.context()); value.put("watermarks", admission.watermarks());
        value.put("document", document); value.put("resource_grant", grant); value.put("published_schema", published);
        value.put("document_basis", basis);
        value.put("authorization_manifest", manifest); value.put("dependency_header", verified.header());
        value.put("dependency_manifest", verified.manifest()); value.put("sources", verified.sources());
        value.put("lineage_root", verified.lineage().root()); value.put("lineage_versions", verified.lineage().versions());
        value.put("lineage_relations", verified.lineage().graph().edgeSet().stream().sorted(
            Comparator.comparing(SourceReadProtocol.ParentRow::consumerVersionId).thenComparing(SourceReadProtocol.ParentRow::upstreamVersionId)
                .thenComparing(SourceReadProtocol.ParentRow::relationKind)).toList());
        var versions = new LinkedHashMap<String,Object>();
        for (var item : verified.versions().entrySet()) {
            var version = item.getValue(); var entry = new LinkedHashMap<String,Object>();
            entry.put("version", version.version()); entry.put("domain", version.domain()); entry.put("owner", version.owner());
            entry.put("resource_grant", version.resourceGrant()); entry.put("version_project_grant", version.versionProjectGrant());
            entry.put("artifact", version.artifactRegistration() == null ? null : ContentReadAuthorization.registration(version.artifactRegistration()));
            versions.put(item.getKey(), entry);
        }
        value.put("versions", versions); return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));
    }

    record VerifiedDocument(UploadTransferProtocol.DocumentRow document, PermissionProtocol.GrantRow resourceGrant,
            DependencyAccessProtocol.VerifiedAccess dependencies, SchemaCatalogue.PublishedSchema publishedSchema,
            UploadAuthorizationProtocol.Manifest manifest, DocumentUploadBasisProtocol.Basis basis, String authorizationSha256) { }

    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
