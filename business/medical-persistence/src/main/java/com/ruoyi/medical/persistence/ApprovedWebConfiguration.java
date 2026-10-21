package com.ruoyi.medical.persistence;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
@Configuration
@EnableConfigurationProperties(ApprovedWebConfiguration.Policy.class)
public class ApprovedWebConfiguration {
 @ConfigurationProperties("medical.approved-web")
 public record Policy(boolean enabled,Path journalDirectory,Path approvedCredentialManifest,List<ApprovedWebGateway.Route> routes){}
 @Bean @ConditionalOnProperty(prefix="medical.approved-web",name="enabled",havingValue="true")
 ApprovedWebGateway approvedWebGateway(Policy p){if(p.journalDirectory()==null||p.approvedCredentialManifest()==null||p.routes()==null||p.routes().isEmpty())throw new IllegalArgumentException("APPROVED_WEB_DEPLOYMENT_REQUIRED");return new ApprovedWebGateway(p.journalDirectory(),p.routes(),new ApprovedCredentialAuthority(new ConfiguredCredentialDirectory(p.approvedCredentialManifest())));}
}
