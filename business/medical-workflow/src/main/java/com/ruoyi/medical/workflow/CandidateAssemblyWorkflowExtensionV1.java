package com.ruoyi.medical.workflow;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.WireValues;
import java.util.*;
/** Separate memo-pinned additive extension: the original sixteen V2 pins stay byte-identical. */
public record CandidateAssemblyWorkflowExtensionV1(String schemaVersion,String revision,String baseProtocolSha256,String sourceVerifierExtensionSha256,String prepareActivity,String dispatchActivity,String commitActivity,String recoverActivity,String versionMarker){
    public static final String MARKER="task-candidate-assembly-stage-v1",CONFIG_MEMO="medical.task.candidate-assembly.v1",HASH_MEMO="medical.task.candidate-assembly.v1.sha256";
    private static final JsonMapper JSON=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    public CandidateAssemblyWorkflowExtensionV1{WireValues.uuid(revision);WireValues.hash(baseProtocolSha256);WireValues.hash(sourceVerifierExtensionSha256);if(!"1.0.0".equals(schemaVersion)||!"medical.run.candidate-assembly.prepare.v1".equals(prepareActivity)||!"medical.run.candidate-assembly.dispatch.v1".equals(dispatchActivity)||!"medical.run.candidate-assembly.commit.v1".equals(commitActivity)||!"medical.run.candidate-assembly.recover.v1".equals(recoverActivity)||!MARKER.equals(versionMarker))invalid();}
    public static CandidateAssemblyWorkflowExtensionV1 load(String value){try{return JSON.readValue(value,CandidateAssemblyWorkflowExtensionV1.class);}catch(Exception e){throw new IllegalArgumentException("ASSEMBLY_EXTENSION_REQUIRED",e);}}
    public String canonical(){try{return new org.erdtman.jcs.JsonCanonicalizer(JSON.writeValueAsString(this)).getEncodedString();}catch(Exception e){throw new IllegalArgumentException("ASSEMBLY_EXTENSION_REQUIRED",e);}}
    public String canonicalSha256(){try{return WireValues.sha256(canonical().getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public void require(WaitingWorkflowDeploymentV2 base,SourceVerifierWorkflowExtensionV1 verifier){if(!base.canonicalSha256().equals(baseProtocolSha256)||!sourceVerifierExtensionSha256.equals(verifier.canonicalSha256())||!baseProtocolSha256.equals(verifier.baseProtocolSha256()))invalid();}
    public Map<String,Object> memo(){return Map.of(CONFIG_MEMO,canonical(),HASH_MEMO,canonicalSha256());}
    public void requireMemo(String config,String hash){if(!canonical().equals(config)||!canonicalSha256().equals(hash)||!equals(load(config)))invalid();}
    private static void invalid(){throw new IllegalArgumentException("ASSEMBLY_EXTENSION_REQUIRED");}
}
