package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "oidc")
public final class OidcClientLedger {
    private final JdbcTemplate jdbc;
    private final String key, issuer, classification;
    private final ClientRegistration registration;

    public OidcClientLedger(@Qualifier("masterDataSource") DataSource master, OidcPolicy policy,
            ClientRegistrationRepository registrations, IdentityDirectoryPolicy directory) throws IOException {
        jdbc = new JdbcTemplate(master);
        registration = registrations.findByRegistrationId(policy.registrationId());
        policy.verify(registration);
        issuer = policy.issuer().toASCIIString();
        if (issuer.length() > 512 || registration.getClientId().length() > 255 || registration.getRegistrationId().length() > 100) {
            throw new IllegalArgumentException("OIDC_CLIENT_STORAGE_INVALID");
        }
        key = CanonicalOidcKey.hash(issuer, registration.getClientId());
        classification = directory.classification();
    }

    String key() { return key; }
    String issuer() { return issuer; }

    void lock(String actor) {
        WireValues.uuid(actor);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("OIDC_TRANSACTION_REQUIRED");
        jdbc.update("""
                INSERT INTO platform_oidc_client (client_key_sha256, issuer, client_id, registration_id,
                    created_by, created_at, classification, revision, updated_at)
                VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(6), ?, 1, UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE client_key_sha256 = client_key_sha256
                """, key, issuer, registration.getClientId(), registration.getRegistrationId(), actor, classification);
        boolean matches = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT issuer, client_id, registration_id FROM platform_oidc_client WHERE client_key_sha256 = ? FOR UPDATE
                """, (row, ordinal) -> issuer.equals(row.getString("issuer"))
                        && registration.getClientId().equals(row.getString("client_id"))
                        && registration.getRegistrationId().equals(row.getString("registration_id")), key));
        if (!matches) failed();
    }

    void requireNoPriorLogout(VerifiedOidcSession session) {
        var receipts = jdbc.query("""
                SELECT logout_key_sha256 FROM platform_logout_receipt
                 WHERE client_key_sha256 = ? AND oidc_issued_at >= ?
                   AND ((provider_session_id IS NULL AND subject = ?)
                     OR (provider_session_id = ? AND (subject IS NULL OR subject = ?))) LIMIT 1
                """, (row, ordinal) -> row.getString(1), key, LocalDateTime.ofInstant(session.issuedAt(), ZoneOffset.UTC),
                session.identity().subject(), session.providerSessionId(), session.identity().subject());
        if (!receipts.isEmpty()) failed();
    }

    private static void failed() {
        throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_FAILED.name()));
    }
}
