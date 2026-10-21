package com.ruoyi.medical.workflow;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskRunWaitingProtocol.*;

/** No aliasing to the V1 endpoint. Version, operation, full reference and original
 * dispatch are required before the private waiting producer can be consulted. */
public final class TaskRunWaitingSchema {
    public static final String ROUTE="/internal/medical/task-run/activities/v2/waiting";
    private final TaskRunActivitySchema records=new TaskRunActivitySchema();
    private final JsonMapper json=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;
    public TaskRunWaitingSchema(){try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-run-waiting-v2.schema.json"))){schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(in),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();}catch(Exception e){throw new IllegalStateException("WAITING_V2_SCHEMA_REQUIRED",e);}}
    public record Request(Reference reference,Dispatch dispatch,WaitingCompletionV2 completion){public Request{Objects.requireNonNull(reference);Objects.requireNonNull(dispatch);Objects.requireNonNull(completion);}}
    public Request read(byte[] bytes)throws Exception{
        var n=json.readTree(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());
        if(n==null||!schema.validate(n).isEmpty())invalid();var r=records.record(n.required("reference"),Reference.class);var d=records.record(n.required("dispatch"),Dispatch.class);var c=records.record(n.required("completion"),WaitingCompletionV2.class);
        require(r,d,c);return new Request(r,d,c);
    }
    public byte[] request(Reference r,Dispatch d,WaitingCompletionV2 c)throws Exception{
        require(r,d,c);var n=json.createObjectNode().put("schema_version","2.0.0").put("operation","COMMIT_WAITING");n.set("reference",json.valueToTree(r));n.set("dispatch",json.valueToTree(d));n.set("completion",json.valueToTree(c));byte[] result=json.writeValueAsBytes(n);read(result);return result;
    }
    public ObjectNode response(Request r,Receipt receipt){requireReceipt(r,receipt);var n=json.createObjectNode().put("schema_version","2.0.0").put("operation","COMMIT_WAITING").put("run_id",r.reference.runId());n.set("receipt",json.valueToTree(receipt));return n;}
    public Receipt response(byte[] bytes,Request request)throws Exception{
        var n=json.readTree(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());
        var fields=new HashSet<String>();if(n==null||!n.isObject())invalid();n.fieldNames().forEachRemaining(fields::add);if(!fields.equals(Set.of("schema_version","operation","run_id","receipt"))||!n.required("schema_version").isTextual()||!n.required("operation").isTextual()||!n.required("run_id").isTextual()||!"2.0.0".equals(n.required("schema_version").textValue())||!"COMMIT_WAITING".equals(n.required("operation").textValue())||!request.reference.runId().equals(n.required("run_id").textValue()))invalid();
        var receipt=records.record(n.required("receipt"),Receipt.class);requireReceipt(request,receipt);return receipt;
    }
    private static void require(Reference r,Dispatch d,WaitingCompletionV2 c){if(r.executionKind()!=ExecutionKind.TASK||!r.runId().equals(d.runId())||!d.runId().equals(c.runId())||!d.stepId().equals(c.stepId())||!d.inputSha256().equals(c.inputSha256())||d.leaseGeneration()!=c.leaseGeneration())invalid();}
    private static void requireReceipt(Request r,Receipt receipt){if(receipt.status()!=State.WAITING_INPUT||receipt.waitingItemId()==null||!receipt.outputs().isEmpty()||!r.dispatch.stepId().equals(receipt.stepId())||!r.dispatch.inputSha256().equals(receipt.inputSha256())||r.dispatch.leaseGeneration()!=receipt.leaseGeneration())invalid();}
    private static void invalid(){throw new IllegalArgumentException("WAITING_V2_SCHEMA_INVALID");}
}
