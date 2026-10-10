package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class MemberGovernanceReadSchema {
    public record Query(int limit, String after, String subjectId, String watermark) { }
    private final PermissionSchema json;
    private final InvitationSchema invitations;
    private final JsonSchema schema;
    private final SchemaCatalogue shared;

    public MemberGovernanceReadSchema(PermissionSchema json, InvitationSchema invitations) throws IOException, NoSuchAlgorithmException {
        this.json = json; this.invitations = invitations; shared = SchemaCatalogue.fromClasspath();
        var documents = new HashMap<String,String>(); JsonNode document = null;
        for (String name : List.of("member-governance-read.schema.json", "permission-decisions.schema.json", "member-invitations.schema.json")) {
            try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) {
                JsonNode value = invitations.read(input.readAllBytes());
                documents.put(value.required("$id").textValue(), json.canonical(value));
                if (name.equals("member-governance-read.schema.json")) document = value;
            }
        }
        // 读取结构引用原字段定义，未登记引用立即拒绝。
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012, builder -> builder.schemaLoaders(loaders -> loaders.schemas(iri -> {
            String value = documents.get(iri); if (value == null) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); return value;
        })));
        schema = factory.getSchema(Objects.requireNonNull(document), SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        var names = document.required("$defs").fieldNames();
        while (names.hasNext()) definition(names.next()).initializeValidators();
    }

    Query query(Map<String,String[]> parameters) {
        var value = json.object();
        for (var entry : parameters.entrySet()) {
            if (entry.getValue().length != 1) invalid();
            if (entry.getKey().equals("limit")) value.put("limit", PermissionSchema.revision(entry.getValue()[0]));
            else value.put(entry.getKey(), entry.getValue()[0]);
        }
        if (!value.has("limit")) value.put("limit", 50);
        verify("Query", value);
        return new Query(value.required("limit").intValue(), text(value, "after"), text(value, "subject_id"), text(value, "dataset_watermark"));
    }

    JsonNode envelope(String kind, JsonNode page) throws IOException {
        verify(kind + "Page", page);
        for (String field : List.of("context_revision", "auth_epoch", "governance_epoch"))
            PermissionSchema.revision(page.required("current_context").required(field).textValue());
        if (!page.required("tenant_id").equals(page.required("current_context").required("tenant_id"))) invalid();
        for (var item : page.required("items")) {
            if (PermissionSchema.revision(item.required("revision").textValue()).signum() == 0) invalid();
            if (kind.equals("Grants")) json.condition(json.canonical(item.required("condition")));
            if (kind.equals("Invitations")) invitations.roles(json.canonical(item.required("roles")));
            if (!kind.equals("Members")) {
                var scope = json.scope(item.required("scope")); json.basis(item.required("basis"));
                if (!scope.tenant_id().equals(page.required("tenant_id").textValue())) invalid();
            }
        }
        JsonNode envelope = json.tree(new InterfaceProtocol.Envelope("1.0.0", java.util.UUID.randomUUID().toString(), null, page));
        shared.validate("interfaces", envelope, "Envelope"); return envelope;
    }
    private void verify(String kind, JsonNode value) { if (!definition(kind).validate(value).isEmpty()) invalid(); }
    private JsonSchema definition(String kind) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind)); }
    private static String text(JsonNode value, String field) { return value.has(field) ? value.required(field).textValue() : null; }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
