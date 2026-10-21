package com.ruoyi.medical.persistence;

import java.nio.file.Path;
import org.springframework.boot.context.properties.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
@EnableConfigurationProperties(CustomerConnectorConfiguration.Policy.class)
public class CustomerConnectorConfiguration {
    @ConfigurationProperties("medical.customer-connector")
    public record Policy(Path approvedCredentialManifest) { }
    @Bean
    @ConditionalOnProperty(prefix="medical.customer-connector",name="approved-credential-manifest")
    CustomerConnectorCredentials customerConnectorCredentials(Policy policy){
        return new CustomerConnectorCredentials(new ConfiguredCredentialDirectory(policy.approvedCredentialManifest()));
    }
}
