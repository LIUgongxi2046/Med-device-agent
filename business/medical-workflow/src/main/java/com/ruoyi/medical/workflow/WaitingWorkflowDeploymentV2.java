package com.ruoyi.medical.workflow;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.WireValues;
import java.util.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

/** Explicit immutable operational protocol, separate from unchanged V1 launch
 * configuration. Both the durable launch binding and actual started-event memo pin
 * canonicalSha256; a queue name alone never selects new semantics. */
public record WaitingWorkflowDeploymentV2(String schemaVersion,String revision,String namespace,String controlTaskQueue,
        String professionalTaskQueue,String workflowType,String professionalActivity,String waitingActivity,String versionMarker,
        String loadActivity,String recoveryActivity,long recoveryPollMillis,String undispatchedFailureActivity,String executeStageActivity,String commitStageActivity,String recoverStageActivity){
    public static final String MARKER="task-professional-outcome-v2";
    public static final String CONFIG_MEMO="medical.task.protocol.v2", HASH_MEMO="medical.task.protocol.v2.sha256", COMMAND_MEMO="medical.start_command_id";
    private static final JsonMapper JSON=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    public WaitingWorkflowDeploymentV2{
        WireValues.uuid(revision);if(!"2.0.0".equals(schemaVersion)||namespace==null||!namespace.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,254}")||!"medical.task.run".equals(workflowType)||!"medical.professional.step.v2".equals(professionalActivity)||!"medical.run.commit-waiting.v2".equals(waitingActivity)||!MARKER.equals(versionMarker)||!"medical.run.load.v2".equals(loadActivity)||!"medical.run.recover-step.v2".equals(recoveryActivity)||!"medical.run.fail-undispatched.v2".equals(undispatchedFailureActivity)||!"medical.run.execute-stage.v2".equals(executeStageActivity)||!"medical.run.commit-stage.v2".equals(commitStageActivity)||!"medical.run.recover-stage.v2".equals(recoverStageActivity)||recoveryPollMillis<100||recoveryPollMillis>60000)invalid();
        for(String queue:Arrays.asList(controlTaskQueue,professionalTaskQueue))if(queue==null||!queue.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}"))invalid();if(controlTaskQueue.equals(professionalTaskQueue))invalid();
    }
    public static WaitingWorkflowDeploymentV2 load(String value){try{return JSON.readValue(value,WaitingWorkflowDeploymentV2.class);}catch(Exception e){throw new IllegalArgumentException("EXPLICIT_WAITING_V2_DEPLOYMENT_REQUIRED",e);}}
    public String canonical(){try{return new org.erdtman.jcs.JsonCanonicalizer(JSON.writeValueAsString(this)).getEncodedString();}catch(Exception e){throw new IllegalArgumentException("WAITING_V2_CANONICAL_REQUIRED",e);}}
    public String canonicalSha256(){try{return WireValues.sha256(canonical().getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA256_REQUIRED",impossible);}}
    public void require(Reference reference,String actualNamespace){if(!namespace.equals(actualNamespace)||!controlTaskQueue.equals(reference.controlTaskQueue())||!professionalTaskQueue.equals(reference.professionalTaskQueue())||reference.executionKind()!=ExecutionKind.TASK)invalid();}
    public Map<String,Object> memo(String command){WireValues.uuid(command);return Map.of(CONFIG_MEMO,canonical(),HASH_MEMO,canonicalSha256(),COMMAND_MEMO,command);}
    public void requireMemo(String config,String hash,String command){WireValues.uuid(command);if(!canonical().equals(config)||!canonicalSha256().equals(hash)||!equals(load(config)))invalid();}
    private static void invalid(){throw new IllegalArgumentException("EXPLICIT_WAITING_V2_DEPLOYMENT_REQUIRED");}
}
