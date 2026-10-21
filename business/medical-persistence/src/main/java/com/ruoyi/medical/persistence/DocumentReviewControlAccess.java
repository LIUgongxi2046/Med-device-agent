package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** N07 bounded mTLS reads over an original CHECK_DOCUMENT job. Not a human, TASK or PLAN_DRAFT identity. */
public final class DocumentReviewControlAccess {
    private final JdbcTemplate db;private final TenantTransaction transactions;private final ServiceCertificateIdentity services;
    private final VersionResourceMapper owners;private final ArtifactRegistryMapper registry;private final PermissionMapper permissions;private final PermissionAuthority grants;private final DocumentReviewExecutionService jobs;
    private final DocumentReviewDeployment deployment;private final RunTokenAuthoritySchema runtime;private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();private final Duration maximum;
    public DocumentReviewControlAccess(DataSource data,TenantTransaction transactions,ServiceCertificateIdentity services,
            PermissionMapper permissions,PermissionAuthority grants,DocumentReviewExecutionService jobs,RunTokenAuthoritySchema runtime,Duration maximum,DocumentReviewDeployment deployment,VersionResourceMapper owners,ArtifactRegistryMapper registry){
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.transactions=transactions;this.services=services;this.permissions=permissions;this.grants=grants;this.jobs=jobs;this.runtime=runtime;this.deployment=deployment;this.owners=owners;this.registry=registry;
        if(maximum==null||maximum.isNegative()||maximum.isZero()||maximum.compareTo(Duration.ofMinutes(10))>0)throw new IllegalArgumentException("EXPLICIT_DOCUMENT_REVIEW_READ_BOUND_REQUIRED");this.maximum=maximum;
    }
    public <T>T withReads(HttpServletRequest http,String tenant,String run,String command,Function<Reads,T> operation)throws Exception{
        WireValues.uuid(tenant);WireValues.uuid(run);WireValues.uuid(command);if(TransactionSynchronizationManager.isActualTransactionActive())deny();
        var service=services.require(http);var actor=one(db.query("SELECT created_by FROM document_review_execution WHERE tenant_id=? AND run_id=? AND operation_id=? LIMIT 2",(r,n)->r.getString(1),tenant,run,command));var reads=new Reads(http,tenant,run,command,actor,service,false);
        try{reads.execute(a->null);return operation.apply(reads);}finally{reads.active=false;}
    }
    <T>T withSupervisorReads(HttpServletRequest http,String tenant,String run,String command,Function<Reads,T> operation)throws Exception{
        WireValues.uuid(tenant);WireValues.uuid(run);WireValues.uuid(command);if(TransactionSynchronizationManager.isActualTransactionActive())deny();var service=services.require(http);supervisor(service);
        var actor=one(db.query("SELECT created_by FROM document_review_execution WHERE tenant_id=? AND run_id=? AND operation_id=? LIMIT 2",(r,n)->r.getString(1),tenant,run,command));var reads=new Reads(http,tenant,run,command,actor,service,true);
        try{reads.execute(a->null);return operation.apply(reads);}finally{reads.active=false;}
    }
    private void supervisor(ServiceCertificateIdentity.Verified peer){var d=deployment.value();if(!d.required("supervisor_service_uri").asText().equals(peer.serviceUri())||!d.required("supervisor_certificate_sha256").asText().equals(peer.certificateSha256())||!d.required("supervisor_trust_revision").asText().equals(peer.trustRevision())||deployment.workerService().equals(peer.serviceUri()))deny();}
    public final class Reads {
        private final boolean supervisorRead;private final HttpServletRequest http;private DocumentReviewExecutionService.Job original;private final String tenant,run,command,actor;private final ServiceCertificateIdentity.Verified certificate;
        private final Thread thread=Thread.currentThread();private final long deadline=System.nanoTime()+maximum.toNanos();private boolean active=true;private EnterpriseAccess.Admission current;
        private Reads(HttpServletRequest http,String tenant,String run,String command,String actor,ServiceCertificateIdentity.Verified certificate,boolean supervisorRead){this.supervisorRead=supervisorRead;this.http=http;this.tenant=tenant;this.run=run;this.command=command;this.actor=actor;this.certificate=certificate;}
        public void requireActive(){if(!active||thread!=Thread.currentThread()||System.nanoTime()>=deadline)deny();}
        public String actorId(){requireActive();return actor;}public String tenantId(){requireActive();return tenant;}public String runId(){requireActive();return run;}public String contextRevision(){requireActive();return "0";}
        public DocumentReviewExecutionService.Job job(){requireActive();if(original==null)deny();return original;}
        public JsonNode admission(){requireActive();return ThrowingSupplier.of(()->json.read(job().admissionJson())).get();}
        EvidenceReadAccess evidenceAccess(){requireActive();return EvidenceReadAccess.documentReview(this);}
        public void requireVersion(String id,String hash){requireActive();WireValues.uuid(id);WireValues.hash(hash);int found=0;for(var pin:admission().required("dependency_versions"))if(id.equals(pin.required("version_id").asText())){if(!hash.equals(pin.required("content_sha256").asText()))deny();found++;}if(found!=1)deny();}
        public void requirePublication(String release,String purpose){requireActive();if(!"RUN_CREATION".equals(purpose)||!release.equals(admission().required("agent_release_id").asText()))deny();}
        public <T>T execute(Function<EnterpriseAccess.Admission,T> operation){
            requireActive();if(TransactionSynchronizationManager.isActualTransactionActive()){if(current==null||current.documentReviewProof().reads!=this)deny();try{return operation.apply(current);}finally{current.documentReviewProof().requireActive();}}
            var epochs=one(db.query("SELECT auth_epoch,governance_epoch FROM tenant WHERE tenant_id=? LIMIT 2",(r,n)->new TenantWatermarks(r.getBigDecimal(1).toBigIntegerExact(),r.getBigDecimal(2).toBigIntegerExact()),tenantId()));
            try(var scope=TenantScope.open(new TenantScope.Identity(tenantId(),actorId(),BigInteger.ZERO,epochs.authEpoch(),epochs.governanceEpoch()))){return transactions.execute(w->ThrowingSupplier.of(()->{
                var before=state();var proof=new Proof(this,before);current=new EnterpriseAccess.Admission(proof,new BusinessSessionProtocol.Context(tenantId(),"0",w.authEpoch().toString(),w.governanceEpoch().toString()),w);
                try{var result=operation.apply(current);proof.requireActive();var after=state();if(!before.equals(after))deny();return result;}finally{current=null;proof.active=false;}
            }).get());}
        }
        private boolean pinnedRead(PermissionProtocol.Scope scope,String action,String purpose)throws Exception {
            requireActive();if(!tenantId().equals(scope.tenant_id())||action==null||!action.endsWith(".read")||purpose==null||"SIMULATION_EVALUATION".equals(purpose))return false;
            var pins=admission().required("dependency_versions");if(!pins.isArray()||pins.isEmpty()||pins.size()>10000)deny();var ids=new ArrayList<String>();for(var pin:pins)ids.add(pin.required("version_id").asText());
            var domains=owners.ownersAcrossDomains(ids,Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());
            for(var pin:pins){String id=pin.required("version_id").asText(),hash=pin.required("content_sha256").asText();var version=one(registry.version(id));if(!id.equals(version.id())||!hash.equals(version.contentSha256()))deny();
                if("ARTIFACT".equals(version.entityKind())&&scope.equals(new PermissionProtocol.Scope(tenantId(),version.projectId(),"ARTIFACT",id)))return true;
                if("RUNTIME_RETENTION_POLICY".equals(version.entityKind())&&scope.equals(RuntimeRetentionAuthority.scope(tenantId(),version.projectId()))&&"retention.read".equals(action)&&"RUN_CREATION".equals(purpose))return true;
                if(Set.of("AGENT_PUBLICATION_RECORD","EXPERT_QUALIFICATION","EXPERT_EVIDENCE_RECORD").contains(version.entityKind())&&scope.equals(RuntimeRetentionAuthority.scope(tenantId(),version.projectId()))&&"configuration.read".equals(action)&&Set.of("AGENT_PUBLICATION","EVALUATION_RESULTS").contains(purpose))return true;
                for(var domain:domains){var owner=domain.owner();if(id.equals(owner.versionId())&&tenantId().equals(owner.tenantId())&&owner.conversationId()==null&&(owner.contentSha256()==null||hash.equals(owner.contentSha256()))&&scope.equals(new PermissionProtocol.Scope(tenantId(),owner.resourceProjectId(),owner.resourceKind(),owner.resourceId())))return true;}
            }return false;
        }
        private RunTokenAuthoritySchema ownerRuntime(){return runtime;}
        private PermissionProtocol.GrantRow originalGrant(PermissionProtocol.Scope scope,String action,String purpose,String mode)throws java.io.IOException {
            var owner=one(permissions.resourceOwner(scope.resource_kind(),scope.resource_id()));if(!scope.resource_id().equals(owner.id())||!Objects.equals(scope.project_id(),owner.projectId()))deny();return grants.requireResource(scope,actorId(),mode,action,purpose,permissions.resourceGrants(actorId(),action),permissions.databaseTime());
        }
        private State state()throws Exception{
            requireActive();var fresh=services.require(http);if(!certificate.equals(fresh)){
                if(!certificate.serviceUri().equals(fresh.serviceUri())||!certificate.certificateSha256().equals(fresh.certificateSha256())||!certificate.trustRevision().equals(fresh.trustRevision()))deny();}
            var found=jobs.byRun(tenantId(),runId());if(!command.equals(found.operation())||!actor.equals(found.actor()))deny();if(original==null)original=found;else if(!original.equals(found))deny();var admitted=admission();if(!"SYSTEM_JOB".equals(admitted.required("execution_kind").asText())||!"CHECK_DOCUMENT".equals(admitted.required("job_type").asText())||!"N07".equals(admitted.required("node_kind").asText()))deny();
            var rows=one(db.query("SELECT j.status,p.status,p.capability_manifest,e.scope_manifest FROM system_job_run j JOIN run_principal p ON p.tenant_id=j.tenant_id AND p.run_id=j.run_id AND p.principal_id=? JOIN execution_registry e ON e.tenant_id=j.tenant_id AND e.run_id=j.run_id WHERE j.tenant_id=? AND j.run_id=? AND j.project_id=? AND j.initiator_id=? AND j.execution_kind='SYSTEM_JOB' AND j.job_type='CHECK_DOCUMENT' AND e.execution_kind='SYSTEM_JOB' LIMIT 2 FOR UPDATE",(r,n)->List.of(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),original.principal(),tenantId(),runId(),original.project(),actorId()));
            if(!Set.of("QUEUED","RUNNING","WAITING_INPUT","SUCCEEDED","FAILED","CANCELLED").contains(rows.get(0))||!"ACTIVE".equals(rows.get(1)))deny();
            var scope=runtime.scopeManifest(rows.get(3),tenantId(),original.project(),actorId(),runId(),"SYSTEM_JOB");var principal=runtime.principalManifest(rows.get(2),tenantId(),original.project(),actorId(),runId(),original.principal(),scope);
            if(!scope.equals(admitted.required("scope_manifest"))||!principal.equals(admitted.required("principal_manifest")))deny();int count=0;for(var service:principal.required("services"))if(certificate.serviceUri().equals(service.required("service_uri").asText()))count++;if(supervisorRead)supervisor(fresh);else if(count!=1)deny();
            var binding=one(permissions.currentBinding(actorId()));if(!actorId().equals(binding.userId())||!"ACTIVE".equals(binding.status()))deny();
            if("local".equals(binding.mode())){if(!actorId().equals(binding.localUserId()))deny();one(db.query("SELECT user_id FROM platform_local_account WHERE user_id=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getString(1),actorId()));}
            else if("oidc".equals(binding.mode())){if(binding.localUserId()!=null)deny();one(db.query("SELECT user_id FROM platform_identity WHERE identity_id=? AND user_id=? AND issuer=? AND subject=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getString(1),binding.identityId(),actorId(),binding.issuer(),binding.subject()));}else deny();
            one(db.query("SELECT user_id FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE' AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6)) FOR UPDATE",(r,n)->r.getString(1),tenantId(),actorId()));if(!"ACTIVE".equals(one(permissions.projectStatus(original.project()))))deny();
            for(var permission:runtime.permissions(principal)){var owner=one(permissions.resourceOwner(permission.scope().resource_kind(),permission.scope().resource_id()));if(!permission.scope().resource_id().equals(owner.id())||!Objects.equals(permission.scope().project_id(),owner.projectId()))deny();grants.requireResource(permission.scope(),actorId(),binding.mode(),permission.action(),permission.purpose(),permissions.resourceGrants(actorId(),permission.action()),permissions.databaseTime());}
            var document=new PermissionProtocol.Scope(tenantId(),original.project(),"DOCUMENT",original.document());for(String action:List.of("document.read","document.check"))grants.requireResource(document,actorId(),binding.mode(),action,"DOCUMENT_BODY",permissions.resourceGrants(actorId(),action),permissions.databaseTime());
            return new State(new AuthenticatedIdentityBinding.View(binding.mode(),binding.userId(),binding.issuer(),binding.subject()),principal,scope);
        }
    }
    private record State(AuthenticatedIdentityBinding.View binding,JsonNode principal,JsonNode scope){}
    static final class Proof {
        private final Reads reads;private final State state;private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> resources=Map.copyOf(TransactionSynchronizationManager.getResourceMap());private boolean active=true;
        private Proof(Reads reads,State state){this.reads=reads;this.state=state;}
        void requireActive(){reads.requireActive();if(!active||!TransactionSynchronizationManager.isActualTransactionActive()||scope!=TenantScope.requireCurrent()||resources.isEmpty())deny();var actual=TransactionSynchronizationManager.getResourceMap();if(actual.size()!=resources.size()||resources.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))deny();}
        void require(PermissionProtocol.Scope scope,String action,String purpose){requireActive();var j=reads.job();boolean document=scope.equals(new PermissionProtocol.Scope(j.tenant(),j.project(),"DOCUMENT",j.document()))&&Set.of("document.read","document.check").contains(action)&&"DOCUMENT_BODY".equals(purpose);boolean read=action!=null&&action.endsWith(".read")&&ThrowingSupplier.of(()->readsRuntime().permissions(state.principal()).contains(new RunTokenAuthoritySchema.Permission(scope,action,purpose))).get();boolean release="task.run".equals(action)&&"RUN_CREATION".equals(purpose)&&scope.equals(new PermissionProtocol.Scope(j.tenant(),j.project(),"TASK",reads.admission().required("document_baseline").required("task_id").asText()));boolean pinned=ThrowingSupplier.of(()->reads.pinnedRead(scope,action,purpose)).get();if(!document&&!read&&!release&&!pinned)deny();}
        PermissionProtocol.GrantRow requireOriginalAction(PermissionProtocol.Scope scope,String action,String purpose)throws java.io.IOException {
            requireActive();if(!readsRuntime().permissions(state.principal()).contains(new RunTokenAuthoritySchema.Permission(scope,action,purpose)))deny();
            return reads.originalGrant(scope,action,purpose,state.binding().mode());
        }
        void requireSupervisor(){requireActive();if(!reads.supervisorRead)deny();}
        private RunTokenAuthoritySchema readsRuntime(){return reads.ownerRuntime();}
        void requireVersion(String id,String hash){requireActive();reads.requireVersion(id,hash);}void requireRelease(String release){requireActive();reads.requirePublication(release,"RUN_CREATION");}
        String actorId(){requireActive();return reads.actorId();}AuthenticatedIdentityBinding.View bindingView(){requireActive();return state.binding();}
        Object identity(){requireActive();return List.of("CHECK_DOCUMENT_CONTROL_READ",reads.tenantId(),reads.runId(),reads.job().operation(),actorId(),reads.certificate.serviceUri(),reads.certificate.trustRevision(),reads.certificate.certificateSha256(),reads.job().inputHash());}
        DocumentReviewExecutionService.Job job(){requireActive();return reads.job();}
    }
    private static <T>T one(List<T> values){if(values.size()!=1||values.get(0)==null)deny();return values.get(0);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
