package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class BudgetSchema {
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;
    private final PermissionSchema json;
    private final SchemaCatalogue shared;
    private final String manifest;
    public BudgetSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        var document = mapper.readTree(resource("budgets.schema.json"));
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators(); var names = document.required("$defs").fieldNames();
        while (names.hasNext()) definition(names.next()).initializeValidators();
        var metadata = json.object(); metadata.put("schema_version", "1.0.0");
        for (String name : List.of("budgets.schema.json", "permission-decisions.schema.json", "contracts/catalogue.json"))
            metadata.put(name, DigestUtils.sha256Hex(resource(name)));
        manifest = DigestUtils.sha256Hex(json.canonical(metadata));
    }
    BudgetProtocol.Create create(byte[] bytes) throws IOException {
        var value = mapper.treeToValue(parse("Create", bytes), BudgetProtocol.Create.class);
        limits(value.limits()); ProjectSchema.text(value.reason(), 2000); return value;
    }
    BudgetProtocol.Decide decide(byte[] bytes) throws IOException {
        var value = mapper.treeToValue(parse("Decide", bytes), BudgetProtocol.Decide.class);
        positive(value.expected_revision()); limits(value.limits()); ProjectSchema.text(value.reason(), 2000); return value;
    }
    BudgetProtocol.Approval approval(String content) throws IOException {
        var value = mapper.treeToValue(parse("Approval", content.getBytes(java.nio.charset.StandardCharsets.UTF_8)), BudgetProtocol.Approval.class);
        limits(value.limits()); positive(value.authority_revision()); ProjectSchema.text(value.reason(), 2000); return value;
    }
    void limits(BudgetProtocol.Limits value) {
        verify("Limits", json.tree(value)); money(value.amount());
        for (String count : List.of(value.tokens(), value.calls(), value.research_rounds(), value.revision_rounds())) PermissionSchema.revision(count);
        PermissionAuthority.time(value.deadline_at());
    }
    JsonNode read(String content) throws IOException { return mapper.readTree(content); }
    JsonNode envelope(String kind, JsonNode value, String requestId, String revision) {
        verify(kind, value); if (revision != null) positive(revision);
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, value));
        shared.validate("interfaces", result, "Envelope");
        if (List.of("Account", "Receipt").contains(kind) && !result.required("resource_revision").equals(value.required("revision"))) invalid();
        return result;
    }
    JsonNode receipt(String content) throws IOException {
        var result = read(content); shared.validate("interfaces", result, "Envelope");
        if (!envelope("Receipt", result.required("data"), result.required("request_id").textValue(), result.required("resource_revision").textValue()).equals(result)) invalid();
        for (String field : List.of("context_revision", "auth_epoch", "governance_epoch"))
            PermissionSchema.revision(result.required("data").required("current_context").required(field).textValue());
        if (!result.required("data").required("tenant_id").equals(result.required("data").required("current_context").required("tenant_id"))) invalid();
        return result;
    }
    String hash(String path, Object request, String context, String session) throws IOException {
        var content = json.object(); content.put("method", "POST"); content.put("path", path); content.put("schema_version", "1.0.0");
        content.putNull("query"); content.put("context_header", context); content.put("session_id", session); content.set("body", json.tree(request));
        return DigestUtils.sha256Hex(json.canonical(content));
    }
    void verify(String kind, JsonNode value) { if (value == null || !definition(kind).validate(value).isEmpty()) invalid(); }
    String manifestSha256() { return manifest; }
    static BigDecimal money(String value) {
        if (value == null || !value.matches("(?:0|[1-9][0-9]{0,15})(?:\\.[0-9]{1,8})?")) invalid();
        return com.ruoyi.medical.contracts.WireValues.decimal(value, 24, 8);
    }
    private static void positive(String value) { if (PermissionSchema.revision(value).signum() == 0) invalid(); }
    private JsonNode parse(String kind, byte[] bytes) throws IOException { var value = mapper.readTree(bytes); verify(kind, value); return value; }
    private JsonSchema definition(String kind) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind)); }
    private byte[] resource(String name) throws IOException {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) { return input.readAllBytes(); }
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
