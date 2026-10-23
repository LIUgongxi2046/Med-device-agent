package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class DocumentWorkspaceAvailabilityController {
    private final ApplicationSessionIdentity identities;private final EnterpriseAccess access;private final ListableBeanFactory beans;private final PermissionSchema json;private final DocumentWorkspaceSchema schema;
    public DocumentWorkspaceAvailabilityController(ApplicationSessionIdentity identities,EnterpriseAccess access,ListableBeanFactory beans,PermissionSchema json,DocumentWorkspaceSchema schema){this.identities=identities;this.access=access;this.beans=beans;this.json=json;this.schema=schema;}
    @GetMapping("/api/document-workspace-availability")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws IOException{
        String context=BusinessAuditController.headers(request,response);if(request.getQueryString()!=null||request.getInputStream().read()!=-1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var result=access.execute(identities.requireCurrent(request,authentication),admission->{
            if(!context.equals(admission.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var value=json.object().put("schema_version","1.0.0").put("candidate_adoption_enabled",enabled(DocumentCandidateAdoptionController.class))
                .put("professional_review_enabled",enabled(DocumentReviewExecutionController.class)).put("review_confirmation_enabled",enabled(DocumentReviewConfirmationController.class)).put("document_signing_enabled",enabled(DocumentSigningController.class));
            value.set("current_context",json.tree(admission.context()));schema.validate("Availability",value);
            return json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),context,value));
        });response.setHeader("X-Request-Id",result.required("request_id").asText());return result;
    }
    private boolean enabled(Class<?> type){return beans.getBeanNamesForType(type,false,false).length>0;}
    @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,failure.code());}
    @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,SimulationRuleVersionController.errorStatus(failure.code()),failure.code());}
}
