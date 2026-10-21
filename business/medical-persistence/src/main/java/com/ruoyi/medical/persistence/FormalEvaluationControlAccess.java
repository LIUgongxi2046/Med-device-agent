package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Independent, bounded EVALUATION_VERIFICATION admission. Never creates a human session,
 * TASK proof, evaluation approval, source right or grant. The original initiator's grants
 * and authenticated service are rechecked in every transaction and object-read chunk. */
public final class FormalEvaluationControlAccess {
    private final JdbcTemplate db;
    private final TenantTransaction transactions;
    private final ServiceCertificateIdentity identities;
    private final PermissionMapper permissions;
    private final PermissionAuthority grants;
    private final RunTokenAuthoritySchema runtime;
    private final FormalEvaluationJobs jobs;
    private final Duration maximum;
    private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public FormalEvaluationControlAccess(DataSource data,TenantTransaction transactions,ServiceCertificateIdentity identities,
            PermissionMapper permissions,PermissionAuthority grants,RunTokenAuthoritySchema runtime,FormalEvaluationJobs jobs,Duration maximum) {
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.transactions=transactions;this.identities=identities;
        this.permissions=permissions;this.grants=grants;this.runtime=runtime;this.jobs=jobs;
        if(maximum==null||maximum.isZero()||maximum.isNegative()||maximum.compareTo(Duration.ofMinutes(10))>0)throw new IllegalArgumentException("EVALUATION_READ_BOUND_REQUIRED");this.maximum=maximum;
    }
    public <T>T withReads(HttpServletRequest http,String tenant,String run,Function<Reads,T> operation)throws Exception {
        WireValues.uuid(tenant);WireValues.uuid(run);if(TransactionSynchronizationManager.isActualTransactionActive())deny();
        var peer=identities.require(http);
        String actor=one(db.query("SELECT created_by FROM formal_evaluation_job WHERE tenant_id=? AND run_id=? LIMIT 2",(r,n)->r.getString(1),tenant,run));
        var reads=new Reads(http,tenant,run,actor,peer,null);
        try{reads.execute(a->null);return operation.apply(reads);}finally{reads.active=false;}
    }
    /** Independent supervisor identity, never a worker-selected flag. */
    public <T>T withSupervisorReads(HttpServletRequest http,String tenant,String run,FormalEvaluationDeployment deployment,Function<Reads,T> operation)throws Exception {
        WireValues.uuid(tenant);WireValues.uuid(run);if(TransactionSynchronizationManager.isActualTransactionActive())deny();
        var peer=identities.require(http);requireSupervisor(peer,deployment);
        String actor=one(db.query("SELECT created_by FROM formal_evaluation_job WHERE tenant_id=? AND run_id=? LIMIT 2",(r,n)->r.getString(1),tenant,run));
        var reads=new Reads(http,tenant,run,actor,peer,deployment);
        try{reads.execute(a->null);return operation.apply(reads);}finally{reads.active=false;}
    }
    private static void requireSupervisor(ServiceCertificateIdentity.Verified peer,FormalEvaluationDeployment deployment){
        var d=deployment.value();if(deployment.workerService().equals(peer.serviceUri())||!d.required("supervisor_service_uri").asText().equals(peer.serviceUri())||!d.required("supervisor_certificate_sha256").asText().equals(peer.certificateSha256())||!d.required("supervisor_trust_revision").asText().equals(peer.trustRevision()))deny();
    }
    public final class Reads {
        private final HttpServletRequest http;private final String tenant,run,actor;
        private final ServiceCertificateIdentity.Verified peer;private final FormalEvaluationDeployment supervisor;
        private final Thread thread=Thread.currentThread();private final long deadline=System.nanoTime()+maximum.toNanos();
        private boolean active=true;private EnterpriseAccess.Admission current;private FormalEvaluationJobs.Job original;
        private Reads(HttpServletRequest http,String tenant,String run,String actor,ServiceCertificateIdentity.Verified peer,FormalEvaluationDeployment supervisor){this.supervisor=supervisor;this.http=http;this.tenant=tenant;this.run=run;this.actor=actor;this.peer=peer;}
        private RunTokenAuthoritySchema runtimeOwner(){return runtime;}
        public void requireActive(){if(!active||thread!=Thread.currentThread()||System.nanoTime()>=deadline)deny();}
        public String tenantId(){requireActive();return tenant;}public String runId(){requireActive();return run;}
        public String actorId(){requireActive();return actor;}public String contextRevision(){requireActive();return "0";}
        public String workerService(){requireActive();return supervisor==null?peer.serviceUri():supervisor.workerService();}
        public void requireSupervisor(){requireActive();if(supervisor==null)deny();FormalEvaluationControlAccess.requireSupervisor(peer,supervisor);}
        public FormalEvaluationJobs.Job job(){requireActive();if(original==null)deny();return original;}
        public <T>T execute(Function<EnterpriseAccess.Admission,T> operation){
            requireActive();if(TransactionSynchronizationManager.isActualTransactionActive()){
                if(current==null||current.evaluationProof().reads!=this)deny();try{return operation.apply(current);}finally{current.evaluationProof().requireActive();}
            }
            var epochs=one(db.query("SELECT auth_epoch,governance_epoch FROM tenant WHERE tenant_id=? LIMIT 2",(r,n)->new TenantWatermarks(r.getBigDecimal(1).toBigIntegerExact(),r.getBigDecimal(2).toBigIntegerExact()),tenant));
            try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ZERO,epochs.authEpoch(),epochs.governanceEpoch()))){
                return transactions.execute(w->ThrowingSupplier.of(()->{
                    var before=state();var proof=new Proof(this,before);
                    current=new EnterpriseAccess.Admission(proof,new BusinessSessionProtocol.Context(tenant,"0",w.authEpoch().toString(),w.governanceEpoch().toString()),w);
                    try{var checked=jobs.byRun(current,run);if(original==null)original=checked;else if(!original.equals(checked))deny();
                        var result=operation.apply(current);proof.requireActive();if(!before.equals(state()))deny();return result;
                    }finally{current=null;proof.active=false;}
                }).get());
            }
        }
        private State state()throws Exception {
            requireActive();var nowPeer=identities.require(http);
            if(!peer.serviceUri().equals(nowPeer.serviceUri())||!peer.certificateSha256().equals(nowPeer.certificateSha256())||!peer.trustRevision().equals(nowPeer.trustRevision()))deny();
            var row=one(db.queryForList("SELECT j.project_id,j.canonical_admission,j.admission_sha256,j.created_by,s.job_type,s.execution_kind,s.target_kind,s.target_id,p.capability_manifest,p.status principal_status,e.scope_manifest FROM formal_evaluation_job j JOIN system_job_run s ON s.tenant_id=j.tenant_id AND s.run_id=j.run_id JOIN formal_evaluation_cell c ON c.tenant_id=j.tenant_id AND c.run_id=j.run_id JOIN run_principal p ON p.tenant_id=j.tenant_id AND p.run_id=j.run_id AND p.principal_id=c.principal_id JOIN execution_registry e ON e.tenant_id=j.tenant_id AND e.run_id=j.run_id WHERE j.tenant_id=? AND j.run_id=? LIMIT 2 FOR UPDATE",tenant,run));
            if(!actor.equals(row.get("created_by"))||!"SYSTEM_JOB".equals(row.get("execution_kind"))||!"EVALUATION_VERIFICATION".equals(row.get("job_type"))||!"EVALUATION".equals(row.get("target_kind"))||!"ACTIVE".equals(row.get("principal_status")))deny();
            String project=text(row,"project_id");var admitted=parser.readTree(text(row,"canonical_admission"));
            if(!WireValues.sha256(text(row,"canonical_admission").getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(row.get("admission_sha256")))deny();
            String principalId=admitted.required("principal_id").asText();
            var scope=runtime.scopeManifest(text(row,"scope_manifest"),tenant,project,actor,run,"SYSTEM_JOB");
            var principal=runtime.principalManifest(text(row,"capability_manifest"),tenant,project,actor,run,principalId,scope);
            if(!scope.equals(admitted.required("scope_manifest"))||!principal.equals(admitted.required("principal_manifest")))deny();
            int found=0;for(var service:principal.required("services"))if(peer.serviceUri().equals(service.required("service_uri").asText()))found++;if(supervisor==null){if(found!=1)deny();}else FormalEvaluationControlAccess.requireSupervisor(nowPeer,supervisor);
            var binding=one(permissions.currentBinding(actor));if(!"ACTIVE".equals(binding.status())||!actor.equals(binding.userId()))deny();
            if("local".equals(binding.mode())){if(!actor.equals(binding.localUserId()))deny();one(db.query("SELECT user_id FROM platform_local_account WHERE user_id=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getString(1),actor));}
            else if("oidc".equals(binding.mode())){if(binding.localUserId()!=null)deny();one(db.query("SELECT user_id FROM platform_identity WHERE identity_id=? AND user_id=? AND issuer=? AND subject=? AND status='ACTIVE' FOR UPDATE",(r,n)->r.getString(1),binding.identityId(),actor,binding.issuer(),binding.subject()));}else deny();
            one(db.query("SELECT user_id FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE' AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6)) FOR UPDATE",(r,n)->r.getString(1),tenant,actor));
            if(!"ACTIVE".equals(one(permissions.projectStatus(project))))deny();
            for(var permission:runtime.permissions(principal))originalGrant(permission.scope(),permission.action(),permission.purpose(),binding.mode());
            originalGrant(new PermissionProtocol.Scope(tenant,project,"EVALUATION",text(row,"target_id")),"evaluation.run","EVALUATION_RESULTS",binding.mode());
            return new State(new AuthenticatedIdentityBinding.View(binding.mode(),binding.userId(),binding.issuer(),binding.subject()),principal,scope,admitted);
        }
        private PermissionProtocol.GrantRow originalGrant(PermissionProtocol.Scope scope,String action,String purpose,String mode)throws java.io.IOException {
            if(!tenant.equals(scope.tenant_id())||Set.of("plan.confirm","agent.publish","evaluation.accept","evaluation.score").contains(action))deny();
            var owner=one(permissions.resourceOwner(scope.resource_kind(),scope.resource_id()));if(!scope.resource_id().equals(owner.id())||!Objects.equals(scope.project_id(),owner.projectId()))deny();
            return grants.requireResource(scope,actor,mode,action,purpose,permissions.resourceGrants(actor,action),permissions.databaseTime());
        }
        public void requireVersion(String id,String hash){
            requireActive();WireValues.uuid(id);WireValues.hash(hash);
            // A fixed dependency is not permission to read its bytes; ResourceReadAuthority
            // separately requires the original scoped grant for the actual business purpose.
            var j=job();boolean fixed=false;
            for(var row:j.nodes()){var n=row.node();for(var p:List.of(n.cell().candidate(),n.cell().policy(),n.cell().input(),n.cell().expected(),n.role()))if(id.equals(p.version_id())&&hash.equals(p.content_sha256()))fixed=true;
                if(n.model()!=null&&(id.equals(n.model().version_id())&&hash.equals(n.model().content_sha256())||id.equals(n.tokenizer().version_id())&&hash.equals(n.tokenizer().content_sha256())))fixed=true;}
            if(!fixed){
                var anchors=new LinkedHashSet<String>();for(var row:j.nodes()){var n=row.node();for(var p:List.of(n.cell().candidate(),n.cell().policy(),n.cell().input(),n.role()))anchors.add(p.version_id());if(n.model()!=null){anchors.add(n.model().version_id());anchors.add(n.tokenizer().version_id());}}
                var admission=ThrowingSupplier.of(()->parser.readTree(j.canonicalAdmission())).get();anchors.add(admission.required("context_binding").required("version_id").asText());
                var queue=new ArrayDeque<String>(anchors);var seen=new HashSet<String>();
                while(!queue.isEmpty()){String current=queue.removeFirst();if(!seen.add(current))continue;if(seen.size()>2048)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
                    var row=one(db.queryForList("SELECT content_sha256,owner_project_id FROM version_registry WHERE tenant_id=? AND version_id=? LIMIT 2",tenant,current));
                    if(row.get("owner_project_id")!=null&&!j.projectId().equals(row.get("owner_project_id")))deny();
                    if(id.equals(current)){if(!hash.equals(row.get("content_sha256")))deny();fixed=true;break;}
                    queue.addAll(db.query("SELECT upstream_version_id FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? LIMIT 2049",(r,n)->r.getString(1),tenant,current));
                }
            }
            if(!fixed)deny();
        }
    }
    private record State(AuthenticatedIdentityBinding.View binding,JsonNode principal,JsonNode scope,JsonNode admission){}
    static final class Proof {
        private final Reads reads;private final State state;private final TenantScope scope=TenantScope.requireCurrent();
        private final Map<Object,Object> resources=Map.copyOf(TransactionSynchronizationManager.getResourceMap());private boolean active=true;
        private Proof(Reads reads,State state){this.reads=reads;this.state=state;}
        void requireActive(){reads.requireActive();if(!active||!TransactionSynchronizationManager.isActualTransactionActive()||scope!=TenantScope.requireCurrent()||resources.isEmpty())deny();var actual=TransactionSynchronizationManager.getResourceMap();if(actual.size()!=resources.size()||resources.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))deny();}
        void require(PermissionProtocol.Scope scope,String action,String purpose){requireActive();if(!ThrowingSupplier.of(()->readsRuntime().permissions(state.principal()).contains(new RunTokenAuthoritySchema.Permission(scope,action,purpose))).get())deny();}
        PermissionProtocol.GrantRow requireOriginalAction(PermissionProtocol.Scope scope,String action,String purpose)throws java.io.IOException{require(scope,action,purpose);return reads.originalGrant(scope,action,purpose,state.binding().mode());}
        private RunTokenAuthoritySchema readsRuntime(){return readsRuntimeOwner(reads);}
        String actorId(){requireActive();return reads.actorId();}AuthenticatedIdentityBinding.View bindingView(){requireActive();return state.binding();}
        Object identity(){requireActive();return List.of("EVALUATION_VERIFICATION",reads.tenant,reads.run,reads.actor,reads.peer.serviceUri(),reads.peer.trustRevision(),reads.peer.certificateSha256());}
        FormalEvaluationJobs.Job job(){requireActive();return reads.job();}
    }
    private static RunTokenAuthoritySchema readsRuntimeOwner(Reads r){return r.runtimeOwner();}
    private static String text(Map<String,Object> row,String key){if(!(row.get(key) instanceof String))deny();return (String)row.get(key);}
    private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)deny();return rows.get(0);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
