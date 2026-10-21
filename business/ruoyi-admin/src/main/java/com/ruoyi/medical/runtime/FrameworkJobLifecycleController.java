package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.FrameworkBindingAuthority;
import jakarta.servlet.http.*;
import java.util.*;
import javax.sql.DataSource;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Lifecycle for predeclared compiled handlers; no class names or invocation expressions from clients. */
@RestController
@ConditionalOnProperty(name="medical.framework.job-dispatch.enabled",havingValue="true")
public class FrameworkJobLifecycleController {
    private final ApplicationSessionIdentity identities; private final PermissionService permissions;
    private final FrameworkBindingAuthority bindings; private final Scheduler scheduler;
    private final Map<Long,FrameworkJobRegistration> registrations; private final JdbcTemplate jdbc;
    private final TransactionTemplate tx; private final JsonMapper json=JsonMapper.builder().build(); private final String schemaHash;
    public FrameworkJobLifecycleController(ApplicationSessionIdentity identities,PermissionService permissions,FrameworkBindingAuthority bindings,
            @Qualifier("frameworkApprovedScheduler") Scheduler scheduler,List<FrameworkJobRegistration> entries,
            @Qualifier("masterDataSource") DataSource source)throws Exception {
        this.identities=identities;this.permissions=permissions;this.bindings=bindings;this.scheduler=scheduler;
        var map=new HashMap<Long,FrameworkJobRegistration>();for(var entry:entries)if(map.putIfAbsent(entry.jobId(),entry)!=null)throw invalid();
        registrations=Map.copyOf(map);jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/framework-job-command.schema.json"))){schemaHash=hash(input.readAllBytes());}
    }
    @GetMapping("/api/monitor/job/registrations") @PreAuthorize("@ss.hasPermi('monitor:job:add')")
    public AjaxResult registrations(HttpServletRequest request,HttpServletResponse response,Authentication auth){
        current(request,response,auth,"monitor:job:add");
        var rows=registrations.values().stream().sorted(Comparator.comparingLong(FrameworkJobRegistration::jobId))
                .map(r->Map.of("jobId",Long.toString(r.jobId()),"jobGroup",r.jobGroup(),"handlerKey",r.handlerKey(),
                        "concurrent",r.jobClass().isAnnotationPresent(DisallowConcurrentExecution.class)?"1":"0")).toList();
        return AjaxResult.success(rows);
    }
    @PostMapping("/api/monitor/job") @PreAuthorize("@ss.hasPermi('monitor:job:add')")
    public AjaxResult create(@RequestBody JsonNode body,@RequestHeader("Idempotency-Key")String key,
            HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception{
        return mutate("CREATE",body,null,key,request,response,auth);
    }
    @PutMapping("/api/monitor/job") @PreAuthorize("@ss.hasPermi('monitor:job:edit')")
    public AjaxResult edit(@RequestBody JsonNode body,@RequestHeader("Idempotency-Key")String key,
            HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception{
        return mutate("EDIT",body,null,key,request,response,auth);
    }
    @DeleteMapping("/api/monitor/job/{jobId:[0-9]+}") @PreAuthorize("@ss.hasPermi('monitor:job:remove')")
    public AjaxResult retire(@PathVariable String jobId,@RequestHeader("X-Framework-Job-Revision")String revision,
            @RequestHeader("Idempotency-Key")String key,HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception{
        if(request.getContentLengthLong()>0)throw invalid();
        var body=json.createObjectNode().put("jobId",jobId).put("expectedRevision",revision);
        return mutate("RETIRE",body,revision,key,request,response,auth);
    }
    private AjaxResult mutate(String action,JsonNode body,String ignored,String key,HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception{
        String permission="monitor:job:"+Map.of("CREATE","add","EDIT","edit","RETIRE","remove").get(action);
        var subject=current(request,response,auth,permission);
        if(!key.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw invalid();
        var keys=new HashSet<>(Set.of("jobId"));if(!action.equals("CREATE"))keys.add("expectedRevision");
        if(!action.equals("RETIRE"))keys.addAll(Set.of("jobName","cronExpression","misfirePolicy","concurrent"));
        if(!body.isObject()||body.size()!=keys.size())throw invalid();body.fieldNames().forEachRemaining(k->{if(!keys.contains(k))throw invalid();});
        long id=positive(body.path("jobId"));long expected=action.equals("CREATE")?0:positive(body.path("expectedRevision"));
        var registration=registrations.get(id);if(registration==null)throw denied();
        if(!action.equals("RETIRE")){
            if(!body.path("jobName").isTextual()||body.path("jobName").textValue().isBlank()||body.path("jobName").textValue().length()>64
                    ||!body.path("cronExpression").isTextual()||body.path("cronExpression").textValue().length()>255
                    ||!CronExpression.isValidExpression(body.path("cronExpression").textValue())
                    ||!body.path("misfirePolicy").isTextual()||!Set.of("0","1","2","3").contains(body.path("misfirePolicy").textValue())
                    ||!body.path("concurrent").isTextual()||!(registration.jobClass().isAnnotationPresent(DisallowConcurrentExecution.class)?"1":"0").equals(body.path("concurrent").textValue()))throw invalid();
        }
        String operation="framework.job."+Map.of("CREATE","create","EDIT","edit","RETIRE","delete").get(action);
        var binding=bindings.require(operation,request.getMethod(),request.getRequestURI());
        String definition=new org.erdtman.jcs.JsonCanonicalizer(json.writeValueAsBytes(body)).getEncodedString();
        String digest=hash((action+"\n"+definition).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String original=json.writeValueAsString(Map.of("command_id",key,"state","RESERVED","execution_complete",false));
        boolean fresh=Boolean.TRUE.equals(tx.execute(status->{
            jdbc.queryForObject("SELECT coordination_key FROM platform_local_bootstrap WHERE coordination_key='LOCAL' FOR UPDATE",String.class);
            identities.lockCurrent(subject);if(!permissions.hasPermi(permission))throw denied();
            var previous=jdbc.queryForList("SELECT actor_id,request_sha256,binding_manifest_sha256 FROM platform_framework_job_command WHERE command_id=?",key);
            if(!previous.isEmpty()){
                var row=previous.get(0);if(!subject.user().user_id().equals(row.get("actor_id"))||!digest.equals(row.get("request_sha256"))||!binding.manifestSha256().equals(row.get("binding_manifest_sha256")))throw conflict();return false;
            }
            var existing=jdbc.queryForList("SELECT status,invoke_target,job_group FROM sys_job WHERE job_id=? FOR UPDATE",id);
            if(action.equals("CREATE")){if(!existing.isEmpty())throw conflict();
                jdbc.update("INSERT INTO sys_job(job_id,job_name,job_group,invoke_target,cron_expression,misfire_policy,concurrent,status,create_by,create_time) VALUES(?,?,?,?,?,?,?,'1',?,UTC_TIMESTAMP(6))",
                        id,body.path("jobName").textValue(),registration.jobGroup(),"registered:"+registration.handlerKey(),body.path("cronExpression").textValue(),body.path("misfirePolicy").textValue(),body.path("concurrent").textValue(),subject.user().username());
            }else{
                if(existing.size()!=1||!"1".equals(existing.get(0).get("status"))||!("registered:"+registration.handlerKey()).equals(existing.get(0).get("invoke_target"))||!registration.jobGroup().equals(existing.get(0).get("job_group")))throw conflict();
                var versions=jdbc.queryForList("SELECT revision,state FROM platform_framework_job_revision WHERE job_id=? ORDER BY revision DESC LIMIT 1",id);
                if(versions.size()!=1||((Number)versions.get(0).get("revision")).longValue()!=expected||!"ACTIVE".equals(versions.get(0).get("state")))throw conflict();
            }
            Long pending=jdbc.queryForObject("SELECT COUNT(*) FROM platform_framework_job_command WHERE job_id=? AND state IN ('RESERVED','UNKNOWN')",Long.class,id);
            if(pending==null||pending!=0)throw conflict();
            jdbc.update("INSERT INTO platform_framework_job_command(command_id,actor_id,job_id,handler_key,job_group,action,request_sha256,binding_manifest_sha256,state,original_response,schema_version,schema_manifest_sha256,created_by,created_at,updated_at,classification) VALUES(?,?,?,?,?,?,?,?,'RESERVED',?,'1.0.0',?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'RESTRICTED')",
                    key,subject.user().user_id(),id,registration.handlerKey(),registration.jobGroup(),action,digest,binding.manifestSha256(),original,schemaHash,subject.user().user_id());
            if(action.equals("EDIT"))jdbc.update("UPDATE sys_job SET job_name=?,cron_expression=?,misfire_policy=?,concurrent=?,update_by=?,update_time=UTC_TIMESTAMP(6) WHERE job_id=?",
                    body.path("jobName").textValue(),body.path("cronExpression").textValue(),body.path("misfirePolicy").textValue(),body.path("concurrent").textValue(),subject.user().username(),id);
            String definitionHash;try{definitionHash=hash(definition.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception failure){throw new IllegalStateException(failure);}
            jdbc.update("INSERT INTO platform_framework_job_revision(job_id,revision,command_id,state,definition,definition_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),'RESTRICTED')",
                    id,Math.addExact(expected,1),key,action.equals("RETIRE")?"RETIRED":"ACTIVE",definition,definitionHash,schemaHash,subject.user().user_id());
            return true;
        }));
        if(fresh){try{
            current(request,response,auth,permission);if(!binding.equals(bindings.require(operation,request.getMethod(),request.getRequestURI())))throw denied();
            if(action.equals("CREATE"))scheduler.addJob(JobBuilder.newJob(registration.jobClass()).withIdentity(registration.jobKey()).storeDurably().build(),false);
            else{
                var detail=scheduler.getJobDetail(registration.jobKey());if(detail==null||detail.getJobClass()!=registration.jobClass()||!detail.getJobDataMap().isEmpty())throw denied();
                for (var trigger : scheduler.getTriggersOfJob(registration.jobKey()))
                    if (scheduler.getTriggerState(trigger.getKey()) != Trigger.TriggerState.PAUSED) throw conflict();
                if(action.equals("RETIRE")){if(!scheduler.deleteJob(registration.jobKey()))throw conflict();}
                else { // Editing is allowed only while paused; remove old schedules, resume installs the new fixed cron.
                    for(var trigger:scheduler.getTriggersOfJob(registration.jobKey()))if(!scheduler.unscheduleJob(trigger.getKey()))throw conflict();
                }
            }
            outcome(key,"DISPATCHED","SCHEDULER_ACCEPTED");
        }catch(Exception failure){outcome(key,"UNKNOWN","SCHEDULER_OUTCOME_UNKNOWN");}}
        var rows=jdbc.queryForList("SELECT command_id,state,outcome_code,created_at,updated_at FROM platform_framework_job_command WHERE command_id=? AND actor_id=?",key,subject.user().user_id());
        if(rows.size()!=1)throw denied();rows.get(0).put("execution_complete",false);response.setStatus(202);return AjaxResult.success(rows.get(0));
    }
    private void outcome(String key,String state,String code){if(jdbc.update("UPDATE platform_framework_job_command SET state=?,outcome_code=?,updated_at=UTC_TIMESTAMP(6) WHERE command_id=? AND state='RESERVED'",state,code,key)!=1)throw new IllegalStateException("FRAMEWORK_JOB_OUTCOME_NOT_RECORDED");}
    private ApplicationSessionIdentity.Verified current(HttpServletRequest request,HttpServletResponse response,Authentication auth,String permission){
        var subject=identities.requireCurrent(request,auth);if(!"PLATFORM_ADMIN".equals(subject.user().platform_role())||!permissions.hasPermi(permission))throw denied();
        if(!request.getParameterMap().isEmpty())throw invalid();response.setHeader("Cache-Control","no-store");return subject;
    }
    private static long positive(JsonNode value){try{if(!value.isTextual()||!value.textValue().matches("[1-9][0-9]{0,18}"))throw invalid();return Long.parseLong(value.textValue());}catch(NumberFormatException e){throw invalid();}}
    private static String hash(byte[] bytes)throws java.security.NoSuchAlgorithmException{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static AccessDeniedException denied(){return new AccessDeniedException("ACCESS_DENIED");}
    private static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_REGISTERED_JOB_DEFINITION");}
    private static ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"REGISTERED_JOB_REVISION_OR_STATE_CONFLICT");}
}
