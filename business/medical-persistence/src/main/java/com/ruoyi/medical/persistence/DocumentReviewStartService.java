package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.springframework.util.function.ThrowingSupplier;
/** Explicit admission of an immutable saved-edit intent. No caller findings or asserted approval are accepted. */
public final class DocumentReviewStartService {
    private final EnterpriseAccess access;private final DocumentReviewJobService intents;private final DocumentReviewAuthority documents;private final DocumentReviewAdmission admissions;private final DocumentReviewExecutionService jobs;private final AgentPublicationAuthority publications;private final AgentExecutionPolicyAuthority policies;private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
    public DocumentReviewStartService(EnterpriseAccess access,DocumentReviewJobService intents,DocumentReviewAuthority documents,DocumentReviewAdmission admissions,DocumentReviewExecutionService jobs,AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies){this.access=access;this.intents=intents;this.documents=documents;this.admissions=admissions;this.jobs=jobs;this.publications=publications;this.policies=policies;}
    public JsonNode start(ApplicationSessionIdentity.Verified subject,String context,String intentId,byte[] bytes)throws Exception {
        WireValues.uuid(intentId);var request=json.read(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));ProfessionalRuntimeObservations.closed(request,java.util.Set.of("schema_version","agent_release_id","execution_policy","enterprise_budget","parent_budget","currency"));if(!"1.0.0".equals(request.required("schema_version").asText()))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        var selection=new DocumentReviewAdmission.Selection(request.required("agent_release_id").asText(),basis(request.required("execution_policy")),basis(request.required("enterprise_budget")),basis(request.required("parent_budget")),request.required("currency").asText());
        var original=access.execute(subject,a->ThrowingSupplier.of(()->{requireContext(a,context);return intents.original(a,intentId);}).get());
        var body=documents.read(subject,context,original.required("document_id").asText(),original.required("document_revision").asText());
        var prepared=publications.prepare(subject,context,selection.agentReleaseId(),DocumentReviewAdmission.PURPOSE,false);
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            requireContext(a,context);var publication=publications.require(a,selection.agentReleaseId(),DocumentReviewAdmission.PURPOSE,prepared);var policy=policies.requireByVersion(a,selection.executionPolicy().version_id(),selection.executionPolicy().content_sha256(),original.required("project_id").asText(),DocumentReviewAdmission.PURPOSE);
            var proof=admissions.require(a,documents.require(a,context,body),selection,publication,policy);var job=jobs.queued(proof,intentId);
            return json.tree(java.util.Map.of("schema_version","1.0.0","execution_kind","SYSTEM_JOB","job_type","CHECK_DOCUMENT","run_id",job.run(),"intent_id",job.intent(),"document_id",job.document(),"document_revision",job.documentRevision(),"input_sha256",job.inputHash()));
        }).get());
    }
    private static PermissionProtocol.Basis basis(JsonNode n){ProfessionalRuntimeObservations.closed(n,java.util.Set.of("version_id","content_sha256"));return new PermissionProtocol.Basis(n.required("version_id").asText(),n.required("content_sha256").asText());}
    private static void requireContext(EnterpriseAccess.Admission a,String context){if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
}
