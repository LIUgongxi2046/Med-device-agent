package com.ruoyi.medical.persistence;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
/** One reconciliation service for mutually exclusive observation-only or full TASK routes. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnExpression("${medical.professional-runtime.observations-enabled:false} || ${medical.professional-runtime.task-enabled:false}")
public class ProfessionalRuntimeObservationConfiguration {
 @Bean ProfessionalRuntimeObservations professionalRuntimeObservations(@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identities,TenantTransaction transactions,@Qualifier("masterDataSource")DataSource data,PermissionSchema json){return new ProfessionalRuntimeObservations(identities,transactions,data,json);}
}
