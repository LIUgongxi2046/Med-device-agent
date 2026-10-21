package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fresh exact-version structural/source-policy check for human adoption and edited descendants. */
public final class TaskPlanDraftHumanCheck {
    private final JdbcTemplate db;private final TaskPlanDraftSchema schema;private final PermissionSchema json;private final TaskPlanDraftCandidatePolicy policy;
    private final TaskPlanService plans;private final PermissionMapper permissions;private final TemplateMapper templates;private final ResourceReadAuthority resources;private final SchemaCatalogue shared;
    public TaskPlanDraftHumanCheck(DataSource data,TaskPlanDraftSchema schema,PermissionSchema json,TaskPlanDraftCandidatePolicy policy,TaskPlanService plans,
            PermissionMapper permissions,TemplateMapper templates,ResourceReadAuthority resources)throws Exception{db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.json=json;this.policy=policy;this.plans=plans;this.permissions=permissions;this.templates=templates;this.resources=resources;shared=SchemaCatalogue.fromClasspath();}
    Checked candidate(TaskPlanDraftSavedCandidate.Verified saved)throws Exception{return check(saved,null);}
    Checked draft(TaskPlanDraftSavedCandidate.Verified saved,String draft)throws Exception{WireValues.uuid(draft);return check(saved,draft);}
    private Checked check(TaskPlanDraftSavedCandidate.Verified saved,String draft)throws Exception {
        saved.requireUnchanged();var target=target(saved,draft);var result=inspect(saved,target.content);var human=saved.human();var j=saved.job();String semantic=schema.hash(Map.of("authority",human.authoritySha256(),"candidate",saved.receiptSha256(),"target_kind",target.kind,"target_revision",target.revision,"target_sha256",target.hash,"coverage",json.tree(result.coverage),"missing",result.missing,"implementation","TASK_PLAN_DRAFT_SERVER_CHECK_V1"));
        String id=UUID.nameUUIDFromBytes((j.tenant()+"/"+j.run()+"/"+semantic).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var previous=db.queryForList("SELECT * FROM task_plan_draft_check WHERE tenant_id=? AND check_id=? LIMIT 2 FOR UPDATE",j.tenant(),id);if(previous.size()>1)bad();
        String audit=previous.isEmpty()?UUID.randomUUID().toString():previous.get(0).get("audit_id").toString(),event=previous.isEmpty()?UUID.randomUUID().toString():previous.get(0).get("event_id").toString();var at=previous.isEmpty()?now():time(previous.get(0).get("created_at"));
        var receipt=json.object().put("schema_version","1.0.0").put("tenant_id",j.tenant()).put("project_id",j.project()).put("task_id",j.task()).put("task_revision",j.taskRevision()).put("run_id",j.run()).put("actor_id",human.admission().actorId()).put("check_id",id)
                .put("candidate_receipt_sha256",saved.receiptSha256()).put("candidate_sha256",schema.hash(saved.candidate())).put("target_kind",target.kind).put("target_revision",target.revision).put("target_sha256",target.hash)
                .put("source_scope_sha256",schema.hash(AgentPublicationWitness.tree(human.sources()))).put("check_implementation","TASK_PLAN_DRAFT_SERVER_CHECK_V1").put("authority_sha256",human.authoritySha256()).put("coverage_sha256",schema.hash(json.tree(result.coverage)))
                .put("result",result.missing.isEmpty()?"COMPLETE":"INCOMPLETE").put("created_at",at.toInstant(ZoneOffset.UTC).toString());
        receipt.set("template",schema.read(j.admissionJson()).required("template"));receipt.set("profile",schema.read(j.admissionJson()).required("profile"));receipt.set("missing_item_ids",json.tree(result.missing));schema.validate("CheckReceipt",receipt);
        String raw=schema.canonical(receipt);var log=meta(saved,at);log.putAll(Map.of("audit_id",audit,"subject_id",human.admission().actorId(),"action","task.plan_draft.human_check","resource_ref",raw,"result","SUCCESS","request_id",id,"trace_id",j.run()));if(previous.isEmpty())insert("audit_event",log);
        if(previous.isEmpty())insert("outbox_event",Map.of("tenant_id",j.tenant(),"event_id",event,"aggregate_id",j.run(),"aggregate_revision",1,"event_type","task.plan_draft.human_check","payload",raw,"occurred_at",at));
        var row=meta(saved,at);row.putAll(Map.of("task_id",j.task(),"run_id",j.run(),"check_id",id,"candidate_receipt_sha256",saved.receiptSha256(),"receipt_json",raw,"receipt_sha256",schema.hash(receipt),"audit_id",audit,"event_id",event));if(previous.isEmpty())insert("task_plan_draft_check",row);else if(!receipt.equals(schema.read(previous.get(0).get("receipt_json").toString())))bad();
        var checked=new Checked(saved,draft,target,receipt,row,log,event,at);checked.requireUnchanged();return checked;
    }
    private Target target(TaskPlanDraftSavedCandidate.Verified saved,String draft)throws Exception {
        saved.requireUnchanged();if(draft==null)return new Target("PLAN_CANDIDATE",saved.artifactId(),schema.hash(saved.candidate()),saved.candidate());
        var human=saved.human();var view=plans.readFixed(human.admission(),human.fixedTask(),draft,true);
        if(!"DRAFT".equals(view.status())||!draft.equals(view.currentPlanRevision())||!saved.job().taskRevision().equals(view.taskRevision()))throw new TenantAccessException(ErrorCode.PLAN_STALE);
        return new Target("PLAN_DRAFT",draft,view.contentSha256(),json.tree(view.content()));
    }
    private Report inspect(TaskPlanDraftSavedCandidate.Verified saved,JsonNode content)throws Exception {
        saved.requireUnchanged();var human=saved.human();var a=human.admission();var template=human.template();var candidate=TypedSchemaBindings.bind(shared,ArtifactBindings.PLAN_CANDIDATE,content).read();
        var requested=new TreeSet<String>();candidate.sections().forEach(s->requested.addAll(s.completionRuleIds()));candidate.questions().forEach(q->requested.addAll(q.completionRuleIds()));
        for(String rule:requested){var ref=template.template().dependency_refs().stream().filter(r->r.version_id().equals(rule)).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));var basis=one(permissions.basis(rule));if(basis.ownerProjectId()!=null||!ref.content_sha256().equals(basis.sha256()))bad();resources.require(a,new PermissionProtocol.Scope(saved.job().tenant(),null,"RULE",rule),"rule.read","PLAN_MAINTENANCE");if(!"APPROVED".equals(one(templates.ruleState(Map.of("userId",a.actorId(),"upstreamId",rule)))))throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
        var result=policy.inspect(candidate,human.fixedTask().head().profileRevision(),template,requested);var missing=new TreeSet<String>();result.missing().forEach(m->missing.add(m.kind()+":"+m.targetId()));
        var materials=schema.read(human.fixedTask().input().missingRequirements());if(!materials.isArray())bad();for(var raw:materials)missing.add("SOURCE_REQUIREMENT_MISSING:"+TypedSchemaBindings.bind(shared,ResourceBindings.REQUIREMENT,raw).read().requirementId());
        for(var section:candidate.sections())if(section.ownerId()!=null&&!plans.creationOwnerEligible(a,saved.job().project(),section.ownerId()))missing.add("OWNER_INELIGIBLE:"+section.sectionId());
        saved.requireUnchanged();return new Report(result.coverage(),List.copyOf(missing));
    }
    private record Target(String kind,String revision,String hash,JsonNode content){}private record Report(List<TaskPlanDraftCandidatePolicy.Coverage> coverage,List<String> missing){}
    public final class Checked {
        private final TaskPlanDraftSavedCandidate.Verified saved;private final String draft;private final Target target;private final JsonNode receipt;private final Map<String,Object> row,log;private final String event;private final LocalDateTime at;
        private Checked(TaskPlanDraftSavedCandidate.Verified saved,String draft,Target target,JsonNode receipt,Map<String,Object> row,Map<String,Object> log,String event,LocalDateTime at){this.saved=saved;this.draft=draft;this.target=target;this.receipt=receipt.deepCopy();this.row=Map.copyOf(row);this.log=Map.copyOf(log);this.event=event;this.at=at;}
        void requireUnchanged()throws Exception {saved.requireUnchanged();if(!target.equals(target(saved,draft)))bad();var result=inspect(saved,target.content);if(!schema.hash(json.tree(result.coverage)).equals(receipt.required("coverage_sha256").asText())||!json.tree(result.missing).equals(receipt.required("missing_item_ids")))bad();var j=saved.job();same(row,one(db.queryForList("SELECT * FROM task_plan_draft_check WHERE tenant_id=? AND check_id=? LIMIT 2",j.tenant(),receipt.required("check_id").asText())));same(log,one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2",j.tenant(),row.get("audit_id"))));same(Map.of("tenant_id",j.tenant(),"event_id",event,"aggregate_id",j.run(),"aggregate_revision",1,"event_type","task.plan_draft.human_check","payload",schema.canonical(receipt),"occurred_at",at),one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2",j.tenant(),event)));}
        void requireComplete()throws Exception{requireUnchanged();if(!"COMPLETE".equals(receipt.required("result").asText()))throw new TenantAccessException(ErrorCode.PLAN_UNCONFIRMED);}
        JsonNode receipt(){return receipt.deepCopy();}String receiptSha256()throws Exception{return schema.hash(receipt);}TaskPlanDraftSavedCandidate.Verified candidate(){return saved;}String targetRevision(){return target.revision;}String targetSha256(){return target.hash;}
    }
    private Map<String,Object> meta(TaskPlanDraftSavedCandidate.Verified saved,LocalDateTime at){var j=saved.job();var m=new LinkedHashMap<String,Object>();m.put("tenant_id",j.tenant());m.put("project_id",j.project());m.put("created_by",saved.human().admission().actorId());m.put("created_at",at);m.put("classification",j.classification());m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",schema.manifest());return m;}
    private void insert(String table,Map<String,Object> m){var keys=new ArrayList<>(m.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(m::get).toArray())!=1)bad();}
    private void same(Map<String,Object> expected,Map<String,Object> actual)throws Exception{for(var e:expected.entrySet()){Object v=actual.get(e.getKey());if(v instanceof java.sql.Timestamp t)v=t.toLocalDateTime();if(e.getValue() instanceof Number&&v instanceof Number){if(!e.getValue().toString().equals(v.toString()))bad();}else if(Set.of("payload","resource_ref").contains(e.getKey())&&e.getValue() instanceof String l&&v instanceof String r){if(!schema.read(l).equals(schema.read(r)))bad();}else if(!Objects.equals(e.getValue(),v))bad();}}
    private static LocalDateTime time(Object value){if(value instanceof LocalDateTime t)return t;if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
