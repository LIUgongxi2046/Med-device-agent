package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Close independent SourceVerifier process, recover its original settled result, persist findings and coverage,
 * then invoke AT-07 with its own signed TOOL token. No live authority crosses SQL segments. */
public final class TaskSourceVerifierFinishOrchestrator {
    private final JdbcTemplate db;private final TaskSourceVerifierRuntimeAdapter runtime;private final TaskSourceVerifierSupervisor supervisor;
    private final TaskSourceVerifierInvocationService invocations;private final TaskSourceVerifierReviewService reviews;
    private final TaskSourceVerifierObjectLedger objects;private final RuntimeObjectTransport transport;private final TaskSourceVerifierProducerAuthority producers;
    private final RunTokenAuthority tokens;private final RunTokenAuthoritySchema tokenSchema;private final TaskSourceVerifierWorkerAccess workers;private final TaskSourceVerifierSchema json;
    public TaskSourceVerifierFinishOrchestrator(DataSource data,TaskSourceVerifierRuntimeAdapter runtime,TaskSourceVerifierSupervisor supervisor,
            TaskSourceVerifierInvocationService invocations,TaskSourceVerifierReviewService reviews,TaskSourceVerifierObjectLedger objects,
            RuntimeObjectTransport transport,TaskSourceVerifierProducerAuthority producers,RunTokenAuthority tokens,RunTokenAuthoritySchema tokenSchema,
            TaskSourceVerifierWorkerAccess workers,TaskSourceVerifierSchema json){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.runtime=runtime;this.supervisor=supervisor;this.invocations=invocations;this.reviews=reviews;this.objects=objects;this.transport=transport;this.producers=producers;this.tokens=tokens;this.tokenSchema=tokenSchema;this.workers=workers;this.json=json;}
    public JsonNode finish(HttpServletRequest http,TaskSourceVerifierHostAccess.Handle host)throws Exception {
        var snapshot=host.execute(supervisor::snapshotClosure);var observation=supervisor.inspect(snapshot);
        host.execute(session->{var closed=supervisor.close(session,observation);if(!"CLOSED".equals(session.state()))runtime.close(session,closed);return null;});
        var binding=host.execute(session->{var terminal=invocations.settledForSession(session,supervisor.requireClosed(session,observation));terminal.requireUnchanged();return bind(session);});
        var bodyAttempt=host.execute(session->{var produced=body(session,observation,binding.action);return objects.prepareProduced(produced,producers.require(produced.input(),session.admission()));});
        S3ObjectReader.Admission bodyGuard=()->checked(()->host.execute(session->{var produced=body(session,observation,binding.action);objects.requireTransfer(produced,producers.require(produced.input(),session.admission()),bodyAttempt);return null;}));
        var bodyTransfer=transport.transferVerified(bodyAttempt,bodyGuard);
        var candidateAttempt=host.execute(session->{var produced=body(session,observation,binding.action);var producer=producers.require(produced.input(),session.admission());var registered=objects.registerProduced(produced,producer,bodyAttempt,bodyTransfer);return objects.prepareProduced(reviews.coverage(produced),producer);});
        if(!plannedArtifact(binding.action.actionId(),"REVIEW_COVERAGE").equals(candidateAttempt.artifactId()))bad();
        S3ObjectReader.Admission candidateGuard=()->checked(()->host.execute(session->{var produced=body(session,observation,binding.action);var producer=producers.require(produced.input(),session.admission());var registered=objects.registerProduced(produced,producer,bodyAttempt,bodyTransfer);objects.requireTransfer(reviews.coverage(produced),producer,candidateAttempt);return null;}));
        var candidateTransfer=transport.transferVerified(candidateAttempt,candidateGuard);
        host.execute(session->{var produced=body(session,observation,binding.action);var producer=producers.require(produced.input(),session.admission());var registered=objects.registerProduced(produced,producer,bodyAttempt,bodyTransfer);objects.registerProduced(reviews.coverage(produced),producer,candidateAttempt,candidateTransfer);return null;});
        var token=tokens.issue(http,new RunTokenAuthority.IssueRequest(binding.tenant,binding.actor,binding.action.runId(),binding.action.stepId(),binding.action.generation(),binding.actions,Duration.ofMinutes(2)));
        var arguments=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("findings_artifact_id",bodyAttempt.artifactId());
        return workers.withAction(http,token.token(),binding.action,(admission,admitted,current)->reviews.submitSaved(current,admitted,binding.action,arguments,invocations));
    }
    private TaskSourceVerifierReviewService.Produced body(TaskSourceVerifierRuntimeAdapter.Session session,TaskSourceVerifierSupervisor.Inspection observation,RunTokenAuthority.ActionRequest action)throws Exception {
        return reviews.produce(invocations.settledForSession(session,supervisor.requireClosed(session,observation)),action);
    }
    private Bound bind(TaskSourceVerifierRuntimeAdapter.Session session)throws Exception {
        session.requireUnchanged();var current=session.authority();var input=current.input();var i=input.input();
        var scope=new PermissionProtocol.Scope(i.tenantId(),i.projectId(),"TASK",i.taskId());var permission=new RunTokenAuthoritySchema.Permission(scope,TaskSourceVerifierReviewService.ACTION,TaskSourceVerifierReviewService.PURPOSE);
        if(!i.nodePermissions().contains(permission))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        String action=UUID.nameUUIDFromBytes(("N05_AT07/"+i.tenantId()+"/"+i.runId()+"/"+i.stepId()+"/"+current.leaseGeneration()).getBytes(StandardCharsets.UTF_8)).toString();
        var args=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("findings_artifact_id",plannedArtifact(action,"REVIEW_FINDINGS"));String hash=json.hash(args);
        var rows=db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",i.tenantId(),i.runId(),i.stepId(),action);
        if(rows.isEmpty()){
            var at=db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);
            if(db.update("INSERT INTO step_action_binding(tenant_id,project_id,run_id,step_id,action_id,input_sha256,origin_run_id,origin_step_id,origin_action_id,kind,arguments_sha256,tool_version,state,lease_generation,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,?,?,?,'TOOL',?,?,'BOUND',?,'1.0.0',?,?,?,?,1,?)",i.tenantId(),i.projectId(),i.runId(),i.stepId(),action,input.inputSha256(),i.runId(),i.stepId(),action,hash,i.submitToolVersion().version_id(),current.leaseGeneration(),tokenSchema.manifestSha256(),current.job().actor(),at,current.job().classification(),at)!=1)bad();
        }
        var row=one(db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",i.tenantId(),i.runId(),i.stepId(),action));
        if(!hash.equals(row.get("arguments_sha256"))||!"TOOL".equals(row.get("kind"))||!i.submitToolVersion().version_id().equals(row.get("tool_version"))||!input.inputSha256().equals(row.get("input_sha256"))||!current.leaseGeneration().toString().equals(row.get("lease_generation").toString()))bad();
        var requested=new RunTokenAuthority.ActionRequest(i.runId(),i.stepId(),action,current.leaseGeneration(),input.inputSha256(),hash,"TOOL",i.submitToolVersion().version_id(),scope,permission.action(),permission.purpose());
        var actions=new TreeSet<String>();for(var p:i.nodePermissions())if(p.action().endsWith(".read")||"task.run".equals(p.action())||permission.action().equals(p.action()))actions.add(p.action());
        current.requireUnchanged();return new Bound(i.tenantId(),current.job().actor(),requested,List.copyOf(actions));
    }
    static String plannedArtifact(String action,String kind){if(!Set.of("REVIEW_FINDINGS","REVIEW_COVERAGE").contains(kind))bad();return UUID.nameUUIDFromBytes(("N05/"+kind+"/"+action).getBytes(StandardCharsets.UTF_8)).toString();}
    private record Bound(String tenant,String actor,RunTokenAuthority.ActionRequest action,List<String> actions){}
    @FunctionalInterface private interface Checked{void run()throws Exception;}
    private static void checked(Checked operation){try{operation.run();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}}
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
