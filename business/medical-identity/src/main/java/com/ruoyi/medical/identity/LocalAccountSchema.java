package com.ruoyi.medical.identity;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="medical.identity.mode", havingValue="local")
public final class LocalAccountSchema {
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final BusinessSessionSchema identity;
    private final SchemaCatalogue shared;
    private final com.networknt.schema.JsonSchema ownProfile;
    private final String ownProfileSha256;
    private final SecretKeySpec key;
    private final LocalManagementPolicy policy;
    public LocalAccountSchema(BusinessSessionSchema identity, LocalManagementPolicy policy) throws IOException, GeneralSecurityException {
        this.identity = identity; this.policy = policy; shared = SchemaCatalogue.fromClasspath();
        key = new SecretKeySpec(HexFormat.of().parseHex(policy.hmacKey()), "HmacSHA256");
        var mac = Mac.getInstance("HmacSHA256"); mac.init(key);
        try (var input = java.util.Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("own-profile.schema.json"))) {
            byte[] bytes = input.readAllBytes();
            ownProfileSha256 = org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
            ownProfile = shared.compileApiSchema(json.readTree(bytes));
            ownProfile.initializeValidators();
        }
    }
    Request request(LocalAccountProtocol.Action action, byte[] bytes, String idempotencyKey) throws IOException {
        if (idempotencyKey == null || !idempotencyKey.matches("[!-~]{16,128}")) invalid();
        JsonNode body = json.readTree(bytes);
        if (body == null) invalid();
        if (action == LocalAccountProtocol.Action.OWN_PROFILE) {
            profileDefinition("Request", body);
            if (!LocalAccountInputs.validName(body.required("nickName").textValue())) invalid();
        } else identity.validateDefinition(action.definition, body);
        for (String name : java.util.List.of("username", "display_name")) {
            if (body.has(name) && !LocalAccountInputs.validName(body.required(name).textValue())) invalid();
        }
        for (String name : java.util.List.of("current_password", "password")) {
            if (body.has(name) && !LocalAccountInputs.validPassword(body.required(name).textValue())) invalid();
        }
        if (body.has("expected_revision")) WireValues.revision(body.required("expected_revision").textValue());
        return new Request(body);
    }
    String digest(String path, Request request) throws IOException, GeneralSecurityException {
        var value = json.createObjectNode();
        value.put("method", "POST"); value.put("path", path); value.put("schema_version", "1.0.0"); value.set("body", request.body);
        if (request.body.has("nickName")) value.put("supplemental_schema_sha256", ownProfileSha256);
        var mac = Mac.getInstance("HmacSHA256"); mac.init(key);
        return HexFormat.of().formatHex(mac.doFinal(identity.canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    JsonNode envelope(String definition, Object data, String requestId, String revision) {
        JsonNode body = json.valueToTree(data);
        identity.validateDefinition(definition, body);
        JsonNode value = json.valueToTree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, body));
        shared.validate("interfaces", value, "Envelope");
        if (definition.equals("LocalAccountMutation")) validateMutation(value);
        return value;
    }
    JsonNode readMutation(String value) throws IOException {
        var node = json.readTree(value); validateMutation(node); return node;
    }
    void validateMutation(JsonNode value) {
        shared.validate("interfaces", value, "Envelope"); identity.validateDefinition("LocalAccountMutation", value.required("data"));
        var account = value.required("data").required("account");
        if (!value.required("resource_revision").equals(account.required("revision"))) invalid();
        WireValues.revision(account.required("revision").textValue()); WireValues.revision(account.required("credential_revision").textValue());
    }
    String audit(LocalAccountProtocol.AccountItem account) throws IOException {
        JsonNode value = json.valueToTree(new LocalAccountProtocol.Audit("1.0.0", "LOCAL_ACCOUNT", account.user_id(),
                account.revision(), account.credential_revision(), account.status()));
        identity.validateDefinition("LocalAccountAudit", value); return identity.canonical(value);
    }
    String canonical(JsonNode value) throws IOException { return identity.canonical(value); }
    String manifest() { return identity.manifestSha256(); }
    String manifest(LocalAccountProtocol.Action action) {
        return action == LocalAccountProtocol.Action.OWN_PROFILE
                ? org.apache.commons.codec.digest.DigestUtils.sha256Hex(identity.manifestSha256() + ownProfileSha256) : manifest();
    }
    void profileDefinition(String definition, JsonNode value) {
        if (!ownProfile.getSubSchema(new com.networknt.schema.JsonNodePath(com.networknt.schema.PathType.JSON_POINTER)
                .append("$defs").append(definition)).validate(value).isEmpty()) invalid();
    }
    String keyId() { return policy.hmacKeyId(); }
    static String increment(String value) {
        WireValues.revision(value); String next = new BigInteger(value).add(BigInteger.ONE).toString(); WireValues.revision(next); return next;
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    static final class Request {
        private final JsonNode body;
        private Request(JsonNode body) { this.body = body; }
        String text(String name) { return body.required(name).textValue(); }
        boolean has(String name) { return body.has(name); }
        @Override public String toString() { return "LocalAccountManagementRequest"; }
    }
}
