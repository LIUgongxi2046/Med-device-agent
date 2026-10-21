package com.ruoyi.medical.identity;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.system.domain.SysCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** No credential/session/authorization, CAPTCHA, replay, rate-limit or password cache access. */
@RestController
@RequestMapping("/api/monitor/cache")
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
@PreAuthorize("@ss.hasPermi('monitor:cache:list')")
public class FrameworkCacheMonitorController {
    private static final String DICT = "sys_dict:";
    private static final String CONFIG = "sys_config:";
    // Configuration contains password/registration policy too: never allow its whole prefix.
    private static final Set<String> UI_CONFIG = Set.of(CONFIG + "sys.index.skinName", CONFIG + "sys.index.sideTheme");
    private static final Set<String> INFO_FIELDS = Set.of("redis_version", "redis_mode", "tcp_port", "connected_clients",
            "uptime_in_days", "used_memory_human", "used_memory", "used_cpu_user_children", "maxmemory_human",
            "aof_enabled", "rdb_last_bgsave_status", "instantaneous_input_kbps", "instantaneous_output_kbps");
    private final ApplicationSessionIdentity identities;
    private final PermissionService permissions;
    private final StringRedisTemplate redis;

    public FrameworkCacheMonitorController(ApplicationSessionIdentity identities, PermissionService permissions, StringRedisTemplate redis) {
        this.identities = identities; this.permissions = permissions; this.redis = redis;
    }

    @GetMapping
    public AjaxResult info(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        current(request, response, authentication);
        Properties raw = redis.execute((RedisCallback<Properties>) connection -> connection.serverCommands().info());
        if (raw == null) throw unavailable();
        var info = new LinkedHashMap<String, String>();
        for (String field : INFO_FIELDS) if (raw.getProperty(field) != null) info.put(field, raw.getProperty(field));
        Properties stats = redis.execute((RedisCallback<Properties>) connection -> connection.serverCommands().info("commandstats"));
        if (stats == null) throw unavailable();
        var commands = new ArrayList<Map<String, String>>();
        // Aggregate command counts only. Never return clients, slowlog arguments or arbitrary INFO fields.
        for (String command : List.of("get", "set", "del", "expire", "scan", "hget", "hset")) {
            String stat = stats.getProperty("cmdstat_" + command);
            if (stat == null) continue;
            var matcher = java.util.regex.Pattern.compile("(?:^|,)calls=([0-9]+)(?:,|$)").matcher(stat);
            if (!matcher.find()) throw unavailable();
            commands.add(Map.of("name", command, "value", matcher.group(1)));
        }
        Long size = redis.execute((RedisCallback<Long>) connection -> connection.serverCommands().dbSize());
        if (size == null) throw unavailable();
        return AjaxResult.success(Map.of("info", info, "dbSize", size, "commandStats", commands,
                "keyAccessScope", "DICTIONARIES_AND_UI_CONFIG_ONLY"));
    }

    @GetMapping("/getNames")
    public AjaxResult names(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        current(request, response, authentication);
        return AjaxResult.success(List.of(new SysCache(DICT, "数据字典"), new SysCache(CONFIG, "非安全界面配置（仅皮肤与侧栏主题）")));
    }

    @GetMapping("/getKeys/{cacheName}")
    public AjaxResult keys(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String cacheName) {
        current(request, response, authentication); return AjaxResult.success(allowedKeys(cacheName));
    }

    @GetMapping("/getValue/{cacheName}/{cacheKey}")
    public AjaxResult value(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String cacheName, @PathVariable String cacheKey) {
        current(request, response, authentication); requireName(cacheName); requireKey(cacheKey);
        if (!cacheKey.startsWith(cacheName)) throw new AccessDeniedException("ACCESS_DENIED");
        Long bytes = redis.opsForValue().size(cacheKey);
        if (bytes == null || bytes > 262144) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CACHE_VALUE_TOO_LARGE");
        String value = redis.opsForValue().get(cacheKey);
        if (value == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CACHE_KEY_NOT_FOUND");
        if (value.length() > 262144) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CACHE_VALUE_TOO_LARGE");
        return AjaxResult.success(new SysCache(cacheName, cacheKey, value));
    }

    @DeleteMapping("/clearCacheName/{cacheName}")
    public AjaxResult clearName(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String cacheName) {
        current(request, response, authentication);
        Set<String> keys = allowedKeys(cacheName);
        if (keys.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "CACHE_NAMESPACE_ALREADY_EMPTY");
        // Delete the exact observed safe keys, not a pattern or a global flush.
        Long removed = redis.delete(keys);
        if (removed == null) throw unavailable();
        if (removed != keys.size()) throw new ResponseStatusException(HttpStatus.CONFLICT, "CACHE_KEYS_CHANGED_REFRESH_REQUIRED");
        return AjaxResult.success(Map.of("deleted", removed, "scope", "ALLOWLISTED_KEYS_ONLY"));
    }

    @DeleteMapping("/clearCacheKey/{cacheKey}")
    public AjaxResult clearKey(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String cacheKey) {
        current(request, response, authentication); requireKey(cacheKey);
        Boolean deleted = redis.delete(cacheKey);
        if (deleted == null) throw unavailable();
        if (!deleted) throw new ResponseStatusException(HttpStatus.CONFLICT, "CACHE_KEY_NO_LONGER_PRESENT");
        return AjaxResult.success(Map.of("deleted", 1));
    }

    @DeleteMapping("/clearCacheAll")
    public AjaxResult clearAll(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        current(request, response, authentication);
        throw new AccessDeniedException("GLOBAL_CACHE_CLEAR_FORBIDDEN");
    }

    private Set<String> allowedKeys(String name) {
        requireName(name);
        var result = new TreeSet<String>();
        if (CONFIG.equals(name)) {
            for (String key : UI_CONFIG) if (Boolean.TRUE.equals(redis.hasKey(key))) result.add(key);
        } else {
            int visited = 0;
            try (var cursor = redis.scan(ScanOptions.scanOptions().match(DICT + "*").count(200).build())) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    if (++visited > 10000) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CACHE_SCOPE_TOO_LARGE");
                    if (isAllowedKey(key)) result.add(key);
                }
            }
        }
        return result;
    }
    static boolean isAllowedKey(String key) {
        return key != null && (UI_CONFIG.contains(key) || key.matches("sys_dict:[A-Za-z0-9_]{1,100}"));
    }
    private static void requireKey(String key) { if (!isAllowedKey(key)) throw new AccessDeniedException("ACCESS_DENIED"); }
    private static void requireName(String name) { if (!DICT.equals(name) && !CONFIG.equals(name)) throw new AccessDeniedException("ACCESS_DENIED"); }
    private void current(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        response.setHeader("Cache-Control", "no-store");
        var subject = identities.requireCurrent(request, authentication);
        if (!"PLATFORM_ADMIN".equals(subject.user().platform_role()) || !permissions.hasPermi("monitor:cache:list")) throw new AccessDeniedException("ACCESS_DENIED");
    }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CACHE_OBSERVATION_UNAVAILABLE"); }
}
