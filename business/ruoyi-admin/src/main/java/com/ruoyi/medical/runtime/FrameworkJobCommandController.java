package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.FrameworkBindingAuthority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.quartz.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Durable, idempotent submission to already registered typed jobs. Never evaluates invokeTarget. */
@RestController
@ConditionalOnProperty(name = "medical.framework.job-dispatch.enabled", havingValue = "true")
public class FrameworkJobCommandController {
    private final ApplicationSessionIdentity identities;
    private final FrameworkBindingAuthority bindings;
    private final com.ruoyi.framework.web.service.PermissionService permissions;
    private final Map<Long, FrameworkJobRegistration> registered;
    private final Scheduler scheduler;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final JsonMapper json = JsonMapper.builder().build();
    private final String schemaHash;

    public FrameworkJobCommandController(ApplicationSessionIdentity identities, FrameworkBindingAuthority bindings,
            com.ruoyi.framework.web.service.PermissionService permissions, List<FrameworkJobRegistration> registrations, @Qualifier("frameworkApprovedScheduler") Scheduler scheduler,
            @Qualifier("masterDataSource") DataSource source) throws Exception {
        this.identities = identities; this.bindings = bindings; this.permissions = permissions; this.scheduler = scheduler;
        var entries = new HashMap<Long, FrameworkJobRegistration>();
        for (var registration : registrations) if (entries.putIfAbsent(registration.jobId(), registration) != null)
            throw new IllegalStateException("DUPLICATE_FRAMEWORK_JOB_REGISTRATION");
        if (entries.isEmpty()) throw new IllegalStateException("PUBLISHED_FRAMEWORK_JOBS_REQUIRED");
        registered = Map.copyOf(entries); jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/framework-job-command.schema.json"))) {
            schemaHash = hash(input.readAllBytes());
        }
    }
    @PutMapping("/api/monitor/job/run")
    @PreAuthorize("@ss.hasPermi('monitor:job:changeStatus')")
    public AjaxResult run(@RequestBody JsonNode body, @RequestHeader("Idempotency-Key") String key,
            HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws Exception {
        return submit("RUN", body, key, request, response, authentication);
    }
    @PutMapping("/api/monitor/job/changeStatus")
    @PreAuthorize("@ss.hasPermi('monitor:job:changeStatus')")
    public AjaxResult changeStatus(@RequestBody JsonNode body, @RequestHeader("Idempotency-Key") String key,
            HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws Exception {
        if (!body.path("status").isTextual()) throw invalid();
        String status = body.path("status").textValue();
        if (!Set.of("0", "1").contains(status)) throw invalid();
        return submit(status.equals("1") ? "PAUSE" : "RESUME", body, key, request, response, authentication);
    }
    private AjaxResult submit(String action, JsonNode body, String key, HttpServletRequest request,
            HttpServletResponse response, Authentication authentication) throws Exception {
        var subject = current(request, response, authentication, "monitor:job:changeStatus");
        if (!key.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw invalid();
        Set<String> keys = action.equals("RUN") ? Set.of("jobId", "jobGroup") : Set.of("jobId", "status");
        if (!body.isObject() || body.size() != keys.size()) throw invalid();
        body.fieldNames().forEachRemaining(field -> { if (!keys.contains(field)) throw invalid(); });
        if (action.equals("RUN") && !body.path("jobGroup").isTextual()) throw invalid();
        JsonNode jobValue = body.path("jobId"); long jobId;
        try {
            if (jobValue.isTextual() && jobValue.textValue().matches("[1-9][0-9]{0,18}")) jobId = Long.parseLong(jobValue.textValue());
            else if (jobValue.isIntegralNumber() && jobValue.canConvertToLong() && jobValue.longValue() <= 9007199254740991L) jobId = jobValue.longValue();
            else throw invalid();
        } catch (NumberFormatException failure) { throw invalid(); }
        if (jobId <= 0) throw invalid();
        var registration = registered.get(jobId);
        if (registration == null || action.equals("RUN") && !registration.jobGroup().equals(body.required("jobGroup").textValue())) throw denied();
        String operation = action.equals("RUN") ? "framework.job.run" : "framework.job.changeStatus";
        var binding = bindings.require(operation, "PUT", request.getRequestURI());
        verifyJob(registration);
        String digest = hash((action + "\n" + jobId + "\n" + registration.jobGroup()).getBytes(StandardCharsets.UTF_8));
        String original = json.writeValueAsString(Map.of("command_id", key, "state", "RESERVED", "execution_complete", false));
        boolean fresh = Boolean.TRUE.equals(transaction.execute(status -> {
            jdbc.queryForObject("SELECT coordination_key FROM platform_local_bootstrap WHERE coordination_key='LOCAL' FOR UPDATE", String.class);
            identities.lockCurrent(subject);
            var previous = jdbc.queryForList("SELECT actor_id,request_sha256,binding_manifest_sha256 FROM platform_framework_job_command WHERE command_id=?", key);
            if (!previous.isEmpty()) {
                var saved = previous.get(0);
                if (!subject.user().user_id().equals(saved.get("actor_id")) || !digest.equals(saved.get("request_sha256"))
                        || !binding.manifestSha256().equals(saved.get("binding_manifest_sha256"))) throw conflict();
                return false;
            }
            var targets = jdbc.queryForList("SELECT job_group,invoke_target FROM sys_job WHERE job_id=? FOR UPDATE", jobId);
            if (targets.size() != 1 || !registration.jobGroup().equals(targets.get(0).get("job_group"))
                    || !("registered:" + registration.handlerKey()).equals(targets.get(0).get("invoke_target"))) throw denied();
            var revisions = jdbc.queryForList("SELECT state FROM platform_framework_job_revision WHERE job_id=? ORDER BY revision DESC LIMIT 1", jobId);
            if (!revisions.isEmpty() && !"ACTIVE".equals(revisions.get(0).get("state"))) throw denied();
            Long unresolved = jdbc.queryForObject("SELECT COUNT(*) FROM platform_framework_job_command WHERE job_id=? AND state IN ('RESERVED','UNKNOWN')", Long.class, jobId);
            if (unresolved == null || unresolved != 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "PRIOR_JOB_COMMAND_UNRESOLVED");
            jdbc.update("""
                INSERT INTO platform_framework_job_command
                (command_id,actor_id,job_id,handler_key,job_group,action,request_sha256,binding_manifest_sha256,state,
                 original_response,schema_version,schema_manifest_sha256,created_by,created_at,updated_at,classification)
                VALUES (?,?,?,?,?,?,?,?,'RESERVED',?,'1.0.0',?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'RESTRICTED')
                """, key, subject.user().user_id(), jobId, registration.handlerKey(), registration.jobGroup(), action, digest,
                    binding.manifestSha256(), original, schemaHash, subject.user().user_id());
            return true;
        }));
        if (fresh) {
            try {
                // Recheck the release and principal immediately before the external scheduler effect.
                var currentBinding = bindings.require(operation, "PUT", request.getRequestURI());
                if (!currentBinding.equals(binding)) throw denied();
                current(request, response, authentication, "monitor:job:changeStatus"); verifyJob(registration);
                if (action.equals("RUN")) {
                    var trigger = TriggerBuilder.newTrigger().withIdentity(key, "medical-framework-command")
                            .forJob(registration.jobKey()).usingJobData("framework_command_id", key).startNow().build();
                    scheduler.scheduleJob(trigger);
                } else if (action.equals("PAUSE")) scheduler.pauseJob(registration.jobKey());
                else {
                    if (scheduler.getTriggersOfJob(registration.jobKey()).isEmpty()) {
                        var definitions = jdbc.queryForList("SELECT cron_expression,misfire_policy FROM sys_job WHERE job_id=? AND invoke_target=?",
                                jobId, "registered:" + registration.handlerKey());
                        if (definitions.size() != 1) throw denied();
                        var definition = definitions.get(0);
                        var schedule = CronScheduleBuilder.cronSchedule((String) definition.get("cron_expression"))
                                .inTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        schedule = switch ((String) definition.get("misfire_policy")) {
                            case "0" -> schedule;
                            case "1" -> schedule.withMisfireHandlingInstructionIgnoreMisfires();
                            case "2" -> schedule.withMisfireHandlingInstructionFireAndProceed();
                            case "3" -> schedule.withMisfireHandlingInstructionDoNothing();
                            default -> throw denied();
                        };
                        scheduler.scheduleJob(TriggerBuilder.newTrigger().withIdentity("registered-trigger-" + jobId, registration.jobGroup())
                                .forJob(registration.jobKey()).withSchedule(schedule).build());
                    }
                    scheduler.resumeJob(registration.jobKey());
                }
                transaction.executeWithoutResult(status -> {
                    if (!action.equals("RUN") && jdbc.update("UPDATE sys_job SET status=?,update_time=UTC_TIMESTAMP(6) WHERE job_id=? AND job_group=? AND invoke_target=?",
                            action.equals("PAUSE") ? "1" : "0", jobId, registration.jobGroup(), "registered:" + registration.handlerKey()) != 1)
                        throw new IllegalStateException("FRAMEWORK_JOB_STATE_CHANGED");
                    outcome(key, "DISPATCHED", "SCHEDULER_ACCEPTED");
                });
            } catch (Exception failure) {
                // A thrown scheduler/database error does not prove that submission did not happen.
                outcome(key, "UNKNOWN", "SCHEDULER_OUTCOME_UNKNOWN");
            }
        }
        response.setStatus(202);
        return AjaxResult.success(read(key, subject.user().user_id()));
    }
    @GetMapping("/api/monitor/job/commands/{commandId}")
    @PreAuthorize("@ss.hasPermi('monitor:job:query')")
    public AjaxResult command(@PathVariable String commandId, HttpServletRequest request,
            HttpServletResponse response, Authentication authentication) {
        var subject = current(request, response, authentication, "monitor:job:query");
        if (!commandId.matches("[0-9a-f-]{36}")) throw invalid();
        return AjaxResult.success(read(commandId, subject.user().user_id()));
    }
    @PostMapping("/api/monitor/job/commands/{commandId}/observations")
    @PreAuthorize("@ss.hasPermi('monitor:job:changeStatus')")
    public AjaxResult observe(@PathVariable String commandId, HttpServletRequest request,
            HttpServletResponse response, Authentication authentication) throws Exception {
        var subject = current(request, response, authentication, "monitor:job:changeStatus");
        if (!commandId.matches("[0-9a-f-]{36}") || request.getContentLengthLong() > 0) throw invalid();
        var records = jdbc.queryForList("SELECT job_id,action,state FROM platform_framework_job_command WHERE command_id=? AND actor_id=?",
                commandId, subject.user().user_id());
        if (records.size() != 1) throw denied();
        var record = records.get(0); var registration = registered.get(((Number) record.get("job_id")).longValue());
        if (registration == null) throw denied();
        String action = (String) record.get("action");
        String originalPermission = switch (action) {
            case "CREATE" -> "monitor:job:add"; case "EDIT" -> "monitor:job:edit";
            case "RETIRE" -> "monitor:job:remove"; default -> "monitor:job:changeStatus";
        };
        if (!permissions.hasPermi(originalPermission)) throw denied();
        boolean accepted = false;
        if (Set.of("UNKNOWN", "RESERVED").contains(record.get("state"))) {
            if (action.equals("RUN")) {
                verifyJob(registration);
                var trigger = scheduler.getTrigger(new TriggerKey(commandId, "medical-framework-command"));
                accepted = trigger != null && registration.jobKey().equals(trigger.getJobKey())
                        && commandId.equals(trigger.getJobDataMap().getString("framework_command_id"));
            } else if (Set.of("CREATE", "EDIT", "RETIRE").contains(action)) {
                var definitions = jdbc.queryForList("SELECT command_id,state FROM platform_framework_job_revision WHERE job_id=? ORDER BY revision DESC LIMIT 1", registration.jobId());
                if (definitions.size() == 1 && commandId.equals(definitions.get(0).get("command_id"))) {
                    var detail = scheduler.getJobDetail(registration.jobKey());
                    if (action.equals("RETIRE")) accepted = detail == null && "RETIRED".equals(definitions.get(0).get("state"));
                    else accepted = detail != null && detail.getJobClass() == registration.jobClass()
                            && detail.getJobDataMap().isEmpty() && detail.isDurable()
                            && scheduler.getTriggersOfJob(registration.jobKey()).isEmpty();
                }
            }
            if (Set.of("PAUSE", "RESUME").contains(action)) {
                transaction.executeWithoutResult(status -> {
                    jdbc.queryForObject("SELECT coordination_key FROM platform_local_bootstrap WHERE coordination_key='LOCAL' FOR UPDATE", String.class);
                    identities.lockCurrent(subject);
                    var eligible = jdbc.queryForList("SELECT command_id FROM platform_framework_job_command WHERE command_id=? AND actor_id=? AND (state='UNKNOWN' OR (state='RESERVED' AND created_at<UTC_TIMESTAMP(6)-INTERVAL 120 SECOND)) FOR UPDATE", commandId, subject.user().user_id());
                    if (eligible.size() != 1) return;
                    try {
                        verifyJob(registration);
                        var triggers = scheduler.getTriggersOfJob(registration.jobKey());
                        // An absent trigger proves neither pause nor resume. Every existing trigger must agree.
                        if (triggers.isEmpty()) return;
                        for (var trigger : triggers) {
                            var observed = scheduler.getTriggerState(trigger.getKey());
                            if (action.equals("PAUSE") ? observed != Trigger.TriggerState.PAUSED
                                    : observed != Trigger.TriggerState.NORMAL && observed != Trigger.TriggerState.BLOCKED) return;
                        }
                        if (jdbc.update("UPDATE sys_job SET status=?,update_time=UTC_TIMESTAMP(6) WHERE job_id=? AND job_group=? AND invoke_target=?",
                                action.equals("PAUSE") ? "1" : "0", registration.jobId(), registration.jobGroup(), "registered:" + registration.handlerKey()) != 1) throw denied();
                        jdbc.update("UPDATE platform_framework_job_command SET state='DISPATCHED',outcome_code='SCHEDULER_STATE_OBSERVED',updated_at=UTC_TIMESTAMP(6) WHERE command_id=?", commandId);
                    } catch (SchedulerException unavailable) { throw new IllegalStateException("SCHEDULER_OBSERVATION_UNAVAILABLE"); }
                });
            }
            // Absence of a RUN trigger is never interpreted as non-execution or completion.
            if (accepted) jdbc.update("UPDATE platform_framework_job_command SET state='DISPATCHED',outcome_code='SCHEDULER_STATE_OBSERVED',updated_at=UTC_TIMESTAMP(6) "
                    + "WHERE command_id=? AND (state='UNKNOWN' OR (state='RESERVED' AND created_at<UTC_TIMESTAMP(6)-INTERVAL 120 SECOND))", commandId);
        }
        return AjaxResult.success(read(commandId, subject.user().user_id()));
    }

    private Map<String, Object> read(String id, String actor) {
        var rows = jdbc.queryForList("SELECT command_id,state,outcome_code,created_at,updated_at FROM platform_framework_job_command WHERE command_id=? AND actor_id=?", id, actor);
        if (rows.size() != 1) throw denied(); rows.get(0).put("execution_complete", false); return rows.get(0);
    }
    private void verifyJob(FrameworkJobRegistration registration) throws SchedulerException {
        var detail = scheduler.getJobDetail(registration.jobKey());
        if (detail == null || detail.getJobClass() != registration.jobClass() || !detail.getJobDataMap().isEmpty()) throw denied();
    }
    private void outcome(String id, String state, String code) {
        if (jdbc.update("UPDATE platform_framework_job_command SET state=?,outcome_code=?,updated_at=UTC_TIMESTAMP(6) WHERE command_id=? AND state='RESERVED'", state, code, id) != 1)
            throw new IllegalStateException("FRAMEWORK_JOB_OUTCOME_NOT_RECORDED");
    }
    private ApplicationSessionIdentity.Verified current(HttpServletRequest request, HttpServletResponse response, Authentication authentication, String permission) {
        var subject = identities.requireCurrent(request, authentication);
        if (!"PLATFORM_ADMIN".equals(subject.user().platform_role()) || !permissions.hasPermi(permission)) throw denied();
        if (!request.getParameterMap().isEmpty()) throw invalid(); response.setHeader("Cache-Control", "no-store"); return subject;
    }
    private static String hash(byte[] bytes) throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("ACCESS_DENIED"); }
    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_JOB_COMMAND"); }
    private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT"); }
}
