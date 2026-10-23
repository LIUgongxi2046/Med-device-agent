package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

/** Ordinary authenticated session/CSRF routes. No worker, permission or approval exemption. */
@RestController
public final class TaskFieldTargetController {
    private final ApplicationSessionIdentity identities;private final TaskFieldMappingService mappings;
    private final TaskFieldTargetService targets;private final TaskFieldTargetPreparationService preparation;
    private final TaskFieldTargetInputCapabilityService inputs;private final PermissionSchema json;
    public TaskFieldTargetController(ApplicationSessionIdentity identities,TaskFieldMappingService mappings,
            TaskFieldTargetService targets,TaskFieldTargetPreparationService preparation,
            @Nullable TaskFieldTargetInputCapabilityService inputs,PermissionSchema json){
        this.identities=identities;this.mappings=mappings;this.targets=targets;this.preparation=preparation;this.inputs=inputs;this.json=json;
    }
    @GetMapping("/api/tasks/{task}/field-target-preparation")
    public JsonNode preparation(@PathVariable String task,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,false,false);return envelope(preparation.read(identities.requireCurrent(q,auth),context,task),s);
    }
    @PostMapping(value="/api/tasks/{task}/field-mappings",consumes="application/json")
    public JsonNode authorMapping(@PathVariable String task,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,true,true);var result=mappings.author(identities.requireCurrent(q,auth),context,task,FormalEvaluationContextHttp.key(q),body(q));s.setStatus(201);return envelope(result,s);
    }
    @GetMapping("/api/tasks/{task}/field-mappings/{assignment}")
    public JsonNode mapping(@PathVariable String task,@PathVariable String assignment,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,false,false);return envelope(mappings.read(identities.requireCurrent(q,auth),context,task,assignment),s);
    }
    @PostMapping(value="/api/tasks/{task}/field-mappings/recovery",consumes="application/json")
    public JsonNode recoverMapping(@PathVariable String task,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,true,false);return envelope(mappings.recover(identities.requireCurrent(q,auth),context,task,body(q)),s);
    }
    @PostMapping(value="/api/tasks/{task}/field-targets",consumes="application/json")
    public JsonNode produce(@PathVariable String task,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,true,true);return envelope(targets.produce(identities.requireCurrent(q,auth),context,task,FormalEvaluationContextHttp.key(q),body(q)),s);
    }
    @PostMapping(value="/api/tasks/{task}/field-targets/recovery",consumes="application/json")
    public JsonNode recoverTarget(@PathVariable String task,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,true,false);return envelope(targets.recover(identities.requireCurrent(q,auth),context,task,body(q)),s);
    }
    @GetMapping("/api/tasks/{task}/field-target-input-capabilities")
    public JsonNode inputContext(@PathVariable String task,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,false,false);return envelope(inputs().context(identities.requireCurrent(q,auth),context,task),s);
    }
    @PostMapping(value="/api/tasks/{task}/field-target-input-capabilities",consumes="application/json")
    public JsonNode authorInput(@PathVariable String task,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,true,true);var result=inputs().author(identities.requireCurrent(q,auth),context,task,FormalEvaluationContextHttp.key(q),body(q));s.setStatus(201);return envelope(result,s);
    }
    @GetMapping("/api/tasks/{task}/field-target-input-capabilities/{capability}")
    public JsonNode input(@PathVariable String task,@PathVariable String capability,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,false,false);return envelope(inputs().read(identities.requireCurrent(q,auth),context,task,capability),s);
    }
    @PostMapping(value="/api/tasks/{task}/field-target-input-capabilities/{capability}/decisions",consumes="application/json")
    public JsonNode decide(@PathVariable String task,@PathVariable String capability,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,true,true);return envelope(inputs().decide(identities.requireCurrent(q,auth),context,task,capability,FormalEvaluationContextHttp.key(q),body(q)),s);
    }
    @PostMapping(value="/api/tasks/{task}/field-target-input-capabilities/recovery",consumes="application/json")
    public JsonNode recoverInput(@PathVariable String task,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,true,false);return envelope(inputs().recover(identities.requireCurrent(q,auth),context,task,body(q)),s);
    }
    private TaskFieldTargetInputCapabilityService inputs(){
        if(inputs==null)throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE);
        return inputs;
    }
    private static String headers(HttpServletRequest q,HttpServletResponse s,boolean post,boolean write)throws Exception{return FormalEvaluationContextHttp.headers(q,s,post,write);}
    private static byte[] body(HttpServletRequest q)throws java.io.IOException{return FormalEvaluationContextHttp.body(q,8_388_608);}
    private JsonNode envelope(JsonNode value,HttpServletResponse s){
        String request=java.util.UUID.randomUUID().toString();
        String revision=value==null?"1":value.has("head_revision")?value.required("head_revision").asText():value.has("revision")?value.required("revision").asText():"1";
        s.setHeader("X-Request-Id",request);s.setHeader("X-Resource-Revision",revision);
        return json.tree(new com.ruoyi.medical.contracts.InterfaceProtocol.Envelope("1.0.0",request,revision,value==null?com.fasterxml.jackson.databind.node.NullNode.instance:value));
    }
    @ExceptionHandler(Exception.class)public void failure(Exception e,HttpServletResponse s)throws java.io.IOException{FormalEvaluationContextHttp.failure(e,s);}
}
