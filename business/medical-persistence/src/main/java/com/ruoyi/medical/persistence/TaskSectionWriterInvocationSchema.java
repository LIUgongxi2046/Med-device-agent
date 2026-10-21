package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;

/** Closed data formats for the bounded text-only N04 adapter; parsing grants no authority. */
public final class TaskSectionWriterInvocationSchema {
    public static final String URI="urn:medical:task-section-writer-invocation:1.0.0";
    public static final String PROJECTION_REF=URI+"#/$defs/SdkProjection",EXACT_INPUT_REF=URI+"#/$defs/ExactInput";
    public static final int MAXIMUM_BYTES=2*1024*1024;
    private final JsonMapper mapper=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private final JsonSchema schema;private final String digest;
    public TaskSectionWriterInvocationSchema()throws Exception {
        byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-section-writer-invocation.schema.json"))){bytes=in.readAllBytes();}
        digest=WireValues.sha256(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(mapper.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
    }
    public String sha256(){return digest;}
    public JsonNode readProjection(byte[] bytes)throws Exception{return read("SdkProjection",bytes);}
    public JsonNode readExactInput(byte[] bytes)throws Exception{return read("ExactInput",bytes);}
    private JsonNode read(String definition,byte[] bytes)throws Exception {
        if(bytes==null||bytes.length==0||bytes.length>MAXIMUM_BYTES)throw new SchemaRejection(ErrorCode.CONTEXT_LIMIT);
        var value=mapper.readTree(bytes);
        if(!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return value;
    }
}
