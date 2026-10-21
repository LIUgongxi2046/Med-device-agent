package com.ruoyi.medical.persistence;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.professional-runtime.document-review-enabled",havingValue="true")
public class HumanEvidenceReviewConfiguration {
 @Bean HumanEvidenceCitationReads humanEvidenceCitationReads(EnterpriseAccess access,DocumentReadService documents,TaskFieldSourceAuthority processing,TaskResearchSourceReader literals,SourceUploadTransport transport){return new HumanEvidenceCitationReads(access,documents,processing,literals,transport);}
 @Bean HumanEvidenceReviewService humanEvidenceReviewService(@Qualifier("masterDataSource") DataSource data,EnterpriseAccess access,DocumentReadService documents,ProfessionalCheckService checks,HumanEvidenceCitationReads citations,ResourceReadAuthority resources,PermissionMapper permissions,DocumentReviewAuthority target,DocumentReviewRules rules)throws Exception{return new HumanEvidenceReviewService(data,access,documents,checks,citations,resources,permissions,target,rules);}
}
