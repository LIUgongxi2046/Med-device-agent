package com.ruoyi.medical.identity;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.nimbusds.jose.JOSEException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;

public final class RunTokenPolicyStore {
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final ProtectedConfigurationFile configuration;
    private final JsonSchema schema;

    public RunTokenPolicyStore(Path directory, UserPrincipal owner, long maximumConfigurationBytes)
            throws IOException, GeneralSecurityException {
        configuration = new ProtectedConfigurationFile(directory, owner, "run-token-policy.json", maximumConfigurationBytes);
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("run-token-policy.schema.json"))) {
            schema = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(JSON.readTree(input),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        schema.initializeValidators(); read();
    }

    public Current read() throws IOException, GeneralSecurityException {
        byte[] bytes = configuration.read();
        var value = JSON.readTree(bytes);
        if (value == null || !schema.validate(value).isEmpty()) throw new SecurityException("RUN_TOKEN_POLICY_SCHEMA_REQUIRED");
        String encoded = value.required("public_key_der_base64").textValue();
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        if (!Base64.getEncoder().encodeToString(keyBytes).equals(encoded)) throw new SecurityException("RUN_TOKEN_POLICY_BASE64_REQUIRED");
        var key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        if (!Arrays.equals(keyBytes, key.getEncoded())) throw new SecurityException("RUN_TOKEN_POLICY_PUBLIC_DER_REQUIRED");
        var policy = new RunTokenSignature.Policy(value.required("issuer").textValue(), value.required("audience").textValue(), key,
            Duration.ofSeconds(value.required("maximum_lifetime_seconds").longValue()), value.required("maximum_token_bytes").intValue());
        return new Current(value.required("revision").textValue(), DigestUtils.sha256Hex(bytes), policy);
    }

    public RunTokenSignature.Verified verify(String raw) throws IOException, GeneralSecurityException, ParseException, JOSEException {
        Current before = read();
        var verified = RunTokenSignature.verify(raw, before.policy());
        Current after = read();
        // 验证结束重新读取当前文件；密钥、期限及任何配置内容变化均拒绝本次身份。
        if (!before.revision().equals(after.revision()) || !before.contentSha256().equals(after.contentSha256())) {
            throw new SecurityException("RUN_TOKEN_POLICY_CHANGED");
        }
        if (!Instant.now().isBefore(verified.expiresAt())) throw new SecurityException("RUN_TOKEN_INVALID");
        return verified;
    }

    public static final class Current {
        private final String revision;
        private final String contentSha256;
        private final RunTokenSignature.Policy policy;
        private Current(String revision, String contentSha256, RunTokenSignature.Policy policy) {
            this.revision = revision; this.contentSha256 = contentSha256; this.policy = policy;
        }
        public String revision() { return revision; }
        public String contentSha256() { return contentSha256; }
        public RunTokenSignature.Policy policy() { return policy; }
        @Override public String toString() { return "CurrentRunTokenPolicy"; }
    }
}
