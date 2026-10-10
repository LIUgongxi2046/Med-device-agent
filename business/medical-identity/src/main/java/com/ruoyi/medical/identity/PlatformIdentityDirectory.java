package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "oidc")
public final class PlatformIdentityDirectory {
    private final OidcPolicy oidcPolicy;
    private final IdentityDirectoryPolicy directoryPolicy;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PlatformIdentityDirectory(@Qualifier("masterDataSource") DataSource master,
            OidcPolicy oidcPolicy, IdentityDirectoryPolicy directoryPolicy) {
        this.oidcPolicy = Objects.requireNonNull(oidcPolicy);
        this.directoryPolicy = Objects.requireNonNull(directoryPolicy);
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(master));
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(master));
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    PlatformIdentity registerCurrentIdentity() {
        VerifiedOidcIdentity identity = VerifiedOidcIdentity.requireCurrent(oidcPolicy);
        requireIndependentTransaction();
        return transaction.execute(status -> {
            String userId = UUID.randomUUID().toString();
            // 重复认证保留当前用户及停用状态，禁止覆盖已有身份或重新启用账号。
            jdbc.update("""
                    INSERT INTO platform_identity
                      (identity_id, issuer, subject, user_id, status, created_by, created_at, classification, revision, updated_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, UTC_TIMESTAMP(6), ?, 1, UTC_TIMESTAMP(6))
                    ON DUPLICATE KEY UPDATE identity_id = identity_id
                    """, UUID.randomUUID().toString(), identity.issuer(), identity.subject(), userId, userId,
                    directoryPolicy.classification());
            return readCurrent(identity, true);
        });
    }

    PlatformIdentity requireCurrentIdentity() {
        VerifiedOidcIdentity identity = VerifiedOidcIdentity.requireCurrent(oidcPolicy);
        requireIndependentTransaction();
        return transaction.execute(status -> readCurrent(identity, false));
    }

    private PlatformIdentity readCurrent(VerifiedOidcIdentity identity, boolean registering) {
        return jdbc.queryForObject("""
                SELECT identity_id, issuer, subject, user_id, status, revision
                  FROM platform_identity WHERE issuer = ? AND subject = ? FOR UPDATE
                """, (row, ordinal) -> {
                    if (!identity.issuer().equals(row.getString("issuer"))
                            || !identity.subject().equals(row.getString("subject"))) {
                        throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_FAILED.name()));
                    }
                    if (!"ACTIVE".equals(row.getString("status"))) {
                        if (registering) {
                            throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_FAILED.name()));
                        }
                        throw new AccessDeniedException(ErrorCode.ACCESS_DENIED.name());
                    }
                    return new PlatformIdentity(row.getString("identity_id"), row.getString("user_id"),
                            new BigInteger(row.getString("revision")), identity);
                }, identity.issuer(), identity.subject());
    }

    PlatformIdentity lockCurrentIdentity() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("IDENTITY_TRANSACTION_REQUIRED");
        return readCurrent(VerifiedOidcIdentity.requireCurrent(oidcPolicy), false);
    }

    private static void requireIndependentTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("IDENTITY_TRANSACTION_ALREADY_ACTIVE");
        }
    }
}
