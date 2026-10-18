package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** Always registered read-only discovery; unavailable runtime is explicit data, never an invented candidate. */
@RestController public final class ModelVerificationTokenizerController {
 private final ApplicationSessionIdentity identities;private final ModelVerificationTokenizerCandidates service;private final PermissionSchema json;
 public ModelVerificationTokenizerController(ApplicationSessionIdentity identities,ModelVerificationTokenizerCandidates service,PermissionSchema json){this.identities=identities;this.service=service;this.json=json;}
 @GetMapping("/api/model-verification-tokenizer-candidates/{modelRevision}")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String modelRevision)throws Exception{
  String context=ModelVerificationInputHttp.headers(request,response,false);var result=service.read(identities.requireCurrent(request,authentication),context,modelRevision);String id=java.util.UUID.randomUUID().toString();response.setHeader("X-Request-Id",id);return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,"1",result));
 }
 @ExceptionHandler(Exception.class)void failure(Exception failure,HttpServletResponse response)throws java.io.IOException{ModelVerificationInputHttp.failure(failure,response);}
}
