package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Written with the code tranche; execution belongs to the deferred integration phase. */
class TaskPlanDraftPackageAuthoringSchemaTest {
 final PermissionSchema json=new PermissionSchema();
 final TaskProfessionalPackageAuthoringSchema task=new TaskProfessionalPackageAuthoringSchema(json),plan=task.planDraft();
 TaskPlanDraftPackageAuthoringSchemaTest()throws Exception{}
 private ObjectNode request(String kind)throws Exception{
  var n=new TaskProfessionalPackageAuthoringSchemaTest().request(kind);var p=n.withObject("package");p.put("node_kind","N01");
  switch(kind){case "PROMPT"->p.put("input_encoding","PLAN_DRAFT_INPUT_CANONICAL_JSON");case "ROLE"->p.put("role_name","Planner");case "PROFILE"->p.put("model_tool_mode","PLAN_DRAFT_N01_V1");default->throw new IllegalArgumentException();}return n;
 }
 @Test void exactN01PackagesUseIndependentPublicationAndRejectTaskRoles()throws Exception{
  for(String kind:List.of("PROMPT","PROFILE","ROLE")){var n=request(kind);assertEquals(n,plan.request(plan.bytes(n)));assertTrue(plan.ref(kind).startsWith(TaskPlanDraftPromptCompiler.URI));assertThrows(SchemaRejection.class,()->task.validateRequest(n));assertThrows(SchemaRejection.class,()->plan.validateRequest(new TaskProfessionalPackageAuthoringSchemaTest().request(kind)));}
 }
 @Test void originalAuthoringProfileAndRouteCannotBeSubstituted()throws Exception{
  String project=TaskProfessionalPackageAuthoringSchemaTest.id(2);var proof=json.object();plan.bindProfile(proof,project);plan.requireProfile(proof,project);assertEquals("PLAN_DRAFT_N01",proof.required("authoring_profile").textValue());assertEquals("/api/projects/"+project+"/task-plan-draft-packages",proof.required("authoring_route").textValue());assertThrows(TenantAccessException.class,()->task.requireProfile(proof,project));assertThrows(TenantAccessException.class,()->task.v2().requireProfile(proof,project));
 }
 @Test void callerCannotSelectProofProfileOrEnableModelTools()throws Exception{
  var injected=request("PROMPT").put("authoring_profile","PLAN_DRAFT_N01");assertThrows(SchemaRejection.class,()->plan.validateRequest(injected));var profile=request("PROFILE");profile.withObject("package").put("model_tool_mode","TASK");assertThrows(SchemaRejection.class,()->plan.validateRequest(profile));
 }
 @Test void frozenV1ContractRemainsDefault()throws Exception{
  assertEquals("4e1904ea23571d016a344ce4ef2212c202e8a4fad078cc7fcd305ec1d9b11e19",task.manifest());assertEquals(TaskProfessionalPackageAuthoringProfile.V1,task.profile());assertEquals("1.0.0",plan.envelope(json.object(),"1").required("schema_version").textValue());
 }
}
