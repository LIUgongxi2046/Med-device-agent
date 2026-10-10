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
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class BudgetHistorySchema {
    private final JsonSchema schema;
    private final PermissionSchema json;
    private final BudgetSchema budgets;
    private final SchemaCatalogue shared;

    public BudgetHistorySchema(PermissionSchema json, BudgetSchema budgets) throws IOException, NoSuchAlgorithmException {
        this.json = json; this.budgets = budgets; shared = SchemaCatalogue.fromClasspath();
        var mapper = JsonMapper.builder().build(); var registered = new HashMap<String,String>();
        for (String name : List.of("budgets.schema.json", "budget-history.schema.json")) {
            try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) {
                var document = mapper.readTree(input); registered.put(document.required("$id").textValue(), mapper.writeValueAsString(document));
            }
        }
        // 独立读取响应不改变历史批准的原Schema身份，未登记引用立即拒绝。
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012, builder -> builder.schemaLoaders(loaders -> loaders.schemas(iri -> {
            String content = registered.get(iri);
            if (content == null) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
            return content;
        })));
        schema = factory.getSchema(SchemaLocation.of("urn:medical:budget-history:1.0.0"),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        for (String kind : List.of("Entry", "Listing")) definition(kind).initializeValidators();
    }

    void verify(String kind, JsonNode value) throws IOException {
        if (value == null || !definition(kind).validate(value).isEmpty()) invalid();
        if ("Listing".equals(kind)) {
            positive(value.required("account_revision").textValue()); PermissionSchema.revision(value.required("total").textValue());
            for (JsonNode entry : value.required("approvals")) {
                verify("Entry", entry);
                for (String field : List.of("tenant_id", "project_id", "budget_id", "current_budget_revision", "account_revision"))
                    if (!value.required(field).equals(entry.required(field))) invalid();
            }
        } else if ("Entry".equals(kind)) {
            positive(value.required("account_revision").textValue());
            var approval = value.required("approval"); budgets.approval(json.canonical(approval));
            positive(approval.required("authority_revision").textValue());
            ProjectSchema.text(approval.required("reason").textValue(), 2000);
            for (String field : List.of("tenant_id", "project_id", "budget_id", "currency", "approver_id"))
                if (!value.required(field).equals(approval.required(field))) invalid();
            if (value.required("budget_revision").equals(approval.required("based_on_budget_revision"))
                    || value.required("is_current").booleanValue() != value.required("budget_revision").equals(value.required("current_budget_revision"))) invalid();
        } else invalid();
    }

    JsonNode envelope(String kind, JsonNode value) throws IOException {
        verify(kind, value);
        var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", java.util.UUID.randomUUID().toString(),
                value.required("account_revision").textValue(), value));
        shared.validate("interfaces", response, "Envelope"); return response;
    }
    private JsonSchema definition(String kind) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind)); }
    private static void positive(String value) { if (PermissionSchema.revision(value).signum() == 0) invalid(); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
