package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.RunTokenPolicyStore;
import com.ruoyi.medical.identity.RunTokenSignature;
import com.ruoyi.medical.identity.RunTokenSigningKeyStore;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

public final class RunTokenAuthority {
    private final RunTokenAuthorityStore store;
    private final JdbcTemplate jdbc;
    private final TenantTransaction transactions;
    private final PermissionMapper permissions;
    private final PermissionAuthority authorization;
    private final RunTokenAuthoritySchema schema;
    private final RunTokenPolicyStore policies;
    private final RunTokenSigningKeyStore signing;
    private final ServiceCertificateIdentity services;

    public RunTokenAuthority(DataSource data,TenantTransaction transactions,PermissionMapper permissions,PermissionAuthority authorization,
            RunTokenAuthoritySchema schema,RunTokenPolicyStore policies,RunTokenSigningKeyStore signing,ServiceCertificateIdentity services) {
        store=new RunTokenAuthorityStore(data); jdbc=new JdbcTemplate(data);
        this.transactions=Objects.requireNonNull(transactions); this.permissions=Objects.requireNonNull(permissions);
        this.authorization=Objects.requireNonNull(authorization); this.schema=Objects.requireNonNull(schema);
        this.policies=Objects.requireNonNull(policies); this.signing=Objects.requireNonNull(signing); this.services=Objects.requireNonNull(services);
    }

    public <T> T execute(HttpServletRequest request,String token,ActionRequest action,Operation<T> operation) throws Exception {
        Objects.requireNonNull(operation); Objects.requireNonNull(action);
        var service=services.require(request);
        var verified=policies.verify(token);
        return transaction(verified.tenantId(),verified.initiatorId(),verified.authEpoch(),current -> {
            var admission=require(verified,service,action.stepId(),action.generation(),current);
            if(!admission.run().id().equals(action.runId()) || !verified.actionScope().contains(action.action())) denied(ErrorCode.ACCESS_DENIED);
            requirePermission(admission,action.scope(),action.action(),action.purpose());
            var binding=store.action(admission.run(),admission.step(),action.actionId());
            if(!binding.input().equals(action.inputSha256()) || !binding.generation().equals(action.generation())
                    || !binding.arguments().equals(action.argumentsSha256()) || !binding.kind().equals(action.kind())
                    || !binding.tool().equals(action.toolVersion()) || !List.of("BOUND","RESERVED","DISPATCHING","IN_FLIGHT").contains(binding.state()))
                denied(ErrorCode.ACTION_STALE);
            var result=operation.apply(new Admission(admission.run().tenant(),admission.run().project(),admission.run().actor(),
                admission.run().principal(),admission.run().id(),admission.step().id(),action.actionId(),action.generation(),admission.now(),
                current.authEpoch(),current.governanceEpoch()));
            // 返回之前核查当前配置和期限，业务写入与准入使用同一个企业事务。
            requirePermission(admission,action.scope(),action.action(),action.purpose()); requireExecution(admission);
            policies.verify(token); recheckService(request,service);
            if(!store.now().isBefore(admission.step().expires()) || !store.now().isBefore(utc(verified.expiresAt()))) denied(ErrorCode.LEASE_STALE);
            return result;
        });
    }

    public Issued issue(HttpServletRequest request,IssueRequest input) throws Exception {
        Objects.requireNonNull(input);
        var service=services.require(request);
        return transaction(input.tenantId(),input.initiatorId(),null,current -> {
            var policy=policies.read();
            var admission=base(input.runId(),input.initiatorId(),input.stepId(),input.generation(),service,policy.policy().audience());
            requireActions(admission,input.actions());
            return issueWithin(request,service,admission,input.actions(),input.lifetime(),current,policy,null);
        });
    }

    public Lease claimStep(HttpServletRequest request,ClaimRequest input) throws Exception {
        Objects.requireNonNull(input); var service=services.require(request);
        return transaction(input.tenantId(),input.initiatorId(),null,current -> {
            var policy=policies.read();
            var admission=base(input.runId(),input.initiatorId(),input.stepId(),null,service,policy.policy().audience());
            var step=admission.step();
            if(!step.revision().equals(input.expectedRevision()) || !("READY".equals(step.status()) && step.owner()==null && step.expires()==null
                    || "RUNNING".equals(step.status()) && step.expires()!=null && !step.expires().isAfter(store.now()))) denied(ErrorCode.LEASE_STALE);
            requireActions(admission,schema.permissions(admission.principal()).stream().map(RunTokenAuthoritySchema.Permission::action).distinct().toList());
            var expires=leaseExpiry(input.lifetime(),policy,store.now());
            WireValues.revision(step.generation().add(BigInteger.ONE).toString());
            store.claim(admission.run(),step,service.serviceUri(),expires);
            var currentStep=store.step(admission.run(),step.id());
            if(!"RUNNING".equals(currentStep.status()) || !service.serviceUri().equals(currentStep.owner()) || !expires.equals(currentStep.expires())
                    || !step.generation().add(BigInteger.ONE).equals(currentStep.generation())
                    || !step.revision().add(BigInteger.ONE).equals(currentStep.revision())) denied(ErrorCode.CONTENT_MISMATCH);
            return leaseReceipt(request,service,admission.run(),currentStep,policy,"run.step.claim","run.step_leased");
        });
    }

    public Lease renewStep(HttpServletRequest request,String token,Duration lifetime) throws Exception {
        var service=services.require(request); var verified=policies.verify(token);
        return transaction(verified.tenantId(),verified.initiatorId(),verified.authEpoch(),current -> {
            var original=store.token(verified.initiatorId(),verified.jti());
            var admission=require(verified,service,verified.stepId(),original.generation(),current);
            requireActions(admission,verified.actionScope());
            var policy=policies.read(); var expires=leaseExpiry(lifetime,policy,store.now());
            if(!expires.isAfter(admission.step().expires())) denied(ErrorCode.LEASE_STALE);
            store.renew(admission.run(),admission.step(),expires);
            var step=store.step(admission.run(),admission.step().id());
            if(!expires.equals(step.expires()) || !admission.step().generation().equals(step.generation())
                    || !admission.step().revision().add(BigInteger.ONE).equals(step.revision())) denied(ErrorCode.CONTENT_MISMATCH);
            policies.verify(token);
            return leaseReceipt(request,service,admission.run(),step,policy,"run.step.renew","run.step_lease_renewed");
        });
    }

    private Lease leaseReceipt(HttpServletRequest request,ServiceCertificateIdentity.Verified service,RunTokenAuthorityStore.Run run,
            RunTokenAuthorityStore.Step step,RunTokenPolicyStore.Current policy,String action,String event) throws Exception {
        var metadata=schema.object(); metadata.put("schema_version","1.0.0"); metadata.put("run_id",run.id()); metadata.put("step_id",step.id());
        metadata.put("lease_owner",step.owner()); metadata.put("lease_generation",step.generation().toString());
        metadata.put("lease_expires_at",step.expires().toInstant(ZoneOffset.UTC).toString()); metadata.put("revision",step.revision().toString());
        metadata.put("service_certificate_sha256",service.certificateSha256());
        store.record(run,java.util.UUID.randomUUID().toString(),action,event,schema.canonical(metadata),schema.manifestSha256());
        recheckService(request,service); requirePolicy(policy,policies.read());
        if(!store.now().isBefore(step.expires())) denied(ErrorCode.LEASE_STALE);
        return new Lease(run.id(),step.id(),step.owner(),step.generation(),step.revision(),step.expires().toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime leaseExpiry(Duration lifetime,RunTokenPolicyStore.Current policy,LocalDateTime now) {
        Objects.requireNonNull(lifetime);
        if(lifetime.isNegative() || lifetime.isZero() || lifetime.getNano()!=0 || lifetime.compareTo(policy.policy().maximumLifetime())>0)
            denied(ErrorCode.ACCESS_DENIED);
        return now.plus(lifetime);
    }

    public Issued renew(HttpServletRequest request,String token,Duration lifetime) throws Exception {
        var service=services.require(request); var verified=policies.verify(token);
        return transaction(verified.tenantId(),verified.initiatorId(),verified.authEpoch(),current -> {
            var original=store.token(verified.initiatorId(),verified.jti());
            var admission=require(verified,service,verified.stepId(),original.generation(),current);
            requireActions(admission,verified.actionScope());
            return issueWithin(request,service,admission,verified.actionScope(),lifetime,current,policies.read(),verified.jti());
        });
    }

    private Issued issueWithin(HttpServletRequest request,ServiceCertificateIdentity.Verified service,Checked admission,List<String> actions,
            Duration lifetime,TenantWatermarks current,RunTokenPolicyStore.Current policy,String replaced) throws Exception {
        Objects.requireNonNull(lifetime);
        if(lifetime.isNegative() || lifetime.isZero() || lifetime.getNano()!=0 || lifetime.compareTo(policy.policy().maximumLifetime())>0)
            denied(ErrorCode.ACCESS_DENIED);
        LocalDateTime now=store.now().withNano(0),expires=now.plus(lifetime);
        if(!expires.isAfter(now) || expires.isAfter(admission.step().expires())) denied(ErrorCode.LEASE_STALE);
        String jti=java.util.UUID.randomUUID().toString();
        var claims=schema.object(); claims.put("iss",policy.policy().issuer()); claims.put("aud",policy.policy().audience());
        claims.put("iat",now.toEpochSecond(ZoneOffset.UTC)); claims.put("exp",expires.toEpochSecond(ZoneOffset.UTC));
        claims.put("tenant_id",admission.run().tenant()); claims.put("project_id",admission.run().project());
        claims.put("initiator_id",admission.run().actor()); claims.put("run_id",admission.run().id()); claims.put("step_id",admission.step().id());
        claims.put("jti",jti); claims.put("auth_epoch",current.authEpoch().toString()); claims.set("action_scope",schema.tree(actions));
        String raw=signing.sign(claims); var verified=policies.verify(raw);
        requirePolicy(policy,policies.read());
        var expected=new RunTokenAuthorityStore.Token(jti,admission.run().principal(),admission.run().project(),admission.run().id(),admission.step().id(),
            policy.policy().audience(),schema.canonical(schema.tree(actions)),current.authEpoch(),now,expires,null,verified.tokenSha256(),service.serviceUri(),
            policy.revision(),policy.contentSha256(),admission.step().generation(),"1.0.0",schema.manifestSha256(),admission.run().actor());
        store.insert(admission.run(),expected,schema.manifestSha256());
        if(!expected.equals(store.token(admission.run().actor(),jti))) denied(ErrorCode.CONTENT_MISMATCH);
        if(replaced!=null) store.revoke(admission.run(),replaced);
        var metadata=schema.object(); metadata.put("schema_version","1.0.0"); metadata.put("run_id",admission.run().id());
        metadata.put("step_id",admission.step().id()); metadata.put("principal_id",admission.run().principal()); metadata.put("jti",jti);
        metadata.put("token_sha256",verified.tokenSha256()); metadata.put("service_uri",service.serviceUri()); metadata.put("policy_revision",policy.revision());
        metadata.put("policy_sha256",policy.contentSha256()); metadata.put("lease_generation",admission.step().generation().toString());
        metadata.put("auth_epoch",current.authEpoch().toString()); metadata.put("replaced_jti",replaced);
        store.record(admission.run(),jti,"run.token.issue","run.token_issued",schema.canonical(metadata),schema.manifestSha256());
        requireExecution(admission); requireActions(admission,actions);
        recheckService(request,service); requirePolicy(policy,policies.read()); policies.verify(raw);
        if(!store.now().isBefore(admission.step().expires())) denied(ErrorCode.LEASE_STALE);
        return new Issued(raw,jti,expires.toInstant(ZoneOffset.UTC));
    }

    private Checked require(RunTokenSignature.Verified verified,ServiceCertificateIdentity.Verified service,String step,BigInteger generation,
            TenantWatermarks current) throws Exception {
        if(verified.stepId()==null || step==null || generation==null || !verified.stepId().equals(step)
                || !verified.authEpoch().equals(current.authEpoch())) denied(ErrorCode.ACCESS_DENIED);
        var admission=base(verified.runId(),verified.initiatorId(),step,generation,service,verified.audience());
        var token=store.token(verified.initiatorId(),verified.jti()); var policy=policies.read();
        if(!verified.jti().equals(token.jti()) || !admission.run().principal().equals(token.principal())
                || !Objects.equals(verified.projectId(),token.project()) || !Objects.equals(verified.projectId(),admission.run().project())
                || !verified.runId().equals(token.run()) || !step.equals(token.step()) || !verified.audience().equals(token.audience())
                || !current.authEpoch().equals(token.auth()) || !utc(verified.issuedAt()).equals(token.issued()) || !utc(verified.expiresAt()).equals(token.expires())
                || token.revoked()!=null || !verified.tokenSha256().equals(token.sha256()) || !service.serviceUri().equals(token.service())
                || !generation.equals(token.generation()) || !policy.revision().equals(token.policyRevision())
                || !policy.contentSha256().equals(token.policySha256()) || !"1.0.0".equals(token.schema())
                || !schema.manifestSha256().equals(token.manifest()) || !verified.initiatorId().equals(token.creator())
                || !schema.canonical(schema.tree(verified.actionScope())).equals(token.actions())
                || !admission.now().isBefore(token.expires())) denied(ErrorCode.ACCESS_DENIED);
        return admission;
    }

    private Checked base(String runId,String actor,String stepId,BigInteger generation,ServiceCertificateIdentity.Verified service,String audience)
            throws Exception {
        var run=store.run(runId,actor); var step=store.step(run,stepId); var now=store.now();
        if(!"ACTIVE".equals(run.principalStatus()) || !"RUNNING".equals(run.status()) || !actor.equals(run.executionActor())
                || run.ended()!=null || run.closed()!=null || run.superseded()!=null || !"1.0.0".equals(run.schema())
                || !schema.manifestSha256().equals(run.manifest()) || !"1.0.0".equals(run.principalSchema())
                || !schema.manifestSha256().equals(run.principalManifest())) denied(ErrorCode.OBJECT_UNAVAILABLE);
        if(generation!=null && (!"RUNNING".equals(step.status()) || !generation.equals(step.generation()) || generation.signum()<=0
                || !service.serviceUri().equals(step.owner()) || step.expires()==null || !now.isBefore(step.expires()))) denied(ErrorCode.LEASE_STALE);
        if(!"PUBLISHED".equals(step.roleStatus()) || !List.of("ACTIVE","PUBLISHED").contains(step.configurationStatus())
                || !step.roleSha256().equals(step.registeredSha256()) || !"CAPABILITY_VERSION".equals(step.entityKind())
                || step.roleProject()!=null && !step.roleProject().equals(run.project()) || !"1.0.0".equals(step.roleSchema())
                || !schema.manifestSha256().equals(step.roleManifest())) denied(ErrorCode.OBJECT_UNAVAILABLE);
        var scope=schema.scopeManifest(run.scope(),run.tenant(),run.project(),actor,run.id(),run.kind());
        var principal=schema.principalManifest(run.capabilities(),run.tenant(),run.project(),actor,run.id(),run.principal(),scope);
        schema.role(step.roleScope(),principal); schema.service(principal,service.serviceUri(),audience);
        var binding=single(permissions.currentBinding(actor));
        if(!actor.equals(binding.userId()) || !"ACTIVE".equals(binding.status())) denied(ErrorCode.ACCESS_DENIED);
        List<String> account;
        if("local".equals(binding.mode())) {
            if(!actor.equals(binding.localUserId())) denied(ErrorCode.ACCESS_DENIED);
            account=jdbc.query("SELECT user_id FROM platform_local_account WHERE user_id=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getString(1),actor);
        } else if("oidc".equals(binding.mode())) {
            if(binding.localUserId()!=null) denied(ErrorCode.ACCESS_DENIED);
            account=jdbc.query("SELECT user_id FROM platform_identity WHERE identity_id=? AND user_id=? AND issuer=? AND subject=? AND status='ACTIVE' FOR UPDATE",
                (r,n)->r.getString(1),binding.identityId(),actor,binding.issuer(),binding.subject());
        } else throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if(account.size()!=1) denied(ErrorCode.ACCESS_DENIED);
        var members=jdbc.query("""
            SELECT user_id FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE'
              AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6)) FOR UPDATE
            """,(r,n)->r.getString(1),run.tenant(),actor);
        if(members.size()!=1 || run.project()!=null && !"ACTIVE".equals(single(permissions.projectStatus(run.project())))) denied(ErrorCode.ACCESS_DENIED);
        var checked=new Checked(run,step,principal,binding.mode(),now);
        requireExecution(checked);
        return checked;
    }

    private void requireExecution(Checked admission) throws Exception {
        var run=admission.run();
        if("TASK".equals(run.kind())) {
            if(run.task()==null || run.project()==null) denied(ErrorCode.OBJECT_UNAVAILABLE);
            authorization.requireResource(new PermissionProtocol.Scope(run.tenant(),run.project(),"TASK",run.task()),
                run.actor(),admission.mode(),"task.run","TASK_EXECUTION",permissions.resourceGrants(run.actor(),"task.run"),store.now());
        } else {
            for(var permission:schema.permissions(admission.principal())) requirePermission(admission,permission.scope(),permission.action(),permission.purpose());
        }
    }

    private void requireActions(Checked admission,List<String> actions) throws Exception {
        if(actions==null || actions.isEmpty() || new java.util.HashSet<>(actions).size()!=actions.size()) denied(ErrorCode.ACCESS_DENIED);
        var allowed=schema.permissions(admission.principal());
        for(String action:actions) {
            if(action==null || !action.matches("[a-z][a-z0-9_.-]{0,63}")) denied(ErrorCode.ACCESS_DENIED);
            var requested=allowed.stream().filter(value -> action.equals(value.action())).toList();
            if(requested.isEmpty()) denied(ErrorCode.ACCESS_DENIED);
            for(var value:requested) requirePermission(admission,value.scope(),value.action(),value.purpose());
        }
    }

    private void requirePermission(Checked admission,PermissionProtocol.Scope scope,String action,String purpose) throws Exception {
        if(!schema.permissions(admission.principal()).contains(new RunTokenAuthoritySchema.Permission(scope,action,purpose))) denied(ErrorCode.ACCESS_DENIED);
        authorization.requireResource(scope,admission.run().actor(),admission.mode(),action,purpose,
            permissions.resourceGrants(admission.run().actor(),action),store.now());
    }

    private <T> T transaction(String tenant,String actor,BigInteger expectedAuth,CheckedOperation<T> operation) {
        WireValues.uuid(tenant); WireValues.uuid(actor);
        var epochs=store.epochs(tenant);
        if(expectedAuth!=null && !expectedAuth.equals(epochs.authEpoch())) denied(ErrorCode.SCOPE_STALE);
        try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ZERO,epochs.authEpoch(),epochs.governanceEpoch()))) {
            return transactions.execute(current -> ThrowingSupplier.of(() -> operation.apply(current)).get());
        }
    }

    private static LocalDateTime utc(java.time.Instant value) { return LocalDateTime.ofInstant(value,ZoneOffset.UTC); }
    private static void requirePolicy(RunTokenPolicyStore.Current expected,RunTokenPolicyStore.Current actual) {
        if(!expected.revision().equals(actual.revision()) || !expected.contentSha256().equals(actual.contentSha256())) denied(ErrorCode.SCOPE_STALE);
    }
    private void recheckService(HttpServletRequest request,ServiceCertificateIdentity.Verified expected) throws Exception {
        var current=services.require(request);
        if(!expected.serviceUri().equals(current.serviceUri()) || !expected.certificateSha256().equals(current.certificateSha256())
                || !expected.trustRevision().equals(current.trustRevision())) denied(ErrorCode.SCOPE_STALE);
    }
    private static <T> T single(List<T> values) { if(values.size()!=1) denied(ErrorCode.ACCESS_DENIED); return values.get(0); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    private record Checked(RunTokenAuthorityStore.Run run,RunTokenAuthorityStore.Step step,JsonNode principal,String mode,LocalDateTime now) { }
    @FunctionalInterface private interface CheckedOperation<T> { T apply(TenantWatermarks current) throws Exception; }
    @FunctionalInterface public interface Operation<T> { T apply(Admission admission) throws Exception; }

    public record ActionRequest(String runId,String stepId,String actionId,BigInteger generation,String inputSha256,String argumentsSha256,
            String kind,String toolVersion,PermissionProtocol.Scope scope,String action,String purpose) {
        public ActionRequest {
            WireValues.uuid(runId); WireValues.uuid(stepId); WireValues.uuid(actionId); WireValues.hash(inputSha256); WireValues.hash(argumentsSha256);
            WireValues.revision(Objects.requireNonNull(generation).toString()); Objects.requireNonNull(scope); Objects.requireNonNull(action);
            if(kind==null || kind.isBlank() || toolVersion==null || toolVersion.isBlank() || purpose==null || purpose.isBlank()) denied(ErrorCode.SCHEMA_INVALID);
        }
    }
    public record IssueRequest(String tenantId,String initiatorId,String runId,String stepId,BigInteger generation,List<String> actions,Duration lifetime) {
        public IssueRequest {
            WireValues.uuid(tenantId); WireValues.uuid(initiatorId); WireValues.uuid(runId); WireValues.uuid(stepId);
            WireValues.revision(Objects.requireNonNull(generation).toString()); actions=List.copyOf(actions); Objects.requireNonNull(lifetime);
        }
    }
    public record ClaimRequest(String tenantId,String initiatorId,String runId,String stepId,BigInteger expectedRevision,Duration lifetime) {
        public ClaimRequest {
            WireValues.uuid(tenantId); WireValues.uuid(initiatorId); WireValues.uuid(runId); WireValues.uuid(stepId);
            WireValues.revision(Objects.requireNonNull(expectedRevision).toString()); Objects.requireNonNull(lifetime);
        }
    }
    public static final class Lease {
        private final String runId,stepId,owner;
        private final BigInteger generation,revision;
        private final java.time.Instant expiresAt;
        private Lease(String run,String step,String owner,BigInteger generation,BigInteger revision,java.time.Instant expires) {
            runId=run; stepId=step; this.owner=owner; this.generation=generation; this.revision=revision; expiresAt=expires;
        }
        public String runId() { return runId; } public String stepId() { return stepId; } public String owner() { return owner; }
        public BigInteger generation() { return generation; } public BigInteger revision() { return revision; }
        public java.time.Instant expiresAt() { return expiresAt; }
        @Override public String toString() { return "PersistentRunStepLease"; }
    }
    public static final class Admission {
        private final String tenantId,projectId,initiatorId,principalId,runId,stepId,actionId;
        private final BigInteger leaseGeneration,authEpoch,governanceEpoch;
        private final LocalDateTime admittedAt;
        private Admission(String tenant,String project,String actor,String principal,String run,String step,String action,BigInteger generation,
                LocalDateTime now,BigInteger auth,BigInteger governance) {
            tenantId=tenant; projectId=project; initiatorId=actor; principalId=principal; runId=run; stepId=step; actionId=action;
            leaseGeneration=generation; admittedAt=now; authEpoch=auth; governanceEpoch=governance;
        }
        public String tenantId() { return tenantId; } public String projectId() { return projectId; }
        public String initiatorId() { return initiatorId; } public String principalId() { return principalId; }
        public String runId() { return runId; } public String stepId() { return stepId; } public String actionId() { return actionId; }
        public BigInteger leaseGeneration() { return leaseGeneration; } public BigInteger authEpoch() { return authEpoch; }
        public BigInteger governanceEpoch() { return governanceEpoch; } public LocalDateTime admittedAt() { return admittedAt; }
        @Override public String toString() { return "AuthorizedRunAction"; }
    }
    public static final class Issued {
        private final String token,jti;
        private final java.time.Instant expiresAt;
        private Issued(String token,String jti,java.time.Instant expiresAt) { this.token=token; this.jti=jti; this.expiresAt=expiresAt; }
        public String token() { return token; } public String jti() { return jti; } public java.time.Instant expiresAt() { return expiresAt; }
        @Override public String toString() { return "IssuedRunToken"; }
    }
}
