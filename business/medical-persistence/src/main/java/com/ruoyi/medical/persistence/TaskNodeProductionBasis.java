package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;

/** Closed host-production basis, never a token or a generic write grant. The queued
 * implementation retains its original replay/launch restrictions. Running production
 * has a separate private minting authority and cannot register replay or launch. */
public sealed interface TaskNodeProductionBasis permits TaskRunBootstrapAuthority.Verified, TaskNodeProductionAuthority.Verified {
    EnterpriseAccess.Admission admission();
    RuntimeReplaySchema.VerifiedInputs replay();
    String runId();String taskId();String principalId();String tenantId();String projectId();String actorId();String classification();
    JsonNode command();String authorizationsSha256();String outboxEventId();
    void requireUnchanged() throws Exception;
}
