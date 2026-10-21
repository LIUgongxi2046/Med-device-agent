package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;
/** Detached N07 read handle. Every segment reconstructs real signed SYSTEM_JOB authority. */
public final class DocumentReviewHostAccess {
 private final RunTokenAuthority tokens;private final DocumentReviewControlAccess controls;private final DocumentReviewWorkerAccess workers;private final DocumentReviewRuntimeAuthority authorities;
 private final TaskInvocationPackages packages;private final DocumentReviewPromptCompiler compiler;private final DocumentReviewRuntimeAdapter runtime;private final DocumentReviewDeployment deployment;private final DocumentReviewContextReads contextReads;
 public DocumentReviewHostAccess(DocumentReviewControlAccess controls,DocumentReviewWorkerAccess workers,DocumentReviewRuntimeAuthority authorities,TaskInvocationPackages packages,DocumentReviewPromptCompiler compiler,DocumentReviewRuntimeAdapter runtime,DocumentReviewDeployment deployment,RunTokenAuthority tokens,DocumentReviewContextReads contextReads){this.controls=controls;this.workers=workers;this.authorities=authorities;this.packages=packages;this.compiler=compiler;this.runtime=runtime;this.deployment=deployment;this.tokens=tokens;this.contextReads=contextReads;}
 public <T>T withHost(HttpServletRequest http,String tenant,String run,String originalOperation,String token,RunTokenAuthority.ActionRequest action,Operation<T> operation)throws Exception{
  outside();if(!"HOST_CONTROL".equals(action.kind())||!run.equals(action.runId()))deny();return controls.withReads(http,tenant,run,originalOperation,reads->ThrowingSupplier.of(()->{var handle=new Handle(http,token,action,reads);try{handle.load();return operation.apply(handle);}finally{handle.active=false;}}).get());
 }
 public <T>T authorized(Handle handle,RuntimeOperation<T> operation)throws Exception{if(handle==null||handle.owner!=this)deny();return handle.authorized(operation);}
 public <T>T execute(Handle handle,Function<EnterpriseAccess.Admission,T> operation)throws Exception{return authorized(handle,r->operation.apply(r.admission()));}
 public final class Handle {
  private final DocumentReviewHostAccess owner=DocumentReviewHostAccess.this;private final HttpServletRequest http;private final String token;private final RunTokenAuthority.ActionRequest action;private final DocumentReviewControlAccess.Reads reads;private final Thread thread=Thread.currentThread();private boolean active=true;
  private DocumentReviewContextReads.Reads content;private TaskInvocationPackages.Read role,prompt,profile;
  private Handle(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,DocumentReviewControlAccess.Reads reads){this.http=http;this.token=token;this.action=action;this.reads=reads;}
  private void require(){if(!active||thread!=Thread.currentThread())deny();reads.requireActive();}
  private <T>T authorized(RuntimeOperation<T> operation)throws Exception{require();outside();return workers.withAction(http,token,action,(a,t,p)->{if(!reads.job().equals(p.job())||!p.job().action().equals(p.actionId()))deny();var result=operation.apply(p);p.requireUnchanged();return result;});}
  private void load()throws Exception{content=contextReads.read(owner,this);String project=reads.job().project();role=packages.readWorker(owner,this,project,authorized(compiler::roleArtifact));prompt=packages.readWorker(owner,this,project,authorized(p->compiler.promptArtifact(p,role)));profile=packages.readWorker(owner,this,project,authorized(compiler::profileArtifact));}
  public DocumentReviewControlAccess.Reads reads(){require();return reads;}
  public void heartbeat()throws Exception{require();outside();tokens.renewStep(http,token,java.time.Duration.ofSeconds(deployment.value().required("lease_lifetime_seconds").longValue()));}
  private DocumentReviewPromptCompiler.Compiled compiled(DocumentReviewRuntimeAuthority.Verified p)throws Exception{require();return compiler.compile(p,role,prompt,profile,deployment,content);}
  private DocumentReviewRuntimeAdapter.Envelope envelope(DocumentReviewRuntimeAuthority.Verified p)throws Exception{return runtime.prepare(p,compiled(p));}
  public <T>T prepare(EnvelopeOperation<T> operation)throws Exception{return authorized(p->operation.apply(envelope(p)));}
  public <T>T execute(SessionOperation<T> operation)throws Exception{return authorized(p->operation.apply(runtime.session(envelope(p))));}
  public DocumentReviewPromptCompiler.Compiled prompt(DocumentReviewRuntimeAdapter.Session session)throws Exception{require();session.requireUnchanged();return compiled(session.authority());}
  public DocumentReviewContextReads.Reads content(){require();return content;}
  public DocumentReviewRuntimeAdapter.Session sessionFor(DocumentReviewRuntimeAuthority.Verified current)throws Exception{
   require();current.requireUnchanged();if(!reads.job().equals(current.job())||!action.generation().equals(current.leaseGeneration()))deny();var worker=current.current().worker();var host=authorities.requireLeased(current.current(),action.actionId(),action.generation(),worker.serviceUri(),worker.audience()).bindAdmission(current.admission());return runtime.session(envelope(host));
  }
 }
 @FunctionalInterface public interface RuntimeOperation<T>{T apply(DocumentReviewRuntimeAuthority.Verified authority)throws Exception;}
 @FunctionalInterface public interface Operation<T>{T apply(Handle handle)throws Exception;}
 @FunctionalInterface public interface SessionOperation<T>{T apply(DocumentReviewRuntimeAdapter.Session session)throws Exception;}
 @FunctionalInterface public interface EnvelopeOperation<T>{T apply(DocumentReviewRuntimeAdapter.Envelope envelope)throws Exception;}
 private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())deny();}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
