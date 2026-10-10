package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.identity.TenantSelectionOptions;
import com.ruoyi.medical.persistence.mapper.TenantMemberMapper;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class BusinessSessionDirectory {
    private final JdbcTemplate jdbc;
    private final IdentityDirectoryPolicy classification;
    private final MemberDiscovery discovery;
    private final TenantTransaction transactions;
    private final TenantMemberMapper members;

    public BusinessSessionDirectory(@Qualifier("masterDataSource") DataSource master, IdentityDirectoryPolicy classification,
            MemberDiscovery discovery, TenantTransaction transactions, TenantMemberMapper members) {
        jdbc = new JdbcTemplate(master); this.classification = classification;
        this.discovery = discovery; this.transactions = transactions; this.members = members;
    }

    State read(ApplicationSessionIdentity.Verified subject) {
        Objects.requireNonNull(subject);
        initializeContext(subject);
        var context = jdbc.queryForObject("""
                SELECT user_id,tenant_id,context_revision,auth_epoch,governance_epoch
                  FROM platform_session_context WHERE session_id=?
                """, (row, ordinal) -> {
                    if (!subject.user().user_id().equals(row.getString("user_id"))) denied(ErrorCode.ACCESS_DENIED);
                    return row.getString("tenant_id") == null ? null : new BusinessSessionProtocol.Context(row.getString("tenant_id"),
                            row.getString("context_revision"), row.getString("auth_epoch"), row.getString("governance_epoch"));
                }, subject.sessionId());
        return readMemberships(subject, context);
    }

    void initializeContext(ApplicationSessionIdentity.Verified subject) {
        jdbc.update("""
                INSERT INTO platform_session_context (context_id,session_id,user_id,tenant_id,context_revision,auth_epoch,governance_epoch,
                    created_by,created_at,classification,revision,updated_at)
                VALUES (?,?,?,NULL,0,NULL,NULL,?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE session_id=session_id
                """, java.util.UUID.randomUUID().toString(), subject.sessionId(), subject.user().user_id(), subject.user().user_id(), classification.classification());
    }

    Route route(ApplicationSessionIdentity.Verified subject, String tenantId) {
        var routes = jdbc.query("SELECT tenant_id,member_id FROM platform_member_route WHERE user_id=? AND tenant_id=?",
                (row, ordinal) -> new Route(row.getString("tenant_id"), row.getString("member_id")), subject.user().user_id(), tenantId);
        if (routes.size() != 1) denied(ErrorCode.ACCESS_DENIED);
        return routes.get(0);
    }

    OptionsState selectionOptions(ApplicationSessionIdentity.Verified subject) {
        Objects.requireNonNull(subject);
        initializeContext(subject);
        var saved = jdbc.queryForObject("""
                SELECT tenant_id,context_revision FROM platform_session_context WHERE session_id=? AND user_id=?
                """, (row, ordinal) -> new SavedSelection(row.getString("tenant_id"), row.getString("context_revision")),
                subject.sessionId(), subject.user().user_id());
        Objects.requireNonNull(saved);
        // 发现本人有效成员不读取旧企业的动作或内容，旧水位仍由正式业务入口拒绝。
        State found = readMemberships(subject, null);
        return new OptionsState(new TenantSelectionOptions("1.0.0", subject.user().user_id(), saved.revision(),
                saved.tenantId(), found.memberships()), found.observed());
    }

    void requireOptionsUnchanged(ApplicationSessionIdentity.Verified subject, OptionsState state) {
        var options = state.options();
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM platform_session_context WHERE session_id=? AND user_id=?
                    AND tenant_id <=> ? AND context_revision=?
                """, Long.class, subject.sessionId(), subject.user().user_id(), options.selected_tenant_id(), options.expected_context_revision());
        if (!options.user_id().equals(subject.user().user_id()) || !Long.valueOf(1).equals(count)) denied(ErrorCode.CONTEXT_CHANGED);
        requireObservedUnchanged(subject, state.observed());
    }

    private State readMemberships(ApplicationSessionIdentity.Verified subject, BusinessSessionProtocol.Context context) {
        var routes = jdbc.query("SELECT tenant_id,member_id FROM platform_member_route WHERE user_id=? ORDER BY tenant_id",
                (row, ordinal) -> new Route(row.getString("tenant_id"), row.getString("member_id")), subject.user().user_id());
        List<BusinessSessionProtocol.Member> memberships = new ArrayList<>();
        List<MemberDiscovery.Found> observed = new ArrayList<>();
        MemberDiscovery.Found selected = null;
        for (Route route : routes) {
            var found = discovery.read(subject, route);
            if (found != null) {
                memberships.add(found.member());
                observed.add(found);
                if (context != null && context.tenant_id().equals(route.tenantId())) selected = found;
            }
        }
        List<BusinessSessionProtocol.Action> actions = List.of();
        if (context != null) {
            if (selected == null || !selected.member().status().equals("ACTIVE")) denied(ErrorCode.ACCESS_DENIED);
            var identity = new TenantScope.Identity(context.tenant_id(), subject.user().user_id(),
                    new BigInteger(context.context_revision()), new BigInteger(context.auth_epoch()), new BigInteger(context.governance_epoch()));
            var route = selected.member();
            try (var scope = TenantScope.open(identity)) {
                actions = transactions.execute(current -> {
                    var currentMembers = members.currentMember(subject.user().user_id(), route.member_id());
                    if (currentMembers.size() != 1 || !currentMembers.get(0).status().equals("ACTIVE")) denied(ErrorCode.ACCESS_DENIED);
                    return members.currentActions(subject.user().user_id());
                });
            }
        }
        return new State(List.copyOf(memberships), context, List.copyOf(actions), List.copyOf(observed));
    }

    void requireUnchanged(ApplicationSessionIdentity.Verified subject, State state) {
        BusinessSessionProtocol.Context context = state.context();
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM platform_session_context WHERE session_id=? AND user_id=?
                    AND tenant_id <=> ? AND context_revision=?
                """, Long.class, subject.sessionId(), subject.user().user_id(), context == null ? null : context.tenant_id(),
                context == null ? "0" : context.context_revision());
        if (!Long.valueOf(1).equals(count)) denied(ErrorCode.CONTEXT_CHANGED);
        requireObservedUnchanged(subject, state.observed());
    }

    private void requireObservedUnchanged(ApplicationSessionIdentity.Verified subject, List<MemberDiscovery.Found> observations) {
        for (var observed : observations) {
            var member = observed.member();
            var current = discovery.read(subject, new Route(member.tenant_id(), member.member_id()));
            if (current == null || !current.member().equals(member) || !current.watermarks().equals(observed.watermarks())) {
                denied(ErrorCode.SCOPE_STALE);
            }
        }
    }

    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    record Route(String tenantId, String memberId) { }
    private record SavedSelection(String tenantId, String revision) { }
    record OptionsState(TenantSelectionOptions options, List<MemberDiscovery.Found> observed) { }
    record State(List<BusinessSessionProtocol.Member> memberships, BusinessSessionProtocol.Context context,
                 List<BusinessSessionProtocol.Action> actions, List<MemberDiscovery.Found> observed) { }
}
