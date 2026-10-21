package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;
/** Fresh token/lease transactions around detached exact object/package reads. Handles
 * are thread/lifetime scoped; no Session or admission survives its transaction. */
public final class TaskPlanDraftHostAccess {
 private final RunTokenAuthority tokens;private final TaskPlanDraftControlAccess controls;private final TaskPlanDraftWorkerAccess workers;private final TaskPlanDraftRuntimeAuthority authorities;
 private final TaskPlanDraftObjectLedger ledger;private final TaskPlanDraftObjectRegistration registration;private final RuntimeObjectTransport transport;
 private final TaskPlanDraftPackages packages;private final TaskPlanDraftPromptCompiler compiler;private final TaskPlanDraftRuntimeAdapter runtime;private final TaskPlanDraftDeployment deployment;
 public TaskPlanDraftHostAccess(TaskPlanDraftControlAccess controls,TaskPlanDraftWorkerAccess workers,TaskPlanDraftRuntimeAuthority authorities,TaskPlanDraftObjectLedger ledger,TaskPlanDraftObjectRegistration registration,RuntimeObjectTransport transport,TaskPlanDraftPackages packages,TaskPlanDraftPromptCompiler compiler,TaskPlanDraftRuntimeAdapter runtime,TaskPlanDraftDeployment deployment,RunTokenAuthority tokens){this.tokens=tokens;this.controls=controls;this.workers=workers;this.authorities=authorities;this.ledger=ledger;this.registration=registration;this.transport=transport;this.packages=packages;this.compiler=compiler;this.runtime=runtime;this.deployment=deployment;}
 public <T>T withHost(HttpServletRequest http,String tenant,String run,String originalOperation,String token,RunTokenAuthority.ActionRequest action,Operation<T> operation)throws Exception {
  outside();if(!"HOST_CONTROL".equals(action.kind())||!run.equals(action.runId()))deny();
  return controls.withReads(http,tenant,run,originalOperation,reads->ThrowingSupplier.of(()->{
   var handle=new Handle(http,token,action,reads);try{handle.load();return operation.apply(handle);}finally{handle.active=false;}
  }).get());
 }
 public final class Handle {
  private final HttpServletRequest http;private final String token;private final RunTokenAuthority.ActionRequest action;private final TaskPlanDraftControlAccess.Reads reads;private final Thread thread=Thread.currentThread();private boolean active=true;
  private TaskPlanDraftObjectLedger.Attempt attempt;private RuntimeObjectTransport.VerifiedTransfer transfer;private TaskPlanDraftPackages.Read role,prompt,profile;
  private Handle(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,TaskPlanDraftControlAccess.Reads reads){this.http=http;this.token=token;this.action=action;this.reads=reads;}
  private void require(){if(!active||thread!=Thread.currentThread())deny();reads.requireActive();}
  private <T>T authorized(RuntimeOperation<T> operation)throws Exception {require();outside();return workers.withAction(http,token,action,(a,t,p)->{if(!reads.job().equals(p.job())||!p.job().action().equals(p.actionId()))deny();return operation.apply(p);});}
  private void load()throws Exception {
   attempt=authorized(p->{var i=ledger.prepareInput(p.current().current(),p.job());ledger.requireAttempt(p.current().current(),p.job(),i);return ledger.attemptInput(p.current().current(),p.job(),i);});
   transfer=transport.transferVerified(attempt,()->ThrowingSupplier.of(()->authorized(p->{ledger.requireAttempt(p.current().current(),p.job(),attempt.intent());return null;})).get());
   var roleBasis=authorized(compiler::roleArtifact);role=packages.readControl(reads,reads.job().project(),roleBasis);
   var promptBasis=authorized(p->compiler.promptArtifact(p,role));prompt=packages.readControl(reads,reads.job().project(),promptBasis);
   var profileBasis=authorized(compiler::profileArtifact);profile=packages.readControl(reads,reads.job().project(),profileBasis);
  }
  public void heartbeat()throws Exception{require();outside();tokens.renewStep(http,token,java.time.Duration.ofSeconds(deployment.value().required("lease_lifetime_seconds").longValue()));}
  public TaskPlanDraftControlAccess.Reads reads(){require();return reads;}
  private TaskPlanDraftPromptCompiler.Compiled compiled(TaskPlanDraftRuntimeAuthority.Verified proof)throws Exception {require();var input=registration.register(proof.current(),attempt,transfer);return compiler.compile(proof,input,role,prompt,profile,deployment);}
  private TaskPlanDraftRuntimeAdapter.Envelope envelope(TaskPlanDraftRuntimeAuthority.Verified proof)throws Exception {require();var input=registration.register(proof.current(),attempt,transfer);return runtime.prepare(proof,input,compiler.compile(proof,input,role,prompt,profile,deployment));}
  public <T>T prepare(EnvelopeOperation<T> operation)throws Exception{return authorized(p->operation.apply(envelope(p)));}
  public <T>T execute(SessionOperation<T> operation)throws Exception{return authorized(p->operation.apply(runtime.session(envelope(p))));}
  public TaskPlanDraftObjectRegistration.Registered input(TaskPlanDraftRuntimeAdapter.Session session)throws Exception{require();session.requireUnchanged();return registration.register(session.authority().current(),attempt,transfer);}
  public TaskPlanDraftPromptCompiler.Compiled prompt(TaskPlanDraftRuntimeAdapter.Session session)throws Exception {require();session.requireUnchanged();return compiled(session.authority());}
  public TaskPlanDraftRuntimeAdapter.Session sessionFor(TaskPlanDraftRuntimeAuthority.Verified current)throws Exception {
   require();current.requireUnchanged();if(!reads.job().equals(current.job())||!action.generation().equals(current.leaseGeneration()))deny();
   var worker=current.current().worker();var host=authorities.requireLeased(current.current(),action.actionId(),action.generation(),worker.serviceUri(),worker.audience());return runtime.session(envelope(host));
  }
 }
 @FunctionalInterface public interface Operation<T>{T apply(Handle handle)throws Exception;}
 @FunctionalInterface public interface SessionOperation<T>{T apply(TaskPlanDraftRuntimeAdapter.Session session)throws Exception;}
 @FunctionalInterface public interface EnvelopeOperation<T>{T apply(TaskPlanDraftRuntimeAdapter.Envelope envelope)throws Exception;}
 @FunctionalInterface private interface RuntimeOperation<T>{T apply(TaskPlanDraftRuntimeAuthority.Verified authority)throws Exception;}
 private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())deny();}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
