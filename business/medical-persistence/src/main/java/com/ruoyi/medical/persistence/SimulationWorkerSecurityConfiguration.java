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

/** Exact worker-only transport boundary. It cannot authorize a production or browser-session route. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="medical.simulation-execution",name="enabled",havingValue="true")
class SimulationWorkerSecurityConfiguration {
    static final List<String> ROUTES=List.of("/internal/v1/simulation-evaluations/claim","/internal/v1/simulation-evaluations/issue",
        "/internal/v1/simulation-evaluations/status","/internal/v1/simulation-evaluations/execute","/internal/v1/simulation-evaluations/reconcile","/internal/v1/simulation-evaluations/settle","/internal/v1/simulation-invocations","/internal/v1/simulation-invocations/status","/internal/v1/simulation-invocations/cancel");
    @Bean @Order(0)
    SecurityFilterChain simulationWorkerSecurity(HttpSecurity security,SimulationInvocationConfiguration.Keys keys)throws Exception {
        var errors=new IdentityErrorResponseWriter();
        return security.securityMatcher(matcher())
            .csrf(config->config.disable()).sessionManagement(config->config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(config->config.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(config->config.disable()).formLogin(config->config.disable()).httpBasic(config->config.disable()).logout(config->config.disable())
            .authorizeHttpRequests(config->config.anyRequest().access((authentication,context)->new AuthorizationDecision(authorize(keys.services(),context.getRequest()))))
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
