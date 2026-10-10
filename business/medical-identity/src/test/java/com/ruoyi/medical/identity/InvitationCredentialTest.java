package com.ruoyi.medical.identity;

import static org.junit.jupiter.api.Assertions.*;

import com.ruoyi.medical.contracts.SchemaRejection;
import java.util.Base64;
import org.junit.jupiter.api.Test;

final class InvitationCredentialTest {
    @Test void issuedSecretCanBeVerifiedWithoutPersistingPlaintext() {
        var issued = InvitationCredential.issue();
        assertEquals(32, Base64.getUrlDecoder().decode(issued.secret()).length);
        assertEquals(43, issued.secret().length());
        assertEquals(64, issued.sha256().length());
        assertTrue(InvitationCredential.matches(issued.secret(), issued.sha256()));
        assertFalse(issued.toString().contains(issued.secret()));
        assertFalse(issued.toString().contains(issued.sha256()));
    }

    @Test void changedSecretDoesNotMatchOriginalDigest() {
        var issued = InvitationCredential.issue();
        byte[] changed = Base64.getUrlDecoder().decode(issued.secret());
        changed[0] ^= 1;
        String altered = Base64.getUrlEncoder().withoutPadding().encodeToString(changed);
        assertFalse(InvitationCredential.matches(altered, issued.sha256()));
    }

    @Test void noncanonicalAndMalformedCredentialsAreRejected() {
        var issued = InvitationCredential.issue();
        assertThrows(SchemaRejection.class, () -> InvitationCredential.matches(issued.secret() + "=", issued.sha256()));
        assertThrows(SchemaRejection.class, () -> InvitationCredential.matches(issued.secret().substring(1), issued.sha256()));
        assertThrows(SchemaRejection.class, () -> InvitationCredential.matches(issued.secret(), "G" + issued.sha256().substring(1)));
        assertThrows(SchemaRejection.class, () -> InvitationCredential.matches(null, issued.sha256()));
        assertThrows(SchemaRejection.class, () -> InvitationCredential.matches(issued.secret(), null));
        char end = issued.secret().charAt(42);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        String changed = issued.secret().substring(0, 42) + alphabet.charAt(alphabet.indexOf(end) + 1);
        assertThrows(SchemaRejection.class, () -> InvitationCredential.matches(changed, issued.sha256()));
    }
}
