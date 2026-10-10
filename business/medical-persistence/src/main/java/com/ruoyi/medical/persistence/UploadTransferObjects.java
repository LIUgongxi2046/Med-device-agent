package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class UploadTransferObjects {
    private static final Set<String> FIELDS = Set.of("key", "version_id", "sha256", "size_bytes", "media_type");
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();

    private UploadTransferObjects() { }

    // 上传物理表的五字段沿用其原始定义，接口仍使用ObjectRef的原字段。
    static String encode(ObjectRef reference) throws IOException {
        Objects.requireNonNull(reference);
        StoredObjectReferences.encode(reference);
        if ("null".equals(reference.objectVersion())) mismatch();
        var value = JSON.createObjectNode(); value.put("key", reference.objectKey());
        value.put("version_id", reference.objectVersion()); value.put("sha256", reference.sha256());
        value.put("size_bytes", WireValues.revision(reference.sizeBytes())); value.put("media_type", reference.mediaType());
        return JSON.writeValueAsString(value);
    }

    static ObjectRef decode(String stored, ObjectRef columns) throws IOException {
        var expected = JSON.readTree(encode(columns));
        var value = JSON.readTree(Objects.requireNonNull(stored));
        if (value == null || !value.isObject()) mismatch();
        var fields = new HashSet<String>(); value.fieldNames().forEachRemaining(fields::add);
        if (!FIELDS.equals(fields) || !value.required("size_bytes").isIntegralNumber()) mismatch();
        for (String field : Set.of("key", "version_id", "sha256", "media_type"))
            if (!value.required(field).isTextual()) mismatch();
        if (!expected.equals(value)) mismatch();
        return columns;
    }

    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
