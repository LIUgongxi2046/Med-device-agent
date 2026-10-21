package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Deployment readiness only; never an action grant, context approval or execution readiness claim. */
@Component
public final class FormalEvaluationContextFeatureAvailability {
    private final EnterpriseAccess access;
    private final ListableBeanFactory beans;
    private final Environment environment;
    private final PermissionSchema json;
    private final FormalEvaluationContextHttpSchema schema;
    public FormalEvaluationContextFeatureAvailability(EnterpriseAccess access, ListableBeanFactory beans, Environment environment,
            PermissionSchema json, FormalEvaluationContextHttpSchema schema) {
        this.access = access; this.beans = beans; this.environment = environment; this.json = json; this.schema = schema;
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context) {
        return access.execute(subject, admission -> {
            FormalEvaluationContextService.current(admission, context);
            boolean configured = environment.getProperty("medical.formal-evaluation-context.enabled", Boolean.class, false);
            boolean enabled = configured && actual(FormalEvaluationContextController.class) && actual(FormalEvaluationContextService.class)
                    && actual(FormalEvaluationContextStore.class) && actual(FormalEvaluationContextDependencies.class)
                    && actual(FormalEvaluationContextSchema.class) && actual(FormalEvaluationContextReadAuthority.class);
            var result = json.object().put("schema_version", "1.0.0").put("maximum_page_size", 20);
            result.set("current_context", json.tree(admission.context()));
            result.putObject("features").put("context_bindings", enabled).put("bound_preparation", false);
            if (enabled) result.putNull("unavailable_reason");
            else result.put("unavailable_reason", configured ? "DEPENDENCY_UNAVAILABLE" : "FEATURE_DISABLED");
            return schema.validate("Availability", result);
        });
    }
    private <T> boolean actual(Class<T> type) {
        try { return beans.getBeanProvider(type).getIfUnique() != null; }
        catch (BeansException unavailable) { return false; }
    }
}
