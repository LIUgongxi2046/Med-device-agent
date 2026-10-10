package com.ruoyi.medical.identity;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
public class MedicalSessionConfiguration {
    @Bean
    public SessionRepositoryCustomizer<RedisIndexedSessionRepository> medicalSessionRepositoryCustomizer(Environment environment) {
        Duration timeout = Binder.get(environment).bind("server.servlet.session.timeout", Duration.class)
                .orElseThrow(() -> new IllegalArgumentException("SESSION_TIMEOUT_REQUIRED"));
        boolean local = "local".equals(environment.getProperty("medical.identity.mode"))
                && "127.0.0.1".equals(environment.getProperty("server.address"))
                && "127.0.0.1".equals(environment.getProperty("spring.data.redis.host"));
        if (timeout.isNegative() || timeout.isZero()
                || !"indexed".equals(environment.getProperty("spring.session.redis.repository-type"))
                || (!local && !Boolean.TRUE.equals(environment.getProperty("spring.data.redis.ssl.enabled", Boolean.class)))
                || !environment.containsProperty("spring.data.redis.host")
                || environment.getRequiredProperty("spring.data.redis.host").isBlank()
                || !environment.containsProperty("spring.data.redis.port")) {
            throw new IllegalArgumentException("SESSION_CONFIGURATION_REQUIRED");
        }
        return repository -> repository.setDefaultMaxInactiveInterval(timeout);
    }

    @Bean
    public CookieSerializer medicalSessionCookieSerializer(Environment environment) {
        DefaultCookieSerializer cookies = new DefaultCookieSerializer();
        cookies.setCookieName("MEDICAL_SESSION");
        cookies.setCookiePath("/");
        boolean local = "local".equals(environment.getProperty("medical.identity.mode"))
                && "127.0.0.1".equals(environment.getProperty("server.address"))
                && environment.getRequiredProperty("medical.identity.local.browser-origin").startsWith("http://127.0.0.1:");
        cookies.setUseSecureCookie(!local);
        cookies.setUseHttpOnlyCookie(true);
        cookies.setSameSite("Lax");
        return cookies;
    }
}
