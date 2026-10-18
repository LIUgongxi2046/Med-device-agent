package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.Objects;

/** Separate extension leaves original registration schema bytes/request identity unchanged. */
final class SimulationInputArtifactLookupSchema {
    private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(com.fasterxml.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final SimulationInputArtifactSchema original=new SimulationInputArtifactSchema();
    private final JsonSchema schema;
    SimulationInputArtifactLookupSchema()throws Exception {
        try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/simulation-input-artifact-lookup.schema.json"))){
            schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(stream),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        definition("Request").initializeValidators();definition("Result").initializeValidators();
    }
    JsonNode request(byte[] bytes)throws Exception {
        if(bytes==null||bytes.length==0||bytes.length>131072)invalid();
        try{return validateRequest(parser.readTree(bytes));}
        catch(com.fasterxml.jackson.core.JsonProcessingException|java.nio.charset.CharacterCodingException failure){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    }
    JsonNode validateRequest(JsonNode value)throws Exception {
        validate("Request",value);original.validate("Request",value.required("original_request"));
        if(ContentReadAuthorization.canonicalBytes(value.required("original_request")).length>65536)invalid();
        SimulationInputArtifactSchema.key(value.required("original_key").textValue());return value;
    }
    JsonNode result(JsonNode receipt)throws Exception {
        var result=parser.createObjectNode().put("schema_version","1.0.0").put("status",receipt==null?"NOT_FOUND":"FOUND");
        if(receipt==null)result.putNull("receipt");else{original.validate("Receipt",receipt);result.set("receipt",receipt);}
        validate("Result",result);return result;
    }
    private void validate(String type,JsonNode value)throws Exception {
        if(value==null||!definition(type).validate(value).isEmpty())invalid();SimulationInvocationSchema.numbers(value);
    }
    private JsonSchema definition(String type){return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type));}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
