package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.workflow.WorkflowProtocol.Dispatch;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;

/** Receipt-only inventory. Host observations, expired leases and absent journals
 * are not termination evidence. This class has no network/cancel/send methods. */
public final class TaskRunTerminationFacts {
    private FormalInvocationCancellation cancellation;
    void cancellation(FormalInvocationCancellation value){cancellation=Objects.requireNonNull(value);}
    private org.springframework.beans.factory.ObjectProvider<TaskSourceVerifierLifecycleFacts> verifierFacts;
    void verifierFacts(org.springframework.beans.factory.ObjectProvider<TaskSourceVerifierLifecycleFacts> value){verifierFacts=value;}
    private org.springframework.beans.factory.ObjectProvider<TaskSectionWriterLifecycleFacts> writerFacts;
    void writerFacts(org.springframework.beans.factory.ObjectProvider<TaskSectionWriterLifecycleFacts> value){writerFacts=value;}
    private final org.springframework.beans.factory.ObjectProvider<TaskRunStageCommitServiceV2> stages;private final JdbcTemplate db;private final PermissionSchema json;private final TaskInvocationRecords invocations;
    private final FormalInvocationSettlement settlements;private final TaskProfessionalSupervisor supervisor;
    private final TaskProfessionalRuntimeService.Deployment deployment;private final TaskRuntimeJournal dispatches;private final int maximumFacts;
    private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public TaskRunTerminationFacts(DataSource data,PermissionSchema json,TaskInvocationRecords invocations,FormalInvocationSettlement settlements,
            TaskProfessionalSupervisor supervisor,TaskProfessionalRuntimeService.Deployment deployment,TaskRuntimeJournal dispatches,int maximumFacts,org.springframework.beans.factory.ObjectProvider<TaskRunStageCommitServiceV2> stages){this.stages=stages;
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.invocations=invocations;this.settlements=settlements;this.supervisor=supervisor;this.deployment=deployment;this.dispatches=dispatches;
        if(maximumFacts<1||maximumFacts>10000)throw new IllegalArgumentException("EXPLICIT_TERMINATION_INVENTORY_BOUND_REQUIRED");this.maximumFacts=maximumFacts;
    }
    Verified require(TaskRunLifecycleAuthority.Proof p,String onlyStep)throws Exception{p.require(p.operation());var result=snapshot(p,onlyStep);return new Verified(p,onlyStep,result);}
    final class Verified {
        private final TaskRunLifecycleAuthority.Proof proof;private final String step;private final JsonNode snapshot;
        private Verified(TaskRunLifecycleAuthority.Proof p,String step,JsonNode snapshot){proof=p;this.step=step;this.snapshot=snapshot.deepCopy();}
        void requireUnchanged()throws Exception{proof.require(proof.operation());same(snapshot,TaskRunTerminationFacts.this.snapshot(proof,step));}
        boolean invocationsTerminal(){return snapshot.required("invocations_terminal").booleanValue();}boolean processesExited(){return snapshot.required("processes_exited").booleanValue();}
        boolean failed(){return snapshot.required("failed").booleanValue();}JsonNode evidence(){return snapshot.deepCopy();}
    }
    /** Private original-session capability. It cannot authorize a process launch,
     * invocation or a replacement dispatch. Its lifetime is the current SQL segment. */
    final class PendingClosure {
        private final TaskRunLifecycleAuthority.Proof proof;
        private final Map<String,Object> row;
        private final JsonNode original;
        private PendingClosure(TaskRunLifecycleAuthority.Proof p,Map<String,Object> row,JsonNode original){
            this.proof=p;this.row=Collections.unmodifiableMap(new HashMap<>(row));this.original=original.deepCopy();
        }
        void requireUnchanged()throws Exception {
            proof.require(TaskRunLifecycleAuthority.Operation.REQUEST_CANCEL);
            same("CANCEL_REQUESTED",proof.status());
            same(row,one(db.queryForList("SELECT * FROM professional_runtime_session WHERE tenant_id=? AND run_id=? AND session_id=? LIMIT 2 FOR UPDATE",proof.reference().tenantId(),proof.reference().runId(),session())));
            same(original,snapshot(proof,null));
        }
        String session(){return text(row,"session_id");}
        JsonNode start()throws Exception{requireUnchanged();return reader.readTree(text(row,"canonical_start"));}
        String project(){return proof.reference().projectId();}
        String actor(){return proof.admission().actorId();}
        String classification(){return proof.classification();}
    }
    List<PendingClosure> pendingClosures(TaskRunLifecycleAuthority.Proof p)throws Exception {
        p.require(TaskRunLifecycleAuthority.Operation.REQUEST_CANCEL);same("CANCEL_REQUESTED",p.status());
        var original=snapshot(p,null);var pending=new ArrayList<PendingClosure>();
        for(var session:original.required("sessions"))if(session.required("closed").isNull()){
            var row=one(db.queryForList("SELECT * FROM professional_runtime_session WHERE tenant_id=? AND run_id=? AND session_id=? LIMIT 2 FOR UPDATE",p.reference().tenantId(),p.reference().runId(),session.required("session_id").asText()));
            pending.add(new PendingClosure(p,row,original));
        }
        return List.copyOf(pending);
    }
    private org.springframework.beans.factory.ObjectProvider<TaskSectionWriterSupervisor> writerSupervisor;private org.springframework.beans.factory.ObjectProvider<TaskSectionWriterDeployment> writerDeployment;
    private org.springframework.beans.factory.ObjectProvider<TaskSourceVerifierSupervisor> verifierSupervisor;private org.springframework.beans.factory.ObjectProvider<TaskSourceVerifierDeployment> verifierDeployment;
    void partialSupervisors(org.springframework.beans.factory.ObjectProvider<TaskSectionWriterSupervisor> w,org.springframework.beans.factory.ObjectProvider<TaskSectionWriterDeployment> wd,org.springframework.beans.factory.ObjectProvider<TaskSourceVerifierSupervisor> v,org.springframework.beans.factory.ObjectProvider<TaskSourceVerifierDeployment> vd){writerSupervisor=w;writerDeployment=wd;verifierSupervisor=v;verifierDeployment=vd;}
    Optional<JsonNode> pendingTermination(PendingLaunch original)throws Exception{return switch(original.kind()){case "N02"->supervisor.originalPendingTermination(original,deployment);case "N04"->writerSupervisor.getObject().originalPendingTermination(original,writerDeployment.getObject());case "N05"->verifierSupervisor.getObject().originalPendingTermination(original,verifierDeployment.getObject());default->throw new IllegalStateException();};}
    /** Original durable launch intent without a registered session. No assertion that a process ran. */
    final class PendingLaunch {
        private final TaskRunLifecycleAuthority.Proof proof;private final Map<String,Object> row;private final JsonNode envelope;private final String table,kind;
        private PendingLaunch(TaskRunLifecycleAuthority.Proof proof,Map<String,Object> row,String table,String kind)throws Exception{this.proof=proof;this.row=Collections.unmodifiableMap(new HashMap<>(row));this.table=table;this.kind=kind;envelope=reader.readTree(text(row,"N02".equals(kind)?"canonical_payload":"envelope_json"));requireUnchanged();}
        void requireUnchanged()throws Exception{
            proof.require(proof.operation());same("CANCEL_REQUESTED",proof.status());var ref=proof.reference();
            if("N02".equals(kind))same(row,one(db.queryForList("SELECT * FROM runtime_object_intent WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId(),text(row,"write_id"))));else same(row,one(db.queryForList("SELECT * FROM "+table+" WHERE tenant_id=? AND project_id=? AND run_id=? AND session_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId(),session())));
            for(String field:List.of("tenant_id","project_id","run_id","step_id"))same(text(row,field),envelope.required(field).asText());
            same(ref.tenantId(),text(row,"tenant_id"));same(ref.projectId(),text(row,"project_id"));same(ref.runId(),text(row,"run_id"));same(proof.admission().actorId(),text(row,"created_by"));same(proof.classification(),text(row,"classification"));
            if("N02".equals(kind)){same("STEP_ENVELOPE",text(row,"write_kind"));same("2.0.0",text(row,"schema_version"));}else{same("1.0.0",text(row,"schema_version"));same(hash(reader.readTree(text(row,"launch_json"))),text(row,"launch_sha256"));same(binding(),reader.readTree(text(row,"launch_json")).required("binding"));}same(kind,envelope.required("node_kind").asText());same("TASK",envelope.required("execution_kind").asText());same(hash(envelope),envelopeHash());
            if(!db.queryForList("SELECT session_id FROM professional_runtime_session WHERE tenant_id=? AND session_id=? LIMIT 1 FOR UPDATE",ref.tenantId(),session()).isEmpty())bad();
            var step=one(db.queryForList("SELECT input_sha256,lease_generation FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.runId(),step()));same(text(step,"input_sha256"),envelope.required("input_sha256").asText());same(number(step,"lease_generation"),envelope.required("lease_generation").asText());
        }
        String kind(){return kind;}String envelopeHash(){return text(row,"N02".equals(kind)?"object_sha256":"envelope_sha256");}
        String session(){return envelope.required("session_id").asText();}String step(){return text(row,"step_id");}long generation(){return envelope.required("lease_generation").longValue();}
        String project(){return proof.reference().projectId();}String actor(){return proof.admission().actorId();}String classification(){return proof.classification();}
        String deploymentHash(){return envelope.required("deployment_sha256").asText();}
        JsonNode binding(){return json.object().put("tenant_id",proof.reference().tenantId()).put("run_id",proof.reference().runId()).put("step_id",step()).put("action_id",envelope.required("action_id").asText()).put("lease_generation",generation()).put("input_sha256",envelope.required("input_sha256").asText()).put("step_envelope_sha256",envelopeHash());}
    }
    List<PendingLaunch> pendingLaunches(TaskRunLifecycleAuthority.Proof p)throws Exception{
        if(!"CANCEL_REQUESTED".equals(p.status()))return List.of();var rows=db.queryForList("SELECT i.* FROM runtime_object_intent i WHERE i.tenant_id=? AND i.run_id=? AND i.write_kind='STEP_ENVELOPE' AND i.schema_version='2.0.0' AND i.canonical_payload->>'$.node_kind'='N02' AND NOT EXISTS (SELECT 1 FROM professional_runtime_session s WHERE s.tenant_id=i.tenant_id AND s.session_id=i.canonical_payload->>'$.session_id') ORDER BY i.write_id LIMIT ? FOR UPDATE",p.reference().tenantId(),p.reference().runId(),maximumFacts+1);bound(rows);var result=new ArrayList<PendingLaunch>();for(var row:rows)result.add(new PendingLaunch(p,row,"runtime_object_intent","N02"));for(var type:Map.of("N04","task_section_writer_runtime_prepare","N05","task_source_verifier_runtime_prepare").entrySet()){var prepared=db.queryForList("SELECT i.* FROM "+type.getValue()+" i WHERE i.tenant_id=? AND i.run_id=? AND NOT EXISTS (SELECT 1 FROM professional_runtime_session s WHERE s.tenant_id=i.tenant_id AND s.session_id=i.session_id) ORDER BY i.session_id LIMIT ? FOR UPDATE",p.reference().tenantId(),p.reference().runId(),maximumFacts+1);bound(prepared);for(var row:prepared)result.add(new PendingLaunch(p,row,type.getValue(),type.getKey()));}if(result.size()>maximumFacts)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return List.copyOf(result);
    }
    void cancelPending(TaskRunLifecycleAuthority.Proof p)throws Exception {
        p.require(TaskRunLifecycleAuthority.Operation.REQUEST_CANCEL);same("CANCEL_REQUESTED",p.status());if(cancellation==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var ref=p.reference();var rows=db.queryForList("SELECT invocation_id,step_id FROM invocation WHERE tenant_id=? AND run_id=? AND status IN ('RESERVED','DISPATCHING','IN_FLIGHT','UNKNOWN','CANCEL_REQUESTED') ORDER BY invocation_id LIMIT ? FOR UPDATE",ref.tenantId(),ref.runId(),maximumFacts+1);bound(rows);
        var writer=writerFacts==null?null:writerFacts.getIfAvailable();var verifier=verifierFacts==null?null:verifierFacts.getIfAvailable();
        for(var row:rows){String id=text(row,"invocation_id"),step=text(row,"step_id");FormalInvocationBinding original=verifier!=null&&verifier.verifier(p,step)?verifier.invocation(p,id):writer!=null&&writer.writer(p,step)?writer.invocation(p,id):invocations.byInvocation(ref.tenantId(),id);cancellation.cancelLifecycle(p,original,id);}
    }
    private JsonNode snapshot(TaskRunLifecycleAuthority.Proof p,String onlyStep)throws Exception{
        var ref=p.reference();var a=p.admission();String suffix=onlyStep==null?"":" AND step_id=?";var args=new ArrayList<Object>(List.of(ref.tenantId(),ref.runId()));if(onlyStep!=null)args.add(onlyStep);args.add(maximumFacts+1);
        var calls=db.queryForList("SELECT invocation_id,step_id FROM invocation WHERE tenant_id=? AND run_id=?"+suffix+" ORDER BY invocation_id LIMIT ? FOR UPDATE",args.toArray());bound(calls);
        var body=json.object();var stageService=stages.getIfAvailable();var deterministic=stageService==null?List.<com.ruoyi.medical.workflow.TaskRunStageProtocolV2.StageCommitV2>of():stageService.loadLifecycle(p);body.set("typed_stages",json.tree(deterministic));var callEvidence=body.putArray("invocations");boolean terminal=true,exited=true,failed=false;
        var writer=writerFacts==null?null:writerFacts.getIfAvailable();var verifier=verifierFacts==null?null:verifierFacts.getIfAvailable();
        for(var row:calls){String id=text(row,"invocation_id");FormalInvocationBinding original=verifier!=null&&verifier.verifier(p,text(row,"step_id"))?verifier.invocation(p,id):writer!=null&&writer.writer(p,text(row,"step_id"))?writer.invocation(p,id):invocations.byInvocation(ref.tenantId(),id);same(ref.runId(),original.run());same(ref.projectId(),original.project());same(a.actorId(),original.actor());same(text(row,"step_id"),original.step());
            var noSend=FormalInvocationCancellation.noSend(db,ref.tenantId(),a.actorId(),id);if(noSend.isPresent()){var n=json.object().put("invocation_id",id).put("step_id",original.step());n.set("no_send",noSend.get());callEvidence.add(n);failed=true;continue;}
            var receipt=settlements.read(ref.tenantId(),id);var n=json.object().put("invocation_id",id).put("step_id",original.step());
            if(receipt.isEmpty()){terminal=false;n.putNull("settlement");}
            else{var actual=receipt.get().receipt();n.set("settlement",actual);n.put("settlement_sha256",receipt.get().receiptSha256());boolean done=receipt.get().settled()&&actual.required("provider_terminal").isBoolean()&&actual.required("provider_terminal").booleanValue()&&Set.of("SUCCEEDED","FAILED","CANCELLED").contains(actual.required("status").asText());terminal&=done;failed|=done&&!"SUCCEEDED".equals(actual.required("status").asText());}
            callEvidence.add(n);
        }
        var sessions=db.queryForList("SELECT * FROM professional_runtime_session WHERE tenant_id=? AND run_id=?"+suffix+" ORDER BY session_id LIMIT ? FOR UPDATE",args.toArray());bound(sessions);var sessionEvidence=body.putArray("sessions");var seen=new HashSet<String>();
        for(var session:sessions){same(ref.projectId(),text(session,"project_id"));same(a.actorId(),text(session,"created_by"));same(p.classification(),text(session,"classification"));String step=text(session,"step_id"),id=text(session,"session_id");long generation=Long.parseLong(number(session,"lease_generation"));if(!seen.add(step+":"+generation))bad();
            var terminated=TaskProcessTerminationFacts.read(db,p,session);
            if(terminated.isPresent()){var evidence=terminated.get();var n=json.object().put("session_id",id).put("step_id",step).put("lease_generation",generation).put("start_sha256",text(session,"start_sha256"));n.set("closed",evidence);n.put("closed_sha256",hash(evidence));failed=true;sessionEvidence.add(n);continue;}
            if(verifier!=null&&verifier.verifier(p,step)){var n=verifier.session(p,session);if(n.required("closed").isNull())exited=false;else {var exit=n.required("closed").required("exit");failed|=!"EXITED".equals(exit.required("code").asText())||exit.required("status").intValue()!=0;}sessionEvidence.add(n);continue;}
            if(writer!=null&&writer.writer(p,step)){var n=writer.session(p,session);if(n.required("closed").isNull())exited=false;else {var exit=n.required("closed").required("exit");failed|=!"EXITED".equals(exit.required("code").asText())||exit.required("status").intValue()!=0;}sessionEvidence.add(n);continue;}
            Dispatch original=dispatches.readLifecycleStep(p,step,generation);same(original.actionId(),text(session,"action_id"));same(original.inputSha256(),text(session,"input_sha256"));same(original.stepEnvelopeSha256(),text(session,"envelope_sha256"));
            var start=reader.readTree(text(session,"canonical_start"));same(json.canonical(start),text(session,"canonical_start"));same(hash(start),text(session,"start_sha256"));same(id,start.required("session_id").asText());same(deployment.sha256(),start.required("deployment_sha256").asText());
            var binding=json.tree(new ProfessionalRuntimeObservations.Binding(ref.tenantId(),ref.runId(),step,original.actionId(),generation,original.inputSha256(),original.stepEnvelopeSha256()));same(binding,start.required("binding"));
            var opened=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND created_by=? AND action='task.professional.session.opened' AND request_id=? AND trace_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),a.actorId(),a.actorId(),id,ref.runId()));
            for(String f:List.of("tenant_id","project_id","created_by","classification","schema_version","schema_manifest_sha256","created_at"))same(session.get(f),opened.get(f));same("SUCCESS",text(opened,"result"));same(null,opened.get("reason_code"));same(json.canonical(start),text(opened,"resource_ref"));
            var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",ref.tenantId(),id));same(ref.runId(),event.get("aggregate_id"));same("1",number(event,"aggregate_revision"));same("task.professional.session.opened",event.get("event_type"));same(json.canonical(start),event.get("payload"));same(session.get("created_at"),event.get("occurred_at"));
            var n=json.object().put("session_id",id).put("step_id",step).put("lease_generation",generation).put("start_sha256",hash(start));
            var closed=db.queryForList("SELECT receipt_id,evidence_sha256,canonical_evidence FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase='CLOSED' LIMIT 2 FOR UPDATE",ref.tenantId(),id);
            if(closed.isEmpty()){exited=false;n.putNull("closed");}
            else{var row=one(closed);var genuine=supervisor.requireClosedOriginal(a,binding,id,text(row,"receipt_id"),text(row,"evidence_sha256"),deployment);genuine.requireUnchanged();var evidence=reader.readTree(text(row,"canonical_evidence"));same(hash(evidence),genuine.sha256());n.set("closed",evidence);n.put("closed_sha256",genuine.sha256());failed|=!"EXITED".equals(evidence.required("exit").required("code").asText())||evidence.required("exit").required("status").intValue()!=0;}
            sessionEvidence.add(n);
        }
        var partial=body.putArray("unadmitted_launches");for(var launch:pendingLaunches(p)){if(onlyStep!=null&&!onlyStep.equals(launch.step()))continue;var closure=pendingTermination(launch);var value=json.object().put("session_id",launch.session()).put("step_id",launch.step()).put("lease_generation",launch.generation());if(closure.isEmpty()){value.putNull("termination");exited=false;}else{value.set("termination",closure.get());seen.add(launch.step()+":"+launch.generation());failed=true;}partial.add(value);}
        var steps=db.queryForList("SELECT step_id,lease_generation,status FROM run_step WHERE tenant_id=? AND run_id=?"+suffix+" ORDER BY step_id LIMIT ? FOR UPDATE",args.toArray());bound(steps);var leases=body.putArray("leases");
        for(var step:steps){long generation=Long.parseLong(number(step,"lease_generation"));boolean never=TaskInterruptedStepCancellation.eligible(db,p,text(step,"step_id"));boolean deterministicFenced=TaskDeterministicStageCancellation.fenced(db,p,text(step,"step_id"));if(generation>0&&!seen.contains(text(step,"step_id")+":"+generation)&&deterministic.stream().noneMatch(r->r.completion().stepId().equals(text(step,"step_id"))&&r.completion().leaseGeneration()==generation)&&!never&&!deterministicFenced)exited=false;var lease=json.object().put("step_id",text(step,"step_id")).put("lease_generation",generation);if(never)lease.put("launch_disposition","NEVER_LAUNCHED_UNDER_CLOSED_FENCE");if(deterministicFenced)lease.put("launch_disposition","DETERMINISTIC_CLOSED_FENCE_NO_PROCESS");leases.add(lease);}

        var deterministicExecution=TaskDeterministicExecution.facts(db,json,p);body.set("deterministic_execution",deterministicExecution);exited&=deterministicExecution.required("activities_exited").booleanValue();
        body.put("invocations_terminal",terminal).put("processes_exited",exited).put("failed",failed);p.require(p.operation());return body;
    }
    private String hash(JsonNode n)throws Exception{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(n));}
    private void bound(List<?> rows){if(rows.size()>maximumFacts)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
}
