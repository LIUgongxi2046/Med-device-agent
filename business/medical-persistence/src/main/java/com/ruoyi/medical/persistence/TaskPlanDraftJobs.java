package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Real pre-plan job registration. QUEUED is not launcher acceptance, a lease, a paid
 * reservation, a provider result, or a human plan decision. All writes share the caller's transaction. */
public final class TaskPlanDraftJobs {
    private final JdbcTemplate db;
    private final PermissionSchema json;
    private final TaskPlanDraftSchema schema;
    private final RunTokenAuthoritySchema runtime;
    public TaskPlanDraftJobs(DataSource data, PermissionSchema json, TaskPlanDraftSchema schema, RunTokenAuthoritySchema runtime) {
        db = new JdbcTemplate(Objects.requireNonNull(data)); db.setQueryTimeout(5);
        this.json = Objects.requireNonNull(json); this.schema = Objects.requireNonNull(schema); this.runtime = Objects.requireNonNull(runtime);
    }
    record Job(String tenant, String project, String task, String taskRevision, String run, String principal,
            String step, String action, String operation, String actor, String key, String requestHash, String inputHash,
            String admissionJson, String authorityJson, String scopeId, String ruleId, LocalDateTime created, String classification) { }

    Job queued(TaskPlanDraftAdmission.Verified proof, String operation, String key) throws Exception {
        Objects.requireNonNull(proof); WireValues.uuid(operation); current(proof.admission());
        if (key == null || !key.matches("[!-~]{16,128}")) bad();
        proof.requireUnchanged();
        // Only the private, physically verified package preflight can add this pin.
        if(!proof.evidence().required("package_preflight").isObject())bad();
        String tenant = proof.admission().context().tenant_id(), actor = proof.admission().actorId();
        var prior = db.query("SELECT run_id FROM task_plan_draft_job WHERE tenant_id=? AND created_by=? AND original_key=? LIMIT 2",
                (r,n)->r.getString(1), tenant, actor, key);
        if (!prior.isEmpty()) {
            var old = byRun(tenant, one(prior));
            if (!old.operation().equals(operation) || !old.requestHash().equals(schema.hash(schema.read(proof.original().canonical())))
                    || !old.task().equals(proof.task()) || !old.project().equals(proof.project())) conflict();
            return old;
        }
        String run=id(), principal=id(), step=id(), action=id(), scope=id(), rules=id(), audit=id(), event=id();
        var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
        var head=proof.fixedTask().head(); var request=proof.original().request();
        var allowed=proof.permissions();
        if(allowed.isEmpty() || allowed.stream().anyMatch(p->"plan.confirm".equals(p.action()))) denied();
        var scopeDocument=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",proof.project())
                .put("initiator_id",actor).put("run_id",run).put("execution_kind","SYSTEM_JOB");
        var resourceArray=scopeDocument.putArray("resources"); allowed.stream().map(RunTokenAuthoritySchema.Permission::scope).distinct().forEach(s->resourceArray.add(json.tree(s)));
        runtime.scopeManifest(json.canonical(scopeDocument),tenant,proof.project(),actor,run,"SYSTEM_JOB");
        var principalDocument=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",proof.project())
                .put("initiator_id",actor).put("run_id",run).put("principal_id",principal).put("scope_sha256",schema.hash(scopeDocument));
        principalDocument.putArray("services").add(json.object().put("service_uri",proof.worker().serviceUri()).put("audience",proof.worker().audience()));
        principalDocument.set("permissions",json.tree(allowed));
        runtime.principalManifest(json.canonical(principalDocument),tenant,proof.project(),actor,run,principal,scopeDocument);
        var input=json.object().put("schema_version","1.0.0").put("execution_kind","SYSTEM_JOB").put("job_type","PLAN_DRAFT").put("node_kind","N01")
                .put("tenant_id",tenant).put("project_id",proof.project()).put("task_id",proof.task()).put("task_revision",head.taskRevision())
                .put("run_id",run).put("step_id",step).putNull("plan_revision").put("scope_snapshot_id",head.snapshotId()).put("authority_sha256",proof.digest());
        input.put("fixed_inputs_json",schema.canonical(proof.evidence()));
        String inputHash=schema.hash(input), requestHash=schema.hash(schema.read(proof.original().canonical()));
        var admitted=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",proof.project()).put("task_id",proof.task())
                .put("task_revision",head.taskRevision()).put("run_id",run).put("actor_id",actor).put("execution_kind","SYSTEM_JOB").put("job_type","PLAN_DRAFT")
                .put("node_kind","N01").put("operation_id",operation).put("request_sha256",requestHash).put("context_revision",proof.context())
                .put("input_sha256",inputHash).put("scope_snapshot_id",head.snapshotId()).put("agent_release_id",request.agentReleaseId())
                .put("authority_sha256",proof.digest()).put("created_at",now.toInstant(ZoneOffset.UTC).toString())
                .put("principal_id",principal).put("step_id",step).put("action_id",action).put("scope_manifest_id",scope).put("rule_manifest_id",rules);
        admitted.set("dependency_versions",proof.evidence().required("dependency_versions"));
        admitted.set("template",json.tree(request.template())); admitted.set("profile",json.tree(request.profile())); admitted.set("execution_policy",json.tree(request.executionPolicy()));
        admitted.set("role_version",json.tree(proof.node().roleVersion())); admitted.set("model_version",json.tree(proof.node().modelVersion()));
        admitted.set("scope_manifest",scopeDocument); admitted.set("principal_manifest",principalDocument); admitted.set("input_manifest",input);
        var missing=admitted.putArray("missing_requirement_ids");
        for(var requirement:proof.template().missing_requirements()) missing.add(requirement.requirementId());
        schema.validate("Admission",admitted);
        var job=new Job(tenant,proof.project(),proof.task(),head.taskRevision(),run,principal,step,action,operation,actor,key,requestHash,inputHash,
                schema.canonical(admitted),schema.canonical(proof.evidence()),scope,rules,now,proof.classification());
        // A caller-supplied UUID never substitutes for the actual originating operation.
        if(one(db.query("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_id=? AND subject_id=? AND created_by=? AND request_sha256=? AND idempotency_key=? AND state='PROCESSING' AND operation_name='TASK-PLAN-DRAFT'",
                (r,n)->r.getInt(1),tenant,operation,actor,actor,requestHash,key))!=1)bad();
        var e=metadata(job,runtime.manifestSha256());e.put("run_id",run);e.put("project_id",job.project());e.put("execution_kind","SYSTEM_JOB");e.put("scope_manifest",json.canonical(scopeDocument));insert("execution_registry",e);
        var p=metadata(job,runtime.manifestSha256());p.put("principal_id",principal);p.put("project_id",job.project());p.put("initiator_id",actor);p.put("run_id",run);p.put("capability_manifest",json.canonical(principalDocument));p.put("status","ACTIVE");p.put("revision",1);p.put("updated_at",now);insert("run_principal",p);
        version(job,scope,schema.hash(scopeDocument));version(job,rules,schema.hash(Map.of("rule_versions",proof.releaseManifest().ruleVersions(),"execution_policy",request.executionPolicy())));
        var j=metadata(job,runtime.manifestSha256());j.put("run_id",run);j.put("execution_kind","SYSTEM_JOB");j.put("project_id",job.project());j.put("job_type","PLAN_DRAFT");j.put("initiator_id",actor);j.put("input_manifest",schema.canonical(input));j.put("config_revision",proof.node().modelVersion().version_id());j.put("budget_revision",request.enterpriseBudget().version_id());
        j.put("parent_budget_id",one(db.query("SELECT budget_id FROM budget_revision WHERE tenant_id=? AND budget_revision=? LIMIT 2",(r,n)->r.getString(1),tenant,request.parentBudget().version_id())));
        j.put("target_kind","TASK");j.put("target_id",job.task());j.put("target_revision",job.taskRevision());j.put("input_sha256",inputHash);j.put("scope_manifest_id",scope);j.put("rule_manifest_id",rules);j.put("status","QUEUED");j.put("workflow_id",null);j.put("revision",1);j.put("updated_at",now);insert("system_job_run",j);
        var s=metadata(job,runtime.manifestSha256());s.put("project_id",job.project());s.put("run_id",run);s.put("step_id",step);s.put("role_version",proof.node().roleVersion().version_id());s.put("input_sha256",inputHash);s.put("status","READY");s.put("lease_generation",0);s.put("revision",1);s.put("updated_at",now);insert("run_step",s);
        var ac=metadata(job,runtime.manifestSha256());ac.put("project_id",job.project());ac.put("run_id",run);ac.put("step_id",step);ac.put("action_id",action);ac.put("input_sha256",inputHash);ac.put("origin_run_id",run);ac.put("origin_step_id",step);ac.put("origin_action_id",action);ac.put("kind","HOST_CONTROL");ac.put("arguments_sha256",inputHash);ac.put("tool_version","PLAN_DRAFT_HOST_V1");ac.put("state","BOUND");ac.put("operation_id",operation);ac.put("lease_generation",0);ac.put("revision",1);ac.put("updated_at",now);insert("step_action_binding",ac);
        var receipt=json.object().put("schema_version","1.0.0").put("kind","PLAN_DRAFT_QUEUED").put("run_id",run).put("operation_id",operation).put("admission_sha256",schema.hash(admitted));
        var au=metadata(job,schema.manifest());au.put("project_id",job.project());au.put("audit_id",audit);au.put("subject_id",actor);au.put("action","task.plan_draft_queued");au.put("resource_ref",schema.canonical(receipt));au.put("result","SUCCESS");au.put("request_id",operation);au.put("trace_id",operation);insert("audit_event",au);
        insert("outbox_event",Map.of("tenant_id",tenant,"event_id",event,"aggregate_id",run,"aggregate_revision",1,"event_type","task.plan_draft_queued","payload",schema.canonical(receipt),"occurred_at",now));
        var side=metadata(job,schema.manifest());side.put("project_id",job.project());side.put("task_id",job.task());side.put("run_id",run);side.put("task_revision",job.taskRevision());side.put("operation_id",operation);side.put("original_key",key);side.put("request_body",proof.original().raw());side.put("request_body_sha256",WireValues.sha256(proof.original().raw().getBytes(java.nio.charset.StandardCharsets.UTF_8)));side.put("request_sha256",requestHash);side.put("admission_json",job.admissionJson());side.put("admission_sha256",schema.hash(admitted));side.put("authority_json",job.authorityJson());side.put("authority_sha256",proof.digest());side.put("input_sha256",inputHash);side.put("audit_id",audit);side.put("event_id",event);insert("task_plan_draft_job",side);
        proof.requireUnchanged();return byRun(tenant,run);
    }

    Job byRun(String tenant,String run)throws Exception {
        if(!TransactionSynchronizationManager.isActualTransactionActive()||!tenant.equals(TenantScope.requireCurrent().tenantId()))denied();
        var row=one(db.query("SELECT * FROM task_plan_draft_job WHERE tenant_id=? AND run_id=? LIMIT 2",(r,n)-> {
            var m=new LinkedHashMap<String,Object>(); for(String k:List.of("tenant_id","project_id","task_id","task_revision","run_id","operation_id","created_by","original_key","request_sha256","input_sha256","admission_json","admission_sha256","authority_json","authority_sha256","request_body","request_body_sha256","schema_manifest_sha256","classification","audit_id","event_id"))m.put(k,r.getString(k));m.put("created_at",r.getTimestamp("created_at").toLocalDateTime());return m;},tenant,run));
        var a=schema.validate("Admission",schema.read((String)row.get("admission_json")));var authority=schema.read((String)row.get("authority_json"));
        if(!schema.manifest().equals(row.get("schema_manifest_sha256"))||!schema.hash(a).equals(row.get("admission_sha256"))||!schema.hash(authority).equals(row.get("authority_sha256"))||!schema.hash(a.required("input_manifest")).equals(row.get("input_sha256")))bad();
        var original=schema.start(((String)row.get("request_body")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(!WireValues.sha256(original.raw().getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(row.get("request_body_sha256"))||!schema.hash(schema.read(original.canonical())).equals(row.get("request_sha256")))bad();
        for(var pair:Map.of("tenant_id","tenant_id","project_id","project_id","task_id","task_id","task_revision","task_revision","run_id","run_id","actor_id","created_by","operation_id","operation_id","input_sha256","input_sha256","authority_sha256","authority_sha256","request_sha256","request_sha256").entrySet())if(!text(a,pair.getKey()).equals(row.get(pair.getValue())))bad();
        var job=new Job(tenant,(String)row.get("project_id"),(String)row.get("task_id"),(String)row.get("task_revision"),run,text(a,"principal_id"),text(a,"step_id"),text(a,"action_id"),(String)row.get("operation_id"),(String)row.get("created_by"),(String)row.get("original_key"),(String)row.get("request_sha256"),(String)row.get("input_sha256"),(String)row.get("admission_json"),(String)row.get("authority_json"),text(a,"scope_manifest_id"),text(a,"rule_manifest_id"),(LocalDateTime)row.get("created_at"),(String)row.get("classification"));
        runtime.scopeManifest(schema.canonical(a.required("scope_manifest")),tenant,job.project(),job.actor(),run,"SYSTEM_JOB");runtime.principalManifest(schema.canonical(a.required("principal_manifest")),tenant,job.project(),job.actor(),run,job.principal(),a.required("scope_manifest"));
        equal("execution_registry","scope_manifest","run_id",run,a.required("scope_manifest"),tenant);equal("run_principal","capability_manifest","principal_id",job.principal(),a.required("principal_manifest"),tenant);equal("system_job_run","input_manifest","run_id",run,a.required("input_manifest"),tenant);
        if(one(db.query("SELECT COUNT(*) FROM system_job_run WHERE tenant_id=? AND project_id=? AND run_id=? AND initiator_id=? AND execution_kind='SYSTEM_JOB' AND job_type='PLAN_DRAFT' AND target_kind='TASK' AND target_id=? AND target_revision=? AND input_sha256=? AND scope_manifest_id=? AND rule_manifest_id=? AND config_revision=?",(r,n)->r.getInt(1),tenant,job.project(),run,job.actor(),job.task(),job.taskRevision(),job.inputHash(),job.scopeId(),job.ruleId(),text(a.required("model_version"),"version_id")))!=1)bad();
        if(one(db.query("SELECT COUNT(*) FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND role_version=? AND input_sha256=? AND section_id IS NULL",(r,n)->r.getInt(1),tenant,job.project(),run,job.step(),text(a.required("role_version"),"version_id"),job.inputHash()))!=1)bad();
        if(one(db.query("SELECT COUNT(*) FROM step_action_binding WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND input_sha256=? AND arguments_sha256=? AND operation_id=? AND kind='HOST_CONTROL' AND tool_version='PLAN_DRAFT_HOST_V1' AND origin_run_id=run_id AND origin_step_id=step_id AND origin_action_id=action_id",(r,n)->r.getInt(1),tenant,run,job.step(),job.action(),job.inputHash(),job.inputHash(),job.operation()))!=1)bad();
        var receipt=json.object().put("schema_version","1.0.0").put("kind","PLAN_DRAFT_QUEUED").put("run_id",run).put("operation_id",job.operation()).put("admission_sha256",schema.hash(a));
        equal("audit_event","resource_ref","audit_id",(String)row.get("audit_id"),receipt,tenant);equal("outbox_event","payload","event_id",(String)row.get("event_id"),receipt,tenant);
        return job;
    }
    TaskPlanDraftSchema.Original original(Job job)throws Exception {if(!job.equals(byRun(job.tenant(),job.run())))bad();return schema.start(one(db.query("SELECT request_body FROM task_plan_draft_job WHERE tenant_id=? AND run_id=? LIMIT 2",(r,n)->r.getString(1),job.tenant(),job.run())).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    JsonNode input(Job job)throws Exception{return schema.read(job.admissionJson()).required("input_manifest").deepCopy();}
    private void equal(String table,String column,String idColumn,String id,JsonNode expected,String tenant)throws Exception {var actual=one(db.query("SELECT "+column+" FROM "+table+" WHERE tenant_id=? AND "+idColumn+"=? LIMIT 2",(r,n)->r.getString(1),tenant,id));if(!schema.canonical(expected).equals(schema.canonical(schema.read(actual))))bad();}
    private void version(Job j,String id,String hash){insert("version_registry",Map.of("tenant_id",j.tenant(),"version_id",id,"owner_project_id",j.project(),"entity_kind","TASK_PLAN_DRAFT_MANIFEST","content_sha256",hash));}
    private Map<String,Object> metadata(Job j,String manifest){var m=new LinkedHashMap<String,Object>();m.put("tenant_id",j.tenant());m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",manifest);m.put("created_by",j.actor());m.put("created_at",j.created());m.put("classification",j.classification());return m;}
    private void insert(String table,Map<String,Object> values){var keys=new ArrayList<>(values.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(values::get).toArray())!=1)bad();}
    private static void current(EnterpriseAccess.Admission a){if(!TransactionSynchronizationManager.isActualTransactionActive()||!a.context().tenant_id().equals(TenantScope.requireCurrent().tenantId())||!a.actorId().equals(TenantScope.requireCurrent().subjectId()))denied();}
    private static String id(){return UUID.randomUUID().toString();} private static String text(JsonNode n,String key){var v=n.required(key);if(!v.isTextual())bad();return v.textValue();}
    private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}
    private static void conflict(){throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);}private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
