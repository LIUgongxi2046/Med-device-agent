package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceBindings;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import static org.junit.jupiter.api.Assertions.*;

class OidcSecurityTest {
    @SuppressWarnings("unchecked")
    private Consumer<OAuth2AuthorizationRequest.Builder> proofs() throws Exception {
        String name = "com.ruoyi.medical.identity.RequiredOidcProofs";
        assertNotNull(getClass().getClassLoader().getResource(name.replace('.', '/') + ".class"), "OIDC 请求必须强制核验 PKCE 和 nonce");
        return (Consumer<OAuth2AuthorizationRequest.Builder>) Class.forName(name).getConstructor().newInstance();
    }

    @Test
    void generatedPkceProofUsesS256AndPreservesServerVerifier() throws Exception {
        String nonce = new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96).generateKey();
        String nonceHash = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(nonce.getBytes(StandardCharsets.US_ASCII)));
        var request = OAuth2AuthorizationRequest.authorizationCode();
        request.attributes(attributes -> attributes.put("nonce", nonce));
        request.additionalParameters(parameters -> parameters.put("nonce", nonceHash));
        proofs().accept(request);
        Map<String, Object> attributes = new HashMap<>();
        Map<String, Object> parameters = new HashMap<>();
        request.attributes(attributes::putAll).additionalParameters(parameters::putAll);
        String verifier = assertInstanceOf(String.class, attributes.get("code_verifier"));
        assertTrue(verifier.length() >= 43 && verifier.length() <= 128);
        assertEquals("S256", parameters.get("code_challenge_method"));
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))), parameters.get("code_challenge"));
        assertFalse(parameters.containsKey("code_verifier"));
        assertEquals(nonceHash, parameters.get("nonce"));
    }

    @Test
    void missingNonceStopsAuthorizationRequest() throws Exception {
        var proofs = proofs();
        var failure = assertThrows(OAuth2AuthenticationException.class,
            () -> proofs.accept(OAuth2AuthorizationRequest.authorizationCode()));
        assertEquals("AUTHENTICATION_FAILED", failure.getError().getErrorCode());
        assertNull(failure.getCause());
    }

    @Test
    void repeatedCustomizationPreservesProofAndRejectsChangedChallenge() throws Exception {
        String nonce = new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96).generateKey();
        String nonceHash = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(nonce.getBytes(StandardCharsets.US_ASCII)));
        var request = OAuth2AuthorizationRequest.authorizationCode();
        request.attributes(attributes -> attributes.put("nonce", nonce));
        request.additionalParameters(parameters -> parameters.put("nonce", nonceHash));
        var proofs = proofs();
        proofs.accept(request);
        Map<String, Object> initial = new HashMap<>();
        request.attributes(initial::putAll);
        proofs.accept(request);
        Map<String, Object> repeated = new HashMap<>();
        request.attributes(repeated::putAll);
        assertEquals(initial.get("code_verifier"), repeated.get("code_verifier"));
        request.additionalParameters(parameters -> parameters.put("code_challenge", nonceHash));
        var failure = assertThrows(OAuth2AuthenticationException.class, () -> proofs.accept(request));
        assertEquals("AUTHENTICATION_FAILED", failure.getError().getErrorCode());
    }

    @Test
    void missingIdentityConfigurationCannotCreatePolicy() throws Exception {
        String name = "com.ruoyi.medical.identity.OidcPolicy";
        assertNotNull(getClass().getClassLoader().getResource(name.replace('.', '/') + ".class"), "身份入口必须验证必需配置");
        var constructor = Class.forName(name).getConstructor(String.class, java.net.URI.class, java.net.URI.class, java.net.URI.class, java.net.URI.class);
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> constructor.newInstance(null, null, null, null, null));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals("IDENTITY_CONFIGURATION_REQUIRED", failure.getCause().getMessage());
    }

    private OAuth2AuthorizationRequest.Builder requestWithNonce() throws Exception {
        String nonce = new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96).generateKey();
        String nonceHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(nonce.getBytes(StandardCharsets.US_ASCII)));
        var request = OAuth2AuthorizationRequest.authorizationCode();
        request.attributes(attributes -> attributes.put("nonce", nonce));
        request.additionalParameters(parameters -> parameters.put("nonce", nonceHash));
        return request;
    }

    private void authenticationFailure(OAuth2AuthorizationRequest.Builder request) {
        var failure = assertThrows(OAuth2AuthenticationException.class, () -> new RequiredOidcProofs().accept(request));
        assertEquals("AUTHENTICATION_FAILED", failure.getError().getErrorCode());
        assertNull(failure.getCause());
    }

    @Test
    void changedNonceStopsAuthorizationRequest() throws Exception {
        var request = requestWithNonce();
        new RequiredOidcProofs().accept(request);
        request.additionalParameters(parameters -> parameters.remove("nonce"));
        authenticationFailure(request);
    }

    @Test
    void plainPkceIsRejected() throws Exception {
        var request = requestWithNonce();
        new RequiredOidcProofs().accept(request);
        request.additionalParameters(parameters -> parameters.put("code_challenge_method", "plain"));
        authenticationFailure(request);
    }

    @Test
    void publicVerifierIsRejected() throws Exception {
        var request = requestWithNonce();
        new RequiredOidcProofs().accept(request);
        Map<String, Object> attributes = new HashMap<>();
        request.attributes(attributes::putAll);
        request.additionalParameters(parameters -> parameters.put("code_verifier", attributes.get("code_verifier")));
        authenticationFailure(request);
    }

    @Test
    void missingSessionConfigurationStopsStartup() {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> new MedicalSessionConfiguration().medicalSessionRepositoryCustomizer(new StandardEnvironment()));
        assertEquals("SESSION_TIMEOUT_REQUIRED", failure.getMessage());
    }

    @Test
    void actualIdentityErrorsMatchTheSharedResponseSchema() throws Exception {
        var errors = new IdentityErrorResponseWriter();
        var json = JsonMapper.builder().build();
        var catalogue = SchemaCatalogue.fromClasspath();
        String requestId = UUID.randomUUID().toString();
        for (var code : java.util.List.of(ErrorCode.AUTHENTICATION_REQUIRED, ErrorCode.AUTHENTICATION_FAILED, ErrorCode.ACCESS_DENIED,
                ErrorCode.CONTENT_MISMATCH, ErrorCode.OBJECT_UNAVAILABLE, ErrorCode.BUDGET_EXCEEDED, ErrorCode.REVISION_LIMIT)) {
            var payload = json.readTree(errors.encode(code, requestId));
            var bound = TypedSchemaBindings.bind(catalogue, InterfaceBindings.ERROR, payload).read();
            assertEquals(code.name(), bound.code());
            assertEquals(requestId, bound.requestId());
            assertNull(bound.resourceRevision());
            assertNull(bound.operationId());
            assertTrue(bound.details().isEmpty());
            assertTrue(bound.allowedActions().isEmpty());
            assertFalse(bound.retryable());
            assertEquals(9, payload.size());
        }
    }
}
