package com.ruoyi.medical.identity;

import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.UserDetailsServiceImpl;
import com.ruoyi.system.service.impl.SysMenuServiceImpl;
import com.ruoyi.system.service.impl.SysRoleServiceImpl;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import static org.junit.jupiter.api.Assertions.*;

class RuoYiAuthenticationConfigurationTest {
    @Test
    void originalAccountAuthenticationAndItsPermissionDependenciesAreRegistered() {
        var imports = Set.of(RuoYiAccountConfiguration.class.getAnnotation(Import.class).value());
        assertTrue(imports.contains(UserDetailsServiceImpl.class), "账户验证必须由原框架服务提供");
        assertTrue(imports.contains(SysPermissionService.class), "原认证服务必须使用原权限服务");
        assertTrue(imports.contains(SysRoleServiceImpl.class));
        assertTrue(imports.contains(SysMenuServiceImpl.class));
    }

    @Test
    void medicalAuthenticationRetainsTheOriginalFrameworkIdentity() {
        assertTrue(LoginUser.class.isAssignableFrom(LocalPrincipal.class), "原身份工具和管理组件必须能够读取当前认证主体");
    }

    @Test
    void originalManagementPermissionComponentIsRegistered() {
        var imports = Set.of(RuoYiAccountConfiguration.class.getAnnotation(Import.class).value());
        assertTrue(imports.contains(PermissionService.class), "管理权限判断必须直接使用原 ss 组件");
    }
}
