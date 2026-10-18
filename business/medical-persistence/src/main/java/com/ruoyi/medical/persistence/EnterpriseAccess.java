package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.TenantMemberMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.math.BigInteger;
import java.util.Objects;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class EnterpriseAccess {
    private final JdbcTemplate jdbc;
    private final ApplicationSessionIdentity identities;
    private final BusinessSessionDirectory directory;
    private final TenantTransaction transactions;
    private final TenantMemberMapper members;
    private final PermissionMapper permissions;

    public EnterpriseAccess(@Qualifier("masterDataSource") DataSource master, ApplicationSessionIdentity identities,
            BusinessSessionDirectory directory, TenantTransaction transactions, TenantMemberMapper members, PermissionMapper permissions) {
        jdbc = new JdbcTemplate(master); this.identities = identities; this.directory = directory;
        this.transactions = transactions; this.members = members;
        this.permissions = permissions;
    }

    <T> T execute(ApplicationSessionIdentity.Verified subject, Function<Admission, T> operation) {
        Objects.requireNonNull(subject); Objects.requireNonNull(operation);
        var state = directory.read(subject);
        var context = state.context();
        if (context == null) denied(ErrorCode.ACCESS_DENIED);
        var route = directory.route(subject, context.tenant_id());
        var identity = new TenantScope.Identity(context.tenant_id(), subject.user().user_id(),
                new BigInteger(context.context_revision()), new BigInteger(context.auth_epoch()), new BigInteger(context.governance_epoch()));
        try (var scope = TenantScope.open(identity)) {
            return transactions.execute(watermarks -> {
                lockIdentityCoordination(subject);
                identities.lockCurrent(subject);
                if (!route.equals(directory.route(subject, context.tenant_id()))) denied(ErrorCode.SCOPE_STALE);
                var locked = jdbc.queryForObject("""
                        SELECT context_id,tenant_id,context_revision,auth_epoch,governance_epoch
                          FROM platform_session_context WHERE session_id=? AND user_id=? FOR UPDATE
                        """, (row, ordinal) -> new Locked(row.getString(1), new BusinessSessionProtocol.Context(row.getString(2),
                        row.getString(3), row.getString(4), row.getString(5))), subject.sessionId(), subject.user().user_id());
                if (locked == null || !context.equals(locked.context())) denied(ErrorCode.CONTEXT_CHANGED);
                var current = members.currentMember(subject.user().user_id(), route.memberId());
                if (current.size() != 1 || !"ACTIVE".equals(current.get(0).status())) denied(ErrorCode.ACCESS_DENIED);
                var bindings = permissions.currentBinding(subject.user().user_id());
                if (bindings.size() != 1) denied(ErrorCode.ACCESS_DENIED);
                var binding = bindings.get(0);
                if (!"ACTIVE".equals(binding.status()) || !subject.binding().matches(new AuthenticatedIdentityBinding.View(
                        binding.mode(), binding.userId(), binding.issuer(), binding.subject()))
                        || binding.mode().equals("local") && !binding.userId().equals(binding.localUserId())
                        || binding.mode().equals("oidc") && binding.localUserId() != null) denied(ErrorCode.ACCESS_DENIED);
                return operation.apply(new Admission(subject, locked.id(), context, watermarks));
            });
        }
    }

    void lockIdentityCoordination(ApplicationSessionIdentity.Verified subject) {
        if (subject.binding().view().mode().equals("local")) {
            String locked = jdbc.queryForObject("SELECT coordination_key FROM platform_local_bootstrap WHERE coordination_key='LOCAL' FOR UPDATE", String.class);
            if (!"LOCAL".equals(locked)) denied(ErrorCode.ACCESS_DENIED);
        } else {
            var keys = jdbc.query("""
                    SELECT c.client_key_sha256 FROM platform_oidc_session s
                    JOIN platform_identity i ON i.identity_id=s.identity_id
                    JOIN platform_oidc_client c ON c.issuer=i.issuer AND c.client_id=s.client_id
                         AND c.registration_id=s.registration_id
                    WHERE s.session_id=? AND s.status='ACTIVE' AND i.user_id=? AND i.issuer=? AND i.subject=?
                    """, (row, ordinal) -> row.getString(1), subject.sessionId(), subject.user().user_id(),
                    subject.binding().view().issuer(), subject.binding().view().subject());
            if (keys.size() != 1) denied(ErrorCode.ACCESS_DENIED);
            String locked = jdbc.queryForObject("SELECT client_key_sha256 FROM platform_oidc_client WHERE client_key_sha256=? FOR UPDATE",
                    String.class, keys.get(0));
            if (!keys.get(0).equals(locked)) denied(ErrorCode.ACCESS_DENIED);
        }
    }

    static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    static final class Admission {
        private final ApplicationSessionIdentity.Verified subject;
        private final String contextId;
        private final BusinessSessionProtocol.Context context;
        private final TenantWatermarks watermarks;
        private final SimulationWorkerAccess.Proof worker;
        private final FormalWorkerAccess.Proof formal;
        Admission(ApplicationSessionIdentity.Verified subject,String contextId,BusinessSessionProtocol.Context context,TenantWatermarks watermarks) {
            this.subject=Objects.requireNonNull(subject); this.contextId=contextId; this.context=context; this.watermarks=watermarks; this.worker=null; this.formal=null;
        }
        Admission(SimulationWorkerAccess.Proof worker,BusinessSessionProtocol.Context context,TenantWatermarks watermarks) {
            this.formal=null; this.worker=Objects.requireNonNull(worker); this.subject=null; this.contextId=null; this.context=context; this.watermarks=watermarks;
        }
        Admission(FormalWorkerAccess.Proof formal,BusinessSessionProtocol.Context context,TenantWatermarks watermarks) {
            this.formal=Objects.requireNonNull(formal); formal.requireActive(); this.worker=null; this.subject=null;this.contextId=null;this.context=context;this.watermarks=watermarks;
        }
        ApplicationSessionIdentity.Verified subject() {
            if(subject==null) denied(ErrorCode.ACCESS_DENIED);
            return subject;
        }
        String contextId(){if(formal!=null)formal.requireActive();return contextId;}
        BusinessSessionProtocol.Context context(){if(formal!=null)formal.requireActive();return context;}
        TenantWatermarks watermarks(){if(formal!=null)formal.requireActive();return watermarks;}
        String actorId(){return formal!=null?formal.actorId():worker==null?subject.user().user_id():worker.actorId();}
        AuthenticatedIdentityBinding.View bindingView(){return formal!=null?formal.bindingView():worker==null?subject.binding().view():worker.bindingView();}
        Object actorProof(){return formal!=null?formal.identity():worker==null?subject:worker.identity();}
        boolean formalWorker(){return formal!=null;}
        FormalWorkerAccess.Proof formalProof(){if(formal==null)denied(ErrorCode.ACCESS_DENIED);formal.requireActive();return formal;}
        boolean simulationWorker(){return worker!=null;}
        SimulationWorkerAccess.Proof workerProof(){if(worker==null) denied(ErrorCode.ACCESS_DENIED);return worker;}
    }
    private record Locked(String id, BusinessSessionProtocol.Context context) { }
}
