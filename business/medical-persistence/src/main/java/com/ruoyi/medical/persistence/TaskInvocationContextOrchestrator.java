package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;

/** Actual source/package/input reads and tokenizer execution occur outside SQL transactions.
 * Each proof/intent/physical segment re-enters the original HOST_CONTROL admission. The
 * returned BoundAction is not a model token; issueAndPrepare independently narrows MODEL_CALL. */
public final class TaskInvocationContextOrchestrator {
    private final TaskProfessionalHostAccess hosts;private final TaskProfessionalPromptCompiler prompts;private final TaskInvocationPackages packages;
    private final TaskProfessionalSupervisor supervisor;private final TaskInvocationContext contexts;private final FormalInvocationSourceOriginals originals;
    private final FormalTokenizerRunner tokenizer;private final TaskNodeProducerAuthority producers;private final TaskProfessionalObjectLedger ledger;
    private final TaskProfessionalObjectRegistration registration;private final RuntimeObjectTransport transport;private final TaskInvocationService invocations;
    public TaskInvocationContextOrchestrator(TaskProfessionalHostAccess hosts,TaskProfessionalPromptCompiler prompts,TaskInvocationPackages packages,
            TaskProfessionalSupervisor supervisor,TaskInvocationContext contexts,FormalInvocationSourceOriginals originals,FormalTokenizerRunner tokenizer,
            TaskNodeProducerAuthority producers,TaskProfessionalObjectLedger ledger,TaskProfessionalObjectRegistration registration,
            RuntimeObjectTransport transport,TaskInvocationService invocations){this.hosts=hosts;this.prompts=prompts;this.packages=packages;this.supervisor=supervisor;this.contexts=contexts;this.originals=originals;this.tokenizer=tokenizer;this.producers=producers;this.ledger=ledger;this.registration=registration;this.transport=transport;this.invocations=invocations;}
    public TaskInvocationService.BoundAction prepare(TaskProfessionalHostAccess.Handle handle,String sessionId,String observedRequestId)throws Exception {return prepare(handle,sessionId,observedRequestId,null);}
    public TaskInvocationService.BoundAction prepare(TaskProfessionalHostAccess.Handle handle,String sessionId,String observedRequestId,com.fasterxml.jackson.databind.JsonNode locator)throws Exception {
        WireValues.uuid(sessionId);WireValues.uuid(observedRequestId);var input=hosts.consumeInput(handle);
        String project=hosts.execute(handle,a->a.formalProof().identity().project());
        var rolePin=hosts.execute(handle,a->ThrowingSupplier.of(()->prompts.roleArtifact(hosts.envelope(handle,a))).get());
        var role=packages.readWorker(hosts,handle,project,rolePin);
        var promptPin=hosts.execute(handle,a->ThrowingSupplier.of(()->prompts.promptArtifact(hosts.envelope(handle,a),role)).get());
        var profilePin=hosts.execute(handle,a->ThrowingSupplier.of(()->prompts.profileArtifact(hosts.envelope(handle,a))).get());
        var prompt=packages.readWorker(hosts,handle,project,promptPin);var profile=packages.readWorker(hosts,handle,project,profilePin);
        var evidence=prompts.readPredecessors(hosts,handle,prompt,profile);
        var snapshot=hosts.execute(handle,a->ThrowingSupplier.of(()->supervisor.snapshotModelRequest(hosts.session(handle,a,sessionId),observedRequestId)).get());
        var inspection=supervisor.inspectModelRequest(snapshot);var content=new Content(input,role,prompt,profile,evidence,inspection,locator==null?null:locator.deepCopy());
        var compilation=hosts.execute(handle,a->ThrowingSupplier.of(()->compile(handle,a,sessionId,content)).get());
        var price=originals.readWorker(hosts,handle,compilation.priceBasis(),project);
        var executable=compilation.tokenizerArtifact().artifact();var executableRead=packages.readWorker(hosts,handle,project,new PermissionProtocol.Basis(executable.artifactId(),executable.sha256()));
        Runnable guard=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{price.requireCurrent(a);executableRead.requireCurrent(a);if(!Arrays.equals(compilation.bytes(),compile(handle,a,sessionId,content).bytes()))stale();return null;}).get());return null;}).get();
        guard.run();var counted=tokenizer.count(compilation.bytes(),compilation.tokenizerBasis().version_id(),executable.sha256(),()->{try{guard.run();return false;}catch(RuntimeException denied){return true;}});guard.run();
        var fixed=new Physical(content,price,executableRead,counted);
        var attempt=hosts.execute(handle,a->ThrowingSupplier.of(()->{var prepared=prepare(handle,a,sessionId,fixed);var authority=producers.requireLeased(a,prepared.input());var intent=ledger.prepare(authority,prepared);return ledger.attempt(authority,prepared,intent);}).get());
        S3ObjectReader.Admission current=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{var prepared=prepare(handle,a,sessionId,fixed);registration.requireTransferCurrent(producers.requireLeased(a,prepared.input()),prepared,attempt);return null;}).get());return null;}).get();
        var transferred=transport.transferVerified(attempt,current);
        return hosts.execute(handle,a->ThrowingSupplier.of(()->{var prepared=prepare(handle,a,sessionId,fixed);var authority=producers.requireLeased(a,prepared.input());var registered=registration.register(authority,prepared,attempt,transferred);var context=contexts.register(prepared,registered);return invocations.bindAction(prepared.session(),prepared.supervision(),context);}).get());
    }
    public TaskInvocationService.BoundAction bindExisting(TaskProfessionalHostAccess.Handle handle,String sessionId,String contextId)throws Exception {
        return hosts.execute(handle,a->ThrowingSupplier.of(()->{var session=hosts.session(handle,a,sessionId);return invocations.bindAction(session,supervisor.requireAdmitted(session),contexts.requireCurrent(a,contextId));}).get());
    }
    private TaskInvocationContext.Compilation compile(TaskProfessionalHostAccess.Handle handle,EnterpriseAccess.Admission a,String session,Content content)throws Exception {
        var envelope=hosts.envelope(handle,a);var prompt=content.evidence==null?prompts.compile(envelope,content.input,content.role,content.prompt,content.profile):prompts.compile(envelope,content.input,content.role,content.prompt,content.profile,content.evidence);var current=hosts.session(handle,a,session);
        var request=supervisor.requireModelRequest(current,content.inspection);checkLocator(request,content.locator);return contexts.compile(current,supervisor.requireAdmitted(current),request,prompt);
    }
    private TaskInvocationContext.Prepared prepare(TaskProfessionalHostAccess.Handle handle,EnterpriseAccess.Admission a,String session,Physical content)throws Exception {
        var envelope=hosts.envelope(handle,a);var fixed=content.content;var prompt=fixed.evidence==null?prompts.compile(envelope,fixed.input,fixed.role,fixed.prompt,fixed.profile):prompts.compile(envelope,fixed.input,fixed.role,fixed.prompt,fixed.profile,fixed.evidence);var current=hosts.session(handle,a,session);
        var request=supervisor.requireModelRequest(current,fixed.inspection);checkLocator(request,fixed.locator);return contexts.prepare(current,supervisor.requireAdmitted(current),request,prompt,content.price,content.executable,content.counted);
    }
    private record Content(TaskProfessionalHostAccess.InputContent input,TaskInvocationPackages.Read role,TaskInvocationPackages.Read prompt,TaskInvocationPackages.Read profile,TaskProfessionalPredecessorEvidence.Reads evidence,TaskProfessionalSupervisor.ModelInspection inspection,com.fasterxml.jackson.databind.JsonNode locator){}
    private record Physical(Content content,FormalInvocationSourceOriginals.Read price,TaskInvocationPackages.Read executable,FormalTokenizerRunner.Result counted){}
    private static void checkLocator(TaskProfessionalSupervisor.ModelRequest proof,com.fasterxml.jackson.databind.JsonNode locator)throws Exception {
        proof.requireUnchanged();if(locator!=null&&(!proof.requestId().equals(locator.required("request_id").textValue())||proof.ordinal()!=locator.required("ordinal").longValue()||!proof.receiptId().equals(locator.required("receipt_id").textValue())||!proof.sha256().equals(locator.required("sha256").textValue())))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }
    private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
