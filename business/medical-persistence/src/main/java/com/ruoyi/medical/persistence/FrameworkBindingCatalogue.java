package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;

/** Closed server-owned action vocabulary. It describes endpoints; it never invokes them. */
@Component
public final class FrameworkBindingCatalogue {
    public record Action(String module_id,String operation_id,String method,String path,String request_schema_ref,
                         String response_schema_ref,String side_effect,String permission_code,String environment) { }
    public static final String REF="urn:medical:framework-binding:1.0.0#/$defs/";
    private final Map<String,Action> actions;
    public FrameworkBindingCatalogue() {
        var rows=new LinkedHashMap<String,Action>();
        read(rows,"USER","SysUserController","/api/system/user/list","system:user:list");
        read(rows,"ROLE","SysRoleController","/api/system/role/list","system:role:list");
        read(rows,"MENU","SysMenuController","/api/system/menu/list","system:menu:list");
        read(rows,"DEPARTMENT","SysDeptController","/api/system/dept/list","system:dept:list");
        read(rows,"POST","SysPostController","/api/system/post/list","system:post:list");
        read(rows,"DICTIONARY","SysDictTypeController","/api/system/dict/type/list","system:dict:list");
        read(rows,"PARAMETER","SysConfigController","/api/system/config/list","system:config:list");
        read(rows,"NOTICE","SysNoticeController","/api/system/notice/list","system:notice:list");
        read(rows,"OPERATION_LOG","SysOperlogController","/api/monitor/operlog/list","monitor:operlog:list");
        read(rows,"LOGIN_LOG","SysLogininforController","/api/monitor/logininfor/list","monitor:logininfor:list");
        read(rows,"CODE_GENERATOR","GenController","genList","/api/tool/gen/list","tool:gen:list");
        read(rows,"ONLINE_USER","FrameworkSessionMonitorController","list","/api/monitor/online/list","monitor:online:list");
        read(rows,"CACHE_MONITOR","FrameworkCacheMonitorController","info","/api/monitor/cache","monitor:cache:list");
        read(rows,"CACHE_MONITOR","FrameworkCacheMonitorController","names","/api/monitor/cache/getNames","monitor:cache:list");
        read(rows,"CACHE_MONITOR","FrameworkCacheMonitorController","keys","/api/monitor/cache/getKeys/{cacheName}","monitor:cache:list");
        read(rows,"CACHE_MONITOR","FrameworkCacheMonitorController","value","/api/monitor/cache/getValue/{cacheName}/{cacheKey}","monitor:cache:list");
        read(rows,"SERVICE_MONITOR","FrameworkMonitorController","server","/api/monitor/server","monitor:server:list");
        read(rows,"CONNECTION_POOL","FrameworkMonitorController","connectionPool","/api/monitor/druid","monitor:druid:list");
        read(rows,"SCHEDULED_JOB","FrameworkJobMonitorController","jobs","/api/monitor/job/list","monitor:job:list");
        read(rows,"SCHEDULED_JOB","FrameworkJobMonitorController","job","/api/monitor/job/{jobId}","monitor:job:query");
        read(rows,"SCHEDULED_JOB","FrameworkJobMonitorController","logs","/api/monitor/jobLog/list","monitor:job:list");
        read(rows,"API_DOCUMENTATION","FrameworkToolController","documentation","/api/tool/swagger","tool:swagger:list");
        read(rows,"FORM_BUILDER","FrameworkToolController","formBuilder","/api/tool/build/capabilities","tool:build:list");
        rows.put("framework.job.run",new Action("SCHEDULED_JOB","framework.job.run","PUT","/api/monitor/job/run","urn:medical:framework-job-command:1.0.0#/$defs/Run",REF+"JobCommandResponse","JOB_DISPATCH","monitor:job:changeStatus","ANY"));
        rows.put("framework.job.changeStatus",new Action("SCHEDULED_JOB","framework.job.changeStatus","PUT","/api/monitor/job/changeStatus","urn:medical:framework-job-command:1.0.0#/$defs/Status",REF+"JobCommandResponse","WRITE","monitor:job:changeStatus","ANY"));
        for(String verb:List.of("create","edit","delete")) {
            String method=verb.equals("create")?"POST":verb.equals("edit")?"PUT":"DELETE";
            String path="/api/monitor/job"+(verb.equals("delete")?"/{jobId}":"");String definition=Character.toUpperCase(verb.charAt(0))+verb.substring(1);
            rows.put("framework.job."+verb,new Action("SCHEDULED_JOB","framework.job."+verb,method,path,"urn:medical:framework-job-command:1.0.0#/$defs/"+definition,REF+"JobCommandResponse","WRITE","monitor:job:"+(verb.equals("create")?"add":verb.equals("delete")?"remove":"edit"),"ANY"));
        }
        read(rows,"SCHEDULED_JOB","FrameworkJobLifecycleController","registrations","/api/monitor/job/registrations","monitor:job:add");
        for(String domain:List.of("job","jobLog"))rows.put("framework.FrameworkJobExportController."+(domain.equals("job")?"jobs":"logs"),new Action("SCHEDULED_JOB","framework.FrameworkJobExportController."+(domain.equals("job")?"jobs":"logs"),"POST","/api/monitor/"+domain+"/export",FrameworkBindingNativeSchema.REF+"Query_FrameworkJobExport",FrameworkBindingNativeSchema.REF+"BinaryExportResponse","EXPORT","monitor:job:export","ANY"));
        for(String operation:List.of("list","draft","read","approve","consume")) {
            String method=Set.of("draft","approve").contains(operation)?"POST":"GET";
            String path="/api/tool/build/schemas"+(Set.of("read","approve","consume").contains(operation)?"/{id}":"")+(operation.equals("approve")?"/approvals":operation.equals("consume")?"/published":"");
            String request=operation.equals("draft")?"urn:medical:framework-form:1.0.0#/$defs/Draft":operation.equals("approve")?"urn:medical:framework-form:1.0.0#/$defs/Approve":null;
            rows.put("framework.form."+operation,new Action("FORM_BUILDER","framework.form."+operation,method,path,request,"urn:medical:framework-form:1.0.0#/$defs/Response",method.equals("GET")?"NONE":"WRITE","tool:build:list",Set.of("draft","approve").contains(operation)?"DESIGN_OR_DEVELOPMENT":"ANY"));
        }
        // Only this compiled, reviewed classpath catalogue is loaded; never user JSON or remote descriptors.
        try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/framework-binding-native-actions.json"))) {
            var document=new com.fasterxml.jackson.databind.ObjectMapper().readTree(stream);
            for(var n:document) {
                var action=new Action(n.required("module_id").asText(),n.required("operation_id").asText(),n.required("method").asText(),n.required("path").asText(),n.required("request_schema_ref").isNull()?null:n.required("request_schema_ref").asText(),n.required("response_schema_ref").asText(),n.required("side_effect").asText(),n.required("permission_code").asText(),n.required("environment").asText());
                rows.put(action.operation_id(),action);
            }
        } catch(java.io.IOException failure){throw new IllegalStateException("FRAMEWORK_BINDING_CATALOGUE_INVALID",failure);}
        actions=Collections.unmodifiableMap(rows);
    }
    private static void read(Map<String,Action> rows,String module,String controller,String path,String permission) {
        read(rows,module,controller,"list",path,permission);
    }
    private static void read(Map<String,Action> rows,String module,String controller,String handler,String path,String permission) {
        String id="framework."+controller+"."+handler;
        rows.put(id,new Action(module,id,"GET",path,null,REF+"NativeReadResponse","NONE",permission,
                module.equals("CODE_GENERATOR")?"DEVELOPMENT":module.equals("FORM_BUILDER")?"DESIGN_OR_DEVELOPMENT":"ANY"));
    }
    public Collection<Action> actions(){return actions.values();}
    public Action require(String id){var a=actions.get(id);if(a==null)throw new IllegalArgumentException("FRAMEWORK_OPERATION_UNDECLARED");return a;}
    boolean matches(Action action,String method,String path) {
        if(!action.method().equals(method)||path==null||path.length()>512||path.contains("%")||path.contains("..")||path.contains("?")||path.contains("#"))return false;
        var matcher=java.util.regex.Pattern.compile("\\{([A-Za-z]+)\\}").matcher(action.path());var expression=new StringBuilder();int end=0;
        while(matcher.find()) {
            expression.append(java.util.regex.Pattern.quote(action.path().substring(end,matcher.start())));String variable=matcher.group(1);
            String part=variable.equals("id")||variable.equals("commandId")?"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}":
                variable.endsWith("Ids")||variable.equals("dictCodes")?"[0-9]{1,19}(?:,[0-9]{1,19}){0,999}":variable.endsWith("Id")||variable.equals("dictCode")?"[0-9]{1,19}":"[A-Za-z0-9_:@.-]{1,128}";
            expression.append(part);end=matcher.end();
        }
        expression.append(java.util.regex.Pattern.quote(action.path().substring(end)));
        return path.matches(expression.toString());
    }
    JsonNode document(PermissionSchema json){return json.tree(actions.values());}
}
