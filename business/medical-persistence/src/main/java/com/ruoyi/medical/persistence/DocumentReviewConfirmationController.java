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
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="medical.professional-runtime.document-review-enabled",havingValue="true")
public final class DocumentReviewConfirmationController {
 private final ApplicationSessionIdentity identities;private final DocumentReviewConfirmationService service;
 public DocumentReviewConfirmationController(ApplicationSessionIdentity identities,DocumentReviewConfirmationService service){this.identities=identities;this.service=service;}
 @PostMapping(value="/api/documents/{id}/review-batches",consumes="application/json")
 public JsonNode batch(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable("id")String id)throws Exception{return decide(q,r,auth,id,false);}
 @PostMapping(value="/api/documents/{id}/review-decisions",consumes="application/json")
 public JsonNode single(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable("id")String id)throws Exception{return decide(q,r,auth,id,true);}
 private JsonNode decide(HttpServletRequest q,HttpServletResponse r,Authentication auth,String id,boolean single)throws Exception{String context=headers(q,r,true);return result(r,service.decide(identities.requireCurrent(q,auth),context,SimulationInputArtifactController.one(q,"Idempotency-Key"),id,body(q),single));}
 @PostMapping(value="/api/documents/{id}/review-resubmissions",consumes="application/json")
 public JsonNode resubmit(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable("id")String id)throws Exception{String c=headers(q,r,true);return result(r,service.resubmit(identities.requireCurrent(q,auth),c,SimulationInputArtifactController.one(q,"Idempotency-Key"),id,body(q)));}
 @GetMapping("/api/documents/{id}/review-confirmation-operations")
 public JsonNode recover(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable("id")String id)throws Exception{String c=headers(q,r,false);noBody(q);return result(r,service.recover(identities.requireCurrent(q,auth),c,SimulationInputArtifactController.one(q,"Idempotency-Key"),id,SimulationInputArtifactController.one(q,"X-Document-Revision")));}
 @GetMapping("/api/documents/{id}/review-confirmation-history")
 public JsonNode history(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable("id")String id)throws Exception{String c=headers(q,r,false);noBody(q);return result(r,service.history(identities.requireCurrent(q,auth),c,id,SimulationInputArtifactController.one(q,"X-Document-Revision")));}
 private static String headers(HttpServletRequest q,HttpServletResponse r,boolean write){if(q.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);r.setHeader("Cache-Control","no-store");r.setHeader("X-Schema-Version","1.0.0");r.setHeader("X-Content-Type-Options","nosniff");if(!"1.0.0".equals(SimulationInputArtifactController.one(q,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String c=SimulationInputArtifactController.one(q,"X-Context-Revision");PermissionSchema.revision(c);if(write)SimulationInputArtifactController.one(q,"X-CSRF-TOKEN");return c;}
 private static byte[] body(HttpServletRequest q)throws IOException{byte[] b=q.getInputStream().readNBytes(1048577);if(b.length>1048576)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return b;}
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
                    case SCHEMA_INVALID, SCHEMA_UNSUPPORTED, CONTENT_MISMATCH, DEPENDENCY_UNAVAILABLE, CONTEXT_LIMIT -> 422;
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
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
