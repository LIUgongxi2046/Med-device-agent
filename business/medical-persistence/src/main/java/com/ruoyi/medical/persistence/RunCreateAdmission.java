package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.io.IOException;

/** C06 execution-proof consumer. There is deliberately no permissive/default implementation. */
public interface RunCreateAdmission {
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
