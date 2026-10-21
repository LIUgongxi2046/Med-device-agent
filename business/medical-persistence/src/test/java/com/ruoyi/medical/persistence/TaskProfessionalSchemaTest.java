package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskProfessionalSchemaTest {
 private final JsonMapper json=new JsonMapper();
 private static final String ID="00000000-0000-4000-8000-000000000001",OTHER="00000000-0000-4000-8000-000000000002",HASH="a".repeat(64);
 @Test void explicitProfessionalSchemaNeverReusesN00OrReplay()throws Exception {
  var schema=new TaskProfessionalSchema();assertEquals(TaskProfessionalRuntimeService.ENVELOPE_REF,schema.ref("STEP_ENVELOPE"));assertEquals(TaskResearchCompletionAuthority.RESULT_REF,schema.ref("STEP_RESULT"));
  assertThrows(SchemaRejection.class,()->schema.ref("REPLAY_MANIFEST"));assertThrows(SchemaRejection.class,()->schema.ref("EXACT_INPUT"));
 }
 @Test void closedEnvelopeCannotCarryHostSuccessOrIsolationClaims()throws Exception {
  var schema=new TaskProfessionalSchema();var value=envelope();schema.read("STEP_ENVELOPE",ContentReadAuthorization.canonicalBytes(value));
  value.put("isolated",true);assertThrows(SchemaRejection.class,()->schema.read("STEP_ENVELOPE",ContentReadAuthorization.canonicalBytes(value)));
 }
 @Test void envelopeIsExactN02TaskNotCallerSelectedRoleNode()throws Exception {
  var schema=new TaskProfessionalSchema();var value=envelope();value.put("node_kind","N07");assertThrows(SchemaRejection.class,()->schema.read("STEP_ENVELOPE",ContentReadAuthorization.canonicalBytes(value)));
  value.put("node_kind","N02").put("execution_kind","MODEL_VERIFICATION");assertThrows(SchemaRejection.class,()->schema.read("STEP_ENVELOPE",ContentReadAuthorization.canonicalBytes(value)));
 }
 @Test void duplicateInputFieldsAndTrailingJsonDenied()throws Exception {
  var schema=new TaskProfessionalSchema();assertThrows(Exception.class,()->schema.read("STEP_ENVELOPE","{\"session_id\":\"a\",\"session_id\":\"b\"}".getBytes()));assertThrows(Exception.class,()->schema.read("STEP_ENVELOPE",(json.writeValueAsString(envelope())+" {}").getBytes()));
 }
 @Test void exportRequiresOriginalBytesNotHostFileHashes()throws Exception {
  var schema=new TaskProfessionalSchema();var value=export();schema.read("SESSION_EXPORT",ContentReadAuthorization.canonicalBytes(value));
  ((com.fasterxml.jackson.databind.node.ObjectNode)value.required("files").get(0)).remove("content_base64");assertThrows(SchemaRejection.class,()->schema.read("SESSION_EXPORT",ContentReadAuthorization.canonicalBytes(value)));
 }
 @Test void exportRejectsChangedContentTraversalDuplicateAndMissingInventory()throws Exception {
  var schema=new TaskProfessionalSchema();var value=export();((com.fasterxml.jackson.databind.node.ObjectNode)value.required("files").get(0)).put("content_base64","Yg==");var changed=value;assertThrows(SchemaRejection.class,()->schema.read("SESSION_EXPORT",ContentReadAuthorization.canonicalBytes(changed)));
  for(String path:List.of("/runtime-inventory.json","a/../runtime-inventory.json","runtime\\inventory.json","other.json")){value=export();((com.fasterxml.jackson.databind.node.ObjectNode)value.required("files").get(0)).put("path",path);var candidate=value;assertThrows(SchemaRejection.class,()->schema.read("SESSION_EXPORT",ContentReadAuthorization.canonicalBytes(candidate)));}
 }
 @Test void privateProductionProofsHaveNoPublicConstructorsOrRawFactory(){
  for(Class<?> type:List.of(TaskProfessionalRuntimeService.Envelope.class,TaskProfessionalRuntimeService.Session.class,TaskResearchExecutionService.Produced.class,TaskInvocationContext.Prepared.class,TaskProfessionalObjectLedger.Intent.class,TaskProfessionalObjectLedger.Attempt.class))for(var ctor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(ctor.getModifiers()),type.getName());
  for(var method:TaskNodeProducerAuthority.LeasedProduction.class.getMethods())if(method.getName().equals("requireProduction")){assertEquals(1,method.getParameterCount());assertTrue(Set.of(TaskProfessionalRuntimeService.Envelope.class,TaskResearchExecutionService.Produced.class,TaskInvocationContext.Prepared.class).contains(method.getParameterTypes()[0]));}
 }
 @Test void objectRecoveryIdentityIncludesVersionStepLeaseAndKind()throws Exception {
  var key=TaskProfessionalObjectLedger.key(ID,OTHER,"1","STEP_ENVELOPE");assertEquals(key,TaskProfessionalObjectLedger.key(ID,OTHER,"1","STEP_ENVELOPE"));assertNotEquals(key,TaskProfessionalObjectLedger.key(ID,OTHER,"2","STEP_ENVELOPE"));assertNotEquals(key,TaskProfessionalObjectLedger.key(ID,OTHER,"1","STEP_RESULT"));assertNotEquals(key,TaskProfessionalObjectLedger.key(OTHER,ID,"1","STEP_ENVELOPE"));
 }
 @Test void exactInputKeyBindsRealObservedRequestNotOnlyEqualBytes()throws Exception {
  var key=TaskProfessionalObjectLedger.exactKey(ID,OTHER,"1",ID,HASH);
  assertNotEquals(key,TaskProfessionalObjectLedger.exactKey(ID,OTHER,"1",OTHER,HASH));
  assertNotEquals(key,TaskProfessionalObjectLedger.exactKey(ID,OTHER,"1",ID,"b".repeat(64)));
  assertNotEquals(key,TaskProfessionalObjectLedger.exactKey(ID,OTHER,"2",ID,HASH));
  assertNotEquals(key,TaskProfessionalObjectLedger.key(ID,OTHER,"1","TASK_EXACT_INPUT"));
 }
 private com.fasterxml.jackson.databind.node.ObjectNode envelope()throws Exception{
  var v=json.createObjectNode().put("schema_version","1.0.0").put("execution_kind","TASK").put("node_kind","N02");
  v.put("compiled_prompt","{} ").put("prompt_sha256",WireValues.sha256("{} ".getBytes(java.nio.charset.StandardCharsets.UTF_8)));v.putObject("prompt_provenance").put("launch_sha256",v.required("prompt_sha256").asText());
        for(String k:List.of("tenant_id","project_id","task_id","run_id","step_id","action_id","session_id","question_id","input_artifact_id","agent_release_id","deployment_revision"))v.put(k,ID);
  for(String k:List.of("input_sha256","input_receipt_sha256","runtime_manifest_sha256","publication_approval_sha256","deployment_sha256"))v.put(k,HASH);
  v.put("lease_generation",1).put("worker_service_uri","spiffe://test.invalid/professional-worker");v.set("role_version",json.createObjectNode().put("version_id",ID).put("content_sha256",HASH));v.set("execution_policy_version",json.createObjectNode().put("version_id",ID).put("content_sha256",HASH));v.putArray("action_scope").add("task.read");return v;
 }
 private com.fasterxml.jackson.databind.node.ObjectNode export()throws Exception {
  var b=json.createObjectNode();for(String k:List.of("tenant_id","run_id","step_id","action_id"))b.put(k,ID);b.put("lease_generation",1).put("input_sha256",HASH).put("step_envelope_sha256",HASH);
  var v=json.createObjectNode().put("schema_version","1.0.0").put("kind","SESSION_EXPORT").put("session_id",ID).put("runtime_manifest_sha256",HASH).put("supervision_sha256",HASH);v.set("binding",b);v.set("observation",json.createObjectNode());v.putArray("files").add(json.createObjectNode().put("path","runtime-inventory.json").put("sha256",WireValues.sha256("a".getBytes())).put("size_bytes","1").put("content_base64","YQ=="));return v;
 }
}
