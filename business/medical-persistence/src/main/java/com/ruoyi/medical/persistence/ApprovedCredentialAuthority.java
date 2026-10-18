package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;import java.util.*;
/** Deployment supplied approved references only. Contains no key material, file creation or user-controlled secret path. */
public final class ApprovedCredentialAuthority {
 public record Binding(String tenantId,String referenceId,String revision,String provider,String endpoint,String approvalVersion,String approvalSha256,boolean active){}
 @FunctionalInterface public interface Directory { List<Binding> current(String tenantId,String referenceId); }
 private final Directory directory;
 public ApprovedCredentialAuthority(Directory directory){this.directory=Objects.requireNonNull(directory);}
 public final class Verified {private final Binding binding;private Verified(Binding binding){this.binding=binding;}public Binding binding(){return binding;}public void requireCurrent(){if(!binding.equals(require(binding.tenantId(),binding.referenceId(),binding.revision(),binding.provider(),binding.endpoint()).binding))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}}
 public Verified require(String tenant,String reference,String revision,String provider,String endpoint){WireValues.uuid(tenant);WireValues.uuid(reference);var rows=directory.current(tenant,reference);if(rows.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);var b=rows.get(0);if(!b.active()||!tenant.equals(b.tenantId())||!reference.equals(b.referenceId())||!revision.equals(b.revision())||!provider.equals(b.provider())||!endpoint.equals(b.endpoint()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);WireValues.uuid(b.approvalVersion());WireValues.hash(b.approvalSha256());return new Verified(b);}
}
