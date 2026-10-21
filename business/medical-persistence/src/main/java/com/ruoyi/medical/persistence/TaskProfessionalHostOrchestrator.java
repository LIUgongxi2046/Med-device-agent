package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.util.function.ThrowingSupplier;

/** Real prepared-object and independent admission orchestration. Every SQL segment obtains a
 * fresh formal admission, and every object/supervisor I/O runs outside its transaction. */
public final class TaskProfessionalHostOrchestrator {
 private final TaskProfessionalHostAccess hosts;private final TaskProfessionalRuntimeService runtime;private final TaskProfessionalPromptCompiler prompts;
 private final TaskInvocationPackages packages;private final TaskNodeProducerAuthority producers;private final TaskProfessionalObjectLedger ledger;
 private final RuntimeObjectTransport objects;private final TaskProfessionalObjectRegistration registration;private final TaskProfessionalSupervisor supervisor;private final TaskRuntimeJournal journal;private final PermissionSchema json;
 public TaskProfessionalHostOrchestrator(TaskProfessionalHostAccess hosts,TaskProfessionalRuntimeService runtime,TaskProfessionalPromptCompiler prompts,TaskInvocationPackages packages,TaskNodeProducerAuthority producers,TaskProfessionalObjectLedger ledger,RuntimeObjectTransport objects,TaskProfessionalObjectRegistration registration,TaskProfessionalSupervisor supervisor,TaskRuntimeJournal journal,PermissionSchema json){this.hosts=hosts;this.runtime=runtime;this.prompts=prompts;this.packages=packages;this.producers=producers;this.ledger=ledger;this.objects=objects;this.registration=registration;this.supervisor=supervisor;this.journal=Objects.requireNonNull(journal);this.json=json;}
 public PreparedLaunch prepare(TaskProfessionalHostAccess.Handle handle,String commandId)throws Exception {
  WireValues.uuid(commandId);var input=hosts.consumeInput(handle);
  var rolePin=hosts.execute(handle,a->ThrowingSupplier.of(()->prompts.roleArtifact(hosts.envelope(handle,a))).get());
  var project=hosts.execute(handle,a->a.formalProof().identity().project());var role=packages.readWorker(hosts,handle,project,rolePin);
  var promptPin=hosts.execute(handle,a->ThrowingSupplier.of(()->prompts.promptArtifact(hosts.envelope(handle,a),role)).get());
  var profilePin=hosts.execute(handle,a->ThrowingSupplier.of(()->prompts.profileArtifact(hosts.envelope(handle,a))).get());
  var prompt=packages.readWorker(hosts,handle,project,promptPin);var profile=packages.readWorker(hosts,handle,project,profilePin);
  var evidence=prompts.readPredecessors(hosts,handle,prompt,profile);var fixed=new Content(input,role,prompt,profile,evidence);
  var attempt=hosts.execute(handle,a->ThrowingSupplier.of(()->{var compiled=compile(handle,a,fixed);var producer=producers.requireLeased(a,compiled.envelope.input());var intent=ledger.prepare(producer,compiled.envelope);return ledger.attempt(producer,compiled.envelope,intent);}).get());
  S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{var compiled=compile(handle,a,fixed);var producer=producers.requireLeased(a,compiled.envelope.input());registration.requireTransferCurrent(producer,compiled.envelope,attempt);return null;}).get());return null;}).get();
  var transfer=objects.transferVerified(attempt,guard);
  var launch=hosts.execute(handle,a->ThrowingSupplier.of(()->{var compiled=compile(handle,a,fixed);var producer=producers.requireLeased(a,compiled.envelope.input());var registered=registration.register(producer,compiled.envelope,attempt,transfer);journal.prepareStep(compiled.envelope,registered,commandId).requireUnchanged();return runtime.prepareLaunch(compiled.envelope,registered,input,compiled.prompt);}).get());
  return new PreparedLaunch(this,handle,fixed,attempt,transfer,launch,commandId);
 }
 public JsonNode admit(HttpServletRequest http,TaskProfessionalHostAccess.Handle handle,String expectedSession,String commandId)throws Exception {
  WireValues.uuid(expectedSession);var prepared=prepare(handle,commandId);if(!expectedSession.equals(prepared.launch.required("session_id").textValue()))bad();
  var request=hosts.execute(handle,a->ThrowingSupplier.of(()->supervisor.snapshotAdmission(compile(handle,a,prepared.content).envelope)).get());
  var inspection=supervisor.inspect(request);
  return hosts.execute(handle,a->ThrowingSupplier.of(()->{
   var compiled=compile(handle,a,prepared.content);var producer=producers.requireLeased(a,compiled.envelope.input());var registered=registration.readVerified(producer,compiled.envelope,prepared.attempt,prepared.transfer);journal.requireStep(compiled.envelope,registered,commandId).requireUnchanged();
   var admitted=supervisor.admit(compiled.envelope,inspection);var session=runtime.registerSession(http,compiled.envelope,registered,prepared.content.input,compiled.prompt,admitted);registration.bindAdmitted(producer,compiled.envelope,registered,session,admitted);
   var reply=(com.fasterxml.jackson.databind.node.ObjectNode)runtime.heartbeat(session,admitted);reply.put("isolation_receipt_id",admitted.receiptId()).put("supervision_sha256",admitted.sha256());return reply;
  }).get());
 }
 public JsonNode heartbeat(TaskProfessionalHostAccess.Handle handle,String session)throws Exception{return hosts.execute(handle,a->ThrowingSupplier.of(()->{var current=hosts.session(handle,a,session);return runtime.heartbeat(current,supervisor.requireAdmitted(current));}).get());}
 private Compiled compile(TaskProfessionalHostAccess.Handle handle,EnterpriseAccess.Admission a,Content content)throws Exception{var metadata=hosts.envelope(handle,a);var prompt=content.evidence==null?prompts.compile(metadata,content.input,content.role,content.prompt,content.profile):prompts.compile(metadata,content.input,content.role,content.prompt,content.profile,content.evidence);var envelope=runtime.compileEnvelope(a,metadata.input(),prompt);envelope.requireProductionReady();return new Compiled(envelope,prompt);}
 private record Content(TaskProfessionalHostAccess.InputContent input,TaskInvocationPackages.Read role,TaskInvocationPackages.Read prompt,TaskInvocationPackages.Read profile,TaskProfessionalPredecessorEvidence.Reads evidence){}
 private record Compiled(TaskProfessionalRuntimeService.Envelope envelope,TaskProfessionalPromptCompiler.CompiledPrompt prompt){}
 public static final class PreparedLaunch {
  private final TaskProfessionalHostOrchestrator owner;private final TaskProfessionalHostAccess.Handle handle;private final Content content;private final TaskProfessionalObjectLedger.Attempt attempt;private final RuntimeObjectTransport.VerifiedTransfer transfer;private final JsonNode launch;private final String commandId;
  private PreparedLaunch(TaskProfessionalHostOrchestrator owner,TaskProfessionalHostAccess.Handle handle,Content content,TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer transfer,JsonNode launch,String commandId){this.owner=owner;this.handle=handle;this.content=content;this.attempt=attempt;this.transfer=transfer;this.launch=launch.deepCopy();this.commandId=commandId;}
  public JsonNode launch()throws Exception{return owner.hosts.execute(handle,a->ThrowingSupplier.of(()->{var c=owner.compile(handle,a,content);var p=owner.producers.requireLeased(a,c.envelope.input());var r=owner.registration.readVerified(p,c.envelope,attempt,transfer);owner.journal.requireStep(c.envelope,r,commandId).requireUnchanged();var now=owner.runtime.prepareLaunch(c.envelope,r,content.input,c.prompt);if(!FormalTaskNodeAuthority.sameEvidence(now,launch))bad();return now;}).get());}
  public com.ruoyi.medical.workflow.WorkflowProtocol.Dispatch dispatch()throws Exception{return owner.hosts.execute(handle,a->ThrowingSupplier.of(()->{var c=owner.compile(handle,a,content);var p=owner.producers.requireLeased(a,c.envelope.input());var r=owner.registration.readVerified(p,c.envelope,attempt,transfer);return owner.journal.requireStep(c.envelope,r,commandId).dispatch();}).get());}
 }
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
