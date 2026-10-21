package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class FormalEvaluationContextFeatureAvailabilityController {
    private final ApplicationSessionIdentity identities;
    private final FormalEvaluationContextFeatureAvailability availability;
    private final PermissionSchema json;
    public FormalEvaluationContextFeatureAvailabilityController(ApplicationSessionIdentity identities,
            FormalEvaluationContextFeatureAvailability availability, PermissionSchema json) {
        this.identities = identities; this.availability = availability; this.json = json;
    }
    @GetMapping("/api/formal-evaluation-context-feature-availability")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String context = FormalEvaluationContextHttp.headers(request, response, false, false);
        return FormalEvaluationContextHttp.envelope(availability.read(identities.requireCurrent(request, authentication), context), response, json);
    }
    @ExceptionHandler(Exception.class)
    void failure(Exception failure, HttpServletResponse response) throws IOException { FormalEvaluationContextHttp.failure(failure, response); }
}
