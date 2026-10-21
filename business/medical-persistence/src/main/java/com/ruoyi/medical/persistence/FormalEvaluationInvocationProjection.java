package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
/** Lossless bounded EVAL conversation projection; the caller must supply privately verified exact input. */
final class FormalEvaluationInvocationProjection {
 private FormalEvaluationInvocationProjection(){}
 static JsonNode require(JsonNode observed,String session,JsonNode approved,JsonNode parameters){
  return require(observed,session,approved,parameters,TaskInvocationHistoryData.empty(),1);
 }
 static JsonNode require(JsonNode observed,String session,JsonNode approved,JsonNode parameters,JsonNode history,long ordinal){
  if(!observed.required("parameters").equals(parameters))bad();
  var systems=new ArrayList<String>();String user=null;
  for(var m:approved.required("messages")){String role=m.required("role").asText();if("system".equals(role)&&user==null)systems.add(m.required("content").asText());else if("user".equals(role)&&user==null)user=m.required("content").asText();else bad();}
  if(user==null)bad();var tools=new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode();for(var tool:approved.required("tools")){if(!"function".equals(tool.required("type").asText()))bad();tools.add(tool.required("function").deepCopy());}var result=TaskInvocationProjection.conversation(observed,session,user,systems,tools,history,ordinal,TaskInvocationProjection.Purpose.EVALUATION);if(ordinal==1&&!result.equals(approved))bad();return result;
 }
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
