package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** Session-free, simulation-only actor admission. A worker proof is never an application session. */
public final class SimulationWorkerAccess {
    private final JdbcTemplate jdbc;
    private final RunTokenAuthorityStore store;
    private final TenantTransaction transactions;
    private final PermissionMapper permissions;
    private final RunTokenAuthoritySchema schema;
    private final ServiceCertificateIdentity services;
    private final PermissionAuthority grants;
    public SimulationWorkerAccess(DataSource data,TenantTransaction transactions,PermissionMapper permissions,
            RunTokenAuthoritySchema schema,ServiceCertificateIdentity services,PermissionAuthority grants) {
        this(new JdbcTemplate(data),new RunTokenAuthorityStore(data),transactions,permissions,schema,services,grants);
    }
    SimulationWorkerAccess(JdbcTemplate jdbc,RunTokenAuthorityStore store,TenantTransaction transactions,PermissionMapper permissions,
            RunTokenAuthoritySchema schema,ServiceCertificateIdentity services,PermissionAuthority grants) {
        this.jdbc=jdbc;this.store=store;this.transactions=transactions;this.permissions=permissions;this.schema=schema;this.services=services;this.grants=grants;
    }
    public <T> T execute(HttpServletRequest http,String tenant,String run,Function<EnterpriseAccess.Admission,T> operation) {
        return ThrowingSupplier.of(() -> {
            WireValues.uuid(tenant); WireValues.uuid(run);
            var service=services.require(http);
            // Discovery derives the actor only from the persisted bounded SYSTEM_JOB identity.
            String actor=one(jdbc.query("""
                SELECT initiator_id FROM system_job_run WHERE tenant_id=? AND run_id=?
                  AND execution_kind='SYSTEM_JOB' AND job_type='SIMULATION_EVALUATION' AND target_kind='EVALUATION'
                """,(r,n)->r.getString(1),tenant,run));
            var epochs=store.epochs(tenant);
            try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ZERO,epochs.authEpoch(),epochs.governanceEpoch()))) {
                return transactions.execute(current -> ThrowingSupplier.of(() -> {
                    var proof=require(tenant,run,actor,service);
                    var context=new BusinessSessionProtocol.Context(tenant,"0",current.authEpoch().toString(),current.governanceEpoch().toString());
                    var result=operation.apply(new EnterpriseAccess.Admission(proof,context,current));
                    var again=require(tenant,run,actor,service);
                    var currentService=services.require(http);
                    if(!proof.identity().equals(again.identity()) || !service.serviceUri().equals(currentService.serviceUri())
                            || !Objects.equals(service.certificateSha256(),currentService.certificateSha256())
                            || !Objects.equals(service.trustRevision(),currentService.trustRevision())) denied();
                    return result;
                }).get());
            }
        }).get();
    }
    private Proof require(String tenant,String runId,String actor,ServiceCertificateIdentity.Verified service) throws Exception {
        var run=store.run(runId,actor);
        if(!tenant.equals(run.tenant()) || !actor.equals(run.actor()) || !actor.equals(run.executionActor())
                || !"SYSTEM_JOB".equals(run.kind()) || !"ACTIVE".equals(run.principalStatus())
                || !List.of("QUEUED","RUNNING").contains(run.status()) || run.ended()!=null
                || !"1.0.0".equals(run.schema()) || !"1.0.0".equals(run.principalSchema())
                || !schema.manifestSha256().equals(run.manifest()) || !schema.manifestSha256().equals(run.principalManifest())) denied();
        String evaluation=one(jdbc.query("""
            SELECT target_id FROM system_job_run WHERE tenant_id=? AND run_id=? AND initiator_id=?
              AND execution_kind='SYSTEM_JOB' AND job_type='SIMULATION_EVALUATION' AND target_kind='EVALUATION'
              AND status IN ('QUEUED','RUNNING') AND ended_at IS NULL FOR UPDATE
            """,(r,n)->r.getString(1),tenant,runId,actor));
        var scope=schema.scopeManifest(run.scope(),tenant,run.project(),actor,runId,"SYSTEM_JOB");
        var principal=schema.principalManifest(run.capabilities(),tenant,run.project(),actor,runId,run.principal(),scope);
        var allowed=schema.permissions(principal);
        if(allowed.size()!=1 || scope.required("resources").size()!=1 || principal.required("services").size()!=1) denied();
        var permission=allowed.get(0);
        if(!SimulationExecutionPermissionPolicy.ACTION.equals(permission.action()) || !SimulationExecutionPermissionPolicy.PURPOSE.equals(permission.purpose())
                || !new PermissionProtocol.Scope(tenant,run.project(),"EVALUATION",evaluation).equals(permission.scope())) denied();
        schema.service(principal,service.serviceUri(),SimulationRunTokenVerifier.AUDIENCE);
        var binding=one(permissions.currentBinding(actor));
        if(!actor.equals(binding.userId()) || !"ACTIVE".equals(binding.status())) denied();
        List<String> accounts;
        if("local".equals(binding.mode())) {
            if(!actor.equals(binding.localUserId())) denied();
            accounts=jdbc.query("SELECT user_id FROM platform_local_account WHERE user_id=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getString(1),actor);
        } else if("oidc".equals(binding.mode())) {
            if(binding.localUserId()!=null) denied();
            accounts=jdbc.query("SELECT user_id FROM platform_identity WHERE identity_id=? AND user_id=? AND issuer=? AND subject=? AND status='ACTIVE' FOR UPDATE",
                (r,n)->r.getString(1),binding.identityId(),actor,binding.issuer(),binding.subject());
        } else { denied(); return null; }
        if(!actor.equals(one(accounts))) denied();
        String member=one(jdbc.query("""
            SELECT member_id FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE'
              AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6)) FOR UPDATE
            """,(r,n)->r.getString(1),tenant,actor));
        if(run.project()!=null && !"ACTIVE".equals(one(permissions.projectStatus(run.project())))) denied();
        grants.requireResource(permission.scope(),actor,binding.mode(),SimulationExecutionPermissionPolicy.ACTION,
            SimulationExecutionPermissionPolicy.PURPOSE,permissions.resourceGrants(actor,SimulationExecutionPermissionPolicy.ACTION),store.now());
        var view=new AuthenticatedIdentityBinding.View(binding.mode(),binding.userId(),binding.issuer(),binding.subject());
        return new Proof(actor,view,new Identity(tenant,runId,run.principal(),actor,member,view,service.serviceUri(),run.scope(),run.capabilities()));
    }
    /** Private constructor: only successful real persisted admission creates this proof. */
    static final class Proof {
        private final String actor;
        private final AuthenticatedIdentityBinding.View binding;
        private final Identity identity;
        private Proof(String actor,AuthenticatedIdentityBinding.View binding,Identity identity){this.actor=actor;this.binding=binding;this.identity=identity;}
        String actorId(){return actor;}
        AuthenticatedIdentityBinding.View bindingView(){return binding;}
        Identity identity(){return identity;}
    }
    record Identity(String tenant,String run,String principal,String actor,String member,AuthenticatedIdentityBinding.View binding,
                    String service,String scope,String capabilities) { }
    private static <T>T one(List<T> values){if(values.size()!=1) denied();return values.get(0);}
    private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
