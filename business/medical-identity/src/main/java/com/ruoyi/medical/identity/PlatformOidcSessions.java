package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "oidc")
public final class PlatformOidcSessions {
    private final OidcPolicy policy;
    private final IdentityDirectoryPolicy directoryPolicy;
    private final ClientRegistration registration;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final OidcClientLedger clients;

    public PlatformOidcSessions(@Qualifier("masterDataSource") DataSource master, OidcPolicy policy,
            IdentityDirectoryPolicy directoryPolicy, ClientRegistrationRepository registrations, OidcClientLedger clients) {
        this.policy = Objects.requireNonNull(policy);
        this.directoryPolicy = Objects.requireNonNull(directoryPolicy);
        this.clients = Objects.requireNonNull(clients);
        this.registration = Objects.requireNonNull(registrations).findByRegistrationId(policy.registrationId());
        policy.verify(registration);
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(master));
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(master));
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    void registerCurrentSession(HttpServletRequest request, PlatformIdentity platformIdentity) {
        VerifiedOidcSession session = VerifiedOidcSession.requireCurrent(policy, registration, request.getSession(false));
        Objects.requireNonNull(platformIdentity);
        requireIndependentTransaction();
        transaction.executeWithoutResult(status -> {
            clients.lock(platformIdentity.userId());
            clients.requireNoPriorLogout(session);
            var identities = jdbc.query("""
                    SELECT identity_id, user_id, issuer, subject, status
                      FROM platform_identity WHERE identity_id = ? FOR UPDATE
                    """, (row, ordinal) -> platformIdentity.userId().equals(row.getString("user_id"))
                            && session.identity().issuer().equals(row.getString("issuer"))
                            && session.identity().subject().equals(row.getString("subject"))
                            && "ACTIVE".equals(row.getString("status")), platformIdentity.identityId());
            if (identities.size() != 1 || !identities.get(0)) authenticationFailed();
            // 重复认证保留会话的原有绑定及撤销状态，禁止重新启用旧会话。
            jdbc.update("""
                    INSERT INTO platform_oidc_session
                      (session_id, identity_id, registration_id, client_id, provider_session_id, oidc_issued_at,
                       status, revoked_at, created_by, created_at, classification, revision, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', NULL, ?, UTC_TIMESTAMP(6), ?, 1, UTC_TIMESTAMP(6))
                    ON DUPLICATE KEY UPDATE session_id = session_id
                    """, session.sessionId(), platformIdentity.identityId(), session.registrationId(), session.clientId(),
                    session.providerSessionId(), LocalDateTime.ofInstant(session.issuedAt(), ZoneOffset.UTC),
                    platformIdentity.userId(), directoryPolicy.classification());
            var records = jdbc.query("""
                    SELECT session_id, identity_id, registration_id, client_id, provider_session_id, oidc_issued_at, status
                      FROM platform_oidc_session WHERE session_id = ? FOR UPDATE
                    """, (row, ordinal) -> platformIdentity.identityId().equals(row.getString("identity_id"))
                            && "ACTIVE".equals(row.getString("status")) && matches(row, session), session.sessionId());
            if (records.size() != 1 || !records.get(0)) authenticationFailed();
        });
    }

    void requireCurrentSession(HttpServletRequest request) {
        VerifiedOidcSession session = VerifiedOidcSession.requireCurrent(policy, registration, request.getSession(false));
        requireIndependentTransaction();
        transaction.executeWithoutResult(status -> requireBinding(session));
    }

    void lockCurrentSession(jakarta.servlet.http.HttpSession actual) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("SESSION_TRANSACTION_REQUIRED");
        requireBinding(VerifiedOidcSession.requireCurrent(policy, registration, actual));
    }

    private void requireBinding(VerifiedOidcSession session) {
            var owners = jdbc.query("""
                    SELECT identity_id FROM platform_identity WHERE issuer = ? AND subject = ? AND status = 'ACTIVE' FOR UPDATE
                    """, (row, ordinal) -> row.getString("identity_id"), session.identity().issuer(), session.identity().subject());
            if (owners.size() != 1) throw new AccessDeniedException(ErrorCode.ACCESS_DENIED.name());
            var records = jdbc.query("""
                    SELECT session_id, registration_id, client_id, provider_session_id, oidc_issued_at, status
                      FROM platform_oidc_session WHERE session_id = ? AND identity_id = ? FOR UPDATE
                    """, (row, ordinal) -> "ACTIVE".equals(row.getString("status")) && matches(row, session),
                    session.sessionId(), owners.get(0));
            if (records.size() != 1 || !records.get(0)) throw new AccessDeniedException(ErrorCode.ACCESS_DENIED.name());
    }

    private static boolean matches(ResultSet row, VerifiedOidcSession session) throws SQLException {
        return session.sessionId().equals(row.getString("session_id"))
                && session.registrationId().equals(row.getString("registration_id"))
                && session.clientId().equals(row.getString("client_id"))
                && Objects.equals(session.providerSessionId(), row.getString("provider_session_id"))
                && session.issuedAt().equals(row.getObject("oidc_issued_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

    private static void requireIndependentTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("SESSION_TRANSACTION_ALREADY_ACTIVE");
        }
    }

    private static void authenticationFailed() {
        throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_FAILED.name()));
    }
}
