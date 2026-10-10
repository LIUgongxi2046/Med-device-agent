import java.sql.DriverManager;
import java.util.Objects;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RecoverLocalOwner {
    public static void main(String[] arguments) throws Exception {
        String username = Objects.requireNonNull(System.getenv("MEDICAL_OWNER_USERNAME"));
        String password = Objects.requireNonNull(System.getenv("MEDICAL_OWNER_PASSWORD"));
        if (password.length() != 48 || !password.matches("[a-f0-9]{48}")) throw new IllegalArgumentException("OWNER_PASSWORD_INVALID");
        String hash = new BCryptPasswordEncoder(12).encode(password);
        try (var connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC",
                "root", Objects.requireNonNull(System.getenv("MEDICAL_ROOT_PASSWORD")))) {
            connection.setAutoCommit(false);
            String userId;
            try (var query = connection.prepareStatement("SELECT user_id FROM platform_local_account WHERE username=? AND platform_role='PLATFORM_ADMIN' AND status='ACTIVE' FOR UPDATE")) {
                query.setString(1, username);
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("OWNER_ACCOUNT_REQUIRED");
                    userId = rows.getString(1);
                    if (rows.next()) throw new IllegalStateException("OWNER_ACCOUNT_NOT_UNIQUE");
                }
            }
            try (var query = connection.prepareStatement("SELECT created_by FROM platform_local_bootstrap WHERE coordination_key='LOCAL' FOR UPDATE"); var rows = query.executeQuery()) {
                if (!rows.next() || !userId.equals(rows.getString(1))) throw new IllegalStateException("OWNER_BOOTSTRAP_MISMATCH");
            }
            try (var update = connection.prepareStatement("UPDATE sys_user u JOIN platform_local_identity_metadata m ON m.ruoyi_user_id=u.user_id SET u.password=?,u.pwd_update_date=UTC_TIMESTAMP(6),u.update_time=UTC_TIMESTAMP(6) WHERE m.user_id=?")) {
                update.setString(1, hash);
                update.setString(2, userId);
                if (update.executeUpdate() != 1) throw new IllegalStateException("OWNER_CREDENTIAL_UPDATE_FAILED");
            }
            try (var update = connection.prepareStatement("UPDATE platform_local_identity_metadata SET credential_revision=credential_revision+1,failed_attempts=0,locked_until=NULL,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE user_id=?")) {
                update.setString(1, userId);
                if (update.executeUpdate() != 1) throw new IllegalStateException("OWNER_CREDENTIAL_REVISION_UPDATE_FAILED");
            }
            try (var update = connection.prepareStatement("UPDATE platform_local_session SET status='REVOKED',revoked_at=UTC_TIMESTAMP(6),revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE user_id=? AND status='ACTIVE'")) {
                update.setString(1, userId);
                update.executeUpdate();
            }
            connection.commit();
            System.out.println("LOCAL_OWNER_CREDENTIAL_UPDATED");
        }
    }
}
