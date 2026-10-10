package com.ruoyi.medical.identity;

import jakarta.servlet.http.HttpSession;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import static org.junit.jupiter.api.Assertions.*;

class OidcSessionBindingTest {
    @Test
    void sessionBindingRequiresCurrentAuthenticatedIdentityBeforeSessionAccess() throws Exception {
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        String name = "com.ruoyi.medical.identity.VerifiedOidcSession";
        assertNotNull(getClass().getClassLoader().getResource(name.replace('.', '/') + ".class"),
                "应用会话绑定必须使用当前认证身份的生产入口");
        var method = Class.forName(name).getDeclaredMethod("requireCurrent", OidcPolicy.class,
                ClientRegistration.class, HttpSession.class);
        var failure = assertThrows(InvocationTargetException.class,
                () -> method.invoke(null, null, null, null));
        var denial = assertInstanceOf(OAuth2AuthenticationException.class, failure.getCause());
        assertEquals("AUTHENTICATION_REQUIRED", denial.getError().getErrorCode());
        assertNull(denial.getCause());
    }
}
