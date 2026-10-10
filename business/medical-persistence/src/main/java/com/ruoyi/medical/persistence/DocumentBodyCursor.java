package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

final class DocumentBodyCursor {
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final JOSEObjectType TYPE = new JOSEObjectType("medical-document-body+jwe");
    private static final String CONTENT_TYPE = "medical-document-body-v1";
    private static final Set<String> FIELDS = Set.of("schema_version", "authorization_sha256", "selection_sha256", "start", "limit", "issued_at", "expires_at");
    private final DocumentReadPolicy policy;
    private final byte[] key;
    DocumentBodyCursor(DocumentReadPolicy policy) {
        policy.requireConfigured(); this.policy = policy; key = Base64.getUrlDecoder().decode(policy.cursorKey());
    }
    record Position(int start, int limit, Instant issuedAt, Instant expiresAt) { }
    Position position(String cursor, String authorization, String selection, int limit, int count) throws Exception {
        WireValues.hash(authorization); WireValues.hash(selection);
        if (cursor == null) {
            var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            return new Position(0, limit, now, now.plus(policy.cursorLifetime()).truncatedTo(ChronoUnit.MICROS));
        }
        if (cursor.isEmpty() || cursor.length() > 8192) invalid();
        var encrypted = JWEObject.parse(cursor); var header = encrypted.getHeader();
        if (!JWEAlgorithm.DIR.equals(header.getAlgorithm()) || !EncryptionMethod.A256GCM.equals(header.getEncryptionMethod())
                || !TYPE.equals(header.getType()) || !CONTENT_TYPE.equals(header.getContentType())
                || !policy.cursorKeyId().equals(header.getKeyID())
                || !Set.of("alg", "enc", "typ", "cty", "kid").equals(header.getIncludedParams())) invalid();
        encrypted.decrypt(new DirectDecrypter(key));
        var value = JSON.readTree(encrypted.getPayload().toBytes());
        if (value == null || !value.isObject()) invalid();
        var fields = new HashSet<String>(); value.fieldNames().forEachRemaining(fields::add);
        if (!FIELDS.equals(fields)) invalid();
        for (var item : value) if (!item.isTextual()) invalid();
        if (!"1.0.0".equals(value.required("schema_version").textValue())
                || !authorization.equals(value.required("authorization_sha256").textValue())
                || !selection.equals(value.required("selection_sha256").textValue())) invalid();
        var result = new Position(WireValues.revision(value.required("start").textValue()).intValueExact(),
            WireValues.revision(value.required("limit").textValue()).intValueExact(),
            WireValues.utcTime(value.required("issued_at").textValue()), WireValues.utcTime(value.required("expires_at").textValue()));
        if (result.start() <= 0 || result.start() >= count || result.limit() != limit || result.start() % limit != 0) invalid();
        current(result); return result;
    }
    String next(Position original, int start, String authorization, String selection) throws Exception {
        current(original); var value = JSON.createObjectNode(); value.put("schema_version", "1.0.0");
        value.put("authorization_sha256", authorization); value.put("selection_sha256", selection);
        value.put("start", Integer.toString(start)); value.put("limit", Integer.toString(original.limit()));
        value.put("issued_at", original.issuedAt().toString()); value.put("expires_at", original.expiresAt().toString());
        var header = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM).type(TYPE).contentType(CONTENT_TYPE)
            .keyID(policy.cursorKeyId()).build();
        var encrypted = new JWEObject(header, new Payload(JSON.writeValueAsBytes(value)));
        encrypted.encrypt(new DirectEncrypter(key)); return encrypted.serialize();
    }
    void current(Position position) {
        var now = Instant.now();
        if (position.issuedAt().isAfter(now) || !position.expiresAt().isAfter(position.issuedAt())
                || !position.expiresAt().equals(position.issuedAt().plus(policy.cursorLifetime()).truncatedTo(ChronoUnit.MICROS))) invalid();
        if (!position.expiresAt().isAfter(now)) throw new TenantAccessException(ErrorCode.CURSOR_EXPIRED);
    }
    private static void invalid() { throw new TenantAccessException(ErrorCode.CURSOR_INVALID); }
}
