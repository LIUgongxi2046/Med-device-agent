package com.ruoyi.medical.identity;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@ConfigurationProperties("medical.identity")
public record OidcPolicy(String registrationId, URI issuer, URI callback, URI browserOrigin, URI returnTarget) {
    public OidcPolicy {
        if (registrationId == null || registrationId.isBlank() || issuer == null || callback == null
                || browserOrigin == null || returnTarget == null) {
            throw new IllegalArgumentException("IDENTITY_CONFIGURATION_REQUIRED");
        }
        requireHttps(issuer);
        requireHttps(callback);
        requireHttps(browserOrigin);
        if (!"/auth/callback".equals(callback.getRawPath())
                || (browserOrigin.getRawPath() != null && !browserOrigin.getRawPath().isEmpty())
                || returnTarget.isAbsolute() || returnTarget.getRawAuthority() != null
                || returnTarget.getRawQuery() != null || returnTarget.getRawFragment() != null
                || returnTarget.getPath() == null || !returnTarget.getPath().startsWith("/")
                || returnTarget.getPath().startsWith("//") || returnTarget.getPath().contains("\\")
                || !returnTarget.getRawPath().equals(returnTarget.getPath())
                || !returnTarget.normalize().equals(returnTarget)) {
            throw new IllegalArgumentException("IDENTITY_CONFIGURATION_INVALID");
        }
    }

    public void verify(ClientRegistration registration) {
        if (registration == null || !registrationId.equals(registration.getRegistrationId())
                || !AuthorizationGrantType.AUTHORIZATION_CODE.equals(registration.getAuthorizationGrantType())
                || !registration.getScopes().contains("openid")
                || registration.getClientId() == null || registration.getClientId().isBlank()
                || registration.getClientSecret() == null || registration.getClientSecret().isBlank()
                || !(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(registration.getClientAuthenticationMethod())
                    || ClientAuthenticationMethod.CLIENT_SECRET_POST.equals(registration.getClientAuthenticationMethod()))
                || !issuer.toASCIIString().equals(registration.getProviderDetails().getIssuerUri())
                || !callback.toASCIIString().equals(registration.getRedirectUri())) {
            throw new IllegalArgumentException("IDENTITY_REGISTRATION_INVALID");
        }
        requireHttps(URI.create(registration.getProviderDetails().getAuthorizationUri()));
        requireHttps(URI.create(registration.getProviderDetails().getTokenUri()));
        requireHttps(URI.create(registration.getProviderDetails().getJwkSetUri()));
        String userInfo = registration.getProviderDetails().getUserInfoEndpoint().getUri();
        if (userInfo != null && !userInfo.isBlank()) requireHttps(URI.create(userInfo));
    }

    private static void requireHttps(URI uri) {
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("IDENTITY_HTTPS_CONFIGURATION_REQUIRED");
        }
    }
}
