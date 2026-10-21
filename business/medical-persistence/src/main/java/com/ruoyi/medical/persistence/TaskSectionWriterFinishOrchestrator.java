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

/** Close actual process, recover original settled Writer result, persist body and candidate,
 * then invoke AT-06 with its own signed TOOL token. No live authority crosses SQL segments. */
public final class TaskSectionWriterFinishOrchestrator {
    private final JdbcTemplate db;private final TaskSectionWriterRuntimeAdapter runtime;private final TaskSectionWriterSupervisor supervisor;
    private final TaskSectionWriterInvocationService invocations;private final TaskSectionWriterCandidateService candidates;
    private final TaskSectionWriterObjectLedger objects;private final RuntimeObjectTransport transport;private final TaskSectionWriterProducerAuthority producers;
    private final RunTokenAuthority tokens;private final RunTokenAuthoritySchema tokenSchema;private final TaskSectionWriterWorkerAccess workers;private final TaskSectionWriterSchema json;
    public TaskSectionWriterFinishOrchestrator(DataSource data,TaskSectionWriterRuntimeAdapter runtime,TaskSectionWriterSupervisor supervisor,
            TaskSectionWriterInvocationService invocations,TaskSectionWriterCandidateService candidates,TaskSectionWriterObjectLedger objects,
            RuntimeObjectTransport transport,TaskSectionWriterProducerAuthority producers,RunTokenAuthority tokens,RunTokenAuthoritySchema tokenSchema,
            TaskSectionWriterWorkerAccess workers,TaskSectionWriterSchema json){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.runtime=runtime;this.supervisor=supervisor;this.invocations=invocations;this.candidates=candidates;this.objects=objects;this.transport=transport;this.producers=producers;this.tokens=tokens;this.tokenSchema=tokenSchema;this.workers=workers;this.json=json;}
    public JsonNode finish(HttpServletRequest http,TaskSectionWriterHostAccess.Handle host)throws Exception {
        var snapshot=host.execute(supervisor::snapshotClosure);var observation=supervisor.inspect(snapshot);
        host.execute(session->{var closed=supervisor.close(session,observation);if(!"CLOSED".equals(session.state()))runtime.close(session,closed);return null;});
        var binding=host.execute(session->{var terminal=invocations.settledForSession(session,supervisor.requireClosed(session,observation));terminal.requireUnchanged();return bind(session);});
        var bodyAttempt=host.execute(session->{var produced=body(session,observation,binding.action);return objects.prepareProduced(produced,producers.require(produced.input(),session.admission()));});
        S3ObjectReader.Admission bodyGuard=()->checked(()->host.execute(session->{var produced=body(session,observation,binding.action);objects.requireTransfer(produced,producers.require(produced.input(),session.admission()),bodyAttempt);return null;}));
        var bodyTransfer=transport.transferVerified(bodyAttempt,bodyGuard);
        var candidateAttempt=host.execute(session->{var produced=body(session,observation,binding.action);var producer=producers.require(produced.input(),session.admission());var registered=objects.registerProduced(produced,producer,bodyAttempt,bodyTransfer);return objects.prepareProduced(candidates.candidate(produced,registered),producer);});
        if(!plannedCandidate(binding.action.actionId()).equals(candidateAttempt.artifactId()))bad();
        S3ObjectReader.Admission candidateGuard=()->checked(()->host.execute(session->{var produced=body(session,observation,binding.action);var producer=producers.require(produced.input(),session.admission());var registered=objects.registerProduced(produced,producer,bodyAttempt,bodyTransfer);objects.requireTransfer(candidates.candidate(produced,registered),producer,candidateAttempt);return null;}));
        var candidateTransfer=transport.transferVerified(candidateAttempt,candidateGuard);
        host.execute(session->{var produced=body(session,observation,binding.action);var producer=producers.require(produced.input(),session.admission());var registered=objects.registerProduced(produced,producer,bodyAttempt,bodyTransfer);objects.registerProduced(candidates.candidate(produced,registered),producer,candidateAttempt,candidateTransfer);return null;});
        var token=tokens.issue(http,new RunTokenAuthority.IssueRequest(binding.tenant,binding.actor,binding.action.runId(),binding.action.stepId(),binding.action.generation(),binding.actions,Duration.ofMinutes(2)));
        var arguments=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("candidate_artifact_id",candidateAttempt.artifactId());
        return workers.withAction(http,token.token(),binding.action,(admission,admitted,current)->candidates.submitSaved(current,admitted,binding.action,arguments,invocations));
    }
    private TaskSectionWriterCandidateService.Produced body(TaskSectionWriterRuntimeAdapter.Session session,TaskSectionWriterSupervisor.Inspection observation,RunTokenAuthority.ActionRequest action)throws Exception {
        return candidates.body(invocations.settledForSession(session,supervisor.requireClosed(session,observation)),action);
    }
    private Bound bind(TaskSectionWriterRuntimeAdapter.Session session)throws Exception {
        session.requireUnchanged();var current=session.authority();var input=current.input();var i=input.input();
        var scope=new PermissionProtocol.Scope(i.tenantId(),i.projectId(),"TASK",i.taskId());var permission=new RunTokenAuthoritySchema.Permission(scope,TaskSectionWriterCandidateService.ACTION,TaskSectionWriterCandidateService.PURPOSE);
        if(!i.nodePermissions().contains(permission))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        String action=UUID.nameUUIDFromBytes(("N04_AT06/"+i.tenantId()+"/"+i.runId()+"/"+i.stepId()+"/"+current.leaseGeneration()).getBytes(StandardCharsets.UTF_8)).toString();
        var args=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("candidate_artifact_id",plannedCandidate(action));String hash=json.hash(args);
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
    static String plannedCandidate(String action){return UUID.nameUUIDFromBytes(("N04_SECTION_CANDIDATE/"+action).getBytes(StandardCharsets.UTF_8)).toString();}
    private record Bound(String tenant,String actor,RunTokenAuthority.ActionRequest action,List<String> actions){}
    @FunctionalInterface private interface Checked{void run()throws Exception;}
    private static void checked(Checked operation){try{operation.run();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}}
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
