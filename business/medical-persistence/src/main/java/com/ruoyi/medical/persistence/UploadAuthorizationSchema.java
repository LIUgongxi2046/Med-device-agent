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
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

@Component
public final class UploadAuthorizationSchema {
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema validator;
    private final String schemaManifest;

    public UploadAuthorizationSchema() throws IOException {
        byte[] document;
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("upload-authorization.schema.json"))) {
            document = input.readAllBytes();
        }
        validator = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(json.readTree(document),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        validator.initializeValidators();
        var manifest = json.createObjectNode(); manifest.put("schema_version", "1.0.0");
        manifest.put("upload-authorization.schema.json", DigestUtils.sha256Hex(document));
        schemaManifest = DigestUtils.sha256Hex(new JsonCanonicalizer(json.writeValueAsBytes(manifest)).getEncodedUTF8());
    }

    UploadAuthorizationProtocol.Manifest decode(String body, String sha256) throws IOException {
        WireValues.hash(sha256); var value = json.readTree(Objects.requireNonNull(body));
        if (value == null || !validator.validate(value).isEmpty()) mismatch();
        byte[] canonical = new JsonCanonicalizer(json.writeValueAsBytes(value)).getEncodedUTF8();
        if (!DigestUtils.sha256Hex(canonical).equals(sha256)) mismatch();
        return json.treeToValue(value, UploadAuthorizationProtocol.Manifest.class);
    }

    Encoded encode(UploadAuthorizationProtocol.Manifest manifest) throws IOException {
        Objects.requireNonNull(manifest); var value = json.valueToTree(manifest);
        if (!validator.validate(value).isEmpty()) mismatch();
        byte[] canonical = new JsonCanonicalizer(json.writeValueAsBytes(value)).getEncodedUTF8();
        String body = new String(canonical, StandardCharsets.UTF_8); String sha256 = DigestUtils.sha256Hex(canonical);
        if (!manifest.equals(decode(body, sha256))) mismatch();
        return new Encoded(body, sha256);
    }

    String manifestSha256() { return schemaManifest; }

    UploadAuthorizationProtocol.Manifest requireBinding(UploadTransferProtocol.BindingRow binding,
            UploadTransferProtocol.GrantRow grant) throws IOException {
        Objects.requireNonNull(binding); Objects.requireNonNull(grant);
        if (!"1.0.0".equals(binding.schemaVersion()) || !schemaManifest.equals(binding.schemaManifestSha256())) mismatch();
        var manifest = decode(binding.authorizationManifest(), binding.authorizationSha256());
        if (!binding.tenantId().equals(manifest.tenantId()) || !binding.subjectId().equals(manifest.subjectId())
                || !Objects.equals(binding.projectId(), manifest.projectId()) || !binding.purpose().equals(manifest.purpose())
                || !binding.schemaDocumentSha256().equals(manifest.schemaDocumentSha256())
                || !binding.dependencyAccessSetId().equals(manifest.dependencyAccessSetId())
                || !binding.dependencyManifestSha256().equals(manifest.dependencyManifestSha256())
                || !grant.schemaRef().equals(manifest.schemaRef()) || !binding.grantId().equals(grant.grantId())
                || !binding.tenantId().equals(grant.tenantId()) || !binding.subjectId().equals(grant.subjectId())
                || !Objects.equals(binding.projectId(), grant.projectId()) || !binding.purpose().equals(grant.purpose())
                || !Objects.equals(binding.documentId(), grant.documentId()) || !Objects.equals(binding.runId(), grant.runId())
                || !Objects.equals(binding.stepId(), grant.stepId())) mismatch();
        return manifest;
    }

    record Encoded(String body, String sha256) { }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
