package com.ruoyi.medical.persistence;
import javax.sql.DataSource;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.professional-runtime.document-review-enabled",havingValue="true")
public class ProfessionalIssueConfiguration {
 @Bean ProfessionalIssueSchema professionalIssueSchema()throws Exception{return new ProfessionalIssueSchema();}
 @Bean ProfessionalCheckService professionalCheckService(@Qualifier("masterDataSource")DataSource data,EnterpriseAccess access,DocumentReadService documents,DocumentReviewAuthority target,DocumentReviewRules rules,DocumentReviewExecutionService jobs,ResourceReadAuthority resources,SourceUploadTransport transport,DocumentUploadPolicy policy,DependencyLineage lineage,VersionResourceAuthority versions,ArtifactRegistryMapper artifacts,DependencyManifestSchema manifests)throws Exception{return new ProfessionalCheckService(data,access,documents,target,rules,jobs,resources,transport,policy,lineage,versions,artifacts,manifests);}
 @Bean ProfessionalIssueService professionalIssueService(@Qualifier("masterDataSource")DataSource data,EnterpriseAccess access,DocumentReadService documents,ResourceReadAuthority resources,ProfessionalCheckService checks,ProfessionalIssueSchema schema){return new ProfessionalIssueService(data,access,documents,resources,checks,schema);}
 @Bean CandidateProfessionalCheckReadService candidateProfessionalCheckReadService(@Qualifier("masterDataSource")DataSource data,EnterpriseAccess access,ResourceReadAuthority resources,SourceReadAuthority sources,SourceUploadTransport transport,DocumentUploadPolicy policy,DependencyLineage lineage,VersionResourceAuthority versions,ArtifactRegistryMapper artifacts,DependencyManifestSchema manifests,DocumentReadService documents)throws Exception{return new CandidateProfessionalCheckReadService(data,access,resources,sources,transport,policy,lineage,versions,artifacts,manifests,documents);}
 @Bean CheckQueryService checkQueryService(@Qualifier("masterDataSource")DataSource data,EnterpriseAccess access,ProfessionalCheckService checks,CandidateProfessionalCheckReadService candidates,org.springframework.beans.factory.ObjectProvider<CandidateSetCheckReadService> assemblies,org.springframework.beans.factory.ObjectProvider<DocumentTechnicalCheckService> technical)throws Exception{return new CheckQueryService(data,access,checks,candidates,assemblies,technical);}
}
