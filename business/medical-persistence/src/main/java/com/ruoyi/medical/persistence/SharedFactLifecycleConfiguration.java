package com.ruoyi.medical.persistence;

import javax.sql.DataSource;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
public class SharedFactLifecycleConfiguration {
 @Bean SharedFactLifecycleService sharedFactLifecycleService(@org.springframework.beans.factory.annotation.Qualifier("masterDataSource") DataSource data,EnterpriseAccess access,ResourceReadAuthority resources,PermissionMapper permissions,DocumentReadService documents,TaskFieldSourceAuthority processing,TaskResearchSourceReader literals,ObjectProvider<SourceUploadTransport> transport,SharedFactReplacementImpact impacts,SharedFactScopeAuthority scopes)throws Exception{
  // Evidence reads fail closed when original-object transport is not configured.
  return new SharedFactLifecycleService(data,access,resources,permissions,documents,new HumanEvidenceCitationReads(access,documents,processing,literals,transport.getIfAvailable()),impacts,scopes);
 }
}
