package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class FrameworkBindingController {
    private final ApplicationSessionIdentity identities;private final FrameworkBindingService service;
    public FrameworkBindingController(ApplicationSessionIdentity identities,FrameworkBindingService service){this.identities=identities;this.service=service;}
    @GetMapping("/api/management-bindings")
    public JsonNode discover(HttpServletRequest request,HttpServletResponse response,Authentication auth){return reply(response,service.discover(identities.requireCurrent(request,auth),headers(request)));}
    @GetMapping("/api/management-bindings/catalogue")
    public JsonNode catalogue(HttpServletRequest request,HttpServletResponse response,Authentication auth){return reply(response,service.catalogue(identities.requireCurrent(request,auth),headers(request)));}
    @GetMapping("/api/management-bindings/drafts/{releaseId}")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String releaseId){return reply(response,service.readDraft(identities.requireCurrent(request,auth),headers(request),releaseId));}
    @PostMapping(value="/api/management-bindings/drafts",consumes="application/json")
    public JsonNode draft(HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception{var subject=identities.requireCurrent(request,auth);String context=headers(request),key=single(request,"Idempotency-Key");return reply(response,service.draft(subject,context,key,body(request)));}
    @PostMapping(value="/api/management-bindings/approvals",consumes="application/json")
    public JsonNode approve(HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception{var subject=identities.requireCurrent(request,auth);String context=headers(request),key=single(request,"Idempotency-Key");return reply(response,service.approve(subject,context,key,body(request)));}
    @PostMapping(value="/api/management-bindings/revocations",consumes="application/json")
    public JsonNode revoke(HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception{var subject=identities.requireCurrent(request,auth);String context=headers(request),key=single(request,"Idempotency-Key");return reply(response,service.revoke(subject,context,key,body(request)));}
    private static byte[] body(HttpServletRequest r)throws IOException{if(r.getContentLengthLong()>FrameworkBindingSchema.MAX_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=r.getInputStream().readNBytes(FrameworkBindingSchema.MAX_BYTES+1);if(bytes.length>FrameworkBindingSchema.MAX_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;}
    private static String headers(HttpServletRequest r){if(!"1.0.0".equals(single(r,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String c=single(r,"X-Context-Revision");PermissionSchema.revision(c);if(r.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return c;}
    private static String single(HttpServletRequest r,String name){var values=r.getHeaders(name);if(values==null||!values.hasMoreElements())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);String value=values.nextElement();if(values.hasMoreElements())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return value;}
    private static JsonNode reply(HttpServletResponse r,JsonNode value){r.setHeader("Cache-Control","no-store");r.setHeader("X-Schema-Version","1.0.0");r.setHeader("X-Request-Id",value.required("request_id").asText());return value;}
    @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
    @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,e.code()==ErrorCode.ACCESS_DENIED?403:e.code()==ErrorCode.DEPENDENCY_UNAVAILABLE?503:e.code()==ErrorCode.FILE_TOO_LARGE?413:409,e.code());}
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,IllegalArgumentException.class})void malformed(Exception e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
}
