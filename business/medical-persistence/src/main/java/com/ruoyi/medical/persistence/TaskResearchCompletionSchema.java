package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;

/** Receipt envelope schema; nested authority witnesses remain verified by their actual producers. */
final class TaskResearchCompletionSchema {
    private static final byte[] BYTES=load();
    static final String MANIFEST=digest();
    private static final JsonSchema SCHEMA=parse();
    static void require(JsonNode receipt) {
        if(!SCHEMA.validate(receipt).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
    static void require(String definition,JsonNode value) {
        if(!java.util.Set.of("CommitRequest","CommitReceipt").contains(definition))throw new IllegalArgumentException("UNKNOWN_COMPLETION_SCHEMA");
        if(!SCHEMA.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
    private static String digest(){try{return WireValues.sha256(BYTES);}catch(java.security.NoSuchAlgorithmException e){throw new ExceptionInInitializerError(e);}}
    private static byte[] load(){try(var in=java.util.Objects.requireNonNull(TaskResearchCompletionSchema.class.getResourceAsStream("/task-research-completion.schema.json"))){return in.readAllBytes();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static JsonSchema parse(){try{return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(JsonMapper.builder().build().readTree(BYTES),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());}catch(Exception e){throw new ExceptionInInitializerError(e);}}
}
