package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.erdtman.jcs.JsonCanonicalizer;

/** Closed typed request binder; no permissive ObjectNode-to-domain coercion. */
final class StrictComparisonCodec {
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final int maximumBytes;
    StrictComparisonCodec(int maximumBytes) { if (maximumBytes < 1) throw new IllegalArgumentException("CAPACITY_REQUIRED"); this.maximumBytes = maximumBytes; }
    <T> T bind(byte[] bytes, Class<T> type) throws Exception {
        if (bytes.length > maximumBytes) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        return mapper.readValue(bytes, type);
    }
    <T> T bind(JsonNode value, Class<T> type) throws Exception { return bind(canonical(value).getBytes(StandardCharsets.UTF_8), type); }
    JsonNode read(String value) throws Exception { return bind(value.getBytes(StandardCharsets.UTF_8), JsonNode.class); }
    JsonNode tree(Object value) { return mapper.valueToTree(value); }
    String canonical(Object value) throws Exception { return new JsonCanonicalizer(mapper.writeValueAsBytes(value)).getEncodedString(); }
    String hash(Object value) throws Exception { return WireValues.sha256(canonical(value).getBytes(StandardCharsets.UTF_8)); }
    static StrictComparisonPolicyDefinition.Version implementation(Class<?> type, String revision) throws Exception {
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        classBytes(type,digest);
        String hash=java.util.HexFormat.of().formatHex(digest.digest());
        String id=java.util.UUID.nameUUIDFromBytes((revision+":"+hash).getBytes(StandardCharsets.UTF_8)).toString();
        return new StrictComparisonPolicyDefinition.Version(id,hash);
    }
    private static void classBytes(Class<?> type,java.security.MessageDigest digest) throws Exception {
        digest.update(type.getName().getBytes(StandardCharsets.UTF_8));digest.update((byte)0);
        try(var input=Objects.requireNonNull(type.getResourceAsStream("/"+type.getName().replace('.','/')+".class"))){digest.update(input.readAllBytes());}
        for(var nested:java.util.Arrays.stream(type.getDeclaredClasses()).sorted(java.util.Comparator.comparing(Class::getName)).toList())classBytes(nested,digest);
    }
}
