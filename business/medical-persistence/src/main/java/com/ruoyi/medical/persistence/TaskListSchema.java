package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.PathType;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class TaskListSchema {
    private final PermissionSchema json;
    private final SchemaCatalogue shared;
    private final JsonSchema schema;

    public TaskListSchema(PermissionSchema json, TaskInputSchema inputs) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("task-list.schema.json"))) {
            schema = shared.compileApiSchema(inputs.read(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
        }
    }

    TaskProtocol.Query query(Map<String,String[]> parameters) {
        var value = json.object();
        for (var entry : parameters.entrySet()) {
            if (entry.getValue().length != 1) invalid();
            if (entry.getKey().equals("page") || entry.getKey().equals("page_size")) {
                var number = PermissionSchema.revision(entry.getValue()[0]);
                value.put(entry.getKey(), number);
            } else value.put(entry.getKey(), entry.getValue()[0]);
        }
        if (!value.has("page")) value.put("page", 1);
        if (!value.has("page_size")) value.put("page_size", 20);
        verify("Filter", value);
        String watermark = text(value, "dataset_watermark"), owner = text(value, "owner_id");
        if (watermark != null) WireValues.revision(watermark);
        if (owner != null) CollectionSchema.id(owner);
        var from = timestamp(text(value, "updated_from")); var to = timestamp(text(value, "updated_to"));
        if (from != null && to != null && from.isAfter(to)) invalid();
        return new TaskProtocol.Query(value.required("page").intValue(), value.required("page_size").intValue(),
            watermark, text(value, "filter_sha256"), text(value, "status"), owner, from, to);
    }

    JsonNode envelope(JsonNode data, String requestId, String watermark) {
        verify("Page", data); WireValues.revision(watermark);
        var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, watermark, data));
        shared.validate("interfaces", response, "Envelope"); return response;
    }
    void verify(String definition, JsonNode value) {
        if (!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition))
                .validate(Objects.requireNonNull(value)).isEmpty()) invalid();
    }
    private static LocalDateTime timestamp(String value) {
        if (value == null) return null;
        var time = OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        if (time.getNano() % 1000 != 0) invalid();
        return time;
    }
    private static String text(JsonNode value, String name) { return value.has(name) ? value.required(name).textValue() : null; }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
