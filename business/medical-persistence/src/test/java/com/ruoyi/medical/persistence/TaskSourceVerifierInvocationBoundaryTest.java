package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TaskSourceVerifierInvocationBoundaryTest {
 @Test void verifierBindingsCannotAliasWriterProofs(){assertFalse(TaskSectionWriterInvocationRecords.Binding.class.isAssignableFrom(TaskSourceVerifierInvocationRecords.Binding.class));for(var constructor:TaskSourceVerifierInvocationRecords.Binding.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));}
 @Test void verifierProjectionCannotAcceptToolExecution(){var tools=JSON.createArrayNode().addObject().put("name","tool");assertThrows(TenantAccessException.class,()->TaskSourceVerifierInvocationProjection.initial(JSON.createObjectNode(),UUID.randomUUID().toString(),"review",java.util.List.of(),tools));}

 @Test void originalAndStageRecoveryRequireDistinctPrivateProofTypes()throws Exception {
  assertNotNull(TaskSourceVerifierInvocationService.class.getMethod("requireOriginalProvenance",TaskSourceVerifierDispatchJournal.Original.class,com.fasterxml.jackson.databind.JsonNode.class));
  assertNotNull(TaskSourceVerifierInvocationService.class.getMethod("requireStageProvenance",TaskSourceVerifierDispatchJournal.StageFacts.class,com.fasterxml.jackson.databind.JsonNode.class));
  for(var constructor:TaskSourceVerifierDispatchJournal.Original.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
  for(var constructor:TaskSourceVerifierDispatchJournal.StageFacts.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
 }
 private static final ObjectMapper JSON=new ObjectMapper();
 private final String invocation=UUID.randomUUID().toString(),context=UUID.randomUUID().toString(),model=UUID.randomUUID().toString();
 private com.fasterxml.jackson.databind.node.ObjectNode result(){var n=JSON.createObjectNode().put("invocation_id",invocation).put("context_manifest_id",context).put("model_config_revision",model);n.putObject("reason").put("kind","stop");n.putArray("blocks").addObject().put("type","text").put("text","{\"section_id\":\"candidate\"}");return n;}
 @Test void verifierTerminalIsClosedAndCannotBeConstructedFromJson(){for(var constructor:TaskSourceVerifierInvocationService.Terminal.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));}
 @Test void physicalContextCannotBeConstructedFromReferencesOrJson(){for(var constructor:TaskSourceVerifierContextReads.Reads.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));}
 @Test void exactOriginalModelIdentityIsRequired(){var n=result();assertDoesNotThrow(()->TaskSourceVerifierInvocationService.requireTerminalResult(n,invocation,context,model));assertThrows(TenantAccessException.class,()->TaskSourceVerifierInvocationService.requireTerminalResult(n,UUID.randomUUID().toString(),context,model));assertThrows(TenantAccessException.class,()->TaskSourceVerifierInvocationService.requireTerminalResult(n,invocation,UUID.randomUUID().toString(),model));assertThrows(TenantAccessException.class,()->TaskSourceVerifierInvocationService.requireTerminalResult(n,invocation,context,UUID.randomUUID().toString()));}
 @Test void toolCallsAndMultipleBlocksCannotBecomeReviewFindings(){var n=result();((com.fasterxml.jackson.databind.node.ObjectNode)n.required("reason")).put("kind","tool-calls");assertThrows(TenantAccessException.class,()->TaskSourceVerifierInvocationService.requireTerminalResult(n,invocation,context,model));var second=result();((com.fasterxml.jackson.databind.node.ArrayNode)second.required("blocks")).addObject().put("type","text").put("text","extra");var multiple=second;assertThrows(TenantAccessException.class,()->TaskSourceVerifierInvocationService.requireTerminalResult(multiple,invocation,context,model));}
 @Test void actionNamespaceDoesNotAliasResearchOrPlanner(){assertNotEquals(TaskSectionWriterInvocationService.actionId(context),TaskSourceVerifierInvocationService.actionId(context));assertNotEquals(TaskInvocationService.actionId(context),TaskSourceVerifierInvocationService.actionId(context));assertNotEquals(TaskPlanDraftInvocationService.actionId(context),TaskSourceVerifierInvocationService.actionId(context));assertEquals(TaskSourceVerifierInvocationService.actionId(context),TaskSourceVerifierInvocationService.actionId(context));}
 @Test void verifierSchemaHasOwnIdentity()throws Exception{var schema=new TaskSourceVerifierInvocationSchema();assertNotEquals(new TaskSectionWriterInvocationSchema().sha256(),schema.sha256());assertNotEquals(new TaskInvocationContextSchema().sha256(),schema.sha256());assertNotEquals(new TaskPlanDraftInvocationSchema().sha256(),schema.sha256());assertTrue(TaskSourceVerifierInvocationSchema.EXACT_INPUT_REF.contains("source-verifier"));}
}
