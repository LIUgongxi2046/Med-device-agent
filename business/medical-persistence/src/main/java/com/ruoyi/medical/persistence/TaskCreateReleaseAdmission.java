package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ResourceProtocol;
import java.io.IOException;
import java.util.List;

public interface TaskCreateReleaseAdmission {
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
