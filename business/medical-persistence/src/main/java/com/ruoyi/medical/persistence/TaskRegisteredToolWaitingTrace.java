package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import static com.ruoyi.medical.persistence.TaskRegisteredToolAuthority.*;

/** Pinned runtime-bin 0.2.1rc2 native session V4 framing, verified against the
 * byte-identical official 0.2.1-rc.2 writer/codec embedded in that executable.
 * This does not invent a persisted concludesTurn flag: actual exact tool/result,
 * completed turn, empty process unit and successful owned exit are all required. */
final class TaskRegisteredToolWaitingTrace {
    static final int MAX_LOG_BYTES=2*1024*1024,MAX_ARCHIVE_BYTES=4*1024*1024,MAX_EVENTS=20000;
    private static final Set<String> KNOWN=Set.of(
        "agent-preset/selected","agent/inbox/spliced","approval/asked","approval/decided","approval/policy","assistant/attempt","assistant/message",
        "command/done","command/run","compaction/end","compaction/prune","compaction/start","compaction/summary","deliverables/presented","developer/message",
        "feedback/message-delete","feedback/message-put","feedback/record","goal/change","hook/invoked","hook/result","image/offload","job/registered","job/settled",
        "llm/retry","llm/retry-started","model/selection","permission/preset","plan/mode","request/context","request/header","sandbox/mode","schedule/change",
        "session-log-deepseek/delivery-accepted","session/end-seed","session/title","session/title-llm-request","step/end","step/start","subagent/catalog",
        "subagent/descriptor","subagent/model-selection-policy","system/message","team/member","team/message/delivered","team/message/queued","team/task","todo/write",
        "tool-workflow/agent-end","tool-workflow/agent-start","tool-workflow/run-end","tool-workflow/run-start","tool/call","tool/ptc-dispatch","tool/ptc-dispatch-start",
        "tool/result","turn/end","turn/start","user/message","web/deepseek-search-llm-request","working-directory/change","workspace/changes");
    private static final Set<String> BENIGN_AFTER=Set.of();
    private static final com.fasterxml.jackson.databind.json.JsonMapper JSON=com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private TaskRegisteredToolWaitingTrace(){}
    static JsonNode require(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed closure,JsonNode request,JsonNode response)throws Exception {
        session.requireUnchanged();closure.requireSuccessfulExit();same(session.sessionId(),closure.sessionId());same(session.binding(),closure.binding());
        same("0.2.1rc2",text(session.launch().required("sdk"),"runtime_version"));same("0.2.1rc2",text(session.launch().required("sdk"),"sdk_version"));
        byte[] archive=closure.sessionExport();if(archive.length>MAX_ARCHIVE_BYTES)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);var value=JSON.readTree(archive);ProfessionalRuntimeObservations.closed(value,Set.of("files","observation"));
        var observation=value.required("observation");same(session.sessionId(),text(observation,"session_id"));same(session.binding(),observation.required("binding"));same("completed",text(observation,"finish_reason"));
        String workspace=text(session.launch(),"job_directory")+"/workspace",path=path(workspace,session.sessionId());
        var files=value.required("files");if(!files.isArray()||files.size()>256)bad();JsonNode selected=null;var seen=new HashSet<String>();
        for(var file:files){ProfessionalRuntimeObservations.closed(file,Set.of("path","sha256","size_bytes","content_base64"));String p=text(file,"path");if(!seen.add(p)||p.startsWith("/")||Arrays.asList(p.split("/",-1)).contains(".."))bad();if(path.equals(p)){if(selected!=null)bad();selected=file;}}
        if(selected==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        byte[] log=decode(text(selected,"content_base64"));same(Long.toString(log.length),text(selected,"size_bytes"));same(WireValues.sha256(log),text(selected,"sha256"));
        var pins=(ObjectNode)validate(log,session.sessionId(),workspace,request,response);pins.put("path",path).put("content_base64",Base64.getEncoder().encodeToString(log)).put("sha256",WireValues.sha256(log)).put("archive_sha256",WireValues.sha256(archive)).put("archive_content_base64",Base64.getEncoder().encodeToString(archive));
        session.requireUnchanged();closure.requireUnchanged();return pins;
    }
    static void requireOriginal(JsonNode pins,String session,String job,JsonNode binding,JsonNode request,JsonNode response)throws Exception {
        ProfessionalRuntimeObservations.closed(pins,Set.of("format","call_seq","result_seq","turn_end_seq","turn","step","path","content_base64","sha256","archive_sha256","archive_content_base64"));
        String workspace=job+"/workspace";same(path(workspace,session),text(pins,"path"));var bytes=decode(text(pins,"content_base64"));same(WireValues.sha256(bytes),text(pins,"sha256"));WireValues.hash(text(pins,"archive_sha256"));
        byte[] archive=decode(text(pins,"archive_content_base64"),MAX_ARCHIVE_BYTES);same(WireValues.sha256(archive),text(pins,"archive_sha256"));
        var export=JSON.readTree(archive);ProfessionalRuntimeObservations.closed(export,Set.of("files","observation"));same(session,text(export.required("observation"),"session_id"));same(binding,export.required("observation").required("binding"));same("completed",text(export.required("observation"),"finish_reason"));
        var files=export.required("files");if(!files.isArray()||files.size()>256)bad();JsonNode selected=null;var seen=new HashSet<String>();for(var file:files){ProfessionalRuntimeObservations.closed(file,Set.of("path","sha256","size_bytes","content_base64"));String path=text(file,"path");if(!seen.add(path)||path.startsWith("/")||Arrays.asList(path.split("/",-1)).contains(".."))bad();if(path.equals(text(pins,"path")))selected=file;}if(selected==null)bad();same(text(pins,"content_base64"),text(selected,"content_base64"));same(text(pins,"sha256"),text(selected,"sha256"));same(Long.toString(bytes.length),text(selected,"size_bytes"));
        var actual=validate(bytes,session,workspace,request,response);for(var fields=actual.fields();fields.hasNext();){var f=fields.next();same(f.getValue(),pins.required(f.getKey()));}
    }
    static JsonNode validate(byte[] bytes,String session,String workspace,JsonNode request,JsonNode response)throws Exception {
        if(bytes.length<1||bytes.length>MAX_LOG_BYTES||bytes[bytes.length-1]!='\n')bad();
        String source=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        String[] lines=source.split("\n",-1);if(lines.length<5||lines.length>MAX_EVENTS+2||!lines[lines.length-1].isEmpty())bad();var header=JSON.readTree(lines[0]);
        var headerFields=new HashSet<>(Set.of("type","version","id","createdAt","isSeeded","delegationDepth","cwd"));if(header.has("agentPreset"))headerFields.add("agentPreset");ProfessionalRuntimeObservations.closed(header,headerFields);
        same("session",text(header,"type"));same(4L,number(header,"version"));same(session,text(header,"id"));same(workspace,text(header,"cwd"));long previousTime=number(header,"createdAt");if(previousTime>System.currentTimeMillis()+5000)bad();same(0L,number(header,"delegationDepth"));if(!header.required("isSeeded").isBoolean()||header.required("isSeeded").booleanValue())bad();
        String target=text(request,"call_id");long activeTurn=0,activeStep=0,lastStep=0;boolean turnStarted=false;long turn=0,step=0,callSeq=-1,resultSeq=-1,endSeq=-1;boolean stepEnded=false,turnEnded=false;var calls=new LinkedHashMap<String,Long>();var finished=new HashSet<String>();
        for(int index=1;index<lines.length-1;index++){
            if(lines[index].isEmpty())bad();var event=JSON.readTree(lines[index]);var fields=new HashSet<>(Set.of("type","seq","time","data"));for(String optional:List.of("surfaceOp","sourceEventSeqs","ignorable"))if(event.has(optional))fields.add(optional);ProfessionalRuntimeObservations.closed(event,fields);
            long seq=number(event,"seq");same((long)index-1,seq);long timestamp=number(event,"time");if(timestamp<previousTime||timestamp>System.currentTimeMillis()+5000)bad();previousTime=timestamp;String type=text(event,"type");var data=event.required("data");if(!data.isObject())bad();if(event.has("ignorable")&&!event.required("ignorable").equals(JSON.getNodeFactory().booleanNode(true)))bad();
            if(!KNOWN.contains(type)){if(!event.path("ignorable").asBoolean(false)||callSeq>=0)bad();continue;}if(event.has("sourceEventSeqs"))ranges(event.required("sourceEventSeqs"),seq);
            if(callSeq>=0&&!Set.of("tool/result","step/end","turn/end").contains(type)&&!BENIGN_AFTER.contains(type))bad();
            if(turnEnded&&!BENIGN_AFTER.contains(type))bad();
            if("turn/start".equals(type)){if(turnStarted||activeTurn!=0)bad();activeTurn=positive(data,"turn");if(activeTurn!=1)bad();turnStarted=true;}
            else if("step/start".equals(type)){if(activeTurn==0||activeStep!=0)bad();same(activeTurn,positive(data,"turn"));activeStep=positive(data,"step");if(activeStep!=lastStep+1)bad();lastStep=activeStep;}
            else if("tool/call".equals(type)){
                ProfessionalRuntimeObservations.closed(data,Set.of("turn","step","callId","name","arguments"));String id=text(data,"callId");if(activeTurn==0||activeStep==0)bad();same(activeTurn,positive(data,"turn"));same(activeStep,positive(data,"step"));if(calls.put(id,seq)!=null)bad();
                if(target.equals(id)){same("ask_user_question",text(data,"name"));same(JSON.readTree(text(data,"arguments")),request.required("arguments"));turn=positive(data,"turn");step=positive(data,"step");callSeq=seq;}
                else if("ask_user_question".equals(text(data,"name")))bad();
            }else if("tool/result".equals(type)){
                if(activeTurn==0||activeStep==0)bad();same(activeTurn,positive(data,"turn"));same(activeStep,positive(data,"step"));var resultFields=new HashSet<>(Set.of("turn","step","message"));for(String optional:List.of("meta","error"))if(data.has(optional))resultFields.add(optional);ProfessionalRuntimeObservations.closed(data,resultFields);var message=data.required("message");var messageFields=new HashSet<>(Set.of("id","role","source","toolCallId","content"));if(message.has("isError"))messageFields.add("isError");ProfessionalRuntimeObservations.closed(message,messageFields);ProfessionalRuntimeObservations.closed(message.required("source"),Set.of("kind","callId"));WireValues.uuid(text(message,"id"));if(message.has("isError")&&!message.required("isError").isBoolean())bad();String id=text(message,"toolCallId");if(!calls.containsKey(id)||!finished.add(id))bad();same("tool",text(message,"role"));same("tool",text(message.required("source"),"kind"));same(id,text(message.required("source"),"callId"));
                same(List.of(calls.get(id)),ranges(event.required("sourceEventSeqs"),seq));same("append",text(event,"surfaceOp"));
                if(target.equals(id)){if(callSeq<0||resultSeq>=0||stepEnded)bad();same(turn,positive(data,"turn"));same(step,positive(data,"step"));if(data.has("error")||message.path("isError").asBoolean(false))bad();var content=message.required("content");if(!content.isArray()||content.size()!=1)bad();ProfessionalRuntimeObservations.closed(content.get(0),Set.of("type","text"));same("text",text(content.get(0),"type"));same(new String(ContentReadAuthorization.canonicalBytes(response),StandardCharsets.UTF_8),text(content.get(0),"text"));same(response,JSON.readTree(text(content.get(0),"text")));resultSeq=seq;}
            }else if(callSeq>=0&&"step/end".equals(type)){if(resultSeq<0||stepEnded||turnEnded)bad();ProfessionalRuntimeObservations.closed(data,Set.of("turn","step"));same(turn,positive(data,"turn"));same(step,positive(data,"step"));stepEnded=true;activeStep=0;}
            else if(callSeq>=0&&"turn/end".equals(type)){if(!stepEnded||turnEnded)bad();ProfessionalRuntimeObservations.closed(data,Set.of("turn","reason"));same(turn,positive(data,"turn"));ProfessionalRuntimeObservations.closed(data.required("reason"),Set.of("kind"));same("completed",text(data.required("reason"),"kind"));turnEnded=true;endSeq=seq;activeTurn=0;}
            else if("step/end".equals(type)){if(activeTurn==0||activeStep==0)bad();same(activeTurn,positive(data,"turn"));same(activeStep,positive(data,"step"));activeStep=0;}
            else if("turn/end".equals(type))bad();
        }
        if(callSeq<0||resultSeq<0||!stepEnded||!turnEnded||activeTurn!=0||activeStep!=0||!finished.equals(calls.keySet()))bad();return JSON.createObjectNode().put("format","DSH_SESSION_V4_0.2.1rc2").put("call_seq",callSeq).put("result_seq",resultSeq).put("turn_end_seq",endSeq).put("turn",turn).put("step",step);
    }
    static String path(String workspace,String session){WireValues.uuid(session);if(!workspace.startsWith("/"))bad();var readable=new StringBuilder();boolean separator=false;for(char ch:workspace.toCharArray()){if(ch=='/'||ch=='\\'||ch==':'){if(!separator)readable.append('-');separator=true;}else if(ch!='~'&&((ch>='a'&&ch<='z')||(ch>='A'&&ch<='Z')||(ch>='0'&&ch<='9')||"._-".indexOf(ch)>=0)){readable.append(ch);separator=false;}else{readable.append(String.format(Locale.ROOT,"~%04X",(int)ch));separator=false;}}String key=readable.toString().replaceFirst("^-+","");if(key.isEmpty())key="root";return "home/sessions/--"+key.substring(0,Math.min(251,key.length()))+"--/"+session+"/session.v4.jsonl";}
    private static byte[] decode(String value){return decode(value,MAX_LOG_BYTES);}
    private static byte[] decode(String value,int maximum){if(value.length()>((maximum+2)/3)*4)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);byte[] bytes;try{bytes=Base64.getDecoder().decode(value);}catch(IllegalArgumentException e){bad();return null;}if(bytes.length>maximum)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);same(Base64.getEncoder().encodeToString(bytes),value);return bytes;}
    private static List<Long> ranges(JsonNode values,long seq){if(!values.isArray())bad();var out=new ArrayList<Long>();var seen=new HashSet<Long>();boolean range=false;for(var value:values){if(value.isArray()){if(value.size()!=2)bad();long from=count(value.get(0)),to=count(value.get(1));if(from>to||to>=seq||to-from+1>MAX_EVENTS-out.size())bad();for(long n=from;n<=to;n++)out.add(n);range=true;}else out.add(count(value));}for(int i=0;i<out.size();i++){long n=out.get(i);if(n>=seq||!seen.add(n)||range&&i>0&&n<=out.get(i-1))bad();}return out;}
    private static long count(JsonNode n){if(!n.isIntegralNumber()||!n.canConvertToLong()||n.longValue()<0||n.longValue()>9007199254740991L)bad();return n.longValue();}
    private static long number(JsonNode n,String field){return count(n.required(field));}private static long positive(JsonNode n,String field){long v=number(n,field);if(v<1)bad();return v;}
}
