package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public final class OperationSchema {
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final SchemaCatalogue shared;
    private final PermissionSchema json;

    public OperationSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
    }

    InterfaceProtocol.OperationLookup lookup(byte[] body) throws IOException {
        var node = mapper.readTree(body); if (node == null) invalid(); shared.validate("interfaces", node, "OperationLookup");
        var value = mapper.treeToValue(node, InterfaceProtocol.OperationLookup.class);
        if (!value.operationId().matches("[!-~]{1,64}")) invalid();
        return value;
    }

    JsonNode result(InterfaceProtocol.LookupStatus status, InterfaceProtocol.Operation operation, String requestId) {
        if ((status == InterfaceProtocol.LookupStatus.FOUND) != (operation != null)) invalid();
        if (operation != null) verifyOperation(operation);
        return envelope("OperationLookupResult", new InterfaceProtocol.OperationLookupResult(status, operation), requestId);
    }

    JsonNode operation(InterfaceProtocol.Operation value, String requestId) {
        verifyOperation(value); return envelope("Operation", value, requestId);
    }

    private void verifyOperation(InterfaceProtocol.Operation value) {
        if (value == null || !value.statusUrl().equals("/api/operations/" + value.operationId())) invalid();
        shared.validate("interfaces", json.tree(value), "Operation");
    }

    private JsonNode envelope(String definition, Object value, String requestId) {
        var data = json.tree(value); shared.validate("interfaces", data, definition);
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, null, data));
        shared.validate("interfaces", result, "Envelope"); return result;
    }

    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
