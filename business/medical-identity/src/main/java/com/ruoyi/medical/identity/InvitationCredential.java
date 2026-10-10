package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.apache.commons.codec.digest.DigestUtils;

public final class InvitationCredential {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String secret;
    private final String sha256;

    private InvitationCredential(String secret) {
        this.secret = secret;
        sha256 = DigestUtils.sha256Hex(secret.getBytes(StandardCharsets.US_ASCII));
    }

    public static InvitationCredential issue() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        try {
            return new InvitationCredential(Base64.getUrlEncoder().withoutPadding().encodeToString(value));
        } finally {
            Arrays.fill(value, (byte) 0);
        }
    }

    public static boolean matches(String secret, String expectedSha256) {
        validate(secret);
        if (expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}")) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        // 固定长度的摘要使用标准恒定时间比较，秘密不进入数据库或字符串表示。
        return MessageDigest.isEqual(DigestUtils.sha256Hex(secret.getBytes(StandardCharsets.US_ASCII))
                .getBytes(StandardCharsets.US_ASCII), expectedSha256.getBytes(StandardCharsets.US_ASCII));
    }

    public static void validate(String secret) {
        if (secret == null || !secret.matches("[A-Za-z0-9_-]{43}")) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        byte[] decoded = Base64.getUrlDecoder().decode(secret);
        try {
            if (decoded.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(secret)) {
                throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
            }
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    public String secret() { return secret; }
    public String sha256() { return sha256; }
    @Override public String toString() { return "InvitationCredential"; }
}
