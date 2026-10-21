package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Defense for historical rows; authorized configuration/notice editor data remains intact. */
@ControllerAdvice(basePackages={"com.ruoyi.web.controller.system","com.ruoyi.web.controller.monitor"})
@ConditionalOnProperty(name="medical.identity.mode",havingValue="local")
public final class FrameworkBindingNativeResponseAdvice implements ResponseBodyAdvice<Object> {
    private static final Set<String> CREDENTIALS=Set.of("password","oldPassword","newPassword","confirmPassword","salt","accessToken","refreshToken","sessionToken","token","secret");
    private static final Set<String> AUDIT_PAYLOADS=Set.of("operParam","jsonResult","errorMsg");
    private final ObjectMapper mapper;
    public FrameworkBindingNativeResponseAdvice(ObjectMapper mapper){this.mapper=mapper;}
    @Override public boolean supports(MethodParameter p,Class<? extends HttpMessageConverter<?>> converter){return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter);}
    @Override public Object beforeBodyWrite(Object body,MethodParameter p,MediaType type,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
        if(body==null)return null;
        JsonNode copy=mapper.valueToTree(body);
        boolean audit=p.getContainingClass().getSimpleName().equals("SysOperlogController");
        redact(copy,audit);return copy;
    }
    private static void redact(JsonNode node,boolean audit){
        if(node.isObject()){
            var object=(ObjectNode)node;object.remove(CREDENTIALS);
            if(audit)for(String name:AUDIT_PAYLOADS)if(object.has(name))object.put(name,"[REDACTED]");
        }
        if(node.isContainerNode())for(JsonNode child:node)redact(child,audit);
    }
}
