package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Produces original typed rule-check receipts from genuine frozen facts and a recorded candidate.
 * SATISFIED means the declared checks passed, never independent medical/domain truth. */
public final class TaskResearchCompletionEvaluation {
    static final String ACTION="task.research.rule.checked",KIND="TASK_RESEARCH_RULE_CHECK";
    static final String MANIFEST=TaskResearchCompletionSchema.MANIFEST;
    private final JdbcTemplate db;private final PermissionSchema json;private final RuleExpression engine;
    private final TaskResearchRuleInputs inputs;private final TaskResearchWaitingService waiting;
    public TaskResearchCompletionEvaluation(DataSource data,PermissionSchema json,RuleExpression engine,TaskResearchRuleInputs inputs,TaskResearchWaitingService waiting){
        this(new JdbcTemplate(data),json,engine,inputs,waiting);
    }
    TaskResearchCompletionEvaluation(JdbcTemplate db,PermissionSchema json,RuleExpression engine,TaskResearchRuleInputs inputs,TaskResearchWaitingService waiting){
        this.db=Objects.requireNonNull(db);db.setQueryTimeout(5);this.json=Objects.requireNonNull(json);this.engine=Objects.requireNonNull(engine);this.inputs=Objects.requireNonNull(inputs);this.waiting=Objects.requireNonNull(waiting);
    }
    public Evaluated evaluate(TaskResearchExecutionService.Candidate candidate)throws Exception {
        var state=inspect(candidate,true);String waitingId=null;
        if(!state.reasons().isEmpty())waitingId=waiting.create(new WaitingCause(candidate,state.inputs(),state.reasons(),state.checkIds(),state.receiptPins()));
        candidate.requireUnchanged();state.inputs().requireUnchanged();return new Evaluated(candidate,state,waitingId);
    }
    private record State(TaskResearchRuleInputs.Verified inputs,List<String> reasons,List<String> checkIds,JsonNode receiptPins,
            ArtifactProtocol.QuestionStatus status,String fingerprint) { }
    private State inspect(TaskResearchExecutionService.Candidate candidate,boolean write)throws Exception {
        candidate.requireUnchanged();candidate.sourceReceipts().requireUnchanged();var fixed=inputs.require(candidate.session());
        var projection=candidate.projection();if(!hash(projection).equals(candidate.candidateSha256()))bad();
        var reasons=new TreeSet<>(TaskResearchCompletionChecks.blockingCoverage(projection,fixed.requirementIds()));
        var ids=new ArrayList<String>();var receipts=json.object().arrayNode();var rules=fixed.checks();var ruleIds=rules.stream().map(TaskResearchRuleInputs.Check::ruleId).toList();
        if(rules.isEmpty())reasons.add("APPROVED_COMPLETION_RULE_REQUIRED");
        for(var check:rules){var result=TaskResearchCompletionChecks.evaluate(engine,check.ruleId(),ruleIds,check.rule(),fixed.attributes());
            if(result.outcome()==TaskResearchCompletionChecks.Outcome.UNKNOWN)reasons.add("FROZEN_PROFILE_INPUT_UNKNOWN");
            if(result.outcome()==TaskResearchCompletionChecks.Outcome.NOT_SATISFIED)reasons.add("DECLARED_COMPLETION_CHECK_NOT_SATISFIED");
            var receipt=check(candidate,fixed,check,result,write);ids.add(receipt.required("check_id").textValue());receipts.add(receipt);
        }
        if(!reasons.isEmpty()&&fixed.ownerId()==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var status=reasons.contains("UNRESOLVED_EVIDENCE_CONFLICT")?ArtifactProtocol.QuestionStatus.CONFLICT:
            reasons.isEmpty()?ArtifactProtocol.QuestionStatus.VERIFIED:ArtifactProtocol.QuestionStatus.INSUFFICIENT;
        var identity=json.object().put("candidate_sha256",candidate.candidateSha256()).put("status",status.name());
        identity.set("reasons",json.tree(reasons));identity.set("receipts",receipts);identity.set("inputs",fixed.witness());
        candidate.requireUnchanged();fixed.requireUnchanged();return new State(fixed,List.copyOf(reasons),List.copyOf(ids),receipts,status,hash(identity));
    }
    private JsonNode check(TaskResearchExecutionService.Candidate candidate,TaskResearchRuleInputs.Verified fixed,
            TaskResearchRuleInputs.Check rule,TaskResearchCompletionChecks.Result result,boolean write)throws Exception {
        var session=candidate.session();var a=session.admission();var input=session.input();
        String checkId=UUID.nameUUIDFromBytes((KIND+"|"+input.tenantId()+"|"+session.sessionId()+"|"+candidate.candidateSha256()+"|"+rule.ruleId()).getBytes(StandardCharsets.UTF_8)).toString();
        var value=json.object().put("schema_version","1.0.0").put("kind",KIND).put("check_id",checkId)
            .put("tenant_id",input.tenantId()).put("project_id",input.projectId()).put("run_id",input.runId()).put("step_id",input.stepId())
            .put("action_id",session.actionId()).put("lease_generation",session.generation()).put("question_id",input.questionId())
            .put("session_id",session.sessionId()).put("input_artifact_id",input.inputId()).put("input_sha256",input.inputSha256())
            .put("input_receipt_sha256",input.receiptSha256()).put("candidate_sha256",candidate.candidateSha256())
            .put("rule_version",rule.ruleId()).put("rule_sha256",rule.ruleSha256()).put("engine_revision",rule.rule().engine_revision())
            .put("profile_revision",fixed.profileId()).put("profile_sha256",fixed.profileSha256()).put("outcome",result.outcome().name());
        value.set("unknown_input_keys",json.tree(result.missingInputKeys()));value.set("attributes",json.tree(fixed.attributes()));
        value.set("rule_authorizations",rule.authorizations());value.set("source_read_receipts",candidate.sourceReceipts().canonicalPins());value.set("invocations",candidate.invocationPins());
        TaskResearchCompletionSchema.require(value);
        String canonical=json.canonical(value),receiptHash=hash(value),eventId=UUID.nameUUIDFromBytes((checkId+"|outbox").getBytes(StandardCharsets.UTF_8)).toString();
        var rows=db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? FOR UPDATE",input.tenantId(),checkId);
        if(rows.isEmpty()){
            if(!write)bad();var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
            String classification=one(db.query("SELECT classification FROM task_run WHERE tenant_id=? AND run_id=?",(r,n)->r.getString(1),input.tenantId(),input.runId()));
            changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',NULL,?,?,'1.0.0',?,?,?,?)",input.tenantId(),input.projectId(),checkId,a.actorId(),ACTION,canonical,session.actionId(),input.runId(),MANIFEST,a.actorId(),now,classification));
            changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",input.tenantId(),eventId,checkId,ACTION,canonical,now));
            rows=db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? FOR UPDATE",input.tenantId(),checkId);
        }
        var row=one(rows);for(var pin:Map.ofEntries(Map.entry("tenant_id",input.tenantId()),Map.entry("project_id",input.projectId()),Map.entry("audit_id",checkId),Map.entry("subject_id",a.actorId()),Map.entry("created_by",a.actorId()),Map.entry("action",ACTION),Map.entry("resource_ref",canonical),Map.entry("result","SUCCESS"),Map.entry("request_id",session.actionId()),Map.entry("trace_id",input.runId()),Map.entry("schema_version","1.0.0"),Map.entry("schema_manifest_sha256",MANIFEST)).entrySet())same(pin.getValue(),row.get(pin.getKey()));
        if(row.get("reason_code")!=null)bad();String classification=one(db.query("SELECT classification FROM task_run WHERE tenant_id=? AND run_id=?",(r,n)->r.getString(1),input.tenantId(),input.runId()));same(classification,row.get("classification"));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? FOR UPDATE",input.tenantId(),eventId));
        same(input.tenantId(),event.get("tenant_id"));same(checkId,event.get("aggregate_id"));same(ACTION,event.get("event_type"));same(canonical,event.get("payload"));same(row.get("created_at"),event.get("occurred_at"));TaskNodeInputRegistration.sameColumns(Map.of("aggregate_revision",1),event);same(eventId,event.get("event_id"));
        return json.object().put("check_id",checkId).put("receipt_sha256",receiptHash).put("rule_version",rule.ruleId()).put("outcome",result.outcome().name());
    }
    public final class Evaluated {
        private final TaskResearchExecutionService.Candidate candidate;private final State state;private final String waitingId;
        private Evaluated(TaskResearchExecutionService.Candidate candidate,State state,String waitingId){this.candidate=candidate;this.state=state;this.waitingId=waitingId;}
        public void requireUnchanged()throws Exception{candidate.requireUnchanged();if(!state.fingerprint().equals(inspect(candidate,false).fingerprint()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);if(waitingId!=null)waiting.requireOriginal(new WaitingCause(candidate,state.inputs(),state.reasons(),state.checkIds(),state.receiptPins()),waitingId);}
        public String candidateSha256(){candidate.session().admission();return candidate.candidateSha256();}
        public List<String> completionCheckIds(){candidate.session().admission();return state.checkIds();}
        public ArtifactProtocol.QuestionStatus questionStatus(){candidate.session().admission();return state.status();}
        public List<String> missingRequirementIds(){candidate.session().admission();var result=new TreeSet<String>();for(var values:List.of(candidate.projection().required("missing_requirement_ids"),candidate.projection().required("question_coverage").get(0).required("missing_requirement_ids")))for(var v:values)result.add(v.textValue());return List.copyOf(result);}
        public String waitingItemId(){candidate.session().admission();return waitingId;}
        public String outcome(){candidate.session().admission();return waitingId==null?"SUCCEEDED":"WAITING_INPUT";}
    }
    /** Private factory, consumed by the durable waiting writer, never constructed from HTTP JSON. */
    public final class WaitingCause {
        private final TaskResearchExecutionService.Candidate candidate;private final TaskResearchRuleInputs.Verified fixed;
        private final List<String> reasons,checks;private final JsonNode receipts;
        private WaitingCause(TaskResearchExecutionService.Candidate candidate,TaskResearchRuleInputs.Verified fixed,List<String> reasons,List<String> checks,JsonNode receipts){this.candidate=candidate;this.fixed=fixed;this.reasons=List.copyOf(reasons);this.checks=List.copyOf(checks);this.receipts=receipts.deepCopy();}
        public void requireUnchanged()throws Exception{candidate.requireUnchanged();fixed.requireUnchanged();if(reasons.isEmpty()||fixed.ownerId()==null)bad();}
        TaskResearchExecutionService.Candidate candidate(){return candidate;}TaskResearchRuleInputs.Verified inputs(){return fixed;}
        List<String> reasons(){return reasons;}List<String> checks(){return checks;}JsonNode receiptPins(){return receipts.deepCopy();}
    }
    private static String hash(Object value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
    private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}
    private static void same(Object expected,Object actual){if(!Objects.equals(expected,actual))bad();}private static void changed(int rows){if(rows!=1)bad();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
