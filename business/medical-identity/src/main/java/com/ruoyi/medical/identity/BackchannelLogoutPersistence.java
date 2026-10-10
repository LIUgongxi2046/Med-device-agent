package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "oidc")
public final class BackchannelLogoutPersistence {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RedisIndexedSessionRepository sessions;
    private final BackchannelLogoutVerifier verifier;
    private final OidcClientLedger clients;
    private final LogoutPersistencePolicy policy;
    private final String classification;

    public BackchannelLogoutPersistence(@Qualifier("masterDataSource") DataSource master,
            RedisIndexedSessionRepository sessions, BackchannelLogoutVerifier verifier, OidcClientLedger clients,
            LogoutPersistencePolicy policy, IdentityDirectoryPolicy directory) {
        jdbc = new JdbcTemplate(master);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(master));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.sessions = sessions;
        this.verifier = verifier;
        this.clients = clients;
        this.policy = policy;
        classification = directory.classification();
    }

    String process(String raw) throws IOException, ParseException {
        independent();
        var token = VerifiedLogoutToken.verify(raw, verifier);
        transaction.executeWithoutResult(status -> {
            clients.lock(policy.actorId());
            var prior = jdbc.query("""
                    SELECT client_key_sha256,jti,subject,provider_session_id,token_sha256,oidc_issued_at,oidc_expires_at
                      FROM platform_logout_receipt WHERE logout_key_sha256 = ? FOR UPDATE
                    """, (row, ordinal) -> clients.key().equals(row.getString("client_key_sha256"))
                            && token.jti.equals(row.getString("jti"))
                            && Objects.equals(token.subject, row.getString("subject"))
                            && Objects.equals(token.providerSessionId, row.getString("provider_session_id"))
                            && token.tokenSha256.equals(row.getString("token_sha256"))
                            && utc(token.issuedAt).equals(row.getObject("oidc_issued_at", LocalDateTime.class))
                            && utc(token.expiresAt).equals(row.getObject("oidc_expires_at", LocalDateTime.class)), token.logoutKey);
            if (!prior.isEmpty()) {
                if (prior.size() != 1 || !prior.get(0)) failed();
                return;
            }
            jdbc.update("""
                    INSERT INTO platform_logout_receipt (logout_key_sha256,client_key_sha256,jti,subject,provider_session_id,
                        token_sha256,oidc_issued_at,oidc_expires_at,status,completed_at,created_by,created_at,classification,revision,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,'PENDING',NULL,?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6))
                    """, token.logoutKey, clients.key(), token.jti, token.subject, token.providerSessionId,
                    token.tokenSha256, utc(token.issuedAt), utc(token.expiresAt), policy.actorId(), classification);
            var identities = jdbc.query("""
                    SELECT DISTINCT i.identity_id FROM platform_identity i JOIN platform_oidc_session s ON s.identity_id=i.identity_id
                     WHERE i.issuer=? AND s.client_id=? AND s.registration_id=(SELECT registration_id FROM platform_oidc_client WHERE client_key_sha256=?)
                       AND s.oidc_issued_at<=? AND (? IS NULL OR i.subject=?) AND (? IS NULL OR s.provider_session_id=?)
                     ORDER BY i.identity_id
                    """, (row, ordinal) -> row.getString(1), token.issuer, token.clientId, clients.key(), utc(token.issuedAt),
                    token.subject, token.subject, token.providerSessionId, token.providerSessionId);
            for (String identity : identities) {
                jdbc.queryForObject("SELECT identity_id FROM platform_identity WHERE identity_id=? FOR UPDATE", String.class, identity);
                var bound = jdbc.query("""
                        SELECT session_id FROM platform_oidc_session WHERE identity_id=? AND client_id=?
                           AND registration_id=(SELECT registration_id FROM platform_oidc_client WHERE client_key_sha256=?)
                           AND oidc_issued_at<=? AND (? IS NULL OR provider_session_id=?) ORDER BY session_id FOR UPDATE
                        """, (row, ordinal) -> row.getString(1), identity, token.clientId, clients.key(), utc(token.issuedAt),
                        token.providerSessionId, token.providerSessionId);
                for (String session : bound) {
                    jdbc.update("""
                            INSERT INTO platform_logout_session (logout_key_sha256,session_id,created_by,created_at,classification)
                            VALUES (?,?,?,UTC_TIMESTAMP(6),?)
                            """, token.logoutKey, session, policy.actorId(), classification);
                    jdbc.update("""
                            UPDATE platform_oidc_session SET status='REVOKED',revoked_at=UTC_TIMESTAMP(6),revision=revision+1,
                                updated_at=UTC_TIMESTAMP(6) WHERE session_id=? AND status='ACTIVE'
                            """, session);
                }
            }
        });
        complete(token.logoutKey);
        return token.logoutKey;
    }

    @Scheduled(fixedDelayString = "${medical.identity.logout.recovery-interval}")
    public void recoverPending() {
        independent();
        var pending = jdbc.query("""
                SELECT logout_key_sha256 FROM platform_logout_receipt WHERE client_key_sha256=? AND status='PENDING'
                ORDER BY created_at,logout_key_sha256 LIMIT ?
                """, (row, ordinal) -> row.getString(1), clients.key(), policy.recoveryBatchSize());
        for (String key : pending) complete(key);
    }

    private void complete(String key) {
        List<String> bound = transaction.execute(status -> {
            var state = jdbc.queryForObject("SELECT status FROM platform_logout_receipt WHERE logout_key_sha256=? FOR UPDATE", String.class, key);
            if ("COMPLETED".equals(state)) return List.of();
            if (!"PENDING".equals(state)) throw new IllegalStateException("LOGOUT_RECEIPT_INVALID");
            return jdbc.query("SELECT session_id FROM platform_logout_session WHERE logout_key_sha256=? ORDER BY session_id",
                    (row, ordinal) -> row.getString(1), key);
        });
        // 数据库撤销已经提交；Redis 清除使用首次冻结的集合，失败后保留回执恢复。
        for (String session : Objects.requireNonNull(bound)) sessions.deleteById(session);
        transaction.executeWithoutResult(status -> jdbc.update("""
                UPDATE platform_logout_receipt SET status='COMPLETED',completed_at=UTC_TIMESTAMP(6),revision=revision+1,
                    updated_at=UTC_TIMESTAMP(6) WHERE logout_key_sha256=? AND status='PENDING'
                """, key));
    }

    private static LocalDateTime utc(java.time.Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static void independent() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("LOGOUT_TRANSACTION_ALREADY_ACTIVE");
    }
    private static void failed() { throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_FAILED.name())); }
}
