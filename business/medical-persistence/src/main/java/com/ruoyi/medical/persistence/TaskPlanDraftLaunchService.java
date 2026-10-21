package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.workflow.PlanDraftDelivery;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.function.ThrowingSupplier;
/** Concrete N01 launch consumer. Physical input and Temporal I/O occur outside transactions.
 * The original committed attempt selects query-only recovery before any new object/model work. */
public final class TaskPlanDraftLaunchService {
    private final TaskPlanDraftControlAccess controls;private final TaskPlanDraftJobs jobs;private final TaskPlanDraftAdmission admissions;
    private final TaskPlanDraftJobAuthority authority;private final AgentPublicationAuthority publications;private final AgentExecutionPolicyAuthority policies;
    private final TaskPlanDraftObjectLedger objects;private final RuntimeObjectTransport transport;private final TaskPlanDraftObjectRegistration registration;
    private final TaskPlanDraftLaunchLedger launches;private final TaskPlanDraftLaunchConfiguration configuration;private final PlanDraftDelivery delivery;
    public TaskPlanDraftLaunchService(TaskPlanDraftControlAccess controls,TaskPlanDraftJobs jobs,TaskPlanDraftAdmission admissions,
            TaskPlanDraftJobAuthority authority,AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies,
            TaskPlanDraftObjectLedger objects,RuntimeObjectTransport transport,TaskPlanDraftObjectRegistration registration,
            TaskPlanDraftLaunchLedger launches,TaskPlanDraftLaunchConfiguration configuration,PlanDraftDelivery delivery){this.controls=controls;this.jobs=jobs;this.admissions=admissions;this.authority=authority;this.publications=publications;this.policies=policies;this.objects=objects;this.transport=transport;this.registration=registration;this.launches=launches;this.configuration=configuration;this.delivery=delivery;}
    public JsonNode consume(HttpServletRequest http,String tenant,String run,String originalOperation)throws Exception {
        return controls.withReads(http,tenant,run,originalOperation,reads->ThrowingSupplier.of(()->{
            if(reads.execute(a->launches.attempted(tenant,run)))return recover(reads);
            var publication=publications.preparePlanDraft(reads,reads.admission().required("agent_release_id").textValue(),"RUN_CREATION");
            var intended=reads.execute(a->ThrowingSupplier.of(()->{var proof=current(reads,a,publication);return objects.prepareInput(proof.current(),proof.job());}).get());
            var attempt=reads.execute(a->ThrowingSupplier.of(()->{var proof=current(reads,a,publication);return objects.attemptInput(proof.current(),proof.job(),intended);}).get());
            S3ObjectReader.Admission guard=()->reads.execute(a->ThrowingSupplier.of(()->{var proof=current(reads,a,publication);objects.requireAttempt(proof.current(),proof.job(),intended);return null;}).get());
            var transferred=transport.transferVerified(attempt,guard);
            var launch=reads.execute(a->ThrowingSupplier.of(()->{var proof=current(reads,a,publication);var input=registration.register(proof,attempt,transferred);var intent=launches.prepare(proof,input,configuration);return launches.attempt(proof,intent);}).get());
            if(launch.recoveryOnly())return recover(reads);
            // Fresh authorization immediately before dispatch. The durable attempt cannot
            // become a second start after timeout, process death or uncertain response.
            reads.execute(a->ThrowingSupplier.of(()->{var proof=current(reads,a,publication);registration.register(proof,attempt,transferred).requireUnchanged();launches.require(proof,launch.intent());return null;}).get());
            var accepted=delivery.start(launch.intent().reference(),launch.intent().commandId());
            return reads.execute(a->ThrowingSupplier.of(()->{var result=launches.accepted(launch.intent(),accepted);if(result.transition()!=null)a.planDraftProof().acceptedStart(result.transition());return result.receipt();}).get());
        }).get());
    }
    public JsonNode recover(HttpServletRequest http,String tenant,String run,String originalOperation)throws Exception {return controls.withReads(http,tenant,run,originalOperation,reads->ThrowingSupplier.of(()->recover(reads)).get());}
    private JsonNode recover(TaskPlanDraftControlAccess.Reads reads)throws Exception {
        var intent=reads.execute(a->ThrowingSupplier.of(()->launches.recoveryOriginal(reads.tenantId(),reads.runId())).get());
        var accepted=delivery.recover(intent.reference(),intent.commandId());
        return reads.execute(a->ThrowingSupplier.of(()->{var result=launches.accepted(intent,accepted);if(result.transition()!=null)a.planDraftProof().acceptedStart(result.transition());return result.receipt();}).get());
    }
    private TaskPlanDraftJobAuthority.Verified current(TaskPlanDraftControlAccess.Reads reads,EnterpriseAccess.Admission a,AgentPublicationAuthority.PreparedPublication prepared)throws Exception {
        var job=reads.job();var original=jobs.original(job);var basis=original.request().executionPolicy();var policy=policies.requireByVersion(a,basis.version_id(),basis.content_sha256(),job.project(),"RUN_CREATION");
        var publication=publications.require(a,original.request().agentReleaseId(),"RUN_CREATION",prepared);return authority.require(admissions.requireCurrent(a,job,original,publication,policy),job);
    }
}
