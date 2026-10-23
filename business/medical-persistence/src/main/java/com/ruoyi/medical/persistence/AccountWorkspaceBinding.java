package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class AccountWorkspaceBinding {
    private final JdbcTemplate jdbc;
    private final IdentityDirectoryPolicy policy;

    public AccountWorkspaceBinding(@Qualifier("masterDataSource") DataSource source, IdentityDirectoryPolicy policy) {
        this.jdbc = new JdbcTemplate(source); this.policy = policy;
    }

    String tenant(String actor) {
        WireValues.uuid(actor);
        var values = jdbc.query("SELECT tenant_id FROM platform_account_workspace WHERE user_id=?",
                (row, ordinal) -> row.getString("tenant_id"), actor);
        if (values.size() > 1) throw new IllegalStateException("ACCOUNT_WORKSPACE_DUPLICATE");
        return values.isEmpty() ? null : values.get(0);
    }

    void remember(String actor, String tenant) {
        WireValues.uuid(actor); WireValues.uuid(tenant);
        jdbc.update("""
                INSERT INTO platform_account_workspace
                    (user_id,tenant_id,created_by,created_at,classification,revision,updated_at)
                VALUES (?,?,?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6)) AS incoming
                ON DUPLICATE KEY UPDATE revision=IF(platform_account_workspace.tenant_id<>incoming.tenant_id,
                    platform_account_workspace.revision+1,platform_account_workspace.revision),
                    updated_at=IF(platform_account_workspace.tenant_id<>incoming.tenant_id,
                    UTC_TIMESTAMP(6),platform_account_workspace.updated_at),tenant_id=incoming.tenant_id
                """, actor, tenant, actor, policy.classification());
    }
}
