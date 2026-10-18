package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @ConditionalOnProperty(prefix="medical.formal-evaluation-policy",name="enabled",havingValue="true",matchIfMissing=false)
public final class FormalEvaluationPolicyController {
 private final ApplicationSessionIdentity identities;private final EvaluationAcceptancePolicyService service;private final FormalEvaluationPolicySchema schema;private final PermissionSchema json;
 public FormalEvaluationPolicyController(ApplicationSessionIdentity identities,EvaluationAcceptancePolicyService service,FormalEvaluationPolicySchema schema,PermissionSchema json){this.identities=identities;this.service=service;this.schema=schema;this.json=json;}
 @PostMapping(value="/api/formal-evaluation-policies",consumes="application/json")
 public JsonNode author(HttpServletRequest q,HttpServletResponse r,Authentication a)throws Exception{return write(q,r,a,false);}
 @PostMapping(value="/api/formal-evaluation-policy-decisions",consumes="application/json")
 public JsonNode decide(HttpServletRequest q,HttpServletResponse r,Authentication a)throws Exception{return write(q,r,a,true);}
 private JsonNode write(HttpServletRequest q,HttpServletResponse r,Authentication a,boolean decide)throws Exception{String context=FormalEvaluationInputHttp.headers(q,r,true),key=FormalEvaluationInputHttp.one(q,"Idempotency-Key");SimulationInputArtifactSchema.key(key);var subject=identities.requireCurrent(q,a);if(q.getContentLengthLong()>FormalEvaluationPolicySchema.MAXIMUM_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=q.getInputStream().readNBytes(FormalEvaluationPolicySchema.MAXIMUM_BYTES+1);if(bytes.length>FormalEvaluationPolicySchema.MAXIMUM_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);var request=schema.request(decide?"Decide":"Author",bytes);var result=decide?service.decide(subject,context,key,request):service.author(subject,context,key,request);r.setStatus(201);return envelope(result,r);}
 @GetMapping("/api/formal-evaluation-policies/{id}")public JsonNode read(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String id)throws Exception{String context=FormalEvaluationInputHttp.headers(q,r,false);WireValues.uuid(id);return envelope(service.read(identities.requireCurrent(q,a),context,id),r);}
 @GetMapping("/api/formal-evaluation-policies/{id}/operation-receipts/{operation}")public JsonNode recover(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String id,@PathVariable String operation)throws Exception{String context=FormalEvaluationInputHttp.headers(q,r,false);WireValues.uuid(id);WireValues.uuid(operation);return envelope(service.recover(identities.requireCurrent(q,a),context,id,operation),r);}
 private JsonNode envelope(JsonNode data,HttpServletResponse r){String id=java.util.UUID.randomUUID().toString();r.setHeader("X-Request-Id",id);return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,"1",data));}
 @ExceptionHandler(Exception.class)void failed(Exception failure,HttpServletResponse response)throws IOException{FormalEvaluationInputHttp.failure(failure,response);}
}
