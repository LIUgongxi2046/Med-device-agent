package com.ruoyi.medical.runtime;

import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.FrameworkBindingNativeSchema;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** Fixed original-handler query/form constraints, including rejection of client params.dataScope injection. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.identity.mode",havingValue="local")
public class FrameworkBindingNativeParameterConfiguration implements WebMvcConfigurer {
    private final FrameworkBindingNativeSchema schema;private final ApplicationSessionIdentity identities;
    public FrameworkBindingNativeParameterConfiguration(FrameworkBindingNativeSchema schema,ApplicationSessionIdentity identities){this.schema=schema;this.identities=identities;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
        @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler)throws Exception {
            if(!(handler instanceof HandlerMethod method))return true;Class<?> type=ClassUtils.getUserClass(method.getBeanType());
            if(!(type.getPackageName().equals("com.ruoyi.web.controller.system")||type.getPackageName().equals("com.ruoyi.web.controller.monitor")||type.getPackageName().equals("com.ruoyi.generator.controller")))return true;
            String id="framework."+type.getSimpleName()+"."+method.getMethod().getName();if(!schema.supportsParameters(id))return true;
            if(!"PLATFORM_ADMIN".equals(identities.requireCurrent(request,SecurityContextHolder.getContext().getAuthentication()).user().platform_role()))throw new AccessDeniedException("ACCESS_DENIED");
            try{schema.validateParameters(id,request.getParameterMap());}catch(SchemaRejection failure){new IdentityErrorResponseWriter().write(response,422,failure.code());return false;}
            return true;
        }
    }).order(-50);}
}
