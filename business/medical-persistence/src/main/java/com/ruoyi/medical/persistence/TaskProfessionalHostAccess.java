package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Host-owned action handle. Each SQL/read segment re-enters actual mTLS/token/lease authority.
 * The detached input content below exists only after real exact-version physical readback. */
public final class TaskProfessionalHostAccess {
 private final FormalWorkerAccess workers;private final TaskNodeInputAuthority inputs;private final TaskNodeProducerAuthority producers;
 private final TaskProfessionalRuntimeService runtime;private final SourceUploadTransport transport;private final int maximum;
 public TaskProfessionalHostAccess(FormalWorkerAccess workers,TaskNodeInputAuthority inputs,TaskNodeProducerAuthority producers,TaskProfessionalRuntimeService runtime,SourceUploadTransport transport,TaskProfessionalRuntimeService.Deployment deployment){this.workers=workers;this.inputs=inputs;this.producers=producers;this.runtime=runtime;this.transport=transport;maximum=deployment.maximumBytes();}
 public Handle open(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action)throws Exception {
  outside();if(!TaskProfessionalRuntimeService.HOST_ACTION.equals(action.kind()))deny();
  Snapshot fixed=workers.executeAction(http,token,action,(a,t)->ThrowingSupplier.of(()->snapshot(a)).get());return new Handle(http,token,action,fixed);
 }
 private Snapshot snapshot(EnterpriseAccess.Admission a)throws Exception {
  var i=a.formalProof().identity();var input=inputs.require(a,i.run(),i.step());var authority=producers.requireLeased(a,input);authority.requireHostInputConsume(input);
  var reference=input.objectReference();if(Long.parseLong(reference.sizeBytes())>maximum)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  return new Snapshot(i.tenant(),i.project(),i.actor(),i.run(),i.step(),i.action(),i.generation(),i.workerService(),input.inputId(),input.inputSha256(),input.receiptSha256(),input.manifest(),reference);
 }
 public InputContent consumeInput(Handle handle)throws Exception {
  outside();Snapshot initial=execute(handle,a->ThrowingSupplier.of(()->snapshot(a)).get());
  S3ObjectReader.Admission current=()->ThrowingSupplier.of(()->{Snapshot next=execute(handle,a->ThrowingSupplier.of(()->snapshot(a)).get());if(!initial.equals(next))stale();return null;}).get();
  byte[] bytes=transport.readTaskDocument(initial.tenant,initial.actor,maximum,current,(reader,guard)->{
   try(var object=reader.readVerified(initial.object,guard)){
    if(!object.reference().equals(initial.object))bad();int length=Integer.parseInt(initial.object.sizeBytes());if(length<1||length>maximum)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);byte[] result=new byte[length];
    for(int start=0;start<length;){guard.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,length-start);System.arraycopy(object.chunk(start,count),0,result,start,count);start+=count;guard.requireCurrent();}
    if(!WireValues.sha256(result).equals(initial.hash)||!object.fullSha256().equals(initial.hash)||!Arrays.equals(result,ContentReadAuthorization.canonicalBytes(initial.manifest)))bad();guard.requireCurrent();return result;
   }
  });current.requireCurrent();return new InputContent(this,initial,bytes);
 }
 public <T>T execute(Handle handle,Function<EnterpriseAccess.Admission,T> operation)throws Exception {
  outside();if(handle==null||handle.owner!=this)deny();return workers.executeAction(handle.http,handle.token,handle.action,(a,t)->ThrowingSupplier.of(()->{
   Snapshot fresh=snapshot(a);if(!handle.fixed.equals(fresh))stale();T result=operation.apply(a);if(!fresh.equals(snapshot(a)))stale();return result;
  }).get());
 }
 public TaskProfessionalRuntimeService.Envelope envelope(Handle handle,EnterpriseAccess.Admission a)throws Exception {if(handle==null||handle.owner!=this)deny();var input=inputs.require(a,handle.fixed.run,handle.fixed.step);return runtime.compileEnvelope(a,input);}
 public TaskProfessionalRuntimeService.Session session(Handle handle,EnterpriseAccess.Admission a,String session)throws Exception {if(handle==null||handle.owner!=this)deny();return runtime.requireSession(a,inputs.require(a,handle.fixed.run,handle.fixed.step),session);}
 public final class Handle {private final TaskProfessionalHostAccess owner=TaskProfessionalHostAccess.this;private final HttpServletRequest http;private final String token;private final RunTokenAuthority.ActionRequest action;private final Snapshot fixed;private Handle(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,Snapshot fixed){this.http=http;this.token=token;this.action=action;this.fixed=fixed;}}
 private record Snapshot(String tenant,String project,String actor,String run,String step,String action,String generation,String worker,String input,String hash,String receipt,JsonNode manifest,InterfaceProtocol.ObjectRef object){}
 public static final class InputContent {
  private final TaskProfessionalHostAccess owner;private final Snapshot original;private final byte[] bytes;
  private InputContent(TaskProfessionalHostAccess owner,Snapshot original,byte[] bytes){this.owner=owner;this.original=original;this.bytes=bytes.clone();}
  void requireFor(TaskProfessionalRuntimeService.Envelope e)throws Exception {
   e.requireUnchanged();var a=e.admission();if(!owner.snapshot(a).equals(original)||!e.runId().equals(original.run)||!e.stepId().equals(original.step)||!e.actionId().equals(original.action)||!e.leaseGeneration().equals(original.generation)||!e.workerService().equals(original.worker)||!e.inputId().equals(original.input)||!e.inputSha256().equals(original.hash)||!WireValues.sha256(bytes).equals(original.hash))bad();
  }
  byte[] bytes(TaskProfessionalRuntimeService.Envelope e)throws Exception{requireFor(e);return bytes.clone();}
 }
 private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())deny();}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
