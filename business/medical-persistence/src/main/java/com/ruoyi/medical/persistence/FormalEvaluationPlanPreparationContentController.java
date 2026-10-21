package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.contracts.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.*;
import java.io.IOException;

@RestController
@ConditionalOnProperty(prefix="medical.formal-evaluation-plan",name="enabled",havingValue="true",matchIfMissing=false)
public final class FormalEvaluationPlanPreparationContentController {
    private final ApplicationSessionIdentity identities;
    private final ObjectProvider<FormalEvaluationPlanPreparationContentService> services;
    private final PermissionSchema json;
    public FormalEvaluationPlanPreparationContentController(ApplicationSessionIdentity identities,ObjectProvider<FormalEvaluationPlanPreparationContentService> services,PermissionSchema json){this.identities=identities;this.services=services;this.json=json;}
    @PostMapping(value="/api/formal-evaluation-plan-preparations/content",consumes="application/json")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception {
        String context=FormalEvaluationContextHttp.headers(request,response,true,false);var actor=identities.requireCurrent(request,authentication);
        var bytes=FormalEvaluationContextHttp.body(request,FormalEvaluationPlanPreparationSchema.MAXIMUM_BYTES);
        var service=services.getIfUnique();if(service==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        return FormalEvaluationContextHttp.envelope(service.read(actor,context,bytes),response,json);
    }
    @ExceptionHandler(Exception.class) public void failure(Exception failure,HttpServletResponse response)throws IOException{FormalEvaluationContextHttp.failure(failure,response);}
}
