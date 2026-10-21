package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;

/** New explicit delegation contract; historical ROLE/078 documents retain their original semantics. */
public final class TaskDelegationSchema {
    private final JsonMapper reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();
    private final JsonSchema schema;
    private final PermissionSchema json;
    private final String manifest;
    public TaskDelegationSchema(PermissionSchema json)throws IOException {
        this.json=json;
        byte[] bytes;
        try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/task-delegation.schema.json"))){bytes=stream.readAllBytes();}
        manifest=DigestUtils.sha256Hex(bytes);
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        for(String name:List.of("Author","Decide","Receipt"))definition(name).initializeValidators();
    }
    public JsonNode request(String type,byte[] bytes)throws IOException{return validate(type,reader.readTree(bytes));}
    public JsonNode read(String text)throws IOException{return reader.readTree(text);}
    public JsonNode validate(String type,JsonNode value){
        if(value==null||!definition(type).validate(value).isEmpty())bad();
        if("Author".equals(type)){
            if(!Instant.parse(value.required("valid_until").asText()).isAfter(Instant.parse(value.required("valid_from").asText()))||new BigDecimal(value.required("maximum_amount").asText()).signum()<=0)bad();
            WireValues.revision(value.required("maximum_tokens").asText());WireValues.revision(value.required("maximum_calls").asText());
            var unique=new HashSet<String>();
            for(var op:value.required("operations")){
                String selector=op.required("selector").asText(),action=op.required("action").asText();
                if(!unique.add(selector+":"+action+":"+op.required("purpose").asText()))bad();
                switch(selector){
                    case "TASK" -> {if(!Set.of("task.read","task.run","artifact.register").contains(action)||!op.required("model_version").isNull())bad(); if("artifact.register".equals(action)){if(op.required("artifact_kinds").size()!=1||!"REPLAY_MANIFEST".equals(op.required("artifact_kinds").get(0).asText()))bad();}else if(!op.required("artifact_kinds").isEmpty())bad();}
                    case "REGISTERED_ARTIFACT" -> {if(!"artifact.read".equals(action)||op.required("artifact_kinds").isEmpty()||!op.required("model_version").isNull())bad();}
                    case "MODEL_CONFIG" -> {if(!"model.send".equals(action)||op.required("model_version").isNull()||!op.required("artifact_kinds").isEmpty())bad();}
                    default -> bad();
                }
            }
        }
        return value.deepCopy();
    }
    public String canonical(Object value)throws IOException{return json.canonical(json.tree(value));}
    public String hash(Object value)throws IOException{return DigestUtils.sha256Hex(canonical(value));}
    public String manifest(){return manifest;}
    static void key(String key){if(key==null||!key.matches("[!-~]{16,128}"))bad();}
    private JsonSchema definition(String name){if(!Set.of("Author","Decide","Receipt").contains(name))bad();return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));}
    private static void bad(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
