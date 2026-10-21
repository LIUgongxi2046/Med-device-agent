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

/** Closed, independent091 operation contract. Original closed request is retained in canonical form. */
@org.springframework.stereotype.Component
public final class FormalEvaluationNodeInputObjectSchema {
    public static final String KIND = "EVALUATION_NODE_INPUT";
    public static final String OPERATION = "evaluation.node_input.register";
    private final PermissionSchema json;
    private final JsonMapper decoder = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final JsonSchema root;
    private final String manifest;

    public FormalEvaluationNodeInputObjectSchema(PermissionSchema json) throws IOException {
        this.json = Objects.requireNonNull(json);
        byte[] bytes;
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/formal-evaluation-node-input-object.schema.json"))) {
            bytes = in.readAllBytes();
        }

        manifest = org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
        root = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(decoder.readTree(bytes), SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        root.initializeValidators();
    }

    public record Request(String schemaVersion, String executionId, FormalEvaluationExecutionProtocol.Start start, String sampleId,
            int repeatIndex, String nodeKind, String planOriginalKey, FormalEvaluationPlanPreparationSchema.Request planRequest, JsonNode planReceipt) {
        public Request { planReceipt=planReceipt.deepCopy(); }
        @Override public JsonNode planReceipt(){return planReceipt.deepCopy();}
    }

    record Original(Request request, String body) {
    }

    Original request(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 1 || bytes.length > 1048576) invalid();
        String raw;
        try {
            raw = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        }

        var n = read(raw);
        if (!root.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append("Request")).validate(n).isEmpty()) invalid();
        var request=decoder.treeToValue(n, Request.class);
        var plan=request.planRequest();
        if(!request.executionId().equals(plan.executionId())||!request.start().equals(plan.start())||!request.sampleId().equals(plan.sampleId())||request.repeatIndex()!=plan.repeatIndex()||!request.nodeKind().equals(plan.nodeKind())) invalid();
        return new Original(request, canonical(n));
    }

    record Recovery(String schemaVersion,String originalKey,JsonNode originalRequest) {}
    Recovery recovery(byte[] bytes)throws IOException {
        if(bytes==null||bytes.length<1||bytes.length>2097152)invalid();
        String raw;try{raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
        var n=read(raw);
        if(!root.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append("Recovery")).validate(n).isEmpty())invalid();
        var r=decoder.treeToValue(n,Recovery.class);request(canonical(r.originalRequest()).getBytes(StandardCharsets.UTF_8));return r;
    }
    JsonNode read(String raw) throws IOException {
        try {
            return decoder.readTree(raw);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        }
    }

    String canonical(Object v) throws IOException {
        return json.canonical(v instanceof JsonNode n ? n : AgentPublicationWitness.tree(v));
    }

    String hash(Object v) throws IOException {
        return org.apache.commons.codec.digest.DigestUtils.sha256Hex(canonical(v).getBytes(StandardCharsets.UTF_8));
    }

    String requestHash(String tenant, String actor, String context, String raw) throws IOException {
        return hash(Map.of("operation", OPERATION, "tenant_id", tenant, "actor_id", actor, "context_revision", context,
                "original_body", raw, "schema_manifest_sha256", manifest));
    }

    public String manifest() {
        return manifest;
    }

    static void key(String key) {
        if (key == null || !key.matches("[!-~]{16,128}")) invalid();
    }

    private static void invalid() {
        throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
}
