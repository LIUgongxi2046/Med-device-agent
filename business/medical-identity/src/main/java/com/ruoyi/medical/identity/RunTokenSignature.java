package com.ruoyi.medical.identity;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;

public final class RunTokenSignature {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> HEADER = Set.of("alg", "typ", "kid");
    private static final Set<String> CLAIMS = Set.of("iss", "iat", "exp", "aud", "jti", "tenant_id", "project_id",
        "initiator_id", "run_id", "step_id", "action_scope", "auth_epoch");

    private RunTokenSignature() { }

    public static Verified verify(String raw, Policy current) throws IOException, ParseException, JOSEException {
        Objects.requireNonNull(raw); Objects.requireNonNull(current);
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(raw)
                || raw.getBytes(StandardCharsets.UTF_8).length > current.maximumBytes()) invalid();
        var jwt = SignedJWT.parse(raw);
        var parts = jwt.getParsedParts();
        JsonNode header = JSON.readTree(parts[0].decode()); exactFields(header, HEADER);
        if (!"RS256".equals(text(header.required("alg"), 32))
                || !"medical-run+jwt".equals(text(header.required("typ"), 64))
                || !current.keyId().equals(text(header.required("kid"), 64))
                || !JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) invalid();
        // 请求声明的密钥和网络地址不能参与选择；只使用当前服务器公钥。
        if (!jwt.verify(new RSASSAVerifier(current.publicKey()))) invalid();
        JsonNode body = JSON.readTree(parts[1].decode());
        var claims = claims(body, current);
        if (Instant.now().getEpochSecond() >= claims.expiresAt().getEpochSecond()) invalid();
        return new Verified(claims, DigestUtils.sha256Hex(raw.getBytes(StandardCharsets.UTF_8)));
    }

    static void requireClaims(JsonNode body, Policy current) { claims(body, current); }

    private static Claims claims(JsonNode body, Policy current) {
        exactFields(body, CLAIMS);
        if (!current.issuer().equals(text(body.required("iss"), 512))
                || !current.audience().equals(text(body.required("aud"), 255))) invalid();
        long issued = seconds(body.required("iat")), expires = seconds(body.required("exp"));
        long now = Instant.now().getEpochSecond();
        if (issued > now || expires <= now || expires <= issued || expires - issued > current.maximumLifetime().getSeconds()) invalid();
        var claims = new Claims(uuid(body.required("tenant_id")), nullableUuid(body.required("project_id")),
            uuid(body.required("initiator_id")), uuid(body.required("run_id")), nullableUuid(body.required("step_id")),
            uuid(body.required("jti")), current.audience(), actions(body.required("action_scope")),
            WireValues.revision(text(body.required("auth_epoch"), 20)), Instant.ofEpochSecond(issued), Instant.ofEpochSecond(expires));
        if (Instant.now().getEpochSecond() >= expires) invalid();
        return claims;
    }

    public record Policy(String issuer, String audience, RSAPublicKey publicKey, Duration maximumLifetime, int maximumBytes) {
        public Policy {
            requireText(issuer, 512); requireText(audience, 255);
            URI uri = URI.create(issuer);
            Objects.requireNonNull(publicKey); Objects.requireNonNull(maximumLifetime);
            if (!uri.isAbsolute() || uri.getFragment() != null || publicKey.getModulus().bitLength() < 2048
                    || maximumLifetime.isNegative() || maximumLifetime.getSeconds() < 1 || maximumLifetime.getNano() != 0
                    || maximumBytes < 1) throw new IllegalArgumentException("RUN_TOKEN_POLICY_REQUIRED");
        }
        public String keyId() { return DigestUtils.sha256Hex(publicKey.getEncoded()); }
        @Override public String toString() { return "RunTokenSignaturePolicy"; }
    }

    private static void exactFields(JsonNode node, Set<String> required) {
        if (node == null || !node.isObject()) invalid();
        var names = new HashSet<String>(); node.fieldNames().forEachRemaining(names::add);
        if (!names.equals(required)) invalid();
    }

    private static String text(JsonNode value, int maximum) {
        if (!value.isTextual()) invalid();
        String result = value.textValue(); requireText(result, maximum); return result;
    }

    private static void requireText(String value, int maximum) {
        if (value == null || value.isEmpty() || value.codePointCount(0, value.length()) > maximum
                || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) invalid();
    }

    private static String uuid(JsonNode value) {
        String result = text(value, 36); WireValues.uuid(result); return result;
    }
    private static String nullableUuid(JsonNode value) { return value.isNull() ? null : uuid(value); }

    private static long seconds(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) invalid();
        return value.longValue();
    }

    private static List<String> actions(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) invalid();
        var result = new ArrayList<String>(); var unique = new HashSet<String>();
        for (JsonNode entry : node) {
            String action = text(entry, 64);
            if (action.contains("*") || action.codePoints().anyMatch(value -> Character.isWhitespace(value) || Character.isISOControl(value))
                    || !unique.add(action)) invalid();
            result.add(action);
        }
        return List.copyOf(result);
    }

    private static void invalid() { throw new SecurityException("RUN_TOKEN_INVALID"); }

    private record Claims(String tenantId, String projectId, String initiatorId, String runId, String stepId, String jti,
        String audience, List<String> actionScope, BigInteger authEpoch, Instant issuedAt, Instant expiresAt) { }

    public static final class Verified {
        private final Claims claims;
        private final String tokenSha256;
        private Verified(Claims claims, String tokenSha256) { this.claims = claims; this.tokenSha256 = tokenSha256; }
        public String tenantId() { return claims.tenantId(); }
        public String projectId() { return claims.projectId(); }
        public String initiatorId() { return claims.initiatorId(); }
        public String runId() { return claims.runId(); }
        public String stepId() { return claims.stepId(); }
        public String jti() { return claims.jti(); }
        public String audience() { return claims.audience(); }
        public List<String> actionScope() { return claims.actionScope(); }
        public BigInteger authEpoch() { return claims.authEpoch(); }
        public Instant issuedAt() { return claims.issuedAt(); }
        public Instant expiresAt() { return claims.expiresAt(); }
        public String tokenSha256() { return tokenSha256; }
        @Override public String toString() { return "VerifiedRunTokenSignature"; }
    }
}
