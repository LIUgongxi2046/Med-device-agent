package com.ruoyi.medical.workflow;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
/** Separate strictly parsed wire, without modifying historical signed schemas. */
public final class TaskRunReuseSchemaV1 {
 public static final String ROUTE="/internal/medical/task-run/reuse-originals/v1";
 private final JsonMapper json=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
 public record Request(String schemaVersion,String operation,WorkflowProtocol.Reference reference){public Request{if(!"1.0.0".equals(schemaVersion)||reference==null||!java.util.Set.of("INVENTORY","ASSEMBLY_PREPARE").contains(operation))throw new IllegalArgumentException("REUSE_REQUEST_INVALID");}}
 public Request request(byte[] bytes)throws java.io.IOException{return json.readValue(bytes,Request.class);}public byte[] request(WorkflowProtocol.Reference ref,String operation)throws java.io.IOException{return json.writeValueAsBytes(new Request("1.0.0",operation,ref));}
 public JsonNode response(TaskRunReuseProtocolV1.InventoryV1 value,WorkflowProtocol.Reference ref){value.require(ref);return json.valueToTree(value);}
 public TaskRunReuseProtocolV1.InventoryV1 response(byte[] bytes,WorkflowProtocol.Reference ref)throws java.io.IOException{var value=json.readValue(bytes,TaskRunReuseProtocolV1.InventoryV1.class);value.require(ref);return value;}
}
