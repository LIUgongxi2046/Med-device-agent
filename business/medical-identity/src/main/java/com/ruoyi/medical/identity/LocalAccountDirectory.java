package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.UserDetailsServiceImpl;
import com.ruoyi.system.service.ISysUserService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
public final class LocalAccountDirectory implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PasswordEncoder passwords;
    private final IdentityDirectoryPolicy policy;
    private final ISysUserService users;
    private final UserDetailsServiceImpl authenticationUsers;
    private final RedisCache redis;
    private final int maxRetryCount;

    public LocalAccountDirectory(@Qualifier("masterDataSource") DataSource master, @Qualifier("localPasswordEncoder") PasswordEncoder passwords,
            IdentityDirectoryPolicy policy, ISysUserService users, UserDetailsServiceImpl authenticationUsers, RedisCache redis,
            @Value("${user.password.maxRetryCount}") int maxRetryCount) {
        jdbc = new JdbcTemplate(master);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(master));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.passwords = passwords;
        this.policy = policy;
        this.users = users;
        this.authenticationUsers = authenticationUsers;
        this.redis = redis;
        if (maxRetryCount <= 0) throw new IllegalArgumentException("LOGIN_RETRY_LIMIT_REQUIRED");
        this.maxRetryCount = maxRetryCount;
    }

    @Override public UserDetails loadUserByUsername(String username) {
        if (!LocalAccountInputs.validName(username)) throw new UsernameNotFoundException("AUTHENTICATION_FAILED");
        // 原框架负责账户状态、密码和框架权限；此处关联医疗业务身份。
        var details = authenticationUsers.loadUserByUsername(username);
        if (!(details instanceof LoginUser frameworkIdentity)) throw new IllegalStateException("RUOYI_AUTHENTICATED_USER_REQUIRED");
        var accounts = jdbc.query("""
                SELECT user_id,platform_role,credential_revision
                  FROM platform_local_identity_metadata WHERE ruoyi_user_id=?
                """, (row, ordinal) -> new LocalPrincipal(frameworkIdentity, row.getString("user_id"),
                        row.getString("platform_role"),
                        new BigInteger(row.getString("credential_revision"))), frameworkIdentity.getUserId());
        if (accounts.size() != 1) throw new UsernameNotFoundException("AUTHENTICATION_FAILED");
        return accounts.get(0);
    }

    boolean setupRequired() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class)) == 0;
    }

    void bootstrap(HttpServletRequest request, String username, String password, String displayName) {
        if (!java.util.Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr())) denied();
        LocalAccountInputs.requireName(username);
        LocalAccountInputs.requireName(displayName);
        LocalAccountInputs.requirePassword(password);
        String hash = passwords.encode(password);
        independent();
        transaction.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO platform_local_bootstrap (coordination_key,created_by,created_at,classification,revision,updated_at)
                    VALUES ('LOCAL',NULL,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6))
                    ON DUPLICATE KEY UPDATE coordination_key=coordination_key
                    """, policy.classification());
            jdbc.queryForObject("SELECT coordination_key FROM platform_local_bootstrap WHERE coordination_key='LOCAL' FOR UPDATE", String.class);
            if (!setupRequired()) denied();
            String userId = UUID.randomUUID().toString();
            var user = new SysUser(); user.setUserName(username); user.setNickName(displayName);
            user.setPassword(hash); user.setStatus("0"); user.setCreateBy(userId);
            if (users.insertUser(user) != 1 || user.getUserId() == null) throw new IllegalStateException("RUOYI_ACCOUNT_CREATE_FAILED");
            jdbc.update("""
                    INSERT INTO platform_local_identity_metadata (user_id,ruoyi_user_id,platform_role,
                        failed_attempts,locked_until,credential_revision,created_by,created_at,classification,revision,updated_at)
                    VALUES (?,?,'PLATFORM_ADMIN',0,NULL,1,?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6))
                    """, userId, user.getUserId(), userId, policy.classification());
            if (jdbc.update("""
                    UPDATE platform_local_bootstrap SET created_by=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                     WHERE coordination_key='LOCAL' AND created_by IS NULL
                    """, userId) != 1) throw new IllegalStateException("LOCAL_BOOTSTRAP_CONFLICT");
        });
    }

    void registerSession(HttpServletRequest request, Authentication authentication) {
        LocalPrincipal principal = requirePrincipal(authentication);
        var session = Objects.requireNonNull(request.getSession(false));
        independent();
        transaction.executeWithoutResult(status -> {
            requireAccount(principal);
            jdbc.update("""
                    INSERT INTO platform_local_session (session_id,user_id,credential_revision,status,revoked_at,
                        created_by,created_at,classification,revision,updated_at)
                    VALUES (?,?,?,'ACTIVE',NULL,?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6))
                    ON DUPLICATE KEY UPDATE session_id=session_id
                    """, session.getId(), principal.businessUserId, principal.credentialRevision.toString(),
                    principal.businessUserId, policy.classification());
            requireBinding(session.getId(), principal);
            jdbc.update("""
                    UPDATE platform_local_identity_metadata SET failed_attempts=0,locked_until=NULL,revision=revision+1,
                        updated_at=UTC_TIMESTAMP(6) WHERE user_id=?
                    """, principal.businessUserId);
        });
        // 原用户名用于框架组件，业务 UUID 用于已有会话索引和撤销处理。
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal.businessUserId);
    }

    void requireSession(HttpServletRequest request, Authentication authentication) {
        LocalPrincipal principal = requirePrincipal(authentication);
        var session = request.getSession(false);
        if (session == null) denied();
        independent();
        transaction.executeWithoutResult(status -> {
            requireAccount(principal);
            requireBinding(session.getId(), principal);
        });
    }

    void lockSession(String sessionId, Authentication authentication) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("IDENTITY_TRANSACTION_REQUIRED");
        var principal = requirePrincipal(authentication);
        requireAccount(principal);
        requireBinding(sessionId, principal);
    }

    void revoke(HttpServletRequest request, Authentication authentication) {
        if (authentication == null) return;
        LocalPrincipal principal = requirePrincipal(authentication);
        var session = request.getSession(false);
        if (session == null) return;
        independent();
        transaction.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT user_id FROM platform_local_account WHERE user_id=? FOR UPDATE", String.class, principal.businessUserId);
            jdbc.update("""
                    UPDATE platform_local_session SET status='REVOKED',revoked_at=UTC_TIMESTAMP(6),revision=revision+1,
                        updated_at=UTC_TIMESTAMP(6) WHERE session_id=? AND user_id=? AND status='ACTIVE'
                    """, session.getId(), principal.businessUserId);
        });
    }

    AccountView view(Authentication authentication) {
        var principal = requirePrincipal(authentication);
        if (SecurityUtils.getLoginUser() != principal) denied();
        return jdbc.queryForObject("""
                SELECT user_id,username,display_name,platform_role,revision FROM platform_local_account
                 WHERE user_id=? AND ruoyi_user_id=? AND username=? AND status='ACTIVE'
                """, (row, ordinal) -> new AccountView(row.getString("user_id"), row.getString("username"),
                        row.getString("display_name"), row.getString("platform_role"), row.getString("revision")),
                principal.businessUserId, SecurityUtils.getUserId(), SecurityUtils.getUsername());
    }

    @EventListener
    public void failedPassword(AbstractAuthenticationFailureEvent event) {
        // 保存原框架已执行的结果，失败计数与期限由原服务维护。
        String key = CacheConstants.PWD_ERR_CNT_KEY + event.getAuthentication().getName();
        Integer count = redis.getCacheObject(key);
        if (count == null) return;
        long remaining = redis.getExpire(key);
        if (remaining <= 0) return;
        jdbc.update("""
                UPDATE platform_local_identity_metadata SET
                    failed_attempts=?,locked_until=IF(?,TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),NULL),
                    revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                 WHERE ruoyi_user_id=(SELECT user_id FROM sys_user WHERE user_name=? AND status='0' AND del_flag='0')
                """, count, count >= maxRetryCount, remaining, event.getAuthentication().getName());
    }

    private void requireAccount(LocalPrincipal principal) {
        var valid = jdbc.query("""
                SELECT credential_revision,platform_role,ruoyi_user_id,password_hash FROM platform_local_account WHERE user_id=? AND status='ACTIVE' FOR UPDATE
                """, (row, ordinal) -> {
                    String role = row.getString("platform_role");
                    return principal.credentialRevision.equals(new BigInteger(row.getString("credential_revision")))
                            && principal.getUserId() == row.getLong("ruoyi_user_id")
                            && principal.credentialSha256.equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(row.getString("password_hash")))
                            && principal.platformRole.equals(role);
                }, principal.businessUserId);
        if (valid.size() != 1 || !valid.get(0)) denied();
        // 原服务读取当前角色和菜单权限，已有会话不能继续使用历史权限集合。
        var current = Objects.requireNonNull(users.selectUserById(principal.getUserId()));
        var identity = (LoginUser) authenticationUsers.createLoginUser(current);
        principal.setUser(identity.getUser());
        principal.setDeptId(identity.getDeptId());
        principal.setPermissions(identity.getPermissions());
    }

    private void requireBinding(String sessionId, LocalPrincipal principal) {
        var valid = jdbc.query("""
                SELECT user_id,credential_revision,status FROM platform_local_session WHERE session_id=? FOR UPDATE
                """, (row, ordinal) -> principal.businessUserId.equals(row.getString("user_id"))
                        && principal.credentialRevision.equals(new BigInteger(row.getString("credential_revision")))
                        && "ACTIVE".equals(row.getString("status")), sessionId);
        if (valid.size() != 1 || !valid.get(0)) denied();
    }

    private static LocalPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof LocalPrincipal principal)
                || principal.getUserId() == null || principal.getUser() == null || principal.businessUserId == null
                || principal.credentialRevision == null || principal.credentialSha256 == null || principal.platformRole == null) denied();
        return (LocalPrincipal) authentication.getPrincipal();
    }

    private static void independent() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("LOCAL_IDENTITY_TRANSACTION_ALREADY_ACTIVE");
    }
    private static void denied() { throw new AccessDeniedException(ErrorCode.ACCESS_DENIED.name()); }
    public record AccountView(String user_id, String username, String display_name, String platform_role, String revision) { }
}
