package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class DocumentWorkspaceSchema {
    private final JsonSchema schema;
    public DocumentWorkspaceSchema()throws Exception{
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/document-workspace.schema.json"))){
            schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(JsonMapper.builder().build().readTree(input),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        schema.initializeValidators();
    }
    void validate(String name,JsonNode value){if(!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
