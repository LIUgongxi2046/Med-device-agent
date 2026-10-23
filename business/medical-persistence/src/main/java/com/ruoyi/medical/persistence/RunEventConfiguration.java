package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

@Configuration
class RunEventConfiguration {
    @Bean FilterRegistrationBean<OncePerRequestFilter> runEventStream(ApplicationSessionIdentity identities,
            RunEventTransport transport, PermissionSchema json, org.springframework.web.servlet.DispatcherServlet dispatcher) {
        var pattern = new PathPatternParser().parse("/api/runs/{runId}/events");
        var writer = new ServerSentEventHttpMessageWriter(new Jackson2JsonEncoder());
        var filter = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
                var route = pattern.matchAndExtract(PathContainer.parsePath(request.getRequestURI()));
                if (route == null || !"GET".equals(request.getMethod())) { chain.doFilter(request, response); return; }
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                var handler = WebHttpHandlerBuilder.webHandler(exchange -> Mono.defer(() -> {
                    for (String name : java.util.List.of("X-Schema-Version", "X-Context-Revision"))
                        if (Collections.list(request.getHeaders(name)).size() != 1) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
                    if (!"1.0.0".equals(request.getHeader("X-Schema-Version"))) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
                    String context = request.getHeader("X-Context-Revision"), last = request.getHeader("Last-Event-ID"), after = request.getParameter("after");
                    PermissionSchema.revision(context);
                    if (Collections.list(request.getHeaders("Last-Event-ID")).size() > 1) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
                    for (var parameter : request.getParameterMap().keySet())
                        if (!parameter.equals("after") || request.getParameterValues(parameter).length != 1) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
                    if (last != null) json.requireId(last); if (after != null) json.requireId(after);
                    if (last != null && after != null && !last.equals(after)) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
                    String run = route.getUriVariables().get("runId"); json.requireId(run);
                    var subject = identities.requireCurrent(request, authentication);
                    exchange.getResponse().getHeaders().set("X-Schema-Version", "1.0.0"); exchange.getResponse().getHeaders().set("X-Accel-Buffering", "no");
                    return writer.write(transport.subscribe(subject, context, run, last == null ? after : last, authentication),
                        ResolvableType.forClassWithGenerics(ServerSentEvent.class, com.fasterxml.jackson.databind.JsonNode.class),
                        MediaType.TEXT_EVENT_STREAM, exchange.getResponse(), java.util.Map.of());
                })).exceptionHandler((exchange, failure) -> {
                    if (exchange.getResponse().isCommitted()) return Mono.error(failure);
                    ErrorCode code;
                    if (failure instanceof TenantAccessException denied) code = denied.code();
                    else if (failure instanceof SchemaRejection invalid) code = invalid.code(); else return Mono.error(failure);
                    int status = switch (code) { case ACCESS_DENIED -> 403; case RATE_LIMITED -> 429; case SCHEMA_INVALID, SCHEMA_UNSUPPORTED, CONTENT_MISMATCH -> 422; default -> 409; };
                    String requestId = java.util.UUID.randomUUID().toString();
                    byte[] body = ThrowingSupplier.of(() -> new IdentityErrorResponseWriter().encode(code, requestId)).get();
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.valueOf(status));
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    exchange.getResponse().getHeaders().set("X-Schema-Version", "1.0.0"); exchange.getResponse().getHeaders().set("X-Request-Id", requestId);
                    return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
                }).build();
                response.setHeader("Cache-Control", "no-store");
                var adapter = new ServletHttpHandlerAdapter(handler); adapter.setBufferSize(8192);
                adapter.init(java.util.Objects.requireNonNull(dispatcher.getServletConfig())); adapter.service(request, response);
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter); registration.setName("medicalRunEvents"); registration.setOrder(-90);
        registration.setAsyncSupported(true); registration.addUrlPatterns("/api/runs/*"); registration.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST); return registration;
    }
}
