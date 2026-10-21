package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DocumentReviewInvocationBoundaryTest {
 @Test void verifierBindingsCannotAliasWriterProofs(){assertFalse(TaskSourceVerifierInvocationRecords.Binding.class.isAssignableFrom(DocumentReviewInvocationRecords.Binding.class));assertFalse(TaskPlanDraftInvocationRecords.Binding.class.isAssignableFrom(DocumentReviewInvocationRecords.Binding.class));for(var constructor:DocumentReviewInvocationRecords.Binding.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));}
 @Test void verifierProjectionCannotAcceptToolExecution(){var tools=JSON.createArrayNode().addObject().put("name","tool");assertThrows(TenantAccessException.class,()->DocumentReviewInvocationProjection.initial(JSON.createObjectNode(),UUID.randomUUID().toString(),"review",java.util.List.of(),tools));}

 @Test void recoveryReturnsNoLiveCapability()throws Exception {
  var method=DocumentReviewInvocationService.class.getMethod("requireOriginalProvenance",EnterpriseAccess.Admission.class,DocumentReviewExecutionService.Job.class,com.fasterxml.jackson.databind.JsonNode.class);
  assertEquals(void.class,method.getReturnType());
 }
 private static final ObjectMapper JSON=new ObjectMapper();
 private final String invocation=UUID.randomUUID().toString(),context=UUID.randomUUID().toString(),model=UUID.randomUUID().toString();
 private com.fasterxml.jackson.databind.node.ObjectNode result(){var n=JSON.createObjectNode().put("invocation_id",invocation).put("context_manifest_id",context).put("model_config_revision",model);n.putObject("reason").put("kind","stop");n.putArray("blocks").addObject().put("type","text").put("text","{\"section_id\":\"candidate\"}");return n;}
 @Test void verifierTerminalIsClosedAndCannotBeConstructedFromJson(){for(var constructor:DocumentReviewInvocationService.Terminal.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));}
 @Test void physicalContextCannotBeConstructedFromReferencesOrJson(){for(var constructor:DocumentReviewContextReads.Reads.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));}
 @Test void exactOriginalModelIdentityIsRequired(){var n=result();assertDoesNotThrow(()->DocumentReviewInvocationService.requireTerminalResult(n,invocation,context,model));assertThrows(TenantAccessException.class,()->DocumentReviewInvocationService.requireTerminalResult(n,UUID.randomUUID().toString(),context,model));assertThrows(TenantAccessException.class,()->DocumentReviewInvocationService.requireTerminalResult(n,invocation,UUID.randomUUID().toString(),model));assertThrows(TenantAccessException.class,()->DocumentReviewInvocationService.requireTerminalResult(n,invocation,context,UUID.randomUUID().toString()));}
 @Test void toolCallsAndMultipleBlocksCannotBecomeReviewFindings(){var n=result();((com.fasterxml.jackson.databind.node.ObjectNode)n.required("reason")).put("kind","tool-calls");assertThrows(TenantAccessException.class,()->DocumentReviewInvocationService.requireTerminalResult(n,invocation,context,model));var second=result();((com.fasterxml.jackson.databind.node.ArrayNode)second.required("blocks")).addObject().put("type","text").put("text","extra");var multiple=second;assertThrows(TenantAccessException.class,()->DocumentReviewInvocationService.requireTerminalResult(multiple,invocation,context,model));}
 @Test void actionNamespaceDoesNotAliasResearchOrPlanner(){assertNotEquals(TaskSectionWriterInvocationService.actionId(context),DocumentReviewInvocationService.actionId(context));assertNotEquals(TaskInvocationService.actionId(context),DocumentReviewInvocationService.actionId(context));assertNotEquals(TaskPlanDraftInvocationService.actionId(context),DocumentReviewInvocationService.actionId(context));assertEquals(DocumentReviewInvocationService.actionId(context),DocumentReviewInvocationService.actionId(context));}
 @Test void verifierSchemaHasOwnIdentity()throws Exception{var schema=new DocumentReviewInvocationSchema();assertNotEquals(new TaskSectionWriterInvocationSchema().sha256(),schema.sha256());assertNotEquals(new TaskInvocationContextSchema().sha256(),schema.sha256());assertNotEquals(new TaskPlanDraftInvocationSchema().sha256(),schema.sha256());assertTrue(DocumentReviewInvocationSchema.EXACT_INPUT_REF.contains("document-review"));}
}
