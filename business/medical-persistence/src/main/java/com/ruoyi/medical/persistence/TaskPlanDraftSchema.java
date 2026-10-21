package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;

/** Closed data contracts, never an execution or approval capability. */
public final class TaskPlanDraftSchema {
    private static final Set<String> TYPES = Set.of("Start", "Admission", "CandidateReceipt", "CheckReceipt", "ToolReceipt", "Adopt", "Origin", "Recovery", "InputManifest", "View", "Preparation");
    private final JsonMapper parser = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final JsonSchema schema;
    private final String manifest;
    public TaskPlanDraftSchema() throws IOException {
        byte[] bytes;
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/task-plan-draft.schema.json"))) { bytes = stream.readAllBytes(); }
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        for (String type : TYPES) definition(type).initializeValidators();
        manifest = DigestUtils.sha256Hex(bytes);
    }
    public record Start(String schemaVersion, String taskRevision, String expectedRevision, PermissionProtocol.Basis template,
            PermissionProtocol.Basis profile, String agentReleaseId, PermissionProtocol.Basis executionPolicy,
            PermissionProtocol.Basis enterpriseBudget, PermissionProtocol.Basis parentBudget, String currency,
            String maximumAmount, String maximumTokens, String reason) { }
    record Original(Start request, String raw, String canonical) { }
    Original start(byte[] bytes) throws IOException {
        String raw = utf8(bytes); var value = read(raw); validate("Start", value);
        var request = parser.treeToValue(value, Start.class);
        if (request.enterpriseBudget().version_id().equals(request.parentBudget().version_id())) invalid();
        return new Original(request, raw, canonical(value));
    }
    JsonNode validate(String type, JsonNode value) {
        if (!TYPES.contains(type) || value == null || !definition(type).validate(value).isEmpty()) invalid();
        if (type.equals("CheckReceipt") && (value.required("result").asText().equals("COMPLETE") != value.required("missing_item_ids").isEmpty())) invalid();
        if (type.equals("Origin") && (!value.required("candidate_sha256").equals(value.required("draft_sha256"))
                || value.required("draft_revision").equals(value.required("parent_draft_revision")))) invalid();
        if(type.equals("InputManifest")) {
            try {
                var fixed=read(value.required("fixed_inputs_json").textValue());
                if(!fixed.isObject()||!canonical(fixed).equals(value.required("fixed_inputs_json").textValue())
                        ||!hash(fixed).equals(value.required("authority_sha256").textValue()))invalid();
            } catch(IOException e) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
        }
        if(type.equals("Admission")) {
            validate("InputManifest",value.required("input_manifest"));
            try {
                if(!hash(value.required("input_manifest")).equals(value.required("input_sha256").textValue()))invalid();
                for(String key:List.of("tenant_id","project_id","task_id","task_revision","run_id","step_id","scope_snapshot_id","authority_sha256"))
                    if(!value.required(key).equals(value.required("input_manifest").required(key)))invalid();
                for(String key:List.of("tenant_id","project_id","run_id"))
                    if(!value.required(key).equals(value.required("scope_manifest").required(key))||!value.required(key).equals(value.required("principal_manifest").required(key)))invalid();
                if(!value.required("actor_id").equals(value.required("scope_manifest").required("initiator_id"))
                        ||!value.required("actor_id").equals(value.required("principal_manifest").required("initiator_id"))
                        ||!value.required("principal_id").equals(value.required("principal_manifest").required("principal_id"))
                        ||!hash(value.required("scope_manifest")).equals(value.required("principal_manifest").required("scope_sha256").asText()))invalid();
            } catch(IOException e) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
        }
        return value;
    }
    JsonNode read(String raw) throws IOException {
        try { return parser.readTree(raw); } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    JsonNode tree(Object value) { return parser.valueToTree(value); }
    String canonical(Object value) throws IOException { return new String(ContentReadAuthorization.canonicalBytes(tree(value)), StandardCharsets.UTF_8); }
    String hash(Object value) throws IOException { return DigestUtils.sha256Hex(canonical(value)); }
    String manifest() { return manifest; }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    static String utf8(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 1048576) invalid();
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
