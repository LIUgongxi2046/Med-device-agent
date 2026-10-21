package com.ruoyi.medical.persistence;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
/** Deliberately off unless deployment supplies the real OIDC registration and encryption key. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.signing.enabled",havingValue="true")
public class DocumentSigningConfiguration {
 @Bean SigningOidcProvider signingOidcProvider(ClientRegistrationRepository registrations,@Value("${medical.signing.pkce-encryption-key-base64}") String key){return new SigningOidcProvider(registrations,key);}
 @Bean DocumentSigningService documentSigningService(@Qualifier("masterDataSource") DataSource data,EnterpriseAccess access,ResourceReadAuthority authority,PermissionMapper permissions,DocumentReadService documents,DocumentReviewConfirmationService reviews,DocumentReviewAuthority reviewAuthority,TaskService tasks,TaskPlanService plans,VersionChangeImpactGate impacts,SigningOidcProvider provider)throws Exception{return new DocumentSigningService(data,access,authority,permissions,documents,reviews,reviewAuthority,tasks,plans,impacts,provider);}
}
