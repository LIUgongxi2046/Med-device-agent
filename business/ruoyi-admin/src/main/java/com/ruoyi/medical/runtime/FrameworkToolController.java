package com.ruoyi.medical.runtime;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Read-only interface documentation; no credential-bearing Swagger try-it-out in medical mode. */
@RestController
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
public class FrameworkToolController {
    private final ApplicationSessionIdentity identities;
    private final PermissionService permissions;
    private final RequestMappingHandlerMapping routes;
    private final Environment environment;
    public FrameworkToolController(ApplicationSessionIdentity identities, PermissionService permissions,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping routes, Environment environment) {
        this.identities = identities; this.permissions = permissions; this.routes = routes; this.environment = environment;
    }
    @GetMapping("/api/tool/swagger")
    @PreAuthorize("@ss.hasPermi('tool:swagger:list')")
    public AjaxResult documentation(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        current(request, response, authentication, "tool:swagger:list");
        var operations = new ArrayList<Map<String, String>>();
        routes.getHandlerMethods().forEach((mapping, method) -> {
            String type = method.getBeanType().getName();
            if (!type.startsWith("com.ruoyi.web.controller.") && !method.getBeanType().getSimpleName().startsWith("Framework")) return;
            var guard = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), PreAuthorize.class);
            if (guard == null) guard = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), PreAuthorize.class);
            if (guard == null) return;
            var match = java.util.regex.Pattern.compile("@ss\\.hasPermi\\('([a-zA-Z0-9:]+)'\\)").matcher(guard.value());
            if (!match.matches() || !permissions.hasPermi(match.group(1))) return;
            for (String path : mapping.getPatternValues()) {
                if (!path.matches("/api/(system|monitor|tool)/.*")) continue;
                for (var verb : mapping.getMethodsCondition().getMethods()) {
                    operations.add(Map.of("method", verb.name(), "path", path, "permission", match.group(1),
                            "operation", method.getBeanType().getSimpleName() + "." + method.getMethod().getName()));
                }
            }
        });
        operations.sort(Comparator.comparing(row -> row.get("path") + row.get("method")));
        return AjaxResult.success(Map.of("operations", operations, "try_it_out", false,
                "environment", environment.getProperty("medical.framework.environment", "UNDECLARED")));
    }
    @GetMapping("/api/tool/build/capabilities")
    @PreAuthorize("@ss.hasPermi('tool:build:list')")
    public AjaxResult formBuilder(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        current(request, response, authentication, "tool:build:list");
        if (!Set.of("DESIGN", "DEVELOPMENT").contains(environment.getProperty("medical.framework.environment", ""))
                || !permissions.hasRole("developer")) throw new AccessDeniedException("DESIGN_ENVIRONMENT_REQUIRED");
        return AjaxResult.success(Map.of("local_design", true, "server_script_execution", false,
                "controlled_schema_publication", true, "publication_requires", "CURRENT_TENANT_BINDING_AND_INDEPENDENT_APPROVAL"));
    }
    private void current(HttpServletRequest request, HttpServletResponse response, Authentication authentication, String permission) {
        response.setHeader("Cache-Control", "no-store");
        if (!"PLATFORM_ADMIN".equals(identities.requireCurrent(request, authentication).user().platform_role())
                || !permissions.hasPermi(permission)) throw new AccessDeniedException("ACCESS_DENIED");
        if (!request.getParameterMap().isEmpty()) throw new IllegalArgumentException("UNKNOWN_TOOL_QUERY");
    }
}
