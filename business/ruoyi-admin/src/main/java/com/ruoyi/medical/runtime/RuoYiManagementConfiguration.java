package com.ruoyi.medical.runtime;

import com.ruoyi.framework.aspectj.LogAspect;
import com.ruoyi.generator.controller.GenController;
import com.ruoyi.framework.config.ThreadPoolConfig;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.web.controller.monitor.SysLogininforController;
import com.ruoyi.web.controller.monitor.SysOperlogController;
import com.ruoyi.web.controller.system.SysConfigController;
import com.ruoyi.web.controller.system.SysDeptController;
import com.ruoyi.web.controller.system.SysDictDataController;
import com.ruoyi.web.controller.system.SysDictTypeController;
import com.ruoyi.web.controller.system.SysMenuController;
import com.ruoyi.web.controller.system.SysNoticeController;
import com.ruoyi.web.controller.system.SysPostController;
import com.ruoyi.web.controller.system.SysRoleController;
import com.ruoyi.web.controller.system.SysUserController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
@Import({SysUserController.class, SysDeptController.class, SysMenuController.class,
        SysPostController.class, SysDictTypeController.class, SysDictDataController.class,
        SysConfigController.class, SysNoticeController.class, SysOperlogController.class,
        SysLogininforController.class, SysRoleController.class, TokenService.class, LogAspect.class, ThreadPoolConfig.class})
public class RuoYiManagementConfiguration implements WebMvcConfigurer {
    static final Set<Class<?>> CONTROLLERS = Set.of(SysUserController.class, SysDeptController.class,
            SysMenuController.class, SysPostController.class, SysDictTypeController.class, SysDictDataController.class,
            SysConfigController.class, SysNoticeController.class, SysOperlogController.class, SysLogininforController.class,
            SysRoleController.class, GenController.class);
    static final Map<String, String> AUXILIARY_PERMISSIONS = Map.ofEntries(
            Map.entry("SysUserController#importTemplate", "system:user:import"),
            Map.entry("SysPostController#optionselect", "system:post:query"),
            Map.entry("SysMenuController#treeselect", "system:menu:query"),
            Map.entry("SysMenuController#roleMenuTreeselect", "system:role:query"),
            Map.entry("SysDictTypeController#optionselect", "system:dict:query"),
            Map.entry("SysDictDataController#dictType", "system:dict:query"),
            Map.entry("SysConfigController#getConfigKey", "system:config:query"),
            Map.entry("SysNoticeController#getInfo", "system:notice:query"),
            Map.entry("SysNoticeController#listTop", "system:notice:list"),
            Map.entry("SysNoticeController#markRead", "system:notice:list"),
            Map.entry("SysNoticeController#markReadAll", "system:notice:list"));
    private final ApplicationSessionIdentity identities;
    private final PermissionService permissions;

    public RuoYiManagementConfiguration(ApplicationSessionIdentity identities, PermissionService permissions) {
        this.identities = identities;
        this.permissions = permissions;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 原Controller继续处理请求，统一前缀纳入现有Cookie、CSRF和当前会话检查。
        configurer.addPathPrefix("/api", type -> CONTROLLERS.contains(ClassUtils.getUserClass(type)));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!(handler instanceof HandlerMethod method)) return true;
                Class<?> type = ClassUtils.getUserClass(method.getBeanType());
                if (!CONTROLLERS.contains(type)) return true;
                var subject = identities.requireCurrent(request, SecurityContextHolder.getContext().getAuthentication());
                // 全局框架管理表由平台管理身份使用，企业成员不能经此入口读取其他企业资料。
                if (!"PLATFORM_ADMIN".equals(subject.user().platform_role())) denied();
                response.setHeader("Cache-Control", "no-store");
                if (type == GenController.class) {
                    // Native permissions remain mandatory. No production or ordinary admin fallback.
                    if (!permissions.hasRole("developer")) denied();
                    // SQL creation and filesystem writes are not a deployable medical capability.
                    if (Set.of("createTableSave", "genCode").contains(method.getMethod().getName())) denied();
                }
                // Native historical GET mutations still require the current CSRF token.
                if ((type == GenController.class && Set.of("genCode", "synchDb").contains(method.getMethod().getName()))
                        || (type == SysLogininforController.class && method.getMethod().getName().equals("unlock"))) {
                    Object attribute = request.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
                    if (!(attribute instanceof org.springframework.security.web.csrf.CsrfToken)) denied();
                    var token = (org.springframework.security.web.csrf.CsrfToken) attribute;
                    String supplied = request.getHeader(token.getHeaderName());
                    if (supplied == null || !java.security.MessageDigest.isEqual(
                            supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            token.getToken().getBytes(java.nio.charset.StandardCharsets.UTF_8))) denied();
                }
                if (AnnotatedElementUtils.hasAnnotation(method.getMethod(), PreAuthorize.class)) return true;
                String permission = AUXILIARY_PERMISSIONS.get(type.getSimpleName() + "#" + method.getMethod().getName());
                if (permission == null) throw new IllegalStateException("FRAMEWORK_OPERATION_PERMISSION_REQUIRED");
                if (!permissions.hasPermi(permission)) denied();
                return true;
            }
        });
    }

    private static void denied() { throw new AccessDeniedException("ACCESS_DENIED"); }
}
