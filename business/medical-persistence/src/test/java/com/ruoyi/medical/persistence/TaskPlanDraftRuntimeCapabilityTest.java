package com.ruoyi.medical.persistence;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
/** Structural boundary regressions; integration fixtures must separately exercise real journals. */
class TaskPlanDraftRuntimeCapabilityTest {
 @Test void callersCannotConstructRuntimeOrOutputOrTransitionCapabilities(){
  for(Class<?> type:List.of(TaskPlanDraftRuntimeAuthority.Verified.class,TaskPlanDraftRuntimeAdapter.Envelope.class,
    TaskPlanDraftRuntimeAdapter.Session.class,TaskPlanDraftRuntimeAdapter.Produced.class,TaskPlanDraftWorkerAccess.Action.class,
    TaskPlanDraftLaunchLedger.LaunchTransition.class,TaskPlanDraftCompletionLedger.CompletionTransition.class)){
   assertTrue(Modifier.isFinal(type.getModifiers()),type.getSimpleName());
   for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()),type.getSimpleName());
  }
 }
 @Test void planDraftTransportNeverMatchesTaskRuntimeRoute(){
  assertTrue(TaskPlanDraftRuntimeSecurityConfiguration.ROUTES.stream().allMatch(p->p.startsWith("/internal/v1/plan-draft-runtime/")));
  assertFalse(TaskPlanDraftRuntimeSecurityConfiguration.ROUTES.stream().anyMatch(p->p.startsWith("/internal/v1/professional-runtime/")));
 }
 @Test void hostFailureIsOnlyAClosedObservationShape() throws Exception {
  var json=new com.fasterxml.jackson.databind.ObjectMapper();
  String id="10000000-0000-4000-8000-000000000001";
  var request=json.createObjectNode().put("session_id",id).put("reason","SUPERVISOR_STATE_UNKNOWN").put("host_process_exited",true);
  var binding=request.putObject("binding");for(String key:List.of("tenant_id","run_id","step_id","action_id"))binding.put(key,id);
  binding.put("lease_generation",1).put("input_sha256","a".repeat(64)).put("step_envelope_sha256","b".repeat(64));
  assertDoesNotThrow(()->TaskPlanDraftRuntimeObservations.validate(request,"HOST_FAILURE"));
  // Even a caller claiming real exit may submit only an observation, never a
  // terminal execution state, exit attestation or successful output receipt.
  request.put("execution_status","FAILED");
  assertThrows(TenantAccessException.class,()->TaskPlanDraftRuntimeObservations.validate(request,"HOST_FAILURE"));
 }
}
