package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
public final class FrameworkNavigationController {
    private final ApplicationSessionIdentity identities;
    private final FrameworkNavigation navigation;
    public FrameworkNavigationController(ApplicationSessionIdentity identities, FrameworkNavigation navigation) {
        this.identities = identities; this.navigation = navigation;
    }

    @GetMapping("/api/framework/navigation")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value = "X-Schema-Version", required = false) String version) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (!request.getParameterMap().isEmpty()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var result = navigation.read(identities.requireCurrent(request, authentication));
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-ID", result.required("request_id").textValue()); return result;
    }

    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
    @ExceptionHandler(LocalAccountConflict.class)
    void denied(LocalAccountConflict failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 403, failure.code());
    }
}
