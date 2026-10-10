package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class TemplateOperationSchema {
    private final PermissionSchema json;
    private final TemplateDefinitionSchema definitions;
    private final SchemaCatalogue shared;
    private final JsonMapper reader = JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final String manifest;

    public TemplateOperationSchema(PermissionSchema json, TemplateDefinitionSchema definitions) throws IOException, NoSuchAlgorithmException {
        this.json = json; this.definitions = definitions; shared = SchemaCatalogue.fromClasspath();
        var identity = json.object();
        identity.put("schema_version", "1.0.0"); identity.put("definition_manifest", definitions.manifestSha256());
        for (String name : List.of("templates.schema.json", "template-operations.schema.json")) {
            try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) {
                byte[] bytes = input.readAllBytes(); var document = reader.readTree(bytes);
                shared.requirePublished(document.required("$id").textValue(), DigestUtils.sha256Hex(bytes));
                identity.put(name, DigestUtils.sha256Hex(bytes));
            }
        }
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    JsonNode created(TemplateProtocol.Stored stored, String operationId, String requestId) throws IOException {
        var value = json.object(); value.put("schema_version", "1.0.0"); value.put("operation_id", operationId);
        value.set("template", json.tree(stored)); value.set("allowed_actions", json.tree(List.of()));
        return envelope(value, requestId, stored.revision());
    }

    JsonNode receipt(String content) throws IOException {
        var result = reader.readTree(content); shared.validate("interfaces", result, "Envelope");
        if (!envelope(result.required("data"), result.required("request_id").textValue(),
                result.required("resource_revision").textValue()).equals(result)) invalid();
        return result;
    }

    private JsonNode envelope(JsonNode value, String requestId, String revision) throws IOException {
        shared.validate("template-operations", value, "Created");
        var template = value.required("template");
        if (!template.required("revision").textValue().equals(revision) || PermissionSchema.revision(template.required("authority_revision").textValue()).signum() <= 0) invalid();
        var request = new TemplateProtocol.VersionInput("1.0.0", template.required("base_version").isNull() ? null : json.basis(template.required("base_version")),
                "urn:medical:templates:1.0.0#/$defs/Content", definitions.content(json.canonical(template.required("content")).getBytes(java.nio.charset.StandardCharsets.UTF_8)), template.required("reason").textValue());
        var validated = definitions.version(json.canonicalObject(request).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!validated.content_sha256().equals(template.required("content_sha256").textValue())
                || !json.tree(validated.dependency_refs()).equals(template.required("dependency_refs"))) invalid();
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, value));
        shared.validate("interfaces", result, "Envelope"); return result;
    }

    String hash(TemplateProtocol.VersionInput request, String context, String session) throws IOException {
        var value = json.object(); value.put("method", "POST"); value.put("path", "/api/template-versions"); value.put("schema_version", "1.0.0");
        value.putNull("query"); value.put("context_header", context); value.put("session_id", session); value.set("body", json.tree(request));
        return DigestUtils.sha256Hex(json.canonical(value));
    }
    JsonNode read(String content) throws IOException { return reader.readTree(content); }
    String manifestSha256() { return manifest; }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
