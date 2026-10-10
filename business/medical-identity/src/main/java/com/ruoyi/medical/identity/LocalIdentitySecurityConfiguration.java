package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
@EnableConfigurationProperties({IdentityMode.class, LocalIdentityPolicy.class, IdentityDirectoryPolicy.class, LocalManagementPolicy.class})
public class LocalIdentitySecurityConfiguration {
    @Bean(name = "localPasswordEncoder")
    PasswordEncoder localPasswordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    @Order(1)
    SecurityFilterChain localIdentityFilterChain(HttpSecurity security, LocalAccountDirectory accounts,
            LocalIdentityPolicy policy, @org.springframework.beans.factory.annotation.Qualifier("localPasswordEncoder") PasswordEncoder passwords,
            ApplicationEventPublisher events) throws Exception {
        var errors = new IdentityErrorResponseWriter();
        var manager = new ProviderManager(new LocalAuthenticationProvider(accounts, passwords));
        var publisher = new DefaultAuthenticationEventPublisher(events);
        publisher.setAdditionalExceptionMappings(java.util.Map.of(
                org.springframework.security.authentication.InternalAuthenticationServiceException.class,
                org.springframework.security.authentication.event.AuthenticationFailureServiceExceptionEvent.class));
        manager.setAuthenticationEventPublisher(publisher);
        var csrf = new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-TOKEN");
        var originErrors = new ExceptionTranslationFilter((request, response, failure) -> errors.write(response,
                HttpServletResponse.SC_FORBIDDEN, ErrorCode.ACCESS_DENIED), new org.springframework.security.web.savedrequest.NullRequestCache());
        originErrors.setAccessDeniedHandler((request, response, failure) -> errors.write(response,
                HttpServletResponse.SC_FORBIDDEN, ErrorCode.ACCESS_DENIED));
        return security.securityMatcher("/**")
                .requiresChannel(config -> {
                    if ("https".equals(policy.browserOrigin().getScheme())) config.anyRequest().requiresSecure();
                })
                .authenticationManager(manager)
                .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .requestCache(config -> config.disable())
                .httpBasic(config -> config.disable())
                .authorizeHttpRequests(config -> config
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/", "/auth/login", "/auth/setup", "/auth/csrf", "/local-auth/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/index.html", "/static/**", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, FrameworkFrontendConfiguration.ROUTES.toArray(String[]::new)).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/auth/login", "/auth/setup").permitAll()
                        .requestMatchers("/api/**", "/auth/account").access((authentication, context) -> {
                            var current = authentication.get();
                            boolean authenticated = current != null && current.isAuthenticated() && current.getPrincipal() instanceof LocalPrincipal;
                            if (authenticated) accounts.requireSession(context.getRequest(), current);
                            return new AuthorizationDecision(authenticated);
                        })
                        .anyRequest().denyAll())
                .exceptionHandling(config -> config.authenticationEntryPoint((request, response, failure) ->
                        errors.write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, failure) -> errors.write(response,
                                HttpServletResponse.SC_FORBIDDEN, ErrorCode.ACCESS_DENIED)))
                .formLogin(config -> config.loginPage("/auth/login").loginProcessingUrl("/auth/login")
                        .successHandler((request, response, authentication) -> {
                            accounts.registerSession(request, authentication);
                            response.setHeader("Cache-Control", "no-store");
                            response.sendRedirect("/local-auth/account.html");
                        })
                        .failureHandler((request, response, failure) -> errors.write(response,
                                HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.AUTHENTICATION_FAILED)))
                .logout(config -> config.logoutUrl("/auth/logout")
                        .addLogoutHandler((request, response, authentication) -> accounts.revoke(request, authentication))
                        .deleteCookies("MEDICAL_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setHeader("Cache-Control", "no-store");
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        }))
                .addFilterBefore(new BrowserOriginFilter(policy), org.springframework.security.web.authentication.logout.LogoutFilter.class)
                .addFilterBefore(originErrors, BrowserOriginFilter.class)
                .build();
    }
}
