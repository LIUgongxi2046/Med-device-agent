package com.ruoyi.medical.identity;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import static org.junit.jupiter.api.Assertions.*;

class BackchannelLogoutTokenTest {
    private Class<?> requireType(String simpleName) throws Exception {
        String name = "com.ruoyi.medical.identity." + simpleName;
        assertNotNull(getClass().getClassLoader().getResource(name.replace('.', '/') + ".class"),
                "Back-Channel Logout 必须使用生产校验入口");
        return Class.forName(name);
    }

    @Test
    void missingPolicyIsRejectedBeforeProviderAccess() throws Exception {
        var constructor = requireType("BackchannelLogoutPolicy")
                .getConstructor(Duration.class, Duration.class, Duration.class, Set.class);
        var failure = assertThrows(InvocationTargetException.class,
                () -> constructor.newInstance(null, null, null, null));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals("LOGOUT_TOKEN_CONFIGURATION_REQUIRED", failure.getCause().getMessage());
    }

    @Test
    void missingTokenIsRejectedBeforeKeyLookup() throws Exception {
        var method = requireType("BackchannelLogoutVerifier").getDeclaredMethod("requireSignedToken", String.class);
        var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, (String) null));
        var denial = assertInstanceOf(OAuth2AuthenticationException.class, failure.getCause());
        assertEquals("AUTHENTICATION_FAILED", denial.getError().getErrorCode());
        assertNull(denial.getCause());
    }

    @Test
    void actualRegisteredDatasetCannotActAsLogoutToken() throws Exception {
        String dataset = Files.readString(Path.of("../../data-design/sample_dataset.json"));
        var method = requireType("BackchannelLogoutVerifier").getDeclaredMethod("requireSignedToken", String.class);
        var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, dataset));
        assertInstanceOf(ParseException.class, failure.getCause());
        assertFalse(failure.getCause().getMessage().contains(dataset));
    }
}
