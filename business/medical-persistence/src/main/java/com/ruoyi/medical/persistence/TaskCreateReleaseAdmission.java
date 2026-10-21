package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ResourceProtocol;
import java.io.IOException;
import java.util.List;

public interface TaskCreateReleaseAdmission {
    /** Physical publication evidence must be prepared before entering the write transaction. */
    default TaskCreateReleaseAdmission prepare(com.ruoyi.medical.identity.ApplicationSessionIdentity.Verified subject,
            String context, TaskProtocol.ValidatedCreate input) throws IOException {
        throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    /** Derived only from a prepared genuine publication; not a client-supplied data-use purpose. */
    default AgentExecutionPolicyAuthority.VerifiedPolicy sourcePolicy(EnterpriseAccess.Admission admission,
            TaskProtocol.ValidatedCreate input) throws IOException {
        throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    // C06必须读取实际发布决定、全部当前依赖和选定来源，不能以结构通过代替发布资格。
    Verified require(EnterpriseAccess.Admission admission, TaskProtocol.ValidatedCreate input,
            TemplateApplicabilityService.Target template, ScopeSelectionAuthority.Selection selection,
            AgentReleaseScopeAuthority.VerifiedScope scope) throws IOException;

    interface Verified {
        JsonNode authorizations();
        List<String> dependencyVersions();
        List<ResourceProtocol.Requirement> missingRequirements();
        void requireUnchanged() throws IOException;
    }
}
