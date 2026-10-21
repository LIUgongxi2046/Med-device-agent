package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;
/** Versioned local runtime schema. Not a public contract replacement or a TASK schema alias. */
public final class FormalEvaluationRuntimeSchema {
    public static final String URI="urn:medical:evaluation-node-runtime:1.0.0";
    private final String manifest;private final JsonSchema schema;
    private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public FormalEvaluationRuntimeSchema()throws Exception {
        byte[] raw;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/formal-evaluation-runtime.schema.json"))){raw=in.readAllBytes();}
        manifest=org.apache.commons.codec.digest.DigestUtils.sha256Hex(raw);
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(raw),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        for(String kind:List.of("STEP_ENVELOPE","SESSION_EXPORT","STEP_RESULT"))definition(kind).initializeValidators();
    }
    public String ref(String kind){return URI+"#/$defs/"+name(kind);}public String manifest(String kind){name(kind);return manifest;}
    public JsonNode read(String kind,byte[] raw)throws Exception {
        if(raw==null||raw.length==0||raw.length>16777216)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var value=parser.readTree(raw);if(!definition(kind).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return value;
    }
    private JsonSchema definition(String kind){return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name(kind)));}
    private static String name(String kind){return switch(kind){case "STEP_ENVELOPE"->"StepEnvelope";case "SESSION_EXPORT"->"SessionExport";case "STEP_RESULT"->"StepResult";default->throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);};}
}
