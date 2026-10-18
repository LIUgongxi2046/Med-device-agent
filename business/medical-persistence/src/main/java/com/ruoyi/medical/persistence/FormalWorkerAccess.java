package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Session-free formal worker reads, exclusively inside a real authenticated action.
 * The existing authority owns mTLS/token/action/lease/current-grant admission and its
 * final recheck. This bridge never creates a session, token, principal or grant.
 */
public final class FormalWorkerAccess {
    private final RunTokenAuthority tokens;
    private final RunTokenAuthorityStore store;
    private final RunTokenAuthoritySchema schema;
    private final PermissionMapper permissions;
    private final JdbcTemplate jdbc;

    public FormalWorkerAccess(DataSource data,RunTokenAuthority tokens,RunTokenAuthoritySchema schema,PermissionMapper permissions) {
        this(new JdbcTemplate(data),new RunTokenAuthorityStore(data),tokens,schema,permissions);
    }
    FormalWorkerAccess(JdbcTemplate jdbc,RunTokenAuthorityStore store,RunTokenAuthority tokens,RunTokenAuthoritySchema schema,PermissionMapper permissions) {
        this.tokens=Objects.requireNonNull(tokens);this.schema=Objects.requireNonNull(schema);this.permissions=Objects.requireNonNull(permissions);this.jdbc=jdbc;this.store=store;
    }
    public <T> T execute(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,
            Function<EnterpriseAccess.Admission,T> operation)throws Exception {
        return executeAction(http,token,action,(admission,ignored)->operation.apply(admission));
    }
    public <T>T executeAction(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,
            java.util.function.BiFunction<EnterpriseAccess.Admission,RunTokenAuthority.Admission,T> operation)throws Exception {
        Objects.requireNonNull(operation);
        return tokens.execute(http,token,action,admitted -> {
            var proof=require(admitted);
            try {
                var context=new BusinessSessionProtocol.Context(admitted.tenantId(),"0",admitted.authEpoch().toString(),admitted.governanceEpoch().toString());
                var result=operation.apply(new EnterpriseAccess.Admission(proof,context,new TenantWatermarks(admitted.authEpoch(),admitted.governanceEpoch())),admitted);
                proof.requireActive();
                var after=require(admitted);
                try { if(!proof.identity.equals(after.identity)) denied(); } finally { after.close(); }
                return result;
            } finally { proof.close(); }
        });
    }
    private Proof require(RunTokenAuthority.Admission admitted)throws Exception {
        if(!TransactionSynchronizationManager.isActualTransactionActive() || !TenantScope.requireCurrent().tenantId().equals(admitted.tenantId())
                || !TenantScope.requireCurrent().subjectId().equals(admitted.initiatorId()))denied();
        var run=store.run(admitted.runId(),admitted.initiatorId());
        if(!run.tenant().equals(admitted.tenantId()) || !Objects.equals(run.project(),admitted.projectId())
                || !run.principal().equals(admitted.principalId()) || !run.actor().equals(admitted.initiatorId())
                || !run.actor().equals(run.executionActor()) || !"ACTIVE".equals(run.principalStatus())
                || !List.of("QUEUED","RUNNING").contains(run.status()) || run.ended()!=null)denied();
        String kind;
        if("TASK".equals(run.kind())) {
            if(run.project()==null || run.task()==null || run.closed()!=null || run.superseded()!=null)denied();
            kind="TASK";
        } else if("SYSTEM_JOB".equals(run.kind())) {
            kind=one(jdbc.query("""
                SELECT job_type FROM system_job_run WHERE tenant_id=? AND run_id=? AND initiator_id=?
                  AND execution_kind='SYSTEM_JOB' AND job_type='MODEL_VERIFICATION' AND target_kind='CONFIGURATION'
                  AND status IN ('QUEUED','RUNNING') AND ended_at IS NULL FOR UPDATE
                """,(row,n)->row.getString(1),run.tenant(),run.id(),run.actor()));
        } else { denied();return null; }
        var scope=schema.scopeManifest(run.scope(),run.tenant(),run.project(),run.actor(),run.id(),run.kind());
        var principal=schema.principalManifest(run.capabilities(),run.tenant(),run.project(),run.actor(),run.id(),run.principal(),scope);
        var allowed=List.copyOf(admitted.delegatedPermissions());
        if(allowed.isEmpty() || new java.util.HashSet<>(allowed).size()!=allowed.size() || !schema.permissions(principal).containsAll(allowed)
                || allowed.stream().anyMatch(value -> !admitted.delegatedActions().contains(value.action()) || "SIMULATION_EVALUATION".equals(value.purpose())))denied();
        var step=store.step(run,admitted.stepId());
        if(!"RUNNING".equals(step.status()) || !step.generation().equals(admitted.leaseGeneration())
                || !Objects.equals(step.owner(),admitted.serviceUri()) || step.expires()==null || !store.now().isBefore(step.expires()))denied();
        var binding=one(permissions.currentBinding(run.actor()));
        if(!run.actor().equals(binding.userId()) || !"ACTIVE".equals(binding.status()))denied();
        var view=new AuthenticatedIdentityBinding.View(binding.mode(),binding.userId(),binding.issuer(),binding.subject());
        // Account, membership, project and action permissions were checked by tokens.execute
        // in this same locked tenant transaction and are rechecked after this callback.
        return new Proof(new Identity(run.tenant(),run.project(),run.id(),run.principal(),run.actor(),kind,
            admitted.stepId(),admitted.actionId(),admitted.leaseGeneration().toString(),step.owner(),run.scope(),run.capabilities(),List.copyOf(admitted.delegatedActions()),view),allowed);
    }
    static final class Proof {
        private final Identity identity;
        private final List<RunTokenAuthoritySchema.Permission> allowed;
        private final Thread owner=Thread.currentThread();
        private boolean active=true;
        private final TenantScope admittedScope=TenantScope.requireCurrent();
        private final java.util.Map<Object,Object> transactionResources=java.util.Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Proof(Identity identity,List<RunTokenAuthoritySchema.Permission> allowed){this.identity=identity;this.allowed=List.copyOf(allowed);if(transactionResources.isEmpty())denied();}
        void requireActive(){if(!active || owner!=Thread.currentThread() || !TransactionSynchronizationManager.isActualTransactionActive()
                || TenantScope.requireCurrent()!=admittedScope || !admittedScope.tenantId().equals(identity.tenant())
                || !admittedScope.subjectId().equals(identity.actor()))denied();
            var current=TransactionSynchronizationManager.getResourceMap();
            if(current.size()!=transactionResources.size() || transactionResources.entrySet().stream().anyMatch(e->current.get(e.getKey())!=e.getValue()))denied();
            TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceGrants");}
        void require(PermissionProtocol.Scope scope,String action,String purpose){requireActive();
            if(!allowed.contains(new RunTokenAuthoritySchema.Permission(scope,action,purpose)))denied();}
        String actorId(){requireActive();return identity.actor();}
        AuthenticatedIdentityBinding.View bindingView(){requireActive();return identity.binding();}
        Identity identity(){requireActive();return identity;}
        private void close(){active=false;}
    }
    record Identity(String tenant,String project,String run,String principal,String actor,String executionKind,String step,
                    String action,String generation,String workerService,String scope,String capabilities,List<String> delegatedActions,AuthenticatedIdentityBinding.View binding) { }
    private static <T>T one(List<T> values){if(values.size()!=1)denied();return values.get(0);}
    private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
