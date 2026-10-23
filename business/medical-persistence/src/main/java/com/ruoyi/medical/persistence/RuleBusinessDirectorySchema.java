package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public final class RuleBusinessDirectorySchema {
    public record Query(int limit, String after, String q, String dimension, String handling, String status) {
        ConfigurationDiscoverySchema.Query discovery() { return new ConfigurationDiscoverySchema.Query(limit, after, q); }
    }
    private final JsonSchema schema;
    private final PermissionSchema json;
    private final SchemaCatalogue shared;

    public RuleBusinessDirectorySchema(PermissionSchema json) throws IOException, java.security.NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        var reader = JsonMapper.builder().build(); var documents = new HashMap<String,String>();
        for (String name : List.of("rule-business-directory.schema.json", "rule-business-definition.schema.json",
                "configuration-discovery.schema.json", "rule-version-workflow.schema.json", "model-configuration.schema.json")) {
            try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/" + name))) {
                var value = reader.readTree(input); documents.put(value.required("$id").textValue(), reader.writeValueAsString(value));
            }
        }
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012, builder -> builder.schemaLoaders(loaders -> loaders.schemas(iri -> {
            String value = documents.get(iri); if (value == null) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); return value;
        })));
        schema = factory.getSchema(SchemaLocation.of("urn:medical:rule-business-directory:1.0.0"), SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        for (String kind : List.of("Query", "Filters", "Directory")) definition(kind).initializeValidators();
    }
    Query query(Map<String,String[]> parameters) {
        var value = json.object();
        for (var entry : parameters.entrySet()) {
            if (entry.getValue() == null || entry.getValue().length != 1 || entry.getValue()[0] == null) invalid();
            String text = entry.getValue()[0];
            if (entry.getKey().equals("limit")) {
                if (!text.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid(); value.put("limit", Integer.parseInt(text));
            } else value.put(entry.getKey(), text);
        }
        verify("Query", value);
        var query = new Query(value.has("limit") ? value.required("limit").intValue() : 25, text(value,"after"), text(value,"q"), text(value,"dimension"), text(value,"handling"), text(value,"status"));
        if (query.q() != null) ProjectSchema.text(query.q(), 256);
        return query;
    }
    JsonNode filters(Query query) {
        var value = json.object().put("limit", query.limit());
        value.set("q", json.tree(query.q())); value.set("dimension", json.tree(query.dimension()));
        value.set("handling", json.tree(query.handling())); value.set("status", json.tree(query.status()));
        verify("Filters", value); return value;
    }
    JsonNode envelope(JsonNode value) {
        verify("Directory", value);
        String tenant = value.required("tenant_id").asText();
        if (!tenant.equals(value.required("current_context").required("tenant_id").asText())) mismatch();
        var filters = value.required("query"); String previous = null;
        if (value.required("items").size() > filters.required("limit").intValue()) mismatch();
        for (var item : value.required("items")) {
            var rule = item.required("resource").required("rule"); var business = item.required("business");
            String id = rule.required("rule_version").asText();
            if (!tenant.equals(rule.required("tenant_id").asText()) || previous != null && previous.compareTo(id) >= 0) mismatch();
            if (!filters.required("status").isNull() && !filters.required("status").equals(rule.required("status"))) mismatch();
            if (!business.isNull()) {
                for (String key : List.of("tenant_id","rule_version","created_by","created_at")) if (!rule.required(key).equals(business.required(key))) mismatch();
                if (!rule.required("content_sha256").equals(business.required("rule_content_sha256"))) mismatch();
            }
            for (String key : List.of("dimension","handling")) if (!filters.required(key).isNull()
                    && (business.isNull() || !filters.required(key).equals(business.required("definition").required(key)))) mismatch();
            previous = id;
        }
        var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", UUID.randomUUID().toString(), value.required("current_context").required("context_revision").asText(), value));
        shared.validate("interfaces", response, "Envelope"); return response;
    }
    private JsonSchema definition(String kind) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind)); }
    private void verify(String kind, JsonNode value) { if (!definition(kind).validate(value).isEmpty()) invalid(); }
    private static String text(JsonNode value, String key) { return value.has(key) ? value.required(key).asText() : null; }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
