package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Fixed task data-use metadata only. Never authorizes execution, publication or external processing. */
@Component
public final class TaskSourcePurposeAuthority {
    private final AgentReleaseStore releases;
    private final ObjectProvider<AgentPublicationAuthority> publications;
    private final AgentExecutionPolicyStore policies;

    public TaskSourcePurposeAuthority(AgentReleaseStore releases,
            ObjectProvider<AgentPublicationAuthority> publications, AgentExecutionPolicyStore policies) {
        this.releases = releases; this.publications = publications; this.policies = policies;
    }

    String requireHistorical(EnterpriseAccess.Admission admission, TaskProtocol.ValidatedCreate input,
            String entryPurpose) throws IOException {
        if (!Set.of("TASK_CREATION", "PLAN_MAINTENANCE", "RUN_CREATION").contains(entryPurpose)) fail(ErrorCode.ACCESS_DENIED);
        var authority = publications.getIfAvailable();
        if (authority == null) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var release = releases.read(admission, input.request().agent_release_id(), entryPurpose);
        var reference = authority.taskSourcePolicyReference(admission, release, entryPurpose);
        var loaded = policies.read(admission, reference.version_id(), entryPurpose);
        var definition = loaded.fixed().definition();
        if (!reference.content_sha256().equals(loaded.fixed().definitionSha256())
                || definition.projectId() != null && !input.request().project_id().equals(definition.projectId())
                || !Set.of("APPROVED", "DISABLED").contains(loaded.state().status())) fail(ErrorCode.CONTENT_MISMATCH);
        AgentExecutionPolicyAuthority.requireManifest(definition, release.content().manifest());
        if (!"DOCUMENT_BODY".equals(definition.businessPurpose())) fail(ErrorCode.ACCESS_DENIED);
        // Fixed approved/disabled metadata is sufficient to identify original data use. It does not renew approval.
        releases.requireUnchanged(release);
        return definition.businessPurpose();
    }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
