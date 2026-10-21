package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionSynchronizationManager;
/** Detached host handle; every segment reconstructs signed TASK token and real N05 input. */
public final class TaskSourceVerifierHostAccess {
 private final TaskSourceVerifierProducerAuthority producers;private final TaskSourceVerifierContextReads contextReads;private final RunTokenAuthority tokens;private final TaskSourceVerifierWorkerAccess workers;private final SourceUploadTransport transport;private final int maximum;private final TaskInvocationPackages packages;private final TaskSourceVerifierPromptCompiler compiler;private final TaskSourceVerifierRuntimeAdapter runtime;private final TaskSourceVerifierDeployment deployment;
 public TaskSourceVerifierHostAccess(TaskSourceVerifierWorkerAccess workers,SourceUploadTransport transport,TaskSourceVerifierDeployment deployment,TaskInvocationPackages packages,TaskSourceVerifierPromptCompiler compiler,TaskSourceVerifierRuntimeAdapter runtime,RunTokenAuthority tokens,TaskSourceVerifierContextReads contextReads,TaskSourceVerifierProducerAuthority producers){this.producers=producers;this.contextReads=contextReads;this.tokens=tokens;this.deployment=deployment;this.packages=packages;this.compiler=compiler;this.runtime=runtime;this.workers=workers;this.transport=transport;maximum=deployment.maximumBytes();}
 public Handle open(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action)throws Exception{
  outside();if(!"HOST_CONTROL".equals(action.kind()))deny();var fixed=workers.withAction(http,token,action,(a,t,r)->snapshot(r));var handle=new Handle(http,token,action,fixed);handle.load();return handle;
 }
 private String snapshot(TaskSourceVerifierRuntimeAuthority.Verified r)throws Exception{r.requireUnchanged();producers.require(r.input(),r.admission()).requireHostInputConsume();return WireValues.sha256(ContentReadAuthorization.canonicalBytes(Map.of("job",r.job(),"action",r.actionId(),"generation",r.leaseGeneration(),"service",r.workerService(),"receipt",r.input().receiptSha256())));}
 public <T>T execute(Handle h,Function<EnterpriseAccess.Admission,T> operation)throws Exception{return authorized(h,r->operation.apply(r.admission()));}
 public <T>T authorized(Handle h,Operation<T> operation)throws Exception{outside();if(h==null||h.owner!=this)deny();return workers.withAction(h.http,h.token,h.action,(a,t,r)->{if(!h.fixed.equals(snapshot(r)))stale();var result=operation.apply(r);if(!h.fixed.equals(snapshot(r)))stale();return result;});}
 public InputContent consumeInput(Handle h)throws Exception{
  outside();var initial=authorized(h,r->new Physical(r.job().tenant(),r.job().actor(),r.input().objectReference(),r.inputSha256()));
  S3ObjectReader.Admission guard=()->{try{authorized(h,r->{if(!initial.equals(new Physical(r.job().tenant(),r.job().actor(),r.input().objectReference(),r.inputSha256())))stale();return null;});}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}};
  byte[] bytes=transport.readTaskDocument(initial.tenant,initial.actor,maximum,guard,(reader,current)->{try(var object=reader.readVerified(initial.reference,current)){int size=Integer.parseInt(initial.reference.sizeBytes());if(size<1||size>maximum)deny();byte[] value=new byte[size];for(int offset=0;offset<size;){current.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);System.arraycopy(object.chunk(offset,count),0,value,offset,count);offset+=count;}if(!initial.hash.equals(WireValues.sha256(value))||!initial.hash.equals(object.fullSha256()))deny();return value;}});guard.requireCurrent();return new InputContent(initial,bytes);
 }
 public final class Handle{
  private final TaskSourceVerifierHostAccess owner=TaskSourceVerifierHostAccess.this;private final HttpServletRequest http;private final String token,fixed;private final RunTokenAuthority.ActionRequest action;
  private TaskSourceVerifierContextReads.Reads readContent;private InputContent content;private TaskInvocationPackages.Read role,prompt,profile;
  private Handle(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,String fixed){this.http=http;this.token=token;this.action=action;this.fixed=fixed;}
  private void load()throws Exception{content=consumeInput(this);readContent=contextReads.read(owner,this);String project=authorized(this,r->r.job().project());role=packages.readWorker(owner,this,project,authorized(this,compiler::roleArtifact));prompt=packages.readWorker(owner,this,project,authorized(this,r->compiler.promptArtifact(r,role)));profile=packages.readWorker(owner,this,project,authorized(this,compiler::profileArtifact));}
  public void heartbeat()throws Exception{authorized(this,r->null);tokens.renewStep(http,token,java.time.Duration.ofSeconds(deployment.value().required("lease_lifetime_seconds").longValue()));}
  public TaskSourceVerifierPromptCompiler.Compiled prompt(TaskSourceVerifierRuntimeAdapter.Session session)throws Exception{return compiler.compile(session.authority(),content,role,prompt,profile,deployment,readContent);}
  private TaskSourceVerifierRuntimeAdapter.Envelope envelope(TaskSourceVerifierRuntimeAuthority.Verified r)throws Exception{return runtime.prepare(r,r.input(),compiler.compile(r,content,role,prompt,profile,deployment,readContent));}
  public <T>T prepare(EnvelopeOperation<T> operation)throws Exception{return authorized(this,r->operation.apply(envelope(r)));}
  public <T>T execute(SessionOperation<T> operation)throws Exception{return authorized(this,r->operation.apply(runtime.session(envelope(r))));}
 }
 @FunctionalInterface public interface EnvelopeOperation<T>{T apply(TaskSourceVerifierRuntimeAdapter.Envelope envelope)throws Exception;}
 @FunctionalInterface public interface SessionOperation<T>{T apply(TaskSourceVerifierRuntimeAdapter.Session session)throws Exception;}
 private record Physical(String tenant,String actor,InterfaceProtocol.ObjectRef reference,String hash){}
 public static final class InputContent{
  private final Physical physical;private final byte[] bytes;
  private InputContent(Physical physical,byte[] bytes){this.physical=physical;this.bytes=bytes.clone();}
  public byte[] bytes(TaskSourceVerifierRuntimeAuthority.Verified r)throws Exception{r.requireUnchanged();if(!physical.equals(new Physical(r.job().tenant(),r.job().actor(),r.input().objectReference(),r.inputSha256()))||!Arrays.equals(bytes,ContentReadAuthorization.canonicalBytes(r.input().manifest())))deny();return bytes.clone();}
 }
 @FunctionalInterface public interface Operation<T>{T apply(TaskSourceVerifierRuntimeAuthority.Verified runtime)throws Exception;}
 private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())deny();}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
