package com.ruoyi.medical.runtime;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.quartz.domain.SysJob;
import com.ruoyi.quartz.domain.SysJobLog;
import jakarta.servlet.http.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Original spreadsheet exporters with bounded filters and redacted invocation/exception content. */
@RestController
@ConditionalOnProperty(name="medical.identity.mode",havingValue="local")
public class FrameworkJobExportController {
    private final ApplicationSessionIdentity identities; private final JdbcTemplate jdbc;
    public FrameworkJobExportController(ApplicationSessionIdentity identities,@Qualifier("masterDataSource")DataSource source){this.identities=identities;jdbc=new JdbcTemplate(source);}
    @PostMapping("/api/monitor/job/export") @PreAuthorize("@ss.hasPermi('monitor:job:export')")
    @Log(title="定时任务",businessType=BusinessType.EXPORT)
    public void jobs(HttpServletRequest request,HttpServletResponse response,Authentication auth){
        var filter=filter(request,response,auth,false);
        var rows=jdbc.query("SELECT job_id,job_name,job_group,cron_expression,misfire_policy,concurrent,status FROM sys_job"+filter.sql+" ORDER BY job_id LIMIT 10001",(r,n)->{
            var row=new SysJob();row.setJobId(r.getLong(1));row.setJobName(r.getString(2));row.setJobGroup(r.getString(3));row.setCronExpression(r.getString(4));row.setMisfirePolicy(r.getString(5));row.setConcurrent(r.getString(6));row.setStatus(r.getString(7));row.setInvokeTarget("REDACTED");return row;
        },filter.arguments.toArray());
        if(rows.size()>10000)throw new IllegalArgumentException("EXPORT_SCOPE_TOO_LARGE_REFINE_FILTERS");
        new ExcelUtil<>(SysJob.class).exportExcel(response,rows,"定时任务");
    }
    @PostMapping("/api/monitor/jobLog/export") @PreAuthorize("@ss.hasPermi('monitor:job:export')")
    @Log(title="调度日志",businessType=BusinessType.EXPORT)
    public void logs(HttpServletRequest request,HttpServletResponse response,Authentication auth){
        var filter=filter(request,response,auth,true);
        var rows=jdbc.query("SELECT job_log_id,job_name,job_group,status,create_time FROM sys_job_log"+filter.sql+" ORDER BY job_log_id LIMIT 10001",(r,n)->{
            var row=new SysJobLog();row.setJobLogId(r.getLong(1));row.setJobName(r.getString(2));row.setJobGroup(r.getString(3));row.setStatus(r.getString(4));row.setCreateTime(r.getTimestamp(5));row.setInvokeTarget("REDACTED");row.setJobMessage("REDACTED");row.setExceptionInfo("REDACTED");return row;
        },filter.arguments.toArray());
        if(rows.size()>10000)throw new IllegalArgumentException("EXPORT_SCOPE_TOO_LARGE_REFINE_FILTERS");
        new ExcelUtil<>(SysJobLog.class).exportExcel(response,rows,"调度日志");
    }
    private Filter filter(HttpServletRequest request,HttpServletResponse response,Authentication auth,boolean logs){
        if(!"PLATFORM_ADMIN".equals(identities.requireCurrent(request,auth).user().platform_role()))throw new AccessDeniedException("ACCESS_DENIED");
        response.setHeader("Cache-Control","no-store");
        Set<String> allowed=Set.of("jobName","jobGroup","status","params[beginTime]","params[endTime]","pageNum","pageSize");
        request.getParameterMap().forEach((key,value)->{if(!allowed.contains(key)||value.length!=1||value[0].length()>128)throw new IllegalArgumentException("INVALID_EXPORT_QUERY");});
        var sql=new StringBuilder(" WHERE 1=1");var arguments=new ArrayList<Object>();
        for(String key:List.of("jobName","jobGroup","status")){
            String value=request.getParameter(key);if(value==null||value.isBlank())continue;
            if(key.equals("status")&&!Set.of("0","1").contains(value))throw new IllegalArgumentException("INVALID_EXPORT_STATUS");
            sql.append(" AND ").append(Map.of("jobName","job_name","jobGroup","job_group","status","status").get(key)).append("=?");arguments.add(value);
        }
        for(String key:List.of("params[beginTime]","params[endTime]")){
            String value=request.getParameter(key);if(value==null||value.isBlank())continue;
            java.time.LocalDate.parse(value);sql.append(key.contains("beginTime")?" AND create_time>=?":" AND create_time<DATE_ADD(?,INTERVAL 1 DAY)");arguments.add(value);
        }
        if(!logs)sql.append(" AND NOT EXISTS (SELECT 1 FROM platform_framework_job_revision r WHERE r.job_id=sys_job.job_id AND r.state='RETIRED')");
        return new Filter(sql.toString(),arguments);
    }
    private record Filter(String sql,List<Object>arguments){}
}
