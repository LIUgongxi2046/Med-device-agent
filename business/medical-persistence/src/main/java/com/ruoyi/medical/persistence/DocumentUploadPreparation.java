package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceBindings;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.DependencyAccessMapper;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class DocumentUploadPreparation {
    private static final String PURPOSE = "DOCUMENT_BODY";
    private static final String CLOCK = "com.ruoyi.medical.persistence.mapper.UploadTransferMapper.databaseTime";
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();
    private final DocumentUploadBasisAuthority bases;
    private final DependencyAccessMapper dependencies;
    private final UploadAuthorizationAuthority authority;
    private final UploadTransferMapper uploads;
    private final UploadAuthorizationSchema authorizations;
    private final SchemaCatalogue schemas;

    public DocumentUploadPreparation(DocumentUploadBasisAuthority bases, DependencyAccessMapper dependencies,
            UploadAuthorizationAuthority authority, UploadTransferMapper uploads, UploadAuthorizationSchema authorizations)
            throws IOException, NoSuchAlgorithmException {
        this.bases = Objects.requireNonNull(bases); this.dependencies = Objects.requireNonNull(dependencies);
        this.authority = Objects.requireNonNull(authority); this.uploads = Objects.requireNonNull(uploads);
        this.authorizations = Objects.requireNonNull(authorizations); schemas = SchemaCatalogue.fromClasspath();
    }

    public record Settings(DocumentUploadBasisProtocol.Settings basis, DependencyLineage.Limits lineageLimits,
            String artifactSchemaManifest, BigInteger maximumBytes, long expiresAfterSeconds,
            Map<String, ArtifactRegistryProtocol.ReadRequirement> upstreamArtifacts) {
        public Settings {
            if (basis == null || lineageLimits == null || artifactSchemaManifest == null || maximumBytes == null
                    || upstreamArtifacts == null || maximumBytes.signum() <= 0
                    || maximumBytes.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 || expiresAfterSeconds <= 0)
                throw new IllegalArgumentException("许可准备必须具有明确服务器依据、完整读取要求、发布摘要、容量和期限");
            WireValues.hash(artifactSchemaManifest); upstreamArtifacts = Map.copyOf(upstreamArtifacts);
            for (var entry : upstreamArtifacts.entrySet()) {
                WireValues.uuid(entry.getKey()); var required = entry.getValue();
                if (!PURPOSE.equals(required.purpose())) throw new IllegalArgumentException("上游领域读取要求必须属于文稿上传用途");
                Objects.requireNonNull(required.scope()); Objects.requireNonNull(required.lineageLimits());
            }
        }
    }

    TypedSchemaBindings.Binding<InterfaceProtocol.UploadRequest> request(byte[] bytes) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Objects.requireNonNull(bytes))).toString();
        var value = JSON.readTree(text); if (value == null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        // Syntax selection recognizes only explicitly published request shapes. Authorization below
        // independently binds the requested shape to the registered current body schema.
        var definition = schemas.schemaRef("document-body-v2", "DocumentBody").equals(value.path("schema_ref").asText())
            ? InterfaceBindings.UPLOAD_REQUEST_V2 : InterfaceBindings.UPLOAD_REQUEST;
        return TypedSchemaBindings.bind(schemas, definition, value);
    }

    Prepared prepare(EnterpriseAccess.Admission admission, byte[] bytes, Settings settings) throws IOException, NoSuchAlgorithmException {
        if (admission == null) denied(); TenantScope.requireSqlAccess(CLOCK); Objects.requireNonNull(settings);
        if (!TenantScope.requireCurrent().tenantId().equals(admission.context().tenant_id())) denied();
        var payload = request(bytes); var request = payload.read(); var size = WireValues.revision(request.sizeBytes());
        if (size.signum() == 0) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        if (size.compareTo(settings.maximumBytes()) > 0) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        var basis = bases.require(admission, request.documentId(), request.expectedRevision(), settings.basis());
        var published = authority.requireBaselinePublication(admission, basis.document());
        String requestedRef = "interfaces.schema.yaml#/$defs/DocumentBody".equals(request.schemaRef())
            ? schemas.schemaRef("interfaces", "DocumentBody") : request.schemaRef();
        if (!published.schemaRef().equals(requestedRef) || !published.schemaSha256().equals(payload.payload().schemaSha256())) mismatch();
        var headers = dependencies.header(request.dependencyAccessSetId()); if (headers.size() != 1) denied(); var header = headers.get(0);
        String tenant = admission.context().tenant_id(), subject = admission.subject().user().user_id(), project = basis.document().projectId();
        if (!tenant.equals(header.tenantId()) || !project.equals(header.projectId()) || !request.dependencyAccessSetId().equals(header.accessSetId())
                || !header.manifestSha256().equals(header.contentSha256())) mismatch();
        var manifest = new UploadAuthorizationProtocol.Manifest("1.0.0", tenant, subject, project, PURPOSE,
            admission.watermarks().authEpoch().toString(), admission.watermarks().governanceEpoch().toString(),
            published.schemaRef(), published.schemaSha256(), settings.artifactSchemaManifest(), header.accessSetId(), header.manifestSha256(),
            new DependencyAccessProtocol.ReadRequirement(project, PURPOSE, settings.lineageLimits()), basis.sources(),
            new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.FORMAL, null), settings.upstreamArtifacts(),
            DocumentUploadBasisAuthority.bind(basis, settings.basis()));
        // 原固定清单完成全部当前领域授权核验以后，才分配新的技术身份。
        var verified = authority.requireDocument(admission, request.documentId(), request.expectedRevision(), manifest);
        var encoded = authorizations.encode(manifest);
        var clock = Map.<String,Object>of("userId", subject); TenantScope.requireSubjectParameter(CLOCK, clock);
        var now = Objects.requireNonNull(uploads.databaseTime(clock));
        if (now.getNano() % 1000 != 0) mismatch();
        var expires = deadline(now.plusSeconds(settings.expiresAfterSeconds()), verified);
        if (!expires.isAfter(now)) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        if (expires.getYear() > 9999) throw new IllegalArgumentException("许可期限超出数据库时间范围");
        String grantId = UUID.randomUUID().toString(), uploadId = UUID.randomUUID().toString();
        String key = "artifact-uploads/" + tenant + "/" + uploadId + "/original";
        String classification = verified.document().classification(), schemaManifest = authorizations.manifestSha256();
        var grant = new UploadTransferProtocol.GrantRow(tenant, project, grantId, subject, PURPOSE, request.documentId(), null, null,
            key, null, null, request.sha256(), size, request.mediaType(), published.schemaRef(), expires, "ISSUED", null,
            subject, now, classification, BigInteger.ONE, now);
        var session = new UploadTransferProtocol.SessionRow(tenant, project, uploadId, subject, PURPOSE, null, grantId, size,
            request.sha256(), BigInteger.ZERO, request.mediaType(), key, WireValues.sha256(key.getBytes(StandardCharsets.UTF_8)),
            UploadTransferProtocol.PART_BYTES, expires, "ACTIVE", null, null, null, "1.0.0", schemaManifest,
            subject, now, classification, BigInteger.ONE, now);
        var binding = new UploadTransferProtocol.BindingRow(tenant, project, grantId, uploadId, subject, PURPOSE, request.documentId(),
            null, null, WireValues.revision(request.expectedRevision()), WireValues.revision(admission.context().context_revision()),
            null, null, header.accessSetId(), header.manifestSha256(), published.schemaSha256(), encoded.body(), encoded.sha256(),
            "1.0.0", schemaManifest, subject, now, classification);
        return new Prepared(payload, verified, grant, session, binding);
    }

    private static LocalDateTime deadline(LocalDateTime proposed, UploadAuthorizationAuthority.VerifiedDocument verified) {
        var result = earlier(proposed, verified.resourceGrant().validTo());
        for (var source : verified.dependencies().sources().values()) {
            result = earlier(result, source.rights().validTo()); result = earlier(result, source.sourceGrant().validTo());
            if (source.projectGrant() != null) result = earlier(result, source.projectGrant().validTo());
            if (source.externalGrant() != null) result = earlier(result, source.externalGrant().validTo());
        }
        for (var version : verified.dependencies().versions().values()) {
            result = earlier(result, version.resourceGrant().validTo());
            if (version.versionProjectGrant() != null) result = earlier(result, version.versionProjectGrant().validTo());
            if (version.artifactRegistration() != null) {
                result = earlier(result, version.artifactRegistration().artifactGrant().validTo());
                result = earlier(result, version.artifactRegistration().resourceGrant().validTo());
            }
        }
        return result;
    }

    private static LocalDateTime earlier(LocalDateTime proposed, LocalDateTime boundary) {
        return boundary != null && boundary.isBefore(proposed) ? boundary : proposed;
    }

    // 准备结果仍需与幂等operation、审计及Outbox在同一EnterpriseAccess事务保存并重读。
    record Prepared(TypedSchemaBindings.Binding<InterfaceProtocol.UploadRequest> request,
            UploadAuthorizationAuthority.VerifiedDocument authority, UploadTransferProtocol.GrantRow grant,
            UploadTransferProtocol.SessionRow session, UploadTransferProtocol.BindingRow binding) { }

    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
