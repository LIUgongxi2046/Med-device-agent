package com.ruoyi.medical.runtime;

import com.alibaba.druid.pool.DruidDataSource;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Metrics projection, never the native host/arguments/SQL-console payload. */
@RestController
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
public class FrameworkMonitorController {
    private final ApplicationSessionIdentity identities;
    private final DataSource dataSource;
    public FrameworkMonitorController(ApplicationSessionIdentity identities, @Qualifier("masterDataSource") DataSource dataSource) {
        this.identities = identities; this.dataSource = dataSource;
    }
    private void authorize(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (!"PLATFORM_ADMIN".equals(identities.requireCurrent(request, authentication).user().platform_role()))
            throw new AccessDeniedException("ACCESS_DENIED");
        if (!request.getParameterMap().isEmpty()) throw new IllegalArgumentException("UNKNOWN_MONITOR_QUERY");
        response.setHeader("Cache-Control", "no-store");
    }
    @GetMapping("/api/monitor/server")
    @PreAuthorize("@ss.hasPermi('monitor:server:list')")
    public AjaxResult server(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        authorize(request, response, authentication);
        var runtime = Runtime.getRuntime(); var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var jvm = new LinkedHashMap<String, Object>();
        jvm.put("total", heap.getCommitted() / 1048576.0); jvm.put("used", heap.getUsed() / 1048576.0);
        jvm.put("free", (heap.getCommitted() - heap.getUsed()) / 1048576.0);
        jvm.put("usage", heap.getCommitted() == 0 ? null : 100.0 * heap.getUsed() / heap.getCommitted());
        jvm.put("name", System.getProperty("java.vm.name")); jvm.put("version", System.getProperty("java.version"));
        jvm.put("startTime", java.time.Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()).toString());
        jvm.put("runTime", ManagementFactory.getRuntimeMXBean().getUptime() + " ms");
        jvm.put("home", "REDACTED"); jvm.put("inputArgs", "REDACTED");
        var cpu = new LinkedHashMap<String, Object>(); cpu.put("cpuNum", runtime.availableProcessors());
        // Unsupported samples are unknown, never fabricated zero measurements.
        cpu.put("used", null); cpu.put("sys", null); cpu.put("free", null);
        return AjaxResult.success(Map.of("cpu", cpu, "jvm", jvm, "sysFiles", List.of(),
                "sys", Map.of("computerName", "REDACTED", "computerIp", "REDACTED", "userDir", "REDACTED",
                        "osName", System.getProperty("os.name"), "osArch", System.getProperty("os.arch"))));
    }
    @GetMapping("/api/monitor/druid")
    @PreAuthorize("@ss.hasPermi('monitor:druid:list')")
    public AjaxResult connectionPool(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        authorize(request, response, authentication);
        if (!(dataSource instanceof DruidDataSource pool)) throw new IllegalStateException("CONNECTION_POOL_METRICS_UNAVAILABLE");
        return AjaxResult.success(Map.of("activeCount", pool.getActiveCount(), "poolingCount", pool.getPoolingCount(),
                "maxActive", pool.getMaxActive(), "waitThreadCount", pool.getWaitThreadCount(), "sqlConsoleEnabled", false));
    }
}
