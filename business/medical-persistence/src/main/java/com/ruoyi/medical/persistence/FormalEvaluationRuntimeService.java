package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.workflow.FormalEvaluationProtocol;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** Real EVAL host boundaries. Every I/O guard reconstructs private current authority in a
 * fresh transaction; professional recovery never starts a new process, model call or tool producer;
 * pure recovery may compute from the original registered input if no execution committed. */
public final class FormalEvaluationRuntimeService {
 private final FormalEvaluationControlAccess access;private final FormalEvaluationNodeInputs inputs;private final FormalEvaluationNodeLeases leases;
 private final FormalEvaluationNodeRuntime runtime;private final FormalEvaluationSupervisor supervisor;private final FormalEvaluationRuntimeObjects objects;
 private final RuntimeObjectTransport transport;private final FormalEvaluationNodeOutcomes outcomes;private final FormalEvaluationInvocationService invocations;
 private final FormalEvaluationDeterministicRuntime deterministic;private final FormalEvaluationDeterministicObjects deterministicObjects;private final FormalInvocationCancellation cancellation;private final FormalEvaluationDeployments deployments;
 private final FormalEvaluationCellCompletion cells;private final FormalEvaluationStageInputPreparation stages;private final FormalEvaluationCompletionService completions;private final FormalEvaluationJobs jobs;private final FormalEvaluationLaunchLedger launches;private final PermissionSchema json;private final JdbcTemplate db;
 public FormalEvaluationRuntimeService(DataSource data,FormalEvaluationControlAccess access,FormalEvaluationNodeInputs inputs,FormalEvaluationNodeLeases leases,FormalEvaluationNodeRuntime runtime,FormalEvaluationSupervisor supervisor,FormalEvaluationRuntimeObjects objects,RuntimeObjectTransport transport,FormalEvaluationNodeOutcomes outcomes,FormalEvaluationInvocationService invocations,FormalEvaluationCompletionService completions,FormalEvaluationJobs jobs,FormalEvaluationLaunchLedger launches,PermissionSchema json,FormalEvaluationStageInputPreparation stages,FormalEvaluationCellCompletion cells,FormalInvocationCancellation cancellation,FormalEvaluationDeployments deployments,FormalEvaluationDeterministicRuntime deterministic,FormalEvaluationDeterministicObjects deterministicObjects){this.deterministic=deterministic;this.deterministicObjects=deterministicObjects;this.cancellation=cancellation;this.deployments=deployments;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.access=access;this.inputs=inputs;this.leases=leases;this.runtime=runtime;this.supervisor=supervisor;this.objects=objects;this.transport=transport;this.outcomes=outcomes;this.invocations=invocations;this.completions=completions;this.jobs=jobs;this.launches=launches;this.json=json;this.stages=stages;this.cells=cells;}
 public JsonNode awaitLaunch(HttpServletRequest http,FormalEvaluationProtocol.Reference ref)throws Exception{return access.withReads(http,ref.tenantId(),ref.runId(),reads->reads.execute(a->ThrowingSupplier.of(()->{launches.requireAccepted(a,jobs.byRun(a,ref.runId()),ref);return json.object().put("state","ACCEPTED");}).get()));}
 public FormalEvaluationProtocol.CellReceipt complete(HttpServletRequest http,FormalEvaluationProtocol.Reference reference,List<FormalEvaluationProtocol.NodeReceipt> receipts)throws Exception{return access.withReads(http,reference.tenantId(),reference.runId(),reads->reads.execute(a->ThrowingSupplier.of(()->cells.complete(a,reference,receipts)).get()));}
 public JsonNode cancelPlan(HttpServletRequest http,FormalEvaluationProtocol.Reference ref)throws Exception {
  return access.withReads(http,ref.tenantId(),ref.runId(),reads->reads.execute(a->ThrowingSupplier.of(()->{var proof=runtime.requestCancellation(a,ref);invocations.cancelOriginal(proof,cancellation);return proof.document();}).get()));
 }
 public FormalEvaluationProtocol.CellReceipt cancel(HttpServletRequest http,FormalEvaluationProtocol.Reference ref)throws Exception {
  return access.withReads(http,ref.tenantId(),ref.runId(),reads->ThrowingSupplier.of(()->{
   var prior=reads.execute(a->ThrowingSupplier.of(()->{runtime.requireCancellation(a,ref);return db.queryForList("SELECT run_id FROM formal_evaluation_cell_outcome WHERE tenant_id=? AND run_id=? AND state='CANCELLED' LIMIT 1",ref.tenantId(),ref.runId()).isEmpty()?null:cells.cancel(a,ref);}).get());if(prior!=null)return prior;
   var plan=reads.execute(a->ThrowingSupplier.of(()->runtime.requireCancellation(a,ref).document()).get());
   for(var original:plan.required("original_sessions")){
    boolean issued=reads.execute(a->!db.queryForList("SELECT session_id FROM formal_evaluation_host_launch_attempt WHERE tenant_id=? AND session_id=? LIMIT 1",ref.tenantId(),original.required("session_id").asText()).isEmpty());if(!issued)continue;
    var deployment=deployments.require(original.required("node_kind").asText());
    try{
     var admission=reads.execute(a->ThrowingSupplier.of(()->supervisor.snapshotCancellation(runtime.requireCancellation(a,ref),original,deployment,"ADMITTED")).get());
     var closed=reads.execute(a->ThrowingSupplier.of(()->supervisor.snapshotCancellation(runtime.requireCancellation(a,ref),original,deployment,"CLOSED")).get());
     var actualAdmission=supervisor.inspect(admission);var actualClosure=supervisor.inspect(closed);
     reads.execute(a->ThrowingSupplier.of(()->{supervisor.recordCancellation(runtime.requireCancellation(a,ref),original,deployment,actualAdmission,actualClosure);return null;}).get());
    }catch(java.io.IOException pending){return reads.execute(a->ThrowingSupplier.of(()->cells.cancelUnknown(a,ref)).get());}
    catch(TenantAccessException pending){if(!pending(pending))throw pending;return reads.execute(a->ThrowingSupplier.of(()->cells.cancelUnknown(a,ref)).get());}
   }
   try{return reads.execute(a->ThrowingSupplier.of(()->{var proof=runtime.requireCancellation(a,ref);invocations.cancelOriginal(proof,cancellation);return cells.cancel(a,ref);}).get());}
   catch(TenantAccessException pending){if(!pending(pending))throw pending;return reads.execute(a->ThrowingSupplier.of(()->cells.cancelUnknown(a,ref)).get());}
  }).get());
 }
 public JsonNode open(HttpServletRequest http,FormalEvaluationProtocol.Reference ref,FormalEvaluationProtocol.Step step)throws Exception {
  return access.withReads(http,ref.tenantId(),ref.runId(),reads->ThrowingSupplier.of(()->{var waiting=reads.execute(a->ThrowingSupplier.of(()->{var proof=stages.waitingOriginal(a,ref.runId(),step);return proof==null?null:runtime.waiting(a,ref,step,proof);}).get());if(waiting!=null)return json.tree(waiting);var input=inputs.read(reads,step);if("N03".equals(step.nodeKind())||FormalEvaluationExecutionProtocol.deterministic(input.node().node()))return json.tree(deterministic(reads,ref,step,input,false));var handle=new Handle(reads,ref,step,input,null);
   reads.execute(a->ThrowingSupplier.of(()->{runtime.prepare(leases.acquire(a,jobs.byRun(a,ref.runId()),ref,step,input,reads.workerService()));return null;}).get());
   handle.transfer("STEP_ENVELOPE");return reads.execute(a->ThrowingSupplier.of(()->runtime.issueLaunch(handle.envelope())).get());
  }).get());
 }
 public JsonNode admit(HttpServletRequest http,FormalEvaluationProtocol.Reference ref,FormalEvaluationProtocol.Step step,String session)throws Exception {
  return access.withReads(http,ref.tenantId(),ref.runId(),reads->ThrowingSupplier.of(()->{var h=new Handle(reads,ref,step,inputs.read(reads,step),session);
   var request=reads.execute(a->ThrowingSupplier.of(()->supervisor.snapshotAdmission(h.envelope())).get());var observed=supervisor.inspect(request);
   return reads.execute(a->ThrowingSupplier.of(()->{var e=h.envelope();if(!session.equals(e.sessionId()))bad();var admitted=supervisor.admit(e,observed);var started=runtime.admit(http,e,admitted);return started.launch();}).get());
  }).get());
 }
 public FormalEvaluationProtocol.NodeReceipt finish(HttpServletRequest http,FormalEvaluationProtocol.Reference ref,FormalEvaluationProtocol.Step step,String session)throws Exception {
  return access.withReads(http,ref.tenantId(),ref.runId(),reads->ThrowingSupplier.of(()->{var h=new Handle(reads,ref,step,inputs.read(reads,step),session);
   var snapshot=reads.execute(a->ThrowingSupplier.of(()->supervisor.snapshotClosure(h.session())).get());h.closure=supervisor.inspect(snapshot);
   reads.execute(a->ThrowingSupplier.of(()->{var current=h.session();runtime.close(current,supervisor.close(current,h.closure));return null;}).get());
   for(String kind:List.of("STEP_ENVELOPE","SESSION_EXPORT","BUSINESS_OUTPUT","STEP_RESULT"))h.transfer(kind);
   return reads.execute(a->ThrowingSupplier.of(()->{var receipt=runtime.finish(h.checked(),h.registered("STEP_ENVELOPE"),h.registered("SESSION_EXPORT"),h.registered("BUSINESS_OUTPUT"),h.registered("STEP_RESULT"));if("SUCCEEDED".equals(receipt.state()))completions.record(a,runtime.requireOriginalCompleted(a,ref.runId(),step.stepId()));return receipt;}).get());
  }).get());
 }
 public FormalEvaluationProtocol.NodeReceipt original(HttpServletRequest http,FormalEvaluationProtocol.Reference ref,FormalEvaluationProtocol.Step step)throws Exception {
  return access.withReads(http,ref.tenantId(),ref.runId(),reads->reads.execute(a->ThrowingSupplier.of(()->{launches.requireAccepted(a,jobs.byRun(a,ref.runId()),ref);var receipt=runtime.originalNodeReceipt(a,ref.runId(),step.stepId());receipt.require(ref,step);if("SUCCEEDED".equals(receipt.state()))completions.record(a,runtime.requireOriginalCompleted(a,ref.runId(),step.stepId()));return receipt;}).get()));
 }
 public JsonNode closeAdmission(HttpServletRequest http,FormalEvaluationProtocol.Reference ref,FormalEvaluationProtocol.Step step,String session)throws Exception {
  return access.withReads(http,ref.tenantId(),ref.runId(),reads->ThrowingSupplier.of(()->{var h=new Handle(reads,ref,step,inputs.readOriginal(reads,step),session);h.recovery=true;return reads.execute(a->ThrowingSupplier.of(()->runtime.closeAdmission(h.session())).get());}).get());
 }
 public FormalEvaluationProtocol.NodeReceipt recover(HttpServletRequest http,FormalEvaluationProtocol.Reference ref,FormalEvaluationProtocol.Step step)throws Exception {
  return access.withReads(http,ref.tenantId(),ref.runId(),reads->ThrowingSupplier.of(()->{
   var terminal=reads.execute(a->ThrowingSupplier.of(()->{launches.requireAccepted(a,jobs.byRun(a,ref.runId()),ref);if(!db.queryForList("SELECT step_id FROM formal_evaluation_node_outcome WHERE tenant_id=? AND run_id=? AND step_id=? UNION ALL SELECT step_id FROM formal_evaluation_node_waiting WHERE tenant_id=? AND run_id=? AND step_id=? UNION ALL SELECT step_id FROM formal_evaluation_deterministic_outcome WHERE tenant_id=? AND run_id=? AND step_id=?",ref.tenantId(),ref.runId(),step.stepId(),ref.tenantId(),ref.runId(),step.stepId(),ref.tenantId(),ref.runId(),step.stepId()).isEmpty())return runtime.originalNodeReceipt(a,ref.runId(),step.stepId());return null;}).get());if(terminal!=null){terminal.require(ref,step);if("SUCCEEDED".equals(terminal.state()))reads.execute(a->ThrowingSupplier.of(()->completions.record(a,runtime.requireOriginalCompleted(a,ref.runId(),step.stepId()))).get());return terminal;}
   if(Set.of("N03","N06").contains(step.nodeKind())){var originalInput=inputs.readOriginal(reads,step);if(FormalEvaluationExecutionProtocol.deterministic(originalInput.node().node()))return deterministic(reads,ref,step,originalInput,true);}
   var sessions=reads.execute(a->db.query("SELECT p.session_id FROM formal_evaluation_runtime_prepare p JOIN professional_runtime_session s ON s.tenant_id=p.tenant_id AND s.session_id=p.session_id WHERE p.tenant_id=? AND p.run_id=? AND p.step_id=? LIMIT 2",(r,n)->r.getString(1),ref.tenantId(),ref.runId(),step.stepId()));
   if(sessions.isEmpty())return reads.execute(a->ThrowingSupplier.of(()->runtime.unknownOriginal(a,ref,step)).get());if(sessions.size()!=1)bad();
   var h=new Handle(reads,ref,step,inputs.readOriginal(reads,step),sessions.get(0));h.recovery=true;var snapshot=reads.execute(a->ThrowingSupplier.of(()->supervisor.snapshotClosure(h.session())).get());
   try{h.closure=supervisor.inspect(snapshot);}catch(java.io.IOException unknown){return reads.execute(a->ThrowingSupplier.of(()->runtime.unknownOriginal(a,ref,step)).get());}catch(TenantAccessException unknown){if(!pending(unknown))throw unknown;return reads.execute(a->ThrowingSupplier.of(()->runtime.unknownOriginal(a,ref,step)).get());}
   reads.execute(a->ThrowingSupplier.of(()->{var current=h.session();runtime.close(current,supervisor.close(current,h.closure));return null;}).get());
   for(String kind:List.of("STEP_ENVELOPE","SESSION_EXPORT","BUSINESS_OUTPUT","STEP_RESULT"))h.transfer(kind);
   return reads.execute(a->ThrowingSupplier.of(()->{var receipt=runtime.finish(h.checked(),h.registered("STEP_ENVELOPE"),h.registered("SESSION_EXPORT"),h.registered("BUSINESS_OUTPUT"),h.registered("STEP_RESULT"));if("SUCCEEDED".equals(receipt.state()))completions.record(a,runtime.requireOriginalCompleted(a,ref.runId(),step.stepId()));return receipt;}).get());
  }).get());
 }
 public JsonNode heartbeat(HttpServletRequest http,FormalEvaluationProtocol.Reference ref,FormalEvaluationProtocol.Step step,String session)throws Exception {
  return access.withReads(http,ref.tenantId(),ref.runId(),reads->ThrowingSupplier.of(()->{var h=new Handle(reads,ref,step,inputs.read(reads,step),session);return reads.execute(a->ThrowingSupplier.of(()->{var s=h.session();if(!"OPEN".equals(s.state()))bad();leases.renew(s.lease());return json.object().put("session_id",session).put("state","CURRENT");}).get());}).get());
 }
 private FormalEvaluationProtocol.NodeReceipt deterministic(FormalEvaluationControlAccess.Reads reads,FormalEvaluationProtocol.Reference ref,FormalEvaluationProtocol.Step step,FormalEvaluationNodeInputs.Verified input,boolean recovery)throws Exception {
  reads.execute(a->ThrowingSupplier.of(()->deterministic.prepare(a,ref,step,input,reads.workerService())).get());
  var transfers=new HashMap<String,DeterministicTransfer>();
  for(String kind:List.of("STEP_ENVELOPE","BUSINESS_OUTPUT","STEP_RESULT")){
   var attempt=reads.execute(a->ThrowingSupplier.of(()->{var proof=deterministic.restore(a,ref,step,input);var intent=deterministicObjects.prepare(a,proof,kind);return deterministicObjects.attempt(a,proof,intent);}).get());
   var actual=transport.transferVerified(attempt,()->reads.execute(a->ThrowingSupplier.of(()->{deterministicObjects.requireTransferCurrent(a,deterministic.restore(a,ref,step,input),attempt);return null;}).get()));transfers.put(kind,new DeterministicTransfer(attempt,actual));
   reads.execute(a->ThrowingSupplier.of(()->deterministicObjects.register(a,deterministic.restore(a,ref,step,input),attempt,actual)).get());
  }
  return reads.execute(a->ThrowingSupplier.of(()->{var proof=deterministic.restore(a,ref,step,input);var registered=new HashMap<String,FormalEvaluationDeterministicObjects.Registered>();for(var entry:transfers.entrySet())registered.put(entry.getKey(),deterministicObjects.register(a,proof,entry.getValue().attempt(),entry.getValue().physical()));var receipt=deterministic.finish(a,proof,registered.get("STEP_ENVELOPE"),registered.get("BUSINESS_OUTPUT"),registered.get("STEP_RESULT"));if("SUCCEEDED".equals(receipt.state()))completions.record(a,runtime.requireOriginalCompleted(a,ref.runId(),step.stepId()));return receipt;}).get());
 }
 private record DeterministicTransfer(FormalEvaluationDeterministicObjects.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical){}
 private final class Handle {
  final FormalEvaluationControlAccess.Reads reads;final FormalEvaluationProtocol.Reference ref;final FormalEvaluationProtocol.Step step;final FormalEvaluationNodeInputs.Verified input;final String sessionId;
  boolean recovery;FormalEvaluationSupervisor.Inspection closure;final Map<String,Transfer> transfers=new HashMap<>();
  Handle(FormalEvaluationControlAccess.Reads reads,FormalEvaluationProtocol.Reference ref,FormalEvaluationProtocol.Step step,FormalEvaluationNodeInputs.Verified input,String sessionId){this.reads=reads;this.ref=ref;this.step=step;this.input=input;this.sessionId=sessionId;}
  EnterpriseAccess.Admission admission(){return reads.execute(a->a);}
  FormalEvaluationNodeRuntime.Envelope envelope()throws Exception{return runtime.requireEnvelope(recovery?leases.requireRecovery(admission(),ref,step,input,reads.workerService()):leases.require(admission(),ref,step,input,reads.workerService()));}
  FormalEvaluationNodeRuntime.Session session()throws Exception{return recovery?runtime.requireRecoverySession(admission(),ref,step,input,reads.workerService(),sessionId):runtime.requireSession(admission(),ref,step,input,reads.workerService(),sessionId);}
  FormalEvaluationSupervisor.Closed closed()throws Exception{return supervisor.requireClosed(session(),closure);}
  FormalEvaluationNodeOutcomes.Checked checked()throws Exception {var s=session();var ids=db.query("SELECT i.invocation_id FROM formal_evaluation_invocation_context c JOIN invocation i ON i.tenant_id=c.tenant_id AND i.context_manifest_id=c.context_manifest_id WHERE c.tenant_id=? AND c.run_id=? AND c.step_id=? AND c.session_id=? ORDER BY c.ordinal DESC LIMIT 1",(r,n)->r.getString(1),ref.tenantId(),ref.runId(),step.stepId(),sessionId);if(ids.size()!=1)bad();return outcomes.check(s,closed(),registered("SESSION_EXPORT"),List.of(invocations.terminal(s,ids.get(0))));}
  FormalEvaluationRuntimeObjects.Attempt attempt(String kind)throws Exception{return switch(kind){case "STEP_ENVELOPE"->{var e=envelope();yield objects.attempt(e,objects.prepareEnvelope(e));}case "SESSION_EXPORT"->{var s=session();var c=closed();yield objects.attempt(s,c,objects.prepareExport(s,c));}case "BUSINESS_OUTPUT"->{var c=checked();yield objects.attempt(c,objects.prepareOutput(c));}case "STEP_RESULT"->{var c=checked();yield objects.attempt(c,objects.prepareResult(c));}default->throw new IllegalArgumentException();};}
  void current(String kind,FormalEvaluationRuntimeObjects.Attempt attempt)throws Exception{switch(kind){case "STEP_ENVELOPE"->objects.requireTransferCurrent(envelope(),attempt);case "SESSION_EXPORT"->objects.requireTransferCurrent(session(),closed(),attempt);case "BUSINESS_OUTPUT","STEP_RESULT"->objects.requireTransferCurrent(checked(),attempt);default->throw new IllegalArgumentException();}}
  void transfer(String kind)throws Exception {var attempt=reads.execute(a->ThrowingSupplier.of(()->attempt(kind)).get());var actual=transport.transferVerified(attempt,()->reads.execute(a->ThrowingSupplier.of(()->{current(kind,attempt);return null;}).get()));transfers.put(kind,new Transfer(attempt,actual));reads.execute(a->ThrowingSupplier.of(()->{registered(kind);return null;}).get());}
  FormalEvaluationRuntimeObjects.Registered registered(String kind)throws Exception {var t=transfers.get(kind);if(t==null)bad();return switch(kind){case "STEP_ENVELOPE"->objects.register(envelope(),t.attempt,t.physical);case "SESSION_EXPORT"->objects.register(session(),closed(),t.attempt,t.physical);case "BUSINESS_OUTPUT","STEP_RESULT"->objects.register(checked(),t.attempt,t.physical);default->throw new IllegalArgumentException();};}
 }
 private record Transfer(FormalEvaluationRuntimeObjects.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical){}
 private static boolean pending(TenantAccessException error){return Set.of(ErrorCode.SOURCE_NOT_READY,ErrorCode.DEPENDENCY_UNAVAILABLE,ErrorCode.OBJECT_UNAVAILABLE).contains(error.code());}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
