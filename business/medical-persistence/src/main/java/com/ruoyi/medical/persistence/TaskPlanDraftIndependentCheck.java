package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Independent server reread, not a model verdict, professional assurance or human approval. */
public final class TaskPlanDraftIndependentCheck {
    private final JdbcTemplate db;private final TaskPlanDraftSchema schema;private final PermissionSchema json;
    private final TaskPlanDraftCandidatePolicy policy;private final TaskPlanService plans;private final PermissionMapper permissions;
    private final TemplateMapper templates;private final ResourceReadAuthority resources;private final SchemaCatalogue shared;
    public TaskPlanDraftIndependentCheck(DataSource data,TaskPlanDraftSchema schema,PermissionSchema json,
            TaskPlanDraftCandidatePolicy policy,TaskPlanService plans,PermissionMapper permissions,TemplateMapper templates,ResourceReadAuthority resources)throws Exception {
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.json=json;this.policy=policy;this.plans=plans;this.permissions=permissions;this.templates=templates;this.resources=resources;shared=SchemaCatalogue.fromClasspath();
    }
    Checked check(TaskPlanDraftCandidateRegistration.Registered candidate)throws Exception {
        candidate.requireUnchanged();var current=candidate.produced().authority().current();current.requireUnchanged();
        var report=inspect(current,candidate.candidate());var j=current.job();String id=UUID.randomUUID().toString();
        var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
        var receipt=json.object().put("schema_version","1.0.0").put("tenant_id",j.tenant()).put("project_id",j.project()).put("task_id",j.task())
                .put("task_revision",j.taskRevision()).put("run_id",j.run()).put("actor_id",j.actor()).put("check_id",id)
                .put("candidate_receipt_sha256",candidate.receiptSha256()).put("candidate_sha256",schema.hash(candidate.candidate()))
                .put("target_kind","PLAN_CANDIDATE").put("target_revision",candidate.artifactId()).put("target_sha256",schema.hash(candidate.candidate()))
                .put("source_scope_sha256",schema.hash(current.current().evidence().required("sources"))).put("check_implementation","TASK_PLAN_DRAFT_SERVER_CHECK_V1")
                .put("authority_sha256",current.current().digest()).put("coverage_sha256",schema.hash(json.tree(report.coverage())))
                .put("result",report.missing().isEmpty()?"COMPLETE":"INCOMPLETE").put("created_at",now.toInstant(ZoneOffset.UTC).toString());
        receipt.set("template",json.tree(current.current().original().request().template()));receipt.set("profile",json.tree(current.current().original().request().profile()));receipt.set("missing_item_ids",json.tree(report.missing()));
        schema.validate("CheckReceipt",receipt);String raw=schema.canonical(receipt),hash=schema.hash(receipt),audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();
        var log=metadata(j,now);log.put("audit_id",audit);log.put("subject_id",j.actor());log.put("action","task.plan_draft.independent_check");log.put("resource_ref",raw);log.put("result","SUCCESS");log.put("request_id",id);log.put("trace_id",j.run());insert("audit_event",log);
        insert("outbox_event",Map.of("tenant_id",j.tenant(),"event_id",event,"aggregate_id",j.run(),"aggregate_revision",1,"event_type","task.plan_draft.independent_check","payload",raw,"occurred_at",now));
        var row=metadata(j,now);row.put("task_id",j.task());row.put("run_id",j.run());row.put("check_id",id);row.put("candidate_receipt_sha256",candidate.receiptSha256());row.put("receipt_json",raw);row.put("receipt_sha256",hash);row.put("audit_id",audit);row.put("event_id",event);insert("task_plan_draft_check",row);
        candidate.requireUnchanged();var checked=new Checked(candidate,receipt,row,log,event,now);checked.requireUnchanged();return checked;
    }
    Checked read(TaskPlanDraftCandidateRegistration.Registered candidate,String checkId)throws Exception {
        candidate.requireUnchanged();WireValues.uuid(checkId);var j=candidate.job();
        var row=one(db.queryForList("SELECT * FROM task_plan_draft_check WHERE tenant_id=? AND run_id=? AND check_id=? LIMIT 2",j.tenant(),j.run(),checkId));
        var receipt=schema.validate("CheckReceipt",schema.read((String)row.get("receipt_json")));
        if(!schema.hash(receipt).equals(row.get("receipt_sha256"))||!candidate.receiptSha256().equals(receipt.required("candidate_receipt_sha256").asText())
                ||!candidate.artifactId().equals(receipt.required("target_revision").asText())||!schema.hash(candidate.candidate()).equals(receipt.required("target_sha256").asText()))bad();
        var value=row.get("created_at");var at=value instanceof java.sql.Timestamp t?t.toLocalDateTime():(LocalDateTime)value;
        var log=metadata(j,at);log.put("audit_id",row.get("audit_id"));log.put("subject_id",j.actor());log.put("action","task.plan_draft.independent_check");log.put("resource_ref",schema.canonical(receipt));log.put("result","SUCCESS");log.put("request_id",checkId);log.put("trace_id",j.run());
        var stored=metadata(j,at);stored.put("task_id",j.task());stored.put("run_id",j.run());stored.put("check_id",checkId);stored.put("candidate_receipt_sha256",candidate.receiptSha256());stored.put("receipt_json",schema.canonical(receipt));stored.put("receipt_sha256",schema.hash(receipt));stored.put("audit_id",row.get("audit_id"));stored.put("event_id",row.get("event_id"));
        var checked=new Checked(candidate,receipt,stored,log,(String)row.get("event_id"),at);checked.requireUnchanged();return checked;
    }
    private Report inspect(TaskPlanDraftJobAuthority.Verified authority,JsonNode raw)throws Exception {
        authority.requireUnchanged();var current=authority.current();var a=authority.admission();var fixed=current.template();
        var candidate=TypedSchemaBindings.bind(shared,ArtifactBindings.PLAN_CANDIDATE,raw).read();var allowed=new HashSet<String>();
        var requested=new TreeSet<String>();candidate.sections().forEach(s->requested.addAll(s.completionRuleIds()));candidate.questions().forEach(q->requested.addAll(q.completionRuleIds()));
        for(String rule:requested){var ref=fixed.template().dependency_refs().stream().filter(r->r.version_id().equals(rule)).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
            var basis=one(permissions.basis(rule));if(basis.ownerProjectId()!=null||!ref.content_sha256().equals(basis.sha256()))bad();
            resources.require(a,new PermissionProtocol.Scope(authority.job().tenant(),null,"RULE",rule),"rule.read","RUN_CREATION");
            if(!"APPROVED".equals(one(templates.ruleState(Map.of("userId",a.actorId(),"upstreamId",rule)))))throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);allowed.add(rule);
        }
        var checked=policy.inspect(candidate,current.fixedTask().head().profileRevision(),fixed,allowed);var missing=new TreeSet<String>();
        checked.missing().forEach(m->missing.add(m.kind()+":"+m.targetId()));
        var material=schema.read(current.fixedTask().input().missingRequirements());if(!material.isArray())bad();
        for(var value:material){var requirement=TypedSchemaBindings.bind(shared,ResourceBindings.REQUIREMENT,value).read();missing.add("SOURCE_REQUIREMENT_MISSING:"+requirement.requirementId());}
        for(var section:candidate.sections())if(section.ownerId()!=null){boolean eligible=a.planDraftWorker()?plans.creationOwnerEligiblePlanDraft(a,current.project(),section.ownerId()):plans.creationOwnerEligible(a,current.project(),section.ownerId());if(!eligible)missing.add("OWNER_INELIGIBLE:"+section.sectionId());}
        authority.requireUnchanged();return new Report(checked.coverage(),List.copyOf(missing));
    }
    private record Report(List<TaskPlanDraftCandidatePolicy.Coverage> coverage,List<String> missing) { }
    public final class Checked {
        private final TaskPlanDraftCandidateRegistration.Registered candidate;private final JsonNode receipt;private final Map<String,Object> row,log;private final String event;private final LocalDateTime at;
        private Checked(TaskPlanDraftCandidateRegistration.Registered candidate,JsonNode receipt,Map<String,Object> row,Map<String,Object> log,String event,LocalDateTime at){this.candidate=candidate;this.receipt=receipt.deepCopy();this.row=Map.copyOf(row);this.log=Map.copyOf(log);this.event=event;this.at=at;}
        void requireUnchanged()throws Exception {candidate.requireUnchanged();var current=candidate.produced().authority().current();var fresh=inspect(current,candidate.candidate());
            if(!schema.hash(json.tree(fresh.coverage())).equals(receipt.required("coverage_sha256").asText())||!json.tree(fresh.missing()).equals(receipt.required("missing_item_ids")))bad();var j=candidate.job();
            same(row,one(db.queryForList("SELECT * FROM task_plan_draft_check WHERE tenant_id=? AND check_id=? LIMIT 2",j.tenant(),receipt.required("check_id").asText())));
            same(log,one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",j.tenant(),row.get("audit_id"))));
            same(Map.of("tenant_id",j.tenant(),"event_id",event,"aggregate_id",j.run(),"aggregate_revision",1,"event_type","task.plan_draft.independent_check","payload",schema.canonical(receipt),"occurred_at",at),one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",j.tenant(),event)));
        }
        JsonNode receipt(){return receipt.deepCopy();}String receiptSha256()throws Exception{return schema.hash(receipt);}TaskPlanDraftCandidateRegistration.Registered candidate(){return candidate;}
    }
    private Map<String,Object> metadata(TaskPlanDraftJobs.Job j,LocalDateTime at){var m=new LinkedHashMap<String,Object>();m.put("tenant_id",j.tenant());m.put("project_id",j.project());m.put("created_by",j.actor());m.put("created_at",at);m.put("classification",j.classification());m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",schema.manifest());return m;}
    private void insert(String table,Map<String,Object> m){var keys=new ArrayList<>(m.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(m::get).toArray())!=1)bad();}
    private void same(Map<String,Object> expected,Map<String,Object> actual){for(var e:expected.entrySet()){Object value=actual.get(e.getKey());if(value instanceof java.sql.Timestamp t)value=t.toLocalDateTime();if(e.getValue() instanceof Number&&value instanceof Number){if(!e.getValue().toString().equals(value.toString()))bad();}else if(Set.of("payload","resource_ref","requirement_refs","allowed_resolution_actions").contains(e.getKey())&&e.getValue() instanceof String left&&value instanceof String right){try{if(!schema.read(left).equals(schema.read(right)))bad();}catch(Exception failure){bad();}}else if(!Objects.equals(e.getValue(),value))bad();}}
    private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
