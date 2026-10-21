package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;

/** FINISH records an observation, independently rereads closure/export, and registers original
 * typed outputs. It returns Completion only; the distinct workflow COMMIT owns step transition. */
public final class TaskResearchFinishOrchestrator {
    private final TaskProfessionalHostAccess hosts;private final ProfessionalRuntimeObservations observations;
    private final TaskProfessionalSupervisor supervisor;private final TaskInvocationService invocations;
    private final TaskResearchExecutionService outputs;private final TaskResearchCompletionEvaluation evaluator;
    private final TaskNodeProducerAuthority producers;private final TaskProfessionalObjectLedger ledger;
    private final RuntimeObjectTransport transport;private final TaskProfessionalObjectRegistration registrations;private final PermissionSchema json;
    public TaskResearchFinishOrchestrator(TaskProfessionalHostAccess hosts,ProfessionalRuntimeObservations observations,TaskProfessionalSupervisor supervisor,TaskInvocationService invocations,TaskResearchExecutionService outputs,TaskResearchCompletionEvaluation evaluator,TaskNodeProducerAuthority producers,TaskProfessionalObjectLedger ledger,RuntimeObjectTransport transport,TaskProfessionalObjectRegistration registrations,PermissionSchema json){this.hosts=hosts;this.observations=observations;this.supervisor=supervisor;this.invocations=invocations;this.outputs=outputs;this.evaluator=evaluator;this.producers=producers;this.ledger=ledger;this.transport=transport;this.registrations=registrations;this.json=json;}
    public JsonNode finish(HttpServletRequest http,TaskProfessionalHostAccess.Handle handle,JsonNode request)throws Exception {
        ProfessionalRuntimeObservations.validate(request,"HOST_FINISH");String session=request.required("session_id").textValue();
        // Caller observation is reconciliation data only. Actual evidence is independently pulled below.
        hosts.execute(handle,a->ThrowingSupplier.of(()->{var current=hosts.session(handle,a,session);if(!FormalTaskNodeAuthority.sameEvidence(request.required("binding"),current.binding()))bad();return null;}).get());
        observations.finishObservation(http,request);
        var snapshot=hosts.execute(handle,a->ThrowingSupplier.of(()->supervisor.snapshotClosure(hosts.session(handle,a,session))).get());
        var inspection=supervisor.inspect(snapshot);
        hosts.execute(handle,a->ThrowingSupplier.of(()->{supervisor.close(hosts.session(handle,a,session),inspection).requireUnchanged();return null;}).get());
        var archive=register(handle,session,inspection,Kind.SESSION_EXPORT,null,null);
        var evidence=register(handle,session,inspection,Kind.EVIDENCE_BUNDLE,archive,null);
        var result=register(handle,session,inspection,Kind.STEP_RESULT,archive,evidence);
        return hosts.execute(handle,a->ThrowingSupplier.of(()->{
            var current=current(handle,a,session,inspection);var producer=producers.requireLeased(a,current.session.input());
            var archiveReceipt=read(current,producer,archive,null,null);var evidenceReceipt=read(current,producer,evidence,archiveReceipt,null);
            var resultReceipt=read(current,producer,result,archiveReceipt,evidenceReceipt);current.candidate.requireUnchanged();current.evaluated.requireUnchanged();
            return json.object().put("runId",current.session.runId()).put("stepId",current.session.stepId()).put("inputSha256",current.session.input().inputSha256()).put("leaseGeneration",Long.parseLong(current.session.generation())).put("status",current.evaluated.outcome()).put("resultArtifactId",resultReceipt.artifactId()).put("resultSha256",resultReceipt.reference().sha256());
        }).get());
    }
    private Stored register(TaskProfessionalHostAccess.Handle handle,String session,TaskProfessionalSupervisor.Inspection inspection,Kind kind,Stored archive,Stored evidence)throws Exception {
        var attempt=hosts.execute(handle,a->ThrowingSupplier.of(()->{var state=state(handle,a,session,inspection,kind,archive,evidence);var intent=ledger.prepare(state.producer,state.output);return ledger.attempt(state.producer,state.output,intent);}).get());
        S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{var state=state(handle,a,session,inspection,kind,archive,evidence);registrations.requireTransferCurrent(state.producer,state.output,attempt);return null;}).get());return null;}).get();
        var physical=transport.transferVerified(attempt,guard);
        hosts.execute(handle,a->ThrowingSupplier.of(()->{var state=state(handle,a,session,inspection,kind,archive,evidence);registrations.register(state.producer,state.output,attempt,physical).requireUnchanged();return null;}).get());
        return new Stored(kind,attempt,physical);
    }
    private State state(TaskProfessionalHostAccess.Handle handle,EnterpriseAccess.Admission a,String session,TaskProfessionalSupervisor.Inspection inspection,Kind kind,Stored archive,Stored evidence)throws Exception {
        var current=current(handle,a,session,inspection);var producer=producers.requireLeased(a,current.session.input());
        var ar=archive==null?null:read(current,producer,archive,null,null);var er=evidence==null?null:read(current,producer,evidence,ar,null);
        return new State(producer,produce(current,kind,ar,er));
    }
    private Current current(TaskProfessionalHostAccess.Handle handle,EnterpriseAccess.Admission a,String session,TaskProfessionalSupervisor.Inspection inspection)throws Exception {
        var actual=hosts.session(handle,a,session);var closure=supervisor.requireClosed(actual,inspection);var terminal=invocations.settledForSession(actual,closure);
        var candidate=outputs.candidate(actual,closure,terminal);return new Current(actual,closure,candidate,evaluator.evaluate(candidate));
    }
    private TaskProfessionalObjectRegistration.Registered read(Current current,TaskNodeProducerAuthority.LeasedProduction producer,Stored stored,TaskProfessionalObjectRegistration.Registered archive,TaskProfessionalObjectRegistration.Registered evidence)throws Exception {
        return registrations.readVerified(producer,produce(current,stored.kind,archive,evidence),stored.attempt,stored.physical);
    }
    private TaskResearchExecutionService.Produced produce(Current current,Kind kind,TaskProfessionalObjectRegistration.Registered archive,TaskProfessionalObjectRegistration.Registered evidence)throws Exception {
        return switch(kind){case SESSION_EXPORT->outputs.sessionExport(current.session,current.closure);case EVIDENCE_BUNDLE->outputs.evidenceBundle(current.candidate,current.evaluated);case STEP_RESULT->outputs.stepResult(current.candidate,current.evaluated,Objects.requireNonNull(evidence),Objects.requireNonNull(archive));};
    }
    private enum Kind{SESSION_EXPORT,EVIDENCE_BUNDLE,STEP_RESULT}
    private record Stored(Kind kind,TaskProfessionalObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer physical){}
    private record Current(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Closed closure,TaskResearchExecutionService.Candidate candidate,TaskResearchCompletionEvaluation.Evaluated evaluated){}
    private record State(TaskNodeProducerAuthority.LeasedProduction producer,TaskResearchExecutionService.Produced output){}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
