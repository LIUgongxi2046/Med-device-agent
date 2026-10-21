package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskRegisteredToolWaitingAnswerBoundaryTest {
 final PermissionSchema json=new PermissionSchema();TaskRegisteredToolWaitingAnswerBoundaryTest()throws Exception{}
 ObjectNode replay(String task,String plan,String member,String profile){var r=json.object().put("task_revision",task).put("plan_revision",plan);r.putObject("scope_snapshot").putArray("members").add(member);r.putObject("project_profile").put("content_sha256",profile);var c=r.putObject("plan").putObject("content");c.putArray("sections").addObject().put("section_id","fixed-section");return r;}
 ObjectNode cause(String type){var c=json.object();c.putObject("node_input").put("section_id","fixed-section");c.putObject("request").putObject("arguments").put("question_type",type);return c;}
 @Test void proseOrFreshIdsAloneDoNotEstablishAnActualBusinessChange(){var old=replay("old-task","old-plan","same-source","same-profile");var fresh=replay("new-task","new-plan","same-source","same-profile");assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingNewRun.requireChanged(cause("NEED_EVIDENCE"),old,fresh));assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingNewRun.requireChanged(cause("CLARIFY_APPLICABILITY"),old,fresh));}
 @Test void exactQuestionKindRequiresTheCorrespondingRealChangedBindings(){var old=replay("old-task","old-plan","old-source","same-profile");var fresh=replay("new-task","new-plan","new-source","same-profile");assertDoesNotThrow(()->TaskRegisteredToolWaitingNewRun.requireChanged(cause("NEED_EVIDENCE"),old,fresh));assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingNewRun.requireChanged(cause("CLARIFY_APPLICABILITY"),old,fresh));fresh.withObject("project_profile").put("content_sha256","new-profile");assertDoesNotThrow(()->TaskRegisteredToolWaitingNewRun.requireChanged(cause("CLARIFY_APPLICABILITY"),old,fresh));fresh.withObject("plan").withObject("content").putArray("sections");assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingNewRun.requireChanged(cause("NEED_EVIDENCE"),old,fresh));}
 @Test void callerCannotConstructHumanOrOriginalQuestionProof(){for(Class<?> type:new Class<?>[]{TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion.class,TaskRegisteredToolWaitingAnswerAuthority.Original.class,TaskRegisteredToolWaitingCompletion.QuestionOriginal.class})for(var c:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(c.getModifiers()));}
 @Test void schemaRejectsUnknownApprovalResumeAndFabricatedEvaluatorFields()throws Exception{var schema=new TaskRegisteredToolWaitingAnswerSchema();assertThrows(Exception.class,()->schema.read("Request","{\"schema_version\":\"1.0.0\",\"human_checkpoint_approved\":true}".getBytes()));assertThrows(Exception.class,()->schema.read("Recovery","{\"schema_version\":\"1.0.0\",\"original_request\":{},\"resume\":true}".getBytes()));assertThrows(Exception.class,()->schema.read("Request","{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}".getBytes()));}
 @Test void continuationSeparatesCreationAcceptanceAndActualStart()throws Exception {
  String run="10000000-0000-4000-8000-000000000010",temporal="10000000-0000-4000-8000-000000000011";
  var created=TaskRegisteredToolWaitingNewRun.continuationStatus(json,run,null,null);
  assertEquals("CREATED_NOT_STARTED",created.required("status").textValue());
  assertTrue(created.required("accepted_start").isNull());assertFalse(created.required("can_resume_frozen_run").booleanValue());
  var acceptance=json.object().put("run_id",run).put("temporal_run_id",temporal);
  assertEquals("WORKFLOW_ACCEPTED",TaskRegisteredToolWaitingNewRun.continuationStatus(json,run,acceptance,null).required("status").textValue());
  var start=json.object().put("run_id",run).put("temporal_run_id",temporal).put("workflow_acceptance_sha256",TaskRegisteredToolAuthority.hash(acceptance));
  var started=TaskRegisteredToolWaitingNewRun.continuationStatus(json,run,acceptance,start);
  assertEquals("STARTED",started.required("status").textValue());assertFalse(started.required("is_current_run_status").booleanValue());
  assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingNewRun.continuationStatus(json,run,null,start));
  start.put("workflow_acceptance_sha256","0".repeat(64));
  assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingNewRun.continuationStatus(json,run,acceptance,start));
  assertThrows(TenantAccessException.class,()->TaskRegisteredToolWaitingNewRun.continuationStatus(json,temporal,acceptance,null));
 }
}
