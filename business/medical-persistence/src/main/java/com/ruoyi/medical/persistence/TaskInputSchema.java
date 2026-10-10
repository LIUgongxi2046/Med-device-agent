package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ArtifactBindings;
import com.ruoyi.medical.contracts.CommonBindings;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class TaskInputSchema {
    private final SchemaCatalogue shared;
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final String manifest;

    public TaskInputSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        shared = SchemaCatalogue.fromClasspath();
        var identity = json.object(); identity.put("schema_version", "1.0.0");
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/tasks.schema.json"))) {
            byte[] bytes = input.readAllBytes(); var document = reader.readTree(bytes);
            shared.requirePublished(document.required("$id").textValue(), DigestUtils.sha256Hex(bytes));
            identity.put("tasks.schema.json", DigestUtils.sha256Hex(bytes));
        }
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("contracts/catalogue.json"))) {
            identity.put("contracts/catalogue.json", DigestUtils.sha256Hex(input.readAllBytes()));
        }
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    TaskProtocol.ValidatedCreate create(byte[] body) throws IOException {
        var value = parse(body); var saved = shared.bind("tasks", value, "Create");
        var scope = TypedSchemaBindings.bind(shared, CommonBindings.SOURCE_SCOPE, value.required("scope_selection"));
        var request = reader.treeToValue(value, TaskProtocol.Create.class);
        WireValues.uuid(request.project_id()); WireValues.uuid(request.profile_revision()); WireValues.uuid(request.template_version());
        WireValues.uuid(request.agent_release_id()); WireValues.uuid(request.owner_id());
        if (request.based_on_document_revision() != null) WireValues.uuid(request.based_on_document_revision());
        ProjectSchema.text(request.goal(), Integer.MAX_VALUE);
        if (!scope.read().equals(request.scope_selection())) throw new IllegalArgumentException("TASK_SCOPE_BINDING_CHANGED");
        return new TaskProtocol.ValidatedCreate(request, saved.canonicalJson(), DigestUtils.sha256Hex(saved.canonicalBytes()),
                DigestUtils.sha256Hex(scope.payload().canonicalBytes()), manifest);
    }

    TaskProtocol.PlanVersionInput plan(byte[] body) throws IOException {
        var value = parse(body); shared.validate("tasks", value, "PlanVersionInput");
        var content = TypedSchemaBindings.bind(shared, ArtifactBindings.PLAN_CANDIDATE, value.required("content"));
        ProjectSchema.text(value.required("reason").textValue(), 2000);
        var revision = PermissionSchema.revision(value.required("expected_revision").textValue());
        if (revision.signum() <= 0) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        String base = value.required("base_version").isNull() ? null : value.required("base_version").textValue();
        if (base != null) WireValues.uuid(base);
        return new TaskProtocol.PlanVersionInput("1.0.0", revision.toString(), base, value.required("schema_ref").textValue(),
                content.read(), value.required("reason").textValue());
    }

    JsonNode read(String content) throws IOException { return reader.readTree(content); }
    TaskProtocol.ScopeSnapshotManifest snapshot(JsonNode content) throws IOException {
        return reader.treeToValue(content, TaskProtocol.ScopeSnapshotManifest.class);
    }
    String manifestSha256() { return manifest; }
    private JsonNode parse(byte[] body) throws IOException {
        var value = reader.readTree(body);
        if (value == null || !value.isObject()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return value;
    }
}
