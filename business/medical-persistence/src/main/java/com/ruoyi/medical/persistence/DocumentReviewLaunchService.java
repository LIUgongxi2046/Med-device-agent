package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.workflow.DocumentCheckDelivery;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.function.ThrowingSupplier;
/** Physical saved body/publication reads and Temporal I/O occur outside tenant transactions. */
public final class DocumentReviewLaunchService {
    private final DocumentReviewControlAccess controls;private final DocumentReviewAuthority documents;private final DocumentReviewAdmission admissions;
    private final AgentPublicationAuthority publications;private final AgentExecutionPolicyAuthority policies;private final DocumentReviewLaunchLedger launches;private final DocumentReviewLaunchConfiguration configuration;private final DocumentCheckDelivery delivery;
    public DocumentReviewLaunchService(DocumentReviewControlAccess controls,DocumentReviewAuthority documents,DocumentReviewAdmission admissions,AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies,DocumentReviewLaunchLedger launches,DocumentReviewLaunchConfiguration configuration,DocumentCheckDelivery delivery){this.controls=controls;this.documents=documents;this.admissions=admissions;this.publications=publications;this.policies=policies;this.launches=launches;this.configuration=configuration;this.delivery=delivery;}
    public JsonNode consume(HttpServletRequest http,String tenant,String run,String command)throws Exception {
        return controls.withReads(http,tenant,run,command,reads->ThrowingSupplier.of(()->{
            if(reads.execute(a->launches.attempted(tenant,run)))return recover(reads);
            var j=reads.job();var body=documents.read(reads.evidenceAccess(),reads.contextRevision(),j.document(),j.documentRevision());
            var publication=publications.prepareDocumentReview(reads,reads.admission().required("agent_release_id").asText(),DocumentReviewAdmission.PURPOSE);
            var attempt=reads.execute(a->ThrowingSupplier.of(()->{var current=current(reads,a,body,publication);return launches.attempt(current,launches.prepare(current,j,configuration));}).get());
            if(attempt.recoveryOnly())return recover(reads);
            reads.execute(a->ThrowingSupplier.of(()->{current(reads,a,body,publication).requireUnchanged();launches.recoveryOriginal(tenant,run);return null;}).get());
            var accepted=delivery.start(attempt.intent().reference(),attempt.intent().commandId());
            return reads.execute(a->ThrowingSupplier.of(()->launches.accepted(attempt.intent(),accepted)).get());
        }).get());
    }
    public JsonNode recover(HttpServletRequest http,String tenant,String run,String command)throws Exception{return controls.withReads(http,tenant,run,command,reads->ThrowingSupplier.of(()->recover(reads)).get());}
    private JsonNode recover(DocumentReviewControlAccess.Reads reads)throws Exception {var intent=reads.execute(a->ThrowingSupplier.of(()->launches.recoveryOriginal(reads.tenantId(),reads.runId())).get());var accepted=delivery.recover(intent.reference(),intent.commandId());return reads.execute(a->ThrowingSupplier.of(()->launches.accepted(intent,accepted)).get());}
    private DocumentReviewAdmission.Verified current(DocumentReviewControlAccess.Reads reads,EnterpriseAccess.Admission a,DocumentReadService.FixedRead body,AgentPublicationAuthority.PreparedPublication prepared)throws Exception {var d=reads.admission();var p=d.required("execution_policy");var policy=policies.requireByVersion(a,p.required("version_id").asText(),p.required("content_sha256").asText(),reads.job().project(),DocumentReviewAdmission.PURPOSE);var publication=publications.require(a,d.required("agent_release_id").asText(),DocumentReviewAdmission.PURPOSE,prepared);return admissions.requireCurrent(a,reads.job(),documents.require(a,reads.contextRevision(),body),publication,policy);}
}
