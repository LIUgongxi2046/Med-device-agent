package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.identity.PlatformMemberIdentityStatus;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.context.annotation.*;

/** Explicitly opt-in authoring/read surface. No execution or approval is seeded by this configuration. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "medical.formal-evaluation-context", name = "enabled", havingValue = "true", matchIfMissing = false)
@Import(FormalEvaluationContextConfiguration.WiringSelector.class)
public class FormalEvaluationContextConfiguration {
    /** Evaluate optional domain prerequisites after ordinary component definitions have been registered. */
    public static final class WiringSelector implements DeferredImportSelector {
        @Override public String[] selectImports(AnnotationMetadata metadata) { return new String[]{Wiring.class.getName()}; }
    }
    // This is intentionally not a component-scanned @Configuration: the deferred import owns registration.
    // Identity, tenant access, SQL/mappers and permission services are mandatory application infrastructure.
    @ConditionalOnBean({FormalAgentCandidateAuthority.class, AgentExecutionPolicyAuthority.class,
            FormalCapabilityIdentityAuthority.class, FormalRuntimeCapabilityIdentityAuthority.class, AgentPublicationDependencies.class})
    public static class Wiring {
        @Bean
        FormalEvaluationContextSchema formalEvaluationContextSchema(PermissionSchema json) throws Exception {
            return new FormalEvaluationContextSchema(json);
        }
        @Bean
        FormalEvaluationContextDependencies formalEvaluationContextDependencies(FormalAgentCandidateAuthority candidates,
                AgentExecutionPolicyAuthority policies, FormalCapabilityIdentityAuthority capabilities,
                FormalRuntimeCapabilityIdentityAuthority components, PermissionSchema json) throws Exception {
            return new FormalEvaluationContextDependencies(candidates, policies, capabilities, components, SchemaCatalogue.fromClasspath(), json);
        }
        @Bean
        FormalEvaluationContextStore formalEvaluationContextStore(DataSource data, PermissionSchema json,
                FormalEvaluationContextSchema schema, FormalEvaluationContextDependencies dependencies,
                ResourceReadAuthority resources, PermissionMapper permissions, PermissionAuthority grants,
                PlatformMemberIdentityStatus identities) {
            return new FormalEvaluationContextStore(data, json, schema, dependencies, resources, permissions, grants, identities);
        }
        @Bean
        FormalEvaluationContextReadAuthority formalEvaluationContextReadAuthority(FormalAgentCandidateAuthority candidates,
                AgentExecutionPolicyAuthority policies, AgentPublicationDependencies provenance, PermissionSchema json) {
            return new FormalEvaluationContextReadAuthority(candidates, policies, provenance, json);
        }
        @Bean
        FormalEvaluationContextService formalEvaluationContextService(EnterpriseAccess access, FormalEvaluationContextStore store,
                FormalEvaluationContextHttpSchema schema, AgentAuthoringCursor cursors, PermissionSchema json, FormalEvaluationContextReadAuthority reads) {
            return new FormalEvaluationContextService(access, store, schema, cursors, json, reads);
        }
    }
}
