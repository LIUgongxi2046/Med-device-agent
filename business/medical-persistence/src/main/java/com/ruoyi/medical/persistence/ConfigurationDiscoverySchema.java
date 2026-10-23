package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
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

/** Independent read contract: adding discovery never changes immutable definition manifests. */
@Component
public final class ConfigurationDiscoverySchema {
    public record Query(int limit, String after, String q) { }
    private final JsonSchema schema;
    private final JsonSchema simulationSchema;
    private final PermissionSchema json;
    private final SchemaCatalogue shared;

    public ConfigurationDiscoverySchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        var reader = JsonMapper.builder().build(); var documents = new HashMap<String,String>();
        for (String name : List.of("configuration-discovery.schema.json", "model-configuration.schema.json", "rule-version-workflow.schema.json",
                "simulation-configuration-discovery.schema.json", "simulation-model-configuration.schema.json")) {
            try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) {
                var document = reader.readTree(input); documents.put(document.required("$id").textValue(), reader.writeValueAsString(document));
            }
        }
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012, builder -> builder.schemaLoaders(loaders -> loaders.schemas(iri -> {
            String content = documents.get(iri); if (content == null) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); return content;
        })));
        schema = factory.getSchema(SchemaLocation.of("urn:medical:configuration-discovery:1.0.0"),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        simulationSchema = factory.getSchema(SchemaLocation.of("urn:medical:simulation-configuration-discovery:1.0.0"),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        simulationSchema.initializeValidators(); definition("SimulationModelList").initializeValidators();
        schema.initializeValidators();
        for (String kind : List.of("Query", "ModelList", "RuleList", "TemplateList")) definition(kind).initializeValidators();
    }

    Query query(Map<String,String[]> parameters) {
        var value = json.object();
        for (var entry : parameters.entrySet()) {
            if (entry.getValue() == null || entry.getValue().length != 1 || entry.getValue()[0] == null) invalid();
            String text = entry.getValue()[0];
            if ("limit".equals(entry.getKey())) {
                if (!text.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid(); value.put("limit", Integer.parseInt(text));
            } else value.put(entry.getKey(), text);
        }
        verify("Query", value);
        var query = new Query(value.has("limit") ? value.required("limit").intValue() : 50,
                value.has("after") ? value.required("after").textValue() : null,
                value.has("q") ? value.required("q").textValue() : null);
        validate(query); return query;
    }

    void validate(Query query) {
        if (query == null || query.limit() < 1 || query.limit() > 100) invalid();
        if (query.after() != null && (query.after().isEmpty() || query.after().length() > 2048 || !query.after().matches("[A-Za-z0-9_-]+"))) invalid();
        if (query.q() != null) ProjectSchema.text(query.q(), 256);
    }

    void verify(String kind, JsonNode value) {
        if (value == null || !definition(kind).validate(value).isEmpty()) invalid();
        if ("Query".equals(kind)) return;
        if (!List.of("ModelList", "SimulationModelList", "RuleList", "TemplateList").contains(kind)) invalid();
        String tenant = value.required("tenant_id").textValue(); var context = value.required("current_context");
        if (!tenant.equals(context.required("tenant_id").textValue())) invalid();
        for (String field : List.of("context_revision", "auth_epoch", "governance_epoch")) PermissionSchema.revision(context.required(field).textValue());
        String previous = null;
        for (var item : value.required("items")) {
            var target = kind.endsWith("ModelList") || kind.equals("TemplateList") ? item : item.required("rule");
            String id = target.required(kind.endsWith("ModelList") ? "config_id" : kind.equals("TemplateList") ? "template_version" : "rule_version").textValue();
            if (!tenant.equals(target.required("tenant_id").textValue()) || previous != null && previous.compareTo(id) >= 0) invalid();
            if (kind.endsWith("ModelList") && !item.required("model_config_revision").equals(item.required("current_model_revision"))) invalid();
            previous = id;
        }
    }

    JsonNode envelope(String kind, JsonNode value) {
        verify(kind, value);
        var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", java.util.UUID.randomUUID().toString(),
                value.required("current_context").required("context_revision").textValue(), value));
        shared.validate("interfaces", response, "Envelope"); return response;
    }
    private JsonSchema definition(String kind) { return ("SimulationModelList".equals(kind) ? simulationSchema : schema).getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind)); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
