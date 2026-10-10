package com.ruoyi.medical.identity;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import static org.junit.jupiter.api.Assertions.*;

class IdentityDirectoryTest {
    @Test
    void platformDirectoryRejectsMissingAuthenticatedIdentity() throws Exception {
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        String name = "com.ruoyi.medical.identity.VerifiedOidcIdentity";
        assertNotNull(getClass().getClassLoader().getResource(name.replace('.', '/') + ".class"), "平台身份目录必须验证当前 OIDC 认证身份");
        var method = Class.forName(name).getDeclaredMethod("requireCurrent", OidcPolicy.class);
        var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, (OidcPolicy) null));
        var denial = assertInstanceOf(OAuth2AuthenticationException.class, failure.getCause());
        assertEquals("AUTHENTICATION_REQUIRED", denial.getError().getErrorCode());
        assertNull(denial.getCause());
    }

    @Test
    void platformIdentityClassificationRequiresConfiguration() throws Exception {
        String name = "com.ruoyi.medical.identity.IdentityDirectoryPolicy";
        assertNotNull(getClass().getClassLoader().getResource(name.replace('.', '/') + ".class"), "平台身份保存必须使用批准的数据分类配置");
        var constructor = Class.forName(name).getConstructor(String.class);
        var failure = assertThrows(InvocationTargetException.class, () -> constructor.newInstance((String) null));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals("IDENTITY_DIRECTORY_CONFIGURATION_REQUIRED", failure.getCause().getMessage());
    }
}
