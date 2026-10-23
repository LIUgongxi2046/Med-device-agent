package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.*;
import com.ruoyi.framework.web.service.PermissionService;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="medical.identity.mode",havingValue="local")
public class FrameworkFormController {
    private final ApplicationSessionIdentity identities;private final PermissionService permissions;private final FrameworkFormService service;
    public FrameworkFormController(ApplicationSessionIdentity identities,PermissionService permissions,FrameworkFormService service){this.identities=identities;this.permissions=permissions;this.service=service;}
    @GetMapping("/api/tool/build/schemas") @PreAuthorize("@ss.hasPermi('tool:build:list')")
    public JsonNode list(HttpServletRequest r,HttpServletResponse response,Authentication auth){return reply(response,service.list(current(r,auth),headers(r)));}
    @PostMapping(value="/api/tool/build/schemas",consumes="application/json") @PreAuthorize("@ss.hasPermi('tool:build:list')")
    public JsonNode draft(HttpServletRequest r,HttpServletResponse response,Authentication auth)throws Exception{return reply(response,service.draft(current(r,auth),headers(r),single(r,"Idempotency-Key"),body(r)));}
    @GetMapping("/api/tool/build/schemas/{id}") @PreAuthorize("@ss.hasPermi('tool:build:list')")
    public JsonNode read(@PathVariable String id,HttpServletRequest r,HttpServletResponse response,Authentication auth){return reply(response,service.read(current(r,auth),headers(r),id,false));}
    @GetMapping("/api/tool/build/schemas/{id}/published") @PreAuthorize("@ss.hasPermi('tool:build:list')")
    public JsonNode consume(@PathVariable String id,HttpServletRequest r,HttpServletResponse response,Authentication auth){return reply(response,service.read(current(r,auth),headers(r),id,true));}
    @PostMapping(value="/api/tool/build/schemas/{id}/approvals",consumes="application/json") @PreAuthorize("@ss.hasPermi('tool:build:list')")
    public JsonNode approve(@PathVariable String id,HttpServletRequest r,HttpServletResponse response,Authentication auth)throws Exception{return reply(response,service.approve(current(r,auth),headers(r),id,single(r,"Idempotency-Key"),body(r)));}
    private ApplicationSessionIdentity.Verified current(HttpServletRequest r,Authentication auth){var subject=identities.requireCurrent(r,auth);if(!"PLATFORM_ADMIN".equals(subject.user().platform_role())||!permissions.hasPermi("tool:build:list"))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return subject;}
    private static byte[] body(HttpServletRequest r)throws IOException{if(r.getContentLengthLong()>FrameworkFormSchema.MAX_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);var bytes=r.getInputStream().readNBytes(FrameworkFormSchema.MAX_BYTES+1);if(bytes.length>FrameworkFormSchema.MAX_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;}
    private static String headers(HttpServletRequest r){if(!"1.0.0".equals(single(r,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String context=single(r,"X-Context-Revision");if(!context.matches("(0|[1-9][0-9]{0,19})")||new java.math.BigInteger(context).bitLength()>64)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);if(r.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return context;}
    private static String single(HttpServletRequest r,String name){var values=r.getHeaders(name);if(values==null||!values.hasMoreElements())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);String value=values.nextElement();if(values.hasMoreElements())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return value;}
    private static JsonNode reply(HttpServletResponse r,JsonNode value){r.setHeader("Cache-Control","no-store");r.setHeader("X-Schema-Version","1.0.0");r.setHeader("X-Request-Id",value.required("request_id").asText());return value;}
    @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
    @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,e.code()==ErrorCode.ACCESS_DENIED?403:e.code()==ErrorCode.DEPENDENCY_UNAVAILABLE?503:e.code()==ErrorCode.FILE_TOO_LARGE?413:409,e.code());}
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,IllegalArgumentException.class})void malformed(Exception e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
}
