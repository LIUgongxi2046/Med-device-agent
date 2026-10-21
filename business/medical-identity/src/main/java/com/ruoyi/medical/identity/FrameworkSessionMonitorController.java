package com.ruoyi.medical.identity;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.framework.web.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** The legacy token cache is not the application's session authority. */
@RestController
@RequestMapping("/api/monitor/online")
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
public class FrameworkSessionMonitorController {
    private final ApplicationSessionIdentity identities;
    private final PermissionService permissions;
    private final RedisIndexedSessionRepository sessions;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final FrameworkSessionHandles handles = new FrameworkSessionHandles();

    public FrameworkSessionMonitorController(ApplicationSessionIdentity identities, PermissionService permissions,
            RedisIndexedSessionRepository sessions, @Qualifier("masterDataSource") DataSource master) {
        this.identities = identities; this.permissions = permissions; this.sessions = sessions;
        jdbc = new JdbcTemplate(master);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(master));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('monitor:online:list')")
    public TableDataInfo list(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestParam(required = false) String ipaddr, @RequestParam(required = false) String userName) {
        var subject = current(request, response, authentication, "monitor:online:list");
        if (ipaddr != null && !ipaddr.isBlank()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "SESSION_IP_NOT_RECORDED");
        if (userName != null && userName.length() > 128) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        // Bounded snapshot: fail rather than silently report a truncated list as complete.
        var candidates = jdbc.query("""
                SELECT s.session_id,s.user_id,s.credential_revision,a.username,a.platform_role,a.password_hash,d.dept_name
                FROM platform_local_session s JOIN platform_local_account a ON a.user_id=s.user_id
                LEFT JOIN sys_user u ON u.user_id=a.ruoyi_user_id LEFT JOIN sys_dept d ON d.dept_id=u.dept_id
                WHERE s.status='ACTIVE' AND a.status='ACTIVE' AND s.credential_revision=a.credential_revision
                  AND (? IS NULL OR a.username=?) ORDER BY s.created_at DESC,s.session_id LIMIT 1001
                """, (r, n) -> new Candidate(r.getString(1), r.getString(2), r.getString(3), r.getString(4),
                        r.getString(5), r.getString(6), r.getString(7)),
                userName == null || userName.isBlank() ? null : userName, userName);
        if (candidates.size() > 1000) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ONLINE_SCOPE_TOO_LARGE_FILTER_USERNAME");
        var rows = new ArrayList<Map<String, Object>>();
        for (Candidate candidate : candidates) {
            var session = sessions.findById(candidate.session);
            if (session == null || session.isExpired()) continue;
            Object context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (!(context instanceof SecurityContext security) || security.getAuthentication() == null
                    || !security.getAuthentication().isAuthenticated()
                    || !(security.getAuthentication().getPrincipal() instanceof LocalPrincipal principal)
                    || !candidate.user.equals(principal.businessUserId)
                    || !candidate.credential.equals(principal.credentialRevision.toString())
                    || !candidate.role.equals(principal.platformRole)
                    || !org.apache.commons.codec.digest.DigestUtils.sha256Hex(candidate.passwordHash).equals(principal.credentialSha256)) continue;
            var row = new LinkedHashMap<String, Object>();
            row.put("tokenId", handles.issue(subject.sessionId(), candidate.session, candidate.user, candidate.credential));
            row.put("userName", candidate.username); row.put("deptName", candidate.department);
            row.put("ipaddr", null); row.put("loginLocation", null); row.put("browser", null); row.put("os", null);
            row.put("loginTime", session.getCreationTime().toEpochMilli()); rows.add(row);
        }
        var result = new TableDataInfo(rows, rows.size()); result.setCode(200); result.setMsg("操作成功"); return result;
    }

    @DeleteMapping("/{tokenId}")
    @com.ruoyi.common.annotation.Log(title = "在线用户", businessType = com.ruoyi.common.enums.BusinessType.FORCE)
    @PreAuthorize("@ss.hasPermi('monitor:online:forceLogout')")
    public AjaxResult forceLogout(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String tokenId) {
        var subject = current(request, response, authentication, "monitor:online:forceLogout");
        var target = handles.resolve(subject.sessionId(), tokenId);
        // Commit authoritative revocation first: a Redis deletion failure cannot re-authorize the session.
        transaction.executeWithoutResult(status -> {
            identities.lockCurrent(subject);
            administrator(subject, "monitor:online:forceLogout");
            int changed = jdbc.update("""
                    UPDATE platform_local_session SET status='REVOKED',revoked_at=UTC_TIMESTAMP(6),
                      revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                    WHERE session_id=? AND user_id=? AND credential_revision=? AND status='ACTIVE'
                    """, target.session(), target.user(), target.credential());
            if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "SESSION_NO_LONGER_ACTIVE");
        });
        try {
            sessions.deleteById(target.session());
            if (sessions.findById(target.session()) != null) throw new IllegalStateException("SESSION_DELETE_NOT_CONFIRMED");
        } catch (RuntimeException failure) {
            handles.remove(tokenId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SESSION_REVOKED_REDIS_CLEANUP_UNCONFIRMED");
        }
        handles.remove(tokenId);
        return AjaxResult.success();
    }

    private ApplicationSessionIdentity.Verified current(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication, String permission) {
        response.setHeader("Cache-Control", "no-store");
        var subject = identities.requireCurrent(request, authentication); administrator(subject, permission); return subject;
    }
    private void administrator(ApplicationSessionIdentity.Verified subject, String permission) {
        if (!"PLATFORM_ADMIN".equals(subject.user().platform_role()) || !permissions.hasPermi(permission)) throw new AccessDeniedException("ACCESS_DENIED");
    }
    private record Candidate(String session, String user, String credential, String username, String role, String passwordHash, String department) {
        @Override public String toString() { return "SessionMonitorCandidate"; }
    }
}
