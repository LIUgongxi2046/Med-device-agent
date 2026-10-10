package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class TaskCreateDraftSchema {
    private final PermissionSchema json;
    private final TaskInputSchema inputs;
    private final SchemaCatalogue shared;
    private final JsonSchema schema;
    private final String manifest;

    public TaskCreateDraftSchema(PermissionSchema json, TaskInputSchema inputs) throws IOException, java.security.NoSuchAlgorithmException {
        this.json = json; this.inputs = inputs; shared = SchemaCatalogue.fromClasspath(); byte[] bytes;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/task-create-draft.schema.json"))) { bytes = input.readAllBytes(); }
        schema = shared.compileApiSchema(inputs.read(new String(bytes, StandardCharsets.UTF_8))); schema.initializeValidators();
        var identity = json.object(); identity.put("schema_version", "1.0.0"); identity.put("draft_schema", DigestUtils.sha256Hex(bytes));
        identity.put("task_input_manifest", inputs.manifestSha256()); identity.put("permission_manifest", json.manifestSha256());
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    JsonNode envelope(JsonNode draft, String requestId) throws IOException {
        if (draft != null && !draft.isNull()) {
            if (!schema.validate(draft).isEmpty()) invalid();
            var input = inputs.create(json.canonical(draft.required("content")).getBytes(StandardCharsets.UTF_8));
            if (!input.request().project_id().equals(draft.required("project_id").textValue())
                    || !input.request_sha256().equals(draft.required("body_sha256").textValue())) invalid();
        }
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, null, draft == null ? json.tree(null) : draft));
        shared.validate("interfaces", result, "Envelope"); return result;
    }
    JsonNode response(String value) throws IOException {
        var result = inputs.read(value); shared.validate("interfaces", result, "Envelope");
        if (!result.equals(envelope(result.required("data"), result.required("request_id").textValue()))) invalid(); return result;
    }
    String manifestSha256() { return manifest; }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
