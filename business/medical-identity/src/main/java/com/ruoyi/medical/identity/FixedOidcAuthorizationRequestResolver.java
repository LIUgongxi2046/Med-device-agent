package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class FixedOidcAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private final OidcPolicy policy;
    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final RequestMatcher login = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/auth/login");

    public FixedOidcAuthorizationRequestResolver(ClientRegistrationRepository registrations, OidcPolicy policy) {
        policy.verify(registrations.findByRegistrationId(policy.registrationId()));
        this.policy = policy;
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/auth");
        this.delegate.setAuthorizationRequestCustomizer(new RequiredOidcProofs());
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        if (!login.matches(request)) return null;
        return delegate.resolve(request, policy.registrationId());
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        if (!policy.registrationId().equals(registrationId)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_FAILED.name()));
        }
        return resolve(request);
    }
}
