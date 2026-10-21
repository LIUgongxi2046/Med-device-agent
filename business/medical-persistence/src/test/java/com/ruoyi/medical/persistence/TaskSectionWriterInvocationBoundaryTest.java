package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TaskSectionWriterInvocationBoundaryTest {
 private static final ObjectMapper JSON=new ObjectMapper();
 private final String invocation=UUID.randomUUID().toString(),context=UUID.randomUUID().toString(),model=UUID.randomUUID().toString();
 private com.fasterxml.jackson.databind.node.ObjectNode result(){var n=JSON.createObjectNode().put("invocation_id",invocation).put("context_manifest_id",context).put("model_config_revision",model);n.putObject("reason").put("kind","stop");n.putArray("blocks").addObject().put("type","text").put("text","{\"section_id\":\"candidate\"}");return n;}
 @Test void writerTerminalIsClosedAndCannotBeConstructedFromJson(){for(var constructor:TaskSectionWriterInvocationService.Terminal.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));}
 @Test void physicalContextCannotBeConstructedFromReferencesOrJson(){for(var constructor:TaskSectionWriterContextReads.Reads.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));}
 @Test void exactOriginalModelIdentityIsRequired(){var n=result();assertDoesNotThrow(()->TaskSectionWriterInvocationService.requireTerminalResult(n,invocation,context,model));assertThrows(TenantAccessException.class,()->TaskSectionWriterInvocationService.requireTerminalResult(n,UUID.randomUUID().toString(),context,model));assertThrows(TenantAccessException.class,()->TaskSectionWriterInvocationService.requireTerminalResult(n,invocation,UUID.randomUUID().toString(),model));assertThrows(TenantAccessException.class,()->TaskSectionWriterInvocationService.requireTerminalResult(n,invocation,context,UUID.randomUUID().toString()));}
 @Test void toolCallsAndMultipleBlocksCannotBecomeASectionDraft(){var n=result();((com.fasterxml.jackson.databind.node.ObjectNode)n.required("reason")).put("kind","tool-calls");assertThrows(TenantAccessException.class,()->TaskSectionWriterInvocationService.requireTerminalResult(n,invocation,context,model));var second=result();((com.fasterxml.jackson.databind.node.ArrayNode)second.required("blocks")).addObject().put("type","text").put("text","extra");var multiple=second;assertThrows(TenantAccessException.class,()->TaskSectionWriterInvocationService.requireTerminalResult(multiple,invocation,context,model));}
 @Test void actionNamespaceDoesNotAliasResearchOrPlanner(){assertNotEquals(TaskInvocationService.actionId(context),TaskSectionWriterInvocationService.actionId(context));assertNotEquals(TaskPlanDraftInvocationService.actionId(context),TaskSectionWriterInvocationService.actionId(context));assertEquals(TaskSectionWriterInvocationService.actionId(context),TaskSectionWriterInvocationService.actionId(context));}
 @Test void writerSchemaHasOwnIdentity()throws Exception{var schema=new TaskSectionWriterInvocationSchema();assertNotEquals(new TaskInvocationContextSchema().sha256(),schema.sha256());assertNotEquals(new TaskPlanDraftInvocationSchema().sha256(),schema.sha256());assertTrue(TaskSectionWriterInvocationSchema.EXACT_INPUT_REF.contains("section-writer"));}
}
