package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;

public interface TaskPlanReleaseAdmission {
    // C06核查实际评测发布决定、当前启用及全部依赖、来源范围交集、检索与网络批准和模型连接资格。
    Verified require(EnterpriseAccess.Admission admission, TaskService.VerifiedTask task,
            TaskPlanWriteProtocol.View draft, AgentReleaseScopeAuthority.VerifiedScope scope) throws IOException;

    interface Verified {
        JsonNode authorizations();
        List<String> dependencyVersions();
        void requireUnchanged() throws IOException;
    }
}
