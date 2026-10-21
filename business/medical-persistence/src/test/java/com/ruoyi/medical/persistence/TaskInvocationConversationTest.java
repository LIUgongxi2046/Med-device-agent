package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Projection fixtures are deliberately data, never supervisor/source authorization. */
class TaskInvocationConversationTest {
    final ObjectMapper json=new ObjectMapper();
    final String session=TaskInvocationFixture.id(42),model=TaskInvocationFixture.id(16);
    final ObjectNode observed,history=TaskInvocationHistoryData.empty();
    TaskInvocationConversationTest(){
        observed=json.createObjectNode().put("provider","enterprise-model").put("model","approved").put("session_id",session);
        observed.putNull("system");observed.putArray("tools");observed.putObject("tool_history").set("tools",json.createArrayNode());
        ((ObjectNode)observed.required("tool_history")).putArray("updates");
        observed.putObject("parameters").put("max_output_tokens",50).putNull("temperature").put("reasoning_effort","none").put("purpose","TASK").putArray("stop");
        var user=observed.putArray("messages").addObject().put("id","user").put("role","user");
        user.putObject("source").put("kind","user");user.putArray("content").addObject().put("type","text").put("text","approved input");
    }
    void round(int n,int calls)throws Exception {
        String invocation=TaskInvocationFixture.id(100+n),context=TaskInvocationFixture.id(200+n),replay=TaskInvocationFixture.id(300+n);
        var result=((ArrayNode)history.required("model_results")).addObject().put("schema_version","1.0.0").put("invocation_id",invocation)
            .put("context_manifest_id",context).put("model_config_revision",model).putNull("provider_request_id").putNull("usage").put("replay_state_id",replay);
        result.putObject("reason").put("kind","tool-calls").putNull("failure");var blocks=result.putArray("blocks");
        blocks.addObject().put("type","text").put("text","inspect "+n);
        for(int call=0;call<calls;call++)blocks.addObject().put("type","tool-call").put("id","call-"+n+"-"+call).put("name","read_source").put("arguments","{ \"round\" : "+n+", \"call\" : "+call+" }");
        var modelPin=((ArrayNode)history.required("model_pins")).addObject().put("invocation_id",invocation).put("accounting_sha256","a".repeat(64))
            .put("result_registration_sha256","b".repeat(64)).put("capture_registration_sha256","c".repeat(64));
        var assistant=((ArrayNode)observed.required("messages")).addObject().put("role","assistant").put("id","assistant-"+n);assistant.set("content",blocks.deepCopy());
        assistant.putObject("source").put("kind","model").put("provider","enterprise-model").put("model","approved").putObject("replayState").putObject("response").put("invocation_id",invocation).put("replay_state_id",replay);
        for(int call=0;call<calls;call++){
            String callId="call-"+n+"-"+call;var response=json.createObjectNode().put("kind","TASK_READ_RESULT").put("content","原文 "+n+"-"+call);
            var entry=((ArrayNode)history.required("tool_responses")).addObject().put("invocation_id",invocation).put("tool_call_id",callId);entry.set("response",response);
            var toolPin=((ArrayNode)history.required("tool_pins")).addObject().put("tool_receipt_id",TaskResearchToolReceipts.receiptId(session,callId))
                .put("tool_receipt_sha256","d".repeat(64)).put("response_sha256",TaskInvocationHistoryData.digest(response));
            var source=modelPin.deepCopy().put("context_manifest_id",context).put("tool_call_id",callId).put("tool_name","read_source")
                .put("arguments_sha256",TaskInvocationHistoryData.digest(json.readTree(blocks.get(call+1).required("arguments").textValue())))
                .put("session_id",session).put("supervision_receipt_id",TaskInvocationFixture.id(44)).put("supervision_sha256","e".repeat(64));toolPin.set("tool_call",source);
            var tool=((ArrayNode)observed.required("messages")).addObject().put("id","tool-"+n+"-"+call).put("role","tool").put("toolCallId",callId).put("isError",false);
            tool.putObject("source").put("kind","tool").put("callId",callId);tool.putArray("content").addObject().put("type","text").put("text",new String(ContentReadAuthorization.canonicalBytes(response),StandardCharsets.UTF_8));
        }
    }
    JsonNode project(){return TaskInvocationProjection.conversation(observed,session,"approved input",List.of(),observed.required("tools"),history,history.required("model_results").size()+1L);}
    @Test void initialRequestKeepsExactBytesAndPublishedSchema()throws Exception{
        assertEquals(TaskInvocationProjection.initial(observed,session,"approved input",List.of(),observed.required("tools")),project());
        assertEquals("30378eaad8c98486077b47d995a4da5cd218d57eab20d86d69ddabda1139a01b",new TaskInvocationContextSchema().sha256());
    }
    @Test void successiveActualModelAndToolResultsPreserveOrderAndArgumentBytes()throws Exception{
        round(1,2);round(2,1);var exact=project();var messages=exact.required("messages");assertEquals(6,messages.size());
        assertEquals(List.of("user","assistant","tool","tool","assistant","tool"),java.util.stream.StreamSupport.stream(messages.spliterator(),false).map(m->m.required("role").textValue()).toList());
        assertEquals("{ \"round\" : 1, \"call\" : 0 }",messages.get(1).required("tool_calls").get(0).required("function").required("arguments").textValue());
        var schemas=new TaskInvocationContextSchema();schemas.readProjection(ContentReadAuthorization.canonicalBytes(observed));schemas.readExactInput(ContentReadAuthorization.canonicalBytes(exact));
    }
    @Test void missingInjectedOrReorderedMessagesFailClosed()throws Exception{
        round(1,2);var messages=(ArrayNode)observed.required("messages");var first=messages.get(2);messages.set(2,messages.get(3));messages.set(3,first);assertThrows(TenantAccessException.class,this::project);
        messages.set(3,messages.get(2));messages.set(2,first);messages.remove(3);assertThrows(TenantAccessException.class,this::project);
    }
    @Test void assistantOutputAndOriginalInvocationReplayCannotBeRewritten()throws Exception{
        round(1,1);var assistant=(ObjectNode)observed.required("messages").get(1);
        ((ObjectNode)assistant.required("source").required("replayState").required("response")).put("invocation_id",TaskInvocationFixture.id(999));assertThrows(TenantAccessException.class,this::project);
        ((ObjectNode)assistant.required("source").required("replayState").required("response")).put("invocation_id",TaskInvocationFixture.id(101));
        ((ObjectNode)assistant.required("content").get(0)).put("text","invented output");assertThrows(TenantAccessException.class,this::project);
    }
    @Test void toolTextMustEqualCanonicalAuthenticatedResponse()throws Exception{
        round(1,1);var tool=(ObjectNode)observed.required("messages").get(2);var text=(ObjectNode)tool.required("content").get(0);
        text.put("text",text.required("text").textValue()+" ");assertThrows(TenantAccessException.class,this::project);
        text.put("text",new String(ContentReadAuthorization.canonicalBytes(history.required("tool_responses").get(0).required("response")),StandardCharsets.UTF_8));tool.put("isError",true);assertThrows(TenantAccessException.class,this::project);
    }
    @Test void dynamicToolsAndCompactionRemainUnsupported()throws Exception{
        round(1,1);((ArrayNode)observed.required("tool_history").required("updates")).addObject().put("name","injected");assertThrows(TenantAccessException.class,this::project);
        ((ArrayNode)observed.required("tool_history").required("updates")).removeAll();((ObjectNode)observed.required("parameters")).put("purpose","COMPACTION");assertThrows(TenantAccessException.class,this::project);
    }
    @Test void recordedLaterProjectionNeedsCompleteHistoryAndCanonicalExactInput()throws Exception{
        round(1,1);var schemas=new TaskInvocationContextSchema();byte[] body=ContentReadAuthorization.canonicalBytes(observed),exact=ContentReadAuthorization.canonicalBytes(project());
        var receipt=json.createObjectNode().put("schema_version","1.0.0").put("method","POST").put("route","/v1/model-contexts").put("session_id",session).put("ordinal",2)
            .put("projection_schema_ref",TaskInvocationContextSchema.PROJECTION_REF).put("projection_schema_version","1.0.0").put("projection_schema_sha256",schemas.sha256())
            .put("body_sha256",WireValues.sha256(body)).put("raw_body_sha256",WireValues.sha256(body)).put("raw_size_bytes",body.length).put("body_base64",Base64.getEncoder().encodeToString(body));
        assertDoesNotThrow(()->TaskInvocationOriginals.validateRecordedProjection(body,receipt,session,"approved input",List.of(),exact,schemas,history));
        assertThrows(TenantAccessException.class,()->TaskInvocationOriginals.validateRecordedProjection(body,receipt,session,"approved input",List.of(),exact,schemas));
    }
    @Test void duplicateSdkMessageIdsAreNotSilentlyDiscarded()throws Exception{
        round(1,1);((ObjectNode)observed.required("messages").get(2)).put("id","user");assertThrows(TenantAccessException.class,this::project);
    }
}
