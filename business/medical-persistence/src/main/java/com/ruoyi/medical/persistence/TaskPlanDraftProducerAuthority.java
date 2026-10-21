package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.time.*;
import java.util.*;

/** N01's fixed business producer. Publication and current plan.write authorize this bounded
 * output, not arbitrary artifact writes. No N02 plan/producer decision is manufactured. */
public final class TaskPlanDraftProducerAuthority {
    private final FormalCapabilityIdentityAuthority capabilities;private final PermissionSchema json;
    private final org.springframework.jdbc.core.JdbcTemplate db;private final TaskPlanDraftJobs jobs;
    private final TaskPlanDraftSchema schema;private final PermissionMapper clock;private final SchemaCatalogue shared;
    public TaskPlanDraftProducerAuthority(FormalCapabilityIdentityAuthority capabilities,PermissionSchema json,TaskPlanDraftSchema schema,PermissionMapper clock,javax.sql.DataSource data,TaskPlanDraftJobs jobs)throws Exception {
        this.capabilities=capabilities;this.json=json;this.schema=schema;this.clock=clock;this.db=new org.springframework.jdbc.core.JdbcTemplate(data);db.setQueryTimeout(5);this.jobs=jobs;shared=SchemaCatalogue.fromClasspath();
    }
    Verified require(TaskPlanDraftJobAuthority.Verified job)throws Exception {
        job.requireUnchanged();var current=job.current();var j=job.job();
        var expected=new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(j.tenant(),j.project(),"TASK",j.task()),"plan.write","PLAN_MAINTENANCE");
        if(!"N01".equals(current.node().nodeKind())||!job.nodePermissions().contains(expected))deny();
        var role=capabilities.require(job.admission(),current.node().roleVersion(),"ROLE",j.project(),TaskPlanDraftAdmission.PURPOSE);
        var definition=role.canonicalDefinition().required("request");requireScope(definition,expected);
        var output=definition.required("output_schema_ref");
        if(!shared.schemaDigest("artifacts").equals(output.required("schema_sha256").asText())
                ||!Set.of(shared.schemaRef("artifacts",null),shared.schemaRef("artifacts","PlanCandidate")).contains(output.required("schema_ref").asText()))deny();
        var matches=new ArrayList<FormalCapabilityIdentityAuthority.VerifiedCapability>();
        for(var pin:current.releaseManifest().toolVersions()) {
            var tool=capabilities.require(job.admission(),new PermissionProtocol.Basis(pin.versionId(),pin.contentSha256()),"TOOL",j.project(),TaskPlanDraftAdmission.PURPOSE);
            var request=tool.canonicalDefinition().required("request");var input=request.required("input_schema_ref");
            if(shared.schemaRef("tool-inputs","PlanArgs").equals(input.required("schema_ref").asText())) {
                if(!shared.schemaDigest("tool-inputs").equals(input.required("schema_sha256").asText()))deny();
                var out=request.required("output_schema_ref");if(!shared.schemaRef("tool-success",null).equals(out.required("schema_ref").asText())||!shared.schemaDigest("tool-success").equals(out.required("schema_sha256").asText()))deny();
                requireScope(request,expected);matches.add(tool);
            }
        }
        if(matches.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var tool=matches.get(0);Instant until=current.policyDefinition().validUntil()==null?null:Instant.parse(current.policyDefinition().validUntil());
        for(String field:List.of("plan_grant","run_grant")) {var grant=current.evidence().required(field);var end=grant.required("valid_to");if(!end.isNull())until=min(until,LocalDateTime.parse(end.asText()).toInstant(ZoneOffset.UTC));}
        for(var source:current.sources())if(source.rights().validTo()!=null)until=min(until,source.rights().validTo().toInstant(ZoneOffset.UTC));
        if(until!=null&&!clock.databaseTime().toInstant(ZoneOffset.UTC).isBefore(until))throw new TenantAccessException(ErrorCode.ACTION_STALE);
        var pins=json.object().put("schema_version","1.0.0").put("kind","N01_FIXED_PLAN_PRODUCER").put("node_kind","N01").put("role","Planner")
                .put("tenant_id",j.tenant()).put("project_id",j.project()).put("task_id",j.task()).put("run_id",j.run()).put("input_sha256",j.inputHash())
                .put("authority_sha256",current.digest()).put("output_kind","PLAN_CANDIDATE").put("output_schema_ref",shared.schemaRef("artifacts","PlanCandidate"))
                .put("output_schema_sha256",shared.schemaDigest("artifacts")).put("maximum_candidate_bytes",1048576).put("maximum_candidates",1).put("maximum_artifacts",5).put("maximum_model_calls",1)
                .put("maximum_exact_input_bytes",TaskPlanDraftInvocationSchema.MAXIMUM_BYTES).put("maximum_model_result_bytes",TaskPlanDraftInvocationSchema.MAXIMUM_BYTES);
        if(until==null)pins.putNull("valid_until");else pins.put("valid_until",until.toString());
        pins.set("role_version",json.tree(role.version()));pins.set("at10_version",json.tree(tool.version()));pins.set("permission",json.tree(expected));
        pins.set("retention",current.retention());pins.set("source_closure",current.evidence().required("sources"));
        role.requireUnchanged();tool.requireUnchanged();job.requireUnchanged();return new Verified(job,pins);
    }
    /** Only original authenticated provider facts. This grants no dispatch, source read or candidate production.
     * Expiry is checked at the real original dispatch, not converted into permission to discard its receipt. */
    void requireOriginalOutput(FormalInvocationOutcomeAuthority.VerifiedOutcome outcome,JsonNode expected,String kind,int size)throws Exception {
        outcome.requireActive();if(!Set.of("MODEL_RESULT","PROVIDER_CAPTURE").contains(kind)||size<1||size>TaskPlanDraftInvocationSchema.MAXIMUM_BYTES)deny();
        var job=jobs.byRun(outcome.tenantId(),outcome.binding().required("run_id").asText());
        if(!job.actor().equals(outcome.actorId())||!job.project().equals(outcome.projectId()))deny();
        var rows=db.queryForList("SELECT authorization_snapshot,authorization_sha256 FROM formal_invocation_binding WHERE tenant_id=? AND invocation_id=? LIMIT 2",outcome.tenantId(),outcome.invocationId());
        if(rows.size()!=1)deny();var auth=schema.read(rows.get(0).get("authorization_snapshot").toString());
        if(!schema.hash(auth).equals(rows.get(0).get("authorization_sha256")))deny();
        var commitment=auth.required("plan_draft_output_commitment");
        if(!expected.equals(commitment.required("producer"))||!outcome.invocationId().equals(commitment.required("invocation_id").asText()))deny();
        var original=schema.read(job.authorityJson());
        for(var entry:Map.of("tenant_id",job.tenant(),"project_id",job.project(),"task_id",job.task(),"run_id",job.run(),"input_sha256",job.inputHash(),"authority_sha256",schema.hash(original)).entrySet())if(!entry.getValue().equals(expected.required(entry.getKey()).asText()))deny();
        if(!"N01_FIXED_PLAN_PRODUCER".equals(expected.required("kind").asText())||!original.required("retention").equals(expected.required("retention"))||!original.required("sources").equals(expected.required("source_closure")))deny();
        var dispatched=db.query("SELECT created_at FROM formal_invocation_attempt WHERE tenant_id=? AND invocation_id=? AND attempt_id=? AND dispatch_generation=1 LIMIT 2",(r,n)->r.getTimestamp(1).toInstant(),outcome.tenantId(),outcome.invocationId(),outcome.attemptId());
        if(dispatched.size()!=1)deny();var cutoff=expected.required("valid_until");if(!cutoff.isNull()&&!dispatched.get(0).isBefore(Instant.parse(cutoff.asText())))deny();
        outcome.requireActive();
    }
    public final class Verified {
        private final TaskPlanDraftJobAuthority.Verified job;private final JsonNode pins;
        private Verified(TaskPlanDraftJobAuthority.Verified job,JsonNode pins){this.job=job;this.pins=pins.deepCopy();}
        public void requireUnchanged()throws Exception {if(!pins.equals(require(job).pins))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        public JsonNode canonicalPins(){return pins.deepCopy();}
        public void requireBytes(String kind,int size)throws Exception {requireUnchanged();int maximum=switch(kind){case "PLAN_CANDIDATE","PLAN_DRAFT_INPUT"->1048576;case "EXACT_INPUT","MODEL_RESULT","PROVIDER_CAPTURE"->TaskPlanDraftInvocationSchema.MAXIMUM_BYTES;default->throw new TenantAccessException(ErrorCode.ACCESS_DENIED);};if(size<1||size>maximum)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
    }
    private void requireScope(JsonNode request,RunTokenAuthoritySchema.Permission expected)throws Exception {boolean found=false;for(var p:request.required("tool_scope").required("permissions"))if(FormalTaskNodeAuthority.sameEvidence(json.tree(expected),p))found=true;if(!found)deny();}
    private static Instant min(Instant a,Instant b){return a==null||b.isBefore(a)?b:a;}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
