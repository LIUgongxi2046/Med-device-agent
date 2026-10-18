package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.ErrorCode;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Pure reconstruction from retained raw SSE, never an admission or a billing proof. */
public final class FormalProviderResultEvidence {
 private final FormalInvocationSchema schema;
 public FormalProviderResultEvidence(FormalInvocationSchema schema){this.schema=schema;}
 private static final class Tool {final int block;final String id,name;final StringBuilder arguments=new StringBuilder();Tool(int block,String id,String name){this.block=block;this.id=id;this.name=name;}}
 public void require(JsonNode approvedBody,JsonNode capture,JsonNode result)throws Exception{
  var metadata=capture.required("metadata");
  if(!metadata.required("capture_complete").asBoolean(false)||!metadata.required("http_status").isIntegralNumber()||metadata.required("http_status").intValue()!=200){
   if(!result.required("blocks").isArray()||!result.required("blocks").isEmpty()||!result.required("usage").isNull()||!Set.of("error","cancelled").contains(text(result.required("reason"),"kind")))bad();return;
  }
  String encoded=text(capture,"body_base64");if(encoded.length()>4L*((16777216L+2)/3))bad();byte[] bytes=Base64.getDecoder().decode(encoded);
  if(bytes.length>16777216||!com.ruoyi.medical.contracts.WireValues.sha256(bytes).equals(text(metadata,"sha256"))||!Integer.toString(bytes.length).equals(text(metadata,"size_bytes")))bad();
  String body=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
  Map<String,JsonNode> definitions=new HashMap<>();var tools=approvedBody.get("tools");
  if(tools!=null&&!tools.isNull()){if(!tools.isArray()||tools.size()>128)bad();for(var t:tools){if(!"function".equals(text(t,"type")))bad();var f=t.required("function");String name=text(f,"name");var parameters=f.required("parameters");if(!parameters.isObject()||definitions.putIfAbsent(name,parameters)!=null)bad();localSchema(parameters,0);}}
  List<JsonNode> chunks=new ArrayList<>();StringBuilder data=new StringBuilder();boolean done=false;int doneCount=0;
  for(String line:body.replace("\r\n","\n").replace('\r','\n').split("\n",-1)){
   if(line.isEmpty()){if(data.length()==0)continue;String event=data.toString();data.setLength(0);if(done)bad();if(event.equals("[DONE]")){done=true;doneCount++;}else {if(chunks.size()>=100000)bad();chunks.add(schema.parse(event));}}
   else {if(!line.startsWith("data:"))bad();if(data.length()>0)data.append('\n');String value=line.substring(5);data.append(value.startsWith(" ")?value.substring(1):value);}
  }
  if(data.length()!=0||!done||doneCount!=1||chunks.isEmpty())bad();
  String response=null,model=null,finish=null;boolean usageSeen=false;int next=0,textIndex=-1;StringBuilder text=new StringBuilder();Map<Integer,Tool> calls=new LinkedHashMap<>();Set<String> ids=new HashSet<>();
  for(var chunk:chunks){
   if(!chunk.isObject()||usageSeen)bad();String responseId=text(chunk,"id"),modelId=text(chunk,"model");
   if(response==null){response=responseId;model=modelId;if(!model.equals(text(approvedBody,"model")))bad();}else if(!response.equals(responseId)||!model.equals(modelId))bad();
   var choices=chunk.required("choices");if(!choices.isArray()||choices.size()>1)bad();
   boolean usage=chunk.hasNonNull("usage");
   if(choices.size()==1){
    if(finish!=null)bad();var choice=choices.get(0);if(!choice.required("index").isIntegralNumber()||!choice.required("index").canConvertToInt()||choice.required("index").intValue()!=0)bad();var delta=choice.required("delta");
    if(!delta.isObject())bad();var names=new HashSet<String>();delta.fieldNames().forEachRemaining(names::add);if(!Set.of("role","content","function_call","refusal","tool_calls","reasoning_content").containsAll(names))bad();
    if(delta.hasNonNull("role")&&!"assistant".equals(text(delta,"role"))||delta.hasNonNull("function_call")||delta.hasNonNull("refusal"))bad();
    if(delta.hasNonNull("reasoning_content")&&(!delta.required("reasoning_content").isTextual()||!delta.required("reasoning_content").textValue().isEmpty()))bad();
    if(delta.hasNonNull("content")){String part=string(delta.required("content"));if(!part.isEmpty()){if(textIndex<0)textIndex=next++;text.append(part);}}
    if(delta.hasNonNull("tool_calls")){var fragments=delta.required("tool_calls");if(!fragments.isArray())bad();for(var fragment:fragments){
     var index=fragment.required("index");if(!index.isIntegralNumber()||!index.canConvertToInt()||index.intValue()<0||fragment.hasNonNull("type")&&!"function".equals(text(fragment,"type")))bad();
     JsonNode function=fragment.get("function");if(function!=null&&!function.isNull()&&!function.isObject())bad();Tool tool=calls.get(index.intValue());
     if(tool==null){if(calls.size()>=128||function==null||function.isNull())bad();String id=text(fragment,"id"),name=text(function,"name");if(!ids.add(id)||!definitions.containsKey(name))bad();tool=new Tool(next++,id,name);calls.put(index.intValue(),tool);}
     if(fragment.hasNonNull("id")&&!tool.id.equals(text(fragment,"id")))bad();
     if(function!=null&&!function.isNull()){if(function.hasNonNull("name")&&!tool.name.equals(text(function,"name")))bad();if(function.hasNonNull("arguments"))tool.arguments.append(string(function.required("arguments")));}
    }}
    if(choice.hasNonNull("finish_reason")){finish=text(choice,"finish_reason");if(!Set.of("stop","length","tool_calls","content_filter","insufficient_system_resource","aborted").contains(finish))bad();}
   }else if(!usage||finish==null)bad();
   if(usage){if(finish==null||!chunk.required("usage").isObject())bad();usageSeen=true;}
  }
  if(usageSeen==result.required("usage").isNull())bad();
  if(finish==null||!Objects.equals(response,nullable(result.required("provider_request_id")))||!Objects.equals(response,nullable(metadata.required("provider_response_id")))||!Objects.equals(model,nullable(metadata.required("response_model")))||!finish.equals(text(metadata,"provider_finish_reason")))bad();
  if("tool_calls".equals(finish)&&calls.isEmpty()||!calls.isEmpty()&&!"tool_calls".equals(finish))bad();
  SortedMap<Integer,JsonNode> blocks=new TreeMap<>();if(textIndex>=0)blocks.put(textIndex,JsonNodeFactory.instance.objectNode().put("type","text").put("text",text.toString()));
  for(Tool tool:calls.values()){
   JsonNode arguments=schema.parse(tool.arguments.toString());if(!arguments.isObject())bad();
   var validator=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(definitions.get(tool.name),SchemaValidatorsConfig.builder().formatAssertionsEnabled(false).build());validator.initializeValidators();if(!validator.validate(arguments).isEmpty())bad();
   blocks.put(tool.block,JsonNodeFactory.instance.objectNode().put("type","tool-call").put("id",tool.id).put("name",tool.name).put("arguments",tool.arguments.toString()));
  }
  ArrayNode expected=JsonNodeFactory.instance.arrayNode();blocks.values().forEach(expected::add);if(!expected.equals(result.required("blocks")))bad();
  String kind=switch(finish){case "stop"->"stop";case "length"->"max-tokens";case "tool_calls"->"tool-calls";default->"error";};var reason=result.required("reason");if(!kind.equals(text(reason,"kind")))bad();
  if(!kind.equals("error")){if(!reason.required("failure").isNull())bad();}
  else {var failure=reason.required("failure");if(!"MODEL_PROVIDER_TERMINATED".equals(text(failure,"code"))||!"供应商终止了当前生成".equals(text(failure,"message"))||!response.equals(text(failure,"provider_request_id"))||!failure.required("provider_status").isNull())bad();}
 }
 /** Never fetch external schema references. Approved local JSON pointers remain usable. */
 private static void localSchema(JsonNode n,int depth){if(depth>64)bad();if(n.isObject()){n.fields().forEachRemaining(e->{if(Set.of("$ref","$dynamicRef","$recursiveRef").contains(e.getKey())&&(!e.getValue().isTextual()||!e.getValue().textValue().startsWith("#")))bad();if(e.getKey().equals("$id"))bad();localSchema(e.getValue(),depth+1);});}else if(n.isArray())for(var child:n)localSchema(child,depth+1);}
 private static String nullable(JsonNode n){return n.isNull()?null:string(n);}private static String text(JsonNode n,String k){String v=string(n.required(k));if(v.isEmpty())bad();return v;}private static String string(JsonNode n){if(!n.isTextual())bad();return n.textValue();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
