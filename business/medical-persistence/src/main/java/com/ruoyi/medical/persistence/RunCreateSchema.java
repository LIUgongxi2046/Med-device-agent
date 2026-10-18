package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class RunCreateSchema {
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final PermissionSchema json;
    private final SchemaCatalogue shared;
    private final JsonSchema schema;
    private final String manifest;

    public RunCreateSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        byte[] bytes;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/run-create.schema.json"))) { bytes = input.readAllBytes(); }
        schema = shared.compileApiSchema(reader.readTree(bytes));
        definition("Create").initializeValidators(); definition("Receipt").initializeValidators();
        var identity = json.object().put("schema_version", "1.0.0").put("run-create.schema.json", DigestUtils.sha256Hex(bytes))
            .put("permission_manifest", json.manifestSha256());
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    public record Create(String schema_version, String task_revision, String plan_revision, String snapshot_id,
            String budget_revision, String expected_revision) { }

    Create request(byte[] body) throws IOException {
        var input = reader.readTree(body); validate("Create", input);
        var result = reader.treeToValue(input, Create.class); PermissionSchema.revision(result.expected_revision()); return result;
    }
    JsonNode read(String raw) throws IOException { return reader.readTree(raw); }
    String manifestSha256() { return manifest; }
    String requestHash(String task, Create input) throws IOException {
        var value = json.object().put("operation_id", "RN-01").put("task_id", task); value.set("input", json.tree(input));
        return DigestUtils.sha256Hex(json.canonical(value));
    }
    JsonNode receipt(JsonNode value, String requestId) {
        validate("Receipt", value);
        String operation = value.required("operation").required("operation_id").textValue();
        if (!value.required("run_id").equals(value.required("operation").required("resource_id"))
                || !("/api/operations/" + operation).equals(value.required("operation").required("status_url").textValue())) invalid();
        var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, "1", value));
        shared.validate("interfaces", response, "Envelope"); return response;
    }
    JsonNode response(String raw) throws IOException {
        var response = read(raw); shared.validate("interfaces", response, "Envelope");
        if (!receipt(response.required("data"), response.required("request_id").textValue()).equals(response)) invalid();
        return response;
    }
    static String next(String value) {
        var current = PermissionSchema.revision(value);
        if (current.equals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE))) throw new TenantAccessException(ErrorCode.REVISION_LIMIT);
        return current.add(BigInteger.ONE).toString();
    }
    static void requireReady(TaskProtocol.Head head, Create request) {
        if (!"READY".equals(head.status()) || head.planRevision() == null) throw new TenantAccessException(ErrorCode.PLAN_UNCONFIRMED);
        if (!head.revision().toString().equals(request.expected_revision())) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        if (!head.taskRevision().equals(request.task_revision()) || !head.planRevision().equals(request.plan_revision())
                || !head.snapshotId().equals(request.snapshot_id())) throw new TenantAccessException(ErrorCode.PLAN_STALE);
        next(head.revision().toString());
    }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private void validate(String name, JsonNode input) { if (input == null || !definition(name).validate(input).isEmpty()) invalid(); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
