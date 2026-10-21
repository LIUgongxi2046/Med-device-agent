package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;

/** Closed wire inputs only; native DTO bean validation and all original permissions still execute. */
@Component
public final class FrameworkBindingNativeSchema {
    public static final int MAX_BYTES=262144;
    public static final String REF="urn:medical:framework-native:1.0.0#/$defs/";
    private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;private final Map<String,String> bodies;private final Map<String,String> parameters;private final String digest;
    public FrameworkBindingNativeSchema()throws Exception {
        byte[] bytes;try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/framework-binding-native.schema.json"))){bytes=stream.readAllBytes();}
        digest=WireValues.sha256(bytes);var root=json.readTree(bytes);var entries=new LinkedHashMap<String,String>();root.required("x-operation-bodies").fields().forEachRemaining(e->entries.put(e.getKey(),e.getValue().asText()));bodies=Map.copyOf(entries);var queries=new LinkedHashMap<String,String>();root.required("x-operation-parameters").fields().forEachRemaining(e->queries.put(e.getKey(),e.getValue().asText()));parameters=Map.copyOf(queries);
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(root,SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();
    }
    public boolean supports(String operationId){return bodies.containsKey(operationId);}
    public boolean supportsParameters(String operationId){return parameters.containsKey(operationId);}
    public void validateParameters(String operationId,Map<String,String[]> input) {
        String definition=parameters.get(operationId);if(definition==null)invalid();var value=json.createObjectNode();
        if(input.size()>64)invalid();for(var entry:input.entrySet()) {
            if(entry.getValue()==null||entry.getValue().length!=1||entry.getValue()[0]==null||entry.getValue()[0].length()>20000)invalid();
            value.put(entry.getKey(),entry.getValue()[0]);
        }
        if(!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())invalid();
    }
    public String requestRef(String operationId){String name=bodies.get(operationId);if(name==null)invalid();return REF+name;}
    public String schemaSha256(){return digest;}
    public JsonNode validateRequest(String operationId,byte[] bytes)throws IOException {
        if(bytes.length>MAX_BYTES)throw new SchemaRejection(ErrorCode.FILE_TOO_LARGE);String definition=bodies.get(operationId);if(definition==null)invalid();
        var value=json.readTree(bytes);bounded(value,0);
        if(value==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())invalid();
        if(operationId.endsWith(".updateSort")) {
            String ids=operationId.contains("SysMenuController")?"menuIds":"deptIds";var left=value.required(ids).asText().split(",");var right=value.required("orderNums").asText().split(",");
            if(left.length!=right.length||left.length>1000||new HashSet<>(List.of(left)).size()!=left.length)invalid();
            for(String id:left)positiveLong(id);for(String order:right){try{if(Integer.parseInt(order)<0)invalid();}catch(NumberFormatException failure){invalid();}}
        }
        if(value.hasNonNull("password")&&value.required("password").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)invalid();
        return value;
    }
    private static void bounded(JsonNode n,int depth){if(n==null||depth>32)invalid();if(n.isContainerNode()){if(n.size()>1000)invalid();for(var item:n)bounded(item,depth+1);}else if(n.isTextual()&&n.asText().length()>65536)invalid();}
    public static long positiveLong(String value){try{long id=Long.parseLong(value);if(id<=0)invalid();return id;}catch(NumberFormatException e){invalid();return 0;}}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
