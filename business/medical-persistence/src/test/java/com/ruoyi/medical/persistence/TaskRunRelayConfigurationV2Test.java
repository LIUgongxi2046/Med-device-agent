package com.ruoyi.medical.persistence;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class TaskRunRelayConfigurationV2Test {
    @Test void everyProductionSurfaceIsExplicitDefaultOff(){
        for(Class<?> type:List.of(TaskRunWorkerConfigurationV2.class,TaskRunTemporalConfigurationV2.class,TaskRunRelayClientConfigurationV2.class,TaskRunRelayConfigurationV2.class,TaskRunRelayControllerV2.class,TaskRunRelaySecurityConfigurationV2.class)){
            var gate=type.getAnnotation(ConditionalOnProperty.class);assertNotNull(gate);assertFalse(gate.matchIfMissing());assertEquals("true",gate.havingValue());
            assertTrue(List.of(gate.name()).contains("medical.professional-runtime.waiting-v2-enabled"));assertTrue(List.of(gate.name()).contains("medical.professional-runtime.task-enabled"));
        }
    }
    @Test void relaySecurityMatchesExactPostRouteOnly(){
        var matcher=TaskRunRelaySecurityConfigurationV2.matcher();
        var post=new MockHttpServletRequest("POST",TaskRunRelayControllerV2.ROUTE);post.setServletPath(TaskRunRelayControllerV2.ROUTE);assertTrue(matcher.matches(post));
        for(String method:List.of("GET","PUT","DELETE")){var request=new MockHttpServletRequest(method,TaskRunRelayControllerV2.ROUTE);request.setServletPath(TaskRunRelayControllerV2.ROUTE);assertFalse(matcher.matches(request));}
        var nested=new MockHttpServletRequest("POST",TaskRunRelayControllerV2.ROUTE+"/other");nested.setServletPath(TaskRunRelayControllerV2.ROUTE+"/other");assertFalse(matcher.matches(nested));
    }
    @Test void browserHeadersNeverBecomeServiceAuthority(){
        for(String header:List.of("Cookie","Authorization","Origin","Sec-Fetch-Site")){
            var request=new MockHttpServletRequest();request.addHeader(header,"present");assertFalse(TaskRunRelaySecurityConfigurationV2.authorize(null,request));
        }
    }
}
