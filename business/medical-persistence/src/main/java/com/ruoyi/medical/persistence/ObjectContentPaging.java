package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.ruoyi.medical.contracts.CommonProtocol.Coverage;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.Encoding;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.erdtman.jcs.JsonCanonicalizer;

public final class ObjectContentPaging {
    private static final String CONTENT_TYPE = "medical-object-page-v1";
    private static final JOSEObjectType TYPE = new JOSEObjectType("medical-object-page+jwe");
    private static final Set<String> HEADER_FIELDS = Set.of("alg", "enc", "typ", "cty", "kid");
    private static final Set<String> CURSOR_FIELDS = Set.of("schema_version", "authorization_sha256", "object_ref_sha256",
        "selection_sha256", "start_byte", "limit_bytes", "issued_at", "expires_at");
    private static final int MAX_CURSOR_CHARACTERS = 8192;
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final S3ObjectReader reader;
    private final SecretKey key;
    private final String keyId;
    private final Duration cursorLifetime;
    private final int maximumChunkBytes;

    public ObjectContentPaging(S3ObjectReader reader, SecretKey key, String keyId, Duration cursorLifetime, int maximumChunkBytes) {
        this.reader = Objects.requireNonNull(reader); Objects.requireNonNull(key); WireValues.uuid(keyId);
        if (!"AES".equals(key.getAlgorithm()) || key.getEncoded() == null || key.getEncoded().length != 32)
            throw new IllegalArgumentException("内容游标需要独立AES-256密钥");
        if (cursorLifetime == null || cursorLifetime.isNegative() || cursorLifetime.isZero()
                || maximumChunkBytes <= 0 || maximumChunkBytes > S3ObjectReader.MAX_SEGMENT_BYTES)
            throw new IllegalArgumentException("游标有效期和内容分片容量必须完整配置");
        this.key = new SecretKeySpec(key.getEncoded(), "AES"); this.keyId = keyId;
        this.cursorLifetime = cursorLifetime; this.maximumChunkBytes = maximumChunkBytes;
    }

    public Page read(ObjectRef reference, Supplier<String> currentAuthorization, int limitBytes, String cursor) throws Exception {
        Objects.requireNonNull(reference); Objects.requireNonNull(currentAuthorization);
        if (limitBytes <= 0 || limitBytes > maximumChunkBytes) throw new IllegalArgumentException("内容分片超出已配置范围");
        long size = WireValues.revision(reference.sizeBytes()).longValueExact();
        if (size <= 0) throw new IllegalArgumentException("内容对象必须具有实际字节");
        String authorization = Objects.requireNonNull(currentAuthorization.get()); WireValues.hash(authorization);
        String objectDigest = objectDigest(reference);
        String selection = rangeDigest(objectDigest, 0, size);
        Position position = cursor == null ? initial(authorization, objectDigest, selection, limitBytes)
            : decode(cursor, authorization, objectDigest, selection, limitBytes, size);
        S3ObjectReader.Admission admission = () -> {
            String current = Objects.requireNonNull(currentAuthorization.get()); WireValues.hash(current);
            if (!authorization.equals(current)) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            currentTime(position);
        };
        admission.requireCurrent();
        try (var verified = reader.readVerified(reference, admission)) {
            int length = (int) Math.min(limitBytes, size - position.start());
            long end = position.start() + length;
            byte[] bytes = verified.chunk(position.start(), length);
            String chunkDigest = WireValues.sha256(bytes);
            String delivered = rangeDigest(objectDigest, position.start(), end);
            String next = end == size ? null : encode(new Position(authorization, objectDigest, selection, end,
                limitBytes, position.issuedAt(), position.expiresAt()));
            var coverage = new Coverage(selection, delivered, end == size, next);
            var page = new Page(Encoding.BASE64, Base64.getEncoder().encodeToString(bytes), reference.mediaType(),
                chunkDigest, verified.fullSha256(), Long.toString(position.start()), Long.toString(end), coverage);
            // 返回前继续使用原准入；当前主体、水位、来源和固定对象由业务消费者共同绑定。
            admission.requireCurrent();
            return page;
        }
    }

    public byte[] readComplete(ObjectRef reference, Supplier<String> currentAuthorization, int limitBytes,
                               int maximumContentBytes) throws Exception {
        Objects.requireNonNull(currentAuthorization);
        var assembly = new ObjectContentAssembly(reference, maximumContentBytes);
        String authorization = Objects.requireNonNull(currentAuthorization.get()); WireValues.hash(authorization);
        Supplier<String> fixedAuthorization = () -> {
            String current = Objects.requireNonNull(currentAuthorization.get()); WireValues.hash(current);
            if (!authorization.equals(current)) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            return current;
        };
        String cursor = null;
        do {
            Page page = read(reference, fixedAuthorization, limitBytes, cursor);
            assembly.accept(page);
            cursor = page.coverage().nextCursor();
        } while (cursor != null);
        byte[] content = assembly.content();
        fixedAuthorization.get();
        return content;
    }

    private Position initial(String authorization, String objectDigest, String selection, int limit) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant expiry = now.plus(cursorLifetime).truncatedTo(ChronoUnit.MICROS);
        if (!expiry.isAfter(now)) throw new IllegalArgumentException("游标有效期必须能够使用微秒时间表示");
        return new Position(authorization, objectDigest, selection, 0, limit, now, expiry);
    }

    private Position decode(String cursor, String authorization, String objectDigest, String selection, int limit, long size) throws Exception {
        if (cursor.isEmpty() || cursor.length() > MAX_CURSOR_CHARACTERS) invalidCursor();
        var encrypted = JWEObject.parse(cursor);
        var header = encrypted.getHeader();
        if (!JWEAlgorithm.DIR.equals(header.getAlgorithm()) || !EncryptionMethod.A256GCM.equals(header.getEncryptionMethod())
                || !TYPE.equals(header.getType()) || !CONTENT_TYPE.equals(header.getContentType())
                || !keyId.equals(header.getKeyID()) || !HEADER_FIELDS.equals(header.getIncludedParams())) invalidCursor();
        encrypted.decrypt(new DirectDecrypter(key));
        JsonNode value = JSON.readTree(encrypted.getPayload().toBytes());
        if (value == null || !value.isObject()) invalidCursor();
        var fields = new HashSet<String>(); value.fieldNames().forEachRemaining(fields::add);
        if (!CURSOR_FIELDS.equals(fields)) invalidCursor();
        for (JsonNode field : value) if (!field.isTextual()) invalidCursor();
        if (!"1.0.0".equals(value.required("schema_version").textValue())
                || !authorization.equals(value.required("authorization_sha256").textValue())
                || !objectDigest.equals(value.required("object_ref_sha256").textValue())
                || !selection.equals(value.required("selection_sha256").textValue())) invalidCursor();
        long start = WireValues.revision(value.required("start_byte").textValue()).longValueExact();
        int savedLimit = WireValues.revision(value.required("limit_bytes").textValue()).intValueExact();
        if (start <= 0 || start >= size || savedLimit != limit || start % limit != 0) invalidCursor();
        var position = new Position(authorization, objectDigest, selection, start, savedLimit,
            WireValues.utcTime(value.required("issued_at").textValue()), WireValues.utcTime(value.required("expires_at").textValue()));
        currentTime(position);
        return position;
    }

    private void currentTime(Position position) {
        Instant now = Instant.now();
        if (position.issuedAt().isAfter(now) || !position.expiresAt().isAfter(position.issuedAt())
                || !position.expiresAt().equals(position.issuedAt().plus(cursorLifetime).truncatedTo(ChronoUnit.MICROS))) invalidCursor();
        if (!position.expiresAt().isAfter(now)) throw new TenantAccessException(ErrorCode.CURSOR_EXPIRED);
    }

    private String encode(Position position) throws Exception {
        var value = JSON.createObjectNode(); value.put("schema_version", "1.0.0");
        value.put("authorization_sha256", position.authorization()); value.put("object_ref_sha256", position.objectDigest());
        value.put("selection_sha256", position.selection()); value.put("start_byte", Long.toString(position.start()));
        value.put("limit_bytes", Integer.toString(position.limit())); value.put("issued_at", position.issuedAt().toString());
        value.put("expires_at", position.expiresAt().toString());
        var header = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM)
            .type(TYPE).contentType(CONTENT_TYPE).keyID(keyId).build();
        var encrypted = new JWEObject(header, new Payload(JSON.writeValueAsBytes(value)));
        encrypted.encrypt(new DirectEncrypter(key));
        return encrypted.serialize();
    }

    static String objectDigest(ObjectRef reference) throws Exception {
        var value = JSON.createObjectNode(); value.put("object_key", reference.objectKey()); value.put("object_version", reference.objectVersion());
        value.put("sha256", reference.sha256()); value.put("size_bytes", reference.sizeBytes()); value.put("media_type", reference.mediaType());
        return digest(value);
    }

    static String rangeDigest(String objectDigest, long start, long end) throws Exception {
        var value = JSON.createObjectNode(); value.put("schema_version", "1.0.0"); value.put("object_ref_sha256", objectDigest);
        value.put("start_byte", Long.toString(start)); value.put("end_byte", Long.toString(end));
        return digest(value);
    }

    private static String digest(JsonNode value) throws Exception {
        return WireValues.sha256(new JsonCanonicalizer(JSON.writeValueAsBytes(value)).getEncodedUTF8());
    }

    private static void invalidCursor() { throw new TenantAccessException(ErrorCode.CURSOR_INVALID); }
    private record Position(String authorization, String objectDigest, String selection, long start, int limit, Instant issuedAt, Instant expiresAt) { }
    public record Page(Encoding encoding, String content, String mediaType, String chunkSha256, String fullSha256,
                       String startByte, String endByte, Coverage coverage) { }
}
