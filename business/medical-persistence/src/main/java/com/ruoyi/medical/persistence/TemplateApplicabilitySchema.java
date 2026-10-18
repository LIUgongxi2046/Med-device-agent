package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class TemplateApplicabilitySchema {
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();
    private final SchemaCatalogue shared;
    private final PermissionSchema json;
    private final String ruleManifest;

    public TemplateApplicabilitySchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        var identity = json.object(); identity.put("schema_version", "1.0.0");
        for (String path : List.of("rule-expressions.schema.json", "templates.schema.json", "backend-design/interfaces.schema.yaml")) {
            try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(path))) {
                byte[] bytes = input.readAllBytes(); identity.put(path, DigestUtils.sha256Hex(bytes));
                if (path.equals("rule-expressions.schema.json")) shared.requirePublished("urn:medical:rule-expressions:1.0.0", DigestUtils.sha256Hex(bytes));
            }
        }
        ruleManifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    TemplateApplicabilityProtocol.Expression expression(JsonNode node) throws IOException {
        shared.validate("rule-expressions", node, "Expression");
        var result = reader.treeToValue(node, TemplateApplicabilityProtocol.Expression.class);
        ProjectSchema.text(result.expression(), 10000);
        return result;
    }

    TemplateApplicabilityProtocol.RuleContent rule(TemplateApplicabilityProtocol.RuleRow row) throws IOException {
        var value = json.object(); value.put("schema_version", row.schemaVersion()); value.put("engine_revision", row.engineRevision());
        value.set("expression", reader.readTree(row.expression())); value.set("basis_manifest", reader.readTree(row.basisManifest()));
        var result = content(value);
        if (!DigestUtils.sha256Hex(json.canonical(value)).equals(row.contentHash()) || !row.contentHash().equals(row.registryHash())
                || !ruleManifest.equals(row.manifest())) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        return result;
    }

    TemplateApplicabilityProtocol.RuleContent content(JsonNode node) throws IOException {
        shared.validate("rule-expressions", node, "Content");
        var result = reader.treeToValue(node, TemplateApplicabilityProtocol.RuleContent.class);
        expression(node.required("expression"));
        var bases = new HashMap<String,PermissionProtocol.Basis>();
        for (var basis : result.basis_manifest()) if (bases.putIfAbsent(basis.version_id(), basis) != null) invalid();
        var constants = new java.util.HashSet<String>();
        for (var constant : result.expression().constants()) {
            if (!constants.add(constant.key())) invalid();
            for (var basis : constant.basis_refs()) if (!basis.equals(bases.get(basis.version_id()))) invalid();
        }
        return result;
    }

    TemplateApplicabilityProtocol.Query query(Map<String,String[]> parameters) {
        for (var entry : parameters.entrySet())
            if (!List.of("document_type", "profile_revision", "page", "page_size", "dataset_watermark", "filter_sha256").contains(entry.getKey())
                    || entry.getValue().length != 1) invalid();
        var node = json.object();
        parameters.forEach((key, value) -> { if (!key.equals("page") && !key.equals("page_size")) node.put(key, value[0]); });
        int page = number(parameters, "page", 1, Integer.MAX_VALUE), size = number(parameters, "page_size", 20, 100);
        node.put("page", page); node.put("page_size", size); shared.validate("template-applicability", node, "Query");
        return new TemplateApplicabilityProtocol.Query(node.required("document_type").textValue(), node.required("profile_revision").textValue(),
                page, size, node.path("dataset_watermark").textValue(), node.path("filter_sha256").textValue());
    }
    private static int number(Map<String,String[]> values, String key, int initial, int maximum) {
        if (!values.containsKey(key)) return initial;
        var value = PermissionSchema.revision(values.get(key)[0]);
        if (value.signum() <= 0 || value.compareTo(java.math.BigInteger.valueOf(maximum)) > 0) invalid();
        return value.intValueExact();
    }
    JsonNode envelope(JsonNode data, String requestId, String revision) {
        verify("Page", data);
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, data));
        shared.validate("interfaces", result, "Envelope"); return result;
    }
    void verify(String name, JsonNode value) { shared.validate("template-applicability", value, name); }
    String ruleManifestSha256() { return ruleManifest; }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
