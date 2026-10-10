package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.WireValues;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public final class PlatformMemberIdentityStatus {
    private final JdbcTemplate jdbc;

    public PlatformMemberIdentityStatus(@Qualifier("masterDataSource") DataSource master) {
        jdbc = new JdbcTemplate(Objects.requireNonNull(master));
    }

    // 调用方先核查企业成员和绑定；平台目录只返回该成员当前身份是否有效。
    public boolean active(AuthenticatedIdentityBinding.View binding) {
        Objects.requireNonNull(binding); WireValues.uuid(binding.user_id());
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("IDENTITY_TRANSACTION_REQUIRED");
        List<String> rows;
        if ("local".equals(binding.mode())) {
            if (binding.issuer() != null || binding.subject() != null) throw new IllegalArgumentException("LOCAL_BINDING_INVALID");
            rows = jdbc.queryForList("""
                    SELECT user_id FROM platform_local_account WHERE user_id=? AND status='ACTIVE'
                        AND (locked_until IS NULL OR locked_until <= UTC_TIMESTAMP(6)) FOR UPDATE
                    """, String.class, binding.user_id());
        } else if ("oidc".equals(binding.mode())) {
            Objects.requireNonNull(binding.issuer()); Objects.requireNonNull(binding.subject());
            rows = jdbc.queryForList("""
                    SELECT user_id FROM platform_identity WHERE user_id=? AND issuer=? AND subject=? AND status='ACTIVE' FOR UPDATE
                    """, String.class, binding.user_id(), binding.issuer(), binding.subject());
        } else throw new IllegalArgumentException("IDENTITY_MODE_INVALID");
        if (rows.size() > 1 || rows.size() == 1 && !binding.user_id().equals(rows.get(0))) throw new IllegalStateException("IDENTITY_RECORD_MISMATCH");
        return !rows.isEmpty();
    }
}
