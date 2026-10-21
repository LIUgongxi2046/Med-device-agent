package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;

/** Pure lossless text projection for an explicitly bounded SDK conversation.
 * Inputs here are data. Only Context's private supervisor/package proofs confer authority.
 * Unsupported history is rejected, never truncated or silently re-labelled. */
final class TaskInvocationProjection {
    private TaskInvocationProjection() { }
    enum Purpose { TASK, EVALUATION }
    static JsonNode planDraftInitial(JsonNode observed,String session,String approvedUser,List<String> approvedSystem,JsonNode approvedTools) {return conversation(observed,session,approvedUser,approvedSystem,approvedTools,TaskInvocationHistoryData.empty(),1,Purpose.TASK);}
    static JsonNode initial(JsonNode observed,String session,String approvedUser,List<String> approvedSystem,JsonNode approvedTools) {
        return conversation(observed,session,approvedUser,approvedSystem,approvedTools,TaskInvocationHistoryData.empty(),1);
    }
    static JsonNode conversation(JsonNode observed,String session,String approvedUser,List<String> approvedSystem,JsonNode approvedTools,JsonNode recorded,long ordinal) {
        return conversation(observed,session,approvedUser,approvedSystem,approvedTools,recorded,ordinal,Purpose.TASK);
    }
    static JsonNode conversation(JsonNode observed,String session,String approvedUser,List<String> approvedSystem,JsonNode approvedTools,JsonNode recorded,long ordinal,Purpose purpose) {
        TaskInvocationHistoryData.require(recorded,ordinal);
        if(!"enterprise-model".equals(text(observed,"provider"))||!purpose.name().equals(text(observed.required("parameters"),"purpose")))unavailable();
        var sdkSession=observed.required("session_id");if(!sdkSession.isTextual()||!session.equals(sdkSession.textValue()))bad();
        if(!approvedTools.equals(observed.required("tools")))bad();
        var history=observed.required("tool_history");if(!history.isNull()&&(!history.required("updates").isEmpty()||!history.required("tools").equals(approvedTools)))unavailable();
        var expected=new ArrayList<Map.Entry<String,String>>();for(var system:approvedSystem)expected.add(Map.entry("system",system));expected.add(Map.entry("user",approvedUser));
        var actual=new ArrayList<Map.Entry<String,String>>();var system=observed.required("system");if(!system.isNull())actual.add(Map.entry("system",system.textValue()));
        var ids=new HashSet<String>();
        var sdk=observed.required("messages");int offset=0;
        while(offset<sdk.size()&&Set.of("system","user").contains(text(sdk.get(offset),"role"))){
            var message=sdk.get(offset++);String role=text(message,"role");
            if(message.has("id")&&!ids.add(text(message,"id")))bad();
            if(message.has("toolCallId")||message.has("isError"))bad();
            if(message.has("source")){
                var source=message.required("source");String kind=text(source,"kind");
                if(!("system".equals(role)&&"system-prompt".equals(kind)||"user".equals(role)&&"user".equals(kind)))unavailable();
            }else if(!"user".equals(role)||message.has("id"))unavailable();
            StringBuilder text=new StringBuilder();for(var block:message.required("content")){if(!"text".equals(text(block,"type")))unavailable();text.append(text(block,"text"));}
            actual.add(Map.entry(role,text.toString()));
        }
        if(!expected.equals(actual))bad();
        ObjectNode exact=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();var messages=exact.putArray("messages");
        for(var message:actual)messages.addObject().put("role",message.getKey()).put("content",message.getValue());
        int toolAt=0;
        for(var result:recorded.required("model_results")){
            if(offset>=sdk.size())bad();var assistant=sdk.get(offset++);messageId(assistant,ids);
            if(!"assistant".equals(text(assistant,"role"))||assistant.has("toolCallId")||assistant.has("isError"))bad();
            var source=assistant.required("source");
            if(!"model".equals(text(source,"kind"))||!"enterprise-model".equals(text(source,"provider"))||!text(observed,"model").equals(text(source,"model")))bad();
            var replay=result.required("replay_state_id");
            if(replay.isNull()){if(source.has("replayState"))bad();}
            else{
                var response=source.required("replayState").required("response");
                if(!text(result,"invocation_id").equals(text(response,"invocation_id"))||!replay.textValue().equals(text(response,"replay_state_id")))bad();
            }
            if(!FormalTaskNodeAuthority.sameEvidence(result.required("blocks"),assistant.required("content")))bad();
            var output=messages.addObject().put("role","assistant");var calls=output.putArray("tool_calls");StringBuilder content=new StringBuilder();boolean hasText=false;
            for(var block:result.required("blocks")){
                if("text".equals(text(block,"type"))){content.append(text(block,"text"));hasText=true;}
                else {var call=calls.addObject().put("id",text(block,"id")).put("type","function");call.putObject("function").put("name",text(block,"name")).put("arguments",text(block,"arguments"));}
            }
            if(hasText)output.put("content",content.toString());else output.putNull("content");
            for(var call:calls){
                if(offset>=sdk.size())bad();var tool=sdk.get(offset++);messageId(tool,ids);
                if(!"tool".equals(text(tool,"role"))||!text(call,"id").equals(text(tool,"toolCallId"))||tool.path("isError").asBoolean(false))bad();
                var toolSource=tool.required("source");if(!"tool".equals(text(toolSource,"kind"))||!text(call,"id").equals(text(toolSource,"callId")))bad();
                StringBuilder rendered=new StringBuilder();for(var block:tool.required("content")){if(!"text".equals(text(block,"type")))bad();rendered.append(text(block,"text"));}
                var original=recorded.required("tool_responses").get(toolAt++).required("response");String canonical;
                try{canonical=new String(ContentReadAuthorization.canonicalBytes(original),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){bad();return null;}
                if(!canonical.contentEquals(rendered))bad();
                messages.addObject().put("role","tool").put("tool_call_id",text(call,"id")).put("content",canonical);
            }
        }
        if(offset!=sdk.size())bad();
        var tools=exact.putArray("tools");for(var tool:approvedTools){var target=tools.addObject().put("type","function").putObject("function");target.put("name",text(tool,"name")).put("description",text(tool,"description"));target.set("parameters",tool.required("parameters").deepCopy());}
        return exact;
    }
    private static void messageId(JsonNode message,Set<String> ids){if(message.has("id")&&!ids.add(text(message,"id")))bad();}
    private static String text(JsonNode n,String field){var value=n.required(field);if(!value.isTextual())bad();return value.textValue();}
    private static void unavailable(){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
