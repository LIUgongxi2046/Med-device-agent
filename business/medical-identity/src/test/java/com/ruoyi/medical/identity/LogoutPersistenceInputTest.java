package com.ruoyi.medical.identity;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import static org.junit.jupiter.api.Assertions.*;

class LogoutPersistenceInputTest {
    private Class<?> requireType(String simpleName) throws Exception {
        String name = "com.ruoyi.medical.identity." + simpleName;
        assertNotNull(getClass().getClassLoader().getResource(name.replace('.', '/') + ".class"),
                "持久注销必须使用经过验证的生产身份及批准配置");
        return Class.forName(name);
    }

    @Test
    void persistenceRequiresConfiguredActorAndRecoveryParameters() throws Exception {
        var constructor = requireType("LogoutPersistencePolicy")
                .getConstructor(String.class, Integer.class, Duration.class);
        var failure = assertThrows(InvocationTargetException.class, () -> constructor.newInstance(null, null, null));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals("LOGOUT_PERSISTENCE_CONFIGURATION_REQUIRED", failure.getCause().getMessage());
    }

    @Test
    void persistentLogoutIdentityRejectsMissingTokenBeforeProviderAccess() throws Exception {
        var method = requireType("VerifiedLogoutToken").getDeclaredMethod("verify", String.class, BackchannelLogoutVerifier.class);
        var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, null, null));
        var denial = assertInstanceOf(OAuth2AuthenticationException.class, failure.getCause());
        assertEquals("AUTHENTICATION_FAILED", denial.getError().getErrorCode());
        assertNull(denial.getCause());
    }

    @Test
    void actualDatasetCannotEstablishPersistentLogoutIdentity() throws Exception {
        String dataset = Files.readString(Path.of(System.getProperty("medical.projectRoot")).resolve("data-design/sample_dataset.json"));
        var method = requireType("VerifiedLogoutToken").getDeclaredMethod("verify", String.class, BackchannelLogoutVerifier.class);
        var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, dataset, null));
        assertInstanceOf(ParseException.class, failure.getCause());
        assertFalse(failure.getCause().getMessage().contains(dataset));
    }
}
