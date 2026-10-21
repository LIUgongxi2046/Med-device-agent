package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Exact, read-only PLAN_DRAFT bootstrap access. No browser identity, lease, token,
 * model permission or authoring capability is created by this adapter. */
public final class TaskPlanDraftControlAccess {
    private final JdbcTemplate db;
    private final TenantTransaction transactions;
    private final ServiceCertificateIdentity services;
    private final PermissionMapper permissions;
    private final PermissionAuthority grants;
    private final VersionResourceMapper owners;
    private final ArtifactRegistryMapper registry;
    private final TaskPlanDraftJobs jobs;
    private final TaskPlanDraftSchema schema;
    private final RunTokenAuthoritySchema runtime;
    private final Duration maximumDuration;
    private final int maximumDependencies;

    public TaskPlanDraftControlAccess(DataSource data, TenantTransaction transactions,
            ServiceCertificateIdentity services, PermissionMapper permissions, PermissionAuthority grants,
            VersionResourceMapper owners, ArtifactRegistryMapper registry, TaskPlanDraftJobs jobs,
            TaskPlanDraftSchema schema, RunTokenAuthoritySchema runtime, Duration maximumDuration,
            int maximumDependencies) {
        this.db=new JdbcTemplate(data); db.setQueryTimeout(5); this.transactions=transactions;
        this.services=services; this.permissions=permissions; this.grants=grants; this.owners=owners;
        this.registry=registry; this.jobs=jobs; this.schema=schema; this.runtime=runtime;
        if(maximumDuration==null || maximumDuration.isZero() || maximumDuration.isNegative()
                || maximumDuration.compareTo(Duration.ofMinutes(10))>0 || maximumDependencies<1 || maximumDependencies>10000)
            throw new IllegalArgumentException("EXPLICIT_PLAN_DRAFT_CONTROL_BOUNDS_REQUIRED");
        this.maximumDuration=maximumDuration; this.maximumDependencies=maximumDependencies;
    }

    public <T>T withReads(HttpServletRequest http, String tenant, String run, String command,
            Function<Reads,T> operation) throws Exception {
        WireValues.uuid(tenant); WireValues.uuid(run); WireValues.uuid(command); Objects.requireNonNull(operation);
        if(TransactionSynchronizationManager.isActualTransactionActive()) deny();
        var certificate=services.require(http);
        var actor=one(db.query("SELECT created_by FROM task_plan_draft_job WHERE tenant_id=? AND run_id=? AND operation_id=? LIMIT 2",
                (r,n)->r.getString(1),tenant,run,command));
        var reads=new Reads(http,tenant,run,command,actor,certificate);
        try { reads.execute(a->null); return operation.apply(reads); } finally { reads.active=false; }
    }

    public final class Reads {
        private final HttpServletRequest http;
        private final String tenant,run,command,actor;
        private final ServiceCertificateIdentity.Verified certificate;
        private final Thread thread=Thread.currentThread();
        private final long deadline=System.nanoTime()+maximumDuration.toNanos();
        private boolean active=true;
        private EnterpriseAccess.Admission current;
        private TaskPlanDraftJobs.Job original;
        private Reads(HttpServletRequest http,String tenant,String run,String command,String actor,
                ServiceCertificateIdentity.Verified certificate) {
            this.http=http;this.tenant=tenant;this.run=run;this.command=command;this.actor=actor;this.certificate=certificate;
        }
        public void requireActive(){if(!active || thread!=Thread.currentThread() || System.nanoTime()>=deadline)deny();}
        public String actorId(){requireActive();return actor;}
        public String tenantId(){requireActive();return tenant;}
        public String contextRevision(){requireActive();return "0";}
        public String runId(){requireActive();return run;}
        TaskPlanDraftJobs.Job job(){requireActive();if(original==null)deny();return original;}
        public JsonNode admission(){requireActive();if(original==null)deny();return ThrowingSupplier.of(()->schema.read(original.admissionJson())).get();}
        public void requirePublication(String release,String purpose){requireActive();if(!"RUN_CREATION".equals(purpose)||!release.equals(admission().required("agent_release_id").textValue()))deny();}
        public void requireVersion(String id,String hash){requireActive();WireValues.uuid(id);WireValues.hash(hash);int found=0;
            for(var pin:admission().required("dependency_versions"))if(id.equals(pin.required("version_id").textValue())){if(!hash.equals(pin.required("content_sha256").textValue()))deny();found++;}
            if(found!=1)deny();
        }
        public <T>T execute(Function<EnterpriseAccess.Admission,T> operation) {
            requireActive();Objects.requireNonNull(operation);
            if(TransactionSynchronizationManager.isActualTransactionActive()) {
                if(current==null || current.planDraftProof().reads!=this)deny();
                try{return operation.apply(current);}finally{current.planDraftProof().requireActive();}
            }
            var epochs=one(db.query("SELECT auth_epoch,governance_epoch FROM tenant WHERE tenant_id=? LIMIT 2",
                    (r,n)->new TenantWatermarks(r.getBigDecimal(1).toBigIntegerExact(),r.getBigDecimal(2).toBigIntegerExact()),tenant));
            try(var ignored=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ZERO,epochs.authEpoch(),epochs.governanceEpoch()))) {
                return transactions.execute(watermarks->ThrowingSupplier.of(()->{
                    var state=state(); if(original==null)original=state.job();else if(!original.equals(state.job()))deny();
                    var proof=proof(state); current=new EnterpriseAccess.Admission(proof,
                            new BusinessSessionProtocol.Context(tenant,"0",watermarks.authEpoch().toString(),watermarks.governanceEpoch().toString()),watermarks);
                    try { var result=operation.apply(current);proof.requireActive();var after=state();if(!state.equals(after))proof.requireTransition(state,after);return result; }
                    finally { current=null;proof.active=false; }
                }).get());
            }
        }
        private TaskPlanDraftSchema ownerSchema(){return schema;}
        private boolean runtimePermission(JsonNode principal,PermissionProtocol.Scope scope,String action,String purpose)throws java.io.IOException {
            return runtime.permissions(principal).contains(new RunTokenAuthoritySchema.Permission(scope,action,purpose));
        }
        private PermissionProtocol.GrantRow currentGrant(PermissionProtocol.Scope scope,String action,String purpose,String mode)throws java.io.IOException {
            requireActive();var owner=one(permissions.resourceOwner(scope.resource_kind(),scope.resource_id()));
            if(!scope.resource_id().equals(owner.id())||!Objects.equals(scope.project_id(),owner.projectId()))deny();
            return grants.requireResource(scope,actor,mode,action,purpose,permissions.resourceGrants(actor,action),permissions.databaseTime());
        }
        private State state() throws Exception {
            requireActive();var fresh=services.require(http);
            if(!certificate.serviceUri().equals(fresh.serviceUri())||!certificate.certificateSha256().equals(fresh.certificateSha256())
                    ||!certificate.trustRevision().equals(fresh.trustRevision()))deny();
            var job=jobs.byRun(tenant,run);
            if(!actor.equals(job.actor())||!command.equals(job.operation()))deny();
            var admitted=schema.read(job.admissionJson());
            if(!"SYSTEM_JOB".equals(admitted.required("execution_kind").textValue())||!"PLAN_DRAFT".equals(admitted.required("job_type").textValue())
                    ||!"N01".equals(admitted.required("node_kind").textValue()))deny();
            var row=one(db.query("""
                SELECT j.status,p.status,p.capability_manifest,e.scope_manifest,j.revision FROM system_job_run j
                JOIN run_principal p ON p.tenant_id=j.tenant_id AND p.run_id=j.run_id AND p.principal_id=?
                JOIN execution_registry e ON e.tenant_id=j.tenant_id AND e.run_id=j.run_id
                WHERE j.tenant_id=? AND j.run_id=? AND j.project_id=? AND j.initiator_id=?
                  AND j.execution_kind='SYSTEM_JOB' AND j.job_type='PLAN_DRAFT' AND e.execution_kind='SYSTEM_JOB'
                LIMIT 2 FOR UPDATE
                """,(r,n)->List.of(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)),job.principal(),tenant,run,job.project(),actor));
            if(!Set.of("QUEUED","RUNNING","WAITING_INPUT").contains(row.get(0))||!"ACTIVE".equals(row.get(1)))deny();
            var scope=runtime.scopeManifest(row.get(3),tenant,job.project(),actor,run,"SYSTEM_JOB");
            var principal=runtime.principalManifest(row.get(2),tenant,job.project(),actor,run,job.principal(),scope);
            if(!scope.equals(admitted.required("scope_manifest"))||!principal.equals(admitted.required("principal_manifest")))deny();
            int serviceCount=0;for(var allowed:principal.required("services"))if(certificate.serviceUri().equals(allowed.required("service_uri").textValue()))serviceCount++;
            if(serviceCount!=1)deny();
            var binding=one(permissions.currentBinding(actor));if(!actor.equals(binding.userId())||!"ACTIVE".equals(binding.status()))deny();
            if("local".equals(binding.mode())){if(!actor.equals(binding.localUserId()))deny();one(db.query("SELECT user_id FROM platform_local_account WHERE user_id=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getString(1),actor));}
            else if("oidc".equals(binding.mode())){if(binding.localUserId()!=null)deny();one(db.query("SELECT user_id FROM platform_identity WHERE identity_id=? AND user_id=? AND issuer=? AND subject=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getString(1),binding.identityId(),actor,binding.issuer(),binding.subject()));}
            else deny();
            one(db.query("SELECT user_id FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE' AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6)) FOR UPDATE",(r,n)->r.getString(1),tenant,actor));
            if(!"ACTIVE".equals(one(permissions.projectStatus(job.project()))))deny();
            grants.requireResource(new PermissionProtocol.Scope(tenant,job.project(),"TASK",job.task()),actor,binding.mode(),"task.run","TASK_EXECUTION",permissions.resourceGrants(actor,"task.run"),permissions.databaseTime());
            grants.requireResource(new PermissionProtocol.Scope(tenant,job.project(),"TASK",job.task()),actor,binding.mode(),"plan.write","PLAN_MAINTENANCE",permissions.resourceGrants(actor,"plan.write"),permissions.databaseTime());
            // Current grants are checked independently; this does not give the metadata Proof write authority.
            for(var permission:runtime.permissions(principal)){
                var owner=one(permissions.resourceOwner(permission.scope().resource_kind(),permission.scope().resource_id()));
                if(!permission.scope().resource_id().equals(owner.id())||!Objects.equals(permission.scope().project_id(),owner.projectId()))deny();
                grants.requireResource(permission.scope(),actor,binding.mode(),permission.action(),permission.purpose(),permissions.resourceGrants(actor,permission.action()),permissions.databaseTime());
            }
            var revision=new BigInteger(row.get(4));if(revision.signum()<=0)deny();
            return new State(job,new AuthenticatedIdentityBinding.View(binding.mode(),binding.userId(),binding.issuer(),binding.subject()),row.get(0),revision);
        }
        private Proof proof(State state) throws Exception {
            var pins=admission().required("dependency_versions");if(!pins.isArray()||pins.isEmpty()||pins.size()>maximumDependencies)deny();
            var ids=new ArrayList<String>();for(var pin:pins){var id=pin.required("version_id").textValue();if(ids.contains(id))deny();ids.add(id);}
            var domain=owners.ownersAcrossDomains(ids,Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());
            var grouped=domain.stream().collect(java.util.stream.Collectors.groupingBy(r->r.owner().versionId()));
            var scopes=new HashSet<PermissionProtocol.Scope>();
            var governance=new HashSet<RunTokenAuthoritySchema.Permission>();
            for(var pin:pins){var id=pin.required("version_id").textValue();var version=one(registry.version(id));
                if(!id.equals(version.id())||!pin.required("entity_kind").textValue().equals(version.entityKind())
                        ||!pin.required("content_sha256").textValue().equals(version.contentSha256())
                        ||!Objects.equals(pin.required("project_id").isNull()?null:pin.required("project_id").textValue(),version.projectId()))deny();
                if("ARTIFACT".equals(version.entityKind())){scopes.add(new PermissionProtocol.Scope(tenant,version.projectId(),"ARTIFACT",id));continue;}
                if("RUNTIME_RETENTION_POLICY".equals(version.entityKind())){
                    var originalPolicy=one(db.query("""
                        SELECT project_id,content_sha256 FROM runtime_retention_policy
                        WHERE tenant_id=? AND policy_revision=? LIMIT 2 FOR UPDATE
                        """,(r,n)->new RetentionPolicyOwner(r.getString(1),r.getString(2)),tenant,id));
                    // Bootstrap only the exact read used by RuntimeRetentionAuthority. Its
                    // current lifecycle, approval and content checks still run in requireCurrent.
                    governance.add(retentionPermission(tenant,state.job().project(),admission().required("retention"),version,originalPolicy));
                    continue;
                }
                if(Set.of("AGENT_PUBLICATION_RECORD","EXPERT_QUALIFICATION","EXPERT_EVIDENCE_RECORD").contains(version.entityKind())){
                    String table="AGENT_PUBLICATION_RECORD".equals(version.entityKind())?"agent_publication_record":"expert_qualification_record";
                    var originalRecord=one(db.query("SELECT project_id,kind,content_sha256 FROM "+table+" WHERE tenant_id=? AND record_id=? LIMIT 2 FOR UPDATE",
                            (r,n)->Arrays.asList(r.getString(1),r.getString(2),r.getString(3)),tenant,id));
                    var kinds=switch(version.entityKind()){case "AGENT_PUBLICATION_RECORD"->Set.of("ACCEPTANCE","POLICY","POLICY_DRAFT");case "EXPERT_QUALIFICATION"->Set.of("AUTHOR");default->Set.of("APPROVE","SCORE_BIND");};
                    if(!Objects.equals(version.projectId(),originalRecord.get(0))||!kinds.contains(originalRecord.get(1))||!version.contentSha256().equals(originalRecord.get(2)))deny();
                    String purpose=Set.of("POLICY","POLICY_DRAFT").contains(originalRecord.get(1))?"EVALUATION_RESULTS":"AGENT_PUBLICATION";
                    governance.add(new RunTokenAuthoritySchema.Permission(RuntimeRetentionAuthority.scope(tenant,originalRecord.get(0)),"configuration.read",purpose));continue;
                }
                var owner=one(grouped.getOrDefault(id,List.of())).owner();
                if(!tenant.equals(owner.tenantId())||owner.conversationId()!=null||owner.resourceProjectId()!=null&&!state.job().project().equals(owner.resourceProjectId())
                        ||owner.contentSha256()!=null&&!version.contentSha256().equals(owner.contentSha256()))deny();
                scopes.add(new PermissionProtocol.Scope(tenant,owner.resourceProjectId(),owner.resourceKind(),owner.resourceId()));
            }
            scopes.add(new PermissionProtocol.Scope(tenant,state.job().project(),"TASK",state.job().task()));
            scopes.add(new PermissionProtocol.Scope(tenant,state.job().project(),"PROJECT",state.job().project()));
            return new Proof(this,state,Set.copyOf(scopes),Set.copyOf(governance));
        }
    }
    private record State(TaskPlanDraftJobs.Job job,AuthenticatedIdentityBinding.View binding,String status,BigInteger revision){}
    static final class Proof {
        private final Reads reads;private final AuthenticatedIdentityBinding.View binding;private final Set<PermissionProtocol.Scope> scopes;private final Set<RunTokenAuthoritySchema.Permission> governance;private final State before;private final TenantScope transactionScope=TenantScope.requireCurrent();
        private final Map<Object,Object> transactionResources=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private TaskPlanDraftLaunchLedger.LaunchTransition transition;
        private TaskPlanDraftCompletionLedger.CompletionTransition completion;private boolean active=true;
        private Proof(Reads reads,State before,Set<PermissionProtocol.Scope> scopes,Set<RunTokenAuthoritySchema.Permission> governance){this.reads=reads;this.before=before;this.binding=before.binding();this.scopes=scopes;this.governance=governance;}
        void requireActive(){
            if(!active||!TransactionSynchronizationManager.isActualTransactionActive()||transactionScope!=TenantScope.requireCurrent()||transactionResources.isEmpty())deny();
            var currentResources=TransactionSynchronizationManager.getResourceMap();
            if(currentResources.size()!=transactionResources.size()||transactionResources.entrySet().stream().anyMatch(e->currentResources.get(e.getKey())!=e.getValue()))deny();
            reads.requireActive();
        }
        void require(PermissionProtocol.Scope scope,String action,String purpose){requireActive();if(action==null||!action.endsWith(".read")||purpose==null||"SIMULATION_EVALUATION".equals(purpose)||!(scopes.contains(scope)||governance.contains(new RunTokenAuthoritySchema.Permission(scope,action,purpose))))deny();}
        void requireVersion(String id,String hash){requireActive();reads.requireVersion(id,hash);}
        PermissionProtocol.GrantRow requireOriginalAction(PermissionProtocol.Scope scope,String action,String purpose)throws java.io.IOException {
            requireActive();if(!reads.tenant.equals(scope.tenant_id()))deny();
            var job=reads.job();var principal=reads.admission().required("principal_manifest");
            boolean allowed=reads.runtimePermission(principal,scope,action,purpose);
            var task=new PermissionProtocol.Scope(reads.tenant,job.project(),"TASK",job.task());
            if(task.equals(scope)){
                var original=reads.ownerSchema().read(job.authorityJson());
                if("task.run".equals(action)&&"TASK_EXECUTION".equals(purpose)&&original.has("run_grant"))allowed=true;
                if("plan.write".equals(action)&&"PLAN_MAINTENANCE".equals(purpose)&&original.has("plan_grant"))allowed=true;
                if("task.run".equals(action)&&"RUN_CREATION".equals(purpose)&&original.path("release_scope").has("grant"))allowed=true;
            }
            if(!allowed)deny();return reads.currentGrant(scope,action,purpose,binding.mode());
        }
        void requireRelease(String release){requireActive();reads.requirePublication(release,"RUN_CREATION");}

        void requireTask(String task){requireActive();if(!task.equals(reads.original.task()))deny();}
        TaskPlanDraftJobs.Job job(){requireActive();return reads.job();}
        void acceptedStart(TaskPlanDraftLaunchLedger.LaunchTransition value)throws Exception {
            requireActive();if(value==null||transition!=null||completion!=null||reads.current==null||reads.current.planDraftProof()!=this
                    ||!"QUEUED".equals(before.status())||!before.revision().equals(value.beforeRevision())
                    ||!before.revision().add(BigInteger.ONE).equals(value.afterRevision()))deny();
            value.requireFor(reads.tenant,reads.actor,reads.run,reads.original.principal(),before.revision(),value.afterRevision());transition=value;
        }
        void completedPlanDraft(TaskPlanDraftCompletionLedger.CompletionTransition value)throws Exception {
            requireActive();if(value==null||transition!=null||completion!=null||reads.current==null||reads.current.planDraftProof()!=this
                    ||!"RUNNING".equals(before.status())||!before.revision().equals(value.beforeRevision())
                    ||!before.revision().add(BigInteger.ONE).equals(value.afterRevision()))deny();
            value.requireFor(reads.tenant,reads.actor,reads.run,reads.original.principal(),before.revision(),value.afterRevision());completion=value;
        }
        private void requireTransition(State original,State after)throws Exception {
            requireActive();if(!before.equals(original)||!original.job().equals(after.job())||!original.binding().equals(after.binding())
                    ||!original.revision().add(BigInteger.ONE).equals(after.revision()))deny();
            if(transition!=null&&completion==null){
                if(!"QUEUED".equals(original.status())||!"RUNNING".equals(after.status()))deny();
                transition.requireFor(reads.tenant,reads.actor,reads.run,reads.original.principal(),original.revision(),after.revision());
            }else if(completion!=null&&transition==null){
                if(!"RUNNING".equals(original.status())||!"WAITING_INPUT".equals(after.status()))deny();
                completion.requireFor(reads.tenant,reads.actor,reads.run,reads.original.principal(),original.revision(),after.revision());
            }else deny();
        }

        void requireFixedTask(TaskProtocol.Head head){requireActive();var a=reads.admission();
            if(!head.tenantId().equals(reads.tenant)||!head.projectId().equals(reads.original.project())||!head.taskId().equals(reads.original.task())
                    ||!head.taskRevision().equals(reads.original.taskRevision())||!head.snapshotId().equals(a.required("scope_snapshot_id").textValue())
                    ||!head.templateVersion().equals(a.required("template").required("version_id").textValue())
                    ||!head.profileRevision().equals(a.required("profile").required("version_id").textValue())
                    ||!head.agentReleaseId().equals(a.required("agent_release_id").textValue()))deny();
        }
        void requireSourceScope(String project,String task,String revision,String snapshot,String profile){requireActive();var a=reads.admission();
            if(!project.equals(reads.original.project())||!task.equals(reads.original.task())||!revision.equals(reads.original.taskRevision())
                    ||!snapshot.equals(a.required("scope_snapshot_id").textValue())||!profile.equals(a.required("profile").required("version_id").textValue()))deny();
        }
        String actorId(){requireActive();return reads.actorId();}
        AuthenticatedIdentityBinding.View bindingView(){requireActive();return binding;}
        Object identity(){requireActive();return new Identity("PLAN_DRAFT_CONTROL_READ",reads.tenant,reads.run,reads.command,reads.actor,
                reads.certificate.serviceUri(),reads.certificate.trustRevision(),reads.certificate.certificateSha256(),reads.original.inputHash(),reads.original.requestHash());}
    }
    record Identity(String kind,String tenantId,String runId,String commandId,String actorId,String serviceUri,
            String trustRevision,String certificateSha256,String inputSha256,String requestSha256){}
    record RetentionPolicyOwner(String projectId,String contentSha256){}
    static RunTokenAuthoritySchema.Permission retentionPermission(String tenant,String project,JsonNode pin,
            ArtifactRegistryProtocol.VersionRow version,RetentionPolicyOwner original) {
        if(!"RUNTIME_RETENTION_POLICY".equals(version.entityKind())
                ||!version.id().equals(pin.required("policy_revision").textValue())
                ||!version.contentSha256().equals(pin.required("content_sha256").textValue())
                ||!Objects.equals(version.projectId(),original.projectId())
                ||original.projectId()!=null&&!project.equals(original.projectId())
                ||!version.contentSha256().equals(original.contentSha256()))deny();
        return new RunTokenAuthoritySchema.Permission(RuntimeRetentionAuthority.scope(tenant,original.projectId()),
                "retention.read",TaskPlanDraftAdmission.PURPOSE);
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)deny();return rows.get(0);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
