package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Short-lived authenticated continuation shared by replicas using the explicitly configured dedicated key. */
@Component
public final class ConfigurationDiscoveryCursor {
    private static final byte[] DOMAIN = "medical-configuration-discovery-v1".getBytes(StandardCharsets.UTF_8);
    private final ConfigurationDiscoveryPolicy policy;
    private final long lifetime;
    private final SecretKeySpec key;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final JsonMapper reader = JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    @org.springframework.beans.factory.annotation.Autowired
    public ConfigurationDiscoveryCursor(ConfigurationDiscoveryPolicy policy) { this(policy, Clock.systemUTC()); }
    ConfigurationDiscoveryCursor(byte[] key, Clock clock) {
        this(new ConfigurationDiscoveryPolicy("00000000-0000-4000-8000-000000009999",
                Base64.getUrlEncoder().withoutPadding().encodeToString(key), Duration.ofMinutes(30)), clock);
    }
    ConfigurationDiscoveryCursor(ConfigurationDiscoveryPolicy policy, Clock clock) {
        this.policy = policy; this.clock = clock;
        key = policy.cursorKey() == null ? null : new SecretKeySpec(Base64.getUrlDecoder().decode(policy.cursorKey()), "AES");
        lifetime = policy.cursorLifetime() == null ? 0 : policy.cursorLifetime().toMillis();
    }

    String next(String after, String binding) {
        policy.requireConfigured(); WireValues.uuid(after);
        try {
            var value = reader.createObjectNode(); value.put("after", after); value.put("binding", binding);
            value.put("expires", Math.addExact(clock.millis(), lifetime));
            byte[] iv = new byte[12]; random.nextBytes(iv); var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv)); cipher.updateAAD(DOMAIN); cipher.updateAAD(policy.cursorKeyId().getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(reader.writeValueAsBytes(value));
            byte[] result = Arrays.copyOf(iv, iv.length + encrypted.length); System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (Exception failure) { throw new IllegalStateException("DISCOVERY_CURSOR_UNAVAILABLE", failure); }
    }

    String after(String token, String binding) {
        policy.requireConfigured(); if (token == null) return null;
        try {
            if (token.isEmpty() || token.length() > 2048 || !token.matches("[A-Za-z0-9_-]+")) invalid();
            byte[] bytes = Base64.getUrlDecoder().decode(token);
            if (bytes.length < 29 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token)) invalid();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(bytes, 12))); cipher.updateAAD(DOMAIN); cipher.updateAAD(policy.cursorKeyId().getBytes(StandardCharsets.UTF_8));
            var value = reader.readTree(cipher.doFinal(bytes, 12, bytes.length - 12));
            if (!value.isObject() || value.size() != 3 || !value.path("after").isTextual() || !value.path("binding").isTextual()
                    || !value.path("expires").isIntegralNumber() || !value.path("expires").canConvertToLong()
                    || !binding.equals(value.required("binding").textValue())
                    || value.required("expires").longValue() <= clock.millis()
                    || value.required("expires").longValue() > Math.addExact(clock.millis(), lifetime)) invalid();
            String after = value.required("after").textValue(); WireValues.uuid(after); return after;
        } catch (Exception failure) { invalid(); return null; }
    }
    private static void invalid() { throw new TenantAccessException(ErrorCode.CURSOR_INVALID); }
}
