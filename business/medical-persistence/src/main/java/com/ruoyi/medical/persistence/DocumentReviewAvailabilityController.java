package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Authenticated deployment state only. No resource permission is granted by this response. */
@RestController
public final class DocumentReviewAvailabilityController {
    private final ApplicationSessionIdentity identities;
    private final EnterpriseAccess access;
    private final ListableBeanFactory beans;
    private final PermissionSchema json;
    private final DocumentReviewReturnSchema schema;
    public DocumentReviewAvailabilityController(ApplicationSessionIdentity identities,EnterpriseAccess access,ListableBeanFactory beans,
            PermissionSchema json,DocumentReviewReturnSchema schema) {
        this.identities=identities;this.access=access;this.beans=beans;this.json=json;this.schema=schema;
    }
    @GetMapping("/api/document-review-availability")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication) throws IOException {
        if(!"1.0.0".equals(one(request,"X-Schema-Version"))) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context=one(request,"X-Context-Revision"); PermissionSchema.revision(context);
        if(request.getQueryString()!=null || request.getInputStream().read()!=-1) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var subject=identities.requireCurrent(request,authentication);
        var result=access.execute(subject,admission -> {
            if(!context.equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var data=json.object().put("schema_version","1.0.0")
                    .put("review_return_enabled",beans.getBeanNamesForType(DocumentReviewReturnController.class,false,false).length>0);
            data.set("current_context",json.tree(admission.context())); schema.validate("Availability",data);
            return json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),context,data));
        });
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");
        response.setHeader("X-Request-Id",result.required("request_id").asText());return result;
    }
    private static String one(HttpServletRequest request,String name) {
        var values=Collections.list(request.getHeaders(name));
        if(values.size()!=1 || values.get(0)==null || values.get(0).isBlank()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return values.get(0);
    }
    @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection failure,HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response,422,failure.code());
    }
    @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException failure,HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response,SimulationRuleVersionController.errorStatus(failure.code()),failure.code());
    }
}
