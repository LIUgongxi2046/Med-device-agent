package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;

/** Bounded, detached history data. This class never grants execution or content access.
 * Live callers must obtain Results.OrderedHistory and ToolReceipts.History separately. */
final class TaskInvocationHistoryData {
    static final int MAXIMUM_ROUNDS=256,MAXIMUM_TOOL_CALLS=256;
    private static final Set<String> FIELDS=Set.of("model_results","model_pins","tool_responses","tool_pins");
    private TaskInvocationHistoryData(){}
    static ObjectNode empty(){
        var value=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        for(String field:List.of("model_results","model_pins","tool_responses","tool_pins"))value.putArray(field);
        return value;
    }
    static ObjectNode of(JsonNode results,JsonNode pins,JsonNode responses,JsonNode tools){
        var value=empty();value.set("model_results",results.deepCopy());value.set("model_pins",pins.deepCopy());
        value.set("tool_responses",responses.deepCopy());value.set("tool_pins",tools.deepCopy());return value;
    }
    static JsonNode fromDependencies(JsonNode dependencies,long ordinal){
        JsonNode value=dependencies.has("invocation_history")?dependencies.required("invocation_history"):empty();
        require(value,ordinal);return value.deepCopy();
    }
    static void require(JsonNode value,long ordinal){
        ProfessionalRuntimeObservations.closed(value,FIELDS);
        if(ordinal<1||ordinal>MAXIMUM_ROUNDS+1)limit();
        for(String field:FIELDS)if(!value.required(field).isArray())bad();
        var results=value.required("model_results");var pins=value.required("model_pins");
        var responses=value.required("tool_responses");var tools=value.required("tool_pins");
        if(results.size()!=ordinal-1||pins.size()!=results.size()||responses.size()!=tools.size()
                ||responses.size()>MAXIMUM_TOOL_CALLS)bad();
        var invocations=new HashSet<String>();var contexts=new HashSet<String>();var calls=new HashSet<String>();int at=0;
        for(int round=0;round<results.size();round++){
            var result=results.get(round);var pin=pins.get(round);
            ProfessionalRuntimeObservations.closed(pin,Set.of("invocation_id","accounting_sha256","result_registration_sha256","capture_registration_sha256"));
            String invocation=text(result,"invocation_id"),context=text(result,"context_manifest_id");WireValues.uuid(invocation);WireValues.uuid(context);
            if(!invocations.add(invocation)||!contexts.add(context)||!invocation.equals(text(pin,"invocation_id")))bad();
            for(String field:List.of("accounting_sha256","result_registration_sha256","capture_registration_sha256"))WireValues.hash(text(pin,field));
            if(!"tool-calls".equals(text(result.required("reason"),"kind")))bad();
            int count=0;
            for(var block:result.required("blocks")){
                String type=text(block,"type");
                if("text".equals(type)){text(block,"text");continue;}
                if(!"tool-call".equals(type))bad();
                String id=text(block,"id");if(id.isBlank()||!calls.add(id)||at>=responses.size())bad();
                var response=responses.get(at);var tool=tools.get(at++);
                ProfessionalRuntimeObservations.closed(response,Set.of("invocation_id","tool_call_id","response"));
                ProfessionalRuntimeObservations.closed(tool,Set.of("tool_receipt_id","tool_receipt_sha256","response_sha256","tool_call"));
                var call=tool.required("tool_call");
                ProfessionalRuntimeObservations.closed(call,Set.of("invocation_id","accounting_sha256","result_registration_sha256","capture_registration_sha256","context_manifest_id","tool_call_id","tool_name","arguments_sha256","session_id","supervision_receipt_id","supervision_sha256"));
                if(!invocation.equals(text(response,"invocation_id"))||!id.equals(text(response,"tool_call_id"))
                    ||!invocation.equals(text(call,"invocation_id"))||!id.equals(text(call,"tool_call_id"))
                    ||!context.equals(text(call,"context_manifest_id"))||!text(block,"name").equals(text(call,"tool_name")))bad();
                for(String field:List.of("accounting_sha256","result_registration_sha256","capture_registration_sha256"))
                    if(!text(pin,field).equals(text(call,field)))bad();
                if(!digest(response.required("response")).equals(text(tool,"response_sha256")))bad();
                WireValues.uuid(text(tool,"tool_receipt_id"));WireValues.hash(text(tool,"tool_receipt_sha256"));
                try{
                    var arguments=new com.fasterxml.jackson.databind.ObjectMapper()
                        .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text(block,"arguments"));
                    if(!arguments.isObject()||!digest(arguments).equals(text(call,"arguments_sha256")))bad();
                }catch(TenantAccessException e){throw e;}catch(Exception e){bad();}
                count++;
            }
            if(count==0)bad();
        }
        if(at!=responses.size())bad();
    }
    static ObjectNode prefix(JsonNode value,int rounds){
        if(rounds<0||rounds>value.required("model_results").size())bad();
        var result=empty();int calls=0;
        for(int i=0;i<rounds;i++){
            ((ArrayNode)result.required("model_results")).add(value.required("model_results").get(i));
            ((ArrayNode)result.required("model_pins")).add(value.required("model_pins").get(i));
            for(var block:value.required("model_results").get(i).required("blocks"))if("tool-call".equals(text(block,"type")))calls++;
        }
        for(int i=0;i<calls;i++){((ArrayNode)result.required("tool_responses")).add(value.required("tool_responses").get(i));((ArrayNode)result.required("tool_pins")).add(value.required("tool_pins").get(i));}
        return result;
    }
    static void sameSession(TaskInvocationProtocol.Node n,String actor,TaskInvocationProtocol.Node other,String otherActor){
        if(!actor.equals(otherActor)||!n.tenant_id().equals(other.tenant_id())||!n.project_id().equals(other.project_id())
            ||!n.task_id().equals(other.task_id())||!n.run_id().equals(other.run_id())||!n.step_id().equals(other.step_id())
            ||!n.principal_id().equals(other.principal_id())||!n.host_action_id().equals(other.host_action_id())
            ||!n.session_id().equals(other.session_id())||!n.lease_generation().equals(other.lease_generation())
            ||!n.worker_service_uri().equals(other.worker_service_uri())||!n.node_input_artifact_id().equals(other.node_input_artifact_id())
            ||!n.node_input_sha256().equals(other.node_input_sha256())||!n.node_input_receipt_sha256().equals(other.node_input_receipt_sha256())
            ||!n.supervisor_receipt_id().equals(other.supervisor_receipt_id())||!n.supervisor_receipt_sha256().equals(other.supervisor_receipt_sha256())
            ||!n.classification().equals(other.classification()))bad();
    }
    static String digest(JsonNode value){try{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}catch(Exception e){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}}
    static String text(JsonNode n,String field){var value=n.required(field);if(!value.isTextual())bad();return value.textValue();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void limit(){throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
}
