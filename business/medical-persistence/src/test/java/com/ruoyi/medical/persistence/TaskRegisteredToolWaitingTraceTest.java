package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Starts from records actually encoded by the hash-pinned official RC.2 codec.
 * Logical input is expressly synthetic test data, not a real runtime acceptance. */
class TaskRegisteredToolWaitingTraceTest {
 final JsonMapper json=JsonMapper.builder().build();final byte[] bytes;final JsonNode expected;
 TaskRegisteredToolWaitingTraceTest()throws Exception{try(var in=getClass().getResourceAsStream("/fixtures/registered-waiting-v4/session.v4.jsonl")){bytes=Objects.requireNonNull(in).readAllBytes();}try(var in=getClass().getResourceAsStream("/fixtures/registered-waiting-v4/expectations.json")){expected=json.readTree(Objects.requireNonNull(in));}}
 String session(){return expected.required("session").textValue();}String workspace(){return expected.required("workspace").textValue();}
 JsonNode validate(byte[] log)throws Exception{return TaskRegisteredToolWaitingTrace.validate(log,session(),workspace(),expected.required("request"),expected.required("response"));}
 List<ObjectNode> records()throws Exception{var out=new ArrayList<ObjectNode>();for(String line:new String(bytes,StandardCharsets.UTF_8).split("\n"))out.add((ObjectNode)json.readTree(line));return out;}
 byte[] encode(List<ObjectNode> records)throws Exception{var text=new StringBuilder();for(var row:records)text.append(json.writeValueAsString(row)).append('\n');return text.toString().getBytes(StandardCharsets.UTF_8);}
 @Test void officialEncodedFixtureHasRealCompletedTurnNotAPersistedConclusionFlag()throws Exception{assertEquals(expected.required("sha256").textValue(),WireValues.sha256(bytes));var pins=validate(bytes);assertEquals(2,pins.required("call_seq").intValue());assertEquals(3,pins.required("result_seq").intValue());assertEquals(5,pins.required("turn_end_seq").intValue());assertFalse(new String(bytes,StandardCharsets.UTF_8).contains("concludesTurn"));}
 @Test void rawIdsWrongArgumentsAndWrongSessionCannotQualify()throws Exception{
  var wrong=(ObjectNode)expected.required("request").deepCopy();wrong.put("call_id","invented");assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingTrace.validate(bytes,session(),workspace(),wrong,expected.required("response")));
  wrong.put("call_id",expected.required("request").required("call_id").textValue());((ObjectNode)wrong.required("arguments")).put("prompt_text","rewritten");assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingTrace.validate(bytes,session(),workspace(),wrong,expected.required("response")));
  assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingTrace.validate(bytes,TaskInvocationFixture.id(999),workspace(),expected.required("request"),expected.required("response")));
 }
 @Test void tornMissingOrFutureClosureFailsClosed()throws Exception{
  assertThrows(TenantAccessException.class,()->validate(Arrays.copyOf(bytes,bytes.length-1)));var missing=records();missing.remove(missing.size()-1);assertThrows(TenantAccessException.class,()->validate(encode(missing)));
  var future=records();future.get(future.size()-1).put("time",System.currentTimeMillis()+60000);assertThrows(TenantAccessException.class,()->validate(encode(future)));
  var blocked=records();((ObjectNode)blocked.get(6).required("data").required("reason")).put("kind","blocked");assertThrows(TenantAccessException.class,()->validate(encode(blocked)));
 }
 @Test void inventedConcludesFlagAndPostAskCallCannotReplaceActualClosure()throws Exception{
  var invented=records();((ObjectNode)invented.get(4).required("data")).put("concludesTurn",true);assertThrows(TenantAccessException.class,()->validate(encode(invented)));
  var later=records();var event=later.get(3).deepCopy();event.put("seq",6).put("time",1792454400006L);((ObjectNode)event.required("data")).put("callId","after-ask");later.add(event);assertThrows(TenantAccessException.class,()->validate(encode(later)));
 }
 @Test void mismatchedResultForwardRangeAndDuplicateJsonKeyAreDenied()throws Exception{
  var wrong=records();((ObjectNode)wrong.get(4).required("data").required("message").required("content").get(0)).put("text","{}");assertThrows(TenantAccessException.class,()->validate(encode(wrong)));
  var range=records();range.get(4).putArray("sourceEventSeqs").addArray().add(2).add(3);assertThrows(TenantAccessException.class,()->validate(encode(range)));
  var duplicate=new String(bytes,StandardCharsets.UTF_8).replaceFirst("\\\"seq\\\":0","\"seq\":0,\"seq\":0");assertThrows(Exception.class,()->validate(duplicate.getBytes(StandardCharsets.UTF_8)));
 }
 @Test void originalArchiveMustActuallyContainThePinnedNativeLog()throws Exception{
  var pins=(ObjectNode)validate(bytes);String logPath=TaskRegisteredToolWaitingTrace.path(workspace(),session());pins.put("path",logPath).put("content_base64",Base64.getEncoder().encodeToString(bytes)).put("sha256",WireValues.sha256(bytes));var binding=json.createObjectNode().put("fixture","binding");var archive=json.createObjectNode();var observation=archive.putObject("observation").put("session_id",session()).put("finish_reason","completed");observation.set("binding",binding);archive.putArray("files").addObject().put("path",logPath).put("sha256",WireValues.sha256(bytes)).put("size_bytes",Long.toString(bytes.length)).put("content_base64",Base64.getEncoder().encodeToString(bytes));var archiveBytes=json.writeValueAsBytes(archive);pins.put("archive_sha256",WireValues.sha256(archiveBytes)).put("archive_content_base64",Base64.getEncoder().encodeToString(archiveBytes));
  assertDoesNotThrow(()->TaskRegisteredToolWaitingTrace.requireOriginal(pins,session(),"/jobs/test",binding,expected.required("request"),expected.required("response")));
  ((ObjectNode)archive.required("files").get(0)).put("path","unrelated.jsonl");archiveBytes=json.writeValueAsBytes(archive);pins.put("archive_sha256",WireValues.sha256(archiveBytes)).put("archive_content_base64",Base64.getEncoder().encodeToString(archiveBytes));assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingTrace.requireOriginal(pins,session(),"/jobs/test",binding,expected.required("request"),expected.required("response")));
 }
}
