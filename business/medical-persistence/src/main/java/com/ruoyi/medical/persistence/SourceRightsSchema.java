package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

@Component
public final class SourceRightsSchema {
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema validator;
    private final String schemaManifest;
    private final JsonSchema pendingValidator;
    private final String pendingManifest;

    public SourceRightsSchema() throws IOException {
        byte[] document;
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("source-rights.schema.json"))) {
            document = input.readAllBytes();
        }
        validator = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(json.readTree(document),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        validator.initializeValidators();
        var manifest = json.createObjectNode(); manifest.put("schema_version", "1.0.0");
        manifest.put("source-rights.schema.json", DigestUtils.sha256Hex(document));
        schemaManifest = DigestUtils.sha256Hex(new JsonCanonicalizer(json.writeValueAsBytes(manifest)).getEncodedUTF8());
        byte[] pending;
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("source-rights-pending.schema.json"))) {
            pending=input.readAllBytes();
        }
        pendingValidator=JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(json.readTree(pending),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        pendingValidator.initializeValidators();
        var identity=json.createObjectNode(); identity.put("schema_version","1.0.0");
        identity.put("source-rights-pending.schema.json",DigestUtils.sha256Hex(pending));
        pendingManifest=DigestUtils.sha256Hex(new JsonCanonicalizer(json.writeValueAsBytes(identity)).getEncodedUTF8());
    }

    ObjectNode verify(SourceReadProtocol.RightsRow row) throws IOException {
        Objects.requireNonNull(row);
        if (!"1.0.0".equals(row.schemaVersion())) mismatch();
        boolean pending="PENDING_REVIEW".equals(row.status()) && pendingManifest.equals(row.schemaManifest());
        if(!pending && !schemaManifest.equals(row.schemaManifest())) mismatch();
        var body = json.createObjectNode(); body.put("schema_version", row.schemaVersion());
        body.put("tenant_id", row.tenantId()); body.put("rights_revision", row.rightsRevision());
        body.put("source_version", row.sourceVersion()); body.put("purpose", row.purpose());
        body.set("allowed_actions", json.readTree(row.allowedActions())); body.set("providers", json.readTree(row.providers()));
        body.set("regions", json.readTree(row.regions())); body.set("evidence_manifest", json.readTree(row.evidenceManifest()));
        time(body, "valid_from", row.validFrom()); time(body, "valid_to", row.validTo()); body.put("classification", row.classification());
        if (!(pending?pendingValidator:validator).validate(body).isEmpty()) mismatch();
        for (var evidence : body.required("evidence_manifest").required("authorization_evidence")) {
            WireValues.revision(evidence.required("size_bytes").textValue());
        }
        String digest = DigestUtils.sha256Hex(new JsonCanonicalizer(json.writeValueAsBytes(body)).getEncodedUTF8());
        if (!digest.equals(row.contentSha256()) || !digest.equals(row.registeredSha256())) mismatch();
        return body;
    }

    void require(SourceReadProtocol.RightsRow row, SourceReadProtocol.SourceRow source,
            SourceReadProtocol.ReadRequirement requirement, LocalDateTime now) throws IOException {
        Objects.requireNonNull(now);
        var body = verify(row);
        if (!source.tenantId().equals(row.tenantId()) || !source.sourceVersion().equals(row.sourceVersion())
                || !source.rightsRevision().equals(row.rightsRevision()) || !requirement.rightsRevision().equals(row.rightsRevision())
                || !Objects.equals(source.ownerProjectId(), row.registeredProjectId())) mismatch();
        if ("PENDING_REVIEW".equals(row.status())) throw new TenantAccessException(ErrorCode.RIGHTS_PENDING);
        if (!"APPROVED".equals(row.status()) || !requirement.purpose().equals(row.purpose())
                || row.validFrom() != null && row.validFrom().isAfter(now)
                || row.validTo() != null && !row.validTo().isAfter(now)
                || row.validFrom() != null && row.validTo() != null && !row.validTo().isAfter(row.validFrom())
                || !contains(body.required("allowed_actions"), "READ")
                || !contains(body.required("regions"), requirement.region())
                || !contains(body.required("evidence_manifest").required("environments"), requirement.environment())
                || !contains(body.required("evidence_manifest").required("classifications"), source.classification())) denied();
        if (requirement.use() != SourceReadProtocol.Use.REVIEW && !contains(body.required("allowed_actions"), "ENTERPRISE_USE")) denied();
        if (requirement.use() == SourceReadProtocol.Use.MODEL_SEND && (!contains(body.required("allowed_actions"), "EXTERNAL_PROCESS")
                || !contains(body.required("providers"), requirement.provider()))) denied();
    }

    String manifestSha256() { return schemaManifest; }
    String pendingManifestSha256() { return pendingManifest; }
    void requireEnterpriseUse(SourceReadProtocol.RightsRow row) throws IOException {
        var body=verify(row);
        if("PENDING_REVIEW".equals(row.status())) throw new TenantAccessException(ErrorCode.RIGHTS_PENDING);
        if (!"APPROVED".equals(row.status()) || !contains(body.required("allowed_actions"), "ENTERPRISE_USE")) denied();
    }
    private static boolean contains(JsonNode array, String value) {
        for (var item : array) if (value.equals(item.textValue())) return true;
        return false;
    }
    private static void time(ObjectNode body, String field, LocalDateTime value) {
        if (value == null) body.putNull(field); else body.put(field, value.toInstant(ZoneOffset.UTC).toString());
    }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
}
