package com.ruoyi.medical.identity;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;

public final class RunTokenSigningKeyStore {
    private static final JsonMapper JSON=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final ProtectedConfigurationFile configuration;
    private final RunTokenPolicyStore policies;
    private final JsonSchema schema;
    public RunTokenSigningKeyStore(Path directory,UserPrincipal owner,long maximumConfigurationBytes,RunTokenPolicyStore policies)
            throws IOException,GeneralSecurityException {
        this.policies=Objects.requireNonNull(policies);
        configuration=new ProtectedConfigurationFile(directory,owner,"run-token-signing-key.json",maximumConfigurationBytes);
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/run-token-signing-key.schema.json"))) {
            schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(JSON.readTree(input.readAllBytes()),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        schema.initializeValidators(); read();
    }
    public Current read() throws IOException,GeneralSecurityException {
        var policy=policies.read(); byte[] bytes=configuration.read(); var value=JSON.readTree(bytes);
        if(value==null || !schema.validate(value).isEmpty()) invalid("RUN_TOKEN_SIGNING_SCHEMA_REQUIRED");
        if(!policy.revision().equals(value.required("policy_revision").textValue())
                || !policy.contentSha256().equals(value.required("policy_sha256").textValue())) invalid("RUN_TOKEN_SIGNING_POLICY_REQUIRED");
        String encoded=value.required("private_key_der_base64").textValue(); byte[] der=Base64.getDecoder().decode(encoded);
        if(!Base64.getEncoder().encodeToString(der).equals(encoded)) invalid("RUN_TOKEN_SIGNING_BASE64_REQUIRED");
        var factory=KeyFactory.getInstance("RSA"); var generated=factory.generatePrivate(new PKCS8EncodedKeySpec(der));
        if(!(generated instanceof RSAPrivateCrtKey key)) throw new SecurityException("RUN_TOKEN_SIGNING_RSA_CRT_REQUIRED");
        if(!"RSA".equals(key.getAlgorithm()) || !"PKCS#8".equals(key.getFormat())
                || key.getModulus().bitLength()<2048 || !Arrays.equals(der,key.getEncoded())) invalid("RUN_TOKEN_SIGNING_PRIVATE_DER_REQUIRED");
        var publicKey=factory.generatePublic(new RSAPublicKeySpec(key.getModulus(),key.getPublicExponent()));
        if(!Arrays.equals(publicKey.getEncoded(),policy.policy().publicKey().getEncoded())) invalid("RUN_TOKEN_SIGNING_KEY_BINDING_REQUIRED");
        var after=policies.read();
        if(!policy.revision().equals(after.revision()) || !policy.contentSha256().equals(after.contentSha256())
                || !Arrays.equals(bytes,configuration.read())) invalid("RUN_TOKEN_SIGNING_CONFIGURATION_CHANGED");
        return new Current(value.required("revision").textValue(),DigestUtils.sha256Hex(bytes),policy,key);
    }
    public String sign(JsonNode claims) throws IOException,GeneralSecurityException,ParseException,JOSEException {
        var current=read(); String body=JSON.writeValueAsString(Objects.requireNonNull(claims));
        if(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>current.policy.policy().maximumBytes()) invalid("RUN_TOKEN_INVALID");
        // 签名前校验独立JSON快照，调用方后续修改不能改变已经核查的声明。
        RunTokenSignature.requireClaims(JSON.readTree(body),current.policy.policy());
        var header=new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("medical-run+jwt"))
            .keyID(current.policy.policy().keyId()).build();
        var object=new JWSObject(header,new Payload(body));
        int signatureBytes=Math.addExact(current.key.getModulus().bitLength(),7)/8;
        int signatureLength=com.nimbusds.jose.util.Base64URL.encode(new byte[signatureBytes]).toString().length();
        if((long)object.getSigningInput().length+1+signatureLength>current.policy.policy().maximumBytes()) invalid("RUN_TOKEN_INVALID");
        object.sign(new RSASSASigner(current.key));
        String token=object.serialize(); var verified=policies.verify(token); var after=read();
        if(!current.revision.equals(after.revision) || !current.contentSha256.equals(after.contentSha256)
                || !current.policy.revision().equals(after.policy.revision())
                || !current.policy.contentSha256().equals(after.policy.contentSha256())) invalid("RUN_TOKEN_SIGNING_CONFIGURATION_CHANGED");
        if(!java.time.Instant.now().isBefore(verified.expiresAt())) invalid("RUN_TOKEN_INVALID"); return token;
    }
    public static final class Current {
        private final String revision;
        private final String contentSha256;
        private final RunTokenPolicyStore.Current policy;
        private final RSAPrivateCrtKey key;
        private Current(String revision,String contentSha256,RunTokenPolicyStore.Current policy,RSAPrivateCrtKey key) {
            this.revision=revision; this.contentSha256=contentSha256; this.policy=policy; this.key=key;
        }
        public String revision() { return revision; }
        public String contentSha256() { return contentSha256; }
        public String policyRevision() { return policy.revision(); }
        public String policySha256() { return policy.contentSha256(); }
        public String keyId() { return policy.policy().keyId(); }
        RSAPrivateCrtKey signingKey() { return key; }
        @Override public String toString() { return "CurrentRunTokenSigningKey"; }
    }
    private static void invalid(String code) { throw new SecurityException(code); }
}
