package com.ruoyi.medical.runtime;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Reads existing Quartz records without instantiating its destructive startup scheduler. */
@RestController
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
public class FrameworkJobMonitorController {
    private final ApplicationSessionIdentity identities;
    private final JdbcTemplate jdbc;
    public FrameworkJobMonitorController(ApplicationSessionIdentity identities, @Qualifier("masterDataSource") DataSource source) {
        this.identities = identities; jdbc = new JdbcTemplate(source);
    }
    private void authorize(HttpServletRequest request, HttpServletResponse response, Authentication auth, Set<String> keys) {
        if (!"PLATFORM_ADMIN".equals(identities.requireCurrent(request, auth).user().platform_role()))
            throw new AccessDeniedException("ACCESS_DENIED");
        request.getParameterMap().forEach((key, values) -> {
            if (!keys.contains(key) || values.length != 1 || values[0].length() > 128)
                throw new IllegalArgumentException("INVALID_JOB_QUERY");
        });
        response.setHeader("Cache-Control", "no-store");
    }
    private int number(HttpServletRequest request, String key, int fallback, int maximum) {
        String value = request.getParameter(key);
        if (value == null) return fallback;
        if (!value.matches("[1-9][0-9]{0,5}")) throw new IllegalArgumentException("INVALID_JOB_QUERY");
        int result = Integer.parseInt(value);
        if (result > maximum) throw new IllegalArgumentException("INVALID_JOB_QUERY");
        return result;
    }
    @GetMapping("/api/monitor/job/list")
    @PreAuthorize("@ss.hasPermi('monitor:job:list')")
    public Map<String, Object> jobs(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        authorize(request, response, auth, Set.of("pageNum", "pageSize", "jobName", "jobGroup", "status"));
        return list(request, false);
    }
    @GetMapping("/api/monitor/job/{jobId:[0-9]+}")
    @PreAuthorize("@ss.hasPermi('monitor:job:query')")
    public AjaxResult job(@PathVariable long jobId, HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        authorize(request, response, auth, Set.of());
        var rows = jdbc.queryForList("SELECT job_id AS jobId,job_name AS jobName,job_group AS jobGroup,cron_expression AS cronExpression,"
                + "misfire_policy AS misfirePolicy,concurrent,status,create_time AS createTime,CAST((SELECT MAX(r.revision) FROM platform_framework_job_revision r WHERE r.job_id=sys_job.job_id) AS CHAR) AS frameworkRevision FROM sys_job WHERE job_id=?", jobId);
        if (rows.size() != 1) throw new IllegalArgumentException("JOB_NOT_FOUND");
        rows.get(0).put("jobId", rows.get(0).get("jobId").toString());
        rows.get(0).put("invokeTarget", "REDACTED");
        return AjaxResult.success(rows.get(0));
    }
    @GetMapping("/api/monitor/jobLog/list")
    @PreAuthorize("@ss.hasPermi('monitor:job:list')")
    public Map<String, Object> logs(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        authorize(request, response, auth, Set.of("pageNum", "pageSize", "jobName", "jobGroup", "status", "params[beginTime]", "params[endTime]"));
        return list(request, true);
    }
    private Map<String, Object> list(HttpServletRequest request, boolean logs) {
        int page = number(request, "pageNum", 1, 10000), size = number(request, "pageSize", 10, 100);
        StringBuilder where = new StringBuilder(" WHERE 1=1"); var args = new ArrayList<Object>();
        for (String key : List.of("jobName", "jobGroup", "status")) {
            String value = request.getParameter(key);
            if (value == null || value.isEmpty()) continue;
            if (key.equals("status") && !Set.of("0", "1").contains(value)) throw new IllegalArgumentException("INVALID_JOB_QUERY");
            String column = Map.of("jobName", "job_name", "jobGroup", "job_group", "status", "status").get(key);
            where.append(" AND ").append(column).append("=?"); args.add(value);
        }
        if (logs) for (String key : List.of("params[beginTime]", "params[endTime]")) {
            String value = request.getParameter(key);
            if (value == null || value.isBlank()) continue;
            try { java.time.LocalDate.parse(value); } catch (java.time.DateTimeException invalid) { throw new IllegalArgumentException("INVALID_JOB_DATE"); }
            where.append(key.contains("beginTime") ? " AND create_time>=?" : " AND create_time<DATE_ADD(?, INTERVAL 1 DAY)");
            args.add(value);
        }
        if (!logs) where.append(" AND NOT EXISTS (SELECT 1 FROM platform_framework_job_revision retired WHERE retired.job_id=sys_job.job_id AND retired.state='RETIRED')");
        String table = logs ? "sys_job_log" : "sys_job";
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where, Long.class, args.toArray());
        String fields = logs ? "job_log_id AS jobLogId,job_name AS jobName,job_group AS jobGroup,status,create_time AS createTime"
                : "job_id AS jobId,job_name AS jobName,job_group AS jobGroup,cron_expression AS cronExpression,status,create_time AS createTime,CAST((SELECT MAX(r.revision) FROM platform_framework_job_revision r WHERE r.job_id=sys_job.job_id) AS CHAR) AS frameworkRevision";
        args.add(size); args.add((page - 1) * size);
        var rows = jdbc.queryForList("SELECT " + fields + " FROM " + table + where + " ORDER BY "
                + (logs ? "job_log_id" : "job_id") + " DESC LIMIT ? OFFSET ?", args.toArray());
        rows.forEach(row -> { String id = logs ? "jobLogId" : "jobId"; row.put(id, row.get(id).toString()); row.put("invokeTarget", "REDACTED"); if (logs) {row.put("jobMessage", "REDACTED"); row.put("exceptionInfo", "REDACTED");} });
        return Map.of("code", 200, "msg", "success", "rows", rows, "total", count == null ? 0 : count);
    }
}
