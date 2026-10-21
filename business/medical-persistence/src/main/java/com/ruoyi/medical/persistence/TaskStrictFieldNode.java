package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Deterministic host AT-05. Never launches a model or manufactures a C07 tool call. */
public final class TaskStrictFieldNode {
    public static final String ACTION="task.field.check",PURPOSE="STRICT_FIELD_COMPARISON";
    private final TaskStrictFieldOutputProducer outputs;private final org.springframework.jdbc.core.JdbcTemplate db;
    private final FormalWorkerAccess workers;private final TaskStrictFieldSchema schema;private final StrictFieldComparisonSchema comparison;
    private final StrictComparisonRunScope scopes;private final StrictComparisonPolicyService policies;
    private final TaskStrictFieldSourceReader sources;private final TaskStrictFieldResultRegistration results;
    private final StrictFieldComparisonEngine.Limits limits;
    public TaskStrictFieldNode(javax.sql.DataSource data,FormalWorkerAccess workers,TaskStrictFieldSchema schema,StrictFieldComparisonSchema comparison,
            StrictComparisonRunScope scopes,StrictComparisonPolicyService policies,TaskStrictFieldSourceReader sources,
            TaskStrictFieldResultRegistration results,StrictFieldComparisonEngine.Limits limits,TaskStrictFieldOutputProducer outputs){this.outputs=outputs;this.db=new org.springframework.jdbc.core.JdbcTemplate(data);db.setQueryTimeout(5);this.workers=workers;this.schema=schema;this.comparison=comparison;
        this.scopes=scopes;this.policies=policies;this.sources=sources;this.results=results;this.limits=limits;}
    public JsonNode execute(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,JsonNode arguments)throws Exception {
        outside();SchemaCatalogue.fromClasspath().validate("tool-inputs",arguments,"FieldArgs");
        Handle handle=new Handle(http,token,action,arguments.deepCopy());
        var input=execute(handle,c->c.input().input());
        var original=execute(handle,c->results.recover(c));if(original!=null)return outputs.produce(this,handle,original);
        // Read the actual versioned target, not only its recorded JSON or a predicted object digest.
        sources.readTarget(this,handle);
        var reads=new ArrayList<TaskStrictFieldSourceReader.Read>();
        for(var entry:schema.target(input).entries())reads.add(sources.read(this,handle,entry.entryId()));
        var registered=execute(handle,c->{
            var target=schema.target(c.input().input());var policyScope=scopes.require(c.input().production());
            var definitions=new LinkedHashMap<PermissionProtocol.Basis,TaskStrictFieldCheck.Policy>();
            var requests=new ArrayList<StrictFieldComparisonProtocol.EntryRequest>();
            for(var selection:c.input().input().policies()) {
                var approval=policies.requireApprovedFixedRun(policyScope,selection.policy().version_id(),selection.policy().content_sha256());
                definitions.putIfAbsent(selection.policy(),new TaskStrictFieldCheck.Policy(approval.revisionId(),approval.digest(),approval.definition()));
                var entry=target.entries().stream().filter(e->e.entryId().equals(selection.entryId())).findFirst().orElseThrow();
                var read=reads.stream().filter(r->r.entryId().equals(selection.entryId())).findFirst().orElseThrow();sources.requireCurrent(c,read);
                requests.add(new StrictFieldComparisonProtocol.EntryRequest(entry.entryId(),comparison.hash(entry),
                    new StrictFieldComparisonProtocol.Rule(approval.revisionId(),approval.revisionId(),approval.digest(),approval.definition().operator().name()),
                    read.source(),read.observation(approval.definition())));approval.requireUnchanged();
            }
            var request=new StrictFieldComparisonProtocol.Request(target.schemaVersion(),comparison.hash(target),target.identity(),requests);
            var evaluated=new TaskStrictFieldCheck(comparison,StrictComparisonPolicyService.implementation()).evaluate(target,request,List.copyOf(definitions.values()),limits);
            policyScope.requireUnchanged();c.requireUnchanged();return results.register(c,request,evaluated,reads,sources);
        });
        return outputs.produce(this,handle,registered);
    }
    <T>T execute(Handle handle,Operation<T> operation)throws Exception {
        if(handle==null||handle.owner!=this)deny();outside();
        return workers.executeAction(handle.http,handle.token,handle.action,(a,admitted)->ThrowingSupplier.of(()->{
            var input=admitted.currentStrictFieldInput();var current=new Current(a,admitted,input,handle.action,handle.arguments);
            current.requireUnchanged();T value=operation.apply(current);current.requireUnchanged();return value;
        }).get());
    }
    Current currentForCommit(FormalTaskNodeAuthority.Verified node,String resultId)throws Exception {
        node.requireUnchanged();WireValues.uuid(resultId);var input=node.currentStrictFieldInput();var a=node.controlAdmission();
        var rows=db.queryForList("SELECT result_json,result_sha256 FROM task_strict_field_result WHERE tenant_id=? AND run_id=? AND step_id=? AND result_id=? LIMIT 2 FOR UPDATE",input.input().tenantId(),input.input().runId(),input.input().stepId(),resultId);
        if(rows.size()!=1)deny();var body=schema.read(rows.get(0).get("result_json").toString());if(!schema.hash(body).equals(rows.get(0).get("result_sha256")))deny();
        var identity=body.required("identity");var action=schema.action(identity.required("action"));var arguments=identity.required("arguments").deepCopy();
        var current=new Current(a,node,input,action,arguments,identity.required("worker_service").textValue());current.requireUnchanged();if(!current.identity().equals(identity))deny();return current;
    }
    @FunctionalInterface interface Operation<T>{T apply(Current current)throws Exception;}
    final class Handle {
        private final TaskStrictFieldNode owner=TaskStrictFieldNode.this;private final HttpServletRequest http;private final String token;
        private final RunTokenAuthority.ActionRequest action;private final JsonNode arguments;
        private Handle(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,JsonNode arguments){this.http=http;this.token=token;this.action=action;this.arguments=arguments;}
    }
    final class Current {
        private final EnterpriseAccess.Admission admission;private final RunTokenAuthority.Admission admitted;
        private final FormalTaskNodeAuthority.Verified control;private final String worker;
        private final TaskStrictFieldInputAuthority.Verified input;private final RunTokenAuthority.ActionRequest action;private final JsonNode arguments;
        private Current(EnterpriseAccess.Admission a,RunTokenAuthority.Admission admitted,TaskStrictFieldInputAuthority.Verified input,RunTokenAuthority.ActionRequest action,JsonNode arguments){admission=a;this.admitted=admitted;this.input=input;this.action=action;this.arguments=arguments;control=null;worker=admitted.serviceUri();}
        private Current(EnterpriseAccess.Admission a,FormalTaskNodeAuthority.Verified control,TaskStrictFieldInputAuthority.Verified input,RunTokenAuthority.ActionRequest action,JsonNode arguments,String worker){admission=a;admitted=null;this.control=control;this.input=input;this.action=action;this.arguments=arguments;this.worker=worker;}
        void requireUnchanged()throws Exception {
            input.requireUnchanged();var fixed=input.input();
            if(control!=null){
                if(!admission.controlWorker()||admission.formalWorker())deny();control.requireUnchanged();admission.controlProof().requireActive();
                var rows=db.queryForList("SELECT s.step_id FROM run_step s JOIN step_action_binding b ON b.tenant_id=s.tenant_id AND b.project_id=s.project_id AND b.run_id=s.run_id AND b.step_id=s.step_id WHERE s.tenant_id=? AND s.project_id=? AND s.run_id=? AND s.step_id=? AND s.status='RUNNING' AND s.lease_generation=? AND s.lease_owner=? AND s.lease_expires_at>UTC_TIMESTAMP(6) AND s.input_sha256=? AND b.action_id=? AND b.lease_generation=? AND b.input_sha256=? AND b.arguments_sha256=? AND b.kind='TOOL' AND b.tool_version=? AND b.state IN ('BOUND','RESERVED','DISPATCHING','IN_FLIGHT') LIMIT 2 FOR UPDATE",fixed.tenantId(),fixed.projectId(),fixed.runId(),fixed.stepId(),generation(),worker,input.inputSha256(),actionId(),generation(),input.inputSha256(),action.argumentsSha256(),fixed.toolVersion().version_id());
                if(rows.size()!=1)deny();
            }else{
            if(!admission.formalWorker())deny();admission.formalProof().requireActive();var id=admission.formalProof().identity();
            if(!fixed.tenantId().equals(id.tenant())||!fixed.projectId().equals(id.project())||!fixed.runId().equals(id.run())||!fixed.stepId().equals(id.step())
                    ||!action.actionId().equals(id.action())||!action.generation().toString().equals(id.generation()))deny();
            }
            if(!action.inputSha256().equals(input.inputSha256())||!action.runId().equals(fixed.runId())||!action.stepId().equals(fixed.stepId())
                    ||!"TOOL".equals(action.kind())||!fixed.toolVersion().version_id().equals(action.toolVersion())||!ACTION.equals(action.action())||!PURPOSE.equals(action.purpose())
                    ||!new PermissionProtocol.Scope(fixed.tenantId(),fixed.projectId(),"TASK",fixed.taskId()).equals(action.scope()))deny();
            requireArguments(fixed,arguments);if(!schema.hash(arguments).equals(action.argumentsSha256()))deny();
            var permission=new RunTokenAuthoritySchema.Permission(action.scope(),ACTION,PURPOSE);
            if(!fixed.nodePermissions().contains(permission)||admitted!=null&&!admitted.delegatedPermissions().contains(permission))deny();
        }
        EnterpriseAccess.Admission admission(){return admission;}TaskStrictFieldInputAuthority.Verified input(){return input;}
        RunTokenAuthority.ActionRequest action(){return action;}String actorId(){return admission.actorId();}
        String generation(){return action.generation().toString();}String actionId(){return action.actionId();}
        String workerService(){return worker;}String classification(){return input.production().classification();}
        JsonNode identity()throws Exception {requireUnchanged();var n=schema.tree(action);var out=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            out.set("action",n);out.set("arguments",arguments.deepCopy());out.put("input_id",input.inputId()).put("input_receipt_sha256",input.receiptSha256()).put("actor_id",actorId()).put("worker_service",workerService());return out;}
    }
    static void requireArguments(TaskStrictFieldInput input,JsonNode arguments){
        ProfessionalRuntimeObservations.closed(arguments,Set.of("artifact_id","field_ids"));
        if(!input.targetArtifactId().equals(arguments.required("artifact_id").textValue())||!arguments.required("field_ids").isArray())deny();
        Set<String> expected=new HashSet<>();for(var entry:input.targetManifest().required("target").required("entries"))expected.add(entry.required("field_id").textValue());
        Set<String> actual=new HashSet<>();for(var id:arguments.required("field_ids"))if(!id.isTextual()||!actual.add(id.textValue()))deny();
        if(expected.isEmpty()||!expected.equals(actual))deny();
    }
    private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())deny();}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
