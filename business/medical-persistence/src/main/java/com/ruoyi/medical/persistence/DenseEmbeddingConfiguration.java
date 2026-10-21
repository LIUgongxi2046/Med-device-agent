package com.ruoyi.medical.persistence;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DenseEmbeddingConfiguration.Policy.class)
public class DenseEmbeddingConfiguration {
    @ConfigurationProperties("medical.dense-embedding")
    public record Policy(boolean enabled, Path journalDirectory, Path approvedCredentialManifest,
            List<DenseEmbeddingGateway.Route> routes) { }
    @Bean
    @ConditionalOnProperty(prefix="medical.dense-embedding",name="enabled",havingValue="true")
    DenseEmbeddingGateway denseEmbeddingGateway(Policy policy, PermissionSchema json) {
        if(policy.journalDirectory()==null||policy.approvedCredentialManifest()==null||policy.routes()==null||policy.routes().isEmpty())
            throw new IllegalArgumentException("DENSE_EMBEDDING_APPROVED_DEPLOYMENT_INPUT_REQUIRED");
        var credentials=new ApprovedCredentialAuthority(new ConfiguredCredentialDirectory(policy.approvedCredentialManifest()));
        return new DenseEmbeddingGateway(policy.journalDirectory(),policy.routes(),credentials,json);
    }
}
