package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper;
import java.util.Objects;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class InvitationAccess {
    private final JdbcTemplate jdbc;
    private final ApplicationSessionIdentity identities;
    private final EnterpriseAccess enterprise;
    private final TenantWatermarkMapper watermarks;
    private final TransactionTemplate transaction;
    public InvitationAccess(@Qualifier("masterDataSource") DataSource master, ApplicationSessionIdentity identities,
            EnterpriseAccess enterprise, TenantWatermarkMapper watermarks, PlatformTransactionManager manager) {
        jdbc = new JdbcTemplate(master); this.identities = identities; this.enterprise = enterprise; this.watermarks = watermarks;
        transaction = new TransactionTemplate(manager); transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    <T> T execute(ApplicationSessionIdentity.Verified subject, String invitationId, Function<Admission,T> operation) {
        Objects.requireNonNull(subject); Objects.requireNonNull(operation);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        String tenant = route(subject, invitationId);
        try (var scope = TenantScope.openInvitation(subject, tenant)) {
            return transaction.execute(status -> {
                var current = Objects.requireNonNull(watermarks.lockCurrentTenant()); scope.lockDiscovery(current);
                try {
                    enterprise.lockIdentityCoordination(subject); identities.lockCurrent(subject);
                    if (!tenant.equals(route(subject, invitationId))) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
                    return operation.apply(new Admission(subject, tenant, current));
                } finally { scope.finishTransaction(); }
            });
        }
    }
    private String route(ApplicationSessionIdentity.Verified subject, String id) {
        var rows = jdbc.query("SELECT tenant_id FROM platform_invitation_route WHERE user_id=? AND invitation_id=?",
                (row, ordinal) -> row.getString(1), subject.user().user_id(), id);
        if (rows.size() != 1) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        return rows.get(0);
    }
    record Admission(ApplicationSessionIdentity.Verified subject, String tenantId, TenantWatermarks watermarks) { }
}
