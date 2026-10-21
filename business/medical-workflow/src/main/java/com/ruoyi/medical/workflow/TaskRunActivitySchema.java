package com.ruoyi.medical.workflow;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

/** Additive private 1.0.0 transport contract. Wire IDs and hashes are locators, not
 * authority. Parsing is closed at every nested record, with no scalar coercion. */
public final class TaskRunActivitySchema {
    public static final String ROUTE="/internal/medical/task-run/activities";
    public enum Operation {LOAD,DISPATCH,COMMIT,FAILURE,DEPENDENCY_FAILED,CONSUME,CANCELLATION,FINISH}
    private final JsonMapper json=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private final JsonSchema request;
    public TaskRunActivitySchema(){try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-run-activities.schema.json"))){request=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(in),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());request.initializeValidators();}catch(Exception e){throw new IllegalStateException("TASK_ACTIVITY_SCHEMA_REQUIRED",e);}}
    public record Request(Operation operation,Reference reference,JsonNode fields){public Request{Objects.requireNonNull(operation);Objects.requireNonNull(reference);fields=fields.deepCopy();}@Override public JsonNode fields(){return fields.deepCopy();}}
    public Request readRequest(byte[] bytes)throws Exception{return request(parse(bytes));}
    public Request request(JsonNode value)throws Exception{
        if(value==null||!request.validate(value).isEmpty())invalid();var op=Operation.valueOf(value.required("operation").textValue());var ref=record(value.required("reference"),Reference.class);
        if(op==Operation.FINISH&&(value.required("receipts").size()>ref.maximumSteps()||value.required("command_ids").size()>ref.maximumCommands()))invalid();
        return new Request(op,ref,value);
    }
    public ObjectNode request(Operation op,Reference reference,Object... fields)throws Exception{
        if(fields.length%2!=0)invalid();var value=json.createObjectNode().put("schema_version","1.0.0").put("operation",op.name());value.set("reference",json.valueToTree(reference));
        for(int i=0;i<fields.length;i+=2){String name=(String)fields[i];if(value.has(name))invalid();value.set(name,json.valueToTree(fields[i+1]));}request(value);return value;
    }
    public Object execute(Request request,RunActivities activities)throws Exception{
        var r=request.reference();var n=request.fields();return switch(request.operation()){
            case LOAD->activities.load(r);
            case DISPATCH->activities.dispatch(r,text(n,"step_id"),text(n,"command_id"));
            case COMMIT->activities.commit(r,record(n.required("dispatch"),Dispatch.class),record(n.required("completion"),Completion.class));
            case FAILURE->activities.failure(r,record(n.required("dispatch"),Dispatch.class),text(n,"reason_code"));
            case DEPENDENCY_FAILED->activities.dependencyFailed(r,text(n,"step_id"));
            case CONSUME->activities.consume(r,record(n.required("command"),Command.class));
            case CANCELLATION->activities.cancellation(r,text(n,"command_id"),text(n,"reason_code"));
            case FINISH->{var receipts=new ArrayList<Receipt>();for(var receipt:n.required("receipts"))receipts.add(record(receipt,Receipt.class));var commands=new ArrayList<String>();for(var command:n.required("command_ids"))commands.add(command.textValue());yield activities.finish(r,State.valueOf(text(n,"requested")),n.required("reason_code").isNull()?null:text(n,"reason_code"),receipts,commands);}
        };
    }
    public byte[] encode(JsonNode value)throws Exception{return json.writeValueAsBytes(value);}
    public ObjectNode response(Request request,Object result)throws Exception{
        Class<?> type=resultType(request.operation());if(result==null||result.getClass()!=type)invalid();var value=json.createObjectNode().put("schema_version","1.0.0").put("operation",request.operation().name()).put("run_id",request.reference().runId());value.set("result",json.valueToTree(result));record(value.required("result"),type);return value;
    }
    public <T>T readResponse(byte[] bytes,Operation operation,Reference reference,Class<T> type)throws Exception{
        var value=parse(bytes);closed(value,Set.of("schema_version","operation","run_id","result"));
        if(!"1.0.0".equals(text(value,"schema_version"))||!operation.name().equals(text(value,"operation"))||!reference.runId().equals(text(value,"run_id"))||type!=resultType(operation))invalid();
        return record(value.required("result"),type);
    }
    private static Class<?> resultType(Operation operation){return switch(operation){case LOAD->Plan.class;case DISPATCH->Dispatch.class;case COMMIT,FAILURE,DEPENDENCY_FAILED->Receipt.class;case CONSUME->CommandReceipt.class;case CANCELLATION->Termination.class;case FINISH->FinalReceipt.class;};}
    private JsonNode parse(byte[] bytes)throws Exception{String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();return json.readTree(text);}
    public <T>T record(JsonNode node,Class<T> type)throws Exception{structure(node,type);return json.treeToValue(node,type);}
    private static void structure(JsonNode node,Type type){
        if(type instanceof ParameterizedType p){if(p.getRawType()!=List.class||!node.isArray()||node.size()>4096)invalid();for(var item:node)structure(item,p.getActualTypeArguments()[0]);return;}
        if(!(type instanceof Class<?>))invalid();Class<?> c=(Class<?>)type;
        if(node==null)invalid();if(node.isNull()){if(c.isPrimitive()||c.isRecord())invalid();return;}
        if(c.isRecord()){
            var fields=new HashSet<String>();for(var component:c.getRecordComponents())fields.add(snake(component.getName()));closed(node,fields);
            for(var component:c.getRecordComponents()){var v=node.required(snake(component.getName()));
                // Nullable nested completedReceipt is explicitly part of Plan's wire contract.
                if(v.isNull()&&c==Plan.class&&component.getName().equals("completedReceipt"))continue;
                structure(v,component.getGenericType());}return;
        }
        if(c==String.class||c.isEnum()){if(!node.isTextual())invalid();return;}
        if(c==boolean.class){if(!node.isBoolean())invalid();return;}
        if(c==long.class){if(!node.isIntegralNumber()||!node.canConvertToLong())invalid();return;}
        if(c==int.class){if(!node.isIntegralNumber()||!node.canConvertToInt())invalid();return;}invalid();
    }
    private static String snake(String name){return PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE.translate(name);}
    private static void closed(JsonNode n,Set<String> names){if(n==null||!n.isObject()||n.size()!=names.size())invalid();var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!actual.equals(names))invalid();}
    private static String text(JsonNode n,String f){var v=n.required(f);if(!v.isTextual())invalid();return v.textValue();}
    private static void invalid(){throw new IllegalArgumentException("TASK_ACTIVITY_SCHEMA_INVALID");}
}
