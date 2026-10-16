package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class SourceIndexViewSchema {
    private final PermissionSchema json;
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    public SourceIndexViewSchema(PermissionSchema json) throws Exception {
        this.json=json; shared=SchemaCatalogue.fromClasspath();
        var reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/source-index-view.schema.json"))) {
            schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(input.readAllBytes()),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        for(String name:java.util.List.of("Result","Build","Listing")) definition(name).initializeValidators();
    }
    void require(String name,JsonNode value) {
        if(!definition(name).validate(value).isEmpty()) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }
    JsonNode envelope(JsonNode data,String requestId) {
        require("Listing",data); var result=json.tree(new InterfaceProtocol.Envelope("1.0.0",requestId,null,data));
        shared.validate("interfaces",result,"Envelope"); return result;
    }
    private JsonSchema definition(String name) {
        return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));
    }
}
