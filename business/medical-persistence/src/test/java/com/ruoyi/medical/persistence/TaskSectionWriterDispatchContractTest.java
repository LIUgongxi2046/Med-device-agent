package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline contract cases only. Written alongside implementation; no provider or DB. */
class TaskSectionWriterDispatchContractTest {
    private static final String ID="00000000-0000-4000-8000-000000000001",HASH="a".repeat(64);
    private final JsonMapper json=new JsonMapper();
    private JsonSchema definition(String name)throws Exception{try(var in=getClass().getResourceAsStream("/task-section-writer-dispatch.schema.json")){return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(Objects.requireNonNull(in)),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build()).getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));}}
    private ObjectNode envelope(){var n=json.createObjectNode();for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id","action_id","session_id","plan_revision","snapshot_id","input_artifact_id","dependency_access_set_id"))n.put(f,ID);for(String f:List.of("input_sha256","input_receipt_sha256","deployment_sha256","runtime_manifest_sha256","prompt_sha256"))n.put(f,HASH);for(String f:List.of("producer","role_version","model_version","execution_policy_version","prompt_provenance"))n.putObject(f);return n.put("schema_version","1.0.0").put("execution_kind","TASK").put("node_kind","N04").put("lease_generation",1).put("deployment_revision","1").put("worker_service_uri","spiffe://example/writer").put("compiled_prompt","Literal writer instructions and registered input");}
    @Test void envelopeContractCannotRelabelResearchOrInventFields()throws Exception{var schema=definition("Envelope");var valid=envelope();assertTrue(schema.validate(valid).isEmpty());assertFalse(schema.validate(valid.deepCopy().put("node_kind","N02")).isEmpty());assertFalse(schema.validate(valid.deepCopy().put("model_tools",true)).isEmpty());assertFalse(schema.validate(valid.deepCopy().put("compiled_prompt","")).isEmpty());var missing=valid.deepCopy();missing.remove("input_receipt_sha256");assertFalse(schema.validate(missing).isEmpty());assertFalse(schema.validate(valid.deepCopy().put("lease_generation",0)).isEmpty());}
    @Test void historicalAndClaimProofsArePrivate(){for(Class<?> type:List.of(TaskSectionWriterDispatchJournal.Original.class,TaskSectionWriterDispatchReservation.Pending.class,TaskSectionWriterDispatchReservation.Original.class,TaskSectionWriterObjectLedger.Attempt.class)){for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()),type.getName());}}
    @Test void distinctOriginalCommandNamespaces(){assertNotEquals(TaskRunDispatchReservation.reservationId(ID,ID,ID),TaskSectionWriterDispatchReservation.reservationId(ID,ID,ID));assertNotEquals(TaskHostControlBootstrap.actionId(ID,ID,ID,BigInteger.ONE),TaskSectionWriterHostBootstrap.actionId(ID,ID,ID,BigInteger.ONE));}
    @Test void workerRoutesRequireWorkflowOriginalNotActorLocator(){assertTrue(TaskSectionWriterRuntimeSecurityConfiguration.ROUTES.contains("/internal/v1/section-writer-runtime/open-workflow"));assertTrue(TaskSectionWriterRuntimeSecurityConfiguration.ROUTES.contains("/internal/v1/section-writer-runtime/completed-original"));assertFalse(TaskSectionWriterRuntimeSecurityConfiguration.ROUTES.contains("/internal/v1/section-writer-runtime/open"));}
    @Test void dispatchRequiresPhysicalObjectAndOriginalCommand()throws Exception{var schema=definition("Dispatch");var n=json.createObjectNode();for(String f:List.of("tenant_id","project_id","task_id","run_id","step_id","action_id","command_id","session_id","input_artifact_id","envelope_artifact_id","initiator_id"))n.put(f,ID);for(String f:List.of("reference_sha256","input_sha256","input_receipt_sha256","envelope_sha256","envelope_receipt_sha256","launch_sha256"))n.put(f,HASH);for(String f:List.of("input_object_version","envelope_object_version","worker_service_uri","classification"))n.put(f,"original");n.put("schema_version","1.0.0").put("kind","TASK_SECTION_WRITER_DISPATCH").put("lease_generation",1);assertTrue(schema.validate(n).isEmpty());for(String f:List.of("command_id","envelope_receipt_sha256","envelope_object_version","reference_sha256")){var missing=n.deepCopy();missing.remove(f);assertFalse(schema.validate(missing).isEmpty(),f);}}
}
