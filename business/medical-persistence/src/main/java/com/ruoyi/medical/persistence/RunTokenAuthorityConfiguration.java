package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.RunTokenPolicyStore;
import com.ruoyi.medical.identity.RunTokenSigningKeyStore;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.identity.ServiceCertificateTrustStore;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix="medical.run-authority",name="enabled",havingValue="true")
class RunTokenAuthorityConfiguration {
    @Bean RunTokenAuthority runTokenAuthority(@Qualifier("masterDataSource") DataSource data,TenantTransaction transactions,
            PermissionMapper permissions,PermissionAuthority authorization,RunTokenAuthoritySchema schema,
            @Value("${medical.run-authority.configuration-directory}") Path directory,
            @Value("${medical.run-authority.configuration-owner}") String ownerName,
            @Value("${medical.run-authority.maximum-configuration-bytes}") long maximumBytes) throws Exception {
        if(!directory.isAbsolute() || ownerName.isBlank()) throw new IllegalArgumentException("RUN_AUTHORITY_CONFIGURATION_REQUIRED");
        var owner=directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(ownerName);
        var policy=new RunTokenPolicyStore(directory,owner,maximumBytes);
        var signing=new RunTokenSigningKeyStore(directory,owner,maximumBytes,policy);
        var trust=new ServiceCertificateTrustStore(directory,owner,maximumBytes);
        return new RunTokenAuthority(data,transactions,permissions,authorization,schema,policy,signing,new ServiceCertificateIdentity(trust::read));
    }
}
