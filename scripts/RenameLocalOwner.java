import java.sql.DriverManager;
import java.util.Objects;

class RenameLocalOwner {
    public static void main(String[] arguments) throws Exception {
        String previous = Objects.requireNonNull(System.getenv("MEDICAL_OWNER_USERNAME"));
        String username = Objects.requireNonNull(System.getenv("MEDICAL_OWNER_NEW_USERNAME"));
        if (!username.matches("[A-Za-z][A-Za-z0-9._-]{2,127}") || username.equals(previous)) {
            throw new IllegalArgumentException("OWNER_USERNAME_INVALID");
        }
        try (var connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC",
                "root", Objects.requireNonNull(System.getenv("MEDICAL_ROOT_PASSWORD")))) {
            connection.setAutoCommit(false);
            String userId;
            long frameworkId;
            try (var query = connection.prepareStatement("SELECT a.user_id,a.ruoyi_user_id FROM platform_local_account a JOIN platform_local_bootstrap b ON b.created_by=a.user_id WHERE b.coordination_key='LOCAL' AND a.username=? AND a.platform_role='PLATFORM_ADMIN' AND a.status='ACTIVE' FOR UPDATE")) {
                query.setString(1, previous);
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("OWNER_BOOTSTRAP_REQUIRED");
                    userId = rows.getString(1);
                    frameworkId = rows.getLong(2);
                    if (rows.next()) throw new IllegalStateException("OWNER_ACCOUNT_NOT_UNIQUE");
                }
            }
            try (var query = connection.prepareStatement("SELECT user_id FROM sys_user WHERE user_name=? FOR UPDATE")) {
                query.setString(1, username);
                try (var rows = query.executeQuery()) {
                    if (rows.next()) throw new IllegalStateException("OWNER_USERNAME_EXISTS");
                }
            }
            try (var update = connection.prepareStatement("UPDATE sys_user SET user_name=?,nick_name=?,update_time=UTC_TIMESTAMP(6) WHERE user_id=? AND user_name=?")) {
                update.setString(1, username);
                update.setString(2, username);
                update.setLong(3, frameworkId);
                update.setString(4, previous);
                if (update.executeUpdate() != 1) throw new IllegalStateException("OWNER_USERNAME_UPDATE_FAILED");
            }
            try (var update = connection.prepareStatement("UPDATE platform_local_identity_metadata SET credential_revision=credential_revision+1,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE user_id=?")) {
                update.setString(1, userId);
                if (update.executeUpdate() != 1) throw new IllegalStateException("OWNER_IDENTITY_UPDATE_FAILED");
            }
            try (var update = connection.prepareStatement("UPDATE platform_local_session SET status='REVOKED',revoked_at=UTC_TIMESTAMP(6),revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE user_id=? AND status='ACTIVE'")) {
                update.setString(1, userId);
                update.executeUpdate();
            }
            connection.commit();
            System.out.println("LOCAL_OWNER_USERNAME_UPDATED " + userId);
        }
    }
}
