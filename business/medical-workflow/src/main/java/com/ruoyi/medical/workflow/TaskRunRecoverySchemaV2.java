package com.ruoyi.medical.workflow;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskRunRecoveryProtocolV2.*;
/** Closed V2 wire only; no changes to the V1 decoder or nullable V1 completion. */
public final class TaskRunRecoverySchemaV2 {
    public static final String ROUTE="/internal/medical/task-run/activities/v2/recovery";
    private final TaskRunActivitySchema records=new TaskRunActivitySchema();
    private final JsonMapper json=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final com.networknt.schema.JsonSchema schema;
    public TaskRunRecoverySchemaV2(){try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-run-recovery-v2.schema.json"))){schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(json.readTree(in),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();}catch(Exception e){throw new IllegalStateException("TASK_RECOVERY_V2_CONTRACT_REQUIRED",e);}}
    public record Request(String operation,Reference reference,Dispatch dispatch,String stepId){
        public Request(String operation,Reference reference,Dispatch dispatch){this(operation,reference,dispatch,null);}
        public Request{if(!Set.of("LOAD","RECOVER","FAIL_UNDISPATCHED").contains(operation)||reference==null||reference.executionKind()!=ExecutionKind.TASK||(dispatch!=null)!=operation.equals("RECOVER")||(stepId!=null)!=operation.equals("FAIL_UNDISPATCHED")||dispatch!=null&&!reference.runId().equals(dispatch.runId()))invalid();if(stepId!=null)com.ruoyi.medical.contracts.WireValues.uuid(stepId);}}
    public Request read(byte[] bytes)throws Exception{var n=parse(bytes);if(n==null||!schema.validate(n).isEmpty())invalid();String operation=text(n,"operation");closed(n,switch(operation){case "LOAD"->Set.of("schema_version","operation","reference");case "RECOVER"->Set.of("schema_version","operation","reference","dispatch");case "FAIL_UNDISPATCHED"->Set.of("schema_version","operation","reference","step_id");default->throw new IllegalArgumentException();});if(!"2.0.0".equals(text(n,"schema_version")))invalid();return new Request(operation,records.record(n.required("reference"),Reference.class),operation.equals("RECOVER")?records.record(n.required("dispatch"),Dispatch.class):null,operation.equals("FAIL_UNDISPATCHED")?text(n,"step_id"):null);}
    public byte[] request(Request r)throws Exception{var n=json.createObjectNode().put("schema_version","2.0.0").put("operation",r.operation());n.set("reference",json.valueToTree(r.reference()));if(r.dispatch()!=null)n.set("dispatch",json.valueToTree(r.dispatch()));if(r.stepId()!=null)n.put("step_id",r.stepId());byte[] bytes=json.writeValueAsBytes(n);read(bytes);return bytes;}
    public ObjectNode response(Request request,Object result)throws Exception{validateResponse(request,result);var n=json.createObjectNode().put("schema_version","2.0.0").put("operation",request.operation()).put("run_id",request.reference().runId());n.set("result",json.valueToTree(result));return n;}
    private void validateResponse(Request request,Object result){switch(request.operation()){
        case "LOAD"->{if(!(result instanceof RunPlanV2))invalid();((RunPlanV2)result).require(request.reference());}
        case "RECOVER"->{if(!(result instanceof StepRecoveryV2))invalid();((StepRecoveryV2)result).require(request.dispatch());}
        case "FAIL_UNDISPATCHED"->{if(!(result instanceof UndispatchedFailureV2))invalid();var failure=(UndispatchedFailureV2)result;if(!request.reference().runId().equals(failure.runId())||!request.stepId().equals(failure.stepId()))invalid();}
        default->invalid();}}
    public Object response(byte[] bytes,Request request)throws Exception{var n=parse(bytes);closed(n,Set.of("schema_version","operation","run_id","result"));if(!"2.0.0".equals(text(n,"schema_version"))||!request.operation().equals(text(n,"operation"))||!request.reference().runId().equals(text(n,"run_id")))invalid();
        if(request.operation().equals("LOAD")){var plan=records.record(n.required("result"),RunPlanV2.class);validateResponse(request,plan);return plan;}
        if(request.operation().equals("FAIL_UNDISPATCHED")){var failure=records.record(n.required("result"),UndispatchedFailureV2.class);validateResponse(request,failure);return failure;}
        var value=n.required("result");closed(value,Set.of("schema_version","state","dispatch","receipt","outcome"));Receipt receipt=value.required("receipt").isNull()?null:records.record(value.required("receipt"),Receipt.class);ProfessionalOutcomeV2 outcome=null;
        if(!value.required("outcome").isNull()){var o=value.required("outcome");closed(o,Set.of("schema_version","kind","completion","waiting"));outcome=new ProfessionalOutcomeV2(text(o,"schema_version"),text(o,"kind"),o.required("completion").isNull()?null:records.record(o.required("completion"),Completion.class),o.required("waiting").isNull()?null:records.record(o.required("waiting"),TaskRunWaitingProtocol.WaitingCompletionV2.class));}
        var result=new StepRecoveryV2(text(value,"schema_version"),text(value,"state"),records.record(value.required("dispatch"),Dispatch.class),receipt,outcome);result.require(request.dispatch());return result;
    }
    private JsonNode parse(byte[] bytes)throws Exception{return json.readTree(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());}
    private static String text(JsonNode n,String name){if(n==null||!n.isObject()||!n.required(name).isTextual())invalid();return n.required(name).textValue();}
    private static void closed(JsonNode n,Set<String> fields){if(n==null||!n.isObject()||n.size()!=fields.size())invalid();var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!fields.equals(actual))invalid();}
    private static void invalid(){throw new IllegalArgumentException("TASK_RECOVERY_V2_SCHEMA_INVALID");}
}
