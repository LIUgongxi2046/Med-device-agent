package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationWorkerSecurityConfigurationTest {
    @Test void narrowRegisteredServiceTransportHasNoSessionFallback()throws Exception {
        var services=mock(ServiceCertificateIdentity.class);var request=mock(HttpServletRequest.class);
        when(services.require(request)).thenThrow(new SecurityException("NO_MTLS"));assertFalse(SimulationWorkerSecurityConfiguration.authorize(services,request));
        doReturn(mock(ServiceCertificateIdentity.Verified.class)).when(services).require(request);assertTrue(SimulationWorkerSecurityConfiguration.authorize(services,request));
        when(request.getHeader("Origin")).thenReturn("https://browser.example");assertFalse(SimulationWorkerSecurityConfiguration.authorize(services,request));
        assertEquals(9,SimulationWorkerSecurityConfiguration.ROUTES.size());assertTrue(SimulationWorkerSecurityConfiguration.ROUTES.stream().allMatch(p->p.startsWith("/internal/v1/simulation-")));
        assertTrue(SimulationWorkerSecurityConfiguration.ROUTES.stream().noneMatch(p->p.contains("*")||p.contains("dispatch")));
    }
    @Test void actualMatcherOnlySelectsExactPostWorkerPaths() {
        var matcher=SimulationWorkerSecurityConfiguration.matcher();
        for(String path:SimulationWorkerSecurityConfiguration.ROUTES){
            assertTrue(matcher.matches(request("POST",path)),path);
            for(String method:java.util.List.of("GET","HEAD","OPTIONS","PUT","PATCH","DELETE"))assertFalse(matcher.matches(request(method,path)),method+path);
            for(String suffix:java.util.List.of("/","/extra","evil"))assertFalse(matcher.matches(request("POST",path+suffix)),path+suffix);
        }
        for(String path:java.util.List.of("/api/simulation-evaluations","/api/simulation-fixtures","/internal/v1/run-tokens","/internal/v1/tasks","/internal/v1/simulation-evaluations"))assertFalse(matcher.matches(request("POST",path)),path);
    }
    private static HttpServletRequest request(String method,String path){
        var request=mock(HttpServletRequest.class);when(request.getMethod()).thenReturn(method);when(request.getRequestURI()).thenReturn(path);
        when(request.getContextPath()).thenReturn("");when(request.getServletPath()).thenReturn(path);var mapping=mock(jakarta.servlet.http.HttpServletMapping.class);when(mapping.getMappingMatch()).thenReturn(jakarta.servlet.http.MappingMatch.DEFAULT);when(mapping.getPattern()).thenReturn("/");when(request.getHttpServletMapping()).thenReturn(mapping);return request;
    }
    @Test void actualFilterChainIsStatelessAndHasNoBrowserCsrfOrLoginFilters()throws Exception {
        var post=new org.springframework.security.config.ObjectPostProcessor<Object>(){public <O>O postProcess(O object){return object;}};
        var context=new org.springframework.context.support.StaticApplicationContext();context.refresh();
        var shared=new java.util.HashMap<Class<?>,Object>();shared.put(org.springframework.context.ApplicationContext.class,context);
        var security=new org.springframework.security.config.annotation.web.builders.HttpSecurity(post,new org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder(post),shared);
        security.authenticationManager(mock(org.springframework.security.authentication.AuthenticationManager.class));
        var services=mock(ServiceCertificateIdentity.class);var keys=new SimulationInvocationConfiguration.Keys(null,null,null,services);
        var chain=new SimulationWorkerSecurityConfiguration().simulationWorkerSecurity(security,keys);
        assertTrue(chain.matches(request("POST",SimulationWorkerSecurityConfiguration.ROUTES.get(0))));
        assertFalse(chain.matches(request("POST","/api/simulation-evaluations")));
        assertTrue(chain.getFilters().stream().anyMatch(f->f instanceof org.springframework.security.web.access.intercept.AuthorizationFilter));
        assertTrue(chain.getFilters().stream().noneMatch(f->f instanceof org.springframework.security.web.csrf.CsrfFilter
            ||f instanceof org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
            ||f instanceof org.springframework.security.web.authentication.www.BasicAuthenticationFilter));context.close();
    }
    @Test void genuineTransportRejectsBrowserCookieSessionAndMissingCertificate()throws Exception {
        var reads=new java.util.concurrent.atomic.AtomicInteger();var trust=mock(ServiceCertificateIdentity.Trust.class);
        when(trust.ciphers()).thenReturn(java.util.Set.of("TLS_AES_128_GCM_SHA256"));
        var real=new ServiceCertificateIdentity(()->{reads.incrementAndGet();return trust;});var request=request("POST",SimulationWorkerSecurityConfiguration.ROUTES.get(0));
        when(request.getSession(false)).thenReturn(mock(jakarta.servlet.http.HttpSession.class));
        assertFalse(SimulationWorkerSecurityConfiguration.authorize(real,request));assertEquals(0,reads.get());
        when(request.isSecure()).thenReturn(true);when(request.getHeader("Cookie")).thenReturn("MEDICAL_SESSION=existing");
        assertFalse(SimulationWorkerSecurityConfiguration.authorize(real,request));assertEquals(0,reads.get());
        when(request.getHeader("Cookie")).thenReturn(null);when(request.getHeader("Sec-Fetch-Site")).thenReturn("same-origin");
        assertFalse(SimulationWorkerSecurityConfiguration.authorize(real,request));assertEquals(0,reads.get());
        when(request.getHeader("Sec-Fetch-Site")).thenReturn(null);when(request.getAttribute("jakarta.servlet.request.cipher_suite")).thenReturn("TLS_AES_128_GCM_SHA256");
        assertFalse(SimulationWorkerSecurityConfiguration.authorize(real,request));assertEquals(1,reads.get());
    }
    @Test void disabledOrAbsentFlagCreatesNoWorkerSecurityChain() {
        for(String value:java.util.List.of("","false"))try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()){
            if(!value.isEmpty())context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",java.util.Map.of("medical.simulation-execution.enabled",value)));
            context.register(SimulationWorkerSecurityConfiguration.class);context.refresh();assertTrue(context.getBeansOfType(org.springframework.security.web.SecurityFilterChain.class).isEmpty());
        }
    }
}
