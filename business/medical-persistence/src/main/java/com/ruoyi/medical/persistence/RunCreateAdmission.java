package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.io.IOException;

/** C06 execution-proof consumer. There is deliberately no permissive/default implementation. */
public interface RunCreateAdmission {
    /** Prepare original physical publication bytes outside any database transaction.
     * The returned consumer must remain bound to this exact task/request/subject/context. */
    default RunCreateAdmission prepare(com.ruoyi.medical.identity.ApplicationSessionIdentity.Verified subject,
            String context, TaskService.VerifiedTask task, RunCreateSchema.Create request) throws IOException {
        throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    default RunCreateAdmission prepareContinuation(com.ruoyi.medical.identity.ApplicationSessionIdentity.Verified subject,
            String context, TaskService.VerifiedTask task, RunCreateSchema.Create request, RunContinuationService.Authority continuation) throws IOException {
        throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    /** Must verify actual evaluation acceptance, all current release dependencies and runtime artifacts,
     * source-scope intersection, retrieval/network approval, model eligibility and approved stream retention.
     * The complete ReplayManifest must be bound to its published schema, not an arbitrary JSON document.
     * No workflow start or paid provider call may occur here; this executes inside the enterprise transaction. */
    Verified require(EnterpriseAccess.Admission admission, TaskService.VerifiedTask task,
            TaskPlanWriteProtocol.View plan, AgentReleaseScopeAuthority.VerifiedScope releaseScope,
            RunCreateSchema.Create request, String runId, String principalId) throws IOException;

    interface Verified {
        SchemaCatalogue.ValidatedPayload replayManifest();
        JsonNode scopeManifest();
        JsonNode principalManifest();
        String retentionPolicyRevision();
        void requireUnchanged() throws IOException;
    }
}
