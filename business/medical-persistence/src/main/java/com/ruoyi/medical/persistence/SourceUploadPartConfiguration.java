package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

@Configuration
class SourceUploadPartConfiguration {
    @Bean FilterRegistrationBean<OncePerRequestFilter> sourceUploadParts(ApplicationSessionIdentity identities,
            SourceUploadTransport transport, PermissionSchema json, org.springframework.web.servlet.DispatcherServlet dispatcher) {
        var pattern = new PathPatternParser().parse("/api/uploads/{uploadId}/parts/{partNo}");
        var filter = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                var route = pattern.matchAndExtract(PathContainer.parsePath(request.getRequestURI()));
                if (route == null || !"PUT".equals(request.getMethod())) { chain.doFilter(request, response); return; }
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                var handler = WebHttpHandlerBuilder.webHandler(exchange -> Mono.defer(() -> {
                    for (String name : java.util.List.of("X-Schema-Version", "X-Context-Revision", "Idempotency-Key", "Content-SHA256", "Content-Length", "Content-Type"))
                        if (java.util.Collections.list(request.getHeaders(name)).size() != 1) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
                    if (!"1.0.0".equals(request.getHeader("X-Schema-Version"))) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
                    if (request.getQueryString() != null || !"application/octet-stream".equals(request.getContentType())) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
                    String context = request.getHeader("X-Context-Revision"), key = request.getHeader("Idempotency-Key"), sha256 = request.getHeader("Content-SHA256");
                    json.headers(context, key); WireValues.hash(sha256);
                    String upload = route.getUriVariables().get("uploadId"), number = route.getUriVariables().get("partNo"); WireValues.uuid(upload);
                    if (!number.matches("[1-9][0-9]{0,9}") || Long.parseLong(number) > 4294967295L) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
                    var subject = identities.requireCurrent(request, authentication);
                    return transport.receive(subject, context, key, upload, Long.parseLong(number), sha256, exchange.getRequest()).flatMap(result -> {
                        var value = result.body();
                        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.valueOf(result.status()));
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        exchange.getResponse().getHeaders().set("X-Schema-Version", "1.0.0");
                        exchange.getResponse().getHeaders().set("X-Request-Id", value.required("request_id").textValue());
                        byte[] bytes = ThrowingSupplier.of(() -> json.canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)).get();
                        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
                    });
                })).exceptionHandler((exchange, failure) -> {
                    ErrorCode code;
                    if (failure instanceof TenantAccessException denied) code = denied.code();
                    else if (failure instanceof SchemaRejection invalid) code = invalid.code();
                    else return Mono.error(failure);
                    int status = switch (code) {
                        case ACCESS_DENIED -> 403;
                        case RATE_LIMITED -> 429;
                        case FILE_TOO_LARGE -> 413;
                        case SCHEMA_INVALID, CONTENT_MISMATCH, SCHEMA_UNSUPPORTED -> 422;
                        default -> 409;
                    };
                    String requestId = java.util.UUID.randomUUID().toString();
                    byte[] bytes = ThrowingSupplier.of(() -> new IdentityErrorResponseWriter().encode(code, requestId)).get();
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.valueOf(status));
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    exchange.getResponse().getHeaders().set("X-Schema-Version", "1.0.0");
                    exchange.getResponse().getHeaders().set("X-Request-Id", requestId);
                    return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
                }).build();
                // Spring适配器直接接收Servlet异步数据，接收线程及总期限由传输组件管理。
                response.setHeader("Cache-Control", "no-store");
                var adapter = new ServletHttpHandlerAdapter(handler);
                adapter.setBufferSize(transport.segmentBytes());
                adapter.init(java.util.Objects.requireNonNull(dispatcher.getServletConfig()));
                adapter.service(request, response);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(filter); registration.setName("medicalSourceUploadParts");
        registration.setOrder(-90); registration.setAsyncSupported(true); registration.addUrlPatterns("/api/uploads/*");
        registration.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST); return registration;
    }
}
