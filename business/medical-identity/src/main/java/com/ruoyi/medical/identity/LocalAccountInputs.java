package com.ruoyi.medical.identity;

import java.nio.charset.StandardCharsets;

final class LocalAccountInputs {
    private LocalAccountInputs() { }

    static void requireName(String value) {
        if (!validName(value)) {
            throw new IllegalArgumentException("LOCAL_NAME_INVALID");
        }
    }

    static boolean validName(String value) {
        return value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= 128
                && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }

    static void requirePassword(String value) {
        if (!validPassword(value)) {
            throw new IllegalArgumentException("LOCAL_PASSWORD_INVALID");
        }
    }

    static boolean validPassword(String value) {
        return value != null && value.codePointCount(0, value.length()) >= 15
                && StandardCharsets.UTF_8.newEncoder().canEncode(value)
                && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
