package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;
/** Reacquires N01 lease/read authority around every external original read, independently
 * observed SDK projection, trusted tokenizer process and exact-input object transfer. */
public final class TaskPlanDraftInvocationContextOrchestrator {
 private final TaskPlanDraftSupervisor supervisors;private final TaskPlanDraftInvocationContext contexts;private final FormalInvocationSourceOriginals originals;
 private final TaskPlanDraftPackages packages;private final FormalTokenizerRunner tokenizer;private final TaskPlanDraftInvocationObjects objects;private final RuntimeObjectTransport transport;private final TaskPlanDraftInvocationRecords records;private final TaskPlanDraftInvocationService invocations;
 public TaskPlanDraftInvocationContextOrchestrator(TaskPlanDraftSupervisor supervisors,TaskPlanDraftInvocationContext contexts,FormalInvocationSourceOriginals originals,TaskPlanDraftPackages packages,FormalTokenizerRunner tokenizer,TaskPlanDraftInvocationObjects objects,RuntimeObjectTransport transport,TaskPlanDraftInvocationRecords records,TaskPlanDraftInvocationService invocations){this.supervisors=supervisors;this.contexts=contexts;this.originals=originals;this.packages=packages;this.tokenizer=tokenizer;this.objects=objects;this.transport=transport;this.records=records;this.invocations=invocations;}
 public TaskPlanDraftInvocationService.BoundAction prepare(TaskPlanDraftHostAccess.Handle handle,String session,String requestId,JsonNode locator)throws Exception {
  WireValues.uuid(session);WireValues.uuid(requestId);var snapshot=handle.execute(s->{same(session,s.sessionId());return supervisors.snapshotModelRequest(s,requestId);});var inspected=supervisors.inspectModelRequest(snapshot);
  var compilation=handle.execute(s->compile(handle,s,session,inspected,locator));String project=handle.execute(s->s.authority().job().project());
  var price=originals.readControl(handle.reads(),compilation.priceBasis(),project);var artifact=compilation.tokenizerArtifact().artifact();var executable=packages.readControl(handle.reads(),project,new PermissionProtocol.Basis(artifact.artifactId(),artifact.sha256()));
  S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->handle.execute(s->{price.requireCurrent(s.admission());executable.requireCurrent(s.admission());if(!Arrays.equals(compilation.bytes(),compile(handle,s,session,inspected,locator).bytes()))bad();return null;})).get();
  guard.requireCurrent();var counted=tokenizer.count(compilation.bytes(),compilation.tokenizerBasis().version_id(),artifact.sha256(),()->{try{guard.requireCurrent();return false;}catch(Exception e){return true;}});guard.requireCurrent();
  var attempt=handle.execute(s->{var p=prepared(handle,s,session,inspected,locator,price,executable,counted);return objects.attempt(p,objects.prepare(p));});
  var transferred=transport.transferVerified(attempt,()->ThrowingSupplier.of(()->handle.execute(s->{objects.requireAttempt(prepared(handle,s,session,inspected,locator,price,executable,counted),attempt);return null;})).get());
  return handle.execute(s->{var p=prepared(handle,s,session,inspected,locator,price,executable,counted);var registered=objects.register(p,attempt,transferred);return invocations.bindAction(s,records.register(p,registered));});
 }
 public TaskPlanDraftInvocationService.BoundAction bindExisting(TaskPlanDraftHostAccess.Handle handle,String session,String context)throws Exception {return handle.execute(s->{same(session,s.sessionId());return invocations.bindAction(s,records.requireStored(s.authority().job().tenant(),context));});}
 private TaskPlanDraftInvocationContext.Compilation compile(TaskPlanDraftHostAccess.Handle h,TaskPlanDraftRuntimeAdapter.Session s,String session,TaskPlanDraftSupervisor.ModelInspection inspection,JsonNode locator)throws Exception {same(session,s.sessionId());var observed=supervisors.requireModelRequest(s,inspection);check(observed,locator);return contexts.compile(s,supervisors.requireAdmitted(s),observed,h.prompt(s));}
 private TaskPlanDraftInvocationContext.Prepared prepared(TaskPlanDraftHostAccess.Handle h,TaskPlanDraftRuntimeAdapter.Session s,String session,TaskPlanDraftSupervisor.ModelInspection inspection,JsonNode locator,FormalInvocationSourceOriginals.Read price,TaskPlanDraftPackages.Read executable,FormalTokenizerRunner.Result counted)throws Exception {same(session,s.sessionId());var observed=supervisors.requireModelRequest(s,inspection);check(observed,locator);return contexts.prepare(s,supervisors.requireAdmitted(s),observed,h.prompt(s),price,executable,counted);}
 private static void check(TaskPlanDraftSupervisor.ModelRequest request,JsonNode locator)throws Exception {request.requireUnchanged();if(locator==null||!request.requestId().equals(locator.required("request_id").textValue())||request.ordinal()!=locator.required("ordinal").longValue()||!request.receiptId().equals(locator.required("receipt_id").textValue())||!request.sha256().equals(locator.required("sha256").textValue()))bad();}
 private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
