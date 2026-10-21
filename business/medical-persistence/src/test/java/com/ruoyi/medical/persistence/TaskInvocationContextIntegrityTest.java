package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Structural replay checks only: these fixtures do not mint authenticated supervisor proofs. */
class TaskInvocationContextIntegrityTest {
    private final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
    private TaskInvocationContextSchema schemas;private ObjectNode projection,receipt;private byte[] observed,exact;
    @BeforeEach void fixture()throws Exception {
        schemas=new TaskInvocationContextSchema();projection=json.createObjectNode().put("provider","enterprise-model").put("model","approved").put("session_id",TaskInvocationProjectionTest.SESSION);
        projection.putNull("system");projection.putNull("tool_history");projection.putArray("tools");var message=projection.putArray("messages").addObject().put("role","user");message.putObject("source").put("kind","user");message.putArray("content").addObject().put("type","text").put("text","actual input");
        projection.putObject("parameters").put("max_output_tokens",5).putNull("temperature").put("reasoning_effort","none").put("purpose","TASK").putArray("stop");
        observed=ContentReadAuthorization.canonicalBytes(projection);exact=ContentReadAuthorization.canonicalBytes(TaskInvocationProjection.initial(projection,TaskInvocationProjectionTest.SESSION,"actual input",List.of(),projection.required("tools")));
        receipt=json.createObjectNode().put("schema_version","1.0.0").put("method","POST").put("route","/v1/model-contexts").put("session_id",TaskInvocationProjectionTest.SESSION).put("ordinal",1)
            .put("projection_schema_ref",TaskInvocationContextSchema.PROJECTION_REF).put("projection_schema_version","1.0.0").put("projection_schema_sha256",schemas.sha256()).put("body_sha256",WireValues.sha256(observed)).put("raw_body_sha256",WireValues.sha256(observed)).put("raw_size_bytes",observed.length).put("body_base64",Base64.getEncoder().encodeToString(observed));
    }
    private void validate()throws Exception{TaskInvocationOriginals.validateRecordedProjection(observed,receipt,TaskInvocationProjectionTest.SESSION,"actual input",List.of(),exact,schemas);}
    @Test void exactObservedProjectionAndCanonicalInputRemainBound()throws Exception{assertDoesNotThrow(this::validate);}
    @Test void rawHashIsNotInferredFromCanonicalHash(){receipt.put("raw_body_sha256","0".repeat(64));assertThrows(TenantAccessException.class,this::validate);}
    @Test void rawLengthMustMatchActualCapturedBytes(){receipt.put("raw_size_bytes",observed.length+1);assertThrows(TenantAccessException.class,this::validate);}
    @Test void projectionSchemaRevisionCannotDrift(){receipt.put("projection_schema_sha256","0".repeat(64));assertThrows(TenantAccessException.class,this::validate);}
    @Test void rewrittenObservedBytesAreNotAcceptedAsOriginal()throws Exception{observed=(new String(observed,StandardCharsets.UTF_8)+" ").getBytes(StandardCharsets.UTF_8);receipt.put("body_base64",Base64.getEncoder().encodeToString(observed)).put("body_sha256",WireValues.sha256(observed)).put("raw_body_sha256",WireValues.sha256(observed)).put("raw_size_bytes",observed.length);assertThrows(TenantAccessException.class,this::validate);}
    @Test void differentExactInputCannotReuseCapturedObservation(){exact="{\"messages\":[],\"tools\":[]}".getBytes(StandardCharsets.UTF_8);assertThrows(RuntimeException.class,this::validate);}
    @Test void unsupportedLaterHistoryCannotBecomeInitialInput(){receipt.put("ordinal",2);assertThrows(TenantAccessException.class,this::validate);}
    @Test void privateContextCapabilitiesCannotBeConstructedFromJson(){for(var type:List.of(TaskInvocationContext.Prepared.class,TaskInvocationContext.Registered.class,TaskInvocationOriginals.Stored.class))for(var ctor:type.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()));}
}
