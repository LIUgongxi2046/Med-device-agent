package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import java.io.IOException;
import java.util.Objects;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;

/** Read-only deployment availability. This is never a permission or authorization grant. */
@Component
public final class DocumentRevisionDirectoryFeatureAvailability {
    public record Features(boolean document_history) { }
    public record View(String schema_version, BusinessSessionProtocol.Context current_context, Features features, Integer maximum_page_size) { }
    private final EnterpriseAccess access;
    private final ListableBeanFactory beans;
    private final PermissionSchema json;
    private final JsonSchema schema;

    public DocumentRevisionDirectoryFeatureAvailability(EnterpriseAccess access, ListableBeanFactory beans, PermissionSchema json) throws IOException {
        this.access = access;
        this.beans = beans;
        this.json = json;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/document-history-feature-availability.schema.json"))) {
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(input),
                    SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        schema.initializeValidators();
    }

    JsonNode read(ApplicationSessionIdentity.Verified subject, String context) {
        return access.execute(subject, admission -> {
            if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            // Inspect only these controller registrations; never inspect arbitrary properties or instantiate disabled services.
            var features = new Features(registered(DocumentRevisionDirectoryController.class));
            var value = json.tree(new View("1.0.0", admission.context(), features, features.document_history() ? beans.getBean(DocumentRevisionDirectoryPolicy.class).maximumPageSize() : null));
            validate(value);
            return value;
        });
    }

    private boolean registered(Class<?> type) { return beans.getBeanNamesForType(type, false, false).length > 0; }
    void validate(JsonNode value) {
        if (!schema.validate(value).isEmpty()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
}
