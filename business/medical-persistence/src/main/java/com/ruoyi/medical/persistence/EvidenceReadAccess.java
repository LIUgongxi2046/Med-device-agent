package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.medical.contracts.ErrorCode;

/** Internal two-phase reader, constructed only from a genuine human or private bounded
 * control capability. Control branches expose no authoring/approval API. */
final class EvidenceReadAccess {
    private final FormalEvaluationControlAccess.Reads evaluation;
    private final DocumentReviewControlAccess.Reads documentReview;
    private final EnterpriseAccess access;
    private final ApplicationSessionIdentity.Verified subject;
    private final FormalRunControlAccess.Reads control;
    private final TaskPlanDraftControlAccess.Reads planDraft;
    private EvidenceReadAccess(EnterpriseAccess access,ApplicationSessionIdentity.Verified subject,
            FormalRunControlAccess.Reads control,TaskPlanDraftControlAccess.Reads planDraft) {
        this.evaluation=null;this.documentReview=null;this.access=access;this.subject=subject;this.control=control;this.planDraft=planDraft;
    }
    private EvidenceReadAccess(DocumentReviewControlAccess.Reads reads){evaluation=null;documentReview=Objects.requireNonNull(reads);access=null;subject=null;control=null;planDraft=null;reads.requireActive();}
    private EvidenceReadAccess(FormalEvaluationControlAccess.Reads reads){evaluation=Objects.requireNonNull(reads);documentReview=null;access=null;subject=null;control=null;planDraft=null;reads.requireActive();}
    static EvidenceReadAccess evaluation(FormalEvaluationControlAccess.Reads reads){return new EvidenceReadAccess(reads);}
    static EvidenceReadAccess documentReview(DocumentReviewControlAccess.Reads reads){return new EvidenceReadAccess(reads);}
    static EvidenceReadAccess human(EnterpriseAccess access,ApplicationSessionIdentity.Verified subject) {
        if(subject==null)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        return new EvidenceReadAccess(Objects.requireNonNull(access),subject,null,null);
    }
    static EvidenceReadAccess control(FormalRunControlAccess.Reads reads) {
        Objects.requireNonNull(reads).requireActive();return new EvidenceReadAccess(null,null,reads,null);
    }
    static EvidenceReadAccess planDraft(TaskPlanDraftControlAccess.Reads reads) {
        Objects.requireNonNull(reads).requireActive();return new EvidenceReadAccess(null,null,null,reads);
    }
    <T>T execute(Function<EnterpriseAccess.Admission,T> operation) {
        return evaluation!=null?evaluation.execute(operation):documentReview!=null?documentReview.execute(operation):planDraft!=null?planDraft.execute(operation):control!=null?control.execute(operation):access.execute(subject,operation);
    }
    String actorId(){return evaluation!=null?evaluation.actorId():documentReview!=null?documentReview.actorId():planDraft!=null?planDraft.actorId():control!=null?control.actorId():subject.user().user_id();}
    PublicationEvidenceRevalidation.Prepared prepareEvidence(PublicationEvidenceRevalidation evidence,String context,
            com.fasterxml.jackson.databind.JsonNode content,boolean historical)throws Exception {
        return control()?evidence.prepare(this,context,content,historical):evidence.prepare(subject,context,content,historical);
    }
    EvaluationAcceptanceObjectReader.VerifiedDocument source(EvaluationAcceptanceObjectReader reader,String context,
            String project,PermissionProtocol.Basis ref,String purpose)throws Exception {
        return control()?reader.sourceEvidence(this,context,project,ref,purpose):reader.source(subject,context,project,ref,purpose);
    }
    EvaluationAcceptanceObjectReader.VerifiedDocument artifact(EvaluationAcceptanceObjectReader reader,String context,
            String project,PermissionProtocol.Basis ref,String purpose,String schema,
            java.util.List<AgentPublicationDependencies.TypedUse> children)throws Exception {
        return control()?reader.artifactEvidence(this,context,project,ref,purpose,schema,children):reader.artifact(subject,context,project,ref,purpose,schema,children);
    }
    FormalEvaluationInputService.VerifiedInput input(FormalEvaluationInputService reader,String context,String id)throws Exception {
        return control()?reader.readVerified(this,context,id):reader.readVerified(subject,context,id);
    }
    void requireVersion(String id,String hash){if(evaluation!=null)evaluation.requireVersion(id,hash);else if(documentReview!=null)documentReview.requireVersion(id,hash);else if(planDraft!=null)planDraft.requireVersion(id,hash);else if(control!=null)control.requireVersion(id,hash);}
    boolean control(){return evaluation!=null||control!=null||planDraft!=null||documentReview!=null;}
    void outsideTransaction() {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        if(evaluation!=null)evaluation.requireActive();else if(documentReview!=null)documentReview.requireActive();else if(planDraft!=null)planDraft.requireActive();else if(control!=null)control.requireActive();else DocumentUploadTransferService.requireOutsideTransaction(subject);
    }
    <T>T readDocument(SourceUploadTransport transport,String tenant,int maximumBytes,S3ObjectReader.Admission guard,
            SourceUploadTransport.DocumentRead<T> reading)throws Exception {
        outsideTransaction();
        if(evaluation!=null){if(!evaluation.tenantId().equals(tenant))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return transport.readEvaluationDocument(evaluation,maximumBytes,guard,reading);}
        if(documentReview!=null){if(!documentReview.tenantId().equals(tenant))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return transport.readDocumentReviewDocument(documentReview,maximumBytes,guard,reading);}
        if(planDraft!=null){if(!planDraft.tenantId().equals(tenant))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return transport.readPlanDraftDocument(planDraft,maximumBytes,guard,reading);}
        if(control!=null){if(!control.tenantId().equals(tenant))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return transport.readControlDocument(control,maximumBytes,guard,reading);}
        return transport.readDocument(subject,tenant,maximumBytes,guard,reading);
    }
}
