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
    private final FormalModelVerificationBootstrap modelVerification;
    private final FormalTaskNodeAuthority taskNodes;
    private final TaskPlanDraftTokenAuthority planDraft;
    private final DocumentReviewTokenAuthority documentReview;

    public RunTokenAuthority(DataSource data,TenantTransaction transactions,PermissionMapper permissions,PermissionAuthority authorization,
            RunTokenAuthoritySchema schema,RunTokenPolicyStore policies,RunTokenSigningKeyStore signing,ServiceCertificateIdentity services) {
        this(data,transactions,permissions,authorization,schema,policies,signing,services,null);
    }
    public RunTokenAuthority(DataSource data,TenantTransaction transactions,PermissionMapper permissions,PermissionAuthority authorization,
            RunTokenAuthoritySchema schema,RunTokenPolicyStore policies,RunTokenSigningKeyStore signing,ServiceCertificateIdentity services,
            FormalModelVerificationBootstrap modelVerification) {
        this(data,transactions,permissions,authorization,schema,policies,signing,services,modelVerification,null);
    }
    public RunTokenAuthority(DataSource data,TenantTransaction transactions,PermissionMapper permissions,PermissionAuthority authorization,
            RunTokenAuthoritySchema schema,RunTokenPolicyStore policies,RunTokenSigningKeyStore signing,ServiceCertificateIdentity services,
            FormalModelVerificationBootstrap modelVerification,FormalTaskNodeAuthority taskNodes) {
        this(data,transactions,permissions,authorization,schema,policies,signing,services,modelVerification,taskNodes,null);
    }
    public RunTokenAuthority(DataSource data,TenantTransaction transactions,PermissionMapper permissions,PermissionAuthority authorization,
            RunTokenAuthoritySchema schema,RunTokenPolicyStore policies,RunTokenSigningKeyStore signing,ServiceCertificateIdentity services,
            FormalModelVerificationBootstrap modelVerification,FormalTaskNodeAuthority taskNodes,TaskPlanDraftTokenAuthority planDraft) {
        this(data,transactions,permissions,authorization,schema,policies,signing,services,modelVerification,taskNodes,planDraft,null);
    }
    public RunTokenAuthority(DataSource data,TenantTransaction transactions,PermissionMapper permissions,PermissionAuthority authorization,
            RunTokenAuthoritySchema schema,RunTokenPolicyStore policies,RunTokenSigningKeyStore signing,ServiceCertificateIdentity services,
            FormalModelVerificationBootstrap modelVerification,FormalTaskNodeAuthority taskNodes,TaskPlanDraftTokenAuthority planDraft,DocumentReviewTokenAuthority documentReview) {
        this.documentReview=documentReview;this.modelVerification=modelVerification; this.taskNodes=taskNodes; this.planDraft=planDraft;
        store=new RunTokenAuthorityStore(data); jdbc=new JdbcTemplate(data);
        this.transactions=Objects.requireNonNull(transactions); this.permissions=Objects.requireNonNull(permissions);
        this.authorization=Objects.requireNonNull(authorization); this.schema=Objects.requireNonNull(schema);
        this.policies=Objects.requireNonNull(policies); this.signing=Objects.requireNonNull(signing); this.services=Objects.requireNonNull(services);
    }

    public <T> T execute(HttpServletRequest request,String token,ActionRequest action,Operation<T> operation) throws Exception {
        Objects.requireNonNull(operation); Objects.requireNonNull(action);
        var service=services.require(request);
        var verified=policies.verify(token);
        // Existing action authorization uses within-bound totals. Creating a new paid
        // invocation still requires its independent exact priced atomic reservation.
        return transaction(request,verified.tenantId(),verified.initiatorId(),verified.runId(),verified.stepId(),verified.authEpoch(),false,(current,taskNode) -> {
            var admission=require(verified,service,action.stepId(),action.generation(),current,taskNode);
            if(!admission.run().id().equals(action.runId()) || !verified.actionScope().contains(action.action())) denied(ErrorCode.ACCESS_DENIED);
            requirePermission(admission,action.scope(),action.action(),action.purpose());
            var binding=store.action(admission.run(),admission.step(),action.actionId());
            if(!binding.input().equals(action.inputSha256()) || !binding.generation().equals(action.generation())
                    || !binding.arguments().equals(action.argumentsSha256()) || !binding.kind().equals(action.kind())
                    || !binding.tool().equals(action.toolVersion()) || !List.of("BOUND","RESERVED","DISPATCHING","IN_FLIGHT").contains(binding.state()))
                denied(ErrorCode.ACTION_STALE);
            // Publication/input preparation can be nontrivial; do not enter the business
            // operation using the earlier admission timestamp after a lease/token expires.
            if(!store.now().isBefore(admission.step().expires())||!store.now().isBefore(utc(verified.expiresAt())))denied(ErrorCode.LEASE_STALE);
            var result=operation.apply(new Admission(admission.run().tenant(),admission.run().project(),admission.run().actor(),
                admission.run().principal(),admission.run().id(),admission.step().id(),action.actionId(),action.generation(),admission.now(),
                current.authEpoch(),current.governanceEpoch(),verified.actionScope(),service.serviceUri(),
                effectivePermissions(admission,verified.actionScope()),admission.taskNode()));
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
        return transaction(request,input.tenantId(),input.initiatorId(),input.runId(),input.stepId(),null,true,(current,taskNode) -> {
            var policy=policies.read();
            var admission=base(input.runId(),input.initiatorId(),input.stepId(),input.generation(),service,policy.policy().audience(),taskNode);
            requireActions(admission,input.actions());
            return issueWithin(request,service,admission,input.actions(),input.lifetime(),current,policy,null);
        });
    }

    public Lease claimStep(HttpServletRequest request,ClaimRequest input) throws Exception {
        return claim(request,input,null,null,null,null);
    }

    /** TASK scheduler-only claim. A caller cannot mint this committed command proof.
     * The ordinary public claim path and its expired-lease semantics are unchanged. */
    Lease claimReserved(HttpServletRequest request,ClaimRequest input,TaskRunDispatchReservation.Pending reservation) throws Exception {
        return claim(request,input,Objects.requireNonNull(reservation),null,null,null);
    }
    /** N03 deterministic command claim. Only the original privately minted committed reservation is accepted. */
    Lease claimStrictFieldReserved(HttpServletRequest request,ClaimRequest input,TaskStrictFieldDispatchReservation.Pending reservation) throws Exception {
        return claim(request,input,null,Objects.requireNonNull(reservation),null,null);
    }
    Lease claimWriterReserved(HttpServletRequest request,ClaimRequest input,TaskSectionWriterDispatchReservation.Pending reservation) throws Exception {return claim(request,input,null,null,Objects.requireNonNull(reservation),null);}
    Lease claimVerifierReserved(HttpServletRequest request,ClaimRequest input,TaskSourceVerifierDispatchReservation.Pending reservation) throws Exception {return claim(request,input,null,null,null,Objects.requireNonNull(reservation));}

    private Lease claim(HttpServletRequest request,ClaimRequest input,TaskRunDispatchReservation.Pending reservation,TaskStrictFieldDispatchReservation.Pending strictField,TaskSectionWriterDispatchReservation.Pending writer,TaskSourceVerifierDispatchReservation.Pending verifier) throws Exception {
        Objects.requireNonNull(input); var service=services.require(request);
        return transaction(request,input.tenantId(),input.initiatorId(),input.runId(),input.stepId(),null,true,(current,taskNode) -> {
            var policy=policies.read();
            var admission=base(input.runId(),input.initiatorId(),input.stepId(),null,service,policy.policy().audience(),taskNode);
            var step=admission.step();
            if(!step.revision().equals(input.expectedRevision()) || !("READY".equals(step.status()) && step.owner()==null && step.expires()==null
                    || "RUNNING".equals(step.status()) && step.expires()!=null && !step.expires().isAfter(store.now()))) denied(ErrorCode.LEASE_STALE);
            requireActions(admission,nodePermissions(admission).stream().map(RunTokenAuthoritySchema.Permission::action).distinct().toList());
            var expires=leaseExpiry(input.lifetime(),policy,store.now());
            if(reservation!=null)reservation.before(taskNode,input,service,admission.run(),step);
            if(strictField!=null)strictField.before(taskNode,input,service,admission.run(),step);
            if(writer!=null)writer.before(taskNode,input,service,admission.run(),step);
            if(verifier!=null)verifier.before(taskNode,input,service,admission.run(),step);
            WireValues.revision(step.generation().add(BigInteger.ONE).toString());
            store.claim(admission.run(),step,service.serviceUri(),expires);
            var currentStep=store.step(admission.run(),step.id());
            if(!"RUNNING".equals(currentStep.status()) || !service.serviceUri().equals(currentStep.owner()) || !expires.equals(currentStep.expires())
                    || !step.generation().add(BigInteger.ONE).equals(currentStep.generation())
                    || !step.revision().add(BigInteger.ONE).equals(currentStep.revision())) denied(ErrorCode.CONTENT_MISMATCH);
            if(admission.planDraft()!=null)planDraft.bindClaimedHost(admission.run(),currentStep);
            if(admission.documentReview()!=null)documentReview.bindClaimedHost(admission.run(),currentStep);
            var lease=leaseReceipt(request,service,admission.run(),currentStep,policy,"run.step.claim","run.step_leased");
            if(reservation!=null)reservation.after(taskNode,lease);
            if(strictField!=null)strictField.after(taskNode,lease);
            if(writer!=null)writer.after(taskNode,lease);
            if(verifier!=null)verifier.after(taskNode,lease);
            return lease;
        });
    }

    public Lease renewStep(HttpServletRequest request,String token,Duration lifetime) throws Exception {
        var service=services.require(request); var verified=policies.verify(token);
        return transaction(request,verified.tenantId(),verified.initiatorId(),verified.runId(),verified.stepId(),verified.authEpoch(),false,(current,taskNode) -> {
            var original=store.token(verified.initiatorId(),verified.jti());
            var admission=require(verified,service,verified.stepId(),original.generation(),current,taskNode);
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
        return transaction(request,verified.tenantId(),verified.initiatorId(),verified.runId(),verified.stepId(),verified.authEpoch(),false,(current,taskNode) -> {
            var original=store.token(verified.initiatorId(),verified.jti());
            var admission=require(verified,service,verified.stepId(),original.generation(),current,taskNode);
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
            TenantWatermarks current,FormalTaskNodeAuthority.Verified taskNode) throws Exception {
        if(verified.stepId()==null || step==null || generation==null || !verified.stepId().equals(step)
                || !verified.authEpoch().equals(current.authEpoch())) denied(ErrorCode.ACCESS_DENIED);
        var admission=base(verified.runId(),verified.initiatorId(),step,generation,service,verified.audience(),taskNode);
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

    // Retain the legacy metadata-only path and its tests. It cannot acquire a TASK proof.
    private Checked base(String runId,String actor,String stepId,BigInteger generation,ServiceCertificateIdentity.Verified service,String audience)
            throws Exception {
        return base(runId,actor,stepId,generation,service,audience,null);
    }

    private Checked base(String runId,String actor,String stepId,BigInteger generation,ServiceCertificateIdentity.Verified service,String audience,FormalTaskNodeAuthority.Verified taskNode)
            throws Exception {
        var run=store.run(runId,actor); var step=store.step(run,stepId); var now=store.now();
        if(!"ACTIVE".equals(run.principalStatus()) || !"RUNNING".equals(run.status()) || !actor.equals(run.executionActor())
                || run.ended()!=null || run.closed()!=null || run.superseded()!=null || !"1.0.0".equals(run.schema())
                || !schema.manifestSha256().equals(run.manifest()) || !"1.0.0".equals(run.principalSchema())
                || !schema.manifestSha256().equals(run.principalManifest())) denied(ErrorCode.OBJECT_UNAVAILABLE);
        if(generation!=null && (!"RUNNING".equals(step.status()) || !generation.equals(step.generation()) || generation.signum()<=0
                || !service.serviceUri().equals(step.owner()) || step.expires()==null || !now.isBefore(step.expires()))) denied(ErrorCode.LEASE_STALE);
        var scope=schema.scopeManifest(run.scope(),run.tenant(),run.project(),actor,run.id(),run.kind());
        var principal=schema.principalManifest(run.capabilities(),run.tenant(),run.project(),actor,run.id(),run.principal(),scope);
        schema.service(principal,service.serviceUri(),audience);
        FormalModelVerificationBootstrap.Verified bootstrap=null;
        if(modelVerification!=null && "SYSTEM_JOB".equals(run.kind())) {
            var kinds=jdbc.query("SELECT job_type FROM system_job_run WHERE tenant_id=? AND run_id=? AND initiator_id=? AND job_type='MODEL_VERIFICATION' AND target_kind='CONFIGURATION' FOR UPDATE",(r,n)->r.getString(1),run.tenant(),run.id(),actor);
            if(kinds.size()>1)denied(ErrorCode.CONTENT_MISMATCH);
            if(kinds.size()==1){
                String roleVersion=single(jdbc.query("SELECT role_version FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? AND project_key=? FOR UPDATE",(r,n)->r.getString(1),run.tenant(),run.id(),stepId,run.project()==null?"ENTERPRISE":run.project()));
                bootstrap=modelVerification.require(run.tenant(),actor,run.id(),stepId,roleVersion,step.roleSha256(),service.serviceUri(),audience);
                if(!schema.permissions(principal).equals(bootstrap.nodePermissions()))denied(ErrorCode.CONTENT_MISMATCH);
            }
        }
        TaskPlanDraftRuntimeAuthority.Bootstrap planBootstrap=null;
        if("SYSTEM_JOB".equals(run.kind())) {
            var drafts=jdbc.query("SELECT j.run_id FROM system_job_run j JOIN task_plan_draft_job d ON d.tenant_id=j.tenant_id AND d.run_id=j.run_id WHERE j.tenant_id=? AND j.run_id=? AND j.initiator_id=? AND j.job_type='PLAN_DRAFT' AND j.target_kind='TASK' AND j.target_id=d.task_id AND j.target_revision=d.task_revision FOR UPDATE",(r,n)->r.getString(1),run.tenant(),run.id(),actor);
            if(drafts.size()>1)denied(ErrorCode.CONTENT_MISMATCH);
            if(drafts.size()==1){
                if(planDraft==null)denied(ErrorCode.DEPENDENCY_UNAVAILABLE);
                planBootstrap=planDraft.require(run.tenant(),actor,run.id(),stepId,step.roleSha256(),service.serviceUri(),audience);
                if(!schema.permissions(principal).equals(planBootstrap.nodePermissions()))denied(ErrorCode.CONTENT_MISMATCH);
            }
        }
        DocumentReviewRuntimeAuthority.Bootstrap documentBootstrap=null;
        if("SYSTEM_JOB".equals(run.kind())) {
            var reviews=jdbc.query("SELECT j.run_id FROM system_job_run j JOIN document_review_execution d ON d.tenant_id=j.tenant_id AND d.run_id=j.run_id WHERE j.tenant_id=? AND j.run_id=? AND j.initiator_id=? AND j.job_type='CHECK_DOCUMENT' AND j.target_kind='DOCUMENT' AND j.target_id=d.document_id AND j.target_revision=d.document_revision FOR UPDATE",(r,n)->r.getString(1),run.tenant(),run.id(),actor);
            if(reviews.size()>1)denied(ErrorCode.CONTENT_MISMATCH);
            if(reviews.size()==1){if(documentReview==null)denied(ErrorCode.DEPENDENCY_UNAVAILABLE);documentBootstrap=documentReview.require(run.tenant(),actor,run.id(),stepId,step.roleSha256(),service.serviceUri(),audience);if(!schema.permissions(principal).equals(documentBootstrap.nodePermissions()))denied(ErrorCode.CONTENT_MISMATCH);}
        }
        if(!step.roleSha256().equals(step.registeredSha256()) || !"CAPABILITY_VERSION".equals(step.entityKind())
                || step.roleProject()!=null && !step.roleProject().equals(run.project()) || !"1.0.0".equals(step.roleSchema()))denied(ErrorCode.OBJECT_UNAVAILABLE);
        if(taskNode!=null && !"TASK".equals(run.kind()))denied(ErrorCode.ACCESS_DENIED);
        if(taskNode!=null) taskNode.requireIdentity(run.tenant(),actor,run.id(),stepId,step.roleSha256(),service.serviceUri(),audience);
        if(bootstrap==null && taskNode==null && planBootstrap==null && documentBootstrap==null && (!"PUBLISHED".equals(step.roleStatus()) || !List.of("ACTIVE","PUBLISHED").contains(step.configurationStatus())
                || !schema.manifestSha256().equals(step.roleManifest())))denied(ErrorCode.OBJECT_UNAVAILABLE);
        if(taskNode==null && planBootstrap==null && documentBootstrap==null) schema.role(step.roleScope(),principal);
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
        var checked=new Checked(run,step,principal,binding.mode(),now,bootstrap,taskNode,planBootstrap,documentBootstrap);
        requireExecution(checked);
        return checked;
    }

    private void requireExecution(Checked admission) throws Exception {
        if(admission.bootstrap()!=null)admission.bootstrap().requireUnchanged();
        if(admission.taskNode()!=null)admission.taskNode().requireUnchanged();
        if(admission.planDraft()!=null)admission.planDraft().requireUnchanged();
        if(admission.documentReview()!=null)admission.documentReview().requireUnchanged();
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
        var allowed=nodePermissions(admission);
        for(String action:actions) {
            if(action==null || !action.matches("[a-z][a-z0-9_.-]{0,63}")) denied(ErrorCode.ACCESS_DENIED);
            var requested=allowed.stream().filter(value -> action.equals(value.action())).toList();
            if(requested.isEmpty()) denied(ErrorCode.ACCESS_DENIED);
            for(var value:requested) requirePermission(admission,value.scope(),value.action(),value.purpose());
        }
    }

    private void requirePermission(Checked admission,PermissionProtocol.Scope scope,String action,String purpose) throws Exception {
        if(!nodePermissions(admission).contains(new RunTokenAuthoritySchema.Permission(scope,action,purpose))) denied(ErrorCode.ACCESS_DENIED);
        authorization.requireResource(scope,admission.run().actor(),admission.mode(),action,purpose,
            permissions.resourceGrants(admission.run().actor(),action),store.now());
    }

    private List<RunTokenAuthoritySchema.Permission> nodePermissions(Checked admission) throws Exception {
        if(admission.documentReview()!=null)return RuntimeNodePermissions.intersect(schema.permissions(admission.principal()),admission.documentReview().nodePermissions(),admission.documentReview().nodePermissions().stream().map(RunTokenAuthoritySchema.Permission::action).distinct().toList());
        if(admission.planDraft()!=null)return RuntimeNodePermissions.intersect(schema.permissions(admission.principal()),admission.planDraft().nodePermissions(),admission.planDraft().nodePermissions().stream().map(RunTokenAuthoritySchema.Permission::action).distinct().toList());
        if(admission.taskNode()==null)return schema.permissions(admission.principal());
        var node=admission.taskNode().nodePermissions();
        return RuntimeNodePermissions.intersect(schema.permissions(admission.principal()),node,
            node.stream().map(RunTokenAuthoritySchema.Permission::action).distinct().toList());
    }
    private List<RunTokenAuthoritySchema.Permission> effectivePermissions(Checked admission,List<String> actions) throws Exception {
        return RuntimeNodePermissions.intersect(schema.permissions(admission.principal()),nodePermissions(admission),actions);
    }
    private <T> T transaction(HttpServletRequest request,String tenant,String actor,String run,String step,BigInteger expectedAuth,boolean requireAvailable,CheckedOperation<T> operation) throws Exception {
        if(documentReview!=null && documentReview.supports(tenant,actor,run))
            return documentReview.execute(request,tenant,actor,run,step,expectedAuth,current->operation.apply(current,null));
        if(planDraft!=null && planDraft.supports(tenant,actor,run))
            return planDraft.execute(request,tenant,actor,run,step,expectedAuth,current->operation.apply(current,null));
        if(taskNodes!=null && taskNodes.supports(tenant,actor,run))
            return taskNodes.execute(request,tenant,actor,run,step,expectedAuth,requireAvailable,operation::apply);
        WireValues.uuid(tenant); WireValues.uuid(actor);
        var epochs=store.epochs(tenant);
        if(expectedAuth!=null && !expectedAuth.equals(epochs.authEpoch())) denied(ErrorCode.SCOPE_STALE);
        try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ZERO,epochs.authEpoch(),epochs.governanceEpoch()))) {
            return transactions.execute(current -> ThrowingSupplier.of(() -> operation.apply(current,null)).get());
        }
    }

    boolean documentReviewCompleted(Admission admission)throws Exception{return documentReview!=null&&documentReview.completedIfActive(admission);}
    DocumentReviewRuntimeAuthority.Verified documentReviewRuntime(ActionRequest action)throws Exception{if(documentReview==null)denied(ErrorCode.DEPENDENCY_UNAVAILABLE);return documentReview.leased(action);}
    void requireDocumentReviewAdmission(Admission admission)throws Exception{if(documentReview==null)denied(ErrorCode.DEPENDENCY_UNAVAILABLE);documentReview.requireAdmission(admission);}
    TaskPlanDraftRuntimeAuthority.Verified planDraftRuntime(ActionRequest action)throws Exception {if(planDraft==null)denied(ErrorCode.DEPENDENCY_UNAVAILABLE);return planDraft.leased(action);}
    void requirePlanDraftAdmission(Admission admission)throws Exception {if(planDraft==null)denied(ErrorCode.DEPENDENCY_UNAVAILABLE);planDraft.requireAdmission(admission);}
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
    private record Checked(RunTokenAuthorityStore.Run run,RunTokenAuthorityStore.Step step,JsonNode principal,String mode,LocalDateTime now,FormalModelVerificationBootstrap.Verified bootstrap,FormalTaskNodeAuthority.Verified taskNode,TaskPlanDraftRuntimeAuthority.Bootstrap planDraft,DocumentReviewRuntimeAuthority.Bootstrap documentReview) { }
    @FunctionalInterface private interface CheckedOperation<T> { T apply(TenantWatermarks current,FormalTaskNodeAuthority.Verified taskNode) throws Exception; }
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
        private final List<String> delegatedActions;
        private final List<RunTokenAuthoritySchema.Permission> delegatedPermissions;
        private final String serviceUri;
        private final FormalTaskNodeAuthority.Verified taskNode;
        private Admission(String tenant,String project,String actor,String principal,String run,String step,String action,BigInteger generation,
                LocalDateTime now,BigInteger auth,BigInteger governance,List<String> delegatedActions,String serviceUri,List<RunTokenAuthoritySchema.Permission> delegatedPermissions,FormalTaskNodeAuthority.Verified taskNode) {
            this.taskNode=taskNode;tenantId=tenant; projectId=project; initiatorId=actor; principalId=principal; runId=run; stepId=step; actionId=action;
            leaseGeneration=generation; admittedAt=now; authEpoch=auth; governanceEpoch=governance;this.delegatedActions=List.copyOf(delegatedActions);this.serviceUri=Objects.requireNonNull(serviceUri);this.delegatedPermissions=List.copyOf(delegatedPermissions);
        }
        TaskStrictFieldInputAuthority.Verified currentStrictFieldInput()throws Exception {
            if(taskNode==null)denied(ErrorCode.ACCESS_DENIED);taskNode.requireUnchanged();
            var input=taskNode.currentStrictFieldInput();input.requireUnchanged();
            if(!tenantId.equals(input.input().tenantId())||!projectId.equals(input.input().projectId())||!runId.equals(input.input().runId())||!stepId.equals(input.input().stepId()))denied(ErrorCode.ACCESS_DENIED);
            taskNode.requireUnchanged();return input;
        }
        TaskSectionWriterInputAuthority.Verified currentWriterInput()throws Exception {
            if(taskNode==null)denied(ErrorCode.ACCESS_DENIED);taskNode.requireUnchanged();
            var input=taskNode.currentWriterInput();input.requireUnchanged();
            if(!tenantId.equals(input.input().tenantId())||!projectId.equals(input.input().projectId())||!runId.equals(input.input().runId())||!stepId.equals(input.input().stepId()))denied(ErrorCode.ACCESS_DENIED);
            taskNode.requireUnchanged();return input;
        }
        TaskSourceVerifierInputAuthority.Verified currentVerifierInput()throws Exception {
            if(taskNode==null)denied(ErrorCode.ACCESS_DENIED);taskNode.requireUnchanged();
            var input=taskNode.currentVerifierInput();input.requireUnchanged();
            if(!tenantId.equals(input.input().tenantId())||!projectId.equals(input.input().projectId())||!runId.equals(input.input().runId())||!stepId.equals(input.input().stepId()))denied(ErrorCode.ACCESS_DENIED);
            taskNode.requireUnchanged();return input;
        }
        public List<String> delegatedActions(){return delegatedActions;}
        public List<RunTokenAuthoritySchema.Permission> delegatedPermissions(){return delegatedPermissions;}
        public String serviceUri(){return serviceUri;}
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
