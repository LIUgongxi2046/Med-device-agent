package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class InvitationResponseCipher {
    private final SecretKeySpec key;
    private final String keyId;
    private final PermissionSchema json;
    private final InvitationSchema schema;
    private final SecureRandom random = new SecureRandom();
    public InvitationResponseCipher(@Value("${medical.invitation.response-key-id}") String keyId,
            @Value("${medical.invitation.response-key}") String keyHex, PermissionSchema json, InvitationSchema schema) {
        WireValues.uuid(keyId);
        if (keyHex == null || !keyHex.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("INVITATION_RESPONSE_KEY_INVALID");
        byte[] bytes = HexFormat.of().parseHex(keyHex);
        try { key = new SecretKeySpec(bytes, "AES"); } finally { Arrays.fill(bytes, (byte) 0); }
        this.keyId = keyId; this.json = json; this.schema = schema;
    }

    String encrypt(JsonNode value, JsonNode aad) throws GeneralSecurityException, IOException {
        byte[] nonce = new byte[12]; random.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(json.canonical(aad).getBytes(StandardCharsets.UTF_8));
        byte[] plaintext = json.canonical(value).getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext;
        try { ciphertext = cipher.doFinal(plaintext); } finally { Arrays.fill(plaintext, (byte) 0); }
        var result = json.object(); result.put("schema_version", "1.0.0"); result.put("purpose", "INVITATION_RESPONSE");
        result.put("algorithm", "AES-256-GCM"); result.put("key_id", keyId);
        result.put("nonce", Base64.getUrlEncoder().withoutPadding().encodeToString(nonce));
        result.put("ciphertext", Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext));
        schema.validate("Encrypted", result); return json.canonical(result);
    }

    JsonNode decrypt(String text, JsonNode aad) throws GeneralSecurityException, IOException {
        JsonNode value = schema.encrypted(text);
        if (!keyId.equals(value.required("key_id").textValue())) throw new IllegalStateException("INVITATION_RESPONSE_KEY_UNAVAILABLE");
        byte[] nonce = Base64.getUrlDecoder().decode(value.required("nonce").textValue());
        byte[] ciphertext = Base64.getUrlDecoder().decode(value.required("ciphertext").textValue());
        if (nonce.length != 12 || ciphertext.length < 16
                || !Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext).equals(value.required("ciphertext").textValue())) {
            throw new IllegalArgumentException("INVITATION_ENCRYPTED_RESPONSE_INVALID");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(json.canonical(aad).getBytes(StandardCharsets.UTF_8));
        byte[] plaintext = cipher.doFinal(ciphertext);
        try { return schema.read(plaintext); } finally { Arrays.fill(plaintext, (byte) 0); }
    }

    String requestHmac(JsonNode request) throws GeneralSecurityException, IOException {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
        mac.update("INVITATION_ACCEPTANCE_REQUEST\u0000".getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(mac.doFinal(json.canonical(request).getBytes(StandardCharsets.UTF_8)));
    }
}
