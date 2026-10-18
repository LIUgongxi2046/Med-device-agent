package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.*;import com.ruoyi.medical.contracts.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import java.nio.charset.StandardCharsets;import java.util.*;
class FormalProviderResultEvidenceTest {
 FormalInvocationSchema schema;FormalProviderResultEvidence verifier;ObjectNode approved,capture,result,meta;String raw;
 @BeforeEach void setup()throws Exception {schema=new FormalInvocationSchema();verifier=new FormalProviderResultEvidence(schema);approved=(ObjectNode)read("approved-provider-body.json");capture=(ObjectNode)read("actual-sdk-capture.json");result=(ObjectNode)read("actual-sdk-model-result.json");meta=(ObjectNode)capture.required("metadata");raw=new String(Base64.getDecoder().decode(capture.required("body_base64").textValue()),StandardCharsets.UTF_8);}
 com.fasterxml.jackson.databind.JsonNode read(String n)throws Exception{try(var in=getClass().getResourceAsStream("/fixtures/provider-capture/"+n)){return schema.parse(new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8));}}
 void body(String value)throws Exception{raw=value;byte[] bytes=value.getBytes(StandardCharsets.UTF_8);capture.put("body_base64",Base64.getEncoder().encodeToString(bytes));meta.put("sha256",WireValues.sha256(bytes)).put("size_bytes",""+bytes.length).put("observed_bytes",""+bytes.length);}
 void verify()throws Exception{verifier.require(approved,capture,result);}
 @Test void actualSdkOriginalReconstructs()throws Exception{verify();}
 @Test void forgedTextRejected(){((ObjectNode)result.required("blocks").get(0)).put("text","forged");assertThrows(TenantAccessException.class,this::verify);}
 @Test void omittedBlockRejected(){result.putArray("blocks");assertThrows(TenantAccessException.class,this::verify);}
 @Test void missingDoneRejected()throws Exception{body(raw.replace("data: [DONE]",""));assertThrows(TenantAccessException.class,this::verify);}
 @Test void duplicateDoneRejected()throws Exception{body(raw+"data: [DONE]\n\n");assertThrows(TenantAccessException.class,this::verify);}
 @Test void chunkAfterDoneRejected()throws Exception{body(raw+"data: {}\n\n");assertThrows(TenantAccessException.class,this::verify);}
 @Test void duplicateUsageRejected()throws Exception{var chunks=raw.split("\n\n");String usage=Arrays.stream(chunks).filter(c->c.matches("(?s).*\"usage\"\\s*:\\s*\\{.*")).findFirst().orElseThrow();body(raw.replace("data: [DONE]",usage+"\n\ndata: [DONE]"));assertThrows(TenantAccessException.class,this::verify);}
 @Test void missingIdentityRejected()throws Exception{body(raw.replaceAll("\"id\"\\s*:\\s*\"actual-response-id\",?",""));assertThrows(RuntimeException.class,this::verify);}
 @Test void incompleteCannotClaimPersistentText(){meta.put("capture_complete",false);assertThrows(TenantAccessException.class,this::verify);}
 @Test void incompleteEmptyFailureAccepted()throws Exception{meta.put("capture_complete",false);result.putArray("blocks");result.putNull("usage");((ObjectNode)result.required("reason")).put("kind","error");verify();}

 @Test void usageBeforeTerminalRejected()throws Exception{String[] events=raw.split("\\n\\n");String usage=Arrays.stream(events).filter(c->c.matches("(?s).*\"usage\"\\s*:\\s*\\{.*")).findFirst().orElseThrow();body(usage+"\n\n"+raw);assertThrows(TenantAccessException.class,this::verify);}
 @Test void candidateOverflowCannotWrapToZero()throws Exception{tool();body(raw.replace("\"index\":0,\"delta\"","\"index\":4294967296,\"delta\""));assertThrows(TenantAccessException.class,this::verify);}
 @Test void toolStopMismatchRejected()throws Exception{tool();body(raw.replace("\"finish_reason\":\"tool_calls\"","\"finish_reason\":\"stop\""));meta.put("provider_finish_reason","stop");((ObjectNode)result.required("reason")).put("kind","stop");assertThrows(TenantAccessException.class,this::verify);}

 @Test void missingApprovedToolRejected()throws Exception{tool();approved.remove("tools");assertThrows(TenantAccessException.class,this::verify);}
 @Test void missingToolIdRejected()throws Exception{tool();body(raw.replace("\"id\":\"call-real\",",""));assertThrows(RuntimeException.class,this::verify);}
 @Test void duplicateCandidateRejected()throws Exception{tool();var event=schema.parse(raw.substring(6,raw.indexOf("\n\n")));var choices=(ArrayNode)event.required("choices");choices.add(choices.get(0).deepCopy());body("data: "+schema.canonical(event)+"\n\ndata: [DONE]\n\n");assertThrows(TenantAccessException.class,this::verify);}
 @Test void unknownDeltaFieldRejected()throws Exception{tool();body(raw.replace("\"delta\":{","\"delta\":{\"private_extension\":\"x\","));assertThrows(TenantAccessException.class,this::verify);}
 @Test void originalToolArgumentWhitespacePreserved()throws Exception{tool();body(raw.replace("{\\\"term\\\":\\\"x\\\"}","{ \\\"term\\\": \\\"x\\\" }"));((ObjectNode)result.required("blocks").get(0)).put("arguments","{ \"term\": \"x\" }");verify();}
 void tool()throws Exception{
  approved.putArray("tools").add(schema.parse("{\"type\":\"function\",\"function\":{\"name\":\"lookup\",\"parameters\":{\"type\":\"object\",\"properties\":{\"term\":{\"type\":\"string\"}},\"required\":[\"term\"],\"additionalProperties\":false}}}"));
  String id="actual-response-id",model="deepseek-chat";
  body("data: {\"id\":\""+id+"\",\"model\":\""+model+"\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-real\",\"type\":\"function\",\"function\":{\"name\":\"lookup\",\"arguments\":\"{\\\"term\\\":\\\"x\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\ndata: [DONE]\n\n");
  meta.put("provider_finish_reason","tool_calls");((ObjectNode)result.required("reason")).put("kind","tool-calls");result.putNull("usage");result.putArray("blocks").addObject().put("type","tool-call").put("id","call-real").put("name","lookup").put("arguments","{\"term\":\"x\"}");
 }
 @Test void approvedToolOriginalAccepted()throws Exception{tool();verify();}
 @Test void forgedToolNameRejected()throws Exception{tool();((ObjectNode)result.required("blocks").get(0)).put("name","other");assertThrows(TenantAccessException.class,this::verify);}
 @Test void forgedToolIdRejected()throws Exception{tool();((ObjectNode)result.required("blocks").get(0)).put("id","other");assertThrows(TenantAccessException.class,this::verify);}
 @Test void forgedArgumentsRejected()throws Exception{tool();((ObjectNode)result.required("blocks").get(0)).put("arguments","{}");assertThrows(TenantAccessException.class,this::verify);}
 @Test void schemaInvalidArgsRejected()throws Exception{tool();body(raw.replace("\\\"term\\\":\\\"x\\\"","\\\"bad\\\":\\\"x\\\""));((ObjectNode)result.required("blocks").get(0)).put("arguments","{\"bad\":\"x\"}");assertThrows(TenantAccessException.class,this::verify);}
 @Test void remoteToolRefRejectedBeforeResolution()throws Exception{tool();((ObjectNode)approved.required("tools").get(0).required("function").required("parameters")).put("$ref","https://example.invalid/schema");assertThrows(TenantAccessException.class,this::verify);}
 @Test void choicesAfterTerminalRejected()throws Exception{tool();String event=raw.substring(0,raw.indexOf("\n\n"));body(raw.replace("data: [DONE]",event+"\n\ndata: [DONE]"));assertThrows(TenantAccessException.class,this::verify);}
}
