package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.springframework.beans.factory.*;
import org.springframework.beans.BeansException;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.*;
import java.io.IOException;

/** Callable initialized instances only, never permission, input approval or runtime success. */
@RestController
public final class FormalEvaluationNodeInputAvailabilityController {
    private final ApplicationSessionIdentity identities;private final EnterpriseAccess access;private final ListableBeanFactory beans;private final Environment environment;private final PermissionSchema json;
    public FormalEvaluationNodeInputAvailabilityController(ApplicationSessionIdentity i,EnterpriseAccess a,ListableBeanFactory b,Environment e,PermissionSchema j){identities=i;access=a;beans=b;environment=e;json=j;}
    @GetMapping("/api/formal-evaluation-node-input-availability")
    public JsonNode read(HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {
        String context=FormalEvaluationContextHttp.headers(q,s,false,false);var subject=identities.requireCurrent(q,auth);
        var data=access.execute(subject,a->{FormalEvaluationContextService.current(a,context);boolean enabled=environment.getProperty("medical.formal-evaluation-node-input.enabled",Boolean.class,false);
            boolean route=enabled&&actual(FormalEvaluationNodeInputController.class);boolean write=route&&actual(FormalEvaluationNodeInputObjectService.class);boolean read=route&&actual(FormalEvaluationNodeInputReadService.class);
            var result=json.object().put("schema_version","1.0.0");result.putObject("features").put("preparation",write).put("recovery",write).put("status",read).put("content",read&&actual(SourceUploadTransport.class));
            if(write)result.putNull("unavailable_reason");else result.put("unavailable_reason",enabled?"DEPENDENCY_UNAVAILABLE":"FEATURE_DISABLED");return result;});
        return FormalEvaluationContextHttp.envelope(data,s,json);
    }
    private <T>boolean actual(Class<T> type){try{return beans.getBeanProvider(type).getIfUnique()!=null;}catch(BeansException unavailable){return false;}}
    @ExceptionHandler(Exception.class)public void failure(Exception e,HttpServletResponse s)throws IOException{FormalEvaluationContextHttp.failure(e,s);}
}
