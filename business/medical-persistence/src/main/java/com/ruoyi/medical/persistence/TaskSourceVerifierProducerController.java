package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** Session/CSRF governance routes only. No worker exception and no automatic approval. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="medical.task-source-verifier-producer",name="enabled",havingValue="true")
public final class TaskSourceVerifierProducerController {
 private final ApplicationSessionIdentity identities;private final TaskNodeProducerService rules;private final PermissionSchema json;
 public TaskSourceVerifierProducerController(ApplicationSessionIdentity identities,@org.springframework.beans.factory.annotation.Qualifier("taskSourceVerifierProducerService") TaskNodeProducerService rules,PermissionSchema json){this.identities=identities;this.rules=rules;this.json=json;}
    @GetMapping("/api/tasks/{task}/source-verifier-producer-original-rules")
    public JsonNode originalRules(@PathVariable String task,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,false,false);return envelope(rules.originalRules(identities.requireCurrent(q,auth),context,task),s);
    }
    @GetMapping("/api/tasks/{task}/source-verifier-producer-preparation")
    public JsonNode preparation(@PathVariable String task,@RequestParam("original_rule_id") String originalRuleId,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=preparationHeaders(q,s,originalRuleId);return envelope(rules.preparation(identities.requireCurrent(q,auth),context,task,originalRuleId),s);
    }
    @PostMapping(value="/api/tasks/{task}/source-verifier-producer-rules",consumes="application/json")
    public JsonNode authorInput(@PathVariable String task,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,true,true);String key=FormalEvaluationContextHttp.key(q);byte[] bytes=body(q);JsonNode result;
        try { result=rules.author(identities.requireCurrent(q,auth),context,task,key,bytes); }
        catch (com.ruoyi.medical.contracts.SchemaRejection | TenantAccessException rejected) { rejectedAttempt(s,rejected,task,"AUTHOR",null,key,bytes);throw rejected; }
        s.setStatus(201);return envelope(result,s);
    }
    @GetMapping("/api/tasks/{task}/source-verifier-producer-rules")
    public JsonNode list(@PathVariable String task,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,false,false);return envelope(rules.list(identities.requireCurrent(q,auth),context,task),s);
    }
    @GetMapping("/api/tasks/{task}/source-verifier-producer-rules/{capability}")
    public JsonNode input(@PathVariable String task,@PathVariable String capability,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,false,false);return envelope(rules.read(identities.requireCurrent(q,auth),context,task,capability),s);
    }
    @PostMapping(value="/api/tasks/{task}/source-verifier-producer-rules/{capability}/decisions",consumes="application/json")
    public JsonNode decide(@PathVariable String task,@PathVariable String capability,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,true,true);String key=FormalEvaluationContextHttp.key(q);byte[] bytes=body(q);JsonNode result;
        try { result=rules.decide(identities.requireCurrent(q,auth),context,task,capability,key,bytes); }
        catch (com.ruoyi.medical.contracts.SchemaRejection | TenantAccessException rejected) { rejectedAttempt(s,rejected,task,"DECIDE",capability,key,bytes);throw rejected; }
        return envelope(result,s);
    }
    @PostMapping(value="/api/tasks/{task}/source-verifier-producer-rules/recovery",consumes="application/json")
    public JsonNode recoverInput(@PathVariable String task,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{
        String context=headers(q,s,true,false);return envelope(rules.recover(identities.requireCurrent(q,auth),context,task,body(q)),s);
    }
    /** Only direct typed service business rejection: transaction callbacks have unwound/rolled back.
     * Never inspect nested commit/transport errors, and never wrap the post-commit response envelope.
     * This states nothing about a previous attempt with the same key. */
    private static void rejectedAttempt(HttpServletResponse response,RuntimeException rejected,String task,String operation,String rule,String key,byte[] bytes) throws Exception {
        var code=rejected instanceof TenantAccessException denied?denied.code():((com.ruoyi.medical.contracts.SchemaRejection)rejected).code();
        if(!java.util.Set.of(com.ruoyi.medical.contracts.ErrorCode.SCHEMA_INVALID,com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED,com.ruoyi.medical.contracts.ErrorCode.REVISION_CONFLICT,com.ruoyi.medical.contracts.ErrorCode.SCOPE_STALE).contains(code))return;
        response.setHeader("X-Task-Section-Writer-Producer-Outcome","REJECTED_THIS_ATTEMPT");
        response.setHeader("X-Task-Section-Writer-Producer-Task",task);response.setHeader("X-Task-Section-Writer-Producer-Operation",operation);
        if(rule!=null)response.setHeader("X-Task-Section-Writer-Producer-Rule",rule);
        response.setHeader("X-Task-Section-Writer-Producer-Key",key);
        response.setHeader("X-Task-Section-Writer-Producer-Request-Sha256",com.ruoyi.medical.contracts.WireValues.sha256(bytes));
    }
    private static String preparationHeaders(HttpServletRequest request,HttpServletResponse response,String originalRuleId)throws Exception {
        com.ruoyi.medical.contracts.WireValues.uuid(originalRuleId);
        String expected="original_rule_id="+originalRuleId;
        var values=request.getParameterValues("original_rule_id");
        if(!expected.equals(request.getQueryString())||request.getParameterMap().size()!=1||values==null||values.length!=1||!originalRuleId.equals(values[0]))
            throw new com.ruoyi.medical.contracts.SchemaRejection(com.ruoyi.medical.contracts.ErrorCode.SCHEMA_INVALID);
        // Only this fully validated query is consumed here; all shared header/body rules remain intact.
        return headers(new jakarta.servlet.http.HttpServletRequestWrapper(request){@Override public String getQueryString(){return null;}},response,false,false);
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
