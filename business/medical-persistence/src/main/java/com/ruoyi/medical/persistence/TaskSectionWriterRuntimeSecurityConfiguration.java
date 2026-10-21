package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/** Exact worker-only transport boundary. It cannot authorize a browser-session route. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.professional-runtime.section-writer-enabled",havingValue="true",matchIfMissing=false)
class TaskSectionWriterRuntimeSecurityConfiguration {
    static final List<String> ROUTES=List.of("/internal/v1/section-writer-runtime/open-workflow","/internal/v1/section-writer-runtime/completed-original","/internal/v1/section-writer-runtime/queued","/internal/v1/section-writer-runtime/launch","/internal/v1/section-writer-runtime/admit","/internal/v1/section-writer-runtime/heartbeat","/internal/v1/section-writer-runtime/bridge","/internal/v1/section-writer-runtime/finish","/internal/v1/section-writer-runtime/close-admission","/internal/v1/section-writer-runtime/failure","/internal/v1/section-writer-runtime/supervisor-plan","/internal/v1/section-writer-runtime/supervisor-admission","/internal/v1/section-writer-runtime/recover","/internal/v1/section-writer-runtime/status");
    @Bean @Order(0)
    SecurityFilterChain taskSectionWriterRuntimeSecurity(HttpSecurity security,@org.springframework.beans.factory.annotation.Qualifier("formalRunServiceIdentity") ServiceCertificateIdentity services)throws Exception {
        var errors=new IdentityErrorResponseWriter();
        return security.securityMatcher(matcher())
            .csrf(config->config.disable()).sessionManagement(config->config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(config->config.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(config->config.disable()).formLogin(config->config.disable()).httpBasic(config->config.disable()).logout(config->config.disable())
            .authorizeHttpRequests(config->config.anyRequest().access((authentication,context)->new AuthorizationDecision(authorize(services,context.getRequest()))))
            .exceptionHandling(config->config.authenticationEntryPoint((request,response,failure)->errors.write(response,403,ErrorCode.ACCESS_DENIED))
                .accessDeniedHandler((request,response,failure)->errors.write(response,403,ErrorCode.ACCESS_DENIED))).build();
    }
    static org.springframework.security.web.util.matcher.RequestMatcher matcher(){return new OrRequestMatcher(ROUTES.stream().map(path->(org.springframework.security.web.util.matcher.RequestMatcher)PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST,path)).toList());}
    static boolean authorize(ServiceCertificateIdentity services,HttpServletRequest request){
        // A browser-origin request never enters this independent service channel, even with session cookies.
        if(request.getHeader("Origin")!=null||request.getHeader("Sec-Fetch-Site")!=null)return false;
        try{return services.require(request)!=null;}catch(Exception failure){return false;}
    }
}
