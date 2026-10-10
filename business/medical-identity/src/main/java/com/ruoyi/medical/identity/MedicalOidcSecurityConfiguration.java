package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "oidc")
@EnableConfigurationProperties({OidcPolicy.class, IdentityDirectoryPolicy.class, BackchannelLogoutPolicy.class, LogoutPersistencePolicy.class})
public class MedicalOidcSecurityConfiguration {
    @Bean
    @Order(1)
    public SecurityFilterChain medicalOidcSecurityFilterChain(HttpSecurity security,
            ClientRegistrationRepository registrations, OidcPolicy policy, PlatformIdentityDirectory directory,
            PlatformOidcSessions sessions) throws Exception {
        var resolver = new FixedOidcAuthorizationRequestResolver(registrations, policy);
        var errors = new IdentityErrorResponseWriter();
        var csrfTokens = new HttpSessionCsrfTokenRepository();
        csrfTokens.setHeaderName("X-CSRF-TOKEN");
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(policy.browserOrigin().toASCIIString()));
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "X-Context-Revision", "X-Schema-Version", "X-Plan-Decision", "Idempotency-Key", "Last-Event-ID"));
        cors.setExposedHeaders(List.of("X-Schema-Version", "X-Request-Id", "Location"));
        var corsSource = new UrlBasedCorsConfigurationSource();
        corsSource.registerCorsConfiguration("/api/**", cors);
        return security.securityMatcher("/api/**", "/auth/**", "/internal/**")
                .requiresChannel(config -> config.anyRequest().requiresSecure())
                .cors(config -> config.configurationSource(corsSource))
                .csrf(config -> config.csrfTokenRepository(csrfTokens).csrfTokenRequestHandler(csrfHandler))
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .requestCache(config -> config.disable())
                .formLogin(config -> config.disable())
                .httpBasic(config -> config.disable())
                .logout(config -> config.disable())
                .authorizeHttpRequests(config -> config
                        .requestMatchers(HttpMethod.GET, "/auth/login", "/auth/callback").permitAll()
                        .requestMatchers("/api/**").access((authentication, context) -> {
                            var current = authentication.get();
                            boolean authenticated = current instanceof OAuth2AuthenticationToken oidc
                                    && oidc.isAuthenticated() && oidc.getPrincipal() instanceof OidcUser
                                    && policy.registrationId().equals(oidc.getAuthorizedClientRegistrationId());
                            if (authenticated) sessions.requireCurrentSession(context.getRequest());
                            return new AuthorizationDecision(authenticated);
                        })
                        .anyRequest().denyAll())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, failure) -> errors.write(response,
                                HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, failure) -> errors.write(response,
                                HttpServletResponse.SC_FORBIDDEN, ErrorCode.ACCESS_DENIED)))
                .oauth2Login(config -> config.loginPage("/auth/login")
                        .authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver)
                                .authorizationRequestRepository(new HttpSessionOAuth2AuthorizationRequestRepository()))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri("/auth/callback"))
                        .withObjectPostProcessor(new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {
                            @Override
                            public <O extends OAuth2LoginAuthenticationFilter> O postProcess(O filter) {
                                // 认证过滤器在授权规则之前执行，因此固定回调也必须限定 GET。
                                filter.setRequiresAuthenticationRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                        .matcher(HttpMethod.GET, "/auth/callback"));
                                return filter;
                            }
                        })
                        .successHandler((request, response, authentication) -> {
                            var identity = directory.registerCurrentIdentity();
                            sessions.registerCurrentSession(request, identity);
                            response.sendRedirect(policy.returnTarget().toASCIIString());
                        })
                        .failureHandler((request, response, failure) -> errors.write(response,
                                HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.AUTHENTICATION_FAILED)))
                // Origin 拒绝由标准异常处理器转换；检查位于业务授权和 Controller 之前。
                .addFilterAfter(new BrowserOriginFilter(policy), ExceptionTranslationFilter.class)
                .build();
    }
}
