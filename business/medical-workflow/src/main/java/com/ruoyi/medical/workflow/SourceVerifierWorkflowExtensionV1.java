package com.ruoyi.medical.workflow;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.WireValues;
import java.util.*;
/** Separate memo-pinned additive extension: the original sixteen V2 pins stay byte-identical. */
public record SourceVerifierWorkflowExtensionV1(String schemaVersion,String revision,String baseProtocolSha256,String sectionWriterExtensionSha256,String professionalTaskQueue,String executeActivity,String prepareActivity,String inventoryActivity,String dispatchActivity,String commitActivity,String recoverActivity,String versionMarker){
    public static final String MARKER="task-source-verifier-stage-v1",CONFIG_MEMO="medical.task.source-verifier.v1",HASH_MEMO="medical.task.source-verifier.v1.sha256";
    private static final JsonMapper JSON=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    public SourceVerifierWorkflowExtensionV1{WireValues.uuid(revision);WireValues.hash(baseProtocolSha256);WireValues.hash(sectionWriterExtensionSha256);if(!"1.0.0".equals(schemaVersion)||professionalTaskQueue==null||!professionalTaskQueue.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")||!"medical.task.source-verifier.execute.v1".equals(executeActivity)||!"medical.run.source-verifier.prepare.v1".equals(prepareActivity)||!"medical.run.source-verifier.inventory.v1".equals(inventoryActivity)||!"medical.run.source-verifier.dispatch.v1".equals(dispatchActivity)||!"medical.run.source-verifier.commit.v1".equals(commitActivity)||!"medical.run.source-verifier.recover.v1".equals(recoverActivity)||!MARKER.equals(versionMarker))invalid();}
    public static SourceVerifierWorkflowExtensionV1 load(String value){try{return JSON.readValue(value,SourceVerifierWorkflowExtensionV1.class);}catch(Exception e){throw new IllegalArgumentException("VERIFIER_EXTENSION_REQUIRED",e);}}
    public String canonical(){try{return new org.erdtman.jcs.JsonCanonicalizer(JSON.writeValueAsString(this)).getEncodedString();}catch(Exception e){throw new IllegalArgumentException("VERIFIER_EXTENSION_REQUIRED",e);}}
    public String canonicalSha256(){try{return WireValues.sha256(canonical().getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public void require(WaitingWorkflowDeploymentV2 base){if(!base.canonicalSha256().equals(baseProtocolSha256)||professionalTaskQueue.equals(base.controlTaskQueue())||professionalTaskQueue.equals(base.professionalTaskQueue()))invalid();}
    public void require(WaitingWorkflowDeploymentV2 base,SectionWriterWorkflowExtensionV1 writer){require(base);writer.require(base);if(!sectionWriterExtensionSha256.equals(writer.canonicalSha256())||professionalTaskQueue.equals(writer.professionalTaskQueue()))invalid();}
    public Map<String,Object> memo(){return Map.of(CONFIG_MEMO,canonical(),HASH_MEMO,canonicalSha256());}
    public void requireMemo(String config,String hash){if(!canonical().equals(config)||!canonicalSha256().equals(hash)||!equals(load(config)))invalid();}
    private static void invalid(){throw new IllegalArgumentException("VERIFIER_EXTENSION_REQUIRED");}
}
