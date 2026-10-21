package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Business evaluation is separate from SDK/process success. N01 compares an actual settled
 * proposal with the immutable planning input, and preserves every outstanding human blocker. */
public final class FormalEvaluationNodeOutcomes {
    private final JdbcTemplate db;private final PermissionSchema json;private final TaskPlanPolicy plans;
    private final FormalCapabilityIdentityAuthority capabilities;private final FormalEvaluationInvocationService invocations;
    private final FormalEvaluationRuntimeSchema schema;private final TaskPlanService owners;private final FormalEvaluationToolReceipts tools;
    private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public FormalEvaluationNodeOutcomes(DataSource data,PermissionSchema json,TaskPlanPolicy plans,FormalCapabilityIdentityAuthority capabilities,FormalEvaluationInvocationService invocations,FormalEvaluationRuntimeSchema schema,TaskPlanService owners,FormalEvaluationToolReceipts tools){
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.plans=plans;this.capabilities=capabilities;this.invocations=invocations;this.schema=schema;this.owners=owners;this.tools=tools;
    }
    public Checked check(FormalEvaluationNodeRuntime.Session session,FormalEvaluationSupervisor.Closed closed,
            FormalEvaluationRuntimeObjects.Registered exported,List<FormalEvaluationInvocationService.Terminal> terminals)throws Exception {
        session.requireCurrent();closed.requireUnchanged();closed.requireSuccessfulExit();exported.requireUnchanged();same(session.sessionId(),closed.sessionId());same(session.binding(),closed.binding());
        same(schema.ref("SESSION_EXPORT"),exported.artifact().schemaRef());same(org.apache.commons.codec.digest.DigestUtils.sha256Hex(closed.exportBytes()),exported.artifact().sha256());
        if(terminals==null||terminals.isEmpty()||terminals.size()!=1)bad();var l=session.lease();var j=l.job();var actual=db.query("SELECT invocation_id FROM invocation WHERE tenant_id=? AND run_id=? AND step_id=? ORDER BY invocation_id LIMIT 1001",(r,n)->r.getString(1),j.tenantId(),j.runId(),l.stepId());
        var seen=new TreeSet<String>();for(var terminal:terminals){terminal.requireCurrent(session);same(session.sessionId(),terminal.sessionId());for(var call:terminal.invocations())if(!seen.add(call.invocationId()))bad();}same(new TreeSet<>(actual),seen);if(actual.size()!=seen.size())bad();
        var terminal=terminals.get(terminals.size()-1);var output=parseText(terminal.result());
        var role=capabilities.require(l.admission(),l.node().node().role(),"ROLE",j.projectId(),"EVALUATION_RESULTS");var outputSchema=role.canonicalDefinition().required("request").required("output_schema_ref");
        String ref=outputSchema.required("schema_ref").asText(),digest=outputSchema.required("schema_sha256").asText();SchemaCatalogue.fromClasspath().bindPublished(ref,digest,json.canonical(output).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String state;
        switch(l.node().node().nodeKind()){
            case "N01"->state=planner(l.admission(),j.projectId(),l.input().evidence(),output,ref);
            case "N02","N03","N04","N05","N06","N07"->{var checked=new FormalEvaluationBusinessChecks(json).check(l.node().node().nodeKind(),session.sessionId(),l.actionId(),l.input().evidence(),output,ref,digest,tools.businessOriginals(l.admission(),j.runId(),l.stepId(),session.sessionId()));output=checked.output();state=checked.state();ref=FormalEvaluationBusinessSchema.refFor(output);digest=FormalEvaluationBusinessSchema.digest(ref);}
            default->throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        role.requireUnchanged();var result=json.object().put("schema_version","1.0.0").put("execution_kind","SYSTEM_JOB").put("job_type","EVALUATION_VERIFICATION")
            .put("node_kind",l.node().node().nodeKind()).put("session_id",session.sessionId()).put("outcome",state).put("output_schema_ref",ref)
            .put("output_sha256",org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(output))).put("checker","EVALUATION_ORIGINAL_NODE_OUTCOME_V1");
        result.set("binding",session.binding());result.set("session_export",json.tree(exported.artifact()));result.set("model_result",json.tree(terminal.invocation().modelResult()));result.set("provider_capture",json.tree(terminal.invocation().providerCapture()));result.set("output",output);
        result.set("invocation_ids",json.tree(terminal.invocations().stream().map(FormalEvaluationExecutionProtocol.Invocation::invocationId).toList()));result.putArray("check_ids");schema.read("STEP_RESULT",json.canonical(result).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new Checked(this,session,closed,exported,terminals,result,digest);
    }
    private String planner(EnterpriseAccess.Admission admission,String project,JsonNode evidence,JsonNode output,String ref)throws Exception {
        if(!ref.endsWith("#/$defs/PlanCandidate"))bad();
        // A document-only prompt has no approved template/profile against which to check a plan.
        // It may perform a model experiment, but cannot manufacture a professional N01 result.
        if(!evidence.has("planning_original"))throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var planning=evidence.required("planning_original").required("planning");var original=TypedSchemaBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.PLAN_CANDIDATE,planning.required("plan_candidate")).read();
        var proposed=TypedSchemaBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.PLAN_CANDIDATE,output).read();
        same(original.templateVersion(),proposed.templateVersion());same(original.profileRevision(),proposed.profileRevision());
        var conditions=new HashSet<String>();for(var c:planning.required("conditions"))conditions.add(c.required("target_id").asText());
        var completionRules=new HashSet<String>();original.sections().forEach(s->completionRules.addAll(s.completionRuleIds()));original.questions().forEach(q->completionRules.addAll(q.completionRuleIds()));
        var validation=plans.inspect(output,original.templateVersion(),original.profileRevision(),original.requirementNodeIds(),conditions,completionRules);
        var expected=new HashSet<>(original.requirementNodeIds());if(!expected.equals(new HashSet<>(proposed.requirementNodeIds()))||expected.size()!=proposed.requirementNodeIds().size())bad();
        for(String pending:original.pendingConditionIds())if(!proposed.pendingConditionIds().contains(pending))bad();
        for(var section:proposed.sections())if(section.ownerId()!=null&&!(admission.evaluationWorker()?owners.creationOwnerEligibleEvaluation(admission,project,section.ownerId()):owners.creationOwnerEligible(admission,project,section.ownerId())))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        // Recommendations cannot erase an original unknown condition, missing approved rule,
        // required method review or waiting decision. No automatic human approval is created.
        return planning.required("blockers").isEmpty()&&validation.unassignedSectionIds().isEmpty()&&validation.incompleteRuleTargets().isEmpty()&&proposed.pendingConditionIds().isEmpty()?"SUCCEEDED":"WAITING_INPUT";
    }
    public JsonNode requireOriginalOutcome(EnterpriseAccess.Admission a,FormalEvaluationExecutionProtocol.Node node,String project,JsonNode compiler,JsonNode actualModelResult,JsonNode storedResult)throws Exception {
        FormalEvaluationExecutionAdmission.current(a);var projection=parseText(actualModelResult);
        var role=capabilities.require(a,node.role(),"ROLE",project,"EVALUATION_RESULTS");var bound=role.canonicalDefinition().required("request").required("output_schema_ref");
        String ref=bound.required("schema_ref").asText(),digest=bound.required("schema_sha256").asText();
        SchemaCatalogue.fromClasspath().bindPublished(ref,digest,json.canonical(projection).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode output;String state;
        if("N01".equals(node.nodeKind())){output=projection;state=planner(a,project,compiler,projection,ref);}
        else {var binding=storedResult.required("binding");var checked=new FormalEvaluationBusinessChecks(json).check(node.nodeKind(),storedResult.required("session_id").asText(),binding.required("action_id").asText(),compiler,projection,ref,digest,tools.businessOriginals(a,binding.required("run_id").asText(),binding.required("step_id").asText(),storedResult.required("session_id").asText()));output=checked.output();state=checked.state();ref=FormalEvaluationBusinessSchema.refFor(output);}
        same(output,storedResult.required("output"));same(ref,storedResult.required("output_schema_ref").asText());same(org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(output)),storedResult.required("output_sha256").asText());same(state,storedResult.required("outcome").asText());role.requireUnchanged();return output;
    }

    private JsonNode parseText(JsonNode result)throws Exception {
        if(!"stop".equals(result.required("reason").required("kind").asText()))bad();StringBuilder body=new StringBuilder();for(var block:result.required("blocks")){if(!"text".equals(block.required("type").asText()))bad();body.append(block.required("text").asText());if(body.length()>2*1024*1024)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
        if(body.isEmpty())bad();var output=parser.readTree(body.toString());if(!output.isObject()||output.isEmpty())bad();return output;
    }
    public static final class Checked {
        private final FormalEvaluationNodeOutcomes owner;private final FormalEvaluationNodeRuntime.Session session;private final FormalEvaluationSupervisor.Closed closed;private final FormalEvaluationRuntimeObjects.Registered exported;private final List<FormalEvaluationInvocationService.Terminal> terminals;private final JsonNode document;private final String outputSchemaSha256;
        private Checked(FormalEvaluationNodeOutcomes owner,FormalEvaluationNodeRuntime.Session session,FormalEvaluationSupervisor.Closed closed,FormalEvaluationRuntimeObjects.Registered exported,List<FormalEvaluationInvocationService.Terminal> terminals,JsonNode document,String outputSchemaSha256){this.owner=owner;this.session=session;this.closed=closed;this.exported=exported;this.terminals=List.copyOf(terminals);this.document=document.deepCopy();this.outputSchemaSha256=outputSchemaSha256;}
        public void requireUnchanged()throws Exception{same(document,owner.check(session,closed,exported,terminals).document);}
        public FormalEvaluationNodeRuntime.Session session(){session.lease().admission();return session;}public FormalEvaluationSupervisor.Closed closed(){session.lease().admission();return closed;}
        public JsonNode outputDocument(){session.lease().admission();return document.required("output").deepCopy();}
        public String outputSchemaRef(){session.lease().admission();return document.required("output_schema_ref").asText();}
        public String outputSchemaSha256(){session.lease().admission();return outputSchemaSha256;}
        public JsonNode document(){session.lease().admission();return document.deepCopy();}
        public List<FormalEvaluationExecutionProtocol.Artifact> roots(){session.lease().admission();var roots=new ArrayList<FormalEvaluationExecutionProtocol.Artifact>();roots.add(exported.artifact());for(var terminal:terminals)for(var call:terminal.invocations()){roots.add(call.modelResult());roots.add(call.providerCapture());}return List.copyOf(roots);}
        public List<FormalEvaluationExecutionProtocol.Invocation> invocations(){session.lease().admission();return terminals.get(0).invocations();}
    }
    private static void same(Object x,Object y){if(!Objects.equals(x,y))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
