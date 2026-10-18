package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController public final class ExpertQualificationFeatureAvailabilityController {
 private final ApplicationSessionIdentity identities;private final ExpertQualificationFeatureAvailability availability;
 public ExpertQualificationFeatureAvailabilityController(ApplicationSessionIdentity identities,ExpertQualificationFeatureAvailability availability){this.identities=identities;this.availability=availability;}
 @GetMapping("/api/expert-qualifications/availability") public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication){String context=ExpertQualificationHttp.headers(request,response,false);var value=availability.read(identities.requireCurrent(request,authentication),context);String id=java.util.UUID.randomUUID().toString();response.setHeader("X-Request-Id",id);return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().valueToTree(new com.ruoyi.medical.contracts.InterfaceProtocol.Envelope("1.0.0",id,"1",value));}
 @ExceptionHandler(Exception.class)void failed(Exception failure,HttpServletResponse response)throws java.io.IOException{ExpertQualificationHttp.failure(failure,response);}
}
