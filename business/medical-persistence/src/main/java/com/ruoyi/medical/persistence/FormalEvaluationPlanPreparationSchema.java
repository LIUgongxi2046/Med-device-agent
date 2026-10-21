package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Closed090 transport. Request hashes are assertions only; all authority is resolved independently. */
@Component
public final class FormalEvaluationPlanPreparationSchema {
    public static final String KIND = "EVALUATION_PLAN_PREPARATION";
    public static final String OPERATION = "evaluation.plan_preparation.register";
    public static final String PAYLOAD_REF = "https://schemas.medical-agent.invalid/1.0.0/agent-design/artifacts.schema.json#/$defs/PlanCandidate";
    public static final int MAXIMUM_BYTES = 1048576;
    private final PermissionSchema json;
    private final JsonMapper decoder = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final JsonSchema root;
    private final String manifest;
    public FormalEvaluationPlanPreparationSchema(PermissionSchema json) throws IOException {
        this.json = json;
        byte[] bytes;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/formal-evaluation-plan-preparation.schema.json"))) {
            bytes = input.readAllBytes();
        }
        manifest = org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
        root = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(decoder.readTree(bytes),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        root.initializeValidators();
    }
    public record Request(String schemaVersion, String executionId, FormalEvaluationExecutionProtocol.Start start,
            String sampleId, int repeatIndex, String nodeKind, PermissionProtocol.Basis template) { }
    record Original(Request request, String body) { }
    record Recovery(String schemaVersion, String originalKey, Request originalRequest) { }
    record BoundRecovery(String schemaVersion, String originalKey, FormalEvaluationContextProtocol.BoundStart originalRequest) { }
    Original request(byte[] bytes) throws IOException {
        String raw = utf8(bytes); var node = read(raw); validate("Request", node);
        return new Original(decoder.treeToValue(node, Request.class), raw);
    }
    Recovery recovery(byte[] bytes) throws IOException { var value=decode("Recovery", bytes, Recovery.class);key(value.originalKey());return value; }
    BoundRecovery boundRecovery(byte[] bytes) throws IOException { var value=decode("BoundRecovery", bytes, BoundRecovery.class);key(value.originalKey());return value; }
    FormalEvaluationContextProtocol.BoundStart boundStart(byte[] bytes) throws IOException {
        return decode("BoundStart", bytes, FormalEvaluationContextProtocol.BoundStart.class);
    }
    private <T> T decode(String name, byte[] bytes, Class<T> type) throws IOException {
        var node=read(utf8(bytes)); validate(name,node); return decoder.treeToValue(node,type);
    }
    JsonNode validate(String name, JsonNode node) {
        if(node == null || !root.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(node).isEmpty()) invalid();
        return node;
    }
    private String utf8(byte[] bytes) {
        if (bytes == null || bytes.length < 1 || bytes.length > MAXIMUM_BYTES) invalid();
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    JsonNode read(String value) throws IOException { try { return decoder.readTree(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); } }
    String canonical(Object value) throws IOException { return json.canonical(value instanceof JsonNode n ? n : AgentPublicationWitness.tree(value)); }
    String hash(Object value) throws IOException { return org.apache.commons.codec.digest.DigestUtils.sha256Hex(canonical(value).getBytes(StandardCharsets.UTF_8)); }
    String requestHash(String tenant,String actor,String context,String original) throws IOException {
        // Canonical original request allows a typed recovery wrapper to carry the full original request.
        return hash(Map.of("operation",OPERATION,"tenant_id",tenant,"actor_id",actor,"context_revision",context,
                "original_request",read(original),"schema_manifest_sha256",manifest));
    }
    public String manifest() { return manifest; }
    static void key(String key) { if(key == null || !key.matches("[!-~]{16,128}")) invalid(); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
