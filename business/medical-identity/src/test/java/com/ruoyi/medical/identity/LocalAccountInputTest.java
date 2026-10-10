package com.ruoyi.medical.identity;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalAccountInputTest {
    private Class<?> requireType(String simpleName) throws Exception {
        String name = "com.ruoyi.medical.identity." + simpleName;
        assertNotNull(getClass().getClassLoader().getResource(name.replace('.', '/') + ".class"),
                "系统独立账户必须使用生产输入校验和认证模式");
        return Class.forName(name);
    }

    @Test
    void missingPasswordCannotCreateAnAccount() throws Exception {
        var method = requireType("LocalAccountInputs").getDeclaredMethod("requirePassword", String.class);
        var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, (Object) null));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals("LOCAL_PASSWORD_INVALID", failure.getCause().getMessage());
    }

    @Test
    void actualDatasetCannotBeTruncatedIntoABcryptPassword() throws Exception {
        String actual = Files.readString(Path.of("../../data-design/sample_dataset.json"));
        var method = requireType("LocalAccountInputs").getDeclaredMethod("requirePassword", String.class);
        var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, actual));
        assertEquals("LOCAL_PASSWORD_INVALID", failure.getCause().getMessage());
        assertFalse(failure.getCause().getMessage().contains(actual));
    }

    @Test
    void missingUsernameCannotCreateAnAccount() throws Exception {
        var method = requireType("LocalAccountInputs").getDeclaredMethod("requireName", String.class);
        var failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, (Object) null));
        assertEquals("LOCAL_NAME_INVALID", failure.getCause().getMessage());
    }

    @Test
    void authenticationModeMustBeExplicit() throws Exception {
        var constructor = requireType("IdentityMode").getConstructor(String.class);
        var failure = assertThrows(InvocationTargetException.class, () -> constructor.newInstance((Object) null));
        assertEquals("IDENTITY_MODE_REQUIRED", failure.getCause().getMessage());
    }
}
