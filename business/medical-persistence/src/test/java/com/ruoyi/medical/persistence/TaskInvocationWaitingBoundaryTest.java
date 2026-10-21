package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Additive waiting C07 semantics; existing final-text/ordered-history tests remain unchanged. */
class TaskInvocationWaitingBoundaryTest {
 final FormalInvocationSchema schema=new FormalInvocationSchema();
 final JsonNode args=schema.parse("{\"prompt_text\":\"Which evidence applies?\"}");
 TaskInvocationWaitingBoundaryTest()throws Exception{}
 ObjectNode output(String id,String name)throws Exception{var o=(ObjectNode)schema.parse("{\"reason\":{\"kind\":\"tool-calls\"},\"blocks\":[]}");o.withArray("blocks").addObject().put("type","tool-call").put("id",id).put("name",name).put("arguments",schema.canonical(args));return o;}
 @Test void onlyExactFinalExclusiveAskCanConcludeWaiting()throws Exception{var o=output("ask","ask_user_question");assertDoesNotThrow(()->TaskInvocationService.requireWaitingResult(List.of(o),"ask",args,schema));assertThrows(TenantAccessException.class,()->TaskInvocationService.requireWaitingResult(List.of(o),"caller-invented",args,schema));var changed=(ObjectNode)args.deepCopy();changed.put("prompt_text","different");assertThrows(TenantAccessException.class,()->TaskInvocationService.requireWaitingResult(List.of(o),"ask",changed,schema));}
 @Test void earlierAskConcurrentSiblingsDuplicatesAndStopAreDenied()throws Exception{
  var ask=output("ask","ask_user_question");assertThrows(TenantAccessException.class,()->TaskInvocationService.requireWaitingResult(List.of(ask,output("next","read_artifact")),"ask",args,schema));
  ask.withArray("blocks").add(output("other","read_artifact").required("blocks").get(0));assertThrows(TenantAccessException.class,()->TaskInvocationService.requireWaitingResult(List.of(ask),"ask",args,schema));
  assertThrows(TenantAccessException.class,()->TaskInvocationService.requireWaitingResult(List.of(output("duplicate","read_artifact"),output("duplicate","ask_user_question")),"duplicate",args,schema));
  var stopped=output("ask","ask_user_question");((ObjectNode)stopped.required("reason")).put("kind","stop");assertThrows(TenantAccessException.class,()->TaskInvocationService.requireWaitingResult(List.of(stopped),"ask",args,schema));
 }
 @Test void missingHistoryOrMismatchedActualClosureFailsBeforeAnyResultRead()throws Exception{
  assertThrows(TenantAccessException.class,()->TaskInvocationService.requireWaitingResult(List.of(),"ask",args,schema));
  var service=mock(TaskInvocationService.class,CALLS_REAL_METHODS);var session=mock(TaskProfessionalRuntimeService.Session.class);var closed=mock(TaskProfessionalSupervisor.Closed.class);when(session.state()).thenReturn("OPEN");assertThrows(TenantAccessException.class,()->service.settledWaitingForSession(session,closed,"ask",args));
  when(session.state()).thenReturn("CLOSED");when(session.sessionId()).thenReturn(TaskInvocationFixture.id(1));when(closed.sessionId()).thenReturn(TaskInvocationFixture.id(2));assertThrows(TenantAccessException.class,()->service.settledWaitingForSession(session,closed,"ask",args));
 }
 @Test void rawCallerCannotConstructTerminalOrExclusiveToolProof(){for(Class<?> type:List.of(TaskInvocationService.WaitingTerminal.class,TaskInvocationService.WaitingToolCall.class,TaskRegisteredToolWaitingCompletion.Produced.class,TaskRegisteredToolWaitingCompletion.Original.class))for(var c:type.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(c.getModifiers()));}
}
