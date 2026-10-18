package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.Objects;

/** Separate additive API: published fixture and ExactInput catalogue bytes remain unchanged. */
final class SimulationInputArtifactSchema {
    private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(com.fasterxml.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;
    private final String manifest;
    SimulationInputArtifactSchema()throws Exception {
        try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/simulation-input-artifact.schema.json"))){
            byte[] bytes=stream.readAllBytes();manifest=WireValues.sha256(bytes);
            schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        definition("Request").initializeValidators();definition("Receipt").initializeValidators();
    }
    String manifest(){return manifest;}
    static void key(String key){if(key==null||!key.matches("[A-Za-z0-9._:-]{16,128}"))invalid();}
    String requestHash(String caseId,JsonNode request,String context,String session)throws Exception {
        var identity=parser.createObjectNode().put("route","/api/simulation-fixtures/"+caseId+"/input-artifact")
            .put("context_revision",context).put("session_id",session).put("workflow_schema_sha256",manifest);
        identity.set("body",request);return WireValues.sha256(ContentReadAuthorization.canonicalBytes(identity));
    }
    String canonicalRequest(String caseId,JsonNode request)throws Exception {
        WireValues.uuid(caseId);validate("Request",request);
        var identity=parser.createObjectNode().put("case_id",caseId);identity.set("request",request);
        return new String(ContentReadAuthorization.canonicalBytes(identity),java.nio.charset.StandardCharsets.UTF_8);
    }
    String bodyHash(String caseId,JsonNode request)throws Exception {
        return WireValues.sha256(canonicalRequest(caseId,request).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    JsonNode storedRequest(String canonical,String hash)throws Exception {
        JsonNode stored=parser.readTree(canonical);
        if(stored==null||!stored.isObject()||stored.size()!=2||!stored.has("case_id")||!stored.has("request"))
            throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        String caseId=stored.required("case_id").textValue();
        if(!bodyHash(caseId,stored.required("request")).equals(hash))
            throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        return stored;
    }
    JsonNode receipt(String value)throws Exception{return validate("Receipt",parser.readTree(value));}
    JsonNode request(byte[] bytes)throws Exception {
        if(bytes==null||bytes.length==0||bytes.length>65536)invalid();
        try{return validate("Request",parser.readTree(bytes));}
        catch(com.fasterxml.jackson.core.JsonProcessingException | java.nio.charset.CharacterCodingException failure){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    }
    JsonNode validate(String type,JsonNode value)throws java.io.IOException {
        if(value==null||!definition(type).validate(value).isEmpty())invalid();SimulationInvocationSchema.numbers(value);return value;
    }
    private JsonSchema definition(String name){return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
