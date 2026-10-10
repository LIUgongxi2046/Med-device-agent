package com.ruoyi.medical.identity;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class IdentityBindingSchema {
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema binding;
    private final SchemaCatalogue shared;

    public IdentityBindingSchema() throws IOException, NoSuchAlgorithmException {
        shared = SchemaCatalogue.fromClasspath();
        binding = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(json.readTree(resource("identity-binding.schema.json")),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        binding.initializeValidators();
        JsonNode manifest = json.readTree(resource("identity-binding-manifest.json"));
        var validator = binding.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append("Manifest"));
        if (!validator.validate(manifest).isEmpty()) throw new IllegalStateException("IDENTITY_BINDING_MANIFEST_INVALID");
        Set<String> paths = new HashSet<>();
        for (JsonNode entry : manifest.required("schemas")) {
            String path = entry.required("path").textValue();
            if (!paths.add(path) || !DigestUtils.sha256Hex(resource(path)).equals(entry.required("sha256").textValue())) {
                throw new IllegalStateException("IDENTITY_BINDING_MANIFEST_INVALID");
            }
        }
        if (!paths.equals(Set.of("identity-binding.schema.json", "contracts/catalogue.json"))) {
            throw new IllegalStateException("IDENTITY_BINDING_MANIFEST_INVALID");
        }
    }

    public JsonNode envelope(AuthenticatedIdentityBinding identity, String requestId) {
        JsonNode value = json.valueToTree(Objects.requireNonNull(identity).view());
        if (!binding.validate(value).isEmpty()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        JsonNode result = json.valueToTree(new InterfaceProtocol.Envelope("1.0.0", requestId, null, value));
        shared.validate("interfaces", result, "Envelope");
        return result;
    }

    public AuthenticatedIdentityBinding.View readBinding(byte[] bytes) throws IOException {
        JsonNode value = json.readTree(Objects.requireNonNull(bytes));
        if (value == null || !binding.validate(value).isEmpty()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        // 邀请描述只用于完整身份比较，不能建立认证主体。
        return json.treeToValue(value, AuthenticatedIdentityBinding.View.class);
    }

    private byte[] resource(String path) throws IOException {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(path))) {
            return input.readAllBytes();
        }
    }
}
