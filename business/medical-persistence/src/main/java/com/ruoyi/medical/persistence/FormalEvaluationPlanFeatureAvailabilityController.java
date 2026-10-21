package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class FormalEvaluationPlanFeatureAvailabilityController {
    private final ApplicationSessionIdentity identities;
    private final FormalEvaluationPlanFeatureAvailability availability;
    private final PermissionSchema json;
    public FormalEvaluationPlanFeatureAvailabilityController(ApplicationSessionIdentity identities,
            FormalEvaluationPlanFeatureAvailability availability, PermissionSchema json) {
        this.identities = identities; this.availability = availability; this.json = json;
    }
    @GetMapping("/api/formal-evaluation-plan-feature-availability")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String context = FormalEvaluationContextHttp.headers(request, response, false, false);
        return FormalEvaluationContextHttp.envelope(availability.read(identities.requireCurrent(request, authentication), context), response, json);
    }
    @ExceptionHandler(Exception.class)
    void failure(Exception failure, HttpServletResponse response) throws IOException { FormalEvaluationContextHttp.failure(failure, response); }
}
