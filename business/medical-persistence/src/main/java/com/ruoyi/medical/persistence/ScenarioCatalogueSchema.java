package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.PathType;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class ScenarioCatalogueSchema {
    private final PermissionSchema json;
    private final SchemaCatalogue shared;
    private final JsonSchema schema;
    record Query(int limit, String projectId, String documentType, String after, String datasetWatermark) { }

    public ScenarioCatalogueSchema(PermissionSchema json, TaskInputSchema inputs) throws IOException, java.security.NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/scenario-catalogue.schema.json"))) {
            schema = shared.compileApiSchema(inputs.read(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        }
        schema.initializeValidators(); definition("Query").initializeValidators();
    }

    Query query(Map<String,String[]> parameters) {
        var value = json.object(); value.put("limit", 20);
        parameters.forEach((name, entries) -> {
            if (entries.length != 1) invalid();
            if ("limit".equals(name)) {
                if (!entries[0].matches("[1-9][0-9]{0,2}")) invalid();
                value.put(name, Integer.parseInt(entries[0]));
            } else value.put(name, entries[0]);
        });
        if (!definition("Query").validate(value).isEmpty()) invalid();
        return new Query(value.required("limit").intValue(), text(value, "project_id"), text(value, "document_type"),
                text(value, "after"), text(value, "dataset_watermark"));
    }

    JsonNode envelope(JsonNode value, String requestId) {
        if (!schema.validate(value).isEmpty()) invalid();
        var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, null, value));
        shared.validate("interfaces", response, "Envelope"); return response;
    }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private static String text(JsonNode value, String name) { return value.has(name) ? value.required(name).textValue() : null; }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
