package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController public final class FormalEvaluationInputFeatureAvailabilityController {
 private final ApplicationSessionIdentity identities;private final FormalEvaluationInputFeatureAvailability availability;private final PermissionSchema json;
 public FormalEvaluationInputFeatureAvailabilityController(ApplicationSessionIdentity identities,FormalEvaluationInputFeatureAvailability availability,PermissionSchema json){this.identities=identities;this.availability=availability;this.json=json;}
 @GetMapping("/api/formal-evaluation-input-feature-availability")public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication){String context=FormalEvaluationInputHttp.headers(request,response,false);String id=java.util.UUID.randomUUID().toString();response.setHeader("X-Request-Id",id);return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,"1",availability.read(identities.requireCurrent(request,authentication),context)));}
 @ExceptionHandler(Exception.class)void failed(Exception failure,HttpServletResponse response)throws java.io.IOException{FormalEvaluationInputHttp.failure(failure,response);}
}
