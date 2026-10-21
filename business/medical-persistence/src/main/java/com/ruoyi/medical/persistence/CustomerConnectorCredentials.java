package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.CustomerConnectorProtocol.*;
import java.net.URI;

/** Reads already-approved reference identities only. Does not load, save or create secrets. */
public final class CustomerConnectorCredentials {
    private final ApprovedCredentialAuthority authority;
    private final ApprovedCredentialAuthority.Directory directory;
    public CustomerConnectorCredentials(ApprovedCredentialAuthority.Directory directory){this.directory=java.util.Objects.requireNonNull(directory);authority=new ApprovedCredentialAuthority(directory);}
    public void requireReference(String tenant,String reference,String revision){
        var binding=one(directory.current(tenant,reference));
        authority.require(tenant,reference,revision,binding.provider(),binding.endpoint()).requireCurrent();
    }
    public void require(CustomerConnectorAdapter.Request request,CustomerConnectorAdapter.CredentialTarget target){
        if(target==null)deny();bounded(target.provider(),128);bounded(target.endpoint(),2048);
        var endpoint=URI.create(target.endpoint());if(!"https".equals(endpoint.getScheme())||endpoint.getHost()==null||endpoint.getRawUserInfo()!=null||endpoint.getRawFragment()!=null)deny();
        authority.require(request.tenantId(),text(request.definition(),"credential_reference_id"),text(request.definition(),"credential_revision"),target.provider(),target.endpoint()).requireCurrent();
    }
}
