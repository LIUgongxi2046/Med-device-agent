package com.ruoyi.medical.persistence;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.professional-runtime.document-review-enabled",havingValue="true")
public class DocumentReviewConfirmationConfiguration {
 @Bean DocumentReviewConfirmationService documentReviewConfirmationService(@Qualifier("masterDataSource") DataSource data,EnterpriseAccess access,DocumentReadService documents,ProfessionalCheckService checks,HumanEvidenceReviewService humans,StrictFieldHumanService strict,DocumentReviewReturnService returns,ResourceReadAuthority authority,PermissionMapper permissions)throws Exception{return new DocumentReviewConfirmationService(data,access,documents,checks,humans,strict,returns,authority,permissions);}
}
