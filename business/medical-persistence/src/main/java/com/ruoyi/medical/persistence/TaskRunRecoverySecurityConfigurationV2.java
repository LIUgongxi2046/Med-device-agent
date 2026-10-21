package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.workflow.TaskRunRecoverySchemaV2;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.task-run.waiting-v2.server-enabled",havingValue="true",matchIfMissing=false)
class TaskRunRecoverySecurityConfigurationV2 {
    @Bean @Order(0)
    SecurityFilterChain taskRunRecoverySecurityV2(HttpSecurity http,@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identity)throws Exception{
        var errors=new IdentityErrorResponseWriter();return http.securityMatcher(matcher())
            .csrf(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(c->c.securityContextRepository(new NullSecurityContextRepository())).requestCache(c->c.disable())
            .formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
            .authorizeHttpRequests(c->c.anyRequest().access((auth,ctx)->new AuthorizationDecision(authorize(identity,ctx.getRequest()))))
            .exceptionHandling(c->c.authenticationEntryPoint((request,response,failure)->errors.write(response,403,ErrorCode.ACCESS_DENIED))
                .accessDeniedHandler((request,response,failure)->errors.write(response,403,ErrorCode.ACCESS_DENIED))).build();
    }
    static org.springframework.security.web.util.matcher.RequestMatcher matcher(){return PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST,TaskRunRecoverySchemaV2.ROUTE);}
    static boolean authorize(ServiceCertificateIdentity identity,HttpServletRequest request){
        if(request.getHeader("Origin")!=null||request.getHeader("Sec-Fetch-Site")!=null||request.getHeader("Authorization")!=null||request.getHeader("Cookie")!=null)return false;
        try{return identity.require(request)!=null;}catch(Exception denied){return false;}
    }
}
