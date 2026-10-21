package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.generator.service.IGenTableColumnService;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.FrameworkBindingNativeSchema;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Validate a bounded exact wire body before Jackson maps it to the unchanged original DTO/controller. */
@ControllerAdvice(basePackages={"com.ruoyi.web.controller.system","com.ruoyi.generator.controller"})
@ConditionalOnProperty(name="medical.identity.mode",havingValue="local")
public final class FrameworkBindingNativeRequestAdvice extends RequestBodyAdviceAdapter {
    private final FrameworkBindingNativeSchema schema;private final ApplicationSessionIdentity identities;
    private final PermissionService permissions;private final ObjectProvider<IGenTableColumnService> generatorColumns;
    public FrameworkBindingNativeRequestAdvice(FrameworkBindingNativeSchema schema,ApplicationSessionIdentity identities,PermissionService permissions,ObjectProvider<IGenTableColumnService> generatorColumns){this.schema=schema;this.identities=identities;this.permissions=permissions;this.generatorColumns=generatorColumns;}
    private static String id(MethodParameter p){return "framework."+p.getContainingClass().getSimpleName()+"."+Objects.requireNonNull(p.getMethod()).getName();}
    @Override public boolean supports(MethodParameter p,Type target,Class<? extends HttpMessageConverter<?>> converter){return p.hasParameterAnnotation(RequestBody.class)&&schema.supports(id(p));}
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input,MethodParameter parameter,Type target,Class<? extends HttpMessageConverter<?>> converter)throws IOException {
        var attrs=RequestContextHolder.getRequestAttributes();if(!(attrs instanceof ServletRequestAttributes))throw new AccessDeniedException("ACCESS_DENIED");
        var request=((ServletRequestAttributes)attrs).getRequest();var subject=identities.requireCurrent(request,SecurityContextHolder.getContext().getAuthentication());
        if(!"PLATFORM_ADMIN".equals(subject.user().platform_role()))throw new AccessDeniedException("ACCESS_DENIED");
        // Exact existing @ss.hasPermi expressions only; never evaluate client-provided SpEL or weaken the original method guard.
        var annotation=Objects.requireNonNull(parameter.getMethod()).getAnnotation(PreAuthorize.class);
        if(annotation==null||!annotation.value().matches("@ss\\.hasPermi\\('[a-zA-Z0-9:]+?'\\)"))throw new AccessDeniedException("ACCESS_DENIED");
        String expression=annotation.value(),permission=expression.substring(expression.indexOf('\'')+1,expression.lastIndexOf('\''));
        if(!permissions.hasPermi(permission))throw new AccessDeniedException("ACCESS_DENIED");
        if(input.getHeaders().getContentLength()>FrameworkBindingNativeSchema.MAX_BYTES)throw new SchemaRejection(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes=input.getBody().readNBytes(FrameworkBindingNativeSchema.MAX_BYTES+1);var value=schema.validateRequest(id(parameter),bytes);
        if(id(parameter).equals("framework.GenController.editSave"))requireColumnOwnership(value);
        return new HttpInputMessage(){public InputStream getBody(){return new ByteArrayInputStream(bytes);}public HttpHeaders getHeaders(){return input.getHeaders();}};
    }
    private void requireColumnOwnership(JsonNode body) {
        if(!permissions.hasRole("developer"))throw new AccessDeniedException("ACCESS_DENIED");
        var columns=generatorColumns.getIfAvailable();if(columns==null)throw new AccessDeniedException("FRAMEWORK_GENERATOR_UNAVAILABLE");
        long table=FrameworkBindingNativeSchema.positiveLong(body.required("tableId").asText());var permitted=new HashSet<Long>();
        for(var column:columns.selectGenTableColumnListByTableId(table))permitted.add(column.getColumnId());
        var seen=new HashSet<Long>();for(var column:body.required("columns")) {
            long id=FrameworkBindingNativeSchema.positiveLong(column.required("columnId").asText());
            if(FrameworkBindingNativeSchema.positiveLong(column.required("tableId").asText())!=table||!seen.add(id)||!permitted.contains(id))throw new AccessDeniedException("FRAMEWORK_GENERATOR_COLUMN_OWNER_MISMATCH");
        }
    }
    @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,failure.code()==ErrorCode.FILE_TOO_LARGE?413:422,failure.code());}
    @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class)void malformed(Exception failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID);}
}
