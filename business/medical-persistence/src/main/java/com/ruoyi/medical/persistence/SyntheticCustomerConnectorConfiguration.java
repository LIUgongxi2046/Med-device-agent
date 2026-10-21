package com.ruoyi.medical.persistence;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Opt-in demonstration only. No credential, approver, grant or rights records are created. */
@Configuration
@Profile("customer-connector-synthetic")
@ConditionalOnProperty(prefix="medical.customer-connector.synthetic",name="enabled",havingValue="true")
public class SyntheticCustomerConnectorConfiguration {
    @Bean
    SyntheticCustomerConnectorAdapter syntheticCustomerConnectorAdapter(
        @Value("${medical.customer-connector.synthetic.endpoint}") URI endpoint,
        @Value("${medical.customer-connector.synthetic.journal-directory}") Path journal)throws java.io.IOException{
        return new SyntheticCustomerConnectorAdapter(endpoint,journal,HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
}
