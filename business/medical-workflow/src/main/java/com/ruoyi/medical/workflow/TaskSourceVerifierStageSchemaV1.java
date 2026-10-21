package com.ruoyi.medical.workflow;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.*;import java.nio.charset.*;import java.util.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskSourceVerifierStageProtocolV1.*;
/** Closed additive private wire. All nullable operation slots are explicit, never aliases. */
public final class TaskSourceVerifierStageSchemaV1 {
    public static final String ROUTE="/internal/medical/task-run/source-verifier-stage/v1";
    private final JsonMapper json=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    public record Request(String schemaVersion,String operation,Reference reference,CommandV1 command,DispatchedV1 original,ReviewCompletedV1 review){
        public Request{Objects.requireNonNull(reference);if(!"1.0.0".equals(schemaVersion)||reference.executionKind()!=ExecutionKind.TASK||!Set.of("PREPARE","INVENTORY","DISPATCH","RECOVER","COMMIT").contains(operation))invalid();
            boolean cmd=Set.of("DISPATCH","RECOVER").contains(operation),commit="COMMIT".equals(operation);if(cmd!=(command!=null)||commit!=(original!=null)||commit!=(review!=null))invalid();if(command!=null)command.require(reference);if(review!=null)review.require(reference,original);}
    }
    public Request read(byte[] bytes)throws Exception{return json.treeToValue(parse(bytes),Request.class);}
    public byte[] request(Request r)throws Exception{byte[] bytes=json.writeValueAsBytes(r);read(bytes);return bytes;}
    public JsonNode response(Request r,Object value){check(r,value);var n=json.createObjectNode().put("schema_version","1.0.0").put("operation",r.operation()).put("run_id",r.reference().runId());n.set("result",json.valueToTree(value));return n;}
    public Object response(byte[] bytes,Request r)throws Exception{var n=parse(bytes);var names=new HashSet<String>();n.fieldNames().forEachRemaining(names::add);if(!names.equals(Set.of("schema_version","operation","run_id","result"))||!"1.0.0".equals(n.required("schema_version").textValue())||!r.operation().equals(n.required("operation").textValue())||!r.reference().runId().equals(n.required("run_id").textValue()))invalid();Class<?> type=switch(r.operation()){case "PREPARE","INVENTORY"->InventoryV1.class;case "DISPATCH"->DispatchedV1.class;case "RECOVER"->RecoveryV1.class;case "COMMIT"->CommitV1.class;default->throw new IllegalArgumentException();};var result=json.treeToValue(n.required("result"),type);check(r,result);return result;}
    private static void check(Request r,Object value){switch(r.operation()){
        case "PREPARE","INVENTORY"->{if(!(value instanceof InventoryV1 i)||!i.runId().equals(r.reference().runId()))invalid();}
        case "DISPATCH"->{if(!(value instanceof DispatchedV1 d)||!d.command().equals(r.command()))invalid();}
        case "RECOVER"->{if(!(value instanceof RecoveryV1))invalid();((RecoveryV1)value).require(r.reference(),r.command());}
        case "COMMIT"->{if(!(value instanceof CommitV1 c)||!c.original().equals(r.original())||!c.review().equals(r.review()))invalid();((CommitV1)value).require(r.reference());}
        default->invalid();}}
    private JsonNode parse(byte[] bytes)throws Exception{return json.readTree(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());}
    private static void invalid(){throw new IllegalArgumentException("TASK_SOURCE_VERIFIER_STAGE_SCHEMA_INVALID");}
}
