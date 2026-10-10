package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class TenantSelectionOptionsSchema {
    private final JsonMapper json = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema schema;
    private final SchemaCatalogue shared;

    public TenantSelectionOptionsSchema() throws IOException, NoSuchAlgorithmException {
        ObjectNode definition = (ObjectNode) json.readTree(resource("tenant-selection-options.schema.json"));
        // 成员、编号和版本沿用唯一身份定义，已有操作的Schema及清单保持原始字节。
        definition.set("$defs", json.readTree(resource("identity-session.schema.json")).required("$defs"));
        schema = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(definition,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        shared = SchemaCatalogue.fromClasspath();
    }

    public JsonNode envelope(TenantSelectionOptions options, String requestId) {
        JsonNode value = json.valueToTree(options);
        if (!schema.validate(value).isEmpty()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        WireValues.revision(options.expected_context_revision());
        if ((options.selected_tenant_id() == null) != options.expected_context_revision().equals("0")) {
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        }
        var tenants = new HashSet<String>();
        for (var member : options.memberships()) {
            WireValues.revision(member.revision());
            if (!tenants.add(member.tenant_id()) || member.valid_to() != null
                    && !WireValues.utcTime(member.valid_to()).isAfter(WireValues.utcTime(member.valid_from()))) {
                throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
            }
        }
        JsonNode result = json.valueToTree(new InterfaceProtocol.Envelope("1.0.0", requestId,
                options.expected_context_revision(), value));
        shared.validate("interfaces", result, "Envelope");
        return result;
    }

    private byte[] resource(String path) throws IOException {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(path))) {
            return input.readAllBytes();
        }
    }
}
