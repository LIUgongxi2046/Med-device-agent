package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.Base64URL;
import com.ruoyi.medical.contracts.WireValues;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RunTokenSignatureTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String NAME = "com.ruoyi.medical.identity.RunTokenSignature";
    private static final String ISSUER = "urn:medical:local:run-token-signature";
    private static final String AUDIENCE = "urn:medical:local:source-processing";
    private static KeyPair original, other;

    @BeforeAll static void actualSigningKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        original = generator.generateKeyPair(); other = generator.generateKeyPair();
        System.out.println("JAVA_RUNTIME=" + System.getProperty("java.runtime.version")
            + " RSA_PROVIDER=" + generator.getProvider().getName());
    }

    private static Class<?> implementation() throws Exception {
        assertNotNull(RunTokenSignatureTest.class.getClassLoader().getResource(NAME.replace('.', '/') + ".class"),
            "运行令牌必须验证实际签名并完整读取固定Claims");
        return Class.forName(NAME);
    }

    private static Object policy(KeyPair key, String audience, Duration lifetime, int bytes) throws Exception {
        implementation();
        return Class.forName(NAME + "$Policy").getConstructor(String.class, String.class, RSAPublicKey.class, Duration.class, int.class)
            .newInstance(ISSUER, audience, key.getPublic(), lifetime, bytes);
    }

    private static Object verify(String token, Object policy) throws Exception {
        return implementation().getMethod("verify", String.class, Class.forName(NAME + "$Policy")).invoke(null, token, policy);
    }

    private static Object value(Object verified, String method) throws Exception {
        return verified.getClass().getMethod(method).invoke(verified);
    }

    private static ObjectNode claims() {
        long now = Instant.now().getEpochSecond();
        var body = JSON.createObjectNode();
        body.put("iss", ISSUER); body.put("aud", AUDIENCE); body.put("iat", now - 1); body.put("exp", now + 30);
        for (String name : new String[]{"tenant_id", "project_id", "initiator_id", "run_id", "step_id", "jti"})
            body.put(name, UUID.randomUUID().toString());
        body.putArray("action_scope").add("source.read").add("tool.execute");
        body.put("auth_epoch", "1"); return body;
    }

    private static JWSHeader header(KeyPair advertised, JWSAlgorithm algorithm, String type) throws Exception {
        return new JWSHeader.Builder(algorithm).type(new JOSEObjectType(type))
            .keyID(WireValues.sha256(advertised.getPublic().getEncoded())).build();
    }

    private static String signed(byte[] body, JWSHeader header, KeyPair signer) throws Exception {
        var jwt = new JWSObject(header, new Payload(body));
        jwt.sign(new RSASSASigner((RSAPrivateKey) signer.getPrivate())); return jwt.serialize();
    }

    private static String signed(ObjectNode body) throws Exception {
        return signed(JSON.writeValueAsBytes(body), header(original, JWSAlgorithm.RS256, "medical-run+jwt"), original);
    }

    private static void rejected(String token, Object policy, Class<? extends Throwable> expected) {
        var failure = assertThrows(InvocationTargetException.class, () -> verify(token, policy));
        assertInstanceOf(expected, failure.getCause());
    }

    @Test void actualSignedTokenPreservesCompleteClaims() throws Exception {
        var body = claims(); String token = signed(body);
        var verified = verify(token, policy(original, AUDIENCE, Duration.ofSeconds(60), 8192));
        for (var pair : new String[][]{{"tenant_id", "tenantId"}, {"project_id", "projectId"}, {"initiator_id", "initiatorId"},
                {"run_id", "runId"}, {"step_id", "stepId"}, {"jti", "jti"}, {"aud", "audience"}})
            assertEquals(body.required(pair[0]).textValue(), value(verified, pair[1]));
        assertEquals(BigInteger.ONE, value(verified, "authEpoch"));
        assertEquals(Instant.ofEpochSecond(body.required("iat").longValue()), value(verified, "issuedAt"));
        assertEquals(Instant.ofEpochSecond(body.required("exp").longValue()), value(verified, "expiresAt"));
        assertEquals(java.util.List.of("source.read", "tool.execute"), value(verified, "actionScope"));
        assertEquals(WireValues.sha256(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)), value(verified, "tokenSha256"));
        assertEquals("VerifiedRunTokenSignature", verified.toString());
    }

    @Test void enterpriseJobScopePreservesExplicitNulls() throws Exception {
        var body = claims(); body.putNull("project_id"); body.putNull("step_id");
        var verified = verify(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192));
        assertNull(value(verified, "projectId")); assertNull(value(verified, "stepId"));
    }

    @Test void completeUnsignedEpochPreservesAllBits() throws Exception {
        var body = claims(); body.put("auth_epoch", "18446744073709551615");
        var verified = verify(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192));
        assertEquals(new BigInteger("18446744073709551615"), value(verified, "authEpoch"));
    }

    @Test void actualDifferentPrivateKeyCannotSignForCurrentPublicKey() throws Exception {
        String token = signed(JSON.writeValueAsBytes(claims()), header(original, JWSAlgorithm.RS256, "medical-run+jwt"), other);
        rejected(token, policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void changedCurrentPublicKeyRejectsOriginalToken() throws Exception {
        rejected(signed(claims()), policy(other, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "iat", "exp", "aud", "jti", "tenant_id", "project_id", "initiator_id", "run_id", "step_id", "action_scope", "auth_epoch"})
    void eachRequiredClaimMustBePresent(String name) throws Exception {
        var body = claims(); body.remove(name);
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void unknownClaimIsRejected() throws Exception {
        var body = claims(); body.put("nbf", Instant.now().getEpochSecond());
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"JWT", "logout+jwt"})
    void otherTokenPurposeCannotProvideRunIdentity(String type) throws Exception {
        String token = signed(JSON.writeValueAsBytes(claims()), header(original, JWSAlgorithm.RS256, type), original);
        rejected(token, policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void otherActualRsaAlgorithmIsRejected() throws Exception {
        String token = signed(JSON.writeValueAsBytes(claims()), header(original, JWSAlgorithm.RS384, "medical-run+jwt"), original);
        rejected(token, policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void unknownJoseHeaderIsRejected() throws Exception {
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("medical-run+jwt"))
            .keyID(WireValues.sha256(original.getPublic().getEncoded())).customParam("unregistered", true).build();
        rejected(signed(JSON.writeValueAsBytes(claims()), header, original),
            policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void exactAudienceCannotBeExpandedByArray() throws Exception {
        var body = claims(); body.putArray("aud").add(AUDIENCE);
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void changedAudienceIsRejected() throws Exception {
        rejected(signed(claims()), policy(original, ISSUER, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void changedIssuerIsRejected() throws Exception {
        var body = claims(); body.put("iss", AUDIENCE);
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "01", "18446744073709551616"})
    void unsignedEpochMustHaveCanonicalPrecision(String epoch) throws Exception {
        var body = claims(); body.put("auth_epoch", epoch);
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), IllegalArgumentException.class);
    }

    @Test void numericEpochIsRejected() throws Exception {
        var body = claims(); body.put("auth_epoch", 1);
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void explicitByteLimitIsEnforcedBeforeParsing() throws Exception {
        rejected(signed(claims()), policy(original, AUDIENCE, Duration.ofSeconds(60), 100), SecurityException.class);
    }

    @Test void explicitLifetimeCannotBeExtended() throws Exception {
        var body = claims(); body.put("exp", body.required("iat").longValue() + 61);
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void realTimeExpiryStopsOriginalToken() throws Exception {
        var body = claims(); long now = Instant.now().getEpochSecond(); body.put("iat", now); body.put("exp", now + 2);
        var policy = policy(original, AUDIENCE, Duration.ofSeconds(4), 8192);
        String token = signed(body); assertNotNull(verify(token, policy)); TimeUnit.SECONDS.sleep(3);
        rejected(token, policy, SecurityException.class);
    }

    @Test void futureIssuanceIsRejected() throws Exception {
        var body = claims(); long now = Instant.now().getEpochSecond(); body.put("iat", now + 120); body.put("exp", now + 150);
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void equalIssueAndExpiryIsRejected() throws Exception {
        var body = claims(); body.put("exp", body.required("iat").longValue());
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void fractionalTimeIsRejected() throws Exception {
        var body = claims(); body.put("iat", Instant.now().getEpochSecond() - 0.5);
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "source.*", "source read"})
    void actionNamesCannotGrantUnknownRanges(String action) throws Exception {
        var body = claims(); body.putArray("action_scope").add(action);
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void duplicateActionsAreRejected() throws Exception {
        var body = claims(); body.putArray("action_scope").add("source.read").add("source.read");
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void emptyActionScopeIsRejected() throws Exception {
        var body = claims(); body.putArray("action_scope");
        rejected(signed(body), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }

    @Test void duplicateJsonClaimIsRejectedByMatureDecoder() throws Exception {
        var body = claims(); var bytes = new ByteArrayOutputStream();
        try (var generator = JSON.getFactory().createGenerator(bytes)) {
            generator.writeStartObject();
            var fields = body.fields();
            while (fields.hasNext()) { var item = fields.next(); generator.writeFieldName(item.getKey()); generator.writeTree(item.getValue()); }
            generator.writeStringField("jti", UUID.randomUUID().toString()); generator.writeEndObject();
        }
        rejected(signed(bytes.toByteArray(), header(original, JWSAlgorithm.RS256, "medical-run+jwt"), original),
            policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), IOException.class);
    }

    @Test void changedSignedPayloadCannotUseOriginalSignature() throws Exception {
        var body = claims(); var jwt = JWSObject.parse(signed(body)); body.put("auth_epoch", "2");
        var parts = jwt.getParsedParts();
        var changed = new JWSObject(parts[0], Base64URL.encode(JSON.writeValueAsBytes(body)), parts[2]);
        rejected(changed.serialize(), policy(original, AUDIENCE, Duration.ofSeconds(60), 8192), SecurityException.class);
    }
}
