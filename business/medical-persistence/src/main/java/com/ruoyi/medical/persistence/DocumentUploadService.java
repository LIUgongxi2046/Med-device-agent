package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.DependencyAccessMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
@EnableConfigurationProperties(DocumentUploadPolicy.class)
public final class DocumentUploadService {
    private static final String PURPOSE = "DOCUMENT_BODY";
    private final EnterpriseAccess access;
    private final DocumentUploadPreparation preparation;
    private final DocumentUploadIssuance issuance;
    private final ResourceReadAuthority resources;
    private final PermissionMapper permissions;
    private final ArtifactRegistryMapper artifacts;
    private final DependencyAccessMapper dependencies;
    private final DependencyLineage lineage;
    private final DocumentUploadPolicy policy;
    private final PermissionSchema json;
    private final SchemaCatalogue schemas;

    public DocumentUploadService(EnterpriseAccess access, DocumentUploadPreparation preparation, DocumentUploadIssuance issuance,
            ResourceReadAuthority resources, PermissionMapper permissions, ArtifactRegistryMapper artifacts, DependencyAccessMapper dependencies,
            DependencyLineage lineage, DocumentUploadPolicy policy, PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.access = Objects.requireNonNull(access); this.preparation = Objects.requireNonNull(preparation);
        this.issuance = Objects.requireNonNull(issuance); this.resources = Objects.requireNonNull(resources);
        this.permissions = Objects.requireNonNull(permissions); this.artifacts = Objects.requireNonNull(artifacts);
        this.dependencies = Objects.requireNonNull(dependencies);
        this.lineage = Objects.requireNonNull(lineage); this.policy = Objects.requireNonNull(policy); this.json = Objects.requireNonNull(json);
        schemas = SchemaCatalogue.fromClasspath();
        // 服务器读取类型与发布文件身份固定；客户输入不能扩大上游制品范围。
        for (var profile : policy.artifactProfiles()) schemas.requirePublished(profile.schemaRef(), profile.schemaDocumentSha256());
    }

    DocumentUploadIssuance.Result create(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] bytes)
            throws IOException {
        json.headers(context, key); byte[] original = Objects.requireNonNull(bytes).clone();
        var request = preparation.request(original).read();
        return access.execute(subject, admission -> ThrowingSupplier.of(() ->
            issuance.issueCurrent(admission, context, key, original, () -> settings(admission, request))).get());
    }

    private DocumentUploadPreparation.Settings settings(EnterpriseAccess.Admission admission, InterfaceProtocol.UploadRequest request) throws IOException {
        var owners = permissions.resourceOwner("DOCUMENT", request.documentId());
        if (owners.size() != 1 || owners.get(0).projectId() == null) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        String project = owners.get(0).projectId();
        resources.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), project, "DOCUMENT", request.documentId()),
            "document.write", PURPOSE);
        policy.requireConfigured();
        resources.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), project, "PROJECT", project),
            "dependency.read", PURPOSE);
        var headers = dependencies.header(request.dependencyAccessSetId());
        if (headers.size() != 1 || !project.equals(headers.get(0).projectId())) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var header = headers.get(0);
        var graph = lineage.inspect(request.dependencyAccessSetId(), policy.limits());
        var root = graph.versions().get(request.dependencyAccessSetId());
        if (root == null || !project.equals(root.projectId()) || !header.manifestSha256().equals(header.contentSha256())
                || !header.contentSha256().equals(root.contentSha256()))
            throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        var requirements = new LinkedHashMap<String,ArtifactRegistryProtocol.ReadRequirement>();
        for (var version : graph.versions().values()) {
            if (!"ARTIFACT".equals(version.entityKind())) continue;
            var bindings = artifacts.binding(version.id());
            if (bindings.size() != 1) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            var binding = bindings.get(0);
            var profiles = policy.artifactProfiles().stream().filter(profile -> profile.resourceKind().equals(binding.resourceKind())
                && profile.schemaRef().equals(binding.schemaRef())).toList();
            if (profiles.size() != 1) throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
            var profile = profiles.get(0);
            var scope = new PermissionProtocol.Scope(admission.context().tenant_id(), binding.projectId(), profile.resourceKind(), binding.resourceId());
            json.scope(json.tree(scope));
            requirements.put(version.id(), new ArtifactRegistryProtocol.ReadRequirement(scope, profile.action(), PURPOSE,
                profile.schemaRef(), profile.schemaDocumentSha256(), profile.artifactSchemaManifest(), policy.limits()));
        }
        return new DocumentUploadPreparation.Settings(new DocumentUploadBasisProtocol.Settings(policy.environment(), policy.region(), policy.maximumSources()),
            policy.limits(), policy.artifactSchemaManifest(), policy.maximumBytes(), policy.expiresAfterSeconds(), requirements);
    }
}
