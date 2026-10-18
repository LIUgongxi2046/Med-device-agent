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
            org.springframework.beans.factory.ObjectProvider<FormalModelVerificationBootstrap> bootstrap,
            RunTokenPolicyStore policy,RunTokenSigningKeyStore signing,
            @Qualifier("formalRunServiceIdentity") ServiceCertificateIdentity services) {
        return new RunTokenAuthority(data,transactions,permissions,authorization,schema,policy,signing,services,bootstrap.getIfAvailable());
    }
    @Bean RunTokenPolicyStore runTokenPolicyStore(
            @Value("${medical.run-authority.configuration-directory}") Path directory,
            @Value("${medical.run-authority.configuration-owner}") String ownerName,
            @Value("${medical.run-authority.maximum-configuration-bytes}") long maximumBytes)throws Exception {
        if(!directory.isAbsolute() || ownerName.isBlank())throw new IllegalArgumentException("RUN_AUTHORITY_CONFIGURATION_REQUIRED");
        return new RunTokenPolicyStore(directory,directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(ownerName),maximumBytes);
    }
    @Bean RunTokenSigningKeyStore runTokenSigningKeyStore(RunTokenPolicyStore policy,
            @Value("${medical.run-authority.configuration-directory}") Path directory,
            @Value("${medical.run-authority.configuration-owner}") String ownerName,
            @Value("${medical.run-authority.maximum-configuration-bytes}") long maximumBytes)throws Exception {
        if(!directory.isAbsolute() || ownerName.isBlank())throw new IllegalArgumentException("RUN_AUTHORITY_CONFIGURATION_REQUIRED");
        return new RunTokenSigningKeyStore(directory,directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(ownerName),maximumBytes,policy);
    }
    @Bean(name="formalRunServiceIdentity")
    ServiceCertificateIdentity formalRunServiceIdentity(
            @Value("${medical.run-authority.configuration-directory}") Path directory,
            @Value("${medical.run-authority.configuration-owner}") String ownerName,
            @Value("${medical.run-authority.maximum-configuration-bytes}") long maximumBytes)throws Exception {
        if(!directory.isAbsolute() || ownerName.isBlank())throw new IllegalArgumentException("RUN_AUTHORITY_CONFIGURATION_REQUIRED");
        var owner=directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(ownerName);
        var trust=new ServiceCertificateTrustStore(directory,owner,maximumBytes);
        return new ServiceCertificateIdentity(trust::read);
    }
}
