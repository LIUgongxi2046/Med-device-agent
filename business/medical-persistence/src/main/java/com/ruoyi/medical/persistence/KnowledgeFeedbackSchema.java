package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Additive B30 contract. Endpoints, not a caller-supplied discriminator, select the validator. */
@Component
public final class KnowledgeFeedbackSchema {
    private final JsonMapper reader=JsonMapper.builder()
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;
    private final String manifest;
    public KnowledgeFeedbackSchema()throws Exception {
        byte[] bytes;
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/knowledge-rule-feedback.schema.json"))){bytes=in.readAllBytes();}
        manifest=WireValues.sha256(bytes);
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
    }
    JsonNode request(String definition,byte[] bytes)throws Exception {
        if(bytes==null||bytes.length>32768)invalid();
        var value=reader.readTree(bytes);validate(definition,value);return value;
    }
    void validate(String definition,JsonNode value){
        if(value==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())invalid();
    }
    JsonNode read(String value)throws Exception{return reader.readTree(value);}
    String canonical(JsonNode value)throws Exception{return new String(ContentReadAuthorization.canonicalBytes(value),java.nio.charset.StandardCharsets.UTF_8);}
    String hash(JsonNode value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
    String manifest(){return manifest;}
    static String text(JsonNode n,String k){return n.required(k).textValue();}
    static String nullable(JsonNode n,String k){return n.required(k).isNull()?null:text(n,k);}
    static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
