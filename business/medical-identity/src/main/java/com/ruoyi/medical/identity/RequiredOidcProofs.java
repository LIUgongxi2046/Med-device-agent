package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public final class RequiredOidcProofs implements Consumer<OAuth2AuthorizationRequest.Builder> {
    private static final Pattern VERIFIER = Pattern.compile("[A-Za-z0-9._~-]{43,128}");

    @Override
    public void accept(OAuth2AuthorizationRequest.Builder builder) {
        // Confidential client 同样要求 PKCE；成熟生成器的输出必须满足 S256 和 nonce 校验。
        OAuth2AuthorizationRequestCustomizers.withPkce().accept(builder);
        Map<String, Object> attributes = new HashMap<>();
        Map<String, Object> parameters = new HashMap<>();
        builder.attributes(attributes::putAll).additionalParameters(parameters::putAll);
        if (!(attributes.get("code_verifier") instanceof String verifier)
                || !VERIFIER.matcher(verifier).matches()
                || !"S256".equals(parameters.get("code_challenge_method"))
                || !hash(verifier).equals(parameters.get("code_challenge"))
                || parameters.containsKey("code_verifier")
                || !(attributes.get("nonce") instanceof String nonce)
                || nonce.isBlank()
                || !hash(nonce).equals(parameters.get("nonce"))) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_FAILED.name()));
        }
    }

    private static String hash(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(DigestUtils.sha256(value.getBytes(StandardCharsets.US_ASCII)));
    }
}
