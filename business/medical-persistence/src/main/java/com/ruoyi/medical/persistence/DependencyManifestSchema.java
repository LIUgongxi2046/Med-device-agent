package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

@Component
public final class DependencyManifestSchema {
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema validator;
    private final String schemaManifest;

    public DependencyManifestSchema() throws IOException {
        byte[] document;
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("dependency-access-manifest.schema.json"))) {
            document = input.readAllBytes();
        }
        validator = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(json.readTree(document),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        validator.initializeValidators();
        var manifest = json.createObjectNode(); manifest.put("schema_version", "1.0.0");
        manifest.put("dependency-access-manifest.schema.json", DigestUtils.sha256Hex(document));
        schemaManifest = DigestUtils.sha256Hex(new JsonCanonicalizer(json.writeValueAsBytes(manifest)).getEncodedUTF8());
    }

    ArtifactRegistryProtocol.DependencyManifest verify(ArtifactRegistryProtocol.ManifestRow row,
            List<ArtifactRegistryProtocol.MemberRow> members) throws IOException {
        Objects.requireNonNull(row); Objects.requireNonNull(members);
        if (!"1.0.0".equals(row.schemaVersion()) || !schemaManifest.equals(row.schemaManifest())) mismatch();
        var node = json.readTree(row.storedManifest());
        if (node == null || !validator.validate(node).isEmpty()) mismatch();
        var manifest = json.treeToValue(node, ArtifactRegistryProtocol.DependencyManifest.class);
        if (!row.tenantId().equals(manifest.tenant_id()) || !Objects.equals(row.projectId(), manifest.project_id())
                || !row.accessSetId().equals(manifest.access_set_id()) || !manifest.members().equals(members)
                || !WireValues.revision(row.memberCount().toString()).equals(java.math.BigInteger.valueOf(members.size()))) mismatch();
        String previous = null;
        for (var member : manifest.members()) {
            // 数组次序是清单身份的一部分；同版本不同权利也不能登记为两个成员。
            if (previous != null && previous.compareTo(member.upstreamVersionId()) >= 0) mismatch();
            previous = member.upstreamVersionId();
        }
        String digest = DigestUtils.sha256Hex(new JsonCanonicalizer(json.writeValueAsBytes(node)).getEncodedUTF8());
        if (!digest.equals(row.manifestSha256()) || !digest.equals(row.contentSha256())) mismatch();
        return manifest;
    }

    String manifestSha256() { return schemaManifest; }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
