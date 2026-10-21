package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Exact PLAN_DRAFT/N01 pre-plan and lease capabilities. No generic SYSTEM_JOB allowlist,
 * fake plan, caller permission manifest, or caller lease data is authority. */
public final class TaskPlanDraftRuntimeAuthority {
    private final JdbcTemplate db; private final TaskPlanDraftJobs jobs;
    private final TaskPlanDraftSchema schema; private final RunTokenAuthoritySchema tokens;
    private final FormalCapabilityIdentityAuthority roles;
    public TaskPlanDraftRuntimeAuthority(DataSource data,TaskPlanDraftJobs jobs,TaskPlanDraftSchema schema,
            RunTokenAuthoritySchema tokens,FormalCapabilityIdentityAuthority roles) {
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.jobs=jobs;this.schema=schema;this.tokens=tokens;this.roles=roles;
    }
    Bootstrap requireBootstrap(TaskPlanDraftJobAuthority.Verified current,String service,String audience)throws Exception {
        current.requireUnchanged();var j=current.job();var a=current.admission();
        var admitted=schema.read(j.admissionJson());var role=admitted.required("role_version");
        var basis=new PermissionProtocol.Basis(text(role,"version_id"),text(role,"content_sha256"));
        var identity=roles.require(a,basis,"ROLE",j.project(),"RUN_CREATION");
        var principal=admitted.required("principal_manifest");tokens.service(principal,service,audience);
        tokens.role(schema.canonical(identity.canonicalDefinition().required("request").required("tool_scope")),principal);
        var allowed=tokens.permissions(principal);
        if(!allowed.equals(current.nodePermissions())||allowed.isEmpty()||allowed.stream().anyMatch(p->"plan.confirm".equals(p.action())))deny();
        if(!service.equals(current.worker().serviceUri())||!audience.equals(current.worker().audience()))deny();
        var job=jobs.byRun(j.tenant(),j.run());if(!job.equals(j))stale();
        current.requireUnchanged();return new Bootstrap(current,service,audience,basis,allowed,schema.hash(identity.canonicalDefinition()));
    }
    final class Bootstrap {
        private final TaskPlanDraftJobAuthority.Verified current;private final String service,audience,roleHash;
        private final PermissionProtocol.Basis role;private final List<RunTokenAuthoritySchema.Permission> permissions;
        private Bootstrap(TaskPlanDraftJobAuthority.Verified current,String service,String audience,PermissionProtocol.Basis role,List<RunTokenAuthoritySchema.Permission> permissions,String roleHash){this.current=current;this.service=service;this.audience=audience;this.role=role;this.permissions=List.copyOf(permissions);this.roleHash=roleHash;}
        void requireUnchanged()throws Exception{var fresh=requireBootstrap(current,service,audience);if(!roleHash.equals(fresh.roleHash)||!permissions.equals(fresh.permissions)||!role.equals(fresh.role))stale();}
        String roleVersion(){return role.version_id();}String roleRawSha(){return role.content_sha256();}
        List<RunTokenAuthoritySchema.Permission> nodePermissions(){return permissions;}
        TaskPlanDraftJobAuthority.Verified current(){return current;}
    }
    Verified requireLeased(TaskPlanDraftJobAuthority.Verified current,String action,BigInteger generation,String service,String audience)throws Exception {
        var bootstrap=requireBootstrap(current,service,audience);WireValues.uuid(action);WireValues.revision(generation.toString());
        var j=current.job();var evidence=leased(j,action,generation,service);
        return new Verified(current,bootstrap,action,generation,service,evidence);
    }
    private String leased(TaskPlanDraftJobs.Job j,String action,BigInteger generation,String service)throws Exception {
        var row=one(db.queryForList("SELECT s.*,j.status job_status,j.ended_at job_ended,p.status principal_status FROM run_step s JOIN system_job_run j ON j.tenant_id=s.tenant_id AND j.run_id=s.run_id AND j.project_id=s.project_id JOIN run_principal p ON p.tenant_id=s.tenant_id AND p.run_id=s.run_id AND p.project_id=s.project_id WHERE s.tenant_id=? AND s.project_id=? AND s.run_id=? AND s.step_id=? AND j.job_type='PLAN_DRAFT' AND j.execution_kind='SYSTEM_JOB' AND j.target_kind='TASK' AND j.target_id=? AND j.target_revision=? AND p.principal_id=? AND p.initiator_id=? LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),j.task(),j.taskRevision(),j.principal(),j.actor()));
        if(!"RUNNING".equals(row.get("job_status"))||row.get("job_ended")!=null||!"ACTIVE".equals(row.get("principal_status"))||!"RUNNING".equals(row.get("status"))||!service.equals(row.get("lease_owner"))||!generation.toString().equals(row.get("lease_generation").toString())||!j.inputHash().equals(row.get("input_sha256"))||row.get("section_id")!=null)deny();
        var expires=one(db.query("SELECT lease_expires_at FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",(r,n)->r.getTimestamp(1).toLocalDateTime(),j.tenant(),j.run(),j.step()));
        if(!now().isBefore(expires))throw new TenantAccessException(ErrorCode.LEASE_STALE);
        var binding=one(db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND input_sha256=? AND lease_generation=? AND created_by=? LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step(),action,j.inputHash(),generation,j.actor()));
        if(!Set.of("HOST_CONTROL","MODEL_CALL","TOOL_CALL").contains(binding.get("kind"))||!Set.of("BOUND","RESERVED","DISPATCHING","IN_FLIGHT","SETTLED").contains(binding.get("state")))deny();
        // Reservation/dispatch may update their own state, invocation and counters in
        // this transaction. Pin immutable identity; live lease/status are rechecked above.
        var stepPins=new LinkedHashMap<String,Object>();for(String key:List.of("tenant_id","project_id","run_id","step_id","node_kind","section_id","input_sha256","lease_generation","lease_owner"))stepPins.put(key,row.get(key));
        var actionPins=new LinkedHashMap<String,Object>(binding);for(String key:List.of("state","invocation_id","revision","updated_at"))actionPins.remove(key);
        return schema.hash(AgentPublicationWitness.tree(Map.of("job",j,"step",stepPins,"action",actionPins)));
    }
    public final class Verified {
        private final TaskPlanDraftJobAuthority.Verified current;private final Bootstrap bootstrap;private final String action,service,fingerprint;private final BigInteger generation;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();
        private final Map<Object,Object> resources=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Verified(TaskPlanDraftJobAuthority.Verified current,Bootstrap bootstrap,String action,BigInteger generation,String service,String fingerprint){this.current=current;this.bootstrap=bootstrap;this.action=action;this.generation=generation;this.service=service;this.fingerprint=fingerprint;if(resources.isEmpty())deny();}
        public void requireActive(){if(!TransactionSynchronizationManager.isActualTransactionActive()||thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent())deny();var actual=TransactionSynchronizationManager.getResourceMap();if(actual.size()!=resources.size()||resources.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))deny();}
        public void requireUnchanged()throws Exception{requireActive();current.requireUnchanged();bootstrap.requireUnchanged();if(!fingerprint.equals(leased(current.job(),action,generation,service)))stale();}
        public EnterpriseAccess.Admission admission(){requireActive();return current.admission();}
        TaskPlanDraftJobs.Job job(){requireActive();return current.job();}
        public String stepId(){return job().step();}public String actionId(){requireActive();return action;}
        public BigInteger leaseGeneration(){requireActive();return generation;}public String workerService(){requireActive();return service;}
        public String inputSha256(){return job().inputHash();}
        TaskPlanDraftJobAuthority.Verified current(){requireActive();return current;}
    }
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}
    private static <T>T one(List<T> values){if(values.size()!=1||values.get(0)==null)bad();return values.get(0);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
