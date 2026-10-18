package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.util.Objects;
import org.springframework.stereotype.Component;
@Component public final class FormalAgentDraftDirectorySchema {
    private final JsonSchema schema;
    private final JsonMapper reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public FormalAgentDraftDirectorySchema()throws IOException{
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/formal-agent-draft-directory.schema.json"))){
            schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(in),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        schema.initializeValidators();
    }
    JsonNode request(byte[] raw)throws IOException{
        if(raw==null||raw.length>4096)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return validate("Query",reader.readTree(raw));
    }
    JsonNode validate(String type,JsonNode value){
        if(value==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type)).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return value;
    }
}
