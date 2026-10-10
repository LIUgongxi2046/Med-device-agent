package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.erdtman.jcs.JsonCanonicalizer;

final class ContentReadAuthorization {
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .addModule(new SimpleModule().addSerializer(BigInteger.class, ToStringSerializer.instance)
            .addSerializer(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
                @Override public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider provider) throws IOException {
                    generator.writeString(value.toInstant(ZoneOffset.UTC).toString());
                }
            })).build();
    private ContentReadAuthorization() { }

    static String digest(EnterpriseAccess.Admission admission, ContentReadProtocol.ReadIntent intent,
            ArtifactSourceAuthority.VerifiedSources verified) throws IOException, NoSuchAlgorithmException {
        TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.ContentReadMapper.header");
        if (!admission.context().context_revision().equals(intent.contextRevision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var value = new LinkedHashMap<String, Object>(); value.put("schema_version", "1.0.0");
        value.put("subject_id", admission.subject().user().user_id()); value.put("session_id", admission.subject().sessionId());
        value.put("identity_binding", admission.subject().binding().view()); value.put("context_id", admission.contextId());
        value.put("context", admission.context()); value.put("watermarks", admission.watermarks()); value.put("intent", intent);
        value.put("registration", registration(verified.registration())); value.put("sources", verified.sources());
        var versions = new LinkedHashMap<String, Object>();
        for (var item : verified.versions().entrySet()) {
            var version = item.getValue(); var entry = new LinkedHashMap<String, Object>();
            entry.put("version", version.version()); entry.put("domain", version.domain()); entry.put("owner", version.owner());
            entry.put("resource_grant", version.resourceGrant()); entry.put("version_project_grant", version.versionProjectGrant());
            entry.put("artifact", version.artifactRegistration() == null ? null : registration(version.artifactRegistration()));
            versions.put(item.getKey(), entry);
        }
        value.put("versions", versions);
        return WireValues.sha256(canonicalBytes(value));
    }

    static Map<String, Object> registration(ArtifactRegistryProtocol.Registration registered) {
        var value = new LinkedHashMap<String, Object>(); value.put("artifact", registered.artifact());
        value.put("binding", registered.binding()); value.put("manifest", registered.manifest());
        value.put("artifact_grant", registered.artifactGrant()); value.put("resource_grant", registered.resourceGrant());
        value.put("lineage_root", registered.lineage().root()); value.put("lineage_versions", registered.lineage().versions());
        value.put("lineage_relations", registered.lineage().graph().edgeSet().stream()
            .sorted(Comparator.comparing(SourceReadProtocol.ParentRow::consumerVersionId)
                .thenComparing(SourceReadProtocol.ParentRow::upstreamVersionId).thenComparing(SourceReadProtocol.ParentRow::relationKind)).toList());
        return value;
    }

    static byte[] canonicalBytes(Object value) throws IOException {
        return new JsonCanonicalizer(JSON.writeValueAsBytes(value)).getEncodedUTF8();
    }

    static boolean sameJson(String expected, String actual) throws IOException {
        return JSON.readTree(expected).equals(JSON.readTree(actual));
    }
}
