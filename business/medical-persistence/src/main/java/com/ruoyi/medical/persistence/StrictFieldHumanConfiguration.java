package com.ruoyi.medical.persistence;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.professional-runtime.document-review-enabled",havingValue="true")
public class StrictFieldHumanConfiguration {
 @Bean StrictFieldHumanSchema strictFieldHumanSchema()throws Exception{return new StrictFieldHumanSchema();}
 @Bean StrictFieldHumanBasis strictFieldHumanBasis(@Qualifier("masterDataSource")DataSource data,EnterpriseAccess access,DocumentReadService documents,ProfessionalCheckService checks,TaskStrictFieldOutputLedger outputs,StrictComparisonPolicyService policies,TemplateApplicabilityService templates,ResourceReadAuthority resources,SourceUploadTransport transport,DocumentUploadPolicy limits,TaskStrictFieldSchema strictJson,TaskStrictFieldContextService contexts,TaskStrictFieldTypedTargetService typedTargets,StrictDecimalEvidenceService decimals,TaskStrictFieldTextEvidence texts){return new StrictFieldHumanBasis(data,access,documents,checks,outputs,policies,templates,resources,transport,limits,strictJson,contexts,typedTargets,decimals,texts);}
 @Bean StrictFieldHumanService strictFieldHumanService(@Qualifier("masterDataSource")DataSource data,EnterpriseAccess access,DocumentReadService documents,ResourceReadAuthority resources,StrictFieldHumanBasis basis,StrictFieldHumanSchema schema,HumanEvidenceReviewService humans,SharedFactConsumptionAuthority facts){return new StrictFieldHumanService(data,access,documents,resources,basis,schema,humans,facts);}
}
