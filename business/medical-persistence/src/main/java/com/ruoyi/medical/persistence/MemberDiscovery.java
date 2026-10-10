package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.TenantMemberMapper;
import com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class MemberDiscovery {
    private final TransactionTemplate transaction;
    private final TenantWatermarkMapper watermarks;
    private final TenantMemberMapper members;

    public MemberDiscovery(PlatformTransactionManager manager, TenantWatermarkMapper watermarks, TenantMemberMapper members) {
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.watermarks = watermarks; this.members = members;
    }

    Found read(ApplicationSessionIdentity.Verified subject, BusinessSessionDirectory.Route route) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        try (var scope = TenantScope.openDiscovery(subject, route.tenantId())) {
            return transaction.execute(status -> {
                TenantWatermarks current = watermarks.lockCurrentTenant();
                scope.lockDiscovery(current);
                try {
                    List<TenantMemberRow> found = members.currentMember(subject.user().user_id(), route.memberId());
                    if (found.isEmpty()) return null;
                    if (found.size() != 1) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
                    var member = found.get(0);
                    if (!subject.user().user_id().equals(member.userId()) || !route.tenantId().equals(member.tenantId())
                            || !route.memberId().equals(member.memberId())) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
                    return new Found(new BusinessSessionProtocol.Member(member.tenantId(), member.memberId(), member.status(),
                            member.validFrom().toInstant(ZoneOffset.UTC).toString(),
                            member.validTo() == null ? null : member.validTo().toInstant(ZoneOffset.UTC).toString(),
                            member.revision().toString()), current);
                } finally { scope.finishTransaction(); }
            });
        }
    }
    record Found(BusinessSessionProtocol.Member member, TenantWatermarks watermarks) { }
}
