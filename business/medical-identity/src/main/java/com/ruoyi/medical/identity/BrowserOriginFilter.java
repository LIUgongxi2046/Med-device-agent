package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.filter.OncePerRequestFilter;

public final class BrowserOriginFilter extends OncePerRequestFilter {
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final String origin;

    public BrowserOriginFilter(OidcPolicy policy) {
        this.origin = policy.browserOrigin().toASCIIString();
    }

    public BrowserOriginFilter(LocalIdentityPolicy policy) {
        this.origin = policy.browserOrigin().toASCIIString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!READ_METHODS.contains(request.getMethod())
                && !Collections.list(request.getHeaders("Origin")).equals(java.util.List.of(origin))) {
            throw new AccessDeniedException(ErrorCode.ACCESS_DENIED.name());
        }
        chain.doFilter(request, response);
    }
}
