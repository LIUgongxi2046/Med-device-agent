package com.ruoyi.medical.identity;

import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.ruoyi.medical.contracts.ErrorCode;
import java.net.http.HttpClient;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "oidc")
public final class BackchannelLogoutVerifier {
    private final NimbusJwtDecoder decoder;
    private final String issuer;
    private final String clientId;

    public BackchannelLogoutVerifier(ClientRegistrationRepository registrations, OidcPolicy oidc,
            BackchannelLogoutPolicy policy) {
        var registration = registrations.findByRegistrationId(oidc.registrationId());
        oidc.verify(registration);
        issuer = oidc.issuer().toASCIIString();
        clientId = registration.getClientId();
        var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(policy.connectTimeout()).build();
        var requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(policy.readTimeout());
        decoder = NimbusJwtDecoder.withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
                .jwsAlgorithms(algorithms -> {
                    algorithms.clear();
                    algorithms.addAll(policy.signatureAlgorithms());
                }).validateType(false).restOperations(new RestTemplate(requests)).build();
        Map<String, Converter<Object, ?>> conversions = new HashMap<>();
        // 原始身份和受众类型保留；日期由成熟组件转换，必需声明缺失时立即拒绝。
        for (String claim : java.util.List.of("iss", "sub", "aud", "jti")) conversions.put(claim, null);
        var converter = MappedJwtClaimSetConverter.withDefaults(conversions);
        decoder.setClaimSetConverter(claims -> {
            if (!(claims.get("iat") instanceof Date) || !(claims.get("exp") instanceof Date)) {
                throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_FAILED.name()));
            }
            return converter.convert(claims);
        });
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new LogoutClaimsValidator(oidc.issuer().toASCIIString(), registration.getClientId(), policy.clockSkew()),
                new JwtTimestampValidator(policy.clockSkew())));
    }

    Jwt verify(String token) throws ParseException {
        requireSignedToken(token);
        return decoder.decode(token);
    }

    String issuer() { return issuer; }
    String clientId() { return clientId; }

    static void requireSignedToken(String token) throws ParseException {
        if (token == null || token.isBlank() || !(JWTParser.parse(token) instanceof SignedJWT)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_FAILED.name()));
        }
    }
}
