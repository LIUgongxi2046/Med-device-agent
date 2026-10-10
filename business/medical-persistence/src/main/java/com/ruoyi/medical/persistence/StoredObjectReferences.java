package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class StoredObjectReferences {
    private static final Set<String> FIELDS = Set.of("object_key", "object_version", "sha256", "size_bytes", "media_type");
    private static final List<String> TEXT_FIELDS = List.of("object_key", "object_version", "sha256", "media_type");
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();

    private StoredObjectReferences() { }

    public static String encode(ObjectRef reference) throws IOException {
        requirePhysicalReference(reference);
        var value = JSON.createObjectNode(); value.put("object_key", reference.objectKey());
        value.put("object_version", reference.objectVersion()); value.put("sha256", reference.sha256());
        // MySQL既有JSON约束使用精确数值；对外U64继续保持原十进制字符串。
        value.put("size_bytes", WireValues.revision(reference.sizeBytes())); value.put("media_type", reference.mediaType());
        return JSON.writeValueAsString(value);
    }

    public static ObjectRef decode(String stored, ObjectRef columns) throws IOException {
        requirePhysicalReference(columns); Objects.requireNonNull(stored);
        var value = JSON.readTree(stored);
        if (value == null || !value.isObject()) mismatch();
        var fields = new HashSet<String>(); value.fieldNames().forEachRemaining(fields::add);
        if (!FIELDS.equals(fields)) mismatch();
        for (String field : TEXT_FIELDS) if (!value.required(field).isTextual()) mismatch();
        var size = value.required("size_bytes");
        if (!size.isIntegralNumber() || !size.bigIntegerValue().equals(WireValues.revision(columns.sizeBytes()))
                || !value.required("object_key").textValue().equals(columns.objectKey())
                || !value.required("object_version").textValue().equals(columns.objectVersion())
                || !value.required("sha256").textValue().equals(columns.sha256())
                || !value.required("media_type").textValue().equals(columns.mediaType())) mismatch();
        return columns;
    }

    private static void requirePhysicalReference(ObjectRef reference) throws IOException {
        Objects.requireNonNull(reference);
        if (reference.objectKey().isEmpty() || reference.objectVersion().isEmpty() || reference.mediaType().isEmpty()
                || WireValues.revision(reference.sizeBytes()).signum() <= 0) mismatch();
        for (String field : List.of(reference.objectKey(), reference.objectVersion(), reference.sha256(), reference.mediaType()))
            StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(field));
    }

    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
