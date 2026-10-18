package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class GruRecordExportFeatureAvailabilityController {
    private final ApplicationSessionIdentity identities;
    private final GruRecordExportFeatureAvailability availability;
    private final PermissionSchema json;

    public GruRecordExportFeatureAvailabilityController(ApplicationSessionIdentity identities, GruRecordExportFeatureAvailability availability, PermissionSchema json) {
        this.identities = identities;
        this.availability = availability;
        this.json = json;
    }

    @GetMapping("/api/gru-record-export-feature-availability")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String version = SimulationInputArtifactController.one(request, "X-Schema-Version");
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context = SimulationInputArtifactController.one(request, "X-Context-Revision");
        PermissionSchema.revision(context);
        if (request.getQueryString() != null || request.getContentLengthLong() > 0) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var data = availability.read(identities.requireCurrent(request, authentication), context);
        String requestId = UUID.randomUUID().toString();
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", requestId);
        return json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, context, data));
    }

    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, SimulationRuleVersionController.errorStatus(failure.code()), failure.code());
    }
}
