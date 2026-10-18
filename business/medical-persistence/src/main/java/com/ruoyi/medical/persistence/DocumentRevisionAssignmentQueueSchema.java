package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class DocumentRevisionAssignmentQueueSchema {
    private final JsonSchema schema;
    private final PermissionSchema json;
    public DocumentRevisionAssignmentQueueSchema(PermissionSchema json) throws IOException {
        this.json = json;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/document-revision-assignment-queue.schema.json"))) {
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(new ObjectMapper().readTree(input),
                    SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        schema.initializeValidators();
    }
    JsonNode envelope(JsonNode data, String requestId) {
        if (!schema.validate(data).isEmpty()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, data.required("revision").asText(), data));
    }
}
