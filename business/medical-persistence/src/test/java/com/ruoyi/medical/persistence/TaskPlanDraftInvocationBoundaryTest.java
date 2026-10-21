package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Code-first regression coverage. Full execution is deliberately deferred to integration. */
class TaskPlanDraftInvocationBoundaryTest {
 private final ObjectMapper json=new ObjectMapper();
 @Test void n01LiteralCompilerCannotConsumeN02Input()throws Exception {
  var prompt=json.readTree("{\"schema_version\":\"1.0.0\",\"node_kind\":\"N01\",\"system_instructions\":\"Plan from fixed inputs only\",\"input_encoding\":\"PLAN_DRAFT_INPUT_CANONICAL_JSON\"}");
  var input=json.readTree("{\"node_kind\":\"N02\"}");assertThrows(TenantAccessException.class,()->TaskPlanDraftPromptCompiler.compileDocument(prompt,input));
 }
 @Test void n01LiteralCompilationPreservesExactInputWithoutInventingPlan()throws Exception {
  var prompt=json.readTree("{\"schema_version\":\"1.0.0\",\"node_kind\":\"N01\",\"system_instructions\":\"Plan from fixed inputs only\",\"input_encoding\":\"PLAN_DRAFT_INPUT_CANONICAL_JSON\"}");
  var input=json.readTree("{\"node_kind\":\"N01\",\"execution_kind\":\"SYSTEM_JOB\",\"job_type\":\"PLAN_DRAFT\",\"plan_revision\":null,\"fixed_inputs_json\":\"{}\"}");var result=TaskPlanDraftPromptCompiler.compileDocument(prompt,input);
  assertEquals(input,result.required("plan_draft_input"));assertEquals("N01",result.required("node_kind").textValue());assertTrue(result.required("plan_draft_input").required("plan_revision").isNull());
 }
 @Test void n02RoleCannotBecomePlannerByChangingOnlyNode()throws Exception {
  var role=json.readTree("{\"schema_version\":\"1.0.0\",\"node_kind\":\"N01\",\"role_name\":\"Researcher\",\"prompt_basis\":{},\"profile_artifact\":{}}");assertThrows(TenantAccessException.class,()->TaskPlanDraftPromptCompiler.validateRole(role));
 }
 @Test void allN01ProducerProofsHaveOnlyPrivateConstructors(){
  for(var proof:java.util.List.of(TaskPlanDraftPackagePreflight.Selection.class,TaskPlanDraftPackagePreflight.Verified.class,TaskPlanDraftPackages.Read.class,TaskPlanDraftPromptCompiler.Compiled.class,TaskPlanDraftInvocationContext.Prepared.class,TaskPlanDraftInvocationObjects.Intent.class,TaskPlanDraftInvocationObjects.Attempt.class,TaskPlanDraftInvocationObjects.Registered.class,TaskPlanDraftInvocationRecords.Registered.class,TaskPlanDraftInvocationRecords.Binding.class,TaskPlanDraftInvocationService.Prepared.class,TaskPlanDraftInvocationService.Terminal.class,TaskPlanDraftInvocationService.OriginalTerminal.class))
   for(var constructor:proof.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()),proof.getName());
 }
 @Test void actionsAreDedicatedNotN02Aliases(){String id="00000000-0000-0000-0000-000000000001";assertNotEquals(TaskInvocationService.actionId(id),TaskPlanDraftInvocationService.actionId(id));assertEquals(TaskPlanDraftInvocationService.actionId(id),TaskPlanDraftInvocationService.actionId(id));}
 @Test void packageModelSendAlwaysRequiresPrivateCurrentLease(){
  for(var method:TaskPlanDraftPackages.Read.class.getDeclaredMethods())if(method.getName().equals("requireModelSend"))assertTrue(java.util.Arrays.asList(method.getParameterTypes()).contains(TaskPlanDraftRuntimeAuthority.Verified.class));
  for(var method:TaskPlanDraftPackages.class.getDeclaredMethods())if(method.getName().equals("requireCurrentPins"))assertTrue(java.util.Arrays.asList(method.getParameterTypes()).contains(TaskPlanDraftRuntimeAuthority.Verified.class));
 }
 @Test void planBindingDoesNotMasqueradeAsTask(){assertFalse(java.util.Arrays.stream(TaskPlanDraftInvocationRecords.Binding.class.getDeclaredMethods()).anyMatch(m->m.getName().equals("task")));}
}
