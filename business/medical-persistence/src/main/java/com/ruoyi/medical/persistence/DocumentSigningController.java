package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="medical.signing.enabled",havingValue="true")
public final class DocumentSigningController {
 private final ApplicationSessionIdentity identities;private final DocumentSigningService service;
 public DocumentSigningController(ApplicationSessionIdentity i,DocumentSigningService s){identities=i;service=s;}
 @PostMapping(value="/api/projects/{project}/signing-policies",consumes="application/json")
 public JsonNode draft(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String project)throws Exception{String c=headers(q,r,true);return result(r,service.draftPolicy(identities.requireCurrent(q,a),c,project,one(q,"Idempotency-Key"),body(q)));}
 @PostMapping(value="/api/projects/{project}/signing-policy-approvals",consumes="application/json")
 public JsonNode approve(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String project)throws Exception{String c=headers(q,r,true);return result(r,service.approvePolicy(identities.requireCurrent(q,a),c,project,one(q,"Idempotency-Key"),body(q)));}
 @GetMapping("/api/documents/{id}/signing-policy")
 public JsonNode policy(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String id)throws Exception{String c=headers(q,r,false);noBody(q);return result(r,service.policy(identities.requireCurrent(q,a),c,id,one(q,"X-Document-Revision"),one(q,"X-Signing-Purpose")));}
 @GetMapping("/api/documents/{id}/signing-readiness")
 public JsonNode readiness(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String id)throws Exception{String c=headers(q,r,false);noBody(q);return result(r,service.readiness(identities.requireCurrent(q,a),c,id,one(q,"X-Document-Revision"),one(q,"X-Signing-Purpose")));}
 @PostMapping(value="/api/reauthentication-challenges",consumes="application/json")
 public JsonNode challenge(HttpServletRequest q,HttpServletResponse r,Authentication a)throws Exception{String c=headers(q,r,true);return result(r,service.challenge(identities.requireCurrent(q,a),c,one(q,"Idempotency-Key"),body(q)));}
 @GetMapping("/api/reauthentication-challenges/{id}")
 public JsonNode challengeStatus(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String id)throws Exception{String c=headers(q,r,false);noBody(q);return result(r,service.challengeStatus(identities.requireCurrent(q,a),c,id));}
 @GetMapping("/api/signing/oidc/callback")
 public JsonNode callback(HttpServletRequest q,HttpServletResponse r,Authentication a)throws Exception{secure(r);noBody(q);if(!q.getParameterMap().keySet().equals(Set.of("state","code"))||q.getParameterValues("state").length!=1||q.getParameterValues("code").length!=1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return result(r,service.callback(identities.requireCurrent(q,a),q.getParameter("state"),q.getParameter("code")));}
 @PostMapping(value="/api/reauthentication-challenges/{id}/completions",consumes="application/json")
 public JsonNode complete(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String id)throws Exception{String c=headers(q,r,true);return result(r,service.complete(identities.requireCurrent(q,a),c,one(q,"Idempotency-Key"),id,body(q)));}
 @PostMapping(value="/api/documents/{id}/signatures",consumes="application/json")
 public JsonNode sign(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String id)throws Exception{String c=headers(q,r,true);return result(r,service.sign(identities.requireCurrent(q,a),c,id,one(q,"Idempotency-Key"),body(q)));}
 @GetMapping("/api/documents/{id}/signatures")
 public JsonNode history(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String id)throws Exception{String c=headers(q,r,false,"1.1.0");noBody(q);return result(r,service.history(identities.requireCurrent(q,a),c,id,one(q,"X-Document-Revision")));}
 @GetMapping("/api/documents/{id}/signatures/{signature}/snapshot")
 public JsonNode snapshot(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String id,@PathVariable String signature)throws Exception{String c=headers(q,r,false);noBody(q);return result(r,service.snapshot(identities.requireCurrent(q,a),c,id,one(q,"X-Document-Revision"),signature));}
 @GetMapping("/api/documents/{id}/signature-operations")
 public JsonNode operation(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String id)throws Exception{String c=headers(q,r,false);noBody(q);return result(r,service.operation(identities.requireCurrent(q,a),c,id,one(q,"X-Document-Revision"),one(q,"Idempotency-Key")));}
 private static String one(HttpServletRequest q,String h){return SimulationInputArtifactController.one(q,h);}
 private static void secure(HttpServletResponse r){r.setHeader("Cache-Control","no-store");r.setHeader("Referrer-Policy","no-referrer");r.setHeader("X-Content-Type-Options","nosniff");r.setHeader("X-Schema-Version","1.0.0");}
 private static String headers(HttpServletRequest q,HttpServletResponse r,boolean write){return headers(q,r,write,"1.0.0");}
 private static String headers(HttpServletRequest q,HttpServletResponse r,boolean write,String version){secure(r);r.setHeader("X-Schema-Version",version);if(q.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);if(!version.equals(one(q,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String c=one(q,"X-Context-Revision");PermissionSchema.revision(c);if(write)one(q,"X-CSRF-TOKEN");return c;}
 private static byte[] body(HttpServletRequest q)throws IOException{byte[] b=q.getInputStream().readNBytes(65537);if(b.length>65536)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return b;}
 private static void noBody(HttpServletRequest q)throws IOException{if(q.getInputStream().read()!=-1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 private static JsonNode result(HttpServletResponse r,JsonNode n){r.setHeader("X-Request-Id",n.required("request_id").asText());r.setHeader("X-Resource-Revision",n.required("resource_revision").asText());return n;}
    @ExceptionHandler(Exception.class)
    void failed(Exception failure, HttpServletResponse response) throws IOException {
        // Unwrap without logging request text, SQL, object locations, or provider exception messages.
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SchemaRejection rejection) { write(response, 422, rejection.code()); return; }
            if (cause instanceof TenantAccessException denial) {
                int status = switch (denial.code()) {
                    case ACCESS_DENIED -> 403;
                    case OBJECT_UNAVAILABLE, SOURCE_MISSING -> 404;
                    case FILE_TOO_LARGE -> 413;
                    case RATE_LIMITED -> 429;
                    case SIGNING_POLICY_UNCONFIGURED, SCHEMA_INVALID, SCHEMA_UNSUPPORTED, CONTENT_MISMATCH, DEPENDENCY_UNAVAILABLE, CONTEXT_LIMIT -> 422;
                    default -> 409;
                };
                write(response, status, denial.code()); return;
            }
            if (cause instanceof com.fasterxml.jackson.core.JsonProcessingException
                    || cause instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
                write(response, 422, ErrorCode.SCHEMA_INVALID); return;
            }
            if (cause instanceof org.springframework.security.core.AuthenticationException) {
                write(response, 401, ErrorCode.AUTHENTICATION_REQUIRED); return;
            }
            if (cause instanceof org.springframework.security.access.AccessDeniedException) {
                write(response, 403, ErrorCode.ACCESS_DENIED); return;
            }
        }
        write(response, 500, ErrorCode.INTERNAL_ERROR);
    }
    private static void write(HttpServletResponse response, int status, ErrorCode code) throws IOException {
        new IdentityErrorResponseWriter().write(response, status, code);
    }
}
