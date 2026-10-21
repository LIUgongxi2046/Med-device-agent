package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.FrameworkBindingAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.ruoyi.medical.contracts.*;

/** Resolve the actual current session, never a configured or caller-supplied governing tenant. */
@Component
public final class FrameworkBindingRuntimeAuthority implements FrameworkBindingAuthority {
    private final FrameworkBindingService service;private final ApplicationSessionIdentity identities;
    public FrameworkBindingRuntimeAuthority(FrameworkBindingService service,ApplicationSessionIdentity identities){this.service=service;this.identities=identities;}
    @Override public Binding require(String operationId,String method,String path){
        if(!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var request=((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest();
        String context=request.getHeader("X-Context-Revision");var headers=request.getHeaders("X-Context-Revision");
        if(headers==null||!headers.hasMoreElements()||!java.util.Objects.equals(headers.nextElement(),context)||headers.hasMoreElements())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        PermissionSchema.revision(context);
        if(!method.equals(request.getMethod())||!path.equals(request.getRequestURI().substring(request.getContextPath().length())))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        return service.require(identities.requireCurrent(request,SecurityContextHolder.getContext().getAuthentication()),context,operationId,method,path);
    }
}
